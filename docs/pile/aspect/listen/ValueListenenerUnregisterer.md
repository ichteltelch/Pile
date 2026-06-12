# `ValueListenenerUnregisterer`

Bundles `ValueListener`s with the `ListenValue`s they were added to, so they can all be unregistered later in one call.

Source folder: `src` (package `pile.aspect.listen`).

## What it's for

A `ValueListenenerUnregisterer` is a small accounting helper. When you register a listener you normally have to hold on to both the listener **and** the value to be able to remove it again. This interface lets you register listeners through it and forget the bookkeeping: each `add`/`addWeak` records the `(listener, value)` pair, and a single `run` (or `close`) removes them all.

It extends both [`Runnable`](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) and `SafeCloseable` (`pile.aspect.suppress.SafeCloseable`), so it doubles as a cleanup handle — usable as the body of a try-with-resources or as a teardown `Runnable`.

## Key methods

- `add(ValueListener c, ListenValue v)` — register `c` on `v` (strongly), remembering the pair for later removal on `run`.
- `addWeak(ValueListener c, ListenValue v)` — same, but registers via `ListenValue.addWeakValueListener`, so the listener can be garbage-collected if nothing else references it (see [`WeakValueListener`](WeakValueListener.md)). Returns the `ValueListener` that was actually added (the weak wrapper).
- `run` — unregister every listener added so far. Inherited from `Runnable`.
- `close` — `default`, just calls `run` (so it works in try-with-resources).

## Salient behavior

- `run` and `close` are the **same action**; `close` is only a try-with-resources alias.
- `addWeak` returns a listener (the weak wrapper) because that wrapper — not the original `c` — is what got registered; callers that need the wrapper (e.g. to keep a strong reference and prevent premature collection) get it back.

## Caveats & gotchas

- This is only the **interface** (a contract for "unregisterers"). The behavior after `run` — whether the same instance can be reused for a fresh batch, or is single-shot — is up to the implementation and not specified here.
- Weak vs. strong is the caller's choice per call; mixing both on one unregisterer is fine.

## Tech debt / warts

- **The type name is misspelled**: `ValueListenenerUnregisterer` (note the stray `en` — should be `ValueListenerUnregisterer`). The doc filename intentionally matches the real class name. Renaming would be an API break, so it is left as-is.

## See also

- [`ListenValue`](ListenValue.md) — the observable values listeners are added to.
- [`ValueListener`](ValueListener.md) — the listener callback being registered/unregistered.
- [`WeakValueListener`](WeakValueListener.md) — what `addWeak` uses under the hood.
- Package index: [`_index.md`](_index.md) · Project [overview](../../../overview.md) · [concepts](../../../concepts/)
