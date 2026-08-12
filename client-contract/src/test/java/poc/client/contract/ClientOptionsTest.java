package poc.client.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOptionsTest {

    @Test
    void rounds_the_read_timeout_up_to_100ms_and_adds_the_round_trip_allowance() {
        ClientOptions options = ClientOptions.of(500, 1500, "order_api", 20);

        assertThat(options.getReadTimeoutMillis()).isEqualTo(1600);
    }

    @Test
    void builds_the_cache_key_from_the_pool_name_and_the_adjusted_read_timeout() {
        ClientOptions options = ClientOptions.of(500, 1500, "order_api", 20);

        assertThat(options.cacheKey()).isEqualTo("order_api-1600");
    }

    @Test
    void different_pools_get_different_cache_keys_even_at_the_same_timeout() {
        ClientOptions order = ClientOptions.of(500, 1500, "order_api", 20);
        ClientOptions product = ClientOptions.of(500, 1500, "product_api", 20);

        assertThat(order.cacheKey()).isNotEqualTo(product.cacheKey());
    }

    @Test
    void passes_the_connect_timeout_through_without_adjustment() {
        ClientOptions options = ClientOptions.of(550, 1500, "order_api", 20);

        assertThat(options.getConnectTimeoutMillis()).isEqualTo(550);
    }

    @Test
    void passes_the_pool_name_and_size_through() {
        ClientOptions options = ClientOptions.of(500, 1500, "order_api", 20);

        assertThat(options.getPoolName()).isEqualTo("order_api");
        assertThat(options.getPoolSize()).isEqualTo(20);
    }
}
