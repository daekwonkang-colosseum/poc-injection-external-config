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
