package poc.apigateway.services.product_api.model;

import poc.apigateway.pylon.RequestBase;

public class RequestParamOfGetApiV1ProductsProductId extends RequestBase {

    public RequestParamOfGetApiV1ProductsProductId(String productId) {
        addPathParam("productId", productId);
    }
}
