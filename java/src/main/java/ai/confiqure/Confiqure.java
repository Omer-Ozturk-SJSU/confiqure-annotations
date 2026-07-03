package ai.confiqure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a confiqure.ai configuration target. The confiqure CLI
 * scans source files for this annotation and uploads them; the confiqure
 * backend AI parses the annotated class and generates the chat playbook.
 *
 * <pre>
 * &#64;Confiqure(
 *     end      = "/notifications",
 *     type     = Confiqure.Type.SINGLE,
 *     scope    = Confiqure.Scope.LIMITED,
 *     tools    = {"SEND_TEST_NOTIFICATION"}
 * )
 * public class Notifications { ... }
 * </pre>
 *
 * <p>Tools come in two kinds, both declared once as a {@link Tool}-annotated
 * method and referenced by name in {@link #tools()}:
 * <ul>
 *   <li><b>Server-side</b> ({@code serverSide=true}, the default) — a real
 *       controller method confiqure invokes over HTTP. Returns data or performs
 *       a backend action; no UI.</li>
 *   <li><b>Frontend</b> ({@code serverSide=false}) — a contract-stub method
 *       (body never runs on the backend) whose handler runs in the host's
 *       browser via the embed SDK. Use for anything needing a UI (OAuth, a
 *       picker, rendering a view). The method signature still carries the I/O
 *       contract: its {@code @RequestBody} DTO is the input, its return type the
 *       output. Example:
 *       <pre>
 *       &#64;Confiqure.Tool(name = "show_report", serverSide = false)
 *       public String showReport(&#64;RequestBody StockFilterData filter) { return null; }
 *       </pre></li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Confiqure {
    /** Chat endpoint segment. Defaults to snake_case of the class name when blank. */
    String end() default "";

    /** Configuration shape: SINGLE (one record per user) or MULTI (table of records). */
    Type type() default Type.SINGLE;

    /** Chat context scope: LIMITED (this endpoint only) or UNLIMITED (can navigate all endpoints). */
    Scope scope() default Scope.LIMITED;

    /** Names of @Confiqure.Tool methods this endpoint can invoke during chat. */
    String[] tools() default {};

    enum Type {
        SINGLE,
        MULTI
    }

    enum Scope {
        LIMITED,
        UNLIMITED
    }

    /** Marks a method as a tool the chat agent can invoke during a session. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tool {
        /** Tool name. Defaults to the method name when blank. */
        String name() default "";

        /**
         * {@code true} (default): a server-side tool — confiqure dispatches an
         * HTTP call to this controller method (returns data / performs a backend
         * action, no UI).
         * <p>{@code false}: a frontend tool — this method is a contract stub
         * whose handler runs in the host's browser via the embed SDK. The method
         * signature still defines the I/O contract (its {@code @RequestBody} DTO
         * is the input, its return type the output), but the body never runs on
         * the backend. Use for anything needing a UI (OAuth, pickers, rendering).
         */
        boolean serverSide() default true;

        /**
         * Server-side reply discipline (ignored when {@code serverSide=false}).
         * <p>{@code false} (default) — <b>synchronous</b>: confiqure waits on the HTTP call and
         * takes the method's return value as the tool result. Your handler is plain Spring — the
         * request body IS your DTO (no envelope), and {@code confiqureKey} is injected into it:
         * <pre>
         * &#64;Confiqure.Tool(name = "lookup_supplier")
         * &#64;PostMapping("/supplier")
         * public SupplierDto lookup(&#64;RequestBody SupplierQuery q) { return service.lookup(q); }
         * </pre>
         * <p>{@code true} — <b>asynchronous</b>: confiqure ACKs immediately and you deliver the
         * result later. The {@code ai.confiqure:confiqure-spring} SDK injects a {@code
         * ConfiqureCallback} that does the header-reading + signed POST for you (the same {@code
         * confiqureKey} rides your {@code @RequestBody} DTO as in the sync case):
         * <pre>
         * &#64;Confiqure.Tool(name = "analyze_supplier", async = true)
         * &#64;PostMapping("/analyze")
         * public ResponseEntity&lt;Void&gt; analyze(&#64;RequestBody SupplierQuery q, ConfiqureCallback reply) {
         *     CompletableFuture.supplyAsync(() -&gt; service.slowAnalysis(q))
         *         .whenComplete((r, ex) -&gt; { if (ex != null) reply.fail(ex.getMessage()); else reply.reply(r); });
         *     return ResponseEntity.accepted().build();   // ACK now; the result follows
         * }
         * </pre>
         * Without the SDK, read the {@code X-Confiqure-Tool-Call-Id} + {@code X-Confiqure-Reply-Url}
         * headers and POST {@code {"result":…}} back yourself. Use async only when the work outlives
         * one request (long jobs, human-in-the-loop, webhooks); ~90% of tools are synchronous, and
         * even a slow sync handler has a 5-minute window before async is warranted.
         */
        boolean async() default false;
    }

    /** Marks a controller method as the workspace's default callback hook.
     *  Receives lifecycle events (onStart, onComplete, onTimeout) with confiqureKeys. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface DefaultCallbackHook {}
}
