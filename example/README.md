# Native Showcase

This example demonstrates features Javan can compile today through its own native backend:

- plain Java source detection
- object allocation and final fields
- interface dispatch
- primitive arrays
- `ArrayList` / `List`
- `HashMap` / `Map.copyOf`
- `Optional`
- explicit `Iterator`
- enums
- static initialization
- scoped try/catch
- string intrinsics and string concatenation
- selected JDK intrinsics such as `Math.abs`, `Math.max`, `Arrays.copyOf`, and `System.arraycopy`

This is the rolling public proof target. When Javan gains a visible compiler or runtime
capability, this example should grow unless a separate complete public example is the
clearer fit.

## Build And Run

From the Javan repository root:

```sh
java -cp target/classes javan.Main build example --output native-showcase
example/.javan/bin/native-showcase
```

After building a release binary, the native Javan binary can build it too:

```sh
dist/javan build example --output native-showcase
example/.javan/bin/native-showcase
```

Expected output:

```text
javan native showcase
metric requests -> 9
first request
iter first request
request second request
map 9
samples 3
copy 8
name-length 8
char e
same true
enum READY
static ready 1
caught boom
safe deterministic native build
```
