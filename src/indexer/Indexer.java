package indexer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.tartarus.snowball.ext.EnglishStemmer;
import database_manager.DatabaseManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static crawler.Crawler.normalizeUrl;


public class Indexer implements Runnable{
	private static DatabaseManager dbManager;
	private static final int THREADS_COUNT = 10;
	private static int pagesCount = 0;
	private static int actualPagesCount = 0;
	private static List<String> pages;
	private static HashMap <Integer, Integer> pagesState = new HashMap<>();
	private static HashMap <String, Integer> pageURLToID = new HashMap<>();
	public enum States { SKIP, CRAWL, RECRAWL};
	private static int countIndexed;
	private static List<Connection> connections = new ArrayList<>();

	public Indexer(DatabaseManager dbManager) {
		this.dbManager = dbManager;
	}

	public static boolean isArabic(String s) {
		for (int i = 0; i < s.length();) {
			int c = s.codePointAt(i);
			if (c >= 0x0600 && c <= 0x06E0)
				return true;
			i += Character.charCount(c);
		}
		return false;
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
			int thisPageID = Integer.valueOf(fileNameParts[htmlIndex - 1].split("[/]")[fileNameParts[htmlIndex - 1].split("[/]").length - 1]);
			boolean recrawl = false;
			if(pagesState.containsKey(thisPageID))
			{
				if(pagesState.get(thisPageID) == States.valueOf("SKIP").ordinal())
					continue;
				else if(pagesState.get(thisPageID) == States.valueOf("RECRAWL").ordinal())
					recrawl = true;
			}
			else
				continue;
			String title = doc.title();
			title = title.substring(0, Math.min(title.length(), 255));
			Elements metaTags = doc.getElementsByTag("meta");
			HashMap<String, String> metas = new HashMap<>();

			for (Element metaTag : metaTags) {
				String content = metaTag.attr("content");
				String name = metaTag.attr("name");
				metas.put(name, content);
			}
			String description = metas.get("description");
			if(description!=null)
				description = description.substring(0, Math.min(description.length(), 255));

			Elements links = doc.select("a[href]");
			ArrayList<Integer> pagesMentioned = new ArrayList<>();
			for (final Element link : links) {
				String urlText = link.attr("abs:href");
				if (!urlText.equals("")) {
					urlText = normalizeUrl(urlText); // URL Normalization
					int mentionedPageID;
					if(pageURLToID.containsKey(urlText))
					{
						mentionedPageID = pageURLToID.get(urlText);
						if(mentionedPageID != thisPageID)
						{
							pagesMentioned.add(mentionedPageID);
						}

					}

				}
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
					String word = wordsList.get(j);
					String stemmedWord;
					if(!isArabic(word))
					{
						word = word.toLowerCase();
						word = word.substring(0, Math.min(word.length(), 500));
						stemmer.setCurrent(word);
						stemmer.stem();
						stemmedWord = stemmer.getCurrent();
					}
					else
					{
						stemmedWord = word;
					}



					if (stemmedWord.equals(""))//|| stopWords.containsKey(stemmedWord))
					{
						wordsList.remove(j);
						wordsListSize--;
						j--;
					} else

						importantWords.put(stemmedWord, true);
				}
			}


			String[] words = doc.text().split("[\\s\\W][^\\u0600-\\u06FF]");
			ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
			HashMap<String, Integer> hm = new HashMap<String, Integer>();
			HashMap<String, String> hmIndices = new HashMap<String, String>();

			File textFile;
			BufferedWriter writer;
			try {
				textFile = new File("txt_docs/"+thisPageID+".txt");
				writer = new BufferedWriter(new FileWriter(textFile));
				textFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				writer = null;
			}

			int wordsListSize = wordsList.size();

