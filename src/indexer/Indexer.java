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
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import static crawler.Crawler.normalizeUrl;
import static java.lang.Math.ceil;

import java.util.Queue;
import me.tongfei.progressbar.*;
import ranker.PageRanker;


public class Indexer implements Runnable {
	private static DatabaseManager dbManager;
	private static Integer imagesGlobalLock = 0;
	private static final int THREADS_COUNT = 10;
	private static final int BATCH_SIZE = 500;
	private static int leastID;
	private static List<String> pages;
	private static ProgressBar pb;
	private static int actualPagesCount;
	private static HashMap<Integer, Integer> pagesState = new HashMap<>();
	private static HashMap<String, Integer> pageURLToID;
	private static boolean commitThread = true, imageThread = true;
	private static HashMap<String, Integer> globalWordsIDs ;
	private static HashMap<String, Integer> globalImageWordsIDs;

	public enum States {SKIP, CRAWL, RECRAWL};
	private static int countIndexed;
	private static Connection connection;

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
					HashMap<String, Boolean> wordsMap = new HashMap<>();
					String[] words = image.description.split("[^\\\\s\\w\\u0600-\\u06FF]|[\\\\]");
					ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
					int wordsListSize = wordsList.size();
					EnglishStemmer stemmer = new EnglishStemmer();
					image.description = image.description.replaceAll("[\\\\]","/");
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
					String sql;
					PreparedStatement pst;
					ResultSet rs ;
					sql = "INSERT IGNORE INTO image (url, description) VALUES (?, ?)";
					pst = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
					pst.setString(1, image.url);
					pst.setString(2, image.description);
					pst.executeUpdate();
					rs = pst.getGeneratedKeys();
					if (rs != null && rs.next()) {
						imageID = rs.getInt(1);
					}
					else
						continue;


					//Populate word_index and word tables
					sql = "SELECT ";
					boolean first = true;
					HashMap<String, Integer> wordsIDs = new HashMap<>();
					HashMap<String, Boolean> neededWordsIDs = new HashMap<>();
					Integer wordID;
					Queue<String> newWordsQueue = new LinkedList<>();
					for (HashMap.Entry<String, Boolean> entry : wordsMap.entrySet()) {
						synchronized (globalImageWordsIDs) {
							if (globalImageWordsIDs.containsKey(entry.getKey())) {
								wordsIDs.put(entry.getKey(), globalImageWordsIDs.get(entry.getKey()));
							} else {
								neededWordsIDs.put(entry.getKey(), true);
							}
						}
					}
					for (HashMap.Entry<String, Boolean> entry : neededWordsIDs.entrySet()) {
						if (globalImageWordsIDs.containsKey(entry.getKey())) {
							wordsIDs.put(entry.getKey(), globalImageWordsIDs.get(entry.getKey()));
							continue;
						}

						if (!first) {
							sql = sql + ',';
						} else {
							first = false;
						}

						String prepText = " (SELECT id FROM word_image  WHERE  word = '" + entry.getKey()
								+ "')AS t_" + entry.getKey();
						sql = sql + prepText;
					}
					pst = connection.prepareStatement(sql);
					String sql2 = "INSERT INTO word_image (word, images_count) VALUES (?, 0)";
					PreparedStatement pst2 = connection.prepareStatement(sql2, Statement.RETURN_GENERATED_KEYS);
					if (!first) {
						synchronized (imagesGlobalLock) {
							rs = pst.executeQuery();
							rs.next();
							for (HashMap.Entry<String, Boolean> entry : neededWordsIDs.entrySet()) {
								if (globalImageWordsIDs.containsKey(entry.getKey())) {
									wordsIDs.put(entry.getKey(), globalImageWordsIDs.get(entry.getKey()));
									continue;
								}
								wordID = rs.getInt("t_" + entry.getKey());
								if (!rs.wasNull()) {
									wordsIDs.put(entry.getKey(), wordID);
								} else {

									pst2.setString(1, entry.getKey());
									pst2.addBatch();
									newWordsQueue.add(entry.getKey());

								}
							}
							pst2.executeBatch();
							rs = pst2.getGeneratedKeys();
							while (rs != null && rs.next()) {
								wordID = rs.getInt(1);
								String word = newWordsQueue.poll();
								if (word != null) {
									synchronized (globalImageWordsIDs) {
										globalImageWordsIDs.put(word, wordID);
									}
									wordsIDs.put(word, wordID);
								} else
									throw new Exception("words queue failed");
							}
						}
					}

