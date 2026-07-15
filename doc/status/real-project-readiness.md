# Real Project Readiness

Status summary:

| Project | Status | Current evidence | Missing release gate |
| --- | --- | --- | --- |
| TypeMap | Partial | `typemap-pair` builds natively against the pinned Maven artifact and is exercised by required CI acceptance plus focused CLI integration. | Broader TypeMap dependency graph and quieter unreachable-dependency diagnostics. |
| Nano metrics helper | Partial | `nano-metric` builds natively against the pinned Maven artifact and is exercised by required CI acceptance plus focused CLI integration. | Broader Nano dependency graph and quieter unreachable-dependency diagnostics. |
| Nano duration helper | Partial | `nano-duration` builds natively against the pinned Maven artifact and is exercised by required CI acceptance plus focused CLI integration. | Broader Nano helper surface and quieter unreachable-dependency diagnostics. |
| Nano scheduler lifecycle | Partial | `nano-scheduler` and `nano-scheduler-fixed-rate` build natively against the pinned Maven artifact and are exercised by required CI acceptance plus focused CLI integration. | Broader Nano service graph and scheduler-adjacent runtime coverage beyond the current lifecycle slice. |
| Nano HTTP service | Planned | Nano-style `HttpServer` dependency now fails clearly with `JAVAN061` and reports `network/http`. | Broader HTTP service runtime, resources, thread/blocking model, and dev-console/reflection exclusion. |

These external probes are intentionally excluded from `doc/status/support-matrix.*` so the
JDK support ledger stays limited to synthetic/compiler-owned scenarios.

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

These probes prove that the backend can consume real dependency bytecode for simple object constructors, object fields, object returns, object arrays, records, scalar long/float/double operations, primitive arrays, basic enum names, closed-world virtual/interface dispatch, static fields, reachable class initializers, javac string concatenation, basic string intrinsics, exact `LambdaMetafactory` `Function`/`Predicate` bridges into `Optional.filter`, `Optional.map`, and `Map.computeIfAbsent`, selected Nano static helper code, direct same-method exception catches, uncaught panic-style exceptions, and concrete instance calls.

Known blockers before broader TypeMap/Nano coverage:

