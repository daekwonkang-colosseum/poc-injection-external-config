# api-pylon 외부 설정 주입 적용 가이드

대상: 사내 api-pylon 코드 생성기가 만든 어댑터를 쓰는 서비스 팀.

이 저장소는 **POC 다.** 실물 `api-pylon-tools` 를 미러링해 함정을 재현하고 대응을 테스트로 못박은 것이지, 실물을 테스트한 것이 아니다. 미러는 `api-pylon-tools:2.14.9.RELEASE` 의 소스를 읽고 만들었다. **당신의 앱에 적용하려면 1절의 점검을 직접 실행해 전제가 맞는지 확인해야 한다.** 값을 그대로 베끼지 말고 확인하는 명령을 써라.

---

## 0. 이 가이드가 필요 없는 경우

먼저 자기 상황을 판별하라. **대부분의 앱은 1~3만 필요하고 6절 전송 계약은 필요 없다.**

```
스펙 하나의 timeout 만 바꾸고 싶다
   └─▶ 함정 1·2·3 만 읽어라. 2절 단계 1~2 로 끝난다.
       client-contract 도, 전송 계약도 필요 없다.

provider 단위로 timeout·커넥션풀을 조정하고 싶다
   └─▶ + 1절의 "provider 별 timeout 편차 감사" 를 반드시 실행하라.
       편차가 큰 provider 에 일괄 설정하면 긴 스펙이 조용히 잘린다.

로컬·테스트에서 특정 provider 를 스텁으로 돌리고 싶다
   └─▶ 2절 단계 4 (api_gateway.manual_override.*). 코드 0줄.

WebClient 나 OkHttp3 어댑터를 쓴다
   └─▶ 1절의 "전송 확장 지원 여부" 를 먼저 확인하라.
       false 면 그 전송의 함정은 아예 발동하지 않는다. 대다수 앱은 RestTemplate 만 쓴다.

원격 라우팅 정책 갱신이 켜져 있다
   └─▶ 2절 단계 5. 이걸 빼면 주입한 timeout 이 갱신 주기마다 되돌아간다.
```

**RestTemplate 만 쓰는 앱이 대다수다.** 그 경우 함정 4·5 는 무관하고, 전송 계약(`client-contract`)은 도입할 이유가 없다. 함정 4·5 는 "WebClient/OkHttp3 확장을 켠 앱에서만" 발동한다.

---

## 1. 적용 전 점검 (30분)

다섯 가지를 자기 앱에서 직접 확인한다. 이 절이 이 가이드의 핵심이다 — 아래 명령들은 **당신의 생성 jar** 를 열어본다.

먼저 생성 jar 경로를 잡는다.

```bash
JAR=$(find ~/.gradle/caches -name 'api-gateway-consumer-role-*.jar' ! -name '*sources*' | head -1)
echo "$JAR"
```

Gradle 캐시에 없으면 앱의 의존성 좌표를 먼저 확인한다.

```bash
./gradlew :<your-module>:dependencies --configuration runtimeClasspath | grep -i "api-pylon-tools\|consumer-role"
```

### 1-1. pylon 버전 확인

```bash
./gradlew :<your-module>:dependencies --configuration runtimeClasspath | grep "api-pylon-tools"
```

이 POC 는 **2.14.9.RELEASE** 기준이다. 2.17.0 과 대조한 결과는 다음과 같다.

| 항목 | 2.14.9 → 2.17.0 |
|---|---|
| 함정 1·2·3·4·5·6 | **전부 유지된다** (확인함) |
| `RestTemplatePool.get(Spec)` | `get(Spec, String dstVdc)` 로 **시그니처 변경** |
| `Spec.getTimeout()` | `getTimeout(String dstVdc)` 추가 |
| timeout 보정식 `ceil(t/100)*100 + 100` | 변경 없음 |
| `TimeoutCustomizer.isApplicableInRuntime()` | `false` 유지 → 함정 6 유지 |
| `DefaultOkHttp3ClientPool` | 바이트 단위로 동일 |

**2.17.0 이상이면** VDC 인자가 추가된 시그니처를 쓰게 되므로, 이 저장소의 `client-config/src/main/kotlin/poc/client/config/transport/` 구현을 그대로 베끼면 컴파일되지 않는다. `Spec` 을 받는 지점마다 `dstVdc` 를 어디서 얻을지(`ClientContext`) 결정해야 한다.

**2.14.9 미만이거나 그 사이 버전이면** 위 표를 신뢰하지 마라. 대조하지 않았다. 소스 jar 를 받아 `TimeoutCustomizer.isApplicableInRuntime()` 과 `ApiGatewayAdapterConfig.timeoutCustomizer()` 를 직접 읽어라.

