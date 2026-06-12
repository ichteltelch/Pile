# `WeakDependencyBracket`

Weak-reference variant of the dependency bracket: wires `Depender`→`Dependency` for the lifetime of the held value, but holds the dependers only weakly so they can be garbage-collected.

Source folder: `src` · package `pile.aspect.bracket`.

A [`ValueOnlyBracket`](_index.md) that, while open over a value, makes one or more [`Depender`](../Depender.md)s depend on a [`Dependency`](../Dependency.md) extracted from that value (via the `extract` function). It is the GC-friendly twin of `StrongDependencyBracket` — see [`StrongDependencyBracket.md`](StrongDependencyBracket.md) and the shared sibling [`DependerBracket.md`](DependerBracket.md), and the [aspect/Dependency](../Dependency.md) concept. For the bracket lifecycle (open while value held, close while value's `mutex` is held) see the package [`_index.md`](_index.md) and the [overview](../../../overview.md).

## What it's for / how it differs from the strong variant
It holds each depender in a `WeakReference` (`ds`, `d0` at `WeakDependencyBracket.java`). The point: the bracket itself must not keep the dependers alive. Use this when the value (and its bracket) may outlive the dependers and you don't want the bracket to be the reason they stay reachable.

- **Strong variant**: keeps the dependers strongly reachable for as long as the bracket is open — the value pins the dependers.
- **Weak variant**: the dependers may be collected at any time; once all are gone the bracket is dead weight and asks to be removed (`canBecomeObsolete` → `true`, `WeakDependencyBracket.java`).

## Key methods by purpose
- `create(inheritable, extract, d0, ds)` — factory; wraps in `detectStuck` when `DebugEnabled.DETECT_STUCK_BRACKETS` is on. Constructor is package-private; go through `create`.
- `open` / `doOpen` — on open, calls `extract.apply(value)` then `addDependency` on each *still-live* referent.
- `close` / `doClose` — symmetric `removeDependency` on each still-live referent. **`extract` must be deterministic** — `doClose` re-derives the `Dependency` from the value and removes *that*; a non-deterministic `extract` would remove the wrong (or no) dependency (javadoc at ).
- `isInheritable` / `openIsNop` / `closeIsNop` / `canBecomeObsolete` — flags; open/close are never no-ops, and this bracket can become obsolete.

## Salient / surprising behavior
- **Lock-deferral.** If `owner` is a `HasInternalLock` and the current thread holds its lock, `open`/`close` defer the real work via `ListenValue.DEFER` rather than running inline. This avoids doing `addDependency`/`removeDependency` reentrantly under the value's mutex.
- **`null` value short-circuits.** `open(null,…)` returns `true` (treated as opened, no work); `close(null,…)` returns `false`.
- **Obsolescence handshake via `obsoleteOn`.** If `doOpen` finds *no* live depender (`!doneSth`), it records the owner in the `obsoleteOn` map. A subsequent `open` for that same owner sees the entry, removes it, and returns `false` — signalling the host that this bracket is obsolete and should be dropped. So a weak bracket whose dependers have all been collected reports itself obsolete on the *next* open attempt.
- **Return values are inverted from intuition.** `open` returns `true` on success but `close` always returns `false`; both are the contract values the host expects, not error flags.

## Caveats & gotchas
- The dependency relationship is only as durable as the dependers' reachability *elsewhere*. If nothing else holds a depender, it can vanish mid-open and the dependency silently stops being maintained — by design, not a bug.
- `extract` non-determinism breaks removal (see above) — the dependency added on open won't match the one looked up on close.
- Deferral means the dependency edge may not exist synchronously after `open` returns when the owner's lock is held; it lands once `ListenValue.DEFER` runs.

## Common tasks
- **Make a depender weakly track a value's extracted dependency**: `WeakDependencyBracket.create(inheritable, extract, depender, null)` and add the result as a value bracket on a `HasBrackets`.
- **Choose weak vs strong**: pick weak when the bracket/value must not keep dependers alive; otherwise [`StrongDependencyBracket`](StrongDependencyBracket.md).

## Tech debt / warts
- Commented-out `QueuedValueBracket.getDefaultDependencyQueue` lines mark a superseded deferral mechanism, now replaced by `ListenValue.DEFER`.
- `obsoleteOn` is a `ConcurrentHashMap<Object,Object>` used essentially as a per-owner flag set; the obsolescence signal only fires on a *later* `open`, so an obsolete bracket lingers until re-opened.
