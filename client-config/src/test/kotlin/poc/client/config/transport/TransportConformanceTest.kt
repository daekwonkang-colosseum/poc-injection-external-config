package poc.client.config.transport

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import poc.apigateway.pylon.testsupport.StubApiServer
import poc.client.contract.ClientOptions

/**
 * 적합성 매트릭스. 하나의 [ClientOptions] 가 모든 전송에서 같은 유효 옵션을 만드는지 단언한다.
 *
 * 전송이 늘어날 때마다 [transports] 에 행이 하나씩 붙는다. 실물 pylon 에서
 * WebClient 는 풀을, OkHttp3 는 timeout 과 풀을 모두 흘리므로, 그 미러가 들어오는
 * PR 에서는 계약 적용 전에 이 매트릭스가 먼저 빨갛게 되어야 한다.
 */
class TransportConformanceTest {

    @ParameterizedTest
    @MethodSource("transports")
    fun `the read timeout from the options reaches the socket`(probe: TransportProbe) {
        StubApiServer.start().use { stub ->
            stub.respondAfter("/slow", 1500L, 200, "{}")

            // 200 은 보정을 거쳐 300ms 가 된다. 스텁은 1500ms 뒤에 응답한다.
            val options = ClientOptions.of(500, 200, "order_api", 20)

            assertThatThrownBy { probe.call(options, stub.baseUrl() + "/slow") }
                .isInstanceOf(probe.timeoutFailure)
        }
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun `a response inside the read timeout comes back normally`(probe: TransportProbe) {
        StubApiServer.start().use { stub ->
            stub.respondAfter("/fast", 100L, 200, "{}")

            val options = ClientOptions.of(500, 1000, "order_api", 20)

            assertThat(probe.call(options, stub.baseUrl() + "/fast")).isEqualTo(200)
        }
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun `clients are reused when the contract cache key matches`(probe: TransportProbe) {
        val rounded = probe.clientFor(ClientOptions.of(500, 1500, "order_api", 20))
        val sameBucket = probe.clientFor(ClientOptions.of(500, 1450, "order_api", 20))

        assertThat(rounded).isSameAs(sameBucket)
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun `different pools get different clients even at the same timeout`(probe: TransportProbe) {
        val order = probe.clientFor(ClientOptions.of(500, 1500, "order_api", 20))
        val product = probe.clientFor(ClientOptions.of(500, 1500, "product_api", 20))

        assertThat(order).isNotSameAs(product)
    }

    companion object {
        @JvmStatic
        fun transports(): List<TransportProbe> = listOf(
            ApacheHttpClientProbe(),
            WebClientProbe(),
            OkHttp3Probe(),
            FeignProbe(),
        )
    }
}
