package javan.compat;

import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.ForbiddenApiRules;

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
        final String jdkName = jdkName(feature);
        final List<Path> files = new ArrayList<>();
        final Path reports = outputDirectory.resolve("reports");
        final Path jdkInventory = outputDirectory.resolve("jdk-inventory").resolve(suffixedJsonName(jdkName));
        final Path bytecodePatterns = outputDirectory.resolve("bytecode-patterns").resolve(suffixedJsonName(jdkName));

        files.add(Files2.writeString(reports.resolve(inventoryReportName(jdkName)), jdkInventoryJson(javaVersion, feature, jdkClasses)));
        files.add(Files2.writeString(jdkInventory, jdkInventoryJson(javaVersion, feature, jdkClasses)));
        files.add(Files2.writeString(reports.resolve(bytecodePatternReportName(jdkName)), bytecodePatternsJson(javaVersion, feature, root, projectClasses)));
        files.add(Files2.writeString(bytecodePatterns, bytecodePatternsJson(javaVersion, feature, root, projectClasses)));
        files.add(Files2.writeString(reports.resolve("compatibility-summary.json"), summaryJson(javaVersion, feature, projectClasses, jdkClasses, diagnostics)));
        files.add(Files2.writeString(reports.resolve("compatibility-summary.md"), summaryMarkdown(javaVersion, feature, projectClasses, jdkClasses, diagnostics)));
        files.add(Files2.writeString(root.resolve("doc/status/support-matrix.json"), supportMatrixJson(feature)));
        files.add(Files2.writeString(root.resolve("doc/status/support-matrix.md"), supportMatrixMarkdown(feature)));
        files.add(Files2.writeString(root.resolve("doc/status/jdk-compatibility.md"), jdkCompatibilityMarkdown(javaVersion, feature, projectClasses, jdkClasses)));
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
        final JdkCallableSupportTotals callableSupport = jdkCallableSupportTotals(jdkClasses);
        final List<SupportRow> rows = supportRows();
        final int passRows = countSupportRows(rows, "pass");
        final int scopedRows = countSupportRows(rows, "scoped");
        final int targetRows = countSupportRows(rows, "target");
        final int rejectedRows = countSupportRows(rows, "rejected");
        final int accountedRows = passRows + rejectedRows;
        final int unaccountedRows = rows.size() - accountedRows;
        final StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"status\": ").append(Json.string(pass ? "pass" : "fail")).append(",\n")
            .append("  \"javaVersion\": ").append(Json.string(javaVersion)).append(",\n")
            .append("  \"javaFeatureVersion\": ").append(feature).append(",\n")
            .append("  \"projectClasses\": ").append(projectClasses.size()).append(",\n")
            .append("  \"jdkClasses\": ").append(jdkClasses.size()).append(",\n")
            .append("  \"jdkInventory\": {\"classes\": ").append(jdkTotals.classes())
            .append(", \"fields\": ").append(jdkTotals.fields())
            .append(", \"constructors\": ").append(jdkTotals.constructors())
            .append(", \"methods\": ").append(jdkTotals.methods()).append("},\n")
            .append("  \"exactSupportedJdkCallables\": {\"classes\": ").append(callableSupport.classesWithSupportedCallables())
            .append(", \"constructors\": ").append(callableSupport.supportedConstructors())
            .append(", \"methods\": ").append(callableSupport.supportedMethods())
            .append(", \"callables\": ").append(callableSupport.supportedCallables())
            .append(", \"totalCallables\": ").append(callableSupport.totalCallables())
            .append(", \"leftCallables\": ").append(callableSupport.leftCallables())
            .append(", \"coveragePercent\": ").append(Json.string(coveragePercentText(callableSupport.supportedCallables(), callableSupport.totalCallables())))
            .append("},\n")
            .append("  \"exactJdkCallableAccounting\": {\"supportedCallables\": ").append(callableSupport.supportedCallables())
            .append(", \"explicitRejectedCallables\": ").append(callableSupport.explicitRejectedCallables())
            .append(", \"doneCallables\": ").append(callableSupport.doneCallables())
            .append(", \"unknownCallables\": ").append(callableSupport.unknownCallables())
            .append(", \"totalCallables\": ").append(callableSupport.totalCallables())
            .append(", \"donePercent\": ").append(Json.string(coveragePercentText(callableSupport.doneCallables(), callableSupport.totalCallables())))
            .append("},\n")
            .append("  \"jdkCoverageAccounting\": {\"implemented\": true, \"complete\": false, \"scope\": ")
            .append(Json.string("exact-supported-plus-unknown-baseline"))
            .append(", \"note\": ")
            .append(Json.string("inventory is generated; exact supported callable-member accounting is implemented; explicit rejected callable-member accounting is still incomplete; unknown callables currently include every callable that is not yet counted as supported or explicitly rejected")).append("},\n")
            .append("  \"supportRows\": ").append(rows.size()).append(",\n")
            .append("  \"passRows\": ").append(passRows).append(",\n")
            .append("  \"scopedRows\": ").append(scopedRows).append(",\n")
            .append("  \"targetRows\": ").append(targetRows).append(",\n")
            .append("  \"rejectedRows\": ").append(rejectedRows).append(",\n")
            .append("  \"accountedRows\": ").append(accountedRows).append(",\n")
            .append("  \"unaccountedRows\": ").append(unaccountedRows).append(",\n")
            .append("  \"diagnosticErrors\": ").append(errors).append(",\n")
            .append("  \"recognizedRejectedOpcodeUses\": ").append(rejected).append(",\n")
            .append("  \"unknownFatalOpcodeUses\": ").append(unknown).append(",\n")
            .append("  \"classFileMajorVersions\": ").append(Json.intList(majorVersions(projectClasses))).append(",\n")
            .append("  \"constantPoolTags\": ").append(Json.intList(constantPoolTags(projectClasses))).append(",\n")
            .append("  \"attributes\": ").append(Json.stringList(attributes(projectClasses))).append("\n")
            .append("}\n");
        return json.toString();
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
        final JdkCallableSupportTotals callableSupport = jdkCallableSupportTotals(jdkClasses);
        final List<SupportRow> rows = supportRows();
        final int passRows = countSupportRows(rows, "pass");
        final int scopedRows = countSupportRows(rows, "scoped");
        final int targetRows = countSupportRows(rows, "target");
        final int rejectedRows = countSupportRows(rows, "rejected");
        final int accountedRows = passRows + rejectedRows;
        final int unaccountedRows = rows.size() - accountedRows;
        final StringBuilder markdown = new StringBuilder();
        markdown.append("# Compatibility Summary\n\n")
            .append("- status: `").append(status).append("`\n")
            .append("- java: `").append(javaVersion).append("`\n")
            .append("- jdk: `JDK").append(feature).append("`\n")
            .append("- project classes: `").append(projectClasses.size()).append("`\n")
            .append("- JDK inventory classes: `").append(jdkClasses.size()).append("`\n")
            .append("- JDK inventory fields: `").append(jdkTotals.fields()).append("`\n")
            .append("- JDK inventory constructors: `").append(jdkTotals.constructors()).append("`\n")
            .append("- JDK inventory methods: `").append(jdkTotals.methods()).append("`\n")
            .append("- exact supported JDK callable classes: `").append(callableSupport.classesWithSupportedCallables()).append("`\n")
            .append("- exact supported JDK constructors: `").append(callableSupport.supportedConstructors()).append("`\n")
            .append("- exact supported JDK methods: `").append(callableSupport.supportedMethods()).append("`\n")
            .append("- exact supported JDK callables: `").append(callableSupport.supportedCallables())
            .append(" / ").append(callableSupport.totalCallables()).append("` (`")
            .append(coveragePercentDisplay(callableSupport.supportedCallables(), callableSupport.totalCallables())).append("`)\n")
            .append("- exact explicit rejected JDK callables: `").append(callableSupport.explicitRejectedCallables()).append("`\n")
            .append("- exact done JDK callables: `").append(callableSupport.doneCallables())
            .append(" / ").append(callableSupport.totalCallables()).append("` (`")
            .append(coveragePercentDisplay(callableSupport.doneCallables(), callableSupport.totalCallables())).append("`)\n")
            .append("- exact unknown JDK callables: `").append(callableSupport.unknownCallables()).append("`\n")
            .append("- exact supported JDK callables left: `").append(callableSupport.leftCallables()).append("`\n")
            .append("- JDK coverage accounting: `partial (exact supported + unknown baseline)`\n")
            .append("- support rows: `").append(rows.size()).append("`\n")
            .append("- pass rows: `").append(passRows).append("`\n")
            .append("- scoped rows: `").append(scopedRows).append("`\n")
            .append("- target rows: `").append(targetRows).append("`\n")
            .append("- rejected rows: `").append(rejectedRows).append("`\n")
            .append("- accounted rows: `").append(accountedRows).append("`\n")
            .append("- unaccounted rows: `").append(unaccountedRows).append("`\n")
            .append("- diagnostic errors: `").append(errors).append("`\n")
            .append("- recognized rejected opcode uses: `").append(rejected).append("`\n")
            .append("- unknown fatal opcode uses: `").append(unknown).append("`\n\n")
            .append("Inventory counts say what exists in the scanned JDK. They do not claim native support.\n")
            .append("Unknown bytecode is fatal. Recognized rejected bytecode must be added to the native profile or remain explicitly unsupported.\n");
        return markdown.toString();
    }

    private static String bytecodePatternsJson(
        final String javaVersion,
        final int feature,
        final Path root,
        final List<ClassMetadata> classes
    ) {
        final List<InstructionMetadata> instructions = projectInstructions(classes);
        final List<OpcodeCount> opcodeCounts = opcodeCounts(instructions);
        final StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"javaVersion\": ").append(Json.string(javaVersion)).append(",\n")
            .append("  \"javaFeatureVersion\": ").append(feature).append(",\n")
            .append("  \"root\": ").append(Json.string(root.toString())).append(",\n")
            .append("  \"classFileMajorVersions\": ").append(Json.intList(majorVersions(classes))).append(",\n")
            .append("  \"opcodes\": ").append(opcodeCountsJson(opcodeCounts)).append(",\n")
            .append("  \"constantPoolTags\": ").append(Json.intList(constantPoolTags(classes))).append(",\n")
            .append("  \"attributes\": ").append(Json.stringList(attributes(classes))).append(",\n")
            .append("  \"bootstrapMethodPatterns\": ").append(Json.stringList(bootstrapMethodPatterns(classes))).append(",\n")
            .append("  \"syntheticMethods\": ").append(Json.stringList(syntheticMethods(classes))).append(",\n")
            .append("  \"previewClasses\": ").append(Json.stringList(previewClasses(classes))).append("\n")
            .append("}\n");
        return json.toString();
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
        return new StringBuilder()
            .append("        {\"name\": ").append(Json.string(metadata.name()))
            .append(", \"major\": ").append(metadata.majorVersion())
            .append(", \"flags\": ").append(metadata.accessFlags())
            .append(", \"preview\": ").append(metadata.preview())
            .append(", \"deprecated\": ").append(metadata.deprecated())
            .append(", \"fields\": ").append(membersJson(metadata.fields()))
            .append(", \"constructors\": ").append(membersJson(metadata.constructors()))
            .append(", \"methods\": ").append(membersJson(metadata.methods()))
            .append("}")
            .toString();
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
        final StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"generatedForJdk\": ").append(feature).append(",\n")
            .append("  \"statusLegend\": {\n")
            .append("    \"pass\": \"implemented and tested for the named scenario\",\n")
            .append("    \"scoped\": \"supported subset with explicit rejection for unsupported shapes\",\n")
            .append("    \"target\": \"planned and not claimed as supported\",\n")
            .append("    \"rejected\": \"rejected by design for native output\"\n")
            .append("  },\n")
            .append("  \"jdkCoverageAccounting\": {\n")
            .append("    \"implemented\": false,\n")
            .append("    \"rule\": ")
            .append(Json.string("done = supported variants + rejected variants; unknown leftovers must be 0 for a release-gated JDK"))
            .append("\n")
            .append("  },\n")
            .append("  \"counts\": {\n")
            .append("    \"rows\": ").append(rows.size()).append(",\n")
            .append("    \"pass\": ").append(countSupportRows(rows, "pass")).append(",\n")
            .append("    \"scoped\": ").append(countSupportRows(rows, "scoped")).append(",\n")
            .append("    \"target\": ").append(countSupportRows(rows, "target")).append(",\n")
            .append("    \"rejected\": ").append(countSupportRows(rows, "rejected")).append("\n")
            .append("  },\n")
            .append("  \"features\": [\n");
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            final SupportRow row = rows.get(index);
            json.append("    {\"feature\": ").append(Json.string(row.feature()))
                .append(", \"jdk").append(feature).append("\": ").append(Json.string(row.status()))
                .append("}");
        }
        json.append("\n  ]\n}\n");
        return json.toString();
    }

    private static String supportMatrixMarkdown(final int feature) {
        final StringBuilder markdown = new StringBuilder("# javan Support Matrix\n\n");
        markdown.append("This matrix tracks named javan/JDK behavior scenarios. It is not a claim that every class or method in the scanned JDK is native-supported.\n\n");
        final List<SupportRow> rows = supportRows();
        markdown.append("## Current Counts\n\n");
        markdown.append("| Measure | Count |\n");
        markdown.append("| --- | ---: |\n");
        markdown.append("| rows | ").append(rows.size()).append(" |\n");
        markdown.append("| pass | ").append(countSupportRows(rows, "pass")).append(" |\n");
        markdown.append("| scoped | ").append(countSupportRows(rows, "scoped")).append(" |\n");
        markdown.append("| target | ").append(countSupportRows(rows, "target")).append(" |\n");
        markdown.append("| rejected | ").append(countSupportRows(rows, "rejected")).append(" |\n\n");
        markdown.append("Status mapping:\n\n");
        markdown.append("| Matrix status | Roadmap status | Meaning |\n");
        markdown.append("| --- | --- | --- |\n");
        markdown.append("| `pass` | Done | Implemented and tested for the named scenario. |\n");
        markdown.append("| `scoped` | Partial | Supported subset exists; unsupported shapes must reject clearly. |\n");
        markdown.append("| `target` | Planned | Tracked as a goal, not claimed as supported yet. |\n");
        markdown.append("| `rejected` | Dismissed | Rejected by design for native output. |\n\n");
        markdown.append("| feature | JDK").append(feature).append(" |\n");
        markdown.append("| --- | --- |\n");
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
            pass("boxed-primitive-gc"),
            pass("boolean-basic"),
            pass("static-fields"),
            pass("string-concat"),
            pass("exception-panic"),
            pass("try-catch"),
            target("try-finally"),
            pass("enum-basic"),
            pass("enum-ordinal"),
            pass("enum-values"),
            pass("enum-switch"),
            pass("enum-value-of"),
            pass("interface-dispatch"),
            pass("polymorphic-virtual"),
            pass("interface-polymorphic"),
            pass("string-intrinsics"),
            pass("non-ascii-string-semantic-rejection"),
            pass("operand-object-compare-temporary-root"),
            pass("operand-field-load-temporary-root"),
            pass("operand-chained-field-load-temporary-root"),
            pass("operand-chained-call-receiver-temporary-root"),
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
            pass("library-binding-ownership-smoke"),
            pass("library-retained-input-ownership"),
            pass("library-last-error-abi"),
            pass("library-c-result-wrapper-success"),
            pass("library-c-result-wrapper-error"),
            pass("library-c-result-wrapper-null-out"),
            pass("library-c-result-wrapper-free"),
            pass("library-rust-result-wrapper"),
            pass("library-go-result-wrapper"),
            pass("library-python-result-wrapper"),
            pass("library-null-string-input"),
            pass("library-empty-byte-array-input"),
            pass("library-negative-byte-array-rejection"),
            pass("library-structured-last-error-fields"),
            pass("deduplication-plan"),
            pass("hashmap-realloc-gc"),
            pass("list-of-varargs-gc"),
            pass("owned-buffer-realloc-validation"),
            pass("stringbuilder-setlength-overflow-panic"),
            pass("network-address-runtime"),
            pass("network-tcp-client-socket"),
            pass("network-tcp-server-socket"),
            pass("network-tcp-socket-stream-io"),
            pass("network-http-client-get-string"),
            pass("network-http-client-post-string-byte-array"),
            pass("network-http-client-put-byte-array"),
            pass("platform-thread-construction"),
            pass("platform-thread-empty-start-join"),
            pass("platform-thread-runnable-start-join-single-threaded"),
            pass("platform-thread-current-interrupt-state"),
            pass("platform-thread-current-thread-root-gc-pressure"),
            pass("platform-thread-runnable-target-root-gc-pressure"),
            pass("platform-thread-current-thread-inventory"),
            pass("platform-thread-live-root-registry"),
            pass("platform-thread-finished-thread-reclaim"),
            pass("platform-thread-sleep-uninterrupted"),
            pass("platform-thread-sleep-entry-interrupted-same-method-catch"),
            pass("platform-thread-join-entry-interrupted-same-method-catch"),
            pass("platform-thread-current-thread-start-build-reject"),
            pass("platform-thread-current-thread-join-build-reject"),
            pass("platform-thread-duplicate-start-build-reject"),
            pass("network-socket-rejection"),
            pass("network-http-rejection"),
            pass("network-runtime-feature-reporting"),
            pass("typemap-pair"),
            pass("nano-metric"),
            pass("nano-duration")
        );
    }

    private static int countSupportRows(final List<SupportRow> rows, final String status) {
        int count = 0;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).status().equals(status)) {
                count++;
            }
        }
        return count;
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
        final JdkCallableSupportTotals callableSupport = jdkCallableSupportTotals(jdkClasses);
        final List<SupportRow> rows = supportRows();
        final StringBuilder markdown = new StringBuilder();
        markdown.append("# JDK Compatibility\n\n")
            .append("`javan` reads classfile versions directly from `.class` files. Users should not need\n")
            .append("to pass a Java version for supported classfiles; the compiler either understands the\n")
            .append("bytecode pattern or rejects it before native code generation.\n\n")
            .append("| JDK | Class file major | Release-gate status |\n")
            .append("| --- | ---: | --- |\n")
            .append("| 21 | 65 | planned matrix target |\n")
            .append("| 22 | 66 | planned matrix target |\n")
            .append("| 23 | 67 | planned matrix target |\n")
            .append("| 24 | 68 | planned matrix target |\n")
            .append("| 25 | 69 | integrated local gate |\n\n")
            .append("## Active Scan\n\n")
            .append("- scanned java: `").append(javaVersion).append("`\n")
            .append("- scanned JDK: `JDK").append(feature).append("`\n")
            .append("- project classfile majors: `").append(intListText(majorVersions(projectClasses))).append("`\n")
            .append("- JDK classfile majors: `").append(intListText(majorVersions(jdkClasses))).append("`\n")
            .append("- JDK modules: `").append(jdkModuleCount(jdkClasses)).append("`\n\n")
            .append("## Inventory Totals\n\n")
            .append("| item | count |\n")
            .append("| --- | ---: |\n")
            .append("| classes | ").append(totals.classes()).append(" |\n")
            .append("| fields | ").append(totals.fields()).append(" |\n")
            .append("| constructors | ").append(totals.constructors()).append(" |\n")
            .append("| methods | ").append(totals.methods()).append(" |\n\n")
            .append("## Inventory Is Not Support\n\n")
            .append("Inventory means `javan` can see the JDK surface: modules, packages, classes,\n")
            .append("methods, fields, constructors, descriptors, flags, attributes, constant-pool\n")
            .append("tags, bootstrap methods, synthetic members, deprecated markers, and preview\n")
            .append("markers.\n\n")
            .append("Native support means a reachable API or bytecode variant is either implemented\n")
            .append("or deliberately rejected with a clear diagnostic. A release-gated JDK must have\n")
            .append("no unknown leftovers.\n\n")
            .append("## Support Accounting\n\n")
            .append("Inventory is implemented. Exact supported callable-member accounting is implemented as a\n")
            .append("lower-bound progress signal. Exact explicit rejected and unknown callable counts are now\n")
            .append("reported as a baseline, but full member-by-member rejection accounting is still planned.\n\n")
            .append("Current support ledger for the active JDK ").append(feature).append(" evidence set:\n\n")
            .append("| Measure | Count |\n")
            .append("| --- | ---: |\n")
            .append("| support rows | ").append(rows.size()).append(" |\n")
            .append("| pass rows | ").append(countSupportRows(rows, "pass")).append(" |\n")
            .append("| scoped rows | ").append(countSupportRows(rows, "scoped")).append(" |\n")
            .append("| target rows | ").append(countSupportRows(rows, "target")).append(" |\n")
            .append("| rejected rows | ").append(countSupportRows(rows, "rejected")).append(" |\n")
            .append("| accounted rows | ").append(countSupportRows(rows, "pass") + countSupportRows(rows, "rejected")).append(" |\n")
            .append("| unaccounted rows | ").append(rows.size() - countSupportRows(rows, "pass") - countSupportRows(rows, "rejected")).append(" |\n")
            .append("| exact supported JDK callable classes | ").append(callableSupport.classesWithSupportedCallables()).append(" |\n")
            .append("| exact supported JDK constructors | ").append(callableSupport.supportedConstructors()).append(" |\n")
            .append("| exact supported JDK methods | ").append(callableSupport.supportedMethods()).append(" |\n")
            .append("| exact supported JDK callables | ").append(callableSupport.supportedCallables()).append(" / ")
            .append(callableSupport.totalCallables()).append(" (").append(coveragePercentDisplay(callableSupport.supportedCallables(), callableSupport.totalCallables())).append(") |\n")
            .append("| exact explicit rejected JDK callables | ").append(callableSupport.explicitRejectedCallables()).append(" |\n")
            .append("| exact done JDK callables | ").append(callableSupport.doneCallables()).append(" / ")
            .append(callableSupport.totalCallables()).append(" (").append(coveragePercentDisplay(callableSupport.doneCallables(), callableSupport.totalCallables())).append(") |\n")
            .append("| exact unknown JDK callables | ").append(callableSupport.unknownCallables()).append(" |\n")
            .append("| exact supported JDK callables left | ").append(callableSupport.leftCallables()).append(" |\n\n")
            .append("Release-gated JDKs must report:\n\n")
            .append("```text\n")
            .append("done = supported variants + rejected variants\n")
            .append("leftovers = unknown variants\n")
            .append("leftovers must be 0\n")
            .append("```\n\n")
            .append("The exact supported and done JDK callable counts above are lower-bound progress signals.\n")
            .append("Unknown callables still include everything not yet counted as supported or explicitly rejected,\n")
            .append("so this is not a full JDK completion claim.\n\n")
            .append("Compatibility reports are generated under `.javan/reports`, `.javan/jdk-inventory`, and `.javan/bytecode-patterns`.\n")
            .append("New opcodes, constant-pool tags, attributes, and bootstrap patterns must be classified before native code generation accepts them.\n");
        return markdown.toString();
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

    private static JdkCallableSupportTotals jdkCallableSupportTotals(final List<ClassMetadata> classes) {
        long supportedConstructors = 0;
        long supportedMethods = 0;
        long explicitRejectedConstructors = 0;
        long explicitRejectedMethods = 0;
        long totalConstructors = 0;
        long totalMethods = 0;
        long classesWithSupportedCallables = 0;
        final ForbiddenApiRules forbiddenApiRules = new ForbiddenApiRules();
        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            final ClassMetadata metadata = classes.get(classIndex);
            long classSupportedConstructors = countSupportedMembers(metadata.name(), metadata.constructors());
            long classSupportedMethods = countSupportedMembers(metadata.name(), metadata.methods());
            long classExplicitRejectedConstructors = countExplicitRejectedMembers(metadata.name(), metadata.constructors(), forbiddenApiRules);
            long classExplicitRejectedMethods = countExplicitRejectedMembers(metadata.name(), metadata.methods(), forbiddenApiRules);
            supportedConstructors += classSupportedConstructors;
            supportedMethods += classSupportedMethods;
            explicitRejectedConstructors += classExplicitRejectedConstructors;
            explicitRejectedMethods += classExplicitRejectedMethods;
            totalConstructors += metadata.constructors().size();
            totalMethods += metadata.methods().size();
            if ((classSupportedConstructors + classSupportedMethods) > 0) {
                classesWithSupportedCallables++;
            }
        }
        return new JdkCallableSupportTotals(
            classesWithSupportedCallables,
            supportedConstructors,
            supportedMethods,
            explicitRejectedConstructors,
            explicitRejectedMethods,
            totalConstructors + totalMethods
        );
    }

    private static long countSupportedMembers(final String owner, final List<MemberMetadata> members) {
        long supported = 0;
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            final MemberMetadata member = members.get(memberIndex);
            if (JdkCallSupport.isSupported(new javan.classfile.MethodRef(owner, member.name(), member.descriptor()))) {
                supported++;
            }
        }
        return supported;
    }

    private static long countExplicitRejectedMembers(
        final String owner,
        final List<MemberMetadata> members,
        final ForbiddenApiRules forbiddenApiRules
    ) {
        long rejected = 0;
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            final MemberMetadata member = members.get(memberIndex);
            if (forbiddenApiRules.forbiddenReason(new javan.classfile.MethodRef(owner, member.name(), member.descriptor())).isPresent()) {
                rejected++;
            }
        }
        return rejected;
    }

    private record JdkCallableSupportTotals(
        long classesWithSupportedCallables,
        long supportedConstructors,
        long supportedMethods,
        long explicitRejectedConstructors,
        long explicitRejectedMethods,
        long totalCallables
    ) {
        private long supportedCallables() {
            return supportedConstructors + supportedMethods;
        }

        private long explicitRejectedCallables() {
            return explicitRejectedConstructors + explicitRejectedMethods;
        }

        private long doneCallables() {
            return supportedCallables() + explicitRejectedCallables();
        }

        private long unknownCallables() {
            return totalCallables - doneCallables();
        }

        private long leftCallables() {
            return totalCallables - supportedCallables();
        }
    }

    private static String coveragePercentText(final long supported, final long total) {
        if (total == 0) {
            return "0.0";
        }
        return new StringBuilder()
            .append((supported * 1000L) / total / 10L)
            .append('.')
            .append((supported * 1000L) / total % 10L)
            .toString();
    }

    private static String coveragePercentDisplay(final long supported, final long total) {
        return new StringBuilder().append(coveragePercentText(supported, total)).append('%').toString();
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
                addStringSorted(
                    result,
                    new StringBuilder()
                        .append(className)
                        .append('.')
                        .append(member.name())
                        .append(member.descriptor())
                        .toString()
                );
            }
        }
    }

    private static String jdkName(final int feature) {
        return new StringBuilder().append("jdk-").append(feature).toString();
    }

    private static String suffixedJsonName(final String value) {
        return new StringBuilder().append(value).append(".json").toString();
    }

    private static String inventoryReportName(final String jdkName) {
        return new StringBuilder().append(jdkName).append("-inventory.json").toString();
    }

    private static String bytecodePatternReportName(final String jdkName) {
        return new StringBuilder().append("bytecode-patterns-").append(jdkName).append(".json").toString();
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
