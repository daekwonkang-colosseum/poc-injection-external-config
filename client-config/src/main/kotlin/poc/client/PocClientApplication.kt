package poc.client

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Import
import poc.apigateway.pylon.configuration.EnablePocApiGatewayAdapters
import poc.client.config.PylonClientConfig

/**
 * @SpringBootTest 가 찾는 설정 루트. main() 은 없다 — POC에 실행 가능한 앱은 없다.
 *
 * @SpringBootConfiguration 은 @SpringBootApplication 과 달리 @ComponentScan 을
 * 포함하지 않으므로 PylonClientConfig 를 직접 @Import 한다. @ComponentScan 을 쓰면
 * 같은 패키지의 테스트용 중첩 @Configuration 까지 함께 주워온다.
 */
@SpringBootConfiguration
@EnablePocApiGatewayAdapters
@ConfigurationPropertiesScan
@Import(PylonClientConfig::class)
class PocClientApplication