### 1-2. 전송 확장 지원 여부 — 가장 먼저 확인할 것

**이건 앱마다 다르다.** 생성 jar 가 결정하며 클라이언트는 켤 수 없다. `false` 면 그 전송의 함정은 아예 발동하지 않는다.

소스 jar 가 있으면:

```bash
unzip -p "${JAR%.jar}-sources.jar" '*PylonCodeGeneratorVersion.java' | grep -A2 "supports.*Extension"
```

소스 jar 없이 바이너리만으로:

```bash
cd "$(mktemp -d)" && unzip -q "$JAR" '*PylonCodeGeneratorVersion.class' \
  && javap -c -p "$(find . -name '*PylonCodeGeneratorVersion.class')" \
     | grep -A2 "supportsWebClientExtension\|supportsOkHttp3ClientExtension" \
     | grep -E "supports|iconst"
```

읽는 법 — `iconst_0` 은 `false`, `iconst_1` 은 `true` 다.

```
public boolean supportsOkHttp3ClientExtension();
     0: iconst_0        ← false. OkHttp3 확장 로드 안 됨. 함정 5 무관.
public boolean supportsWebClientExtension();
     0: iconst_1        ← true.  WebClient 확장 로드됨. 함정 4 발동.
```

기동 중인 앱에서 확인하려면 `com.coupang.apigateway.pylon` 로그를 INFO 로 올리고 다음 줄을 찾는다. `ExtensionConfigurationSelector` 가 남긴다.

```
Trying to load WebClient extension configuration for Pylon.
Trying to load okhttp3 extension configuration for Pylon.
```

없으면 그 확장은 로드되지 않은 것이다.

### 1-3. provider 별 timeout 편차 감사 — 가장 위험한 항목

**편차가 큰 provider 에 provider 일괄 `read-timeout` 을 주면 긴 스펙이 조용히 잘린다.** 예외도 로그도 없다. 실측에서 한 자릿수 배가 아니라 **20배 이상** 벌어진 provider 가 여럿 존재하는 생성 jar 를 확인했다.

```bash
cd "$(mktemp -d)" && unzip -q "$JAR" '*_configuration.json' && python3 - <<'PY'
import json, glob
rows = []
for f in glob.glob('*_configuration.json'):
    d = json.load(open(f))
    ts = [s['timeout'] for s in (d.get('specifications') or []) if s.get('timeout')]
    if ts:
        rows.append((max(ts) / min(ts), d['name'], len(ts), min(ts), max(ts)))
for ratio, name, n, lo, hi in sorted(rows, reverse=True):
    flag = '  <== provider 일괄 설정 금지' if ratio >= 4 else ''
    print(f'{name:30s} specs={n:3d}  min={lo:6d}  max={hi:6d}  ratio={ratio:5.1f}{flag}')
PY
```

출력 형태 (수치는 예시다):

```
<provider-a>                   specs= NN  min=  3000  max= XXXXX  ratio= XX.X  <== provider 일괄 설정 금지
<provider-b>                   specs= NN  min=  3000  max=  6000  ratio=  2.0
<provider-c>                   specs=  1  min=  3000  max=  3000  ratio=  1.0
```

**해석과 대응**

- `ratio` 가 크면 그 provider 에 `read-timeout` 하나로 일괄 설정하면 안 된다. 짧은 값을 주면 긴 스펙이 죽고, 긴 값을 주면 짧은 스펙의 장애 감지가 늦어진다.
- 그래도 provider 단위 설정이 필요하면 **긴 스펙을 `read-timeout-per-spec` 으로 되살려라.** 2절 단계 2 참조.
- `ratio` 가 1에 가까우면 provider 일괄 설정이 안전하다.

특정 spec 의 timeout 을 알아야 하면:

```bash
unzip -p "$JAR" '*_configuration.json' | python3 -c "
import json,sys
for line in sys.stdin: pass" 2>/dev/null
# provider 파일을 하나씩 열어 id 로 찾는다
unzip -p "$JAR" '<provider-name>_of_<role>_configuration.json' | python3 -m json.tool | grep -B2 -A6 '<spec-id>'
```

### 1-4. 원격 정책 갱신 활성 여부

함정 6 해당 여부를 가른다. `UnifiedRoutingPolicyUpdater` 는 pylon 런타임의 `@Component` 이므로 `@EnableApiGatewayAdapters` 를 쓰면 **기본으로 동작한다.** 주기는 `PylonConfiguration.routingInfoDuration` (기본 60초)이다.

기동 로그에서 다음을 찾는다.

