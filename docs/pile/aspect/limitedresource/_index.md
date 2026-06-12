# `pile.aspect.limitedresource` — package index (Tier 1)

Source folder: `src` (all types below).

A small aspect for modelling a **limited resource** that several requesters (*supplicants*) compete for. Peripheral to the core reactive machinery.

Up: [aspect index](../_index.md) · [overview](../../../overview.md).

> **Status: unfinished / unused.** Both classes are skeletons with zero usages in the codebase (`find_references`). Documented for completeness.

## Types
- [`LimitedResource`](LimitedResource.md) — a counted, capacity-limited resource (name + `max`/`used` + an `available` `ObservableCondition`). **Stub** — no acquire/release API; `used` is never mutated.
- [`Supplicant`](Supplicant.md) — an abstract priority/deadline/id ordering token for a requester (`compareTo`: priority → deadline → id). **No request/grant lifecycle here** despite the name; meant to be subclassed.
