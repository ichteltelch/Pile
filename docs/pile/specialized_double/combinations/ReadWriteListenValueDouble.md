# `ReadWriteListenValueDouble`

Combines `ReadListenValueDouble` and `ReadWriteValueDouble`, narrowing the buffer factory methods to writable `SealDouble`/`IndependentDouble` variants.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadWriteListenValueDouble extends ReadListenValueDouble, ReadWriteValueDouble, ReadWriteListenValueComparable<Double>`.

## What it adds

All members override the generic buffer surface to return double-typed writable buffers via `PileDouble.sb()` / `PileDouble.ib()`:

- `validBuffer()` / `writableValidBuffer()` / `validBuffer_memo()` / `writableValidBuffer_memo()` — return `IndependentDouble`.
- `validBufferBuilder()` / `writableValidBufferBuilder()` — return `IndependentBuilder<IndependentDouble, Double>`.
- `writableValidBuffer(Function<Consumer<? super Double>, Consumer<? super Double>> defer)` — deferred-write variant.
- `writableValidBufferBuilder(defer)` — builder for deferred-write valid buffer.
- `buffer()` / `writableBuffer()` — return `SealDouble`.
- `bufferBuilder()` / `writableBufferBuilder()` — return `SealPileBuilder<SealDouble, Double>`.
- `rateLimited(cold, cool)` / `writableRateLimited(...)` — return `SealDouble`.
- `rateLimitedBuilder(...)` / `writableRateLimitedBuilder(...)` — return `SealPileBuilder<SealDouble, Double>`.
- `setNull()` — narrows to `ReadWriteListenValueDouble`.

## See also
- [ReadListenValueDouble](ReadListenValueDouble.md) — the read-only buffer surface
- [ReadWriteListenDependencyDouble](ReadWriteListenDependencyDouble.md) — the full contract
- [combinations index](_index.md)
