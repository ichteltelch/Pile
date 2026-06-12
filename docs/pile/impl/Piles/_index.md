# `pile.impl.Piles` — static utility catalogue (Tier-1 index)

The library's central static factory/combinator hub: ~100 static methods for **creating** reactive values (constants, independents, builders), **deriving** one value from another (read-only wrappers, deref, buffers, rate-limiting, fallbacks), **aggregating** many values (min/max, monoid `aggregate`, boolean and/or aggregators), and a handful of cross-cutting services (deep-revalidate, transform dummies, deep-revalidate thread-local flags). It is the primary user-facing entry point of Pile.

Source folder: **`src`**. File: `src/pile/impl/Piles.java` (~2,885 lines). This is an **index**, not a per-method doc — one line per method/group; detail docs come later for the categories flagged below.

Up: [impl index](../_index.md) *(pending)* · [overview](../../../overview.md). Aspects: [aspect index](../../aspect/_index.md) · [combinations index](../../aspect/combinations/_index.md). Concepts: [concepts/](../../../concepts/) (esp. [transactions.md](../../../concepts/transactions.md)).

## Conventions (read first)
- **Generality in, specialization out.** Per the README, these methods take the *general* aspect types as parameters (`ReadDependency`, `ReadListenDependency`, `ReadWriteListenValue`, …) and return the *appropriate concrete specialization* (`SealPile`, `Constant`, `Independent`, or the typed `SealBool`/`SealInt`/`SealDouble`/`SealString`/`ConstantInt`/… variants). Most methods have a generic form plus per-primitive twins (`…Bool`/`…Int`/`…Double`/`…String`), because Java lacks extension methods.
- **More specialized utilities live on the type classes.** `not`, `and`/`or`, `field`, `overridable`, `choose`, `sum`/`product`, comparison→boolean operators, etc. are **not** here — they live as default/static methods on `ReadDependency`/`PileBool`/`PileInt`/… (see the [combinations index](../../aspect/combinations/_index.md)). `Piles` holds the *type-agnostic* and *cross-type* factories plus the `*Aggregator` constants those classes consume.
- **`make…` vs the short name.** Many families come in pairs: the public convenience (`min`, `deref`, `readOnlyWrapper`, `firstValid`, `binOp`) builds a fresh `SealPile`; the `make…(…, V template)` overload configures and seals a *caller-supplied* `SealPile` subtype `V` and returns it — that is the extension point the typed classes route through.
- Derived values are almost always **sealed `SealPile`s** (read-only, recompute-driven); writable variants (`writable…`, `firstValid(writableFirst, …)`, `fallback(ReadWriteListenDependency,…)`) redirect writes to an underlying writable value.

## Constants & singletons (fields)
- `TRUE`, `FALSE`, `NULL`, `EMPTY_STRING` — shared constant reactive values (`ConstantBool`/`Constant`/`ConstantString`).
- `NULL_D`/`ZERO_D`/`ONE_D`/`POSITIVE_INFINITY_D`/`NEGATIVE_INFINITY_D`, `NULL_B`, `NULL_I`/`ZERO_I`/`ONE_I`/`MIN_VALUE_I`/`MAX_VALUE_I` — common typed constants.
- `CONST_INVALID` / `constInvalid` — a permanently-invalid sealed value (and `makeNewConstInvalid` to mint a fresh one).
- `SEALED_NULL` / `sealedNull` — a sealed value permanently holding `null`.
- `FULFILL_INVALID`, `FULFILL_NULL` — sentinel `Runnable`s returned from staged recomputation to fulfill invalid / null (consumed by `AbstractPileBuilder`).
- `RESTORE_OLD_VALUE` — reified `Recomputation::restoreOldValue` to use as a recompute that keeps the prior value.
- `and/and2/and3/andNn`Aggregator, `or/or2/or3/orNn`Aggregator — `BoolAggregator` monoids backing `aggregate(...)` for the boolean reductions (see aggregations).

## Constant factories
- `constNull` — the `NULL` constant cast to `E`.
- `constant(E)` / `constant(Boolean|Double|Integer|String)` — make an immutable `Constant`/`ConstantBool`/… holding the value.
- `getConstant(boolean)` — return the shared `TRUE`/`FALSE` singleton (no allocation).
- `sealedConstant(E)` / `sealedConstant(Boolean|Double|Integer|String)` — a sealed `SealPile` constant; **caveat:** opening a transaction on it makes it invalid for the transaction's duration (unlike a true `Constant`).

