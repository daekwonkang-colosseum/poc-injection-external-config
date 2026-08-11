package poc.apigateway.pylon.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import poc.apigateway.pylon.configuration.dto.GenerationMetaDto;
import poc.apigateway.pylon.configuration.dto.InitialConfigurationDto;
import poc.apigateway.pylon.configuration.dto.ProviderConfigurationDto;
import poc.apigateway.pylon.configuration.generated.GenerationMetaLocator;
import poc.apigateway.pylon.configuration.generated.InitialConfigurationLocator;
import poc.apigateway.pylon.configuration.generated.SpecConfigurationLocator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 생성 모듈이 심어둔 JSON을 읽어 "jar가 들고 있는 환경 값"을 메모리에 올린다.
 * 이 값이 뒤에서 PylonConfiguration(외부 설정)과 만나 덮어써진다.
 */
@Component
public class BuildConfigurations {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final List<ProviderConfigurationDto> providers;
    private final InitialConfigurationDto initialConfiguration;
    private final GenerationMetaDto generationMeta;

    public BuildConfigurations(List<SpecConfigurationLocator> specLocators,
                               InitialConfigurationLocator initialLocator,
                               GenerationMetaLocator metaLocator) {
        List<ProviderConfigurationDto> loaded = new ArrayList<>();
        for (SpecConfigurationLocator locator : specLocators) {
            loaded.add(read(locator.getPath(), ProviderConfigurationDto.class));
        }
        this.providers = Collections.unmodifiableList(loaded);
        this.initialConfiguration = read(initialLocator.getPath(), InitialConfigurationDto.class);
        this.generationMeta = read(metaLocator.getPath(), GenerationMetaDto.class);
    }

    public List<ProviderConfigurationDto> getProviders() {
        return providers;
    }

    public InitialConfigurationDto getInitialConfiguration() {
        return initialConfiguration;
    }

    public GenerationMetaDto getGenerationMeta() {
        return generationMeta;
    }

    private <T> T read(String path, Class<T> type) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("classpath resource not found: " + path);
            }
            return MAPPER.readValue(stream, type);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read classpath resource: " + path, e);
        }
    }
}
