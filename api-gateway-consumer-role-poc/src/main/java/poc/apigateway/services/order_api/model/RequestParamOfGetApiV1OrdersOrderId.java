package poc.apigateway.services.order_api.model;

import poc.apigateway.pylon.RequestBase;

public class RequestParamOfGetApiV1OrdersOrderId extends RequestBase {

    public RequestParamOfGetApiV1OrdersOrderId(String orderId) {
        addPathParam("orderId", orderId);
    }

    public RequestParamOfGetApiV1OrdersOrderId withVerbose(boolean verbose) {
        addQueryParam("verbose", verbose);
        return this;
    }
}
