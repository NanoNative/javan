package javan.analysis;

import javan.verify.Diagnostic;

import java.util.List;
import java.util.Set;

/**
 * Reachability result.
 *
 * @param entryPoint entry point method
 * @param reachableMethods reachable application methods
 * @param diagnostics reachability diagnostics
 */
public record CallGraph(EntryPoint entryPoint, Set<EntryPoint> reachableMethods, List<Diagnostic> diagnostics) {
}
