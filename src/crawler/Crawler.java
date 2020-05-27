package crawler;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    };

    private static class UrlInDB {
        public java.util.Date date;
        public int id;
        public UrlInDB(java.util.Date date, int id) {
            this.date = date;
            this.id = id;
        }
    };

    public static final int MAX_WEBSITES = 5000;
    private final long startCrawlingTime = System.currentTimeMillis();
    private static final int numThreads = 10;
    public static final String seedSetFileName = "./src/crawler/SeedSet.txt";
    public static final String outputFolderBase = "./html_docs/";

    public static final DatabaseManager dbManager = new DatabaseManager();
    public static final SimpleDateFormat formatter= new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");

    public static final Object LOCK_LINKS_QUEUE = new Object();
    public static final Object LOCK_VISITED_SET = new Object();
    private static final Object LOCK_RECRAWLING_QUEUE = new Object();
    public static Queue<String> linksQueue = new LinkedList<>();
    public static HashMap<String, UrlInDB> visitedLinks = new HashMap<String, UrlInDB>();
    private static Queue<UrlObject> recrawlingQueue = new LinkedList<>();
    private static List<Connection> connections = new ArrayList<>();

    public Crawler() {
    }

    private void dequeue_state(Connection connection) {
        try {
            String query = String.format("DELETE FROM state LIMIT 1;");
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void enqueue_state(Connection connection, String url) {
        try {
            String query = String.format("INSERT INTO state (url) VALUES ('%s');", url);
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void clear_state(Connection connection) {
        try {
            String query = String.format("DELETE FROM state;");
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Boolean isAllowedURL(Connection connection, String url) {
        try {
            URI uri = new URI(url);
            uri = uri.normalize();
            String path = uri.getPath();
            // System.out.println(path);
            String rootPath = url.replace(path, "");
            String robotPath = rootPath + "/robots.txt";
            // System.out.println(robotPath);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(robotPath).openStream()))) {
                String line = null;
                boolean flag = false;
                final String disallowStartWith = "disallow:";
                final String userAgentStartWith = "user-agent:";
                final String userAgentName = "*";
                while ((line = in.readLine()) != null) {
                    int offset = line.indexOf("#");
                    if (-1 != offset) {
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
                            if (line.substring(userAgentStartWith.length()).equals(userAgentName)) {
                                // System.out.println("FOUND: " + line);
                                flag = true;
                            }
                        }
                    }
                }
                return true;
            }
        } catch (IOException e) {
            synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                Crawler.linksQueue.add(url);
                enqueue_state(connection, url);
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String normalizeUrl(String urlStr) {
        if (urlStr == null) {
            return null;
        }
        String path = new String(urlStr);
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
                    Boolean isChanged = false;
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
                            for (final Element link : linksFound) {
                                final String urlText = link.attr("abs:href");
                                String path = normalizeUrl(urlText); // URL Normalization
                                if (this.isAllowedURL(connection, path)) {
                                    synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                        Crawler.linksQueue.add(path);
                                        enqueue_state(connection, path);
                                    }
                                }
                            }
                        } else {
                            // delete from db
                            delete_from_db(connection, crawledURL.url, crawledURL.id);
                            // delete from HDD
                            File toDeleteFile = new File(outputFolderBase+crawledURL.id+".html");
                            toDeleteFile.delete();
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
                    crawledURL = Crawler.linksQueue.poll();
                    dequeue_state(connection);
                    synchronized (Crawler.LOCK_VISITED_SET) {
                        if (Crawler.visitedLinks.containsKey(crawledURL)) {
                            flagAlreadyVisited = true;
                        }
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
                        System.out.println(
                                "_________________________________________________________________________________________");
                        System.out.println("Thread (" + Thread.currentThread().getName() + "): " + crawledURL
                                + " is now added to output folder");
                        System.out.println(
                                "_________________________________________________________________________________________");
                        // end lock
                        final Elements linksFound = urlContent.select("a[href]");
                        for (final Element link : linksFound) {
                            final String urlText = link.attr("abs:href");
                            // start lock
                            String path = normalizeUrl(urlText); // URL Normalization
                            int sizeQueue;
                            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                sizeQueue = Crawler.linksQueue.size();
                            }
                            boolean breakFlag = false;
                            synchronized (Crawler.LOCK_VISITED_SET) {
                                if (Crawler.visitedLinks.size() + sizeQueue >= MAX_WEBSITES) {
                                    breakFlag = true;
                                }
                            }
                            if(breakFlag){
                                break;
                            }
                            if (this.isAllowedURL(connection, path)) {
                                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                    Crawler.linksQueue.add(path);
                                    enqueue_state(connection, path);
                                }
                            }
                            // end lock
                        }
                    }
                } catch (IOException e) {
                    synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                        Crawler.linksQueue.add(crawledURL);
                        enqueue_state(connection, crawledURL);
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
                    crawledURL = Crawler.linksQueue.poll();
                    dequeue_state(connection);
                    synchronized (Crawler.LOCK_VISITED_SET) {
                        if (Crawler.visitedLinks.containsKey(crawledURL)) {
                            flagVisitedLink = true;
                        }
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
                    synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                        Crawler.linksQueue.add(crawledURL);
                        enqueue_state(connection, crawledURL);
                    }
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
            urlContent = Jsoup.connect(url.toString()).get();
            Date date = new Date(System.currentTimeMillis());
            String query = "";
            if(updateId != -1) { // update
                query = String.format("UPDATE page SET url='%s',  crawled_time='%s' WHERE id=%d;", url, Crawler.formatter.format(date), updateId);
            }
            else{ // insert
                query = String.format("INSERT INTO page (url, crawled_time) VALUES ('%s', '%s');", url, Crawler.formatter.format(date));
            }
            Statement stmt = connection.createStatement();
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
            synchronized (Crawler.LOCK_VISITED_SET) {
                Crawler.visitedLinks.put(url, new UrlInDB(date, id));
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

    public void run() {
        int threadNumber = Integer.valueOf(Thread.currentThread().getName());
        Connection connection = Crawler.connections.get(threadNumber);
        crawling(connection); // before recrawling just crawl new websites
        while(true){
            recrawling(connection);
            crawlUpdatedLinks(connection); // After recrawling, this function crawls new fetched urls in updated websites
        }
    }

    public static void main(String[] args) {
        Crawler.formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        boolean isRecrawling = false;
        try {
            String query = String.format("SELECT * FROM page;");
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
                query = String.format("SELECT * FROM state;");
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
