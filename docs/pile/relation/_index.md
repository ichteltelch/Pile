# `pile.relation` — package index (Tier 1)

Source folder: `src`.

The **constraint layer**: objects that maintain an invariant *between* reactive values rather than computing one value from others. A relation watches its participants and writes back to keep some property true — two values kept equal, a logical implication enforced, or a group of booleans constrained (exactly-one / at-most-one / at-least-one true). Relations are the bidirectional counterpart to the (one-directional) recompute/derive machinery.

**Common patterns across the package (read first):**
- **Strong field, weak listener.** Relations hold their participants by strong fields but attach listeners *weakly*, so the participants do not retain the relation. **You must keep the relation object reachable** or the constraint silently dies on GC — by design.
- **Loop-breaking varies.** `CoupleEqual` and `MultiSourceIntegrator` use explicit guards ([`Nonreentrant`](../utils/Nonreentrant.md) / rate-limit + suppression); `Implication` and the `BooleanGroup_*` classes instead rely on *structural* convergence (a write-back no-ops once the invariant holds, often via an "active pointer updated before the write" trick).
- **Not all extend `AbstractRelation`.** `Equalizer` and the three `BooleanGroup_*` classes are standalone; only the couple/implication family uses the base.

Up: [overview](../../overview.md). Building blocks: [`Nonreentrant`](../utils/Nonreentrant.md), [combinations index](../aspect/combinations/_index.md).

## Base & switchability
- [`AbstractRelation`](AbstractRelation.md) — thin base for the couple/implication relations: holds participants strongly, listens weakly, writes back to keep an invariant; provides the enabled/active hooks the switchable variants need.
- [`SwitchableRelation`](SwitchableRelation.md) — interface for a relation that can be turned on/off at runtime, gating whether it enforces its invariant (two AND-ed channels: stacked `Suppressor`s and a reactive `shouldBeEnabled`).
- [`ImplSwitchableRelation`](ImplSwitchableRelation.md) — standard `SwitchableRelation` **mixin** (held as a field and forwarded to, not subclassed); drives a sealed `IndependentBool isEnabled` from suppressor count AND `shouldBeEnabled`.

## Equality coupling
- [`CoupleEqual`](CoupleEqual.md) — keep two reactive values equal (two-way), copying each into the other on change via a `Nonreentrant`-guarded listener. `Mode` selects the initial tie-break / one-way variants.
- [`SwitchableCoupleEqual`](SwitchableCoupleEqual.md) — a `CoupleEqual` that can be switched on/off at runtime via a reactive boolean and `Suppressor`s.
- [`Equalizer`](Equalizer.md) — a `SealBool` tracking whether two values (`giver`/`receiver`) are equal; `set(true)` copies `giver`→`receiver` to make them so. **Binary & one-directional** (built via `make(...)`), *not* an N-ary mutual equalizer.

## Implication
- [`Implication`](Implication.md) — enforce a material implication `premise → conclusion` between two reactive booleans, writing back only to repair the one forbidden state (premise true, conclusion false).
- [`SwitchableImplication`](SwitchableImplication.md) — an `Implication` that can be switched on/off at runtime via a reactive boolean and `Suppressor`s.

## Boolean-group constraints
- [`BooleanGroup_Exactly1`](BooleanGroup_Exactly1.md) — constrain a group of booleans so exactly one is true (radio-button semantics): selecting one deselects the others, the group resists going empty (re-asserts a member, asynchronously, when the active one is cleared).
- [`BooleanGroup_Max1`](BooleanGroup_Max1.md) — at most one true (select forces others false; all-false allowed).
- [`BooleanGroup_Min1`](BooleanGroup_Min1.md) — at least one stays true (resists all-false once non-empty; multiple-true allowed).

## Integration
- [`MultiSourceIntegrator`](MultiSourceIntegrator.md) — integrate several writable source values into one target by folding them through a user-supplied `integrate` `BiFunction` (fold-all, not last-writer-wins), with optional write-back of the target into all sources.
