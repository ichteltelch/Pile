# `Piles` aggregation machinery — the balanced-tree monoid fold

The detail (mechanism) doc for `Piles`'s N-ary aggregation: how `aggregate(...)` folds many reactive values into a **balanced binary tree** of pairwise combinations via an `AggregationMonoid`, and how the concrete monoids (min/max, boolean and/or, predicate-pickers) and the typed-class operators route through it.

Source folder: **`src`**. File: `src/pile/impl/Piles.java` (aggregation region). The boolean monoid `BoolAggregator` lives in `src/pile/specialized_bool/PileBool.java`.

Up: [Piles index](_index.md) · [impl PileImpl](../PileImpl.md) · [overview](../../../overview.md). See also: [combinations index](../../aspect/combinations/_index.md), [concepts/transactions.md](../../../concepts/transactions.md).

## What this is for

`Piles.aggregate` reduces a *sequence of reactive values* to a *single reactive value* that stays live: whenever any input changes, the aggregate recomputes. The naive way — fold left into one chained `op(op(op(a,b),c),d)…` — produces a comb of linear depth, so a change to the first leaf must propagate through `n` recompute hops. `aggregate` instead builds a **balanced binary tree** of pairwise combinations, so the depth is `⌈log₂ n⌉`. The whole thing is the single engine behind `PileBool`'s and/or reductions, min/max-over-many, and the predicate "pick the first that fulfills" reductions.

## The balanced-binary-tree fold (`aggregate`)

`aggregate(operation, items)` and `aggregate(isNeutral, operation, items)` exist as both `Iterable` and `@SafeVarargs` overloads; the two-arg forms delegate with `isNeutral == null`. All four share one algorithm (duplicated verbatim between the `Iterable` and varargs bodies — see *Warts*).

The build is a textbook **bottom-up balanced reduction using a stack and a bit trick**:

- Maintain an `ArrayList` `stack` and a running `index` (count of items accepted so far).
- For each item: skip it if `null`, or if `isNeutral != null && isNeutral.test(item)` (pruning, below). Otherwise push it and then, **while the low bits of `index` are set**, pop the top two and push `operation.apply(op1, op2)`:
  ```
  for(int bits = index; (bits&1)!=0; bits>>>=1) { pop op2; pop op1; push apply(op1,op2); }
  ```
  This is the standard "merge whenever a new leaf completes a perfect subtree" pattern (the same carry logic as incrementing a binary counter / a binomial-heap merge). After accepting `index+1` leaves the stack holds one combined node per set bit of the new count, each a perfectly-balanced subtree — so every subtree has logarithmic depth.
- **Final assembly.** After the loop, if `index==0` (no surviving items) return `operation.constantNeutral()`; if `index==1` return `operation.inject(stack.get(0))` (the lone survivor, *wrapped* — never `apply`-ed); otherwise fold the leftover stack of unequal-size subtrees together right-to-left with `apply` and return the last result.

Because the tree is balanced, an input change re-aggregates only **one root-to-leaf path** of `apply` nodes — `O(log n)` recompute work, not `O(n)`. (Each interior `apply` node is itself a reactive value depending on its two children; that is what makes propagation follow the tree.)

### How `isNeutral` prunes

`isNeutral` is an **identity/value test on the *input* (a `ReadListenDependency`), not on its current contents** — the boolean callers pass `i -> i == Piles.TRUE` (reference equality against the shared `TRUE` constant), min/max callers pass `null`. A pruned item is simply never pushed, so it never becomes a leaf and never contributes an `apply` node. This is a structural optimisation: dropping a *known-constant* neutral operand shrinks the tree (e.g. `and` over a list where some entries are the literal `TRUE` constant builds a smaller tree). It does **not** dynamically re-prune when a non-constant value happens to equal the neutral at runtime — only operands that are *the* neutral constant by reference are removed, and only at build time. The tree is **static**: built once from the items as given, never restructured when values change.

## The `AggregationMonoid<E, V>` contract

`interface AggregationMonoid<E, V extends ReadListenDependency<? extends E>>` is a *quasi*-monoid over reactive values. Its own javadoc flags that it is "not strictly a monoid because the type of the results … is a subtype of the type of the inputs":

- `V constantNeutral()` — the identity element, returned when **zero** items survive. Some monoids reject this (`OpAggregation` throws `UnsupportedOperationException` "Must provide at least one operand" if no neutral was supplied).
- `V apply(ReadListenDependency<? extends E> op1, op2)` — the binary combiner; builds an interior tree node combining two children.
- `V inject(ReadListenDependency<? extends E> o)` — wrap/adapt a single input to the result type `V`, used for the **one-item** case (and as the leaf adapter conceptually).

