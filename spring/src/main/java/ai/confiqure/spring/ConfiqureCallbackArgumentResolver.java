package ai.confiqure.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Resolves a {@link ConfiqureCallback} controller-method parameter into a live, per-request instance
 * bound to the current confiqure tool call. Reads the correlation token + reply URL off the request
 * headers; the shared {@link HttpClient}, {@link ObjectMapper}, and {@link ConfiqureProperties} are
 * threaded into the callback so the async reply POST can be fired without per-request setup.
 *
 * <p>Registered by {@link ConfiqureAutoConfiguration} via {@code WebMvcConfigurer.addArgumentResolvers}.
 */
public class ConfiqureCallbackArgumentResolver implements HandlerMethodArgumentResolver {

    /** Header carrying the opaque one-shot tool-call token, echoed back on the reply. */
    static final String TOOL_CALL_ID_HEADER = "X-Confiqure-Tool-Call-Id";
    /** Header carrying the absolute URL the result is POSTed back to. */
    static final String REPLY_URL_HEADER = "X-Confiqure-Reply-Url";

    private final ConfiqureProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public ConfiqureCallbackArgumentResolver(ConfiqureProperties props, ObjectMapper mapper) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        // The host's configured ObjectMapper (passed by the auto-config) so result serialization
        // honours the app's Jackson modules/settings — not a bare new ObjectMapper().
        this.mapper = mapper;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ConfiqureCallback.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String toolCallId = webRequest.getHeader(TOOL_CALL_ID_HEADER);
        String replyUrl = webRequest.getHeader(REPLY_URL_HEADER);
        return new HttpConfiqureCallback(props, http, mapper, toolCallId, replyUrl);
    }
}
