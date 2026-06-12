# `Implication`

Enforce a material implication `premise → conclusion` between two reactive booleans, writing back when the invariant is violated.

Source folder: `src` (package `pile.relation`). Extends [`AbstractRelation`](AbstractRelation.md). Switchable variant: [`SwitchableImplication`](SwitchableImplication.md). Package index: [_index.md](_index.md). Project overview: [../../overview.md](../../overview.md).

## What it's for

An `Implication` keeps the logical statement **"if `premise` then `conclusion`"** true between two `ReadWriteListenValue<Boolean>`s. The only forbidden combination is `premise == true && conclusion == false`; the other three (`F→F`, `F→T`, `T→T`) are all valid. When the relation detects (or would create) the forbidden state, it writes back to one of the operands to restore consistency.

It is a *relation* (constraint layer), not a derivation: neither boolean is computed from the other, both remain independently writable, and the `Implication` only intervenes to repair a violation.

## The exact rule (which way it forces)

Two private one-directional repair primitives, each **conditional**:

- `forceConclusion()` — if `premise.get()` is `TRUE`, sets `conclusion` to `true`. (Read the premise; if the premise holds, the conclusion must hold.) Does nothing otherwise.
- `forcePremise()` — if `conclusion.get()` is `FALSE`, sets `premise` to `false`. (Read the conclusion; if the conclusion is false, the premise must be false too.) Does nothing otherwise.

Both are deliberately one-sided: `forceConclusion` never *clears* the conclusion, `forcePremise` never *sets* the premise. They only ever push toward the consistent state, never away from it. This is what makes the write-back loop terminate (see the re-entrancy section).

## How it reacts to each side changing

The single listener `vl` is registered on **both** operands via `addWeakValueListener` (the handles `removeFromPremise` / `removeFromConclusion` are kept for teardown). On each fire it first checks `isEnabledPrim()`; if the relation is disabled, it does nothing. Then it dispatches on the event source `e.getSource()`:

- **premise changed** (`src == premise`) → `forceConclusion()`. A premise that just became true drags the conclusion true; a premise that became false does nothing (`F→anything` is legal).
- **conclusion changed** (`src == conclusion`) → `forcePremise()`. A conclusion that just became false drags the premise false; a conclusion that became true does nothing.
- **source is `null`** (a "something happened, recheck everything" event, e.g. the initial `vl.runImmediately(true)` in the constructor, or an enable/disable transition) → the **conflict-resolution** path below.

So during steady-state operation each side is repaired in only the one direction that can be violated by *that* side moving. The full both-directions sweep happens only on a sourceless re-check.

## Conflict resolution on (re-)activation — `onConflictKeepPremise`

When the relation activates or is re-checked with no specific source, it may find an existing conflict (`premise==true, conclusion==false`) that neither side's change produced. The constructor parameter `onConflictKeepPremise` (a **nullable** `Boolean`) decides how to resolve it:

- `null` → **do nothing**; the conflict is left in place (no attempt to resolve).
- `TRUE` → keep the premise, fix the conclusion: call `forceConclusion()` **then** `forcePremise()`.
- `FALSE` → keep the conclusion, fix the premise: call `forcePremise()` **then** `forceConclusion()`.

Both primitives are run in both non-null cases — only the **order** differs. This is intentional: the first call expresses the preferred direction, but it "may fail due to corrections or [sealing](../aspect/Sealable.md)", so the other direction is tried as a fallback. If the preferred write goes through, the value is already consistent and the second call's guard (`TRUE.equals` / `FALSE.equals`) is false, so it no-ops; if the preferred write is rejected/corrected, the second call repairs from the other end. (Reflected in the javadoc on the constructor.)

## Handling of invalid / null booleans

Operand reads use `Boolean.TRUE.equals(...)` / `Boolean.FALSE.equals(...)` rather than auto-unboxing, so a **`null`** value (an invalid or unset boolean) is treated as "neither true nor false": both `forcePremise` and `forceConclusion` simply do nothing. The relation never forces on a `null` operand and never NPEs on one. There is no special invalid-value handling beyond this null-tolerance — an invalid operand just suppresses repair until it becomes a concrete `true`/`false`.

