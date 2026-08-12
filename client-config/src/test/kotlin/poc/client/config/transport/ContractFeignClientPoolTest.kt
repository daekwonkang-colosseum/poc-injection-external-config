package poc.client.config.transport

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.apigateway.pylon.HttpClientConnectionManagerFactory
import poc.client.contract.ClientOptions

/**
 * Feign 전송 고유 검증. 전송 공통 불변식은 [TransportConformanceTest] 가 맡는다.
 *
 * Feign 은 좌석이 둘로 나뉜 유일한 전송이다 — timeout 은 [feign.Request.Options] 가,
 * 커넥션 풀은 [feign.Client] 구현체가 쥔다. 계약이 둘 다 채우는지 본다.
 */
class ContractFeignClientPoolTest {

    @Test
    fun `the request options carry the adjusted contract timeouts`() {
        val pool = ContractFeignClientPool(ApacheHttpClientPool(HttpClientConnectionManagerFactory()))

        val transport = pool.get(ClientOptions.of(550, 1500, "order_api", 20))

        assertThat(transport.options.connectTimeoutMillis()).isEqualTo(550)
        assertThat(transport.options.readTimeoutMillis()).isEqualTo(1600)
    }

    @Test
    fun `the connection manager behind the feign client is sized from the options`() {
        val managerFactory = HttpClientConnectionManagerFactory()
        val pool = ContractFeignClientPool(ApacheHttpClientPool(managerFactory))

        pool.get(ClientOptions.of(500, 1500, "order_api", 20))

        val manager = managerFactory.getOrCreate("order_api", 20) as PoolingHttpClientConnectionManager
        assertThat(manager.maxTotal).isEqualTo(20)
    }

    @Test
    fun `transports are reused when the contract cache key matches`() {
        val pool = ContractFeignClientPool(ApacheHttpClientPool(HttpClientConnectionManagerFactory()))

        val rounded = pool.get(ClientOptions.of(500, 1500, "order_api", 20))
        val sameBucket = pool.get(ClientOptions.of(500, 1450, "order_api", 20))

        assertThat(rounded).isSameAs(sameBucket)
    }
}
