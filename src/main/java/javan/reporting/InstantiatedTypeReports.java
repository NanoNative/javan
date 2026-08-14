package javan.reporting;

import javan.analysis.InstantiatedTypeAnalysis;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Path;

/** Writes the concrete receiver types used to bound closed-world dispatch. */
public final class InstantiatedTypeReports {
    /**
     * Writes deterministic JSON and Markdown construction evidence.
     *
     * @param outputDirectory Javan output directory
     * @param analysis completed instantiated-type analysis
     * @return the unchanged analysis for pipeline composition
     * @throws IOException when report output cannot be written
     */
    public InstantiatedTypeAnalysis.Result write(
        final Path outputDirectory,
        final InstantiatedTypeAnalysis.Result analysis
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        Files2.writeString(reports.resolve("instantiated-types.json"), json(analysis));
        Files2.writeString(reports.resolve("instantiated-types.md"), markdown(analysis));
        return analysis;
    }

    private static String json(final InstantiatedTypeAnalysis.Result analysis) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n")
            .append("  \"schemaVersion\": 1,\n")
            .append("  \"strategy\": \"reachable-construction-fixpoint\",\n")
            .append("  \"complete\": ").append(analysis.complete()).append(",\n")
            .append("  \"types\": ").append(analysis.facts().size()).append(",\n")
            .append("  \"facts\": [\n");
        for (int factIndex = 0; factIndex < analysis.facts().size(); factIndex++) {
            final InstantiatedTypeAnalysis.Fact fact = analysis.facts().get(factIndex);
            result.append("    {\"type\": ").append(Json.string(fact.type())).append(", \"origins\": [");
            for (int originIndex = 0; originIndex < fact.origins().size(); originIndex++) {
                if (originIndex > 0) result.append(", ");
                result.append(Json.string(origin(fact.origins().get(originIndex))));
            }
            result.append("]}");
            if (factIndex + 1 < analysis.facts().size()) result.append(',');
            result.append('\n');
        }
        return result.append("  ]\n}\n").toString();
    }

    private static String markdown(final InstantiatedTypeAnalysis.Result analysis) {
        final StringBuilder result = new StringBuilder()
            .append("# Instantiated Types\n\n")
            .append("- strategy: `reachable-construction-fixpoint`\n")
            .append("- complete: `").append(analysis.complete()).append("`\n")
            .append("- concrete receiver types: `").append(analysis.facts().size()).append("`\n\n");
        if (analysis.facts().isEmpty()) {
            return result.append("No concrete application receiver type was proven constructible.\n").toString();
        }
        for (final InstantiatedTypeAnalysis.Fact fact : analysis.facts()) {
            result.append("- `").append(fact.type()).append("`: ");
            for (int index = 0; index < fact.origins().size(); index++) {
                if (index > 0) result.append(", ");
                result.append('`').append(origin(fact.origins().get(index))).append('`');
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static String origin(final InstantiatedTypeAnalysis.Origin origin) {
        return origin.name().toLowerCase(java.util.Locale.ROOT);
    }
}
