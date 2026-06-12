# `pile.aspect.combinations.ReadWriteValue`

The combination contract for a value you can **read and write** but which is **not (necessarily) observable or a dependency** — read + write only, the smallest read/write union.

Source folder: `src`. File: `pile/aspect/combinations/ReadWriteValue.java`.

`ReadWriteValue<E>` unions [`ReadValue<E>`](../ReadValue.md) and [`WriteValue<E>`](../WriteValue.md), and also extends [`Prosumer<E>`](Prosumer.md). Through its parents it transitively already is a `Supplier<E>` (from `ReadValue`) and a `Consumer<E>` (from `WriteValue`), so the extra `extends Prosumer` is redundant for the type bound but makes the "supplier *and* consumer of the same `E`" intent explicit and lets a `ReadWriteValue` be used wherever a `Prosumer` is expected.

## What it's for

This is the contract for a holder you can both `get` and `set(...)` but that does **not** promise to notify listeners or to act as a graph dependency. It is the union sitting one rung below the listen/dependency combinations. The canonical concrete implementer is [`MutRef`](../../impl/MutRef.md) — a plain mutable reference cell that is read/write (plus `JustReadValue`, i.e. permanently valid) and deliberately *not* a `Dependency`/`ListenValue`.

## What it unions (and what it deliberately omits)

- From [`ReadValue`](../ReadValue.md): the validity-aware read surface (`get`, the blocking/pure/recording read variants) and `Supplier`/`DoesTransactions`.
- From [`WriteValue`](../WriteValue.md): `set` (returns the actually-set value), `setNull`, invalidation/revalidation, `Consumer`/`RemembersLastValue`, transaction-close hooks.
- **Absent on purpose:** no `ListenValue` (no listener registration / change events) and no `Dependency`/`Depender` (it is not a node in the reactive graph). That absence is the whole point — see the contrast below.

## Relationship to siblings

- **vs [`Prosumer`](Prosumer.md):** `Prosumer<E>` is just `Supplier<E> & Consumer<E>` — raw `get`/`accept` with *no* Pile semantics (no validity, no transactions, no `set`-returns-value, no `setNull`). `ReadWriteValue` is the Pile-aware refinement: same read+write shape, but with the framework's read/write contracts layered on, and it `extends Prosumer` so it remains usable as one.
- **vs [`ReadWriteListenDependency`](ReadWriteListenDependency.md):** that is the full-fat read/write member of the graph — it adds `ListenValue` (observability) *and* `Dependency` (others may depend on it) on top of read/write. `ReadWriteValue` is precisely that stack with **listen and dependency stripped off**: write to it and read it, but nothing observes it and nothing recomputes from it. Choose `ReadWriteValue` (or `MutRef`) when you want a mutable cell whose changes must *not* drive the reactive graph.

The intermediate combinations between the two live in the same package: [`ReadWriteDependency`](ReadWriteDependency.md) (`ReadWriteValue` + `ReadDependency`, adds the `biject*` family) and `ReadWriteListenValue` (adds listening); `ReadWriteListenDependency` joins those branches.

## What it adds over its parents

Almost nothing — it is mainly a join type. Its only own member is the `setNull` default override: calls `set(null)` and returns `this`, narrowing the return type from `WriteValue.setNull` to `ReadWriteValue<E>`. The javadoc instructs subinterfaces/implementors to keep re-narrowing this return type to their own type — and they do: [`ReadWriteDependency`](ReadWriteDependency.md), [`ReadWriteListenDependency`](ReadWriteListenDependency.md), and `MutRef` (`MutRef.java`, which bypasses `set` and nulls the field directly) all override it. So treat `setNull`'s concrete return type as "whatever you called it on," not literally `ReadWriteValue`.

It also declares the nested `ReadWriteValue.PleaseReAdd` exception: a `RuntimeException` a `ValueListener` handler may throw to request being re-added. Note the oddity — this listener-protocol signal is hosted on the one read/write combination that itself has *no* listen capability; it lives here presumably as a convenient shared location rather than for any structural reason.

## Who implements it

- Direct concrete: [`MutRef`](../../impl/MutRef.md) (`implements ReadWriteValue<T>, JustReadValue<T>`).
- Sub-interfaces: [`ReadWriteDependency`](ReadWriteDependency.md), `ReadWriteListenValue`, hence [`ReadWriteListenDependency`](ReadWriteListenDependency.md) and the capstone `Pile<E>`; the transform aspect `TransformableValue`; and the primitive specializations `ReadWriteValueBool`, `ReadWriteValueComparable` (and their kin). Every full `Pile` is therefore a `ReadWriteValue`, but the converse is false — `MutRef` is the example of a `ReadWriteValue` that is not a `Pile`.

## Caveats & gotchas

- `extends Prosumer` is type-theoretically redundant (the `Supplier`/`Consumer` bounds already arrive via `ReadValue`/`WriteValue`); it is there for intent/usability, not to add capability.
- `PleaseReAdd` is declared here despite this interface having no listening — don't infer listen support from its presence.
- Don't reach for `ReadWriteValue` expecting reactivity: a value typed as just `ReadWriteValue` makes **no** promise of change notification or graph participation. If you need those, use [`ReadWriteListenDependency`](ReadWriteListenDependency.md)/`Pile`.

## See also

- Package index: [`pile.aspect` combinations](../_index.md) · framework [overview](../../../overview.md).
- Concepts: [transactions & validity](../../../concepts/transactions.md) (the read/write contracts both parents pull in).
