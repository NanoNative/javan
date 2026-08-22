# Native ABI Contract

Status: implemented C ABI v2 baseline for primitive, `String`, `byte[]`, `void`, and
opaque GC-rooted object-handle exports. ABI v1 direct export symbols remain available
for compatibility, and ABI v2 adds C `javan_try_*` result wrappers with owned diagnostic
fields.

## Scope

The native ABI is C-first. Rust, Go, and Python bindings wrap the generated C ABI.

Library builds use the same deterministic frontend as app builds:

```text
Java .class
-> javan IR
-> C
-> native linker
-> .so / .dylib / .dll / .a
-> C ABI exports
-> C / Rust / Go / Python bindings
```

Implemented build kinds:

- `app`
- `jar` (JVM jar output, not library mode)
- `library`
- `staticlib`
- `sharedlib`

Library mode:

- does not require `Main.main`
- starts reachability from explicit exports
- accepts exports from CLI or `javan.toml`
- rejects unsupported reachable code before C generation
- ignores unsupported unreachable code

Current supported export types:

- primitive values
- `String`
- `byte[]`
- `void`
- opaque `java.lang.Object` handles (C bindings only)

Unsupported export signatures and non-C binding requests for object handles fail before
native code generation.

Export declarations:

```sh
javan build . --library --export com.acme.Math.add
javan build . --library --format shared --export 'com.acme.Math.add(int,int):int'
javan build . --library --bindings c,rust,go,python
```

```toml
[exports]
methods = ["com.acme.Math.add(int,int):int"]
```

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
#define JAVAN_ABI_OBJECT_HANDLES 1
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

## Embedded Resources

App and library builds embed resources from application class folders and dependency
classpath entries. Application resources win path collisions, followed by dependencies in
classpath order. The same bytes are copied to `.javan/resources` and
`.javan/dist/resources`; `.javan/reports/resources.json` records their paths, sizes, and
SHA-256 checksums.

Embedded bytes are immutable and owned by the generated runtime. Each resource stream owns
only its cursor; closing it never transfers or releases the embedded bytes. A library export
that returns data read from a resource follows the normal `String` or `byte[]` ABI ownership
rules above. URL-shaped `getResource` and `getResources` calls remain unsupported and fail
during `javan check`; use the supported stream APIs instead.

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
- object handles are opaque `JavanObjectHandle*` values backed by a native reference-counted
  registry; each live handle is marked as a Java GC root
- a returned object handle owns one reference; callers must retain copied references and
  release every reference with `javan_object_handle_release`
- handle values are valid only for the library lifetime and invalid handles fail through
  the library error boundary
- object handles are currently exposed through generated C headers only; Rust, Go, and
  Python generation rejects exports containing object parameters or results explicitly
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

## Generic Java-to-C Native Imports

This is the direct Java-to-C import contract. It is separate from the generated C ABI
export contract above.

Configure static Java `native` methods and project-local link inputs in `javan.toml`:

```toml
[native]
imports = [
  "com.acme.NativeApi.mix(int,long,float,double,byte[]):long -> acme_mix",
]
sources = ["native/acme.c"]
objects = ["native/acme.o"]
library-search-paths = ["native/lib"]
libraries = ["m"]
frameworks = []

[native.target.macos]
sources = ["native/acme_macos.m"]
frameworks = ["Foundation"]

[native.target.macos-aarch64]
libraries = ["compression"]
```

The project parser accepts multiline arrays of quoted strings and trailing commas in
these configuration arrays. It is intentionally a small TOML parser, not full TOML:
a physical newline inside a quoted array item is rejected.

The import declaration names a Java method with `package.Class.method(parameters):return`
syntax, followed by `->` and one external C symbol. The method must be `static native`.
The supported import ABI is:

- parameters: `int`, `long`, `float`, `double`, or non-null `byte[]`
- return: `void`, `int`, `long`, `float`, or `double`
- no Java receiver, `String`, object, primitive array other than `byte[]`, or imported
  native return array is supported

Configured C and Objective-C sources compile with the generated runtime-header directory
on their include path, so they can include `javan_runtime.h` by name. That header is the
canonical declaration source for `JavanNativeImportedByteArray`; do not duplicate the
struct declaration in project sources. A corresponding C declaration is:

