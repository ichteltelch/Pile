# `pile.aspect.combinations.ReadWriteDependency`

Read + write + be-depended-on, but **not** observable (no `ListenValue`) — and the home of the `biject*` family that builds two-way-linked derived views.

Source folder: `src`. File: `pile/aspect/combinations/ReadWriteDependency.java`.

`public interface ReadWriteDependency<E> extends ReadWriteValue<E>, ReadDependency<E>`. It joins the read/write union [`ReadWriteValue`](ReadWriteValue.md) with the read/depend union [`ReadDependency`](ReadDependency.md); the intersection (`ReadValue`) is shared, so the net union is **`ReadValue` + `WriteValue` + `Dependency`**. See the [combinations index](_index.md), [aspect index](../_index.md), and [overview](../../../overview.md) for where it sits.

## What it unions

- [`ReadValue<E>`](../ReadValue.md) — read the (possibly invalid) wrapped value.
- [`WriteValue<E>`](../WriteValue.md) — `set` the value (returns the *actually-set* value after corrections/sealing).
- [`Dependency`](../Dependency.md) — be depended on by other reactive values; a node others wire to.

Deliberately **absent**: `ListenValue` (no listener registration / change events). So a value typed as just `ReadWriteDependency` can be read, written, and depended on, but you cannot attach a `ValueListener` to it. That single omission is what separates it from [`ReadWriteListenDependency`](ReadWriteListenDependency.md) (see below).

## Where it sits in the lattice

This is one rung of the combination lattice over the four capabilities (read / write / listen / dependency):

```
ReadDependency<E>                 (read + depend)                ← parent; the map*/field*/comparison sugar
        │  + write (via ReadWriteValue)
        ▼
ReadWriteDependency<E>            (read + write + depend)        ← this interface; adds biject*
        │  + listen (via ReadWriteListenValue / ReadListenDependency)
        ▼
ReadWriteListenDependency<E>      (read + write + listen + depend)
        │  + recompute/transform/seal/...
        ▼
Pile<E>                          (the capstone)
```

- **Relative to [`ReadDependency`](ReadDependency.md):** this adds **write access** (`WriteValue`, pulled in via the `ReadWriteValue` parent). Everything `ReadDependency` carries — the large `map*`/`field*`/comparison/`readOnly`/`overridable` factory surface — is inherited unchanged.
- **Relative to [`ReadWriteListenDependency`](ReadWriteListenDependency.md):** that interface adds **listen** (observability) on top of this one, and is `ReadWriteDependency`'s most important sub-interface. `ReadWriteListenDependency` is `ReadWriteDependency` + `ListenValue`; the capstone [`Pile`](Pile.md) then adds recomputation. So `ReadWriteDependency` is "everything `ReadWriteListenDependency` is, minus the ability to be observed."

## What it ADDS over its parents — the `biject*` family

Almost the entire body is one feature: **bijections** — derived values that maintain a *two-way* one-to-one correspondence with this value. Unlike `ReadDependency.map*` (one-way: derived reads from source), a bijected view is itself **writable**, and writing it sets *this* value to the preimage. This is the read/write analogue of `map`, and it is the reason this combination — not `ReadDependency` — hosts these methods: they need both the `Dependency`/`ReadValue` side (to recompute the view from `get`) **and** the `WriteValue` side (to push the inverse back into `this`).

### The core: `_bijectSetup(...)`
All public `biject*` methods funnel into `_bijectSetup(v, mapFunction, reentryGuard, consistencyCheck, dependencies...)`. It configures a (sealed-but-writable) [`SealPile`](../../impl/SealPile.md) `v` so that:
- it **recomputes** as `mapFunction.apply(get)` and registers `whenChanged(this)`;
- its **seal interceptor** runs both writes inside nested transactions — it sets `v` to the written value, then sets `this` to `mapFunction.applyInverse(value)`; the dual transaction means observers see one atomic update.

