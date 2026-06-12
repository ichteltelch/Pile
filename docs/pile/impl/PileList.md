# `pile.impl.PileList`

A reactive, observable list of pre-built reactive value boxes (`ReadWriteListenDependency<E>`); the minimal concrete [`AbstractValueList`](AbstractValueList.md) that does **not** know how to wrap raw `E`s.

Source folder: `src`. File: `pile/impl/PileList.java` (~22 lines).

`PileList<E> extends `[`AbstractValueList`](AbstractValueList.md)`<PileList<E>, E>`. It is a near-empty subclass: a `String`-name constructor and `self` returning `this` to satisfy the F-bounded `Self` type parameter. **All list behavior lives in `AbstractValueList`** (which in turn `extends `[`PileCompound`](PileCompound.md)) — read that doc for add/remove/observe/iterate/query semantics, the reactive `head` Pile, and `sizeR`/`isEmptyR`.

## What it holds
The list stores `ReadWriteListenDependency<E>` boxes (reactive cells), **not** raw `E` values — see the `elems` field on the base. Each element is itself a full reactive value that can be depended on, listened to, and written independently. The list as a whole participates in the dependency graph through `AbstractValueList`'s `head` Pile and its reactive `sizeR` / `isEmptyR` sub-values; see [`AbstractValueList`](AbstractValueList.md) for how structural changes invalidate/refire those.

## The one thing `PileList` itself decides: it does NOT override `wrap`
`AbstractValueList.wrap(E)` is a protected hook that "wraps a simple value into an observable value", and **its default body throws `UnsupportedOperationException`**. `PileList` does not override it. Consequence: the **value-based** mutators — `add(E)`, `add(int, E)`, `set(int, E)` — all delegate to `wrap(e)` and therefore **throw `UnsupportedOperationException` on a `PileList`**.

To populate a `PileList`, use the **box-based** API instead, which bypasses `wrap`:
- `addV(ReadWriteListenDependency<E>)` / `addV(int, …)` — append / insert an already-built reactive cell.
- `setV(int, ReadWriteListenDependency<E>)` — replace a cell.

Read-only / structural operations (`get`, `size`, `remove(int)`, `indexOf`, `iterator`, `toArrayList`, `clear`, `removeIf`, `head`, `sizeR`, `isEmptyR`, …) do not touch `wrap` and work normally. This makes `PileList` the right base when you already have reactive values and want to collect them; reach for a sibling that overrides `wrap` (e.g. a typed list) when you want to add plain `E`s and have them auto-boxed.

## Common tasks
- **Collect existing reactive values:** `new PileList<E>(name)`, then `list.addV(somePile)`.
- **Observe the list reactively:** depend on / listen to `list.head`, or use `list.sizeR` / `list.isEmptyR` (all on [`AbstractValueList`](AbstractValueList.md)).
- **Do NOT** call `add(e)` / `set(i, e)` with a raw value on a `PileList` — it throws. Use `addV` / `setV`.

## Caveats & gotchas
- The value-based `add`/`set` API is present (inherited) but a runtime trap on `PileList` — the failure is an `UnsupportedOperationException` at call time, not a compile error.
- `self` exists only to satisfy the self-type bound; it is not a copy/identity operation of interest to callers.
- Everything else (concurrency via `synchronized`, listener/bracket installation, destruction) is `AbstractValueList`/`PileCompound` behavior — verify there, not here.

## Related
- [`AbstractValueList`](AbstractValueList.md) — the base holding all real list semantics (add/remove/observe, `head`, `sizeR`, brackets, `wrap` hook).
- [`PileCompound`](PileCompound.md) — the composite base under `AbstractValueList`.
- [`PileImpl`](PileImpl.md) — the standalone full `Pile` (the kind of value the elements typically are).
- Package index: [`_index.md`](_index.md). · Up: [overview](../../overview.md). · Model: [concepts/transactions.md](../../concepts/transactions.md).