```
Success to resolve unified routing policy for <consumer>
API read timeout configuration for <spec-id> updated: <before> ms -> <after> ms
```

**두 번째 줄이 보이면 함정 6 이 실제로 발동하고 있다.** 주입한 값이 원격 값으로 덮이는 순간이 로그로 남는다. 이 줄에 당신이 yml 로 설정한 spec 이 등장하면 2절 단계 5 가 필수다.

첫 줄만 보이고 두 번째가 없다면 현재 원격 값과 주입 값이 우연히 같은 것일 수 있다. 설정을 바꾼 직후 다시 확인하라.

### 1-5. 보정식 인지

실효 read timeout 은 설정값이 아니다.

```
실효값 = ceil(설정값 / 100) * 100 + 100
```

- `1500` → `1600`
- `1000` → `1100`
- `1450` → `1600`
- `3000` → `3100`

**SLO 에서 역산한 값을 그대로 넣으면 100~199ms 만큼 어긋난다.** p99 가 1500ms 라 1500 을 넣으면 실제 소켓은 1600ms 를 기다린다.

근거: `README.md` 의 "함정 3 — timeout 보정" 절, `pylon-lite/src/main/java/poc/apigateway/pylon/RestTemplatePool.java`.

---

## 2. 단계별 적용

각 단계는 **독립적으로 적용·롤백 가능**하다. 순서는 의존성 순이지만, 필요 없는 단계는 건너뛴다.

| 단계 | 의존 | 필요한 앱 |
|---|---|---|
| 1. `@Primary PylonConfiguration` | 없음 | **전부** |
| 2. timeout 주입 | 단계 1 | timeout 을 바꾸는 앱 |
| 3. 커넥션 풀 분리 | 단계 1 | provider 격리가 필요한 앱 |
| 4. host 치환 | 없음 (독립) | 로컬·테스트 스텁이 필요한 앱 |
| 5. 함정 6 방어 | 단계 2 | 원격 갱신이 켜진 앱 (1-4 로 판별) |
| 6. 전송 계약 | 단계 1 | WebClient/OkHttp3 를 쓰는 앱 (1-2 로 판별) |

### 단계 1 — `@Primary PylonConfiguration` 도입

**목적.** 라이브러리가 `ApiGatewayAdapterConfig.defaultPylonConfiguration()` 으로 등록하는 기본 빈을 이긴다. 이 빈에는 `@ConditionalOnMissingBean` 이 없으므로 **같은 이름으로 정의하면 빈 정의 충돌이 나고, 이름이 다른 `@Primary` 빈으로만 이길 수 있다.** (함정 1)

**코드.** 참조 구현은 `client-config/src/main/kotlin/poc/client/config/PylonClientConfig.kt` 와 `PylonClientProperty.kt` 다. 패키지와 import 만 `com.coupang.apigateway.*` 로 바꾸면 된다.

```kotlin
@ConstructorBinding
@ConfigurationProperties("pylon.client")
data class PylonClientProperty(
    val connectTimeout: Int = 3_000,
    val maxConnection: Int? = null,
    val routingInfoDuration: Int = 60_000,
    val providers: Map<String, Provider> = emptyMap(),
) {
    data class Provider(
        /** 필수다. 이유는 단계 2 참조. */
        val readTimeout: Int,
        val maxConnection: Int? = null,
        val readTimeoutPerSpec: Map<String, Int> = emptyMap(),
        val scheme: String? = null,
        val port: Int? = null,
    ) {
        val schemeAndPortOverridden: Boolean get() = scheme != null && port != null
    }
}
```

```kotlin
@Configuration
@EnableConfigurationProperties(PylonClientProperty::class)
class PylonClientConfig {

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
        return builder.build()
    }

    /** provider 명·specId 오타를 기동 실패로 만든다. */
    private fun verifyTargetsExist(
        property: PylonClientProperty,
        buildConfigurations: BuildConfigurations,
    ) {
        val specIdsByProvider = buildConfigurations.providers
            .associate { dto -> dto.name to dto.specifications.orEmpty().map { it.id }.toSet() }

        property.providers.forEach { (name, provider) ->
            val specIds = specIdsByProvider[name]
                ?: throw IllegalStateException(
                    "생성된 jar 에 없는 provider: '$name'. 사용 가능: ${specIdsByProvider.keys.sorted()}"
                )
            val unknown = provider.readTimeoutPerSpec.keys - specIds
            if (unknown.isNotEmpty()) {
                throw IllegalStateException("provider '$name' 에 없는 specId: ${unknown.sorted()}")
            }
        }
    }
}
```

