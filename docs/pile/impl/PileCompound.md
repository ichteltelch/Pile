# `pile.impl.PileCompound`

Abstract base for a reactive structure built from several `PileImpl` components, fronted by a single dependency-aggregating **head** value.

Source folder: `src`. File: `pile/impl/PileCompound.java` (~130 lines).

`PileCompound` is *not itself a `Pile`* — it is a plain abstract class (`extends Object`) that bundles a group of related reactive values and gives them a shared **head**: one `PileImpl<Object>` (`head`) that the subclass makes depend on all the components. Because the head depends on every component, it becomes a single point through which the whole structure can be observed: any component change invalidates/changes the head, and the head re-fires the components' [`ValueEvent`](../aspect/listen/...)s. It is the common superclass of the list family — [`AbstractValueList`](AbstractValueList.md) `extends PileCompound`, and `PileList` sits under that. See the [impl index](_index.md) and the [overview](../../overview.md).

## What it compounds (the head pattern)

- A subclass declares its components as fields (e.g. several `PileImpl`s) and, in an instance initializer/constructor, calls `head.addDependency(component1, component2, …)` to wire the head to them. See the `CompoundTest.Dodo` example.
- The head is created eagerly in a field initializer: `head = makeHead`. `head` returns it — **protected by default**; override only to widen visibility (see `PublicHead` below).
- The head's job is purely structural aggregation; it carries a **dummy value** (`"dummy"`, ) — its *value* is meaningless, only its validity/change/event stream matters.

## Head construction

`makeHead` → `makeHead(false)` builds a [`Hub`](Hub.md)-based head and wires the subclass hooks onto it:
- `recompute` = `this::recomputeHead` → by default fulfills the dummy value.
- a value listener = `this::headFiresChange`.
- a validity listener that calls `headInvalidated` when the head's `validity` goes false.
- sets `head.owner = this` and `head.avName = autoCompundName`.

The static `makeHead(ret, recompute, changed, validityChanged, parent)` is the reusable wiring routine: if `ret==null` it creates a `Hub(false)`; if `recompute==null` it uses `defaultRecomputeHead`; it attaches the listeners and sets `owner`. Subclasses (or external code building a head outside a `PileCompound`) can call this directly.

### Why a `Hub` and not a plain `PileImpl`

The head is a [`Hub`](Hub.md), whose **equivalence relation always reports "unequal"** (`Hub.HUBS_ARE_ALWAYS_UNEQUAL`, `Hub.java`). That means each time a component changes, the head is *always* treated as having changed and fires a change event — even though its dummy value never actually changes. This is the whole point: it guarantees a `ValueEvent` per component change. Caveat (lifted from `Hub`'s javadoc): do **not** build a `field(...)` off the head expecting value-equality dedup — the head deliberately never compares equal. With `deep=true` a `Hub` additionally re-fires its dependencies' own `ValueEvent`s; `PileCompound` defaults to `deep=false`.

## Methods to override

| Method | Purpose | Default |
|---|---|---|
| `destroy` | **abstract** — destroy all component `PileImpl`s | none — must implement |
| `autoCompundName` | **abstract** — a name for the compound (used as the head's `avName`) | none — must implement |
| `recomputeHead(Recomputation)` | how the head recomputes | fulfills `"dummy"` |
| `headFiresChange(ValueEvent)` | callback when the head fires a change | no-op |
| `headInvalidated` | callback when the head is invalidated | no-op |
| `makeHead` / `makeHead(boolean)` | supply a custom head value | builds a `Hub` |

In `AbstractValueList`, `autoCompundName` returns the list name and `subclassDestroy` destroys the head under an auto-validation suppressor.

## `PublicHead` and `headDependBracket`

- `PileCompound.PublicHead` is an abstract subclass that merely overrides `head` to make it `public` (returning `Pile<Object>`). Use it when external code needs to reach the head — e.g. to depend on the whole compound.
- `headDependBracket(Depender d)` returns a [`ValueBracket`](../aspect/bracket/...) (via `ValueBracket.dependencyBracket(PileCompound::head, d)`) that, when opened on a `PublicHead`, makes `d` depend on that compound's head. This is how a compound gets pulled into another value's dependency graph as a unit (e.g. a list element that is itself a compound contributing to its container's head). Note the bound is `<E extends PileCompound.PublicHead>` — it only works on `PublicHead` subclasses, since it needs public `head`.

## Dependency-graph role

`PileCompound` is the **aggregation node** of the structure: components → head (head depends on all) → (optionally) a containing value depends on the head via `headDependBracket`. It does not recompute anything meaningful itself; it exists to collapse "this group of values" into one observable/dependable handle. Contrast with [`PileImpl`](PileImpl.md) (a single reactive value) and `PileList`/`AbstractValueList` (a *list* of values, which adds element management — add/remove/clear, index events — on top of this head machinery).

## How `AbstractValueList` differs from `PileCompound`

`PileCompound` fixes the components at construction (subclass fields, wired once). `AbstractValueList` makes the component set **dynamic**: it holds an `ArrayList` of element values and adds/removes them as dependencies of the head at runtime (`clear`/`removeIf` etc., `AbstractValueList.java`), firing interval-added/removed events. So `PileList` is "a `PileCompound` whose components are a mutable, ordered collection". For the element-management contract, see [`AbstractValueList`](AbstractValueList.md) / [`PileList`](PileList.md).

## Caveats & gotchas

- **The head's value is a dummy** — never read it for data. Only its validity and event stream are meaningful.
- **`head` is protected** unless the subclass widens it; many graph operations (and `headDependBracket`) require `PublicHead`.
- **Always-unequal equivalence** on the head (via `Hub`) defeats value-equality dedup downstream — see [`Hub`](Hub.md).
- `head` is built in a **field initializer**, so `makeHead`/`recomputeHead` run before the subclass constructor body — be careful overriding `makeHead` to reference not-yet-initialized subclass fields.

## Tech debt / warts

- Typo in the public API: `autoCompundName` (missing the 'o' in "Compound") — appears in both the abstract declaration and overrides, so it is baked into the contract.
- `makeHead(...)`'s javadoc mislabels `changed`/`validityChanged` as *"an optional `PileList`"*; they are actually `ValueListener`s. (See SUSPECTED_BUGS.)
- The head's recompute/event machinery is duplicated wiring that every compound carries even when it only needs the change stream.

## Related

- [`Hub`](Hub.md) — the head's class (always-unequal `Pile`). *(doc pending)*
- [`AbstractValueList`](AbstractValueList.md) · [`PileList`](PileList.md) — the dynamic-component subclasses. *(doc pending)*
- [`PileImpl`](PileImpl.md) — the component/head base class.
- [concepts/transactions.md](../../concepts/transactions.md) — validity/change propagation that the head rides on.
- [impl index](_index.md) · [overview](../../overview.md).
