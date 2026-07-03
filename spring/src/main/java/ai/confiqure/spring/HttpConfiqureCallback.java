package ai.confiqure.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link ConfiqureCallback} that POSTs the result back to confiqure's reply endpoint. One instance
 * per tool call (built by {@link ConfiqureCallbackArgumentResolver}); the HTTP client + mapper are
 * shared. The POST is non-blocking ({@link HttpClient#sendAsync}) so a handler can ACK its inbound
 * request immediately and let the reply ride confiqure-spring's background completion.
 */
class HttpConfiqureCallback implements ConfiqureCallback {

    private static final Logger log = LoggerFactory.getLogger(HttpConfiqureCallback.class);

    private final ConfiqureProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String toolCallId;
    private final String replyUrl;

    HttpConfiqureCallback(ConfiqureProperties props, HttpClient http, ObjectMapper mapper,
                          String toolCallId, String replyUrl) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
        this.toolCallId = toolCallId;
        this.replyUrl = replyUrl;
    }

    @Override
    public boolean isPresent() {
        return toolCallId != null && !toolCallId.isBlank()
                && replyUrl != null && !replyUrl.isBlank();
    }

    @Override
    public void reply(Object result) {
        send(result, null);
    }

    @Override
    public void reply(Object result, String error) {
        send(result, error);
    }

    @Override
    public void fail(String error) {
        send(null, error);
    }

    private void send(Object result, String error) {
        if (!isPresent()) {
            log.warn("confiqure.reply.skipped reason=missing_headers toolCallId={} replyUrl={} "
                    + "-- was this invoked as a @Confiqure.Tool(async=true) tool call?",
                    toolCallId, replyUrl);
            return;
        }
        String apiKey = props.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("confiqure.reply.skipped reason=no_api_key -- set confiqure.api-key (your cqai_ "
                    + "workspace key) to enable async tool replies");
            return;
        }
        // Only ever send the API key to the EXACT confiqure origin the host configured. A raw
        // startsWith would be bypassable (e.g. https://api.confiqure.ai.evil.com or
        // https://api.confiqure.ai@evil.com), so compare PARSED URI components — scheme + host +
        // normalized port, with embedded userinfo rejected. This pin stops a forged/unsigned
        // dispatch's hostile X-Confiqure-Reply-Url from exfiltrating the cqai_ key.
        if (!replyUrlMatchesConfiguredOrigin(props.getBaseUrl(), replyUrl)) {
            log.warn("confiqure.reply.refused reason=reply_url_outside_base replyUrl={} base={} "
                    + "-- not sending API key", replyUrl, props.getBaseUrl());
            return;
        }

        String json;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.set("result", mapper.valueToTree(result)); // null result -> JSON null (valid)
            if (error != null && !error.isBlank()) {
                body.put("error", error);
            } else {
                body.putNull("error");
            }
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("confiqure.reply.serialize_failed toolCallId={} err={}", toolCallId, e.getMessage());
            return;
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(replyUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header(ConfiqureCallbackArgumentResolver.TOOL_CALL_ID_HEADER, toolCallId)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Fire-and-forget: the inbound tool request must return its ACK promptly, so the reply rides
        // the HttpClient's own executor. We only log the outcome.
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, ex) -> {
                    if (ex != null) {
                        log.warn("confiqure.reply.failed toolCallId={} url={} err={}",
                                toolCallId, replyUrl, ex.getMessage());
                    } else if (resp.statusCode() >= 300) {
                        log.warn("confiqure.reply.rejected toolCallId={} url={} status={} body={}",
                                toolCallId, replyUrl, resp.statusCode(), resp.body());
                    } else {
                        log.info("confiqure.reply.ok toolCallId={} url={} status={}",
                                toolCallId, replyUrl, resp.statusCode());
                    }
                });
    }

    /**
     * True when {@code replyUrl} points at the SAME origin as the configured {@code base} — scheme +
     * host + normalized port, host/scheme compared case-insensitively, embedded userinfo rejected.
     * A blank/null base disables the pin (explicit opt-out). Any parse failure fails CLOSED so a
     * malformed or hostile reply URL never receives the API key. Scheme is matched against the
     * configured base (not hardcoded {@code https}) so a dev/self-hosted {@code http://localhost}
     * confiqure still works while a prod {@code https} base stays https-only.
     */
    static boolean replyUrlMatchesConfiguredOrigin(String base, String replyUrl) {
        if (base == null || base.isBlank()) {
            return true; // pin disabled — host opted out of origin checking
        }
        URI b;
        URI r;
        try {
            b = URI.create(base.trim());
            r = URI.create(replyUrl.trim());
        } catch (RuntimeException e) {
            return false;
        }
        if (r.getHost() == null || r.getUserInfo() != null) {
            return false; // unparsed authority or smuggled credentials -> refuse
        }
        if (!equalsIgnoreCaseNullable(b.getScheme(), r.getScheme())) {
            return false;
        }
        if (!equalsIgnoreCaseNullable(b.getHost(), r.getHost())) {
            return false;
        }
        return effectivePort(b) == effectivePort(r);
    }

    private static int effectivePort(URI u) {
        if (u.getPort() != -1) {
            return u.getPort();
        }
        String scheme = u.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    private static boolean equalsIgnoreCaseNullable(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
