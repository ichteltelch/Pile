# `pile.aspect.Depender`

Source folder: `src`. File: `pile/aspect/Depender.java`.

The **aspect interface a reactive value implements when it depends on other values.** It is the depending counterpart to [`Dependency`](Dependency.md) (the value that is *depended on*). Concrete piles implement it via the assembled contracts in `pile.aspect.combinations` and the `AbstractReadListenDependency` (ARLD) base.

See the [overview](../../overview.md) for where this sits in the architecture, and [concepts/transactions.md](../../concepts/transactions.md) for how the begin/end-changing callbacks below drive validity propagation, the diamond, and deep-revalidation.

## What it is for

A `Depender` exposes the surface needed to (a) wire up and tear down dependency relationships, (b) receive the begin/end-changing and validity callbacks a `Dependency` fires at it, (c) take part in deep-revalidation and long-term-invalidity bookkeeping, and (d) manage *essential* dependencies and destruction. As with `Dependency`, most methods are **framework-internal plumbing**; the `__`-prefixed ones carry explicit "do not call from anywhere else" warnings.

## Key methods by purpose

### Adding / removing dependencies (the client-ish surface)
The two primitives carry separate `recompute` and `recordChange` flags:
- `addDependency(Dependency d, boolean recompute, boolean recordChange)` / `removeDependency(...)` — the full forms. `recompute` = trigger a recomputation; `recordChange` = record the `Dependency` as changed.
- Convenience overloads collapse the two flags into one (`addDependency(d, recomputeAndRecordChanged)`, /) or default to `true` (`addDependency(d)` = recompute, /).
- **Varargs forms only recompute on the *last* element** so a batch add/remove triggers a single recomputation: `addDependency(Dependency... ds)` passes `recompute = (i == ds.length-1)` and always records change; `addDependency(boolean recompute, Dependency... ds)` gates that last-element recompute on the flag. `null` entries are skipped.
- `dependsOn(Dependency d)` — test the current relationship.
- `getDependencies` returns all current dependencies as a `Dependency[]`; `giveDependencies(Consumer)` iterates them.

### Change-propagation callbacks (internal — fired by a `Dependency`)
These are the depender side of the begin/end bracket described in [transactions § the diamond](../../concepts/transactions.md):
- `dependencyBeginsChanging(Dependency d, boolean wasValid, boolean invalidate)` — a dependency is entering a transaction that might change it. **Do not call from anywhere else**.
- `dependencyEndsChanging(Dependency d, boolean changed)` — that transaction finished; `changed` says whether value/validity actually changed. **Do not call from anywhere else**.
- `escalateDependencyChange(Dependency newlyInvalidDependency)` — a dependency that *already* notified begin-changing but was still valid has now become invalid.

### Validity / revalidation callbacks (internal — `__`)
- `__dependencyIsNowValid(Dependency d)` — a dependency became valid.
- `__dependencyBecameLongTermInvalid(Dependency d)` — a dependency became *long-term* invalid; if this depender is itself invalid it should become long-term invalid too and make that invalidity observed. De-duplicated across a branching/rejoining graph via the static `informingLongTermInvalid` set (below).
- `deepRevalidate(Dependency d)` — re-validate this depender and all transitive dependers that need deep revalidation because they became valid while a dependency was invalid. This is the depender side of the registry on `Dependency#__dependerNeedsDeepRevalidate` — see [transactions § deep-revalidate](../../concepts/transactions.md).

### Essential dependencies & lifecycle
- `setDependencyEssential(boolean essential, Dependency d)` — mark a dependency essential; an essential dependency's destruction triggers this value's destruction. Varargs convenience loops over it.
- `isEssential(Dependency value)` — query that state.
- `destroy` — destroy this value; it must not be used afterward. `isDestroyed` queries that.
- `deepDestroy` — destroy this *after* destroying everything that depends on it transitively through **essential** relations.

### Sealing escape hatch
- `getPrivilegedDepender` — if this is a `Sealable` value, its dependencies normally can't be changed once sealed; this returns a proxy that can still call the blocked dependency-mutating methods.

### Static
- `informingLongTermInvalid` — a `ThreadLocal<HashSet<Object>>` used only by long-term-invalidity propagation. Each propagating call aborts if its target is already in the set, else adds it and proceeds, so the "inform" runs **once per object, thread, and external call** even in diamond graphs. The *first* such call owns cleanup; the set is empty/null when no such call is on the stack (; see `AbstractReadListenDependency#informLongTermInvalid`).

## Salient / surprising behavior

- **Batch add/remove recomputes only once.** The varargs overloads deliberately set `recompute` true only on the last non-null element. If you split a logical batch across several single-arg `addDependency(d)` calls you get a recomputation per call instead.
- **`recompute` and `recordChange` are independent.** The primitives let you add a dependency without recording it as changed, or vice versa; the single-flag and varargs overloads hide this by tying them together (the varargs always pass `recordChange = true`).
- **Long-term-invalidity propagation is deliberately de-duplicated via a `ThreadLocal`.** Without `informingLongTermInvalid`, a rejoining dependency graph would inform a shared depender multiple times.
- **The `__`-prefixed and the begin/end-changing methods are part of the depender↔dependency protocol** and carry hard "do not call from elsewhere" warnings; invoking them directly breaks invariants.

## Caveats & gotchas

- Do not call `dependencyBeginsChanging` / `dependencyEndsChanging` / `escalateDependencyChange` / `__dependencyIsNowValid` / `__dependencyBecameLongTermInvalid` / `deepRevalidate` yourself — they are fired by the `Dependency` side of the protocol.
- After sealing a `Sealable`, ordinary dependency mutation is blocked; you must route through `getPrivilegedDepender`.
- `deepDestroy` only cascades through **essential** relations; non-essential dependers are not destroyed.
- **Javadoc drift:** several `@param invalidate` tags actually document the `recompute` parameter (the param is named `recompute`, the doc says `invalidate` — , , , , ), and `__dependencyBecameLongTermInvalid`'s Javadoc has a mismatched `{@link Depender)` brace. Trust the signatures over the tags.

## Common tasks (how to…)

- **Add one dependency and recompute:** `depender.addDependency(d)`.
- **Add several dependencies with a single recomputation:** `depender.addDependency(d1, d2, d3)` (varargs, recomputes once on the last — ).
- **Add dependencies without recomputing yet:** `depender.addDependency(false, d1, d2, …)`.
- **Mark a dependency as load-bearing:** `depender.setDependencyEssential(true, d)` so destroying `d` destroys this value.
- **Tear down a subtree:** `deepDestroy` to also destroy transitive essential dependers.
- **Mutate dependencies of a sealed value:** get `getPrivilegedDepender` first.

## Tech debt / warts

- `@param` Javadoc names disagree with the actual parameter names (`invalidate` vs `recompute`) on most add/remove forms.
- Malformed Javadoc link `{@link Depender)` in `__dependencyBecameLongTermInvalid`.
- Heavy reliance on `__`-prefixed "do not call" methods plus a `ThreadLocal` to express protocol/access constraints the language can't enforce — consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../overview.md)).

## Related

- [`Dependency`](Dependency.md) — the counterpart aspect (registration, validity, the deep-revalidate registry).
- [concepts/transactions.md](../../concepts/transactions.md) — the begin/end-changing bracket, the diamond, and deep revalidation.
- [overview.md](../../overview.md) — architecture map.