					if (wordsMap.size() != wordsIDs.size())
						throw new Exception("words queue failed");



					sql = "INSERT IGNORE INTO word_index_image (word_id, image_id) VALUES (?, ?)";
					pst = connection.prepareStatement(sql);
					for (HashMap.Entry<String, Integer> entry : wordsIDs.entrySet()) {
						wordID = entry.getValue();

						pst.setInt(1, wordID);
						pst.setInt(2, imageID);
						pst.addBatch();
					}
					pst.executeBatch();

					sql = "UPDATE word_image INNER JOIN word_index_image ON word_image.id = word_index_image.word_id" +
							" SET word_image.images_count = word_image.images_count + 1 " +
							"WHERE word_index_image.image_id  = ?;";
					pst = connection.prepareStatement(sql);
					pst.setInt(1, imageID);
					pst.executeUpdate();

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	private static String returnDocument(Document doc)
	{
		Elements data;
		String docString = "";

		data = doc.select("meta[name=description]");
		docString = docString + " " + data.text();

		data = doc.select("title");
		docString = docString + " " + data.text();

		data = doc.getElementsByAttribute("pubdate");
		docString = docString + " " + data.text();

		data = doc.getElementsByAttribute("itemprop");
		docString = docString + " " + data.text();

		data = doc.select("h1");
		docString = docString + " " + data.text();

		data = doc.select("h2");
		docString = docString + " " + data.text();

		data = doc.select("h3");
		docString = docString + " " + data.text();

		data = doc.select("h4");
		docString = docString + " " + data.text();

		data = doc.select("h5");
		docString = docString + " " + data.text();

		data = doc.select("h6");
		docString = docString + " " + data.text();

		data = doc.select("b");
		docString = docString + " " + data.text();

		data = doc.select("p");
		docString = docString + " " + data.text();

		data = doc.select("strong");
		docString = docString + " " + data.text();

		return docString;
	}
	public void run() {
		int threadNumber = Integer.valueOf(Thread.currentThread().getName());
		if (threadNumber == THREADS_COUNT) {
			while (commitThread || commitOrder.peek() != null) {
				try {
					Thread.sleep(1000);
					if (actualPagesCount > 0)
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

		} else if (threadNumber > THREADS_COUNT) {
			ImageThread();
		} else {
			for (int p = threadNumber + leastID; p <= pagesState.size()+1; p += THREADS_COUNT) {
				int pageState;
				int thisPageID = p;
				if(pagesState.containsKey(thisPageID))
					pageState = pagesState.get(thisPageID);
				else
					continue;

				boolean recrawl = false;
				if (pageState == States.valueOf("SKIP").ordinal())
					;
				else {
					if (pageState == States.valueOf("RECRAWL").ordinal())
						recrawl = true;



					File input = new File("html_docs/" + thisPageID + ".html");
					Document doc = null;
					try {
						doc = Jsoup.parse(input, "UTF-8", "");
					} catch (IOException e) {
						SimpleDateFormat formatter = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
						formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
						String sql;
						PreparedStatement pst;
						Date now = new Date(System.currentTimeMillis());
						sql = "UPDATE page SET indexed_time = ? where (id = ?)";
						try {
							pst = connection.prepareStatement(sql);
							pst.setString(1, formatter.format(now));
							pst.setInt(2, thisPageID);
							pst.executeUpdate();
							synchronized (commitOrder) {
								{
									countIndexed++;
									if (countIndexed % 10 == 9) {
										commitOrder.add(true);
									}
								}
							}
						if (countIndexed > BATCH_SIZE - THREADS_COUNT)
							break;
						} catch (SQLException e1) {
							e1.printStackTrace();
						}
						continue;
					}
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
							if (image.url.equals("") || !image.url.startsWith("http"))
								continue;
							synchronized (images) {
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


					String[] words = returnDocument(doc).split("[^\\\\s\\w\\u0600-\\u06FF]|[\\\\]");
					ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
					HashMap<String, Integer> hm = new HashMap<String, Integer>();
					HashMap<String, ArrayList<Integer>> wordsPositions = new HashMap<>();


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
						word = word.substring(0, Math.min(word.length(), 30));
						if (word.equals(""))//|| stopWords.containsKey(word))
						{
							wordsList.remove(i);
							wordsListSize--;
							i--;
						} else {
							String stemmedWord;
							if (!isArabic(word)) {
								word = word.toLowerCase();
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
								wordsPositions.putIfAbsent(stemmedWord, new ArrayList<>());
								wordsPositions.get(stemmedWord).add(i);
								count++;
								hm.replace(stemmedWord, count);
							} else {
								hm.put(stemmedWord, 1);
								wordsPositions.putIfAbsent(stemmedWord, new ArrayList<>());
								wordsPositions.get(stemmedWord).add(i);
							}
						}
					}
					try {
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					boolean print = true;
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
							sql = "DELETE FROM word_positions WHERE page_id = ?";
							pst = connection.prepareStatement(sql);
							pst.setInt(1, thisPageID);
							pst.executeUpdate();


							sql = "UPDATE word INNER JOIN word_index ON word.id = word_index.word_id " +
									"SET word.pages_count = word.pages_count - 1 WHERE word_index.page_id  = ? " +
									"AND word.pages_count > 0;";
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
						for (HashMap.Entry<String, Boolean> entry : neededWordsIDs.entrySet()) {
							if (globalWordsIDs.containsKey(entry.getKey())) {
								wordsIDs.put(entry.getKey(), globalWordsIDs.get(entry.getKey()));
								continue;
							}

							if (!first) {
								sql = sql + ',';
							} else {
								first = false;
							}

							String prepText = " (SELECT id FROM word  WHERE  word = '" + entry.getKey()
									+ "')AS t_" + entry.getKey();
							sql = sql + prepText;
						}
						superQ = sql;
						pst = connection.prepareStatement(sql);
						String sql2 = "INSERT INTO word (word, pages_count) VALUES (?, 0)";
						PreparedStatement pst2 = connection.prepareStatement(sql2, Statement.RETURN_GENERATED_KEYS);
						if (!first) {
							synchronized (dbManager) {
								rs = pst.executeQuery();
								rs.next();
								for (HashMap.Entry<String, Boolean> entry : neededWordsIDs.entrySet()) {
									if (globalWordsIDs.containsKey(entry.getKey())) {
										wordsIDs.put(entry.getKey(), globalWordsIDs.get(entry.getKey()));
										continue;
									}
									wordID = rs.getInt("t_" + entry.getKey());
									if (!rs.wasNull()) {
										wordsIDs.put(entry.getKey(), wordID);
									} else {

										pst2.setString(1, entry.getKey());
										pst2.addBatch();
										newWordsQueue.add(entry.getKey());

									}
								}
								pst2.executeBatch();
								rs = pst2.getGeneratedKeys();
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


						if (hm.size() != wordsIDs.size())
							throw new Exception("words queue failed");

						sql = "INSERT IGNORE INTO word_index (page_id, word_id, count, important) VALUES (?, ?, ?, ?);";
						pst = connection.prepareStatement(sql);
						sql2 = "INSERT IGNORE INTO word_positions (page_id, word_id, position) VALUES (?, ?, ?);";
						pst2 = connection.prepareStatement(sql2);
						int count = 0;


						for (HashMap.Entry<String, Integer> entry : hm.entrySet()) {
							count++;


							pst.setInt(1, thisPageID);
							pst.setInt(2, wordsIDs.get(entry.getKey()));
							pst.setInt(3, entry.getValue());
							pst.setBoolean(4, importantWords.containsKey(entry.getKey()));
							pst.addBatch();
							if (count % 50 == 49) pst.executeBatch();
							ArrayList<Integer> indices = wordsPositions.get(entry.getKey());


							for (int i = 0; i < indices.size(); i++) {
								pst2.setInt(1, thisPageID);
								pst2.setInt(2, wordsIDs.get(entry.getKey()));
								pst2.setInt(3, indices.get(i));
								pst2.addBatch();

							}
							if (count % 50 == 49) pst2.executeBatch();

						}

						pst.executeBatch();
						pst2.executeBatch();

						sql = "UPDATE word INNER JOIN word_index ON word.id = word_index.word_id " +
								"SET word.pages_count = word.pages_count + 1 WHERE word_index.page_id  = ?;";
						pst = connection.prepareStatement(sql);
						pst.setInt(1, thisPageID);
						pst.executeUpdate();

						synchronized (commitOrder) {
							{
								countIndexed++;
								if (countIndexed % 10 == 9) {
									commitOrder.add(true);
								}
							}
						}

					} catch (Exception e) {
						e.printStackTrace();
						System.out.println(superQ);
					}
					if (countIndexed > BATCH_SIZE - THREADS_COUNT)
						break;
				}
			}
		}
	}
	public static void main(String args[]) {
		double expectedTime = 0;
		while(true) {
			long start = System.currentTimeMillis();
			countIndexed = 0;
			commitThread = true;
			imageThread = true;
			dbManager = new DatabaseManager();
			Connection initialConnection = dbManager.getDBConnection();
//			String disableIndexing = "ALTER TABLE `word` DISABLE KEYS;";
//			String disableImageIndexing = "ALTER TABLE `word_image` DISABLE KEYS;";
//			String enableIndexing = "ALTER TABLE `word` ENABLE KEYS;";
//			String enableImageIndexing = "ALTER TABLE `word_image` ENABLE KEYS;";
			initializePages(initialConnection);
			if (actualPagesCount > 0) {
				System.out.println("Indexing in batches of " + BATCH_SIZE + ", total pages remaining are " + actualPagesCount);
				try {
//					PreparedStatement pst = initialConnection.prepareStatement(disableIndexing);
//					pst.executeUpdate();
//					pst = initialConnection.prepareStatement(disableImageIndexing);
//					pst.executeUpdate();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				if(expectedTime != 0)
					System.out.print(" estimated remaining time is " + (expectedTime*actualPagesCount/BATCH_SIZE) + " minutes");
				pb = new ProgressBar("Indexing", Math.min(actualPagesCount,BATCH_SIZE)); // name, initial max
				pb.start();
			}
			connection = initialConnection;
			List<Thread> threads = new ArrayList<>();
			for (int i = 0; i < THREADS_COUNT; i++) {
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
			for (int i = THREADS_COUNT + 1; i < (int) (ceil(1.25 * THREADS_COUNT)); i++) {
				Thread tImage = new Thread(new Indexer(dbManager));
				tImage.setName(Integer.toString(i));
				threadsImages.add(tImage);
				tImage.start();
			}
			try {
				for (int i = 0; i < THREADS_COUNT && i < actualPagesCount; i++) {
					threads.get(i).join();
				}
				imageThread = false;
				for (int i = THREADS_COUNT + 1; i < (int)  (ceil(1.25 * THREADS_COUNT)); i++) {
					threadsImages.get(i - THREADS_COUNT - 1).join();
				}
				commitThread = false;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			try {
				tCommit.join();
				connection.commit();
				if (actualPagesCount > 0) {
					pb.stop();
					PageRanker.getInstance().timedUpdatePageRanks();
					try {
//						pst = initialConnection.prepareStatement(enableIndexing);
//						pst.executeUpdate();
//						pst = initialConnection.prepareStatement(enableImageIndexing);
//						pst.executeUpdate();
						connection.commit();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					if(expectedTime == 0)
						expectedTime = (System.currentTimeMillis() - start)/60000.0;
					else
						expectedTime =((System.currentTimeMillis() - start)/60000.0 + expectedTime)/2;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void initializePages(Connection connection) {

		try {
			actualPagesCount = 0;
			pagesState = new HashMap<>();
			pageURLToID = new HashMap<>();
			commitThread = true;
			imageThread = true;
			globalWordsIDs = new HashMap<>();
			globalImageWordsIDs = new HashMap<>();
			leastID = 2147483647;
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
						leastID = Math.min(leastID,pageID);
					}
				} else {
					pagesState.put(pageID, States.valueOf("CRAWL").ordinal());
					actualPagesCount++;
					leastID = Math.min(leastID,pageID);
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
