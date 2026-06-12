# `pile.aspect.CanAutoValidate`

The aspect a reactive value implements when it can **auto-validate** — recompute its value as soon as it is invalid and its dependencies are ready, recursing over the transitive dependency graph.

Source folder: `src`. File: `pile/aspect/CanAutoValidate.java`.

`CanAutoValidate extends AutoValidationSuppressible`: every auto-validating value also exposes the surface to *suppress* that auto-validation. It is the capability side of that pairing — [`AutoValidationSuppressible`](AutoValidationSuppressible.md) says "this can be suppressed"; `CanAutoValidate` says "this is currently set to (and able to) auto-validate, and here is how to force it."

See the [overview](../../overview.md) for the architecture map, and [concepts/transactions.md](../../concepts/transactions.md) for how recomputation, validity, and transactions interact (auto-validation is one of the gates on whether a `Pile` recomputes).

## What it adds

Three members beyond the inherited suppression API:

- `isAutoValidating` — snapshot: is this value *currently* set to auto-validate (no suppressors active, not destroyed)?
- `autoValidating` — a reactive `ReadListenDependencyBool` mirroring that state for observation.
- `autoValidate` — **imperatively** recompute now if invalid, recursing over all transitive `Dependency`s.
- `autoValidationInProgress` — a `static ThreadLocal<HashSet<CanAutoValidate>>` cycle-guard shared by all `autoValidate` calls on a thread.

## `autoValidate` — capability vs. mechanism

The interface only *declares* the capability. The real recompute behavior lives in the implementations:

| Implementor | `autoValidate` | `isAutoValidating` |
|---|---|---|
| `PileImpl` | the real algorithm | `!destroyed && autoValidationSuppressors<=0` |
| `AbstractReadListenDependency_NoDepender` (base of `Independent`, `Constant`, `SealPile`-without-recompute, …) | **overridden to a literal no-op** `{ return; }` | always `false` |

So for values with **no concept of recomputation/invalidity** (an `Independent` is always valid), `autoValidate` is a deliberate no-op and `isAutoValidating` is `false` — exactly the "can be ignored" case the Javadoc describes.

`PileImpl.autoValidate` does, per value:
1. Enter the thread-local cycle guard; **abort if `this` is already in it** — see below.
2. Under `mutex`: if `lazyValidating`, or already valid, or `invalidated` is being cleared, return; else clear `invalidated`.
3. `__scheduleRecomputation(false)`, then `__startPendingRecompute(true)` **only if `isAutoValidating`**.
4. Recurse: `giveDependencies(Dependency::autoValidate)` when not all dependencies are valid.
5. The *starter* call clears the thread-local on exit.

Note step 3: even an imperative `autoValidate` will only actually *start* the recompute when auto-validation is not suppressed; otherwise it schedules but leaves the recompute pending until a [`Suppressor`](AutoValidationSuppressible.md) is released (`PileImpl.releaseAutoValidationSuppressor`, ).

## Relation to `Dependency.autoValidate`

`Dependency` re-declares `autoValidate` with the same contract (`Dependency.java`, documented in [Dependency.md](Dependency.md)); the recursion in step 4 calls it through the `Dependency` view. `CanAutoValidate` is the standalone capability interface, while `Dependency.autoValidate` is the same method surfaced on the dependency-target contract — they resolve to the **same implementations**, not two behaviors. Treat the two doc entries as describing one method from two interfaces.

## The cycle guard (`autoValidationInProgress`)

`autoValidate` walks a dependency graph that can branch and re-join (the "diamond"). To call `autoValidate` **at most once per Dependency, per thread, per outermost call**, the first call on the stack creates a `HashSet` in the `ThreadLocal`; each call aborts if `this` is already a member, otherwise adds itself and proceeds; the *starter* is responsible for nulling the set out on exit. This mirrors the analogous `lazyValidatingItt` guard for `lazyValidate`.

## Salient behavior & caveats

- **No-op for valueless/always-valid types.** `Independent`/`Constant` etc. inherit the empty override; do not expect `autoValidate` to "do" anything on them.
- **Suppression gates the actual start.** With an auto-validation `Suppressor` held, `isAutoValidating` is `false` and `autoValidate` schedules but does not start the recompute until release.
- **Lazy validation also gates it.** While `lazyValidating`, `autoValidate` returns early; lazy values validate on request via `lazyValidate` instead. (Lazy validation is flagged immature project-wide — see [overview § caveats](../../overview.md).)
- **`isAutoValidating` is `public` here but `protected abstract` on the impl base.** `AbstractReadListenDependency` declares it `protected abstract`; `PileImpl` widens it to `public` to satisfy this interface. The `_NoDepender` base overrides the *protected* one, so the public widening only takes effect on the full `PileImpl` branch.
- **`autoValidating` is lazily built and sealed** — a self-keeping `IndependentBool` created on first call; the setter is wired so suppress/release flips it.
- The Javadoc uses `#see` instead of `@see`, so those links don't render — consistent with the project-wide "unsystematic API/Javadoc" note.

## Common tasks (how to…)

- **Force an invalid subtree to recompute now:** call `autoValidate` on the value (recurses over dependencies). For a single value with no dependencies, `lazyValidate` may be what you want instead.
- **Batch writes without intermediate recomputes:** hold `suppressAutoValidation` (from [`AutoValidationSuppressible`](AutoValidationSuppressible.md)) across the writes; release triggers the catch-up recompute. See the `AbstractValueList` idiom: `try(Suppressor s = head.suppressAutoValidation){ … } finally { head.autoValidate; }`.
- **Observe auto-validation state reactively:** subscribe to `autoValidating`.
- **Check it cheaply:** `isAutoValidating` (non-blocking snapshot under `mutex`).

## Tech debt / warts

- The capability is split across `CanAutoValidate` (public), `Dependency` (re-declares), and a `protected abstract` on `AbstractReadListenDependency` — three declaration sites of one method, with the public/protected widening only meaningful on one branch.
- `#see` (not `@see`) Javadoc tags don't link.
- The thread-local cycle-guard's cleanup contract is by-convention ("first call cleans up after itself", `CanAutoValidate.java`); a thrown exception inside a non-starter frame relies on the starter's `finally`.

## Related

- [`AutoValidationSuppressible`](AutoValidationSuppressible.md) — the superinterface; how to build/compose `Suppressor`s that turn auto-validation off.
- [`Dependency`](Dependency.md) — re-declares `autoValidate`; the dependency-target contract the recursion walks.
- [concepts/transactions.md](../../concepts/transactions.md) — recomputation gating, validity propagation, the diamond.
- [overview.md](../../overview.md) — architecture map and project-wide caveats.
