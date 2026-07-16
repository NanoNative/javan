# External Static Duration Probe

Builds a small app against the currently pinned published duration-helper artifact and compiles it
to a native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

The probe intentionally stays on one helper call. It does not start a service graph and does not
make any broader framework support claim.

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
