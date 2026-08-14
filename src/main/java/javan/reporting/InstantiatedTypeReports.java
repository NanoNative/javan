package javan.reporting;

import javan.analysis.FunctionValueFlow;
import javan.analysis.InstantiatedTypeAnalysis;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Writes the concrete receiver evidence used to bound closed-world dispatch. */
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

    /**
     * Writes deterministic JSON and Markdown callback receiver evidence.
     *
     * @param outputDirectory Javan output directory
     * @param flow completed callback value flow
     * @return the unchanged flow for pipeline composition
     * @throws IOException when report output cannot be written
     */
    public FunctionValueFlow.Result writeProvenance(
        final Path outputDirectory,
        final FunctionValueFlow.Result flow
    ) throws IOException {
        final List<ProvenanceItem> items = provenanceItems(flow);
        final Path reports = outputDirectory.resolve("reports");
        Files2.writeString(reports.resolve("receiver-provenance.json"), provenanceJson(flow, items));
        Files2.writeString(reports.resolve("receiver-provenance.md"), provenanceMarkdown(flow, items));
        return flow;
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

    private static List<ProvenanceItem> provenanceItems(final FunctionValueFlow.Result flow) {
        final List<ProvenanceItem> result = new ArrayList<>();
        addProvenance(result, "function", flow.functionKinds(), flow.functionProvenance());
        addProvenance(result, "supplier", flow.supplierKinds(), flow.supplierProvenance());
        return List.copyOf(result);
    }

    private static void addProvenance(
        final List<ProvenanceItem> items,
        final String callable,
        final Map<FunctionValueFlow.Site, FunctionValueFlow.ValueKind> kinds,
        final Map<FunctionValueFlow.Site, FunctionValueFlow.Provenance> provenance
    ) {
        for (final Map.Entry<FunctionValueFlow.Site, FunctionValueFlow.ValueKind> entry : kinds.entrySet()) {
            final ProvenanceItem item = new ProvenanceItem(
                callable,
                entry.getKey(),
                entry.getValue(),
                provenance.getOrDefault(entry.getKey(), FunctionValueFlow.Provenance.unavailable())
            );
            int index = 0;
            while (index < items.size() && compare(items.get(index), item) <= 0) {
                index++;
            }
            items.add(index, item);
        }
    }

    private static int compare(final ProvenanceItem left, final ProvenanceItem right) {
        int result = Strings2.compareAscii(left.site().className(), right.site().className());
        if (result == 0) result = Strings2.compareAscii(left.site().methodName(), right.site().methodName());
        if (result == 0) result = Strings2.compareAscii(left.site().descriptor(), right.site().descriptor());
        if (result == 0) {
            result = left.site().offset() < right.site().offset()
                ? -1
                : left.site().offset() == right.site().offset() ? 0 : 1;
        }
        return result == 0 ? Strings2.compareAscii(left.callable(), right.callable()) : result;
    }

    private static String provenanceJson(
        final FunctionValueFlow.Result flow,
        final List<ProvenanceItem> items
    ) {
        final StringBuilder result = new StringBuilder()
            .append("{\n")
            .append("  \"schemaVersion\": 1,\n")
            .append("  \"strategy\": \"bounded-callback-value-flow\",\n")
            .append("  \"complete\": ").append(flow.complete()).append(",\n")
            .append("  \"maxExactTypes\": ").append(FunctionValueFlow.maxExactTypes()).append(",\n")
            .append("  \"sites\": ").append(items.size()).append(",\n")
            .append("  \"facts\": [\n");
        for (int index = 0; index < items.size(); index++) {
            final ProvenanceItem item = items.get(index);
            result.append("    {\"callable\": ").append(Json.string(item.callable()))
                .append(", \"class\": ").append(Json.string(item.site().className()))
                .append(", \"method\": ").append(Json.string(item.site().methodName()))
                .append(", \"descriptor\": ").append(Json.string(item.site().descriptor()))
                .append(", \"offset\": ").append(item.site().offset())
                .append(", \"kind\": ").append(Json.string(origin(item.kind())))
                .append(", \"unknown\": ").append(item.provenance().unknown())
                .append(", \"types\": [");
            appendTypes(result, item.provenance().types());
            result.append("]}");
            if (index + 1 < items.size()) result.append(',');
            result.append('\n');
        }
        return result.append("  ]\n}\n").toString();
    }

    private static String provenanceMarkdown(
        final FunctionValueFlow.Result flow,
        final List<ProvenanceItem> items
    ) {
        final StringBuilder result = new StringBuilder()
            .append("# Receiver Provenance\n\n")
            .append("- strategy: `bounded-callback-value-flow`\n")
            .append("- complete: `").append(flow.complete()).append("`\n")
            .append("- maximum exact types: `").append(FunctionValueFlow.maxExactTypes()).append("`\n")
            .append("- callback sites: `").append(items.size()).append("`\n\n");
        if (items.isEmpty()) {
            return result.append("No tracked callback receiver use was reachable.\n").toString();
        }
        for (final ProvenanceItem item : items) {
            result.append("- `").append(item.site().className()).append('.')
                .append(item.site().methodName()).append(item.site().descriptor()).append('@')
                .append(item.site().offset()).append("` ")
                .append(item.callable()).append(": `").append(origin(item.kind())).append("`, ");
            if (item.provenance().unknown()) {
                result.append("unknown");
            } else if (item.provenance().types().isEmpty()) {
                result.append("materialized callable");
            } else {
                appendMarkdownTypes(result, item.provenance().types());
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static void appendTypes(final StringBuilder result, final List<String> types) {
        for (int index = 0; index < types.size(); index++) {
            if (index > 0) result.append(", ");
            result.append(Json.string(types.get(index)));
        }
    }

    private static void appendMarkdownTypes(final StringBuilder result, final List<String> types) {
        for (int index = 0; index < types.size(); index++) {
            if (index > 0) result.append(", ");
            result.append('`').append(types.get(index)).append('`');
        }
    }

    private static String origin(final FunctionValueFlow.ValueKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    private record ProvenanceItem(
        String callable,
        FunctionValueFlow.Site site,
        FunctionValueFlow.ValueKind kind,
        FunctionValueFlow.Provenance provenance
    ) {
    }
}
