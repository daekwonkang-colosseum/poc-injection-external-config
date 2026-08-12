package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.model.Spec

class ClientOptionsFactoryTest {

    @Test
    fun `combines the spec timeout and pool with the configuration connect timeout`() {
        val factory = ClientOptionsFactory(
            PylonConfiguration.Builder().connectionTimeout(500).build()
        )

        val options = factory.of(specWith(timeout = 1500, poolName = "order_api", poolSize = 20))

        assertThat(options.connectTimeoutMillis).isEqualTo(500)
        assertThat(options.readTimeoutMillis).isEqualTo(1600)
        assertThat(options.poolName).isEqualTo("order_api")
        assertThat(options.poolSize).isEqualTo(20)
    }

    @Test
    fun `two specs sharing a pool and a timeout bucket produce the same cache key`() {
        val factory = ClientOptionsFactory(
            PylonConfiguration.Builder().connectionTimeout(500).build()
        )

        val rounded = factory.of(specWith(timeout = 1500, poolName = "pylon-common", poolSize = 1000))
        val unrounded = factory.of(specWith(timeout = 1450, poolName = "pylon-common", poolSize = 1000))

        assertThat(rounded.cacheKey()).isEqualTo(unrounded.cacheKey())
    }

    private fun specWith(timeout: Int, poolName: String, poolSize: Int): Spec =
        Spec.builder("6512a0b1c2d3e4f500000001", "order_api", "/api/v1/orders/{orderId}")
            .setTimeout(timeout)
            .setConnectionPool(Spec.ConnectionPool(poolName, poolSize))
            .build()
}
