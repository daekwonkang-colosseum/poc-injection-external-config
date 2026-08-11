package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PylonConfigurationTest {

    @Test
    void empty_builder_uses_library_defaults() {
        PylonConfiguration configuration = new PylonConfiguration.Builder().build();

        assertThat(configuration.getConnectionTimeout()).isEqualTo(3000);
        assertThat(configuration.getRoutingInfoDuration()).isEqualTo(60000);
        assertThat(configuration.getMaxConnection()).isNull();
        assertThat(configuration.getProviders()).isEmpty();
    }

    @Test
    void registers_provider_options() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .connectionTimeout(500)
            .maxConnection(1234)
            .provider("order_api")
                .defaultReadTimeout(1000)
                .maxConnection(20)
                .readTimeoutPerSpec("spec-order-1", 1500)
                .schemeAndPort("http", 8080)
                .register()
            .build();

        assertThat(configuration.getConnectionTimeout()).isEqualTo(500);
        assertThat(configuration.getMaxConnection()).isEqualTo(1234);

        List<PylonConfiguration.Provider> providers = new ArrayList<>(configuration.getProviders());
        assertThat(providers).hasSize(1);

        PylonConfiguration.Provider provider = providers.get(0);
        assertThat(provider.getName()).isEqualTo("order_api");
        assertThat(provider.getDefaultTimeout()).isEqualTo(1000);
        assertThat(provider.getMaxConnection()).isEqualTo(20);
        assertThat(provider.getReadTimeoutPerSpec()).containsEntry("spec-order-1", 1500);
        assertThat(provider.getScheme()).isEqualTo("http");
        assertThat(provider.getPort()).isEqualTo(8080);
    }

    @Test
    void provider_without_scheme_and_port_reports_null() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("product_api").defaultReadTimeout(2000).register()
            .build();

        PylonConfiguration.Provider provider = configuration.getProviders().iterator().next();
        assertThat(provider.getScheme()).isNull();
        assertThat(provider.getPort()).isNull();
        assertThat(provider.getMaxConnection()).isNull();
        assertThat(provider.getReadTimeoutPerSpec()).isEmpty();
    }

    @Test
    void returned_collections_are_defensive_copies() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).readTimeoutPerSpec("a", 1).register()
            .build();

        configuration.getProviders().clear();
        assertThat(configuration.getProviders()).hasSize(1);

        PylonConfiguration.Provider provider = configuration.getProviders().iterator().next();
        provider.getReadTimeoutPerSpec().clear();
        assertThat(provider.getReadTimeoutPerSpec()).hasSize(1);
    }
}
