package poc.client.config.transport

import org.springframework.web.reactive.function.client.WebClient
import poc.apigateway.pylon.extension.webclient.WebClientPool
import poc.apigateway.pylon.specs.model.Spec
import poc.client.config.ClientOptionsFactory

/**
 * 라이브러리 좌석과 전송 중립 계약을 잇는 어댑터.
 *
 * pylon 은 [WebClientPool.get] 에 [Spec] 을 넘긴다. 계약은 [poc.client.contract.ClientOptions]
 * 만 안다. 그 사이 변환이 여기 한 줄로 고립되므로, 전송 구현([ContractWebClientPool])은
 * pylon 타입을 전혀 모른다.
 */
class SpecAwareWebClientPool(
    private val pool: ContractWebClientPool,
    private val optionsFactory: ClientOptionsFactory,
) : WebClientPool {

    override fun get(spec: Spec): WebClient = pool.get(optionsFactory.of(spec))
}
