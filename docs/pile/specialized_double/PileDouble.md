# `PileDouble` — `Pile<Double>` specialized to `double`, with the arithmetic operator algebra

`PileDouble` is the double-precision specialization of [`Pile<Double>`](../aspect/combinations/Pile.md): same reactive value semantics, plus primitive-`double` accessors and a large catalogue of **static factories** that build derived reactive doubles (arithmetic operators, aggregations, dynamic monoids, a writable average). It adds **no new reactive semantics** — read [`Pile`](../aspect/combinations/Pile.md) / [`PileImpl`](../impl/PileImpl.md) for validity, transactions, and recomputation; this doc is the *delta*.

Source folder: `src`. File: `src/pile/specialized_double/PileDouble.java` (interface). Concrete implementation: `PileDoubleImpl` (`PileImpl<Double>` + `PileDouble`).

Up: [double index](_index.md) · [overview](../../overview.md). See also: [generic `Pile`](../aspect/combinations/Pile.md) · [`PileImpl`](../impl/PileImpl.md) · [bool exemplar](../specialized_bool/PileBool.md) · [int family](../specialized_int/_index.md) · [`Piles` aggregation](../impl/Piles/aggregation.md) · [Comparable layer](../specialized_Comparable/_index.md).

## Where each thing lives (the specialization split)

`PileDouble extends Depender, ReadWriteListenDependencyDouble, PileComparable<Double>`. Instance entry points for arithmetic (`plus`, `minus`, `times`, `over`, …) live on `ReadDependencyDouble` (read side) and `ReadWriteListenDependencyDouble` (write side) as thin `default` methods that **delegate to the static factories on `PileDouble`**. The static factory catalogue itself — every operator, aggregator, and builder shortcut — lives here on `PileDouble`.

So: to add two reactive doubles you call `a.plus(b)` on the instance; the algebra it routes to is `PileDouble.add(a, b)`.

Primitive read accessor specific to double: `ReadValueDouble` adds `getF()` (boxed `Float`, lossy, null-preserving). The boxed `Double get()` comes from generic `ReadValue`. There is no reactive `getAsDouble()`.

## The arithmetic operator catalogue (static factories)

All operators are `static` on `PileDouble` and return a sealed [`SealDouble`](_index.md). The `RW` forms install a `Bijection` so writes propagate back to the mutable operand; the `RO` forms seal without a write-back. Overload resolution on the static name picks `RW` when the first operand is `ReadWriteDependency<Double>`, `RO` otherwise.

### Unary operators

- `negativeRO(ReadDependency)` / `negativeRW(ReadWriteDependency)` — reactive negation. `negativeRW` seals with write-back: writing `-v` sets the source to `-v` (an involution). The dispatch overloads `negative(ReadDependency)` / `negative(ReadWriteDependency)` select the appropriate variant by type.
- `inverseRO(ReadDependency)` / `inverseRW(ReadWriteDependency)` — intended reactive reciprocal. **See PB-39 under warts** — the recompute is wrong in both forms.

### Binary value-op-value

- `add(op1, op2)`, `subtract(op1, op2)`, `multiply(op1, op2)`, `divide(op1, op2)` — all take `ReadDependency<? extends Number>` (not only `Double`) and delegate to `binOp`. `null` in either operand → `null` result. Division by zero follows IEEE-754 (`±Infinity` or `NaN`), not an exception.

### Binary value-op-constant (with RO/RW split)

Each of `add`/`subtract`/`multiply`/`divide` has `…RO(ReadDependency, double)` and `…RW(ReadWriteDependency<Double>, double)` variants, plus a three-arg `…RW(ReadWriteDependency<Double>, ReadListenDependency<Number>)` overload that buffers the second operand via `validBuffer_memo()`.

- `addRW` ⇄ subtract-by-`Bijection`; `subtractRW` ⇄ add-by-`Bijection`; `multiplyRW` ⇄ divide-by-`Bijection`.
- **`divideRW(ReadWriteDependency<Double>, double)` is broken** — see PB-40 under warts.
- `subtractRO(double, op)` / `subtractRW(double, op)` — constant-minus-reactive variants. The `RW` form uses `Bijection.involution` (subtraction from a constant is its own inverse).
- `divideRO(double, op)` / `divideRW(double, op)` — constant-over-reactive variants. The `RW` form also uses `Bijection.involution` (constant-divided-by is its own inverse: `c/(c/x) = x`).

