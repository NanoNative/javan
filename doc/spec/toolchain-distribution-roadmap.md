# Toolchain Distribution Roadmap

Status: roadmap only. The first release direction is binary-first; see
[binary-first-distribution.md](binary-first-distribution.md). The JDK-shaped delegating
facade is the primary zero-integration path: Maven, Gradle, and IDE support may use it,
but are never required for it to work.

## Goal

Make `javan` present a JDK-shaped facade over an existing JDK without replacing or mutating
that original JDK.

The production distribution should install a standalone `javan` binary, add native-build
awareness around normal Java outputs, and remain transparent to IDEs and existing Java
build tools. Users should be able to install `javan`, select its JDK facade as a normal JDK,
and get:

- normal Java compilation through a real `javac`
- deterministic native-build checks and reports
- local-first JDK discovery and managed installation only when needed and allowed
- optional Maven, Gradle, and IDE integration without a different compiler path
- stable distribution metadata with versions, checksums, and source provenance

## Progress Accounting

Progress is reported by release evidence, never by command names or rejected API inventory.

| Measure | Current value | Meaning |
| --- | ---: | --- |
| Current local-resolution slice | 13/13 scenarios (100.0%) | Explicit, environment, current-JDK, PATH, managed-metadata, invalid-home, macOS-launcher, Windows-executable, and four known-root platform-discovery scenarios have focused proof. |
| Current managed-JDK download slice | 11/11 scenarios (100.0%) | Machine, user, and temporary storage policy; official Temurin 25 catalog selection; HTTPS-only archive retrieval; SHA-256 verification; staged extraction; atomic publish; toolchain registration; and non-elevated macOS `.pkg` expansion have focused proof. |
| Current javac-facade slice | 16/16 scenarios (100.0%) | Backend delegation, argument partitioning, `--jn-off`, `--jn-warn`, `--jn-strict`, `--jn-build`, help/version, complete analysis, unavailable analysis, failed compilation, native app output, and invocation-report proof have focused coverage. |
| Current facade-install slice | 18/18 focused scenarios (100.0%) | `javan install` copies the native launcher, creates a stable JDK home, and publishes a switchable whole-layout facade without changing the original JDK. `java jdk list`, `java jdk use 25`, and `java jdk use temurin@25` work through that facade. macOS public facades carry refreshed JDK bundle metadata and native launchers, verified locally through `java_home -V`. Installed facades use native `java`, `javac`, and `javan` launchers; Windows uses junctions, not batch-file impersonation. |
| Roadmap phases release-complete | 0/8 (0.0%) | No phase has all required cross-platform, package-backed, release evidence. |
| Roadmap phases initiated | 4/8 (50.0%) | Local resolution, managed-store policy, transparent `javan javac` selection, and the initial `--jn-*` parser have production code; this is not a claim that any phase is release-complete. |

The next report for every slice must state the current slice scenario count, current slice
percentage, release phases complete, release phases remaining, and the specific blocker
that prevents the next phase from being called complete.

## Principles

- Use real JDKs. Do not rebuild Java or ship fake platform classes.
- Keep Java source and `.class` output ordinary enough for existing tools.
- Prefer explicit installation over silent global mutation.
- Make every resolved toolchain deterministic: version, vendor, OS, architecture,
  download URL, checksum, and selected path.
- Keep project-local state separate from user-global state.
- Make unsupported native features visible early through `javan check` reports.
- Keep IDE integration optional: the wrapper must work when no tool knows Javan exists.
- Preserve every non-Javan `javac` argument, backend output text/order, and exit code; append
  only documented Javan help, version, diagnostic, or report output after backend output.
- Use safe defaults: compatibility reports are on; native package creation is explicit.
- Give Javan options one reserved namespace so normal `javac` options remain future-safe.
- Borrow established CLI conventions only when they reduce learning; define Javan semantics
  and names deliberately instead of imitating another tool's public contract.
- Treat JDK and dependency acquisition as a compliance boundary: preserve provenance,
  licenses, notices, terms, checksums, and the user's repository policy.
- Treat plugins and installers as thin entrypoints into the same CLI behavior.

## Distribution Shape

Initial deliverables:

