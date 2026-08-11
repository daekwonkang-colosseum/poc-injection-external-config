package poc.apigateway.pylon;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 풀 이름별로 커넥션 매니저를 하나만 만든다. */
@Component
public class HttpClientConnectionManagerFactory {

    private static final int VALIDATE_AFTER_INACTIVITY_MS = 100;

    private final ConcurrentMap<String, HttpClientConnectionManager> managers = new ConcurrentHashMap<>();

    public HttpClientConnectionManager getOrCreate(String poolName, int poolSize) {
        return managers.computeIfAbsent(poolName, name -> create(poolSize));
    }

    private HttpClientConnectionManager create(int poolSize) {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(poolSize);
        manager.setDefaultMaxPerRoute(poolSize);
        manager.setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY_MS);
        return manager;
    }
}
