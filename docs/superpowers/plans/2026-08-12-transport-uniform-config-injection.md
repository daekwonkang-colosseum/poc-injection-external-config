# 전송 구현체 공통 옵션 주입 계약 구현 계획

날짜: 2026-08-12
관련 spec: `docs/superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md`

## 전제 조건

- spec APPROVED (완료)
- 신규 의존성 최초 1회 mavenCentral 해석 — `reactor-netty`, `okhttp3` 3.14.x, `feign-core`. 네트워크 필요 (승인됨)
- 열린 질문 4(Feign 의존성 형태) 확정 — 권고: `feign-core` 단독

### 착수 전 확인할 빌드 사실

루트 `build.gradle.kts` 의 `subprojects {}` 블록이 **모든 모듈**에 `api(platform(spring-boot-dependencies))` 와 `testImplementation(spring-boot-starter-test)` 를 주입한다. `client-contract` 도 예외가 아니다.

- `platform(...)` 은 BOM(버전 관리)일 뿐 클래스를 컴파일 클래스패스에 올리지 않는다 → **main 소스에서 Spring 타입 참조는 여전히 컴파일 에러**가 된다. 강제 성립.
- `spring-boot-starter-test` 는 test 클래스패스에만 올라간다 → main 소스 강제에는 영향 없다.

즉 루트 블록을 수정하지 않아도 목표는 달성된다. `client-contract/build.gradle.kts` 에 `dependencies` 블록을 **아예 쓰지 않는 것**이 이 모듈의 설계 단언이다.

## 구현 단계

| 단계 | 작업 | 검증 방법 | PR |
|---|---|---|---|
| 1 | `client-contract` 모듈 신설(의존성 블록 없음). `ClientOptions`(불변, `cacheKey()`), `ClientPool<C>` 정의. 보정식 `ceil(t/100)*100 + ROUND_TRIP_TIME` 은 여기 한 곳에만 존재 | `--tests '*ClientOptions*'` — 보정식 단언, 같은 (풀,timeout)이 같은 `cacheKey()` 를 내는지. `client-contract/build.gradle.kts` 에 `dependencies` 블록이 없음을 리뷰로 확인 | 2 |
| 2 | `client-config` 에 `ClientOptionsFactory`(`Spec` + `PylonConfiguration` → `ClientOptions`) 추가. 의존 방향 `client-config → client-contract` 단방향 | 기존 24개 테스트 무회귀. `ClientOptionsFactory` 가 jar 기본값·provider 일괄·per-spec 우선순위를 그대로 반영하는지 단언 | 2 |
| 3 | Apache HC 전송 `ApacheHttpClientPool implements ClientPool<CloseableHttpClient>` + `TransportConformanceTest` 골격(전송 1종) | 생성 인스턴스에서 `RequestConfig.getSocketTimeout/getConnectTimeout`, `PoolingHttpClientConnectionManager.getMaxTotal` 회수 단언 + `StubApiServer` 지연으로 소켓 도달 1건 | 2 |
| 4 | `pylon-lite-webclient` 모듈 신설. 실물 `WebClientPool`/`DefaultWebClientPool` 을 **결함 포함** 미러링 | 결함 재현 테스트 — 풀 이름이 다르고 timeout이 같은 두 `Spec` 이 **동일 인스턴스**를 받는지 단언(캐시 키에 풀 이름 없음) | 3 |
| 5 | WebClient 계약 구현 `@Primary` + `ConnectionProvider.builder(poolName).maxConnections(poolSize)` 배선. 매트릭스에 행 추가 | 4의 동일성 단언이 **분리**로 뒤집힘 + `poolSize=1` 로 동시 요청 2건이 직렬화되는지 `StubApiServer` 로 1건 | 3 |
| 6 | `pylon-lite-okhttp3` 모듈 신설. 실물 `OkHttp3ClientPool`/`DefaultOkHttp3ClientPool` 을 **결함 포함** 미러링 | `client.readTimeoutMillis() == 3000` 이 yml·jar 설정과 무관하게 성립함을 단언 (결함 고정) | 4 |
| 7 | OkHttp3 계약 구현 `@Primary` — `SpecResolver` 로 `specId → Spec` 복구, `newBuilder()` 옵션별 인스턴스, `Dispatcher.setMaxRequestsPerHost`. 매트릭스에 행 추가 | `readTimeoutMillis()` == 기대 보정값, `dispatcher().maxRequestsPerHost()` == `poolSize`, 6의 단언이 뒤집힘 | 4 |
| 8 | Feign 전송 — `Spec → Request.Options` 매핑 + `Client` 에 커넥션 매니저 주입. 매트릭스에 행 추가 | `Request.Options.readTimeoutMillis/connectTimeoutMillis` 회수 단언 + `StubApiServer` 소켓 도달 1건 | 5 |
| 9 | RestTemplate 대조군 행 추가. `docs/design.md` non-goal·모듈·검증 절 갱신, `README.md` 함정 4·5 + 매트릭스 + 표준 훅 차단 표 + 대안 계층 | `./gradlew build` 전체 통과, `git diff --stat pylon-lite api-gateway-consumer-role-poc` 가 **빈 출력** | 5 |

