# Pile — documentation overview

Pile is a **reactive-values framework** (Java 8, no external dependencies): wrappers around plain values that can depend on one another and recompute when their dependencies change. It is developed by Promadent and is open source.

This file is the entry point. It describes the **source-folder layout**, the **package/architecture map**, and a **reading guide** pointing to the per-topic docs. Read it first, then jump to the specific doc for the area you are touching.

> Authoritative source is always the code + the project `README.md` (a thorough conceptual tour). These docs add a navigable map, verified internals, caveats, and task recipes. Where a doc states non-obvious runtime behavior, it cites `File.java:line`.

## Source folders

The Pile Eclipse project has four source folders on the build path (`.classpath`), plus one off-path variant. **Package paths alone do not tell you which source folder a file is in — these docs name the source folder whenever it is not `src`.**

| Source folder | Role |
|---|---|
| `src` | The framework itself. Everything below unless noted otherwise. |
| `tests` | Tests (our characterization/unit tests go here). |
| `examples` | Runnable usage examples (e.g. `pile.examples.Basic`). |
| `debug` | **On the build path.** Holds the single class `pile.interop.debug.DebugEnabled` with debugging flags. |
| `debug_off` | **Off the build path.** A drop-in twin of `DebugEnabled` with the flags compiled to `false`. Swap `debug`↔`debug_off` on the build path to toggle debugging. |

**Caveat (source-folder split):** the package `pile.interop.debug` is *not* under `src` — it lives in `debug` (or `debug_off`). Its `pile.interop.*` siblings (`preferences`, `wait`, `exec`) are under `src`. The debug flags are `static final boolean`s exploited for conditional compilation, so **changing a flag requires recompiling the library** (see `README.md`).

## Architecture map (packages)

Pile is layered: granular capability interfaces → assembled contracts → concrete implementations → builders, with primitive specializations and cross-cutting services alongside. All under `src` unless noted.

- **`pile.aspect`** — granular *aspect* interfaces, one capability each: `ReadValue`, `WriteValue`, `Dependency`, `Depender`, `DoesTransactions`, `Sealable`, `CorrigibleValue`, `HasAssociations`, `RemembersLastValue`, `AutoValidationSuppressible`, `LazyValidatable`, … Sub-packages: `listen` (`ValueListener`/`ValueEvent`), `recompute` (`Recomputation`/`Recomputer`), `transform`, `bracket` (`ValueBracket`), `suppress` (`Suppressor`), `combinations`, `limitedresource`.
- **`pile.aspect.combinations`** — aspects assembled into usable contracts: `ReadDependency` → `ReadListenDependency` → `ReadWriteListenDependency`, the capstone **`Pile<E>`**, and `Prosumer`.
- **`pile.impl`** — general-purpose implementations: `PileImpl` (full `Pile`), `SealPile` (+ sealable), `Constant`, `Independent`, the shared base `AbstractReadListenDependency`, the composite family (`PileList`, `PileCompound`, `Hub`, `MutRef`), and the static utility hub **`Piles`** (~2,900 lines) — see [Piles/_index.md](pile/impl/Piles/_index.md). **Note:** `Piles` holds the *type-agnostic / cross-type* factories (`Constant`/`Independent`, buffers, `validBuffer`, `rateLimited`, `deref`, `readOnlyWrapper`, `firstValid`/`fallback`, the aggregation monoids, deep-revalidate helpers). The per-type operators (`not`, `field`, `choose`, `sum`/`product`, comparisons) live on the typed aspect classes instead (`ReadDependency`, `PileBool`, `PileInt`, …).
- **`pile.builder`** — fluent builders for `Pile`s/`Independent`s (dynamic-dependency recording, threaded/delayed recomputation).
- **`pile.specialized_{bool,int,double,String,Comparable}`** (+ each `.combinations`) — parallel hierarchies (`PileBool`, `PileInt`, …) adding type-specific methods Java's lack of extension methods otherwise forbids. Documented as an **exemplar + deltas**: [`specialized_bool`](pile/specialized_bool/_index.md) is the fully-worked family ([`PileBool`](pile/specialized_bool/PileBool.md) operator algebra, [combinations map](pile/specialized_bool/combinations/_index.md)); [`int`](pile/specialized_int/_index.md), [`double`](pile/specialized_double/_index.md), [`String`](pile/specialized_String/_index.md), [`Comparable`](pile/specialized_Comparable/_index.md) are delta-indexes over it.
- **`pile.relation`** — constraints not expressible as plain dependencies (equality, one-of-N booleans, material implication). See [relation index](pile/relation/_index.md).
- **`pile.interop`** — injectable host services: `wait` (`WaitService`), `exec` (`StandardExecutors`), `preferences`, and `debug` (`DebugEnabled`, in the `debug`/`debug_off` source folder). See [interop index](pile/interop/_index.md).
- **`pile.utils`** (+ `defer`) — helpers (`SequentialQueue`, weak-reference cleanup, deferral). See [utils index](pile/utils/_index.md).

