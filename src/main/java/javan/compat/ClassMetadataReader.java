package javan.compat;

import javan.classfile.ClassByteCursor;
import javan.util.Strings2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads classfiles for compatibility inventory without assuming native support.
 */
public final class ClassMetadataReader {
    private static final int MAGIC = 0xCAFEBABE;

    /**
     * Reads class metadata.
     *
     * @param input classfile input
     * @param source source path
     * @return metadata
     * @throws IOException when parsing fails
     */
    public ClassMetadata read(final InputStream input, final Path source) throws IOException {
        return read(input.readAllBytes(), source);
    }

    /**
     * Reads class metadata.
     *
     * @param bytes classfile bytes
     * @param source source path
     * @return metadata
     * @throws IOException when parsing fails
     */
    public ClassMetadata read(final byte[] bytes, final Path source) throws IOException {
        final ClassByteCursor in = new ClassByteCursor(bytes);
        if (in.i4() != MAGIC) {
            throw new IOException("Not a Java class file: " + source.toString());
        }
        final int minor = in.u2();
        final int major = in.u2();
        final Pool pool = readConstantPool(in);
        final int accessFlags = in.u2();
        final String thisClass = pool.className(in.u2());
        final int superIndex = in.u2();
        final String superClass = superIndex == 0 ? "" : pool.className(superIndex);
        final List<String> interfaces = readInterfaces(in, pool);
        final List<MemberMetadata> fields = readMembers(in, pool, false);
        final List<MemberMetadata> rawMethods = readMembers(in, pool, true);
        final List<String> classAttributes = new ArrayList<>();
        final List<String> bootstrapMethods = new ArrayList<>();
        final int attributes = in.u2();
        for (int index = 0; index < attributes; index++) {
            final String name = pool.utf8(in.u2());
            classAttributes.add(name);
            final long length = in.u4();
            if ("BootstrapMethods".equals(name)) {
                bootstrapMethods.addAll(readBootstrapMethods(in, pool));
            } else {
                in.skip(length);
            }
        }
        return new ClassMetadata(
            source,
            true,
            "",
            minor,
            major,
            accessFlags,
            thisClass,
            superClass,
            List.copyOf(interfaces),
            sortedInts(pool.tags()),
            sorted(classAttributes),
            sorted(bootstrapMethods),
            List.copyOf(fields),
            constructors(rawMethods),
            methods(rawMethods)
        );
    }

    private static Pool readConstantPool(final ClassByteCursor in) throws IOException {
        final int count = in.u2();
        final Object[] entries = new Object[count];
        final int[] entryTags = new int[count];
        final List<Integer> tags = new ArrayList<>();
        for (int index = 1; index < count; index++) {
            final int tag = in.u1();
            entryTags[index] = tag;
            tags.add(tag);
            switch (tag) {
                case 1 -> entries[index] = new Utf8Entry(in.modifiedUtf8());
                case 3 -> entries[index] = new RawEntry(tag, in.i4());
                case 4 -> entries[index] = new RawEntry(tag, in.f4());
                case 5 -> {
                    entries[index] = new RawEntry(tag, in.i8());
                    index++;
                }
                case 6 -> {
                    entries[index] = new RawEntry(tag, in.f8());
                    index++;
                }
                case 7 -> entries[index] = new ClassEntry(in.u2());
                case 8 -> entries[index] = new StringEntry(in.u2());
                case 9, 10, 11 -> entries[index] = new RefEntry(tag, in.u2(), in.u2());
                case 12 -> entries[index] = new NameAndTypeEntry(in.u2(), in.u2());
                case 15 -> entries[index] = new MethodHandleEntry(in.u1(), in.u2());
                case 16 -> entries[index] = new MethodTypeEntry(in.u2());
                case 17, 18 -> entries[index] = new DynamicEntry(tag, in.u2(), in.u2());
                case 19, 20 -> entries[index] = new RawEntry(tag, in.u2());
                default -> throw new IOException("Unsupported constant pool tag " + tag);
            }
        }
        return new Pool(entries, entryTags, List.copyOf(tags));
    }

