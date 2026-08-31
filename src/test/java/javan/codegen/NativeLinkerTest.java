package javan.codegen;

import javan.build.NativeLinkInputs;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
            .containsExactly("-lws2_32", "-lshell32");
    }

    @Test
    void macHostDoesNotAddPlatformLibrariesDuringLink() {
        assertThat(NativeLinker.platformLinkFlagsForOs("Mac OS X"))
            .isEmpty();
    }

    @Test
    void linuxHostAddsMathLibraryDuringLink() {
        assertThat(NativeLinker.platformLinkFlagsForOs("Linux"))
            .containsExactly("-lm");
    }

    @Test
    void releaseCompilerFlagsOptimizeWithoutChangingPlatformLinkRequirements() {
        assertThat(NativeLinker.compilerFlagsForOs("Linux", true))
            .containsExactly("-O2", "-pthread", "-Wno-parentheses");
        assertThat(NativeLinker.compilerFlagsForOs("Mac OS X", true))
            .containsExactly("-O2", "-pthread", "-Wno-parentheses");
        assertThat(NativeLinker.compilerFlagsForOs("Windows 11", true))
            .containsExactly("-O2", "-Wno-parentheses");
        assertThat(NativeLinker.compilerFlagsForOs("Linux", false))
            .containsExactly("-pthread", "-Wno-parentheses");
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
            assertThat(command).contains("-lws2_32", "-lshell32", "-o", tempDir.resolve("out/app.exe").toString());
            assertThat(command).doesNotContain("-pthread");
        });
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
    void cachedLinkReusesVerifiedObjectsAndInvalidatesChangedGeneratedInputs() throws Exception {
        final Path header = Files.writeString(tempDir.resolve("javan_runtime.h"), "#define EXIT_CODE 0\n");
        final Path main = Files.writeString(
            tempDir.resolve("main.c"), "#include \"javan_runtime.h\"\nint main(void) { return EXIT_CODE; }\n"
        );
        final Path runtime = Files.writeString(tempDir.resolve("runtime.c"), "\n");
        final Path cache = tempDir.resolve("cache");
        final NativeLinker linker = new NativeLinker();

        final NativeLinker.CacheLinkResult initial = linker.linkCached(
            tempDir, main, runtime, tempDir.resolve("out/initial"), cache, NativeLinkInputs.empty(), List.of()
        );
        final NativeLinker.CacheLinkResult reused = linker.linkCached(
            tempDir, main, runtime, tempDir.resolve("out/reused"), cache, NativeLinkInputs.empty(), List.of()
        );
        final NativeLinker.CacheLinkResult release = linker.linkCached(
            tempDir,
            List.of(main),
            List.of(header),
            runtime,
            tempDir.resolve("out/release"),
            cache,
            NativeLinkInputs.empty(),
            List.of(),
            0,
            true
        );
        final NativeLinker.CacheLinkResult reusedRelease = linker.linkCached(
            tempDir,
            List.of(main),
            List.of(header),
            runtime,
            tempDir.resolve("out/reused-release"),
            cache,
            NativeLinkInputs.empty(),
            List.of(),
            0,
            true
        );
        Files.writeString(header, "#define EXIT_CODE 1\n");
        final NativeLinker.CacheLinkResult changedHeader = linker.linkCached(
            tempDir, main, runtime, tempDir.resolve("out/changed-header"), cache, NativeLinkInputs.empty(), List.of()
        );
        Files.writeString(changedHeader.objects().getFirst().object(), "corrupt object");
        final NativeLinker.CacheLinkResult repaired = linker.linkCached(
            tempDir, main, runtime, tempDir.resolve("out/repaired"), cache, NativeLinkInputs.empty(), List.of()
        );
        Files.writeString(main, "int main(void) { return 1; }\n");
        final NativeLinker.CacheLinkResult changed = linker.linkCached(
            tempDir, main, runtime, tempDir.resolve("out/changed"), cache, NativeLinkInputs.empty(), List.of()
        );

        assertThat(initial.artifact()).isRegularFile();
        assertThat(initial.objects()).allSatisfy(entry -> assertThat(entry.reused()).isFalse());
        assertThat(reused.objects()).allSatisfy(entry -> assertThat(entry.reused()).isTrue());
        assertThat(release.objects()).allSatisfy(entry -> assertThat(entry.reused()).isFalse());
        assertThat(reusedRelease.objects()).allSatisfy(entry -> assertThat(entry.reused()).isTrue());
        assertThat(changedHeader.objects()).allSatisfy(entry -> assertThat(entry.reused()).isFalse());
        assertThat(repaired.objects()).anySatisfy(entry -> {
            assertThat(entry.source()).isEqualTo("main.c");
            assertThat(entry.reused()).isFalse();
        });
        assertThat(repaired.objects()).anySatisfy(entry -> {
            assertThat(entry.source()).isEqualTo("runtime.c");
            assertThat(entry.reused()).isTrue();
        });
        assertThat(changed.objects()).anySatisfy(entry -> {
            assertThat(entry.source()).isEqualTo("main.c");
            assertThat(entry.reused()).isFalse();
        });
        assertThat(changed.objects()).anySatisfy(entry -> {
            assertThat(entry.source()).isEqualTo("runtime.c");
            assertThat(entry.reused()).isTrue();
        });
    }

    @Test
    void cachedLinkCompilesOrderedProgramUnitsAndInvalidatesTheirSharedHeader() throws Exception {
        final Path generated = Files.createDirectories(tempDir.resolve("generated"));
        final Path units = Files.createDirectories(generated.resolve("units"));
        final Path runtimeHeader = Files.writeString(generated.resolve("javan_runtime.h"), "\n");
        final Path programHeader = Files.writeString(
            generated.resolve("javan_program.h"), "#include \"javan_runtime.h\"\nint value(void);\n"
        );
        final Path main = Files.writeString(
            generated.resolve("main.c"), "#include \"javan_program.h\"\nint main(void) { return value(); }\n"
        );
        final Path unit = Files.writeString(
            units.resolve("functions-00.c"), "#include \"javan_program.h\"\nint value(void) { return 0; }\n"
        );
        final Path runtime = Files.writeString(generated.resolve("javan_runtime.c"), "\n");
        final Path cache = tempDir.resolve("cache");
        final NativeLinker linker = new NativeLinker();

        final NativeLinker.CacheLinkResult initial = linker.linkCached(
            tempDir, List.of(main, unit), List.of(programHeader, runtimeHeader), runtime,
            tempDir.resolve("out/initial"), cache, NativeLinkInputs.empty(), List.of()
        );
        final NativeLinker.CacheLinkResult reused = linker.linkCached(
            tempDir, List.of(main, unit), List.of(programHeader, runtimeHeader), runtime,
            tempDir.resolve("out/reused"), cache, NativeLinkInputs.empty(), List.of()
        );
        Files.writeString(unit, "#include \"javan_program.h\"\nint value(void) { return 1; }\n");
        final NativeLinker.CacheLinkResult changedUnit = linker.linkCached(
            tempDir, List.of(main, unit), List.of(programHeader, runtimeHeader), runtime,
            tempDir.resolve("out/changed-unit"), cache, NativeLinkInputs.empty(), List.of()
        );
        Files.writeString(programHeader, "#include \"javan_runtime.h\"\nint value(void);\n/* changed */\n");
        final NativeLinker.CacheLinkResult changedHeader = linker.linkCached(
            tempDir, List.of(main, unit), List.of(programHeader, runtimeHeader), runtime,
            tempDir.resolve("out/changed-header"), cache, NativeLinkInputs.empty(), List.of()
        );
        Files.writeString(runtimeHeader, "/* changed runtime contract */\n");
        final NativeLinker.CacheLinkResult changedRuntimeHeader = linker.linkCached(
            tempDir, List.of(main, unit), List.of(programHeader, runtimeHeader), runtime,
            tempDir.resolve("out/changed-runtime-header"), cache, NativeLinkInputs.empty(), List.of()
        );

        assertThat(initial.objects()).extracting(NativeLinker.CacheEntry::source)
            .containsExactly("main.c", "units/functions-00.c", "javan_runtime.c");
        assertThat(initial.objects()).allSatisfy(entry -> assertThat(entry.reused()).isFalse());
        assertThat(reused.objects()).allSatisfy(entry -> assertThat(entry.reused()).isTrue());
        assertThat(changedUnit.objects()).extracting(NativeLinker.CacheEntry::reused)
            .containsExactly(true, false, true);
        assertThat(changedHeader.objects()).extracting(NativeLinker.CacheEntry::reused)
            .containsExactly(false, false, true);
        assertThat(changedRuntimeHeader.objects()).allSatisfy(entry -> assertThat(entry.reused()).isFalse());
    }

    @Test
    void cachedLinkSerializesIndependentCompilesAndPreservesObjectOrder() throws Exception {
        final GeneratedSources sources = generatedSources(3);
        final ObjectWritingProcessRunner runner = new ObjectWritingProcessRunner(1);
        final NativeLinker linker = new NativeLinker(runner);

        final NativeLinker.CacheLinkResult linked = linker.linkCached(
            tempDir,
            sources.programSources(),
            sources.headers(),
            sources.runtime(),
            tempDir.resolve("out/app"),
            tempDir.resolve("cache"),
            NativeLinkInputs.empty(),
            List.of(),
            2
        );

        assertThat(linked.workers()).satisfies(workers -> {
            assertThat(workers.requestedJobs()).isEqualTo(2);
            assertThat(workers.effectiveJobs()).isOne();
            assertThat(workers.queued()).isEqualTo(4);
        });
        assertThat(runner.peakCompiles()).isOne();
        assertThat(linked.objects()).extracting(NativeLinker.CacheEntry::source).containsExactly(
            "main.c", "units/functions-00.c", "units/functions-01.c", "units/functions-02.c", "javan_runtime.c"
        );
    }

    @Test
    void cachedLinkProducesTheSameExecutableForDifferentRequestedWorkerCaps() throws Exception {
        final GeneratedSources sources = generatedSources(2);
        final NativeLinker linker = new NativeLinker(new ProcessRunner());
        final Path serialOutput = nativeOutput("serial");
        final Path parallelOutput = nativeOutput("parallel");

        final NativeLinker.CacheLinkResult serial = linker.linkCached(
            tempDir,
            sources.programSources(),
            sources.headers(),
            sources.runtime(),
            serialOutput,
            tempDir.resolve("cache/serial"),
            NativeLinkInputs.empty(),
            List.of(),
            1
        );
        final NativeLinker.CacheLinkResult parallel = linker.linkCached(
            tempDir,
            sources.programSources(),
            sources.headers(),
            sources.runtime(),
            parallelOutput,
            tempDir.resolve("cache/parallel"),
            NativeLinkInputs.empty(),
            List.of(),
            2
        );

        assertThat(serial.workers().effectiveJobs()).isOne();
        assertThat(parallel.workers().effectiveJobs()).isOne();
        assertThat(serial.objects()).extracting(NativeLinker.CacheEntry::source)
            .containsExactlyElementsOf(parallel.objects().stream().map(NativeLinker.CacheEntry::source).toList());
        assertThat(new ProcessRunner().run(serialOutput.getParent(), List.of(serialOutput.toString())).exitCode()).isZero();
        assertThat(new ProcessRunner().run(parallelOutput.getParent(), List.of(parallelOutput.toString())).exitCode()).isZero();
    }

    @Test
    void cachedLinkDoesNotRunTheFinalLinkWhenAnObjectCompilationFails() throws Exception {
        final GeneratedSources sources = generatedSources(1);
        Files.writeString(sources.programSources().get(1), "not valid C\n");
        final Path output = nativeOutput("failed");
        final NativeLinker linker = new NativeLinker(new ProcessRunner());

        assertThatThrownBy(() -> linker.linkCached(
            tempDir,
            sources.programSources(),
            sources.headers(),
            sources.runtime(),
            output,
            tempDir.resolve("cache/failed"),
            NativeLinkInputs.empty(),
            List.of(),
            2
        )).isInstanceOf(IOException.class).hasMessageContaining("Native compile failed");

        assertThat(output).doesNotExist();
    }

    @Test
    void cachedLinkCapsExplicitWorkersAtOne() throws Exception {
        final GeneratedSources sources = generatedSources(6);
        final ObjectWritingProcessRunner runner = new ObjectWritingProcessRunner(1);
        final NativeLinker linker = new NativeLinker(runner);

        final NativeLinker.CacheLinkResult linked = linker.linkCached(
            tempDir,
            sources.programSources(),
            sources.headers(),
            sources.runtime(),
            tempDir.resolve("out/app"),
            tempDir.resolve("cache"),
            NativeLinkInputs.empty(),
            List.of(),
            4
        );

        assertThat(linked.workers().effectiveJobs()).isOne();
        assertThat(linked.workers().queued()).isEqualTo(7);
        assertThat(runner.peakCompiles()).isOne();
    }

    @Test
    void cachedLinkCleansStagingAfterFailure() throws Exception {
        final GeneratedSources sources = generatedSources(1);
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(1, "", "intentional failure")
        );
        final NativeLinker linker = new NativeLinker(runner);

        assertThatThrownBy(() -> linker.linkCached(
            tempDir,
            sources.programSources(),
            sources.headers(),
            sources.runtime(),
            tempDir.resolve("out/failed"),
            tempDir.resolve("cache"),
            NativeLinkInputs.empty(),
            List.of(),
            2
        )).isInstanceOf(IOException.class).hasMessageContaining("Native compile failed");

        assertThat(sources.programSources().getFirst().resolveSibling("main.c.object")).doesNotExist();
        assertThat(sources.programSources().get(1).resolveSibling("functions-00.c.object")).doesNotExist();
    }

    @Test
    void legacyAppOverloadMatchesExplicitEmptyInputs() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", "")
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path output = tempDir.resolve("out/app");

        withOsName("Linux", () -> {
            linker.link(tempDir, tempDir.resolve("main.c"), tempDir.resolve("runtime.c"), output);
            linker.link(tempDir, tempDir.resolve("main.c"), tempDir.resolve("runtime.c"), output, NativeLinkInputs.empty(), List.of());
        });

        assertThat(runner.commands().get(0)).isEqualTo(runner.commands().get(1));
    }

    @Test
    void linkOrdersConfiguredInputsAfterGeneratedSources() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(0, "", ""));
        final Path source = tempDir.resolve("native/backend.c");
        final Path object = tempDir.resolve("native/helper.o");
        final Path firstSearchPath = tempDir.resolve("native/lib-one");
        final Path secondSearchPath = tempDir.resolve("native/lib-two");
        final Path output = tempDir.resolve("out/app");

        withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            output,
            new NativeLinkInputs(
                List.of(source),
                List.of(object),
                List.of(firstSearchPath, secondSearchPath),
                List.of("alpha", "beta"),
                List.of()
            ),
            List.of()
        ));

        assertThat(commandArguments(runner.commands())).containsExactlyElementsOf(List.of(
            List.of(
                "-pthread",
                "-Wno-parentheses",
                tempDir.resolve("main.c").toString(),
                tempDir.resolve("runtime.c").toString(),
                "-I",
                tempDir.toString(),
                source.toString(),
                object.toString(),
                "-L",
                firstSearchPath.toString(),
                "-L",
                secondSearchPath.toString(),
                "-lalpha",
                "-lbeta",
                "-lm",
                "-o",
                output.toString()
            )
        ));
    }

    @Test
    void linkAddsMacFrameworksAfterConfiguredLibraries() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(0, "", ""));
        final Path output = tempDir.resolve("out/app");

        withOsName("Mac OS X", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            output,
            new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("objc"), List.of("Cocoa", "Metal")),
            List.of()
        ));

        assertThat(commandArguments(runner.commands())).containsExactlyElementsOf(List.of(
            List.of(
                "-pthread",
                "-Wno-parentheses",
                tempDir.resolve("main.c").toString(),
                tempDir.resolve("runtime.c").toString(),
                "-lobjc",
                "-framework",
                "Cocoa",
                "-framework",
                "Metal",
                "-o",
                output.toString()
            )
        ));
    }

    @Test
    void linkRejectsFrameworksOnNonMacHostBeforeRunningACommand() {
        final NeverRunProcessRunner runner = new NeverRunProcessRunner();

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/app"),
            new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(), List.of("Cocoa")),
            List.of()
        ))).isInstanceOf(IOException.class)
            .hasMessage("macOS frameworks are only supported on macOS hosts");
    }

    @Test
    void linkReportsConfiguredImportedSymbolsPresentInFailureOutput() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(1, "", "undefined reference to `alpha_symbol'\nUndefined symbols: \"_beta_symbol\"")
        );

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/app"),
            NativeLinkInputs.empty(),
            List.of("alpha_symbol", "beta_symbol", "other_symbol")
        ))).isInstanceOf(IOException.class)
            .hasMessageStartingWith("Native link failed\nMissing native import symbols: alpha_symbol, beta_symbol\n");
    }

    @Test
    void linkKeepsGenericFailureDiagnosticWhenNoImportedSymbolIsPresent() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(1, "stdout", "stderr"));

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).link(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/app"),
            NativeLinkInputs.empty(),
            List.of("missing_symbol")
        ))).isInstanceOf(IOException.class)
            .hasMessage("Native link failed\nstderrstdout");
    }

    @Test
    void linkKeepsGenericFailureWhenConfiguredSymbolIsNotReportedUndefined() throws Exception {
        final String stderr = "clang: error: invalid value 'alpha_symbol' in '-o' option\n";
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(1, "", stderr));
        final IOException failure;
        try {
            new NativeLinker(runner).link(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/app"),
                NativeLinkInputs.empty(),
                List.of("alpha_symbol")
            );
            throw new AssertionError("Expected native link failure");
        } catch (final IOException exception) {
            failure = exception;
        }

        assertThat(failure).hasMessage("Native link failed\n" + stderr);
    }

    @Test
    void linkSharedLibraryUsesDynamiclibOnDarwinHost() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", "")
        );

        withOsName("Darwin", () -> {
            final NativeLinker linker = new NativeLinker(runner);
            linker.linkSharedLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/libdemo.dylib")
            );
        });

        assertThat(runner.commands().get(0)).contains("-dynamiclib", "-Wl,-undefined,error");
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

        assertThat(runner.commands().get(0)).contains("-shared", "-Wl,--no-undefined");
    }

    @Test
    void releaseLibraryCommandsCompileWithPortableOptimization() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", "")
        );
        final NativeLinker linker = new NativeLinker(runner);

        withOsName("Linux", () -> {
            linker.linkSharedLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/libdemo.so"),
                NativeLinkInputs.empty(),
                List.of(),
                true
            );
            linker.linkStaticLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                tempDir.resolve("out/libdemo.a"),
                NativeLinkInputs.empty(),
                List.of(),
                true
            );
        });

        assertThat(runner.commands().get(0)).contains("-O2", "-shared");
        assertThat(runner.commands().get(1)).contains("-O2", "-fPIC", "-c");
        assertThat(runner.commands().get(2)).contains("-O2", "-fPIC", "-c");
        assertThat(runner.commands().get(3)).doesNotContain("-O2");
    }

    @Test
    void linkSharedLibraryUsesNoUndefinedOnWindowsGnuDriver() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(0, "", ""));

        withOsName("Windows 11", () -> new NativeLinker(runner).linkSharedLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/demo.dll")
        ));

        assertThat(runner.commands().get(0)).contains("-shared", "-Wl,--no-undefined");
    }

    @Test
    void legacySharedOverloadMatchesExplicitEmptyInputs() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", "")
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path output = tempDir.resolve("out/libdemo.so");

        withOsName("Linux", () -> {
            linker.linkSharedLibrary(tempDir, tempDir.resolve("main.c"), tempDir.resolve("runtime.c"), output);
            linker.linkSharedLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                output,
                NativeLinkInputs.empty(),
                List.of()
            );
        });

        assertThat(runner.commands().get(0)).isEqualTo(runner.commands().get(1));
    }

    @Test
    void sharedLinkOrdersConfiguredInputsAfterSharedFlags() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(0, "", ""));
        final Path source = tempDir.resolve("native/backend.c");
        final Path object = tempDir.resolve("native/helper.o");
        final Path searchPath = tempDir.resolve("native/lib");
        final Path output = tempDir.resolve("out/libdemo.so");

        withOsName("Linux", () -> new NativeLinker(runner).linkSharedLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            output,
            new NativeLinkInputs(List.of(source), List.of(object), List.of(searchPath), List.of("math"), List.of()),
            List.of()
        ));

        assertThat(commandArguments(runner.commands())).containsExactlyElementsOf(List.of(
            List.of(
                "-pthread",
                "-Wno-parentheses",
                "-shared",
                "-fPIC",
                "-Wl,--no-undefined",
                tempDir.resolve("main.c").toString(),
                tempDir.resolve("runtime.c").toString(),
                "-I",
                tempDir.toString(),
                source.toString(),
                object.toString(),
                "-L",
                searchPath.toString(),
                "-lmath",
                "-lm",
                "-o",
                output.toString()
            )
        ));
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
    void legacyStaticOverloadMatchesExplicitEmptyInputs() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", "")
        );
        final NativeLinker linker = new NativeLinker(runner);
        final Path output = tempDir.resolve("out/libdemo.a");

        withOsName("Linux", () -> {
            linker.linkStaticLibrary(tempDir, tempDir.resolve("main.c"), tempDir.resolve("runtime.c"), output);
            linker.linkStaticLibrary(
                tempDir,
                tempDir.resolve("main.c"),
                tempDir.resolve("runtime.c"),
                output,
                NativeLinkInputs.empty(),
                List.of()
            );
        });

        assertThat(runner.commands().subList(0, 3)).isEqualTo(runner.commands().subList(3, 6));
    }

    @Test
    void staticLinkCompilesConfiguredSourcesThenArchivesConfiguredObject() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", "")
        );
        final Path firstSource = tempDir.resolve("native/first.c");
        final Path secondSource = tempDir.resolve("native/second.m");
        final Path configuredObject = tempDir.resolve("native/helper.o");
        final Path output = tempDir.resolve("out/libdemo.a");

        withOsName("Mac OS X", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            output,
            new NativeLinkInputs(
                List.of(firstSource, secondSource),
                List.of(configuredObject),
                List.of(),
                List.of(),
                List.of()
            ),
            List.of()
        ));

        assertThat(commandArguments(runner.commands())).containsExactlyElementsOf(List.of(
            List.of("-pthread", "-Wno-parentheses", "-fPIC", "-c", tempDir.resolve("main.c").toString(), "-o", tempDir.resolve("out/objects/javan_library.o").toString()),
            List.of("-pthread", "-Wno-parentheses", "-fPIC", "-c", tempDir.resolve("runtime.c").toString(), "-o", tempDir.resolve("out/objects/javan_runtime.o").toString()),
            List.of("-pthread", "-Wno-parentheses", "-fPIC", "-I", tempDir.toString(), "-c", firstSource.toString(), "-o", tempDir.resolve("out/objects/native_input_0.o").toString()),
            List.of("-pthread", "-Wno-parentheses", "-fPIC", "-I", tempDir.toString(), "-c", secondSource.toString(), "-o", tempDir.resolve("out/objects/native_input_1.o").toString()),
            List.of(
                "rcs",
                output.toString(),
                tempDir.resolve("out/objects/javan_library.o").toString(),
                tempDir.resolve("out/objects/javan_runtime.o").toString(),
                tempDir.resolve("out/objects/native_input_0.o").toString(),
                tempDir.resolve("out/objects/native_input_1.o").toString(),
                configuredObject.toString()
            )
        ));
    }

    @Test
    void staticLinkRejectsLibrarySearchPathsBeforeRunningCommand() {
        final NeverRunProcessRunner runner = new NeverRunProcessRunner();

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.a"),
            new NativeLinkInputs(List.of(), List.of(), List.of(tempDir.resolve("native/lib")), List.of(), List.of()),
            List.of()
        ))).isInstanceOf(IOException.class)
            .hasMessage("Static library link does not support library search paths");
    }

    @Test
    void staticLinkRejectsNamedLibrariesBeforeRunningCommand() {
        final NeverRunProcessRunner runner = new NeverRunProcessRunner();

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.a"),
            new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("math"), List.of()),
            List.of()
        ))).isInstanceOf(IOException.class)
            .hasMessage("Static library link does not support named libraries");
    }

    @Test
    void staticLinkRejectsFrameworksOnMacBeforeRunningCommand() {
        final NeverRunProcessRunner runner = new NeverRunProcessRunner();

        assertThatThrownBy(() -> withOsName("Mac OS X", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.a"),
            new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(), List.of("Cocoa")),
            List.of()
        ))).isInstanceOf(IOException.class)
            .hasMessage("Static library link does not support frameworks");
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
    void staticLinkFailsWhenConfiguredSourceCompilationFails() {
        final RecordingProcessRunner runner = new RecordingProcessRunner(
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(0, "", ""),
            new ProcessRunner.Result(1, "stdout", "stderr")
        );

        assertThatThrownBy(() -> withOsName("Linux", () -> new NativeLinker(runner).linkStaticLibrary(
            tempDir,
            tempDir.resolve("main.c"),
            tempDir.resolve("runtime.c"),
            tempDir.resolve("out/libdemo.a"),
            new NativeLinkInputs(List.of(tempDir.resolve("native/backend.c")), List.of(), List.of(), List.of(), List.of()),
            List.of()
        ))).isInstanceOf(IOException.class)
            .hasMessage("Native compile failed\nstderrstdout");
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

    private static List<List<String>> commandArguments(final List<List<String>> commands) {
        final List<List<String>> arguments = new ArrayList<>();
        for (final List<String> command : commands) {
            arguments.add(List.copyOf(command.subList(1, command.size())));
        }
        return List.copyOf(arguments);
    }

    private static Path output(final List<String> command) {
        final int output = command.lastIndexOf("-o");
        if (output < 0 || output + 1 >= command.size()) {
            throw new AssertionError("Expected compiler output argument");
        }
        return Path.of(command.get(output + 1));
    }

    private GeneratedSources generatedSources(final int unitCount) throws IOException {
        final Path generated = Files.createDirectories(tempDir.resolve("generated"));
        final Path units = Files.createDirectories(generated.resolve("units"));
        final Path runtimeHeader = Files.writeString(generated.resolve("javan_runtime.h"), "\n");
        final Path programHeader = Files.writeString(generated.resolve("javan_program.h"), "\n");
        final Path main = Files.writeString(generated.resolve("main.c"), "int main(void) { return 0; }\n");
        final List<Path> sources = new ArrayList<>();
        sources.add(main);
        for (int index = 0; index < unitCount; index++) {
            sources.add(Files.writeString(
                units.resolve("functions-0" + index + ".c"), "int function_" + index + "(void) { return " + index + "; }\n"
            ));
        }
        final Path runtime = Files.writeString(generated.resolve("javan_runtime.c"), "\n");
        return new GeneratedSources(List.copyOf(sources), List.of(programHeader, runtimeHeader), runtime);
    }

    private Path nativeOutput(final String name) {
        return tempDir.resolve("out").resolve(name + (System.getProperty("os.name", "").contains("Windows") ? ".exe" : ""));
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

    private static final class NeverRunProcessRunner extends ProcessRunner {
        @Override
        public ProcessRunner.Result run(final Path workingDirectory, final List<String> command) {
            throw new AssertionError("Link command must not run");
        }

        @Override
        public Optional<String> firstAvailable(final List<String> executables) {
            return executables.isEmpty() ? Optional.empty() : Optional.of(executables.getFirst());
        }
    }

    private record GeneratedSources(List<Path> programSources, List<Path> headers, Path runtime) {
    }

    private static class ObjectWritingProcessRunner extends ProcessRunner {
        private final AtomicInteger activeCompiles = new AtomicInteger();
        private final AtomicInteger peakCompiles = new AtomicInteger();
        private final CountDownLatch compilationGate;

        private ObjectWritingProcessRunner(final int parallelStarts) {
            this.compilationGate = new CountDownLatch(parallelStarts);
        }

        @Override
        public ProcessRunner.Result run(final Path workingDirectory, final List<String> command)
            throws IOException, InterruptedException {
            if (command.contains("--version")) {
                return new ProcessRunner.Result(0, "", "");
            }
            final Path output = output(command);
            if (command.contains("-c")) {
                final int active = activeCompiles.incrementAndGet();
                peakCompiles.accumulateAndGet(active, Math::max);
                compilationGate.countDown();
                try {
                    compilationGate.await(1, TimeUnit.SECONDS);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "object\n");
                } finally {
                    activeCompiles.decrementAndGet();
                }
            } else {
                Files.createDirectories(output.getParent());
                Files.writeString(output, "binary\n");
            }
            return new ProcessRunner.Result(0, "", "");
        }

        @Override
        public Optional<String> firstAvailable(final List<String> executables) {
            return executables.isEmpty() ? Optional.empty() : Optional.of(executables.getFirst());
        }

        protected final int peakCompiles() {
            return peakCompiles.get();
        }

    }

}
