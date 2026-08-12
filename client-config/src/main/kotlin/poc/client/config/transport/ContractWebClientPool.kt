package poc.client.config.transport

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import poc.client.contract.ClientOptions
import poc.client.contract.ClientPool
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import reactor.netty.tcp.TcpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * 실물 [poc.apigateway.pylon.extension.webclient.DefaultWebClientPool] 의 결함 두 개를 계약으로 메운다.
 *
 * 1. 캐시 키를 [ClientOptions.cacheKey] 로 잡는다 — 풀 이름이 들어가므로 timeout 이 같은
 *    두 provider 가 클라이언트를 공유하지 않는다.
 * 2. [ConnectionProvider] 를 풀 이름·크기로 만들어 전송에 실제로 붙인다 — 실물은
 *    `spec.getConnectionPool()` 을 아예 읽지 않아 Reactor Netty 기본 풀이 쓰인다.
 *
 * 보정식은 [ClientOptions] 안에만 있다. 이 클래스는 계산하지 않는다.
 */
class ContractWebClientPool : ClientPool<WebClient> {

    private val container = ConcurrentHashMap<String, WebClient>()

    override fun get(options: ClientOptions): WebClient =
        container.computeIfAbsent(options.cacheKey()) { create(options) }

    private fun create(options: ClientOptions): WebClient {
        // reactor-netty 0.9.x 의 API. 1.0 부터는 ConnectionProvider.builder(name).maxConnections(n) 다.
        val provider = ConnectionProvider.fixed(options.poolName, options.poolSize)

        val tcpClient = TcpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.connectTimeoutMillis)
            .doOnConnected { it.addHandlerLast(ReadTimeoutHandler(options.readTimeoutMillis.toLong(), MILLISECONDS)) }

        return WebClient.builder()
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs { it.defaultCodecs().maxInMemorySize(-1) }
                    .build()
            )
            .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient)))
            .build()
    }
}
