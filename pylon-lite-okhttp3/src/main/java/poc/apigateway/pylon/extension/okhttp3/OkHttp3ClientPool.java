package poc.apigateway.pylon.extension.okhttp3;

import okhttp3.OkHttpClient;

/**
 * 실물: com.coupang.apigateway.pylon.extension.ohkttp3.OkHttp3ClientPool
 * (api-pylon-tools:2.14.9.RELEASE — 실물 패키지명의 {@code ohkttp3} 오타는 정정해 옮겼다)
 *
 * <p><b>이 인터페이스가 POC 의 핵심 증거다.</b> 다른 두 전송은 {@code Spec} 을 받는데
 * 여기만 {@code String specId} 를 받는다. 옵션 캐리어가 전송 계층에 도달할 방법이
 * 시그니처 수준에서 없다 — {@code DefaultOkHttp3ClientPool} 의 timeout 하드코딩은
 * 구현 실수가 아니라 이 계약의 필연적 귀결이다.
 */
public interface OkHttp3ClientPool {

    OkHttpClient get(String specId);
}
