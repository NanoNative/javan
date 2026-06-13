package javan.analysis;

/**
 * Reachable method identity.
 *
 * @param className JVM internal owner class
 * @param methodName method name
 * @param descriptor method descriptor
 */
public record EntryPoint(String className, String methodName, String descriptor) {
    /**
     * Formats the entry point for diagnostics.
     *
     * @return JVM display name
     */
    public String display() {
        return className + "." + methodName + descriptor;
    }
}
