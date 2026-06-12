# `ReadWriteListenValueString` — writable buffer family narrowed to `SealString`/`IndependentString`

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadListenValueString`](ReadListenValueString.md), [`ReadWriteValueString`](ReadWriteValueString.md), and [`ReadWriteListenValueComparable<String>`](../../specialized_Comparable/combinations/ReadWriteListenValueComparable.md) *(pending)*. Overrides the writable buffer factory family to return String-specialized types, and narrows `setNull()` to return `ReadWriteListenValueString`.

The writable buffer overrides cover:
- `validBuffer` / `validBuffer_memo` / `validBufferBuilder` / `writableValidBuffer` / `writableValidBuffer_memo` / `writableValidBufferBuilder` (with and without a `defer` parameter) → `IndependentString` (via `PileString.ib().setupWritableValidBuffer`)
- `buffer` / `bufferBuilder` / `writableBuffer` / `writableBufferBuilder` → `SealString` (via `PileString.sb().setupWritableBuffer`)
- `rateLimited` / `rateLimitedBuilder` / `writableRateLimited` / `writableRateLimitedBuilder` → `SealString` (via `PileString.sb().setupWritableRateLimited`)

No new logic beyond type narrowing and `setNull()` covariance.

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
