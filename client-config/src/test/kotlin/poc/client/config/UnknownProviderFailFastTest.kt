package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import poc.apigateway.pylon.configuration.EnablePocApiGatewayAdapters
import org.springframework.context.annotation.Configuration

class UnknownProviderFailFastTest {

    @Configuration
    @EnablePocApiGatewayAdapters
    class Enable

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration::class.java
            )
        )
        .withUserConfiguration(Enable::class.java, PylonClientConfig::class.java)

    @Test
    fun `a misspelled provider name fails the context`() {
        runner.withPropertyValues(
            "pylon.client.providers.[order_apii].read-timeout=1000"
        ).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .hasRootCauseInstanceOf(IllegalStateException::class.java)
            assertThat(context.startupFailure!!.stackTraceToString())
                .contains("order_apii")
                .contains("order_api")
        }
    }

    @Test
    fun `an unknown spec id fails the context`() {
        runner.withPropertyValues(
            "pylon.client.providers.[order_api].read-timeout=1000",
            "pylon.client.providers.[order_api].read-timeout-per-spec.[deadbeef]=500"
        ).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure!!.stackTraceToString()).contains("deadbeef")
        }
    }

    @Test
    fun `a correct configuration starts`() {
        runner.withPropertyValues(
            "pylon.client.providers.[order_api].read-timeout=1000"
        ).run { context ->
            assertThat(context).hasNotFailed()
        }
    }
}
