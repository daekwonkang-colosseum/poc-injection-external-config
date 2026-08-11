package poc.apigateway.pylon.configuration.dto;

import java.util.List;

public class GenerationMetaDto {
    private String profile;
    private List<String> consumers;
    private String apiManagementHost;

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public List<String> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<String> consumers) {
        this.consumers = consumers;
    }

    public String getApiManagementHost() {
        return apiManagementHost;
    }

    public void setApiManagementHost(String apiManagementHost) {
        this.apiManagementHost = apiManagementHost;
    }
}
