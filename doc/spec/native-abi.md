# Native ABI Contract

Status: implemented baseline for primitive, `String`, and `byte[]` exports. The current
ABI is versioned as `JAVAN_ABI_VERSION = 1`.

## Scope

The native ABI is C-first. Rust, Go, and Python bindings wrap the generated C ABI.

Current supported export types:

- primitive values
- `String`
- `byte[]`
- `void`

Unsupported export signatures fail before native code generation.

## Versioning

Generated C headers define:

```c
#define JAVAN_ABI_VERSION 1
#define JAVAN_ABI_STRING_UTF8 1
#define JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH 1
```

Rules:

- ABI version changes only when generated binary/header compatibility changes.
- Additive helper macros may keep the same ABI version.
- Layout changes to `JavanByteArray`, ownership changes, or error/result ABI changes must
  bump the ABI version.
- Generated bindings must check or encode the ABI version they were generated for.

## String Ownership

Inputs:

- `String` parameters map to borrowed UTF-8 `const char*`/`char*`-compatible inputs.
- The caller keeps ownership of input memory.
- Javan must not free input strings.
- Current string handling is byte/UTF-8 oriented; full Java UTF-16 semantics remain an
  open runtime gate.

Returns:

- returned `String` values map to `char*`
- returned strings are javan-owned
- caller must release returned strings with `javan_free`
- callers must not release returned strings with raw `free`
- returned memory remains valid until released by the caller
- generated export wrappers root returned Java `String` values until the C ABI copy is
  complete

## byte[] Ownership

Inputs:

- `byte[]` parameters map to:

```c
typedef struct {
    int8_t* data;
    int length;
} JavanByteArray;
```

- caller owns input `data`
- Javan copies input byte arrays into runtime-owned temporary arrays before calling Java
- wrapper-created Java byte-array inputs are rooted until result export completes
- temporary input copies are freed before the export returns

Returns:

- returned `byte[]` values map to `JavanByteArray`
- returned `JavanByteArray.data` is javan-owned
- caller must release returned `data` with `javan_free`
- callers must not release returned `data` with raw `free`
- zero-length arrays must return a stable length and either null or releasable data
- generated export wrappers root returned Java `byte[]` values until the C ABI copy is
  complete

## Error And Result ABI

Current ABI v1 behavior:

- successful exports return the declared result directly
- unsupported reachable code fails at build time
- uncaught native runtime failures panic/abort the current process path
- no stable `JavanResult` error object is emitted yet

Planned ABI v2 candidate:

```c
typedef struct {
    int ok;
    int code;
    char* message;
    void* value;
} JavanResult;
```

The result ABI must not be introduced silently. It requires a version bump and generated
binding updates.

## Exception Mapping

Current behavior:

- deterministic native panics for unsupported runtime failures
- limited same-method catch lowering for supported native-profile shapes
- generated/internal frames are not hidden at the ABI boundary yet

Planned behavior:

- map supported Java exceptions to source-focused diagnostics
- preserve reachable call path where available
- expose structured error results for library mode once `JavanResult` exists

## Thread And Runtime Rules

Current ABI/runtime rule:

- library exports are single-threaded native-profile entrypoints
- Java thread APIs, monitors, virtual threads, and thread-local runtime behavior are not
  supported in native library mode yet
- callers must not assume Javan exports are reentrant unless the report says so

Planned gates:

- explicit runtime initialization policy
- thread root registration
- virtual-thread scheduler support
- carrier pinning and blocking diagnostics
- per-export reentrancy/thread-safety report

## Generated Tests

Generated C binding output includes:

- `<name>.h`
- `<name>_abi_test.c`

The generated ABI test compiles the header and checks:

- ABI version macro
- `JavanByteArray` field layout assumptions
- string ABI macro
- byte-array ABI macro
- `javan_free` declaration

Acceptance and CI compile generated ABI tests for library test projects.

## Reports

Library builds report:

- `abiVersion`
- `stringOwnership`
- `byteArrayOwnership`
- `errorResultAbi`
- `exceptionMapping`
- `threadRuntimeRules`
- `generatedAbiTests`

These fields appear in:

- `.javan/reports/library-build.json`
- `.javan/reports/library-build.md`
- unified `.javan/reports/report.json`
- unified `.javan/reports/report.md`
