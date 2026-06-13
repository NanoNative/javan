# javan

Minimal native-first Java toolchain.

`javan` keeps Java source normal, consumes existing `.class` files when possible,
checks a closed native profile, lowers reachable bytecode to a compact IR, emits C,
and links native artifacts. It also keeps a normal JVM jar path available, because
shipping Java should not become harder just because native output exists.

## Fast Start

From this source checkout:

```sh
mvn -q -DskipTests package
java -jar target/javan-0.1.0.jar --version
java -jar target/javan-0.1.0.jar build examples/hello
examples/hello/.javan/bin/hello
```

With an installed `javan` executable:

```sh
javan build .
javan run . -- one two
javan build . --kind jar
javan build . --kind staticlib --export com.acme.Math.add --bindings c,rust,go,python
```

The default native app output is `.javan/bin/<project-name>`. The name is inferred
from Maven `artifactId`, Gradle `rootProject.name`, the input file/jar name, or the
directory name. Override it with `--output <name>`.

## Build Pipelines

| Build kind | Pipeline | Output |
| --- | --- | --- |
| `app` | Java `.class` -> javan IR -> C -> native linker | `.javan/bin/<name>` |
| `jar` | Java source/classes/resources -> JVM jar | `.javan/dist/<name>.jar` |
| `staticlib` | Java `.class` exports -> javan IR -> C -> native linker -> C ABI | `.javan/dist/lib<name>.a` |
| `sharedlib` | Java `.class` exports -> javan IR -> C -> native linker -> C ABI | `.javan/dist/lib<name>.so`, `.dylib`, or `.dll` |

## Commands

| Command | What it does | Main outputs |
| --- | --- | --- |
| `javan --version` | Prints the CLI version. | stdout |
| `javan inspect [path]` | Detects project layout, build tool, classes, sources, resources, and output names. | `.javan/reports/project.json` |
| `javan check [path]` | Builds classes if needed, scans reachable code, rejects unsupported native bytecode, and writes reports. | reachability, diagnostics, intrinsics, deduplication, optimizer scaffold |
| `javan test [path]` | Runs the detected project test task after ensuring classes exist. | test process exit code |
| `javan build [path]` | Produces the selected build kind. Default is a native app. | `.javan/bin` or `.javan/dist` |
| `javan run [path] -- args...` | Builds a native app, then executes it with passthrough args. | app stdout/stderr |
| `javan javac [args...]` | JDK-friendly wrapper around `javac`; intended for IDE/build-tool integration. | normal `javac` outputs |
| `javan compat [path]` | Builds/scans classes, inventories the active JDK, classifies bytecode support, and writes deterministic reports. | `.javan/reports`, `.javan/jdk-inventory`, `.javan/bytecode-patterns`, `docs/` |
| `javan clean [path]` | Removes the project `.javan` folder. | deleted `.javan` |
| `javan doctor` | Checks visible local Java/native toolchain commands. | stdout |
| `javan toolchain list` | Lists globally known toolchains from the user `.javan` home. | stdout |
| `javan toolchain doctor` | Checks global javan settings/toolchain health. | stdout |

## Options

| Option | Used by | Meaning |
| --- | --- | --- |
| `--main com.acme.Main` | `check`, `build`, `run`, `compat` | Sets the app entry class when auto-detection is ambiguous. For `--kind jar`, it writes `Main-Class` into the manifest. |
| `--classes <dir>` | `check`, `build`, `run`, `compat` | Adds an explicit classes directory to scan instead of relying only on detection. |
| `--classpath <paths>` / `-cp <paths>` | `check`, `build`, `run`, `compat` | Adds dependency jars/classes. Uses the platform path separator. |
| `--output <name>` / `-o <name>` | `build`, `run`, `inspect` | Overrides the inferred binary/library/jar base name. |
| `--kind app` | `build`, `run` | Builds a native executable and requires a supported `main`. |
| `--kind jar` | `build` | Builds a JVM jar. No native profile check and no `main` required unless you want a runnable jar manifest. |
| `--kind staticlib` | `build` | Builds a native static library. No `Main.main`; reachability starts at exported methods. |
| `--kind sharedlib` | `build` | Builds a native shared library. No `Main.main`; reachability starts at exported methods. |
| `--export com.acme.Math.add` | library builds | Exposes a static Java method through the C ABI and starts reachability there. |
| `--export com.acme.Math.add(int,int):int` | library builds | Same export, but with an explicit Java-like signature when overloads exist. |
| `--bindings c,rust,go,python` | library builds | Generates FFI binding files. C headers are the base; Rust/Go/Python wrap that ABI. |
| `--profile core|service|library|strict` | `check`, `build`, `run` | Records intended profile in reports. Lowering is not profile-specialized yet. |
| `--release` | `build`, `run` | Accepted release-mode flag. Optimization behavior is still conservative. |
| `--target <triple>` | `build` | Accepted target-triple flag. Cross-linking is not release-gated yet. |
| `--no-build` | `check`, `build`, `run`, `compat` | Reuses existing class files and skips Maven/Gradle/javac invocation. |

