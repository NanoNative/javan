package javan;

import javan.analysis.CallGraph;
import javan.analysis.CallEdge;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import javan.reporting.ThreadReports;
import javan.reporting.VirtualThreadReports;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes machine-readable and text reports under .javan/reports.
 */
public final class ProjectReports {
    private final ThreadReports threadReports = new ThreadReports();
    private final VirtualThreadReports virtualThreadReports = new VirtualThreadReports();

    /**
     * Writes the detected project layout as JSON.
     *
     * @param layout detected project layout
     * @throws IOException when writing fails
     */
    public void writeProject(final ProjectLayout layout) throws IOException {
        writeProject(layout, Profile.CORE);
    }

    /**
     * Writes the detected project layout and selected profile as JSON.
     *
     * @param layout detected project layout
     * @param profile selected profile
     * @throws IOException when writing fails
     */
    public void writeProject(final ProjectLayout layout, final Profile profile) throws IOException {
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendJsonField(json, "root", json(layout.root().toString()), true);
        appendJsonField(json, "input", json(layout.input().toString()), true);
        appendJsonField(json, "inputKind", json(layout.inputKind().name()), true);
        appendJsonField(json, "buildTool", json(layout.buildTool().name()), true);
        appendJsonField(json, "profile", json(profile.cliName()), true);
        appendJsonField(json, "sourceFolders", pathJsonList(layout.sourceFolders()), true);
        appendJsonField(json, "resourceFolders", pathJsonList(layout.resourceFolders()), true);
        appendJsonField(json, "classFolders", pathJsonList(layout.classFolders()), true);
        appendJsonField(json, "classpathEntries", pathJsonList(layout.classpathEntries()), true);
        appendJsonField(json, "outputDirectory", json(layout.outputDirectory().toString()), true);
        appendJsonField(json, "outputName", json(layout.outputName()), true);
        appendJsonField(json, "warnings", jsonList(layout.warnings()), false);
        json.append("}\n");
        Files2.writeString(layout.outputDirectory().resolve("reports/project.json"), json.toString());
    }

    /**
     * Writes reachability information and a visualizable call graph.
     *
     * @param layout detected project layout
     * @param callGraph call graph
     * @throws IOException when writing fails
     */
    public void writeReachability(final ProjectLayout layout, final CallGraph callGraph) throws IOException {
        writeReachabilityReport(layout, callGraph);
        writeCallGraph(layout.outputDirectory().resolve("reports"), callGraph);
    }

    private static void writeReachabilityReport(final ProjectLayout layout, final CallGraph callGraph) throws IOException {
        final StringBuilder report = new StringBuilder();
        report.append("entry: ").append(callGraph.entryPoint().display()).append(System.lineSeparator());
        report.append("reachable:").append(System.lineSeparator());
        for (final String line : sortedEntries(callGraph.reachableMethods())) {
            report.append("  ").append(line).append(System.lineSeparator());
        }
        final Path reports = layout.outputDirectory().resolve("reports");
        Files2.writeString(reports.resolve("reachability.txt"), report.toString());
    }

    /**
     * Writes diagnostics.
     *
     * @param layout detected project layout
     * @param diagnostics diagnostics
     * @throws IOException when writing fails
     */
    public void writeDiagnostics(final ProjectLayout layout, final List<Diagnostic> diagnostics) throws IOException {
        writeDiagnostics(layout, diagnostics, ThreadReports.summarize(diagnostics));
    }

    /**
     * Writes diagnostics and reachable thread summary details.
     *
     * @param layout detected project layout
     * @param diagnostics diagnostics
     * @param classes scanned classes
     * @param callGraph reachable methods and caller edges
     * @throws IOException when writing fails
     */
    public void writeDiagnostics(
        final ProjectLayout layout,
        final List<Diagnostic> diagnostics,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        writeDiagnostics(layout, diagnostics, ThreadReports.summarize(diagnostics, classes, callGraph), classes, callGraph);
    }

    private void writeDiagnostics(
        final ProjectLayout layout,
        final List<Diagnostic> diagnostics,
        final ThreadReports.Summary threadSummary
    ) throws IOException {
        final String value = diagnosticsValue(diagnostics);
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        final Path reports = layout.outputDirectory().resolve("reports");
        Files2.writeString(reports.resolve("diagnostics.txt"), value);
        Files2.writeString(reports.resolve("diagnostics.json"), diagnosticsJson(diagnostics));
        Files2.writeString(reports.resolve("diagnostics.md"), diagnosticsMarkdown(diagnostics));
        threadReports.write(reports, diagnostics, threadSummary);
        virtualThreadReports.write(reports);
    }

    private void writeDiagnostics(
        final ProjectLayout layout,
        final List<Diagnostic> diagnostics,
        final ThreadReports.Summary threadSummary,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        writeReachabilityReport(layout, callGraph);
        final String value = diagnosticsValue(diagnostics);
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        final Path reports = layout.outputDirectory().resolve("reports");
        Files2.writeString(reports.resolve("diagnostics.txt"), value);
        Files2.writeString(reports.resolve("diagnostics.json"), diagnosticsJson(diagnostics));
        Files2.writeString(reports.resolve("diagnostics.md"), diagnosticsMarkdown(diagnostics));
        threadReports.write(reports, diagnostics, threadSummary);
        virtualThreadReports.write(reports, diagnostics, classes, callGraph);
        writeCallGraph(reports, callGraph, diagnostics);
    }

    /**
     * Refreshes virtual-thread runtime status from existing profiling reports without changing the
     * last recorded reachability summary.
     *
     * @param outputDirectory project output directory
     * @throws IOException when reading or writing fails
     */
    public void refreshVirtualThreadRuntimeStatus(final Path outputDirectory) throws IOException {
        virtualThreadReports.refresh(outputDirectory.resolve("reports"));
    }

