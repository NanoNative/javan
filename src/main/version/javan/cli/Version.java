package javan.cli;

/**
 * Resolves the packaged javan version.
 */
public final class Version {
    private static final String VERSION = "${project.version}";

    private Version() {
    }

    /**
     * Returns the version number.
     *
     * @return version number
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
