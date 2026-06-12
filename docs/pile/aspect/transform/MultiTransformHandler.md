# `MultiTransformHandler`

Combines several [`TransformHandler`](TransformHandler.md)s into one, trying them in insertion order and returning the first usable [`TransformReaction`](TransformReaction.md).

Source folder: `src` · package `pile.aspect.transform`.

See the package [_index.md](_index.md) and the [overview](../../../overview.md). The transform mechanism is described as rudimentary in the [package index](_index.md); concepts live under [`../../../concepts/`](../../../concepts/).

## What it's for

A single `TransformHandler.react(value, transform)` decides how a value reacts to a transformation. `MultiTransformHandler` lets you assemble a dispatch chain of sub-handlers, each optionally guarded by a `Predicate` on the `transform` object, with a fallback reaction when nothing applies.

## How it composes handlers

Two parallel lists are maintained: `crits` (the guard predicates) and `sub` (the handlers), kept in lockstep by `add`.

`react` walks the lists in **insertion order** and, for each entry whose predicate tests `true` on the `transform`, calls the sub-handler. The **first sub-handler that returns a non-`null` reaction wins** and is returned immediately. If a guarded handler returns `null`, the loop continues to later entries. If no entry produces a non-`null` reaction, `defaultReaction` is returned (initially `TransformReaction.ignore`, `MultiTransformHandler.java`).

So selection is gated by **two** conditions per entry: the predicate must match **and** the handler must yield non-`null`. Note this is *not* "first non-`IGNORE` wins" — an explicit `IGNORE` reaction is a non-`null` value and so wins as soon as it is returned; only a literal `null` is skipped over.

## Key methods

- `add(Predicate, then)` — append a guarded handler.
- `add(then)` — append an always-tried handler; the guard is `Functional.CONST_TRUE`, so the predicate always passes and selection reduces to "first non-`null` reaction".
- `setDefault(reaction)` — replace the fallback used when nothing matches.
- `react(v, transform)` — the dispatch loop.

All three mutators return `this` for fluent chaining.

## Who creates it

Constructed directly via `new MultiTransformHandler<>` and populated with `add(...)`; there is no builder. It is the only `TransformHandler` aggregator in the package.

## Caveats & gotchas

- **Not thread-safe by design.** The javadoc on both `add` overloads explicitly warns that the methods are unsynchronized and concurrent access can break the handler. Build it up fully before sharing it across threads.
- **Javadoc/code mismatch on `add(then)`.** Its javadoc describes a criterion that "matches if it returns a non-`null` reaction and none of the criteria of previously added handlers match" — but the code simply installs `CONST_TRUE` and relies on the same non-`null` check as every other entry. The "none of the previously added match" phrasing is misleading: earlier entries are tried first only because of insertion order, and a later `CONST_TRUE` entry is still reached whenever all earlier entries returned `null`. Read it as "always tried, falls through on `null`".
- **`IGNORE` is a real reaction, not a skip.** If you want a guarded handler to defer to later handlers, it must return `null`, not `TransformReaction.ignore`. Returning `IGNORE` stops the chain.
- Predicates receive the raw `Object transform`; they must do their own type-checking.

## Tech debt / warts

- The two `add` overloads share documentation that no longer matches the simplified `CONST_TRUE` implementation (see above).
- No synchronization and no defensive copying; the parallel-list invariant is unenforced.
