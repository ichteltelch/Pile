# `Recomputations`

Static helpers for the recomputation machinery: install/read the current `Recomputation`/`DependencyRecorder` thread-locals, query scouting mode, suspend dynamic-dependency recording, defer recompute-*starts*, and the `NOT_NOW` listener-deferral suppressor.

Source folder: `src` (package `pile.aspect.recompute`).

Up: [recompute index](_index.md) · [overview](../../../overview.md). Siblings: [`Recomputation`](Recomputation.md) (the handle), [`DependencyRecorder`](DependencyRecorder.md) (the scouting recorder). Impl that uses these: [`PileImpl`](../../impl/PileImpl.md). Concept: [transactions.md](../../../concepts/transactions.md).

## What it's for

A running recompute (the user's `Recomputer`) needs an ambient handle to "the recomputation I am inside of" so that (a) reads of `Dependency`s get **recorded as dynamic dependencies**, and (b) convenience static calls can act on the current recomputation without threading the handle through. `Recomputations` holds that ambient state in two `ThreadLocal`s and exposes the surrounding controls. It is a bag of statics — there is no instance state worth constructing (see the gotcha about the stray instance methods).

## The current-recomputation / recorder thread-locals

One `ThreadLocal<DependencyRecorder>` `currentRecorder` is the whole story. A `Recomputation` *is-a* `DependencyRecorder`, so `withCurrentRecomputation(reco)` just delegates to `withDependencyRecorder(reco)`, and `getCurrentRecomputation` unwraps `recorder.getRecomputation`.

- `getCurrentRecomputation` / `getCurrentRecorder` — read the thread-local (may be `null`).
- `withDependencyRecorder(reco)` / `withCurrentRecomputation(reco)` — **set** the thread-local for the lifetime of the returned `MockBlock` (try-with-resources). Pass `null` to *hide* the current recomputation from code that must not record dependencies or reach the handle.
- Use this to transfer the ambient recomputation onto a **different thread** than the one the recompute started on (e.g. a worker you spawned). Threads launched by the value builders already have it set (per javadoc).

### Salient: short-circuit + wrong-order detection
`withDependencyRecorder` returns the shared no-op `MockBlock.NOP` when the new value equals the current one — so a redundant install allocates nothing and the close does nothing. Otherwise the returned block, on close, **checks that the thread-local still holds `reco`** and, if not, logs a `SEVERE` "MockBlocks ... closed in wrong order!" with a synthetic stack trace, then restores `old` anyway. It restores `old` (the value before this call), not `null` — so these blocks nest correctly only if closed LIFO.

## Scouting

- `isScouting` — `true` iff there is a current recomputation and it `isDependencyScout`. "Scouting" is the dependency-discovery pass where dependencies are probed rather than the value really being produced. `PileImpl.__startPendingRecompute` passes a `scout` flag that bypasses several gates (see [transactions.md](../../../concepts/transactions.md) § the recompute gate).

## Suspending dependency recording (vs. suspending recompute *starts*)

Two unrelated "suspend" mechanisms live here; don't conflate them.

**(1) Stop recording reads as dynamic dependencies** — affects the recorder thread-local:
- `dontRecord` → `dontRecord(false)`: if a recomputation is current, installs `reco.nonForwarding` as the recorder (reads still see the recomputation but are *not* forwarded as dependencies); else nulls it.
- `withoutRecomputation` → `withCurrentRecomputation(null)`: hides the recomputation entirely.
- `dontRecord(boolean nullRecomputation)`: `true` behaves like `withoutRecomputation`, `false` like `dontRecord`. Prefer `dontRecord` when you still want `getCurrentRecomputation` to work inside the block.

**(2) Hold off the *start* of pending recomputations** — a separate `ThreadLocal<ArrayList<Runnable>>` `suspendedRecomputationsRequests`:
- `suspendRecomputationRequests(ExecutorService async)` opens a `MockBlock`; while any such block is open on the thread, queued jobs accumulate instead of running.
- `possiblySuspendRecomputation(Runnable r)` runs `r` immediately if no block is open, else enqueues it.
- `areRecomputationsSuspended` — is such a block open on this thread?
- On closing the **outermost** block, the queued jobs run: if `async==null`, **synchronously** on the closing thread (RuntimeExceptions logged at `WARNING` and swallowed; `Error`s logged and rethrown); if `async!=null`, each is `execute`d on the executor. Nested blocks no-op because they see a non-null `old` and leave the queue alone.

**Who uses (2):** `PileImpl.__startPendingRecompute` and the validity-toggle path — when recomputations are suspended *and* auto-validation is suppressed, the actual `___startPendingRecompute(...)` is enqueued via `possiblySuspendRecomputation` rather than run inline, so a batch of validity changes triggers recomputes once, after the batch.

## `NOT_NOW` — the listener-deferral suppressor

`public static final Deferrer NOT_NOW = ListenValue.DEFER;`. It is **not** about recomputation at all despite living here — it is an alias for the framework's thread-local FiFo listener `Deferrer`. Opening it defers the running of `ValueListener`s until it closes, so a burst of changes notifies listeners once at the end. The commented-out line above it shows it used to be a separately-allocated deferrer; it now shares `ListenValue.DEFER`. Treat `NOT_NOW` as the convenient name for "don't fire listeners yet."

## Common tasks

- **Run code on a worker thread that should still record dynamic dependencies:** `try (MockBlock b = Recomputations.withCurrentRecomputation(reco)) { ... }` on that worker.
- **Read a `Dependency` without it becoming a dynamic dependency:** wrap the read in `try (var b = Recomputations.dontRecord) { ... }`.
- **Batch many validity/recompute triggers into one recompute pass:** hold a `suspendRecomputationRequests(exec)` block around the batch.
- **Batch listener notifications:** hold `Recomputations.NOT_NOW` (a `Deferrer`) open around the changes.
- **Inside a recompute, fulfill against the current handle in one line:** the convenience methods (`fulfillNull`, `fulfillInvalid`, `restoreOldValue`, `getOldValue`, …) forward to `getCurrentRecomputation` — but see the gotcha: they are **instance** methods.

## Caveats & gotchas

- **`withDependencyRecorder` blocks must be closed strictly LIFO.** Out-of-order close logs a `SEVERE` and restores the wrong predecessor — the thread-local can end up pointing at a stale recorder. Always use try-with-resources.
- **`NOT_NOW` defers *listeners*, not *recomputation*.** Its name and its home in this class invite the opposite assumption. For deferring recompute starts, use `suspendRecomputationRequests`.
- `dontRecord` keeps the recomputation visible (via `nonForwarding`); `withoutRecomputation` hides it. Choosing the wrong one either leaks dependencies or hides a handle the body needs.
- `possiblySuspendRecomputation(null)` is a silent no-op; enqueued `null`s are also skipped on flush.
- Synchronous flush (`async==null`) runs the deferred jobs **on the thread that closes the outermost block**, holding whatever that thread holds — be mindful of re-entrancy and lock state at the close site.

## Tech debt / warts

- **Stray instance methods on a static-helper class.** `fulfillInvalid`, `fulfillRestoreOldValue`, `restoreOldValue`, `fulfillNull`, `getOldValue`, `forgetOldValue`, `isRecomputationfinished`, `hasOldValue`, `queryChangedDependencies` are declared as **non-`static`** even though every sibling is static and the class exposes no constructor/instance. They forward to `getCurrentRecomputation`, so they would work fine as statics; as written you need an instance to call them, which the class never hands out. Almost certainly meant to be `static`.
- `isRecomputationfinished` has a lowercase `f` (should be `isRecomputationFinished`) — minor naming slip.
- Two "suspend" vocabularies (recording vs. recompute-starts) plus the misleadingly-named `NOT_NOW` make this class easy to misread; a rename/split would help.
</content>
</invoke>
