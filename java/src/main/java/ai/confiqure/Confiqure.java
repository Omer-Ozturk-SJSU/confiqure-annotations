package ai.confiqure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The confiqure.ai annotation vocabulary. {@code Confiqure} itself is a namespace and cannot be
 * applied to anything; use its nested annotations. The confiqure CLI scans source files for them
 * and pushes the annotated classes; the chat model reads the class source and follows it.
 *
 * <p><b>Objects.</b> A configuration class is an <i>object</i>: something the end user would call
 * "my X". Declare what it is with ONE of four annotations:
 * <pre>
 *                        one record per owner        many records per owner
 *   shared by the org    &#64;Confiqure.Setting          &#64;Confiqure.List
 *   private to a user    &#64;Confiqure.User.Setting     &#64;Confiqure.User.List
 * </pre>
 * A class used only as a field type inside an object (an address, a schedule) is a
 * <i>part</i>: it needs no annotation and has no records of its own. A field whose type is
 * another object class is a <i>reference</i> to that object's record(s), never a copy of it.
 * A {@link List} object names the field that identifies a record with {@link Identity}.
 * The chat is never fenced to one object: it opens on the screen's context and reaches any
 * object or tool class the conversation needs.
 *
 * <p><b>Tools.</b> Tools are declared only as <b>tool classes</b> ({@link Tool} on a class): the
 * class Javadoc is the business flow, each public method one typed operation. An operation runs
 * on your server unless marked {@link Browser} (runs in the page) or {@link Async} (result
 * delivered later).
 *
 * <p><b>Facts.</b> {@link Facts} marks the read-only user-facts contract your application serves
 * back to confiqure.
 *
 * <p>The pre-3.0 forms — {@code @Confiqure(end, type, scope, dataScope, tools)} on a class and
 * {@code @Confiqure.Tool} on a method — no longer compile.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Confiqure {

    /**
     * An object shared by the whole organization with exactly ONE record per organization
     * (account settings, the repricer's account-wide rules, the business model). The chat reads
     * and edits that one record; it never creates a second one.
     *
     * <pre>
     * &#64;Confiqure.Setting(end = "/repricer-settings")
     * public class RepricerSettings { ... }
     * </pre>
     *
     * @since 2.0
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Setting {
        /** Data-API address of the object. Defaults to snake_case of the class name when blank. */
        String end() default "";

    }

    /**
     * An object shared by the whole organization with MANY records per organization (suppliers,
     * warehouses, per-listing settings). Mark the field that identifies one record with
     * {@link Identity}; the engine refuses a second record with the same identity.
     *
     * <pre>
     * &#64;Confiqure.List(end = "/listing-repricing")
     * public class ListingRepricing {
     *     &#64;Confiqure.Identity
     *     private String listingSku;     // from a ListingsTool search result, never typed
     *     private Money minPrice;
     * }
     * </pre>
     *
     * @since 2.0
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        /** Data-API address of the object. Defaults to snake_case of the class name when blank. */
        String end() default "";

    }

    /**
     * The per-user variants of {@link Setting} and {@link List}: each end user gets private
     * records even inside an organization (personal credentials, individual preferences).
     *
     * @since 2.0
     */
    interface User {
        /** One private record per end user. See {@link Confiqure.Setting}. */
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface Setting {
            /** Data-API address of the object. Defaults to snake_case of the class name when blank. */
            String end() default "";

        }

        /** Many private records per end user. See {@link Confiqure.List}. */
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            /** Data-API address of the object. Defaults to snake_case of the class name when blank. */
            String end() default "";

        }
    }

    /**
     * The <b>user-facts contract</b>: not a configuration, but what your application already knows
     * about an end user (their categories, their counts, their open issues). Its FIELD COMMENTS
     * tell the model what each fact means. confiqure {@code GET}s {@code hostBaseUrl + callback}
     * for the acting user, binds the JSON into this class, and puts the compact core in front of
     * the model so the chat stops asking for things you already know.
     *
     * <pre>
     * &#64;Confiqure.Facts(callback = "/api/confiqure/user-facts")
     * public class SellerFacts {
     *     // The product categories this seller actually sells in.
     *     private List&lt;String&gt; sellingCategories;
     *     // How many of their listings are currently stranded.
     *     private Integer strandedCount;
     * }
     * </pre>
     *
     * <p>Read-only and host-owned: the conversation never writes a facts class, it is not an
     * object, and it holds no records. Fetches are cached for 24 hours and refreshed in the
     * background — a slow or down endpoint never delays a chat, it just serves the last known
     * facts. Comment every field.
     *
     * @since 3.0
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Facts {
        /** Relative path of the {@code GET} endpoint in YOUR application that returns the current facts for one end user. */
        String callback();
    }

    /**
     * On ONE field of a {@link List} (or {@link User.List}) object: the value that identifies a
     * record. Two records of the same owner can never share it — the engine refuses the second
     * save. It is usually an id the host owns (a SKU, an order number) that arrives from a tool
     * result the user picked, never a value the chat typed.
     *
     * @since 2.0
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Identity {}

    /**
     * A <b>tool class</b>: a group of related operations the chat can call, declared ONCE as a
     * class. Since 2.0 this is the only place a tool can be declared — there are no method-level
     * tools any more; the navigator hands the chat a whole tool class, never a single method.
     *
     * <p><b>A tool class is a contract.</b> Its Javadoc is the business flow the chat follows —
     * when to search, with which method, how to show the results, what a change needs, what to
     * say after — and every public method is one operation with typed parameters and a typed
     * return value. The chat model reads the class source and follows it; the confiqure engine
     * converts and checks every argument against the declared parameter types, calls the method,
     * and checks the reply against the declared return type. Nothing else is inferred.
     *
     * <p>Operations run on your server by default (a real controller method confiqure invokes
     * over HTTP). Mark an operation that must run in the host's browser with {@link Browser},
     * and one whose result arrives later with {@link Async}.
     *
     * <pre>
     * /** FLOW: the seller's listings — find one, then change it.
     *  *  FIND: ask what they know (a title, an ASIN, or a code) and search with the matching
     *  *  method. One hit: confirm it. Several: show them as options and let the seller pick.
     *  *  None: say what was searched and ask for another clue.
     *  *  CHANGE: every change takes the sku of a found listing, never a typed one.
     *  *  Call, then say what changed. After a pick, "it" means that listing. *&#47;
     * &#64;Confiqure.Tool(name = "ListingsTool")
     * &#64;RestController
     * public class ListingsTool {
     *     &#64;PostMapping("/by-title")  public List&lt;Listing&gt; byTitle(&#64;RequestBody TitleQuery q) { ... }
     *     &#64;PostMapping("/by-asin")   public List&lt;Listing&gt; byAsin(&#64;RequestBody AsinQuery q) { ... }
     *     &#64;PostMapping("/quantity")  public Ack setQuantity(&#64;RequestBody QuantityChange c) { ... }
     *     &#64;Confiqure.Browser
     *     public Ack openProduct360(&#64;RequestBody SkuRef ref) { return null; }   // runs in the page
     * }
     * </pre>
     * Tool classes are not attached to objects: the chat's navigator brings a tool class into the
     * conversation as its own <i>tool frame</i> when an ask needs it, exactly as it brings an
     * object in as a <i>setting frame</i>.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tool {
        /** Tool class name. Defaults to the class name when blank. */
        String name() default "";
    }

    /**
     * An operation of a {@link Tool} class that runs in the host's <b>browser</b>, not on the
     * server: the method is a contract stub (its body never runs on the backend) whose handler
     * runs in the page via the embed SDK. Use it for anything that needs a UI — opening a view,
     * a picker, OAuth. The signature still carries the I/O contract: the {@code @RequestBody}
     * DTO is the input, the return type the output.
     *
     * @since 2.0
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Browser {}

    /**
     * An operation of a {@link Tool} class whose result arrives <b>later</b>: confiqure ACKs the
     * call immediately and you deliver the result afterwards. The {@code ai.confiqure:confiqure-spring}
     * SDK injects a {@code ConfiqureCallback} that does the header-reading + signed POST for you:
     * <pre>
     * &#64;Confiqure.Async
     * &#64;PostMapping("/analyze")
     * public ResponseEntity&lt;Void&gt; analyze(&#64;RequestBody SupplierQuery q, ConfiqureCallback reply) {
     *     CompletableFuture.supplyAsync(() -&gt; service.slowAnalysis(q))
     *         .whenComplete((r, ex) -&gt; { if (ex != null) reply.fail(ex.getMessage()); else reply.reply(r); });
     *     return ResponseEntity.accepted().build();   // ACK now; the result follows
     * }
     * </pre>
     * Without the SDK, read the {@code X-Confiqure-Tool-Call-Id} + {@code X-Confiqure-Reply-Url}
     * headers and POST {@code {"result":…}} back yourself. Use it only when the work outlives one
     * request (long jobs, human-in-the-loop, webhooks); even a slow synchronous handler has a
     * 5-minute window.
     *
     * @since 2.0
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Async {}

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
     * &#64;Confiqure.List(end = "/suppliers")
     * &#64;Confiqure.ToolGate(tool     = "analyseSite",
     *                     requires = "siteUrl != null &amp;&amp; credentialsVerified == true",
     *                     message  = "I need a verified site URL before I can run the analysis.")
     * public class SupplierConfig { ... }
     * </pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(ToolGates.class)
    @interface ToolGate {
        /** The operation name (a method of a {@link Tool} class) this gate governs. */
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

    /**
     * Verbatim, auditable consent capture on a Boolean field. The annotated field is the capture
     * target: the conversation can NEVER write it (it is implicitly {@link SystemOnly}) — only the
     * end user's explicit Accept/Decline click on the consent card the engine renders can set it.
     * The engine shows {@link #text()} word-for-word (an LLM never generates or paraphrases legal
     * text) and records the decision — with a version hash of the exact wording shown — as a
     * compliance audit row ("user U accepted consent v3 at T in conversation C").
     *
     * <p>Gate the fields that depend on the consent with plain {@link Gate}/{@link SectionGate}
     * predicates over this Boolean — consent composes with gating instead of being its own gate:
     *
     * <pre>
     * &#64;Confiqure.Consent(id = "supplier-scraping",
     *                    text = "By continuing you confirm you have the right to access this "
     *                         + "supplier site with the credentials you provide, and you accept "
     *                         + "the risks described in our automation terms.")
     * private Boolean userAcceptedLegalTermsRisks;
     *
     * &#64;Confiqure.Gate(requires = "userAcceptedLegalTermsRisks == true",
     *                 message  = "The legal terms must be accepted first.")
     * private String siteUrl;
     * </pre>
     *
     * <p>The text is inline source (string literals, {@code +} concatenation allowed) — it ships
     * with the class on push, so the engine binds each acceptance to the exact wording that was live.
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Consent {
        /** Stable consent identifier, e.g. {@code "supplier-scraping"} — the audit rows key on it. */
        String id();

        /** The verbatim consent/legal text shown to the user. Never paraphrased, never model-generated. */
        String text();
    }

    /**
     * Classifies a String field as a secret (password, API token, TOTP seed, …). The engine keeps
     * the plaintext OUT of the conversation entirely: the user enters the value in a masked input
     * that posts directly to the engine, the stored configuration carries an opaque
     * {@code secret_ref:…} reference, and the real value — encrypted at rest — is substituted back
     * ONLY where your application receives the data (tool-call bodies and the data API). The chat
     * model, the transcript, and the logs only ever see the reference.
     *
     * <p>Gate predicates may test whether a secret is present ({@code password != null}) but never
     * its value — a value comparison against a secret field fails the push.
     *
     * <pre>
     * &#64;Confiqure.Secret(kind = Confiqure.SecretKind.PASSWORD)
     * private String password;
     * </pre>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Secret {
        /** What kind of credential this is (drives masking hints and audit labels). */
        SecretKind kind() default SecretKind.PASSWORD;
    }

    /** The kinds of secret a {@link Secret} field can hold. */
    enum SecretKind {
        PASSWORD,
        TOKEN,
        TOTP,
        OTHER
    }

    /**
     * Opts a {@link List} (or {@link User.List}) object OUT of chat deletion. By default a List
     * object's records can be deleted from chat — the user asks, the engine shows a
     * direct-bind confirmation card, and only the user's click disposes (soft-delete; the data is
     * staff-recoverable, and your application is notified via the {@code config.deleted} /
     * {@code config.bulk_deleted} lifecycle webhooks). Add {@code @Confiqure.Protect} to a class
     * whose instances must NEVER be chat-deletable — audit logs, immutable records, anything whose
     * removal should only ever happen through your own application:
     *
     * <pre>
     * &#64;Confiqure.List(end = "/audit-entries")
     * &#64;Confiqure.Protect
     * public class AuditEntry { ... }
     * </pre>
     *
     * <p>{@link Setting} objects are never chat-deletable regardless (their one record is reset by
     * reconfiguring it, not deleted), so {@code @Protect} is meaningful only on List objects.
     * The opt-out is capability-level and composes with {@link ToolGate} on {@code nf_delete} (the
     * conditional layer — "deletable, but not while an analysis is running"). Adding or removing
     * {@code @Protect} takes effect on the class's next push; existing saved instances are
     * unaffected by the annotation itself.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Protect {}
}
