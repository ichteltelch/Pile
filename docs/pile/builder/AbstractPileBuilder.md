# `pile.builder.AbstractPileBuilder`

Abstract base that implements the fluent `Pile` build logic: it collects config, then in `build` assembles a `Recomputer`, installs it on the pre-existing `PileImpl`, and wires dependencies / bounds / dynamic-dependency scouting / threaded or delayed recomputation.

Source folder: `src`. File: `pile/builder/AbstractPileBuilder.java` (~1,005 lines).

`AbstractPileBuilder<Self extends AbstractPileBuilder<Self,V,E>, V extends PileImpl<E>, E> implements IPileBuilder<Self,V,E>`. The `Self` recursive type parameter is why the class is abstract — it makes every fluent setter return the concrete builder's own type. Concrete subclasses ([`PileBuilder`], [`AbstractSealPileBuilder`]/[`SealPileBuilder`]) are thin. See the [builder index](_index.md). The recompute/transaction *model* is in [PileImpl.md](../impl/PileImpl.md) and [concepts/transactions.md](../../concepts/transactions.md); this doc does not re-derive it.

## Key idea: the builder wraps an already-constructed `PileImpl`

The target `value` (the `PileImpl`) is passed to the constructor and **already exists** before configuration. Almost every fluent method is a thin forwarder that mutates `value` directly and returns `self`:

- `name`/`nameIfUnnamed`/`parent` set `value.avName` / `value.owner` fields directly.
- `init(val)` calls `value.set(val)` *during building*.
- `bracket`/`oldBracket`/`anyBracket` → `value._add*ValueBracket`.
- `corrector` → `value._addCorrector`; `upperBound`/`lowerBound`/`ordering` are buffered locally and applied in `build` (see Bounds).
- `dependOn(...)` → `value.addDependency(...)` immediately, optionally `setDependencyEssential`.
- `lazy`, `transformHandler`, `equivalence`, `debug`, `deferRecomputations`, `deferListeners`, `onChange` → corresponding `value.*` setters.

What is **deferred to `build`**: the recomputer assembly, the bounds-as-dependencies wiring, and `value._setRecompute(reco)`. So `build` is the only place that produces the `Recomputer`.

## What `build` actually does

1. **Bounds → dependencies.** `ICorrigibleBuilder.applyBounds(value, lowerBounds, upperBounds, ordering)` installs the buffered bounds; the resulting upper/lower bound values are then **added as ordinary dependencies** of `value` so the pile recomputes when a bound changes.
2. **Collapse the delay-switch list.** Multiple `setDelaySwitch(...)` suppliers are AND-ed into one `BooleanSupplier` (all-true ⇒ "don't delay this run"); 0 ⇒ `null`, 1 ⇒ that one. See "Delay switch" below for the inverted sense.
3. **Pick a recomputer shape** from which of the three recompute callbacks were set, plus `delay`, and build the matching `MyRecomputer` subclass (see the dispatch table).
4. **Configure scouting on the recomputer** — `dependenciesThatTriggerScouting`, `mayRemoveDynamicDependency`, `failHandler`.
5. **`value._setRecompute(reco)`** installs it ([PileImpl.md](../impl/PileImpl.md), `_setRecompute`), and returns `value`.

If neither a delayed nor immediate nor staged recomputer was configured, `reco` stays `null` and the pile is a plain settable value with no recompute.

## The three recompute callbacks and how they map

The fluent API offers three mutually-combinable callbacks (`IPileBuilder`):

- `recompute(Consumer<Recomputation>)` — the **delayed/normal** recomputer (`recomputer`, ).
- `recomputeImmediate(Consumer<Recomputation>)` — runs **synchronously** on the invalidating thread (`immediateRecomputer`, ).
- `recomputeStaged(Function<Recomputation, Runnable>)` — a **two-phase** recomputer: the function runs immediately (synchronous phase, may read dependencies / record dynamic deps) and **returns a `Runnable`** that is the deferred/threaded continuation (`combinedRecomputer`, ).

`delay(millis)` (, default `-1`) chooses the execution strategy for the non-immediate phase.

### Dispatch table (which `MyRecomputer` subclass `build` instantiates)

Resolved at . `combinedRecomputer` (staged) is exclusive — combining it with either other recomputer throws `IllegalArgumentException`.

