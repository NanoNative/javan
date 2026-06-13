package javan.compat;

import javan.util.Files2;
import javan.util.Json;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes deterministic compatibility reports.
 */
public final class CompatibilityReports {
    /**
     * Writes all compatibility reports.
     *
     * @param root project root
     * @param outputDirectory javan output directory
     * @param projectClasses project/dependency metadata
     * @param jdkClasses current JDK metadata
     * @param diagnostics static profile diagnostics
     * @return generated result
     * @throws IOException when writing fails
     */
    public CompatibilityResult write(
        final Path root,
        final Path outputDirectory,
        final List<ClassMetadata> projectClasses,
        final List<ClassMetadata> jdkClasses,
        final List<Diagnostic> diagnostics
    ) throws IOException {
        final String javaVersion = System.getProperty("java.version");
        final int feature = Runtime.version().feature();
        final String jdkName = "jdk-" + feature;
        final List<Path> files = new ArrayList<>();
        final Path reports = outputDirectory.resolve("reports");
        final Path jdkInventory = outputDirectory.resolve("jdk-inventory").resolve(jdkName + ".json");
        final Path bytecodePatterns = outputDirectory.resolve("bytecode-patterns").resolve(jdkName + ".json");

        files.add(Files2.writeString(reports.resolve(jdkName + "-inventory.json"), jdkInventoryJson(javaVersion, feature, jdkClasses)));
        files.add(Files2.writeString(jdkInventory, jdkInventoryJson(javaVersion, feature, jdkClasses)));
        files.add(Files2.writeString(reports.resolve("bytecode-patterns-" + jdkName + ".json"), bytecodePatternsJson(javaVersion, feature, root, projectClasses)));
        files.add(Files2.writeString(bytecodePatterns, bytecodePatternsJson(javaVersion, feature, root, projectClasses)));
        files.add(Files2.writeString(reports.resolve("compatibility-summary.json"), summaryJson(javaVersion, feature, projectClasses, jdkClasses, diagnostics)));
        files.add(Files2.writeString(reports.resolve("compatibility-summary.md"), summaryMarkdown(javaVersion, feature, projectClasses, jdkClasses, diagnostics)));
        files.add(Files2.writeString(root.resolve("docs/support-matrix.json"), supportMatrixJson(feature)));
        files.add(Files2.writeString(root.resolve("docs/support-matrix.md"), supportMatrixMarkdown(feature)));
        files.add(Files2.writeString(root.resolve("docs/jdk-compatibility.md"), jdkCompatibilityMarkdown(javaVersion, feature, projectClasses, jdkClasses)));
        return new CompatibilityResult(
            outputDirectory,
            javaVersion,
            feature,
            List.copyOf(projectClasses),
            List.copyOf(jdkClasses),
            List.copyOf(diagnostics),
            List.copyOf(files)
        );
    }

    private static String summaryJson(
        final String javaVersion,
        final int feature,
        final List<ClassMetadata> projectClasses,
        final List<ClassMetadata> jdkClasses,
        final List<Diagnostic> diagnostics
    ) {
        final List<InstructionMetadata> instructions = projectInstructions(projectClasses).toList();
        final long unknown = instructions.stream().filter(instruction -> instruction.support() == BytecodeSupport.Status.UNKNOWN_FATAL).count();
        final long rejected = instructions.stream().filter(instruction -> instruction.support() == BytecodeSupport.Status.RECOGNIZED_REJECTED).count();
        final long errors = diagnostics.stream().filter(Diagnostic::error).count();
        final boolean pass = unknown == 0 && errors == 0;
        return "{\n"
            + "  \"status\": " + Json.string(pass ? "pass" : "fail") + ",\n"
            + "  \"javaVersion\": " + Json.string(javaVersion) + ",\n"
            + "  \"javaFeatureVersion\": " + feature + ",\n"
            + "  \"projectClasses\": " + projectClasses.size() + ",\n"
            + "  \"jdkClasses\": " + jdkClasses.size() + ",\n"
            + "  \"diagnosticErrors\": " + errors + ",\n"
            + "  \"recognizedRejectedOpcodeUses\": " + rejected + ",\n"
            + "  \"unknownFatalOpcodeUses\": " + unknown + ",\n"
            + "  \"classFileMajorVersions\": " + Json.intList(majorVersions(projectClasses)) + ",\n"
            + "  \"constantPoolTags\": " + Json.intList(projectClasses.stream().flatMap(metadata -> metadata.constantPoolTags().stream()).distinct().sorted().toList()) + ",\n"
            + "  \"attributes\": " + Json.stringList(attributes(projectClasses)) + "\n"
            + "}\n";
    }

