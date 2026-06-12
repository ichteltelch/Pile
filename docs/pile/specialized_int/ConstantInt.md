# `ConstantInt` — immutable integer constant; always valid, silently ignores writes

Thin int specialization of [`Constant`](../impl/Constant.md). A `ConstantInt` holds a fixed `Integer` value set at construction time; it never changes, never participates in the dependency graph as a dependent, and is always valid. All reactive behavior is inherited unchanged — read [`Constant.md`](../impl/Constant.md) for the full contract.

Source folder: `src`. Package: `pile.specialized_int`.

Up: [int index](_index.md) · [overview](../../overview.md). Generic base: [`Constant.md`](../impl/Constant.md). Family exemplar: [`../specialized_bool/_index.md`](../specialized_bool/_index.md).

## Class hierarchy

`ConstantInt extends ConstantComparable<Integer> implements ReadWriteListenDependencyInt`

The only two levels between `ConstantInt` and the generic `Constant` are `ConstantComparable<Integer>` (adds natural-order comparator) and `ConstantInt` itself. There is no additional logic at the int level.

## Delta over `Constant`

- **`setNull()` override** — returns `this` (covariant, no-op). Constants are immutable and unnamed; `setNull` is silently ignored as per the Pile silent-ignore idiom — not a bug.
- **`ReadWriteListenDependencyInt`** — implements the full combination interface, so a `ConstantInt` can be used anywhere a `ReadWriteListenDependencyInt` is expected (the write side is a no-op).

## Constructor

`ConstantInt(Integer init)` — pass `null` for a constant null-valued integer. There is no zero-arg constructor; use `Piles.NULL_I`, `Piles.ZERO_I`, `Piles.ONE_I`, `Piles.MAX_VALUE_I`, `Piles.MIN_VALUE_I` for the shared flyweight constants (see the [int index](_index.md) § Routing & aggregation).

## Caveats & gotchas

- **No `setName`** — `Constant` does not expose a fluent `setName`; assigning a name is not meaningful for a constant. If you need a named integer constant, wrap it in `IndependentInt` initialized once.
- **Writes are silently ignored** — neither `set(v)` nor `setNull()` have any effect. This is intentional and idiomatic; do not treat missing feedback as a bug.
- **`getAsInt()` is not available** — constants implement the reactive interface, which returns boxed `Integer` via `get()`. The primitive accessor `getAsInt()` lives only on `MutInt`. Use `get()` and null-check, or `threeWay`/`isTrue`-style methods if you need a default-on-null pattern.
