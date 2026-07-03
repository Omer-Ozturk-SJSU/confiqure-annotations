package ai.confiqure.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Auto-configuration that wires {@link ConfiqureCallback} injection into Spring MVC controllers.
 * Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>It contributes a {@link WebMvcConfigurer} <em>bean</em> that only ADDS the {@link
 * ConfiqureCallbackArgumentResolver} (no {@code @EnableWebMvc}), so it augments — never replaces —
 * Boot's MVC setup and the host's own resolvers. The {@code WebMvcConfigurer} is produced from a
 * {@code @Bean} rather than implemented on this class, so the provided-scope MVC types stay off the
 * auto-config's own type signature. Conditions keep it inert on non-servlet apps. Every type touched
 * here is stable across Spring Boot 3.x and 4.x.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableConfigurationProperties(ConfiqureProperties.class)
public class ConfiqureAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfiqureAutoConfiguration.class);

    /**
     * Contributes the {@link ConfiqureCallback} argument resolver. Serialization uses the host's
     * configured {@link ObjectMapper} bean (honouring the app's Jackson setup — JavaTimeModule,
     * custom serializers, {@code spring.jackson.*}) instead of a bare default, falling back to a
     * plain mapper only if the host has none.
     */
    @Bean
    public WebMvcConfigurer confiqureCallbackWebMvcConfigurer(ConfiqureProperties props,
                                                              ObjectProvider<ObjectMapper> objectMapper) {
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            log.warn("confiqure.base-url is blank -- the reply-URL origin pin is DISABLED, so the "
                    + "workspace API key could be POSTed to any X-Confiqure-Reply-Url a dispatch carries. "
                    + "Set confiqure.base-url (default https://api.confiqure.ai) unless you intend this.");
        }
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        ConfiqureCallbackArgumentResolver resolver = new ConfiqureCallbackArgumentResolver(props, mapper);
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }
}
