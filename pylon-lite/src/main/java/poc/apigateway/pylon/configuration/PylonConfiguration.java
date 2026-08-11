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
