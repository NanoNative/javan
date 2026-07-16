# External Fixed-Rate Scheduled Executor Probe

Builds a small app against the currently pinned published scheduler artifact and compiles it to a
native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

The probe exercises the fixed-rate scheduling slice through the pinned artifact: the task is
scheduled with a later first fire time, the scheduler shuts down before that time, and
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
true
done
```
