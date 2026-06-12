# `pile.impl.AbstractValueList`

Source folder: `src`. File: `pile/impl/AbstractValueList.java`.

The base of the reactive **list** family: an ordered, mutable list whose elements are each wrapped in a [`ReadWriteListenDependency`](../aspect/combinations/ReadListenDependency.md), all of which feed a single aggregating [`head`](PileCompound.md) value. Extends [`PileCompound`](PileCompound.md) and implements `Iterable<E>`. Concrete subclass: [`PileList`](PileList.md). (`PileCompound` itself is not a list — it is the head-owning aggregate base shared with non-list compounds.)

## What it models

An `AbstractValueList<Self, E>` is a list of *reactive cells*, not a reactive list-as-one-value. Each element `e` lives in its own `ReadWriteListenDependency<E>` (the "wrapper"); the list keeps these in a plain `ArrayList<ReadWriteListenDependency<E>>` field `elems`. The compound's `head` (a [`Hub`](PileCompound.md), inherited from `PileCompound`) is made to **depend on every element wrapper** — so a change to any element, or any structural change, invalidates/refires the head. The head is the list's single handle into the dependency graph; depend on `head` (or use `PileCompound.headDependBracket`) to react to "anything about this list changed".

The self-type parameter `Self` is the CRTP fluent-return type (`setName` returns `self`); subclasses implement the abstract `self`. `E` is the unwrapped element type.

## Element ↔ graph relationship

- Structural mutators wrap their body in `head.suppressAutoValidation` (a [`Suppressor`](../aspect/suppress/Suppressor.md) try-with-resources), call `head.addDependency`/`removeDependency` to attach/detach the element wrapper, mutate `elems`, then let `autoValidate` revalidate the head once. This batches the head's reaction to a single revalidation per operation.
- **Removing an element `destroy`s its wrapper**. A wrapper handed in via `addV`/`setV` is owned by the list from then on and will be destroyed on removal — see Caveats.
- Reads (`get`, `size`, `getElementAt`, the static `toArrayList` helpers, …) call `head.recordRead` (, , …) so that a recomputation reading the list records a dependency on the head — i.e. reading the list inside another value's recompute makes that value depend on the whole list via the head.

## Key methods by purpose

- **Structural (unwrapped):** `add(E)` / `add(int,E)` / `set(int,E)` — wrap the value via `wrap(E)` then delegate to the `*V` variants. `remove(int)`, `clear`, `removeIf(Predicate)`, `removeFirst(Object|Predicate)`, `removeAll(Predicate)`.
- **Structural (pre-wrapped):** `addV(ReadWriteListenDependency)` / `addV(int,…)` / `setV(int,…)` — insert an existing reactive cell directly (no `wrap`).
- **Reads:** `get(int)` returns the *wrapper*; `getElementAt(int)` / `iterator.next` return the *unwrapped* value; `size`/`getSize`/`isEmpty`; `indexOf`, `indexOfFirst`, `getFirst`.
- **Bulk:** `toArrayList(...)` (instance + static overloads, with optional filter and map) snapshot the current unwrapped contents under `synchronized(this)`.
- **Iteration:** `iterator` yields unwrapped `E`; `autoIterator`/`autoIterable` yield the wrappers. Both support `remove` (which detaches + destroys + fires `intervalRemoved`).
- **Derived reactive values:** `sizeR` → a lazily-built [`ReadListenDependencyInt`](../specialized_int/combinations/ReadListenDependencyInt.md) tracking `size`; `isEmptyR` → a `ReadListenDependencyBool` (built as `sizeR==0`). Both are memoised (double-checked) and recompute when the head changes.
- **Naming/lifecycle:** `setName`, `autoCompundName` (overrides `PileCompound`), `head` (widened to `public`), `destroy`/`isDestroyed`.
- **Bracket plumbing:** `addBracket(...)` stores into the `brackets` field — *but the base never applies them* (see Caveats).

## The abstract surface subclasses fill

- `Self self` — return `this` (CRTP). **Required.** `PileList` implements only this.
- `ReadWriteListenDependency<E> wrap(E e)` — turn a raw value into a reactive cell. The base throws `UnsupportedOperationException`; a subclass must override it before any of `add(E)`/`set(int,E)` can be used. Note `PileList` does **not** override it (see Gotchas).
- The notification hooks `intervalRemoved(begin,end)`, `intervalAdded(begin,end)`, `contentsChanged(begin,end)` — empty no-op bodies in the base; a subclass (e.g. a Swing `ListModel` adapter) overrides them to translate structural changes into its own events. The base calls them at the right moments with inclusive indices.

## Salient / surprising behavior

