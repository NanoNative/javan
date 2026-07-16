# Real Project Readiness

This ledger is the only place where named third-party compatibility probes are allowed to stay
hardcoded. They are pinned moving snapshots of upstream artifacts, not compiler-owned support
claims and not internal fixtures.

Probe summary:

| Probe class | Probe status | Current evidence | Missing release gate |
| --- | --- | --- | --- |
| External library artifact smoke | Smoke | One pinned third-party library probe builds natively against its published artifact and is exercised by the required external-probe acceptance gate plus focused CLI integration. | Broader external-library dependency graphs and quieter unreachable-dependency diagnostics. |
| External helper/library smoke | Smoke | Several pinned third-party helper/library probes build natively against their published artifacts and are exercised by the required external-probe acceptance gate plus focused CLI integration. | Broader helper surfaces, broader dependency graphs, and quieter unreachable-dependency diagnostics. |
| External scheduler/runtime smoke | Smoke | Several pinned third-party scheduler/runtime probes build natively against their published artifacts and are exercised by the required external-probe acceptance gate plus focused CLI integration. | Broader service graphs and scheduler-adjacent runtime coverage beyond the current lifecycle slice. |
| External HTTP service smoke | Planned smoke | One pinned external HTTP-service-shaped probe currently fails clearly with `JAVAN061` and reports `network/http`. | Broader HTTP service runtime, resources, thread/blocking model, and dev-console/reflection exclusion. |

These external probes are intentionally excluded from `doc/status/support-matrix.*`,
`doc/status/jdk-compatibility.md`, and the core JDK support ledger. They are compatibility
smoke only, driven by per-probe metadata and exact stdout expectations under
`src/test/resources/projects/real-probes/*`.

Read that literally: upstream project identities are not part of javan's compiler knowledge. They
are moving upstream artifacts. Javan is only allowed to know them inside the dedicated
external-smoke boundary. If one of them exposes a compiler gap, the permanent fix belongs in a
generic JDK/runtime regression first, then the probe stays only as a published-artifact
compatibility check.

Each probe now also declares `genericEvidence=...` in `probe.properties`. That metadata must point
at an existing compiler-owned generic regression test, so a real-project smoke case cannot exist
without a project-neutral proof in the main javan test line.

The acceptance harness now reads all real-probe metadata through one shared test-only catalog. The
catalog is allowed to load probe names and coordinates from metadata; the harness itself and the
compiler-owned support line must stay project-neutral.

They are not static compiler knowledge. Upstream project code may change at any time. Javan is
allowed to keep named smoke probes here only as compatibility evidence for the currently pinned
artifacts; support claims must still be expressed in generic JDK/runtime terms elsewhere.

Read that as a moving snapshot, not as a frozen allowlist. The current probe directories may
change, grow, or disappear over time. Javan must still stay project-neutral outside this dedicated
smoke boundary.

Rule:

- if a real probe breaks, fix the compiler/runtime gap in a generic JDK-support test first
- then keep or update the external probe only as upstream compatibility smoke
- never add a Nano- or TypeMap-named support row, intrinsic, substitution, or verifier rule

Compiler-owned regression coverage for the same shapes lives under `src/test/java/javan/*`
using synthetic dependency jars and projects. The external probes are allowed to fail only as
real-project compatibility evidence, never as the definition of a JDK support claim.

Boundary rules:

- compiler-owned tests must stay generic and project-neutral
- support rows, intrinsics, substitutions, and verifier rules must stay JDK/runtime-shaped
- external project names may stay hardcoded only in:
  - `src/test/resources/projects/real-probes/*`
  - this document

The dedicated acceptance harness, generic dependency regressions, and status dashboards are all
expected to remain metadata-driven and free of hardcoded probe identities.

When one of these probes finds a gap, the durable fix must be captured by compiler-owned tests
that prove the JDK/runtime shape directly, without naming the upstream project in the core support
line. Probe metadata under `src/test/resources/projects/real-probes/*` and this dedicated ledger
are intentionally the only places where those project identities may stay hardcoded for acceptance;
Javan itself must not encode project-specific support rules.

Current pinned compatibility snapshot:

- TypeMap: `src/test/resources/projects/real-probes/typemap-pair` builds against the pinned Maven-cache TypeMap jar by default and prints `value`.
- Nano metrics helper: `src/test/resources/projects/real-probes/nano-metric` builds against the pinned Maven-cache Nano jar by default and prints `requests`.
- Nano duration example slice: `src/test/resources/projects/real-probes/nano-duration` builds against the pinned Maven-cache
  Nano jar by default and prints `1m 5s` using `NanoUtils.formatDuration(long)`, the helper
  used by the upstream example's `/load1` response path. `DevConsoleService` is not
  included.