**주의 — `buildConfigurations.providers` 접근 경로가 실물과 다르다.** 실물은 `buildConfigurations.gradlePluginGeneratingDtoLoader.providers` 다. `README.md` 의 "실제 pylon으로 갈아끼울 때" 표를 보라.

**검증.** 존재하지 않는 provider 명을 일부러 넣고 기동한다. 컨텍스트가 뜨면 안 된다.

```yaml
pylon:
  client:
    providers:
      "[definitely-not-a-provider]":
        read-timeout: 1000
```

`IllegalStateException` 과 함께 사용 가능한 provider 목록이 로그에 찍혀야 한다. 이 검증을 건너뛰지 마라 — **오타는 조용한 무동작으로 끝나고, 그게 가장 비싼 실패 모드다.**

**롤백.** `@Primary` 빈을 제거하면 라이브러리 기본 빈이 다시 주입된다. yml 은 남아 있어도 무해하다.

### 단계 2 — provider·spec 단위 timeout 주입

**목적.** 함정 2 회피. 라이브러리 조립부가 이렇게 생겼다.

```java
// ApiGatewayAdapterConfig.timeoutCustomizer()
if (provider.getDefaultTimeout() != null) {
    customizer.registerByProvider(provider.getName(), provider.getDefaultTimeout());
    for (Map.Entry<String, Integer> e : provider.getReadTimeoutPerSpec().entrySet()) {
        customizer.registerBySpec(e.getKey(), e.getValue());   // ← 바깥 if 안에 갇혀 있다
    }
}
```

**provider 기본 timeout 없이 per-spec 만 주면 조용히 무시된다.** 위 `PylonClientProperty.Provider.readTimeout` 을 **필수 파라미터**로 둔 이유가 이것이다 — 런타임 무동작을 기동 시점 실패로 끌어올린다.

**코드.**

```yaml
pylon:
  client:
    connect-timeout: 2000
    providers:
      "[<provider-name>]":              # provider 명에 '_' 가 있으면 대괄호 표기 필수
        read-timeout: 3000
        read-timeout-per-spec:
          "[<spec-id>]": 1500
```

Spring Boot 는 소문자 영숫자와 `-` 외의 문자가 map key 에 있으면 대괄호를 요구한다. **pylon provider 명에는 `_` 가 흔하므로 대부분 대괄호가 필요하다.**

**1-3 에서 편차가 크게 나온 provider 라면** `read-timeout` 을 짧게 주는 순간 긴 스펙이 잘린다. 긴 스펙을 `read-timeout-per-spec` 으로 되살려라.

```yaml
      "[<provider-with-wide-spread>]":
        read-timeout: 3000              # 대다수 스펙 기준
        read-timeout-per-spec:
          "[<long-running-spec-id>]": 60000   # 원래 값 복구
```

**검증.**

```kotlin
@SpringBootTest
class TimeoutInjectionTest @Autowired constructor(
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {
    @Test
    fun `injected timeout reaches the spec`() {
        assertThat(specResolver.get("<spec-id>").timeout).isEqualTo(1500)
    }

    @Test
    fun `the effective socket timeout includes the round trip allowance`() {
        val spec = specResolver.get("<spec-id>")
        assertThat(restTemplatePool.readTimeoutOf(spec)).isEqualTo(1600)   // ceil(1500/100)*100+100
    }
}
```

`readTimeoutOf` 는 2.14.9 기준이다. 버전이 다르면 1-1 표를 확인하라.

참조 테스트: `client-config/src/test/kotlin/poc/client/config/ProviderReadTimeoutTest.kt`, `PerSpecBeatsProviderTest.kt`.

**롤백.** yml 의 해당 provider 블록을 지우면 생성 jar 의 값으로 돌아간다.

### 단계 3 — 커넥션 풀 분리

**목적.** provider 별 전용 풀을 만들어 한 provider 의 지연이 다른 provider 호출을 굶기지 않게 한다. 설정이 없으면 모든 spec 이 공용 풀을 쓴다. 공용 풀 크기는 `pylon.client.max-connection`, 없으면 `min(provider 수 × 500, 4000)` 이다.

**코드.**

```yaml
pylon:
  client:
    max-connection: 2000               # 공용 풀
    providers:
      "[<provider-name>]":
        read-timeout: 3000
        max-connection: 50             # 이 provider 전용 풀
```

**검증.**

```kotlin
@Test
fun `the provider gets its own pool`() {
    val pool = specResolver.get("<spec-id>").connectionPool
    assertThat(pool.name).isEqualTo("<provider-name>")
    assertThat(pool.size).isEqualTo(50)
}
```

**롤백.** `max-connection` 만 지운다. 그 provider 는 공용 풀로 돌아간다.

### 단계 4 — host 치환 (로컬·테스트용)

