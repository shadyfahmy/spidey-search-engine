import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.tartarus.snowball.ext.EnglishStemmer;

import org.jsoup.*;
import org.jsoup.nodes.Document;

public class Indexer {

	public static void main(String args[]) throws IOException
	{
		
//		//Stopping words removal		
//		HashMap<String,Boolean> stopWords=new HashMap<String,Boolean> ();
//		 try
//		    {
//		        FileReader filereader = new FileReader("stopwords_en.txt");
//		        BufferedReader bufferedreader = new BufferedReader(filereader);
//		        String line = bufferedreader.readLine();
//		        //While we have read in a valid line
//		        while (line != null) {
//			        stopWords.put(line,true);
//		            line = bufferedreader.readLine();
//		        }
//		        bufferedreader.close();
//		    }
//		    catch(FileNotFoundException filenotfoundexception)
//		    {
//		        System.out.println("File not found.");
//		    }
//		    catch(IOException ioexception)
//		    {
//		        System.out.println("File input error occured!");
//		        ioexception.printStackTrace();
//		    }
//        
		//Read File
		File input = new File("../Crawler/Output/http-||www_gutenberg_org|wiki|gutenberg-feeds#navigation.html");
		Document doc = Jsoup.parse(input, "UTF-8", "http://example.com/");
		
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
	
	}
}