## The re-entrancy guard

There is **no explicit `Nonreentrant` / flag guard** inside `Implication` (unlike some other relations in this package, where the `_index.md` notes [`Nonreentrant`](../utils/Nonreentrant.md) breaks the loop). Termination here is **structural**, from the conditionality of the two primitives:

- `forceConclusion` writes `conclusion=true` only while `premise==true`. That write re-fires `vl` with `src==conclusion` → `forcePremise`, which writes `premise=false` only while `conclusion==false` — but the conclusion is now `true`, so `forcePremise` no-ops. The cascade stops.
- Symmetrically, `forcePremise` setting `premise=false` re-fires `forceConclusion`, which requires `premise==true` and so no-ops.

Because each primitive only ever moves the pair *toward* the single consistent state and never back, at most one corrective write per side is needed before the listeners observe a consistent pair and fall silent. Reaching consistency is the fixed point. (Note: this relies on `set` being a no-op / non-re-firing when the value is unchanged, and on the one-sided repair directions; it is not a generic re-entrancy lock.)

## Lifecycle

- **Construction** wires the two weak listeners, conditionally installs an enable-listener (only when `isEnabled() != Piles.TRUE`, i.e. for the switchable subclass — see below), and then calls `vl.runImmediately(true)` to perform the initial conflict check/repair.
- **`destroy()`** removes both value listeners (`removeValueListener` with the stored handles). It does **not** touch any enable-listener.

## Interaction with `AbstractRelation` enable plumbing

`Implication` itself is **always enabled**: it does not override `isEnabledPrim()` (returns `true`) or `isEnabled()` (returns `Piles.TRUE`). The constructor's `if(isEnabled()!=Piles.TRUE)` branch and the enable-listener it installs are therefore **dead for a plain `Implication`** and exist for [`SwitchableImplication`](SwitchableImplication.md), which overrides those to expose a reactive on/off boolean. When enabled-state matters, the enable-listener re-runs `vl` (a sourceless re-check → conflict resolution) unless `shouldActOnlyOnOperandChanges()` is true.

Note the constructor **re-implements** the enable-listener wiring inline rather than calling the base class's `installEnabledListener()` (which does the same thing). See the warts section.

## Caveats & gotchas

- **Asymmetric, one-sided repair.** Steady-state operation never clears a conclusion or sets a premise. If you set `premise=false`, the conclusion is left untouched (it may stay `true`); if you set `conclusion=true`, the premise is left untouched. Only the forbidden corner is repaired. Do not expect this to keep the two booleans equal — that is [`CoupleEqual`](CoupleEqual.md)'s job.
- **`onConflictKeepPremise` only matters at activation / sourceless re-check.** During ordinary operand changes the direction is fixed by which side changed; the preference flag is not consulted there.
- **Writes can silently fail.** Repair uses `premise.set` / `conclusion.set`, which may be vetoed by corrections or [sealing](../aspect/Sealable.md). The conflict-resolution path tries both directions to cope; the per-source paths do not, so a sealed/corrected operand can leave a steady-state violation unrepaired. This is idiomatic (a write to a value that rejects it is silently ignored), not a bug.
- **`null` operands suspend the constraint** rather than forcing a value — by design.
- **Weak listeners.** Operand listeners are added weakly (`addWeakValueListener`); keep a strong reference to the `Implication` (or call `destroy()` deliberately) so it is not collected while you still expect it to enforce the invariant.

## Tech debt / warts

- The constructor duplicates `AbstractRelation.installEnabledListener()` inline instead of calling it. The inline copy passes `vl.runImmediately(true)` (note the `true` arg) whereas the base method calls `vl.runImmediately()`; the divergence is easy to miss and a candidate for consolidation.
- `getListener()` exists only to satisfy the base class; the listener is also captured directly in the field `vl`, so there are two ways to reach the same object.
- The two private primitives `forcePremise` / `forceConclusion` are declared between unrelated members with several blank-line gaps — purely cosmetic, but the ordering obscures their pairing.
