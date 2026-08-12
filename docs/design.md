# poc-injection-external-config 설계

작성일: 2026-08-11

## 1. 목적

**제어할 수 없는 라이브러리 jar가 이미 환경 값을 들고 있을 때, 외부 설정 주입으로 그것을 이기는 방법**을 재현 가능한 형태로 증명한다.

동기는 실제 사례다. `mycoupang-app` 은 `api-gateway-consumer-role-mycoupang` jar에서 생성된 어댑터(`TransportapiApiV6TrackingAdapter` 등)를 쓰는데, 어댑터는 옵션을 받는 생성자 자리가 없다. specId를 하드코딩해 공용 `DynamicApiClient` 에 위임할 뿐이다. timeout·커넥션풀·호스트는 그 아래 `Spec` / `RestTemplatePool` 레이어가 쥐고 있고, 그 값의 출처는 jar 안의 JSON이다.

이 POC는 그 구조를 최소 재현하고, 클라이언트 코드가 **모듈을 수정하지 않고** 옵션을 덮어쓰는 것을 테스트로 못박는다.

## 2. 범위

### 하는 것
- pylon 런타임의 최소 자기완결 재현 (외부 사내 저장소 의존 없음)
- 어댑터 2개(주문·상품)만 있는 생성 jar 모방 모듈
- 외부 설정 주입 모듈 + 통합테스트

### 하지 않는 것 (Non-goals)
- 실제 API-GW 프로토콜 준수 (인증 토큰, 요청 서명, rate limit, precondition)
- 라우팅 정책 **원격 fetch**(스케줄러·DTO 트리), API 시뮬레이션, dry-run
  — 단 원격 값이 *도착했을 때* 주입된 timeout 이 되돌아가는 문제(함정 6)는 2026-08-12 에 스코프로 들어왔다
- Fluent API — 전송 구현체가 아니라 호출 스타일이다. 실물 `GenericApiClient` 도
  `specResolver` → `restTemplatePool.get(spec)` 를 그대로 쓰므로 RestTemplate 경로에 이미 포함된다
- 실행 가능한 데모 앱 (검증은 통합테스트로만)
- 현재 `mycoupang-app` 코드베이스와의 연동 — POC는 완전 독립이다

### 범위 확장 (2026-08-12)

**WebClient/OkHttp3 확장은 non-goal 에서 목표로 승격됐다.** 이 문서를 쓴 시점의 목표는
"RestTemplate 한 경로에서 주입이 되는가" 였으나, 이후 목표가
**"여러 전송 구현체에서 하나의 외부 설정이 동일한 유효 옵션을 만드는가"** 로 확장됐다.

배경·설계 결정·전송별 좌석 매핑은 별도 문서에 있다.

- [`superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md`](superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md)
- [`superpowers/plans/2026-08-12-transport-uniform-config-injection.md`](superpowers/plans/2026-08-12-transport-uniform-config-injection.md)

이 문서(design.md)는 최초 설계 의도의 기록으로 남긴다. 아래 4~12절은 그 시점의 3모듈
구성을 서술하며, 현재 모듈 구성과 검증 전략은 README 와 위 spec 이 최신이다.

### 제약
- **`poc-injection-external-config/` 는 독립 Gradle 빌드다.** 루트 `settings.gradle.kts` 에 포함하지 않는다. 디렉토리째 다른 프로젝트로 옮겨서 그대로 빌드되어야 한다.
- 의존성은 **mavenCentral 만** 사용한다. 사내 저장소 접근 없이 빌드되어야 한다.
- **`client-config` 는 `pylon-lite` 와 `api-gateway-consumer-role-poc` 를 절대 수정하지 않는다.** 이것이 POC의 전제다. README에 명시하고, 테스트는 오직 설정 주입만으로 동작을 바꾼다.

## 3. 플랫폼

| 항목 | 값 | 근거 |
|---|---|---|
| Gradle | 6.5 (현재 레포 wrapper 재사용) | 검증된 조합 |
| Java | 8 | 실제 pylon(Spring 5.2 기반)과 동일 |
| Kotlin | 1.4.10 | 현재 레포와 동일 |
| Spring Boot | 2.3.4.RELEASE (BOM만, 플러그인 없음) | `@ConstructorBinding` 동작 동일 |
| 테스트 | JUnit 5 + `spring-boot-starter-test` | 추가 의존 없음 |
| 스텁 서버 | JDK `com.sun.net.httpserver.HttpServer` | 의존성 0, Java 8 내장 |

