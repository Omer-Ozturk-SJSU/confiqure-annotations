package ai.confiqure.spring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The origin pin guards a real credential leak: {@code reply(...)} sends the workspace {@code cqai_}
 * key to the {@code X-Confiqure-Reply-Url}, which arrives on a (possibly unsigned) inbound dispatch.
 * A raw {@code startsWith} would let {@code https://api.confiqure.ai.evil.com} pass and exfiltrate
 * the key — these cases prove the structured check rejects every such bypass while allowing the
 * legitimate prod + dev origins.
 */
class HttpConfiqureCallbackOriginTest {

    private static final String PROD = "https://api.confiqure.ai";
    private static final String DEV = "http://localhost:9090";

    @Test
    void allows_exactProdOrigin() {
        assertTrue(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "https://api.confiqure.ai/api/ABC123/toolreply"));
    }

    @Test
    void allows_devHttpLocalhost() {
        assertTrue(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                DEV, "http://localhost:9090/api/ABC123/toolreply"));
    }

    @Test
    void allows_caseInsensitiveHostAndScheme() {
        assertTrue(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "HTTPS://API.Confiqure.AI/api/x/toolreply"));
    }

    @Test
    void rejects_suffixBypass() {
        // The classic startsWith bypass — must be refused.
        assertFalse(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "https://api.confiqure.ai.evil.com/api/x/toolreply"));
    }

    @Test
    void rejects_embeddedUserinfo() {
        assertFalse(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "https://api.confiqure.ai@evil.com/api/x/toolreply"));
    }

    @Test
    void rejects_schemeDowngrade() {
        // base is https → an http reply URL (downgrade / MITM surface) is refused.
        assertFalse(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "http://api.confiqure.ai/api/x/toolreply"));
    }

    @Test
    void rejects_differentPort() {
        assertFalse(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                DEV, "http://localhost:9091/api/x/toolreply"));
    }

    @Test
    void rejects_unrelatedHost() {
        assertFalse(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "https://evil.com/api/x/toolreply"));
    }

    @Test
    void rejects_malformedReplyUrl() {
        assertFalse(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "ht!tp://[not-a-url"));
    }

    @Test
    void blankBase_disablesPin_optOut() {
        // Documented opt-out: a host that blanks confiqure.base-url accepts any reply target.
        assertTrue(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                "", "https://anything.example/api/x/toolreply"));
    }

    @Test
    void prodDefaultPort_matchesExplicit443() {
        assertTrue(HttpConfiqureCallback.replyUrlMatchesConfiguredOrigin(
                PROD, "https://api.confiqure.ai:443/api/x/toolreply"));
    }
}
