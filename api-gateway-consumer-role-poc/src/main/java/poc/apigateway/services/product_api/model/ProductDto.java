package poc.apigateway.services.product_api.model;

public class ProductDto {
    private String productId;
    private String name;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
