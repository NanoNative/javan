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
import javan.compat.JavanNativeSubstitutions;
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
import javan.ir.IrSourceLocation;
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
    static final int TYPE_JAVA_LANG_INTEGER = -1001;
    static final int TYPE_JAVA_LANG_LONG = -1002;
    static final int TYPE_JAVA_LANG_FLOAT = -1003;
    static final int TYPE_JAVA_LANG_DOUBLE = -1004;
    static final int TYPE_JAVA_LANG_BOOLEAN = -1005;

    /**
     * Lowers reachable methods to IR.
     *
     * @param classes parsed classes
     * @param callGraph reachable call graph
     * @return lowered IR program
     */
    public IrProgram lower(final Map<String, ClassFile> classes, final CallGraph callGraph) {
        return lower(classes, callGraph, SourceLineIndex.empty());
    }

    /**
     * Lowers reachable methods to IR with source-line lookup for diagnostics.
     *
     * @param classes parsed classes
     * @param callGraph reachable call graph
     * @param sourceLines source-line index
     * @return lowered IR program
     */
    public IrProgram lower(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final SourceLineIndex sourceLines
    ) {
        final List<IrFunction> functions = new ArrayList<>();
        final Map<String, IrDispatch> dispatches = new LinkedHashMap<>();
        final List<EntryPoint> reachableMethods = BytecodeToIRMetadataSupport.sortedEntryPoints(callGraph.reachableMethods());
        final List<EntryPoint> runnableThreadTargets = BytecodeToIRInvokeSupport.runnableThreadTargets(classes, reachableMethods);
        if (!runnableThreadTargets.isEmpty()) {
            final MethodRef runnableRun = BytecodeToIRInvokeSupport.runnableRunMethodRef();
            final String dispatchSymbol = dispatchSymbol(runnableRun);
            dispatches.putIfAbsent(
                dispatchSymbol,
                BytecodeToIRInvokeSupport.dispatch(
                    dispatchSymbol,
                    MethodDescriptor.parse(runnableRun.descriptor()),
                    runnableThreadTargets
                )
            );
        }
        for (final EntryPoint reachable : reachableMethods) {
            functions.add(lowerFunction(classes, reachable, dispatches, sourceLines));
        }
        return new IrProgram(BytecodeToIRMetadataSupport.lowerClasses(classes), List.copyOf(functions), List.copyOf(dispatches.values()), symbol(callGraph.entryPoint()));
    }

    static IrFunction lowerFunction(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint,
        final Map<String, IrDispatch> dispatches,
        final SourceLineIndex sourceLines
    ) {
        final ClassFile classFile = classes.get(entryPoint.className());
        final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElseThrow();
        final MethodDescriptor descriptor = MethodDescriptor.parse(method.descriptor());
        final List<IrParameter> parameters = BytecodeToIRMetadataSupport.parameters(method, descriptor);
        final List<IrInstruction> instructions = new ArrayList<>();
        final List<StackValue> stack = new ArrayList<>();
        final Map<Integer, IrExpression> locals = new HashMap<>();
        final Map<Integer, StackKind> objectLocalKinds = new HashMap<>();
        final Map<Integer, IrLocal> localDeclarations = new LinkedHashMap<>();
        final Map<Integer, StackValue> pendingExceptionHandlerStacks = new HashMap<>();
        final CodeAttribute code = method.code().orElseThrow();
        final List<Instruction> bytecode = code.instructions();
        final List<Integer> ignoredHandlerOffsets = ignoredEnumSwitchMapHandlerOffsets(classes, classFile, method, code);
        final List<Integer> handlerOffsets = exceptionHandlerOffsets(code);
        final List<Integer> branchTargets = branchTargets(code);
        final List<Integer> skippedOffsets = new ArrayList<>();
        BytecodeToIRMetadataSupport.bindParameters(method, descriptor, parameters, locals);
        for (int index = 0; index < bytecode.size(); index++) {
            final Instruction instruction = bytecode.get(index);
            if (containsInt(branchTargets, instruction.offset())) {
                instructions.add(IrInstruction.label(label(instruction.offset())));
            }
            if (containsInt(handlerOffsets, instruction.offset())) {
                final StackValue pendingException = pendingExceptionHandlerStacks.get(instruction.offset());
                if (pendingException != null) {
                    BytecodeToIRControlFlowSupport.clearStack(stack);
                    stack.add(pendingException);
                } else if (stack.isEmpty()) {
                    stack.add(StackValue.objectExpression(IrExpression.objectNull()));
                }
            }
            if (shouldSkipOffset(ignoredHandlerOffsets, skippedOffsets, instruction.offset())) {
                continue;
            }
            final int instructionStart = instructions.size();
            final Optional<IrSourceLocation> sourceLocation = BytecodeToIRControlFlowSupport.generatedStatementSourceLocation(
                classFile,
                method,
                instruction,
                sourceLines
            );
            if (BytecodeToIRControlFlowSupport.lowerBranchValueSelection(
                classes,
                classFile,
                method,
                bytecode,
                index,
                instructions,
                stack,
                locals,
                objectLocalKinds,
                localDeclarations,
                dispatches,
                skippedOffsets
            )) {
                BytecodeToIRControlFlowSupport.annotateNewInstructions(instructions, instructionStart, sourceLocation);
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
                objectLocalKinds,
                localDeclarations,
                dispatches,
                sourceLines
            );
            BytecodeToIRControlFlowSupport.annotateNewInstructions(instructions, instructionStart, sourceLocation);
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










    static void lowerInstruction(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final SourceLineIndex sourceLines
    ) {
        switch (instruction.opcode()) {
            case 1:
                stack.add(StackValue.objectExpression(IrExpression.objectNull()));
                break;
            case 177:
                instructions.add(IrInstruction.returnVoid());
                break;
            case 172:
                instructions.add(IrInstruction.returnInt(popInt(classFile, method, instruction, stack)));
                break;
            case 173:
                instructions.add(IrInstruction.returnLong(popLong(classFile, method, instruction, stack)));
                break;
            case 174:
                instructions.add(IrInstruction.returnFloat(popFloat(classFile, method, instruction, stack)));
                break;
            case 175:
                instructions.add(IrInstruction.returnDouble(popDouble(classFile, method, instruction, stack)));
                break;
            case 176:
                if (stack.isEmpty()) {
                    throw invalidStack(classFile, method, instruction, "An object return did not have a value on the bytecode stack.");
                }
                instructions.add(IrInstruction.returnObject(popObject(classFile, method, instruction, stack)));
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
                stack.add(localObjectValue(classFile, method, locals, objectLocalKinds, unsigned(instruction.operands()[0])));
                break;
            case 42:
            case 43:
            case 44:
            case 45:
                stack.add(localObjectValue(classFile, method, locals, objectLocalKinds, instruction.opcode() - 42));
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
                storeObject(classFile, method, instruction, instructions, stack, locals, objectLocalKinds, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 75:
            case 76:
            case 77:
            case 78:
                storeObject(classFile, method, instruction, instructions, stack, locals, objectLocalKinds, localDeclarations, instruction.opcode() - 75);
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
                BytecodeToIRControlFlowSupport.branchZero(classFile, method, instruction, instructions, stack);
                break;
            case 159:
            case 160:
            case 161:
            case 162:
            case 163:
            case 164:
                BytecodeToIRControlFlowSupport.branchIntCompare(classFile, method, instruction, instructions, stack);
                break;
            case 165:
            case 166:
                BytecodeToIRControlFlowSupport.branchObjectCompare(classFile, method, instruction, instructions, stack);
                break;
            case 167:
                instructions.add(IrInstruction.jump(label(branchTarget(instruction))));
                break;
            case 170:
                BytecodeToIRControlFlowSupport.tableSwitch(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 171:
                BytecodeToIRControlFlowSupport.lookupSwitch(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 198:
            case 199:
                BytecodeToIRControlFlowSupport.branchObjectNull(classFile, method, instruction, instructions, stack);
                break;
            case 178:
                BytecodeToIRInvokeSupport.pushField(classes, classFile, method, instruction, stack);
                break;
            case 179:
                BytecodeToIRInvokeSupport.assignStaticField(classes, classFile, method, instruction, instructions, stack);
                break;
            case 18:
            case 19:
            case 20:
                BytecodeToIRInvokeSupport.pushConstant(classFile, method, instruction, stack);
                break;
            case 180:
                BytecodeToIRInvokeSupport.pushInstanceField(classFile, method, instruction, stack);
                break;
            case 181:
                BytecodeToIRInvokeSupport.assignInstanceField(classFile, method, instruction, instructions, stack);
                break;
            case 182:
                BytecodeToIRInvokeSupport.lowerVirtualCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations,
                    pendingExceptionHandlerStacks,
                    dispatches,
                    sourceLines
                );
                break;
            case 183:
                BytecodeToIRInvokeSupport.lowerInstanceCall(classes, classFile, method, instruction, instructions, stack);
                break;
            case 184:
                BytecodeToIRInvokeSupport.lowerStaticCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations,
                    pendingExceptionHandlerStacks,
                    sourceLines
                );
                break;
            case 185:
                BytecodeToIRInvokeSupport.lowerInterfaceCall(classes, classFile, method, instruction, instructions, stack, localDeclarations, dispatches);
                break;
            case 186:
                BytecodeToIRInvokeSupport.lowerDynamicCall(classFile, method, instruction, stack);
                break;
            case 187:
                BytecodeToIRInvokeSupport.newObject(classes, classFile, method, instruction, instructions, stack, localDeclarations);
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
                BytecodeToIRControlFlowSupport.lowerThrow(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines);
                break;
            case 192:
                // checkcast is a verifier/runtime type check; exact supported code keeps the reference unchanged.
                break;
            case 193:
                BytecodeToIRInvokeSupport.lowerInstanceOf(classes, classFile, method, instruction, stack);
                break;
            default:
                if (instruction.opcode() != 0) {
                    throw unsupported(classFile, method, instruction);
                }
                break;
        }
    }





























































































    static void binaryInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popInt(classFile, method, stack);
        final IrExpression left = popInt(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intBinary(operator, left, right)));
    }

    static void binaryLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popLong(classFile, method, stack);
        final IrExpression left = popLong(classFile, method, stack);
        stack.add(StackValue.longExpression(IrExpression.longBinary(operator, left, right)));
    }

    static void shiftInt(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String symbol
    ) {
        final IrExpression shift = popInt(classFile, method, stack);
        final IrExpression value = popInt(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value, shift))));
    }

    static void shiftLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String symbol
    ) {
        final IrExpression shift = popInt(classFile, method, stack);
        final IrExpression value = popLong(classFile, method, stack);
        stack.add(StackValue.longExpression(IrExpression.longCall(symbol, List.of(value, shift))));
    }

    static void binaryFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popFloat(classFile, method, stack);
        final IrExpression left = popFloat(classFile, method, stack);
        stack.add(StackValue.floatExpression(IrExpression.floatBinary(operator, left, right)));
    }

    static void binaryDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final String operator
    ) {
        final IrExpression right = popDouble(classFile, method, stack);
        final IrExpression left = popDouble(classFile, method, stack);
        stack.add(StackValue.doubleExpression(IrExpression.doubleBinary(operator, left, right)));
    }

    static void unaryFloatNeg(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.floatExpression(IrExpression.floatBinary(
            "-",
            IrExpression.floatLiteral(0.0f),
            popFloat(classFile, method, stack)
        )));
    }

    static void unaryDoubleNeg(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.doubleExpression(IrExpression.doubleBinary(
            "-",
            IrExpression.doubleLiteral(0.0),
            popDouble(classFile, method, stack)
        )));
    }

    static void intToChar(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intBinary(
            "&",
            popInt(classFile, method, stack),
            IrExpression.intLiteral(65_535)
        )));
    }

    static void intToByte(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_i2b", List.of(popInt(classFile, method, stack)))));
    }

    static void intToShort(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_i2s", List.of(popInt(classFile, method, stack)))));
    }

    static void compareFloat(
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

    static void compareLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression right = popLong(classFile, method, stack);
        final IrExpression left = popLong(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_lcmp", List.of(left, right))));
    }

    static void compareDouble(
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

    static void storeInt(
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

    static void storeLong(
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

    static void storeFloat(
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

    static void storeDouble(
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

    static void storeObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        if (stack.isEmpty()) {
            if (isSyntheticSwitchMapInitializer(classFile, method) && isEnumSwitchMapHandlerInstruction(instruction.opcode())) {
                return;
            }
            throw invalidStack(classFile, method, instruction, "object store requires a value on the bytecode stack");
        }
        final StackValue value = popObjectValue(classFile, method, instruction, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(target.value(), value.expression().orElseThrow()));
        updateObjectLocalKind(objectLocalKinds, slot, value.kind());
    }

    static void newObjectArray(
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

    static void newPrimitiveArray(
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

    static IrExpression primitiveArrayAllocation(
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

    static void loadObjectArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.objectExpression(IrExpression.objectArrayLoad(array, index)));
    }

    static void loadIntArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.intArrayLoad(array, index)));
    }

    static void loadLongArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.longExpression(IrExpression.longArrayLoad(array, index)));
    }

    static void loadFloatArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.floatExpression(IrExpression.floatArrayLoad(array, index)));
    }

    static void loadDoubleArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.doubleExpression(IrExpression.doubleArrayLoad(array, index)));
    }

    static void loadByteArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.byteArrayLoad(array, index)));
    }

    static void loadShortArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.shortArrayLoad(array, index)));
    }

    static void loadCharArray(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        final IrExpression index = popInt(classFile, method, stack);
        final IrExpression array = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(IrExpression.charArrayLoad(array, index)));
    }

    static void storeObjectArray(
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

    static void storeIntArray(
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

    static void storeLongArray(
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

    static void storeFloatArray(
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

    static void storeDoubleArray(
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

    static void storeByteArray(
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

    static void storeShortArray(
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

    static void storeCharArray(
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

    static void arrayLength(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack
    ) {
        stack.add(StackValue.intExpression(IrExpression.arrayLength(popObject(classFile, method, stack))));
    }











    static void incrementInt(
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
































    static IrExpression popInt(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        return popInt(classFile, method, firstInstruction(method), stack);
    }

    static IrExpression popInt(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final StackValue value = popTyped(classFile, method, instruction, stack, StackKind.INT, "int");
        return value.expression().orElseThrow();
    }

    static IrExpression popObject(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        return popObject(classFile, method, firstInstruction(method), stack);
    }

    static IrExpression popObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        return popObjectValue(classFile, method, instruction, stack).expression().orElseThrow();
    }

    static StackValue popObjectValue(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (BytecodeToIRControlFlowSupport.isObjectLike(value.kind())) {
            return switch (value.kind()) {
                case PRINT_STREAM -> StackValue.objectExpression(IrExpression.objectCall("javan_system_out", List.of()));
                case ERROR_PRINT_STREAM -> StackValue.objectExpression(IrExpression.objectCall("javan_system_err", List.of()));
                default -> value;
            };
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
    }

    static IrExpression popLong(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        return popLong(classFile, method, firstInstruction(method), stack);
    }

    static IrExpression popLong(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final StackValue value = popTyped(classFile, method, instruction, stack, StackKind.LONG, "long");
        return value.expression().orElseThrow();
    }

    static IrExpression popFloat(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        return popFloat(classFile, method, firstInstruction(method), stack);
    }

    static IrExpression popFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final StackValue value = popTyped(classFile, method, instruction, stack, StackKind.FLOAT, "float");
        return value.expression().orElseThrow();
    }

    static IrExpression popDouble(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        return popDouble(classFile, method, firstInstruction(method), stack);
    }

    static IrExpression popDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final StackValue value = popTyped(classFile, method, instruction, stack, StackKind.DOUBLE, "double");
        return value.expression().orElseThrow();
    }

    static StackValue popTyped(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack,
        final StackKind expected,
        final String expectedName
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "Expected " + expectedName + " value on the bytecode stack, but stack was empty.");
        }
        final StackValue value = pop(stack);
        if (value.kind() == expected) {
            return value;
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason(expectedName, value.kind()));
    }

    static String wrongStackTypeReason(final String expectedName, final StackKind actual) {
        return "Expected " + expectedName + " value on the bytecode stack, but found " + stackKindName(actual) + ".";
    }

    static String stackKindName(final StackKind kind) {
        return Strings2.toAsciiLowerCase(kind.name()).replace('_', ' ');
    }

    static Instruction firstInstruction(final MethodInfo method) {
        return method.code().orElseThrow().instructions().getFirst();
    }

    static void discardTop(final List<IrInstruction> instructions, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        if (value.expression().isPresent()) {
            final IrExpression expression = value.expression().orElseThrow();
            if (expression.kind() == IrExpression.Kind.CALL) {
                instructions.add(IrInstruction.callStaticVoid(expression.value(), expression.arguments()));
            }
        }
    }

    static IrExpression local(
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

    static StackValue localObjectValue(
        final ClassFile classFile,
        final MethodInfo method,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final int slot
    ) {
        final IrExpression expression = local(classFile, method, locals, slot, IrType.OBJECT);
        final StackKind kind = objectLocalKinds.getOrDefault(slot, StackKind.OBJECT);
        return BytecodeToIRControlFlowSupport.stackValue(kind, expression);
    }

    static IrExpression localOrCreate(
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

    static void updateObjectLocalKind(
        final Map<Integer, StackKind> objectLocalKinds,
        final int slot,
        final StackKind kind
    ) {
        if (kind == StackKind.SOCKET_INPUT_STREAM
            || kind == StackKind.SOCKET_OUTPUT_STREAM
            || kind == StackKind.VIRTUAL_THREAD_BUILDER
            || kind == StackKind.VIRTUAL_THREAD_FACTORY
            || kind == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            objectLocalKinds.put(slot, kind);
            return;
        }
        objectLocalKinds.put(slot, StackKind.OBJECT);
    }

    static String localName(final int slot, final IrType type, final int ordinal) {
        if (ordinal == 0) {
            return "local" + slot;
        }
        return "local" + slot + "_" + Strings2.toAsciiLowerCase(type.name()) + "_" + ordinal;
    }

    static IrExpression localExpression(final IrType type, final IrLocal local) {
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

    static StackValue pop(final List<StackValue> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Invalid bytecode stack");
        }
        return stack.removeLast();
    }

    static List<Integer> branchTargets(final CodeAttribute code) {
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

    static boolean isBranchTargetOpcode(final int opcode) {
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

    static List<Integer> exceptionHandlerOffsets(final CodeAttribute code) {
        final List<Integer> result = new ArrayList<>();
        for (final javan.classfile.CodeException handler : code.exceptionTable()) {
            addInt(result, handler.handlerPc());
        }
        return List.copyOf(result);
    }

    static List<Integer> ignoredEnumSwitchMapHandlerOffsets(
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

    static void addInt(final List<Integer> values, final int value) {
        if (!containsInt(values, value)) {
            values.add(value);
        }
    }

    static boolean containsInt(final List<Integer> values, final int target) {
        for (final int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    static boolean shouldSkipOffset(final List<Integer> ignoredHandlerOffsets, final List<Integer> skippedOffsets, final int offset) {
        if (containsInt(ignoredHandlerOffsets, offset)) {
            return true;
        }
        return containsInt(skippedOffsets, offset);
    }

    static boolean supportedEnumSwitchMapHandler(
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

    static boolean isSwitchMapInitializer(final ClassFile classFile, final MethodInfo method) {
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

    static boolean isSyntheticSwitchMapInitializer(final ClassFile classFile, final MethodInfo method) {
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

    static boolean endsWithDollarOne(final String value) {
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

    static boolean isSwitchMapField(final FieldInfo field) {
        if (!"[I".equals(field.descriptor())) {
            return false;
        }
        return startsWithSwitchMapPrefix(field.name());
    }

    static boolean startsWithSwitchMapPrefix(final String value) {
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

    static Optional<Instruction> instructionAtOffset(final CodeAttribute code, final int offset) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() == offset) {
                return Optional.of(instruction);
            }
        }
        return Optional.empty();
    }

    static boolean isAstore(final int opcode) {
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

    static boolean isEnumSwitchMapHandlerInstruction(final int opcode) {
        if (opcode == 87) {
            return true;
        }
        return isAstore(opcode);
    }

    static void addTableSwitchTargets(final List<Integer> result, final Instruction instruction) {
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

    static void addLookupSwitchTargets(final List<Integer> result, final Instruction instruction) {
        final int padding = switchPadding(instruction.offset());
        addInt(result, instruction.offset() + int32(instruction.operands(), padding));
        final int pairs = int32(instruction.operands(), padding + 4);
        int operandOffset = padding + 8;
        for (int index = 0; index < pairs; index++) {
            addInt(result, instruction.offset() + int32(instruction.operands(), operandOffset + 4));
            operandOffset += 8;
        }
    }

    static int branchTarget(final Instruction instruction) {
        return instruction.offset() + signedShort(instruction.operands());
    }

    static String label(final int offset) {
        return "label_" + offset;
    }

    static String zeroOperator(final int opcode) {
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

    static String intCompareOperator(final int opcode) {
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

    static String objectCompareOperator(final int opcode) {
        if (opcode == 165) {
            return "==";
        }
        if (opcode == 166) {
            return "!=";
        }
        throw new IllegalArgumentException("Unsupported object compare opcode " + opcode);
    }

    static String nullOperator(final int opcode) {
        if (opcode == 198) {
            return "==";
        }
        if (opcode == 199) {
            return "!=";
        }
        throw new IllegalArgumentException("Unsupported null branch opcode " + opcode);
    }

    static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor - offset - 1;
    }

    static int int32(final byte[] operands, final int offset) {
        return (unsigned(operands[offset]) << 24)
            | (unsigned(operands[offset + 1]) << 16)
            | (unsigned(operands[offset + 2]) << 8)
            | unsigned(operands[offset + 3]);
    }

    static int signedByte(final byte value) {
        return value;
    }

    static int signedShort(final byte[] operands) {
        return (short) ((unsigned(operands[0]) << 8) | unsigned(operands[1]));
    }

    static int unsigned(final byte value) {
        return value & 0xFF;
    }

    static DiagnosticException unsupported(final ClassFile classFile, final MethodInfo method, final Instruction instruction) {
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

    static DiagnosticException unsupportedStringConstant(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN046",
            "non-ASCII string constants require the UTF-16 string model",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "The current native runtime stores strings as UTF-8 C strings for the supported ASCII subset. Accepting this constant would make Java String length, indexing, substring, and ABI ownership semantics unsafe.",
            "Use ASCII string constants for now, or keep this code on the JVM until Javan's full UTF-16 String object model is implemented."
        ));
    }

    static DiagnosticException invalidStack(
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

    static String instructionSubject(final Instruction instruction) {
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

    static boolean isConcreteExactCallTarget(final Map<String, ClassFile> classes, final String owner) {
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

    static boolean isEnumConstant(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
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

    static boolean matchingEnumConstant(final FieldInfo field, final FieldRef fieldRef) {
        if (!field.isEnumConstant()) {
            return false;
        }
        if (!field.name().equals(fieldRef.name())) {
            return false;
        }
        return field.descriptor().equals(fieldRef.descriptor());
    }

    static boolean isSupportedJdkEnumConstant(final FieldRef fieldRef) {
        if (isStandardCopyReplaceExisting(fieldRef)) {
            return true;
        }
        return isLinkOptionNoFollowLinks(fieldRef);
    }

    static boolean isStandardCopyReplaceExisting(final FieldRef fieldRef) {
        if (!"java/nio/file/StandardCopyOption".equals(fieldRef.owner())) {
            return false;
        }
        if (!"REPLACE_EXISTING".equals(fieldRef.name())) {
            return false;
        }
        return "Ljava/nio/file/StandardCopyOption;".equals(fieldRef.descriptor());
    }

    static boolean isLinkOptionNoFollowLinks(final FieldRef fieldRef) {
        if (!"java/nio/file/LinkOption".equals(fieldRef.owner())) {
            return false;
        }
        if (!"NOFOLLOW_LINKS".equals(fieldRef.name())) {
            return false;
        }
        return "Ljava/nio/file/LinkOption;".equals(fieldRef.descriptor());
    }

    static boolean isEnumIntrinsic(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final ClassFile owner = classes.get(methodRef.owner());
        if (!isEnumOwner(owner, methodRef.owner())) {
            return false;
        }
        if (!isEnumStringMethod(methodRef.name())) {
            return false;
        }
        return "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    static boolean isEnumOrdinal(final Map<String, ClassFile> classes, final MethodRef methodRef) {
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

    static boolean isEnumOwner(final ClassFile owner, final String methodOwner) {
        if ("java/lang/Enum".equals(methodOwner)) {
            return true;
        }
        if (owner == null) {
            return false;
        }
        return owner.isEnum();
    }

    static boolean isEnumStringMethod(final String methodName) {
        if ("name".equals(methodName)) {
            return true;
        }
        return "toString".equals(methodName);
    }

    static Optional<Integer> enumOrdinal(final ClassFile enumClass, final String constant) {
        if (enumClass == null || !enumClass.isEnum()) {
            return Optional.empty();
        }
        final List<String> constants = BytecodeToIRMetadataSupport.enumConstants(enumClass);
        for (int index = 0; index < constants.size(); index++) {
            if (constants.get(index).equals(constant)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    static DiagnosticException unsupportedEnumConstant(
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

    static String enumOrdinalSymbol(final String owner) {
        return "javan_enum_ordinal_" + sanitize(owner);
    }

    static Optional<IrType> staticFieldType(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
        final ClassFile owner = classes.get(fieldRef.owner());
        if (owner == null) {
            return Optional.empty();
        }
        for (final FieldInfo field : owner.fields()) {
            if (field.isStatic()
                && field.name().equals(fieldRef.name())
                && field.descriptor().equals(fieldRef.descriptor())) {
                return BytecodeToIRMetadataSupport.fieldType(field.descriptor());
            }
        }
        return Optional.empty();
    }

    static List<EntryPoint> interfaceTargets(final Map<String, ClassFile> classes, final MethodRef methodRef) {
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

    static List<EntryPoint> virtualTargets(final Map<String, ClassFile> classes, final MethodRef methodRef) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (!candidate.isInterface() && isSubtypeOf(classes, candidate.name(), methodRef.owner())) {
                final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(classes, candidate.name(), methodRef);
                if (resolved.isPresent()) {
                    final EntryPoint entryPoint = resolved.orElseThrow();
                    if (!result.contains(entryPoint)) {
                        result.add(entryPoint);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    static Optional<EntryPoint> resolvedVirtualTarget(
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

    private static Optional<EntryPoint> lowerableResolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef methodRef
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, methodRef);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        final EntryPoint entryPoint = resolved.orElseThrow();
        final ClassFile owner = classes.get(entryPoint.className());
        if (owner == null) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(entryPoint.methodName(), entryPoint.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return resolved;
    }

    static List<Integer> assignableTypeIds(final Map<String, ClassFile> classes, final String target) {
        final List<Integer> result = new ArrayList<>();
        final List<ClassFile> sorted = BytecodeToIRMetadataSupport.sortedClasses(classes);
        for (int index = 0; index < sorted.size(); index++) {
            final ClassFile candidate = sorted.get(index);
            if (!candidate.isInterface() && isAssignableTo(classes, candidate.name(), target)) {
                result.add(index + 1);
            }
        }
        return List.copyOf(result);
    }

    static boolean isAssignableTo(final Map<String, ClassFile> classes, final String candidate, final String expected) {
        if ("java/lang/Object".equals(expected)) {
            return classes.containsKey(candidate);
        }
        String current = candidate;
        final List<String> visitedClasses = new ArrayList<>();
        while (current != null && !current.isEmpty()) {
            if (current.equals(expected)) {
                return true;
            }
            if (visitedClasses.contains(current)) {
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

    static boolean hasInterface(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final String expected,
        final List<String> visited
    ) {
        for (final String interfaceName : classFile.interfaces()) {
            if (interfaceName.equals(expected)) {
                return true;
            }
            if (visited.contains(interfaceName)) {
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

    static Optional<Integer> platformWrapperTypeId(final String target) {
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
        if ("java/lang/Boolean".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_BOOLEAN);
        }
        return Optional.empty();
    }

    static boolean isSubtypeOf(final Map<String, ClassFile> classes, final String candidate, final String expectedSuper) {
        String current = candidate;
        while (classes.containsKey(current)) {
            if (current.equals(expectedSuper)) {
                return true;
            }
            current = classes.get(current).superName();
        }
        return current.equals(expectedSuper);
    }

    static DiagnosticException unsupportedInstanceOfTarget(
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

    static boolean isNoopPlatformConstructor(final MethodRef methodRef) {
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

    static boolean isPlatformThrowableStringConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if (!"(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isPlatformThrowableDefaultConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if (!"()V".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isPlatformThrowableGetMessage(final MethodRef methodRef) {
        if (!"getMessage".equals(methodRef.name())) {
            return false;
        }
        if (!"()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isKnownPlatformThrowable(final String owner) {
        return JdkCallSupport.isPlatformThrowable(owner);
    }

    static void updatePendingThrowableMessage(final List<StackValue> stack, final IrExpression message) {
        if (!stack.isEmpty()) {
            final StackValue current = stack.getLast();
            if (current.throwableType().isPresent()) {
                stack.set(stack.size() - 1, StackValue.platformThrowable(current.throwableType().orElseThrow(), message));
                return;
            }
            stack.set(stack.size() - 1, StackValue.objectExpression(message));
        }
    }

    static DiagnosticException unsupportedTypedExceptionHandler(
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

    static String classSymbol(final String className) {
        return "javan_class_" + sanitize(className);
    }

    static String fieldSymbol(final String fieldName) {
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

    static String dispatchSymbol(final MethodRef methodRef) {
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

    static String sanitize(final String value) {
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

    enum StackKind {
        VIRTUAL_THREAD_BUILDER,
        VIRTUAL_THREAD_FACTORY,
        VIRTUAL_THREAD_EXECUTOR,
        PRINT_STREAM,
        ERROR_PRINT_STREAM,
        SOCKET_INPUT_STREAM,
        SOCKET_OUTPUT_STREAM,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        OBJECT
    }

    record BlockResult(List<IrInstruction> instructions, List<StackValue> stack) {
    }

    record StackValue(StackKind kind, Optional<String> throwableType, Optional<IrExpression> expression) {
        static StackValue virtualThreadBuilder() {
            return new StackValue(StackKind.VIRTUAL_THREAD_BUILDER, Optional.empty(), Optional.of(IrExpression.objectNull()));
        }

        static StackValue virtualThreadBuilder(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_BUILDER, Optional.empty(), Optional.of(expression));
        }

        static StackValue virtualThreadFactory(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_FACTORY, Optional.empty(), Optional.of(expression));
        }

        static StackValue virtualThreadExecutor(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_EXECUTOR, Optional.empty(), Optional.of(expression));
        }

        static StackValue printStream() {
            return new StackValue(StackKind.PRINT_STREAM, Optional.empty(), Optional.empty());
        }

        static StackValue errorPrintStream() {
            return new StackValue(StackKind.ERROR_PRINT_STREAM, Optional.empty(), Optional.empty());
        }

        static StackValue socketInputStream(final IrExpression expression) {
            return new StackValue(StackKind.SOCKET_INPUT_STREAM, Optional.empty(), Optional.of(expression));
        }

        static StackValue socketOutputStream(final IrExpression expression) {
            return new StackValue(StackKind.SOCKET_OUTPUT_STREAM, Optional.empty(), Optional.of(expression));
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
