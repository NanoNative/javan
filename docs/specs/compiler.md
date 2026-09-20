# Compiler And Java Compatibility

## Human Review

JavaN turns ordinary compiled Java into a host-native executable or library. Users keep
their source and build tools; `javan check` explains whether the reachable program fits
the supported native subset before `javan build` generates and links C.

This specification consolidates the existing compiler contract from the earlier roadmap
and distribution document. It introduces no new Java feature or backend decision.
[ADR 0003](../adr/0003-static-native-compilation.md) records the architectural rationale.

Owns: input, reachability, initialization, compatibility and diagnostic boundaries.
Does not own: [ABI ownership](native-abi.md), [heap lifetimes](memory-runtime-correctness.md),
[optimization rules](analysis-and-optimization.md), [diagnostic rendering](diagnostics.md),
or [publication](release.md).

The current implementation is a supported subset, not a complete JVM. The
[generated support ledger](../status/support-matrix.md) names its exact scenarios.
Finding a class or method in the JDK inventory does not establish native support.

## Requirements

| ID | Observable contract |
| --- | --- |
| COMP-001 | JavaN MUST accept its documented class-directory, JAR and source-project inputs through the same compiler path; source compilation uses a real JDK. |
| COMP-002 | A supported program MUST preserve its observable Java behavior through the native artifact, including initialization order and supported failure handling. |
| COMP-003 | Unsupported reachable behavior MUST produce a deterministic diagnostic with its location, reason and actionable boundary; native linking must not be the first unsupported-feature detector. |
| COMP-004 | Closed-world reachability MUST retain every constructible receiver and explicit entrypoint; unknown receiver facts MUST remain conservative. |
| COMP-005 | Application initialization MUST remain lazy runtime work. Compiling an application MUST NOT execute its initializers or capture runtime entropy, clocks or environment as application state. |
| COMP-006 | Supported reflection, resource and service discovery MUST resolve from the compiled world automatically, without user-maintained registration lists. Behavior outside that world MUST be reported explicitly. |
| COMP-007 | Reports MUST distinguish proven reachable edges, findings and unsupported scope. Unknown behavior MUST NOT be presented as a proven edge or supported callable. |
| DIST-001 | The distributed executable MUST consume normal Java outputs through the same CLI and reports as the development build. |

## Current Boundary

The public journey is `check -> build -> run`; `report` reads the same generated evidence.
The pipeline uses classfile ingestion, canonical control flow, reachability, verification,
lowered IR and C generation. Legacy `jsr`, `jsr_w` and `ret` subroutines are normalized at
ingress. The [analysis contract](analysis-and-optimization.md) owns the facts and transformations.

Class initialization uses one dependency graph for active uses (`new`, static field access,
static calls, main and native exports), superclass ordering and applicable default interfaces.
Same-thread re-entry observes default state; another thread waits. Failed initialization stays
erroneous, preserves the original cause and fails later uses without rerunning initialization.
Native allocation denial still has an explicit panic boundary; it is not general Java
`OutOfMemoryError` recovery. See [memory correctness](memory-runtime-correctness.md).

Closed-world support includes:

- One-argument `Class.forName`, compiled class/array lookup and once-only initialization.
- Declared/public method lookup, supported metadata and access checks, and generated
  application `Method.invoke` with dispatch, supported boxing/widening and wrapped causes.
  Metadata-only platform methods are not automatically invocable.
- Application/dependency resource streams, application-first lookup, hashes, missing-resource
  behavior and embedded app/library resources. URL-returning lookup remains outside the slice.
- Services from `META-INF/services` and module declarations, lazy cached provider construction,
  supported provider factories, iteration, `findFirst` and `reload`. `loadInstalled` uses the
  platform view. Dynamic loader discovery and provider streams are not implied.

This summary does not replace exact callable/shape entries in the generated ledger.
Broader exceptions/finally, UTF-16 operations, collections, networking and threading require
their own native/JVM behavior proofs. Unrestricted runtime class definition, arbitrary loaders,
proxies, instrumentation, JNI and unrestricted accessibility are outside the current contract.
Scoped accessibility already implemented for known methods is not a blanket rejection.

## Build Inputs And Outputs

