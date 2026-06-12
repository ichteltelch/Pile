# `PileIntImpl` — default full reactive integer; `PileImpl<Integer>` + `PileInt`

`PileIntImpl` is the standard reactive integer node: it supports recomputation, dependency tracking, transactions, and the full int operator algebra. All reactive behavior is inherited from [`PileImpl`](../impl/PileImpl.md); this class adds only covariant overrides and the `PileInt` interface. Read [`PileImpl.md`](../impl/PileImpl.md) for the full contract.

Source folder: `src`. Package: `pile.specialized_int`.

Up: [int index](_index.md) · [overview](../../overview.md). Generic base: [`PileImpl.md`](../impl/PileImpl.md). Int operator algebra: the [int index](_index.md) § Int-specific surface. Bool analogue pattern: [`../specialized_bool/PileBool.md`](../specialized_bool/PileBool.md) § PileBoolImpl.

## Class hierarchy

`PileIntImpl extends PileComparableImpl<Integer> implements PileInt`

`PileComparableImpl<Integer>` (between `PileImpl<Integer>` and `PileIntImpl`) adds natural-order comparator support for ordering and change-detection. `PileIntImpl` itself is only five lines of code.

## Delta over `PileImpl`

- **`setName(String)`** — covariant override; assigns `avName` directly, returns `this` (typed `PileIntImpl`).
- **`setNull()`** — covariant override; calls `set(null)`, returns `this`.
- **`PileInt`** — implements the full int operator interface, making `PileIntImpl` a legal left-hand operand in arithmetic chains (e.g., `myPile.plus(other)` returns a `SealInt`).
- No memoized operator overrides — unlike `PileBoolImpl.not()`, `PileIntImpl` does **not** override any operator to add memoization. Operator calls produce a fresh `SealInt` on each invocation (memoization, if desired, must be managed externally).

## Lifecycle and builders

Use `PileInt.rb()` to obtain a `PileBuilder<PileIntImpl>` pre-seeded with `Comparator.naturalOrder()`. Configure recompute, dependencies, and ordering through the builder, then call `.build()`. Direct instantiation (`new PileIntImpl()`) is valid but requires manual setup of the recompute and sealing steps.

`PileInt.readOnly(value)` / `PileInt.readOnlyWrapper(value)` / `PileInt.overridable(value)` produce int-typed wrappers that narrow to `PileIntImpl`; see the [int index](_index.md) § Builders & wrappers.

## Caveats & gotchas

- **No primitive accessor** — `get()` returns boxed `Integer`; `getAsInt()` is not present on `PileIntImpl` (it exists only on `MutInt`). Handle `null` from `get()` explicitly.
- **`null` propagation in operators** — every arithmetic operator built from `PileIntImpl` as an operand will produce `null` if `this` is `null`; there is no implicit zero-default.
- **Integer division** — `integerDivide` and `remainder`/`modulo` by a reactive operand that becomes `0` will throw `ArithmeticException` inside the recompute lambda; there is no guard. Protect with a `choose`/conditional if a zero denominator is possible.
- **`over` is floating-point** — `myPileIntImpl.over(other)` returns a `SealDouble`, not an integer quotient. Use `integerDivide` for truncating integer division.
- **No reactive semantics of its own** — every validity, transaction, and propagation behavior comes from `PileImpl`. If behavior is surprising, the answer is in [`PileImpl.md`](../impl/PileImpl.md), not here.
