# `WaitServiceConfig`

Package-private holder for the global and thread-local selection of the active [`WaitService`](WaitService.md).

Source folder: `src` · package `pile.interop.wait`.

Up: [wait index](_index.md) · [interop overview](../../../overview.md).

## What it is

`WaitServiceConfig` is a `final`, non-instantiable utility (private constructor) that stores *which* `WaitService` Pile should block through. It holds no logic of its own beyond lazily creating the thread-local; it is the backing store that [`WaitService.get`](WaitService.md) reads and that the `WaitService` setters write. Clients never touch it directly — it is package-private — they go through the public static API on `WaitService` (`get`, `setGlobalDefault`, `setThreadLocalDefault`, `withThreadLocalDefault`).

## What it configures

Two layers, consulted in this order by `WaitService.get`:

1. **`current`** — a `volatile ThreadLocal<WaitService>` (an `InheritableThreadLocal`, so child threads inherit the parent's selection). A non-null thread-local value wins.
2. **`globalDefault`** — the process-wide fallback used when no thread-local is set. Initialised to `WaitService.DEBUGGABLE_NATIVE`.

So resolution is: thread-local value → else `globalDefault`. See `WaitService.get`.

## The default

`globalDefault = WaitService.DEBUGGABLE_NATIVE` — the `DebuggableWaitService` wrapper around native `wait`/`notify`/`sleep`, which adds periodic wakeups (see [`WaitService.md`](WaitService.md), the `DebuggableWaitService` class). This is the out-of-the-box behavior unless something calls `WaitService.setGlobalDefault`.

## How it's set

All via `WaitService`'s static methods:

- `setGlobalDefault(ws)` — overwrites `globalDefault` (rejects null).
- `setThreadLocalDefault(ws)` — `current().set(ws)`; sticks until overwritten or an enclosing `MockBlock` closes.
- `withThreadLocalDefault(ws)` — returns a `MockBlock` that sets the thread-local on `open` and restores the previous value on close (scoped override, the idiomatic way to swap services temporarily, e.g. in tests).

## Lazy thread-local creation

The `ThreadLocal` is created on first need, not at class load:

- `current()` — double-checked-locked lazy init of the `InheritableThreadLocal`; always returns a non-null thread-local (used by the setters, which need somewhere to write).
- `current(WaitService notIfThis)` — same, but **returns `null`** (skipping creation) if the thread-local doesn't yet exist *and* `globalDefault == notIfThis`. This lets a caller avoid allocating the thread-local when the requested service already equals the global default, so a later `get` short-circuits to `globalDefault` with no per-thread state. Currently this overload has no live caller — its only use site is the commented-out `setThreadLocalDefault_ignorable` in `WaitService`. Dead but harmless.

The `volatile` field plus `synchronized` block is the standard lazy-singleton pattern; `WaitService.get` reads `current` directly (not via `current()`) so a never-initialised config costs nothing.

## Caveats & gotchas

- **Package-private by design** — not part of the public surface. Configure through `WaitService`.
- **`InheritableThreadLocal`**: threads spawned after a `setThreadLocalDefault` inherit the value at creation time. Pool threads created earlier will not see it; mind thread-pool reuse when relying on thread-local selection.
- **`globalDefault` is a plain (non-volatile) static field.** A `setGlobalDefault` on one thread is not guaranteed to be promptly visible to a `get` on another without other synchronisation. In practice the default is set once at startup, so this is rarely observable.
- `current(WaitService)` is effectively dead code (see above).

## Tech debt / warts

- The `current(WaitService notIfThis)` overload and its only (commented-out) caller are vestigial; either wire up the `setThreadLocalDefault_ignorable` optimisation or drop the overload.
- `globalDefault` non-volatile while `current` is volatile is an inconsistency in the memory-visibility story.
