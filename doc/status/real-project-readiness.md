# Real Project Readiness

This ledger describes the dedicated external-probes boundary without teaching Javan any
project-specific compiler semantics. Hardcoded external artifact coordinates and package names
belong only in the probe metadata, bundled artifact sources under
`src/test/resources/external-artifacts/*`, and the tiny probe apps under
`src/test/resources/external-probes/*`. They are compatibility smoke only, not compiler-owned
support claims and not product knowledge.

Probe summary:

| Probe class | Probe status | Current evidence | Missing release gate |
| --- | --- | --- | --- |
| External library artifact smoke | Smoke | One bundled external library probe builds natively against a reproducible jar installed into the local Maven repository and is exercised by the required external-probe acceptance gate plus focused CLI integration. | Broader external-library dependency graphs and quieter unreachable-dependency diagnostics. |
| External helper/library smoke | Smoke | Several bundled helper/library probes build natively against reproducible jars installed into the local Maven repository and are exercised by the required external-probe acceptance gate plus focused CLI integration. | Broader helper surfaces, broader dependency graphs, and quieter unreachable-dependency diagnostics. |
| External scheduler/runtime smoke | Smoke | Several bundled scheduler/runtime probes build natively against reproducible jars installed into the local Maven repository and are exercised by the required external-probe acceptance gate plus focused CLI integration. | Broader service graphs and scheduler-adjacent runtime coverage beyond the current lifecycle slice. |
| External HTTP service smoke | Partial smoke | Compiler-owned public-entrypoint tests now prove one-context `com.sun.net.httpserver.HttpServer` bind/context/start/request/response/stop parity, `HttpServer.getAddress()` local endpoint parity, `HttpExchange.getLocalAddress()`/`getRemoteAddress()` endpoint parity, `HttpExchange.getResponseCode()` state parity, `HttpExchange.getProtocol()` plain HTTP/1.1 protocol parity, malformed request-target percent-escape rejection with HTTP 400, malformed/conflicting `Content-Length`, malformed header names, and unsupported transfer framing rejection with HTTP 400, actual POST `HttpExchange.getRequestMethod()` dispatch, bounded `Content-Length` and chunked transfer encoding through `HttpExchange.getRequestBody().readAllBytes()`, `/hello?mode=full` request path/query parity through `HttpExchange.getRequestURI()`, decoded and raw URI request-target parity, concurrent request dispatch through rooted native workers with stop-time draining, configured virtual-thread executor dispatch through `HttpServer.setExecutor(Executor)`, pre-start `HttpServer.removeContext(String)` routing removal plus default 404-body parity, live `Headers.keySet()`/`Headers.values()` mutation, live `Headers.entrySet()` replacement/removal, case-insensitive `X-Mode` lookup through `HttpExchange.getRequestHeaders().getFirst(String)`, `Headers.containsKey(Object)` presence checks, unique-key `Headers.size()`/`isEmpty()` cardinality, mutable-list response replacement through covariant `Headers.put(String,List<String>)`, `Headers.putIfAbsent(Object,Object)` absent/present insertion parity, `Headers.remove(Object,Object)` mismatched/matching conditional-removal parity, `Headers.getOrDefault(Object,Object)` present/missing fallback parity, `Headers.replace(Object,Object)` present/absent replacement parity, `Headers.replace(Object,Object,Object)` mismatched/matching conditional-replacement parity, `Headers.putAll(Map)` bulk replacement/addition, ordered value-list matching through `Headers.containsValue(Object)`, ordered removal of duplicate response values through covariant `Headers.remove(Object)`, response-header clearing through `Headers.clear()`, `X-mode: strict` response-header wire emission through `HttpExchange.getResponseHeaders().set(String,String)`, ordered duplicate request/response header values through `Headers.get(Object)`/`add(String,String)`, unmatched one-context request targets returning 404, multiple registered contexts selecting the correct handler, and sequential requests served until `HttpServer.stop()` against the JVM; the pinned external HTTP-service-shaped probe still fails clearly with `JAVAN061` and reports `network/http`. | Broader external HTTP service runtime, arbitrary executor policies, richer chunked framing/client response decoding, resources, thread/blocking model, and dev-console/reflection exclusion. |

These external probes are intentionally excluded from `doc/status/support-matrix.*`,
`doc/status/jdk-compatibility.md`, and the core JDK support ledger. They are compatibility
smoke only, driven by per-probe metadata and exact stdout expectations under
`src/test/resources/external-probes/*`.

