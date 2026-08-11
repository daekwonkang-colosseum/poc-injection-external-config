package poc.apigateway.pylon.specs.customizer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutCustomizerTest {

    private Spec spec(String id, String provider, int timeout) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(timeout)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void leaves_the_spec_untouched_when_nothing_is_registered() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();

        assertThat(customizer.process(spec("s1", "order_api", 3000)).getTimeout()).isEqualTo(3000);
    }

    @Test
    void provider_timeout_replaces_the_jar_value() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", 1000);

        assertThat(customizer.process(spec("s1", "order_api", 3000)).getTimeout()).isEqualTo(1000);
    }

    @Test
    void provider_timeout_does_not_leak_to_other_providers() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", 1000);

        assertThat(customizer.process(spec("s2", "product_api", 8000)).getTimeout()).isEqualTo(8000);
    }

    @Test
    void spec_timeout_beats_provider_timeout() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", 1000);
        customizer.registerBySpec("s1", 1500);

        assertThat(customizer.process(spec("s1", "order_api", 3000)).getTimeout()).isEqualTo(1500);
    }

    @Test
    void applies_at_boot_time_only() {
        assertThat(new TimeoutCustomizer().isApplicableInRuntime()).isFalse();
    }
}
