# javan

Minimal native-first Java toolchain.

`javan` is a standalone executable that sits next to `javac`, Maven, and Gradle. It
keeps Java source normal, consumes compiled `.class` files, checks the reachable code,
lowers supported bytecode to javan IR, emits C, and links host-native artifacts.

The first product goal is simple: build Java into a native executable or native library
without making users think about compiler internals.

## Fast Start

From this checkout:

```sh
mvn -q package
java -jar target/javan-*.jar --version
java -jar target/javan-*.jar build examples/native-showcase
examples/native-showcase/.javan/bin/native-showcase
```

With an installed `javan` binary:

```sh
javan check .
javan build .
javan run . -- one two
javan build . --jar
javan build . --library --export com.acme.Math.add --bindings c,rust,go,python
```

Application arguments are passed after `--`; no Maven `-D...` tunnel is needed:

```sh
javan run . -- Alice 42
```

## Container Images

After a GitHub release is published, the container image workflow publishes Linux OCI
images to GHCR from the released Linux archives. They run on Linux directly and on
macOS/Windows through Docker Desktop or another Linux-container runtime.

```sh
docker run --rm ghcr.io/nanonative/javan:<version> --version
docker run --rm -v "$PWD:/workspace" -w /workspace ghcr.io/nanonative/javan:<version> build target/classes
```

| Image tag | Base | Platforms | Use |
| --- | --- | --- | --- |
| `ghcr.io/nanonative/javan:<version>` | Chainguard gcc-glibc | `linux/amd64`, `linux/arm64` | default image with C linker; expects compiled class output |
| `ghcr.io/nanonative/javan:<version>-wolfi` | Chainguard gcc-glibc | `linux/amd64`, `linux/arm64` | explicit default variant |
| `ghcr.io/nanonative/javan:<version>-distroless` | distroless C runtime | `linux/amd64`, `linux/arm64` | smaller runtime image, no shell |
| `ghcr.io/nanonative/javan:<version>-scratch` | scratch with copied dynamic runtime libs | `linux/amd64`, `linux/arm64` | smallest Linux runtime image |

Docker selects the host architecture from the multi-platform manifest. To force x64 on
Apple Silicon or Windows arm64:

```sh
docker run --rm --platform linux/amd64 ghcr.io/nanonative/javan:<version> --version
```

## Commands

| Command | Description | Main output |
| --- | --- | --- |
| `javan --version` | Prints the CLI version. | stdout |
| `javan inspect [path]` | Detects Java source/classes, Maven, Gradle, jars, resources, and output names. | `.javan/reports/project.json` |
| `javan check [path]` | Builds classes if needed, analyzes reachable code, rejects unsupported native shapes, and writes reports. | `.javan/reports/*` |
| `javan test [path]` | Runs the detected project test task after class output exists. | test exit code |
| `javan build [path]` | Builds the default native app when a `main` exists. | `.javan/bin/<name>` |
| `javan build [path] --jar` | Builds a normal JVM jar. | `.javan/dist/<name>.jar` |
| `javan build [path] --library` | Builds a native library package from explicit exports. | `.javan/dist/lib/<name>/...` |
| `javan run [path] -- args...` | Builds and runs the native app. | app stdout/stderr |
| `javan javac [args...]` | Runs the JDK-friendly `javac` wrapper for build tools and IDEs. | normal `javac` output |
| `javan compat [path]` | Generates deterministic JDK/classfile inventory and bytecode support reports. | `.javan/reports`, `.javan/jdk-inventory`, `doc/*` |
| `javan report [path]` | Reads existing report files and writes one Markdown/JSON summary. | `.javan/reports/report.*` |
| `javan clean [path]` | Removes generated `.javan` output. | deleted output |
| `javan doctor` | Checks visible Java and native toolchain commands. | stdout |
| `javan toolchain list` | Lists configured global toolchains. | stdout |
| `javan toolchain doctor` | Checks global javan toolchain settings. | stdout |

## Build Kinds

| User command | Kind | Requires `main` | Output |
| --- | --- | --- | --- |
| `javan build` | native app | yes | `.javan/bin/<name>` |
| `javan build --jar` | JVM jar | no, unless a runnable manifest is wanted | `.javan/dist/<name>.jar` |
| `javan build --library` | native library package | no | `.javan/dist/lib/<name>/...` |

Compatibility aliases still work for automation: `--kind app`, `--kind jar`,
`--kind library`, `--kind staticlib`, and `--kind sharedlib`.

## Common Options

