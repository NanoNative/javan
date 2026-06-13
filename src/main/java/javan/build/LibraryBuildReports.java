package javan.build;

import javan.analysis.CallGraph;
import javan.classfile.ClassFile;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes deterministic native library build reports.
 */
public final class LibraryBuildReports {
    /**
     * Writes library build metrics.
     *
     * @param outputDirectory javan output directory
     * @param classes parsed classes
     * @param callGraph call graph
     * @param exports exports
     * @param artifact native artifact
     * @param bindings binding files
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> write(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final List<ExportedMethod> exports,
        final Path artifact,
        final List<Path> bindings
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        final long inputMethods = classes.values().stream().mapToLong(classFile -> classFile.methods().size()).sum();
        final long inputClasses = classes.size();
        final long reachableMethods = callGraph.reachableMethods().size();
        final long reachableClasses = callGraph.reachableMethods().stream().map(entry -> entry.className()).distinct().count();
        final long artifactSize = Files.exists(artifact) ? Files.size(artifact) : 0;
        final List<String> runtimeModules = runtimeModules(callGraph);
        final String json = "{\n"
            + "  \"inputClasses\": " + inputClasses + ",\n"
            + "  \"inputMethods\": " + inputMethods + ",\n"
            + "  \"reachableClassesFromExports\": " + reachableClasses + ",\n"
            + "  \"reachableMethodsFromExports\": " + reachableMethods + ",\n"
            + "  \"exportedMethods\": " + exports.size() + ",\n"
            + "  \"artifact\": " + Json.string(artifact.toString()) + ",\n"
            + "  \"artifactBytes\": " + artifactSize + ",\n"
            + "  \"runtimeModulesLinked\": " + Json.stringList(runtimeModules) + ",\n"
            + "  \"dependencyReductionMethods\": " + Math.max(0, inputMethods - reachableMethods) + ",\n"
            + "  \"bindings\": " + Json.stringList(bindings.stream().map(Path::toString).toList()) + "\n"
            + "}\n";
        final String markdown = "# Library Build Metrics\n\n"
            + "- input classes: `" + inputClasses + "`\n"
            + "- input methods: `" + inputMethods + "`\n"
            + "- reachable classes from exports: `" + reachableClasses + "`\n"
            + "- reachable methods from exports: `" + reachableMethods + "`\n"
            + "- exported methods: `" + exports.size() + "`\n"
            + "- artifact bytes: `" + artifactSize + "`\n"
            + "- runtime modules linked: `" + String.join(", ", runtimeModules) + "`\n"
            + "- dependency reduction methods: `" + Math.max(0, inputMethods - reachableMethods) + "`\n";
        return List.of(
            Files2.writeString(reports.resolve("library-build.json"), json),
            Files2.writeString(reports.resolve("library-build.md"), markdown)
        );
    }

    private static List<String> runtimeModules(final CallGraph callGraph) {
        final boolean arrays = callGraph.reachableMethods().stream().anyMatch(entry -> entry.descriptor().contains("["));
        final boolean strings = callGraph.reachableMethods().stream().anyMatch(entry -> entry.descriptor().contains("Ljava/lang/String;"));
        final java.util.ArrayList<String> modules = new java.util.ArrayList<>();
        modules.add("core");
        if (strings) {
            modules.add("strings");
        }
        if (arrays) {
            modules.add("arrays");
        }
        return List.copyOf(modules);
    }
}
