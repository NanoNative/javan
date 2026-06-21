# Native Library Output

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

Current C ABI:

- primitive values map to stable C primitive types
- `String` inputs map to UTF-8 `char*` and are copied into GC-managed Java strings
- `byte[]` inputs map to `JavanByteArray { int8_t* data; int length; }` and are copied
  into GC-managed Java arrays
- returned strings and byte buffers are javan-owned and must be released with `javan_free`
- wrapper-created `String`/`byte[]` inputs and returned Java `String`/`byte[]` values are
  rooted until ABI export copies complete
- generated headers define `JAVAN_ABI_VERSION 2`
- generated headers keep `JAVAN_ABI_V1_DIRECT_EXPORTS 1` for direct-return
  compatibility symbols
- generated headers define string and byte-array ABI feature macros
- generated headers define `JAVAN_ABI_RUNTIME_DIAGNOSTICS 1` when `javan_last_error`
  and `javan_clear_error` are available
- generated headers define `JAVAN_ABI_STRUCTURED_ERROR 1` when borrowed structured
  `javan_last_error_*` accessors are available
- generated headers define `JAVAN_ABI_RESULT_WRAPPERS 1` when C `javan_try_*`
  wrappers and owned `JavanResult` diagnostics are available
- generated C ABI compile tests validate header version and layout assumptions
- native-library sanitizer smoke covers primitive, `String`, and `byte[]` exports under a
  constrained heap, retained input ownership after caller-side mutation, and
  counter-checks repeated C ABI export/free paths before shutdown cleanup
- generated Rust, Go, and Python bindings include explicit helpers for freeing
  javan-owned `String` and `byte[]` results
- native-library sanitizer smoke builds static and shared artifacts, runs Python binding
  ownership against the shared library when `python3` is available, and runs Rust/Go
  package ownership smoke tests when `rustc` or `go` are available
- current error/result ABI is ABI v2 C owned-result wrappers plus ABI v1-compatible
  direct return exports. Direct exports still expose `javan_last_error()` and borrowed
  structured `javan_last_error_*` fields; `javan_try_*` wrappers return owned
  `JavanResult` diagnostics freed with `javan_result_free`
- Rust, Go, and Python generated bindings expose result-level wrappers over `javan_try_*`
  in addition to direct exports and last-error helpers. These wrappers copy diagnostics
  before `javan_result_free` and copy successful `String`/`byte[]` outputs before
  `javan_free`
- thread/runtime rule is single-threaded native-profile exports until thread runtime support lands

Detailed ownership, exception, thread, result, and versioning rules live in
[native-abi.md](native-abi.md).

Generated outputs:

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

The `--library` command is the preferred user-facing path. It builds one library package
and can emit static, shared, or both native artifact formats:

```sh
javan build . --library --format static
javan build . --library --format shared
javan build . --library --format both
```

The old `--kind staticlib` and `--kind sharedlib` forms remain compatibility aliases for
automation that expects a single artifact format.

Metrics:

- input classes/methods
- reachable classes/methods from exports
- exported method count
- binary/library size
- runtime module families linked
- dependency reduction

Next library work:

- annotation-based exports
- richer ABI types for records and handles
- Cargo/Go/Python package manifests
- ABI compatibility reports
- stable error/result ABI
- exception-to-result mapping for library mode
- per-export thread/reentrancy reports
- cross-target shared library production
- LLVM and Cranelift backends after the C backend has enough deterministic coverage
