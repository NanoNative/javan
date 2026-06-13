package javan.classfile;

import javan.compat.BytecodeSupport;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal Java class file reader.
 */
public final class ClassFileReader {
    private static final int MAGIC = 0xCAFEBABE;

    /**
     * Reads a class file.
     *
     * @param input class file input stream
     * @param source source path used for diagnostics
     * @return parsed class file
     * @throws IOException when parsing fails
     */
    public ClassFile read(final InputStream input, final Path source) throws IOException {
        final DataInputStream in = new DataInputStream(input);
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a Java class file: " + source);
        }
        in.readUnsignedShort();
        final int major = in.readUnsignedShort();
        final ConstantPool constantPool = readConstantPool(in);
        final int accessFlags = in.readUnsignedShort();
        final String thisClass = constantPool.className(in.readUnsignedShort());
        final int superIndex = in.readUnsignedShort();
        final String superClass = superIndex == 0 ? "" : constantPool.className(superIndex);
        final List<String> interfaces = readInterfaces(in, constantPool);
        final List<FieldInfo> fields = readFields(in, constantPool);
        final List<MethodInfo> methods = readMethods(in, constantPool);
        final List<BootstrapMethod> bootstrapMethods = readClassAttributes(in, constantPool);
        return new ClassFile(
            major,
            thisClass,
            superClass,
            accessFlags,
            interfaces,
            List.copyOf(fields),
            resolveInstructions(methods, constantPool, bootstrapMethods),
            source,
            true
        );
    }

    private static ConstantPool readConstantPool(final DataInputStream in) throws IOException {
        final int count = in.readUnsignedShort();
        final Object[] entries = new Object[count];
        for (int index = 1; index < count; index++) {
            final int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> entries[index] = new ConstantPool.Utf8Entry(in.readUTF());
                case 3 -> entries[index] = new ConstantPool.RawEntry(tag, in.readInt());
                case 4 -> entries[index] = new ConstantPool.RawEntry(tag, in.readFloat());
                case 5 -> {
                    entries[index] = new ConstantPool.RawEntry(tag, in.readLong());
                    index++;
                }
                case 6 -> {
                    entries[index] = new ConstantPool.RawEntry(tag, in.readDouble());
                    index++;
                }
                case 7 -> entries[index] = new ConstantPool.ClassEntry(in.readUnsignedShort());
                case 8 -> entries[index] = new ConstantPool.StringEntry(in.readUnsignedShort());
                case 9, 10, 11 -> entries[index] = new ConstantPool.RefEntry(tag, in.readUnsignedShort(), in.readUnsignedShort());
                case 12 -> entries[index] = new ConstantPool.NameAndTypeEntry(in.readUnsignedShort(), in.readUnsignedShort());
                case 15 -> entries[index] = new ConstantPool.MethodHandleEntry(in.readUnsignedByte(), in.readUnsignedShort());
                case 16 -> entries[index] = new ConstantPool.MethodTypeEntry(in.readUnsignedShort());
                case 17, 18 -> entries[index] = new ConstantPool.DynamicEntry(tag, in.readUnsignedShort(), in.readUnsignedShort());
                case 19, 20 -> entries[index] = new ConstantPool.RawEntry(tag, in.readUnsignedShort());
                default -> throw new IOException("Unsupported constant pool tag " + tag);
            }
        }
        return new ConstantPool(entries);
    }

    private static List<String> readInterfaces(final DataInputStream in, final ConstantPool constantPool) throws IOException {
        final int count = in.readUnsignedShort();
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(constantPool.className(in.readUnsignedShort()));
        }
        return List.copyOf(result);
    }

    private static List<FieldInfo> readFields(final DataInputStream in, final ConstantPool constantPool) throws IOException {
        final int count = in.readUnsignedShort();
        final List<FieldInfo> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int accessFlags = in.readUnsignedShort();
            final String name = constantPool.utf8(in.readUnsignedShort());
            final String descriptor = constantPool.utf8(in.readUnsignedShort());
            skipAttributes(in);
            result.add(new FieldInfo(accessFlags, name, descriptor));
        }
        return result;
    }

    private static List<MethodInfo> readMethods(final DataInputStream in, final ConstantPool constantPool) throws IOException {
        final int count = in.readUnsignedShort();
        final List<MethodInfo> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int accessFlags = in.readUnsignedShort();
            final String name = constantPool.utf8(in.readUnsignedShort());
            final String descriptor = constantPool.utf8(in.readUnsignedShort());
            Optional<CodeAttribute> code = Optional.empty();
            final int attributes = in.readUnsignedShort();
            for (int attribute = 0; attribute < attributes; attribute++) {
                final String attributeName = constantPool.utf8(in.readUnsignedShort());
                final int length = in.readInt();
                if ("Code".equals(attributeName)) {
                    code = Optional.of(readCode(in, constantPool));
                } else {
                    in.skipNBytes(length);
                }
            }
            result.add(new MethodInfo(accessFlags, name, descriptor, code));
        }
        return result;
    }

    private static CodeAttribute readCode(final DataInputStream in, final ConstantPool constantPool) throws IOException {
        final int maxStack = in.readUnsignedShort();
        final int maxLocals = in.readUnsignedShort();
        final int codeLength = in.readInt();
        final byte[] bytecode = in.readNBytes(codeLength);
        final int exceptionTableLength = in.readUnsignedShort();
        final List<CodeException> exceptionTable = readExceptionTable(in, constantPool, exceptionTableLength);
        skipAttributes(in);
        return new CodeAttribute(maxStack, maxLocals, bytecode, exceptionTableLength, exceptionTable, List.of());
    }

    private static List<CodeException> readExceptionTable(
        final DataInputStream in,
        final ConstantPool constantPool,
        final int exceptionTableLength
    ) throws IOException {
        final List<CodeException> result = new ArrayList<>();
        for (int index = 0; index < exceptionTableLength; index++) {
            final int startPc = in.readUnsignedShort();
            final int endPc = in.readUnsignedShort();
            final int handlerPc = in.readUnsignedShort();
            final int catchType = in.readUnsignedShort();
            result.add(new CodeException(
                startPc,
                endPc,
                handlerPc,
                catchType == 0 ? Optional.empty() : Optional.of(constantPool.className(catchType))
            ));
        }
        return List.copyOf(result);
    }

    private static List<BootstrapMethod> readClassAttributes(final DataInputStream in, final ConstantPool constantPool) throws IOException {
        final int count = in.readUnsignedShort();
        final List<BootstrapMethod> bootstrapMethods = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String attributeName = constantPool.utf8(in.readUnsignedShort());
            final int length = in.readInt();
            if ("BootstrapMethods".equals(attributeName)) {
                bootstrapMethods.addAll(readBootstrapMethods(in));
            } else {
                in.skipNBytes(length);
            }
        }
        return List.copyOf(bootstrapMethods);
    }

    private static List<BootstrapMethod> readBootstrapMethods(final DataInputStream in) throws IOException {
        final int count = in.readUnsignedShort();
        final List<BootstrapMethod> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int methodHandleIndex = in.readUnsignedShort();
            final int argumentCount = in.readUnsignedShort();
            final List<Integer> arguments = new ArrayList<>();
            for (int argument = 0; argument < argumentCount; argument++) {
                arguments.add(in.readUnsignedShort());
            }
            result.add(new BootstrapMethod(methodHandleIndex, List.copyOf(arguments)));
        }
        return List.copyOf(result);
    }

    private static List<MethodInfo> resolveInstructions(
        final List<MethodInfo> methods,
        final ConstantPool constantPool,
        final List<BootstrapMethod> bootstrapMethods
    ) throws IOException {
        final List<MethodInfo> result = new ArrayList<>();
        for (final MethodInfo method : methods) {
            if (method.code().isEmpty()) {
                result.add(method);
                continue;
            }
            final CodeAttribute code = method.code().orElseThrow();
            result.add(new MethodInfo(method.accessFlags(), method.name(), method.descriptor(), Optional.of(new CodeAttribute(
                code.maxStack(),
                code.maxLocals(),
                code.bytecode(),
                code.exceptionTableLength(),
                code.exceptionTable(),
                decode(code.bytecode(), constantPool, bootstrapMethods)
            ))));
        }
        return List.copyOf(result);
    }

    private static void skipAttributes(final DataInputStream in) throws IOException {
        final int count = in.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            in.readUnsignedShort();
            in.skipNBytes(in.readInt());
        }
    }

    private static List<Instruction> decode(
        final byte[] bytecode,
        final ConstantPool constantPool,
        final List<BootstrapMethod> bootstrapMethods
    ) throws IOException {
        final List<Instruction> instructions = new ArrayList<>();
        int offset = 0;
        while (offset < bytecode.length) {
            final int opcode = unsigned(bytecode[offset]);
            final int length = instructionLength(bytecode, offset);
            final byte[] operands = new byte[Math.max(0, length - 1)];
            System.arraycopy(bytecode, offset + 1, operands, 0, operands.length);
            instructions.add(new Instruction(
                offset,
                opcode,
                BytecodeSupport.mnemonic(opcode),
                operands,
                methodRef(opcode, operands, constantPool),
                fieldRef(opcode, operands, constantPool),
                className(opcode, operands, constantPool),
                stringValue(opcode, operands, constantPool),
                intValue(opcode, operands, constantPool),
                longValue(opcode, operands, constantPool),
                floatValue(opcode, operands, constantPool),
                doubleValue(opcode, operands, constantPool),
                dynamicRef(opcode, operands, constantPool, bootstrapMethods)
            ));
            offset += length;
        }
        return List.copyOf(instructions);
    }

    private static Optional<MethodRef> methodRef(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 182 || opcode == 183 || opcode == 184 || opcode == 185) {
            return Optional.of(constantPool.methodRef(index16(operands, 0)));
        }
        return Optional.empty();
    }

    private static Optional<DynamicRef> dynamicRef(
        final int opcode,
        final byte[] operands,
        final ConstantPool constantPool,
        final List<BootstrapMethod> bootstrapMethods
    ) {
        if (opcode == 186) {
            return constantPool.dynamicRef(index16(operands, 0), bootstrapMethods);
        }
        return Optional.empty();
    }

    private static Optional<FieldRef> fieldRef(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode >= 178 && opcode <= 181) {
            return Optional.of(constantPool.fieldRef(index16(operands, 0)));
        }
        return Optional.empty();
    }

    private static Optional<String> className(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 187 || opcode == 189) {
            return Optional.of(constantPool.className(index16(operands, 0)));
        }
        return Optional.empty();
    }

    private static Optional<String> stringValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 18) {
            return constantPool.string(unsigned(operands[0]));
        }
        if (opcode == 19) {
            return constantPool.string(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Integer> intValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 18) {
            return constantPool.intValue(unsigned(operands[0]));
        }
        if (opcode == 19) {
            return constantPool.intValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Long> longValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 20) {
            return constantPool.longValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Float> floatValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 18) {
            return constantPool.floatValue(unsigned(operands[0]));
        }
        if (opcode == 19) {
            return constantPool.floatValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Double> doubleValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 20) {
            return constantPool.doubleValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static int instructionLength(final byte[] bytecode, final int offset) throws IOException {
        final int opcode = unsigned(bytecode[offset]);
        return switch (opcode) {
            case 16, 18, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58, 169, 188 -> 2;
            case 17, 19, 20, 132, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 178,
                 179, 180, 181, 182, 183, 184, 187, 189, 192, 193, 198, 199 -> 3;
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

    private static int index16(final byte[] operands, final int offset) {
        return (unsigned(operands[offset]) << 8) | unsigned(operands[offset + 1]);
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

}
