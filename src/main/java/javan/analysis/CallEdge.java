package javan.analysis;

/**
 * Reachable caller-to-callee relationship inside the closed-world graph.
 *
 * @param caller reachable caller
 * @param callee reachable callee
 * @param kind edge kind
 */
public record CallEdge(EntryPoint caller, EntryPoint callee, Kind kind) {
    /**
     * Backward-compatible constructor for direct same-thread calls.
     *
     * @param caller reachable caller
     * @param callee reachable callee
     */
    public CallEdge(final EntryPoint caller, final EntryPoint callee) {
        this(caller, callee, Kind.CALL);
    }

    /**
     * Reachable edge kind.
     */
    public enum Kind {
        CALL,
        CLASS_INITIALIZER,
        THREAD_START_TASK
    }
}
