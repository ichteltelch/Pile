# `pile.aspect.HasAssociations`

Source folder: `src`. File: `pile/aspect/HasAssociations.java`.

The **aspect interface a reactive value implements when arbitrary extra data may be stored on it, keyed by typed key objects.** It is a small per-instance, typed key→value store (effectively a private `Map`) mixed into reactive values and used by the framework for upper/lower bounds, last-value remembering, memoization caches, keep-alive references, and a "disposable" marker. Concrete piles get the implementation by also implementing the nested `Mixin` interface (e.g. `PileImpl`, `Independent`).

See the [overview](../../overview.md) for where this sits in the architecture.

## What the association store is

A `HasAssociations` object holds a lazily-created `WeakHashMap<Object,Object>` mapping **key objects** to (possibly reference-wrapped) values (`HasAssociations.java`, allocated on first write in the `Mixin`, ). It is not a public bag of named properties; in practice each *feature* that wants to attach data defines its own key constant and reads/writes through it. The five public operations are:

- `getAssociation(key)` — read.
- `putAssociation(key, value)` — write.
- `putAssociationIfAbsentAndGet(key, Supplier)` — read-or-lazily-create.
- `computeAssociationIfAbsentAndGet(key, Function, param)` — read-or-lazily-compute-from-a-parameter.
- static `putAssociation(object, key, value)` — chainable variant returning the object.

## The typed-key mechanism

Keys are **identity objects carrying a phantom type parameter**: `AssociationKey<E>`. The `<E>` is purely a compile-time annotation tying a key to the value type it stores; lookup is by object identity (`WeakHashMap` default `equals`/`hashCode`), so two distinct key instances never collide. This gives type-safe, collision-free namespacing without a registry — anyone can mint a private key.

Each key also names a `ReferencePolicy` via `referenceStrength`, which decides how strongly the *value* is held:

- `ReferencePolicy.STRONG` — store the value directly; `null` becomes the `__PrivateStuff.NULL` sentinel (`HasAssociations.java`, sentinel at ). No reference queue.
- `ReferencePolicy.WEAK` / `ReferencePolicy.SOFT` — wrap the value in a `WeakReference`/`SoftReference`, so the association evaporates when the value is GC'd. These need a `ReferenceQueue`.

`ReferencePolicy` also provides `wrap`/`unwrap`/`isAbsent`/`needsReferenceQueue` plus `wrapRemoving*` helpers that build a `Runnable`-bearing reference which, when cleared, removes the now-dead entry from the backing map/collection. The weak/soft references used are `RunnableWeakReference`/`RunnableSoftReference`.

Key implementations supplied here:
- `SimpleAssociationKey<E>` — bare key; default ctor uses `STRONG`.
- `NamedAssociationKey<E>` — same plus a `name` for a readable `toString` (debug aid).

A key can also *be* its own logic: `memoize` (below) builds a private class that is simultaneously the `Function` and its own `AssociationKey`.

## The `Mixin` nested type

`HasAssociations.Mixin extends HasAssociations` supplies `default` implementations of all four instance operations, so an implementing class only has to provide five storage accessors:

- `__HasAssocitations_Mixin_getMutex` — the lock guarding the map.
- `__HasAssocitations_Mixin_getMap` / `__HasAssocitations_Mixin_setMap(map)`.
- `__HasAssocitations_Mixin_getQueue` / `__HasAssocitations_Mixin_setQueue(queue)`.

(`HasAssociations.java`; the typo "Associtations" is in the actual method names — see tech debt.) All five are flagged **"Must not be called from outside Mixin's methods."**

**How implementations wire it up.** `PileImpl implements … HasAssociations.Mixin` and backs the accessors with a `private WeakHashMap<Object,Object> associations` + `private ReferenceQueue<Object> associationRq`, returning its existing `mutex` as the coordination lock. `Independent` does the same. The map and queue are **lazily created on first write** by the `Mixin` defaults, and the queue only when a key's policy `needsReferenceQueue`.

Mixin behavior worth noting:
- **Writes/computes synchronize on the mutex** for the whole operation.
- **Lazy creation suppresses listeners during the value-maker call.** `computeAssociationIfAbsentAndGet`/`putAssociationIfAbsentAndGet` bracket the supplier with `ListenValue.DEFER.__incrementSuppressors`/`__decrementSuppressors` so listener notifications fired while building a cached value are deferred.
- **`getAssociation` drains the reference queue first.** Before reading, it polls the `ReferenceQueue` and runs each cleared `Reference` that is `Runnable` via `StandardExecutors.safe(...)`, evicting GC'd entries. This drain is the only place cleanup happens on read.

## Documented uses

