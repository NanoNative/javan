package javan;

import javan.analysis.CallGraph;
import javan.analysis.ReachabilityAnalyzer;
import javan.build.BindingGenerator;
import javan.build.BuildKind;
import javan.build.BuildInvoker;
import javan.build.DeduplicationPlanner;
import javan.build.ExportedMethod;
import javan.build.ExportResolver;
import javan.build.LibraryBuildReports;
import javan.build.JarPackager;
import javan.build.ResourceBundler;
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
import javan.detect.MainClassDetector;
import javan.detect.ProjectDetector;
import javan.detect.ProjectLayout;
import javan.ir.IrProgram;
import javan.optimizer.OptimizationReports;
import javan.reporting.IntrinsicUsageReports;
import javan.test.ProjectTestRunner;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;
import javan.verify.StaticVerifier;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * High-level command orchestration for javan.
 */
public final class Javan {
    private final ProjectDetector projectDetector = new ProjectDetector();
    private final BuildInvoker buildInvoker = new BuildInvoker();
    private final ExportResolver exportResolver = new ExportResolver();
    private final BindingGenerator bindingGenerator = new BindingGenerator();
    private final LibraryBuildReports libraryBuildReports = new LibraryBuildReports();
    private final JarPackager jarPackager = new JarPackager();
    private final ResourceBundler resourceBundler = new ResourceBundler();
    private final DeduplicationPlanner deduplicationPlanner = new DeduplicationPlanner();
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
    private final IntrinsicUsageReports intrinsicUsageReports = new IntrinsicUsageReports();
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
        final List<ExportedMethod> exports = options.buildKind().library()
            ? exportResolver.resolve(classes, layout.root(), options.exports())
            : List.of();
        final String mainClass = options.buildKind().library() ? "" : mainClassDetector.detect(options.mainClass(), classes);
        final CallGraph callGraph = options.buildKind().library()
            ? reachabilityAnalyzer.analyze(classes, exports.stream().map(ExportedMethod::entryPoint).toList())
            : reachabilityAnalyzer.analyze(classes, mainClass);
        final List<Diagnostic> diagnostics = new ArrayList<>(callGraph.diagnostics());
        diagnostics.addAll(staticVerifier.verify(classes, callGraph.reachableMethods()));
        reports.writeReachability(layout, callGraph);
        reports.writeDiagnostics(layout, diagnostics);
        intrinsicUsageReports.write(layout.outputDirectory(), classes, callGraph);
        deduplicationPlanner.writePlan(layout.outputDirectory(), classes, callGraph);
        optimizationReports.writeScaffold(layout.outputDirectory());
        final List<Diagnostic> errors = diagnostics.stream().filter(Diagnostic::error).toList();
        if (!errors.isEmpty()) {
            throw new DiagnosticException(errors.getFirst());
        }
        out.println("Checking static Java profile...");
        out.println("  build kind:        " + options.buildKind().name().toLowerCase(java.util.Locale.ROOT));
        out.println("  profile:           " + options.profile().cliName());
        if (options.buildKind().library()) {
            out.println("  exported methods:  " + exports.size());
        }
        out.println("  reachable classes: " + callGraph.reachableMethods().stream().map(entry -> entry.className()).distinct().count());
        out.println("  reachable methods: " + callGraph.reachableMethods().size());
        out.println("  diagnostics:       " + diagnostics.size());
        diagnostics.stream().filter(diagnostic -> !diagnostic.error()).forEach(diagnostic -> out.println(diagnostic.format()));
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
    public Path build(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        if (options.buildKind() == BuildKind.JAR) {
            return buildJar(cwd, options, out);
        }
        final CheckResult check = check(cwd, options, out);
        final Path generated = check.layout().outputDirectory().resolve("generated");
        final IrProgram program = bytecodeToIR.lower(check.classes(), check.callGraph());
        if (options.buildKind() == BuildKind.APP) {
            return buildApp(check, program, generated, out);
        }
        return buildLibrary(check, program, generated, options, out);
    }