### Min / Max

- `min(op1, op2)` / `max(op1, op2)` — two reactive `Number` operands; `null` propagates.
- `min(op1, double)` / `max(op1, double)` — reactive clamped to a constant; `null` propagates.

### `binOp` — arbitrary combiner

`binOp(op1, op2, ToDoubleBiFunction)` and `binOp(op1, op2, BiFunction<…, Double>)` route through `Piles.makeBinOp` — the generic escape hatch for arbitrary double-producing computations from two reactive operands.

## `round` and `signum` → `SealInt`

- `round()` on `ReadDependencyDouble` → `SealInt`; calls `Math.round` (long round, returning `int` via cast).
- `signum(ReadDependency<? extends Number>)` (static) → `SealInt`: `(int) Math.signum(v.doubleValue())`. `null` operand → `null` result.

These are the only operators that cross from the double layer into the int layer without going through the Comparable layer.

## Comparisons → `SealBool` / `SealInt` (Comparable layer)

`PileDouble extends PileComparable<Double>`, so ordering comparisons are **not declared here**. They come from `ReadDependencyComparable` (parent of `ReadDependencyDouble`) via the [Comparable layer](../specialized_Comparable/_index.md):

- `greaterThan` / `lessThan` / `greaterThanOrEqual` / `lessThanOrEqual` (and `…Const` variants) → `SealBool`.
- `compareTo` / `compareToConst` → `SealInt`.

Each takes a `nullIsLess` flag controlling where `null` sorts. When `nullIsLess` is `null`, the comparison itself yields `null` whenever an operand is `null`.

## n-ary aggregation — `DoubleAggregator` and `Piles.aggregate`

`DoubleAggregator implements Piles.AggregationMonoid<Number, ReadListenDependencyDouble>` is the nested aggregation glue. Four ready monoids are defined as static fields:

| Field | neutral | op |
|---|---|---|
| `sumAggregator` | `0.0` | `PileDouble::add` |
| `productAggregator` | `1.0` | `PileDouble::multiply` |
| `minAggregator` | `+∞` | `PileDouble::min` |
| `maxAggregator` | `−∞` | `PileDouble::max` |

`DoubleAggregator`'s constructor interns well-known neutral values via the `Piles` constants (`Piles.ZERO_D`, `Piles.ONE_D`, `Piles.POSITIVE_INFINITY_D`, `Piles.NEGATIVE_INFINITY_D`, `Piles.NULL_D`) rather than allocating new constants.

The `sum(Iterable/varargs)` / `product(…)` / `min(…)` / `max(…)` n-ary statics call `Piles.aggregate` with the corresponding monoid, which builds a *balanced binary tree* of pairwise operations — O(log n) propagation depth. See [aggregation.md](../impl/Piles/aggregation.md) for the fold algorithm.

## `writableAverage` — a reactive mean with write-back

`writableAverage(ReadWriteListenDependencyDouble... items)` is a double-specific factory with no bool/int analogue. It builds a `SealDouble` that:

1. **Reads** as `sum / n` (where `sum` is a reactive `PileDouble.sum` of all items).
2. **Writes** by setting every source item to the new average value, optionally suppressing auto-validation during the burst (when `sum` implements `AutoValidationSuppressible`).

The two-arg overload `writableAverage(boolean writeSourcesIfUnchanged, boolean flicker, items)` and the full three-arg form accept a `writeUnworthyChange` predicate that can skip writing back when the incoming value would not meaningfully change the average.

`flicker` causes an intermediate `setter.set(v)` before the write-back, producing a brief flicker to the written value before sources propagate back their actual average. Mainly for UI responsiveness.

If sources do not in practice change to the target (e.g. clamped), the average settles at their actual average after the write attempt — this is documented in the javadoc as expected behavior, not a bug.

## Dynamic monoid aggregation — `DoubleMonoidOp`

`DoubleMonoidOp` (a nested `interface` on `PileDouble`) is the dynamic equivalent of `DoubleAggregator`: it aggregates over **whatever `Dependency`s happen to be attached at recompute time** that are also `ReadValueDouble` instances — not a fixed operand list.

