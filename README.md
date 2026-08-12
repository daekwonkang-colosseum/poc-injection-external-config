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
| `pylon-lite-webclient` | Java | **남의 코드.** WebClient 확장 미러. 실물 결함까지 보존 |
| `pylon-lite-okhttp3` | Java | **남의 코드.** OkHttp3 확장 미러. 실물 결함까지 보존 |
| `api-gateway-consumer-role-poc` | Java | **남의 코드.** 코드 생성기 산출물. 수정 금지 |
| `client-contract` | Java | **내 코드.** 전송 중립 옵션 계약. **의존성 0** |
| `client-config` | Kotlin | **내 코드.** 배선·전송 구현·테스트 |

`client-config` 는 "남의 코드" 모듈을 **한 줄도 수정하지 않는다.** 동작 변경은 오직 설정 주입과 `@Primary` 빈 대체로만 일어난다. 이 제약이 없으면 POC가 증명하는 것이 없다.

의존 방향: `client-config` → `api-gateway-consumer-role-poc` → `pylon-lite`, 그리고 `client-config` → `client-contract` (단방향. `client-contract` 는 pylon 도 Spring 도 모른다)

`client-contract/build.gradle.kts` 에는 `dependencies` 블록이 없다. 그것이 이 모듈의 설계 단언이다 — 컴파일 클래스패스에 BOM(버전 제약만 제공) 외에 아무것도 없으므로, 계약이 `java.*` 밖을 참조하면 **리뷰가 아니라 컴파일 에러로** 잡힌다.

```
./gradlew :client-contract:dependencies --configuration compileClasspath
compileClasspath - Compile classpath for source set 'main'.
\--- org.springframework.boot:spring-boot-dependencies:2.3.4.RELEASE
```

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

### 함정 4 — WebClient 확장이 커넥션 풀을 통째로 무시한다

`DefaultWebClientPool` 은 캐시 키를 **보정된 timeout 단독**으로 잡는다. 풀 이름이 키에 없으므로 timeout 이 같은 두 provider 가 하나의 `WebClient` 를 공유한다. 게다가 `spec.getConnectionPool()` 을 **아예 읽지 않아서** Reactor Netty 기본 프로바이더가 쓰이고, `pylon.client.providers.<name>.max-connection` 은 조용히 무동작이 된다.

보정식도 `RestTemplatePool` 과 따로 복제돼 있다 — 자체 `ROUND_TRIP_TIME` 상수까지 별도로 선언한다. 지금은 결과값이 같지만 한쪽만 바뀌면 조용히 갈라진다.

→ `DefaultWebClientPoolTest` (결함 고정) / `ContractWebClientPoolTest` (계약으로 복구)

### 함정 5 — OkHttp3 확장은 read timeout 이 3초로 고정돼 있다

```java
.readTimeout(3, SECONDS) // this value will be overridden on the fly
```

**저 주석은 사실이 아니다.** 전 소스에 per-call 오버라이드가 없고 어댑터는 `pool.get(specId).newCall(...)` 을 그대로 호출한다. `2.17.0` 에서도 이 파일은 동일하다.

원인은 구현 실수가 아니라 **계약 결함**이다. 풀 인터페이스 시그니처부터 다르다.

```java
RestTemplate get(Spec spec)          // RestTemplatePool
WebClient    get(Spec spec)          // WebClientPool
OkHttpClient get(String specId)      // OkHttp3ClientPool  ← 옵션 캐리어가 도달조차 못 한다
```

jar 의 `product_api` 스펙은 8000ms 인데 이 경로에서는 3000ms 가 된다. **5초가 조용히 잘린다.**

→ `DefaultOkHttp3ClientPoolTest` (결함 고정) / `OkHttp3PoolOverrideTest` (같은 컨텍스트에서 두 빈을 나란히 대조)

## 문서화한 위험 — provider 일괄 설정

`order_api` 스펙은 jar에서 3000ms, `product_api` 는 8000ms로 온다. 일부러 다르게 뒀다.

provider 단위 `read-timeout: 1000` 을 주면 **`product_api` 의 8000이 조용히 짓밟힌다.** 실제 jar에서도 스펙별 timeout은 1000~80000ms로 넓게 퍼져 있으므로, 오래 걸리는 스펙이 섞인 provider에 일괄 설정을 하면 안 된다.

→ `ProviderWideClobberTest` (위험 재현) / `ProviderWideClobberRestoredTest` (per-spec으로 복구)

## 전송 공통 옵션 계약

함정 4·5가 말하는 것은 하나다 — **외부 설정 → `Spec` 까지는 단일한데, `Spec` → 전송 사이에 공통 계약이 없다.** 그래서 전송마다 다르게 샌다.

계약은 `client-contract` 가 소유한다. 값 하나와 인터페이스 하나뿐이다.

```java
public final class ClientOptions {   // 보정식과 캐시 키가 존재하는 유일한 장소
    public static ClientOptions of(int connectTimeoutMillis, int readTimeoutMillis,
                                   String poolName, int poolSize);
    public String cacheKey();        // poolName + "-" + 보정된 readTimeout
}

public interface ClientPool<C> {
    C get(ClientOptions options);
}
```

