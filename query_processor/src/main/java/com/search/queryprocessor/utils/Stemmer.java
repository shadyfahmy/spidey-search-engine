package com.search.queryprocessor.utils;


import java.util.ArrayList;
import java.util.Arrays;
import org.tartarus.snowball.ext.EnglishStemmer;

public class Stemmer {

    EnglishStemmer stemmer = new EnglishStemmer();

    public ArrayList<String> getStemmedWords (String text) {

        String[] words = text.split("[\\s\\W]");
        ArrayList<String> wordsList = new ArrayList<String>(Arrays.asList(words));
        ArrayList<String> impWords = new ArrayList<String>();
        int wordsListSize = wordsList.size();

        for (int i = 0; i < wordsListSize; i++) {
            String word = wordsList.get(i);
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
