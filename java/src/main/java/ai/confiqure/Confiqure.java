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
 *     type     = "single",
 *     tools    = {"Amazon_Connect", "Ebay_Connect"},
 *     callback = "https://myapp.com/webhooks/confiqure"
 * )
 * public class Notifications { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Confiqure {
    /** Chat endpoint segment. Defaults to snake_case of the class name when blank. */
    String end() default "";

    /** Configuration shape. Defaults to "single". */
    String type() default "single";

    /** Names of workspace tools referenced during chat (must match ConfigTool rows). */
    String[] tools() default {};

    /** End-of-chat callback URL. Defaults to the workspace's defaultCallbackUrl when blank. */
    String callback() default "";
}
