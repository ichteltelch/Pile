# `pile.aspect.LazyValidatable`

The aspect that lets a reactive value **defer recomputation until its value is actually requested**, instead of recomputing eagerly when it becomes invalid.

Source folder: `src`. File: `pile/aspect/LazyValidatable.java`.

`LazyValidatable` is a tiny granular aspect interface. [`Dependency extends LazyValidatable`](Dependency.md), so every dependency — and therefore every [`Pile`](combinations/Pile.md) — is lazy-validatable. See the [overview](../../overview.md) for where this sits, and [concepts/transactions.md](../../concepts/transactions.md) for the validity/recomputation machinery it hooks into.

> **Immaturity warning (in source).** The interface Javadoc states outright: *"The lazy-validating feature of Piles is not mature and will probably change in the future!"*. A whole alternative `couldBeValid(...)` API sits commented out in both the interface and `PileImpl`, and the test still references it in dead comments. Treat the whole feature as provisional.

## What it is for

Normally a `Pile` recomputes "as soon as possible" once it is invalid and its dependencies are valid (eager / auto-validation). Marking a value **lazy-validating** suppresses that eager recompute; the value only recomputes when something actually reads it (`get`, `getValid`, …) and calls `lazyValidate` (described under *Members* below). This is useful for expensive computations whose result may never be needed.

## Members

- `boolean isLazyValidating` — whether this value is currently lazy-validating.
- `void setLazyValidating(boolean newState)` — turn the mode on/off. Turning it **off** should attempt a recomputation if nothing else forbids it.
- `void lazyValidate` — the on-demand recompute trigger. If the value is already valid or already recomputing, it does nothing; otherwise it recurses over all [`Dependency`](Dependency.md) dependencies and then recomputes itself **only if** the sole reason it was not already computed is the lazy flag / an explicit invalidation. **Caveat from the contract:** if lazy-validating the dependencies does not make all of them valid immediately, this value's own recompute may not happen.
- `static ThreadLocal<HashSet<LazyValidatable>> lazyValidatingItt` — reentrancy guard (see below).

## Override map (implementations)

| Type | `isLazyValidating` | `lazyValidate` | `setLazyValidating` |
|---|---|---|---|
| [`PileImpl`](../impl/PileImpl.md) | real flag, read under `mutex` | full algorithm | toggles flag + adjusts auto-validation suppressors |
| `Constant` | `false` | no-op | no-op |
| `Independent` | `false` | no-op | no-op |

`PileImpl` is the only meaningful implementation; `Constant` and `Independent` have no "invalid" state so the whole aspect is inert for them (mirroring how they no-op `autoValidate` etc.).

## How it works in `PileImpl` (salient behavior)

- **Lazy = an auto-validation suppressor.** `setLazyValidating(true)` does `++autoValidationSuppressors`; `setLazyValidating(false)` does `--autoValidationSuppressors` and only auto-validates if the count is back to `0`. So lazy validation is layered on top of [`AutoValidationSuppressible`](AutoValidationSuppressible.md): an externally-held [`Suppressor`](suppress/Suppressor.md) keeps the value lazy even after you clear the flag. The call is idempotent — setting the same state returns early.
- **Reads trigger it.** Every value accessor checks the flag and calls `lazyValidate` before locking: `get`, `getValid(..)`, `getOldIfInvalid`, `getValidOrThrow`. That is the "until the value is actually requested" mechanism.
- **`lazyValidate` algorithm**: bail if already valid (`__valid`) or a recomputation is ongoing and unfinished; clear `invalidated`; `__scheduleRecomputation(false)`; if not `allDependenciesValid`, recurse via `giveDependencies(Dependency::lazyValidate)` so dependencies validate themselves on-demand; then `__startPendingRecompute(true)`.
- **Reentrancy / diamond guard.** The static `ThreadLocal<HashSet<LazyValidatable>>` ensures each object is lazy-validated **once per thread per top-level call**, even in branching-and-rejoining (diamond) dependency graphs. The *first* `lazyValidate` on the stack owns the set: it creates it, and in the `finally` clears it back to `null`; nested calls return early if `this` is already in the set. This matches the contract spelled out at `LazyValidatable.java`.

## Interaction with auto-validation

Because lazy-validation is implemented as a suppressor count, it composes with `suppressAutoValidation` rather than overriding it. Turning lazy off does **not** force a recompute while other suppressors are held. See `LazyValidateTest.java` for a worked scenario that holds a weak auto-validation `Suppressor` on `v3` while `v2` is lazy.

## Common tasks (how to…)

- **Make a pile lazy at construction:** use the builder's `lazy` — it just calls `setLazyValidating(true)` on the value. E.g. `Piles.compute(...).lazy...`.
- **Make an existing pile lazy / eager:** `pile.setLazyValidating(true|false)`.
- **Force an on-demand validation without reading:** call `lazyValidate` directly (rarely needed — any `get*` does it for you).
- **Check the mode:** `isLazyValidating`.

## Caveats & gotchas

- **Immature / in flux** — see the warning above; the API may change.
- **Recompute is not guaranteed by `lazyValidate`.** If a dependency does not become valid synchronously, this value may stay invalid; a read can therefore still return an invalid/`null` result.
- **`lazyValidatingItt` is shared static state** with a strict ownership protocol; only `lazyValidate` may touch it, and the first caller must clean it up. Calling into it incorrectly breaks the once-per-object guarantee.
- **Turning lazy off is not always a recompute trigger** — other auto-validation suppressors win.

## Tech debt / warts

- Dead `couldBeValid(...)` API commented out in three places — evidence of an abandoned/unfinished design direction.
- Feature explicitly self-described as not mature (see [overview § caveats](../../overview.md), which lists lazy validation among the immature features).

## Related

- [`Dependency`](Dependency.md) — extends this aspect; `lazyValidate` recurses over dependencies.
- [`AutoValidationSuppressible`](AutoValidationSuppressible.md) — lazy validation is implemented as a suppressor on top of it.
- [concepts/transactions.md](../../concepts/transactions.md) — the recomputation/validity machinery lazy validation defers.
- [overview.md](../../overview.md) — architecture map and project-wide caveats.
