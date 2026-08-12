# poc-injection-external-config Implementation Plan

> ## ⚠️ 이 계획은 완료됐다. 실행하지 말 것.
>
> **작성 2026-08-11 · 완료 2026-08-11 · 이후 저장소가 더 진행됐다.**
>
> 아래 16개 태스크는 전부 구현·머지됐고, 그 뒤 **전송 구현체 공통 옵션 계약**과
> **원격 정책 갱신 방어**가 추가되면서 모듈 구성·테스트 수·제약이 모두 바뀌었다.
> 이 문서를 태스크 목록으로 실행하면 지금 저장소와 다른 것을 만들게 된다.
>
> | 항목 | 이 문서 (2026-08-11) | 현재 |
> |---|---|---|
> | 모듈 | 3개 | 6개 (`client-contract`, `pylon-lite-webclient`, `pylon-lite-okhttp3` 추가) |
> | 전송 | RestTemplate 하나 | 5개 (+ Apache HC·WebClient·OkHttp3·Feign) |
> | 재현한 함정 | 3개 | 6개 |
> | 테스트 | 93 | 158 |
> | `pylon-lite` 수정 | 절대 금지 | **실물 미러링 자격에 한해 허용** (함정 6 재현 시 적용) |
>
> **현재 상태를 알려면 [`../README.md`](../README.md) 를 읽어라.** 이 문서는 최초 구현이
> 어떤 순서와 근거로 진행됐는지의 기록으로만 남긴다.
>
> 이후 작업의 설계·계획은 다음에 있다.
> - [`superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md`](superpowers/specs/2026-08-12-transport-uniform-config-injection-design.md)
> - [`superpowers/plans/2026-08-12-transport-uniform-config-injection.md`](superpowers/plans/2026-08-12-transport-uniform-config-injection.md)

---

*아래는 2026-08-11 최초 구현 계획 원문이다. 이미 완료된 내용이며 갱신하지 않는다.*

> **For agentic workers:** ~~REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.~~ **완료된 계획이다. 실행 대상이 아니다.** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 제어할 수 없는 라이브러리 jar가 이미 환경 값(timeout·host)을 들고 있을 때, 클라이언트가 그 모듈을 수정하지 않고 외부 설정 주입만으로 값을 덮어쓰는 것을 테스트로 증명하는 독립 POC를 만든다.

**Architecture:** 3개 Gradle 모듈. `pylon-lite`(Java)는 "내가 못 건드리는 런타임" — `SpecResolver` + `SpecCustomizer` 체인, `PylonConfiguration`, `RestTemplatePool`, `DynamicApiClient`. `api-gateway-consumer-role-poc`(Java)는 "내가 못 건드리는 생성 jar" — 어댑터 2개와 timeout·host를 담은 JSON 리소스. `client-config`(Kotlin)는 "내 코드" — `@Primary PylonConfiguration` 빈과 프로파일별 yml, 그리고 통합테스트 8개.

**Tech Stack:** Gradle 6.5, Java 8, Kotlin 1.4.10, Spring Boot 2.3.4.RELEASE (BOM만), JUnit 5, JDK `com.sun.net.httpserver.HttpServer`

## Global Constraints

- 디렉토리는 `poc-injection-external-config/` 이며 **독립 Gradle 빌드**다. 루트 `settings.gradle.kts` 에 절대 포함하지 않는다.
- 저장소는 **`mavenCentral()` 만** 선언한다. 사내 저장소 의존 금지.
- Spring Boot **플러그인을 쓰지 않는다.** `platform("org.springframework.boot:spring-boot-dependencies:2.3.4.RELEASE")` BOM만 import한다. 실행 가능한 앱이 없다.
- Java 8 (`sourceCompatibility = JavaVersion.VERSION_1_8`), Kotlin `jvmTarget = "1.8"`.
- 패키지 루트는 `poc.` 다. `com.coupang.*` 를 쓰지 않는다.
- **`client-config` 는 `pylon-lite` 와 `api-gateway-consumer-role-poc` 의 소스를 수정하지 않는다.** 동작 변경은 오직 설정 주입으로만 한다. 이것이 POC의 전제다.
- 프로퍼티 네임스페이스 2개는 의도적이다: 타입 빈 경로는 `pylon.client.*`, 프로퍼티 스캔 경로는 `api_gateway.manual_override.*` (실제 pylon 키 미러링).
- 미러링 기준은 `com.coupang.apigateway:api-pylon-tools:2.14.9.RELEASE` 다.
- 커밋 메시지는 Conventional Commits (`feat:`, `test:`, `chore:`, `docs:`).

---

## File Structure

### 빌드 루트

| 파일 | 책임 |
|---|---|
| `poc-injection-external-config/settings.gradle.kts` | 3개 모듈 include |
| `poc-injection-external-config/build.gradle.kts` | 공통 플러그인·저장소·BOM·Java/Kotlin 타겟 |
| `poc-injection-external-config/gradle.properties` | Gradle 데몬 JVM 옵션 |
| `poc-injection-external-config/gradle/wrapper/*` | 현재 레포에서 복사 (Gradle 6.5) |
| `poc-injection-external-config/gradlew`, `gradlew.bat` | 현재 레포에서 복사 |
| `poc-injection-external-config/README.md` | 목적·모듈 소유권·재현한 함정·실행법 |

### `pylon-lite` (Java) — "못 건드리는 런타임"

| 파일 | 책임 |
|---|---|
| `src/main/java/poc/apigateway/pylon/PylonToolsMarker.java` | 스캔 기준점 |
| `src/main/java/poc/apigateway/services/ApiGatewayServiceMarker.java` | 스캔 기준점 (모듈 1이 같은 패키지 사용) |
| `src/main/java/poc/apigateway/configuration/ApiGatewayConfigurationMarker.java` | 스캔 기준점 (모듈 1이 같은 패키지 사용) |
| `.../pylon/ApiException.java` | 호출 실패 |
| `.../pylon/Pair.java` | name/value |
| `.../pylon/RequestBase.java` | path/query/header 파라미터 + body |
| `.../pylon/specs/model/Spec.java` | 스펙 값 객체 + 복사 빌더 |
| `.../pylon/specs/SpecResolver.java` | 커스터마이저 체인 + specId 레지스트리 |
| `.../pylon/specs/customizer/TimeoutCustomizer.java` | per-spec / per-provider timeout |
| `.../pylon/specs/customizer/ConnectionPoolCustomizer.java` | provider별 풀 |
| `.../pylon/specs/customizer/ManualOverrideCustomizer.java` | Spec에 hostOverride 부착 |
| `.../pylon/specs/customizer/HostOverride.java` | scheme/host/port |
| `.../pylon/configuration/generated/SpecConfigurationLocator.java` | 계약 |
| `.../pylon/configuration/generated/InitialConfigurationLocator.java` | 계약 |
| `.../pylon/configuration/generated/GenerationMetaLocator.java` | 계약 |
| `.../pylon/configuration/generated/PylonCodeGeneratorVersion.java` | 계약 |
| `.../pylon/configuration/dto/ApiSpecificationConfigurationDto.java` | JSON 매핑 |
| `.../pylon/configuration/dto/ProviderConfigurationDto.java` | JSON 매핑 |
| `.../pylon/configuration/dto/InitialConfigurationDto.java` | JSON 매핑 (라우팅 타겟) |
| `.../pylon/configuration/dto/GenerationMetaDto.java` | JSON 매핑 |
| `.../pylon/configuration/BuildConfigurations.java` | Locator로 JSON 3종 로드 |
| `.../pylon/configuration/PylonConfiguration.java` | **옵션 주입의 입구** + Builder |
| `.../pylon/configuration/SchemeAndPortOverrider.java` | scheme·port만 치환 |
| `.../pylon/configuration/ManualOverrideConfiguration.java` | Environment 정규식 스캔 |
| `.../pylon/configuration/ApiGatewayAdapterConfig.java` | 조립 중심. **함정 1·2 재현** |
| `.../pylon/configuration/EnablePocApiGatewayAdapters.java` | `@Import` 애노테이션 |
| `.../pylon/targets/TargetUriFinder.java` | 최종 URI 결정 (치환 우선순위) |
| `.../pylon/HttpClientConnectionManagerFactory.java` | poolName별 커넥션 매니저 |
| `.../pylon/RestTemplatePool.java` | RestTemplate 캐시. **함정 3 재현** |
| `.../pylon/DynamicApiClient.java` | specId로 호출 수행 |
| `src/testFixtures/java/poc/apigateway/pylon/testsupport/StubApiServer.java` | JDK HttpServer 스텁 (모듈 2도 사용) |

### `api-gateway-consumer-role-poc` (Java) — "못 건드리는 생성 jar"

| 파일 | 책임 |
|---|---|
| `.../configuration/ApiGatewayConsumerRolePocPylonCodeGeneratorVersion.java` | 버전 선언 |
| `.../configuration/ApiGatewayConsumerRolePocGenerationMetaLocator.java` | → `generation-meta.json` |
| `.../configuration/ApiGatewayConsumerRolePocInitialConfigurationLocator.java` | → `initial_configuration.json` |
| `.../configuration/OrderApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java` | → order 스펙 JSON |
| `.../configuration/ProductApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java` | → product 스펙 JSON |
| `.../services/order_api/OrderapiApiV1OrdersAdapter.java` | 어댑터 (옵션 자리 없음) |
| `.../services/order_api/model/RequestParamOfGetApiV1OrdersOrderId.java` | 요청 파라미터 |
| `.../services/order_api/model/OrderDto.java` | 응답 |
| `.../services/product_api/ProductapiApiV1ProductsAdapter.java` | 어댑터 |
| `.../services/product_api/model/RequestParamOfGetApiV1ProductsProductId.java` | 요청 파라미터 |
| `.../services/product_api/model/ProductDto.java` | 응답 |
| `src/main/resources/generation-meta.json` | profile/consumers |
| `src/main/resources/initial_configuration.json` | provider별 host (jar 기본값) |
| `src/main/resources/order_api_of_api-gateway-consumer-role-poc_configuration.json` | timeout **3000** |
| `src/main/resources/product_api_of_api-gateway-consumer-role-poc_configuration.json` | timeout **8000** |

### `client-config` (Kotlin) — "내 코드"

| 파일 | 책임 |
|---|---|
| `src/main/kotlin/poc/client/PocClientApplication.kt` | `@SpringBootConfiguration` 루트 |
| `src/main/kotlin/poc/client/config/PylonClientProperty.kt` | `pylon.client.*` 바인딩 |
| `src/main/kotlin/poc/client/config/PylonClientConfig.kt` | `@Bean @Primary` + 오타 검증 + 로그 |
| `src/main/resources/application.yml` | 기본 (무변경) |
| `src/main/resources/application-local.yml` | 짧은 timeout |
| `src/main/resources/application-production.yml` | 긴 timeout + per-spec |
| `src/test/kotlin/poc/client/config/PrimaryOverrideTest.kt` | 함정 1 |
| `src/test/kotlin/poc/client/config/ProviderReadTimeoutTest.kt` | yml → Spec.timeout |
| `src/test/kotlin/poc/client/config/PerSpecBeatsProviderTest.kt` | per-spec 우선 |
| `src/test/kotlin/poc/client/config/ProviderWideClobberTest.kt` | 8000 짓밟기 문서화 |
| `src/test/kotlin/poc/client/config/ReadTimeoutReachesSocketTest.kt` | 함정 3 + 소켓 도달 |
| `src/test/kotlin/poc/client/config/ManualOverrideHostTest.kt` | 프로퍼티 host 치환 |
| `src/test/kotlin/poc/client/config/UnknownProviderFailFastTest.kt` | 오타 → 기동 실패 |

---

## Task 1: 독립 Gradle 빌드 스캐폴딩

**Files:**
- Create: `poc-injection-external-config/settings.gradle.kts`
- Create: `poc-injection-external-config/build.gradle.kts`
- Create: `poc-injection-external-config/gradle.properties`
- Create: `poc-injection-external-config/pylon-lite/build.gradle.kts`
- Create: `poc-injection-external-config/api-gateway-consumer-role-poc/build.gradle.kts`
- Create: `poc-injection-external-config/client-config/build.gradle.kts`
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: Gradle 프로젝트 경로 `:pylon-lite`, `:api-gateway-consumer-role-poc`, `:client-config`. `pylon-lite` 는 `java-test-fixtures` 를 적용하므로 다른 모듈이 `testImplementation(testFixtures(project(":pylon-lite")))` 로 스텁 서버를 쓸 수 있다.

- [ ] **Step 1: 디렉토리와 wrapper 복사**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
mkdir -p poc-injection-external-config/gradle/wrapper
mkdir -p poc-injection-external-config/pylon-lite/src/main/java
mkdir -p poc-injection-external-config/pylon-lite/src/test/java
mkdir -p poc-injection-external-config/pylon-lite/src/testFixtures/java
mkdir -p poc-injection-external-config/api-gateway-consumer-role-poc/src/main/java
mkdir -p poc-injection-external-config/api-gateway-consumer-role-poc/src/main/resources
mkdir -p poc-injection-external-config/api-gateway-consumer-role-poc/src/test/java
mkdir -p poc-injection-external-config/client-config/src/main/kotlin
mkdir -p poc-injection-external-config/client-config/src/main/resources
mkdir -p poc-injection-external-config/client-config/src/test/kotlin
cp gradlew gradlew.bat poc-injection-external-config/
cp gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties \
   poc-injection-external-config/gradle/wrapper/
chmod +x poc-injection-external-config/gradlew
```

주의: 현재 레포의 `gradlew.bat` 은 수정된 상태(`git status` 에 `M`)다. 복사되는 내용이 그 수정본이어도 무해하다 — Windows 배치 파일이고 POC는 macOS/Linux에서 검증한다.

- [ ] **Step 2: `settings.gradle.kts` 작성**

```kotlin
rootProject.name = "poc-injection-external-config"

include("pylon-lite")
include("api-gateway-consumer-role-poc")
include("client-config")
```

- [ ] **Step 3: 루트 `build.gradle.kts` 작성**

```kotlin
plugins {
    base
    kotlin("jvm") version "1.4.10" apply false
    kotlin("plugin.spring") version "1.4.10" apply false
}

