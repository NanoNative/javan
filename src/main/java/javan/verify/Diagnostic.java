package javan.verify;

/**
 * Human-readable diagnostic for static verification and build failures.
 *
 * @param error whether the diagnostic is fatal
 * @param code stable diagnostic code
 * @param message short message
 * @param className class context
 * @param methodName method context
 * @param subject offending bytecode/API/item
 * @param reason explanation
 * @param fix suggested fix
 */
public record Diagnostic(
    boolean error,
    String code,
    String message,
    String className,
    String methodName,
    String subject,
    String reason,
    String fix
) {
    /**
     * Creates a fatal diagnostic.
     *
     * @param code diagnostic code
     * @param message short message
     * @param className class context
     * @param methodName method context
     * @param subject offending subject
     * @param reason explanation
     * @param fix suggested fix
     * @return diagnostic
     */
    public static Diagnostic error(
        final String code,
        final String message,
        final String className,
        final String methodName,
        final String subject,
        final String reason,
        final String fix
    ) {
        return new Diagnostic(true, code, message, className, methodName, subject, reason, fix);
    }

    /**
     * Creates a warning diagnostic.
     *
     * @param code diagnostic code
     * @param message short message
     * @param className class context
     * @param methodName method context
     * @param subject warning subject
     * @param reason explanation
     * @param fix suggested fix
     * @return diagnostic
     */
    public static Diagnostic warning(
        final String code,
        final String message,
        final String className,
        final String methodName,
        final String subject,
        final String reason,
        final String fix
    ) {
        return new Diagnostic(false, code, message, className, methodName, subject, reason, fix);
    }

    /**
     * Formats the diagnostic for humans.
     *
     * @return formatted diagnostic
     */
    public String format() {
        final String severity = error ? "error" : "warning";
        return severity + "[" + code + "]: " + message + System.lineSeparator()
            + "Class:" + System.lineSeparator()
            + "  " + emptyDash(className) + System.lineSeparator()
            + "Method:" + System.lineSeparator()
            + "  " + emptyDash(methodName) + System.lineSeparator()
            + "Subject:" + System.lineSeparator()
            + "  " + emptyDash(subject) + System.lineSeparator()
            + "Reason:" + System.lineSeparator()
            + "  " + emptyDash(reason) + System.lineSeparator()
            + "Fix:" + System.lineSeparator()
            + "  " + emptyDash(fix);
    }

    private static String emptyDash(final String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
