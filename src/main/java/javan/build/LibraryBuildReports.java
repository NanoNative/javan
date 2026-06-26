package javan.build;

import javan.analysis.CallGraph;
import javan.classfile.ClassFile;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes deterministic native library build reports.
 */
public final class LibraryBuildReports {
    private static final int ABI_VERSION = 2;
    private static final String ERROR_RESULT_ABI =
        "abi-v2-c-owned-javanresult-try-wrappers-v1-direct-exports-compatible";

    /**
     * Writes library build metrics.
     *
     * @param outputDirectory javan output directory
     * @param classes parsed classes
     * @param callGraph call graph
     * @param exports exports
     * @param artifacts native artifacts
     * @param bindings binding files
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> write(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final List<ExportedMethod> exports,
        final List<Path> artifacts,
        final List<Path> bindings
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        long inputMethods = 0;
        for (final ClassFile classFile : classes.values()) {
            inputMethods += classFile.methods().size();
        }
        final long inputClasses = classes.size();
        final long reachableMethods = callGraph.reachableMethods().size();
        final long reachableClasses = reachableClassCount(callGraph);
        long artifactSize = 0;
        for (final Path artifact : artifacts) {
            artifactSize += Files.exists(artifact) ? Files.size(artifact) : 0;
        }
        final List<String> runtimeModules = runtimeModules(callGraph);
        final String json = "{\n"
            + "  \"abiVersion\": " + ABI_VERSION + ",\n"
            + "  \"stringOwnership\": \"input-copied-gc-managed-utf8-output-javan-owned-free-with-javan_free\",\n"
            + "  \"byteArrayOwnership\": \"input-copied-gc-managed-output-javan-owned-data-free-with-javan_free\",\n"
            + "  \"errorResultAbi\": \"" + ERROR_RESULT_ABI + "\",\n"
            + "  \"exceptionMapping\": \"caught-runtime-panic-to-last-error-limited-same-method-catch\",\n"
            + "  \"threadRuntimeRules\": \"parallel-host-thread-bootstrap-current-thread-interrupt-isalive-sleep-start-join-runnable-target-plus-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-and-isvirtual-no-virtual-scheduler\",\n"
            + "  \"generatedAbiTests\": \"c-header-compile-test\",\n"
            + "  \"inputClasses\": " + inputClasses + ",\n"
            + "  \"inputMethods\": " + inputMethods + ",\n"
            + "  \"reachableClassesFromExports\": " + reachableClasses + ",\n"
            + "  \"reachableMethodsFromExports\": " + reachableMethods + ",\n"
            + "  \"exportedMethods\": " + exports.size() + ",\n"
            + "  \"artifacts\": " + Json.stringList(pathStrings(artifacts)) + ",\n"
            + "  \"artifactBytes\": " + artifactSize + ",\n"
            + "  \"runtimeModulesLinked\": " + Json.stringList(runtimeModules) + ",\n"
            + "  \"dependencyReductionMethods\": " + Math.max(0, inputMethods - reachableMethods) + ",\n"
            + "  \"bindings\": " + Json.stringList(pathStrings(bindings)) + "\n"
            + "}\n";
        final String markdown = "# Library Build Metrics\n\n"
            + "- ABI version: `" + ABI_VERSION + "`\n"
            + "- string ownership: `input-copied-gc-managed-utf8-output-javan-owned-free-with-javan_free`\n"
            + "- byte[] ownership: `input-copied-gc-managed-output-javan-owned-data-free-with-javan_free`\n"
            + "- error/result ABI: `" + ERROR_RESULT_ABI + "`\n"
            + "- exception mapping: `caught-runtime-panic-to-last-error-limited-same-method-catch`\n"
            + "- thread/runtime rules: `parallel-host-thread-bootstrap-current-thread-interrupt-isalive-sleep-start-join-runnable-target-plus-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-and-isvirtual-no-virtual-scheduler`\n"
            + "- generated ABI tests: `c-header-compile-test`\n"
            + "- input classes: `" + inputClasses + "`\n"
            + "- input methods: `" + inputMethods + "`\n"
            + "- reachable classes from exports: `" + reachableClasses + "`\n"
            + "- reachable methods from exports: `" + reachableMethods + "`\n"
            + "- exported methods: `" + exports.size() + "`\n"
            + "- artifact bytes: `" + artifactSize + "`\n"
            + "- runtime modules linked: `" + join(", ", runtimeModules) + "`\n"
            + "- dependency reduction methods: `" + Math.max(0, inputMethods - reachableMethods) + "`\n";
        return List.of(
            Files2.writeString(reports.resolve("library-build.json"), json),
            Files2.writeString(reports.resolve("library-build.md"), markdown)
        );
    }

    private static List<String> runtimeModules(final CallGraph callGraph) {
        boolean arrays = false;
        boolean strings = false;
        for (final javan.analysis.EntryPoint entry : callGraph.reachableMethods()) {
            final String descriptor = entry.descriptor();
            if (descriptor.contains("[")) {
                arrays = true;
            }
            if (descriptor.contains("Ljava/lang/String;")) {
                strings = true;
            }
        }
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

    private static long reachableClassCount(final CallGraph callGraph) {
        final List<String> classNames = new ArrayList<>();
        for (final javan.analysis.EntryPoint entry : callGraph.reachableMethods()) {
            if (!contains(classNames, entry.className())) {
                classNames.add(entry.className());
            }
        }
        return classNames.size();
    }

    private static List<String> pathStrings(final List<Path> paths) {
        final List<String> result = new ArrayList<>();
        for (final Path path : paths) {
            result.add(path.toString());
        }
        return List.copyOf(result);
    }

    private static boolean contains(final List<String> values, final String target) {
        for (final String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static String join(final String delimiter, final List<String> values) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(delimiter);
            }
            result.append(values.get(index));
        }
        return result.toString();
    }
}
