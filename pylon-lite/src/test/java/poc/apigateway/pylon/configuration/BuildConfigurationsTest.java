package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import poc.apigateway.pylon.configuration.dto.ApiSpecificationConfigurationDto;
import poc.apigateway.pylon.configuration.dto.InitialConfigurationDto;
import poc.apigateway.pylon.configuration.dto.ProviderConfigurationDto;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildConfigurationsTest {

    private BuildConfigurations load(String providerPath) {
        List<SpecConfigurationLocator> specs =
            Collections.singletonList(() -> providerPath);
        InitialConfigurationLocator initial = () -> "fixture-initial.json";
        GenerationMetaLocator meta = () -> "fixture-meta.json";
        return new BuildConfigurations(specs, initial, meta);
    }

    @Test
    void reads_provider_specifications_from_classpath() {
        BuildConfigurations configurations = load("fixture-provider.json");

        List<ProviderConfigurationDto> providers = configurations.getProviders();
        assertThat(providers).hasSize(1);

        ProviderConfigurationDto provider = providers.get(0);
        assertThat(provider.getName()).isEqualTo("order_api");
        assertThat(provider.getSpecifications()).hasSize(1);

        ApiSpecificationConfigurationDto spec = provider.getSpecifications().get(0);
        assertThat(spec.getId()).isEqualTo("spec-order-1");
        assertThat(spec.getPath()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(spec.getMethod()).isEqualTo("get");
        assertThat(spec.getTimeout()).isEqualTo(3000);
    }

    @Test
    void reads_routing_targets_from_initial_configuration() {
        InitialConfigurationDto initial = load("fixture-provider.json").getInitialConfiguration();

        InitialConfigurationDto.Target target = initial
            .getConsumers().get("poc")
            .getRoutingPolicies()
            .getProviders().get(0)
            .getRegions().get(0)
            .getTargets().get(0);

        assertThat(target.getScheme()).isEqualTo("HTTP");
        assertThat(target.getHost()).isEqualTo("order-api.fixture.internal");
        assertThat(target.getPort()).isEqualTo(80);
    }

    @Test
    void reads_generation_meta() {
        assertThat(load("fixture-provider.json").getGenerationMeta().getProfile()).isEqualTo("TEST");
    }

    @Test
    void fails_fast_when_a_resource_is_missing() {
        assertThatThrownBy(() -> load("no-such-file.json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no-such-file.json");
    }
}
