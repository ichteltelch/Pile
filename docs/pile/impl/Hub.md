# `pile.impl.Hub`

Bundles several [`Dependency`](../aspect/_index.md)s into one reactive value that fires on *any* dependency change — even when the recomputed value is byte-for-byte identical.

Source folder: `src`. File: `pile/impl/Hub.java` (~142 lines). `Hub extends `[`PileImpl`](PileImpl.md)`<Object>`.

## What it's for

A `Hub` is an **aggregation / fan-in point**: you give it a set of `Dependency`s, and it becomes a single value that is considered "changed" whenever *any* of them changes. Its own value is constant (a fixed `value`, default the string `"Hub"`); the value carries no information — the *change event* is the payload. Downstream code listens to one `Hub` instead of N dependencies.

Two ingredients make this work:
- **A deliberately broken equivalence relation** — `HUBS_ARE_ALWAYS_UNEQUAL = (a,b)->false`, installed via `_setEquivalence` in the constructor. Because no two values are ever "equal", every recompute counts as a change and fires listeners, even though the recompute always re-yields the same constant.
- **A trivial recompute** that just re-`fulfill`s the fixed `value`. Recompute is triggered by `PileImpl`'s normal machinery whenever a dependency changes.

## Construction

Four constructors, all funnelling into `Hub(Object value, boolean deep, Dependency... deps)`:
- `value` — the constant the Hub holds when valid (default `"Hub"`). If it's a `String`, it's *also* used as the Hub's name (`Hub.java`, and again in `setName`-style via `super.setName`).
- `deep` — if `true`, the Hub additionally **forwards `ValueEvent`s** from any dependency that is also a [`ListenValue`](../aspect/listen/), not just reacting to validity changes. See "Deep Hubs" below.
- `deps` — initial dependencies; passed to `addDependency(false, deps)`.

`Hub(boolean deep)` and `Hub(boolean deep, Dependency... deps)` use the default name/value `"Hub"`.

## Salient / surprising behavior

- **`set(Object)` throws `UnsupportedOperationException`**. A Hub's value is fixed; you are not meant to write it. This overrides `PileImpl.set`. To force a different value anyway, use **`setExplicitly(Object)`**, which routes through `super.set`. `setNull` is therefore also broken — it calls `set(null)` and thus throws.
  - SUPERDOC note: this narrows the [`WriteValue`](../aspect/combinations/ReadWriteValue.md)/`Pile` contract — callers that treat any `Pile` as writable via `set` will get an exception on a `Hub`.
- **"Always unequal" defeats value-equality fields.** The class javadoc warns: if you build a [`field`](../aspect/combinations/ReadDependency.md) (or other derived value) *from* a Hub, the field's default equivalence is value-equality, so it will collapse the Hub's "everything is a change" signal back into "no change" unless you override the field's equivalence too. The whole point of the Hub is lost otherwise.
- **The value is informationless.** Don't read a Hub for its value; observe it for its *change*. `get` returns the constant (`"Hub"` by default) when valid, `null` when invalid (inherited `PileImpl.get` semantics — see [PileImpl.md](PileImpl.md)).
- **Covariant builder-style returns.** `setName`/`setNull`/`setExplicitly` return `Hub` (not `PileImpl`/`Pile`) so chaining keeps the concrete type.

## Deep Hubs

When `deep` is set, the constructor builds a `deepListener` and registers/unregisters it on each dependency as dependencies are added/removed, via the overridden `dependencyAdded`/`dependencyRemoved` hooks — but **only for dependencies that are `ListenValue`s** (`instanceof ListenValue`). The listener re-fires the incoming `ValueEvent` through this Hub's own listener manager (`_getListenerManager.fireValueChange(e)`, `Hub.java`), so observers of the Hub also see the granular value-change events of its dependencies, not merely the validity/invalidation transitions a non-deep Hub would propagate.

A non-deep Hub has `deepListener == null`; the add/remove hooks then do nothing, and the Hub reacts only through the normal dependency-invalidation → recompute → "always unequal so it's a change" path.

## Caveats & gotchas

- Inherits `PileImpl`'s "built for flexibility, not speed" tradeoff (see [overview](../../overview.md)). A deep Hub with many `ListenValue` dependencies forwards every one of their events.
- `setExplicitly` is the *only* sanctioned way to change the held value; it bypasses the `set`-throws guard but leaves the always-unequal equivalence in place, so even setting the same value fires.
- The deep listener guards on `listeners==null` and returns early — no events are forwarded before anyone is listening (consistent with `PileImpl`'s lazy listener manager).

## Common tasks

- **Fan several dependencies into one change signal:** `new Hub(false, depA, depB, depC)` and listen to the result.
- **Also forward the dependencies' own value events:** pass `deep = true`.
- **Add/remove dependencies later:** use the inherited `addDependency`/`removeDependency` from [`PileImpl`](PileImpl.md) (the deep listener wiring is maintained automatically through the overridden hooks).
- **Force a specific held value:** `setExplicitly(value)` — never `set(value)`.

## Tech debt / warts

- Dead commented-out code: a `setDebugCallback` block in the constructor and overrides of `endTransaction`/`releaseAutoValidationSuppressor` left in as forensic remnants of an auto-validation investigation.
- `setNull` silently inherits the `set`-throws behavior rather than being overridden to throw a clearer message or to call `setExplicitly(null)`; calling it on a Hub yields an `UnsupportedOperationException` from `set`, which may surprise callers expecting `setNull` to work.

## Related

- [`PileImpl`](PileImpl.md) — the base; recompute/transaction/dependency machinery the Hub leans on entirely.
- [`PileCompound`](PileCompound.md) *(doc pending)* — composite reactive structure that uses `Hub` internally.
- Package index: [`_index.md`](_index.md). Project overview: [overview](../../overview.md). The change/recompute model: [concepts/transactions.md](../../concepts/transactions.md).
