package poc.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import poc.apigateway.pylon.configuration.EnablePocApiGatewayAdapters;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.services.order_api.OrderapiApiV1OrdersAdapter;
import poc.apigateway.services.product_api.ProductapiApiV1ProductsAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerRolePocWiringTest {

    static final String ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001";
    static final String PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002";

    @Configuration
    @EnablePocApiGatewayAdapters
    static class Enable {
    }

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(Enable.class);

    @Test
    void discovers_both_adapters_by_component_scan() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OrderapiApiV1OrdersAdapter.class);
            assertThat(context).hasSingleBean(ProductapiApiV1ProductsAdapter.class);
        });
    }

    @Test
    void registers_specs_with_the_timeouts_baked_into_the_jar() {
        runner.run(context -> {
            SpecResolver resolver = context.getBean(SpecResolver.class);

            assertThat(resolver.get(ORDER_SPEC_ID).getTimeout()).isEqualTo(3000);
            assertThat(resolver.get(PRODUCT_SPEC_ID).getTimeout())
                .as("product_api 는 일부러 다른 값을 갖는다 — provider 일괄 설정의 위험을 보여주기 위해")
                .isEqualTo(8000);
        });
    }

    @Test
    void registers_both_providers() {
        runner.run(context -> assertThat(context.getBean(SpecResolver.class).getProviderNames())
            .containsExactlyInAnyOrder("order_api", "product_api"));
    }

    @Test
    void exposes_the_jar_default_hosts() {
        runner.run(context -> {
            assertThat(context.getBean(SpecResolver.class).get(ORDER_SPEC_ID).getHostOverride())
                .as("설정 주입이 없으면 치환도 없다")
                .isNull();
        });
    }
}
