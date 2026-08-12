package poc.client.config.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import poc.apigateway.pylon.extension.okhttp3.DefaultOkHttp3ClientPool
import poc.apigateway.pylon.extension.okhttp3.OkHttp3ClientPool

/**
 * OkHttp3 경로에서 잃어버린 옵션 캐리어를 되찾는다.
 *
 * 라이브러리 좌석은 `get(String specId)` 라 Spec 이 도달하지 않는다. 계약 구현은
 * SpecResolver 로 specId → Spec → ClientOptions 를 복원한다. 두 빈이 같은 컨텍스트에
 * 나란히 있으므로 차이를 직접 대조할 수 있다.
 */
@Suppress("DEPRECATION")
@SpringBootTest
@Import(OkHttp3TransportConfig::class)
class OkHttp3PoolOverrideTest @Autowired constructor(
    private val pool: OkHttp3ClientPool,
    private val context: ApplicationContext,
) {

    @Test
    fun `the primary bean is the one injected by type`() {
        assertThat(pool).isInstanceOf(SpecAwareOkHttp3ClientPool::class.java)
    }

    @Test
    fun `the library default is still registered but loses`() {
        val beans = context.getBeansOfType(OkHttp3ClientPool::class.java)

        assertThat(beans.values).hasAtLeastOneElementOfType(DefaultOkHttp3ClientPool::class.java)
        assertThat(beans).hasSize(2)
    }

    @Test
    fun `the spec id recovers the jar timeout instead of the hardcoded three seconds`() {
        // jar 의 product_api 스펙은 8000ms 다. 보정을 거쳐 8100ms 가 되어야 한다.
        assertThat(pool.get(PRODUCT_SPEC_ID).readTimeoutMillis()).isEqualTo(8100)
    }

    @Test
    fun `the library default would have cut that eight second spec down to three`() {
        val libraryDefault = context.getBean(DefaultOkHttp3ClientPool::class.java)

        assertThat(libraryDefault.get(PRODUCT_SPEC_ID).readTimeoutMillis()).isEqualTo(3000)
    }

    @Test
    fun `the pool size from the spec reaches the dispatcher`() {
        // provider 별 max-connection 이 없으므로 공용 풀 크기(provider 수 x 500)를 쓴다.
        assertThat(pool.get(ORDER_SPEC_ID).dispatcher().maxRequestsPerHost).isEqualTo(1000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
