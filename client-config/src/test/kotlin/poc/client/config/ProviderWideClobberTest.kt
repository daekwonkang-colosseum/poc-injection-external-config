package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import poc.apigateway.pylon.specs.SpecResolver

/**
 * 이 테스트는 버그를 잡는 것이 아니라 **위험을 문서화한다.**
 *
 * product_api 의 스펙은 jar 에서 8000ms 로 왔다. provider 단위 read-timeout 을 주면
 * 그 8000 이 조용히 짓밟힌다. 오래 걸리는 spec 이 섞인 provider 에 일괄 설정을
 * 하면 안 된다는 것을 여기서 못박는다.
 */
@SpringBootTest(
    properties = [
        "pylon.client.providers.[product_api].read-timeout=1000",
    ]
)
class ProviderWideClobberTest @Autowired constructor(
    // Spring 5.2 의 spring.test.constructor.autowire.mode 기본값은 ANNOTATED 다.
    // @Autowired 가 없으면 파라미터가 리졸브되지 않는다.
    private val specResolver: SpecResolver,
) {

    @Test
    fun `a provider-wide read timeout clobbers a long jar timeout`() {
        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout)
            .`as`("jar 의 8000 이 provider 일괄 설정 1000 으로 대체된다 — 의도한 위험")
            .isEqualTo(1000)
    }

    @Test
    fun `the untouched provider keeps its jar timeout`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout)
            .`as`("짓밟기는 설정한 provider 에만 일어난다")
            .isEqualTo(3000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}

/**
 * 복구 경로. 프로퍼티가 다르므로 별도 컨텍스트가 필요하고, 그래서 별도 최상위 클래스다.
 *
 * provider 일괄 설정을 그대로 둔 채 per-spec 항목으로 긴 timeout 을 되살린다.
 * TimeoutCustomizer 가 spec 우선이라 provider 값 1000 을 이긴다.
 */
@SpringBootTest(
    properties = [
        "pylon.client.providers.[product_api].read-timeout=1000",
        "pylon.client.providers.[product_api].read-timeout-per-spec.[6512a0b1c2d3e4f500000002]=8000",
    ]
)
class ProviderWideClobberRestoredTest @Autowired constructor(
    private val specResolver: SpecResolver,
) {

    @Test
    fun `an explicit per-spec entry restores the long timeout`() {
        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout)
            .`as`("provider 일괄 1000 위에 per-spec 8000 을 명시해 jar 값을 되살린다")
            .isEqualTo(8000)
    }

    companion object {
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
