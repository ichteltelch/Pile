# `pile.aspect.RemembersLastValue`

The **aspect a value-holder implements when it can persist one previously-set value to an external store and restore it across program runs** (e.g. user settings) — while *programmatic* writes can be excluded from being remembered.

Source folder: `src`. File: `pile/aspect/RemembersLastValue.java`.

`RemembersLastValue` is the *behavior* aspect; it deliberately splits its concerns across two collaborators:
- [`LastValueRememberer<E>`](LastValueRememberer.md) — the **storage strategy** (where/how the value is saved and recalled); doc-pending.
- [`LastValueRememberSuppressible`](LastValueRememberSuppressible.md) — the **suppression** super-interface; `RemembersLastValue extends LastValueRememberSuppressible.Single`; doc-pending.

See the [overview](../../overview.md) for where this sits in the `pile.aspect` aspect family.

## What it is for

A value that "remembers its last value" wires up so that **user-driven changes are written through to a persistent store**, and on the next program run the value is **initialized from that store** rather than from a hard-coded default. The classic use is a GUI setting: the user picks a value, it survives a restart, but a programmatic/recomputed write does not overwrite the user's remembered choice.

This aspect declares only the *control surface*. The actual save/restore plumbing is assembled at construction time by the builder (see Lifecycle).

## Members (delta over javadoc)

All five are on the interface:

- `boolean remembersLastValue` — is remembering currently *active* (not suppressed)? The auto-store listener consults this as its gate.
- `void storeLastValueNow` — force the current value into the store right now.
- `void resetToLastValue` — overwrite the current value with what the store holds.
- `Suppressor suppressRememberLastValue` — open a suppression scope; while any returned `Suppressor` is unreleased, `remembersLastValue` is `false`. This is the **single canonical method**; the plural-named one delegates to it.
- `Suppressor suppressRememberLastValues` — `default`, delegates to `suppressRememberLastValue`. It exists only to satisfy the `LastValueRememberSuppressible` super-interface (whose method-handle constants are spelled in the plural).

## Override map

The aspect is implemented by the always-valid holders, and stubbed out by the recomputing/constant ones.

| Implementor | Source | Behavior |
|---|---|---|
| `pile.impl.Independent` | `Independent.java` | **Canonical implementation.** Counts unreleased suppressors in `storingSuppressors`; `remembersLastValue` ⇔ `storingSuppressors<=0`. `storeLastValueNow`/`resetToLastValue` reach the `LastValueRememberer` via the association key. |
| `Independent`'s `makeSetter` `WriteValue` | `Independent.java` | Delegates all four back to the owning `Independent`. |
| `pile.impl.SealPile` (and inner `Setter`) | `SealPile.java` | Delegates to the enclosing `SealPile`, which inherits `PileImpl`'s stubs. |
| `pile.impl.PileImpl` | `PileImpl.java` | **No-op stub:** `remembersLastValue` → `false`, `suppressRememberLastValue` → `Suppressor.NOP`. A recomputing `Pile` does not remember. |
| `pile.impl.Constant`, `pile.impl.MutRef` | `Constant.java`, `MutRef.java` | No-op (`Suppressor.NOP`). |
| `pile.interop.preferences.PreferencesBackedValue`, `SynchronizingFilesBackedValue` | `PreferencesBackedValue.java`, `SynchronizingFilesBackedValue.java` | Their own backing-store-aware implementations. |

The authoritative reference implementation is **`Independent`** — read it first.

## Remember / restore lifecycle

The aspect's methods are inert until a `LastValueRememberer` is attached. That happens in the builder, in `AbstractIndependentBuilder.build` when `fromStore(...)` was called:

