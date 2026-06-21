# javan

Minimal native-first Java toolchain.

`javan` is a standalone executable that sits next to `javac`, Maven, and Gradle. It
keeps Java source normal, consumes compiled `.class` files, checks the reachable code,
lowers supported bytecode to javan IR, emits C, and links host-native artifacts.

The first product goal is simple: build Java into a native executable or native library
without making users think about compiler internals.

## At A Glance

| Question | Current answer |
| --- | --- |
| Can I use it today? | Yes for small supported native CLI/library slices; no for general Java apps yet. |
| Does it self-build? | Locally, Javan can self-check and rebuild a native Javan binary; remote OS/ARCH release gates remain. |
| Does HTTP/socket code work natively? | Not yet. Reachable socket/HTTP shapes fail clearly with `JAVAN061` and runtime-module reports. Positive TCP/HTTP runtime support is planned next. |
| Which JDK is actively inventoried? | JDK 25. JDK 17 and JDK 21 are planned release-accounting rows. |
| Is memory leak proof complete? | Generated app, native-library, and package-backed native self-host probes have local counter/sanitizer proof for current shapes. Remote release-matrix validation remains 0/4 completed, and full managed heap/thread roots remain unclaimed. |
| Are arbitrary reflection/JNI/dynamic loading supported? | No. Those are dismissed for native output; only explicit closed-world metadata reflection may be revisited later. |

Roadmap feature ledger snapshot:

| Bucket | Count | Meaning |
| --- | ---: | --- |
| Fully implemented | 3 | Release-gated for the named row. |
| In progress / partial | 17 | Some production behavior exists, but the row is not complete. |
| Planned | 15 | Specified and wanted, not claimed as supported. |
| Dismissed | 3 | Deliberately outside the native-support goal. |

## Fast Start

From this checkout:

