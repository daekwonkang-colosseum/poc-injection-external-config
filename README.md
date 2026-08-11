# poc-injection-external-config

**제어할 수 없는 라이브러리 jar가 이미 환경 값을 들고 있을 때, 외부 설정 주입으로 그것을 이기는 방법**을 증명하는 POC.

## 왜 만들었나

실제 사례에서 출발했다. 어떤 서비스가 사내 API-GW 코드 생성기(`api-pylon`)로 만들어진 어댑터를 쓰는데, 생성된 어댑터에는 옵션을 받는 자리가 없다.

```java
@Component
public class TransportapiApiV6TrackingAdapter {
    public TransportapiApiV6TrackingAdapter(DynamicApiClient dynamicApiClient) { ... }

    public TrackingDetailInfoResultDTO getApiV6TrackingRtTrackingInfo(RequestParam req) {
        String specId = "5bebe1d950c57d003a783912";   // 하드코딩
        return apiClient.invokeAPIForResponseEntity(specId, ...);
    }
}
```

timeout·커넥션풀·호스트는 어댑터가 아니라 그 아래 `Spec` / `RestTemplatePool` 레이어가 쥐고 있고, 그 값의 출처는 **jar 안의 JSON**이다. 그러면 환경별로 다르게 동작시키려면 어디에 손을 대야 하는가? 이 POC의 답이다.

## 모듈 소유권 — 가장 중요한 규칙

| 모듈 | 언어 | 소유권 |
|---|---|---|
| `pylon-lite` | Java | **남의 코드.** 라이브러리 런타임. 수정 금지 |
| `api-gateway-consumer-role-poc` | Java | **남의 코드.** 코드 생성기 산출물. 수정 금지 |
| `client-config` | Kotlin | **내 코드.** 여기서만 작업한다 |

`client-config` 는 앞의 두 모듈을 **한 줄도 수정하지 않는다.** 동작 변경은 오직 설정 주입으로만 일어난다. 이 제약이 없으면 POC가 증명하는 것이 없다.

의존 방향: `client-config` → `api-gateway-consumer-role-poc` → `pylon-lite`

## 두 개의 주입 경로

### 1. 타입 빈 주입 — `@Primary PylonConfiguration`

`pylon-lite` 의 `ApiGatewayAdapterConfig` 는 `PylonConfiguration` 빈을 기본 제공하되 **`@ConditionalOnMissingBean` 을 붙이지 않는다.** 따라서 같은 이름으로 정의하면 빈 정의 충돌이 나고, **이름이 다른 `@Primary` 빈으로만 이길 수 있다.**

```kotlin
@Bean
@Primary
fun pylonConfiguration(property: PylonClientProperty, buildConfigurations: BuildConfigurations) =
    PylonConfiguration.Builder()
        .connectionTimeout(property.connectTimeout)
        .provider("order_api").defaultReadTimeout(1000).register()
        .build()
```

yml:

```yaml
pylon:
  client:
    connect-timeout: 500
    providers:
      "[order_api]":            # provider 명에 '_' 가 있어 대괄호 표기 필수
        read-timeout: 1000
        max-connection: 20
        read-timeout-per-spec:
          "[6512a0b1c2d3e4f500000001]": 1500
```

타입 안전하고, 오타를 기동 시점에 잡을 수 있다.

### 2. 프로퍼티 스캔 — `api_gateway.manual_override.*`

`ManualOverrideConfiguration` 이 `Environment` 를 정규식으로 훑어 host를 통째로 갈아치운다. 코드 0줄.

```yaml
api_gateway:
  manual_override:
    version: 1               # 선택. 어떤 production 코드도 읽지 않는다
    provider:
      order_api:
        server: http://127.0.0.1:9001
```

**네임스페이스가 두 개인 것은 의도적이다.** 후자는 실제 `api-pylon` 이 쓰는 키를 그대로 미러링해야 POC가 실물에 대응된다.

### 치환 우선순위

높은 쪽이 이긴다.

1. `api_gateway.manual_override.*` — scheme·host·port 통째 교체
2. `pylon.client.providers.<name>.{scheme,port}` — scheme·port만, **host는 jar 값 유지**
3. `initial_configuration.json` 의 provider 타겟 — jar 기본값

2번을 쓰려면 `Provider.readTimeout` 이 필수 파라미터이므로 provider 단위 `read-timeout` 도 함께 줘야 하는데, 그 순간 뒤에서 설명하는 provider 일괄 설정 클로버 위험이 그대로 발동한다 — 스펙별로 더 긴 jar timeout이 있었다면 per-spec 항목으로 복구해야 한다.

## 재현한 함정 3개

이게 POC의 실질 콘텐츠다. 셋 다 `api-pylon-tools:2.14.9.RELEASE` 에서 확인한 실제 동작이다.

### 함정 1 — `@ConditionalOnMissingBean` 부재

기본 빈에 조건이 없다. `@Primary` 로만 이길 수 있고, 그 전략이 성립하려면 **모든 소비처가 타입 단건 주입**이어야 한다.

→ `PrimaryOverrideTest`

### 함정 2 — per-spec 등록이 provider 기본값 안에 갇힘

```java
if (provider.getDefaultTimeout() != null) {
    customizer.registerByProvider(...);
    for (Map.Entry<String, Integer> e : provider.getReadTimeoutPerSpec().entrySet()) {
        customizer.registerBySpec(e.getKey(), e.getValue());   // ← 바깥 if 안에 갇혀 있다
    }
}
```

