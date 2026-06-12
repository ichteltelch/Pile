# `SealInt` — sealable integer; the redirect target of int operator factories

Thin int specialization of [`SealPile`](../impl/SealPile.md). A `SealInt` is a reactive integer that can be *sealed* — its value source redirected to another reactive value — and is the standard return type of arithmetic and comparison operator factories on `PileInt`/`ReadDependencyInt`. All sealing, redirect, and transaction semantics are inherited from `SealPile`; read that doc for the full contract.

Source folder: `src`. Package: `pile.specialized_int`.

Up: [int index](_index.md) · [overview](../../overview.md). Generic base: [`SealPile.md`](../impl/SealPile.md). Family exemplar: [`../specialized_bool/_index.md`](../specialized_bool/_index.md).

## Class hierarchy

`SealInt extends SealComparable<Integer> implements ReadWriteListenDependencyInt, PileInt`

`SealComparable<Integer>` adds natural-order comparator support between `SealPile<Integer>` and `SealInt`. `SealInt` additionally implements `PileInt`, making it a full participant in the int operator algebra — it can itself be the left-hand side of further arithmetic chains.

## Delta over `SealPile`

- **`setName(String)`** — covariant override; assigns `avName` directly and returns `this` (typed `SealInt`).
- **`setNull()`** — covariant override; calls `set(null)` and returns `this`.
- **Implements `PileInt`** — unlike `SealBool` which also implements `PileBool`, `SealInt` directly implements the `PileInt` interface (not just `ReadWriteListenDependencyInt`), giving it access to the static int operator factories and making it usable as an operand in further operator chains without casting.

## Role in the operator algebra

Every arithmetic and bijection factory (`plus`, `minus`, `times`, `integerDivide`, `remainder`, `modulo`, `negative`, `min`, `max`, `addRW`, `subtractRW`, `negativeRW`, `over`) returns a `SealInt`. The sealing mechanism allows the result to be *writable* for the bijection operators: writing into the `SealInt` returned by `negativeRW(x)` transparently writes the negated value back into `x`. Use `PileInt.sb()` / `PileInt.rb()` for the shorthand builders when you need a bare `SealInt` / `SealPile` pre-seeded with natural ordering.

## Caveats & gotchas

- **Unsealed `SealInt` has no value source** — before sealing, reads return `null` (or invalid depending on configuration). Do not expose an unsealed `SealInt` to consumers.
- **`PileInt.sb()` returns a `SealPileBuilder<SealInt>`** — a builder, not a ready `SealInt`; call `.build()` or configure further before use.
- **Sealed with a bijection → write-back** — operator factories that produce a writable result (e.g. `negativeRW`) call `Bijection.involution` / `Bijection.define` on the builder; the consumer writing into the returned `SealInt` writes through to the operand. Read [`SealPile.md`](../impl/SealPile.md) for the bijection/redirect mechanics.
