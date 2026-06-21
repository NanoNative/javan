package javan;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.analysis.ReachabilityAnalyzer;
import javan.build.BindingGenerator;
import javan.build.BuildInvoker;
import javan.build.DeduplicationPlanner;
import javan.build.ExportedMethod;
import javan.build.ExportResolver;
import javan.build.LibraryBuildReports;
import javan.build.JarPackager;
import javan.build.LibraryFormat;
import javan.build.ResourceBundler;
import javan.build.RuntimeFeatureSelection;
import javan.classfile.ClassFile;
import javan.classfile.ClassFileScanner;
import javan.cli.Options;
import javan.codegen.BytecodeToIR;
import javan.codegen.CCodegen;
import javan.compat.ClassMetadata;
import javan.compat.ClassMetadataScanner;
import javan.compat.CompatibilityReports;
import javan.compat.CompatibilityResult;
import javan.codegen.NativeLinker;
import javan.codegen.RuntimeFiles;
import javan.codegen.SourceLineIndex;
import javan.detect.MainClassDetector;
import javan.detect.MainClassDetector.MainClassDetection;
import javan.detect.ProjectDetector;
import javan.detect.ProjectLayout;
import javan.ir.IrProgram;
import javan.optimizer.OptimizationReports;
import javan.reporting.DependencyReports;
import javan.reporting.ExceptionReports;
import javan.reporting.IntrinsicUsageReports;
import javan.reporting.ReportSummarizer;
import javan.reporting.RuntimeContractReports;
import javan.reporting.RuntimeFootprintReports;
import javan.test.ProjectTestRunner;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.StaticVerifier;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * High-level command orchestration for javan.
 */
public final class Javan {
    private static final int WARNING_DETAIL_LIMIT = 25;

    private final ProjectDetector projectDetector = new ProjectDetector();
    private final BuildInvoker buildInvoker = new BuildInvoker();
    private final ExportResolver exportResolver = new ExportResolver();
    private final BindingGenerator bindingGenerator = new BindingGenerator();
    private final LibraryBuildReports libraryBuildReports = new LibraryBuildReports();
    private final JarPackager jarPackager = new JarPackager();
    private final ResourceBundler resourceBundler = new ResourceBundler();
    private final DeduplicationPlanner deduplicationPlanner = new DeduplicationPlanner();
    private final RuntimeFeatureSelection runtimeFeatureSelection = new RuntimeFeatureSelection();
    private final ClassFileScanner classFileScanner = new ClassFileScanner();
    private final ClassMetadataScanner classMetadataScanner = new ClassMetadataScanner();
    private final MainClassDetector mainClassDetector = new MainClassDetector();
    private final ReachabilityAnalyzer reachabilityAnalyzer = new ReachabilityAnalyzer();
    private final StaticVerifier staticVerifier = new StaticVerifier();
    private final BytecodeToIR bytecodeToIR = new BytecodeToIR();
    private final CCodegen cCodegen = new CCodegen();
    private final RuntimeFiles runtimeFiles = new RuntimeFiles();
    private final NativeLinker nativeLinker = new NativeLinker();
    private final ProjectReports reports = new ProjectReports();
    private final CompatibilityReports compatibilityReports = new CompatibilityReports();
    private final OptimizationReports optimizationReports = new OptimizationReports();
    private final DependencyReports dependencyReports = new DependencyReports();
    private final ExceptionReports exceptionReports = new ExceptionReports();
    private final IntrinsicUsageReports intrinsicUsageReports = new IntrinsicUsageReports();
    private final RuntimeContractReports runtimeContractReports = new RuntimeContractReports();
    private final RuntimeFootprintReports runtimeFootprintReports = new RuntimeFootprintReports();
    private final ReportSummarizer reportSummarizer = new ReportSummarizer();
    private final ProcessRunner processRunner = new ProcessRunner();
    private final ProjectTestRunner projectTestRunner = new ProjectTestRunner();

    /**
     * Detects and reports project information.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return detected layout
     * @throws IOException when inspection fails
     */
    public ProjectLayout inspect(final Path cwd, final Options options, final PrintStream out) throws IOException {
        final ProjectLayout layout = projectDetector.detect(cwd, options);
        reports.writeProject(layout, options.profile());
        printLayout(layout, out);
        return layout;
    }

