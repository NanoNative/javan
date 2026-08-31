# Dependency And License Reports

Status: implemented slice plus roadmap. Javan now writes classpath dependency and
license reports from resolved `--classpath`, Maven, and Gradle runtime classpaths during
reachability-backed `check`, `build`, and `compat` flows. Javan also reads local
`javan.mod` path dependencies, resolves direct and compile/runtime-transitive coordinates
from a configured local Maven repository or `~/.m2/repository`, stores verified artifacts
and POM metadata in the global Javan cache, and writes deterministic `javan.lock`. Network
resolution, authenticated mirrors, and full test reachability remain roadmap work.

## Current Implementation

Generated today:

- `.javan/reports/dependencies.json`
- `.javan/reports/dependencies.md`
- `.javan/reports/licenses.json`
- `.javan/reports/licenses.md`

Current dependency rows are based on the resolved classpath Javan actually scans. Each row
includes path, classpath index, kind, scope, direct/transitive origin, requesting coordinate,
present/missing status, class count, reachable dependency class count, reachable classes,
Maven coordinate, source (`classpath` or `javan.mod`), and used/unused classification.

Current `javan.mod` syntax:

```text
module com.acme.app
java 25

require main libs/runtime.jar
require main com.acme:math:1.2.3
require test libs/test-support.jar
require tool tools/codegen.jar
license allow "Apache License, Version 2.0"
license deny "GNU General Public License, version 3"
```

Rules today:

- `main` local jar/classes dependencies are added before plain `javac` compilation.
- direct `group:artifact:version` and `group:artifact version` coordinates are resolved
  from `-Djavan.maven.localRepository`, `-Dmaven.repo.local`, then `~/.m2/repository`.
- sibling local POMs resolve compile/runtime transitives breadth-first with direct dependencies
  taking precedence; optional, test, provided, and system dependencies stay out.
- local POM properties, parent inheritance, imported BOM and dependency-management versions,
  and exclusions are honored; missing or cyclic parent/BOM metadata fails before compilation.
- successful coordinate resolution copies the complete used JAR/POM closure to
  `$JAVAN_HOME/cache/dependencies` (normally `~/.javan/cache/dependencies`); later builds replay
  from that cache without the original Maven repository.
- every cached file has SHA-256 metadata. Corrupt cache content fails before compilation;
  an interrupted entry without checksum metadata is repaired only while its source remains available.
- `test` and `tool` local dependencies are recorded in `javan.lock` but not added to
  native app classpath.
- missing local declarations fail clearly.
- missing local Maven-cache coordinates fail clearly after writing lock metadata.
- `javan.lock` version 2 records scope, notation, direct/transitive origin, requesting
  coordinate, status, artifact kind, path, relative path, size,
  SHA-256 content checksum, local repository origin, and detected license name, URL, source,
  and path. Existing FNV64 or checksum-only locks upgrade automatically on their next verified use.
- unchanged declarations verify their locked content checksum before compilation; changed
  module or dependency declarations regenerate the lock deterministically.
- unchanged declarations also reject dependency-graph, repository, or license metadata drift
  without rewriting the lock. Version 1 locks verify their direct artifacts before upgrading.
- jar extraction is content-addressed by SHA-256 and shared by lock, scan, and report generation.

Current license rows inspect jar metadata first:

- `META-INF/maven/**/pom.xml` license name and URL
- sibling local-Maven `.pom` license name and URL
- `META-INF/LICENSE*`, `META-INF/NOTICE*`, `LICENSE*`, `NOTICE*`, `COPYING`
- directory-level `LICENSE`, `LICENSE.txt`, `LICENSE.md`, `NOTICE`, `COPYING`

License rules match only the exact identifier that artifact metadata reported. Javan never guesses
SPDX ids, parses license text, or gives legal advice. A matching `allow` rule is reported as
`allowed`; a matching `deny` rule is reported as advisory `blocked` and produces `JAVAN181` with
both the detected metadata source and the `javan.mod` line. A deny rule does not block a build.
Unknown licenses remain `unknown` and are reported as warnings.

