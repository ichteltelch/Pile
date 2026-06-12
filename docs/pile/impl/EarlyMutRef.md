# `pile.impl.EarlyMutRef`

A plain mutable get/set box that implements **only** [`Prosumer`](../aspect/combinations/Prosumer.md) — usable very early at startup because it avoids loading the [`Piles`](_index.md) class and the heavy aspect graph that [`MutRef`](MutRef.md) drags in.

Source folder: `src`. File: `pile/impl/EarlyMutRef.java`.

`EarlyMutRef<T>` is `final`, extends [`ReleasableMutRef<T>`](ReleasableMutRef.md), and implements [`Prosumer<T>`](../aspect/combinations/Prosumer.md) (= `Supplier<T>` + `Consumer<T>`) and nothing else. It is the lightweight sibling of [`MutRef`](MutRef.md).

## Why a separate "early" variant exists

This is the whole point of the class. Its javadoc says to use it **instead of [`MutRef`](MutRef.md) during startup, in order to avoid loading the `Piles` class and all that belongs to it**.

`MutRef` implements the rich [`ReadWriteValue`](../aspect/combinations/ReadWriteValue.md) + [`JustReadValue`](../aspect/JustReadValue.md) contracts. Satisfying those pulls in the full Pile aspect graph (validity/`AlwaysValid`, transactions, correction, equivalence, remember-last-value, `Suppressor`, …) and transitively the `Piles` utility hub. During very early bootstrap — before that machinery is ready or as a deliberate class-loading optimization — you want a get/set box that touches **none** of it. `EarlyMutRef` implements only `Prosumer` (two `java.util.function` interfaces) and so loads essentially nothing beyond `ReleasableMutRef`.

It is the class-loading constraint, not any behavioral difference, that justifies a third near-identical flavour of the same one-field cell.

## The field box

State is the single **public** field `val`, inherited from [`ReleasableMutRef`](ReleasableMutRef.md). Everything operates directly on it:

- `get` → returns `val`.
- `set(T val)` → assigns the field and returns **`this`**.
- `set(EarlyMutRef<T> o)` → copies `o.val` into this and returns `this` — the analogue of `MutRef.take`.
- `accept(T t)` → assigns the field, no return — the `Consumer` side of `Prosumer`.
- `setNull` → nulls the field and returns `this`.
- `release` → nulls the field, returns `void`, the `ReleasableMutRef` hook.
- `toString` → the wrapped value in angle brackets, `"<"+val+">"`.

Constructors: no-arg (`val` stays `null`) and `EarlyMutRef(T val)`. Note it does **not** delegate to the `ReleasableMutRef(T)` constructor; it assigns `this.val=val` directly.

## How it differs from `MutRef`

Same base, same public `val` field, same conceptual "plain box", but:

- **Interfaces:** `EarlyMutRef` implements only `Prosumer` (Supplier+Consumer). `MutRef` implements `ReadWriteValue` + `JustReadValue` and therefore carries dozens of no-op Pile-contract overrides (transactions, validity, correction, equivalence, remember-last-value). `EarlyMutRef` has **none** of those — there is no Pile machinery to stub out.
- **`set(T)` return:** `EarlyMutRef.set` returns **`this`** (fluent); `MutRef.set` returns the **value that was set** (per the `WriteValue.set` contract).
- **Copy method:** `EarlyMutRef.set(EarlyMutRef<T>)` vs `MutRef.take(MutRef<T>)` — same job, different name.
- **`accept`:** `EarlyMutRef` adds the `Consumer` method `accept(T)`; `MutRef` does not implement `Consumer`.
- **Loading cost:** `EarlyMutRef` avoids `Piles` and the aspect graph; `MutRef` requires them.

## Common tasks

- **A get/set box safe to use during bootstrap:** `EarlyMutRef<Foo> box = new EarlyMutRef<>;` — won't trigger loading of `Piles`/aspect classes.
- **Use it as a `Consumer`/`Supplier`/`Prosumer` sink-and-source:** pass it where a `Prosumer<T>` (or `Consumer`/`Supplier`) is expected; `accept`/`get` wire straight to `val`.
- **Clear it / release a held resource:** `setNull` (returns `this`) or `release` (returns `void`); both just null `val`.

## Caveats & gotchas

- **Public mutable field `val`** — no encapsulation; callers may read/write state behind `get`/`set`. Deliberate (cheap box), but invariants can't be enforced.
- **Not reactive.** Like `MutRef`, nothing observes an `EarlyMutRef`; writing to one invalidates and recomputes nothing. If you need reactivity use a [`Pile`](../aspect/combinations/Pile.md).
- **`set`/`setNull`/`set(EarlyMutRef)` return `this`, `accept` returns `void`, but `set(T)` here returns `this` whereas `MutRef.set(T)` returns the value** — easy to confuse when swapping between the two types.
- `release` here is a plain field-null; it does **not** do anything fancier despite `ReleasableMutRef`'s contract allowing "destroying the value or decrementing a reference counter". Subclass `ReleasableMutRef` directly if you need that — `EarlyMutRef` is `final`.

## Tech debt / warts

- Yet another near-identical one-field cell. `MutRef` / `EarlyMutRef` / `ReleasableMutRef` differ mainly in which aspect interfaces they implement and in `set`'s return type; the duplication exists purely to honor the class-loading constraint (`EarlyMutRef` must avoid `Piles`).
- Public `val` is a deliberate but unguarded escape hatch.
- The `set(T)` return-type mismatch with `MutRef` (`this` vs the set value) is a small inconsistency between siblings.

## See also

- Siblings: [`MutRef`](MutRef.md) (the full `ReadWriteValue` cell), [`ReleasableMutRef`](ReleasableMutRef.md) (the abstract base).
- Contract: [`Prosumer`](../aspect/combinations/Prosumer.md).
- Package index: [`pile.impl` `_index.md`](_index.md) · framework [overview](../../overview.md).
- Concepts (which this box deliberately opts out of): [transactions & validity](../../concepts/transactions.md).
