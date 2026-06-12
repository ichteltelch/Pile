# `ValueEvent`

The change event handed to a [`ValueListener`](ValueListener.md) — it carries the value that changed as its `source`; `TransformValueEvent` is a subclass.

Source folder: `src` · package `pile.aspect.listen`.

Up: [listen index](_index.md) · [overview](../../../overview.md). See also [concepts/transactions.md](../../../concepts/transactions.md) for when changes are fired.

## What it is

A trivial `java.util.EventObject` subclass. When an observable ([`ListenValue`](ListenValue.md)) fires a change, it hands each registered listener a `ValueEvent` whose `getSource` is the value that changed. That is the entire payload — there is no old/new value, no change kind. A listener that watches several values uses `getSource` to tell which one fired.

## Key members

- `ValueEvent(Object source)` — wraps the source (the changed value).
- `getSource` — inherited from `EventObject`; returns the value that fired.
- `isTransformValueEvent` — `false` here, overridden to `true` in `TransformValueEvent`. A convenience flag for distinguishing transform-origin events. Note: the framework's own filter ([`TransformValueEventIgnoringValueListener`](TransformValueEventIgnoringValueListener.md)) tests `instanceof TransformValueEvent` rather than calling this method; both are equivalent in practice.
- `toString` — `"ValueEvent[source=…]"`.

## `TransformValueEvent` distinction

`TransformValueEvent` (package `pile.aspect.transform` — undocumented; see [`../transform/`](../transform/)) is a `ValueEvent` fired specifically because a `TransformableValue`'s value was *mutated by a transform*, rather than by an ordinary recompute/write. It overrides `isTransformValueEvent` to return `true` and otherwise adds nothing.

The point of the distinction is opt-out filtering: a listener that does not care about transform-driven mutations can wrap itself to drop those events. Build such a wrapper via [`ValueListener.ignoreTransformEvents`](ValueListener.md) (instance or static), which returns a [`TransformValueEventIgnoringValueListener`](TransformValueEventIgnoringValueListener.md). That wrapper forwards `valueChanged(e)` to the delegate only when `e` is not a `TransformValueEvent`, while passing through `priority` and `runImmediately(...)` unchanged.

## How listeners use it

A `ValueListener.valueChanged(ValueEvent e)` implementation typically reads `e.getSource` to identify the firing value, or ignores `e` entirely if it only watches one value.

## Caveats & gotchas

- **`e` can be `null`.** `ValueListener.runImmediately(...)` invokes `valueChanged(null)` to trigger the handler out-of-band ([`ValueListener.java`](../../../../src/pile/aspect/listen/ValueListener.java)). A handler that dereferences `e` must null-check.
- **No old value / no change kind.** The event says only *that* `source` changed, not how. Read the current value from the source if you need it.
- **`source` is typed `Object`.** It is the reactive value, but you get no compile-time type; cast as needed.
- The class is `Serializable` (from `EventObject`) and carries a `serialVersionUID`, but the source value generally is not meaningfully serializable — treat serialization support as vestigial.

## Tech debt / warts

- Two parallel ways to detect a transform event (`isTransformValueEvent` vs. `instanceof TransformValueEvent`); the framework prefers `instanceof`, leaving the method as redundant API surface.
