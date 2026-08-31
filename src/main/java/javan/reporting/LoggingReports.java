package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.FieldRef;
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
 * Writes deterministic reachable Java logging evidence without inspecting message content.
 */
public final class LoggingReports {
    private static final String LOGGER = "java/util/logging/Logger";
    private static final String LEVEL = "java/util/logging/Level";
    private static final List<String> LEVELS = List.of(
        "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST", "OFF", "ALL"
    );

    /**
     * Analyzes reachable bytecode and writes JSON and Markdown logging reports.
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
        final Path json = outputDirectory.resolve("reports/logging.json");
        final Path markdown = outputDirectory.resolve("reports/logging.md");
        Files2.writeString(json, json(report));
        Files2.writeString(markdown, markdown(report));
        return List.of(json, markdown);
    }

    /**
     * Counts reachable {@code java.util.logging.Logger} call sites without executing logging code.
     *
     * @param classes parsed application and dependency classes
     * @param reachable reachable application methods
     * @return immutable logging evidence
     */
    Report analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<LevelCount> levels = initialLevels();
        final List<UnknownCall> unknownCalls = new ArrayList<>();
        int reachableLoggerCalls = 0;
        int levelCalls = 0;
        int inferredLevelCalls = 0;
        int literalLevelCalls = 0;

