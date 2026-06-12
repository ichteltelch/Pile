# `pile.interop.preferences` — package index (Tier 1)

Source folder: `src`.

The bridge to `java.util.prefs.Preferences` (and synced files): the factories that back **remember-last-value** with a `Preferences` node, and reactive values that mirror persisted state.

Up: [interop index](../_index.md) · [overview](../../../overview.md). Remember-last-value model: [`RemembersLastValue`](../../aspect/RemembersLastValue.md).

## Types
- [`PrefInterop`](PrefInterop.md) — static factories building `LastValueRememberer`s and live `*preference` `Independent`s over a `java.util.prefs.Preferences` node, governed by a `NullBehavior` policy.
- [`PreferencesBackedValue`](PreferencesBackedValue.md) — a reactive, writable, listenable value mirroring a single `Preferences` entry in real time; always-valid and deliberately not a `Dependency`.
- [`SynchronizingFilesBackedValue`](SynchronizingFilesBackedValue.md) — a reactive, writable, listenable value backed by a set of mutually-synchronised, file-locked files; `AlwaysValid`, not a `Dependency`.
