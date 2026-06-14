# Toolchain Distribution Roadmap

Status: roadmap only. The first release direction is now binary-first; see
[binary-first-distribution.md](binary-first-distribution.md). This file keeps longer-term
toolchain ideas that may be useful after the standalone executable, Maven/Gradle
plugins, Homebrew packaging, and IDE report consumption are stable.

## Goal

Make `javan` integrate cleanly with existing JDKs without pretending to be a replacement
JDK.

The production distribution should install a standalone `javan` binary, add native-build
awareness around normal Java outputs, and remain friendly to IDEs and existing Java build
tools. Users should be able to install `javan`, point it at a project or wire it through a
build plugin, and get:

- normal Java compilation through a real `javac`
- deterministic native-build checks and reports
- automatic toolchain discovery and installation only when needed and explicitly allowed
- Maven and Gradle integration
- stable distribution metadata with versions, checksums, and source provenance

## Principles

- Use real JDKs. Do not rebuild Java or ship fake platform classes.
- Keep Java source and `.class` output ordinary enough for existing tools.
- Prefer explicit installation over silent global mutation.
- Make every resolved toolchain deterministic: version, vendor, OS, architecture,
  download URL, checksum, and selected path.
- Keep project-local state separate from user-global state.
- Make unsupported native features visible early through `javan check` reports.
- Keep IDE integration as a reporting and configuration layer, not a second compiler.
- Treat plugins and installers as thin entrypoints into the same CLI behavior.

## Distribution Shape

Initial deliverables:

- `javan` standalone executable
- optional platform-specific archive with helper launchers
- checksummed release manifest
- installation metadata under the user's home `.javan` directory

Optional later deliverables:

- `javan-jdk` wrapper layout that an IDE can select as an SDK, only if binary reports and
  plugins are not enough
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

Example global settings:

```toml
[defaults]
jdk = "temurin-25.0.1+8"
target = "host"
profile = "core"

[downloads]
require_checksum = true
allow_prerelease = false

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

Planned commands:

```sh
javan toolchain install jdk 25
javan toolchain use jdk 25
javan toolchain doctor
javan javac --release 25 src/main/java/com/acme/Main.java
javan java -cp target/classes com.acme.Main
```

The wrapper flow:

1. Resolve the selected original JDK.
2. Delegate `javac` or `java` to the original binary.
3. Index emitted `.class` files.
4. Run `javan check` for native-profile compatibility when enabled.
5. Write source-focused reports for IDEs and build tools.

The wrapper JDK layout can later expose a normal-looking `bin/javac`, `bin/java`, and
`release` file so IDEs can register it as an SDK. Internally those launchers delegate to
the original JDK plus `javan` checks.

## Toolchain Resolution

Resolution order should be deterministic:

1. CLI flags
2. project `javan.toml`
3. project lock files
4. environment variables such as `JAVAN_HOME` or `JAVA_HOME`
5. user-global `~/.javan/settings.toml`
6. already installed global toolchains
7. explicit install command

`javan build` may suggest an install command when a required tool is missing. It should
not download or mutate global state unless the user ran an install command or enabled an
explicit auto-install policy.

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

## Homebrew Integration

Planned distribution:

```sh
brew tap javan-dev/javan
brew install javan
```

Homebrew should install the `javan` executable and shell completions only. JDKs and
compiler toolchains remain managed by `javan toolchain install` or existing system tools,
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

The javac wrapper may also emit javan diagnostics in source-focused compiler format so
IDEs that already parse `javac` output can display warnings without a custom plugin:

```text
src/main/java/com/acme/Main.java:42: warning: [JAVAN-RISK-NULL] possible null dereference
```

Those diagnostics must still come from the same report model. The wrapper delegates to
the original `javac` first and must preserve normal Java compiler diagnostics and exit
codes.

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

### Phase 1: Spec And Report Contracts

- define `toolchain.lock.json`
- define global `settings.toml`
- define report schema stability rules
- add `javan doctor` / `javan toolchain doctor` read-only inspection
- add `javan toolchain list` read-only local inventory
- add deterministic error messages for missing JDK/linker/plugin prerequisites

### Phase 2: Global Toolchain Store

- implement `javan toolchain install jdk`
- implement checksum verification
- implement atomic install directories
- extend `javan toolchain list` with installed/downloaded provenance
- implement `javan toolchain remove`
- implement no-network lock replay

### Phase 3: JDK Wrapper

- implement `javan javac`
- implement `javan java`
- implement wrapper `bin/javac` and `bin/java`
- emit IDE-friendly report files after compile
- prove that ordinary Java compilation still behaves like the original JDK

### Phase 4: Build Plugins

- release Maven plugin with `check` and `build`
- release Gradle plugin with `javanCheck` and `javanBuild`
- add test projects for both plugins
- document CI usage

### Phase 5: Package Managers

- publish checksummed archives
- publish Homebrew formula
- add release verification scripts
- add upgrade and rollback documentation

### Phase 6: IDE Integration

- evaluate whether existing report files plus plugins are enough
- build JetBrains plugin only for gaps that cannot be solved cleanly by CLI reports
- keep IDE diagnostics mapped to the same diagnostic IDs as the CLI

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

## Open Questions

- Which JDK vendors should be supported first?
- Should the default JDK install command pick latest LTS or project `java` version?
- Should `javan-jdk` be a separate artifact from the base CLI?
- Should Homebrew manage only `javan`, or also offer optional JDK bundle formulae?
- How much of Maven/Gradle dependency resolution should `javan.mod` import, and how much
  should stay delegated to those tools?