- `javan` standalone executable
- `javan install` creating a stable, JDK-shaped facade at the first writable standard location
- optional platform-specific archive with helper launchers
- checksummed release manifest
- installation metadata under the user's home `.javan` directory

Optional later deliverables:

- bundled original JDK distribution where licensing permits
- Homebrew formula and bottles
- Maven plugin
- Gradle plugin
- JetBrains plugin if CLI reports and build plugins are not enough for good feedback

## User-Global Layout

Planned default home layout:

```text
~/.javan/
  settings.toml
  versions/
    javan/<version>/<os>-<arch>/
  jdks/
    <vendor>/<feature-version>/<build>/<os>-<arch>/
  toolchains/
    <toolchain-id>/
      toolchain.toml
      bin/
  cache/
    downloads/
    maven/
    gradle/
    probes/
  logs/
  reports/
```

Global settings should only contain user preferences and default policy. Project results
and project locks stay inside the project.

`javan install` and `javan jdk use 25` discover an already installed Java 25 JDK first. If
none is usable, they obtain Eclipse Temurin 25 from the official Adoptium catalog, download
over HTTPS, verify the catalog SHA-256 before extraction, stage and publish it atomically,
then register it under the user-global Javan home. Javan never invokes elevation or changes
an existing vendor JDK. Storage selection attempts the machine-wide location without
elevation, then the user-global Javan home, then an explicitly ephemeral temporary cache.
`JAVAN_HOME` or `-Djavan.home` changes the user-store root; it does not silently redirect a
machine-wide install.

On macOS, Adoptium currently distributes JDKs as signed `.pkg` archives. Javan expands the
verified package into its own selected store with `pkgutil --expand-full`; it never runs the
elevated system installer. The extracted `Contents/Home` is then verified and registered like
the tar/zip layouts used on other platforms.

## Managed JDK Placement

The verified installer will use this exact order. It must attempt to create both the JDK
installation root and its archive cache and confirm both are writable; an access or filesystem failure advances to the next
row. It must never invoke `sudo`, request elevation, or leave a partial installation selected.

| Scope | macOS install root | Linux install root | Windows install root | Archive cache | Persistence |
| --- | --- | --- | --- | --- | --- |
| Machine | `/Library/Java/JavaVirtualMachines` | `/usr/lib/jvm` | `%ProgramFiles%\\Java` | macOS: `/Library/Caches/Javan/downloads`; Linux: `/var/cache/javan/downloads`; Windows: `%ProgramData%\\Javan\\cache\\downloads` | Persistent |
| User | `~/.javan/jdks` | `~/.javan/jdks` | `~/.javan/jdks` | `~/.javan/cache/downloads` | Persistent |
| Temporary | `<java.io.tmpdir>/javan/jdks` | `<java.io.tmpdir>/javan/jdks` | `<java.io.tmpdir>/javan/jdks` | `<java.io.tmpdir>/javan/cache/downloads` | Ephemeral; may be cleaned by the OS |

The installer downloads to the selected cache only after resolving an official,
checksummed catalog entry. It verifies the archive before extraction, extracts to a staging
directory under the selected install root, atomically publish the final JDK directory, then
register a small `toolchains/<toolchain-id>/toolchain.toml` record below the user-global
Javan home. A temporary installation is never a durable default and must be reported as such.

Example global settings:

```toml
[defaults]
jdk = "temurin@lts"
target = "host"
profile = "core"

[downloads]
require_checksum = true
allow_prerelease = false
auto_install = "prompt" # prompt | always | never

[ide]
write_machine_reports = true
```

## Project-Local State

Planned project files:

```text
<project>/
  javan.toml
  javan.lock
  .javan/
    toolchain.lock.json
    reports/
    dist/
```

`javan.toml` describes intent. `javan.lock` and `.javan/toolchain.lock.json` describe
what was actually resolved.

The existing experimental `javan.mod` file is not the public direct-dependency contract.
Before direct dependency management is released, Javan must provide an explicit,
lossless migration to `javan.toml` or keep the experimental file private. It must not ship
two competing manifests or silently reinterpret existing dependency scopes.

The lock should include:

- `javan` version
- selected JDK vendor, version, OS, architecture, path, checksum
- selected C linker and version
- selected Maven or Gradle wrapper/version when used
- dependency coordinates and checksums
- target triple
- profile

## JDK Wrapper Model