Five operations are defined as constants (`SUM`, `PRODUCT`, `FLIP_PRODUCT`, `MAX`, `MIN`) with matching neutral elements (`SUM_NEUTRAL=0`, `PRODUCT_NEUTRAL=1`, `FLIP_PRODUCT_NEUTRAL=0`, `MAX_NEUTRAL=−∞`, `MIN_NEUTRAL=+∞`).

`FLIP_PRODUCT` computes `1 - (1-a)(1-b)` — a probabilistic union formula; useful when operands are independent failure probabilities.

`DoubleMonoidOp.configurator(ifEmpty, op)` builds a `PileBuilder` `Consumer` that installs a recompute: walk `giveDependencies`, fold `ReadValueDouble` ones with `op`, throw `FulfillInvalid` on a `null` operand, yield `ifEmpty` when no operands present. It always names the result `"Dynamic double aggregator"` (via `nameIfUnnamed`).

Ready configurators as static fields: `SUM_CONFIG`, `PRODUCT_CONFIG`, `FLIP_PRODUCT_CONFIG`, `MAX_CONFIG`, `MIN_CONFIG`.

Convenience factory pairs (no-arg and `Dependency...`):
- `dynamicSum()` / `dynamicSum(Dependency...)`
- `dynamicProduct()` / `dynamicProduct(Dependency...)`
- `dynamicFlipProduct()` / `dynamicFlipProduct(Dependency...)`
- `dynamicMin()` / `dynamicMin(Dependency...)`
- `dynamicMax()` / `dynamicMax(Dependency...)`

And builder variants: `buildDynamicSum(V)`, `buildDynamicProduct(V)`, `buildDynamicFlipProduct(V)`, `buildDynamicMin(V)`, `buildDynamicMax(V)`, `buildDynamicMonoid(V, Double, DoubleMonoidOp)`.

## Builder shortcuts

`PileDouble.rb()` / `sb()` / `ib()` / `ib(Double)` return `PileBuilder<PileDoubleImpl, Double>` / `SealPileBuilder<SealDouble, Double>` / `IndependentBuilder<IndependentDouble, Double>`, each pre-seeded with `Comparator.naturalOrder()` ordering. These are the idiomatic entry points for hand-building a reactive double.

`readOnlyWrapper(in)` and `readOnlyWrapperIdempotent(in)` accept `ReadDependency<? extends Number>` (not just `Double`) — so any reactive `Number` can be wrapped into a `SealDouble`. The idempotent form returns the input unchanged when it is already a default-sealed `SealDouble` or a `ConstantDouble`.

## NaN / ±Infinity handling

IEEE-754 arithmetic propagates normally — `0.0 / 0.0 = NaN`, `1.0 / 0.0 = +Infinity`. There is no special-casing in any operator for `NaN` or infinity; they pass through as ordinary `double` values. The aggregation neutrals use `±Infinity` intentionally (`minAggregator` neutral = `+∞`, `maxAggregator` neutral = `−∞`) to guarantee correct behavior with any finite input.

`null` is the third state separate from `NaN`. All arithmetic lambdas explicitly short-circuit: `if(a==null || b==null) return null;`. A `NaN` operand is not `null` and will produce `NaN` output.

## toString-equivalence for preferences

`PrefInterop.doublePreference(...)` builds an `IndependentDouble` whose change-equivalence is `STRING_EQUIVALENCE` (`a.toString().equals(b.toString())`, with `null` unequal to non-`null`) rather than numeric `equals`. Two doubles that print identically are treated as unchanged — this avoids spurious preference-store writes from float round-trips. `STORE_NULL` `NullBehavior` is rejected for double preferences (they are `neverNull()`). This affects only the preference-interop entry point; manually built `IndependentDouble`s use normal `Objects.equals`.

## Caveats & gotchas

