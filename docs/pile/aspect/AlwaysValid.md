# `pile.aspect.AlwaysValid`

A mixin aspect that supplies "permanently valid" default implementations of [`ReadValue`](ReadValue.md)'s validity- and blocking-read surface, collapsing them all to plain `get`.

Source folder: `src`. File: `pile/aspect/AlwaysValid.java`.

`AlwaysValid<E> extends ReadValue<E>` is a tiny, all-`default` interface — it declares **no abstract methods of its own**. You mix it into a class/interface that already supplies `get` (and, depending on the host, the other still-abstract `ReadValue` members) to assert "this value is never invalid," so the framework can skip blocking and validity bookkeeping. It is the validity half of [`JustReadValue`](JustReadValue.md); see the [overview](../../overview.md) for where the aspect layer sits and [`ReadValue`](ReadValue.md) for the contract being narrowed.

## What it provides (the override map)

Every method here is `@Override default` and trivially derived from `get` or a constant. The point is the *delta* over the `ReadValue` contract: the general contract distinguishes "valid" from "invalid" and lets reads block / observe invalidity; `AlwaysValid` erases that distinction.

| Method | `ReadValue` general contract | `AlwaysValid` collapse |
|---|---|---|
| `getAsync` | snapshot read, no locking, may return `null` if invalid | `get` |
| `getValid` / `getValid(long)` / `getValid(WaitService)` / `getValid(WaitService,long)` | **block** until valid (via `WaitService`), `throws InterruptedException` | `get`, never blocks — see exception note below |
| `getValidOrThrow` | `throws InvalidValueException` if invalid | `get`, never throws |
| `getOldIfInvalid` | returns prior "old" value when invalid | `get` |
| `isValid` | may block/observe invalidity | constant `true` |
| `isValidAsync` | non-blocking status query | `isValid` → `true` |
| `validity` | lazily-init reactive `ReadListenDependencyBool` of observed validity | the shared constant `Piles.TRUE` |
| `isValidNull` | "holds `null` **and** is valid" | `get==null` — valid is a given |
| `nullOrInvalid` | reactive "null-or-invalid" bool | a bare `this::isNull` lambda, non-observable |

Not touched here: `get` stays abstract; transaction/lifecycle methods (`isInTransaction`, `inTransactionValue`, `isDestroyed`, the `__beginTransaction`/`__endTransaction` hooks) are **not** provided — those are filled in separately by [`JustReadValue`](JustReadValue.md) or by the concrete host class.

## Who mixes it in

`find_references` shows exactly three implementors:

- [`JustReadValue<E>`](JustReadValue.md) — the minimal `@FunctionalInterface` read aspect; combines `AlwaysValid` (validity defaults) with its own no-op transaction/lifecycle defaults so a single `get` lambda is a complete `ReadValue`.
- `pile.interop.preferences.PreferencesBackedValue<T>`.
- `pile.interop.preferences.SynchronizingFilesBackedValue<T>`.

The two preferences-backed classes are the **surprising** consumers: they are full `ReadWriteListenValue` + `ListenValue.Managed` (writable *and* listenable), yet they mix in `AlwaysValid` and **deliberately do not implement [`Dependency`](Dependency.md)**. So `AlwaysValid` is not only for read-only stubs — it is the reusable "this value is always valid, so don't expect invalidation/blocking" assertion, here applied to a mutable value whose backing store (a `Preferences` node / a file) is treated as always current. Both offer `asDependency` / `writableValidBuffer_memo` for callers that *do* need a dependency target.

## Salient / surprising behavior

- **Checked exceptions disappear.** `getValid(...)` and `getValidOrThrow` are re-declared without `throws InterruptedException` / `throws InvalidValueException`. A caller holding an `AlwaysValid`-typed reference need not handle those — but a caller holding a plain `ReadValue` reference still must, even though they can never fire here.
- **`get` is the entire story.** Because all blocking/validity reads route to `get`, behavior is fully determined by the host's `get`; there is no waiting, no recompute, no "return old value" path.
- **`validity` is a shared constant.** It returns `Piles.TRUE`, the same singleton for every `AlwaysValid` — fine to observe, but it never changes, so subscribing is pointless.
- **`isValidNull` simplifies cleanly.** Since validity is guaranteed, "valid null" reduces to "is null"; none of `ReadValue`'s "null might mean invalid" ambiguity applies.

## Caveats & gotchas

- **No transaction / lifecycle defaults.** `AlwaysValid` alone does *not* make a class compile as a `ReadValue` — `get`, `isInTransaction`, `inTransactionValue`, `isDestroyed`, and the transaction-driver hooks remain to be supplied by the host (`JustReadValue` does this; the preferences classes implement them via their full `ReadWriteListenValue` plumbing).
- **`get` re-evaluated on every delegating call.** `getValid*`, `getAsync`, `getOldIfInvalid`, `isValidNull`, `isNull` each call `get`; if the host's supplier is expensive or non-idempotent, this matters.
- **`nullOrInvalid` returns a non-observable lambda** — consistent with `ReadValue`'s note that the bare boolean forms are not watchable.
- **"Always valid" is a promise the mixer must keep.** Mixing this in asserts the value is never invalid; if the host's `get` can actually fail or be stale (e.g. a missing preferences key), that contradiction is silent — consumers will never see invalidity.

## Common tasks (how to…)

- **Make a read-only `Supplier` into a complete reactive value:** don't use `AlwaysValid` directly — use [`JustReadValue`](JustReadValue.md) / `ReadValue.wrap(supplier)`, which already combines it with the transaction stubs.
- **Give a writable/listenable value "always valid" semantics without making it a `Dependency`:** implement your read/write/listen surface and add `AlwaysValid<E>` to the `implements` list, as the preferences-backed values do.
- **Expose a dependency target for such a value anyway:** wrap it (`PreferencesBackedValue.asDependency` → `writableValidBuffer_memo`, `PreferencesBackedValue.java`).

## Tech debt / warts

- The interface carries **no Javadoc at all** — every method is an undocumented one-liner; the contract is entirely inherited from [`ReadValue`](ReadValue.md). This is in line with the project-wide note on unsystematic API docs (see [overview § caveats](../../overview.md)).
- Its semantics are an inherited wart shared with `JustReadValue`: it satisfies the rich `ReadValue` API by collapsing meaningful distinctions (blocking, observability of invalidity) to no-ops, which can surprise callers who hold a `ReadValue`-typed reference and expect real validity behavior.

## Related

- [`JustReadValue`](JustReadValue.md) — extends `AlwaysValid`, adds the transaction/lifecycle stubs; the canonical consumer.
- [`ReadValue`](ReadValue.md) — the full read contract whose validity/blocking surface this collapses.
- [`Dependency`](Dependency.md) — the dependency-target aspect that the `AlwaysValid` consumers deliberately do **not** implement.
- [overview.md](../../overview.md) — architecture map and the valid/invalid mental model.
