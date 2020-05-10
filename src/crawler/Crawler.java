package crawler;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class Crawler implements Runnable {
    private final int MAX_WEBSITES = 5000;
    private final long startCrawlingTime = System.currentTimeMillis();
    private static final int numThreads = 10;
    public static final String visitedFileName = "./Saved_State/VisitedLinks.txt";
    public static final String linksQueueFileName = "./Saved_State/LinksQueue.txt";

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
            path = path.replace("https://", "http://");
            path = path.toLowerCase();
            if (path.length() > 0 && path.charAt(path.length() - 1) == '/') {
                path = path.substring(0, path.length() - 1);
            }
        }
        return path;
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
                        flag = true;
                    }
                }
            }
            if (flag) {
                continue;
            }
            // end lock
            try {
                final URL url = new URL(crawledURL);
                try {
                    System.out.println("Time: " + (System.currentTimeMillis() - this.startCrawlingTime)
                            + ", crawling url : " + url);
                    final Document urlContent = Jsoup.connect(url.toString()).get();
                    // start lock
                    synchronized (Crawler.LOCK_VISITED_SET) {
                        Crawler.visitedLinks.add(crawledURL);
                    }
                    // end lock

                    final Elements linksFound = urlContent.select("a[href]");
                    for (final Element link : linksFound) {
                        final String urlText = link.attr("abs:href");
                        // start lock
                        String path = normalizeUrl(urlText); // URL Normalization
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
                } catch (IOException e) {
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

    public static void main(String[] args) {
        try {
            File urlsFile = new File("./Saved_State/LinksQueue.txt");
            Scanner sc = new Scanner(urlsFile);
            while (sc.hasNextLine()) {
                Crawler.linksQueue.add(sc.nextLine());
            }
            if (Crawler.linksQueue.isEmpty()) {
                System.out.println("QUEUE EMPTY!!");
                Crawler.linksQueue.add("http://www.gutenberg.org");
            }
        } catch (FileNotFoundException e) {
            Crawler.linksQueue.add("http://www.gutenberg.org");
            System.out.println("Can not open Queue file, QUEUE EMPTY!!");
        }
        try {
            File visitedLinksFile = new File("./Saved_State/VisitedLinks.txt");
            Scanner sc = new Scanner(visitedLinksFile);
            while (sc.hasNextLine()) {
                Crawler.visitedLinks.add(sc.nextLine());
            }
        } catch (FileNotFoundException e) {
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
