package poc.apigateway.pylon.extension.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실물 결함을 고정하는 테스트다. 여기서 단언하는 동작은 "옳은 동작"이 아니라
 * api-pylon-tools:2.14.9.RELEASE 가 실제로 하는 동작이다.
 *
 * <p>계약을 적용한 구현(client-config)이 이 단언들을 뒤집는 것이 POC 의 내용이다.
 */
class DefaultWebClientPoolTest {

    @Test
    void different_pools_share_one_client_because_the_key_omits_the_pool_name() {
        WebClientPool pool = new DefaultWebClientPool(configuration());

        WebClient order = pool.get(spec("order_api", 1500));
        WebClient product = pool.get(spec("product_api", 1500));

        assertThat(order).isSameAs(product);
    }

    @Test
    void timeouts_in_the_same_bucket_share_one_client() {
        WebClientPool pool = new DefaultWebClientPool(configuration());

        WebClient rounded = pool.get(spec("order_api", 1500));
        WebClient unrounded = pool.get(spec("order_api", 1450));

        assertThat(rounded).isSameAs(unrounded);
    }

    @Test
    void different_timeout_buckets_get_different_clients() {
        WebClientPool pool = new DefaultWebClientPool(configuration());

        WebClient short_ = pool.get(spec("order_api", 1000));
        WebClient long_ = pool.get(spec("order_api", 3000));

        assertThat(short_).isNotSameAs(long_);
    }

    private static PylonConfiguration configuration() {
        return new PylonConfiguration.Builder().connectionTimeout(500).build();
    }

    private static Spec spec(String provider, int timeout) {
        return Spec.builder("6512a0b1c2d3e4f500000001", provider, "/api/v1/orders/{orderId}")
            .setTimeout(timeout)
            .setConnectionPool(new Spec.ConnectionPool(provider, 20))
            .build();
    }
}
