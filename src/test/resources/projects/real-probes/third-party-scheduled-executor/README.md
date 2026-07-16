# External Scheduled Executor Probe

Builds a small app against the currently pinned published scheduler artifact and compiles it to a
native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

The probe exercises the current scheduled-executor lifecycle slice through the pinned artifact:
one scheduled task runs, the scheduler shuts down, and
`awaitTermination(...)` returns `true`.

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
tick
true
```
