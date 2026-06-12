# `PileBool` — `Pile<Boolean>` specialized to `boolean`, with the boolean combinator algebra

`PileBool` is the boolean specialization of [`Pile<Boolean>`](../aspect/combinations/Pile.md): same reactive value, plus primitive `boolean` accessors and a large catalogue of **static factories** that build derived reactive booleans (logic gates, choice/multiplexers, comparisons, dynamic monoid aggregators). It adds **no new reactive semantics** — read [`Pile`](../aspect/combinations/Pile.md) / [`PileImpl`](../impl/PileImpl.md) for validity, transactions, recomputation; this doc is the *delta*.

Source folder: `src`. File: `src/pile/specialized_bool/PileBool.java` (interface) and `src/pile/specialized_bool/PileBoolImpl.java` (the concrete `PileImpl` subclass).

Up: [bool index](_index.md) · [overview](../../overview.md). See also: [generic `Pile`](../aspect/combinations/Pile.md) · [`PileImpl`](../impl/PileImpl.md) · [`Piles` aggregation](../impl/Piles/aggregation.md) · combinations: [`combinations/_index.md`](combinations/_index.md).

## Where each thing lives (the specialization split)

`PileBool extends Depender, ReadWriteListenDependencyBool, Pile<Boolean>`. The specialization is spread over three roles — know which file to open:

- **Primitive `boolean` accessors** are inherited from `ReadValueBool` (in `combinations`): `isTrue()`, `isFalse()`, `getAsBoolean()` (= `isTrue`), `threeWay(ifTrue, ifFalse, ifNull)`, plus the static `ReadValueBool.isTrue(Supplier)` / `isFalse(Supplier)` null-safe testers. These are *value-snapshot* tests (`Boolean.TRUE.equals(get())`), so a `null` or invalid read reads as neither true nor false.
- **Instance-side reactive operators** — `x.and(y)`, `x.or(y)`, `x.not()`, the whole `choose*`/`chooseConst*` multiplexer family, `mapToInt()`, `validIfTrue()`, `readOnly()`, `overridable()` — live on [`ReadDependencyBool`](combinations/_index.md) (read side) and `ReadWriteListenDependencyBool` (write side). They are thin `default` methods that **delegate to the static factories on `PileBool`** (e.g. `ReadDependencyBool.and` → `PileBool.and(this, op2)`).
- **The static factory catalogue itself** lives on `PileBool` (this file): every gate, comparison, aggregator, builder shortcut below is a `static` method. The interface declares essentially **no abstract instance methods of its own** beyond `setName` and the `not()` override; almost everything `PileBool` "adds" is static.

So: to *use* `a & b` reactively you call the instance method `a.and(b)`; the algebra it routes to is the static `PileBool.and`.

## Primitive accessors & null/three-valued logic

Booleans here are **three-valued**: `TRUE`, `FALSE`, and `null` (plus the orthogonal *invalid* state from the base `Pile`). Every gate has an explicit `null` column in its javadoc truth table. The accessor methods collapse this: `isTrue()`/`isFalse()` treat `null` as "not that"; `threeWay` is the explicit three-way switch. Do not assume `!isTrue()` means false.

## The boolean operator catalogue (static gates)

All gates are `static` on `PileBool`, take `ReadDependency<? extends Boolean>` operands, and return a sealed [`SealBool`](_index.md) built via a `SealPileBuilder`. Each carries a javadoc **truth table** (rows/cols `I`=invalid, `N`=null, `T`/`F`) — the variants differ precisely in how they treat `null` and invalidity. All special-case `op1 == op2` (identity) to a trivial passthrough.

