# `pile.builder.IIndependentBuilder`

The fluent builder API for [`Independent`](../impl/Independent.md) leaf values: initial value, remember-last-value (`fromStore`), correctors/bounds, brackets, coupling/following, sealing, and the valid-buffer setups.

Source folder: `src`. File: `pile/builder/IIndependentBuilder.java`.

`IIndependentBuilder<Self, V, E>` is the `Independent`-specific tier of the builder hierarchy. It adds the entry points unique to a non-recomputing leaf cell (initial value, `fromStore`, debug/parent/coupling) on top of the capability interfaces it inherits. You normally obtain a concrete `IndependentBuilder` from a [`Piles`](../impl/Piles/_index.md) factory and finish with `.build`.

Up: [builder index](_index.md) · [overview](../../overview.md). The value it builds: [`Independent`](../impl/Independent.md).

## What it's for

This interface is the *fluent API surface only* — it declares the configuration methods and returns the right `Self` type so chaining stays type-safe. The actual `build` wiring (and the remember-last-value save/restore plumbing) lives in `AbstractIndependentBuilder`; the concrete `IndependentBuilder` is thin. Configuration methods are recorded on the builder and applied **at `build` time**, not eagerly — with two deliberate exceptions (`initNow`, the buffer `setup*` methods) noted below.

## Capability-interface inheritance

`IIndependentBuilder` is an assembly of four interfaces:

| Inherited from | Brings |
|---|---|
| [`ICorrigibleBuilder`](_index.md) | correctors/bounds: `corrector`, `neverNull`, `bounds`/`upperBound`/`lowerBound`, `ordering`, `equivalence` |
| `IListenValueBuilder` | listener config: `onChange`, `deferListeners` |
| [`ISealableBuilder`](_index.md) | sealing: `seal(...)` overloads, `makeSetter`, `giveSetter`, `sealWithSetter` |
| `IBuilder` (root, via the above) | `self`, `build`, `valueBeingBuilt`, `configure`, `runIfWeak`, `name`/owner config |

So an `Independent` builder is simultaneously a corrigible builder, a listen builder, and a sealable builder — the same fluent chain can clamp, listen, and seal. (Doc-pending interfaces are listed in [`_index.md`](_index.md).)

This doc is a **delta over the javadoc**; the per-method contracts live in the source. Below is what the javadoc doesn't say.

## Initial value — `init` vs `initNow` (the key gotcha)

There are two ways to set a starting value, and they differ in *when* the underlying `Independent#set` runs:

- **`init(initValue)`** — records the value; the `set` is applied **late, in `build`**, *after* brackets, correctors, and the remember-last-value listener are installed. Prefer this: brackets and corrections take effect on the initial value.
- **`initNow(initValue)`** — calls `Independent#set` **immediately**. Any brackets/correctors **not yet added** at that point will not apply to this value. Use only when you specifically need the value set before further configuration.

Note that an `Independent` always has an initial value anyway (its constructor takes a mandatory one); `init`/`initNow` overwrite it. See [`Independent`](../impl/Independent.md).

**Build-time ordering caveat:** in `build` the order is bounds → remember-listener → `init` → seal. Because the auto-store listener is installed *before* the `init` write and no suppressor is active there, **an `init(...)` value is stored to the rememberer as if user-driven** (the restore `set` precedes the listener, so it is not re-stored). This subtlety is documented at [`RemembersLastValue` § build-time init is remembered](../aspect/RemembersLastValue.md).

## `fromStore` / remember-last-value

`fromStore(remember, correctNulls)` is the entry point for a value backed by a persistent store (the classic "GUI setting that survives a restart"):

- It records the [`LastValueRememberer`](_index.md) strategy. At `build` time this triggers the full save/restore wiring in `AbstractIndependentBuilder.build`: restore-at-construction (`value.set(remember.recallLastValue)`), attach the strategy as a `STRONG` association, and add the auto-store `ValueListener` gated on `value.remembersLastValue`. The lifecycle is documented in full at [`RemembersLastValue` § Remember/restore lifecycle](../aspect/RemembersLastValue.md) — read that for the contract.
- **`correctNulls`** does *not* live in `build`: `fromStore` installs a corrector immediately when called, `v -> v==null ? remember.recallLastValue : v`. So when `correctNulls` is true, **any** write of `null` (not just at construction) is replaced by the recalled last value — it is a normalize-corrector, ordered relative to other correctors by call order. See [`CorrigibleValue`](../aspect/CorrigibleValue.md) for corrector composition.

The programmatic-vs-user-driven distinction (suppress remembering for programmatic writes via `suppressRememberLastValue`) is convention-driven and described under [`RemembersLastValue`](../aspect/RemembersLastValue.md).

## Other entry points by purpose

- **Coupling / following** — `equalTo(partner)` / `equalFrom(partner)` install a [`CoupleEqual`](../../concepts/) two-way equality relation; they differ only in **which side's value seeds the initial value** (`equalTo` → the value being built; `equalFrom` → the partner). `follow(leader)` is one-way: the built value tracks the leader via a `ValueListener` (`Independent#follow` adds it *weakly*, see [`Independent`](../impl/Independent.md)).
- **Brackets** — `bracket` / `oldValueBracket` / `anyBracket`, each with an `openNow` overload (the no-arg form defaults `openNow=false`, ). `inheritBrackets(openNow, template)` copies brackets from a template **only if** it is an `AbstractReadListenDependency` (silently no-ops otherwise, ).
- **Metadata / structure** — `debug(dc)`, `parent(p)` sets `Independent#owner`, `dontDependOnBounds` checks bounds on each change instead of adding listeners to them (see the `dob` branch in `build`, `AbstractIndependentBuilder.java`).
- **Wiring into a graph** — `depender(dep)` adds the value being built as a `Dependency` to a `Depender`.
- **Finish** — `build` installs correctors/bound-listeners, runs the remember/init/seal sequence, and returns the `Independent`.

