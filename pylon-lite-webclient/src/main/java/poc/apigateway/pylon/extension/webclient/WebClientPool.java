package poc.apigateway.pylon.extension.webclient;

import org.springframework.web.reactive.function.client.WebClient;
import poc.apigateway.pylon.specs.model.Spec;

/**
 * 실물: com.coupang.apigateway.pylon.extension.webclient.WebClientPool
 * (api-pylon-tools:2.14.9.RELEASE)
 *
 * <p>실물 javadoc 은 "User can override by providing new one and annotate @Primary" 라고
 * 명시한다. 그 문장이 이 POC 가 계약을 씌울 수 있는 근거다.
 */
public interface WebClientPool {

    WebClient get(Spec spec);
}
