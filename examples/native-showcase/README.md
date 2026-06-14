# Native Showcase

This example demonstrates features Javan can compile today through its own native backend:

- plain Java source detection
- object allocation and final fields
- interface dispatch
- primitive arrays
- `ArrayList` / `List`
- string intrinsics and string concatenation
- selected JDK intrinsics such as `Math.abs`, `Math.max`, `Arrays.copyOf`, and `System.arraycopy`

## Build And Run

From the Javan repository root:

```sh
java -cp target/classes javan.Main build examples/native-showcase --output native-showcase
examples/native-showcase/.javan/bin/native-showcase
```

After building a release binary, the native Javan binary can build it too:

```sh
dist/javan build examples/native-showcase --output native-showcase
examples/native-showcase/.javan/bin/native-showcase
```

Expected output:

```text
javan native showcase
metric requests -> 9
first request
samples 3
copy 8
name-length 8
char e
same true
safe deterministic native build
```
