# `pile.aspect.combinations.ReadWriteListenValue`

The combination contract for a value you can **read**, **write**, and **observe (listen to)** — but which is **not (necessarily) a dependency** — `ReadListenValue` ∪ `ReadWriteValue` (= `ReadValue` + `WriteValue` + `ListenValue`).

Source folder: `src`. File: `pile/aspect/combinations/ReadWriteListenValue.java`.

`ReadWriteListenValue<E>` unions [`ReadListenValue<E>`](ReadListenValue.md) and [`ReadWriteValue<E>`](ReadWriteValue.md). Through those parents it transitively pulls in the three aspects [`ReadValue`](../ReadValue.md), [`WriteValue`](../WriteValue.md), and `ListenValue` (the `pile.aspect.listen` aspect). It is the **writable twin of [`ReadListenValue`](ReadListenValue.md)** and the **non-dependency value-side** point one rung below [`ReadWriteListenDependency`](ReadWriteListenDependency.md).

## What it's for

This is the contract for a holder you can `get`, `set(...)`, **and** attach `ValueListener`s to / `await` conditions on — yet which makes **no promise to be a graph dependency** (nothing can declare a reactive dependency *on* it; it is not a `Dependency` node). Concretely it sits between [`ReadWriteValue`](ReadWriteValue.md) (adds listening) and [`ReadWriteListenDependency`](ReadWriteListenDependency.md) (which further adds `Dependency`).

In the four-dimension lattice (Read / Write / Listen / Dependency — see the [combinations index](_index.md)) this is the point **R + W + L, no D**.

## What it unions (and what it deliberately omits)

- From [`ReadListenValue`](ReadListenValue.md): the validity-aware read surface, the `ListenValue` observation surface (`addValueListener`, `fireValueChange`, `ValueEvent`/`ValueListener`), `await`/`runWhen`, `_getEquivalence`, `willNeverChange`, and the whole **buffer / validBuffer / rateLimited factory family**.
- From [`ReadWriteValue`](ReadWriteValue.md): `set` (returns the *actually-set* value), `setNull`, invalidation/revalidation, `Consumer`/`RemembersLastValue`, and `Prosumer`.
- **Absent on purpose:** no `Dependency`/`Depender` — it is not a node others can depend on. Adding `Dependency` is exactly what the subtype [`ReadWriteListenDependency`](ReadWriteListenDependency.md) does.

## What it adds over its parents (the delta)

Almost nothing *new* in capability — it is mainly a join type. Its real contribution is to **override the buffer-factory family inherited from [`ReadListenValue`](ReadListenValue.md) so the buffers become writable**. Because this value is writable, its buffers can write back:

- **`validBuffer` / `validBuffer_memo` / `validBufferBuilder`** are overridden to delegate to the **`writable*` variants**. Note the curious detail: `validBuffer_memo` delegates to `readOnlyValidBuffer_memo`, while `validBuffer` delegates to `writableValidBuffer` — so the *memoized* default-named buffer is read-only but the *non-memoized* one is writable. (Contrast `ReadListenValue`, where every default-named accessor is read-only.)
- **`buffer` / `bufferBuilder`** → `writableBuffer` / `writableBufferBuilder`, producing a [`SealPile`](Pile.md) whose writes redirect back into `this`.
- **`weakBuffer`**, **`rateLimited(cold,cool)`** → their `writable*` twins.
- New **`writable*` methods** not present on the read-only parent: `writableValidBuffer[_memo|Builder]`, `writableBuffer[Builder]`, `writableWeakBuffer[Builder]`, `writableRateLimited[Builder]` — each delegating to a `Piles.ib/sb.setup*Writable*(this, …)` builder.
- **`WRITABLE_VALID_BUFFER_CACHE`** — an `IdentitiyMemoCache` keyed on the value, backing `writableValidBuffer_memo`. The twin of `ReadListenValue.READ_ONLY_VALID_BUFFER_CACHE`.
- **`asDependency`** — default returns `validBuffer_memo`: since a bare `ReadWriteListenValue` is *not* a `Dependency`, "make me dependable" must materialise a memoized valid-buffer ([`Independent`](../../impl/Independent.md), which *is* a dependency). This is **overridden to `return this`** by [`ReadWriteListenDependency`](ReadWriteListenDependency.md) once the value really is a dependency — see conflicts.

### The writable-buffer-is-an `Independent`
All the `*ValidBuffer*` methods are typed to return [`Independent<E>`](../../impl/Independent.md). That is significant: the buffer you get back is itself a full `ReadWriteListenDependency` (an `Independent` *is* a dependency), even though the source `ReadWriteListenValue` is not. So "buffer a non-dependency value" is the standard way to obtain a dependency-capable, settled copy of it.

## How it differs from its neighbours