val springBootVersion = "2.3.4.RELEASE"

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    dependencies {
        "api"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
```

- [ ] **Step 4: `gradle.properties` 작성**

```properties
org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8
org.gradle.parallel=true
kotlin.code.style=official
```

- [ ] **Step 5: 모듈별 `build.gradle.kts` 작성**

`pylon-lite/build.gradle.kts`:

```kotlin
plugins {
    `java-test-fixtures`
}

dependencies {
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.apache.httpcomponents:httpclient")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.slf4j:slf4j-api")

    testFixturesApi("com.fasterxml.jackson.core:jackson-databind")
}
```

`api-gateway-consumer-role-poc/build.gradle.kts`:

```kotlin
dependencies {
    api(project(":pylon-lite"))
    testImplementation(testFixtures(project(":pylon-lite")))
}
```

`client-config/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    api(project(":api-gateway-consumer-role-poc"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation(testFixtures(project(":pylon-lite")))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
```

- [ ] **Step 6: 빌드가 통과하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew build`
Expected: `BUILD SUCCESSFUL`. 소스가 없으므로 컴파일할 것이 없고 테스트 0개다.

최초 실행은 의존성 다운로드로 네트워크가 필요할 수 있다. 실패하면 오프라인 캐시 부재를 그대로 보고하고 다음 태스크로 넘어가지 말 것.

- [ ] **Step 7: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config
git commit -m "chore: scaffold standalone poc-injection-external-config gradle build"
```

---

## Task 2: pylon-lite 기초 타입 — Pair, ApiException, RequestBase, Spec

**Files:**
- Create: `poc-injection-external-config/pylon-lite/src/main/java/poc/apigateway/pylon/PylonToolsMarker.java`
- Create: `.../poc/apigateway/services/ApiGatewayServiceMarker.java`
- Create: `.../poc/apigateway/configuration/ApiGatewayConfigurationMarker.java`
- Create: `.../poc/apigateway/pylon/Pair.java`
- Create: `.../poc/apigateway/pylon/ApiException.java`
- Create: `.../poc/apigateway/pylon/RequestBase.java`
- Create: `.../poc/apigateway/pylon/specs/model/Spec.java`
- Test: `poc-injection-external-config/pylon-lite/src/test/java/poc/apigateway/pylon/specs/model/SpecTest.java`

**Interfaces:**
- Consumes: Task 1의 `:pylon-lite` 모듈
- Produces:
  - `Pair(String name, String value)` — `getName()`, `getValue()`
  - `ApiException extends RuntimeException` — `ApiException(String specId, int statusCode, String message)`, `ApiException(String specId, String message, Throwable cause)`; `getSpecId()`, `getStatusCode()` (없으면 `0`)
  - `abstract RequestBase` — `getPathParams(): List<Pair>`, `getQueryParams(): List<Pair>`, `getHeaderParams(): Map<String,String>`, `getBody(): Object`; protected `addPathParam(String,Object)`, `addQueryParam(String,Object)`, `addHeaderParam(String,String)`, `setBody(Object)`
  - `Spec` — `getId()`, `getProvider()`, `getPath()`, `getMethod(): HttpMethod`, `getTimeout(): int`, `getConnectionPool(): Spec.ConnectionPool`, `getHostOverride(): HostOverride` (nullable). `Spec.builder(String id, String provider, String path): SpecBuilder`, `Spec.builder(Spec spec): SpecBuilder` (복사). `SpecBuilder` 는 `setMethod`, `setTimeout`, `setConnectionPool`, `setHostOverride`, `build()`
  - `Spec.ConnectionPool(String name, int size)` — `getName()`, `getSize()`

`HostOverride` 는 Task 5에서 만든다. Task 2에서는 `Spec` 이 그 타입을 참조하므로, **Task 2에서 `HostOverride` 를 먼저 만든다** (아래 Step 3에 포함).

- [ ] **Step 1: 실패하는 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/specs/model/SpecTest.java`:

```java
package poc.apigateway.pylon.specs.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.customizer.HostOverride;

import static org.assertj.core.api.Assertions.assertThat;

class SpecTest {

    private Spec base() {
        return Spec.builder("spec-1", "order_api", "/api/v1/orders/{orderId}")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void builds_with_all_fields() {
        Spec spec = base();

        assertThat(spec.getId()).isEqualTo("spec-1");
        assertThat(spec.getProvider()).isEqualTo("order_api");
        assertThat(spec.getPath()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(spec.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(spec.getTimeout()).isEqualTo(3000);
        assertThat(spec.getConnectionPool().getName()).isEqualTo("shared");
        assertThat(spec.getConnectionPool().getSize()).isEqualTo(100);
        assertThat(spec.getHostOverride()).isNull();
    }

    @Test
    void copy_builder_changes_only_the_named_field() {
        Spec copy = Spec.builder(base()).setTimeout(1500).build();

        assertThat(copy.getTimeout()).isEqualTo(1500);
        assertThat(copy.getId()).isEqualTo("spec-1");
        assertThat(copy.getProvider()).isEqualTo("order_api");
        assertThat(copy.getPath()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(copy.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(copy.getConnectionPool().getName()).isEqualTo("shared");
    }

    @Test
    void copy_builder_leaves_the_original_untouched() {
        Spec original = base();
        Spec.builder(original).setTimeout(1).build();

        assertThat(original.getTimeout()).isEqualTo(3000);
    }

    @Test
    void carries_host_override() {
        Spec spec = Spec.builder(base())
            .setHostOverride(HostOverride.of("http", "127.0.0.1", 8080))
            .build();

        assertThat(spec.getHostOverride().getScheme()).isEqualTo("http");
        assertThat(spec.getHostOverride().getHost()).isEqualTo("127.0.0.1");
        assertThat(spec.getHostOverride().getPort()).isEqualTo(8080);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*SpecTest'`
Expected: 컴파일 실패 — `package poc.apigateway.pylon.specs.model does not exist`, `cannot find symbol Spec`, `cannot find symbol HostOverride`

- [ ] **Step 3: 마커·기초 타입 구현**

`poc/apigateway/pylon/PylonToolsMarker.java`:

```java
package poc.apigateway.pylon;

/** 컴포넌트 스캔 기준점. 인스턴스를 만들지 않는다. */
public final class PylonToolsMarker {
    private PylonToolsMarker() {
    }
}
```

`poc/apigateway/services/ApiGatewayServiceMarker.java`:

```java
package poc.apigateway.services;

/**
 * 컴포넌트 스캔 기준점. 생성 모듈(api-gateway-consumer-role-poc)이 이 패키지 아래에
 * 어댑터를 채워 넣는다. 실제 pylon도 마커를 tools jar에 두고 생성 jar가 같은 패키지를 쓴다.
 */
public final class ApiGatewayServiceMarker {
    private ApiGatewayServiceMarker() {
    }
}
```

`poc/apigateway/configuration/ApiGatewayConfigurationMarker.java`:

```java
package poc.apigateway.configuration;

/**
 * 컴포넌트 스캔 기준점. 생성 모듈이 이 패키지 아래에 Locator 구현을 채워 넣는다.
 */
public final class ApiGatewayConfigurationMarker {
    private ApiGatewayConfigurationMarker() {
    }
}
```

`poc/apigateway/pylon/Pair.java`:

```java
package poc.apigateway.pylon;

public class Pair {
    private final String name;
    private final String value;

    public Pair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
```

`poc/apigateway/pylon/ApiException.java`:

```java
package poc.apigateway.pylon;

public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String specId;
    private final int statusCode;

    public ApiException(String specId, int statusCode, String message) {
        super("[" + specId + "] " + message);
        this.specId = specId;
        this.statusCode = statusCode;
    }

    public ApiException(String specId, String message, Throwable cause) {
        super("[" + specId + "] " + message, cause);
        this.specId = specId;
        this.statusCode = 0;
    }

    public String getSpecId() {
        return specId;
    }

    /** HTTP 상태를 특정할 수 없는 실패(타임아웃 등)는 0이다. */
    public int getStatusCode() {
        return statusCode;
    }
}
```

`poc/apigateway/pylon/RequestBase.java`:

```java
package poc.apigateway.pylon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RequestBase {
    private final List<Pair> pathParams = new ArrayList<>();
    private final List<Pair> queryParams = new ArrayList<>();
    private final Map<String, String> headerParams = new HashMap<>();
    private Object body;

    public List<Pair> getPathParams() {
        return pathParams;
    }

    public List<Pair> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getHeaderParams() {
        return headerParams;
    }

    public Object getBody() {
        return body;
    }

    protected void addPathParam(String name, Object value) {
        pathParams.add(new Pair(name, String.valueOf(value)));
    }

    protected void addQueryParam(String name, Object value) {
        if (value != null) {
            queryParams.add(new Pair(name, String.valueOf(value)));
        }
    }

    protected void addHeaderParam(String name, String value) {
        headerParams.put(name, value);
    }

    protected void setBody(Object body) {
        this.body = body;
    }
}
```

`poc/apigateway/pylon/specs/customizer/HostOverride.java`:

```java
package poc.apigateway.pylon.specs.customizer;

public class HostOverride {
    private final String scheme;
    private final String host;
    private final int port;

    private HostOverride(String scheme, String host, int port) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
    }

    public static HostOverride of(String scheme, String host, int port) {
        return new HostOverride(scheme, host, port);
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ":" + port;
    }
}
```

`poc/apigateway/pylon/specs/model/Spec.java`:

```java
package poc.apigateway.pylon.specs.model;

import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.customizer.HostOverride;

public class Spec {
    private final String id;
    private final String provider;
    private final String path;
    private final HttpMethod method;
    private final int timeout;
    private final ConnectionPool connectionPool;
    private final HostOverride hostOverride;

    private Spec(SpecBuilder builder) {
        this.id = builder.id;
        this.provider = builder.provider;
        this.path = builder.path;
        this.method = builder.method;
        this.timeout = builder.timeout;
        this.connectionPool = builder.connectionPool;
        this.hostOverride = builder.hostOverride;
    }

    public static SpecBuilder builder(String id, String provider, String path) {
        return new SpecBuilder(id, provider, path);
    }

    /** 기존 Spec을 복사한 빌더. 원본은 변하지 않는다. */
    public static SpecBuilder builder(Spec spec) {
        SpecBuilder builder = new SpecBuilder(spec.id, spec.provider, spec.path);
        builder.method = spec.method;
        builder.timeout = spec.timeout;
        builder.connectionPool = spec.connectionPool;
        builder.hostOverride = spec.hostOverride;
        return builder;
    }

    public String getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getPath() {
        return path;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public int getTimeout() {
        return timeout;
    }

    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    /** null이면 치환이 없다는 뜻이다. */
    public HostOverride getHostOverride() {
        return hostOverride;
    }

    public String describe() {
        return "Spec{id=" + id + ", provider=" + provider + ", method=" + method + ", path=" + path
            + ", timeout=" + timeout + ", pool=" + connectionPool + ", hostOverride=" + hostOverride + "}";
    }

    @Override
    public String toString() {
        return describe();
    }

    public static class ConnectionPool {
        private final String name;
        private final int size;

        public ConnectionPool(String name, int size) {
            this.name = name;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            return size;
        }

        @Override
        public String toString() {
            return name + "(" + size + ")";
        }
    }

    public static class SpecBuilder {
        private final String id;
        private final String provider;
        private final String path;
        private HttpMethod method = HttpMethod.GET;
        private int timeout;
        private ConnectionPool connectionPool;
        private HostOverride hostOverride;

        private SpecBuilder(String id, String provider, String path) {
            this.id = id;
            this.provider = provider;
            this.path = path;
        }

        public SpecBuilder setMethod(HttpMethod method) {
            this.method = method;
            return this;
        }

        public SpecBuilder setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public SpecBuilder setConnectionPool(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
            return this;
        }

        public SpecBuilder setHostOverride(HostOverride hostOverride) {
            this.hostOverride = hostOverride;
            return this;
        }

        public Spec build() {
            return new Spec(this);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*SpecTest'`
Expected: PASS, 4개 테스트

- [ ] **Step 5: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add pylon-lite core types (Pair, ApiException, RequestBase, Spec, HostOverride)"
```

---

## Task 3: 계약 인터페이스 + DTO + BuildConfigurations

**Files:**
- Create: `.../poc/apigateway/pylon/configuration/generated/SpecConfigurationLocator.java`
- Create: `.../poc/apigateway/pylon/configuration/generated/InitialConfigurationLocator.java`
- Create: `.../poc/apigateway/pylon/configuration/generated/GenerationMetaLocator.java`
- Create: `.../poc/apigateway/pylon/configuration/generated/PylonCodeGeneratorVersion.java`
- Create: `.../poc/apigateway/pylon/configuration/dto/ApiSpecificationConfigurationDto.java`
- Create: `.../poc/apigateway/pylon/configuration/dto/ProviderConfigurationDto.java`
- Create: `.../poc/apigateway/pylon/configuration/dto/InitialConfigurationDto.java`
- Create: `.../poc/apigateway/pylon/configuration/dto/GenerationMetaDto.java`
- Create: `.../poc/apigateway/pylon/configuration/BuildConfigurations.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/configuration/BuildConfigurationsTest.java`
- Test resources: `pylon-lite/src/test/resources/fixture-*.json` (3개)

**Interfaces:**
- Consumes: Task 2의 `ApiException`
- Produces:
  - `SpecConfigurationLocator` / `InitialConfigurationLocator` / `GenerationMetaLocator` — 각각 `String getPath()`
  - `PylonCodeGeneratorVersion` — `String getVersion()`, `int getCompatibilityLevel()`
  - `ApiSpecificationConfigurationDto` — `getId()`, `getRevision()`, `getType()`, `getPath()`, `getMethod()`, `getProduces()`, `getConsumes()`, `getTimeout(): Integer` + setter 전부
  - `ProviderConfigurationDto` — `getName()`, `getSpecifications(): List<ApiSpecificationConfigurationDto>` + setter
  - `InitialConfigurationDto` — `getConsumers(): Map<String, Consumer>`; 중첩 `Consumer.getRoutingPolicies()`, `RoutingPolicies.getProviders(): List<ProviderPolicy>`, `ProviderPolicy.getName()`/`getRegions()`, `Region.getRoutingType()`/`getTargets()`, `Target.getScheme()`/`getHost()`/`getPort()`
  - `GenerationMetaDto` — `getProfile()`, `getConsumers(): List<String>`, `getApiManagementHost()`
  - `BuildConfigurations` (`@Component`) — 생성자 `(List<SpecConfigurationLocator>, InitialConfigurationLocator, GenerationMetaLocator)`; `getProviders(): List<ProviderConfigurationDto>`, `getInitialConfiguration(): InitialConfigurationDto`, `getGenerationMeta(): GenerationMetaDto`

**설계 노트:** 실제 pylon은 `BuildConfigurations.getGradlePluginGeneratingDtoLoader().getProviders()` 로 한 단계 더 감싼다. POC는 `getProviders()` 로 직접 노출해 계층을 하나 줄인다. Task 13의 `client-config` 가 `mycoupang-app` 버전과 다른 유일한 줄이 이것이다.

- [ ] **Step 1: 테스트 픽스처 JSON 3개 작성**

`pylon-lite/src/test/resources/fixture-provider.json`:

```json
{
  "name": "order_api",
  "specifications": [
    {
      "id": "spec-order-1",
      "revision": "rev-order-1",
      "type": "SINGLE",
      "path": "/api/v1/orders/{orderId}",
      "method": "get",
      "produces": ["application/json"],
      "consumes": [],
      "timeout": 3000
    }
  ]
}
```

`pylon-lite/src/test/resources/fixture-initial.json`:

```json
{
  "consumers": {
    "poc": {
      "routingPolicies": {
        "providers": [
          {
            "name": "order_api",
            "regions": [
              {
                "name": "TO_LOAD_BALANCER",
                "usage": 100,
                "routingType": "DIRECT",
                "targets": [
                  { "scheme": "HTTP", "host": "order-api.fixture.internal", "port": 80 }
                ]
              }
            ]
          }
        ]
      }
    }
  }
}
```

`pylon-lite/src/test/resources/fixture-meta.json`:

```json
{
  "profile": "TEST",
  "consumers": ["poc"],
  "apiManagementHost": "http://api-management.fixture.internal"
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/configuration/BuildConfigurationsTest.java`:

```java
package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import poc.apigateway.pylon.configuration.dto.ApiSpecificationConfigurationDto;
import poc.apigateway.pylon.configuration.dto.InitialConfigurationDto;
import poc.apigateway.pylon.configuration.dto.ProviderConfigurationDto;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildConfigurationsTest {

    private BuildConfigurations load(String providerPath) {
        List<SpecConfigurationLocator> specs =
            Collections.singletonList(() -> providerPath);
        InitialConfigurationLocator initial = () -> "fixture-initial.json";
        GenerationMetaLocator meta = () -> "fixture-meta.json";
        return new BuildConfigurations(specs, initial, meta);
    }

    @Test
    void reads_provider_specifications_from_classpath() {
        BuildConfigurations configurations = load("fixture-provider.json");

        List<ProviderConfigurationDto> providers = configurations.getProviders();
        assertThat(providers).hasSize(1);

        ProviderConfigurationDto provider = providers.get(0);
        assertThat(provider.getName()).isEqualTo("order_api");
        assertThat(provider.getSpecifications()).hasSize(1);

        ApiSpecificationConfigurationDto spec = provider.getSpecifications().get(0);
        assertThat(spec.getId()).isEqualTo("spec-order-1");
        assertThat(spec.getPath()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(spec.getMethod()).isEqualTo("get");
        assertThat(spec.getTimeout()).isEqualTo(3000);
    }

    @Test
    void reads_routing_targets_from_initial_configuration() {
        InitialConfigurationDto initial = load("fixture-provider.json").getInitialConfiguration();

        InitialConfigurationDto.Target target = initial
            .getConsumers().get("poc")
            .getRoutingPolicies()
            .getProviders().get(0)
            .getRegions().get(0)
            .getTargets().get(0);

        assertThat(target.getScheme()).isEqualTo("HTTP");
        assertThat(target.getHost()).isEqualTo("order-api.fixture.internal");
        assertThat(target.getPort()).isEqualTo(80);
    }

    @Test
    void reads_generation_meta() {
        assertThat(load("fixture-provider.json").getGenerationMeta().getProfile()).isEqualTo("TEST");
    }

    @Test
    void fails_fast_when_a_resource_is_missing() {
        assertThatThrownBy(() -> load("no-such-file.json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no-such-file.json");
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*BuildConfigurationsTest'`
Expected: 컴파일 실패 — `cannot find symbol BuildConfigurations`

- [ ] **Step 4: 계약 인터페이스 구현**

`poc/apigateway/pylon/configuration/generated/SpecConfigurationLocator.java`:

```java
package poc.apigateway.pylon.configuration.generated;

/** 생성 모듈이 provider별 스펙 JSON의 클래스패스 경로를 알려준다. */
public interface SpecConfigurationLocator {
    String getPath();
}
```

`poc/apigateway/pylon/configuration/generated/InitialConfigurationLocator.java`:

```java
package poc.apigateway.pylon.configuration.generated;

/** 생성 모듈이 라우팅 기본값 JSON의 클래스패스 경로를 알려준다. */
public interface InitialConfigurationLocator {
    String getPath();
}
```

`poc/apigateway/pylon/configuration/generated/GenerationMetaLocator.java`:

```java
package poc.apigateway.pylon.configuration.generated;

/** 생성 모듈이 생성 메타 JSON의 클래스패스 경로를 알려준다. */
public interface GenerationMetaLocator {
    String getPath();
}
```

`poc/apigateway/pylon/configuration/generated/PylonCodeGeneratorVersion.java`:

```java
package poc.apigateway.pylon.configuration.generated;

public interface PylonCodeGeneratorVersion {
    String getVersion();

    int getCompatibilityLevel();
}
```

- [ ] **Step 5: DTO 구현**

`poc/apigateway/pylon/configuration/dto/ApiSpecificationConfigurationDto.java`:

```java
package poc.apigateway.pylon.configuration.dto;

import java.util.List;

public class ApiSpecificationConfigurationDto {
    private String id;
    private String revision;
    private String type;
    private String path;
    private String method;
    private List<String> produces;
    private List<String> consumes;
    private Integer timeout;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public void setConsumes(List<String> consumes) {
        this.consumes = consumes;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}
```

`poc/apigateway/pylon/configuration/dto/ProviderConfigurationDto.java`:

```java
package poc.apigateway.pylon.configuration.dto;

import java.util.List;

public class ProviderConfigurationDto {
    private String name;
    private List<ApiSpecificationConfigurationDto> specifications;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ApiSpecificationConfigurationDto> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(List<ApiSpecificationConfigurationDto> specifications) {
        this.specifications = specifications;
    }
}
```

`poc/apigateway/pylon/configuration/dto/InitialConfigurationDto.java`:

```java
package poc.apigateway.pylon.configuration.dto;

import java.util.List;
import java.util.Map;

public class InitialConfigurationDto {
    private Map<String, Consumer> consumers;

    public Map<String, Consumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(Map<String, Consumer> consumers) {
        this.consumers = consumers;
    }

    public static class Consumer {
        private RoutingPolicies routingPolicies;

        public RoutingPolicies getRoutingPolicies() {
            return routingPolicies;
        }

        public void setRoutingPolicies(RoutingPolicies routingPolicies) {
            this.routingPolicies = routingPolicies;
        }
    }

    public static class RoutingPolicies {
        private List<ProviderPolicy> providers;

        public List<ProviderPolicy> getProviders() {
            return providers;
        }

        public void setProviders(List<ProviderPolicy> providers) {
            this.providers = providers;
        }
    }

    public static class ProviderPolicy {
        private String name;
        private List<Region> regions;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Region> getRegions() {
            return regions;
        }

        public void setRegions(List<Region> regions) {
            this.regions = regions;
        }
    }

    public static class Region {
        private String name;
        private int usage;
        private String routingType;
        private List<Target> targets;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getUsage() {
            return usage;
        }

        public void setUsage(int usage) {
            this.usage = usage;
        }

        public String getRoutingType() {
            return routingType;
        }

        public void setRoutingType(String routingType) {
            this.routingType = routingType;
        }

        public List<Target> getTargets() {
            return targets;
        }

        public void setTargets(List<Target> targets) {
            this.targets = targets;
        }
    }

    public static class Target {
        private String scheme;
        private String host;
        private int port;

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
```

`poc/apigateway/pylon/configuration/dto/GenerationMetaDto.java`:

```java
package poc.apigateway.pylon.configuration.dto;

import java.util.List;

public class GenerationMetaDto {
    private String profile;
    private List<String> consumers;
    private String apiManagementHost;

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public List<String> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<String> consumers) {
        this.consumers = consumers;
    }

    public String getApiManagementHost() {
        return apiManagementHost;
    }

    public void setApiManagementHost(String apiManagementHost) {
        this.apiManagementHost = apiManagementHost;
    }
}
```

- [ ] **Step 6: BuildConfigurations 구현**

`poc/apigateway/pylon/configuration/BuildConfigurations.java`:

```java
package poc.apigateway.pylon.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.dto.GenerationMetaDto;
import poc.apigateway.pylon.configuration.dto.InitialConfigurationDto;
import poc.apigateway.pylon.configuration.dto.ProviderConfigurationDto;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 생성 모듈이 심어둔 JSON을 읽어 "jar가 들고 있는 환경 값"을 메모리에 올린다.
 * 이 값이 뒤에서 PylonConfiguration(외부 설정)과 만나 덮어써진다.
 */
@Component
public class BuildConfigurations {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final List<ProviderConfigurationDto> providers;
    private final InitialConfigurationDto initialConfiguration;
    private final GenerationMetaDto generationMeta;

    public BuildConfigurations(List<SpecConfigurationLocator> specLocators,
                               InitialConfigurationLocator initialLocator,
                               GenerationMetaLocator metaLocator) {
        List<ProviderConfigurationDto> loaded = new ArrayList<>();
        for (SpecConfigurationLocator locator : specLocators) {
            loaded.add(read(locator.getPath(), ProviderConfigurationDto.class));
        }
        this.providers = Collections.unmodifiableList(loaded);
        this.initialConfiguration = read(initialLocator.getPath(), InitialConfigurationDto.class);
        this.generationMeta = read(metaLocator.getPath(), GenerationMetaDto.class);
    }

    public List<ProviderConfigurationDto> getProviders() {
        return providers;
    }

    public InitialConfigurationDto getInitialConfiguration() {
        return initialConfiguration;
    }

    public GenerationMetaDto getGenerationMeta() {
        return generationMeta;
    }

    private <T> T read(String path, Class<T> type) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("classpath resource not found: " + path);
            }
            return MAPPER.readValue(stream, type);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read classpath resource: " + path, e);
        }
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*BuildConfigurationsTest'`
Expected: PASS, 4개 테스트

- [ ] **Step 8: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add pylon-lite generated-jar contracts, DTOs and BuildConfigurations"
```

---

## Task 4: PylonConfiguration — 옵션 주입의 입구

**Files:**
- Create: `.../poc/apigateway/pylon/configuration/PylonConfiguration.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/configuration/PylonConfigurationTest.java`

**Interfaces:**
- Consumes: 없음 (독립 값 객체)
- Produces:
  - `PylonConfiguration` — `getProviders(): Collection<Provider>`, `getMaxConnection(): Integer` (nullable), `getConnectionTimeout(): int`, `getRoutingInfoDuration(): int`
  - `PylonConfiguration.DEFAULT_CONNECTION_PER_PROVIDER = 500`
  - `PylonConfiguration.Provider` — `getName()`, `getScheme()`, `getPort(): Integer`, `getDefaultTimeout(): Integer`, `getMaxConnection(): Integer`, `getReadTimeoutPerSpec(): Map<String,Integer>`
  - `PylonConfiguration.Builder` — `provider(String): ProviderBuilder`, `maxConnection(int)`, `connectionTimeout(int)`, `routingInfoDuration(int)`, `build()`
  - `PylonConfiguration.ProviderBuilder` — `defaultReadTimeout(Integer)`, `maxConnection(int)`, `readTimeoutPerSpec(String,int)`, `schemeAndPort(String,int)`, `register(): Builder`

기본값은 실물과 동일: `connectionTimeout` 3000ms, `routingInfoDuration` 60000ms, `maxConnection` null.

- [ ] **Step 1: 실패하는 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/configuration/PylonConfigurationTest.java`:

```java
package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PylonConfigurationTest {

    @Test
    void empty_builder_uses_library_defaults() {
        PylonConfiguration configuration = new PylonConfiguration.Builder().build();

        assertThat(configuration.getConnectionTimeout()).isEqualTo(3000);
        assertThat(configuration.getRoutingInfoDuration()).isEqualTo(60000);
        assertThat(configuration.getMaxConnection()).isNull();
        assertThat(configuration.getProviders()).isEmpty();
    }

    @Test
    void registers_provider_options() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .connectionTimeout(500)
            .maxConnection(1234)
            .provider("order_api")
                .defaultReadTimeout(1000)
                .maxConnection(20)
                .readTimeoutPerSpec("spec-order-1", 1500)
                .schemeAndPort("http", 8080)
                .register()
            .build();

        assertThat(configuration.getConnectionTimeout()).isEqualTo(500);
        assertThat(configuration.getMaxConnection()).isEqualTo(1234);

        List<PylonConfiguration.Provider> providers = new ArrayList<>(configuration.getProviders());
        assertThat(providers).hasSize(1);

        PylonConfiguration.Provider provider = providers.get(0);
        assertThat(provider.getName()).isEqualTo("order_api");
        assertThat(provider.getDefaultTimeout()).isEqualTo(1000);
        assertThat(provider.getMaxConnection()).isEqualTo(20);
        assertThat(provider.getReadTimeoutPerSpec()).containsEntry("spec-order-1", 1500);
        assertThat(provider.getScheme()).isEqualTo("http");
        assertThat(provider.getPort()).isEqualTo(8080);
    }

    @Test
    void provider_without_scheme_and_port_reports_null() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("product_api").defaultReadTimeout(2000).register()
            .build();

        PylonConfiguration.Provider provider = configuration.getProviders().iterator().next();
        assertThat(provider.getScheme()).isNull();
        assertThat(provider.getPort()).isNull();
        assertThat(provider.getMaxConnection()).isNull();
        assertThat(provider.getReadTimeoutPerSpec()).isEmpty();
    }

    @Test
    void returned_collections_are_defensive_copies() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).readTimeoutPerSpec("a", 1).register()
            .build();

        configuration.getProviders().clear();
        assertThat(configuration.getProviders()).hasSize(1);

        PylonConfiguration.Provider provider = configuration.getProviders().iterator().next();
        provider.getReadTimeoutPerSpec().clear();
        assertThat(provider.getReadTimeoutPerSpec()).hasSize(1);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*PylonConfigurationTest'`
Expected: 컴파일 실패 — `cannot find symbol PylonConfiguration`

- [ ] **Step 3: 구현**

`poc/apigateway/pylon/configuration/PylonConfiguration.java`:

```java
package poc.apigateway.pylon.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Client 옵션의 단일 입구.
 *
 * ApiGatewayAdapterConfig 가 이 타입의 빈을 기본 제공하되 @ConditionalOnMissingBean 을
 * 붙이지 않는다. 따라서 클라이언트는 이름이 다른 @Primary 빈으로만 이길 수 있다.
 */
public class PylonConfiguration {

    public static final int DEFAULT_CONNECTION_PER_PROVIDER = 500;

    private static final int DEFAULT_CONNECTION_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(3);
    private static final int DEFAULT_ROUTING_INFORMATION_RENEW_TIME = (int) TimeUnit.SECONDS.toMillis(60);

    private final List<Provider> providers;
    private final Integer maxConnection;
    private final int connectionTimeout;
    private final int routingInfoDuration;

    private PylonConfiguration(List<Provider> providers, Integer maxConnection,
                               int connectionTimeout, int routingInfoDuration) {
        this.providers = providers;
        this.maxConnection = maxConnection;
        this.connectionTimeout = connectionTimeout;
        this.routingInfoDuration = routingInfoDuration;
    }

    public Collection<Provider> getProviders() {
        return new ArrayList<>(providers);
    }

    /** null 이면 공용 풀 크기를 provider 수로 계산한다. */
    public Integer getMaxConnection() {
        return maxConnection;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getRoutingInfoDuration() {
        return routingInfoDuration;
    }

    public static class Provider {
        private final String name;
        private final String scheme;
        private final Integer port;
        private final Integer defaultTimeout;
        private final Integer maxConnection;
        private final Map<String, Integer> readTimeoutPerSpec;

        Provider(String name, String scheme, Integer port, Integer defaultTimeout,
                 Integer maxConnection, Map<String, Integer> readTimeoutPerSpec) {
            this.name = name;
            this.scheme = scheme;
            this.port = port;
            this.defaultTimeout = defaultTimeout;
            this.maxConnection = maxConnection;
            this.readTimeoutPerSpec = new HashMap<>(readTimeoutPerSpec);
        }

        public String getName() {
            return name;
        }

        public String getScheme() {
            return scheme;
        }

        public Integer getPort() {
            return port;
        }

        public Integer getDefaultTimeout() {
            return defaultTimeout;
        }

        public Integer getMaxConnection() {
            return maxConnection;
        }

        public Map<String, Integer> getReadTimeoutPerSpec() {
            return new HashMap<>(readTimeoutPerSpec);
        }
    }

    public static class ProviderBuilder {
        private final Builder builder;
        private final String name;
        private final Map<String, Integer> readTimeoutPerSpec = new HashMap<>();
        private String scheme;
        private Integer port;
        private Integer defaultTimeout;
        private Integer maxConnection;

        ProviderBuilder(Builder builder, String name) {
            this.builder = builder;
            this.name = name;
        }

        public ProviderBuilder defaultReadTimeout(Integer timeout) {
            this.defaultTimeout = timeout;
            return this;
        }

        public ProviderBuilder maxConnection(int maxConnection) {
            this.maxConnection = maxConnection;
            return this;
        }

        public ProviderBuilder readTimeoutPerSpec(String specId, int timeout) {
            this.readTimeoutPerSpec.put(specId, timeout);
            return this;
        }

        public ProviderBuilder schemeAndPort(String scheme, int port) {
            this.scheme = scheme;
            this.port = port;
            return this;
        }

        public Builder register() {
            builder.appendProvider(
                new Provider(name, scheme, port, defaultTimeout, maxConnection, readTimeoutPerSpec));
            return builder;
        }
    }

    public static class Builder {
        private final List<Provider> providers = new ArrayList<>();
        private Integer maxConnection = null;
        private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private int routingInfoDuration = DEFAULT_ROUTING_INFORMATION_RENEW_TIME;

        public ProviderBuilder provider(String name) {
            return new ProviderBuilder(this, name);
        }

        public Builder maxConnection(int maxConnection) {
            this.maxConnection = maxConnection;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder routingInfoDuration(int routingInfoDuration) {
            this.routingInfoDuration = routingInfoDuration;
            return this;
        }

        void appendProvider(Provider provider) {
            providers.add(provider);
        }

        public PylonConfiguration build() {
            return new PylonConfiguration(providers, maxConnection, connectionTimeout, routingInfoDuration);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*PylonConfigurationTest'`
Expected: PASS, 4개 테스트

- [ ] **Step 5: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add PylonConfiguration as the single entry point for client options"
```

---

## Task 5: SpecResolver + SpecCustomizer 체인

**Files:**
- Create: `.../poc/apigateway/pylon/specs/SpecResolver.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/specs/SpecResolverTest.java`

**Interfaces:**
- Consumes: Task 2의 `Spec`, `ApiException`
- Produces:
  - `SpecResolver(List<SpecResolver.SpecCustomizer> customizers, Spec.ConnectionPool defaultConnectionPool)`
  - `void register(Spec spec)` — 최초 등록 시 전체 체인 적용. 같은 id 재등록은 무시
  - `void update(Spec spec)` — 이미 등록된 id에만, `isApplicableInRuntime() == true` 인 커스터마이저만 적용
  - `Spec get(String id) throws ApiException` — 없으면 `ApiException(id, 404, ...)`
  - `Spec getEvenNull(String id)`
  - `Spec.ConnectionPool getDefaultConnectionPool()`
  - `Set<String> getProviderNames()`
  - `interface SpecResolver.SpecCustomizer { Spec process(Spec spec); boolean isApplicableInRuntime(); }`

체인은 주입된 리스트 순서대로 적용된다. Spring은 컬렉션 주입 시 `@Order`/`Ordered` 를 존중하므로 순서 제어가 필요하면 그것으로 한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/specs/SpecResolverTest.java`:

```java
package poc.apigateway.pylon.specs;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.ApiException;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecResolverTest {

    private static final Spec.ConnectionPool DEFAULT_POOL = new Spec.ConnectionPool("shared", 100);

    private Spec spec(String id, String provider) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(DEFAULT_POOL)
            .build();
    }

    /** 호출 순서를 기록하며 timeout 에 표식을 남기는 커스터마이저. */
    private static class Recording implements SpecResolver.SpecCustomizer {
        private final List<String> log;
        private final String name;
        private final int timeout;
        private final boolean runtime;

        Recording(List<String> log, String name, int timeout, boolean runtime) {
            this.log = log;
            this.name = name;
            this.timeout = timeout;
            this.runtime = runtime;
        }

        @Override
        public Spec process(Spec spec) {
            log.add(name);
            return Spec.builder(spec).setTimeout(timeout).build();
        }

        @Override
        public boolean isApplicableInRuntime() {
            return runtime;
        }
    }

    @Test
    void applies_customizers_in_list_order_on_register() {
        List<String> log = new ArrayList<>();
        SpecResolver resolver = new SpecResolver(Arrays.asList(
            new Recording(log, "first", 1000, false),
            new Recording(log, "second", 2000, false)), DEFAULT_POOL);

        resolver.register(spec("s1", "order_api"));

        assertThat(log).containsExactly("first", "second");
        assertThat(resolver.get("s1").getTimeout()).isEqualTo(2000);
    }

    @Test
    void register_ignores_duplicate_ids() {
        List<String> log = new ArrayList<>();
        SpecResolver resolver = new SpecResolver(
            Collections.singletonList(new Recording(log, "only", 1000, false)), DEFAULT_POOL);

        resolver.register(spec("s1", "order_api"));
        resolver.register(spec("s1", "order_api"));

        assertThat(log).containsExactly("only");
    }

    @Test
    void update_applies_only_runtime_applicable_customizers() {
        List<String> log = new ArrayList<>();
        SpecResolver resolver = new SpecResolver(Arrays.asList(
            new Recording(log, "boot-only", 1000, false),
            new Recording(log, "runtime", 2000, true)), DEFAULT_POOL);

        resolver.register(spec("s1", "order_api"));
        log.clear();

        resolver.update(spec("s1", "order_api"));

        assertThat(log).containsExactly("runtime");
        assertThat(resolver.get("s1").getTimeout()).isEqualTo(2000);
    }

    @Test
    void update_ignores_unregistered_ids() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);

        resolver.update(spec("nope", "order_api"));

        assertThat(resolver.getEvenNull("nope")).isNull();
    }

    @Test
    void get_throws_api_exception_for_unknown_id() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);

        assertThatThrownBy(() -> resolver.get("missing"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void collects_provider_names() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);
        resolver.register(spec("s1", "order_api"));
        resolver.register(spec("s2", "product_api"));

        assertThat(resolver.getProviderNames()).containsExactlyInAnyOrder("order_api", "product_api");
    }

    @Test
    void exposes_the_default_connection_pool() {
        SpecResolver resolver = new SpecResolver(Collections.emptyList(), DEFAULT_POOL);

        assertThat(resolver.getDefaultConnectionPool().getName()).isEqualTo("shared");
        assertThat(resolver.getDefaultConnectionPool().getSize()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*SpecResolverTest'`
Expected: 컴파일 실패 — `cannot find symbol SpecResolver`

- [ ] **Step 3: 구현**

`poc/apigateway/pylon/specs/SpecResolver.java`:

```java
package poc.apigateway.pylon.specs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poc.apigateway.pylon.ApiException;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * jar가 들고 온 Spec 과 외부 설정에서 온 커스터마이저가 만나는 지점.
 * 이 클래스가 POC의 중심이다.
 */
public class SpecResolver {

    private static final Logger log = LoggerFactory.getLogger(SpecResolver.class);

    private final Map<String, Spec> specs = new HashMap<>();
    private final List<SpecCustomizer> initializingChain;
    private final List<SpecCustomizer> updatingChain;
    private final Spec.ConnectionPool defaultConnectionPool;

    public SpecResolver(List<SpecCustomizer> customizers, Spec.ConnectionPool defaultConnectionPool) {
        this.initializingChain = Collections.unmodifiableList(new ArrayList<>(customizers));
        this.updatingChain = Collections.unmodifiableList(runtimeApplicable(customizers));
        this.defaultConnectionPool = defaultConnectionPool;
    }

    private static List<SpecCustomizer> runtimeApplicable(List<SpecCustomizer> customizers) {
        List<SpecCustomizer> filtered = new ArrayList<>();
        for (SpecCustomizer customizer : customizers) {
            if (customizer.isApplicableInRuntime()) {
                filtered.add(customizer);
            }
        }
        return filtered;
    }

    public void register(Spec spec) {
        if (specs.containsKey(spec.getId())) {
            return;
        }
        Spec customized = process(initializingChain, spec);
        specs.put(customized.getId(), customized);
        log.debug("register - {}", customized.describe());
    }

    public void update(Spec spec) {
        if (!specs.containsKey(spec.getId())) {
            return;
        }
        Spec customized = process(updatingChain, spec);
        specs.put(customized.getId(), customized);
        log.debug("update - {}", customized.describe());
    }

    public Spec get(String id) {
        Spec spec = getEvenNull(id);
        if (spec == null) {
            throw new ApiException(id, 404, "SpecResolver does not contain specId: " + id);
        }
        return spec;
    }

    public Spec getEvenNull(String id) {
        return specs.get(id);
    }

    public Spec.ConnectionPool getDefaultConnectionPool() {
        return defaultConnectionPool;
    }

    public Set<String> getProviderNames() {
        Set<String> providers = new HashSet<>();
        for (Spec spec : specs.values()) {
            providers.add(spec.getProvider());
        }
        return providers;
    }

    private Spec process(List<SpecCustomizer> chain, Spec spec) {
        Spec current = spec;
        for (SpecCustomizer customizer : chain) {
            current = customizer.process(current);
        }
        return current;
    }

    public interface SpecCustomizer {
        Spec process(Spec spec);

        /** false 면 기동 시점에만 적용된다. */
        boolean isApplicableInRuntime();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*SpecResolverTest'`
Expected: PASS, 7개 테스트

- [ ] **Step 5: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add SpecResolver with boot-time and runtime customizer chains"
```

---

## Task 6: 커스터마이저 3종 — Timeout, ConnectionPool, ManualOverride

**Files:**
- Create: `.../poc/apigateway/pylon/specs/customizer/TimeoutCustomizer.java`
- Create: `.../poc/apigateway/pylon/specs/customizer/ConnectionPoolCustomizer.java`
- Create: `.../poc/apigateway/pylon/specs/customizer/ManualOverrideCustomizer.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/specs/customizer/TimeoutCustomizerTest.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/specs/customizer/ConnectionPoolCustomizerTest.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/specs/customizer/ManualOverrideCustomizerTest.java`

**Interfaces:**
- Consumes: Task 2의 `Spec`, `HostOverride`; Task 5의 `SpecResolver.SpecCustomizer`
- Produces:
  - `TimeoutCustomizer()` — `registerBySpec(String specId, int timeout)`, `registerByProvider(String provider, int timeout)`. `isApplicableInRuntime() == false`. 우선순위: spec > provider > 원래 값
  - `ConnectionPoolCustomizer()` — `register(String provider, String poolName, int size)` (`putIfAbsent` 시맨틱). `isApplicableInRuntime() == true`
  - `ManualOverrideCustomizer()` — `registerProvider(String provider, HostOverride)`, `registerSpec(String specId, HostOverride)`, `setVersion(int)`, `getVersion(): int`. `isApplicableInRuntime() == true`. 우선순위: spec > provider

- [ ] **Step 1: TimeoutCustomizer 실패 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/specs/customizer/TimeoutCustomizerTest.java`:

```java
package poc.apigateway.pylon.specs.customizer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutCustomizerTest {

    private Spec spec(String id, String provider, int timeout) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(timeout)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void leaves_the_spec_untouched_when_nothing_is_registered() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();

        assertThat(customizer.process(spec("s1", "order_api", 3000)).getTimeout()).isEqualTo(3000);
    }

    @Test
    void provider_timeout_replaces_the_jar_value() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", 1000);

        assertThat(customizer.process(spec("s1", "order_api", 3000)).getTimeout()).isEqualTo(1000);
    }

    @Test
    void provider_timeout_does_not_leak_to_other_providers() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", 1000);

        assertThat(customizer.process(spec("s2", "product_api", 8000)).getTimeout()).isEqualTo(8000);
    }

    @Test
    void spec_timeout_beats_provider_timeout() {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        customizer.registerByProvider("order_api", 1000);
        customizer.registerBySpec("s1", 1500);

        assertThat(customizer.process(spec("s1", "order_api", 3000)).getTimeout()).isEqualTo(1500);
    }

    @Test
    void applies_at_boot_time_only() {
        assertThat(new TimeoutCustomizer().isApplicableInRuntime()).isFalse();
    }
}
```

- [ ] **Step 2: ConnectionPoolCustomizer 실패 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/specs/customizer/ConnectionPoolCustomizerTest.java`:

```java
package poc.apigateway.pylon.specs.customizer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolCustomizerTest {

    private Spec spec(String provider) {
        return Spec.builder("s1", provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void keeps_the_shared_pool_when_nothing_is_registered() {
        Spec result = new ConnectionPoolCustomizer().process(spec("order_api"));

        assertThat(result.getConnectionPool().getName()).isEqualTo("shared");
        assertThat(result.getConnectionPool().getSize()).isEqualTo(100);
    }

    @Test
    void assigns_a_dedicated_pool_to_the_registered_provider() {
        ConnectionPoolCustomizer customizer = new ConnectionPoolCustomizer();
        customizer.register("order_api", "order_api", 20);

        Spec result = customizer.process(spec("order_api"));

        assertThat(result.getConnectionPool().getName()).isEqualTo("order_api");
        assertThat(result.getConnectionPool().getSize()).isEqualTo(20);
    }

    @Test
    void first_registration_wins() {
        ConnectionPoolCustomizer customizer = new ConnectionPoolCustomizer();
        customizer.register("order_api", "order_api", 20);
        customizer.register("order_api", "order_api", 999);

        assertThat(customizer.process(spec("order_api")).getConnectionPool().getSize()).isEqualTo(20);
    }

    @Test
    void applies_at_runtime_too() {
        assertThat(new ConnectionPoolCustomizer().isApplicableInRuntime()).isTrue();
    }
}
```

- [ ] **Step 3: ManualOverrideCustomizer 실패 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/specs/customizer/ManualOverrideCustomizerTest.java`:

```java
package poc.apigateway.pylon.specs.customizer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class ManualOverrideCustomizerTest {

    private Spec spec(String id, String provider) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void attaches_nothing_when_no_override_is_registered() {
        assertThat(new ManualOverrideCustomizer().process(spec("s1", "order_api")).getHostOverride()).isNull();
    }

    @Test
    void attaches_the_provider_override() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();
        customizer.registerProvider("order_api", HostOverride.of("http", "127.0.0.1", 9001));

        HostOverride override = customizer.process(spec("s1", "order_api")).getHostOverride();

        assertThat(override.getScheme()).isEqualTo("http");
        assertThat(override.getHost()).isEqualTo("127.0.0.1");
        assertThat(override.getPort()).isEqualTo(9001);
    }

    @Test
    void spec_override_beats_provider_override() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();
        customizer.registerProvider("order_api", HostOverride.of("http", "127.0.0.1", 9001));
        customizer.registerSpec("s1", HostOverride.of("https", "127.0.0.1", 9002));

        HostOverride override = customizer.process(spec("s1", "order_api")).getHostOverride();

        assertThat(override.getScheme()).isEqualTo("https");
        assertThat(override.getPort()).isEqualTo(9002);
    }

    @Test
    void carries_a_version_marker() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();
        assertThat(customizer.getVersion()).isZero();

        customizer.setVersion(2);
        assertThat(customizer.getVersion()).isEqualTo(2);
    }

    @Test
    void applies_at_runtime_too() {
        assertThat(new ManualOverrideCustomizer().isApplicableInRuntime()).isTrue();
    }
}
```

- [ ] **Step 4: 세 테스트가 모두 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*CustomizerTest'`
Expected: 컴파일 실패 — `cannot find symbol TimeoutCustomizer`, `ConnectionPoolCustomizer`, `ManualOverrideCustomizer`

- [ ] **Step 5: TimeoutCustomizer 구현**

`poc/apigateway/pylon/specs/customizer/TimeoutCustomizer.java`:

```java
package poc.apigateway.pylon.specs.customizer;

import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TimeoutCustomizer implements SpecResolver.SpecCustomizer {

    private final ConcurrentMap<String, Integer> timeoutPerProvider = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> timeoutPerSpec = new ConcurrentHashMap<>();

    public void registerBySpec(String specId, int timeout) {
        timeoutPerSpec.put(specId, timeout);
    }

    public void registerByProvider(String provider, int timeout) {
        timeoutPerProvider.put(provider, timeout);
    }

    @Override
    public Spec process(Spec spec) {
        Integer bySpec = timeoutPerSpec.get(spec.getId());
        if (bySpec != null) {
            return Spec.builder(spec).setTimeout(bySpec).build();
        }
        Integer byProvider = timeoutPerProvider.get(spec.getProvider());
        if (byProvider != null) {
            return Spec.builder(spec).setTimeout(byProvider).build();
        }
        return spec;
    }

    @Override
    public boolean isApplicableInRuntime() {
        return false;
    }
}
```

- [ ] **Step 6: ConnectionPoolCustomizer 구현**

`poc/apigateway/pylon/specs/customizer/ConnectionPoolCustomizer.java`:

```java
package poc.apigateway.pylon.specs.customizer;

import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionPoolCustomizer implements SpecResolver.SpecCustomizer {

    private final ConcurrentMap<String, Spec.ConnectionPool> connectionPools = new ConcurrentHashMap<>();

    /** 같은 provider에 대한 두 번째 등록은 무시한다 (실물과 동일한 putIfAbsent 시맨틱). */
    public void register(String provider, String poolName, int size) {
        connectionPools.putIfAbsent(provider, new Spec.ConnectionPool(poolName, size));
    }

    @Override
    public Spec process(Spec spec) {
        Spec.ConnectionPool pool = connectionPools.get(spec.getProvider());
        if (pool != null) {
            return Spec.builder(spec).setConnectionPool(pool).build();
        }
        return spec;
    }

    @Override
    public boolean isApplicableInRuntime() {
        return true;
    }
}
```

- [ ] **Step 7: ManualOverrideCustomizer 구현**

`poc/apigateway/pylon/specs/customizer/ManualOverrideCustomizer.java`:

```java
package poc.apigateway.pylon.specs.customizer;

import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 프로퍼티 스캔으로 들어온 host 치환을 Spec에 부착한다.
 * 실제 적용은 TargetUriFinder 가 spec.getHostOverride() 를 보고 한다.
 */
public class ManualOverrideCustomizer implements SpecResolver.SpecCustomizer {

    private final ConcurrentMap<String, HostOverride> overridePerProvider = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HostOverride> overridePerSpec = new ConcurrentHashMap<>();

    private volatile int version;

    public void registerProvider(String provider, HostOverride override) {
        overridePerProvider.put(provider, override);
    }

    public void registerSpec(String specId, HostOverride override) {
        overridePerSpec.put(specId, override);
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public Spec process(Spec spec) {
        HostOverride bySpec = overridePerSpec.get(spec.getId());
        if (bySpec != null) {
            return Spec.builder(spec).setHostOverride(bySpec).build();
        }
        HostOverride byProvider = overridePerProvider.get(spec.getProvider());
        if (byProvider != null) {
            return Spec.builder(spec).setHostOverride(byProvider).build();
        }
        return spec;
    }

    @Override
    public boolean isApplicableInRuntime() {
        return true;
    }
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*CustomizerTest'`
Expected: PASS, 14개 테스트 (Timeout 5 + ConnectionPool 4 + ManualOverride 5)

- [ ] **Step 9: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add timeout, connection pool and manual override spec customizers"
```

---

## Task 7: SchemeAndPortOverrider + ManualOverrideConfiguration (프로퍼티 스캔)

**Files:**
- Create: `.../poc/apigateway/pylon/configuration/SchemeAndPortOverrider.java`
- Create: `.../poc/apigateway/pylon/configuration/ManualOverrideConfiguration.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/configuration/SchemeAndPortOverriderTest.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/configuration/ManualOverrideConfigurationTest.java`

**Interfaces:**
- Consumes: Task 4의 `PylonConfiguration`; Task 6의 `ManualOverrideCustomizer`, `HostOverride`
- Produces:
  - `SchemeAndPortOverrider(PylonConfiguration)` (`@Component`) — `String schemeOf(String provider, String fallback)`, `int portOf(String provider, int fallback)`, `boolean has(String provider)`
  - `ManualOverrideConfiguration` (`@Configuration`) — `@Bean ManualOverrideCustomizer apiGatewayManualOverrideProvider()`. 인식하는 키:
    - `api_gateway.manual_override.version`
    - `api_gateway.manual_override.provider.<name>.server`
    - `api_gateway.manual_override.spec.<specId>.server`

정규식은 실물과 동일하게 `([\w]+)` 를 쓴다 — `_` 를 포함하는 provider명(`order_api`)이 매칭돼야 한다. 포트가 URI에 없으면 `https` → 443, 그 외 → 80.

- [ ] **Step 1: SchemeAndPortOverrider 실패 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/configuration/SchemeAndPortOverriderTest.java`:

```java
package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemeAndPortOverriderTest {

    @Test
    void falls_back_when_the_provider_has_no_override() {
        SchemeAndPortOverrider overrider =
            new SchemeAndPortOverrider(new PylonConfiguration.Builder().build());

        assertThat(overrider.has("order_api")).isFalse();
        assertThat(overrider.schemeOf("order_api", "https")).isEqualTo("https");
        assertThat(overrider.portOf("order_api", 443)).isEqualTo(443);
    }

    @Test
    void replaces_scheme_and_port_for_the_registered_provider() {
        SchemeAndPortOverrider overrider = new SchemeAndPortOverrider(new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).schemeAndPort("http", 8080).register()
            .build());

        assertThat(overrider.has("order_api")).isTrue();
        assertThat(overrider.schemeOf("order_api", "https")).isEqualTo("http");
        assertThat(overrider.portOf("order_api", 443)).isEqualTo(8080);
    }

    @Test
    void ignores_a_provider_that_configured_only_a_timeout() {
        SchemeAndPortOverrider overrider = new SchemeAndPortOverrider(new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).register()
            .build());

        assertThat(overrider.has("order_api")).isFalse();
        assertThat(overrider.portOf("order_api", 443)).isEqualTo(443);
    }
}
```

- [ ] **Step 2: ManualOverrideConfiguration 실패 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/configuration/ManualOverrideConfigurationTest.java`:

```java
package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.customizer.ManualOverrideCustomizer;
import poc.apigateway.pylon.specs.model.Spec;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ManualOverrideConfigurationTest {

    private ManualOverrideCustomizer customizerFrom(MockEnvironment environment) {
        ManualOverrideConfiguration configuration = new ManualOverrideConfiguration();
        configuration.setEnvironment(environment);
        return configuration.apiGatewayManualOverrideProvider();
    }

    private Spec spec(String id, String provider) {
        return Spec.builder(id, provider, "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    @Test
    void reads_a_provider_override_with_an_explicit_port() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.provider.order_api.server", "http://127.0.0.1:9001");

        HostOverride override = customizerFrom(environment).process(spec("s1", "order_api")).getHostOverride();

        assertThat(override.getScheme()).isEqualTo("http");
        assertThat(override.getHost()).isEqualTo("127.0.0.1");
        assertThat(override.getPort()).isEqualTo(9001);
    }

    @Test
    void defaults_the_port_from_the_scheme() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.provider.order_api.server", "https://order.example.com")
            .withProperty("api_gateway.manual_override.provider.product_api.server", "http://product.example.com");

        ManualOverrideCustomizer customizer = customizerFrom(environment);

        assertThat(customizer.process(spec("s1", "order_api")).getHostOverride().getPort()).isEqualTo(443);
        assertThat(customizer.process(spec("s2", "product_api")).getHostOverride().getPort()).isEqualTo(80);
    }

    @Test
    void reads_a_spec_level_override() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.spec.specorder1.server", "http://127.0.0.1:9002");

        HostOverride override =
            customizerFrom(environment).process(spec("specorder1", "order_api")).getHostOverride();

        assertThat(override.getPort()).isEqualTo(9002);
    }

    @Test
    void reads_the_version_marker() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.version", "3");

        assertThat(customizerFrom(environment).getVersion()).isEqualTo(3);
    }

    @Test
    void skips_an_unparseable_value_instead_of_failing() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("api_gateway.manual_override.provider.order_api.server", ":::not a uri:::");

        assertThat(customizerFrom(environment).process(spec("s1", "order_api")).getHostOverride()).isNull();
    }

    @Test
    void ignores_unrelated_properties() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("pylon.client.connect-timeout", "500");

        assertThat(customizerFrom(environment).process(spec("s1", "order_api")).getHostOverride()).isNull();
    }
}
```

- [ ] **Step 3: 두 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*SchemeAndPortOverriderTest' --tests '*ManualOverrideConfigurationTest'`
Expected: 컴파일 실패 — `cannot find symbol SchemeAndPortOverrider`, `ManualOverrideConfiguration`

`MockEnvironment` 는 `spring-test` 에 있고 `spring-boot-starter-test` 로 이미 들어와 있다.

- [ ] **Step 4: SchemeAndPortOverrider 구현**

`poc/apigateway/pylon/configuration/SchemeAndPortOverrider.java`:

```java
package poc.apigateway.pylon.configuration;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * provider별 scheme·port만 치환한다. host는 건드리지 않는다 —
 * host 통째 교체는 ManualOverride 경로의 몫이다.
 */
@Component
public class SchemeAndPortOverrider {

    private final Map<String, String> schemes = new HashMap<>();
    private final Map<String, Integer> ports = new HashMap<>();

    public SchemeAndPortOverrider(PylonConfiguration configuration) {
        for (PylonConfiguration.Provider provider : configuration.getProviders()) {
            if (provider.getScheme() != null && provider.getPort() != null) {
                schemes.put(provider.getName(), provider.getScheme());
                ports.put(provider.getName(), provider.getPort());
            }
        }
    }

    public boolean has(String provider) {
        return schemes.containsKey(provider);
    }

    public String schemeOf(String provider, String fallback) {
        return has(provider) ? schemes.get(provider) : fallback;
    }

    public int portOf(String provider, int fallback) {
        return has(provider) ? ports.get(provider) : fallback;
    }
}
```

- [ ] **Step 5: ManualOverrideConfiguration 구현**

`poc/apigateway/pylon/configuration/ManualOverrideConfiguration.java`:

```java
package poc.apigateway.pylon.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.customizer.ManualOverrideCustomizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 두 번째 주입 경로. 타입 빈이 아니라 Environment 를 정규식으로 훑는다.
 * 키 이름은 실제 pylon 을 그대로 미러링한다.
 */
@Configuration
public class ManualOverrideConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ManualOverrideConfiguration.class);

    private static final String VERSION = "api_gateway.manual_override.version";
    private static final Pattern PROVIDER_SERVER =
        Pattern.compile("api_gateway\\.manual_override\\.provider\\.([\\w]+)\\.server");
    private static final Pattern SPEC_SERVER =
        Pattern.compile("api_gateway\\.manual_override\\.spec\\.([\\w]+)\\.server");

    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final String SCHEME_HTTPS = "https";

    private Environment environment;

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public ManualOverrideCustomizer apiGatewayManualOverrideProvider() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();

        for (PropertySource<?> source : ((AbstractEnvironment) environment).getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String key : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                String value = environment.getProperty(key);
                if (value == null) {
                    continue;
                }
                registerVersion(key, value, customizer);
                registerProvider(key, value, customizer);
                registerSpec(key, value, customizer);
            }
        }
        return customizer;
    }

    private void registerVersion(String key, String value, ManualOverrideCustomizer customizer) {
        if (!VERSION.equals(key)) {
            return;
        }
        try {
            int version = Integer.parseInt(value.trim());
            log.info("API Gateway manual override version will be applied as {}", version);
            customizer.setVersion(version);
        } catch (NumberFormatException e) {
            log.warn("invalid manual override version '{}', ignoring", value);
        }
    }

    private void registerProvider(String key, String value, ManualOverrideCustomizer customizer) {
        Matcher matcher = PROVIDER_SERVER.matcher(key);
        if (!matcher.matches()) {
            return;
        }
        HostOverride override = parse(key, value);
        if (override != null) {
            log.info("manual override - provider {} -> {}", matcher.group(1), override);
            customizer.registerProvider(matcher.group(1), override);
        }
    }

    private void registerSpec(String key, String value, ManualOverrideCustomizer customizer) {
        Matcher matcher = SPEC_SERVER.matcher(key);
        if (!matcher.matches()) {
            return;
        }
        HostOverride override = parse(key, value);
        if (override != null) {
            log.info("manual override - spec {} -> {}", matcher.group(1), override);
            customizer.registerSpec(matcher.group(1), override);
        }
    }

    private HostOverride parse(String key, String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                log.warn("invalid manual override value '{}' for property {}", value, key);
                return null;
            }
            int port = uri.getPort();
            if (port < 0) {
                port = SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme()) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }
            return HostOverride.of(uri.getScheme(), uri.getHost(), port);
        } catch (URISyntaxException e) {
            log.warn("invalid manual override value '{}' for property {}", value, key);
            return null;
        }
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*SchemeAndPortOverriderTest' --tests '*ManualOverrideConfigurationTest'`
Expected: PASS, 9개 테스트 (SchemeAndPort 3 + ManualOverride 6)

- [ ] **Step 7: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add scheme/port overrider and property-scan manual override path"
```

---

## Task 8: TargetUriFinder — 치환 우선순위 확정

**Files:**
- Create: `.../poc/apigateway/pylon/targets/TargetUriFinder.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/targets/TargetUriFinderTest.java`

**Interfaces:**
- Consumes: Task 3의 `BuildConfigurations`, `InitialConfigurationDto`; Task 6의 `HostOverride`; Task 7의 `SchemeAndPortOverrider`; Task 2의 `Spec`, `Pair`
- Produces:
  - `TargetUriFinder(BuildConfigurations, SchemeAndPortOverrider)` (`@Component`)
  - `URI find(Spec spec, List<Pair> pathParams, List<Pair> queryParams)`

우선순위 (높은 쪽이 이긴다):
1. `spec.getHostOverride()` — scheme·host·port 통째 교체
2. `SchemeAndPortOverrider` — scheme·port만, host는 jar 값 유지
3. `initial_configuration.json` 의 첫 번째 region의 첫 번째 target

path 변수 `{orderId}` 는 `pathParams` 의 이름으로 치환한다. query는 `?a=1&b=2` 로 붙인다. scheme 은 소문자화한다 (jar JSON은 `HTTP`/`HTTPS` 대문자다).

- [ ] **Step 1: 실패하는 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/targets/TargetUriFinderTest.java`:

```java
package poc.apigateway.pylon.targets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.Pair;
import poc.apigateway.pylon.configuration.BuildConfigurations;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.configuration.SchemeAndPortOverrider;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.model.Spec;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetUriFinderTest {

    private BuildConfigurations buildConfigurations() {
        List<SpecConfigurationLocator> specs = Collections.singletonList(() -> "fixture-provider.json");
        InitialConfigurationLocator initial = () -> "fixture-initial.json";
        GenerationMetaLocator meta = () -> "fixture-meta.json";
        return new BuildConfigurations(specs, initial, meta);
    }

    private TargetUriFinder finder(PylonConfiguration configuration) {
        return new TargetUriFinder(buildConfigurations(), new SchemeAndPortOverrider(configuration));
    }

    private Spec spec(String provider, String path, HostOverride hostOverride) {
        return Spec.builder("spec-order-1", provider, path)
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .setHostOverride(hostOverride)
            .build();
    }

    @Test
    void uses_the_jar_target_when_nothing_overrides_it() {
        URI uri = finder(new PylonConfiguration.Builder().build())
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.singletonList(new Pair("orderId", "42")),
                Collections.emptyList());

        assertThat(uri.toString()).isEqualTo("http://order-api.fixture.internal:80/api/v1/orders/42");
    }

    @Test
    void scheme_and_port_override_keeps_the_jar_host() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).schemeAndPort("https", 8443).register()
            .build();

        URI uri = finder(configuration)
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.singletonList(new Pair("orderId", "42")),
                Collections.emptyList());

        assertThat(uri.toString()).isEqualTo("https://order-api.fixture.internal:8443/api/v1/orders/42");
    }

    @Test
    void host_override_wins_over_scheme_and_port_override() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).schemeAndPort("https", 8443).register()
            .build();

        URI uri = finder(configuration)
            .find(spec("order_api", "/api/v1/orders/{orderId}", HostOverride.of("http", "127.0.0.1", 9001)),
                Collections.singletonList(new Pair("orderId", "42")),
                Collections.emptyList());

        assertThat(uri.toString()).isEqualTo("http://127.0.0.1:9001/api/v1/orders/42");
    }

    @Test
    void appends_query_parameters() {
        URI uri = finder(new PylonConfiguration.Builder().build())
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.singletonList(new Pair("orderId", "42")),
                Arrays.asList(new Pair("verbose", "true"), new Pair("lang", "ko")));

        assertThat(uri.toString())
            .isEqualTo("http://order-api.fixture.internal:80/api/v1/orders/42?verbose=true&lang=ko");
    }

    @Test
    void fails_when_the_provider_has_no_routing_target() {
        assertThatThrownBy(() -> finder(new PylonConfiguration.Builder().build())
            .find(spec("unknown_api", "/api/v1/x", null), Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown_api");
    }

    @Test
    void fails_when_a_path_variable_is_not_supplied() {
        assertThatThrownBy(() -> finder(new PylonConfiguration.Builder().build())
            .find(spec("order_api", "/api/v1/orders/{orderId}", null),
                Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("orderId");
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*TargetUriFinderTest'`
Expected: 컴파일 실패 — `cannot find symbol TargetUriFinder`

- [ ] **Step 3: 구현**

`poc/apigateway/pylon/targets/TargetUriFinder.java`:

```java
package poc.apigateway.pylon.targets;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.Pair;
import poc.apigateway.pylon.configuration.BuildConfigurations;
import poc.apigateway.pylon.configuration.SchemeAndPortOverrider;
import poc.apigateway.pylon.configuration.dto.InitialConfigurationDto;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.model.Spec;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 최종 호출 URI를 만든다. 치환 우선순위는 hostOverride > schemeAndPort > jar 기본값이다.
 */
@Component
public class TargetUriFinder {

    private final Map<String, InitialConfigurationDto.Target> targetsByProvider = new HashMap<>();
    private final SchemeAndPortOverrider schemeAndPortOverrider;

    public TargetUriFinder(BuildConfigurations buildConfigurations,
                           SchemeAndPortOverrider schemeAndPortOverrider) {
        this.schemeAndPortOverrider = schemeAndPortOverrider;
        indexTargets(buildConfigurations.getInitialConfiguration());
    }

    private void indexTargets(InitialConfigurationDto initialConfiguration) {
        if (initialConfiguration == null || initialConfiguration.getConsumers() == null) {
            return;
        }
        for (InitialConfigurationDto.Consumer consumer : initialConfiguration.getConsumers().values()) {
            if (consumer.getRoutingPolicies() == null || consumer.getRoutingPolicies().getProviders() == null) {
                continue;
            }
            for (InitialConfigurationDto.ProviderPolicy policy : consumer.getRoutingPolicies().getProviders()) {
                if (policy.getRegions() == null || policy.getRegions().isEmpty()) {
                    continue;
                }
                InitialConfigurationDto.Region region = policy.getRegions().get(0);
                if (region.getTargets() == null || region.getTargets().isEmpty()) {
                    continue;
                }
                targetsByProvider.put(policy.getName(), region.getTargets().get(0));
            }
        }
    }

    public URI find(Spec spec, List<Pair> pathParams, List<Pair> queryParams) {
        String scheme;
        String host;
        int port;

        HostOverride hostOverride = spec.getHostOverride();
        if (hostOverride != null) {
            scheme = hostOverride.getScheme();
            host = hostOverride.getHost();
            port = hostOverride.getPort();
        } else {
            InitialConfigurationDto.Target target = targetsByProvider.get(spec.getProvider());
            if (target == null) {
                throw new IllegalStateException(
                    "no routing target for provider '" + spec.getProvider() + "' in initial_configuration.json");
            }
            host = target.getHost();
            scheme = schemeAndPortOverrider.schemeOf(spec.getProvider(), target.getScheme());
            port = schemeAndPortOverrider.portOf(spec.getProvider(), target.getPort());
        }

        String path = resolvePath(spec, pathParams);
        String query = toQueryString(queryParams);

        try {
            return new URI(scheme.toLowerCase() + "://" + host + ":" + port + path + query);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("failed to build URI for spec " + spec.getId(), e);
        }
    }

    private String resolvePath(Spec spec, List<Pair> pathParams) {
        String path = spec.getPath();
        for (Pair pair : pathParams) {
            path = path.replace("{" + pair.getName() + "}", pair.getValue());
        }
        int unresolved = path.indexOf('{');
        if (unresolved >= 0) {
            throw new IllegalStateException(
                "unresolved path variable in " + path + " for spec " + spec.getId());
        }
        return path;
    }

    private String toQueryString(List<Pair> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("?");
        for (int i = 0; i < queryParams.size(); i++) {
            if (i > 0) {
                builder.append('&');
            }
            Pair pair = queryParams.get(i);
            builder.append(pair.getName()).append('=').append(pair.getValue());
        }
        return builder.toString();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*TargetUriFinderTest'`
Expected: PASS, 6개 테스트

- [ ] **Step 5: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add TargetUriFinder with hostOverride > schemeAndPort > jar precedence"
```

---

## Task 9: RestTemplatePool — 함정 3(timeout 보정) 재현

**Files:**
- Create: `.../poc/apigateway/pylon/HttpClientConnectionManagerFactory.java`
- Create: `.../poc/apigateway/pylon/RestTemplatePool.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/RestTemplatePoolTest.java`

**Interfaces:**
- Consumes: Task 2의 `Spec`; Task 4의 `PylonConfiguration`
- Produces:
  - `HttpClientConnectionManagerFactory()` (`@Component`) — `HttpClientConnectionManager getOrCreate(String poolName, int poolSize)`. 같은 이름은 같은 인스턴스를 반환한다
  - `RestTemplatePool(PylonConfiguration, HttpClientConnectionManagerFactory)` (`@Component`)
    - `RestTemplate get(Spec spec)` — `(poolName, 보정된 readTimeout)` 키로 캐시
    - `int readTimeoutOf(Spec spec)` — `ceil(timeout/100)*100 + 100`. 단언용으로 공개한다
    - `int getConnectionTimeout()`
    - `static final int ROUND_TRIP_TIME = 100`

- [ ] **Step 1: 실패하는 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/RestTemplatePoolTest.java`:

```java
package poc.apigateway.pylon;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

class RestTemplatePoolTest {

    private RestTemplatePool pool(PylonConfiguration configuration) {
        return new RestTemplatePool(configuration, new HttpClientConnectionManagerFactory());
    }

    private Spec spec(String poolName, int timeout) {
        return Spec.builder("s-" + timeout, "order_api", "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(timeout)
            .setConnectionPool(new Spec.ConnectionPool(poolName, 20))
            .build();
    }

    @Test
    void rounds_the_timeout_up_to_100ms_and_adds_the_round_trip_allowance() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.readTimeoutOf(spec("shared", 1500))).isEqualTo(1600);
        assertThat(pool.readTimeoutOf(spec("shared", 1501))).isEqualTo(1700);
        assertThat(pool.readTimeoutOf(spec("shared", 3000))).isEqualTo(3100);
        assertThat(pool.readTimeoutOf(spec("shared", 1))).isEqualTo(200);
    }

    @Test
    void reuses_one_rest_template_per_pool_and_timeout() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        RestTemplate first = pool.get(spec("shared", 1500));
        RestTemplate second = pool.get(spec("shared", 1500));

        assertThat(first).isSameAs(second);
    }

    @Test
    void timeouts_that_round_to_the_same_bucket_share_one_rest_template() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.get(spec("shared", 1401))).isSameAs(pool.get(spec("shared", 1500)));
    }

    @Test
    void different_timeouts_get_different_rest_templates() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.get(spec("shared", 1500))).isNotSameAs(pool.get(spec("shared", 3000)));
    }

    @Test
    void different_pools_get_different_rest_templates() {
        RestTemplatePool pool = pool(new PylonConfiguration.Builder().build());

        assertThat(pool.get(spec("shared", 1500))).isNotSameAs(pool.get(spec("order_api", 1500)));
    }

    @Test
    void takes_the_connection_timeout_from_the_configuration() {
        assertThat(pool(new PylonConfiguration.Builder().build()).getConnectionTimeout()).isEqualTo(3000);
        assertThat(pool(new PylonConfiguration.Builder().connectionTimeout(500).build())
            .getConnectionTimeout()).isEqualTo(500);
    }

    @Test
    void connection_manager_factory_reuses_managers_by_name() {
        HttpClientConnectionManagerFactory factory = new HttpClientConnectionManagerFactory();

        assertThat(factory.getOrCreate("shared", 100)).isSameAs(factory.getOrCreate("shared", 100));
        assertThat(factory.getOrCreate("shared", 100)).isNotSameAs(factory.getOrCreate("order_api", 20));
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*RestTemplatePoolTest'`
Expected: 컴파일 실패 — `cannot find symbol RestTemplatePool`, `HttpClientConnectionManagerFactory`

- [ ] **Step 3: HttpClientConnectionManagerFactory 구현**

`poc/apigateway/pylon/HttpClientConnectionManagerFactory.java`:

```java
package poc.apigateway.pylon;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 풀 이름별로 커넥션 매니저를 하나만 만든다. */
@Component
public class HttpClientConnectionManagerFactory {

    private static final int VALIDATE_AFTER_INACTIVITY_MS = 100;

    private final ConcurrentMap<String, HttpClientConnectionManager> managers = new ConcurrentHashMap<>();

    public HttpClientConnectionManager getOrCreate(String poolName, int poolSize) {
        return managers.computeIfAbsent(poolName, name -> create(poolSize));
    }

    private HttpClientConnectionManager create(int poolSize) {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(poolSize);
        manager.setDefaultMaxPerRoute(poolSize);
        manager.setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY_MS);
        return manager;
    }
}
```

- [ ] **Step 4: RestTemplatePool 구현**

`poc/apigateway/pylon/RestTemplatePool.java`:

```java
package poc.apigateway.pylon;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RestTemplate 을 (풀 이름, 보정된 read timeout) 키로 캐시한다.
 *
 * 함정: timeout 은 100ms 단위로 올림된 뒤 왕복 여유 100ms 가 더해진다.
 * 즉 1500 을 설정하면 소켓에 걸리는 값은 1600 이다.
 */
@Component
public class RestTemplatePool {

    public static final int ROUND_TRIP_TIME = 100;

    private static final int BUCKET = 100;

    private final int connectionTimeout;
    private final HttpClientConnectionManagerFactory connectionManagerFactory;
    private final ConcurrentMap<String, RestTemplate> container = new ConcurrentHashMap<>();

    public RestTemplatePool(PylonConfiguration pylonConfiguration,
                            HttpClientConnectionManagerFactory connectionManagerFactory) {
        this.connectionTimeout = pylonConfiguration.getConnectionTimeout();
        this.connectionManagerFactory = connectionManagerFactory;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /** 실제로 소켓에 걸리는 read timeout. 테스트가 이 값을 단언한다. */
    public int readTimeoutOf(Spec spec) {
        return uplifting(spec.getTimeout()) + ROUND_TRIP_TIME;
    }

    public RestTemplate get(Spec spec) {
        Spec.ConnectionPool pool = spec.getConnectionPool();
        int readTimeout = readTimeoutOf(spec);
        String key = pool.getName() + "-" + readTimeout;

        return container.computeIfAbsent(key,
            ignored -> create(readTimeout, connectionManagerFactory.getOrCreate(pool.getName(), pool.getSize())));
    }

    private static int uplifting(int timeout) {
        return (int) (Math.ceil((double) timeout / BUCKET) * BUCKET);
    }

    private RestTemplate create(int readTimeout, HttpClientConnectionManager connectionManager) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
            HttpClientBuilder.create().setConnectionManager(connectionManager).build());
        factory.setConnectTimeout(connectionTimeout);
        factory.setConnectionRequestTimeout(connectionTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*RestTemplatePoolTest'`
Expected: PASS, 7개 테스트

- [ ] **Step 6: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add RestTemplatePool reproducing the 100ms uplift and round-trip allowance"
```

---

## Task 10: StubApiServer (testFixtures) + DynamicApiClient

**Files:**
- Create: `pylon-lite/src/testFixtures/java/poc/apigateway/pylon/testsupport/StubApiServer.java`
- Create: `.../poc/apigateway/pylon/DynamicApiClient.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/DynamicApiClientTest.java`

**Interfaces:**
- Consumes: Task 5의 `SpecResolver`; Task 8의 `TargetUriFinder`; Task 9의 `RestTemplatePool`; Task 2의 `RequestBase`, `ApiException`
- Produces:
  - `StubApiServer implements AutoCloseable` (testFixtures) — `static StubApiServer start()`, `int getPort()`, `String baseUrl()`, `void respond(String path, int status, String body)`, `void respondAfter(String path, long delayMillis, int status, String body)`, `List<String> receivedPaths()`, `void close()`
  - `DynamicApiClient(SpecResolver, RestTemplatePool, TargetUriFinder)` (`@Component`) — `<T> T invokeAPI(String specId, RequestBase request, Class<T> responseType)`

`invokeAPI` 는 4xx/5xx를 `ApiException(specId, status, body)` 로, `ResourceAccessException`(타임아웃·연결실패)을 `ApiException(specId, message, cause)` 로 바꾼다.

- [ ] **Step 1: StubApiServer 작성 (testFixtures — 프로덕션 코드가 아니므로 TDD 대상 아님)**

`pylon-lite/src/testFixtures/java/poc/apigateway/pylon/testsupport/StubApiServer.java`:

```java
package poc.apigateway.pylon.testsupport;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * JDK 내장 HttpServer 기반 스텁. 외부 의존성이 없다.
 * 포트 0으로 바인딩해 OS가 빈 포트를 주게 하고 getPort() 로 회수한다.
 */
public class StubApiServer implements AutoCloseable {

    private final HttpServer server;
    private final List<String> receivedPaths = new CopyOnWriteArrayList<>();

    private StubApiServer(HttpServer server) {
        this.server = server;
    }

    public static StubApiServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return new StubApiServer(server);
        } catch (IOException e) {
            throw new IllegalStateException("failed to start stub server", e);
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    public void respond(String path, int status, String body) {
        respondAfter(path, 0L, status, body);
    }

    public void respondAfter(String path, long delayMillis, int status, String body) {
        server.createContext(path, exchange -> {
            receivedPaths.add(exchange.getRequestURI().toString());
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
    }

    /** 수신한 요청의 path + query 목록. 순서는 도착 순이다. */
    public List<String> receivedPaths() {
        return Collections.unmodifiableList(receivedPaths);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
```

- [ ] **Step 2: DynamicApiClient 실패 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/DynamicApiClientTest.java`:

```java
package poc.apigateway.pylon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.configuration.BuildConfigurations;
import poc.apigateway.pylon.configuration.PylonConfiguration;
import poc.apigateway.pylon.configuration.SchemeAndPortOverrider;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.model.Spec;
import poc.apigateway.pylon.targets.TargetUriFinder;
import poc.apigateway.pylon.testsupport.StubApiServer;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicApiClientTest {

    private StubApiServer stub;

    private static class OrderRequest extends RequestBase {
        OrderRequest(String orderId) {
            addPathParam("orderId", orderId);
        }
    }

    public static class OrderPayload {
        private String orderId;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }
    }

    @BeforeEach
    void setUp() {
        stub = StubApiServer.start();
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private DynamicApiClient client(int specTimeout) {
        Spec spec = Spec.builder("spec-order-1", "order_api", "/api/v1/orders/{orderId}")
            .setMethod(HttpMethod.GET)
            .setTimeout(specTimeout)
            .setConnectionPool(new Spec.ConnectionPool("shared", 10))
            .setHostOverride(HostOverride.of("http", "127.0.0.1", stub.getPort()))
            .build();

        SpecResolver resolver =
            new SpecResolver(Collections.emptyList(), new Spec.ConnectionPool("shared", 10));
        resolver.register(spec);

        PylonConfiguration configuration = new PylonConfiguration.Builder().connectionTimeout(1000).build();
        RestTemplatePool pool =
            new RestTemplatePool(configuration, new HttpClientConnectionManagerFactory());

        // 실제 TargetUriFinder 를 쓴다. spec 에 hostOverride 가 있으므로 그것이 최우선으로 적용되어
        // fixture-initial.json 의 호스트를 무시하고 스텁으로 향한다.
        BuildConfigurations buildConfigurations = new BuildConfigurations(
            Collections.singletonList(() -> "fixture-provider.json"),
            () -> "fixture-initial.json",
            () -> "fixture-meta.json");
        TargetUriFinder uriFinder =
            new TargetUriFinder(buildConfigurations, new SchemeAndPortOverrider(configuration));

        return new DynamicApiClient(resolver, pool, uriFinder);
    }

    @Test
    void deserializes_a_successful_response() {
        stub.respond("/api/v1/orders/42", 200, "{\"orderId\":\"42\"}");

        OrderPayload payload = client(3000)
            .invokeAPI("spec-order-1", new OrderRequest("42"), OrderPayload.class);

        assertThat(payload.getOrderId()).isEqualTo("42");
        assertThat(stub.receivedPaths()).containsExactly("/api/v1/orders/42");
    }

    @Test
    void wraps_a_read_timeout_in_an_api_exception() {
        stub.respondAfter("/api/v1/orders/42", 900L, 200, "{\"orderId\":\"42\"}");

        assertThatThrownBy(() -> client(100)
            .invokeAPI("spec-order-1", new OrderRequest("42"), OrderPayload.class))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("spec-order-1");
    }

    @Test
    void wraps_a_server_error_with_its_status_code() {
        stub.respond("/api/v1/orders/42", 503, "{\"message\":\"down\"}");

        assertThatThrownBy(() -> client(3000)
            .invokeAPI("spec-order-1", new OrderRequest("42"), OrderPayload.class))
            .isInstanceOf(ApiException.class)
            .satisfies(thrown -> assertThat(((ApiException) thrown).getStatusCode()).isEqualTo(503));
    }

    @Test
    void fails_for_an_unregistered_spec_id() {
        assertThatThrownBy(() -> client(3000)
            .invokeAPI("nope", new OrderRequest("42"), OrderPayload.class))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("nope");
    }
}
```

`TargetUriFinder` 를 상속하려면 클래스와 `find` 가 `final` 이 아니어야 한다. Task 8의 구현이 그 조건을 만족한다.

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*DynamicApiClientTest'`
Expected: 컴파일 실패 — `cannot find symbol DynamicApiClient`

- [ ] **Step 4: 구현**

`poc/apigateway/pylon/DynamicApiClient.java`:

```java
package poc.apigateway.pylon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.model.Spec;
import poc.apigateway.pylon.targets.TargetUriFinder;

import java.net.URI;
import java.util.Map;

/**
 * 생성된 어댑터가 유일하게 의존하는 실행 지점.
 * 어댑터는 specId만 넘기고, 옵션은 전부 여기서 Spec을 통해 결정된다.
 */
@Component
public class DynamicApiClient {

    private static final Logger log = LoggerFactory.getLogger(DynamicApiClient.class);

    private final SpecResolver specResolver;
    private final RestTemplatePool restTemplatePool;
    private final TargetUriFinder targetUriFinder;

    public DynamicApiClient(SpecResolver specResolver,
                            RestTemplatePool restTemplatePool,
                            TargetUriFinder targetUriFinder) {
        this.specResolver = specResolver;
        this.restTemplatePool = restTemplatePool;
        this.targetUriFinder = targetUriFinder;
    }

    public <T> T invokeAPI(String specId, RequestBase request, Class<T> responseType) {
        Spec spec = specResolver.get(specId);
        URI uri = targetUriFinder.find(spec, request.getPathParams(), request.getQueryParams());
        RestTemplate restTemplate = restTemplatePool.get(spec);

        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> header : request.getHeaderParams().entrySet()) {
            headers.add(header.getKey(), header.getValue());
        }
        HttpEntity<Object> entity = new HttpEntity<>(request.getBody(), headers);

        log.debug("invoke {} {} readTimeout={}ms", spec.getMethod(), uri, restTemplatePool.readTimeoutOf(spec));

        try {
            ResponseEntity<T> response =
                restTemplate.exchange(uri, spec.getMethod(), entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new ApiException(specId, e.getRawStatusCode(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new ApiException(specId, "provider access failed: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*DynamicApiClientTest'`
Expected: PASS, 4개 테스트

- [ ] **Step 6: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: add DynamicApiClient and JDK-HttpServer based stub test fixture"
```

---

## Task 11: ApiGatewayAdapterConfig — 함정 1·2 재현

**Files:**
- Create: `.../poc/apigateway/pylon/configuration/ApiGatewayAdapterConfig.java`
- Create: `.../poc/apigateway/pylon/configuration/EnablePocApiGatewayAdapters.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/configuration/ApiGatewayAdapterConfigTest.java`
- Test: `pylon-lite/src/test/java/poc/apigateway/pylon/configuration/TimeoutCustomizerAssemblyTest.java`

**Interfaces:**
- Consumes: Task 3의 `BuildConfigurations`; Task 4의 `PylonConfiguration`; Task 5의 `SpecResolver`; Task 6의 커스터마이저 3종
- Produces:
  - `@EnablePocApiGatewayAdapters` — `@Import(ApiGatewayAdapterConfig.class)`
  - `ApiGatewayAdapterConfig` 빈들:
    - `PylonConfiguration defaultPylonConfiguration()` — **`@ConditionalOnMissingBean` 없음**
    - `TimeoutCustomizer timeoutCustomizer(PylonConfiguration)` — **per-spec 등록이 `defaultTimeout != null` 안에 갇혀 있음**
    - `ConnectionPoolCustomizer connectionPoolCustomizer(PylonConfiguration)`
    - `SpecResolver specResolver(List<SpecResolver.SpecCustomizer>, PylonConfiguration, BuildConfigurations)`

컴포넌트 스캔 기준: `PylonToolsMarker`, `ApiGatewayServiceMarker`, `ApiGatewayConfigurationMarker`. `@Component` 만 포함하고 `@Configuration` 은 제외한다 (실물과 동일).

- [ ] **Step 1: 조립 검증 테스트 작성 (테스트 전용 Locator + JSON 재사용)**

`pylon-lite/src/test/java/poc/apigateway/pylon/configuration/ApiGatewayAdapterConfigTest.java`:

```java
package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import poc.apigateway.pylon.DynamicApiClient;
import poc.apigateway.pylon.RestTemplatePool;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;
import poc.apigateway.pylon.specs.SpecResolver;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGatewayAdapterConfigTest {

    @Configuration
    @EnablePocApiGatewayAdapters
    static class FixtureLocators {
        @Bean
        SpecConfigurationLocator orderSpecs() {
            return () -> "fixture-provider.json";
        }

        @Bean
        InitialConfigurationLocator initial() {
            return () -> "fixture-initial.json";
        }

        @Bean
        GenerationMetaLocator meta() {
            return () -> "fixture-meta.json";
        }
    }

    @Configuration
    static class OverridingConfig {
        @Bean
        @Primary
        PylonConfiguration myPylonConfiguration() {
            return new PylonConfiguration.Builder()
                .connectionTimeout(777)
                .provider("order_api").defaultReadTimeout(1200).register()
                .build();
        }
    }

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(FixtureLocators.class);

    @Test
    void wires_the_runtime_and_registers_specs_from_the_jar() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DynamicApiClient.class);
            assertThat(context).hasSingleBean(SpecResolver.class);
            assertThat(context).hasSingleBean(BuildConfigurations.class);

            SpecResolver resolver = context.getBean(SpecResolver.class);
            assertThat(resolver.getProviderNames()).containsExactly("order_api");
            assertThat(resolver.get("spec-order-1").getTimeout()).isEqualTo(3000);
        });
    }

    @Test
    void a_primary_bean_replaces_the_library_default() {
        runner.withUserConfiguration(OverridingConfig.class).run(context -> {
            assertThat(context.getBean(PylonConfiguration.class).getConnectionTimeout()).isEqualTo(777);
            assertThat(context.getBean(RestTemplatePool.class).getConnectionTimeout()).isEqualTo(777);
            assertThat(context.getBean(SpecResolver.class).get("spec-order-1").getTimeout()).isEqualTo(1200);
        });
    }

    @Test
    void the_library_default_is_still_present_but_not_injected() {
        runner.withUserConfiguration(OverridingConfig.class).run(context -> {
            assertThat(context.getBeanNamesForType(PylonConfiguration.class))
                .contains("defaultPylonConfiguration", "myPylonConfiguration");
            assertThat(context.getBean("defaultPylonConfiguration", PylonConfiguration.class)
                .getConnectionTimeout()).isEqualTo(3000);
        });
    }
}
```

- [ ] **Step 2: 함정 2 검증 테스트 작성**

`pylon-lite/src/test/java/poc/apigateway/pylon/configuration/TimeoutCustomizerAssemblyTest.java`:

```java
package poc.apigateway.pylon.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import poc.apigateway.pylon.specs.customizer.TimeoutCustomizer;
import poc.apigateway.pylon.specs.model.Spec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 함정 2: ApiGatewayAdapterConfig 는 per-spec timeout 등록을
 * provider 기본값이 있을 때만 수행한다. 기본값 없이 per-spec 만 주면 조용히 무시된다.
 */
class TimeoutCustomizerAssemblyTest {

    private Spec spec() {
        return Spec.builder("spec-order-1", "order_api", "/api/v1/x")
            .setMethod(HttpMethod.GET)
            .setTimeout(3000)
            .setConnectionPool(new Spec.ConnectionPool("shared", 100))
            .build();
    }

    private TimeoutCustomizer assemble(PylonConfiguration configuration) {
        return new ApiGatewayAdapterConfig().timeoutCustomizer(configuration);
    }

    @Test
    void per_spec_timeout_is_silently_dropped_without_a_provider_default() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").readTimeoutPerSpec("spec-order-1", 1500).register()
            .build();

        assertThat(assemble(configuration).process(spec()).getTimeout())
            .as("provider 기본값이 없으면 per-spec 이 등록되지 않는다 — 이것이 재현하려는 함정이다")
            .isEqualTo(3000);
    }

    @Test
    void per_spec_timeout_applies_once_a_provider_default_is_present() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api")
                .defaultReadTimeout(1000)
                .readTimeoutPerSpec("spec-order-1", 1500)
                .register()
            .build();

        assertThat(assemble(configuration).process(spec()).getTimeout()).isEqualTo(1500);
    }

    @Test
    void provider_default_alone_applies() {
        PylonConfiguration configuration = new PylonConfiguration.Builder()
            .provider("order_api").defaultReadTimeout(1000).register()
            .build();

        assertThat(assemble(configuration).process(spec()).getTimeout()).isEqualTo(1000);
    }
}
```

- [ ] **Step 3: 두 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test --tests '*ApiGatewayAdapterConfigTest' --tests '*TimeoutCustomizerAssemblyTest'`
Expected: 컴파일 실패 — `cannot find symbol ApiGatewayAdapterConfig`, `EnablePocApiGatewayAdapters`

`ApplicationContextRunner` 는 `spring-boot-test` 에 있고 `spring-boot-starter-test` 로 들어온다. `pylon-lite` 의 테스트에서 쓰려면 그것만으로 충분하다 (Task 1의 루트 `build.gradle.kts` 가 모든 서브프로젝트에 추가한다).

- [ ] **Step 4: EnablePocApiGatewayAdapters 구현**

`poc/apigateway/pylon/configuration/EnablePocApiGatewayAdapters.java`:

```java
package poc.apigateway.pylon.configuration;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(ApiGatewayAdapterConfig.class)
public @interface EnablePocApiGatewayAdapters {
}
```

- [ ] **Step 5: ApiGatewayAdapterConfig 구현**

`poc/apigateway/pylon/configuration/ApiGatewayAdapterConfig.java`:

```java
package poc.apigateway.pylon.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import poc.apigateway.configuration.ApiGatewayConfigurationMarker;
import poc.apigateway.pylon.PylonToolsMarker;
import poc.apigateway.pylon.configuration.dto.ApiSpecificationConfigurationDto;
import poc.apigateway.pylon.configuration.dto.ProviderConfigurationDto;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.customizer.ConnectionPoolCustomizer;
import poc.apigateway.pylon.specs.customizer.TimeoutCustomizer;
import poc.apigateway.pylon.specs.model.Spec;
import poc.apigateway.services.ApiGatewayServiceMarker;

import java.util.List;
import java.util.Map;

/**
 * 런타임 조립의 중심. jar가 들고 온 값(BuildConfigurations)과
 * 외부 설정(PylonConfiguration)이 여기서 만난다.
 */
@Configuration
@ComponentScan(
    basePackageClasses = {
        PylonToolsMarker.class,
        ApiGatewayServiceMarker.class,
        ApiGatewayConfigurationMarker.class},
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Component.class),
    excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class))
@Import(ManualOverrideConfiguration.class)
public class ApiGatewayAdapterConfig {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayAdapterConfig.class);

    private static final int MAX_COMMON_POOL = 4000;
    private static final String COMMON_POOL_NAME = "pylon-common";

    /**
     * 주의: @ConditionalOnMissingBean 이 없다.
     * 클라이언트는 같은 이름으로 정의할 수 없고, 이름이 다른 @Primary 빈으로만 이길 수 있다.
     */
    @Bean
    public PylonConfiguration defaultPylonConfiguration() {
        return new PylonConfiguration.Builder().build();
    }

    /**
     * 주의: per-spec 등록이 provider 기본값 존재 여부 안쪽에 갇혀 있다.
     * defaultReadTimeout 없이 readTimeoutPerSpec 만 주면 조용히 무시된다.
     */
    @Bean
    public TimeoutCustomizer timeoutCustomizer(PylonConfiguration configuration) {
        TimeoutCustomizer customizer = new TimeoutCustomizer();
        for (PylonConfiguration.Provider provider : configuration.getProviders()) {
            if (provider.getDefaultTimeout() != null) {
                customizer.registerByProvider(provider.getName(), provider.getDefaultTimeout());
                for (Map.Entry<String, Integer> entry : provider.getReadTimeoutPerSpec().entrySet()) {
                    customizer.registerBySpec(entry.getKey(), entry.getValue());
                }
            }
        }
        return customizer;
    }

    @Bean
    public ConnectionPoolCustomizer connectionPoolCustomizer(PylonConfiguration configuration) {
        ConnectionPoolCustomizer customizer = new ConnectionPoolCustomizer();
        for (PylonConfiguration.Provider provider : configuration.getProviders()) {
            if (provider.getMaxConnection() != null) {
                customizer.register(provider.getName(), provider.getName(), provider.getMaxConnection());
            }
        }
        return customizer;
    }

    @Bean
    public SpecResolver specResolver(List<SpecResolver.SpecCustomizer> customizers,
                                     PylonConfiguration pylonConfiguration,
                                     BuildConfigurations buildConfigurations) {
        List<ProviderConfigurationDto> providers = buildConfigurations.getProviders();
        Spec.ConnectionPool commonPool =
            new Spec.ConnectionPool(COMMON_POOL_NAME, commonPoolSize(pylonConfiguration, providers.size()));
        log.info("Pylon common connection pool : {}", commonPool);

        SpecResolver resolver = new SpecResolver(customizers, commonPool);
        for (ProviderConfigurationDto provider : providers) {
            if (provider.getSpecifications() == null) {
                continue;
            }
            for (ApiSpecificationConfigurationDto specification : provider.getSpecifications()) {
                resolver.register(Spec.builder(
                        specification.getId(), provider.getName(), specification.getPath())
                    .setMethod(HttpMethod.resolve(specification.getMethod().toUpperCase()))
                    .setTimeout(specification.getTimeout() == null ? 0 : specification.getTimeout())
                    .setConnectionPool(commonPool)
                    .build());
            }
        }
        return resolver;
    }

    private int commonPoolSize(PylonConfiguration configuration, int providerCount) {
        if (configuration.getMaxConnection() != null) {
            return configuration.getMaxConnection();
        }
        return Math.min(providerCount * PylonConfiguration.DEFAULT_CONNECTION_PER_PROVIDER, MAX_COMMON_POOL);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :pylon-lite:test`
Expected: PASS, 전체 (Spec 4 + BuildConfigurations 4 + PylonConfiguration 4 + SpecResolver 7 + Customizer 14 + SchemeAndPort 3 + ManualOverride 6 + TargetUriFinder 6 + RestTemplatePool 7 + DynamicApiClient 4 + AdapterConfig 3 + TimeoutAssembly 3 = 65개)

- [ ] **Step 7: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/pylon-lite
git commit -m "feat: assemble pylon-lite runtime reproducing the missing-ConditionalOnMissingBean and per-spec traps"
```

---

## Task 12: api-gateway-consumer-role-poc — 어댑터 2개 + jar 리소스

**Files:**
- Create: `api-gateway-consumer-role-poc/src/main/java/poc/apigateway/configuration/ApiGatewayConsumerRolePocPylonCodeGeneratorVersion.java`
- Create: `.../configuration/ApiGatewayConsumerRolePocGenerationMetaLocator.java`
- Create: `.../configuration/ApiGatewayConsumerRolePocInitialConfigurationLocator.java`
- Create: `.../configuration/OrderApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java`
- Create: `.../configuration/ProductApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java`
- Create: `.../services/order_api/OrderapiApiV1OrdersAdapter.java`
- Create: `.../services/order_api/model/RequestParamOfGetApiV1OrdersOrderId.java`
- Create: `.../services/order_api/model/OrderDto.java`
- Create: `.../services/product_api/ProductapiApiV1ProductsAdapter.java`
- Create: `.../services/product_api/model/RequestParamOfGetApiV1ProductsProductId.java`
- Create: `.../services/product_api/model/ProductDto.java`
- Create: `api-gateway-consumer-role-poc/src/main/resources/generation-meta.json`
- Create: `.../resources/initial_configuration.json`
- Create: `.../resources/order_api_of_api-gateway-consumer-role-poc_configuration.json`
- Create: `.../resources/product_api_of_api-gateway-consumer-role-poc_configuration.json`
- Test: `api-gateway-consumer-role-poc/src/test/java/poc/apigateway/ConsumerRolePocWiringTest.java`

**Interfaces:**
- Consumes: Task 11의 `@EnablePocApiGatewayAdapters`; Task 10의 `DynamicApiClient`; Task 3의 Locator 인터페이스; Task 2의 `RequestBase`
- Produces:
  - specId `6512a0b1c2d3e4f500000001` = order (`GET /api/v1/orders/{orderId}`, jar timeout **3000**)
  - specId `6512a0b1c2d3e4f500000002` = product (`GET /api/v1/products/{productId}`, jar timeout **8000**)
  - `OrderapiApiV1OrdersAdapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId): OrderDto`
  - `ProductapiApiV1ProductsAdapter.getApiV1ProductsProductId(RequestParamOfGetApiV1ProductsProductId): ProductDto`
  - `RequestParamOfGetApiV1OrdersOrderId(String orderId)`
  - `RequestParamOfGetApiV1ProductsProductId(String productId)`
  - `OrderDto` — `orderId`, `status` (getter/setter)
  - `ProductDto` — `productId`, `name` (getter/setter)

specId 상수는 이 모듈 밖에서도 쓰인다 (Task 14·15의 yml·테스트). 문자열 그대로 복사할 것.

- [ ] **Step 1: 실패하는 테스트 작성**

`api-gateway-consumer-role-poc/src/test/java/poc/apigateway/ConsumerRolePocWiringTest.java`:

```java
package poc.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import poc.apigateway.pylon.configuration.EnablePocApiGatewayAdapters;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.services.order_api.OrderapiApiV1OrdersAdapter;
import poc.apigateway.services.product_api.ProductapiApiV1ProductsAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerRolePocWiringTest {

    static final String ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001";
    static final String PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002";

    @Configuration
    @EnablePocApiGatewayAdapters
    static class Enable {
    }

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(Enable.class);

    @Test
    void discovers_both_adapters_by_component_scan() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OrderapiApiV1OrdersAdapter.class);
            assertThat(context).hasSingleBean(ProductapiApiV1ProductsAdapter.class);
        });
    }

    @Test
    void registers_specs_with_the_timeouts_baked_into_the_jar() {
        runner.run(context -> {
            SpecResolver resolver = context.getBean(SpecResolver.class);

            assertThat(resolver.get(ORDER_SPEC_ID).getTimeout()).isEqualTo(3000);
            assertThat(resolver.get(PRODUCT_SPEC_ID).getTimeout())
                .as("product_api 는 일부러 다른 값을 갖는다 — provider 일괄 설정의 위험을 보여주기 위해")
                .isEqualTo(8000);
        });
    }

    @Test
    void registers_both_providers() {
        runner.run(context -> assertThat(context.getBean(SpecResolver.class).getProviderNames())
            .containsExactlyInAnyOrder("order_api", "product_api"));
    }

    @Test
    void exposes_the_jar_default_hosts() {
        runner.run(context -> {
            assertThat(context.getBean(SpecResolver.class).get(ORDER_SPEC_ID).getHostOverride())
                .as("설정 주입이 없으면 치환도 없다")
                .isNull();
        });
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :api-gateway-consumer-role-poc:test`
Expected: 컴파일 실패 — `package poc.apigateway.services.order_api does not exist`

- [ ] **Step 3: Locator·버전 클래스 구현**

`poc/apigateway/configuration/ApiGatewayConsumerRolePocPylonCodeGeneratorVersion.java`:

```java
package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.PylonCodeGeneratorVersion;

@Component
public class ApiGatewayConsumerRolePocPylonCodeGeneratorVersion implements PylonCodeGeneratorVersion {

    @Override
    public String getVersion() {
        return "0.1.0-POC";
    }

    @Override
    public int getCompatibilityLevel() {
        return 20190101;
    }
}
```

`poc/apigateway/configuration/ApiGatewayConsumerRolePocGenerationMetaLocator.java`:

```java
package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;

@Component
public class ApiGatewayConsumerRolePocGenerationMetaLocator implements GenerationMetaLocator {

    @Override
    public String getPath() {
        return "generation-meta.json";
    }
}
```

`poc/apigateway/configuration/ApiGatewayConsumerRolePocInitialConfigurationLocator.java`:

```java
package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;

@Component
public class ApiGatewayConsumerRolePocInitialConfigurationLocator implements InitialConfigurationLocator {

    @Override
    public String getPath() {
        return "initial_configuration.json";
    }
}
```

`poc/apigateway/configuration/OrderApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java`:

```java
package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

@Component
public class OrderApiOfApiGatewayConsumerRolePocSpecConfigurationLocator implements SpecConfigurationLocator {

    @Override
    public String getPath() {
        return "order_api_of_api-gateway-consumer-role-poc_configuration.json";
    }
}
```

`poc/apigateway/configuration/ProductApiOfApiGatewayConsumerRolePocSpecConfigurationLocator.java`:

```java
package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

@Component
public class ProductApiOfApiGatewayConsumerRolePocSpecConfigurationLocator implements SpecConfigurationLocator {

    @Override
    public String getPath() {
        return "product_api_of_api-gateway-consumer-role-poc_configuration.json";
    }
}
```

- [ ] **Step 4: order_api 어댑터·모델 구현**

`poc/apigateway/services/order_api/model/RequestParamOfGetApiV1OrdersOrderId.java`:

```java
package poc.apigateway.services.order_api.model;

import poc.apigateway.pylon.RequestBase;

public class RequestParamOfGetApiV1OrdersOrderId extends RequestBase {

    public RequestParamOfGetApiV1OrdersOrderId(String orderId) {
        addPathParam("orderId", orderId);
    }

    public RequestParamOfGetApiV1OrdersOrderId withVerbose(boolean verbose) {
        addQueryParam("verbose", verbose);
        return this;
    }
}
```

`poc/apigateway/services/order_api/model/OrderDto.java`:

```java
package poc.apigateway.services.order_api.model;

public class OrderDto {
    private String orderId;
    private String status;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
```

`poc/apigateway/services/order_api/OrderapiApiV1OrdersAdapter.java`:

```java
package poc.apigateway.services.order_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import poc.apigateway.pylon.DynamicApiClient;
import poc.apigateway.services.order_api.model.OrderDto;
import poc.apigateway.services.order_api.model.RequestParamOfGetApiV1OrdersOrderId;

/**
 * 생성 코드를 모방한다. 옵션을 받는 생성자 자리가 없다 —
 * timeout·풀·호스트는 전부 DynamicApiClient 아래에서 결정된다.
 */
@Component
public class OrderapiApiV1OrdersAdapter {

    private final DynamicApiClient apiClient;

    @Autowired
    public OrderapiApiV1OrdersAdapter(DynamicApiClient dynamicApiClient) {
        this.apiClient = dynamicApiClient;
    }

    /** API : order_api GET /api/v1/orders/{orderId} */
    public OrderDto getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId requestBase) {
        String specId = "6512a0b1c2d3e4f500000001";
        return apiClient.invokeAPI(specId, requestBase, OrderDto.class);
    }
}
```

- [ ] **Step 5: product_api 어댑터·모델 구현**

`poc/apigateway/services/product_api/model/RequestParamOfGetApiV1ProductsProductId.java`:

```java
package poc.apigateway.services.product_api.model;

import poc.apigateway.pylon.RequestBase;

public class RequestParamOfGetApiV1ProductsProductId extends RequestBase {

    public RequestParamOfGetApiV1ProductsProductId(String productId) {
        addPathParam("productId", productId);
    }
}
```

`poc/apigateway/services/product_api/model/ProductDto.java`:

```java
package poc.apigateway.services.product_api.model;

public class ProductDto {
    private String productId;
    private String name;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

`poc/apigateway/services/product_api/ProductapiApiV1ProductsAdapter.java`:

```java
package poc.apigateway.services.product_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import poc.apigateway.pylon.DynamicApiClient;
import poc.apigateway.services.product_api.model.ProductDto;
import poc.apigateway.services.product_api.model.RequestParamOfGetApiV1ProductsProductId;

@Component
public class ProductapiApiV1ProductsAdapter {

    private final DynamicApiClient apiClient;

    @Autowired
    public ProductapiApiV1ProductsAdapter(DynamicApiClient dynamicApiClient) {
        this.apiClient = dynamicApiClient;
    }

    /** API : product_api GET /api/v1/products/{productId} */
    public ProductDto getApiV1ProductsProductId(RequestParamOfGetApiV1ProductsProductId requestBase) {
        String specId = "6512a0b1c2d3e4f500000002";
        return apiClient.invokeAPI(specId, requestBase, ProductDto.class);
    }
}
```

- [ ] **Step 6: jar 리소스 4종 작성**

`src/main/resources/generation-meta.json`:

```json
{
  "profile" : "POC",
  "consumers" : [ "poc" ],
  "apiManagementHost" : "http://api-management.poc.internal"
}
```

`src/main/resources/initial_configuration.json`:

```json
{
  "consumers" : {
    "poc" : {
      "routingPolicies" : {
        "providers" : [ {
          "name" : "order_api",
          "regions" : [ {
            "name" : "TO_LOAD_BALANCER",
            "usage" : 100,
            "routingType" : "DIRECT",
            "targets" : [ {
              "scheme" : "HTTP",
              "host" : "order-api.poc.internal",
              "port" : 80
            } ]
          } ]
        }, {
          "name" : "product_api",
          "regions" : [ {
            "name" : "TO_LOAD_BALANCER",
            "usage" : 100,
            "routingType" : "DIRECT",
            "targets" : [ {
              "scheme" : "HTTP",
              "host" : "product-api.poc.internal",
              "port" : 80
            } ]
          } ]
        } ]
      }
    }
  }
}
```

`src/main/resources/order_api_of_api-gateway-consumer-role-poc_configuration.json`:

```json
{
  "name" : "order_api",
  "specifications" : [ {
    "id" : "6512a0b1c2d3e4f500000001",
    "revision" : "6512a0b1c2d3e4f5000000a1",
    "type" : "SINGLE",
    "path" : "/api/v1/orders/{orderId}",
    "method" : "get",
    "produces" : [ "application/json" ],
    "consumes" : [ ],
    "timeout" : 3000
  } ]
}
```

`src/main/resources/product_api_of_api-gateway-consumer-role-poc_configuration.json`:

```json
{
  "name" : "product_api",
  "specifications" : [ {
    "id" : "6512a0b1c2d3e4f500000002",
    "revision" : "6512a0b1c2d3e4f5000000a2",
    "type" : "SINGLE",
    "path" : "/api/v1/products/{productId}",
    "method" : "get",
    "produces" : [ "application/json" ],
    "consumes" : [ ],
    "timeout" : 8000
  } ]
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :api-gateway-consumer-role-poc:test`
Expected: PASS, 4개 테스트

- [ ] **Step 8: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/api-gateway-consumer-role-poc
git commit -m "feat: add mock consumer-role module with order and product adapters"
```

---

## Task 13: client-config — PylonClientProperty + PylonClientConfig

**Files:**
- Create: `client-config/src/main/kotlin/poc/client/PocClientApplication.kt`
- Create: `client-config/src/main/kotlin/poc/client/config/PylonClientProperty.kt`
- Create: `client-config/src/main/kotlin/poc/client/config/PylonClientConfig.kt`
- Create: `client-config/src/main/resources/application.yml`
- Test: `client-config/src/test/kotlin/poc/client/config/PrimaryOverrideTest.kt`
- Test: `client-config/src/test/kotlin/poc/client/config/UnknownProviderFailFastTest.kt`

**Interfaces:**
- Consumes: Task 4의 `PylonConfiguration`; Task 3의 `BuildConfigurations`; Task 11의 `@EnablePocApiGatewayAdapters`
- Produces:
  - `PocClientApplication` — `@SpringBootConfiguration` + `@EnablePocApiGatewayAdapters` + `@ConfigurationPropertiesScan`. `main()` 은 없다
  - `PylonClientProperty` — `@ConstructorBinding @ConfigurationProperties("pylon.client")`. `connectTimeout: Int = 3000`, `maxConnection: Int? = null`, `routingInfoDuration: Int = 60000`, `providers: Map<String, Provider> = emptyMap()`
  - `PylonClientProperty.Provider` — `readTimeout: Int`(필수), `maxConnection: Int? = null`, `readTimeoutPerSpec: Map<String, Int> = emptyMap()`, `scheme: String? = null`, `port: Int? = null`, 파생 `schemeAndPortOverridden: Boolean`
  - `PylonClientConfig` — `@Bean @Primary fun pylonConfiguration(property, buildConfigurations): PylonConfiguration`

`verifyTargetsExist` 는 `buildConfigurations.providers` 를 직접 읽는다 (실제 pylon은 `gradlePluginGeneratingDtoLoader` 를 한 겹 더 거친다 — POC와 다른 유일한 줄).

- [ ] **Step 1: 실패하는 테스트 2개 작성**

`client-config/src/test/kotlin/poc/client/config/PrimaryOverrideTest.kt`:

```kotlin
package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.SpecResolver

@SpringBootTest(
    properties = [
        "pylon.client.connect-timeout=777",
        "pylon.client.providers.[order_api].read-timeout=1200",
    ]
)
class PrimaryOverrideTest(
    private val context: ApplicationContext,
    private val configuration: PylonConfiguration,
    private val restTemplatePool: RestTemplatePool,
    private val specResolver: SpecResolver,
) {

    @Test
    fun `the primary bean is the one injected everywhere`() {
        assertThat(configuration.connectionTimeout).isEqualTo(777)
        assertThat(restTemplatePool.connectionTimeout).isEqualTo(777)
    }

    @Test
    fun `the library default bean still exists but loses`() {
        assertThat(context.getBeanNamesForType(PylonConfiguration::class.java))
            .contains("defaultPylonConfiguration", "pylonConfiguration")

        val libraryDefault = context.getBean<PylonConfiguration>("defaultPylonConfiguration")
        assertThat(libraryDefault.connectionTimeout).isEqualTo(3000)
    }

    @Test
    fun `the injected option reaches the spec built from the jar`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout)
            .`as`("jar 값 3000 이 주입값 1200 으로 대체된다")
            .isEqualTo(1200)
    }

    @Test
    fun `an unconfigured provider keeps the jar value`() {
        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout).isEqualTo(8000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
```

`client-config/src/test/kotlin/poc/client/config/UnknownProviderFailFastTest.kt`:

```kotlin
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
        .withPropertyValues("spring.main.allow-bean-definition-overriding=false")

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
```

`ApplicationContextRunner` 에서 `@ConfigurationProperties` 바인딩이 동작하려면 `ConfigurationPropertiesAutoConfiguration` 을 함께 등록해야 한다. `@ConfigurationPropertiesScan` 은 러너에서 동작하지 않으므로, `PylonClientConfig` 에 `@EnableConfigurationProperties(PylonClientProperty::class)` 를 붙여 두 경로 모두에서 바인딩되게 한다 (Step 4 참조).

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :client-config:test`
Expected: 컴파일 실패 — `unresolved reference: PylonClientConfig`, `PylonClientProperty`

- [ ] **Step 3: PocClientApplication 작성**

`client-config/src/main/kotlin/poc/client/PocClientApplication.kt`:

```kotlin
package poc.client

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import poc.apigateway.pylon.configuration.EnablePocApiGatewayAdapters

/**
 * @SpringBootTest 가 찾는 설정 루트. main() 은 없다 — POC에 실행 가능한 앱은 없다.
 */
@SpringBootConfiguration
@EnablePocApiGatewayAdapters
@ConfigurationPropertiesScan
class PocClientApplication
```

- [ ] **Step 4: PylonClientProperty 작성**

`client-config/src/main/kotlin/poc/client/config/PylonClientProperty.kt`:

```kotlin
package poc.client.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * API-GW(pylon) HTTP Client 옵션.
 *
 * 생성된 어댑터는 옵션을 받는 자리가 없다. 어댑터는 specId 를 들고 공용
 * DynamicApiClient 에 위임하므로, 옵션은 그 아래 Spec / RestTemplatePool 레이어에 준다.
 * 그 입구가 PylonConfiguration 이고 이 클래스가 그것을 프로파일별 yml 로 뽑아낸 것이다.
 *
 * providers 를 비워두면 라이브러리 기본 동작과 동일하다.
 */
@ConstructorBinding
@ConfigurationProperties("pylon.client")
data class PylonClientProperty(

    /** 전역 connect timeout(ms). provider 단위로는 줄 수 없다. */
    val connectTimeout: Int = DEFAULT_CONNECT_TIMEOUT,

    /** 전역 공용 커넥션 풀 크기. null 이면 min(provider 수 * 500, 4000). */
    val maxConnection: Int? = null,

    /** 라우팅 정보 갱신 주기(ms). */
    val routingInfoDuration: Int = DEFAULT_ROUTING_INFO_DURATION,

    /** key = pylon provider 명(예: order_api). map key 는 대괄호 표기가 필요하다. */
    val providers: Map<String, Provider> = emptyMap(),
) {

    data class Provider(
        /**
         * provider 의 모든 spec 에 적용되는 read timeout(ms).
         *
         * 필수다. 라이브러리 TimeoutCustomizer 조립부가 per-spec 등록을
         * `defaultTimeout != null` 블록 안에서 하기 때문에, 이 값이 없으면
         * readTimeoutPerSpec 이 조용히 무시된다.
         *
         * 주의: 이 값은 생성된 스펙의 timeout 을 provider 전체에 대해 덮어쓴다.
         * 오래 걸리는 spec 이 섞인 provider 라면 readTimeoutPerSpec 으로 되살려야 한다.
         */
        val readTimeout: Int,

        /** provider 전용 커넥션 풀 크기. null 이면 전역 공용 풀을 쓴다. */
        val maxConnection: Int? = null,

        /** key = specId. readTimeout 보다 우선한다. */
        val readTimeoutPerSpec: Map<String, Int> = emptyMap(),

        /** 환경별 scheme/port 치환. 둘 다 있어야 적용된다. host 는 건드리지 않는다. */
        val scheme: String? = null,
        val port: Int? = null,
    ) {
        val schemeAndPortOverridden: Boolean
            get() = scheme != null && port != null
    }

    companion object {
        /** PylonConfiguration.Builder 기본값과 동일. */
        const val DEFAULT_CONNECT_TIMEOUT = 3_000

        /** PylonConfiguration.Builder 기본값과 동일. */
        const val DEFAULT_ROUTING_INFO_DURATION = 60_000
    }
}
```

- [ ] **Step 5: PylonClientConfig 작성**

`client-config/src/main/kotlin/poc/client/config/PylonClientConfig.kt`:

```kotlin
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
                    "pylon.client.providers 에 없는 provider: '$name'. " +
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
```

- [ ] **Step 6: 기본 application.yml 작성**

`client-config/src/main/resources/application.yml`:

```yaml
# API-GW(pylon) HTTP Client 옵션 - poc.client.config.PylonClientConfig
# providers 를 비워두면 생성된 스펙의 timeout 을 그대로 쓴다(= 라이브러리 기본 동작).
# 환경별로 다르게 하려면 application-{local,production}.yml 에서 덮어쓴다.
# provider 명에 '_' 가 있어 map key 는 대괄호 표기가 필요하다.
pylon:
  client:
    connect-timeout: 3000

logging:
  level:
    poc.client.config: INFO
    poc.apigateway.pylon: INFO
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :client-config:test --tests '*PrimaryOverrideTest' --tests '*UnknownProviderFailFastTest'`
Expected: PASS, 7개 테스트 (PrimaryOverride 4 + UnknownProviderFailFast 3)

`@SpringBootTest` 에서 생성자 주입을 쓰려면 JUnit 5 + Spring 5.3 미만에서도 동작한다 (`SpringExtension` 이 파라미터 리졸버를 제공). Kotlin 클래스 생성자 파라미터로 주입받는 방식이 그것이다.

- [ ] **Step 8: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/client-config
git commit -m "feat: add client-config primary PylonConfiguration bean with fail-fast validation"
```

---

## Task 14: 프로파일별 yml + 환경차 검증

**Files:**
- Create: `client-config/src/main/resources/application-local.yml`
- Create: `client-config/src/main/resources/application-production.yml`
- Test: `client-config/src/test/kotlin/poc/client/config/ProviderReadTimeoutTest.kt`
- Test: `client-config/src/test/kotlin/poc/client/config/PerSpecBeatsProviderTest.kt`

**Interfaces:**
- Consumes: Task 13의 `PocClientApplication`, `PylonClientConfig`; Task 12의 specId 상수
- Produces: 프로파일 `local`(read 1000, connect 500, pool 20)과 `production`(read 3000, connect 2000, per-spec order=1500)

- [ ] **Step 1: 실패하는 테스트 2개 작성**

`client-config/src/test/kotlin/poc/client/config/ProviderReadTimeoutTest.kt`:

```kotlin
package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.SpecResolver

@SpringBootTest
@ActiveProfiles("local")
class ProviderReadTimeoutTest(
    private val configuration: PylonConfiguration,
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {

    @Test
    fun `local profile shortens the order_api timeout`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout).isEqualTo(1000)
    }

    @Test
    fun `local profile shortens the connect timeout`() {
        assertThat(configuration.connectionTimeout).isEqualTo(500)
        assertThat(restTemplatePool.connectionTimeout).isEqualTo(500)
    }

    @Test
    fun `local profile gives order_api its own connection pool`() {
        val pool = specResolver.get(ORDER_SPEC_ID).connectionPool

        assertThat(pool.name).isEqualTo("order_api")
        assertThat(pool.size).isEqualTo(20)
    }

    @Test
    fun `product_api is untouched and keeps the shared pool and the jar timeout`() {
        val spec = specResolver.get(PRODUCT_SPEC_ID)

        assertThat(spec.timeout).isEqualTo(8000)
        assertThat(spec.connectionPool.name).isEqualTo("pylon-common")
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
```

`client-config/src/test/kotlin/poc/client/config/PerSpecBeatsProviderTest.kt`:

```kotlin
package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.configuration.PylonConfiguration
import poc.apigateway.pylon.specs.SpecResolver

@SpringBootTest
@ActiveProfiles("production")
class PerSpecBeatsProviderTest(
    private val configuration: PylonConfiguration,
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {

    @Test
    fun `the per-spec value wins over the provider default`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout)
            .`as`("provider 기본 3000 이 아니라 per-spec 1500 이어야 한다")
            .isEqualTo(1500)
    }

    @Test
    fun `the effective socket timeout adds the round trip allowance`() {
        assertThat(restTemplatePool.readTimeoutOf(specResolver.get(ORDER_SPEC_ID)))
            .`as`("ceil(1500/100)*100 + 100")
            .isEqualTo(1600)
    }

    @Test
    fun `production profile sets the connect timeout`() {
        assertThat(configuration.connectionTimeout).isEqualTo(2000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd poc-injection-external-config && ./gradlew :client-config:test --tests '*ProviderReadTimeoutTest' --tests '*PerSpecBeatsProviderTest'`
Expected: FAIL — 프로파일 yml이 없으므로 timeout이 jar 값(3000)에 머문다. `expected 1000 but was 3000` 류.

- [ ] **Step 3: application-local.yml 작성**

`client-config/src/main/resources/application-local.yml`:

```yaml
# 로컬: 짧은 timeout으로 빨리 실패하게 하고, order_api 에 전용 풀을 준다.
pylon:
  client:
    connect-timeout: 500
    providers:
      "[order_api]":
        read-timeout: 1000
        max-connection: 20

logging:
  level:
    poc.client.config: DEBUG
    poc.apigateway.pylon: DEBUG
```

- [ ] **Step 4: application-production.yml 작성**

`client-config/src/main/resources/application-production.yml`:

```yaml
# 프로덕션: provider 기본은 넉넉하게, 실시간 조회 스펙만 짧게 잡는다.
# read-timeout 없이 read-timeout-per-spec 만 주면 조용히 무시된다는 점을 주의.
pylon:
  client:
    connect-timeout: 2000
    providers:
      "[order_api]":
        read-timeout: 3000
        read-timeout-per-spec:
          "[6512a0b1c2d3e4f500000001]": 1500

logging:
  level:
    poc.client.config: INFO
    poc.apigateway.pylon: WARN
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd poc-injection-external-config && ./gradlew :client-config:test --tests '*ProviderReadTimeoutTest' --tests '*PerSpecBeatsProviderTest'`
Expected: PASS, 7개 테스트 (ProviderReadTimeout 4 + PerSpecBeatsProvider 3)

- [ ] **Step 6: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/client-config
git commit -m "feat: add local and production profiles proving per-environment client options"
```

---

## Task 15: 위험 문서화 + 소켓 도달 + host 치환

**Files:**
- Test: `client-config/src/test/kotlin/poc/client/config/ProviderWideClobberTest.kt`
- Test: `client-config/src/test/kotlin/poc/client/config/ReadTimeoutReachesSocketTest.kt`
- Test: `client-config/src/test/kotlin/poc/client/config/ManualOverrideHostTest.kt`

**Interfaces:**
- Consumes: Task 13의 설정; Task 12의 어댑터·specId; Task 10의 `StubApiServer`
- Produces: 새 프로덕션 코드 없음. 이 태스크는 **순전히 검증**이다 — 앞선 태스크들이 만든 동작을 못박고, 하나는 위험을 문서화한다.

이 3개는 프로파일 yml에 없는 조합을 다루므로 `@SpringBootTest(properties = [...])` 로 인라인 지정한다.

- [ ] **Step 1: ProviderWideClobberTest 작성 (위험 문서화)**

`client-config/src/test/kotlin/poc/client/config/ProviderWideClobberTest.kt`:

```kotlin
package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import poc.apigateway.pylon.specs.SpecResolver

/**
 * 이 테스트는 버그를 잡는 것이 아니라 **위험을 문서화한다.**
 *
 * product_api 의 스펙은 jar 에서 8000ms 로 왔다. provider 단위 read-timeout 을 주면
 * 그 8000 이 조용히 짓밟힌다. 오래 걸리는 spec 이 섞인 provider 에 일괄 설정을
 * 하면 안 된다는 것을 여기서 못박는다.
 */
@SpringBootTest(
    properties = [
        "pylon.client.providers.[product_api].read-timeout=1000",
    ]
)
class ProviderWideClobberTest(private val specResolver: SpecResolver) {

    @Test
    fun `a provider-wide read timeout clobbers a long jar timeout`() {
        assertThat(specResolver.get(PRODUCT_SPEC_ID).timeout)
            .`as`("jar 의 8000 이 provider 일괄 설정 1000 으로 대체된다 — 의도한 위험")
            .isEqualTo(1000)
    }

    @Test
    fun `the untouched provider keeps its jar timeout`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).timeout)
            .`as`("짓밟기는 설정한 provider 에만 일어난다")
            .isEqualTo(3000)
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"
    }
}
```

복구 경로는 프로퍼티가 다른 별도 컨텍스트가 필요하므로 같은 파일에 두 번째 최상위 클래스로 넣는다:

```kotlin
@SpringBootTest(
    properties = [
        "pylon.client.providers.[product_api].read-timeout=1000",
        "pylon.client.providers.[product_api].read-timeout-per-spec.[6512a0b1c2d3e4f500000002]=8000",
    ]
)
class ProviderWideClobberRestoredTest(private val specResolver: SpecResolver) {

    @Test
    fun `an explicit per-spec entry restores the long timeout`() {
        assertThat(specResolver.get("6512a0b1c2d3e4f500000002").timeout).isEqualTo(8000)
    }
}
```

- [ ] **Step 2: ReadTimeoutReachesSocketTest 작성**

`client-config/src/test/kotlin/poc/client/config/ReadTimeoutReachesSocketTest.kt`:

```kotlin
package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import poc.apigateway.pylon.ApiException
import poc.apigateway.pylon.RestTemplatePool
import poc.apigateway.pylon.specs.SpecResolver
import poc.apigateway.pylon.testsupport.StubApiServer
import poc.apigateway.services.order_api.OrderapiApiV1OrdersAdapter
import poc.apigateway.services.order_api.model.RequestParamOfGetApiV1OrdersOrderId

/**
 * 주입된 timeout 이 실제 소켓까지 도달하는지 증명한다.
 * order_api 를 스텁 서버로 돌려놓고 지연을 조절해 양쪽 경계를 본다.
 */
@SpringBootTest(
    properties = [
        "pylon.client.connect-timeout=1000",
        "pylon.client.providers.[order_api].read-timeout=400",
    ]
)
class ReadTimeoutReachesSocketTest(
    private val adapter: OrderapiApiV1OrdersAdapter,
    private val specResolver: SpecResolver,
    private val restTemplatePool: RestTemplatePool,
) {

    @Test
    fun `the effective socket timeout is the uplifted value`() {
        assertThat(restTemplatePool.readTimeoutOf(specResolver.get(ORDER_SPEC_ID)))
            .`as`("ceil(400/100)*100 + 100")
            .isEqualTo(500)
    }

    @Test
    fun `a response inside the timeout succeeds`() {
        stub.respond("/api/v1/orders/fast", 200, """{"orderId":"fast","status":"OK"}""")

        assertThatCode {
            val order = adapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId("fast"))
            assertThat(order.orderId).isEqualTo("fast")
            assertThat(order.status).isEqualTo("OK")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a response past the timeout raises ApiException`() {
        stub.respondAfter("/api/v1/orders/slow", 1500L, 200, """{"orderId":"slow"}""")

        assertThatThrownBy { adapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId("slow")) }
            .isInstanceOf(ApiException::class.java)
            .hasMessageContaining(ORDER_SPEC_ID)
    }

    @Test
    fun `the request actually reached the stub`() {
        stub.respond("/api/v1/orders/seen", 200, """{"orderId":"seen"}""")

        adapter.getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId("seen"))

        assertThat(stub.receivedPaths()).contains("/api/v1/orders/seen")
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"

        private val stub: StubApiServer = StubApiServer.start()

        @JvmStatic
        @DynamicPropertySource
        fun redirectOrderApi(registry: DynamicPropertyRegistry) {
            registry.add("api_gateway.manual_override.provider.order_api.server") { stub.baseUrl() }
        }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            stub.close()
        }
    }
}
```

`@DynamicPropertySource` 는 Spring 5.2.5+ 에 있고 Boot 2.3.4가 Spring 5.2.9를 쓰므로 사용 가능하다. 스텁을 `companion object` 에서 시작하는 이유는 프로퍼티 등록이 컨텍스트 생성 **전에** 일어나야 하기 때문이다.

- [ ] **Step 3: ManualOverrideHostTest 작성**

`client-config/src/test/kotlin/poc/client/config/ManualOverrideHostTest.kt`:

```kotlin
package poc.client.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import poc.apigateway.pylon.specs.SpecResolver
import poc.apigateway.pylon.testsupport.StubApiServer
import poc.apigateway.services.product_api.ProductapiApiV1ProductsAdapter
import poc.apigateway.services.product_api.model.RequestParamOfGetApiV1ProductsProductId

/**
 * 두 번째 주입 경로: 타입 빈이 아니라 프로퍼티 스캔.
 * jar 의 product-api.poc.internal 을 스텁으로 갈아치운다. 코드 변경은 0줄이다.
 */
@SpringBootTest
class ManualOverrideHostTest(
    private val adapter: ProductapiApiV1ProductsAdapter,
    private val specResolver: SpecResolver,
) {

    @Test
    fun `the property scan attaches a host override to the spec`() {
        val override = specResolver.get(PRODUCT_SPEC_ID).hostOverride

        assertThat(override).isNotNull
        assertThat(override.host).isEqualTo("127.0.0.1")
        assertThat(override.port).isEqualTo(stub.getPort())
    }

    @Test
    fun `the call lands on the stub instead of the jar host`() {
        stub.respond("/api/v1/products/p-1", 200, """{"productId":"p-1","name":"POC"}""")

        val product = adapter.getApiV1ProductsProductId(RequestParamOfGetApiV1ProductsProductId("p-1"))

        assertThat(product.productId).isEqualTo("p-1")
        assertThat(product.name).isEqualTo("POC")
        assertThat(stub.receivedPaths()).contains("/api/v1/products/p-1")
    }

    @Test
    fun `order_api is unaffected and keeps the jar host`() {
        assertThat(specResolver.get(ORDER_SPEC_ID).hostOverride).isNull()
    }

    companion object {
        const val ORDER_SPEC_ID = "6512a0b1c2d3e4f500000001"
        const val PRODUCT_SPEC_ID = "6512a0b1c2d3e4f500000002"

        private val stub: StubApiServer = StubApiServer.start()

        @JvmStatic
        @DynamicPropertySource
        fun redirectProductApi(registry: DynamicPropertyRegistry) {
            registry.add("api_gateway.manual_override.provider.product_api.server") { stub.baseUrl() }
            registry.add("api_gateway.manual_override.version") { "1" }
        }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            stub.close()
        }
    }
}
```

- [ ] **Step 4: 세 테스트 실행**

Run: `cd poc-injection-external-config && ./gradlew :client-config:test`
Expected: PASS, 전체 (PrimaryOverride 4 + UnknownProviderFailFast 3 + ProviderReadTimeout 4 + PerSpecBeatsProvider 3 + ProviderWideClobber 2 + ProviderWideClobberRestored 1 + ReadTimeoutReachesSocket 4 + ManualOverrideHost 3 = 24개)

실패하면 프로덕션 코드를 고치지 말고 먼저 원인을 규명한다. 이 태스크는 새 기능을 추가하지 않으므로, 실패는 앞선 태스크의 결함이거나 테스트 자체의 오류다.

- [ ] **Step 5: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/client-config
git commit -m "test: prove socket-level timeout, host override and document the provider-wide clobber hazard"
```

