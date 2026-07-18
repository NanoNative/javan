# External Fixed-Rate Scheduled Executor Probe

Builds a small app against the currently pinned bundled scheduler artifact and compiles it to a
native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

The probe exercises the fixed-rate scheduling slice through the pinned artifact: the task is
scheduled with a later first fire time, the scheduler shuts down before that time, and
`awaitTermination(...)` returns `true`.

By default the script resolves the pinned artifact from `probe.properties` through
`JAVAN_MAVEN_REPO` or `~/.m2/repository`.

Override with one of:

```sh
JAVAN_PROBE_ARTIFACT=/path/to/dependency.jar ./build-example.sh
JAVAN_PROBE_CLASSPATH=/path/to/classpath-entry ./build-example.sh
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