## Mutable-value & builder factories
- `init(E)` / `init(Boolean|Integer|Double|String)` — start a `PileBuilder` seeded with an initial value (numeric/bool/string variants preconfigure natural ordering for bounds).
- `generic` / `genericSealable` / `genericIndependent` — fresh untyped `PileBuilder` / `SealPileBuilder` / `IndependentBuilder`.
- `rb` / `sb` / `ib` / `ib(E)` — short aliases for `generic` / `genericSealable` / `genericIndependent` (the last with an initial value).
- `independent(E)` / `independent` / `independent(Boolean|Integer|Double|String)` — make a standalone writable `Independent` (no recompute, stays valid in transactions).
- `sealed(E)` / `sealed(Boolean|Double|Integer|String)` — a `SealPile` initialized to a value but **not yet sealed** (writable until sealed).
- `sealedNoInit(Class<E>)` / `sealedNoInit` / `sealedNoInit{Bool,Int,Double,String}` — an unsealed, uninitialized (invalid) `SealPile` of the given type.

## Recompute-builder factories (derive a value from a computation)
Each starts a `PileBuilder` whose value is recomputed; primitive variants preconfigure natural ordering. Three input shapes:
- **Pull (`Supplier`):** `compute(Supplier)`, `computeBool`/`Int`/`Double`/`String`, `decide(BooleanSupplier)`. `computeS…`/`decideS` are identical aliases to **disambiguate overloads** at call sites.
- **Push (`Consumer<Recomputation>`):** `compute(Consumer)`, `computeBool`/`Int`/`Double`/`String`, `decide(Consumer)`; `computeX…`/`decideX` are the disambiguating aliases.
- **Staged (`Function<Recomputation,Runnable>`):** `computeStaged`, `computeStagedBool`/`Int`/`Double`/`String`, `decideStaged` — two-phase recompute (compute off-thread, then apply).

## Remembered-value factories
- `remembered(LastValueRememberer<E>)` and typed overloads (`remembered(LastValueRemembererBool|Int|Double|String)`, `rememberedBool`/`Int`/`Double`/`String`(LastValueRememberer<…>)) — start an `IndependentBuilder` whose value persists/restores via a `LastValueRememberer` (e.g. to `Preferences`).

## Read-only wrappers (derive one value)
- `readOnlyWrapper(ReadDependency)` — a sealed `SealPile` mirror that forbids writes.
- `readOnlyWrapperIdempotent(ReadDependency)` — same, but returns the input unchanged if it's already a `Constant` or default-sealed `SealPile` (avoids needless wrapping).
- `makeReadOnlyWrapper(ReadDependency, V template)` — configure & seal a caller-supplied `SealPile` subtype as the read-only mirror.

## Dereferencing (value-of-a-value)
- `deref(ReadDependency<ReadDependency<T>>)` / `derefBool`/`Int`/`Double`/`String` — a sealed `SealPile` that reactively tracks the value held by an inner reactive value.
- `makeDeref(…, V putHere)` — deref into a caller-supplied `SealPile`.
- `writableDeref(ReadDependency<ReadWriteDependency<E>>)` / `writableDeref{Bool,Int,Double,String}` — deref that also **forwards writes** to the inner value.
- `makeWritableDeref(…, V)` — writable deref into a caller-supplied `SealPile`.

## Buffers (snapshot/decouple a value)
> **Detail doc: [buffers.md](buffers.md)** — the follow-by-listener (not dependency) mechanism, transaction-cascade shortening, read-only vs writable, weak and rate-limited variants.
- `buffer` / `buffer{Bool,Int,Double,String}` — a sealed `SealPile` buffered copy of a `ReadListenValue` (`setupBuffer`).
- `writableBuffer` / typed (note: string variant is misnamed `writableBufferDtSealableString`) — buffered copy that forwards writes (`setupWritableBuffer`).
- `weakBuffer` / typed, and `writableWeakBuffer` / typed (string variant `writableWeakBufferDtSealableString`) — buffers whose link to the leader is weakly referenced (`setupWeakBuffer`/`setupWritableWeakBuffer`).
- `validBuffer` / `validBuffer{Bool,Int,Double,String}` — an **`Independent`** (not `SealPile`) that keeps the *last valid* value of the leader, staying valid even when the leader goes invalid (`setupValidBuffer`).
- `writableValidBuffer(leader)` / typed, plus the `(leader, Function<Consumer,Consumer> defer)` overloads — valid-buffer that forwards writes, optionally deferring them via the `defer` wrapper.

## Rate limiting (derive one value)
- `rateLimited(ReadListenValue, coldStartTime, coolDownTime)` / `rateLimited{Bool,Int,Double,String}` — a sealed `SealPile` that throttles how often it follows the leader (`setupRateLimited`).
- `writableRateLimited` / `writableRateLimited{Bool,Int,Double,String}` — rate-limited mirror that also forwards writes (`setupWritableRateLimited`).