---

## Task 16: README + 전체 빌드 검증

**Files:**
- Create: `poc-injection-external-config/README.md`

**Interfaces:**
- Consumes: Task 1~15 전부
- Produces: 사용자용 문서. 새 코드 없음.

- [ ] **Step 1: 전체 빌드가 통과하는지 먼저 확인**

Run: `cd poc-injection-external-config && ./gradlew clean build`
Expected: `BUILD SUCCESSFUL`. 테스트 총 93개 (pylon-lite 65 + consumer-role-poc 4 + client-config 24).

숫자가 다르면 README에 적기 전에 실제 값을 확인한다. 추정치를 문서에 적지 말 것.

- [ ] **Step 2: README 작성**

`poc-injection-external-config/README.md`:

````markdown
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
    version: 1
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

provider 기본 timeout 없이 per-spec만 주면 **조용히 무시된다.** `client-config` 는 `readTimeout` 을 필수 파라미터로 만들어 이 함정을 컴파일 타임으로 끌어올린다.

→ `TimeoutCustomizerAssemblyTest`, `PerSpecBeatsProviderTest`

### 함정 3 — timeout 보정

실효 read timeout = `ceil(t/100)*100 + 100`. **1500 설정 → 실제 1600ms.**

→ `RestTemplatePoolTest`, `ReadTimeoutReachesSocketTest`