## Core mental model (one paragraph)

A reactive value wraps a plain value and may be **valid** or **invalid**. Values that can be depended on implement `Dependency`; values that depend on others implement `Depender`; when a dependency changes, its dependers are invalidated. A `Pile` recomputes (via its `Recomputer`, handed a `Recomputation`) roughly when it is invalid, all dependencies are valid, auto-validation isn't suppressed, and it isn't mid-transaction. **Transactions** are an in-flux state that suppresses recomputation and remembers the pre-transaction value; they are opened both internally (recomputation pending/ongoing, a dependency changing) and by client code (to batch related writes). See [concepts/transactions.md](concepts/transactions.md).

## Design philosophy / non-goals

Pile is deliberately **not** built on incremental-computation theory (self-adjusting computation, Adapton, Jane Street's `Incremental`, `salsa`). Those systems earn provably-efficient, glitch-free change propagation by assuming **referential transparency** — pure functions over immutable values in a one-way DAG. Pile spends that assumption to buy *hackability* instead:

- bidirectional, **redirected writes** (sealing — writing `not(x)` writes `!` into `x`);
- manually overriding a **computed** node and keeping the manual value (setting despite invalid dependencies + the deep-revalidate registry — see [concepts/transactions.md](concepts/transactions.md));
- **side effects bound to a value's tenure** (brackets), in-place mutation, corrections/vetoes, suppressible automatic behavior.

Each is something a principled incremental core would forbid. The diamond/glitch consistency problem is still solved — **operationally**, via transaction propagation, not by construction. The cost (which the `README` owns) is efficiency and no formal optimality guarantee. The target is a few hundred values at interactive speed, where flexibility and debuggability matter more than asymptotics.

## Reading guide — which doc for what

- **Transactions, validity propagation, the diamond, "write despite invalid dependencies", deep-revalidate** → [concepts/transactions.md](concepts/transactions.md) and the [deep-revalidation detail](pile/impl/Piles/deep-revalidation.md).
- **The `Piles` utility catalogue** → [Piles/_index.md](pile/impl/Piles/_index.md), with detail docs for [buffers](pile/impl/Piles/buffers.md), [aggregation](pile/impl/Piles/aggregation.md), and [deep-revalidation](pile/impl/Piles/deep-revalidation.md).
- **Type-specific values & operators** → the [`specialized_bool` exemplar](pile/specialized_bool/_index.md) and its sibling delta-indexes (int/double/String/Comparable).
- **Injectable host services / weak-ref & deferral helpers** → [interop](pile/interop/_index.md), [utils](pile/utils/_index.md).
- **Constraints between values** → [relation index](pile/relation/_index.md).
- *(to be written as we touch them)* recomputation & dynamic dependencies; sealing & redirection; brackets; builders (the `pile.builder` docs exist per-unit but lack a concept doc).

> **Layout note / open convention:** cross-cutting architectural concepts that span several packages (like transactions, implemented across `pile.aspect` and `pile.impl`) are documented under `docs/concepts/`. Per-class / per-package docs mirror the package tree under `docs/pile/...`. If you'd prefer cross-cutting concepts filed under their primary package instead, say so and these will move.

> **Suspected bugs** found while documenting are logged in [possible-bugs.md](possible-bugs.md) (unverified — for the developer to judge).

## Caveats & technical debt (project-wide, from `README.md` + code)

- Built for flexibility/debuggability/safety, **not** speed ("OK for a few hundred Piles at interactive speeds", `PileImpl`). Don't assume it scales to tight loops.
- Some API names are admittedly unsystematic; the author may change them.
- *Lazy validation* and *transformation* are explicitly described as immature/rudimentary.
- Toggling debug flags requires recompiling (conditional-compilation design).
