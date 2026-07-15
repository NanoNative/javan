# Real Project Readiness

Status summary:

| Project | Status | Current evidence | Missing release gate |
| --- | --- | --- | --- |
| TypeMap | Partial | `typemap-pair` builds natively against the pinned Maven artifact and is exercised by required CI acceptance plus focused CLI integration. | Broader TypeMap dependency graph and quieter unreachable-dependency diagnostics. |
| Nano metrics helper | Partial | `nano-metric` builds natively against the pinned Maven artifact and is exercised by required CI acceptance plus focused CLI integration. | Broader Nano dependency graph and quieter unreachable-dependency diagnostics. |
| Nano duration helper | Partial | `nano-duration` builds natively against the pinned Maven artifact and is exercised by required CI acceptance plus focused CLI integration. | Broader Nano helper surface and quieter unreachable-dependency diagnostics. |
| Nano scheduler lifecycle | Partial | `nano-scheduler` and `nano-scheduler-fixed-rate` build natively against the pinned Maven artifact and are exercised by required CI acceptance plus focused CLI integration. | Broader Nano service graph and scheduler-adjacent runtime coverage beyond the current lifecycle slice. |
| Nano HTTP service | Planned | Nano-style `HttpServer` dependency now fails clearly with `JAVAN061` and reports `network/http`. | Broader HTTP service runtime, resources, thread/blocking model, and dev-console/reflection exclusion. |

Current compatibility probes:

- TypeMap: `src/test/resources/projects/real-probes/typemap-pair` builds against the pinned Maven-cache TypeMap jar by default and prints `value`.
- Nano: `src/test/resources/projects/real-probes/nano-metric` builds against the pinned Maven-cache Nano jar by default and prints `requests`.
- Nano duration example slice: `src/test/resources/projects/real-probes/nano-duration` builds against the pinned Maven-cache
  Nano jar by default and prints `1m 5s` using `NanoUtils.formatDuration(long)`, the helper
  used by the upstream example's `/load1` response path. `DevConsoleService` is not
  included.
- Nano scheduler lifecycle slice: `src/test/resources/projects/real-probes/nano-scheduler` and
  `src/test/resources/projects/real-probes/nano-scheduler-fixed-rate` build against the pinned
  Maven-cache Nano jar by default and prove one-shot scheduling, fixed-rate scheduling shutdown,
  and `awaitTermination(...)` through the real `Scheduler` type.

`.github/scripts/acceptance.sh` now auto-discovers these pinned Maven artifacts and CI prefetches
them before `mvn verify`, so the five probes are required in the acceptance gate. Local runs still
skip cleanly when the artifacts are absent.

These probes prove that the backend can consume real dependency bytecode for simple object constructors, object fields, object returns, object arrays, records, scalar long/float/double operations, primitive arrays, basic enum names, closed-world virtual/interface dispatch, static fields, reachable class initializers, javac string concatenation, basic string intrinsics, selected Nano static helper code, direct same-method exception catches, uncaught panic-style exceptions, and concrete instance calls.

Known blockers before broader TypeMap/Nano coverage:

- the current main-based Nano frontier has moved through the scheduler/bootstrap worker slice: `ScheduledThreadPoolExecutor.<init>(int)`, `ScheduledThreadPoolExecutor.<init>(int,ThreadFactory,RejectedExecutionHandler)`, same-method static virtual-thread builder/factory bootstrap, runtime ownership for `Scheduler extends ScheduledThreadPoolExecutor`, `AtomicLong(long|get|incrementAndGet|decrementAndGet)`, `ExecutorService.submit(Runnable)`, `Future.cancel(boolean)`, `schedule(...)`, `scheduleAtFixedRate(...)`, `awaitTermination(...)`, and `shutdownNow()` are now integrated and acceptance-backed; the next concrete Nano blocker is the broader HTTP service graph
- only the current blocking TCP loopback socket slice is implemented
- only raw loopback HTTP responder slices are verified so far (`GET` success, unmatched-route `404`, POST body handling, sequential two-connection lifetime, method-plus-path dispatch, multi-class route-handler dispatch, request-header matching, response-header emission, and a request/response object model over router and service classes); no higher-level native HTTP server API yet
- no native HTTPS/TLS runtime yet
- no certificate/trust-store model yet
- no thread-root model for network service lifetimes yet
- richer class initialization ordering across complex dependency graphs
- full enum initialization beyond basic constant names
- `invokedynamic` lambdas and dynamic-call sites
- non-ASCII/full UTF-16 `String` runtime semantics
- general try/catch/finally exception-handler lowering
- common JDK intrinsics for `String`, collections, streams, `Optional`, and broader atomics

Fresh Nano packaging may still fail if a broader Nano graph resolves a TypeMap version
that does not provide `JsonDecoder.jsonTypeOf(String)`. The `src/test/resources/projects/real-probes/nano-metric` probe
still accepts `NANO_CLASSES=/path/to/nano/target/classes` as a local fallback, but the release gate now uses the pinned
published Nano jar rather than a sibling checkout.

Next gates before claiming Nano support:

1. done: make the five real probes reproducible and required in at least one CI row
2. done: add negative diagnostics for `Socket`, `ServerSocket`, `HttpClient`, and Nano-style `HttpServer`
3. done: report reachable `network`, `socket`, and `http` usage even while unsupported
4. implement TCP loopback support with close/ownership and sanitizer proof
5. done partially: implement plain HTTP client loopback support for GET/string, POST+headers/byte[], and PUT byte[], plus raw loopback responder slices over `ServerSocket`/`Socket` for `GET /hello -> 200 pong`, unmatched-route `404`, POST body handling via `Content-Length`, sequential two-connection lifetime, method-plus-path dispatch, multi-class route-handler dispatch, request-header matching, response-header emission, and a request/response object model over router and service classes
6. run the Nano example without dev console/reflection-heavy paths as a native service
7. add HTTPS/TLS/certificates after plain HTTP is stable
