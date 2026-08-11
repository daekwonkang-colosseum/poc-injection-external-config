package poc.apigateway.services.order_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import poc.apigateway.pylon.DynamicApiClient;
import poc.apigateway.services.order_api.model.OrderDto;
import poc.apigateway.services.order_api.model.RequestParamOfGetApiV1OrdersOrderId;

/**
 * 생성 코드를 모방한다. 옵션을 받는 생성자 자리가 없다 —
 * timeout·풀·호스트는 전부 DynamicApiClient 아래에서 결정된다.
 */
@Component
public class OrderapiApiV1OrdersAdapter {

    private final DynamicApiClient apiClient;

    @Autowired
    public OrderapiApiV1OrdersAdapter(DynamicApiClient dynamicApiClient) {
        this.apiClient = dynamicApiClient;
    }

    /** API : order_api GET /api/v1/orders/{orderId} */
    public OrderDto getApiV1OrdersOrderId(RequestParamOfGetApiV1OrdersOrderId requestBase) {
        String specId = "6512a0b1c2d3e4f500000001";
        return apiClient.invokeAPI(specId, requestBase, OrderDto.class);
    }
}
