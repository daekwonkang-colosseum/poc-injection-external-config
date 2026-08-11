package poc.apigateway.pylon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.configuration.BuildConfigurations;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.configuration.SchemeAndPortOverrider;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.model.Spec;
import poc.apigateway.pylon.targets.TargetUriFinder;
import poc.apigateway.pylon.testsupport.StubApiServer;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicApiClientTest {

    private StubApiServer stub;

    private static class OrderRequest extends RequestBase {
        OrderRequest(String orderId) {
            addPathParam("orderId", orderId);
        }
    }

    public static class OrderPayload {
        private String orderId;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }
    }

    @BeforeEach
    void setUp() {
        stub = StubApiServer.start();
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private DynamicApiClient client(int specTimeout) {
        Spec spec = Spec.builder("spec-order-1", "order_api", "/api/v1/orders/{orderId}")
            .setMethod(HttpMethod.GET)
            .setTimeout(specTimeout)
            .setConnectionPool(new Spec.ConnectionPool("shared", 10))
            .setHostOverride(HostOverride.of("http", "127.0.0.1", stub.getPort()))
            .build();

        SpecResolver resolver =
            new SpecResolver(Collections.emptyList(), new Spec.ConnectionPool("shared", 10));
        resolver.register(spec);

        PylonConfiguration configuration = new PylonConfiguration.Builder().connectionTimeout(1000).build();
        RestTemplatePool pool =
            new RestTemplatePool(configuration, new HttpClientConnectionManagerFactory());

        // 실제 TargetUriFinder 를 쓴다. spec 에 hostOverride 가 있으므로 그것이 최우선으로 적용되어
        // fixture-initial.json 의 호스트를 무시하고 스텁으로 향한다.
        BuildConfigurations buildConfigurations = new BuildConfigurations(
            Collections.singletonList(() -> "fixture-provider.json"),
            () -> "fixture-initial.json",
            () -> "fixture-meta.json");
        TargetUriFinder uriFinder =
            new TargetUriFinder(buildConfigurations, new SchemeAndPortOverrider(configuration));

        return new DynamicApiClient(resolver, pool, uriFinder);
    }

    @Test
    void deserializes_a_successful_response() {
        stub.respond("/api/v1/orders/42", 200, "{\"orderId\":\"42\"}");

        OrderPayload payload = client(3000)
            .invokeAPI("spec-order-1", new OrderRequest("42"), OrderPayload.class);

        assertThat(payload.getOrderId()).isEqualTo("42");
        assertThat(stub.receivedPaths()).containsExactly("/api/v1/orders/42");
    }

    @Test
    void wraps_a_read_timeout_in_an_api_exception() {
        stub.respondAfter("/api/v1/orders/42", 900L, 200, "{\"orderId\":\"42\"}");

        assertThatThrownBy(() -> client(100)
            .invokeAPI("spec-order-1", new OrderRequest("42"), OrderPayload.class))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("spec-order-1");
    }

    @Test
    void wraps_a_server_error_with_its_status_code() {
        stub.respond("/api/v1/orders/42", 503, "{\"message\":\"down\"}");

        assertThatThrownBy(() -> client(3000)
            .invokeAPI("spec-order-1", new OrderRequest("42"), OrderPayload.class))
            .isInstanceOf(ApiException.class)
            .satisfies(thrown -> assertThat(((ApiException) thrown).getStatusCode()).isEqualTo(503));
    }

    @Test
    void fails_for_an_unregistered_spec_id() {
        assertThatThrownBy(() -> client(3000)
            .invokeAPI("nope", new OrderRequest("42"), OrderPayload.class))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("nope");
    }
}
