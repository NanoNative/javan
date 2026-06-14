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
- `String` maps to UTF-8 `char*`
- `byte[]` maps to `JavanByteArray { int8_t* data; int length; }`
- returned strings and byte buffers are javan-owned and must be released with `javan_free`
- wrapper-created `byte[]` inputs and returned Java `String`/`byte[]` values are rooted
  until ABI export copies complete
- generated headers define `JAVAN_ABI_VERSION 1`
- generated headers define string and byte-array ABI feature macros
- generated C ABI compile tests validate header version and layout assumptions
- native-library sanitizer smoke covers primitive, `String`, and `byte[]` exports under a
  constrained heap
- current error/result ABI is direct return plus native panic on uncaught runtime failure
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
