package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
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
     * @throws IOException when no JDK tool can be resolved
     */
    public static String java() throws IOException {
        return tool("java");
    }

    /**
     * Returns the current JDK {@code javac} executable path.
     *
     * @return executable path
     * @throws IOException when no JDK tool can be resolved
     */
    public static String javac() throws IOException {
        return tool("javac");
    }

    /**
     * Returns the current JDK {@code jar} executable path.
     *
     * @return executable path
     * @throws IOException when no JDK tool can be resolved
     */
    public static String jar() throws IOException {
        return tool("jar");
    }

    /**
     * Returns the current JDK {@code jimage} executable path.
     *
     * @return executable path
     * @throws IOException when no JDK tool can be resolved
     */
    public static String jimage() throws IOException {
        return tool("jimage");
    }

    /**
     * Returns the current JDK home, or discovers one when Javan is native.
     *
     * @return verified JDK home
     * @throws IOException when no JDK home is available
     */
    public static Path home() throws IOException {
        final String javaHome = System.getProperty("java.home");
        if (!Strings2.isBlank(javaHome)) {
            return Path.of(javaHome);
        }
        return new ToolchainManager().requiredJdkHome();
    }

    private static String tool(final String name) throws IOException {
        final String javaHome = System.getProperty("java.home");
        if (Strings2.isBlank(javaHome)) {
            return new ToolchainManager().requiredJdkTool(name).toString();
        }
        final String executable = java.io.File.separatorChar == '\\'
            ? name + ".exe"
            : name;
        return Path.of(javaHome).resolve("bin").resolve(executable).toString();
    }
}
