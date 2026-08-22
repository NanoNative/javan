package javan;

import javan.analysis.CallGraph;
import javan.analysis.CallEdge;
import javan.analysis.EntryPoint;
import javan.codegen.NativeLinker;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ProjectReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeProjectDefaultsProfileToCore() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeProject(layout);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/project.json")))
            .contains("\"profile\": \"core\"");
    }

    @Test
    void writeProjectEscapesWarningControlCharacters() throws Exception {
        final ProjectLayout layout = layout(List.of("quote \" slash \\ newline\nreturn\rtab\t"));

        new ProjectReports().writeProject(layout, Profile.STRICT);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/project.json")))
            .contains("\"warnings\": [\"quote \\\" slash \\\\ newline\\nreturn\\rtab\\t\"]");
    }

    @Test
    void writeProjectSeparatesMultipleListEntries() throws Exception {
        final ProjectLayout layout = layout(List.of("first", "second"));

        new ProjectReports().writeProject(layout, Profile.SERVICE);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/project.json")))
            .contains("\"warnings\": [\"first\", \"second\"]");
    }

    @Test
    void writeToolchainReportsTargetCompilerAndDecision() throws Exception {
        final ProjectLayout layout = layout(List.of());
        final NativeLinker.Toolchain toolchain = new NativeLinker.Toolchain(
            "linux-x64", "linux-x64", "incompatible", List.of("cc", "clang"),
            Optional.of("/usr/bin/cc"), "unsupported \"flag\""
        );

        new ProjectReports().writeToolchain(layout, toolchain);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/toolchain.json")))
            .contains(
                "\"hostTarget\": \"linux-x64\"",
                "\"decision\": \"incompatible\"",
                "\"compilerStatus\": \"available\"",
                "\"detail\": \"unsupported \\\"flag\\\"\""
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/toolchain.md")))
            .contains("- requested target: `linux-x64`", "- compiler: `/usr/bin/cc`");
    }

    @Test
    void writeReachabilityWritesDeterministicMachineReadableAndVisualCallGraph() throws Exception {
        final ProjectLayout layout = layout(List.of());
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint alpha = new EntryPoint("com/acme/Alpha", "call", "()V");
        final EntryPoint zed = new EntryPoint("com/acme/Zed", "call", "()V");
        final CallGraph graph = new CallGraph(
            main,
            List.of(main, zed, alpha),
            List.of(),
            List.of(
                new CallEdge(alpha, zed, CallEdge.Kind.CLASS_INITIALIZER),
                new CallEdge(main, alpha, CallEdge.Kind.CALL),
                new CallEdge(zed, main, CallEdge.Kind.CALL)
            )
        );

        new ProjectReports().writeReachability(layout, graph);

        final String report = Files.readString(layout.outputDirectory().resolve("reports/reachability.txt"));
        assertThat(report.indexOf("com/acme/Alpha.call()V")).isLessThan(report.indexOf("com/acme/Zed.call()V"));
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.json")))
            .contains(
                "\"schemaVersion\": 1",
                "\"completeness\": \"closed-world-proven\"",
                "\"entryPoint\": \"com/acme/Main.main([Ljava/lang/String;)V\"",
                "\"entryPointLabel\": \"Main.main(String[])\"",
                "\"reachableMethods\": 3",
                "\"edgeCount\": 3",
                "\"kind\": \"call\"",
                "\"label\": \"calls\"",
                "\"kind\": \"class-initializer\""
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.md")))
            .contains(
                "# Call Graph",
                "- reachable methods: `3`",
                "- edges: `3`",
                "[open the call flow in a browser](call-flow.html)",
                "[Graphviz DOT](call-graph.dot)",
                "`Main.main(String[])` calls `Alpha.call()`",
                "unresolved or dynamic targets remain diagnostics"
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-flow.html")))
            .contains(
                "<title>Javan Call Flow</title>",
                "Static Java call flow",
                "Main",
                "main(String[])",
                "class initialization",
                "Javan proved 3 reachable methods and 3 connections",
                "class=\"edge initialize\"",
                "M 942 63 C 998 119 90 119 34 63"
            )
            .doesNotContain("com/acme/Main.main([Ljava/lang/String;)V", "<script", "http://", "https://");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.dot")))
            .contains(
                "digraph javan_call_graph",
                "\"com/acme/Main.main([Ljava/lang/String;)V\" -> \"com/acme/Alpha.call()V\"",
                "label=\"Main.main(String[])\"",
                "label=\"initializes\"",
                "style=dashed"
            );
    }

    @Test
    void writeReachabilityMarksAnEmptyGraphNotAnalyzed() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeReachability(layout, new CallGraph(new EntryPoint("", "", ""), List.of(), List.of()));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.json")))
            .contains("\"completeness\": \"not-analyzed\"", "No reachable code was analyzed");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.md")))
            .contains("- entry point: `not available`");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-flow.html")))
            .contains("No visual flow is available", "valid application entry point");
    }

    @Test
    void writeDiagnosticsRefreshesEveryCallGraphOutputFromTheFinalFindingSet() throws Exception {
        final ProjectLayout layout = layout(List.of());
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint helper = new EntryPoint("com/acme/Main", "helper", "()V");
        final CallGraph graph = new CallGraph(main, List.of(main, helper), List.of(), List.of(new CallEdge(main, helper)));
        final List<Diagnostic> diagnostics = List.of(
            Diagnostic.error("JAVAN001", "unsupported main call", "com/acme/Main", "main([Ljava/lang/String;)V", "call", "unsupported", "replace it"),
            Diagnostic.warning("JAVAN101", "helper needs review", "com/acme/Main", "helper()V", "call", "risk", "review it"),
            Diagnostic.error("JAVAN002", "not reachable from this graph", "com/acme/Other", "other()V", "call", "unsupported", "replace it")
        );

        final ProjectReports reports = new ProjectReports();
        reports.writeReachability(layout, graph);
        reports.writeDiagnostics(layout, diagnostics, Map.of(), graph);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.json")))
            .contains(
                "\"diagnostics\": 3",
                "\"errors\": 2",
                "\"warnings\": 1",
                "\"methodsWithFindings\": 2",
                "\"diagnosticsOutsideFlow\": 1",
                "\"codes\": [\"JAVAN001\"]",
                "\"codes\": [\"JAVAN101\"]"
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.md")))
            .contains(
                "## Static Findings",
                "- errors: `2`",
                "- warnings: `1`",
                "`Main.main(String[])`: error `[JAVAN001]` unsupported main call",
                "### Outside Current Flow",
                "error `[JAVAN002]` not reachable from this graph"
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-flow.html")))
            .contains(
                "class=\"node entry error\"",
                "class=\"node warning\"",
                "[JAVAN001]",
                "Outside current flow - error",
                "Open all diagnostic details"
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/call-graph.dot")))
            .contains("fillcolor=\"#fef2f2\"", "fillcolor=\"#fffbeb\"", "JAVAN001", "JAVAN101");
    }

    @Test
    void writeDiagnosticsWritesEmptyReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of());

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.txt")))
            .isEqualTo("No diagnostics." + System.lineSeparator());
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.json")))
            .contains("\"diagnostics\": 0", "\"errors\": 0", "\"warnings\": 0", "\"items\": [");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.md")))
            .contains("- diagnostics: `0`", "No diagnostics.");
    }

    @Test
    void writeDiagnosticsWritesEmptyThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of());

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.json")))
            .contains(
                "\"diagnostics\": 0",
                "\"errors\": 0",
                "\"warnings\": 0",
                "\"lifecycle\": 0",
                "\"synchronization\": 0",
                "\"concurrencyRuntime\": 0",
                "\"blocking\": 0",
                "\"threadStartSites\": 0",
                "\"threadStartMethods\": 0",
                "\"lifecycleMethods\": 0",
                "\"blockingMethods\": 0",
                "\"synchronizationMethods\": 0",
                "\"concurrencyRuntimeMethods\": 0",
                "\"unknownBlockingMethods\": 0",
                "\"unsupportedThreadTaskMethods\": 0",
                "\"sleepWaits\": 0",
                "\"joinWaits\": 0",
                "\"blockingTaskMethods\": 0",
                "\"cpuBoundTaskMethods\": 0",
                "\"tinyCpuTaskMethods\": 0",
                "\"pinningRiskMethods\": 0",
                "\"unknownTaskMethods\": 0",
                "\"ioSignalMethods\": 0",
                "\"taskRoots\": 0",
                "\"threadStartRoots\": 0",
                "\"blockingRoots\": 0",
                "\"pinningRiskRoots\": 0",
                "\"unsupportedRuntimeRoots\": 0",
                "\"lifecycleRiskRoots\": 0",
                "\"unknownRoots\": 0",
                "\"methods\": ["
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.md")))
            .contains("# Thread Analysis", "No thread diagnostics.");
    }

    @Test
    void writeDiagnosticsWritesVirtualThreadStatusReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of());

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/virtual-threads.json")))
            .contains(
                "\"status\": \"partial\"",
                "\"runtimeSupported\": true",
                "\"profilingSupported\": false",
                "\"profilingCollected\": false",
                "\"schedulerImplemented\": false",
                "\"carrierPoolImplemented\": false",
                "\"threadModelImplemented\": true",
                "\"threadLocalImplemented\": true",
                "\"blockingIoAware\": false",
                "\"reachableApiScan\": \"not-collected\"",
                "\"reachableVirtualStartSites\": 0",
                "\"reachableVirtualStartMethods\": 0",
                "\"reachableIsVirtualSites\": 0",
                "\"unsupportedBuilderApis\": 0",
                "\"unsupportedBuilderApisReachable\": 0",
                "\"unsupportedBuilderApisUnreachable\": 0",
                "\"unsupportedExecutorApis\": 0",
                "\"unsupportedExecutorApisReachable\": 0",
                "\"unsupportedExecutorApisUnreachable\": 0",
                "\"diagnosticSource\": \"platform-thread-analysis-plus-virtual-builder-executor-park-slice\"",
                "\"reasonCount\": 3",
                "\"reasons\": ["
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/virtual-threads.md")))
            .contains(
                "# Virtual Thread Analysis",
                "- status: `partial`",
                "- runtimeSupported: `true`",
                "- profilingCollected: `false`",
                "- reachableApiScan: `not-collected`",
                "- reachableVirtualStartSites: `0`",
                "- reachableVirtualStartMethods: `0`",
                "- reachableIsVirtualSites: `0`",
                "- unsupportedBuilderApis: `0`",
                "- unsupportedBuilderApisReachable: `0`",
                "- unsupportedBuilderApisUnreachable: `0`",
                "- unsupportedExecutorApis: `0`",
                "- unsupportedExecutorApisReachable: `0`",
                "- unsupportedExecutorApisUnreachable: `0`",
                "## Reasons"
            );
    }

    @Test
    void writeDiagnosticsPromotesVirtualThreadProfilingSupportWhenRuntimeProfilingIsReady() throws Exception {
        final ProjectLayout layout = layout(List.of());
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        Files.writeString(layout.outputDirectory().resolve("reports/runtime-profiling.json"), """
            {
              "status": "ready",
              "requested": true,
              "enabled": true,
              "collectionState": "linked-not-run"
            }
            """);

        new ProjectReports().writeDiagnostics(layout, List.of());

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/virtual-threads.json")))
            .contains(
                "\"profilingSupported\": true",
                "\"profilingCollected\": false",
                "\"reachableApiScan\": \"not-collected\""
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/virtual-threads.md")))
            .contains(
                "- profilingSupported: `true`",
                "- profilingCollected: `false`",
                "Virtual-thread profiling hooks are linked through runtime-profiling.*, but the current run has not collected counters yet."
            );
    }

    @Test
    void writeDiagnosticsSeparatesMultipleDiagnostics() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error("JAVAN001", "first", "com/acme/Main", "main", "a", "because", "fix"),
            Diagnostic.warning("JAVAN101", "second", "com/acme/Main", "main", "b", "maybe", "fix")
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.txt")))
            .contains("error[JAVAN001]: first")
            .contains("Fix:" + System.lineSeparator() + "  fix" + System.lineSeparator()
                + System.lineSeparator() + "warning[JAVAN101]: second");
    }

    @Test
    void writeDiagnosticsWritesMachineReadableReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error(
                "JAVAN001",
                "quote \" unsafe",
                "com/acme/Main",
                "main",
                "java/lang/Class.forName",
                "dynamic class loading is not supported",
                "Use direct static references."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.json")))
            .contains(
                "\"schemaVersion\": 1",
                "\"severity\": \"error\"",
                "\"code\": \"JAVAN001\"",
                "\"message\": \"quote \\\" unsafe\"",
                "\"class\": \"com/acme/Main\"",
                "\"subject\": \"java/lang/Class.forName\"",
                "\"reason\": \"dynamic class loading is not supported\"",
                "\"fix\": \"Use direct static references.\""
            );
    }

    @Test
    void writeDiagnosticsWritesReadableMarkdownReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.warning(
                "JAVAN101",
                "unsupported API in unreachable code",
                "com/acme/Main",
                "unused",
                "java/lang/Class.forName",
                "unreachable reflection is ignored for native code generation",
                "Remove it when possible."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.md")))
            .contains(
                "# Diagnostics",
                "- warnings: `1`",
                "## warning[JAVAN101] unsupported API in unreachable code",
                "- class: `com/acme/Main`",
                "- method: `unused`",
                "- subject: `java/lang/Class.forName`",
                "- reason: unreachable reflection is ignored for native code generation",
                "- fix: Remove it when possible."
            );
    }

    @Test
    void writeDiagnosticsFiltersThreadDiagnosticsIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error(
                "JAVAN077",
                "unsupported reachable concurrency runtime API",
                "com/acme/Main",
                "main",
                "Executors.newCachedThreadPool()",
                "The current native runtime does not implement this broader executor, scheduler, or concurrent-runtime API surface yet.",
                "Keep this concurrency API on the JVM, or wait until Javan's broader scheduler and virtual-thread runtime lands."
            ),
            Diagnostic.warning(
                "JAVAN101",
                "unsupported API in unreachable code",
                "com/acme/Main",
                "unused",
                "java/lang/Class.forName",
                "unreachable reflection is ignored for native code generation",
                "Remove it when possible."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.json")))
            .contains("\"diagnostics\": 1", "\"concurrencyRuntime\": 1", "\"code\": \"JAVAN077\"", "\"category\": \"concurrency-runtime\"", "\"subject\": \"Executors.newCachedThreadPool()\"")
            .doesNotContain("JAVAN101");
    }

    @Test
    void writeDiagnosticsWritesLifecycleThreadDiagnosticIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error(
                "JAVAN075",
                "unsupported reachable thread lifecycle",
                "com/acme/Main",
                "main",
                "Thread.currentThread().start()",
                "The native runtime models the current thread as already started. Starting it again reaches the duplicate-start runtime panic instead of a supported thread runtime.",
                "Do not call Thread.start() on Thread.currentThread(); start a separate Thread instance or keep this flow on the JVM until real parallel platform-thread support lands."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.json")))
            .contains(
                "\"diagnostics\": 1",
                "\"lifecycle\": 1",
                "\"lifecycleMethods\": 1",
                "\"unknownBlockingMethods\": 0",
                "\"unsupportedThreadTaskMethods\": 1",
                "\"unknownTaskMethods\": 1",
                "\"taskRoots\": 1",
                "\"lifecycleRiskRoots\": 1",
                "\"lifecycleRisks\": 1",
                "\"classification\": \"UNKNOWN\"",
                "\"rootKind\": \"LIFECYCLE_RISK\"",
                "\"code\": \"JAVAN075\"",
                "\"category\": \"lifecycle\""
            );
    }

    @Test
    void writeDiagnosticsWritesUnreachableLifecycleThreadWarningIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.warning(
                "JAVAN175",
                "unsupported thread lifecycle in unreachable code",
                "com/acme/Main",
                "dead",
                "Thread.currentThread().join()",
                "Joining the current thread has no supported native runtime model and currently reaches the explicit self-join runtime panic.",
                "Remove self-join logic, join a different Thread instance, or keep this flow on the JVM until broader platform-thread support lands."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.md")))
            .contains(
                "- diagnostics: `1`",
                "- warnings: `1`",
                "- lifecycle: `1`",
                "- lifecycleMethods: `1`",
                "- unknownBlockingMethods: `0`",
                "- unsupportedThreadTaskMethods: `1`",
                "- unknownTaskMethods: `1`",
                "- ioSignalMethods: `0`",
                "- taskRoots: `1`",
                "- lifecycleRiskRoots: `1`",
                "## Task Roots",
                "`com/acme/Main#dead`: rootKind=`LIFECYCLE_RISK`, classification=`UNKNOWN`, threadStartSites=`0`, blockingWaits=`0`, lifecycleRisks=`1`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
                "## Reachable Thread Methods",
                "`com/acme/Main#dead`: threadStartSites=`0`, lifecycleRisks=`1`, blockingWaits=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, sleepWaits=`0`, joinWaits=`0`, estimatedInstructions=`0`, allocationSites=`0`, ioCallSites=`0`, hasLoop=`false`, classification=`UNKNOWN`",
                "## warning[JAVAN175] unsupported thread lifecycle in unreachable code",
                "- category: `lifecycle`"
            );
    }

    @Test
    void writeDiagnosticsWritesReachableSynchronizationThreadDiagnosticIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error(
                "JAVAN076",
                "unsupported reachable synchronization",
                "com/acme/Main",
                "main",
                "Object.wait()",
                "The current native runtime does not implement Java monitor wait/notify semantics, ownership checks, parking, wake-up ordering, or interruption behavior for Object monitor methods.",
                "Keep Object.wait/notify code on the JVM, or wait until Javan's broader platform-thread and monitor runtime lands."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.json")))
            .contains(
                "\"diagnostics\": 1",
                "\"errors\": 1",
                "\"synchronization\": 1",
                "\"lifecycleMethods\": 0",
                "\"unknownBlockingMethods\": 1",
                "\"unsupportedThreadTaskMethods\": 1",
                "\"pinningRiskMethods\": 1",
                "\"taskRoots\": 1",
                "\"pinningRiskRoots\": 1",
                "\"synchronizationMethods\": 1",
                "\"concurrencyRuntimeMethods\": 0",
                "\"lifecycleRisks\": 0",
                "\"synchronizationRisks\": 1",
                "\"classification\": \"PINNING_RISK\"",
                "\"rootKind\": \"PINNING_RISK\"",
                "\"code\": \"JAVAN076\"",
                "\"category\": \"synchronization\""
            );
    }

    @Test
    void writeDiagnosticsWritesSynchronizationThreadWarningIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.warning(
                "JAVAN176",
                "unsupported synchronization in unreachable code",
                "com/acme/Main",
                "dead",
                "synchronized method",
                "The current native runtime does not implement Java monitor enter/exit semantics, lock ownership, or the broader parallel-thread model required for synchronized methods.",
                "Remove synchronized from this method, keep this flow on the JVM, or wait until Javan's broader platform-thread and monitor support lands."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.md")))
            .contains(
                "- diagnostics: `1`",
                "- warnings: `1`",
                "- synchronization: `1`",
                "- lifecycleMethods: `0`",
                "- unknownBlockingMethods: `1`",
                "- unsupportedThreadTaskMethods: `1`",
                "- synchronizationMethods: `1`",
                "- concurrencyRuntimeMethods: `0`",
                "- pinningRiskMethods: `1`",
                "- ioSignalMethods: `0`",
                "- taskRoots: `1`",
                "- pinningRiskRoots: `1`",
                "## Task Roots",
                "`com/acme/Main#dead`: rootKind=`PINNING_RISK`, classification=`PINNING_RISK`, threadStartSites=`0`, blockingWaits=`0`, lifecycleRisks=`0`, synchronizationRisks=`1`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
                "## Reachable Thread Methods",
                "`com/acme/Main#dead`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`0`, synchronizationRisks=`1`, concurrencyRuntimeRisks=`0`, sleepWaits=`0`, joinWaits=`0`, estimatedInstructions=`0`, allocationSites=`0`, ioCallSites=`0`, hasLoop=`false`, classification=`PINNING_RISK`",
                "## warning[JAVAN176] unsupported synchronization in unreachable code",
                "- category: `synchronization`"
            );
    }

    @Test
    void writeDiagnosticsWritesUnreachableConcurrencyRuntimeWarningIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.warning(
                "JAVAN177",
                "unsupported concurrency runtime API in unreachable code",
                "com/acme/Main",
                "dead",
                "Executors.newCachedThreadPool()",
                "The current native runtime does not implement this broader executor, scheduler, or concurrent-runtime API surface yet.",
                "Keep this concurrency API on the JVM, or wait until Javan's broader scheduler and virtual-thread runtime lands."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.md")))
            .contains(
                "- diagnostics: `1`",
                "- warnings: `1`",
                "- concurrencyRuntime: `1`",
                "- lifecycleMethods: `0`",
                "- synchronizationMethods: `0`",
                "- concurrencyRuntimeMethods: `1`",
                "- unknownBlockingMethods: `1`",
                "- unsupportedThreadTaskMethods: `1`",
                "- unknownTaskMethods: `1`",
                "- ioSignalMethods: `0`",
                "- taskRoots: `1`",
                "- unsupportedRuntimeRoots: `1`",
                "## Task Roots",
                "`com/acme/Main#dead`: rootKind=`UNSUPPORTED_RUNTIME`, classification=`UNKNOWN`, threadStartSites=`0`, blockingWaits=`0`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`1`, ioCallSites=`0`",
                "## Reachable Thread Methods",
                "`com/acme/Main#dead`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`1`, sleepWaits=`0`, joinWaits=`0`, estimatedInstructions=`0`, allocationSites=`0`, ioCallSites=`0`, hasLoop=`false`, classification=`UNKNOWN`",
                "## warning[JAVAN177] unsupported concurrency runtime API in unreachable code",
                "- category: `concurrency-runtime`"
            );
    }

    @Test
    void writeDiagnosticsWritesBlockingThreadWarningIntoThreadReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.warning(
                "JAVAN178",
                "reachable blocking wait",
                "com/acme/Main",
                "main",
                "Thread.sleep(long)",
                "This reachable code performs an explicit blocking wait.",
                "Keep explicit sleeps intentional."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.json")))
            .contains(
                "\"diagnostics\": 1",
                "\"warnings\": 1",
                "\"blocking\": 1",
                "\"threadStartSites\": 0",
                "\"threadStartMethods\": 0",
                "\"lifecycleMethods\": 0",
                "\"blockingMethods\": 1",
                "\"synchronizationMethods\": 0",
                "\"concurrencyRuntimeMethods\": 0",
                "\"unknownBlockingMethods\": 0",
                "\"unsupportedThreadTaskMethods\": 0",
                "\"sleepWaits\": 1",
                "\"joinWaits\": 0",
                "\"blockingTaskMethods\": 1",
                "\"ioSignalMethods\": 0",
                "\"taskRoots\": 1",
                "\"blockingRoots\": 1",
                "\"classification\": \"BLOCKING_WAIT\"",
                "\"rootKind\": \"BLOCKING_WAIT\"",
                "\"methods\": [",
                "\"lifecycleRisks\": 0",
                "\"synchronizationRisks\": 0",
                "\"concurrencyRuntimeRisks\": 0",
                "\"code\": \"JAVAN178\"",
                "\"category\": \"blocking\""
            );
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/threads.md")))
            .contains(
                "- blocking: `1`",
                "- threadStartSites: `0`",
                "- threadStartMethods: `0`",
                "- lifecycleMethods: `0`",
                "- blockingMethods: `1`",
                "- synchronizationMethods: `0`",
                "- concurrencyRuntimeMethods: `0`",
                "- unknownBlockingMethods: `0`",
                "- unsupportedThreadTaskMethods: `0`",
                "- sleepWaits: `1`",
                "- joinWaits: `0`",
                "- blockingTaskMethods: `1`",
                "- ioSignalMethods: `0`",
                "- taskRoots: `1`",
                "- blockingRoots: `1`",
                "## Task Roots",
                "`com/acme/Main#main`: rootKind=`BLOCKING_WAIT`, classification=`BLOCKING_WAIT`, threadStartSites=`0`, blockingWaits=`1`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
                "## Reachable Thread Methods",
                "`com/acme/Main#main`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`1`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, sleepWaits=`1`, joinWaits=`0`, estimatedInstructions=`0`, allocationSites=`0`, ioCallSites=`0`, hasLoop=`false`, classification=`BLOCKING_WAIT`",
                "## warning[JAVAN178] reachable blocking wait",
                "- category: `blocking`"
            );
    }

    private ProjectLayout layout(final List<String> warnings) {
        return new ProjectLayout(
            tempDir,
            tempDir,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(tempDir.resolve("src/main/java")),
            List.of(tempDir.resolve("src/main/resources")),
            List.of(tempDir.resolve("target/classes")),
            List.of(tempDir.resolve("lib/app.jar")),
            tempDir.resolve(".javan"),
            "report-test",
            warnings
        );
    }
}
