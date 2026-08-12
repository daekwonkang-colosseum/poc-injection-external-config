package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.apigateway.pylon.specs.ApiProviderPolicyDeployer
import poc.apigateway.pylon.specs.SpecResolver
import poc.apigateway.pylon.specs.customizer.TimeoutCustomizer
import poc.apigateway.pylon.specs.model.Spec

/**
 * 함정 6 방어. 라이브러리 [TimeoutCustomizer] 는 `isApplicableInRuntime() == false` 라
 * 원격 정책 갱신 체인에서 빠지는데, 클라이언트가 `true` 인 커스터마이저를 하나 더 얹으면
 * 갱신 후에도 주입값이 살아남는다.
 *
 * 이것이 가능한 이유는 `ApiGatewayAdapterConfig.specResolver` 가
 * `List<SpecCustomizer>` 로 **리스트 주입**을 받기 때문이다. `@Primary` 는 리스트를
 * 중복 제거하지 않으므로 이 경로에서는 무력하지만, 반대로 빈을 하나 더 등록하면
 * 그대로 체인에 합류한다.
 */
class RuntimeTimeoutCustomizerTest {

    @Test
    fun `the injected timeout survives a remote policy update`() {
        val resolver = SpecResolver(
            listOf(libraryCustomizer(), RuntimeTimeoutCustomizer(mapOf("order_api" to 1000), emptyMap())),
            COMMON,
        )
        resolver.register(jarSpec(3000))
        assertThat(resolver.get(ORDER_SPEC_ID).timeout).isEqualTo(1000)

        ApiProviderPolicyDeployer(resolver).updateTimeout(ORDER_SPEC_ID, 3000)

        assertThat(resolver.get(ORDER_SPEC_ID).timeout).isEqualTo(1000)
    }

    @Test
    fun `a per-spec entry beats the provider default, same as the library`() {
        val customizer = RuntimeTimeoutCustomizer(
            mapOf("order_api" to 1000),
            mapOf(ORDER_SPEC_ID to 1500),
        )

        assertThat(customizer.process(jarSpec(3000)).timeout).isEqualTo(1500)
    }

    @Test
    fun `an unconfigured provider is left untouched`() {
        val customizer = RuntimeTimeoutCustomizer(mapOf("order_api" to 1000), emptyMap())

        val untouched = Spec.builder("other-spec", "product_api", "/api/v1/products/{productId}")
            .setTimeout(8000)
            .setConnectionPool(COMMON)
            .build()

        assertThat(customizer.process(untouched).timeout).isEqualTo(8000)
    }

    @Test
    fun `it applies at runtime, unlike the library customizer`() {
        assertThat(RuntimeTimeoutCustomizer(emptyMap(), emptyMap()).isApplicableInRuntime).isTrue()
        assertThat(TimeoutCustomizer().isApplicableInRuntime).isFalse()
    }

    private fun libraryCustomizer() = TimeoutCustomizer().apply { registerByProvider("order_api", 1000) }

    private fun jarSpec(timeout: Int): Spec =
        Spec.builder(ORDER_SPEC_ID, "order_api", "/api/v1/orders/{orderId}")
            .setTimeout(timeout)
            .setConnectionPool(COMMON)
            .build()

    private companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        val COMMON = Spec.ConnectionPool("pylon-common", 1000)
    }
}
