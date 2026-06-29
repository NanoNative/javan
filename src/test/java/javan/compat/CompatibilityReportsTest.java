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
            List.of(
                metadata(
                    "java.base",
                    "java/lang/Object",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of(
                        member(0, "getClass", "()Ljava/lang/Class;", List.of(), List.of()),
                        member(0, "wait", "()V", List.of(), List.of())
                    )
                ),
                metadata(
                    "java.base",
                    "java/lang/Class",
                    0,
                    List.of(),
                    List.of(member(0, "forName", "(Ljava/lang/String;)Ljava/lang/Class;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/util/concurrent/Executors",
                    0,
                    List.of(),
                    List.of(member(0, "newSingleThreadExecutor", "()Ljava/util/concurrent/ExecutorService;", List.of(), List.of()))
                )
            ),
            List.of()
        );

        final String summary = Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"));

        assertThat(summary).contains(
            "\"exactSupportedJdkCallables\": {\"classes\": 1, \"constructors\": 1, \"methods\": 0, \"callables\": 1, \"totalCallables\": 5, \"leftCallables\": 4, \"coveragePercent\": \"20.0\"}",
            "\"exactJdkCallableAccounting\": {\"supportedCallables\": 1, \"explicitRejectedCallables\": 3, \"doneCallables\": 4, \"unknownCallables\": 1, \"totalCallables\": 5, \"donePercent\": \"80.0\"}",
            "\"supportRows\": 108",
            "\"passRows\": 107",
            "\"scopedRows\": 0",
            "\"targetRows\": 1",
            "\"rejectedRows\": 0",
            "\"accountedRows\": 107",
            "\"unaccountedRows\": 1"
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
            "| `try-catch` | pass |",
            "| `try-finally` | target |",
            "| `boxed-primitive-gc` | pass |",
            "| `enum-basic` | pass |",
            "| `enum-ordinal` | pass |",
            "| `enum-values` | pass |",
            "| `enum-switch` | pass |",
            "| `enum-value-of` | pass |",
            "| `interface-dispatch` | pass |",
            "| `polymorphic-virtual` | pass |",
            "| `interface-polymorphic` | pass |",
            "| `string-intrinsics` | pass |",
            "| `library-last-error-abi` | pass |",
            "| `library-c-result-wrapper-success` | pass |",
            "| `library-retained-input-ownership` | pass |",
            "| `library-negative-byte-array-rejection` | pass |",
            "| `hashmap-realloc-gc` | pass |",
            "| `list-of-varargs-gc` | pass |",
            "| `owned-buffer-realloc-validation` | pass |",
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
            "| `network-runtime-feature-reporting` | pass |",
            "| `typemap-pair` | pass |",
            "| `nano-metric` | pass |",
            "| `nano-duration` | pass |"
        );
        assertThat(json).contains(
            "\"generatedForJdk\": " + feature,
            "\"feature\": \"try-catch\"",
            "\"feature\": \"try-finally\"",
            "\"feature\": \"boxed-primitive-gc\"",
            "\"feature\": \"enum-value-of\"",
            "\"feature\": \"interface-dispatch\"",
            "\"feature\": \"polymorphic-virtual\"",
            "\"feature\": \"interface-polymorphic\"",
            "\"feature\": \"string-intrinsics\"",
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
            "\"feature\": \"network-runtime-feature-reporting\"",
            "\"feature\": \"typemap-pair\"",
            "\"feature\": \"nano-metric\"",
            "\"feature\": \"nano-duration\""
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

    @Test
    void writeJdkCompatibilityMarkdownShowsExplicitRejectedAndUnknownCallableCounts() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(
                metadata(
                    "java.base",
                    "java/lang/Object",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of(
                        member(0, "getClass", "()Ljava/lang/Class;", List.of(), List.of()),
                        member(0, "wait", "(J)V", List.of(), List.of())
                    )
                ),
                metadata(
                    "java.base",
                    "java/lang/Class",
                    0,
                    List.of(),
                    List.of(member(0, "forName", "(Ljava/lang/String;)Ljava/lang/Class;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/util/concurrent/Executors",
                    0,
                    List.of(),
                    List.of(member(0, "newCachedThreadPool", "()Ljava/util/concurrent/ExecutorService;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/lang/InheritableThreadLocal",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of()
                ),
                metadata(
                    "java.base",
                    "java/lang/String",
                    0,
                    List.of(),
                    List.of(member(0, "valueOf", "(I)Ljava/lang/String;", List.of(), List.of()))
                )
            ),
            List.of()
        );

        assertThat(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"))).contains(
            "| exact supported JDK callables | 2 / 7 (28.5%) |",
            "| exact explicit rejected JDK callables | 4 |",
            "| exact done JDK callables | 6 / 7 (85.7%) |",
            "| exact unknown JDK callables | 1 |",
            "| exact supported JDK callables left | 5 |"
        );
    }

    @Test
    void writeSummaryReportsFlowQualifiedRejectedJdkShapesSeparatelyFromExactMemberAccounting() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(metadata("java.base", "java/lang/Object")),
            List.of(
                Diagnostic.error("JAVAN075", "lifecycle", "com/acme/Main", "run()V", "Thread.currentThread().start()", "reason", "fix"),
                Diagnostic.warning("JAVAN175", "lifecycle", "com/acme/Main", "run()V", "duplicate Thread.start() on local 1", "reason", "fix"),
                Diagnostic.error("JAVAN077", "concurrency", "com/acme/Main", "run()V", "Thread.Builder.start(Runnable)", "reason", "fix"),
                Diagnostic.warning("JAVAN177", "concurrency", "com/acme/Main", "run()V", "Thread.Builder.factory()", "reason", "fix"),
                Diagnostic.error("JAVAN077", "concurrency", "com/acme/Main", "run()V", "Thread.ofVirtual()", "reason", "fix"),
                Diagnostic.warning("JAVAN177", "concurrency", "com/acme/Main", "run()V", "Thread.Builder.OfVirtual.factory()", "reason", "fix"),
                Diagnostic.error("JAVAN077", "concurrency", "com/acme/Main", "run()V", "Executor.execute(Runnable)", "reason", "fix"),
                Diagnostic.warning("JAVAN177", "concurrency", "com/acme/Main", "run()V", "ExecutorService.close()", "reason", "fix"),
                Diagnostic.error("JAVAN076", "sync", "com/acme/Main", "run()V", "Object.wait()", "reason", "fix")
            )
        );

        assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"))).contains(
            "\"flowQualifiedRejectedJdkCalls\": {\"reachableCurrentThreadLifecycle\": 1, \"unreachableCurrentThreadLifecycle\": 1, \"reachableThreadBuilderReceiverShape\": 1, \"unreachableThreadBuilderReceiverShape\": 1, \"reachableVirtualThreadFactoryShape\": 1, \"unreachableVirtualThreadFactoryShape\": 1, \"reachableExecutorReceiverShape\": 1, \"unreachableExecutorReceiverShape\": 1, \"total\": 8}",
            "\"jdkCoverageAccounting\": {\"implemented\": true, \"complete\": false, \"scope\": \"exact-member-baseline-plus-flow-qualified-diagnostics\""
        );
        assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.md"))).contains(
            "- flow-qualified reachable current-thread lifecycle rejects: `1`",
            "- flow-qualified unreachable current-thread lifecycle rejects: `1`",
            "- flow-qualified reachable thread-builder receiver-shape rejects: `1`",
            "- flow-qualified unreachable thread-builder receiver-shape rejects: `1`",
            "- flow-qualified reachable virtual-thread factory-shape rejects: `1`",
            "- flow-qualified unreachable virtual-thread factory-shape rejects: `1`",
            "- flow-qualified reachable executor receiver-shape rejects: `1`",
            "- flow-qualified unreachable executor receiver-shape rejects: `1`",
            "- flow-qualified rejected JDK call shapes total: `8`"
        );
        assertThat(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"))).contains(
            "| flow-qualified reachable current-thread lifecycle rejects | 1 |",
            "| flow-qualified unreachable current-thread lifecycle rejects | 1 |",
            "| flow-qualified reachable thread-builder receiver-shape rejects | 1 |",
            "| flow-qualified unreachable thread-builder receiver-shape rejects | 1 |",
            "| flow-qualified reachable virtual-thread factory-shape rejects | 1 |",
            "| flow-qualified unreachable virtual-thread factory-shape rejects | 1 |",
            "| flow-qualified reachable executor receiver-shape rejects | 1 |",
            "| flow-qualified unreachable executor receiver-shape rejects | 1 |",
            "| flow-qualified rejected JDK call shapes total | 8 |"
        );
    }

    @Test
    void writeSummaryCountsDeliberateOwnerFamilyRejectionsInExactCallableAccounting() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(
                metadata(
                    "java.base",
                    "java/lang/Object",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of()
                ),
                metadata(
                    "jdk.jfr",
                    "jdk/jfr/FlightRecorder",
                    0,
                    List.of(),
                    List.of(member(0, "isAvailable", "()Z", List.of(), List.of()))
                ),
                metadata(
                    "jdk.unsupported",
                    "sun/misc/Unsafe",
                    0,
                    List.of(),
                    List.of(member(0, "getUnsafe", "()Lsun/misc/Unsafe;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/lang/String",
                    0,
                    List.of(),
                    List.of(member(0, "valueOf", "(I)Ljava/lang/String;", List.of(), List.of()))
                )
            ),
            List.of()
        );

        assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"))).contains(
            "\"exactSupportedJdkCallables\": {\"classes\": 2, \"constructors\": 1, \"methods\": 1, \"callables\": 2, \"totalCallables\": 4, \"leftCallables\": 2, \"coveragePercent\": \"50.0\"}",
            "\"exactJdkCallableAccounting\": {\"supportedCallables\": 2, \"explicitRejectedCallables\": 2, \"doneCallables\": 4, \"unknownCallables\": 0, \"totalCallables\": 4, \"donePercent\": \"100.0\"}"
        );
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