Spring Boot 플러그인은 쓰지 않는다. 실행 가능한 앱이 없으므로 `platform("org.springframework.boot:spring-boot-dependencies:2.3.4.RELEASE")` BOM만 import한다.

### MockWebServer 대신 JDK HttpServer

승인 시점엔 MockWebServer였으나 구현 직전 대체했다. okhttp3 mockwebserver가 로컬 Gradle 캐시에 없고(캐시엔 okhttp 5.3.2뿐 — Java 8 스택과 불일치) 받으려면 네트워크가 필요하다. 자기완결형이라는 결정과 어긋난다. JDK 내장 `HttpServer` 로 지연·상태코드·경로 검증이 모두 되므로 목적(소켓 레벨 timeout 증명)은 동일하다.

## 4. 모듈 구성

```
poc-injection-external-config/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
├── gradlew, gradlew.bat
├── README.md
├── pylon-lite/                      [Java]    "내가 못 건드리는 런타임"
├── api-gateway-consumer-role-poc/   [Java]    "내가 못 건드리는 생성 jar"
└── client-config/                   [Kotlin]  "내 코드" + 통합테스트
```

### 모듈이 3개인 이유

사용자 요구는 2개였다. 3개로 늘린 근거:

덮어써야 할 대상인 `defaultPylonConfiguration()` 빈은 **런타임**(실제로는 `api-pylon-tools`) 소유이고, 기본 timeout 값은 **생성 jar** 소유다. 둘을 한 모듈로 합치면 "어느 아티팩트가 무엇을 소유하는가"가 사라지는데, 그 소유권 분리가 이 POC의 주제다. 실제 세계에서도 두 아티팩트는 별개다.

의존 방향: `client-config` → `api-gateway-consumer-role-poc` → `pylon-lite`

## 5. pylon-lite (모듈 0)

패키지 루트 `poc.apigateway.pylon`. 실제 pylon의 클래스명·흐름을 1:1 미러링한다. 패키지만 다르다.

### 클래스 인벤토리

| 경로 | 클래스 | 역할 |
|---|---|---|
| `.` | `PylonToolsMarker` | 컴포넌트 스캔 기준점 |
| `.` | `ApiException` | 호출 실패 (RuntimeException) |
| `.` | `Pair` | name/value 쌍 |
| `.` | `RequestBase` | abstract. pathParams/queryParams/headerParams/body |
| `.` | `DynamicApiClient` | `@Component`. specId로 호출 수행 |
| `.` | `RestTemplatePool` | `@Component`. (poolName, timeout) 키로 RestTemplate 캐시 |
| `.` | `HttpClientConnectionManagerFactory` | `@Component`. poolName별 커넥션 매니저 |
| `configuration.generated` | `SpecConfigurationLocator` | interface `String getPath()` |
| `configuration.generated` | `InitialConfigurationLocator` | interface |
| `configuration.generated` | `GenerationMetaLocator` | interface |
| `configuration.generated` | `PylonCodeGeneratorVersion` | interface `getVersion()`, `getCompatibilityLevel()` |
| `configuration.dto` | `ProviderConfigurationDto` | `{name, specifications[]}` |
| `configuration.dto` | `ApiSpecificationConfigurationDto` | `{id, revision, type, path, method, produces, consumes, timeout}` |
| `configuration.dto` | `InitialConfigurationDto` | provider별 라우팅 타겟 |
| `configuration.dto` | `GenerationMetaDto` | `{profile, consumers, apiManagementHost}` |
| `configuration` | `BuildConfigurations` | `@Component`. `List<SpecConfigurationLocator>` 주입받아 JSON 로드 |
| `configuration` | `PylonConfiguration` | `Builder`/`ProviderBuilder`. **옵션 주입의 입구** |
| `configuration` | `SchemeAndPortOverrider` | `@Component`. provider별 scheme/port 치환 |
| `configuration` | `ManualOverrideConfiguration` | `@Configuration`. Environment 정규식 스캔 |
| `configuration` | `ApiGatewayAdapterConfig` | `@Configuration`. 조립의 중심 |
| `configuration` | `EnablePocApiGatewayAdapters` | `@Import(ApiGatewayAdapterConfig)` 애노테이션 |
| `specs.model` | `Spec` | `{id, provider, path, method, timeout, connectionPool, hostOverride}` + builder |
| `.` (`poc.apigateway.services`) | `ApiGatewayServiceMarker` | 스캔 기준점. **모듈 0 소유, 모듈 1이 같은 패키지에 클래스를 넣는다** |
| `.` (`poc.apigateway.configuration`) | `ApiGatewayConfigurationMarker` | 동일 |
| `specs` | `SpecResolver` | 커스터마이저 체인 + specId→Spec 레지스트리. `SpecCustomizer` 내부 interface |
| `specs.customizer` | `TimeoutCustomizer` | per-spec / per-provider timeout |
| `specs.customizer` | `ConnectionPoolCustomizer` | provider별 풀 |
| `specs.customizer` | `ManualOverrideCustomizer` | host 치환 |
| `specs.customizer` | `HostOverride` | `{scheme, host, port}` |
| `targets` | `TargetUriFinder` | `@Component`. 최종 URI 결정 |

