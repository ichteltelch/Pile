# `pile.aspect.combinations` — package index (Tier 1)

Source folder: `src` (all interfaces below).

The **assembled contracts**: each combination interface unions several granular [aspects](../_index.md) into a usable contract, occasionally adding methods that only make sense for the combination. Concrete values (`pile.impl`) implement these.

Up: [aspect index](../_index.md) · [overview](../../../overview.md). Model behind recompute/transactions: [concepts/transactions.md](../../../concepts/transactions.md).

## The four dimensions
Combinations are points in a lattice over four capabilities: **Read** ([`ReadValue`](../ReadValue.md)), **Write** ([`WriteValue`](../WriteValue.md)), **Listen** (observe — `ListenValue`), **Dependency** (be depended on — [`Dependency`](../Dependency.md)). [`Pile`](Pile.md) sits at the top and additionally adds recomputation/transactions/transform/seal.

## Capstone
- [`Pile`](Pile.md) — the library namesake: full reactive value bundling read/write/listen/dependency **plus** recompute, transaction, transform, seal. Thin assembly interface; real behavior in `PileImpl`. Recompute/transaction model → [concepts/transactions.md](../../../concepts/transactions.md).

## Dependency-side (can be depended on)
- [`ReadDependency`](ReadDependency.md) — read + depend-on (`ReadValue` + `Dependency`); carries the big `map*`/`field*`/comparison/`readOnly`/`overridable` default-method factory surface that builds derived `SealPile`s.
- [`ReadListenDependency`](ReadListenDependency.md) — read + observe + depend-on (adds `ListenValue` + `HasBrackets`); the middle rung `ReadDependency` → this → `Pile`.
- [`ReadWriteDependency`](ReadWriteDependency.md) — read + write + depend-on (no listen); `ReadWriteValue` + `ReadDependency`, adding the two-way `biject*` family.
- [`ReadWriteListenDependency`](ReadWriteListenDependency.md) — the full non-recompute contract: read + write + observe + depend-on; `Pile` adds recomputation on top.
- [`TransformableDependency`](TransformableDependency.md) — a dependency supporting the (rudimentary) transform mechanism; thin union, real protocol in `TransformableValue`/`PileImpl`.
- [`WriteDepender`](WriteDepender.md) — a depender that can also be written (`WriteValue` + `Depender`); key implementor is `SealPile`'s privileged seal-bypass proxy.

## Value-side (not a graph dependency)
- [`ReadWriteValue`](ReadWriteValue.md) — read + write only, deliberately **not** observable and **not** a dependency; smallest read/write union (canonically `MutRef`).
- [`ReadListenValue`](ReadListenValue.md) — read + observe (listen), no write/dependency; `asDependency` default returns a memoized buffered copy (subtype `ReadListenDependency` overrides to `return this`).
- [`ReadWriteListenValue`](ReadWriteListenValue.md) — read + write + observe, no dependency; the writable twin of `ReadListenValue` (overrides the buffer family to writable variants).

## Plain interop
- [`Prosumer`](Prosumer.md) — marker unioning `Supplier` + `Consumer` (`get`/`accept`); a non-reactive get/set handle, super-interface of `ReadWriteValue` and `LastValueRememberer`.

## Misc
- [`WriteElsewherePile`](WriteElsewherePile.md) — vestigial, entirely commented-out combination marrying `Pile` to the dead `WriteElsewhere` deferred-write idea; currently inert.
