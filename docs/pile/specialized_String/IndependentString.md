# `IndependentString`

An always-valid, non-recomputing reactive `String` leaf — the String-typed specialization of [`IndependentComparable<String>`](../specialized_Comparable/_index.md), which itself extends [`Independent`](../impl/Independent.md).

Source folder: `src`. Package: `pile.specialized_String`.

## What it is

`IndependentString` mirrors `IndependentBool`/`IndependentComparable` for the `String` type. It is a directly writable, always-valid value with no recompute logic and no dependency graph participation as a dependee. For all reactive semantics see [Independent.md](../impl/Independent.md); for the Comparable layer see [`specialized_Comparable/_index.md`](../specialized_Comparable/_index.md).

## Overrides

- **`setName(String)`** — typed fluent override; returns `this` as `IndependentString` (for chaining without casting).
- **`setNull()`** — calls `set(null)` and returns `this` as `IndependentString`.

The commented-out `validBuffer()`/`validBuffer_memo()` overrides are vestiges — the `Independent` base already returns itself; the overrides were not needed and were removed.

## Builder

`PileString.ib()` / `PileString.ib(String init)` returns an `IndependentBuilder<IndependentString, String>` pre-configured with `Comparator.naturalOrder()`. Prefer the builder for configuration (name, initial value, ordering) over direct construction.

## Caveats & gotchas

- Invalidation attempts are silent no-ops (idiomatic `Independent` behavior — it is always valid).
- `setNull()` is a legitimate write (stores `null` as the value), unlike `ConstantString.setNull()` which is a silent ignore. Do not conflate the two.

See also: [_index.md](_index.md) · [overview](../../overview.md).
