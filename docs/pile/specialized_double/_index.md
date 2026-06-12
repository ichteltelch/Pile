# `pile.specialized_double` — package index (delta over the bool exemplar)

The `double`/`Double` instance of the primitive-specialization family; same layout and semantics as the documented exemplar [`pile.specialized_bool`](../specialized_bool/_index.md) — read that first for the pattern. This index records only what differs.

Source folder: `src` (all classes below).

Up: [overview](../../overview.md). Exemplar family (read first): [`../specialized_bool/_index.md`](../specialized_bool/_index.md). Sibling number family: [`../specialized_int/_index.md`](../specialized_int/_index.md). Generic counterparts: [`../impl/_index.md`](../impl/_index.md), capstone impl [`../impl/PileImpl.md`](../impl/PileImpl.md). Aggregation monoids: [`../impl/Piles/aggregation.md`](../impl/Piles/aggregation.md).

> The whole "generality in, specialization out" mechanism — every `*Double` IS-A the matching generic type, narrowed return types, memoized derived operators, the silent-ignore/no-op idioms, `Mut*` not being a graph node — is exactly as documented in the bool exemplar. Do not re-read it here; this page only flags the double-specific deltas.

## Structural deltas vs the bool family

Two structural differences from `specialized_bool`/`specialized_int`:

- **No `SuppressDouble`.** A reference-counted suppression flag is inherently boolean; it exists only in the `bool`/`int` families. There is nothing to mirror for `double`.
- **Extra: `FieldDouble`** — present here, absent in bool/int. **It is NOT a `double`-typed `field`/`deref` view** (despite the name "Field"); it is unrelated to `Piles.deref`/`Piles.field`. `FieldDouble` is a tiny GUI-display bundle: an interface pairing a reactive double `value()` (a [`ReadWriteListenDependencyDouble`](combinations/_index.md)) with a `getText()` label and a `getIncrement()` step (useful for a spinner/stepper widget). It carries **no reactive behavior of its own** — it just groups three already-built things; `FieldDouble.make(textSupplier, increment, value)` returns an anonymous impl. Think "view-model record for a numeric input field", not "dereferenced cell".

Everything else maps 1:1 to bool: `ConstantDouble`/`IndependentDouble`/`SealDouble`/`PileDoubleImpl` extend `Constant`/`Independent`/`SealPile`/`PileImpl` (each `<Double>` + the double combinations), and `MutDouble` is the bare mutable-`double` box (the analogue of `MutBool`/`MutInt`), not a graph node.

## Concrete types — one-line mappings

| `*Double` type | extends (generic) | role / delta |
|---|---|---|
| `PileDoubleImpl` | [`PileImpl`](../impl/PileImpl.md)`<Double>` | default full reactive double; implements [`PileDouble`](PileDouble.md). |
| `SealDouble` | [`SealPile`](../impl/SealPile.md)`<Double>` | sealable double; the type every redirecting double operator (`negativeRW`, `plus`, `mapToDouble`, …) hands back. |
| `ConstantDouble` | `Constant<Double>` | never-changing double; always valid, silently ignores writes. |
| `IndependentDouble` | `Independent<Double>` | always-valid non-recomputing leaf double; what `PrefInterop.doublePreference` vends. |
| `MutDouble` | *(none)* | bare mutable `double` box (`val`), `JustReadValueDouble` + `DoubleSupplier`; **not** a graph node. Adds `getAsDouble()`, `sqrt()`, `set(double)`. |
| `FieldDouble` | *(none — interface)* | GUI bundle: `value()` + `getText()` + `getIncrement()`. See above. |

The 12 `combinations/*Double` interfaces mirror the 12 generic `pile.aspect.combinations` interfaces exactly as in bool; see [`combinations/_index.md`](combinations/_index.md) *(pending)*. `WriteElsewhereDouble` is the same inert/commented-out vestige as `WriteElsewhereBool`.

## The double-specific operator surface

Where bool adds a logic algebra (`not`/`and`/`or`), double adds **arithmetic + ordering**, split across two layers:

