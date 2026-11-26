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
Native execution need not reproduce a JVM's internal representation, ABI or unspecified
provider and platform choices. Supported Java contracts still apply to observable results,
failures and ordering. Where an API permits operating-system-dependent results, tests verify
its contract instead of requiring identical values from separate executions.

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

- `Class.forName` with one argument or the system/bootstrap loader, compiled class/array
  lookup, optional initialization and once-only initialization failures. Custom loaders remain
  outside the supported closed world.
- Declared/public method lookup, supported metadata and access checks, and generated
  application `Method.invoke` with dispatch, supported boxing/widening and wrapped causes.
  Metadata-only platform methods are not automatically invocable.
- Application/dependency resource streams, application-first lookup, hashes, runtime resource
  names, null-name failures and embedded app/library resources. URL-returning lookup remains
  outside the slice.
- Services from `META-INF/services` and module declarations, lazy cached provider construction,
  supported provider factories, iteration, `findFirst` and `reload`. `loadInstalled` uses the
  platform view. Dynamic loader discovery and provider streams are not implied.

Direct `new Object()` creates a distinct managed instance with `java.lang.Object` class metadata.
`Object.equals` dispatches statically reachable application overrides and supports default identity,
strings, boxed primitives and application enum constants. Some JDK value comparisons, including
separate lists, maps, optionals and inherited platform equality, fail explicitly until their equality
is implemented. The supported `TimeUnit`, `StandardCopyOption` and `LinkOption` constants have
stable enum identity distinct from equal-looking strings. Supported collection searches, additions,
clearing and size queries transport a null receiver as `NullPointerException`. Supported map clearing
also transports a null receiver. Single-element additions and clearing transport
`UnsupportedOperationException` for supported immutable collections and maps. Application-defined
`equals` remains incomplete for searches outside the supported list operations. Search, addition and
clear callables remain unknown in the support ledger while custom collection overrides and set equality
remain incomplete.
Other methods remain subject to their supported callable entries.

Native map primitives serialize access to shared map storage, including access through `Map`
references. This protects those primitives and record map traversal during concurrent mutation.
`ConcurrentHashMap` rejects null keys for `get`, `getOrDefault`, `containsKey`, `put`, `putIfAbsent`,
`remove` and `replace`, and null values for `containsValue`, `put`, `putIfAbsent` and `replace`,
including through `Map` references. Bulk `putAll` rejects null entries when targeting a concurrent
map. The map-copy constructor rejects null sources and null entries, and can transport the error to
its caller. Some direct handlers around construction are not yet supported. A null default value is
allowed, and two-argument `remove` with a non-null key and null expected value returns false. Other
null-sensitive methods and constructors, application-defined key equality, compound callback
operations and concurrent views still need their complete API semantics, so `ConcurrentHashMap` is
not yet accounted as fully supported. Copy constructors of `HashMap` and `LinkedHashMap` likewise
reject null sources, while accepting null keys and values in the source. `Map.copyOf` rejects null
sources and null entries and creates an immutable snapshot. `Map.entry` and all fixed-arity `Map.of`
overloads reject null arguments. The multi-entry overloads and `Map.ofEntries` reject duplicate keys
within the supported key semantics. `Map.ofEntries` also rejects null arrays and entries. It consumes
native map entries and does not yet support application-defined `Map.Entry` implementations.
`containsKey`, `containsValue`, `get` and `getOrDefault` reject null queries on maps created by
`Map.of`, `Map.ofEntries` and `Map.copyOf`, including unmodifiable views.

Zero-argument `ArrayList` allocation, single-element append and clear coordinate their native
storage updates with garbage collection. Other list mutation paths still require concurrency
verification.

Fixed-arity and array-based `Set.of` reject null elements and duplicate elements within the
supported equality semantics. The immutable result retains its elements independently of an input
array. `Set.copyOf` rejects null sources and elements, collapses duplicates and creates an immutable
snapshot. `contains` rejects null queries on these sets, including unmodifiable views.
Application-defined equality remains incomplete.