    private static String jsonList(final List<String> values) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(json(values.get(index)));
        }
        return result.append("]").toString();
    }

    private static String pathJsonList(final List<Path> values) {
        final List<String> strings = new ArrayList<>();
        for (final Path value : values) {
            strings.add(value.toString());
        }
        return jsonList(strings);
    }

    private static List<String> sortedEntries(final List<EntryPoint> entries) {
        final List<String> result = new ArrayList<>();
        for (final EntryPoint entry : sortedMethods(entries)) {
            result.add(entry.display());
        }
        return List.copyOf(result);
    }

    private static void writeCallGraph(final Path reports, final CallGraph callGraph) throws IOException {
        writeCallGraph(reports, callGraph, callGraph.diagnostics());
    }

    private static void writeCallGraph(
        final Path reports,
        final CallGraph callGraph,
        final List<Diagnostic> diagnostics
    ) throws IOException {
        final List<EntryPoint> methods = sortedMethods(callGraph.reachableMethods());
        final List<CallEdge> edges = sortedEdges(callGraph.callEdges());
        final FindingSummary findings = findingSummary(methods, diagnostics);
        Files2.writeString(reports.resolve("call-graph.json"), callGraphJson(callGraph.entryPoint(), methods, edges, findings));
        Files2.writeString(reports.resolve("call-graph.md"), callGraphMarkdown(callGraph.entryPoint(), methods, edges, findings));
        Files2.writeString(reports.resolve("call-flow.html"), callGraphHtml(callGraph.entryPoint(), methods, edges, findings));
        Files2.writeString(reports.resolve("call-graph.dot"), callGraphDot(callGraph.entryPoint(), methods, edges, findings));
    }

    private static String callGraphJson(
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges,
        final FindingSummary findings
    ) {
        final String completeness = graphCompleteness(methods);
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        appendJsonField(result, "schemaVersion", "1", true);
        appendJsonField(result, "completeness", json(completeness), true);
        appendJsonField(result, "entryPoint", json(entryPoint.display()), true);
        appendJsonField(result, "entryPointLabel", json(methodLabel(entryPoint)), true);
        appendJsonField(result, "reachableMethods", Integer.toString(methods.size()), true);
        appendJsonField(result, "edgeCount", Integer.toString(edges.size()), true);
        appendJsonField(result, "diagnostics", Integer.toString(findings.diagnostics()), true);
        appendJsonField(result, "errors", Integer.toString(findings.errors()), true);
        appendJsonField(result, "warnings", Integer.toString(findings.warnings()), true);
        appendJsonField(result, "methodsWithFindings", Integer.toString(findings.methodsWithFindings()), true);
        appendJsonField(result, "diagnosticsOutsideFlow", Integer.toString(findings.outsideFlow().size()), true);
        result.append("  \"nodes\": [\n");
        for (int index = 0; index < methods.size(); index++) {
            final EntryPoint method = methods.get(index);
            result.append("    {")
                .append("\"method\": ").append(json(method.display()))
                .append(", \"label\": ").append(json(methodLabel(method)))
                .append(", \"owner\": ").append(json(method.className()))
                .append(", \"name\": ").append(json(method.methodName()))
                .append(", \"descriptor\": ").append(json(method.descriptor()))
                .append(", \"findings\": ").append(methodFindingsJson(findings.forMethod(method)))
                .append("}");
            if (index + 1 < methods.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ],\n");
        result.append("  \"edges\": [\n");
        for (int index = 0; index < edges.size(); index++) {
            final CallEdge edge = edges.get(index);
            result.append("    {")
                .append("\"caller\": ").append(json(edge.caller().display()))
                .append(", \"callerLabel\": ").append(json(methodLabel(edge.caller())))
                .append(", \"callee\": ").append(json(edge.callee().display()))
                .append(", \"calleeLabel\": ").append(json(methodLabel(edge.callee())))
                .append(", \"kind\": ").append(json(edgeKind(edge.kind())))
                .append(", \"label\": ").append(json(edgePhrase(edge.kind())))
                .append("}");
            if (index + 1 < edges.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ],\n");
        result.append("  \"scope\": ").append(json(graphScope(methods))).append('\n');
        return result.append("}\n").toString();
    }

    private static String callGraphMarkdown(
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges,
        final FindingSummary findings
    ) {
        final String completeness = graphCompleteness(methods);
        final StringBuilder result = new StringBuilder();
        result.append("# Call Graph\n\n");
        result.append("- completeness: `").append(completeness).append("`\n");
        result.append("- entry point: `").append(methods.isEmpty() ? "not available" : methodLabel(entryPoint)).append("`\n");
        result.append("- reachable methods: `").append(methods.size()).append("`\n");
        result.append("- edges: `").append(edges.size()).append("`\n");
        result.append("- visualization: [open the call flow in a browser](call-flow.html)\n");
        result.append("- optional export: [Graphviz DOT](call-graph.dot)\n\n");
        result.append("## Edge Kinds\n\n");
        for (final CallEdge.Kind kind : CallEdge.Kind.values()) {
            result.append("- ").append(edgeKind(kind)).append(": `").append(edgeCount(edges, kind)).append("`\n");
        }
        appendFindingsMarkdown(result, methods, findings);
        result.append("\n## Flow\n\n");
        if (edges.isEmpty()) {
            result.append("No method-to-method calls were proven reachable.\n");
        } else {
            for (final CallEdge edge : edges) {
                result.append("- `").append(methodLabel(edge.caller())).append("` ")
                    .append(edgePhrase(edge.kind())).append(" `").append(methodLabel(edge.callee())).append("`.\n");
            }
        }
        result.append("\n## Scope\n\n");
        result.append(graphScope(methods)).append("\n");
        return result.toString();
    }

    private static String callGraphHtml(
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges,
        final FindingSummary findings
    ) {
        final StringBuilder result = new StringBuilder();
        result.append("""
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Javan Call Flow</title>
              <style>
                :root { color-scheme: light; }
                * { box-sizing: border-box; }
                body { margin: 0; background: #f6f7f8; color: #1f2933; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
                main { max-width: 1440px; margin: 0 auto; padding: 32px 24px 48px; }
                h1, h2, p { margin: 0; }
                h1 { font: 600 28px/1.2 system-ui, sans-serif; letter-spacing: -0.02em; }
                h2 { font: 600 16px/1.3 system-ui, sans-serif; }
                .intro { color: #52606d; margin-top: 8px; max-width: 72ch; line-height: 1.55; }
                .facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1px; margin: 24px 0; background: #d9e2ec; border: 1px solid #d9e2ec; border-radius: 8px; overflow: hidden; }
                .facts div { min-width: 0; padding: 14px 16px; background: #ffffff; }
                dt { color: #627d98; font: 600 12px/1.2 system-ui, sans-serif; }
                dd { margin: 6px 0 0; font-size: 13px; overflow-wrap: anywhere; }
                .diagram { border: 1px solid #cbd5e1; border-radius: 8px; background: #ffffff; overflow: auto; }
                svg { display: block; min-width: 100%; }
                .legend { display: flex; flex-wrap: wrap; gap: 16px; padding: 12px 16px; border-top: 1px solid #e5e7eb; color: #52606d; font: 13px/1.4 system-ui, sans-serif; }
                .key { display: inline-flex; align-items: center; gap: 7px; }
                .line { width: 22px; height: 0; border-top: 2px solid #64748b; }
                .line.initialize { border-top-color: #7c3aed; border-top-style: dashed; }
                .line.thread { border-top-color: #b45309; }
                .scope { margin-top: 20px; color: #52606d; font: 14px/1.55 system-ui, sans-serif; }
                .empty { padding: 36px; text-align: center; }
                .empty p { color: #52606d; margin-top: 8px; font: 14px/1.5 system-ui, sans-serif; }
                .edge { fill: none; stroke: #64748b; stroke-width: 1.7; marker-end: url(#arrow); }
                .edge.initialize { stroke: #7c3aed; stroke-dasharray: 5 4; marker-end: url(#arrow-initialize); }
                .edge.thread { stroke: #b45309; stroke-width: 2.3; marker-end: url(#arrow-thread); }
                .edge-label { fill: #52606d; font: 12px system-ui, sans-serif; text-anchor: middle; }
                .node rect { fill: #eff6ff; stroke: #2563eb; stroke-width: 1.4; }
                .node.entry rect { fill: #ecfdf3; stroke: #15803d; stroke-width: 2; }
                .node.warning rect { fill: #fffbeb; stroke: #b45309; stroke-width: 2; }
                .node.error rect { fill: #fef2f2; stroke: #b91c1c; stroke-width: 2; }
                .node-owner { fill: #17202a; font: 600 14px system-ui, sans-serif; }
                .node-method { fill: #334e68; font: 13px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
                .finding-count { fill: #ffffff; font: 600 12px system-ui, sans-serif; text-anchor: middle; }
                .finding-count-background.warning { fill: #b45309; }
                .finding-count-background.error { fill: #b91c1c; }
                .findings { margin-top: 20px; border: 1px solid #cbd5e1; border-radius: 8px; background: #ffffff; }
                .findings-header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; padding: 16px; border-bottom: 1px solid #e5e7eb; }
                .findings-header p { color: #52606d; font: 14px/1.45 system-ui, sans-serif; }
                .findings-header a { color: #1d4ed8; }
                .finding-list { margin: 0; padding: 0; list-style: none; }
                .finding { padding: 14px 16px; border-top: 1px solid #e5e7eb; }
                .finding:first-child { border-top: 0; }
                .finding.error { border-left: 3px solid #b91c1c; }
                .finding.warning { border-left: 3px solid #b45309; }
                .finding p { margin-top: 6px; color: #334e68; font: 14px/1.45 system-ui, sans-serif; }
                .finding-meta { color: #52606d; font-size: 13px; }
                .finding-code { color: #17202a; font-weight: 600; }
                @media (max-width: 640px) {
                  main { padding: 24px 16px 36px; }
                  .facts { grid-template-columns: 1fr; }
                }
              </style>
            </head>
            <body>
              <main>
                <h1>Call flow</h1>
            """);
        result.append("    <p class=\"intro\">")
            .append(html(flowDescription(entryPoint, methods, edges)))
            .append("</p>\n");
        appendCallGraphFacts(result, entryPoint, methods, edges, findings);
        if (methods.isEmpty()) {
            result.append("""
                    <section class="diagram empty">
                      <h2>No visual flow is available</h2>
                      <p>Javan needs a valid application entry point before it can prove reachable code.</p>
                    </section>
                """);
        } else {
            appendCallGraphSvg(result, entryPoint, methods, edges, findings);
            result.append("""
                    <div class="legend" aria-label="Edge legend">
                      <span class="key"><span class="line"></span>method call</span>
                      <span class="key"><span class="line initialize"></span>class initialization</span>
                      <span class="key"><span class="line thread"></span>thread start</span>
                    </div>
                """);
        }
        appendFindingsHtml(result, methods, findings);
        result.append("    <p class=\"scope\">").append(html(graphScope(methods))).append("</p>\n");
        return result.append("""
              </main>
            </body>
            </html>
            """).toString();
    }

    private static void appendCallGraphFacts(
        final StringBuilder result,
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges,
        final FindingSummary findings
    ) {
        result.append("    <dl class=\"facts\">\n");
        appendCallGraphFact(result, "Starts at", methods.isEmpty() ? "not available" : methodLabel(entryPoint));
        appendCallGraphFact(result, "Reachable methods", Integer.toString(methods.size()));
        appendCallGraphFact(result, "Proven connections", Integer.toString(edges.size()));
        appendCallGraphFact(result, "Errors", Integer.toString(findings.errors()));
        appendCallGraphFact(result, "Warnings", Integer.toString(findings.warnings()));
        result.append("    </dl>\n");
    }

    private static void appendCallGraphFact(final StringBuilder result, final String label, final String value) {
        result.append("      <div><dt>").append(html(label)).append("</dt><dd>").append(html(value))
            .append("</dd></div>\n");
    }

    private static void appendFindingsHtml(
        final StringBuilder result,
        final List<EntryPoint> methods,
        final FindingSummary findings
    ) {
        result.append("""
                <section class="findings">
                  <div class="findings-header">
                    <h2>Static findings</h2>
            """);
        if (findings.diagnostics() == 0) {
            result.append("      <p>No findings were reported for this analysis.</p>\n");
        } else {
            result.append("      <p>").append(findings.diagnostics()).append(" finding")
                .append(pluralSuffix(findings.diagnostics())).append(" from this analysis. ")
                .append("<a href=\"diagnostics.md\">Open all diagnostic details</a>.</p>\n");
        }
        result.append("    </div>\n");
        if (findings.diagnostics() != 0) {
            result.append("    <ul class=\"finding-list\">\n");
            for (final EntryPoint method : methods) {
                for (final Diagnostic finding : findings.forMethod(method)) {
                    appendHtmlFinding(result, finding, methodLabel(method));
                }
            }
            for (final Diagnostic finding : findings.outsideFlow()) {
                appendHtmlFinding(result, finding, "Outside current flow");
            }
            result.append("    </ul>\n");
        }
        result.append("    </section>\n");
    }

    private static void appendHtmlFinding(
        final StringBuilder result,
        final Diagnostic finding,
        final String location
    ) {
        final String severity = findingSeverity(finding);
        result.append("      <li class=\"finding ").append(severity).append("\"><div class=\"finding-meta\">")
            .append(html(location)).append(" - ").append(html(severity)).append(" </div><p><span class=\"finding-code\">[")
            .append(html(emptyDash(finding.code()))).append("]</span> ").append(html(emptyDash(finding.message())))
            .append("</p></li>\n");
    }

    private static void appendCallGraphSvg(
        final StringBuilder result,
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges,
        final FindingSummary findings
    ) {
        final FlowLayout layout = flowLayout(entryPoint, methods, edges);
        result.append("    <section class=\"diagram\" aria-label=\"Static call flow\">\n");
        result.append("      <svg viewBox=\"0 0 ").append(layout.width()).append(' ').append(layout.height())
            .append("\" role=\"img\" aria-label=\"Static Java call flow\">\n");
        result.append("""
                <title>Static Java call flow</title>
                <defs>
                  <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path fill="#64748b" d="M 0 0 L 10 5 L 0 10 z"/></marker>
                  <marker id="arrow-initialize" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path fill="#7c3aed" d="M 0 0 L 10 5 L 0 10 z"/></marker>
                  <marker id="arrow-thread" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path fill="#b45309" d="M 0 0 L 10 5 L 0 10 z"/></marker>
                </defs>
            """);
        for (final CallEdge edge : edges) {
            final FlowNode caller = layout.nodes().get(edge.caller().display());
            final FlowNode callee = layout.nodes().get(edge.callee().display());
            if (caller != null && callee != null) {
                appendHtmlEdge(result, edge, caller, callee);
            }
        }
        for (final EntryPoint method : methods) {
            final FlowNode node = layout.nodes().get(method.display());
            if (node != null) {
                appendHtmlNode(result, method, node, method.equals(entryPoint), findings.forMethod(method));
            }
        }
        result.append("      </svg>\n    </section>\n");
    }

    private static void appendHtmlEdge(
        final StringBuilder result,
        final CallEdge edge,
        final FlowNode caller,
        final FlowNode callee
    ) {
        final int startX = caller.x() + FlowLayout.NODE_WIDTH;
        final int startY = caller.y() + FlowLayout.NODE_HEIGHT / 2;
        final int endX = callee.x();
        final int endY = callee.y() + FlowLayout.NODE_HEIGHT / 2;
        final int bend = Math.max(54, Math.abs(endX - startX) / 2);
        final String edgeClass = htmlEdgeClass(edge.kind());
        result.append("        <path class=\"edge ").append(edgeClass).append("\" d=\"M ")
            .append(startX).append(' ').append(startY).append(" C ");
        if (endX <= startX) {
            final int detourY = Math.max(startY, endY) + FlowLayout.RETURN_EDGE_OFFSET;
            result.append(startX + FlowLayout.RETURN_EDGE_OFFSET).append(' ').append(detourY).append(' ')
                .append(endX + FlowLayout.RETURN_EDGE_OFFSET).append(' ').append(detourY).append(' ');
        } else {
            result.append(startX + bend).append(' ').append(startY).append(' ')
                .append(endX - bend).append(' ').append(endY).append(' ');
        }
        result.append(endX).append(' ').append(endY).append("\"><title>")
            .append(html(methodLabel(edge.caller()))).append(' ').append(html(edgePhrase(edge.kind()))).append(' ')
            .append(html(methodLabel(edge.callee()))).append("</title></path>\n");
        if (edge.kind() != CallEdge.Kind.CALL) {
            final int labelX = (startX + endX) / 2;
            final int labelY = (startY + endY) / 2 - 8;
            result.append("        <text class=\"edge-label\" x=\"").append(labelX).append("\" y=\"")
                .append(labelY).append("\">").append(html(edgePhrase(edge.kind()))).append("</text>\n");
        }
    }

    private static String htmlEdgeClass(final CallEdge.Kind kind) {
        return switch (kind) {
            case CALL -> "call";
            case CLASS_INITIALIZER -> "initialize";
            case THREAD_START_TASK -> "thread";
        };
    }

    private static void appendHtmlNode(
        final StringBuilder result,
        final EntryPoint method,
        final FlowNode node,
        final boolean entryPoint,
        final List<Diagnostic> findings
    ) {
        final List<String> lines = nodeLines(method);
        final String findingClass = findingClass(findings);
        result.append("        <g class=\"node");
        if (entryPoint) {
            result.append(" entry");
        }
        if (!findingClass.isEmpty()) {
            result.append(' ').append(findingClass);
        }
        result.append("\"><title>").append(html(nodeTitle(method, findings))).append("</title><rect x=\"")
            .append(node.x()).append("\" y=\"").append(node.y()).append("\" width=\"")
            .append(FlowLayout.NODE_WIDTH).append("\" height=\"").append(FlowLayout.NODE_HEIGHT)
            .append("\" rx=\"8\"/>\n");
        result.append("        <text class=\"node-owner\" x=\"").append(node.x() + 14).append("\" y=\"")
            .append(node.y() + 24).append("\">").append(html(lines.getFirst())).append("</text>\n");
        result.append("        <text class=\"node-method\" x=\"").append(node.x() + 14).append("\" y=\"")
            .append(node.y() + 45).append("\">").append(html(lines.get(1))).append("</text>\n");
        if (!findingClass.isEmpty()) {
            result.append("        <rect class=\"finding-count-background ").append(findingClass).append("\" x=\"")
                .append(node.x() + 204).append("\" y=\"").append(node.y() + 10)
                .append("\" width=\"32\" height=\"20\" rx=\"10\"/>\n");
            result.append("        <text class=\"finding-count\" x=\"").append(node.x() + 220).append("\" y=\"")
                .append(node.y() + 25).append("\">").append(findingBadge(findings.size())).append("</text>\n");
        }
        result.append("        </g>\n");
    }

    private static String findingClass(final List<Diagnostic> findings) {
        if (errorCount(findings) > 0) {
            return "error";
        }
        return findings.isEmpty() ? "" : "warning";
    }

    private static String findingBadge(final int count) {
        return count > 99 ? "99+" : Integer.toString(count);
    }

    private static String nodeTitle(final EntryPoint method, final List<Diagnostic> findings) {
        final StringBuilder result = new StringBuilder(methodLabel(method));
        for (final Diagnostic finding : findings) {
            result.append(System.lineSeparator()).append(findingSeverity(finding)).append(" [")
                .append(emptyDash(finding.code())).append("]: ").append(emptyDash(finding.message()));
        }
        return result.toString();
    }

    private static List<String> nodeLines(final EntryPoint entryPoint) {
        final String owner = shortTypeName(entryPoint.className());
        if ("<clinit>".equals(entryPoint.methodName())) {
            return List.of(owner, "initialization");
        }
        if ("<init>".equals(entryPoint.methodName())) {
            return List.of("new " + owner, "(" + parameterLabels(entryPoint.descriptor()) + ")");
        }
        return List.of(owner, entryPoint.methodName() + "(" + parameterLabels(entryPoint.descriptor()) + ")");
    }

    private static FlowLayout flowLayout(
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges
    ) {
        final Map<String, EntryPoint> methodsById = new HashMap<>();
        final Map<String, List<EntryPoint>> callsByMethod = new HashMap<>();
        for (final EntryPoint method : methods) {
            methodsById.put(method.display(), method);
        }
        for (final CallEdge edge : edges) {
            if (methodsById.containsKey(edge.caller().display()) && methodsById.containsKey(edge.callee().display())) {
                callsByMethod.computeIfAbsent(edge.caller().display(), ignored -> new ArrayList<>()).add(edge.callee());
            }
        }
        final Map<String, Integer> layers = new HashMap<>();
        final List<EntryPoint> queue = new ArrayList<>();
        final EntryPoint root = methodsById.getOrDefault(entryPoint.display(), methods.getFirst());
        layers.put(root.display(), 0);
        queue.add(root);
        for (int index = 0; index < queue.size(); index++) {
            final EntryPoint caller = queue.get(index);
            final int callerLayer = layers.get(caller.display());
            for (final EntryPoint callee : callsByMethod.getOrDefault(caller.display(), List.of())) {
                if (!layers.containsKey(callee.display())) {
                    layers.put(callee.display(), callerLayer + 1);
                    queue.add(callee);
                }
            }
        }
        final int unconnectedLayer = highestLayer(layers) + 1;
        for (final EntryPoint method : methods) {
            if (!layers.containsKey(method.display())) {
                layers.put(method.display(), unconnectedLayer);
            }
        }
        final int layerCount = highestLayer(layers) + 1;
        final List<List<EntryPoint>> grouped = new ArrayList<>();
        for (int index = 0; index < layerCount; index++) {
            grouped.add(new ArrayList<>());
        }
        for (final EntryPoint method : methods) {
            grouped.get(layers.get(method.display())).add(method);
        }
        final Map<String, FlowNode> nodes = new HashMap<>();
        int largestLayer = 1;
        for (int layer = 0; layer < grouped.size(); layer++) {
            final List<EntryPoint> group = grouped.get(layer);
            largestLayer = Math.max(largestLayer, group.size());
            for (int index = 0; index < group.size(); index++) {
                nodes.put(
                    group.get(index).display(),
                    new FlowNode(
                        FlowLayout.HORIZONTAL_MARGIN + layer * FlowLayout.HORIZONTAL_DISTANCE,
                        FlowLayout.VERTICAL_MARGIN + index * FlowLayout.VERTICAL_DISTANCE
                    )
                );
            }
        }
        final int width = Math.max(720, FlowLayout.HORIZONTAL_MARGIN * 2
            + (layerCount - 1) * FlowLayout.HORIZONTAL_DISTANCE + FlowLayout.NODE_WIDTH);
        final int height = Math.max(180, FlowLayout.VERTICAL_MARGIN * 2
            + (largestLayer - 1) * FlowLayout.VERTICAL_DISTANCE + FlowLayout.NODE_HEIGHT + FlowLayout.RETURN_EDGE_OFFSET);
        return new FlowLayout(width, height, Map.copyOf(nodes));
    }

    private static int highestLayer(final Map<String, Integer> layers) {
        int result = 0;
        for (final int layer : layers.values()) {
            result = Math.max(result, layer);
        }
        return result;
    }

    private static String flowDescription(
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges
    ) {
        if (methods.isEmpty()) {
            return "No application entry point was available for static analysis.";
        }
        return "Javan proved " + methods.size() + " reachable method" + pluralSuffix(methods.size())
            + " and " + edges.size() + " connection" + pluralSuffix(edges.size())
            + " starting at " + methodLabel(entryPoint) + ".";
    }

    private static String pluralSuffix(final int count) {
        return count == 1 ? "" : "s";
    }

    private static String html(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&#39;");
                default -> result.append(character);
            }
        }
        return result.toString();
    }

    private record FlowLayout(int width, int height, Map<String, FlowNode> nodes) {
        private static final int NODE_WIDTH = 248;
        private static final int NODE_HEIGHT = 58;
        private static final int HORIZONTAL_MARGIN = 34;
        private static final int VERTICAL_MARGIN = 34;
        private static final int HORIZONTAL_DISTANCE = 330;
        private static final int VERTICAL_DISTANCE = 82;
        private static final int RETURN_EDGE_OFFSET = 56;
    }

    private record FlowNode(int x, int y) {
    }

    private static String callGraphDot(
        final EntryPoint entryPoint,
        final List<EntryPoint> methods,
        final List<CallEdge> edges,
        final FindingSummary findings
    ) {
        final StringBuilder result = new StringBuilder();
        result.append("digraph javan_call_graph {\n");
        result.append("  graph [rankdir=LR, bgcolor=\"transparent\", pad=0.2, nodesep=0.35, ranksep=0.7];\n");
        result.append("  node [shape=box, style=\"rounded,filled\", fillcolor=\"#eff6ff\", color=\"#2563eb\", fontname=\"Helvetica\"];\n");
        result.append("  edge [color=\"#64748b\", arrowsize=0.7, fontname=\"Helvetica\"];\n");
        for (final EntryPoint method : methods) {
            result.append("  ").append(dot(method.display())).append(" [label=").append(dot(methodLabel(method)));
            if (method.equals(entryPoint)) {
                result.append(", shape=doubleoctagon, fillcolor=\"#dcfce7\", color=\"#15803d\", penwidth=2");
            }
            appendDotFindingStyle(result, findings.forMethod(method));
            result.append("];\n");
        }
        for (final CallEdge edge : edges) {
            result.append("  ").append(dot(edge.caller().display())).append(" -> ").append(dot(edge.callee().display()));
            appendDotEdgeStyle(result, edge.kind());
            result.append(";\n");
        }
        return result.append("}\n").toString();
    }

    private static void appendDotFindingStyle(final StringBuilder result, final List<Diagnostic> findings) {
        final String findingClass = findingClass(findings);
        if (findingClass.isEmpty()) {
            return;
        }
        if ("error".equals(findingClass)) {
            result.append(", fillcolor=\"#fef2f2\", color=\"#b91c1c\", penwidth=2");
        } else {
            result.append(", fillcolor=\"#fffbeb\", color=\"#b45309\", penwidth=2");
        }
        result.append(", tooltip=").append(dot(nodeTitleForDot(findings)));
    }

    private static String nodeTitleForDot(final List<Diagnostic> findings) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < findings.size(); index++) {
            if (index > 0) {
                result.append(System.lineSeparator());
            }
            final Diagnostic finding = findings.get(index);
            result.append(findingSeverity(finding)).append(" [").append(emptyDash(finding.code())).append("]: ")
                .append(emptyDash(finding.message()));
        }
        return result.toString();
    }

    private static void appendDotEdgeStyle(final StringBuilder result, final CallEdge.Kind kind) {
        if (kind == CallEdge.Kind.CLASS_INITIALIZER) {
            result.append(" [label=\"initializes\", style=dashed, color=\"#7c3aed\"]");
        } else if (kind == CallEdge.Kind.THREAD_START_TASK) {
            result.append(" [label=\"starts a thread\", penwidth=2, color=\"#b45309\"]");
        }
    }

    private static List<EntryPoint> sortedMethods(final List<EntryPoint> entries) {
        final List<EntryPoint> result = new ArrayList<>();
        result.addAll(entries);
        result.sort((left, right) -> Strings2.compareAscii(left.display(), right.display()));
        return List.copyOf(result);
    }

    private static List<CallEdge> sortedEdges(final List<CallEdge> edges) {
        final List<CallEdge> result = new ArrayList<>();
        result.addAll(edges);
        result.sort((left, right) -> Strings2.compareAscii(edgeKey(left), edgeKey(right)));
        return List.copyOf(result);
    }

    private static String edgeKey(final CallEdge edge) {
        return edge.caller().display() + '\u0000' + edgeKind(edge.kind()) + '\u0000' + edge.callee().display();
    }

    private static String edgeKind(final CallEdge.Kind kind) {
        return switch (kind) {
            case CALL -> "call";
            case CLASS_INITIALIZER -> "class-initializer";
            case THREAD_START_TASK -> "thread-start-task";
        };
    }

    private static String edgePhrase(final CallEdge.Kind kind) {
        return switch (kind) {
            case CALL -> "calls";
            case CLASS_INITIALIZER -> "initializes";
            case THREAD_START_TASK -> "starts a thread running";
        };
    }

    private static FindingSummary findingSummary(
        final List<EntryPoint> methods,
        final List<Diagnostic> diagnostics
    ) {
        final Map<String, List<Diagnostic>> byMethod = new HashMap<>();
        for (final EntryPoint method : methods) {
            byMethod.put(methodKey(method.className(), method.methodName() + method.descriptor()), new ArrayList<>());
        }
        final List<Diagnostic> outsideFlow = new ArrayList<>();
        int errors = 0;
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                errors++;
            }
            final List<Diagnostic> methodFindings = byMethod.get(methodKey(diagnostic.className(), diagnostic.methodName()));
            if (methodFindings == null) {
                outsideFlow.add(diagnostic);
            } else {
                methodFindings.add(diagnostic);
            }
        }
        final Map<String, List<Diagnostic>> immutableByMethod = new HashMap<>();
        for (final Map.Entry<String, List<Diagnostic>> entry : byMethod.entrySet()) {
            immutableByMethod.put(entry.getKey(), sortedDiagnostics(entry.getValue()));
        }
        return new FindingSummary(
            diagnostics.size(),
            errors,
            Map.copyOf(immutableByMethod),
            sortedDiagnostics(outsideFlow)
        );
    }

    private static String methodKey(final String className, final String methodName) {
        if (Strings2.isBlank(className) || Strings2.isBlank(methodName)) {
            return "";
        }
        return className + '\u0000' + methodName;
    }

    private static List<Diagnostic> sortedDiagnostics(final List<Diagnostic> diagnostics) {
        final List<Diagnostic> result = new ArrayList<>(diagnostics);
        result.sort((left, right) -> Strings2.compareAscii(diagnosticKey(left), diagnosticKey(right)));
        return List.copyOf(result);
    }

    private static String diagnosticKey(final Diagnostic diagnostic) {
        return nonBlank(diagnostic.code()) + '\u0000' + nonBlank(diagnostic.className()) + '\u0000'
            + nonBlank(diagnostic.methodName()) + '\u0000' + nonBlank(diagnostic.message());
    }

    private static String nonBlank(final String value) {
        return value == null ? "" : value;
    }

    private static String methodFindingsJson(final List<Diagnostic> findings) {
        final List<String> codes = new ArrayList<>();
        for (final Diagnostic finding : findings) {
            codes.add(nonBlank(finding.code()));
        }
        final long errors = errorCount(findings);
        return "{\"errors\": " + errors
            + ", \"warnings\": " + (findings.size() - errors)
            + ", \"codes\": " + jsonList(codes) + "}";
    }

    private static void appendFindingsMarkdown(
        final StringBuilder result,
        final List<EntryPoint> methods,
        final FindingSummary findings
    ) {
        result.append("\n## Static Findings\n\n");
        result.append("- errors: `").append(findings.errors()).append("`\n");
        result.append("- warnings: `").append(findings.warnings()).append("`\n");
        result.append("- reachable methods affected: `").append(findings.methodsWithFindings()).append("`\n");
        result.append("- findings outside current flow: `").append(findings.outsideFlow().size()).append("`\n");
        result.append("- details: [diagnostics](diagnostics.md)\n");
        if (findings.diagnostics() == 0) {
            return;
        }
        result.append("\n### By Reachable Method\n\n");
        boolean attached = false;
        for (final EntryPoint method : methods) {
            for (final Diagnostic finding : findings.forMethod(method)) {
                attached = true;
                result.append("- `").append(methodLabel(method)).append("`: ")
                    .append(findingSeverity(finding)).append(" `[").append(nonBlank(finding.code())).append("]` ")
                    .append(nonBlank(finding.message())).append("\n");
            }
        }
        if (!attached) {
            result.append("No findings match a reachable method exactly.\n");
        }
        if (findings.outsideFlow().isEmpty()) {
            return;
        }
        result.append("\n### Outside Current Flow\n\n");
        for (final Diagnostic finding : findings.outsideFlow()) {
            result.append("- ").append(findingSeverity(finding)).append(" `[")
                .append(nonBlank(finding.code())).append("]` ").append(nonBlank(finding.message())).append("\n");
        }
    }

    private static String findingSeverity(final Diagnostic finding) {
        return finding.error() ? "error" : "warning";
    }

    private record FindingSummary(
        int diagnostics,
        int errors,
        Map<String, List<Diagnostic>> byMethod,
        List<Diagnostic> outsideFlow
    ) {
        private int warnings() {
            return diagnostics - errors;
        }

        private int methodsWithFindings() {
            int result = 0;
            for (final List<Diagnostic> findings : byMethod.values()) {
                if (!findings.isEmpty()) {
                    result++;
                }
            }
            return result;
        }

        private List<Diagnostic> forMethod(final EntryPoint method) {
            return byMethod.getOrDefault(methodKey(method.className(), method.methodName() + method.descriptor()), List.of());
        }
    }

    private static String methodLabel(final EntryPoint entryPoint) {
        if (Strings2.isBlank(entryPoint.className())) {
            return "not available";
        }
        final String owner = shortTypeName(entryPoint.className());
        if ("<clinit>".equals(entryPoint.methodName())) {
            return owner + " initialization";
        }
        final String name = "<init>".equals(entryPoint.methodName())
            ? "new " + owner
            : owner + "." + entryPoint.methodName();
        return name + "(" + parameterLabels(entryPoint.descriptor()) + ")";
    }

    private static String parameterLabels(final String descriptor) {
        if (Strings2.isBlank(descriptor) || descriptor.charAt(0) != '(') {
            return "?";
        }
        final List<String> result = new ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final DescriptorToken token = descriptorToken(descriptor, index);
            if (!token.valid()) {
                return "?";
            }
            result.add(token.label());
            index = token.end();
        }
        if (index >= descriptor.length()) {
            return "?";
        }
        final StringBuilder labels = new StringBuilder();
        for (int labelIndex = 0; labelIndex < result.size(); labelIndex++) {
            if (labelIndex > 0) {
                labels.append(", ");
            }
            labels.append(result.get(labelIndex));
        }
        return labels.toString();
    }

    private static DescriptorToken descriptorToken(final String descriptor, final int start) {
        int index = start;
        int arrays = 0;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            arrays++;
            index++;
        }
        if (index >= descriptor.length()) {
            return DescriptorToken.invalid();
        }
        final char type = descriptor.charAt(index);
        final String label;
        if (type == 'L') {
            final int end = descriptor.indexOf(';', index);
            if (end < 0) {
                return DescriptorToken.invalid();
            }
            label = shortTypeName(descriptor.substring(index + 1, end));
            index = end + 1;
        } else {
            label = primitiveType(type);
            if (label.isEmpty()) {
                return DescriptorToken.invalid();
            }
            index++;
        }
        final StringBuilder arrayLabel = new StringBuilder(label);
        for (int arrayIndex = 0; arrayIndex < arrays; arrayIndex++) {
            arrayLabel.append("[]");
        }
        return new DescriptorToken(arrayLabel.toString(), index, true);
    }

    private static String primitiveType(final char type) {
        return switch (type) {
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'D' -> "double";
            case 'F' -> "float";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'S' -> "short";
            case 'Z' -> "boolean";
            default -> "";
        };
    }

    private static String shortTypeName(final String internalName) {
        final int slash = internalName.lastIndexOf('/');
        final String simple = slash < 0 ? internalName : internalName.substring(slash + 1);
        return simple.replace('$', '.');
    }

    private static String graphCompleteness(final List<EntryPoint> methods) {
        return methods.isEmpty() ? "not-analyzed" : "closed-world-proven";
    }

    private static String graphScope(final List<EntryPoint> methods) {
        if (methods.isEmpty()) {
            return "No reachable code was analyzed because a valid entry point is unavailable; see diagnostics.";
        }
        return "Only analyzer-proven closed-world edges are listed; unresolved or dynamic targets remain diagnostics.";
    }

    private static long edgeCount(final List<CallEdge> edges, final CallEdge.Kind target) {
        long result = 0L;
        for (final CallEdge edge : edges) {
            if (edge.kind() == target) {
                result++;
            }
        }
        return result;
    }

    private static String dot(final String value) {
        final StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch == '\\' || ch == '"') {
                result.append('\\').append(ch);
            } else if (ch == '\n' || ch == '\r' || ch < 0x20) {
                result.append(' ');
            } else {
                result.append(ch);
            }
        }
        return result.append('"').toString();
    }

    private record DescriptorToken(String label, int end, boolean valid) {
        private static DescriptorToken invalid() {
            return new DescriptorToken("", -1, false);
        }
    }

    private static String diagnosticsValue(final List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return new StringBuilder().append("No diagnostics.").append(System.lineSeparator()).toString();
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < diagnostics.size(); index++) {
            if (index > 0) {
                result.append(System.lineSeparator()).append(System.lineSeparator());
            }
            result.append(diagnostics.get(index).format());
        }
        return result.append(System.lineSeparator()).toString();
    }

    private static String diagnosticsJson(final List<Diagnostic> diagnostics) {
        final long errors = errorCount(diagnostics);
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"schemaVersion\": 1,\n");
        result.append("  \"diagnostics\": ").append(diagnostics.size()).append(",\n");
        result.append("  \"errors\": ").append(errors).append(",\n");
        result.append("  \"warnings\": ").append(diagnostics.size() - errors).append(",\n");
        result.append("  \"items\": [\n");
        for (int index = 0; index < diagnostics.size(); index++) {
            final Diagnostic diagnostic = diagnostics.get(index);
            result.append("    {\n");
            appendJsonField(result, "severity", json(diagnostic.error() ? "error" : "warning"), true, 6);
            appendJsonField(result, "code", json(diagnostic.code()), true, 6);
            appendJsonField(result, "message", json(diagnostic.message()), true, 6);
            appendJsonField(result, "class", json(diagnostic.className()), true, 6);
            appendJsonField(result, "method", json(diagnostic.methodName()), true, 6);
            appendJsonField(result, "subject", json(diagnostic.subject()), true, 6);
            appendJsonField(result, "reason", json(diagnostic.reason()), true, 6);
            appendJsonField(result, "fix", json(diagnostic.fix()), false, 6);
            result.append("    }");
            if (index + 1 < diagnostics.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        return result.append("}\n").toString();
    }

    private static String diagnosticsMarkdown(final List<Diagnostic> diagnostics) {
        final long errors = errorCount(diagnostics);
        final StringBuilder result = new StringBuilder();
        result.append("# Diagnostics\n\n");
        result.append("- diagnostics: `").append(diagnostics.size()).append("`\n");
        result.append("- errors: `").append(errors).append("`\n");
        result.append("- warnings: `").append(diagnostics.size() - errors).append("`\n\n");
        if (diagnostics.isEmpty()) {
            return result.append("No diagnostics.\n").toString();
        }
        for (final Diagnostic diagnostic : diagnostics) {
            result.append("## ")
                .append(diagnostic.error() ? "error" : "warning")
                .append("[")
                .append(diagnostic.code())
                .append("] ")
                .append(diagnostic.message())
                .append("\n\n");
            result.append("- class: `").append(emptyDash(diagnostic.className())).append("`\n");
            result.append("- method: `").append(emptyDash(diagnostic.methodName())).append("`\n");
            result.append("- subject: `").append(emptyDash(diagnostic.subject())).append("`\n");
            result.append("- reason: ").append(emptyDash(diagnostic.reason())).append('\n');
            result.append("- fix: ").append(emptyDash(diagnostic.fix())).append("\n\n");
        }
        return result.toString();
    }

    private static String emptyDash(final String value) {
        return Strings2.isBlank(value) ? "-" : value;
    }

    private static long errorCount(final List<Diagnostic> diagnostics) {
        long result = 0L;
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                result++;
            }
        }
        return result;
    }

    private static String json(final String value) {
        return Json.string(value);
    }

    private static void appendJsonField(
        final StringBuilder result,
        final String name,
        final String value,
        final boolean comma
    ) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static void appendJsonField(
        final StringBuilder result,
        final String name,
        final String value,
        final boolean comma,
        final int spaces
    ) {
        appendSpaces(result, spaces);
        result.append("\"").append(name).append("\": ").append(value);
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
}
