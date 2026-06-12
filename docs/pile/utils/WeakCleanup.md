# `WeakCleanup`

Register a `Runnable` to fire on a daemon thread when a given referent is garbage-collected — the workhorse Pile uses to auto-unregister listeners and detect loose ends.

Source folder: `src`. Package `pile.utils`.

Up: [overview](../../overview.md) · [utils index](_index.md).

## What it's for

`WeakCleanup<E>` is an abstract `WeakReference<E>` that is also `Runnable`. When its referent becomes weakly reachable and the reference is enqueued, a polling worker thread dequeues it and calls its `run()`. So it converts "object `o` got collected" into "this callback fired", off any client thread.

This is the mechanism behind Pile's self-unregistering listeners: a listener registration holds a `WeakCleanup` keyed on the *listener* (or the listened-to value), and when that referent dies the cleanup removes the now-dead registration.

## The simple API: `runIfWeak`

The everyday entry point is the static `WeakCleanup.runIfWeak(Object o, Runnable run)` (and the overload taking an explicit `Supplier<? extends ReferenceQueue<? super Object>> rm`). You do **not** subclass for this:

- It wraps `o` and `run` in a private `WeakCleanup.Bucket` and adds it to a static `buckets` list.
- When `o` is collected, the worker (from the reference manager) calls `Bucket.run()`, which first `remove()`s the bucket from the list (so the list doesn't leak dead entries), then runs your `run`.
- The single-arg form routes through `AbstractReferenceManager.Std()` — the process-wide default queue/worker (see `DefaultReferenceManager`). The three-arg form lets you supply a different queue.

The `buckets` list exists only to keep the live `Bucket` references reachable (a `WeakReference` whose own object is collected never enqueues) and to allow compaction; `Bucket.remove()` does swap-remove and occasionally `trimToSize()`s.

> Note: the API method is `runIfWeak`, not `runOnceAfter` — if you were sent looking for `runOnceAfter`, this is it. There is also `AbstractReferenceManager.runIfWeak(Object, Runnable)` (instance form) that does the same against a specific manager.

## The lifetime gotcha — do NOT capture the referent

This is the single most important pitfall, and it is easy to get wrong:

**The cleanup `Runnable` must not strongly reference the referent.** If `run` closes over `o` (directly, or transitively — e.g. via a listener object that points back at `o`), then `run` keeps `o` reachable, `o` is never collected, the reference never enqueues, and the cleanup never fires. You have created a leak with the very tool meant to prevent one.

Practical rules:
- Capture only the *other* end of the relationship to be torn down (the registry, the list, the key), never the referent itself.
- Beware non-static inner classes and lambdas: an inner class implicitly captures its enclosing instance; a lambda captures every free variable it mentions. If the enclosing instance or a captured variable reaches back to the referent, you have the same leak.
- The subclass variants below hold the action in a field, so the *reference object itself* must not be reachable from the referent for the same reason.

If your cleanup genuinely needs data about the referent, copy out the plain data you need (an id, a name) before constructing the cleanup, and capture that copy — not the referent.

## How it routes through the reference manager

`WeakCleanup`'s constructors take a `Supplier<? extends ReferenceQueue<? super Object>>`. Calling `.get()` on that supplier both yields the `ReferenceQueue` the reference registers with **and** (by contract of `AbstractReferenceManager.get()` → `getQueueAndStartWorker()`) ensures the polling worker thread is running. So merely constructing a `WeakCleanup` against the standard manager spins up the daemon worker on demand.

`AbstractReferenceManager.Std()` lazily creates a `DefaultReferenceManager` the first time it is needed (double-checked locking); `AbstractReferenceManager.setStd` can swap in a custom global manager before first use. The default manager owns the queue and the daemon thread that calls `run()` on dequeued references.

## Siblings (the concrete variants)

You rarely instantiate `WeakCleanup` directly; you use one of:

- [`WeakCleanupWithRunnable`](WeakCleanupWithRunnable.md) — a concrete `WeakCleanup` that *holds* its cleanup `Runnable` in a field and runs it from `run()`; its action is replaceable via `setCleanupAction`. Use this when the registration object itself is what you keep alive (e.g. a listener that should self-unregister on GC).
- [`WeakIdentityCleanup`](WeakIdentityCleanup.md) — overrides `equals`/`hashCode` so two cleanups are equal iff identical or sharing the same non-null referent (identity-keyed); its own `run()` is an empty no-op, so it's used as a *map/set key* for identity-based cleanup tracking rather than to carry an action.
- The internal `WeakCleanup.Bucket` is the one `runIfWeak` uses — it both removes itself from `buckets` and runs the handler.

Backing infrastructure: [`DefaultReferenceManager`](DefaultReferenceManager.md) (the standard queue + worker), `AbstractReferenceManager` (the contract).

## Common tasks

- **Fire a callback when object `o` dies, no subclassing:** `WeakCleanup.runIfWeak(o, () -> /* tear-down, NOT touching o */ );`
- **A self-cleaning registration object you hold elsewhere:** construct a `WeakCleanupWithRunnable<>(referent, action)` and keep *that* object reachable (not via the referent); it runs `action` on GC of the referent.
- **Identity-keyed cleanup bookkeeping:** key a set/map by `WeakIdentityCleanup` instances.
- **Custom queue/worker (testing, isolation):** pass your own `AbstractReferenceManager`/queue `Supplier` to the three-arg `runIfWeak` or the constructors instead of `AbstractReferenceManager.Std()`.

## Caveats & gotchas

- **Referent-capture leak** (above) — the cardinal sin.
- **No timing guarantee.** Cleanup runs only after the GC collects the referent *and* the worker dequeues it. It may run much later, or — if the JVM exits first — never. Don't use it for ordering-sensitive or must-happen teardown; it's best-effort hygiene.
- **Runs on a shared daemon thread.** Your `run()` executes on the reference manager's worker, not your thread. Keep it short and non-blocking (`Bucket.run` even reaches into the cleanup synchronously); a blocking or throwing handler stalls/affects cleanup for everyone sharing that queue.
- **`Bucket` mutation is guarded by `synchronized(buckets)`**; the handler `runThis.run()` itself is called *outside* that lock (after `remove()` returns), which is correct but means handlers must not assume any `buckets` lock is held.
- **`WeakIdentityCleanup.run()` is intentionally empty** — it's a key type, not an action carrier. Don't expect side effects from collecting one.

## Tech debt / warts

- `deb()` is a debugging method that blocks on `System.in.read()` three times and keeps `buckets`/`Std()` artificially referenced; it is dead weight in production and only useful when attaching a debugger.
- The commented-out `clear()` override (which would have called `ReferenceManager.safeRun(this)` eagerly on `clear()`) hints at an abandoned alternative path; cleanup currently happens only via enqueue/`run`, not on manual `clear()`.
- The static `buckets` list plus `bucketsCap` hand-rolls a compacting array (swap-remove + occasional `trimToSize`); functional but bespoke, and the compaction heuristic (`size()<<1 < cap`) is a magic rule.