**Why result-type-is-a-subtype-of-input buys something.** Inputs are typed `ReadListenDependency<? extends E>` (the general read aspect), but the result `V` is the *specialized* value type — e.g. `SealPile<E>`, `ReadListenDependencyBool`. So `apply` of two general operands yields a concrete sealed value that can itself be fed back as an operand to the next `apply` (it *is* a `ReadListenDependency<? extends E>`). That closure under the operand type is exactly what lets the tree stack interior results as new operands. `inject` exists precisely because a *raw* lone input isn't yet the polished `V` the caller expects — it must be wrapped (typically a read-only wrapper) so the single-element aggregate has the same type and read-only/sealed character as the multi-element one. This is the "generality in, specialization out" convention of `Piles` (see [_index.md](_index.md)).

## The concrete monoids

### `OpAggregation<E, V>` — the generic monoid

A plain struct monoid built from `(V neutral, Function inject, BiFunction op)`. `constantNeutral()` returns `neutral` (throwing if it is `null`), `apply` = `op.apply`, `inject` = `inject.apply`. This is what `minAggregation`/`maxAggregation` instantiate: `op` is a `minOp`/`maxOp` `BiFunction`, `inject` is a read-only wrapper (`makeReadOnlyWrapper(in, makeTemplate.get())` for the templated forms, or `Piles::readOnlyWrapperIdempotent` for the untyped forms), and `neutral` is the "max possible value" (for min) / "min possible value" (for max) so that aggregating *zero* items yields the correct identity.

### `SidemostFulfilling` / `LeftmostFulfilling` / `RightmostFulfilling` — predicate pickers

`abstract SidemostFulfilling<E, V extends SealPile<E>>` is a monoid whose `apply` picks **whichever operand satisfies a `Predicate<? super E> mustFulfill`**, biased to one side:

- Constructed with `(mustFulfill, ifNone)`; the constructor **asserts `!mustFulfill.test(ifNone)`** (throws `IllegalArgumentException` "The ifNone element must not fulfill the predicate") and builds `constantNeutral = makeConstant(ifNone)` (a `sealedConstant`). So the neutral element is the `ifNone` fallback.
- `applyPreferring(preferred, notPreferred)` builds a `SealPile` whose recompute (`opPreferring`) returns `preferred.get()` if it fulfills, else `notPreferred.get()` if it fulfills, else `ifNone`. Crucially it only `dependOn(true, preferred)` plus `whenChanged` semantics — and the javadoc on the subclasses warns: **"If the left/right operand is picked, the other operand will be ignored and may be invalid."** That is the point — a short-circuit pick that tolerates an invalid loser.
- `inject(o)` = `makeBuilder().recompute(o).whenChanged(o)` — a passthrough wrapper of the single value.
- `LeftmostFulfilling.apply(op1,op2)` calls `applyPreferring(op1, op2)` (prefer left); `RightmostFulfilling` prefers right. Both supply `makeBuilder()=sb()` and `makeConstant=sealedConstant`.
- **`notNull` convenience:** the static singletons `LeftmostFulfilling.NOT_NULL` / `RightmostFulfilling.NOT_NULL` (and `notNull()` accessors, unchecked-cast to `<E>`) use `Functional.IS_NOT_NULL` with `ifNone = null` — i.e. "the first non-null operand, else null". Aggregating these over a sequence is the reactive "first non-null wins" reduction.

### min / max via `minOp` / `maxOp`

The pairwise min/max combiner is a `BiFunction` produced by `minOp` / `maxOp` (overloads taking a `Boolean nullIsLess` or a `Comparator`, optionally a `Supplier<V> makeTemplate`). Each lambda delegates to `min`/`max` → `makeMin`/`makeMax`, which is a `makeBinOp` whose op compares with the chosen ordering (or, for the `Boolean` forms, treats `null` as least/greatest per `nullIsLess`, with `null` `nullIsLess` causing NPEs by design). The N-ary factories `minAggregation`/`maxAggregation` wrap that `BiFunction` plus a neutral into an `OpAggregation`:

- `minAggregation(makeTemplate, max, …)` — neutral is the supplied **`max` value** (a `ReadListenDependency`); `inject` wraps into a fresh template.
- `minAggregation(max, ordering|nullIsLess)` (no template) — neutral is `max`; `inject = readOnlyWrapperIdempotent`; for the `Boolean` form the op is the method reference `Piles::minOpNullIsLess`/`minOpNullIsGreater`.
- `minAggregationC` / `maxAggregationC` — convenience taking a neutral **element `E`** instead of a value; they wrap it (`makeReadOnlyWrapper(new Constant<>(max), …)` or a sealed init) into the neutral value, then delegate to `minAggregation`/`maxAggregation`. (`maxAggregation` mirrors all of this with the min-possible value as neutral.)

### the boolean and/or aggregators

The field constants `andAggregator`/`and2Aggregator`/`and3Aggregator`/`andNnAggregator` and `or…Aggregator` are `BoolAggregator` instances (defined in `PileBool`). `BoolAggregator(neutral, op)` maps its `Boolean neutral` to a shared constant — `null → NULL_B`, `true → TRUE`, `false → FALSE` — so `constantNeutral()` is the right identity (`and` identity = `TRUE`, `or` identity = `FALSE`, the `Nn` "null-aware" variants = `NULL_B`). `apply = op.apply` where `op` is `PileBool::and` / `or` / `and2` / `or2` / `and3` / `or3` / `andNn` / `orNn` (the pairwise typed operators). `inject = readOnlyWrapperIdempotent`. The `*Aggregator` constants live in `Piles` only so the typed `PileBool` reductions can consume them without a cyclic field initialization.

