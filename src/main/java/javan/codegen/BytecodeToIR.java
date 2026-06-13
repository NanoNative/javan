package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.FieldInfo;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.ir.IrClass;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrFunction;
import javan.ir.IrExpression;
import javan.ir.IrField;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrProgram;
import javan.ir.IrType;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers the initial supported bytecode subset to javan IR.
 */
public final class BytecodeToIR {
    /**
     * Lowers reachable methods to IR.
     *
     * @param classes parsed classes
     * @param callGraph reachable call graph
     * @return lowered IR program
     */
    public IrProgram lower(final Map<String, ClassFile> classes, final CallGraph callGraph) {
        final List<IrFunction> functions = new ArrayList<>();
        final Map<String, IrDispatch> dispatches = new LinkedHashMap<>();
        for (final EntryPoint reachable : callGraph.reachableMethods().stream()
            .sorted(java.util.Comparator.comparing(BytecodeToIR::symbol))
            .toList()) {
            functions.add(lowerFunction(classes, reachable, dispatches));
        }
        return new IrProgram(lowerClasses(classes), List.copyOf(functions), List.copyOf(dispatches.values()), symbol(callGraph.entryPoint()));
    }

    private static IrFunction lowerFunction(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint,
        final Map<String, IrDispatch> dispatches
    ) {
        final ClassFile classFile = classes.get(entryPoint.className());
        final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElseThrow();
        final MethodDescriptor descriptor = MethodDescriptor.parse(method.descriptor());
        final List<IrParameter> parameters = parameters(method, descriptor);
        final List<IrInstruction> instructions = new ArrayList<>();
        final List<StackValue> stack = new ArrayList<>();
        final Map<Integer, IrExpression> locals = new HashMap<>();
        final Map<Integer, IrLocal> localDeclarations = new LinkedHashMap<>();
        final CodeAttribute code = method.code().orElseThrow();
        final List<Instruction> bytecode = code.instructions();
        final Set<Integer> branchTargets = branchTargets(code);
        bindParameters(method, descriptor, parameters, locals);
        for (final Instruction instruction : bytecode) {
            if (branchTargets.contains(instruction.offset())) {
                instructions.add(IrInstruction.label(label(instruction.offset())));
            }
            lowerInstruction(classes, classFile, method, instruction, instructions, stack, locals, localDeclarations, dispatches);
        }
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.copyOf(localDeclarations.values()),
            List.copyOf(instructions)
        );
    }

    private static List<IrClass> lowerClasses(final Map<String, ClassFile> classes) {
        return classes.values().stream()
            .sorted(java.util.Comparator.comparing(ClassFile::name))
            .map(classFile -> new IrClass(
                classFile.name(),
                classSymbol(classFile.name()),
                classFile.fields().stream()
                    .filter(field -> !field.isStatic())
                    .filter(field -> fieldType(field.descriptor()).isPresent())
                    .map(field -> new IrField(fieldType(field.descriptor()).orElseThrow(), field.name(), fieldSymbol(field.name())))
                    .toList(),
                classFile.fields().stream()
                    .filter(FieldInfo::isStatic)
                    .filter(field -> fieldType(field.descriptor()).isPresent())
                    .map(field -> new IrField(fieldType(field.descriptor()).orElseThrow(), field.name(), fieldSymbol(field.name())))
                    .toList()
            ))
            .toList();
    }

    private static List<IrParameter> parameters(final MethodInfo method, final MethodDescriptor descriptor) {
        final List<IrParameter> result = new ArrayList<>();
        if (!method.isStatic()) {
            result.add(new IrParameter(IrType.OBJECT, "self"));
        }
        for (int index = 0; index < descriptor.parameterTypes().size(); index++) {
            result.add(new IrParameter(descriptor.parameterTypes().get(index), "arg" + index));
        }
        return List.copyOf(result);
    }

    private static void bindParameters(
        final MethodInfo method,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters,
        final Map<Integer, IrExpression> locals
    ) {
        int parameterIndex = 0;
        int slot = 0;
        if (!method.isStatic()) {
            locals.put(slot, parameterExpression(parameters.get(parameterIndex)));
            parameterIndex++;
            slot++;
        }
        for (int index = 0; index < descriptor.parameterTypes().size(); index++) {
            locals.put(slot, parameterExpression(parameters.get(parameterIndex)));
            parameterIndex++;
            slot += descriptor.parameterTypes().get(index).slotWidth();
        }
    }

    private static IrExpression parameterExpression(final IrParameter parameter) {
        return switch (parameter.type()) {
            case INT -> IrExpression.intLocal(parameter.name());
            case LONG -> IrExpression.longLocal(parameter.name());
            case FLOAT -> IrExpression.floatLocal(parameter.name());
            case DOUBLE -> IrExpression.doubleLocal(parameter.name());
            case OBJECT -> IrExpression.objectLocal(parameter.name());
            case VOID -> throw new IllegalArgumentException("void parameter is invalid");
        };
    }

    private static Optional<IrType> fieldType(final String descriptor) {
        if ("B".equals(descriptor) || "C".equals(descriptor) || "I".equals(descriptor) || "S".equals(descriptor) || "Z".equals(descriptor)) {
            return Optional.of(IrType.INT);
        }
        if ("J".equals(descriptor)) {
            return Optional.of(IrType.LONG);
        }
        if ("F".equals(descriptor)) {
            return Optional.of(IrType.FLOAT);
        }
        if ("D".equals(descriptor)) {
            return Optional.of(IrType.DOUBLE);
        }
        if (descriptor.startsWith("L") || descriptor.startsWith("[")) {
            return Optional.of(IrType.OBJECT);
        }
        return Optional.empty();
    }

    private static void lowerInstruction(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        switch (instruction.opcode()) {
            case 1 -> stack.add(StackValue.objectExpression(IrExpression.objectNull()));
            case 177 -> instructions.add(IrInstruction.returnVoid());
            case 172 -> instructions.add(IrInstruction.returnInt(popInt(classFile, method, stack)));
            case 173 -> instructions.add(IrInstruction.returnLong(popLong(classFile, method, stack)));
            case 174 -> instructions.add(IrInstruction.returnFloat(popFloat(classFile, method, stack)));
            case 175 -> instructions.add(IrInstruction.returnDouble(popDouble(classFile, method, stack)));
            case 176 -> instructions.add(IrInstruction.returnObject(popObject(classFile, method, stack)));
            case 2, 3, 4, 5, 6, 7, 8 -> stack.add(StackValue.intExpression(IrExpression.intLiteral(instruction.opcode() - 3)));
            case 9, 10 -> stack.add(StackValue.longExpression(IrExpression.longLiteral(instruction.opcode() - 9L)));
            case 11, 12, 13 -> stack.add(StackValue.floatExpression(IrExpression.floatLiteral(instruction.opcode() - 11.0f)));
            case 14, 15 -> stack.add(StackValue.doubleExpression(IrExpression.doubleLiteral(instruction.opcode() - 14.0)));
            case 16 -> stack.add(StackValue.intExpression(IrExpression.intLiteral(signedByte(instruction.operands()[0]))));
            case 17 -> stack.add(StackValue.intExpression(IrExpression.intLiteral(signedShort(instruction.operands()))));
            case 21 -> stack.add(StackValue.intExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.INT)));
            case 22 -> stack.add(StackValue.longExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.LONG)));
            case 23 -> stack.add(StackValue.floatExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.FLOAT)));
            case 24 -> stack.add(StackValue.doubleExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.DOUBLE)));
            case 26, 27, 28, 29 -> stack.add(StackValue.intExpression(local(classFile, method, locals, instruction.opcode() - 26, IrType.INT)));
            case 30, 31, 32, 33 -> stack.add(StackValue.longExpression(local(classFile, method, locals, instruction.opcode() - 30, IrType.LONG)));
            case 34, 35, 36, 37 -> stack.add(StackValue.floatExpression(local(classFile, method, locals, instruction.opcode() - 34, IrType.FLOAT)));
            case 38, 39, 40, 41 -> stack.add(StackValue.doubleExpression(local(classFile, method, locals, instruction.opcode() - 38, IrType.DOUBLE)));
            case 25 -> stack.add(StackValue.objectExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.OBJECT)));
            case 42, 43, 44, 45 -> stack.add(StackValue.objectExpression(local(classFile, method, locals, instruction.opcode() - 42, IrType.OBJECT)));
            case 46 -> loadIntArray(classFile, method, stack);
            case 47 -> loadLongArray(classFile, method, stack);
            case 48 -> loadFloatArray(classFile, method, stack);
            case 49 -> loadDoubleArray(classFile, method, stack);
            case 51 -> loadByteArray(classFile, method, stack);
            case 52 -> loadCharArray(classFile, method, stack);
            case 53 -> loadShortArray(classFile, method, stack);
            case 50 -> loadObjectArray(classFile, method, stack);
            case 54 -> storeInt(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
            case 55 -> storeLong(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
            case 56 -> storeFloat(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
            case 57 -> storeDouble(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
            case 59, 60, 61, 62 -> storeInt(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 59);
            case 63, 64, 65, 66 -> storeLong(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 63);
            case 67, 68, 69, 70 -> storeFloat(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 67);
            case 71, 72, 73, 74 -> storeDouble(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 71);
            case 58 -> storeObject(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
            case 75, 76, 77, 78 -> storeObject(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 75);
            case 79 -> storeIntArray(classFile, method, instructions, stack);
            case 80 -> storeLongArray(classFile, method, instructions, stack);
            case 81 -> storeFloatArray(classFile, method, instructions, stack);
            case 82 -> storeDoubleArray(classFile, method, instructions, stack);
            case 84 -> storeByteArray(classFile, method, instructions, stack);
            case 85 -> storeCharArray(classFile, method, instructions, stack);
            case 86 -> storeShortArray(classFile, method, instructions, stack);
            case 83 -> storeObjectArray(classFile, method, instructions, stack);
            case 89 -> stack.add(stack.getLast());
            case 87 -> pop(stack);
            case 96 -> binaryInt(classFile, method, stack, "+");
            case 97 -> binaryLong(classFile, method, stack, "+");
            case 98 -> binaryFloat(classFile, method, stack, "+");
            case 99 -> binaryDouble(classFile, method, stack, "+");
            case 100 -> binaryInt(classFile, method, stack, "-");
            case 101 -> binaryLong(classFile, method, stack, "-");
            case 102 -> binaryFloat(classFile, method, stack, "-");
            case 103 -> binaryDouble(classFile, method, stack, "-");
            case 104 -> binaryInt(classFile, method, stack, "*");
            case 105 -> binaryLong(classFile, method, stack, "*");
            case 106 -> binaryFloat(classFile, method, stack, "*");
            case 107 -> binaryDouble(classFile, method, stack, "*");
            case 108 -> binaryInt(classFile, method, stack, "/");
            case 109 -> binaryLong(classFile, method, stack, "/");
            case 110 -> binaryFloat(classFile, method, stack, "/");
            case 111 -> binaryDouble(classFile, method, stack, "/");
            case 112 -> binaryInt(classFile, method, stack, "%");
            case 113 -> binaryLong(classFile, method, stack, "%");
            case 118 -> unaryFloatNeg(classFile, method, stack);
            case 119 -> unaryDoubleNeg(classFile, method, stack);
            case 132 -> incrementInt(classFile, method, instructions, locals, localDeclarations, instruction);
            case 149 -> compareFloat(classFile, method, stack, -1);
            case 150 -> compareFloat(classFile, method, stack, 1);
            case 151 -> compareDouble(classFile, method, stack, -1);
            case 152 -> compareDouble(classFile, method, stack, 1);
            case 153, 154, 155, 156, 157, 158 -> branchZero(classFile, method, instruction, instructions, stack);
            case 159, 160, 161, 162, 163, 164 -> branchIntCompare(classFile, method, instruction, instructions, stack);
            case 167 -> instructions.add(IrInstruction.jump(label(branchTarget(instruction))));
            case 198, 199 -> branchObjectNull(classFile, method, instruction, instructions, stack);
            case 178 -> pushField(classes, classFile, method, instruction, stack);
            case 179 -> assignStaticField(classes, classFile, method, instruction, instructions, stack);
            case 18, 19, 20 -> pushConstant(classFile, method, instruction, stack);
            case 180 -> pushInstanceField(classFile, method, instruction, stack);
            case 181 -> assignInstanceField(classFile, method, instruction, instructions, stack);
            case 182 -> lowerVirtualCall(classes, classFile, method, instruction, instructions, stack, dispatches);
            case 183 -> lowerInstanceCall(classes, classFile, method, instruction, instructions, stack);
            case 184 -> lowerStaticCall(classes, classFile, method, instruction, instructions, stack, localDeclarations);
            case 185 -> lowerInterfaceCall(classes, classFile, method, instruction, instructions, stack, dispatches);
            case 186 -> lowerDynamicCall(classFile, method, instruction, stack);
            case 187 -> newObject(classes, classFile, method, instruction, instructions, stack, localDeclarations);
            case 188 -> newPrimitiveArray(classFile, method, instruction, instructions, stack, localDeclarations);
            case 189 -> newObjectArray(classFile, method, instructions, stack, localDeclarations);
            case 190 -> arrayLength(classFile, method, stack);
            case 191 -> lowerThrow(classFile, method, instruction, instructions, stack);
            case 192 -> {
                // checkcast is a verifier/runtime type check; exact supported code keeps the reference unchanged.
            }
            default -> {
                if (!isIgnoredNoop(instruction.opcode())) {
                    throw unsupported(classFile, method, instruction);
                }
            }
        }
    }

    private static void pushField(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        if ("java/lang/System".equals(fieldRef.owner()) && "out".equals(fieldRef.name())) {
            stack.add(StackValue.printStream());
            return;
        }
        if (isEnumConstant(classes, fieldRef)) {
            stack.add(StackValue.objectExpression(IrExpression.stringLiteral(fieldRef.name())));
            return;
        }
        final Optional<IrType> type = staticFieldType(classes, fieldRef);
        if (type.isPresent() && type.orElseThrow() == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        if (type.isPresent() && type.orElseThrow() == IrType.LONG) {
            stack.add(StackValue.longExpression(IrExpression.longStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        if (type.isPresent() && type.orElseThrow() == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(IrExpression.floatStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        if (type.isPresent() && type.orElseThrow() == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        if (type.isPresent() && type.orElseThrow() == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void assignStaticField(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        final IrType type = staticFieldType(classes, fieldRef).orElseThrow(() -> unsupported(classFile, method, instruction));
        if (type == IrType.INT) {
            instructions.add(IrInstruction.assignStaticFieldInt(fieldRef.owner(), fieldRef.name(), popInt(classFile, method, stack)));
            return;
        }
        if (type == IrType.LONG) {
            instructions.add(IrInstruction.assignStaticFieldLong(fieldRef.owner(), fieldRef.name(), popLong(classFile, method, stack)));
            return;
        }
        if (type == IrType.FLOAT) {
            instructions.add(IrInstruction.assignStaticFieldFloat(fieldRef.owner(), fieldRef.name(), popFloat(classFile, method, stack)));
            return;
        }
        if (type == IrType.DOUBLE) {
            instructions.add(IrInstruction.assignStaticFieldDouble(fieldRef.owner(), fieldRef.name(), popDouble(classFile, method, stack)));
            return;
        }
        if (type == IrType.OBJECT) {
            instructions.add(IrInstruction.assignStaticFieldObject(fieldRef.owner(), fieldRef.name(), popObject(classFile, method, stack)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void pushInstanceField(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        final IrType type = fieldType(fieldRef.descriptor()).orElseThrow(() -> unsupported(classFile, method, instruction));
        final IrExpression receiver = popObject(classFile, method, stack);
        if (type == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intField(fieldRef.owner(), fieldRef.name(), receiver)));
            return;
        }
        if (type == IrType.LONG) {
            stack.add(StackValue.longExpression(IrExpression.longField(fieldRef.owner(), fieldRef.name(), receiver)));
            return;
        }
        if (type == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(IrExpression.floatField(fieldRef.owner(), fieldRef.name(), receiver)));
            return;
        }
        if (type == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleField(fieldRef.owner(), fieldRef.name(), receiver)));
            return;
        }
        if (type == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectField(fieldRef.owner(), fieldRef.name(), receiver)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void assignInstanceField(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        final IrType type = fieldType(fieldRef.descriptor()).orElseThrow(() -> unsupported(classFile, method, instruction));
        if (type == IrType.INT) {
            final IrExpression value = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.assignFieldInt(fieldRef.owner(), fieldRef.name(), receiver, value));
            return;
        }
        if (type == IrType.LONG) {
            final IrExpression value = popLong(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.assignFieldLong(fieldRef.owner(), fieldRef.name(), receiver, value));
            return;
        }
        if (type == IrType.FLOAT) {
            final IrExpression value = popFloat(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.assignFieldFloat(fieldRef.owner(), fieldRef.name(), receiver, value));
            return;
        }
        if (type == IrType.DOUBLE) {
            final IrExpression value = popDouble(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.assignFieldDouble(fieldRef.owner(), fieldRef.name(), receiver, value));
            return;
        }
        if (type == IrType.OBJECT) {
            final IrExpression value = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.assignFieldObject(fieldRef.owner(), fieldRef.name(), receiver, value));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerVirtualCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<String, IrDispatch> dispatches
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popObject(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnObject(arg));
                return;
            }
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popObject(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnObject(arg));
                return;
            }
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popInt(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnInt(arg));
                return;
            }
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(J)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popLong(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnLong(arg));
                return;
            }
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(F)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popFloat(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnFloat(arg));
                return;
            }
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(D)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popDouble(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnDouble(arg));
                return;
            }
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "println".equals(methodRef.name())
            && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popInt(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printlnBoolean(arg));
                return;
            }
        }
        if (isEnumIntrinsic(classes, methodRef)) {
            stack.add(StackValue.objectExpression(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "length".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_length", List.of(popObject(classFile, method, stack)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_is_empty", List.of(popObject(classFile, method, stack)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "charAt".equals(methodRef.name()) && "(I)C".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_char_at", List.of(receiver, index))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_equals", List.of(receiver, argument))));
            return;
        }
        if (isPlatformThrowableGetMessage(methodRef)) {
            stack.add(StackValue.objectExpression(popObject(classFile, method, stack)));
            return;
        }
        if (isConcreteExactCallTarget(classes, methodRef.owner())) {
            lowerInstanceCall(classes, classFile, method, instruction, instructions, stack);
            return;
        }
        final List<EntryPoint> targets = virtualTargets(classes, methodRef);
        if (!targets.isEmpty()) {
            lowerDispatchCall(classFile, method, instruction, instructions, stack, dispatches, methodRef, targets);
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        if (isPlatformThrowableStringConstructor(methodRef)) {
            updatePendingThrowableMessage(stack, arguments.getFirst());
            return;
        }
        if (isPlatformThrowableDefaultConstructor(methodRef)) {
            return;
        }
        if (isNoopPlatformConstructor(methodRef)) {
            return;
        }
        if (!classes.containsKey(methodRef.owner())) {
            throw unsupported(classFile, method, instruction);
        }
        arguments.addFirst(receiver);
        final String symbol = symbol(new EntryPoint(methodRef.owner(), methodRef.name(), methodRef.descriptor()));
        if (descriptor.returnType() == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(symbol, arguments));
            return;
        }
        if (descriptor.returnType() == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.LONG) {
            stack.add(StackValue.longExpression(IrExpression.longCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerStaticCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (lowerJdkStaticIntrinsic(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (!classes.containsKey(methodRef.owner())) {
            throw unsupported(classFile, method, instruction);
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = popArguments(classFile, method, stack, descriptor);
        final String symbol = symbol(new EntryPoint(methodRef.owner(), methodRef.name(), methodRef.descriptor()));
        if (descriptor.returnType() == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(symbol, arguments));
            return;
        }
        if (descriptor.returnType() == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.LONG) {
            stack.add(StackValue.longExpression(IrExpression.longCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static boolean lowerJdkStaticIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/lang/Math".equals(methodRef.owner())) {
            return lowerMathIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/System".equals(methodRef.owner())) {
            return lowerSystemIntrinsic(classFile, method, methodRef, instructions, stack);
        }
        if ("java/util/Objects".equals(methodRef.owner())) {
            return lowerObjectsIntrinsic(classFile, method, methodRef, instructions, stack, localDeclarations);
        }
        if ("java/util/Arrays".equals(methodRef.owner())) {
            return lowerArraysIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Integer".equals(methodRef.owner())) {
            return lowerIntegerIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Long".equals(methodRef.owner())) {
            return lowerLongIntrinsic(classFile, method, methodRef, stack);
        }
        return false;
    }

    private static boolean lowerMathIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("abs".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_math_abs_int", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("abs".equals(methodRef.name()) && "(J)J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_math_abs_long", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        if ("min".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_math_min_int", List.of(left, right))));
            return true;
        }
        if ("min".equals(methodRef.name()) && "(JJ)J".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_math_min_long", List.of(left, right))));
            return true;
        }
        if ("max".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_math_max_int", List.of(left, right))));
            return true;
        }
        if ("max".equals(methodRef.name()) && "(JJ)J".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_math_max_long", List.of(left, right))));
            return true;
        }
        return false;
    }

    private static boolean lowerIntegerIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    private static boolean lowerLongIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(J)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_long", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    private static boolean lowerSystemIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        if ("nanoTime".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_system_nano_time", List.of())));
            return true;
        }
        if ("currentTimeMillis".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_system_current_time_millis", List.of())));
            return true;
        }
        if ("arraycopy".equals(methodRef.name()) && "(Ljava/lang/Object;ILjava/lang/Object;II)V".equals(methodRef.descriptor())) {
            final IrExpression length = popInt(classFile, method, stack);
            final IrExpression targetPosition = popInt(classFile, method, stack);
            final IrExpression target = popObject(classFile, method, stack);
            final IrExpression sourcePosition = popInt(classFile, method, stack);
            final IrExpression source = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_system_arraycopy",
                List.of(source, sourcePosition, target, targetPosition, length)
            ));
            return true;
        }
        return false;
    }

    private static boolean lowerArraysIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"copyOf".equals(methodRef.name())) {
            return false;
        }
        final Optional<String> symbol = arraysCopyOfSymbol(methodRef.descriptor());
        if (symbol.isEmpty()) {
            return false;
        }
        final IrExpression newLength = popInt(classFile, method, stack);
        final IrExpression source = popObject(classFile, method, stack);
        stack.add(StackValue.objectExpression(IrExpression.objectCall(symbol.orElseThrow(), List.of(source, newLength))));
        return true;
    }

    private static Optional<String> arraysCopyOfSymbol(final String descriptor) {
        return switch (descriptor) {
            case "([II)[I" -> Optional.of("javan_arrays_copy_of_int");
            case "([JI)[J" -> Optional.of("javan_arrays_copy_of_long");
            case "([BI)[B" -> Optional.of("javan_arrays_copy_of_byte");
            case "([SI)[S" -> Optional.of("javan_arrays_copy_of_short");
            case "([CI)[C" -> Optional.of("javan_arrays_copy_of_char");
            case "([FI)[F" -> Optional.of("javan_arrays_copy_of_float");
            case "([DI)[D" -> Optional.of("javan_arrays_copy_of_double");
            case "([Ljava/lang/Object;I)[Ljava/lang/Object;" -> Optional.of("javan_arrays_copy_of_object");
            default -> Optional.empty();
        };
    }

    private static boolean lowerObjectsIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("requireNonNull".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression value = popObject(classFile, method, stack);
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, value));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(local)));
            stack.add(StackValue.objectExpression(local));
            return true;
        }
        return false;
    }

    private static void lowerInterfaceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<String, IrDispatch> dispatches
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        final List<EntryPoint> targets = interfaceTargets(classes, methodRef);
        if (targets.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        if (targets.size() > 1) {
            lowerDispatchCall(classFile, method, instruction, instructions, stack, dispatches, methodRef, targets);
            return;
        }
        final EntryPoint target = targets.getFirst();
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        arguments.addFirst(receiver);
        final String symbol = symbol(target);
        if (descriptor.returnType() == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(symbol, arguments));
            return;
        }
        if (descriptor.returnType() == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.LONG) {
            stack.add(StackValue.longExpression(IrExpression.longCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(symbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerDispatchCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<String, IrDispatch> dispatches,
        final MethodRef methodRef,
        final List<EntryPoint> targets
    ) {
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        arguments.addFirst(receiver);
        final String dispatchSymbol = dispatchSymbol(methodRef);
        dispatches.putIfAbsent(dispatchSymbol, dispatch(dispatchSymbol, descriptor, targets));
        if (descriptor.returnType() == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(dispatchSymbol, arguments));
            return;
        }
        if (descriptor.returnType() == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intCall(dispatchSymbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.LONG) {
            stack.add(StackValue.longExpression(IrExpression.longCall(dispatchSymbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall(dispatchSymbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(dispatchSymbol, arguments)));
            return;
        }
        if (descriptor.returnType() == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(dispatchSymbol, arguments)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static IrDispatch dispatch(final String symbol, final MethodDescriptor descriptor, final List<EntryPoint> targets) {
        final List<IrParameter> parameters = new ArrayList<>();
        parameters.add(new IrParameter(IrType.OBJECT, "self"));
        for (int index = 0; index < descriptor.parameterTypes().size(); index++) {
            parameters.add(new IrParameter(descriptor.parameterTypes().get(index), "arg" + index));
        }
        final List<IrDispatchTarget> dispatchTargets = targets.stream()
            .sorted(java.util.Comparator.comparing(EntryPoint::className))
            .map(target -> new IrDispatchTarget(target.className(), symbol(target)))
            .toList();
        return new IrDispatch(symbol, descriptor.returnType(), List.copyOf(parameters), dispatchTargets);
    }

    private static List<IrExpression> popArguments(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final MethodDescriptor descriptor
    ) {
        final List<IrExpression> arguments = new ArrayList<>();
        for (int index = descriptor.parameterTypes().size() - 1; index >= 0; index--) {
            final IrType type = descriptor.parameterTypes().get(index);
            arguments.addFirst(popValue(classFile, method, stack, type));
        }
        return List.copyOf(arguments);
    }

    private static void lowerDynamicCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow(() -> unsupported(classFile, method, instruction));
        if (!isSupportedStringConcat(dynamicRef)) {
            throw unsupported(classFile, method, instruction);
        }
        final List<String> parameterDescriptors = parameterDescriptors(dynamicRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>();
        for (int index = parameterDescriptors.size() - 1; index >= 0; index--) {
            arguments.addFirst(popStringConcatArgument(classFile, method, stack, parameterDescriptors.get(index)));
        }
        final String recipe = stringConcatRecipe(dynamicRef, arguments.size()).orElseThrow(() -> unsupported(classFile, method, instruction));
        stack.add(StackValue.objectExpression(IrExpression.stringConcat(recipe, arguments)));
    }

    private static IrExpression popStringConcatArgument(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String descriptor
    ) {
        return switch (descriptor.charAt(0)) {
            case 'B', 'I', 'S' -> IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)));
            case 'C' -> IrExpression.objectCall("javan_string_value_of_char", List.of(popInt(classFile, method, stack)));
            case 'Z' -> IrExpression.objectCall("javan_string_value_of_bool", List.of(popInt(classFile, method, stack)));
            case 'J' -> IrExpression.objectCall("javan_string_value_of_long", List.of(popLong(classFile, method, stack)));
            case 'F' -> IrExpression.objectCall("javan_string_value_of_float", List.of(popFloat(classFile, method, stack)));
            case 'D' -> IrExpression.objectCall("javan_string_value_of_double", List.of(popDouble(classFile, method, stack)));
            case 'L', '[' -> popObject(classFile, method, stack);
            default -> throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
        };
    }

    private static boolean isSupportedStringConcat(final DynamicRef dynamicRef) {
        if (!"java/lang/invoke/StringConcatFactory".equals(dynamicRef.bootstrapOwner())) {
            return false;
        }
        final int returnStart = dynamicRef.descriptor().indexOf(')');
        if (returnStart < 0 || !"Ljava/lang/String;".equals(dynamicRef.descriptor().substring(returnStart + 1))) {
            return false;
        }
        return "makeConcat".equals(dynamicRef.bootstrapName()) || "makeConcatWithConstants".equals(dynamicRef.bootstrapName());
    }

    private static Optional<String> stringConcatRecipe(final DynamicRef dynamicRef, final int argumentCount) {
        if ("makeConcat".equals(dynamicRef.bootstrapName())) {
            return Optional.of("\u0001".repeat(argumentCount));
        }
        if (!"makeConcatWithConstants".equals(dynamicRef.bootstrapName()) || dynamicRef.bootstrapArguments().isEmpty()) {
            return Optional.empty();
        }
        final String recipe = dynamicRef.bootstrapArguments().getFirst();
        return recipe.indexOf(2) < 0 ? Optional.of(recipe) : Optional.empty();
    }

    private static List<String> parameterDescriptors(final String descriptor) {
        if (!descriptor.startsWith("(")) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
        final List<String> result = new ArrayList<>();
        int index = 1;
        while (descriptor.charAt(index) != ')') {
            final int start = index;
            final char type = descriptor.charAt(index);
            if ("BCDFIJSZ".indexOf(type) >= 0) {
                result.add(descriptor.substring(start, start + 1));
                index++;
            } else if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
                }
                result.add(descriptor.substring(start, end + 1));
                index = end + 1;
            } else if (type == '[') {
                index = skipParameterArrayDescriptor(descriptor, index);
                result.add(descriptor.substring(start, index));
            } else {
                throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
            }
        }
        return List.copyOf(result);
    }

    private static int skipParameterArrayDescriptor(final String descriptor, final int start) {
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index >= descriptor.length()) {
            throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
        }
        if ("BCDFIJSZ".indexOf(descriptor.charAt(index)) >= 0) {
            return index + 1;
        }
        if (descriptor.charAt(index) == 'L') {
            final int end = descriptor.indexOf(';', index);
            if (end < 0) {
                throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
            }
            return end + 1;
        }
        throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
    }

    private static IrExpression popValue(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final IrType type
    ) {
        return switch (type) {
            case INT -> popInt(classFile, method, stack);
            case LONG -> popLong(classFile, method, stack);
            case FLOAT -> popFloat(classFile, method, stack);
            case DOUBLE -> popDouble(classFile, method, stack);
            case OBJECT -> popObject(classFile, method, stack);
            case VOID -> throw new DiagnosticException(Diagnostic.error(
                "JAVAN041",
                "unsupported call argument type",
                classFile.name(),
                method.name() + method.descriptor(),
                type.name(),
                "Void is not a valid call argument.",
                "Use value-carrying parameters only."
            ));
        };
    }

    private static void pushConstant(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (instruction.stringValue().isPresent()) {
            stack.add(StackValue.objectExpression(IrExpression.stringLiteral(instruction.stringValue().orElseThrow())));
            return;
        }
        if (instruction.intValue().isPresent()) {
            stack.add(StackValue.intExpression(IrExpression.intLiteral(instruction.intValue().orElseThrow())));
            return;
        }
        if (instruction.longValue().isPresent()) {
            stack.add(StackValue.longExpression(IrExpression.longLiteral(instruction.longValue().orElseThrow())));
            return;
        }
        if (instruction.floatValue().isPresent()) {
            stack.add(StackValue.floatExpression(IrExpression.floatLiteral(instruction.floatValue().orElseThrow())));
            return;
        }
        if (instruction.doubleValue().isPresent()) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleLiteral(instruction.doubleValue().orElseThrow())));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void binaryInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popInt(classFile, method, stack);
        final IrExpression left = popInt(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intBinary(operator, left, right)));
    }

    private static void binaryLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popLong(classFile, method, stack);
        final IrExpression left = popLong(classFile, method, stack);
        stack.add(StackValue.longExpression(IrExpression.longBinary(operator, left, right)));
    }

    private static void binaryFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popFloat(classFile, method, stack);
        final IrExpression left = popFloat(classFile, method, stack);
        stack.add(StackValue.floatExpression(IrExpression.floatBinary(operator, left, right)));
    }

    private static void binaryDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popDouble(classFile, method, stack);
        final IrExpression left = popDouble(classFile, method, stack);
        stack.add(StackValue.doubleExpression(IrExpression.doubleBinary(operator, left, right)));
    }

    private static void unaryFloatNeg(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.floatExpression(IrExpression.floatBinary(
            "-",
            IrExpression.floatLiteral(0.0f),
            popFloat(classFile, method, stack)
        )));
    }

    private static void unaryDoubleNeg(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.doubleExpression(IrExpression.doubleBinary(
            "-",
            IrExpression.doubleLiteral(0.0),
            popDouble(classFile, method, stack)
        )));
    }

    private static void compareFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final int nanValue
    ) {
        final IrExpression right = popFloat(classFile, method, stack);
        final IrExpression left = popFloat(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_float_compare",
            List.of(left, right, IrExpression.intLiteral(nanValue))
        )));
    }

    private static void compareDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final int nanValue
    ) {
        final IrExpression right = popDouble(classFile, method, stack);
        final IrExpression left = popDouble(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_double_compare",
            List.of(left, right, IrExpression.intLiteral(nanValue))
        )));
    }

    private static void storeInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.INT);
        instructions.add(IrInstruction.assignInt(target.value(), value));
    }

    private static void storeLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popLong(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.LONG);
        instructions.add(IrInstruction.assignLong(target.value(), value));
    }

    private static void storeFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popFloat(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.FLOAT);
        instructions.add(IrInstruction.assignFloat(target.value(), value));
    }

    private static void storeDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popDouble(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.DOUBLE);
        instructions.add(IrInstruction.assignDouble(target.value(), value));
    }

    private static void storeObject(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popObject(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(target.value(), value));
    }

    private static void newObjectArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final IrExpression length = popInt(classFile, method, stack);
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression local = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectArrayAllocation(length)));
        stack.add(StackValue.objectExpression(local));
    }

    private static void newPrimitiveArray(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (instruction.operands().length == 0) {
            throw unsupported(classFile, method, instruction);
        }
        final IrExpression length = popInt(classFile, method, stack);
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression local = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, primitiveArrayAllocation(classFile, method, instruction, length)));
        stack.add(StackValue.objectExpression(local));
    }

    private static IrExpression primitiveArrayAllocation(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final IrExpression length
    ) {
        return switch (unsigned(instruction.operands()[0])) {
            case 4 -> IrExpression.booleanArrayAllocation(length);
            case 8 -> IrExpression.byteArrayAllocation(length);
            case 5 -> IrExpression.charArrayAllocation(length);
            case 6 -> IrExpression.floatArrayAllocation(length);
            case 7 -> IrExpression.doubleArrayAllocation(length);
            case 9 -> IrExpression.shortArrayAllocation(length);
            case 10 -> IrExpression.intArrayAllocation(length);
            case 11 -> IrExpression.longArrayAllocation(length);
            default -> throw unsupported(classFile, method, instruction);
        };
    }

    private static void loadObjectArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.objectExpression(IrExpression.objectArrayLoad(array, index)));
    }

    private static void loadIntArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intArrayLoad(array, index)));
    }

    private static void loadLongArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.longExpression(IrExpression.longArrayLoad(array, index)));
    }

    private static void loadFloatArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.floatExpression(IrExpression.floatArrayLoad(array, index)));
    }

    private static void loadDoubleArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.doubleExpression(IrExpression.doubleArrayLoad(array, index)));
    }

    private static void loadByteArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.byteArrayLoad(array, index)));
    }

    private static void loadShortArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.shortArrayLoad(array, index)));
    }

    private static void loadCharArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.charArrayLoad(array, index)));
    }

    private static void storeObjectArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popObject(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayObject(array, index, value));
    }

    private static void storeIntArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayInt(array, index, value));
    }

    private static void storeLongArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popLong(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayLong(array, index, value));
    }

    private static void storeFloatArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popFloat(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayFloat(array, index, value));
    }

    private static void storeDoubleArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popDouble(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayDouble(array, index, value));
    }

    private static void storeByteArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayByte(array, index, value));
    }

    private static void storeShortArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayShort(array, index, value));
    }

    private static void storeCharArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        instructions.add(IrInstruction.assignArrayChar(array, index, value));
    }

    private static void arrayLength(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        stack.add(StackValue.intExpression(IrExpression.arrayLength(popObject(classFile, method, stack))));
    }

    private static void lowerThrow(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression thrown = popObject(classFile, method, stack);
        final Optional<Integer> handler = exceptionHandler(method, instruction.offset());
        if (handler.isPresent()) {
            stack.add(StackValue.objectExpression(thrown));
            instructions.add(IrInstruction.jump(label(handler.orElseThrow())));
            return;
        }
        instructions.add(IrInstruction.panic(thrown));
    }

    private static Optional<Integer> exceptionHandler(final MethodInfo method, final int offset) {
        return method.code().orElseThrow().exceptionTable().stream()
            .filter(handler -> offset >= handler.startPc() && offset < handler.endPc())
            .map(javan.classfile.CodeException::handlerPc)
            .findFirst();
    }

    private static void newObject(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final String owner = instruction.className().orElseThrow();
        if (isPlatformThrowable(owner)) {
            stack.add(StackValue.objectExpression(IrExpression.stringLiteral(owner)));
            return;
        }
        if (!classes.containsKey(owner)) {
            throw unsupported(classFile, method, instruction);
        }
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression local = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectAllocation(owner)));
        stack.add(StackValue.objectExpression(local));
    }

    private static void incrementInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final Instruction instruction
    ) {
        final int slot = unsigned(instruction.operands()[0]);
        final int amount = signedByte(instruction.operands()[1]);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.INT);
        instructions.add(IrInstruction.assignInt(
            target.value(),
            IrExpression.intBinary("+", target, IrExpression.intLiteral(amount))
        ));
    }

    private static void branchZero(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        instructions.add(IrInstruction.branchIf(
            label(branchTarget(instruction)),
            IrExpression.intComparison(zeroOperator(instruction.opcode()), value, IrExpression.intLiteral(0))
        ));
    }

    private static void branchIntCompare(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression right = popInt(classFile, method, stack);
        final IrExpression left = popInt(classFile, method, stack);
        instructions.add(IrInstruction.branchIf(
            label(branchTarget(instruction)),
            IrExpression.intComparison(intCompareOperator(instruction.opcode()), left, right)
        ));
    }

    private static void branchObjectNull(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression value = popObject(classFile, method, stack);
        instructions.add(IrInstruction.branchIf(
            label(branchTarget(instruction)),
            IrExpression.objectComparison(nullOperator(instruction.opcode()), value, IrExpression.objectNull())
        ));
    }

    private static IrExpression popInt(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.INT) {
            return value.expression().orElseThrow();
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static IrExpression popObject(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.OBJECT) {
            return value.expression().orElseThrow();
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static IrExpression popLong(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.LONG) {
            return value.expression().orElseThrow();
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static IrExpression popFloat(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.FLOAT) {
            return value.expression().orElseThrow();
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static IrExpression popDouble(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.DOUBLE) {
            return value.expression().orElseThrow();
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static IrExpression local(
        final ClassFile classFile,
        final MethodInfo method,
        final Map<Integer, IrExpression> locals,
        final int slot,
        final IrType type
    ) {
        final IrExpression value = locals.get(slot);
        if (value != null && value.type() == type) {
            return value;
        }
        throw new DiagnosticException(Diagnostic.error(
            "JAVAN042",
            "unsupported or uninitialized local variable",
            classFile.name(),
                method.name() + method.descriptor(),
                "slot " + slot,
                "The current backend only tracks initialized profile locals in supported reachable code.",
                "Keep local variables to supported primitive values and exact object references for this version."
            ));
    }

    private static IrExpression localOrCreate(
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot,
        final IrType type
    ) {
        final IrExpression existing = locals.get(slot);
        if (existing != null) {
            if (existing.type() != type) {
                throw new IllegalStateException("Local slot " + slot + " changes type from " + existing.type() + " to " + type);
            }
            return existing;
        }
        final IrLocal local = new IrLocal(type, "local" + slot);
        localDeclarations.put(slot, local);
        final IrExpression expression = switch (type) {
            case INT -> IrExpression.intLocal(local.name());
            case LONG -> IrExpression.longLocal(local.name());
            case FLOAT -> IrExpression.floatLocal(local.name());
            case DOUBLE -> IrExpression.doubleLocal(local.name());
            case OBJECT -> IrExpression.objectLocal(local.name());
            case VOID -> throw new IllegalArgumentException("void local is invalid");
        };
        locals.put(slot, expression);
        return expression;
    }

    private static StackValue pop(final List<StackValue> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Invalid bytecode stack");
        }
        return stack.removeLast();
    }

    private static boolean isIgnoredNoop(final int opcode) {
        return opcode == 0;
    }

    private static Set<Integer> branchTargets(final CodeAttribute code) {
        final Set<Integer> result = new HashSet<>();
        for (final Instruction instruction : code.instructions()) {
            if ((instruction.opcode() >= 153 && instruction.opcode() <= 167) || instruction.opcode() == 198 || instruction.opcode() == 199) {
                result.add(branchTarget(instruction));
            }
        }
        code.exceptionTable().stream()
            .map(javan.classfile.CodeException::handlerPc)
            .forEach(result::add);
        return Set.copyOf(result);
    }

    private static int branchTarget(final Instruction instruction) {
        return instruction.offset() + signedShort(instruction.operands());
    }

    private static String label(final int offset) {
        return "label_" + offset;
    }

    private static String zeroOperator(final int opcode) {
        return switch (opcode) {
            case 153 -> "==";
            case 154 -> "!=";
            case 155 -> "<";
            case 156 -> ">=";
            case 157 -> ">";
            case 158 -> "<=";
            default -> throw new IllegalArgumentException("Unsupported zero branch opcode " + opcode);
        };
    }

    private static String intCompareOperator(final int opcode) {
        return switch (opcode) {
            case 159 -> "==";
            case 160 -> "!=";
            case 161 -> "<";
            case 162 -> ">=";
            case 163 -> ">";
            case 164 -> "<=";
            default -> throw new IllegalArgumentException("Unsupported int compare opcode " + opcode);
        };
    }

    private static String nullOperator(final int opcode) {
        return switch (opcode) {
            case 198 -> "==";
            case 199 -> "!=";
            default -> throw new IllegalArgumentException("Unsupported null branch opcode " + opcode);
        };
    }

    private static int signedByte(final byte value) {
        return value;
    }

    private static int signedShort(final byte[] operands) {
        return (short) ((unsigned(operands[0]) << 8) | unsigned(operands[1]));
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static DiagnosticException unsupported(final ClassFile classFile, final MethodInfo method, final Instruction instruction) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN040",
            "bytecode is not implemented by native code generation",
            classFile.name(),
            method.name() + method.descriptor(),
            instruction.mnemonic(),
            "The verifier allowed the program shape, but this backend slice cannot emit C for this instruction yet.",
            "Keep reachable code to supported ints, exact object fields, constructors, and static/final-class calls for this version."
        ));
    }

    private static boolean isConcreteExactCallTarget(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile target = classes.get(owner);
        if (target == null || target.isInterface()) {
            return false;
        }
        return target.isFinal() || classes.values().stream().noneMatch(candidate -> owner.equals(candidate.superName()));
    }

    private static boolean isEnumConstant(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
        final ClassFile owner = classes.get(fieldRef.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        return owner.fields().stream()
            .anyMatch(field -> field.isEnumConstant()
                && field.name().equals(fieldRef.name())
                && field.descriptor().equals(fieldRef.descriptor()));
    }

    private static boolean isEnumIntrinsic(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final ClassFile owner = classes.get(methodRef.owner());
        return (("java/lang/Enum".equals(methodRef.owner()) || (owner != null && owner.isEnum()))
            && ("name".equals(methodRef.name()) || "toString".equals(methodRef.name()))
            && "()Ljava/lang/String;".equals(methodRef.descriptor()));
    }

    private static Optional<IrType> staticFieldType(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
        final ClassFile owner = classes.get(fieldRef.owner());
        if (owner == null) {
            return Optional.empty();
        }
        return owner.fields().stream()
            .filter(FieldInfo::isStatic)
            .filter(field -> field.name().equals(fieldRef.name()))
            .filter(field -> field.descriptor().equals(fieldRef.descriptor()))
            .findFirst()
            .flatMap(field -> fieldType(field.descriptor()));
    }

    private static List<EntryPoint> interfaceTargets(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        return classes.values().stream()
            .filter(candidate -> !candidate.isInterface())
            .filter(candidate -> candidate.interfaces().contains(methodRef.owner()))
            .filter(candidate -> candidate.method(methodRef.name(), methodRef.descriptor()).isPresent())
            .map(candidate -> new EntryPoint(candidate.name(), methodRef.name(), methodRef.descriptor()))
            .toList();
    }

    private static List<EntryPoint> virtualTargets(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        return classes.values().stream()
            .filter(candidate -> !candidate.isInterface())
            .filter(candidate -> isSubtypeOf(classes, candidate.name(), methodRef.owner()))
            .map(candidate -> resolvedVirtualTarget(classes, candidate.name(), methodRef))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    }

    private static Optional<EntryPoint> resolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef methodRef
    ) {
        String current = receiver;
        while (classes.containsKey(current)) {
            final ClassFile classFile = classes.get(current);
            if (classFile.method(methodRef.name(), methodRef.descriptor()).isPresent()) {
                return Optional.of(new EntryPoint(current, methodRef.name(), methodRef.descriptor()));
            }
            current = classFile.superName();
        }
        return Optional.empty();
    }

    private static boolean isSubtypeOf(final Map<String, ClassFile> classes, final String candidate, final String expectedSuper) {
        String current = candidate;
        while (classes.containsKey(current)) {
            if (current.equals(expectedSuper)) {
                return true;
            }
            current = classes.get(current).superName();
        }
        return current.equals(expectedSuper);
    }

    private static boolean isNoopPlatformConstructor(final MethodRef methodRef) {
        return "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor())
            && ("java/lang/Object".equals(methodRef.owner()) || "java/lang/Record".equals(methodRef.owner()));
    }

    private static boolean isPlatformThrowableStringConstructor(final MethodRef methodRef) {
        return "<init>".equals(methodRef.name())
            && "(Ljava/lang/String;)V".equals(methodRef.descriptor())
            && isPlatformThrowable(methodRef.owner());
    }

    private static boolean isPlatformThrowableDefaultConstructor(final MethodRef methodRef) {
        return "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor())
            && isPlatformThrowable(methodRef.owner());
    }

    private static boolean isPlatformThrowableGetMessage(final MethodRef methodRef) {
        return "getMessage".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())
            && isPlatformThrowable(methodRef.owner());
    }

    private static boolean isPlatformThrowable(final String owner) {
        return "java/lang/Throwable".equals(owner) || owner.endsWith("Exception") || owner.endsWith("Error");
    }

    private static void updatePendingThrowableMessage(final List<StackValue> stack, final IrExpression message) {
        if (!stack.isEmpty()) {
            stack.set(stack.size() - 1, StackValue.objectExpression(message));
        }
    }

    private static String classSymbol(final String className) {
        return "javan_class_" + sanitize(className);
    }

    private static String fieldSymbol(final String fieldName) {
        return "field_" + sanitize(fieldName);
    }

    /**
     * Returns a stable C symbol for a reachable method.
     *
     * @param entryPoint method identity
     * @return C symbol
     */
    public static String symbol(final EntryPoint entryPoint) {
        return "javan_" + (entryPoint.className() + "_" + entryPoint.methodName() + "_" + entryPoint.descriptor())
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }

    private static String dispatchSymbol(final MethodRef methodRef) {
        return "javan_dispatch_" + (methodRef.owner() + "_" + methodRef.name() + "_" + methodRef.descriptor())
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }

    private static String sanitize(final String value) {
        return value
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }

    private enum StackKind {
        PRINT_STREAM,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        OBJECT
    }

    private record StackValue(StackKind kind, Optional<String> value, Optional<IrExpression> expression) {
        static StackValue printStream() {
            return new StackValue(StackKind.PRINT_STREAM, Optional.empty(), Optional.empty());
        }

        static StackValue intExpression(final IrExpression expression) {
            return new StackValue(StackKind.INT, Optional.empty(), Optional.of(expression));
        }

        static StackValue longExpression(final IrExpression expression) {
            return new StackValue(StackKind.LONG, Optional.empty(), Optional.of(expression));
        }

        static StackValue floatExpression(final IrExpression expression) {
            return new StackValue(StackKind.FLOAT, Optional.empty(), Optional.of(expression));
        }

        static StackValue doubleExpression(final IrExpression expression) {
            return new StackValue(StackKind.DOUBLE, Optional.empty(), Optional.of(expression));
        }

        static StackValue objectExpression(final IrExpression expression) {
            return new StackValue(StackKind.OBJECT, Optional.empty(), Optional.of(expression));
        }
    }
}
