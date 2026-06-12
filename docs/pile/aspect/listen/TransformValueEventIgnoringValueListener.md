# `TransformValueEventIgnoringValueListener`

A [`ValueListener`](ValueListener.md) wrapper that drops [`ValueEvent`](ValueEvent.md)s originating from a transform (`TransformValueEvent`), forwarding only ordinary value-change events to the wrapped listener.

Source folder: `src` · package `pile.aspect.listen`.

Up: [listen index](_index.md) · [overview](../../../overview.md). See also [concepts/transactions.md](../../../concepts/transactions.md).

## What it is

A tiny, **package-private `final` decorator**. It holds a delegate (`self`) and forwards every `ValueListener` call straight through — *except* `valueChanged(e)`, which it forwards only when the event is **not** a `TransformValueEvent`. `priority`, `runImmediately`, and `runImmediately(boolean)` are delegated verbatim.

The use case is opt-out filtering: a listener that cares only about real value changes — not mutations driven by a [transform](../transform/) (package `pile.aspect.transform`, undocumented) — can wrap itself to suppress the transform-origin events. See [`ValueEvent`](ValueEvent.md) for the `ValueEvent` / `TransformValueEvent` distinction.

## Who creates it

Never instantiated directly (it is not public). The only construction site is the factory pair on [`ValueListener`](ValueListener.md):

- `listener.ignoreTransformEvents` — instance form; wraps `this`.
- `ValueListener.ignoreTransformEvents(listener)` — static form; wraps the argument.

Both just `return new TransformValueEventIgnoringValueListener(self)`.

## The filtering (two detection paths)

Detection uses `instanceof TransformValueEvent`, **not** the `ValueEvent.isTransformValueEvent` convenience flag. Both paths exist in the framework and are equivalent in practice — `isTransformValueEvent` returns `false` on `ValueEvent` and is overridden to `true` on `TransformValueEvent` (see [`ValueEvent`](ValueEvent.md)). This wrapper happens to prefer the `instanceof` check, leaving the method as parallel, redundant API surface.

## Caveats & gotchas

- **`null` events pass through.** `runImmediately(...)` ultimately calls `valueChanged(null)`; `null instanceof TransformValueEvent` is `false`, so a `null` event is forwarded to the delegate (correct — a `runImmediately` trigger is not a transform event).
- **`priority` is delegated.** The wrapper keeps the delegate's priority, so wrapping does not change listener ordering. (`ValueListener` requires priority to be stable.)
- The filter is purely on event *type*; it does not inspect the source value.

## Tech debt / warts

- Two parallel transform-detection mechanisms (`instanceof` here vs. `ValueEvent.isTransformValueEvent`); one of them is redundant.