`javan` should wrap an original JDK and delegate Java compilation to the original
`javac`. The wrapper must not provide fake Java standard-library classes.

There are two command surfaces. The direct `javan` CLI is concise. The `javac` facade is
strictly namespaced because it must not steal current or future `javac` options.

Planned direct commands:

```sh
javan install
javan jdk install
javan jdk use 25
javan jdk use temurin@25
javan jdk list
javan jdk resolve
javan doctor

javan check target/classes
javan build target/classes --main com.acme.Main -t linux/amd64
javan build target/classes --main com.acme.Main -t darwin/arm64 -t windows/amd64
```

The generated `javac` facade accepts normal compiler arguments unchanged plus readable
Javan-specific extensions. Normal use needs no extra flag because compatibility reporting
is the default; the extensions are for changing that default or creating a package.

```sh
javac -d target/classes src/main/java/com/acme/Main.java
javac --jn-build --jn-main com.acme.Main --jn-out dist/acme \
  --jn-target linux/amd64 -d target/classes src/main/java/com/acme/Main.java
```

The facade removes only `--jn-*` options before starting the real compiler. `-jn-*` is an
equivalent compact alias for terminal use. The double-dash spelling is canonical in project
configuration, Maven, Gradle, IDE compiler arguments, logs, and support requests. Bare
`jn-*` tokens are never Javan options because they can be valid source or ordinary `javac`
arguments. There is no `-Xjavan:*` public alias.

### Reused Javac Arguments

Javan consumes existing `javac` arguments as shared facts while forwarding them unchanged.
It must not add a second spelling for a value that `javac` already owns:

| Javac argument | Javan use | Must not mean |
| --- | --- | --- |
| `-d <classes>` | Fresh class-output root to analyze after successful compilation. | Native package output. |
| `-classpath`, `-cp`, `--class-path` | Resolved dependency input and dependency/license reporting. | A second Javan-only classpath. |
| `--module-path`, `--upgrade-module-path`, `--module-source-path`, `--module` | Module-aware class discovery and reporting. | Native OS/architecture target. |
| `--release <feature>` | Java API/language compatibility context and minimum compiler capability. | The selected JDK vendor/version or native target. |
| `-sourcepath`, `--source-path`, source-file arguments | Source mapping for reports and diagnostics. | A substitute for compiled class output. |
| `-g`, `-parameters`, `--enable-preview` | Debug/source-map and bytecode interpretation context. | Native build configuration. |

For example, this has one class-output directory and one classpath, shared by both tools:

```sh
javac --release 25 -d target/classes --class-path 'lib/*' \
  --jn-build --jn-main com.acme.Main --jn-target linux/amd64 \
  src/main/java/com/acme/Main.java
```

`--jn-*` exists only where `javac` has no equivalent: native mode, native OS/architecture,
native entrypoint, package output, report destination/presentation, and native stacktrace.
Javan must reject contradictory input instead of guessing. In particular, `--jn-build`
requires a fresh, identifiable class output; it must not scan old `.class` files beside
sources and claim that they came from the current compile.

The initial extension set is deliberately small:

```text
--jn-off                         disable all Javan post-compile work and reports
--jn-warn                        print Javan findings after javac succeeds
--jn-strict                      fail after javac succeeds on native blockers
--jn-build                       create a native package after javac succeeds
--jn-target <os>/<arch>          repeat to build multiple packages
--jn-main <class>
--jn-out <name>
--jn-diag <auto|compiler|pretty|jsonl>
```

`--jn-end` stops Javan option parsing and passes every following token through unchanged.

`report` is the default native mode. It writes a compatibility report after successful
compilation and never changes `javac` success or failure. `build` includes the report, so
`report` and `build` are not combinable modes. Native package creation remains explicit:
an ordinary Java compile must not unexpectedly take longer, require a main class, or write
platform packages. `off`, `report`, `warn`, `strict`, and `build` are the complete modes.
`strict` may fail only after a successful Java compile and only when explicitly selected.

`host` is the default target. Native targets use Go-style `<os>/<arch>` values:
`linux/amd64`, `linux/arm64`, `darwin/arm64`, and `windows/amd64`. The current native
backend accepts one host-target assertion only; repeating `--jn-target` and cross-linking are
explicitly rejected until real target toolchains exist. The wrapper uses `--jn-target ...`;
the direct CLI uses `--target`. `macos` is accepted as a friendly alias for canonical
`darwin`. `--release` remains exclusively a real `javac` language/API target.