- **vs [`ReadListenValue`](ReadListenValue.md) (read-only twin):** identical observation surface; this one **adds write** (`WriteValue` via `ReadWriteValue`) and therefore overrides the buffer family to the writable variants. `ReadListenValue.validBuffer_memo` etc. explicitly note "subclasses that also implement `ReadWriteListenValue` call `writable*` instead" — *this interface is that subclass.*
- **vs [`ReadWriteValue`](ReadWriteValue.md):** that is read+write only, **not observable**. This adds `ListenValue` (listeners, `await`, equivalence-based change detection) on top.
- **vs [`ReadWriteListenDependency`](ReadWriteListenDependency.md) (the dependency superset):** that interface adds [`Dependency`](../Dependency.md) — it can be depended on. The defining difference is the dependency dimension, and it shows up behaviorally in `asDependency`: here it makes a buffer; there it returns `this`. Choose `ReadWriteListenValue` when you want a writable, observable cell whose changes must **not** be wireable as a reactive-graph dependency.

## Who implements it

There is **no concrete leaf that implements `ReadWriteListenValue` and stops there** in the general hierarchy. Every general-purpose concrete writable+observable value is also a dependency:

- **Sub-interface:** [`ReadWriteListenDependency`](ReadWriteListenDependency.md) `extends ReadWriteListenValue, ReadListenDependency, ReadWriteDependency`, and hence the capstone [`Pile`](Pile.md).
- **Concrete:** [`Independent`](../../impl/Independent.md) implements `ReadWriteListenDependency`, [`SealPile`](Pile.md), `PileImpl`, etc. — all via the dependency subtype, never via this interface bare.
- **Specialized twins:** `ReadWriteListenValueBool`/`Int`/`Double`/`String`/`Comparable` (e.g. `ReadWriteListenValueBool.java`) are the primitive-specialized parallels, used by `pile.relation` constraints and `pile.interop.preferences` backed values.

So in practice this interface is a **structural waypoint**: it exists to host the writable-buffer overrides and to be the value-side parent that `ReadWriteListenDependency` joins with the dependency-side parents. You will rarely declare a variable of this exact type; you will type things as `ReadWriteListenDependency`/`Pile` (dependency) or `ReadWriteValue`/`MutRef` (no listen).

## Salient / surprising behavior

- **`validBuffer_memo` is read-only, `validBuffer` is writable**. The memoized default-named valid buffer deliberately stays read-only here; reach for `writableValidBuffer_memo` if you want a *memoized writable* one.
- **`asDependency` materialises a buffer**, unlike on the dependency subtype where it returns `this`. If you call it polymorphically, the runtime type decides whether you get a copy or the original.
- **Writable buffers redirect writes back into `this`.** `writableBuffer`/`writableValidBuffer` produce `SealPile`/`Independent` views whose `set` flows back to the source — consistent with the writable `fallback` on [`ReadWriteListenDependency`](ReadWriteListenDependency.md).
- **Memoization caveats** (per the javadoc on the `*_memo` methods): do **not** use the `*_memo` buffers if you will give the result a debug name/owner, add never-removed listeners, need it to fire its own `ValueEvent`s as an independent entity, or only reference `this` locally. Use the non-memo builder variants instead.

## Caveats & gotchas

- This interface has **no field/state of its own** — pure aspect glue. All concrete behavior lives in `AbstractReadListenDependency` / the leaf impls (`Independent`, `SealPile`, `PileImpl`).
- Don't read "Value" (vs "Dependency") as a weaker *implementation* — read it as "**not a graph dependency**." A value typed as `ReadWriteListenValue` may still be observed and written; it simply can't be the target of a reactive `Depender`.
- The read-only/writable buffer split is easy to trip over: same method *name* (`buffer`, `validBuffer`, `rateLimited`) returns a writable result here but a read-only one on [`ReadListenValue`](ReadListenValue.md). Behavior is selected by the static type / the override, per the javadoc cross-notes in `ReadListenValue`.

## Common tasks (how to…)

- **Get a writable, settled (valid-only) copy:** `writableValidBuffer` (non-memo) or `writableValidBuffer_memo` (cached) → an [`Independent<E>`](../../impl/Independent.md).
- **Get a writable buffered view that redirects writes back:** `writableBuffer` / `writableWeakBuffer`.
- **Rate-limit writes/reads:** `writableRateLimited(coldStartMs, coolDownMs)`.
- **Make this dependable:** `asDependency` — returns a memoized valid-buffer (because this is not itself a `Dependency`).
- **Observe / await:** inherited from [`ReadListenValue`](ReadListenValue.md) (`addValueListener`, `await`, `runWhen`).

## Tech debt / warts

- The asymmetry between `validBuffer_memo` (read-only) and `validBuffer` (writable) within the *same* interface is surprising and easy to misuse; treat the method names as load-bearing and prefer the explicit `writable*` / `readOnly*` forms when intent matters.
- Like its siblings, heavy `@Override`-redeclaration for covariant returns and the read-only/writable parallel method families inflate the apparent surface; the genuinely new capability over the parents is just "the buffers can now write back."

## See also

- Read-only twin: [`ReadListenValue`](ReadListenValue.md)
- Read+write, non-observable sibling: [`ReadWriteValue`](ReadWriteValue.md)
- Dependency superset: [`ReadWriteListenDependency`](ReadWriteListenDependency.md) (adds `Dependency`; `asDependency` returns `this`)
- Aspects unioned: [`ReadValue`](../ReadValue.md) · [`WriteValue`](../WriteValue.md) · `ListenValue` (`pile.aspect.listen`, doc pending)
- Package index: [combinations](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md)
- Concepts: [transactions / validity](../../../concepts/transactions.md)
