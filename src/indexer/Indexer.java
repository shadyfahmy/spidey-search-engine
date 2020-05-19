package indexer;
import crawler.Crawler;
import de.jkeylockmanager.manager.KeyLockManager;
import de.jkeylockmanager.manager.KeyLockManagers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.tartarus.snowball.ext.EnglishStemmer;
import database_manager.DatabaseManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static crawler.Crawler.normalizeUrl;

public class Indexer implements Runnable{
	private static DatabaseManager dbManager;
	private static final int THREADS_COUNT = 10;
	private static int pagesCount = 0;
	private static List<String> pages;
	private static List<Connection> connections = new ArrayList<>();
	private static  final KeyLockManager lockManager = KeyLockManagers.newLock();

	private	void UpdateWord(Connection connection,HashMap.Entry<String, Integer> entry, int thisPageID,boolean importantHM ) throws SQLException
	{
		try {
			String sql = "SELECT id FROM word WHERE (word = ?)";
			PreparedStatement pst = connection.prepareStatement(sql);
			pst.setString(1, entry.getKey());
			ResultSet rs = pst.executeQuery();
			if (rs.next()) {    //exists
				int wordID = rs.getInt("id");
				//check if there exists an index of this word in this page
				sql = "SELECT * FROM word_index WHERE (page_id = ? AND word_id = ?)";
				pst = connection.prepareStatement(sql);
				pst.setInt(1, thisPageID);
				pst.setInt(2, wordID);
				rs = pst.executeQuery();
				if (rs.next()) {    //exists
					int countDB = rs.getInt("count");
					boolean importantDB = rs.getBoolean("important");
					if (countDB != entry.getValue() || importantDB != importantHM) {
						sql = "UPDATE word_index SET count = ?, important = ? WHERE (word_id = ? AND page_id = ?)";
						pst = connection.prepareStatement(sql);
						pst.setInt(1,entry.getValue());
						pst.setBoolean(2,importantHM);
						pst.setInt(3,wordID);
						pst.setInt(4,thisPageID);
						pst.executeUpdate();
					}
				}
				else
				{
					sql = "INSERT INTO word_index (page_id, word_id, count, important) VALUES (?, ?, ?, ?);";
					pst = connection.prepareStatement(sql);
					pst.setInt(1,thisPageID);
					pst.setInt(2,wordID);
					pst.setInt(3,entry.getValue());
					pst.setBoolean(4,importantHM);
					pst.executeUpdate();
					sql = "SELECT * FROM word WHERE id = ?";
					pst = connection.prepareStatement(sql);
					pst.setInt(1,wordID);
					ResultSet rs2 = pst.executeQuery();
					if(rs2.next())
					{
						int pages_count = rs2.getInt("pages_count");
						pages_count++;
						sql = "UPDATE word SET pages_count = ? WHERE id = ?";
						pst = connection.prepareStatement(sql);
						pst.setInt(1,pages_count);
						pst.setInt(2,wordID);
						pst.executeUpdate();
					}
				}
			}
			else {    //does not exist
				sql = "INSERT INTO word (word, pages_count) VALUES (?, 1)";
				pst = connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
				pst.setString(1, entry.getKey());;
				pst.executeUpdate();
				rs = pst.getGeneratedKeys();
				if (rs != null && rs.next()) {
					int wordID = rs.getInt(1);
					sql = "INSERT INTO word_index (page_id, word_id, count, important) VALUES (?, ?, ?, ?);";
					pst = connection.prepareStatement(sql);
					pst.setInt(1, thisPageID);
					pst.setInt(2, wordID);
					pst.setInt(3, entry.getValue());
					pst.setBoolean(4, importantHM);
					pst.executeUpdate();
				}


			}

		} catch (SQLException e)
		{
			e.printStackTrace();
		}
	}

