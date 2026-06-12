# `JustReadValueBool` — `@FunctionalInterface` narrow of `JustReadValue` to `Boolean`

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Pure assembly; narrows [`JustReadValue`](../../aspect/JustReadValue.md) (and by extension `ReadValueBool`) to `Boolean`; marks the interface `@FunctionalInterface`. No new members beyond what `ReadValueBool` already declares — the functional contract is simply a `Boolean`-returning `get()`.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
