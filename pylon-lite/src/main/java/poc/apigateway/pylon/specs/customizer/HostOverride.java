package poc.apigateway.pylon.specs.customizer;

public class HostOverride {
    private final String scheme;
    private final String host;
    private final int port;

    private HostOverride(String scheme, String host, int port) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
    }

    public static HostOverride of(String scheme, String host, int port) {
        return new HostOverride(scheme, host, port);
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ":" + port;
    }
}
