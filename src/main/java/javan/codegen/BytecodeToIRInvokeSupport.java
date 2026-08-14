package javan.codegen;

import javan.analysis.ClassInitializationGraph;
import javan.analysis.EntryPoint;
import javan.analysis.FunctionValueFlow;
import javan.analysis.InstantiatedTypeAnalysis;
import javan.analysis.VirtualThreadInvokePatterns;
import javan.classfile.ClassFile;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.ir.IrDispatch;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrType;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRCollectionSupport.*;
import static javan.codegen.BytecodeToIRDynamicSupport.*;
import static javan.codegen.BytecodeToIRThreadSupport.*;
import static javan.codegen.BytecodeToIRLangSupport.*;
import static javan.codegen.BytecodeToIRMathSupport.*;
import static javan.codegen.BytecodeToIRMetadataSupport.*;

final class BytecodeToIRInvokeSupport {
    static final String MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL = "javan_materialized_lambda_apply_object";
    static final String MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL = "javan_materialized_lambda_apply_long_object";
    static final String MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL = "javan_materialized_lambda_apply_object2";
    static final String MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL = "javan_materialized_lambda_apply_supplier";
    static final String MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL = "javan_materialized_lambda_apply_boolean";
    static final String MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL = "javan_materialized_lambda_apply_void";
    static final String MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL = "javan_materialized_lambda_apply_void2";
    static final String MATERIALIZED_LAMBDA_IS_INSTANCE_SYMBOL = "javan_materialized_lambda_is_instance";

    enum MaterializedLambdaDispatchKind {
        OBJECT,
        LONG_OBJECT,
        BOOLEAN,
        VOID,
        SUPPLIER
    }

    static void lowerInstanceOf(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final String target = instruction.className().orElseThrow();
        final IrExpression value = popObject(classFile, method, stack);
        stack.add(StackValue.intExpression(instanceOfExpression(
            classes,
            classFile,
            method,
            instruction,
            target,
            value
        )));
    }

    private static IrExpression instanceOfExpression(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final String target,
        final IrExpression value
    ) {
        if ("java/lang/Object".equals(target)) {
            return IrExpression.intCall("javan_object_non_null", List.of(value));
        }
        final Optional<Integer> wrapperTypeId = platformWrapperTypeId(target);
        final List<IrExpression> arguments = new ArrayList<>();
        arguments.add(value);
        if (wrapperTypeId.isPresent()) {
            arguments.add(IrExpression.intLiteral(1));
            arguments.add(IrExpression.intLiteral(wrapperTypeId.orElseThrow()));
            return IrExpression.intCall("javan_object_type_in", arguments);
        }
        final Optional<Integer> builtinTargetId = JdkCallSupport.builtinInstanceOfTargetId(target);
        if (builtinTargetId.isPresent()) {
            return IrExpression.intCall(
                "javan_object_builtin_instance_of",
                List.of(value, IrExpression.intLiteral(builtinTargetId.orElseThrow()))
            );
        }
        final boolean knownTarget = classes.containsKey(target);
        final List<Integer> typeIds = assignableTypeIds(classes, target);
        if (typeIds.isEmpty() && !knownTarget) {
            throw unsupportedInstanceOfTarget(classFile, method, instruction, target);
        }
        if (typeIds.isEmpty()) {
            return IrExpression.intLiteral(0);
        }
        arguments.add(IrExpression.intLiteral(typeIds.size()));
        for (final int typeId : typeIds) {
            arguments.add(IrExpression.intLiteral(typeId));
        }
        return IrExpression.intCall("javan_object_type_in", arguments);
    }