## Fallback / first-valid (choose among many)
- `firstValid(writableFirst, values…)` / `firstValid{Bool,Double,String,Int}` — sealed value taking the first **valid** value in the sequence; the optional leading `writableFirst` receives redirected writes.
- `makeFirstValid(V, writableFirst, values…)` — first-valid into a caller-supplied `SealPile`; dynamically depends only on validities up to the chosen one.
- `firstValidV(items…)` / `makeFirstValidV(V, items…)` — first-valid whose *value* is the first valid **inner `ReadDependency`** itself (not its dereferenced value).
- `firstNonNull(possibilities…)` — first value that is both valid and non-`null` (else invalid).
- `fallback(ReadListenDependency, def)` and `fallback(ReadWriteListenDependency, def)` plus typed Bool/Int/Double/String overloads — value that mirrors `v` when valid, else a constant `def`; the writable overload redirects writes.

## Comparisons → min/max reactive values
Two-operand:
- `minOpNullIsLess` / `minOpNullIsGreater` / `maxOpNullIsLess` / `maxOpNullIsGreater` — sealed min/max of two values, choosing how `null` sorts.
- `min`/`max(op1, op2, Comparator)` and `min`/`max(op1, op2, Boolean nullIsLess)` — sealed min/max with explicit ordering.
- `makeMin`/`makeMax(op1, op2, V template, Comparator|Boolean)` — min/max into a caller-supplied `SealPile`.
- `minOp`/`maxOp(Boolean|Comparator)` and `minOp`/`maxOp(Supplier<V>, Boolean|Comparator)` — return reusable binary-op functions / `AggregationMonoid` building blocks for the aggregation machinery.

N-ary (aggregation, see below):
- `minAggregation` / `maxAggregation` (over a `Supplier<V>` template + neutral, or over a `ReadListenDependency` of items; Comparator or Boolean forms) and the `…C` variants (`minAggregationC`/`maxAggregationC`) that take a neutral **element** `E` rather than a value — fold a collection to its min/max.

## Aggregations over many values
> **Detail doc: [aggregation.md](aggregation.md)** — the balanced-binary-tree monoid fold behind `aggregate(...)`, the `AggregationMonoid` contract, the concrete monoids, and how the typed operators route through it.
- `interface AggregationMonoid<E,V>` — the (quasi-)monoid contract: `constantNeutral`, `apply(op1,op2)`, `inject(o)`. Result type is a subtype of the input type.
- `aggregate(monoid, Iterable|varargs)` and `aggregate(isNeutral, monoid, Iterable|varargs)` — fold a sequence into a **balanced binary tree** of reactive values (logarithmic depth); the `isNeutral` predicate skips neutral elements. This is the engine behind boolean and/or, string concat, and min/max aggregation.
- `class SidemostFulfilling` / `LeftmostFulfilling` / `RightmostFulfilling` — `AggregationMonoid`s that pick whichever operand satisfies a `Predicate` (left- or right-biased), with `notNull` convenience instances and an `ifNone` default.
- `class OpAggregation` — generic monoid built from a neutral, an `inject` function, and a binary op.
- The `and*Aggregator`/`or*Aggregator` field constants (above) are the boolean monoids; `PileBool::and`/`or`/`andNn`/`orNn` etc. consume them.

## Binary operators, concatenation, string conversion
- `makeBinOp(op1, op2, op)` / `makeBinOp(op1, op2, V template, op)` — sealed value = `op.apply(op1, op2)`, applied reactively (the base for arithmetic/comparison operators defined on the typed classes).
- `concatStrings(op1, op2)` — sealed `SealString` of the two operands' string representations.
- `concatAny(preserveNull, Object[])` — reactive string concatenation over a mix of constants and `ReadDependency`s (dereferenced reactively); `preserveNull` keeps a lone `null` as `null` rather than `"null"`.

## Transform mechanism
- `transformDummy(deps…)` / `transformDummy(Predicate accept, deps…)` — a `PileImpl` whose only job is to forward `transform` requests to its dependers (optionally filtered by `accept`). Part of the rudimentary transform subsystem.

## Deep-revalidation
> **Detail doc: [deep-revalidation.md](deep-revalidation.md)** — what deep-revalidation solves (forcing a manually-overridden / invalid-dependency subtree to recompute), the transitive traversal, and the thread-local toggles.

- `superDeepRevalidate(Depender, followDependency, followInfluencer)` — transitively revalidate every recomputable `Pile` reachable via `Dependency`/influencer edges (gated by two predicates), under auto-validation suppression.
- `collectDependenciesAndInfluencers(o, followDependency, followInfluencer, dedup, found)` — the traversal primitive: gather the transitive dependency/influencer closure of an object into `dedup`, reporting new finds to `found`.
- `shouldFireDeepRevalidateOnSet` / `withShouldFireDeepRevalidateOnSet(Boolean)` and `shouldDeepRevalidate` / `dontDeepRevalidate` / `withShouldDeepRevalidate(Boolean)` — thread-local flags (returning `MockBlock`s for try-with-resources scoping) that toggle deep-revalidation behavior for the current thread. Default is "true" (only an explicit `FALSE` disables).

## Misc
- `loadClass` — no-op used to force-load the `Piles` class early if class-load ordering causes trouble.
