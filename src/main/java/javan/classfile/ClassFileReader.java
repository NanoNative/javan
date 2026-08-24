package javan.classfile;

import javan.compat.BytecodeSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        final List<MethodInfo> methods = readMethods(in, constantPool, thisClass);
        final ClassAttributes classAttributes = readClassAttributes(in, constantPool);
        return new ClassFile(
            major,
            thisClass,
            superClass,
            accessFlags,
            interfaces,
            List.copyOf(fields),
            resolveInstructions(methods, constantPool, classAttributes.bootstrapMethods()),
            classAttributes.sourceFile(),
            classAttributes.recordComponents(),
            classAttributes.permittedSubclasses(),
            classAttributes.nestHost().orElse(thisClass),
            classAttributes.serviceUses(),
            classAttributes.serviceProviders(),
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
            if (!FieldInfo.isValidDescriptor(descriptor)) {
                throw new IOException("Invalid field descriptor for " + name + ": " + descriptor);
            }
            if (containsField(result, name, descriptor)) {
                throw new IOException("Duplicate field: " + name + " " + descriptor);
            }
            final Optional<String> signature = readSignatureAttribute(in, constantPool, "field " + name);
            result.add(new FieldInfo(accessFlags, name, descriptor, signature));
        }
        return result;
    }

    private static boolean containsField(
        final List<FieldInfo> fields,
        final String name,
        final String descriptor
    ) {
        for (final FieldInfo field : fields) {
            if (field.name().equals(name) && field.descriptor().equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    private static List<MethodInfo> readMethods(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final String className
    ) throws IOException {
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
                    code = Optional.of(readCode(in, constantPool, className, accessFlags, name, descriptor));
                } else {
                    in.skip(length);
                }
            }
            result.add(new MethodInfo(accessFlags, name, descriptor, code));
        }
        return result;
    }

    private static CodeAttribute readCode(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final String className,
        final int accessFlags,
        final String methodName,
        final String descriptor
    ) throws IOException {
        final int maxStack = in.u2();
        final int maxLocals = in.u2();
        final long codeLength = in.u4();
        final byte[] bytecode = in.bytes(codeLength);
        final int exceptionTableLength = in.u2();
        final List<CodeException> exceptionTable = readExceptionTable(in, constantPool, exceptionTableLength);
        final Set<Integer> instructionOffsets = instructionOffsets(bytecode);
        final CodeAttributes attributes = readCodeAttributes(
            in,
            constantPool,
            className,
            accessFlags,
            methodName,
            descriptor,
            bytecode,
            instructionOffsets,
            maxLocals,
            maxStack
        );
        validateHandlerStackFrames(exceptionTable, attributes);
        return new CodeAttribute(
            maxStack,
            maxLocals,
            bytecode,
            exceptionTableLength,
            exceptionTable,
            attributes.lineNumbers(),
            attributes.stackMapFrames(),
            List.of()
        );
    }

    private static CodeAttributes readCodeAttributes(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final String className,
        final int accessFlags,
        final String methodName,
        final String descriptor,
        final byte[] bytecode,
        final Set<Integer> instructionOffsets,
        final int maxLocals,
        final int maxStack
    ) throws IOException {
        final int count = in.u2();
        final List<LineNumberEntry> lineNumbers = new ArrayList<>();
        List<StackMapFrame> stackMapFrames = List.of();
        boolean stackMapSeen = false;
        for (int index = 0; index < count; index++) {
            final String attributeName = constantPool.utf8(in.u2());
            final long length = in.u4();
            if ("LineNumberTable".equals(attributeName)) {
                final ClassByteCursor attribute = new ClassByteCursor(in.bytes(length));
                lineNumbers.addAll(readLineNumberTable(attribute));
                if (!attribute.exhausted()) {
                    throw new IOException("Invalid LineNumberTable attribute length");
                }
            } else if ("StackMapTable".equals(attributeName)) {
                if (stackMapSeen) {
                    throw new IOException("Duplicate StackMapTable attribute");
                }
                stackMapSeen = true;
                final ClassByteCursor attribute = new ClassByteCursor(in.bytes(length));
                stackMapFrames = readStackMapTable(
                    attribute,
                    constantPool,
                    initialFrameLocals(className, accessFlags, methodName, descriptor),
                    bytecode,
                    instructionOffsets,
                    maxLocals,
                    maxStack
                );
                if (!attribute.exhausted()) {
                    throw new IOException("Invalid StackMapTable attribute length");
                }
            } else {
                in.skip(length);
            }
        }
        return new CodeAttributes(List.copyOf(lineNumbers), stackMapFrames, stackMapSeen);
    }

    private static List<LineNumberEntry> readLineNumberTable(final ClassByteCursor in) throws IOException {
        final int count = in.u2();
        final List<LineNumberEntry> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new LineNumberEntry(in.u2(), in.u2()));
        }
        return List.copyOf(result);
    }

    private static List<StackMapFrame> readStackMapTable(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final List<FrameValue> initialLocals,
        final byte[] bytecode,
        final Set<Integer> instructionOffsets,
        final int maxLocals,
        final int maxStack
    ) throws IOException {
        final int count = in.u2();
        final List<StackMapFrame> result = new ArrayList<>(count);
        List<FrameValue> locals = initialLocals;
        validateLocalSlots(locals, maxLocals);
        int previousOffset = -1;
        for (int index = 0; index < count; index++) {
            final int frameType = in.u1();
            final int offsetDelta;
            List<FrameValue> stackValues = List.of();
            if (frameType <= 63) {
                offsetDelta = frameType;
            } else if (frameType <= 127) {
                offsetDelta = frameType - 64;
                stackValues = List.of(readVerificationType(in, constantPool, bytecode, instructionOffsets));
            } else if (frameType == 247) {
                offsetDelta = in.u2();
                stackValues = List.of(readVerificationType(in, constantPool, bytecode, instructionOffsets));
            } else if (frameType >= 248 && frameType <= 250) {
                offsetDelta = in.u2();
                final int removed = 251 - frameType;
                if (removed > locals.size()) {
                    throw new IOException("Invalid StackMapTable chop frame");
                }
                final List<FrameValue> retained = new ArrayList<>(locals.size() - removed);
                for (int local = 0; local < locals.size() - removed; local++) {
                    retained.add(locals.get(local));
                }
                locals = List.copyOf(retained);
            } else if (frameType == 251) {
                offsetDelta = in.u2();
            } else if (frameType >= 252 && frameType <= 254) {
                offsetDelta = in.u2();
                final List<FrameValue> appended = new ArrayList<>(locals);
                for (int local = 0; local < frameType - 251; local++) {
                    appended.add(readVerificationType(in, constantPool, bytecode, instructionOffsets));
                }
                locals = List.copyOf(appended);
            } else if (frameType == 255) {
                offsetDelta = in.u2();
                final int localCount = in.u2();
                final List<FrameValue> fullLocals = new ArrayList<>(localCount);
                for (int local = 0; local < localCount; local++) {
                    fullLocals.add(readVerificationType(in, constantPool, bytecode, instructionOffsets));
                }
                locals = List.copyOf(fullLocals);
                final int stackCount = in.u2();
                final List<FrameValue> fullStack = new ArrayList<>(stackCount);
                for (int stack = 0; stack < stackCount; stack++) {
                    fullStack.add(readVerificationType(in, constantPool, bytecode, instructionOffsets));
                }
                stackValues = List.copyOf(fullStack);
            } else {
                throw new IOException("Invalid StackMapTable frame type " + frameType);
            }
            final long calculatedOffset = (long) previousOffset + offsetDelta + 1L;
            if (calculatedOffset > Integer.MAX_VALUE
                || !instructionOffsets.contains(Integer.valueOf((int) calculatedOffset))) {
                throw new IOException("Invalid StackMapTable frame offset " + calculatedOffset);
            }
            final int offset = (int) calculatedOffset;
            final Map<Integer, String> objectLocals = objectLocals(locals, maxLocals);
            result.add(new StackMapFrame(offset, objectLocals, stackTypes(stackValues, maxStack)));
            previousOffset = offset;
        }
        return List.copyOf(result);
    }

    private static FrameValue readVerificationType(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final byte[] bytecode,
        final Set<Integer> instructionOffsets
    ) throws IOException {
        final int tag = in.u1();
        if (tag == 0 || tag == 1 || tag == 2 || tag == 5 || tag == 6) {
            return FrameValue.other(1);
        }
        if (tag == 3 || tag == 4) {
            return FrameValue.other(2);
        }
        if (tag == 7) {
            final int classIndex = in.u2();
            if (!constantPool.containsIndex(classIndex)
                || constantPool.entryTag(classIndex).orElse(-1) != 7) {
                throw new IOException("Invalid StackMapTable object type index " + classIndex);
            }
            return FrameValue.object(constantPool.className(classIndex));
        }
        if (tag == 8) {
            final int offset = in.u2();
            if (!instructionOffsets.contains(Integer.valueOf(offset))
                || unsigned(bytecode[offset]) != 187) {
                throw new IOException("Invalid StackMapTable uninitialized offset " + offset);
            }
            return FrameValue.other(1);
        }
        throw new IOException("Invalid StackMapTable verification type " + tag);
    }

    private static List<FrameValue> initialFrameLocals(
        final String className,
        final int accessFlags,
        final String methodName,
        final String descriptor
    ) throws IOException {
        if (descriptor.length() < 3 || descriptor.charAt(0) != '(') {
            throw new IOException("Invalid method descriptor: " + descriptor);
        }
        final List<FrameValue> result = new ArrayList<>();
        if ((accessFlags & 0x0008) == 0) {
            result.add("<init>".equals(methodName) ? FrameValue.other(1) : FrameValue.object(className));
        }
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final int start = index;
            while (index < descriptor.length() && descriptor.charAt(index) == '[') {
                index++;
            }
            if (index >= descriptor.length()) {
                throw new IOException("Invalid method descriptor: " + descriptor);
            }
            final char type = descriptor.charAt(index);
            if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    throw new IOException("Invalid method descriptor: " + descriptor);
                }
                result.add(FrameValue.object(start == index
                    ? descriptor.substring(index + 1, end)
                    : descriptor.substring(start, end + 1)));
                index = end + 1;
            } else if (start != index) {
                if ("BCDFIJSZ".indexOf(type) < 0) {
                    throw new IOException("Invalid method descriptor: " + descriptor);
                }
                result.add(FrameValue.object(descriptor.substring(start, index + 1)));
                index++;
            } else if (type == 'J' || type == 'D') {
                result.add(FrameValue.other(2));
                index++;
            } else if ("BCFISZ".indexOf(type) >= 0) {
                result.add(FrameValue.other(1));
                index++;
            } else {
                throw new IOException("Invalid method descriptor: " + descriptor);
            }
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            throw new IOException("Invalid method descriptor: " + descriptor);
        }
        return List.copyOf(result);
    }

    private static Map<Integer, String> objectLocals(
        final List<FrameValue> locals,
        final int maxLocals
    ) throws IOException {
        final Map<Integer, String> result = new LinkedHashMap<>();
        int slot = 0;
        for (final FrameValue local : locals) {
            if (slot + local.width() > maxLocals) {
                throw new IOException("StackMapTable locals exceed max_locals");
            }
            if (local.objectType().isPresent()) {
                result.put(Integer.valueOf(slot), local.objectType().orElseThrow());
            }
            slot += local.width();
        }
        return Map.copyOf(result);
    }

    private static void validateLocalSlots(final List<FrameValue> locals, final int maxLocals) throws IOException {
        objectLocals(locals, maxLocals);
    }

    private static List<Optional<String>> stackTypes(
        final List<FrameValue> stack,
        final int maxStack
    ) throws IOException {
        final List<Optional<String>> result = new ArrayList<>(stack.size());
        int slots = 0;
        for (final FrameValue value : stack) {
            slots += value.width();
            if (slots > maxStack) {
                throw new IOException("StackMapTable stack exceeds max_stack");
            }
            result.add(value.objectType());
        }
        return List.copyOf(result);
    }

    private static Set<Integer> instructionOffsets(final byte[] bytecode) throws IOException {
        final Set<Integer> result = new HashSet<>();
        int offset = 0;
        while (offset < bytecode.length) {
            result.add(Integer.valueOf(offset));
            offset += instructionLength(bytecode, offset);
        }
        if (offset != bytecode.length) {
            throw new IOException("Invalid bytecode length");
        }
        return Set.copyOf(result);
    }

    private static void validateHandlerStackFrames(
        final List<CodeException> handlers,
        final CodeAttributes attributes
    ) throws IOException {
        if (!attributes.stackMapPresent()) {
            return;
        }
        for (final CodeException handler : handlers) {
            Optional<StackMapFrame> matching = Optional.empty();
            for (final StackMapFrame frame : attributes.stackMapFrames()) {
                if (frame.offset() == handler.handlerPc()) {
                    matching = Optional.of(frame);
                    break;
                }
            }
            if (matching.isEmpty()) {
                throw new IOException("Missing StackMapTable frame for exception handler " + handler.handlerPc());
            }
            if (matching.orElseThrow().singleStackObjectType().isEmpty()) {
                throw new IOException("Invalid StackMapTable stack for exception handler " + handler.handlerPc());
            }
        }
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

    private static ClassAttributes readClassAttributes(final ClassByteCursor in, final ConstantPool constantPool) throws IOException {
        final int count = in.u2();
        final List<BootstrapMethod> bootstrapMethods = new ArrayList<>();
        Optional<String> sourceFile = Optional.empty();
        Optional<List<RecordComponentInfo>> recordComponents = Optional.empty();
        List<String> permittedSubclasses = List.of();
        Optional<String> nestHost = Optional.empty();
        List<String> serviceUses = List.of();
        List<ServiceProvider> serviceProviders = List.of();
        boolean moduleSeen = false;
        for (int index = 0; index < count; index++) {
            final String attributeName = constantPool.utf8(in.u2());
            final long length = in.u4();
            if ("BootstrapMethods".equals(attributeName)) {
                bootstrapMethods.addAll(readBootstrapMethods(in));
            } else if ("SourceFile".equals(attributeName)) {
                sourceFile = Optional.of(constantPool.utf8(in.u2()));
            } else if ("Record".equals(attributeName)) {
                if (recordComponents.isPresent()) {
                    throw new IOException("Duplicate Record attribute");
                }
                final ClassByteCursor attribute = new ClassByteCursor(in.bytes(length));
                recordComponents = Optional.of(readRecordComponents(attribute, constantPool));
                if (!attribute.exhausted()) {
                    throw new IOException("Invalid Record attribute length");
                }
            } else if ("PermittedSubclasses".equals(attributeName)) {
                if (!permittedSubclasses.isEmpty()) {
                    throw new IOException("Duplicate PermittedSubclasses attribute");
                }
                permittedSubclasses = readPermittedSubclassesAttribute(in, length, constantPool);
            } else if ("NestHost".equals(attributeName)) {
                if (nestHost.isPresent() || length != 2L) {
                    throw new IOException("Invalid or duplicate NestHost attribute");
                }
                nestHost = Optional.of(constantPool.className(in.u2()));
            } else if ("Module".equals(attributeName)) {
                if (moduleSeen) {
                    throw new IOException("Duplicate Module attribute");
                }
                moduleSeen = true;
                final ModuleServices services = readModuleServices(in, length, constantPool);
                serviceUses = services.uses();
                serviceProviders = services.providers();
            } else {
                in.skip(length);
            }
        }
        return new ClassAttributes(
            List.copyOf(bootstrapMethods), sourceFile, recordComponents, permittedSubclasses, nestHost,
            serviceUses, serviceProviders
        );
    }

    private static ModuleServices readModuleServices(
        final ClassByteCursor in,
        final long length,
        final ConstantPool constantPool
    ) throws IOException {
        final ClassByteCursor attribute = new ClassByteCursor(in.bytes(length));
        attribute.skip(6);
        skipFixedModuleTable(attribute, 3);
        skipTargetedModuleTable(attribute);
        skipTargetedModuleTable(attribute);
        final int uses = attribute.u2();
        final List<String> serviceUses = new ArrayList<>();
        for (int index = 0; index < uses; index++) {
            serviceUses.add(constantPool.className(attribute.u2()));
        }
        final int provides = attribute.u2();
        final List<ServiceProvider> result = new ArrayList<>();
        for (int index = 0; index < provides; index++) {
            final String service = constantPool.className(attribute.u2());
            final int providerCount = attribute.u2();
            for (int provider = 0; provider < providerCount; provider++) {
                result.add(new ServiceProvider(service, constantPool.className(attribute.u2()), true));
            }
        }
        if (!attribute.exhausted()) {
            throw new IOException("Invalid Module attribute length");
        }
        return new ModuleServices(List.copyOf(serviceUses), List.copyOf(result));
    }

    private static void skipFixedModuleTable(final ClassByteCursor in, final int fields) throws IOException {
        final int count = in.u2();
        in.skip((long) count * fields * 2L);
    }

    private static void skipTargetedModuleTable(final ClassByteCursor in) throws IOException {
        final int count = in.u2();
        for (int index = 0; index < count; index++) {
            attributeEntry(in);
        }
    }

    private static void attributeEntry(final ClassByteCursor in) throws IOException {
        in.skip(4);
        final int targets = in.u2();
        in.skip(2L * targets);
    }

    private static List<String> readPermittedSubclassesAttribute(
        final ClassByteCursor in,
        final long length,
        final ConstantPool constantPool
    ) throws IOException {
        if (length < 2L) {
            throw new IOException("Invalid PermittedSubclasses attribute length");
        }
        final ClassByteCursor attribute = new ClassByteCursor(in.bytes(length));
        final int count = attribute.u2();
        if (length != 2L + 2L * count) {
            throw new IOException("Invalid PermittedSubclasses attribute length");
        }
        if (count == 0) {
            throw new IOException("PermittedSubclasses attribute has no classes");
        }
        return readPermittedSubclasses(attribute, count, constantPool);
    }

    private static List<String> readPermittedSubclasses(
        final ClassByteCursor in,
        final int count,
        final ConstantPool constantPool
    ) throws IOException {
        final List<String> result = new ArrayList<>(count);
        final Set<String> owners = new HashSet<>();
        for (int index = 0; index < count; index++) {
            final int classIndex = in.u2();
            final String owner = permittedSubclassName(classIndex, constantPool);
            if (!owners.add(owner)) {
                throw new IOException("Duplicate permitted subclass: " + owner);
            }
            result.add(owner);
        }
        return List.copyOf(result);
    }

    private static String permittedSubclassName(final int classIndex, final ConstantPool constantPool) throws IOException {
        if (!constantPool.containsIndex(classIndex)) {
            throw new IOException(
                "Invalid PermittedSubclasses constant pool index " + classIndex + ": out of range"
            );
        }
        if (constantPool.entryTag(classIndex).orElse(-1) != 7) {
            throw new IOException(
                "Invalid PermittedSubclasses constant pool index " + classIndex + ": expected CONSTANT_Class"
            );
        }
        final int nameIndex = constantPool.classNameIndex(classIndex).orElseThrow();
        if (!constantPool.containsIndex(nameIndex)) {
            throw new IOException(
                "Invalid PermittedSubclasses class name constant pool index " + nameIndex + ": out of range"
            );
        }
        if (constantPool.entryTag(nameIndex).orElse(-1) != 1) {
            throw new IOException(
                "Invalid PermittedSubclasses class name constant pool index " + nameIndex
                    + ": expected CONSTANT_Utf8"
            );
        }
        return constantPool.utf8(nameIndex);
    }

    private static List<RecordComponentInfo> readRecordComponents(
        final ClassByteCursor in,
        final ConstantPool constantPool
    ) throws IOException {
        final int count = in.u2();
        final List<RecordComponentInfo> result = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        for (int index = 0; index < count; index++) {
            final String name = constantPool.utf8(in.u2());
            final String descriptor = constantPool.utf8(in.u2());
            if (!names.add(name)) {
                throw new IOException("Duplicate record component: " + name);
            }
            if (!FieldInfo.isValidDescriptor(descriptor)) {
                throw new IOException("Invalid record component descriptor for " + name + ": " + descriptor);
            }
            final Optional<String> signature =
                readSignatureAttribute(in, constantPool, "record component " + name);
            result.add(new RecordComponentInfo(name, descriptor, signature));
        }
        return List.copyOf(result);
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
            final CodeAttribute resolved = new CodeAttribute(
                code.maxStack(),
                code.maxLocals(),
                code.bytecode(),
                code.exceptionTableLength(),
                code.exceptionTable(),
                code.lineNumbers(),
                code.stackMapFrames(),
                decode(code.bytecode(), constantPool, bootstrapMethods)
            );
            result.add(new MethodInfo(
                method.accessFlags(),
                method.name(),
                method.descriptor(),
                Optional.of(LegacySubroutineNormalizer.normalize(resolved))
            ));
        }
        return List.copyOf(result);
    }

    private record ClassAttributes(
        List<BootstrapMethod> bootstrapMethods,
        Optional<String> sourceFile,
        Optional<List<RecordComponentInfo>> recordComponents,
        List<String> permittedSubclasses,
        Optional<String> nestHost,
        List<String> serviceUses,
        List<ServiceProvider> serviceProviders
    ) {
    }

    private record ModuleServices(List<String> uses, List<ServiceProvider> providers) {
    }

    private record CodeAttributes(
        List<LineNumberEntry> lineNumbers,
        List<StackMapFrame> stackMapFrames,
        boolean stackMapPresent
    ) {
    }

    private record FrameValue(int width, Optional<String> objectType) {
        private static FrameValue other(final int width) {
            return new FrameValue(width, Optional.empty());
        }

        private static FrameValue object(final String type) {
            return new FrameValue(1, Optional.of(type));
        }
    }

    private static Optional<String> readSignatureAttribute(
        final ClassByteCursor in,
        final ConstantPool constantPool,
        final String owner
    ) throws IOException {
        final int count = in.u2();
        Optional<String> signature = Optional.empty();
        for (int index = 0; index < count; index++) {
            final String name = constantPool.utf8(in.u2());
            final long length = in.u4();
            if (!"Signature".equals(name)) {
                in.skip(length);
                continue;
            }
            if (signature.isPresent()) {
                throw new IOException("Duplicate Signature attribute for " + owner);
            }
            if (length != 2) {
                throw new IOException("Invalid Signature attribute length for " + owner);
            }
            signature = Optional.of(constantPool.utf8(in.u2()));
        }
        return signature;
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
                dynamicRef(opcode, operands, constantPool, bootstrapMethods),
                constantPoolTag(opcode, operands, constantPool)
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
        if (opcode == 18) {
            return constantPool.classLiteralName(unsigned(operands[0]));
        }
        if (opcode == 19) {
            return constantPool.classLiteralName(index16(operands, 0));
        }
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
        final Optional<Integer> smallInteger = smallIntegerLiteral(opcode, operands);
        if (smallInteger.isPresent()) {
            return smallInteger;
        }
        if (opcode == 18) {
            return constantPool.intValue(unsigned(operands[0]));
        }
        if (opcode == 19) {
            return constantPool.intValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Integer> smallIntegerLiteral(final int opcode, final byte[] operands) {
        if (opcode == 2) {
            return Optional.of(-1);
        }
        if (opcode == 3) {
            return Optional.of(0);
        }
        if (opcode == 4) {
            return Optional.of(1);
        }
        if (opcode == 5) {
            return Optional.of(2);
        }
        if (opcode == 6) {
            return Optional.of(3);
        }
        if (opcode == 7) {
            return Optional.of(4);
        }
        if (opcode == 8) {
            return Optional.of(5);
        }
        if (opcode == 16) {
            if (operands.length != 1) {
                return Optional.empty();
            }
            return Optional.of((int) operands[0]);
        }
        if (opcode == 17) {
            if (operands.length != 2) {
                return Optional.empty();
            }
            return Optional.of(signedShort(operands[0], operands[1]));
        }
        return Optional.empty();
    }

    private static int signedShort(final byte high, final byte low) {
        final int unsigned = ((high & 0xFF) << 8) | (low & 0xFF);
        return unsigned > Short.MAX_VALUE ? unsigned - 0x1_0000 : unsigned;
    }

    private static Optional<Long> longValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        final Optional<Long> smallLong = smallLongLiteral(opcode);
        if (smallLong.isPresent()) {
            return smallLong;
        }
        if (opcode == 20) {
            return constantPool.longValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Float> floatValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        final Optional<Float> smallFloat = smallFloatLiteral(opcode);
        if (smallFloat.isPresent()) {
            return smallFloat;
        }
        if (opcode == 18) {
            return constantPool.floatValue(unsigned(operands[0]));
        }
        if (opcode == 19) {
            return constantPool.floatValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Double> doubleValue(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        final Optional<Double> smallDouble = smallDoubleLiteral(opcode);
        if (smallDouble.isPresent()) {
            return smallDouble;
        }
        if (opcode == 20) {
            return constantPool.doubleValue(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Integer> constantPoolTag(final int opcode, final byte[] operands, final ConstantPool constantPool) {
        if (opcode == 18) {
            return constantPool.entryTag(unsigned(operands[0]));
        }
        if (opcode == 19 || opcode == 20) {
            return constantPool.entryTag(index16(operands, 0));
        }
        return Optional.empty();
    }

    private static Optional<Long> smallLongLiteral(final int opcode) {
        if (opcode == 9) {
            return Optional.of(0L);
        }
        if (opcode == 10) {
            return Optional.of(1L);
        }
        return Optional.empty();
    }

    private static Optional<Float> smallFloatLiteral(final int opcode) {
        if (opcode == 11) {
            return Optional.of(0.0f);
        }
        if (opcode == 12) {
            return Optional.of(1.0f);
        }
        if (opcode == 13) {
            return Optional.of(2.0f);
        }
        return Optional.empty();
    }

    private static Optional<Double> smallDoubleLiteral(final int opcode) {
        if (opcode == 14) {
            return Optional.of(0.0d);
        }
        if (opcode == 15) {
            return Optional.of(1.0d);
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
