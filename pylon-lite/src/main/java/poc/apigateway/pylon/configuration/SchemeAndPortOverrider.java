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
