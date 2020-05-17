package crawler;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

import database_connection.DatabaseManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler implements Runnable {
    private final int MAX_WEBSITES = 5000;
    private final long startCrawlingTime = System.currentTimeMillis();
    private static final int numThreads = 10;
    public static final String visitedFileName = "./src/crawler/Saved_State/VisitedLinks.txt";
    public static final String linksQueueFileName = "./src/crawler/Saved_State/LinksQueue.txt";
    public static final String seedSetFileName = "./src/crawler/SeedSet.txt";

    public static BufferedWriter visitedLinksWriter;
    public static BufferedWriter linksQueueWriter;

    public static final Object LOCK_LINKS_QUEUE = new Object();
    public static final Object LOCK_VISIted_SET_WRITER = new Object();
    public static final Object LOCK_LINKS_QUEUE_WRITER = new Object();
    public static final Object LOCK_VISITED_SET = new Object();
    public static Queue<String> linksQueue = new LinkedList<>();
    public static Set<String> visitedLinks = new HashSet<>();

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
                Boolean flag = false;
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

    public String normalizeUrl(String urlStr) {
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
        // Response resp = Jsoup.connect(url.toString()).method(Method.HEAD).execute();
        // String length = resp.header("Last-Modified");

    }

    public void run() {
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts running");
        while (true) {
            String crawledURL = "";
            // start lock
            boolean flag = false;
            synchronized (Crawler.LOCK_VISITED_SET) {
                if (visitedLinks.size() > MAX_WEBSITES) {
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
                        if (Crawler.visitedLinks.contains(crawledURL)) {
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
                    final Document urlContent = Jsoup.connect(url.toString()).get();
                    // final Document test = Jsoup.connect(url.toString()).head();
                    // System.out.println(test);
                    // start lock
                    synchronized (Crawler.LOCK_VISITED_SET) {
                        Crawler.visitedLinks.add(crawledURL);
                    }
                    synchronized (Crawler.LOCK_VISIted_SET_WRITER) {
                        Crawler.visitedLinksWriter = new BufferedWriter(new FileWriter(Crawler.visitedFileName, true));
                        Crawler.visitedLinksWriter.write(crawledURL + '\n');
                        Crawler.visitedLinksWriter.close();
                    }
                    // Download to the disk
                    String fileName = new String(crawledURL);
                    fileName = fileName.replace(":", "-");
                    fileName = fileName.replace(".", "_");
                    fileName = fileName.replace("/", "|");
                    BufferedWriter writer = new BufferedWriter(new FileWriter("./Output/" + fileName + ".html"));
                    writer.write(urlContent.toString());
                    writer.close();
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

    public static void main(String[] args) throws IOException {
        try {
//            Date today = new Date();
//            final long millisecPerDay = 86400000; // 24 * 60 * 60 * 1000
//            URL url = new URL("http://www.amazon.com");
//            URLConnection uc = url.openConnection();
//            uc.setIfModifiedSince(System.currentTimeMillis());
//            uc.setIfModifiedSince((new Date(today.getTime() - millisecPerDay)).getTime());
//            Date lastModified = new Date(uc.getIfModifiedSince());
//            Date t = new Date(today.getTime());
//            System.out.println("lastModified : " + lastModified);
//            System.out.println("today : " + t);
//            System.out.println(t.before(lastModified));
//            System.exit(0);
//            DatabaseManager dbManager = new DatabaseManager();
//            dbManager.getDBConnection()
            File urlsFile = new File(Crawler.linksQueueFileName);
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
        }
        try {
            File visitedLinksFile = new File(Crawler.visitedFileName);
            Scanner sc = new Scanner(visitedLinksFile);
            while (sc.hasNextLine()) {
                Crawler.visitedLinks.add(sc.nextLine());
            }
        } catch (FileNotFoundException e) {
            System.out.println("Can not find visited links file");
        }

        int counter = 1;
        List<Thread> threads = new ArrayList<>();
        try {
            Crawler.visitedLinksWriter = new BufferedWriter(new FileWriter(Crawler.visitedFileName, true));
            Crawler.linksQueueWriter = new BufferedWriter(new FileWriter(Crawler.linksQueueFileName));
        } catch (IOException e) {
            System.out.println("                     ----------------- Error IO-Exception Can not open ("
                    + Crawler.visitedFileName + ") or (" + Crawler.linksQueueFileName
                    + ") Exit program -----------------                     ");
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
                count = Crawler.linksQueue.size();
                if (count >= 1) {
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
