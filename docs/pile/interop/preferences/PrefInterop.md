# `pile.interop.preferences.PrefInterop`

Static factory hub bridging Pile to `java.util.prefs.Preferences`: it builds [`LastValueRememberer`](../../aspect/LastValueRememberer.md)s (the remember-last-value storage strategy) backed by a `Preferences` node, and `*preference(...)` reactive `Independent` values that mirror a prefs entry live, all governed by a `NullBehavior` policy.

Source folder: `src`. File: `pile/interop/preferences/PrefInterop.java`.

Up: [package index](_index.md) · [interop index](../_index.md) · [overview](../../../overview.md). Remember-last-value model: [`RemembersLastValue`](../../aspect/RemembersLastValue.md), [`LastValueRememberer`](../../aspect/LastValueRememberer.md). Sibling reactive-value bridge: [`PreferencesBackedValue`](PreferencesBackedValue.md).

## What it is for

`PrefInterop` is a leaf utility class (all members `static`; never instantiated). It provides **two parallel mechanisms** for connecting a `Preferences` entry to Pile, plus the `NullBehavior` enum they share:

1. **`remember*` / `remember`** — build a [`LastValueRememberer`](../../aspect/LastValueRememberer.md): a passive store/recall slot you hand to `IndependentBuilder.fromStore(...)`. This is the canonical way to make a persisted setting and is the route the [`RemembersLastValue`](../../aspect/RemembersLastValue.md) aspect uses. The rememberer has **no listeners** and does not itself observe the node.
2. **`*preference` / `preference`** — build a fully-wired reactive `Independent` (`IndependentBool`/`IndependentInt`/`IndependentDouble`/`IndependentString`/`Independent<E>`) that is **bidirectionally synced** with the node: writes to the value are pushed into the node, and external `Preferences` changes are pushed back into the value via a `PreferenceChangeListener`. This is a *parallel* mechanism that does **not** go through `RemembersLastValue` — choosing between the two is a known wart (see [`RemembersLastValue` § Preferences-backed use](../../aspect/RemembersLastValue.md)).

Both mechanisms exist in the same per-type families (bool / int / double / String / enum) and share the same `NullBehavior` null-write policy.

## The `NullBehavior` policy

`PrefInterop.NullBehavior` decides what happens when `null` is written to a value/rememberer whose preference cannot or should not be `null` (e.g. a primitive-typed key):

| Constant | Effect when `null` is stored |
|---|---|
| `DELETE` | `node.remove(key)` — drop the entry. |
| `IGNORE` | Leave the store untouched (the `Independent` itself stays `null`). **This is the default** whenever the passed `NullBehavior` argument is itself `null` (every factory does `nb = _nb==null ? IGNORE : _nb`). |
| `STORE_DEFAULT` | Substitute the `defaultValue` and store that. |
| `STORE_NULL` | "Store a `null` reference anyway" — **unsupported**; the primitive/typed factories throw `IllegalArgumentException` up front (see gotchas for the String/enum exceptions). |

`STORE_NULL` is rejected eagerly by `boolPreference`, `doublePreferenceBuilder`, `intPreferenceBuilder`, `stringPreference`, `enumPreference`, and the corresponding `rememberBool/Double/Int` factories. `rememberString` and `rememberEnum` do **not** reject it at construction (they handle it in `storeLastValue` instead — buggily; see Tech debt).

## The `remember*` factory family (`LastValueRememberer`s)

Each `remember<Type>` returns an anonymous `LastValueRememberer<Type>` (the typed subinterfaces `LastValueRemembererBool/Int/Double/String`, or plain `LastValueRememberer<E>` for enums) capturing the `node`, `key`, `defaultValue`, and resolved `nb`:

- **`recallLastValue`** simply delegates to the matching typed getter: `node.getBoolean/getInt/getDouble(key, defaultValue)`, `node.get(key, defaultValue)` for String, and (for enums) `resolver.apply(node.get(key, defaultValue.name()))`. There is no "absent" signal — the prefs default fills in (consistent with the [`LastValueRememberer`](../../aspect/LastValueRememberer.md) contract).
- **`storeLastValue(e)`** applies the `NullBehavior` switch when `e==null`, then writes via the matching typed putter (`putBoolean`/`putInt`/`putDouble`/`put`). For enums it stores `e.name()`.

