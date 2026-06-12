# `pile.aspect.recompute.Recomputer`

The functional interface for user-supplied recompute code: `accept(Recomputation)` computes and fulfills a [`Pile`](../combinations/Pile.md)'s value, plus three policy hooks governing dependency scouting and dynamic-dependency removal.

Source folder: `src`. File: `pile/aspect/recompute/Recomputer.java` (~57 lines).

`Recomputer<E> extends Consumer<Recomputation<E>>` and is `@FunctionalInterface` — the single abstract method is the inherited `accept(Recomputation<E>)`. The three policy methods are all `default`, so a bare lambda is a complete `Recomputer`. See the sibling [`Recomputation`](Recomputation.md) for the handle `accept` receives, the package [`_index.md`](_index.md), and [overview](../../../overview.md).

## What it's for

A `Pile` recomputes its value by handing its installed `Recomputer` a fresh `Recomputation` and calling `accept`. The recompute code reads its dependencies, computes a value, and **fulfills** the `Recomputation` (`fulfill` / `fulfillInvalid` / `fulfillRestoreOldValue`). A `Recomputer` that doesn't fulfill leaves the value mid-recompute (the framework warns; see "Caveats"). The interface also configures *dependency scouting* — re-running the recompute in a discovery mode to re-derive which `Dependency`s are actually needed.

## Methods by purpose

- **`accept(Recomputation<E>)`** *(inherited SAM)* — the actual recompute. Called by `PileImpl` when the value needs computing. Receives a `Recomputation` already in normal or scouting mode (`Recomputation.isDependencyScout`).
- **`useDependencyScouting`** — master switch: if `true`, an explicit `set` or `permaInvalidate` on the value hands `accept` a `Recomputation` in *dependency-scouting* mode so the recompute can re-record its dynamic dependencies. Default `false`.
- **`useDependencyScoutingIfInvalid(Dependency d)`** — finer policy: when scouting is enabled and some other dependency finished changing but the value still depends on the invalid non-essential `d`, should scouting run to potentially drop the now-spurious `d`? Default just delegates to `useDependencyScouting`. Per its javadoc, a `null` argument means "return `true` unless it's certain `false` would be returned for every argument" — i.e. `null` is a "would scouting ever fire?" probe.
- **`mayRemoveDynamicDependency(Dependency dy, Depender dr)`** — may `dy` be removed from `dr` during scouting? Default: `true` iff `dy` is **non-essential** to `dr` (`!dr.isEssential(dy)`, `Recomputer.java`). Essential dependencies are never auto-removed.

## The override map

Almost all real `Recomputer`s are the builder's package-private `AbstractPileBuilder.MyRecomputer<E>` and its subclasses — you rarely implement this interface by hand except as a plain lambda.

- **`AbstractPileBuilder.MyRecomputer<E>`** is the abstract base for builder-produced recomputers. It overrides the policy hooks to read builder-configured fields:
  - `useDependencyScouting` → returns the builder's `dynamic` flag.
  - `useDependencyScoutingIfInvalid(d)` → uses the `dependenciesThatTriggerScouting` predicate: `false` if that predicate is null or `Functional.CONST_FALSE`; `true` if `d==null`; else `predicate.test(d)`. So the `null`-probe contract is honored explicitly here.
  - `mayRemoveDynamicDependency(dy,dr)` → uses the `mayRemoveDynamicDependency` `BiPredicate` if set, else **falls back to the interface default** via `Recomputer.super.mayRemoveDynamicDependency(dy,dr)`.
- The concrete subclasses — `MyRecomputationForImmediate`, `MyRecomputerFor0Delay`, `MyRecomputeForDelayed` (instantiated in `build` around `AbstractPileBuilder.java`) — supply `accept`. They wrap the user's `Consumer<Recomputation>` with `FulfillInvalid`-exception handling, exception logging, and (for delayed variants) executor dispatch and `forgetOldValue`. The `combi` path chains an immediate + a delayed recomputer.
- No override in `PileImpl` / `SealPile` / `Independent` themselves — they are the *callers/holders* of a `Recomputer`, not implementors. `Independent` has no recomputer (it is always-valid). `Constant` likewise.