### `consistencyCheck` (the corrections guard)
If a non-null `consistencyCheck` `BiPredicate` is supplied, then after writing back to `this`, the result is re-mapped forward (`mapFunction.apply(get)`) and compared to the originally-written value; on disagreement `v` is re-set to the corrected forward value. This exists because `this`'s correctors may change the value, so the bijection is only "modulo corrections." See [`WriteValue` § ways a write is corrected](../WriteValue.md).

### `reentryGuard` (the cycle guard)
There is **no built-in guard against an infinite mutual-update loop** if the corrections don't converge (the javadoc warns of this, ). Passing `reentryGuard=true` wraps the interceptor in a `pile.utils.Nonreentrant` that drops re-entrant writes and logs `"Cycle detected in bijected value …"` at WARNING. The public `biject`/`bijectTo*` convenience methods all pass `reentryGuard=false`; only the 5-arg `_bijectSetup` exposes the guard.

### The public surface (all default methods, all thin wrappers)
- `biject(mapFunction[, consistencyCheck][, dependencies...])` → `SealPile<F>` — generic two-way view.
- `bijectToBool` / `bijectToInt` / `bijectToDouble` → the typed `SealBool`/`SealInt`/`SealDouble` from `pile.specialized_*` (avoid boxing the mapper; Java has no extension methods).
- Each comes in overloads: with an explicit `consistencyCheck`, or using the default `DEFAULT_BIJECT_EQUIVALENCE` (= `Objects::equals`, ); with or without extra `Dependency...` that the bijection function itself depends on.
- `_bijectSetup` overloads default `reentryGuard=false`.

### Other added members
- `E set(E v)` — **re-declared abstract** here. The real contract is `WriteValue.set` (returns the actually-set value); concrete behavior lives in the impl classes, not here.
- `setNull` — default; `set(null)` then returns `this` re-typed as `ReadWriteDependency<E>` (covariant-return refinement of [`ReadWriteValue.setNull`](ReadWriteValue.md), ).
- `isEqualConstRW(value[, eq])` → `SealBool` — a **writable** equality indicator: reads as "is `this` equal to the constant"; writing `true` sets `this` to the constant, writing `false` sets `this` to `null` **only if** currently equal to the constant; `null` vetoes. This is the read/write counterpart to `ReadDependency.isEqualConst` (which is read-only). Built via `PileBool.sb…seal(...).parent(this).whenChanged(this)`.

## Override map (where the real behavior lives)

`ReadWriteDependency` itself declares only `set` as abstract; everything else is a default factory. The factories (`biject*`, `isEqualConstRW`) are **not overridden** downstream — they are pure builders and behave identically for every implementor; their behavior is fixed by the `SealPileBuilder` / `Piles` / `PileBool` code they delegate to. The state-dependent contracts come from the inherited aspects, realized on the shared base and leaf piles:
- **`AbstractReadListenDependency`** — the base under all general piles; supplies the inherited `ReadValue`/`WriteValue`/`Dependency` plumbing.
- **[`PileImpl`](../../impl/PileImpl.md)** — full implementation of `set` (corrections, transactions, propagation; see `PileImpl`).
- **[`SealPile`](../../impl/SealPile.md)** — `set` becomes a redirect once sealed (the mechanism `_bijectSetup` itself exploits via `seal(interceptor)`).
- **[`Independent`](../../impl/Independent.md)** — writable, stays valid through transactions.

## Who implements / extends it

- **Sub-interfaces:** [`ReadWriteListenDependency`](ReadWriteListenDependency.md) (adds listen; itself the super of `Pile<E>`), and the primitive/typed specializations `ReadWriteDependencyBool`, `ReadWriteDependencyComparable` (and the `…Int`/`…Double`/`…String` kin) in `pile.specialized_*.combinations`. Also reached by the transform aspect via `TransformableDependency`.
- **Concrete implementers (transitively):** every full [`Pile`](Pile.md) (`PileImpl`, `SealPile`, primitive piles), `Independent`, and the persistence-backed values `PreferencesBackedValue` / `SynchronizingFilesBackedValue`.
- Note `MutRef` is **not** here — it is a `ReadWriteValue` but deliberately *not* a `Dependency`, so it stops one rung below this interface.

