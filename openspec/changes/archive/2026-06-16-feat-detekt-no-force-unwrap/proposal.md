# Feat: add NoForceUnwrap detekt rule

## Why

The codebase enforces FC/IS at the type level — every fallible
operation is supposed to return a typed value (`Outcome<T>`, sealed
`DomainError`) rather than throw. The audit's "FP cleanups" item
called out that the codestyle has no automated guard against
`!!` (force-unwrap), which bypasses the type system and throws
NPE at runtime.

The one place a `!!` survived in production code was
`SearchViewModel.ensureCatalogLoaded()` — a benign-looking use that
just so happens to be safe today, but a future refactor could break
the invariant. The rule catches that future break.

## Scope

- New: `testing/detekt-rules/.../NoForceUnwrap.kt`. Visits
  `KtPostfixExpression` and reports any `!!` use.
- New: `NoForceUnwrapTest.kt` (3 cases: single, multiple, none).
  Uses `dev.detekt.test.lint` to assert findings.
- Register the rule in `AppRuleSetProvider` alongside
  `NoFullyQualifiedNames`.
- Activate the rule in `config/detekt.yml` and exclude all test
  source sets via detekt's `excludes` (production code only;
  tests still use `!!` to assert preconditions).
- Catalog: add `junit-platform-launcher` alias needed by the new
  JUnit5 test setup.
- SearchViewModel: replace `catalogDeferred!!.await()` with
  `requireNotNull(catalogDeferred) { ... }.await()` to clear the
  existing finding.

## Out of scope

- A "no non-null assertion (`!!`) and no implicit unwrap of nullable
  Kotlin types" — too aggressive; not part of the audit.
- Migration of all test `!!` to `.also { checkNotNull(it) }`. Tests
  are explicitly excluded.

## Risk

- The rule excludes all test source sets; no test file is touched
  by detekt.
- The `SearchViewModel` change is local; the local invariant
  (catalogDeferred is non-null because we just set it) is preserved
  by the `requireNotNull` message.
- The test sources now need JUnit Platform; adding the launcher
  dep is the standard fix and matches the existing `core/media` setup.
