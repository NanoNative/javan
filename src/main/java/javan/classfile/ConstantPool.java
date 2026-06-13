package javan.classfile;

import java.util.Optional;
import java.util.List;

/**
 * Parsed constant pool with typed resolution helpers.
 */
public final class ConstantPool {
    private final Object[] entries;

    /**
     * Creates a constant pool.
     *
     * @param entries raw entries indexed from one
     */
    public ConstantPool(final Object[] entries) {
        this.entries = entries.clone();
    }

    /**
     * Resolves a UTF-8 entry.
     *
     * @param index constant pool index
     * @return UTF-8 value
     */
    public String utf8(final int index) {
        return ((Utf8Entry) entries[index]).value();
    }

    /**
     * Resolves a class internal name.
     *
     * @param index constant pool index
     * @return JVM internal class name
     */
    public String className(final int index) {
        return utf8(((ClassEntry) entries[index]).nameIndex());
    }

    /**
     * Resolves a string literal.
     *
     * @param index constant pool index
     * @return string literal when the index points at a string
     */
    public Optional<String> string(final int index) {
        final Object entry = entries[index];
        if (entry instanceof StringEntry stringEntry) {
            return Optional.of(utf8(stringEntry.stringIndex()));
        }
        if (entry instanceof Utf8Entry utf8Entry) {
            return Optional.of(utf8Entry.value());
        }
        return Optional.empty();
    }

    /**
     * Resolves an int literal.
     *
     * @param index constant pool index
     * @return int literal when the index points at one
     */
    public Optional<Integer> intValue(final int index) {
        final Object entry = entries[index];
        if (entry instanceof RawEntry rawEntry && rawEntry.tag() == 3 && rawEntry.value() instanceof Integer value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * Resolves a long literal.
     *
     * @param index constant pool index
     * @return long literal when the index points at one
     */
    public Optional<Long> longValue(final int index) {
        final Object entry = entries[index];
        if (entry instanceof RawEntry rawEntry && rawEntry.tag() == 5 && rawEntry.value() instanceof Long value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * Resolves a float literal.
     *
     * @param index constant pool index
     * @return float literal when the index points at one
     */
    public Optional<Float> floatValue(final int index) {
        final Object entry = entries[index];
        if (entry instanceof RawEntry rawEntry && rawEntry.tag() == 4 && rawEntry.value() instanceof Float value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * Resolves a double literal.
     *
     * @param index constant pool index
     * @return double literal when the index points at one
     */
    public Optional<Double> doubleValue(final int index) {
        final Object entry = entries[index];
        if (entry instanceof RawEntry rawEntry && rawEntry.tag() == 6 && rawEntry.value() instanceof Double value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * Resolves a method reference.
     *
     * @param index constant pool index
     * @return method reference
     */
    public MethodRef methodRef(final int index) {
        final RefEntry ref = (RefEntry) entries[index];
        final NameAndTypeEntry nameAndType = (NameAndTypeEntry) entries[ref.nameAndTypeIndex()];
        return new MethodRef(className(ref.classIndex()), utf8(nameAndType.nameIndex()), utf8(nameAndType.descriptorIndex()));
    }

    /**
     * Resolves an invokedynamic reference.
     *
     * @param index constant pool index
     * @param bootstrapMethods class bootstrap methods
     * @return dynamic reference when resolvable
     */
    public Optional<DynamicRef> dynamicRef(final int index, final List<BootstrapMethod> bootstrapMethods) {
        final Object entry = entries[index];
        if (!(entry instanceof DynamicEntry dynamic) || dynamic.tag() != 18) {
            return Optional.empty();
        }
        if (dynamic.bootstrapMethodAttributeIndex() < 0 || dynamic.bootstrapMethodAttributeIndex() >= bootstrapMethods.size()) {
            return Optional.empty();
        }
        final NameAndTypeEntry nameAndType = (NameAndTypeEntry) entries[dynamic.nameAndTypeIndex()];
        final BootstrapMethod bootstrapMethod = bootstrapMethods.get(dynamic.bootstrapMethodAttributeIndex());
        final Object handleEntry = entries[bootstrapMethod.methodHandleIndex()];
        if (!(handleEntry instanceof MethodHandleEntry handle)) {
            return Optional.empty();
        }
        final MethodRef bootstrap = methodRef(handle.referenceIndex());
        return Optional.of(new DynamicRef(
            utf8(nameAndType.nameIndex()),
            utf8(nameAndType.descriptorIndex()),
            bootstrap.owner(),
            bootstrap.name(),
            bootstrap.descriptor(),
            bootstrapMethod.argumentIndexes().stream().map(this::bootstrapArgument).toList()
        ));
    }

    /**
     * Resolves a field reference.
     *
     * @param index constant pool index
     * @return field reference
     */
    public FieldRef fieldRef(final int index) {
        final RefEntry ref = (RefEntry) entries[index];
        final NameAndTypeEntry nameAndType = (NameAndTypeEntry) entries[ref.nameAndTypeIndex()];
        return new FieldRef(className(ref.classIndex()), utf8(nameAndType.nameIndex()), utf8(nameAndType.descriptorIndex()));
    }

    /**
     * UTF-8 constant pool entry.
     *
     * @param value value
     */
    public record Utf8Entry(String value) {
    }

    /**
     * Class constant pool entry.
     *
     * @param nameIndex class name index
     */
    public record ClassEntry(int nameIndex) {
    }

    /**
     * String constant pool entry.
     *
     * @param stringIndex UTF-8 string index
     */
    public record StringEntry(int stringIndex) {
    }

    /**
     * Name-and-type constant pool entry.
     *
     * @param nameIndex name index
     * @param descriptorIndex descriptor index
     */
    public record NameAndTypeEntry(int nameIndex, int descriptorIndex) {
    }

    /**
     * Field, method, or interface method reference entry.
     *
     * @param tag constant tag
     * @param classIndex class index
     * @param nameAndTypeIndex name-and-type index
     */
    public record RefEntry(int tag, int classIndex, int nameAndTypeIndex) {
    }

    /**
     * Method-handle constant pool entry.
     *
     * @param referenceKind JVM method handle kind
     * @param referenceIndex referenced method or field index
     */
    public record MethodHandleEntry(int referenceKind, int referenceIndex) {
    }

    /**
     * Method-type constant pool entry.
     *
     * @param descriptorIndex descriptor UTF-8 index
     */
    public record MethodTypeEntry(int descriptorIndex) {
    }

    /**
     * Dynamic or invokedynamic constant pool entry.
     *
     * @param tag constant tag
     * @param bootstrapMethodAttributeIndex bootstrap method index
     * @param nameAndTypeIndex name-and-type index
     */
    public record DynamicEntry(int tag, int bootstrapMethodAttributeIndex, int nameAndTypeIndex) {
    }

    /**
     * Unused or unsupported entry placeholder.
     *
     * @param tag constant tag
     * @param value raw value
     */
    public record RawEntry(int tag, Object value) {
    }

    private String bootstrapArgument(final int index) {
        final Object entry = entries[index];
        if (entry instanceof StringEntry stringEntry) {
            return utf8(stringEntry.stringIndex());
        }
        if (entry instanceof Utf8Entry utf8Entry) {
            return utf8Entry.value();
        }
        if (entry instanceof MethodTypeEntry methodType) {
            return utf8(methodType.descriptorIndex());
        }
        if (entry instanceof RawEntry rawEntry) {
            return String.valueOf(rawEntry.value());
        }
        return "";
    }
}
