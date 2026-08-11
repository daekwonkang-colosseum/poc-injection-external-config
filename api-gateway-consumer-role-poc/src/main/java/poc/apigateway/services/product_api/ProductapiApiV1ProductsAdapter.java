package poc.apigateway.services.product_api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import poc.apigateway.pylon.DynamicApiClient;
import poc.apigateway.services.product_api.model.ProductDto;
import poc.apigateway.services.product_api.model.RequestParamOfGetApiV1ProductsProductId;

@Component
public class ProductapiApiV1ProductsAdapter {

    private final DynamicApiClient apiClient;

    @Autowired
    public ProductapiApiV1ProductsAdapter(DynamicApiClient dynamicApiClient) {
        this.apiClient = dynamicApiClient;
    }

    /** API : product_api GET /api/v1/products/{productId} */
    public ProductDto getApiV1ProductsProductId(RequestParamOfGetApiV1ProductsProductId requestBase) {
        String specId = "6512a0b1c2d3e4f500000002";
        return apiClient.invokeAPI(specId, requestBase, ProductDto.class);
    }
}
