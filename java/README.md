# confiqure-annotation-java

The `@Confiqure` annotation vocabulary for Java hosts (`ai.confiqure:confiqure-annotation-java`). The confiqure CLI
scans your source for these annotations and pushes the annotated classes; the chat model reads the class source and
follows it. The full vocabulary is documented on `ai.confiqure.Confiqure`.

## Validators and verifiers (3.1)

A comment guides the model; a declaration on the field is what the engine enforces.

- **Named validators** check a general format on the value itself: `@Confiqure.Email`, `@Confiqure.NAPhoneNumber`,
  `@Confiqure.UKPhoneNumber`, `@Confiqure.WorldPhoneNumber`, `@Confiqure.USZipCode`. Each takes an optional
  `message`, the sentence the user gets when a value is refused. More join over time.
- **Host checks** are your own, run by the engine without the chat model. A `@Confiqure.Tool` class implements
  `ConfiqureValidator<T>` (a format check: ok, or not ok with a message) or `ConfiqureVerifier<T>` (a domain check only
  your application can make: ok, not ok with a message, or a corrected value), with its `validate` / `verify` mapped
  like any operation. Attach it to a field with `@Confiqure.ValidatedBy(SkuFormat.class)` or
  `@Confiqure.VerifiedBy(CatalogCheck.class)`, each with an optional `message`. The engine POSTs
  `{field, value, confiqureKey?}` and reads back `Confiqure.Verdict` (`ok()`, `notOk(message)`, `corrected(value)`).
  The compiler checks the class is the right kind; the CLI checks at push that it is a pushed tool class with the
  method mapped. `@Confiqure.VerifiedBy` on a request class checks the whole request before the operation is called
  (`value` is the request object, `field` the class's simple name).

  ```java
  @Confiqure.Tool
  @RestController
  public class SkuFormat implements ConfiqureValidator<String> {
      @PostMapping("/checks/sku-format")
      public Confiqure.Verdict<Void> validate(@RequestBody Confiqure.Check<String> check) {
          return check.getValue().matches("[A-Z]{3}-\\d{4}")
              ? Confiqure.Verdict.ok()
              : Confiqure.Verdict.notOk("A SKU looks like ABC-1234.");
      }
  }
  ```
  These host checks, `Verify(ValidatorKind.PRODUCT_CODE)` and `Confirm(false)` take effect with the next engine deploy;
  until then the engine skips them.
- **Confiqure checks** run in the engine with no call to you, through `@Confiqure.Verify(...)`:
  - `ValidatorKind.ADDRESS` checks an address and saves its corrected form. The engine recognises the address's parts
    by these field names: street or addressLine1 or line1, addressLine2, city, state or region, postalCode or zip,
    country; a String field takes the whole address as one line.
  - `ValidatorKind.PRODUCT_CODE` accepts a GTIN-8, -12, -13 or -14 with a valid check digit (UPC-A, EAN-13 and ISBN-13
    are GTINs), an ISBN-10 with a valid check digit (`X` allowed last), or an ASIN (`B0` followed by 8 letters or
    digits); spaces and dashes are ignored.

Ranges and sizes (`@Min`, `@Max`, `@Size`) are not enforced by Confiqure: write them in the field's comment, and the
model follows them.

`@Confiqure.Confirm` on a tool operation that deletes, stops, pauses or cancels something, or changes something
live (a live price), makes the call run only with `confirmed=true`. The engine sets it only from the user's click
on the confirmation card it shows. `@Confiqure.Confirm(false)` turns the card off for that operation.

## Compiling a host

The annotation processor adds a `confiqureKey` to every `@Confiqure.Setting` / `List` class. It runs inside javac's own
JVM, so it needs these JVM options. In Maven, put them in the `maven-compiler-plugin`'s `compilerArgs` with
`<fork>true</fork>`, because the `-J` options reach only a forked javac:

```
-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
-J--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
```

Without them, the build fails on each object class with a message naming these options.
