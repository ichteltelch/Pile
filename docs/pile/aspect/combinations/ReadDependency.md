# `pile.aspect.combinations.ReadDependency`

Read-and-depend combination: unions [`ReadValue`](../ReadValue.md) + [`Dependency`](../Dependency.md) and adds the big default-method *operator surface* (`map*`, `field*`, comparisons, `readOnly`, `overridable`) that builds derived `SealPile`s depending on this value.

Source folder: `src`. File: `pile/aspect/combinations/ReadDependency.java`.

`public interface ReadDependency<E> extends ReadValue<E>, Dependency`. This is the first assembled *combination* in the contract chain `ReadDependency` → [`ReadListenDependency`](ReadListenDependency.md) → … → the capstone [`Pile`](Pile.md). See the [aspect index](../_index.md) and the [overview](../../../overview.md) for where it sits.

## What it unions

- [`ReadValue<E>`](../ReadValue.md) — you can *read* the value (`get`, validity-aware reads, `validity`, …).
- [`Dependency`](../Dependency.md) — others can *depend on* the value (depender registration, `isValid`/`isValidAsync`, deep-revalidate registry).

The combination is the minimum a `Pile` needs in order to *consume* another value as a recompute input: something it can both read and register a dependency edge against. The interface javadoc states exactly this purpose — "define `Pile`s that depend on instances of this class and recompute themselves based on their values".

## What it ADDS over the two aspects

Everything below is a **default method** unless marked abstract. They only make sense for the combination because each one builds a *new derived value* that **depends on `this`** (a `Dependency` role) and **recomputes from `this.get`** (a `ReadValue` role) — neither aspect alone could express it. They are thin sugar over the builders in `pile.builder` and the static hub `Piles`/`PileBool`/`PileInt`/… ; the real wiring lives there, not here.

### Mapping (derive a value through a function)
- `_mapBuilder`, `_mapSetup` — the shared core: `new SealPileBuilder<>(v).recompute(->fn.apply(get)).dependOn(true, this)`. Every public `map*` routes through these.
- `map`, `mapToBool`/`mapToInt`/`mapToDouble`/`mapToString` (Boxed-`Function` form), and the primitive twins `mapPrimitive`/`mapToBoolP`/`mapToIntP`/`mapToDoubleP` (taking `Predicate`/`ToIntFunction`/`ToDoubleFunction`). Each has a 2-arg overload taking a `Consumer<? super SealPileBuilder<…>>` for extra builder configuration.
- The `…P` ("primitive") variants exist only because Java has no extension methods — they avoid boxing the mapper, returning the typed `SealBool`/`SealInt`/`SealDouble` from `pile.specialized_*`.

### Field extraction (follow a reactive field of the wrapped value)
- `field` / `fieldBuilder(nullable, extract)` and the typed `fieldBool/Double/Int/String` (+ builder twins) — build a `SealPile` that mirrors a `ReadDependency`-valued **field of `this.get`**, re-targeting when `this` changes. Delegates to `Piles.sb.setupField(this, nullable, extract)` (and `PileBool/Int/…` for the typed forms).
- `writableField` / `writableFieldBuilder` (+ typed twins) — same, but the extracted field is a [`ReadWriteDependency`](ReadWriteDependency.md) so **writes to the returned `SealPile` propagate into the field** if it currently exists; routes through `setupWritableField`.
- **`nullable` gotcha:** the no-arg `field`/`writableField` pass `nullable=false`, so when `this` holds `null` the `extract` function is **not called** and the derived pile is **invalid**. Pass `true` via the builder to have `extract` invoked on `null`.

### Comparisons (derive a boolean/int from two values)
- `isEqual`/`isUnequal` (+ `…Const`, + `BiPredicate` equivalence overloads) → `SealBool` via `PileBool.equalityComparison(...)`.
- `compareTo`/`compareToConst` → `SealInt`; `greaterThan`/`lessThan`/`…OrEqual` (+ `…Const`) → `SealBool`, all taking an explicit `Comparator`, via `PileInt.comparison` / `PileBool.greaterThan` etc..
- Note these shadow names you'd expect from `Object`/`Comparable` (`isEqual` ≠ `equals`, `compareTo` here returns a *reactive* `SealInt`, not an `int`).

### Wrappers / lifecycle helpers
- `readOnly` → a `SealPile<E>` view that tracks this value but rejects writes; `Piles.readOnlyWrapper(this)`.
- `overridable` → a `PileImpl<E>` that follows this value but, when written to, holds the override until this value next changes or goes invalid; built as `Piles.compute(this::get).name(dependencyName+"*").whenChanged(this)`.
- `destroyIfMarkedDisposable` (default) — calls `destroy` only if `this instanceof HasAssociations` and `isMarkedDisposable`.

### The four genuinely abstract members (implemented downstream)
These are *not* defaults; subclasses supply the behavior:
- `willNeverChange` — guaranteed-immutable query. True for a [`Constant`](../../impl/Constant.md), and for a [`Sealable`](../Sealable.md) sealed with the default interceptor and no recomputer (per its javadoc, ).
- `destroy` — tear down the instance; **also destroys dependers for which this is an *essential* dependency**.
- `_getEquivalence` → the `BiPredicate` used to decide whether a new wrapped value really counts as a change. This is the same equivalence that `ReadValue.is(...)` is allowed to use (see [`ReadValue` § is](../ReadValue.md)).
- `nullOrInvalid` → a reactive `ReadListenDependencyBool` that is `true` when this value is `null` or observably invalid; **covariantly re-narrows** `ReadValue.nullOrInvalid`'s abstract declaration to the bool-specialized type.

