package poc.client.config.transport

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import poc.client.contract.ClientOptions
import poc.client.contract.ClientPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.MINUTES

/**
 * 실물 [poc.apigateway.pylon.extension.okhttp3.DefaultOkHttp3ClientPool] 의 결함 세 개를 메운다.
 *
 * 1. read timeout 을 [ClientOptions] 에서 받는다 — 실물은 3초 하드코딩이다.
 * 2. 풀 크기를 [Dispatcher] 와 [ConnectionPool] 에 실제로 붙인다.
 * 3. 캐시 키가 [ClientOptions.cacheKey] 다 — 실물은 specId 라 옵션이 같은 두 spec 도
 *    클라이언트를 따로 만든다.
 *
 * OkHttp 의 [Dispatcher] 상한은 비동기 호출(`enqueue`)에 적용된다. 동기 `execute` 는
 * 호출 스레드에서 바로 실행되므로, 동시성 제한이 필요한 쪽은 [ConnectionPool] 과 함께
 * 두 좌석 모두 채운다.
 */
class ContractOkHttp3ClientPool : ClientPool<OkHttpClient> {

    private val container = ConcurrentHashMap<String, OkHttpClient>()

    override fun get(options: ClientOptions): OkHttpClient =
        container.computeIfAbsent(options.cacheKey()) { create(options) }

    private fun create(options: ClientOptions): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = options.poolSize
            maxRequestsPerHost = options.poolSize
        }

        return OkHttpClient.Builder()
            .connectTimeout(options.connectTimeoutMillis.toLong(), MILLISECONDS)
            .readTimeout(options.readTimeoutMillis.toLong(), MILLISECONDS)
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(options.poolSize, KEEP_ALIVE_MINUTES, MINUTES))
            .build()
    }

    private companion object {
        const val KEEP_ALIVE_MINUTES = 5L
    }
}
