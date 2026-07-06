package ai.confiqure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
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

    /**
     * Declarative flow gate on a single field: the confiqure conversation may only WRITE this
     * field when the {@link #requires()} predicate is {@code true} over the root instance's
     * current values. This turns a sequencing rule that used to live in Javadoc prose ("don't
     * collect the site URL until the legal terms are accepted") into a machine-enforced gate.
     * The model proposes; the engine disposes — a well-behaved model never trips the gate, and a
     * misbehaving one gets a structured, recoverable correction instead of a silent bad write.
     *
     * <p><b>Predicate grammar</b> (the same grammar {@link ToolGate#requires()} and
     * {@link SectionGate#requires()} use). It is evaluated over a flat, dotted-key view of the
     * root instance's current values — a nested field is addressed by its path from the root
     * (e.g. {@code quietHours.enabled}). It is safe by construction: no method calls, no
     * arithmetic, no reflection.
     * <pre>
     * expr       := or
     * or         := and ('||' and)*
     * and        := unary ('&amp;&amp;' unary)*
     * unary      := '!' unary | '(' expr ')' | comparison
     * comparison := operand (('=='|'!='|'&lt;'|'&lt;='|'&gt;'|'&gt;=') operand)?   // a bare operand is a truthy boolean test
     *             | operand 'in' '{' literal (',' literal)* '}'   // membership
     * operand    := literal | fieldPath
     * literal    := number | 'single-quoted' | "double-quoted" | true | false | null
     * fieldPath  := ident ('.' ident)*
     * </pre>
     * Semantics: {@code ==}/{@code !=} compare loosely across String/Boolean/Number (numeric when
     * both sides parse as numbers, else string equality; {@code field == true} matches both
     * {@code Boolean.TRUE} and the string {@code "true"}). {@code &lt; &lt;= &gt; &gt;=} apply only
     * when both operands are numeric, otherwise they are {@code false}. A missing/unset field is
     * {@code null}; any comparison involving {@code null} is {@code false} EXCEPT {@code x == null}
     * (true when missing) and {@code x != null}. A bare {@code fieldPath} means {@code fieldPath == true}.
     *
     * <p><b>Examples</b>
     * <pre>
     * &#64;Confiqure.Gate(requires = "userAcceptedLegalTermsRisks == true",
     *                 message  = "Please accept the legal terms first — then I can save your site URL.")
     * private String siteUrl;
     *
     * &#64;Confiqure.Gate(requires = "plan in {'pro', 'enterprise'} &amp;&amp; seatCount &gt;= 5")
     * private boolean advancedAnalyticsEnabled;
     * </pre>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Gate {
        /** Predicate (grammar above) that must hold over the root instance's current values before this field may be written. */
        String requires();

        /** Optional coaching shown to the conversation when the gate blocks a write. Defaults to the predicate text. */
        String message() default "";
    }

    /**
     * Declarative flow gate on a whole nested section: ANY write at or under this field's path is
     * only permitted while the {@link #requires()} predicate is {@code true} over the root
     * instance's current values. Use it to lock an entire sub-object (e.g. a whole
     * {@code billingDetails} block) behind a prerequisite, instead of repeating {@link Gate} on
     * every leaf. Predicate grammar and semantics are identical to {@link Gate}.
     *
     * <pre>
     * &#64;Confiqure.SectionGate(requires = "userAcceptedLegalTermsRisks == true",
     *                        message  = "Accept the terms first, then we can set up your product interests.")
     * private InterestedProductCategories interestedProductCategories;
     * </pre>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface SectionGate {
        /** Predicate (see {@link Gate}) that must hold before any field at or under this path may be written. */
        String requires();

        /** Optional coaching shown to the conversation when the section gate blocks a write. Defaults to the predicate text. */
        String message() default "";
    }

    /**
     * Marks a field as engine- or tool-populated ONLY: every conversation-proposed write to it (or
     * to any field beneath it) is rejected. Use it for fields a host tool fills in — an analysis
     * result, a computed score, a system-assigned id — that the chat must never author or overwrite.
     * The field fills automatically as a side effect of the work that produces it.
     *
     * <pre>
     * &#64;Confiqure.SystemOnly
     * private SiteAnalysis siteAnalysis;   // written by the SUPPLIER_SITE_ANALYSER tool, never by chat
     * </pre>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface SystemOnly {}

    /**
     * Declarative flow gate on a host tool, declared on the endpoint (root) class: the named tool
     * is only dispatchable while the {@link #requires()} predicate is {@code true} over the bound
     * instance's current values. This enforces call-ordering rules ("never call the analyser before
     * the credentials are set") without the model having to remember them. Repeatable — declare one
     * per gated tool. Predicate grammar and semantics are identical to {@link Gate}.
     *
     * <pre>
     * &#64;Confiqure(end = "/suppliers", tools = {"SUPPLIER_SITE_ANALYSER"})
     * &#64;Confiqure.ToolGate(tool     = "SUPPLIER_SITE_ANALYSER",
     *                     requires = "siteUrl != null &amp;&amp; credentialsVerified == true",
     *                     message  = "I need a verified site URL before I can run the analysis.")
     * public class SupplierConfig { ... }
     * </pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(ToolGates.class)
    @interface ToolGate {
        /** The tool name (as referenced in {@link Confiqure#tools()}) this gate governs. */
        String tool();

        /** Predicate (see {@link Gate}) that must hold over the bound instance before the tool may be dispatched. */
        String requires();

        /** Optional coaching shown to the conversation when the tool gate blocks a dispatch. Defaults to the predicate text. */
        String message() default "";
    }

    /** Container for repeated {@link ToolGate} declarations on one endpoint class. Populated automatically by {@code @Repeatable}. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ToolGates {
        ToolGate[] value();
    }
}
