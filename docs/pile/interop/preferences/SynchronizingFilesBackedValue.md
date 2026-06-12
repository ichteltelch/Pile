# `pile.interop.preferences.SynchronizingFilesBackedValue`

A reactive, writable, listenable value backed by a *set* of synchronised on-disk files, kept mutually consistent under file locks; like [`PreferencesBackedValue`](PreferencesBackedValue.md) it is `AlwaysValid` and **not** a `Dependency`.

Source folder: `src`. File: `pile/interop/preferences/SynchronizingFilesBackedValue.java`.

Up: [preferences index](_index.md) · [overview](../../../overview.md). Aspect it mixes in: [`AlwaysValid`](../../aspect/AlwaysValid.md). Sibling: [`PreferencesBackedValue`](PreferencesBackedValue.md), [`PrefInterop`](PrefInterop.md).

## What it's for

Mirror one logical value into **several backing files at once** (e.g. the same setting written to several directories / drives for redundancy), keeping them in sync: the newest file wins on read, and a write fans out to all of them. The class is `ReadWriteListenValue<T>` + `ListenValue.Managed` + `AlwaysValid<T>` — so it is gettable, settable, and listenable, but like its `Preferences`-backed sibling it **deliberately does not implement `Dependency`** (see the class Javadoc and the [`AlwaysValid`](../../aspect/AlwaysValid.md) "surprising consumers" note). Call `asDependency()` (→ `writableValidBuffer_memo()`, an `Independent`) when you need a dependency target.

The *which files* is itself reactive: the constructor takes a `ReadListenDependency<? extends Collection<? extends Path>>`, so the backing set can change at runtime and the value reacts to that.

## Construction

- `SynchronizingFilesBackedValue(ReadListenDependency<Collection<Path>> files, FileCodec<T> codec, Supplier<T> defaultValue)` — the general form (multiple files).
- `forSingleFile(ReadListenDependency<Path> file, codec, defaultValue)` — convenience wrapping a single `Path` via `Collections::singleton`.
- The constructor `map`s the incoming path collection through `canonicalize` (resolve canonical paths, drop nulls/unreadables, de-dupe, sort) and installs `restoreFromTmpFiles` as the mapping's **corrector** (crash recovery, see below). It registers a **weak** value listener (`addWeakValueListener`) on the `files` dependency and schedules an initial `read()` via `files.doOnceWhenValid`.
- `FileCodec<T>` is the (de)serialisation strategy: `encode(value, path, out)` / `decode(path, in)`. Built-ins: `STRING_CODEC` (UTF-8 text) and `viaString(Bijection<T,String>)`. `name(String)` sets the `dependencyName`.

## Read / write / sync mechanism

There is **no `WatchService` and no inotify** — external file-content changes are *not* pushed. Reactivity comes from two sources only:

1. **Changes to the *path set*** fire `fileListener`, which resets the cached `timestamp`/`size` sentinels and calls `read()`. (Re-reading because *which files back us* changed, not because their content changed.)
2. **Explicit polling** — `pollOnce()` (read newest, then write our value to any missing/stale file) and `autoPoll(scheduler, period)` to schedule it. **To pick up content edited by another process you must poll**; without it the value only refreshes on path-set changes and on its own writes. `autoPoll` jitters the first delay randomly and self-cancels via weak references once the value (or the poll runnable) is GC'd or `isDestroyed()`.

**Read** (`_read`): pick the `newest` readable file (`newest` filtered by `consider`); take a **shared** `FileLock` on a side-car `*.lock` file; if our cached `timestamp`/`size` already match, no-op; otherwise `codec.decode` it, update `currentValue`/`timestamp`/`size`, and — if the decoded value differs under the equivalence — fire a change. If decoding a file fails it is added to a `blacklist` and the read retries with the next-newest. If **no** file exists, the `defaultValue` is materialised and written out (`_write(..., force=true)`).

**Write** (`_write`): under `synchronized(this)`, short-circuits if not forced and the new value is equivalent to the current one. It locks **all** backing files (`lockingAll`, recursive, exclusive locks), but first checks whether some file on disk is *newer* than our timestamp — if so it returns code `2`, abandons the write, and **re-reads instead** (disk wins over a stale in-memory write). Otherwise it `codec.encode`s into every path and stamps each file's last-modified time to the write time.

**Locking & atomicity** (`lockingAll`): per-path it takes an in-process `ReentrantLock` (interned per canonical path via `canonicalPathLock`) *and* a cross-process `FileLock` on `<file>.lock`. Before writing it copies the live file to `<file>.tmp` (via `<file>.tmp.tmp` then rename) so a crash mid-write can be undone; the `finally` restores from `.tmp` unless the action completed. `restoreFromTmpFiles` (the path-collection corrector) performs the same recovery on startup: any leftover `<file>.tmp` is copied back over the main file.

