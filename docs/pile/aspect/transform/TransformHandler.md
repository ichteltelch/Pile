# `TransformHandler`

Functional interface that, given a value and a transform object, decides how the value reacts to a transform request (returns a [`TransformReaction`](TransformReaction.md)).

Source folder: `src` · package `pile.aspect.transform`.

See the package [_index.md](_index.md) for how the transform mechanism fits together, the project [overview](../../../overview.md), and [concepts/transactions.md](../../../concepts/transactions.md). The transform feature is **rudimentary/immature** by the author's own note (see the package index).

## What it's for

`TransformHandler<E>` is a `@FunctionalInterface` with one method:

```java
TransformReaction react(TransformableValue<E> owner, Object transform);
```

It is the per-value **policy** consulted when a transform is propagated through the dependency graph: for `owner` and the (untyped, `Object`) `transform` descriptor, it returns a [`TransformReaction`](TransformReaction.md) saying whether to ignore, recompute, mutate in place, replace, or just propagate. The reaction kinds are described in [`TransformReaction.md`](TransformReaction.md).

The handler is stored on a [`TransformableValue`](TransformableValue.md) and retrieved by `getTransformHandler(transform)`; the value's `collectTransformReactions(...)` then calls `react` to gather reactions across itself and its dependers.

## Two distinct null-conventions (gotcha)

The transform-collection contract distinguishes **two** nulls, and they mean opposite things:

- **`getTransformHandler(transform)` returns `null`** → "this kind of transform is not handled" → reaction is assumed **`RECOMPUTE`** (invalidate and recompute).
- **the handler's `react(...)` returns `null`** → reaction is treated as **`IGNORE`** (do nothing).

So a missing handler recomputes, but a present handler that declines (returns null) ignores. Do not conflate the two. Note that the constant handlers below never return null, and `PileImpl`'s default handler is `IGNORE` (not null), so the `RECOMPUTE`-on-null path only fires when something deliberately hands back a null handler.

## Constant handlers (the convenience instances)

The interface exposes five shared singleton handlers, each ignoring its arguments and always returning the matching `TransformReaction` constant:

- `IGNORE` / `ignore` → `TransformReaction.IGNORE`
- `RECOMPUTE` / `recompute` → `TransformReaction.RECOMPUTE`
- `UNCHANGING` / `unchanging` → `TransformReaction.UNCHANGING`
- `JUST_PROPAGATE_NO_TRANSACTION` / `justPropagate_noTransaction`
- `JUST_PROPAGATE_WITH_TRANSACTION` / `justPropagateWithTransaction`

The `static <E> ...` factory methods just cast the raw singleton to the requested element type (`@SuppressWarnings("unchecked")`) — type-safe because these handlers ignore the value. Prefer the factory methods when you need a typed handler.

These are the *only* "untyped" reactions; they are wrapped by the nested `UntypedReaction` class (see below). The two value-producing reaction kinds (`MUTATE`/`REPLACE`) cannot be constants — they carry the per-value transform function — so there is no constant handler for them; you build [`MutateReaction`](TransformReaction.md)/[`ReplaceReaction`](TransformReaction.md) per request inside a custom handler.

## Nested reaction classes

Defined inside this interface (not as separate top-level files):

- **`UntypedReaction`** — a `final TransformReaction` holding just a `ReactionType`. The five `TransformReaction` singletons (`IGNORE`, `RECOMPUTE`, `UNCHANGING`, `JUST_PROPAGATE_*`) are instances of it. "Untyped" = needs no `<E>` because it carries no transform function.
- **`TypedReaction<E>`** — abstract base for the value-bearing reactions, i.e. [`MutateReaction`](TransformReaction.md) (`MUTATE`) and [`ReplaceReaction`](TransformReaction.md) (`REPLACE`), both top-level classes in this package. Adds:
  - `E apply(E in)` — the actual transformation (default identity; overridden by the subclasses). May throw `InvalidValueException`.
  - `cancel` — runs the optional `cancelCode` `Runnable` passed at construction; called "when the transformation code was not actually invoked because the value had become invalid" (e.g. `MutateReaction.apply` calls `cancel` and throws if the held value changed out from under it — `MutateReaction.java`).

## How `PileImpl` consults it

`PileImpl` implements `TransformableValue`'s handler accessors:

- A single field `th` holds the current handler, initialized to a shared **`DEFAULT_TRANSFORM_HANDLER`** = `(v,o) -> TransformReaction.IGNORE` (so by default a `PileImpl` ignores transforms).
- `_setTransformHandler(t)` stores `t`; passing `null` **resets to the default IGNORE handler** (not to a null field). So `getTransformHandler` on a `PileImpl` never returns null — the `RECOMPUTE`-on-null path above does not arise from `PileImpl` itself.
- `getTransformHandler(Object transform)` ignores its argument and returns `th` (the one handler, regardless of transform kind). Per-transform discrimination on a `PileImpl` must therefore be done *inside* the handler — that is what [`MultiTransformHandler`](MultiTransformHandler.md) is for.

The handler can also be set at build time via the builder (`IPileBuilder`/`AbstractPileBuilder` expose a `transformHandler(...)`; see those if doing builder work) and `Pile` declares the contract.

## Combining handlers

[`MultiTransformHandler`](MultiTransformHandler.md) is a non-SAM implementation that dispatches to sub-handlers by `Predicate<? super Object>` on the transform object, falling through to a configurable default reaction (initially `IGNORE`). A sub-handler that returns `null` is **skipped** (the next matching criterion is tried), reinforcing the "react→null means decline" reading. It is explicitly **not synchronized** — build it fully before sharing.

## Common tasks

- **Ignore transforms (default):** do nothing, or `_setTransformHandler(TransformHandler.ignore)`.
- **Recompute on transform:** `_setTransformHandler(TransformHandler.recompute)`.
- **Mutate/replace the held value:** write a custom `(owner, transform) -> new MutateReaction<>(owner, e -> ...)` (or `ReplaceReaction`); these are the only reactions that actually run transform code.
- **Different policies per transform kind:** use [`MultiTransformHandler`](MultiTransformHandler.md) with one criterion + sub-handler per kind.
- **Reset to default:** `_setTransformHandler(null)`.

## Caveats & gotchas

- The two null conventions above are the main trap.
- `transform` is untyped `Object`; there is no compile-time check that a handler matches the transform descriptors a value will actually receive.
- `getTransformHandler` ignores the transform argument in `PileImpl`; do not expect per-transform handler selection without `MultiTransformHandler`.
- A custom handler returning a `MUTATE`/`REPLACE` reaction may have its `apply` skipped and `cancel` invoked if the value goes invalid before the transform runs (`TypedReaction.cancel`, `MutateReaction.java`).
- Operations like `set`/`permaInvalidate` block while a transform transaction is active (see [`TransformableValue`](TransformableValue.md)); avoid them from handler/transform code.

## Tech debt / warts

- Commented-out `allowMultipleTransforms` / `AllowingMultipleTransforms` scaffolding — vestige of the unfinished "concurrent overlapping transforms" feature the package index flags as missing.
- The whole transform subsystem is documented (by the author) as immature; treat its API as unstable.
