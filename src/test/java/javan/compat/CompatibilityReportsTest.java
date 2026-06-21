package javan.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class CompatibilityReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeOrdersJdkInventoryClassesByName() throws Exception {
        final List<ClassMetadata> jdkClasses = new ArrayList<>();
        for (int index = 25; index >= 0; index--) {
            jdkClasses.add(metadata("java.base", "java/lang/C" + twoDigits(index)));
        }

        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            jdkClasses,
            List.of()
        );

        final int feature = Runtime.version().feature();
        final String inventory = Files.readString(tempDir.resolve(".javan/reports/jdk-" + feature + "-inventory.json"));
        assertThat(inventory.indexOf("\"name\": \"java/lang/C00\""))
            .isLessThan(inventory.indexOf("\"name\": \"java/lang/C25\""));
    }

    @Test
    void writeSummaryCountsSupportRowsByStatus() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(metadata("java.base", "java/lang/Object")),
            List.of()
        );

        final String summary = Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"));

        assertThat(summary).contains(
            "\"supportRows\": ",
            "\"passRows\": ",
            "\"scopedRows\": ",
            "\"targetRows\": "
        );
    }

    @Test
    void writeSupportMatrixIncludesEvidenceBackedMemoryAndLibraryRows() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(metadata("java.base", "java/lang/Object")),
            List.of()
        );

        final int feature = Runtime.version().feature();
        final String matrix = Files.readString(tempDir.resolve("doc/status/support-matrix.md"));
        final String json = Files.readString(tempDir.resolve("doc/status/support-matrix.json"));

        assertThat(matrix).contains(
            "| `boxed-primitive-gc` | scoped |",
            "| `library-c-result-wrapper-success` | pass |",
            "| `library-retained-input-ownership` | pass |",
            "| `library-negative-byte-array-rejection` | pass |",
            "| `hashmap-realloc-gc` | scoped |",
            "| `list-of-varargs-gc` | scoped |",
            "| `owned-buffer-realloc-validation` | scoped |",
            "| `network-address-runtime` | pass |",
            "| `network-tcp-client-socket` | pass |",
            "| `network-tcp-server-socket` | pass |",
            "| `network-tcp-socket-stream-io` | pass |",
            "| `network-http-client-get-string` | pass |",
            "| `network-http-client-post-string-byte-array` | pass |",
            "| `network-http-client-put-byte-array` | pass |",
            "| `platform-thread-construction` | pass |",
            "| `platform-thread-current-interrupt-state` | scoped |",
            "| `platform-thread-sleep-uninterrupted` | pass |",
            "| `network-socket-rejection` | pass |",
            "| `network-http-rejection` | pass |",
            "| `network-runtime-feature-reporting` | pass |"
        );
        assertThat(json).contains(
            "\"generatedForJdk\": " + feature,
            "\"feature\": \"boxed-primitive-gc\"",
            "\"feature\": \"library-c-result-wrapper-success\"",
            "\"feature\": \"network-address-runtime\"",
            "\"feature\": \"network-tcp-client-socket\"",
            "\"feature\": \"network-tcp-server-socket\"",
            "\"feature\": \"network-tcp-socket-stream-io\"",
            "\"feature\": \"network-http-client-get-string\"",
            "\"feature\": \"network-http-client-post-string-byte-array\"",
            "\"feature\": \"network-http-client-put-byte-array\"",
            "\"feature\": \"platform-thread-construction\"",
            "\"feature\": \"platform-thread-current-interrupt-state\"",
            "\"feature\": \"platform-thread-sleep-uninterrupted\"",
            "\"feature\": \"network-socket-rejection\"",
            "\"feature\": \"network-http-rejection\"",
            "\"feature\": \"network-runtime-feature-reporting\""
        );
    }

    private static ClassMetadata metadata(final String moduleName, final String className) {
        return new ClassMetadata(
            Path.of(className + ".class"),
            true,
            moduleName,
            0,
            69,
            0,
            className,
            "java/lang/Object",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private static String twoDigits(final int value) {
        if (value < 10) {
            return "0" + value;
        }
        return Integer.toString(value);
    }
}
