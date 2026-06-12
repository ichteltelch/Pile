# `WrappedCondition`

A decorator that adapts a plain `java.util.concurrent.locks.Condition` into a `WaitServiceUsingCondition`. Source folder: `src` (package `pile.interop.wait`).

Up: [wait index](_index.md) · [overview](../../../overview.md).

## What it's for

`WrappedCondition` holds a backing `Condition` (the `back` field) and implements `WaitServiceUsingCondition` by forwarding every `await*` call to the injected `WaitService`, passing `back` as the underlying condition. This is the bridge that lets an ordinary JDK `Condition` participate in Pile's injectable wait layer (see [`_index.md`](_index.md)), so that blocking goes through the `WaitService` rather than calling `Condition.await` directly.

## How to use it

- **Wrap an existing condition:** `WrappedCondition.of(back)` returns an anonymous subclass that is immediately usable as a `WaitServiceUsingCondition`. This is the common path.
- **Extend it:** subclass `WrappedCondition` to add behavior while inheriting the await-forwarding. The class is `abstract` precisely because it leaves the signalling side open.

## What it forwards vs. what it leaves abstract

- **Forwarded (final, concrete):** every waiting method — `await`, `awaitUninterruptibly`, `awaitNanos`, the timed `await(ws, time, unit)`, `awaitUntil`, `awaitNanosUninterruptibly`, `awaitUninterruptiblyUntil`. Each just calls the same-named method on the `WaitService` with `back`. There is no extra logic; the decorator adds *adaptation*, not behavior.
- **Left abstract:** `signal(WaitService)` and `signalAll(WaitService)` are **not** implemented here — `WrappedCondition` only decorates the waiting half. The `of` factory supplies the obvious implementations (`ws.signal(back)` / `ws.signalAll(back)`); a hand-written subclass must provide them.

## Caveats & gotchas

- The waiting methods are effectively final (no `protected` seam to override their forwarding) — customization is expected on the signalling side or by wrapping a richer `back` condition, not by intercepting `await`.
- `back` is `protected final`; subclasses read it but cannot re-target it after construction.
- All semantics (interruptibility, fairness, spurious wakeups) come from the backing `Condition` and the active `WaitService`; `WrappedCondition` itself imposes none.

## See also

Sibling conditions: [`ObservableCondition`](ObservableCondition.md), [`GuardedCondition`](GuardedCondition.md), [`NativeCondition`](NativeCondition.md). Concepts: [`../../../concepts/`](../../../concepts/).
