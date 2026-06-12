# `Recomputation`

The handle handed to recompute code to drive an in-progress recomputation: fulfill it with a value, fulfill-invalid, restore the old value, query/record dependencies, transfer to another thread, or cancel.

Source folder: `src` — `pile/aspect/recompute/Recomputation.java`.

`Recomputation<E>` is the interface a [`Recomputer`](_index.md) receives (`accept(Recomputation)`) for the single value it is recomputing. It extends [`DependencyRecorder`](_index.md) (it *is* the recorder during dynamic-dependency tracking). The general-purpose implementation, where all the live mechanics actually happen, is **`PileImpl.MyRecomputation`** — see [PileImpl](../../impl/PileImpl.md) (inner class, the override map below points at its line numbers). This interface mostly declares the contract plus a layer of `default` convenience methods; the only concrete classes here are the nested `WrapWeak` forwarder.

The hard rule (interface javadoc, `Recomputation.java`): **recompute code must call one of the `fulfill*` methods at least once**, unless it knows the recomputation was cancelled. A recomputation that is neither fulfilled nor cancelled holds its value's recomputation transaction open forever (the value never settles). `WrapWeak` exists as a GC safety net for exactly this mistake.

For the surrounding model — what a recomputation transaction is, the recompute gate, old-value capture/restore — read [concepts/transactions.md](../../../concepts/transactions.md). Recomputers are usually configured through builders ([`IPileBuilder`](../../builder/IPileBuilder.md)), not constructed by hand. Up: [recompute index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md).

## Methods by purpose

### Fulfilling (ending the recomputation)
- `fulfill(E)` / `fulfill(E, Runnable onSuccess)` — end with a value. The default `fulfill(E)` delegates to `fulfill(value, null)`.
- `fulfillInvalid` / `fulfillInvalid(Runnable)` — end with no value (value becomes invalid).
- `fulfillRetry` — "basically the same as `fulfillInvalid`"; a semantic alias signalling *retry later* rather than *deliberately invalid*.
- The `onSuccess` `Runnable` runs **before the transaction is closed**, and **only if** the recomputation was still ongoing and **not** in dependency-scouting mode. Use it to add/remove dependencies, or to write results into buffers of downstream `PileImpl`s whose own recomputers just read those buffers — i.e. fan out one computation's results atomically within the closing transaction.
- All `fulfill*` return **`true` iff the recomputation was still ongoing** (not already cancelled/fulfilled); a stale/obsolete call returns `false` and does nothing. They throw `IllegalStateException` if called from the wrong thread (see Threading).
- `setSuccessHandler` / `setFailHandler`: the success handler runs immediately before fulfillment (skipped if obsolete — but note it can become obsolete *between* handler and fulfillment, ); the fail handler is given the value to destroy when a fulfill is attempted but turns out obsolete so the value was never actually installed (e.g. to release native/Closeable resources).

