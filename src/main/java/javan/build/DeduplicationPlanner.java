package javan.build;

import javan.analysis.CallGraph;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.compat.JavanNativeSubstitutions;
import javan.compat.JdkCallSupport;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * @return deduplication plan
     * @throws IOException when writing fails
     */
    public Plan writePlan(
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
            + "- runtime modules: `" + join(", ", plan.runtimeModules()) + "`\n"
            + "- duplicate string literals: `" + plan.duplicateStringLiteralCount() + "`\n"
            + "- array helper families: `" + join(", ", plan.arrayHelpers()) + "`\n"
            + "- bounds-check helpers: `" + join(", ", plan.boundsCheckHelpers()) + "`\n\n"
            + "This pass deduplicates infrastructure only. It does not merge observable Java object identity.\n";
        Files2.writeString(reports.resolve("deduplication-plan.json"), json);
        Files2.writeString(reports.resolve("deduplication-plan.md"), markdown);
        return plan;
    }

    private static Plan plan(final Map<String, ClassFile> classes, final CallGraph callGraph) {
        final List<String> strings = new ArrayList<>();
        final List<String> helpers = new ArrayList<>();
        final List<String> modules = new ArrayList<>();
        modules.add("core");
        for (final javan.analysis.EntryPoint entry : callGraph.reachableMethods()) {
            final ClassFile classFile = classes.get(entry.className());
            final Optional<javan.classfile.MethodInfo> method = classFile.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty()) {
                continue;
            }
            final Optional<javan.classfile.CodeAttribute> code = method.orElseThrow().code();
            if (code.isEmpty()) {
                continue;
            }
            for (final Instruction instruction : code.orElseThrow().instructions()) {
                inspect(classes, instruction, strings, helpers, modules);
            }
        }
        final long duplicates = duplicateCount(strings);
        final List<String> helperFamilies = sortedUnique(helpers);
        final List<String> moduleList = sortedUnique(modules);
        final List<String> bounds = boundsHelpers(helperFamilies);
        return new Plan(moduleList, duplicates, helperFamilies, bounds);
    }

    private static void inspect(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<String> strings,
        final List<String> helpers,
        final List<String> modules
    ) {
        final Optional<String> literal = instruction.stringValue();
        if (literal.isPresent()) {
            strings.add(literal.orElseThrow());
        }
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
            case 178, 179, 186 -> modules.add("strings");
            case 182, 183, 184, 185 -> inspectMethodRef(classes, instruction, modules);
            default -> {
            }
        }
    }

    private static void inspectMethodRef(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<String> modules
    ) {
        if (instruction.methodRef().isEmpty()) {
            return;
        }
        final javan.classfile.MethodRef methodRef = JdkCallSupport.normalizeInheritedSupportedJdkCall(
            classes,
            instruction.methodRef().orElseThrow()
        ).orElse(instruction.methodRef().orElseThrow());
        for (final String module : JdkCallSupport.runtimeModules(methodRef)) {
            modules.add(module);
        }
        for (final String module : JavanNativeSubstitutions.runtimeModules(methodRef)) {
            modules.add(module);
        }
    }

    private static long duplicateCount(final List<String> literals) {
        sortLiterals(literals, 0, literals.size());
        long result = 0L;
        for (int index = 1; index < literals.size(); index++) {
            if (literals.get(index - 1).equals(literals.get(index))) {
                result++;
            }
        }
        return result;
    }

    private static void sortLiterals(
        final List<String> literals,
        final int from,
        final int to
    ) {
        if (to - from <= 1) {
            return;
        }
        final int middle = from + ((to - from) / 2);
        sortLiterals(literals, from, middle);
        sortLiterals(literals, middle, to);
        mergeLiterals(literals, from, middle, to);
    }

    private static void mergeLiterals(
        final List<String> literals,
        final int from,
        final int middle,
        final int to
    ) {
        final List<String> left = new ArrayList<>();
        for (int index = from; index < middle; index++) {
            left.add(literals.get(index));
        }
        int leftIndex = 0;
        int rightIndex = middle;
        int target = from;
        while (leftIndex < left.size() && rightIndex < to) {
            final String leftValue = left.get(leftIndex);
            final String rightValue = literals.get(rightIndex);
            if (Strings2.compareAscii(leftValue, rightValue) <= 0) {
                literals.set(target, leftValue);
                leftIndex++;
            } else {
                literals.set(target, rightValue);
                rightIndex++;
            }
            target++;
        }
        while (leftIndex < left.size()) {
            literals.set(target, left.get(leftIndex));
            leftIndex++;
            target++;
        }
    }

    private static List<String> sortedUnique(final List<String> values) {
        final List<String> result = new ArrayList<>();
        for (final String value : values) {
            if (contains(result, value)) {
                continue;
            }
            int index = 0;
            while (index < result.size() && Strings2.compareAscii(result.get(index), value) <= 0) {
                index++;
            }
            result.add(index, value);
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

    private static List<String> boundsHelpers(final List<String> helpers) {
        final List<String> result = new ArrayList<>();
        for (final String helper : helpers) {
            if (helper.endsWith("-array")) {
                result.add(helper + "-bounds");
            }
        }
        return List.copyOf(result);
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

    /**
     * Planned infrastructure after reachability.
     *
     * @param runtimeModules runtime modules needed by reachable code
     * @param duplicateStringLiteralCount duplicate string literal count
     * @param arrayHelpers array helper families
     * @param boundsCheckHelpers bounds-check helper families
     */
    public record Plan(
        List<String> runtimeModules,
        long duplicateStringLiteralCount,
        List<String> arrayHelpers,
        List<String> boundsCheckHelpers
    ) {
    }
}
