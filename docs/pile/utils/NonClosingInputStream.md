# `NonClosingInputStream`

An `InputStream` decorator whose `close` does not close the wrapped stream — it just counts the attempt.

Source folder: `src`. Package `pile.utils`.

Up: [utils index](_index.md) · [overview](../../overview.md). Sibling: [`NonClosingOutputStream`](NonClosingOutputStream.md).

## What it's for

Wrap a shared or long-lived `InputStream` so a consumer can use try-with-resources (or otherwise call `close()`) without actually closing the underlying stream. The caller keeps ownership of the real stream's lifecycle.

## Behavior

- **`close()`** does **not** close, flush, or otherwise touch the wrapped stream. It only increments a counter (`closeAttempts`). It is `synchronized` (the only synchronized method). Note: unlike most of the class it is *not* a forwarding method — there is no flush-on-close, nothing is propagated downstream.
- **`getCloseAttempts()`** returns how many times `close()` was called — useful to detect callers that closed a stream they did not own.
- Every other method (`read` in all three overloads, `skip`, `available`, `mark`, `markSupported`, `reset`) is a **plain pass-through** to the wrapped stream. The class extends `InputStream` directly (not `FilterInputStream`) and forwards manually.

## Caveats & gotchas

- `read()`/`read(byte[])`/`read(byte[],int,int)` are forwarded individually, so `read(byte[])`'s contract follows the *wrapped* stream's implementation, not `InputStream`'s default-in-terms-of-`read()` behavior.
- `mark`/`reset` support depends entirely on the wrapped stream (`markSupported()` is forwarded).
- The wrapped stream is never closed by this class — that is the whole point; closing it remains the caller's responsibility.
