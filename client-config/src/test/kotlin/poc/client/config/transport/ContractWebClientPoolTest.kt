package poc.client.config.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.apigateway.pylon.testsupport.StubApiServer
import poc.client.contract.ClientOptions
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.Collections

/**
 * WebClient 전송 고유 검증. 전송 공통 불변식은 [TransportConformanceTest] 가 맡는다.
 *
 * 여기서 증명하는 것은 실물 [poc.apigateway.pylon.extension.webclient.DefaultWebClientPool]
 * 이 놓친 두 번째 결함이다 — Spec 의 커넥션 풀 크기가 전송에 도달하는가.
 */
class ContractWebClientPoolTest {

    @Test
    fun `a pool of one serializes concurrent calls`() {
        StubApiServer.start().use { stub ->
            stub.respondAfter("/slow", 800L, 200, "{}")
            stub.respond("/fast", 200, "{}")

            val client = ContractWebClientPool().get(ClientOptions.of(500, 3000, "order_api", 1))
            val finished = Collections.synchronizedList(mutableListOf<String>())

            Flux.merge(
                client.get().uri(stub.baseUrl() + "/slow").exchange().doOnSuccess { finished.add("/slow") },
                client.get().uri(stub.baseUrl() + "/fast").exchange().doOnSuccess { finished.add("/fast") },
            ).blockLast(Duration.ofSeconds(20))

            // 풀 크기가 전송에 닿지 않으면 지연 없는 /fast 가 먼저 끝난다.
            assertThat(finished).containsExactly("/slow", "/fast")
        }
    }

    @Test
    fun `a larger pool lets the fast call overtake the slow one`() {
        StubApiServer.start().use { stub ->
            stub.respondAfter("/slow", 800L, 200, "{}")
            stub.respond("/fast", 200, "{}")

            val client = ContractWebClientPool().get(ClientOptions.of(500, 3000, "order_api", 2))
            val finished = Collections.synchronizedList(mutableListOf<String>())

            Flux.merge(
                client.get().uri(stub.baseUrl() + "/slow").exchange().doOnSuccess { finished.add("/slow") },
                client.get().uri(stub.baseUrl() + "/fast").exchange().doOnSuccess { finished.add("/fast") },
            ).blockLast(Duration.ofSeconds(20))

            assertThat(finished).containsExactly("/fast", "/slow")
        }
    }
}
