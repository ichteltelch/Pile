# `BooleanGroup_Max1`

Constrain a group of boolean reactive values so that **at most one** is true — selecting one deselects the others, but all-false is allowed.

Source folder: `src` (package `pile.relation`).

A near-twin of [`BooleanGroup_Exactly1`](BooleanGroup_Exactly1.md). Read that doc for the shared model; this page is a **delta** covering only what differs. See also the sibling [`BooleanGroup_Min1`](BooleanGroup_Min1.md), the package [index](_index.md), and the project [overview](../../overview.md).

## What it's for

Radio-button-style mutual exclusion **without** a forced selection: turning an item on turns the previously-active one off, but you may leave the whole group deselected. The empty / all-false state is a legal resting state — unlike `BooleanGroup_Exactly1`, which always drives a replacement true to keep exactly one selected.

Like its siblings this is a plain helper object, **not** a subclass of `AbstractRelation` (no [`AbstractRelation`](AbstractRelation.md) wiring, no on/off switchability). It is **not thread-safe** (per the class javadoc).

## The delta vs `BooleanGroup_Exactly1`

| | `BooleanGroup_Max1` | `BooleanGroup_Exactly1` |
|---|---|---|
| all-false allowed | **yes** | no — forces a replacement true |
| replacement map / `setReplacement` | absent | present |
| `add(elem, repl)` overload | absent | present |
| async write-back via `StandardExecutors` | none — writes are synchronous in the listener | yes (`fr.set(true)` deferred) |
| `remove` of active item | just clears `active` | clears `active`, may set a replacement true |

Because nothing has to be re-selected, `Max1` never needs the `replacements` map nor the deferred executor write that `Exactly1` uses; all its mutation happens **synchronously inside the listener**.

## Behavior to know

- **Set-true forces the others false.** When a member's listener (in `add`) sees the member became `TRUE` and it isn't already `active`, it sets the previously-`active` member to `false` and adopts the new one. Note the ordering in `add`: `active` is reassigned to the new item *before* the old item is set false (`active.set(false)` then `active=elem`). The old item's own listener then fires for that false write, sees it is no longer `active`, and does nothing — so the deselect doesn't cascade.

- **Set-false is fine.** When the active member goes `FALSE`, its listener simply clears `active` to `null`. No other member is promoted; the group is now empty and stays that way until something is set true again. (Contrast `Exactly1`, which would drive a replacement true.) Setting a *non*-active member false is a no-op.

- **Initial multi-true resolution.** Each `add` ends with `cl.runImmediately(true)`, running the just-registered listener once against the member's current value. So if you add several members that are already `true`, each one in turn becomes `active` and sets the prior `active` to false — the **last-added true member wins** and the rest are forced false. Construction (varargs / `Iterable`) simply calls `add` per member, inheriting this.

- **The guard.** The body acts only when the changed member's value differs from the current `active` pointer (`if(elem!=active)` on a true write; `if(elem==active)` on a false write). This is what stops the write-back from re-triggering endlessly: the `active.set(false)` that `add`'s listener performs re-enters the listener for the old item, but by then `active` already points at the new item, so `elem==active` is false and the branch is skipped. This is the equivalent of the `Nonreentrant`-style loop-breaking the package index mentions — here done by hand via the `active` pointer rather than a dedicated guard object.

## Key methods (by purpose)

- `add(elem)` — register a member and its listener; runs the listener immediately so a currently-true member becomes (or contends for) `active`. Also fires the `afterChange` callback on first registration of a *new* member (see warts).
- `remove(elem)` — unregister; if it was the active one, `active` becomes `null` (no replacement). The remaining group keeps its at-most-one invariant.
- `deselectAll()` — set the active member false (if any). Leaves the group empty — which for `Max1` is a valid state, unlike `Exactly1`.
- `getSelected()` — the currently-true member, or `null` if none. The name's "supposedly only" caveat in the javadoc applies: it returns the tracked `active` pointer, not a fresh scan.
- `clear()` / `destroy()` — remove all members and detach all listeners; `destroy()` just calls `clear()`.
- `afterChange(callback)` — set a `Runnable` run after a member's `ValueEvent` is handled; returns `this` (fluent).

## Caveats & gotchas

- **Not thread-safe**; mutates plain fields and writes back into members from within listener callbacks.
- **`getSelected()` trusts the `active` pointer.** If members are mutated by paths that bypass these listeners, or in pathological re-entrancy, the pointer could lag reality. Under normal use it stays consistent.
- **Idiomatic no-ops** (not bugs): setting an already-false / non-active member false does nothing; adding the same member twice re-runs only `cl.runImmediately` without re-registering.

## Tech debt / warts

- **`afterChange` callback fires inconsistently** compared to the sibling. In `BooleanGroup_Max1.add`, `callback.run()` lives inside the `if(cl==null)` *registration* branch — it runs **once at add time** (when a new member is added while a callback is set), **not** on every subsequent `ValueEvent`. The listener lambda itself contains **no** `callback` call, so ordinary value changes never invoke it. The javadoc on `afterChange` ("invoked when a `ValueEvent` happens on one of the group's items … run after the `ValueEvent` has been handled") describes the `Exactly1` behavior, where the call sits in the listener lambda. In `Max1` the callback is effectively dead for change events. See `SUSPECTED_BUGS`.
- Shared with the family: helper-object design (no `AbstractRelation` base, no switchability); manual pointer-based loop-breaking instead of a reusable guard.