    /**
     * Compiles when needed, scans classes, resolves main, analyzes reachability, and verifies the static profile.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return check result
     * @throws IOException when IO or build invocation fails
     * @throws InterruptedException when interrupted while waiting for processes
     */
    public CheckResult check(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final ProjectLayout detected = projectDetector.detect(cwd, options);
        final ProjectLayout layout = buildInvoker.ensureClasses(detected, options);
        reports.writeProject(layout, options.profile());
        final Map<String, ClassFile> classes = classFileScanner.scan(layout);
        final List<ExportedMethod> exports;
        if (options.libraryBuild()) {
            exports = exportResolver.resolve(classes, layout.root(), options.exports());
        } else {
            exports = List.of();
        }
        final MainClassDetection mainDetection = selectedMainClass(options, classes);
        final String mainClass = mainDetection.mainClass();
        final CallGraph callGraph;
        if (options.libraryBuild()) {
            callGraph = reachabilityAnalyzer.analyze(classes, entryPoints(exports));
        } else if (mainDetection.pass()) {
            callGraph = reachabilityAnalyzer.analyze(classes, mainClass);
        } else {
            callGraph = emptyCallGraph();
        }
        final List<Diagnostic> diagnostics = new ArrayList<>(mainDetection.diagnostics());
        diagnostics.addAll(callGraph.diagnostics());
        if (mainDetection.pass()) {
            diagnostics.addAll(staticVerifier.verify(classes, callGraph.reachableMethods()));
        }
        final DeduplicationPlanner.Plan deduplicationPlan = deduplicationPlanner.writePlan(layout.outputDirectory(), classes, callGraph);
        diagnostics.addAll(runtimeFeatureSelection.write(layout.root(), layout.outputDirectory(), deduplicationPlan).diagnostics());
        reports.writeReachability(layout, callGraph);
        dependencyReports.write(layout, classes, callGraph);
        reports.writeDiagnostics(layout, diagnostics);
        intrinsicUsageReports.write(layout.outputDirectory(), classes, callGraph);
        optimizationReports.writeScaffold(layout.outputDirectory());
        writeUnifiedReport(layout.outputDirectory());
        final List<Diagnostic> errors = errors(diagnostics);
        if (!errors.isEmpty()) {
            return new CheckResult(layout, classes, mainClass, callGraph, diagnostics, exports);
        }
        out.println("Checking static Java profile...");
        printText(out, "  build kind:        ", Strings2.toAsciiLowerCase(options.buildKindName()));
        printText(out, "  profile:           ", options.profile().cliName());
        if (options.libraryBuild()) {
            printInt(out, "  exported methods:  ", exports.size());
        }
        printInt(out, "  reachable classes: ", reachableClassCount(callGraph.reachableMethods()));
        printInt(out, "  reachable methods: ", callGraph.reachableMethods().size());
        printInt(out, "  diagnostics:       ", diagnostics.size());
        printWarnings(diagnostics, out);
        return new CheckResult(layout, classes, mainClass, callGraph, diagnostics, exports);
    }

    /**
     * Builds project classes when needed, then runs the configured project test task.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return project test process exit code
     * @throws IOException when build or test invocation fails
     * @throws InterruptedException when interrupted while waiting for build or test processes
     */
    public int test(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final ProjectLayout detected = projectDetector.detect(cwd, options);
        final ProjectLayout layout = buildInvoker.ensureClasses(detected, options);
        reports.writeProject(layout, options.profile());
        return projectTestRunner.run(layout, out);
    }

