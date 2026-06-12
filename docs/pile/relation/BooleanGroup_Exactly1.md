# `BooleanGroup_Exactly1`

Constrain a group of boolean reactive values so that exactly one is true at a time (radio-button semantics): selecting one deselects the others, and the group resists going empty.

Source folder: `src`. Package `pile.relation`.

Up: [overview](../../overview.md) · [relation index](_index.md). Siblings: [`BooleanGroup_Max1`](BooleanGroup_Max1.md) (at-most-one), [`BooleanGroup_Min1`](BooleanGroup_Min1.md) (at-least-one).

## What it's for

Watches a set of `ReadWriteListenValue<Boolean>` members. Whenever a member becomes `true` it forces every other member to `false`; whenever the currently-active member becomes `false` it re-asserts a member to `true` so the group does not go empty. It combines the deselect-others behavior of `BooleanGroup_Max1` with the never-empty behavior of `BooleanGroup_Min1` (hence "exactly one").

Note: this is a **plain class, not an `AbstractRelation`** — despite living in `pile.relation` it does not extend the relation base class, has no switchability, and registers/removes raw `ValueListener`s itself. The state is a single `active` reference (the supposedly-true member) plus the `items` and `replacements` maps.

## Key members by purpose

- `add(elem)` / `add(elem, repl)` — register a member (the second form also sets its replacement via `setReplacement`). Wires a per-member `ValueListener` into `items` and, crucially, calls `cl.runImmediately(true)` so the constraint is applied to the member's *current* value at add time.
- `setReplacement(elem, repl)` — declare which member should be re-asserted true if `elem` was the sole active member and gets deselected. `elem==repl` clears the replacement. The replacement is only honored if it is itself a registered member (`items.containsKey`).
- `remove(elem)` — unregister a member and its listener. If `elem` was active, re-assert its replacement (if any, and still a member).
- `deselectAll()` — set the active member to `false` *without* triggering re-assertion (it nulls `active` first, so the listener does not re-select).
- `getSelected()` — returns `active`, the supposedly-only true member, or `null`.
- `clear()` / `destroy()` — remove all members and their listeners; `active=null`. `destroy()` just calls `clear()`.
- `afterChange(callback)` — fluent setter for a `Runnable` run after each member `ValueEvent` is handled (see the callback gotcha below).

## Salient behavior

This is the delta worth knowing beyond the javadoc; per-method contracts are in the source javadoc.

### Selecting a member (set true)
The member's listener checks `Boolean.TRUE.equals(elem.get())`. If `elem` is not already `active`, it sets `active=elem` first, then sets the previously-active member to `false`. Setting the old one false re-enters its listener, but by then `active` already points at the new member, so the old member's `elem==a` branch does not fire a re-assertion — that identity check (`elem==a` in the false-branch) is the **re-entrancy guard**. There is no `Nonreentrant` / `Suppressor` here; correctness rests entirely on updating `active` before writing back, plus the deferral described next.

### Deselecting the active member (set false)
When the active member goes `false`, the listener sets `active=null` and schedules a re-assertion: it picks the replacement `replacements.get(elem)`; if that is absent or no longer a member, it falls back to `a` (the member being deselected itself — i.e. it re-asserts the very member you just turned off). The chosen member is set `true` **asynchronously** via `StandardExecutors.unlimited().execute(...)`, not inline. This deferral is what lets the current event finish before the group re-fills, avoiding a write-back recursion within the same event.

Consequence: the group is **transiently empty** between the deselect and the deferred re-assertion. `getSelected()` returns `null` in that window, and the re-assertion happens on whatever thread `StandardExecutors.unlimited()` runs it on — so the bounce-back is not synchronous with the caller's `set(false)`. By default (no replacement set) deselecting the active member simply **snaps it back to `true`**, so a lone radio button cannot be turned off.

### Initial state at add time
`add` applies the listener immediately to the member's current value:
- A member added while already `true` becomes the new `active`, forcing any prior active member false (last-true-added wins).
- A member added while `false` does nothing special.

There is **no normalization pass**: if you construct/populate a group whose members are all `false`, the group stays empty until something is set true — the never-empty invariant is only enforced *reactively*, when an active member is later deselected. Likewise if two members are somehow `true` after adds, the later add will have deselected the earlier, so the final state is single — but only because each add ran its listener.

### Removing the active member
`remove` nulls `active` if it was the removed member, unregisters the listener, then (if a still-registered replacement exists) re-asserts that replacement true asynchronously. If there is no valid replacement, the group is left **empty** — unlike deselection, removal does not bounce back to the removed member (it is gone). The doc/javadoc says "without an active item until one of the remaining items is set to true."

## Caveats & gotchas

- **Transiently empty + async re-fill.** After deselecting the active member, `getSelected()` is briefly `null` and the re-assertion runs on `StandardExecutors.unlimited()`, possibly on another thread. Do not assume the group is non-empty synchronously after a `set(false)`.
- **`deselectAll()` really empties the group** and does *not* re-assert — it deliberately nulls `active` before the write so the listener's re-assertion branch is skipped. This is the one supported way to leave an Exactly1 group with nothing selected.
- **Default replacement = self.** With no `setReplacement`, deselecting the active member re-selects that same member. A member with no replacement effectively cannot be turned off by itself.
- **Replacement must be an added member.** `setReplacement` does not add the replacement; an unregistered replacement is ignored and the fallback applies (`!items.containsKey(r)` check in the listener, and the analogous check in `remove`).
- **Not thread-safe** (stated in the javadoc). The async re-assertion makes this sharper: the executor task touches the same `active`/`items` state without synchronization.
- **No automatic invariant repair on bulk setup.** All-false or pre-seeded states are not normalized; the "exactly one" guarantee only holds once the reactive flow has run (each add runs its listener, but a group never driven true stays empty).
- **`callback` timing differs from the siblings.** Here `afterChange`'s callback runs *inside* the per-member listener after each event is handled (both the true and false branches reach it). `BooleanGroup_Max1` instead runs the callback only inside `add` when first registering a listener — a likely inconsistency between the siblings (see Tech debt).

## Common tasks

- **Make radio buttons from N booleans:** `new BooleanGroup_Exactly1(b1, b2, b3)` (varargs or `Iterable`). Set whichever should start selected to `true` before or after construction.
- **Choose what gets selected when the current one is turned off:** `group.add(elem, replacement)` or `group.setReplacement(elem, replacement)` (replacement must also be added).
- **Allow a momentarily-empty group:** call `deselectAll()` (the only path that leaves it empty without bounce-back).
- **Tear down:** `clear()` or `destroy()` removes all listeners.

## Tech debt / warts

- **Inconsistent `afterChange` semantics across the three `BooleanGroup_*` classes** — `Exactly1` and `Min1` run the callback per event inside the listener; `Max1` runs it once at registration time. See Suspected bugs in the report.
- **No shared base class.** The three boolean-group classes duplicate the `items` map, `add`/`remove`/`clear`/`destroy`/`getSelected`/`afterChange` scaffolding by copy-paste rather than extending `AbstractRelation` or a common boolean-group base.
- **Async re-assertion via `StandardExecutors.unlimited()`** trades synchronous predictability (and the never-empty guarantee at the call site) for re-entrancy safety; `Max1` is fully synchronous. A reader expecting the group to be non-empty right after `set(false)` will be surprised.
</content>
</invoke>