### Arithmetic — on `PileDouble` (statics) and `ReadDependencyDouble` (instance entry points)
[`PileDouble`](PileDouble.md) is the capstone interface and the static factory hub; `ReadDependencyDouble` exposes the per-instance methods that delegate into it (each cites the `PileDouble` static it forwards to in its javadoc). The surface, all returning `SealDouble`:

- Unary: `negative()`/`negativeRO()`/`negativeRW()` (write-back negates the source), `inverse()`/`inverseRO()`/`inverseRW()` (reciprocal; note the **asymmetry** — see warts).
- Binary, value op value: `plus`/`minus`/`times`/`over` (named `over`, not `div`), and the statics `add`/`subtract`/`multiply`/`divide`. Each takes `ReadDependency<? extends Number>` (any reactive number, not only `Double`).
- Binary, value op constant: `plus(double)`/`minus(double)`/`times(double)`/`over(double)` with `…RO`/`…RW` variants — the `RW` forms install a `Bijection` so writes propagate back to the operand (e.g. `addRW` ⇄ subtract). There are also `…RW(op, ReadListenDependency<Number>)` overloads that buffer the second operand via `validBuffer_memo()`.
- `min`/`max` (reactive×reactive and reactive×constant).
- `round()` → `SealInt` (on `ReadDependencyDouble`), and static `signum(...)` → `SealInt`.

There is **no `abs`, no `sqrt`, no `negate`, no `div`** on the reactive surface. `sqrt()`/`getAsDouble()` exist only on the non-reactive `MutDouble`. `negate` is spelled `negative`; division is `over`/`divide`.

### Comparison → `bool` and `int` — inherited from the Comparable layer
`PileDouble extends PileComparable<Double>`, so comparisons are **not redeclared here** — they come from `ReadDependencyComparable` (parent of `ReadDependencyDouble`): `greaterThan`/`lessThan`/`greaterThanOrEqual`/`lessThanOrEqual` (+ `…Const`) → `SealBool`, and `compareTo`/`compareToConst` → `SealInt`. Each takes a `nullIsLess` flag controlling where `null` sorts.

### `mapTo*` coercions — inherited from generic `ReadDependency`
`mapToInt`/`mapToBool`/`mapToDouble` (and `…P`/config overloads) are **declared on the generic `ReadDependency`**, not on the double layer — so a `ReadDependencyDouble` already has them. Internally the arithmetic helpers lean on `mapToDouble` (e.g. `addRO`, `subtractRO(double,op)`). Primitive read accessors specific to double are minimal: `ReadValueDouble` adds only `getF()` (boxed `Float`, null-preserving); the boxed-`Double` `get()` comes from the generic `ReadValue`. There is no reactive `getAsDouble`.

### Aggregation
`PileDouble` defines `DoubleAggregator` (a `Piles.AggregationMonoid<Number, ReadListenDependencyDouble>`) and four ready monoids — `sumAggregator`/`productAggregator`/`minAggregator`/`maxAggregator` — plus `sum`/`product`/`min`/`max` over `Iterable`/varargs, routed through `Piles.aggregate` (see [`../impl/Piles/aggregation.md`](../impl/Piles/aggregation.md)). It also has a **dynamic** monoid family keyed off live `Dependency` membership: `DoubleMonoidOp` (`SUM`/`PRODUCT`/`FLIP_PRODUCT`/`MAX`/`MIN`, where flip-product is `1-(1-a)(1-b)`) with `dynamicSum()`/`dynamicProduct()`/`dynamicFlipProduct()`/`dynamicMin()`/`dynamicMax()` and matching `buildDynamic*` configurators — these aggregate exactly those dependencies that are `ReadValueDouble` instances, and a `null` operand throws `FulfillInvalid`. `writableAverage(...)` is a notable double-only factory: a reactive mean that, when written, sets every source to the new average (optionally suppressing their auto-validation during the burst).

### Builders
`PileDouble.rb()`/`sb()`/`ib()`/`ib(init)` return `PileBuilder`/`SealPileBuilder`/`IndependentBuilder` pre-seeded with `Comparator.naturalOrder()` ordering (the natural double order), unlike bool which has no ordering.

