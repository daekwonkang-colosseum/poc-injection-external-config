package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemeAndPortOverriderTest {

    @Test
    void falls_back_when_the_provider_has_no_override() {
        SchemeAndPortOverrider overrider =
            new SchemeAndPortOverrider(new PylonConfiguration.Builder().build());

        assertThat(overrider.has("order_api")).isFalse();
        assertThat(overrider.schemeOf("order_api", "https")).isEqualTo("https");
        assertThat(overrider.portOf("order_api", 443)).isEqualTo(443);
    }

    @Test
    void replaces_scheme_and_port_for_the_registered_provider() {
        SchemeAndPortOverrider overrider = new SchemeAndPortOverrider(new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).schemeAndPort("http", 8080).register()
            .build());

        assertThat(overrider.has("order_api")).isTrue();
        assertThat(overrider.schemeOf("order_api", "https")).isEqualTo("http");
        assertThat(overrider.portOf("order_api", 443)).isEqualTo(8080);
    }

    @Test
    void ignores_a_provider_that_configured_only_a_timeout() {
        SchemeAndPortOverrider overrider = new SchemeAndPortOverrider(new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).register()
            .build());

        assertThat(overrider.has("order_api")).isFalse();
        assertThat(overrider.portOf("order_api", 443)).isEqualTo(443);
    }
}
