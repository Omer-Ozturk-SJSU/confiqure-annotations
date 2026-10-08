# confiqure-annotation-java

The `@Confiqure` annotation vocabulary for Java hosts (`ai.confiqure:confiqure-annotation-java`). The confiqure CLI
scans your source for these annotations and pushes the annotated classes; the chat model reads the class source and
follows it. The full vocabulary is documented on `ai.confiqure.Confiqure`.

## Validators and verifiers (3.1)

A comment guides the model; a declaration on the field is what the engine enforces.

- **Named validators** check a general format on the value itself: `@Confiqure.Email`, `@Confiqure.NAPhoneNumber`,
  `@Confiqure.UKPhoneNumber`, `@Confiqure.WorldPhoneNumber`, `@Confiqure.USZipCode`. Each takes an optional
  `message`, the sentence the user gets when a value is refused. More join over time.
- **Host verifiers** check a domain value only your application can judge: `@Confiqure.VerifiedBy("ToolClass.method")`
  names one of your tool operations. The engine calls it with the value before setting it; it answers ok, not ok with
  a message, or a corrected value. The name is checked at compile time.
- **Confiqure verifiers** need an outside answer Confiqure provides: `@Confiqure.Verify(Confiqure.ValidatorKind.ADDRESS)`
  checks an address and saves its corrected form.

Ranges and sizes (`@Min`, `@Max`, `@Size`) are not enforced by Confiqure: write them in the field's comment, and the
model follows them.

`@Confiqure.Confirm` on a tool operation that deletes, stops or cancels something makes the call run only with
`confirmed=true`. The engine sets it only from the user's click on the confirmation card it shows.
