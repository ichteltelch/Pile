# `ReadWriteListenValueInt`

Narrows [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) to `Integer`, returning writable `SealInt` / `IndependentInt` from the buffer family; adds `setNull()`.

Source folder: `src` · package `pile.specialized_int.combinations`.

Extends `ReadListenValueInt`, `ReadWriteValueInt`, and `ReadWriteListenValueComparable<Integer>`. Overrides buffer factory methods to produce writable buffers: `buffer()`/`writableBuffer()` → `SealInt` (via `PileInt.sb().setupWritableBuffer`); `validBuffer()`/`writableValidBuffer()` / `writableValidBuffer_memo()` → `IndependentInt` (via `PileInt.ib().setupWritableValidBuffer`); `rateLimited(long, long)`/`writableRateLimited(long, long)` → `SealInt` (via `PileInt.sb().setupWritableRateLimited`). Each flavour has a corresponding `*Builder` method returning the typed builder for further configuration. The memo variant (`validBuffer_memo` / `writableValidBuffer_memo`) is cached via `WRITABLE_VALID_BUFFER_CACHE`; plain `writableValidBuffer()` always creates a new instance. `setNull()` is also narrowed to return `ReadWriteListenValueInt`.

See [combinations index](_index.md) · [overview](../../../overview.md).
