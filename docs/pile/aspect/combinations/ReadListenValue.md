# `pile.aspect.combinations.ReadListenValue`

The combination contract for a value you can **read** and **observe (listen to)** — but **not** write and **not** depend on as a graph node — `ReadValue` ∪ `ListenValue`.

Source folder: `src`. File: `pile/aspect/combinations/ReadListenValue.java`.

This is the **value-side** observable read contract (not a `Dependency`). It sits below its dependency-carrying subtype [`ReadListenDependency`](ReadListenDependency.md) and beside its writable subtype [`ReadWriteListenValue`](ReadWriteListenValue.md). See the [combinations index](_index.md), the [aspect index](../_index.md), and the [overview](../../../overview.md) for where the aspects it unions live.

## What it is for

`ReadListenValue<E>` is what a reactive value satisfies once it can be **read** (the validity-aware [`ReadValue`](../ReadValue.md) surface) **and observed** (the `ListenValue` listener surface — `addValueListener`/`fireValueChange`/`ValueEvent`/`ValueListener`). Crucially it adds **neither** write access **nor** the [`Dependency`](../Dependency.md) graph-target surface. That makes it the natural type for "an observable read-only handle on a value" where the value should not be wired into the dependency graph directly.

It also layers a family of **convenience recipes** on top of plain read+listen:
- `await(...)` / `runWhen(...)` — block or fire once on a condition, re-checked on every change.
- the **buffer / valid-buffer / weak-buffer / rate-limited** factory surface that materializes derived `SealPile`/`Independent` copies.
- `asDependency` — promote this value into something depend-able.

## Aspects it unions

```
ReadListenValue<E>
 ├─ ReadValue<E>     ← validity-aware reads (get/getValid/isValid/validity/…)
 └─ ListenValue      ← listener registration + firing (addValueListener/fireValueChange/…)
```
(declaration at `ReadListenValue.java`.) `ListenValue` lives in `pile.aspect.listen` *(doc pending)*. See [`ReadValue`](../ReadValue.md) for the (large, side-effect-laden) read surface this inherits.

## Methods it adds (delta over its supertypes)

### Promotion to a dependency
- `asDependency` — **default returns `validBuffer_memo`**, i.e. a *memoized buffered copy* of this value, **not `this`**. Because a bare `ReadListenValue` is **not** a `Dependency`, it cannot hand itself out as a dependency target; instead it materializes (and memoizes) an `Independent` valid-buffer that mirrors it and *is* depend-able. **This is the key distinction from the subtype:** [`ReadListenDependency`](ReadListenDependency.md) overrides `asDependency` to `return this`, since it already is a `Dependency`. Likewise [`ReadWriteListenValue`](ReadWriteListenValue.md) keeps the buffer behavior but returns a writable dependency. **If you call `asDependency` polymorphically, the runtime type decides whether you get `this` or a fresh buffer.**

### Memoized valid buffers
- `validBuffer_memo` — **default delegates to `readOnlyValidBuffer_memo`**. The seam exists so that `ReadWriteListenValue` can re-point it at the *writable* memoized buffer. (See the gotcha below about that override.)
- `readOnlyValidBuffer_memo` — retrieve-or-make a **memoized**, read-only `Independent` valid-buffer, keyed on `this` in the static `READ_ONLY_VALID_BUFFER_CACHE` (an `IdentitiyMemoCache`, `ReadListenValue.java`, ). Memoization means repeated calls return the **same** buffer instance.
- The Javadoc lists when **not** to use memoization: if you will give the buffer a debug name/owner, add never-removed `ValueListener`s, want it to act as its own event-firing entity, or `this` is only referenced locally (the cache entry would never be reused). In those cases use the non-`_memo` builder variants.

### Buffer / weak-buffer / rate-limited factories (non-memoized)
All of these build a **fresh** derived value each call and route through `Piles` builders:
- `validBuffer` / `validBufferBuilder` / `readOnlyValidBuffer` / `readOnlyValidBufferBuilder` — an `Independent<E>` that holds the latest **valid** value of this value (`ReadListenValue.java`; builder is `Piles.ib.setupValidBuffer(this)`).
- `buffer` / `readOnlyBuffer` (+ `*Builder`) — a `SealPile<E>` buffer (`ReadListenValue.java`; `Piles.sb.setupBuffer(this)`).
- `weakBuffer` / `readOnlyWeakBuffer` (+ `*Builder`) — a weakly-held buffer (`ReadListenValue.java`; `setupWeakBuffer`).
- `rateLimited(coldStartTime, coolDownTime)` / `readOnlyRateLimited(...)` (+ `*Builder`) — a `SealPile<E>` that throttles change propagation (`ReadListenValue.java`; `setupRateLimited`).

The unprefixed `validBuffer`/`buffer`/`weakBuffer`/`rateLimited` are the **dispatch seams**: their defaults delegate to the `readOnly*` variants here, and [`ReadWriteListenValue`](ReadWriteListenValue.md) overrides them to delegate to `writable*` variants instead.

### Observation conveniences
- `runWhen(BooleanSupplier cond, Runnable run)` — install a `ValueListener` that, the **first** time `cond` becomes true, removes itself and runs `run`. One-shot, self-removing.
- `await(BooleanSupplier)` / `await(BooleanSupplier, long millis)` and their `WaitService`-taking cores — block the calling thread until a condition holds (or a timeout elapses); the condition is re-checked on every change **and** periodically with a slow period. The no-`WaitService` overloads delegate via `WaitService.get`; the `WaitService` overloads are abstract. The `millis`-bounded one returns the condition's final state. All `throws InterruptedException`.

