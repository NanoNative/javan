package javan.toolchain;

/**
 * Signals invalid or unreadable toolchain metadata.
 */
public final class ToolchainMetadataException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param message problem summary
     * @param cause root cause
     */
    public ToolchainMetadataException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
