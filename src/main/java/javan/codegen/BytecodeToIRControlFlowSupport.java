package javan.codegen;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javan.codegen.BytecodeToIR.*;

final class BytecodeToIRControlFlowSupport {
    static void lowerThrow(
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
        instructions.add(IrInstruction.panic(thrown, sourceLocation(classFile, method, instruction, sourceLines)));
        clearStack(stack);
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
        if (value.kind() == StackKind.OBJECT) {
            return value;
        }
        throw unsupported(classFile, method, instruction);
    }
    static Optional<Integer> exceptionHandler(
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
                if (supportedFinallyRethrowHandler(method.code().orElseThrow(), handler)) {
                    return Optional.of(handler.handlerPc());
                }
                continue;
            }
            if (JdkCallSupport.isPlatformThrowableAssignable(thrownType, handler.catchType().orElseThrow())) {
                return Optional.of(handler.handlerPc());
            }
        }
        return Optional.empty();
    }

    static boolean supportedFinallyRethrowHandler(
        final CodeAttribute code,
        final javan.classfile.CodeException handler
    ) {
        if (handler.catchType().isPresent()) {
            return false;
        }
        final Optional<Instruction> first = instructionAtOffset(code, handler.handlerPc());
        if (first.isEmpty()) {
            return false;
        }
        final int throwableLocal = astoreLocalIndex(first.orElseThrow());
        if (throwableLocal < 0) {
            return false;
        }
        for (int index = 0; index + 1 < code.instructions().size(); index++) {
            final Instruction instruction = code.instructions().get(index);
            if (instruction.offset() < handler.handlerPc()) {
                continue;
            }
            if (aloadLocalIndex(instruction) == throwableLocal && code.instructions().get(index + 1).opcode() == 191) {
                return true;
            }
        }
        return false;
    }

    private static int aloadLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 25) {
            if (instruction.operands().length == 0) {
                return -1;
            }
            return instruction.operands()[0] & 0xFF;
        }
        if (opcode >= 42 && opcode <= 45) {
            return opcode - 42;
        }
        return -1;
    }

    private static int astoreLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 58) {
            if (instruction.operands().length == 0) {
                return -1;
            }
            return instruction.operands()[0] & 0xFF;
        }
        if (opcode >= 75 && opcode <= 78) {
            return opcode - 75;
        }
        return -1;
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
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
            objectLocalKinds,
            objectLocalThrowableTypes,
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
        stack.add(stackValue(valueKind, localExpression(valueType, new IrLocal(valueType, localName))));
        addInt(skippedOffsets, jumpInstruction.offset());
        addInstructionOffsets(bytecode, index + 1, jumpIndex, skippedOffsets);
        addInstructionOffsets(bytecode, targetIndex, doneIndex, skippedOffsets);
        return true;
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
        final Map<Integer, String> objectLocalThrowableTypes,
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
        final Map<Integer, StackKind> workingObjectLocalKinds = copyObjectLocalKinds(objectLocalKinds);
        final Map<Integer, String> workingObjectLocalThrowableTypes = copyObjectLocalThrowableTypes(objectLocalThrowableTypes);
        final List<IrInstruction> mergedInstructions = new ArrayList<>();
        final List<StackValue> workingStack = new ArrayList<>(stack);
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
                    new HashMap<>(),
                    workingLocals,
                    workingObjectLocalKinds,
                    workingObjectLocalThrowableTypes,
                    workingDeclarations,
                    dispatches,
                    SourceLineIndex.empty()
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        final List<IrInstruction> blockInstructions = new ArrayList<>();
        final List<StackValue> blockStack = new ArrayList<>(stackPrefix);
        final Map<Integer, IrExpression> blockLocals = copyExpressionLocals(locals);
        final Map<Integer, StackKind> blockObjectLocalKinds = copyObjectLocalKinds(objectLocalKinds);
        final Map<Integer, String> blockObjectLocalThrowableTypes = copyObjectLocalThrowableTypes(objectLocalThrowableTypes);
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
                blockObjectLocalKinds,
                blockObjectLocalThrowableTypes,
                localDeclarations,
                dispatches,
                SourceLineIndex.empty()
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
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
        for (int targetCursor = 0; targetCursor < targetOffsets.size(); targetCursor++) {
            final int targetOffset = targetOffsets.get(targetCursor);
            final int targetIndex = instructionIndex(bytecode, targetOffset);
            if (targetIndex < 0 || targetIndex <= index) {
                return false;
            }
            final int nextTargetIndex = targetCursor + 1 < targetOffsets.size()
                ? instructionIndex(bytecode, targetOffsets.get(targetCursor + 1))
                : bytecode.size();
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
                blockEnds.put(targetOffset, jumpIndex);
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
        if (doneIndex < 0 || doneIndex <= index || !isValueConsumer(bytecode.get(doneIndex).opcode())) {
            return false;
        }
        final int lastTargetOffset = targetOffsets.getLast();
        final int lastTargetIndex = instructionIndex(bytecode, lastTargetOffset);
        if (lastTargetIndex < 0 || lastTargetIndex > doneIndex || containsControlTransfer(bytecode, lastTargetIndex, doneIndex)) {
            return false;
        }
        blockEnds.put(lastTargetOffset, doneIndex);

        final List<StackValue> selectorStack = new ArrayList<>(stack);
        popInt(classFile, method, selectorStack);
        final List<StackValue> prefix = List.copyOf(selectorStack);
        final int originalLocalDeclarationCount = localDeclarations.size();
        final Map<Integer, IrLocal> workingDeclarations = copyLocalDeclarations(localDeclarations);
        final List<SwitchBlock> blocks = new ArrayList<>();
        StackKind selectedKind = null;
        for (final int targetOffset : targetOffsets) {
            final int targetIndex = instructionIndex(bytecode, targetOffset);
            final int blockEndIndex = blockEnds.get(targetOffset);
            final BlockResult block = lowerLinearBlock(
                classes,
                classFile,
                method,
                bytecode,
                targetIndex,
                blockEndIndex,
                prefix,
                locals,
                objectLocalKinds,
                objectLocalThrowableTypes,
                workingDeclarations,
                dispatches
            );
            if (!hasSelectedValue(prefix, block.stack())) {
                return false;
            }
            final StackValue selectedValue = block.stack().getLast();
            if (selectedKind == null) {
                selectedKind = selectedValue.kind();
            } else if (selectedKind != selectedValue.kind()) {
                throw unsupportedBranchValueMerge(classFile, method, instruction);
            }
            blocks.add(new SwitchBlock(targetOffset, blockEndIndex, block.instructions(), selectedValue));
        }
        if (selectedKind == null) {
            return false;
        }
        appendNewLocalDeclarations(localDeclarations, workingDeclarations, originalLocalDeclarationCount);
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
            instructions.add(assignLocal(selectedKind, localName, stackValueExpression(block.selectedValue())));
            instructions.add(IrInstruction.jump(doneLabel));
            addInstructionOffsets(bytecode, instructionIndex(bytecode, block.targetOffset()), block.endIndex(), skippedOffsets);
            if (block.endIndex() < bytecode.size() && bytecode.get(block.endIndex()).opcode() == 167) {
                addInt(skippedOffsets, bytecode.get(block.endIndex()).offset());
            }
        }
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(stackValue(selectedKind, localExpression(valueType, new IrLocal(valueType, localName))));
        return true;
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
    static Map<Integer, String> copyObjectLocalThrowableTypes(final Map<Integer, String> source) {
        final Map<Integer, String> result = new HashMap<>();
        int slot = 0;
        while (slot < 512) {
            final String value = source.get(slot);
            if (value != null) {
                result.put(slot, value);
            }
            slot++;
        }
        return result;
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
        if (kind == StackKind.VIRTUAL_THREAD_BUILDER) {
            return StackValue.virtualThreadBuilder(expression);
        }
        if (kind == StackKind.VIRTUAL_THREAD_FACTORY) {
            return StackValue.virtualThreadFactory(expression);
        }
        if (kind == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            return StackValue.virtualThreadExecutor(expression);
        }
        if (kind == StackKind.SOCKET_INPUT_STREAM) {
            return StackValue.socketInputStream(expression);
        }
        if (kind == StackKind.SOCKET_OUTPUT_STREAM) {
            return StackValue.socketOutputStream(expression);
        }
        throw new IllegalArgumentException("Unsupported selected stack kind");
    }
    static boolean isObjectLike(final StackKind kind) {
        return kind == StackKind.OBJECT
            || kind == StackKind.VIRTUAL_THREAD_BUILDER
            || kind == StackKind.VIRTUAL_THREAD_FACTORY
            || kind == StackKind.VIRTUAL_THREAD_EXECUTOR
            || kind == StackKind.PRINT_STREAM
            || kind == StackKind.ERROR_PRINT_STREAM
            || kind == StackKind.SOCKET_INPUT_STREAM
            || kind == StackKind.SOCKET_OUTPUT_STREAM;
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

    record SwitchBlock(int targetOffset, int endIndex, List<IrInstruction> instructions, StackValue selectedValue) {
    }

}