Fixed-arity and array-based `List.of` reject null elements, and the array overload rejects a null
array. They create immutable ordered snapshots and retain duplicate elements. Other list operations
and application-defined element equality remain incomplete. `List.copyOf` rejects null sources and
elements and creates an immutable ordered snapshot.
`contains`, `indexOf` and `lastIndexOf` on supported list representations call an application-defined
query object's `equals`, including inherited implementations. Search values of unknown type
conservatively retain possible implementations. `List.of` and `List.copyOf` searches reject null
queries, including through unmodifiable views. Set membership still uses the existing incomplete
equality semantics.
Reads using `List.get`, `getFirst` and `getLast` publish returned references safely during concurrent
garbage collection. Supported `getFirst` and `getLast` calls transport null-receiver and empty-list
failures within supported handler shapes.
Supported indexed `get` calls transport null-receiver and invalid-index failures within supported
handler shapes.
Supported indexed `set` calls also transport immutable-list failures and preserve the replaced value
while publishing it to the collector.
Indexed `remove` transports the same failures and publishes the removed value safely.
Indexed insertion transports null-receiver, invalid-index and immutable-list failures, and retains
the inserted value through concurrent collection.
Indexed bulk insertion reads supported collection views, handles self-insertion and transports
null, invalid-index, immutable-list and representational-size failures within supported handlers.
Bulk append through `List` or `ArrayList` supports the same collection views and failure transport.
Bulk insertion through `Collection`, `Set`, `HashSet` and `LinkedHashSet` handles supported
views and transports null, immutable-collection and representational-size failures within
supported handlers.
`containsAll` on supported collection and set owners handles views and transports null receiver
and null source failures within supported handlers. Immutable `List.of`, `List.copyOf`, `Set.of`
and `Set.copyOf` receivers reject a null probe when iteration reaches it.
`removeAll` and `retainAll` on supported collection and set owners handle supported views and
transport null and immutable-collection failures within supported handlers.
Object removal through supported list, set and `Collection` owners transports null-receiver and
immutable-collection failures for the currently supported collection representations.
Supported endpoint removals transport null, empty and immutable-list failures and publish removed
values safely.
Supported endpoint insertions transport null and immutable-list failures and retain inserted values
through concurrent collection.
Application-defined collection implementations are not yet supported.

The default `SecureRandom` constructor supports calls resolved as `SecureRandom.nextBytes` and
`generateSeed`. The static `getSeed` entry is also supported. The supported random value methods
are `nextBoolean` and the zero-argument, single-bound and origin-bound `nextInt`, `nextLong`,
`nextFloat` and `nextDouble` overloads. These methods use operating-system entropy during execution.
The supported `Random` and `RandomGenerator` `nextBytes`, `nextBoolean`, `nextInt`, `nextLong`,
`nextFloat` and `nextDouble` overloads also work when the runtime receiver is a `SecureRandom`,
including values passed through fields or method arguments. `RandomGenerator.nextLong()`,
`nextBytes(byte[])`, `nextInt()` and `nextInt(int)` also dispatch statically reachable application overrides,
including inherited ones. Other receivers for the remaining overloads fail explicitly and are not
treated as secure generators. Entropy-source failures retain the native runtime panic boundary.
Provider selection, explicit seeding, other calls resolved through random supertypes and other
random APIs remain subject to their own supported callable entries. Reachable constructors of
application-defined `SecureRandom` subclasses are rejected until subclass state and overridden
methods can be represented.

