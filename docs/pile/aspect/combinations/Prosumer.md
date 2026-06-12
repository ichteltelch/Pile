# `pile.aspect.combinations.Prosumer`

Marker interface unioning `java.util.function.Supplier` and `Consumer` for the same type — something you can both read (`get`) and write (`accept(E)`).

Source folder: `src`. File: `pile/aspect/combinations/Prosumer.java`.

## What it is for

`Prosumer<E>` (= **pro**ducer + con**sumer**) is the minimal "both directions" handle: `interface Prosumer<E> extends Supplier<E>, Consumer<E>`. It declares **no methods of its own** — the body is empty. Its entire value is naming the union, so a single object can be passed wherever code needs *both* a get-side and a set-side for one value, instead of threading a separate `Supplier` and `Consumer` around.

It lives in `pile.aspect.combinations` alongside the heavyweight reactive contracts (`ReadDependency` → `ReadListenDependency` → [`Pile`](_index.md)), but it is far lighter than those: it is **not** a reactive aspect. It carries no validity, no transactions, no dependency graph — just plain JDK functional reads and writes. Contrast with [`ReadValue`](../ReadValue.md) / [`WriteValue`](../WriteValue.md), the reactive read/write aspects, which add validity-awareness, dependency recording, correction/veto/sealing, etc.

## Relationship to `java.util.function`

Direct: it *is* a `Supplier<E>` and a `Consumer<E>`, nothing more. So:
- `get` comes from `Supplier` — read the value.
- `accept(E)` comes from `Consumer` — write the value.

Any lambda pair can be lifted into one via [`LastValueRememberer.make(Supplier, Consumer)`](../LastValueRememberer.md) — see below.

## Who implements it

Two distinct uses in the library:

1. **As a super-interface of the reactive read/write contract.** [`ReadWriteValue`](ReadWriteValue.md) `extends ReadValue<E>, WriteValue<E>, Prosumer<E>`. This is how every full reactive value (`PileImpl`, `SealPile`, the [`Pile`](_index.md) capstone, the specialized `…combinations` hierarchies) also satisfies plain `Supplier`/`Consumer` — handy for interop with non-Pile code. `ReadValue` already `extends Supplier` on its own, so `Prosumer` mainly contributes the `Consumer`/`accept` side here. (Note: `WriteValue.set(E)` *returns* the actually-set value, whereas `Consumer.accept` is `void`; `Prosumer` only promises the `void accept`.)

2. **As a lightweight standalone read+write handle**, independent of the reactive machinery:
   - [`LastValueRememberer`](../LastValueRememberer.md) `extends Prosumer<E>` and **maps the `Prosumer` methods onto its own domain pair** via defaults: `accept(v)` → `storeLastValue(v)`, `get` → `recallLastValue`. It also offers `Prosumer`-based factories: `of(Prosumer)` wraps an existing prosumer, and `make(Supplier, Consumer)` builds one from a lambda pair.
   - `MutBool` and `EarlyMutRef` (`impl/EarlyMutRef.java`) — tiny mutable boxes that implement `Prosumer<Boolean>` / `Prosumer<E>` to expose their public field as a get/set pair without dragging in the reactive stack.

## What it adds

Nothing behavioral — it is a pure **named union** (a "conjunction" marker). All semantics come from `Supplier.get` and `Consumer.accept`. Implementors that want richer meaning (reactive set with correction/veto, or store/recall semantics) layer that on themselves; `Prosumer` does not constrain it.

## Caveats & gotchas

- **Not reactive.** Despite living next to `Pile`, `Prosumer` has no validity/transaction/dependency semantics. Don't assume a `Prosumer` you receive participates in the dependency graph — it may be a `MutBool` field box or a `make(get, set)` lambda pair.
- **`accept` vs `set`.** Through the `Prosumer`/`Consumer` view, a write is `void accept`. The same object reached as a [`WriteValue`](../WriteValue.md) exposes `set`, which can correct/veto/redirect the value and returns the actually-stored result; that information is lost through the `Prosumer` lens.
- **Empty interface, no `@FunctionalInterface`.** It declares two abstract methods (inherited from its two parents), so it is *not* a single-method functional interface and cannot be written as one lambda; use `LastValueRememberer.make`/`of` (or an anonymous class) to build one from pieces.

## Common tasks

- **Pass read+write together:** accept a `Prosumer<E>` parameter instead of a separate `Supplier<E>` and `Consumer<E>`.
- **Build one from a lambda pair:** `LastValueRememberer.make(getter, setter)` (returns a `LastValueRememberer`, which *is* a `Prosumer`).
- **Treat a reactive value as plain get/set:** any [`ReadWriteValue`](ReadWriteValue.md) (hence any `Pile`) already is a `Prosumer<E>`; just use it where one is expected.

## Related

- [`ReadWriteValue`](ReadWriteValue.md) — the reactive read+write combination that also extends `Prosumer`.
- [`ReadValue`](../ReadValue.md) · [`WriteValue`](../WriteValue.md) — the reactive read/write aspects (validity-aware), the heavyweight counterparts.
- [`LastValueRememberer`](../LastValueRememberer.md) — store/recall strategy built on `Prosumer`.
- Package index: [`aspect/_index.md`](../_index.md) · Overview: [overview](../../../overview.md).