## Native Library Exports

Library mode is for calling Java code from C, Rust, Go, or Python without requiring
`public static void main(String[] args)`.

```sh
javan build . --kind staticlib --export com.acme.Math.add --bindings c,rust,go,python
javan build . --kind sharedlib --export 'com.acme.Text.greet(String):String'
```

`--export com.acme.Math.add` means: find the static method `com.acme.Math.add`,
make it a C ABI entry point, and treat it as a reachability root. If a method is
overloaded, use the signature form:

```text
com.acme.Math.add(int,int):int
```

The C ABI supports primitives, UTF-8 `char*` strings, and `byte[]` as pointer plus
length. Returned strings and byte buffers are owned by javan and must be released
with `javan_free`.

Generated library outputs:

| Output | Path |
| --- | --- |
| Static library | `.javan/dist/lib<name>.a` |
| Shared library | `.javan/dist/lib<name>.so`, `.dylib`, or `.dll` |
| C header | `.javan/dist/bindings/c/<name>.h` |
| Rust wrapper | `.javan/dist/bindings/rust/lib.rs` |
| Go cgo wrapper | `.javan/dist/bindings/go/<name>.go` |
| Python ctypes wrapper | `.javan/dist/bindings/python/<name>.py` |
| Metrics report | `.javan/reports/library-build.md` and `.json` |

## Resources

Resource files are supported as artifacts. `javan` detects `src/main/resources` and
`resources`, copies non-Java/non-class files into class output for plain `javac`
projects, includes them in `--kind jar`, and preserves them for native builds.

| Resource behavior | Integrated | Output |
| --- | --- | --- |
| Include resources in `--kind jar` | [x] | entries inside `.javan/dist/<name>.jar` |
| Copy resources for native app/library builds | [x] | `.javan/resources` and `.javan/dist/resources` |
| Remove stale generated resource files | [x] | deleted source resources do not remain in rebuilt jars |
| Report copied resources | [x] | `.javan/reports/resources.md` and `.json` |
| Native `ClassLoader.getResource*` / `getResourceAsStream` runtime API | [ ] | planned |
| Embed resource bytes into generated C runtime tables | [ ] | planned |

Static web assets, pictures, language `.properties`, templates, and similar files
are therefore preserved beside native artifacts today. Reading them through Java
resource APIs in the generated native binary is the next slice, not claimed here.

## Supported JDKs

`javan` reads the class file major version from each `.class` file automatically.
Users should not need to pass a JDK version for supported class files. Unknown future
bytecode patterns are rejected before native code generation.

| JDK | Class file major | Release-gate status | Notes |
| --- | ---: | --- | --- |
| 21 | 65 | planned matrix target | Inventory/diff flow is designed for it, but this checkout is not CI-gated on JDK 21 yet. |
| 22 | 66 | planned matrix target | Same deterministic scan path; not release-gated yet. |
| 23 | 67 | planned matrix target | Same deterministic scan path; not release-gated yet. |
| 24 | 68 | planned matrix target | Same deterministic scan path; not release-gated yet. |
| 25 | 69 | integrated local gate | Current development and verification JDK. |

Compatibility output is written to:

- `.javan/reports/compatibility-summary.md`
- `.javan/reports/compatibility-summary.json`
- `.javan/reports/jdk-<version>-inventory.json`
- `.javan/reports/bytecode-patterns-jdk-<version>.json`
- `.javan/jdk-inventory/jdk-<version>.json`
- `.javan/bytecode-patterns/jdk-<version>.json`
- `docs/support-matrix.md`
- `docs/support-matrix.json`
- `docs/jdk-compatibility.md`

