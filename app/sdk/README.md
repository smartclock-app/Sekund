# :app:sdk

The public plugin API. A plugin module (`plugins/*`) depends on this module only, never on `:app:runtime`.

It also re-exports `:app:ui` (design system) and `:core:protocol` (shared data types), so plugins get both for free.

See `docs/plugins/authoring.md` (written in a later phase) for a walkthrough.
