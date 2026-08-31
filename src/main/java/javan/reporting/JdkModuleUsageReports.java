package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.compat.ClassMetadata;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes exact current-JDK module usage for direct references in closed-world reachable code.
 */
public final class JdkModuleUsageReports {
    /**
     * Intersects direct references in reachable class supertypes and bytecode with current-JDK metadata and writes reports.
     *
     * @param outputDirectory Javan output directory
     * @param classes parsed application and dependency classes
     * @param callGraph closed-world reachability result
     * @param jdkClasses current JDK class metadata with exact module names
     * @return written report paths
     * @throws IOException when report files cannot be written
     */
    public List<Path> write(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final List<ClassMetadata> jdkClasses
    ) throws IOException {
        final Report report = analyze(classes, callGraph.reachableMethods(), jdkClasses);
        final Path json = outputDirectory.resolve("reports/jdk-module-usage.json");
        final Path markdown = outputDirectory.resolve("reports/jdk-module-usage.md");
        Files2.writeString(json, json(report));
        Files2.writeString(markdown, markdown(report));
        return List.of(json, markdown);
    }

    /**
     * Counts distinct direct class references in reachable bytecode with an exact module entry in the current JDK inventory.
     *
     * @param classes parsed application and dependency classes
     * @param reachable reachable closed-world methods
     * @param jdkClasses current JDK class metadata with exact module names
     * @return immutable JDK module usage evidence
     */
    Report analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final List<ClassMetadata> jdkClasses
    ) {
        final Map<String, String> modulesByClass = modulesByClass(jdkClasses);
        final Set<String> countedClasses = new HashSet<>();
        final Set<String> inspectedClasses = new HashSet<>();
        final List<ModuleCount> modules = new ArrayList<>();
        for (final EntryPoint entry : reachable) {
            final ClassFile classFile = classes.get(entry.className());
            if (classFile == null) {
                continue;
            }
            inspectClass(classFile, modulesByClass, countedClasses, modules, inspectedClasses);
            final Optional<MethodInfo> method = classFile.method(entry.methodName(), entry.descriptor());
            if (method.isPresent() && method.orElseThrow().code().isPresent()) {
                inspectInstructions(
                    method.orElseThrow().code().orElseThrow().instructions(),
                    modulesByClass,
                    countedClasses,
                    modules
                );
            }
        }
        return new Report(countedClasses.size(), List.copyOf(modules));
    }

    private static void inspectClass(
        final ClassFile classFile,
        final Map<String, String> modulesByClass,
        final Set<String> countedClasses,
        final List<ModuleCount> modules,
        final Set<String> inspectedClasses
    ) {
        if (!inspectedClasses.add(classFile.name())) {
            return;
        }
        add(classFile.superName(), modulesByClass, countedClasses, modules);
        for (final String interfaceName : classFile.interfaces()) {
            add(interfaceName, modulesByClass, countedClasses, modules);
        }
    }

    private static void inspectInstructions(
        final List<Instruction> instructions,
        final Map<String, String> modulesByClass,
        final Set<String> countedClasses,
        final List<ModuleCount> modules
    ) {
        for (final Instruction instruction : instructions) {
            if (instruction.methodRef().isPresent()) {
                add(instruction.methodRef().orElseThrow().owner(), modulesByClass, countedClasses, modules);
            }
            if (instruction.fieldRef().isPresent()) {
                add(instruction.fieldRef().orElseThrow().owner(), modulesByClass, countedClasses, modules);
            }
            if (instruction.className().isPresent()) {
                add(instruction.className().orElseThrow(), modulesByClass, countedClasses, modules);
            }
        }
    }

    private static void add(
        final String className,
        final Map<String, String> modulesByClass,
        final Set<String> countedClasses,
        final List<ModuleCount> modules
    ) {
        final String module = modulesByClass.get(className);
        if (module != null && countedClasses.add(className)) {
            increment(modules, module);
        }
    }

    private static Map<String, String> modulesByClass(final List<ClassMetadata> jdkClasses) {
        final Map<String, String> result = new HashMap<>();
        for (final ClassMetadata metadata : jdkClasses) {
            if (!Strings2.isBlank(metadata.moduleName())) {
                result.put(metadata.name(), metadata.moduleName());
            }
        }
        return result;
    }

    private static void increment(final List<ModuleCount> modules, final String name) {
        for (int index = 0; index < modules.size(); index++) {
            final ModuleCount module = modules.get(index);
            final int comparison = Strings2.compareAscii(name, module.name());
            if (comparison == 0) {
                modules.set(index, new ModuleCount(name, module.reachableClassCount() + 1));
                return;
            }
            if (comparison < 0) {
                modules.add(index, new ModuleCount(name, 1));
                return;
            }
        }
        modules.add(new ModuleCount(name, 1));
    }

    private static String json(final Report report) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n")
            .append("  \"schemaVersion\": \"1\",\n")
            .append("  \"analysisScope\": \"reachable-direct-jdk-references\",\n")
            .append("  \"reachableDirectJdkClassCount\": ").append(report.reachableDirectJdkClassCount()).append(",\n")
            .append("  \"usedJdkModuleCount\": ").append(report.modules().size()).append(",\n")
            .append("  \"modules\": [\n");
        for (int index = 0; index < report.modules().size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final ModuleCount module = report.modules().get(index);
            result.append("    {\"name\": ").append(Json.string(module.name()))
                .append(", \"reachableClassCount\": ").append(module.reachableClassCount()).append("}");
        }
        return result.append("\n  ]\n}\n").toString();
    }

    private static String markdown(final Report report) {
        final StringBuilder result = new StringBuilder();
        result.append("# Reachable JDK Modules\n\n")
            .append("`javan compat` intersects direct class references in closed-world reachable bytecode and class supertypes with the exact current-JDK class inventory. ")
            .append("It reports only module names present in that inventory; it does not infer module names from package names.\n\n")
            .append("- directly referenced JDK classes: `").append(report.reachableDirectJdkClassCount()).append("`\n")
            .append("- used JDK modules: `").append(report.modules().size()).append("`\n\n")
            .append("## Modules\n\n")
            .append("| Module | Reachable classes |\n")
            .append("| --- | ---: |\n");
        if (report.modules().isEmpty()) {
            return result.append("| none | 0 |\n").toString();
        }
        for (final ModuleCount module : report.modules()) {
            result.append("| `").append(module.name()).append("` | ").append(module.reachableClassCount()).append(" |\n");
        }
        return result.toString();
    }

    record Report(int reachableDirectJdkClassCount, List<ModuleCount> modules) {
    }

    record ModuleCount(String name, int reachableClassCount) {
    }
}
