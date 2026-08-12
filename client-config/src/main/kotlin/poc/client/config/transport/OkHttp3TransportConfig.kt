package poc.client.config.transport

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import poc.apigateway.pylon.configuration.PylonOkHttp3ClientConfiguration
import poc.apigateway.pylon.extension.okhttp3.OkHttp3ClientPool
import poc.apigateway.pylon.specs.SpecResolver
import poc.client.config.ClientOptionsFactory

/**
 * OkHttp3 전송을 계약으로 덮는다.
 *
 * 라이브러리 확장 설정을 [Import] 로 켜 두고, 그 설정이 등록하는
 * `defaultOkHttp3ClientPool` 을 이름이 다른 [Primary] 빈으로 이긴다.
 */
@Configuration
@Import(PylonOkHttp3ClientConfiguration::class, ClientContractConfig::class)
class OkHttp3TransportConfig {

    @Bean
    @Primary
    fun contractOkHttp3ClientPool(
        specResolver: SpecResolver,
        optionsFactory: ClientOptionsFactory,
    ): OkHttp3ClientPool =
        SpecAwareOkHttp3ClientPool(ContractOkHttp3ClientPool(), specResolver, optionsFactory)
}