## 완료 기준

- [ ] `TransportConformanceTest` 가 Apache HC·WebClient·OkHttp3·Feign 4종에서 `(connectTimeout, readTimeout, poolName, poolSize)` 동일함을 단언하고 통과
- [ ] RestTemplate 대조군 행이 매트릭스에 존재하고, 계약 미적용으로 인한 차이가 문서에 기록됨
- [ ] `pylon-lite`, `api-gateway-consumer-role-poc` 무변경 — `git diff` 빈 출력으로 확인
- [ ] 미러 모듈 2종이 실물 결함을 보존하고, 각 결함이 계약 적용 전/후 뒤집히는 테스트로 고정됨
- [ ] `client-contract/build.gradle.kts` 에 `dependencies` 블록이 없다 — 계약의 순수성이 빌드로 강제됨
- [ ] `./gradlew build` 전체 통과
- [ ] `docs/design.md` non-goal 재편(Fluent API 유지 / 전송 승격), `README.md` 갱신 완료

## 위험 요소

| 위험 | 대응 |
|---|---|
| **Java 8 + Gradle 6.5 + reactor-netty 조합 미검증.** Boot 2.3.4 BOM은 reactor-netty 0.9.x를 관리하나 실제 해석·컴파일 확인 안 됨 | 단계 4 착수 즉시 빈 모듈로 의존성 해석만 먼저 시도. 실패 시 WebClient를 후순위로 미루고 OkHttp3·Feign 먼저 진행 |
| **okhttp3 버전 충돌.** 로컬 캐시에 5.3.2만 있고 API가 다름(`RequestBody.create(MediaType, String)` 제거) | Boot 2.3.4 BOM이 관리하는 3.14.x를 강제. 버전을 명시하지 않고 BOM에 위임 |
| **feign-core는 BOM 밖** — Java 8 호환 버전 확인 필요 | 착수 전 `feign-core` 의 Java 8 지원 라인을 확인해 버전 고정. 실패 시 단계 8만 분리 보류 |
| **WebClient 동시성 테스트 플레이키** | 타이밍 값 단언 금지. `StubApiServer` 지연을 충분히 크게 잡고 **요청 도착 순서**만 단언 |
| **미러가 실물과 어긋남** | 미러 각 파일 상단에 실물 경로 + `2.14.9.RELEASE` 주석. 결함은 주석으로 "의도된 재현"임을 명시 |
| **모듈 증가로 총 파일 수 급증** (design.md:390이 이미 위험으로 지목) | 미러 모듈은 파일 2개씩, `client-contract` 는 2개로 제한. 계약 구현은 전송당 1클래스 원칙 |

## PR 전략

squash 머지 환경을 가정해 **스택 PR을 쓰지 않는다.** 각 PR의 base는 `main` 이며, 앞 PR이 머지된 뒤 `main` 에서 새로 분기한다.

| PR | 범위 | 단계 | 기존 테스트 |
|---|---|---|---|
| 1 | spec + plan 문서 | — | 무영향 |
| 2 | `client-contract` + `ClientOptionsFactory` + Apache HC 전송 + 매트릭스 골격 | 1-3 | 무회귀 |
| 3 | WebClient 미러 + 계약 구현 | 4-5 | 무회귀 |
| 4 | OkHttp3 미러 + 계약 구현 | 6-7 | 무회귀 |
| 5 | Feign + 문서 갱신 | 8-9 | 무회귀 |

PR 2~5는 각각 단독으로 빌드·테스트가 통과해야 한다. 매트릭스 테스트는 PR마다 전송 행이 하나씩 늘어나는 형태로 증분한다.
