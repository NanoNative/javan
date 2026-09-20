# Dependency And License Reports

## Human Review

Owns dependency resolution, locks, resource provenance and license evidence.
The implemented and proposed sections below remain distinct. License evidence is
not a legal-compatibility guarantee; missing facts must remain unknown.

Before another repository/provider, decide authentication, declared inputs, checksum/lock
semantics and credential redaction. Wider repository support is not implied by local
Maven resolution or bundled external probes.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| DEP-001 | Resolved dependencies MUST retain deterministic identity, source and integrity evidence; invalid declared paths or locks MUST fail explicitly. | [Dependency CLI](../../src/test/java/javan/CliDependencyProjectIntegrationTest.java), module/lock behavior below. |
| DEP-002 | Dependency and license reports MUST distinguish declared, resolved, reachable and unknown evidence without exposing credentials. | Current report fields and [acceptance examples](#acceptance-examples); broader provider/license inference remains proposed. |
| DEP-003 | External smoke artifacts MUST NOT define compiler-specific support rules. | [External isolation tests](../../src/test/java/javan/ExternalProbeIsolationTest.java), [example ownership](examples-and-test-projects.md). |

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
When both rules name the same identifier, `deny` wins. Unmatched known licenses retain
`known`; missing metadata keeps identity `unknown` and policy `warning`. License reports retain the dependency identity,
scope, origin, usage, detected license/source/path, policy result and matching declaration line.
Policy never replaces the artifact's detected identity.

## Planned Extensions

The following are not current resolver or report guarantees. Each needs a concrete consumer
and its own public acceptance before implementation.

### Network Resolution And Authentication

Extend the existing local resolver rather than creating another dependency path. Candidate
sources are configured Maven/Ivy repositories, Maven Central, GitHub Packages, pinned Git
sources and authenticated mirrors. Provider order, authentication and retry/failure semantics
must be agreed before adding a source.

Repository aliases belong in project configuration; credentials belong in explicit user-global
settings, environment references, system credential helpers or CI secrets. Reports must redact
secrets and lock files must never retain them. Downloaded artifacts need pinned identity and
verified checksums before entering the existing cache.

Profile activation, classifiers, non-version dependency-management fields, remote repositories
and source revisions remain future schema work. Exact license rules above are already implemented.

### Wider Usage Analysis

Production, test and tool scopes must remain separate. Current app reachability does not
establish test-only usage. Planned additions include production-versus-test reachability,
package-level usage and unsupported-dependency classification. Method-level attribution is
deferred; it is not a first-release gate.

## IDE And CI Contract

Dependency and license findings use the same unified diagnostics/report model as native
checks. IDEs consume stable JSON and may receive javac-style diagnostics through the facade;
they do not infer support or license compatibility independently.

## Acceptance Examples

The current cases are exercised through [dependency CLI tests](../../src/test/java/javan/CliDependencyProjectIntegrationTest.java).
Planned cases below are requirements for their future slice, not passing evidence.

| Scenario | Expected result | Status |
| --- | --- | --- |
| Reachable main dependency, including a transitive dependency | Correct scope, origin and used classes in reports. | Current |
| Declared dependency not reached by the app | Unused app dependency; do not infer test usage. | Current |
| Missing license metadata | Unknown identity and advisory warning, not a guessed license. | Current |
| Exact denied license | Advisory `JAVAN181`, blocked report row and successful analysis; an allow rule cannot override the same deny. | Current |
| Cached local Maven closure without its original repository | Verified replay without network access; reject checksum or locked-metadata drift. | Current |
| Dependency reachable only from tests | `test used` without entering production reachability. | Planned |
| Git source dependency | Pinned revision and checksum evidence. | Planned |
| Authenticated mirror | Verified artifact without credentials in reports or locks. | Planned |
