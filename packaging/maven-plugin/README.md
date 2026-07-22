# Javan Maven Plugin

This thin adapter runs the installed `javan` executable after Maven has produced
`target/classes`:

```xml
<plugin>
  <groupId>dev.javan</groupId>
  <artifactId>javan-maven-plugin</artifactId>
  <version>2026.6.14</version>
  <executions>
    <execution><goals><goal>check</goal></goals></execution>
  </executions>
</plugin>
```

Use `-Djavan.executable=/path/to/javan` when the binary is not on `PATH`.
