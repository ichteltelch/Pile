# `MultiSourceIntegrator`

Integrates several writable source values into one target by folding them through a user-supplied binary `integrate` function, and (optionally) writes the target's value back into the sources.

Source folder: `src`. Package: `pile.relation`. Up: [package index](_index.md), [overview](../../overview.md).

> **Not** an `AbstractRelation`. Despite living in `pile.relation`, `MultiSourceIntegrator` is a *standalone* class with no base class — it does not extend [`AbstractRelation`](AbstractRelation.md) and does not participate in the switchable-relation machinery. It is a relation in spirit (it maintains an invariant between values by writing back) but not in type.

## What "integrate" means here

You give it:
- a `target` (`ReadWriteListenDependency<T>`),
- a `neutral` `Supplier<T>` (the value to fall back to when nothing valid is available),
- an `integrate` `BiFunction<T,T,T>` — a fold/merge operator (think of it as a monoid binary op with `neutral` as identity).

The integrator collects the **distinct** (by identity) valid values among the sources, folds them with `integrate`, and pushes the result into `target`. The *exact* policy depends on the `monotonous` flag (see below) — it is **not** simply last-writer-wins; it is a **fold over all currently-valid distinct source values**.

## The two directions

The class wires two listeners (in the constructor and in the `sources` bracket):

- **source → target** (`sourceChanged`, driven by `sourceListener`): when any source fires, recompute the target by folding the source values. This is rate-limited (see *The guard*).
- **target → source** (`targetChanged`, driven by `targetListener`): when the target fires and is valid, copy the target's value into **every** source (skipping the source that *is* the target, if any). This is the write-back that makes the constraint two-way.

