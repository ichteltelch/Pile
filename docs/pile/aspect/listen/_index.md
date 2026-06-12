# `pile.aspect.listen` — package index (Tier 1)

Source folder: `src` (all types below).

The **observation machinery**: how code outside the dependency graph watches a reactive value for changes. A value that can be observed implements [`ListenValue`](ListenValue.md); observers are [`ValueListener`](ValueListener.md)s notified with a [`ValueEvent`](ValueEvent.md). The general-purpose `ListenValue.Managed`/`ListenerManager` implementation lives in [`AbstractReadListenDependency`](../../impl/AbstractReadListenDependency.md).

Up: [aspect index](../_index.md) · [overview](../../../overview.md). Rate-limited observation and the listener-deferral (`ListenValue.DEFER`, a.k.a. `Recomputations.NOT_NOW`) tie into [concepts/transactions.md](../../../concepts/transactions.md).

## Core
- [`ListenValue`](ListenValue.md) — the aspect for observable values: add/remove `ValueListener`s, fire changes, the nested `Managed`/`ListenerManager`, and the `DEFER` (≡ `Recomputations.NOT_NOW`) listener-deferral.
- [`ValueListener`](ValueListener.md) — the observer callback (functional `valueChanged(ValueEvent)`); carries a priority and wrapper factories (weak/rate-limited/prio/async/queued). **Gotcha: the event `e` may be `null`** (`runImmediately`).
- [`ValueEvent`](ValueEvent.md) — the change event handed to a listener; carries the changed value as its `source` (no old/new value). `TransformValueEvent` subclass marks transform-origin events.

## Listener wrappers
- [`WeakValueListener`](WeakValueListener.md) — weakly-referenced listener wrapper that auto-unregisters from all its `ListenValue`s once the real listener is GC'd (something must keep the real listener strongly reachable).
- [`RateLimitedValueListener`](RateLimitedValueListener.md) — listener that fires at most once per cooldown window, accumulating the sources that changed in between into a `MultiEvent` (runs on a scheduled executor).
- [`PrioWrappedValueListener`](PrioWrappedValueListener.md) — wrapper that forwards to a listener but reports a chosen `priority` (changes firing order without touching the handler). Doesn't forward the weak/lifecycle hooks.
- [`TransformValueEventIgnoringValueListener`](TransformValueEventIgnoringValueListener.md) — wrapper that drops `TransformValueEvent`s, forwarding only ordinary value changes.

## Multi-value observation
- [`MultiListenValue`](MultiListenValue.md) — observe several `ListenValue`s through one MLV (one listener over a mutable set of values; pass-through or rate-limited).
- [`ConcreteMultiListenValue`](ConcreteMultiListenValue.md) — the sole concrete `MultiListenValue`: a `HashMap` of watched values, each relayed through one shared weak listener.

## Helper
- [`ValueListenenerUnregisterer`](ValueListenenerUnregisterer.md) — bundles listeners with the `ListenValue`s they were added to so they can all be unregistered in one `run`/`close` (note: misspelled type name).
