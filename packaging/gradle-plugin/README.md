# Javan Gradle Plugin

The thin `dev.javan` plugin adds `javanCheck` and `javanBuild` to Java projects.
Both tasks depend on the `main` source set’s classes task and delegate to the
configured installed Javan executable:

```groovy
plugins {
    id 'java'
    id 'dev.javan' version '2026.6.14'
}
```

Use `-Pjavan.executable=/path/to/javan` and `-Pjavan.main=example.Main` when needed.
