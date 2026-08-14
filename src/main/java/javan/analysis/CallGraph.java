package javan.analysis;

import javan.verify.Diagnostic;

import java.util.List;

/**
 * Reachability result.
 *
 * @param entryPoint entry point method
 * @param reachableMethods reachable closed-world methods
 * @param diagnostics reachability diagnostics
 * @param callEdges reachable caller-to-callee edges
 * @param functionValueFlow exact Function receiver flow when analyzed
 * @param instantiatedTypes concrete receiver types proven constructible
 */
public record CallGraph(
    EntryPoint entryPoint,
    List<EntryPoint> reachableMethods,
    List<Diagnostic> diagnostics,
    List<CallEdge> callEdges,
    FunctionValueFlow.Result functionValueFlow,
    InstantiatedTypeAnalysis.Result instantiatedTypes
) {
    /**
     * Backward-compatible constructor for tests and utility call sites that do not care about caller edges yet.
     *
     * @param entryPoint entry point method
     * @param reachableMethods reachable closed-world methods
     * @param diagnostics reachability diagnostics
     */
    public CallGraph(final EntryPoint entryPoint, final List<EntryPoint> reachableMethods, final List<Diagnostic> diagnostics) {
        this(
            entryPoint,
            reachableMethods,
            diagnostics,
            List.of(),
            FunctionValueFlow.Result.unavailable(),
            InstantiatedTypeAnalysis.Result.unavailable()
        );
    }

    /**
     * Backward-compatible constructor for call sites that assemble caller edges without function-flow analysis.
     *
     * @param entryPoint entry point method
     * @param reachableMethods reachable closed-world methods
     * @param diagnostics reachability diagnostics
     * @param callEdges reachable caller-to-callee edges
     */
    public CallGraph(
        final EntryPoint entryPoint,
        final List<EntryPoint> reachableMethods,
        final List<Diagnostic> diagnostics,
        final List<CallEdge> callEdges
    ) {
        this(
            entryPoint,
            reachableMethods,
            diagnostics,
            callEdges,
            FunctionValueFlow.Result.unavailable(),
            InstantiatedTypeAnalysis.Result.unavailable()
        );
    }
}
