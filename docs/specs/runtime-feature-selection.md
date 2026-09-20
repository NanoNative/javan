# Runtime Feature Selection

## Human Review

Owns runtime-module selection, disabled-feature enforcement and footprint reporting.
Parsed settings do not imply an implemented backend choice. Exact implemented behavior
is listed below; [release](release.md) owns supported package targets.

Before size/speed/static-linkage modes become behavior, agree their measurable artifact
and dependency contract. No new runtime mode is selected by this document migration.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| RT-001 | A reachable disabled runtime module MUST fail before code generation; an unused disabled module MUST remain nonblocking. | [Runtime-feature CLI](../../src/test/java/javan/CliRuntimeFeatureIntegrationTest.java). |
| RT-002 | Footprint reports MUST distinguish requested settings, actual linkage and unknown measurements. | [Runtime reporting CLI](../../src/test/java/javan/CliRuntimeReportingIntegrationTest.java); parsed-only options remain explicitly partial. |
| RT-003 | Host-target assertions MUST reject mismatches until a cross-linking contract is implemented. | [Toolchains](toolchains.md#boundaries); future package targets require [release evidence](release.md). |
| DIST-003 | A requested alternative runtime footprint MUST either meet its linkage contract or reject the request explicitly. | Runtime reporting tests above; libc-free and full self-contained linkage remain future decisions. |

Goal: let users reduce binary size, deployment weight, and diagnostic overhead without
making builds mysterious.

Current implemented slice:

- native builds write `.javan/reports/runtime-footprint.json`
- native builds write `.javan/reports/runtime-footprint.md`
- `javan.toml` disabled runtime modules are enforced during `check`, `build`, and `run`
- disabled reachable runtime modules fail before native codegen
- disabled unused runtime modules are reported as unused/omitted
- native checks write `.javan/reports/runtime-features.json`
- native checks write `.javan/reports/runtime-features.md`
- normal `check`, `build`, and `compat` commands refresh `.javan/reports/report.md`
  and `.javan/reports/report.json`
- unified reports summarize the runtime-footprint family
- unified reports summarize the runtime-features family
- `javan report` remains the explicit report reader/refresh command
- `--target` is a host-target assertion and fails before native codegen on mismatch
- host-native package requirements belong to [release scope](release.md#first-native-release-scope);
  workflow platform smoke and disabled rows do not imply package support

## Principles

- Default builds auto-select runtime modules from reachability.
- User-disabled features are hard contracts.
- If reachable code needs a disabled feature, the build fails before native codegen.
- Every linked, skipped, disabled, or rejected runtime feature is reported.
- Prefer one config block and a few build profiles over many CLI flags.

## Configuration Shape

```toml
[build]
profile = "core"

[build.runtime]
containment = "system"
optimize = "size"
debug = false
profiling = false
disabled = ["thread-profiling", "reflection-metadata"]
```

The shorter `[runtime]` table is accepted for the same keys. `disabled` is enforced now.
`containment`, `optimize` and `debug` are parsed and reported, but do not establish the
alternative linkage, size/speed or native-debug behavior proposed below. Existing release
optimizations are owned by [analysis and optimization](analysis-and-optimization.md).

Requested `profiling` has a narrower implemented host-thread-backed slice, including
`runtime-profiling.*` reports and collected counters through native execution. The
[runtime reporting CLI tests](../../src/test/java/javan/CliRuntimeReportingIntegrationTest.java)
prove that slice, not the complete scheduler/profiling target in [concurrency](concurrency-runtime.md).

CLI flags may override config for automation, but they should stay sparse. The normal
path remains `javan build`.

## Proposed Runtime Choices

These are intended tradeoffs, not measured size/speed guarantees or implemented mode selectors.

| Choice | Smaller | Faster | Good for | Cost |
| --- | --- | --- | --- | --- |
| `containment = "system"` | yes | neutral | Docker/base images | Requires compatible system libraries. |
| `containment = "self-contained"` | no | neutral | Downloadable apps | Larger; static linking is platform-dependent. |
| `optimize = "size"` | yes | maybe no | CLI tools, desktop apps | Fewer duplicated fast helpers and less metadata. |
| `optimize = "speed"` | no | yes | Services, hot loops | Larger binary from helpers/specialization. |
| `debug = false` | yes | neutral | Release builds | Less native/source mapping detail. |
| `profiling = false` | yes | neutral | Most release builds | No live profiling hooks. |
| Disable runtime module | yes | maybe | Known-small apps | Build fails if reachable code needs it. |

### Linux libc-free Footprint — Planned

An optional Linux-only footprint could call kernel syscalls directly for constrained programs.
It is not the default runtime or a selected implementation; macOS and Windows retain platform
APIs. Start only with tiny modules whose syscalls are stable and testable. DNS, certificates,
HTTPS, locale/timezone and the full thread runtime remain outside the initial slice unless
explicitly implemented and stress-tested.

Before claiming this mode:

- runtime and container reports identify the syscall/libc posture and no libc dependency;
- unsupported modules fail before code generation when the mode is requested;
- supported modules pass sanitizer/leak and native showcase smoke;
- normal system-linked builds remain the default.

The mode's user-facing selection and linkage contract need agreement before implementation.

## Proposed Feature Families

Initial feature families should be coarse and understandable:

| Family | Examples | Default |
| --- | --- | --- |
| `core` | startup, args, primitive/object model | always on |
| `strings` | string literals, concat, string intrinsics | auto |
| `arrays` | primitive/object arrays, copy helpers, bounds checks | auto |
| `io` | files, stdout/stderr/stdin, resources | auto |
| `time` | `nanoTime`, `currentTimeMillis` | auto |
| `process` | process execution, env, properties subset | auto |
| `exceptions` | panic, readable exception mapping, catch support | auto |
| `threads` | platform/virtual thread runtime | auto when implemented |
| `profiling` | counters, thread profiling, allocation profiling | off in release |
| `debug-map` | optimized/generated-to-source mapping | on for debug, slim for release |
| `reflection-metadata` | limited closed-world metadata | off unless reachable/configured |

## Proposed Report Expansion

The current output files are listed above. The additional aggregate `runtime.*` report and
complete field set below remain proposed; requested settings must not be confused with actual linkage.

Proposed additional outputs are `.javan/reports/runtime.json` and `.md`, integrated with
the existing feature, footprint and unified reports rather than duplicating their models.

Required report fields:

- requested containment
- actual linkage
- included runtime modules
- disabled runtime modules
- rejected reachable disabled features
- omitted unused modules
- debug/profiling/sanitizer posture
- estimated feature byte cost when available
- host target, requested target, actual target
- OS/architecture coverage rows

## Acceptance

Current public checks:

- A system-linked build reports its artifact and host footprint.
- Disabled unused modules stay nonblocking; reachable disabled modules fail before code generation.
- Requested profiling distinguishes linked-but-not-run from collected counters.

Future mode-selection gates, to specify and prove before claiming those modes:

- Self-contained build either succeeds or fails with a platform-specific reason.
- `optimize = "size"` produces an artifact no larger than balanced for the same app.
- `optimize = "speed"` may grow binary size and reports why.
- Debug-off build omits debug-only maps where safe.
- Profiling-off build omits profiling hooks.
- Runtime report explains every included and omitted feature.
