import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler {
    private final Set<URL> links;
    private final long startCrawlingTime;

    private Crawler(final URL startURL) {
        this.links = new HashSet<>();
        this.startCrawlingTime = System.currentTimeMillis();
        start_crawling(Collections.singleton(startURL));
    }

    private void start_crawling(final Set<URL> urls) {
        urls.removeAll(this.links);
        if (!urls.isEmpty()) {
            final Set<URL> newURLS = new HashSet<>();
            try {
                this.links.addAll(urls);
                for (final URL url : urls) {
                    System.out
                            .println((System.currentTimeMillis() - this.startCrawlingTime) + " crawling url : " + url);
                    final Document urlContent = Jsoup.connect(url.toString()).get();
                    final Elements linksFound = urlContent.select("a[href]");
                    for (final Element link : linksFound) {
                        final String urlText = link.attr("abs:href");
                        if (urlText != "") {
                            newURLS.add(new URL(urlText));
                        }
                    }
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            start_crawling(newURLS);
        }
    }

    public static void main(String[] args) throws IOException {
        final Crawler crawler = new Crawler(new URL("https://www.gutenberg.org/"));
    }
}
