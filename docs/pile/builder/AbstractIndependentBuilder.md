# `pile.builder.AbstractIndependentBuilder`

The abstract base that implements the `Independent` `build` logic — it assembles correctors/bounds, the **remember-last-value save/restore wiring**, init, and sealing onto a pre-created `Independent`; this is the collaborator the [`Independent`](../impl/Independent.md) and [`RemembersLastValue`](../aspect/RemembersLastValue.md) docs deferred to.

Source folder: `src`. File: `pile/builder/AbstractIndependentBuilder.java`.

`AbstractIndependentBuilder<Self, V extends Independent<E>, E>` implements `IIndependentBuilder` and is the only non-trivial layer between the fluent `IndependentBuilder` API and a finished `Independent`. Unlike `AbstractPileBuilder` it does **not** create the value: the value is passed into the constructor (already unsealed) and every configuration method mutates *that* live object, then `build` does the ordering-sensitive final wiring. Self-type recursion (`Self`) forces the class to be abstract.

Up: [builder index](_index.md) · [overview](../../overview.md). See also [`Independent`](../impl/Independent.md), [`RemembersLastValue`](../aspect/RemembersLastValue.md), [`CorrigibleValue`](../aspect/CorrigibleValue.md).

## What it's for

Take an `Independent<E>` that callers obtained empty, let them stack configuration fluently (correctors, bounds, brackets, listeners, follow/couple relations, remember-store, init, seal), and finalize it in one `build` pass whose **ordering matters**. Most setters apply their effect *immediately* to the live value (e.g. `corrector`, `onChange`, `bracket`, `parent`); only bounds, remember-last-value, init, and seal are deferred to `build` because their order relative to each other is significant.

## The build pass — what happens, in order

`build` operates on the live `value` and does, strictly in this sequence:

1. **Bounds correctors** — `ICorrigibleBuilder.applyBounds(value, lowerBounds, upperBounds, ordering)` compiles the collected bounds into `_addCorrector` clamps and stores the upper/lower-bound associations. Multiple bounds are aggregated via min/max monoids; an upper+lower pair also installs a consistency veto. Requires an `ordering` if any bound is present, else throws (see [`ICorrigibleBuilder`](_index.md), `applyBounds`).
2. **Depend-on-bounds re-clamp listener** — if `dontDependOnBounds` was *not* called (`dob` defaults true) and at least one bound exists, attach a **weak** value listener to each bound dependency so that when a bound changes, the value is re-set through itself (`value.set(value.get)`) to re-trigger the clamp correctors. This is how a bounded `Independent` stays inside *moving* bounds. (See the bug note below — this block is partly broken.)
3. **Remember-last-value save/restore wiring** — only if `fromStore(...)` set `remember`:
   - **Restore at construction:** `value.set(remember.recallLastValue)` — value starts from the stored value (or the rememberer's default).
   - **Attach strategy:** `value.putAssociation(LastValueRememberer.key, remember)` — strong association under the singleton key; `storeLastValueNow`/`resetToLastValue` later look it up.
   - **Auto-store listener:** `value.addValueListener(e -> { if(value.remembersLastValue) remember.storeLastValue(value.get); })` — every change writes through to the store unless remembering is currently suppressed.
4. **Init** — if `init(...)` was called, `value.set(init)`. This runs **after** the restore `set` and **after** the auto-store listener is installed.
5. **Seal** — if `seal`/`seal(interceptor[, …])` was called, `value.seal(interceptor, false)`.

Then returns the same `value`.

### init vs. the restore set (the ordering that bites)

The two writes interact through their relative position to the auto-store listener:

- The **restore** `set` at  happens *before* the listener is added, so restoring the stored value does **not** re-store it (no feedback loop).
- The **init** `set` at  happens *after* the listener is added, with no suppressor active, so `init(...)` **overwrites the restored value and is itself persisted** as if a user typed it. If you use `fromStore(...)`, an `init(...)` defeats the restore. Use `init(...)` only when you have not wired a store, or when you deliberately want a fixed startup value persisted. (`initNow(...)` is different — it writes the live value *immediately* at call time, , so relative to a later `build` its effect is overwritten by both restore and init.) This subtle ordering dependency is also flagged in [`RemembersLastValue`](../aspect/RemembersLastValue.md).

### seal application

Sealing is always **`allowInvalidation=false`** regardless of which `seal(...)` overload was used — all three overloads merely record `sealOnBuild=true` and the `interceptor`; the `allowInvalidation` argument of `seal(interceptor, allowInvalidation)` is **ignored**, and `build` hard-codes `false`. For an `Independent` that is harmless (it can never be invalid anyway), but the parameter is dead. Because seal is last, all correctors/bounds/brackets/init are in place before the structure freezes.

## Configuration methods (delta over javadoc)

Most setters are thin and apply immediately; the interesting ones:

- `fromStore(remember, correctNulls)` — records the rememberer for the build-pass wiring; if `correctNulls`, **also installs a corrector right now** that replaces a `null` write with `remember.recallLastValue`. So null-correction uses the rememberer's *recall* (current stored value), not a fixed default.
- `corrector` / `equivalence` / `bracket` / `anyBracket` / `oldValueBracket` / `deferListeners` — delegate straight to the live value's `_addCorrector`/`_setEquivalence`/`_add*Bracket`/`_setDeferringListeners` immediately. Adding these after a value would be sealed throws (the value isn't sealed until build, so during configuration they succeed).
- `ordering(comp)` vs `orderingRaw(comp)` — `ordering` wraps `comp` so that a `null` operand throws `VetoException` (unless both null → equal); `orderingRaw` uses the comparator as-is (caller handles nulls).
- `onChange(l)` adds a value listener immediately; `equalFrom`/`equalTo` install a `CoupleEqual` relation kept strong; `follow(leader)` adds a weak follow.
- `dontDependOnBounds` flips `dob=false` to suppress the build-pass re-clamp listener (step 2).
- `name`, `parent`, `debug`, `valueBeingBuilt`, `makeSetter` — trivial pass-throughs to the live value.

