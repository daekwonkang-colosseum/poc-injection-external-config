package poc.client.config.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.apigateway.pylon.HttpClientConnectionManagerFactory
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.model.Spec
import poc.client.config.ClientOptionsFactory

/**
 * RestTemplate 은 매트릭스의 기준선이다. 계약을 적용하지 않는다.
 *
 * 네 전송 중 유일하게 실물이 처음부터 옳게 하던 경로이기 때문이다 — timeout 보정도,
 * 풀 이름을 포함한 캐시 키도 [RestTemplatePool] 이 이미 갖고 있다. 계약이 한 일은
 * 규칙을 발명한 것이 아니라 **이 경로의 규칙을 나머지 셋으로 옮긴 것**이다.
 *
 * 이 클래스는 그 사실을 못박는다. 라이브러리가 기준을 바꾸면 여기서 먼저 깨진다.
 *
 * RestTemplate 자체를 계약으로 덮지 않는 이유: [RestTemplatePool] 은 인터페이스가 아닌
 * 구체 클래스이고 [poc.apigateway.pylon.DynamicApiClient] 가 그 타입을 직접 주입받는다.
 * @Primary 로 이기려면 상속뿐인데, 그것은 계약을 Spring 에 강결합시킨다.
 */
class RestTemplateBaselineTest {

    @Test
    fun `the contract reproduces the library reference formula`() {
        val configuration = configuration()
        val spec = spec("order_api", 1500)
        val libraryPool = RestTemplatePool(configuration, HttpClientConnectionManagerFactory())

        val contractValue = ClientOptionsFactory(configuration).of(spec).readTimeoutMillis

        assertThat(contractValue).isEqualTo(libraryPool.readTimeoutOf(spec))
    }

    @Test
    fun `the library already shares one client across a timeout bucket`() {
        val pool = RestTemplatePool(configuration(), HttpClientConnectionManagerFactory())

        assertThat(pool.get(spec("order_api", 1500))).isSameAs(pool.get(spec("order_api", 1450)))
    }

    @Test
    fun `the library already separates clients by pool name`() {
        val pool = RestTemplatePool(configuration(), HttpClientConnectionManagerFactory())

        assertThat(pool.get(spec("order_api", 1500))).isNotSameAs(pool.get(spec("product_api", 1500)))
    }

    private fun configuration(): PylonConfiguration =
        PylonConfiguration.Builder().connectionTimeout(500).build()

    private fun spec(provider: String, timeout: Int): Spec =
        Spec.builder("6512a0b1c2d3e4f500000001", provider, "/api/v1/orders/{orderId}")
            .setTimeout(timeout)
            .setConnectionPool(Spec.ConnectionPool(provider, 20))
            .build()
}
