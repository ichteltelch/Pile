# `PileInt` — `Pile<Integer>` specialized to `int`, with the arithmetic operator algebra

`PileInt` is the integer specialization of [`Pile<Integer>`](../aspect/combinations/Pile.md): same reactive value, plus a large catalogue of **static factories** that build derived reactive integers (arithmetic operators, sign predicates, sign-dispatch multiplexers, double-promotion, and n-ary aggregators). It adds **no new reactive semantics** — read [`Pile`](../aspect/combinations/Pile.md) / [`PileImpl`](../impl/PileImpl.md) for validity, transactions, recomputation; this doc is the *delta*.

Source folder: `src`. File: `src/pile/specialized_int/PileInt.java` (interface). Concrete implementation: `src/pile/specialized_int/PileIntImpl.java`.

Up: [int index](_index.md) · [overview](../../overview.md). See also: [generic `Pile`](../aspect/combinations/Pile.md) · [`PileImpl`](../impl/PileImpl.md) · [`Piles` aggregation](../impl/Piles/aggregation.md) · [Comparable layer](../specialized_Comparable/_index.md) · [bool exemplar](../specialized_bool/PileBool.md).

## Where each thing lives (the specialization split)

`PileInt extends Depender, ReadWriteListenDependencyInt, PileComparable<Integer>`. The operator surface is split across three roles:

- **Arithmetic instance methods** (`plus`/`minus`/`times`/`over`/`integerDivide`/`remainder`/`modulo`/`negative`/`min`/`max`/`choose`/`toDouble`/`readOnly`/`overridable`) are `default` methods on `ReadDependencyInt` (in `combinations/`). They delegate to the static factories on `PileInt`.
- **Comparison instance methods** (`lessThan`/`greaterThan`/`lessThanOrEqual`/`greaterThanOrEqual` → `SealBool`) are **inherited from the Comparable layer** (`ReadDependencyComparable`) via `PileComparable<Integer>`, not declared on `PileInt` or `ReadDependencyInt`. See [Comparable layer index](../specialized_Comparable/_index.md).
- **The static factory catalogue** lives on `PileInt` itself: every arithmetic op, sign predicate, aggregator, and builder shortcut is a `static` method here. The interface declares essentially no abstract instance methods of its own beyond `setNull()`.

So: to add reactively, call the instance method `a.plus(b)`; the algebra it routes to is the static `PileInt.add`.

## Arithmetic factories — the RO/RW split

All arithmetic factories return `SealInt`. The binary-operand overloads follow a consistent pattern:

### Two-reactive-operand forms
`add`, `subtract`, `multiply`, `integerDivide`, `remainder`, `modulo`, `min`, `max` each take two `ReadDependency<? extends Integer>` operands and route through `PileInt.binOp` → `Piles.makeBinOp`. **Any `null` operand makes the result `null`** — no NPE, but a silently-null output is easy to produce unintentionally.

- `remainder` uses Java `%` (may be negative for negative dividends).
- `modulo` is the **least non-negative residue**: `c = a%b; return c<0 ? c+b : c`. Prefer `modulo` over `remainder` when working with indices or wrapped values.
- `integerDivide`, `remainder`, `modulo` by a zero operand throw `ArithmeticException` inside the recompute lambda — unguarded, same as plain Java division.

### Constant-operand forms (int overloads)

Each op has constant-int overloads in all three arities (`op, value` / `value, op` / for those where it applies). These go through `mapToInt` rather than `makeBinOp`.

Two static names per op (`*RO` read-only, `*RW` write-back), plus an overloaded `add`/`subtract` that dispatches by operand type:

- `add(ReadDependency, int)` → delegates to `addRO`; `add(ReadWriteDependency, int)` → delegates to `addRW`.
- `addRW(op, value)` seals a **bijection** (`Bijection.define(o→o+value, o→o-value)`) via `bijectToInt`, so writing the result writes `result-value` back into the operand. Similarly `subtractRW(op, value)` calls `addRW(op, -value)`, and `subtractRW(value, op)` uses `Bijection.involution(o→value-o)`.
- `multiply(op, int)`, `integerDivide(op, int)`, `remainder(op, int)`, `modulo(op, int)` — constant-operand only, no write-back variants (multiplication/division are not generally bijective).

### `negative` — writable negation

`negative(ReadWriteDependency)` → delegates to `negativeRW`: seals a bijection (`v → -v`) so writing the result writes the negated value back. `negative(ReadDependency)` → delegates to `negativeRO`: read-only. Operand and result are `null`-safe (`null`→`null`).

### `min` / `max` — constant-int overloads

`min(op, int)` / `max(op, int)` are read-only (`mapToInt` based), with no write-back form.

## Double-promotion — `toDouble` and `over`

