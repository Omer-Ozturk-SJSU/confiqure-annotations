# Changelog

## 3.2.0

- `@Confiqure.Effect(OperationEffect)` on a tool operation, with `Confiqure.OperationEffect` `NONE` / `PAGE_OPENING`:
  a `PAGE_OPENING` operation opens a page of your application, and the engine does not offer it to a chat on a mobile
  client. Without the annotation an operation is `NONE`. Additive: nothing existing changes.
- Pushing it needs `@confiqure/cli` 1.2.0, and CLI 1.2.0 needs the engine that reads the effect: an older engine
  rejects the field 1.2.0 sends on every operation, so use both only once that engine is live. The mobile rule takes
  effect with it. An explicit `NONE` (sent for every operation without the annotation) clears an earlier `PAGE_OPENING`.

## 3.1.0

- Named validators `@Confiqure.Email`, `NAPhoneNumber`, `UKPhoneNumber`, `WorldPhoneNumber` and `USZipCode`.
- Host checks: the interfaces `ConfiqureValidator<T>` and `ConfiqureVerifier<T>`, implemented on a tool class and
  attached by `@Confiqure.ValidatedBy(X.class)` / `@Confiqure.VerifiedBy(Y.class)` (each with `message`); the wire
  types `Confiqure.Check<T>` and `Confiqure.Verdict<T>`. `VerifiedBy` also goes on a request class, checking the whole
  request before the call.
- Confiqure checks: `@Confiqure.Verify(ValidatorKind.ADDRESS)` and `ValidatorKind.PRODUCT_CODE` (GTIN-8/12/13/14,
  ISBN-10, ASIN).
- `@Confiqure.Confirm` on a tool operation; `Confirm(false)` turns its card off.
- The checks new in this release (`ValidatedBy` / `VerifiedBy` with a class, `Verify(ValidatorKind.PRODUCT_CODE)`) and
  `Confirm(false)` take effect with the next engine deploy. Until then the engine skips the new checks, and an
  operation marked `Confirm(false)` still shows its card.
- Pushing these needs `@confiqure/cli` 1.1.0. A host coming from CLI 0.5 moves its classes to the 3.0 vocabulary
  first (`Setting`/`List`/`User.*` on classes, `@Confiqure.Tool` on classes, not methods) and drops `confiqure tools
  set`: tool classes ship with every push.

Earlier releases are described in the repository's release commits.
