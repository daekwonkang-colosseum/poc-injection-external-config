package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import poc.apigateway.pylon.specs.ApiProviderPolicyDeployer
import poc.apigateway.pylon.specs.SpecResolver

/**
 * 함정 6 방어를 실제 컨텍스트에서 증명한다.
 *
 * `local` 프로파일은 order_api 에 read-timeout 1000ms 를 준다. jar 값은 3000ms 다.
 * 원격 정책이 jar 값을 들고 도착해도 주입값이 유지되어야 한다.
 *
 * 이 테스트는 공유 컨텍스트의 SpecResolver 를 갱신하므로 [DirtiesContext] 를 붙인다.
 * 방어가 동작하면 결과 상태는 갱신 전과 같지만, 공유 가변 상태를 건드린다는 사실
 * 자체가 다른 테스트에 대한 위험이다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuntimePolicyClobberDefenceTest @Autowired constructor(
    private val specResolver: SpecResolver,
    private val context: ApplicationContext,
) {

    @Test
    fun `the runtime customizer is registered alongside the library one`() {
        val customizers = context.getBeansOfType(SpecResolver.SpecCustomizer::class.java)

        assertThat(customizers.values).hasAtLeastOneElementOfType(RuntimeTimeoutCustomizer::class.java)
    }

    @Test
    fun `a remote policy update cannot undo the injected timeout`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout).isEqualTo(1000)

        ApiProviderPolicyDeployer(specResolver).updateTimeout(ORDER_SPEC_ID, 3000)

        assertThat(specResolver.get(ORDER_SPEC_ID).timeout).isEqualTo(1000)
    }

    @Test
    fun `an unconfigured provider still takes the remote value`() {
        // product_api 에는 클라이언트 설정이 없다. 원격 값이 그대로 반영되는 것이 맞다.
        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout).isEqualTo(8000)

        ApiProviderPolicyDeployer(specResolver).updateTimeout(PRODUCT_SPEC_ID, 5000)

        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout).isEqualTo(5000)
    }

    private companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
