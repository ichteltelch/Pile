# `pile.specialized_String.combinations` — String-specialized combination interfaces (Tier 1 map)

Twelve `*String` interfaces that mirror the twelve generic [`pile.aspect.combinations`](../../aspect/combinations/_index.md) interfaces, re-typed to `String`. These are the **thinnest** combination family: almost every member is a pure assembly or type-narrowing stub with no new logic, because `String` has no operator algebra on the value types (concatenation lives in [`Piles`](../../impl/Piles/_index.md), not here).

Source folder: `src` (all interfaces below).

Up: [String package index](../_index.md) · [overview](../../../overview.md). Bool sibling (the exemplar family): [`specialized_bool/combinations`](../../specialized_bool/combinations/_index.md). Generic counterparts: [aspect combinations index](../../aspect/combinations/_index.md).

## Layering: `*String` extends `*Comparable`, not the generic directly

Unlike the bool family (where each `*Bool` extends the generic interface directly), every `*String` combination interface extends the corresponding **`*Comparable<String>`** combination interface. String is-a Comparable, so the Comparable layer is inserted in between. The `*Comparable` sibling docs are at `../../specialized_Comparable/combinations/` *(pending)*.

## Map: `*String` → `*Comparable` counterpart → generic

| `*String` interface | Extends (`*Comparable`) | Also extends (generic) | Narrows / adds |
|---|---|---|---|
| `JustReadValueString` | [`JustReadValueComparable<String>`](../../specialized_Comparable/combinations/JustReadValueComparable.md) *(pending)* | [`JustReadValue`](../../aspect/JustReadValue.md) (via `ReadValueString`) | Pure assembly stub; no new members. |
| `ReadValueString` | [`ReadValueComparable<String>`](../../specialized_Comparable/combinations/ReadValueComparable.md) *(pending)* | [`ReadValue`](../../aspect/ReadValue.md) | Pure assembly stub; no new members. |
| `ReadDependencyString` | [`ReadDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadDependencyComparable.md) *(pending)* | [`ReadDependency`](../../aspect/combinations/ReadDependency.md) | **Adds** `readOnly()` → `SealString`; `overridable()` → `PileStringImpl`. |
| `ReadListenValueString` | [`ReadListenValueComparable<String>`](../../specialized_Comparable/combinations/ReadListenValueComparable.md) *(pending)* | [`ReadListenValue`](../../aspect/combinations/ReadListenValue.md) | Narrows the buffer family (`buffer`/`validBuffer`/`rateLimited` + builders) to `SealString`/`IndependentString`. No new logic. |
| `ReadListenDependencyString` | [`ReadListenDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadListenDependencyComparable.md) *(pending)* | [`ReadListenDependency`](../../aspect/combinations/ReadListenDependency.md) | **Adds** `fallback(String)`. |
| `WriteValueString` | [`WriteValueComparable<String>`](../../specialized_Comparable/combinations/WriteValueComparable.md) *(pending)* | [`WriteValue`](../../aspect/WriteValue.md) | **Adds** `setEmpty()` (`set("")`) and `setNull()` (returns `this`). |
| `WriteElsewhereString` | — | [`WriteElsewherePile`](../../aspect/combinations/WriteElsewherePile.md) | **Entirely commented out** (inert vestige). |
| `ReadWriteValueString` | [`ReadWriteValueComparable<String>`](../../specialized_Comparable/combinations/ReadWriteValueComparable.md) *(pending)* | [`ReadWriteValue`](../../aspect/combinations/ReadWriteValue.md) | Narrows `setNull()` return type to `ReadWriteValueString`. |
| `ReadWriteDependencyString` | [`ReadWriteDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadWriteDependencyComparable.md) *(pending)* | [`ReadWriteDependency`](../../aspect/combinations/ReadWriteDependency.md) | Narrows `setNull()` return type to `ReadWriteDependencyString`. |
| `ReadWriteListenValueString` | [`ReadWriteListenValueComparable<String>`](../../specialized_Comparable/combinations/ReadWriteListenValueComparable.md) *(pending)* | [`ReadWriteListenValue`](../../aspect/combinations/ReadWriteListenValue.md) | Narrows the **writable** buffer family to `SealString`/`IndependentString`; narrows `setNull()`. |
| `ReadWriteListenDependencyString` | [`ReadWriteListenDependencyComparable<String>`](../../specialized_Comparable/combinations/ReadWriteListenDependencyComparable.md) *(pending)* | [`ReadWriteListenDependency`](../../aspect/combinations/ReadWriteListenDependency.md) | **Adds** `nullableWrapper()` (delegates to `PileString.nullableWrapper`); `fallback(String)`; narrows `setNull()`. |
| `LastValueRemembererString` | — | [`LastValueRememberer`](../../aspect/LastValueRememberer.md) | Pure assembly stub; no new members. |

`PileString` (the capstone, in the parent package) extends `ReadWriteListenDependencyString` — exactly as generic `Pile` sits atop `ReadWriteListenDependency`.

## The String-specific additions (what's new at this layer)

### Write conveniences — `WriteValueString`
- `setEmpty()` — calls `set("")`; the only String-specific write shortcut.
- `setNull()` — `set(null)` returning `this` for chaining. Present (with covariant return-type narrowing) on all writable interfaces: `WriteValueString`, `ReadWriteValueString`, `ReadWriteDependencyString`, `ReadWriteListenValueString`, `ReadWriteListenDependencyString`.

### Return-type narrowing — `ReadDependencyString`
- `readOnly()` — wraps `this` in a `SealString` read-only view (via `Piles.makeReadOnlyWrapper`).
- `overridable()` — returns a `PileStringImpl` that recomputes from `this` but can be locally overridden (via `Piles.computeString`).

### Buffer family — `ReadListenValueString` / `ReadWriteListenValueString`
Same mechanical narrowing as the bool family: the generic `buffer`/`validBuffer`/`rateLimited` (+ their `readOnly*`/`writable*`/builder variants) are overridden to return `SealString` or `IndependentString` instead of raw `SealPile<String>`/`Independent<String>`. Builders route through `PileString.sb()` and `PileString.ib()`.

### `fallback` and `nullableWrapper` — upper read-write interfaces
- `ReadListenDependencyString.fallback(String)` — delegates to `Piles.fallback`; returns a `SealString` that holds the constant while `this` is invalid and redirects writes back to `this`.
- `ReadWriteListenDependencyString.fallback(String)` — same (re-declared at the writable level).
- `ReadWriteListenDependencyString.nullableWrapper()` — delegates to `PileString.nullableWrapper(this)`; see [`../_index.md`](../_index.md) for the null-as-`""` encoding semantics.

## What is NOT here
- No string operators (no `concat`, no `isEmpty`, no `length` reactive ops) — concatenation is in [`Piles`](../../impl/Piles/_index.md).
- No `flip()`/`not()`/logic algebra — this is not the bool family.
- `WriteElsewhereString` is dead commented-out code.

## Caveats & gotchas
- **`null` vs `""`**: `setEmpty()` sets `""`, not `null`. `setNull()` sets `null`. These are distinct states; the `nullableWrapper` deliberately conflates them for backends that can't store null.
- **`overridable()` naming**: `Piles.computeString` is used, which sets up a recompute dependency from `this`. The `name(...)` call appends `*` to the dependency name — a convention that the returned value is a mutable override of its source.
- **`*Comparable` layer is the real interface**: the String combinations add almost nothing themselves; look to the `*Comparable` sibling docs for the comparison surface (ordering, min/max, etc.).

## Tech debt / warts
- `WriteElsewhereString` is entirely commented out — dead code mirroring `WriteElsewhereBool`.
- The `*Comparable` combination interfaces have no docs yet; forward links in this file are marked *(pending)*.
