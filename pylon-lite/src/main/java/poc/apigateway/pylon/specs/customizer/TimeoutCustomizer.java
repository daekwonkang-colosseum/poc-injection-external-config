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