**목적.** 생성 jar 의 `initial_configuration.json` 에 박힌 타겟 호스트를 통째로 갈아친다. **코드 0줄** — 라이브러리가 `Environment` 를 정규식으로 훑는다.

**코드.**

```yaml
api_gateway:
  manual_override:
    provider:
      <provider-name>:                 # 여기는 대괄호가 필요 없다. 프로퍼티 스캔 경로다.
        server: http://127.0.0.1:9001
```

**네임스페이스가 `pylon.client.*` 와 다른 것은 의도가 아니라 실물이 그렇다.** 이 키는 라이브러리가 정한 것이라 바꿀 수 없다.

치환 우선순위 (높은 쪽이 이긴다):

1. `api_gateway.manual_override.*` — scheme·host·port 통째 교체
2. `pylon.client.providers.<name>.{scheme,port}` — scheme·port 만, **host 는 jar 값 유지**
3. `initial_configuration.json` 의 provider 타겟 — jar 기본값

2번을 쓰려면 `read-timeout` 이 필수 파라미터라 함께 줘야 하고, 그 순간 단계 2 의 일괄 설정 위험이 발동한다.

**검증.** 스텁 서버를 띄우고 실제 요청이 거기 도달하는지 본다. 참조: `client-config/src/test/kotlin/poc/client/config/ManualOverrideHostTest.kt`.

**롤백.** yml 블록 삭제. production 프로파일에는 아예 넣지 마라.

### 단계 5 — 함정 6 방어 (원격 갱신이 켜진 앱만)

**목적.** 1-4 에서 `API read timeout configuration ... updated` 로그를 봤다면 필수다. **주입한 timeout 이 갱신 주기마다 원격 값으로 되돌아간다.**

원인은 `SpecResolver.update` 가 `isApplicableInRuntime() == true` 인 커스터마이저만 태우는데 `TimeoutCustomizer` 만 `false` 이기 때문이다. 커넥션 풀과 host 치환은 살아남고 timeout 만 사라진다.

**코드.** 커스터마이저를 **하나 더** 등록한다. `@Primary` 가 아니다.

```kotlin
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
        fun from(property: PylonClientProperty) = RuntimeTimeoutCustomizer(
            timeoutPerProvider = property.providers.mapValues { (_, p) -> p.readTimeout },
            timeoutPerSpec = property.providers.values
                .flatMap { it.readTimeoutPerSpec.entries }
                .associate { it.key to it.value },
        )
    }
}
```

```kotlin
// PylonClientConfig 안
@Bean   // @Primary 가 아니다. 추가 빈이다.
fun runtimeTimeoutCustomizer(property: PylonClientProperty) =
    RuntimeTimeoutCustomizer.from(property)
```

**왜 `@Primary` 가 아닌가.** `ApiGatewayAdapterConfig.specResolver` 는 `List<SpecCustomizer>` 로 **리스트 주입**을 받는다. 리스트는 중복 제거를 하지 않으므로 `@Primary` 가 무력하다. 반대로 빈을 하나 더 등록하면 그대로 체인에 합류한다.

**순서 걱정은 필요 없다.** 라이브러리 커스터마이저와 같은 출처(`PylonClientProperty`)에서 만들면 기동 시점에 두 커스터마이저가 같은 값을 낸다. 갱신 시점에는 클라이언트 것만 남는다.

**과잉 방어도 아니다.** 설정하지 않은 provider 는 손대지 않으므로 원격 값이 그대로 반영된다. 되돌리는 것은 당신이 명시한 값뿐이다.

**검증.**

```kotlin
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClobberDefenceTest @Autowired constructor(private val specResolver: SpecResolver) {

    @Test
    fun `a remote policy update cannot undo the injected timeout`() {
        assertThat(specResolver.get("<spec-id>").timeout).isEqualTo(1500)

        ApiProviderPolicyDeployer(specResolver).updateTimeout("<spec-id>", 60000)

        assertThat(specResolver.get("<spec-id>").timeout).isEqualTo(1500)
    }
}
```

실물에서는 `ApiProviderPolicyDeployer` 를 직접 부를 수 없다(`deploy(ApiProviderPolicies)` 시그니처이고 package-private 이다). 대신 `routingInfoDuration` 을 짧게 두고 기동 로그에서 `updated:` 줄이 사라지는지 확인하라.

**공유 컨텍스트를 갱신하므로 `@DirtiesContext` 를 붙여라.** 방어가 동작하면 결과 상태는 같지만, 공유 가변 상태를 건드리는 것 자체가 다른 테스트에 대한 위험이다.

