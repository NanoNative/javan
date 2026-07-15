package javan.classfile;

import java.util.Optional;

/**
 * Resolved invokedynamic bootstrap argument metadata.
 *
 * @param kind normalized bootstrap argument kind
 * @param text legacy text form used by existing string-based consumers
 * @param methodRef resolved method reference when the argument is a method handle
 * @param referenceKind JVM method-handle reference kind, or {@code -1} when not applicable
 */
public record BootstrapArgument(Kind kind, String text, Optional<MethodRef> methodRef, int referenceKind) {
    /**
     * Bootstrap argument kind.
     */
    public enum Kind {
        STRING,
        UTF8,
        METHOD_TYPE,
        METHOD_HANDLE,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        UNKNOWN
    }

    /**
     * String literal bootstrap argument.
     *
     * @param text argument text
     * @return bootstrap argument
     */
    public static BootstrapArgument string(final String text) {
        return new BootstrapArgument(Kind.STRING, text, Optional.empty(), -1);
    }

    /**
     * UTF-8 bootstrap argument.
     *
     * @param text argument text
     * @return bootstrap argument
     */
    public static BootstrapArgument utf8(final String text) {
        return new BootstrapArgument(Kind.UTF8, text, Optional.empty(), -1);
    }

    /**
     * Method-type bootstrap argument.
     *
     * @param descriptor JVM descriptor text
     * @return bootstrap argument
     */
    public static BootstrapArgument methodType(final String descriptor) {
        return new BootstrapArgument(Kind.METHOD_TYPE, descriptor, Optional.empty(), -1);
    }

    /**
     * Method-handle bootstrap argument.
     *
     * @param referenceKind JVM reference kind
     * @param methodRef referenced method
     * @return bootstrap argument
     */
    public static BootstrapArgument methodHandle(final int referenceKind, final MethodRef methodRef) {
        return new BootstrapArgument(Kind.METHOD_HANDLE, methodRef.display(), Optional.of(methodRef), referenceKind);
    }

    /**
     * Primitive/raw bootstrap argument.
     *
     * @param kind primitive kind
     * @param text normalized text
     * @return bootstrap argument
     */
    public static BootstrapArgument raw(final Kind kind, final String text) {
        return new BootstrapArgument(kind, text, Optional.empty(), -1);
    }

    /**
     * Unknown bootstrap argument.
     *
     * @param text normalized text
     * @return bootstrap argument
     */
    public static BootstrapArgument unknown(final String text) {
        return new BootstrapArgument(Kind.UNKNOWN, text, Optional.empty(), -1);
    }
}
