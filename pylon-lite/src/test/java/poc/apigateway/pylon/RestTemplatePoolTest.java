package poc.apigateway.pylon;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class RestTemplatePoolTest {

    private RestTemplatePool pool(PylonConfiguration configuration) {
        return new RestTemplatePool(configuration, new HttpClientConnectionManagerFactory());
    }

    private Spec spec(String poolName, int timeout) {
        return Spec.builder("s-" + timeout, "order_api", "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(timeout)
            .setConnectionPool(new Spec.ConnectionPool(poolName, 20))
            .build();
    }

    @Test
    void rounds_the_timeout_up_to_100ms_and_adds_the_round_trip_allowance() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.readTimeoutOf(spec("shared", 1500))).isEqualTo(1600);
        assertThat(pool.readTimeoutOf(spec("shared", 1501))).isEqualTo(1700);
        assertThat(pool.readTimeoutOf(spec("shared", 3000))).isEqualTo(3100);
        assertThat(pool.readTimeoutOf(spec("shared", 1))).isEqualTo(200);
    }

    @Test
    void reuses_one_rest_template_per_pool_and_timeout() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        RestTemplate first = pool.get(spec("shared", 1500));
        RestTemplate second = pool.get(spec("shared", 1500));

        assertThat(first).isSameAs(second);
    }

    @Test
    void timeouts_that_round_to_the_same_bucket_share_one_rest_template() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.get(spec("shared", 1401))).isSameAs(pool.get(spec("shared", 1500)));
    }

    @Test
    void different_timeouts_get_different_rest_templates() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.get(spec("shared", 1500))).isNotSameAs(pool.get(spec("shared", 3000)));
    }

    @Test
    void different_pools_get_different_rest_templates() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.get(spec("shared", 1500))).isNotSameAs(pool.get(spec("order_api", 1500)));
    }

    @Test
    void takes_the_connection_timeout_from_the_configuration() {
        assertThat(pool(new PylonConfiguration.Builder().build()).getConnectionTimeout()).isEqualTo(3000);
        assertThat(pool(new PylonConfiguration.Builder().connectionTimeout(500).build())
            .getConnectionTimeout()).isEqualTo(500);
    }

    @Test
    void connection_manager_factory_reuses_managers_by_name() {
        HttpClientConnectionManagerFactory factory = new HttpClientConnectionManagerFactory();

        assertThat(factory.getOrCreate("shared", 100)).isSameAs(factory.getOrCreate("shared", 100));
        assertThat(factory.getOrCreate("shared", 100)).isNotSameAs(factory.getOrCreate("order_api", 20));
    }
}