```sh
mvn -q package
java -jar target/javan-*.jar --version
java -jar target/javan-*.jar build example
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

The release image workflow also verifies that the default image can build and run the
native showcase from compiled classes. Distroless and scratch images are version-smoke
images until they have a linker path.

## Commands

| Command | Description | Main output |
| --- | --- | --- |
| `javan --version` | Prints the CLI version. | stdout |
| `javan inspect [path]` | Detects Java source/classes, Maven, Gradle, jars, resources, and output names. | `.javan/reports/project.json` |
| `javan check [path]` | Builds classes if needed, analyzes reachable code, rejects unsupported native shapes, and writes reports. | `.javan/reports/report.*` plus families |
| `javan test [path]` | Runs the detected project test task after class output exists. | test exit code |
| `javan build [path]` | Builds the default native app when a `main` exists. | `.javan/bin/<name>` |
| `javan build [path] --jar` | Builds a normal JVM jar. | `.javan/dist/<name>.jar` |
| `javan build [path] --library` | Builds a native library package from explicit exports. | `.javan/dist/lib/<name>/...` |
| `javan run [path] -- args...` | Builds and runs the native app. | app stdout/stderr |
| `javan javac [args...]` | Runs the JDK-friendly `javac` wrapper for build tools and IDEs. | normal `javac` output |
| `javan compat [path]` | Generates deterministic JDK/classfile inventory and bytecode support reports. | `.javan/reports/report.*`, `.javan/jdk-inventory`, `doc/*` |
| `javan report [path]` | Refreshes and prints the unified Markdown/JSON summary from existing report families. | `.javan/reports/report.*` |
| `javan clean [path]` | Removes generated `.javan` output. | deleted output |
| `javan doctor` | Checks visible Java and native toolchain commands. | stdout |
| `javan toolchain list` | Lists configured global toolchains. | stdout |
| `javan toolchain doctor` | Checks global javan toolchain settings. | stdout |

## Reports

| File | Purpose |
| --- | --- |
| `.javan/reports/report.md` | One human-readable summary across all report families. |
| `.javan/reports/report.json` | One machine-readable summary for CI, IDEs, and tools. |
| `.javan/reports/diagnostics.txt` | Legacy plain diagnostic stream kept for stable CLI/report parsing. |
| `.javan/reports/diagnostics.md` | Human-readable diagnostics with severity, code, subject, reason, and fix. |
| `.javan/reports/diagnostics.json` | Machine-readable diagnostics for IDE integration and automation. |
| `.javan/reports/exceptions.*` | Source-focused generated runtime panic sites. |
| `.javan/reports/dependencies.*` | Resolved classpath entries, present/missing state, and used/unused reachability. |
| `.javan/reports/licenses.*` | License evidence from Maven metadata, packed license files, or unknown warnings. |
| `.javan/reports/runtime.*` | Native artifact size, linkage, symbols, and runtime contract. |
| `.javan/reports/library-build.*` | Native library ABI, ownership, bindings, and reachability metrics. |

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

## `javan.mod`

Plain Java projects can declare local dependencies without passing `--classpath`.

```text
module com.acme.app
java 25

require main libs/runtime.jar
require main com.acme:math:1.2.3
require test libs/test-support.jar
require tool tools/codegen.jar
```

| Line | Meaning |
| --- | --- |
| `module com.acme.app` | Project identity for the lock file. |
| `java 25` | Intended Java feature version. |
| `require main <path>` | Local jar/classes dependency used for app compile/check/build. |
| `require main group:artifact:version` | Main dependency resolved from the local Maven cache. |
| `require test <path>` | Local test dependency recorded in `javan.lock`; not added to native app classpath. |
| `require tool <path>` | Local tool dependency recorded in `javan.lock`; not added to native app classpath. |

Current scope is local filesystem jars/classes and direct Maven-style coordinates already
present in a local Maven repository. Javan checks `-Djavan.maven.localRepository=...`,
then `-Dmaven.repo.local=...`, then `~/.m2/repository`. GitHub packages, network
mirrors, auth, and transitive resolution fail clearly until those resolvers exist.
When `javan.mod` is present, `javan` writes deterministic `javan.lock` with scope, path,
artifact kind, size, and `fnv64` content checksum metadata.

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
| `String` input | UTF-8 `char*`; copied into a GC-managed Java string |
| `String` return | javan-owned UTF-8 `char*`; release with `javan_free` |
| `byte[]` input | pointer + length; copied into a GC-managed Java array |
| `byte[]` return | javan-owned pointer + length; release with `javan_free` |
| caught library runtime error | safe zero/null/default return; inspect `javan_last_error()` or structured `javan_last_error_*` fields |
| C result wrapper | `javan_try_*` returns `JavanResult`; diagnostic strings are owned and released with `javan_result_free` |
| Rust result wrapper | `try_javan_export_*` returns `Result<T, JavanError>` and copies/frees native diagnostics |
| Go result wrapper | `TryJavanExport*` returns `(T, error)` and copies/frees native diagnostics |
| Python result wrapper | `try_javan_export_*` returns Python values or raises `JavanError` |

Generated folders:

| Output | Path |
| --- | --- |
| static/shared artifacts | `.javan/dist/lib/<name>/c` |
| C header | `.javan/dist/bindings/c/<name>.h` |
| Rust wrapper | `.javan/dist/bindings/rust/` with `javan_free_string` / `javan_free_byte_array` |
| Go cgo wrapper | `.javan/dist/bindings/go/` with `JavanFree` / `JavanFreeByteArray` |
| Python ctypes wrapper | `.javan/dist/bindings/python/` with `free` / `free_byte_array` |
| ABI/report files | `.javan/reports/library-build.*` |

Full ABI contract: [doc/spec/native-abi.md](doc/spec/native-abi.md).

## Resources

Resource files are preserved as artifacts.

| Resource behavior | Status |
| --- | --- |
| Copy `src/main/resources` and `resources` into class output for plain Java projects | Done |
| Include resources in `--jar` output | Done |
| Copy resources beside native app/library artifacts | Done |
| Report copied resources | Done |
| Native `ClassLoader.getResource*` runtime API | Planned |
| Embed resource bytes into generated C runtime tables | Planned |

Static web assets, images, `.properties`, templates, and similar files are therefore
kept with the build output today. Reading those resources through Java APIs in the native
binary is not claimed yet.

## Supported JDKs

`javan` reads each class file version automatically. Users should not pass the Java
version manually for supported class files.

| JDK | Class file major | Status | Native support claim |
| --- | ---: | --- | --- |
| 17 | 61 | Planned | no release gate yet |
| 21 | 65 | Planned | no release gate yet |
| 22 | 66 | Planned | no release gate yet |
| 23 | 67 | Planned | no release gate yet |
| 24 | 68 | Planned | no release gate yet |
| 25 | 69 | Partial | active inventory and support-matrix gate |

Inventory is not native support. Inventory answers what exists in a JDK. Native support
means every reachable class/member/bytecode shape is either implemented or rejected
clearly before code generation.

Current support ledger:

| Measure | Count |
| --- | ---: |
| support rows | 93 |
| `pass` rows | 76 |
| `scoped` rows | 15 |
| `target` rows | 2 |
| active JDK 25 classes inventoried | 32,482 |
| active JDK 25 methods inventoried | 232,677 |

## Feature Status

Status words:

| Status | Meaning |
| --- | --- |
| Done | Implemented, tested, and release-gated for the stated scope. |
| Partial | Useful subset exists; unsupported reachable shapes fail clearly. |
| In progress | Production work is underway, but the release gate is not complete. |
| Planned | Wanted and specified, not claimed as supported yet. |
| Blocked | Waiting on remote platform/tool validation or an external prerequisite. |
| Dismissed | Deliberately outside the native-support goal, except for narrower future variants. |

| Tool feature | Status | Notes |
| --- | --- | --- |
| Plain Java, Maven, Gradle, classes directory, jar input detection | Done | Detection exists; plugins are separate planned integrations. |
| Automatic main-class detection | Done | Ambiguous projects can still pass `--main`. |
| Native executable output | Done | Host-native app path for supported bytecode. |
| JVM jar output | Done | Jar remains a first-class output. |
| Native library C ABI | Partial | Static/shared package layout, primitive/`String`/`byte[]`, result/error ABI, and ownership tests exist; richer object ABI remains. |
| Rust library bindings | Partial | Generated Rust FFI wrapper exists for the current C ABI. |
| Go library bindings | Partial | Generated cgo wrapper exists for the current C ABI. |
| Python library bindings | Partial | Generated `ctypes` wrapper exists for the current C ABI. |
| Resource artifact preservation | Partial | Files are copied and reported; Java runtime lookup APIs remain. |
| Reachability analysis | Done | Used by check/build/report and unsupported-code rejection. |
| Unsupported reachable-code rejection | Done | Unsupported reachable shapes fail before native corruption. |
| Unified report output | Done | `check`, `build`, `compat`, and `report` refresh `report.md/json` for current report families. IDE-grade diagnostics are tracked separately. |
| Runtime/binary contract reports | Partial | Runtime footprint/ownership reporting exists; enforcement remains. |
| Human-readable generated panic reports | Partial | Source-focused panics exist; full exception mapping remains. |
| Dependency usage and license reports | Partial | Local classpath/cache evidence exists; transitive/network/auth remain. |
| `javan.mod` local path/local Maven-cache dependencies and `javan.lock` | Partial | Direct local dependencies only. |
| Runtime owned-buffer validation | Done | Covered for current ABI/runtime helpers. |
| Native ABI retained input ownership | Done | Covered by C ABI smoke tests. |
| Exact native substitution contracts | Done | Host-only methods must be explicit contracts. |
| Clean self-host native check profile | Done | Current self-check has 0 diagnostics. |
| JDK inventory and bytecode-pattern reports | Partial | JDK 25 is active; 17/21/22/23/24 release gates remain. |
| Managed heap and single-thread safe-point GC subset | Partial | Thread roots and all Java heap shapes remain. |
| Runtime feature selection enforcement | Partial | Disabled reachable modules fail; disabled unused modules report as omitted. Size/speed/self-contained backend selection remains. |
| Network/HTTP unsupported diagnostics and report visibility | Done | `Socket`, `ServerSocket`, `HttpClient`, and Nano-style `HttpServer` shapes fail with `JAVAN061` and report `network/socket/http` modules. |
| Linux libc-free syscall runtime footprint | Planned | Linux-only raw-syscall runtime track. |
| IDE diagnostics through `javan javac` | Planned | Machine-readable reports exist, wrapper surfacing remains. |
| Maven plugin | Planned | Thin wrapper around installed `javan` binary. |
| Gradle plugin | Planned | Thin wrapper around installed `javan` binary. |
| Homebrew formula | Planned | After stable release artifacts. |
| GHCR container images | In progress | Built from released binaries after release. |
| Windows release artifact | Planned | Runtime/linker port missing. |

| Java/native feature | Status | Notes |
| --- | --- | --- |
| primitive arithmetic and primitive arrays | Done | Current support-matrix rows pass. |
| object arrays and `String[] args` | Done | Current support-matrix rows pass. |
| simple objects, constructors, fields, records | Partial | Common shapes work; broader class/JDK shapes remain. |
| enums | Partial | Basic enum rows exist; full enum/JDK behavior remains. |
| interface and virtual dispatch | Partial | Scoped dispatch rows exist. |
| string concat and selected string intrinsics | Partial | UTF-8-focused subset; full UTF-16 semantics remain. |
| simple exception panic/catch support | Partial | Full Java exception semantics remain. |
| boxed primitive wrappers value-of/unbox and GC ownership | Partial | Current wrapper/GC rows exist. |
| HTTP and sockets | Planned | Wanted; not native-supported yet. |
| HTTPS/TLS/certificates | Planned | Wanted after sockets/plain HTTP; certificate APIs currently reject/report as network modules. |
| platform threads and virtual threads | Planned | Runtime scheduler/thread roots remain. |
| full exceptions | Planned | Needs full throw/catch/finally/runtime mapping. |
| arbitrary reflection/runtime scanning | Dismissed | Future closed-world metadata reflection may be explicit and reported. |
| dynamic class loading | Dismissed | Static native output cannot load arbitrary runtime classes. |
| JNI/native method loading | Dismissed | Native-library output is supported instead. |

## Unsupported Diagnostics

This table covers native-support diagnostics and rejection diagnostics. It does not cover
routine input/CLI problems like `JAVAN020`, `JAVAN021`, `JAVAN022`, `JAVAN900`,
`JAVAN901`, or `JAVAN902`.

Warning mirrors mean the same shape was found only in unreachable code:
`JAVAN101`, `JAVAN114`, `JAVAN130`, `JAVAN131`, `JAVAN145`, `JAVAN146`, and
`JAVAN161`.

| Code(s) | What it means | Why it is unsupported today | Class |
| --- | --- | --- | --- |
| `JAVAN001`, `JAVAN101` | forbidden dynamic API such as `Class.forName`, `ClassLoader`, reflection, `Proxy`, method handles, instrumentation, Java serialization, or `System.loadLibrary` | These APIs depend on runtime metadata, dynamic type generation, dynamic loading, or native library loading that breaks closed-world native compilation. | rejected by design |
| `JAVAN011` | reachable non-JDK call target cannot be resolved | Closed-world analysis needs every reachable application class on the classpath. If the class is missing, Javan cannot prove the call graph. | workaround/config |
| `JAVAN012` | reachable application dispatch target cannot be resolved | Interface/virtual dispatch could not be reduced to at least one concrete closed-world implementation. | temporary / closed-world restriction |
| `JAVAN013` | native method is reachable | Arbitrary JNI/native methods are not linkable by the current runtime model. Javan only supports explicit native-library output and exact runtime intrinsics/substitutions. | rejected by design for now |
| `JAVAN014`, `JAVAN114` | exception handler shape is outside the supported catch model | Current support is limited to direct same-method `athrow` ranges with known platform exception types. Full Java exception objects and handler routing are not complete yet. | temporary |
| `JAVAN015` | reachable enum synthetic `valueOf(String)` | Deterministic native lowering for enum `valueOf` is not implemented yet. | temporary |
| `JAVAN030`, `JAVAN130` | reachable bytecode shape is not supported | The verifier found a bytecode/invokedynamic/newarray shape that the current native profile does not implement yet. | temporary |
| `JAVAN031`, `JAVAN131` | reachable JDK call has no native model yet | The JDK method has no intrinsic, substitution, or supported runtime implementation in the current slice. | temporary |
| `JAVAN040` | code generation backend cannot emit C for a verifier-accepted instruction | The static profile allowed the shape, but the backend slice still lacks codegen for it. | temporary compiler gap |
| `JAVAN041` | unsupported call argument type | Internal lowering hit an invalid `void` argument shape. This is a backend restriction/integrity guard, not a supported public Java call shape. | integrity guard |
| `JAVAN042` | unsupported or uninitialized local variable shape | The backend only tracks initialized locals in the currently supported profile. | temporary compiler restriction |
| `JAVAN043` | enum constant could not be lowered | The compiler could not match the enum constant against the parsed enum fields. This usually means a classpath/recompile mismatch. | workaround/config |
| `JAVAN044` | array `clone()` kind has no helper | The runtime only has clone/copy helpers for selected array kinds. | temporary / scoped |
| `JAVAN045`, `JAVAN145` | `instanceof` target is outside the current type-metadata model | The runtime only has deterministic type metadata for application classes, `Object`, interfaces in the closed world, and selected boxed wrappers. | temporary / scoped |
| `JAVAN046`, `JAVAN146` | non-ASCII string constant needs the UTF-16 string model | Current native strings are modeled as UTF-8 C strings for the supported ASCII subset. Accepting arbitrary Unicode now would break Java `String` semantics and ABI ownership guarantees. | temporary / scoped |
| `JAVAN047` | support registry and lowering are out of sync | A call was declared supported, but lowering for it does not exist. This is a compiler integrity failure, not a user feature claim. | integrity guard |
| `JAVAN048` | native `ProcessRunner` substitution cannot allocate its result type | The exact substitution needs `javan.util.ProcessRunner.Result` in the closed world. | workaround/config |
| `JAVAN049` | bytecode stack shape is unsupported | The stack pattern before an instruction is not one the backend can currently lower safely. | temporary compiler gap |
| `JAVAN051` | conditional stack merge is unsupported | The compiler found a branch merge/value-selection pattern it cannot yet prove safe to lower. | temporary compiler gap |
| `JAVAN060` | runtime module selection is invalid | The config disabled an unknown module, or disabled a module that reachable code actually needs. | workaround/config |
| `JAVAN061`, `JAVAN161` | reachable network API has no native runtime model yet | The needed `network/socket/http` runtime slice is incomplete for that reachable API shape. | temporary |
| `JAVAN062` | supported stream call is used on the wrong receiver kind | Current stream support is scoped to streams returned directly from `java.net.Socket`. Generic `InputStream`/`OutputStream` lowering is not implemented yet. | temporary / scoped |

## Intrinsics And Substitutions

Intrinsics are the intended native compiler path for JDK/runtime hotspots. They avoid
pulling VM internals into small native artifacts and must be visible in reports.

| API/pattern | Status | Notes |
| --- | --- | --- |
| `Objects.requireNonNull(Object)` | Done | Intrinsic. |
| `Math.abs/min/max` selected int/long forms | Done | Intrinsic. |
| `System.nanoTime()` / `currentTimeMillis()` | Done | Intrinsic. |
| `System.arraycopy` | Done | Intrinsic. |
| `Arrays.copyOf` selected primitive/object forms | Partial | Unsupported shapes must reject. |
| array `clone()` selected forms | Partial | Unsupported shapes must reject. |
| `Integer.toString` / `Long.toString` | Done | Intrinsic. |
| `String.length/isEmpty/charAt/equals` | Partial | UTF-8 subset; full UTF-16 remains. |
| javac `StringConcatFactory` concat shapes | Partial | Supported shapes only. |
| `SecureRandom.nextBytes` | Planned | Needs OS entropy module. |
| `UUID.randomUUID` | Planned | Depends on random/runtime module. |
| `Path.of` / `Files.*` selected helpers | Partial | Selected filesystem helpers only. |
| `Duration.ofMillis/ofSeconds/toMillis` | Partial | Small managed runtime object subset. |
| `javan.util.ProcessRunner.run(Path,List)` native substitution | Done | Exact host-only substitution contract. |
| `System.getenv` / `System.getProperty` subset | Planned | Needs environment/property policy. |

## Showcase

The only public example is [example](example). It shows
the currently supported path without pretending to be a full application framework.
This showcase is the rolling public proof target: visible compiler/runtime enrichments
should grow it or add another complete public example.

```sh
mvn -q package
java -cp target/classes javan.Main build example --output native-showcase
example/.javan/bin/native-showcase
```

Expected output:

```text
javan native showcase
metric requests -> 9
first request
iter first request
request second request
map 9
samples 3
copy 8
name-length 8
char e
same true
enum READY
static ready 1
caught boom
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

Local CI package smoke:

```sh
JAVAN_PACKAGE_TARGET=macos-aarch64 sh .github/scripts/verify-ci-package-smoke.sh
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
| `example` | one public showcase |
| `doc` | specs, process notes, ADRs, and status ledgers |
| `scripts/build.sh` | local self-hosting build script |
| `.github/scripts` | CI/release validation helpers |

## Further Docs

- [Roadmap](doc/spec/roadmap.md)
- [Documentation index](doc/README.md)
- [Roadmap progress](doc/status/roadmap-progress.md)
- [Support matrix](doc/status/support-matrix.md)
- [JDK compatibility](doc/status/jdk-compatibility.md)
- [Native ABI contract](doc/spec/native-abi.md)
- [Human-readable exceptions](doc/spec/human-readable-exceptions.md)
- [Memory/runtime correctness](doc/spec/memory-runtime-correctness.md)
- [Release gates](doc/spec/release.md)
- [Examples and test projects](doc/spec/examples-and-test-projects.md)
