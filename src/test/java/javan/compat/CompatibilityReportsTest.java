package javan.compat;

import javan.verify.Diagnostic;
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
            "\"supportRows\": 105",
            "\"passRows\": 89",
            "\"scopedRows\": 14",
            "\"targetRows\": 2"
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
            "| `platform-thread-empty-start-join` | pass |",
            "| `platform-thread-runnable-start-join-single-threaded` | pass |",
            "| `platform-thread-current-interrupt-state` | pass |",
            "| `platform-thread-current-thread-root-gc-pressure` | pass |",
            "| `platform-thread-runnable-target-root-gc-pressure` | pass |",
            "| `platform-thread-current-thread-inventory` | pass |",
            "| `platform-thread-live-root-registry` | pass |",
            "| `platform-thread-finished-thread-reclaim` | pass |",
            "| `platform-thread-sleep-uninterrupted` | pass |",
            "| `platform-thread-sleep-entry-interrupted-same-method-catch` | pass |",
            "| `platform-thread-join-entry-interrupted-same-method-catch` | pass |",
            "| `platform-thread-current-thread-start-build-reject` | pass |",
            "| `platform-thread-current-thread-join-build-reject` | pass |",
            "| `platform-thread-duplicate-start-build-reject` | pass |",
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
            "\"feature\": \"platform-thread-empty-start-join\"",
            "\"feature\": \"platform-thread-runnable-start-join-single-threaded\"",
            "\"feature\": \"platform-thread-current-interrupt-state\"",
            "\"feature\": \"platform-thread-current-thread-root-gc-pressure\"",
            "\"feature\": \"platform-thread-runnable-target-root-gc-pressure\"",
            "\"feature\": \"platform-thread-current-thread-inventory\"",
            "\"feature\": \"platform-thread-live-root-registry\"",
            "\"feature\": \"platform-thread-finished-thread-reclaim\"",
            "\"feature\": \"platform-thread-sleep-uninterrupted\"",
            "\"feature\": \"platform-thread-sleep-entry-interrupted-same-method-catch\"",
            "\"feature\": \"platform-thread-join-entry-interrupted-same-method-catch\"",
            "\"feature\": \"platform-thread-current-thread-start-build-reject\"",
            "\"feature\": \"platform-thread-current-thread-join-build-reject\"",
            "\"feature\": \"platform-thread-duplicate-start-build-reject\"",
            "\"feature\": \"network-socket-rejection\"",
            "\"feature\": \"network-http-rejection\"",
            "\"feature\": \"network-runtime-feature-reporting\""
        );
    }

    @Test
    void writeUsesLegacyJavaVersionPrefixForFeatureDetection() throws Exception {
        withJavaVersion("1.8.0_442", () -> {
            new CompatibilityReports().write(
                tempDir,
                tempDir.resolve(".javan"),
                List.of(metadata("", "com/acme/Main")),
                List.of(metadata("java.base", "java/lang/Object")),
                List.of()
            );

            assertThat(tempDir.resolve(".javan/reports/jdk-8-inventory.json")).isRegularFile();
            assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json")))
                .contains("\"javaFeatureVersion\": 8");
        });
    }

    @Test
    void writeUsesZeroFeatureForBlankJavaVersion() throws Exception {
        withJavaVersion(" ", () -> {
            new CompatibilityReports().write(
                tempDir,
                tempDir.resolve(".javan"),
                List.of(metadata("", "com/acme/Main")),
                List.of(metadata("java.base", "java/lang/Object")),
                List.of()
            );

            assertThat(tempDir.resolve(".javan/reports/jdk-0-inventory.json")).isRegularFile();
            assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json")))
                .contains("\"javaFeatureVersion\": 0");
        });
    }

    @Test
    void writeStopsFeatureParsingAtSuffixAndCountsPreviewSyntheticAndErrors() throws Exception {
        withJavaVersion("25-ea", () -> {
            final List<ClassMetadata> projectClasses = List.of(
                metadata(
                    "",
                    "com/acme/Preview",
                    65_535,
                    List.of(
                        member(
                            0x1000,
                            "<init>",
                            "()V",
                            List.of("Synthetic"),
                            List.of(new InstructionMetadata(0, 197, "multianewarray", 3, BytecodeSupport.Status.RECOGNIZED_REJECTED))
                        )
                    ),
                    List.of(
                        member(
                            0x1000,
                            "bridge",
                            "()V",
                            List.of(),
                            List.of(new InstructionMetadata(1, 255, "opcode_255", 0, BytecodeSupport.Status.UNKNOWN_FATAL))
                        )
                    )
                )
            );
            final List<ClassMetadata> jdkClasses = List.of(
                metadata("java.logging", "java/util/logging/Logger"),
                metadata("java.base", "java/lang/Object"),
                metadata("java.base", "java/lang/String")
            );

            new CompatibilityReports().write(
                tempDir,
                tempDir.resolve(".javan"),
                projectClasses,
                jdkClasses,
                List.of(
                    Diagnostic.error("JAVAN999", "fatal", "com/acme/Preview", "bridge()V", "opcode_255", "reason", "fix"),
                    Diagnostic.warning("JAVAN199", "warning", "com/acme/Preview", "<init>()V", "multianewarray", "reason", "fix")
                )
            );

            assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"))).contains(
                "\"javaFeatureVersion\": 25",
                "\"diagnosticErrors\": 1",
                "\"recognizedRejectedOpcodeUses\": 1",
                "\"unknownFatalOpcodeUses\": 1",
                "\"status\": \"fail\""
            );
            assertThat(Files.readString(tempDir.resolve(".javan/reports/bytecode-patterns-jdk-25.json"))).contains(
                "\"previewClasses\": [\"com/acme/Preview\"]",
                "\"syntheticMethods\": [\"com/acme/Preview.<init>()V\", \"com/acme/Preview.bridge()V\"]"
            );
            assertThat(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"))).contains(
                "- JDK modules: `2`"
            );
        });
    }

    private static ClassMetadata metadata(final String moduleName, final String className) {
        return metadata(moduleName, className, 0, List.of(), List.of());
    }

    private static ClassMetadata metadata(
        final String moduleName,
        final String className,
        final int minorVersion,
        final List<MemberMetadata> constructors,
        final List<MemberMetadata> methods
    ) {
        return new ClassMetadata(
            Path.of(className + ".class"),
            true,
            moduleName,
            minorVersion,
            69,
            0,
            className,
            "java/lang/Object",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            constructors,
            methods
        );
    }

    private static String twoDigits(final int value) {
        if (value < 10) {
            return "0" + value;
        }
        return Integer.toString(value);
    }

    private static MemberMetadata member(
        final int accessFlags,
        final String name,
        final String descriptor,
        final List<String> attributes,
        final List<InstructionMetadata> instructions
    ) {
        return new MemberMetadata(accessFlags, name, descriptor, attributes, instructions);
    }

    private static void withJavaVersion(final String value, final ThrowingRunnable runnable) throws Exception {
        final String previous = System.getProperty("java.version");
        try {
            System.setProperty("java.version", value);
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty("java.version");
            } else {
                System.setProperty("java.version", previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
