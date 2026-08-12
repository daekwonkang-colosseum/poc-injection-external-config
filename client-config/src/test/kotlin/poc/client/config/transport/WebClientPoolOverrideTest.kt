package poc.client.config.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import poc.apigateway.pylon.extension.webclient.DefaultWebClientPool
import poc.apigateway.pylon.extension.webclient.WebClientPool
import poc.apigateway.pylon.specs.model.Spec

/**
 * 함정 1(@ConditionalOnMissingBean 부재)이 전송 확장 빈에서도 반복된다는 것을 못박는다.
 *
 * 실물 PylonWebClientConfiguration 은 DefaultWebClientPool 을 조건 없이 등록하고,
 * javadoc 이 "@Primary 로 덮으라"고 안내한다. 그 좌석을 계약 구현이 차지한다.
 */
@SpringBootTest
@Import(WebClientTransportConfig::class)
class WebClientPoolOverrideTest @Autowired constructor(
    private val webClientPool: WebClientPool,
    private val context: ApplicationContext,
) {

    @Test
    fun `the primary bean is the one injected by type`() {
        assertThat(webClientPool).isInstanceOf(SpecAwareWebClientPool::class.java)
    }

    @Test
    fun `the library default is still registered but loses`() {
        val beans = context.getBeansOfType(WebClientPool::class.java)

        assertThat(beans.values).hasAtLeastOneElementOfType(DefaultWebClientPool::class.java)
        assertThat(beans).hasSize(2)
    }

    @Test
    fun `different providers no longer share one client`() {
        val order = webClientPool.get(spec("order_api", 1500))
        val product = webClientPool.get(spec("product_api", 1500))

        assertThat(order).isNotSameAs(product)
    }

    @Test
    fun `the same provider and timeout bucket still shares one client`() {
        val rounded = webClientPool.get(spec("order_api", 1500))
        val unrounded = webClientPool.get(spec("order_api", 1450))

        assertThat(rounded).isSameAs(unrounded)
    }

    private fun spec(provider: String, timeout: Int): Spec =
        Spec.builder("6512a0b1c2d3e4f500000001", provider, "/api/v1/orders/{orderId}")
            .setTimeout(timeout)
            .setConnectionPool(Spec.ConnectionPool(provider, 20))
            .build()
}