The wrapper flow:

1. Resolve the selected original JDK.
2. Stream the original `javac` or `java` output unchanged, append only documented Javan
   output afterwards, and return its exact exit code unless an explicit future strict mode
   requests otherwise.
3. On a successful compile, use an explicit `-d <classes>` output without rebuilding.
4. Run Javan's analysis directly over those class files when native mode is not `off`.
5. Write source-focused reports even when compilation or analysis is incomplete.
6. Run native packaging only for `build`.

Current implementation: `javan javac` writes `javac-invocation.json` and
`javac-invocation.md` by default. A successful compile with `-d <classes>` runs the current
Javan check pipeline against exactly that directory and writes the normal compatibility
reports beside it. A failed compile, or a compile without `-d`, writes an invocation report
with `analysis: not-run` or `analysis: unavailable`; it never guesses a class directory. A
declared `-d` directory may be incremental and retain older class files, so current reports
mark output freshness as unverified rather than claiming a clean compile. `--jn-off` is the
explicit opt-out. `--jn-warn` prints findings without changing a successful javac exit code;
`--jn-strict` returns `2` only after javac succeeds and Javan reports blockers; `--jn-build`
builds an app from the declared output without recompiling Java sources. `--jn-main`,
`--jn-out`, one host `--jn-target`, and `--jn-diag` are implemented for build/diagnostic
control. `--jn-reports` and `--jn-stacktrace` remain intentionally unsupported rather than
silently ignored.

`javan --version` reports Javan itself; `javan jdk resolve` reports the selected backend
and why it won. The generated facade preserves normal backend output first, then appends a
small deterministic Javan section for `--help` and `--version`.

### Help And Version Contract

Compatibility comes before branding. These commands must remain distinct:

| Invocation | Required result |
| --- | --- |
| `javan --help` | Javan CLI command help. |
| `javan --version` | Javan version only. |
| facade `javac --help` | Backend `javac` help followed by a compact Javan extensions section. |
| facade `javac --version` | Backend `javac` version followed by Javan facade version, selected backend path, and resolution source. |
| facade `java --help` | Backend `java` help followed by `java jdk list`, `java jdk use <25\|vendor@25>`, and `java jdk doctor`. |
| facade `java --version` | Backend `java` version followed by a compact Javan facade and management section. |

The Javan extensions section appears after backend help and documents the defaults, command
examples, report location, and the exact `--jn-*` options. The legacy `javan javac` command
remains compatible but is intentionally not advertised; the installed facade is the public
entrypoint. Javan-specific options are implemented only with their real behavior; until then
they fail as unsupported rather than being silently ignored.

`javan install` creates one public JDK home and one private facade store. The public home
always targets the store's `current` facade, so `java jdk use 25` changes the active JDK
without changing `JAVA_HOME` or touching the vendor JDK. The facade links every JDK-root
entry and every `bin` entry except `java` and `javac`; vendor-specific tools, including
`javaw`, therefore remain direct backend tools without a Javan-maintained file list. On Unix,
`java`, `javac`, and `javan` are native Javan launcher copies. On Windows, `java.exe`,
`javac.exe`, and `javan.exe` are native Javan executables and the switchable directories are
junctions, not `.cmd` files. The installer attempts machine, user, then temporary locations without
elevation; it does not alter shell profiles, PATH, `JAVA_HOME`, or an existing JDK. Maven,
Gradle, and IntelliJ end-to-end fixture proof remains open.

## Toolchain Resolution

Resolution order should be deterministic and local-first:

1. explicit Javan CLI selection
2. exact project lock
3. project `javan.toml`
4. JDK selected by the active build or IDE process when verifiable
5. `JAVA_HOME`, `JDK_HOME`, and the JDK running Javan
6. `javac` found on `PATH`
7. platform JDK discovery: macOS `java_home`, Linux alternatives and `/usr/lib/jvm`,
   Windows Java registry plus standard vendor installation directories
8. already managed Javan JDKs
9. managed download

