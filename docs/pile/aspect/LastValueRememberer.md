# `pile.aspect.LastValueRememberer`

The **storage strategy that stores and recalls a value-holder's remembered "last value"** (e.g. into `Preferences`) — the pluggable collaborator behind [`RemembersLastValue`](RemembersLastValue.md).

Source folder: `src`. File: `pile/aspect/LastValueRememberer.java`.

`LastValueRememberer<E>` is the *where/how* half of the remember-last-value feature; [`RemembersLastValue`](RemembersLastValue.md) is the *when/whether* half. A holder built with `fromStore(...)` keeps one rememberer as a **`STRONG`** association under the singleton `KEY`; the aspect's `storeLastValueNow`/`resetToLastValue` and the auto-store listener all funnel through this object's two methods. See the [overview](../../overview.md) for where this sits in the `pile.aspect` family.

## What it is for

A `LastValueRememberer<E>` abstracts a tiny persistent slot for a single value:

- `void storeLastValue(E e)` — write the value to the external store.
- `E recallLastValue` — read the value back; **if nothing was ever stored, return a default** (the strategy supplies the default — see gotcha below). So `recallLastValue` never has to mean "absent".

That is the whole contract. Everything about *which* store, *what* key, and how `null` is handled lives inside a concrete implementation (typically a `Preferences`-backed one from `PrefInterop`); the interface is store-agnostic.

## It is also a `Prosumer`

`LastValueRememberer<E> extends Prosumer<E>` (a `Supplier<E>` + `Consumer<E>`, see `pile/aspect/combinations/Prosumer.java`). The two `default`s wire the prosumer methods to the strategy methods:

- `accept(v)` → `storeLastValue(v)`
- `get` → `recallLastValue`

So a rememberer can be passed anywhere a `Prosumer`/`Supplier`/`Consumer` is wanted, and — symmetrically — any `Prosumer` (or a `Supplier`+`Consumer` pair) can *become* a rememberer via the factories below.

## Factories (build one without writing a class)

- `static <E> LastValueRememberer<E> of(Prosumer<E> i)` — adapts an existing prosumer: `recallLastValue` = `i.get`, `storeLastValue` = `i.accept(e)`.
- `static <E> LastValueRememberer<E> make(Supplier<? extends E> g, Consumer<? super E> s)` — same from a separate getter/setter pair.

Both are thin anonymous-class adapters with no default-value or null handling of their own — those concerns are the caller's. For the real, store-backed implementations use `pile.interop.preferences.PrefInterop` (the `@see` on the interface points there): `PrefInterop.remember(...)` / `rememberBool/Int/Double/String/Enum(...)` build rememberers that put/get a key in a `Preferences` node and carry a `NullBehavior` policy (see [`RemembersLastValue` § Preferences-backed use](RemembersLastValue.md)).

## The association key and singleton

The interface also carries the machinery for attaching a rememberer to a holder, via [`HasAssociations`](HasAssociations.md):

- `LastValueAssociationKey<E>` — a private-constructor `AssociationKey<LastValueRememberer<E>>` whose `referenceStrength` is **`ReferencePolicy.STRONG`**.
- `static final LastValueAssociationKey<?> KEY` — the one shared key instance.
- `static <E> LastValueAssociationKey<E> key` — returns `KEY` with an unchecked cast to the desired `E`.

Because the key is a singleton, a holder can carry **at most one** rememberer; `putAssociation(LastValueRememberer.key, remember)` replaces any previous one. `Independent.storeLastValueNow`/`resetToLastValue` retrieve it with `getAssociation(LastValueRememberer.key)` and no-op when absent. The `STRONG` policy means the holder keeps the rememberer — and whatever it captures, e.g. a `Preferences` node — alive for the holder's lifetime.

## Salient / surprising behavior

- **`recallLastValue` conflates "stored value" and "default".** There is no "absent" signal in the contract; a strategy that has never been written must return *some* default. So the restore step in the builder (`value.set(remember.recallLastValue)`, see [`RemembersLastValue` § lifecycle](RemembersLastValue.md)) always sets a value, falling back to the strategy's default when the store is empty.
- **One rememberer per holder** (singleton key); you cannot stack two stores on one value through this aspect.
- **The `of`/`make` factories add no null/default policy.** They forward verbatim; null handling exists only in the `Preferences`-backed strategies (`PrefInterop`'s `NullBehavior`, where primitive-typed prefs reject `STORE_NULL`).
- **The store call is synchronous on the writing thread** — the auto-store listener calls `storeLastValue(...)` inline on every remembered change, so a `Preferences` strategy touches the prefs subsystem on the setting thread.

## Caveats & gotchas

- A rememberer is only *consulted* if it was attached. Building a value without `fromStore(...)` leaves no association, so this strategy never runs even though `remembersLastValue` is `true` (the gate means "not suppressed", not "has a rememberer").
- `storeLastValue(null)` behavior is entirely the strategy's: the adapters forward `null` to the wrapped consumer; `Preferences`-backed strategies apply their `NullBehavior` and may reject it.
- The unchecked cast in `key` is the usual phantom-type-key pattern (cf. [`HasAssociations` § typed-key mechanism](HasAssociations.md)); identity-keyed, so there is no runtime type check on the stored rememberer's `E`.

## Common tasks (how to…)

- **Adapt an existing getter/setter into a rememberer:** `LastValueRememberer.make( -> read, v -> write(v))`, or `LastValueRememberer.of(existingProsumer)`.
- **Get a `Preferences`-backed rememberer:** `PrefInterop.remember(node, key, default)` (and the typed `rememberInt/Bool/...` variants).
- **Attach one to a holder:** done for you by `IndependentBuilder.fromStore(rememberer, correctNulls)`; manually it is `holder.putAssociation(LastValueRememberer.key, rememberer)`.
- **Read/write the store directly:** `rememberer.recallLastValue` / `rememberer.storeLastValue(v)` — or, since it is a `Prosumer`, `rememberer.get` / `rememberer.accept(v)`.

## Tech debt / warts

- Empty javadoc `@param`/`@return` tags throughout, consistent with the project-wide "unsystematic API docs" note (see [overview § caveats](../../overview.md)).
- "Default when nothing stored" is an *unwritten* part of the `recallLastValue` contract — not expressible or enforced at the interface; only the concrete strategies honour it.

## Related

- [`RemembersLastValue`](RemembersLastValue.md) — the behavior aspect this strategy serves (the *when/whether*; this is the *where/how*).
- [`LastValueRememberSuppressible`](LastValueRememberSuppressible.md) — the suppression super-interface for temporarily turning remembering off (doc-pending).
- [`HasAssociations`](HasAssociations.md) — the typed key→value store via which a rememberer is attached (`KEY`, `STRONG` policy).
- [package index](_index.md) · [overview.md](../../overview.md).
