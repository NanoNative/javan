package javan.build;

import javan.analysis.CallGraph;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Plans deterministic infrastructure deduplication after reachability.
 */
public final class DeduplicationPlanner {
    /**
     * Writes a deduplication plan for the reachable graph.
     *
     * @param outputDirectory javan output directory
     * @param classes parsed classes
     * @param callGraph reachable graph
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> writePlan(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        final Plan plan = plan(classes, callGraph);
        final Path reports = outputDirectory.resolve("reports");
        final String json = "{\n"
            + "  \"runtimeModules\": " + Json.stringList(plan.runtimeModules()) + ",\n"
            + "  \"deduplicatedStringLiterals\": " + plan.duplicateStringLiteralCount() + ",\n"
            + "  \"arrayHelperFamilies\": " + Json.stringList(plan.arrayHelpers()) + ",\n"
            + "  \"boundsCheckHelpers\": " + Json.stringList(plan.boundsCheckHelpers()) + "\n"
            + "}\n";
        final String markdown = "# Deduplication Plan\n\n"
            + "- runtime modules: `" + String.join(", ", plan.runtimeModules()) + "`\n"
            + "- duplicate string literals: `" + plan.duplicateStringLiteralCount() + "`\n"
            + "- array helper families: `" + String.join(", ", plan.arrayHelpers()) + "`\n"
            + "- bounds-check helpers: `" + String.join(", ", plan.boundsCheckHelpers()) + "`\n\n"
            + "This pass deduplicates infrastructure only. It does not merge observable Java object identity.\n";
        return List.of(
            Files2.writeString(reports.resolve("deduplication-plan.json"), json),
            Files2.writeString(reports.resolve("deduplication-plan.md"), markdown)
        );
    }

    private static Plan plan(final Map<String, ClassFile> classes, final CallGraph callGraph) {
        final Map<String, Integer> strings = new TreeMap<>();
        final List<String> helpers = new ArrayList<>();
        final List<String> modules = new ArrayList<>();
        modules.add("core");
        for (final javan.analysis.EntryPoint entry : callGraph.reachableMethods()) {
            classes.get(entry.className()).method(entry.methodName(), entry.descriptor())
                .flatMap(method -> method.code())
                .ifPresent(code -> code.instructions().forEach(instruction -> inspect(instruction, strings, helpers, modules)));
        }
        final long duplicates = strings.values().stream().filter(count -> count > 1).mapToLong(count -> count - 1L).sum();
        final List<String> helperFamilies = helpers.stream().distinct().sorted().toList();
        final List<String> moduleList = modules.stream().distinct().sorted().toList();
        final List<String> bounds = helperFamilies.stream().filter(helper -> helper.endsWith("-array")).map(helper -> helper + "-bounds").toList();
        return new Plan(moduleList, duplicates, helperFamilies, bounds);
    }

    private static void inspect(
        final Instruction instruction,
        final Map<String, Integer> strings,
        final List<String> helpers,
        final List<String> modules
    ) {
        instruction.stringValue().ifPresent(literal -> strings.merge(literal, 1, Integer::sum));
        switch (instruction.opcode()) {
            case 50, 83, 189 -> {
                helpers.add("object-array");
                modules.add("arrays");
            }
            case 46, 79, 188 -> {
                helpers.add("int-array");
                modules.add("arrays");
            }
            case 47, 80 -> {
                helpers.add("long-array");
                modules.add("arrays");
            }
            case 48, 81 -> {
                helpers.add("float-array");
                modules.add("arrays");
            }
            case 49, 82 -> {
                helpers.add("double-array");
                modules.add("arrays");
            }
            case 178, 179, 184, 186 -> modules.add("strings");
            default -> {
            }
        }
    }

    private record Plan(
        List<String> runtimeModules,
        long duplicateStringLiteralCount,
        List<String> arrayHelpers,
        List<String> boundsCheckHelpers
    ) {
    }
}
