# Deep-revalidation (`Piles`)

Force a manually-overridden, invalid-dependency subtree to recompute on demand — the bulk "revalidate everything reachable" operation plus the thread-local switches that govern the per-node deep-revalidate machinery.

Source folder: **`src`**. File: `src/pile/impl/Piles.java`. Per-node mechanism lives in [`AbstractReadListenDependency`](../AbstractReadListenDependency.md) (ARLD) and [`PileImpl`](../PileImpl.md); the registry semantics are in [`Dependency`](../../aspect/Dependency.md) and [concepts/transactions.md](../../../concepts/transactions.md). Up: [Piles index](_index.md) · [overview](../../../overview.md).

This is a *delta* over the javadoc — read it alongside the per-method javadoc, not instead of it.

## The problem it solves

Normally an invalid `Dependency` holds an open transaction on each `Depender` and the invalidation cascades downstream automatically (see [transactions.md](../../../concepts/transactions.md) § how the diamond is handled). Deep-revalidation exists for the case Pile deliberately allows that breaks that cascade: **writing a value while one of its dependencies is invalid** ("valid despite invalid dependencies"). When you `set` a computed `D` whose dependency `X` is invalid, `D` becomes valid and *keeps its manual value*; the normal cascade will **not** recompute `D` later, because a valid depender is not invalidated.

To make this recoverable, each such node is recorded in a **registry**: the depender side keeps `thisNeedsDeepRevalidate`, and the registry propagates up so each invalid dependency keeps a `dependersNeedingDeepRevalidate` set (ARLD; registered via `Dependency.__dependerNeedsDeepRevalidate` — see [Dependency.md](../../aspect/Dependency.md) § deep revalidation). "Deep-revalidate" is then the operation that walks that registry and forces the manually-overridden subtree to recompute against fresh inputs.

### What "deep revalidate" does to one node

`fireDeepRevalidate` (ARLD) is the per-dependency trigger: while *this* value is still invalid, it drains its `dependersNeedingDeepRevalidate` set and calls `Depender.deepRevalidate(this)` on each registered depender. `deepRevalidate` (overridden in `PileImpl`, `Independent`) is `revalidate` + re-`fireDeepRevalidate`, so the call recurses transitively through the chain of manually-overridden nodes until the whole subtree has recomputed. It is gated: it no-ops if the value is already valid, if it has no dependers, if its registry is empty, or while a `suppressDeepRevalidation` `Suppressor` is held (ARLD `deepRevalidationSuppressors`).

`fireDeepRevalidateOnSet` (ARLD) is the variant fired *from within `set`*, before the value turns valid (`PileImpl.set` path; see [transactions.md](../../../concepts/transactions.md) § the subtle part). It is the "an invalid dependency was set manually → cascade through everyone who overrode me" half of the interaction.

## `superDeepRevalidate` — the bulk operation

`superDeepRevalidate(d, followDependency, followInfluencer)` ignores the registry entirely and brute-forces a whole reachable region:

1. Call `collectDependenciesAndInfluencers` to gather every object transitively reachable from `d`.
2. Keep only those that are a `Pile` **with a recomputer defined** (`Pile._isRecomputerDefined`) — a plain `Independent`/`Constant` has nothing to recompute and is skipped.
3. Open a single `Suppressor.many(PileImpl.SUPPRESS_AUTO_VALIDATION, found)` over *all* the collected piles, then `revalidate` each one inside that scope.

The auto-validation suppression in step 3 is the point: it stops each individual `revalidate` from eagerly cascading and triggering redundant recomputes mid-traversal, so the batch settles once at the end instead of repeatedly. This is a heavyweight "recompute this entire subtree now" hammer, distinct from the registry-driven `fireDeepRevalidate` that only touches nodes that actually overrode an invalid dependency.

## `collectDependenciesAndInfluencers` — the traversal primitive

`collectDependenciesAndInfluencers(o, followDependency, followInfluencer, dedup, found)` is a plain recursive DFS over two kinds of edge:

- **Dependency edges:** if `o` is a `Depender`, it enumerates `giveDependencies` and recurses into each `Dependency dep` for which `followDependency.test(dep)` is true.
- **Influencer edges:** if `o` is `HasInfluencers`, it enumerates `giveInfluencers` and recurses into each `i` for which `followInfluencer.test(i)` is true. Influencers are a looser "this affects that" relation than a hard dependency.

The two `Predicate`s let a caller prune the walk (e.g. follow dependencies but not influencers, or stop at a boundary). The `dedup` set both **prevents reprocessing** and **doubles as the result accumulator** — `dedup.add(o)` returns false for an already-seen node, which is what terminates **cycles** (Pile dependency graphs can contain influencer loops). Each genuinely-new node is reported once to the `found` callback (so `superDeepRevalidate` can filter while the set keeps the full closure). The method returns `dedup`. Note `o` itself (the starting `Depender`) is included in the closure, not just its dependencies.

## Thread-local switches