1. **Restore at construction:** `value.set(remember.recallLastValue)` — the value starts from the stored value (or the rememberer's default if nothing was stored).
2. **Attach the strategy:** `value.putAssociation(LastValueRememberer.key, remember)` — the `LastValueRememberer` is stored as a `STRONG`-referenced association under the singleton `LastValueRememberer.KEY`. `storeLastValueNow`/`resetToLastValue` later look it up with `getAssociation(LastValueRememberer.key)` and no-op if absent.
3. **Auto-store on change:** a `ValueListener` is added:
   ```java
   value.addValueListener(e->{ if(value.remembersLastValue) remember.storeLastValue(value.get); });
   ```
   Every value change writes through to the store **unless remembering is currently suppressed**.

`resetToLastValue` is the reverse of step 1 done on demand: `set(rem.recallLastValue)`.

## User-driven vs programmatic sets

This is the crux of the aspect, and it is **convention-driven, not automatic**:

- The auto-store listener fires on *every* change and is gated solely by `remembersLastValue`.
- A **user-driven** `set(...)` runs with no suppressor active, so `remembersLastValue` is `true` → the new value is stored.
- A **programmatic** write that should *not* be remembered must be wrapped by the writer in a suppression scope:
  ```java
  try (… s = v.suppressRememberLastValue) { v.set(programmaticValue); } // s.release in finally
  ```
  While `s` is unreleased, `storingSuppressors>0` ⇒ `remembersLastValue` is `false` ⇒ the listener skips storing.

So "programmatic sets aren't remembered" is a contract the **caller upholds** by suppressing; the aspect just provides the counter and the gate. (Suppression is re-entrant: the counter supports nested scopes.) For batch suppression across many values, see [`LastValueRememberSuppressible`](LastValueRememberSuppressible.md), whose `SuppressMany` machinery and `SUPPRESS_LAST_VALUE_REMEMBERING*` method-handles collect suppressors from a whole collection at once.

## Preferences-backed use

The usual `LastValueRememberer` implementations come from `pile.interop.preferences.PrefInterop`, which bridges to `java.util.prefs.Preferences`:
- `PrefInterop.remember(...)` / `rememberBool/Int/Double/String/Enum(...)` build a `LastValueRememberer` whose `storeLastValue`/`recallLastValue` put/get a key in a `Preferences` node.
- A `NullBehavior` enum (`DELETE`/`IGNORE`/`STORE_DEFAULT`/`STORE_NULL`) controls what happens when `null` is stored; `STORE_NULL` is unsupported for primitive-typed preferences and throws (`PrefInterop.java`, and the per-type guards).

Pair such a rememberer with `IndependentBuilder.fromStore(rememberer, correctNulls)` to get a setting that loads on startup and persists on user change. (Note: `PrefInterop` also offers `*preference(...)` factories that build a `Preferences`-synced `Independent` directly via a value listener, **without** going through `RemembersLastValue` — a parallel mechanism, not this aspect.)

## Salient / surprising behavior

- **`remembersLastValue` is the only gate**, and it means "not suppressed" — not "has a rememberer". On a value built without `fromStore(...)` there is no association and no listener, so `storeLastValueNow`/`resetToLastValue` are silent no-ops even though `remembersLastValue` returns `true`.
- **Build-time `init(...)` is remembered.** In `build`, the auto-store listener is installed *before* an `init(...)` value is applied; since no suppressor is active there, an explicit `init` write is stored as if user-driven. The *restore* `set` at  precedes the listener, so it is not re-stored.
- **A recomputing `Pile` never remembers** — `PileImpl` hard-codes `false`/`NOP`. Only the always-valid `Independent` (and the preferences-backed holders) actually remember.
- **The store is written on the setting thread**, synchronously inside the value listener; for a `Preferences` backing this touches the prefs subsystem on every change.

## Caveats & gotchas

- Programmatic-vs-user separation is **not enforced**: forget the `suppressRememberLastValue` wrapper and your programmatic write *will* be persisted as the remembered value.
- Always release the `Suppressor` (try/finally) or remembering stays off forever for that value.
- The rememberer is a **`STRONG`** association; it keeps the strategy (and whatever it captures, e.g. a `Preferences` node) alive for the value's lifetime.
- `LastValueRememberer.storeLastValue(null)` behavior depends entirely on the strategy's `NullBehavior`; primitive `Preferences`-backed strategies reject `STORE_NULL`.

## Common tasks (how to…)

- **Make a persisted setting:** `Piles.independent(default).fromStore(PrefInterop.remember(node, key, default), correctNulls).build`.
- **Suppress remembering for a programmatic write:** `Suppressor s = v.suppressRememberLastValue; try { v.set(x); } finally { s.release; }`.
- **Suppress across many values at once:** use `LastValueRememberSuppressible`'s `SuppressMany` / `SUPPRESS_LAST_VALUE_REMEMBERING*` handles — see [`LastValueRememberSuppressible`](LastValueRememberSuppressible.md).
- **Force a save / reload:** `v.storeLastValueNow` / `v.resetToLastValue` (no-op if no rememberer was attached).
- **Check whether a change will be persisted:** `v.remembersLastValue`.

## Tech debt / warts

- The remember/not-remember split rides on caller discipline plus a counter; there is no protocol-level distinction between user and programmatic sets at the `set(...)` boundary.
- Two parallel `Preferences` integrations coexist (`PrefInterop`'s `*preference(...)` listeners vs. this aspect's `fromStore` + `LastValueRememberer`); choosing the wrong one is easy.
- Build-time `init(...)` being stored (above) is a subtle ordering dependency in `AbstractIndependentBuilder.build`.
- Javadoc `@return` tags on the interface are empty, consistent with the project-wide note that some API is unsystematic (see [overview § caveats](../../overview.md)).

## Related

- [`LastValueRememberer`](LastValueRememberer.md) — the storage strategy (doc-pending).
- [`LastValueRememberSuppressible`](LastValueRememberSuppressible.md) — batch/hierarchical suppression super-interface (doc-pending).
- [overview.md](../../overview.md) — architecture map.