    /**
     * Builds a native executable.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return built binary path
     * @throws IOException when generation or linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public BuildResult build(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        if (options.jarBuild()) {
            return BuildResult.success(buildJar(cwd, options, out), List.of());
        }
        RuntimeFootprintReports.requireHostTarget(options.targetTriple());
        final CheckResult check = check(cwd, options, out);
        if (!check.pass()) {
            return BuildResult.failed(check.diagnostics());
        }
        final Path generated = check.layout().outputDirectory().resolve("generated");
        final IrProgram program = bytecodeToIR.lower(
            check.classes(),
            check.callGraph(),
            SourceLineIndex.from(check.layout())
        );
        exceptionReports.write(check.layout().outputDirectory(), program);
        final Path artifact;
        if (options.appBuild()) {
            artifact = buildApp(check, program, generated, options, out);
        } else {
            artifact = buildLibrary(check, program, generated, options, out);
        }
        return BuildResult.success(artifact, check.diagnostics());
    }

    private Path buildApp(
        final CheckResult check,
        final IrProgram program,
        final Path generated,
        final Options options,
        final PrintStream out
    )
        throws IOException, InterruptedException {
        final Path mainC = cCodegen.generate(program, generated);
        final Path runtimeC = runtimeFiles.write(generated);
        final Path output = check.layout().outputDirectory().resolve("bin").resolve(check.layout().outputName());
        final Path binary = nativeLinker.link(check.layout().root(), mainC, runtimeC, output);
        resourceBundler.bundle(check.layout());
        runtimeContractReports.write(check.layout().outputDirectory(), "app", List.of(binary));
        runtimeFootprintReports.write(
            check.layout().outputDirectory(),
            "app",
            List.of(binary),
            options.targetTriple(),
            options.profile().cliName(),
            options.release()
        );
        writeUnifiedReport(check.layout().outputDirectory());
        out.println("Built:");
        out.print("  ");
        out.println(binary.toString());
        return binary;
    }

    private Path buildLibrary(
        final CheckResult check,
        final IrProgram program,
        final Path generated,
        final Options options,
        final PrintStream out
    ) throws IOException, InterruptedException {
        final Path libraryC = cCodegen.generateLibrary(program, generated, check.exports());
        final Path runtimeC = runtimeFiles.write(generated);
        final List<Path> artifacts = new ArrayList<>();
        for (final LibraryFormat format : options.libraryFormats()) {
            final Path output = libraryArtifactPath(format, check.layout().outputDirectory(), check.layout().outputName());
            artifacts.add(linkLibraryFormat(format, check.layout().root(), libraryC, runtimeC, output));
        }
        resourceBundler.bundle(check.layout());
        final List<Path> bindings = bindingGenerator.generate(
            check.layout().outputDirectory(),
            check.layout().outputName(),
            check.exports(),
            options.bindings(),
            artifacts
        );
        libraryBuildReports.write(check.layout().outputDirectory(), check.classes(), check.callGraph(), check.exports(), artifacts, bindings);
        runtimeContractReports.write(check.layout().outputDirectory(), "library", artifacts, exportedSymbols(check.exports()));
        runtimeFootprintReports.write(
            check.layout().outputDirectory(),
            "library",
            artifacts,
            options.targetTriple(),
            options.profile().cliName(),
            options.release()
        );
        writeUnifiedReport(check.layout().outputDirectory());
        out.println("Built:");
        for (final Path artifact : artifacts) {
            printText(out, "  ", artifact.toString());
        }
        out.println("Bindings:");
        for (final Path binding : bindings) {
            printText(out, "  ", binding.toString());
        }
        out.println("Metrics:");
        printInt(out, "  exported methods: ", check.exports().size());
        printInt(out, "  reachable methods: ", check.callGraph().reachableMethods().size());
        printText(out, "  report: ", check.layout().outputDirectory().resolve("reports/library-build.md").toString());
        if (options.combinedLibraryBuild()) {
            return check.layout().outputDirectory().resolve("dist").resolve("lib").resolve(check.layout().outputName());
        }
        return artifacts.getFirst();
    }

    private Path buildJar(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final ProjectLayout detected = projectDetector.detect(cwd, options);
        final ProjectLayout layout = buildInvoker.ensureClasses(detected, options);
        reports.writeProject(layout, options.profile());
        final Path artifact = layout.outputDirectory().resolve("dist").resolve(concat(layout.outputName(), ".jar"));
        final Path jar = jarPackager.packageJar(layout, artifact, options.mainClass());
        resourceBundler.bundle(layout);
        writeUnifiedReport(layout.outputDirectory());
        out.println("Built:");
        printText(out, "  ", jar.toString());
        out.println("Resources:");
        printText(out, "  ", layout.outputDirectory().resolve("reports/resources.md").toString());
        return jar;
    }

    /**
     * Builds and runs the native executable.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return process exit code
     * @throws IOException when build or execution fails
     * @throws InterruptedException when interrupted while running
     */
    public RunResult run(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final BuildResult build = build(cwd, options, out);
        if (!build.pass()) {
            return RunResult.failed(build.diagnostics());
        }
        final Path binary = build.artifact().orElseThrow();
        final List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.addAll(options.passthroughArgs());
        final ProcessRunner.Result result = processRunner.run(binary.getParent(), command);
        out.print(result.stdout());
        if (!Strings2.isBlank(result.stderr())) {
            out.print(result.stderr());
        }
        return RunResult.success(result.exitCode());
    }