Two `ThreadLocal<Boolean>` flags govern whether the per-node machinery actually fires on the current thread. Both are **default-on**: the getters treat `null` (unset) and `TRUE` as "yes" and only an explicit `FALSE` disables — `shouldDeepRevalidate()` / `shouldFireDeepRevalidateOnSet()` return `!Boolean.FALSE.equals(get())`.

- **`shouldFireDeepRevalidateOnSet()` / `withShouldFireDeepRevalidateOnSet(Boolean)`** — consulted by `fireDeepRevalidateOnSet` (ARLD). When false, a `set` on an invalid value does **not** kick off the deep-revalidate cascade through its overriding dependers. (There is also a *per-instance* gate, `ARLD.shouldFireDeepRevalidateOnSet` set via `__shouldFireDeepRevalidateOnSet`; both must allow it. Note the field and the `Piles` thread-local share a name — they are different switches.)
- **`shouldDeepRevalidate()` / `dontDeepRevalidate()` / `withShouldDeepRevalidate(Boolean)`** — consulted by `fireDeepRevalidate` (ARLD), which early-returns when false. It is *also* consulted by `ARLD.fireValueChange`, which — interestingly — does the opposite: if the flag is currently false it **re-enables it (to true) for the duration of listener notification**, so listeners always see normal deep-revalidate behavior even when the surrounding code suppressed it.

The setters return a `MockBlock` (a close-only scope handle) that restores the previous value, so they are used with try-with-resources:

```java
try (MockBlock b = Piles.dontDeepRevalidate()) {
    // bulk-mutate a cluster of manually-overridden values here;
    // no per-set deep-revalidate cascades fire on this thread
}   // previous flag value restored
```

### When you'd flip them

You flip these off when you are about to make a batch of writes into overridden/invalid values and do **not** want each individual write to trigger an (expensive, possibly re-entrant) deep-revalidate cascade — you suppress the per-write firing, do the batch, then revalidate deliberately (e.g. via `superDeepRevalidate`) once. The default-on design means you never have to opt *in*; deep-revalidation just works, and these switches are escape hatches for the rare batch case. They are **thread-scoped**, so they only affect work done on the flipping thread within the `MockBlock`.

## Recipe — force a subtree to recompute now

```java
Piles.superDeepRevalidate(
    rootDepender,
    dep -> true,        // follow every Dependency edge
    infl -> true);      // follow every influencer edge
```

To prune, return false from a predicate for edges you don't want to cross (e.g. stop at a known boundary value, or skip influencers entirely with `infl -> false`).

## Caveats & gotchas

- **Cost.** `superDeepRevalidate` walks the *entire* reachable closure and revalidates every recomputable pile in it; on a large graph this is expensive. It is a deliberate hammer, not something to call routinely (recall Pile targets a few hundred values — see [overview.md](../../../overview.md)).
- **Cycles** are safe only because of the `dedup` set; if you pass a `found` callback that itself mutates the graph you can still surprise the traversal.
- **Name collision.** `Piles.shouldFireDeepRevalidateOnSet` (thread-local) and `ARLD.shouldFireDeepRevalidateOnSet` (per-instance field) are *different* gates with the same name; both must permit firing. Easy to conflate when reading.
- **Default-on, FALSE-only-off.** Setting the thread-local to `null` does **not** disable it — only `Boolean.FALSE` does. `withShould…(null)` effectively re-enables.
- **`Independent`/`Constant` are skipped** by `superDeepRevalidate` (no recomputer); only computed `Pile`s are revalidated. Their *dependencies* are still traversed.
- `superDeepRevalidate` ignores the `dependersNeedingDeepRevalidate` registry — it revalidates *everything* reachable, not just nodes that actually overrode an invalid dependency. That is the registry-driven path (`fireDeepRevalidate`), a separate mechanism.

## Tech debt / warts

- The shared name between the thread-local flag and the ARLD instance field is a readability trap.
- The asymmetry whereby `fireValueChange` force-re-enables `shouldDeepRevalidate` during listener notification is subtle and undocumented in the javadoc; it means the suppression is not honored uniformly. The `Dependency` aspect javadoc already flags the deep-revalidate *propagation/suppression* semantics as uncertain (an open `TODO` — see [Dependency.md](../../aspect/Dependency.md) § salient behavior).

## Related

- [concepts/transactions.md](../../../concepts/transactions.md) — "valid despite invalid dependencies", the registry, and how the per-node `deepRevalidate` cooperates with transactions.
- [AbstractReadListenDependency.md](../AbstractReadListenDependency.md) — `fireDeepRevalidate`, `fireDeepRevalidateOnSet`, the registry fields, `suppressDeepRevalidation`.
- [PileImpl.md](../PileImpl.md) — the `set` path that fires deep-revalidate-on-set and the `deepRevalidate` override.
- [Dependency.md](../../aspect/Dependency.md) — `__dependerNeedsDeepRevalidate`, `suppressDeepRevalidation`.
- [Piles index](_index.md) · [overview.md](../../../overview.md).
