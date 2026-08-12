package poc.client.config.transport

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.configuration.PylonWebClientConfiguration
import poc.apigateway.pylon.extension.webclient.WebClientPool
import poc.client.config.ClientOptionsFactory

/**
 * WebClient 전송을 계약으로 덮는다.
 *
 * 라이브러리 확장 설정을 [Import] 로 켜 두고, 그 설정이 등록하는
 * `defaultWebClientPoolFactoryBean` 을 이름이 다른 [Primary] 빈으로 이긴다.
 * 라이브러리는 한 줄도 고치지 않는다.
 */
@Configuration
@Import(PylonWebClientConfiguration::class)
class WebClientTransportConfig {

    @Bean
    fun clientOptionsFactory(configuration: PylonConfiguration) = ClientOptionsFactory(configuration)

    @Bean
    @Primary
    fun contractWebClientPool(optionsFactory: ClientOptionsFactory): WebClientPool =
        SpecAwareWebClientPool(ContractWebClientPool(), optionsFactory)
}
