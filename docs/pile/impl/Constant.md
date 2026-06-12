# `pile.impl.Constant`

A reactive value that wraps a single never-changing value: always valid, never fires changes, and silently ignores all writes.

Source folder: `src`. File: `pile/impl/Constant.java`.

`Constant<E>` is the trivial reactive cell. It holds one `final E value` set at construction and implements the full read/write/listen/dependency contract by making every method either return that value, return a constant, or do nothing.

Up: [impl index](_index.md) · [overview](../../overview.md). Mental model: [concepts/transactions.md](../../concepts/transactions.md).

## What it is for

Wrap a plain immutable value so it can be passed wherever a reactive value (a `Dependency`, a `ReadValue`, even a `WriteValue`) is expected, with zero machinery. Because it can never change, it needs no listener list, no depender registry, no transaction counter, and no validity bookkeeping. Created via the `Piles` factories — see *Who creates it*.

## Supertype

`Constant<E> implements ReadWriteListenDependency<E>` **directly** — the capstone read+write+listen+dependency combination (one level below [`Pile`](../aspect/combinations/Pile.md)). It does **not** extend [`AbstractReadListenDependency`](AbstractReadListenDependency.md) or its `_NoDepender` variant; it is a standalone hand-written implementation of every interface method. (The impl [`_index.md`](_index.md) describes `AbstractReadListenDependency_NoDepender` as "base of `Independent`/`Constant`" — that is **not** the case in the current source; see *Tech debt / warts*.)

It does not implement [`Sealable`](../aspect/Sealable.md) (unlike `SealPile`) and is not a `Pile` (no recomputation, no `Recomputer`).

## Behavior in one table — everything collapses

| Concern | `Constant`'s behavior | Where |
|---|---|---|
| **Read** (`get`, `getAsync`, `getValid*`, `getValidOrThrow`, `getOldIfInvalid`) | always returns the stored `value`; `getValid*` never block, never throw | ,  |
| **Validity** | `isValid`/`isValidAsync` → `true`; `validity` → `Piles.TRUE`; `isValidNull` → `value==null`; `nullOrInvalid` → `Piles.TRUE`/`FALSE` by nullness | , ,  |
| **Write** (`set`, `setNull`, `applyCorrection`) | **no-op**: `set` ignores the argument and returns the existing `value`; `setNull` returns `this`; `applyCorrection` returns `value` | ,  |
| **Invalidation** (`revalidate`, `permaInvalidate`, `autoValidate`, `lazyValidate`, `valueMutated`, `recordRead`) | all no-ops | , , , , ,  |
| **Transactions** (`__beginTransaction`, `__endTransaction`, `transaction`, `isInTransaction`, `inTransactionValue`) | no-ops; `transaction` → `Suppressor.NOP`; `isInTransaction` → `false`; `inTransactionValue` → `Piles.FALSE` | , ,  |
| **Listeners** (`addValueListener`, `addWeakValueListener`, `removeValueListener`, `fireValueChange`, `hasValueListener`) | no-ops; the `add*` methods return the listener unwrapped; `hasValueListener` → `false` | ,  |
| **Dependers** (`__addDepender`, `__removeDepender`, `giveDependers`, `__setEssentialFor`, `__dependerNeedsDeepRevalidate`) | no-ops; `giveDependers` yields nothing | , ,  |
| **Deep revalidation** | `isDeepRevalidationSuppressed` → `false`; `suppressDeepRevalidation` → `Suppressor.NOP` |  |
| **Remember-last-value** | `remembersLastValue` → `false`; store/reset/suppress are no-ops |  |
| **Brackets** (`_add*Bracket`, `bequeathBrackets`) | all no-ops — a `Constant` ignores [value brackets](../aspect/bracket/_index.md) entirely |  |
| **Lifecycle** (`destroy`, `isDestroyed`, `destroyIfMarkedDisposable`) | `destroy` no-op; `isDestroyed` → `false` (can never be destroyed); `destroyIfMarkedDisposable` → `false` | , ,  |
| **Identity** | `willNeverChange` → `true`; `equivalence` is identity (`a==b`); `dependencyName` / `toString` → `"Constant <value>"` | , ,  |

This is the *delta over the javadoc*: the per-method contracts live in [`ReadWriteListenDependency`](../aspect/combinations/_index.md) and its parents; the point here is that **`Constant` answers every one of them with the simplest possible constant/no-op**.

## Salient / surprising behavior