| Config | `delay` | Recomputer class | Execution |
|---|---|---|---|
| immediate only | (ignored) | `MyRecomputationForImmediate` | synchronous, on caller thread |
| delayed only | `< 0` | `MyRecomputationForImmediate` | **delay `<0` means "treat the delayed recomputer as immediate"** — runs synchronously |
| delayed only | `== 0` | `MyRecomputerFor0Delay` | submitted to executor (no time delay) |
| delayed only | `> 0` | `MyRecomputeForDelayed` | `schedule(...)` after `delay` ms |
| immediate **and** delayed | `max(0, delay)` | a `combi` lambda → `MyRecomputerForStaged*` | immediate phase runs, then if not finished the delayed phase is scheduled/submitted |
| staged (`recomputeStaged`) | `max(0, delay)` | `MyRecomputerForStaged0Delay` (delay 0) or `MyRecomputerForStaged` (delay >0) | function runs sync; returned `Runnable` submitted/scheduled |

Note the "immediate + delayed" case is internally **synthesised into a staged recomputer**: the immediate consumer becomes the synchronous phase, and `->dreco.accept(re)` becomes the returned continuation. So "immediate+delayed" and "staged" share the `MyRecomputerForStaged*` execution machinery.

### Executor selection (threading via `pile.interop.exec`)

When a phase must run off-thread, `build` picks the executor:
- `delay == 0` → `StandardExecutors.unlimited` (unless a custom `pool(...)` was set).
- `delay > 0` → `StandardExecutors.delayed` (a `ScheduledExecutorService`); a custom `pool(...)` is **cast to `ScheduledExecutorService`** — supplying a non-scheduled pool with a positive delay throws `ClassCastException` at build time. Gotcha.

## `MyRecomputer` family — shared accept shape

All five concrete recomputers extend `MyRecomputer<E>` (, a `Recomputer`). The common steps in `accept(Recomputation re)`:

