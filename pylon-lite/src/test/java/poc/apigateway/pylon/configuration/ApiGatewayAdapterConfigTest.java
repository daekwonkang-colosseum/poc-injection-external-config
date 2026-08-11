package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import poc.apigateway.pylon.DynamicApiClient;
import poc.apigateway.pylon.RestTemplatePool;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;
import poc.apigateway.pylon.specs.SpecResolver;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGatewayAdapterConfigTest {

    @Configuration
    @EnablePocApiGatewayAdapters
    static class FixtureLocators {
        @Bean
        SpecConfigurationLocator orderSpecs() {
            return () -> "fixture-provider.json";
        }

        @Bean
        InitialConfigurationLocator initial() {
            return () -> "fixture-initial.json";
        }

        @Bean
        GenerationMetaLocator meta() {
            return () -> "fixture-meta.json";
        }
    }

    @Configuration
    static class OverridingConfig {
        @Bean
        @Primary
        PylonConfiguration myPylonConfiguration() {
            return new PylonConfiguration.Builder()
                .connectionTimeout(777)
                .provider("order_api").defaultReadTimeout(1200).register()
                .build();
        }
    }

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(FixtureLocators.class);

    @Test
    void wires_the_runtime_and_registers_specs_from_the_jar() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DynamicApiClient.class);
            assertThat(context).hasSingleBean(SpecResolver.class);
            assertThat(context).hasSingleBean(BuildConfigurations.class);

            SpecResolver resolver = context.getBean(SpecResolver.class);
            assertThat(resolver.getProviderNames()).containsExactly("order_api");
            assertThat(resolver.get("spec-order-1").getTimeout()).isEqualTo(3000);
        });
    }

    @Test
    void a_primary_bean_replaces_the_library_default() {
        runner.withUserConfiguration(OverridingConfig.class).run(context -> {
            assertThat(context.getBean(PylonConfiguration.class).getConnectionTimeout()).isEqualTo(777);
            assertThat(context.getBean(RestTemplatePool.class).getConnectionTimeout()).isEqualTo(777);
            assertThat(context.getBean(SpecResolver.class).get("spec-order-1").getTimeout()).isEqualTo(1200);
        });
    }

    @Test
    void the_library_default_is_still_present_but_not_injected() {
        runner.withUserConfiguration(OverridingConfig.class).run(context -> {
            assertThat(context.getBeanNamesForType(PylonConfiguration.class))
                .contains("defaultPylonConfiguration", "myPylonConfiguration");
            assertThat(context.getBean("defaultPylonConfiguration", PylonConfiguration.class)
                .getConnectionTimeout()).isEqualTo(3000);
        });
    }
}
