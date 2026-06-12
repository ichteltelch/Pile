# `IdentitiyMemoCache`

An identity-keyed memoisation cache with weak keys *and* weak values (note the misspelled class name).

Source folder: `src`. Package `pile.utils`.

Up: [package index](_index.md) · [overview](../../overview.md). Sibling: [`IdentityComparator`](IdentityComparator.md).

## What it's for

`IdentitiyMemoCache<K,V>` wraps a `derive` function `K -> V` and memoises its results, so each distinct key is mapped through `derive` at most once (while the result is still reachable). It implements `Function<K,V>` itself: you call `apply(key)` and get the cached-or-freshly-derived value.

The misspelling is in the public type name (`IdentitiyMemoCache`, transposed `ti`/`it`). Preserve it verbatim in code references; it cannot be renamed without breaking callers.

## Identity semantics

Keys are compared by **object identity**, not `equals`. Each call wraps the key in a `WeakIdentityCleanup` (see its doc *pending*), whose `equals`/`hashCode` are defined so two wrappers match only when their non-null referents are the *same object* (`==`) and share the same `System.identityHashCode`. So `apply(a)` and `apply(b)` hit the same entry **iff `a == b`** — two equal-but-distinct keys get separate entries. This is the cache analogue of an `IdentityHashMap`.

## The memo function

`apply` (the sole method):
1. Lazily resolves the `ReferenceQueue` supplier `rq` to the shared standard manager (`AbstractReferenceManager.Std()`) on first use if none was supplied.
2. Builds a fresh `keyRef` wrapper for the key and checks the backing `HashMap` under its monitor. A hit returns the live value; if the value weak-ref has been cleared, the stale entry is removed and treated as a miss. A stored `null` value-ref is a memoised *`null` result* and is returned as `null` (see "`null` handling").
3. On a miss it calls `derive.apply(key)` **outside** the cache lock, inside a `Recomputations.withoutRecomputation()` `MockBlock`, then re-checks under the lock and stores the result. The re-check + `continue` `while(true)` loop handles the race where another thread populated (or invalidated) the entry while `derive` ran.

`derive` is invoked under `Recomputations.withoutRecomputation()` so that creating memoised **reactive** values during dependency recording does not get those values entangled as dependencies — the comment notes this is safe because derivation happens only once per key.

## Eviction / weakness

Eviction is purely **garbage-collection-driven**; there is no size bound, TTL, or manual `remove`/`clear`.

- **Weak keys:** the key is held only through `WeakIdentityCleanup` (a `WeakReference`). When the key is collected, the registered cleanup fires (`run()` removes the entry under the `cache` monitor).
- **Weak values:** the derived value is wrapped in a `WeakCleanup` (also a `WeakReference`); when the *value* is collected its `run()` likewise removes the entry. So an entry whose value is no longer strongly reachable elsewhere disappears even if the key survives — the cache never keeps a value alive on its own.

Both cleanups are driven by the `ReferenceQueue` of the `AbstractReferenceManager` (the daemon-thread machinery of `WeakCleanup`), not by polling on access — though `apply` also opportunistically drops an entry it finds already cleared.

## `null` handling

A `null` *result* from `derive` is memoised as a `null` value-ref (`cache.put(keyRef, null)`) and subsequently returned as `null` — so a key that derives to `null` is not re-derived. Because both "value GC'd" and "memoised null" can surface as a `null` value-ref, the lookup paths return `null` directly when they encounter one. Keys themselves are assumed non-null (their `identityHashCode` is taken eagerly).

## Thread-safety

Safe for concurrent use. All map access is guarded by `synchronized(cache)`. `derive` runs *outside* that lock (so derivation can't deadlock against cleanup), and the loop re-validates the entry afterwards, so concurrent callers converge on a single stored value per key. Note this means `derive` for the same key **may run more than once** under contention (lost-update style) — only one result is kept; the cache guarantees a single *stored* value, not a single *invocation*.

## Caveats & gotchas

- Identity keys: passing a fresh-but-equal key object each call defeats memoisation (every call is a miss). Intern your keys if you need value-equality semantics.
- No bounded eviction: this is a weak cache, not an LRU. Entries vanish on GC, not on capacity. A value kept strongly reachable elsewhere stays cached indefinitely.
- The stored key wrapper is the first one created for a given referent; later calls build a throwaway wrapper used only for lookup. Don't rely on wrapper identity.
- Reentrancy into reactive-value creation is deliberately suppressed (`withoutRecomputation`) — derived reactive values won't be recorded as dependencies of an enclosing recomputation.

## Tech debt / warts

- The class name is misspelled (`IdentitiyMemoCache`).
- No way to inspect, size, or explicitly evict; behavior is entirely GC-timing dependent, which makes it hard to test deterministically.
