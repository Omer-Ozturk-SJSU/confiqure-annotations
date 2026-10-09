package ai.confiqure;

/**
 * A format check your application makes on a field's value, run by the confiqure engine without
 * the chat model. Implement it on a {@link Confiqure.Tool} class, map {@link #validate} like any
 * other operation, and attach the class to a field with {@link Confiqure.ValidatedBy}.
 *
 * <p>Before the value is set, the engine POSTs {@code {field, value, confiqureKey?}} to the
 * mapped method. {@link Confiqure.Verdict#ok()} sets the value; {@link Confiqure.Verdict#notOk}
 * refuses it, and the user gets the field's declared message, else yours. A validator cannot
 * correct a value: that is a {@link ConfiqureVerifier}.
 *
 * <pre>
 * &#64;Confiqure.Tool
 * &#64;RestController
 * public class SkuFormat implements ConfiqureValidator&lt;String&gt; {
 *     &#64;PostMapping("/checks/sku-format")
 *     public Confiqure.Verdict&lt;Void&gt; validate(&#64;RequestBody Confiqure.Check&lt;String&gt; check) {
 *         return check.getValue().matches("[A-Z]{3}-\\d{4}")
 *             ? Confiqure.Verdict.ok()
 *             : Confiqure.Verdict.notOk("A SKU looks like ABC-1234.");
 *     }
 * }
 * </pre>
 *
 * @param <T> the field's type, as the engine sends its value
 * @since 3.1
 */
public interface ConfiqureValidator<T> {

    /** Checks one value. Answer {@link Confiqure.Verdict#ok()} or {@link Confiqure.Verdict#notOk}. */
    Confiqure.Verdict<Void> validate(Confiqure.Check<T> check);
}