- **Dynamic dependencies:** if `dynamic`, `re.activateDynamicDependencies` at the top; otherwise if `re.isDependencyScout` it short-circuits with `re.fulfillInvalid` (a non-dynamic pile cannot scout) ( etc.).
- **Thread renaming** (debug only): gated on `RENAME_RECOMPUTATION_THREADS` (from `pile.interop.debug.DebugEnabled`), temporarily renames the worker thread to `re.suggestThreadName` for readable stack dumps.
- **Run the user callback** inside `Recomputations.withCurrentRecomputation(re)`, asserting `Recomputations.getCurrentRecomputation==re`.
- **`FulfillInvalid`** thrown by user code is caught and turned into `re.fulfillInvalid` (this is the [`FulfillInvalid`] sentinel exception, ).
- **Sentinel return values** from a staged function: `Piles.FULFILL_INVALID` → `re.fulfillInvalid`, `Piles.FULFILL_NULL` → `re.fulfill(null)`.
- **Delayed mode:** for off-thread phases, `re.enterDelayedMode` then `re.setThread(myExec.submit/schedule(...))` so the recomputation knows which `Future`/thread to interrupt on cancel ( etc.).
- **`forgetOldValue` (fov):** if `forgetOldValueOnDelayedRecompute` was set, `re.forgetOldValue` before/after the sync phase so the old value isn't retained across the delay.
- **Exception handling:** `Error` is logged and rethrown; other `Throwable` is logged (only if `logAllExceptions` or the recomputation isn't finished) and swallowed.
- **Unfulfilled guard (ug):** in a `finally`, `unfulfilledWarning(value, re, nullNotInvalid)` calls `re.fulfillInvalid` (or `re.fulfill(null)` if `unfulfilledIsNull`); if that actually did something it logs a warning + construction stack trace — i.e. it catches user callbacks that forgot to fulfill the recomputation. Disable with `noUnfulfilledGuard`.

The recompute lifecycle methods used here (`fulfill`, `fulfillInvalid`, `forgetOldValue`, `activateDynamicDependencies`, `enterDelayedMode`, `setThread`, `setInterruptible`, `isDependencyScout`) belong to `Recomputation` — see [PileImpl.md](../impl/PileImpl.md) (`MyRecomputation` is the concrete impl). The `pile.aspect.recompute` package (`Recomputation`/`Recomputer`/`Recomputations`) is not yet documented — forward-link.

## Dynamic dependencies & scouting

- `dynamicDependencies` sets the `dynamic` flag. With it on, every `accept` activates dynamic-dependency recording so the recomputer's reads register as dependencies and stale ones can be dropped.
- **Scouting** (a dependency-scout recomputation that runs only to discover which dependencies exist, without producing a value): controlled by `scoutIfInvalid(Predicate<Dependency>)`. In `build`, if `dynamicDependencies` is on and no predicate was set, scouting defaults to `Functional.CONST_TRUE` (scout for any invalid dependency); otherwise the predicate is used as-is. `MyRecomputer.useDependencyScoutingIfInvalid` treats a `null`/`CONST_FALSE` predicate as "never scout", a `null` dependency arg as "yes", else the predicate result.
- `mayRemoveDynamicDependency(BiPredicate)` lets you veto removal of a dynamically-recorded dependency; `MyRecomputer.mayRemoveDynamicDependency` falls back to the `Recomputer` default when unset.

## Other fluent config (by purpose)

- **Bounds / ordering:** `upperBound`/`lowerBound` buffer into `ArrayList`s applied in `build`; `ordering(comp)` wraps the comparator to throw `VetoException` on null operands, `orderingRaw(comp)` does not. Bound association keys: `upperBound`="max", `lowerBound`="min".
- **Unfulfilled handling:** `unfulfilledIsInvalid` / `unfulfilledIsNull` toggle `nullNotInvalid` — controls what the unfulfilled-guard fulfills with.
- **Failure:** `onFailedFulfill(Consumer)` sets `failHandler`, copied onto the recomputer.
- **Deferral:** `deferRecomputations`/`deferRecomputations(b)` → `value._setDeferringRecomputations`; `deferListeners(b)` → `value._setDeferringListeners` ([PileImpl.md](../impl/PileImpl.md)).
- **Pooling:** `pool(ExecutorService)` overrides the default executor.
- **`logAllExceptions`** (field default `true`, ) — no fluent setter in this class; when false, only exceptions on not-yet-finished recomputations are logged.

## Salient / surprising behavior

- **`delay(-1)` (the default) makes a `recompute(...)` callback run synchronously**, not on a pool — "delayed" only kicks in at `delay >= 0`. To get off-thread recompute you must call `delay(0)` (or positive).
- **Bounds become real dependencies**, so a bounded pile recomputes when its bound piles change, and the bound piles appear in its dependency set.
- **A custom `pool` with a positive delay is cast to `ScheduledExecutorService`** — wrong type ⇒ `ClassCastException` at build.
- **"immediate + delayed" is rewritten as a staged recomputer** under the hood; they are not two independent code paths.
- **`init(val)` writes during build**, opening/closing a transaction on the still-being-configured pile.

## Caveats & gotchas

- Combining `recomputeStaged` with `recompute`/`recomputeImmediate` throws `IllegalArgumentException`.
- The **delay switch sense is inverted/confusing:** `setDelaySwitch` suppliers are AND-ed; in `build` the combined supplier returns true only if *all* are true, and `doDelay = delaySwitch==null || !delaySwitch.getAsBoolean` — so a switch reading `true` means "do NOT delay" (run inline). The local lambda at  also early-returns `false` on the first false supplier — i.e. it computes logical AND, despite the surrounding `default:` suggesting multiple switches.
- The unfulfilled guard mutates state in a `finally` (it fulfills the recomputation); a recompute callback that legitimately defers fulfilment to another thread must keep the recomputation alive via the `Recomputation` API, or disable the guard with `noUnfulfilledGuard`.
- `MyRecomputerForStaged.accept` re-invokes `combi.apply(re)` a second time *before* throwing `IllegalStateException("Staged recomputer did not return a valid continuation")` when the staged function returns `null` without finishing — i.e. the user function runs twice in that error path (see Suspected bugs).

## Tech debt / warts

- Five nearly-identical `MyRecomputer` subclasses (staged / staged-0-delay / delayed / 0-delay / immediate) duplicate the thread-rename + try/catch/finally + unfulfilled-guard boilerplate; a dead commented-out `MyRecomputerForImmediate` remains.
- `if(combi==null && reco==null) reco=null;` is a no-op branch.
- `logAllExceptions` has a field but no fluent setter here.
- The inverted delay-switch semantics (true ⇒ don't delay) and the redundant `switch` over the switch-list size are easy to misread.

## Related

- [builder index](_index.md) · [`PileImpl`](../impl/PileImpl.md) · [`Pile`](../aspect/combinations/Pile.md) · [concepts/transactions.md](../../concepts/transactions.md) · [overview](../../overview.md).
- `pile.aspect.recompute` (`Recomputation`/`Recomputer`/`Recomputations`) — *doc pending*; the contract for the lifecycle methods used throughout the `MyRecomputer` family.
- `AbstractIndependentBuilder` *(doc pending)* — the `Independent` sibling base (remember-last-value wiring).
