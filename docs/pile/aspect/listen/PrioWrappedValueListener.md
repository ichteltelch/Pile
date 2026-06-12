# `PrioWrappedValueListener`

A `ValueListener` decorator that forwards every callback to a wrapped listener but reports a chosen `priority`, so the same handler can be registered at a different firing-order priority.

Source folder: `src` · package `pile.aspect.listen`.

Package-private `final` class. You never name it directly; it is produced by [`ValueListener.withPrio(int)`](ValueListener.md) (instance) and the static `ValueListener.withPrio(int, ValueListener)`. See the package [`_index.md`](_index.md) and the [overview](../../../overview.md).

## What it does

It holds a back listener `back` and an `int prio`. Everything except `priority` is pure delegation:

- **`priority`** returns the stored `prio` instead of `back.priority` — the whole point of the wrapper.
- **`valueChanged(e)`** forwards to `back.valueChanged(e)` unchanged — same handler behavior, including the `null`-event case from `runImmediately` (see [`ValueListener.md`](ValueListener.md) § Null event in handlers).
- **`runImmediately`** / **`runImmediately(boolean)`** forward to `back`.

## How priority affects firing order

A [`ListenValue`](ListenValue.md) keeps its listeners ordered by `priority` (smaller runs sooner — see [`ValueListener.md`](ValueListener.md) § Priority and ordering, comparators `COMPARE_BY_PRIORITY[_AND_IDENTITY]`). The general-purpose `ListenValue.Managed`/`ListenerManager` implementation lives in [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md); it reads `priority` when inserting a listener, so wrapping a handler with `withPrio` is how you place that handler earlier or later in the notification sequence without changing its code.

## Caveat: `withPrio` does not re-wrap

`withPrio(int)` delegates to `back.withPrio(prio)`, **not** to itself. So `vl.withPrio(2).withPrio(5)` re-wraps the original `vl` at priority 5 — it does not stack a second wrapper. This is deliberate (avoids wrapper chains) and gives the natural "last `withPrio` wins" semantics. The returned object's identity differs from `this`, which matters because a listener is stored under its own identity (`COMPARE_BY_PRIORITY_AND_IDENTITY`): registering both `vl` and `vl.withPrio(n)` registers two distinct listeners that both invoke the same handler.

## Common tasks

- **Register a handler at a non-default priority** — `listenValue.addValueListener(myHandler.withPrio(n))`, or `ValueListener.withPrio(n, myHandler)`.

## Gotchas

- **No equality override.** Two `PrioWrappedValueListener`s wrapping the same `back` are distinct objects; remove with the exact reference you added, or via the wrappers that track their host (e.g. [`WeakValueListener`](WeakValueListener.md)). Priority must also stay stable per the `ValueListener` contract — this wrapper satisfies that since `prio` is `final`.
- **Lifecycle hooks not forwarded.** `youWereAdded`/`youWereRemoved`/`asWeakValueListener` are *not* overridden, so they use the `ValueListener` defaults (no-op / `null`) rather than delegating to `back`. If `back` relied on those hooks (e.g. a weak listener), wrapping it in `withPrio` would silently drop that behavior. See tech debt below.

## Tech debt / warts

- It delegates `valueChanged`/`runImmediately` but **not** the lifecycle hooks (`youWereAdded`, `youWereRemoved`, `asWeakValueListener`). Wrapping a listener that depends on those (notably a [`WeakValueListener`](WeakValueListener.md)) breaks its host-tracking — the wrapper would not be recognized as weak and would not be told which `ListenValue`s it was added to. Order matters: take `weakValueListener(...).withPrio(n)` and the weak machinery is hidden behind the prio wrapper.