	public Indexer(DatabaseManager dbManager) {
		this.dbManager = dbManager;
	}
	public void run() {
		int threadNumber = Integer.valueOf(Thread.currentThread().getName());
		Connection connection = connections.get(threadNumber);

		for (int p = threadNumber; p < pagesCount; p += THREADS_COUNT) {
			String fileName = pages.get(p);
			String[] fileNameParts = fileName.split("[.]");
			int htmlIndex = fileNameParts.length - 1;
			if (htmlIndex < 0)
				continue;
			if (!fileNameParts[htmlIndex].contentEquals("html"))
				continue;
			File input = new File(fileName);
			Document doc = null;

			try {
				doc = Jsoup.parse(input, "UTF-8", "");
			} catch (IOException e) {
				e.printStackTrace();

			}
			boolean recrawl = false;
			Elements links = doc.select("a[href]");
			int thisPageID = Integer.valueOf(fileNameParts[htmlIndex - 1].split("[/]")[fileNameParts[htmlIndex - 1].split("[/]").length - 1]);
			PreparedStatement pst;
			String sql;
			ResultSet rs;


			String title = doc.title();
			String body = doc.body().text();
			title = title.substring(0, Math.min(title.length(), 255));
			body = body.substring(0, Math.min(body.length(), 255));
			Elements metaTags = doc.getElementsByTag("meta");
			HashMap<String, String> metas = new HashMap<>();

			for (Element metaTag : metaTags) {
				String content = metaTag.attr("content");
				String name = metaTag.attr("name");
				metas.put(name, content);
			}
			String description = metas.get("description");
			System.out.println("thread is " + threadNumber +" " + description);


			try {
				connection.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}


			sql = "SELECT * from test_search_engine.page WHERE (id = " + thisPageID + ")";


			try {
				Statement st = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
				//pst.setInt(1,thisPageID);
				rs = st.executeQuery(sql);
				if (!rs.next())
					continue;
				else {
					try {
						String crawledTime = rs.getString("crawled_time");
						System.out.println("BEfore Error : " + crawledTime);
						java.util.Date crawledDate = Crawler.formatter.parse(crawledTime);
						String indexedTime = rs.getString("indexed_time");

						if (!rs.wasNull()) {
							java.util.Date indexedDate = Crawler.formatter.parse(indexedTime);
							if (indexedDate.getTime() > crawledDate.getTime())
								continue;
							else
								recrawl = true;
						}
						Date now = new Date(System.currentTimeMillis());
						System.out.println(Crawler.formatter.format(now));
						sql = "UPDATE page SET description = ? , title = ?, indexed_time = ? where (id = ?)";
						pst = connection.prepareStatement(sql);
						pst.setString(1, description);
						pst.setString(2, title);
						pst.setString(3, Crawler.formatter.format(now));
						pst.setInt(4, thisPageID);
						pst.executeUpdate();
//						rs.updateString("description",description);
//						rs.updateString("title",title);
//						System.out.println(Crawler.formatter.format(now) + "is null?" + crawledTime);
//						rs.updateString("indexed_time", Crawler.formatter.format(now));
					} catch (ParseException e) {
						e.printStackTrace();
					}

				}
			} catch (SQLException e) {
				e.printStackTrace();
			}


			ArrayList<Integer> pagesMentioned = new ArrayList<>();
			for (final Element link : links) {
				String urlText = link.attr("abs:href");
				if (!urlText.equals("")) {
					urlText = normalizeUrl(urlText); // URL Normalization
					sql = String.format("Select id From page where (url='%s')", urlText);
					int mentionedPageID;
					try {
						pst = connection.prepareStatement(sql);
						rs = pst.executeQuery();
						if (rs.next()) {
							mentionedPageID = rs.getInt(1); //IDTable
						} else {
							mentionedPageID = -1;
							if (threadNumber == 3) System.out.println("No pages found with url:" + urlText);
						}
					} catch (SQLException e) {
						e.printStackTrace();
						mentionedPageID = -1;
					}
					if (mentionedPageID != -1) {
						if (threadNumber == 3) System.out.println(thisPageID + "->" + mentionedPageID);
						pagesMentioned.add(mentionedPageID);
					}

				}
			}


			try {
				connection.setAutoCommit(false);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			sql = "";
			try {
				sql = "insert ignore into page_connections (from_page_id, to_page_id) values (?,?)";
				pst = connection.prepareStatement(sql);
				for (int i = 0; i < pagesMentioned.size(); i++) {
					pst.setInt(1, thisPageID);
					pst.setInt(2, pagesMentioned.get(i));
					pst.addBatch();
				}
				pst.executeBatch();
				connection.commit();
			} catch (SQLException e) {
				e.printStackTrace();
				System.out.println("Error in this sql line:" + sql);

			}


			//Create a stemmer
			EnglishStemmer stemmer = new EnglishStemmer();

			//Populate important words
			ArrayList<String> importantTags = (ArrayList<String>) doc.select("h1, h2, h3, h4, h5, h6, h7, title").eachText();
			HashMap<String, Boolean> importantWords = new HashMap<String, Boolean>();
			//System.out.println(Arrays.toString(importantTags.toArray()));
			for (int i = 0; i < importantTags.size(); i++) {
				String[] words = importantTags.get(i).split("[\\s\\W]");
				ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
				int wordsListSize = wordsList.size();
				for (int j = 0; j < wordsListSize; j++) {
					String word = wordsList.get(j).toLowerCase();
					if (word.equals(""))//|| stopWords.containsKey(word))
					{
						wordsList.remove(j);
						wordsListSize--;
						j--;
					} else
						importantWords.put(word, true);
				}
			}


			String[] words = doc.text().split("[\\s\\W]");
			ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
			HashMap<String, Integer> hm = new HashMap<String, Integer>();

			int wordsListSize = wordsList.size();
			for (int i = 0; i < wordsListSize; i++) {
				String word = wordsList.get(i).toLowerCase();
				if (word.equals(""))//|| stopWords.containsKey(word))
				{
					wordsList.remove(i);
					wordsListSize--;
					i--;
				} else {

					stemmer.setCurrent(word);
					stemmer.stem();
					String stemmedWord = stemmer.getCurrent();
					wordsList.set(i, stemmedWord);
					if (hm.containsKey(word)) {
						int count = hm.get(word);
						count++;
						hm.replace(word, count);
					} else {
						hm.put(word, 1);
					}

				}
			}

			try {
				connection.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			if(recrawl) {
				try {
					sql = "SELECT word.word as word, word_index.word_id as word_id FROM word INNER JOIN word_index ON word.id=word_index.word_id WHERE word.id = ?;";
					pst = connection.prepareStatement(sql);
					pst.setInt(1,thisPageID);
					rs = pst.executeQuery();
					while(rs.next())
					{
						String word = rs.getString("word");

						if(!hm.containsKey(word))
						{
							int wordID = rs.getInt("word_id");
							sql = "DELETE FROM word_index WHERE word_id = ? and page_id = ?;";
							pst = connection.prepareStatement(sql);
							pst.setInt(1,wordID);
							pst.setInt(2,thisPageID);
							pst.executeUpdate();
							sql = "SELECT * FROM word WHERE id = ?";
							pst = connection.prepareStatement(sql);
							pst.setInt(1,wordID);
							ResultSet rs2 = pst.executeQuery();
							if(rs2.next())
							{
								int pages_count = rs2.getInt("pages_count");
								pages_count--;
								sql = "UPDATE word SET pages_count = ? WHERE id = ?";
								pst = connection.prepareStatement(sql);
								pst.setInt(1,pages_count);
								pst.setInt(2,wordID);
								pst.executeUpdate();
							}

						}
					}

				}
				catch (SQLException e)
				{
					e.printStackTrace();
				}

			}


			for (HashMap.Entry<String, Integer> entry : hm.entrySet()) {
				boolean importantHM = importantWords.containsKey(entry.getKey());
				//check if word exists

				final HashMap.Entry<String, Integer>entryLambda = entry;
				lockManager.executeLocked(entry.getKey(), () -> {
					try {
						UpdateWord(connection, entryLambda, thisPageID, importantHM);
					}
					catch (SQLException e)
					{
						e.printStackTrace();
					}
				});


			}






			//String  printWords = Arrays.toString(wordsList.toArray());
			//System.out.println(printWords);


		}

	}
	public static void main(String args[]) throws IOException
	{
		Crawler.formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		dbManager = new DatabaseManager();
		try (Stream<Path> walk = Files.walk(Paths.get("html_docs/"))) {

					pages = walk.filter(Files::isRegularFile)
					.map(x -> x.toString()).collect(Collectors.toList());
					pagesCount = pages.size();

			//pages.forEach(System.out::println);

		} catch (IOException e) {
			e.printStackTrace();
		}
		List<Thread> threads = new ArrayList<>();
		for( int i = 0; i < THREADS_COUNT && i < pagesCount; i++)
		{
			Connection connection = dbManager.getDBConnection();
			Thread t = new Thread(new Indexer(dbManager));
			connections.add(connection);
			t.setName(Integer.toString(i));
			threads.add(t);
			t.start();

		}

	
	}
}