## Float / NaN / infinity & toString-equivalence

- `getF()` narrows to `Float` (lossy) — there is no reactive float type; floats are an accessor convenience only.
- `±Infinity` are first-class: the aggregator neutrals are `POSITIVE_INFINITY`/`NEGATIVE_INFINITY` (for `min`/`max`) and `Piles` interns `NULL_D`/`ZERO_D`/`ONE_D`/`POSITIVE_INFINITY_D`/`NEGATIVE_INFINITY_D` as shared constant neutrals (`DoubleAggregator`'s constructor picks the interned one).
- **`null` is the third state everywhere**, exactly as bool: every arithmetic lambda short-circuits to `null` if an operand is `null` (`a==null||b==null ? null : …`).
- **toString-equivalence for preferences.** `PrefInterop.doublePreference(...)` builds an `IndependentDouble` whose change-equivalence is `STRING_EQUIVALENCE` (`a.toString().equals(b.toString())`, with `null`s unequal to non-`null`) rather than numeric `equals`. So two doubles that print identically are treated as unchanged — this avoids spurious preference-store writes from float round-trips, and means the value's notion of "changed" is its decimal string, not its bit pattern. `STORE_NULL` `NullBehavior` is rejected for double preferences (they are `neverNull()`).

## Caveats & gotchas

- **`FieldDouble` is a GUI bundle, not a dereferenced/`field` value** — do not confuse it with `Piles.field`/`deref`. It has no reactive semantics.
- **Operator naming drift:** division is `over`/`divide` (no `div`); negation is `negative` (no `negate`); there is no `abs`/`sqrt` on reactive doubles (only on `MutDouble`).
- **`Number`-typed operands:** arithmetic factories accept `ReadDependency<? extends Number>` and call `.doubleValue()`, so you can add an `Int` pile to a `Double` pile; the result is always `SealDouble`.
- **Preference doubles compare by `toString`**, not numerically (see above) — a subtle equality model if you reuse that `IndependentDouble`.
- **`RW` arithmetic is bijection-based**, so write-back only round-trips cleanly for invertible ops; `divideRW(op, value)` notably does **not** install a bijection (see warts).

## Tech debt / warts

- `WriteElsewhereDouble` — dead commented-out vestige (mirrors `WriteElsewhereBool`).
- `inverseRW` (`PileDouble.inverseRW`) — its **recompute** computes `-v` (negation) while its **seal** write-back computes `1/v` (reciprocal). The recompute should presumably be `1/v` to match an "inverse" value; as written the displayed value is the negative but writing inverts. Flagged below.
- `divideRW(ReadWriteDependency, double)` (`PileDouble.divideRW`) — delegates to `multiplyRO(op, 1/value)`, i.e. it returns a **read-only** (`RO`) wrapper despite the `RW` name and javadoc ("Writing to it will attempt to change the first operand"). No write-back bijection is installed. Flagged below.
- `negative`/`inverse` `name(...)` fallbacks all read `"! ?"` / use a `!`-prefixed template copied from the boolean `not` factory, so derived double values can print with a boolean-looking `!` name. Cosmetic.

## Common tasks

- Reactive arithmetic → `a.plus(b)` / `a.times(2.0)` / `PileDouble.add(a, b)` (all yield `SealDouble`).
- Reactive comparison → `a.greaterThan(b, nullIsLess)` (→ `SealBool`) or `a.compareTo(b, nullIsLess)` (→ `SealInt`).
- Round / sign → `a.round()` / `PileDouble.signum(a)` (→ `SealInt`).
- Sum/avg a collection → `PileDouble.sum(items)` / `PileDouble.writableAverage(items)`.
- A persisted double setting → `PrefInterop.doublePreference(node, key, default, nullBehavior)` (toString-equivalence; not `STORE_NULL`).
- A numeric GUI field descriptor → `FieldDouble.make(textSupplier, increment, value)`.
</content>
</invoke>