    static boolean lowerSupportedCheckcast(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        final String target = instruction.className().orElseThrow();
        if (!"java/lang/String".equals(target)
            && !"java/lang/Object".equals(target)) {
            return false;
        }
        final String valueLocal = "object" + localDeclarations.size();
        localDeclarations.put(
            Integer.MIN_VALUE + localDeclarations.size(),
            new IrLocal(IrType.OBJECT, valueLocal)
        );
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            popObject(classFile, method, instruction, stack)
        ));
        final IrExpression value = IrExpression.objectLocal(valueLocal);
        final String successLabel = "label_typed_checkcast_success_"
            + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.objectComparison("==", value, IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "!=",
                IrExpression.intCall(
                    "javan_class_is_instance",
                    List.of(
                        classLiteralExpression(classes, classFile, method, instruction),
                        value
                    )
                ),
                IrExpression.intLiteral(0)
            )
        ));
        final List<StackValue> successStack = new ArrayList<>(stack);
        successStack.add(StackValue.objectExpression(value));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/ClassCastException",
            IrExpression.stringLiteral("Cannot cast value to " + target)
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        return true;
    }

    static void guardTypedReceiver(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        if (instruction.opcode() != 182 && instruction.opcode() != 185) {
            return;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        if (!guardedMapGet(target)
            && !(classes.containsKey(target.owner())
                && classes.get(target.owner()).application()
                && !"<init>".equals(target.name()))) {
            return;
        }
        final IrExpression nullMessage = IrExpression.stringLiteral(
            "Cannot invoke " + target.owner() + "." + target.name() + " on null"
        );
        if (BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile,
            method,
            instruction,
            StackValue.platformThrowable("java/lang/NullPointerException", nullMessage),
            instruction.offset()
        ).isEmpty()) {
            return;
        }
        final int receiverIndex = stack.size()
            - MethodDescriptor.parse(target.descriptor()).parameterTypes().size()
            - 1;
        if (receiverIndex < 0) {
            throw unsupported(classFile, method, instruction);
        }
        final StackValue receiver = stack.get(receiverIndex);
        if (receiver.expression().isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final String receiverLocal = "object" + localDeclarations.size();
        localDeclarations.put(
            Integer.MIN_VALUE + localDeclarations.size(),
            new IrLocal(IrType.OBJECT, receiverLocal)
        );
        instructions.add(IrInstruction.assignObject(
            receiverLocal,
            receiver.expression().orElseThrow()
        ));
        final IrExpression materialized = IrExpression.objectLocal(receiverLocal);
        stack.set(
            receiverIndex,
            new StackValue(
                receiver.kind(),
                receiver.throwableType(),
                Optional.of(materialized),
                receiver.dynamicLambda()
            )
        );
        final List<StackValue> successStack = List.copyOf(stack);
        final String successLabel = "label_typed_receiver_success_"
            + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.objectComparison("!=", materialized, IrExpression.objectNull())
        ));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/NullPointerException",
            nullMessage
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
    }

    private static boolean guardedMapGet(final MethodRef target) {
        return ("java/util/Map".equals(target.owner())
                || "java/util/HashMap".equals(target.owner())
                || "java/util/LinkedHashMap".equals(target.owner())
                || "java/util/TreeMap".equals(target.owner())
                || "java/util/concurrent/ConcurrentHashMap".equals(target.owner()))
            && "get".equals(target.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor())
            && JdkCallSupport.supportedCall(target).isPresent();
    }

    static void pushField(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        if ("java/lang/System".equals(fieldRef.owner())) {
            if ("out".equals(fieldRef.name()) && "Ljava/io/PrintStream;".equals(fieldRef.descriptor())) {
                stack.add(StackValue.printStream());
                return;
            }
            if ("err".equals(fieldRef.name()) && "Ljava/io/PrintStream;".equals(fieldRef.descriptor())) {
                stack.add(StackValue.errorPrintStream());
                return;
            }
        }
        if ("java/io/File".equals(fieldRef.owner())) {
            if ("separatorChar".equals(fieldRef.name()) && "C".equals(fieldRef.descriptor())) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_file_separator_char", List.of())));
                return;
            }
            if ("pathSeparatorChar".equals(fieldRef.name()) && "C".equals(fieldRef.descriptor())) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_file_path_separator_char", List.of())));
                return;
            }
            if ("pathSeparator".equals(fieldRef.name()) && "Ljava/lang/String;".equals(fieldRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_file_path_separator", List.of())));
                return;
            }
        }
        if ("java/nio/charset/StandardCharsets".equals(fieldRef.owner())
            && "Ljava/nio/charset/Charset;".equals(fieldRef.descriptor())
            && !"UTF_8".equals(fieldRef.name())) {
            throw unsupportedStandardCharset(classFile, method, instruction, fieldRef);
        }
        if (pushBuiltinObjectField(fieldRef, stack)) {
            return;
        }
        if (isSupportedJdkEnumConstant(fieldRef)) {
            stack.add(StackValue.objectExpression(enumConstantExpression(classes, fieldRef)));
            return;
        }
        if (isEnumConstant(classes, fieldRef)) {
            stack.add(StackValue.objectExpression(enumConstantExpression(classes, fieldRef)));
            return;
        }
        if ("java/lang/Boolean".equals(fieldRef.owner()) && "Ljava/lang/Boolean;".equals(fieldRef.descriptor())) {
            if ("TRUE".equals(fieldRef.name())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_boolean_value_of", List.of(IrExpression.intLiteral(1)))));
                return;
            }
            if ("FALSE".equals(fieldRef.name())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_boolean_value_of", List.of(IrExpression.intLiteral(0)))));
                return;
            }
        }
        final Optional<IrExpression> primitiveClassField = supportedPrimitiveClassField(fieldRef);
        if (primitiveClassField.isPresent()) {
            stack.add(StackValue.objectExpression(primitiveClassField.orElseThrow()));
            return;
        }
        if (supportedVirtualThreadFactoryStaticField(classes, method, instruction, fieldRef)) {
            stack.add(StackValue.virtualThreadFactory(IrExpression.objectStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        if (supportedVirtualThreadExecutorStaticField(classes, method, instruction, fieldRef)) {
            stack.add(StackValue.virtualThreadExecutor(IrExpression.objectStaticField(fieldRef.owner(), fieldRef.name())));
            return;
        }
        final Optional<IrType> type = staticFieldType(classes, fieldRef);
        if (type.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final String owner = ClassInitializationGraph.staticFieldOwner(classes, fieldRef).orElse(fieldRef.owner());
        switch (type.orElseThrow()) {
            case INT -> stack.add(StackValue.intExpression(IrExpression.intStaticField(owner, fieldRef.name())));
            case LONG -> stack.add(StackValue.longExpression(IrExpression.longStaticField(owner, fieldRef.name())));
            case FLOAT -> stack.add(StackValue.floatExpression(IrExpression.floatStaticField(owner, fieldRef.name())));
            case DOUBLE -> stack.add(StackValue.doubleExpression(IrExpression.doubleStaticField(owner, fieldRef.name())));
            case OBJECT -> stack.add(StackValue.objectExpression(IrExpression.objectStaticField(owner, fieldRef.name())));
        }
    }

    static void assignStaticField(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        final IrType type = requiredIrType(staticFieldType(classes, fieldRef), classFile, method, instruction);
        final String owner = ClassInitializationGraph.staticFieldOwner(classes, fieldRef).orElse(fieldRef.owner());
        switch (type) {
            case INT -> instructions.add(IrInstruction.assignStaticFieldInt(owner, fieldRef.name(), popInt(classFile, method, stack)));
            case LONG -> instructions.add(IrInstruction.assignStaticFieldLong(owner, fieldRef.name(), popLong(classFile, method, stack)));
            case FLOAT -> instructions.add(IrInstruction.assignStaticFieldFloat(owner, fieldRef.name(), popFloat(classFile, method, stack)));
            case DOUBLE -> instructions.add(IrInstruction.assignStaticFieldDouble(owner, fieldRef.name(), popDouble(classFile, method, stack)));
            case OBJECT -> instructions.add(IrInstruction.assignStaticFieldObject(owner, fieldRef.name(), popObject(classFile, method, stack)));
            case VOID -> throw new IllegalStateException("void static field is invalid");
        }
    }

    static void pushInstanceField(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        final IrType type = requiredIrType(fieldType(fieldRef.descriptor()), classFile, method, instruction);
        final IrExpression receiver = popObject(classFile, method, stack);
        switch (type) {
            case INT -> stack.add(StackValue.intExpression(IrExpression.intField(fieldRef.owner(), fieldRef.name(), receiver)));
            case LONG -> stack.add(StackValue.longExpression(IrExpression.longField(fieldRef.owner(), fieldRef.name(), receiver)));
            case FLOAT -> stack.add(StackValue.floatExpression(IrExpression.floatField(fieldRef.owner(), fieldRef.name(), receiver)));
            case DOUBLE -> stack.add(StackValue.doubleExpression(IrExpression.doubleField(fieldRef.owner(), fieldRef.name(), receiver)));
            case OBJECT -> stack.add(StackValue.objectExpression(IrExpression.objectField(fieldRef.owner(), fieldRef.name(), receiver)));
            case VOID -> throw new IllegalStateException("void instance field is invalid");
        }
    }

    static void assignInstanceField(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        final IrType type = requiredIrType(fieldType(fieldRef.descriptor()), classFile, method, instruction);
        switch (type) {
            case INT -> {
                final IrExpression value = popInt(classFile, method, stack);
                final IrExpression receiver = popObject(classFile, method, stack);
                instructions.add(IrInstruction.assignFieldInt(fieldRef.owner(), fieldRef.name(), receiver, value));
            }
            case LONG -> {
                final IrExpression value = popLong(classFile, method, stack);
                final IrExpression receiver = popObject(classFile, method, stack);
                instructions.add(IrInstruction.assignFieldLong(fieldRef.owner(), fieldRef.name(), receiver, value));
            }
            case FLOAT -> {
                final IrExpression value = popFloat(classFile, method, stack);
                final IrExpression receiver = popObject(classFile, method, stack);
                instructions.add(IrInstruction.assignFieldFloat(fieldRef.owner(), fieldRef.name(), receiver, value));
            }
            case DOUBLE -> {
                final IrExpression value = popDouble(classFile, method, stack);
                final IrExpression receiver = popObject(classFile, method, stack);
                instructions.add(IrInstruction.assignFieldDouble(fieldRef.owner(), fieldRef.name(), receiver, value));
            }
            case OBJECT -> {
                final IrExpression value = popObject(classFile, method, stack);
                final IrExpression receiver = popObject(classFile, method, stack);
                instructions.add(IrInstruction.assignFieldObject(fieldRef.owner(), fieldRef.name(), receiver, value));
            }
            case VOID -> throw new IllegalStateException("void instance field write is invalid");
        }
    }

    static IrType requiredIrType(
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

    static void lowerVirtualCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final SourceLineIndex sourceLines
    ) {
        final MethodRef rawMethodRef = instruction.methodRef().orElseThrow();
        final MethodRef methodRef = JdkCallSupport.normalizeInheritedSupportedJdkCall(classes, rawMethodRef)
            .orElse(rawMethodRef);
        if (lowerPrintStreamCall(classFile, method, instruction, methodRef, instructions, stack)) {
            return;
        }
        if (isEnumIntrinsic(classes, methodRef)) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_from", List.of(receiver));
            return;
        }
        if (isEnumOrdinal(classes, methodRef)) {
            lowerEnumOrdinal(classes, classFile, method, methodRef, stack);
            return;
        }
        if (lowerArrayClone(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerObjectClone(classes, classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJavanFiles2CreateDirectoriesIfPossible(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJavanProcessRunnerRun(classes, classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerScheduledThreadPoolExecutorCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "length".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_length", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "hashCode".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_hash_code", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_is_empty", List.of(popObject(classFile, method, stack)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "isBlank".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_is_blank", List.of(popObject(classFile, method, stack)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "charAt".equals(methodRef.name()) && "(I)C".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_char_at", List.of(receiver, index))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "indexOf".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_char", List.of(receiver, ch))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "indexOf".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_char_from", List.of(receiver, ch, fromIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "indexOf".equals(methodRef.name())
            && "(Ljava/lang/String;)I".equals(methodRef.descriptor())) {
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            rejectUnsupportedStringSemantic(classFile, method, instruction, needle);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_string", List.of(receiver, needle))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "indexOf".equals(methodRef.name())
            && "(Ljava/lang/String;I)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            rejectUnsupportedStringSemantic(classFile, method, instruction, needle);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_index_of_string_from", List.of(receiver, needle, fromIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "lastIndexOf".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_last_index_of_char", List.of(receiver, ch))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "lastIndexOf".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_last_index_of_char_from", List.of(receiver, ch, fromIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "lastIndexOf".equals(methodRef.name())
            && "(Ljava/lang/String;)I".equals(methodRef.descriptor())) {
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            rejectUnsupportedStringSemantic(classFile, method, instruction, needle);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_last_index_of_string", List.of(receiver, needle))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "lastIndexOf".equals(methodRef.name())
            && "(Ljava/lang/String;I)I".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            rejectUnsupportedStringSemantic(classFile, method, instruction, needle);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_last_index_of_string_from", List.of(receiver, needle, fromIndex))));
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
            && "startsWith".equals(methodRef.name())
            && "(Ljava/lang/String;I)Z".equals(methodRef.descriptor())) {
            final IrExpression fromIndex = popInt(classFile, method, stack);
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_string_starts_with_from",
                List.of(receiver, argument, fromIndex)
            )));
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
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            stack.add(StackValue.objectExpression(receiver));
            return;
        }
        if ("java/lang/Object".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_object_equals", List.of(receiver, argument))));
            return;
        }
        if ("java/lang/Object".equals(methodRef.owner())
            && "getClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_object_get_class", List.of(receiver))));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "isInstance".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression value = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_class_is_instance", List.of(receiver, value))));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "cast".equals(methodRef.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression value = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_cast", List.of(receiver, value));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "isEnum".equals(methodRef.name())
            && "()Z".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_class_is_enum", List.of(receiver))));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "isArray".equals(methodRef.name())
            && "()Z".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_class_is_array", List.of(receiver))));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "isPrimitive".equals(methodRef.name())
            && "()Z".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_class_is_primitive", List.of(receiver))));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "isAssignableFrom".equals(methodRef.name())
            && "(Ljava/lang/Class;)Z".equals(methodRef.descriptor())) {
            final IrExpression source = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_class_is_assignable_from", List.of(receiver, source))));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "getName".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_runtime_class_get_name", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "getResourceAsStream".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/io/InputStream;".equals(methodRef.descriptor())) {
            final IrExpression name = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_class_resource_as_stream", List.of(receiver, name))));
            stack.add(StackValue.resourceInputStream(IrExpression.objectLocal(localName)));
            return;
        }
        if ("java/lang/ClassLoader".equals(methodRef.owner())
            && "getSystemResourceAsStream".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/io/InputStream;".equals(methodRef.descriptor())) {
            final IrExpression name = popObject(classFile, method, stack);
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_loader_resource_as_stream", List.of(name))));
            stack.add(StackValue.resourceInputStream(IrExpression.objectLocal(localName)));
            return;
        }
        if ("java/lang/ClassLoader".equals(methodRef.owner())
            && "getResourceAsStream".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/io/InputStream;".equals(methodRef.descriptor())) {
            final IrExpression name = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_class_loader_resource_as_stream", List.of(receiver, name))));
            stack.add(StackValue.resourceInputStream(IrExpression.objectLocal(localName)));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "getSimpleName".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_simple_name", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "getPackageName".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_package_name", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "getTypeName".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_type_name", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "descriptorString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_descriptor_string", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "getComponentType".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_component_type", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "componentType".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_component_type", List.of(receiver));
            return;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && "arrayType".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_class_array_type", List.of(receiver));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "concat".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(argument)));
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            instructions.add(IrInstruction.assignObject(
                localName,
                IrExpression.stringConcat("\u0001\u0001", List.of(receiver, argument))
            ));
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(localName)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "repeat".equals(methodRef.name())
            && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression count = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_repeat", List.of(receiver, count));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "intern".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "describeConstable".equals(methodRef.name())
            && "()Ljava/util/Optional;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_of", List.of(receiver))));
            return;
        }
        if (isDirectConstableWrapperOwner(methodRef.owner())
            && "describeConstable".equals(methodRef.name())
            && "()Ljava/util/Optional;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_of", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "resolveConstantDesc".equals(methodRef.name())
            && ("(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;".equals(methodRef.descriptor())
            || "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;".equals(methodRef.descriptor()))) {
            popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(popObject(classFile, method, stack)));
            return;
        }
        if (isDirectConstableWrapperOwner(methodRef.owner())
            && "resolveConstantDesc".equals(methodRef.name())
            && isDirectConstableWrapperResolveDescriptor(methodRef.owner(), methodRef.descriptor())) {
            popObject(classFile, method, stack);
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
            && "strip".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_trim", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "stripLeading".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_strip_leading", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "stripTrailing".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_strip_trailing", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "getBytes".equals(methodRef.name())
            && "(Ljava/nio/charset/Charset;)[B".equals(methodRef.descriptor())) {
            final IrExpression charset = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            lowerStringGetBytesCharset(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                receiver,
                charset
            );
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "toLowerCase".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_to_lower_case", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "toLowerCase".equals(methodRef.name())
            && "(Ljava/util/Locale;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression locale = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            lowerStringToLowerCaseLocale(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                receiver,
                locale
            );
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "toUpperCase".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_to_upper_case", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "substring".equals(methodRef.name())
            && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring", List.of(receiver, begin));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "substring".equals(methodRef.name())
            && "(II)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring_range", List.of(receiver, begin, end));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "subSequence".equals(methodRef.name())
            && "(II)Ljava/lang/CharSequence;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring_range", List.of(receiver, begin, end));
            return;
        }
        if (lowerJdkWrapperInstanceCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerOptionalInstanceCall(
            classes,
            classFile,
            method,
            instruction,
            methodRef,
            instructions,
            dispatches,
            materializedLambdaMethods,
            functionValueFlow,
            instantiatedTypes,
            stack,
            localDeclarations,
            pendingExceptionHandlerStacks,
            sourceLines
        )) {
            return;
        }
        if (lowerStringBuilderCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkHttpVirtualCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkThreadInstanceCall(
            classes,
            classFile,
            method,
            instruction,
            methodRef,
            instructions,
            stack,
            localDeclarations,
            pendingExceptionHandlerStacks,
            dispatches,
            sourceLines
        )) {
            return;
        }
        if (lowerSocketStreamCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkNetworkInstanceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkPathInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkTimeInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerAtomicBooleanInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerAtomicIntegerInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerAtomicLongInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerAtomicReferenceInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerDateTimeFormatterBuilderVirtualCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerThreadLocalInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkCollectionInstanceCall(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            materializedLambdaMethods,
            functionValueFlow,
            instantiatedTypes,
            methodRef,
            instructions,
            stack,
            localDeclarations
        )) {
            return;
        }
        if (isPlatformThrowableGetMessage(methodRef)) {
            final StackValue receiver = popObjectValue(classFile, method, instruction, stack);
            final IrExpression message = receiver.kind() == StackKind.CAUGHT_THROWABLE
                ? IrExpression.objectCall(
                    "javan_caught_throwable_message",
                    List.of(receiver.expression().orElseThrow())
                )
                : receiver.expression().orElseThrow();
            stack.add(StackValue.objectExpression(message));
            return;
        }
        if (isConcreteExactCallTarget(classes, methodRef.owner())) {
            lowerInstanceCall(classes, classFile, method, instruction, instructions, stack, localDeclarations);
            return;
        }
        final List<EntryPoint> targets = virtualTargets(classes, methodRef, instantiatedTypes);
        if (!targets.isEmpty()) {
            lowerDispatchCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                dispatches,
                methodRef,
                targets
            );
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    static boolean lowerJdkHttpStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/net/URI".equals(methodRef.owner())
            && "create".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/net/URI;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_uri_create", arguments)));
            return true;
        }
        if ("java/net/http/HttpClient".equals(methodRef.owner())
            && "newHttpClient".equals(methodRef.name())
            && "()Ljava/net/http/HttpClient;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_client_new", arguments)));
            return true;
        }
        if ("java/net/http/HttpRequest".equals(methodRef.owner())
            && "newBuilder".equals(methodRef.name())
            && "(Ljava/net/URI;)Ljava/net/http/HttpRequest$Builder;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_request_builder_new", arguments)));
            return true;
        }
        if ("java/net/http/HttpRequest$BodyPublishers".equals(methodRef.owner())
            && "ofString".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/net/http/HttpRequest$BodyPublisher;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_body_publisher_string", arguments)));
            return true;
        }
        if ("java/net/http/HttpRequest$BodyPublishers".equals(methodRef.owner())
            && "ofByteArray".equals(methodRef.name())
            && "([B)Ljava/net/http/HttpRequest$BodyPublisher;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_body_publisher_byte_array", arguments)));
            return true;
        }
        if ("java/net/http/HttpResponse$BodyHandlers".equals(methodRef.owner())
            && "ofString".equals(methodRef.name())
            && "()Ljava/net/http/HttpResponse$BodyHandler;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_body_handler_string", arguments)));
            return true;
        }
        if ("java/net/http/HttpResponse$BodyHandlers".equals(methodRef.owner())
            && "ofByteArray".equals(methodRef.name())
            && "()Ljava/net/http/HttpResponse$BodyHandler;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_body_handler_byte_array", arguments)));
            return true;
        }
        return false;
    }

    static boolean lowerJdkHttpVirtualCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/net/http/HttpClient".equals(methodRef.owner())
            && "send".equals(methodRef.name())
            && "(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            final List<IrExpression> callArguments = new ArrayList<>();
            callArguments.add(receiver);
            callArguments.addAll(arguments);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_client_send", callArguments)));
            return true;
        }
        return false;
    }

    static boolean lowerJdkHttpInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/net/http/HttpRequest$Builder".equals(methodRef.owner())) {
            if ("GET".equals(methodRef.name())
                && "()Ljava/net/http/HttpRequest$Builder;".equals(methodRef.descriptor())) {
                final IrExpression receiver = popObject(classFile, method, instruction, stack);
                pushObjectCall(instructions, stack, localDeclarations, "javan_http_request_builder_get", List.of(receiver));
                return true;
            }
            if ("header".equals(methodRef.name())
                && "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/http/HttpRequest$Builder;".equals(methodRef.descriptor())) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()), instruction);
                final IrExpression receiver = popObject(classFile, method, instruction, stack);
                final List<IrExpression> callArguments = new ArrayList<>();
                callArguments.add(receiver);
                callArguments.addAll(arguments);
                pushObjectCall(instructions, stack, localDeclarations, "javan_http_request_builder_header", callArguments);
                return true;
            }
            if ("POST".equals(methodRef.name())
                && "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;".equals(methodRef.descriptor())) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()), instruction);
                final IrExpression receiver = popObject(classFile, method, instruction, stack);
                final List<IrExpression> callArguments = new ArrayList<>();
                callArguments.add(receiver);
                callArguments.addAll(arguments);
                pushObjectCall(instructions, stack, localDeclarations, "javan_http_request_builder_post", callArguments);
                return true;
            }
            if ("PUT".equals(methodRef.name())
                && "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;".equals(methodRef.descriptor())) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()), instruction);
                final IrExpression receiver = popObject(classFile, method, instruction, stack);
                final List<IrExpression> callArguments = new ArrayList<>();
                callArguments.add(receiver);
                callArguments.addAll(arguments);
                pushObjectCall(instructions, stack, localDeclarations, "javan_http_request_builder_put", callArguments);
                return true;
            }
            if ("build".equals(methodRef.name())
                && "()Ljava/net/http/HttpRequest;".equals(methodRef.descriptor())) {
                final IrExpression receiver = popObject(classFile, method, instruction, stack);
                pushObjectCall(instructions, stack, localDeclarations, "javan_http_request_builder_build", List.of(receiver));
                return true;
            }
            return false;
        }
        if ("java/net/http/HttpResponse".equals(methodRef.owner())) {
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            if ("statusCode".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_http_response_status_code", List.of(receiver));
                return true;
            }
            if ("body".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_http_response_body", List.of(receiver));
                return true;
            }
            return false;
        }
        return false;
    }

    static boolean lowerSocketStreamCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/io/InputStream".equals(methodRef.owner())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final StackValue receiver = popObjectValue(classFile, method, instruction, stack);
            if (receiver.kind() == StackKind.SOCKET_INPUT_STREAM) {
                if ("read".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                    stack.add(StackValue.intExpression(IrExpression.intCall("javan_socket_input_stream_read", List.of(receiver.expression().orElseThrow()))));
                    return true;
                }
                if ("read".equals(methodRef.name()) && "([B)I".equals(methodRef.descriptor())) {
                    pushIntCall(instructions, stack, localDeclarations, "javan_socket_input_stream_read_bytes",
                        List.of(receiver.expression().orElseThrow(), arguments.getFirst()));
                    return true;
                }
                if ("read".equals(methodRef.name()) && "([BII)I".equals(methodRef.descriptor())) {
                    pushIntCall(instructions, stack, localDeclarations, "javan_socket_input_stream_read_bytes_range",
                        List.of(receiver.expression().orElseThrow(), arguments.get(0), arguments.get(1), arguments.get(2)));
                    return true;
                }
                if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
                    instructions.add(IrInstruction.callStaticVoid("javan_socket_input_stream_close", List.of(receiver.expression().orElseThrow())));
                    return true;
                }
            }
            if (receiver.kind() == StackKind.RESOURCE_INPUT_STREAM) {
                if ("read".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                    stack.add(StackValue.intExpression(IrExpression.intCall("javan_resource_input_stream_read", List.of(receiver.expression().orElseThrow()))));
                    return true;
                }
                if ("read".equals(methodRef.name()) && "([B)I".equals(methodRef.descriptor())) {
                    pushIntCall(instructions, stack, localDeclarations, "javan_resource_input_stream_read_bytes",
                        List.of(receiver.expression().orElseThrow(), arguments.getFirst()));
                    return true;
                }
                if ("read".equals(methodRef.name()) && "([BII)I".equals(methodRef.descriptor())) {
                    pushIntCall(instructions, stack, localDeclarations, "javan_resource_input_stream_read_bytes_range",
                        List.of(receiver.expression().orElseThrow(), arguments.get(0), arguments.get(1), arguments.get(2)));
                    return true;
                }
                if ("readAllBytes".equals(methodRef.name()) && "()[B".equals(methodRef.descriptor())) {
                    pushObjectCall(instructions, stack, localDeclarations, "javan_resource_input_stream_read_all_bytes",
                        List.of(receiver.expression().orElseThrow()));
                    return true;
                }
                if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
                    instructions.add(IrInstruction.callStaticVoid("javan_resource_input_stream_close", List.of(receiver.expression().orElseThrow())));
                    return true;
                }
            }
            if (receiver.kind() == StackKind.SOCKET_INPUT_STREAM || receiver.kind() == StackKind.RESOURCE_INPUT_STREAM) {
                return false;
            }
            throw unsupportedSpecializedStreamReceiver(classFile, method, methodRef);
        }
        if (!"java/io/OutputStream".equals(methodRef.owner())) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        final StackValue receiver = popObjectValue(classFile, method, instruction, stack);
        if (receiver.kind() != StackKind.SOCKET_OUTPUT_STREAM) {
            throw unsupportedSpecializedStreamReceiver(classFile, method, methodRef);
        }
        if ("write".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_socket_output_stream_write", List.of(receiver.expression().orElseThrow(), arguments.getFirst())));
            return true;
        }
        if ("write".equals(methodRef.name()) && "([B)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_socket_output_stream_write_bytes", List.of(receiver.expression().orElseThrow(), arguments.getFirst())));
            return true;
        }
        if ("write".equals(methodRef.name()) && "([BII)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_socket_output_stream_write_bytes_range",
                List.of(receiver.expression().orElseThrow(), arguments.get(0), arguments.get(1), arguments.get(2))));
            return true;
        }
        if ("flush".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_socket_output_stream_flush", List.of(receiver.expression().orElseThrow())));
            return true;
        }
        if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_socket_output_stream_close", List.of(receiver.expression().orElseThrow())));
            return true;
        }
        return false;
    }

    static DiagnosticException unsupportedSpecializedStreamReceiver(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN062",
            "supported stream call requires a specialized native stream receiver",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            "This release only supports " + methodRef.owner().replace('/', '.') + " calls on native stream receivers produced by the current runtime support slice.",
            "Use Class.getResourceAsStream(String) for packaged resources, java.net.Socket streams for socket I/O, or keep this code on the JVM until broader stream support lands."
        ));
    }

    static boolean lowerPrintStreamCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        if (!"java/io/PrintStream".equals(methodRef.owner())) {
            return false;
        }
        if ("print".equals(methodRef.name()) && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, instruction, stack);
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popPrintableObject(classFile, method, instruction, stack);
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "([C)V".equals(methodRef.descriptor())) {
            final IrExpression array = popObject(classFile, method, instruction, stack);
            emitPrintObject(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                IrExpression.objectCall(
                    "javan_string_from_chars",
                    List.of(array, IrExpression.intLiteral(0), IrExpression.intCall("javan_array_length", List.of(array)))
                )
            );
            return true;
        }
        if ("print".equals(methodRef.name()) && "(C)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_char", List.of(popInt(classFile, method, stack)));
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_bool", List.of(popInt(classFile, method, stack)));
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)));
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_long", List.of(popLong(classFile, method, stack)));
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "(F)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_float", List.of(popFloat(classFile, method, stack)));
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("print".equals(methodRef.name()) && "(D)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_double", List.of(popDouble(classFile, method, stack)));
            emitPrintObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, instruction, stack);
            emitPrintlnObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            emitPrintlnObject(classFile, method, instruction, instructions, stack, IrExpression.stringLiteral(""));
            return true;
        }
        if ("println".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popPrintableObject(classFile, method, instruction, stack);
            emitPrintlnObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "([C)V".equals(methodRef.descriptor())) {
            final IrExpression array = popObject(classFile, method, instruction, stack);
            emitPrintlnObject(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                IrExpression.objectCall(
                    "javan_string_from_chars",
                    List.of(array, IrExpression.intLiteral(0), IrExpression.intCall("javan_array_length", List.of(array)))
                )
            );
            return true;
        }
        if ("println".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popInt(classFile, method, instruction, stack);
            emitPrintlnInt(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popLong(classFile, method, instruction, stack);
            emitPrintlnLong(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(F)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popFloat(classFile, method, instruction, stack);
            emitPrintlnFloat(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(D)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popDouble(classFile, method, instruction, stack);
            emitPrintlnDouble(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popInt(classFile, method, instruction, stack);
            emitPrintlnBoolean(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        if ("println".equals(methodRef.name()) && "(C)V".equals(methodRef.descriptor())) {
            final IrExpression argument = IrExpression.objectCall("javan_string_value_of_char", List.of(popInt(classFile, method, stack)));
            emitPrintlnObject(classFile, method, instruction, instructions, stack, argument);
            return true;
        }
        return false;
    }

    static StackValue popPrintStream(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A PrintStream receiver was expected on the bytecode stack.");
        }
        final StackValue receiver = pop(stack);
        if (receiver.kind() == StackKind.OBJECT || receiver.kind() == StackKind.PRINT_STREAM || receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            return receiver;
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason("PrintStream receiver", receiver.kind()));
    }

    static void emitPrintObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printErrorObject(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printObject(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_print_object", List.of(receiver.expression().orElseThrow(), argument)));
    }

    static void emitPrintlnObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
        if (receiver.kind() == StackKind.ERROR_PRINT_STREAM) {
            instructions.add(IrInstruction.printlnErrorObject(argument));
            return;
        }
        if (receiver.kind() == StackKind.PRINT_STREAM) {
            instructions.add(IrInstruction.printlnObject(argument));
            return;
        }
        instructions.add(IrInstruction.callStaticVoid("javan_printstream_println_object", List.of(receiver.expression().orElseThrow(), argument)));
    }

    static void emitPrintlnInt(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
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

    static void emitPrintlnLong(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
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

    static void emitPrintlnFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
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

    static void emitPrintlnDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
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

    static void emitPrintlnBoolean(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrExpression argument
    ) {
        final StackValue receiver = popPrintStream(classFile, method, instruction, stack);
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

    static boolean lowerArrayClone(
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

    static Optional<String> arrayCloneSymbol(final String owner) {
        if ("[Z".equals(owner)) {
            return Optional.of("javan_arrays_copy_of_boolean");
        }
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

    static boolean lowerJavanProcessRunnerRun(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"javan/util/ProcessRunner".equals(methodRef.owner())
            || !"run".equals(methodRef.name())
            || !"(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;".equals(methodRef.descriptor())) {
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

    static boolean lowerJavanFiles2CreateDirectoriesIfPossible(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"javan/util/Files2".equals(methodRef.owner())
            || !"createDirectoriesIfPossible".equals(methodRef.name())
            || !"(Ljava/nio/file/Path;)Z".equals(methodRef.descriptor())) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        stack.add(StackValue.intExpression(IrExpression.intCall("javan_files_create_directories_if_possible", arguments)));
        return true;
    }

    static DiagnosticException unsupportedJavanProcessResult(
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

    static boolean lowerJdkWrapperInstanceCall(
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
        if ("java/lang/Integer".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_int",
                List.of(IrExpression.intCall("javan_integer_int_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Long".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_long",
                List.of(IrExpression.longCall("javan_long_long_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Float".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_float",
                List.of(IrExpression.floatCall("javan_float_float_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Double".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_double",
                List.of(IrExpression.doubleCall("javan_double_double_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Boolean".equals(methodRef.owner()) && "booleanValue".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_boolean_boolean_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Boolean".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_bool",
                List.of(IrExpression.intCall("javan_boolean_boolean_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Byte".equals(methodRef.owner()) && "byteValue".equals(methodRef.name()) && "()B".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_byte_byte_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Byte".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_int",
                List.of(IrExpression.intCall("javan_byte_byte_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Short".equals(methodRef.owner()) && "shortValue".equals(methodRef.name()) && "()S".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_short_short_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Short".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_int",
                List.of(IrExpression.intCall("javan_short_short_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Character".equals(methodRef.owner()) && "charValue".equals(methodRef.name()) && "()C".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_char_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Character".equals(methodRef.owner()) && "toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_char",
                List.of(IrExpression.intCall("javan_character_char_value", List.of(receiver)))
            )));
            return true;
        }
        if ("java/lang/Boolean".equals(methodRef.owner()) && "equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_boolean_equals", List.of(receiver, argument))));
            return true;
        }
        return false;
    }

    private static boolean isDirectConstableWrapperOwner(final String owner) {
        return "java/lang/Integer".equals(owner)
            || "java/lang/Long".equals(owner)
            || "java/lang/Float".equals(owner)
            || "java/lang/Double".equals(owner);
    }

    private static boolean isDirectConstableWrapperResolveDescriptor(final String owner, final String descriptor) {
        if ("(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;".equals(descriptor)) {
            return true;
        }
        return switch (owner) {
            case "java/lang/Integer" -> "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Integer;".equals(descriptor);
            case "java/lang/Long" -> "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Long;".equals(descriptor);
            case "java/lang/Float" -> "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Float;".equals(descriptor);
            case "java/lang/Double" -> "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Double;".equals(descriptor);
            default -> false;
        };
    }

    static void lowerInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final MethodRef rawMethodRef = instruction.methodRef().orElseThrow();
        final MethodRef normalizedMethodRef = JdkCallSupport.normalizeInheritedSupportedJdkCall(classes, rawMethodRef)
            .orElse(rawMethodRef);
        MethodRef methodRef = normalizedMethodRef;
        if (instruction.opcode() == 182 && isConcreteExactCallTarget(classes, normalizedMethodRef.owner())) {
            final Optional<EntryPoint> target = resolvedVirtualTarget(
                classes,
                normalizedMethodRef.owner(),
                normalizedMethodRef
            );
            if (target.isPresent()) {
                final EntryPoint entryPoint = target.orElseThrow();
                methodRef = new MethodRef(entryPoint.className(), entryPoint.methodName(), entryPoint.descriptor());
            }
        }
        if (isZeroArgNoopPlatformConstructor(methodRef)) {
            popObject(classFile, method, stack);
            return;
        }
        if (JdkCallSupport.isPlatformThrowableCauseConstructor(methodRef)) {
            popObjectValue(classFile, method, instruction, stack);
            final IrExpression message = popObject(classFile, method, instruction, stack);
            popObject(classFile, method, instruction, stack);
            updatePendingThrowableMessage(stack, message);
            return;
        }
        if (lowerObjectClone(classes, classFile, method, methodRef, stack)) {
            return;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = descriptor.parameterTypes().isEmpty()
            ? List.of()
            : popArguments(classFile, method, stack, descriptor);
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
        if (lowerThreadConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerScheduledThreadPoolExecutorConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerAtomicBooleanConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerAtomicIntegerConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerAtomicLongConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerAtomicReferenceConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerDateTimeFormatterBuilderConstructor(methodRef)) {
            return;
        }
        if (lowerThreadLocalConstructor(methodRef)) {
            return;
        }
        if (lowerStringConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerInetSocketAddressConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerSocketConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerStringBuilderConstructor(methodRef, instructions, stack, arguments, receiver)) {
            return;
        }
        if (lowerDateTimeFormatterBuilderInstanceCall(methodRef, stack, arguments, receiver)) {
            return;
        }
        if (lowerJdkCollectionConstructorCall(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (!classes.containsKey(methodRef.owner())) {
            throw unsupported(classFile, method, instruction);
        }
        final List<IrExpression> callArguments = new ArrayList<>(arguments);
        callArguments.addFirst(receiver);
        final String symbol = symbol(new EntryPoint(methodRef.owner(), methodRef.name(), methodRef.descriptor()));
        appendCallResult(instructions, stack, localDeclarations, descriptor.returnType(), symbol, callArguments);
    }

    private static boolean isZeroArgNoopPlatformConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name()) || !"()V".equals(methodRef.descriptor())) {
            return false;
        }
        return "java/lang/Object".equals(methodRef.owner())
            || "java/lang/Record".equals(methodRef.owner())
            || "java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy".equals(methodRef.owner());
    }

    static void lowerStaticCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (lowerEnumValues(classes, classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkStaticIntrinsic(
            classes,
            classFile,
            method,
            instruction,
            methodRef,
            instructions,
            stack,
            localDeclarations,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            pendingExceptionHandlerStacks,
            sourceLines
        )) {
            return;
        }
        if (lowerJavanFiles2CreateDirectoriesIfPossible(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJdkCollectionStaticCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileStaticCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkHttpStaticCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerExecutorsStaticCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerOptionalStaticCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (!classes.containsKey(methodRef.owner())) {
            throw unsupported(classFile, method, instruction);
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = popArguments(classFile, method, stack, descriptor, instruction);
        final String owner = ClassInitializationGraph.staticMethodOwner(classes, methodRef).orElse(methodRef.owner());
        final String symbol = symbol(new EntryPoint(owner, methodRef.name(), methodRef.descriptor()));
        if (VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(classes, methodRef)) {
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        if (VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(classes, methodRef)) {
            stack.add(StackValue.virtualThreadFactory(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        appendCallResult(instructions, stack, localDeclarations, descriptor.returnType(), symbol, arguments);
    }

    private static boolean lowerExecutorsStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(methodRef)) {
            stack.add(StackValue.virtualThreadExecutor(IrExpression.objectCall("javan_virtual_thread_executor_new", List.of())));
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(methodRef)) {
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadExecutor(IrExpression.objectCall(
                "javan_virtual_thread_executor_from_factory",
                List.of(factory.expression().orElse(IrExpression.objectNull()))
            )));
            return true;
        }
        return false;
    }

    static void lowerEnumOrdinal(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        final IrExpression receiver = popObject(classFile, method, stack);
        if (receiver.kind() == IrExpression.Kind.STATIC_FIELD_OBJECT) {
            final Optional<Integer> ordinal = enumOrdinalForStaticField(receiver.value(), methodRef.owner(), classes);
            if (ordinal.isPresent()) {
                stack.add(StackValue.intExpression(IrExpression.intLiteral(ordinal.orElseThrow().intValue())));
                return;
            }
        }
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

    static boolean lowerEnumValues(
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
        instructions.add(IrInstruction.assignObject(
            localName,
            IrExpression.objectArrayAllocation(
                IrExpression.intLiteral(constants.size()),
                "[L" + binaryClassName(methodRef.owner()) + ";"
            )
        ));
        for (int index = 0; index < constants.size(); index++) {
            instructions.add(IrInstruction.assignArrayObject(
                local,
                IrExpression.intLiteral(index),
                IrExpression.objectStaticField(methodRef.owner(), constants.get(index))
            ));
        }
        stack.add(StackValue.objectExpression(local));
        return true;
    }

    private static IrExpression enumConstantExpression(final Map<String, ClassFile> classes, final FieldRef fieldRef) {
        final ClassFile owner = classes.get(fieldRef.owner());
        if (owner != null && owner.isEnum()) {
            return IrExpression.objectStaticField(fieldRef.owner(), fieldRef.name());
        }
        return IrExpression.stringLiteral(fieldRef.name());
    }

    private static Optional<Integer> enumOrdinalForStaticField(
        final String ownerField,
        final String enumOwner,
        final Map<String, ClassFile> classes
    ) {
        final int separator = ownerField.indexOf('#');
        if (separator < 1 || separator == ownerField.length() - 1) {
            return Optional.empty();
        }
        final String owner = ownerField.substring(0, separator);
        if (!enumOwner.equals(owner)) {
            return Optional.empty();
        }
        final ClassFile enumClass = classes.get(owner);
        if (enumClass == null || !enumClass.isEnum()) {
            return Optional.empty();
        }
        return enumOrdinal(enumClass, ownerField.substring(separator + 1));
    }

    static boolean lowerJdkStaticIntrinsic(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        if ("java/lang/Math".equals(methodRef.owner())) {
            return lowerMathIntrinsic(
                classFile,
                method,
                instruction,
                methodRef,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines
            );
        }
        if ("java/lang/System".equals(methodRef.owner())) {
            return lowerSystemIntrinsic(classFile, method, methodRef, instructions, stack);
        }
        if ("java/util/Objects".equals(methodRef.owner())) {
            return lowerObjectsIntrinsic(
                classes,
                classFile,
                method,
                instruction,
                methodRef,
                instructions,
                stack,
                localDeclarations,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes
            );
        }
        if ("java/util/Arrays".equals(methodRef.owner())) {
            return lowerArraysIntrinsic(
                classFile,
                method,
                instruction,
                methodRef,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines
            );
        }
        if (lowerDecimalParse(
            classFile,
            method,
            instruction,
            methodRef,
            instructions,
            stack,
            localDeclarations,
            pendingExceptionHandlerStacks,
            sourceLines
        )) {
            return true;
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
        if (lowerDoubleParse(
            classFile,
            method,
            instruction,
            methodRef,
            instructions,
            stack,
            localDeclarations,
            pendingExceptionHandlerStacks,
            sourceLines
        )) {
            return true;
        }
        if ("java/lang/Double".equals(methodRef.owner())) {
            return lowerDoubleIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Boolean".equals(methodRef.owner())) {
            return lowerBooleanIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Byte".equals(methodRef.owner())) {
            return lowerByteIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Short".equals(methodRef.owner())) {
            return lowerShortIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Character".equals(methodRef.owner())) {
            return lowerCharacterIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(Ljava/lang/Object;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_printable_object_string",
                List.of(popPrintableObject(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "([C)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression array = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_from_chars",
                List.of(array, IrExpression.intLiteral(0), IrExpression.intCall("javan_array_length", List.of(array)))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "([CII)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression count = popInt(classFile, method, stack);
            final IrExpression offset = popInt(classFile, method, stack);
            final IrExpression array = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_from_chars",
                List.of(array, offset, count)
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "copyValueOf".equals(methodRef.name())
            && "([C)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression array = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_from_chars",
                List.of(array, IrExpression.intLiteral(0), IrExpression.intCall("javan_array_length", List.of(array)))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "copyValueOf".equals(methodRef.name())
            && "([CII)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression count = popInt(classFile, method, stack);
            final IrExpression offset = popInt(classFile, method, stack);
            final IrExpression array = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_from_chars",
                List.of(array, offset, count)
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_int",
                List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(J)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_long",
                List.of(popLong(classFile, method, stack))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(F)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_float",
                List.of(popFloat(classFile, method, stack))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(D)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_double",
                List.of(popDouble(classFile, method, stack))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(Z)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_bool",
                List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(C)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_string_value_of_char",
                List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        if ("java/time/Duration".equals(methodRef.owner())) {
            return lowerDurationIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/Thread".equals(methodRef.owner())) {
            return lowerThreadStaticCall(
                classFile,
                method,
                instruction,
                methodRef,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines
            );
        }
        if ("java/util/concurrent/locks/LockSupport".equals(methodRef.owner())) {
            return lowerLockSupportStaticCall(classFile, method, methodRef, instructions, stack);
        }
        if ("java/net/InetAddress".equals(methodRef.owner())) {
            return lowerInetAddressIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/lang/ClassLoader".equals(methodRef.owner())) {
            return lowerClassLoaderStaticCall(classFile, method, methodRef, stack);
        }
        return false;
    }

    static boolean lowerClassLoaderStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("getSystemClassLoader".equals(methodRef.name())
            && "()Ljava/lang/ClassLoader;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_class_loader_system", List.of())));
            return true;
        }
        if ("getSystemResourceAsStream".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/io/InputStream;".equals(methodRef.descriptor())) {
            stack.add(StackValue.resourceInputStream(IrExpression.objectCall(
                "javan_loader_resource_as_stream",
                List.of(popObject(classFile, method, stack))
            )));
            return true;
        }
        return false;
    }

    static boolean lowerIntegerIntrinsic(
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

    static boolean lowerDecimalParse(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        final boolean parseInt = "java/lang/Integer".equals(methodRef.owner())
            && "parseInt".equals(methodRef.name())
            && "(Ljava/lang/String;)I".equals(methodRef.descriptor());
        final boolean parseLong = "java/lang/Long".equals(methodRef.owner())
            && "parseLong".equals(methodRef.name())
            && "(Ljava/lang/String;)J".equals(methodRef.descriptor());
        if (!parseInt && !parseLong) {
            return false;
        }
        final long negativeLimit = parseInt ? Integer.MIN_VALUE : Long.MIN_VALUE;
        final long positiveLimit = parseInt ? -Integer.MAX_VALUE : -Long.MAX_VALUE;
        final int valueLocalIndex = localDeclarations.size();
        final String valueLocalName = "object" + valueLocalIndex;
        localDeclarations.put(
            Integer.MIN_VALUE + valueLocalIndex,
            new IrLocal(IrType.OBJECT, valueLocalName)
        );
        instructions.add(IrInstruction.assignObject(
            valueLocalName,
            popObject(classFile, method, stack)
        ));

        final int statusLocalIndex = localDeclarations.size();
        final String statusLocalName = "int" + statusLocalIndex;
        localDeclarations.put(
            Integer.MIN_VALUE + statusLocalIndex,
            new IrLocal(IrType.INT, statusLocalName)
        );
        instructions.add(IrInstruction.assignInt(
            statusLocalName,
            IrExpression.intCall(
                "javan_decimal_parse_status",
                List.of(
                    IrExpression.objectLocal(valueLocalName),
                    IrExpression.longLiteral(negativeLimit),
                    IrExpression.longLiteral(positiveLimit)
                )
            )
        ));

        final String successLabel = "label_decimal_parse_success_"
            + instruction.offset() + "_" + statusLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(statusLocalName),
                IrExpression.intLiteral(0)
            )
        ));
        final List<StackValue> successStack = List.copyOf(stack);
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/NumberFormatException",
            IrExpression.objectCall(
                "javan_decimal_parse_message",
                List.of(
                    IrExpression.objectLocal(valueLocalName),
                    IrExpression.intLocal(statusLocalName)
                )
            )
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        final IrExpression parsedValue = IrExpression.longCall(
            "javan_decimal_parse_value",
            List.of(
                IrExpression.objectLocal(valueLocalName),
                IrExpression.longLiteral(negativeLimit),
                IrExpression.longLiteral(positiveLimit)
            )
        );
        if (parseInt) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_l2i",
                List.of(parsedValue)
            )));
        } else {
            stack.add(StackValue.longExpression(parsedValue));
        }
        return true;
    }

    static boolean lowerLongIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("compare".equals(methodRef.name()) && "(JJ)I".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_lcmp", List.of(left, right))));
            return true;
        }
        if ("compareUnsigned".equals(methodRef.name()) && "(JJ)I".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_long_compare_unsigned",
                List.of(left, right)
            )));
            return true;
        }
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

    static boolean lowerFloatIntrinsic(
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
        if ("floatToRawIntBits".equals(methodRef.name()) && "(F)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_float_to_raw_int_bits",
                List.of(popFloat(classFile, method, stack))
            )));
            return true;
        }
        if ("isFinite".equals(methodRef.name()) && "(F)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_float_is_finite",
                List.of(popFloat(classFile, method, stack))
            )));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(F)Ljava/lang/Float;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_float_value_of", List.of(popFloat(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    static boolean lowerDoubleIntrinsic(
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
        if ("isFinite".equals(methodRef.name()) && "(D)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_double_is_finite",
                List.of(popDouble(classFile, method, stack))
            )));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(D)Ljava/lang/Double;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_double_value_of", List.of(popDouble(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    static boolean lowerDoubleParse(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        if (!"java/lang/Double".equals(methodRef.owner())
            || !"parseDouble".equals(methodRef.name())
            || !"(Ljava/lang/String;)D".equals(methodRef.descriptor())) {
            return false;
        }
        final int valueLocalIndex = localDeclarations.size();
        final String valueLocalName = "object" + valueLocalIndex;
        localDeclarations.put(
            Integer.MIN_VALUE + valueLocalIndex,
            new IrLocal(IrType.OBJECT, valueLocalName)
        );
        instructions.add(IrInstruction.assignObject(
            valueLocalName,
            popObject(classFile, method, stack)
        ));

        final int statusLocalIndex = localDeclarations.size();
        final String statusLocalName = "int" + statusLocalIndex;
        localDeclarations.put(
            Integer.MIN_VALUE + statusLocalIndex,
            new IrLocal(IrType.INT, statusLocalName)
        );
        instructions.add(IrInstruction.assignInt(
            statusLocalName,
            IrExpression.intCall(
                "javan_double_parse_status",
                List.of(IrExpression.objectLocal(valueLocalName))
            )
        ));

        final String labelSuffix = instruction.offset() + "_" + statusLocalIndex;
        final String successLabel = "label_double_parse_success_" + labelSuffix;
        final String malformedLabel = "label_double_parse_malformed_" + labelSuffix;
        final IrExpression status = IrExpression.intLocal(statusLocalName);
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison("==", status, IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.branchIf(
            malformedLabel,
            IrExpression.intComparison("!=", status, IrExpression.intLiteral(1))
        ));
        final List<StackValue> successStack = List.copyOf(stack);
        final IrExpression message = IrExpression.objectCall(
            "javan_double_parse_message",
            List.of(IrExpression.objectLocal(valueLocalName), status)
        );
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/NullPointerException",
            message
        );
        instructions.add(IrInstruction.label(malformedLabel));
        stack.addAll(successStack);
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/NumberFormatException",
            message
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.doubleExpression(IrExpression.doubleCall(
            "javan_double_parse_value",
            List.of(IrExpression.objectLocal(valueLocalName))
        )));
        return true;
    }

    static boolean lowerBooleanIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("parseBoolean".equals(methodRef.name()) && "(Ljava/lang/String;)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_boolean_parse",
                List.of(popObject(classFile, method, stack))
            )));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "(Z)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_bool", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(Z)Ljava/lang/Boolean;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_boolean_value_of", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_boolean_equals", List.of(receiver, argument))));
            return true;
        }
        return false;
    }

    static boolean lowerByteIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(B)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(B)Ljava/lang/Byte;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_byte_value_of", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    static boolean lowerShortIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(S)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_int", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(S)Ljava/lang/Short;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_short_value_of", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    static boolean lowerCharacterIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(C)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_char", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(C)Ljava/lang/Character;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_character_value_of", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isWhitespace".equals(methodRef.name()) && "(C)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_character_is_whitespace",
                List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        return false;
    }

    static boolean lowerDurationIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("ofMillis".equals(methodRef.name()) && "(J)Ljava/time/Duration;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_duration_of_millis", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        if ("ofSeconds".equals(methodRef.name()) && "(J)Ljava/time/Duration;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_duration_of_seconds", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        return false;
    }

    static boolean lowerThreadStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        if ("ofVirtual".equals(methodRef.name())
            && "()Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor())) {
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall("javan_virtual_thread_builder_new", List.of())));
            return true;
        }
        if ("startVirtualThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor())) {
            final IrExpression runnable = popObject(classFile, method, stack);
            startVirtualThread(classFile, method, instructions, stack, localDeclarations, runnable);
            return true;
        }
        if ("currentThread".equals(methodRef.name()) && "()Ljava/lang/Thread;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_thread_current", List.of())));
            return true;
        }
        if ("yield".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_yield", List.of()));
            return true;
        }
        if ("onSpinWait".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_on_spin_wait", List.of()));
            return true;
        }
        if ("sleep".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                true,
                IrExpression.stringLiteral("sleep interrupted"),
                "javan_thread_sleep_millis_interruptible",
                List.of(popLong(classFile, method, stack))
            );
            return true;
        }
        if ("sleep".equals(methodRef.name()) && "(JI)V".equals(methodRef.descriptor())) {
            final IrExpression nanos = popInt(classFile, method, stack);
            final IrExpression millis = popLong(classFile, method, stack);
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                true,
                IrExpression.stringLiteral("sleep interrupted"),
                "javan_thread_sleep_millis_nanos_interruptible",
                List.of(millis, nanos)
            );
            return true;
        }
        if ("sleep".equals(methodRef.name()) && "(Ljava/time/Duration;)V".equals(methodRef.descriptor())) {
            final IrExpression duration = popObjectForJdkCall(classFile, method, instruction, stack);
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                true,
                IrExpression.stringLiteral("sleep interrupted"),
                "javan_thread_sleep_millis_interruptible",
                List.of(IrExpression.longCall("javan_duration_to_millis", List.of(duration)))
            );
            return true;
        }
        if ("interrupted".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_thread_interrupted", List.of())));
            return true;
        }
        return false;
    }

    static boolean lowerLockSupportStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        if ("park".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_park", List.of()));
            return true;
        }
        if ("parkNanos".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_thread_park_nanos",
                List.of(popLong(classFile, method, stack))
            ));
            return true;
        }
        if ("parkUntil".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_thread_park_until",
                List.of(popLong(classFile, method, stack))
            ));
            return true;
        }
        if ("unpark".equals(methodRef.name()) && "(Ljava/lang/Thread;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_thread_unpark",
                List.of(popObject(classFile, method, stack))
            ));
            return true;
        }
        return false;
    }

    static boolean lowerInetAddressIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("getByName".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_inet_address_get_by_name",
                List.of(popObject(classFile, method, stack))
            )));
            return true;
        }
        if ("getAllByName".equals(methodRef.name())
            && "(Ljava/lang/String;)[Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_inet_address_get_all_by_name",
                List.of(popObject(classFile, method, stack))
            )));
            return true;
        }
        if ("getByAddress".equals(methodRef.name())
            && "([B)Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_inet_address_get_by_address",
                List.of(popObject(classFile, method, stack))
            )));
            return true;
        }
        if ("getByAddress".equals(methodRef.name())
            && "(Ljava/lang/String;[B)Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            final IrExpression bytes = popObject(classFile, method, stack);
            final IrExpression host = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_inet_address_get_by_address_named",
                List.of(host, bytes)
            )));
            return true;
        }
        if ("getLoopbackAddress".equals(methodRef.name())
            && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_inet_address_loopback", List.of())));
            return true;
        }
        return false;
    }

    static boolean lowerSystemIntrinsic(
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

    static void pushIntCall(
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

    static void pushObjectCall(
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

    static void routePendingPlatformException(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final String throwableType,
        final IrExpression message
    ) {
        final StackValue thrownValue = StackValue.platformThrowable(
            throwableType,
            message
        );
        final Optional<Integer> handler = BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile,
            method,
            instruction,
            thrownValue,
            instruction.offset()
        );
        if (handler.isPresent()) {
            final int handlerOffset = handler.orElseThrow();
            instructions.add(IrInstruction.setPending(
                throwableType,
                message,
                BytecodeToIRControlFlowSupport.sourceLocation(classFile, method, instruction, sourceLines)
            ));
            BytecodeToIRControlFlowSupport.registerPendingHandlerStack(
                pendingExceptionHandlerStacks,
                handlerOffset
            );
            instructions.add(IrInstruction.jump(label(handlerOffset)));
            BytecodeToIRControlFlowSupport.clearStack(stack);
            return;
        }
        instructions.add(IrInstruction.throwPending(
            throwableType,
            message,
            BytecodeToIRControlFlowSupport.sourceLocation(classFile, method, instruction, sourceLines)
        ));
        BytecodeToIRControlFlowSupport.clearStack(stack);
    }

    static boolean lowerJdkFileStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/nio/file/Paths".equals(methodRef.owner())
            && "get".equals(methodRef.name())
            && "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_of", arguments)));
            return true;
        }
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
        if ("getLastModifiedTime".equals(methodRef.name())
            && "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Ljava/nio/file/attribute/FileTime;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_get_last_modified_time", arguments);
            return true;
        }
        if ("newDirectoryStream".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Ljava/nio/file/DirectoryStream;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_files_new_directory_stream", arguments);
            return true;
        }
        return false;
    }

    static boolean lowerJdkTimeInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/time/Duration".equals(methodRef.owner())
            && "toMillis".equals(methodRef.name())
            && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                "javan_duration_to_millis",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        return false;
    }
}