### Old value
A recomputation may carry the value that is about to be replaced.
- `hasOldValue` / `oldValue` — query/get it (`oldValue` returns `null` when there is none — indistinguishable from a genuine `null` old value, ).
- `fulfillRestoreOldValue` / `fulfillRestoreOldValue(Runnable)` — fulfill by reinstating the old value. **If there is no old value, nothing happens and the recomputation stays ongoing** — a silent no-op, so guard it with `hasOldValue`.
- `restoreOldValue` / `restoreOldValue(Runnable)` (default, ) — the safe wrapper: restore if there is an old value, else `fulfillInvalid`. Prefer these over bare `fulfillRestoreOldValue`.
- `forgetOldValue` — drop the old value (e.g. so a later restore can't bring back a stale/expensive object).

### Changed-dependencies query
Why was this recomputation triggered?
- `queryChangedDependencies(boolean copy)` — the set of dependencies whose change caused this recomputation. **May return `null` or empty** when the info isn't available. `copy=false` returns a read-only **live view** that can mutate concurrently — but only once this recomputation is already obsolete, so it's safe to read while you're still the active recomputer.
- `onlyChanged(Dependency)` / `onlyChanged(Dependency...)` (default, , ) — convenience predicates for "was *only* this/these dependency(ies) the cause" — useful to skip expensive work when an irrelevant input changed. Caveat: both call `queryChangedDependencies(false)` and do **not** null-check, so they NPE if the changed-set is `null`; only use them when you know the set is populated.

### Static (declared) dependency editing
Edit the recomputed value's dependency set *without* triggering a further recomputation:
- `addDependency(Dependency)` / `removeDependency(Dependency)`, `setDependencyStatus(d, boolean)` (default toggle, ), `dependsOn(Dependency)`.
- Typically called from inside an `onSuccess` runnable so the edit lands atomically with fulfillment.

### Dynamic-dependency recording (scouting)
The recomputed value can discover its dependencies by *reading* them rather than declaring them up front. `Recomputation` is itself the `DependencyRecorder` for this (`getRecomputation`/`getReceivingRecomputation` return `this`, ).
- `activateDynamicDependencies` — turn it on: after successful fulfillment, the value's **non-essential** dependency set is replaced by exactly the set recorded via `recordDependency`.
- `recordDependency(Dependency)` — note a read; only accumulates if dynamic recording is active.
- `isDynamicRecording` — whether dynamic recording is on. **Gotcha:** it flips to `false` *before* the success handler runs, so don't test it there.
- **Dependency scouting** is the related "discover deps cheaply, then bail" mode:
  - `isDependencyScout` — true if in scouting mode (from the start, or because an undeclared dependency was accessed).
  - `terminateDependencyScout` (default, ) — if scouting, `fulfillInvalid` and return `true` (recompute code should then return without doing real work); otherwise returns `isFinished`. Call it after touching all dependencies but before doing expensive work.
  - `setDependencyVeto(Predicate<Dependency>)` — debug aid: throw when a forbidden dependency is accessed, to catch unwanted dependencies. Only active when `DebugEnabled.DE` is true.

### Delayed / multi-phase recomputation
- `enterDelayedMode` — announce the immediate phase is done; the recomputation may continue on a different thread. Accessing an **undeclared** dependency after this logs a warning.
- `setAsCurrent` (default, ) — install `this` as the current recomputation via `Recomputations.withCurrentRecomputation`, returning a `MockBlock` (try-with-resources scope). Lets nested code obtain the current recomputation.

### Threading & thread naming
Several methods (the `fulfill*`, `forgetOldValue`) are **only legal from the recomputation's designated thread** and throw `IllegalStateException` otherwise.
- `setThread(Thread)` / `setThread(Future<?>)` — designate the responsible thread, or a `Future` that will start one (delayed recomputation). Throws `IllegalArgumentException` for a `null`/dead thread/future.
- `setThread` (default, ) — designate the current thread.
- `getThread` — the running `Thread`, or the scheduled `Future` if delayed (note: erased to `Object`; the commented-out `Either<Thread,Future>` form at / was the intended type).
- `suggestThreadName` / `renameThread(String)` — rename the recomputing thread for debugging; only effective when `DebugEnabled.RENAME_RECOMPUTATION_THREADS` is true and called from the current recomputing thread. `renameThread(null)` restores the original; the name is also restored on hand-off/deactivation. Repeated renames before a restore remember the *first* pre-rename name.

### Cancellation & status
- `cancel` — cancel; interrupts the thread (only if made interruptible) or the `Future` (always interruptible). Returns `true` iff it was still ongoing.
- `setInterruptible` / `setInterruptible(boolean)` — control whether `cancel` may interrupt the current *thread* (a `Future` can always be interrupted). Must be set before `cancel` can interrupt the thread.
- `isFinished` — cancelled or fulfilled (synchronized).
- `isFinishedAsync` — lightweight unsynchronized check; **may return false negatives** — use only as a cheap fast-path.
- `join` / `join(long)` / `join(WaitService[, long])` (–) — block until fulfilled or cancelled, optionally bounded; the parameterless and `long` overloads default to `WaitService.get`.

## `WrapWeak` — the leak guard

`Recomputation.WrapWeak<E>` is a forwarding wrapper that registers a `WeakCleanup.runIfWeak` so that **when the wrapper becomes weakly reachable, `cancel` is fired on the wrapped recomputation** via an `ExecutorService` (default `StandardExecutors.unlimited`). This is the safety net against the "forgot to fulfill" mistake: a dropped recomputation gets cancelled at GC time instead of pinning the transaction forever.
- `wrapWeak` / `wrapWeak(String warn)` (default factories, , ) create it. If `warn != null` and the cleanup's `cancel` returns `true` (it really was still ongoing), a warning naming `warn` is logged — i.e. *you* forgot to fulfill. The no-warn form cancels silently.
- The cleanup uses `isFinishedAsync` to skip already-finished recomputations.

## Override map / where the real work is

This interface is mostly contract + `default` glue. The behaviour-bearing implementation is **`PileImpl.MyRecomputation`** (`PileImpl.java`, inner class ~; see [PileImpl](../../impl/PileImpl.md)). Notable landing points there: `fulfill`/`fulfillInvalid`/`fulfillRestoreOldValue` (~//), `forgetOldValue` (~), dynamic-dependency recording `activateDynamicDependencies`/`recordDependency`/`diffRecorded` (~). `MyRecomputation` holds its outer `PileImpl` **weakly**, so an abandoned recomputation does not pin the value, and cancellation is wired into GC cleanup. The recompute *gate* (when a value is even allowed to start recomputing) and old-value capture/restore live in `PileImpl`/ARLD and are described in [concepts/transactions.md](../../../concepts/transactions.md) (see its *recompute gate* and old-value sections).

`WrapWeak` is the only other implementation and is a pure delegate (plus the GC-cancel hook).

## Caveats & gotchas
- **You must fulfill or cancel.** Forgetting leaks an open recomputation transaction; `WrapWeak` only mitigates this at an unpredictable GC time.
- **`fulfillRestoreOldValue` with no old value is a silent no-op** and leaves the recomputation ongoing — use `restoreOldValue` instead, or guard with `hasOldValue`.
- **`oldValue` returns `null` for "no old value"** — ambiguous with a real `null`; use `hasOldValue` to disambiguate.
- **`onlyChanged(...)` does not null-check** the changed-dependency set (`queryChangedDependencies` may legitimately return `null`); it can NPE.
- **`queryChangedDependencies(false)` is a live, concurrently-mutating view** — only safe to read while you are the active recomputer; pass `copy=true` if you need a stable snapshot.
- **Wrong-thread `fulfill*`/`forgetOldValue` throw `IllegalStateException`** — designate the thread (`setThread*`) before handing the recomputation off.
- **`isDynamicRecording` reads `false` inside the success handler** even when dynamic recording was used for the value.
- **`isFinishedAsync` can lie (false negatives)** — don't use it where correctness depends on the answer.
- Thread renaming only works with `DebugEnabled.RENAME_RECOMPUTATION_THREADS`; the dependency veto only with `DebugEnabled.DE`.

## Common tasks
- **Compute and finish:** read declared dependencies, then `reco.fulfill(result)` (or `fulfillInvalid` if the value can't be produced).
- **Skip work when an irrelevant input changed:** `if (!reco.onlyChanged(theInputICareAbout)) { reco.fulfillRestoreOldValue; return; }` — but only when you know the changed-set is non-null (else use the `restoreOldValue` wrapper and check `hasOldValue`).
- **Cheap dependency discovery:** read your dependencies, then `if (reco.terminateDependencyScout) return;` before doing expensive work.
- **Dynamic dependencies:** `reco.activateDynamicDependencies` up front; reads route through `recordDependency` automatically; on fulfill the non-essential dependency set is rebuilt from what was read.
- **Long/async computation:** `reco.setThread` (or `setThread(future)`), `reco.enterDelayedMode` after the synchronous phase, optionally `setInterruptible` so `cancel` can stop it; fulfill from the designated thread.
- **Leak-proof a handed-out recomputation:** `Recomputation<E> safe = reco.wrapWeak("my-pile")` and pass `safe` onward.

## Tech debt / warts
- `getThread` is erased to `Object` (running `Thread` *or* `Future`); the intended `Either<Thread, Future<?>>` typing is present only as commented-out code. Callers must `instanceof`-dispatch.
- `oldValue` / "no old value" overloads `null`, forcing the parallel `hasOldValue` query.
- `fulfillRetry` and `setThreadNameOnFinish` (commented out, ) hint at half-finished API surface; `fulfillRetry` is currently just an alias for `fulfillInvalid`.
- Several `default` convenience methods (`onlyChanged`) are not defensive against the documented `null`/empty return of `queryChangedDependencies`.
