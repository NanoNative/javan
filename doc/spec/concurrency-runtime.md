# Full Concurrency Runtime And Thread Analysis

Status: roadmap.

Existing spike/lab input:

- `/Users/yuna/projects/javan-project/javan-virtual-threads-native-spike`

The spike is migration material only. It does not change production status until slices
are moved into `javan`, tested, and accepted through the normal gates.

## Goal

`javan` must support Java virtual threads as a first-class native runtime feature and
analyze reachable thread usage for correctness, blocking behavior, scalability, and
pointless overhead.

## Scope

- virtual threads
- platform threads
- `ExecutorService`
- virtual-thread executors
- blocking calls
- CPU-bound tasks
- tiny tasks
- `synchronized` and locks
- `ThreadLocal`
- sleep, join, interrupt
- park and unpark

## Runtime Requirements

- virtual scheduler
- carrier thread pool
- virtual `Thread` object model
- `Thread.startVirtualThread(...)`
- `Thread.ofVirtual()`
- `Executors.newVirtualThreadPerTaskExecutor()`
- sleep, join, interrupt, park, and unpark
- `ThreadLocal`
- uncaught exception handling
- readable virtual-thread stack traces
- scheduler-aware blocking I/O
- pinning and blocking diagnostics

## Compiler Analysis

For every reachable thread or task root, build a `ThreadTaskSummary`:

- root method
- reachable call graph
- estimated instruction count
- allocation count
- loop presence
- blocking calls
- I/O calls
- sleep, park, and join usage
- synchronized and lock usage
- `ThreadLocal` usage
- native or unknown calls
- possible carrier pinning
- CPU-bound score
- tiny-task score

Task classifications:

- `IO_BOUND`
- `BLOCKING_WAIT`
- `CPU_BOUND`
- `TINY_CPU_TASK`
- `MIXED`
- `UNKNOWN`
- `PINNING_RISK`

## Diagnostics

| Diagnostic | Level | Meaning |
| --- | --- | --- |
| `JAVAN-THREAD-SMALL` | info | Task is very small, CPU-only, and has no blocking or I/O. Suggest inline execution or batching. |
| `JAVAN-THREAD-CPU` | warning | Virtual threads do not improve CPU throughput. Suggest a bounded platform-thread executor near CPU core count. |
| `JAVAN-THREAD-BLOCKING` | info/warning | Platform thread performs blocking I/O; suggest virtual threads when many concurrent blocking tasks exist. |
| `JAVAN-THREAD-UNKNOWN-BLOCK` | warning | Scheduler-safe blocking behavior cannot be proven. |
| `JAVAN-THREAD-PIN` | warning | `synchronized`, native, or unknown blocking may pin a carrier. |
| `JAVAN-THREAD-FLOOD` | warning | Many short-lived threads or tasks detected. |
| `JAVAN-THREAD-LOCAL` | info | Report memory and lifecycle implications for many virtual threads. |

Rules:

- definite correctness issue is an error
- likely scalability or performance issue is a warning
- possibly pointless usage is info
- do not fail builds for style or performance opinions unless warnings-as-errors policy is enabled
- never claim a task is safe unless proven

Every diagnostic must include:

- where
- why
- fix
- reachable path

## Runtime Profiling

Profiling should be enabled through project/global settings first, with results written
into the normal report model.

Collect:

- virtual threads created
- platform threads created
- average task duration
- blocked time
- CPU time
- pinning events
- scheduler queue delay
- carrier utilization
- `ThreadLocal` count
- blocking call sites

## Reports

- `.javan/reports/threads.json`
- `.javan/reports/threads.md`
- `.javan/reports/virtual-threads.json`
- `.javan/reports/virtual-threads.md`

Console summary:

```text
Thread analysis:
  virtual thread roots:      X
  platform thread roots:     X
  CPU-bound tasks:           X
  tiny tasks:                X
  blocking tasks:            X
  pinning risks:             X
  unknown blocking calls:    X
```

## CLI And Report Policy

Thread diagnostics should feed the same report model as other analysis tracks:

- normal `javan check` and `javan build` emit reachable thread diagnostics when enabled
- normal `javan report` shows thread and virtual-thread sections when present
- thread-specific public flags are not first-choice UX; prefer project/global settings
  unless an interactive workflow truly needs a flag

## Acceptance

- full virtual-thread APIs are supported
- thread diagnostics are emitted only for reachable code
- blocking, platform-thread, and virtual-thread usage is analyzed
- reports are stable JSON plus readable Markdown
- diagnostics include where, why, fix, and reachable path
- tests cover virtual threads, platform threads, tiny tasks, CPU-bound tasks, blocking
  tasks, pinning risk, `ThreadLocal`, join, sleep, interrupt, and profiling
