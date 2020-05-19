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
    public static final String linksQueueFileName = "./src/crawler/Saved_State/LinksQueue.txt";
    public static final String seedSetFileName = "./src/crawler/SeedSet.txt";
    public static final String outputFolderBase = "./html_docs/";

    public static final DatabaseManager dbManager = new DatabaseManager();
    public static final SimpleDateFormat formatter= new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");

    public static BufferedWriter linksQueueWriter;

    public static final Object LOCK_LINKS_QUEUE = new Object();
    public static final Object LOCK_LINKS_QUEUE_WRITER = new Object();
    public static final Object LOCK_VISITED_SET = new Object();
    private static final Object LOCK_RECRAWLING_QUEUE = new Object();
    public static Queue<String> linksQueue = new LinkedList<>();
    public static HashMap<String, UrlInDB> visitedLinks = new HashMap<String, UrlInDB>();
    private static Queue<UrlObject> recrawlingQueue = new LinkedList<>();
    private static boolean recrawlingQueueFilled = false;

    public Crawler() {
    }

    private Boolean isAllowedURL(String url) {
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

    public void recrawling() {
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts recrawling");
        synchronized (Crawler.LOCK_RECRAWLING_QUEUE) {
            if(!Crawler.recrawlingQueueFilled) {
                Crawler.linksQueue.clear(); // clear in the start to make sure nothing there
                Crawler.visitedLinks.entrySet().forEach(entry->{
                    Crawler.recrawlingQueue.add(new UrlObject(entry.getKey(), entry.getValue().date, entry.getValue().id));
                });
                Crawler.recrawlingQueueFilled = true;
            }
        }
        while (true) {
            UrlObject crawledURL = null;
            // start lock
            boolean flag = false;
            synchronized (Crawler.LOCK_RECRAWLING_QUEUE) {
                if (Crawler.recrawlingQueue.isEmpty()) {
                    flag = true;
                }
            }
            // end lock
            if(flag)
                break;
            // start lock
            synchronized (Crawler.LOCK_RECRAWLING_QUEUE) {
                if (!Crawler.recrawlingQueue.isEmpty()) {
                    crawledURL = Crawler.recrawlingQueue.poll();
                }
            }
            // end lock
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
                    if(isChanged || (lastModifiedDate == null && downloadDate.after(dateToRecrawl))) {
                        Document urlContent = save_url_to_db(url.toString(), crawledURL.id);
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
                                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                    synchronized (Crawler.LOCK_VISITED_SET) {
                                        if (Crawler.linksQueue.size() + Crawler.visitedLinks.size() >= MAX_WEBSITES) {
                                            break;
                                        }
                                    }
                                }
                                if (this.isAllowedURL(path)) {
                                    synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                        Crawler.linksQueue.add(path);
                                    }
                                }
                                // end lock
                            }

                            // save to saved state
                            synchronized (Crawler.LOCK_LINKS_QUEUE_WRITER) {
                                Crawler.linksQueueWriter = new BufferedWriter(new FileWriter(Crawler.linksQueueFileName));
                                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                    for (final String urlStr : Crawler.linksQueue) {
                                        Crawler.linksQueueWriter.write(urlStr + '\n');
                                    }
                                }
                                Crawler.linksQueueWriter.close();
                            }
                        } else {
                            // delete from db
                            // delete from HDD
                            delete_from_db(crawledURL.url, crawledURL.id);
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

    public void crawling(){
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts crawling");
        while (true) {
            String crawledURL = "";
            // start lock
            boolean flag = false;
            synchronized (Crawler.LOCK_VISITED_SET) {
                if (visitedLinks.size() >= MAX_WEBSITES) {
                    flag = true;
                }
            }
            if (flag) {
                return;
            }
            // end lock
            flag = false;
            boolean flag2 = false;
            // start lock
            synchronized (Crawler.LOCK_VISITED_SET) {
                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                    if (!Crawler.linksQueue.isEmpty()) {
                        crawledURL = Crawler.linksQueue.poll();
                        if (Crawler.visitedLinks.containsKey(crawledURL)) {
                            flag = true;
                        }
                    } else {
                        System.out.println("Thread (" + Thread.currentThread().getName() + "): Empty Queue List");
                        flag2 = true;
                    }
                }
            }
            if (flag) {
                continue;
            }
            if (flag2) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.out.println(
                            "_________________________________________________________________________________________");
                    System.out.println("Error interupted while sleeping");
                    System.out.println(
                            "_________________________________________________________________________________________");

                }
                continue;
            }
            // end lock
            try {
                final URL url = new URL(crawledURL);
                try {
                    System.out.println("Time: " + (System.currentTimeMillis() - this.startCrawlingTime)
                            + ", crawling url : " + url);
                    Document urlContent = save_url_to_db(url.toString(), -1);
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
                            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                synchronized (Crawler.LOCK_VISITED_SET) {
                                    if (Crawler.linksQueue.size() + Crawler.visitedLinks.size() >= MAX_WEBSITES) {
                                        break;
                                    }
                                }
                            }
                            if (this.isAllowedURL(path)) {
                                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                    Crawler.linksQueue.add(path);
                                }
                            }
                            // end lock
                        }

                        // save to saved state
                        synchronized (Crawler.LOCK_LINKS_QUEUE_WRITER) {
                            Crawler.linksQueueWriter = new BufferedWriter(new FileWriter(Crawler.linksQueueFileName));
                            synchronized (Crawler.LOCK_LINKS_QUEUE) {
                                for (final String urlStr : Crawler.linksQueue) {
                                    Crawler.linksQueueWriter.write(urlStr + '\n');
                                }
                            }
                            Crawler.linksQueueWriter.close();
                        }
                    }
                } catch (IOException e) {
                    synchronized (Crawler.LOCK_LINKS_QUEUE) { // try again later by pushing in the end of queue
                        Crawler.linksQueue.add(crawledURL);
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
    public Document save_url_to_db(String url, int updateId) throws IOException{
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
            Connection connection = dbManager.getDBConnection();
            Statement stmt = connection.createStatement();
            int rowsAffected = stmt.executeUpdate( query, Statement.RETURN_GENERATED_KEYS );
            ResultSet rs = stmt.getGeneratedKeys();
            stmt.close();
            connection.close();
            rs.beforeFirst();
            rs.next();
            int id = updateId != -1? updateId:rs.getInt(1);
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

    public void delete_from_db(String url, int deleteId) {
        try {
            String query = String.format("DELETE FROM page WHERE id=%d;", deleteId);
            Connection connection = dbManager.getDBConnection();
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
        while(true){
            crawling(); // before recrawling just crawl new websites, after recrawling crawl new fetched urls in updated websites
            recrawling();
        }
    }

    public static void main(String[] args) {
        Crawler.formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        boolean isRecrawling = false;
        try {
            Date date = new Date(System.currentTimeMillis());
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
            File urlsFile = new File(Crawler.linksQueueFileName);
            if(!isRecrawling) {
                Scanner sc = new Scanner(urlsFile);
                while (sc.hasNextLine()) {
                    Crawler.linksQueue.add(sc.nextLine());
                }
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

        int counter = 1;
        List<Thread> threads = new ArrayList<>();
        try {
            Crawler.linksQueueWriter = new BufferedWriter(new FileWriter(Crawler.linksQueueFileName));
        } catch (IOException e) {
            System.out.println("                     ----------------- Error IO-Exception Can not open ("
                    + Crawler.linksQueueFileName + ") Exit program -----------------                     ");
            System.exit(0);
        }
        Thread t;
        while (Crawler.numThreads >= counter) {
            t = new Thread(new Crawler());
            t.setName(String.valueOf(counter++));
            threads.add(t);
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
            } catch (InterruptedException e) {
                System.out.println(
                        "                     ----------------- Error Thread has been interupted -----------------                     ");
            }
        }
    }
}
