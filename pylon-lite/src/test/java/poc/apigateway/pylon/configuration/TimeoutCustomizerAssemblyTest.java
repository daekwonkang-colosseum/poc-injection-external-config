package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.customizer.TimeoutCustomizer;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 함정 2: ApiGatewayAdapterConfig 는 per-spec timeout 등록을
 * provider 기본값이 있을 때만 수행한다. 기본값 없이 per-spec 만 주면 조용히 무시된다.
 */
class TimeoutCustomizerAssemblyTest {

    private Spec spec() {
        return Spec.builder("spec-order-1", "order_api", "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    private TimeoutCustomizer assemble(PylonConfiguration configuration) {
        return new ApiGatewayAdapterConfig().timeoutCustomizer(configuration);
    }

    @Test
    void per_spec_timeout_is_silently_dropped_without_a_provider_default() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").readTimeoutPerSpec("spec-order-1", 1500).register()
            .build();

        assertThat(assemble(configuration).process(spec()).getTimeout())
            .as("provider 기본값이 없으면 per-spec 이 등록되지 않는다 — 이것이 재현하려는 함정이다")
            .isEqualTo(3000);
    }

    @Test
    void per_spec_timeout_applies_once_a_provider_default_is_present() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api")
                .defaultReadTimeout(1000)
                .readTimeoutPerSpec("spec-order-1", 1500)
                .register()
            .build();

        assertThat(assemble(configuration).process(spec()).getTimeout()).isEqualTo(1500);
    }

    @Test
    void provider_default_alone_applies() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).register()
            .build();

        assertThat(assemble(configuration).process(spec()).getTimeout()).isEqualTo(1000);
    }
}
