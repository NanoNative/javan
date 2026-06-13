# Feature Lab Workflow

Status: process policy. This file describes how larger javan features should be built
before they are migrated into the main compiler, runtime, and CLI.

## Goal

Large features may be explored independently in temporary feature labs, agent work areas, or
ignored subfolders. The main project only receives the reviewed, minimal production slice.

The workflow keeps parallel work useful without letting experimental code leak into `javan`.
Durable standalone tracks can move to separate repositories or folders with their own context
files; see [independent-workspaces.md](independent-workspaces.md).

## Lab Locations

Allowed locations:

- `/tmp/javan-lab-*`
- `.javan/labs/<feature-name>` for ignored local experiments
- agent worktrees or agent-owned temporary directories
- external spike branches such as `feature/<name>-spike`

The production source tree remains the integration target. A lab is not a second product.

## Slice Shape

Each feature starts as a small, testable slice with one narrow promise.

Good slices:

- one intrinsic overload
- one bytecode shape
- one CLI flag
- one report file
- one ABI signature family
- one deterministic diagnostic case

Bad slices:

- "support collections"
- "make exceptions good"
- "optimize strings"
- "finish the JDK"

Each slice must define:

- the exact accepted inputs
- the exact rejected inputs
- the observable output
- the report output, if any
- the public entrypoint used for tests
- the unsupported cases that must fail clearly

## Test Rule

Every test checks exactly one assumption, scenario, or case.

Do not put unrelated assertions into one test just because setup is expensive. Shared setup is
allowed; shared assumptions are not. If a test failure cannot name the broken promise from the
test name alone, split the test.

Required coverage shape:

- success path tests
- negative tests for unsupported input
- deterministic fixture tests
- public-entrypoint tests for CLI/build/runtime behavior
- regression tests for every bug fix
- report-content tests when reports are part of the feature

The target remains:

- line coverage around or above 95%
- branch coverage around or above 90%
- every reachable behavior either tested or explicitly documented as intentionally untested

Coverage is not permission to merge vague behavior. It is only the tripwire.

## Public Entrypoints

Prefer tests through the same boundary a user exercises:

- `javan check`
- `javan build`
- `javan run`
- `javan test`
- generated native executable
- generated library plus C/Rust/Go/Python binding smoke tests where available

Private helper tests are allowed only when the public boundary cannot isolate one assumption
without making the test obscure or fragile.

## Determinism

Feature labs must make fixture behavior repeatable:

- fixed source files
- fixed classpath order
- fixed random seeds
- injected clocks when time affects output
- stable report ordering
- stable diagnostic ids
- no network dependency during normal verification
- no host-specific absolute paths in expected outputs unless normalized

When testing multiple JDKs, OSes, or architectures, use explicit matrices. Docker is acceptable
for Linux matrix checks when it makes failures reproducible. Cross-OS results must be reported as
environment evidence, not implied by one host run.

## Agent Workflow

Agents may experiment independently, but integration stays centralized.

Expected agent handoff:

- changed files
- implemented slice
- tests added
- verification commands and results
- known unsupported cases
- any assumptions made

The integrator must review the actual diff, run local verification, and migrate only the useful
parts. Do not accept agent work because the summary sounds confident. Summaries are appetizers,
not evidence.

## Migration Rules

Before moving a lab feature into `javan`:

- reduce the implementation to the smallest production slice
- remove lab-only scaffolding
- keep deterministic fixtures that prove the slice
- add negative tests before claiming unsupported behavior is safe
- update reports/docs only for behavior that exists
- keep unsupported paths explicit and readable
- preserve concurrent edits and adapt to the current tree

No direct merge of spike feature code is allowed without review. Spike code may inform the final
patch; it does not become the final patch by gravity.

## Acceptance Checklist

A feature slice is acceptable when all items are true:

- The slice has one clear promise.
- Accepted inputs are documented or visible in tests.
- Rejected inputs fail clearly.
- Every test checks one assumption, scenario, or case.
- Public-entrypoint tests cover user-visible behavior.
- Negative tests cover unsupported reachable behavior.
- Reports are deterministic and tested when generated.
- Coverage gates remain at or above project policy.
- `mvn verify` passes.
- Native smoke tests run when native behavior changed.
- Optional language/toolchain smoke tests run when bindings or plugins changed and the tool is available.
- Cross-architecture or cross-OS claims are backed by matrix evidence.
- Docs do not claim future behavior as current behavior.
- Agent or lab code was reviewed before migration.
- No unrelated refactor is bundled into the slice.

## Default Milestone Loop

For every next milestone:

1. Define the smallest slice.
2. Explore in a lab or agent work area if useful.
3. Write or migrate one-assumption tests.
4. Implement the production slice.
5. Verify locally through public entrypoints.
6. Update docs and reports only for shipped behavior.
7. Record unsupported cases for the next slice.

This loop is intentionally boring. Boring is how native compilers avoid becoming folklore.
