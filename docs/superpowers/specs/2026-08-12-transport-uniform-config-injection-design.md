# 전송 구현체 공통 옵션 주입 계약 설계

날짜: 2026-08-12
상태: APPROVED

## 배경

POC는 현재 **RestTemplate 단일 전송**에서만 외부 설정 주입을 증명한다. 그런데 실물 `api-pylon-tools:2.14.9.RELEASE` 는 RestTemplate 외에 WebClient·OkHttp3 확장을 제공하고, 각 전송이 **같은 `Spec` 을 서로 다르게 소비한다.** 같은 yml을 주고도 전송을 바꾸면 유효 옵션이 달라진다.

| 전송 | connectTimeout | readTimeout | poolName/Size |
|---|---|---|---|
| RestTemplate | 반영 | 반영 | 반영 |
| WebClient | 반영 | 반영 | **무시** |
| OkHttp3 | 반영 | **3000ms 고정** | **무시** |
| Feign | — | — | **통합 없음** |

원인은 구현 버그가 아니라 **계약 결함**이다. 풀 인터페이스의 시그니처부터 갈라져 있다.

```java
// RestTemplatePool.java:37,42
public RestTemplate get(Spec spec)
public RestTemplate get(int timeout, Spec.ConnectionPool connectionPool)   // 보정 미적용 경로
// WebClientPool.java
WebClient get(Spec spec);
// OkHttp3ClientPool.java
OkHttpClient get(String specId);        // ← 옵션 캐리어가 전송에 도달조차 못 한다
```

`DefaultOkHttp3ClientPool.java:47` 의 `readTimeout(3, SECONDS) // this value will be overridden on the fly` 주석은 사실이 아니다. 전 소스에서 per-call 오버라이드가 없고 어댑터는 `pool.get(specId).newCall(...)` 을 직접 호출한다(`DefaultOkHttp3ClientAdaptor.java:84,89,94`). 2.17.0에서도 이 파일은 동일하다.

보정식도 세 벌로 복제돼 있다. `RestTemplatePool` 은 `uplifting()` + 생성 시 `+ROUND_TRIP_TIME`, `DefaultWebClientPool.java:42` 는 인라인 계산 + **자체 `ROUND_TRIP_TIME` 상수 별도 선언**, OkHttp3는 없음. 현재 결과값은 우연히 일치하지만 캐시 키는 이미 갈라져 있다 — WebClient는 키에 풀 이름이 없어 timeout이 같은 두 provider가 클라이언트를 공유한다.

## 목표

**하나의 외부 설정(`pylon.client.*`)이 여러 전송 구현체에서 동일한 유효 옵션을 만든다.**

측정 기준: 전송을 파라미터로 도는 적합성 테스트에서, 동일 yml에 대해 `(connectTimeoutMillis, readTimeoutMillis, poolName, poolSize)` 4종이 계약 적용 전송 전부에서 동일하다고 단언한다.

| 전송 | 위치 | 역할 |
|---|---|---|
| RestTemplate | pylon 기본 경로 | **기준선(대조군)** — 계약 미적용 |
| Apache HttpClient | 신규 | 기준 전송 — Spring 비의존 |
| WebClient | 실물 확장 미러 | 계약 적용 |
| OkHttp3 | 실물 확장 미러 | 계약 적용 |
| Feign | 신규 | 계약 적용 |

계약 계층은 **`java.*` 외의 타입을 참조하지 않는다.** Spring은 배선(`@Primary` 빈 등록)에만 등장한다. 이 성질을 코드 리뷰가 아니라 **빌드로 강제**하기 위해, 계약은 의존성이 하나도 없는 별도 모듈 `client-contract` 에 둔다 — Spring이 클래스패스에 존재하지 않으므로 위반은 컴파일 에러가 된다.

## 비목표

- **API-GW 프로토콜** — 인증 토큰, 요청 서명, 라우팅 정책 원격 갱신, rate limit, precondition.
  - 부수 확인 사항: 원격 정책 갱신이 주입된 timeout을 런타임에 덮어쓴다(`ApiProviderPolicyDeployer.java:68-71` + `SpecResolver.java:65-71` + 실물 `TimeoutCustomizer.java:46-48` 이 `isApplicableInRuntime()==false`). 원격 갱신이 비목표이므로 증명 대상은 아니며, README 각주로만 남긴다.
