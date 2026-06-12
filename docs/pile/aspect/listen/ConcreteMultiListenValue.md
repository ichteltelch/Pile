# `ConcreteMultiListenValue`

The sole concrete implementation of [`MultiListenValue`](MultiListenValue.md) — relays change events from a set of watched `ListenValue`s to its own listeners.

Source folder: `src` · package `pile.aspect.listen`.

Up: [listen index](_index.md) · [overview](../../../overview.md). Background: [`ListenValue`](ListenValue.md), [`MultiListenValue`](MultiListenValue.md), [`ValueListener`](ValueListener.md), [`ValueEvent`](ValueEvent.md).

## What it adds over the interface

[`MultiListenValue`](MultiListenValue.md) declares the contract (`add`/`remove`/`collectsFrom`, the bulk `addCount`/`removeCount`/fluent variants) and the two factories. This class is the only thing that supplies *state*:

- It **stores the watched set** in `final HashMap<ListenValue, ValueListener> listenTo` — keys are the upstream values, values are the per-value `WeakValueListener` returned by `addWeakValueListener`, kept so the same instance can be passed to `removeValueListener` on `remove`.
- It is also a `ListenValue.Managed`: it owns a `final ListenerManager manager` and exposes it via `_getListenerManager`. That `ListenerManager` is what its *downstream* listeners attach to, and what `fireValueChange` pushes through.
- It builds **one shared forwarding listener** `final ValueListener listener` in the constructor and registers that same instance with every value added.

## The shared listener — three forwarding modes

The constructor chosen fixes how upstream events become downstream events:

- **`new ConcreteMultiListenValue`** / `MultiListenValue.make` — pass-through. The listener is `e -> manager.fireValueChange(e)`, so each upstream `ValueEvent` is forwarded **individually, preserving its original `source`** (the upstream value that changed).
- **`rateLimited(coldStartTime, coolDownTime, startCoolingBefore, careAboutSources)`** — wraps the forwarder in a `ValueListener.rateLimited(...)` `RateLimitedValueListener`, so bursts are collapsed and fired at a limited rate. Two sub-modes:
  - `careAboutSources == true` — fires `new ValueEvent(manager.getValueEventSource)`; the event's source is the rate-limiter's collected source set.
  - `careAboutSources == false` — fires `manager.fireValueChange` with **no event**; downstream listeners get a `null` event and this `ConcreteMultiListenValue` is the implied source.

## Watch-set management

- `add(v)` — idempotent: returns `false` if `v` is already watched; otherwise registers the shared `listener` on `v` **weakly** (`addWeakValueListener`) and records the returned wrapper. Because the registration is weak, `v` holds **no strong reference back** to this object (per the javadoc on ); something else must keep this `ConcreteMultiListenValue` (and thus its `listener`) reachable, or the weak listener silently stops firing.
- `remove(v)` — looks up and removes the stored wrapper, then `removeValueListener`s it from `v`; returns `false` if `v` wasn't watched.
- `collectsFrom(v)` — membership test against the map.
- All three are `synchronized` on the instance, so the watch-set is thread-safe; the downstream listener set is guarded separately by the `ListenerManager`.

## Who creates it

Never constructed directly by clients in practice — both constructors are reached through the [`MultiListenValue`](MultiListenValue.md) static factories `make` and `rateLimited(...)`. Construct via those; treat the concrete type as an implementation detail.

## Caveats & gotchas

- **Weak upstream registration:** the `add` javadoc's "holds no strong references to this" is the whole point — a `ConcreteMultiListenValue` that nothing else keeps alive will be GC'd and quietly stop relaying. Keep a strong reference for as long as you want forwarding.
- **`careAboutSources == false` delivers `null` events:** downstream `ValueListener`s must tolerate a `null` `ValueEvent` (the general `ValueListener` gotcha — see [`ValueListener`](ValueListener.md)). Use `careAboutSources == true` if listeners need the source.
- Rate-limited mode loses one-event-per-change fidelity by design; only `make` forwards each upstream event verbatim.

## Tech debt / warts

- No `clear` / "remove all" and no way to iterate the watched set; removal is one-by-one or via the interface bulk helpers.
- The two-constructor + four-flag `rateLimited` signature is positional and easy to misread (`startCoolingBefore` vs `careAboutSources`); the factory javadoc is the only guard.
