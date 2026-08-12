package poc.client.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import poc.apigateway.pylon.configuration.BuildConfigurations
import poc.apigateway.pylon.configuration.PylonConfiguration

/**
 * API-GW(pylon) HTTP Client 옵션을 프로파일별로 부여한다.
 *
 * pylon-lite 의 ApiGatewayAdapterConfig 는 PylonConfiguration 을
 * `@ConditionalOnMissingBean` 없이 `defaultPylonConfiguration()` 으로 등록한다.
 * 소비처(RestTemplatePool, SchemeAndPortOverrider, TimeoutCustomizer,
 * ConnectionPoolCustomizer, SpecResolver)가 모두 타입 단건 주입이므로,
 * 이름이 다른 @Primary 빈으로 대체한다.
 * 같은 이름(defaultPylonConfiguration)을 쓰면 빈 정의 충돌이 난다.
 *
 * @see PylonClientProperty yml 키 및 옵션별 주의사항
 */
@Configuration
@EnableConfigurationProperties(PylonClientProperty::class)
class PylonClientConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @Primary
    fun pylonConfiguration(
        property: PylonClientProperty,
        buildConfigurations: BuildConfigurations,
    ): PylonConfiguration {
        verifyTargetsExist(property, buildConfigurations)

        val builder = PylonConfiguration.Builder()
            .connectionTimeout(property.connectTimeout)
            .routingInfoDuration(property.routingInfoDuration)

        property.maxConnection?.let { builder.maxConnection(it) }

        property.providers.forEach { (name, provider) ->
            val providerBuilder = builder.provider(name)
                .defaultReadTimeout(provider.readTimeout)

            provider.maxConnection?.let { providerBuilder.maxConnection(it) }

            if (provider.schemeAndPortOverridden) {
                providerBuilder.schemeAndPort(provider.scheme!!, provider.port!!)
            }

            provider.readTimeoutPerSpec.forEach { (specId, readTimeout) ->
                providerBuilder.readTimeoutPerSpec(specId, readTimeout)
            }

            providerBuilder.register()
        }

        logApplied(property)

        return builder.build()
    }

    /**
     * 함정 6 방어 — 원격 정책 갱신이 주입된 timeout 을 되돌리는 것을 막는다.
     *
     * **`@Primary` 가 아니라 추가 빈이다.** `ApiGatewayAdapterConfig.specResolver` 는
     * `List<SpecCustomizer>` 로 리스트 주입을 받으므로 `@Primary` 가 무력하지만,
     * 반대로 빈을 하나 더 등록하면 그대로 체인에 합류한다.
     *
     * @see RuntimeTimeoutCustomizer 왜 라이브러리 커스터마이저만으로는 부족한지
     */
    @Bean
    fun runtimeTimeoutCustomizer(property: PylonClientProperty): RuntimeTimeoutCustomizer =
        RuntimeTimeoutCustomizer.from(property)

    /**
     * provider 명/specId 오타는 조용한 무동작으로 끝난다. 환경별로 튜닝하는 값이라
     * 그 침묵이 가장 비싸므로 부팅을 실패시킨다. 두 목록 모두 생성된 jar 에서
     * 오므로 배포마다 결정적이고, 로컬/CI 에서 먼저 걸린다.
     */
    private fun verifyTargetsExist(property: PylonClientProperty, buildConfigurations: BuildConfigurations) {
        val specIdsByProvider = buildConfigurations.providers
            .associate { dto -> dto.name to dto.specifications.orEmpty().map { it.id }.toSet() }

        property.providers.forEach { (name, provider) ->
            val specIds = specIdsByProvider[name]
                ?: throw IllegalStateException(
                    "생성된 jar 에 없는 provider: '$name'. " +
                        "사용 가능: ${specIdsByProvider.keys.sorted()}"
                )

            val unknownSpecIds = provider.readTimeoutPerSpec.keys - specIds
            if (unknownSpecIds.isNotEmpty()) {
                throw IllegalStateException(
                    "provider '$name' 에 없는 specId: ${unknownSpecIds.sorted()}"
                )
            }
        }
    }

    private fun logApplied(property: PylonClientProperty) {
        log.info(
            "API-GW client - connectTimeout={}ms, maxConnection={}, routingInfoDuration={}ms",
            property.connectTimeout,
            property.maxConnection ?: "default",
            property.routingInfoDuration
        )

        if (property.providers.isEmpty()) {
            log.info("API-GW client - provider 별 옵션 없음. 생성된 스펙의 timeout 을 그대로 사용한다.")
            return
        }

        property.providers.forEach { (name, provider) ->
            log.info(
                "API-GW client - provider={}, readTimeout={}ms, maxConnection={}, schemeAndPort={}, perSpec={}",
                name,
                provider.readTimeout,
                provider.maxConnection ?: "shared",
                if (provider.schemeAndPortOverridden) "${provider.scheme}:${provider.port}" else "default",
                provider.readTimeoutPerSpec
            )
        }
    }
}