### 의도적으로 재현하는 함정

POC의 실질 콘텐츠다. 셋 다 실제 `api-pylon-tools:2.14.9.RELEASE` 에서 확인한 동작이다.

**함정 1 — `@ConditionalOnMissingBean` 부재**

```java
// ApiGatewayAdapterConfig
@Bean
public PylonConfiguration defaultPylonConfiguration() {
    return new PylonConfiguration.Builder().build();
}
```

조건 애노테이션이 없다. 따라서 같은 이름의 빈을 정의하면 정의 충돌이 나고, **이름이 다른 `@Primary` 빈으로만 이길 수 있다.** 모든 소비처가 타입 단건 주입이어야 이 전략이 성립한다 — `RestTemplatePool`, `SchemeAndPortOverrider`, `timeoutCustomizer()`, `connectionPoolCustomizer()`, `specResolver()` 전부 그렇게 만든다.

**함정 2 — per-spec 등록이 provider 기본값 안에 갇힘**

```java
// ApiGatewayAdapterConfig.timeoutCustomizer()
if (provider.getDefaultTimeout() != null) {
    customizer.registerByProvider(provider.getName(), provider.getDefaultTimeout());
    for (Map.Entry<String, Integer> e : provider.getReadTimeoutPerSpec().entrySet()) {
        customizer.registerBySpec(e.getKey(), e.getValue());   // ← 바깥 if 안에 갇혀 있다
    }
}
```

provider 기본 timeout 없이 per-spec만 주면 **조용히 무시된다.** `client-config` 는 `readTimeout` 을 필수 파라미터로 만들어 이 함정을 컴파일 타임으로 끌어올린다.

**함정 3 — timeout 보정**

```java
// RestTemplatePool
private int uplifting(double timeout) {
    return (int)(Math.ceil(timeout / 100) * 100);   // 100ms 단위 올림
}
// createRestTemplate: readTimeout + ROUND_TRIP_TIME(100)
```

실효 read timeout = `ceil(t/100)*100 + 100`. 1500 설정 → 실제 1600ms. 테스트가 이 값을 단언한다.

### 버리는 것

UserAgent, ConsumerToken, Revision, ApiSimulation, 로그 포매터 전체, RateLimit, RequestSign, Precondition, 라우팅 정책 원격 fetch/스케줄러, ApiStatusVerifier, DryRun, FluentAPI, WebClient/OkHttp3 확장, ManualOverrideProxy(서블릿 전파).

## 6. api-gateway-consumer-role-poc (모듈 1)

role 명은 `poc`. 실제 jar의 네이밍 규칙을 그대로 따른다.

### 클래스

마커 클래스(`ApiGatewayServiceMarker`, `ApiGatewayConfigurationMarker`)는 **모듈 0이 소유한다.** 실제 pylon도 그렇다 — 마커는 `api-pylon-tools` 에 있고 생성 jar가 같은 패키지에 클래스를 채워 넣는다(split package). 이 구조 덕분에 모듈 0이 모듈 1을 참조하지 않고도 컴포넌트 스캔이 성립한다.

