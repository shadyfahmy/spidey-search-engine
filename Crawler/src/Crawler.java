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
    private final long startCrawlingTime;
    private static final int numThreads = 10;
    static final Object LOCK_LINKS_QUEUE = new Object();
    static final Object LOCK_VISITED_SET = new Object();
    public static Queue<String> linksQueue = new LinkedList<>();
    public static Set<String> VisitedLinks = new HashSet<>();

    public void run() {
        crawling();
    }

    private Crawler() {
        this.startCrawlingTime = System.currentTimeMillis();
        this.crawling();
    }

    private void crawling() {
        while(true) {
            String crawledURL;
            // start lock
            synchronized (Crawler.LOCK_VISITED_SET) {
                synchronized (Crawler.LOCK_LINKS_QUEUE) {
                    if (VisitedLinks.size() > MAX_WEBSITES || Crawler.linksQueue.isEmpty()) {
                        return;
                    }
                    crawledURL = Crawler.linksQueue.poll();
                }
            }
            // end lock
            // start lock
            synchronized (Crawler.LOCK_VISITED_SET) {
                if(Crawler.VisitedLinks.contains(crawledURL)) {
                    continue;
                }
            }
            // end lock
            try {
                final URL url = new URL(crawledURL);
                try {
                    System.out.println("Time: " + (System.currentTimeMillis() - this.startCrawlingTime) + ", crawling url : " + url);
                    final Document urlContent = Jsoup.connect(url.toString()).get();
                    // Download to the disk
                    // System.out.println(urlContent.toString());
                    BufferedWriter writer = new BufferedWriter(new FileWriter("./" + crawledURL + ".html"));
                    writer.write(urlContent.toString());
                    writer.close();
                    // start lock
                    synchronized (Crawler.LOCK_VISITED_SET) {
                        Crawler.VisitedLinks.add(crawledURL);
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
                } catch (IOException e) {
                    System.out.println("                     ----------------- Error IO-Exception while crawling : " + crawledURL + " -----------------                     ");
                }
            } catch (MalformedURLException e) {
                System.out.println("                     ----------------- Error Mal-Formed-URL while crawling : " + crawledURL + " -----------------                     ");
            }

        }
    }

    public static void main(String[] args) {
        try {
            File urlsFile = new File("./linksQueue.txt");
            Scanner sc = new Scanner(urlsFile);
            while (sc.hasNextLine())
                Crawler.linksQueue.add(sc.nextLine());
        } catch (FileNotFoundException e) {
            Crawler.linksQueue.add("https://www.gutenberg.org/");
        }
        int counter = 1;
        List<Thread> threads = new ArrayList<>();
        while(Crawler.numThreads >= counter) {
            Thread t = new Thread (new Crawler()); t.setName(String.valueOf(counter));
            threads.add(t);
            counter++;
        }
        for(final Thread t :threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("                     ----------------- Error Thread has been interupted -----------------                     ");
            }
        }
    }
}
