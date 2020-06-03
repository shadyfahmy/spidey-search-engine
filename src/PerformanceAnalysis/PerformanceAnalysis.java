package PerformanceAnalysis;

import database_manager.DatabaseManager;
import ranker.PageRanker;

import java.io.*;
import java.net.*;
import java.net.http.HttpHeaders;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PerformanceAnalysis implements Runnable {

    public static final DatabaseManager dbManager = new DatabaseManager();  // Database manager instance
    public final static int startNumOfThreads = 1;                          // could be any number
    public final static int numThreadsStep = 1;                             // could be any number
    public final static int secondsBeforeRunAgain = 0;
    public static final int connectTimeOut = 5000;
    public static final int readTimeOut = 5000;
    public static int numOfThreads = startNumOfThreads;
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
            List headerValues = entry.getValue();
            Iterator it = headerValues.iterator();
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

    private static String build_request_url(Map<String, String> parameters) throws UnsupportedEncodingException {
        StringBuilder req = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            req.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            req.append("=");
            req.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
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
            String url = PerformanceAnalysis.baseUrl + this.build_request_url(parameters);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(PerformanceAnalysis.connectTimeOut);
            connection.setReadTimeout(PerformanceAnalysis.readTimeOut);
            int status = connection.getResponseCode();
//            System.out.println("status: " + status);
            // status > 299 // error // 405 timeout error
            connection.disconnect();
        } catch (SocketTimeoutException e) {
            System.out.println("Time Out!, Thread Number: " + Thread.currentThread().getName());
            ret = true;
        } catch (IOException e) {
            System.out.println("Not Time Out!");
        }
        return ret;

//        return this.httpClient.get<any>(this.baseUrl + "/get-results?text="+text+
//                "&user="+user+"&page="+page, {headers: header})
    }

    public void run() {
//        int threadNumber = Integer.valueOf(Thread.currentThread().getName());
//        System.out.println("my name is " + threadNumber);
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
//            System.out.println("-------------------------------------------------------");
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
            System.out.println("Try simultaneous search requests = " + threads.size());

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

            if(!PerformanceAnalysis.isFailed) {
                PerformanceAnalysis.maxNumHandeledRequests = PerformanceAnalysis.numOfThreads;
            } else {
                System.out.println("Max number of simultaneous search requests = " + PerformanceAnalysis.maxNumHandeledRequests);
                break;
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
            if(PerformanceAnalysis.secondsBeforeRunAgain > 0){
                try {
                    TimeUnit.SECONDS.sleep(PerformanceAnalysis.secondsBeforeRunAgain);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