```c
#include "javan_runtime.h"

long long acme_mix(
    int arg0,
    long long arg1,
    float arg2,
    double arg3,
    JavanNativeImportedByteArray arg4
);
```

For a `byte[]` parameter, `data` is a mutable borrowed pointer to the Java array storage
and `length` is its element count. The generated wrapper roots the Java array until the
external call returns. The external function may mutate the bytes, but must not retain
the struct or its `data` pointer after return and must not free the storage. Copy it when
longer-lived ownership is required. A null Java `byte[]` is rejected at the native
boundary.

When a reachable configured native method is used through a method reference, the
instantiated SAM descriptor must equal the native implementation descriptor exactly.
No boxing or unboxing adaptation is inserted. For example, `Consumer<byte[]>` referring
to a native `void(byte[])` method is supported, while `Consumer<Integer>` referring to
`void(int)` and `Supplier<Integer>` referring to `int()` are rejected deterministically.
An unreachable mismatched method reference does not reject the build.

All configured imports are parsed and validated before reachability filtering: the class
and exact method must exist, be native, be static, and use the supported ABI. Invalid
unreachable imports therefore reject `javan check` and `javan build`. Valid unreachable
imports emit no generated wrapper, external symbol declaration, native call, or link
reference, so their external symbols need not be provided.

Link inputs are selected in this order: common `[native]` values, the platform overlay
`[native.target.<platform>]`, then the exact target overlay
`[native.target.<target>]`. Values append in declaration order; duplicates are rejected.
The resolver normalizes the requested target before selecting overlays, so target IDs and
overlay order are deterministic.

- `sources`: existing regular `.c` or `.m` files
- `objects`: existing regular `.o` or `.obj` files
- `library-search-paths`: existing directories
- `libraries`: named libraries passed to the linker
- `frameworks`: named macOS frameworks passed to the linker; frameworks are rejected on
  non-macOS hosts

Configured paths must be relative to the project root, must not contain `..`, must stay
within that root, and must use non-symlink regular files or directories as applicable.
Source/object extensions are lowercase and restricted to the forms above. Import symbols
must be valid non-reserved C identifiers; library and framework names use the supported
linker-name character set. Path separators are rejected in all three name fields, and
duplicate names are rejected. For static archive output, `library-search-paths`, named
`libraries`, and `frameworks` are rejected; sources and objects may still be archived.

Every reachable configured import must resolve while linking an application or shared
library. Static archives deliberately retain unresolved configured external references for
the final consumer link. Generated import wrappers use private ordinal C identifiers while
the canonical Java method symbol remains the IR call key; canonical-key conflicts with
generated functions and dispatches reject deterministically. C struct tags use their
separate C namespace.

This is compile/link-time direct symbol binding. Dynamic JNI loading, JVM native-method
resolution, `System.load`, and `System.loadLibrary` remain unsupported.

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

## Generated Outputs

Generated outputs include:

- `.javan/dist/lib<name>.a`
- `.javan/dist/lib<name>.so`
- `.javan/dist/lib<name>.dylib`
- `.javan/dist/<name>.dll`
- `.javan/dist/bindings/c/<name>.h`
- `.javan/dist/bindings/rust/lib.rs`
- `.javan/dist/bindings/go/<name>.go`
- `.javan/dist/bindings/python/<name>.py`
- `.javan/dist/lib/<name>/c/`
- `.javan/dist/lib/<name>/rust/`
- `.javan/dist/lib/<name>/go/`
- `.javan/dist/lib/<name>/python/`

The preferred user-facing path is:

```sh
javan build . --library --format static
javan build . --library --format shared
javan build . --library --format both
```

`--kind staticlib` and `--kind sharedlib` remain compatibility aliases for automation
that expects a single artifact format.

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

Library-build reporting also includes metrics such as:

- input classes and methods
- reachable classes and methods from exports
- exported method count
- binary and library size
- runtime module families linked
- dependency reduction

## Open Follow-Ups

Current follow-up work for library output:

- annotation-based exports
- richer ABI types for records and handles
- Cargo, Go, and Python package manifests
- ABI compatibility reports
- exception-to-result mapping for library mode
- per-export thread and reentrancy reports
- cross-target shared library production
- LLVM and Cranelift backends after the C backend has enough deterministic coverage
