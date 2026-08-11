package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import poc.apigateway.pylon.ApiException
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.specs.SpecResolver
import poc.apigateway.pylon.testsupport.StubApiServer
import poc.apigateway.services.order_api.OrderapiApiV1OrdersAdapter
import poc.apigateway.services.order_api.model.RequestParamOfGetApiV1OrdersOrderId

/**
 * 주입된 timeout 이 실제 소켓까지 도달하는지 증명한다.
 * order_api 를 스텁 서버로 돌려놓고 지연을 조절해 양쪽 경계를 본다.
 */
@SpringBootTest(
    properties = [
        "pylon.client.connect-timeout=1000",
        "pylon.client.providers.[order_api].read-timeout=400",
    ]
)
class ReadTimeoutReachesSocketTest @Autowired constructor(
    // Spring 5.2 의 spring.test.constructor.autowire.mode 기본값은 ANNOTATED 다.
    // @Autowired 가 없으면 파라미터가 리졸브되지 않는다.
    private val adapter: OrderapiApiV1OrdersAdapter,
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {

    @Test
    fun `the effective socket timeout is the uplifted value`() {
        assertThat(restTemplatePool.readTimeoutOf(specResolver.get(ORDER_SPEC_ID)))
            .`as`("ceil(400/100)*100 + 100")
            .isEqualTo(500)
    }

    @Test
    fun `a response inside the timeout succeeds`() {
        stub.respond("/api/v1/orders/fast", 200, """{"orderId":"fast","status":"OK"}""")

        assertThatCode {
            val order = adapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId("fast"))
            assertThat(order.orderId).isEqualTo("fast")
            assertThat(order.status).isEqualTo("OK")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a response past the timeout raises ApiException`() {
        stub.respondAfter("/api/v1/orders/slow", 1500L, 200, """{"orderId":"slow"}""")

        assertThatThrownBy { adapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId("slow")) }
            .`as`("500ms 소켓 timeout 이 1500ms 응답을 끊는다")
            .isInstanceOf(ApiException::class.java)
            .hasMessageContaining(ORDER_SPEC_ID)
    }

    @Test
    fun `the request actually reached the stub`() {
        stub.respond("/api/v1/orders/seen", 200, """{"orderId":"seen"}""")

        adapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId("seen"))

        assertThat(stub.receivedPaths()).contains("/api/v1/orders/seen")
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"

        // 프로퍼티 등록이 컨텍스트 생성 전에 일어나야 하므로 여기서 띄운다.
        private val stub: StubApiServer = StubApiServer.start()

        @JvmStatic
        @DynamicPropertySource
        fun redirectOrderApi(registry: DynamicPropertyRegistry) {
            registry.add("api_gateway.manual_override.provider.order_api.server") { stub.baseUrl() }
        }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            stub.close()
        }
    }
}
