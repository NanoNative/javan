package javan.compat;

import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        final int feature = javaFeature(javaVersion);
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
        files.add(Files2.writeString(root.resolve("doc/support-matrix.json"), supportMatrixJson(feature)));
        files.add(Files2.writeString(root.resolve("doc/support-matrix.md"), supportMatrixMarkdown(feature)));
        files.add(Files2.writeString(root.resolve("doc/jdk-compatibility.md"), jdkCompatibilityMarkdown(javaVersion, feature, projectClasses, jdkClasses)));
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

    private static int javaFeature(final String version) {
        if (Strings2.isBlank(version)) {
            return 0;
        }
        int start = 0;
        if (version.length() > 2 && version.charAt(0) == '1' && version.charAt(1) == '.') {
            start = 2;
        }
        int result = 0;
        for (int index = start; index < version.length(); index++) {
            final char ch = version.charAt(index);
            if (ch < '0' || ch > '9') {
                break;
            }
            result = (result * 10) + (ch - '0');
        }
        return result;
    }

    private static String summaryJson(
        final String javaVersion,
        final int feature,
        final List<ClassMetadata> projectClasses,
        final List<ClassMetadata> jdkClasses,
        final List<Diagnostic> diagnostics
    ) {
        final List<InstructionMetadata> instructions = projectInstructions(projectClasses);
        final long unknown = countInstructionsWithStatus(instructions, BytecodeSupport.Status.UNKNOWN_FATAL);
        final long rejected = countInstructionsWithStatus(instructions, BytecodeSupport.Status.RECOGNIZED_REJECTED);
        final long errors = countDiagnosticErrors(diagnostics);
        final boolean pass = unknown == 0 && errors == 0;
        final InventoryTotals jdkTotals = inventoryTotals(jdkClasses);
        return "{\n"
            + "  \"status\": " + Json.string(pass ? "pass" : "fail") + ",\n"
            + "  \"javaVersion\": " + Json.string(javaVersion) + ",\n"
            + "  \"javaFeatureVersion\": " + feature + ",\n"
            + "  \"projectClasses\": " + projectClasses.size() + ",\n"
            + "  \"jdkClasses\": " + jdkClasses.size() + ",\n"
            + "  \"jdkInventory\": {\"classes\": " + jdkTotals.classes()
            + ", \"fields\": " + jdkTotals.fields()
            + ", \"constructors\": " + jdkTotals.constructors()
            + ", \"methods\": " + jdkTotals.methods() + "},\n"
            + "  \"jdkCoverageAccounting\": {\"implemented\": false, \"note\": "
            + Json.string("inventory is generated; supported/rejected/unknown JDK API variant accounting is planned") + "},\n"
            + "  \"diagnosticErrors\": " + errors + ",\n"
            + "  \"recognizedRejectedOpcodeUses\": " + rejected + ",\n"
            + "  \"unknownFatalOpcodeUses\": " + unknown + ",\n"
            + "  \"classFileMajorVersions\": " + Json.intList(majorVersions(projectClasses)) + ",\n"
            + "  \"constantPoolTags\": " + Json.intList(constantPoolTags(projectClasses)) + ",\n"
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
        final List<InstructionMetadata> instructions = projectInstructions(projectClasses);
        final long errors = countDiagnosticErrors(diagnostics);
        final long unknown = countInstructionsWithStatus(instructions, BytecodeSupport.Status.UNKNOWN_FATAL);
        final long rejected = countInstructionsWithStatus(instructions, BytecodeSupport.Status.RECOGNIZED_REJECTED);
        final String status = errors == 0 && unknown == 0 ? "pass" : "fail";
        final InventoryTotals jdkTotals = inventoryTotals(jdkClasses);
        return "# Compatibility Summary\n\n"
            + "- status: `" + status + "`\n"
            + "- java: `" + javaVersion + "`\n"
            + "- jdk: `JDK" + feature + "`\n"
            + "- project classes: `" + projectClasses.size() + "`\n"
            + "- JDK inventory classes: `" + jdkClasses.size() + "`\n"
            + "- JDK inventory fields: `" + jdkTotals.fields() + "`\n"
            + "- JDK inventory constructors: `" + jdkTotals.constructors() + "`\n"
            + "- JDK inventory methods: `" + jdkTotals.methods() + "`\n"
            + "- JDK coverage accounting: `planned`\n"
            + "- diagnostic errors: `" + errors + "`\n"
            + "- recognized rejected opcode uses: `" + rejected + "`\n"
            + "- unknown fatal opcode uses: `" + unknown + "`\n\n"
            + "Inventory counts say what exists in the scanned JDK. They do not claim native support.\n"
            + "Unknown bytecode is fatal. Recognized rejected bytecode must be added to the native profile or remain explicitly unsupported.\n";
    }

    private static String bytecodePatternsJson(
        final String javaVersion,
        final int feature,
        final Path root,
        final List<ClassMetadata> classes
    ) {
        final List<InstructionMetadata> instructions = projectInstructions(classes);
        final List<OpcodeCount> opcodeCounts = opcodeCounts(instructions);
        return "{\n"
            + "  \"javaVersion\": " + Json.string(javaVersion) + ",\n"
            + "  \"javaFeatureVersion\": " + feature + ",\n"
            + "  \"root\": " + Json.string(root.toString()) + ",\n"
            + "  \"classFileMajorVersions\": " + Json.intList(majorVersions(classes)) + ",\n"
            + "  \"opcodes\": " + opcodeCountsJson(opcodeCounts) + ",\n"
            + "  \"constantPoolTags\": " + Json.intList(constantPoolTags(classes)) + ",\n"
            + "  \"attributes\": " + Json.stringList(attributes(classes)) + ",\n"
            + "  \"bootstrapMethodPatterns\": " + Json.stringList(bootstrapMethodPatterns(classes)) + ",\n"
            + "  \"syntheticMethods\": " + Json.stringList(syntheticMethods(classes)) + ",\n"
            + "  \"previewClasses\": " + Json.stringList(previewClasses(classes)) + "\n"
            + "}\n";
    }

    private static String opcodeCountsJson(final List<OpcodeCount> opcodeCounts) {
        final StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < opcodeCounts.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            final OpcodeCount entry = opcodeCounts.get(index);
            final int opcode = entry.opcode();
            json.append("{\"opcode\": ").append(opcode)
                .append(", \"mnemonic\": ").append(Json.string(BytecodeSupport.mnemonic(opcode)))
                .append(", \"support\": ").append(Json.string(BytecodeSupport.classify(opcode).name()))
                .append(", \"count\": ").append(entry.count())
                .append("}");
        }
        json.append(']');
        return json.toString();
    }

    private static String jdkInventoryJson(final String javaVersion, final int feature, final List<ClassMetadata> classes) {
        final List<ModuleGroup> byModule = moduleGroups(classes);
        final InventoryTotals totals = inventoryTotals(classes);
        final StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"javaVersion\": ").append(Json.string(javaVersion)).append(",\n")
            .append("  \"javaFeatureVersion\": ").append(feature).append(",\n")
            .append("  \"classFileMajorVersions\": ").append(Json.intList(majorVersions(classes))).append(",\n")
            .append("  \"totals\": {\"modules\": ").append(byModule.size())
            .append(", \"classes\": ").append(totals.classes())
            .append(", \"fields\": ").append(totals.fields())
            .append(", \"constructors\": ").append(totals.constructors())
            .append(", \"methods\": ").append(totals.methods()).append("},\n")
            .append("  \"modules\": [\n");
        for (int index = 0; index < byModule.size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            final ModuleGroup entry = byModule.get(index);
            json.append(moduleJson(entry.moduleName(), entry.classes()));
        }
        json.append("\n  ]\n}\n");
        return json.toString();
    }

    private static String moduleJson(final String moduleName, final List<ClassMetadata> classes) {
        final List<ClassMetadata> sortedClasses = sortedClasses(classes);
        final List<String> packages = packages(sortedClasses);
        final StringBuilder json = new StringBuilder();
        json.append("    {\n")
            .append("      \"name\": ").append(Json.string(moduleName)).append(",\n")
            .append("      \"packages\": ").append(Json.stringList(packages)).append(",\n")
            .append("      \"classes\": [\n");
        for (int index = 0; index < sortedClasses.size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            json.append(classJson(sortedClasses.get(index)));
        }
        json.append("\n      ]\n")
            .append("    }");
        return json.toString();
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
        final List<MemberMetadata> sortedMembers = sortedMembers(members);
        final StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < sortedMembers.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            final MemberMetadata member = sortedMembers.get(index);
            json.append("{\"name\": ").append(Json.string(member.name()))
                .append(", \"descriptor\": ").append(Json.string(member.descriptor()))
                .append(", \"flags\": ").append(member.accessFlags())
                .append(", \"deprecated\": ").append(member.deprecated())
                .append(", \"synthetic\": ").append(member.synthetic())
                .append("}");
        }
        json.append(']');
        return json.toString();
    }

    private static String supportMatrixJson(final int feature) {
        final List<SupportRow> rows = supportRows();
        final StringBuilder features = new StringBuilder("[");
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) {
                features.append(", ");
            }
            final SupportRow row = rows.get(index);
            features.append("{\"feature\": ").append(Json.string(row.feature()))
                .append(", \"jdk").append(feature).append("\": ").append(Json.string(row.status()))
                .append("}");
        }
        features.append(']');
        return "{\n"
            + "  \"generatedForJdk\": " + feature + ",\n"
            + "  \"statusLegend\": {\"pass\": \"implemented and tested for the named scenario\", "
            + "\"scoped\": \"supported subset with explicit rejection for unsupported shapes\", "
            + "\"target\": \"planned and not claimed as supported\", "
            + "\"rejected\": \"rejected by design for native output\"},\n"
            + "  \"jdkCoverageAccounting\": {\"implemented\": false, \"rule\": "
            + Json.string("done = supported variants + rejected variants; unknown leftovers must be 0 for a release-gated JDK") + "},\n"
            + "  \"features\": " + features.toString() + "\n"
            + "}\n";
    }

    private static String supportMatrixMarkdown(final int feature) {
        final StringBuilder markdown = new StringBuilder("# javan Support Matrix\n\n");
        markdown.append("This matrix tracks named javan/JDK behavior scenarios. It is not a claim that every class or method in the scanned JDK is native-supported.\n\n");
        markdown.append("| feature | JDK").append(feature).append(" |\n");
        markdown.append("| --- | --- |\n");
        final List<SupportRow> rows = supportRows();
        for (int index = 0; index < rows.size(); index++) {
            final SupportRow row = rows.get(index);
            markdown.append("| `").append(row.feature()).append("` | ").append(row.status()).append(" |\n");
        }
        markdown.append("\n`pass` means covered by the current deterministic verification suite for the active JDK.\n");
        markdown.append("`scoped` means a supported subset exists and unsupported shapes must be rejected clearly.\n");
        markdown.append("`target` means tracked for the milestone but not claimed as native-supported by this matrix.\n");
        markdown.append("JDK coverage accounting is planned: `done = supported variants + rejected variants`, and unknown leftovers must be `0` for a release-gated JDK.\n");
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
            pass("int-bitwise-and"),
            pass("long-basic"),
            pass("float-double"),
            pass("boolean-basic"),
            pass("static-fields"),
            pass("string-concat"),
            pass("exception-panic"),
            scoped("try-catch"),
            scoped("enum-basic"),
            scoped("enum-ordinal"),
            scoped("enum-values"),
            scoped("enum-switch"),
            scoped("interface-dispatch"),
            scoped("polymorphic-virtual"),
            scoped("interface-polymorphic"),
            scoped("string-intrinsics"),
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
            pass("object-array-clone"),
            pass("int-array-clone"),
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
            target("typemap-pair"),
            target("nano-metric")
        );
    }

    private static SupportRow pass(final String feature) {
        return new SupportRow(feature, "pass");
    }

    private static SupportRow scoped(final String feature) {
        return new SupportRow(feature, "scoped");
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
        final InventoryTotals totals = inventoryTotals(jdkClasses);
        return "# JDK Compatibility\n\n"
            + "- scanned java: `" + javaVersion + "`\n"
            + "- scanned JDK: `JDK" + feature + "`\n"
            + "- project classfile majors: `" + intListText(majorVersions(projectClasses)) + "`\n"
            + "- JDK classfile majors: `" + intListText(majorVersions(jdkClasses)) + "`\n"
            + "- JDK modules: `" + jdkModuleCount(jdkClasses) + "`\n\n"
            + "## Inventory Totals\n\n"
            + "| item | count |\n"
            + "| --- | ---: |\n"
            + "| classes | " + totals.classes() + " |\n"
            + "| fields | " + totals.fields() + " |\n"
            + "| constructors | " + totals.constructors() + " |\n"
            + "| methods | " + totals.methods() + " |\n\n"
            + "## Support Accounting\n\n"
            + "Inventory is implemented. Full JDK API variant accounting is planned.\n\n"
            + "Release-gated JDKs must report:\n\n"
            + "```text\n"
            + "done = supported variants + rejected variants\n"
            + "leftovers = unknown variants\n"
            + "leftovers must be 0\n"
            + "```\n\n"
            + "Compatibility reports are generated under `.javan/reports`, `.javan/jdk-inventory`, and `.javan/bytecode-patterns`.\n"
            + "New opcodes, constant-pool tags, attributes, and bootstrap patterns must be classified before native code generation accepts them.\n";
    }

    private static String intListText(final List<Integer> values) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index).intValue());
        }
        result.append(']');
        return result.toString();
    }

    private static InventoryTotals inventoryTotals(final List<ClassMetadata> classes) {
        long fields = 0;
        long constructors = 0;
        long methods = 0;
        for (int index = 0; index < classes.size(); index++) {
            final ClassMetadata metadata = classes.get(index);
            fields += metadata.fields().size();
            constructors += metadata.constructors().size();
            methods += metadata.methods().size();
        }
        return new InventoryTotals(classes.size(), fields, constructors, methods);
    }

    private record InventoryTotals(long classes, long fields, long constructors, long methods) {
    }

    private static List<Integer> majorVersions(final List<ClassMetadata> classes) {
        final List<Integer> result = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            addIntSortedUnique(result, classes.get(index).majorVersion());
        }
        return List.copyOf(result);
    }

    private static List<Integer> constantPoolTags(final List<ClassMetadata> classes) {
        final List<Integer> result = new ArrayList<>();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            final List<Integer> tags = classes.get(classIndex).constantPoolTags();
            for (int tagIndex = 0; tagIndex < tags.size(); tagIndex++) {
                addIntSortedUnique(result, tags.get(tagIndex).intValue());
            }
        }
        return List.copyOf(result);
    }

    private static List<String> attributes(final List<ClassMetadata> classes) {
        final List<String> result = new ArrayList<>();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            final ClassMetadata metadata = classes.get(classIndex);
            addStringsSortedUnique(result, metadata.attributes());
            addMemberAttributes(result, metadata.fields());
            addMemberAttributes(result, metadata.constructors());
            addMemberAttributes(result, metadata.methods());
        }
        return List.copyOf(result);
    }

    private static void addMemberAttributes(final List<String> result, final List<MemberMetadata> members) {
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            addStringsSortedUnique(result, members.get(memberIndex).attributes());
        }
    }

    private static List<String> bootstrapMethodPatterns(final List<ClassMetadata> classes) {
        final List<String> result = new ArrayList<>();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            addStringsSortedUnique(result, classes.get(classIndex).bootstrapMethods());
        }
        return List.copyOf(result);
    }

    private static List<String> previewClasses(final List<ClassMetadata> classes) {
        final List<String> result = new ArrayList<>();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            final ClassMetadata metadata = classes.get(classIndex);
            if (metadata.preview()) {
                addStringSorted(result, metadata.name());
            }
        }
        return List.copyOf(result);
    }

    private static List<String> syntheticMethods(final List<ClassMetadata> classes) {
        final List<String> result = new ArrayList<>();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            final ClassMetadata metadata = classes.get(classIndex);
            addSyntheticMethods(result, metadata.name(), metadata.constructors());
            addSyntheticMethods(result, metadata.name(), metadata.methods());
        }
        return List.copyOf(result);
    }

    private static void addSyntheticMethods(
        final List<String> result,
        final String className,
        final List<MemberMetadata> members
    ) {
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            final MemberMetadata member = members.get(memberIndex);
            if (member.synthetic()) {
                addStringSorted(result, className + "." + member.name() + member.descriptor());
            }
        }
    }

    private static List<InstructionMetadata> projectInstructions(final List<ClassMetadata> classes) {
        final List<InstructionMetadata> result = new ArrayList<>();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            final ClassMetadata metadata = classes.get(classIndex);
            addInstructions(result, metadata.constructors());
            addInstructions(result, metadata.methods());
        }
        return List.copyOf(result);
    }

    private static void addInstructions(final List<InstructionMetadata> result, final List<MemberMetadata> members) {
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            final List<InstructionMetadata> instructions = members.get(memberIndex).instructions();
            for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++) {
                result.add(instructions.get(instructionIndex));
            }
        }
    }

    private static long countInstructionsWithStatus(
        final List<InstructionMetadata> instructions,
        final BytecodeSupport.Status status
    ) {
        long count = 0;
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).support() == status) {
                count++;
            }
        }
        return count;
    }

    private static long countDiagnosticErrors(final List<Diagnostic> diagnostics) {
        long count = 0;
        for (int index = 0; index < diagnostics.size(); index++) {
            if (diagnostics.get(index).error()) {
                count++;
            }
        }
        return count;
    }

    private static List<OpcodeCount> opcodeCounts(final List<InstructionMetadata> instructions) {
        final List<OpcodeCount> result = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            addOpcodeCount(result, instructions.get(index).opcode());
        }
        return List.copyOf(result);
    }

    private static void addOpcodeCount(final List<OpcodeCount> result, final int opcode) {
        int index = 0;
        while (index < result.size() && result.get(index).opcode() < opcode) {
            index++;
        }
        if (index < result.size() && result.get(index).opcode() == opcode) {
            final OpcodeCount current = result.get(index);
            result.set(index, new OpcodeCount(opcode, current.count() + 1));
        } else {
            result.add(index, new OpcodeCount(opcode, 1));
        }
    }

    private record OpcodeCount(int opcode, long count) {
    }

    private static List<ModuleGroup> moduleGroups(final List<ClassMetadata> classes) {
        final List<ModuleGroup> result = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            final ClassMetadata metadata = classes.get(index);
            final String moduleName = Strings2.isBlank(metadata.moduleName()) ? "unnamed" : metadata.moduleName();
            addClassToModule(result, moduleName, metadata);
        }
        return List.copyOf(result);
    }

    private static void addClassToModule(
        final List<ModuleGroup> result,
        final String moduleName,
        final ClassMetadata metadata
    ) {
        int index = 0;
        while (index < result.size() && Strings2.compareAscii(result.get(index).moduleName(), moduleName) < 0) {
            index++;
        }
        if (index < result.size() && result.get(index).moduleName().equals(moduleName)) {
            result.get(index).classes().add(metadata);
        } else {
            final List<ClassMetadata> classes = new ArrayList<>();
            classes.add(metadata);
            result.add(index, new ModuleGroup(moduleName, classes));
        }
    }

    private record ModuleGroup(String moduleName, List<ClassMetadata> classes) {
    }

    private static List<ClassMetadata> sortedClasses(final List<ClassMetadata> classes) {
        final List<ClassMetadata> result = new ArrayList<>(classes);
        sortClasses(result, 0, result.size());
        return List.copyOf(result);
    }

    private static void sortClasses(final List<ClassMetadata> values, final int from, final int to) {
        if (to - from <= 1) {
            return;
        }
        final int middle = from + ((to - from) / 2);
        sortClasses(values, from, middle);
        sortClasses(values, middle, to);
        mergeClasses(values, from, middle, to);
    }

    private static void mergeClasses(final List<ClassMetadata> values, final int from, final int middle, final int to) {
        final List<ClassMetadata> left = new ArrayList<>();
        for (int index = from; index < middle; index++) {
            left.add(values.get(index));
        }
        int leftIndex = 0;
        int rightIndex = middle;
        int target = from;
        while (leftIndex < left.size() && rightIndex < to) {
            final ClassMetadata leftValue = left.get(leftIndex);
            final ClassMetadata rightValue = values.get(rightIndex);
            if (Strings2.compareAscii(leftValue.name(), rightValue.name()) <= 0) {
                values.set(target, leftValue);
                leftIndex++;
            } else {
                values.set(target, rightValue);
                rightIndex++;
            }
            target++;
        }
        while (leftIndex < left.size()) {
            values.set(target, left.get(leftIndex));
            leftIndex++;
            target++;
        }
    }

    private static List<String> packages(final List<ClassMetadata> classes) {
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            addStringSortedUnique(result, classes.get(index).packageName());
        }
        return List.copyOf(result);
    }

    private static List<MemberMetadata> sortedMembers(final List<MemberMetadata> members) {
        final List<MemberMetadata> result = new ArrayList<>();
        for (int index = 0; index < members.size(); index++) {
            final MemberMetadata member = members.get(index);
            int target = 0;
            while (target < result.size() && compareMembers(result.get(target), member) <= 0) {
                target++;
            }
            result.add(target, member);
        }
        return List.copyOf(result);
    }

    private static int compareMembers(final MemberMetadata left, final MemberMetadata right) {
        final int nameComparison = Strings2.compareAscii(left.name(), right.name());
        if (nameComparison != 0) {
            return nameComparison;
        }
        return Strings2.compareAscii(left.descriptor(), right.descriptor());
    }

    private static void addStringsSortedUnique(final List<String> result, final List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            addStringSortedUnique(result, values.get(index));
        }
    }

    private static void addStringSortedUnique(final List<String> result, final String value) {
        int index = 0;
        while (index < result.size() && Strings2.compareAscii(result.get(index), value) < 0) {
            index++;
        }
        if (index >= result.size() || !result.get(index).equals(value)) {
            result.add(index, value);
        }
    }

    private static void addStringSorted(final List<String> result, final String value) {
        int index = 0;
        while (index < result.size() && Strings2.compareAscii(result.get(index), value) <= 0) {
            index++;
        }
        result.add(index, value);
    }

    private static void addIntSortedUnique(final List<Integer> result, final int value) {
        int index = 0;
        while (index < result.size() && result.get(index).intValue() < value) {
            index++;
        }
        if (index >= result.size() || result.get(index).intValue() != value) {
            result.add(index, Integer.valueOf(value));
        }
    }

    private static long jdkModuleCount(final List<ClassMetadata> classes) {
        final List<String> modules = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            final String moduleName = classes.get(index).moduleName();
            if (!Strings2.isBlank(moduleName)) {
                addStringSortedUnique(modules, moduleName);
            }
        }
        return modules.size();
    }
}
