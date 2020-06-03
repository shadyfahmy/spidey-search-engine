package PerformanceAnalysis;

import crawler.Crawler;
import database_manager.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PerformanceAnalysis implements Runnable {

    // final static Query processor
    public static final DatabaseManager dbManager = new DatabaseManager(); // database manager instance
    public final static int startNumOfThreads = 1;
    public final static int numThreadsStep = 1;
    public final static int secondsBeforeRun = 1;
    public static int numOfThreads = startNumOfThreads;
    public void run() {
        int threadNumber = Integer.valueOf(Thread.currentThread().getName());
        System.out.println("my name is " + threadNumber);
    }

    public static void main(String[] args) {
        Connection connection = dbManager.getDBConnection();
        List<Thread> threads = new ArrayList<>();
        List<Integer> numOfCrawledPagesList = new ArrayList<>();
        List<Integer> sizeOfIndexTableList = new ArrayList<>();
        List<Integer> keyWordsSizeList = new ArrayList<>();

        String searchText = "Sorting algorithms";


        // 1. How many simultaneous search requests can your solution handle?

        // 2. How is the latency of your solution affected by the number of simultaneous search requests?
        Thread t;
        int threadsCounter = 0;
        while (PerformanceAnalysis.numOfThreads > threadsCounter) {
            t = new Thread(new PerformanceAnalysis());
            t.setName(String.valueOf(threadsCounter++));
            threads.add(t);
        }

        while(true) {
            // 3. How is the search req uest latency of your solution affected by the number of web pages crawled?
            try {
                Statement stm = connection.createStatement();
                String sql = "SELECT COUNT(*) AS total FROM page;";
                ResultSet res = stm.executeQuery(sql);
                res.next();
                int numOfCrawledPages = res.getInt("total");
                numOfCrawledPagesList.add(numOfCrawledPages);
                res.close();
                stm.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            // 4. How is the search request latency of your solution affected by the size of the index table?
            try {
                Statement stm = connection.createStatement();
                String sql = "SELECT COUNT(*) AS total FROM page WHERE indexed_time IS NOT NULL;";
                ResultSet res = stm.executeQuery(sql);
                res.next();
                int sizeOfIndexTable = res.getInt("total");
                sizeOfIndexTableList.add(sizeOfIndexTable);
                res.close();
                stm.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            // 5. How is the search request latency of your solution affected by the ranking process?
            try {
                Statement stm = connection.createStatement();
                String sql = "SELECT COUNT(*) AS total FROM word;";
                ResultSet res = stm.executeQuery(sql);
                res.next();
                int keyWordsSize = res.getInt("total");
                keyWordsSizeList.add(keyWordsSize);
                res.close();
                stm.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }

            // Run threads
            for (final Thread thread : threads) {
                thread.start();
            }

            // Join threads
            for (final Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    System.out.println(
                            "                     ----------------- Error Thread has been interupted -----------------                     ");
                }
            }

            // Add new number of threads
            threads.clear();
            threadsCounter = 0;
            PerformanceAnalysis.numOfThreads += PerformanceAnalysis.numThreadsStep;
            while (PerformanceAnalysis.numOfThreads > threadsCounter) {
                t = new Thread(new PerformanceAnalysis());
                t.setName(String.valueOf(threadsCounter++));
                threads.add(t);
            }

            // Sleep if you want
            if(PerformanceAnalysis.secondsBeforeRun > 0){
                try {
                    TimeUnit.SECONDS.sleep(PerformanceAnalysis.secondsBeforeRun);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
