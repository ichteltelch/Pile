# `ReadListenValueString` — buffer family narrowed to `SealString`/`IndependentString`

Source folder: `src`. Package: `pile.specialized_String.combinations`.

Extends [`ReadValueString`](ReadValueString.md) and [`ReadListenValueComparable<String>`](../../specialized_Comparable/combinations/ReadListenValueComparable.md) *(pending)*. Overrides the entire buffer factory family to return String-specialized types instead of raw `SealPile<String>`/`Independent<String>`, so callers stay in the String-specialized world without casts. No new logic.

The overrides cover:
- `validBuffer` / `validBuffer_memo` / `validBufferBuilder` / `readOnlyValidBuffer` / `readOnlyValidBuffer_memo` / `readOnlyValidBufferBuilder` → `IndependentString` (via `PileString.ib().setupValidBuffer`)
- `buffer` / `bufferBuilder` / `readOnlyBuffer` / `readOnlyBufferBuilder` → `SealString` (via `PileString.sb().setupBuffer`)
- `rateLimited` / `rateLimitedBuilder` / `readOnlyRateLimited` / `readOnlyRateLimitedBuilder` → `SealString` (via `PileString.sb().setupRateLimited`)

Up: [combinations index](_index.md) · [String package index](../_index.md) · [overview](../../../overview.md).
