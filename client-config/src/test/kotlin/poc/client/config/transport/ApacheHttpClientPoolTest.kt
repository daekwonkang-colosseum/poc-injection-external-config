package poc.client.config.transport

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.apigateway.pylon.HttpClientConnectionManagerFactory
import poc.client.contract.ClientOptions

/**
 * Apache 전송 고유 검증. 전송 공통 불변식은 [TransportConformanceTest] 가 맡는다.
 */
class ApacheHttpClientPoolTest {

    @Test
    fun `the connection manager is sized from the options`() {
        val managerFactory = HttpClientConnectionManagerFactory()
        val pool = ApacheHttpClientPool(managerFactory)

        pool.get(ClientOptions.of(500, 1000, "order_api", 20))

        val manager = managerFactory.getOrCreate("order_api", 20) as PoolingHttpClientConnectionManager
        assertThat(manager.maxTotal).isEqualTo(20)
    }

    @Test
    fun `clients on the same pool share one connection manager`() {
        val managerFactory = HttpClientConnectionManagerFactory()
        val pool = ApacheHttpClientPool(managerFactory)

        pool.get(ClientOptions.of(500, 1000, "order_api", 20))
        pool.get(ClientOptions.of(500, 3000, "order_api", 20))

        assertThat(managerFactory.getOrCreate("order_api", 20))
            .isSameAs(managerFactory.getOrCreate("order_api", 999))
    }
}
