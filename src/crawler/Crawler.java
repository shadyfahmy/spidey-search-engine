package crawler;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import database_manager.DatabaseManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler implements Runnable {
    private class UrlObject {
        public String url;
        public java.util.Date date;
        public int id;
        public UrlObject(String url, java.util.Date date, int id) {
            this.url = url;
            this.date = date;
            this.id = id;
        }
    }

    private static class UrlInDB {
        public java.util.Date date;
        public int id;
        public UrlInDB(java.util.Date date, int id) {
            this.date = date;
            this.id = id;
        }
    }

    // max number of website to crawl may be more but not less
    public static final int MAX_WEBSITES = 5000;
    private static final int EXTRA_MAX_WEBSITES_FACTOR = 5;
    private static final int EXTRA_FOR_IO_EXEPTION = MAX_WEBSITES / EXTRA_MAX_WEBSITES_FACTOR;
    public static final int TOTAL_MAX_WEBSITES_TO_QUEUE = MAX_WEBSITES + EXTRA_FOR_IO_EXEPTION;
    // user agent name
    private static final String USER_AGENT_NAME = "*";
    // start crawling time
    private final long startCrawlingTime = System.currentTimeMillis();
    // number of threads to run
    private static final int numThreads = 10;
    // seedset file for intail crawling
    public static final String seedSetFileName = "./src/crawler/SeedSet.txt";
    // folder to download the crawled pages
    public static final String outputFolderBase = "./html_docs/";

    public static final DatabaseManager dbManager = new DatabaseManager(); // database manager instance
    // formatter to GMT
    public static final SimpleDateFormat formatter= new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");

    public static final Object LOCK_LINKS_QUEUE = new Object();                                 // linksQueue lock
    public static final Object LOCK_VISITED_SET = new Object();                                 // visitedSet Lock
    private static final Object LOCK_RECRAWLING_QUEUE = new Object();                           // linksQueue lock
    public static final Object LOCK_LINKS_DB_FAIL = new Object();                               // fail list lock
    public static List<String> failedLinksList = new ArrayList<>();                             // linksQueue lock
    public static Queue<String> linksQueue = new LinkedList<>();                                // linksQueue lock
    public static HashMap<String, UrlInDB> visitedLinks = new HashMap<>();       // visited links list

    private static Queue<UrlObject> recrawlingQueue = new LinkedList<>();                       // recrawling urls queue
    // array of connections every thread has a connection with the sql server
    private static List<Connection> connections = new ArrayList<>();
    // blocked extensions from crawling
    private final List<String> extensions = new ArrayList<>() {
        {
            // videos
            add(".gif");
            add(".gifv");
            add(".mp4");
            add(".webm");
            add(".mkv");
            add(".flv");
            add(".vob");
            add(".ogv");
            add(".ogg");
            add(".avi");
            add(".mts");
            add(".m2ts");
            add(".ts");
            add(".mov");
            add(".qt");
            add(".wmv");
            add(".yuv");
            add(".rm");
            add(".rmvb");
            add(".asf");
            add(".amv");
            add(".m4p");
            add(".m4v");
            add(".mpg");
            add(".mp2");
            add(".mpeg");
            add(".mpe");
            add(".mpv");
            add(".m2v");
            add(".m4v");
            add(".svi");
            add(".3gp");
            add(".3g2");
            add(".mxf");
            add(".roq");
            add(".nsv");
            add(".f4v");
            // images
            add(".png");
            add(".jpg");
            add(".webp");
            add(".tiff");
            add(".psd");
            add(".raw");
            add(".bmp");
            add(".heif");
            add(".indd");
            add(".jp2");
            add(".svg");
            add(".ai");
            add(".eps");
            // add(".pdf"); // not html
            // add(".ppt"); // not html
        }
    };

    public Crawler() {}

    /* if url end of one of the blocked extensions return true otherwise false */
    private boolean is_url_end_with_extensions(String url) {
        return extensions.stream().anyMatch(entry -> url.endsWith(entry));
    }

    /* dequeue url from state table */
    private boolean dequeue_state(Connection connection) {
        try {
            String query = "DELETE FROM state LIMIT 1;";
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
            return true;
        } catch (SQLException e) {
            e.getMessage();
            return false;
        }
    }

    /* enqueue url to state table */
    private boolean enqueue_state(Connection connection, String url) {
        try{
            String query = String.format("INSERT INTO state (url) VALUES ('%s');", url);
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
            return true;
        } catch (SQLException e) {
            e.getMessage();
            synchronized (Crawler.LOCK_LINKS_DB_FAIL) {
                failedLinksList.add(url);
            }
            return false;
        }
    }

    /* enqueue list of urls to state table */
    private boolean enqueue_multiple_links(Connection connection, List<String> urls) {
        try {
            String query = "INSERT INTO state (url) VALUES (?);";
            PreparedStatement pst = connection.prepareStatement(query);
            connection.setAutoCommit(false);
            for (String url : urls) {
                pst.setString(1, url);
                pst.addBatch();
            }
            pst.executeLargeBatch();
            connection.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            e.getMessage();
            failedLinksList.addAll(urls);
            return false;
        }
    }

    private boolean clear_state(Connection connection) {
        try{
            String query = "DELETE FROM state;";
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
            return true;
        } catch (SQLException e){
            e.getMessage();
            return false;
        }
    }

    private boolean is_allowed_url(Connection connection, String url) {
        try {
            if(is_url_end_with_extensions(url)) {
                return false;
            }
            boolean flagAlreadyVisited = false;
            // not necessary but instead of parsing robot.txt for already visited link
            synchronized (Crawler.LOCK_VISITED_SET) {
                if (Crawler.visitedLinks.containsKey(url)) {
                    flagAlreadyVisited = true;
                }
            }
            if(flagAlreadyVisited) {
                return false;
            }
            URI uri = new URI(url);
            uri = uri.normalize();
            String path = uri.getPath();
            // System.out.println(path);
            String rootPath = url.replace(path, "");
            String robotPath = rootPath + "/robots.txt";
            // System.out.println(robotPath);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(robotPath).openStream()))) {
                String line;
                boolean flag = false;
                final String disallowStartWith = "disallow:";
                final String userAgentStartWith = "user-agent:";
                while ((line = in.readLine()) != null) {
                    int offset = line.indexOf("#");
                    if (offset != -1) {
                        line = line.substring(0, offset);
                    }
                    line = line.replaceAll(" ", "").replace("\n", "").toLowerCase();
                    // System.out.println(line);
                    if (flag) {
                        if (line.startsWith(disallowStartWith)) {
                            offset = line.indexOf(":");
                            if (-1 != offset && ++offset < line.length()) {
                                String disallowedValue;
                                if (line.charAt(line.length() - 1) == '/') {
                                    disallowedValue = line.substring(offset, line.length() - 1);
                                } else {
                                    disallowedValue = line.substring(offset);
                                }
                                String disallowedURL = rootPath + disallowedValue;
                                // System.out.println(disallowedURL);
                                if (url.length() >= disallowedURL.length()
                                        && disallowedURL.equals(url.substring(0, disallowedURL.length()))) {
                                    System.out.println(
                                            "_________________________________________________________________________________________");
                                    System.out.println("(Robot.txt): Not allowed to crawl: " + url);
                                    System.out.println(
                                            "_________________________________________________________________________________________");
                                    return false;
                                }
                            }
                        } else if (line.startsWith(userAgentStartWith)) {
                            break;
                        }
                    } else if (line.startsWith(userAgentStartWith)) {
                        if (userAgentStartWith.length() < line.length()) {
                            if (line.substring(userAgentStartWith.length()).equals(Crawler.USER_AGENT_NAME)) {
                                // System.out.println("FOUND: " + line);
                                flag = true;
                            }
                        }
                    }
                }
                return true;
            }
        } catch (IOException ex1) {
                synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                    if(this.enqueue_state(connection, url)) {
                        Crawler.linksQueue.add(url);
                    }
                }
            return false;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String normalizeUrl(String urlStr) {
        if (urlStr == null) {
            return null;
        }
        String path = urlStr;
        if (path != null) {
            String http = "http:/";
            String https = "https:/";
            // http:////wWw.Fb.cOm////
            path = path.replaceAll("//*/", "/"); // http:/wWw.Fb.cOm/
            if (path.startsWith(http)) {
                path = path.substring(http.length()); // wWw.Fb.cOm/
            } else if (path.startsWith(https)) {
                path = path.substring(https.length());
            }
            path = http + "/" + path; // http://wWw.Fb.cOm/
            if (path.charAt(path.length() - 1) == '/') {
                path = path.substring(0, path.length() - 1);
            }
            // http://wWw.Fb.cOm
            path = path.toLowerCase();
            // http://www.fb.com
        }
        return path;
    }

    public void recrawling(Connection connection) {
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts recrawling");
        synchronized (Crawler.LOCK_RECRAWLING_QUEUE) {
            if (Crawler.recrawlingQueue.isEmpty()) {
                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                    Crawler.linksQueue.clear(); // clear in the start to make sure nothing there
                    clear_state(connection);
                }
                synchronized (Crawler.LOCK_VISITED_SET) {
                    Crawler.visitedLinks.entrySet().forEach(entry -> {
                        Crawler.recrawlingQueue.add(new UrlObject(entry.getKey(), entry.getValue().date, entry.getValue().id));
                    });
                }
            }
        }
        while (true) {
            UrlObject crawledURL = null;
            // start lock
            boolean flagEmptyQueue = false;
            // start lock
            synchronized (Crawler.LOCK_RECRAWLING_QUEUE) {
                if (!Crawler.recrawlingQueue.isEmpty()) {
                    crawledURL = Crawler.recrawlingQueue.poll();
                } else {
                    flagEmptyQueue = true;
                }
            }
            // end lock
            if(flagEmptyQueue) {
                break;
            }
            try {
                if(crawledURL == null) {
                    continue;
                }
                final URL url = new URL(crawledURL.url);
                try {
                    System.out.println("Time: " + (System.currentTimeMillis() - this.startCrawlingTime)
                            + ", recrawling url : " + url);
                    URLConnection connectionHead = url.openConnection();
                    String lastModified = connectionHead.getHeaderField("Last-Modified");
                    boolean isChanged = false;
                    java.util.Date lastModifiedDate = null;
                    java.util.Date downloadDate = Crawler.visitedLinks.get(crawledURL.url).date;
                    if(lastModified != null) {
                        lastModifiedDate = Crawler.formatter.parse(lastModified);
                        isChanged = lastModifiedDate.after(downloadDate);
                    }
                    final int delay = 3600000; // ms
                    java.util.Date dateToRecrawl = new Date(System.currentTimeMillis() - delay);
                    // if lastModifiedDate is null update
                    if(isChanged || (lastModifiedDate == null && downloadDate.before(dateToRecrawl))) {
                        Document urlContent = save_url_to_db(connection, url.toString(), crawledURL.id);
                        if(urlContent != null) {
                            System.out.println(
                                    "_________________________________________________________________________________________");
                            System.out.println("Thread (" + Thread.currentThread().getName() + "): " + crawledURL
                                    + " is now added to output folder");
                            System.out.println(
                                    "_________________________________________________________________________________________");
                            final Elements linksFound = urlContent.select("a[href]");
                            List<String> urls = new ArrayList<>();
                            for (final Element link : linksFound) {
                                final String urlText = link.attr("abs:href");
                                String path = normalizeUrl(urlText); // URL Normalization
                                if (this.is_allowed_url(connection, path)) {
                                    urls.add(path);
                                }
                            }
                            if(urls.size() > 0) {
                                // start lock
                                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                    if(this.enqueue_multiple_links(connection, urls)) {
                                        Crawler.linksQueue.addAll(urls);
                                    }
                                }
                                // end lock
                            }
                        } else {
                            /* delete from db */
                            // delete_from_db(connection, crawledURL.url, crawledURL.id);
                            /* delete from HDD */
                            // File toDeleteFile = new File(outputFolderBase+crawledURL.id+".html");
                            // toDeleteFile.delete();
                        }
                    }
                } catch (IOException e) {
                    synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                        Crawler.recrawlingQueue.add(crawledURL);
                    }
                    System.out.println(
                            "_________________________________________________________________________________________");
                    System.out.println("Error IO-Exception while crawling : " + crawledURL);
                    System.out.println(
                            "_________________________________________________________________________________________");
                } catch (ParseException e){
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                System.out.println(
                        "_________________________________________________________________________________________");
                System.out.println("Error Mal-Formed-URL while crawling : " + crawledURL);
                System.out.println(
                        "_________________________________________________________________________________________");
            }
        }
    }

    public void crawling(Connection connection){
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts crawling");
        while (true) {
            String crawledURL = "";
            // start lock
            boolean flagReachedToMax = false;
            synchronized (Crawler.LOCK_VISITED_SET) {
                if (visitedLinks.size() >= MAX_WEBSITES) {
                    flagReachedToMax = true;
                }
            }
            if (flagReachedToMax) {
                return;
            }
            // end lock
            boolean flagAlreadyVisited = false;
            boolean flagEmptyQueue = false;
            // start lock
            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                if (!Crawler.linksQueue.isEmpty()) {
                    if(dequeue_state(connection)) {
                        crawledURL = Crawler.linksQueue.poll();
                        synchronized (Crawler.LOCK_VISITED_SET) {
                            if (Crawler.visitedLinks.containsKey(crawledURL)) {
                                flagAlreadyVisited = true;
                            }
                        }
                    } else {
                        flagAlreadyVisited = true;
                    }
                } else {
                    System.out.println("Thread (" + Thread.currentThread().getName() + "): Empty Queue List");
                    flagEmptyQueue = true;
                }
            }
            if (flagAlreadyVisited || flagEmptyQueue) {
                continue;
            }
            // end lock
            try {
                final URL url = new URL(crawledURL);
                try {
                    System.out.println("Time: " + (System.currentTimeMillis() - this.startCrawlingTime)
                            + ", crawling url : " + url);
                    Document urlContent = save_url_to_db(connection, url.toString(), -1);
                    if(urlContent != null) {
                        System.out.println("Thread (" + Thread.currentThread().getName() + "): " + crawledURL
                                + " is now added to output folder");
                        int remainingLinksCount;
                        synchronized (Crawler.LOCK_LINKS_QUEUE) {
                            synchronized (Crawler.LOCK_VISITED_SET) {
                                remainingLinksCount = Crawler.TOTAL_MAX_WEBSITES_TO_QUEUE - (Crawler.visitedLinks.size() + Crawler.linksQueue.size());
                            }
                        }
                        if (remainingLinksCount <= 0) {
                            continue; // continue downloading pages
                        }
                        List<String> urls = new ArrayList<>();

                        final Elements linksFound = urlContent.select("a[href]");

                        for (final Element link : linksFound) {
                            final String urlText = link.attr("abs:href");
                            // start lock
                            String path = normalizeUrl(urlText); // URL Normalization
                            if (this.is_allowed_url(connection, path)) {
                                urls.add(path);
                                remainingLinksCount--;
                            }
                            if(remainingLinksCount == 0){
                                break;
                            }
                        }

                        if(urls.size() > 0) {
                            // start lock
                            synchronized (Crawler.LOCK_LINKS_QUEUE) { // make sure before add to db
                                synchronized (Crawler.LOCK_VISITED_SET) {
                                    remainingLinksCount = Crawler.TOTAL_MAX_WEBSITES_TO_QUEUE - (Crawler.linksQueue.size() + Crawler.visitedLinks.size());
                                }
                            }
                            // end lock
                            if(remainingLinksCount <= 0) {
                                continue;
                            }

                            if(urls.size() > remainingLinksCount) {
                                urls = urls.subList(0, remainingLinksCount);
                            }

                            // start lock
                            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                if(this.enqueue_multiple_links(connection, urls)) {
                                    Crawler.linksQueue.addAll(urls);
                                }
                            }
                            // end lock
                        }
                    }
                } catch (IOException e) {
                    synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                        if(this.enqueue_state(connection, crawledURL)) {
                            Crawler.linksQueue.add(crawledURL);
                        }
                    }
                    System.out.println(
                            "_________________________________________________________________________________________");
                    System.out.println("Error IO-Exception while crawling : " + crawledURL);
                    System.out.println(
                            "_________________________________________________________________________________________");
                }
            } catch (MalformedURLException e) {
                System.out.println(
                        "_________________________________________________________________________________________");
                System.out.println("Error Mal-Formed-URL while crawling : " + crawledURL);
                System.out.println(
                        "_________________________________________________________________________________________");
            }
        }
    }


    public void crawlUpdatedLinks(Connection connection){
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts crawl updated links");
        while (true) {
            String crawledURL = "";
            boolean flagVisitedLink = false, flagEmptyQueue = false;
            // start lock
            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                if (!Crawler.linksQueue.isEmpty()) {
                    if(dequeue_state(connection)) {
                        crawledURL = Crawler.linksQueue.poll();
                        synchronized (Crawler.LOCK_VISITED_SET) {
                            if (Crawler.visitedLinks.containsKey(crawledURL)) {
                                flagVisitedLink = true;
                            }
                        }
                    } else {
                        flagVisitedLink = true;
                    }
                } else {
                    System.out.println("Thread (" + Thread.currentThread().getName() + "): Empty Queue List");
                    flagEmptyQueue = true;
                }
            }
            if (flagVisitedLink) {
                continue; // get new one from queue
            }
            if (flagEmptyQueue) {
                break;
            }
            // end lock
            try {
                final URL url = new URL(crawledURL);
                try {
                    System.out.println("Time: " + (System.currentTimeMillis() - this.startCrawlingTime)
                            + ", crawling new url in recrawling: " + url);
                    Document urlContent = save_url_to_db(connection, url.toString(), -1);
                } catch (IOException e) {
//                    synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
//                        if(this.enqueue_state(connection, crawledURL)) {
//                            Crawler.linksQueue.add(crawledURL);
//                        }
//                    }
                    System.out.println(
                            "_________________________________________________________________________________________");
                    System.out.println("Error IO-Exception while crawling new Recrawled links: " + crawledURL);
                    System.out.println(
                            "_________________________________________________________________________________________");
                }
            } catch (MalformedURLException e) {
                System.out.println(
                        "_________________________________________________________________________________________");
                System.out.println("Error Mal-Formed-URL while crawling new Recrawled links : " + crawledURL);
                System.out.println(
                        "_________________________________________________________________________________________");
            }
        }
    }

    public Document save_url_to_db(Connection connection, String url, int updateId) throws IOException{
        Document urlContent = null;
        try {
            urlContent = Jsoup.connect(url).get();
            Date date = new Date(System.currentTimeMillis());
            String query;
            if(updateId != -1) { // update
                query = String.format("UPDATE page SET url='%s',  crawled_time='%s' WHERE id=%d;", url, Crawler.formatter.format(date), updateId);
            }
            else{ // insert
                query = String.format("INSERT INTO page (url, crawled_time) VALUES ('%s', '%s');", url, Crawler.formatter.format(date));
            }
            Statement stmt = connection.createStatement();
            synchronized (Crawler.LOCK_VISITED_SET) {
                if(!Crawler.visitedLinks.containsKey(url)) {
                    int rowsAffected = stmt.executeUpdate( query, Statement.RETURN_GENERATED_KEYS );
                    ResultSet rs = stmt.getGeneratedKeys();
                    rs.beforeFirst();
                    rs.next();
                    int id = updateId != -1? updateId:rs.getInt(1);
                    stmt.close();
                    // System.out.println("ID: "+ id);
                    BufferedWriter writer = new BufferedWriter(new FileWriter(Crawler.outputFolderBase + id + ".html"));
                    writer.write(urlContent.toString());
                    writer.close();
                    Crawler.visitedLinks.put(url, new UrlInDB(date, id));
                } else {
                    urlContent = null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return urlContent;
    }

    public void delete_from_db(Connection connection, String url, int deleteId) {
        try {
            String query = String.format("DELETE FROM page WHERE id=%d;", deleteId);
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
            synchronized (Crawler.LOCK_VISITED_SET) {
                Crawler.visitedLinks.remove(url);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handel_falied_inserted_urls(Connection connection) {
        // Handeling not inserted in db urls
        // I chose to crawl them
        while(failedLinksList.size() > 0){
            synchronized (Crawler.LOCK_LINKS_DB_FAIL) {
                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                    if (enqueue_multiple_links(connection, failedLinksList)) {
                        linksQueue.addAll(failedLinksList);
                        failedLinksList.clear();
                    }
                }
            }
            crawlUpdatedLinks(connection);
        }
    }

    public void run() {
        int threadNumber = Integer.valueOf(Thread.currentThread().getName());
        Connection connection = Crawler.connections.get(threadNumber);
        crawling(connection); // before recrawling just crawl new websites
        handel_falied_inserted_urls(connection);
        while(true){
            recrawling(connection);
            crawlUpdatedLinks(connection); // After recrawling, this function crawls new fetched urls in updated websites
            handel_falied_inserted_urls(connection);
        }
    }

    public static void main(String[] args) {
        Crawler.formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        boolean isRecrawling = false;
        try {
            String query = "SELECT * FROM page;";
            Connection connection = dbManager.getDBConnection();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(query);
            System.out.println(result);
            while (result.next()) {
                String url = result.getString("url");
                String crawled_time = result.getString("crawled_time");
                int id = result.getInt("id");
                java.util.Date crawledDate = Crawler.formatter.parse(crawled_time);
                Crawler.visitedLinks.put(url, new UrlInDB(crawledDate, id));
            }
            result.close();
            statement.close();
            connection.close();
            // check if in recrawling mode first // if yes don't load queue list
            isRecrawling = (Crawler.visitedLinks.size() >= Crawler.MAX_WEBSITES);
            if(!isRecrawling) {
                query = "SELECT * FROM state;";
                connection = dbManager.getDBConnection();
                statement = connection.createStatement();
                result = statement.executeQuery(query);
                System.out.println(result);
                while (result.next()) {
                    String url = result.getString("url");
                    Crawler.linksQueue.add(url);
                }
                result.close();
                statement.close();
                connection.close();
                if (Crawler.linksQueue.isEmpty()) {
                    System.out.println("QUEUE EMPTY!!");
                    File seedSetFile = new File(Crawler.seedSetFileName);
                    Scanner seedSetScanner = new Scanner(seedSetFile);
                    while (seedSetScanner.hasNextLine()) {
                        Crawler.linksQueue.add(seedSetScanner.nextLine());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            try {
                System.out.println("Can not open Queue file, QUEUE EMPTY!!, Loading Seed Set");
                if (Crawler.linksQueue.isEmpty()) {
                    System.out.println("QUEUE EMPTY!!");
                    File seedSetFile = new File(Crawler.seedSetFileName);
                    Scanner seedSetScanner = new Scanner(seedSetFile);
                    while (seedSetScanner.hasNextLine()) {
                        Crawler.linksQueue.add(seedSetScanner.nextLine());
                    }
                }
            } catch (FileNotFoundException ex) {
                System.out.println("Can not open seed seed file, exit program ... ");
                System.exit(-1);
            }
        } catch (SQLException e) {
            System.out.println("Can not connect to Data base or parsing Date error ... ");
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        int counter = 0;
        List<Thread> threads = new ArrayList<>();
        Thread t;
        while (Crawler.numThreads > counter) {
            t = new Thread(new Crawler());
            t.setName(String.valueOf(counter++));
            threads.add(t);
            Connection connection = dbManager.getDBConnection();
            Crawler.connections.add(connection);
            System.out.println("Thread " + t.getName() + " is created");
        }

        // start threads
        for (final Thread thread : threads) {
            int count = 0;
            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                if(!isRecrawling) {
                    count = Crawler.linksQueue.size();
                    if (count >= 1) {
                        thread.start();
                    }
                } else {
                    thread.start();
                }
            }
            try {
                if (count <= 1) {
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (InterruptedException e) {
                System.out.println(
                        "                     ----------------- Error interupted while sleeping -----------------                     ");
            }
        }
        // join threads
        for (final Thread thread : threads) {
            try {
                thread.join();
                int threadNumber = Integer.valueOf(Thread.currentThread().getName());
                Connection connection = Crawler.connections.get(threadNumber);
                connection.close();
            } catch (InterruptedException | SQLException e) {
                System.out.println(
                        "                     ----------------- Error Thread has been interupted -----------------                     ");
            }
        }
    }
}
