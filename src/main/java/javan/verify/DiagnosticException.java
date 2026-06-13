package javan.verify;

/**
 * Exception carrying a single fatal diagnostic.
 */
public final class DiagnosticException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Diagnostic diagnostic;

    /**
     * Creates a diagnostic exception.
     *
     * @param diagnostic fatal diagnostic
     */
    public DiagnosticException(final Diagnostic diagnostic) {
        super(diagnostic.format());
        this.diagnostic = diagnostic;
    }

    /**
     * Returns the fatal diagnostic.
     *
     * @return diagnostic
     */
    public Diagnostic diagnostic() {
        return diagnostic;
    }
}