## How the typed-class operators route through `aggregate`

The N-ary typed operators are thin one-liners that pick the matching monoid and a pruning predicate, then call `Piles.aggregate`:

- `PileBool.conjunction[/2/3]` → `aggregate(i -> i == Piles.TRUE, andAggregator|and2Aggregator|and3Aggregator, items)`. The `isNeutral` test prunes operands that are literally the `TRUE` constant (the `and` neutral).
- `PileBool.disjunction[/2/3]` → `aggregate(i -> i == Piles.FALSE, or…Aggregator, items)` — prunes the literal `FALSE` (the `or` neutral).
- `conjunctionNa` / `disjunctionNa` (null-aware) → `aggregate(i -> i == Piles.<Boolean>constNull(), andNnAggregator|orNnAggregator, items)` — prunes the literal null constant.
- min/max over many: callers build a `minAggregation`/`maxAggregation` (or `…C`) monoid and pass it to `aggregate` (no `isNeutral`).
- The predicate-picker reductions feed a `LeftmostFulfilling`/`RightmostFulfilling` (often `notNull()`) into `aggregate`.

The pattern is always the same: the *operator* is the monoid; the *pruning rule* is the `isNeutral` predicate; `aggregate` supplies the tree.

## Recipe — aggregate your own values

To reduce `List<ReadListenDependencyBool> flags` to a reactive "all true":
```java
ReadListenDependencyBool all = PileBool.conjunction(flags);   // and-tree, prunes literal TRUE
```
To reduce arbitrary `Comparable` values to a reactive minimum with `null` sorting greatest:
```java
var monoid = Piles.minAggregationC(SOME_MAX_ELEMENT, /*nullIsLess=*/false);
SealPile<T> theMin = Piles.aggregate(monoid, value1, value2, value3);
```
To take the first non-null of a sequence, reactively, left-biased:
```java
SealPile<T> firstNonNull = Piles.aggregate(LeftmostFulfilling.<T>notNull(), v1, v2, v3);
```
For a custom associative op, build an `OpAggregation(neutral, injectWrapper, biOp)` and pass it to `aggregate`. Ensure `op` is associative (the tree groups operands differently than left-fold) and that `neutral` is a genuine identity, or zero-/one-element results will surprise you.

## Caveats & gotchas

- **The tree is static.** It is built once from the items in the order given; it does not restructure as values change, and `isNeutral` prunes only at build time (and only operands that *are* the neutral constant by reference). Reordering or replacing items means rebuilding.
- **Associativity is your responsibility.** Because operands are regrouped into a balanced tree, a non-associative `op` gives different results than a left-fold. Min/max/and/or/concat are fine; a subtractive op is not.
- **`apply` must tolerate an invalid/ignored operand** for the short-circuiting monoids (`SidemostFulfilling`, and `PileBool::and`/`or` which can ignore a decided operand). The picked branch may leave the other operand invalid — that is intended, not a fault.
- **Zero items → `constantNeutral()`.** With an `OpAggregation` lacking a neutral (`null`), aggregating an empty sequence throws `UnsupportedOperationException`. Always provide a neutral if the input may be empty.
- **`null` items are silently dropped** by `aggregate` (the `item == null` skip) — this is idiomatic, not a bug.
- **Reactive/transaction note.** Each interior `apply` node is an ordinary `SealPile`/reactive value, so it participates in transactions and validity propagation like any derived value (see [concepts/transactions.md](../../../concepts/transactions.md)). A change at a leaf invalidates and re-recomputes only the `O(log n)` nodes on the path to the root; the diamond/glitch consistency is handled operationally by transaction propagation as everywhere else in Pile, not specially by `aggregate`. Note `sealedConstant` neutrals (used by `SidemostFulfilling`) go *invalid for the duration of a transaction opened on them*, unlike a true `Constant` (a documented `sealedConstant` caveat in [_index.md](_index.md)).

## Warts / tech debt

- **Code duplication.** The fold body is copy-pasted verbatim between the `Iterable` overload of `aggregate` and the `@SafeVarargs` overload — two identical ~35-line loops. A single private helper over `Iterable` (with the varargs form doing `Arrays.asList`) would remove the duplication.
- The `AggregationMonoid` javadoc on `constantNeutral`/`apply`/`inject` is empty (`@return` with no text); the real contract is reconstructed only from the implementations.
- `LeftmostFulfilling.NOT_NULL` / `RightmostFulfilling.NOT_NULL` are mutable `public static` (non-`final`) fields holding shared singletons — a stray reassignment would corrupt every `notNull()` caller.
