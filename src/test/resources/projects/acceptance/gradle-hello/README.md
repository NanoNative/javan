# gradle-hello

Gradle example for `javan`.

Java 25 requires Gradle 9.1.0 or newer to run Gradle itself. If your system `gradle`
is older, add a wrapper and use that:

```sh
gradle wrapper --gradle-version=9.1.0
./gradlew classes
```

`javan` prefers `./gradlew` over `gradle`.