전송별로 채워야 할 좌석:

| 전송 | 대체 방법 | timeout 좌석 | 풀 좌석 |
|---|---|---|---|
| **RestTemplate** | 대체하지 않음 (기준선) | 라이브러리 기본 | 라이브러리 기본 |
| **Apache HttpClient** | 신규 `ClientPool` | `RequestConfig` | `PoolingHttpClientConnectionManager` |
| **WebClient** | `WebClientPool` + `@Primary` | `ReadTimeoutHandler` | `ConnectionProvider.fixed(name, size)` |
| **OkHttp3** | `OkHttp3ClientPool` + `@Primary` | `newBuilder().readTimeout` | `Dispatcher` + `ConnectionPool` |
| **Feign** | 신규 (pylon 에 통합 없음) | `Request.Options` | `Client` 구현체 |

**`OkHttp3ClientPool.get(String specId)` 는 `SpecResolver` 를 주입받아 `specId → Spec → ClientOptions` 로 복원한다.** 라이브러리 시그니처는 한 글자도 바꾸지 않는다.

**Feign 은 좌석이 둘로 나뉜 유일한 전송이다.** `feign-core` 만 쓰므로 `feign.Client` 를 직접 구현해 계약이 만든 Apache 클라이언트를 물린다 — 계약이 전송을 가로질러 합성된다.

### 적합성 매트릭스

`TransportConformanceTest` 가 전송을 파라미터로 돌며 같은 `ClientOptions` 에 같은 유효 옵션이 나오는지 단언한다. 소켓 timeout 도달, timeout 내 정상 응답, 캐시 키 재사용, 풀별 분리 — 4개 × 4전송 = **16칸**.

**RestTemplate 은 계약을 적용하지 않고 기준선으로 남긴다.** 네 전송 중 실물이 처음부터 옳게 하던 유일한 경로이기 때문이다. 계약이 한 일은 규칙을 발명한 것이 아니라 **이 경로의 규칙을 나머지 셋으로 옮긴 것**이며, `RestTemplateBaselineTest` 가 두 값이 일치함을 못박는다. 라이브러리가 기준을 바꾸면 거기서 먼저 깨진다.

계약을 씌우지 않는 이유는 `RestTemplatePool` 이 인터페이스가 아닌 구체 클래스이고 `DynamicApiClient` 가 그 타입을 직접 주입받기 때문이다. `@Primary` 로 이기려면 상속뿐인데, 그러면 계약이 Spring 에 강결합된다.

## 왜 `@Primary` 인가 — 표준 훅이 전부 막혀 있다

"남의 빈을 밖에서 고친다"는 프레임워크 표준 훅이 셋 있는데 pylon 에는 전부 통하지 않는다.

| 훅 | 성립 조건 | pylon 실제 |
|---|---|---|
| `RestTemplateCustomizer` / `WebClientCustomizer` | 대상이 `RestTemplateBuilder` 를 경유해 생성 | ✗ `RestTemplatePool` 이 `new RestTemplate(factory)` 로 직접 생성 |
| `BeanPostProcessor` | 대상이 스프링 빈 | ✗ 풀 내부 `ConcurrentHashMap` 에 lazy 생성 — 빈이 아니다 |
| Apache HC `useSystemProperties()` | 빌더가 그 메서드를 호출 | ✗ 메인 경로는 호출하지 않는다 |

마지막 항목엔 각주가 필요하다. Apache HttpClient 4.5 의 `useSystemProperties()` 는 `http.maxConnections`·`http.keepAlive` 는 읽지만 **소켓/read timeout 계열은 읽지 않는다**(SSL·프록시·연결 3종만). 호출했더라도 JVM 옵션으로 timeout 을 넣는 길은 애초에 없었다.

그래서 `@Primary` 빈 대체가 우회로가 아니라 사실상 유일한 정공법이다. 그리고 라이브러리가 그것을 허용한다 — `DefaultWebClientPool`/`DefaultOkHttp3ClientPool` 의 javadoc 이 "User can override by providing new one and annotate `@Primary`" 라고 명시하고, 등록 지점에 `@ConditionalOnMissingBean` 이 없다. **함정 1이 전송 확장 빈에서 그대로 반복된다.**

## 이 문제의 오픈소스 지형

"HTTP 클라이언트 옵션을 외부 설정으로 주입한다"는 기능 자체는 이미 표준 해결 문제다. pylon 이 특이한 것은 주입 수단이 없어서가 아니라 **코드 생성 템플릿이 DI 좌석을 안 만들어서**다.

