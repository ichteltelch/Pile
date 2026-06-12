# `pile.impl.MutRef`

A plain mutable get/set cell — the canonical concrete `ReadWriteValue` — that is a bare field box, **not** a graph dependency, not observable, never invalid.

Source folder: `src`. File: `pile/impl/MutRef.java`.

`MutRef<T>` is `final`, extends [`ReleasableMutRef<T>`](ReleasableMutRef.md), and implements [`ReadWriteValue<T>`](../aspect/combinations/ReadWriteValue.md) and [`JustReadValue<T>`](../aspect/JustReadValue.md). It is the example the framework points to of a `ReadWriteValue` that is **not** a [`Pile`](../aspect/combinations/Pile.md): read and write, nothing more.

## What it's for

A simple mutable wrapper around a single reference. Use it when you want a get/set cell whose changes must **not** drive the reactive graph — no listeners fire, nothing recomputes from it, and it is permanently valid (via `JustReadValue`/`AlwaysValid`). It is the read/write contract with **listen and dependency stripped off**; see the contrast in [`ReadWriteValue`](../aspect/combinations/ReadWriteValue.md). For the read/write/listen/dependency graph member, use `ReadWriteListenDependency`/`Pile` instead.

It is also a convenient mutable box for closures (effectively-final capture workaround) and an out-parameter holder — hence its very heavy use across the codebase.

## The field box

The single state is the **public** field `val`, inherited from `ReleasableMutRef`. Everything operates directly on that field:

- `get` → returns `val`.
- `set(T val)` → assigns the field and returns the **value that was set** (not `this`), matching the `WriteValue.set` contract.
- `setNull` → nulls the field directly and returns `this`. **Note:** it does **not** route through `set` — it assigns `val=null` itself. This is flagged in the [`ReadWriteValue`](../aspect/combinations/ReadWriteValue.md) doc; the default `ReadWriteValue.setNull` would call `set(null)`, but `MutRef` overrides that to a direct field write. Behaviorally identical for `MutRef` (its own `set` has no side effects), but a subclass-style override hook the cell deliberately bypasses.
- `take(MutRef<T> o)` → copies `o.val` into this and returns `this`.
- `release` → nulls the field, the `ReleasableMutRef` hook. Plain `MutRef.release` is identical to `setNull` apart from return type (`void`).
- `toString` → the wrapped value enclosed in angle brackets, `"<"+val+">"`.

Because `val` is a public field, callers can also read/write it directly without going through `get`/`set`.

## No validity, no transactions, no graph — the no-op overrides

All the Pile-machinery methods required by the interfaces are implemented as **no-ops or constant returns**, which is what makes `MutRef` a "plain box":

- Transactions: `__beginTransaction`, `__endTransaction(boolean)`, `__endTransaction` (delegates to the `JustReadValue` default) — all no-ops. `JustReadValue` reports `isInTransaction==false`.
- Validity/invalidation: `permaInvalidate`, `revalidate`, `valueMutated` — no-ops. Validity comes from `AlwaysValid` (via `JustReadValue`): it is always valid and never destroyed.
- Correction: `applyCorrection(v)` returns `v` unchanged.
- Equivalence: `_setEquivalence(...)` is a no-op; `_getEquivalence` always returns the shared `DEFAULT_EQUIVALENCE`, which is **identity** `(a,b)->a==b`. Equivalence is never actually consulted here (nothing observes changes), so this is just contract-filling.
- Remember-last-value: `remembersLastValue` returns `false`; `resetToLastValue`, `storeLastValueNow` are no-ops; `suppressRememberLastValue` returns `Suppressor.NOP`. It keeps no history.

## Relation to its siblings

Three classes share the `ReleasableMutRef` base and the public `val` field; pick by capability:

- [`ReleasableMutRef<T>`](ReleasableMutRef.md) — the abstract base: just `val`, `get`, and the abstract `release` hook (which "may also do other things, for example destroying the value or decrementing a reference counter"). It is only a `Supplier<T>`, with no Pile aspects.
- [`EarlyMutRef<T>`](EarlyMutRef.md) — a `final` sibling implementing only `Prosumer<T>` (`Supplier`+`Consumer`). Its docs say to use it **instead of `MutRef` during startup, to avoid loading the [`Piles`](_index.md) class** (and everything that drags in) — `MutRef` pulls in the full `ReadWriteValue`/`JustReadValue` aspect graph, `EarlyMutRef` does not. Differences from `MutRef`: its `set(T)` returns `this` (not the value); it adds `set(EarlyMutRef<T>)` (the analogue of `MutRef.take`); and it implements `accept(T)` for the `Consumer` side. No Pile contract methods.
- `MutRef<T>` — this class: the full `ReadWriteValue` + `JustReadValue` cell.

## Common tasks

- **Mutable box for a closure / out-parameter:** `MutRef<Foo> box = new MutRef<>;` then `box.set(x)` inside the lambda, `box.get` after. (Or use `EarlyMutRef` if you must avoid loading `Piles`.)
- **A read/write cell that must not touch the reactive graph:** type it as `ReadWriteValue<T>` and instantiate `MutRef`. Changes notify nobody and trigger no recompute.
- **Clear it / release a held resource:** `setNull` (returns `this`) or `release` (returns `void`); both just null `val`. Override `release` (via a `ReleasableMutRef` subclass) if nulling should also tear down the value.

## Caveats & gotchas

- **Public mutable field `val`** — no encapsulation; callers may mutate state behind `get`/`set`. By design (cheap box), but it means invariants can't be enforced.
- **Not reactive.** Nothing observes a `MutRef`. Writing to one will not invalidate or recompute any `Pile`. If you expected reactivity, you picked the wrong type — use `ReadWriteListenDependency`/`Pile`.
- `setNull` bypasses `set` (direct field null), so overriding `set` would **not** intercept a `setNull` — see above and the [`ReadWriteValue`](../aspect/combinations/ReadWriteValue.md) doc.
- `set` returns the **set value**; `setNull`/`take`/`release` return `this`/`void`. The asymmetry trips up fluent chaining.
- Equivalence is identity-only and never consulted; don't rely on `_getEquivalence` reflecting any meaningful comparison.

## Tech debt / warts

- The class is mostly boilerplate no-op overrides to satisfy the rich `ReadWriteValue`/`JustReadValue` contracts for what is conceptually a one-field box. That the same plain cell exists in three near-identical flavours (`MutRef`/`EarlyMutRef`/`ReleasableMutRef`), differing mainly in which aspect interfaces they implement and in `set`'s return type, is itself a wart — driven by the class-loading constraint (`EarlyMutRef` avoiding `Piles`).
- Public `val` field is a deliberate but unguarded escape hatch.

## See also

- Package index: [`pile.impl` `_index.md`](_index.md) · framework [overview](../../overview.md).
- Contract docs: [`ReadWriteValue`](../aspect/combinations/ReadWriteValue.md), [`JustReadValue`](../aspect/JustReadValue.md).
- Concepts: [transactions & validity](../../concepts/transactions.md) (which `MutRef` deliberately opts out of via no-ops + `AlwaysValid`).
