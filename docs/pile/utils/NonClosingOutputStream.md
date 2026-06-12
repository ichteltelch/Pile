# `NonClosingOutputStream`

An `OutputStream` decorator whose `close()` flushes the delegate instead of closing it.

Source folder: `src`. Package `pile.utils`.

## What it's for

Hand a wrapped `OutputStream` to code that will `close()` it (e.g. a serializer, `try-with-resources`), while keeping the underlying stream open for further writing. Mirror of the sibling [`NonClosingInputStream.md`](NonClosingInputStream.md).

## Behavior

Extends `OutputStream` directly (not `FilterOutputStream`) and forwards everything to the `wrapped` stream:

- `write(int)`, `write(byte[])`, `write(byte[], int, int)` — all forwarded verbatim. The two array overrides are explicit, so the `byte[]` write goes straight to `wrapped.write(byte[])` (it is **not** decomposed into per-byte calls the way `OutputStream`'s default array methods would be).
- `flush()` — forwarded.
- `close()` — does **not** close `wrapped`; it calls `wrapped.flush()` and increments `closeAttempts`. The delegate stays open.
- `getCloseAttempts()` — how many times `close()` was called (each call re-flushes; there is no idempotency guard).

## Caveats

- Repeated `close()` calls keep flushing; nothing prevents writing after a "close".
- Closing the underlying stream is the caller's responsibility — this wrapper never does it.

Up: [`utils` index](_index.md) · [overview](../../overview.md).
