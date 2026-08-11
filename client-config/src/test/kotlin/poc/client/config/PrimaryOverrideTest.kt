package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.SpecResolver

@SpringBootTest(
    properties = [
        "pylon.client.connect-timeout=777",
        "pylon.client.providers.[order_api].read-timeout=1200",
    ]
)
class PrimaryOverrideTest @Autowired constructor(
    // Spring 5.2 의 spring.test.constructor.autowire.mode 기본값은 ANNOTATED 다.
    // @Autowired 가 없으면 ApplicationContext 외의 파라미터는 리졸브되지 않는다.
    private val context: ApplicationContext,
    private val configuration: PylonConfiguration,
    private val restTemplatePool: RestTemplatePool,
    private val specResolver: SpecResolver,
) {

    @Test
    fun `the primary bean is the one injected everywhere`() {
        assertThat(configuration.connectionTimeout).isEqualTo(777)
        assertThat(restTemplatePool.connectionTimeout).isEqualTo(777)
    }

    @Test
    fun `the library default bean still exists but loses`() {
        assertThat(context.getBeanNamesForType(PylonConfiguration::class.java))
            .contains("defaultPylonConfiguration", "pylonConfiguration")

        val libraryDefault = context.getBean<PylonConfiguration>("defaultPylonConfiguration")
        assertThat(libraryDefault.connectionTimeout).isEqualTo(3000)
    }

    @Test
    fun `the injected option reaches the spec built from the jar`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout)
            .`as`("jar 값 3000 이 주입값 1200 으로 대체된다")
            .isEqualTo(1200)
    }

    @Test
    fun `an unconfigured provider keeps the jar value`() {
        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout).isEqualTo(8000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
