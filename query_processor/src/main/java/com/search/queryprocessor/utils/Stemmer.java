package com.search.queryprocessor.utils;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.tartarus.snowball.ext.EnglishStemmer;

public class Stemmer {

    ArrayList<String> stopwords = new ArrayList<>();            //to store stop words

    EnglishStemmer stemmer = new EnglishStemmer();

    //read stop words and store them
    public Stemmer() {
        File f = new File("stopwords_en.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(f.getAbsolutePath()))) {
            String line;
            while ((line = br.readLine()) != null) {
                stopwords.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<String> getStemmedWords (String text) {

        String[] words = text.split("[\\s\\W]");
        ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
        ArrayList<String> impWords = new ArrayList<String>();
        int wordsListSize = wordsList.size();

        for (int i = 0; i < wordsListSize; i++) {
            String word = wordsList.get(i);
            if (stopwords.contains(word))
                continue;
            if (word.contentEquals(""))
                continue;
            String stemmedWord;

            word = word.toLowerCase();
            word = word.substring(0, Math.min(word.length(), 500));
            stemmer.setCurrent(word);
            stemmer.stem();
            stemmedWord = stemmer.getCurrent();
            impWords.add(stemmedWord);
        }

        return impWords;
    }
}