- Nano scheduler lifecycle slice: `src/test/resources/projects/real-probes/nano-scheduler` and
  `src/test/resources/projects/real-probes/nano-scheduler-fixed-rate` build against the pinned
  Maven-cache Nano jar by default and prove one-shot scheduling, fixed-rate scheduling shutdown,
  and `awaitTermination(...)` through the real `Scheduler` type.

Compiler-owned generic equivalents:

| External probe shape | Compiler-owned regression evidence |
| --- | --- |
| Pair getter from third-party jar | `CliDependencyProjectIntegrationTest.dependencyJarGenericPairGetterBuilds` |
| Nullable multi-field record constructor plus accessor | `CliDependencyProjectIntegrationTest.dependencyJarNullableRecordAccessorBuilds` |
| Static helper returning formatted duration text | `CliDependencyProjectIntegrationTest.dependencyJarStaticDurationFormatterBuilds` |
| Scheduled executor subclass with one-shot task | `CliDependencyProjectIntegrationTest.dependencyJarScheduledExecutorSubclassBuilds` |
| Scheduled executor subclass with fixed-rate scheduling plus shutdown/awaitTermination before first fire | `CliDependencyProjectIntegrationTest.dependencyJarScheduledExecutorFixedRateBuilds` |

That mapping is the rule: when an external probe breaks, the permanent fix belongs in one of these
generic compiler-owned dependency tests or a new generic equivalent, not in a Nano/TypeMap-specific
support row.

`.github/scripts/acceptance.sh` now auto-discovers probe directories and CI prefetches the pinned
artifacts from probe metadata via `.github/scripts/list-real-probe-artifacts.sh` before `mvn verify`,
so the current discovered probe set is required in the external-probe acceptance gate.
Local runs still skip cleanly when the declared dependency is absent.

These probes prove that the backend can consume real dependency bytecode for simple object constructors, object fields, object returns, object arrays, records, scalar long/float/double operations, primitive arrays, basic enum names, closed-world virtual/interface dispatch, static fields, reachable class initializers, javac string concatenation, basic string intrinsics, exact `LambdaMetafactory` `Function`/`Predicate` bridges into `Optional.filter`, `Optional.map`, and `Map.computeIfAbsent`, selected Nano static helper code, direct same-method exception catches, uncaught panic-style exceptions, and concrete instance calls.

Known blockers before broader real-project coverage:

- broader dependency-jar surface beyond the current smoke slices, especially deeper class-initialization graphs, richer collection/map helpers, additional atomics, more functional-interface shapes, and wider string/temporal helper families
- broader service runtime coverage, especially HTTP server APIs above the current raw loopback responder slices, HTTPS/TLS, certificates, long-lived service thread ownership, and resource-heavy app packaging
- richer Java semantic coverage, especially general try/catch/finally lowering, broader dynamic-call sites, wider enum/class-introspection edges, and full UTF-16 string semantics
- more dependency-version variance proof, because pinned artifact smoke is not the same as broad upstream-version compatibility

This document stays at the compatibility-smoke level. Detailed compiler/runtime support claims
belong in `doc/status/support-matrix.md`, `doc/status/jdk-compatibility.md`, and the
compiler-owned tests under `src/test/java/javan/*`.

Fresh Nano packaging may still fail if a broader Nano graph resolves a TypeMap version
that does not provide `JsonDecoder.jsonTypeOf(String)`. The `src/test/resources/projects/real-probes/nano-metric` probe
accepts explicit `NANO_JAR`, `NANO_CLASSPATH`, or `NANO_CLASSES` overrides for local investigation,
but the release gate uses the pinned published Nano jar rather than a sibling checkout.

Next gates before claiming Nano support:

1. done: make the current real-probe set reproducible and required in at least one CI row
2. done: add negative diagnostics for `Socket`, `ServerSocket`, `HttpClient`, and Nano-style `HttpServer`
3. done: report reachable `network`, `socket`, and `http` usage even while unsupported
4. implement TCP loopback support with close/ownership and sanitizer proof
5. done partially: implement plain HTTP client loopback support for GET/string, POST+headers/byte[], and PUT byte[], plus raw loopback responder slices over `ServerSocket`/`Socket` for `GET /hello -> 200 pong`, unmatched-route `404`, POST body handling via `Content-Length`, sequential two-connection lifetime, method-plus-path dispatch, multi-class route-handler dispatch, request-header matching, response-header emission, and a request/response object model over router and service classes
6. run the Nano example without dev console/reflection-heavy paths as a native service
7. add HTTPS/TLS/certificates after plain HTTP is stable