- **Fluent API** — 전송 구현체가 아니라 호출 스타일이다. `GenericApiClient.java:107,166-168` 이 `specResolver` → `restTemplatePool.get(spec)` 를 그대로 쓰므로 RestTemplate 기준선에 이미 포함된다.
- **RestTemplate 경로의 통일** — 아래 설계 결정 참조. 대조군으로만 유지한다.
- **생성 어댑터를 통한 전송 교체** — 신규 전송은 `client-config` 가 specId로 직접 호출한다. 실물도 전송별로 어댑터를 따로 생성하므로(`ExtensionConfigurationSelector.java:30-37`) 어댑터 하나로 전송을 갈아끼우는 구조는 애초에 존재하지 않는다.
- 실행 가능한 데모 앱. 검증은 통합테스트로만.
- 비동기/리액티브 시맨틱 자체의 검증. WebClient는 **옵션 전달 경로**만 대상이며 Mono 조립·백프레셔는 다루지 않는다.

## 현황 분석

POC 측 단일 입구는 이미 존재한다. 끊기는 지점만 정확히 특정된다.

| 계층 | 위치 | 상태 |
|---|---|---|
| 외부 설정 | `PylonClientProperty.kt` / `application-*.yml` | 단일 |
| 옵션 객체 | `PylonConfiguration.java:16-51` (`@Primary` 로 대체) | 단일 |
| 정규화 | `SpecResolver.java:45-61` + 커스터마이저 체인 | 단일 |
| 정규화 산출물 | `Spec.java:6-13` `{id, provider, path, method, timeout, connectionPool, hostOverride}` | 단일 |
| **전송 소비** | `RestTemplatePool.java:42-53` 만 존재 | **여기서 갈라진다** |

`RestTemplatePool.java:42-53` 이 유일한 소비자이고, 보정과 캐시 키를 자기 안에 갖고 있다.

```java
public int readTimeoutOf(Spec spec) { return uplifting(spec.getTimeout()) + ROUND_TRIP_TIME; }
String key = pool.getName() + "-" + readTimeout;
```

풀 실현은 `HttpClientConnectionManagerFactory.java:18-28` 이 `poolName → PoolingHttpClientConnectionManager(maxTotal=defaultMaxPerRoute=poolSize)` 로 담당한다. **Apache HC 전용 개념이라 다른 전송으로 이식되지 않는다** — WebClient는 `ConnectionProvider`, OkHttp3는 `ConnectionPool`+`Dispatcher` 가 대응 좌석이다.

결정적으로, 라이브러리가 전송 풀의 교체를 **명시적으로 허용**한다.

```java
// DefaultWebClientPool.java:24, DefaultOkHttp3ClientPool.java:19 (실물 javadoc)
// User can override by providing new one and annotate {@link Primary}
```

그리고 등록 지점에 `@ConditionalOnMissingBean` 이 없다(`PylonWebClientConfiguration.java:26-29`, `PylonOkHttp3ClientConfiguration.java:19-22`). **POC가 이미 증명한 함정 1이 전송 확장 빈에서 그대로 반복된다.**

반면 `RestTemplatePool` 은 인터페이스가 아닌 `@Component` 구체 클래스이고, `DynamicApiClient.java:43` 이 그 타입을 직접 주입받는다. `@Primary` 로 이기려면 같은 타입이어야 하므로 **상속 외에 수단이 없다.**

## 설계 결정

### 선택한 방향 — 라이브러리를 고치지 않고 계약을 씌운다

POC의 전제인 **"모듈 0·1 수정 금지"를 유지한다.** 계약은 `client-config`(내 코드)가 소유하고, `@Primary` 로 전송 풀을 대체한다.

**1) 파생 규칙의 유일한 장소 — Spring 비의존**

```java
// client-contract 모듈 소유. 의존성 0 — java.* 만 참조 가능하다.
public final class ClientOptions {          // 불변 값
    private final int connectTimeoutMillis;  // PylonConfiguration
    private final int readTimeoutMillis;     // ceil(t/100)*100 + ROUND_TRIP_TIME — 이 식은 여기에만 존재
    private final String poolName;           // Spec.ConnectionPool
    private final int poolSize;
    public String cacheKey();                // poolName + "-" + readTimeoutMillis — 전 전송 공통
}

public interface ClientPool<C> {             // 전송 중립 계약
    C get(ClientOptions options);
}
```

`Spec` 은 `org.springframework.http.HttpMethod` 를 필드로 갖지만(`Spec.java:3,10`), `ClientOptions` 파생에는 `timeout` 과 `connectionPool` 만 쓰이므로 **계약 타입에 Spring이 새어 들어오지 않는다.**

