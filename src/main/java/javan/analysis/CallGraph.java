package javan.analysis;

import javan.verify.Diagnostic;

import java.util.List;

/**
 * Reachability result.
 *
 * @param entryPoint entry point method
 * @param reachableMethods reachable application methods
 * @param diagnostics reachability diagnostics
 * @param callEdges reachable caller-to-callee edges
 */
public record CallGraph(
    EntryPoint entryPoint,
    List<EntryPoint> reachableMethods,
    List<Diagnostic> diagnostics,
    List<CallEdge> callEdges
) {
    /**
     * Backward-compatible constructor for tests and utility call sites that do not care about caller edges yet.
     *
     * @param entryPoint entry point method
     * @param reachableMethods reachable application methods
     * @param diagnostics reachability diagnostics
     */
    public CallGraph(final EntryPoint entryPoint, final List<EntryPoint> reachableMethods, final List<Diagnostic> diagnostics) {
        this(entryPoint, reachableMethods, diagnostics, List.of());
    }
}
