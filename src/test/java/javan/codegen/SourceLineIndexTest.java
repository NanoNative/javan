package javan.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class SourceLineIndexTest {
    @TempDir
    private Path tempDir;

    @Test
    void resolvesPackageRelativeSourceLine() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path source = sourceRoot.resolve("com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new int[-1].length);
                }
            }
            """);

        final SourceLineIndex index = SourceLineIndex.from(List.of(sourceRoot));

        assertThat(index.line("com/acme/Main", Optional.of("Main.java"), Optional.of(5)))
            .contains("        System.out.println(new int[-1].length);");
    }

    @Test
    void returnsEmptyWhenLineNumberIsOutsideSourceFile() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path source = sourceRoot.resolve("com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.acme;\n");

        final SourceLineIndex index = SourceLineIndex.from(List.of(sourceRoot));

        assertThat(index.line("com/acme/Main", Optional.of("Main.java"), Optional.of(4))).isEmpty();
    }

    @Test
    void returnsEmptyWhenSourceFileIsMissing() throws Exception {
        final SourceLineIndex index = SourceLineIndex.empty();

        assertThat(index.line("com/acme/Main", Optional.empty(), Optional.of(1))).isEmpty();
    }

    @Test
    void returnsEmptyWhenLineNumberIsMissing() throws Exception {
        final SourceLineIndex index = SourceLineIndex.empty();

        assertThat(index.line("com/acme/Main", Optional.of("Main.java"), Optional.empty())).isEmpty();
    }

    @Test
    void returnsEmptyWhenLineNumberIsNotPositive() throws Exception {
        final SourceLineIndex index = SourceLineIndex.empty();

        assertThat(index.line("com/acme/Main", Optional.of("Main.java"), Optional.of(0))).isEmpty();
    }

    @Test
    void resolvesSourceLineByFileNameWhenPackagePathDiffers() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path source = sourceRoot.resolve("fallback/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Main {}\n");

        final SourceLineIndex index = SourceLineIndex.from(List.of(sourceRoot));

        assertThat(index.line("com/acme/Main", Optional.of("Main.java"), Optional.of(1)))
            .contains("class Main {}");
    }

    @Test
    void keepsFirstFileNameFallbackWhenSourcesShareName() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path first = sourceRoot.resolve("a/Main.java");
        final Path second = sourceRoot.resolve("b/Main.java");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.writeString(first, "class First {}\n");
        Files.writeString(second, "class Second {}\n");

        final SourceLineIndex index = SourceLineIndex.from(List.of(sourceRoot));

        assertThat(index.line("missing/Main", Optional.of("Main.java"), Optional.of(1)))
            .contains("class First {}");
    }

    @Test
    void parsesCarriageReturnLineEndings() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path source = sourceRoot.resolve("Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Main {}\rfinal class Other {}\r");

        final SourceLineIndex index = SourceLineIndex.from(List.of(sourceRoot));

        assertThat(index.line("Main", Optional.of("Main.java"), Optional.of(2)))
            .contains("final class Other {}");
    }

    @Test
    void parsesCarriageReturnLineFeedAsSingleLineEnding() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path source = sourceRoot.resolve("Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Main {}\r\nfinal class Other {}\n");

        final SourceLineIndex index = SourceLineIndex.from(List.of(sourceRoot));

        assertThat(index.line("Main", Optional.of("Main.java"), Optional.of(2)))
            .contains("final class Other {}");
    }
}
