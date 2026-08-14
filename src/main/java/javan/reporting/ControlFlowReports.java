package javan.reporting;

import javan.analysis.BytecodeControlFlow;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.util.Files2;
import javan.util.Json;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validates reachable bytecode CFGs and writes their stable block/edge report. */
public final class ControlFlowReports {
    private record MethodGraph(EntryPoint method, BytecodeControlFlow.Graph graph) {
    }

    private record Analysis(List<MethodGraph> methods, List<Diagnostic> diagnostics) {
    }

    /** Validates reachable methods and writes {@code control-flow.json} and {@code control-flow.md}. */
    public List<Diagnostic> write(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) throws IOException {
        final List<MethodGraph> methods = new ArrayList<>();
        final List<Diagnostic> diagnostics = new ArrayList<>();
        for (final EntryPoint entry : reachableMethods) {
            final ClassFile classFile = classes.get(entry.className());
            if (classFile == null) {
                continue;
            }
            final Optional<MethodInfo> resolved = classFile.method(entry.methodName(), entry.descriptor());
            if (resolved.isEmpty()) {
                continue;
            }
            final MethodInfo method = resolved.orElseThrow();
            if (method.code().isEmpty()) {
                continue;
            }
            final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method);
            methods.add(new MethodGraph(entry, result.graph()));
            for (final String issue : result.issues()) {
                diagnostics.add(Diagnostic.error(
                    "JAVAN032",
                    "invalid reachable control flow",
                    entry.className(),
                    entry.methodName() + entry.descriptor(),
                    issue,
                    "The bytecode control-flow graph is malformed or has an incompatible operand-stack merge.",
                    "Recompile the class with a conforming JVM compiler or fix the bytecode producer."
                ));
            }
        }
        final Analysis analysis = new Analysis(methods, diagnostics);
        final Path reports = outputDirectory.resolve("reports");
        Files2.writeString(reports.resolve("control-flow.json"), json(analysis));
        Files2.writeString(reports.resolve("control-flow.md"), markdown(analysis));
        return List.copyOf(diagnostics);
    }

    private static String json(final Analysis analysis) {
        int blocks = 0;
        int edges = 0;
        for (final MethodGraph method : analysis.methods()) {
            blocks += method.graph().blocks().size();
            edges += method.graph().edges().size();
        }
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"schemaVersion\": 1,\n");
        result.append("  \"status\": ").append(Json.string(analysis.diagnostics().isEmpty() ? "pass" : "fail")).append(",\n");
        result.append("  \"methods\": ").append(analysis.methods().size()).append(",\n");
        result.append("  \"blocks\": ").append(blocks).append(",\n");
        result.append("  \"edges\": ").append(edges).append(",\n");
        result.append("  \"issues\": ").append(analysis.diagnostics().size()).append(",\n");
        result.append("  \"graphs\": [\n");
        for (int index = 0; index < analysis.methods().size(); index++) {
            appendMethod(result, analysis.methods().get(index));
            if (index + 1 < analysis.methods().size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static void appendMethod(final StringBuilder result, final MethodGraph method) {
        result.append("    {\n");
        result.append("      \"class\": ").append(Json.string(method.method().className())).append(",\n");
        result.append("      \"method\": ").append(Json.string(method.method().methodName())).append(",\n");
        result.append("      \"descriptor\": ").append(Json.string(method.method().descriptor())).append(",\n");
        result.append("      \"blocks\": [");
        for (int index = 0; index < method.graph().blocks().size(); index++) {
            final BytecodeControlFlow.Block block = method.graph().blocks().get(index);
            if (index > 0) result.append(',');
            result.append("{\"id\":").append(block.id())
                .append(",\"start\":").append(block.startOffset())
                .append(",\"end\":").append(block.endOffset()).append('}');
        }
        result.append("],\n");
        result.append("      \"edges\": [");
        for (int index = 0; index < method.graph().edges().size(); index++) {
            final BytecodeControlFlow.Edge edge = method.graph().edges().get(index);
            if (index > 0) result.append(',');
            result.append("{\"from\":").append(edge.fromBlock())
                .append(",\"to\":").append(edge.toBlock())
                .append(",\"kind\":").append(Json.string(edgeKindName(edge.kind())))
                .append('}');
        }
        result.append("]\n");
        result.append("    }");
    }

    private static String markdown(final Analysis analysis) {
        int blocks = 0;
        int edges = 0;
        for (final MethodGraph method : analysis.methods()) {
            blocks += method.graph().blocks().size();
            edges += method.graph().edges().size();
        }
        final StringBuilder result = new StringBuilder();
        result.append("# Bytecode Control Flow\n\n");
        result.append("- status: `").append(analysis.diagnostics().isEmpty() ? "pass" : "fail").append("`\n");
        result.append("- methods: `").append(analysis.methods().size()).append("`\n");
        result.append("- blocks: `").append(blocks).append("`\n");
        result.append("- edges: `").append(edges).append("`\n");
        result.append("- issues: `").append(analysis.diagnostics().size()).append("`\n\n");
        result.append("Exact method graphs, block offsets, and typed edges are in `control-flow.json`.\n");
        return result.toString();
    }

    private static String edgeKindName(final BytecodeControlFlow.EdgeKind kind) {
        if (kind == BytecodeControlFlow.EdgeKind.FALLTHROUGH) return "fallthrough";
        if (kind == BytecodeControlFlow.EdgeKind.BRANCH) return "branch";
        if (kind == BytecodeControlFlow.EdgeKind.SWITCH) return "switch";
        return "exception";
    }
}
