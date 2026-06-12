# `pile.impl.AbstractReadListenDependency_NoDepender`

The [`AbstractReadListenDependency`](AbstractReadListenDependency.md) (ARLD) variant for reactive values that are **not [`Depender`](../aspect/Depender.md)s and have no concept of recomputation** — the base of [`Independent`](Independent.md) (and similar always-valid, non-`Depender` values); it stubs out the auto-validate / pending-recompute hooks to no-ops. **Note: [`Constant`](Constant.md) does NOT extend this** — it implements `ReadWriteListenDependency` directly.

Source folder: `src`. File: `pile/impl/AbstractReadListenDependency_NoDepender.java` (~34 lines).

## What it's for

ARLD's machinery (transactions, listeners, brackets, the inform-queue) is useful even for values that **never recompute and never depend on anything** — e.g. an `Independent` (a writable cell that is always valid). (`Constant` is a similar always-valid value but, unlike `Independent`, implements the interfaces directly rather than extending this base.) But ARLD also declares abstract `__`-hooks for *recomputation* and *auto-validation* that such values have no meaning for. This thin abstract subclass fills exactly those hooks with trivial answers, leaving the rest of ARLD's abstract surface (value/validity representation, etc.) for the concrete subclass to implement.

The class adds **no state and no new methods** — it only overrides. It is the "no `Depender`" half of ARLD's two-way split; the full reactive branch is [`PileImpl`](PileImpl.md).

## The invariant it assumes

An instance initializer asserts the defining precondition:

```java
{ assert !(this instanceof Depender); }
```

Subclasses must genuinely not be `Depender`s. The override behavior below is only *sound* under that invariant: a value with no dependencies can never be "invalid because a dependency changed," so there is nothing to auto-validate or recompute.

## The override map (delta over ARLD)

Everything here is a stubbed ARLD abstract/overridable hook. Lines are current.

| Member | ARLD | here | meaning |
|---|---|---|---|
| `autoValidate` | the auto-validate entry point (abstract via [`CanAutoValidate`](../aspect/CanAutoValidate.md)) | `{ return; }` | nothing to validate — no-op |
| `isAutoValidating` | `protected abstract` | `false` | never auto-validates |
| `__shouldRemainInvalid` | `protected abstract` | `false` | never invalid, so never "should remain invalid" |
| `cancelPendingRecomputation(boolean)` | `protected abstract` | `false` | no recompute can be pending → nothing cancelled |
| `cancelPendingRecomputation(boolean, boolean)` | `protected abstract` | `false` | as above |
| `__startPendingRecompute(boolean force)` | `protected abstract` | `{ }` | no recompute to start — no-op |

Contrast with the [`PileImpl`](PileImpl.md) branch, where these are the real recomputation algorithm. See [`CanAutoValidate.md`](../aspect/CanAutoValidate.md) for the side-by-side `autoValidate`/`isAutoValidating` table across implementors.

## What it leaves abstract

It does **not** implement the value/validity hooks — `__valid`/`__value`/`__oldValid`/`__oldValue`, `__setValidity`, `moveValueToOldValue`/`copyValueToOldValue`/`__restoreValueFromOldValue`, `__hasChangedDependencies`/`__clearChangedDependencies`, `__dependencies`, `revalidate`, `isComputing`, etc. (see [ARLD § Abstract hooks](AbstractReadListenDependency.md)). Those remain for the concrete subclass (`Independent`) to define — which is why this class is itself `abstract`.

## Salient behavior & caveats

- **The empty `autoValidate` / `false` `isAutoValidating` are deliberate, not stubs-to-be-filled.** For an always-valid, dependency-free value there is nothing to recompute, so "force a recompute now" is correctly a no-op. Callers that `autoValidate` an `Independent` should not expect any effect — see [`CanAutoValidate.md` § Salient behavior](../aspect/CanAutoValidate.md).
- **`isAutoValidating` here overrides the *protected* declaration on ARLD.** [`CanAutoValidate`](../aspect/CanAutoValidate.md) exposes a `public isAutoValidating`; `PileImpl` widens the ARLD protected method to public to satisfy it, but on *this* branch the override stays at the protected level. The public-interface widening therefore only matters on the `PileImpl` branch (see `CanAutoValidate.md`).
- **The `Depender` exclusion is an assertion, not a compile-time guarantee.** If a subclass *did* implement `Depender`, the assert would catch it only with assertions enabled; the no-op overrides would otherwise silently mishandle real dependencies.
- One override is commented out: `scheduleRecomputationWithActivatedTransaction` — historical, no current effect.

## Common tasks (how to…)

- **Write a new always-valid, dependency-free reactive type:** extend this class instead of ARLD directly, supply the value/validity hooks, and you get the recompute/auto-validate surface stubbed for free. Existing example: [`Independent`](Independent.md). (`Constant` reaches the same always-valid behavior by implementing the interfaces directly instead of extending this base.)
- **Understand why `autoValidate` does nothing on an `Independent`:** it inherits the `{ return; }` here.

## Tech debt / warts

- The `Depender`-exclusion invariant is enforced only by an `assert`; it is a documentation-by-assertion contract rather than a type-level constraint.
- A commented-out override is dead source noise.

## Related

- [`AbstractReadListenDependency`](AbstractReadListenDependency.md) — the parent; the machinery these overrides plug into (rely on its abstract-hooks section).
- [`Independent`](Independent.md) — the concrete subclass on this branch. ([`Constant`](Constant.md) is a sibling always-valid value but does NOT extend this — it implements `ReadWriteListenDependency` directly.)
- [`PileImpl`](PileImpl.md) — the *other* (full `Depender`/recomputing) ARLD branch, where these hooks carry the real algorithm.
- [`CanAutoValidate`](../aspect/CanAutoValidate.md) — the auto-validate capability; its doc already records this class's no-op overrides.
- [concepts/transactions.md](../../concepts/transactions.md) — recomputation/validity model these stubs opt out of.
- Package index: [`_index.md`](_index.md). Up: [overview](../../overview.md).
