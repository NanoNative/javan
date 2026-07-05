package javan.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
