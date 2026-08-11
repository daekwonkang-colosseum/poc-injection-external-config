package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

@Component
public class ProductApiOfApiGatewayConsumerRolePocSpecConfigurationLocator implements SpecConfigurationLocator {

    @Override
    public String getPath() {
        return "product_api_of_api-gateway-consumer-role-poc_configuration.json";
    }
}
