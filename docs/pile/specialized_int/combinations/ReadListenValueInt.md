# `ReadListenValueInt`

Narrows [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) to `Integer`, returning `SealInt` and `IndependentInt` from the buffer family; adds no new logic.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadValueInt` and `ReadListenValueComparable<Integer>`. Overrides the buffer factory methods to narrow return types: `buffer()`/`readOnlyBuffer()` → `SealInt` (via `SealPileBuilder<SealInt, Integer>`); `validBuffer()`/`readOnlyValidBuffer()`/`validBuffer_memo()` → `IndependentInt` (via `IndependentBuilder<IndependentInt, Integer>`); `rateLimited(long, long)` → `SealInt`. All builder wiring delegates to `PileInt.sb()` and `PileInt.ib()`. The memo variant (`validBuffer_memo` / `readOnlyValidBuffer_memo`) is cached per-source via `READ_ONLY_VALID_BUFFER_CACHE`; plain `validBuffer()` always creates a new instance.

See [combinations index](_index.md) · [overview](../../../overview.md).
