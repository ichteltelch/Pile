# `pile.builder.IPileBuilder`

The fluent builder interface for [`Pile`](../aspect/combinations/Pile.md)s — configures recompute code, dependencies, dynamic-dependency recording, lazy/eager and threaded/delayed recomputation.

Source folder: `src`. File: `pile/builder/IPileBuilder.java`.

`IPileBuilder<Self, V extends Pile<E>, E>` is a *target* interface in the layered builder hierarchy (see the [builder index](_index.md)). It declares the fluent API surface for `Pile`s; the actual `build` wiring lives in `AbstractPileBuilder`/`PileBuilder` *(docs pending)*. You normally obtain a concrete builder from a [`Piles`](../impl/Piles/_index.md) factory and finish with `build`.

## Capability inheritance (the override/extend map)
`IPileBuilder` extends two capability interfaces, so the same configuration method returns the correct `Self` self-type:
- **`ICorrigibleBuilder`** *(doc pending)* — correctors & bounds: `corrector`, `neverNull`, `upperBound`/`lowerBound`/`bounds`, `ordering`/`orderingRaw`, `equivalence`, plus `valueBeingBuilt`. (`IPileBuilder` itself adds no corrector methods; they are inherited.)
- **`IListenValueBuilder`** *(doc pending)* — listener registration: `onChange`, `onChange_f`, `onChange_weak`(`_f`).
- Both extend the root **`IBuilder`** *(doc pending)* — `self`, `build`, `valueBeingBuilt`, `configure(presets)`, `runIfWeak`, `deferListeners`.

Note: the **sealable** capability (`ISealableBuilder`) is *not* on this interface — it is mixed into the sibling `ISealPileBuilder` (for `SealPile`s), not `IPileBuilder`.

## Methods by purpose

### Recompute code (how the value is computed)
The whole point of a `Pile`: supply a function that produces the value. Several overloads trade convenience for control over threading and the [`Recomputation`](../aspect/recompute) handle:
- `recompute(Consumer<Recomputation<E>>)` — full form; you receive the `Recomputation` and must call one of its `fulfill*` methods. Runs on a **separate thread** from the one that started the recomputation if a non-negative `delay` is set (see *Threading & delay* below) or if `recomputeImmediate` is also defined.
- `recompute(Supplier<E>)` (default) — convenience: just return the value; the wrapper calls `Recomputation.fulfill(value)`, maps the `FulfillInvalid` exception *(helper class, doc pending)* to `fulfillInvalid`, and swallows post-finish runtime errors. `recomputeS(Supplier)` is the same body, for when the compiler can't infer the lambda type.
- `recomputeImmediate(Consumer<Recomputation<E>>)` — code that runs **in the calling thread**; if it does not fulfill the recomputation, the `recompute(...)` code (if any) is then dispatched to another thread.
- `recomputeStaged(Function<Recomputation<E>, Runnable>)` — two-stage: the function runs immediately and returns either `null` (done) or a `Runnable` to continue in a separate thread. Do **not** combine with the other `recompute*` methods. Return `Piles.FULFILL_INVALID` / `Piles.FULFILL_NULL` to fulfill invalid / fulfill-with-null immediately.

The recompute machinery itself (`Recomputer`, `Recomputation`, package `pile.aspect.recompute`) is undocumented — see [`PileImpl.MyRecomputation`](../impl/PileImpl.md) (the `Recomputation` impl) and `PileImpl._setRecompute` for how the supplied code is stored and run. Briefly: a `Recomputation` is the per-run handle the recompute code uses to deliver a result (`fulfill`/`fulfillInvalid`/`fulfillRestoreOldValue`), record dynamic dependencies, and detect cancellation.

