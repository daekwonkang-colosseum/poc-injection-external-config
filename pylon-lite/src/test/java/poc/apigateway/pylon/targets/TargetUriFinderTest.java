package poc.apigateway.pylon.targets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.Pair;
import poc.apigateway.pylon.configuration.BuildConfigurations;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.configuration.SchemeAndPortOverrider;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.model.Spec;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetUriFinderTest {

    private BuildConfigurations buildConfigurations() {
        List<SpecConfigurationLocator> specs = Collections.singletonList(() -> "fixture-provider.json");
        InitialConfigurationLocator initial = () -> "fixture-initial.json";
        GenerationMetaLocator meta = () -> "fixture-meta.json";
        return new BuildConfigurations(specs, initial, meta);
    }

    private TargetUriFinder finder(PylonConfiguration configuration) {
        return new TargetUriFinder(buildConfigurations(), new SchemeAndPortOverrider(configuration));
    }

    private Spec spec(String provider, String path, HostOverride hostOverride) {
        return Spec.builder("spec-order-1", provider, path)
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .setHostOverride(hostOverride)
            .build();
    }

    @Test
    void uses_the_jar_target_when_nothing_overrides_it() {
        URI uri = finder(new PylonConfiguration.Builder().build())
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.singletonList(new Pair("orderId", "42")),
                Collections.emptyList());

        assertThat(uri.toString()).isEqualTo("http://order-api.fixture.internal:80/api/v1/orders/42");
    }

    @Test
    void scheme_and_port_override_keeps_the_jar_host() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).schemeAndPort("https", 8443).register()
            .build();

        URI uri = finder(configuration)
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.singletonList(new Pair("orderId", "42")),
                Collections.emptyList());

        assertThat(uri.toString()).isEqualTo("https://order-api.fixture.internal:8443/api/v1/orders/42");
    }

    @Test
    void host_override_wins_over_scheme_and_port_override() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).schemeAndPort("https", 8443).register()
            .build();

        URI uri = finder(configuration)
            .find(spec("order_api", "/api/v1/orders/{orderId}", HostOverride.of("http", "127.0.0.1", 9001)),
                Collections.singletonList(new Pair("orderId", "42")),
                Collections.emptyList());

        assertThat(uri.toString()).isEqualTo("http://127.0.0.1:9001/api/v1/orders/42");
    }

    @Test
    void appends_query_parameters() {
        URI uri = finder(new PylonConfiguration.Builder().build())
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.singletonList(new Pair("orderId", "42")),
                Arrays.asList(new Pair("verbose", "true"), new Pair("lang", "ko")));

        assertThat(uri.toString())
            .isEqualTo("http://order-api.fixture.internal:80/api/v1/orders/42?verbose=true&lang=ko");
    }

    @Test
    void fails_when_the_provider_has_no_routing_target() {
        assertThatThrownBy(() -> finder(new PylonConfiguration.Builder().build())
            .find(spec("unknown_api", "/api/v1/x", null), Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown_api");
    }

    @Test
    void fails_when_a_path_variable_is_not_supplied() {
        assertThatThrownBy(() -> finder(new PylonConfiguration.Builder().build())
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("orderId");
    }
}
