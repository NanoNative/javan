# Nano Duration Probe

Builds a small app against Nano and compiles it to a native executable with `javan`.

This probe is derived from `YunaBraska/nano example`'s `/load1` handler, which
uses `NanoUtils.formatDuration(...)` for response data. It intentionally does not start
`HttpServer` and does not include `DevConsoleService`, because the current native profile
does not support the full Nano service graph yet.

By default the script uses the local Maven Nano jar:

```sh
~/.m2/repository/org/nanonative/nano/2025.11.3131219/nano-2025.11.3131219.jar
```

Override with:

```sh
NANO_JAR=/path/to/nano.jar ./build-example.sh
```

Run:

```sh
./build-example.sh
```

Expected output:

```text
1m 5s
```
