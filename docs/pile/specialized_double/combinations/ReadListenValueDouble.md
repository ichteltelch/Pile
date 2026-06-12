# `ReadListenValueDouble`

Narrows [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) to `Double`, binding the buffer/rate-limiter factory methods to `SealDouble`/`IndependentDouble`; no new arithmetic.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`ReadListenValueDouble extends ReadValueDouble, ReadListenValueComparable<Double>`.

## What it adds

All members are `@Override` narrowings of the generic buffer factory surface. They delegate to [`PileDouble`](../PileDouble.md) builders (`PileDouble.sb()`, `PileDouble.ib()`):

- `validBuffer()` / `readOnlyValidBuffer()` / `validBuffer_memo()` / `readOnlyValidBuffer_memo()` — return `IndependentDouble`; built via `PileDouble.ib().setupValidBuffer(this)`.
- `validBufferBuilder()` / `readOnlyValidBufferBuilder()` — return `IndependentBuilder<IndependentDouble, Double>`.
- `buffer()` / `readOnlyBuffer()` — return `SealDouble`; built via `PileDouble.sb().setupBuffer(this)`.
- `bufferBuilder()` / `readOnlyBufferBuilder()` — return `SealPileBuilder<SealDouble, Double>`.
- `rateLimited(coldStartTime, coolDownTime)` / `readOnlyRateLimited(...)` — return `SealDouble`.
- `rateLimitedBuilder(...)` / `readOnlyRateLimitedBuilder(...)` — return `SealPileBuilder<SealDouble, Double>`.

The `readOnly*` prefix names are explicit counterparts to the `writable*` variants in `ReadWriteListenValueDouble`.

## See also
- [ReadWriteListenValueDouble](ReadWriteListenValueDouble.md) — adds writable buffer variants
- [SealDouble](../SealDouble.md) · [IndependentDouble](../IndependentDouble.md)
- [combinations index](_index.md)
