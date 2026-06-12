# `WeakIdentityCleanup`

Identity-based variant of `WeakCleanup`: a weak reference that is equal to another only when they share the *same* (identical) non-null referent, so a set/map of these tracks objects by reference identity rather than `equals`.

Source folder: `src` · package `pile.utils`.

Up: [utils index](_index.md) · [overview](../../overview.md). Base: [`WeakCleanup`](WeakCleanup.md). Related: [`WeakCleanupWithRunnable`](WeakCleanupWithRunnable.md), [`IdentityComparator`](IdentityComparator.md).

## Delta over `WeakCleanup`

`WeakCleanup<E>` is an abstract `WeakReference<E> implements Runnable` whose `run()` fires (in the reference manager's daemon thread) when the referent is collected. It inherits `Object`'s identity `equals`/`hashCode`, so two distinct references to the *same* object are never equal.

`WeakIdentityCleanup<T>` changes exactly two things:

- **Concrete, no-op `run()`.** It overrides `run` with an empty body, so this subclass is a usable (non-abstract) `WeakCleanup` that does *nothing* on collection. It exists to be a hashable/comparable handle on an object, not to perform cleanup. (If you need cleanup work on GC, hold the action elsewhere — cf. `WeakCleanupWithRunnable`.)
- **Identity-based `equals`/`hashCode`.** Two `WeakIdentityCleanup`s are equal iff they are the same object *or* they have the same non-null referent (`==`, not `equals`). `hashCode()` returns a value captured at construction.

## How / why identity

`equals` (see `WeakIdentityCleanup.equals`) is the interesting part:

- `o == this` → equal; `o == null` → not equal; non-`WeakIdentityCleanup` → not equal.
- Fast reject if the stored hash codes differ.
- Otherwise compares referents with `ar != tr || ar == null` → **not equal**. So two references are equal only when `ar == tr` *and* that referent is still alive (non-null).

The hash code is frozen at construction: `hc = System.identityHashCode(referent)` (both constructors do this). This is the same notion of identity that [`IdentityComparator`](IdentityComparator.md) orders by. Storing it in the `hc` field is necessary because once the referent is collected, `get()` returns `null` and the live identity hash is no longer recoverable — the reference must keep a stable `hashCode()` so it can still be *removed* from a hash structure after its referent dies.

This makes `WeakIdentityCleanup` the right key/element type for a hash-based weak registry that must track objects **by reference**, even objects whose own `equals`/`hashCode` are overridden (value semantics) or expensive.

## Caveats & gotchas

- **Collected referents are never equal — not even to themselves.** Once the referent is GC'd, `get()` returns `null`, so `ar == null` makes the reference unequal to *every* other reference, including another `WeakIdentityCleanup` over the (now dead) same object. A dead entry can still be located for removal by its frozen `hashCode()`, but never matched by `equals`. This is the intended "the object is gone, stop tracking it" behavior, not a defect.
- **`hashCode` can collide / is not unique.** `System.identityHashCode` is not guaranteed distinct across objects, so equal hashes still require the `==` referent check — which the code does. Don't read `hc` as an object id.
- **`null` referent.** A `null` referent yields `hc = System.identityHashCode(null) = 0` and a reference whose `get()` is always `null`, hence never equal to anything but itself. Effectively a dead-on-arrival entry.
- **No cleanup action.** Because `run()` is empty, enqueuing one of these does nothing on its own. The value comes purely from its identity-keyed equality; pick a different subclass if you also need a GC callback.

## Common tasks

- *Track a set of objects by identity, weakly:* wrap each in a `WeakIdentityCleanup` and put it in a `HashSet`/`HashMap` — entries match by reference and stop matching once the object is collected.
- *Need a GC callback too:* don't use this; subclass `WeakCleanup` with a real `run()`, or see [`WeakCleanupWithRunnable`](WeakCleanupWithRunnable.md).

## Tech debt / warts

- The empty `run()` makes the cleanup machinery of the base class dead weight for this subclass — the type reuses `WeakCleanup` only for its weak-reference + identity-hash scaffolding, not its cleanup purpose. The naming (`...Cleanup`) is therefore slightly misleading for this variant.
