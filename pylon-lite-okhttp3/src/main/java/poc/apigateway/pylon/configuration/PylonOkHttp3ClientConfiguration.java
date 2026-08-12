package poc.apigateway.pylon.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import poc.apigateway.pylon.extension.okhttp3.DefaultOkHttp3ClientPool;

/**
 * 실물: com.coupang.apigateway.pylon.configuration.PylonOkHttp3ClientConfiguration
 * (api-pylon-tools:2.14.9.RELEASE)
 *
 * <p><b>결함 보존:</b> {@code @ConditionalOnMissingBean} 이 없다. WebClient 확장과 똑같이
 * 함정 1 이 반복되고, 이름이 다른 {@code @Primary} 빈으로만 이길 수 있다.
 *
 * <p>실물이 함께 등록하는 {@code DefaultOkHttp3ClientAdaptor} 는 POC 가 버린 어댑터 계층에
 * 속하므로 미러링하지 않는다.
 */
@Configuration
public class PylonOkHttp3ClientConfiguration {

    @SuppressWarnings("deprecation")
    @Bean
    public DefaultOkHttp3ClientPool defaultOkHttp3ClientPool(PylonConfiguration configuration) {
        return new DefaultOkHttp3ClientPool(configuration);
    }
}
