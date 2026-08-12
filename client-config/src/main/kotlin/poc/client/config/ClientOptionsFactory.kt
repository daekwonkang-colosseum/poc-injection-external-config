package poc.client.config

import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.model.Spec
import poc.client.contract.ClientOptions

/**
 * pylon 타입과 계약이 만나는 유일한 지점.
 *
 * `client-contract` 는 pylon 도 Spring 도 모른다. 변환을 아는 쪽은 여기다.
 */
class ClientOptionsFactory(private val configuration: PylonConfiguration) {

    fun of(spec: Spec): ClientOptions = ClientOptions.of(
        configuration.connectionTimeout,
        spec.timeout,
        spec.connectionPool.name,
        spec.connectionPool.size,
    )
}
