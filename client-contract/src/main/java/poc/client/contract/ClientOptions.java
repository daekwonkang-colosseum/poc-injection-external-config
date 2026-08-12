package poc.client.contract;

/**
 * 전송 구현체에 전달되는 유효 옵션. 불변이다.
 *
 * <p>read timeout 보정식은 이 클래스에만 존재한다. 전송마다 식을 복제하면
 * 한쪽만 바뀌었을 때 조용히 갈라지는데, 그것이 이 계약이 막으려는 결함이다.
 */
public final class ClientOptions {

    private static final int BUCKET = 100;
    private static final int ROUND_TRIP_TIME = 100;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String poolName;
    private final int poolSize;

    private ClientOptions(int connectTimeoutMillis, int readTimeoutMillis,
                          String poolName, int poolSize) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.poolName = poolName;
        this.poolSize = poolSize;
    }

    public static ClientOptions of(int connectTimeoutMillis, int readTimeoutMillis,
                                   String poolName, int poolSize) {
        return new ClientOptions(connectTimeoutMillis, uplift(readTimeoutMillis), poolName, poolSize);
    }

    /** 보정하지 않는다. 보정은 read timeout 에만 적용되는 실물 동작이다. */
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /** 실제로 소켓에 걸리는 read timeout. */
    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public String getPoolName() {
        return poolName;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /**
     * 전송 구현체가 클라이언트 인스턴스를 캐시할 때 쓰는 키.
     *
     * <p>풀 이름이 키에 들어가는 것이 핵심이다. 실물 {@code DefaultWebClientPool} 은
     * 보정된 timeout 만으로 키를 잡아, 풀이 다른 두 provider 가 같은 클라이언트를
     * 공유한다.
     */
    public String cacheKey() {
        return poolName + "-" + readTimeoutMillis;
    }

    private static int uplift(int timeout) {
        return (int) (Math.ceil((double) timeout / BUCKET) * BUCKET) + ROUND_TRIP_TIME;
    }
}
