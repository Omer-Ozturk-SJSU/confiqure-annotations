package ai.confiqure.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for confiqure-spring, bound from {@code confiqure.*} (e.g. {@code confiqure.api-key},
 * {@code confiqure.base-url}, or the {@code CONFIQURE_API_KEY} / {@code CONFIQURE_BASE_URL} env vars).
 */
@ConfigurationProperties(prefix = "confiqure")
public class ConfiqureProperties {

    /**
     * The workspace API key ({@code cqai_…}) confiqure-spring authenticates async tool replies with.
     * Required for {@link ConfiqureCallback} to send anything — without it, replies no-op with a
     * warning. Get it from the dashboard's Host integration card. Keep it out of source (env var).
     */
    private String apiKey;

    /**
     * The confiqure API origin replies are allowed to go to. A reply is only sent when its
     * {@code X-Confiqure-Reply-Url} starts with this value — a guard so a forged/unsigned dispatch
     * carrying a hostile reply URL can never trick the host into POSTing its {@code cqai_} key to an
     * attacker. Defaults to the public host; set it for a self-hosted/dev confiqure. Blank disables
     * the check (not recommended).
     */
    private String baseUrl = "https://api.confiqure.ai";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