Variants:
- `rememberBool` / `rememberDouble` / `rememberInt` / `rememberString` — `(Preferences node, String key, <prim> defaultValue, NullBehavior)`.
- **`rememberBool(ReadListenDependency<? extends Preferences> nodeV, String key, ReadListenDependency<? extends Boolean> defaultValue, NullBehavior)`** — a *reactive* overload: the node and the default are themselves Pile values, re-read (`nodeV.get`, `defaultValue.get`) on every store/recall. Only `rememberBool` has this overload; the other types do not.
- `rememberEnum(node, key, E defaultValue, Function<String,E> resolver[, NullBehavior])` — two overloads; the 4-arg one defaults to `NullBehavior.IGNORE`. Its `recallLastValue` is the most elaborate: it reads with default `defaultValue==null?"":defaultValue.name()`, treats an **empty string as `null`** (returns `null`), and if `resolver` throws `IllegalArgumentException` it falls back to `defaultValue` (so an unparseable stored name degrades gracefully rather than propagating).

## The `*preference` reactive-value family

`boolPreference` / `doublePreference` / `intPreference` / `stringPreference` / `enumPreference` build a live, two-way-synced `Independent`. The wiring (identical in shape across types) is:

1. Create the `Independent` (directly, or via `Piles.independent(...)` for int/double so a builder can be returned), initialised from `node.get<Type>(key, defaultValue)`.
2. A `ThreadLocal<Boolean> inChange` guards against feedback loops: whenever the code is propagating a change in one direction it sets the flag, and both directions bail out early if the flag is already `TRUE`. (`inChange` holds `Boolean`, so the nesting is restored to the previous value in `finally`, not blindly cleared.)
3. A `PreferenceChangeListener` (`pcl`) is added to the node; on a matching `key` it pushes `node.get<Type>(...)` into the value. The value is held only **weakly** by the listener via a `WeakCleanupWithRunnable` (`weakRet`): if the `Independent` is GC'd, `weakRet.get()` returns `null` and the listener no-ops, and the cleanup action `node.removePreferenceChangeListener(pcl)` detaches the listener. This prevents the listener (and thus the node) from keeping the value alive forever.
4. A `ValueListener` on the value applies the `NullBehavior` switch and writes through to the node via the typed putter.

Type-specific notes:
- **`doublePreference`** sets `_setEquivalence(STRING_EQUIVALENCE)` on the value: two doubles count as equal iff their `toString()`s match. This avoids spurious writes from round-trip float formatting (the stored representation is the string form). `STRING_EQUIVALENCE` is declared with `Double` generics but is only used here.
- **int / double** additionally call `builder.neverNull()` and return an `IndependentBuilder` from the `*Builder` variants (`intPreferenceBuilder`, `doublePreferenceBuilder`) so the caller can configure bounds etc. before `.build()`. `intPreference`/`doublePreference` just call `…Builder(...).build()`.
- **`enumPreference`** takes a `Function<String,E> resolver` to parse the stored name; it stores `value.name()`. (Unlike `rememberEnum`, its `resolver` failures are **not** caught here.)

## The `preference` / `remember` overload shims

`preference(...)` (4 overloads: bool/double/int/String) and `remember(...)` (bool/double/int/String, each with and without a trailing `NullBehavior`) are **convenience aliases** that just forward to the type-named factory. They exist so callers can rely on Java overload resolution by the `defaultValue` argument's type instead of spelling the type into the method name; the javadoc on each notes that if overloading is ambiguous you can call the explicit `boolPreference`/`rememberInt`/… form instead. The no-`NullBehavior` `remember(...)` overloads default to `NullBehavior.IGNORE`. There is **no** `preference`/`remember` shim for enums — call `enumPreference` / `rememberEnum` directly.

## Salient / surprising behavior

- **Two unrelated integrations look similar.** A `remember*` result is an inert store you must wire via `fromStore`; a `*preference` result is an already-live `Independent`. They share names and the `NullBehavior` enum but nothing else. The `RemembersLastValue` aspect only ever uses the `remember*` form.
- **`null` default coalescing is pervasive:** a `null` `NullBehavior` always becomes `IGNORE`. Passing `null` is the idiomatic way to ask for "ignore null writes".
- **Weak-reference self-cleanup** (`*preference` only): the prefs listener never strong-holds the value; GC of the value silently unregisters the listener. The `remember*` rememberers, by contrast, hold nothing reactive (except the `ReadListenDependency` overload) and add no listeners.
- **External edits propagate** into a `*preference` value (another process / another `Preferences` writer triggers the `pcl`), guarded against echoing back by `inChange`.
- **`STRING_EQUIVALENCE` on doubles** means a write that changes the value but not its `toString()` is suppressed.
- **`rememberEnum` empty-string ⇒ `null`** on recall, and parse failures fall back to the default — neither is true of `enumPreference`.