    private static List<String> readInterfaces(final ClassByteCursor in, final Pool pool) throws IOException {
        final int count = in.u2();
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(pool.className(in.u2()));
        }
        return result;
    }

    private static List<MemberMetadata> readMembers(
        final ClassByteCursor in,
        final Pool pool,
        final boolean methods
    ) throws IOException {
        final int count = in.u2();
        final List<MemberMetadata> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int flags = in.u2();
            final String name = pool.utf8(in.u2());
            final String descriptor = pool.utf8(in.u2());
            final List<String> attributes = new ArrayList<>();
            final List<InstructionMetadata> instructions = new ArrayList<>();
            final int attributeCount = in.u2();
            for (int attribute = 0; attribute < attributeCount; attribute++) {
                final String attributeName = pool.utf8(in.u2());
                attributes.add(attributeName);
                final long length = in.u4();
                if (methods && "Code".equals(attributeName)) {
                    instructions.addAll(readCode(in, pool));
                } else {
                    in.skip(length);
                }
            }
            result.add(new MemberMetadata(flags, name, descriptor, sorted(attributes), List.copyOf(instructions)));
        }
        return result;
    }

    private static List<InstructionMetadata> readCode(final ClassByteCursor in, final Pool pool) throws IOException {
        in.u2();
        in.u2();
        final long codeLength = in.u4();
        final byte[] bytecode = in.bytes(codeLength);
        final int exceptionTableLength = in.u2();
        in.skip(exceptionTableLength * 8L);
        skipNestedAttributes(in, pool);
        return decode(bytecode);
    }

    private static void skipNestedAttributes(final ClassByteCursor in, final Pool pool) throws IOException {
        final int count = in.u2();
        for (int index = 0; index < count; index++) {
            pool.utf8(in.u2());
            in.skip(in.u4());
        }
    }

    private static List<String> readBootstrapMethods(final ClassByteCursor in, final Pool pool) throws IOException {
        final int count = in.u2();
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String method = pool.methodHandle(in.u2()).orElse("methodHandle#unknown");
            final int argumentCount = in.u2();
            final List<String> arguments = new ArrayList<>();
            for (int argument = 0; argument < argumentCount; argument++) {
                arguments.add(pool.describe(in.u2()));
            }
            result.add(method + " args=" + describeList(arguments));
        }
        return result;
    }

    private static String describeList(final List<String> values) {
        final StringBuilder result = new StringBuilder();
        result.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index));
        }
        result.append(']');
        return result.toString();
    }

    private static List<InstructionMetadata> decode(final byte[] bytecode) throws IOException {
        final List<InstructionMetadata> result = new ArrayList<>();
        int offset = 0;
        while (offset < bytecode.length) {
            final int opcode = unsigned(bytecode[offset]);
            final int length = instructionLength(bytecode, offset);
            result.add(new InstructionMetadata(
                offset,
                opcode,
                BytecodeSupport.mnemonic(opcode),
                Math.max(0, length - 1),
                BytecodeSupport.classify(opcode)
            ));
            offset += length;
        }
        return List.copyOf(result);
    }

    private static int instructionLength(final byte[] bytecode, final int offset) throws IOException {
        final int opcode = unsigned(bytecode[offset]);
        return switch (opcode) {
            case 16, 18, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58, 169, 188 -> 2;
            case 17, 19, 20, 132, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168,
                 178, 179, 180, 181, 182, 183, 184, 187, 189, 192, 193, 198, 199 -> 3;
            case 185, 186, 200, 201 -> 5;
            case 196 -> wideLength(bytecode, offset);
            case 197 -> 4;
            case 170 -> tableSwitchLength(bytecode, offset);
            case 171 -> lookupSwitchLength(bytecode, offset);
            default -> 1;
        };
    }

    private static int wideLength(final byte[] bytecode, final int offset) throws IOException {
        if (offset + 1 >= bytecode.length) {
            throw new IOException("Invalid wide instruction at " + offset);
        }
        final int widened = unsigned(bytecode[offset + 1]);
        return widened == 132 ? 6 : 4;
    }

    private static int tableSwitchLength(final byte[] bytecode, final int offset) throws IOException {
        final int aligned = alignedSwitchOffset(offset);
        if (aligned + 12 > bytecode.length) {
            throw new IOException("Invalid tableswitch at " + offset);
        }
        final int low = int32(bytecode, aligned + 4);
        final int high = int32(bytecode, aligned + 8);
        return aligned - offset + 12 + ((high - low + 1) * 4);
    }

    private static int lookupSwitchLength(final byte[] bytecode, final int offset) throws IOException {
        final int aligned = alignedSwitchOffset(offset);
        if (aligned + 8 > bytecode.length) {
            throw new IOException("Invalid lookupswitch at " + offset);
        }
        final int pairs = int32(bytecode, aligned + 4);
        return aligned - offset + 8 + (pairs * 8);
    }

    private static int alignedSwitchOffset(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor;
    }

    private static int int32(final byte[] values, final int offset) {
        return (unsigned(values[offset]) << 24)
            | (unsigned(values[offset + 1]) << 16)
            | (unsigned(values[offset + 2]) << 8)
            | unsigned(values[offset + 3]);
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static List<String> sorted(final List<String> values) {
        final List<String> result = new ArrayList<>();
        for (final String value : values) {
            insertString(result, value);
        }
        return List.copyOf(result);
    }

    private static List<Integer> sortedInts(final List<Integer> values) {
        final List<Integer> result = new ArrayList<>();
        for (final Integer value : values) {
            insertInt(result, value.intValue());
        }
        return List.copyOf(result);
    }

    private static List<MemberMetadata> constructors(final List<MemberMetadata> rawMethods) {
        final List<MemberMetadata> result = new ArrayList<>();
        for (final MemberMetadata member : rawMethods) {
            if ("<init>".equals(member.name())) {
                result.add(member);
            }
        }
        return List.copyOf(result);
    }

    private static List<MemberMetadata> methods(final List<MemberMetadata> rawMethods) {
        final List<MemberMetadata> result = new ArrayList<>();
        for (final MemberMetadata member : rawMethods) {
            if (!"<init>".equals(member.name())) {
                result.add(member);
            }
        }
        return List.copyOf(result);
    }

    private static void insertString(final List<String> values, final String value) {
        int index = 0;
        while (index < values.size()) {
            final int comparison = Strings2.compareAscii(values.get(index), value);
            if (comparison == 0) {
                return;
            }
            if (comparison > 0) {
                break;
            }
            index++;
        }
        values.add(index, value);
    }

    private static void insertInt(final List<Integer> values, final int value) {
        int index = 0;
        while (index < values.size()) {
            final int current = values.get(index).intValue();
            if (current == value) {
                return;
            }
            if (current > value) {
                break;
            }
            index++;
        }
        values.add(index, Integer.valueOf(value));
    }

    private record Pool(Object[] entries, int[] entryTags, List<Integer> tags) {
        String utf8(final int index) {
            return ((Utf8Entry) entries[index]).value();
        }

        String className(final int index) {
            return utf8(((ClassEntry) entries[index]).nameIndex());
        }

        String describe(final int index) {
            return switch (entryTags[index]) {
                case 1 -> "Utf8:" + ((Utf8Entry) entries[index]).value();
                case 7 -> "Class:" + utf8(((ClassEntry) entries[index]).nameIndex());
                case 8 -> "String:" + utf8(((StringEntry) entries[index]).stringIndex());
                case 9, 10, 11 -> refName((RefEntry) entries[index]);
                case 15 -> methodHandleDescription((MethodHandleEntry) entries[index]);
                case 16 -> "MethodType:" + utf8(((MethodTypeEntry) entries[index]).descriptorIndex());
                case 17, 18 -> "Dynamic:bootstrap=" + ((DynamicEntry) entries[index]).bootstrapMethodAttributeIndex();
                case 3, 4, 5, 6, 19, 20 -> "tag" + ((RawEntry) entries[index]).tag();
                default -> "unknown";
            };
        }

        Optional<String> methodHandle(final int index) {
            if (entryTags[index] == 15) {
                return Optional.of(methodHandleDescription((MethodHandleEntry) entries[index]));
            }
            return Optional.empty();
        }

        private String methodHandleDescription(final MethodHandleEntry handle) {
            return "refKind=" + handle.referenceKind() + " " + describe(handle.referenceIndex());
        }

        private String refName(final RefEntry ref) {
            final NameAndTypeEntry nameAndType = (NameAndTypeEntry) entries[ref.nameAndTypeIndex()];
            return "Ref:" + className(ref.classIndex()) + "." + utf8(nameAndType.nameIndex()) + utf8(nameAndType.descriptorIndex());
        }
    }

    private record Utf8Entry(String value) {
    }

    private record ClassEntry(int nameIndex) {
    }

    private record StringEntry(int stringIndex) {
    }

    private record NameAndTypeEntry(int nameIndex, int descriptorIndex) {
    }

    private record RefEntry(int tag, int classIndex, int nameAndTypeIndex) {
    }

    private record MethodHandleEntry(int referenceKind, int referenceIndex) {
    }

    private record MethodTypeEntry(int descriptorIndex) {
    }

    private record DynamicEntry(int tag, int bootstrapMethodAttributeIndex, int nameAndTypeIndex) {
    }

    private record RawEntry(int tag, Object value) {
    }
}
