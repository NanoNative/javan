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
javan build . --kind staticlib --export com.acme.Math.add
javan build . --kind sharedlib --export 'com.acme.Math.add(int,int):int'
javan build . --kind sharedlib --bindings c,rust,go,python
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

Generated outputs:

- `.javan/dist/lib<name>.a`
- `.javan/dist/lib<name>.so`
- `.javan/dist/lib<name>.dylib`
- `.javan/dist/<name>.dll`
- `.javan/dist/bindings/c/<name>.h`
- `.javan/dist/bindings/rust/lib.rs`
- `.javan/dist/bindings/go/<name>.go`
- `.javan/dist/bindings/python/<name>.py`

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
- cross-target shared library production
- LLVM and Cranelift backends after the C backend has enough deterministic coverage
