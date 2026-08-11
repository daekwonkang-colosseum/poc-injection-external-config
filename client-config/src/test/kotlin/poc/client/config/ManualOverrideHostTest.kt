package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import poc.apigateway.pylon.specs.SpecResolver
import poc.apigateway.pylon.testsupport.StubApiServer
import poc.apigateway.services.product_api.ProductapiApiV1ProductsAdapter
import poc.apigateway.services.product_api.model.RequestParamOfGetApiV1ProductsProductId

/**
 * 두 번째 주입 경로: 타입 빈이 아니라 프로퍼티 스캔.
 * jar 의 product-api.poc.internal 을 스텁으로 갈아치운다. 코드 변경은 0줄이다.
 */
@SpringBootTest
class ManualOverrideHostTest @Autowired constructor(
    // Spring 5.2 의 spring.test.constructor.autowire.mode 기본값은 ANNOTATED 다.
    // @Autowired 가 없으면 파라미터가 리졸브되지 않는다.
    private val adapter: ProductapiApiV1ProductsAdapter,
    private val specResolver: SpecResolver,
) {

    @Test
    fun `the property scan attaches a host override to the spec`() {
        val override = specResolver.get(PRODUCT_SPEC_ID).hostOverride

        assertThat(override).isNotNull()
        assertThat(override.host).isEqualTo("127.0.0.1")
        assertThat(override.port).isEqualTo(stub.port)
    }

    @Test
    fun `the call lands on the stub instead of the jar host`() {
        stub.respond("/api/v1/products/p-1", 200, """{"productId":"p-1","name":"POC"}""")

        val product = adapter.getApiV1ProductsProductId(RequestParamOfGetApiV1ProductsProductId("p-1"))

        assertThat(product.productId).isEqualTo("p-1")
        assertThat(product.name).isEqualTo("POC")
        assertThat(stub.receivedPaths()).contains("/api/v1/products/p-1")
    }

    @Test
    fun `order_api is unaffected and keeps the jar host`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).hostOverride).isNull()
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"

        // 프로퍼티 등록이 컨텍스트 생성 전에 일어나야 하므로 여기서 띄운다.
        private val stub: StubApiServer = StubApiServer.start()

        @JvmStatic
        @DynamicPropertySource
        fun redirectProductApi(registry: DynamicPropertyRegistry) {
            registry.add("api_gateway.manual_override.provider.product_api.server") { stub.baseUrl() }
            registry.add("api_gateway.manual_override.version") { "1" }
        }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            stub.close()
        }
    }
}
