package javan.analysis;

import javan.verify.Diagnostic;

import java.util.List;

/**
 * Reachability result.
 *
 * @param entryPoint entry point method
 * @param reachableMethods reachable application methods
 * @param diagnostics reachability diagnostics
 */
public record CallGraph(EntryPoint entryPoint, List<EntryPoint> reachableMethods, List<Diagnostic> diagnostics) {
}
