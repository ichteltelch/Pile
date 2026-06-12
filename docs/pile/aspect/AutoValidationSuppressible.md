# `pile.aspect.AutoValidationSuppressible`

The **aspect interface a thing (or group of things) implements when its automatic recomputation can be temporarily suspended via a released `Suppressor`.**

Source folder: `src`. File: `pile/aspect/AutoValidationSuppressible.java`.

It is one of the framework's several "suppress a behavior with a `Suppressor`" aspects — the sibling of [`Dependency`](Dependency.md)'s `suppressDeepRevalidation` and of [`Sealable`](Sealable.md)'s sealing idiom, except here the thing being switched off is **auto-validation** (automatic recompute when all dependencies become valid). The interface is a thin contract; the real behavior lives in [`PileImpl`](../impl/PileImpl.md) (citations below are mostly to `PileImpl.java`).

See the [overview](../../overview.md) for where this sits, and [concepts/transactions.md](../../concepts/transactions.md) for how auto-validation is one of the gates on recomputation.

## What "auto-validation" is, and what suspending it prevents

A `Pile` is *auto-validating* when it will try to recompute its value **as soon as it is invalid, all dependencies are valid, and (for lazy values) validation is requested** — the definition lives on the companion query interface [`CanAutoValidate`](CanAutoValidate.md). Recomputation is gated on several conditions; "auto-validation isn't suppressed" is one of them, alongside not-in-transaction and a pending-recompute slot (see the [core mental model](../../overview.md) and `PileImpl.java`).

`suppressAutoValidation` returns a [`Suppressor`](suppress/Suppressor.md) that **switches that gate off** for the duration. While at least one such `Suppressor` is unreleased, the value will **not** auto-recompute even when it is invalid and all its dependencies are valid. It does *not* freeze the value otherwise: explicit `set`s, invalidations, and dependency changes still happen and are remembered (in `changedDependencies`); they are simply not acted on by a recompute until suppression lifts. This is the tool you reach for when you are about to mutate several dependencies and don't want a recompute storm in between (the `AbstractValueList` bulk mutators wrap each batch in `head.suppressAutoValidation`, e.g. `AbstractValueList.java`, , ).

## The method surface

- **`suppressAutoValidation`** → a `Suppressor`; suppression is active from creation until the first `release`. Reified as the static `SUPPRESS_AUTO_VALIDATION` function.
- **`suppressAutoValidation(SuppressMany s)`** → adds the needed `Suppressor`(s) to a `SuppressMany` and returns `s`, so many values can be suppressed together under one release.

### Suppressor release semantics (counter, not flag)

In `PileImpl`, suppression is **reference-counted**, not boolean. `suppressAutoValidation` increments `autoValidationSuppressors`; each returned `Suppressor` decrements it on its first `release` (`releaseAutoValidationSuppressor`, `PileImpl.java`). `isAutoValidating` is simply `autoValidationSuppressors <= 0`. So **nested/overlapping suppressors stack**, and auto-validation only resumes when the *last* one is released.

Per the `Suppressor` contract, the behavior stays suppressed as long as one unreleased suppressor exists **even if that suppressor has been garbage-collected** — use `wrapWeak` if you need release tied to GC. `Suppressor` is `SafeCloseable`, so the idiomatic use is try-with-resources (see common tasks).

### Recompute on release (caveat)

Releasing the **last** suppressor does not merely re-arm the gate — if the value is currently invalid *and* has accumulated changed dependencies, `releaseAutoValidationSuppressor` **immediately schedules and starts a recomputation**. I.e. the writes you batched under suppression are flushed into a single recompute the moment suppression fully lifts. Releasing a *non-last* suppressor (counter still `> 0`) does not recompute. A double-release on a single instance is a no-op (`release` returns `false` on later calls, `Suppressor.java`); over-release across instances would drive the counter negative and is guarded with an `IllegalStateException`.

## Reactive observation of the state

The companion [`CanAutoValidate`](CanAutoValidate.md) exposes the state both as a snapshot and reactively:

- `isAutoValidating` — boolean snapshot.
- `autoValidating` — a lazily-created, sealed `ReadListenDependencyBool` you can **subscribe to** to observe the auto-validating flag flip as suppressors are taken and released (`CanAutoValidate.java`; built in `PileImpl.java`). `PileImpl` pushes updates into it on every create/release via `setAutoValidating.accept(isAutoValidating)`.

Note `AutoValidationSuppressible` itself carries **no** query/observe methods — for those you need the `CanAutoValidate` sub-interface.

## The shape sub-interfaces (`Multi` / `Single` / `None`)

The interface ships three default-implementation mixins so an implementer only writes one of the two methods:

