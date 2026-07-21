# Javan In Plain Language

Read this page before changing compiler or runtime code.

## What Javan Is

Javan is an additional compiler stage for Java projects. It works with normal Java
source, existing build tools, and normal `.class` files.

```text
Java source
  -> javac, Maven, or Gradle compiles source to .class files
  -> Javan checks the reachable classes
  -> Javan IR
  -> generated C plus the Javan runtime
  -> host C compiler/linker
  -> native executable or native library
```

Javan does not replace `javac` in the first release. Its normal native input is compiled
class files.

## Terms That Are Easy To Confuse

| Term | Plain meaning | Is it runnable? |
| --- | --- | --- |
| `.class` file | Java bytecode emitted by `javac`, Maven, or Gradle. | Only on a JVM. |
| Javan IR | Javan's internal description of supported, reachable Java behavior before C generation. | No. It is an internal compiler data model, not a product artifact. |
| Generated C | C source emitted from the Javan IR plus the selected Javan runtime. | Not until a native compiler links it. |
| Native application | A host-OS executable built from generated C. | Yes. It does not need a JVM to run. |
| Native library | A static or shared C-ABI library with generated C, Rust, Go, or Python bindings. | It is loaded or linked by another native program. |
| Packaged `javan` | The Javan compiler CLI distributed as a native executable. | Yes. It can process existing `.class` files without a JVM. |

Javan may invoke `javac`, Maven, or Gradle when a project has source but no class output.
That step needs a JDK. Running the packaged Javan CLI or a Javan-built native application
does not.

## What "Native" Means Here

For a supported project, a Javan native executable contains the reachable Java application
code and Javan's generated C runtime. It does not need a JRE or JVM on the target machine.

The first release does not claim a fully self-contained binary for every operating system.
Current output uses the host C toolchain and may require compatible operating-system runtime
libraries. Javan must state those dependencies in reports and fail clearly when a requested
packaging mode is not implemented.

## What Javan Does Not Claim

- It does not support all of JDK 25.
- It does not silently emulate unsupported Java APIs.
- It does not translate Java source into Go.
- It does not make Javan IR available as a stable public API.
- It does not claim that a native executable preserves behavior that has not passed a
  public-entrypoint proof.

An unsupported reachable shape must fail before native code generation with a deterministic,
actionable diagnostic.

## How A Beginner Can Help Safely

Javan must never guess whether native output is correct. Every support claim needs a small
reproduction and a public proof. That means a contributor can add real value without first
understanding compiler internals: a clear reproduction, a reliable package check, or a
regression test tells the maintainer exactly what must keep working.

The first role is not "implement a Java API." It is "make one observed behavior easy to
understand and prove." This protects users from fake support and gives an experienced
maintainer a safe, small implementation target when production work is needed.

Do not begin by changing Javan IR, generated C runtime code, garbage collection, threads,
or ABI ownership. Those areas change language semantics and need prior context.

Start with one proof task:

1. Reproduce a reported behavior using the smallest Java project and a real `javan` command.
2. Record the command, Javan commit, host OS/architecture, exit code, stdout/stderr, and
   generated report path.
3. Turn the behavior into a public CLI, package, or generated-artifact test if the expected
   result is already clear.
4. Stop and ask for review if the task needs production changes in more than two files or
   crosses more than one subsystem.

Good first tasks:

- reproduce a package or CLI failure
- improve a failing public-entrypoint regression test
- verify archive contents, checksums, generated bindings, or report consistency
- correct documentation claims that lack a reproducible command
- reduce a real-project failure to a neutral Java reproduction

Before opening a PR, state the one user-visible behavior, the exact proof command, and what
remains unsupported. Do not use probe-project names in Javan production code, fixtures,
support claims, or compiler-owned tests.

After several small PRs with good reproduction and proof, take one implementation task only
when it already has a failing public test, a named code seam, and a maintainer-confirmed scope.
