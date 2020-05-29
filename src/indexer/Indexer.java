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
import java.util.Queue;
import me.tongfei.progressbar.*;
import ranker.PageRanker;


public class Indexer implements Runnable {
	private static DatabaseManager dbManager;
	private static final int THREADS_COUNT = 15;
	private static int pagesCount = 0;
	private static int actualPagesCount = 0;
	private static List<String> pages;
	private static HashMap<Integer, Integer> pagesState = new HashMap<>();
	private static HashMap<String, Integer> pageURLToID = new HashMap<>();
	private static ProgressBar pb;

	public enum States {SKIP, CRAWL, RECRAWL}

	;
	private static int countIndexed;
	private static Connection connection;
	private static boolean commitThread = true, imageThread = true;
	private static HashMap<String, Integer> globalWordsIDs = new HashMap<>();
	private class Image {
		public String description;
		public String url;
	}

	private static Queue<Image> images = new LinkedList<>();
	private static Queue<Boolean> commitOrder = new LinkedList<>();

	public Indexer(DatabaseManager dbManager) {
		this.dbManager = dbManager;
	}

	public static boolean isArabic(String s) {
		for (int i = 0; i < s.length(); ) {
			int c = s.codePointAt(i);
			if (c >= 0x0600 && c <= 0x06E0)
				return true;
			i += Character.charCount(c);
		}
		return false;
	}