- **`null` vs `NaN` are distinct.** `null` means "unknown/unavailable" and short-circuits all operators to `null`. `NaN` is a valid (if mathematically degenerate) `double` and propagates through arithmetic normally. Do not conflate them.
- **Operator naming drift.** Division is `over`/`divide` (no `div`); negation is `negative` (no `negate`); reciprocal is `inverse`. There is no `abs`, no `sqrt`, no `negate` on reactive doubles — those exist only on `MutDouble`.
- **`Number`-typed operands.** The value-op-value arithmetic statics accept `ReadDependency<? extends Number>` and call `.doubleValue()`, so mixing an `int` pile with a `double` pile is legal; the result is always a `SealDouble`.
- **`RW` operators are bijection-based.** Write-back only round-trips cleanly for invertible ops and lossless intermediates. Multiplying by a constant and then writing back divides — but if the constant is `0`, the bijection is `1/0 = Infinity`.
- **`divideRW(op, double)` is silently read-only** — see warts (PB-40).
- **Dynamic monoid and `null` operands.** `DoubleMonoidOp.configurator` throws `FulfillInvalid` (making the result invalid) if any attached `ReadValueDouble` dependency is `null`. It does not propagate `null` like the static binary operators do.
- **Preference doubles compare by `toString`**, not by bit pattern — see the toString-equivalence section above.

## Tech debt / warts

- **PB-39 (`inverseRW` / `inverseRO` recompute mismatch).** Both `inverseRW` and `inverseRO` recompute `−v` (negation) in their recompute lambda but call the method "inverse" (reciprocal). `inverseRW` then seals with write-back `1/v` (correct for a reciprocal). As written, the displayed value is the negative of the source, but writing into it applies the reciprocal. `inverseRO` is purely wrong — it computes and displays the negative while claiming to be a reciprocal. Already logged as PB-39.
- **PB-40 (`divideRW(ReadWriteDependency<Double>, double)` silently read-only).** This overload delegates to `multiplyRO(op, 1/value)` — the `RO` variant — so no write-back bijection is installed despite the `RW` name and javadoc. A write to the result is silently ignored. The three-arg `divideRW(ReadWriteDependency<Double>, ReadListenDependency<Number>)` is correctly implemented with a bijection. Already logged as PB-40.
- **`DoubleMonoidOp.configurator` always names the value `"Dynamic double aggregator"`** via `nameIfUnnamed` — this is correct for the double family (unlike `BoolMonoidOp.configurator` which has a copy-paste bug naming its value `"Dynamic double aggregator"` too, noted in the bool doc).
- **Name template uses `"! ?"` / `"!"` prefix for unary operators** (both `negative` and `inverse`), borrowed from the boolean `not` factory. So a derived double with no input name prints with a boolean-looking `!` prefix — cosmetic.
- **`writableAverage`** has a commented-out `.deferListeners()` line in the builder chain — dead configuration left in place.
- `readOnlyWrapper` accepts `ReadDependency<? extends Number>` (any numeric reactive), which is broader than the `PileDouble` type — could coerce a reactive integer silently into a reactive double, which may be intentional but is not called out in the javadoc.

## Common tasks

- Reactive arithmetic → `a.plus(b)` / `a.times(2.0)` / `a.over(b)` on instances; or `PileDouble.add(a, b)` / `PileDouble.multiply(a, b)` statically (all yield `SealDouble`).
- Writable arithmetic → `a.plusRW(2.0)` / `PileDouble.addRW(a, 2.0)` — writes propagate back to `a`.
- Reactive reciprocal → avoid `inverse` until PB-39 is fixed; compose via `PileDouble.divide(1.0, a)` (`divideRO(1.0, a)`) instead.
- Reactive comparison → `a.greaterThan(b, nullIsLess)` (→ `SealBool`) or `a.compareTo(b, nullIsLess)` (→ `SealInt`), both from the Comparable layer.
- Round / sign → `a.round()` (→ `SealInt`) / `PileDouble.signum(a)` (→ `SealInt`).
- Sum/product a fixed collection → `PileDouble.sum(items)` / `PileDouble.product(items)`.
- Reactive mean with write-back → `PileDouble.writableAverage(items)`.
- Dynamic aggregation (open dependency set) → `PileDouble.dynamicSum()` / `buildDynamicMonoid(val, ifEmpty, op)`.
- Persisted double setting → `PrefInterop.doublePreference(node, key, default, nullBehavior)` (toString-equivalence; not `STORE_NULL`).
- Hand-build a reactive double → `PileDouble.rb()` (full pile) / `PileDouble.sb()` (sealable) / `PileDouble.ib(init)` (independent leaf).
