# javan

A native compiler for ordinary Java build outputs.

`javan` consumes compiled `.class` files, checks reachable code, lowers the supported
subset to C, and links host-native executables or native libraries without changing
normal Java source.

## Status

| Question | Current answer |
| --- | --- |
| Is it useful today? | Yes for small deterministic native apps and native libraries built from the supported subset. |
| Which JDK is actively gated? | Project verification uses JDK 25. A wider JDK matrix remains planned. |
| What is solid now? | Native app output, JVM jar output, native library packaging, reports, and the current showcase path. |
| What is still incomplete? | Broad JDK coverage, full exception semantics, thread/runtime breadth, richer library ABI types, and package support beyond the three declared release targets. |
| Can it rebuild itself? | Yes, within the supported subset; historical package-backed proof is linked in [verification](docs/verification.md). A new release still needs candidate-specific evidence. |

## Quick Start

From this checkout:

```sh
./mvnw -q package
java -jar target/javan-*.jar --version
java -jar target/javan-*.jar build example --output native-showcase
example/.javan/bin/native-showcase
```

With an installed `javan` binary:

```sh
javan check .
javan build .
javan build . --jobs 2
javan run . -- one two
javan build . --jar
javan build . --library --export com.acme.Math.add --bindings c,rust,go,python
```

Native application builds currently compile generated C objects one at a time. Use `--jobs <count>`
to record the requested worker cap; the resulting `.javan/reports/native-object-cache.*`
records the requested cap, effective cap of one, queued objects, and outcome. Parallel native
compilation remains blocked until its executor runtime, cleanup, interruption, self-host, and
package-backed proof are complete. Final native linking remains serial and ordered.

## Use Javan As A JDK

`javan install` creates a JDK-shaped facade over a real JDK. It does not replace or
modify the original JDK. The command prints the exact `jdk home` path to select in an IDE,
set as `JAVA_HOME` for a shell, or place before other JDK `bin` directories on `PATH`.

```sh
javan install

# Replace <javan-jdk-home> with the printed `jdk home` path.
<javan-jdk-home>/bin/java --version
<javan-jdk-home>/bin/javac -d target/classes src/main/java/com/acme/Main.java
<javan-jdk-home>/bin/java jdk list
<javan-jdk-home>/bin/java jdk use 25
<javan-jdk-home>/bin/java jdk use temurin@25
```

The facade delegates ordinary `java` and `javac` work to the selected original JDK. It
adds Javan reporting to `javac` compilation, while every other JDK tool, such as `jar`,
`javadoc`, and `javaw`, remains the original vendor tool. Javan first uses a matching local
JDK. Its built-in default is Java `25`, with verified Temurin used only when no local JDK 25
exists. Users may optionally choose a default selector in `~/.javan/settings.toml`:

```toml
default_jdk = "25"
```

Javan never changes shell profiles, `PATH`, `JAVA_HOME`, or the vendor JDK automatically.
The detailed behavior and currently unsupported integration proof are documented in the
[JDK facade contract](docs/specs/toolchains.md#installed-facade).

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

## Call Graphs

Every reachability-backed `check`, native `build`, and `compat` run writes a deterministic,
analyzer-proven static call graph under `.javan/reports/`:

- `call-graph.json`: stable machine-readable nodes, edges, and exact per-method finding counts for IDEs and tools
- `call-graph.md`: concise human summary, finding list, and scope statement
- `call-flow.html`: self-contained visual flow that opens in a browser; no installation or network access required; errors are red and warnings amber
- `call-graph.dot`: optional Graphviz export for environments that already have Graphviz

The graph contains only closed-world method edges that Javan proved reachable. Dynamic or
unresolved behavior is never guessed as an edge; it remains explicit in diagnostics. A finding
colors a node only when its class, method, and descriptor exactly match a reachable method.
Every other finding remains visible as outside the current flow instead of being guessed onto a node.

## Support Snapshot

Current supported output shapes:

- Native executables from the supported bytecode/JDK subset.
- Configured static Java-to-C native imports and project-local link inputs, with a narrow
  primitive/borrowed-`byte[]` ABI: [native ABI contract](docs/specs/native-abi.md#generic-java-to-c-native-imports).
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

For current support and release state, use:

- [docs/status/support-matrix.md](docs/status/support-matrix.md)
- [docs/status/jdk-compatibility.md](docs/status/jdk-compatibility.md)
- [docs/specs/release.md](docs/specs/release.md)

## Showcase

- Public showcase: [example/README.md](example/README.md)
- Long-form example policy and probes: [docs/specs/examples-and-test-projects.md](docs/specs/examples-and-test-projects.md)

## Documentation

- Start here: [documentation map](docs/README.md)
- What JavaN promises: [compiler specification](docs/specs/compiler.md)
- What comes next: [roadmap](docs/roadmap.md#current-sequence)
- Why it works this way: [architecture decisions](docs/README.md#decisions)
- What has been proved: [verification history](docs/verification.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Generated support: [scenario matrix](docs/status/support-matrix.md), [JDK inventory](docs/status/jdk-compatibility.md)
- Native library ABI: [docs/specs/native-abi.md](docs/specs/native-abi.md)
- Release and verification: [release](docs/specs/release.md), [testing](docs/specs/testing.md)
- Runtime and packaging specs: [docs/specs/runtime-feature-selection.md](docs/specs/runtime-feature-selection.md), [docs/specs/container-images.md](docs/specs/container-images.md)
