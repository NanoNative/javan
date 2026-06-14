package javan.classfile;

import javan.compat.BytecodeSupport;

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
        return read(input.readAllBytes(), source);
    }

    /**
     * Reads a class file.
     *
     * @param bytes class file bytes
     * @param source source path used for diagnostics
     * @return parsed class file
     * @throws IOException when parsing fails
     */
    public ClassFile read(final byte[] bytes, final Path source) throws IOException {
        final ClassByteCursor in = new ClassByteCursor(bytes);
        if (in.i4() != MAGIC) {
            throw new IOException("Not a Java class file: " + source.toString());
        }
        in.u2();
        final int major = in.u2();
        final ConstantPool constantPool = readConstantPool(in);
        final int accessFlags = in.u2();
        final String thisClass = constantPool.className(in.u2());
        final int superIndex = in.u2();
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

    private static ConstantPool readConstantPool(final ClassByteCursor in) throws IOException {
        final int count = in.u2();
        final Object[] entries = new Object[count];
        for (int index = 1; index < count; index++) {
            final int tag = in.u1();
            switch (tag) {
                case 1 -> entries[index] = new ConstantPool.Utf8Entry(in.modifiedUtf8());
                case 3 -> entries[index] = new ConstantPool.RawEntry(tag, in.i4());
                case 4 -> entries[index] = new ConstantPool.RawEntry(tag, in.f4());
                case 5 -> {
                    entries[index] = new ConstantPool.RawEntry(tag, in.i8());
                    index++;
                }
                case 6 -> {
                    entries[index] = new ConstantPool.RawEntry(tag, in.f8());
                    index++;
                }
                case 7 -> entries[index] = new ConstantPool.ClassEntry(in.u2());
                case 8 -> entries[index] = new ConstantPool.StringEntry(in.u2());
                case 9, 10, 11 -> entries[index] = new ConstantPool.RefEntry(tag, in.u2(), in.u2());
                case 12 -> entries[index] = new ConstantPool.NameAndTypeEntry(in.u2(), in.u2());
                case 15 -> entries[index] = new ConstantPool.MethodHandleEntry(in.u1(), in.u2());
                case 16 -> entries[index] = new ConstantPool.MethodTypeEntry(in.u2());
                case 17, 18 -> entries[index] = new ConstantPool.DynamicEntry(tag, in.u2(), in.u2());
                case 19, 20 -> entries[index] = new ConstantPool.RawEntry(tag, in.u2());
                default -> throw new IOException("Unsupported constant pool tag " + tag);
            }
        }
        return new ConstantPool(entries);
    }

    private static List<String> readInterfaces(final ClassByteCursor in, final ConstantPool constantPool) throws IOException {
        final int count = in.u2();
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(constantPool.className(in.u2()));
        }
        return List.copyOf(result);
    }

    private static List<FieldInfo> readFields(final ClassByteCursor in, final ConstantPool constantPool) throws IOException {
        final int count = in.u2();
        final List<FieldInfo> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int accessFlags = in.u2();
            final String name = constantPool.utf8(in.u2());
            final String descriptor = constantPool.utf8(in.u2());
            skipAttributes(in);
            result.add(new FieldInfo(accessFlags, name, descriptor));
        }
        return result;
    }

    private static List<MethodInfo> readMethods(final ClassByteCursor in, final ConstantPool constantPool) throws IOException {
        final int count = in.u2();
        final List<MethodInfo> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int accessFlags = in.u2();
            final String name = constantPool.utf8(in.u2());
            final String descriptor = constantPool.utf8(in.u2());
            Optional<CodeAttribute> code = Optional.empty();
            final int attributes = in.u2();
            for (int attribute = 0; attribute < attributes; attribute++) {
                final String attributeName = constantPool.utf8(in.u2());
                final long length = in.u4();
                if ("Code".equals(attributeName)) {
                    code = Optional.of(readCode(in, constantPool));
                } else {
                    in.skip(length);
                }
            }
            result.add(new MethodInfo(accessFlags, name, descriptor, code));
        }
        return result;
    }

    private static CodeAttribute readCode(final ClassByteCursor in, final ConstantPool constantPool) throws IOException {
        final int maxStack = in.u2();
        final int maxLocals = in.u2();
        final long codeLength = in.u4();
        final byte[] bytecode = in.bytes(codeLength);
        final int exceptionTableLength = in.u2();
        final List<CodeException> exceptionTable = readExceptionTable(in, constantPool, exceptionTableLength);
        skipAttributes(in);
        return new CodeAttribute(maxStack, maxLocals, bytecode, exceptionTableLength, exceptionTable, List.of());
    }

    private static List<CodeException> readExceptionTable(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final int exceptionTableLength
    ) throws IOException {
        final List<CodeException> result = new ArrayList<>();
        for (int index = 0; index < exceptionTableLength; index++) {
            final int startPc = in.u2();
            final int endPc = in.u2();
            final int handlerPc = in.u2();
            final int catchType = in.u2();
            Optional<String> catchClass = Optional.empty();
            if (catchType != 0) {
                catchClass = Optional.of(constantPool.className(catchType));
            }
            result.add(new CodeException(
                startPc,
                endPc,
                handlerPc,
                catchClass
            ));
        }
        return List.copyOf(result);
    }

    private static List<BootstrapMethod> readClassAttributes(final ClassByteCursor in, final ConstantPool constantPool) throws IOException {
        final int count = in.u2();
        final List<BootstrapMethod> bootstrapMethods = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final String attributeName = constantPool.utf8(in.u2());
            final long length = in.u4();
            if ("BootstrapMethods".equals(attributeName)) {
                bootstrapMethods.addAll(readBootstrapMethods(in));
            } else {
                in.skip(length);
            }
        }
        return List.copyOf(bootstrapMethods);
    }

    private static List<BootstrapMethod> readBootstrapMethods(final ClassByteCursor in) throws IOException {
        final int count = in.u2();
        final List<BootstrapMethod> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int methodHandleIndex = in.u2();
            final int argumentCount = in.u2();
            final List<Integer> arguments = new ArrayList<>();
            for (int argument = 0; argument < argumentCount; argument++) {
                arguments.add(in.u2());
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

    private static void skipAttributes(final ClassByteCursor in) throws IOException {
        final int count = in.u2();
        for (int index = 0; index < count; index++) {
            in.u2();
            in.skip(in.u4());
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
        if (opcode == 187 || opcode == 189 || opcode == 192 || opcode == 193) {
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
        if (hasOneOperandByte(opcode)) {
            return 2;
        }
        if (hasTwoOperandBytes(opcode)) {
            return 3;
        }
        if (opcode == 185 || opcode == 186 || opcode == 200 || opcode == 201) {
            return 5;
        }
        if (opcode == 196) {
            return wideLength(bytecode, offset);
        }
        if (opcode == 197) {
            return 4;
        }
        if (opcode == 170) {
            return tableSwitchLength(bytecode, offset);
        }
        if (opcode == 171) {
            return lookupSwitchLength(bytecode, offset);
        }
        return 1;
    }

    private static boolean hasOneOperandByte(final int opcode) {
        if (opcode == 16) {
            return true;
        }
        if (opcode == 18) {
            return true;
        }
        if (opcode >= 21 && opcode <= 25) {
            return true;
        }
        if (opcode >= 54 && opcode <= 58) {
            return true;
        }
        if (opcode == 169) {
            return true;
        }
        if (opcode == 188) {
            return true;
        }
        return false;
    }

    private static boolean hasTwoOperandBytes(final int opcode) {
        if (opcode == 17) {
            return true;
        }
        if (opcode == 19) {
            return true;
        }
        if (opcode == 20) {
            return true;
        }
        if (opcode == 132) {
            return true;
        }
        if (opcode >= 153 && opcode <= 168) {
            return true;
        }
        if (opcode >= 178 && opcode <= 184) {
            return true;
        }
        if (opcode == 187) {
            return true;
        }
        if (opcode == 189) {
            return true;
        }
        if (opcode == 192) {
            return true;
        }
        if (opcode == 193) {
            return true;
        }
        if (opcode == 198) {
            return true;
        }
        if (opcode == 199) {
            return true;
        }
        return false;
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
