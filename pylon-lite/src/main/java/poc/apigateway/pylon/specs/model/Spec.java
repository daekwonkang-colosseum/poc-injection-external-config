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