    private Path buildApp(final CheckResult check, final IrProgram program, final Path generated, final PrintStream out)
        throws IOException, InterruptedException {
        final Path mainC = cCodegen.generate(program, generated);
        final Path runtimeC = runtimeFiles.write(generated);
        final Path output = BuildKind.APP.artifactPath(check.layout().outputDirectory(), check.layout().outputName());
        final Path binary = nativeLinker.link(check.layout().root(), mainC, runtimeC, output);
        resourceBundler.bundle(check.layout());
        out.println("Built:");
        out.println("  " + binary);
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
        final Path output = options.buildKind().artifactPath(check.layout().outputDirectory(), check.layout().outputName());
        final Path artifact = switch (options.buildKind()) {
            case STATICLIB -> nativeLinker.linkStaticLibrary(check.layout().root(), libraryC, runtimeC, output);
            case SHAREDLIB -> nativeLinker.linkSharedLibrary(check.layout().root(), libraryC, runtimeC, output);
            case APP, JAR -> throw new IllegalArgumentException("App and jar builds must not use buildLibrary");
        };
        resourceBundler.bundle(check.layout());
        final List<Path> bindings = bindingGenerator.generate(
            check.layout().outputDirectory(),
            check.layout().outputName(),
            check.exports(),
            options.bindings()
        );
        libraryBuildReports.write(check.layout().outputDirectory(), check.classes(), check.callGraph(), check.exports(), artifact, bindings);
        out.println("Built:");
        out.println("  " + artifact);
        out.println("Bindings:");
        bindings.forEach(binding -> out.println("  " + binding));
        out.println("Metrics:");
        out.println("  exported methods: " + check.exports().size());
        out.println("  reachable methods: " + check.callGraph().reachableMethods().size());
        out.println("  report: " + check.layout().outputDirectory().resolve("reports/library-build.md"));
        return artifact;
    }

    private Path buildJar(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final ProjectLayout detected = projectDetector.detect(cwd, options);
        final ProjectLayout layout = buildInvoker.ensureClasses(detected, options);
        reports.writeProject(layout, options.profile());
        final Path artifact = BuildKind.JAR.artifactPath(layout.outputDirectory(), layout.outputName());
        final Path jar = jarPackager.packageJar(layout, artifact, options.mainClass());
        resourceBundler.bundle(layout);
        out.println("Built:");
        out.println("  " + jar);
        out.println("Resources:");
        out.println("  " + layout.outputDirectory().resolve("reports/resources.md"));
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
    public int run(final Path cwd, final Options options, final PrintStream out) throws IOException, InterruptedException {
        final Path binary = build(cwd, options, out);
        final List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.addAll(options.passthroughArgs());
        final ProcessRunner.Result result = processRunner.run(binary.getParent(), command);
        out.print(result.stdout());
        if (!result.stderr().isBlank()) {
            out.print(result.stderr());
        }
        return result.exitCode();
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
        reports.writeDiagnostics(layout, diagnostics);
        final List<ClassMetadata> projectMetadata = classMetadataScanner.scanLayout(layout);
        final List<ClassMetadata> jdkMetadata = classMetadataScanner.scanCurrentJdk();
        final CompatibilityResult result = compatibilityReports.write(
            layout.root(),
            layout.outputDirectory(),
            projectMetadata,
            jdkMetadata,
            diagnostics
        );
        out.println("Compatibility:");
        out.println("  status:          " + (result.pass() ? "pass" : "fail"));
        out.println("  java:            " + result.javaVersion());
        out.println("  project classes: " + result.projectClasses().size());
        out.println("  jdk classes:     " + result.jdkClasses().size());
        out.println("  reports:         " + layout.outputDirectory().resolve("reports/compatibility-summary.md"));
        diagnostics.stream().filter(Diagnostic::error).findFirst().ifPresent(diagnostic -> out.println(diagnostic.format()));
        return result;
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
        out.println("Removed " + layout.outputDirectory());
    }

    private static void printLayout(final ProjectLayout layout, final PrintStream out) {
        out.println("javan 0.1");
        out.println("Project: " + layout.buildTool());
        out.println("Input:   " + layout.inputKind() + " " + layout.input());
        out.println("Root:    " + layout.root());
        out.println("Classes: " + (layout.classFolders().isEmpty() ? "-" : layout.classFolders()));
        out.println("Sources: " + (layout.sourceFolders().isEmpty() ? "-" : layout.sourceFolders()));
        out.println("Resources: " + (layout.resourceFolders().isEmpty() ? "-" : layout.resourceFolders()));
        out.println("Output:  " + layout.outputDirectory().resolve("bin").resolve(layout.outputName()));
        for (final String warning : layout.warnings()) {
            out.println("warning: " + warning);
        }
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
    }
}