			for (int i = 0; i < wordsListSize; i++) {
				String word = wordsList.get(i);
				if (word.equals(""))//|| stopWords.containsKey(word))
				{
					wordsList.remove(i);
					wordsListSize--;
					i--;
				}
				else
					{
						String stemmedWord;
						if(!isArabic(word))
						{
							word = word.toLowerCase();
							word = word.substring(0, Math.min(word.length(), 500));
							stemmer.setCurrent(word);
							stemmer.stem();
							stemmedWord = stemmer.getCurrent();
						}
						else
						{
							stemmedWord = word;
						}
					wordsList.set(i, stemmedWord);

					try {
						writer.write(word);
						writer.write(' ');
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (hm.containsKey(stemmedWord)) {
						int count = hm.get(stemmedWord);
						hmIndices.replace(stemmedWord,hmIndices.get(stemmedWord) + ","+i);
						count++;
						hm.replace(stemmedWord, count);
					} else {
						hm.put(stemmedWord, 1);
						hmIndices.put(stemmedWord,String.valueOf(i));
					}
				}
			}
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			//Database Access
				try {
					PreparedStatement pst;
					String sql;
					ResultSet rs;
					synchronized (dbManager) {

						//Populate page table with title, description, and indexing time.
						SimpleDateFormat formatter = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
						formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
						Date now = new Date(System.currentTimeMillis());
						sql = "UPDATE page SET description = ? , title = ?, indexed_time = ? where (id = ?)";
						pst = connection.prepareStatement(sql);
						pst.setString(1, description);
						pst.setString(2, title);
						pst.setString(3, formatter.format(now));
						pst.setInt(4, thisPageID);
						pst.executeUpdate();

						//Recrawling Logic
						if (recrawl) {
							sql = "DELETE FROM page_connections WHERE from_page_id = ?";
							pst = connection.prepareStatement(sql);
							pst.setInt(1, thisPageID);
							pst.executeUpdate();
							sql = "SELECT word.word as word, word_index.word_id as word_id, word.pages_count as pages_count FROM word INNER JOIN word_index ON word.id=word_index.word_id WHERE word_index.page_id = ?;";
							pst = connection.prepareStatement(sql);
							pst.setInt(1, thisPageID);
							rs = pst.executeQuery();
							while (rs.next()) {
								String word = rs.getString("word");
								if (!hm.containsKey(word)) {
									int wordID = rs.getInt("word_id");
									int pagesCountDB = rs.getInt("pages_count");
									pagesCountDB--;
									sql = "UPDATE word SET pages_count = ? WHERE id = ?";
									pst = connection.prepareStatement(sql);
									pst.setInt(1, pagesCountDB);
									pst.setInt(2, wordID);
									pst.executeUpdate();
									sql = "DELETE FROM word_index WHERE page_id = ? and word_id = ?";
									pst = connection.prepareStatement(sql);
									pst.setInt(1, thisPageID);
									pst.setInt(1, wordID);
									pst.executeUpdate();
								}
							}

						}

						//Populate page_connections table
						sql = "insert ignore into page_connections (from_page_id, to_page_id) values (?,?)";
						pst = connection.prepareStatement(sql);
						for (int i = 0; i < pagesMentioned.size(); i++) {
							pst.setInt(1, thisPageID);
							pst.setInt(2, pagesMentioned.get(i));
							pst.addBatch();
						}
						pst.executeBatch();
						connection.commit();
					}
					synchronized (dbManager) {

						//Populate word_index and word tables
						for (HashMap.Entry<String, Integer> entry : hm.entrySet()) {
							boolean importantHM = importantWords.containsKey(entry.getKey());
							sql = "SELECT id FROM word WHERE (word = ?)";
							pst = connection.prepareStatement(sql);
							pst.setString(1, entry.getKey());
							rs = pst.executeQuery();
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
									String indices = rs.getString("indices");
									if (countDB != entry.getValue() || importantDB != importantHM && !indices.contentEquals(hmIndices.get(entry.getKey()))) {
										sql = "UPDATE word_index SET count = ?, important = ?, indices = ? WHERE (word_id = ? AND page_id = ?)";
										pst = connection.prepareStatement(sql);
										pst.setInt(1, entry.getValue());
										pst.setBoolean(2, importantHM);
										pst.setString(3, hmIndices.get(entry.getKey()));
										pst.setInt(4, wordID);
										pst.setInt(5, thisPageID);
										pst.executeUpdate();
									}
								} else {
									sql = "INSERT INTO word_index (page_id, word_id, count, important, indices) VALUES (?, ?, ?, ?, ?);";
									pst = connection.prepareStatement(sql);
									pst.setInt(1, thisPageID);
									pst.setInt(2, wordID);
									pst.setInt(3, entry.getValue());
									pst.setBoolean(4, importantHM);
									pst.setString(5, hmIndices.get(entry.getKey()));
									pst.executeUpdate();
									sql = "SELECT * FROM word WHERE id = ?";
									pst = connection.prepareStatement(sql);
									pst.setInt(1, wordID);
									ResultSet rs2 = pst.executeQuery();
									if (rs2.next()) {
										int pages_count = rs2.getInt("pages_count");
										pages_count++;
										sql = "UPDATE word SET pages_count = ? WHERE id = ?";
										pst = connection.prepareStatement(sql);
										pst.setInt(1, pages_count);
										pst.setInt(2, wordID);
										pst.executeUpdate();
									}
								}
							} else {    //does not exist
								sql = "INSERT INTO word (word, pages_count) VALUES (?, 1)";
								pst = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
								pst.setString(1, entry.getKey());
								pst.executeUpdate();
								rs = pst.getGeneratedKeys();
								if (rs != null && rs.next()) {
									int wordID = rs.getInt(1);
									sql = "INSERT INTO word_index (page_id, word_id, count, important, indices) VALUES (?, ?, ?, ?, ?);";
									pst = connection.prepareStatement(sql);
									pst.setInt(1, thisPageID);
									pst.setInt(2, wordID);
									pst.setInt(3, entry.getValue());
									pst.setBoolean(4, importantHM);
									pst.setString(5, hmIndices.get(entry.getKey()));
									pst.executeUpdate();

								}

							}


						}
						connection.commit();
						if (!recrawl)
							countIndexed++;

					}
					if (!recrawl)
						System.out.println("Indexed: " + countIndexed + "/" + actualPagesCount);

				} catch (SQLException e) {
					e.printStackTrace();
				}


		}

	}








	public static void main(String args[]) throws IOException
	{
		countIndexed = 0;
		long start = System.currentTimeMillis();
		dbManager = new DatabaseManager();
		Connection initialConnection = dbManager.getDBConnection();
		try (Stream<Path> walk = Files.walk(Paths.get("html_docs/"))) {

					pages = walk.filter(Files::isRegularFile)
					.map(x -> x.toString()).collect(Collectors.toList());
					pagesCount = pages.size();

			//pages.forEach(System.out::println);

		} catch (IOException e) {
			e.printStackTrace();
		}
		initializePages(initialConnection);

		List<Thread> threads = new ArrayList<>();
		for( int i = 0; i < THREADS_COUNT && i < pagesCount; i++)
		{
			Connection connection = dbManager.getDBConnection();
			try {
				connection.setAutoCommit(false);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			Thread t = new Thread(new Indexer(dbManager));
			connections.add(connection);
			t.setName(Integer.toString(i));
			threads.add(t);
			t.start();
		}
		for( int i = 0; i < THREADS_COUNT && i < pagesCount; i++) {
			try {
				threads.get(i).join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

			System.out.println("Total runtime : " + (System.currentTimeMillis()-start)/1000 +" seconds.");
	}








	private static void initializePages(Connection connection)
	{

		try {
			connection.setAutoCommit(false);
			String sql = "SELECT * from test_search_engine.page";
			PreparedStatement pst = connection.prepareStatement(sql);
			ResultSet rs = pst.executeQuery();
			while(rs.next())
			{
				actualPagesCount++;
				int pageID = rs.getInt("id");
				SimpleDateFormat formatter= new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
				formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
				String crawledTime = rs.getString("crawled_time");
				java.util.Date crawledDate = formatter.parse(crawledTime);
				String url = rs.getString("url");
				String indexedTime = rs.getString("indexed_time");
				pageURLToID.put(url,pageID);
				if (!rs.wasNull()) {
					java.util.Date indexedDate = formatter.parse(indexedTime);
					if (indexedDate.getTime() > crawledDate.getTime())
						pagesState.put(pageID,States.valueOf("SKIP").ordinal());
					else
						pagesState.put(pageID,States.valueOf("RECRAWL").ordinal());

				}
				else
				{
					pagesState.put(pageID,States.valueOf("CRAWL").ordinal());
				}

			}
			connection.commit();
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		catch(ParseException e)
		{
			e.printStackTrace();
		}

	}




}
