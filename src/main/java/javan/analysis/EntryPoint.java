package javan.analysis;

/**
 * Reachable method identity.
 *
 * @param className JVM internal owner class
 * @param methodName method name
 * @param descriptor method descriptor
 */
public record EntryPoint(String className, String methodName, String descriptor) {
    @Override
    public boolean equals(final Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof EntryPoint other)) {
            return false;
        }
        return componentEquals(className, other.className)
            && componentEquals(methodName, other.methodName)
            && componentEquals(descriptor, other.descriptor);
    }

    @Override
    public int hashCode() {
        int result = componentHashCode(className);
        result = (31 * result) + componentHashCode(methodName);
        return (31 * result) + componentHashCode(descriptor);
    }

    private static boolean componentEquals(final String left, final String right) {
        if (left == right) {
            return true;
        }
        return left != null && left.equals(right);
    }

    private static int componentHashCode(final String value) {
        return value == null ? 0 : value.hashCode();
    }

    /**
     * Formats the entry point for diagnostics.
     *
     * @return JVM display name
     */
    public String display() {
        return new StringBuilder()
            .append(className)
            .append('.')
            .append(methodName)
            .append(descriptor)
            .toString();
    }

    @Override
    public String toString() {
        return display();
    }
}
