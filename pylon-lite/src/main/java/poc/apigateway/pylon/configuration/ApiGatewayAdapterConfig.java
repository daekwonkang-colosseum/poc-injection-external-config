package poc.apigateway.pylon.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import poc.apigateway.configuration.ApiGatewayConfigurationMarker;
import poc.apigateway.pylon.PylonToolsMarker;
import poc.apigateway.pylon.configuration.dto.ApiSpecificationConfigurationDto;
import poc.apigateway.pylon.configuration.dto.ProviderConfigurationDto;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.customizer.ConnectionPoolCustomizer;
import poc.apigateway.pylon.specs.customizer.TimeoutCustomizer;
import poc.apigateway.pylon.specs.model.Spec;
import poc.apigateway.services.ApiGatewayServiceMarker;

import java.util.List;
import java.util.Map;

/**
 * 런타임 조립의 중심. jar가 들고 온 값(BuildConfigurations)과
 * 외부 설정(PylonConfiguration)이 여기서 만난다.
 */
@Configuration
@ComponentScan(
    basePackageClasses = {
        PylonToolsMarker.class,
        ApiGatewayServiceMarker.class,
        ApiGatewayConfigurationMarker.class},
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Component.class),
    excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class))
@Import(ManualOverrideConfiguration.class)
public class ApiGatewayAdapterConfig {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayAdapterConfig.class);

    private static final int MAX_COMMON_POOL = 4000;
    private static final String COMMON_POOL_NAME = "pylon-common";

    /**
     * 주의: @ConditionalOnMissingBean 이 없다.
     * 클라이언트는 같은 이름으로 정의할 수 없고, 이름이 다른 @Primary 빈으로만 이길 수 있다.
     */
    @Bean
    public PylonConfiguration defaultPylonConfiguration() {
        return new PylonConfiguration.Builder().build();
    }

    /**
     * 주의: per-spec 등록이 provider 기본값 존재 여부 안쪽에 갇혀 있다.
     * defaultReadTimeout 없이 readTimeoutPerSpec 만 주면 조용히 무시된다.
     */
    @Bean
    public TimeoutCustomizer timeoutCustomizer(PylonConfiguration configuration) {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        for (PylonConfiguration.Provider provider : configuration.getProviders()) {
            if (provider.getDefaultTimeout() != null) {
                customizer.registerByProvider(provider.getName(), provider.getDefaultTimeout());
                for (Map.Entry<String, Integer> entry : provider.getReadTimeoutPerSpec().entrySet()) {
                    customizer.registerBySpec(entry.getKey(), entry.getValue());
                }
            }
        }
        return customizer;
    }

    @Bean
    public ConnectionPoolCustomizer connectionPoolCustomizer(PylonConfiguration configuration) {
        ConnectionPoolCustomizer customizer = new ConnectionPoolCustomizer();
        for (PylonConfiguration.Provider provider : configuration.getProviders()) {
            if (provider.getMaxConnection() != null) {
                customizer.register(provider.getName(), provider.getName(), provider.getMaxConnection());
            }
        }
        return customizer;
    }

    @Bean
    public SpecResolver specResolver(List<SpecResolver.SpecCustomizer> customizers,
                                     PylonConfiguration pylonConfiguration,
                                     BuildConfigurations buildConfigurations) {
        List<ProviderConfigurationDto> providers = buildConfigurations.getProviders();
        Spec.ConnectionPool commonPool =
            new Spec.ConnectionPool(COMMON_POOL_NAME, commonPoolSize(pylonConfiguration, providers.size()));
        log.info("Pylon common connection pool : {}", commonPool);

        SpecResolver resolver = new SpecResolver(customizers, commonPool);
        for (ProviderConfigurationDto provider : providers) {
            if (provider.getSpecifications() == null) {
                continue;
            }
            for (ApiSpecificationConfigurationDto specification : provider.getSpecifications()) {
                resolver.register(Spec.builder(
                        specification.getId(), provider.getName(), specification.getPath())
                    .setMethod(HttpMethod.resolve(specification.getMethod().toUpperCase()))
                    .setTimeout(specification.getTimeout() == null ? 0 : specification.getTimeout())
                    .setConnectionPool(commonPool)
                    .build());
            }
        }
        return resolver;
    }

    private int commonPoolSize(PylonConfiguration configuration, int providerCount) {
        if (configuration.getMaxConnection() != null) {
            return configuration.getMaxConnection();
        }
        return Math.min(providerCount * PylonConfiguration.DEFAULT_CONNECTION_PER_PROVIDER, MAX_COMMON_POOL);
    }
}