| 대안 | 네임스페이스 | pylon 대비 |
|---|---|---|
| Spring Cloud OpenFeign | `spring.cloud.openfeign.client.config.<name>.*` | refresh 시 `Request.Options` 갱신까지 지원 |
| Micronaut | `micronaut.http.services.<id>.{read-timeout,pool.max-connections}` | provider 별 풀까지 외부 설정 — 이 POC 와 같은 모델 |
| Quarkus REST Client | `quarkus.rest-client."<key>".{connect,read}-timeout` | 글로벌 + per-client 폴백 |
| Spring Boot 4 | `spring.http.client.service.group.<name>.*` | 네임드 그룹 |

대조가 가장 선명한 것은 같은 범주의 OSS 코드 생성기다. `openapi-generator` 의 Java `resttemplate` 템플릿은 `@Autowired public XxxApi(ApiClient apiClient)` 를 만들어 준다 — pylon 어댑터에는 그 좌석이 없다. 차이는 그것 하나다.

전송 계층을 못 고칠 때의 보완재로는 Resilience4j(`resilience4j.timelimiter.instances.<name>.timeoutDuration`, `bulkhead.instances.<name>.maxConcurrentCalls`)와 서비스 메시(Istio `VirtualService.timeout`, `DestinationRule.connectionPool`)가 있다. 다만 이들은 소켓 옵션을 통일하는 것이 아니라 상위에서 상한을 거는 것이라 "동일 유효 옵션" 단언은 성립하지 않는다.

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

전송 매트릭스만 보려면:

```bash
./gradlew :client-config:test --tests '*TransportConformanceTest'
```

전체 빌드는 테스트 147개로 `BUILD SUCCESSFUL` 이다 — pylon-lite 65, pylon-lite-webclient 3, pylon-lite-okhttp3 4, api-gateway-consumer-role-poc 4, client-contract 5, client-config 66.

신규 의존성(`reactor-netty`, `okhttp3`, `feign-core`)은 최초 1회 mavenCentral 해석이 필요하다. 버전은 `feign-core` 만 명시하고 나머지는 Spring Boot 2.3.4 BOM 에 위임한다 — 로컬 캐시의 okhttp 5.x 는 Kotlin 기반의 별개 API 라 실물(3.x) 미러링에 쓸 수 없다.

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

POC가 버린 것: 인증 토큰, 요청 서명, rate limit, precondition, 라우팅 정책 원격 갱신, API 시뮬레이션, dry-run, 로그 포매터, Fluent API. `TargetUriFinder.indexTargets` 는 provider 정책의 첫 번째 region·target만 읽는다 — `usage`/`routingType` 이 암시하는 가중치 라우팅은 구현하지 않았다.

WebClient/OkHttp3 확장은 **미러링했다.** 다만 실물이 함께 등록하는 어댑터 계층(`DefaultWebClientAdaptor`, `DefaultOkHttp3ClientAdaptor`, `HeaderExtractionWebFilter`)은 POC 가 버린 계층이라 옮기지 않았고, 확장 로딩도 실물의 `ExtensionConfigurationSelector`(생성 jar 의 `PylonCodeGeneratorVersion.supportsWebClientExtension()` 을 보고 자동 선택) 대신 클라이언트가 명시적으로 `@Import` 한다. 실물 패키지명의 오타 `ohkttp3` 는 정정해 옮겼다.

**Feign 은 미러링이 아니다.** 실물 전 소스에 feign 문자열이 0건이므로 대조할 실물이 없다 — 계약을 새 전송으로 확장 적용한 사례다.

### 원격 정책 갱신이 주입을 되돌린다 (범위 밖, 기록만)

`ApiProviderPolicyDeployer.updateTimeout` 이 원격 정책 값으로 `specResolver.update(...)` 를 호출하는데, `SpecResolver.update` 는 `isApplicableInRuntime() == true` 인 커스터마이저만 태운다. 그리고 실물 `TimeoutCustomizer` 는 **`false`** 다. 즉 `@Primary` 로 이겨 놓은 timeout 이 첫 정책 갱신 주기에 API Management 값으로 되돌아간다.

라우팅 정책 원격 갱신이 이 POC 의 non-goal 이므로 재현하지 않았다. 실제 운영에 적용할 때 반드시 확인해야 할 항목이라 여기 남긴다.

`PylonConfiguration` / `SpecResolver` / `SpecCustomizer` / `TimeoutCustomizer` / `ConnectionPoolCustomizer` / `RestTemplatePool` 의 이름과 흐름은 실물과 1:1이다.

## 설계 문서

이 POC가 만들어진 경위와 근거는 `docs/` 에 있다.

- [`docs/design.md`](docs/design.md) — 설계 스펙. 모듈 경계와 소유권 구분, 재현할 함정 3개의 선정 근거, 버린 기능 목록, 검증 전략.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) — 16개 태스크 구현 계획. 태스크마다 파일 목록·인터페이스·TDD 단계와 전체 코드가 들어 있다.

두 문서는 구현 전에 작성됐고, 구현 중 발견된 결함(예: `@SpringBootConfiguration` 이 `@ComponentScan` 을 포함하지 않아 `@Import` 가 필요하다는 점)은 코드가 정답이다. 문서는 의도의 기록이지 최신 명세가 아니다.
