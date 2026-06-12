# `Equalizer`

A reactive boolean that reports whether two values are equal, and — when set to `true` — makes them equal by copying one into the other.

Source folder: `src` (package `pile.relation`).

Up: [overview](../../overview.md) · [relation index](_index.md). Super: [`SealBool`](../specialized_bool/SealBool.md) (sealable boolean Pile). Sibling: [`CoupleEqual`](CoupleEqual.md).

> **Index caveat.** The package [`_index.md`](_index.md) currently describes `Equalizer` as "keep several values mutually equal" / "the N-ary generalisation of `CoupleEqual`". **The source does not support that.** `Equalizer` is strictly **binary** and **one-directional in its write-back**: a single `receiver`/`giver` pair, with a fixed leader (`giver`). It is closer to a read-only, one-way variant of `CoupleEqual` than a generalisation of it.

## What it is

`Equalizer<T>` extends `SealBool`. Its *value* is a `Boolean` that tracks whether two reactive participants are currently equal:

- `receiver` (`ReadWriteDependency<T>`) — the value that gets **overwritten** to establish equality.
- `giver` (`ReadDependency<? extends T>`) — the value that is **read** but never written.

Both are `public final` fields. The boolean recomputes from `equivalence.test(giver.get(), receiver.get())` and re-fires whenever either participant changes (`make` passes both to `whenChanged`).

## Construction (no public constructor)

The constructor is `private`; you build instances through the static `make(...)` overloads:

- `make(receiver, giver)` — `giver` is another `ReadDependency`; uses `Objects::equals` as the equivalence relation.
- `make(receiver, giver, equivalence)` — same, with a custom `BiPredicate` equivalence.
- `make(receiver, T giver)` / `make(receiver, T giver, equivalence)` — convenience overloads that wrap a **plain constant** value as the giver via `Piles.constant(giver)`.

`make` does the real wiring through a `SealPileBuilder` on a freshly-constructed `Equalizer`:
`.recompute(...)` installs the equality computation, `.seal(ret::interceptor)` seals the boolean and registers the write-back interceptor, `.whenChanged(giver, receiver)` declares the two dynamic dependencies.

## How equality is *requested* (the interceptor)

`Equalizer` is **sealed with an interceptor** (`Equalizer.interceptor`). Because of how `SealPile` sealing works (see `SealPile.set`), once sealed:

- a client `set(...)` on the `Equalizer` **never changes the boolean directly** — the boolean only ever reflects the recomputed equality. The attempted value is handed to the interceptor and otherwise discarded.
- `Equalizer.interceptor` reacts only to `Boolean.TRUE`: it calls `receiver.set(giver.get())`, copying the giver's current value into the receiver. **Setting `false` or `null` is silently ignored** (idiomatic — there is no meaningful "make them unequal" action).

So `equalizer.set(true)` means *"make them equal now,"* and the boolean then recomputes to `true` (assuming the write took). The direction is fixed: **`giver` → `receiver`** only. There is no mode selector as in `CoupleEqual.Mode`.

## Conflict resolution / correctors

The write-back goes through `receiver.set(...)`, so any **corrector** installed on `receiver` can refuse or adjust the incoming value (the class javadoc notes the equality may not actually be achieved "unless a corrector installed on the receiver prohibits it"). If the receiver rejects/transforms the value, the recomputed boolean simply reports the resulting (in)equality on the next change. There is no retry or error.

## Re-entrancy

Unlike `CoupleEqual`, `Equalizer` has **no explicit `Nonreentrant` guard**. The feedback loop is broken structurally instead: the interceptor writes to `receiver` (not to the boolean), and the boolean is recomputed (not set) from the participants — `set` on the sealed boolean cannot recurse into itself because the seal diverts every `set` to the interceptor rather than to the value. `allowInvalidation` is `false` for the seal (the default of `seal(interceptor)`), so the boolean cannot be perma-invalidated by clients.

## Lifetime / references

`make` returns the `Equalizer` (as a `SealBool`-typed builder result). The dependency wiring (`whenChanged`) is what keeps it reactive; as with other relations you must **retain the reference** yourself if you want the coupling to persist (the participants do not necessarily hold a strong reference back). Compare the explicit weak-listener teardown (`destroy`) in `CoupleEqual`; `Equalizer` exposes no `destroy` of its own beyond what `SealBool`/`PileImpl` provide.

## Common tasks

- **Show whether two values are equal, reactively:** `Equalizer<T> eq = Equalizer.make(receiver, giver);` then read/observe `eq` as a boolean Pile.
- **Compare against a constant:** `Equalizer.make(receiver, someConstant)` (auto-wrapped via `Piles.constant`).
- **Custom equivalence (e.g. tolerance):** pass a `BiPredicate` as the third arg.
- **Force equality on demand:** `eq.set(true)` — copies `giver` into `receiver`.

## Caveats & gotchas

- **Asymmetric, not symmetric.** Only `receiver` is ever written. If you need bidirectional coupling, use [`CoupleEqual`](CoupleEqual.md), not `Equalizer`.
- **`set(false)` / `set(null)` are no-ops**, not errors (idiomatic silent-ignore). You cannot "un-equalize" through the boolean.
- **Equality may silently fail to hold** if a `receiver` corrector vetoes/alters the written value; the boolean will then read `false` after recompute.
- **Index/javadoc mismatch** about N-ary behavior (see the caveat at the top) — trust the source.

## Tech debt / warts

- The [`_index.md`](_index.md) gist ("keep several values mutually equal" / N-ary generalisation) misdescribes this binary class and should be corrected.
- No `destroy()`/teardown specific to the relation, in contrast to its sibling `CoupleEqual`; cleanup relies on the underlying `SealBool` dependency machinery.
