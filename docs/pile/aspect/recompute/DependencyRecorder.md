# `pile.aspect.recompute.DependencyRecorder`

The interface that takes note of `Dependency` reads during *scouting* (dynamic-dependency discovery), so a recomputation can learn which dependencies it actually accessed.

Source folder: `src`. File: `pile/aspect/recompute/DependencyRecorder.java`.

It is the sink end of the dynamic-dependency feature: a value being read calls [`Dependency.recordRead`](../Dependency.md), which looks up the thread's current recorder and feeds the dependency into it. The recorder normally *is* (or forwards to) the running [`Recomputation`](Recomputation.md), which collects the reads and — if `activateDynamicDependencies` was called — uses them to reset the value's non-essential dependency set after fulfillment. See the [recompute index](_index.md) and [concepts/transactions.md](../../../concepts/transactions.md) for the surrounding model.

## What it is for

When a `Pile`'s recompute code runs, the framework wants to discover *which* dependencies it touched so it can keep the value's dependency set in sync with what the code actually uses (rather than a fixed declared list). To do that it installs a `DependencyRecorder` as a thread-local for the duration of the recompute, and every `Dependency` read during that window funnels itself in. The interface is deliberately tiny and general: the recompute machinery is the built-in client, but you can install your own implementation via [`Recomputations.withDependencyRecorder(...)`](Recomputations.md) for any purpose (e.g. just to inspect what a block of code reads).

## Key methods by purpose

### Recording
- `recordDependency(Dependency d)` — called once per read access. **Not guaranteed thread-safe**; recording is expected to happen on the recomputing thread.

### Locating the recomputation behind the recorder
- `getRecomputation` — the `Recomputation` this recorder belongs to: return `this` if the recorder *is* a `Recomputation`, delegate if it wraps another recorder, else `null`. This is how [`Recomputations.getCurrentRecomputation`](Recomputations.md) reaches the active recomputation from the thread-local recorder.
- `getReceivingRecomputation` — usually the same as `getRecomputation`, but `null` when `recordDependency` does **not** forward recorded reads to a recomputation. The distinction matters for the non-forwarding wrapper below: you can still find the recomputation (for `getCurrentRecomputation`) while recording is effectively switched off.

## The two built-in shapes

- **`NOP`** — a static recorder that does nothing and knows of no recomputation (all three methods return `null` / no-op). Use as a neutral "no recording, no recomputation" sentinel.
- **`nonForwarding`** (default method) — wraps `this`, capturing its `getRecomputation` once, and returns a recorder that **drops every `recordDependency` call** but still reports that recomputation via `getRecomputation`, while `getReceivingRecomputation` returns `null`. This is the "suspend recording but keep the recomputation visible" mode used by [`Recomputations.dontRecord`](Recomputations.md).

## The override map (implementors)

There are two implementors plus the default-method wrapper:

- **`Recomputation<E>`** (`extends DependencyRecorder`) — the real client. Its `recordDependency` adds `d` to the record *iff* `activateDynamicDependencies` was called earlier; `getRecomputation` returns `this`. The concrete body lives in `PileImpl.MyRecomputation`; `Recomputation`'s own inner forwarding wrapper just delegates `recordDependency`/`getRecomputation` to its backing instance.
- **[`GenericDependencyRecorder`](GenericDependencyRecorder.md)** — a standalone recorder that accumulates every read into an `ArrayList` and *optionally* forwards each one to an `outer` recorder passed at construction. `getRecomputation`/`getReceivingRecomputation` delegate to `outer` (or return `null` if there is none). `getRecorded` returns the reads de-duplicated and unordered. Use it to *observe* what code reads, optionally while still feeding the real recomputation underneath.
- the anonymous `nonForwarding` result described above.

## How it ties to `Dependency.recordRead` and scouting

The data path is:

1. The framework opens a recording window by setting the thread-local recorder via `Recomputations.withDependencyRecorder(reco)` — value-builder threads already have this set.
2. Recompute code reads a reactive value; that value calls `Dependency.recordRead`, which fetches `Recomputations.getCurrentRecorder` and, if non-null, calls `recorder.recordDependency(this)`.
3. If the recorder is the running `Recomputation` and `activateDynamicDependencies` was set, the read joins the record; after the recomputation is fulfilled, the value's non-essential dependency set is made equal to that record.

"Scouting" (`Recomputations.isScouting` → `Recomputation.isDependencyScout`, `Recomputations.java`) is the mode in which this discovery is the *point* of the run. `DependencyRecorder` itself is mode-agnostic — it only sees `recordDependency` calls — but it is the mechanism scouting relies on.

## Salient / surprising behavior

- **`recordDependency` is explicitly not thread-safe**. It is meant to run on the recomputing thread; cross-thread access needs `withDependencyRecorder` to re-establish the recorder on the other thread.
- **`getRecomputation` vs `getReceivingRecomputation` can disagree.** A non-forwarding recorder reports a recomputation from the first but `null` from the second — the second answers "are reads actually reaching a recomputation?", the first answers "which recomputation is in scope?".
- **Recording can be live without affecting dependencies.** Even on a real `Recomputation`, reads are only retained if `activateDynamicDependencies` was called; otherwise `recordDependency` runs but discards.

## Caveats & gotchas

- Do not call `recordDependency` from multiple threads on the same recorder.
- Installing a recorder is thread-local and **must be unwound in LIFO order** — `withDependencyRecorder` logs a SEVERE error if the `MockBlock`s are closed out of order. Always use try-with-resources.
- `getCurrentRecorder` may return `null` (no active window); `Dependency.recordRead` already null-guards, but custom callers must too.
- `GenericDependencyRecorder.getRecorded` loses ordering and duplicates by design; use it for set membership, not call sequence.

## Common tasks (how to…)

- **Suspend recording but keep the recomputation reachable:** `Recomputations.dontRecord` (uses `nonForwarding`), or install `NOP` to also hide the recomputation.
- **Observe what a block of code reads:** install a `GenericDependencyRecorder` via `Recomputations.withDependencyRecorder(...)`, run the code, then read `getRecorded`. Pass the previous recorder as `outer` to keep feeding the real recomputation.
- **Reach the active recomputation from a worker thread:** `Recomputations.getCurrentRecomputation` (delegates to `getRecorder.getRecomputation`).

## Tech debt / warts

- Several Javadoc `@return` tags are empty and one comment misspells "Use" as "USe" — cosmetic, consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../../overview.md)).
- The thread-safety contract is by-convention only; nothing enforces single-threaded recording.

## Related

- [`Dependency`](../Dependency.md) — its `recordRead` is the source of every `recordDependency` call.
- [`Recomputation`](Recomputation.md) / [`Recomputations`](Recomputations.md) — the primary client and the install/query helpers.
- [`GenericDependencyRecorder`](GenericDependencyRecorder.md) — the concrete general-purpose recorder.
- [recompute index](_index.md) · [overview](../../../overview.md) · [concepts/transactions.md](../../../concepts/transactions.md).
