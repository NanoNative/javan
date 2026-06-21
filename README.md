# javan

Minimal native-first Java toolchain.

`javan` consumes compiled `.class` files, checks reachable code, lowers the supported
subset to C, and links host-native executables or native libraries without changing
normal Java source.

## Status

| Question | Current answer |
| --- | --- |
| Is it useful today? | Yes for small deterministic native apps and native libraries built from the supported subset. |
| Which JDK is actively gated? | JDK 25 locally. JDK 21-24 remain planned matrix targets. |
| What is solid now? | Native app output, JVM jar output, native library packaging, reports, and the current showcase path. |
| What is still incomplete? | Broad JDK coverage, full exception semantics, thread/runtime breadth, richer library ABI types, and remote release validation across every target row. |
| Can it rebuild itself? | Locally, yes. Remote package validation for all configured release targets is still open. |

## Quick Start

From this checkout:

```sh
mvn -q package
java -jar target/javan-*.jar --version
java -jar target/javan-*.jar build example --output native-showcase
example/.javan/bin/native-showcase
```

With an installed `javan` binary:

```sh
javan check .
javan build .
javan run . -- one two
javan build . --jar
javan build . --library --export com.acme.Math.add --bindings c,rust,go,python
```

## Commands And Outputs

| Command | What it does | Main output |
| --- | --- | --- |
| `javan check [path]` | Builds classes if needed, analyzes reachable code, and rejects unsupported native shapes before code generation. | `.javan/reports/report.*` plus report families |
| `javan build [path]` | Builds the default native app when a `main` exists. | `.javan/bin/<name>` |
| `javan build [path] --jar` | Builds a normal JVM jar. | `.javan/dist/<name>.jar` |
| `javan build [path] --library` | Builds a native library package from explicit exports. | `.javan/dist/lib/<name>/...` |
| `javan run [path] -- args...` | Builds and runs the native app. | app stdout/stderr |
| `javan compat [path]` | Generates deterministic JDK/classfile inventory and support reports. | `.javan/reports/report.*`, `.javan/jdk-inventory`, `.javan/bytecode-patterns` |
| `javan report [path]` | Refreshes and prints the unified report view. | `.javan/reports/report.*` |
| `javan doctor` | Checks visible Java and native toolchain commands. | stdout |

## Support Snapshot

Current supported output shapes:

- Native executables from the supported bytecode/JDK subset.
- JVM jar output as a first-class build kind.
- Native libraries with C ABI plus generated C, Rust, Go, and Python bindings for
  primitives, `String`, `byte[]`, and the current result/error ABI.
- Unified reports for diagnostics, runtime footprint, dependencies, licenses, and
  library builds.

Current visible gaps:

- Broad JDK/API coverage remains partial even on the active JDK 25 gate.
- Full Java exception semantics, richer threading, and richer object/library ABI types
  remain incomplete.
- Remote release validation across Linux/macOS target rows is still not complete.

For the honest progress view, use:

- [doc/status/roadmap-progress.md](doc/status/roadmap-progress.md)
- [doc/status/support-matrix.md](doc/status/support-matrix.md)
- [doc/status/jdk-compatibility.md](doc/status/jdk-compatibility.md)

## Showcase

- Public showcase: [example/README.md](example/README.md)
- Long-form example policy and probes: [doc/spec/examples-and-test-projects.md](doc/spec/examples-and-test-projects.md)

## Docs

- Documentation index: [doc/README.md](doc/README.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Roadmap and status: [doc/status/roadmap-progress.md](doc/status/roadmap-progress.md), [doc/status/support-matrix.md](doc/status/support-matrix.md), [doc/status/jdk-compatibility.md](doc/status/jdk-compatibility.md)
- Native library ABI: [doc/spec/native-abi.md](doc/spec/native-abi.md)
- Release and verification: [doc/spec/release.md](doc/spec/release.md), [doc/spec/cross-platform-verification.md](doc/spec/cross-platform-verification.md)
- Runtime and packaging specs: [doc/spec/runtime-feature-selection.md](doc/spec/runtime-feature-selection.md), [doc/spec/container-images.md](doc/spec/container-images.md)