The probe directories themselves are intentionally generic `artifact-*` names. Upstream
identities stay in `probe.properties` and the tiny Java source that imports the external
classes, not in compiler-owned directory labels.

Those probe labels are not product vocabulary either. They are temporary smoke handles for the
current pinned external set, and they are allowed to change without renaming compiler-owned tests,
support ledgers, or JDK accounting.

The current pinned set is a reproducible external-artifact slice checked into this repository.
That is still not product knowledge, not a support allowlist, and not a stable contract. The
compiler-owned test and support line must remain generic so the same regressions still make sense
after the probe set changes.

That includes any live external example repository tested outside this deterministic gate. Those
projects may evolve independently. Javan must not encode their names, packages, or semantics
anywhere in the compiler-owned support line.

Read that literally: project identities are not part of javan's compiler knowledge. Javan is only
allowed to know them inside the dedicated external smoke boundary. Compiler-owned tests must stay
generic even when an external probe breaks. If one of them exposes a compiler gap, the permanent
fix belongs in a generic JDK/runtime regression first, then the probe stays only as a
compatibility check.

Each probe now also declares `genericEvidence=...` in `probe.properties`. That metadata must point
at an existing compiler-owned generic regression test, so a real-project smoke case cannot exist
without a project-neutral proof in the main javan test line.

If a smoke probe stands for a live upstream repository or example service, its repository name,
marketing name, shorthand, and package roots must stay only in probe metadata such as
`identityAliases` and `identityPackages`. Those aliases are treated as forbidden identities
outside the dedicated smoke boundary.

The acceptance harness now reads all real-probe metadata through one shared test-only catalog. The
catalog is allowed to load probe names and coordinates from metadata; the harness itself, this
ledger, and the compiler-owned support line must stay project-neutral.

Today the pinned probes point at bundled reproducible artifacts, but that is still incidental
evidence, not product knowledge. If the pinned set changes tomorrow, the compiler-owned support
line and its generic regression names must still read the same.

The acceptance harness must also stay directory-name neutral. Probe metadata may use a project
name that differs from the on-disk probe directory; the harness must copy and run the probe from
the catalog-provided directory path rather than assuming any external identity maps to a fixed
resource name.

Local probe helper scripts now follow the same rule: they resolve classpaths from the current
probe's `probe.properties` metadata and generic `JAVAN_PROBE_*` overrides instead of probe-named
environment variables or hardcoded artifact paths.

They are not static compiler knowledge. Live external project code may change at any time. Javan is
allowed to keep named smoke probes here only as compatibility evidence for the currently pinned
artifact shapes; support claims must still be expressed in generic JDK/runtime terms elsewhere.

Read that as a moving snapshot, not as a frozen allowlist. The current probe directories may
change, grow, or disappear over time. Javan must still stay project-neutral outside this dedicated
smoke boundary.

Rule:

- if a real probe breaks, fix the compiler/runtime gap in a generic JDK-support test first
- then keep or update the external probe only as upstream compatibility smoke
- never add an upstream-project-named support row, intrinsic, substitution, or verifier rule
- never add a probe-project label such as `artifact-*` to compiler-owned support rows, JDK
  accounting, or milestone ledgers

The metadata fence also covers upstream package identities. Probe metadata declares the external
package roots that are allowed only inside the dedicated smoke boundary, and isolation tests fail
if those package names leak into compiler-owned tests, support ledgers, product code, or workflow
scripts.

Compiler-owned regression coverage for the same shapes lives under `src/test/java/javan/*`
using synthetic dependency jars and projects. The external probes are allowed to fail only as
real-project compatibility evidence, never as the definition of a JDK support claim.

Boundary rules:

- compiler-owned tests must stay generic and project-neutral
- support rows, intrinsics, substitutions, and verifier rules must stay JDK/runtime-shaped
- external project names may stay hardcoded only in:
  - `src/test/resources/external-probes/*/probe.properties`
  - `src/test/resources/external-probes/*/src/main/java/**`

The dedicated acceptance harness, generic dependency regressions, and status dashboards are all
expected to remain metadata-driven and free of hardcoded probe identities.