Every candidate must contain a usable `javac` and match the requested OS, architecture,
and version constraints. A `PATH` launcher may be used for compiler delegation when its
real JDK home cannot yet be derived, but it must be marked as such and is never SDK-facade
eligible. Record selected and rejected candidates with their reasons.

Platform discovery supplements, but never overrides, the explicit/project/environment
precedence above. It should inspect only known roots and verify each candidate from its
`release` metadata, not infer a version from a directory name:

- macOS: `/usr/libexec/java_home -V`, `~/Library/Java/JavaVirtualMachines`,
  `/Library/Java/JavaVirtualMachines`, and known Homebrew OpenJDK layouts.
- Linux: `/usr/lib/jvm`, `/usr/java`, `~/.sdkman/candidates/java`, and `~/.jdks`.
- Windows: JavaSoft registry entries plus standard Java, Eclipse Adoptium, Amazon Corretto,
  Microsoft, and Zulu installation roots under Program Files.
- IDE runtimes are candidates only when they contain a real `javac`; a JRE-only runtime is
  rejected clearly.

The current implementation scans the listed filesystem roots and known JDK bundle layouts
for `release`, `bin/java`, and `bin/javac`; it has focused macOS, Linux, and Windows
fixtures. It does not yet invoke macOS `java_home`, inspect Linux alternatives, read the
Windows registry, or consume build/IDE-specific configuration. Those sources remain open
and must retain this precedence when added.

Build-tool inputs must be adapter-based and read-only: Maven toolchains and configured
compiler JDKs, Gradle `org.gradle.java.home` and declared toolchains, then current process
environment. Do not execute an arbitrary project build or grep source/build scripts as a
discovery shortcut. Locks and project configuration decide required version/vendor; JDK
metadata decides whether a discovered installation actually satisfies it.

When no suitable local JDK exists, the default interactive policy prompts to install
Temurin latest LTS. Non-interactive execution must not prompt: it either follows the
explicit `always` policy or fails with a deterministic install command. Downloads never
mutate `JAVA_HOME`, shell profiles, or project build files.

## Deterministic Downloads

Every downloadable artifact must be described by metadata:

- logical name
- version
- vendor or source
- OS and architecture
- URL
- SHA-256 checksum
- optional signature metadata
- license pointer
- extraction path

Install verification:

1. Download to a temporary file.
2. Verify checksum before extraction.
3. Extract to a content-addressed or versioned directory.
4. Write `toolchain.toml`.
5. Atomically mark the install usable.

Partial installs should be ignored and cleanly removable.

## Maven Plugin

Planned coordinates:

```xml
<plugin>
  <groupId>dev.javan</groupId>
  <artifactId>javan-maven-plugin</artifactId>
  <version>${javan.version}</version>
</plugin>
```

Planned goals:

- `javan:check`
- `javan:build`
- `javan:run`
- `javan:test`
- `javan:toolchain-doctor`

Expected behavior:

- run after Maven has compiled classes, unless configured to invoke compilation
- respect Maven toolchains and project wrappers
- reuse the same report files as the CLI
- fail the build on errors
- optionally fail on warnings through `warningsAsErrors`
- never hide the original Maven compiler diagnostics

## Gradle Plugin

Planned plugin id:

```kotlin
plugins {
    id("dev.javan") version "<version>"
}
```

Planned tasks:

- `javanCheck`
- `javanBuild`
- `javanRun`
- `javanTest`
- `javanToolchainDoctor`

Expected behavior:

- consume Gradle Java source sets and compiled class directories
- prefer the project `gradlew` wrapper
- support configuration cache where possible
- write deterministic task inputs and outputs
- keep all diagnostics in the same CLI report format
- avoid replacing Gradle dependency resolution

## Direct Dependency Management

Javan may eventually provide a direct project workflow, but it must not become a second,
incompatible Maven or Gradle resolver first. The wrapper already works with the classpath
supplied by either build tool; no plugin or migration is required.

The later direct workflow should be intentionally small:

```sh
javan init com.acme.app
javan deps add org.slf4j:slf4j-api@2.0.17
javan deps add --test org.junit.jupiter:junit-jupiter@6.0.3
javan deps remove --test org.junit.jupiter:junit-jupiter
javan deps sync
javan test
javan build .
```

