# Human-Readable Exceptions

Status: implemented slice. Generated uncaught `athrow` panic sites now use source-mapped
runtime diagnostics, generated runtime-helper panics inherit source context from the active
generated Java statement, source-backed builds print a `Code:` block with the Java source
line, exception/debug-map reports are written, and generated symbols are preserved only in
`debug-map.json`. Full Java exception semantics, exact expression/range highlighting, call
paths, expression-level helper blame, and `--debug-native` frame expansion remain planned.

## Goal

Build-time and runtime failures should point to the Java source the user wrote. The default
output must avoid raw JVM, native, generated C, and specialized-method frame noise. Native and
generated detail expansion remains planned behind an explicit `--debug-native` option.

## Diagnostic Shape

Target exception diagnostics should contain:

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

## Implemented Slice

Current runtime diagnostics cover generated uncaught Java exception panics lowered from
reachable `athrow` bytecode and source-context runtime helper panics emitted from generated
Java statements:

- `LineNumberTable` is parsed and used when present
- `SourceFile` is parsed and used when present, with a deterministic `<Class>.java` fallback
- app stderr prints code, summary, Java class/method/file/line, bytecode offset, why, detail, and fix
- when source files are available, app stderr prints a `Code:` block with the matching Java
  source line and caret marker
- native-library exports store a compact `javan_last_error()` envelope with code, summary,
  where, bytecode offset, and detail; it is not the full app stderr layout yet
- `.javan/reports/exceptions.json`, `.javan/reports/exceptions.md`, and
  `.javan/reports/debug-map.json` are written during native builds
- `.javan/reports/diagnostics.txt`, `.javan/reports/diagnostics.json`, and
  `.javan/reports/diagnostics.md` are written from the same build/check diagnostic model
- exception and debug-map JSON include `sourceLine` when Java source was found, and the
  Markdown exception report includes a source-line section
- generated C uses allocation-free stack nodes for active source context, so nested generated
  calls restore the caller context and recovered library panics do not keep stale source pointers
- runtime-internal panics outside generated Java source context remain message-only
- helper failures are currently mapped to the consuming generated IR statement; exact expression
  and operand-source mapping remains planned

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

Current debug-map entries cover generated panic sites. The target debug map should make every
optimized, specialized, generated wrapper, and C ABI export method traceable back to its Java
origin.

The debug map should preserve:

- generated symbol
- original Java class and method
- original descriptor
- source file
- source line table when available
- optimization or specialization reason
- reachable call path segment when known

This will keep release-mode diagnostics source-focused even when the executable contains
renamed, deduplicated, specialized, or inlined generated code.

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