```
poc/apigateway/configuration/ApiGatewayConsumerRolePocPylonCodeGeneratorVersion.java
poc/apigateway/configuration/ApiGatewayConsumerRolePocGenerationMetaLocator.java
poc/apigateway/configuration/ApiGatewayConsumerRolePocInitialConfigurationLocator.java
poc/apigateway/configuration/OrderApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java
poc/apigateway/configuration/ProductApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java
poc/apigateway/services/order_api/OrderapiApiV1OrdersAdapter.java
poc/apigateway/services/order_api/model/RequestParamOfGetApiV1OrdersOrderId.java
poc/apigateway/services/order_api/model/OrderDto.java
poc/apigateway/services/product_api/ProductapiApiV1ProductsAdapter.java
poc/apigateway/services/product_api/model/RequestParamOfGetApiV1ProductsProductId.java
poc/apigateway/services/product_api/model/ProductDto.java
```

어댑터는 실제 생성 코드와 같은 형태 — 옵션을 받는 자리가 없다:

```java
@Component
public class OrderapiApiV1OrdersAdapter {
    private final DynamicApiClient apiClient;

    @Autowired
    public OrderapiApiV1OrdersAdapter(DynamicApiClient dynamicApiClient) {
        this.apiClient = dynamicApiClient;
    }

    public OrderDto getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId requestBase) {
        String specId = "6512a0b1c2d3e4f500000001";
        return apiClient.invokeAPI(specId, requestBase, OrderDto.class);
    }
}
```

`DynamicApiClient` 의 시그니처는 실물보다 단순화한다. 실제 pylon은
`invokeAPIForResponseEntity(specId, pathParams, queryParams, body, headerParams, formParams, ParameterizedTypeReference)` 를 쓰지만, POC는
`<T> T invokeAPI(String specId, RequestBase request, Class<T> responseType)` 하나로 줄인다.
미러링의 목적은 **옵션이 흘러가는 경로**를 같게 만드는 것이지 제네릭 시그니처를 같게 만드는 것이 아니다. 파라미터 추출은 `RequestBase` 가 이미 담당한다.

### 리소스 (클래스패스 루트)

`generation-meta.json`
```json
{ "profile": "PROD", "consumers": ["poc"], "apiManagementHost": "http://api-management.poc.internal" }
```

`initial_configuration.json` — provider별 실제 타겟 호스트. 이것이 "jar가 들고 있는 환경 값"이다.
```json
{
  "consumers": {
    "poc": {
      "routingPolicies": {
        "providers": [
          { "name": "order_api",   "regions": [{ "name": "TO_LOAD_BALANCER", "usage": 100, "routingType": "DIRECT",
              "targets": [{ "scheme": "HTTP", "host": "order-api.poc.internal", "port": 80 }] }] },
          { "name": "product_api", "regions": [{ "name": "TO_LOAD_BALANCER", "usage": 100, "routingType": "DIRECT",
              "targets": [{ "scheme": "HTTP", "host": "product-api.poc.internal", "port": 80 }] }] }
        ]
      }
    }
  }
}
```

`order_api_of_api-gateway-consumer-role-poc_configuration.json`
```json
{
  "name": "order_api",
  "specifications": [{
    "id": "6512a0b1c2d3e4f500000001", "revision": "6512a0b1c2d3e4f5000000a1",
    "type": "SINGLE", "path": "/api/v1/orders/{orderId}", "method": "get",
    "produces": ["application/json"], "consumes": [], "timeout": 3000
  }]
}
```

`product_api_of_api-gateway-consumer-role-poc_configuration.json`
```json
{
  "name": "product_api",
  "specifications": [{
    "id": "6512a0b1c2d3e4f500000002", "revision": "6512a0b1c2d3e4f5000000a2",
    "type": "SINGLE", "path": "/api/v1/products/{productId}", "method": "get",
    "produces": ["application/json"], "consumes": [], "timeout": 8000
  }]
}
```

**`product_api` 를 8000으로 두는 것은 의도적 연출이다.** 실제 jar에서도 spec별 timeout은 1000~80000ms로 넓게 퍼져 있다. provider 단위 `readTimeout` 을 주면 이 8000이 조용히 짓밟히는데, 그 위험을 `ProviderWideClobberTest` 가 문서화한다.

## 7. client-config (모듈 2)

패키지 `poc.client.config`. `mycoupang-app` 에 작성한 코드를 패키지만 바꿔 이식한다.

