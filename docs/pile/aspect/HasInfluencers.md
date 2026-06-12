# `pile.aspect.HasInfluencers`

Aspect interface for a value to expose its non-`Dependency` *influencers* (currently just the `owner`/derivation source), used only by `superDeepRevalidate`.

Source folder: `src`. File: `pile/aspect/HasInfluencers.java`.

This is a one-method interface accounting for the possibility that a reactive value is influenced by things *other than* its [`Dependencies`](Dependency.md) — primarily its **owner** (the larger structure it belongs to / the value it was derived from). It exists purely to feed the [`Piles.superDeepRevalidate`](../impl/Piles/_index.md) traversal; it is **not** part of the change-propagation protocol.

See the [overview](../../overview.md) for where aspects sit; its dependency-graph siblings are [`Dependency`](Dependency.md) (depended-on) and [`Depender`](Depender.md) (depends-on).

## The single method

- `giveInfluencers(Consumer<? super Object> out)` — push each influencing object into `out`. Influencers are plain `Object`s, not necessarily reactive values; the traversal that consumes them filters for the kinds it cares about.

There is **no default**; every implementor must supply the method.

## Override map

Both concrete implementors do the exact same thing — **emit the `owner` if it is non-null, nothing else**:

- `PileImpl.giveInfluencers` — `if(owner!=null) out.accept(owner);`. Inherited unchanged by `SealPile` and the specialized piles.
- `Independent.giveInfluencers` — identical body.

So in practice "influencers" == "the owner, if any". The `owner` field is set via `Pile#_setOwner(Object)` / `PileImpl._setOwner`, described as the pile's *owner/parent* "mainly for debugging" but also usable to **keep a strong reference to a value this one was derived from** so it isn't garbage-collected.

## Who has the aspect

- [`Pile`](combinations/Pile.md) (the capstone combination) `extends HasInfluencers`, so every full pile has it.
- `Independent` declares it directly — it is *not* a `Pile`, so it opts in separately.

## What consumes it

The aspect is wired into exactly one mechanism, in [`Piles`](../impl/Piles/_index.md):

- `collectDependenciesAndInfluencers(o, followDependency, followInfluencer, dedup, found)` — recursive transitive traversal. At each node it follows `Dependency` edges (if `o instanceof Depender`) **and** influencer edges (if `o instanceof HasInfluencers`), gated by the two predicates, de-duplicating via the `dedup` set.
- `superDeepRevalidate(Depender d, Predicate followDependency, Predicate followInfluencer)` — collects every transitively reachable `Pile` *that has a recomputer defined* (`_isRecomputerDefined`), suppresses their auto-validation, and `revalidate`s them. This is the only purpose the interface javadoc cites for the aspect's existence.

So `HasInfluencers` lets `superDeepRevalidate` cross from a value to the larger structure that produced it — a link the ordinary [`Dependency`](Dependency.md)/[`Depender`](Depender.md) graph does not record.

## Salient / surprising behavior

- **Influencers are not part of change propagation.** Unlike dependencies, an owner change does *not* invalidate the value. Influencer edges are consulted only when someone explicitly calls `superDeepRevalidate`/`collectDependenciesAndInfluencers`.
- **"Influencer" is, today, exactly "owner".** The interface is written to admit more kinds later, but no implementor emits anything but the owner.
- **The consumer takes `Object`, not a reactive type.** An owner may be any object (e.g. a GUI component or a `PileCompound`); `collectDependenciesAndInfluencers` only recurses into ones that are themselves `Depender`/`HasInfluencers`, and `superDeepRevalidate` only revalidates the `Pile`s among them.

## Caveats & gotchas

- Setting an owner via `_setOwner` is dual-purpose (debug label *and* GC keep-alive). If you rely on it for keep-alive, remember it also becomes a `superDeepRevalidate` edge.
- The `_`-prefixed `_setOwner` is a framework/advanced method; the javadoc frames the owner as mainly a debugging aid.

## Common tasks (how to…)

- **Make a derived pile's source reachable by `superDeepRevalidate` (and GC-pinned):** `derived._setOwner(source)` — `source` then shows up as an influencer.
- **Force-revalidate a whole structure including owner links:** `Piles.superDeepRevalidate(root, dep -> true, inf -> true)` (follow all dependency and influencer edges).
- **Enumerate a value's influencers:** call `value.giveInfluencers(System.out::println)` (yields the owner, if set).

## Tech debt / warts

- The aspect is deliberately minimal/provisional — its javadoc says it is "currently" only the owner and "used only" for `superDeepRevalidate`, signalling it may grow.
- Both implementations are byte-for-byte identical; the body could live in a shared base instead of being duplicated in `PileImpl` and `Independent`.

## Related

- [`Dependency`](Dependency.md) / [`Depender`](Depender.md) — the ordinary dependency-graph edges that influencers complement.
- [`Pile`](combinations/Pile.md) — the combination that mixes this aspect in.
- [`Piles`](../impl/Piles/_index.md) — `superDeepRevalidate` / `collectDependenciesAndInfluencers`, the only consumers.
- [overview.md](../../overview.md) — architecture map.