참조: `client-config/src/main/kotlin/poc/client/config/RuntimeTimeoutCustomizer.kt`, `README.md` 의 "함정 6" 절.

**롤백.** `@Bean` 하나 제거. 즉시 원래 동작으로 돌아간다.

### 단계 6 — 전송 계약 (WebClient/OkHttp3 를 쓰는 앱만)

**1-2 에서 해당 확장이 `true` 로 나온 앱만 필요하다.** `false` 면 이 단계를 건너뛴다.

**목적.** 전송마다 `Spec` 을 다르게 소비하는 것을 하나의 계약으로 통일한다. 실물 상태는 이렇다.

| 전송 | connectTimeout | readTimeout | 커넥션 풀 |
|---|---|---|---|
| RestTemplate | 반영 | 반영 | 반영 |
| WebClient | 반영 | 반영 | **무시** |
| OkHttp3 | 반영 | **3000ms 고정** | **무시** |

**코드.** 참조 구현 전체가 `client-contract/src/main/java/poc/client/contract/` 와 `client-config/src/main/kotlin/poc/client/config/transport/` 에 있다. 핵심은 둘이다.

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

전송별 대체 방법:

- **WebClient** — `WebClientPool` 구현 + `@Primary`. 라이브러리 javadoc 이 명시적으로 허용한다. `ConnectionProvider.fixed(poolName, poolSize)` 로 풀을 붙인다 (reactor-netty 0.9.x. 1.0+ 는 `builder(name).maxConnections(n)`).
- **OkHttp3** — `OkHttp3ClientPool` 구현 + `@Primary`. 이 인터페이스는 `get(String specId)` 라 `Spec` 이 도달하지 않으므로, `SpecResolver` 를 주입받아 `specId → Spec → ClientOptions` 를 복원한다.

**RestTemplate 은 계약 대상이 아니다.** 네 전송 중 실물이 처음부터 옳게 하던 유일한 경로이고, `RestTemplatePool` 이 인터페이스가 아닌 구체 클래스라 `@Primary` 로 이기려면 상속뿐이다 (4절 참조).

**검증.** 같은 설정으로 전송을 바꿔가며 유효 옵션이 같은지 단언한다. 참조: `client-config/src/test/kotlin/poc/client/config/transport/TransportConformanceTest.kt`.

**롤백.** `@Primary` 전송 풀 빈을 제거하면 라이브러리 기본 풀로 돌아간다. 계약 모듈은 남아 있어도 무해하다.

---

## 3. 함정별 대응표

`README.md` 는 POC 관점(무엇을 재현했는가)이고, 이 표는 적용 관점(내가 뭘 해야 하는가)이다.

| 함정 | 내 앱에 해당하는가 | 증상 | 대응 | 검증 |
|---|---|---|---|---|
| **1** `@ConditionalOnMissingBean` 부재 | pylon 을 쓰면 **항상** | 같은 이름 빈 정의 시 기동 실패 | 이름이 다른 `@Primary` 빈 (단계 1) | 컨텍스트에 두 빈이 있고 주입되는 쪽이 내 것 |
| **2** per-spec 이 provider 기본값에 갇힘 | per-spec timeout 을 쓰면 | per-spec 값이 **조용히 무시** | `readTimeout` 필수 파라미터화 (단계 2) | provider 기본 없이 per-spec 만 주면 기동 실패 |
| **3** timeout 보정 | pylon 을 쓰면 **항상** | 설정 1500 → 실제 1600ms | 역산해서 설정 (1-5) | `RestTemplatePool.readTimeoutOf(spec)` |
| **4** WebClient 가 풀을 무시 | 1-2 가 `supportsWebClientExtension() == true` | `max-connection` 무동작, timeout 같은 provider 끼리 클라이언트 공유 | `WebClientPool` `@Primary` (단계 6) | 풀 이름이 다른 두 spec 이 다른 인스턴스 |
| **5** OkHttp3 timeout 3초 고정 | 1-2 가 `supportsOkHttp3ClientExtension() == true` | 모든 스펙이 3000ms. 긴 스펙이 잘린다 | `OkHttp3ClientPool` `@Primary` + `SpecResolver` 복원 (단계 6) | `client.readTimeoutMillis()` 가 기대 보정값 |
| **6** 원격 갱신이 timeout 을 되돌림 | 1-4 에서 `updated:` 로그가 보이면 | 기동 직후엔 맞다가 갱신 주기 뒤 원복 | `RuntimeTimeoutCustomizer` 추가 빈 (단계 5) | 갱신 후에도 주입값 유지 |

---

## 4. 하지 말아야 할 것

### 편차 큰 provider 에 provider 일괄 `read-timeout`

