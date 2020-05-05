import java.io.IOException;
import java.net.URL;

public class Crawler {
    public static void main(String[] args) throws IOException {
        final Crawler crawler = new Crawler(new URL("https://www.gutenberg.org/"));
    }
}
