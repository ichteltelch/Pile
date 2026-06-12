# `ReadWriteListenValueBool` — writable buffer family narrowed to `SealBool`/`IndependentBool`

Source folder: `src`. Package: `pile.specialized_bool.combinations`.

Narrows [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) to `Boolean` by unioning [`ReadListenValueBool`](ReadListenValueBool.md) and [`ReadWriteValueBool`](ReadWriteValueBool.md), and overrides the buffer factory family to return the writable bool-specialized types (`SealBool`, `IndependentBool`). Also adds `setNull()` (returns `ReadWriteListenValueBool` for chaining). No new logic beyond type narrowing and `setNull`.

The overrides cover `validBuffer`/`validBuffer_memo`/`writableValidBuffer`/`writableValidBufferBuilder` (with optional `defer` argument), `buffer`/`writableBuffer`/`writableBufferBuilder`, `weakBuffer`/`writableWeakBuffer`/`writableWeakBufferBuilder`, and `rateLimited`/`writableRateLimited`/`writableRateLimitedBuilder`. Each delegates to the matching `PileBool.ib()` or `PileBool.sb()` builder setup method.

Up: [combinations index](_index.md) · [bool package index](../_index.md) · [overview](../../../overview.md).
