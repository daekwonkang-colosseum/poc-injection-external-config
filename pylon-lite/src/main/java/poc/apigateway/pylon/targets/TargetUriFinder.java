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