        for (final EntryPoint entry : reachable) {
            final Optional<MethodInfo> method = method(classes, entry);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Optional<MethodRef> reference = instructions.get(index).methodRef();
                if (reference.isEmpty() || !LOGGER.equals(reference.orElseThrow().owner())) {
                    continue;
                }
                reachableLoggerCalls++;
                final Optional<String> inferred = inferredLevel(reference.orElseThrow());
                if (inferred.isPresent()) {
                    increment(levels, inferred.orElseThrow(), false);
                    levelCalls++;
                    inferredLevelCalls++;
                    continue;
                }
                if (!levelArgument(reference.orElseThrow())) {
                    continue;
                }
                levelCalls++;
                final Optional<String> literal = literalLevel(instructions, index);
                if (literal.isPresent()) {
                    increment(levels, literal.orElseThrow(), true);
                    literalLevelCalls++;
                } else {
                    incrementUnknown(unknownCalls, reference.orElseThrow().display());
                }
            }
        }

        final int unknownLevelCalls = levelCalls - literalLevelCalls - inferredLevelCalls;
        return new Report(
            List.copyOf(levels),
            reachableLoggerCalls,
            levelCalls,
            literalLevelCalls,
            inferredLevelCalls,
            unknownLevelCalls,
            reachableLoggerCalls - levelCalls,
            List.copyOf(unknownCalls)
        );
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile classFile = classes.get(entry.className());
        return classFile == null ? Optional.empty() : classFile.method(entry.methodName(), entry.descriptor());
    }

    private static Optional<String> inferredLevel(final MethodRef reference) {
        return switch (reference.name()) {
            case "severe" -> Optional.of("SEVERE");
            case "warning" -> Optional.of("WARNING");
            case "info" -> Optional.of("INFO");
            case "config" -> Optional.of("CONFIG");
            case "fine" -> Optional.of("FINE");
            case "finer", "entering", "exiting", "throwing" -> Optional.of("FINER");
            case "finest" -> Optional.of("FINEST");
            default -> Optional.empty();
        };
    }

    private static boolean levelArgument(final MethodRef reference) {
        return switch (reference.name()) {
            case "log", "logp", "logrb" -> reference.descriptor().startsWith("(Ljava/util/logging/Level;");
            default -> false;
        };
    }

    private static Optional<String> literalLevel(final List<Instruction> instructions, final int invocationIndex) {
        if (invocationIndex < 2) {
            return Optional.empty();
        }
        final Instruction instruction = instructions.get(invocationIndex - 2);
        if (instruction.opcode() != 178) {
            return Optional.empty();
        }
        final Optional<FieldRef> reference = instruction.fieldRef();
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        final FieldRef field = reference.orElseThrow();
        if (!LEVEL.equals(field.owner()) || !"Ljava/util/logging/Level;".equals(field.descriptor())) {
            return Optional.empty();
        }
        return LEVELS.contains(field.name()) ? Optional.of(field.name()) : Optional.empty();
    }

    private static List<LevelCount> initialLevels() {
        final List<LevelCount> levels = new ArrayList<>();
        for (final String level : LEVELS) {
            levels.add(new LevelCount(level, 0, 0));
        }
        return levels;
    }

    private static void increment(final List<LevelCount> counts, final String level, final boolean literal) {
        for (int index = 0; index < counts.size(); index++) {
            final LevelCount existing = counts.get(index);
            if (existing.level().equals(level)) {
                counts.set(index, literal
                    ? new LevelCount(level, existing.literal() + 1, existing.inferred())
                    : new LevelCount(level, existing.literal(), existing.inferred() + 1));
                return;
            }
        }
    }

    private static void incrementUnknown(final List<UnknownCall> calls, final String target) {
        for (int index = 0; index < calls.size(); index++) {
            final UnknownCall existing = calls.get(index);
            final int comparison = Strings2.compareAscii(target, existing.target());
            if (comparison == 0) {
                calls.set(index, new UnknownCall(target, existing.count() + 1));
                return;
            }
            if (comparison < 0) {
                calls.add(index, new UnknownCall(target, 1));
                return;
            }
        }
        calls.add(new UnknownCall(target, 1));
    }

    private static String json(final Report report) {
        return new StringBuilder()
            .append("{\n")
            .append("  \"schemaVersion\": \"1\",\n")
            .append("  \"apiFamily\": \"java.util.logging.Logger\",\n")
            .append("  \"reachableLoggerCallSiteCount\": ").append(report.reachableLoggerCallSiteCount()).append(",\n")
            .append("  \"levelCallSiteCount\": ").append(report.levelCallSiteCount()).append(",\n")
            .append("  \"literalLevelCallSiteCount\": ").append(report.literalLevelCallSiteCount()).append(",\n")
            .append("  \"inferredLevelCallSiteCount\": ").append(report.inferredLevelCallSiteCount()).append(",\n")
            .append("  \"unknownLevelCallSiteCount\": ").append(report.unknownLevelCallSiteCount()).append(",\n")
            .append("  \"nonEmittingCallSiteCount\": ").append(report.nonEmittingCallSiteCount()).append(",\n")
            .append("  \"levels\": [\n")
            .append(levelsJson(report.levels()))
            .append("  ],\n")
            .append("  \"unknownLevelCalls\": [\n")
            .append(unknownCallsJson(report.unknownCalls()))
            .append("  ]\n")
            .append("}\n")
            .toString();
    }

    private static String levelsJson(final List<LevelCount> levels) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < levels.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final LevelCount level = levels.get(index);
            result.append("    {\"level\": ").append(Json.string(level.level()))
                .append(", \"literal\": ").append(level.literal())
                .append(", \"inferred\": ").append(level.inferred())
                .append("}");
        }
        return result.append("\n").toString();
    }

    private static String unknownCallsJson(final List<UnknownCall> calls) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < calls.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final UnknownCall call = calls.get(index);
            result.append("    {\"target\": ").append(Json.string(call.target()))
                .append(", \"count\": ").append(call.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String markdown(final Report report) {
        final StringBuilder result = new StringBuilder();
        result.append("# Reachable Logging\n\n");
        result.append("The compiler scans reachable `java.util.logging.Logger` calls without executing logging code or recording message content. ")
            .append("Logger runtime support remains outside the native profile.\n\n");
        result.append("- reachable Logger call sites: `").append(report.reachableLoggerCallSiteCount()).append("`\n");
        result.append("- level call sites: `").append(report.levelCallSiteCount()).append("`\n");
        result.append("- literal levels: `").append(report.literalLevelCallSiteCount()).append("`\n");
        result.append("- inferred levels: `").append(report.inferredLevelCallSiteCount()).append("`\n");
        result.append("- unknown levels: `").append(report.unknownLevelCallSiteCount()).append("`\n");
        result.append("- non-emitting Logger calls: `").append(report.nonEmittingCallSiteCount()).append("`\n\n");
        result.append("| Level | Literal call sites | Inferred call sites |\n");
        result.append("| --- | ---: | ---: |\n");
        for (final LevelCount level : report.levels()) {
            result.append("| `").append(level.level()).append("` | ").append(level.literal()).append(" | ")
                .append(level.inferred()).append(" |\n");
        }
        result.append("\n## Unknown levels\n\n");
        result.append("| Logger target | Reachable call sites |\n");
        result.append("| --- | ---: |\n");
        if (report.unknownCalls().isEmpty()) {
            result.append("| none | 0 |\n");
        } else {
            for (final UnknownCall call : report.unknownCalls()) {
                result.append("| `").append(call.target()).append("` | ").append(call.count()).append(" |\n");
            }
        }
        return result.toString();
    }

    record Report(
        List<LevelCount> levels,
        int reachableLoggerCallSiteCount,
        int levelCallSiteCount,
        int literalLevelCallSiteCount,
        int inferredLevelCallSiteCount,
        int unknownLevelCallSiteCount,
        int nonEmittingCallSiteCount,
        List<UnknownCall> unknownCalls
    ) {
    }

    record LevelCount(String level, int literal, int inferred) {
    }

    record UnknownCall(String target, int count) {
    }
}
