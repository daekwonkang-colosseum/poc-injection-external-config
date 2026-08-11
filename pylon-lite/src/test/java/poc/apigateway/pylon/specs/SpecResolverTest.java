package poc.apigateway.pylon.specs;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.ApiException;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecResolverTest {

    private static final Spec.ConnectionPool DEFAULT_POOL = new Spec.ConnectionPool("shared", 100);

    private Spec spec(String id, String provider) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(DEFAULT_POOL)
            .build();
    }

    /** 호출 순서를 기록하며 timeout 에 표식을 남기는 커스터마이저. */
    private static class Recording implements SpecResolver.SpecCustomizer {
        private final List<String> log;
        private final String name;
        private final int timeout;
        private final boolean runtime;

        Recording(List<String> log, String name, int timeout, boolean runtime) {
            this.log = log;
            this.name = name;
            this.timeout = timeout;
            this.runtime = runtime;
        }

        @Override
        public Spec process(Spec spec) {
            log.add(name);
            return Spec.builder(spec).setTimeout(timeout).build();
        }

        @Override
        public boolean isApplicableInRuntime() {
            return runtime;
        }
    }

    @Test
    void applies_customizers_in_list_order_on_register() {
        List<String> log = new ArrayList<>();
        SpecResolver resolver = new SpecResolver(Arrays.asList(
            new Recording(log, "first", 1000, false),
            new Recording(log, "second", 2000, false)), DEFAULT_POOL);

        resolver.register(spec("s1", "order_api"));

        assertThat(log).containsExactly("first", "second");
        assertThat(resolver.get("s1").getTimeout()).isEqualTo(2000);
    }

    @Test
    void register_ignores_duplicate_ids() {
        List<String> log = new ArrayList<>();
        SpecResolver resolver = new SpecResolver(
            Collections.singletonList(new Recording(log, "only", 1000, false)), DEFAULT_POOL);

        resolver.register(spec("s1", "order_api"));
        resolver.register(spec("s1", "order_api"));

        assertThat(log).containsExactly("only");
    }

    @Test
    void update_applies_only_runtime_applicable_customizers() {
        List<String> log = new ArrayList<>();
        SpecResolver resolver = new SpecResolver(Arrays.asList(
            new Recording(log, "boot-only", 1000, false),
            new Recording(log, "runtime", 2000, true)), DEFAULT_POOL);

        resolver.register(spec("s1", "order_api"));
        log.clear();

        resolver.update(spec("s1", "order_api"));

        assertThat(log).containsExactly("runtime");
        assertThat(resolver.get("s1").getTimeout()).isEqualTo(2000);
    }

    @Test
    void update_ignores_unregistered_ids() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);

        resolver.update(spec("nope", "order_api"));

        assertThat(resolver.getEvenNull("nope")).isNull();
    }

    @Test
    void get_throws_api_exception_for_unknown_id() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);

        assertThatThrownBy(() -> resolver.get("missing"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void collects_provider_names() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);
        resolver.register(spec("s1", "order_api"));
        resolver.register(spec("s2", "product_api"));

        assertThat(resolver.getProviderNames()).containsExactlyInAnyOrder("order_api", "product_api");
    }

    @Test
    void exposes_the_default_connection_pool() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);

        assertThat(resolver.getDefaultConnectionPool().getName()).isEqualTo("shared");
        assertThat(resolver.getDefaultConnectionPool().getSize()).isEqualTo(100);
    }
}
