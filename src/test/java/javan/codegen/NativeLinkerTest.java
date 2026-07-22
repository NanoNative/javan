package javan.codegen;

import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
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
            .containsExactly("-lws2_32");
    }

    @Test
    void nonWindowsHostDoesNotAddWinsockLibraryDuringLink() {
        assertThat(NativeLinker.platformLinkFlagsForOs("Mac OS X"))
            .isEmpty();
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
    void firstOnPathForBlankPathReturnsEmpty() {
        assertThat(NativeLinker.firstOnPathForOs(
            List.of("cc"),
            "   ",
            "Linux",
            List.of(".exe")
        )).isEmpty();
    }

    @Test
    void firstOnPathForBlankExecutableReturnsEmpty() {
        assertThat(NativeLinker.firstOnPathForOs(
            List.of("   "),
            tempDir.toString(),
            "Linux",
            List.of(".exe")
        )).isEmpty();
    }

    @Test
    void firstOnPathForExplicitExecutablePathResolvesWithoutSearchPath() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("clang"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(NativeLinker.firstOnPathForOs(
            List.of(compiler.toString()),
            "",
            "Linux",
            List.of(".exe")
        )).contains(compiler.toString());
    }

    @Test
    void resolveExecutablePathForWindowsUsesCmdExtensionFallback() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.cmd"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        assertThat(NativeLinker.resolveExecutablePathForOs(
            tempDir.resolve("gcc"),
            "Windows 11",
            List.of(".exe", ".cmd")
        )).contains(compiler.toString());
    }

    @Test
    void linkUsesWinsockWithoutPthreadOnWindowsHost() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", "")
        );

        withOsName("Windows 11", () -> {
            final NativeLinker linker = new NativeLinker(runner);
            final Path output = tempDir.resolve("out/app.exe");

            assertThat(linker.link(tempDir, tempDir.resolve("main.c"), tempDir.resolve("runtime.c"), output)).isEqualTo(output);
        });

        assertThat(runner.commands()).singleElement().satisfies(command -> {
            assertThat(Path.of(command.getFirst()).getFileName().toString()).isEqualTo("gcc");
            assertThat(command).contains("-lws2_32", "-o", tempDir.resolve("out/app.exe").toString());
            assertThat(command).doesNotContain("-pthread");
        });
    }

    @Test
    void linkUsesRequestedOptimizationPosture() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", "")
        );

        withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/app"),
            "size-first"
        ));

        assertThat(runner.commands()).singleElement().satisfies(command -> assertThat(command).contains("-Os"));
    }

    @Test
    void linkUsesDebugSymbolsWhenRequested() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(0, "", ""));

        withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/app"),
            "balanced",
            true
        ));

        assertThat(runner.commands()).singleElement().satisfies(command -> assertThat(command).contains("-g"));
    }

    @Test
    void linkThrowsWhenCompilerReturnsFailure() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(1, "stdout", "stderr")
        );

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/app")
        ))).isInstanceOf(IOException.class)
            .hasMessageContaining("Native link failed")
            .hasMessageContaining("stderrstdout");
    }

    @Test
    void linkSharedLibraryUsesDynamiclibOnMacHost() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", "")
        );

        withOsName("Mac OS X", () -> {
            final NativeLinker linker = new NativeLinker(runner);
            linker.linkSharedLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/libdemo.dylib")
            );
        });

        assertThat(runner.commands()).singleElement().satisfies(command -> {
            assertThat(command).contains("-dynamiclib", "-fPIC", "-pthread", "-o", tempDir.resolve("out/libdemo.dylib").toString());
            assertThat(command).doesNotContain("-shared");
        });
    }

    @Test
    void linkSharedLibraryUsesSharedFlagOnLinuxHost() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", "")
        );

        withOsName("Linux", () -> {
            final NativeLinker linker = new NativeLinker(runner);
            linker.linkSharedLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/libdemo.so")
            );
        });

        assertThat(runner.commands()).singleElement().satisfies(command -> {
            assertThat(command).contains("-shared", "-fPIC", "-pthread", "-o", tempDir.resolve("out/libdemo.so").toString());
            assertThat(command).doesNotContain("-dynamiclib");
        });
    }

    @Test
    void linkSharedLibraryThrowsWhenCompilerReturnsFailure() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(1, "stdout", "stderr")
        );

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).linkSharedLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.so")
        ))).isInstanceOf(IOException.class)
            .hasMessageContaining("Native shared library link failed")
            .hasMessageContaining("stderrstdout");
    }

    @Test
    void linkStaticLibraryCompilesObjectsThenArchives() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", "")
        );

        withOsName("Linux", () -> {
            final NativeLinker linker = new NativeLinker(runner);
            linker.linkStaticLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/libdemo.a")
            );
        });

        assertThat(runner.commands()).hasSize(3);
        assertThat(runner.commands().get(0)).contains("-pthread", "-fPIC", "-c", tempDir.resolve("main.c").toString());
        assertThat(runner.commands().get(1)).contains("-pthread", "-fPIC", "-c", tempDir.resolve("runtime.c").toString());
        assertThat(runner.commands().get(2)).contains(
            "rcs",
            tempDir.resolve("out/libdemo.a").toString(),
            tempDir.resolve("out/objects/javan_library.o").toString(),
            tempDir.resolve("out/objects/javan_runtime.o").toString()
        );
    }

    @Test
    void linkStaticLibraryFailsWhenObjectCompilationFails() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(1, "stdout", "stderr")
        );

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.a")
        ))).isInstanceOf(IOException.class)
            .hasMessageContaining("Native compile failed")
            .hasMessageContaining("stderrstdout");
    }

    @Test
    void linkStaticLibraryFailsWhenArchiverReturnsFailure() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(1, "stdout", "stderr")
        );

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.a")
        ))).isInstanceOf(IOException.class)
            .hasMessageContaining("Native static library link failed")
            .hasMessageContaining("stderrstdout");
    }

    private static void withOsName(final String osName, final ThrowingRunnable action) throws Exception {
        final String original = System.getProperty("os.name");
        System.setProperty("os.name", osName);
        try {
            action.run();
        } finally {
            if (original == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", original);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class RecordingProcessRunner extends ProcessRunner {
        private final Deque<ProcessRunner.Result> scripted;
        private final List<List<String>> commands;

        private RecordingProcessRunner(final ProcessRunner.Result... scripted) {
            this.scripted = new ArrayDeque<>(List.of(scripted));
            this.commands = new ArrayList<>();
        }

        @Override
        public ProcessRunner.Result run(final Path workingDirectory, final List<String> command) {
            commands.add(List.copyOf(command));
            if (scripted.isEmpty()) {
                return new ProcessRunner.Result(0, "", "");
            }
            return scripted.removeFirst();
        }

        @Override
        public Optional<String> firstAvailable(final List<String> executables) {
            return executables.isEmpty() ? Optional.empty() : Optional.of(executables.getFirst());
        }

        private List<List<String>> commands() {
            return commands;
        }
    }
}
