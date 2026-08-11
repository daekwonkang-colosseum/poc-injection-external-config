package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.SpecResolver

@SpringBootTest
@ActiveProfiles("local")
class ProviderReadTimeoutTest @Autowired constructor(
    // Spring 5.2 의 spring.test.constructor.autowire.mode 기본값은 ANNOTATED 다.
    // @Autowired 가 없으면 나머지 파라미터는 리졸브되지 않는다.
    private val configuration: PylonConfiguration,
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {

    @Test
    fun `local profile shortens the order_api timeout`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout).isEqualTo(1000)
    }

    @Test
    fun `local profile shortens the connect timeout`() {
        assertThat(configuration.connectionTimeout).isEqualTo(500)
        assertThat(restTemplatePool.connectionTimeout).isEqualTo(500)
    }

    @Test
    fun `local profile gives order_api its own connection pool`() {
        val pool = specResolver.get(ORDER_SPEC_ID).connectionPool

        assertThat(pool.name).isEqualTo("order_api")
        assertThat(pool.size).isEqualTo(20)
    }

    @Test
    fun `product_api is untouched and keeps the shared pool and the jar timeout`() {
        val spec = specResolver.get(PRODUCT_SPEC_ID)

        assertThat(spec.timeout).isEqualTo(8000)
        assertThat(spec.connectionPool.name).isEqualTo("pylon-common")
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
