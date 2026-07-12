package javan;

import javan.build.LibraryFormat;
import javan.codegen.NativeLinker;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.util.ProcessRunner;
import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class JavanInternalTest {
    @TempDir
    private Path tempDir;

    @Test
    void warningGroupsDeduplicateMatchingWarningsAndKeepDistinctOnesSeparate() throws Exception {
        final List<?> groups = warningGroups(List.of(
            Diagnostic.warning("JAVAN101", "duplicate", "a/A", "m", "s", "why", "fix"),
            Diagnostic.warning("JAVAN101", "duplicate", "a/B", "m", "s", "why", "fix"),
            Diagnostic.warning("JAVAN102", "different", "a/C", "m", "s", "why", "fix"),
            Diagnostic.warning("JAVAN101", "different-message", "a/D", "m", "s", "why", "fix"),
            Diagnostic.error("JAVAN001", "error", "a/D", "m", "s", "why", "fix")
        ));

        assertThat(groups).hasSize(3);
        assertThat(code(groups.get(0))).isEqualTo("JAVAN101");
        assertThat(message(groups.get(0))).isEqualTo("duplicate");
        assertThat(count(groups.get(0))).isEqualTo(2);
        assertThat(code(groups.get(1))).isEqualTo("JAVAN102");
        assertThat(message(groups.get(1))).isEqualTo("different");
        assertThat(count(groups.get(1))).isEqualTo(1);
        assertThat(code(groups.get(2))).isEqualTo("JAVAN101");
        assertThat(message(groups.get(2))).isEqualTo("different-message");
        assertThat(count(groups.get(2))).isEqualTo(1);
    }

    @Test
    void libraryArtifactPathUsesPlatformSharedLibrarySuffixes() throws Exception {
        final Path output = Path.of("/tmp/javan-out");

        assertThat(withOsName("Windows 11", () -> libraryArtifactPath(LibraryFormat.SHARED, output, "demo")))
            .isEqualTo(output.resolve("dist/demo.dll"));
        assertThat(withOsName("Mac OS X", () -> libraryArtifactPath(LibraryFormat.SHARED, output, "demo")))
            .isEqualTo(output.resolve("dist/libdemo.dylib"));
        assertThat(withOsName("Linux", () -> libraryArtifactPath(LibraryFormat.SHARED, output, "demo")))
            .isEqualTo(output.resolve("dist/libdemo.so"));
        assertThat(libraryArtifactPath(LibraryFormat.STATIC, output, "demo"))
            .isEqualTo(output.resolve("dist/libdemo.a"));
    }

    @Test
    void printLayoutWritesWarningsAndPlatformAwareOutputPath() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ProjectLayout layout = new ProjectLayout(
            Path.of("/tmp/project"),
            Path.of("/tmp/project"),
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(Path.of("/tmp/project/src/main/java")),
            List.of(Path.of("/tmp/project/src/main/resources")),
            List.of(Path.of("/tmp/project/.javan/classes")),
            List.of(),
            Path.of("/tmp/project/.javan"),
            "demo",
            List.of("warn-one", "warn-two")
        );

        withOsName("Windows 11", () -> {
            printLayout(layout, new PrintStream(bytes, true));
            return null;
        });

        assertThat(bytes.toString())
            .contains("warning: warn-one", "warning: warn-two")
            .contains("Output:  /tmp/project/.javan/bin/demo");
    }

    @Test
    void firstErrorAndPrintWarningsCoverDetailedOutputPaths() throws Exception {
        final Diagnostic warning = Diagnostic.warning("JAVAN101", "warn", "a/A", "m", "s", "why", "fix");
        final Diagnostic error = Diagnostic.error("JAVAN001", "boom", "a/B", "m", "s", "why", "fix");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(bytes, true);

        assertThat(firstError(List.of(warning, error))).isEqualTo(error);
        assertThat(firstError(List.of(warning))).isNull();

        printWarnings(List.of(warning, error), out);

        assertThat(bytes.toString()).contains("warning[JAVAN101]");
    }

    @Test
    void runtimeProfilingArgumentsRespectDisabledProfilingModulesAndEmitPathsWhenEnabled() throws Exception {
        final Javan javan = new Javan();
        final Path root = tempDir.resolve("project");
        final Path output = root.resolve(".javan");
        final ProjectLayout layout = new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.NONE,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            output,
            "demo",
            List.of()
        );
        Files.createDirectories(root);

        Files.writeString(root.resolve("javan.toml"), """
            [runtime]
            profiling = true
            disabled = ["thread-profiling"]
            """);
        assertThat(runtimeProfilingArguments(javan, layout)).isEmpty();

        Files.writeString(root.resolve("javan.toml"), """
            [runtime]
            profiling = true
            disabled = ["process"]
            """);
        assertThat(runtimeProfilingArguments(javan, layout)).containsExactly(
            "--javan-runtime-profile-json=" + output.resolve("reports/runtime-profiling.json"),
            "--javan-runtime-profile-md=" + output.resolve("reports/runtime-profiling.md")
        );
    }

    @Test
    void linkLibraryFormatRoutesStaticAndSharedFormatsToNativeLinker() throws Exception {
        final Javan javan = new Javan();
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            List.of(
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(0, "", "")
            )
        );
        setField(javan, "nativeLinker", new NativeLinker(runner));
        final Path root = tempDir.resolve("link-project");
        final Path mainC = writeSource(root.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = writeSource(root.resolve("runtime.c"), "void runtime(void) {}\n");
        final Path staticOut = root.resolve("dist/libdemo.a");
        final Path sharedOut = root.resolve("dist/libdemo.so");

        final Path staticLinked = withOsName("Linux", () -> linkLibraryFormat(javan, LibraryFormat.STATIC, root, mainC, runtimeC, staticOut));
        final Path sharedLinked = withOsName("Linux", () -> linkLibraryFormat(javan, LibraryFormat.SHARED, root, mainC, runtimeC, sharedOut));

        assertThat(staticLinked).isEqualTo(staticOut);
        assertThat(sharedLinked).isEqualTo(sharedOut);
        assertThat(runner.commands()).hasSize(4);
        assertThat(runner.commands().get(3)).contains("-shared", "-fPIC");
    }

    private static List<?> warningGroups(final List<Diagnostic> diagnostics) throws Exception {
        final Method method = Javan.class.getDeclaredMethod("warningGroups", List.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(null, diagnostics);
    }

    private static Path libraryArtifactPath(
        final LibraryFormat format,
        final Path outputDirectory,
        final String outputName
    ) throws Exception {
        final Method method = Javan.class.getDeclaredMethod("libraryArtifactPath", LibraryFormat.class, Path.class, String.class);
        method.setAccessible(true);
        return (Path) method.invoke(null, format, outputDirectory, outputName);
    }

    private static void printLayout(final ProjectLayout layout, final PrintStream out) throws Exception {
        final Method method = Javan.class.getDeclaredMethod("printLayout", ProjectLayout.class, PrintStream.class);
        method.setAccessible(true);
        method.invoke(null, layout, out);
    }

    private static Diagnostic firstError(final List<Diagnostic> diagnostics) throws Exception {
        final Method method = Javan.class.getDeclaredMethod("firstError", List.class);
        method.setAccessible(true);
        return (Diagnostic) method.invoke(null, diagnostics);
    }

    private static void printWarnings(final List<Diagnostic> diagnostics, final PrintStream out) throws Exception {
        final Method method = Javan.class.getDeclaredMethod("printWarnings", List.class, PrintStream.class);
        method.setAccessible(true);
        method.invoke(null, diagnostics, out);
    }

    @SuppressWarnings("unchecked")
    private static List<String> runtimeProfilingArguments(final Javan javan, final ProjectLayout layout) throws Exception {
        final Method method = Javan.class.getDeclaredMethod("runtimeProfilingArguments", ProjectLayout.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(javan, layout);
    }

    private static Path linkLibraryFormat(
        final Javan javan,
        final LibraryFormat format,
        final Path root,
        final Path libraryC,
        final Path runtimeC,
        final Path output
    ) throws Exception {
        final Method method = Javan.class.getDeclaredMethod(
            "linkLibraryFormat",
            LibraryFormat.class,
            Path.class,
            Path.class,
            Path.class,
            Path.class
        );
        method.setAccessible(true);
        return (Path) method.invoke(javan, format, root, libraryC, runtimeC, output);
    }

    private static String code(final Object group) throws Exception {
        return stringField(group, "code");
    }

    private static String message(final Object group) throws Exception {
        return stringField(group, "message");
    }

    private static int count(final Object group) throws Exception {
        final Field field = group.getClass().getDeclaredField("count");
        field.setAccessible(true);
        return (Integer) field.get(group);
    }

    private static String stringField(final Object value, final String name) throws Exception {
        final Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(value);
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Path writeSource(final Path path, final String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }

    private static <T> T withOsName(final String osName, final ThrowingSupplier<T> action) throws Exception {
        final String previous = System.getProperty("os.name");
        System.setProperty("os.name", osName);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class RecordingProcessRunner extends ProcessRunner {
        private final List<Result> scriptedResults;
        private final List<List<String>> commands = new ArrayList<>();

        private RecordingProcessRunner(final List<Result> scriptedResults) {
            this.scriptedResults = new ArrayList<>(scriptedResults);
        }

        @Override
        public Result run(final Path workingDirectory, final List<String> command) {
            commands.add(List.copyOf(command));
            if (scriptedResults.isEmpty()) {
                throw new IllegalStateException("No scripted process result left for command: " + command);
            }
            return scriptedResults.removeFirst();
        }

        private List<List<String>> commands() {
            return List.copyOf(commands);
        }
    }
}