- **It implements [`WriteValue`](../aspect/WriteValue.md) but writes are silently swallowed.** `set(x)` discards `x` and returns the unchanged stored value; `setNull` returns `this` without nulling. The class javadoc says "all attempts to alter the value have no effect"; the README adds this "may change in the future." This is a *quiet* no-op — no exception, no log, no veto. See *SUPERDOC* note below.
- **Listeners and dependers are registered into the void.** `addValueListener`/`__addDepender` do nothing and keep **no references** — by design, since the value never changes there is nothing to notify (class javadoc ). So you cannot enumerate listeners/dependers of a `Constant`, and weak-listener registration just hands your listener straight back.
- **`await(...)` on a false condition busy-warns forever.** Because the value never changes, `await(ws, cond)` can never be unblocked by a state change; if the condition is already false it logs `"Waiting for a false Condition on a Constant!"` once and then loops on a 1000 ms `ws.wait`. The timed overload returns `false` after the timeout. Treat awaiting a not-yet-true condition on a `Constant` as a likely bug.
- **`setName(...)` is accepted but ignored**, returning `this`; the commented-out alternative would have thrown. So a `Constant` silently has no name.
- **`_printConstructionStackTrace`** prints a fixed apology to `stderr` instead of a trace — constants don't capture construction traces.
- **`equivalence` is reference identity** (`EQUIVALENCE = a==b`, ), not `.equals`. Irrelevant for change detection (nothing changes) but observable via `_getEquivalence`.

## Caveats & gotchas

- **Do not expect writes to land.** Code holding a `WriteValue`/`ReadWriteListenDependency` reference that happens to be a `Constant` will see `set` silently do nothing. This is the one writable value where the return-value-of-`set` discipline (use the returned value, see [`WriteValue` § ways a write is ignored](../aspect/WriteValue.md)) is *mandatory*: the return is always the original.
- **Avoid blocking primitives.** `getValid*` are fine (they return immediately), but `await(...)` with a condition that isn't already true will hang/loop — there is no event that can ever satisfy it.
- **No bracket support.** If you rely on [value brackets](../aspect/bracket/_index.md) for open/close side effects, a `Constant` will silently never open them.
- **Shared singletons.** `Piles.TRUE`/`FALSE`/`NULL` and the typed constants are themselves `Constant`-family singletons; `getConstant(boolean)` and `nullOrInvalid` hand them out without allocation — don't assume a distinct instance per call.

## Common tasks (how to…)

- **Make a constant reactive value:** `Piles.constant(x)` (typed twins `constant(Boolean|Double|Integer|String)`); for `null`, `Piles.constNull`. See [`Piles` index § constant factories](Piles/_index.md).
- **Get the shared boolean constant:** `Piles.getConstant(b)` → the `TRUE`/`FALSE` singleton (no allocation).
- **Need a constant whose `set` *throws* / that participates in transactions:** use `Piles.sealedConstant(x)` instead — a sealed [`SealPile`](AbstractReadListenDependency.md); **but note** it goes invalid for the duration of any open transaction, unlike a true `Constant` (see [`Piles` index](Piles/_index.md)).
- **Idempotently wrap a value read-only:** `Piles.readOnlyWrapperIdempotent(v)` returns `v` unchanged if it is already a `Constant`.

## Tech debt / warts

- **Index/source mismatch on the base class.** The impl [`_index.md`](_index.md) lists `AbstractReadListenDependency_NoDepender` as the "base of `Independent`/`Constant`," but the current `Constant.java` implements `ReadWriteListenDependency` directly and extends nothing. Either the index is stale or `Constant` predates/bypasses that base. (Reported in this task's `SUPERDOC_CONFLICTS`.)
- **Silently-ignored writes are an admitted provisional design** ("this may change in the future", README). A future version might throw, like the sealed-constant path does.
- **Two commented-out method bodies** remain (`getWithValidity` , `detachDepender` , `couldBeValid` ) — dead alternatives left in place.
- **Hand-written full-interface implementation.** Every method of the large `ReadWriteListenDependency` surface is re-implemented by hand; adding a method to the interface forces a manual stub here (no shared base to inherit no-ops from).

## Related

- [`AbstractReadListenDependency`](AbstractReadListenDependency.md) — the real base of `PileImpl`/`Independent` (which `Constant`, notably, does **not** use); `SealPile`/`sealedConstant` for a sealed alternative.
- [`Independent`](Independent.md) — a *mutable* value with no dependencies and no invalidity (the writable cousin).
- [`WriteValue`](../aspect/WriteValue.md) — the write aspect whose `set` here is a silent no-op (a fifth "write does nothing" path beyond correction/veto/sealing/transaction).
- [`AlwaysValid`](../aspect/AlwaysValid.md) — the "permanently valid" mixin; `Constant` reproduces its collapse (validity → `true`, blocking reads → `get`) by hand rather than mixing it in.
- [`Piles` index](Piles/_index.md) — `constant`/`constNull`/`getConstant`/`sealedConstant` factories and the `TRUE`/`FALSE`/`NULL` singletons.
- [overview.md](../../overview.md) — architecture map and the valid/invalid mental model.