## Feature Status

| Feature | Integrated | Status |
| --- | --- | --- |
| Plain Java, Maven, Gradle, classes directory, jar input detection | [x] | Implemented. |
| Automatic `Main.main` detection | [x] | Implemented for app builds. |
| JVM jar output | [x] | `javan build --kind jar`; no native subset restriction. |
| Native executable output | [x] | Host-native app builds through generated C and system linker. |
| Static/shared native library output | [x] | `staticlib` and `sharedlib` with explicit exports. |
| C, Rust, Go, Python binding generation | [x] | Generated wrappers for supported C ABI signatures. |
| Resource file preservation | [x] | Included in jars and copied beside native artifacts. |
| Native Java resource API support | [ ] | Planned. |
| Reachability analysis | [x] | Starts from `main` or exported methods. |
| Unsupported reachable bytecode rejection | [x] | Fails before native codegen. |
| Unsupported unreachable application bytecode warning | [x] | Reported as warning. |
| Unsupported unreachable dependency bytecode | [x] | Skipped unless reachable. |
| JDK inventory and bytecode pattern reports | [x] | Active JDK scan is deterministic. |
| `int`, `long`, `float`, `double`, `boolean` lowering | [x] | Supported in explicit tested shapes. |
| Primitive array variants | [x] | `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`. |
| Object arrays and `String[] args` | [x] | Supported in tested shapes. |
| Constructors, fields, simple objects, records | [x] | Closed-world, supported bytecode only. |
| Enums | [x] | Basic constants, `name()`, and `toString()`. |
| Interface and virtual dispatch | [x] | Closed-world dispatch tables and monomorphic direct cases. |
| String concat lowering | [x] | Supported `StringConcatFactory` shapes. |
| String intrinsics | [x] | `length`, `isEmpty`, `charAt`, `equals` for native string values. |
| Platform exception panic and simple same-method catch | [x] | Narrow exception support. |
| Human-readable exception cleanup | [ ] | Roadmap. |
| Runtime-risk warnings (`javan check --strict`) | [ ] | Roadmap. |
| Deduplication detection/planning | [x] | Reports infrastructure/string/helper candidates. |
| Actual emitted-helper deduplication | [ ] | Roadmap. |
| Redundant-check elimination | [ ] | Roadmap. |
| Escape analysis and stack allocation | [ ] | Roadmap. |
| Method specialization and inlining | [ ] | Roadmap. |
| Generic specialization and boxing elimination | [ ] | Roadmap. |
| Global JDK/toolchain install/download | [ ] | Roadmap. Current `toolchain` commands inspect configured state. |
| Maven/Gradle plugins | [ ] | Roadmap. |
| Homebrew formula | [ ] | Roadmap. |
| JetBrains plugin | [ ] | Roadmap. |

## Intrinsics And Substitutions

Intrinsics are the intended path, not a shameful workaround. Native Java compilers
need explicit substitutions for platform/JDK hotspots where normal bytecode either
depends on VM internals or would pull too much runtime. Every intrinsic must be
visible in reports and tested against JVM behavior.

| API or pattern | Integrated | Native behavior / limit |
| --- | --- | --- |
| `Objects.requireNonNull(Object)` | [x] | Native null guard; message overloads are not supported yet. |
| `Math.abs(int/long)` | [x] | JVM-compatible integer/long behavior, including min values. |
| `Math.min/max(int,int)` and `(long,long)` | [x] | Direct native comparisons. |
| `System.nanoTime()` | [x] | Runtime time module. |
| `System.currentTimeMillis()` | [x] | Runtime time module. |
| `System.arraycopy(Object,int,Object,int,int)` | [x] | Native array helper with type and bounds checks. |
| `Arrays.copyOf` for `int`, `long`, `byte`, `short`, `char`, `float`, `double`, `Object` | [x] | Native array copy helpers; `boolean[]` overload is still unsupported. |
| `Integer.toString(int)` | [x] | Native number-to-string helper. |
| `Long.toString(long)` | [x] | Native number-to-string helper. |
| `String.length/isEmpty/charAt/equals` | [x] | Native string helpers for supported string values. |
| javac `StringConcatFactory` concat | [x] | Supported concat shapes; unsupported invokedynamic forms are rejected. |
| `SecureRandom.nextBytes` | [ ] | Planned OS entropy substitution. |
| `UUID.randomUUID` | [ ] | Planned runtime substitution. |
| `Path.of`, `Files.readString`, `Files.writeString` | [ ] | Planned IO substitutions. |
| `System.getenv` / `System.getProperty` subset | [ ] | Planned controlled runtime substitutions. |
| Base64 encoder/decoder | [ ] | Planned. |

