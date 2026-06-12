# `LastValueRemembererDouble`

Pure narrowing of [`LastValueRememberer`](../../aspect/LastValueRememberer.md) to `Double`; no new members.

Source folder: `src` · Package: `pile.specialized_double.combinations`

`LastValueRemembererDouble extends LastValueRememberer<Double>`. The sole purpose is type narrowing so that consumers requiring `LastValueRememberer<Double>` can receive a double-specialized value without a cast.

## See also
- [LastValueRememberer](../../aspect/LastValueRememberer.md) — the generic interface
- [combinations index](_index.md)
