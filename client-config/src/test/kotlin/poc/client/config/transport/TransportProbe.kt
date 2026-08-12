package poc.client.config.transport

import org.apache.http.client.methods.HttpGet
import poc.apigateway.pylon.HttpClientConnectionManagerFactory
import poc.client.contract.ClientOptions
import java.net.SocketTimeoutException

/**
 * 적합성 매트릭스가 전송을 동일하게 다루기 위한 테스트 전용 어댑터.
 *
 * 전송마다 클라이언트 타입이 다르므로(CloseableHttpClient, WebClient, OkHttpClient,
 * Feign 타깃) 제네릭을 노출하지 않고 여기서 흡수한다. 프로덕션 코드에는 이 타입이
 * 존재하지 않는다 — 테스트 편의를 위한 좌석을 계약에 뚫지 않기 위해서다.
 */
interface TransportProbe {

    /** 동일성 비교에만 쓴다. 타입은 전송마다 다르므로 Any 로 받는다. */
    fun clientFor(options: ClientOptions): Any

    /** 상태코드를 돌려준다. read timeout 을 넘기면 예외를 던진다. */
    fun call(options: ClientOptions, url: String): Int

    /**
     * read timeout 초과 시 이 전송이 던지는 예외.
     * 전송마다 다르므로(Apache 는 SocketTimeoutException, Netty 는 ReadTimeoutException)
     * 매트릭스가 "무언가 터졌다"가 아니라 "timeout 으로 터졌다"를 단언할 수 있게 한다.
     */
    val timeoutFailure: Class<out Throwable>
}

class ApacheHttpClientProbe : TransportProbe {

    private val pool = ApacheHttpClientPool(HttpClientConnectionManagerFactory())

    override fun clientFor(options: ClientOptions): Any = pool.get(options)

    override fun call(options: ClientOptions, url: String): Int =
        pool.get(options).execute(HttpGet(url)).use { it.statusLine.statusCode }

    override val timeoutFailure: Class<out Throwable> = SocketTimeoutException::class.java

    override fun toString(): String = "apache-httpclient"
}