1-3 의 `ratio` 가 4 이상인 provider 에 `read-timeout` 하나만 주면 긴 스펙이 잘린다. **예외도 경고도 없다.** 긴 스펙을 `read-timeout-per-spec` 으로 되살려라.

### `RestTemplatePool` 상속

`@Primary` 로 RestTemplate 경로를 이기려면 상속뿐인데, 그러면 Spring 에 강결합되고 **라이브러리가 의도한 확장점이 아니라 상위 버전에서 깨진다.** 실제로 2.17.0 에서 `get(Spec)` 이 `get(Spec, String dstVdc)` 로 바뀌었다 — 상속했다면 그 시점에 컴파일이 깨진다.

RestTemplate 경로는 이미 옳게 동작한다. 손대지 마라.

### 커스터마이저 리스트 주입 지점에서 `@Primary` 기대

`ApiGatewayAdapterConfig.specResolver(List<SpecCustomizer>, ...)` 는 **리스트 주입**이다. `@Primary` 를 붙여도 라이브러리 커스터마이저가 리스트에서 빠지지 않는다. 둘 다 들어간다.

이 지점은 `@Primary` 전략의 전제(모든 소비처가 타입 단건 주입)가 깨지는 **유일한 곳**이다. 여기서는 "이기는" 게 아니라 "추가하는" 방식으로 접근하라 (단계 5).

### `RestTemplateCustomizer` / `BeanPostProcessor` / `useSystemProperties()`

"남의 빈을 밖에서 고친다"는 프레임워크 표준 훅 셋이 **전부 통하지 않는다.**

| 훅 | 성립 조건 | pylon 실제 |
|---|---|---|
| `RestTemplateCustomizer` / `WebClientCustomizer` | 대상이 `RestTemplateBuilder` 를 경유해 생성 | ✗ `RestTemplatePool` 이 `new RestTemplate(factory)` 로 직접 생성 |
| `BeanPostProcessor` | 대상이 스프링 빈 | ✗ 풀 내부 `ConcurrentHashMap` 에 lazy 생성 — 빈이 아니다 |
| Apache HC `useSystemProperties()` | 빌더가 그 메서드를 호출 | ✗ 메인 경로는 호출하지 않는다 |

마지막 항목엔 각주가 필요하다. Apache HttpClient 4.5 의 `useSystemProperties()` 는 `http.maxConnections`·`http.keepAlive` 는 읽지만 **소켓/read timeout 계열은 읽지 않는다** (SSL·프록시·연결 3종만). 호출했더라도 JVM 옵션으로 timeout 을 넣는 길은 애초에 없었다.

시간 낭비하지 말고 `@Primary` 로 가라. 근거: `README.md` 의 "왜 `@Primary` 인가 — 표준 훅이 전부 막혀 있다" 절.

---

## 5. 운영 확인

### 기동 시점

`com.coupang.apigateway.pylon` 로그를 INFO 로 올린다.

```yaml
logging:
  level:
    com.coupang.apigateway.pylon: INFO
```

확인할 줄:

| 로그 | 의미 |
|---|---|
| `Pylon common connection pool : <name>(<size>)` | 공용 풀 크기. 단계 3 검증 |
| `Trying to load WebClient extension configuration for Pylon.` | WebClient 확장 로드됨 (1-2) |
| `Success to resolve unified routing policy for <consumer>` | 원격 정책 갱신 동작 중 (1-4) |
| `API read timeout configuration for <id> updated: <a> ms -> <b> ms` | **함정 6 발동 중.** 단계 5 필요 |

단계 1 의 `PylonClientConfig` 에 적용된 옵션을 직접 로그로 남기는 것을 권한다. 참조 구현의 `logApplied()` 를 보라.

### 런타임 실효값 확인

설정이 실제로 걸렸는지는 `Spec` 과 `RestTemplatePool` 로 확인한다.

```kotlin
val spec = specResolver.get("<spec-id>")
log.info("spec={} timeout={} pool={}", spec.id, spec.timeout, spec.connectionPool)
log.info("effective socket read timeout = {}ms", restTemplatePool.readTimeoutOf(spec))
```

`spec.timeout` 은 설정값, `readTimeoutOf` 는 보정 후 실효값이다. **둘이 다른 것이 정상이다** (함정 3).

### "설정했는데 안 먹는다" 진단 순서

위에서부터 하나씩 배제한다.