### Dependencies (`dependOn` and friends)
- `dependOn(Dependency)` / `dependOn(Dependency...)` — make the new `Pile` depend on the given value(s); it recomputes when they change.
- `dependOn(boolean essential, …)` — same, marking the dependency essential (see `Pile.setDependencyEssential`; an essential dependency's destruction cascades — cf. [`PileImpl`](../impl/PileImpl.md) `destroy`).
- `essential(Dependency…)` — mark an already-added dependency essential (acts directly on `valueBeingBuilt`).
- `depender(Depender)` — the inverse: add the value-being-built as a dependency of some other `Depender`.
- `whenChanged(Dependency…)` / `whenChanged(Dependency)` — convenience = `dependOn(true, …).build` (essential + build in one call).

### Dynamic dependencies (recorded at recompute time)
- `dynamicDependencies` — enable recording dependencies as the recompute code reads them. The javadoc spells out strict rules: access identical `Dependency` instances under equal conditions (else infinite scouting → `StackOverflowError`); access/record all relevant dependencies *before* significant computation; check for and `terminateDependencyScout` before heavy work; don't start a delayed recompute while scouting; suspend recording via `Piles.withCurrentRecomputation(...)` around code (e.g. the result's constructor) that touches fields you don't want to depend on.
- `dd` — convenience = `dynamicDependencies.build`.
- `scoutIfInvalid(Predicate/Collection/Dependency…)` + `dontScoutIfInvalid` — which currently-invalid dependencies still trigger scouting.
- `mayRemoveDynamicDependency(…)` / `mayNotRemoveDynamicDependency(…)` / `mayRemoveNonessentialDependencies` — a large family of overloads (predicate / bi-predicate / collection / varargs, each with a `notNegated` boolean variant) controlling which recorded dynamic dependencies may later be dropped when no longer read. `mayRemoveNonessentialDependencies` passes a `null` criterion (the impl's "all nonessential" default).

### Lazy / eager validation
- `lazy` — set the `Pile.setLazyValidating` flag: the value recomputes only when its value is actually requested, not eagerly when it becomes invalid. (Lazy validation is flagged immature project-wide — see [overview](../../overview.md).)

### Threading & delay of recomputation
- `delay(long millis)` — run the second (separate-thread) stage after a delay. `0` = start immediately in a separate `Thread`; **negative** = restore default behavior.
- `pool(ExecutorService)` — thread pool for non-immediate recomputations. **Gotcha:** if a *positive* `delay` is set, this must actually be a `ScheduledExecutorService`, or `build` throws `ClassCastException`.
- `limitedPool` — convenience = `pool(StandardExecutors.limited)`.
- `setDelaySwitch(BooleanSupplier)` — when the supplier returns `false`, recomputations run synchronously and without delay. Called multiple times → the conjunction of the conditions is used.
- `forgetOldValueOnDelayedRecompute` — drop the old value as soon as a separate-thread recompute starts.
- `noUnfulfilledGuard` — disable the check/warning/auto-fulfill that normally fires when recompute code returns without fulfilling its `Recomputation`. Use only when you transfer the computation to another thread yourself.
- `onFailedFulfill(Consumer<E>)` — handle a value handed to a `fulfill*` method but rejected because the `Recomputation` had already gone obsolete.

### Equivalence / change detection
- `equivalence(BiPredicate<E,E>)` — *(inherited from `ICorrigibleBuilder`)* the relation deciding whether the value "changed". Listed here because change detection is core to recompute propagation.

### Value brackets (lifecycle hooks on the held value)
[`ValueBracket`](../aspect/bracket) *(doc pending)* objects open/close around the value:
- `bracket(...)` — current value; `oldBracket(...)` — old value; `anyBracket(...)` — both (opened once if current == old).
- Each comes in `(bracket)`, `(boolean openNow, bracket)`, and `Iterable<…>` overloads.
- `inheritBrackets(boolean openNow, ReadValue template)` — copy brackets from a template, but only if the template is an `AbstractReadListenDependency` (silently no-ops otherwise — see gotchas).

### Initial value, naming, debugging, misc
- `init(E)` — **gotcha:** immediately calls `Independent.set(...)`; brackets/correctors added *afterwards* have no effect on this initial value.
- `name(String)` / `nameIfUnnamed(String)` — debug name. `parent(Object)` — set the owner reference (debug; also a GC anchor and used by `Piles.superDeepRevalidate`).
- `debug(DebugCallback)`, `transformHandler(TransformHandler)` / `recomputeOnTransform`, `tron` (enable trace recording — **leaks memory, debug only**, and only when `DebugEnabled.ET_TRACE`).

## Salient / surprising behavior
- The `recompute(Supplier)` default wrapper has a `finally { re.fulfillInvalid; }`: after a successful `fulfill(value)` this second call is a no-op (recomputation already finished), so it acts as a safety net only when the supplier returned without fulfilling. It also **swallows** runtime exceptions thrown after the recomputation is finished (treated as a late cancellation, ).
- Mutually exclusive recompute forms: use **either** `recomputeStaged` **or** the `recompute*` family, not both.
- Many "configuration" methods (`essential`, `depender`, `init`, `tron`, `inheritBrackets`) act directly on `valueBeingBuilt` rather than buffering in the builder — they take effect immediately, not at `build`.

## Caveats & gotchas
- `init(E)` bypasses later brackets/correctors (above).
- Positive `delay` + non-scheduled `pool` ⇒ `ClassCastException` at `build`.
- Dynamic-dependency recompute code must obey the strict rules above, or risk `StackOverflowError` (runaway scouting).
- `inheritBrackets` silently does nothing unless the template is an `AbstractReadListenDependency`.
- `tron` deliberately creates a memory leak; never ship it.

## Common tasks (how to…)
- **A computed value reacting to inputs:** `Piles.….recompute( -> a.get + b.get).whenChanged(a, b)` (essential deps + build).
- **Heavy/blocking recompute off the EDT:** add `recompute(consumer)` + `delay(0)` (or a positive delay) and a `pool(...)`; fulfill from the worker thread.
- **Show a placeholder immediately, then compute:** `recomputeImmediate(...)` (fast/placeholder) + `recompute(...)` (slow, runs on another thread if immediate didn't fulfill); or `recomputeStaged(...)`.
- **Dependencies discovered at runtime:** `dynamicDependencies` (or `dd`), following the recording rules; tune with `scoutIfInvalid` / `mayRemoveDynamicDependency`.
- **Compute only on demand:** add `lazy`.

## Tech debt / warts
- The `recompute*` / dynamic-dependency contract is enforced by convention and javadoc prose, not the type system — easy to misuse (the `StackOverflowError` failure mode is harsh).
- `delay` overloads the `long` argument with three meanings (negative = default, 0 = immediate-separate-thread, positive = scheduled) — non-obvious.
- The undocumented `Recomputer`/`Recomputation` collaborators (`pile.aspect.recompute`) carry the real recompute semantics; this builder only configures them. Those docs are pending.

## Related
- [builder index](_index.md) · [overview](../../overview.md) · [`PileImpl`](../impl/PileImpl.md) (recompute storage & `MyRecomputation`) · [`Pile`](../aspect/combinations/Pile.md) (the built type) · [transactions.md](../../concepts/transactions.md) (the recompute gate & propagation model) · `Recomputation`/`Recomputer` in `pile.aspect.recompute` *(docs pending)*.