| Option | Meaning |
| --- | --- |
| `--main com.acme.Main` | Selects the app entry class when detection is ambiguous. |
| `--classes <dir>` | Adds an explicit class output directory. |
| `--classpath <paths>` / `-cp <paths>` | Adds dependency jars/classes. |
| `--output <name>` / `-o <name>` | Overrides the inferred artifact base name. |
| `--jar` | Builds JVM jar output. |
| `--library` / `--lib` | Builds a native library package from exported methods. |
| `--format static\|shared\|both` | Selects native library formats. |
| `--export com.acme.Math.add` | Exposes a static Java method as a C ABI root. |
| `--export com.acme.Math.add(int,int):int` | Export form for overloads. |
| `--bindings c,rust,go,python` | Generates language binding folders for the library ABI. |
| `--profile core\|service\|library\|strict` | Records intended runtime profile in reports. |
| `--release` | Enables release build mode. Optimizations are still conservative. |
| `--target <os-arch>` | Asserts the host target; cross-linking is not claimed yet. |

## Project Detection

`javan` accepts a project directory, classes directory, jar, or single Java source file.
For Maven and Gradle it uses normal build output folders, including multi-module
`target/classes` and `build/classes/java/main` directories.

Output names are inferred from Maven `artifactId`, Gradle `rootProject.name`, jar/file
name, or directory name. Override with `--output`.

## Native Libraries

Library mode starts reachability at explicit exports instead of `Main.main`.

```sh
javan build . --library --export com.acme.Math.add --bindings c,rust,go,python
javan build . --library --format shared --export 'com.acme.Text.greet(String):String'
```

Supported ABI surface today:

| Java type | C ABI shape |
| --- | --- |
| primitives | stable C integer/float types |
| `String` input | UTF-8 `char*` |
| `String` return | javan-owned UTF-8 `char*`; release with `javan_free` |
| `byte[]` input | pointer + length |
| `byte[]` return | javan-owned pointer + length; release with `javan_free` |

Generated folders:

| Output | Path |
| --- | --- |
| static/shared artifacts | `.javan/dist/lib/<name>/c` |
| C header | `.javan/dist/bindings/c/<name>.h` |
| Rust wrapper | `.javan/dist/bindings/rust/` |
| Go cgo wrapper | `.javan/dist/bindings/go/` |
| Python ctypes wrapper | `.javan/dist/bindings/python/` |
| ABI/report files | `.javan/reports/library-build.*` |

Full ABI contract: [doc/spec/native-abi.md](doc/spec/native-abi.md).

## Resources

Resource files are preserved as artifacts.

| Resource behavior | Status |
| --- | --- |
| Copy `src/main/resources` and `resources` into class output for plain Java projects | `[x]` |
| Include resources in `--jar` output | `[x]` |
| Copy resources beside native app/library artifacts | `[x]` |
| Report copied resources | `[x]` |
| Native `ClassLoader.getResource*` runtime API | `[ ]` |
| Embed resource bytes into generated C runtime tables | `[ ]` |

Static web assets, images, `.properties`, templates, and similar files are therefore
kept with the build output today. Reading those resources through Java APIs in the native
binary is not claimed yet.

## Supported JDKs

`javan` reads each class file version automatically. Users should not pass the Java
version manually for supported class files.

| JDK | Class file major | Status |
| --- | ---: | --- |
| 21 | 65 | planned release-gate |
| 22 | 66 | planned release-gate |
| 23 | 67 | planned release-gate |
| 24 | 68 | planned release-gate |
| 25 | 69 | current development/release gate |

Inventory is not native support. Inventory answers what exists in a JDK. Native support
means every reachable class/member/bytecode shape is either implemented or rejected
clearly before code generation.

## Feature Status

| Tool feature | Status |
| --- | --- |
| Plain Java, Maven, Gradle, classes directory, jar input detection | `[x]` |
| Automatic main-class detection | `[x]` |
| Native executable output | `[x]` |
| JVM jar output | `[x]` |
| Native library output | `[x]` |
| C/Rust/Go/Python binding generation | `[x]` |
| Resource artifact preservation | `[x]` |
| Reachability analysis | `[x]` |
| Unsupported reachable-code rejection | `[x]` |
| Unified report command | `[x]` |
| Runtime/binary contract reports | `[x]` |
| JDK inventory and bytecode-pattern reports | `[x]` |
| Managed heap and single-thread safe-point GC subset | `[-]` |
| Runtime feature selection enforcement | `[-]` |
| IDE diagnostics through `javan javac` | `[ ]` |
| Maven plugin | `[ ]` |
| Gradle plugin | `[ ]` |
| Homebrew formula | `[ ]` |
| GHCR container images | `[-]` |
| Windows release artifact | `[ ]` |