- `PylonClientProperty.kt` — `@ConstructorBinding` + `@ConfigurationProperties("pylon.client")`. `connectTimeout` / `maxConnection` / `routingInfoDuration` / `providers: Map<String, Provider>`. `Provider` 는 `readTimeout`(필수) / `maxConnection` / `readTimeoutPerSpec` / `scheme` / `port`.
- `PylonClientConfig.kt` — `@Bean @Primary fun pylonConfiguration(...)`. `verifyTargetsExist()` 로 provider명·specId를 `BuildConfigurations` 와 대조해 오타 시 기동 실패. 적용된 옵션을 기동 로그로 남긴다.

### 프로파일별 yml

`application.yml` (기본, 무변경)
```yaml
pylon:
  client:
    connect-timeout: 3000
```

`application-local.yml` — 스텁 서버로 치환 + 짧은 timeout
```yaml
pylon:
  client:
    connect-timeout: 500
    providers:
      "[order_api]":
        read-timeout: 1000
        max-connection: 20
```

`application-production.yml` — 긴 timeout
```yaml
pylon:
  client:
    connect-timeout: 2000
    providers:
      "[order_api]":
        read-timeout: 3000
        read-timeout-per-spec:
          "[6512a0b1c2d3e4f500000001]": 1500
```

provider 명에 `_` 가 있어 map key는 대괄호 표기가 필수다. Spring Boot는 소문자 영숫자와 `-` 외의 문자가 키에 있으면 대괄호를 요구한다.

## 8. 데이터 흐름

```
[모듈1 jar JSON]  timeout 3000/8000, host *.poc.internal
        │ BuildConfigurations ← List<SpecConfigurationLocator>
        ▼
   ApiGatewayAdapterConfig.specResolver(customizers, pylonConfiguration)
        │                                    ▲
        │       [모듈2] PylonClientConfig.pylonConfiguration()   @Primary
        │                                    ▲
        │               application-{profile}.yml   (pylon.client.*)
        ▼
   SpecResolver chain: Timeout → ConnectionPool → ManualOverride
        ▼
   Spec(timeout, connectionPool)
        ▼
   DynamicApiClient → RestTemplatePool.get(spec) → RestTemplate(readTimeout)
        ▼
   TargetUriFinder: initial_configuration host + schemeAndPort/manualOverride 치환
        ▼
   실제 HTTP → JDK HttpServer 스텁
```

### 치환 우선순위

호스트·scheme·port를 결정하는 출처가 셋이므로 순서를 못박는다. 높은 쪽이 이긴다.

1. `ManualOverrideCustomizer` — `api_gateway.manual_override.*` 프로퍼티. scheme·host·port를 통째로 교체한다.
2. `SchemeAndPortOverrider` — `pylon.client.providers.<name>.{scheme,port}`. **host는 건드리지 않고** scheme·port만 교체한다.
3. `initial_configuration.json` 의 provider 타겟 — jar 기본값.

### 커넥션 풀 기본값

`pylon.client.providers.<name>.max-connection` 이 없으면 그 provider의 `Spec` 은 **공용 기본 풀**을 쓴다. 기본 풀 크기는 `pylon.client.max-connection`, 그것도 없으면 `min(provider 수 × 500, 4000)` 이다 (실물과 동일한 식).

주입 경로가 두 개라는 점이 핵심이다:
1. **타입 빈 주입** — `@Primary PylonConfiguration`. 타입 안전, 컴파일 검증 가능, 구조적.
2. **프로퍼티 스캔** — `api_gateway.manual_override.provider.<name>.server`. 코드 0줄, 라이브러리가 Environment를 뒤져서 찾아감.

POC는 둘을 나란히 보여준다.

## 9. 에러 처리

부팅 시점 실패와 호출 시점 실패를 분리한다.

| 상황 | 처리 |
|---|---|
| 알 수 없는 provider명 / specId (yml 오타) | `IllegalStateException` → 컨텍스트 기동 실패 |
| jar JSON 로드 실패 / 파싱 실패 | 기동 실패 |
| read timeout 초과 | `ApiException` (원인 `ResourceAccessException` 보존) |
| 4xx / 5xx | `ApiException` (상태코드 보존) |
| 등록되지 않은 specId 호출 | `ApiException` |

오타를 기동 실패로 만드는 것은 의도적 선택이다. 환경별로 튜닝하는 값이라 조용한 무동작이 가장 비싼 실패 모드다. 두 목록 모두 jar에서 오므로 배포마다 결정적이고, 로컬·CI에서 먼저 걸린다.

