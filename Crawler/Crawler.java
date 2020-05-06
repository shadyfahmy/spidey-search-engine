import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler implements Runnable {
    private final int MAX_WEBSITES = 5000;
    private final long startCrawlingTime = System.currentTimeMillis();
    private static final int numThreads = 10;

    public static BufferedWriter visitedLinksWriter;
    public static BufferedWriter linksQueueWriter;

    static final Object LOCK_LINKS_QUEUE = new Object();
    static final Object LOCK_VISIted_SET_WRITER = new Object();
    static final Object LOCK_LINKS_QUEUE_WRITER = new Object();
    static final Object LOCK_VISITED_SET = new Object();
    public static Queue<String> linksQueue = new LinkedList<>();
    public static Set<String> visitedLinks = new HashSet<>();

    // public void run() {
    // this.crawling();
    // }

    public Crawler() {
    }

    public void run() {
        System.out.println("Thread (" + Thread.currentThread().getName() + "): starts running");
        while (true) {
            String crawledURL = "";
            // start lock
            boolean flag = false;
            synchronized (Crawler.LOCK_VISITED_SET) {
                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                    if (visitedLinks.size() > MAX_WEBSITES || Crawler.linksQueue.isEmpty()) {
                        flag = true;
                    } else {
                        crawledURL = Crawler.linksQueue.poll();
                    }
                }
            }
            if (flag) {
                return;
            }
            // end lock
            flag = false;
            // start lock
            synchronized (Crawler.LOCK_VISITED_SET) {
                if (Crawler.visitedLinks.contains(crawledURL)) {
                    flag = true;
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
                        synchronized (Crawler.LOCK_LINKS_QUEUE) {
                            Crawler.linksQueue.add(urlText);
                        }
                        // end lock
                    }

                    // Download to the disk
                    String fileName = new String(crawledURL);
                    System.out.println(crawledURL);
                    fileName = fileName.replace(":", "-");
                    fileName = fileName.replace(".", "_");
                    fileName = fileName.replace("/", "|");
                    System.out.println(
                            "_________________________________________________________________________________________");
                    BufferedWriter writer = new BufferedWriter(new FileWriter("./Output/" + fileName + ".html"));
                    writer.write(urlContent.toString());
                    writer.close();
                    System.out.println("Thread (" + Thread.currentThread().getName() + "): " + crawledURL
                            + " is now added to output folder");
                    System.out.println(
                            "_________________________________________________________________________________________");
                    // save to saved state
                    synchronized (Crawler.LOCK_LINKS_QUEUE_WRITER) {
                        Crawler.linksQueueWriter = new BufferedWriter(new FileWriter("./Saved_State/LinksQueue.txt"));
                        for (final String urlStr : linksQueue) {
                            linksQueueWriter.write(urlStr + '\n');
                        }
                        Crawler.linksQueueWriter.close();
                    }
                    synchronized (Crawler.LOCK_VISIted_SET_WRITER) {
                        visitedLinksWriter.write(crawledURL + '\n');
                    }

                } catch (IOException e) {
                    System.out.println("                     ----------------- Error IO-Exception while crawling : "
                            + crawledURL + " -----------------                     ");
                }
            } catch (MalformedURLException e) {
                System.out.println("                     ----------------- Error Mal-Formed-URL while crawling : "
                        + crawledURL + " -----------------                     ");
            }
        }
    }

    public static void main(String[] args) {
        try {
            File urlsFile = new File("./Saved_state/LinksQueue.txt");
            Scanner sc = new Scanner(urlsFile);
            while (sc.hasNextLine())
                Crawler.linksQueue.add(sc.nextLine());
            if (Crawler.linksQueue.isEmpty()) {
                Crawler.linksQueue.add("https://www.gutenberg.org/");
            }
        } catch (FileNotFoundException e) {
            Crawler.linksQueue.add("https://www.gutenberg.org/");
        }
        try {
            File visitedLinksFile = new File("./Saved_state/VisitedLinks.txt");
            Scanner sc = new Scanner(visitedLinksFile);
            while (sc.hasNextLine())
                Crawler.visitedLinks.add(sc.nextLine());
        } catch (FileNotFoundException e) {
        }

        int counter = 1;
        List<Thread> threads = new ArrayList<>();
        String visitedFileName = "./Saved_State/VisitedLinks.txt";
        try {
            Crawler.visitedLinksWriter = new BufferedWriter(new FileWriter(visitedFileName));
        } catch (IOException e) {
            System.out.println("                     ----------------- Error IO-Exception Can not open ("
                    + visitedFileName + ") Exit program -----------------                     ");
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
            thread.start();
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
