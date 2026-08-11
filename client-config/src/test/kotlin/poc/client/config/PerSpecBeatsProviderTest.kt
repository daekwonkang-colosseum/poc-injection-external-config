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
@ActiveProfiles("production")
class PerSpecBeatsProviderTest @Autowired constructor(
    // Spring 5.2 의 spring.test.constructor.autowire.mode 기본값은 ANNOTATED 다.
    // @Autowired 가 없으면 나머지 파라미터는 리졸브되지 않는다.
    private val configuration: PylonConfiguration,
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {

    @Test
    fun `the per-spec value wins over the provider default`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout)
            .`as`("provider 기본 3000 이 아니라 per-spec 1500 이어야 한다")
            .isEqualTo(1500)
    }

    @Test
    fun `the effective socket timeout adds the round trip allowance`() {
        assertThat(restTemplatePool.readTimeoutOf(specResolver.get(ORDER_SPEC_ID)))
            .`as`("ceil(1500/100)*100 + 100")
            .isEqualTo(1600)
    }

    @Test
    fun `production profile sets the connect timeout`() {
        assertThat(configuration.connectionTimeout).isEqualTo(2000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
    }
}
