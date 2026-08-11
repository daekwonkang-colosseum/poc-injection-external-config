package poc.apigateway.pylon.specs.customizer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolCustomizerTest {

    private Spec spec(String provider) {
        return Spec.builder("s1", provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void keeps_the_shared_pool_when_nothing_is_registered() {
        Spec result = new ConnectionPoolCustomizer().process(spec("order_api"));

        assertThat(result.getConnectionPool().getName()).isEqualTo("shared");
        assertThat(result.getConnectionPool().getSize()).isEqualTo(100);
    }

    @Test
    void assigns_a_dedicated_pool_to_the_registered_provider() {
        ConnectionPoolCustomizer customizer = new ConnectionPoolCustomizer();
        customizer.register("order_api", "order_api", 20);

        Spec result = customizer.process(spec("order_api"));

        assertThat(result.getConnectionPool().getName()).isEqualTo("order_api");
        assertThat(result.getConnectionPool().getSize()).isEqualTo(20);
    }

    @Test
    void first_registration_wins() {
        ConnectionPoolCustomizer customizer = new ConnectionPoolCustomizer();
        customizer.register("order_api", "order_api", 20);
        customizer.register("order_api", "order_api", 999);

        assertThat(customizer.process(spec("order_api")).getConnectionPool().getSize()).isEqualTo(20);
    }

    @Test
    void applies_at_runtime_too() {
        assertThat(new ConnectionPoolCustomizer().isApplicableInRuntime()).isTrue();
    }
}
