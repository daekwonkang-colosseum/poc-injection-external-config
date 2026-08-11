package poc.apigateway.pylon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import poc.apigateway.pylon.specs.SpecResolver;
import poc.apigateway.pylon.specs.model.Spec;
import poc.apigateway.pylon.targets.TargetUriFinder;

import java.net.URI;
import java.util.Map;

/**
 * 생성된 어댑터가 유일하게 의존하는 실행 지점.
 * 어댑터는 specId만 넘기고, 옵션은 전부 여기서 Spec을 통해 결정된다.
 */
@Component
public class DynamicApiClient {

    private static final Logger log = LoggerFactory.getLogger(DynamicApiClient.class);

    private final SpecResolver specResolver;
    private final RestTemplatePool restTemplatePool;
    private final TargetUriFinder targetUriFinder;

    public DynamicApiClient(SpecResolver specResolver,
                            RestTemplatePool restTemplatePool,
                            TargetUriFinder targetUriFinder) {
        this.specResolver = specResolver;
        this.restTemplatePool = restTemplatePool;
        this.targetUriFinder = targetUriFinder;
    }

    public <T> T invokeAPI(String specId, RequestBase request, Class<T> responseType) {
        Spec spec = specResolver.get(specId);
        URI uri = targetUriFinder.find(spec, request.getPathParams(), request.getQueryParams());
        RestTemplate restTemplate = restTemplatePool.get(spec);

        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> header : request.getHeaderParams().entrySet()) {
            headers.add(header.getKey(), header.getValue());
        }
        HttpEntity<Object> entity = new HttpEntity<>(request.getBody(), headers);

        log.debug("invoke {} {} readTimeout={}ms", spec.getMethod(), uri, restTemplatePool.readTimeoutOf(spec));

        try {
            ResponseEntity<T> response =
                restTemplate.exchange(uri, spec.getMethod(), entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw new ApiException(specId, e.getRawStatusCode(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new ApiException(specId, "provider access failed: " + e.getMessage(), e);
        }
    }
}