## 문서화한 위험 — provider 일괄 설정

`order_api` 스펙은 jar에서 3000ms, `product_api` 는 8000ms로 온다. 일부러 다르게 뒀다.

provider 단위 `read-timeout: 1000` 을 주면 **`product_api` 의 8000이 조용히 짓밟힌다.** 실제 jar에서도 스펙별 timeout은 1000~80000ms로 넓게 퍼져 있으므로, 오래 걸리는 스펙이 섞인 provider에 일괄 설정을 하면 안 된다.

→ `ProviderWideClobberTest` (위험 재현) / `ProviderWideClobberRestoredTest` (per-spec으로 복구)

## 실행

```bash
./gradlew test          # 전체 테스트
./gradlew build         # 컴파일 + 테스트
```

프로파일별 동작을 보려면:

```bash
./gradlew :client-config:test --tests '*ProviderReadTimeoutTest'   # local  프로파일
./gradlew :client-config:test --tests '*PerSpecBeatsProviderTest'  # production 프로파일
```

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

POC가 버린 것: 인증 토큰, 요청 서명, rate limit, precondition, 라우팅 정책 원격 갱신, API 시뮬레이션, dry-run, 로그 포매터, Fluent API, WebClient/OkHttp3 확장.

`PylonConfiguration` / `SpecResolver` / `SpecCustomizer` / `TimeoutCustomizer` / `ConnectionPoolCustomizer` / `RestTemplatePool` 의 이름과 흐름은 실물과 1:1이다.
````

