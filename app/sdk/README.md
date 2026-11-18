# :app:sdk

The public plugin API. A plugin module (`plugins/*`) depends on this module only, never on `:app:runtime`.

It also re-exports `:app:ui` (design system) and `:core:protocol` (shared data types), so plugins get both for free.

See `docs/plugins/authoring.md` for a walkthrough (signing in to services so far).

Signed-in HTTP lives in `sdk/auth`: `context.auth.client(accountRef)` returns a Ktor client that handles tokens
for you, and `context.auth.state(accountRef)` says whether the account works.