## Override map (where the real behavior lives)

The four abstract members are realized on the shared base and the concrete piles:
- **`AbstractReadListenDependency`** (the base under all general piles) provides the common implementations of `destroy`, `_getEquivalence`, `nullOrInvalid`, and most validity plumbing inherited from `ReadValue`/`Dependency`.
- **[`PileImpl`](../../impl/PileImpl.md)** — full recomputing pile: `willNeverChange` is generally `false` (it has a recomputer / can be invalidated); `_getEquivalence` returns whatever equivalence was configured on the builder.
- **[`Independent`](../../impl/Independent.md)** — stays valid through transactions; references `_getEquivalence` at `Independent.java`.
- **[`SealPile`](../../impl/SealPile.md)** — once sealed without a recomputer and with the default interceptor, `willNeverChange` becomes `true` (it can no longer be written or recomputed); `SealPile.java` is in this area.
- **[`Constant`](../../impl/Constant.md)** — `willNeverChange` ≡ `true`; reads collapse to the fixed value.

The default operator methods (`map*`/`field*`/comparisons) are **not** overridden downstream — they are pure factories and behave identically for every implementor; their behavior is determined entirely by the builder/`Piles` code they delegate to.

## Salient / surprising behavior

- **This interface is ~90% factory sugar.** Only four members carry state-dependent semantics; the rest construct new `SealPile`s. Reading the source top-to-bottom is mostly overloaded `map`/`field` permutations.
- **Derived values capture a hard dependency on `this`** (`dependOn(true, this)` — the `true` is "essential"), so a `map`/`field`/comparison result keeps `this` reachable and is itself torn down if `this` is `destroy`ed (essential-dependency destruction, ).
- **`overridable` names the result `<thisname>*`** — a handy debug tell that a value is an override wrapper.
- **`_getEquivalence` is load-bearing for change detection**, not just `equals`: it decides whether dependers are even notified, so a custom equivalence can silently suppress propagation.

## Caveats & gotchas

- **`field`/`writableField` default to `nullable=false`** → derived pile goes *invalid* (not null) when the source is null; use the `*Builder(true, …)` form if you need `extract` to run on null.
- **`map(fn).get` re-invokes `fn` on each recompute via `get`**, inheriting all of `get`'s side effects ([observe-invalidity / dependency-recording](../ReadValue.md)); don't put expensive or side-effecting work in a map function expecting it to run once.
- **Name collisions with `java.lang`:** `isEqual`/`compareTo`/`greaterThan` build *reactive* piles, not eager comparisons — easy to misread.
- **`destroyIfMarkedDisposable` is a no-op unless the runtime type is `HasAssociations`**; on a bare `ReadDependency` it always returns `false`.

## Common tasks (how to…)

- **Derive a value through a function:** `dep.map(x -> f(x))` (or `mapToInt`/`mapToBool`/… for typed results; `…P` variants to avoid boxing).
- **Configure the derived pile (name, threading, etc.):** use the 2-arg overload, e.g. `dep.map(f, b -> b.name("derived"))`, or grab `dep._mapBuilder(new SealPile<>, f)` directly.
- **Follow a reactive field of the wrapped value:** `dep.field(x -> x.someReactiveField)`; writable: `dep.writableField(x -> x.someReadWriteField)`.
- **Make a read-only view:** `dep.readOnly`. **Make an overrideable follower:** `dep.overridable`.
- **Reactively compare:** `dep.greaterThan(other, cmp)`, `dep.isEqualConst(value)`, `dep.compareTo(other, cmp)`.
- **Ask whether it can ever change:** `dep.willNeverChange` (e.g. to skip wiring a listener).

## Tech debt / warts

- The `map*`/`field*` family is a large hand-rolled overload matrix (boxed × primitive × with/without config × five element types) — the project [overview](../../../overview.md) notes Java's missing extension methods force these specialized parallel hierarchies.
- Pervasive empty `@param`/`@return` javadoc tags (e.g. `@param Additional configuration…` with no name, , ), consistent with the project-wide "unsystematic API" note.
- `map`'s second overload returns `SealPile<? extends F>` while its 1-arg sibling returns `SealPile<F>` — a minor, surprising signature asymmetry.

## Related

- Aspects unioned: [`ReadValue`](../ReadValue.md), [`Dependency`](../Dependency.md).
- Next in the chain: [`ReadListenDependency`](ReadListenDependency.md), [`ReadWriteDependency`](ReadWriteDependency.md) (used by `writableField`), capstone [`Pile`](Pile.md).
- Builders/hub the defaults delegate to: `pile.builder.SealPileBuilder`, `pile.impl.Piles`, `pile.specialized_*`.
- [aspect index](../_index.md) · [overview](../../../overview.md) · [concepts/transactions.md](../../../concepts/transactions.md).
