# `pile.aspect.JustReadValue`

Source folder: `src`. File: `pile/aspect/JustReadValue.java`.

The **minimal read aspect**: a `@FunctionalInterface` that lets you turn a single `get` into a fully-functional [`ReadValue`](ReadValue.md) by filling in no-op / always-valid defaults for everything else. It is the "nothing fancy" implementation mentioned in [`ReadValue`](ReadValue.md) — a readable value that **neither supports the `ListenValue` aspect, nor `Dependency`, nor transactions, nor any other machinery**.

See the [overview](../../overview.md) for where this sits in the aspect layer, and [`ReadValue.md`](ReadValue.md) for the full read contract this narrows.

## What it provides

`JustReadValue<E> extends ReadValue<E>, AlwaysValid<E>`. Because it is a `@FunctionalInterface`, the **only abstract method left is `ReadValue.get`** — supply that one lambda and you have a complete read-only reactive value. All the "fancy" behavior of `ReadValue` is stubbed out across two interfaces:

- From `AlwaysValid<E>` (the sibling super-interface, `pile/aspect/AlwaysValid.java`): the value is **permanently valid**. `isValid`/`isValidAsync` return `true`, `validity` returns the constant `Piles.TRUE`, and every blocking/validity-aware read (`getAsync`, `getValid`, `getValid(...)`, `getValidOrThrow`, `getOldIfInvalid`) collapses to plain `get`. `nullOrInvalid` reduces to a bare `this::isNull` lambda.
- From `JustReadValue` itself: the **transaction and lifecycle** surface is stubbed (see below).

So a `JustReadValue` is effectively *a `Supplier` dressed up as a reactive value that is always valid and never in transaction*. This is exactly what [`ReadValue.wrap(Supplier)`](ReadValue.md) produces.

## Default methods

All five are `@Override default` and trivial:

- `isInTransaction` → `false` — never in a transaction.
- `inTransactionValue` → `Piles.FALSE` — the reactive `ReadListenDependencyBool` mirror, pinned to the constant `false`.
- `isDestroyed` → `false` — the value is never considered destroyed.
- `__beginTransaction(boolean)` → `{}` (no-op).
- `__endTransaction` → `{}` (no-op).

The two `__`-prefixed methods are the transaction-driver hooks from `DoesTransactions` (which `ReadValue` extends); stubbing them to no-ops is what makes "no transaction machinery" concrete.

## How it relates to `ReadValue`

[`ReadValue`](ReadValue.md) is the **full read contract** — it distinguishes reads along three axes (block-until-valid, observe-invalidity side effects, record-as-dependency), exposes reactive validity/null predicates, blocking gets with a `WaitService`, `doOnceWhenValid`, etc. `JustReadValue` keeps the *same interface* but **degenerates every one of those distinctions** because its value can never be invalid and never transacts:

| Concern | `ReadValue` (general) | `JustReadValue` |
|---|---|---|
| Validity | may be valid/invalid; reads can *observe* invalidity | always valid |
| Blocking gets | really wait via `WaitService` | immediate `get` |
| `get` side effects | may record a dependency, may trigger recompute | none beyond your lambda |
| Transactions | driven by `DoesTransactions`/ARLD | always "not in transaction" |
| Lifecycle | can be destroyed | never destroyed |

It does **not** implement [`Dependency`](Dependency.md), `Depender`, or `ListenValue`, so a `JustReadValue` cannot be a dependency target, cannot be depended on reactively, and cannot be listened to — consumers only ever pull via `get`.

## Salient behavior

- **One abstract method only.** Being a `@FunctionalInterface`, `JustReadValue` is meant to be created as a lambda over `get` (this is how `ReadValue.wrap` builds one, `ReadValue.java`).
- **Always valid means `get` is the whole story.** All the side-effecting / blocking reads of `ReadValue` route to `get` here, so behavior is entirely determined by the supplied supplier.
- **`null` is unambiguous-ish.** Since the value is always valid, a `null` from `get` means a *valid null* (`isValidNull` is `get==null`, `AlwaysValid.java`) — none of `ReadValue`'s "null could mean invalid" ambiguity applies.

## Caveats

- **Not observable / not a dependency.** Despite being a "reactive value", a `JustReadValue` participates in no graph: no listeners, no invalidation, no recomputation. If you need other values to react to it, you need a real `Pile`, not this.
- **`get` is re-evaluated every call** through all the delegating defaults; if your supplier is expensive or non-idempotent, note that `getValid*`, `getAsync`, `getOldIfInvalid`, `isValidNull`, `isNull`, etc. each call it.
- **`nullOrInvalid` returns a non-observable lambda** — consistent with `ReadValue`'s note that the bare boolean forms here are not watchable.

## Common tasks (how to…)

- **Wrap a plain `Supplier` as a read-only reactive value:** `ReadValue.wrap(supplier)` (returns a `JustReadValue`, `ReadValue.java`), or write the lambda directly: `JustReadValue<String> v =  -> "hi";`.
- **Expose a constant/derived read where no reactivity is needed:** implement `get` only and let the defaults handle the rest.

## Tech debt / warts

- The interface is intentionally minimal and carries no `TODO`s of its own. Its main wart is **inherited**: it satisfies the rich `ReadValue` API by collapsing meaningful distinctions to no-ops, which can surprise callers who expect, e.g., blocking or observability from a `ReadValue`-typed reference.
- Javadoc is sparse (a class comment plus an undocumented `@param <E>`, `JustReadValue.java`), in line with the project-wide note on unsystematic API docs (see [overview § caveats](../../overview.md)).

## Related

- [`ReadValue`](ReadValue.md) — the full read contract this narrows; `JustReadValue` is its minimal implementation.
- `AlwaysValid` (`pile/aspect/AlwaysValid.java`) — the sibling super-interface supplying the "permanently valid" defaults (not yet separately documented).
- [`Dependency`](Dependency.md) — the dependency-target aspect that `JustReadValue` deliberately does **not** implement.
- [overview.md](../../overview.md) — architecture map and the valid/invalid mental model.
