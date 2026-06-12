# `PileDoubleImpl`

Default full reactive `double` — the go-to concrete impl for recomputing, dependency-tracked double values.

Source folder: `src`. Package: `pile.specialized_double`.

Up: [_index.md](_index.md) · [overview](../../overview.md). Generic counterpart: [../impl/PileImpl.md](../impl/PileImpl.md). Operator surface: [_index.md](_index.md) § The double-specific operator surface.

## What it is

`PileDoubleImpl` extends `PileComparableImpl<Double>` and implements `PileDouble`. It is the full-featured reactive double: recomputes on dependency change, participates in transactions, supports bracketing, validity, listeners, and sealing. It is the concrete type you get from `PileDouble.rb().build()` (via `PileBuilder` pre-seeded with `Comparator.naturalOrder()`).

Because it implements `PileDouble`, all arithmetic operators (`plus`, `times`, `over`, `negative`, `min`, `max`, comparisons, aggregators, …) are available as instance methods — see [_index.md](_index.md) for the full surface.

## Delta over the generic

The body adds only two typed overrides:
- `setName(String)` — writes `avName` directly and returns `this` (typed `PileDoubleImpl`).
- `setNull()` — calls `set(null)` and returns `this`.

All reactive behavior — recompute scheduling, dependency tracking, validity, transactions, change listeners — lives entirely in `PileComparableImpl` / `PileImpl`. `PileDoubleImpl` contributes only the typed surface narrowing and `PileDouble` interface implementation.

## Common tasks

- Create via builder: `PileDouble.rb().recomputeS(supplier).build()` — returns a `PileDoubleImpl`.
- Reactive arithmetic: `impl.plus(other)` / `impl.times(2.0)` — returns `SealDouble`.
- Reactive comparison: `impl.greaterThan(other, nullIsLess)` — returns `SealBool`.
- Aggregation: `PileDouble.sum(collection)` — see [_index.md](_index.md) § Aggregation.

## Caveats & gotchas

- The arithmetic operators return `SealDouble`, not `PileDoubleImpl` — operator chaining stays in `SealDouble` land. Only the root value is a `PileDoubleImpl`.
- `null` is the third state (invalid); arithmetic lambdas short-circuit to `null` when any operand is `null`.
