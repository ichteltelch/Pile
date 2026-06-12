# `pile.aspect.listen.MultiListenValue`

A `ListenValue` that re-broadcasts events from **several** other `ListenValue`s, so one listener can observe a whole set of values at once.

Source folder: `src`. File: `pile/aspect/listen/MultiListenValue.java`.

Up: [listen index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md). It extends [`ListenValue`](ListenValue.md) (so you observe it with ordinary [`ValueListener`](ValueListener.md)s); the sole implementation is [`ConcreteMultiListenValue`](ConcreteMultiListenValue.md); rate-limited mode is built on [`RateLimitedValueListener`](RateLimitedValueListener.md). Deferral/transaction context: [concepts/transactions.md](../../../concepts/transactions.md).

## What it's for

A `MultiListenValue` (MLV) is itself a `ListenValue` that holds a **dynamic set of upstream `ListenValue`s**. It registers one weak listener on each upstream value; whenever any of them fires, the MLV fires *its own* listeners. So instead of adding the same `ValueListener` to N values (and remembering to remove it from all N), you `add(...)` the N values to one MLV and attach a single listener to the MLV. The MLV is the "join point" for a set of observable values.

This is the value-observation analogue of "subscribe to a group": the membership (which values are in the set) is mutable at runtime via `add`/`remove`, independent of who is listening to the MLV.

## Construction (the two flavours)

Both factories return a `ConcreteMultiListenValue`:

- `MultiListenValue.make` — **pass-through** mode. Each upstream event is forwarded **individually and synchronously**, preserving the original `ValueEvent` (so the event's source is the upstream value that actually changed).
- `MultiListenValue.rateLimited(coldStartTime, coolDownTime, startCoolingBefore, careAboutSources)` — **rate-limited** mode. Upstream events are accumulated in a [`RateLimitedValueListener`](RateLimitedValueListener.md) and the MLV fires at most once per cool-down window instead of once per upstream event. Use this when many upstreams can change in a burst and you only need a coalesced "something changed" notification. See the source-fidelity gotcha below for what `careAboutSources` actually does.

## Membership API (the abstract contract)

Three abstract methods every implementor supplies; everything else is a `default`:

- `add(ListenValue v)` — start collecting events from `v`. Returns `true` iff `v` was not already in the set. The MLV holds **no strong reference** back from `v` (it registers a *weak* listener on `v` — see gotchas).
- `remove(ListenValue v)` — stop collecting from `v`. Returns `true` iff `v` was previously collected.
- `collectsFrom(ListenValue v)` — membership test.

### Bulk `default` helpers

Layered on top of the three abstract methods:

- **Counting variants** return how many actually changed state: `addCount(ListenValue...)` / `addCount(Iterable)`, `removeCount(ListenValue...)`, `removeCounting(Iterable)`.
- **Fluent variants** return `this` for chaining: `add(ListenValue...)` / `add(Iterable)`, `remove(ListenValue...)` / `remove(Iterable)`.

Note the naming asymmetry: the `Iterable` remove-and-count method is `removeCounting` (not `removeCount`) because the varargs `removeCount(ListenValue...)` and an `Iterable` overload would otherwise erase-clash. (`addCount` has no such clash and keeps the name for both overloads.) See tech debt.

## Relationship to `ConcreteMultiListenValue`

`MultiListenValue` is purely the **interface** (membership contract + bulk defaults). All behaviour lives in [`ConcreteMultiListenValue`](ConcreteMultiListenValue.md):

- It is a `ListenValue.Managed` backed by an internal `ListenerManager`; that manager is what your listeners are actually registered on, and what `fireValueChange` re-broadcasts through.
- It keeps a `HashMap<ListenValue, ValueListener>` mapping each collected upstream to the **wrapper listener that was registered on it**, so `remove` can unregister exactly that wrapper.
- `add` registers via `v.addWeakValueListener(listener)` and stores the returned wrapper; `remove` calls `v.removeValueListener(wrapper)`. All three mutators are `synchronized` on the MLV.
- The single forwarding `listener` differs by constructor: the `make` path forwards each event verbatim (`manager.fireValueChange(e)`); the `rateLimited` path wraps a `RateLimitedValueListener` around the manager fire.

## Salient / surprising behavior

- **Weak registration upstream.** Because `add` uses `addWeakValueListener`, the upstream value does **not** strongly retain the MLV's forwarding listener. The `ConcreteMultiListenValue` itself keeps the listener strongly reachable (its `listener` field), so as long as the MLV is alive the subscription stays alive. But if the MLV becomes unreachable, its upstream subscriptions can be silently GC'd and auto-unregistered (the usual [`WeakValueListener`](WeakValueListener.md) contract).
- **Rate-limited mode coalesces, it does not queue.** Multiple upstream firings within a cool-down window collapse into a single MLV firing.

## Caveats & gotchas

- **`careAboutSources=true` does NOT actually deliver the collected sources.** The constructor javadoc (and this interface's `rateLimited` javadoc, ) say that with `careAboutSources==true` the event source will be "the set of sources collected by the `RateLimitedValueListener`". But the implementation ignores the accumulated `MultiEvent e` and fires `new ValueEvent(manager.getValueEventSource)` — i.e. the source is **always just the MLV itself**, identical in spirit to the `false` branch which calls `manager.fireValueChange`. So in rate-limited mode you never see *which* upstreams changed, regardless of the flag. Flagged as a suspected bug. (Pass-through mode via `make` *does* preserve the real per-event source.)
- **Re-adding is a no-op, not a refresh.** `add` returns `false` and does nothing if the value is already collected; it does not re-register or reset anything.
- **No "current value" semantics.** An MLV is an event relay, not a value — it has no meaningful read value of its own; treat it purely as a `ListenValue` you attach listeners to.
- **Deferral applies to the firing thread.** Re-broadcast goes through the internal `ListenerManager`, so the `ListenValue.DEFER`/`Recomputations.NOT_NOW` deferral described in [`ListenValue.md`](ListenValue.md) applies as usual.

## Common tasks

- **Watch a group of values with one listener:** `MultiListenValue mlv = MultiListenValue.make; mlv.add(a, b, c); mlv.addValueListener(l);` — `l` fires whenever any of `a/b/c` fires, with the event's source being the value that changed.
- **Coalesce a bursty group into throttled notifications:** `MultiListenValue.rateLimited(cold, cool, startBefore, false)` then `add(...)` the values and attach your listener; it fires at most once per cool-down window.
- **Change the watched set at runtime:** `mlv.add(extra)` / `mlv.remove(gone)`; query with `mlv.collectsFrom(x)`.
- **Count how many were actually (un)subscribed:** use `addCount(...)` / `removeCount(...)` / `removeCounting(iterable)`.

## Tech debt / warts

- The `removeCounting(Iterable)` vs `removeCount(ListenValue...)` naming split (to dodge erasure clash) is inconsistent with `addCount`, which reuses one name for both overloads.
- The `careAboutSources` parameter is effectively **dead** in rate-limited mode (both branches end up sourcing the event as the MLV); the javadoc promises source fidelity the code does not deliver (see gotcha).
- Several constructor-javadoc `@param` tags (`coldStartTime`, `coolDownTime`, `startCoolingBefore`) are present but empty, and a couple of javadocs on the interface refer to `ConcreteMultiListenValue` (the impl) rather than the interface type.