## Valid-buffer setups (`setupValidBuffer` / `setupWritableValidBuffer`)

Three `default` methods turn the value being built into an always-valid mirror of a *leader* value that may go invalid — a "valid buffer" that holds the **last valid** value of the leader:

- **`setupValidBuffer(leader)`** — read-only buffer. A weak-referenced `ValueListener` copies the leader's last valid value into the buffer; the buffer pins the leader in its `owner` field (unless already set). Returns `seal` — the buffer is sealed read-only.
- **`setupWritableValidBuffer(leader[, deferWrites])`** — read-write buffer. Same mirroring, plus a seal **interceptor** so writes to the buffer flow to the leader first, then the buffer. `deferWrites` optionally wraps the leader's setter to defer/redirect writes (e.g. to another thread).

These run their wiring **eagerly when called** (not at `build`): they read `valueBeingBuilt`, attach listeners, set the value, copy equivalence/correctors from the leader, and return a `seal(...)` call. Treat them as terminal-ish configuration. Buffer setups are also hosted (for `SealPile`) on `ISealPileBuilder` per [`_index.md`](_index.md).

**Known wart in the code:** `setupValidBuffer` carries a `//TODO: Why does the buffer sometimes seem to fail to update?` and a commented-out `leader.validity.addValueListener(cl); //FIX?` — the author suspects the buffer can miss updates when the leader's *validity* (not value) changes. See SUSPECTED_BUGS.

## Salient / surprising behavior

- **Most config is deferred to `build`; `initNow`, `fromStore`'s `correctNulls` corrector, and the `setup*` buffers are eager.** Mixing eager and deferred operations means call order can matter (a `corrector` added after `initNow` won't see the `initNow` value).
- **`equalTo` vs `equalFrom` differ only in seed direction** — easy to pick the wrong one.
- **`inheritBrackets` silently no-ops** for any template that isn't an `AbstractReadListenDependency`.
- **Buffer setters are held weakly**; the buffer relies on its `owner` strong-ref to the leader to stay alive. Drop the leader and the buffer stops updating.

## Caveats & gotchas

- Use `init`, not `initNow`, unless you have a specific reason — `initNow` bypasses not-yet-installed brackets/correctors.
- A `fromStore(..., true)` value silently turns every `null` write into the stored value for the value's whole life (it is a permanent corrector), not just at startup.
- `setupValidBuffer`/`setupWritableValidBuffer` seal the value as part of their setup — you cannot continue configuring afterward the way you can with plain `seal` (which only takes effect at `build`).
- The `setupWritableValidBuffer` write interceptor's logic is subtle (identity-compares old vs new, defers a follower write until the leader is valid, ); don't assume a buffer write lands synchronously.

## Common tasks (how to…)

- **Settable leaf with an initial value:** `Piles.independent(x)…build` or `…init(x).build`.
- **Persisted setting:** `…fromStore(PrefInterop.remember(node, key, dflt), correctNulls).build` — see [`RemembersLastValue`](../aspect/RemembersLastValue.md).
- **Clamp / reject null:** `.bounds(lo, hi)` / `.neverNull` from [`ICorrigibleBuilder`](_index.md); see [`CorrigibleValue`](../aspect/CorrigibleValue.md).
- **Read-only mirror of a sometimes-invalid value:** `…setupValidBuffer(leader)` (returns the sealed buffer).
- **Editable mirror that writes through:** `…setupWritableValidBuffer(leader)`.
- **Two-way equality coupling:** `.equalTo(partner)` (seed from self) or `.equalFrom(partner)` (seed from partner).
- **Make read-only after setup:** grab `makeSetter`/`giveSetter(...)` then `.seal` — see [`ISealableBuilder`](_index.md).

## Tech debt / warts

- **`setupValidBuffer` update reliability is unresolved** ( TODO,  commented-out validity listener) — the author flags possible missed updates.
- **Eager-vs-deferred inconsistency**: most builder methods defer to `build`, but `initNow`, `correctNulls`, and the `setup*` buffers act immediately, so call order is load-bearing in ways the fluent API doesn't signal.
- **Build-time `init` is remembered** (ordering dependency in `AbstractIndependentBuilder.build`,  before ) — see [`RemembersLastValue`](../aspect/RemembersLastValue.md).
- The two-arg buffer `setup*` methods carry stale `@param <V>`/`@param <E>` javadoc tags that don't correspond to type parameters on the method.

## Related

- [`Independent`](../impl/Independent.md) — the value built (correctors, sealing, remember-last-value canonical impl).
- Aspects: [`RemembersLastValue`](../aspect/RemembersLastValue.md) · [`CorrigibleValue`](../aspect/CorrigibleValue.md).
- [builder index](_index.md) · [overview](../../overview.md). Recompute/transaction model: [concepts](../../concepts/).
