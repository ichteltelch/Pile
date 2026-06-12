# `TransformableDependency`

A graph-dependency that also supports the *transform* mechanism — `TransformableValue` + `Dependency` (via `ReadWriteDependency`), so a covariant/homomorphic transformation applied to a value can be propagated to its dependers.

Source folder: `src` · package `pile.aspect.combinations`.

Up: [combinations index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md).

## What it's for

`TransformableDependency<E>` is the assembly point that says "this value is *both* transformable *and* a dependency". It is a thin union:

```
TransformableDependency<E>
  extends TransformableValue<E>, Dependency, ReadWriteDependency<E>
```

- [`TransformableValue<E>`](../../transform/) — *(doc pending)* carries the whole transform protocol; see below.
- [`Dependency`](../Dependency.md) — can be depended on; this is what lets a transform fan out to dependers.
- `ReadWriteDependency<E>` — *(doc pending)* read + write + depend-on (`ReadWriteValue` + [`ReadDependency`](ReadDependency.md)), no listen.

The interface adds **no transform machinery of its own** — it only narrows return types. The reason it exists separately is that the transform protocol (in `TransformableValue`) only does something useful when the value is also a `Dependency` (so the request can propagate); pairing the two in one interface gives `Pile` a single supertype to extend.

## Methods it declares

This interface declares only covariant-return overrides — the actual contract is inherited:

- `TransformableDependency<E> setName(String s)` — debug name; return type narrowed to itself.
- `asDependency` — `default` returns `this` (it *is* a `Dependency`), refining `TransformableValue.asDependency` which may return `null` for non-dependency transformables.
- `setNull` — `default` delegates to `ReadWriteDependency.super.setNull` then returns `this`, narrowing the return type.

For the substantive surface, read the supertype docs; everything below is inherited from `TransformableValue`.

## The transform mechanism (inherited from `TransformableValue`)

The transform feature lets you apply a transformation to a value and have selected dependers *transform themselves the same way* instead of fully recomputing — either because they would recompute "covariantly" to the same result anyway (cheaper to just transform), or to preserve manual edits across the transformation. The protocol, all on `TransformableValue` (`src/pile/aspect/transform/TransformableValue.java`):

- **`transform(Object transform, Runnable afterCollect)`** — the entry point. Collects reactions across this value and its transitive dependers, then runs the per-value jobs (in parallel threads, except `fast` reactions which run sequentially), then ends the transactions so `RECOMPUTE` values revalidate.
- **`collectTransformReactions(...)`** — walks the depender graph under `GLOBAL_TRANSFORM_COLLECT_MUTEX`, asking each value's `TransformHandler` for a `TransformReaction`; propagation stops at a dependency whose reaction is `IGNORE`. A `null` handler defaults to `RECOMPUTE`; a `null` reaction defaults to `IGNORE`.
- **`getTransformHandler(Object transform)`** — per-value strategy lookup; returns `null` if this transform kind isn't handled.
- **`runTransform(TypedReaction<E>)` / `runTransformRevalidate`** — actually mutate/replace, or revalidate, the value.
- **transform transactions** — `transformTransaction` / `beginTransformTransaction` / `endTransformTransaction` mark a value as *mid-transform*. These are **distinct from ordinary `transaction`s**; while one is active, `set(...)` and `permaInvalidate` block until the transform ends, so avoid calling them from code running during a transform.
- **`checkForTransformEnd([BehaviorDuringTransform])`** — re-entrancy guard threaded through the read/write paths to react when a transform is ongoing (`BehaviorDuringTransform` is *(doc pending)*).
- **`valueTransformMutated`** — fired after a transform mutates the value, to emit a transform value-event.

`TransformReaction`, `TransformHandler`, `BehaviorDuringTransform`, `TransformingException` live in the not-yet-documented `pile.aspect.transform` package.

## Where it's implemented

`TransformableDependency` is extended by exactly one interface, the capstone [`Pile`](Pile.md), and the concrete implementation is **`PileImpl`** (in `pile.impl`), which supplies the real transform methods — e.g. `valueTransformMutated` and the many `checkForTransformEnd(...)` calls woven through its set/read/validity paths. There is no transform-only concrete type; transform is a `Pile` capability.

## Caveats & gotchas

- **The transform feature is explicitly rudimentary/immature.** The `TransformableValue` javadoc warns it "is quite primitive… it does what I need it to do". The project `README.md` (Transformation feature) adds: do **not** run two concurrent transformations on overlapping parts of the dependency graph, and the propagated transformation cannot be changed mid-flight. The overview lists transformation among the immature features ([overview](../../../overview.md)).
- **A single global mutex** (`GLOBAL_TRANSFORM_COLLECT_MUTEX`) serializes the collect phase across the whole process — another reason concurrent transforms are unsupported.
- **Don't `set`/`permaInvalidate` during a transform** — those block on the transform transaction (see above).
- This interface itself is trivial; if you're trying to understand or extend transform behavior, read `TransformableValue` and `PileImpl`, not this file.

## See also

- [`Pile`](Pile.md) — the only extender / actual capability holder.
- [`Dependency`](../Dependency.md) · [`ReadDependency`](ReadDependency.md) — the dependency side.
- [concepts](../../../concepts/) — transactions/validity model that transform transactions sit alongside.