`Spec → ClientOptions` 변환은 pylon 타입과 Spring을 모두 아는 `ClientOptionsFactory` 가 담당하며, 이것은 `client-contract` 가 아니라 `client-config` 소유다. 즉 **의존 방향은 `client-config → client-contract` 단방향이고 역방향은 존재하지 않는다.** `client-contract` 는 pylon도 Spring도 모른다.

**2) 전송별 좌석 매핑**

| 전송 | 대체 방법 | connect/read timeout 좌석 | 풀 좌석 |
|---|---|---|---|
| **Apache HttpClient** (기준) | 신규 `ClientPool<CloseableHttpClient>` | `RequestConfig.setConnectTimeout` / `setSocketTimeout` | `PoolingHttpClientConnectionManager` |
| WebClient | `WebClientPool` 구현 + `@Primary` | `CONNECT_TIMEOUT_MILLIS` / `ReadTimeoutHandler` | `ConnectionProvider.builder(poolName).maxConnections(poolSize)` |
| OkHttp3 | `OkHttp3ClientPool` 구현 + `@Primary` | `newBuilder().connectTimeout/readTimeout` — **옵션별 인스턴스** | `ConnectionPool` + `Dispatcher.setMaxRequestsPerHost` |
| Feign | 신규 (`pylon` 에 통합 없음) | `Request.Options(connect, read)` | `Client` 구현체에 커넥션 매니저 주입 |
| RestTemplate | **대체하지 않음** | 라이브러리 기본값 | 라이브러리 기본값 |

**`OkHttp3ClientPool.get(String specId)` 의 계약 결함은 클라이언트 구현이 `SpecResolver` 를 주입받아 `specId → Spec → ClientOptions` 로 되찾는 방식으로 복구한다.** 라이브러리 시그니처를 바꾸지 않고 옵션 캐리어를 재연결하는 것이 이 설계의 핵심이다.

**3) pylon-lite에 추가할 것 — 실물 미러링 자격으로만**

`WebClientPool`/`DefaultWebClientPool`, `OkHttp3ClientPool`/`DefaultOkHttp3ClientPool` 을 **결함까지 포함해** 실물대로 추가한다. 결함을 고쳐서 넣으면 POC가 증명할 대상이 사라진다. Apache HC 전송과 Feign은 실물에 대응물이 없으므로 pylon-lite에 넣지 않는다.

전송 의존성이 코어를 오염시키지 않도록 모듈을 쪼갠다.

```
pylon-lite/              (변경 없음 — 코어 + RestTemplate 대조군)
pylon-lite-webclient/    [신규] 실물 WebClient 확장 미러 (결함 포함)
pylon-lite-okhttp3/      [신규] 실물 OkHttp3 확장 미러 (결함 포함)
client-contract/         [신규] ClientOptions + ClientPool<C> — 의존성 0
client-config/           ClientOptionsFactory + 전송 4종 구현 + 적합성 테스트
```

부수 효과로 **계약이 전송에도 프레임워크에도 의존하지 않는다는 사실이 모듈 그래프로 증명된다.** `client-contract` 의 빌드 스크립트에 의존성이 한 줄도 없다는 것 자체가 그 단언이다.

**4) 검증 — 적합성 매트릭스**

전송을 파라미터로 도는 `TransportConformanceTest` 가 POC의 새 결론이다.

- 계약 도입 **전에** 실패를 고정한다 — WebClient는 풀에서, OkHttp3는 timeout·풀 양쪽에서 실패.
- 단언은 두 층이다. (a) 생성된 클라이언트 인스턴스에서 설정값을 직접 회수해 비교, (b) `StubApiServer` 지연 응답으로 소켓 도달을 전송별 1건씩 확인.
- RestTemplate은 **기대 불일치 행**으로 남긴다. "계약을 쓴 전송 vs 안 쓴 전송"의 차이가 매트릭스 안에서 드러나는 것이 이 테스트의 값어치다.
- 기존 `ReadTimeoutReachesSocketTest` 의 보정식 단언은 이 매트릭스에 흡수한다.

### 대안 검토

**(A) pylon-lite에 `ClientPool<C>` 계약을 직접 도입** — 채택하지 않음. 실물에 없는 인터페이스를 코어에 넣으면 README:169의 "실물과 1:1" 성질이 깨지고, POC가 증명하는 명제가 "우리가 설계한 라이브러리는 잘 동작한다"로 약해진다. 이 프로젝트의 값어치는 **못 고치는 라이브러리 위에서도 단일화가 되는가**에 있다.

