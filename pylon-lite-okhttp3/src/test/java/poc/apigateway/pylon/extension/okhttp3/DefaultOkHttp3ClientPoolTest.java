package poc.apigateway.pylon.extension.okhttp3;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import poc.apigateway.pylon.configuration.PylonConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실물 결함을 고정하는 테스트다. 여기서 단언하는 동작은 "옳은 동작"이 아니라
 * api-pylon-tools:2.14.9.RELEASE 가 실제로 하는 동작이다.
 *
 * <p>세 전송 중 read timeout 까지 흘리는 유일한 경로다. WebClient 는 풀만 놓쳤지만
 * OkHttp3 는 계약 자체가 specId 만 받아 옵션 캐리어가 도달조차 못 한다.
 */
class DefaultOkHttp3ClientPoolTest {

    private static final String ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001";
    private static final String PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002";

    @Test
    void the_read_timeout_is_hardcoded_and_ignores_every_configuration() {
        OkHttp3ClientPool pool = new DefaultOkHttp3ClientPool(configurationWith(500));

        OkHttpClient client = pool.get(ORDER_SPEC_ID);

        // jar 의 spec timeout 도, yml 의 read-timeout 도 여기 닿지 않는다.
        assertThat(client.readTimeoutMillis()).isEqualTo(3000);
    }

    @Test
    void the_connect_timeout_does_reach_the_client() {
        OkHttp3ClientPool pool = new DefaultOkHttp3ClientPool(configurationWith(550));

        assertThat(pool.get(ORDER_SPEC_ID).connectTimeoutMillis()).isEqualTo(550);
    }

    @Test
    void clients_are_cached_by_spec_id() {
        OkHttp3ClientPool pool = new DefaultOkHttp3ClientPool(configurationWith(500));

        assertThat(pool.get(ORDER_SPEC_ID)).isSameAs(pool.get(ORDER_SPEC_ID));
    }

    @Test
    void two_specs_with_identical_options_still_get_separate_clients() {
        OkHttp3ClientPool pool = new DefaultOkHttp3ClientPool(configurationWith(500));

        assertThat(pool.get(ORDER_SPEC_ID)).isNotSameAs(pool.get(PRODUCT_SPEC_ID));
    }

    private static PylonConfiguration configurationWith(int connectTimeout) {
        return new PylonConfiguration.Builder().connectionTimeout(connectTimeout).build();
    }
}