`javan.toml` declares direct Maven coordinates in two explicit graphs: `main` and `test`.
`javan deps add` defaults to `main`; `--test` is required for a test-only dependency. The
source convention is equally explicit:

```text
src/main/java/        production sources
src/main/resources/   production resources
src/test/java/        test sources
src/test/resources/   test resources
```

```toml
[dependencies.main]
"org.slf4j:slf4j-api" = "2.0.17"

[dependencies.test]
"org.junit.jupiter:junit-jupiter" = "6.0.3"
```

The `main` graph compiles production code and is the only dependency graph eligible for a
production native package. The `test` graph is additive: test compilation and `javan test`
receive `main + test`, while `javan build` receives `main` only. Test classes, resources,
and test-only dependencies must never appear in a production package, production
compatibility count, runtime dependency report, or generated bindings.

`javan.lock` records both complete resolved graphs, repository URL, SHA-256 checksums,
licenses, target, and JDK. Every declared and resolved dependency report entry includes its
scope, direct/transitive origin, and whether it reached production or test code. The initial
contract supports pinned, ordinary JAR dependencies from explicit repositories. It does not
pretend to reproduce every Maven or Gradle feature such as arbitrary plugins, profiles,
BOM semantics, generated sources, classifiers, or custom repository authentication.

This adopts useful general properties--one command, a manifest, a lock, cached artifacts,
and reproducible offline builds--without copying another ecosystem's terms or semantics.
Maven and Gradle remain first-class inputs; `javan import maven` or `javan import gradle`
may later produce a reviewed starter manifest, never silently replace the original build.
Imports map unambiguous test configurations to `test` and production configurations to
`main`; unsupported or ambiguous scopes stay visible in the import report rather than being
silently flattened into production.

The resolver must produce a dependency and license report before creating a native package.
It must preserve upstream notices, distinguish declared from resolved dependencies, flag
unknown licenses and policy conflicts, and never download from an undeclared repository.
Provider adapters and bundled JDK options require a review of their current distribution
terms, license obligations, trademark use, checksum source, and redistribution rights before
release. Display vendor names as provenance, never as Javan product branding.

## Homebrew Integration

Planned distribution:

```sh
brew tap javan-dev/javan
brew install javan
```

Homebrew should install the `javan` executable and shell completions only. JDKs and
compiler toolchains remain managed by `javan jdk install` or existing system tools,
unless a separate formula explicitly documents bundled contents.

Formula requirements:

- pinned release URL
- SHA-256 checksum
- bottle checksums per platform
- `javan --version` smoke test
- `javan doctor` smoke test when network access is not required

## JetBrains Plugin

A JetBrains plugin is optional. Build it only if CLI reports, Maven plugin, and Gradle
plugin do not give enough feedback inside IntelliJ IDEA.

Potential responsibilities:

- detect `javan.toml` and selected toolchain
- show unsupported native-profile APIs from report files
- show safety warnings and source-focused diagnostics
- provide run configurations for `javan check`, `javan build`, and `javan run`
- register the wrapper SDK if the JDK-like layout exists

Non-responsibilities:

- no custom Java parser
- no second bytecode verifier
- no independent native compiler path

## IDE Report Contract

Reports intended for IDEs must be stable and machine-readable:

- `.javan/reports/report.json`
- `.javan/reports/report.md`
- `.javan/reports/project.json`
- `.javan/reports/safety-warnings.json`
- `.javan/reports/exceptions.json`
- `.javan/reports/debug-map.json`
- `.javan/reports/intrinsics.json`
- `.javan/reports/compatibility-summary.json`
- `.javan/reports/threads.json`

The IDE layer should render reports; it should not infer unsupported native behavior on
its own. One source of truth keeps the knives facing away from the user.

The unified report should also carry Sonar-like findings for reachable code where Javan
can prove or conservatively suspect a problem:

- correctness bugs
- possible runtime failures
- security and unsafe API usage
- performance traps
- concurrency misuse
- maintainability smells when they affect native readiness
- dead or unreachable dependency usage
- license and dependency policy findings

Every finding uses the same diagnostic id, severity, source location, reachable path, why,
and fix model as compiler/native-profile diagnostics. IDE integrations and build plugins
must not run a second analyzer with different semantics.