    /**
     * Generates deterministic compatibility reports for the current project and runtime JDK.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return compatibility result
     * @throws IOException when IO or build invocation fails
     * @throws InterruptedException when interrupted while waiting for processes
     */
    public CompatibilityResult compat(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final ProjectLayout detected = projectDetector.detect(cwd, options);
        final ProjectLayout layout = buildInvoker.ensureClasses(detected, options);
        reports.writeProject(layout, options.profile());
        final Map<String, ClassFile> classes = classFileScanner.scan(layout);
        final String mainClass = mainClassDetector.detect(options.mainClass(), classes);
        final CallGraph callGraph = reachabilityAnalyzer.analyze(classes, mainClass);
        final List<Diagnostic> diagnostics = new ArrayList<>(callGraph.diagnostics());
        diagnostics.addAll(staticVerifier.verify(classes, callGraph.reachableMethods()));
        reports.writeReachability(layout, callGraph);
        dependencyReports.write(layout, classes, callGraph);
        reports.writeDiagnostics(layout, diagnostics);
        final List<ClassMetadata> projectMetadata = classMetadataScanner.scanLayout(layout);
        final List<ClassMetadata> jdkMetadata = classMetadataScanner.scanCurrentJdk(layout.outputDirectory());
        final CompatibilityResult result = compatibilityReports.write(
            layout.root(),
            layout.outputDirectory(),
            projectMetadata,
            jdkMetadata,
            diagnostics
        );
        writeUnifiedReport(layout.outputDirectory());
        out.println("Compatibility:");
        printCompatibilityStatus(out, result);
        printText(out, "  java:            ", result.javaVersion());
        printInt(out, "  project classes: ", result.projectClasses().size());
        printInt(out, "  jdk classes:     ", result.jdkClasses().size());
        printText(out, "  reports:         ", layout.outputDirectory().resolve("reports/compatibility-summary.md").toString());
        final Diagnostic error = firstError(diagnostics);
        if (error != null) {
            out.println(error.format());
        }
        return result;
    }

    /**
     * Reads existing report files and writes a unified report summary.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @return written report summary
     * @throws IOException when report files cannot be read or written
     */
    public ReportSummarizer.Summary report(final Path cwd, final Options options, final PrintStream out) throws IOException {
        final Path target = options.target().orElse(cwd);
        final Path resolved = target.isAbsolute() ? target : cwd.resolve(target);
        final ReportSummarizer.Summary summary = reportSummarizer.write(resolved);
        out.print(summary.markdown());
        return summary;
    }

    /**
     * Removes the .javan output folder.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @param out output stream
     * @throws IOException when deletion fails
     */
    public void clean(final Path cwd, final Options options, final PrintStream out) throws IOException {
        final ProjectLayout layout = projectDetector.detect(cwd, options);
        Files2.deleteRecursive(layout.outputDirectory());
        printText(out, "Removed ", layout.outputDirectory().toString());
    }

    private static void printLayout(final ProjectLayout layout, final PrintStream out) {
        out.println("javan 0.1");
        printText(out, "Project: ", layout.buildTool().name());
        out.print("Input:   ");
        out.print(layout.inputKind().name());
        out.print(" ");
        out.println(layout.input().toString());
        printText(out, "Root:    ", layout.root().toString());
        printText(out, "Classes: ", pathListText(layout.classFolders()));
        printText(out, "Sources: ", pathListText(layout.sourceFolders()));
        printText(out, "Resources: ", pathListText(layout.resourceFolders()));
        printText(out, "Output:  ", layout.outputDirectory().resolve("bin").resolve(layout.outputName()).toString());
        for (final String warning : layout.warnings()) {
            printText(out, "warning: ", warning);
        }
    }