### Misc declared members
- `willNeverChange` — abstract; true when this value is guaranteed never to change (e.g. a `Constant`, or a `Sealable` sealed with the default interceptor and no recomputer).
- `dependencyName` — abstract; the `Dependency`-style name. Mirrors `Dependency#dependencyName` even though this type is not itself a `Dependency`.
- `_getEquivalence` — abstract; the `BiPredicate` equivalence relation used to decide whether a wrapped-value change really counts as a change.

## Override map

- **Listener / await machinery** (`addValueListener`, `fireValueChange`, `await(WaitService,…)`, `dependencyName`, `_getEquivalence`) is implemented once for concrete piles in `pile.impl.AbstractReadListenDependency` (ARLD) — the same base that backs [`ReadListenDependency`](ReadListenDependency.md). So this interface contributes contract + recipes; ARLD contributes behavior. *(ARLD doc pending — do not deep-read it just for this.)*
- **`asDependency`** — `return this` in `ReadListenDependency`; returns a writable buffer in `ReadWriteListenValue`. Only the bare-`ReadListenValue` case hands out a *read-only* memoized buffer.
- **The `validBuffer`/`buffer`/`weakBuffer`/`rateLimited` dispatch seams** are overridden in [`ReadWriteListenValue`](ReadWriteListenValue.md) to delegate to the `writable*` variants.
- **Specialized parallels:** `ReadListenValueBool`/`…Int`/`…Double`/`…String`/`…Comparable` (e.g. `ReadListenValueComparable.java`) extend this to add type-specific surface; the dependency-side `ReadListenDependency*` types extend it transitively via `ReadListenDependency`.

## Salient / surprising behavior

- **`asDependency` materializes a buffer here, but returns `this` in the dependency subtype.** This is the single most important behavioral fact about this interface. The buffer is *memoized*, so repeated `asDependency` calls on the same value reuse one buffer.
- **`_memo` variants are identity-cached; the others are not.** `readOnlyValidBuffer_memo` returns the same `Independent` every time (static `IdentitiyMemoCache` keyed on `this`); `readOnlyValidBuffer`/`buffer`/`weakBuffer`/`rateLimited` build a brand-new value each call.
- **Don't memoize when you'll mutate the buffer's identity.** Adding never-removed listeners, naming it, or treating it as an independent event source all violate the memoization assumptions spelled out at `ReadListenValue.java` — use the non-`_memo` builders there.
- **`await`/`runWhen` re-check on every change *and* on a slow poll.** Even a condition that depends on something other than this value's change events will eventually be re-evaluated by the periodic re-check.

## Caveats & gotchas

- **`ReadWriteListenValue`'s `validBuffer_memo` override looks like a copy-paste slip:** its Javadoc says "Delegates to `writableValidBuffer_memo`", but the body actually calls `readOnlyValidBuffer_memo` — identical to this interface's default. So on a writable value, `validBuffer_memo` (and therefore `asDependency`) appears to hand back a **read-only** memoized buffer despite the surrounding writable-buffer intent. Treat the writable-buffer-via-`asDependency` path with suspicion and prefer `writableValidBuffer`/`writableValidBuffer_memo` explicitly if you need writability. (Flagged as tech debt, not corrected here.)
- **This type is not a `Dependency`.** You cannot wire a bare `ReadListenValue` into the graph as a dependency target — that's exactly why `asDependency` exists and why it buffers. If you have a `ReadListenDependency`, you already have the graph surface.
- **Inherited read side effects still apply.** Everything from [`ReadValue`](../ReadValue.md) carries over: `get`/`isValid` can observe invalidity and record dependencies; `null` is overloaded between "valid null" and "invalid". See that doc.
- **`willNeverChange` is a hint, not a hard guarantee across all implementors** — it is true for `Constant` and appropriately-sealed values; don't assume an arbitrary implementor computes it conservatively.

## Common tasks (how to…)

- **Treat an observable read-only value as a dependency target:** `v.asDependency` — gives a memoized buffered `Independent` (or `this` if `v` is actually a `ReadListenDependency`).
- **Get a stable, valid-only snapshot value:** `v.readOnlyValidBuffer_memo` (memoized) or `v.readOnlyValidBuffer` (fresh).
- **Throttle a chatty value:** `v.rateLimited(coldStartTime, coolDownTime)`.
- **Run something once when a condition first holds:** `v.runWhen(cond, run)`.
- **Block until a condition holds (optionally bounded):** `v.await(cond)` / `v.await(cond, millis)` (handle `InterruptedException`).
- **Buffer with weak holding:** `v.readOnlyWeakBuffer`.

## Tech debt / warts

- The `validBuffer_memo` override in `ReadWriteListenValue` (Javadoc vs body mismatch, see gotchas) is a likely bug or stale copy-paste.
- The buffer/rate-limited surface is heavily overloaded (`validBuffer`/`readOnlyValidBuffer`/`validBuffer_memo`/`readOnlyValidBuffer_memo` × `Builder`), reflecting the project's acknowledged unsystematic-naming caveat (see [overview § caveats](../../../overview.md)).
- Several `@return`/`@param` Javadoc tags here are empty.

## Related

- [`ReadListenDependency`](ReadListenDependency.md) — this **plus** `Dependency` + `HasBrackets`; overrides `asDependency` to `return this`.
- [`ReadWriteListenValue`](ReadWriteListenValue.md) — this **plus** write access; re-points the buffer seams at `writable*` variants.
- [`ReadValue`](../ReadValue.md) — the read aspect unioned here (the bulk of the inherited surface). The listen aspect (`pile.aspect.listen.ListenValue`) doc is pending.
- [combinations index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md) · [concepts/](../../../concepts/) (e.g. [transactions](../../../concepts/transactions.md) for the validity model behind valid-buffers).
