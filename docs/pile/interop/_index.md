# `pile.interop` — package index (Tier 1)

The **host-environment glue**: injectable services that let Pile be adapted to its runtime — how to run jobs on other threads, how to wait/notify/sleep/interrupt, how to persist settings, and which debugging features are compiled in. Most of these are dependencies Pile lets you swap out (see the `README` "injectable dependencies" note).

Up: [overview](../../overview.md).

> **Source folders:** `exec`, `wait`, `preferences` are under `src`. **`pile.interop.debug` (`DebugEnabled`) is under the `debug` source folder** (with a swappable `debug_off` twin) — *not* `src`.

## Sub-packages
- [`interop.exec`](exec/StandardExecutors.md) — `StandardExecutors`: the `ExecutorService`s Pile uses to run jobs in different threads (recompute delay, off-thread work). *(single file; doc linked directly)*
- [`interop.wait`](wait/_index.md) — the injectable `WaitService` (wait/notify/sleep/interrupt) and the awaitable-`Condition` abstraction.
- [`interop.preferences`](preferences/_index.md) — `PrefInterop` (`Preferences`-backed remember-last-value) and the `*BackedValue` reactive values that sync to preferences/files.
- [`interop.debug`](debug/DebugEnabled.md) — `DebugEnabled`: the `static final boolean` debug flags (`DE`, `ET_TRACE`, `DETECT_STUCK_BRACKETS`, `TRANSACTION_TRACES`, …) exploited for conditional compilation. **Lives in the `debug`/`debug_off` source folder.** *(single file; doc linked directly)*
