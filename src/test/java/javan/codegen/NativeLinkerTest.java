package javan.codegen;

import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class NativeLinkerTest {
    @TempDir
    private Path tempDir;

    @Test
    void windowsHostPrefersGccBeforeClangAndCc() {
        assertThat(NativeLinker.compilerCandidatesForOs("Windows 11", null))
            .containsExactly("gcc", "clang", "cc");
    }

    @Test
    void windowsHostAddsWinsockLibraryDuringLink() {
        assertThat(NativeLinker.platformLinkFlagsForOs("Windows 11"))
            .containsExactly("-lws2_32", "-ladvapi32");
    }

    @Test
    void nonWindowsHostDoesNotAddWinsockLibraryDuringLink() {
        assertThat(NativeLinker.platformLinkFlagsForOs("Mac OS X"))
            .isEmpty();
    }

    @Test
    void windowsExecutableExtensionsFallbackToDefaultsWhenPathExtIsBlank() {
        assertThat(NativeLinker.windowsExecutableExtensionsForValue("  "))
            .containsExactly(".exe", ".cmd", ".bat", ".com");
    }

    @Test
    void windowsExecutableExtensionsNormalizesConfiguredEntries() {
        assertThat(NativeLinker.windowsExecutableExtensionsForValue("EXE;.cmd; bat ; ;.COM"))
            .containsExactly(".EXE", ".cmd", ".bat", ".COM");
    }

    @Test
    void windowsExecutableExtensionsFallbackWhenConfiguredEntriesAreAllBlank() {
        assertThat(NativeLinker.windowsExecutableExtensionsForValue(" ;  ; "))
            .containsExactly(".exe", ".cmd", ".bat", ".com");
    }

    @Test
    void windowsHostResolvesExeSuffixFromPathEntry() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(NativeLinker.resolveExecutablePathForOs(
            tempDir.resolve("gcc"),
            "Windows 11",
            List.of(".exe", ".cmd")
        )).contains(compiler.toString());
    }

    @Test
    void windowsHostPrefersConfiguredCompilerBeforeFallbackCandidates() {
        assertThat(NativeLinker.compilerCandidatesForOs("Windows 11", "C:\\toolchain\\gcc.exe"))
            .containsExactly("C:\\toolchain\\gcc.exe", "gcc", "clang", "cc");
    }

    @Test
    void nonWindowsHostPrefersCcBeforeClangAndGcc() {
        assertThat(NativeLinker.compilerCandidatesForOs("Linux", null))
            .containsExactly("cc", "clang", "gcc");
    }

    @Test
    void firstOnPathForWindowsResolvesExeFromConcretePathEntries() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(NativeLinker.firstOnPathForOs(
            List.of("gcc", "clang", "cc"),
            tempDir.toString(),
            "Windows 11",
            List.of(".exe", ".cmd")
        )).contains(compiler.toString());
    }

    @Test
    void resolveExecutablePathForWindowsDoesNotAppendSuffixWhenExtensionIsExplicit() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.cmd"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(NativeLinker.resolveExecutablePathForOs(
            tempDir.resolve("gcc.cmd"),
            "Windows 11",
            List.of(".exe", ".cmd")
        )).contains(compiler.toString());

        assertThat(NativeLinker.resolveExecutablePathForOs(
            tempDir.resolve("gcc.bat"),
            "Windows 11",
            List.of(".exe", ".cmd")
        )).isEmpty();
    }

    @Test
    void resolveExecutablePathForNonWindowsRequiresExactMatch() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("cc"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(NativeLinker.resolveExecutablePathForOs(
            tempDir.resolve("cc"),
            "Linux",
            List.of(".exe")
        )).contains(compiler.toString());

        assertThat(NativeLinker.resolveExecutablePathForOs(
            tempDir.resolve("gcc"),
            "Linux",
            List.of(".exe")
        )).isEmpty();
    }

    @Test
    void firstOnPathForNonWindowsReturnsEmptyWhenExecutableIsMissing() {
        assertThat(NativeLinker.firstOnPathForOs(
            List.of("cc"),
            tempDir.toString(),
            "Linux",
            List.of(".exe")
        )).isEmpty();
    }

    @Test
    void resolveOnPathRejectsBlankExecutable() throws Exception {
        assertThat(resolveOnPath("", tempDir.toString(), "Linux", List.of(".exe"))).isEmpty();
    }

    @Test
    void resolveOnPathRejectsBlankPathForNamedExecutable() throws Exception {
        assertThat(resolveOnPath("cc", "   ", "Linux", List.of(".exe"))).isEmpty();
    }

    @Test
    void resolveOnPathResolvesExecutableWithExplicitPathSeparator() throws Exception {
        Files.createDirectories(tempDir.resolve("toolchain"));
        final Path compiler = Files.createFile(tempDir.resolve("toolchain/cc"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(resolveOnPath(compiler.toString(), "", "Linux", List.of(".exe"))).contains(compiler.toString());
    }

    @Test
    void resolveOnPathTreatsBlankPathEntriesAsCurrentDirectory() throws Exception {
        final Path compiler = Path.of("cc");
        Files.deleteIfExists(compiler);
        Files.writeString(compiler, "#!/bin/sh\nexit 0\n");
        assertThat(compiler.toFile().setExecutable(true)).isTrue();
        try {
            assertThat(resolveOnPath("cc", ":" + tempDir, "Linux", List.of(".exe"))).contains("./cc");
        } finally {
            Files.deleteIfExists(compiler);
        }
    }

    @Test
    void requiredExecutableUsesProcessRunnerFallbackWhenPathLookupMisses() throws Exception {
        final NativeLinker linker = new NativeLinker(new FakeProcessRunner(Optional.of("fallback-cc"), List.of()));

        assertThat(requiredExecutable(linker, List.of("definitely-not-on-path"), "missing"))
            .isEqualTo("fallback-cc");
    }

    @Test
    void requiredExecutableThrowsWhenPathLookupAndFallbackMiss() {
        final NativeLinker linker = new NativeLinker(new FakeProcessRunner(Optional.empty(), List.of()));

        assertThatThrownBy(() -> requiredExecutable(linker, List.of("definitely-not-on-path"), "missing compiler"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("missing compiler");
    }

    @Test
    void hasExplicitExtensionRejectsLeadingAndTrailingDots() throws Exception {
        assertThat(hasExplicitExtension(Path.of(".hidden"))).isFalse();
        assertThat(hasExplicitExtension(Path.of("tool."))).isFalse();
        assertThat(hasExplicitExtension(Path.of("tool.exe"))).isTrue();
    }

    @Test
    void hasExplicitExtensionReturnsFalseWhenFileNameIsMissing() throws Exception {
        assertThat(hasExplicitExtension(Path.of("/"))).isFalse();
    }

    @Test
    void containsPathSeparatorDetectsUnixWindowsAndPlainExecutableNames() throws Exception {
        assertThat(containsPathSeparator("toolchain/cc")).isTrue();
        assertThat(containsPathSeparator("toolchain\\cc")).isTrue();
        assertThat(containsPathSeparator("cc")).isFalse();
    }

    @Test
    void pathSeparatorFallsBackToColonWhenPropertyIsEmptyAndUsesConfiguredValueOtherwise() throws Exception {
        final String previous = System.getProperty("path.separator");
        try {
            System.setProperty("path.separator", "");
            assertThat(pathSeparator()).isEqualTo(':');
            System.setProperty("path.separator", ";");
            assertThat(pathSeparator()).isEqualTo(';');
        } finally {
            if (previous == null) {
                System.clearProperty("path.separator");
            } else {
                System.setProperty("path.separator", previous);
            }
        }
    }

    @Test
    void linkBuildsLinuxExecutableCommandWithPthread() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-cc"),
            List.of(new ProcessRunner.Result(0, "", ""))
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("main.c"), "int main(void) { return 0; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");
        final Path output = tempDir.resolve("bin/app");

        final Path linked = withOsName("Linux", () -> linker.link(tempDir, mainC, runtimeC, output));

        assertThat(linked).isEqualTo(output);
        assertThat(runner.commands()).hasSize(1);
        assertThat(runner.commands().getFirst().getFirst()).isNotBlank();
        assertThat(runner.commands().getFirst().subList(1, runner.commands().getFirst().size())).containsExactly(
            "-pthread",
            mainC.toString(),
            runtimeC.toString(),
            "-o",
            output.toString()
        );
    }

    @Test
    void linkBuildsWindowsExecutableCommandWithoutPthreadAndWithWinsock() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-gcc"),
            List.of(new ProcessRunner.Result(0, "", ""))
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("main.c"), "int main(void) { return 0; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");
        final Path output = tempDir.resolve("bin/app.exe");

        final Path linked = withOsName("Windows 11", () -> linker.link(tempDir, mainC, runtimeC, output));

        assertThat(linked).isEqualTo(output);
        assertThat(runner.commands()).hasSize(1);
        assertThat(runner.commands().getFirst().subList(1, runner.commands().getFirst().size())).containsExactly(
            mainC.toString(),
            runtimeC.toString(),
            "-lws2_32",
            "-ladvapi32",
            "-o",
            output.toString()
        );
    }

    @Test
    void linkSharedLibraryBuildsMacDynamicLibraryCommand() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-clang"),
            List.of(new ProcessRunner.Result(0, "", ""))
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");
        final Path output = tempDir.resolve("lib/libdemo.dylib");

        final Path linked = withOsName("Mac OS X", () -> linker.linkSharedLibrary(tempDir, mainC, runtimeC, output));

        assertThat(linked).isEqualTo(output);
        assertThat(runner.commands()).hasSize(1);
        assertThat(runner.commands().getFirst().subList(1, runner.commands().getFirst().size())).containsExactly(
            "-pthread",
            "-dynamiclib",
            "-fPIC",
            mainC.toString(),
            runtimeC.toString(),
            "-o",
            output.toString()
        );
    }

    @Test
    void linkSharedLibraryBuildsLinuxSharedLibraryCommand() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-cc"),
            List.of(new ProcessRunner.Result(0, "", ""))
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");
        final Path output = tempDir.resolve("lib/libdemo.so");

        final Path linked = withOsName("Linux", () -> linker.linkSharedLibrary(tempDir, mainC, runtimeC, output));

        assertThat(linked).isEqualTo(output);
        assertThat(runner.commands()).hasSize(1);
        assertThat(runner.commands().getFirst().subList(1, runner.commands().getFirst().size())).containsExactly(
            "-pthread",
            "-shared",
            "-fPIC",
            mainC.toString(),
            runtimeC.toString(),
            "-o",
            output.toString()
        );
    }

    @Test
    void linkSharedLibraryFailureIncludesProcessOutput() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-cc"),
            List.of(new ProcessRunner.Result(7, "stdout", "stderr"))
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");

        assertThatThrownBy(() -> withOsName(
            "Linux",
            () -> linker.linkSharedLibrary(tempDir, mainC, runtimeC, tempDir.resolve("lib/libdemo.so"))
        ))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Native shared library link failed")
            .hasMessageContaining("stderr")
            .hasMessageContaining("stdout");
    }

    @Test
    void linkStaticLibraryBuildsTwoObjectsThenArchives() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-cc"),
            List.of(
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(0, "", "")
            )
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");
        final Path output = tempDir.resolve("lib/libdemo.a");

        final Path linked = withOsName("Linux", () -> linker.linkStaticLibrary(tempDir, mainC, runtimeC, output));

        final Path objects = output.getParent().resolve("objects");
        final Path mainObject = objects.resolve("javan_library.o");
        final Path runtimeObject = objects.resolve("javan_runtime.o");
        assertThat(linked).isEqualTo(output);
        assertThat(runner.commands()).hasSize(3);
        assertThat(runner.commands().get(0).subList(1, runner.commands().get(0).size())).containsExactly(
            "-pthread",
            "-fPIC",
            "-c",
            mainC.toString(),
            "-o",
            mainObject.toString()
        );
        assertThat(runner.commands().get(1).subList(1, runner.commands().get(1).size())).containsExactly(
            "-pthread",
            "-fPIC",
            "-c",
            runtimeC.toString(),
            "-o",
            runtimeObject.toString()
        );
        assertThat(runner.commands().get(2).getFirst()).endsWith("ar");
        assertThat(runner.commands().get(2).subList(1, runner.commands().get(2).size())).containsExactly(
            "rcs",
            output.toString(),
            mainObject.toString(),
            runtimeObject.toString()
        );
    }

    @Test
    void linkStaticLibraryStopsAfterFirstCompileFailure() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-cc"),
            List.of(new ProcessRunner.Result(1, "stdout", "stderr"))
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");

        assertThatThrownBy(() -> withOsName(
            "Linux",
            () -> linker.linkStaticLibrary(tempDir, mainC, runtimeC, tempDir.resolve("lib/libdemo.a"))
        ))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Native compile failed")
            .hasMessageContaining("stderr")
            .hasMessageContaining("stdout");
        assertThat(runner.commands()).hasSize(1);
    }

    @Test
    void linkStaticLibraryReportsArchiveFailure() throws Exception {
        final FakeProcessRunner runner = new FakeProcessRunner(
            Optional.of("fake-cc"),
            List.of(
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(0, "", ""),
                new ProcessRunner.Result(2, "archive-out", "archive-err")
            )
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path mainC = Files.writeString(tempDir.resolve("library.c"), "int add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");

        assertThatThrownBy(() -> withOsName(
            "Linux",
            () -> linker.linkStaticLibrary(tempDir, mainC, runtimeC, tempDir.resolve("lib/libdemo.a"))
        ))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Native static library link failed")
            .hasMessageContaining("archive-err")
            .hasMessageContaining("archive-out");
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

    private static Optional<String> resolveOnPath(
        final String executable,
        final String path,
        final String osName,
        final List<String> windowsExtensions
    ) throws Exception {
        final Method method = NativeLinker.class.getDeclaredMethod(
            "resolveOnPath",
            String.class,
            String.class,
            String.class,
            List.class
        );
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Optional<String> result = (Optional<String>) method.invoke(null, executable, path, osName, windowsExtensions);
        return result;
    }

    private static String requiredExecutable(
        final NativeLinker linker,
        final List<String> executables,
        final String message
    ) throws Exception {
        final Method method = NativeLinker.class.getDeclaredMethod("requiredExecutable", List.class, String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(linker, executables, message);
        } catch (final InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private static boolean hasExplicitExtension(final Path candidate) throws Exception {
        final Method method = NativeLinker.class.getDeclaredMethod("hasExplicitExtension", Path.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, candidate);
    }

    private static boolean containsPathSeparator(final String executable) throws Exception {
        final Method method = NativeLinker.class.getDeclaredMethod("containsPathSeparator", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, executable);
    }

    private static char pathSeparator() throws Exception {
        final Method method = NativeLinker.class.getDeclaredMethod("pathSeparator");
        method.setAccessible(true);
        return (Character) method.invoke(null);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class FakeProcessRunner extends ProcessRunner {
        private final Optional<String> firstAvailable;
        private final List<Result> scriptedResults;
        private final List<List<String>> commands = new ArrayList<>();

        private FakeProcessRunner(final Optional<String> firstAvailable, final List<Result> scriptedResults) {
            this.firstAvailable = firstAvailable;
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

        @Override
        public Optional<String> firstAvailable(final List<String> executables) {
            return firstAvailable;
        }

        private List<List<String>> commands() {
            return List.copyOf(commands);
        }
    }
}
