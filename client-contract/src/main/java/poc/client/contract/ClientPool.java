package poc.client.contract;

/**
 * 전송 구현체가 클라이언트 인스턴스를 만들고 캐시하는 계약.
 *
 * <p>실물 pylon 은 전송마다 시그니처가 달랐다 — {@code RestTemplatePool.get(Spec)},
 * {@code WebClientPool.get(Spec)}, {@code OkHttp3ClientPool.get(String specId)}.
 * 마지막 것은 옵션 캐리어가 전송에 도달조차 못 한다. 여기서는 모든 전송이
 * {@link ClientOptions} 하나만 받는다.
 *
 * @param <C> 전송 구현체의 클라이언트 타입
 */
public interface ClientPool<C> {

    C get(ClientOptions options);
}