## Always-valid nature

It mixes in [`AlwaysValid`](../../aspect/AlwaysValid.md): `get()` just returns `currentValue` (throwing only if destroyed), `isValid` is constantly `true`, `getValid*` never block, `validity` is the shared `Piles.TRUE`. The backing file is *treated as always current* — there is no "invalid while loading" state exposed to listeners; before the first successful read/default `currentValue` is simply `null`/uninitialised. This is the same posture as `PreferencesBackedValue`.

Transaction / lifecycle surface is all stubbed: `isInTransaction()` → `false`, `inTransactionValue()` → `Piles.FALSE`, `__beginTransaction`/`__endTransaction`/`permaInvalidate`/`revalidate` are no-ops, `remembersLastValue()` → `false`, `suppressRememberLastValue()` → `Suppressor.NOP`. `valueMutated()` forces a write of the current value (use after mutating a mutable `T` in place). Listener delivery is via the lazily-created `ListenValue.ListenerManager` (`_getListenerManager`); `fireValueChange()` drives it.

## How it differs from `PreferencesBackedValue`

| | `PreferencesBackedValue` | `SynchronizingFilesBackedValue` |
|---|---|---|
| Backing store | a single `java.util.prefs.Preferences` entry | one or **several** files on disk |
| Backing target | fixed key/node | a **reactive** `ReadListenDependency<Collection<Path>>` (can change) |
| External-change pickup | `Preferences` change events | **polling only** (`autoPoll`/`pollOnce`) — no file watch |
| Concurrency | delegated to `Preferences` | explicit in-process + cross-process **file locks**, tmp-file crash recovery |
| Codec | string-ish prefs values | pluggable `FileCodec<T>` |

Both: `ReadWriteListenValue` + `AlwaysValid`, not a `Dependency`, expose `asDependency()`.

## Common tasks (how to…)

- **Back a value by one file:** `SynchronizingFilesBackedValue.forSingleFile(pathDep, STRING_CODEC, ()->"")`.
- **Back a typed value:** supply `viaString(bijection)` or a custom `FileCodec<T>`.
- **Detect external edits:** call `autoPoll(scheduler, periodMillis)` and keep a strong ref to the value (the poll job is weakly held and self-cancels otherwise).
- **Depend on it in the reactive graph:** `asDependency()` / `writableValidBuffer_memo()`.
- **Force a re-write after mutating `T` in place:** `valueMutated()`.
- **Clear the backing files:** `reset()` deletes them and un-initialises.
- **Stop it:** `destroy()` (sets `currentValue=null`, future `get()` throws).

## Caveats & gotchas

- **No file watching.** Content written by another process is invisible until you poll or the path set changes. This is by design, not a bug.
- **Newest-wins, by last-modified time.** Sync correctness leans on file mtimes; clock skew between drives / NFS granularity can mis-order which file is "newest" and which write is honoured (the disk-newer-than-us code-`2` path re-reads instead of writing).
- **Side-car files proliferate.** Each backing file gets a `.lock` (and transient `.tmp`/`.tmp.tmp`) sibling; `.lock` files are created on demand and not cleaned up.
- **Silent failure modes are idiomatic here.** Per-file I/O errors are logged at `WARNING` and swallowed (the other files still get written/read); a bad file is blacklisted and skipped. Writes/reads on a destroyed value throw `IllegalStateException`.
- **Not a `Dependency`.** Cannot be a dependency of a `Pile` directly — wrap via `asDependency()`.

## Tech debt / warts

- Class-level `//TODO: use separate locking file` — the lock file is derived from the main file name; the author notes wanting it fully separated.
- `STRING_CODEC.encode`/`decode` build their `OutputStreamWriter`/`InputStreamReader` from the parameter `useThis` rather than the `os`/`is` local that accounts for `useThis==null`; when called with `useThis==null` (the only way the class itself calls them, via `codec.encode(value, p, null)`) this NPEs. See `SUSPECTED_BUGS` in the report — flagged for the developer.
- `autoPoll`'s static helper checks `if(pollfRef==null)` where it almost certainly means `deref==null` (the `pollfRef` field is never null at that point), so the poll-runnable-collected cancellation branch is unreachable.
- `_write` nests a redundant `synchronized(this)` inside an already-`synchronized` method.

## Related

- [`PreferencesBackedValue`](PreferencesBackedValue.md) — the `Preferences`-node analogue.
- [`PrefInterop`](PrefInterop.md) — factories over a `Preferences` node.
- [`AlwaysValid`](../../aspect/AlwaysValid.md) — the always-valid mixin and its other consumers.
- [overview.md](../../../overview.md) · [transactions.md](../../../concepts/transactions.md) — the valid/invalid + transaction model these classes opt out of.