These live on `ReadDependencyInt` (combinations layer), not on `PileInt` itself:

- `toDouble()` — promotes the reactive int to a `SealDouble`.
- `over(ReadDependency<? extends Number>)` / `over(double)` / `over(int)` — **always floating-point division** (routes to `PileDouble.divide`); returns `SealDouble`. There is **no integer `over`** — use `integerDivide` for truncating int quotient.
- `plus(ReadDependencyDouble)` / `minus(ReadDependencyDouble)` / `times(ReadDependencyDouble)` / `min(ReadDependencyDouble)` / `max(ReadDependencyDouble)` — mixed int×double overloads, all return `SealDouble`.
- `plus(double)` / `minus(double)` / `times(double)` / `over(double)` — constant-double overloads, also return `SealDouble`.

## Sign predicates — `isPositive` / `isZero` / …

Six static factories on `PileInt`, all `mapToBool`, all `null`→`null`:

| Method | Condition |
|---|---|
| `isPositive(op)` | `v > 0` |
| `isNegative(op)` | `v < 0` |
| `isNonPositive(op)` | `v <= 0` |
| `isNonNegative(op)` | `v >= 0` |
| `isZero(op)` | `v == 0` |
| `isNonZero(op)` | `v != 0` |

Note: `isPositive` and `isNonPositive` are **not complementary** when the value is `null` — both return `null`. Use explicit null-handling if you need a definite boolean.

Also: `signum(value)` — returns a `ReadListenDependencyInt` that is `+1`, `0`, or `-1` (via `(int)Math.signum(v.doubleValue())`), `null`→`null`.

## Four-way sign `choose` — sign-dispatch multiplexer

The int analogue of `PileBool`'s two-way `choose`: dispatches on **sign and nullity** of a reactive integer chooser across four branches (`ifNeg`, `ifZero`, `ifPos`, `ifNull`). Three static primitives on `PileInt`:

- `_choose(chooser, ifNeg, ifZero, ifPos, ifNull, template)` — read-only; uses `dynamicDependencies()` so only the active branch is tracked. If `ifNull` is `null`, substitutes `Piles.constNull()`. Chooser invalid → `fulfillRetry()` (waits rather than committing a wrong branch).
- `_chooseWritable(chooser, ifNeg, ifZero, ifPos, ifNull, template)` — bidirectional: writes to the result are forwarded to the currently-active branch via the builder's `seal` redirect. The write-back re-checks `chooser` at write time; if chooser is invalid, the write is silently dropped.
- `_chooseConst(chooser, ifNeg, ifZero, ifPos, ifNull, template)` — constant branches (`E` values, not reactive).

The `_` prefix marks these as "take an explicit template" primitives. The `default` instance methods `choose`/`chooseWritable`/`chooseConst` on `ReadDependencyInt` call these with the appropriate typed template. Typed return convenience forms (`chooseInt`/`chooseBool`/`chooseDouble`/`chooseString`/`chooseWritableInt`/…) pass the matching `SealInt`/`SealBool`/… template.

Note: **writes to `_chooseWritable` hit only the currently active branch** — switching the chooser's sign redirects future writes elsewhere; the previously-written branch is not adjusted.

## `comparison` — reactive comparator-result as integer

Six static overloads of `comparison` on `PileInt`, returning `SealInt` whose value is the `compareTo`/`Comparator.compare` result (negative/zero/positive). Arities: reactive×reactive, const×reactive, reactive×const; comparator source: natural ordering (with `Boolean nullIsLess`) or explicit `Comparator`. These are distinct from the boolean comparisons inherited from `PileComparable` — `comparison` returns a `SealInt` three-value signal, not a `SealBool`.

## `IntAggregator` monoids — routing through `Piles.aggregate`

`IntAggregator` is a static nested class inside `PileInt` that implements `Piles.AggregationMonoid<Integer, ReadListenDependencyInt>`. It wraps a neutral element and a binary operator; its `inject` method calls `readOnlyWrapperIdempotent` to normalize inputs (returns the input unchanged if it is already a default-sealed `SealInt` or a `ConstantInt`, otherwise wraps it). The neutral element is resolved against the shared constants `Piles.ZERO_I`, `Piles.ONE_I`, `Piles.MAX_VALUE_I`, `Piles.MIN_VALUE_I`, `Piles.NULL_I` — falling back to `Piles.sealedConstant(neutral)` for arbitrary values.

Four pre-built monoid constants on `PileInt`:

| Constant | Neutral | Operator |
|---|---|---|
| `sumAggregator` | `0` (`Piles.ZERO_I`) | `PileInt::add` |
| `productAggregator` | `1` (`Piles.ONE_I`) | `PileInt::multiply` |
| `minAggregator` | `Integer.MAX_VALUE` (`Piles.MAX_VALUE_I`) | `PileInt::min` |
| `maxAggregator` | `Integer.MIN_VALUE` (`Piles.MIN_VALUE_I`) | `PileInt::max` |

