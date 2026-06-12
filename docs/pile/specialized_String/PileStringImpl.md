# `PileStringImpl`

The default full reactive `String` — the String-typed specialization of [`PileComparableImpl<String>`](../specialized_Comparable/_index.md), which itself extends [`PileImpl`](../impl/PileImpl.md).

Source folder: `src`. Package: `pile.specialized_String`.

## What it is

`PileStringImpl` is the concrete class for reactive strings that participate fully in the dependency graph: recomputation, transactions, validity, dependency tracking. Its entire body is two typed overrides and the `PileString` interface tag. For all reactive semantics see [PileImpl.md](../impl/PileImpl.md); for the Comparable layer see [`specialized_Comparable/_index.md`](../specialized_Comparable/_index.md).

## Overrides

- **`setName(String)`** — assigns `avName` directly and returns `this` as `PileStringImpl` (typed fluent chaining).
- **`setNull()`** — calls `set(null)` and returns `this` as `PileStringImpl`.

## Builder

`PileString.rb()` returns a `PileBuilder<PileStringImpl, String>` pre-configured with `Comparator.naturalOrder()`. Use this builder to attach recompute lambdas, dependencies, and other configuration rather than constructing `PileStringImpl` directly.

## Caveats & gotchas

- No string-specific behavior beyond typed chaining. For concatenation of two reactive strings, use `Piles.concatStrings` (result is a `SealString`, not a `PileStringImpl`). See [`Piles/_index.md`](../impl/Piles/_index.md).
- `PileStringImpl` implements `PileString` (the capstone interface), so it satisfies all `PileString`-typed parameters.

See also: [_index.md](_index.md) · [overview](../../overview.md).
