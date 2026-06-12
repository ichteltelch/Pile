# `BooleanGroup_Min1`

Constrain a group of boolean reactive values so that **at least one** stays true — the group resists going all-false, while any number may be true at once.

Source folder: `src`. Package [`pile.relation`](_index.md). Up: [overview](../../overview.md). Siblings: [`BooleanGroup_Exactly1`](BooleanGroup_Exactly1.md) (radio-button: exactly one), [`BooleanGroup_Max1`](BooleanGroup_Max1.md) (at most one).

Note: unlike most relations in this package, `BooleanGroup_Min1` does **not** extend `AbstractRelation` — it is a standalone class that wires its own `ValueListener` onto each member. Not thread-safe (per its javadoc).

## What it does

Holds members in an `IdentityHashMap` (`items`, member → its listener) and tracks which members are currently true in a `HashSet` (`active`). Each member gets a `ValueListener` (registered in `add`) that maintains `active` and enforces the min-1 invariant.

## The guard — what happens when the last true member is set false

In the listener installed by `add`: when a member's event fires and the member is now false, it is removed from `active`; **then `if(active.isEmpty())`** the group re-asserts a true. Which one depends on `invertWhenLastIsDeselected` (default false, set via `setInvertWhenLastDeselected`):

- **default (`false`)**: the **same member that was just deselected** is set back to `true` (`elem.set(true)`). The group snaps back — you cannot turn off the last remaining true member; it bounces.
- **`true` and `items.size()>=2`**: instead, **every *other* member is set to `true`** (the loop over `items.keySet()` skipping `elem`), inverting the pattern — the last one stays false and the rest come on. With fewer than 2 members the `>=2` check falls through to the default same-member re-assert.

The re-assert is itself a `set(true)`, which fires the listener again and re-adds that member to `active`; the recursion terminates because `active` is then non-empty.

## Multi-true is allowed

`active` is a plain `Set` and nothing ever forces members false. Several members may be true simultaneously; the relation only acts when `active` would become empty. (Contrast `BooleanGroup_Max1` / `BooleanGroup_Exactly1`.)

## Initial all-false is NOT resolved

The invariant is enforced **only reactively**, inside the listener, in response to a deselection event that empties `active`. Neither the constructors nor `add` force a true when the group starts (or is built) all-false. So **a group constructed from all-false members stays all-false** until some member receives a value event that empties an already-non-empty `active`. The class only *resists* going all-false from a true state; it does not *establish* the invariant on its own. If you need a guaranteed-true start, set one member true yourself after building the group.

## Members & lifecycle

- Constructors: no-arg; varargs `ReadWriteListenValue<Boolean>...`; `Iterable<...>`. The collection forms call `add` per member.
- `add(elem)` — registers the listener **only if the member is new** (`items.get(elem)==null`); for an already-present member it skips re-registration. In **both** branches it ends by adding `elem` to `active` if the member is currently true, syncing initial state. (Gotcha: see warts below — the `active.add` placement.)
- `remove(elem)` — drops it from `active` and unregisters its listener. Per its javadoc, if the removed member was the only true one the group is then left with no active item until something else is set true (removal does **not** trigger the re-assert guard — that guard lives only in the listener).
- `clear()` / `destroy()` (`destroy` just calls `clear`) — unregister all listeners and empty both maps.
- `afterChange(Runnable)` — sets `callback`, run **after** each handled member event (including after a re-assert). Fluent.

## Caveats & gotchas

- **No initial-invariant enforcement** (see above) — all-false-in stays all-false.
- **Multi-true permitted** — this is min-1, not exactly-1; do not assume a single selection.
- **The bounce**: with the default flag, attempting to deselect the last true member silently re-selects it (a `set(true)` echo). This is intended min-1 behaviour, not a glitch — but a UI driving the member sees its write reverted.
- **No feedback-loop breaker**: unlike the `CoupleEqual` / `Implication` relations (which use [`Nonreentrant`](../utils/Nonreentrant.md)), this class relies purely on `active` being non-empty after the re-assert to stop recursion. Safe for the min-1 logic, but be wary if members have unusual `set` semantics.
- **`add` on an already-present member** re-runs the trailing `active.add` but does not re-add a listener — harmless, but the early-return-less structure makes it easy to misread.
- Not thread-safe.

## Warts / tech debt

- In `add`, the trailing `if(Boolean.TRUE.equals(elem.get())) active.add(elem);` sits **outside** the `if(cl==null)` block, so it runs on every `add` call, while listener registration runs only for new members. Re-adding an existing member thus re-syncs `active` without re-registering — most likely harmless but the asymmetry is a smell.
- The `set(true)` re-assert path for `invertWhenLastIsDeselected` sets **all** other members true on every last-deselection, which can be a heavier side effect than a UI expects (it does not restore a *previous* pattern, it turns everything else on).
