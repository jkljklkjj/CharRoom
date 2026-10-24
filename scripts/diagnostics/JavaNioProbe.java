import java.net.http.HttpClient;
import java.nio.channels.Selector;

/** Local JVM startup check. No server or external network request is made. */
public final class JavaNioProbe {
    public static void main(String[] args) throws Exception {
        try (Selector selector = Selector.open()) {
            selector.selectNow();
            System.out.println("PASS: Java NIO selector (" + selector.getClass().getSimpleName() + ")");
        }
        try (HttpClient ignored = HttpClient.newHttpClient()) {
            System.out.println("PASS: Java HTTP client initialization");
        }
    }
}
