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
     * Resolves a class literal entry.
     *
     * @param index constant pool index
     * @return class internal name when the index points at a class literal
     */
    public Optional<String> classLiteralName(final int index) {
        final Object entry = entries[index];
        if (entry instanceof ClassEntry) {
            return Optional.of(className(index));
        }
        return Optional.empty();
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
     * Reports whether a string literal encoded an embedded NUL that the current
     * native C-string ABI cannot preserve.
     *
     * @param index constant-pool index
     * @return whether the entry is a string value whose modified UTF-8 contains NUL
     */
    public boolean stringContainsNul(final int index) {
        final Object entry = entries[index];
        if (entry instanceof StringEntry stringEntry) {
            return utf8Entry(stringEntry.stringIndex())
                .map(Utf8Entry::containsNul)
                .orElse(false);
        }
        return entry instanceof Utf8Entry utf8Entry && utf8Entry.containsNul();
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
     * Resolves the raw constant-pool tag at the given index.
     *
     * @param index constant pool index
     * @return constant-pool tag when the entry exists
     */
    public Optional<Integer> entryTag(final int index) {
        final Object entry = entries[index];
        if (entry instanceof Utf8Entry) {
            return Optional.of(1);
        }
        if (entry instanceof RawEntry rawEntry) {
            return Optional.of(rawEntry.tag());
        }
        if (entry instanceof ClassEntry) {
            return Optional.of(7);
        }
        if (entry instanceof StringEntry) {
            return Optional.of(8);
        }
        if (entry instanceof RefEntry refEntry) {
            return Optional.of(refEntry.tag());
        }
        if (entry instanceof NameAndTypeEntry) {
            return Optional.of(12);
        }
        if (entry instanceof MethodHandleEntry) {
            return Optional.of(15);
        }
        if (entry instanceof MethodTypeEntry) {
            return Optional.of(16);
        }
        if (entry instanceof DynamicEntry dynamicEntry) {
            return Optional.of(dynamicEntry.tag());
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
        final Object entry = entry(index);
        if (!(entry instanceof DynamicEntry dynamic) || dynamic.tag() != 18) {
            return Optional.empty();
        }
        if (dynamic.bootstrapMethodAttributeIndex() < 0 || dynamic.bootstrapMethodAttributeIndex() >= bootstrapMethods.size()) {
            return Optional.empty();
        }
        final Object nameAndTypeEntry = entry(dynamic.nameAndTypeIndex());
        if (!(nameAndTypeEntry instanceof NameAndTypeEntry nameAndType)) {
            return Optional.empty();
        }
        final Optional<String> dynamicName = utf8Value(nameAndType.nameIndex());
        final Optional<String> dynamicDescriptor = utf8Value(nameAndType.descriptorIndex());
        if (dynamicName.isEmpty() || dynamicDescriptor.isEmpty()) {
            return Optional.empty();
        }
        final BootstrapMethod bootstrapMethod = bootstrapMethods.get(dynamic.bootstrapMethodAttributeIndex());
        final Object handleEntry = entry(bootstrapMethod.methodHandleIndex());
        if (!(handleEntry instanceof MethodHandleEntry handle)) {
            return Optional.empty();
        }
        final Optional<MethodRef> maybeBootstrap = methodHandleReference(handle);
        if (maybeBootstrap.isEmpty()) {
            return Optional.empty();
        }
        final MethodRef bootstrap = maybeBootstrap.orElseThrow();
        final List<BootstrapArgument> bootstrapArgumentDetails = bootstrapArgumentDetails(bootstrapMethod.argumentIndexes());
        final java.util.ArrayList<String> bootstrapArguments = new java.util.ArrayList<>();
        for (final BootstrapArgument bootstrapArgument : bootstrapArgumentDetails) {
            bootstrapArguments.add(bootstrapArgument.text());
        }
        return Optional.of(new DynamicRef(
            dynamicName.orElseThrow(),
            dynamicDescriptor.orElseThrow(),
            bootstrap.owner(),
            bootstrap.name(),
            bootstrap.descriptor(),
            handle.referenceKind(),
            List.copyOf(bootstrapArguments),
            bootstrapArgumentDetails
        ));
    }

    private List<BootstrapArgument> bootstrapArgumentDetails(final List<Integer> argumentIndexes) {
        final java.util.ArrayList<BootstrapArgument> result = new java.util.ArrayList<>();
        for (final Integer argumentIndex : argumentIndexes) {
            result.add(bootstrapArgument(argumentIndex.intValue()));
        }
        return List.copyOf(result);
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
     * @param containsNul whether the raw classfile bytes encoded an embedded NUL
     */
    public record Utf8Entry(String value, boolean containsNul) {
        /**
         * Creates an entry without embedded-NUL metadata.
         *
         * @param value value
         */
        public Utf8Entry(final String value) {
            this(value, false);
        }
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

    private BootstrapArgument bootstrapArgument(final int index) {
        final Object entry = entry(index);
        if (entry instanceof StringEntry stringEntry) {
            final Optional<Utf8Entry> value = utf8Entry(stringEntry.stringIndex());
            if (value.isEmpty()) {
                return BootstrapArgument.unknown("");
            }
            final Utf8Entry resolved = value.orElseThrow();
            return BootstrapArgument.string(resolved.value(), resolved.containsNul());
        }
        if (entry instanceof Utf8Entry utf8Entry) {
            return BootstrapArgument.utf8(utf8Entry.value());
        }
        if (entry instanceof ClassEntry classEntry) {
            final Optional<String> value = utf8Value(classEntry.nameIndex());
            return value.isPresent()
                ? BootstrapArgument.classLiteral(value.orElseThrow())
                : BootstrapArgument.unknown("");
        }
        if (entry instanceof MethodTypeEntry methodType) {
            final Optional<String> value = utf8Value(methodType.descriptorIndex());
            return value.isPresent()
                ? BootstrapArgument.methodType(value.orElseThrow())
                : BootstrapArgument.unknown("");
        }
        if (entry instanceof MethodHandleEntry handle) {
            final Optional<MethodRef> reference = methodHandleReference(handle);
            return reference.isPresent()
                ? BootstrapArgument.methodHandle(handle.referenceKind(), reference.orElseThrow())
                : BootstrapArgument.unknown("");
        }
        if (entry instanceof RawEntry rawEntry) {
            return rawBootstrapArgument(rawEntry.value());
        }
        return BootstrapArgument.unknown("");
    }

    private Optional<MethodRef> methodHandleReference(final MethodHandleEntry handle) {
        final Object referenceEntry = entry(handle.referenceIndex());
        if (!(referenceEntry instanceof RefEntry reference)
            || !referenceKindMatchesTag(handle.referenceKind(), reference.tag())) {
            return Optional.empty();
        }
        final Object ownerEntry = entry(reference.classIndex());
        final Object nameAndTypeEntry = entry(reference.nameAndTypeIndex());
        if (!(ownerEntry instanceof ClassEntry owner)
            || !(nameAndTypeEntry instanceof NameAndTypeEntry nameAndType)) {
            return Optional.empty();
        }
        final Optional<String> ownerName = utf8Value(owner.nameIndex());
        final Optional<String> memberName = utf8Value(nameAndType.nameIndex());
        final Optional<String> descriptor = utf8Value(nameAndType.descriptorIndex());
        if (ownerName.isEmpty() || memberName.isEmpty() || descriptor.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MethodRef(
            ownerName.orElseThrow(),
            memberName.orElseThrow(),
            descriptor.orElseThrow()
        ));
    }

    private static boolean referenceKindMatchesTag(final int referenceKind, final int referenceTag) {
        if (referenceKind >= 1 && referenceKind <= 4 && referenceTag == 9) {
            return true;
        }
        if ((referenceKind == 5 || referenceKind == 8) && referenceTag == 10) {
            return true;
        }
        if ((referenceKind == 6 || referenceKind == 7)
            && (referenceTag == 10 || referenceTag == 11)) {
            return true;
        }
        if (referenceKind == 9 && referenceTag == 11) {
            return true;
        }
        return false;
    }

    private Optional<String> utf8Value(final int index) {
        final Optional<Utf8Entry> entry = utf8Entry(index);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entry.orElseThrow().value());
    }

    private Optional<Utf8Entry> utf8Entry(final int index) {
        final Object entry = entry(index);
        return entry instanceof Utf8Entry utf8Entry ? Optional.of(utf8Entry) : Optional.empty();
    }

    private Object entry(final int index) {
        return index > 0 && index < entries.length ? entries[index] : null;
    }

    private static BootstrapArgument rawBootstrapArgument(final Object value) {
        if (value instanceof Integer integer) {
            return BootstrapArgument.raw(BootstrapArgument.Kind.INT, Integer.toString(integer.intValue()));
        }
        if (value instanceof Long longValue) {
            return BootstrapArgument.raw(BootstrapArgument.Kind.LONG, Long.toString(longValue.longValue()));
        }
        if (value instanceof Float floatValue) {
            return BootstrapArgument.raw(BootstrapArgument.Kind.FLOAT, Float.toString(floatValue.floatValue()));
        }
        if (value instanceof Double doubleValue) {
            return BootstrapArgument.raw(BootstrapArgument.Kind.DOUBLE, Double.toString(doubleValue.doubleValue()));
        }
        return BootstrapArgument.unknown("");
    }
}
