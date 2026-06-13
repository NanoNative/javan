package javan.compat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

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
        final DataInputStream in = new DataInputStream(input);
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a Java class file: " + source);
        }
        final int minor = in.readUnsignedShort();
        final int major = in.readUnsignedShort();
        final Pool pool = readConstantPool(in);
        final int accessFlags = in.readUnsignedShort();
        final String thisClass = pool.className(in.readUnsignedShort());
        final int superIndex = in.readUnsignedShort();
        final String superClass = superIndex == 0 ? "" : pool.className(superIndex);
        final List<String> interfaces = readInterfaces(in, pool);
        final List<MemberMetadata> fields = readMembers(in, pool, false);
        final List<MemberMetadata> rawMethods = readMembers(in, pool, true);
        final List<String> classAttributes = new ArrayList<>();
        final List<String> bootstrapMethods = new ArrayList<>();
        final int attributes = in.readUnsignedShort();
        for (int index = 0; index < attributes; index++) {
            final String name = pool.utf8(in.readUnsignedShort());
            classAttributes.add(name);
            final int length = in.readInt();
            if ("BootstrapMethods".equals(name)) {
                bootstrapMethods.addAll(readBootstrapMethods(in, pool));
            } else {
                in.skipNBytes(length);
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
            List.copyOf(new TreeSet<>(pool.tags())),
            sorted(classAttributes),
            sorted(bootstrapMethods),
            List.copyOf(fields),
            rawMethods.stream().filter(member -> "<init>".equals(member.name())).toList(),
            rawMethods.stream().filter(member -> !"<init>".equals(member.name())).toList()
        );
    }

    private static Pool readConstantPool(final DataInputStream in) throws IOException {
        final int count = in.readUnsignedShort();
        final Object[] entries = new Object[count];
        final List<Integer> tags = new ArrayList<>();
        for (int index = 1; index < count; index++) {
            final int tag = in.readUnsignedByte();
            tags.add(tag);
            switch (tag) {
                case 1 -> entries[index] = new Utf8Entry(in.readUTF());
                case 3 -> entries[index] = new RawEntry(tag, in.readInt());
                case 4 -> entries[index] = new RawEntry(tag, in.readFloat());
                case 5 -> {
                    entries[index] = new RawEntry(tag, in.readLong());
                    index++;
                }
                case 6 -> {
                    entries[index] = new RawEntry(tag, in.readDouble());
                    index++;
                }
                case 7 -> entries[index] = new ClassEntry(in.readUnsignedShort());
                case 8 -> entries[index] = new StringEntry(in.readUnsignedShort());
                case 9, 10, 11 -> entries[index] = new RefEntry(tag, in.readUnsignedShort(), in.readUnsignedShort());
                case 12 -> entries[index] = new NameAndTypeEntry(in.readUnsignedShort(), in.readUnsignedShort());
                case 15 -> entries[index] = new MethodHandleEntry(in.readUnsignedByte(), in.readUnsignedShort());
                case 16 -> entries[index] = new MethodTypeEntry(in.readUnsignedShort());
                case 17, 18 -> entries[index] = new DynamicEntry(tag, in.readUnsignedShort(), in.readUnsignedShort());
                case 19, 20 -> entries[index] = new RawEntry(tag, in.readUnsignedShort());
                default -> throw new IOException("Unsupported constant pool tag " + tag);
            }
        }
        return new Pool(entries, List.copyOf(tags));
    }

    private static List<String> readInterfaces(final DataInputStream in, final Pool pool) throws IOException {
        final int count = in.readUnsignedShort();
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(pool.className(in.readUnsignedShort()));
        }
        return result;
    }

    private static List<MemberMetadata> readMembers(
        final DataInputStream in,
        final Pool pool,
        final boolean methods
    ) throws IOException {
        final int count = in.readUnsignedShort();
        final List<MemberMetadata> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int flags = in.readUnsignedShort();
            final String name = pool.utf8(in.readUnsignedShort());
            final String descriptor = pool.utf8(in.readUnsignedShort());
            final List<String> attributes = new ArrayList<>();
            final List<InstructionMetadata> instructions = new ArrayList<>();
            final int attributeCount = in.readUnsignedShort();
            for (int attribute = 0; attribute < attributeCount; attribute++) {
                final String attributeName = pool.utf8(in.readUnsignedShort());
                attributes.add(attributeName);
                final int length = in.readInt();
                if (methods && "Code".equals(attributeName)) {
                    instructions.addAll(readCode(in, pool));
                } else {
                    in.skipNBytes(length);
                }
            }
            result.add(new MemberMetadata(flags, name, descriptor, sorted(attributes), List.copyOf(instructions)));
        }
        return result;
    }

    private static List<InstructionMetadata> readCode(final DataInputStream in, final Pool pool) throws IOException {
        in.readUnsignedShort();
        in.readUnsignedShort();
        final int codeLength = in.readInt();
        final byte[] bytecode = in.readNBytes(codeLength);
        final int exceptionTableLength = in.readUnsignedShort();
        in.skipNBytes(exceptionTableLength * 8L);
        skipNestedAttributes(in, pool);
        return decode(bytecode);
    }

    private static void skipNestedAttributes(final DataInputStream in, final Pool pool) throws IOException {
        final int count = in.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            pool.utf8(in.readUnsignedShort());
            in.skipNBytes(in.readInt());
        }
    }

    private static List<String> readBootstrapMethods(final DataInputStream in, final Pool pool) throws IOException {
        final int count = in.readUnsignedShort();
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String method = pool.methodHandle(in.readUnsignedShort()).orElse("methodHandle#unknown");
            final int argumentCount = in.readUnsignedShort();
            final List<String> arguments = new ArrayList<>();
            for (int argument = 0; argument < argumentCount; argument++) {
                arguments.add(pool.describe(in.readUnsignedShort()));
            }
            result.add(method + " args=" + arguments);
        }
        return result;
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
        return List.copyOf(new TreeSet<>(values));
    }

    private record Pool(Object[] entries, List<Integer> tags) {
        String utf8(final int index) {
            return ((Utf8Entry) entries[index]).value();
        }

        String className(final int index) {
            return utf8(((ClassEntry) entries[index]).nameIndex());
        }

        String describe(final int index) {
            final Object entry = entries[index];
            if (entry instanceof Utf8Entry utf8) {
                return "Utf8:" + utf8.value();
            }
            if (entry instanceof ClassEntry classEntry) {
                return "Class:" + utf8(classEntry.nameIndex());
            }
            if (entry instanceof StringEntry stringEntry) {
                return "String:" + utf8(stringEntry.stringIndex());
            }
            if (entry instanceof RefEntry ref) {
                return refName(ref);
            }
            if (entry instanceof MethodHandleEntry handle) {
                return methodHandleDescription(handle);
            }
            if (entry instanceof MethodTypeEntry methodType) {
                return "MethodType:" + utf8(methodType.descriptorIndex());
            }
            if (entry instanceof DynamicEntry dynamic) {
                return "Dynamic:bootstrap=" + dynamic.bootstrapMethodAttributeIndex();
            }
            if (entry instanceof RawEntry raw) {
                return "tag" + raw.tag();
            }
            return "unknown";
        }

        Optional<String> methodHandle(final int index) {
            final Object entry = entries[index];
            if (entry instanceof MethodHandleEntry handle) {
                return Optional.of(methodHandleDescription(handle));
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
