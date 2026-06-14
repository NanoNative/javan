package javan.cli;

/**
 * Resolves the packaged javan version.
 */
public final class Version {
    private static final String VERSION = "2026.6.14";

    private Version() {
    }

    /**
     * Returns the version number.
     *
     * @return version number or a development fallback
     */
    public static String number() {
        return VERSION;
    }

    /**
     * Returns the human-readable CLI version line.
     *
     * @return version line
     */
    public static String full() {
        return "javan " + VERSION;
    }

}