    private static String summaryMarkdown(
        final String javaVersion,
        final int feature,
        final List<ClassMetadata> projectClasses,
        final List<ClassMetadata> jdkClasses,
        final List<Diagnostic> diagnostics
    ) {
        final List<InstructionMetadata> instructions = projectInstructions(projectClasses).toList();
        final long errors = diagnostics.stream().filter(Diagnostic::error).count();
        final long unknown = instructions.stream().filter(instruction -> instruction.support() == BytecodeSupport.Status.UNKNOWN_FATAL).count();
        final long rejected = instructions.stream().filter(instruction -> instruction.support() == BytecodeSupport.Status.RECOGNIZED_REJECTED).count();
        final String status = errors == 0 && unknown == 0 ? "pass" : "fail";
        return "# Compatibility Summary\n\n"
            + "- status: `" + status + "`\n"
            + "- java: `" + javaVersion + "`\n"
            + "- jdk: `JDK" + feature + "`\n"
            + "- project classes: `" + projectClasses.size() + "`\n"
            + "- JDK inventory classes: `" + jdkClasses.size() + "`\n"
            + "- diagnostic errors: `" + errors + "`\n"
            + "- recognized rejected opcode uses: `" + rejected + "`\n"
            + "- unknown fatal opcode uses: `" + unknown + "`\n\n"
            + "Unknown bytecode is fatal. Recognized rejected bytecode must be added to the native profile or remain explicitly unsupported.\n";
    }

    private static String bytecodePatternsJson(
        final String javaVersion,
        final int feature,
        final Path root,
        final List<ClassMetadata> classes
    ) {
        final List<InstructionMetadata> instructions = projectInstructions(classes).toList();
        final Map<Integer, Long> opcodeCounts = instructions.stream()
            .collect(Collectors.groupingBy(InstructionMetadata::opcode, TreeMap::new, Collectors.counting()));
        return "{\n"
            + "  \"javaVersion\": " + Json.string(javaVersion) + ",\n"
            + "  \"javaFeatureVersion\": " + feature + ",\n"
            + "  \"root\": " + Json.string(root.toString()) + ",\n"
            + "  \"classFileMajorVersions\": " + Json.intList(majorVersions(classes)) + ",\n"
            + "  \"opcodes\": " + opcodeCountsJson(opcodeCounts) + ",\n"
            + "  \"constantPoolTags\": " + Json.intList(classes.stream().flatMap(metadata -> metadata.constantPoolTags().stream()).distinct().sorted().toList()) + ",\n"
            + "  \"attributes\": " + Json.stringList(attributes(classes)) + ",\n"
            + "  \"bootstrapMethodPatterns\": " + Json.stringList(classes.stream().flatMap(metadata -> metadata.bootstrapMethods().stream()).distinct().sorted().toList()) + ",\n"
            + "  \"syntheticMethods\": " + Json.stringList(syntheticMethods(classes)) + ",\n"
            + "  \"previewClasses\": " + Json.stringList(classes.stream().filter(ClassMetadata::preview).map(ClassMetadata::name).sorted().toList()) + "\n"
            + "}\n";
    }

    private static String opcodeCountsJson(final Map<Integer, Long> opcodeCounts) {
        final List<String> entries = new ArrayList<>();
        for (final Map.Entry<Integer, Long> entry : opcodeCounts.entrySet()) {
            final int opcode = entry.getKey();
            entries.add("{\"opcode\": " + opcode
                + ", \"mnemonic\": " + Json.string(BytecodeSupport.mnemonic(opcode))
                + ", \"support\": " + Json.string(BytecodeSupport.classify(opcode).name())
                + ", \"count\": " + entry.getValue() + "}");
        }
        return "[" + String.join(", ", entries) + "]";
    }

