# `AbstractRelation`

Minimal abstract base for the constraint layer: a relation holds participants **strongly**, listens to them **weakly**, and on a participant change writes back to re-establish an invariant. Source folder: `src` (package `pile.relation`).

Up: [overview](../../overview.md) · package index: [_index.md](_index.md). Siblings: [`CoupleEqual`](CoupleEqual.md), [`Implication`](Implication.md), [`Equalizer`](Equalizer.md). Loop-breaking helper: [`Nonreentrant`](../utils/Nonreentrant.md); weak-listener cleanup: [`WeakCleanup`](../utils/WeakCleanup.md).

## What it's for

`AbstractRelation` is a *thin* template. It does **not** itself register participants, attach listeners, hold the re-entrancy guard, or implement teardown — every concrete relation does that in its own constructor and `destroy()`. What the base actually provides is the small shared vocabulary that the *switchable* variants need: an "is this relation currently active?" notion (both as a plain `boolean` and as a reactive boolean), the "act only on operand changes vs. also on enable" policy flag, and a single helper (`installEnabledListener`) that wires "re-run the sync when the relation gets enabled". The two leaf hierarchies are `CoupleEqual` (with `SwitchableCoupleEqual`) and `Implication` (with `SwitchableImplication`).

Read this base together with at least one concrete subclass — the interesting mechanics (weak listeners, strong participant fields, the `Nonreentrant` guard, write-back) live there, not here.

## The abstract template — provided vs. required

**Subclasses must implement:**

- `getListener()` — return *the* `ValueListener` that performs the write-back / re-sync. It must be safe to call `runImmediately()` on. Subclasses build this listener in their constructor (`CoupleEqual.vl`, `Implication.vl`) and return it here. The base only ever uses it through `installEnabledListener`.
- `destroy()` — detach this relation's listeners from the participants and (where applicable) drop the strong references to them. See `CoupleEqual.destroy` (removes both listeners and nulls `op1`/`op2`) and `Implication.destroy` (removes both listeners; participants are `final` so not nulled).

**Provided (overridable) hooks:**

