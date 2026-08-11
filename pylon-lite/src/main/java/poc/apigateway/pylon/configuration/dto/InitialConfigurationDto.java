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