	public static void ImageThread() {
		while (imageThread || images.peek() != null) {
			Image image;
			synchronized (images) {
				 image = images.poll();
			}if (image != null) {
				try {
					int imageID = -1;
					boolean skipRecrawlDeletion = false;
					HashMap<String, Boolean> wordsMap = new HashMap<>();
					String[] words = image.description.split("[^\\\\s\\w\\u0600-\\u06FF]|[\\\\]");
					ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
					int wordsListSize = wordsList.size();
					EnglishStemmer stemmer = new EnglishStemmer();
					for (int j = 0; j < wordsListSize; j++) {
						String word = wordsList.get(j);
						String stemmedWord;

						if (!isArabic(word)) {
							word = word.toLowerCase();
							word = word.substring(0, Math.min(word.length(), 500));
							stemmer.setCurrent(word);
							stemmer.stem();
							stemmedWord = stemmer.getCurrent();
						} else {
							stemmedWord = word;
						}

						if (stemmedWord.equals(""))//|| stopWords.containsKey(stemmedWord))
						{
							wordsList.remove(j);
							wordsListSize--;
							j--;
						} else

							wordsMap.put(stemmedWord, true);
					}
					String sql = "SELECT * FROM image WHERE url = ?";
					PreparedStatement pst = connection.prepareStatement(sql);
					pst.setString(1,image.url);
					ResultSet rs = pst.executeQuery();
					if (rs.next()) {
						imageID = rs.getInt("id");
					} else {
						sql = "INSERT INTO image (url, description) VALUES (?, ?)";
						pst = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
						pst.setString(1, image.url);
						pst.setString(2, image.description);
						pst.executeUpdate();
						rs = pst.getGeneratedKeys();
						skipRecrawlDeletion = true;
						if (rs != null && rs.next()) {
							imageID = rs.getInt(1);
						}
					}
					if (!skipRecrawlDeletion) {
						sql = "UPDATE word_image INNER JOIN word_index_image ON word_image.id = word_index_image.word_id SET word_image.images_count = word_image.images_count - 1 WHERE word_index_image.image_id  = ? AND word_image.images_count > 0;";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, imageID);
						pst.executeUpdate();
						sql = "DELETE FROM word_index_image WHERE image_id = ?";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, imageID);
						pst.executeUpdate();
					}
					for (HashMap.Entry<String, Boolean> entry : wordsMap.entrySet()) {
						sql = "SELECT * FROM word_image WHERE word = ?";
						pst = connection.prepareStatement(sql);
						pst.setString(1, entry.getKey());
						rs = pst.executeQuery();
						int wordID = -1;
						if (!rs.next()) {
							sql = "INSERT IGNORE INTO word_image (word, images_count) VALUES (?, 0)";
							pst = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
							pst.setString(1, entry.getKey());
							pst.executeUpdate();
							rs = pst.getGeneratedKeys();
							if (rs != null && rs.next()) {
								wordID = rs.getInt(1);
							}
						}

						sql = "INSERT IGNORE INTO word_index_image (word_id, image_id) VALUES (?, ?)";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, wordID);
						pst.setInt(2, imageID);
						pst.executeUpdate();
					}
					sql = "UPDATE word_image INNER JOIN word_index_image ON word_image.id = word_index_image.word_id SET word_image.images_count = word_image.images_count + 1 WHERE word_index_image.image_id  = ?;";
					pst = connection.prepareStatement(sql);
					pst.setInt(1, imageID);
					pst.executeUpdate();

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void run() {
		int threadNumber = Integer.valueOf(Thread.currentThread().getName());
		if (threadNumber == THREADS_COUNT) {
			while (commitThread || commitOrder.peek() != null) {
                try {
                    Thread.sleep(1000);
                    if(actualPagesCount > 0)
						pb.stepTo(countIndexed);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Boolean commit = commitOrder.poll();
				if (commit != null) {
					try {
						connection.commit();
					} catch (SQLException e) {
						e.printStackTrace();
					}

				}

			}

		} else if (threadNumber > THREADS_COUNT)
			ImageThread();
		else
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
				if (pagesState.containsKey(thisPageID)) {
					if (pagesState.get(thisPageID) == States.valueOf("SKIP").ordinal())
						continue;
					else if (pagesState.get(thisPageID) == States.valueOf("RECRAWL").ordinal())
						recrawl = true;
				} else
					continue;
				String title = doc.title();
				title = title.substring(0, Math.min(title.length(), 250));
				Elements metaTags = doc.getElementsByTag("meta");
				HashMap<String, String> metas = new HashMap<>();

				for (Element metaTag : metaTags) {
					String content = metaTag.attr("content");
					String name = metaTag.attr("name");
					metas.put(name, content);
				}
				String description = metas.get("description");
				if (description != null)
					description = description.substring(0, Math.min(description.length(), 250));

				Elements links = doc.select("a[href]");
				ArrayList<Integer> pagesMentioned = new ArrayList<>();
				for (final Element link : links) {
					String urlText = link.attr("abs:href");
					if (!urlText.equals("")) {
						urlText = normalizeUrl(urlText); // URL Normalization
						int mentionedPageID;
						if (pageURLToID.containsKey(urlText)) {
							mentionedPageID = pageURLToID.get(urlText);
							if (mentionedPageID != thisPageID) {
								pagesMentioned.add(mentionedPageID);
							}
						}
					}
				}
				Elements img = doc.getElementsByTag("img");
				for (Element element : img) {
					if (!(element.attr("alt") == null || element.attr("alt").equals(""))) {
						Image image = new Image();
						image.description = element.attr("alt");
						image.description = image.description.substring(0, Math.min(image.description.length(), 250));
						image.url = element.attr("abs:src");
						if(image.url.equals("") || !image.url.startsWith("http"))
							continue;
						synchronized (images){
							images.add(image);
						}
					}
				}
				//Create a stemmer
				EnglishStemmer stemmer = new EnglishStemmer();
				//Populate important words
				ArrayList<String> importantTags = (ArrayList<String>) doc.select("title").eachText();
				HashMap<String, Boolean> importantWords = new HashMap<String, Boolean>();
				//System.out.println(Arrays.toString(importantTags.toArray()));
				for (int i = 0; i < importantTags.size(); i++) {
					String[] words = importantTags.get(i).split("[^\\\\s\\w\\u0600-\\u06FF]|[\\\\]");
					ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
					int wordsListSize = wordsList.size();
					for (int j = 0; j < wordsListSize; j++) {
						String word = wordsList.get(j);
						String stemmedWord;
						if (!isArabic(word)) {
							word = word.toLowerCase();
							word = word.substring(0, Math.min(word.length(), 500));
							stemmer.setCurrent(word);
							stemmer.stem();
							stemmedWord = stemmer.getCurrent();
						} else {
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


				String[] words = doc.text().split("[^\\\\s\\w\\u0600-\\u06FF]|[\\\\]");
				ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
				HashMap<String, Integer> hm = new HashMap<String, Integer>();
				HashMap<String, String> hmIndices = new HashMap<String, String>();

				String superQ = "";

				File textFile;
				BufferedWriter writer;
				try {
					textFile = new File("txt_docs/" + thisPageID + ".txt");
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
					} else {
						String stemmedWord;
						if (!isArabic(word)) {
							word = word.toLowerCase();
							word = word.substring(0, Math.min(word.length(), 500));
							stemmer.setCurrent(word);
							stemmer.stem();
							stemmedWord = stemmer.getCurrent();
						} else {
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
							hmIndices.replace(stemmedWord, hmIndices.get(stemmedWord) + "," + i);
							count++;
							hm.replace(stemmedWord, count);
						} else {
							hm.put(stemmedWord, 1);
							hmIndices.put(stemmedWord, String.valueOf(i));
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
//					synchronized (dbManager) {

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
						sql = "DELETE FROM word_positions WHERE from_page_id = ?";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, thisPageID);
						pst.executeUpdate();


						sql = "UPDATE word INNER JOIN word_index ON word.id = word_index.word_id SET word.pages_count = word.pages_count - 1 WHERE word_index.page_id  = ? AND word.pages_count > 0;";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, thisPageID);
						pst.executeUpdate();
						sql = "DELETE FROM word_index WHERE page_id = ?";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, thisPageID);
						pst.executeUpdate();

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
//						connection.commit();
//					}
//					synchronized (dbManager) {

					//Populate word_index and word tables
					long start = System.currentTimeMillis();
					sql = "SELECT ";
					boolean first = true;
					HashMap<String, Integer> wordsIDs = new HashMap<>();
					HashMap<String, Boolean> neededWordsIDs = new HashMap<>();
					Integer wordID;
					Queue<String> newWordsQueue = new LinkedList<>();
					for (HashMap.Entry<String, Integer> entry : hm.entrySet()) {
						synchronized (globalWordsIDs) {
							if (globalWordsIDs.containsKey(entry.getKey())) {
								wordsIDs.put(entry.getKey(), globalWordsIDs.get(entry.getKey()));
							} else {
								neededWordsIDs.put(entry.getKey(), true);
							}
						}
					}

					synchronized (dbManager) {
						for (HashMap.Entry<String, Boolean> entry : neededWordsIDs.entrySet()) {
							synchronized (globalWordsIDs) {
								if (globalWordsIDs.containsKey(entry.getKey())) {
									wordsIDs.put(entry.getKey(), globalWordsIDs.get(entry.getKey()));
									continue;
								}
							}
							if (!first)
								sql = sql + ',';
							else
								first = false;

							String prepText = " (SELECT id FROM word  WHERE  word = '" + entry.getKey() + "')AS table_" + entry.getKey();
							sql = sql + prepText;
						}
						if(!first) {
							superQ = sql;
							pst = connection.prepareStatement(sql);
							rs = pst.executeQuery();
							rs.next();
							sql = "INSERT INTO word (word, pages_count) VALUES (?, 0)";
							pst = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);


							for (HashMap.Entry<String, Boolean> entry : neededWordsIDs.entrySet()) {
								if (globalWordsIDs.containsKey(entry.getKey()))
									continue;
								wordID = rs.getInt("table_" + entry.getKey());
								if (!rs.wasNull()) {
									wordsIDs.put(entry.getKey(), wordID);
								} else {

									pst.setString(1, entry.getKey());
									pst.addBatch();
									newWordsQueue.add(entry.getKey());

								}
							}
//						System.out.println(p + " " + (System.currentTimeMillis()-start)/1000);
							pst.executeBatch();
							rs = pst.getGeneratedKeys();
							while (rs != null && rs.next()) {
								wordID = rs.getInt(1);
								String word = newWordsQueue.poll();
								if (word != null) {
									synchronized (globalWordsIDs) {
										globalWordsIDs.put(word, wordID);
									}
									wordsIDs.put(word, wordID);


								} else
									throw new Exception("words queue failed");
							}
						}
					}
					if(hm.size() != wordsIDs.size())
						throw new Exception("words queue failed");

					sql = "INSERT INTO word_index (page_id, word_id, count, important) VALUES (?, ?, ?, ?);";
					pst = connection.prepareStatement(sql);
					String sql2 = "INSERT IGNORE INTO word_positions (page_id, word_id, position) VALUES (?, ?, ?);";
					PreparedStatement pst2 = connection.prepareStatement(sql2);
					for (HashMap.Entry<String, Integer> entry : hm.entrySet()) {
						pst.setInt(1, thisPageID);
						pst.setInt(2, wordsIDs.get(entry.getKey()));
						pst.setInt(3, entry.getValue());
						pst.setBoolean(4, importantWords.containsKey(entry.getKey()));
						pst.addBatch();

						String indices = hmIndices.get(entry.getKey());
						String[] wordPositions = indices.split("[,]");
						ArrayList<String> wordPositionsList = new ArrayList<>(Arrays.asList(wordPositions));
						int wordPositionsSize = wordPositionsList.size();
						for (int i = 0; i < wordPositionsSize; i++) {
							if (wordPositionsList.get(i).contentEquals(""))
								continue;
							pst2.setInt(1, thisPageID);
							pst2.setInt(2, wordsIDs.get(entry.getKey()));
							pst2.setInt(3, Integer.valueOf(wordPositionsList.get(i)));
							pst2.addBatch();

						}
					}
					pst.executeBatch();
					pst2.executeBatch();
					sql = "UPDATE word INNER JOIN word_index ON word.id = word_index.word_id SET word.pages_count = word.pages_count + 1 WHERE word_index.page_id  = ?;";
					pst = connection.prepareStatement(sql);
					pst.setInt(1, thisPageID);
					pst.executeUpdate();

					synchronized (commitOrder) {
						{
							countIndexed++;
							if (countIndexed % 10 == 9)
								commitOrder.add(true);						}
					}

				} catch (Exception e) {
					e.printStackTrace();
					System.out.println(superQ);
				}


			}

	}


	public static void main(String args[]) {
		countIndexed = 0;
		commitThread = true;
		imageThread = true;
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
		if(actualPagesCount > 0)
		{
			pb = new ProgressBar("Indexing", actualPagesCount); // name, initial max
			pb.start();
		}

		connection = initialConnection;
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < THREADS_COUNT && i < pagesCount; i++) {
//			Connection connection = dbManager.getDBConnection();
			try {
				connection.setAutoCommit(false);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			Thread t = new Thread(new Indexer(dbManager));
			t.setName(Integer.toString(i));
			threads.add(t);
			t.start();
		}
		Thread tCommit = new Thread(new Indexer(dbManager));
		tCommit.setName(Integer.toString(THREADS_COUNT));
		tCommit.start();
		List<Thread> threadsImages = new ArrayList<>();
		for(int i = THREADS_COUNT+1; i < (int)(1.25*THREADS_COUNT);i++)
		{
			Thread tImage = new Thread(new Indexer(dbManager));
			tImage.setName(Integer.toString(i));
			threadsImages.add(tImage);
			tImage.start();
		}
		try {
			for (int i = 0; i < THREADS_COUNT && i < pagesCount; i++) {

				threads.get(i).join();

			}
			imageThread = false;
			for (int i = THREADS_COUNT + 1; i < (int) (1.25 * THREADS_COUNT); i++) {
				threadsImages.get(i - THREADS_COUNT - 1).join();
			}
			commitThread = false;
		}catch (InterruptedException e) {
			e.printStackTrace();
		}

		try {
			tCommit.join();
            connection.commit();
            if(actualPagesCount > 0)
			{
				pb.stop();
				System.out.println(globalWordsIDs.size());
				PageRanker.getInstance().timedUpdatePageRanks();
			}
        } catch (Exception e) {
			e.printStackTrace();
		}

	}


	private static void initializePages(Connection connection) {

		try {
			connection.setAutoCommit(false);
			String sql = "SELECT * from test_search_engine.page";
			PreparedStatement pst = connection.prepareStatement(sql);
			ResultSet rs = pst.executeQuery();
			while (rs.next()) {
				int pageID = rs.getInt("id");
				SimpleDateFormat formatter = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
				formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
				String crawledTime = rs.getString("crawled_time");
				java.util.Date crawledDate = formatter.parse(crawledTime);
				String url = rs.getString("url");
				String indexedTime = rs.getString("indexed_time");
				pageURLToID.put(url, pageID);
				if (!rs.wasNull()) {
					java.util.Date indexedDate = formatter.parse(indexedTime);
					if (indexedDate.getTime() > crawledDate.getTime())
						pagesState.put(pageID, States.valueOf("SKIP").ordinal());
					else {
						pagesState.put(pageID, States.valueOf("RECRAWL").ordinal());
						actualPagesCount++;
					}
				} else {
					pagesState.put(pageID, States.valueOf("CRAWL").ordinal());
					actualPagesCount++;
				}

			}
			connection.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

	}


}
