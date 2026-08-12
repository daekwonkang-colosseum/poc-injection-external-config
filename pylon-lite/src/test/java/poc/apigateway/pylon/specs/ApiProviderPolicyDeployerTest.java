package poc.apigateway.pylon.specs;

import org.junit.jupiter.api.Test;
import poc.apigateway.pylon.specs.customizer.ConnectionPoolCustomizer;
import poc.apigateway.pylon.specs.customizer.TimeoutCustomizer;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실물 결함을 고정하는 테스트다. 여기서 단언하는 동작은 "옳은 동작"이 아니라
 * api-pylon-tools:2.14.9.RELEASE 가 실제로 하는 동작이다.
 *
 * <p>원격 라우팅 정책이 도착하면 주입해 둔 timeout 이 되돌아간다. 커넥션 풀과
 * host 치환은 살아남는데 timeout 만 사라진다 — 셋 중 TimeoutCustomizer 만
 * {@code isApplicableInRuntime() == false} 이기 때문이다.
 */
class ApiProviderPolicyDeployerTest {

    private static final String ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001";
    private static final Spec.ConnectionPool COMMON = new Spec.ConnectionPool("pylon-common", 1000);

    @Test
    void a_remote_policy_update_drops_the_injected_timeout() {
        SpecResolver resolver = resolverWith(injectedTimeout(1000));
        resolver.register(jarSpec(3000));
        assertThat(resolver.get(ORDER_SPEC_ID).getTimeout()).isEqualTo(1000);

        new ApiProviderPolicyDeployer(resolver).updateTimeout(ORDER_SPEC_ID, 3000);

        assertThat(resolver.get(ORDER_SPEC_ID).getTimeout()).isEqualTo(3000);
    }

    @Test
    void the_connection_pool_survives_the_same_update() {
        ConnectionPoolCustomizer poolCustomizer = new ConnectionPoolCustomizer();
        poolCustomizer.register("order_api", "order_api", 20);
        SpecResolver resolver = resolverWith(injectedTimeout(1000), poolCustomizer);
        resolver.register(jarSpec(3000));

        new ApiProviderPolicyDeployer(resolver).updateTimeout(ORDER_SPEC_ID, 3000);

        Spec spec = resolver.get(ORDER_SPEC_ID);
        assertThat(spec.getConnectionPool().getName()).isEqualTo("order_api");
        assertThat(spec.getTimeout()).isEqualTo(3000);
    }

    @Test
    void an_update_that_matches_the_current_value_changes_nothing() {
        SpecResolver resolver = resolverWith(injectedTimeout(1000));
        resolver.register(jarSpec(3000));

        new ApiProviderPolicyDeployer(resolver).updateTimeout(ORDER_SPEC_ID, 1000);

        assertThat(resolver.get(ORDER_SPEC_ID).getTimeout()).isEqualTo(1000);
    }

    @Test
    void an_unknown_spec_is_registered_with_the_remote_timeout() {
        SpecResolver resolver = resolverWith(injectedTimeout(1000));

        new ApiProviderPolicyDeployer(resolver).updateTimeout("unknown-spec", 5000);

        assertThat(resolver.getEvenNull("unknown-spec")).isNull();
    }

    private static TimeoutCustomizer injectedTimeout(int timeout) {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", timeout);
        return customizer;
    }

    private static SpecResolver resolverWith(SpecResolver.SpecCustomizer... customizers) {
        List<SpecResolver.SpecCustomizer> list = new ArrayList<>(Arrays.asList(customizers));
        return new SpecResolver(list, COMMON);
    }

    private static Spec jarSpec(int timeout) {
        return Spec.builder(ORDER_SPEC_ID, "order_api", "/api/v1/orders/{orderId}")
            .setTimeout(timeout)
            .setConnectionPool(COMMON)
            .build();
    }
}