This summary does not replace exact callable/shape entries in the generated ledger.
A typed handler can include a direct `Class.forName(...).getName()` sequence when control flow
cannot bypass the lookup. An isolated protected range can start with a same-class static field
and a direct supported JDK instance call using primitive arguments. Numeric literals, local loads
and same-class no-argument `int` calls with proven nonthrowing bodies or supported exception
transport are supported as arguments. Other calls and unproven control flow remain subject to
their own supported shapes.
Nested conditional values and nested switch results support ordinary values and previously
initialized primitive or ordinary object locals. A single new local in a nested conditional is
supported when each value-producing branch assigns it at its exit. Partially initialized shapes
are rejected during lowering. Some nested expressions that combine an earlier operand with a
conditional also remain unsupported and fail during lowering. Within supported control-flow
shapes, an earlier static-field read is preserved across later calls, writes and class initialization.
Nested conditionals inside a local exception handler remain limited to the verifier's supported
handler shapes and otherwise fail with `JAVAN014`.
Within supported handler shapes, `String.charAt`, `codePointAt`, `codePointBefore`,
`codePointCount`, and `offsetByCodePoints` transport null-receiver and invalid-index exceptions.
`String.toCharArray` returns an independent array of UTF-16 code units and transports a null
receiver exception within supported handler shapes.
`String.getChars` copies UTF-16 code units into the requested destination range and transports
null and range errors within supported handler shapes.
`Charset.defaultCharset()` returns Javan's UTF-8 default, and `Charset.name`, `displayName` and
`toString` expose its canonical name. Parameterless `String.getBytes()` encodes with that same
charset. Other configurable default encodings are not supported.
`String` byte-array constructors using the default or explicit UTF-8 charset decode UTF-8,
replace malformed input and transport null and invalid-range exceptions within supported handler
shapes. Other explicit charsets remain unsupported.
`String.repeat` preserves UTF-16 code units and transports null-receiver, negative-count,
and representational-overflow errors within supported handler shapes.
`String.length` counts UTF-16 code units and transports a null-receiver exception within
supported handler shapes.
`String.hashCode` hashes UTF-16 code units and transports a null-receiver exception within
supported handler shapes.
`String.isEmpty` tests for zero UTF-16 code units and transports a null-receiver exception within
supported handler shapes.
`String.isBlank` applies Java whitespace rules and transports a null-receiver exception within
supported handler shapes.
`String.charAt` supports Unicode literals. Provably invalid ASCII literal indices remain subject
to the diagnostics policy.
`String.codePointAt`, `codePointBefore`, `codePointCount`, and `offsetByCodePoints` also accept
Unicode literals.
The supported integer and String overloads of `String.indexOf` and `lastIndexOf` accept Unicode
literals and transport null-receiver and null-argument exceptions within supported handler shapes.
The bounded `String.indexOf` overloads also transport invalid-range exceptions.
`String.substring` and `String.subSequence` preserve UTF-16 code units and transport null-receiver
and invalid-range exceptions within supported handler shapes.
`String.replace(char, char)` preserves UTF-16 code units, returns its receiver if the old character
is absent and transports null-receiver failures within supported handler shapes.
`String.replace(CharSequence, CharSequence)` performs literal, non-overlapping replacements over
UTF-16 code units. Its arguments support strings, string builders and application implementations
with a statically reachable supported `toString` override. Null and supported conversion failures
are transported within supported handler shapes. Other implementations are rejected at runtime.
`String.concat` preserves UTF-16 code units, returns its receiver for an empty argument and
transports null receiver and argument failures within supported handler shapes.
`String.startsWith` and `String.endsWith` preserve UTF-16 comparisons and transport null failures
within supported handler shapes; a negative start offset returns false for a non-null receiver.
`String.equals` compares only strings and transports null-receiver failures within supported
handler shapes.
`String.contains` supports String, StringBuilder and closed-world application CharSequence
implementations with a supported `toString` override. Null failures and exceptions from the
application override are transported within supported handler shapes. Unsupported runtime
CharSequence shapes fail explicitly.
Both `Character.toChars` overloads produce UTF-16 code units and transport invalid-code-point
and destination errors within supported handler shapes. A direct `Character.toChars(...).length`
can be used in a typed handler when control flow cannot bypass the array creation.
`Character.toString(int)` preserves UTF-16 code units and transports invalid-code-point errors
within supported handler shapes.
The `char[]` overloads of `Character.codePointAt` and `Character.codePointBefore` read surrogate
pairs within their optional bounds and transport null-array and invalid-index errors within
supported handler shapes.
`Character.codePointCount(char[], int, int)` counts within the requested array slice and
transports null-array and invalid-range errors within supported handler shapes.
`Character.offsetByCodePoints(char[], int, int, int, int)` moves within its array slice and
transports null-array and invalid-range errors within supported handler shapes.
`Path.of`, `Paths.get`, and `Path.resolve` report supported null inputs and embedded
U+0000 through typed exceptions. Some inline varargs inside `catch` ranges still fail
verification and can be moved outside the protected range.

Broader exceptions/finally, UTF-16 operations, collections, networking and threading require
their own public-boundary proofs of supported Java contracts. Unrestricted runtime class
definition, arbitrary loaders, proxies, instrumentation, JNI and unrestricted accessibility
are outside the current contract.
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
| COMP-001/002 | Given a supported source, class directory or JAR, when built and run, then the native result satisfies the supported Java contract. | [CLI behavior](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java), [dependency projects](../../src/test/java/javan/CliDependencyProjectIntegrationTest.java), [acceptance harness](../../.github/scripts/acceptance.sh). |
| COMP-003/007 | Given reachable unsupported bytecode or a proven hazard, when checked, then diagnostics explain failure before native generation; unreachable findings remain visible without inventing a reachable edge. | [Safety diagnostics](../../src/test/java/javan/CliSafetyDiagnosticsIntegrationTest.java), [control-flow CLI](../../src/test/java/javan/ControlFlowCliTest.java), [compatibility CLI](../../src/test/java/javan/CliCompatIntegrationTest.java). |
| COMP-004 | Given direct, inherited or unknown receivers, when analyzed and built, then valid dispatch survives and emitted facts remain conservative. | [Core behavior](../../src/test/java/javan/CoreBehaviorTest.java), [runtime translation](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java). |
| COMP-005 | Given ordered, re-entered or failing initializers, when the artifact starts or an export invokes them, then Java ordering and subsequent failure state hold. | [Runtime translation](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java), [package/native-library tests](../../src/test/java/javan/CliPackagingIntegrationTest.java). |
| COMP-006 | Given compiled classes/resources/providers, when used through native Java APIs, then lookup, absence, ownership and failure satisfy the supported Java API contract. | [JDK semantics](../../src/test/java/javan/CliJdkSemanticsIntegrationTest.java), [resources](../../src/test/java/javan/CliResourceRuntimeIntegrationTest.java), [services](../../src/test/java/javan/CliServiceLoaderIntegrationTest.java). |
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