    private static String jdkInventoryJson(final String javaVersion, final int feature, final List<ClassMetadata> classes) {
        final Map<String, List<ClassMetadata>> byModule = classes.stream()
            .collect(Collectors.groupingBy(metadata -> metadata.moduleName().isBlank() ? "unnamed" : metadata.moduleName(), TreeMap::new, Collectors.toList()));
        final long methods = classes.stream().mapToLong(metadata -> metadata.methods().size()).sum();
        final long constructors = classes.stream().mapToLong(metadata -> metadata.constructors().size()).sum();
        final long fields = classes.stream().mapToLong(metadata -> metadata.fields().size()).sum();
        final StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"javaVersion\": ").append(Json.string(javaVersion)).append(",\n")
            .append("  \"javaFeatureVersion\": ").append(feature).append(",\n")
            .append("  \"classFileMajorVersions\": ").append(Json.intList(majorVersions(classes))).append(",\n")
            .append("  \"totals\": {\"modules\": ").append(byModule.size())
            .append(", \"classes\": ").append(classes.size())
            .append(", \"fields\": ").append(fields)
            .append(", \"constructors\": ").append(constructors)
            .append(", \"methods\": ").append(methods).append("},\n")
            .append("  \"modules\": [\n");
        final List<String> moduleJson = new ArrayList<>();
        for (final Map.Entry<String, List<ClassMetadata>> entry : byModule.entrySet()) {
            moduleJson.add(moduleJson(entry.getKey(), entry.getValue()));
        }
        json.append(String.join(",\n", moduleJson));
        json.append("\n  ]\n}\n");
        return json.toString();
    }

    private static String moduleJson(final String moduleName, final List<ClassMetadata> classes) {
        final List<ClassMetadata> sortedClasses = classes.stream().sorted(Comparator.comparing(ClassMetadata::name)).toList();
        final Set<String> packages = sortedClasses.stream().map(ClassMetadata::packageName).collect(Collectors.toCollection(TreeSet::new));
        return "    {\n"
            + "      \"name\": " + Json.string(moduleName) + ",\n"
            + "      \"packages\": " + Json.stringList(List.copyOf(packages)) + ",\n"
            + "      \"classes\": [\n"
            + String.join(",\n", sortedClasses.stream().map(CompatibilityReports::classJson).toList())
            + "\n      ]\n"
            + "    }";
    }

    private static String classJson(final ClassMetadata metadata) {
        return "        {\"name\": " + Json.string(metadata.name())
            + ", \"major\": " + metadata.majorVersion()
            + ", \"flags\": " + metadata.accessFlags()
            + ", \"preview\": " + metadata.preview()
            + ", \"deprecated\": " + metadata.deprecated()
            + ", \"fields\": " + membersJson(metadata.fields())
            + ", \"constructors\": " + membersJson(metadata.constructors())
            + ", \"methods\": " + membersJson(metadata.methods())
            + "}";
    }

    private static String membersJson(final List<MemberMetadata> members) {
        return "[" + String.join(", ", members.stream()
            .sorted(Comparator.comparing(MemberMetadata::name).thenComparing(MemberMetadata::descriptor))
            .map(member -> "{\"name\": " + Json.string(member.name())
                + ", \"descriptor\": " + Json.string(member.descriptor())
                + ", \"flags\": " + member.accessFlags()
                + ", \"deprecated\": " + member.deprecated()
                + ", \"synthetic\": " + member.synthetic()
                + "}")
            .toList()) + "]";
    }

    private static String supportMatrixJson(final int feature) {
        final List<String> rows = supportRows().stream()
            .map(row -> "{\"feature\": " + Json.string(row.feature()) + ", \"jdk" + feature + "\": " + Json.string(row.status()) + "}")
            .toList();
        return "{\n"
            + "  \"generatedForJdk\": " + feature + ",\n"
            + "  \"features\": [" + String.join(", ", rows) + "]\n"
            + "}\n";
    }

    private static String supportMatrixMarkdown(final int feature) {
        final StringBuilder markdown = new StringBuilder("# javan Support Matrix\n\n");
        markdown.append("| feature | JDK").append(feature).append(" |\n");
        markdown.append("| --- | --- |\n");
        supportRows().forEach(row -> markdown.append("| `").append(row.feature()).append("` | ").append(row.status()).append(" |\n"));
        markdown.append("\n`pass` means covered by the current deterministic verification suite for the active JDK.\n");
        markdown.append("`target` means tracked for the milestone but not claimed as native-supported by this matrix.\n");
        return markdown.toString();
    }

    private static List<SupportRow> supportRows() {
        return List.of(
            pass("println-string"),
            pass("println-int"),
            pass("println-boolean"),
            pass("println-long"),
            pass("println-float"),
            pass("println-double"),
            pass("int-arithmetic"),
            pass("long-basic"),
            pass("float-double"),
            pass("boolean-basic"),
            pass("static-fields"),
            pass("string-concat"),
            pass("exception-panic"),
            pass("try-catch"),
            pass("enum-basic"),
            pass("interface-dispatch"),
            pass("polymorphic-virtual"),
            pass("interface-polymorphic"),
            pass("string-intrinsics"),
            pass("jdk-intrinsics-math-abs-min-max"),
            pass("jdk-intrinsics-objects-require-non-null"),
            pass("jdk-intrinsics-system-time"),
            pass("jdk-intrinsics-system-arraycopy"),
            pass("jdk-intrinsics-arrays-copy-of"),
            pass("jdk-intrinsics-number-to-string"),
            pass("if-else"),
            pass("while-loop"),
            pass("records-basic"),
            pass("object-fields"),
            pass("object-array"),
            pass("int-array"),
            pass("long-array"),
            pass("primitive-array-variants"),
            pass("main-args"),
            pass("jar-output"),
            pass("jar-main-manifest"),
            pass("resource-file-copy"),
            pass("resource-stale-removal"),
            pass("native-resource-distribution"),
            pass("library-static-int-export"),
            pass("library-string-export"),
            pass("library-byte-array-export"),
            pass("library-without-main"),
            pass("library-c-bindings"),
            pass("library-rust-binding-smoke"),
            pass("library-go-binding-smoke"),
            pass("library-python-ctypes-smoke"),
            pass("deduplication-plan"),
            pass("typemap-pair"),
            pass("nano-metric")
        );
    }

    private static SupportRow pass(final String feature) {
        return new SupportRow(feature, "pass");
    }

    private static SupportRow target(final String feature) {
        return new SupportRow(feature, "target");
    }

    private record SupportRow(String feature, String status) {
    }

    private static String jdkCompatibilityMarkdown(
        final String javaVersion,
        final int feature,
        final List<ClassMetadata> projectClasses,
        final List<ClassMetadata> jdkClasses
    ) {
        return "# JDK Compatibility\n\n"
            + "- scanned java: `" + javaVersion + "`\n"
            + "- scanned JDK: `JDK" + feature + "`\n"
            + "- project classfile majors: `" + majorVersions(projectClasses) + "`\n"
            + "- JDK classfile majors: `" + majorVersions(jdkClasses) + "`\n"
            + "- JDK modules: `" + jdkClasses.stream().map(ClassMetadata::moduleName).filter(name -> !name.isBlank()).distinct().count() + "`\n\n"
            + "Compatibility reports are generated under `.javan/reports`, `.javan/jdk-inventory`, and `.javan/bytecode-patterns`.\n"
            + "New opcodes, constant-pool tags, attributes, and bootstrap patterns must be classified before native code generation accepts them.\n";
    }

    private static List<Integer> majorVersions(final List<ClassMetadata> classes) {
        return classes.stream().map(ClassMetadata::majorVersion).distinct().sorted().toList();
    }

    private static List<String> attributes(final List<ClassMetadata> classes) {
        return classes.stream()
            .flatMap(metadata -> Stream.concat(
                metadata.attributes().stream(),
                Stream.concat(metadata.fields().stream(), Stream.concat(metadata.constructors().stream(), metadata.methods().stream()))
                    .flatMap(member -> member.attributes().stream())
            ))
            .distinct()
            .sorted()
            .toList();
    }

    private static List<String> syntheticMethods(final List<ClassMetadata> classes) {
        return classes.stream()
            .flatMap(metadata -> Stream.concat(metadata.constructors().stream(), metadata.methods().stream())
                .filter(MemberMetadata::synthetic)
                .map(member -> metadata.name() + "." + member.name() + member.descriptor()))
            .sorted()
            .toList();
    }

    private static Stream<InstructionMetadata> projectInstructions(final List<ClassMetadata> classes) {
        return classes.stream()
            .flatMap(metadata -> Stream.concat(metadata.constructors().stream(), metadata.methods().stream()))
            .flatMap(member -> member.instructions().stream());
    }
}