## 10. 테스트

전부 `client-config` 모듈. 모듈 0·1은 수정하지 않는다.

| 테스트 | 증명하는 것 |
|---|---|
| `PrimaryOverrideTest` | `@Primary` 빈이 `defaultPylonConfiguration` 을 이긴다 |
| `ProviderReadTimeoutTest` | yml 값이 `Spec.timeout` 에 반영된다 |
| `PerSpecBeatsProviderTest` | per-spec timeout이 provider 기본을 이긴다 |
| `ProviderWideClobberTest` | provider-wide 설정이 jar의 8000을 짓밟는 것을 문서화 |
| `ReadTimeoutReachesSocketTest` | 스텁 지연 > timeout → `ApiException`, 지연 < timeout → 성공. 함정 3의 보정식(`ceil(t/100)*100+100`)도 함께 단언 |
| `ManualOverrideHostTest` | 프로퍼티 host 치환으로 스텁에 실제 도달 |
| `UnknownProviderFailFastTest` | provider 오타 시 컨텍스트 기동 실패 |

설정 출처는 테스트마다 다르다. `ProviderReadTimeoutTest` / `PerSpecBeatsProviderTest` 는 `@ActiveProfiles("local")` · `@ActiveProfiles("production")` 로 7절의 yml을 그대로 검증한다. 나머지 넷은 그 yml에 없는 조합(product_api provider-wide, 오타, 동적 포트)을 다루므로 `@SpringBootTest(properties = [...])` 로 인라인 지정한다 — 프로파일 yml을 테스트 편의를 위해 오염시키지 않는다.

`UnknownProviderFailFastTest` 는 컨텍스트 기동 자체가 실패해야 하므로 `@SpringBootTest` 를 쓰지 않고 `ApplicationContextRunner` 로 검증한다.

### 스텁 서버

`StubApiServer` 테스트 유틸. `com.sun.net.httpserver.HttpServer` 기반.
- 포트 0으로 바인딩해 OS가 빈 포트 할당 → `getPort()` 로 회수
- 경로별 응답 본문·상태코드·지연 등록
- 수신 요청 기록 (경로 검증용)
- `AutoCloseable`

동적 포트를 yml에 주입해야 하므로 테스트는 `ApplicationContextInitializer` 또는 `@DynamicPropertySource` 로 `api_gateway.manual_override.provider.order_api.server=http://127.0.0.1:{port}` 를 설정한다.

## 11. README

`poc-injection-external-config/README.md` 에 다음을 명시한다.
- POC의 목적과 실제 사례(mycoupang `TransportApi`) 연결
- 모듈 3개의 소유권 구분 — **모듈 0·1은 "남의 코드"로 취급하며 수정 금지**
- 재현한 함정 3개와 각각을 잡는 테스트
- 실제 pylon으로 갈아끼울 때의 차이점 (패키지명, 버린 기능 목록)
- 실행법: `./gradlew test`

## 12. 열린 위험

- **pylon-lite가 실물과 어긋날 위험.** 미러링 대상은 `api-pylon-tools:2.14.9.RELEASE` 로 고정한다. README에 버전을 명시해 나중에 대조 가능하게 한다.
- **파일 수가 많다** (약 55개: pylon-lite 27, consumer-role 13+리소스 4, client-config 2+yml 3+테스트 8). 각각은 10~60줄로 작지만 총량이 크다. 구현 계획에서 모듈 0 → 1 → 2 순으로 쪼개고, 각 단계마다 컴파일이 통과하는지 확인한다.
- **프로퍼티 네임스페이스가 두 개다.** 타입 빈 경로는 `pylon.client.*`, 프로퍼티 스캔 경로는 `api_gateway.manual_override.*` 다. 일관성이 없어 보이지만 **의도적**이다 — 후자는 실제 pylon이 쓰는 키를 그대로 미러링해야 POC가 실물에 대응된다. README에 이 이유를 적는다.
- **Gradle 6.5 + Java 8 + mavenCentral 조합의 오프라인 빌드 가능 여부.** Boot 2.3.4 의존 대부분은 현재 레포 캐시에 있을 것으로 보이나 확인이 필요하다. 최초 빌드 시 네트워크가 필요하면 그 사실을 보고한다.