provider 기본 timeout 없이 per-spec만 주면 **조용히 무시된다.** `client-config` 는 `readTimeout` 을 필수 파라미터로 만들어 이 함정을 기동 시점으로 끌어올린다.

→ `TimeoutCustomizerAssemblyTest`, `PerSpecBeatsProviderTest`

### 함정 3 — timeout 보정

실효 read timeout = `ceil(t/100)*100 + 100`. **1500 설정 → 실제 1600ms.**

→ `RestTemplatePoolTest`, `ReadTimeoutReachesSocketTest`

## 문서화한 위험 — provider 일괄 설정

`order_api` 스펙은 jar에서 3000ms, `product_api` 는 8000ms로 온다. 일부러 다르게 뒀다.

provider 단위 `read-timeout: 1000` 을 주면 **`product_api` 의 8000이 조용히 짓밟힌다.** 실제 jar에서도 스펙별 timeout은 1000~80000ms로 넓게 퍼져 있으므로, 오래 걸리는 스펙이 섞인 provider에 일괄 설정을 하면 안 된다.

→ `ProviderWideClobberTest` (위험 재현) / `ProviderWideClobberRestoredTest` (per-spec으로 복구)

## 알려진 한계 — 기본값 중복

`PylonClientProperty` 의 `DEFAULT_CONNECT_TIMEOUT`·`DEFAULT_ROUTING_INFO_DURATION` 은 `PylonConfiguration` 내부의 `private static` 값(각각 3000ms, 60000ms)을 그대로 베껴 온 상수다. `pylonConfiguration()` 은 `connectionTimeout(property.connectTimeout)` 과 `routingInfoDuration(property.routingInfoDuration)` 을 조건 없이 항상 호출하므로, 클라이언트 프로퍼티의 기본값이 무조건 이긴다. 라이브러리가 나중에 자기 기본값을 바꾸면 클라이언트는 그 변경을 모른 채 옛 값에 조용히 고정된다. 라이브러리 값이 `private` 이라 참조할 수 없어서, 라이브러리를 고치지 않는 한 이 POC 구조로는 해결할 수 없다.

## 실행

```bash
cd poc-injection-external-config
./gradlew test          # 전체 테스트
./gradlew build         # 컴파일 + 테스트
```

프로파일별 동작을 보려면:

```bash
./gradlew :client-config:test --tests '*ProviderReadTimeoutTest'   # local  프로파일
./gradlew :client-config:test --tests '*PerSpecBeatsProviderTest'  # production 프로파일
```

전체 빌드는 테스트 93개(pylon-lite 65 + api-gateway-consumer-role-poc 4 + client-config 24)로 `BUILD SUCCESSFUL` 이다.

## 스텁 서버

외부 테스트 의존을 쓰지 않는다. JDK 내장 `com.sun.net.httpserver.HttpServer` 로 만든 `StubApiServer` (`pylon-lite` 의 `testFixtures`)가 지연·상태코드·경로 검증을 담당한다. MockWebServer를 쓰지 않은 이유는 자기완결성이다 — 이 디렉토리는 네트워크 없이도(의존성 캐시만 있으면) 빌드되어야 한다.

동적 포트는 `@DynamicPropertySource` 로 `api_gateway.manual_override.*` 에 주입한다. 스텁은 `companion object` 에서 시작한다 — 프로퍼티 등록이 컨텍스트 생성 전에 일어나야 하기 때문이다.

## 실제 pylon으로 갈아끼울 때

| 항목 | POC | 실제 |
|---|---|---|
| 패키지 | `poc.apigateway.pylon.*` | `com.coupang.apigateway.pylon.*` |
| Enable 애노테이션 | `@EnablePocApiGatewayAdapters` | `@EnableApiGatewayAdapters` |
| provider 목록 접근 | `buildConfigurations.providers` | `buildConfigurations.gradlePluginGeneratingDtoLoader.providers` |
| 호출 시그니처 | `invokeAPI(specId, request, Class)` | `invokeAPIForResponseEntity(specId, pathParams, queryParams, body, headerParams, formParams, ParameterizedTypeReference)` |

POC가 버린 것: 인증 토큰, 요청 서명, rate limit, precondition, 라우팅 정책 원격 갱신, API 시뮬레이션, dry-run, 로그 포매터, Fluent API, WebClient/OkHttp3 확장. `TargetUriFinder.indexTargets` 는 provider 정책의 첫 번째 region·target만 읽는다 — `usage`/`routingType` 이 암시하는 가중치 라우팅은 구현하지 않았다.

`PylonConfiguration` / `SpecResolver` / `SpecCustomizer` / `TimeoutCustomizer` / `ConnectionPoolCustomizer` / `RestTemplatePool` 의 이름과 흐름은 실물과 1:1이다.

## 설계 문서

이 POC가 만들어진 경위와 근거는 `docs/` 에 있다.

- [`docs/design.md`](docs/design.md) — 설계 스펙. 모듈 경계와 소유권 구분, 재현할 함정 3개의 선정 근거, 버린 기능 목록, 검증 전략.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — 16개 태스크 구현 계획. 태스크마다 파일 목록·인터페이스·TDD 단계와 전체 코드가 들어 있다.

두 문서는 구현 전에 작성됐고, 구현 중 발견된 결함(예: `@SpringBootConfiguration` 이 `@ComponentScan` 을 포함하지 않아 `@Import` 가 필요하다는 점)은 코드가 정답이다. 문서는 의도의 기록이지 최신 명세가 아니다.
