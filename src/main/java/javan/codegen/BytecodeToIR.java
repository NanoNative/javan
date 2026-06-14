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
import javan.compat.JdkCallSupport;
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
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lowers the initial supported bytecode subset to javan IR.
 */
public final class BytecodeToIR {
    private static final int TYPE_JAVA_LANG_INTEGER = -1001;
    private static final int TYPE_JAVA_LANG_LONG = -1002;
    private static final int TYPE_JAVA_LANG_FLOAT = -1003;
    private static final int TYPE_JAVA_LANG_DOUBLE = -1004;

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
        final List<EntryPoint> reachableMethods = sortedEntryPoints(callGraph.reachableMethods());
        for (final EntryPoint reachable : reachableMethods) {
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
        final Map<Integer, StackValue> pendingExceptionHandlerStacks = new HashMap<>();
        final CodeAttribute code = method.code().orElseThrow();
        final List<Instruction> bytecode = code.instructions();
        final List<Integer> ignoredHandlerOffsets = ignoredEnumSwitchMapHandlerOffsets(classes, classFile, method, code);
        final List<Integer> handlerOffsets = exceptionHandlerOffsets(code);
        final List<Integer> branchTargets = branchTargets(code);
        final List<Integer> skippedOffsets = new ArrayList<>();
        bindParameters(method, descriptor, parameters, locals);
        for (int index = 0; index < bytecode.size(); index++) {
            final Instruction instruction = bytecode.get(index);
            if (containsInt(branchTargets, instruction.offset())) {
                instructions.add(IrInstruction.label(label(instruction.offset())));
            }
            if (containsInt(handlerOffsets, instruction.offset())) {
                final StackValue pendingException = pendingExceptionHandlerStacks.get(instruction.offset());
                if (pendingException != null) {
                    clearStack(stack);
                    stack.add(pendingException);
                } else if (stack.isEmpty()) {
                    stack.add(StackValue.objectExpression(IrExpression.objectNull()));
                }
            }
            if (shouldSkipOffset(ignoredHandlerOffsets, skippedOffsets, instruction.offset())) {
                continue;
            }
            if (lowerBranchValueSelection(
                classes,
                classFile,
                method,
                bytecode,
                index,
                instructions,
                stack,
                locals,
                localDeclarations,
                dispatches,
                skippedOffsets
            )) {
                continue;
            }
            lowerInstruction(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                locals,
                localDeclarations,
                dispatches
            );
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
        final List<IrClass> result = new ArrayList<>();
        final List<ClassFile> sorted = sortedClasses(classes);
        for (final ClassFile classFile : sorted) {
            result.add(new IrClass(
                classFile.name(),
                classSymbol(classFile.name()),
                fields(classFile, false),
                fields(classFile, true),
                enumConstants(classFile)
            ));
        }
        return List.copyOf(result);
    }

    private static List<EntryPoint> sortedEntryPoints(final List<EntryPoint> entries) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final EntryPoint entry : entries) {
            int index = 0;
            final String value = symbol(entry);
            while (index < result.size() && Strings2.compareAscii(symbol(result.get(index)), value) <= 0) {
                index++;
            }
            result.add(index, entry);
        }
        return List.copyOf(result);
    }

    private static List<ClassFile> sortedClasses(final Map<String, ClassFile> classes) {
        final List<ClassFile> result = new ArrayList<>();
        for (final ClassFile classFile : classes.values()) {
            int index = 0;
            while (index < result.size() && Strings2.compareAscii(result.get(index).name(), classFile.name()) <= 0) {
                index++;
            }
            result.add(index, classFile);
        }
        return List.copyOf(result);
    }

    private static List<IrField> fields(final ClassFile classFile, final boolean statics) {
        final List<IrField> result = new ArrayList<>();
        for (final FieldInfo field : classFile.fields()) {
            if (field.isStatic() != statics) {
                continue;
            }
            final Optional<IrType> type = fieldType(field.descriptor());
            if (type.isPresent()) {
                result.add(new IrField(type.orElseThrow(), field.name(), fieldSymbol(field.name())));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> enumConstants(final ClassFile classFile) {
        final List<String> result = new ArrayList<>();
        for (final FieldInfo field : classFile.fields()) {
            if (field.isEnumConstant()) {
                result.add(field.name());
            }
        }
        return List.copyOf(result);
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
        if (parameter.type() == IrType.INT) {
            return IrExpression.intLocal(parameter.name());
        }
        if (parameter.type() == IrType.LONG) {
            return IrExpression.longLocal(parameter.name());
        }
        if (parameter.type() == IrType.FLOAT) {
            return IrExpression.floatLocal(parameter.name());
        }
        if (parameter.type() == IrType.DOUBLE) {
            return IrExpression.doubleLocal(parameter.name());
        }
        if (parameter.type() == IrType.OBJECT) {
            return IrExpression.objectLocal(parameter.name());
        }
        if (parameter.type() == IrType.VOID) {
            throw new IllegalArgumentException("void parameter is invalid");
        }
        throw new IllegalStateException("Unsupported IR type");
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
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        switch (instruction.opcode()) {
            case 1:
                stack.add(StackValue.objectExpression(IrExpression.objectNull()));
                break;
            case 177:
                instructions.add(IrInstruction.returnVoid());
                break;
            case 172:
                instructions.add(IrInstruction.returnInt(popInt(classFile, method, stack)));
                break;
            case 173:
                instructions.add(IrInstruction.returnLong(popLong(classFile, method, stack)));
                break;
            case 174:
                instructions.add(IrInstruction.returnFloat(popFloat(classFile, method, stack)));
                break;
            case 175:
                instructions.add(IrInstruction.returnDouble(popDouble(classFile, method, stack)));
                break;
            case 176:
                if (stack.isEmpty()) {
                    throw invalidStack(classFile, method, instruction, "An object return did not have a value on the bytecode stack.");
                }
                instructions.add(IrInstruction.returnObject(popObject(classFile, method, stack)));
                break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                stack.add(StackValue.intExpression(IrExpression.intLiteral(instruction.opcode() - 3)));
                break;
            case 9:
            case 10:
                stack.add(StackValue.longExpression(IrExpression.longLiteral(instruction.opcode() - 9L)));
                break;
            case 11:
            case 12:
            case 13:
                stack.add(StackValue.floatExpression(IrExpression.floatLiteral(instruction.opcode() - 11.0f)));
                break;
            case 14:
            case 15:
                stack.add(StackValue.doubleExpression(IrExpression.doubleLiteral(instruction.opcode() - 14.0)));
                break;
            case 16:
                stack.add(StackValue.intExpression(IrExpression.intLiteral(signedByte(instruction.operands()[0]))));
                break;
            case 17:
                stack.add(StackValue.intExpression(IrExpression.intLiteral(signedShort(instruction.operands()))));
                break;
            case 21:
                stack.add(StackValue.intExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.INT)));
                break;
            case 22:
                stack.add(StackValue.longExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.LONG)));
                break;
            case 23:
                stack.add(StackValue.floatExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.FLOAT)));
                break;
            case 24:
                stack.add(StackValue.doubleExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.DOUBLE)));
                break;
            case 26:
            case 27:
            case 28:
            case 29:
                stack.add(StackValue.intExpression(local(classFile, method, locals, instruction.opcode() - 26, IrType.INT)));
                break;
            case 30:
            case 31:
            case 32:
            case 33:
                stack.add(StackValue.longExpression(local(classFile, method, locals, instruction.opcode() - 30, IrType.LONG)));
                break;
            case 34:
            case 35:
            case 36:
            case 37:
                stack.add(StackValue.floatExpression(local(classFile, method, locals, instruction.opcode() - 34, IrType.FLOAT)));
                break;
            case 38:
            case 39:
            case 40:
            case 41:
                stack.add(StackValue.doubleExpression(local(classFile, method, locals, instruction.opcode() - 38, IrType.DOUBLE)));
                break;
            case 25:
                stack.add(StackValue.objectExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.OBJECT)));
                break;
            case 42:
            case 43:
            case 44:
            case 45:
                stack.add(StackValue.objectExpression(local(classFile, method, locals, instruction.opcode() - 42, IrType.OBJECT)));
                break;
            case 46:
                loadIntArray(classFile, method, stack);
                break;
            case 47:
                loadLongArray(classFile, method, stack);
                break;
            case 48:
                loadFloatArray(classFile, method, stack);
                break;
            case 49:
                loadDoubleArray(classFile, method, stack);
                break;
            case 51:
                loadByteArray(classFile, method, stack);
                break;
            case 52:
                loadCharArray(classFile, method, stack);
                break;
            case 53:
                loadShortArray(classFile, method, stack);
                break;
            case 50:
                loadObjectArray(classFile, method, stack);
                break;
            case 54:
                storeInt(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 55:
                storeLong(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 56:
                storeFloat(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 57:
                storeDouble(classFile, method, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 59:
            case 60:
            case 61:
            case 62:
                storeInt(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 59);
                break;
            case 63:
            case 64:
            case 65:
            case 66:
                storeLong(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 63);
                break;
            case 67:
            case 68:
            case 69:
            case 70:
                storeFloat(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 67);
                break;
            case 71:
            case 72:
            case 73:
            case 74:
                storeDouble(classFile, method, instructions, stack, locals, localDeclarations, instruction.opcode() - 71);
                break;
            case 58:
                storeObject(classFile, method, instruction, instructions, stack, locals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 75:
            case 76:
            case 77:
            case 78:
                storeObject(classFile, method, instruction, instructions, stack, locals, localDeclarations, instruction.opcode() - 75);
                break;
            case 79:
                storeIntArray(classFile, method, instructions, stack);
                break;
            case 80:
                storeLongArray(classFile, method, instructions, stack);
                break;
            case 81:
                storeFloatArray(classFile, method, instructions, stack);
                break;
            case 82:
                storeDoubleArray(classFile, method, instructions, stack);
                break;
            case 84:
                storeByteArray(classFile, method, instructions, stack);
                break;
            case 85:
                storeCharArray(classFile, method, instructions, stack);
                break;
            case 86:
                storeShortArray(classFile, method, instructions, stack);
                break;
            case 83:
                storeObjectArray(classFile, method, instructions, stack);
                break;
            case 89:
                stack.add(stack.getLast());
                break;
            case 87:
                if (!stack.isEmpty()) {
                    discardTop(instructions, stack);
                }
                break;
            case 96:
                binaryInt(classFile, method, stack, "+");
                break;
            case 97:
                binaryLong(classFile, method, stack, "+");
                break;
            case 98:
                binaryFloat(classFile, method, stack, "+");
                break;
            case 99:
                binaryDouble(classFile, method, stack, "+");
                break;
            case 100:
                binaryInt(classFile, method, stack, "-");
                break;
            case 101:
                binaryLong(classFile, method, stack, "-");
                break;
            case 102:
                binaryFloat(classFile, method, stack, "-");
                break;
            case 103:
                binaryDouble(classFile, method, stack, "-");
                break;
            case 104:
                binaryInt(classFile, method, stack, "*");
                break;
            case 105:
                binaryLong(classFile, method, stack, "*");
                break;
            case 106:
                binaryFloat(classFile, method, stack, "*");
                break;
            case 107:
                binaryDouble(classFile, method, stack, "*");
                break;
            case 108:
                binaryInt(classFile, method, stack, "/");
                break;
            case 109:
                binaryLong(classFile, method, stack, "/");
                break;
            case 110:
                binaryFloat(classFile, method, stack, "/");
                break;
            case 111:
                binaryDouble(classFile, method, stack, "/");
                break;
            case 112:
                binaryInt(classFile, method, stack, "%");
                break;
            case 113:
                binaryLong(classFile, method, stack, "%");
                break;
            case 120:
                shiftInt(classFile, method, stack, "javan_int_shl");
                break;
            case 121:
                shiftLong(classFile, method, stack, "javan_long_shl");
                break;
            case 122:
                shiftInt(classFile, method, stack, "javan_int_shr");
                break;
            case 123:
                shiftLong(classFile, method, stack, "javan_long_shr");
                break;
            case 124:
                shiftInt(classFile, method, stack, "javan_int_ushr");
                break;
            case 125:
                shiftLong(classFile, method, stack, "javan_long_ushr");
                break;
            case 126:
                binaryInt(classFile, method, stack, "&");
                break;
            case 127:
                binaryLong(classFile, method, stack, "&");
                break;
            case 128:
                binaryInt(classFile, method, stack, "|");
                break;
            case 129:
                binaryLong(classFile, method, stack, "|");
                break;
            case 130:
                binaryInt(classFile, method, stack, "^");
                break;
            case 131:
                binaryLong(classFile, method, stack, "^");
                break;
            case 118:
                unaryFloatNeg(classFile, method, stack);
                break;
            case 119:
                unaryDoubleNeg(classFile, method, stack);
                break;
            case 132:
                incrementInt(classFile, method, instructions, locals, localDeclarations, instruction);
                break;
            case 133:
                stack.add(StackValue.longExpression(IrExpression.longCall("javan_i2l", List.of(popInt(classFile, method, stack)))));
                break;
            case 134:
                stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_i2f", List.of(popInt(classFile, method, stack)))));
                break;
            case 135:
                stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_i2d", List.of(popInt(classFile, method, stack)))));
                break;
            case 136:
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_l2i", List.of(popLong(classFile, method, stack)))));
                break;
            case 145:
                intToByte(classFile, method, stack);
                break;
            case 146:
                intToChar(classFile, method, stack);
                break;
            case 147:
                intToShort(classFile, method, stack);
                break;
            case 148:
                compareLong(classFile, method, stack);
                break;
            case 149:
                compareFloat(classFile, method, stack, -1);
                break;
            case 150:
                compareFloat(classFile, method, stack, 1);
                break;
            case 151:
                compareDouble(classFile, method, stack, -1);
                break;
            case 152:
                compareDouble(classFile, method, stack, 1);
                break;
            case 153:
            case 154:
            case 155:
            case 156:
            case 157:
            case 158:
                branchZero(classFile, method, instruction, instructions, stack);
                break;
            case 159:
            case 160:
            case 161:
            case 162:
            case 163:
            case 164:
                branchIntCompare(classFile, method, instruction, instructions, stack);
                break;
            case 165:
            case 166:
                branchObjectCompare(classFile, method, instruction, instructions, stack);
                break;
            case 167:
                instructions.add(IrInstruction.jump(label(branchTarget(instruction))));
                break;
            case 170:
                tableSwitch(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 171:
                lookupSwitch(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 198:
            case 199:
                branchObjectNull(classFile, method, instruction, instructions, stack);
                break;
            case 178:
                pushField(classes, classFile, method, instruction, stack);
                break;
            case 179:
                assignStaticField(classes, classFile, method, instruction, instructions, stack);
                break;
            case 18:
            case 19:
            case 20:
                pushConstant(classFile, method, instruction, stack);
                break;
            case 180:
                pushInstanceField(classFile, method, instruction, stack);
                break;
            case 181:
                assignInstanceField(classFile, method, instruction, instructions, stack);
                break;
            case 182:
                lowerVirtualCall(classes, classFile, method, instruction, instructions, stack, localDeclarations, dispatches);
                break;
            case 183:
                lowerInstanceCall(classes, classFile, method, instruction, instructions, stack);
                break;
            case 184:
                lowerStaticCall(classes, classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 185:
                lowerInterfaceCall(classes, classFile, method, instruction, instructions, stack, localDeclarations, dispatches);
                break;
            case 186:
                lowerDynamicCall(classFile, method, instruction, stack);
                break;
            case 187:
                newObject(classes, classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 188:
                newPrimitiveArray(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 189:
                newObjectArray(classFile, method, instructions, stack, localDeclarations);
                break;
            case 190:
                arrayLength(classFile, method, stack);
                break;
            case 191:
                lowerThrow(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks);
                break;
            case 192:
                // checkcast is a verifier/runtime type check; exact supported code keeps the reference unchanged.
                break;
            case 193:
                lowerInstanceOf(classes, classFile, method, instruction, stack);
                break;
            default:
                if (!isIgnoredNoop(instruction.opcode())) {
                    throw unsupported(classFile, method, instruction);
                }
                break;
        }
    }

    private static void lowerInstanceOf(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final String target = instruction.className().orElseThrow();
        final IrExpression value = popObject(classFile, method, stack);
        if ("java/lang/Object".equals(target)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_object_non_null", List.of(value))));
            return;
        }
        final Optional<Integer> wrapperTypeId = platformWrapperTypeId(target);
        final List<IrExpression> arguments = new ArrayList<>();
        arguments.add(value);
        if (wrapperTypeId.isPresent()) {
            arguments.add(IrExpression.intLiteral(1));
            arguments.add(IrExpression.intLiteral(wrapperTypeId.orElseThrow()));
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_object_type_in", arguments)));
            return;
        }
        final boolean knownTarget = classes.containsKey(target);
        final List<Integer> typeIds = assignableTypeIds(classes, target);
        if (typeIds.isEmpty() && !knownTarget) {
            throw unsupportedInstanceOfTarget(classFile, method, instruction, target);
        }
        if (typeIds.isEmpty()) {
            stack.add(StackValue.intExpression(IrExpression.intLiteral(0)));
            return;
        }
        arguments.add(IrExpression.intLiteral(typeIds.size()));
        for (final int typeId : typeIds) {
            arguments.add(IrExpression.intLiteral(typeId));
        }
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_object_type_in", arguments)));
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
        if ("java/lang/System".equals(fieldRef.owner()) && "err".equals(fieldRef.name())) {
            stack.add(StackValue.errorPrintStream());
            return;
        }
        if ("java/io/File".equals(fieldRef.owner()) && "separatorChar".equals(fieldRef.name()) && "C".equals(fieldRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_file_separator_char", List.of())));
            return;
        }
        if ("java/io/File".equals(fieldRef.owner()) && "pathSeparatorChar".equals(fieldRef.name()) && "C".equals(fieldRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_file_path_separator_char", List.of())));
            return;
        }
        if ("java/io/File".equals(fieldRef.owner()) && "pathSeparator".equals(fieldRef.name()) && "Ljava/lang/String;".equals(fieldRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_file_path_separator", List.of())));
            return;
        }
        if (isSupportedJdkEnumConstant(fieldRef)) {
            stack.add(StackValue.objectExpression(IrExpression.stringLiteral(fieldRef.name())));
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
        final IrType type = requiredIrType(staticFieldType(classes, fieldRef), classFile, method, instruction);
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
        final IrType type = requiredIrType(fieldType(fieldRef.descriptor()), classFile, method, instruction);
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
        final IrType type = requiredIrType(fieldType(fieldRef.descriptor()), classFile, method, instruction);
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

    private static IrType requiredIrType(
        final Optional<IrType> type,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        if (type.isPresent()) {
            return type.orElseThrow();
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
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (lowerPrintStreamCall(classFile, method, methodRef, instructions, stack)) {
            return;
        }
        if ("java/io/PrintStream".equals(methodRef.owner())
            && "print".equals(methodRef.name())
            && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression arg = popObject(classFile, method, stack);
            final StackValue receiver = pop(stack);
            if (receiver.kind() == StackKind.PRINT_STREAM) {
                instructions.add(IrInstruction.printObject(arg));
                return;
            }
        }
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
        if (isEnumOrdinal(classes, methodRef)) {
            lowerEnumOrdinal(classes, classFile, method, methodRef, stack);
            return;
        }
        if (lowerArrayClone(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJavanProcessRunnerRun(classes, classFile, method, methodRef, instructions, stack, localDeclarations)) {
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
        if ("java/lang/String".equals(methodRef.owner()) && "indexOf".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_char", List.of(receiver, ch))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "indexOf".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_char_from", List.of(receiver, ch, fromIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "indexOf".equals(methodRef.name())
            && "(Ljava/lang/String;)I".equals(methodRef.descriptor())) {
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_string", List.of(receiver, needle))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "indexOf".equals(methodRef.name())
            && "(Ljava/lang/String;I)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_string_from", List.of(receiver, needle, fromIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "lastIndexOf".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_last_index_of_char", List.of(receiver, ch))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "lastIndexOf".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_last_index_of_char_from", List.of(receiver, ch, fromIndex))));
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
        if ("java/lang/String".equals(methodRef.owner())
            && "contains".equals(methodRef.name())
            && "(Ljava/lang/CharSequence;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_contains", List.of(receiver, argument))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "startsWith".equals(methodRef.name())
            && "(Ljava/lang/String;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_starts_with", List.of(receiver, argument))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "endsWith".equals(methodRef.name())
            && "(Ljava/lang/String;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_ends_with", List.of(receiver, argument))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "replace".equals(methodRef.name())
            && "(CC)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression newCh = popInt(classFile, method, stack);
            final IrExpression oldCh = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_replace_char", List.of(receiver, oldCh, newCh));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "intern".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "trim".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_trim", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "substring".equals(methodRef.name())
            && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring", List.of(receiver, begin));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "substring".equals(methodRef.name())
            && "(II)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring_range", List.of(receiver, begin, end));
            return;
        }
        if (lowerJdkWrapperInstanceCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerOptionalInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerStringBuilderCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkPathInstanceCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileInstanceCall(methodRef, stack)) {
            return;
        }
        if (lowerJdkCollectionInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
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

    private static boolean lowerPrintStreamCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        if (!"java/io/PrintStream".equals(methodRef.owner())) {
            return false;
        }
        if ("print".equals(methodRef.name()) && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            emitPrintObject(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            emitPrintlnObject(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            emitPrintlnObject(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popInt(classFile, method, stack);
            emitPrintlnInt(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popLong(classFile, method, stack);
            emitPrintlnLong(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(F)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popFloat(classFile, method, stack);
            emitPrintlnFloat(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(D)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popDouble(classFile, method, stack);
            emitPrintlnDouble(classFile, method, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popInt(classFile, method, stack);
            emitPrintlnBoolean(classFile, method, instructions, stack, argument);
            return true;
        }
        return false;
    }

    private static StackValue popPrintStream(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue receiver = pop(stack);
        if (receiver.kind() == StackKind.OBJECT || receiver.kind() == StackKind.PRINT_STREAM || receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            return receiver;
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static void emitPrintObject(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printErrorObject(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printObject(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_print", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static void emitPrintlnObject(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorObject(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnObject(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static void emitPrintlnInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorInt(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnInt(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println_int", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static void emitPrintlnLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorLong(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnLong(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println_long", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static void emitPrintlnFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorFloat(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnFloat(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println_float", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static void emitPrintlnDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorDouble(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnDouble(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println_double", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static void emitPrintlnBoolean(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorBoolean(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnBoolean(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println_bool", List.of(receiver.expression().orElseThrow(), argument)));
    }

    private static boolean lowerArrayClone(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!methodRef.owner().startsWith("[")
            || !"clone".equals(methodRef.name())
            || !"()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            return false;
        }
        final Optional<String> cloneSymbol = arrayCloneSymbol(methodRef.owner());
        if (cloneSymbol.isEmpty()) {
            throw new DiagnosticException(Diagnostic.error(
                "JAVAN044",
                "array clone type is not supported",
                classFile.name(),
                method.name() + method.descriptor(),
                methodRef.display(),
                "The runtime does not have a clone helper for this array kind yet.",
                "Use a supported array kind or add the matching runtime copy helper."
            ));
        }
        final String symbol = cloneSymbol.orElseThrow();
        final IrExpression value = popObject(classFile, method, stack);
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression array = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, value));
        stack.add(StackValue.objectExpression(IrExpression.objectCall(
            symbol,
            List.of(array, IrExpression.intCall("javan_array_length", List.of(array)))
        )));
        return true;
    }

    private static Optional<String> arrayCloneSymbol(final String owner) {
        if ("[I".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_int");
        }
        if ("[J".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_long");
        }
        if ("[B".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_byte");
        }
        if ("[S".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_short");
        }
        if ("[C".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_char");
        }
        if ("[F".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_float");
        }
        if ("[D".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_double");
        }
        if (owner.startsWith("[L") || owner.startsWith("[[")) {
            return Optional.of("javan_arrays_copy_of_object");
        }
        return Optional.empty();
    }

    private static boolean lowerJavanProcessRunnerRun(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!isJavanProcessRunnerRun(methodRef)) {
            return false;
        }
        if (!classes.containsKey("javan/util/ProcessRunner$Result")) {
            throw unsupportedJavanProcessResult(classFile, method, methodRef);
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = popArguments(classFile, method, stack, descriptor);
        final IrExpression receiver = popObject(classFile, method, stack);
        final IrExpression workingDirectory = arguments.get(0);
        final IrExpression command = arguments.get(1);
        final IrExpression timeout = IrExpression.longField("javan/util/ProcessRunner", "timeoutMillis", receiver);

        final String nativeResultName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, nativeResultName));
        final IrExpression nativeResult = IrExpression.objectLocal(nativeResultName);
        instructions.add(IrInstruction.assignObject(
            nativeResultName,
            IrExpression.objectCall("javan_process_run", List.of(workingDirectory, command, timeout))
        ));

        final String resultName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, resultName));
        final IrExpression result = IrExpression.objectLocal(resultName);
        instructions.add(IrInstruction.assignObject(resultName, IrExpression.objectAllocation("javan/util/ProcessRunner$Result")));
        instructions.add(IrInstruction.assignFieldInt(
            "javan/util/ProcessRunner$Result",
            "exitCode",
            result,
            IrExpression.intCall("javan_process_result_exit_code", List.of(nativeResult))
        ));
        instructions.add(IrInstruction.assignFieldObject(
            "javan/util/ProcessRunner$Result",
            "stdout",
            result,
            IrExpression.objectCall("javan_process_result_stdout", List.of(nativeResult))
        ));
        instructions.add(IrInstruction.assignFieldObject(
            "javan/util/ProcessRunner$Result",
            "stderr",
            result,
            IrExpression.objectCall("javan_process_result_stderr", List.of(nativeResult))
        ));
        stack.add(StackValue.objectExpression(result));
        return true;
    }

    private static boolean isJavanProcessRunnerRun(final MethodRef methodRef) {
        if (!"javan/util/ProcessRunner".equals(methodRef.owner())) {
            return false;
        }
        if (!"run".equals(methodRef.name())) {
            return false;
        }
        return "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;".equals(methodRef.descriptor());
    }

    private static DiagnosticException unsupportedJavanProcessResult(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN048",
            "javan process substitution cannot allocate result",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            "The native process substitution requires javan.util.ProcessRunner.Result in the closed world.",
            "Compile ProcessRunner and its nested Result record with the javan classes."
        ));
    }

    private static boolean lowerJdkWrapperInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/lang/Integer".equals(methodRef.owner()) && "intValue".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_integer_int_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Long".equals(methodRef.owner()) && "longValue".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_long_long_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Float".equals(methodRef.owner()) && "floatValue".equals(methodRef.name()) && "()F".equals(methodRef.descriptor())) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_float_float_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Double".equals(methodRef.owner()) && "doubleValue".equals(methodRef.name()) && "()D".equals(methodRef.descriptor())) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_double_double_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        return false;
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
            updatePendingThrowableMessage(stack, IrExpression.objectNull());
            return;
        }
        if (isNoopPlatformConstructor(methodRef)) {
            return;
        }
        if (lowerStringConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerStringBuilderConstructor(methodRef, instructions, stack, arguments, receiver)) {
            return;
        }
        if (lowerJdkCollectionConstructorCall(methodRef, instructions, arguments, receiver)) {
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
        if (lowerEnumValues(classes, classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkStaticIntrinsic(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkCollectionStaticCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileStaticCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerOptionalStaticCall(classFile, method, methodRef, stack)) {
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

    private static void lowerEnumOrdinal(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        final IrExpression receiver = popObject(classFile, method, stack);
        if (receiver.kind() == IrExpression.Kind.STRING_LITERAL) {
            final Optional<Integer> ordinal = enumOrdinal(classes.get(methodRef.owner()), receiver.value());
            if (ordinal.isEmpty()) {
                throw unsupportedEnumConstant(classFile, method, methodRef, receiver.value());
            }
            final int value = ordinal.orElseThrow();
            stack.add(StackValue.intExpression(IrExpression.intLiteral(value)));
            return;
        }
        stack.add(StackValue.intExpression(IrExpression.intCall(enumOrdinalSymbol(methodRef.owner()), List.of(receiver))));
    }

    private static boolean lowerEnumValues(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final ClassFile enumClass = classes.get(methodRef.owner());
        if (enumClass == null || !enumClass.isEnum() || !"values".equals(methodRef.name())
            || !methodRef.descriptor().equals("()[L" + methodRef.owner() + ";")) {
            return false;
        }
        final List<String> constants = enumConstants(enumClass);
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression local = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectArrayAllocation(IrExpression.intLiteral(constants.size()))));
        for (int index = 0; index < constants.size(); index++) {
            instructions.add(IrInstruction.assignArrayObject(
                local,
                IrExpression.intLiteral(index),
                IrExpression.stringLiteral(constants.get(index))
            ));
        }
        stack.add(StackValue.objectExpression(local));
        return true;
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
        if ("java/lang/Float".equals(methodRef.owner())) {
            return lowerFloatIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Double".equals(methodRef.owner())) {
            return lowerDoubleIntrinsic(classFile, method, methodRef, stack);
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
        if ("toIntExact".equals(methodRef.name()) && "(J)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_math_to_int_exact", List.of(popLong(classFile, method, stack)))));
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
        if ("valueOf".equals(methodRef.name()) && "(I)Ljava/lang/Integer;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_integer_value_of", List.of(popInt(classFile, method, stack)))));
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
        if ("valueOf".equals(methodRef.name()) && "(J)Ljava/lang/Long;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_long_value_of", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    private static boolean lowerFloatIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(F)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_float", List.of(popFloat(classFile, method, stack)))));
            return true;
        }
        if ("intBitsToFloat".equals(methodRef.name()) && "(I)F".equals(methodRef.descriptor())) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_float_int_bits_to_float", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(F)Ljava/lang/Float;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_float_value_of", List.of(popFloat(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    private static boolean lowerDoubleIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(D)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_double", List.of(popDouble(classFile, method, stack)))));
            return true;
        }
        if ("longBitsToDouble".equals(methodRef.name()) && "(J)D".equals(methodRef.descriptor())) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_double_long_bits_to_double", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(D)Ljava/lang/Double;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_double_value_of", List.of(popDouble(classFile, method, stack)))));
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
        if ("lineSeparator".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_system_line_separator", List.of())));
            return true;
        }
        if ("getenv".equals(methodRef.name()) && "(Ljava/lang/String;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_system_getenv", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("getProperty".equals(methodRef.name()) && "(Ljava/lang/String;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_system_get_property", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("getProperty".equals(methodRef.name()) && "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression fallback = popObject(classFile, method, stack);
            final IrExpression key = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_system_get_property_or_default", List.of(key, fallback))));
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
        if ("exit".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_system_exit", List.of(popInt(classFile, method, stack))));
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
        if ("copyOfRange".equals(methodRef.name()) && "([BII)[B".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression source = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_arrays_copy_of_range_byte",
                List.of(source, begin, end)
            )));
            return true;
        }
        if ("copyOfRange".equals(methodRef.name()) && "([Ljava/lang/Object;II)[Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression source = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_arrays_copy_of_range_object",
                List.of(source, begin, end)
            )));
            return true;
        }
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
        if ("([II)[I".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_int");
        }
        if ("([JI)[J".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_long");
        }
        if ("([BI)[B".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_byte");
        }
        if ("([SI)[S".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_short");
        }
        if ("([CI)[C".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_char");
        }
        if ("([FI)[F".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_float");
        }
        if ("([DI)[D".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_double");
        }
        if ("([Ljava/lang/Object;I)[Ljava/lang/Object;".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_object");
        }
        return Optional.empty();
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
        if ("requireNonNull".equals(methodRef.name()) && "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression message = popObject(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, value));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null_msg", List.of(local, message)));
            stack.add(StackValue.objectExpression(local));
            return true;
        }
        return false;
    }

    private static boolean lowerStringBuilderConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/lang/StringBuilder".equals(methodRef.owner()) || !"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_append_string", List.of(receiver, arguments.getFirst())));
            return true;
        }
        return false;
    }

    private static boolean lowerStringConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/lang/String".equals(methodRef.owner()) || !"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("([CII)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_string_from_chars", arguments)
            ));
            return true;
        }
        return false;
    }

    private static boolean lowerOptionalStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"java/util/Optional".equals(methodRef.owner()) || JdkCallSupport.supportedCall(methodRef).isEmpty()) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        if ("empty".equals(methodRef.name()) && "()Ljava/util/Optional;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_empty", List.of())));
            return true;
        }
        if ("of".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/util/Optional;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_of", arguments)));
            return true;
        }
        if ("ofNullable".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/util/Optional;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_of_nullable", arguments)));
            return true;
        }
        return false;
    }

    private static boolean lowerOptionalInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/util/Optional".equals(methodRef.owner()) || JdkCallSupport.supportedCall(methodRef).isEmpty()) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        final IrExpression receiver = popObject(classFile, method, stack);
        if ("isPresent".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_optional_is_present", List.of(receiver))));
            return true;
        }
        if ("isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_optional_is_empty", List.of(receiver))));
            return true;
        }
        if ("orElse".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_or_else", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("get".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_optional_or_else_throw", List.of(receiver));
            return true;
        }
        if ("orElseThrow".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_optional_or_else_throw", List.of(receiver));
            return true;
        }
        return false;
    }

    private static boolean lowerStringBuilderCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/lang/StringBuilder".equals(methodRef.owner()) || JdkCallSupport.supportedCall(methodRef).isEmpty()) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        if ("append".equals(methodRef.name()) && "(Ljava/lang/String;)Ljava/lang/StringBuilder;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_string", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("append".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/lang/StringBuilder;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_object", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("append".equals(methodRef.name()) && "(Z)Ljava/lang/StringBuilder;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_boolean", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("append".equals(methodRef.name()) && "(C)Ljava/lang/StringBuilder;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_char", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("append".equals(methodRef.name()) && "(I)Ljava/lang/StringBuilder;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_int", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("append".equals(methodRef.name()) && "(J)Ljava/lang/StringBuilder;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_long", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_to_string", List.of(receiver));
            return true;
        }
        if ("length".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_length", List.of(receiver))));
            return true;
        }
        if ("isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_is_empty", List.of(receiver))));
            return true;
        }
        if ("setLength".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_set_length", List.of(receiver, arguments.getFirst())));
            return true;
        }
        return false;
    }

    private static boolean lowerJdkCollectionStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/util/Map".equals(methodRef.owner())
            && "copyOf".equals(methodRef.name())
            && "(Ljava/util/Map;)Ljava/util/Map;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_copy_of", arguments)));
            return true;
        }
        if (!"java/util/List".equals(methodRef.owner())) {
            return false;
        }
        if ("copyOf".equals(methodRef.name()) && "(Ljava/util/Collection;)Ljava/util/List;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_copy_of", arguments)));
            return true;
        }
        if (!"of".equals(methodRef.name())) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        if ("([Ljava/lang/Object;)Ljava/util/List;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of_array", arguments)));
            return true;
        }
        final List<IrExpression> callArguments = new ArrayList<>();
        callArguments.add(IrExpression.intLiteral(arguments.size()));
        callArguments.addAll(arguments);
        stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of", callArguments)));
        return true;
    }

    private static boolean lowerJdkCollectionInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!isJdkCollectionOwner(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        return lowerJdkCollectionInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations, arguments, receiver);
    }

    private static boolean lowerJdkCollectionConstructorCall(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if ("java/util/ArrayList".equals(methodRef.owner()) && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(I)V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(Ljava/util/Collection;)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_all", List.of(receiver, arguments.getFirst())));
                return true;
            }
        }
        if (isJdkMapClass(methodRef.owner()) && "<init>".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            return true;
        }
        return false;
    }

    private static boolean lowerJdkCollectionInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (isJdkListClass(methodRef.owner())
            && "add".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if (isJdkListClass(methodRef.owner())
            && "add".equals(methodRef.name())
            && "(ILjava/lang/Object;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_at", List.of(receiver, arguments.get(0), arguments.get(1))));
            return true;
        }
        if (isJdkListClass(methodRef.owner())
            && "addAll".equals(methodRef.name())
            && "(Ljava/util/Collection;)Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add_all", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "addFirst".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_first", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "set".equals(methodRef.name()) && "(ILjava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_set", List.of(receiver, arguments.get(0), arguments.get(1)));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "removeLast".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_last", List.of(receiver));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "size".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_size", List.of(receiver))));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_is_empty", List.of(receiver))));
            return true;
        }
        if (isJdkListOrCollection(methodRef.owner())
            && "contains".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_contains", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "get".equals(methodRef.name()) && "(I)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "getFirst".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get_first", List.of(receiver))));
            return true;
        }
        if ("java/util/List".equals(methodRef.owner()) && "getLast".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get_last", List.of(receiver))));
            return true;
        }
        if (isJdkListOrCollectionIterator(methodRef)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
            return true;
        }
        if (isJdkIteratorHasNext(methodRef)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_iterator_has_next", List.of(receiver))));
            return true;
        }
        if (isJdkIteratorNext(methodRef)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_iterator_next", List.of(receiver));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "get".equals(methodRef.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_map_get", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "getOrDefault".equals(methodRef.name())
            && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_map_get_or_default", List.of(receiver, arguments.get(0), arguments.get(1)));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "put".equals(methodRef.name())
            && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_map_put", List.of(receiver, arguments.get(0), arguments.get(1)));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "putIfAbsent".equals(methodRef.name())
            && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_map_put_if_absent", List.of(receiver, arguments.get(0), arguments.get(1)));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "containsKey".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_map_contains_key", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "size".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_map_size", List.of(receiver))));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "isEmpty".equals(methodRef.name())
            && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_map_is_empty", List.of(receiver))));
            return true;
        }
        if (isJdkMapOwner(methodRef.owner())
            && "values".equals(methodRef.name())
            && "()Ljava/util/Collection;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_values", List.of(receiver))));
            return true;
        }
        throw new DiagnosticException(Diagnostic.error(
            "JAVAN047",
            "declared supported collection call has no lowering",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            "The JDK support registry and bytecode lowering are out of sync.",
            "Add the missing lowering or remove the call from the support registry."
        ));
    }

    private static void pushIntCall(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        final String localName = "int" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.INT, localName));
        instructions.add(IrInstruction.assignInt(localName, IrExpression.intCall(symbol, arguments)));
        stack.add(StackValue.intExpression(IrExpression.intLocal(localName)));
    }

    private static void pushObjectCall(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall(symbol, arguments)));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(localName)));
    }

    private static boolean isJdkCollectionOwner(final String owner) {
        if (isJdkListOrCollection(owner)) {
            return true;
        }
        if ("java/util/Iterator".equals(owner)) {
            return true;
        }
        return isJdkMapOwner(owner);
    }

    private static boolean isJdkListOrCollectionIterator(final MethodRef methodRef) {
        if (!isJdkListOrCollection(methodRef.owner())) {
            return false;
        }
        if (!"iterator".equals(methodRef.name())) {
            return false;
        }
        return "()Ljava/util/Iterator;".equals(methodRef.descriptor());
    }

    private static boolean isJdkIteratorHasNext(final MethodRef methodRef) {
        if (!"java/util/Iterator".equals(methodRef.owner())) {
            return false;
        }
        if (!"hasNext".equals(methodRef.name())) {
            return false;
        }
        return "()Z".equals(methodRef.descriptor());
    }

    private static boolean isJdkIteratorNext(final MethodRef methodRef) {
        if (!"java/util/Iterator".equals(methodRef.owner())) {
            return false;
        }
        if (!"next".equals(methodRef.name())) {
            return false;
        }
        return "()Ljava/lang/Object;".equals(methodRef.descriptor());
    }

    private static boolean isJdkListClass(final String owner) {
        if ("java/util/List".equals(owner)) {
            return true;
        }
        return "java/util/ArrayList".equals(owner);
    }

    private static boolean isJdkListOrCollection(final String owner) {
        if (isJdkListClass(owner)) {
            return true;
        }
        return "java/util/Collection".equals(owner);
    }

    private static boolean isJdkMapOwner(final String owner) {
        if ("java/util/Map".equals(owner)) {
            return true;
        }
        return isJdkMapClass(owner);
    }

    private static boolean isJdkMapClass(final String owner) {
        if ("java/util/HashMap".equals(owner)) {
            return true;
        }
        if ("java/util/LinkedHashMap".equals(owner)) {
            return true;
        }
        return "java/util/TreeMap".equals(owner);
    }

    private static boolean lowerJdkFileStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/nio/file/Path".equals(methodRef.owner())
            && "of".equals(methodRef.name())
            && "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_of", arguments)));
            return true;
        }
        if (!"java/nio/file/Files".equals(methodRef.owner())) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        if ("exists".equals(methodRef.name()) && "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_files_exists", arguments)));
            return true;
        }
        if ("isDirectory".equals(methodRef.name()) && "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_files_is_directory", arguments)));
            return true;
        }
        if ("isRegularFile".equals(methodRef.name()) && "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_files_is_regular_file", arguments)));
            return true;
        }
        if ("isExecutable".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_files_is_executable", arguments)));
            return true;
        }
        if ("createDirectories".equals(methodRef.name())
            && "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_create_directories", arguments);
            return true;
        }
        if ("copy".equals(methodRef.name())
            && "(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_copy", arguments);
            return true;
        }
        if ("readString".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_read_string", arguments);
            return true;
        }
        if ("writeString".equals(methodRef.name())
            && "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_write_string", arguments);
            return true;
        }
        if ("write".equals(methodRef.name())
            && "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_write_bytes", arguments);
            return true;
        }
        if ("readAllBytes".equals(methodRef.name()) && "(Ljava/nio/file/Path;)[B".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_read_all_bytes", arguments);
            return true;
        }
        if ("deleteIfExists".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_files_delete_if_exists", arguments);
            return true;
        }
        if ("size".equals(methodRef.name()) && "(Ljava/nio/file/Path;)J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_files_size", arguments)));
            return true;
        }
        if ("newDirectoryStream".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Ljava/nio/file/DirectoryStream;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_new_directory_stream", arguments);
            return true;
        }
        return false;
    }

    private static boolean lowerJdkFileInstanceCall(final MethodRef methodRef, final List<StackValue> stack) {
        if (isDirectoryStreamClose(methodRef)) {
            popObjectForJdkCall(stack);
            return true;
        }
        if (isDirectoryStreamIterator(methodRef)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        return false;
    }

    private static boolean isDirectoryStreamClose(final MethodRef methodRef) {
        if (!"java/nio/file/DirectoryStream".equals(methodRef.owner())) {
            return false;
        }
        if (!"close".equals(methodRef.name())) {
            return false;
        }
        return "()V".equals(methodRef.descriptor());
    }

    private static boolean isDirectoryStreamIterator(final MethodRef methodRef) {
        if (!isIterableOrDirectoryStream(methodRef.owner())) {
            return false;
        }
        if (!"iterator".equals(methodRef.name())) {
            return false;
        }
        return "()Ljava/util/Iterator;".equals(methodRef.descriptor());
    }

    private static boolean isIterableOrDirectoryStream(final String owner) {
        if ("java/lang/Iterable".equals(owner)) {
            return true;
        }
        return "java/nio/file/DirectoryStream".equals(owner);
    }

    private static boolean lowerJdkPathInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"java/nio/file/Path".equals(methodRef.owner())) {
            return false;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(popObjectForJdkCall(stack)));
            return true;
        }
        if ("toAbsolutePath".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_to_absolute", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        if ("normalize".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_normalize", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        if ("getParent".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_get_parent", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        if ("getFileName".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_get_file_name", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        if ("isAbsolute".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_is_absolute", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        if ("getNameCount".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_get_name_count", List.of(popObjectForJdkCall(stack)))));
            return true;
        }
        if ("getName".equals(methodRef.name()) && "(I)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_get_name", List.of(popObjectForJdkCall(stack), index))));
            return true;
        }
        if ("equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(stack);
            final IrExpression receiver = popObjectForJdkCall(stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_equals", List.of(receiver, other))));
            return true;
        }
        if ("startsWith".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Z".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(stack);
            final IrExpression receiver = popObjectForJdkCall(stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_starts_with", List.of(receiver, other))));
            return true;
        }
        if ("relativize".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(stack);
            final IrExpression receiver = popObjectForJdkCall(stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_relativize", List.of(receiver, other))));
            return true;
        }
        if ("resolve".equals(methodRef.name())
            && ("(Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodRef.descriptor())
            || "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(methodRef.descriptor()))) {
            final IrExpression child = popObjectForJdkCall(stack);
            final IrExpression receiver = popObjectForJdkCall(stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_resolve", List.of(receiver, child))));
            return true;
        }
        return false;
    }

    private static IrExpression popObjectForJdkCall(final List<StackValue> stack) {
        final StackValue value = stack.removeLast();
        if (value.kind() != StackKind.OBJECT) {
            throw new IllegalStateException("Expected object stack value");
        }
        return value.expression().orElseThrow();
    }

    private static void lowerInterfaceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (lowerJdkPathInstanceCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileInstanceCall(methodRef, stack)) {
            return;
        }
        if (lowerJdkCollectionInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
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
        final List<IrDispatchTarget> dispatchTargets = new ArrayList<>();
        final List<EntryPoint> sortedTargets = sortedEntryPointsByClassName(targets);
        for (final EntryPoint target : sortedTargets) {
            dispatchTargets.add(new IrDispatchTarget(target.className(), symbol(target)));
        }
        return new IrDispatch(symbol, descriptor.returnType(), List.copyOf(parameters), dispatchTargets);
    }

    private static List<EntryPoint> sortedEntryPointsByClassName(final List<EntryPoint> entries) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final EntryPoint entry : entries) {
            int index = 0;
            while (index < result.size() && Strings2.compareAscii(result.get(index).className(), entry.className()) <= 0) {
                index++;
            }
            result.add(index, entry);
        }
        return List.copyOf(result);
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
        final Optional<DynamicRef> maybeDynamicRef = instruction.dynamicRef();
        if (maybeDynamicRef.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final DynamicRef dynamicRef = maybeDynamicRef.orElseThrow();
        if (!isSupportedStringConcat(dynamicRef)) {
            throw unsupported(classFile, method, instruction);
        }
        final List<String> parameterDescriptors = parameterDescriptors(dynamicRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>();
        for (int index = parameterDescriptors.size() - 1; index >= 0; index--) {
            arguments.addFirst(popStringConcatArgument(classFile, method, stack, parameterDescriptors.get(index)));
        }
        final Optional<String> recipe = stringConcatRecipe(dynamicRef, arguments.size());
        if (recipe.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        stack.add(StackValue.objectExpression(IrExpression.stringConcat(recipe.orElseThrow(), arguments)));
    }

    private static IrExpression popStringConcatArgument(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String descriptor
    ) {
        final char type = descriptor.charAt(0);
        if (type == 'B') {
            return IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)));
        }
        if (type == 'I') {
            return IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)));
        }
        if (type == 'S') {
            return IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)));
        }
        if (type == 'C') {
            return IrExpression.objectCall("javan_string_value_of_char", List.of(popInt(classFile, method, stack)));
        }
        if (type == 'Z') {
            return IrExpression.objectCall("javan_string_value_of_bool", List.of(popInt(classFile, method, stack)));
        }
        if (type == 'J') {
            return IrExpression.objectCall("javan_string_value_of_long", List.of(popLong(classFile, method, stack)));
        }
        if (type == 'F') {
            return IrExpression.objectCall("javan_string_value_of_float", List.of(popFloat(classFile, method, stack)));
        }
        if (type == 'D') {
            return IrExpression.objectCall("javan_string_value_of_double", List.of(popDouble(classFile, method, stack)));
        }
        if (type == 'L') {
            return popObject(classFile, method, stack);
        }
        if (type == '[') {
            return popObject(classFile, method, stack);
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static boolean isSupportedStringConcat(final DynamicRef dynamicRef) {
        if (!"java/lang/invoke/StringConcatFactory".equals(dynamicRef.bootstrapOwner())) {
            return false;
        }
        final int returnStart = dynamicRef.descriptor().indexOf(')');
        if (returnStart < 0) {
            return false;
        }
        if (!"Ljava/lang/String;".equals(dynamicRef.descriptor().substring(returnStart + 1))) {
            return false;
        }
        if ("makeConcat".equals(dynamicRef.bootstrapName())) {
            return true;
        }
        return "makeConcatWithConstants".equals(dynamicRef.bootstrapName());
    }

    private static Optional<String> stringConcatRecipe(final DynamicRef dynamicRef, final int argumentCount) {
        if ("makeConcat".equals(dynamicRef.bootstrapName())) {
            return Optional.of(repeatedConcatPlaceholder(argumentCount));
        }
        if (!"makeConcatWithConstants".equals(dynamicRef.bootstrapName())) {
            return Optional.empty();
        }
        if (dynamicRef.bootstrapArguments().isEmpty()) {
            return Optional.empty();
        }
        final String recipe = dynamicRef.bootstrapArguments().getFirst();
        if (recipe.indexOf(2) >= 0) {
            return Optional.empty();
        }
        return Optional.of(recipe);
    }

    private static String repeatedConcatPlaceholder(final int count) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append('\u0001');
        }
        return result.toString();
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
        if (type == IrType.INT) {
            return popInt(classFile, method, stack);
        }
        if (type == IrType.LONG) {
            return popLong(classFile, method, stack);
        }
        if (type == IrType.FLOAT) {
            return popFloat(classFile, method, stack);
        }
        if (type == IrType.DOUBLE) {
            return popDouble(classFile, method, stack);
        }
        if (type == IrType.OBJECT) {
            return popObject(classFile, method, stack);
        }
        if (type == IrType.VOID) {
            throw new DiagnosticException(Diagnostic.error(
                "JAVAN041",
                "unsupported call argument type",
                classFile.name(),
                method.name() + method.descriptor(),
                type.name(),
                "Void is not a valid call argument.",
                "Use value-carrying parameters only."
            ));
        }
        throw new IllegalStateException("Unsupported IR type");
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

    private static void shiftInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String symbol
    ) {
        final IrExpression shift = popInt(classFile, method, stack);
        final IrExpression value = popInt(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value, shift))));
    }

    private static void shiftLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String symbol
    ) {
        final IrExpression shift = popInt(classFile, method, stack);
        final IrExpression value = popLong(classFile, method, stack);
        stack.add(StackValue.longExpression(IrExpression.longCall(symbol, List.of(value, shift))));
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

    private static void intToChar(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intBinary(
            "&",
            popInt(classFile, method, stack),
            IrExpression.intLiteral(65_535)
        )));
    }

    private static void intToByte(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_i2b", List.of(popInt(classFile, method, stack)))));
    }

    private static void intToShort(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_i2s", List.of(popInt(classFile, method, stack)))));
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

    private static void compareLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression right = popLong(classFile, method, stack);
        final IrExpression left = popLong(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_lcmp", List.of(left, right))));
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
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        if (stack.isEmpty()) {
            if (isSyntheticSwitchMapInitializer(classFile, method) && isEnumSwitchMapHandlerInstruction(instruction.opcode())) {
                return;
            }
            throw invalidStack(classFile, method, instruction, "object store requires a value on the bytecode stack");
        }
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
        final int type = unsigned(instruction.operands()[0]);
        if (type == 4) {
            return IrExpression.booleanArrayAllocation(length);
        }
        if (type == 8) {
            return IrExpression.byteArrayAllocation(length);
        }
        if (type == 5) {
            return IrExpression.charArrayAllocation(length);
        }
        if (type == 6) {
            return IrExpression.floatArrayAllocation(length);
        }
        if (type == 7) {
            return IrExpression.doubleArrayAllocation(length);
        }
        if (type == 9) {
            return IrExpression.shortArrayAllocation(length);
        }
        if (type == 10) {
            return IrExpression.intArrayAllocation(length);
        }
        if (type == 11) {
            return IrExpression.longArrayAllocation(length);
        }
        throw unsupported(classFile, method, instruction);
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
        final List<StackValue> stack,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks
    ) {
        final StackValue thrownValue = popThrowable(classFile, method, instruction, stack);
        final IrExpression thrown = thrownValue.expression().orElseThrow();
        final Optional<Integer> handler = exceptionHandler(classFile, method, instruction, thrownValue, instruction.offset());
        if (handler.isPresent()) {
            final int handlerOffset = handler.orElseThrow();
            if (pendingExceptionHandlerStacks.containsKey(handlerOffset)) {
                throw unsupportedTypedExceptionHandler(classFile, method, instruction);
            }
            pendingExceptionHandlerStacks.put(handlerOffset, thrownValue);
            instructions.add(IrInstruction.jump(label(handler.orElseThrow())));
            clearStack(stack);
            return;
        }
        instructions.add(IrInstruction.panic(thrown));
        clearStack(stack);
    }

    private static void clearStack(final List<StackValue> stack) {
        while (!stack.isEmpty()) {
            stack.removeLast();
        }
    }

    private static StackValue popThrowable(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.OBJECT) {
            return value;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static Optional<Integer> exceptionHandler(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final StackValue thrown,
        final int offset
    ) {
        final boolean inProtectedRange = hasExceptionHandler(method, offset);
        if (!inProtectedRange) {
            return Optional.empty();
        }
        if (thrown.throwableType().isEmpty()) {
            throw unsupportedTypedExceptionHandler(classFile, method, instruction);
        }
        final String thrownType = thrown.throwableType().orElseThrow();
        for (final javan.classfile.CodeException handler : method.code().orElseThrow().exceptionTable()) {
            if (offset < handler.startPc() || offset >= handler.endPc()) {
                continue;
            }
            if (handler.catchType().isEmpty()) {
                continue;
            }
            if (JdkCallSupport.isPlatformThrowableAssignable(thrownType, handler.catchType().orElseThrow())) {
                return Optional.of(handler.handlerPc());
            }
        }
        return Optional.empty();
    }

    private static boolean hasExceptionHandler(final MethodInfo method, final int offset) {
        for (final javan.classfile.CodeException handler : method.code().orElseThrow().exceptionTable()) {
            if (offset >= handler.startPc() && offset < handler.endPc()) {
                return true;
            }
        }
        return false;
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
        if (isKnownPlatformThrowable(owner)) {
            stack.add(StackValue.platformThrowable(owner, IrExpression.stringLiteral(owner)));
            return;
        }
        if ("java/lang/String".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectNull()));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/lang/StringBuilder".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_stringbuilder_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/util/ArrayList".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_arraylist_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if (isJdkMapClass(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_hashmap_new", List.of())));
            stack.add(StackValue.objectExpression(local));
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

    private static void branchObjectCompare(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final IrExpression right = popObject(classFile, method, stack);
        final IrExpression left = popObject(classFile, method, stack);
        instructions.add(IrInstruction.branchIf(
            label(branchTarget(instruction)),
            IrExpression.objectComparison(objectCompareOperator(instruction.opcode()), left, right)
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

    private static boolean lowerBranchValueSelection(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int index,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final List<Integer> skippedOffsets
    ) {
        final Instruction instruction = bytecode.get(index);
        if (!isConditionalBranch(instruction.opcode())) {
            return false;
        }
        if (lowerGuardedValueSelection(
            classes,
            classFile,
            method,
            bytecode,
            index,
            instructions,
            stack,
            locals,
            localDeclarations,
            dispatches,
            skippedOffsets,
            instruction
        )) {
            return true;
        }
        final int targetOffset = branchTarget(instruction);
        final int targetIndex = instructionIndex(bytecode, targetOffset);
        if (targetIndex < 0 || targetIndex <= index + 1) {
            return false;
        }
        final int jumpIndex = unconditionalJumpBefore(bytecode, index + 1, targetIndex);
        if (jumpIndex < 0) {
            return false;
        }
        final Instruction jumpInstruction = bytecode.get(jumpIndex);
        if (jumpInstruction.opcode() != 167) {
            return false;
        }
        final int doneOffset = branchTarget(jumpInstruction);
        final int doneIndex = instructionIndex(bytecode, doneOffset);
        if (doneIndex < 0 || doneIndex <= targetIndex) {
            return false;
        }
        if (hasEarlierBranchTarget(bytecode, index, doneOffset)) {
            return false;
        }
        if (containsControlTransfer(bytecode, index + 1, jumpIndex) || containsControlTransfer(bytecode, targetIndex, doneIndex)) {
            return false;
        }

        final List<StackValue> conditionStack = new ArrayList<>(stack);
        final IrExpression condition = branchCondition(classFile, method, instruction, conditionStack);
        final List<StackValue> prefix = List.copyOf(conditionStack);
        final int originalLocalDeclarationCount = localDeclarations.size();
        final Map<Integer, IrLocal> workingDeclarations = copyLocalDeclarations(localDeclarations);
        final BlockResult elseBlock = lowerLinearBlock(
            classes,
            classFile,
            method,
            bytecode,
            index + 1,
            jumpIndex,
            prefix,
            locals,
            workingDeclarations,
            dispatches
        );
        final BlockResult targetBlock = lowerLinearBlock(
            classes,
            classFile,
            method,
            bytecode,
            targetIndex,
            doneIndex,
            prefix,
            locals,
            workingDeclarations,
            dispatches
        );
        if (!hasSelectedValue(prefix, elseBlock.stack()) || !hasSelectedValue(prefix, targetBlock.stack())) {
            return false;
        }
        final StackValue elseValue = elseBlock.stack().getLast();
        final StackValue targetValue = targetBlock.stack().getLast();
        if (elseValue.kind() != targetValue.kind() || elseValue.expression().isEmpty() || targetValue.expression().isEmpty()) {
            throw unsupportedBranchValueMerge(classFile, method, instruction);
        }
        appendNewLocalDeclarations(localDeclarations, workingDeclarations, originalLocalDeclarationCount);
        branchCondition(classFile, method, instruction, stack);
        final StackKind valueKind = targetValue.kind();
        final IrType valueType = stackKindType(valueKind);
        final String localName = "branchValue" + localDeclarations.size() + "_" + instruction.offset();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(valueType, localName));
        final String targetLabel = "branch_value_target_" + instruction.offset();
        final String doneLabel = "branch_value_done_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(targetLabel, condition));
        instructions.addAll(elseBlock.instructions());
        instructions.add(assignLocal(valueKind, localName, elseValue.expression().orElseThrow()));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(targetLabel));
        instructions.addAll(targetBlock.instructions());
        instructions.add(assignLocal(valueKind, localName, targetValue.expression().orElseThrow()));
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(stackValue(valueKind, localExpression(valueType, new IrLocal(valueType, localName))));
        addInt(skippedOffsets, jumpInstruction.offset());
        addInstructionOffsets(bytecode, index + 1, jumpIndex, skippedOffsets);
        addInstructionOffsets(bytecode, targetIndex, doneIndex, skippedOffsets);
        return true;
    }

    private static boolean lowerGuardedValueSelection(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int index,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final List<Integer> skippedOffsets,
        final Instruction instruction
    ) {
        final int targetOffset = branchTarget(instruction);
        final int targetIndex = instructionIndex(bytecode, targetOffset);
        if (targetIndex < 0) {
            return false;
        }
        if (targetIndex <= index + 1) {
            return false;
        }
        final int jumpIndex = unconditionalJumpBefore(bytecode, index + 1, targetIndex);
        if (jumpIndex < 0) {
            return false;
        }
        final Instruction jumpInstruction = bytecode.get(jumpIndex);
        if (jumpInstruction.opcode() != 167) {
            return false;
        }
        final int doneOffset = branchTarget(jumpInstruction);
        final int doneIndex = instructionIndex(bytecode, doneOffset);
        if (doneIndex < 0) {
            return false;
        }
        if (doneIndex <= targetIndex) {
            return false;
        }
        if (!isValueConsumer(bytecode.get(doneIndex).opcode())) {
            return false;
        }
        if (!hasOnlyTargetBranches(bytecode, index, jumpIndex, targetOffset)) {
            return false;
        }
        if (containsControlTransfer(bytecode, targetIndex, doneIndex)) {
            return false;
        }

        final int originalLocalDeclarationCount = localDeclarations.size();
        final Map<Integer, IrLocal> workingDeclarations = copyLocalDeclarations(localDeclarations);
        final Map<Integer, IrExpression> workingLocals = copyExpressionLocals(locals);
        final List<IrInstruction> mergedInstructions = new ArrayList<>();
        final List<StackValue> workingStack = new ArrayList<>(stack);
        List<StackValue> prefix = List.of();
        final String targetLabel = "guarded_value_target_" + instruction.offset();
        final String doneLabel = "guarded_value_done_" + instruction.offset();
        for (int cursor = index; cursor < jumpIndex; cursor++) {
            final Instruction current = bytecode.get(cursor);
            if (isConditionalBranch(current.opcode())) {
                final IrExpression condition = branchCondition(classFile, method, current, workingStack);
                if (prefix.isEmpty()) {
                    prefix = List.copyOf(workingStack);
                }
                mergedInstructions.add(IrInstruction.branchIf(targetLabel, condition));
            } else {
                if (isControlTransfer(current.opcode())) {
                    return false;
                }
                lowerInstruction(
                    classes,
                    classFile,
                    method,
                    current,
                    mergedInstructions,
                    workingStack,
                    new HashMap<>(),
                    workingLocals,
                    workingDeclarations,
                    dispatches
                );
            }
        }
        if (prefix.isEmpty()) {
            return false;
        }
        if (!hasSelectedValue(prefix, workingStack)) {
            return false;
        }
        final StackValue elseValue = workingStack.getLast();
        final BlockResult targetBlock = lowerLinearBlock(
            classes,
            classFile,
            method,
            bytecode,
            targetIndex,
            doneIndex,
            prefix,
            locals,
            workingDeclarations,
            dispatches
        );
        if (!hasSelectedValue(prefix, targetBlock.stack())) {
            return false;
        }
        final StackValue targetValue = targetBlock.stack().getLast();
        if (elseValue.kind() != targetValue.kind()) {
            throw unsupportedBranchValueMerge(classFile, method, instruction);
        }
        if (elseValue.expression().isEmpty()) {
            throw unsupportedBranchValueMerge(classFile, method, instruction);
        }
        if (targetValue.expression().isEmpty()) {
            throw unsupportedBranchValueMerge(classFile, method, instruction);
        }
        appendNewLocalDeclarations(localDeclarations, workingDeclarations, originalLocalDeclarationCount);
        final StackKind valueKind = targetValue.kind();
        final IrType valueType = stackKindType(valueKind);
        final String localName = "branchValue" + localDeclarations.size() + "_" + instruction.offset();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(valueType, localName));
        instructions.addAll(mergedInstructions);
        instructions.add(assignLocal(valueKind, localName, elseValue.expression().orElseThrow()));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(targetLabel));
        instructions.addAll(targetBlock.instructions());
        instructions.add(assignLocal(valueKind, localName, targetValue.expression().orElseThrow()));
        instructions.add(IrInstruction.label(doneLabel));
        while (!stack.isEmpty()) {
            stack.removeLast();
        }
        stack.addAll(prefix);
        stack.add(stackValue(valueKind, localExpression(valueType, new IrLocal(valueType, localName))));
        addInstructionOffsets(bytecode, index + 1, doneIndex, skippedOffsets);
        return true;
    }

    private static BlockResult lowerLinearBlock(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int startIndex,
        final int endIndex,
        final List<StackValue> stackPrefix,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        final List<IrInstruction> blockInstructions = new ArrayList<>();
        final List<StackValue> blockStack = new ArrayList<>(stackPrefix);
        final Map<Integer, IrExpression> blockLocals = copyExpressionLocals(locals);
        for (int index = startIndex; index < endIndex; index++) {
            final Instruction blockInstruction = bytecode.get(index);
            if (isControlTransfer(blockInstruction.opcode())) {
                throw unsupportedBranchValueMerge(classFile, method, blockInstruction);
            }
            lowerInstruction(
                classes,
                classFile,
                method,
                blockInstruction,
                blockInstructions,
                blockStack,
                new HashMap<>(),
                blockLocals,
                localDeclarations,
                dispatches
            );
        }
        return new BlockResult(List.copyOf(blockInstructions), List.copyOf(blockStack));
    }

    private static boolean hasSelectedValue(final List<StackValue> prefix, final List<StackValue> branchStack) {
        if (branchStack.size() != prefix.size() + 1) {
            return false;
        }
        for (int index = 0; index < prefix.size(); index++) {
            if (prefix.get(index) != branchStack.get(index)) {
                return false;
            }
        }
        return true;
    }

    private static IrExpression branchCondition(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (instruction.opcode() >= 153 && instruction.opcode() <= 158) {
            return IrExpression.intComparison(
                zeroOperator(instruction.opcode()),
                popInt(classFile, method, stack),
                IrExpression.intLiteral(0)
            );
        }
        if (instruction.opcode() >= 159 && instruction.opcode() <= 164) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            return IrExpression.intComparison(intCompareOperator(instruction.opcode()), left, right);
        }
        if (instruction.opcode() == 165 || instruction.opcode() == 166) {
            final IrExpression right = popObject(classFile, method, stack);
            final IrExpression left = popObject(classFile, method, stack);
            return IrExpression.objectComparison(objectCompareOperator(instruction.opcode()), left, right);
        }
        if (instruction.opcode() == 198 || instruction.opcode() == 199) {
            final IrExpression value = popObject(classFile, method, stack);
            return IrExpression.objectComparison(nullOperator(instruction.opcode()), value, IrExpression.objectNull());
        }
        throw unsupportedBranchValueMerge(classFile, method, instruction);
    }

    private static boolean isConditionalBranch(final int opcode) {
        if (opcode >= 153) {
            if (opcode <= 166) {
                return true;
            }
        }
        if (opcode == 198) {
            return true;
        }
        if (opcode == 199) {
            return true;
        }
        return false;
    }

    private static boolean isControlTransfer(final int opcode) {
        if (opcode >= 153) {
            if (opcode <= 167) {
                return true;
            }
        }
        if (opcode == 170) {
            return true;
        }
        if (opcode == 171) {
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

    private static boolean isValueConsumer(final int opcode) {
        if (opcode >= 172) {
            if (opcode <= 176) {
                return true;
            }
        }
        if (opcode == 54) {
            return true;
        }
        if (opcode >= 59) {
            if (opcode <= 62) {
                return true;
            }
        }
        if (opcode == 55) {
            return true;
        }
        if (opcode >= 63) {
            if (opcode <= 66) {
                return true;
            }
        }
        if (opcode == 56) {
            return true;
        }
        if (opcode >= 67) {
            if (opcode <= 70) {
                return true;
            }
        }
        if (opcode == 57) {
            return true;
        }
        if (opcode >= 71) {
            if (opcode <= 74) {
                return true;
            }
        }
        if (opcode == 58) {
            return true;
        }
        if (opcode >= 75) {
            if (opcode <= 78) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOnlyTargetBranches(
        final List<Instruction> bytecode,
        final int startIndex,
        final int endIndex,
        final int targetOffset
    ) {
        for (int index = startIndex; index < endIndex; index++) {
            final Instruction instruction = bytecode.get(index);
            if (isConditionalBranch(instruction.opcode())) {
                if (branchTarget(instruction) != targetOffset) {
                    return false;
                }
            } else if (isControlTransfer(instruction.opcode())) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsControlTransfer(final List<Instruction> bytecode, final int startIndex, final int endIndex) {
        for (int index = startIndex; index < endIndex; index++) {
            if (isControlTransfer(bytecode.get(index).opcode())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEarlierBranchTarget(final List<Instruction> bytecode, final int currentIndex, final int targetOffset) {
        for (int index = 0; index < currentIndex; index++) {
            final Instruction instruction = bytecode.get(index);
            if (isSimpleBranch(instruction.opcode()) && branchTarget(instruction) == targetOffset) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimpleBranch(final int opcode) {
        if (opcode >= 153) {
            if (opcode <= 168) {
                return true;
            }
        }
        if (opcode == 198) {
            return true;
        }
        if (opcode == 199) {
            return true;
        }
        if (opcode == 200) {
            return true;
        }
        if (opcode == 201) {
            return true;
        }
        return false;
    }

    private static int unconditionalJumpBefore(final List<Instruction> bytecode, final int startIndex, final int endIndex) {
        int result = -1;
        for (int index = startIndex; index < endIndex; index++) {
            if (bytecode.get(index).opcode() == 167) {
                if (result >= 0) {
                    return -1;
                }
                result = index;
            }
        }
        return result;
    }

    private static void addInstructionOffsets(
        final List<Instruction> bytecode,
        final int startIndex,
        final int endIndex,
        final List<Integer> offsets
    ) {
        for (int index = startIndex; index < endIndex; index++) {
            addInt(offsets, bytecode.get(index).offset());
        }
    }

    private static Map<Integer, IrExpression> copyExpressionLocals(final Map<Integer, IrExpression> source) {
        final Map<Integer, IrExpression> result = new HashMap<>();
        int slot = 0;
        while (slot < 512) {
            final IrExpression value = source.get(slot);
            if (value != null) {
                result.put(slot, value);
            }
            slot++;
        }
        return result;
    }

    private static Map<Integer, IrLocal> copyLocalDeclarations(final Map<Integer, IrLocal> source) {
        final Map<Integer, IrLocal> result = new LinkedHashMap<>();
        int index = 0;
        for (final IrLocal local : source.values()) {
            result.put(Integer.MIN_VALUE + index, local);
            index++;
        }
        return result;
    }

    private static void appendNewLocalDeclarations(
        final Map<Integer, IrLocal> target,
        final Map<Integer, IrLocal> source,
        final int originalCount
    ) {
        int index = 0;
        for (final IrLocal local : source.values()) {
            if (index >= originalCount) {
                target.put(Integer.MIN_VALUE + target.size(), local);
            }
            index++;
        }
    }

    private static DiagnosticException unsupportedBranchValueMerge(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN051",
            "unsupported conditional stack merge",
            classFile.name(),
            method.name() + method.descriptor(),
            "bytecode offset " + instruction.offset(),
            "The compiler found a conditional value shape it cannot prove safe to lower.",
            "Rewrite the expression as explicit straight-line assignments or add support for this branch shape."
        ));
    }

    private static Optional<StackValue> simplePushValue(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<Integer, IrExpression> locals
    ) {
        switch (instruction.opcode()) {
            case 1:
                return Optional.of(StackValue.objectExpression(IrExpression.objectNull()));
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                return Optional.of(StackValue.intExpression(IrExpression.intLiteral(instruction.opcode() - 3)));
            case 9:
            case 10:
                return Optional.of(StackValue.longExpression(IrExpression.longLiteral(instruction.opcode() - 9L)));
            case 11:
            case 12:
            case 13:
                return Optional.of(StackValue.floatExpression(IrExpression.floatLiteral(instruction.opcode() - 11.0f)));
            case 14:
            case 15:
                return Optional.of(StackValue.doubleExpression(IrExpression.doubleLiteral(instruction.opcode() - 14.0)));
            case 16:
                return Optional.of(StackValue.intExpression(IrExpression.intLiteral(signedByte(instruction.operands()[0]))));
            case 17:
                return Optional.of(StackValue.intExpression(IrExpression.intLiteral(signedShort(instruction.operands()))));
            case 18:
            case 19:
            case 20:
                return simpleConstantValue(classFile, method, instruction);
            case 21:
                return Optional.of(StackValue.intExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.INT)));
            case 22:
                return Optional.of(StackValue.longExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.LONG)));
            case 23:
                return Optional.of(StackValue.floatExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.FLOAT)));
            case 24:
                return Optional.of(StackValue.doubleExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.DOUBLE)));
            case 25:
                return Optional.of(StackValue.objectExpression(local(classFile, method, locals, unsigned(instruction.operands()[0]), IrType.OBJECT)));
            case 26:
            case 27:
            case 28:
            case 29:
                return Optional.of(StackValue.intExpression(local(classFile, method, locals, instruction.opcode() - 26, IrType.INT)));
            case 30:
            case 31:
            case 32:
            case 33:
                return Optional.of(StackValue.longExpression(local(classFile, method, locals, instruction.opcode() - 30, IrType.LONG)));
            case 34:
            case 35:
            case 36:
            case 37:
                return Optional.of(StackValue.floatExpression(local(classFile, method, locals, instruction.opcode() - 34, IrType.FLOAT)));
            case 38:
            case 39:
            case 40:
            case 41:
                return Optional.of(StackValue.doubleExpression(local(classFile, method, locals, instruction.opcode() - 38, IrType.DOUBLE)));
            case 42:
            case 43:
            case 44:
            case 45:
                return Optional.of(StackValue.objectExpression(local(classFile, method, locals, instruction.opcode() - 42, IrType.OBJECT)));
            default:
                return Optional.empty();
        }
    }

    private static Optional<StackValue> simpleConstantValue(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        if (instruction.stringValue().isPresent()) {
            return Optional.of(StackValue.objectExpression(IrExpression.stringLiteral(instruction.stringValue().orElseThrow())));
        }
        if (instruction.intValue().isPresent()) {
            return Optional.of(StackValue.intExpression(IrExpression.intLiteral(instruction.intValue().orElseThrow())));
        }
        if (instruction.longValue().isPresent()) {
            return Optional.of(StackValue.longExpression(IrExpression.longLiteral(instruction.longValue().orElseThrow())));
        }
        if (instruction.floatValue().isPresent()) {
            return Optional.of(StackValue.floatExpression(IrExpression.floatLiteral(instruction.floatValue().orElseThrow())));
        }
        if (instruction.doubleValue().isPresent()) {
            return Optional.of(StackValue.doubleExpression(IrExpression.doubleLiteral(instruction.doubleValue().orElseThrow())));
        }
        throw unsupported(classFile, method, instruction);
    }

    private static int instructionIndex(final List<Instruction> instructions, final int offset) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() == offset) {
                return index;
            }
        }
        return -1;
    }

    private static int instructionOffsetAfter(final List<Instruction> instructions, final int index) {
        if (index + 1 >= instructions.size()) {
            return -1;
        }
        return instructions.get(index + 1).offset();
    }

    private static IrType stackKindType(final StackKind kind) {
        if (kind == StackKind.INT) {
            return IrType.INT;
        }
        if (kind == StackKind.LONG) {
            return IrType.LONG;
        }
        if (kind == StackKind.FLOAT) {
            return IrType.FLOAT;
        }
        if (kind == StackKind.DOUBLE) {
            return IrType.DOUBLE;
        }
        if (kind == StackKind.OBJECT) {
            return IrType.OBJECT;
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }

    private static IrInstruction assignLocal(final StackKind kind, final String localName, final IrExpression expression) {
        if (kind == StackKind.INT) {
            return IrInstruction.assignInt(localName, expression);
        }
        if (kind == StackKind.LONG) {
            return IrInstruction.assignLong(localName, expression);
        }
        if (kind == StackKind.FLOAT) {
            return IrInstruction.assignFloat(localName, expression);
        }
        if (kind == StackKind.DOUBLE) {
            return IrInstruction.assignDouble(localName, expression);
        }
        if (kind == StackKind.OBJECT) {
            return IrInstruction.assignObject(localName, expression);
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }

    private static StackValue stackValue(final StackKind kind, final IrExpression expression) {
        if (kind == StackKind.INT) {
            return StackValue.intExpression(expression);
        }
        if (kind == StackKind.LONG) {
            return StackValue.longExpression(expression);
        }
        if (kind == StackKind.FLOAT) {
            return StackValue.floatExpression(expression);
        }
        if (kind == StackKind.DOUBLE) {
            return StackValue.doubleExpression(expression);
        }
        if (kind == StackKind.OBJECT) {
            return StackValue.objectExpression(expression);
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }

    private static void tableSwitch(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final int padding = switchPadding(instruction.offset());
        final int defaultTarget = instruction.offset() + int32(instruction.operands(), padding);
        final int low = int32(instruction.operands(), padding + 4);
        final int high = int32(instruction.operands(), padding + 8);
        final IrExpression selector = switchSelector(classFile, method, instructions, stack, localDeclarations);
        int operandOffset = padding + 12;
        for (int value = low; value <= high; value++) {
            final int target = instruction.offset() + int32(instruction.operands(), operandOffset);
            instructions.add(IrInstruction.branchIf(
                label(target),
                IrExpression.intComparison("==", selector, IrExpression.intLiteral(value))
            ));
            operandOffset += 4;
        }
        instructions.add(IrInstruction.jump(label(defaultTarget)));
    }

    private static void lookupSwitch(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final int padding = switchPadding(instruction.offset());
        final int defaultTarget = instruction.offset() + int32(instruction.operands(), padding);
        final int pairs = int32(instruction.operands(), padding + 4);
        final IrExpression selector = switchSelector(classFile, method, instructions, stack, localDeclarations);
        int operandOffset = padding + 8;
        for (int index = 0; index < pairs; index++) {
            final int value = int32(instruction.operands(), operandOffset);
            final int target = instruction.offset() + int32(instruction.operands(), operandOffset + 4);
            instructions.add(IrInstruction.branchIf(
                label(target),
                IrExpression.intComparison("==", selector, IrExpression.intLiteral(value))
            ));
            operandOffset += 8;
        }
        instructions.add(IrInstruction.jump(label(defaultTarget)));
    }

    private static IrExpression switchSelector(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final String localName = "switch" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.INT, localName));
        instructions.add(IrInstruction.assignInt(localName, value));
        return IrExpression.intLocal(localName);
    }

    private static IrExpression popInt(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.INT) {
            return value.expression().orElseThrow();
        }
        throw unsupported(classFile, method, method.code().orElseThrow().instructions().getFirst());
    }

    private static IrExpression popObject(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, method.code().orElseThrow().instructions().getFirst(), "An object value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.OBJECT) {
            return value.expression().orElseThrow();
        }
        if (value.kind() == StackKind.PRINT_STREAM) {
            return IrExpression.objectCall("javan_system_out", List.of());
        }
        if (value.kind() == StackKind.ERROR_PRINT_STREAM) {
            return IrExpression.objectCall("javan_system_err", List.of());
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

    private static void discardTop(final List<IrInstruction> instructions, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.expression().isPresent()) {
            final IrExpression expression = value.expression().orElseThrow();
            if (expression.kind() == IrExpression.Kind.CALL) {
                instructions.add(IrInstruction.callStaticVoid(expression.value(), expression.arguments()));
            }
        }
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
            if (existing.type() == type) {
                return existing;
            }
        }
        final IrLocal local = new IrLocal(type, localName(slot, type, localDeclarations.size()));
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), local);
        final IrExpression expression = localExpression(type, local);
        locals.put(slot, expression);
        return expression;
    }

    private static String localName(final int slot, final IrType type, final int ordinal) {
        if (ordinal == 0) {
            return "local" + slot;
        }
        return "local" + slot + "_" + Strings2.toAsciiLowerCase(type.name()) + "_" + ordinal;
    }

    private static IrExpression localExpression(final IrType type, final IrLocal local) {
        if (type == IrType.INT) {
            return IrExpression.intLocal(local.name());
        }
        if (type == IrType.LONG) {
            return IrExpression.longLocal(local.name());
        }
        if (type == IrType.FLOAT) {
            return IrExpression.floatLocal(local.name());
        }
        if (type == IrType.DOUBLE) {
            return IrExpression.doubleLocal(local.name());
        }
        if (type == IrType.OBJECT) {
            return IrExpression.objectLocal(local.name());
        }
        if (type == IrType.VOID) {
            throw new IllegalArgumentException("void local is invalid");
        }
        throw new IllegalStateException("Unsupported IR type");
    }

    private static StackValue pop(final List<StackValue> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Invalid bytecode stack");
        }
        return stack.removeLast();
    }

    private static boolean isIgnoredNoop(final int opcode) {
        if (opcode == 0) {
            return true;
        }
        return false;
    }

    private static List<Integer> branchTargets(final CodeAttribute code) {
        final List<Integer> result = new ArrayList<>();
        for (final Instruction instruction : code.instructions()) {
            if (isBranchTargetOpcode(instruction.opcode())) {
                addInt(result, branchTarget(instruction));
            } else if (instruction.opcode() == 170) {
                addTableSwitchTargets(result, instruction);
            } else if (instruction.opcode() == 171) {
                addLookupSwitchTargets(result, instruction);
            }
        }
        for (final javan.classfile.CodeException handler : code.exceptionTable()) {
            addInt(result, handler.handlerPc());
        }
        return List.copyOf(result);
    }

    private static boolean isBranchTargetOpcode(final int opcode) {
        if (opcode >= 153) {
            if (opcode <= 167) {
                return true;
            }
        }
        if (opcode == 198) {
            return true;
        }
        if (opcode == 199) {
            return true;
        }
        return false;
    }

    private static List<Integer> exceptionHandlerOffsets(final CodeAttribute code) {
        final List<Integer> result = new ArrayList<>();
        for (final javan.classfile.CodeException handler : code.exceptionTable()) {
            addInt(result, handler.handlerPc());
        }
        return List.copyOf(result);
    }

    private static List<Integer> ignoredEnumSwitchMapHandlerOffsets(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code
    ) {
        final List<Integer> result = new ArrayList<>();
        for (final javan.classfile.CodeException handler : code.exceptionTable()) {
            if (supportedEnumSwitchMapHandler(classes, classFile, method, code, handler)) {
                addInt(result, handler.handlerPc());
            }
        }
        return List.copyOf(result);
    }

    private static void addInt(final List<Integer> values, final int value) {
        if (!containsInt(values, value)) {
            values.add(value);
        }
    }

    private static boolean containsInt(final List<Integer> values, final int target) {
        for (final int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSkipOffset(final List<Integer> ignoredHandlerOffsets, final List<Integer> skippedOffsets, final int offset) {
        if (containsInt(ignoredHandlerOffsets, offset)) {
            return true;
        }
        return containsInt(skippedOffsets, offset);
    }

    private static boolean supportedEnumSwitchMapHandler(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code,
        final javan.classfile.CodeException handler
    ) {
        if (!isSyntheticSwitchMapInitializer(classFile, method)) {
            return false;
        }
        final Optional<String> catchType = handler.catchType();
        if (catchType.isEmpty()) {
            return false;
        }
        if (!"java/lang/NoSuchFieldError".equals(catchType.orElseThrow())) {
            return false;
        }
        final Optional<Instruction> handlerInstruction = instructionAtOffset(code, handler.handlerPc());
        if (handlerInstruction.isEmpty()) {
            return false;
        }
        if (!isEnumSwitchMapHandlerInstruction(handlerInstruction.orElseThrow().opcode())) {
            return false;
        }
        return true;
    }

    private static boolean isSwitchMapInitializer(final ClassFile classFile, final MethodInfo method) {
        if (!"<clinit>".equals(method.name())) {
            return false;
        }
        if (!"()V".equals(method.descriptor())) {
            return false;
        }
        for (final FieldInfo field : classFile.fields()) {
            if (isSwitchMapField(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSyntheticSwitchMapInitializer(final ClassFile classFile, final MethodInfo method) {
        if (isSwitchMapInitializer(classFile, method)) {
            return true;
        }
        if (!"<clinit>".equals(method.name())) {
            return false;
        }
        if (!"()V".equals(method.descriptor())) {
            return false;
        }
        return endsWithDollarOne(classFile.name());
    }

    private static boolean endsWithDollarOne(final String value) {
        if (value.length() < 2) {
            return false;
        }
        if (value.charAt(value.length() - 2) != '$') {
            return false;
        }
        if (value.charAt(value.length() - 1) == '1') {
            return true;
        }
        return false;
    }

    private static boolean isSwitchMapField(final FieldInfo field) {
        if (!"[I".equals(field.descriptor())) {
            return false;
        }
        return startsWithSwitchMapPrefix(field.name());
    }

    private static boolean startsWithSwitchMapPrefix(final String value) {
        final String prefix = "$SwitchMap$";
        if (value.length() < prefix.length()) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (value.charAt(index) != prefix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Instruction> instructionAtOffset(final CodeAttribute code, final int offset) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() == offset) {
                return Optional.of(instruction);
            }
        }
        return Optional.empty();
    }

    private static boolean supportedEnumSwitchMapInstruction(final Map<String, ClassFile> classes, final Instruction instruction) {
        if (instruction.opcode() == 79) {
            return true;
        }
        if (isAload(instruction.opcode())) {
            return true;
        }
        if (isSmallIntConstant(instruction.opcode())) {
            return true;
        }
        if (instruction.opcode() == 16) {
            return true;
        }
        if (instruction.opcode() == 17) {
            return true;
        }
        if (instruction.opcode() == 178) {
            final Optional<FieldRef> maybeFieldRef = instruction.fieldRef();
            if (maybeFieldRef.isEmpty()) {
                return false;
            }
            final FieldRef fieldRef = maybeFieldRef.orElseThrow();
            final ClassFile owner = classes.get(fieldRef.owner());
            if ("[I".equals(fieldRef.descriptor())) {
                return true;
            }
            if (owner == null) {
                return false;
            }
            return owner.isEnum();
        }
        if (instruction.opcode() == 182) {
            final Optional<MethodRef> maybeMethodRef = instruction.methodRef();
            if (maybeMethodRef.isEmpty()) {
                return false;
            }
            final MethodRef methodRef = maybeMethodRef.orElseThrow();
            final ClassFile owner = classes.get(methodRef.owner());
            if (owner == null) {
                return false;
            }
            if (!owner.isEnum()) {
                return false;
            }
            if (!"ordinal".equals(methodRef.name())) {
                return false;
            }
            if ("()I".equals(methodRef.descriptor())) {
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean isAstore(final int opcode) {
        if (opcode == 58) {
            return true;
        }
        if (opcode == 75) {
            return true;
        }
        if (opcode == 76) {
            return true;
        }
        if (opcode == 77) {
            return true;
        }
        if (opcode == 78) {
            return true;
        }
        return false;
    }

    private static boolean isSmallIntConstant(final int opcode) {
        if (opcode == 2) {
            return true;
        }
        if (opcode == 3) {
            return true;
        }
        if (opcode == 4) {
            return true;
        }
        if (opcode == 5) {
            return true;
        }
        if (opcode == 6) {
            return true;
        }
        if (opcode == 7) {
            return true;
        }
        if (opcode == 8) {
            return true;
        }
        return false;
    }

    private static boolean isAload(final int opcode) {
        if (opcode == 25) {
            return true;
        }
        if (opcode == 42) {
            return true;
        }
        if (opcode == 43) {
            return true;
        }
        if (opcode == 44) {
            return true;
        }
        if (opcode == 45) {
            return true;
        }
        return false;
    }

    private static boolean isEnumSwitchMapHandlerInstruction(final int opcode) {
        if (opcode == 87) {
            return true;
        }
        return isAstore(opcode);
    }

    private static void addTableSwitchTargets(final List<Integer> result, final Instruction instruction) {
        final int padding = switchPadding(instruction.offset());
        addInt(result, instruction.offset() + int32(instruction.operands(), padding));
        final int low = int32(instruction.operands(), padding + 4);
        final int high = int32(instruction.operands(), padding + 8);
        int operandOffset = padding + 12;
        for (int value = low; value <= high; value++) {
            addInt(result, instruction.offset() + int32(instruction.operands(), operandOffset));
            operandOffset += 4;
        }
    }

    private static void addLookupSwitchTargets(final List<Integer> result, final Instruction instruction) {
        final int padding = switchPadding(instruction.offset());
        addInt(result, instruction.offset() + int32(instruction.operands(), padding));
        final int pairs = int32(instruction.operands(), padding + 4);
        int operandOffset = padding + 8;
        for (int index = 0; index < pairs; index++) {
            addInt(result, instruction.offset() + int32(instruction.operands(), operandOffset + 4));
            operandOffset += 8;
        }
    }

    private static int branchTarget(final Instruction instruction) {
        return instruction.offset() + signedShort(instruction.operands());
    }

    private static String label(final int offset) {
        return "label_" + offset;
    }

    private static String zeroOperator(final int opcode) {
        if (opcode == 153) {
            return "==";
        }
        if (opcode == 154) {
            return "!=";
        }
        if (opcode == 155) {
            return "<";
        }
        if (opcode == 156) {
            return ">=";
        }
        if (opcode == 157) {
            return ">";
        }
        if (opcode == 158) {
            return "<=";
        }
        throw new IllegalArgumentException("Unsupported zero branch opcode " + opcode);
    }

    private static String intCompareOperator(final int opcode) {
        if (opcode == 159) {
            return "==";
        }
        if (opcode == 160) {
            return "!=";
        }
        if (opcode == 161) {
            return "<";
        }
        if (opcode == 162) {
            return ">=";
        }
        if (opcode == 163) {
            return ">";
        }
        if (opcode == 164) {
            return "<=";
        }
        throw new IllegalArgumentException("Unsupported int compare opcode " + opcode);
    }

    private static String objectCompareOperator(final int opcode) {
        if (opcode == 165) {
            return "==";
        }
        if (opcode == 166) {
            return "!=";
        }
        throw new IllegalArgumentException("Unsupported object compare opcode " + opcode);
    }

    private static String nullOperator(final int opcode) {
        if (opcode == 198) {
            return "==";
        }
        if (opcode == 199) {
            return "!=";
        }
        throw new IllegalArgumentException("Unsupported null branch opcode " + opcode);
    }

    private static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor - offset - 1;
    }

    private static int int32(final byte[] operands, final int offset) {
        return (unsigned(operands[offset]) << 24)
            | (unsigned(operands[offset + 1]) << 16)
            | (unsigned(operands[offset + 2]) << 8)
            | unsigned(operands[offset + 3]);
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
            instructionSubject(instruction),
            "The verifier allowed the program shape, but this backend slice cannot emit C for this instruction yet.",
            "Keep reachable code to supported ints, exact object fields, constructors, and static/final-class calls for this version."
        ));
    }

    private static DiagnosticException invalidStack(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final String reason
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN049",
            "bytecode stack shape is not supported",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            reason,
            "Add lowering for the preceding bytecode pattern or keep this method outside the native closed world."
        ));
    }

    private static String instructionSubject(final Instruction instruction) {
        if (instruction.methodRef().isPresent()) {
            final MethodRef ref = instruction.methodRef().orElseThrow();
            return instruction.mnemonic() + " " + ref.owner() + "." + ref.name() + ref.descriptor();
        }
        if (instruction.fieldRef().isPresent()) {
            final FieldRef ref = instruction.fieldRef().orElseThrow();
            return instruction.mnemonic() + " " + ref.owner() + "." + ref.name() + ":" + ref.descriptor();
        }
        if (instruction.className().isPresent()) {
            return instruction.mnemonic() + " " + instruction.className().orElseThrow();
        }
        return instruction.mnemonic();
    }

    private static boolean isConcreteExactCallTarget(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile target = classes.get(owner);
        if (target == null) {
            return false;
        }
        if (target.isInterface()) {
            return false;
        }
        if (target.isFinal()) {
            return true;
        }
        for (final ClassFile candidate : classes.values()) {
            if (owner.equals(candidate.superName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEnumConstant(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
        final ClassFile owner = classes.get(fieldRef.owner());
        if (owner == null) {
            return false;
        }
        if (!owner.isEnum()) {
            return false;
        }
        for (final FieldInfo field : owner.fields()) {
            if (matchingEnumConstant(field, fieldRef)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchingEnumConstant(final FieldInfo field, final FieldRef fieldRef) {
        if (!field.isEnumConstant()) {
            return false;
        }
        if (!field.name().equals(fieldRef.name())) {
            return false;
        }
        return field.descriptor().equals(fieldRef.descriptor());
    }

    private static boolean isSupportedJdkEnumConstant(final FieldRef fieldRef) {
        if (isStandardCopyReplaceExisting(fieldRef)) {
            return true;
        }
        return isLinkOptionNoFollowLinks(fieldRef);
    }

    private static boolean isStandardCopyReplaceExisting(final FieldRef fieldRef) {
        if (!"java/nio/file/StandardCopyOption".equals(fieldRef.owner())) {
            return false;
        }
        if (!"REPLACE_EXISTING".equals(fieldRef.name())) {
            return false;
        }
        return "Ljava/nio/file/StandardCopyOption;".equals(fieldRef.descriptor());
    }

    private static boolean isLinkOptionNoFollowLinks(final FieldRef fieldRef) {
        if (!"java/nio/file/LinkOption".equals(fieldRef.owner())) {
            return false;
        }
        if (!"NOFOLLOW_LINKS".equals(fieldRef.name())) {
            return false;
        }
        return "Ljava/nio/file/LinkOption;".equals(fieldRef.descriptor());
    }

    private static boolean isEnumIntrinsic(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final ClassFile owner = classes.get(methodRef.owner());
        if (!isEnumOwner(owner, methodRef.owner())) {
            return false;
        }
        if (!isEnumStringMethod(methodRef.name())) {
            return false;
        }
        return "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isEnumOrdinal(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final ClassFile owner = classes.get(methodRef.owner());
        if (owner == null) {
            return false;
        }
        if (!owner.isEnum()) {
            return false;
        }
        if (!"ordinal".equals(methodRef.name())) {
            return false;
        }
        return "()I".equals(methodRef.descriptor());
    }

    private static boolean isEnumOwner(final ClassFile owner, final String methodOwner) {
        if ("java/lang/Enum".equals(methodOwner)) {
            return true;
        }
        if (owner == null) {
            return false;
        }
        return owner.isEnum();
    }

    private static boolean isEnumStringMethod(final String methodName) {
        if ("name".equals(methodName)) {
            return true;
        }
        return "toString".equals(methodName);
    }

    private static Optional<Integer> enumOrdinal(final ClassFile enumClass, final String constant) {
        if (enumClass == null || !enumClass.isEnum()) {
            return Optional.empty();
        }
        final List<String> constants = enumConstants(enumClass);
        for (int index = 0; index < constants.size(); index++) {
            if (constants.get(index).equals(constant)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private static DiagnosticException unsupportedEnumConstant(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final String constant
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN043",
            "enum constant cannot be lowered",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.owner() + "." + constant,
            "The enum ordinal helper could not match the constant against the parsed enum fields.",
            "Recompile the enum and ensure its constants are present in the classpath."
        ));
    }

    private static String enumOrdinalSymbol(final String owner) {
        return "javan_enum_ordinal_" + sanitize(owner);
    }

    private static Optional<IrType> staticFieldType(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
        final ClassFile owner = classes.get(fieldRef.owner());
        if (owner == null) {
            return Optional.empty();
        }
        for (final FieldInfo field : owner.fields()) {
            if (field.isStatic()
                && field.name().equals(fieldRef.name())
                && field.descriptor().equals(fieldRef.descriptor())) {
                return fieldType(field.descriptor());
            }
        }
        return Optional.empty();
    }

    private static List<EntryPoint> interfaceTargets(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (!candidate.isInterface()
                && candidate.interfaces().contains(methodRef.owner())
                && candidate.method(methodRef.name(), methodRef.descriptor()).isPresent()) {
                result.add(new EntryPoint(candidate.name(), methodRef.name(), methodRef.descriptor()));
            }
        }
        return List.copyOf(result);
    }

    private static List<EntryPoint> virtualTargets(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (!candidate.isInterface() && isSubtypeOf(classes, candidate.name(), methodRef.owner())) {
                final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, candidate.name(), methodRef);
                if (resolved.isPresent()) {
                    final EntryPoint entryPoint = resolved.orElseThrow();
                    if (!containsEntry(result, entryPoint)) {
                        result.add(entryPoint);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean containsEntry(final List<EntryPoint> entries, final EntryPoint target) {
        for (final EntryPoint entry : entries) {
            if (entry.className().equals(target.className())
                && entry.methodName().equals(target.methodName())
                && entry.descriptor().equals(target.descriptor())) {
                return true;
            }
        }
        return false;
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

    private static List<Integer> assignableTypeIds(final Map<String, ClassFile> classes, final String target) {
        final List<Integer> result = new ArrayList<>();
        final List<ClassFile> sorted = sortedClasses(classes);
        for (int index = 0; index < sorted.size(); index++) {
            final ClassFile candidate = sorted.get(index);
            if (!candidate.isInterface() && isAssignableTo(classes, candidate.name(), target)) {
                result.add(index + 1);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isAssignableTo(final Map<String, ClassFile> classes, final String candidate, final String expected) {
        if ("java/lang/Object".equals(expected)) {
            return classes.containsKey(candidate);
        }
        String current = candidate;
        final List<String> visitedClasses = new ArrayList<>();
        while (current != null && !current.isEmpty()) {
            if (current.equals(expected)) {
                return true;
            }
            if (containsString(visitedClasses, current)) {
                return false;
            }
            visitedClasses.add(current);
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                return current.equals(expected);
            }
            if (hasInterface(classes, classFile, expected, new ArrayList<>())) {
                return true;
            }
            current = classFile.superName();
        }
        return false;
    }

    private static boolean hasInterface(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final String expected,
        final List<String> visited
    ) {
        for (final String interfaceName : classFile.interfaces()) {
            if (interfaceName.equals(expected)) {
                return true;
            }
            if (containsString(visited, interfaceName)) {
                continue;
            }
            visited.add(interfaceName);
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null && hasInterface(classes, interfaceClass, expected, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsString(final List<String> values, final String target) {
        for (final String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Integer> platformWrapperTypeId(final String target) {
        if ("java/lang/Integer".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_INTEGER);
        }
        if ("java/lang/Long".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_LONG);
        }
        if ("java/lang/Float".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_FLOAT);
        }
        if ("java/lang/Double".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_DOUBLE);
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

    private static DiagnosticException unsupportedInstanceOfTarget(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final String target
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN045",
            "instanceof target is not supported",
            classFile.name(),
            method.name() + method.descriptor(),
            instruction.mnemonic() + " " + target,
            "The native runtime only has deterministic type metadata for application classes and supported boxed primitive wrappers.",
            "Keep instanceof targets to application classes/interfaces, Object, or supported wrappers until this runtime model expands."
        ));
    }

    private static boolean isNoopPlatformConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Object".equals(methodRef.owner())) {
            return "()V".equals(methodRef.descriptor());
        }
        if ("java/lang/Record".equals(methodRef.owner())) {
            return "()V".equals(methodRef.descriptor());
        }
        if ("java/lang/Enum".equals(methodRef.owner())) {
            return "(Ljava/lang/String;I)V".equals(methodRef.descriptor());
        }
        return false;
    }

    private static boolean isPlatformThrowableStringConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if (!"(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    private static boolean isPlatformThrowableDefaultConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if (!"()V".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    private static boolean isPlatformThrowableGetMessage(final MethodRef methodRef) {
        if (!"getMessage".equals(methodRef.name())) {
            return false;
        }
        if (!"()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    private static boolean isKnownPlatformThrowable(final String owner) {
        return JdkCallSupport.isPlatformThrowable(owner);
    }

    private static void updatePendingThrowableMessage(final List<StackValue> stack, final IrExpression message) {
        if (!stack.isEmpty()) {
            final StackValue current = stack.getLast();
            if (current.throwableType().isPresent()) {
                stack.set(stack.size() - 1, StackValue.platformThrowable(current.throwableType().orElseThrow(), message));
                return;
            }
            stack.set(stack.size() - 1, StackValue.objectExpression(message));
        }
    }

    private static DiagnosticException unsupportedTypedExceptionHandler(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN014",
            "exception handler needs a known thrown type",
            classFile.name(),
            method.name() + method.descriptor(),
            instruction.mnemonic(),
            "The native backend only routes catch blocks when the thrown platform exception type is known during bytecode lowering.",
            "Throw a directly constructed platform exception inside the try block, or keep this path on the JVM until full exception objects are supported."
        ));
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
        ERROR_PRINT_STREAM,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        OBJECT
    }

    private record BlockResult(List<IrInstruction> instructions, List<StackValue> stack) {
    }

    private record StackValue(StackKind kind, Optional<String> throwableType, Optional<IrExpression> expression) {
        static StackValue printStream() {
            return new StackValue(StackKind.PRINT_STREAM, Optional.empty(), Optional.empty());
        }

        static StackValue errorPrintStream() {
            return new StackValue(StackKind.ERROR_PRINT_STREAM, Optional.empty(), Optional.empty());
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

        static StackValue platformThrowable(final String throwableType, final IrExpression message) {
            return new StackValue(StackKind.OBJECT, Optional.of(throwableType), Optional.of(message));
        }
    }
}
