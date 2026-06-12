# `ReadListenValueBool` — buffer family narrowed to `SealBool`/`IndependentBool`

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) to `Boolean` and overrides the entire buffer factory family to return bool-specialized types (`SealBool`, `IndependentBool`) instead of raw `SealPile`/`Independent`. No new logic — purely type narrowing of the buffer surface so callers stay in the primitive-specialized world without casts.

The overrides cover `validBuffer`/`validBuffer_memo`/`validBufferBuilder`, `buffer`/`bufferBuilder`, `weakBuffer`/`weakBufferBuilder`, and `rateLimited`/`rateLimitedBuilder` (plus their `readOnly*` variants). Each delegates to the corresponding `PileBool.ib()` or `PileBool.sb()` builder method.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
