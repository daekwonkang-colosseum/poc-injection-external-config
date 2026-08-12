package poc.apigateway.pylon.specs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poc.apigateway.pylon.specs.model.Spec;

/**
 * 실물: com.coupang.apigateway.pylon.specs.ApiProviderPolicyDeployer
 * (api-pylon-tools:2.14.9.RELEASE)
 *
 * <p><b>재현 범위:</b> 원격 값이 <i>도착했을 때</i> 무슨 일이 일어나는가만 옮긴다.
 * 원격에서 어떻게 가져오는가(UnifiedRoutingPolicyUpdater 의 fetch·스케줄러,
 * ApiProviderPolicies DTO 트리)는 여전히 non-goal 이다. 실물의
 * {@code deploy(ApiProviderPolicies)} 대신 {@link #updateTimeout(String, int)} 하나로 줄인다.
 *
 * <p><b>결함 보존 — 함정 6.</b> 이 클래스는 {@link SpecResolver#update(Spec)} 를 호출하는데,
 * 그 메서드는 {@code isApplicableInRuntime() == true} 인 커스터마이저만 태운다. 실물
 * {@code TimeoutCustomizer} 는 <b>false</b> 다. 따라서 클라이언트가 {@code @Primary} 로
 * 주입해 둔 timeout 이 원격 값으로 되돌아간다.
 *
 * <p>ConnectionPool·ManualOverride 커스터마이저는 {@code true} 라 살아남는다.
 * <b>셋 중 timeout 만 사라진다.</b>
 */
public class ApiProviderPolicyDeployer {

    private static final Logger log = LoggerFactory.getLogger(ApiProviderPolicyDeployer.class);

    private final SpecResolver specResolver;

    public ApiProviderPolicyDeployer(SpecResolver specResolver) {
        this.specResolver = specResolver;
    }

    /** 실물 {@code ApiProviderPolicyDeployer.updateTimeout(ApiSpecification)} 과 같은 흐름이다. */
    public void updateTimeout(String specId, int remoteTimeout) {
        Spec previous = specResolver.getEvenNull(specId);

        if (previous == null) {
            // 실물은 여기서 원격 스펙을 새로 등록한다. POC 는 등록에 필요한 provider·path 가
            // 원격 DTO 에서 오는데 그 DTO 트리를 미러링하지 않으므로 건너뛴다.
            log.debug("unknown spec, skipping - {}", specId);
            return;
        }

        if (previous.getTimeout() != remoteTimeout) {
            specResolver.update(Spec.builder(previous).setTimeout(remoteTimeout).build());
            log.info("API read timeout configuration for {} updated: {} ms -> {} ms",
                specId, previous.getTimeout(), remoteTimeout);
        }
    }
}
