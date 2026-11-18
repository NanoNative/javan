package javan.codegen;

import javan.analysis.FunctionValueFlow;
import javan.analysis.CaughtThrowableRethrowAnalysis;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.ir.IrDispatch;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrSourceLocation;
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

import static javan.codegen.BytecodeToIR.*;

final class BytecodeToIRControlFlowSupport {
    static void lowerThrow(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        final StackValue thrownValue = popThrowable(classFile, method, instruction, stack);
        final IrExpression thrown = thrownValue.expression().orElseThrow();
        if (thrownValue.kind() == StackKind.OBJECT
            && thrownValue.throwableType().isEmpty()
            && thrown.kind() == IrExpression.Kind.OBJECT_NULL) {
            BytecodeToIRInvokeSupport.routePendingPlatformException(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                sourceLines,
                "java/lang/NullPointerException",
                IrExpression.stringLiteral("Cannot throw null")
            );
        } else if (thrownValue.kind() == StackKind.CAUGHT_THROWABLE) {
            instructions.add(IrInstruction.callStaticVoid("javan_pending_rethrow", List.of(thrown)));
            if (thrownValue.throwableType().isPresent()) {
                appendPendingExceptionDispatch(
                    classes,
                    method,
                    instruction,
                    instructions,
                    thrownValue.possibleThrowableTypes(),
                    pendingExceptionHandlerStacks
                );
            } else {
                appendPendingExceptionDispatch(
                    method,
                    instruction,
                    instructions,
                    List.of("java/lang/Throwable"),
                    pendingExceptionHandlerStacks
                );
            }
        } else if (thrownValue.throwableType().isPresent()) {
            final String throwableType = thrownValue.throwableType().orElseThrow();
            final boolean generatedObject = thrownValue.generatedThrowableObject()
                || classes.containsKey(throwableType);
            instructions.add(generatedObject
                ? IrInstruction.setPendingObject(
                    throwableType,
                    thrown,
                    sourceLocation(classFile, method, instruction, sourceLines)
                )
                : IrInstruction.setPending(
                    throwableType,
                    thrown,
                    sourceLocation(classFile, method, instruction, sourceLines)
                ));
            appendPendingExceptionDispatch(
                classes,
                method,
                instruction,
                instructions,
                throwableDispatchTypes(thrownValue, generatedObject),
                pendingExceptionHandlerStacks
            );
        } else {
            if (hasExceptionHandler(method, instruction.offset())
                || insideExceptionHandlerBody(method, instruction.offset())) {
                throw unsupportedTypedExceptionHandler(classFile, method, instruction);
            }
            instructions.add(IrInstruction.panic(thrown, sourceLocation(classFile, method, instruction, sourceLines)));
        }
        clearStack(stack);
    }

    private static List<String> throwableDispatchTypes(
        final StackValue thrownValue,
        final boolean generatedObject
    ) {
        final List<String> result = new ArrayList<>(thrownValue.possibleThrowableTypes());
        if (generatedObject && !result.contains("java/lang/NullPointerException")) {
            result.add("java/lang/NullPointerException");
        }
        return List.copyOf(result);
    }

    private static boolean insideExceptionHandlerBody(
        final MethodInfo method,
        final int offset
    ) {
        final CodeAttribute code = method.code().orElseThrow();
        for (final javan.classfile.CodeException handler : code.exceptionTable()) {
            int bodyEnd = code.bytecode().length;
            for (final Instruction instruction : code.instructions()) {
                if (instruction.offset() != handler.endPc()) {
                    continue;
                }
                if (instruction.opcode() == 167 || instruction.opcode() == 200) {
                    final int normalJoin = branchTarget(instruction);
                    if (normalJoin > handler.handlerPc()) {
                        bodyEnd = normalJoin;
                    }
                }
                break;
            }
            if (offset >= handler.handlerPc() && offset < bodyEnd) {
                return true;
            }
        }
        return false;
    }

    static void appendPendingExceptionDispatch(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<String> possibleThrowableTypes,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks
    ) {
        appendPendingExceptionDispatch(classes, method, instruction, instructions, possibleThrowableTypes,
            pendingExceptionHandlerStacks, "label_pending_continue_" + instruction.offset());
    }

