package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;

@Component
public class ApiGatewayConsumerRolePocGenerationMetaLocator implements GenerationMetaLocator {

    @Override
    public String getPath() {
        return "generation-meta.json";
    }
}
