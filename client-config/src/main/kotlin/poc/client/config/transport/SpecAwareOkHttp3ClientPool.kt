package poc.client.config.transport

import okhttp3.OkHttpClient
import poc.apigateway.pylon.extension.okhttp3.OkHttp3ClientPool
import poc.apigateway.pylon.specs.SpecResolver
import poc.client.config.ClientOptionsFactory

/**
 * 라이브러리가 시그니처에서 떨어뜨린 옵션 캐리어를 되찾는 어댑터.
 *
 * [OkHttp3ClientPool.get] 은 `specId` 만 준다. WebClient 좌석이 `Spec` 을 주는 것과 달리
 * 여기서는 옵션이 도달할 길이 없다 — 그래서 실물 구현이 read timeout 을 3초로
 * 하드코딩할 수밖에 없었다.
 *
 * [SpecResolver] 는 스프링 빈이므로 주입받을 수 있고, specId 로 Spec 을 되찾으면
 * 나머지는 다른 전송과 완전히 같은 경로다. **라이브러리 시그니처는 한 글자도 바꾸지 않는다.**
 */
class SpecAwareOkHttp3ClientPool(
    private val pool: ContractOkHttp3ClientPool,
    private val specResolver: SpecResolver,
    private val optionsFactory: ClientOptionsFactory,
) : OkHttp3ClientPool {

    override fun get(specId: String): OkHttpClient =
        pool.get(optionsFactory.of(specResolver.get(specId)))
}