    static void appendPendingExceptionDispatch(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<String> possibleThrowableTypes,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final String continueLabel
    ) {
        if (possibleThrowableTypes.isEmpty()) {
            return;
        }
        instructions.add(IrInstruction.branchIf(
            continueLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intCall("javan_pending_has", List.of()),
                IrExpression.intLiteral(0)
            )
        ));
        for (final javan.classfile.CodeException handler : method.code().orElseThrow().exceptionTable()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (handler.catchType().isEmpty()) {
                if (!supportedFinallyHandler(classes, method, method.code().orElseThrow(), handler)) {
                    continue;
                }
                registerPendingHandlerStack(
                    pendingExceptionHandlerStacks,
                    handler.handlerPc()
                );
                instructions.add(IrInstruction.jump(label(handler.handlerPc())));
                break;
            }
            final String catchType = handler.catchType().orElseThrow();
            if (!BytecodeToIR.isThrowable(classes, catchType)) {
                continue;
            }
            String applicationType = null;
            boolean hasPlatformType = false;
            for (final String possibleType : possibleThrowableTypes) {
                if (!BytecodeToIR.isAssignableThrowable(classes, possibleType, catchType)) {
                    continue;
                }
                if (classes.containsKey(possibleType)) {
                    if (applicationType == null) {
                        applicationType = possibleType;
                    }
                } else {
                    hasPlatformType = true;
                }
            }
            registerPendingHandlerStack(
                pendingExceptionHandlerStacks,
                handler.handlerPc(),
                hasPlatformType ? Optional.empty() : Optional.ofNullable(applicationType)
            );
            instructions.add(IrInstruction.branchIf(
                label(handler.handlerPc()),
                IrExpression.intCall(
                    "javan_pending_type_assignable_to",
                    List.of(IrExpression.stringLiteral(catchType))
                )
            ));
        }
        instructions.add(IrInstruction.propagatePending());
        instructions.add(IrInstruction.label(continueLabel));
    }

    static void appendPendingExceptionDispatch(
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<String> possibleThrowableTypes,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks
    ) {
        appendPendingExceptionDispatch(Map.of(), method, instruction, instructions,
            possibleThrowableTypes, pendingExceptionHandlerStacks);
    }

    static void registerPendingHandlerStack(
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final int handlerOffset
    ) {
        registerPendingHandlerStack(pendingExceptionHandlerStacks, handlerOffset, Optional.empty());
    }

    private static void registerPendingHandlerStack(
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final int handlerOffset,
        final Optional<String> applicationThrowableType
    ) {
        pendingExceptionHandlerStacks.putIfAbsent(
            handlerOffset,
            applicationThrowableType.isPresent()
                ? StackValue.caughtThrowable(
                    IrExpression.objectCall("javan_pending_catch", List.of()),
                    applicationThrowableType.orElseThrow()
                )
                : StackValue.caughtThrowable(IrExpression.objectCall("javan_pending_catch", List.of()))
        );
    }
    static Optional<IrSourceLocation> generatedStatementSourceLocation(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final SourceLineIndex sourceLines
    ) {
        final IrSourceLocation location = sourceLocation(classFile, method, instruction, sourceLines);
        if (location.lineNumber().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(location);
    }
    static void annotateNewInstructions(
        final List<IrInstruction> instructions,
        final int start,
        final Optional<IrSourceLocation> sourceLocation
    ) {
        if (sourceLocation.isEmpty()) {
            return;
        }
        final IrSourceLocation location = sourceLocation.orElseThrow();
        for (int index = start; index < instructions.size(); index++) {
            final IrInstruction instruction = instructions.get(index);
            if (instruction.op() != IrInstruction.Op.LABEL) {
                instructions.set(index, instruction.withSourceLocation(location));
            }
        }
    }
    static IrSourceLocation sourceLocation(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final SourceLineIndex sourceLines
    ) {
        final Optional<CodeAttribute> code = method.code();
        Optional<Integer> lineNumber = Optional.empty();
        if (code.isPresent()) {
            lineNumber = code.orElseThrow().lineForOffset(instruction.offset());
        }
        final Optional<String> sourceFile = sourceFile(classFile);
        return new IrSourceLocation(
            classFile.name(),
            method.name(),
            method.descriptor(),
            instruction.offset(),
            sourceFile,
            lineNumber,
            sourceLines.line(classFile.name(), sourceFile, lineNumber)
        );
    }
    static Optional<String> sourceFile(final ClassFile classFile) {
        if (classFile.sourceFile().isPresent()) {
            return classFile.sourceFile();
        }
        final int slash = classFile.name().lastIndexOf('/');
        final String simpleName = slash < 0 ? classFile.name() : classFile.name().substring(slash + 1);
        return Optional.of(new StringBuilder(simpleName).append(".java").toString());
    }
    static void clearStack(final List<StackValue> stack) {
        while (!stack.isEmpty()) {
            stack.removeLast();
        }
    }
    static StackValue popThrowable(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.OBJECT || value.kind() == StackKind.CAUGHT_THROWABLE) {
            return value;
        }
        throw unsupported(classFile, method, instruction);
    }
    static Optional<Integer> exceptionHandler(
        final Map<String, ClassFile> classes,
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
                if (supportedFinallyHandler(classes, method, method.code().orElseThrow(), handler)) {
                    return Optional.of(handler.handlerPc());
                }
                continue;
            }
            if (BytecodeToIR.isAssignableThrowable(classes, thrownType, handler.catchType().orElseThrow())) {
                return Optional.of(handler.handlerPc());
            }
        }
        return Optional.empty();
    }

    static Optional<Integer> exceptionHandler(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final StackValue thrown,
        final int offset
    ) {
        return exceptionHandler(Map.of(), classFile, method, instruction, thrown, offset);
    }

    static boolean supportedFinallyRethrowHandler(
        final CodeAttribute code,
        final javan.classfile.CodeException handler
    ) {
        if (handler.catchType().isPresent()) {
            return false;
        }
        return handlerRethrowsCaughtThrowable(code, handler);
    }

    private static boolean supportedFinallyHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final javan.classfile.CodeException handler
    ) {
        final Optional<CaughtThrowableRethrowAnalysis.FinallyFlow> flow =
            CaughtThrowableRethrowAnalysis.analyzeFinally(code, handler);
        if (flow.isEmpty()) {
            return false;
        }
        final CaughtThrowableRethrowAnalysis.FinallyFlow result = flow.orElseThrow();
        for (final CaughtThrowableRethrowAnalysis.ReplacementThrow replacement : result.replacements()) {
            if (!isThrowable(classes, replacement.throwableType())) {
                return false;
            }
        }
        for (final int local : result.replacementLocals()) {
            if (!declaredThrowableParameter(classes, method, local)
                && !handlerThrowableLocal(classes, code, handler, local)) {
                return false;
            }
        }
        for (final int offset : result.replacementValueOffsets()) {
            if (!directThrowableValue(classes, code, offset)) {
                return false;
            }
        }
        return true;
    }

    private static boolean handlerThrowableLocal(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final javan.classfile.CodeException handler,
        final int local
    ) {
        final Optional<String> type = code.objectLocalTypeAt(handler.handlerPc(), local);
        final Optional<String> handlerStack = code.singleStackObjectTypeAt(handler.handlerPc());
        return type.isPresent()
            && handlerStack.isPresent()
            && isThrowable(classes, type.orElseThrow())
            && isThrowable(classes, handlerStack.orElseThrow());
    }

    private static boolean declaredThrowableParameter(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final int local
    ) {
        int slot = method.isStatic() ? 0 : 1;
        final List<String> parameters = BytecodeToIRDynamicSupport.parameterDescriptors(method.descriptor()).orElseThrow();
        final List<IrType> types = MethodDescriptor.parse(method.descriptor()).parameterTypes();
        for (int index = 0; index < parameters.size(); index++) {
            if (slot == local) {
                return throwableTypeFromDescriptor(classes, parameters.get(index)).isPresent();
            }
            slot += types.get(index).slotWidth();
        }
        return false;
    }

    private static boolean directThrowableValue(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final int throwOffset
    ) {
        final List<Instruction> instructions = code.instructions();
        for (int index = 1; index < instructions.size(); index++) {
            if (instructions.get(index).offset() != throwOffset) {
                continue;
            }
            final Instruction source = instructions.get(index - 1);
            if (source.fieldRef().isPresent()) {
                return throwableTypeFromDescriptor(classes, source.fieldRef().orElseThrow().descriptor()).isPresent();
            }
            if (source.methodRef().isPresent()) {
                final String descriptor = source.methodRef().orElseThrow().descriptor();
                return throwableTypeFromDescriptor(classes, descriptor.substring(descriptor.indexOf(')') + 1)).isPresent();
            }
            return false;
        }
        return false;
    }

    static boolean handlerMayThrow(
        final CodeAttribute code,
        final javan.classfile.CodeException handler
    ) {
        // Handlers may jump backward to shared throw code. Over-reporting only adds a pending check.
        for (final Instruction instruction : code.instructions()) {
            if (instruction.opcode() == 191) {
                return true;
            }
        }
        return false;
    }

    static boolean handlerRethrowsCaughtThrowable(
        final CodeAttribute code,
        final javan.classfile.CodeException handler
    ) {
        return CaughtThrowableRethrowAnalysis.rethrowOffset(code, handler).isPresent();
    }
    static boolean hasExceptionHandler(final MethodInfo method, final int offset) {
        for (final javan.classfile.CodeException handler : method.code().orElseThrow().exceptionTable()) {
            if (offset >= handler.startPc() && offset < handler.endPc()) {
                return true;
            }
        }
        return false;
    }
    static void branchZero(
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
    static void branchIntCompare(
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
    static void branchObjectCompare(
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
    static void branchObjectNull(
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
    static boolean lowerBranchValueSelection(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int index,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<String, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final List<Integer> skippedOffsets,
        final List<Integer> replacementLabelOffsets
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
            objectLocalKinds,
            objectLocalThrowableTypes,
            objectLocalLambdas,
            localDeclarations,
            dispatches,
            functionOrNullTargetIds,
            materializedLambdaMethods,
            functionValueFlow,
            pendingExceptionHandlerStacks,
            sourceLines,
            skippedOffsets,
            instruction
        )) {
            return true;
        }
        if (lowerNestedBranchValueSelection(
            classes, classFile, method, bytecode, index, instructions, stack, locals,
            objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, localDeclarations,
            dispatches, functionOrNullTargetIds, materializedLambdaMethods, functionValueFlow,
            pendingExceptionHandlerStacks, sourceLines, skippedOffsets, replacementLabelOffsets, -1, false
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
        final int doneOffset = branchTarget(jumpInstruction);
        final int doneIndex = instructionIndex(bytecode, doneOffset);
        if (doneIndex < 0 || doneIndex <= targetIndex) {
            return false;
        }
        final int elseOffset = bytecode.get(index + 1).offset();
        if (containsControlTransfer(bytecode, index + 1, jumpIndex) || containsControlTransfer(bytecode, targetIndex, doneIndex)) {
            return false;
        }

        final List<StackValue> conditionStack = new ArrayList<>(stack);
        final IrExpression condition = branchCondition(classFile, method, instruction, conditionStack);
        final List<StackValue> prefix = List.copyOf(conditionStack);
        final int originalLocalDeclarationCount = localDeclarations.size();
        final Map<Integer, IrLocal> workingDeclarations = copyLocalDeclarations(localDeclarations);
        final Map<Integer, StackValue> workingPendingExceptionHandlerStacks =
            new HashMap<>(pendingExceptionHandlerStacks);
        final BlockResult elseBlock = lowerLinearBlock(
            classes,
            classFile,
            method,
            bytecode,
            index + 1,
            jumpIndex,
            prefix,
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            objectLocalLambdas,
            workingDeclarations,
            dispatches,
            functionOrNullTargetIds,
            materializedLambdaMethods,
            functionValueFlow,
            workingPendingExceptionHandlerStacks,
            sourceLines
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
            objectLocalKinds,
            objectLocalThrowableTypes,
            objectLocalLambdas,
            workingDeclarations,
            dispatches,
            functionOrNullTargetIds,
            materializedLambdaMethods,
            functionValueFlow,
            workingPendingExceptionHandlerStacks,
            sourceLines
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
        pendingExceptionHandlerStacks.putAll(workingPendingExceptionHandlerStacks);
        branchCondition(classFile, method, instruction, stack);
        final StackKind valueKind = targetValue.kind();
        final IrType valueType = stackKindType(valueKind);
        final String localName = "branchValue" + localDeclarations.size() + "_" + instruction.offset();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(valueType, localName));
        final String targetLabel = "branch_value_target_" + instruction.offset();
        final String doneLabel = "branch_value_done_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(targetLabel, condition));
        if (hasEarlierBranchTarget(bytecode, index, elseOffset)) {
            instructions.add(IrInstruction.label(label(elseOffset)));
            addInt(replacementLabelOffsets, elseOffset);
        }
        instructions.addAll(elseBlock.instructions());
        instructions.add(assignLocal(valueKind, localName, elseValue.expression().orElseThrow()));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(targetLabel));
        instructions.addAll(targetBlock.instructions());
        instructions.add(assignLocal(valueKind, localName, targetValue.expression().orElseThrow()));
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(mergedValue(
            classes,
            elseValue,
            targetValue,
            localExpression(valueType, new IrLocal(valueType, localName))
        ));
        addInt(skippedOffsets, jumpInstruction.offset());
        addInstructionOffsets(bytecode, index + 1, jumpIndex, skippedOffsets);
        addInstructionOffsets(bytecode, targetIndex, doneIndex, skippedOffsets);
        return true;
    }
    static boolean lowerNestedBranchValueSelection(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int index,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<String, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final List<Integer> skippedOffsets,
        final List<Integer> replacementLabelOffsets,
        final int boundedEndIndex,
        final boolean allowSimple
    ) {
        final Instruction first = bytecode.get(index);
        final int targetOffset = branchTarget(first);
        final int targetIndex = instructionIndex(bytecode, targetOffset);
        if (targetIndex <= index + 1) {
            return false;
        }
        int doneOffset = -1;
        boolean conditionalBeforeTarget = false;
        for (int cursor = index + 1; cursor < targetIndex; cursor++) {
            final Instruction candidate = bytecode.get(cursor);
            if (isConditionalBranch(candidate.opcode())) {
                conditionalBeforeTarget = true;
            }
            if (candidate.opcode() == 167 && branchTarget(candidate) > targetOffset) {
                doneOffset = branchTarget(candidate);
                break;
            }
        }
        if (doneOffset < 0 && !conditionalBeforeTarget) {
            return false;
        }
        if (doneOffset < 0) {
            for (int cursor = targetIndex; cursor < bytecode.size(); cursor++) {
                final Instruction candidate = bytecode.get(cursor);
                if (candidate.opcode() == 167) {
                    if (branchTarget(candidate) > targetOffset) {
                        doneOffset = branchTarget(candidate);
                    }
                    break;
                }
                if (candidate.opcode() == 168 || candidate.opcode() == 169
                    || candidate.opcode() == 170 || candidate.opcode() == 171
                    || candidate.opcode() >= 172 && candidate.opcode() <= 177 || candidate.opcode() == 191
                    || candidate.opcode() == 200 || candidate.opcode() == 201) {
                    break;
                }
            }
        }
        final int doneIndex = instructionIndex(bytecode, doneOffset);
        final int selectionEndIndex = boundedEndIndex < 0 ? doneIndex : boundedEndIndex;
        if (doneIndex <= targetIndex || selectionEndIndex <= targetIndex || selectionEndIndex > doneIndex) {
            return false;
        }
        final int selectionEndOffset = bytecode.get(selectionEndIndex).offset();
        boolean nested = allowSimple;
        boolean unsupportedStore = false;
        int newValueSlot = -1;
        IrType newValueType = null;
        for (int cursor = index + 1; cursor < selectionEndIndex; cursor++) {
            final Instruction candidate = bytecode.get(cursor);
            final int opcode = candidate.opcode();
            if (isConditionalBranch(opcode)) {
                final int target = branchTarget(candidate);
                if (target <= candidate.offset() || target >= selectionEndOffset) {
                    return false;
                }
                if (target != targetOffset) {
                    nested = true;
                }
            } else if (opcode == 167) {
                if (branchTarget(candidate) != doneOffset) {
                    return false;
                }
            } else if (opcode >= 54 && opcode <= 78) {
                if (!existingValueStore(candidate, locals, objectLocalKinds,
                    objectLocalThrowableTypes, objectLocalLambdas)) {
                    final int slot = uninitializedValueStoreSlot(candidate, locals, objectLocalKinds,
                        objectLocalThrowableTypes, objectLocalLambdas);
                    final Optional<IrType> type = valueStoreType(candidate);
                    if (boundedEndIndex >= 0 || slot < 0 || type.isEmpty()
                        || newValueSlot >= 0 && (newValueSlot != slot || newValueType != type.orElseThrow())) {
                        unsupportedStore = true;
                    } else {
                        newValueSlot = slot;
                        newValueType = type.orElseThrow();
                    }
                }
            } else if (isControlTransfer(opcode) || opcode >= 168 && opcode <= 177 || opcode == 132
                || opcode == 191 || opcode == 194 || opcode == 195 || opcode == 196 || opcode == 200 || opcode == 201) {
                return false;
            }
        }
        if (!nested) {
            return false;
        }
        if (newValueSlot >= 0 && !unsupportedStore) {
            final Set<Integer> conditionalTargets = new HashSet<>();
            for (int cursor = index; cursor < selectionEndIndex; cursor++) {
                final Instruction branch = bytecode.get(cursor);
                if (isConditionalBranch(branch.opcode())) {
                    conditionalTargets.add(branchTarget(branch));
                }
            }
            for (int cursor = index + 1; cursor < selectionEndIndex; cursor++) {
                final Instruction exit = bytecode.get(cursor);
                if (exit.opcode() != 167) {
                    continue;
                }
                if (cursor == index + 1 || uninitializedValueStoreSlot(bytecode.get(cursor - 1), locals,
                    objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas) != newValueSlot) {
                    unsupportedStore = true;
                }
                if (conditionalTargets.contains(exit.offset())) {
                    unsupportedStore = true;
                }
            }
            final Instruction last = bytecode.get(selectionEndIndex - 1);
            if (last.opcode() != 167 && uninitializedValueStoreSlot(last, locals,
                objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas) != newValueSlot) {
                unsupportedStore = true;
            }
        }
        if (unsupportedStore) {
            boolean valueAtJoin = isValueConsumer(bytecode.get(doneIndex).opcode());
            final List<StackValue> conditionStack = new ArrayList<>(stack);
            branchCondition(classFile, method, first, conditionStack);
            if (method.code().isPresent()) {
                for (final javan.classfile.StackMapFrame frame : method.code().orElseThrow().stackMapFrames()) {
                    if (frame.offset() == doneOffset && frame.stackTypes().size() > conditionStack.size()) {
                        valueAtJoin = true;
                    }
                }
            }
            if (valueAtJoin) {
                throw unsupportedBranchValueMerge(classFile, method, first);
            }
            return false;
        }

        boolean externalEntry = false;
        for (int cursor = 0; cursor < bytecode.size(); cursor++) {
            if (cursor >= index && cursor < selectionEndIndex) {
                continue;
            }
            final Instruction source = bytecode.get(cursor);
            if (isSimpleBranch(source.opcode())) {
                final int target = branchTarget(source);
                if (target > first.offset() && (target < selectionEndOffset
                    || boundedEndIndex < 0 && target == selectionEndOffset)) {
                    externalEntry = true;
                }
            } else if (source.opcode() == 170 || source.opcode() == 171) {
                for (final SwitchEntry entry : switchEntries(source)) {
                    if (entry.targetOffset() > first.offset() && (entry.targetOffset() < selectionEndOffset
                        || boundedEndIndex < 0 && entry.targetOffset() == selectionEndOffset)) {
                        externalEntry = true;
                    }
                }
            }
        }
        if (method.code().isPresent()) {
            for (final javan.classfile.CodeException handler : method.code().orElseThrow().exceptionTable()) {
                if (handler.handlerPc() > first.offset() && handler.handlerPc() < selectionEndOffset) {
                    externalEntry = true;
                }
            }
        }

        if (externalEntry) {
            return false;
        }

        final int declarationCount = localDeclarations.size();
        final Map<Integer, IrLocal> workingDeclarations = copyLocalDeclarations(localDeclarations);
        final Map<Integer, IrExpression> workingLocals = copyExpressionLocals(locals);
        final Map<Integer, StackKind> workingKinds = copyObjectLocalKinds(objectLocalKinds);
        final Map<Integer, ThrowableValue> workingThrowables = copyObjectLocalThrowableTypes(objectLocalThrowableTypes);
        final Map<Integer, DynamicLambda> workingLambdas = copyObjectLocalLambdas(objectLocalLambdas);
        if (newValueSlot >= 0) {
            localOrCreate(workingLocals, workingDeclarations, newValueSlot, newValueType);
        }
        final Map<Integer, StackValue> workingHandlers = new HashMap<>(pendingExceptionHandlerStacks);
        final List<IrInstruction> workingInstructions = new ArrayList<>();
        final List<StackValue> workingStack = new ArrayList<>(stack);
        final IrExpression firstCondition = branchCondition(classFile, method, first, workingStack);
        final List<StackValue> prefix = List.copyOf(workingStack);
        final Map<Integer, List<StackValue>> branchEntries = new HashMap<>();
        branchEntries.put(targetOffset, prefix);
        final List<Integer> replacementOffsets = new ArrayList<>();
        final String doneLabel = "nested_branch_value_done_" + first.offset();
        workingInstructions.add(IrInstruction.branchIf(label(targetOffset), firstCondition));
        final int lastDuplicate = BytecodeToIR.lastMaterializingDuplicateOffset(bytecode);
        boolean active = true;
        StackValue selected = null;
        StackKind selectedKind = null;
        IrType selectedType = null;
        String selectedLocal = "";
        for (int cursor = index + 1; cursor < selectionEndIndex; cursor++) {
            final Instruction current = bytecode.get(cursor);
            if (branchEntries.containsKey(current.offset())) {
                if (active) {
                    if (workingStack.size() != prefix.size()) {
                        if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                            throw unsupportedBranchValueMerge(classFile, method, current);
                        }
                        return false;
                    }
                    for (int slot = 0; slot < prefix.size(); slot++) {
                        if (workingStack.get(slot) != prefix.get(slot)) {
                            throw unsupportedBranchValueMerge(classFile, method, current);
                        }
                    }
                }
                clearStack(workingStack);
                workingStack.addAll(branchEntries.get(current.offset()));
                workingInstructions.add(IrInstruction.label(label(current.offset())));
                addInt(replacementOffsets, current.offset());
                active = true;
            }
            if (!active) {
                if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                    throw unsupportedBranchValueMerge(classFile, method, current);
                }
                return false;
            }
            if (isConditionalBranch(current.opcode())) {
                final IrExpression condition = branchCondition(classFile, method, current, workingStack);
                final int target = branchTarget(current);
                if (target <= current.offset() || target >= selectionEndOffset || instructionIndex(bytecode, target) < 0
                    || workingStack.size() != prefix.size()) {
                    if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                        throw unsupportedBranchValueMerge(classFile, method, current);
                    }
                    return false;
                }
                for (int slot = 0; slot < prefix.size(); slot++) {
                    if (workingStack.get(slot) != prefix.get(slot)) {
                        if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                            throw unsupportedBranchValueMerge(classFile, method, current);
                        }
                        return false;
                    }
                }
                branchEntries.put(target, prefix);
                workingInstructions.add(IrInstruction.branchIf(label(target), condition));
                continue;
            }
            if (current.opcode() == 167) {
                if (branchTarget(current) != doneOffset || !hasSelectedValue(prefix, workingStack)) {
                    if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                        throw unsupportedBranchValueMerge(classFile, method, current);
                    }
                    return false;
                }
                final StackValue value = workingStack.getLast();
                if (value.expression().isEmpty()) {
                    throw unsupportedBranchValueMerge(classFile, method, current);
                }
                if (selected == null) {
                    selected = value;
                    selectedKind = value.kind();
                    selectedType = stackKindType(selectedKind);
                    selectedLocal = "nestedBranchValue" + workingDeclarations.size() + "_" + first.offset();
                    workingDeclarations.put(Integer.MIN_VALUE + workingDeclarations.size(),
                        new IrLocal(selectedType, selectedLocal));
                } else if (selectedKind != value.kind()) {
                    throw unsupportedBranchValueMerge(classFile, method, current);
                } else {
                    selected = mergedValue(classes, selected, value,
                        localExpression(selectedType, new IrLocal(selectedType, selectedLocal)));
                }
                workingInstructions.add(assignLocal(selectedKind, selectedLocal, value.expression().orElseThrow()));
                workingInstructions.add(IrInstruction.jump(doneLabel));
                active = false;
                continue;
            }
            if (isControlTransfer(current.opcode())
                || current.opcode() >= 168 && current.opcode() <= 177 || current.opcode() == 191
                || current.opcode() == 194 || current.opcode() == 195 || current.opcode() == 196
                || current.opcode() == 200 || current.opcode() == 201 || current.opcode() == 132) {
                if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                    throw unsupportedBranchValueMerge(classFile, method, current);
                }
                return false;
            }
            if (current.opcode() == 58 || current.opcode() >= 75 && current.opcode() <= 78) {
                if (workingStack.isEmpty()) {
                    throw unsupportedBranchValueMerge(classFile, method, current);
                }
                final StackValue value = workingStack.getLast();
                if (value.kind() != StackKind.OBJECT || value.throwable().isPresent()
                    || value.dynamicLambda().isPresent()) {
                    throw unsupportedBranchValueMerge(classFile, method, current);
                }
            }
            lowerInstruction(classes, classFile, method, current, workingInstructions, workingStack,
                workingHandlers, workingLocals, workingKinds, workingThrowables, workingLambdas,
                workingDeclarations, dispatches, functionOrNullTargetIds, materializedLambdaMethods,
                functionValueFlow, sourceLines, lastDuplicate);
        }
        if (active) {
            if (!hasSelectedValue(prefix, workingStack)) {
                if (selected != null || isValueConsumer(bytecode.get(doneIndex).opcode())) {
                    throw unsupportedBranchValueMerge(classFile, method, first);
                }
                return false;
            }
            final StackValue value = workingStack.getLast();
            if (value.expression().isEmpty() || selected == null || selectedKind != value.kind()) {
                throw unsupportedBranchValueMerge(classFile, method, first);
            }
            selected = mergedValue(classes, selected, value,
                localExpression(selectedType, new IrLocal(selectedType, selectedLocal)));
            workingInstructions.add(assignLocal(selectedKind, selectedLocal, value.expression().orElseThrow()));
        }
        if (selected == null) {
            return false;
        }
        workingInstructions.add(IrInstruction.label(doneLabel));
        appendNewLocalDeclarations(localDeclarations, workingDeclarations, declarationCount);
        pendingExceptionHandlerStacks.putAll(workingHandlers);
        if (newValueSlot >= 0) {
            locals.put(newValueSlot, workingLocals.get(newValueSlot));
            if (newValueType == IrType.OBJECT) {
                objectLocalKinds.put(newValueSlot, StackKind.OBJECT);
                objectLocalThrowableTypes.put(newValueSlot, null);
                objectLocalLambdas.remove(newValueSlot);
            }
        }
        instructions.addAll(workingInstructions);
        replacementLabelOffsets.addAll(replacementOffsets);
        clearStack(stack);
        stack.addAll(prefix);
        stack.add(mergedValue(classes, selected, selected,
            localExpression(selectedType, new IrLocal(selectedType, selectedLocal))));
        addInstructionOffsets(bytecode, index + 1, selectionEndIndex, skippedOffsets);
        return true;
    }

    static int valueStoreSlot(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 54 && opcode <= 58 && instruction.operands().length == 1) {
            return unsigned(instruction.operands()[0]);
        }
        if (opcode >= 59 && opcode <= 78) {
            return (opcode - 59) % 4;
        }
        return -1;
    }

    static Optional<IrType> valueStoreType(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 54 && opcode <= 58 && instruction.operands().length == 1) {
            return Optional.of(switch (opcode) {
                case 54 -> IrType.INT;
                case 55 -> IrType.LONG;
                case 56 -> IrType.FLOAT;
                case 57 -> IrType.DOUBLE;
                default -> IrType.OBJECT;
            });
        }
        if (opcode >= 59 && opcode <= 78) {
            return Optional.of(switch ((opcode - 59) / 4) {
                case 0 -> IrType.INT;
                case 1 -> IrType.LONG;
                case 2 -> IrType.FLOAT;
                case 3 -> IrType.DOUBLE;
                default -> IrType.OBJECT;
            });
        }
        return Optional.empty();
    }

    static int uninitializedValueStoreSlot(
        final Instruction instruction,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas
    ) {
        final int slot = valueStoreSlot(instruction);
        final Optional<IrType> type = valueStoreType(instruction);
        if (slot < 0 || type.isEmpty() || locals.get(slot) != null) {
            return -1;
        }
        if (type.orElseThrow() != IrType.OBJECT) {
            return slot;
        }
        final StackKind kind = objectLocalKinds.get(slot);
        if (kind != null && kind != StackKind.OBJECT || objectLocalThrowableTypes.get(slot) != null
            || objectLocalLambdas.get(slot) != null) {
            return -1;
        }
        return slot;
    }

    static boolean existingValueStore(
        final Instruction instruction,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas
    ) {
        final int slot = valueStoreSlot(instruction);
        final Optional<IrType> type = valueStoreType(instruction);
        if (slot < 0 || type.isEmpty()) {
            return false;
        }
        final IrExpression local = locals.get(slot);
        if (local == null || local.type() != type.orElseThrow()) {
            return false;
        }
        if (type.orElseThrow() != IrType.OBJECT) {
            return true;
        }
        final StackKind kind = objectLocalKinds.get(slot);
        return (kind == null || kind == StackKind.OBJECT)
            && objectLocalThrowableTypes.get(slot) == null && objectLocalLambdas.get(slot) == null;
    }

    static boolean lowerGuardedValueSelection(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int index,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<String, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
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
        if (hasEarlierControlFlowEntry(bytecode, index, bytecode.get(index + 1).offset(), doneOffset)) {
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
        final Map<Integer, StackKind> workingObjectLocalKinds = copyObjectLocalKinds(objectLocalKinds);
        final Map<Integer, ThrowableValue> workingObjectLocalThrowableTypes = copyObjectLocalThrowableTypes(objectLocalThrowableTypes);
        final Map<Integer, DynamicLambda> workingObjectLocalLambdas = copyObjectLocalLambdas(objectLocalLambdas);
        final Map<Integer, StackValue> workingPendingExceptionHandlerStacks =
            new HashMap<>(pendingExceptionHandlerStacks);
        final List<IrInstruction> mergedInstructions = new ArrayList<>();
        final List<StackValue> workingStack = new ArrayList<>(stack);
        final int lastMaterializingDuplicateOffset = BytecodeToIR.lastMaterializingDuplicateOffset(bytecode);
        List<StackValue> prefix = List.of();
        int conditionalCount = 0;
        final String targetLabel = "guarded_value_target_" + instruction.offset();
        final String doneLabel = "guarded_value_done_" + instruction.offset();
        for (int cursor = index; cursor < jumpIndex; cursor++) {
            final Instruction current = bytecode.get(cursor);
            if (isConditionalBranch(current.opcode())) {
                conditionalCount++;
                final IrExpression condition = branchCondition(classFile, method, current, workingStack);
                if (prefix.isEmpty()) {
                    prefix = List.copyOf(workingStack);
                }
                mergedInstructions.add(IrInstruction.branchIf(targetLabel, condition));
            } else {
                lowerInstruction(
                    classes,
                    classFile,
                    method,
                    current,
                    mergedInstructions,
                    workingStack,
                    workingPendingExceptionHandlerStacks,
                    workingLocals,
                    workingObjectLocalKinds,
                    workingObjectLocalThrowableTypes,
                    workingObjectLocalLambdas,
                    workingDeclarations,
                    dispatches,
                    functionOrNullTargetIds,
                    materializedLambdaMethods,
                    functionValueFlow,
                    sourceLines,
                    lastMaterializingDuplicateOffset
                );
            }
        }
        if (conditionalCount < 2) {
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
            objectLocalKinds,
            objectLocalThrowableTypes,
            objectLocalLambdas,
            workingDeclarations,
            dispatches,
            functionOrNullTargetIds,
            materializedLambdaMethods,
            functionValueFlow,
            workingPendingExceptionHandlerStacks,
            sourceLines
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
        pendingExceptionHandlerStacks.putAll(workingPendingExceptionHandlerStacks);
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
        stack.add(mergedValue(
            classes,
            elseValue,
            targetValue,
            localExpression(valueType, new IrLocal(valueType, localName))
        ));
        addInstructionOffsets(bytecode, index + 1, doneIndex, skippedOffsets);
        return true;
    }

    private static StackValue mergedValue(
        final Map<String, ClassFile> classes,
        final StackValue first,
        final StackValue second,
        final IrExpression expression
    ) {
        if (first.throwable().isEmpty() || second.throwable().isEmpty()) {
            return stackValue(first.kind(), expression);
        }
        final ThrowableValue firstThrowable = first.throwable().orElseThrow();
        final ThrowableValue secondThrowable = second.throwable().orElseThrow();
        if (firstThrowable.generatedObject() != secondThrowable.generatedObject()
            || firstThrowable.arrayDepth() != secondThrowable.arrayDepth()) {
            return stackValue(first.kind(), expression);
        }
        final String upperBound = commonThrowableType(
            classes,
            firstThrowable.upperBound(),
            secondThrowable.upperBound()
        );
        if (upperBound == null) {
            return stackValue(first.kind(), expression);
        }
        final List<String> possibleTypes = new ArrayList<>(firstThrowable.possibleTypes());
        for (final String type : secondThrowable.possibleTypes()) {
            if (!possibleTypes.contains(type)) {
                possibleTypes.add(type);
            }
        }
        if (firstThrowable.arrayDepth() > 0) {
            return new StackValue(
                first.kind(),
                Optional.of(new ThrowableValue(
                    upperBound,
                    possibleTypes,
                    firstThrowable.generatedObject(),
                    firstThrowable.arrayDepth()
                )),
                Optional.of(expression),
                Optional.empty()
            );
        }
        return StackValue.throwable(upperBound, possibleTypes, firstThrowable.generatedObject(), expression);
    }

    private static String commonThrowableType(
        final Map<String, ClassFile> classes,
        final String first,
        final String second
    ) {
        if (BytecodeToIR.isAssignableThrowable(classes, first, second)) {
            return second;
        }
        if (BytecodeToIR.isAssignableThrowable(classes, second, first)) {
            return first;
        }
        String candidate = first;
        final List<String> visited = new ArrayList<>();
        while (candidate != null && !candidate.isEmpty() && !visited.contains(candidate)) {
            visited.add(candidate);
            final ClassFile applicationClass = classes.get(candidate);
            candidate = applicationClass == null
                ? platformThrowableParent(candidate)
                : applicationClass.superName();
            if (candidate != null && BytecodeToIR.isAssignableThrowable(classes, second, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String platformThrowableParent(final String type) {
        for (final JdkCallSupport.PlatformThrowableParent parent : JdkCallSupport.platformThrowableParents()) {
            if (parent.type().equals(type)) {
                return parent.parent();
            }
        }
        return null;
    }

    static BlockResult lowerLinearBlock(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int startIndex,
        final int endIndex,
        final List<StackValue> stackPrefix,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<String, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow
    ) {
        return lowerLinearBlock(
            classes,
            classFile,
            method,
            bytecode,
            startIndex,
            endIndex,
            stackPrefix,
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            objectLocalLambdas,
            localDeclarations,
            dispatches,
            functionOrNullTargetIds,
            materializedLambdaMethods,
            functionValueFlow,
            new HashMap<>(),
            SourceLineIndex.empty()
        );
    }
    static BlockResult lowerLinearBlock(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int startIndex,
        final int endIndex,
        final List<StackValue> stackPrefix,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<String, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        final List<IrInstruction> blockInstructions = new ArrayList<>();
        final List<StackValue> blockStack = new ArrayList<>(stackPrefix);
        final Map<Integer, IrExpression> blockLocals = copyExpressionLocals(locals);
        final Map<Integer, StackKind> blockObjectLocalKinds = copyObjectLocalKinds(objectLocalKinds);
        final Map<Integer, ThrowableValue> blockObjectLocalThrowableTypes = copyObjectLocalThrowableTypes(objectLocalThrowableTypes);
        final Map<Integer, DynamicLambda> blockObjectLocalLambdas = copyObjectLocalLambdas(objectLocalLambdas);
        final int lastMaterializingDuplicateOffset = BytecodeToIR.lastMaterializingDuplicateOffset(bytecode);
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
                pendingExceptionHandlerStacks,
                blockLocals,
                blockObjectLocalKinds,
                blockObjectLocalThrowableTypes,
                blockObjectLocalLambdas,
                localDeclarations,
                dispatches,
                functionOrNullTargetIds,
                materializedLambdaMethods,
                functionValueFlow,
                sourceLines,
                lastMaterializingDuplicateOffset
            );
        }
        return new BlockResult(List.copyOf(blockInstructions), List.copyOf(blockStack));
    }
    static boolean hasSelectedValue(final List<StackValue> prefix, final List<StackValue> branchStack) {
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
    static boolean lowerSwitchValueSelection(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode,
        final int index,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, ThrowableValue> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<String, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final List<Integer> skippedOffsets,
        final List<Integer> replacementLabelOffsets
    ) {
        final Instruction instruction = bytecode.get(index);
        if (instruction.opcode() != 170 && instruction.opcode() != 171) {
            return false;
        }
        final List<SwitchEntry> entries = switchEntries(instruction);
        if (entries.isEmpty()) {
            return false;
        }
        final List<Integer> targetOffsets = uniqueSortedTargetOffsets(entries);
        if (targetOffsets.isEmpty()) {
            return false;
        }
        int doneOffset = -1;
        final Map<Integer, Integer> blockEnds = new HashMap<>();
        final List<Integer> terminalThrowTargets = new ArrayList<>();
        final List<Integer> nestedValueTargets = new ArrayList<>();
        for (int targetCursor = 0; targetCursor < targetOffsets.size(); targetCursor++) {
            final int targetOffset = targetOffsets.get(targetCursor);
            final int targetIndex = instructionIndex(bytecode, targetOffset);
            if (targetIndex < 0 || targetIndex <= index) {
                return false;
            }
            final int knownDoneIndex = instructionIndex(bytecode, doneOffset);
            final int nextTargetIndex = targetCursor + 1 < targetOffsets.size()
                ? instructionIndex(bytecode, targetOffsets.get(targetCursor + 1))
                : knownDoneIndex > targetIndex ? knownDoneIndex : bytecode.size();
            final int jumpIndex = unconditionalJumpBefore(bytecode, targetIndex, nextTargetIndex);
            if (jumpIndex >= 0) {
                final int candidateDoneOffset = branchTarget(bytecode.get(jumpIndex));
                if (candidateDoneOffset <= targetOffset) {
                    return false;
                }
                if (doneOffset >= 0 && doneOffset != candidateDoneOffset) {
                    return false;
                }
                doneOffset = candidateDoneOffset;
                boolean nestedLastBlock = false;
                if (targetCursor + 1 == targetOffsets.size()) {
                    for (int cursor = targetIndex; cursor < jumpIndex; cursor++) {
                        if (isConditionalBranch(bytecode.get(cursor).opcode())) {
                            nestedLastBlock = true;
                            break;
                        }
                    }
                }
                if (nestedLastBlock) {
                    final int caseEndIndex = instructionIndex(bytecode, candidateDoneOffset);
                    if (caseEndIndex <= jumpIndex) {
                        return false;
                    }
                    blockEnds.put(targetOffset, caseEndIndex);
                    nestedValueTargets.add(targetOffset);
                } else {
                    blockEnds.put(targetOffset, jumpIndex);
                }
                continue;
            }
            final int nestedEndIndex = targetCursor + 1 < targetOffsets.size()
                ? nextTargetIndex : instructionIndex(bytecode, doneOffset);
            if (nestedEndIndex > targetIndex) {
                int candidateDoneOffset = -1;
                int jumpCount = 0;
                boolean hasConditional = false;
                for (int cursor = targetIndex; cursor < nestedEndIndex; cursor++) {
                    final Instruction candidate = bytecode.get(cursor);
                    if (isConditionalBranch(candidate.opcode())) {
                        hasConditional = true;
                    } else if (candidate.opcode() == 167) {
                        final int target = branchTarget(candidate);
                        if (candidateDoneOffset >= 0 && candidateDoneOffset != target) {
                            return false;
                        }
                        candidateDoneOffset = target;
                        jumpCount++;
                    }
                }
                if (jumpCount > 1 && hasConditional && candidateDoneOffset > targetOffset) {
                    if (doneOffset >= 0 && doneOffset != candidateDoneOffset) {
                        return false;
                    }
                    doneOffset = candidateDoneOffset;
                    blockEnds.put(targetOffset, nestedEndIndex);
                    nestedValueTargets.add(targetOffset);
                    continue;
                }
            }
            if (endsWithThrow(bytecode, targetIndex, nextTargetIndex)) {
                blockEnds.put(targetOffset, nextTargetIndex);
                terminalThrowTargets.add(targetOffset);
                continue;
            }
            if (targetCursor + 1 < targetOffsets.size()) {
                return false;
            }
        }
        if (doneOffset < 0) {
            return false;
        }
        final int doneIndex = instructionIndex(bytecode, doneOffset);
        if (doneIndex < 0 || doneIndex <= index) {
            return false;
        }
        final int consumer = bytecode.get(doneIndex).opcode();
        if (!isValueConsumer(consumer) && !(consumer >= 96 && consumer <= 131)) {
            return false;
        }
        final int lastTargetOffset = targetOffsets.getLast();
        if (!blockEnds.containsKey(lastTargetOffset)) {
            final int lastTargetIndex = instructionIndex(bytecode, lastTargetOffset);
            if (lastTargetIndex < 0 || lastTargetIndex > doneIndex || containsControlTransfer(bytecode, lastTargetIndex, doneIndex)) {
                return false;
            }
            blockEnds.put(lastTargetOffset, doneIndex);
        }

        final List<StackValue> selectorStack = new ArrayList<>(stack);
        popInt(classFile, method, selectorStack);
        final List<StackValue> prefix = List.copyOf(selectorStack);
        final int originalLocalDeclarationCount = localDeclarations.size();
        final Map<Integer, IrLocal> workingDeclarations = copyLocalDeclarations(localDeclarations);
        final Map<Integer, StackValue> workingPendingExceptionHandlerStacks =
            new HashMap<>(pendingExceptionHandlerStacks);
        final List<SwitchBlock> blocks = new ArrayList<>();
        final List<Integer> nestedReplacementOffsets = new ArrayList<>();
        StackKind selectedKind = null;
        for (final int targetOffset : targetOffsets) {
            final int targetIndex = instructionIndex(bytecode, targetOffset);
            final int blockEndIndex = blockEnds.get(targetOffset);
            final BlockResult block;
            if (nestedValueTargets.contains(targetOffset)) {
                int conditionalIndex = targetIndex;
                while (conditionalIndex < blockEndIndex
                    && !isConditionalBranch(bytecode.get(conditionalIndex).opcode())) {
                    final int opcode = bytecode.get(conditionalIndex).opcode();
                    if (isControlTransfer(opcode) || opcode >= 54 && opcode <= 78
                        || opcode == 132 || opcode == 196) {
                        throw unsupportedBranchValueMerge(classFile, method, bytecode.get(conditionalIndex));
                    }
                    conditionalIndex++;
                }
                if (conditionalIndex >= blockEndIndex) {
                    throw unsupportedBranchValueMerge(classFile, method, instruction);
                }
                final BlockResult preparation = lowerLinearBlock(
                    classes, classFile, method, bytecode, targetIndex, conditionalIndex, prefix, locals,
                    objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, workingDeclarations,
                    dispatches, functionOrNullTargetIds, materializedLambdaMethods, functionValueFlow,
                    workingPendingExceptionHandlerStacks, sourceLines
                );
                final List<IrInstruction> blockInstructions = new ArrayList<>(preparation.instructions());
                final List<StackValue> blockStack = new ArrayList<>(preparation.stack());
                final List<Integer> nestedSkippedOffsets = new ArrayList<>();
                if (!lowerNestedBranchValueSelection(
                    classes, classFile, method, bytecode, conditionalIndex, blockInstructions, blockStack,
                    locals, objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, workingDeclarations,
                    dispatches, functionOrNullTargetIds, materializedLambdaMethods, functionValueFlow,
                    workingPendingExceptionHandlerStacks, sourceLines, nestedSkippedOffsets,
                    nestedReplacementOffsets, blockEndIndex, true
                )) {
                    throw unsupportedBranchValueMerge(classFile, method, bytecode.get(conditionalIndex));
                }
                block = new BlockResult(List.copyOf(blockInstructions), List.copyOf(blockStack));
            } else {
                block = lowerLinearBlock(
                    classes, classFile, method, bytecode, targetIndex, blockEndIndex, prefix, locals,
                    objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, workingDeclarations,
                    dispatches, functionOrNullTargetIds, materializedLambdaMethods, functionValueFlow,
                    workingPendingExceptionHandlerStacks, sourceLines
                );
            }
            if (terminalThrowTargets.contains(targetOffset)) {
                blocks.add(new SwitchBlock(targetOffset, blockEndIndex, block.instructions(), Optional.empty()));
                continue;
            }
            if (!hasSelectedValue(prefix, block.stack())) {
                return false;
            }
            final StackValue selectedValue = block.stack().getLast();
            if (selectedKind == null) {
                selectedKind = selectedValue.kind();
            } else if (selectedKind != selectedValue.kind()) {
                throw unsupportedBranchValueMerge(classFile, method, instruction);
            }
            blocks.add(new SwitchBlock(targetOffset, blockEndIndex, block.instructions(), Optional.of(selectedValue)));
        }
        if (selectedKind == null) {
            return false;
        }
        appendNewLocalDeclarations(localDeclarations, workingDeclarations, originalLocalDeclarationCount);
        pendingExceptionHandlerStacks.putAll(workingPendingExceptionHandlerStacks);
        replacementLabelOffsets.addAll(nestedReplacementOffsets);
        final IrExpression selector = switchSelector(classFile, method, instructions, stack, localDeclarations);
        final IrType valueType = stackKindType(selectedKind);
        final String localName = "switchValue" + localDeclarations.size() + "_" + instruction.offset();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(valueType, localName));
        final String doneLabel = "switch_value_done_" + instruction.offset();
        for (final SwitchEntry entry : entries) {
            if (entry.value().isPresent()) {
                instructions.add(IrInstruction.branchIf(
                    label(entry.targetOffset()),
                    IrExpression.intComparison("==", selector, IrExpression.intLiteral(entry.value().orElseThrow()))
                ));
            }
        }
        instructions.add(IrInstruction.jump(label(defaultTarget(entries))));
        for (final SwitchBlock block : blocks) {
            addInt(replacementLabelOffsets, block.targetOffset());
            instructions.add(IrInstruction.label(label(block.targetOffset())));
            instructions.addAll(block.instructions());
            if (block.selectedValue().isPresent()) {
                instructions.add(assignLocal(
                    selectedKind,
                    localName,
                    stackValueExpression(block.selectedValue().orElseThrow())
                ));
                instructions.add(IrInstruction.jump(doneLabel));
            }
            addInstructionOffsets(bytecode, instructionIndex(bytecode, block.targetOffset()), block.endIndex(), skippedOffsets);
            if (block.endIndex() < bytecode.size() && bytecode.get(block.endIndex()).opcode() == 167) {
                addInt(skippedOffsets, bytecode.get(block.endIndex()).offset());
            }
        }
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(stackValue(selectedKind, localExpression(valueType, new IrLocal(valueType, localName))));
        return true;
    }
    static boolean endsWithThrow(final List<Instruction> bytecode, final int startIndex, final int endIndex) {
        if (startIndex >= endIndex) {
            return false;
        }
        return bytecode.get(endIndex - 1).opcode() == 191
            && !containsControlTransfer(bytecode, startIndex, endIndex - 1);
    }
    static IrExpression branchCondition(
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
    static boolean isConditionalBranch(final int opcode) {
        return (opcode >= 153 && opcode <= 166)
            || opcode == 198
            || opcode == 199;
    }
    static boolean isControlTransfer(final int opcode) {
        return (opcode >= 153 && opcode <= 167)
            || opcode == 170
            || opcode == 171
            || opcode == 198
            || opcode == 199;
    }
    static boolean isValueConsumer(final int opcode) {
        return (opcode >= 172 && opcode <= 176)
            || opcode == 54
            || (opcode >= 59 && opcode <= 62)
            || opcode == 55
            || (opcode >= 63 && opcode <= 66)
            || opcode == 56
            || (opcode >= 67 && opcode <= 70)
            || opcode == 57
            || (opcode >= 71 && opcode <= 74)
            || opcode == 58
            || (opcode >= 75 && opcode <= 78);
    }
    static boolean hasOnlyTargetBranches(
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
    static boolean containsControlTransfer(final List<Instruction> bytecode, final int startIndex, final int endIndex) {
        for (int index = startIndex; index < endIndex; index++) {
            if (isControlTransfer(bytecode.get(index).opcode())) {
                return true;
            }
        }
        return false;
    }
    static boolean hasEarlierBranchTarget(final List<Instruction> bytecode, final int currentIndex, final int targetOffset) {
        for (int index = 0; index < currentIndex; index++) {
            final Instruction instruction = bytecode.get(index);
            if (isSimpleBranch(instruction.opcode()) && branchTarget(instruction) == targetOffset) {
                return true;
            }
        }
        return false;
    }

    static boolean hasEarlierControlFlowEntry(
        final List<Instruction> bytecode,
        final int currentIndex,
        final int startOffset,
        final int endOffset
    ) {
        for (int index = 0; index < currentIndex; index++) {
            final Instruction instruction = bytecode.get(index);
            if (isSimpleBranch(instruction.opcode())) {
                final int targetOffset = branchTarget(instruction);
                if (targetOffset >= startOffset && targetOffset < endOffset) {
                    return true;
                }
            } else if (instruction.opcode() == 170 || instruction.opcode() == 171) {
                for (final SwitchEntry entry : switchEntries(instruction)) {
                    if (entry.targetOffset() >= startOffset && entry.targetOffset() < endOffset) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isSimpleBranch(final int opcode) {
        return (opcode >= 153 && opcode <= 168)
            || opcode == 198
            || opcode == 199
            || opcode == 200
            || opcode == 201;
    }
    static int unconditionalJumpBefore(final List<Instruction> bytecode, final int startIndex, final int endIndex) {
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
    static void addInstructionOffsets(
        final List<Instruction> bytecode,
        final int startIndex,
        final int endIndex,
        final List<Integer> offsets
    ) {
        for (int index = startIndex; index < endIndex; index++) {
            addInt(offsets, bytecode.get(index).offset());
        }
    }
    static Map<Integer, IrExpression> copyExpressionLocals(final Map<Integer, IrExpression> source) {
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
    static Map<Integer, StackKind> copyObjectLocalKinds(final Map<Integer, StackKind> source) {
        final Map<Integer, StackKind> result = new HashMap<>();
        int slot = 0;
        while (slot < 512) {
            final StackKind value = source.get(slot);
            if (value != null) {
                result.put(slot, value);
            }
            slot++;
        }
        return result;
    }
    static Map<Integer, ThrowableValue> copyObjectLocalThrowableTypes(final Map<Integer, ThrowableValue> source) {
        final Map<Integer, ThrowableValue> result = new HashMap<>();
        int slot = 0;
        while (slot < 512) {
            final ThrowableValue value = source.get(slot);
            if (value != null) {
                result.put(slot, value);
            }
            slot++;
        }
        return result;
    }
    static Map<Integer, DynamicLambda> copyObjectLocalLambdas(final Map<Integer, DynamicLambda> source) {
        return new HashMap<>(source);
    }
    static Map<Integer, IrLocal> copyLocalDeclarations(final Map<Integer, IrLocal> source) {
        final Map<Integer, IrLocal> result = new LinkedHashMap<>();
        int index = 0;
        for (final IrLocal local : source.values()) {
            result.put(Integer.MIN_VALUE + index, local);
            index++;
        }
        return result;
    }
    static void appendNewLocalDeclarations(
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
    static DiagnosticException unsupportedBranchValueMerge(
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
    static int instructionIndex(final List<Instruction> instructions, final int offset) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() == offset) {
                return index;
            }
        }
        return -1;
    }
    static IrType stackKindType(final StackKind kind) {
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
        if (isObjectLike(kind)) {
            return IrType.OBJECT;
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }
    static IrInstruction assignLocal(final StackKind kind, final String localName, final IrExpression expression) {
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
        if (isObjectLike(kind)) {
            return IrInstruction.assignObject(localName, expression);
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }
    static StackValue stackValue(final StackKind kind, final IrExpression expression) {
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
        if (kind == StackKind.CAUGHT_THROWABLE) {
            return StackValue.caughtThrowable(expression);
        }
        if (kind == StackKind.VIRTUAL_THREAD_BUILDER) {
            return StackValue.virtualThreadBuilder(expression);
        }
        if (kind == StackKind.VIRTUAL_THREAD_FACTORY) {
            return StackValue.virtualThreadFactory(expression);
        }
        if (kind == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            return StackValue.virtualThreadExecutor(expression);
        }
        if (kind == StackKind.THREAD_FUTURE) {
            return StackValue.threadFuture(expression);
        }
        if (kind == StackKind.SCHEDULED_THREAD_POOL_EXECUTOR) {
            return StackValue.scheduledThreadPoolExecutor(expression);
        }
        if (kind == StackKind.SOCKET_INPUT_STREAM) {
            return StackValue.socketInputStream(expression);
        }
        if (kind == StackKind.RESOURCE_INPUT_STREAM) {
            return StackValue.resourceInputStream(expression);
        }
        if (kind == StackKind.HTTP_EXCHANGE_INPUT_STREAM) {
            return StackValue.httpExchangeInputStream(expression);
        }
        if (kind == StackKind.SOCKET_OUTPUT_STREAM) {
            return StackValue.socketOutputStream(expression);
        }
        if (kind == StackKind.HTTP_EXCHANGE_OUTPUT_STREAM) {
            return StackValue.httpExchangeOutputStream(expression);
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }
    static boolean isObjectLike(final StackKind kind) {
        return kind == StackKind.OBJECT
            || kind == StackKind.CAUGHT_THROWABLE
            || kind == StackKind.VIRTUAL_THREAD_BUILDER
            || kind == StackKind.VIRTUAL_THREAD_FACTORY
            || kind == StackKind.VIRTUAL_THREAD_EXECUTOR
            || kind == StackKind.THREAD_FUTURE
            || kind == StackKind.SCHEDULED_THREAD_POOL_EXECUTOR
            || kind == StackKind.PRINT_STREAM
            || kind == StackKind.ERROR_PRINT_STREAM
            || kind == StackKind.SOCKET_INPUT_STREAM
            || kind == StackKind.RESOURCE_INPUT_STREAM
            || kind == StackKind.HTTP_EXCHANGE_INPUT_STREAM
            || kind == StackKind.SOCKET_OUTPUT_STREAM
            || kind == StackKind.HTTP_EXCHANGE_OUTPUT_STREAM;
    }
    static void tableSwitch(
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
    static void lookupSwitch(
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
    static IrExpression switchSelector(
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

    static List<SwitchEntry> switchEntries(final Instruction instruction) {
        final List<SwitchEntry> result = new ArrayList<>();
        final int padding = switchPadding(instruction.offset());
        if (instruction.opcode() == 170) {
            final int defaultTarget = instruction.offset() + int32(instruction.operands(), padding);
            final int low = int32(instruction.operands(), padding + 4);
            final int high = int32(instruction.operands(), padding + 8);
            int operandOffset = padding + 12;
            for (int value = low; value <= high; value++) {
                final int target = instruction.offset() + int32(instruction.operands(), operandOffset);
                result.add(new SwitchEntry(Optional.of(value), target));
                operandOffset += 4;
            }
            result.add(new SwitchEntry(Optional.empty(), defaultTarget));
            return result;
        }
        final int defaultTarget = instruction.offset() + int32(instruction.operands(), padding);
        final int pairs = int32(instruction.operands(), padding + 4);
        int operandOffset = padding + 8;
        for (int index = 0; index < pairs; index++) {
            final int value = int32(instruction.operands(), operandOffset);
            final int target = instruction.offset() + int32(instruction.operands(), operandOffset + 4);
            result.add(new SwitchEntry(Optional.of(value), target));
            operandOffset += 8;
        }
        result.add(new SwitchEntry(Optional.empty(), defaultTarget));
        return result;
    }

    static List<Integer> uniqueSortedTargetOffsets(final List<SwitchEntry> entries) {
        final List<Integer> result = new ArrayList<>();
        for (final SwitchEntry entry : entries) {
            addInt(result, entry.targetOffset());
        }
        for (int leftIndex = 0; leftIndex < result.size(); leftIndex++) {
            int smallestIndex = leftIndex;
            for (int rightIndex = leftIndex + 1; rightIndex < result.size(); rightIndex++) {
                if (result.get(rightIndex) < result.get(smallestIndex)) {
                    smallestIndex = rightIndex;
                }
            }
            if (smallestIndex != leftIndex) {
                final int left = result.get(leftIndex);
                result.set(leftIndex, result.get(smallestIndex));
                result.set(smallestIndex, left);
            }
        }
        return result;
    }

    static int defaultTarget(final List<SwitchEntry> entries) {
        for (final SwitchEntry entry : entries) {
            if (entry.value().isEmpty()) {
                return entry.targetOffset();
            }
        }
        throw new IllegalArgumentException("Missing default switch target");
    }

    record SwitchEntry(Optional<Integer> value, int targetOffset) {
    }

    record SwitchBlock(
        int targetOffset,
        int endIndex,
        List<IrInstruction> instructions,
        Optional<StackValue> selectedValue
    ) {
    }

}
