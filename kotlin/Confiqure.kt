package ai.confiqure

/**
 * Marks a class as a confiqure.ai configuration target.
 *
 * Example:
 *
 * ```
 * @Confiqure(
 *     end      = "/notifications",
 *     type     = "single",
 *     tools    = ["Amazon_Connect", "Ebay_Connect"],
 *     callback = "https://myapp.com/webhooks/confiqure"
 * )
 * class Notifications { /* ... */ }
 * ```
 *
 * The confiqure CLI scans for this annotation; the backend AI parses the
 * annotated class and generates the chat playbook.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Confiqure(
    val end: String = "",
    val type: String = "single",
    val tools: Array<String> = [],
    val callback: String = ""
)