### NOT
- `not(ReadWriteDependency)` / `notRW(...)` — **writable** inverter: reads `!input`, and writing the result writes `!v` back into `input` (bidirectional, via the builder's `seal(...)` redirect). `null`↔`null`.
- `not(ReadDependency)` / `notRO(...)` — read-only inverter (default-sealed, no write-back).
- Overloads are resolved by operand type; the `ReadWriteDependency` overload picks `notRW`.

### AND family
- `and` — null-propagating bitwise `&`: any `null` operand → `null`.
- `and2` — short-circuit-flavoured: a known `false` operand → `FALSE` even if the other is `null`/unknown; else `null` if either is `null`, else `TRUE`.
- `and3` — validity-aware variant operating on `validBuffer_memo()` + `validity()`; **dynamically re-wires its dependencies** (drops the irrelevant operand when one side decides the result) so it can stay valid on a partially-invalid input. (Contains a stray `System.err.println("&&&: both invalid")` — see *Warts*.)
- `andNn` ("null-as-neutral"): treats `null` as the identity `TRUE`-ish neutral, so a single non-null operand decides; both `null` → `null`.
- `andScd` — "short-circuit dependency": uses `validity()` of each operand and `dynamicDependencies()` to evaluate left-to-right, going **invalid** when the relevant operand is invalid.

### OR family
Mirror images of the AND family: `or` (null-propagating `|`), `or2`, `or3` (validity-aware, dynamically rewiring), `orNn` (null-neutral), `orScd` (short-circuit). Same truth-table discipline with `TRUE` as the absorbing element.

### XOR / NAND / NOR / IMPLIES / equivalence — *not present as named gates*
There is **no** `xor`, `nand`, `nor`, or `implies` static gate on `PileBool`. The boolean delta covers AND/OR/NOT only. The XOR-shaped operations are:
- `cNot(input, control)` — a *writable controlled NOT*: the result is `input XOR control`; writing it writes `input XOR control` back into `input`. This is the only first-class XOR, and it is built directly (not via the gate template) using `Piles.sealedNoInitBool()`.
- `BoolMonoidOp.XOR` — an XOR monoid used only by the **dynamic aggregation** path (below).

Equivalence/implication are expressed through the **comparison family** instead (`equalityComparison` with chosen true/false outputs; an "implies" is just `not(a).or(b)` if you need it). If you expect a gate by those names, you will not find one — compose from the primitives or use a comparison.

## n-ary aggregation — routing through `Piles.aggregate`

Two distinct n-ary mechanisms exist; do not confuse them.

### 1. Static tree-fold reductions (`conjunction`/`disjunction`)
`conjunction[/2/3]`, `conjunctionNa`, `disjunction[/2/3]`, `disjunctionNa` (each with an `Iterable` and a varargs overload) reduce many reactive booleans to one. They are one-liners that pick the matching **`BoolAggregator` monoid** and a pruning predicate and call `Piles.aggregate`, which builds a *balanced binary tree* of pairwise gate applications — `O(log n)` propagation. The monoids (`andAggregator`, `and2Aggregator`, `and3Aggregator`, `andNnAggregator`, `or…Aggregator`) are `BoolAggregator` instances; the pairwise `op` is exactly the gate of the same name; the pruning `isNeutral` test drops the literal `Piles.TRUE` / `Piles.FALSE` / `constNull()` constant. See [aggregation.md](../impl/Piles/aggregation.md) for the fold algorithm and the `BoolAggregator`/`AggregationMonoid` contract (the `BoolAggregator` class is defined here in `PileBool`; the `*Aggregator` constants live in `Piles` to dodge cyclic init).

### 2. Dynamic monoid aggregation (`dynamicAnd`/`dynamicOr`/`dynamicXor`)
The nested `BoolMonoidOp` interface (`AND`/`OR`/`XOR` constants + `OR_NEUTRAL`/`AND_NEUTRAL`/`XOR_NEUTRAL`) plus `dynamicAnd`/`dynamicOr`/`dynamicXor`/`buildDynamic*` build a `PileBoolImpl` that aggregates **over whatever `Dependency`s happen to be attached** that are also `ReadValueBool` instances — not a fixed operand list. `BoolMonoidOp.configurator` installs a recompute that walks `giveDependencies`, folds the bool-valued ones with the chosen monoid op, and throws `FulfillInvalid` if any operand is `null`; an empty set yields the monoid's neutral. This is the only place XOR-aggregation exists. (`configurator`'s default name is the copy-pasted `"Dynamic double aggregator"` — see *Warts*.)

### `binOp` — arbitrary boolean-producing combiner
`binOp(op1, op2, BiPredicate|BiFunction)` routes through `Piles.makeBinOp` to make a `SealBool` from any two reactive values and a `boolean`-valued function — the generic escape hatch the comparisons are built on.

## `choose` / `chooseConst` / `chooseWritable` — reactive multiplexers

The choice family is the boolean's *if/else over reactive branches*, keyed on the three-valued chooser:
- `choose(chooser, ifTrue, ifFalse, ifNull)` and the 3-arg `chooseConst(chooser, ifTrue, ifFalse, ifNull)` (constant branches) — read-only selection; built with `dynamicDependencies()` + `scoutIfInvalid` so only the active branch is depended on.
- `chooseWritable(...)` — **bidirectional**: writes to the result are forwarded (and corrections applied) to the *currently active* branch via the builder's `corrector` + `seal` redirect.
- The public statics delegate to the underscore primitives `_choose` / `_chooseWritable` / `_chooseConst`, which take a `template` `SealPile` so the same logic produces a typed result (`SealInt`, `SealBool`, …). The typed `chooseInt`/`chooseBool`/`chooseDouble`/`chooseString` conveniences live on [`ReadDependencyBool`](combinations/_index.md) and just pass the matching template.
- If `ifNull` is omitted/`null`, it defaults to `Piles.constNull()`.
- `chooser` invalid → the recompute issues `fulfillRetry()` (waits rather than committing a wrong branch).