- **Upper / lower bounds.** `ICorrigibleBuilder.putUpperBound`/`putLowerBound`/`getUpperBound`/`getLowerBound` store a bound `ReadListenDependency` under the shared keys `AbstractPileBuilder.upperBound`/`lowerBound`. The association only records the bound; it does not install the correctors/listeners that enforce it (noted there).
- **Memoizing a function of a reactive value.** Static `memoize(rpol, f)` returns a `Function<I,R>` whose private class is *also* an `AssociationKey<R>`; calling it stores/reads the result in the argument's own associations via `computeAssociationIfAbsentAndGet`. The `ReferencePolicy` controls how long cached results survive memory pressure.
- **Keeping a strong reference alive to defeat GC.** `keepStrong(o)` lazily creates a `HashSet` under the private `KEEP_STRONG` key (STRONG policy) and adds `o` to it (`HasAssociations.java`, key at ). Because the set is strongly held by the association, `o` lives at least as long as this value — used to pin helper objects (e.g. `PileImpl` does `localRef.keepStrong(this)`, `PileImpl.java`).
- **Remembering the last value.** `LastValueRememberer` defines its own `LastValueAssociationKey`.
- **Disposable marker.** `markAsDisposable(object)` / `isMarkedDisposable` set/read `Boolean.TRUE` under the private `DISPOSABLE` key, distinguishing single-purpose objects (safe to destroy) from shared ones (`HasAssociations.java`, key at ).

## Salient / surprising behavior

- **The backing map is a `WeakHashMap` keyed on the *key object*.** If a key object becomes unreachable, its whole entry can vanish — fine for keys held as `static final` constants, but a key kept only locally may be collected and lose its association.
- **`null` values are storable** via the `__PrivateStuff.NULL` sentinel; `getAssociation` unwraps it back to `null`.
- **Weak/soft associations self-evict**, but only lazily: dead entries are removed when their reference is enqueued and later drained on the next `getAssociation` — not eagerly.
- **`memoize` keys are per-`Function`-instance.** Two `memoize(...)` calls on the same `f` produce independent caches (different key identities).

## Caveats & gotchas

- **Do not call the `__HasAssocitations_Mixin_*` accessors yourself** — they are storage plumbing for `Mixin`'s own methods and bypass the mutex/lazy-init protocol (`HasAssociations.java`, , …).
- **Keep your `AssociationKey` reachable** for as long as you want the association to survive (the map keys weakly).
- **Bounds stored via the association are inert by themselves**; enforcement is a separate concern.
- **Mutating a stored mutable value isn't synchronized for you** — e.g. `keepStrong` re-synchronizes on the `HashSet` itself before adding, because the store only guards the map, not your value's internals.

## Common tasks (how to…)

- **Attach typed data to a value:** mint `AssociationKey<T> k = new SimpleAssociationKey<>;` (keep it as a constant), then `v.putAssociation(k, data)` / `v.getAssociation(k)`.
- **Cache a derived value lazily:** `v.putAssociationIfAbsentAndGet(k,  -> compute)` or, from a parameter, `v.computeAssociationIfAbsentAndGet(k, this::compute, param)`.
- **Memoize a pure function across calls:** `Function<I,R> mf = HasAssociations.memoize(ReferencePolicy.WEAK, f);` — results live in each argument's associations.
- **Pin an object against GC:** `v.keepStrong(helper)`.
- **Make a value's data GC-sensitive:** use a key whose `referenceStrength` is `WEAK` or `SOFT`.
- **Give your class an association store:** implement `HasAssociations.Mixin` and back the five accessors with a `WeakHashMap`, a `ReferenceQueue`, and your existing lock (model on `PileImpl.java`).

## Tech debt / warts

- **Misspelled mixin method names:** `__HasAssocitations_Mixin_*` ("Associtations") — baked into the API, so callers must reproduce the typo.
- **Inner helper class `__PrivateStuff`** is used to fake package-private statics on an interface; `KEEP_STRONG`/`DISPOSABLE`/`NULL` live there.
- **`RunnableSoftReference` actually extends `WeakReference`, not `SoftReference`** — likely a copy-paste slip; the `SOFT` policy itself uses a real `SoftReference`, so this unused-looking class is the wart, not the policy.
- **Reference-queue draining is read-triggered only** (`getAssociation`, `HasAssociations.java`); writes don't drain, so dead entries linger until the next read.
- Consistent with the project-wide note that some API is unsystematic — see [overview § caveats](../../overview.md).

## Related

- [overview.md](../../overview.md) — architecture map; `HasAssociations` is one of the granular `pile.aspect` capability interfaces.
- [`Dependency`](Dependency.md) — sibling aspect, same `__`-prefixed "do not call" convention for framework-internal plumbing.
