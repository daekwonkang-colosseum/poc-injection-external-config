package poc.client.config

import poc.apigateway.pylon.specs.SpecResolver
import poc.apigateway.pylon.specs.model.Spec

/**
 * 원격 정책 갱신이 주입된 timeout 을 되돌리는 것(함정 6)에 대한 방어.
 *
 * 라이브러리 `TimeoutCustomizer` 는 `isApplicableInRuntime() == false` 라
 * `SpecResolver.update` 체인에서 빠진다. 그래서 원격 값이 도착하면 `@Primary` 로
 * 주입해 둔 timeout 이 사라진다. 이 커스터마이저는 `true` 를 돌려주므로 갱신 체인에도
 * 남아 매번 클라이언트 값을 다시 씌운다.
 *
 * **라이브러리를 고치지 않고 이것이 가능한 이유**는 `ApiGatewayAdapterConfig.specResolver`
 * 가 `List<SpecCustomizer>` 로 **리스트 주입**을 받기 때문이다. `@Primary` 는 리스트를
 * 중복 제거하지 않아 이 경로에서 무력하지만(design.md 가 지적한 `@Primary` 전략의 전제
 * — 모든 소비처가 타입 단건 주입 — 이 깨지는 유일한 지점이다), 반대로 빈을 하나 더
 * 등록하면 그대로 체인에 합류한다. 약점이 곧 탈출구다.
 *
 * 우선순위는 라이브러리와 같다 — per-spec 이 provider 기본값을 이긴다. 출처도 같은
 * [PylonClientProperty] 이므로 기동 시점에는 두 커스터마이저가 같은 값을 낸다.
 * 따라서 체인 내 순서에 관계없이 결과가 같다.
 */
class RuntimeTimeoutCustomizer(
    private val timeoutPerProvider: Map<String, Int>,
    private val timeoutPerSpec: Map<String, Int>,
) : SpecResolver.SpecCustomizer {

    override fun process(spec: Spec): Spec {
        val timeout = timeoutPerSpec[spec.id] ?: timeoutPerProvider[spec.provider] ?: return spec

        return Spec.builder(spec).setTimeout(timeout).build()
    }

    override fun isApplicableInRuntime(): Boolean = true

    companion object {

        /** 라이브러리 커스터마이저와 같은 출처에서 만든다. 두 값이 갈라질 여지를 없앤다. */
        fun from(property: PylonClientProperty): RuntimeTimeoutCustomizer =
            RuntimeTimeoutCustomizer(
                timeoutPerProvider = property.providers.mapValues { (_, provider) -> provider.readTimeout },
                timeoutPerSpec = property.providers.values
                    .flatMap { it.readTimeoutPerSpec.entries }
                    .associate { it.key to it.value },
            )
    }
}
