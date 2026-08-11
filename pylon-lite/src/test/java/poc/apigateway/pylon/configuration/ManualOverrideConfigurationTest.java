package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.customizer.ManualOverrideCustomizer;
import poc.apigateway.pylon.specs.model.Spec;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ManualOverrideConfigurationTest {

    private ManualOverrideCustomizer customizerFrom(MockEnvironment environment) {
        ManualOverrideConfiguration configuration = new ManualOverrideConfiguration();
        configuration.setEnvironment(environment);
        return configuration.apiGatewayManualOverrideProvider();
    }

    private Spec spec(String id, String provider) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void reads_a_provider_override_with_an_explicit_port() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.provider.order_api.server", "http://127.0.0.1:9001");

        HostOverride override = customizerFrom(environment).process(spec("s1", "order_api")).getHostOverride();

        assertThat(override.getScheme()).isEqualTo("http");
        assertThat(override.getHost()).isEqualTo("127.0.0.1");
        assertThat(override.getPort()).isEqualTo(9001);
    }

    @Test
    void defaults_the_port_from_the_scheme() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.provider.order_api.server", "https://order.example.com")
            .withProperty("api_gateway.manual_override.provider.product_api.server", "http://product.example.com");

        ManualOverrideCustomizer customizer = customizerFrom(environment);

        assertThat(customizer.process(spec("s1", "order_api")).getHostOverride().getPort()).isEqualTo(443);
        assertThat(customizer.process(spec("s2", "product_api")).getHostOverride().getPort()).isEqualTo(80);
    }

    @Test
    void reads_a_spec_level_override() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.spec.specorder1.server", "http://127.0.0.1:9002");

        HostOverride override =
            customizerFrom(environment).process(spec("specorder1", "order_api")).getHostOverride();

        assertThat(override.getPort()).isEqualTo(9002);
    }

    @Test
    void reads_the_version_marker() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.version", "3");

        assertThat(customizerFrom(environment).getVersion()).isEqualTo(3);
    }

    @Test
    void skips_an_unparseable_value_instead_of_failing() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.provider.order_api.server", ":::not a uri:::");

        assertThat(customizerFrom(environment).process(spec("s1", "order_api")).getHostOverride()).isNull();
    }

    @Test
    void ignores_unrelated_properties() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("pylon.client.connect-timeout", "500");

        assertThat(customizerFrom(environment).process(spec("s1", "order_api")).getHostOverride()).isNull();
    }
}
