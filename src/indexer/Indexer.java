package indexer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.tartarus.snowball.ext.EnglishStemmer;
import database_connection.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Indexer implements Runnable{
	private static DatabaseManager dbManager;
	private static final int THREADS_COUNT = 10;
	private static int pagesCount = 0;
	private static Connection connection;
	private static List<String> pages;
	public Indexer(DatabaseManager dbManager) {
		this.dbManager = dbManager;
	}
	public void run() {
		int threadNumber = Integer.valueOf(Thread.currentThread().getName());
		for(int p = threadNumber; p < pagesCount; p+=pagesCount)
		{
			String fileName = pages.get(p);
			String[] fileNameParts = fileName.split("[.]");
			int htmlIndex = fileNameParts.length-1;
			if(htmlIndex < 0)
				continue;
			if(!fileNameParts[htmlIndex].contentEquals("html"))
				continue;
//			String baseUri, fullUrl;
//			fileNameParts = fileNameParts[htmlIndex-1].split("[/]");
//			int urlIndex = fileNameParts.length-1;
//			fullUrl = fileNameParts[urlIndex];
//			fullUrl = fullUrl.replace("-", ":");
//			fullUrl = fullUrl.replace("_", ".");
//			fullUrl = fullUrl.replace("|", "/");
//			baseUri
			File input = new File(fileName);
			Document doc = null;
			try {
				doc = Jsoup.parse(input, "UTF-8","");
			} catch (IOException e) {
				e.printStackTrace();
			}




			//Create a stemmer
			EnglishStemmer stemmer = new EnglishStemmer();

			//Populate important words
			ArrayList<String>  importantTags = (ArrayList<String>) doc.select("h1, h2, h3, h4, h5, h6, h7, title").eachText();
			HashMap<String,Boolean> importantWords = new HashMap<String,Boolean>();
			System.out.println(Arrays.toString(importantTags.toArray()));
			for(int i = 0; i < importantTags.size();i++)
			{
				String [] words = importantTags.get(i).split("[\\s\\W]");
				ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
				int wordsListSize = wordsList.size();
				for(int j = 0; j < wordsListSize; j++)
				{
					String word = wordsList.get(j).toLowerCase();
					if(word.equals(""))//|| stopWords.containsKey(word))
					{
						wordsList.remove(j);
						wordsListSize--;
						j--;
					}
					else
						importantWords.put(word,true);
				}
			}


			String [] words = doc.text().split("[\\s\\W]");
			ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
			HashMap<String,Integer> hm=new HashMap<String,Integer> ();

			int wordsListSize = wordsList.size();
			for(int i = 0; i <wordsListSize;i++)
			{
				String word = wordsList.get(i).toLowerCase();
				if(word.equals(""))//|| stopWords.containsKey(word))
				{
					wordsList.remove(i);
					wordsListSize--;
					i--;
				}
				else
				{

					stemmer.setCurrent(word);
					stemmer.stem();
					String stemmedWord = stemmer.getCurrent();
					wordsList.set(i,stemmedWord);
					if(hm.containsKey(word))
					{
						int count = hm.get(word);
						count++;
						hm.replace(word, count);
					}
					else
					{
						hm.put(word,1);
					}

				}
			}



			hm.entrySet().forEach(entry->{
				System.out.println(entry.getKey() + " " + entry.getValue());
			});
			importantWords.entrySet().forEach(entry->{
				System.out.println(entry.getKey() + " " + entry.getValue());
			});
			//String  printWords = Arrays.toString(wordsList.toArray());
			//System.out.println(printWords);

			while(true);







		}
	}
	public static void main(String args[]) throws IOException
	{
		dbManager = new DatabaseManager();
		connection = dbManager.getDBConnection();
		try (Stream<Path> walk = Files.walk(Paths.get("html_docs/"))) {

					pages = walk.filter(Files::isRegularFile)
					.map(x -> x.toString()).collect(Collectors.toList());
					pagesCount = pages.size();

			pages.forEach(System.out::println);

		} catch (IOException e) {
			e.printStackTrace();
		}
		List<Thread> threads = new ArrayList<>();
		for( int i = 0; i < THREADS_COUNT && i < pagesCount; i++)
		{
			Thread t = new Thread(new Indexer(dbManager));
			t.setName(Integer.toString(i));
			threads.add(t);
			t.start();

		}

	
	}
}