`target` is held with a **weak** value listener (`target.addWeakValueListener(targetListener)`), and each source is held weakly too (`addWeakValueListener(sourceListener)` in the `sources` bracket's install half). So the integrator does not keep its participants alive by itself.

## Configuring the source set: `sources` / `sourcesIndir`

The set of sources is itself reactive and **mutable at runtime**:
- `sourcesIndir` — an `Independent` holding a `ReadDependency<Set<ReadWriteListenValue<T>>>`. Write a new set-valued reactive here to change which values are integrated. Defaults to a shared constant empty set (`constEmptySet`).
- `sources` — a `SealPile` that **derefs** `sourcesIndir` (`setupDeref`), so it tracks the indirection. Its `ValueBracket` is where listener (un)wiring happens: when a new source set becomes current, it adds `sourceListener` to every member; when it leaves, it removes the listener. The bracket is `nopOnNull` (a null set installs nothing) and `defer(ListenValue.DEFER)`.

When the source set changes, the bracket also (re)computes `monotonous` and kicks `sourceListener.runImmediately()` once the value is valid (`monotonous.doOnceWhenValid(...)`) so freshly-added sources get synced.

## The `monotonous` flag — the policy switch

`monotonous` (public read-only `ReadListenDependencyBool`, backed by `monotonous__`) is set true **iff the source set contains the target itself** (`set.contains(target)`), recomputed in the `sources` bracket. It selects between two folding policies in `sourceChanged`:

- **Monotonous (target is one of the sources):** start the accumulator from the target's current valid value (`old`), then fold in the *other* distinct source values. The target's own previous value participates, so the integration is **incremental / accumulating** — the target can only "grow" by merging in news, never lose its old contribution. (Hence "monotonous": e.g. a union-of-sets or OR-accumulation where the target is also a source.)
- **Non-monotonous (target is not a source):** start from scratch (`accu=null`, `oldValid=false`), fold all distinct valid source values fresh each time; if the source set is `null`, set the target to `neutral.get()`.

So the **conflict policy when several sources change** is: **fold them all together with `integrate`** over the distinct-by-identity valid values — not last-writer-wins. The order of folding is `IdentityHashMap` key-iteration order (effectively unspecified); `integrate` should therefore be associative/commutative for predictable results.

### Distinctness and the early-out
Values are de-duplicated by **reference identity** into an `IdentityHashMap` (`distinct`). If `distinct.size() <= 1` the method returns without writing — there is nothing to integrate (all sources, plus `old` in the monotonous case, already agree by identity). This means a fold is only performed when at least two distinct values exist.

### Re-sync on source-set change
When the event is an "all sources" event (`e.allSources()` — fired when the source set itself changed, see *The guard*), after setting the target the code calls `targetChanged(null)` **if the target value did not actually change** (`!oldValid || old==actuallySet`). Rationale (per the in-code comment): the target won't have fired its own event, so newly-added sources would be left out of sync; this manual call distributes the current target value to them.

## The guard / suppression

Two distinct mechanisms keep the feedback loop (source→target→source→…) from running away:

1. **Rate limiting.** `sourceListener` is `ValueListener.rateLimited(10, 100, this::sourceChanged)` — a [`RateLimitedValueListener`](../aspect/listen/RateLimitedValueListener.md) with a 10 ms cold-start delay and 100 ms cool-down. Its handler receives a `MultiEvent`, not a single `ValueEvent`, coalescing a burst of source changes into one run. `MultiEvent.allSources()` is true when the listener was poked with a `null` (e.g. via `runImmediately()` from the bracket) meaning "consider every source"; otherwise `getSources()` is the specific set that changed.
2. **Deferral during the fold.** `sourceChanged` brackets its whole body in `ListenValue.DEFER.__incrementSuppressors()` / `__decrementSuppressors()` (in a `finally`). Events that the writes generate are deferred until the fold completes, so the write-back doesn't re-trigger mid-computation.

Both `sourceChanged` and `targetChanged` `synchronized (this)`, so the two directions never interleave on one integrator instance.

Note: unlike most relations in this package, the loop is **not** broken with [`Nonreentrant`](../utils/Nonreentrant.md); it relies on rate-limiting + `DEFER` + the distinct-value early-out instead.

## Invalid handling

- **`targetChanged`** does nothing unless `target.isValid()`; inside it reads `target.getValidOrThrow()` and silently swallows `InvalidValueException` (target became invalid mid-flight → no write-back this round).
- **`sourceChanged`** reads each source with `source.getValid(1000)` (block up to 1000 ms for validity) and only folds in `val` when `source.isValid()`. Invalid sources are skipped. The target is read with `getValid(10)` (10 ms) in the monotonous branch.
- If, after folding, nothing valid contributed (`!accuValid`), the target is set to `neutral.get()`.
- `getValid(...)` can throw `InterruptedException`; `sourceChanged` catches it, re-sets the thread's interrupt flag, and bails (the `finally` still decrements the suppressor).

## Common tasks

- **Create an integrator:** `new MultiSourceIntegrator<>(target, neutral, integrate)`. Then populate the sources by writing a set-valued reactive into `sourcesIndir`.
- **Make the target also be one of its sources (accumulating mode):** include `target` in the source set — `monotonous` flips true automatically and the fold becomes incremental.
- **Change the participating sources at runtime:** set a new value into `sourcesIndir`; listener wiring and `monotonous` are recomputed by the `sources` bracket.

## Caveats & gotchas

- **Not a relation type.** No `AbstractRelation` base, no on/off switch, no standard teardown — you cannot `switch` it off; drop all references (listeners are weak) to let it be collected.
- **Fold order is unspecified** (`IdentityHashMap` iteration). Use an associative+commutative `integrate` or accept order-dependence.
- **Distinctness is by identity, not `equals`.** Two equal-but-not-identical source values are folded as two distinct inputs; two identical references count once. The `size()<=1` early-out also keys off identity.
- **`getValid` timeouts are silent.** A source that stays invalid past 1000 ms is simply omitted from the fold; the target past 10 ms (monotonous) is treated as invalid `old`.
- **Write-back targets *every* source unconditionally** (except the target-as-source). After an integration that changes the target, all sources are overwritten with the integrated value — sources are not preserved independently unless `monotonous` keeps re-merging them.
- **`InvalidValueException` is swallowed empty** in `targetChanged` — intentional (transient invalidity), but invisible.

## Tech debt / warts

- The class is an outlier in `pile.relation`: it reimplements its own loop-breaking and lifecycle rather than reusing the `AbstractRelation` / `SwitchableRelation` infrastructure, so it can't be switched and shares none of that teardown.
- Magic timeouts (`getValid(10)`, `getValid(1000)`) and rate-limit constants (`10, 100`) are hardcoded.
- Commented-out dead line in the `sources` bracket (`//Recomputations.NOT_NOW.run(...)`).
- The shared static `CONST_EMPTY_SET` is an unchecked-cast singleton reused across all type parameters.
</content>
</invoke>