| Sub-interface | You implement | You get for free | Use when |
|---|---|---|---|
| `Multi` | `suppressAutoValidation(SuppressMany)` | `suppressAutoValidation` — builds a `SuppressMany`, releasing it on failure | your object yields **several** suppressors |
| `Single` | `suppressAutoValidation` | `suppressAutoValidation(SuppressMany)` — `makePlaceFor1.add(...)` | your object yields **one** suppressor, or you want them organized hierarchically |
| `None` | nothing | both, as no-ops returning `Suppressor.NOP`; `None.JUST` is a shared instance | a superclass forces you to implement the aspect but there is nothing to suppress |

`Multi` also has `of(...)` factories that compose an `AutoValidationSuppressible` from an array / `Iterable` / iterable-of-iterables of sub-suppressibles.

## Bulk and switched suppression (the reason this interface exists)

The interface's stated purpose is **collective** suppression: because it reifies "how to get a `Suppressor` from this object" as the static handles, many values can be suppressed and released as a unit.

- `SUPPRESS_AUTO_VALIDATION` — `Function<AutoValidationSuppressible, Suppressor>`; feed it to `Suppressor.many(fn, collection)` / `Suppressor.many(fn, vararg)`.
- `SUPPRESS_AUTO_VALIDATION_ADD` — `BiConsumer<…, SuppressMany>` adding one object's suppressor to a `SuppressMany`.
- `SUPPRESS_AUTO_VALIDATION_COLLECTION` — the lifted form adding a whole `Iterable`'s worth.
- `makeSwitcher` / `makeReactiveSwitcher` — build a `SuppressionSwitcher.Final` / `ReactiveSuppressionSwitcher` that hands out and swaps suppressors over the objects it controls, driven by `SUPPRESS_AUTO_VALIDATION`.

## Salient / surprising behavior

- **Suppression is a stacking counter, not an on/off flag**. Two callers suppressing concurrently both have to release before auto-validation resumes.
- **Releasing the last suppressor can immediately recompute** — it is not a passive "re-enable", it actively flushes pending changes. Don't release inside a context that must not trigger graph recomputation.
- **GC does not release for you** — an unreleased-but-collected suppressor keeps the behavior suppressed. Always `release`/close, or `wrapWeak`.
- **The aspect ≠ the query.** `AutoValidationSuppressible` only *suppresses*; to *read/observe* the flag you need `CanAutoValidate`.

## Caveats & gotchas

- Forgetting to release a suppressor silently wedges a value as never-auto-validating — it will look "stuck invalid". Prefer try-with-resources so release is guaranteed.
- A `Multi.suppressAutoValidation` that fails mid-build releases the partial `SuppressMany` for you, but a `Single` gives you a single suppressor whose release is entirely your responsibility.
- Over-releasing across multiple suppressor instances trips the negative-counter guard; the underlying single-instance `release` is idempotent, the *counter* is not.

## Common tasks (how to…)

- **Batch several writes without intermediate recomputes:**
  `try (Suppressor s = v.suppressAutoValidation) { /* mutate v and its deps */ }` — the recompute (if any) fires once at the close (`PileImpl.java`; pattern used throughout `AbstractValueList`).
- **Suppress a whole collection under one release:**
  `Suppressor s = Suppressor.many(AutoValidationSuppressible.SUPPRESS_AUTO_VALIDATION, values); … s.release;`.
- **Suppress several values into an existing `SuppressMany`:** call `each.suppressAutoValidation(many)` or use `SUPPRESS_AUTO_VALIDATION_ADD` / `…_COLLECTION`.
- **Observe whether a value is currently auto-validating:** subscribe to `((CanAutoValidate) v).autoValidating`.
- **Implement the aspect on a new type:** extend `Single` (one suppressor) or `Multi` (several); use `None` when there's nothing to suppress.

## Tech debt / warts

- Several Javadoc `@return` tags are bare and the class comment has a typo (*"some kind if autovalidating"*, ), consistent with the [project-wide note on unsystematic API](../../overview.md).
- Access control is by runtime counter rather than type: nothing stops a caller from over-releasing across instances; the negative-counter guard exists precisely because that can happen.
- The split between *suppress* (`AutoValidationSuppressible`) and *query/observe* (`CanAutoValidate`) means you frequently need a cast to the latter to check what the former changed.

## Related

- [`CanAutoValidate`](CanAutoValidate.md) — the query/observe sub-interface (`isAutoValidating`, reactive `autoValidating`, `autoValidate`).
- [`Dependency`](Dependency.md) — sibling aspect with the same `Suppressor` idiom (`suppressDeepRevalidation`).
- [`Sealable`](Sealable.md) — another `Suppressor`-adjacent "lock down a behavior" aspect.
- [concepts/transactions.md](../../concepts/transactions.md) — auto-validation as one of the gates on recomputation, and how suppressing it interacts with batched writes.
- [overview.md](../../overview.md) — architecture map; the implementation lives in `pile.impl.PileImpl`.
