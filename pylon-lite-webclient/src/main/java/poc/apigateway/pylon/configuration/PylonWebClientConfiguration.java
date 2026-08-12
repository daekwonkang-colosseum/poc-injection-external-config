package poc.apigateway.pylon.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import poc.apigateway.pylon.extension.webclient.DefaultWebClientPool;

/**
 * 실물: com.coupang.apigateway.pylon.configuration.PylonWebClientConfiguration
 * (api-pylon-tools:2.14.9.RELEASE)
 *
 * <p><b>결함 보존:</b> {@code @ConditionalOnMissingBean} 이 없다. 함정 1 이 전송 확장
 * 빈에서 그대로 반복된다 — 같은 이름으로는 정의 충돌이 나고, 이름이 다른
 * {@code @Primary} 빈으로만 이길 수 있다. 실물 {@code DefaultWebClientPool} 의 javadoc 이
 * "User can override by providing new one and annotate @Primary" 라고 안내하는 이유다.
 *
 * <p>실물은 이 설정을 {@code ExtensionConfigurationSelector} 가
 * {@code PylonCodeGeneratorVersion.supportsWebClientExtension()} 을 보고 자동 선택한다.
 * POC 는 클라이언트가 명시적으로 {@code @Import} 한다 — 생성 jar 의 버전 메타로 확장을
 * 켜는 부분은 이 POC 의 주제가 아니다.
 *
 * <p>실물이 함께 등록하는 {@code DefaultWebClientAdaptor} 와
 * {@code HeaderExtractionWebFilter} 는 POC 가 버린 어댑터 계층에 속하므로 미러링하지 않는다.
 */
@Configuration
public class PylonWebClientConfiguration {

    @Bean
    public DefaultWebClientPool defaultWebClientPoolFactoryBean(PylonConfiguration configuration) {
        return new DefaultWebClientPool(configuration);
    }
}
