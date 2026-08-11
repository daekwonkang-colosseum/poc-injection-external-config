package poc.apigateway.pylon.specs.customizer;

import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.model.Spec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionPoolCustomizer implements SpecResolver.SpecCustomizer {

    private final ConcurrentMap<String, Spec.ConnectionPool> connectionPools = new ConcurrentHashMap<>();

    /** 같은 provider에 대한 두 번째 등록은 무시한다 (실물과 동일한 putIfAbsent 시맨틱). */
    public void register(String provider, String poolName, int size) {
        connectionPools.putIfAbsent(provider, new Spec.ConnectionPool(poolName, size));
    }

    @Override
    public Spec process(Spec spec) {
        Spec.ConnectionPool pool = connectionPools.get(spec.getProvider());
        if (pool != null) {
            return Spec.builder(spec).setConnectionPool(pool).build();
        }
        return spec;
    }

    @Override
    public boolean isApplicableInRuntime() {
        return true;
    }
}