## Salient / surprising behavior

- **`biject` is two-way, `map` is one-way.** The returned `SealPile` is sealed *but writable*: writing it drives `this` to the inverse. Easy to mistake for a read-only `map`.
- **No cycle protection by default.** Mutual-update loops are possible if correctors don't converge; you must opt into `reentryGuard` via the 5-arg `_bijectSetup` — the convenience methods do not.
- **The bijection holds "modulo corrections."** If `this` corrects the inverse value, the view may not round-trip exactly; that is what `consistencyCheck` repairs.
- **`isEqualConstRW(false)` is conditional:** writing `false` nulls `this` *only if* it currently equals the constant — otherwise the write is silently inert.

## Caveats & gotchas

- **`set(x)` returns the actually-set value**, not `x` (inherited `WriteValue` semantics — corrections/sealing). Capture the return if it matters. See [`WriteValue`](../WriteValue.md).
- **Not observable.** Don't try to attach a listener to a `ReadWriteDependency`; if you need that, you need [`ReadWriteListenDependency`](ReadWriteListenDependency.md)/`Pile`.
- **Bijected writes open two transactions** (on `v` and on `this`, ) and the inverse runs inside them; a misbehaving `mapFunction.applyInverse`/corrector can stall both values mid-transaction.
- The `static Logger log` is only used by the `reentryGuard` cycle warning; nothing else logs here.

## Common tasks (how to…)

- **Two-way mapped view:** `rwd.biject(Bijection.of(fwd, inv))` → writable `SealPile`; write it to drive `rwd` to the preimage.
- **Typed two-way view (no boxing):** `rwd.bijectToInt(bijection)` / `bijectToBool` / `bijectToDouble`.
- **Guard a correcting/non-convergent bijection:** call `rwd._bijectSetup(new SealPile<>, bij, /*reentryGuard=*/true, consistencyCheck)`.
- **Bijection whose function has extra inputs:** pass them as the trailing `Dependency...` so the view also depends on them.
- **Writable "is it this constant?" toggle:** `rwd.isEqualConstRW(value)` → `SealBool` you can both read and set.
- **Clear:** `rwd.setNull`.

## Tech debt / warts

- The `biject*` matrix is another large hand-rolled overload grid (generic × bool/int/double × with/without `consistencyCheck` × with/without `dependencies`), mirroring the `map*` sprawl on `ReadDependency` — the [overview](../../../overview.md) attributes this to Java's missing extension methods.
- `_bijectSetup`'s javadoc carries an open `//TODO: Give users access to the builder for more detailed configuration needs` — no escape hatch yet for customizing the generated `SealPileBuilder` (unlike `map`'s 2-arg `Consumer<Builder>` overloads).
- Pervasive empty `@param`/`@return` javadoc tags, consistent with the project-wide "unsystematic API" note.
- `isEqualConstRW` references `Pile.DEFAULT_BIJECT_EQUIVALENCE` while the local constant `DEFAULT_BIJECT_EQUIVALENCE` is the same `Objects::equals` — two equivalent constants in flight.

## See also

- Aspects unioned: [`ReadValue`](../ReadValue.md) · [`WriteValue`](../WriteValue.md) · [`Dependency`](../Dependency.md).
- Parent (read-only sibling): [`ReadDependency`](ReadDependency.md) — the `map*`/`field*` surface this inherits. Other parent: [`ReadWriteValue`](ReadWriteValue.md).
- Listen-adding subtype: [`ReadWriteListenDependency`](ReadWriteListenDependency.md); capstone [`Pile`](Pile.md).
- [combinations index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md) · [concepts/transactions](../../../concepts/transactions.md).
