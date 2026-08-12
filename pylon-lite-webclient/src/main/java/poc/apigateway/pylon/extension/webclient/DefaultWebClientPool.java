package poc.apigateway.pylon.extension.webclient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.specs.model.Spec;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * 실물: com.coupang.apigateway.pylon.extension.webclient.DefaultWebClientPool
 * (api-pylon-tools:2.14.9.RELEASE, 2.17.0 도 이 부분은 동일)
 *
 * <p><b>결함 두 개를 의도적으로 보존한다.</b> 고쳐서 옮기면 POC 가 증명할 대상이 사라진다.
 *
 * <ol>
 *   <li>캐시 키가 보정된 timeout 단독이다. 풀 이름이 키에 없어서, timeout 이 같은 두
 *       provider 가 하나의 WebClient 를 공유한다. RestTemplatePool 은 같은 상황에서
 *       {@code poolName + "-" + timeout} 으로 키를 잡아 분리한다.
 *   <li>{@link Spec#getConnectionPool()} 를 아예 읽지 않는다. Reactor Netty 의 기본
 *       ConnectionProvider 가 쓰이므로 provider 별 max-connection 설정이 조용히 무시된다.
 * </ol>
 *
 * <p>보정식도 {@code RestTemplatePool} 과 따로 복제돼 있다 — 자체 ROUND_TRIP_TIME 상수까지
 * 별도로 선언한다. 지금은 결과값이 같지만 한쪽만 바뀌면 조용히 갈라진다.
 *
 * <p>실물은 Guava Cache 를 쓴다. POC 는 pylon-lite 가 Guava 에 의존하지 않으므로
 * ConcurrentHashMap 으로 미러링한다 — RestTemplatePool 미러와 같은 방식이고,
 * 결함 재현에는 영향이 없다.
 */
public class DefaultWebClientPool implements WebClientPool {

    private static final int ROUND_TRIP_TIME = 100;

    private final PylonConfiguration configuration;
    private final ConcurrentMap<Integer, WebClient> container = new ConcurrentHashMap<>();

    public DefaultWebClientPool(PylonConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public WebClient get(Spec spec) {
        int refinedTimeout = (int) Math.ceil((float) spec.getTimeout() / 100) * 100 + ROUND_TRIP_TIME;
        return container.computeIfAbsent(refinedTimeout, this::create);
    }

    protected WebClient create(int readTimeout) {
        TcpClient tcpClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectionTimeout())
            .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(readTimeout, MILLISECONDS)));

        return WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build())
            .clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient)))
            .build();
    }
}
