# `WeakValueListener`

A [`ValueListener`](ValueListener.md) wrapper that holds the real listener weakly and auto-unregisters itself once that listener is garbage-collected.

Source folder: `src` · package `pile.aspect.listen`.

Up: [listen index](_index.md) · [overview](../../../overview.md).

## What it's for

Lets you register a listener with one or more [`ListenValue`](ListenValue.md)s **without the `ListenValue` keeping the listener alive**. Normal registration holds the listener strongly, so it (and anything it captures) lives as long as the observed value does. A `WeakValueListener` inverts that: the value holds only the *wrapper* strongly; the wrapper holds the real listener via a `WeakReference`. When nothing else references the real listener and it is collected, the wrapper detects this and removes itself from every `ListenValue` it was added to.

It extends [`WeakCleanup<ValueListener>`](../../utils/WeakCleanup.md) (a `WeakReference` with a `run` cleanup callback fired on a daemon thread when the referent is enqueued).

## The weak-reference mechanism

- The wrapper *is* the `WeakReference` to the real listener (`extends WeakCleanup<ValueListener>`). `get` returns the listener or `null` once collected.
- Forwarding methods (`valueChanged`, `runImmediately`) call `get` and forward only if non-null — after collection they become silent no-ops (`WeakValueListener.java`, `66`, `71`).
- Registration is tracked in a `WeakHashMap<ListenValue, ?> addedTo`. The `ListenValue` calls back `youWereAdded`/`youWereRemoved` (the `ValueListener` registration hooks) so the wrapper knows which values currently hold it (`WeakValueListener.java`, `51`). The map is weak in the *keys* too, so collected `ListenValue`s drop out automatically.
- When the real listener is collected, `WeakCleanup` enqueues the wrapper and a daemon thread calls `run`, which snapshots `addedTo`, nulls it, and calls `removeValueListener(this)` on each remaining `ListenValue`. After `run`, `addedTo == null` and later `youWereAdded`/`youWereRemoved` calls are guarded no-ops.
- `priority` is snapshotted from the real listener at construction time, so it survives collection of the referent.
- `asWeakValueListener` returns `this` (already weak).

All four of `run`/`youWereAdded`/`youWereRemoved` are `synchronized` on the wrapper, guarding `addedTo`.

## Who creates it / how to use it

You normally never `new` it yourself. It is produced by:

- **`ListenValue.addWeakValueListener(listener)`** — wraps, adds, and **returns the wrapper**. Keep a strong ref to your *original* `listener`; pass the *returned wrapper* to `removeValueListener` for cheap manual removal (or `removeWeakValueListener(listener)`, which is slower).
- **Builder `onChange_weak(l)`** and `onChange_weak_f(...)` on [`IListenValueBuilder`](../../builder/IListenValueBuilder.md) (`IListenValueBuilder.java`, `49`) — the fluent way to attach a weak listener while building a value.
- **`onChange_weak(l, out)`** — same, but hands you *both* references via the `BiConsumer`: arg 1 = the wrapper (for removal), arg 2 = the real listener (the one you must keep strongly reachable).

## Caveats & gotchas

- **Keep-alive is YOUR job.** The whole point is that the `ListenValue` does *not* keep the listener alive. If nothing else holds the real listener strongly, it can be collected at any time and the listener silently stops firing. A lambda passed inline to `onChange_weak(...)` with no other reference is the classic trap — it may be collected almost immediately. The javadoc says explicitly: keep a reference for as long as it should remain registered.
- **Do not also register the inner listener directly** anywhere — register only the wrapper (the factories do this for you).
- Unregistration is **asynchronous and non-deterministic**: it happens whenever the GC enqueues the reference and the daemon cleanup thread runs, not at the moment of collection.
- After collection, `valueChanged`/`runImmediately` are no-ops rather than errors — a "listener that mysteriously went quiet" usually means its strong reference was lost.

## Tech debt / warts

- `priority` is frozen at construction; if the real listener's priority were meant to change later it would not be reflected (priority is generally static, so this is minor).
- The `removeFrom` private helper and the `addedTo` field comment are lightly documented; the cleanup path relies on `WeakHashMap` iteration semantics during `run` (snapshot of `keySet` taken before nulling the field).

## See also

- [`ValueListener`](ValueListener.md) — the wrapped interface and its registration hooks (`youWereAdded`/`youWereRemoved`).
- [`ListenValue`](ListenValue.md) — `addWeakValueListener` / `removeWeakValueListener`.
- [`ValueListenenerUnregisterer`](ValueListenenerUnregisterer.md) — alternative manual-unregistration helper (note the misspelled type name).
- [`WeakCleanup`](../../utils/WeakCleanup.md) — the weak-reference + daemon-cleanup base class.
