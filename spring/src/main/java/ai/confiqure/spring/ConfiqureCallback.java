package ai.confiqure.spring;

/**
 * Delivers the result of an <b>asynchronous</b> confiqure host tool
 * ({@code @Confiqure.Tool(async = true)}) after the inbound HTTP request has returned.
 *
 * <p>Declare it as a parameter on the tool's controller method and confiqure-spring injects a
 * live instance bound to <em>this</em> tool call (it reads the {@code X-Confiqure-Tool-Call-Id}
 * and {@code X-Confiqure-Reply-Url} headers off the request):
 *
 * <pre>
 * &#64;Confiqure.Tool(name = "analyze_supplier", async = true)
 * &#64;PostMapping("/tools/analyze")
 * public ResponseEntity&lt;Void&gt; analyze(&#64;RequestBody SupplierQuery q, ConfiqureCallback reply) {
 *     // ACK confiqure immediately so it stops waiting on this request…
 *     CompletableFuture
 *         .supplyAsync(() -&gt; service.expensiveAnalysis(q))   // …then do the slow work off-thread
 *         .whenComplete((result, ex) -&gt; {
 *             if (ex != null) reply.fail(ex.getMessage());
 *             else            reply.reply(result);            // POSTed back to confiqure for you
 *         });
 *     return ResponseEntity.accepted().build();
 * }
 * </pre>
 *
 * <p>Each {@code reply}/{@code fail} call POSTs once to confiqure's reply endpoint with your
 * workspace API key ({@code confiqure.api-key}); the POST is fire-and-forget (it never blocks your
 * handler) and its outcome is logged. The reply is one-shot on confiqure's side — the first
 * {@code reply}/{@code fail} wins; later calls for the same tool call are rejected.
 *
 * <p>For a <b>synchronous</b> tool ({@code async = false}, the default) you do NOT need this: just
 * {@code return} the result and confiqure takes the response body inline.
 */
public interface ConfiqureCallback {

    /**
     * Deliver a successful result. {@code result} is serialized to JSON (Jackson) and becomes the
     * tool's output the chat agent sees. Pass any object, {@code Map}, DTO, or {@code null}.
     */
    void reply(Object result);

    /**
     * Deliver a result or an error in one call. When {@code error} is non-blank it takes precedence
     * (confiqure records the call as failed and {@code result} is ignored); otherwise behaves like
     * {@link #reply(Object)}.
     */
    void reply(Object result, String error);

    /** Deliver a failure. The chat agent is told the tool failed with this message and can recover. */
    void fail(String error);

    /**
     * {@code true} when this request actually carried the confiqure async-reply headers — i.e. it
     * was dispatched as an {@code async = true} tool call. {@code false} for a direct/local call (no
     * headers); {@code reply}/{@code fail} then no-op with a warning rather than throwing.
     */
    boolean isPresent();
}
