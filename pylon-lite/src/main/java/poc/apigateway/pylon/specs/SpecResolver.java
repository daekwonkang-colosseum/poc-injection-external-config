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
