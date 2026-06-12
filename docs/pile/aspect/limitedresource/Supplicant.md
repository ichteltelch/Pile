# `Supplicant`

An abstract identity-and-priority token representing one requester competing for a [`LimitedResource`](LimitedResource.md).

Source folder: `src` · package `pile.aspect.limitedresource`.

Part of the small [limited-resource aspect](_index.md); see the package [overview](../../../overview.md) for where this sits.

## What it's for

A `Supplicant` is the *handle* by which a caller asks for a slice of a shared, capped resource. It carries the two facts the resource needs to arbitrate between competing requesters — a **priority** and a **deadline** — plus a unique **id** for stable tie-breaking. It is `abstract`, so callers subclass it to attach their own request payload/behavior; the base class itself adds no abstract methods to override.

## Shape (what the current source actually provides)

The class is intentionally tiny — it is ordering metadata, not a state machine:

- `Supplicant(int priority, long deadline)` — the only constructor; both values are final.
- `long deadline` — the deadline accessor. There is **no** `priority` accessor; `priority` is private and used only inside `compareTo`.
- `compareTo(Supplicant)` — orders by `priority` first, then `deadline`, then `id`. Lower priority value sorts first.
- `BY_DEADLINE` — a `Comparator` that orders by `deadline` alone.
- `id` — a per-instance serial from a static `AtomicInteger INST_COUNTER`, assigned at construction; guarantees `compareTo` never returns 0 for two distinct instances (so it is consistent with `equals` in practice, giving a total order usable as a key in sorted sets/queues).

## Salient / surprising behavior

- **No lifecycle here.** Despite the name, this base class has *no* `request` / `hold` / `release` methods and *no* `granted` / `revoked` callbacks. Acquisition lifecycle (if any) lives in [`LimitedResource`](LimitedResource.md) and/or in your subclass — not in `Supplicant`. Treat this file purely as a comparable request token.
- **Two different orderings coexist.** `compareTo` is priority-major (priority → deadline → id); the static `BY_DEADLINE` comparator ignores priority entirely. Pick deliberately: a `PriorityQueue<Supplicant>` using natural order honors priority, while `BY_DEADLINE` gives earliest-deadline-first regardless of priority.
- **"Lower is more urgent"** for both priority and deadline — `Integer.compare`/`Long.compare` put smaller values first.
- **`id` is process-global and monotonic**, sourced from a static counter shared across all `Supplicant` instances; it is the final tie-breaker, so insertion order (construction order) decides ties between equal priority+deadline.

## Caveats & gotchas

- The base class deliberately does nothing on its own; subclassing is expected. An "abstract class with no abstract methods" is the idiom here (it forces subclassing to carry the actual request, while keeping the comparable contract in one place) — not an oversight to "fix".
- `priority` has no getter; if a subclass needs to read its own priority it must store it again or rely on `compareTo`. (Wart, see below.)

## Common tasks

- **Make a request token:** subclass `Supplicant`, pass `(priority, deadline)` to `super(...)`, and add whatever your code needs to actually do with a grant.
- **Order requesters by urgency-then-priority vs. priority-then-urgency:** use `Supplicant.BY_DEADLINE` for the former, natural ordering (`compareTo`) for the latter.

## Tech debt / warts

- Asymmetric accessors: `deadline` is exposed but `priority` is not — minor API gap.
- The class name implies a request/grant protocol that is not present in this unit, which can mislead; the protocol (if present) belongs to [`LimitedResource`](LimitedResource.md). Worth a javadoc note pointing readers there.