**(B) `RestTemplatePool` 상속 + `@Primary`** — 채택하지 않음. 전송 5종을 모두 통일할 수 있는 유일한 방법이지만, 라이브러리가 의도한 확장점이 아니라 상위 버전에서 깨질 수 있고 계약을 Spring에 강결합시킨다. 계약을 순수 Java로 유지하는 편이 목표에 부합한다. 대신 RestTemplate을 대조군으로 남겨 차이를 드러낸다.

**(C) JDK `java.net.http.HttpClient` 를 기준 전송으로** — 채택하지 않음. Java 11+ 가 필요해 기준선을 올려야 하고, 그 순간 "실물 pylon(Java 8 / Spring 5.2)과 같은 플랫폼"이라는 성질(design.md:36-38)이 깨진다. Apache HttpClient는 Spring 비의존이면서 Java 8을 유지하고 이미 의존성에 있다.

**(D) 전송별 설정 네임스페이스 분리**(`pylon.client.webclient.*` 등) — 채택하지 않음. 목표와 정면으로 어긋난다.

**(E) Resilience4j `TimeLimiter`/`Bulkhead` 로 덮기** — 보완재로만. 소켓 timeout을 통일하는 게 아니라 상위에서 상한을 거는 것이라 "동일 유효 옵션" 단언이 성립하지 않는다. README 대안 절에 기록만 한다.

## 영향 범위

| 대상 | 변경 |
|---|---|
| `settings.gradle.kts` | 모듈 3개 추가 |
| `pylon-lite/` | **변경 없음** |
| `api-gateway-consumer-role-poc/` | **변경 없음** |
| `pylon-lite-webclient/` | 신규 — 실물 미러 2파일 + 빌드 스크립트 |
| `pylon-lite-okhttp3/` | 신규 — 실물 미러 2파일 + 빌드 스크립트 |
| `client-contract/` | 신규 — `ClientOptions`, `ClientPool<C>`. **빌드 스크립트에 의존성 블록이 없다** |
| `client-config/` | `ClientOptionsFactory`, 전송 4종 구현, 적합성 테스트. `client-contract` 에 의존 |
| `docs/design.md` | non-goal 줄 분리(Fluent API 유지 / 전송 승격), 모듈 구성·검증 전략 갱신 |
| `README.md` | 함정 4·5 추가, 매트릭스 표, 표준 훅이 왜 전부 막혔는지, 대안 계층 |
| 신규 의존성 | `reactor-netty`(캐시 없음), `okhttp3` 3.14.x(Boot 2.3.4 BOM 관리), `feign-core`(BOM 밖, 버전 명시). **최초 1회 mavenCentral 다운로드 허용** — 승인됨 |
| CI | 모듈 증가에 따른 빌드 시간. 워크플로 자체 변경은 없음 |

## 열린 질문

1. ~~신규 의존성의 최초 1회 네트워크 다운로드 허용 여부~~ → **허용으로 확정.** design.md:29의 mavenCentral 제약은 그대로 두되, README:154의 "네트워크 없이 빌드" 문구는 "의존성 최초 해결 후 오프라인 빌드 가능"으로 조정한다.
2. ~~순수 java http client의 해석~~ → **Apache HttpClient 직접 사용으로 확정.** Java 8 유지.
3. ~~RestTemplate 경로 처리~~ → **통일 대상에서 제외, 대조군으로 유지.**
4. **Feign 의존성 형태.** `feign-core` 단독 vs `spring-cloud-openfeign`. 권고는 `feign-core` 단독 — POC는 Spring Cloud 스택이 아니고, 단일 계약 증명에는 core로 충분하며 의존성이 훨씬 가볍다. → 결정 시점: 구현 착수 전.
5. **OkHttp3 우선순위.** 실물 `DefaultOkHttp3ClientPool` 은 `@Deprecated` 다. 반례로서의 가치는 가장 크지만(유일하게 timeout까지 새는 경로) 계약 이식 대상으로는 Feign보다 뒤로 미룰 근거가 된다. → 결정 시점: PR 분할 시.
6. **매트릭스에서 RestTemplate 행의 단언 방식.** 기대 불일치를 명시적으로 단언할지(회귀 감지 가능, 라이브러리 변경 시 깨짐), 기록만 할지. → 결정 시점: 적합성 테스트 작성 시.