- **Two iteration views.** `iterator` iterates unwrapped values; `autoIterator` iterates the wrappers. `autoIterable` is cached in a field.
- **`manipulate(Runnable)`** runs an arbitrary batch of mutations under head auto-validation suppression and a re-entrant `manipulating` flag, then fires a single `contentsChanged(0, size)`. Use it to wrap multi-step edits into one notification. (The `manipulating` flag is set/restored but **never read** inside this class — it exists for subclasses to detect "we're inside a manipulate".)
- **`removeIf`** compacts in place: matching slots are nulled, survivors shifted left by `removedCount`, then the tail trimmed. It fires `contentsChanged(firstRemoved, oldEnd)` rather than per-element `intervalRemoved`.
- **`sizeR`/`isEmptyR` build their inner values inside `Recomputations.withoutRecomputation`** so creating them doesn't trigger a recompute mid-construction; they are parented/owned by `this` and chained to the head via `.whenChanged(head)`.
- **`removeFirst(Object)`** dispatches null specially to `Functional.IS_NULL`, otherwise to `e::equals` — matching is by the *argument's* `equals`, against unwrapped elements.

## Caveats & gotchas

- **`PileList` cannot take raw values out of the box.** `PileList` overrides only `self`, not `wrap`, so `add(E)`/`set(int,E)` on a plain `PileList` throw `UnsupportedOperationException`. Only the `*V` (pre-wrapped) API works unless a further subclass supplies `wrap`. *(Possible incompleteness — see SUSPECTED_BUGS in the report.)*
- **Ownership transfer on `addV`/`setV`.** A wrapper you pass in becomes list-owned and is `destroy`ed on removal/clear/replace. Don't share one wrapper between two lists or keep using it after removal.
- **`addBracket` is a stub at this level.** The base stores brackets in `brackets` but applies them nowhere; the javadoc explicitly says "whether and how this field is used is up to the concrete subclass". A bracket added here does nothing unless a subclass wires it in. (`wrap` would be the natural place.)
- **`get`/`size`/`getElementAt` record a read on the head**, so calling them inside another value's recomputation creates a dependency on the entire list (via the head), not just the touched element — coarse-grained by design.
- **Indices in the hooks differ by operation.** `intervalRemoved` indices refer to the *pre-removal* state; `intervalAdded` to the *post-add* state (per the hook javadocs at ). `setV` fires `intervalAdded` (not a "changed") even though it replaces.
- **`clear` calls `head.permaInvalidate`** before tearing down; the head is revalidated in the `finally`. Same pattern in `removeIf`.
- `destroy` is `final` and delegates to `subclassDestroy` (which clears + destroys the head) guarded by the `destroyed` flag — idempotent.

## Common tasks

- **React to the whole list changing:** depend on `list.head`, or open `PileCompound.headDependBracket(depender)` on it.
- **Track size/emptiness reactively:** use `sizeR` / `isEmptyR` (lazy, memoised).
- **Bulk edit with one notification:** wrap the edits in `manipulate( -> { … })`.
- **Snapshot to a plain list:** `toArrayList` / `toArrayList(filter)` / `toArrayList(filter, map)`.
- **Make a usable concrete list of raw `E`:** subclass `AbstractValueList` (or `PileList`) and override `wrap(E)` to build the appropriate typed reactive cell.

## Tech debt / warts

- `wrap` left abstract-by-exception on the only concrete subclass (`PileList`) — the unwrapped `add`/`set` API is unusable there; either `PileList` should override `wrap` or the base should make it genuinely `abstract`.
- `addBracket`/`brackets` is dead infrastructure at this level (stored, never applied).
- `manipulating` is written but never read within the class.
- `PileList`'s class javadoc reads "Concrete implementation of `{@link PileList}`" — a self-referential copy-paste slip (should reference `AbstractValueList`).
- `removeIf`'s javadoc is copy-pasted from `clear` ("Clear the list…") and doesn't describe filtering ( vs the actual `removeIf` body).
- Several read paths (`get`, `getElementAt`) record reads but element mutations bypass the list entirely (you mutate the wrapper directly), so the head only sees *structural* changes plus whatever the wrapper-dependency relays — fine, but the read-recording granularity is coarse.

## Related

- [`PileCompound`](PileCompound.md) — the head-owning aggregate superclass. · [`PileList`](PileList.md) — the concrete subclass. · [`PileImpl`](PileImpl.md) / [`AbstractReadListenDependency`](AbstractReadListenDependency.md) — what the head/element wrappers are. · package [`_index.md`](_index.md) · [overview](../../overview.md) · model: [concepts/transactions.md](../../concepts/transactions.md).