## SUPERDOC verification

The two docs that described this wiring without reading it check out:

- **`Independent.md` DEFER note** ("save/restore wiring is assembled in `AbstractIndependentBuilder.build`: `LastValueRememberer` association, restore-at-construction, auto-store value listener") — **confirmed** against . No conflict.
- **`RemembersLastValue.md` § Remember/restore lifecycle** — its three steps (restore `set` , attach association , auto-store listener ) and its line citations are **all correct**. Its § Salient behavior claim "Build-time `init(...)` is remembered … installed before an `init(...)` value is applied" is **confirmed** by the  (listener) →  (init) ordering, and "the restore `set` precedes the listener, so it is not re-stored" is **confirmed** by  < . No conflicts to reconcile — both superdocs are accurate.

## Salient / surprising behavior

- **The value is not created here.** It is handed in via the constructor, which rejects an already-sealed value. So every `value.*` call assumes an unsealed, live `Independent` until step 5 of `build`.
- **Most configuration is eager, not deferred.** Correctors, brackets, listeners, relations, equivalence, parent, name, deferListeners take effect the instant you call them — not at `build`. Only bounds, remember, init, seal wait for `build`. Calling `build` twice would re-run the deferred wiring on the same value (double restore/init/seal-attempt) — build once.
- **`fromStore` + `init` conflict** (see above) — init wins and is persisted.
- **`seal(...)`'s `allowInvalidation` is ignored** — build always seals with `false`.

## Caveats & gotchas

- Don't combine `fromStore(...)` with `init(...)` unless you intend the init value to overwrite and re-persist the restored value.
- A bounded value with **variable** bounds needs the depend-on-bounds listener (default on) to track moving bounds; calling `dontDependOnBounds` freezes clamping to the bounds' values at write time only.
- The bounds re-clamp listeners are **weak** — they are kept alive by the bound dependencies, not by the value; this matches the rest of Pile's weak-listener convention.
- `build` returns the *same* object you could already reach via `valueBeingBuilt` — `build` is the finalize step, not a factory.

## Tech debt / warts

- **Broken default-sealed guard in the re-clamp block** — see SUSPECTED_BUGS. The `if(value.isDefaultSealed) vl=null;` is immediately overwritten by an unconditional `vl = e->value.set(value.get);`, so the `vl==null` filter at  is dead for that branch.
- **Dead seal branch in the same block** — at build time the value can never be sealed (the constructor forbids a sealed value  and sealing happens later at ), so the entire `if(value.isSealed)` arm is unreachable in normal flow; only the `else` (unsealed) arm runs.
- **`seal(interceptor, allowInvalidation)`'s second argument is dead** (, hard-coded `false` at ).
- **`init`-after-restore ordering** is a subtle, undocumented-in-code dependency (callers must know not to mix `fromStore` and `init`).

## Related

- [`Independent`](../impl/Independent.md) — the value this builder finalizes (canonical remember-last-value / corrector / bracket impl).
- [`RemembersLastValue`](../aspect/RemembersLastValue.md) · [`LastValueRememberer`](../aspect/LastValueRememberer.md) — the aspect and storage strategy the save/restore wiring uses.
- [`CorrigibleValue`](../aspect/CorrigibleValue.md) — corrector/bounds semantics applied here.
- [builder index](_index.md) · [overview](../../overview.md) · concepts: [`../../concepts/`](../../concepts/).
