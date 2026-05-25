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

    /** Configuration shape: SINGLE (one record per user) or MULTI (table of records). */
    Type type() default Type.SINGLE;

    /** Chat context scope: LIMITED (this endpoint only) or UNLIMITED (can navigate all endpoints). */
    Scope scope() default Scope.LIMITED;

    /** End-of-chat callback URL. Defaults to the workspace's defaultCallbackUrl when blank. */
    String callback() default "";

    enum Type {
        SINGLE,
        MULTI
    }

    enum Scope {
        LIMITED,
        UNLIMITED
    }

    /** Marks a controller method as a tool the chat agent can invoke during a session. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tool {
        /** Tool name. Defaults to the method name when blank. */
        String name() default "";
    }
}
