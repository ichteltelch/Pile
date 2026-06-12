# `CoupleEqual`

Keep two reactive values equal by copying each into the other on change, with a re-entrancy guard so the write-back doesn't ping-pong.

Source folder: `src` (package `pile.relation`).

Up: [relation index](_index.md) · [overview](../../overview.md). Super: [AbstractRelation.md](AbstractRelation.md). Siblings: [SwitchableCoupleEqual.md](SwitchableCoupleEqual.md), [Equalizer.md](Equalizer.md). Guard: [`Nonreentrant`](../utils/Nonreentrant.md).

## What it is for

`CoupleEqual<E>` ties two `ReadWriteListenValue<E>`s (`op1`, `op2`) together so that a change to either is written into the other. It is a `Relation` (extends `AbstractRelation`), i.e. it maintains an invariant *between* two values rather than deriving one from the other. The direction(s) in which changes propagate are chosen by a `Mode`.

**Lifetime gotcha (from the javadoc):** the coupled values hold only **weak** listeners back to the `CoupleEqual` (see `addWeakValueListener` in the constructor). If you don't keep a strong reference to the `CoupleEqual` somewhere, it can be garbage-collected and the two values silently decouple. Store the instance.

## Modes — which direction wins

`Mode` (nested enum) has four members; the constructor defaults a `null` mode to `BIDI_2_TO_1`:

- `BIDI_2_TO_1` (default) — bidirectional. On **initial coupling**, `op1` takes `op2`'s value (op2 wins at startup).
- `BIDI_1_TO_2` — bidirectional. On initial coupling, `op2` takes `op1`'s value (op1 wins at startup).
- `ONLY_1_TO_2` — one-way: `op2` is set from `op1`, never the reverse.
- `ONLY_2_TO_1` — one-way: `op1` is set from `op2`, never the reverse.

The two `BIDI_*` modes differ **only** in the initial-sync direction; after that they are symmetric — a change to either operand propagates to the other. The "leader/follower" wording in the constructor javadoc refers to that startup tie-break, not to ongoing precedence.

## How sync works — `sync(ValueEvent)`

A single `ValueListener` (`vl`) is registered (weakly) on both operands and also invoked once at construction for the initial sync. The handler `sync` branches on the event:

- **`e == null`** — the *initial-sync* call (and any `runImmediately` with no source event). Resolves direction from `mode`: `1→2` modes do `op2.transferFrom(op1, false)`; `2→1` modes (incl. the default) do `op1.transferFrom(op2, false)`.
- **`e.getSource() == op2`** — copy op2 into op1 (`op1.transferFrom(op2, false)`), except in `ONLY_1_TO_2` where it is ignored.
- **`e.getSource() == op1`** — copy op1 into op2 (`op2.transferFrom(op1, false)`), except in `ONLY_2_TO_1` where it is ignored.

`sync` first checks `isEnabledPrim()` and returns early if the relation is disabled (the base `CoupleEqual` is always enabled — see [SwitchableCoupleEqual.md](SwitchableCoupleEqual.md) for the switchable override).

### The `Nonreentrant` guard

`vl` is built from `nr.fixed(this::sync, Functional.NOP)`: the listener is wrapped by a per-thread [`Nonreentrant`](../utils/Nonreentrant.md). When `sync` writes into the other operand, that write fires *its* value listener, which would re-enter `vl` — but the guard takes the **fail** branch (`Functional.NOP`, a no-op) on re-entry, so the echo is dropped immediately rather than bouncing back. This is the canonical "don't let the echo recurse" use of `Nonreentrant`. The guard is **per thread**: a write on one thread does not block coupling running concurrently on another.

## How equality is judged

`CoupleEqual` does **not** itself compare the two values. It always copies via `WriteValue.transferFrom(src, false)`, which calls `set(src.getValidOrThrow())` on the target. Whether that `set` counts as a *change* (and thus fires further listeners / ends a transaction as changed) is decided by the **target value's own equivalence relation** (`_getEquivalence` / `_setEquivalence` on each operand), not by anything in this class. So:

- Equality is whatever each operand's configured equivalence predicate says — typically `Objects.equals`, but per-operand-configurable.
- It is not symmetric by construction: each direction uses the *receiving* operand's equivalence. If the two operands carry different equivalence relations, the coupling can behave asymmetrically.

There is **no transform/mapping** between the two values — `CoupleEqual` requires them to share the element type `E` and copies the value through unchanged. For a coupling with a transform, look elsewhere (this class is identity-only).

## What happens when one side is invalid

`transferFrom(src, false)` is always called with `alsoInvalidate == false`. Per `WriteValue.transferFrom`: if the source is **invalid**, and because `alsoInvalidate` is false, the method does **nothing** — the target keeps its current value and is *not* invalidated. So pushing from an invalid operand is silently a no-op; the invalid state is **not** propagated, only valid values are copied. (This is idiomatic, not a bug: an invalid source has nothing meaningful to transfer.)

## Construction & teardown

- Public ctor `CoupleEqual(op1, op2, mode)` delegates to the protected `CoupleEqual(op1, op2, initSync, mode)` with `initSync = true`. Both operands are null-checked (`Objects.requireNonNull`).
- The protected ctor exists so subclasses can suppress the initial sync (`initSync = false`) — e.g. when enablement is deferred. When `initSync` is true it calls `vl.runImmediately(true)`, driving the `e == null` branch once.
- `destroy()` removes both weak listeners and nulls out `op1`/`op2`, breaking the relation.
- `getListener()` (the `AbstractRelation` hook) returns `vl`; the base `AbstractRelation.installEnabledListener` machinery is what re-runs `vl` when an `isEnabled` reactive boolean flips (used by the switchable subclass, not by the always-enabled base).

## Caveats & gotchas

- **Keep a strong reference** or the coupling dies at the next GC (weak listeners).
- Equality/"did it change" is the *target operand's* business, not this class's — configure `_setEquivalence` on the operands if you need non-default semantics.
- Invalid source ⇒ no-op (value not copied, target not invalidated). Invalidity does not propagate through the coupling.
- Mode only sets the **initial** tie-break for the two `BIDI_*` modes; don't read more precedence into "leader/follower" than the startup sync.
- No value transform — operands must share type `E` and are coupled by identity copy.
- Re-entry within the same `Nonreentrant` is dropped via `Functional.NOP`; a write arriving on a *different* thread is not guarded against and runs the action branch.

## Tech debt / warts

- The `sync` switch enumerates mode cases per branch; adding a `Mode` requires touching three switch sites. Minor, but easy to get out of sync.
- Asymmetric equivalence (each direction using the receiver's predicate) is a subtle correctness footgun if the two operands are configured differently — undocumented in the source.