The javac wrapper emits one compiler-compatible anchor line so terminals, CI systems, and
IDEs can link a finding without a custom plugin. IntelliJ external-tool filters recognise
an absolute path with line and column numbers on the same line, so that form is mandatory
when a real source location is known. [JetBrains documents this contract](https://www.jetbrains.com/help/idea/settings-tools-external-tools.html?q=rebase+m).

```text
/absolute/project/src/main/java/com/acme/Main.java:42:13: warning: [JAVAN-NAT-101] reflective lookup is not translatable
```

On an interactive TTY, Javan follows that anchor with a compact ASCII detail card. It
uses colour only when supported, honours `NO_COLOR`, and never relies on colour for
meaning. Non-interactive output defaults to the single anchor line; `--jn-diag`
selects `auto`, `compiler`, `pretty`, or `jsonl` explicitly.

```text
/absolute/project/src/main/java/com/acme/Main.java:42:13: warning: [JAVAN-NAT-101] reflective lookup is not translatable
+-- JAVAN-NAT-101 ----------------------------------------------------------+
| Native output needs a statically known lookup target.                     |
| at: /absolute/project/src/main/java/com/acme/Main.java:42:13             |
| help: replace dynamic lookup with an explicit registry                    |
+---------------------------------------------------------------------------+
```

The canonical JSON diagnostic has a schema version, immutable code, severity, concise
message, optional real source location, details, help, related locations, and category.
Never fabricate a location or rewrite/localise the underlying `javac` diagnostic. Runtime
exceptions use the same model: a concise mapped failure is shown by default and the raw
native stack trace is available through `--jn-stacktrace` and the report.

Those diagnostics must still come from the same report model. The wrapper delegates to
the original `javac` first and preserves normal Java compiler diagnostics and exit codes.

The target unified invocation model atomically writes `.javan/reports/run.json`, including
toolchain source, compile result, native result, analysis completeness, compatibility counts,
diagnostics, and report paths. The current javac facade writes
`javac-invocation.json` and `javac-invocation.md` first. A failed compile is reported as
`analysis: not-run`; Javan must not inspect stale class files or claim a native result. A
partial analysis is reported as `unavailable` or `failed`, not as supported or rejected.

Compatibility summaries for IDEs must distinguish:

- JDK inventory counts
- supported reachable variants
- deliberately rejected reachable variants
- unknown leftovers

IDE integrations must never infer native support from inventory counts alone.

## Feature Incubator Workflow

Large features should be developed in isolated temporary folders or agent-owned work
areas before migration into the main compiler.

Recommended flow:

1. Create a feature folder under `.javan/tmp/features/<feature-id>/` or an agent worktree.
2. Build the smallest public-entrypoint probe for one assumption.
3. Add one test per assumption, scenario, or failure mode.
4. Prove the probe against JVM behavior when applicable.
5. Migrate the minimal production code into `src/main/java`.
6. Migrate tests into the normal test suite.
7. Run focused tests, then `mvn verify`, then native smoke tests.
8. Delete temporary generated code or document why it remains.

No feature should be accepted because a broad demo passed. Broad demos are useful, but
the gate is many narrow tests with clear assumptions.

## Test Strategy

Distribution and toolchain tests should remain public-entrypoint oriented:

- CLI tests for install, use, doctor, resolution, and missing-tool failures
- facade tests proving normal `javac` argument order, backend stdout/stderr ordering,
  documented Javan output, and exit-code preservation
- JDK-discovery fixtures for environment, PATH, macOS, Linux, and Windows candidates,
  including proof that no network download happens when a suitable local JDK exists
- report tests for compile success, compile failure, complete analysis, partial analysis,
  and unavailable class output detection
- terminal snapshot tests for `compiler`, `pretty`, `jsonl`, `NO_COLOR`, and source-link
  diagnostic modes
- test projects for plain Java, Maven, Gradle, wrapper Maven, and wrapper Gradle
- checksum rejection tests
- corrupt download and partial extraction tests
- global settings precedence tests
- project lock precedence tests
- no-network deterministic replay tests
- plugin smoke tests against real Maven and Gradle where available
- Homebrew formula smoke tests where Homebrew is available
- Docker Linux matrix for supported Linux architectures where runners are available

Each test should check one assumption. If a test name needs "and", split the test. The
build system has enough jobs; no need to make one test carry furniture.

## Phases

### Phase 1: Resolution Contract And Local Discovery

- define `toolchain.lock.json`, global settings, and JDK candidate metadata
- implement deterministic local-first resolution and `javan jdk resolve`
- add `javan doctor` / `javan jdk doctor` read-only inspection
- prove no-download selection of valid system, build, PATH, and platform JDKs
- add deterministic errors for missing JDK, linker, target, and policy prerequisites

### Phase 2: Managed JDK Store

- implement and prove machine-wide -> user -> temporary storage preparation without elevation
- expose the read-only placement policy through `javan jdk doctor`
- retain `javan install` / `javan jdk install` default Temurin 25 download from the official checksummed catalog
- retain checksum verification and atomic install directories
- extend `javan jdk list` with system/managed provenance and selection reasons
- implement `javan jdk remove`
- implement no-network lock replay

### Phase 3: Transparent JDK Facade

- implement `javan javac` and the `--jn-*` / `-jn-*` parser
- stream original compiler output without reordering or rewriting it
- prove ordinary Java compilation behaves exactly like the selected original JDK
- make facade `java` and `javac` use Javan launchers on every supported platform while leaving backend `javaw` direct
- dynamically preserve each selected JDK's whole layout while switching only the stable facade link
- prove `java jdk list` and `java jdk use 25` through a packaged facade
- keep a public compile-once -> native-build proof for `--jn-build`, including strict failure
  after successful javac compilation

### Phase 4: Reports And Diagnostics

- implement fresh class-output discovery and direct analysis without another build
- write atomic invocation reports for success, failure, and partial analysis
- introduce canonical diagnostic JSON plus compiler and interactive ASCII renderers
- preserve raw `javac` diagnostics and add mapped runtime exception presentation

### Phase 5: Native Modes And Targets

- retain the implemented `off`, `report`, `warn`, `strict`, and `build` native modes
- implement real repeated cross-target selection and linking beyond the current one-host-target guard
- fail clearly when a real target linker, runtime, or native dependency is unavailable
- prove executable and native-library package output for each supported target

### Phase 6: JDK-Shaped Facade

- retain the Unix linked facade and native Windows `java.exe` / `javac.exe` launcher plus junction layout
- prove shell, Maven, Gradle, and IntelliJ use it without proprietary integration
- retain the backend JDK's standard `java`, `javac`, and standard-library behavior

### Phase 7: Optional Integrations

- release Maven and Gradle convenience plugins that call the same facade and report API
- add Corretto as the second managed JDK provider
- build a JetBrains plugin only for feedback gaps the facade and report links cannot solve

### Phase 8: Direct Workflow And Distribution

- deliver the narrow `javan init`, `javan deps add`, `javan deps sync`, and `javan build .`
  workflow with locked, checksummed main and test JAR dependency graphs
- prove `javan test` can use test-only dependencies while production builds and packages
  exclude them completely
- publish checksummed archives and Homebrew formula
- add release verification, upgrade, rollback, and offline-build documentation

## Non-Goals

- Reimplementing the JDK.
- Replacing `javac`.
- Shipping fake platform classes to trick IDEs.
- Silently changing `JAVA_HOME`, shell profiles, or project build files.
- Downloading global tools during `javan build` without explicit policy.
- Hiding Maven, Gradle, or `javac` diagnostics.
- Claiming full JDK API native support because the wrapper JDK can compile code.
- Making a JetBrains plugin mandatory for normal use.
- Solving licensing by vague bundling. Every bundled tool needs explicit provenance.
- Copying another tool's command names or promising source/build compatibility with it.
- Resolving dependencies from undeclared repositories or omitting required notices.

## Decisions

- Managed installs default to Eclipse Temurin latest LTS; Amazon Corretto follows after the
  primary provider is proven end-to-end.
- A valid local JDK always beats a download. When Javan needs its default JDK, it installs
  verified Temurin 25 without changing existing JDK configuration.
- The base `javan` executable and generated JDK facade are separate distribution forms but
  share one resolver, compiler wrapper, report format, and native pipeline.
- Homebrew manages Javan only. JDKs remain existing local installations or Javan-managed
  downloads with recorded provenance.
- Maven and Gradle stay delegated until the narrow direct dependency contract is proven.
