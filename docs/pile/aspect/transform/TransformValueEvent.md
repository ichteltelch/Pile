# `TransformValueEvent`

The [`ValueEvent`](../listen/ValueEvent.md) subclass that marks a change originating from a transform, so listeners can filter it out via `ignoreTransformEvents`.

Source folder: `src` · package `pile.aspect.transform`.

Up: [transform index](_index.md) · [overview](../../../overview.md). See also [`ValueEvent`](../listen/ValueEvent.md) and the filtering wrapper [`TransformValueEventIgnoringValueListener`](../listen/TransformValueEventIgnoringValueListener.md).

## What it is

A three-line subclass of `ValueEvent` that adds nothing but its own type and one override. It is fired in place of a plain `ValueEvent` when a [`TransformableValue`](TransformableValue.java)'s held value was *mutated by a transform* (rather than by an ordinary write/recompute). The type itself is the signal — it lets a listener distinguish, and opt out of, transform-driven changes.

## Key behavior

- `isTransformValueEvent` overrides the base (`false`) to return **`true`**. This is the only behavioral difference from `ValueEvent`. The payload is still just `getSource` (the value that changed) — no old/new value, no change kind.

## Where it is fired

`PileImpl.valueTransformMutated` constructs and fires it: `_getListenerManager.fireValueChange(new TransformValueEvent(this))` ([`PileImpl.java`](../../impl/PileImpl.md)). Compare the adjacent `valueMutated` ([`PileImpl.java`](../../impl/PileImpl.md)), which fires a plain `ValueEvent` for non-transform mutations. The two methods are otherwise identical, including the early `return` when `listeners == null` (no listeners → nothing constructed or fired). `valueTransformMutated` is declared on `TransformableValue` ([`TransformableValue.java`](TransformableValue.java)) with the contract "called after a transformation has mutated the value; should fire a `TransformValueEvent`."

## Relation to the ignoring-listener

The opt-out path: [`ValueListener.ignoreTransformEvents`](../listen/ValueEvent.md) wraps a listener in a [`TransformValueEventIgnoringValueListener`](../listen/TransformValueEventIgnoringValueListener.md), which forwards `valueChanged(e)` to the delegate only when `e` is **not** a `TransformValueEvent`. Note the wrapper tests `instanceof TransformValueEvent`, not `isTransformValueEvent`; the two are equivalent in practice.

## Caveats & gotchas

- **Two parallel detection idioms.** `instanceof TransformValueEvent` (what the framework's own filter uses) vs. `e.isTransformValueEvent`. Both work; the method is redundant API surface.
- Inherits every `ValueEvent` caveat: `e` may be `null` (e.g. from `runImmediately`), `source` is typed `Object`, and serialization support is vestigial — see [`ValueEvent`](../listen/ValueEvent.md).
- Whether the event is fired at all depends on the transform machinery calling `valueTransformMutated`; the transform feature itself is rudimentary/immature (see the [transform index](_index.md)).

## Tech debt / warts

- The `isTransformValueEvent` method exists but the framework filters on `instanceof` instead, so the method is effectively dead API surface (carried over from `ValueEvent`).
