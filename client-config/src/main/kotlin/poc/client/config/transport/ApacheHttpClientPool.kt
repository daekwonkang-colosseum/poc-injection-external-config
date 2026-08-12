package poc.client.config.transport

import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import poc.apigateway.pylon.HttpClientConnectionManagerFactory
import poc.client.contract.ClientOptions
import poc.client.contract.ClientPool
import java.util.concurrent.ConcurrentHashMap

/**
 * 기준 전송. Spring 에 의존하지 않는 Apache HttpClient 를 직접 조립한다.
 *
 * 커넥션 매니저는 pylon 의 [HttpClientConnectionManagerFactory] 를 그대로 쓴다.
 * 라이브러리의 풀링 원시요소 위에 계약이 얹힌다는 것을 보이는 편이,
 * 같은 것을 다시 만드는 것보다 증명력이 있다.
 */
class ApacheHttpClientPool(
    private val connectionManagerFactory: HttpClientConnectionManagerFactory,
) : ClientPool<CloseableHttpClient> {

    private val container = ConcurrentHashMap<String, CloseableHttpClient>()

    override fun get(options: ClientOptions): CloseableHttpClient =
        container.computeIfAbsent(options.cacheKey()) { create(options) }

    private fun create(options: ClientOptions): CloseableHttpClient {
        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(options.connectTimeoutMillis)
            .setConnectionRequestTimeout(options.connectTimeoutMillis)
            .setSocketTimeout(options.readTimeoutMillis)
            .build()

        return HttpClientBuilder.create()
            .setConnectionManager(connectionManagerFactory.getOrCreate(options.poolName, options.poolSize))
            // 하나의 커넥션 매니저를 timeout 이 다른 여러 클라이언트가 공유한다.
            // shared 로 두지 않으면 클라이언트 하나를 닫을 때 매니저가 함께 내려간다.
            .setConnectionManagerShared(true)
            .setDefaultRequestConfig(requestConfig)
            .build()
    }
}
