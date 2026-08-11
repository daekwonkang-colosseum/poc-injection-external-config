package poc.apigateway.configuration;

import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.generated.PylonCodeGeneratorVersion;

@Component
public class ApiGatewayConsumerRolePocPylonCodeGeneratorVersion implements PylonCodeGeneratorVersion {

    @Override
    public String getVersion() {
        return "0.1.0-POC";
    }

    @Override
    public int getCompatibilityLevel() {
        return 20190101;
    }
}
