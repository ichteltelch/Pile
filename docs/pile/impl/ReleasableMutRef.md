# `pile.impl.ReleasableMutRef`

The abstract base of the mutable-reference boxes: just a public `val` field, `get`, and an abstract `release` hook — a bare `Supplier<T>` with no Pile aspects.

Source folder: `src`. File: `pile/impl/ReleasableMutRef.java`.

`ReleasableMutRef<T>` is `abstract` and implements only `java.util.function.Supplier<T>`. It is the shared parent of [`MutRef`](MutRef.md) (and conceptually the box family); it carries none of the reactive-graph machinery — that is added by `MutRef` on top.

## What it's for

A minimal box around a single reference that can take a **special action when released** (set to `null`). The class itself does nothing reactive: it is a plain holder you can `get`, plus a subclass-defined `release` that nulls the value and may additionally tear it down — "destroying the value or decrementing a reference counter". Subclasses decide what `release` does and which extra contracts (read/write, prosumer, …) the box implements.

## The `val` field

The single state is the **public** field `val`. It is read by `get` and set directly by callers or by the two constructors:

- `ReleasableMutRef` — leaves `val` `null`.
- `ReleasableMutRef(T val)` — initializes the field.

Because `val` is public, callers can read/write it without going through `get`/`release`. This is the deliberate "cheap box" escape hatch shared across the family (see the [`MutRef`](MutRef.md) caveats).

## `release`

`release` is `abstract`. Its contract (from the javadoc): it **should set `val` to `null`** and **may also do other things** such as destroying the held value or decrementing a reference counter. The base class does not enforce the null-out — that is left to each subclass.

This is the one extension point of the class: the whole reason `MutRef` has a base type at all is so a box can be released with custom teardown while still exposing the same public `val` + `get`.

## How `MutRef` extends it

[`MutRef<T>`](MutRef.md) is the `final` concrete subclass that turns this bare box into a full reactive-value cell: it adds the `ReadWriteValue` + `JustReadValue` aspect contracts (set/setNull/take, plus the no-op Pile-machinery overrides) on top of the inherited `val`/`get`. Its `release` simply nulls the field — the trivial form of the hook, identical to `setNull` apart from returning `void`. A subclass that needs real teardown overrides `release` instead of using `MutRef`'s plain nulling.

`EarlyMutRef` is the sibling that avoids loading the [`Piles`](_index.md) class graph (see [`MutRef`](MutRef.md) § Relation to its siblings).

## Caveats & gotchas

- **Public mutable `val`** — no encapsulation; invariants can't be enforced through the box.
- **`release`'s null-out is only a convention**, not enforced by the base class. A misbehaving subclass could leave `val` non-null after `release`; rely on the subclass's documented behavior.
- Not reactive in any way at this level — it is only a `Supplier`. All reactivity (and its no-op opt-out) lives in `MutRef`, not here.

## Tech debt / warts

- Three near-identical box flavours (`MutRef` / `EarlyMutRef` / `ReleasableMutRef`) exist mainly because of the class-loading constraint that keeps `EarlyMutRef` off the `Piles` graph; the abstract base only factors out `val` + `get` + the `release` hook. See the [`MutRef`](MutRef.md) tech-debt note.

## See also

- Package index: [`pile.impl` `_index.md`](_index.md) · framework [overview](../../overview.md).
- Siblings: [`MutRef`](MutRef.md) (the full reactive cell), [`EarlyMutRef`](EarlyMutRef.md) (startup-safe box).
- Concepts: [transactions & validity](../../concepts/transactions.md) — which this base, being a bare `Supplier`, plays no part in.