1. **기동 로그에 `IllegalStateException` 이 없는가** — 있으면 provider 명·specId 오타다. 단계 1 의 `verifyTargetsExist` 가 잡는다.
2. **`specResolver.get(specId).timeout` 이 기대값인가** — 아니면 주입이 애초에 안 된 것이다. `@Primary` 빈이 실제로 주입됐는지 확인하라. 컨텍스트에 `PylonConfiguration` 빈이 둘 있고, 주입되는 쪽이 내 것이어야 한다.
3. **per-spec 만 주지 않았는가** — provider 기본 `read-timeout` 없이 `read-timeout-per-spec` 만 주면 조용히 무시된다 (함정 2).
4. **실효값과 설정값을 혼동하지 않았는가** — `readTimeoutOf` 는 `ceil(t/100)*100 + 100` 이다 (함정 3).
5. **기동 직후엔 맞다가 나중에 틀려지는가** — 원격 정책 갱신이다 (함정 6). `updated:` 로그를 찾아라. 단계 5 가 답이다.
6. **WebClient/OkHttp3 어댑터로 호출하고 있지 않은가** — 그 경로는 RestTemplate 과 다르게 동작한다 (함정 4·5). 1-2 로 확장 로드 여부를 확인하라.

**1~4 는 기동 시점, 5~6 은 런타임 문제다.** 증상이 "처음엔 맞았는데 나중에 틀려짐" 이면 5 부터 보라.

---

## 6. 알려진 한계

POC 가 해결하지 못한 것들이다. 적용 시 그대로 안고 간다.

### 기본값 중복

`PylonClientProperty` 의 `DEFAULT_CONNECT_TIMEOUT`·`DEFAULT_ROUTING_INFO_DURATION` 은 `PylonConfiguration` 내부의 `private static` 값을 **베껴 온 상수**다. `pylonConfiguration()` 이 조건 없이 항상 호출하므로 클라이언트 기본값이 무조건 이긴다.

**라이브러리가 자기 기본값을 바꾸면 클라이언트는 그 변경을 모른 채 옛 값에 고정된다.** 라이브러리 값이 `private` 이라 참조할 수 없어 구조적으로 해결 불가능하다.

대응: pylon 버전을 올릴 때 `PylonConfiguration` 의 기본값이 바뀌었는지 확인하고 상수를 맞춰라.

### connectTimeout 이 일부 전송에서 미검증

POC 의 적합성 매트릭스가 전 전송 공통으로 보는 것은 **readTimeout 과 캐시 키 규율뿐이다.**

| 전송 | readTimeout | poolSize | connectTimeout |
|---|---|---|---|
| Apache HC | 소켓 도달 | 커넥션 매니저 회수 | **미검증** |
| WebClient | 소켓 도달 | 동시성 행동 | **미검증** |
| OkHttp3 | 소켓 도달 | `Dispatcher` 회수 | 회수 |
| Feign | 소켓 도달 | 커넥션 매니저 회수 | `Request.Options` 회수 |

Apache HC 는 `CloseableHttpClient` 에서 `RequestConfig` 를 회수할 공개 API 가 없고, WebClient 는 `TcpClient` 옵션을 되읽을 수 없다. 행동 검증은 라우팅되지 않는 주소가 필요한데 환경에 따라 즉시 거부되거나 훨씬 오래 걸려 CI 에서 불안정하다.

**connectTimeout 이 `ClientOptions` 에서 각 전송의 좌석으로 전달되는 것은 코드상 명백하지만, 절반의 전송에서 테스트로 단언되지 않았다.** 이 구분을 인지하고 적용하라.

### OkHttp3 `Dispatcher` 는 비동기 호출에만 적용

`Dispatcher.maxRequestsPerHost` 는 `enqueue` 경로에만 걸린다. 동기 `execute` 는 호출 스레드에서 바로 실행되므로 동시성 상한이 적용되지 않는다. POC 는 `ConnectionPool` 에도 같은 크기를 넣지만, **동기 호출 위주라면 풀 크기가 기대만큼 동시성을 제한하지 않는다.**

### POC 는 미러를 테스트한다

가장 중요한 한계다. 이 저장소의 158개 테스트는 `api-pylon-tools:2.14.9.RELEASE` 의 **소스를 읽고 만든 미러**를 검증한다. 실물을 검증하지 않는다.

미러가 실물과 어긋나면 여기 적힌 대응이 당신 앱에서 다르게 동작할 수 있다. **1절의 점검을 실행해 전제를 직접 확인하라.** 특히 pylon 버전이 2.14.9 가 아니면 1-1 표부터 다시 세워야 한다.

---

## 더 읽을 것

- [`../README.md`](../README.md) — 현재 상태의 정본. 함정 6개의 재현 근거와 테스트 매핑
- [`design.md`](design.md) — 최초 설계 의도 (완료된 기록)
- [`superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md`](superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md) — 전송 계약의 설계 결정과 채택하지 않은 대안
