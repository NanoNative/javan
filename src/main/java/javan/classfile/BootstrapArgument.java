package javan.classfile;

import java.util.Optional;

/**
 * Resolved invokedynamic bootstrap argument metadata.
 *
 * @param kind normalized bootstrap argument kind
 * @param text legacy text form used by existing string-based consumers
 * @param methodRef resolved method reference when the argument is a method handle
 * @param referenceKind JVM method-handle reference kind, or {@code -1} when not applicable
 * @param containsNul whether the raw classfile argument encoded an embedded NUL
 */
public record BootstrapArgument(
    Kind kind,
    String text,
    Optional<MethodRef> methodRef,
    int referenceKind,
    boolean containsNul
) {
    /**
     * Creates an argument without embedded-NUL metadata.
     *
     * @param kind normalized bootstrap argument kind
     * @param text legacy text form
     * @param methodRef resolved method reference
     * @param referenceKind JVM method-handle reference kind
     */
    public BootstrapArgument(
        final Kind kind,
        final String text,
        final Optional<MethodRef> methodRef,
        final int referenceKind
    ) {
        this(kind, text, methodRef, referenceKind, false);
    }

    /**
     * Bootstrap argument kind.
     */
    public enum Kind {
        CLASS,
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
     * Class-literal bootstrap argument.
     *
     * @param internalName JVM internal class name
     * @return bootstrap argument
     */
    public static BootstrapArgument classLiteral(final String internalName) {
        return new BootstrapArgument(Kind.CLASS, internalName, Optional.empty(), -1, false);
    }

    /**
     * String literal bootstrap argument.
     *
     * @param text argument text
     * @return bootstrap argument
     */
    public static BootstrapArgument string(final String text) {
        return string(text, false);
    }

    /**
     * String literal bootstrap argument with raw embedded-NUL metadata.
     *
     * @param text argument text
     * @param containsNul whether the raw classfile value encoded an embedded NUL
     * @return bootstrap argument
     */
    public static BootstrapArgument string(final String text, final boolean containsNul) {
        return new BootstrapArgument(Kind.STRING, text, Optional.empty(), -1, containsNul);
    }

    /**
     * UTF-8 bootstrap argument.
     *
     * @param text argument text
     * @return bootstrap argument
     */
    public static BootstrapArgument utf8(final String text) {
        return new BootstrapArgument(Kind.UTF8, text, Optional.empty(), -1, false);
    }

    /**
     * Method-type bootstrap argument.
     *
     * @param descriptor JVM descriptor text
     * @return bootstrap argument
     */
    public static BootstrapArgument methodType(final String descriptor) {
        return new BootstrapArgument(Kind.METHOD_TYPE, descriptor, Optional.empty(), -1, false);
    }

    /**
     * Method-handle bootstrap argument.
     *
     * @param referenceKind JVM reference kind
     * @param methodRef referenced method
     * @return bootstrap argument
     */
    public static BootstrapArgument methodHandle(final int referenceKind, final MethodRef methodRef) {
        return new BootstrapArgument(Kind.METHOD_HANDLE, methodRef.display(), Optional.of(methodRef), referenceKind, false);
    }

    /**
     * Primitive/raw bootstrap argument.
     *
     * @param kind primitive kind
     * @param text normalized text
     * @return bootstrap argument
     */
    public static BootstrapArgument raw(final Kind kind, final String text) {
        return new BootstrapArgument(kind, text, Optional.empty(), -1, false);
    }

    /**
     * Unknown bootstrap argument.
     *
     * @param text normalized text
     * @return bootstrap argument
     */
    public static BootstrapArgument unknown(final String text) {
        return new BootstrapArgument(Kind.UNKNOWN, text, Optional.empty(), -1, false);
    }
}