Four n-ary reducers (each with `Iterable` and varargs overloads): `sum`, `product`, `min`, `max` — each calls `Piles.aggregate(monoid, items)`, which builds a balanced binary tree of pairwise ops (O(log n) propagation depth). See [aggregation.md](../impl/Piles/aggregation.md) for the fold algorithm and `AggregationMonoid` contract.

## Comparisons — inherited, not redeclared

The boolean comparison operators (`lessThan`/`greaterThan`/`lessThanOrEqual`/`greaterThanOrEqual`) and their `*Const` variants are **not declared on `PileInt` or `ReadDependencyInt`**. They are inherited from the Comparable layer (`ReadDependencyComparable`) via `PileComparable<Integer>`. Each returns a `SealBool`. See [Comparable layer index](../specialized_Comparable/_index.md). Do not look for them on `PileInt`; they live upstream.

## Builder shortcuts and wrappers

- `rb()` — `PileBuilder<PileIntImpl, Integer>`, pre-seeded with `Comparator.naturalOrder()`.
- `sb()` — `SealPileBuilder<SealInt, Integer>`, pre-seeded with natural order.
- `ib()` / `ib(Integer init)` — `IndependentBuilder<IndependentInt, Integer>`, null or given initial value, natural order.
- `readOnlyWrapper(in)` — wraps any `ReadDependency<? extends Integer>` in a new `SealInt` that recomputes from it; always default-sealed.
- `readOnlyWrapperIdempotent(in)` — returns the input unchanged if it is already a default-sealed `SealInt` or a `ConstantInt`; otherwise delegates to `readOnlyWrapper`. Used internally by `IntAggregator.inject`.
- `binOp(op1, op2, ToIntBiFunction)` / `binOp(op1, op2, BiFunction<…,Integer>)` — generic escape hatch: build any `SealInt` from two reactive values and a custom combining function, via `Piles.makeBinOp`.

## Caveats & gotchas

- **`null` propagates through every arithmetic op** — if any operand is `null`, the result is `null`. No NPE, but easy to get a silently-null pipeline. Guard with an explicit null check or use `isNonNull` before computing.
- **`over` is always floating-point** — `intA.over(intB)` returns `SealDouble`. Use `integerDivide` for truncating integer quotient.
- **`remainder` vs `modulo`** — `remainder` follows Java `%` and can be negative for negative dividends; `modulo` always returns the least non-negative residue. Wrong choice produces sign bugs on negative inputs.
- **Division by zero is unguarded** — `integerDivide`, `remainder`, `modulo` by a reactive zero will throw `ArithmeticException` inside the recompute, which the reactive framework will propagate as an invalid value (see `PileImpl` error handling). Guard the divisor if zero is possible.
- **`getAsInt()` is not on reactive values** — only `MutInt` (non-reactive box) has `getAsInt()`; reactive reads return boxed `Integer` via `get()`. Null-check the result.
- **`isPositive` and `isNonPositive` are not complementary** — both return `null` when the value is `null`. Same applies to the other complementary pairs.
- **Writes to `chooseWritable` hit only the active branch** — switching the chooser's sign does not retroactively update the previously-written branch.
- **`PileInt.addRO` bug (PB-38)** — the lambda in `addRO(ReadDependency, int)` is `o->o==null?null:+value`, which uses unary-plus on the constant and **drops the operand `o` entirely**. Every call to `addRO` (and to `add(ReadDependency, int)`, which delegates to it, and to `subtractRO` which calls `addRO(-value)`) returns a constant rather than a sum. Use `addRW` / `plus(int)` on a `ReadWriteDependency`, or call `subtractRW(value, op)` for the symmetric case, as a workaround until PB-38 is fixed.

## Tech debt / warts

- **`addRO` operand-dropping bug (PB-38)** — as described above; `add(ReadDependency, int)` and `subtractRO(op, int)` are silently broken for read-only operands.
- **`comparison` result is an int, not a bool** — the six `comparison` overloads return a `SealInt` three-valued signal. This is by design (you can threshold it), but it is easy to confuse with the inherited boolean comparisons. The naming (`comparison` vs `lessThan`/`greaterThan`) is the only distinction.
- The `_choose` / `_chooseWritable` / `_chooseConst` primitives take a `template` argument rather than constructing it internally — consistent with the `PileBool` pattern but makes the call site more verbose than the typed convenience wrappers.
- No `abs` / `clamp` / `bounds` operators; these are absent from the package. Compose from `max`/`min` or a `binOp` lambda.