## Builder shortcuts & misc factories

- `rb()` / `sb()` / `ib()` / `ib(init)` — make a `PileBuilder<PileBoolImpl>` / `SealPileBuilder<SealBool>` / `IndependentBuilder<IndependentBool>`, each pre-set with `ordering(naturalOrder())`. These are the idiomatic entry points for hand-building a reactive boolean.
- `readOnlyWrapper(in)` / `readOnlyWrapperIdempotent(in)` — bool-typed specializations of the `Piles` wrappers; the idempotent form returns the input unchanged when it is already a default-sealed `SealBool` or a `ConstantBool`.
- `comparison(...)` / `equalityComparison(...)` and the sugar `equal`/`unequal`/`lessThan`/`lessThanOrEqual`/`greaterThan`/`greaterThanOrEqual` — make a `SealBool` from comparing two reactive values (or a value and a constant, in all three arg orders), via natural ordering, a `Comparator`, or a `BiPredicate` equivalence. `nullIsLess` (a `Boolean`) controls null sorting; a **`null` `nullIsLess` makes the result `null`** when a null is compared. These are `makeBinOp`/`mapToBool` wrappers.
- `validIfTrue(b)` — a `Dependency` that is valid iff `b` is true; the building block for "only recompute while a flag holds".
- `whileTrueRepeat(condition, intervalMillis, mayInterrupt, scheduler, job)` — schedules `job` on a `ScheduledExecutorService` repeatedly *while `condition` is true*, returning a `Pile<ScheduledFuture<?>>` you must keep a reference to (and `destroy()` to stop); a closing bracket cancels the future. The lifecycle gotcha is in its own javadoc — losing the reference lets GC silently end the effect.

## `PileBoolImpl` — `PileImpl<Boolean>` + `PileBool`

`PileBoolImpl extends PileImpl<Boolean> implements PileBool` adds almost nothing — all reactive machinery is inherited from [`PileImpl`](../impl/PileImpl.md). The only deltas:
- `setName` / `setNull` covariant overrides returning `PileBoolImpl`.
- A **memoized `not()`**: double-checked-locked cache of the inverter so repeated `x.not()` calls return the same writable inverted view rather than building a new one each time (uses the inherited `mutex`). This is the one behavioural addition. Note `PileBool` *also* declares a `default not()` delegating to `ReadWriteListenDependencyBool.super.not()`; `PileBoolImpl` overrides that to add the caching.

## Caveats & gotchas

- **`isTrue()`/`isFalse()` are not complementary** — both are `false` when the value is `null` or invalid. Use `threeWay` for genuine three-way logic.
- **Pick the right gate for `null`/invalidity semantics.** `and` vs `and2` vs `and3` vs `andNn` vs `andScd` differ *only* in their null/invalid columns; the plain `and`/`or` are null-*propagating*, the `Nn` forms treat `null` as neutral, `and3`/`or3`/`*Scd` are validity-aware and rewire dependencies. Read the javadoc truth table before choosing.
- **No `xor`/`nand`/`nor`/`implies` gates.** Compose from `and`/`or`/`not`, use a `comparison`, or use `cNot` (writable XOR) / `dynamicXor`. Don't go looking for the named methods.
- **`chooseWritable` writes hit only the active branch** — switching the chooser redirects future writes elsewhere; the previously-written branch is not updated.
- **`whileTrueRepeat` result must be retained** — it is GC-sensitive by design and ends silently if collected.
- Many factories build *sealed* values (`SealBool`); they are not writable unless explicitly the `*RW`/`chooseWritable`/`cNot`/`notRW` forms.

## Tech debt / warts

- `and3` (and by symmetry `or3`'s neighbourhood) contains a leftover `System.err.println("&&&: both invalid")` debug print in its recompute — stray console output in a library path.
- `BoolMonoidOp.configurator` names the built value `"Dynamic double aggregator"` — a copy-paste from the double specialization; should read "boolean".
- The javadoc on the `buildDynamicDisjunction`/`buildDynamicConjunction`/`buildDynamicXor` helpers all say "disjunction" and reference `ReadValueDouble` — more copy-paste from the double family (they actually conjunct/xor and filter on `ReadValueBool`).
- Large amounts of commented-out dead code (`_choose_good`, `_choose_bad`) remain inline in `_choose`.
- The comparison/sugar block is heavily combinatorial (every op × {value,value}/{value,const}/{const,value} × {natural, Comparator, equivalence}) — a lot of near-duplicate one-liners.
