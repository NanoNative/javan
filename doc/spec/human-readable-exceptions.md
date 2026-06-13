# Human-Readable Exceptions

Status: roadmap only. This file describes planned behavior and does not claim current
implementation.

## Goal

Build-time and runtime failures should point to the Java source the user wrote. The default
output must avoid raw JVM, native, generated C, and specialized-method frame noise. Native and
generated details remain available for compiler debugging through an explicit flag.

## Diagnostic Shape

Each human-readable exception diagnostic should contain:

- stable error code
- short problem summary
- Java class and method
- source file and line when available
- highlighted source line when source is available
- plain-language reason
- concrete fix suggestion
- reachable call path
- hidden generated/internal names by default

Example format:

```text
[JAVAN-RUNTIME-NULL] `user` is null

Where:
  com.acme.UserService.save(UserService.java:42)

Code:
  user.name()
  ^^^^ null

Why:
  `repository.find(id)` can return null.
  No null-check happened before this access.

Fix:
  Add a guard:
    Objects.requireNonNull(user, "user");

Path:
  Main.main -> UserController.handle -> UserService.save
```

## Runtime Behavior

Planned runtime diagnostics:

- null dereference
- array bounds failure
- string bounds failure
- failed cast
- division by zero
- uncaught supported platform exception
- explicit javan panic path

The runtime should report Java source frames first. Generated helper names, C frames, linker
symbols, and specialized method names are hidden unless `--debug-native` is enabled.

## Debug Map

Every optimized, specialized, generated wrapper, and C ABI export method should be traceable
back to its Java origin.

The debug map should preserve:

- generated symbol
- original Java class and method
- original descriptor
- source file
- source line table when available
- optimization or specialization reason
- reachable call path segment when known

This keeps release-mode diagnostics source-focused even when the executable contains renamed,
deduplicated, specialized, or inlined generated code.

## Planned Reports

Generated report paths:

- `.javan/reports/exceptions.json`
- `.javan/reports/exceptions.md`
- `.javan/reports/debug-map.json`

The JSON report should be deterministic and suitable for IDE/LSP consumption. The Markdown
report should be concise enough for humans and CI logs.

## CLI Policy

Planned CLI behavior:

- default output hides generated/native frames
- `--debug-native` includes generated symbols, native frames, and raw runtime details
- build-time diagnostics use the same source-focused shape as runtime diagnostics
- unknown internal failures still get a stable diagnostic code and a short remediation path

## Constraints

- Do not expose generated names by default.
- Do not claim exact source locations when line tables or source files are unavailable.
- Do not collapse multiple reachable failure paths into one vague error.
- Map optimized/specialized methods back to original Java source before printing.
- Keep report ordering deterministic.
