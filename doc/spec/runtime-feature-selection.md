# Runtime Feature Selection

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
- unified `javan report` summarizes the runtime-footprint family
- unified `javan report` summarizes the runtime-features family
- `--target` is a host-target assertion and fails before native codegen on mismatch
- host-native CI is required for Linux/macOS x64/aarch64

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
`containment`, `optimize`, `debug`, and `profiling` are parsed and reported, but real
backend selection remains a follow-up gate.

CLI flags may override config for automation, but they should stay sparse. The normal
path remains `javan build`.

## Runtime Choices

| Choice | Smaller | Faster | Good for | Cost |
| --- | --- | --- | --- | --- |
| `containment = "system"` | yes | neutral | Docker/base images | Requires compatible system libraries. |
| `containment = "self-contained"` | no | neutral | Downloadable apps | Larger; static linking is platform-dependent. |
| `optimize = "size"` | yes | maybe no | CLI tools, desktop apps | Fewer duplicated fast helpers and less metadata. |
| `optimize = "speed"` | no | yes | Services, hot loops | Larger binary from helpers/specialization. |
| `debug = false` | yes | neutral | Release builds | Less native/source mapping detail. |
| `profiling = false` | yes | neutral | Most release builds | No live profiling hooks. |
| Disable runtime module | yes | maybe | Known-small apps | Build fails if reachable code needs it. |

## Feature Families

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

## Reports

Runtime selection must be visible in:

- `.javan/reports/runtime.json`
- `.javan/reports/runtime.md`
- `.javan/reports/runtime-footprint.json`
- `.javan/reports/runtime-footprint.md`
- `.javan/reports/runtime-features.json`
- `.javan/reports/runtime-features.md`
- unified `.javan/reports/report.json`
- unified `.javan/reports/report.md`

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

- System-linked build reports system libraries and passes on the host.
- Self-contained build either succeeds or fails with a platform-specific reason.
- `optimize = "size"` produces an artifact no larger than balanced for the same app.
- `optimize = "speed"` may grow binary size and reports why.
- Disabled unused feature is omitted and reported.
- Disabled reachable feature fails before native codegen.
- Debug-off build omits debug-only maps where safe.
- Profiling-off build omits profiling hooks.
- Runtime report explains every included and omitted feature.
