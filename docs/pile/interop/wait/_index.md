# `pile.interop.wait` — package index (Tier 1)

Source folder: `src`.

The **injectable wait/blocking layer**. `WaitService` abstracts `wait`/`notify`/`sleep`/`interrupt` so Pile can be adapted to non-native semantics (e.g. periodic wakeups for debugging, or treating `interrupt` as a wake-up message rather than a crash — see the `README`). The `Condition` types are an awaitable-condition abstraction used to build wait services and to expose waitable predicates.

Up: [interop index](../_index.md) · [overview](../../../overview.md).

## The service
- [`WaitService`](WaitService.md) — the injectable `wait`/`notify`/`sleep`/`interrupt` abstraction Pile blocks through; `get()` returns the active per-thread service. **Default is `DEBUGGABLE_NATIVE`** (caps every wait with a periodic wakeup) — so blocking is *not* raw out of the box.
- [`WaitServiceConfig`](WaitServiceConfig.md) — package-private holder for the global + thread-local selection of the active `WaitService`.

## Conditions (awaitable, routed through a `WaitService`)
- [`WaitServiceUsingCondition`](WaitServiceUsingCondition.md) — the base `Condition` whose `await*` go through a `WaitService` (each no-arg method delegates to a `ws`-aware twin via `WaitService.get()`); `WaitService.Dispatch` detects these and routes back. (Not a service impl.)
- [`WrappedCondition`](WrappedCondition.md) — abstract decorator adapting a plain JDK `Condition` into a `WaitServiceUsingCondition` (forwards `await*` via the service; leaves `signal` abstract).
- [`NativeCondition`](NativeCondition.md) — a `Condition` backed by a plain object's monitor (`wait`/`notify`), routed through the `WaitService`.
- [`GuardedCondition`](GuardedCondition.md) — a `WrappedCondition` guarding a boolean predicate (await-until-true; loops the wait), with optionally-conditional signalling.
- [`ObservableCondition`](ObservableCondition.md) — a `WrappedCondition` whose `signal`/`signalAll` additionally notify registered observers (used by `LimitedResource.available`).