## Unsupported Native Features

| Feature | Native behavior today | Workaround |
| --- | --- | --- |
| Reflection and runtime scanning | Rejected when reachable. | Use explicit code, exports, and closed-world wiring. |
| Dynamic class loading and arbitrary `ClassLoader` APIs | Rejected when reachable. | Put dependencies on the classpath at build time. |
| JNI/native methods and `System.load*` | Rejected when reachable. | Use `--kind staticlib/sharedlib` C ABI exports instead. |
| General lambdas and arbitrary `invokedynamic` | Rejected unless it is a supported string concat shape. | Use explicit classes/static methods for now. |
| General try/catch/finally and complex exception tables | Rejected outside supported same-method platform catch shape. | Keep native-profile code simple until exception lowering grows. |
| Threads, monitors, `synchronized`, volatile coordination | Rejected when reachable. | Keep native-profile code single-threaded for now. |
| Serialization | Rejected/unsupported. | Use explicit DTO parsing/formatting. |
| Full JDK collections/streams/Optional runtime | Not generally supported. | Use arrays/simple objects or wait for targeted substitutions. |
| Native UI frameworks | Unsupported. | JavanUI/Studio are separate roadmap tracks. |
| Cross-target releases | Not release-gated. | Build on the target host today. |
| Windows release artifact | Shared library naming exists; Windows CI/release is not first-gate. | Build locally when the toolchain path is ready. |

## Build And Release

```sh
mvn verify
scripts/verify-release.sh
```

Build the `javan` CLI itself as a native executable with GraalVM `native-image`:

```sh
scripts/build-javan-native.sh
dist/javan --help
dist/javan --version
```

Package the host-native executable:

```sh
scripts/package-release.sh
scripts/verify-package.sh build/release/javan-0.1.0-<target>.tar.gz
```

Release packaging rejects snapshot versions and target mismatches.

## Examples

```sh
dist/javan build examples/hello
dist/javan build examples/object-fields
dist/javan build examples/int-array
dist/javan build examples/long-array
dist/javan build examples/float-double
dist/javan build examples/boolean-basic
dist/javan build examples/long-basic
dist/javan build examples/exception-panic
dist/javan build examples/try-catch
dist/javan build examples/enum-basic
dist/javan build examples/static-fields
dist/javan build examples/interface-dispatch
dist/javan build examples/polymorphic-virtual
dist/javan build examples/interface-polymorphic
dist/javan build examples/string-intrinsics
dist/javan build examples/string-concat
dist/javan build examples/primitive-arrays
dist/javan build examples/native-library --kind staticlib --export com.acme.Math.add --bindings c,rust,go,python
examples/typemap-pair/build-example.sh
examples/nano-metric/build-example.sh
```

The acceptance runner builds supported examples, compares app output against the
JVM where applicable, validates native-library C ABI output, and checks negative
examples:

```sh
scripts/acceptance.sh
```

## Further Docs

- [Roadmap](doc/spec/roadmap.md)
- [Library output](doc/spec/library-output.md)
- [Release gates](doc/spec/release.md)
- [GitHub workflow testing](doc/spec/github-workflow-testing.md)
- [OSS readiness](doc/spec/oss-readiness.md)
- [Optimizer roadmap](doc/spec/optimizer-roadmap.md)
- [Human-readable exceptions roadmap](doc/spec/human-readable-exceptions.md)
- [Runtime-risk warnings roadmap](doc/spec/runtime-risk-warnings.md)
- [Real project readiness](doc/spec/real-project-readiness.md)
- [Feature lab workflow](doc/spec/feature-lab-workflow.md)
- [Cross-platform verification](doc/spec/cross-platform-verification.md)
- [Support matrix](docs/support-matrix.md)
- [Testing](doc/spec/testing.md)