JavaN works beside the user's JDK, Maven, Gradle and IDE. The
[README](../../README.md#commands-and-outputs) documents current CLI paths;
[toolchains](toolchains.md) owns JDK selection and the optional facade. Reports use Markdown
for people, JSON for integrations, and compiler-style diagnostics for tools that consume
`javac` output. Integrations consume that evidence rather than infer native support themselves.

### Proposed Extensions

The earlier distribution proposal included automatic extra outputs and multi-module aggregation.
These are not the current output contract. Before implementation, agree layout, cost and failure
behavior; no change to existing paths or defaults is authorized by this consolidation.

| Input | Proposed discovery | Proposed output |
| --- | --- | --- |
| Maven | `target/classes`; reachable modules from an aggregator | Each module's `target/javan`, plus a root summary for multiple modules |
| Gradle | `build/classes/java/main` and Kotlin output when present; reachable subprojects | Each project's `build/javan`, plus a root summary for multiple projects |
| Plain Java | Explicit `--classes`, existing `classes`, or real `javac` | `target/javan`, with compiled classes below it |
| JAR | JAR as classpath/root input | Sibling `target/javan` or explicit output |

The proposal allows missing classes to trigger Maven's compile phase or Gradle's classes task
(wrapper first, installed command second), or plain `javac`. Classfile versions determine input
compatibility; users should not need a separate Java-version flag. Any required managed JDK
installation remains explicit, checksummed and cached under `~/.javan` per [toolchains](toolchains.md).

Analysis and reports should accompany builds. The proposed output selection builds an app for
one supported main, a native library for configured exports, and a JAR when requested by config
or integration. Automatic additional outputs still need the decision above. Prefer configuration
for disabling outputs and sparse CLI overrides for automation.

## Acceptance

These are existing checks to run for a change, not a statement that they ran in this
documentation update. [Testing](testing.md#local-verification) owns the commands.

| Requirements | Given / when / then at the public boundary | Evidence / remaining gap |
| --- | --- | --- |
| COMP-001/002 | Given a supported source, class directory or JAR, when built and run, then native output matches its JVM result. | [CLI behavior](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java), [dependency projects](../../src/test/java/javan/CliDependencyProjectIntegrationTest.java), [acceptance harness](../../.github/scripts/acceptance.sh). |
| COMP-003/007 | Given reachable unsupported bytecode or a proven hazard, when checked, then diagnostics explain failure before native generation; unreachable findings remain visible without inventing a reachable edge. | [Safety diagnostics](../../src/test/java/javan/CliSafetyDiagnosticsIntegrationTest.java), [control-flow CLI](../../src/test/java/javan/ControlFlowCliTest.java), [compatibility CLI](../../src/test/java/javan/CliCompatIntegrationTest.java). |
| COMP-004 | Given direct, inherited or unknown receivers, when analyzed and built, then valid dispatch survives and emitted facts remain conservative. | [Core behavior](../../src/test/java/javan/CoreBehaviorTest.java), [runtime translation](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java). |
| COMP-005 | Given ordered, re-entered or failing initializers, when the artifact starts or an export invokes them, then Java ordering and subsequent failure state hold. | [Runtime translation](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java), [package/native-library tests](../../src/test/java/javan/CliPackagingIntegrationTest.java). |
| COMP-006 | Given compiled classes/resources/providers, when used through native Java APIs, then lookup, absence, ownership and failure match the supported JVM slice. | [JDK semantics](../../src/test/java/javan/CliJdkSemanticsIntegrationTest.java), [resources](../../src/test/java/javan/CliResourceRuntimeIntegrationTest.java), [services](../../src/test/java/javan/CliServiceLoaderIntegrationTest.java). |
| DIST-001 | Given ordinary Java build output, when consumed through an extracted JavaN package, then the same CLI behavior and reports remain available. | [Package proof](../../.github/scripts/verify-package.sh), [dependency projects](../../src/test/java/javan/CliDependencyProjectIntegrationTest.java). |

## Open Decisions

No material compiler decision blocks the documentation migration or existing release rehearsal.
Before expanding a capability, name the failing public program and resolve only its dependent choices:

| Before implementation | Required decision and consequence |
| --- | --- |
| Full UTF-16 | String representation, indexing, borrowed values and ABI conversion must agree; changing the representation affects memory and native consumers. |
| Broader exceptions/reflection | Define supported handlers, cleanup, access and target discovery; do not infer complete JVM semantics from a working method lookup. |
| General concurrent mutation | Agree mutator/collector publication and lifecycle in the memory/concurrency specs before enabling more concurrent execution. |
| TLS services | Define certificate/trust-store sourcing, verification and failure behavior before selecting a library or claiming HTTPS. |
| Another backend | Demonstrate a workload the C backend cannot adequately serve and agree the compatibility/proof boundary before implementation. |