- `isEnabledPrim()` — plain-`boolean` "active right now?" check, consulted **inside the listener body** before doing any write-back (see `CoupleEqual.sync`, the lambda in `Implication`'s constructor). Base returns `true` (always active). Overridden by switchable leaves to consult the switcher — e.g. `SwitchableCoupleEqual.isEnabledPrim` returns `switcher != null && switcher.isEnabled().isTrue()`.
- `isEnabled()` — the same notion as a **reactive** `ReadListenDependencyBool`. Base returns the shared constant `Piles.TRUE`. Switchable leaves override to return the switcher's reactive flag (`ImplSwitchableRelation.isEnabled`, a sealed `IndependentBool`).
- `shouldActOnlyOnOperandChanges()` — policy: should the relation re-sync **only** when a participant changes (`true`), or also when the relation transitions to enabled (`false`)? Base returns `true`. Switchable leaves delegate to `ImplSwitchableRelation.shouldActOnlyOnOperandChanges`, which returns its `onlyOnChanges` field.

**Provided concrete helper:**

- `installEnabledListener()` — call this from a *switchable* subclass constructor (it is a no-op for the non-switchable base case, see below). It (1) if `isEnabled()` is not the constant `Piles.TRUE`, subscribes a listener to `isEnabled()` that calls `getListener().runImmediately()` whenever the relation is enabled — but only when `!shouldActOnlyOnOperandChanges()`; and (2) if `isEnabled()` is currently true and `!shouldActOnlyOnOperandChanges()`, runs the sync once immediately. So with the default "only on operand changes" policy, enabling the relation does **not** by itself force a re-sync — that only happens when `onlyOnChanges` is turned off.

## How participants are held — strong fields, weak listeners

This is the key safety property and is stated in `CoupleEqual`'s class javadoc: **the participants do not hold a strong reference back to the relation.** The mechanism:

- The relation keeps **strong** fields to its participants (`CoupleEqual.op1`/`op2`, `Implication.premise`/`conclusion`). So the relation reaches the values.
- The relation attaches its listener via `addWeakValueListener` (see the constructors), so the **values reach the relation only weakly**. The returned remove-handles (`removeFromOp1`/`removeFromOp2`, `removeFromPremise`/`removeFromConclusion`) are kept so `destroy()` can detach explicitly.

**Consequence / gotcha:** if nothing else holds a strong reference to the relation object, it becomes eligible for GC and the coupling silently stops working ("the values become decoupled randomly", per the javadoc). **You must store the relation somewhere yourself.** This is by design, not a bug — see [`WeakCleanup`](../utils/WeakCleanup.md) for the weak-listener machinery behind `addWeakValueListener`.

## Lifecycle

There is no `activate`/`deactivate` pair on the base. The lifecycle is:

1. **Construct** — the subclass constructor registers participants (strong fields), attaches weak listeners, and (via `initSync` / a direct `runImmediately(true)` call) optionally performs an initial sync so the invariant holds from the start. Switchable subclasses additionally build an `ImplSwitchableRelation` and call `installEnabledListener()`.
2. **Run** — on each participant `ValueEvent`, the listener fires; it first checks `isEnabledPrim()` and bails if inactive, then writes back according to its rule. A `null` event means "initial / forced sync" and is handled specially (e.g. `CoupleEqual.sync`'s leader→follower seeding based on `Mode`).
3. **Enable/disable (switchable only)** — handled entirely by the delegated `ImplSwitchableRelation` (`disable()` returns a `Suppressor`; releasing the last one re-enables). Re-establishing validity after re-enable is "implementation specific" per `SwitchableRelation.disable`'s javadoc; in practice it relies on the enabled-listener wired by `installEnabledListener` (only effective when `onlyOnChanges` is false).
4. **Destroy** — `destroy()` detaches the listeners; the strong participant fields may be nulled. After `destroy()` the relation no longer enforces anything.

## The feedback-loop guard — lives in the subclass, not here

The re-entrancy guard that stops write-back from looping is **not** in `AbstractRelation`. `CoupleEqual` owns a `Nonreentrant nr` and wraps its sync via `nr.fixed(this::sync, Functional.NOP)` so a write into `op1` that re-triggers the listener (because `op1` changed) is suppressed while the current sync is still on the stack. See [`Nonreentrant`](../utils/Nonreentrant.md).

Note the contrast: **`Implication` has no `Nonreentrant` guard.** It relies instead on its write-back being **idempotent / convergent** — `forceConclusion` only sets the conclusion `true` when the premise is `true`, and `forcePremise` only sets the premise `false` when the conclusion is `false`, so once the implication holds neither write changes anything and the cascade stops. Equality coupling has no such natural fixpoint in its writes, hence the explicit guard. When writing a new relation, decide which of these two strategies applies.

## Common tasks

- **Implement a new relation:** extend `AbstractRelation`; in the constructor store participants in strong fields, build a `ValueListener` that re-establishes the invariant (guarding against loops with `Nonreentrant` if writes aren't naturally convergent), attach it with `addWeakValueListener` keeping the remove-handles, optionally `runImmediately(true)` once; return the listener from `getListener()`; detach in `destroy()`.
- **Make it switchable:** also implement `SwitchableRelation`, hold an `ImplSwitchableRelation switcher`, delegate `isEnabled()`/`disable()`/`shouldBeEnabled()`/etc. to it, override `isEnabledPrim()` to consult the switcher, and call `installEnabledListener()` at the end of the constructor. See `SwitchableCoupleEqual`.
- **Stop a relation:** call `destroy()`. Do not rely on dropping references alone unless you *want* nondeterministic GC-timed teardown.

## Caveats & warts

- **No-op helper for the base case.** For a non-switchable relation `isEnabled()` is `Piles.TRUE`, so `installEnabledListener` does nothing; the non-switchable constructors don't call it anyway. It is purely a switchable-subclass utility despite living on the base.
- **`runImmediately()` arity mismatch.** `installEnabledListener` calls `getListener().runImmediately()` (no-arg) in two places, whereas every subclass constructor calls `runImmediately(true)`. The `true` overload appears to mean "treat as forced/initial sync". The base's no-arg calls may therefore re-run with different semantics than an initial sync — verify against `ValueListener.runImmediately`'s contract before relying on enable-triggered re-sync. Flagged in `possible-bugs` for the developer to judge.
- **GC-based decoupling** (above) is the single biggest footgun for callers: keep the relation reachable.
- The base carries no `Nonreentrant` of its own — loop safety is each subclass's responsibility (see above). Don't assume extending `AbstractRelation` gives you re-entrancy protection.
