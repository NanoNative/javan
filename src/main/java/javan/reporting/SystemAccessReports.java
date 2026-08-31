package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
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
 * Writes deterministic reachable process, system-configuration, and runtime-loading evidence without executing user code.
 */
public final class SystemAccessReports {
    private static final String SYSTEM = "java/lang/System";
    private static final String RUNTIME = "java/lang/Runtime";
    private static final String PROCESS_BUILDER = "java/lang/ProcessBuilder";
    private static final String CLASS = "java/lang/Class";
    private static final String CLASS_LOADER = "java/lang/ClassLoader";
    private static final MethodRef GETENV = new MethodRef(SYSTEM, "getenv", "(Ljava/lang/String;)Ljava/lang/String;");
    private static final MethodRef GETENV_ALL = new MethodRef(SYSTEM, "getenv", "()Ljava/util/Map;");
    private static final MethodRef GET_PROPERTY = new MethodRef(SYSTEM, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
    private static final MethodRef GET_PROPERTY_DEFAULT = new MethodRef(
        SYSTEM, "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    );
    private static final MethodRef EXEC = new MethodRef(RUNTIME, "exec", "(Ljava/lang/String;)Ljava/lang/Process;");

    /**
     * Analyzes reachable bytecode and writes JSON and Markdown system-access reports.
     *
     * @param outputDirectory Javan output directory
     * @param classes parsed application and dependency classes
     * @param callGraph closed-world reachability result
     * @return written report paths
     * @throws IOException when report files cannot be written
     */
    public List<Path> write(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        final Report report = analyze(classes, callGraph.reachableMethods());
        final Path json = outputDirectory.resolve("reports/system-access.json");
        final Path markdown = outputDirectory.resolve("reports/system-access.md");
        Files2.writeString(json, json(report));
        Files2.writeString(markdown, markdown(report));
        return List.of(json, markdown);
    }

    /**
     * Counts reachable system-access APIs with only directly proven literal names.
     *
     * @param classes parsed application and dependency classes
     * @param reachable reachable application methods
     * @return immutable system-access evidence
     */
    Report analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final Counts counts = new Counts();
        for (final EntryPoint entry : reachable) {
            final Optional<MethodInfo> method = method(classes, entry);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            inspect(method.orElseThrow().code().orElseThrow().instructions(), counts);
        }
        return counts.report();
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile classFile = classes.get(entry.className());
        return classFile == null ? Optional.empty() : classFile.method(entry.methodName(), entry.descriptor());
    }

    private static void inspect(final List<Instruction> instructions, final Counts counts) {
        for (int index = 0; index < instructions.size(); index++) {
            final Optional<MethodRef> reference = instructions.get(index).methodRef();
            if (reference.isEmpty()) {
                continue;
            }
            final MethodRef target = reference.orElseThrow();
            if (GETENV.equals(target)) {
                counts.environment(target, literalArgument(instructions, index, 1, 0));
            } else if (GETENV_ALL.equals(target)) {
                counts.environment(target, Optional.empty());
            } else if (GET_PROPERTY.equals(target)) {
                counts.property(target, literalArgument(instructions, index, 1, 0));
            } else if (GET_PROPERTY_DEFAULT.equals(target)) {
                counts.property(target, literalArgument(instructions, index, 2, 0));
            }
            final Optional<String> className = classLoadName(instructions, index, target);
            if (className.isPresent() || classLoad(target)) {
                counts.classLoad(target, className);
            }
            if (nativeLibraryLoad(target)) {
                counts.nativeLibraryLoad(target, literalFinalStringArgument(instructions, index));
            }
            if (processApi(target)) {
                counts.process(target, literalExecutable(instructions, index, target));
            }
        }
    }

    private static Optional<String> literalArgument(
        final List<Instruction> instructions,
        final int invocationIndex,
        final int argumentCount,
        final int argumentIndex
    ) {
        final int firstArgument = invocationIndex - argumentCount;
        if (firstArgument < 0 || argumentIndex >= argumentCount) {
            return Optional.empty();
        }
        for (int index = firstArgument; index < invocationIndex; index++) {
            if (instructions.get(index).stringValue().isEmpty()) {
                return Optional.empty();
            }
        }
        return instructions.get(firstArgument + argumentIndex).stringValue();
    }

    private static Optional<String> literalFinalStringArgument(final List<Instruction> instructions, final int invocationIndex) {
        if (invocationIndex == 0) {
            return Optional.empty();
        }
        return instructions.get(invocationIndex - 1).stringValue();
    }

    private static boolean processApi(final MethodRef reference) {
        return PROCESS_BUILDER.equals(reference.owner()) || (RUNTIME.equals(reference.owner()) && "exec".equals(reference.name()));
    }

    private static boolean nativeLibraryLoad(final MethodRef reference) {
        return SYSTEM.equals(reference.owner())
            && ("load".equals(reference.name()) || "loadLibrary".equals(reference.name()))
            && "(Ljava/lang/String;)V".equals(reference.descriptor());
    }

    private static boolean classLoad(final MethodRef reference) {
        return (CLASS.equals(reference.owner()) && "forName".equals(reference.name()))
            || ((CLASS_LOADER.equals(reference.owner()) || reference.owner().startsWith(CLASS_LOADER + "$"))
            && "loadClass".equals(reference.name()));
    }

    private static Optional<String> classLoadName(
        final List<Instruction> instructions,
        final int invocationIndex,
        final MethodRef reference
    ) {
        if (CLASS.equals(reference.owner()) && "forName".equals(reference.name())) {
            if ("(Ljava/lang/String;)Ljava/lang/Class;".equals(reference.descriptor())) {
                return literalFinalStringArgument(instructions, invocationIndex);
            }
            if ("(Ljava/lang/Module;Ljava/lang/String;)Ljava/lang/Class;".equals(reference.descriptor())) {
                return literalFinalStringArgument(instructions, invocationIndex);
            }
            return Optional.empty();
        }
        if ((CLASS_LOADER.equals(reference.owner()) || reference.owner().startsWith(CLASS_LOADER + "$"))
            && "loadClass".equals(reference.name())) {
            if ("(Ljava/lang/String;)Ljava/lang/Class;".equals(reference.descriptor())) {
                return literalFinalStringArgument(instructions, invocationIndex);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> literalExecutable(
        final List<Instruction> instructions,
        final int invocationIndex,
        final MethodRef reference
    ) {
        if (!EXEC.equals(reference)) {
            return Optional.empty();
        }
        final Optional<String> command = literalArgument(instructions, invocationIndex, 1, 0);
        return command.isEmpty() ? Optional.empty() : executable(command.orElseThrow());
    }

    private static Optional<String> executable(final String command) {
        int start = 0;
        while (start < command.length() && whitespace(command.charAt(start))) {
            start++;
        }
        if (start == command.length() || command.charAt(start) == '\'' || command.charAt(start) == '"') {
            return Optional.empty();
        }
        int end = start;
        while (end < command.length() && !whitespace(command.charAt(end))) {
            end++;
        }
        return Optional.of(command.substring(start, end));
    }

    private static boolean whitespace(final char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\f';
    }

    private static String json(final Report report) {
        return new StringBuilder()
            .append("{\n")
            .append("  \"schemaVersion\": \"1\",\n")
            .append("  \"reachableProcessApiCallSiteCount\": ").append(report.reachableProcessApiCallSiteCount()).append(",\n")
            .append("  \"processLaunchCallSiteCount\": ").append(report.processLaunchCallSiteCount()).append(",\n")
            .append("  \"processBuilderConfigurationCallSiteCount\": ").append(report.processBuilderConfigurationCallSiteCount()).append(",\n")
            .append("  \"knownExecutableCount\": ").append(report.knownExecutables().size()).append(",\n")
            .append("  \"unknownExecutableLaunchCallSiteCount\": ").append(report.unknownExecutableLaunchCallSiteCount()).append(",\n")
            .append("  \"environmentLookupCallSiteCount\": ").append(report.environmentLookupCallSiteCount()).append(",\n")
            .append("  \"knownEnvironmentVariableCount\": ").append(report.environmentVariables().size()).append(",\n")
            .append("  \"unknownEnvironmentLookupCallSiteCount\": ").append(report.unknownEnvironmentLookupCallSiteCount()).append(",\n")
            .append("  \"propertyLookupCallSiteCount\": ").append(report.propertyLookupCallSiteCount()).append(",\n")
            .append("  \"knownPropertyKeyCount\": ").append(report.propertyKeys().size()).append(",\n")
            .append("  \"unknownPropertyLookupCallSiteCount\": ").append(report.unknownPropertyLookupCallSiteCount()).append(",\n")
            .append("  \"classLoadCallSiteCount\": ").append(report.classLoadCallSiteCount()).append(",\n")
            .append("  \"knownClassLoadTargetCount\": ").append(report.classLoadTargets().size()).append(",\n")
            .append("  \"unknownClassLoadCallSiteCount\": ").append(report.unknownClassLoadCallSiteCount()).append(",\n")
            .append("  \"nativeLibraryLoadCallSiteCount\": ").append(report.nativeLibraryLoadCallSiteCount()).append(",\n")
            .append("  \"knownNativeLibraryLoadTargetCount\": ").append(report.nativeLibraryLoadTargets().size()).append(",\n")
            .append("  \"unknownNativeLibraryLoadCallSiteCount\": ").append(report.unknownNativeLibraryLoadCallSiteCount()).append(",\n")
            .append("  \"knownExecutables\": [\n").append(namesJson(report.knownExecutables())).append("  ],\n")
            .append("  \"environmentVariables\": [\n").append(namesJson(report.environmentVariables())).append("  ],\n")
            .append("  \"propertyKeys\": [\n").append(namesJson(report.propertyKeys())).append("  ],\n")
            .append("  \"classLoadTargets\": [\n").append(namesJson(report.classLoadTargets())).append("  ],\n")
            .append("  \"nativeLibraryLoadTargets\": [\n").append(namesJson(report.nativeLibraryLoadTargets())).append("  ],\n")
            .append("  \"unknownExecutableLaunches\": [\n").append(callsJson(report.unknownExecutableLaunches())).append("  ],\n")
            .append("  \"unknownEnvironmentLookups\": [\n").append(callsJson(report.unknownEnvironmentLookups())).append("  ],\n")
            .append("  \"unknownPropertyLookups\": [\n").append(callsJson(report.unknownPropertyLookups())).append("  ],\n")
            .append("  \"unknownClassLoads\": [\n").append(callsJson(report.unknownClassLoads())).append("  ],\n")
            .append("  \"unknownNativeLibraryLoads\": [\n").append(callsJson(report.unknownNativeLibraryLoads())).append("  ],\n")
            .append("  \"processCalls\": [\n").append(callsJson(report.processCalls())).append("  ]\n")
            .append("}\n")
            .toString();
    }

    private static String namesJson(final List<NameCount> names) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < names.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final NameCount name = names.get(index);
            result.append("    {\"name\": ").append(Json.string(name.name()))
                .append(", \"count\": ").append(name.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String callsJson(final List<CallCount> calls) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < calls.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final CallCount call = calls.get(index);
            result.append("    {\"target\": ").append(Json.string(call.target()))
                .append(", \"count\": ").append(call.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String markdown(final Report report) {
        final StringBuilder result = new StringBuilder();
        result.append("# Reachable System Access\n\n");
        result.append("The compiler scans reachable process, system-configuration, and runtime-loading APIs without executing user code. ")
            .append("It records environment-variable and property names, never their values. Process command arguments are never recorded. ")
            .append("Only direct literal load targets are recorded. The report does not make dynamic class loading or runtime native-library loading supported.\n\n");
        result.append("- reachable process API call sites: `").append(report.reachableProcessApiCallSiteCount()).append("`\n");
        result.append("- process launch call sites: `").append(report.processLaunchCallSiteCount()).append("`\n");
        result.append("- ProcessBuilder configuration call sites: `").append(report.processBuilderConfigurationCallSiteCount()).append("`\n");
        result.append("- known executables: `").append(report.knownExecutables().size()).append("`\n");
        result.append("- unknown executable launches: `").append(report.unknownExecutableLaunchCallSiteCount()).append("`\n");
        result.append("- environment lookup call sites: `").append(report.environmentLookupCallSiteCount()).append("`\n");
        result.append("- known environment variable names: `").append(report.environmentVariables().size()).append("`\n");
        result.append("- unknown environment lookups: `").append(report.unknownEnvironmentLookupCallSiteCount()).append("`\n");
        result.append("- property lookup call sites: `").append(report.propertyLookupCallSiteCount()).append("`\n");
        result.append("- known property keys: `").append(report.propertyKeys().size()).append("`\n");
        result.append("- unknown property lookups: `").append(report.unknownPropertyLookupCallSiteCount()).append("`\n");
        result.append("- class-load call sites: `").append(report.classLoadCallSiteCount()).append("`\n");
        result.append("- known class-load targets: `").append(report.classLoadTargets().size()).append("`\n");
        result.append("- unknown class-load targets: `").append(report.unknownClassLoadCallSiteCount()).append("`\n");
        result.append("- native-library load call sites: `").append(report.nativeLibraryLoadCallSiteCount()).append("`\n");
        result.append("- known native-library load targets: `").append(report.nativeLibraryLoadTargets().size()).append("`\n");
        result.append("- unknown native-library load targets: `").append(report.unknownNativeLibraryLoadCallSiteCount()).append("`\n\n");
        appendNames(result, "Known Executables", "Executable", report.knownExecutables());
        appendNames(result, "Environment Variables", "Variable", report.environmentVariables());
        appendNames(result, "Property Keys", "Property", report.propertyKeys());
        appendNames(result, "Class-Load Targets", "Class", report.classLoadTargets());
        appendNames(result, "Native-Library Load Targets", "Target", report.nativeLibraryLoadTargets());
        appendCalls(result, "Unknown Executable Launches", report.unknownExecutableLaunches());
        appendCalls(result, "Unknown Environment Lookups", report.unknownEnvironmentLookups());
        appendCalls(result, "Unknown Property Lookups", report.unknownPropertyLookups());
        appendCalls(result, "Unknown Class Loads", report.unknownClassLoads());
        appendCalls(result, "Unknown Native-Library Loads", report.unknownNativeLibraryLoads());
        return result.toString();
    }

    private static void appendNames(
        final StringBuilder result,
        final String heading,
        final String name,
        final List<NameCount> values
    ) {
        result.append("## ").append(heading).append("\n\n");
        result.append("| ").append(name).append(" | Reachable call sites |\n");
        result.append("| --- | ---: |\n");
        if (values.isEmpty()) {
            result.append("| none | 0 |\n\n");
            return;
        }
        for (final NameCount value : values) {
            result.append("| `").append(markdownCell(value.name())).append("` | ").append(value.count()).append(" |\n");
        }
        result.append('\n');
    }

    private static void appendCalls(final StringBuilder result, final String heading, final List<CallCount> calls) {
        result.append("## ").append(heading).append("\n\n");
        result.append("| Target | Reachable call sites |\n");
        result.append("| --- | ---: |\n");
        if (calls.isEmpty()) {
            result.append("| none | 0 |\n\n");
            return;
        }
        for (final CallCount call : calls) {
            result.append("| `").append(markdownCell(call.target())).append("` | ").append(call.count()).append(" |\n");
        }
        result.append('\n');
    }

    private static String markdownCell(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '\\' || current == '|' || current == '`') {
                result.append('\\');
            }
            result.append(current);
        }
        return result.toString();
    }

    private static final class Counts {
        private final List<NameCount> knownExecutables = new ArrayList<>();
        private final List<NameCount> environmentVariables = new ArrayList<>();
        private final List<NameCount> propertyKeys = new ArrayList<>();
        private final List<NameCount> classLoadTargets = new ArrayList<>();
        private final List<NameCount> nativeLibraryLoadTargets = new ArrayList<>();
        private final List<CallCount> unknownExecutableLaunches = new ArrayList<>();
        private final List<CallCount> unknownEnvironmentLookups = new ArrayList<>();
        private final List<CallCount> unknownPropertyLookups = new ArrayList<>();
        private final List<CallCount> unknownClassLoads = new ArrayList<>();
        private final List<CallCount> unknownNativeLibraryLoads = new ArrayList<>();
        private final List<CallCount> processCalls = new ArrayList<>();
        private int reachableProcessApiCallSites;
        private int processLaunchCallSites;
        private int processBuilderConfigurationCallSites;
        private int unknownExecutableLaunchCallSites;
        private int environmentLookupCallSites;
        private int unknownEnvironmentLookupCallSites;
        private int propertyLookupCallSites;
        private int unknownPropertyLookupCallSites;
        private int classLoadCallSites;
        private int unknownClassLoadCallSites;
        private int nativeLibraryLoadCallSites;
        private int unknownNativeLibraryLoadCallSites;

        void process(final MethodRef target, final Optional<String> executable) {
            reachableProcessApiCallSites++;
            incrementCall(processCalls, target.display());
            if (PROCESS_BUILDER.equals(target.owner()) && ("<init>".equals(target.name()) || "command".equals(target.name()))) {
                processBuilderConfigurationCallSites++;
            }
            if (!launch(target)) {
                return;
            }
            processLaunchCallSites++;
            if (executable.isPresent()) {
                incrementName(knownExecutables, executable.orElseThrow());
            } else {
                unknownExecutableLaunchCallSites++;
                incrementCall(unknownExecutableLaunches, target.display());
            }
        }

        void environment(final MethodRef target, final Optional<String> name) {
            environmentLookupCallSites++;
            if (name.isPresent()) {
                incrementName(environmentVariables, name.orElseThrow());
            } else {
                unknownEnvironmentLookupCallSites++;
                incrementCall(unknownEnvironmentLookups, target.display());
            }
        }

        void property(final MethodRef target, final Optional<String> name) {
            propertyLookupCallSites++;
            if (name.isPresent()) {
                incrementName(propertyKeys, name.orElseThrow());
            } else {
                unknownPropertyLookupCallSites++;
                incrementCall(unknownPropertyLookups, target.display());
            }
        }

        void classLoad(final MethodRef target, final Optional<String> name) {
            classLoadCallSites++;
            if (name.isPresent()) {
                incrementName(classLoadTargets, name.orElseThrow());
            } else {
                unknownClassLoadCallSites++;
                incrementCall(unknownClassLoads, target.display());
            }
        }

        void nativeLibraryLoad(final MethodRef target, final Optional<String> name) {
            nativeLibraryLoadCallSites++;
            if (name.isPresent()) {
                incrementName(nativeLibraryLoadTargets, name.orElseThrow());
            } else {
                unknownNativeLibraryLoadCallSites++;
                incrementCall(unknownNativeLibraryLoads, target.display());
            }
        }

        Report report() {
            return new Report(
                reachableProcessApiCallSites,
                processLaunchCallSites,
                processBuilderConfigurationCallSites,
                unknownExecutableLaunchCallSites,
                environmentLookupCallSites,
                unknownEnvironmentLookupCallSites,
                propertyLookupCallSites,
                unknownPropertyLookupCallSites,
                classLoadCallSites,
                unknownClassLoadCallSites,
                nativeLibraryLoadCallSites,
                unknownNativeLibraryLoadCallSites,
                List.copyOf(knownExecutables),
                List.copyOf(environmentVariables),
                List.copyOf(propertyKeys),
                List.copyOf(classLoadTargets),
                List.copyOf(nativeLibraryLoadTargets),
                List.copyOf(unknownExecutableLaunches),
                List.copyOf(unknownEnvironmentLookups),
                List.copyOf(unknownPropertyLookups),
                List.copyOf(unknownClassLoads),
                List.copyOf(unknownNativeLibraryLoads),
                List.copyOf(processCalls)
            );
        }
    }

    private static boolean launch(final MethodRef target) {
        return (RUNTIME.equals(target.owner()) && "exec".equals(target.name()))
            || (PROCESS_BUILDER.equals(target.owner()) && "start".equals(target.name()));
    }

    private static void incrementName(final List<NameCount> values, final String name) {
        for (int index = 0; index < values.size(); index++) {
            final NameCount value = values.get(index);
            final int comparison = Strings2.compareAscii(name, value.name());
            if (comparison == 0) {
                values.set(index, new NameCount(name, value.count() + 1));
                return;
            }
            if (comparison < 0) {
                values.add(index, new NameCount(name, 1));
                return;
            }
        }
        values.add(new NameCount(name, 1));
    }

    private static void incrementCall(final List<CallCount> calls, final String target) {
        for (int index = 0; index < calls.size(); index++) {
            final CallCount call = calls.get(index);
            final int comparison = Strings2.compareAscii(target, call.target());
            if (comparison == 0) {
                calls.set(index, new CallCount(target, call.count() + 1));
                return;
            }
            if (comparison < 0) {
                calls.add(index, new CallCount(target, 1));
                return;
            }
        }
        calls.add(new CallCount(target, 1));
    }

    record Report(
        int reachableProcessApiCallSiteCount,
        int processLaunchCallSiteCount,
        int processBuilderConfigurationCallSiteCount,
        int unknownExecutableLaunchCallSiteCount,
        int environmentLookupCallSiteCount,
        int unknownEnvironmentLookupCallSiteCount,
        int propertyLookupCallSiteCount,
        int unknownPropertyLookupCallSiteCount,
        int classLoadCallSiteCount,
        int unknownClassLoadCallSiteCount,
        int nativeLibraryLoadCallSiteCount,
        int unknownNativeLibraryLoadCallSiteCount,
        List<NameCount> knownExecutables,
        List<NameCount> environmentVariables,
        List<NameCount> propertyKeys,
        List<NameCount> classLoadTargets,
        List<NameCount> nativeLibraryLoadTargets,
        List<CallCount> unknownExecutableLaunches,
        List<CallCount> unknownEnvironmentLookups,
        List<CallCount> unknownPropertyLookups,
        List<CallCount> unknownClassLoads,
        List<CallCount> unknownNativeLibraryLoads,
        List<CallCount> processCalls
    ) {
    }

    record NameCount(String name, int count) {
    }

    record CallCount(String target, int count) {
    }
}
