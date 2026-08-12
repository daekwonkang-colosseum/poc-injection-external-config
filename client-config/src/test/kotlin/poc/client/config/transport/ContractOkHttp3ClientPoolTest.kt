package poc.client.config.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.client.contract.ClientOptions

/**
 * OkHttp3 전송 고유 검증. 전송 공통 불변식은 [TransportConformanceTest] 가 맡는다.
 *
 * 실물 [poc.apigateway.pylon.extension.okhttp3.DefaultOkHttp3ClientPool] 이 흘리는
 * 세 가지(read timeout, 풀 크기, 캐시 키)가 모두 뒤집히는지 본다.
 */
class ContractOkHttp3ClientPoolTest {

    @Test
    fun `the read timeout is the adjusted contract value, not the hardcoded three seconds`() {
        val client = ContractOkHttp3ClientPool().get(ClientOptions.of(500, 1500, "order_api", 20))

        assertThat(client.readTimeoutMillis()).isEqualTo(1600)
    }

    @Test
    fun `the connect timeout reaches the client`() {
        val client = ContractOkHttp3ClientPool().get(ClientOptions.of(550, 1500, "order_api", 20))

        assertThat(client.connectTimeoutMillis()).isEqualTo(550)
    }

    @Test
    fun `the pool size caps concurrent requests per host`() {
        val client = ContractOkHttp3ClientPool().get(ClientOptions.of(500, 1500, "order_api", 20))

        assertThat(client.dispatcher().maxRequestsPerHost).isEqualTo(20)
    }

    @Test
    fun `two specs with identical options now share one client`() {
        val pool = ContractOkHttp3ClientPool()

        val rounded = pool.get(ClientOptions.of(500, 1500, "order_api", 20))
        val unrounded = pool.get(ClientOptions.of(500, 1450, "order_api", 20))

        assertThat(rounded).isSameAs(unrounded)
    }

    @Test
    fun `different pools get different clients even at the same timeout`() {
        val pool = ContractOkHttp3ClientPool()

        val order = pool.get(ClientOptions.of(500, 1500, "order_api", 20))
        val product = pool.get(ClientOptions.of(500, 1500, "product_api", 20))

        assertThat(order).isNotSameAs(product)
    }
}
