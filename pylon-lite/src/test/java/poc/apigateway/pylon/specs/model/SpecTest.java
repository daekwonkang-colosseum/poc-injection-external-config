package poc.apigateway.pylon.specs.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.customizer.HostOverride;

import static org.assertj.core.api.Assertions.assertThat;

class SpecTest {

    private Spec base() {
        return Spec.builder("spec-1", "order_api", "/api/v1/orders/{orderId}")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void builds_with_all_fields() {
        Spec spec = base();

        assertThat(spec.getId()).isEqualTo("spec-1");
        assertThat(spec.getProvider()).isEqualTo("order_api");
        assertThat(spec.getPath()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(spec.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(spec.getTimeout()).isEqualTo(3000);
        assertThat(spec.getConnectionPool().getName()).isEqualTo("shared");
        assertThat(spec.getConnectionPool().getSize()).isEqualTo(100);
        assertThat(spec.getHostOverride()).isNull();
    }

    @Test
    void copy_builder_changes_only_the_named_field() {
        Spec copy = Spec.builder(base()).setTimeout(1500).build();

        assertThat(copy.getTimeout()).isEqualTo(1500);
        assertThat(copy.getId()).isEqualTo("spec-1");
        assertThat(copy.getProvider()).isEqualTo("order_api");
        assertThat(copy.getPath()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(copy.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(copy.getConnectionPool().getName()).isEqualTo("shared");
    }

    @Test
    void copy_builder_leaves_the_original_untouched() {
        Spec original = base();
        Spec.builder(original).setTimeout(1).build();

        assertThat(original.getTimeout()).isEqualTo(3000);
    }

    @Test
    void carries_host_override() {
        Spec spec = Spec.builder(base())
            .setHostOverride(HostOverride.of("http", "127.0.0.1", 8080))
            .build();

        assertThat(spec.getHostOverride().getScheme()).isEqualTo("http");
        assertThat(spec.getHostOverride().getHost()).isEqualTo("127.0.0.1");
        assertThat(spec.getHostOverride().getPort()).isEqualTo(8080);
    }
}