| Java/native feature | Status |
| --- | --- |
| primitive arithmetic and primitive arrays | `[x]` |
| object arrays and `String[] args` | `[x]` |
| simple objects, constructors, fields, records | `[-]` |
| enums | `[-]` |
| interface and virtual dispatch | `[-]` |
| string concat and selected string intrinsics | `[-]` |
| simple exception panic/catch support | `[-]` |
| full exceptions | `[ ]` |
| threads, monitors, virtual threads | `[ ]` |
| arbitrary reflection/runtime scanning | `[!]` |
| dynamic class loading | `[!]` |
| JNI/native method loading | `[!]` |

Legend: `[x]` implemented for stated scope, `[-]` scoped subset with clear rejection for
unsupported shapes, `[ ]` planned, `[!]` rejected for native output.

## Intrinsics And Substitutions

Intrinsics are the intended native compiler path for JDK/runtime hotspots. They avoid
pulling VM internals into small native artifacts and must be visible in reports.

| API/pattern | Status |
| --- | --- |
| `Objects.requireNonNull(Object)` | `[x]` |
| `Math.abs/min/max` selected int/long forms | `[x]` |
| `System.nanoTime()` / `currentTimeMillis()` | `[x]` |
| `System.arraycopy` | `[x]` |
| `Arrays.copyOf` selected primitive/object forms | `[-]` |
| array `clone()` selected forms | `[-]` |
| `Integer.toString` / `Long.toString` | `[x]` |
| `String.length/isEmpty/charAt/equals` | `[-]` |
| javac `StringConcatFactory` concat shapes | `[-]` |
| `SecureRandom.nextBytes` | `[ ]` |
| `UUID.randomUUID` | `[ ]` |
| `Path.of` / `Files.*` selected helpers | `[ ]` |
| `System.getenv` / `System.getProperty` subset | `[ ]` |

## Showcase

The only public example is [examples/native-showcase](examples/native-showcase). It shows
the currently supported path without pretending to be a full application framework.

```sh
mvn -q package
java -cp target/classes javan.Main build examples/native-showcase --output native-showcase
examples/native-showcase/.javan/bin/native-showcase
```

Expected output:

```text
javan native showcase
metric requests -> 9
first request
samples 3
copy 8
name-length 8
char e
same true
safe deterministic native build
```

Regression projects live under `src/test/resources/projects`:

| Folder | Purpose |
| --- | --- |
| `acceptance` | end-to-end release acceptance projects |
| `native-profile` | one-assumption native runtime/codegen scenarios |
| `negative` | deterministic rejection scenarios |
| `real-probes` | TypeMap/Nano probes when local artifacts exist |

## Build And Release

Local build:

```sh
mvn -q verify
scripts/build.sh
```

Local release gate:

```sh
.github/scripts/verify-release.sh
```

The release workflow owns versioning. On `main` or manual dispatch it computes
`vYYYY.M.D`, updates `pom.xml` and `CHANGELOG.md`, builds Linux/macOS x64/aarch64
packages, pushes the release commit/tag, and creates the GitHub release. Manual
`dry_run` builds packages without committing, tagging, publishing, or building images.
After a successful GitHub release, the container image workflow can be replayed by tag
without rebuilding the release archives.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `src/main/java` | javan CLI, classfile reader, analyzer, IR, codegen, linker, reports |
| `src/main/resources` | packaged CLI metadata |
| `src/test/java` | public-entrypoint regression tests |
| `src/test/resources/projects` | test projects and native probes |
| `examples/native-showcase` | one public showcase |
| `doc` | specs, roadmap, compatibility, and generated support docs |
| `scripts/build.sh` | local self-hosting build script |
| `.github/scripts` | CI/release validation helpers |

## Further Docs

- [Roadmap](doc/spec/roadmap.md)
- [Roadmap progress](doc/roadmap-progress.md)
- [Support matrix](doc/support-matrix.md)
- [JDK compatibility](doc/jdk-compatibility.md)
- [Native ABI contract](doc/spec/native-abi.md)
- [Memory/runtime correctness](doc/spec/memory-runtime-correctness.md)
- [Release gates](doc/spec/release.md)
- [Examples and test projects](doc/spec/examples-and-test-projects.md)
