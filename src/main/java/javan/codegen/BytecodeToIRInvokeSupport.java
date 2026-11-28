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
        final boolean guardedObjectArguments = JdkCallSupport.isCollectionObjectSearch(target)
            || JdkCallSupport.isCollectionObjectAddition(target) || JdkCallSupport.isListEndpointAdd(target)
            || JdkCallSupport.isMapObjectGetOrPut(target)
            || JdkCallSupport.isMapMembershipOrDefaultQuery(target) || JdkCallSupport.isMapPutIfAbsent(target)
            || JdkCallSupport.isMapRemove(target) || JdkCallSupport.isMapReplace(target) || JdkCallSupport.isMapPutAll(target);
        final boolean guardedCollectionOrMapReceiver = guardedObjectArguments || JdkCallSupport.isCollectionSizeQuery(target)
            || JdkCallSupport.isCollectionClear(target) || JdkCallSupport.isMapClear(target)
            || JdkCallSupport.isConcurrentKeyViewReceiver(target) || JdkCallSupport.isBoxedFloatingClassification(target)
            || JdkCallSupport.isBoxedPrimitiveValueAccess(target);
        if (!guardedCollectionOrMapReceiver && !guardedOptionalReceiver(target)
            && !(classes.containsKey(target.owner())
                && classes.get(target.owner()).application()
                && !"<init>".equals(target.name()))) {
            return;
        }
        final IrExpression nullMessage = IrExpression.stringLiteral(
            "Cannot invoke " + target.owner() + "." + target.name() + " on null"
        );
        if (!guardedCollectionOrMapReceiver && BytecodeToIRControlFlowSupport.exceptionHandler(
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
        final IrExpression receiverExpression = receiver.expression().orElseThrow();
        final IrExpression materialized;
        if (guardedCollectionOrMapReceiver && (receiverExpression.kind() == IrExpression.Kind.LOCAL
            || receiverExpression.kind() == IrExpression.Kind.OBJECT_NULL
            || receiverExpression.kind() == IrExpression.Kind.STRING_LITERAL)) {
            materialized = receiverExpression;
        } else {
            final String receiverLocal = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, receiverLocal));
            instructions.add(IrInstruction.assignObject(receiverLocal, receiverExpression));
            materialized = IrExpression.objectLocal(receiverLocal);
            stack.set(receiverIndex, new StackValue(receiver.kind(), receiver.throwable(),
                Optional.of(materialized), receiver.dynamicLambda()));
        }
        if (guardedObjectArguments) {
            for (int argumentIndex = receiverIndex + 1; argumentIndex < stack.size(); argumentIndex++) {
                final StackValue argument = stack.get(argumentIndex);
                final boolean streamArgument = argument.kind() == StackKind.PRINT_STREAM
                    || argument.kind() == StackKind.ERROR_PRINT_STREAM;
                if (argument.expression().isEmpty() && !streamArgument) {
                    throw unsupported(classFile, method, instruction);
                }
                final IrExpression argumentExpression = stackValueExpression(argument);
                if (argumentExpression.kind() != IrExpression.Kind.LOCAL
                    && argumentExpression.kind() != IrExpression.Kind.OBJECT_NULL
                    && argumentExpression.kind() != IrExpression.Kind.STRING_LITERAL) {
                    final String argumentLocal = "object" + localDeclarations.size();
                    localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, argumentLocal));
                    instructions.add(IrInstruction.assignObject(argumentLocal, argumentExpression));
                    stack.set(argumentIndex, streamArgument
                        ? StackValue.objectExpression(IrExpression.objectLocal(argumentLocal))
                        : new StackValue(argument.kind(), argument.throwable(),
                            Optional.of(IrExpression.objectLocal(argumentLocal)), argument.dynamicLambda()));
                }
            }
        }
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

    private static boolean guardedOptionalReceiver(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && (JdkCallSupport.supportedCall(target).isPresent()
                || JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(target));
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
            case OBJECT -> {
                final IrExpression value = IrExpression.objectStaticField(owner, fieldRef.name());
                final Optional<String> throwableType = BytecodeToIR.throwableTypeFromDescriptor(classes, fieldRef.descriptor());
                stack.add(throwableType.isPresent()
                    ? throwableValue(classes, owner, throwableType.orElseThrow(), value)
                    : StackValue.objectExpression(value));
            }
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
        final Map<String, ClassFile> classes,
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
            case OBJECT -> {
                final IrExpression value = IrExpression.objectField(fieldRef.owner(), fieldRef.name(), receiver);
                final Optional<String> throwableType = BytecodeToIR.throwableTypeFromDescriptor(classes, fieldRef.descriptor());
                stack.add(throwableType.isPresent()
                    ? throwableValue(classes, fieldRef.owner(), throwableType.orElseThrow(), value)
                    : StackValue.objectExpression(value));
            }
            case VOID -> throw new IllegalStateException("void instance field is invalid");
        }
    }

    private static StackValue throwableValue(
        final Map<String, ClassFile> classes,
        final String owner,
        final String throwableType,
        final IrExpression value
    ) {
        if (classes.containsKey(throwableType)) {
            return StackValue.applicationThrowable(throwableType, value);
        }
        return classes.containsKey(owner)
            ? StackValue.objectExpression(value)
            : StackValue.platformThrowable(throwableType, value);
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
        if (lowerResourceAsStream(classFile, method, instruction, methodRef, instructions, stack,
            localDeclarations, pendingExceptionHandlerStacks, sourceLines)) {
            return;
        }
        if (lowerPrintStreamCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations, sourceLines)) {
            return;
        }
        if (lowerMethodMetadataCall(
            classes,
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
        if ("java/lang/String".equals(methodRef.owner()) && "getChars".equals(methodRef.name()) && "(II[CI)V".equals(methodRef.descriptor())) {
            final IrExpression destinationOffset = popInt(classFile, method, stack);
            final IrExpression destination = popObject(classFile, method, stack);
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_string_get_chars",
                List.of(receiver, begin, end, destination, destinationOffset)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "toCharArray".equals(methodRef.name()) && "()[C".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_to_char_array", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "length".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_length_java", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "hashCode".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_hash_code_java", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_is_empty", List.of(popObject(classFile, method, stack)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "isBlank".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_is_blank_java", List.of(popObject(classFile, method, stack)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "charAt".equals(methodRef.name()) && "(I)C".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_char_at_java", List.of(receiver, index))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && ("codePointAt".equals(methodRef.name()) || "codePointBefore".equals(methodRef.name()))
            && "(I)I".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String symbol = "codePointAt".equals(methodRef.name())
                ? "javan_string_code_point_at" : "javan_string_code_point_before";
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(receiver, index))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && ("codePointCount".equals(methodRef.name()) || "offsetByCodePoints".equals(methodRef.name()))
            && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression second = popInt(classFile, method, stack);
            final IrExpression first = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String symbol = "codePointCount".equals(methodRef.name())
                ? "javan_string_code_point_count" : "javan_string_offset_by_code_points";
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(receiver, first, second))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "indexOf".equals(methodRef.name())
            && ("(III)I".equals(methodRef.descriptor()) || "(Ljava/lang/String;II)I".equals(methodRef.descriptor()))) {
            final IrExpression endIndex = popInt(classFile, method, stack);
            final IrExpression beginIndex = popInt(classFile, method, stack);
            final boolean codePoint = "(III)I".equals(methodRef.descriptor());
            final IrExpression needle = codePoint ? popInt(classFile, method, stack) : popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String symbol = codePoint ? "javan_string_index_of_code_point_range" : "javan_string_index_of_string_range";
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(receiver, needle, beginIndex, endIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && ("indexOf".equals(methodRef.name()) || "lastIndexOf".equals(methodRef.name()))
            && ("(I)I".equals(methodRef.descriptor()) || "(II)I".equals(methodRef.descriptor()))) {
            final boolean backwards = "lastIndexOf".equals(methodRef.name());
            final IrExpression fromIndex = "(II)I".equals(methodRef.descriptor())
                ? popInt(classFile, method, stack) : IrExpression.intLiteral(backwards ? Integer.MAX_VALUE : 0);
            final IrExpression ch = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_search_code_point_java",
                List.of(receiver, ch, fromIndex, IrExpression.intLiteral(backwards ? 1 : 0)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && ("indexOf".equals(methodRef.name()) || "lastIndexOf".equals(methodRef.name()))
            && ("(Ljava/lang/String;)I".equals(methodRef.descriptor())
                || "(Ljava/lang/String;I)I".equals(methodRef.descriptor()))) {
            final boolean backwards = "lastIndexOf".equals(methodRef.name());
            final IrExpression fromIndex = "(Ljava/lang/String;I)I".equals(methodRef.descriptor())
                ? popInt(classFile, method, stack) : IrExpression.intLiteral(backwards ? Integer.MAX_VALUE : 0);
            final IrExpression needle = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_search_string_java",
                List.of(receiver, needle, fromIndex, IrExpression.intLiteral(backwards ? 1 : 0)))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_equals_java", List.of(receiver, argument))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "contains".equals(methodRef.name())
            && "(Ljava/lang/CharSequence;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final MethodRef toString = new MethodRef("java/lang/CharSequence", "toString", "()Ljava/lang/String;");
            final List<EntryPoint> targets = interfaceTargets(classes, toString, instantiatedTypes);
            if (targets.isEmpty()) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_contains_java", List.of(receiver, argument))));
                return;
            }
            IrExpression receiverValue = receiver;
            if (receiver.kind() != IrExpression.Kind.LOCAL && receiver.kind() != IrExpression.Kind.STRING_LITERAL
                && receiver.kind() != IrExpression.Kind.OBJECT_NULL) {
                final String local = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(local, receiver));
                receiverValue = IrExpression.objectLocal(local);
            }
            IrExpression argumentValue = argument;
            if (argument.kind() != IrExpression.Kind.LOCAL && argument.kind() != IrExpression.Kind.STRING_LITERAL
                && argument.kind() != IrExpression.Kind.OBJECT_NULL) {
                final String local = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(local, argument));
                argumentValue = IrExpression.objectLocal(local);
            }
            final String convertedLocal = newObjectLocal(localDeclarations);
            final String resultLocal = newIntLocal(localDeclarations);
            final String builtinLabel = "label_contains_builtin_" + instruction.offset() + "_" + resultLocal;
            final String doneLabel = "label_contains_done_" + instruction.offset() + "_" + resultLocal;
            final String dispatchSymbol = dispatchSymbol(toString);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(toString.descriptor()), targets));
            instructions.add(IrInstruction.branchIf(builtinLabel, IrExpression.intCall("javan_string_contains_use_builtin",
                List.of(receiverValue, argumentValue))));
            instructions.add(IrInstruction.assignObject(convertedLocal,
                IrExpression.objectCall(dispatchSymbol, List.of(argumentValue))));
            instructions.add(IrInstruction.branchIf(doneLabel, IrExpression.intCall("javan_pending_has", List.of())));
            instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall("javan_string_contains_java",
                List.of(receiverValue, IrExpression.objectLocal(convertedLocal)))));
            instructions.add(IrInstruction.jump(doneLabel));
            instructions.add(IrInstruction.label(builtinLabel));
            instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall("javan_string_contains_java",
                List.of(receiverValue, argumentValue))));
            instructions.add(IrInstruction.label(doneLabel));
            stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "startsWith".equals(methodRef.name())
            && ("(Ljava/lang/String;)Z".equals(methodRef.descriptor())
                || "(Ljava/lang/String;I)Z".equals(methodRef.descriptor()))) {
            final IrExpression fromIndex = "(Ljava/lang/String;I)Z".equals(methodRef.descriptor())
                ? popInt(classFile, method, stack) : IrExpression.intLiteral(0);
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_starts_with_java",
                List.of(receiver, argument, fromIndex))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "endsWith".equals(methodRef.name())
            && "(Ljava/lang/String;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_ends_with_java", List.of(receiver, argument))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "regionMatches".equals(methodRef.name())
            && ("(ILjava/lang/String;II)Z".equals(methodRef.descriptor())
                || "(ZILjava/lang/String;II)Z".equals(methodRef.descriptor()))) {
            final IrExpression length = popInt(classFile, method, stack);
            final IrExpression otherOffset = popInt(classFile, method, stack);
            final IrExpression other = popObject(classFile, method, stack);
            final IrExpression offset = popInt(classFile, method, stack);
            final IrExpression ignoreCase = "(ZILjava/lang/String;II)Z".equals(methodRef.descriptor())
                ? popInt(classFile, method, stack) : IrExpression.intLiteral(0);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushIntCall(instructions, stack, localDeclarations, "javan_string_region_matches_java",
                List.of(receiver, ignoreCase, offset, other, otherOffset, length));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "replace".equals(methodRef.name())
            && "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression replacement = popObject(classFile, method, stack);
            final IrExpression target = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String receiverLocal = newObjectLocal(localDeclarations);
            final String targetLocal = newObjectLocal(localDeclarations);
            final String replacementLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
            instructions.add(IrInstruction.assignObject(targetLocal, target));
            instructions.add(IrInstruction.assignObject(replacementLocal, replacement));
            final String resultLocal = newObjectLocal(localDeclarations);
            final String doneLabel = "label_replace_done_" + instruction.offset() + "_" + resultLocal;
            instructions.add(IrInstruction.branchIf(doneLabel, IrExpression.intCall("javan_string_replace_null_receiver",
                List.of(IrExpression.objectLocal(receiverLocal)))));
            final MethodRef toString = new MethodRef("java/lang/CharSequence", "toString", "()Ljava/lang/String;");
            final List<EntryPoint> targets = interfaceTargets(classes, toString, instantiatedTypes);
            final String dispatchSymbol = dispatchSymbol(toString);
            if (!targets.isEmpty()) {
                dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(toString.descriptor()), targets));
            }
            final List<IrExpression> converted = new ArrayList<>();
            for (final String argumentLocal : List.of(targetLocal, replacementLocal)) {
                final String convertedLocal = newObjectLocal(localDeclarations);
                if (!targets.isEmpty()) {
                    final String builtinLabel = "label_replace_builtin_" + instruction.offset() + "_" + convertedLocal;
                    final String nextLabel = "label_replace_next_" + instruction.offset() + "_" + convertedLocal;
                    instructions.add(IrInstruction.branchIf(builtinLabel, IrExpression.intCall("javan_string_contains_use_builtin",
                        List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(argumentLocal)))));
                    instructions.add(IrInstruction.assignObject(convertedLocal,
                        IrExpression.objectCall(dispatchSymbol, List.of(IrExpression.objectLocal(argumentLocal)))));
                    instructions.add(IrInstruction.jump(nextLabel));
                    instructions.add(IrInstruction.label(builtinLabel));
                    instructions.add(IrInstruction.assignObject(convertedLocal, IrExpression.objectCall("javan_char_sequence_to_string_java",
                        List.of(IrExpression.objectLocal(argumentLocal)))));
                    instructions.add(IrInstruction.label(nextLabel));
                } else {
                    instructions.add(IrInstruction.assignObject(convertedLocal, IrExpression.objectCall("javan_char_sequence_to_string_java",
                        List.of(IrExpression.objectLocal(argumentLocal)))));
                }
                instructions.add(IrInstruction.branchIf(doneLabel, IrExpression.intCall("javan_pending_has", List.of())));
                converted.add(IrExpression.objectLocal(convertedLocal));
            }
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall("javan_string_replace_sequence_java",
                List.of(IrExpression.objectLocal(receiverLocal), converted.get(0), converted.get(1)))));
            instructions.add(IrInstruction.label(doneLabel));
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "replace".equals(methodRef.name())
            && "(CC)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression newCh = popInt(classFile, method, stack);
            final IrExpression oldCh = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_replace_char_java", List.of(receiver, oldCh, newCh));
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
            final IrExpression argument = popObject(classFile, method, instruction, stack);
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            final IrExpression savedReceiver;
            if (receiver.kind() == IrExpression.Kind.LOCAL || receiver.kind() == IrExpression.Kind.OBJECT_NULL
                || receiver.kind() == IrExpression.Kind.STRING_LITERAL) {
                savedReceiver = receiver;
            } else {
                final String receiverLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
                savedReceiver = IrExpression.objectLocal(receiverLocal);
            }
            final IrExpression savedArgument;
            if (argument.kind() == IrExpression.Kind.LOCAL || argument.kind() == IrExpression.Kind.OBJECT_NULL
                || argument.kind() == IrExpression.Kind.STRING_LITERAL) {
                savedArgument = argument;
            } else {
                final String argumentLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(argumentLocal, argument));
                savedArgument = IrExpression.objectLocal(argumentLocal);
            }
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_object_equals_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent,
                IrExpression.objectComparison("!=", savedReceiver, IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.objectNull());
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            final List<EntryPoint> targets = virtualTargets(classes, methodRef, instantiatedTypes);
            final List<IrExpression> arguments = List.of(savedReceiver, savedArgument);
            boolean requiresDispatch = !targets.isEmpty();
            for (final ClassFile candidate : classes.values()) {
                if (candidate.isEnum() || candidate.superName() == null
                    || !candidate.superName().isEmpty() && !"java/lang/Object".equals(candidate.superName())) {
                    requiresDispatch = true;
                    break;
                }
            }
            if (requiresDispatch) {
                final String symbol = dispatchSymbol(methodRef);
                dispatches.putIfAbsent(symbol, dispatch(classes, symbol, MethodDescriptor.parse(methodRef.descriptor()), targets));
                pushIntCall(instructions, stack, localDeclarations, symbol, arguments);
            } else {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_object_virtual_equals", arguments)));
            }
            return;
        }
        if ("java/lang/Object".equals(methodRef.owner())
            && "getClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            final IrExpression receiver = stackValueExpression(popObjectValue(classFile, method, instruction, stack));
            final IrExpression savedReceiver;
            if (receiver.kind() == IrExpression.Kind.LOCAL || receiver.kind() == IrExpression.Kind.OBJECT_NULL) {
                savedReceiver = receiver;
            } else {
                final String receiverLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
                savedReceiver = IrExpression.objectLocal(receiverLocal);
            }
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_get_class_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent,
                IrExpression.objectComparison("!=", savedReceiver, IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.objectNull());
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_object_get_class", List.of(savedReceiver))));
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
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_concat_java", List.of(receiver, argument));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "repeat".equals(methodRef.name())
            && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression count = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
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
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_strip", List.of(popObject(classFile, method, stack)));
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
        if ("java/nio/charset/Charset".equals(methodRef.owner())
            && ("name".equals(methodRef.name()) || "displayName".equals(methodRef.name()) || "toString".equals(methodRef.name()))
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_charset_name_java", List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "getBytes".equals(methodRef.name())
            && ("(Ljava/nio/charset/Charset;)[B".equals(methodRef.descriptor())
                || "()[B".equals(methodRef.descriptor()))) {
            final IrExpression charset = "()[B".equals(methodRef.descriptor())
                ? IrExpression.objectCall("javan_standard_charset_utf8", List.of()) : popObject(classFile, method, stack);
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
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring_java", List.of(receiver, begin));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "substring".equals(methodRef.name())
            && "(II)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring_range_java", List.of(receiver, begin, end));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "subSequence".equals(methodRef.name())
            && "(II)Ljava/lang/CharSequence;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_substring_range_java", List.of(receiver, begin, end));
            return;
        }
        if (lowerJdkWrapperInstanceCall(classFile, method, methodRef, stack)) {
            return;
        }
        if ("java/util/ServiceLoader".equals(methodRef.owner())
            && "iterator".equals(methodRef.name())
            && "()Ljava/util/Iterator;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_service_loader_iterator",
                List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/util/ServiceLoader".equals(methodRef.owner())
            && "findFirst".equals(methodRef.name())
            && "()Ljava/util/Optional;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_service_loader_find_first",
                List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/util/ServiceLoader".equals(methodRef.owner())
            && "reload".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_service_loader_reload",
                List.of(popObject(classFile, method, stack))));
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
        if (lowerJdkHttpVirtualCall(classFile, method, instruction, methodRef, instructions, stack)) {
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
        if (lowerJdkPathInstanceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkTimeInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if ("java/util/random/RandomGenerator".equals(methodRef.owner()) && "nextInt".equals(methodRef.name())
            && ("(I)I".equals(methodRef.descriptor()) || "()I".equals(methodRef.descriptor()))) {
            final List<EntryPoint> targets = interfaceTargets(classes, methodRef, instantiatedTypes);
            if (!targets.isEmpty()) {
                final boolean bounded = "(I)I".equals(methodRef.descriptor());
                final IrExpression bound = bounded ? popInt(classFile, method, stack) : null;
                final IrExpression receiver = popObject(classFile, method, stack);
                final String receiverLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
                final List<IrExpression> arguments = new ArrayList<>();
                arguments.add(IrExpression.objectLocal(receiverLocal));
                if (bounded) {
                    final String boundLocal = newIntLocal(localDeclarations);
                    instructions.add(IrInstruction.assignInt(boundLocal, bound));
                    arguments.add(IrExpression.intLocal(boundLocal));
                }
                final String resultLocal = newIntLocal(localDeclarations);
                final String builtinLabel = "label_random_next_int_builtin_" + instruction.offset() + "_" + resultLocal;
                final String doneLabel = "label_random_next_int_done_" + instruction.offset() + "_" + resultLocal;
                final String symbol = dispatchSymbol(methodRef);
                dispatches.putIfAbsent(symbol, dispatch(classes, symbol, MethodDescriptor.parse(methodRef.descriptor()), targets));
                instructions.add(IrInstruction.branchIf(builtinLabel, IrExpression.intCall("javan_secure_random_use_builtin",
                    List.of(IrExpression.objectLocal(receiverLocal)))));
                instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall(symbol, arguments)));
                instructions.add(IrInstruction.jump(doneLabel));
                instructions.add(IrInstruction.label(builtinLabel));
                instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall(
                    bounded ? "javan_secure_random_next_int_bound_java" : "javan_secure_random_next_int_java", arguments)));
                instructions.add(IrInstruction.label(doneLabel));
                stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
                return;
            }
        }
        if ("java/util/random/RandomGenerator".equals(methodRef.owner()) && "nextBytes".equals(methodRef.name())
            && "([B)V".equals(methodRef.descriptor())) {
            final List<EntryPoint> targets = interfaceTargets(classes, methodRef, instantiatedTypes);
            if (!targets.isEmpty()) {
                final IrExpression bytes = popObject(classFile, method, stack);
                final IrExpression receiver = popObject(classFile, method, stack);
                final String receiverLocal = newObjectLocal(localDeclarations);
                final String bytesLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
                instructions.add(IrInstruction.assignObject(bytesLocal, bytes));
                final String builtinLabel = "label_random_next_bytes_builtin_" + instruction.offset() + "_" + receiverLocal;
                final String doneLabel = "label_random_next_bytes_done_" + instruction.offset() + "_" + receiverLocal;
                final String symbol = dispatchSymbol(methodRef);
                dispatches.putIfAbsent(symbol, dispatch(classes, symbol, MethodDescriptor.parse(methodRef.descriptor()), targets));
                instructions.add(IrInstruction.branchIf(builtinLabel, IrExpression.intCall("javan_secure_random_use_builtin",
                    List.of(IrExpression.objectLocal(receiverLocal)))));
                instructions.add(IrInstruction.callStaticVoid(symbol,
                    List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(bytesLocal))));
                instructions.add(IrInstruction.jump(doneLabel));
                instructions.add(IrInstruction.label(builtinLabel));
                instructions.add(IrInstruction.callStaticVoid("javan_secure_random_next_bytes_java",
                    List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(bytesLocal))));
                instructions.add(IrInstruction.label(doneLabel));
                return;
            }
        }
        if ("java/util/random/RandomGenerator".equals(methodRef.owner()) && "nextLong".equals(methodRef.name())
            && "()J".equals(methodRef.descriptor())) {
            final List<EntryPoint> targets = interfaceTargets(classes, methodRef, instantiatedTypes);
            if (!targets.isEmpty()) {
                final IrExpression receiver = popObject(classFile, method, stack);
                final String receiverLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
                final int index = localDeclarations.size();
                final String resultLocal = "long" + index;
                localDeclarations.put(Integer.MIN_VALUE + index, new IrLocal(IrType.LONG, resultLocal));
                final String builtinLabel = "label_random_next_long_builtin_" + instruction.offset() + "_" + resultLocal;
                final String doneLabel = "label_random_next_long_done_" + instruction.offset() + "_" + resultLocal;
                final String symbol = dispatchSymbol(methodRef);
                dispatches.putIfAbsent(symbol, dispatch(classes, symbol, MethodDescriptor.parse(methodRef.descriptor()), targets));
                instructions.add(IrInstruction.branchIf(builtinLabel, IrExpression.intCall("javan_secure_random_use_builtin",
                    List.of(IrExpression.objectLocal(receiverLocal)))));
                instructions.add(IrInstruction.assignLong(resultLocal, IrExpression.longCall(symbol,
                    List.of(IrExpression.objectLocal(receiverLocal)))));
                instructions.add(IrInstruction.jump(doneLabel));
                instructions.add(IrInstruction.label(builtinLabel));
                instructions.add(IrInstruction.assignLong(resultLocal, IrExpression.longCall("javan_secure_random_next_long_java",
                    List.of(IrExpression.objectLocal(receiverLocal)))));
                instructions.add(IrInstruction.label(doneLabel));
                stack.add(StackValue.longExpression(IrExpression.longLocal(resultLocal)));
                return;
            }
        }
        if (("java/security/SecureRandom".equals(methodRef.owner()) || JdkCallSupport.isSecureRandomSupertypeCall(methodRef))
            && ("nextInt".equals(methodRef.name()) || "nextLong".equals(methodRef.name()))
            && JdkCallSupport.isSupported(methodRef)) {
            final boolean wide = "nextLong".equals(methodRef.name());
            final boolean ranged = "(II)I".equals(methodRef.descriptor()) || "(JJ)J".equals(methodRef.descriptor());
            final boolean bounded = ranged || "(I)I".equals(methodRef.descriptor()) || "(J)J".equals(methodRef.descriptor());
            final IrExpression zero = wide ? IrExpression.longLiteral(0) : IrExpression.intLiteral(0);
            final IrExpression bound = bounded ? wide ? popLong(classFile, method, stack) : popInt(classFile, method, stack) : zero;
            final IrExpression origin = ranged ? wide ? popLong(classFile, method, stack) : popInt(classFile, method, stack) : zero;
            final IrExpression receiver = popObject(classFile, method, stack);
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
            final int originIndex = localDeclarations.size();
            final String prefix = wide ? "long" : "int";
            final IrType valueType = wide ? IrType.LONG : IrType.INT;
            final String originLocal = prefix + originIndex;
            if (ranged) {
                localDeclarations.put(Integer.MIN_VALUE + originIndex, new IrLocal(valueType, originLocal));
                instructions.add(wide ? IrInstruction.assignLong(originLocal, origin) : IrInstruction.assignInt(originLocal, origin));
            }
            final int boundIndex = localDeclarations.size();
            final String boundLocal = prefix + boundIndex;
            if (bounded) {
                localDeclarations.put(Integer.MIN_VALUE + boundIndex, new IrLocal(valueType, boundLocal));
                instructions.add(wide ? IrInstruction.assignLong(boundLocal, bound) : IrInstruction.assignInt(boundLocal, bound));
            }
            final IrExpression savedOrigin = wide ? IrExpression.longLocal(originLocal) : IrExpression.intLocal(originLocal);
            final IrExpression savedBound = wide ? IrExpression.longLocal(boundLocal) : IrExpression.intLocal(boundLocal);
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_secure_random_" + prefix + "_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
                IrExpression.stringLiteral("SecureRandom"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            if (JdkCallSupport.isSecureRandomSupertypeCall(methodRef)) {
                instructions.add(IrInstruction.callStaticVoid("javan_secure_random_require_random", List.of(IrExpression.objectLocal(receiverLocal))));
            }
            if (bounded) {
                final String boundValid = "label_secure_random_" + prefix + "_bound_" + instruction.offset();
                final IrExpression condition = ranged
                    ? IrExpression.intComparison("<", savedOrigin, savedBound)
                    : IrExpression.intComparison(">", savedBound, zero);
                instructions.add(IrInstruction.branchIf(boundValid, condition));
                routePendingPlatformException(classFile, method, instruction, instructions, stack,
                    pendingExceptionHandlerStacks, sourceLines, "java/lang/IllegalArgumentException",
                    IrExpression.stringLiteral(ranged ? "bound must be greater than origin" : "bound must be positive"));
                instructions.add(IrInstruction.label(boundValid));
                stack.addAll(successStack);
            }
            final List<IrExpression> arguments = ranged
                ? List.of(IrExpression.objectLocal(receiverLocal), savedOrigin, savedBound)
                : bounded ? List.of(IrExpression.objectLocal(receiverLocal), savedBound)
                : List.of(IrExpression.objectLocal(receiverLocal));
            final String symbol = "javan_secure_random_next_" + prefix + (ranged ? "_range" : bounded ? "_bound" : "");
            if (wide) {
                pushLongCall(instructions, stack, localDeclarations, symbol, arguments);
            } else {
                pushIntCall(instructions, stack, localDeclarations, symbol, arguments);
            }
            return;
        }
        if (("java/security/SecureRandom".equals(methodRef.owner()) || JdkCallSupport.isSecureRandomSupertypeCall(methodRef))
            && ("nextBoolean".equals(methodRef.name()) || "nextFloat".equals(methodRef.name())
                || "nextDouble".equals(methodRef.name())) && JdkCallSupport.isSupported(methodRef)) {
            final boolean wide = "nextDouble".equals(methodRef.name());
            final boolean ranged = "(FF)F".equals(methodRef.descriptor()) || "(DD)D".equals(methodRef.descriptor());
            final boolean bounded = ranged || "(F)F".equals(methodRef.descriptor()) || "(D)D".equals(methodRef.descriptor());
            final IrExpression zero = wide ? IrExpression.doubleLiteral(0.0) : IrExpression.floatLiteral(0.0f);
            final IrExpression bound = bounded ? wide ? popDouble(classFile, method, stack) : popFloat(classFile, method, stack) : zero;
            final IrExpression origin = ranged ? wide ? popDouble(classFile, method, stack) : popFloat(classFile, method, stack) : zero;
            final IrExpression receiver = popObject(classFile, method, stack);
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
            final String prefix = wide ? "double" : "float";
            final IrType valueType = wide ? IrType.DOUBLE : IrType.FLOAT;
            final int originIndex = localDeclarations.size();
            final String originName = prefix + originIndex;
            if (ranged) {
                localDeclarations.put(Integer.MIN_VALUE + originIndex, new IrLocal(valueType, originName));
                instructions.add(wide ? IrInstruction.assignDouble(originName, origin) : IrInstruction.assignFloat(originName, origin));
            }
            final int boundIndex = localDeclarations.size();
            final String boundName = prefix + boundIndex;
            if (bounded) {
                localDeclarations.put(Integer.MIN_VALUE + boundIndex, new IrLocal(valueType, boundName));
                instructions.add(wide ? IrInstruction.assignDouble(boundName, bound) : IrInstruction.assignFloat(boundName, bound));
            }
            final IrExpression savedOrigin = ranged ? wide ? IrExpression.doubleLocal(originName) : IrExpression.floatLocal(originName) : zero;
            final IrExpression savedBound = wide ? IrExpression.doubleLocal(boundName) : IrExpression.floatLocal(boundName);
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_secure_random_scalar_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
                IrExpression.stringLiteral("SecureRandom"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            if (JdkCallSupport.isSecureRandomSupertypeCall(methodRef)) {
                instructions.add(IrInstruction.callStaticVoid("javan_secure_random_require_random", List.of(IrExpression.objectLocal(receiverLocal))));
            }
            if (bounded) {
                final String boundValid = "label_secure_random_scalar_bound_" + instruction.offset();
                final IrExpression valid = IrExpression.intCall("javan_secure_random_" + prefix + "_range_valid",
                    List.of(savedOrigin, savedBound));
                instructions.add(IrInstruction.branchIf(boundValid,
                    IrExpression.intComparison("!=", valid, IrExpression.intLiteral(0))));
                routePendingPlatformException(classFile, method, instruction, instructions, stack,
                    pendingExceptionHandlerStacks, sourceLines, "java/lang/IllegalArgumentException",
                    IrExpression.stringLiteral("bounds must be finite and origin less than bound"));
                instructions.add(IrInstruction.label(boundValid));
                stack.addAll(successStack);
            }
            final List<IrExpression> arguments = bounded
                ? List.of(IrExpression.objectLocal(receiverLocal), savedOrigin, savedBound)
                : List.of(IrExpression.objectLocal(receiverLocal));
            if ("nextBoolean".equals(methodRef.name())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_secure_random_next_boolean", arguments);
            } else if (!wide) {
                final int index = localDeclarations.size();
                final String name = "float" + index;
                localDeclarations.put(Integer.MIN_VALUE + index, new IrLocal(IrType.FLOAT, name));
                instructions.add(IrInstruction.assignFloat(name, IrExpression.floatCall(
                    bounded ? "javan_secure_random_next_float_range" : "javan_secure_random_next_float", arguments)));
                stack.add(StackValue.floatExpression(IrExpression.floatLocal(name)));
            } else {
                final int index = localDeclarations.size();
                final String name = "double" + index;
                localDeclarations.put(Integer.MIN_VALUE + index, new IrLocal(IrType.DOUBLE, name));
                instructions.add(IrInstruction.assignDouble(name, IrExpression.doubleCall(
                    bounded ? "javan_secure_random_next_double_range" : "javan_secure_random_next_double", arguments)));
                stack.add(StackValue.doubleExpression(IrExpression.doubleLocal(name)));
            }
            return;
        }
        if ("java/security/SecureRandom".equals(methodRef.owner())
            && "generateSeed".equals(methodRef.name()) && "(I)[B".equals(methodRef.descriptor())) {
            lowerSecureRandomSeedCall(classFile, method, instruction, instructions, stack, localDeclarations,
                pendingExceptionHandlerStacks, sourceLines, true);
            return;
        }
        if (("java/security/SecureRandom".equals(methodRef.owner())
                && "nextBytes".equals(methodRef.name()) && "([B)V".equals(methodRef.descriptor()))
            || ("nextBytes".equals(methodRef.name()) && JdkCallSupport.isSecureRandomSupertypeCall(methodRef))) {
            final IrExpression bytesInput = popObject(classFile, method, stack);
            final IrExpression receiverInput = popObject(classFile, method, stack);
            final boolean randomSupertype = JdkCallSupport.isSecureRandomSupertypeCall(methodRef);
            IrExpression receiver = receiverInput;
            IrExpression bytes = bytesInput;
            if (randomSupertype) {
                final String receiverLocal = newObjectLocal(localDeclarations);
                final String bytesLocal = newObjectLocal(localDeclarations);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiverInput));
                instructions.add(IrInstruction.assignObject(bytesLocal, bytesInput));
                receiver = IrExpression.objectLocal(receiverLocal);
                bytes = IrExpression.objectLocal(bytesLocal);
            }
            final String receiverLabel = "label_secure_random_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(
                receiverLabel,
                IrExpression.objectComparison("!=", receiver, IrExpression.objectNull())
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
                IrExpression.stringLiteral("SecureRandom")
            );
            instructions.add(IrInstruction.label(receiverLabel));
            final String bytesLabel = "label_secure_random_bytes_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(
                bytesLabel,
                IrExpression.objectComparison("!=", bytes, IrExpression.objectNull())
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
                IrExpression.stringLiteral("bytes")
            );
            instructions.add(IrInstruction.label(bytesLabel));
            instructions.add(IrInstruction.callStaticVoid(
                randomSupertype ? "javan_secure_random_next_bytes_as_random" : "javan_secure_random_next_bytes",
                List.of(receiver, bytes)
            ));
            return;
        }
        if ("java/util/UUID".equals(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_uuid_to_string",
                List.of(popObject(classFile, method, stack))
            )));
            return;
        }
        if (lowerBase64InstanceCall(
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
                || (receiver.throwableType().isPresent()
                    && classes.containsKey(receiver.throwableType().orElseThrow()))
                ? IrExpression.objectCall(
                    "javan_caught_throwable_message",
                    List.of(receiver.expression().orElseThrow())
                )
                : receiver.expression().orElseThrow();
            stack.add(StackValue.objectExpression(message));
            return;
        }
        if (isPlatformThrowableGetCause(methodRef)) {
            final StackValue receiver = popObjectValue(classFile, method, instruction, stack);
            if (receiver.kind() == StackKind.CAUGHT_THROWABLE) {
                stack.add(StackValue.caughtThrowable(IrExpression.objectCall(
                    "javan_caught_throwable_cause",
                    List.of(receiver.expression().orElseThrow())
                )));
            } else {
                stack.add(StackValue.objectExpression(IrExpression.objectNull()));
            }
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
        if ("com/sun/net/httpserver/HttpServer".equals(methodRef.owner())
            && "create".equals(methodRef.name())
            && "(Ljava/net/InetSocketAddress;I)Lcom/sun/net/httpserver/HttpServer;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_server_create", arguments)));
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
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        if ("com/sun/net/httpserver/HttpServer".equals(methodRef.owner())) {
            final boolean createContext = "createContext".equals(methodRef.name())
                && "(Ljava/lang/String;Lcom/sun/net/httpserver/HttpHandler;)Lcom/sun/net/httpserver/HttpContext;".equals(methodRef.descriptor());
            final boolean getAddress = "getAddress".equals(methodRef.name())
                && "()Ljava/net/InetSocketAddress;".equals(methodRef.descriptor());
            final boolean start = "start".equals(methodRef.name()) && "()V".equals(methodRef.descriptor());
            final boolean stop = "stop".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor());
            if (!createContext && !getAddress && !start && !stop) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            final List<IrExpression> callArguments = new ArrayList<>();
            callArguments.add(receiver);
            callArguments.addAll(arguments);
            if (createContext) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_server_create_context", callArguments)));
                return true;
            }
            if (getAddress) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_server_get_address", callArguments)));
                return true;
            }
            if (start) {
                instructions.add(IrInstruction.callStaticVoid("javan_http_server_start", callArguments));
                return true;
            }
            if (stop) {
                instructions.add(IrInstruction.callStaticVoid("javan_http_server_stop", callArguments));
                return true;
            }
        }
        if ("java/net/URI".equals(methodRef.owner())) {
            final boolean getRawPath = "getRawPath".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor());
            final boolean getRawQuery = "getRawQuery".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor());
            if (!getRawPath && !getRawQuery) {
                return false;
            }
            popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                getRawPath ? "javan_uri_get_raw_path" : "javan_uri_get_raw_query",
                List.of(receiver)
            )));
            return true;
        }
        if ("com/sun/net/httpserver/HttpExchange".equals(methodRef.owner())) {
            final boolean sendResponseHeaders = "sendResponseHeaders".equals(methodRef.name()) && "(IJ)V".equals(methodRef.descriptor());
            final boolean getRequestMethod = "getRequestMethod".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor());
            final boolean getRequestUri = "getRequestURI".equals(methodRef.name()) && "()Ljava/net/URI;".equals(methodRef.descriptor());
            final boolean getRequestHeaders = "getRequestHeaders".equals(methodRef.name()) && "()Lcom/sun/net/httpserver/Headers;".equals(methodRef.descriptor());
            final boolean getRequestBody = "getRequestBody".equals(methodRef.name()) && "()Ljava/io/InputStream;".equals(methodRef.descriptor());
            final boolean getResponseBody = "getResponseBody".equals(methodRef.name()) && "()Ljava/io/OutputStream;".equals(methodRef.descriptor());
            final boolean close = "close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor());
            if (!sendResponseHeaders && !getRequestMethod && !getRequestUri && !getRequestHeaders && !getRequestBody && !getResponseBody && !close) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            final List<IrExpression> callArguments = new ArrayList<>();
            callArguments.add(receiver);
            callArguments.addAll(arguments);
            if (sendResponseHeaders) {
                instructions.add(IrInstruction.callStaticVoid("javan_http_exchange_send_response_headers", callArguments));
                return true;
            }
            if (getRequestMethod) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_exchange_get_request_method", callArguments)));
                return true;
            }
            if (getRequestUri) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_exchange_get_request_uri", callArguments)));
                return true;
            }
            if (getRequestHeaders) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_exchange_get_request_headers", callArguments)));
                return true;
            }
            if (getRequestBody) {
                stack.add(StackValue.httpExchangeInputStream(IrExpression.objectCall("javan_http_exchange_get_request_body", callArguments)));
                return true;
            }
            if (getResponseBody) {
                stack.add(StackValue.httpExchangeOutputStream(IrExpression.objectCall("javan_http_exchange_output_stream", callArguments)));
                return true;
            }
            if (close) {
                instructions.add(IrInstruction.callStaticVoid("javan_http_exchange_close", callArguments));
                return true;
            }
        }
        if ("com/sun/net/httpserver/Headers".equals(methodRef.owner())
            && "getFirst".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            final List<IrExpression> callArguments = new ArrayList<>();
            callArguments.add(receiver);
            callArguments.addAll(arguments);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_http_headers_get_first", callArguments)));
            return true;
        }
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
            if (receiver.kind() == StackKind.RESOURCE_INPUT_STREAM || receiver.kind() == StackKind.HTTP_EXCHANGE_INPUT_STREAM) {
                final String prefix = receiver.kind() == StackKind.HTTP_EXCHANGE_INPUT_STREAM
                    ? "javan_http_exchange_input_stream"
                    : "javan_resource_input_stream";
                if ("read".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                    stack.add(StackValue.intExpression(IrExpression.intCall(prefix + "_read", List.of(receiver.expression().orElseThrow()))));
                    return true;
                }
                if ("read".equals(methodRef.name()) && "([B)I".equals(methodRef.descriptor())) {
                    pushIntCall(instructions, stack, localDeclarations, prefix + "_read_bytes",
                        List.of(receiver.expression().orElseThrow(), arguments.getFirst()));
                    return true;
                }
                if ("read".equals(methodRef.name()) && "([BII)I".equals(methodRef.descriptor())) {
                    pushIntCall(instructions, stack, localDeclarations, prefix + "_read_bytes_range",
                        List.of(receiver.expression().orElseThrow(), arguments.get(0), arguments.get(1), arguments.get(2)));
                    return true;
                }
                if ("readAllBytes".equals(methodRef.name()) && "()[B".equals(methodRef.descriptor())) {
                    pushObjectCall(instructions, stack, localDeclarations, prefix + "_read_all_bytes",
                        List.of(receiver.expression().orElseThrow()));
                    return true;
                }
                if (receiver.kind() == StackKind.RESOURCE_INPUT_STREAM
                    && "close".equals(methodRef.name())
                    && "()V".equals(methodRef.descriptor())) {
                    instructions.add(IrInstruction.callStaticVoid("javan_resource_input_stream_close", List.of(receiver.expression().orElseThrow())));
                    return true;
                }
            }
            if (receiver.kind() == StackKind.SOCKET_INPUT_STREAM
                || receiver.kind() == StackKind.RESOURCE_INPUT_STREAM
                || receiver.kind() == StackKind.HTTP_EXCHANGE_INPUT_STREAM) {
                return false;
            }
            throw unsupportedSpecializedStreamReceiver(classFile, method, methodRef);
        }
        if (!"java/io/OutputStream".equals(methodRef.owner())) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        final StackValue receiver = popObjectValue(classFile, method, instruction, stack);
        if (receiver.kind() != StackKind.SOCKET_OUTPUT_STREAM && receiver.kind() != StackKind.HTTP_EXCHANGE_OUTPUT_STREAM) {
            throw unsupportedSpecializedStreamReceiver(classFile, method, methodRef);
        }
        final String prefix = receiver.kind() == StackKind.HTTP_EXCHANGE_OUTPUT_STREAM
            ? "javan_http_exchange_output_stream"
            : "javan_socket_output_stream";
        if ("write".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(prefix + "_write", List.of(receiver.expression().orElseThrow(), arguments.getFirst())));
            return true;
        }
        if ("write".equals(methodRef.name()) && "([B)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(prefix + "_write_bytes", List.of(receiver.expression().orElseThrow(), arguments.getFirst())));
            return true;
        }
        if ("write".equals(methodRef.name()) && "([BII)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(prefix + "_write_bytes_range",
                List.of(receiver.expression().orElseThrow(), arguments.get(0), arguments.get(1), arguments.get(2))));
            return true;
        }
        if ("flush".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(prefix + "_flush", List.of(receiver.expression().orElseThrow())));
            return true;
        }
        if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(prefix + "_close", List.of(receiver.expression().orElseThrow())));
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
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final SourceLineIndex sourceLines
    ) {
        if (!JdkCallSupport.isPrintStreamPrint(methodRef)) return false;
        int receiverIndex = stack.size() - 2;
        if ("()V".equals(methodRef.descriptor())) receiverIndex = stack.size() - 1;
        if (receiverIndex >= 0 && stack.get(receiverIndex).kind() == StackKind.OBJECT) {
            final String done = "label_print_stream_done_" + instruction.offset();
            guardPrintStreamReceiver(classFile, method, instruction, instructions, stack, receiverIndex,
                localDeclarations, sourceLines, done);
            final boolean lowered = lowerPrintStreamCallUnchecked(classFile, method, instruction, methodRef,
                instructions, stack, localDeclarations, sourceLines);
            instructions.add(IrInstruction.label(done));
            return lowered;
        }
        return lowerPrintStreamCallUnchecked(classFile, method, instruction, methodRef,
            instructions, stack, localDeclarations, sourceLines);
    }

    static boolean lowerPrintStreamCallUnchecked(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final SourceLineIndex sourceLines
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
            final String done = "label_print_object_done_" + instruction.offset();
            final String converted = BytecodeToIRDynamicSupport.newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(converted,
                IrExpression.objectCall(BytecodeToIR.OBJECT_STRING_DISPATCH, List.of(argument))));
            instructions.add(IrInstruction.branchIf(done, IrExpression.intCall("javan_pending_has", List.of())));
            final String nonNull = "label_print_object_non_null_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(nonNull,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(converted), IrExpression.objectNull())));
            instructions.add(IrInstruction.setPending("java/lang/NullPointerException", IrExpression.objectNull(),
                BytecodeToIRControlFlowSupport.sourceLocation(classFile, method, instruction, sourceLines)));
            instructions.add(IrInstruction.jump(done));
            instructions.add(IrInstruction.label(nonNull));
            emitPrintObject(classFile, method, instruction, instructions, stack, IrExpression.objectLocal(converted));
            instructions.add(IrInstruction.label(done));
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
            final String done = "label_print_object_done_" + instruction.offset();
            final String converted = BytecodeToIRDynamicSupport.newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(converted,
                IrExpression.objectCall(BytecodeToIR.OBJECT_STRING_DISPATCH, List.of(argument))));
            instructions.add(IrInstruction.branchIf(done, IrExpression.intCall("javan_pending_has", List.of())));
            emitPrintlnObject(classFile, method, instruction, instructions, stack, IrExpression.objectLocal(converted));
            instructions.add(IrInstruction.label(done));
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

    static void guardPrintStreamReceiver(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final int receiverIndex,
        final Map<Integer, IrLocal> localDeclarations,
        final SourceLineIndex sourceLines,
        final String done
    ) {
        final StackValue receiver = stack.get(receiverIndex);
        IrExpression value = receiver.expression().orElseThrow();
        if (value.kind() != IrExpression.Kind.LOCAL && value.kind() != IrExpression.Kind.OBJECT_NULL) {
            final String local = BytecodeToIRDynamicSupport.newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(local, value));
            value = IrExpression.objectLocal(local);
            stack.set(receiverIndex, StackValue.objectExpression(value));
        }
        final String present = "label_print_receiver_non_null_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(present,
            IrExpression.objectComparison("!=", value, IrExpression.objectNull())));
        instructions.add(IrInstruction.setPending("java/lang/NullPointerException", IrExpression.objectNull(),
            BytecodeToIRControlFlowSupport.sourceLocation(classFile, method, instruction, sourceLines)));
        instructions.add(IrInstruction.jump(done));
        instructions.add(IrInstruction.label(present));
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
            || !("run".equals(methodRef.name()) || "runResult".equals(methodRef.name()))
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
        if (JdkCallSupport.isBoxedFloatingClassification(methodRef)) {
            final IrExpression receiver = popObject(classFile, method, stack);
            final boolean isFloat = "java/lang/Float".equals(methodRef.owner());
            final IrExpression value = isFloat
                ? IrExpression.floatCall("javan_float_float_value", List.of(receiver))
                : IrExpression.doubleCall("javan_double_double_value", List.of(receiver));
            final String function = (isFloat ? "javan_float_" : "javan_double_")
                + ("isNaN".equals(methodRef.name()) ? "is_nan" : "is_infinite");
            stack.add(StackValue.intExpression(IrExpression.intCall(function, List.of(value))));
            return true;
        }
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
        if ("<init>".equals(method.name())
            && BytecodeToIR.isThrowable(classes, classFile.name())
            && isPlatformThrowableDefaultConstructor(methodRef)) {
            final IrExpression receiver = popObject(classFile, method, stack);
            if (isSelfReceiver(receiver)) {
                instructions.add(IrInstruction.callStaticVoid(
                    "javan_generated_throwable_initialize",
                    List.of(receiver, IrExpression.objectNull())
                ));
            } else {
                updatePendingThrowableMessage(stack, IrExpression.objectNull());
            }
            return;
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
            if ("<init>".equals(method.name())
                && BytecodeToIR.isThrowable(classes, classFile.name())
                && isSelfReceiver(receiver)) {
                instructions.add(IrInstruction.callStaticVoid(
                    "javan_generated_throwable_initialize",
                    List.of(receiver, arguments.getFirst())
                ));
                return;
            }
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
        appendCallResult(
            instructions,
            stack,
            localDeclarations,
            descriptor.returnType(),
            symbol,
            callArguments,
            BytecodeToIR.throwableValueFromMethod(classes, methodRef, instruction.opcode() == 183)
        );
    }

    private static boolean isZeroArgNoopPlatformConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name()) || !"()V".equals(methodRef.descriptor())) {
            return false;
        }
        return "java/lang/Object".equals(methodRef.owner())
            || "java/lang/Record".equals(methodRef.owner())
            || "java/security/SecureRandom".equals(methodRef.owner())
            || "java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy".equals(methodRef.owner());
    }

    private static boolean isSelfReceiver(final IrExpression receiver) {
        return receiver.kind() == IrExpression.Kind.LOCAL && "self".equals(receiver.value());
    }

    private static String returnDescriptor(final String methodDescriptor) {
        return methodDescriptor.substring(methodDescriptor.lastIndexOf(')') + 1);
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
        final MethodRef rawMethodRef = instruction.methodRef().orElseThrow();
        final MethodRef methodRef = JdkCallSupport.normalizeInheritedSupportedJdkCall(classes, rawMethodRef)
            .orElse(rawMethodRef);
        if ("java/nio/charset/Charset".equals(methodRef.owner())
            && "defaultCharset".equals(methodRef.name())
            && "()Ljava/nio/charset/Charset;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_standard_charset_utf8", List.of());
            return;
        }
        if (lowerResourceAsStream(classFile, method, instruction, methodRef, instructions, stack,
            localDeclarations, pendingExceptionHandlerStacks, sourceLines)) {
            return;
        }
        if (lowerEnumValues(classes, classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerClassForName(
            classes,
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
        if (lowerJdkCollectionStaticCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if ("java/util/ServiceLoader".equals(methodRef.owner())
            && ("load".equals(methodRef.name()) || "loadInstalled".equals(methodRef.name()))
            && "(Ljava/lang/Class;)Ljava/util/ServiceLoader;".equals(methodRef.descriptor())) {
            final String loader = "loadInstalled".equals(methodRef.name())
                ? applicationModule(classes, classFile)
                    ? "javan_generated_service_loader_load_installed_module"
                    : "javan_generated_service_loader_load_installed"
                : applicationModule(classes, classFile)
                    ? "javan_generated_service_loader_load_module"
                    : "javan_generated_service_loader_load";
            pushObjectCall(instructions, stack, localDeclarations, loader,
                List.of(popObject(classFile, method, stack)));
            return;
        }
        if ("java/util/ServiceLoader".equals(methodRef.owner()) && "load".equals(methodRef.name())
            && "(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;".equals(methodRef.descriptor())) {
            popObject(classFile, method, stack);
            final String loader = applicationModule(classes, classFile)
                ? "javan_generated_service_loader_load_module"
                : "javan_generated_service_loader_load";
            pushObjectCall(instructions, stack, localDeclarations, loader,
                List.of(popObject(classFile, method, stack)));
            return;
        }
        if (lowerJdkFileStaticCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if ("java/security/SecureRandom".equals(methodRef.owner())
            && "getSeed".equals(methodRef.name()) && "(I)[B".equals(methodRef.descriptor())) {
            lowerSecureRandomSeedCall(classFile, method, instruction, instructions, stack, localDeclarations,
                pendingExceptionHandlerStacks, sourceLines, false);
            return;
        }
        if ("java/util/UUID".equals(methodRef.owner())
            && "randomUUID".equals(methodRef.name())
            && "()Ljava/util/UUID;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_uuid_random", List.of())));
            return;
        }
        if ("java/util/Base64".equals(methodRef.owner())
            && "getEncoder".equals(methodRef.name())
            && "()Ljava/util/Base64$Encoder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_base64_get_encoder", List.of())));
            return;
        }
        if ("java/util/Base64".equals(methodRef.owner())
            && "getDecoder".equals(methodRef.name())
            && "()Ljava/util/Base64$Decoder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_base64_get_decoder", List.of())));
            return;
        }
        if (lowerJdkHttpStaticCall(classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerExecutorsStaticCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerOptionalStaticCall(
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
        appendCallResult(
            instructions,
            stack,
            localDeclarations,
            descriptor.returnType(),
            symbol,
            arguments,
            BytecodeToIR.throwableValueFromMethod(classes, methodRef, true)
        );
    }

    private static boolean applicationModule(final Map<String, ClassFile> classes, final ClassFile caller) {
        final ClassFile module = classes.get("module-info");
        return caller.application() && module != null && module.application();
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
        return IrExpression.objectCall("javan_builtin_enum_constant", List.of(
            IrExpression.stringLiteral(fieldRef.owner()), IrExpression.stringLiteral(fieldRef.name())
        ));
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
        if ("java/lang/Math".equals(methodRef.owner())
            || "java/lang/StrictMath".equals(methodRef.owner())
                && ("clamp".equals(methodRef.name()) || "floorDiv".equals(methodRef.name()) || "floorMod".equals(methodRef.name())
                    || "ceilDiv".equals(methodRef.name()) || "ceilMod".equals(methodRef.name())
                    || "divideExact".equals(methodRef.name()) || "floorDivExact".equals(methodRef.name())
                    || "ceilDivExact".equals(methodRef.name()))) {
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
        if ((("java/lang/Integer".equals(methodRef.owner()) && "(II)I".equals(methodRef.descriptor()))
                || ("java/lang/Long".equals(methodRef.owner()) && "(JJ)J".equals(methodRef.descriptor())))
            && ("divideUnsigned".equals(methodRef.name()) || "remainderUnsigned".equals(methodRef.name()))) {
            final boolean wide = "java/lang/Long".equals(methodRef.owner());
            final IrExpression right = wide ? popLong(classFile, method, stack) : popInt(classFile, method, stack);
            final IrExpression left = wide ? popLong(classFile, method, stack) : popInt(classFile, method, stack);
            final int index = localDeclarations.size();
            final String leftName = "unsignedLeft" + index;
            final String rightName = "unsignedRight" + index;
            final IrType type = wide ? IrType.LONG : IrType.INT;
            localDeclarations.put(Integer.MIN_VALUE + index, new IrLocal(type, leftName));
            localDeclarations.put(Integer.MIN_VALUE + index + 1, new IrLocal(type, rightName));
            final boolean literalDivisor = right.kind() == IrExpression.Kind.INT_LITERAL
                || right.kind() == IrExpression.Kind.LONG_LITERAL;
            final IrExpression savedLeft;
            if (literalDivisor && (left.kind() == IrExpression.Kind.LOCAL || left.kind() == IrExpression.Kind.INT_LITERAL
                || left.kind() == IrExpression.Kind.LONG_LITERAL)) {
                savedLeft = left;
            } else if (wide) {
                instructions.add(IrInstruction.assignLong(leftName, left));
                savedLeft = IrExpression.longLocal(leftName);
            } else {
                instructions.add(IrInstruction.assignInt(leftName, left));
                savedLeft = IrExpression.intLocal(leftName);
            }
            final IrExpression savedRight;
            if (literalDivisor) {
                savedRight = right;
            } else if (wide) {
                instructions.add(IrInstruction.assignLong(rightName, right));
                savedRight = IrExpression.longLocal(rightName);
            } else {
                instructions.add(IrInstruction.assignInt(rightName, right));
                savedRight = IrExpression.intLocal(rightName);
            }
            final String successLabel = "label_unsigned_division_success_" + instruction.offset() + "_" + index;
            instructions.add(IrInstruction.branchIf(successLabel, IrExpression.intComparison("!=", savedRight,
                wide ? IrExpression.longLiteral(0) : IrExpression.intLiteral(0))));
            final List<StackValue> successStack = List.copyOf(stack);
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/ArithmeticException", IrExpression.stringLiteral("/ by zero"));
            instructions.add(IrInstruction.label(successLabel));
            stack.addAll(successStack);
            final String symbol = (wide ? "javan_long_" : "javan_integer_")
                + ("divideUnsigned".equals(methodRef.name()) ? "divide_unsigned" : "remainder_unsigned");
            if (wide) {
                stack.add(StackValue.longExpression(IrExpression.longCall(symbol, List.of(savedLeft, savedRight))));
            } else {
                stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(savedLeft, savedRight))));
            }
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
            pushObjectCall(instructions, stack, localDeclarations, BytecodeToIR.OBJECT_STRING_DISPATCH,
                List.of(popPrintableObject(classFile, method, instruction, stack)));
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
        return false;
    }

    static boolean lowerIntegerIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (("toHexString".equals(methodRef.name()) || "toOctalString".equals(methodRef.name())
            || "toBinaryString".equals(methodRef.name()) || "toUnsignedString".equals(methodRef.name())
            || "toString".equals(methodRef.name()))
            && (("(II)Ljava/lang/String;".equals(methodRef.descriptor())
                    && ("toString".equals(methodRef.name()) || "toUnsignedString".equals(methodRef.name())))
                || ("(I)Ljava/lang/String;".equals(methodRef.descriptor())
                    && !"toString".equals(methodRef.name())))) {
            final IrExpression radix;
            if ("(II)Ljava/lang/String;".equals(methodRef.descriptor())) {
                radix = popInt(classFile, method, stack);
            } else if ("toHexString".equals(methodRef.name())) {
                radix = IrExpression.intLiteral(16);
            } else if ("toOctalString".equals(methodRef.name())) {
                radix = IrExpression.intLiteral(8);
            } else if ("toBinaryString".equals(methodRef.name())) {
                radix = IrExpression.intLiteral(2);
            } else {
                radix = IrExpression.intLiteral(10);
            }
            final IrExpression value = popInt(classFile, method, stack);
            final int unsignedMode = "toString".equals(methodRef.name()) ? 0 : 1;
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_integer_to_radix",
                List.of(value, radix, IrExpression.intLiteral(unsignedMode)))));
            return true;
        }
        if (("rotateLeft".equals(methodRef.name()) || "rotateRight".equals(methodRef.name()))
            && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression distance = popInt(classFile, method, stack);
            final IrExpression value = popInt(classFile, method, stack);
            final String symbol;
            if ("rotateLeft".equals(methodRef.name())) {
                symbol = "javan_integer_rotate_left";
            } else {
                symbol = "javan_integer_rotate_right";
            }
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value, distance))));
            return true;
        }
        if (("highestOneBit".equals(methodRef.name()) || "lowestOneBit".equals(methodRef.name())
            || "reverse".equals(methodRef.name()) || "reverseBytes".equals(methodRef.name())
            || "signum".equals(methodRef.name()))
            && "(I)I".equals(methodRef.descriptor())) {
            final String symbol;
            if ("highestOneBit".equals(methodRef.name())) {
                symbol = "javan_integer_highest_one_bit";
            } else if ("lowestOneBit".equals(methodRef.name())) {
                symbol = "javan_integer_lowest_one_bit";
            } else if ("reverse".equals(methodRef.name())) {
                symbol = "javan_integer_reverse";
            } else if ("reverseBytes".equals(methodRef.name())) {
                symbol = "javan_integer_reverse_bytes";
            } else {
                symbol = "javan_integer_signum";
            }
            final IrExpression value = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value))));
            return true;
        }
        if (("bitCount".equals(methodRef.name()) || "numberOfLeadingZeros".equals(methodRef.name())
            || "numberOfTrailingZeros".equals(methodRef.name())) && "(I)I".equals(methodRef.descriptor())) {
            final String symbol;
            if ("bitCount".equals(methodRef.name())) {
                symbol = "javan_integer_bit_count";
            } else if ("numberOfLeadingZeros".equals(methodRef.name())) {
                symbol = "javan_integer_leading_zeros";
            } else {
                symbol = "javan_integer_trailing_zeros";
            }
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("compareUnsigned".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_integer_compare_unsigned", List.of(left, right))));
            return true;
        }
        if ("toUnsignedLong".equals(methodRef.name()) && "(I)J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_integer_to_unsigned_long",
                List.of(popInt(classFile, method, stack)))));
            return true;
        }
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
        if (("toHexString".equals(methodRef.name()) || "toOctalString".equals(methodRef.name())
            || "toBinaryString".equals(methodRef.name()) || "toUnsignedString".equals(methodRef.name())
            || "toString".equals(methodRef.name()))
            && (("(JI)Ljava/lang/String;".equals(methodRef.descriptor())
                    && ("toString".equals(methodRef.name()) || "toUnsignedString".equals(methodRef.name())))
                || ("(J)Ljava/lang/String;".equals(methodRef.descriptor())
                    && !"toString".equals(methodRef.name())))) {
            final IrExpression radix;
            if ("(JI)Ljava/lang/String;".equals(methodRef.descriptor())) {
                radix = popInt(classFile, method, stack);
            } else if ("toHexString".equals(methodRef.name())) {
                radix = IrExpression.intLiteral(16);
            } else if ("toOctalString".equals(methodRef.name())) {
                radix = IrExpression.intLiteral(8);
            } else if ("toBinaryString".equals(methodRef.name())) {
                radix = IrExpression.intLiteral(2);
            } else {
                radix = IrExpression.intLiteral(10);
            }
            final IrExpression value = popLong(classFile, method, stack);
            final int unsignedMode = "toString".equals(methodRef.name()) ? 0 : 1;
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_long_to_radix",
                List.of(value, radix, IrExpression.intLiteral(unsignedMode)))));
            return true;
        }
        if (("rotateLeft".equals(methodRef.name()) || "rotateRight".equals(methodRef.name()))
            && "(JI)J".equals(methodRef.descriptor())) {
            final IrExpression distance = popInt(classFile, method, stack);
            final IrExpression value = popLong(classFile, method, stack);
            final String symbol;
            if ("rotateLeft".equals(methodRef.name())) {
                symbol = "javan_long_rotate_left";
            } else {
                symbol = "javan_long_rotate_right";
            }
            stack.add(StackValue.longExpression(IrExpression.longCall(symbol, List.of(value, distance))));
            return true;
        }
        if (("highestOneBit".equals(methodRef.name()) || "lowestOneBit".equals(methodRef.name())
            || "reverse".equals(methodRef.name()) || "reverseBytes".equals(methodRef.name())
            || "signum".equals(methodRef.name()))
            && (("signum".equals(methodRef.name()) && "(J)I".equals(methodRef.descriptor()))
                || (!"signum".equals(methodRef.name()) && "(J)J".equals(methodRef.descriptor())))) {
            final String symbol;
            if ("highestOneBit".equals(methodRef.name())) {
                symbol = "javan_long_highest_one_bit";
            } else if ("lowestOneBit".equals(methodRef.name())) {
                symbol = "javan_long_lowest_one_bit";
            } else if ("reverse".equals(methodRef.name())) {
                symbol = "javan_long_reverse";
            } else if ("reverseBytes".equals(methodRef.name())) {
                symbol = "javan_long_reverse_bytes";
            } else {
                symbol = "javan_long_signum";
            }
            final IrExpression value = popLong(classFile, method, stack);
            if ("signum".equals(methodRef.name())) {
                stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value))));
            } else {
                stack.add(StackValue.longExpression(IrExpression.longCall(symbol, List.of(value))));
            }
            return true;
        }
        if (("bitCount".equals(methodRef.name()) || "numberOfLeadingZeros".equals(methodRef.name())
            || "numberOfTrailingZeros".equals(methodRef.name())) && "(J)I".equals(methodRef.descriptor())) {
            final String symbol;
            if ("bitCount".equals(methodRef.name())) {
                symbol = "javan_long_bit_count";
            } else if ("numberOfLeadingZeros".equals(methodRef.name())) {
                symbol = "javan_long_leading_zeros";
            } else {
                symbol = "javan_long_trailing_zeros";
            }
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(popLong(classFile, method, stack)))));
            return true;
        }
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
        if ("floatToIntBits".equals(methodRef.name()) && "(F)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_float_to_int_bits", List.of(popFloat(classFile, method, stack)))));
            return true;
        }
        if ("isNaN".equals(methodRef.name()) && "(F)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_float_is_nan", List.of(popFloat(classFile, method, stack)))));
            return true;
        }
        if ("isInfinite".equals(methodRef.name()) && "(F)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_float_is_infinite", List.of(popFloat(classFile, method, stack)))));
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
        if ("doubleToRawLongBits".equals(methodRef.name()) && "(D)J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                "javan_double_to_raw_long_bits", List.of(popDouble(classFile, method, stack)))));
            return true;
        }
        if ("doubleToLongBits".equals(methodRef.name()) && "(D)J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                "javan_double_to_long_bits", List.of(popDouble(classFile, method, stack)))));
            return true;
        }
        if ("isNaN".equals(methodRef.name()) && "(D)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_double_is_nan", List.of(popDouble(classFile, method, stack)))));
            return true;
        }
        if ("isInfinite".equals(methodRef.name()) && "(D)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_double_is_infinite", List.of(popDouble(classFile, method, stack)))));
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
        if ("offsetByCodePoints".equals(methodRef.name()) && "([CIIII)I".equals(methodRef.descriptor())) {
            final IrExpression distance = popInt(classFile, method, stack);
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression count = popInt(classFile, method, stack);
            final IrExpression start = popInt(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_character_array_offset_by_code_points", List.of(value, start, count, index, distance)
            )));
            return true;
        }
        if ("codePointCount".equals(methodRef.name()) && "([CII)I".equals(methodRef.descriptor())) {
            final IrExpression count = popInt(classFile, method, stack);
            final IrExpression offset = popInt(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_character_array_code_point_count", List.of(value, offset, count)
            )));
            return true;
        }
        if (("codePointAt".equals(methodRef.name()) || "codePointBefore".equals(methodRef.name()))
            && "([CII)I".equals(methodRef.descriptor())) {
            final IrExpression bound = popInt(classFile, method, stack);
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            final String symbol = "codePointAt".equals(methodRef.name())
                ? "javan_character_array_code_point_at_limit" : "javan_character_array_code_point_before_start";
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value, index, bound))));
            return true;
        }
        if (("codePointAt".equals(methodRef.name()) || "codePointBefore".equals(methodRef.name()))
            && "([CI)I".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            final String symbol = "codePointAt".equals(methodRef.name())
                ? "javan_character_array_code_point_at" : "javan_character_array_code_point_before";
            stack.add(StackValue.intExpression(IrExpression.intCall(symbol, List.of(value, index))));
            return true;
        }
        if ("toChars".equals(methodRef.name()) && "(I)[C".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_character_to_chars", List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        if ("toChars".equals(methodRef.name()) && "(I[CI)I".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            final IrExpression destination = popObject(classFile, method, stack);
            final IrExpression value = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_character_write_chars", List.of(value, destination, index)
            )));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "(I)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_character_code_point_to_string", List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "(C)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_char", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(C)Ljava/lang/Character;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_character_value_of", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("highSurrogate".equals(methodRef.name()) && "(I)C".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_high_surrogate", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("lowSurrogate".equals(methodRef.name()) && "(I)C".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_low_surrogate", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("reverseBytes".equals(methodRef.name()) && "(C)C".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_reverse_bytes", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("hashCode".equals(methodRef.name()) && "(C)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_hash_code", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("compare".equals(methodRef.name()) && "(CC)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_compare", List.of(left, right))));
            return true;
        }
        if ("isValidCodePoint".equals(methodRef.name()) && "(I)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_valid_code_point", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isBmpCodePoint".equals(methodRef.name()) && "(I)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_bmp_code_point", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isSupplementaryCodePoint".equals(methodRef.name()) && "(I)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_supplementary_code_point", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("charCount".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_char_count", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isHighSurrogate".equals(methodRef.name()) && "(C)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_high_surrogate", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isLowSurrogate".equals(methodRef.name()) && "(C)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_low_surrogate", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isSurrogate".equals(methodRef.name()) && "(C)Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_surrogate", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isSurrogatePair".equals(methodRef.name()) && "(CC)Z".equals(methodRef.descriptor())) {
            final IrExpression low = popInt(classFile, method, stack);
            final IrExpression high = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_surrogate_pair", List.of(high, low))));
            return true;
        }
        if ("toCodePoint".equals(methodRef.name()) && "(CC)I".equals(methodRef.descriptor())) {
            final IrExpression low = popInt(classFile, method, stack);
            final IrExpression high = popInt(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_to_code_point", List.of(high, low))));
            return true;
        }
        if ("isISOControl".equals(methodRef.name())
            && ("(C)Z".equals(methodRef.descriptor()) || "(I)Z".equals(methodRef.descriptor()))) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_is_iso_control", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("isSpaceChar".equals(methodRef.name())
            && ("(C)Z".equals(methodRef.descriptor()) || "(I)Z".equals(methodRef.descriptor()))) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_character_is_space_char",
                List.of(popInt(classFile, method, stack))
            )));
            return true;
        }
        if ("isWhitespace".equals(methodRef.name())
            && ("(C)Z".equals(methodRef.descriptor()) || "(I)Z".equals(methodRef.descriptor()))) {
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
        if ("identityHashCode".equals(methodRef.name()) && "(Ljava/lang/Object;)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_record_reference_identity_hash_code",
                List.of(popObject(classFile, method, stack)))));
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

    private static void lowerSecureRandomSeedCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final boolean instanceCall
    ) {
        final IrExpression length = popInt(classFile, method, stack);
        final IrExpression receiver = instanceCall ? popObject(classFile, method, stack) : IrExpression.objectNull();
        final String receiverLocal = instanceCall ? newObjectLocal(localDeclarations) : "";
        if (instanceCall) {
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
        }
        final int lengthIndex = localDeclarations.size();
        final String lengthLocal = "int" + lengthIndex;
        localDeclarations.put(Integer.MIN_VALUE + lengthIndex, new IrLocal(IrType.INT, lengthLocal));
        instructions.add(IrInstruction.assignInt(lengthLocal, length));
        final List<StackValue> successStack = List.copyOf(stack);
        if (instanceCall) {
            final String receiverPresent = "label_secure_random_seed_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
                IrExpression.stringLiteral("SecureRandom"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
        }
        final String lengthValid = "label_secure_random_seed_length_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(lengthValid,
            IrExpression.intComparison(">=", IrExpression.intLocal(lengthLocal), IrExpression.intLiteral(0))));
        routePendingPlatformException(classFile, method, instruction, instructions, stack,
            pendingExceptionHandlerStacks, sourceLines, "java/lang/IllegalArgumentException",
            IrExpression.stringLiteral("seed length must not be negative"));
        instructions.add(IrInstruction.label(lengthValid));
        stack.addAll(successStack);
        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.callStaticVoid("javan_secure_random_seed_into",
            List.of(IrExpression.objectLocalAddress(resultLocal), IrExpression.intLocal(lengthLocal))));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
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

    static void pushLongCall(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        final String localName = "long" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.LONG, localName));
        instructions.add(IrInstruction.assignLong(localName, IrExpression.longCall(symbol, arguments)));
        stack.add(StackValue.longExpression(IrExpression.longLocal(localName)));
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

    private static boolean lowerBase64InstanceCall(
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
        final boolean encoder = "java/util/Base64$Encoder".equals(methodRef.owner())
            && ("encode".equals(methodRef.name()) && "([B)[B".equals(methodRef.descriptor())
                || "encodeToString".equals(methodRef.name()) && "([B)Ljava/lang/String;".equals(methodRef.descriptor()));
        final boolean decoder = "java/util/Base64$Decoder".equals(methodRef.owner())
            && "decode".equals(methodRef.name())
            && ("([B)[B".equals(methodRef.descriptor())
                || "(Ljava/lang/String;)[B".equals(methodRef.descriptor()));
        if (!encoder && !decoder) {
            return false;
        }

        final IrExpression input = popObject(classFile, method, stack);
        final IrExpression receiver = popObject(classFile, method, stack);
        final String receiverLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
        final String inputLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(inputLocal, input));
        final List<StackValue> successStack = List.copyOf(stack);

        final String receiverPresent = "label_base64_receiver_present_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            receiverPresent,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())
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
            IrExpression.stringLiteral("Base64 codec")
        );
        instructions.add(IrInstruction.label(receiverPresent));
        stack.addAll(successStack);

        final String inputPresent = "label_base64_input_present_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            inputPresent,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(inputLocal), IrExpression.objectNull())
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
            IrExpression.stringLiteral("src")
        );
        instructions.add(IrInstruction.label(inputPresent));
        stack.addAll(successStack);

        if (decoder) {
            final String statusLocal = "int" + localDeclarations.size();
            localDeclarations.put(
                Integer.MIN_VALUE + localDeclarations.size(),
                new IrLocal(IrType.INT, statusLocal)
            );
            final boolean stringInput = "(Ljava/lang/String;)[B".equals(methodRef.descriptor());
            instructions.add(IrInstruction.assignInt(
                statusLocal,
                IrExpression.intCall(
                    stringInput ? "javan_base64_decode_string_status" : "javan_base64_decode_bytes_status",
                    List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(inputLocal))
                )
            ));
            final String validInput = "label_base64_input_valid_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(
                validInput,
                IrExpression.intComparison("==", IrExpression.intLocal(statusLocal), IrExpression.intLiteral(0))
            ));
            routePendingPlatformException(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                sourceLines,
                "java/lang/IllegalArgumentException",
                IrExpression.stringLiteral("Invalid Base64 input")
            );
            instructions.add(IrInstruction.label(validInput));
            stack.addAll(successStack);
        }

        final String symbol;
        if (encoder) {
            symbol = "encodeToString".equals(methodRef.name())
                ? "javan_base64_encode_string"
                : "javan_base64_encode_bytes";
        } else {
            symbol = "(Ljava/lang/String;)[B".equals(methodRef.descriptor())
                ? "javan_base64_decode_string"
                : "javan_base64_decode_bytes";
        }
        pushObjectCall(
            instructions,
            stack,
            localDeclarations,
            symbol,
            List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(inputLocal))
        );
        return true;
    }

    static boolean lowerResourceAsStream(
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
        if (!"(Ljava/lang/String;)Ljava/io/InputStream;".equals(methodRef.descriptor())) {
            return false;
        }
        final boolean system = instruction.opcode() == 184 && "java/lang/ClassLoader".equals(methodRef.owner())
            && "getSystemResourceAsStream".equals(methodRef.name());
        final boolean classResource = instruction.opcode() == 182 && "java/lang/Class".equals(methodRef.owner())
            && "getResourceAsStream".equals(methodRef.name());
        final boolean loaderResource = instruction.opcode() == 182 && "java/lang/ClassLoader".equals(methodRef.owner())
            && "getResourceAsStream".equals(methodRef.name());
        if (!system && !classResource && !loaderResource) {
            return false;
        }

        final IrExpression name = popObject(classFile, method, stack);
        final IrExpression receiver = system ? IrExpression.objectNull() : popObject(classFile, method, stack);
        if (classResource && name.kind() == IrExpression.Kind.STRING_LITERAL
            && receiver.kind() == IrExpression.Kind.CALL && "javan_runtime_class_literal".equals(receiver.value())) {
            final String result = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(result,
                IrExpression.objectCall("javan_class_resource_as_stream", List.of(receiver, name))));
            stack.add(StackValue.resourceInputStream(IrExpression.objectLocal(result)));
            return true;
        }
        final String receiverLocal = system ? "" : newObjectLocal(localDeclarations);
        if (!system) {
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
        }
        final String nameLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(nameLocal, name));
        final List<StackValue> successStack = List.copyOf(stack);
        if (!system) {
            final String receiverPresent = "label_resource_receiver_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
                IrExpression.stringLiteral("resource receiver"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
        }
        final String namePresent = "label_resource_name_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(namePresent,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(nameLocal), IrExpression.objectNull())));
        routePendingPlatformException(classFile, method, instruction, instructions, stack,
            pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
            IrExpression.stringLiteral("name"));
        instructions.add(IrInstruction.label(namePresent));
        stack.addAll(successStack);

        final String result = newObjectLocal(localDeclarations);
        final String function = system ? "javan_loader_resource_as_stream"
            : classResource ? "javan_class_resource_as_stream" : "javan_class_loader_resource_as_stream";
        final List<IrExpression> arguments = system
            ? List.of(IrExpression.objectLocal(nameLocal))
            : List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(nameLocal));
        instructions.add(IrInstruction.assignObject(result, IrExpression.objectCall(function, arguments)));
        stack.add(StackValue.resourceInputStream(IrExpression.objectLocal(result)));
        return true;
    }

    private static boolean lowerClassForName(
        final Map<String, ClassFile> classes,
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
        if (!"java/lang/Class".equals(methodRef.owner())
            || !"forName".equals(methodRef.name())
            || !("(Ljava/lang/String;)Ljava/lang/Class;".equals(methodRef.descriptor())
                || "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;".equals(methodRef.descriptor()))) {
            return false;
        }

        final boolean explicitLoader = "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;".equals(methodRef.descriptor());
        final IrExpression loader = explicitLoader
            ? popObject(classFile, method, stack)
            : IrExpression.objectCall("javan_class_loader_system", List.of());
        final IrExpression initialize = explicitLoader ? popInt(classFile, method, stack) : IrExpression.intLiteral(1);
        final IrExpression name = popObject(classFile, method, stack);
        final String nameLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(nameLocal, name));
        final int initializeIndex = localDeclarations.size();
        final String initializeLocal = "int" + initializeIndex;
        localDeclarations.put(Integer.MIN_VALUE + initializeIndex, new IrLocal(IrType.INT, initializeLocal));
        instructions.add(IrInstruction.assignInt(initializeLocal, initialize));
        final String loaderLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(loaderLocal, loader));
        final List<StackValue> successStack = List.copyOf(stack);

        final String namePresent = "label_class_for_name_present_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            namePresent,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(nameLocal), IrExpression.objectNull())
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
            IrExpression.stringLiteral("name")
        );
        instructions.add(IrInstruction.label(namePresent));
        stack.addAll(successStack);

        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_generated_class_for_name", List.of(
                IrExpression.objectLocal(nameLocal), IrExpression.intLocal(initializeLocal), IrExpression.objectLocal(loaderLocal)
            ))
        ));
        BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(
            classes, method, instruction, instructions, List.of("java/lang/Error"),
            pendingExceptionHandlerStacks, "label_class_for_name_initialized_" + instruction.offset());
        final String classFound = "label_class_for_name_found_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            classFound,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(resultLocal), IrExpression.objectNull())
        ));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/ClassNotFoundException",
            IrExpression.objectLocal(nameLocal)
        );
        instructions.add(IrInstruction.label(classFound));
        stack.addAll(successStack);
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
        return true;
    }

    private static boolean lowerMethodMetadataCall(
        final Map<String, ClassFile> classes,
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
        if ("java/lang/Class".equals(methodRef.owner()) && "getDeclaredFields".equals(methodRef.name())
            && "()[Ljava/lang/reflect/Field;".equals(methodRef.descriptor())) {
            final String classLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(classLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String present = "label_fields_class_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(present,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(classLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("class"));
            instructions.add(IrInstruction.label(present));
            stack.addAll(successStack);
            final String resultLocal = newObjectLocal(localDeclarations);
            final String statusLocal = "int" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.INT, statusLocal));
            instructions.add(IrInstruction.assignInt(statusLocal, IrExpression.intCall(
                "javan_generated_class_get_declared_fields",
                List.of(IrExpression.objectLocalAddress(resultLocal), IrExpression.objectLocal(classLocal)))));
            final String supported = "label_fields_supported_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(supported,
                IrExpression.intComparison(">=", IrExpression.intLocal(statusLocal), IrExpression.intLiteral(0))));
            routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                sourceLines, "java/lang/UnsupportedOperationException", IrExpression.stringLiteral("field enumeration target"));
            instructions.add(IrInstruction.label(supported));
            stack.addAll(successStack);
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return true;
        }
        if ("java/lang/Class".equals(methodRef.owner()) && "getDeclaredField".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(methodRef.descriptor())) {
            final String nameLocal = newObjectLocal(localDeclarations);
            final String classLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(nameLocal, popObject(classFile, method, stack)));
            instructions.add(IrInstruction.assignObject(classLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String classPresent = "label_field_lookup_class_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(classPresent,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(classLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("class"));
            instructions.add(IrInstruction.label(classPresent));
            stack.addAll(successStack);
            final String namePresent = "label_field_lookup_name_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(namePresent,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(nameLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("name"));
            instructions.add(IrInstruction.label(namePresent));
            stack.addAll(successStack);
            final String resultLocal = newObjectLocal(localDeclarations);
            final String statusLocal = "int" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.INT, statusLocal));
            instructions.add(IrInstruction.assignInt(statusLocal, IrExpression.intCall(
                "javan_generated_class_get_declared_field",
                List.of(IrExpression.objectLocalAddress(resultLocal), IrExpression.objectLocal(classLocal),
                    IrExpression.objectLocal(nameLocal)))));
            final String supported = "label_field_lookup_supported_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(supported,
                IrExpression.intComparison(">=", IrExpression.intLocal(statusLocal), IrExpression.intLiteral(0))));
            routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                sourceLines, "java/lang/UnsupportedOperationException", IrExpression.stringLiteral("platform field metadata"));
            instructions.add(IrInstruction.label(supported));
            stack.addAll(successStack);
            final String found = "label_field_lookup_found_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(found,
                IrExpression.objectComparison("!=", IrExpression.objectLocal(resultLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                sourceLines, "java/lang/NoSuchFieldException", IrExpression.objectLocal(nameLocal));
            instructions.add(IrInstruction.label(found));
            stack.addAll(successStack);
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return true;
        }
        if ("java/lang/reflect/Constructor".equals(methodRef.owner())
            && "newInstance".equals(methodRef.name())
            && "([Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final String argumentsLocal = newObjectLocal(localDeclarations);
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(argumentsLocal, popObject(classFile, method, stack)));
            instructions.add(IrInstruction.assignObject(receiverLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_constructor_invoke_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
                IrExpression.stringLiteral("constructor"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_generated_constructor_invoke", List.of(
                IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(argumentsLocal),
                IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                IrExpression.stringLiteral(classFile.nestHost().replace('/', '.'))));
            return true;
        }
        if ("java/lang/Class".equals(methodRef.owner())
            && ("getDeclaredConstructor".equals(methodRef.name()) || "getConstructor".equals(methodRef.name()))
            && "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;".equals(methodRef.descriptor())) {
            final String parametersLocal = newObjectLocal(localDeclarations);
            final String classLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(parametersLocal, popObject(classFile, method, stack)));
            instructions.add(IrInstruction.assignObject(classLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String classPresent = "label_constructor_lookup_class_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(classPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(classLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException",
                IrExpression.stringLiteral("class"));
            instructions.add(IrInstruction.label(classPresent));
            stack.addAll(successStack);
            final String resultLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall(
                "javan_generated_class_get_declared_constructor",
                List.of(IrExpression.objectLocal(classLocal), IrExpression.objectLocal(parametersLocal),
                    IrExpression.intLiteral("getConstructor".equals(methodRef.name()) ? 1 : 0)))));
            BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(classes, method, instruction, instructions,
                List.of("java/lang/UnsupportedOperationException"), pendingExceptionHandlerStacks,
                "label_constructor_lookup_pending_continue_" + instruction.offset());
            final String found = "label_constructor_lookup_found_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(found, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(resultLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NoSuchMethodException",
                IrExpression.stringLiteral("constructor"));
            instructions.add(IrInstruction.label(found));
            stack.addAll(successStack);
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return true;
        }
        if ("java/lang/reflect/Method".equals(methodRef.owner())
            && "invoke".equals(methodRef.name())
            && "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final String argumentsLocal = newObjectLocal(localDeclarations);
            final String targetLocal = newObjectLocal(localDeclarations);
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(
                argumentsLocal, popObject(classFile, method, stack)
            ));
            instructions.add(IrInstruction.assignObject(targetLocal, popObject(classFile, method, stack)));
            instructions.add(IrInstruction.assignObject(receiverLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_method_invoke_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(
                receiverPresent,
                IrExpression.objectComparison(
                    "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull()
                )
            ));
            routePendingPlatformException(
                classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
                "java/lang/NullPointerException", IrExpression.stringLiteral("method")
            );
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_generated_method_invoke",
                List.of(
                    IrExpression.objectLocal(receiverLocal),
                    IrExpression.objectLocal(targetLocal),
                    IrExpression.objectLocal(argumentsLocal),
                    IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                    IrExpression.stringLiteral(classFile.nestHost().replace('/', '.'))
                )
            );
            return true;
        }
        if ("java/lang/reflect/Field".equals(methodRef.owner()) && "getInt".equals(methodRef.name())
            && "(Ljava/lang/Object;)I".equals(methodRef.descriptor())) {
            final String targetLocal = newObjectLocal(localDeclarations);
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(targetLocal, popObject(classFile, method, stack)));
            instructions.add(IrInstruction.assignObject(receiverLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_field_get_int_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("field"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            pushIntCall(instructions, stack, localDeclarations, "javan_generated_field_get_int", List.of(
                IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(targetLocal),
                IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                IrExpression.stringLiteral(classFile.nestHost().replace('/', '.'))));
            BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(classes, method, instruction, instructions,
                List.of("java/lang/NullPointerException", "java/lang/IllegalArgumentException",
                    "java/lang/IllegalAccessException", "java/lang/UnsupportedOperationException"),
                pendingExceptionHandlerStacks, "label_field_get_int_pending_continue_" + instruction.offset());
            return true;
        }
        if ("java/lang/reflect/Field".equals(methodRef.owner()) && "getLong".equals(methodRef.name())
            && "(Ljava/lang/Object;)J".equals(methodRef.descriptor())) {
            final String targetLocal = newObjectLocal(localDeclarations);
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(targetLocal, popObject(classFile, method, stack)));
            instructions.add(IrInstruction.assignObject(receiverLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_field_get_long_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("field"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            pushLongCall(instructions, stack, localDeclarations, "javan_generated_field_get_long", List.of(
                IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(targetLocal),
                IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                IrExpression.stringLiteral(classFile.nestHost().replace('/', '.'))));
            BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(classes, method, instruction, instructions,
                List.of("java/lang/NullPointerException", "java/lang/IllegalArgumentException",
                    "java/lang/IllegalAccessException", "java/lang/UnsupportedOperationException"),
                pendingExceptionHandlerStacks, "label_field_get_long_pending_continue_" + instruction.offset());
            return true;
        }
        if ("java/lang/reflect/Field".equals(methodRef.owner()) && "setInt".equals(methodRef.name())
            && "(Ljava/lang/Object;I)V".equals(methodRef.descriptor())) {
            final IrExpression value = popInt(classFile, method, stack);
            final IrExpression target = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String receiverLocal = newObjectLocal(localDeclarations);
            final String targetLocal = newObjectLocal(localDeclarations);
            final String valueLocal = "int" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.INT, valueLocal));
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
            instructions.add(IrInstruction.assignObject(targetLocal, target));
            instructions.add(IrInstruction.assignInt(valueLocal, value));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_field_set_int_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("field"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            instructions.add(IrInstruction.callStaticVoid("javan_generated_field_set_int", List.of(
                IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(targetLocal),
                IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                IrExpression.stringLiteral(classFile.nestHost().replace('/', '.')), IrExpression.intLocal(valueLocal))));
            BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(classes, method, instruction, instructions,
                List.of("java/lang/NullPointerException", "java/lang/IllegalArgumentException",
                    "java/lang/IllegalAccessException", "java/lang/UnsupportedOperationException"),
                pendingExceptionHandlerStacks, "label_field_set_int_pending_continue_" + instruction.offset());
            return true;
        }
        if ("java/lang/reflect/Field".equals(methodRef.owner()) && "setLong".equals(methodRef.name())
            && "(Ljava/lang/Object;J)V".equals(methodRef.descriptor())) {
            final IrExpression value = popLong(classFile, method, stack);
            final IrExpression target = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String receiverLocal = newObjectLocal(localDeclarations);
            final String targetLocal = newObjectLocal(localDeclarations);
            final String valueLocal = "long" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.LONG, valueLocal));
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
            instructions.add(IrInstruction.assignObject(targetLocal, target));
            instructions.add(IrInstruction.assignLong(valueLocal, value));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_field_set_long_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("field"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            instructions.add(IrInstruction.callStaticVoid("javan_generated_field_set_long", List.of(
                IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(targetLocal),
                IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                IrExpression.stringLiteral(classFile.nestHost().replace('/', '.')), IrExpression.longLocal(valueLocal))));
            BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(classes, method, instruction, instructions,
                List.of("java/lang/NullPointerException", "java/lang/IllegalArgumentException",
                    "java/lang/IllegalAccessException", "java/lang/UnsupportedOperationException"),
                pendingExceptionHandlerStacks, "label_field_set_long_pending_continue_" + instruction.offset());
            return true;
        }
        if ("java/lang/reflect/Field".equals(methodRef.owner()) && "setDouble".equals(methodRef.name())
            && "(Ljava/lang/Object;D)V".equals(methodRef.descriptor())) {
            final IrExpression value = popDouble(classFile, method, stack);
            final IrExpression target = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            final String receiverLocal = newObjectLocal(localDeclarations);
            final String targetLocal = newObjectLocal(localDeclarations);
            final String valueLocal = "double" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.DOUBLE, valueLocal));
            instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
            instructions.add(IrInstruction.assignObject(targetLocal, target));
            instructions.add(IrInstruction.assignDouble(valueLocal, value));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_field_set_double_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(receiverPresent, IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull())));
            routePendingPlatformException(classFile, method, instruction, instructions, stack,
                pendingExceptionHandlerStacks, sourceLines, "java/lang/NullPointerException", IrExpression.stringLiteral("field"));
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            instructions.add(IrInstruction.callStaticVoid("javan_generated_field_set_double", List.of(
                IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(targetLocal),
                IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                IrExpression.stringLiteral(classFile.nestHost().replace('/', '.')), IrExpression.doubleLocal(valueLocal))));
            BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(classes, method, instruction, instructions,
                List.of("java/lang/NullPointerException", "java/lang/IllegalArgumentException",
                    "java/lang/IllegalAccessException", "java/lang/UnsupportedOperationException"),
                pendingExceptionHandlerStacks, "label_field_set_double_pending_continue_" + instruction.offset());
            return true;
        }
        if (methodAccessCall(methodRef)) {
            return lowerMethodAccessCall(
                classFile, method, instruction, methodRef, instructions, stack, localDeclarations,
                pendingExceptionHandlerStacks, sourceLines
            );
        }
        if ("java/lang/reflect/Method".equals(methodRef.owner())
            || "java/lang/reflect/Constructor".equals(methodRef.owner())
            || "java/lang/reflect/Field".equals(methodRef.owner())
            || "java/lang/reflect/Executable".equals(methodRef.owner())
            || "java/lang/reflect/Member".equals(methodRef.owner())) {
            final String runtimeHelper = methodMetadataRuntimeHelper(methodRef);
            if (runtimeHelper == null) {
                return false;
            }
            final String receiverLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(receiverLocal, popObject(classFile, method, stack)));
            final List<StackValue> successStack = List.copyOf(stack);
            final String receiverPresent = "label_method_metadata_receiver_present_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(
                receiverPresent,
                IrExpression.objectComparison(
                    "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull()
                )
            ));
            routePendingPlatformException(
                classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
                "java/lang/NullPointerException", IrExpression.stringLiteral("method")
            );
            instructions.add(IrInstruction.label(receiverPresent));
            stack.addAll(successStack);
            if ("java/lang/reflect/Field".equals(methodRef.owner()) && "getType".equals(methodRef.name())) {
                final String typeSupported = "label_field_type_supported_" + instruction.offset();
                instructions.add(IrInstruction.branchIf(typeSupported, IrExpression.intComparison("!=",
                    IrExpression.intCall("javan_field_type_supported", List.of(IrExpression.objectLocal(receiverLocal))),
                    IrExpression.intLiteral(0))));
                routePendingPlatformException(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks,
                    sourceLines, "java/lang/UnsupportedOperationException", IrExpression.stringLiteral("field type metadata"));
                instructions.add(IrInstruction.label(typeSupported));
                stack.addAll(successStack);
            }
            if ("getParameterCount".equals(methodRef.name()) || "getModifiers".equals(methodRef.name())) {
                stack.add(StackValue.intExpression(IrExpression.intCall(
                    runtimeHelper, List.of(IrExpression.objectLocal(receiverLocal))
                )));
            } else {
                pushObjectCall(
                    instructions,
                    stack,
                    localDeclarations,
                    runtimeHelper,
                    List.of(IrExpression.objectLocal(receiverLocal))
                );
            }
            return true;
        }
        final boolean declaredLookup = "java/lang/Class".equals(methodRef.owner())
            && "getDeclaredMethod".equals(methodRef.name())
            && "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(methodRef.descriptor());
        final boolean publicLookup = "java/lang/Class".equals(methodRef.owner())
            && "getMethod".equals(methodRef.name())
            && "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(methodRef.descriptor());
        if (!declaredLookup && !publicLookup) {
            return false;
        }

        final String parametersLocal = newObjectLocal(localDeclarations);
        final String nameLocal = newObjectLocal(localDeclarations);
        final String classLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            parametersLocal, popObject(classFile, method, stack)
        ));
        instructions.add(IrInstruction.assignObject(nameLocal, popObject(classFile, method, stack)));
        instructions.add(IrInstruction.assignObject(classLocal, popObject(classFile, method, stack)));
        final List<StackValue> successStack = List.copyOf(stack);

        final String classPresent = "label_method_lookup_class_present_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            classPresent,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(classLocal), IrExpression.objectNull())
        ));
        routePendingPlatformException(
            classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
            "java/lang/NullPointerException", IrExpression.stringLiteral("class")
        );
        instructions.add(IrInstruction.label(classPresent));
        stack.addAll(successStack);

        final String namePresent = "label_method_lookup_name_present_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            namePresent,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(nameLocal), IrExpression.objectNull())
        ));
        routePendingPlatformException(
            classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
            "java/lang/NullPointerException", IrExpression.stringLiteral("name")
        );
        instructions.add(IrInstruction.label(namePresent));
        stack.addAll(successStack);

        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall(
                declaredLookup
                    ? "javan_generated_class_get_declared_method"
                    : "javan_generated_class_get_method",
                List.of(
                    IrExpression.objectLocal(classLocal),
                    IrExpression.objectLocal(nameLocal),
                    IrExpression.objectLocal(parametersLocal)
                )
            )
        ));
        final String found = "label_method_lookup_found_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            found,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(resultLocal), IrExpression.objectNull())
        ));
        routePendingPlatformException(
            classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
            "java/lang/NoSuchMethodException", IrExpression.objectLocal(nameLocal)
        );
        instructions.add(IrInstruction.label(found));
        stack.addAll(successStack);
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
        return true;
    }

    private static boolean lowerMethodAccessCall(
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
        final String targetLocal;
        final String accessibleLocal;
        if ("canAccess".equals(methodRef.name())) {
            targetLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(targetLocal, popObject(classFile, method, stack)));
            accessibleLocal = null;
        } else if ("setAccessible".equals(methodRef.name())) {
            targetLocal = null;
            accessibleLocal = "int" + localDeclarations.size();
            localDeclarations.put(
                Integer.MIN_VALUE + localDeclarations.size(),
                new IrLocal(IrType.INT, accessibleLocal)
            );
            instructions.add(IrInstruction.assignInt(accessibleLocal, popInt(classFile, method, stack)));
        } else {
            targetLocal = null;
            accessibleLocal = null;
        }
        final String receiverLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(receiverLocal, popObject(classFile, method, stack)));
        final List<StackValue> successStack = List.copyOf(stack);
        final String receiverPresent = "label_method_access_receiver_present_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            receiverPresent,
            IrExpression.objectComparison(
                "!=", IrExpression.objectLocal(receiverLocal), IrExpression.objectNull()
            )
        ));
        routePendingPlatformException(
            classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
            "java/lang/NullPointerException", IrExpression.stringLiteral("method")
        );
        instructions.add(IrInstruction.label(receiverPresent));
        stack.addAll(successStack);

        if ("trySetAccessible".equals(methodRef.name())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_method_set_accessible",
                List.of(
                    IrExpression.objectLocal(receiverLocal),
                    IrExpression.intLiteral(1),
                    IrExpression.stringLiteral(classFile.name().replace('/', '.'))
                )
            )));
            return true;
        }
        if ("isAccessible".equals(methodRef.name())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_method_access_override", List.of(IrExpression.objectLocal(receiverLocal))
            )));
            return true;
        }

        final String statusLocal = "int" + localDeclarations.size();
        localDeclarations.put(
            Integer.MIN_VALUE + localDeclarations.size(),
            new IrLocal(IrType.INT, statusLocal)
        );
        if ("canAccess".equals(methodRef.name())) {
            instructions.add(IrInstruction.assignInt(
                statusLocal,
                IrExpression.intCall(
                    "javan_method_can_access",
                    List.of(
                        IrExpression.objectLocal(receiverLocal),
                        IrExpression.objectLocal(targetLocal),
                        IrExpression.stringLiteral(classFile.name().replace('/', '.')),
                        IrExpression.stringLiteral(classFile.nestHost().replace('/', '.'))
                    )
                )
            ));
            final String validTarget = "label_method_access_target_valid_" + instruction.offset();
            instructions.add(IrInstruction.branchIf(
                validTarget,
                IrExpression.intComparison(">=", IrExpression.intLocal(statusLocal), IrExpression.intLiteral(0))
            ));
            routePendingPlatformException(
                classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
                "java/lang/IllegalArgumentException", IrExpression.stringLiteral("object is not an instance")
            );
            instructions.add(IrInstruction.label(validTarget));
            stack.addAll(successStack);
            stack.add(StackValue.intExpression(IrExpression.intLocal(statusLocal)));
            return true;
        }

        instructions.add(IrInstruction.assignInt(
            statusLocal,
            IrExpression.intCall(
                "javan_method_set_accessible",
                List.of(
                    IrExpression.objectLocal(receiverLocal),
                    IrExpression.intLocal(accessibleLocal),
                    IrExpression.stringLiteral(classFile.name().replace('/', '.'))
                )
            )
        ));
        final String allowed = "label_method_access_override_allowed_" + instruction.offset();
        instructions.add(IrInstruction.branchIf(
            allowed,
            IrExpression.intComparison("!=", IrExpression.intLocal(statusLocal), IrExpression.intLiteral(0))
        ));
        routePendingPlatformException(
            classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines,
            "java/lang/reflect/InaccessibleObjectException", IrExpression.stringLiteral("access is not open")
        );
        instructions.add(IrInstruction.label(allowed));
        stack.addAll(successStack);
        return true;
    }

    private static boolean methodAccessCall(final MethodRef methodRef) {
        if (!"java/lang/reflect/Method".equals(methodRef.owner())
            && !"java/lang/reflect/Constructor".equals(methodRef.owner())
            && !"java/lang/reflect/AccessibleObject".equals(methodRef.owner())) {
            return false;
        }
        return "canAccess".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())
            || "setAccessible".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())
            || "trySetAccessible".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())
            || "isAccessible".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor());
    }

    private static String methodMetadataRuntimeHelper(final MethodRef methodRef) {
        if ("getName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return "javan_method_get_name";
        }
        if ("getDeclaringClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            return "javan_method_get_declaring_class";
        }
        if ("getParameterCount".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            return "javan_method_get_parameter_count";
        }
        if ("getParameterTypes".equals(methodRef.name())
            && "()[Ljava/lang/Class;".equals(methodRef.descriptor())) {
            return "javan_method_get_parameter_types";
        }
        if (("getReturnType".equals(methodRef.name()) || "java/lang/reflect/Field".equals(methodRef.owner())
            && "getType".equals(methodRef.name())) && "()Ljava/lang/Class;".equals(methodRef.descriptor())) {
            return "javan_method_get_return_type";
        }
        if ("getModifiers".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            return "javan_method_get_modifiers";
        }
        return null;
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
        if (JdkCallSupport.isPathStringFactory(methodRef)) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            pushObjectCall(instructions, stack, localDeclarations, "javan_path_of", arguments);
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
            pushLongCall(instructions, stack, localDeclarations, "javan_files_size", arguments);
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
