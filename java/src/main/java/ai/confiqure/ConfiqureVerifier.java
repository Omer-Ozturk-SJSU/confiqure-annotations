package ai.confiqure;

/**
 * A domain check only your application can make on a field's value (a product code exists, an
 * account is open), run by the confiqure engine without the chat model. Implement it on a
 * {@link Confiqure.Tool} class, map {@link #verify} like any other operation, and attach the class
 * to a field with {@link Confiqure.VerifiedBy}.
 *
 * <p>Before the value is set, the engine POSTs {@code {field, value, confiqureKey?}} to the
 * mapped method. {@link Confiqure.Verdict#ok()} sets the value as given;
 * {@link Confiqure.Verdict#notOk} refuses it, and the user gets the field's declared message, else
 * yours; {@link Confiqure.Verdict#corrected} sets your value instead and the user is told.
 *
 * <pre>
 * &#64;Confiqure.Tool
 * &#64;RestController
 * public class CatalogCheck implements ConfiqureVerifier&lt;String&gt; {
 *     &#64;PostMapping("/checks/catalog")
 *     public Confiqure.Verdict&lt;String&gt; verify(&#64;RequestBody Confiqure.Check&lt;String&gt; check) {
 *         String code = check.getValue().strip().toUpperCase();
 *         if (!catalog.exists(code)) return Confiqure.Verdict.notOk("No product has the code " + code + ".");
 *         return code.equals(check.getValue()) ? Confiqure.Verdict.ok() : Confiqure.Verdict.corrected(code);
 *     }
 * }
 * </pre>
 *
 * @param <T> the field's type, as the engine sends its value and takes a corrected one back
 * @since 3.1
 */
public interface ConfiqureVerifier<T> {

    /** Checks one value. Answer ok, not ok with a message, or a corrected value. */
    Confiqure.Verdict<T> verify(Confiqure.Check<T> check);
}
