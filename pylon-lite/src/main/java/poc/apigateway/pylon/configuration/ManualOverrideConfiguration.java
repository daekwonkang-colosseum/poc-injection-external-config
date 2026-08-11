package poc.apigateway.pylon.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import poc.apigateway.pylon.specs.customizer.HostOverride;
import poc.apigateway.pylon.specs.customizer.ManualOverrideCustomizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 두 번째 주입 경로. 타입 빈이 아니라 Environment 를 정규식으로 훑는다.
 * 키 이름은 실제 pylon 을 그대로 미러링한다.
 */
@Configuration
public class ManualOverrideConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ManualOverrideConfiguration.class);

    private static final String VERSION = "api_gateway.manual_override.version";
    private static final Pattern PROVIDER_SERVER =
        Pattern.compile("api_gateway\\.manual_override\\.provider\\.([\\w]+)\\.server");
    private static final Pattern SPEC_SERVER =
        Pattern.compile("api_gateway\\.manual_override\\.spec\\.([\\w]+)\\.server");

    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final String SCHEME_HTTPS = "https";

    private Environment environment;

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public ManualOverrideCustomizer apiGatewayManualOverrideProvider() {
        ManualOverrideCustomizer customizer = new ManualOverrideCustomizer();

        for (PropertySource<?> source : ((AbstractEnvironment) environment).getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String key : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                String value = environment.getProperty(key);
                if (value == null) {
                    continue;
                }
                registerVersion(key, value, customizer);
                registerProvider(key, value, customizer);
                registerSpec(key, value, customizer);
            }
        }
        return customizer;
    }

    private void registerVersion(String key, String value, ManualOverrideCustomizer customizer) {
        if (!VERSION.equals(key)) {
            return;
        }
        try {
            int version = Integer.parseInt(value.trim());
            log.info("API Gateway manual override version will be applied as {}", version);
            customizer.setVersion(version);
        } catch (NumberFormatException e) {
            log.warn("invalid manual override version '{}', ignoring", value);
        }
    }

    private void registerProvider(String key, String value, ManualOverrideCustomizer customizer) {
        Matcher matcher = PROVIDER_SERVER.matcher(key);
        if (!matcher.matches()) {
            return;
        }
        HostOverride override = parse(key, value);
        if (override != null) {
            log.info("manual override - provider {} -> {}", matcher.group(1), override);
            customizer.registerProvider(matcher.group(1), override);
        }
    }

    private void registerSpec(String key, String value, ManualOverrideCustomizer customizer) {
        Matcher matcher = SPEC_SERVER.matcher(key);
        if (!matcher.matches()) {
            return;
        }
        HostOverride override = parse(key, value);
        if (override != null) {
            log.info("manual override - spec {} -> {}", matcher.group(1), override);
            customizer.registerSpec(matcher.group(1), override);
        }
    }

    private HostOverride parse(String key, String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                log.warn("invalid manual override value '{}' for property {}", value, key);
                return null;
            }
            int port = uri.getPort();
            if (port < 0) {
                port = SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme()) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }
            return HostOverride.of(uri.getScheme(), uri.getHost(), port);
        } catch (URISyntaxException e) {
            log.warn("invalid manual override value '{}' for property {}", value, key);
            return null;
        }
    }
}
