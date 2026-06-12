# `IdentityComparator`

A `Comparator<Object>` imposing an arbitrary-but-consistent total order that respects *identity* (`==`) rather than `equals` — used to key identity-based sorted collections.

Source folder: `src` (package `pile.utils`).

Up: [utils index](_index.md) · [overview](../../overview.md).

## What it's for

Some sorted collections need to be keyed by object identity, not by `equals`/`compareTo` — e.g. an observer set that must hold two `equals`-equal but distinct observers, or that must avoid invoking user `equals`/`hashCode`. `IdentityComparator` gives a `TreeMap`/`TreeSet` a total order in which `compare(a,b)==0` iff `a==b`. One concrete consumer is the observer set in `ObservableCondition` (see [../interop/wait/ObservableCondition.md](../interop/wait/ObservableCondition.md)).

## The shared instance

There is no public constructor; use the singleton `IdentityComparator.INST`. The disambiguation state (see below) lives on the instance, so sharing `INST` keeps that bookkeeping global and minimal.

## Ordering (`compare`)

`compare` decides in cascading stages, returning as soon as one stage is decisive:

1. **Identity** — `o1==o2` → `0`. This is the *only* path that yields equality, which is what makes the order consistent-with-identity.
2. **Nulls** — `null` sorts first (`null < anything`).
3. **`System.identityHashCode`** — compare the two identity hashes numerically. This is the primary ordering key.
4. **Class name** — on an identity-hash collision, break the tie by `Class.getName().compareTo(...)`.
5. **Insertion-order disambiguation** — for two *distinct* objects that share both identity hash *and* class name, fall back to a per-hash list (`map`, keyed by identity hash) that assigns each object a stable position; the comparison is by list index. Objects are appended on first sight, so the order is effectively first-seen order, but it is *consistent* for the lifetime of those objects.

### Why the disambiguation list exists

`System.identityHashCode` is not unique — two live objects can collide. Without stage 5 the comparator could report `0` for two distinct objects (breaking the total order and corrupting a `TreeSet`). The list guarantees a strict, repeatable tie-break so the order is a genuine total order over distinct objects.

### Weak entries / self-cleaning

List entries are `Ref` (a private inner class extending `WeakCleanup` — see [WeakIdentityCleanup.md](WeakIdentityCleanup.md) for the related weak-cleanup family). Each `Ref` weakly references its object and, when that object is GC'd, removes itself from the per-hash list and drops the list from `map` once empty — so the disambiguation table does not leak. `Ref.equals`/`hashCode` are themselves identity-based, so list membership is by identity.

## Caveats & gotchas

- **Inconsistent with `equals` by design.** The class javadoc states this openly: the induced equivalence is identity, *not* equality, "in violation of the usual contract for Comparators." Do **not** hand this comparator to a sorted collection that is expected to deduplicate by `equals` — it deduplicates by `==` instead. That is the intended use, but it surprises anyone assuming standard `Comparator` semantics.
- **Order is arbitrary and not stable across runs.** It is built on `System.identityHashCode`, so the sequence has no meaningful relation to value, hash, or allocation order, and differs run-to-run. Never rely on the actual ordering — only on its being a consistent total order within one run.
- **Order can differ for the same pair over time** in the collision-disambiguation case: which object gets the lower list index depends on first-comparison order. For a given set of live objects it stays consistent, but it is not derivable from the objects alone.
- **`map` is process-global mutable state** on the singleton, touched on every collision; it grows with collisions and shrinks via weak cleanup. Normal load keeps it tiny (identity-hash collisions among equal-class objects are rare), but it is not free.

## Tech debt / warts

- The `//Or use Phantom?` note on `Ref` flags an unresolved choice between weak and phantom references for the cleanup hook.
- Commented-out debug lines remain in `compare` (a `println` and an `h1=h2=0` collision-forcing line) — leftover test scaffolding.
- The disambiguation list is a `LinkedList` searched linearly per collision; fine because collision lists are expected to be length 1–2, but it is O(n) per `compare` in the (rare) collision path.
