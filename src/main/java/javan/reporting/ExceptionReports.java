package javan.reporting;

import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.ir.IrSourceLocation;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes source-focused runtime failure reports for generated panic sites.
 */
public final class ExceptionReports {
    /**
     * Writes exception diagnostics and debug map reports.
     *
     * @param outputDirectory javan output directory
     * @param program lowered IR program
     * @return written report paths
     * @throws IOException when writing fails
     */
    public List<Path> write(final Path outputDirectory, final IrProgram program) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        final List<PanicSite> sites = panicSites(program);
        final Path exceptionsJson = reports.resolve("exceptions.json");
        final Path exceptionsMarkdown = reports.resolve("exceptions.md");
        final Path debugMap = reports.resolve("debug-map.json");
        Files2.writeString(exceptionsJson, exceptionsJson(sites));
        Files2.writeString(exceptionsMarkdown, exceptionsMarkdown(sites));
        Files2.writeString(debugMap, debugMapJson(sites));
        return List.of(exceptionsJson, exceptionsMarkdown, debugMap);
    }

    private static List<PanicSite> panicSites(final IrProgram program) {
        final List<PanicSite> result = new ArrayList<>();
        int id = 1;
        for (final IrFunction function : program.functions()) {
            for (int index = 0; index < function.instructions().size(); index++) {
                final IrInstruction instruction = function.instructions().get(index);
                if (instruction.op() != IrInstruction.Op.PANIC) {
                    continue;
                }
                result.add(new PanicSite("panic-" + id, function.symbol(), index, source(function, instruction)));
                id++;
            }
        }
        return List.copyOf(result);
    }

    private static IrSourceLocation source(final IrFunction function, final IrInstruction instruction) {
        if (instruction.sourceLocation().isPresent()) {
            return instruction.sourceLocation().orElseThrow();
        }
        return new IrSourceLocation(
            function.owner(),
            function.name(),
            function.descriptor(),
            -1,
            Optional.empty(),
            Optional.empty()
        );
    }

    private static String exceptionsJson(final List<PanicSite> sites) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"panicSites\": ").append(sites.size()).append(",\n");
        result.append("  \"sites\": [\n");
        for (int index = 0; index < sites.size(); index++) {
            final PanicSite site = sites.get(index);
            result.append("    {\n");
            appendField(result, "id", Json.string(site.id()), true, 6);
            appendField(result, "code", Json.string("JAVAN-RUNTIME-PANIC"), true, 6);
            appendField(result, "summary", Json.string("uncaught Java exception"), true, 6);
            appendField(result, "class", Json.string(displayClass(site.location().className())), true, 6);
            appendField(result, "method", Json.string(site.location().methodName() + site.location().descriptor()), true, 6);
            appendField(result, "sourceFile", optionalString(site.location().sourceFile()), true, 6);
            appendField(result, "line", optionalInt(site.location().lineNumber()), true, 6);
            appendField(result, "sourceLine", optionalString(site.location().sourceLine()), true, 6);
            appendField(result, "bytecodeOffset", Integer.toString(site.location().bytecodeOffset()), false, 6);
            result.append("    }");
            if (index + 1 < sites.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static String exceptionsMarkdown(final List<PanicSite> sites) {
        final StringBuilder result = new StringBuilder();
        result.append("# Runtime Exceptions\n\n");
        result.append("- panic sites: `").append(sites.size()).append("`\n\n");
        result.append("| id | code | where | bytecode offset |\n");
        result.append("| --- | --- | --- | --- |\n");
        if (sites.isEmpty()) {
            result.append("| none | - | - | - |\n");
            return result.toString();
        }
        for (final PanicSite site : sites) {
            result.append("| `").append(site.id()).append("` | `JAVAN-RUNTIME-PANIC` | `")
                .append(displayClass(site.location().className()))
                .append('.')
                .append(site.location().methodName())
                .append(site.location().descriptor())
                .append(sourceSuffix(site.location()))
                .append("` | `")
                .append(site.location().bytecodeOffset())
                .append("` |\n");
        }
        appendSourceLines(result, sites);
        return result.toString();
    }

    private static void appendSourceLines(final StringBuilder result, final List<PanicSite> sites) {
        boolean hasSourceLine = false;
        for (final PanicSite site : sites) {
            if (site.location().sourceLine().isPresent()) {
                hasSourceLine = true;
                break;
            }
        }
        if (!hasSourceLine) {
            return;
        }
        result.append("\n## Source Lines\n\n");
        for (final PanicSite site : sites) {
            if (site.location().sourceLine().isEmpty()) {
                continue;
            }
            result.append("### `").append(site.id()).append("`\n\n");
            result.append("```java\n");
            result.append(site.location().sourceLine().orElseThrow()).append('\n');
            result.append("```\n\n");
        }
    }

    private static String debugMapJson(final List<PanicSite> sites) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"debugEntries\": ").append(sites.size()).append(",\n");
        result.append("  \"entries\": [\n");
        for (int index = 0; index < sites.size(); index++) {
            final PanicSite site = sites.get(index);
            result.append("    {\n");
            appendField(result, "id", Json.string(site.id()), true, 6);
            appendField(result, "generatedFunction", Json.string(site.generatedFunction()), true, 6);
            appendField(result, "instructionIndex", Integer.toString(site.instructionIndex()), true, 6);
            appendField(result, "class", Json.string(displayClass(site.location().className())), true, 6);
            appendField(result, "method", Json.string(site.location().methodName() + site.location().descriptor()), true, 6);
            appendField(result, "sourceFile", optionalString(site.location().sourceFile()), true, 6);
            appendField(result, "line", optionalInt(site.location().lineNumber()), true, 6);
            appendField(result, "sourceLine", optionalString(site.location().sourceLine()), true, 6);
            appendField(result, "bytecodeOffset", Integer.toString(site.location().bytecodeOffset()), false, 6);
            result.append("    }");
            if (index + 1 < sites.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static String sourceSuffix(final IrSourceLocation location) {
        if (location.sourceFile().isEmpty()) {
            return "";
        }
        if (location.lineNumber().isPresent()) {
            return "(" + location.sourceFile().orElseThrow() + ":" + location.lineNumber().orElseThrow().intValue() + ")";
        }
        return "(" + location.sourceFile().orElseThrow() + ")";
    }

    private static String displayClass(final String value) {
        return Strings2.replaceChar(value, '/', '.');
    }

    private static String optionalString(final Optional<String> value) {
        if (value.isEmpty()) {
            return "null";
        }
        return Json.string(value.orElseThrow());
    }

    private static String optionalInt(final Optional<Integer> value) {
        if (value.isEmpty()) {
            return "null";
        }
        return Integer.toString(value.orElseThrow());
    }

    private static void appendField(
        final StringBuilder result,
        final String name,
        final String value,
        final boolean comma,
        final int spaces
    ) {
        appendSpaces(result, spaces);
        result.append(Json.string(name))
            .append(": ")
            .append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static void appendSpaces(final StringBuilder result, final int spaces) {
        for (int index = 0; index < spaces; index++) {
            result.append(' ');
        }
    }

    private record PanicSite(String id, String generatedFunction, int instructionIndex, IrSourceLocation location) {
    }
}