## How a Recomputer is installed and invoked

- **Built** by `AbstractPileBuilder.build`: based on `recomputer` / `immediateRecomputer` / `delay` / `dynamicDependencies`, it picks one of the `MyRecomputer` subclasses and assigns it to `reco`, ultimately handed to the value.
- **Installed** by `PileImpl._setRecompute(Recomputer)`: stores it in the `recompute` field under `mutex`; `null` cancels any pending recomputation. A non-null install immediately `__scheduleRecomputation(true)` + `__startPendingRecompute(false)`, so setting a recomputer can trigger a recompute right away. Declared on [`Pile`](../combinations/Pile.md); `_isRecomputerDefined` reports whether one is set.
- **Invoked / consulted** inside `PileImpl`:
  - The policy hooks are consulted during dependency-change handling and scouting decisions, e.g. `useDependencyScouting` at `PileImpl.java`, `mayRemoveDynamicDependency` at  (the scouting loop walks `_thisDependsOn`; if any invalid dependency may **not** be removed, scouting is abandoned — `scout=false`), and again around , , .
  - `accept` is driven through `PileImpl.MyRecomputation` (the `Recomputation` impl) — see [`PileImpl`](../../impl/PileImpl.md) and the transaction/recompute model in [transactions.md](../../../concepts/transactions.md).

## Salient / surprising behavior

- **A lambda is a full Recomputer.** Because the three policy methods default and `accept` is the SAM, `value._setRecompute(reco -> reco.fulfill(...))` works; scouting is simply off (`useDependencyScouting==false`).
- **Scouting requires removability of all invalid dependencies.** Even with scouting enabled, `PileImpl` only actually scouts if every *invalid* current dependency satisfies `mayRemoveDynamicDependency` (i.e. is non-essential by default). One essential-and-invalid dependency suppresses scouting.
- **`null` to `useDependencyScoutingIfInvalid` is a capability probe**, not a real dependency — answer optimistically (`true`) unless you know scouting can never fire. The builder base implements this contract; hand-rolled overrides should too.
- **Default `mayRemoveDynamicDependency` keys off essentialness**, so marking a dynamic dependency essential pins it against scouting removal.

## Caveats & gotchas

- **Not fulfilling is a soft error.** `accept` should fulfill the `Recomputation` on every path; the builder wrappers convert a thrown `FulfillInvalid` into `fulfillInvalid` and log other unfinished exceptions. A hand-written `Recomputer` gets no such safety net — an un-fulfilled `Recomputation` leaves the value stuck and triggers an "unfulfilled" warning.
- **In scouting mode, don't blindly compute** — check `Recomputation.isDependencyScout`. Scouting runs may fulfill invalid / are meant primarily to re-record dependencies (the commented-out reference recomputer at `AbstractPileBuilder.java` shows the idiom: scout ⇒ `fulfillInvalid`).
- **Hooks are advisory inputs to `PileImpl`'s logic, not commands** — returning `true` from `useDependencyScouting` does not guarantee scouting; surrounding conditions (invalid essential deps, transaction state) can still suppress it.

## Tech debt / warts

- Two policy methods (`useDependencyScouting` vs `useDependencyScoutingIfInvalid`) with an overlapping, partly-commented-out decision path in `PileImpl` — the relationship is subtle and the call sites carry dead commented alternatives.
- The `null`-argument convention on `useDependencyScoutingIfInvalid` is contract-by-javadoc only; nothing enforces that overrides honor it.
- Large dead commented `MyRecomputerForImmediate` block shadows the live `MyRecomputationForImmediate`.

## Related

- [`Recomputation`](Recomputation.md) — the handle `accept` fulfills. · [`PileImpl`](../../impl/PileImpl.md) — installs (`_setRecompute`) and drives recomputers (`MyRecomputation`). · [`IPileBuilder`](../../builder/IPileBuilder.md) — fluent configuration that produces `Recomputer`s. · [transactions.md](../../../concepts/transactions.md) — the recompute/transaction model. · [package `_index.md`](_index.md) · [overview](../../../overview.md).
