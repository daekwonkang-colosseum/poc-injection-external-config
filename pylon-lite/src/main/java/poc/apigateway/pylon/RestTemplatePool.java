package poc.apigateway.pylon;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RestTemplate 을 (풀 이름, 보정된 read timeout) 키로 캐시한다.
 *
 * 함정: timeout 은 100ms 단위로 올림된 뒤 왕복 여유 100ms 가 더해진다.
 * 즉 1500 을 설정하면 소켓에 걸리는 값은 1600 이다.
 */
@Component
public class RestTemplatePool {

    public static final int ROUND_TRIP_TIME = 100;

    private static final int BUCKET = 100;

    private final int connectionTimeout;
    private final HttpClientConnectionManagerFactory connectionManagerFactory;
    private final ConcurrentMap<String, RestTemplate> container = new ConcurrentHashMap<>();

    public RestTemplatePool(PylonConfiguration pylonConfiguration,
                            HttpClientConnectionManagerFactory connectionManagerFactory) {
        this.connectionTimeout = pylonConfiguration.getConnectionTimeout();
        this.connectionManagerFactory = connectionManagerFactory;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /** 실제로 소켓에 걸리는 read timeout. 테스트가 이 값을 단언한다. */
    public int readTimeoutOf(Spec spec) {
        return uplifting(spec.getTimeout()) + ROUND_TRIP_TIME;
    }

    public RestTemplate get(Spec spec) {
        Spec.ConnectionPool pool = spec.getConnectionPool();
        int readTimeout = readTimeoutOf(spec);
        String key = pool.getName() + "-" + readTimeout;

        return container.computeIfAbsent(key,
            ignored -> create(readTimeout, connectionManagerFactory.getOrCreate(pool.getName(), pool.getSize())));
    }

    private static int uplifting(int timeout) {
        return (int) (Math.ceil((double) timeout / BUCKET) * BUCKET);
    }

    private RestTemplate create(int readTimeout, HttpClientConnectionManager connectionManager) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
            HttpClientBuilder.create().setConnectionManager(connectionManager).build());
        factory.setConnectTimeout(connectionTimeout);
        factory.setConnectionRequestTimeout(connectionTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