- [ ] **Step 3: README의 테스트 개수를 실측값으로 교정**

Run: `cd poc-injection-external-config && ./gradlew clean build 2>&1 | tail -20`

Step 1에서 얻은 실제 테스트 개수와 README·플랜의 기대값이 다르면 README를 실측값으로 고친다. 문서에 추정치를 남기지 않는다.

- [ ] **Step 4: 독립성 검증 — 루트 빌드에 섞이지 않았는지 확인**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
grep -n "poc-injection" settings.gradle.kts || echo "OK: 루트 빌드와 분리되어 있다"
./gradlew projects --offline -q 2>&1 | grep -i poc || echo "OK: 루트 프로젝트 목록에 없다"
```

Expected: 두 줄 모두 `OK:` 출력

- [ ] **Step 5: 이식성 검증 — 다른 위치로 복사해도 빌드되는지 확인**

```bash
rm -rf /private/tmp/claude-501/-Users-ncrash-IdeaProjects-mycoupang-tiny-module/*/scratchpad/poc-portability
mkdir -p /tmp/poc-portability
cp -R poc-injection-external-config /tmp/poc-portability/
cd /tmp/poc-portability/poc-injection-external-config && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`. 이것이 "완전히 무관한 신규 프로젝트로 옮겨서 사용" 요구의 실제 검증이다. 성공하면 `/tmp/poc-portability` 를 지운다.

- [ ] **Step 6: 커밋**

```bash
cd /Users/ncrash/IdeaProjects/mycoupang-tiny-module
git add poc-injection-external-config/README.md
git commit -m "docs: add poc-injection-external-config README"
```

---

## 완료 기준

- [ ] `cd poc-injection-external-config && ./gradlew clean build` 가 통과한다
- [ ] `/tmp` 로 복사한 사본도 독립적으로 빌드된다 (Task 16 Step 5)
- [ ] 루트 `settings.gradle.kts` 에 `poc-injection-external-config` 가 없다
- [ ] `client-config` 의 커밋 diff가 `pylon-lite` / `api-gateway-consumer-role-poc` 를 건드리지 않는다
- [ ] 재현한 함정 3개가 각각 테스트로 잠겨 있다
- [ ] README의 테스트 개수가 실측값이다
