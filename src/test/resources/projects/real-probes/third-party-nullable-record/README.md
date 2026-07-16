# Nano MetricUpdate Probe

Builds a tiny app against a Nano dependency and compiles it to a native executable with `javan`.

By default the script uses the pinned Maven-cache jar. Override with either:

- `NANO_JAR=/path/to/nano.jar`
- `NANO_CLASSPATH=/path/to/dependency`
- `NANO_CLASSES=/path/to/compiled/classes`

```sh
./build-example.sh
```
