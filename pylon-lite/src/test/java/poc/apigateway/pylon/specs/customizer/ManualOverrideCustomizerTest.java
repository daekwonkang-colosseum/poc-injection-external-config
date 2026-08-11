package poc.apigateway.pylon.specs.customizer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class ManualOverrideCustomizerTest {

    private Spec spec(String id, String provider) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void attaches_nothing_when_no_override_is_registered() {
        assertThat(new ManualOverrideCustomizer().process(spec("s1", "order_api")).getHostOverride()).isNull();
    }

    @Test
    void attaches_the_provider_override() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();
        customizer.registerProvider("order_api", HostOverride.of("http", "127.0.0.1", 9001));

        HostOverride override = customizer.process(spec("s1", "order_api")).getHostOverride();

        assertThat(override.getScheme()).isEqualTo("http");
        assertThat(override.getHost()).isEqualTo("127.0.0.1");
        assertThat(override.getPort()).isEqualTo(9001);
    }

    @Test
    void spec_override_beats_provider_override() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();
        customizer.registerProvider("order_api", HostOverride.of("http", "127.0.0.1", 9001));
        customizer.registerSpec("s1", HostOverride.of("https", "127.0.0.1", 9002));

        HostOverride override = customizer.process(spec("s1", "order_api")).getHostOverride();

        assertThat(override.getScheme()).isEqualTo("https");
        assertThat(override.getPort()).isEqualTo(9002);
    }

    @Test
    void carries_a_version_marker() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();
        assertThat(customizer.getVersion()).isZero();

        customizer.setVersion(2);
        assertThat(customizer.getVersion()).isEqualTo(2);
    }

    @Test
    void applies_at_runtime_too() {
        assertThat(new ManualOverrideCustomizer().isApplicableInRuntime()).isTrue();
    }
}
