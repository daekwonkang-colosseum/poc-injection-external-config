package poc.client.config.transport

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.client.config.ClientOptionsFactory

/**
 * 전송과 무관한 계약 배선. 전송 설정들이 각자 [org.springframework.context.annotation.Import]
 * 한다 — 스프링이 @Configuration 임포트를 중복 제거하므로 여러 전송을 함께 켜도 빈이 하나다.
 */
@Configuration
class ClientContractConfig {

    @Bean
    fun clientOptionsFactory(configuration: PylonConfiguration) = ClientOptionsFactory(configuration)
}
