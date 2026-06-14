# Dependency And License Reports

Status: roadmap only. This file describes required behavior and does not claim current
implementation.

## Goal

Javan should resolve dependencies with a calm Go-like workflow while keeping Java's
important production/test separation. It should also report which dependencies, packages,
classes, and licenses are actually used by reachable native code.

## Module Files

Planned files:

- `javan.mod`: direct project dependencies, repositories, source dependencies, and policies
- `javan.lock`: exact resolved artifacts, source revisions, checksums, licenses, and scopes

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
2. local Maven cache (`~/.m2/repository`)
3. configured Maven/Ivy repositories
4. Maven Central when enabled
5. GitHub Packages when configured
6. Git source dependencies pinned by tag, commit, or signed release archive
7. other authenticated mirrors declared in global or project settings

The lock file must record which resolver produced each dependency and the checksum or
source revision used. Normal verification must be able to run without network access once
the lock/cache is populated.

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

## IDE And CI Contract

The dependency and license findings must feed the same unified diagnostics/report model as
native-profile checks. IDEs should consume stable JSON and may also receive javac-style
diagnostics through the wrapper.

## Acceptance Examples

- main dependency used by reachable production code appears as `main used`
- declared dependency used only from `src/test/java` appears as `test used`
- declared dependency never reached appears as `unused`
- transitive dependency reached through a main dependency appears as `main transitive used`
- dependency with unknown license produces a warning unless policy blocks unknown licenses
- dependency with blocked license fails the build when policy says blocked
- local Maven cache is used without network access
- GitHub source dependency is pinned and checksum-verified
- authenticated mirror works without leaking credentials into reports or lock files
