package javan.classfile;

import java.util.Optional;

/**
 * Structured invokedynamic bootstrap argument.
 *
 * @param kind bootstrap argument kind
 * @param value normalized textual value
 * @param methodRef referenced method when this is a method-handle argument
 * @param referenceKind JVM method-handle reference kind when present
 */
public record BootstrapValue(
    Kind kind,
    String value,
    Optional<MethodRef> methodRef,
    Optional<Integer> referenceKind
) {
    /**
     * Bootstrap argument kinds currently decoded by javan.
     */
    public enum Kind {
        STRING,
        UTF8,
        METHOD_TYPE,
        METHOD_HANDLE,
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        UNKNOWN
    }

    /**
     * Creates a non-method-handle bootstrap value.
     *
     * @param kind bootstrap argument kind
     * @param value normalized textual value
     */
    public BootstrapValue(final Kind kind, final String value) {
        this(kind, value, Optional.empty(), Optional.empty());
    }

    /**
     * Creates a method-handle bootstrap value.
     *
     * @param value normalized textual value
     * @param methodRef referenced method
     * @param referenceKind JVM method-handle reference kind
     */
    public static BootstrapValue methodHandle(
        final String value,
        final MethodRef methodRef,
        final int referenceKind
    ) {
        return new BootstrapValue(Kind.METHOD_HANDLE, value, Optional.of(methodRef), Optional.of(Integer.valueOf(referenceKind)));
    }
}
