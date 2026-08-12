package poc.apigateway.pylon.extension.okhttp3;

import okhttp3.OkHttpClient;
import poc.apigateway.pylon.configuration.PylonConfiguration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 실물: com.coupang.apigateway.pylon.extension.ohkttp3.DefaultOkHttp3ClientPool
 * (api-pylon-tools:2.14.9.RELEASE. 2.17.0 에서도 이 파일은 바이트 단위로 동일하다)
 *
 * <p><b>결함을 의도적으로 보존한다.</b>
 *
 * <ol>
 *   <li>read timeout 이 3초로 하드코딩돼 있다. 실물 주석은 "this value will be overridden
 *       on the fly" 라고 적혀 있으나 <b>사실이 아니다</b> — 전 소스에서 per-call 오버라이드가
 *       없고, 어댑터는 {@code pool.get(specId).newCall(...)} 을 그대로 호출한다.
 *       jar 의 spec timeout 도 yml 의 read-timeout 도 여기 닿지 않는다.
 *   <li>커넥션 풀 설정이 없다. OkHttp 기본 {@code ConnectionPool} 과 {@code Dispatcher} 가
 *       쓰이므로 provider 별 max-connection 이 조용히 무시된다.
 *   <li>캐시 키가 specId 다. 옵션이 완전히 같은 두 spec 도 클라이언트를 공유하지 못한다.
 * </ol>
 *
 * <p>실물은 {@code @Deprecated} 다. 그 표기까지 옮긴다.
 *
 * <p>실물은 Guava Cache(expireAfterAccess 10일)를 쓴다. POC 는 pylon-lite 가 Guava 에
 * 의존하지 않으므로 ConcurrentHashMap 으로 미러링한다 — 결함 재현에는 영향이 없다.
 */
@Deprecated
public class DefaultOkHttp3ClientPool implements OkHttp3ClientPool {

    private final PylonConfiguration configuration;
    private final ConcurrentMap<String, OkHttpClient> container = new ConcurrentHashMap<>();

    public DefaultOkHttp3ClientPool(PylonConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public OkHttpClient get(String specId) {
        return container.computeIfAbsent(specId, this::create);
    }

    private OkHttpClient create(String specId) {
        return new OkHttpClient().newBuilder()
            .readTimeout(3, SECONDS) // this value will be overridden on the fly
            .connectTimeout(configuration.getConnectionTimeout(), TimeUnit.MILLISECONDS)
            .build();
    }
}
