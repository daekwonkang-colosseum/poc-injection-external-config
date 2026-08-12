package poc.client.config.transport

import feign.Client
import feign.Feign
import feign.Request
import poc.client.contract.ClientOptions
import poc.client.contract.ClientPool
import java.util.concurrent.ConcurrentHashMap

/**
 * Feign 은 좌석이 둘로 나뉜 유일한 전송이다.
 *
 * timeout 은 [Request.Options] 가, 커넥션 풀은 [Client] 구현체가 쥔다. 하나의
 * [ClientOptions] 에서 둘을 함께 채워야 다른 전송과 같은 유효 옵션이 나온다.
 */
class FeignTransport(
    val client: Client,
    val options: Request.Options,
) {

    /** Feign 인터페이스를 쓸 때의 진입점. 계약이 채운 두 좌석을 그대로 물려준다. */
    fun <T> target(api: Class<T>, baseUrl: String): T =
        Feign.builder().client(client).options(options).target(api, baseUrl)
}

/**
 * pylon 에는 Feign 통합이 없다 — 실물 전 소스에 feign 문자열이 0건이다.
 * 따라서 이 전송은 미러링이 아니라 계약의 확장 적용이며, 대조할 실물이 존재하지 않는다.
 */
class ContractFeignClientPool(
    private val httpClientPool: ApacheHttpClientPool,
) : ClientPool<FeignTransport> {

    private val container = ConcurrentHashMap<String, FeignTransport>()

    override fun get(options: ClientOptions): FeignTransport =
        container.computeIfAbsent(options.cacheKey()) { create(options) }

    private fun create(options: ClientOptions) = FeignTransport(
        ApacheFeignClient(httpClientPool.get(options)),
        Request.Options(options.connectTimeoutMillis, options.readTimeoutMillis),
    )
}
