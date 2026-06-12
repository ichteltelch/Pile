# `pile.aspect.HasInternalLock`

The one-method aspect that lets code ask "does the current thread already hold this value's internal mutex?" — the framework's reentrancy/deadlock guard.

Source folder: `src`. File: `pile/aspect/HasInternalLock.java`.

A reactive value implements `HasInternalLock` when it has an internal monitor lock and wants to expose **whether the calling thread currently holds it**. This is purely a thread-safety / lock-introspection capability; it carries no read, write, or dependency semantics. See the [overview](../../overview.md) for where aspects sit, and [concepts/transactions.md](../../concepts/transactions.md) for the locked sections this lock protects.

## The interface

- `boolean holdsLock` — returns whether *the current thread* holds this object's internal lock. It is a snapshot of the calling thread only (it cannot tell you another thread holds it), and it does **not** acquire anything — it is a cheap, non-blocking query.

That is the whole interface.

## Where the lock actually lives (override map)

The interface declares the contract; the mutex it talks about is owned by the shared base implementation.

- **`pile.impl.AbstractReadListenDependency`** (ARLD) is the only implementor in the framework. It declares `final protected Object mutex = new Object` — *"The main mutex and monitor of this object"* — and implements `holdsLock` as simply `return Thread.holdsLock(mutex)`.
- ARLD is the base of `PileImpl` and `Independent`, so every concrete `Pile`/`Independent` inherits this single mutex and this `holdsLock`. There is no per-subclass override.

Because the mutex is `protected`, subclasses and same-package impl code `synchronized(mutex){…}` on it directly; the mutex is referenced pervasively across the impl (thousands of references), which is exactly why a public "do I already hold it?" probe is worth having.

## Why expose lock-holding at all

Two distinct needs:

1. **Reentrancy / deferral decisions.** Callbacks must not run framework mutations while the caller is *inside* the value's lock (re-entering invalidation/propagation under the held monitor risks lock-ordering deadlock and broken invariants). The `bracket` package keys off this: `DependerBracket`, `StrongDependencyBracket`, and `WeakDependencyBracket` all test `owner instanceof HasInternalLock && ((HasInternalLock) owner).holdsLock` and, if true, **defer** the dependency wiring via `ListenValue.DEFER.run(...)` instead of doing it inline (`DependerBracket.java`, ; `StrongDependencyBracket.java`, ; `WeakDependencyBracket.java`, ). So `holdsLock` is the runtime switch between "do it now" and "queue it until the lock is released."
2. **Assertions.** Much impl code guards entry/exit of unlocked regions with `assert !Thread.holdsLock(mutex)` (the negative of `holdsLock`), documenting and checking the "must NOT hold the lock here" contract at runtime when assertions are enabled.

## Salient behavior & caveats (thread-safety)

- **Current-thread only.** `holdsLock` answers about the calling thread. It is meaningless as a global "is anyone in the critical section?" check.
- **Snapshot, non-blocking, no side effects.** Unlike [`Dependency#isValid`](Dependency.md), `holdsLock` never blocks and never triggers actions; it is safe to call from anywhere.
- **Lock-ordering / deadlock.** The whole reason this aspect exists is that doing certain framework work *while holding* the mutex can deadlock or re-enter. When you write code that runs under a value's lock, prefer **deferring** outward-facing work (the bracket pattern via `ListenValue.DEFER`) rather than calling back into other values.
- **"Must not hold the lock" contracts.** Many internal methods assume the caller does *not* hold `mutex` on entry (enforced by `assert !Thread.holdsLock(mutex)`). If you extend ARLD/`PileImpl`, respect these — calling a "must be unlocked" method from inside `synchronized(mutex)` will trip the assertion (debug builds) or deadlock/corrupt state (production).
- **Not reentrant accounting.** `holdsLock` is a boolean, not a hold count; it tells you *that* you hold the monitor, not how deep.

## Common tasks

- **Decide whether to act now or defer** (the bracket idiom):
  ```java
  if (owner instanceof HasInternalLock && ((HasInternalLock) owner).holdsLock)
      ListenValue.DEFER.run( -> doWork(...));   // we're inside owner's lock: queue it
  else
      doWork(...);                                 // safe to do inline
  ```
- **Assert you are *not* inside the lock** in new ARLD-level code: `assert !holdsLock;` (or `assert !Thread.holdsLock(mutex);` with direct access).
- **Check reentrancy before taking the lock** to avoid re-`synchronized` work you can skip.

## Tech debt / warts

- The interface Javadoc's `@return` for `holdsLock` is essentially empty; the real contract ("current thread only, non-blocking") is implicit and documented here.
- The mutex is `protected`, not encapsulated, so the "must/must not hold the lock" contracts are convention enforced by scattered `assert` statements rather than by the type system — easy to violate when subclassing.

## Related

- [`Dependency`](Dependency.md) — note `isValid` *can* block on this mutex, whereas `isValidAsync` and `holdsLock` do not.
- [concepts/transactions.md](../../concepts/transactions.md) — the transaction machinery in ARLD/`PileImpl` runs under this mutex; deferral via brackets exists partly to avoid re-entering it.
- [overview.md](../../overview.md) — architecture map; `pile.aspect` granular aspects.
