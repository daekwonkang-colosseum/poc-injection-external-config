package poc.apigateway.pylon.testsupport;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * JDK 내장 HttpServer 기반 스텁. 외부 의존성이 없다.
 * 포트 0으로 바인딩해 OS가 빈 포트를 주게 하고 getPort() 로 회수한다.
 */
public class StubApiServer implements AutoCloseable {

    private final HttpServer server;
    private final List<String> receivedPaths = new CopyOnWriteArrayList<>();

    private StubApiServer(HttpServer server) {
        this.server = server;
    }

    public static StubApiServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return new StubApiServer(server);
        } catch (IOException e) {
            throw new IllegalStateException("failed to start stub server", e);
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    public void respond(String path, int status, String body) {
        respondAfter(path, 0L, status, body);
    }

    public void respondAfter(String path, long delayMillis, int status, String body) {
        server.createContext(path, exchange -> {
            receivedPaths.add(exchange.getRequestURI().toString());
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
    }

    /** 수신한 요청의 path + query 목록. 순서는 도착 순이다. */
    public List<String> receivedPaths() {
        return Collections.unmodifiableList(receivedPaths);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
