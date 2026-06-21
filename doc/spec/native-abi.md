# Native ABI Contract

Status: implemented C ABI v2 baseline for primitive, `String`, `byte[]`, and `void`
exports. ABI v1 direct export symbols remain available for compatibility, and ABI v2
adds C `javan_try_*` result wrappers with owned diagnostic fields.

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
#define JAVAN_ABI_VERSION 2
#define JAVAN_ABI_V1_DIRECT_EXPORTS 1
#define JAVAN_ABI_STRING_UTF8 1
#define JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH 1
#define JAVAN_ABI_RUNTIME_DIAGNOSTICS 1
#define JAVAN_ABI_STRUCTURED_ERROR 1
#define JAVAN_ABI_RESULT_WRAPPERS 1
```

Rules:

- ABI version changes only when generated binary/header compatibility changes.
- Additive helper macros may keep the same ABI version.
- Layout changes to `JavanByteArray`, ownership changes, or error/result ABI changes must
  bump the ABI version.
- Generated bindings must check or encode the ABI version they were generated for.

## String Ownership

Inputs:

- `String` parameters map to UTF-8 `const char*`/`char*`-compatible inputs.
- Javan copies each non-null input into a GC-managed Java string before calling exported
  Java code.
- The caller keeps ownership of input memory and may mutate or release it after the export
  returns.
- Javan must not free input strings.
- A null C string remains Java null.
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
- Javan copies input byte arrays into GC-managed Java arrays before calling Java
- wrapper-created Java byte-array inputs are rooted until result export completes
- copied input arrays remain valid if exported Java code stores them; normal GC reclaims
  them once unreachable

Returns:

- returned `byte[]` values map to `JavanByteArray`
- returned `JavanByteArray.data` is javan-owned
- caller must release returned `data` with `javan_free`
- callers must not release returned `data` with raw `free`
- zero-length arrays must return a stable length and either null or releasable data
- generated export wrappers root returned Java `byte[]` values until the C ABI copy is
  complete

## Error And Result ABI

Current ABI v2 behavior:

- ABI v1 `javan_export_*` functions remain available and return declared results directly
- ABI v2 C `javan_try_*` functions return `JavanResult`
- non-void `javan_try_*` functions append a typed out-parameter for the successful value
- successful `javan_try_*` calls return `ok = 1`, leave diagnostic pointers null, and
  write the typed out-parameter
- failed `javan_try_*` calls return `ok = 0`, leave the typed out-parameter at a safe
  zero/null/default value, and copy diagnostics into owned result fields
- unsupported reachable code fails at build time
- app-mode uncaught native runtime failures panic/abort the current process path
- direct library exports catch supported Javan runtime panics at the generated export boundary,
  store the message in `javan_last_error()`, and return a safe default value for the
  declared return type
- generated Java statements carry allocation-free source-context nodes, so helper panics
  caught at the ABI boundary can store the same readable envelope as app-mode diagnostics
- `javan_last_error()` returns a borrowed static diagnostic string; callers must not free it
- `javan_last_error_code()`, `javan_last_error_summary()`, `javan_last_error_class()`,
  `javan_last_error_method()`, `javan_last_error_file()`, `javan_last_error_line()`,
  `javan_last_error_bytecode_offset()`, `javan_last_error_source_line()`,
  `javan_last_error_why()`, `javan_last_error_fix()`, and `javan_last_error_detail()`
  expose borrowed structured fields for the current process-global error
- callers can clear the stored library error with `javan_clear_error()`
- clearing resets structured pointer fields to `NULL` and numeric fields to `-1`
- the last-error state is process-global and single-threaded; the next export attempt clears it
- text fields are bounded static copies and may be truncated
- `JavanResult` diagnostic strings are owned by the caller and must be released with
  `javan_result_free`
- successful `String` and `byte[]` out-parameter values keep normal ABI ownership and
  must be released with `javan_free`
- `JavanResult` diagnostics survive `javan_clear_error()` and later export attempts until
  `javan_result_free` is called
- result diagnostic fields are not Java heap objects and are not scanned by the Java GC
- Rust, Go, and Python generated bindings expose direct ABI v1 calls, borrowed last-error
  helpers, and result-level wrappers over `javan_try_*`
- result-level language wrappers copy diagnostics into language-owned error values before
  calling `javan_result_free`
- result-level language wrappers copy successful `String`/`byte[]` outputs into
  language-owned values and release the Javan-owned native memory

Current C result type:

```c
typedef struct {
    int ok;
    char* code;
    char* message;
    char* summary;
    char* class_name;
    char* method;
    char* file;
    int line;
    int bytecode_offset;
    char* source_line;
    char* why;
    char* fix;
    char* detail;
} JavanResult;
```

The result ABI was introduced with ABI version 2. Future layout changes require another
version bump.

## Exception Mapping

Current behavior:

- deterministic native panics for unsupported runtime failures
- limited same-method catch lowering for supported native-profile shapes
- generated library exports map caught Javan runtime panics to the ABI v1 last-error
  channel and safe zero/null/default return values
- generated/internal frames are not hidden at the ABI boundary yet

Planned behavior:

- map supported Java exceptions to source-focused diagnostics
- preserve reachable call path where available
- expand Java exception mapping behind the ABI v2 `JavanResult` surface; borrowed
  `javan_last_error_*` accessors remain the ABI v1 structured diagnostic surface

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
- runtime diagnostics ABI macro
- structured error ABI macro
- `javan_free` declaration
- `javan_last_error` and `javan_clear_error` declarations
- structured `javan_last_error_*` declarations

Acceptance and CI compile generated ABI tests for library test projects.
Native-library sanitizer smoke also verifies retained `String` and `byte[]` inputs remain
stable after caller-side buffer mutation and are reclaimed after Java clears the retained
references.

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