When one of these probes finds a gap, the durable fix must be captured by compiler-owned tests
that prove the JDK/runtime shape directly, without naming the upstream project in the core support
line. Probe metadata and tiny probe source under `src/test/resources/external-probes/*` are
intentionally the only places where those project identities may stay hardcoded for acceptance;
Javan itself must not encode project-specific support rules.

Current discovered compatibility shapes:

| External probe shape | Compiler-owned regression evidence |
| --- | --- |
| Generic object getter from third-party jar | `CliDependencyProjectIntegrationTest.dependencyJarGenericObjectGetterBuilds` |
| Nullable record string accessor | `CliDependencyProjectIntegrationTest.dependencyJarNullableRecordStringAccessorBuilds` |
| Static long-to-text formatter | `CliDependencyProjectIntegrationTest.dependencyJarStaticLongFormatterBuilds` |
| Scheduled executor one-shot task | `CliDependencyProjectIntegrationTest.dependencyJarScheduledExecutorOneShotBuilds` |
| Scheduled executor fixed-rate schedule plus shutdown/awaitTermination before first fire | `CliDependencyProjectIntegrationTest.dependencyJarScheduledExecutorFixedRatePreShutdownBuilds` |

That mapping is the rule: when an external probe breaks, the permanent fix belongs in one of these
generic compiler-owned dependency tests or a new generic equivalent, not in an upstream-project-
specific support row.

`.github/scripts/acceptance.sh` now auto-discovers probe directories and CI installs the bundled
artifact jars declared by probe metadata via `.github/scripts/install-external-probe-artifacts.sh`
before `mvn verify`, so the current discovered probe set is required in the external-probe
acceptance gate. Local runs still support explicit dependency overrides when investigation needs a
different jar or classes directory.

These probes prove only that the backend can consume the currently pinned external dependency
bytecode for simple object
constructors, object fields, object returns, object arrays, records, scalar
long/float/double operations, primitive arrays, basic enum names, closed-world
virtual/interface dispatch, static fields, reachable class initializers, javac string
concatenation, basic string intrinsics, exact `LambdaMetafactory` `Function`/`Predicate`
bridges into `Optional.filter`, `Optional.map`, and `Map.computeIfAbsent`, selected
external dependency helper code, direct same-method exception catches, uncaught panic-style
exceptions, and concrete instance calls.

Known blockers before broader real-project coverage:

- broader dependency-jar surface beyond the current smoke slices, especially deeper class-initialization graphs, richer collection/map helpers, additional atomics, more functional-interface shapes, and wider string/temporal helper families
- broader service runtime coverage, especially HTTP server APIs above the current raw loopback responder slices, HTTPS/TLS, certificates, long-lived service thread ownership, and resource-heavy app packaging
- richer Java semantic coverage, especially general try/catch/finally lowering, broader dynamic-call sites, wider enum/class-introspection edges, and full UTF-16 string semantics
- more dependency-version variance proof, because reproducible bundled smoke is not the same as broad upstream-version compatibility

This document stays at the compatibility-smoke level. Detailed compiler/runtime support claims
belong in `doc/status/support-matrix.md`, `doc/status/jdk-compatibility.md`, and the
compiler-owned tests under `src/test/java/javan/*`.

Fresh external-service packaging may still fail when a broader external graph pulls in
transitive API variants that the current dependency/runtime surface does not yet cover. The
real probes accept explicit dependency overrides for local investigation, but the release gate
uses the pinned bundled artifacts declared in probe metadata rather than sibling checkouts.

Next gates before claiming broader external-service compatibility:

1. done: make the current real-probe set reproducible and required in at least one CI row
2. done: add negative diagnostics for `Socket`, `ServerSocket`, `HttpClient`, and external `HttpServer`-shaped services
3. done: report reachable `network`, `socket`, and `http` usage even while unsupported
4. implement TCP loopback support with close/ownership and sanitizer proof
5. done partially: implement plain HTTP client loopback support for GET/string, POST+headers/byte[], and PUT byte[], plus raw loopback responder slices over `ServerSocket`/`Socket` for `GET /hello -> 200 pong`, unmatched-route `404`, POST body handling via `Content-Length`, sequential two-connection lifetime, method-plus-path dispatch, multi-class route-handler dispatch, request-header matching, response-header emission, and a request/response object model over router and service classes
6. run a broader external service example without dev console or reflection-heavy paths as a native service
7. add HTTPS/TLS/certificates after plain HTTP is stable