## Goal

Javan should resolve dependencies with a calm Go-like workflow while keeping Java's
important production/test separation. It should also report which dependencies, packages,
classes, and licenses are actually used by reachable native code.

## Module Files

Module files:

- `javan.mod`: module identity, Java version, and direct main, test, or tool dependencies
- `javan.lock`: resolved direct and transitive artifacts, scopes, paths, sizes, and content checksums

Profile activation, classifiers, non-version dependency-management fields, remote repositories,
source revisions, and license policy remain future schema work.

Dependency scopes:

- `main`: production/runtime code reachable from `src/main/java`
- `test`: test-only code reachable from `src/test/java`
- `tool`: build or code-generation tools that must not enter native app reachability

Unlike Go modules, Javan must keep production and test dependency accounting separate.
Go has no Maven-style `compile` versus `test` dependency scopes in `go.mod`; test imports
can affect the module graph, but the user does not declare a separate test scope. Javan
should be more explicit because Java projects already separate main and test source roots.

## Resolvers

Resolver order should be deterministic and configurable:

1. local project paths
2. verified global Javan dependency cache
3. configured local Maven repositories (`-Djavan.maven.localRepository`, `-Dmaven.repo.local`,
   then `~/.m2/repository`)
4. configured Maven/Ivy repositories
5. Maven Central when enabled
6. GitHub Packages when configured
7. Git source dependencies pinned by tag, commit, or signed release archive
8. other authenticated mirrors declared in global or project settings

The lock file records which local cache produced each dependency and its checksum. Normal
verification runs without the original repository once the cache is populated.

## Authentication

Authentication should come from explicit settings, never from hard-coded credentials:

- project-local repository aliases without secrets
- user-global `~/.javan/settings.toml`
- environment variable references
- system credential helpers where available
- CI secret variables

Reports must redact secrets. Lock files must not contain credentials.

## Usage Analysis

After reachability, Javan should classify dependency usage:

- direct dependency used by production code
- direct dependency used only by tests
- transitive dependency used by production code
- transitive dependency used only by tests
- declared but unused dependency
- dependency present on classpath but unreachable
- dependency rejected because reachable code uses unsupported features

The analysis should report at class and package level first. Method-level dependency
usage is useful later, but the first release gate should not require perfect method-level
attribution.

## License Report

Generated reports:

- `.javan/reports/dependencies.json`
- `.javan/reports/dependencies.md`
- `.javan/reports/licenses.json`
- `.javan/reports/licenses.md`

Each dependency row should include:

- coordinate or source URL
- version, commit, or checksum
- scope (`main`, `test`, `tool`)
- direct or transitive
- used or unused
- reachable classes count
- license id and source of license detection
- license file path when bundled
- policy status: allowed, warning, or blocked

License detection should use artifact metadata first, then packaged license files, then
project policy overrides. Unknown licenses must be reported as unknown, not guessed.

Project policy uses quoted exact identifiers in `javan.mod`:

```text
license allow "Apache License, Version 2.0"
license deny "GNU General Public License, version 3"
```

When both rules name the same exact identifier, `deny` takes precedence. The report records the
matching `javan.mod:<line>` source. These labels are evidence for the project's own review, not a
legal determination or a build blocker.

## IDE And CI Contract

The dependency and license findings must feed the same unified diagnostics/report model as
native-profile checks. IDEs should consume stable JSON and may also receive javac-style
diagnostics through the wrapper.

## Acceptance Examples

- main dependency used by reachable production code appears as `main used`
- declared dependency used only from `src/test/java` appears as `test used`
- declared dependency never reached appears as `unused`
- transitive dependency reached through a main dependency appears as `main transitive used`
- dependency with unknown license remains an evidence-backed warning
- dependency with explicitly denied license produces an advisory warning and a blocked report row
- local Maven cache is used without network access
- GitHub source dependency is pinned and checksum-verified
- authenticated mirror works without leaking credentials into reports or lock files
