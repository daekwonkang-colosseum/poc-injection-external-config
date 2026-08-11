package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

@Component
public class OrderApiOfApiGatewayConsumerRolePocSpecConfigurationLocator implements SpecConfigurationLocator {

    @Override
    public String getPath() {
        return "order_api_of_api-gateway-consumer-role-poc_configuration.json";
    }
}
