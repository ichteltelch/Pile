# `ObservableCondition`

A `WrappedCondition` whose `signal`/`signalAll` calls can be observed by registered listeners.

Source folder: `src` · package `pile.interop.wait`.

Up: [wait index](_index.md) · [interop overview](../../../overview.md). Siblings: [`WrappedCondition`](WrappedCondition.md) · [`GuardedCondition`](GuardedCondition.md) · [`NativeCondition`](NativeCondition.md).

## What it's for

`ObservableCondition` is a [`WrappedCondition`](WrappedCondition.md) decorator that adds a **change-notification side channel**: besides forwarding waits/signals to the backing `Condition`, it lets other code register to be told *whenever a signal happens*. The condition contract (await/signal, all routed through a `WaitService`) is unchanged; the only addition is observability of the signalling.

This is used to expose a waitable predicate that other parties also want to react to without blocking — e.g. `LimitedResource.available` (in `pile.aspect.limitedresource`), where a thread can both `await` the resource becoming available and observe availability transitions.

## The condition contract (inherited)

All the awaiting/signalling methods come from the `WrappedCondition` / [`WaitServiceUsingCondition`](WrappedCondition.md) layer:

- **await family** — `await`, `awaitUninterruptibly`, `awaitNanos`, `awaitUntil`, the timed `await(time, unit)` — are **not overridden**; they delegate straight to `WrappedCondition`, which calls the corresponding `WaitService` method on `back`. So awaiting an `ObservableCondition` behaves exactly like awaiting its backing condition.
- **signal / signalAll** — overridden here (see below) to additionally notify observers.
- Each method exists in two forms: a `Condition`-interface form with no `WaitService` (default methods on `WaitServiceUsingCondition` that pull the injected `WaitService.get()`) and an explicit-`WaitService` form. Blocking always goes through the `WaitService`, never raw `Object.wait`.

## The observability it adds

- `addObserver(ConditionObserver)` / `removeObserver(ConditionObserver)` — register/unregister a listener. Backed by a `ConcurrentSkipListSet` ordered by `IdentityComparator.INST` (identity, not `equals` — distinct observers never collapse even if `equals`-equal).
- `ConditionObserver` (nested interface) — `observe(boolean all)` is called on a signal (`all` = whether it was a `signalAll`); `shouldRemove()` lets the observer ask to be dropped lazily.
- `fire(boolean all)` — iterates the listener set; for each observer it first checks `shouldRemove()` and removes it via the iterator if so, **otherwise** calls `observe(all)`. Note the either/or: an observer that returns `true` from `shouldRemove()` is removed and **not** notified for that signal.

### `signal` / `signalAll` overrides

`signal(ws)` does `ws.signal(back)` then `fire(false)`; `signalAll(ws)` does `ws.signalAll(back)` then `fire(true)`. The backing condition is signalled **first**, observers notified **after**. Observers fire on *every* `signal`/`signalAll` call — there is no predicate gate here (contrast `GuardedCondition`, below).

## Relation to `GuardedCondition` / `WrappedCondition`

- [`WrappedCondition`](WrappedCondition.md) is the common base: a thin decorator over a `back`ing `Condition`, with `WrappedCondition.of(Condition)` for an inline pass-through wrapper. `ObservableCondition` extends it and overrides only the two signal methods.
- [`GuardedCondition`](GuardedCondition.md) is a sibling decorator that knows the *predicate* defining the condition: it loops `await` until the predicate holds and can gate signalling on it. The two compose — `GuardedCondition.observable()` returns `new ObservableCondition(this)`, i.e. an observable view whose backing condition is the guarded one. In that stack the predicate gating happens in the inner `GuardedCondition`'s `signal` (it may suppress the actual signal), while the outer `ObservableCondition.fire` still notifies observers on every outer-level signal call.

## Caveats & gotchas

- **Observers are notified on every signal, unconditionally.** Unlike `GuardedCondition`, `ObservableCondition` has no predicate; a spurious or no-op signal still fires `observe`. Observers must tolerate being called when "nothing meaningful" changed.
- **`shouldRemove()` suppresses that signal's notification.** An observer is removed *instead of* being observed on the firing where it first reports `shouldRemove()==true` — it does not get a final `observe` call.
- **Removal is lazy.** Self-removal only happens the next time `fire` iterates over the observer; until then it lingers in the set. Use `removeObserver` for prompt removal.
- **Identity semantics for observers.** The set uses `IdentityComparator`, so `addObserver` keys on object identity; the same lambda value added twice as two instances counts as two observers.
- **Constructor null-checks `back`** (`Objects.requireNonNull`) — but only *after* `super(back)` has already stored it; the NPE still surfaces, just from the explicit check.

## Common tasks

- *Make an existing guarded condition observable* → call `someGuardedCondition.observable()` (see [`GuardedCondition`](GuardedCondition.md)).
- *Wrap a plain `Condition` to observe its signals* → `new ObservableCondition(cond)`, then `addObserver(...)`.
- *React to availability transitions of a `LimitedResource`* → observe its `available` condition (in `pile.aspect.limitedresource`).

## Tech debt / warts

- `observe`/`shouldRemove` being mutually exclusive on a given firing is a subtle contract that is easy to get wrong from the observer side; it is not documented on `ConditionObserver`.
- No predicate-awareness means observers cannot distinguish meaningful signals from spurious ones at this layer; that knowledge lives only in a wrapped `GuardedCondition`, if present.
