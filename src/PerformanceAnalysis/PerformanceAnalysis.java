package PerformanceAnalysis;

import crawler.Crawler;
import database_manager.DatabaseManager;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class PerformanceAnalysis implements Runnable {

    public static final DatabaseManager dbManager = new DatabaseManager();  // Database manager instance
    public final static int START_NUM_OF_THREADS = 50;                     // could be any number
    public final static int NUM_THREAD_STEP = 10;                           // could be any number
    public final static int SECONDS_BEFORE_LOAD_TEST = 1;
    public final static int SECONDS_TO_SLEEP = 60;
    public static final int CONNECT_TIME_OUT = 5000;
    public static final int READ_TIME_OUT = 5000;
    public static int numOfThreads = START_NUM_OF_THREADS;
    public static int maxNumHandeledRequests = 0;
    private static final String baseUrl = "http://localhost:8080/api/v1/get-results?";
    private static final String searchText = "Sorting algorithms";
    private static final int pageNum = 1;
    private static boolean isFailed = false;
    public static final Object LOCK_IS_FAILED = new Object();

    public static String get_full_response(HttpURLConnection connection) throws IOException {
        StringBuilder resBuilder = new StringBuilder();
        resBuilder.append(connection.getResponseCode()).append(" ").append(connection.getResponseMessage()).append("\n");
        connection.getHeaderFields().entrySet().stream().filter(entry -> entry.getKey() != null).forEach(entry -> {
            resBuilder.append(entry.getKey()).append(": ");
            List <String> headerValues = entry.getValue();
            Iterator <String> it = headerValues.iterator();
            if (it.hasNext()) {
                resBuilder.append(it.next());
                while (it.hasNext()) {
                    resBuilder.append(", ").append(it.next());
                }
            }
            resBuilder.append("\n");
        });
        return resBuilder.toString();
    }

    private static String build_request_url(Map<String, String> parameters) {
        StringBuilder req = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            req.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            req.append("=");
            req.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            req.append("&");
        }
        String reqStr = req.toString();
        return reqStr.length() > 0 ? reqStr.substring(0, reqStr.length() - 1) : reqStr;
    }

    public boolean get_results() {
        // http://localhost:8080/api/v1/get-results?text=Sorting+algorithms&page=1&user=0
        boolean ret = false;
        try {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("text", PerformanceAnalysis.searchText);
            parameters.put("user", "1");
            parameters.put("page", String.valueOf(PerformanceAnalysis.pageNum));
            String url = PerformanceAnalysis.baseUrl + build_request_url(parameters);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(PerformanceAnalysis.CONNECT_TIME_OUT);
            connection.setReadTimeout(PerformanceAnalysis.READ_TIME_OUT);
            int status = connection.getResponseCode();
            // System.out.println("status: " + status);
            // status > 299 is an error and 405 is a timeout error
            connection.disconnect();
        } catch (SocketTimeoutException e) {
            System.out.println("Time Out!, Thread Number: " + Thread.currentThread().getName());
            ret = true;
        } catch (IOException e) {
            System.out.println("Not Time Out!");
        }
        return ret;
    }

    public void run() {
        boolean isFailed = get_results();
        synchronized (PerformanceAnalysis.LOCK_IS_FAILED) {
            if (isFailed) {
                PerformanceAnalysis.isFailed = true;
            }
        }
    }

    public static void main(String[] args) {
        Connection connection = dbManager.getDBConnection();
        List<Thread> threads = new ArrayList<>();
        List<Integer> numOfCrawledPagesList = new ArrayList<>();
        List<Integer> sizeOfIndexTableList = new ArrayList<>();
        List<Integer> keyWordsSizeList = new ArrayList<>();
        List<Double> latencyList = new ArrayList<>();

        int numOfCrawledPages , sizeOfIndexTable = 0;
        while(true) {
            // 3. How is the search request latency of your solution affected by the number of web pages crawled?
            try {
                Statement stm = connection.createStatement();
                String sql = "SELECT COUNT(*) AS total FROM page;";
                ResultSet res = stm.executeQuery(sql);
                res.next();
                numOfCrawledPages = res.getInt("total");
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
                sizeOfIndexTable = res.getInt("total");
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

            Thread t1 = new Thread(new PerformanceAnalysis());
            long start = System.currentTimeMillis();
            t1.start();
            try {
                t1.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long end = System.currentTimeMillis();
            long elapsedTime = end - start;
            latencyList.add((double)elapsedTime);
            if(Crawler.MAX_WEBSITES <= sizeOfIndexTable) {
                break;
            }
            try {
                TimeUnit.SECONDS.sleep(PerformanceAnalysis.SECONDS_TO_SLEEP);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 3. How is the search request latency of your solution affected by the number of web pages crawled?
        if(numOfCrawledPagesList.size() > 0 && latencyList.size() == numOfCrawledPagesList.size()) {
            SwingUtilities.invokeLater(() -> {
                XYLineChart chart = new XYLineChart("Request latency vs Number Of Crawled Pages",
                        numOfCrawledPagesList, latencyList, "Number Of Crawled Pages",
                        "Latency In ms","latency_vs_crawled_num");
                chart.setSize(800, 400);
                chart.setLocationRelativeTo(null);
                chart.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                chart.setVisible(true);
            });
        }

        // 4. How is the search request latency of your solution affected by the size of the index table?
        if(sizeOfIndexTableList.size() > 0 && latencyList.size() == sizeOfIndexTableList.size()) {
            SwingUtilities.invokeLater(() -> {
                XYLineChart chart = new XYLineChart("Request latency vs Size Of Indexed Table",
                        sizeOfIndexTableList, latencyList, "Size Of Indexed Table",
                        "Latency In ms", "latency_vs_indexed_num");
                chart.setSize(800, 400);
                chart.setLocationRelativeTo(null);
                chart.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                chart.setVisible(true);
            });
        }

        // 5. How is the search request latency of your solution affected by the ranking process?
        if(keyWordsSizeList.size() > 0 && latencyList.size() == keyWordsSizeList.size()) {
            SwingUtilities.invokeLater(() -> {
                XYLineChart chart = new XYLineChart("Request latency vs Number Of KeyWords",
                        keyWordsSizeList, latencyList, "Number Of KeyWords",
                        "Latency In ms", "latency_vs_num_keywords");
                chart.setSize(800, 400);
                chart.setLocationRelativeTo(null);
                chart.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                chart.setVisible(true);
            });
        }

        Thread t;
        int threadsCounter = 0;
        while (PerformanceAnalysis.numOfThreads > threadsCounter) {
            t = new Thread(new PerformanceAnalysis());
            t.setName(String.valueOf(threadsCounter++));
            threads.add(t);
        }

        List <Double> newLatencyList = new ArrayList<>();
        List<Integer> numOfSearchReqList = new ArrayList<>();
        numOfSearchReqList.add(PerformanceAnalysis.numOfThreads);
        // 1. How many simultaneous search requests can your solution handle?
        while(true) {
            System.out.println("Try simultaneous search requests = " + threads.size());
            // Run threads
            long start = System.currentTimeMillis();
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
            long end = System.currentTimeMillis();
            long elapsedTime = end - start;
            newLatencyList.add((double)elapsedTime);
            if(!PerformanceAnalysis.isFailed) {
                PerformanceAnalysis.maxNumHandeledRequests = PerformanceAnalysis.numOfThreads;
            } else {
                System.out.println("-----------------------------------------------------------------");
                System.out.println("Max number of simultaneous search requests = " + PerformanceAnalysis.maxNumHandeledRequests);
                System.out.println("-----------------------------------------------------------------");
                break;
            }

            // Add new number of threads
            threads.clear();
            threadsCounter = 0;
            PerformanceAnalysis.numOfThreads += PerformanceAnalysis.NUM_THREAD_STEP;
            numOfSearchReqList.add(PerformanceAnalysis.numOfThreads);
            while (PerformanceAnalysis.numOfThreads > threadsCounter) {
                t = new Thread(new PerformanceAnalysis());
                t.setName(String.valueOf(threadsCounter++));
                threads.add(t);
            }

            // Sleep if you want
            if(PerformanceAnalysis.SECONDS_BEFORE_LOAD_TEST > 0){
                try {
                    TimeUnit.SECONDS.sleep(PerformanceAnalysis.SECONDS_BEFORE_LOAD_TEST);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // 2. How is the latency of your solution affected by the number of simultaneous search requests?
        if(numOfSearchReqList.size() > 0 && newLatencyList.size() == numOfSearchReqList.size()) {
            SwingUtilities.invokeLater(() -> {
                XYLineChart chart = new XYLineChart("Request latency vs Number Of Simultaneous Search Requests",
                        numOfSearchReqList, newLatencyList, "Number Of Simultaneous Search Requests",
                        "Latency In ms", "latency_vs_sim_num_requests");
                chart.setSize(800, 400);
                chart.setLocationRelativeTo(null);
                chart.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                chart.setVisible(true);
            });
        }

    }
}
