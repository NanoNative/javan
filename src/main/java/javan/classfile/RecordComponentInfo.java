package javan.classfile;

import java.util.Optional;

/**
 * Parsed component entry from a classfile {@code Record} attribute.
 *
 * @param name component name
 * @param descriptor JVM field descriptor
 * @param signature optional generic component signature
 */
public record RecordComponentInfo(String name, String descriptor, Optional<String> signature) {
    public RecordComponentInfo {
        if (signature == null) {
            throw new IllegalArgumentException("record component signature optional is null");
        }
    }

    /**
     * Creates component metadata without a generic signature.
     *
     * @param name component name
     * @param descriptor JVM field descriptor
     */
    public RecordComponentInfo(final String name, final String descriptor) {
        this(name, descriptor, Optional.empty());
    }
}
