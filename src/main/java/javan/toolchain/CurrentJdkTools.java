package javan.toolchain;

import java.nio.file.Path;

/**
 * Resolves tool executables from the current {@code java.home}.
 */
public final class CurrentJdkTools {
    private CurrentJdkTools() {
    }

    /**
     * Returns the current JDK {@code java} executable path.
     *
     * @return executable path
     */
    public static String java() {
        return tool("java");
    }

    /**
     * Returns the current JDK {@code javac} executable path.
     *
     * @return executable path
     */
    public static String javac() {
        return tool("javac");
    }

    /**
     * Returns the current JDK {@code jar} executable path.
     *
     * @return executable path
     */
    public static String jar() {
        return tool("jar");
    }

    /**
     * Returns the current JDK {@code jimage} executable path.
     *
     * @return executable path
     */
    public static String jimage() {
        return tool("jimage");
    }

    private static String tool(final String name) {
        final String executable = java.io.File.separatorChar == '\\'
            ? name + ".exe"
            : name;
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable).toString();
    }
}
