# `SealString`

A sealable reactive `String` — the String-typed specialization of [`SealComparable<String>`](../specialized_Comparable/_index.md), which itself extends `SealPile<String>` (see [SealPile.md](../impl/SealPile.md)).

Source folder: `src`. Package: `pile.specialized_String`.

## What it is

`SealString` is the primary output type of the `String` factory family: `PileString.sb()` builds one, `Piles.concatStrings` returns one, `PileString.nullableWrapper` returns one, and `PileString.readOnlyWrapper` returns one. It implements both `ReadWriteListenDependencyString` and `PileString`, making it the richest concrete String type in terms of interface coverage.

For sealing semantics (a seal handler intercepts writes and can redirect them to a backing value) see [SealPile.md](../impl/SealPile.md). For the Comparable layer see [`specialized_Comparable/_index.md`](../specialized_Comparable/_index.md).

## Overrides

- **`setName(String)`** — assigns `avName` directly and returns `this` as `SealString` (typed fluent chaining).
- **`setNull()`** — calls `set(null)` and returns `this` as `SealString`.

The class body is otherwise empty — all reactive behavior is inherited.

## Common roles

- **Read-only wrapper**: `PileString.readOnlyWrapper(in)` / `readOnlyWrapperIdempotent(in)` create a `SealString` sealed in its default (pass-through-reads-only) state.
- **Concat result**: `Piles.concatStrings(op1, op2)` returns a `SealString` whose value is the concatenated string of the two operands.
- **Nullable wrapper**: `PileString.nullableWrapper(back)` returns a `SealString` that encodes `null` as `""` in the backing value.
- **Builder output**: `PileString.sb()` returns a `SealPileBuilder<SealString, String>` for building custom recomputing sealed strings.

## Caveats & gotchas

- `SealString` implements `PileString` (the full capstone interface), while the other concretes (`ConstantString`, `IndependentString`, `PileStringImpl`) do not — they implement `ReadWriteListenDependencyString` only. This means `SealString` satisfies `PileString`-typed parameters directly.
- A freshly constructed `SealString` (via `new SealString()`) is unsealed with no recompute logic — it is essentially a writable value with no auto-update until a builder or factory configures it.

See also: [_index.md](_index.md) · [Piles/_index.md](../impl/Piles/_index.md) · [overview](../../overview.md).
