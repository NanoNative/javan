# Toolchains

Javan uses real local tools. It does not replace `javac`, ship fake JDK classes, or
silently change `PATH` or `JAVA_HOME`.

## Mental Model

```text
source -> real javac -> .class files -> Javan analysis -> C -> host C compiler
```

The installed JDK facade changes the entrypoint, not this pipeline:

```text
IDE / Maven / shell -> Javan JDK facade -> selected real JDK
                                      \-> Javan reports or native build
```

Normal Java behavior stays owned by the selected JDK. Javan only intercepts its documented
management commands and `javac` extensions.

## Commands

```sh
javan install
javan doctor
javan jdk list
javan jdk install
javan jdk use 25
javan jdk use temurin@25
javan jdk doctor
javan jdk resolve [jdk-home]
```

- `install` copies the Javan launcher and creates a stable JDK-shaped facade.
- `list` shows discovered and managed JDKs with their selection state.
- `use` switches the facade to a matching local or managed JDK.
- `resolve` explains which JDK wins and why.
- `doctor` reports Java, `javac`, C compiler, settings, and installation paths.

Resolution is local-first: explicit input, configured selection, environment/current JDK,
`PATH`, known platform JDK locations, managed JDKs, then an explicit managed install. A
candidate needs a usable `javac`; directory names alone are not trusted as version evidence.

## Installed Facade

`javan install` creates:

- one stable public JDK home for IDEs, build tools, and `JAVA_HOME`
- immutable facades for discovered backend JDKs
- one switchable `current` facade

The facade reuses the selected JDK layout. Only `java`, `javac`, and `javan` are Javan
launchers; other vendor tools remain direct backend tools. Unix uses links and native
launchers. Windows uses native executables and junctions. Existing non-Javan paths are not
overwritten.

Run `javan install` to print the selected public JDK home, then configure that path in the
IDE or build tool. Switching with `java jdk use ...` keeps the public path stable.

## Javac Facade

Ordinary arguments are passed to the selected real `javac` unchanged. Javan consumes only
the reserved `--jn-*` namespace:

| Option | Behavior |
| --- | --- |
| `--jn-off` | Disable Javan analysis and reports for this compile. |
| `--jn-warn` | Print native findings without changing successful `javac` status. |
| `--jn-strict` | Return `2` after successful compilation when native blockers exist. |
| `--jn-build` | Build a native app from the fresh `-d` output. |
| `--jn-main <class>` | Select the native main class. |
| `--jn-out <name>` | Select the native output name. |
| `--jn-target <os/arch>` | Assert the current host target. |
| `--jn-diag <format>` | Select `auto`, `compiler`, `pretty`, or `jsonl` diagnostics. |
| `--jn-end` | Pass every following argument directly to `javac`. |

The compact `-jn-*` spellings are equivalent. Reporting is the default. Native building is
always explicit.

Javan reuses `javac` facts instead of introducing duplicate options:

- `-d` is the class directory analyzed after compilation.
- `-cp`, `-classpath`, and `--class-path` are dependency inputs.
- `--release` remains the Java API/language target.

Example:

```sh
javac --release 25 -d target/classes --class-path libs/acme.jar \
  --jn-build --jn-main com.acme.Main --jn-out acme \
  src/main/java/com/acme/Main.java
```

A failed compile is never analyzed. A successful compile without `-d` gets an invocation
report but no guessed class directory. Reports are written below `.javan/reports/`.

## Boundaries

- `javan check` writes `.javan/reports/toolchain.json` and `.md` with the host, requested
  target, compiler, compile-probe evidence, and decision.
- Native generation starts only after the configured compiler accepts Javan's host flags.
- The current native backend builds for the host target; cross-linking fails with `JAVAN081`.
- A missing or incompatible native compiler fails `javan build` with `JAVAN080`; static
  `javan check` remains usable and reports the unavailable toolchain.
- A `PATH` compiler without a verified JDK home can compile but cannot back a JDK facade.
- Managed downloads require an explicit install path and verified metadata/checksum.
- Maven, Gradle, and IDE integrations are optional consumers of the same CLI and reports.
- LLVM, Cranelift, additional JDK providers, plugins, and direct dependency management are
  future roadmap work, not hidden partial implementations.