- the current main-based Nano frontier has moved through the scheduler/bootstrap worker slice, the Nano service bootstrap `AtomicBoolean` subset, the `ConfigRegister` functional bridge, the current byte-string normalization subset (`String.strip`, `String.toLowerCase`), the current `CharSequence.length`/`CharSequence.charAt` plus `Character.isWhitespace` bridge used by `NanoUtils.containsText(...)`, the current `Class`/`Object`/`Collections.emptyMap`/`Map.entrySet` subset used by `TypeConverter.convertObj(...)`, the current `AtomicInteger` subset used by `NanoBase.<clinit>()`, the current exact catch-null enum lookup slice used by `TypeConverter.enumOf(...)`, the exact `DateTimeFormatter` constant plus narrow `DateTimeFormatterBuilder` static-init path used by `TypeConversionRegister.<clinit>()`, the current built-in `instanceof Collection/Map/Map.Entry` plus `Collection.isEmpty()` bridge used by `TypeConverter.getFirstItem(...)`, the exact catch-null custom functional-interface bridge used by the current TypeMap lambda path, the recoverable nested panic scope needed for that bridge, builtin `instanceof [Ljava/lang/Object;` plus all primitive array variants used by `TypeConverter.iterateOverArray(...)`, the first materialized `Consumer.accept(Object)` dispatch slice for object-input, object-capture lambdas, the widened explicit unsupported temporal-conversion lambda bridge for epoch-millis boxing into `Long`, the exact `TypeConversionRegister.temporalOf(...)` loop/fallback bytecode shape, the exact `String -> temporal target` registration bridges that feed it, the exact `calendarOf(long|Date|LocalTime)` helper shapes those registrations pull in, the exact `stringOf(Throwable)` helper shape, and the widened current linear unsupported temporal/sql conversion-lambda subset returning unsupported temporal/sql/date/calendar targets. These helper/conversion methods are compiled as explicit unsupported-runtime bridges today instead of fake `java.time`/`Calendar`/`java.sql` support, which is enough to move the real ConfigRegister frontier deeper without claiming those runtime objects are implemented: `ScheduledThreadPoolExecutor.<init>(int)`, `ScheduledThreadPoolExecutor.<init>(int,ThreadFactory,RejectedExecutionHandler)`, same-method static virtual-thread builder/factory bootstrap, runtime ownership for `Scheduler extends ScheduledThreadPoolExecutor`, `AtomicLong(long|get|incrementAndGet|decrementAndGet)`, `AtomicBoolean.<init>()`, `AtomicBoolean.<init>(boolean)`, `AtomicBoolean.get()`, `AtomicInteger.<init>()`, `AtomicInteger.<init>(int)`, `AtomicInteger.get()`, `AtomicInteger.getAndIncrement()`, `AtomicInteger.incrementAndGet()`, `AtomicInteger.decrementAndGet()`, `ExecutorService.submit(Runnable)`, `Future.cancel(boolean)`, `schedule(...)`, `scheduleAtFixedRate(...)`, `awaitTermination(...)`, `shutdownNow()`, exact `LambdaMetafactory` `Function`/`Predicate` lowering, exact zero-capture catch-null object-SAM materialization, the current materialized `LambdaMetafactory` `Consumer.accept(Object)` dispatch slice for object captures, the current explicit unsupported `lambda$static$*(TemporalLike) -> Long.valueOf(toInstant().toEpochMilli())` bridge family, the exact `getstatic java/time/LocalDate.MIN -> java/sql/Date.valueOf(LocalDate)` helper shape, the exact `Long.longValue() -> toTimestampMs(J) -> new java/sql/Timestamp(long)` helper shape, `Optional.filter(Predicate)`, `Optional.map(Function)`, `Map.computeIfAbsent(Object,Function)`, `String.strip()`, `String.toLowerCase()`, `CharSequence.length()`, `CharSequence.charAt(int)`, `Character.isWhitespace(char)`, `Character.valueOf(char)`, `Character.charValue()`, wrapper `instanceof java/lang/Character`, `Class.isInstance(Object)`, `Class.cast(Object)`, `Class.isEnum()`, `Class.isArray()`, `Class.isAssignableFrom(Class)`, `Class.getName()`, `Object.getClass()`, `Object.equals(Object)`, `Collections.emptyMap()`, `Map.entrySet()`, `Map.Entry.getKey()`, `Map.Entry.getValue()`, `Set.iterator()`, `Collection.isEmpty()`, builtin `instanceof java/util/Collection`, builtin `instanceof java/util/Map`, builtin `instanceof java/util/Map$Entry`, builtin `instanceof [Ljava/lang/Object;`, builtin primitive-array `instanceof` targets, `ldc` class literals, and the exact `Object/Class -> enum or null` bytecode shape used by `TypeConverter.enumOf(...)` are now integrated and acceptance-backed or CLI-native parity-backed where covered; the pinned `ConfigRegister` probe currently fails deeper at a traversal-order-dependent frontier set: either `TypeConverter.iterateOverArray(Ljava/lang/Object;Ljava/util/function/Consumer;)V` on `java/lang/Byte.valueOf(B)Ljava/lang/Byte;` (`JAVAN031`) or `TypeConversionRegister.lambda$static$19(Ljava/lang/String;)Ljava/net/Inet6Address;` on `java/net/InetAddress.getByName(Ljava/lang/String;)Ljava/net/InetAddress;` (`JAVAN061`).
- only the current blocking TCP loopback socket slice is implemented
- only raw loopback HTTP responder slices are verified so far (`GET` success, unmatched-route `404`, POST body handling, sequential two-connection lifetime, method-plus-path dispatch, multi-class route-handler dispatch, request-header matching, response-header emission, and a request/response object model over router and service classes); no higher-level native HTTP server API yet
- no native HTTPS/TLS runtime yet
- no certificate/trust-store model yet
- no thread-root model for network service lifetimes yet
- richer class initialization ordering across complex dependency graphs
- full enum initialization beyond basic constant names
- `LocalDate` constant/value bridging for the current `LocalTime -> java.sql.Date` helper path, materialized custom functional-interface lambdas beyond the current exact `Function`/`Predicate` plus object-capture `Consumer.accept(Object)` bridge, default-method dispatch through custom SAMs, and broader dynamic-call sites
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
