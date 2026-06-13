# Real Project Readiness

Current compatibility probes:

- TypeMap: `examples/typemap-pair` builds against a configured or local TypeMap jar and prints `value`.
- Nano: `examples/nano-metric` builds against configured or local Nano classes and prints `requests`.

`scripts/acceptance.sh` runs these checks when the local artifacts are available and records
an explicit skip when they are not. They are compatibility probes, not required release
inputs, until the dependencies are pinned or fetched reproducibly.

These probes prove that the backend can consume real dependency bytecode for simple object constructors, object fields, object returns, object arrays, records, scalar long/float/double operations, primitive arrays, basic enum names, closed-world virtual/interface dispatch, static fields, reachable class initializers, javac string concatenation, basic string intrinsics, direct same-method exception catches, uncaught panic-style exceptions, and concrete instance calls.

Known blockers before broader TypeMap/Nano coverage:

- richer class initialization ordering across complex dependency graphs
- full enum initialization beyond basic constant names
- `invokedynamic` lambdas and dynamic-call sites
- non-ASCII/full UTF-16 `String` runtime semantics
- general try/catch/finally exception-handler lowering
- common JDK intrinsics for `String`, collections, streams, `Optional`, and atomics

Fresh Nano packaging may still fail if the local Nano checkout resolves a TypeMap version
that does not provide `JsonDecoder.jsonTypeOf(String)`. The `examples/nano-metric` probe
therefore accepts `NANO_CLASSES=/path/to/nano/target/classes` and does not make broader
Nano packaging a `javan` release gate yet.