## Caveats & gotchas

- **`STORE_NULL` is a trap.** Most factories reject it at construction. Where they don't (`rememberString`, `rememberEnum`), the runtime handling is broken (see Tech debt) — never use `STORE_NULL`.
- **Storing happens synchronously on the writing thread**, inline in the value/`storeLastValue` call. For a `Preferences` backing this touches the prefs subsystem on every change.
- **Only `rememberBool` has the reactive `(ReadListenDependency…)` overload.** If you need a value-driven node or default for int/double/String/enum, you must build it yourself.
- **`enumPreference` does not catch resolver exceptions** the way `rememberEnum` does; a malformed stored enum name will propagate the `resolver`'s exception into the prefs listener / restore path.
- A `*preference` value's prefs listener uses `key.equals(e.getKey())`; if you pass a `null` key it will NPE on construction paths that call `node.get...` — keys must be non-null (only `rememberBool(node,...)` calls `Objects.requireNonNull(node)`, and none null-check the key).

## Common tasks (how to…)

- **Persisted reactive setting via the aspect:** `Piles.independent(def).fromStore(PrefInterop.remember(node, key, def), correctNulls).build()` — see [`RemembersLastValue`](../../aspect/RemembersLastValue.md).
- **Live two-way-synced value (no aspect):** `PrefInterop.intPreference(node, key, def, NullBehavior.IGNORE)` (or `preference(node, key, def, …)`).
- **Configure bounds on a synced int/double:** `PrefInterop.intPreferenceBuilder(node, key, def, nb)` then chain builder methods before `.build()`.
- **Enum setting:** `PrefInterop.rememberEnum(node, key, defaultEnum, MyEnum::valueOf)` for a rememberer, or `enumPreference(node, key, defaultEnum, nb, MyEnum::valueOf)` for a live value.
- **Drive node/default from another Pile value (bool only):** `PrefInterop.rememberBool(nodeV, key, defaultV, nb)`.

## Tech debt / warts

- **`STORE_NULL` mishandling in `rememberString` / `rememberEnum`.** Unlike the primitive factories, these accept `STORE_NULL` at construction. In `rememberString.storeLastValue`, the `STORE_NULL` case does `assert false; return;` — so with assertions off it silently does nothing (never reaches the `node.put`). In `rememberEnum.storeLastValue`, the `STORE_NULL` case sets `s = ""` and then **`return`s immediately**, so the computed `s` is never written (the trailing `node.put(key, s)` is dead for that path). Both are inconsistent with the enum-mechanism's own recall (which maps `""`→`null`). See `SUSPECTED_BUGS`.
- **Two parallel Preferences integrations** (`remember*` vs `*preference`) with overlapping names invite picking the wrong one; the distinction (aspect vs standalone reactive value) is not obvious from the call site.
- **Reactive overload asymmetry:** only `rememberBool` has the `ReadListenDependency` node/default form; the other types don't, for no documented reason.
- **No key null-checks** and inconsistent node null-checks (`Objects.requireNonNull(node)` only on one overload).
- **Empty `@return` javadoc tags** throughout, consistent with the project-wide unsystematic-API-docs note (see [overview](../../../overview.md)).
- `STRING_EQUIVALENCE` is typed `BiPredicate<? super Double, ? super Double>` but conceptually generic over any `toString`-comparable; it is reused only by `doublePreference`.

## Related

- [`RemembersLastValue`](../../aspect/RemembersLastValue.md) — the behavior aspect that consumes `remember*` rememberers.
- [`LastValueRememberer`](../../aspect/LastValueRememberer.md) — the storage-strategy interface these factories implement.
- [`PreferencesBackedValue`](PreferencesBackedValue.md) — sibling reactive value mirroring a prefs entry (doc-pending).
- [package index](_index.md) · [interop index](../_index.md) · [overview.md](../../../overview.md).
