# Changelog

## 3.1.0

- Named validators `@Confiqure.Email`, `NAPhoneNumber`, `UKPhoneNumber`, `WorldPhoneNumber` and `USZipCode`; the
  host verifier `@Confiqure.VerifiedBy("ToolClass.method")`, its name checked at compile time; the Confiqure verifier
  `@Confiqure.Verify(ValidatorKind.ADDRESS)`; and `@Confiqure.Confirm` on a tool operation.

Earlier releases are described in the repository's release commits.
