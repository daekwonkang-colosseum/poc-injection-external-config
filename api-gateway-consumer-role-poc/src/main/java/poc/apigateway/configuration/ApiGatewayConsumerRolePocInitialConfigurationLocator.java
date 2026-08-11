package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;

@Component
public class ApiGatewayConsumerRolePocInitialConfigurationLocator implements InitialConfigurationLocator {

    @Override
    public String getPath() {
        return "initial_configuration.json";
    }
}
