# `pile.aspect.combinations.WriteDepender`

A depender that can also be written to — unions [`WriteValue`](../WriteValue.md) (write) + [`Depender`](../Depender.md) (depends-on), with no body of its own.

Source folder: `src`. File: `pile/aspect/combinations/WriteDepender.java`.

`WriteDepender<E> extends WriteValue<E>, Depender` and **adds nothing** — no methods, no defaults. It is a pure assembly interface naming the intersection "I can be written, and I depend on other values."

Up: [combinations index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md).

## What the union buys you

The two halves are deliberately the *mutating* sides of the value:

- [`WriteValue`](../WriteValue.md) — the **write** surface: `set` (returns the actually-set value), `setNull`, `transferFrom`, `revalidate`/`permaInvalidate`, the `__endTransaction` hooks. (Itself already a `Consumer` + `DoesTransactions` + `RemembersLastValue`.)
- [`Depender`](../Depender.md) — the **depend-on** surface: `addDependency`/`removeDependency`, `setDependencyEssential`, `getPrivilegedDepender`, `destroy`/`deepDestroy`, and the internal begin/end-changing + deep-revalidate protocol.

So a `WriteDepender` is a handle through which you can both **push a new value in** and **rewire which values this depends on**. Notably it carries **no read aspect** ([`ReadValue`](../ReadValue.md)) and no listen aspect — it is the minimal "mutate the cell and mutate its dependencies" contract. (Contrast `ReadWriteDependency`, which adds read + the *depended-on* `Dependency` side.)

## Who implements it

Two distinct roles, found via `find_references`:

1. **The capstone interface.** [`Pile`](Pile.md) extends `WriteDepender<E>` among its many supertypes, so every full pile is a `WriteDepender`. Here the union is just one of the contracts folded into `Pile`; `PileImpl` provides the behavior.

2. **The sealing escape-hatch proxy — the reason this interface earns its name.** `SealPile.PrivilegedWriteDepender` is an inner class that implements `WriteDepender<E>` **directly**. It is the object returned by both:
   - `Sealable.makeSetter` (as a `WriteValue`, `SealPile.java`, field `privi` at ), and
   - `Depender.getPrivilegedDepender` (it returns `this`, `SealPile.java`).

   A sealed `SealPile` blocks ordinary `set` (it redirects to the seal interceptor) **and** blocks ordinary dependency mutation. The privileged proxy bypasses *both*: its `set` calls `set0` (the un-sealed store path, `SealPile.java`) and its dependency methods call `SealPile.super.*` (the un-overridden `PileImpl` versions, e.g. `addDependency`/`removeDependency`/`setDependencyEssential` at ). That single object needs to expose write **and** depender mutation at once — which is exactly `WriteDepender`. This is the concrete capability the combination enables: **a privileged write+rewire handle obtained before sealing that keeps working after the value is sealed.**

## Salient behavior

- **Empty interface, real meaning elsewhere.** All behavior lives in `WriteValue`/`Depender` and their implementors; read those two docs for method-level contracts (the override maps for `set`, the begin/end-changing protocol, etc.).
- **No read access.** A `WriteDepender` alone can't `get` the value. The `PrivilegedWriteDepender` proxy still delegates a few reads to the outer `SealPile`, but the *interface* makes no read promise.
- **`getPrivilegedDepender` returning the same proxy** means: once you hold the privileged handle, you have a stable object that is simultaneously the privileged setter and the privileged depender for the sealed value.

## Caveats & gotchas

- Obtain the privileged handle **before sealing** — `makeSetter` throws `IllegalStateException` if the value is already sealed. This is the same "get the bypass first" rule documented on [`Depender#getPrivilegedDepender`](../Depender.md) and [`Sealable`](../Sealable.md).
- Because it omits `ReadValue`, don't expect this type to let you observe the value; pair it with a read handle if you need to read.
- The mutating-protocol caveats of both parents apply: `set` may correct/veto/redirect the value (see [`WriteValue` § ways a write can be refused](../WriteValue.md)); the `__`-prefixed and begin/end-changing `Depender` methods are framework-internal "do not call" plumbing.

## Common tasks (how to…)

- **Keep writing and rewiring a value you intend to seal:** call `makeSetter` / `getPrivilegedDepender` **before** `seal`, hold the returned `WriteDepender`, and use it afterward.
- **Treat any `Pile` as a write+depend handle:** every `Pile` already *is* a `WriteDepender`; just narrow the reference.

## Related

- [`WriteValue`](../WriteValue.md), [`Depender`](../Depender.md) — the two unioned aspects.
- [`Sealable`](../Sealable.md) — sealing/redirection and `makeSetter`, the main client of this interface.
- [`Pile`](Pile.md) — the capstone that folds this contract in; `ReadWriteDependency` / [`ReadWriteListenDependency`](ReadWriteListenDependency.md) — neighboring write-side combinations that also carry read/depended-on.
- [combinations index](_index.md) · [aspect index](../_index.md) · [overview](../../../overview.md).
