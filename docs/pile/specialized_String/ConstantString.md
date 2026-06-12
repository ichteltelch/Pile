# `ConstantString`

A never-changing reactive `String` value — the String-typed specialization of [`ConstantComparable<String>`](../specialized_Comparable/_index.md), which itself extends [`Constant`](../impl/Constant.md).

Source folder: `src`. Package: `pile.specialized_String`.

## What it is

`ConstantString` is a thin typed wrapper around `ConstantComparable<String>`. Its entire body is a constructor and a single override. For all reactive semantics (always-valid, never-recomputed, always-consistent, silently ignores writes) see [Constant.md](../impl/Constant.md). For the Comparable layer (natural-ordering comparisons available on the value) see the [`specialized_Comparable` index](../specialized_Comparable/_index.md).

## Overrides

- **`setNull()`** — returns `this` unchanged (silent-ignore idiom: constants are immutable). This is idiomatic, not a bug.

## Factory access

`PileString.NULL` (= `Piles.constant((String)null)`) and `PileString.EMPTY` (= `Piles.EMPTY_STRING`) are the canonical `ConstantString` instances for the `null` and `""` cases; prefer those singletons over constructing new instances. See [`PileString.md`](PileString.md) for the constants, and [`Piles/_index.md`](../impl/Piles/_index.md) for `Piles.constant`.

## Caveats & gotchas

- `setNull()` is a silent no-op returning `this` — not an error, but callers expecting mutation are writing to the wrong type.
- Implements `ReadWriteListenDependencyString`, so it satisfies write-accepting interfaces structurally, but all writes are silently dropped.

See also: [_index.md](_index.md) · [overview](../../overview.md).
