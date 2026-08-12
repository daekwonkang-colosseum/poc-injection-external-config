package poc.client.config.transport

import feign.Client
import feign.Request
import feign.Response
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.util.EntityUtils

/**
 * Feign 의 [Client] 좌석을 계약이 만든 Apache 클라이언트로 채운다.
 *
 * feign-core 만 쓰는 이유로 `feign-httpclient` 를 끌어오지 않는다. [Client] 는 메서드
 * 하나짜리 인터페이스라 직접 구현하는 편이 의존성을 늘리는 것보다 싸고, 무엇보다
 * **계약이 전송을 가로질러 합성된다**는 것을 보인다 — 여기서 쓰는 클라이언트는
 * [ApacheHttpClientPool] 이 [poc.client.contract.ClientOptions] 로 만든 바로 그 인스턴스다.
 *
 * timeout 은 Feign 시맨틱대로 호출 시점의 [Request.Options] 를 적용한다. 요청별
 * [RequestConfig] 가 클라이언트 기본값을 덮으므로 두 좌석이 충돌하지 않는다.
 */
class ApacheFeignClient(private val httpClient: CloseableHttpClient) : Client {

    override fun execute(request: Request, options: Request.Options): Response {
        val builder = RequestBuilder.create(request.httpMethod().name)
            .setUri(request.url())
            .setConfig(
                RequestConfig.custom()
                    .setConnectTimeout(options.connectTimeoutMillis())
                    .setConnectionRequestTimeout(options.connectTimeoutMillis())
                    .setSocketTimeout(options.readTimeoutMillis())
                    .build()
            )

        request.headers().forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
        request.body()?.let { builder.entity = ByteArrayEntity(it) }

        return httpClient.execute(builder.build()).use { httpResponse ->
            val body = httpResponse.entity?.let { EntityUtils.toByteArray(it) } ?: ByteArray(0)

            Response.builder()
                .status(httpResponse.statusLine.statusCode)
                .reason(httpResponse.statusLine.reasonPhrase)
                .request(request)
                .headers(httpResponse.allHeaders.groupBy({ it.name }, { it.value }))
                .body(body)
                .build()
        }
    }
}
