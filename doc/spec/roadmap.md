# Javan Roadmap

This page contains future work only. Current behavior belongs in executable tests and the
focused contracts linked below; milestone history belongs in Git and pull requests.

## Sources Of Truth

- [support matrix](../status/support-matrix.md): generated compiler-owned scenarios
- [JDK compatibility](../status/jdk-compatibility.md): generated active-JDK accounting
- [release](release.md): packaging and publication gates
- [testing](testing.md): local and CI verification
- [toolchains](toolchains.md): JDK discovery, installation, and facade behavior
- [native ABI](native-abi.md): library ownership and error contracts
- [memory correctness](memory-runtime-correctness.md): GC and sanitizer guarantees

## Priorities

1. Keep every supported program deterministic and every unsupported reachable shape a
   compiler error, never a linker surprise.
2. Make self-host and package verification fast enough for ordinary development.
3. Close the first release on Linux x64, Linux ARM64, and macOS ARM64.
4. Expand Java/JDK compatibility through complete vertical slices, not callable-count vanity.
5. Add deeper analysis only when it removes a measured blocker or runtime cost.

Every milestone needs a public CLI/package proof, native/JVM parity where applicable, and
an explicit unsupported boundary. New user flags are a last resort; the compiler should
normally choose the safe path itself.

## Compiler Analysis

Javan currently has entrypoint-rooted reachability, instantiated-type-bounded class-hierarchy
dispatch, bounded callback receiver provenance, a canonical bytecode CFG, CFG-aware GC-root
liveness, per-block local value facts with proof-backed release rewrites, and transitive method
effects for pure, throwing, allocating, reading, writing, and unknown behavior. Escape
classification is **Done**: managed allocation sites are reported as `NoEscape`, `ArgumentEscape`,
or `GlobalEscape`, including transitive capture through exact application calls. Stack allocation is
**Partial**: release builds use at most 4 KiB of function-stack storage for constant primitive arrays
and application objects proven `NoEscape` outside control-flow cycles. Managed-reference fields and
runtime state remain explicit GC roots. Dynamic or large arrays, repeated loop sites, debug builds,
and arenas remain managed.

| Priority | Analysis | Smallest useful scope | Acceptance gate |
| --- | --- | --- | --- |
| P2 | Stack/arena allocation | Prove repeated-site lifetimes; add arenas only with scoped object-graph proof. | Identity, exception, GC-root, sanitizer, and allocation-counter gates pass. |

Analysis rules:

- unknown facts always fall back conservatively
- emit evidence before an optimization consumes a fact
- never retain mutable-field facts across unknown calls
- measure compile time and peak memory on showcase and self-host gates
- prefer bounded closed-world analysis over a general points-to engine

Out of scope until a public workload proves otherwise: global alias analysis, symbolic
execution, memory SSA, speculative/JIT tiers, profile-guided specialization, arbitrary
runtime class loading, and build-time application heaps. Optimizer-specific work remains in
[optimizer-roadmap.md](optimizer-roadmap.md).

## Runtime And Memory

Near-term work:

- finish concurrent mutation/return ownership beyond the current proven root handoffs
- keep adaptive GC and heap accounting bounded under self-host compiler workloads
- close remaining exception-handler and finally semantics without hidden panic fallbacks
- reduce generated runtime size through measured module selection, not manual feature flags
- preserve zero final live heap/root residue in app, self-host, and native-library sanitizer gates

Concurrency support expands only with hostile lifecycle tests: start/join, interruption,
thread-local cleanup, failure transport, concurrent GC, and repeated reuse. See
[concurrency-runtime.md](concurrency-runtime.md) and
[memory-runtime-correctness.md](memory-runtime-correctness.md).

## Platforms And Release

Required native package targets:

| Target | Direction |
| --- | --- |
| Linux x64 | Release gate and canonical compatibility owner. |
| Linux ARM64 | Native runner release gate; no QEMU self-host compilation. |
| macOS ARM64 | Host-native release gate. |
| macOS x64 | Retained but disabled while disproportionately slow. |
| Windows x64/ARM64 | Enable proven compiler/runtime pieces; package support remains incomplete. |

Remaining platform work:

- finish Windows process, linker, filesystem, and package behavior
- prove fixed-point/self-host output and sanitizer provenance on every release target
- keep snapshot publication on every `main` merge and final releases manual
- keep Maven Central and Homebrew publication hard-disabled until explicitly implemented
- retain one timing comparison for bootstrap generations and compiler hot paths

The live workflow matrix is authoritative; prose must not copy transient runner status.
See [cross-platform-verification.md](cross-platform-verification.md).

## Java And JDK Compatibility

Compatibility grows as complete behavior families. One-argument `Class.forName` is implemented
for classes and arrays present in the compiled closed world; it initializes classes once and
transports `ClassNotFoundException` and `NullPointerException` through normal Java catches.
Closed-world `getDeclaredMethod` and `getMethod` lookup support exact parameter shapes,
declared private metadata, inherited public class and interface methods, `Method.getName`,
`getDeclaringClass`, `getParameterCount`, `getParameterTypes`, `getReturnType`, and `getModifiers`,
plus stateful `canAccess`, `setAccessible`, `trySetAccessible`, and `isAccessible` behavior.
Access checks use the caller, receiver, exact nest metadata, and a bounded platform-access
policy, with catchable lookup, receiver, access, and null failures. Selecting a runtime class
loader remains outside the static native model.

- continue finite member flows with invocation
- service loading from standard descriptors and module declarations
- broader exception semantics and platform throwable transport
- collections, streams, time, networking, files, and concurrency only with native/JVM parity
- deterministic rejection for dynamic class definition, arbitrary loaders, proxies,
  instrumentation, unrestricted accessibility, JNI, and unsupported native services

Users must not maintain reflection or resource registration files. Constant behavior should
be resolved automatically; genuinely dynamic shapes should fail early with a useful reason
and fix. Inventory is not support: the release gate requires every claimed callable family
to be supported or explicitly rejected for a documented reason.

## Dependencies And Build Tools

Javan remains binary-first. Maven, Gradle, IDEs, and installers are thin consumers of the
same executable and reports.

Planned vertical slices:

- locked direct dependencies with coordinates, checksums, repository origin, and licenses
- separate production/test graphs used by `javan build` and `javan test`
- offline replay from the Javan cache
- Maven and Gradle convenience integrations with no second compiler path
- IDE diagnostics through stable report links and the installed JDK facade
- additional managed JDK providers only behind verified catalog/checksum handling

No build may download undeclared tools or dependencies. See
[dependency-and-license-reports.md](dependency-and-license-reports.md) and
[binary-first-distribution.md](binary-first-distribution.md).

## Backends And Products

C remains the production backend while it is deterministic, portable, self-hosting, and
measurably adequate. LLVM, Cranelift, Go, or Rust experiments need a public workload and a
backend boundary; they must not leak language-specific rules into bytecode analysis.

Javan Studio, UI, and other products stay outside the compiler repository and dependency
graph. They integrate through normal Java APIs, generated artifacts, and stable reports.

## Non-Goals

- reimplementing or disguising a JDK
- silently changing user projects, shell profiles, `PATH`, or `JAVA_HOME`
- accepting unsupported bytecode and hoping generated C works
- configuration files that replace compiler analysis
- parallel compiler modes or compatibility layers without a current boundary
- abstractions, modules, plugins, or reports justified only by possible future use