    private void writeUnifiedReport(final Path outputDirectory) throws IOException {
        reportSummarizer.write(outputDirectory);
    }

    private MainClassDetection selectedMainClass(final Options options, final Map<String, ClassFile> classes) {
        if (options.libraryBuild()) {
            return new MainClassDetection("", List.of());
        }
        return mainClassDetector.find(options.mainClass(), classes);
    }

    private static void printText(final PrintStream out, final String prefix, final String value) {
        out.print(prefix);
        out.println(value);
    }

    private static void printInt(final PrintStream out, final String prefix, final int value) {
        out.print(prefix);
        out.println(value);
    }

    private static void printCompatibilityStatus(final PrintStream out, final CompatibilityResult result) {
        out.print("  status:          ");
        if (result.pass()) {
            out.println("pass");
            return;
        }
        out.println("fail");
    }

    private static String pathListText(final List<Path> paths) {
        if (paths.isEmpty()) {
            return "-";
        }
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < paths.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(paths.get(index).toString());
        }
        result.append(']');
        return result.toString();
    }

    private static List<EntryPoint> entryPoints(final List<ExportedMethod> exports) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final ExportedMethod export : exports) {
            result.add(export.entryPoint());
        }
        return List.copyOf(result);
    }

    private static CallGraph emptyCallGraph() {
        return new CallGraph(new EntryPoint("", "", ""), List.of(), List.of());
    }

    private static List<Diagnostic> errors(final List<Diagnostic> diagnostics) {
        final List<Diagnostic> result = new ArrayList<>();
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                result.add(diagnostic);
            }
        }
        return List.copyOf(result);
    }

    private static Diagnostic firstError(final List<Diagnostic> diagnostics) {
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                return diagnostic;
            }
        }
        return null;
    }

    private static int reachableClassCount(final List<EntryPoint> entries) {
        final List<String> classes = new ArrayList<>();
        for (final EntryPoint entry : entries) {
            if (!containsString(classes, entry.className())) {
                classes.add(entry.className());
            }
        }
        return classes.size();
    }

    private static boolean containsString(final List<String> values, final String target) {
        for (final String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> exportedSymbols(final List<ExportedMethod> exports) {
        final List<String> result = new ArrayList<>();
        for (final ExportedMethod export : exports) {
            result.add(export.symbol());
        }
        return List.copyOf(result);
    }

    private static void printWarnings(final List<Diagnostic> diagnostics, final PrintStream out) {
        final int warningCount = warningCount(diagnostics);
        if (warningCount > WARNING_DETAIL_LIMIT) {
            printWarningSummary(diagnostics, warningCount, out);
            return;
        }
        for (final Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.error()) {
                out.println(diagnostic.format());
            }
        }
    }

    private static int warningCount(final List<Diagnostic> diagnostics) {
        int result = 0;
        for (final Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.error()) {
                result++;
            }
        }
        return result;
    }

    private static void printWarningSummary(final List<Diagnostic> diagnostics, final int warningCount, final PrintStream out) {
        printInt(out, "Warnings: ", warningCount);
        out.println("  full details: .javan/reports/diagnostics.txt");
        final List<WarningGroup> groups = warningGroups(diagnostics);
        for (final WarningGroup group : groups) {
            out.print("  warning[");
            out.print(group.code);
            out.print("] ");
            out.print(group.message);
            out.print(": ");
            out.println(group.count);
        }
    }

    private static List<WarningGroup> warningGroups(final List<Diagnostic> diagnostics) {
        final List<WarningGroup> groups = new ArrayList<>();
        for (final Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.error()) {
                addWarningGroup(groups, diagnostic);
            }
        }
        return List.copyOf(groups);
    }

    private static void addWarningGroup(final List<WarningGroup> groups, final Diagnostic diagnostic) {
        for (final WarningGroup group : groups) {
            if (group.same(diagnostic)) {
                group.count++;
                return;
            }
        }
        groups.add(new WarningGroup(diagnostic.code(), diagnostic.message()));
    }

    private static final class WarningGroup {
        private final String code;
        private final String message;
        private int count;

        private WarningGroup(final String code, final String message) {
            this.code = code;
            this.message = message;
            this.count = 1;
        }

        private boolean same(final Diagnostic diagnostic) {
            return code.equals(diagnostic.code()) && message.equals(diagnostic.message());
        }
    }

    private Path linkLibraryFormat(
        final LibraryFormat format,
        final Path root,
        final Path libraryC,
        final Path runtimeC,
        final Path output
    ) throws IOException, InterruptedException {
        if ("STATIC".equals(Options.formatName(format))) {
            return nativeLinker.linkStaticLibrary(root, libraryC, runtimeC, output);
        }
        if ("SHARED".equals(Options.formatName(format))) {
            return nativeLinker.linkSharedLibrary(root, libraryC, runtimeC, output);
        }
        throw new IllegalStateException("Unsupported library format");
    }

    private static Path libraryArtifactPath(final LibraryFormat format, final Path outputDirectory, final String outputName) {
        if ("STATIC".equals(Options.formatName(format))) {
            return outputDirectory.resolve("dist").resolve(concat("lib", outputName, ".a"));
        }
        if ("SHARED".equals(Options.formatName(format))) {
            return outputDirectory.resolve("dist").resolve(sharedLibraryName(outputName));
        }
        throw new IllegalStateException("Unsupported library format");
    }

    private static String sharedLibraryName(final String outputName) {
        final String os = Strings2.toAsciiLowerCase(System.getProperty("os.name", ""));
        if (os.contains("win")) {
            return concat(outputName, ".dll");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return concat("lib", outputName, ".dylib");
        }
        return concat("lib", outputName, ".so");
    }

    private static String concat(final String first, final String second) {
        return new StringBuilder().append(first).append(second).toString();
    }

    private static String concat(final String first, final String second, final String third) {
        return new StringBuilder().append(first).append(second).append(third).toString();
    }

    /**
     * Result of a successful static check.
     *
     * @param layout project layout
     * @param classes parsed classes
     * @param mainClass JVM internal main class
     * @param callGraph reachable call graph
     * @param diagnostics non-fatal diagnostics
     * @param exports native library exports
     */
    public record CheckResult(
        ProjectLayout layout,
        Map<String, ClassFile> classes,
        String mainClass,
        CallGraph callGraph,
        List<Diagnostic> diagnostics,
        List<ExportedMethod> exports
    ) {
        /**
         * Reports whether the check completed without fatal diagnostics.
         *
         * @return true when the result has no errors
         */
        public boolean pass() {
            return firstError().isEmpty();
        }

        /**
         * Returns the first fatal diagnostic.
         *
         * @return first error diagnostic
         */
        public Optional<Diagnostic> firstError() {
            for (final Diagnostic diagnostic : diagnostics) {
                if (diagnostic.error()) {
                    return Optional.of(diagnostic);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Build result.
     *
     * @param artifact built artifact when build succeeded
     * @param diagnostics build diagnostics
     */
    public record BuildResult(Optional<Path> artifact, List<Diagnostic> diagnostics) {
        private static BuildResult success(final Path artifact, final List<Diagnostic> diagnostics) {
            return new BuildResult(Optional.of(artifact), diagnostics);
        }

        private static BuildResult failed(final List<Diagnostic> diagnostics) {
            return new BuildResult(Optional.empty(), diagnostics);
        }

        /**
         * Reports whether the build produced an artifact.
         *
         * @return true when an artifact is present and no fatal diagnostic exists
         */
        public boolean pass() {
            if (artifact.isEmpty()) {
                return false;
            }
            for (final Diagnostic diagnostic : diagnostics) {
                if (diagnostic.error()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Run result.
     *
     * @param exitCode process exit code when execution happened
     * @param diagnostics build diagnostics
     */
    public record RunResult(int exitCode, List<Diagnostic> diagnostics) {
        private static RunResult success(final int exitCode) {
            return new RunResult(exitCode, List.of());
        }

        private static RunResult failed(final List<Diagnostic> diagnostics) {
            return new RunResult(2, diagnostics);
        }
    }
}
