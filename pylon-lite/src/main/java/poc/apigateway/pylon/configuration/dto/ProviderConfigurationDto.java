package poc.apigateway.pylon.configuration.dto;

import java.util.List;

public class ProviderConfigurationDto {
    private String name;
    private List<ApiSpecificationConfigurationDto> specifications;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ApiSpecificationConfigurationDto> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(List<ApiSpecificationConfigurationDto> specifications) {
        this.specifications = specifications;
    }
}
