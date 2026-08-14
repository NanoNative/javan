package javan.reporting;

import javan.analysis.ClassInitializationGraph;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Writes the deterministic runtime class-initialization trigger graph. */
public final class ClassInitializationReports {
    /**
     * Writes {@code class-initialization.json} and {@code class-initialization.md}.
     *
     * @param outputDirectory Javan output directory
     * @param graph analyzed class-initialization graph
     * @return the unchanged graph for direct pipeline composition
     * @throws IOException when report output cannot be written
     */
    public ClassInitializationGraph.Result write(
        final Path outputDirectory,
        final ClassInitializationGraph.Result graph
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        Files2.writeString(reports.resolve("class-initialization.json"), json(graph));
        Files2.writeString(reports.resolve("class-initialization.md"), markdown(graph));
        return graph;
    }

    private static String json(final ClassInitializationGraph.Result graph) {
        int dependencyCount = 0;
        for (final List<String> dependencies : graph.dependencies().values()) {
            dependencyCount += dependencies.size();
        }
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"schemaVersion\": 1,\n");
        result.append("  \"strategy\": \"lazy-runtime-once\",\n");
        result.append("  \"owners\": ").append(graph.dependencies().size()).append(",\n");
        result.append("  \"dependencies\": ").append(dependencyCount).append(",\n");
        result.append("  \"triggers\": ").append(graph.triggers().size()).append(",\n");
        result.append("  \"initializers\": [\n");
        int ownerIndex = 0;
        for (final Map.Entry<String, List<String>> entry : graph.dependencies().entrySet()) {
            result.append("    {\"owner\": ").append(Json.string(entry.getKey())).append(", \"dependencies\": [");
            for (int index = 0; index < entry.getValue().size(); index++) {
                if (index > 0) result.append(", ");
                result.append(Json.string(entry.getValue().get(index)));
            }
            result.append("]}");
            if (++ownerIndex < graph.dependencies().size()) result.append(',');
            result.append('\n');
        }
        result.append("  ],\n");
        result.append("  \"activeUses\": [\n");
        for (int index = 0; index < graph.triggers().size(); index++) {
            final ClassInitializationGraph.Trigger trigger = graph.triggers().get(index);
            result.append("    {\"method\": ").append(Json.string(trigger.method().display()))
                .append(", \"offset\": ").append(trigger.offset())
                .append(", \"kind\": ").append(Json.string(kind(trigger.kind())))
                .append(", \"target\": ").append(Json.string(trigger.target())).append('}');
            if (index + 1 < graph.triggers().size()) result.append(',');
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(final ClassInitializationGraph.Result graph) {
        int dependencyCount = 0;
        for (final List<String> dependencies : graph.dependencies().values()) {
            dependencyCount += dependencies.size();
        }
        return new StringBuilder()
            .append("# Class Initialization\n\n")
            .append("- strategy: `lazy-runtime-once`\n")
            .append("- owners: `").append(graph.dependencies().size()).append("`\n")
            .append("- dependencies: `").append(dependencyCount).append("`\n")
            .append("- active-use triggers: `").append(graph.triggers().size()).append("`\n\n")
            .append("Exact owners, ordered dependencies, and bytecode trigger sites are in `class-initialization.json`.\n")
            .toString();
    }

    private static String kind(final ClassInitializationGraph.TriggerKind kind) {
        if (kind == ClassInitializationGraph.TriggerKind.GET_STATIC) return "getstatic";
        if (kind == ClassInitializationGraph.TriggerKind.PUT_STATIC) return "putstatic";
        if (kind == ClassInitializationGraph.TriggerKind.INVOKE_STATIC) return "invokestatic";
        return "new";
    }
}
