# Verification History

This file records earlier evidence with its scope and source. It is not current CI status,
a release approval, or a claim that every Java program works. The
[roadmap](roadmap.md) owns progress; [testing](specs/testing.md) owns current commands.

## Release Foundation

Reviewed on 2026-09-20 against main `0bd68804` and the linked GitHub issues.

| Scope | Recorded evidence | Limit |
| --- | --- | --- |
| REL-SCOPE-01 | [Issue #103](https://github.com/NanoNative/javan/issues/103) closed; [release contract](specs/release.md#first-native-release-scope) names three targets. | Scope agreement does not itself certify an artifact. |
| REL-ARTIFACT-01 | [Issue #116](https://github.com/NanoNative/javan/issues/116) closed; [rehearsal implementation](../.github/scripts/rehearse-release-artifact.sh), [sidecar packaging](../.github/scripts/package-release-rehearsal.sh), [public script tests](../src/test/java/javan/ReleasePackagingSurfaceTest.java). | No linked completion comment on the issue; rerun for the chosen release candidate. |
| ABI-IMPORT-01 | [Maintainer completion record](https://github.com/NanoNative/javan/issues/117#issuecomment-5459205489), [PR #256](https://github.com/NanoNative/javan/pull/256), commit `28580d5`: extracted-package import proof on Linux x64, Linux ARM64 and macOS ARM64. | Primitive and borrowed-byte-array imports; not arbitrary JNI or object ABI support. |

The earlier release specification attributed platform-contract proof on Linux/macOS x64 and
ARM64 plus Windows x64, and package proof on the required release hosts, to Snapshot run
32689384718. It recorded Windows ARM64 platform proof as unavailable with its hosted JDK.
These are historical claims; current enabled rows and proof depths live in the workflow,
not this record.

## Self-Host Memory Proof (M13R)

The earlier memory contract marked M13R Done for Linux x64, Linux ARM64 and macOS ARM64;
the release contract linked [Snapshot run 32689384718](https://github.com/NanoNative/javan/actions/runs/32689384718).
It recorded package-backed self-host sanitizer proof with nonzero tracked allocations and GC
collections, zero final live heap/root residue, and no sanitizer failure signatures.

The reduced `platform-smoke` path reused the generated self-host C and repeated `--version`
plus a tiny build/check loop while retaining counter/residue assertions. macOS x64 and Windows
package rows were disabled and not covered by the release claim. This is historical evidence,
not general concurrent-memory certification or a pass for a new candidate. Current acceptance
belongs to the [memory specification](specs/memory-runtime-correctness.md#self-host-memory-proof).

## Static-Library Linkage

The earlier cross-platform document recorded a local macOS C consumer linking a generated
library with `Math.floor(double)` using `cc caller.c lib<name>.a -o caller`, without `-lm`.
It did not identify a commit/run and explicitly left Linux and Windows unverified for that
change. Keep it as limited historical context; [ABI linkage](specs/native-abi.md#host-math-linkage)
owns the current consumer contract.

## Build Baselines

PERF-BASELINE-01 was accepted on 2026-08-29 for commit
`3cf0058c4ded2b55748fcc3bc0a439bab514c4ce` in the
[maintainer's record](https://github.com/NanoNative/javan/issues/130#issuecomment-5461076136).
[Run 33237834288](https://github.com/NanoNative/javan/actions/runs/33237834288) passed all three targets.
Each target recorded three cold builds, three warm builds and one controlled source change
through packaged `bin/javan build` using the versioned example.

| Target | Reported elapsed range | Artifact bytes |
| --- | --- | ---: |
| Linux x64 | 10–12 s | 481,792 |
| Linux ARM64 | 20–22 s | 549,128 |
| macOS ARM64 | 15–18 s | 477,120–477,128 |

Cold runs rebuilt seven objects; warm runs reused seven; the source change rebuilt one and
reused six. Reports include toolchains, wall/CPU time, peak RSS and cache decisions.
These figures are fixture-specific measurements, not a future performance budget or proof
that current main compiles C in parallel. The current execution boundary is owned by
[CONC-003](specs/concurrency-runtime.md#requirements-and-acceptance).

The earlier testing document also recorded `CliPackagingIntegrationTest` moving from
116.49 s to 80.18 s for the same 21 tests, and the separated CLI compatibility suite taking
about 31 s versus 88 s. Those notes did not identify a commit or run; retain them only as
historical context, not reproducible acceptance evidence.

## Test Worker Balance

CI-SHARD-01 was accepted on 2026-08-29 in the
[maintainer's record](https://github.com/NanoNative/javan/issues/250#issuecomment-5462791345).
[PR #260](https://github.com/NanoNative/javan/pull/260), commit `645bc6b`, retained six workers.

- [Before, run 33209516104](https://github.com/NanoNative/javan/actions/runs/33209516104): 6:53–10:19, a 3:26 spread.
- [After, run 33253247197](https://github.com/NanoNative/javan/actions/runs/33253247197): 7:14–9:07, a 1:53 spread.

The assignment uses 56 recorded class durations. Selectors remain deterministic, disjoint
and complete; worker count, concurrency and memory limits were unchanged.

## Release Asset Selection

On 2026-09-20, local changes based on main `0bd68804` corrected REL-ASSETS-01. The executable
workflow fixture first reproduced 19 failures: the wildcard included internal/stale files and
accepted missing, empty or wrong-version assets. After correction, all 21 cases passed, including
creation/upload failure propagation, existing releases and repeated uploads:

```sh
./mvnw -q -Dtest='javan.ReleasePackagingSurfaceTest#releaseUpload*' test
```

The test executes the workflow's publication body with a recording `gh` command, not GitHub.
This is local proof of [asset selection](specs/release.md#public-release-assets),
not package-content validation, remote CI evidence or release approval.

The initial full `./mvnw clean verify` run completed 7,087 tests in 28:41: one failure,
no errors and three skips. All 72 release surface tests passed. The failure was the existing
[socket linger expectation](#socket-linger-portability), corrected separately below.

The subsequent `./mvnw -Pquick verify` passed in 2:28: 4,595 tests, no failures or errors,
and three skips. Automatic compatibility refresh also passed without changing tracked reports.

## Socket Linger Portability

On the same 2026-09-20 working tree, macOS ARM64 with GraalVM CE 25.0.1 rejected
`Socket.setSoLinger(true, 65_536)` before JavaN ran. A standalone JVM program and native
socket-option probe reproduced the rejection. Java defines the maximum timeout as
[platform-specific](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/Socket.html#setSoLinger(boolean,int)).

The test-only correction in [CliNetworkIntegrationTest](../src/test/java/javan/CliNetworkIntegrationTest.java)
requires either the exact clamped value on both runtimes or the specific setter failure on both;
it does not skip a platform or accept an unrelated error. A separate native pre-connection test
checks clamp boundaries and option reuse even where the OS rejects a large connected timeout.
Runtime behavior, dependencies and suite selection are unchanged.

The original focused test failed first. After correction, all three tests passed with no skips:

```sh
./mvnw -q -Dtest='javan.CliNetworkIntegrationTest#socketSoLinger*' test
```

The subsequent `./mvnw clean verify` passed in 25:49: 7,088 tests, no failures or errors,
and the same three skips. This includes all 101 network tests and 72 release surface tests.
Automatic compatibility refresh passed without changing tracked status reports. This is local
macOS evidence; Linux/Windows execution and remote candidate proof were not part of this run.

## Evidence Still Needed

An open issue can lag existing implementation: container digest-proof code exists while
issues [#115](https://github.com/NanoNative/javan/issues/115) and
[#263](https://github.com/NanoNative/javan/issues/263) remain open. No new container acceptance
is inferred here. Reconcile the exact artifact/run evidence before changing their status.

For new measurements, record the commit, public command, fixture, OS/architecture, toolchains,
resource conditions, result and remaining scope. Link the result from the owning requirement;
do not copy a passing count into several specs or treat it as current CI status.
