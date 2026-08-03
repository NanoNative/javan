package javan.codegen;

import javan.analysis.EntryPoint;
import javan.analysis.GeneratedObjectCloneSupport;
import javan.classfile.ClassFile;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.FunctionLambdaUse;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactoryCall;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.classfile.RecordObjectMethodsCall;
import javan.compat.JdkCallSupport;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrMaterializedLambdaTarget;
import javan.ir.IrParameter;
import javan.ir.IrType;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRCollectionSupport.*;
import static javan.codegen.BytecodeToIRInvokeSupport.*;
import static javan.codegen.BytecodeToIRMetadataSupport.*;

final class BytecodeToIRDynamicSupport {
    private static final String MATERIALIZED_LAMBDA_NEW_SYMBOL = "javan_materialized_lambda_new";
    private static final String MATERIALIZED_LAMBDA_NEW_WITH_CAPTURES_SYMBOL = "javan_materialized_lambda_new_with_captures";
    static final String DATE_TIME_FORMATTER_BUILDER_OWNER = "java/time/format/DateTimeFormatterBuilder";
    private static final String DATE_TIME_FORMATTER_OWNER = "java/time/format/DateTimeFormatter";
    private static final String TEXT_STYLE_OWNER = "java/time/format/TextStyle";
    private static final String LOCALE_OWNER = "java/util/Locale";
    private static final String MATERIALIZED_LAMBDA_CAPTURE_SYMBOL = "javan_materialized_lambda_capture";

    private record MaterializedLambdaKey(
        String interfaceOwner,
        String interfaceMethodName,
        String interfaceMethodDescriptor,
        MethodRef implementation,
        int captureCount,
        boolean booleanResult,
        boolean voidResult
    ) {
    }

    private BytecodeToIRDynamicSupport() {
    }

    static void lowerDispatchCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
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
        appendCallResult(instructions, stack, localDeclarations, descriptor.returnType(), dispatchSymbol, arguments);
    }

    static void appendCallResult(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrType returnType,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        if (returnType == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(symbol, arguments));
            return;
        }
        final String localName = Strings2.toAsciiLowerCase(returnType.name()) + localDeclarations.size();
        final IrLocal local = new IrLocal(returnType, localName);
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), local);
        final IrExpression call;
        final StackKind stackKind;
        if (returnType == IrType.INT) {
            call = IrExpression.intCall(symbol, arguments);
            stackKind = StackKind.INT;
        } else if (returnType == IrType.LONG) {
            call = IrExpression.longCall(symbol, arguments);
            stackKind = StackKind.LONG;
        } else if (returnType == IrType.FLOAT) {
            call = IrExpression.floatCall(symbol, arguments);
            stackKind = StackKind.FLOAT;
        } else if (returnType == IrType.DOUBLE) {
            call = IrExpression.doubleCall(symbol, arguments);
            stackKind = StackKind.DOUBLE;
        } else if (returnType == IrType.OBJECT) {
            call = IrExpression.objectCall(symbol, arguments);
            stackKind = StackKind.OBJECT;
        } else {
            throw new IllegalStateException("Unsupported call result type.");
        }
        instructions.add(BytecodeToIRControlFlowSupport.assignLocal(stackKind, localName, call));
        final IrExpression localExpression = BytecodeToIR.localExpression(returnType, local);
        if (returnType == IrType.INT) {
            stack.add(StackValue.intExpression(localExpression));
        } else if (returnType == IrType.LONG) {
            stack.add(StackValue.longExpression(localExpression));
        } else if (returnType == IrType.FLOAT) {
            stack.add(StackValue.floatExpression(localExpression));
        } else if (returnType == IrType.DOUBLE) {
            stack.add(StackValue.doubleExpression(localExpression));
        } else if (returnType == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(localExpression));
        } else {
            throw new IllegalStateException("Unsupported call result type.");
        }
    }

    static IrDispatch dispatch(final String symbol, final MethodDescriptor descriptor, final List<EntryPoint> targets) {
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

    static List<EntryPoint> sortedEntryPointsByClassName(final List<EntryPoint> entries) {
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

    static List<IrExpression> popArguments(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final MethodDescriptor descriptor
    ) {
        return popArguments(classFile, method, stack, descriptor, firstInstruction(method));
    }

    static List<IrExpression> popArguments(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final MethodDescriptor descriptor,
        final Instruction instruction
    ) {
        final List<IrExpression> arguments = new ArrayList<>();
        for (int index = descriptor.parameterTypes().size() - 1; index >= 0; index--) {
            final IrType type = descriptor.parameterTypes().get(index);
            arguments.addFirst(popValue(classFile, method, stack, type, instruction));
        }
        return List.copyOf(arguments);
    }

    static List<IrMaterializedLambdaTarget> functionOrNullTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        final List<IrMaterializedLambdaTarget> result = new ArrayList<>();
        final Map<MaterializedLambdaKey, Integer> targetIds = materializedLambdaTargetIds(classes, reachableMethods);
        for (final Map.Entry<MaterializedLambdaKey, Integer> entry : targetIds.entrySet()) {
            final MaterializedLambdaKey key = entry.getKey();
            result.add(new IrMaterializedLambdaTarget(
                entry.getValue().intValue(),
                key.interfaceOwner(),
                key.interfaceMethodName(),
                key.interfaceMethodDescriptor(),
                symbol(new EntryPoint(
                    key.implementation().owner(),
                    key.implementation().name(),
                    key.implementation().descriptor()
                )),
                key.captureCount(),
                key.booleanResult(),
                key.voidResult()
            ));
        }
        return List.copyOf(result);
    }

    static Map<String, Integer> functionOrNullTargetIds(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        final Map<MaterializedLambdaKey, Integer> targetIds = materializedLambdaTargetIds(classes, reachableMethods);
        for (final Map.Entry<MaterializedLambdaKey, Integer> entry : targetIds.entrySet()) {
            result.put(materializedLambdaKey(entry.getKey()), entry.getValue());
        }
        return Map.copyOf(result);
    }

    static Map<MethodRef, MaterializedLambdaDispatchKind> materializedLambdaMethods(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        final Map<MethodRef, MaterializedLambdaDispatchKind> result = new LinkedHashMap<>();
        final Map<MaterializedLambdaKey, Integer> targetIds = materializedLambdaTargetIds(classes, reachableMethods);
        for (final MaterializedLambdaKey key : targetIds.keySet()) {
            result.put(
                new MethodRef(key.interfaceOwner(), key.interfaceMethodName(), key.interfaceMethodDescriptor()),
                "java/util/function/Supplier".equals(key.interfaceOwner())
                    && "get".equals(key.interfaceMethodName())
                    && "()Ljava/lang/Object;".equals(key.interfaceMethodDescriptor())
                    ? MaterializedLambdaDispatchKind.SUPPLIER
                    : key.voidResult()
                    ? MaterializedLambdaDispatchKind.VOID
                    : key.booleanResult()
                    ? MaterializedLambdaDispatchKind.BOOLEAN
                    : materializedLambdaSingleLongInput(key)
                    ? MaterializedLambdaDispatchKind.LONG_OBJECT
                    : MaterializedLambdaDispatchKind.OBJECT
            );
        }
        return Map.copyOf(result);
    }

    private static boolean materializedLambdaSingleLongInput(final MaterializedLambdaKey key) {
        final List<IrType> parameterTypes = MethodDescriptor.parse(key.interfaceMethodDescriptor()).parameterTypes();
        return parameterTypes.size() == 1 && parameterTypes.getFirst() == IrType.LONG;
    }

    private static Map<MaterializedLambdaKey, Integer> materializedLambdaTargetIds(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        final Map<MaterializedLambdaKey, Integer> result = new LinkedHashMap<>();
        int nextId = 1;
        for (final EntryPoint reachable : reachableMethods) {
            final ClassFile classFile = classes.get(reachable.className());
            if (classFile == null) {
                continue;
            }
            final Optional<MethodInfo> method = classFile.method(reachable.methodName(), reachable.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.opcode() != 186 || instruction.dynamicRef().isEmpty()) {
                    continue;
                }
                final Optional<LambdaMetafactoryCall> lambdaCall = LambdaMetafactoryCall.resolve(instruction.dynamicRef().orElseThrow());
                if (lambdaCall.isEmpty()) {
                    continue;
                }
                final LambdaMetafactoryCall resolved = lambdaCall.orElseThrow();
                final boolean materializedFunction = resolved.isMaterializedFunctionLambda(classes)
                    && FunctionLambdaUse.requiresMaterialization(method.orElseThrow(), instruction)
                    && !FunctionLambdaUse.isProvablyDiscardedZeroCapture(
                        resolved,
                        method.orElseThrow(),
                        instruction
                    );
                final boolean materializedBoundCustom = resolved.isMaterializedBoundCustomObjectLambda(classes);
                final boolean materializedStaticLongCustom =
                    resolved.isZeroCaptureMaterializedLongObjectLambda(classes);
                final boolean materializedCapturedLongCustom =
                    resolved.isMaterializedCapturedLongObjectLambda(classes);
                if (!resolved.isZeroCaptureMaterializedObjectLambda()
                    && !materializedStaticLongCustom
                    && !materializedCapturedLongCustom
                    && !resolved.isZeroCaptureMaterializedBooleanLambda()
                    && !resolved.isMaterializedBiFunctionLambda()
                    && !materializedFunction
                    && !resolved.isMaterializedVoidLambda()
                    && !(resolved.isMaterializedSupplierLambda() && shouldMaterializeSupplierLambda(method.orElseThrow(), instruction))
                    && !materializedBoundCustom) {
                    continue;
                }
                if (!classes.containsKey(resolved.implementation().owner())) {
                    continue;
                }
                final MaterializedLambdaKey key = new MaterializedLambdaKey(
                    resolved.interfaceOwner(),
                    resolved.interfaceMethodName(),
                    resolved.samMethodDescriptor(),
                    resolved.implementation(),
                    resolved.capturedParameterDescriptors().size(),
                    resolved.isZeroCaptureMaterializedBooleanLambda(),
                    resolved.isMaterializedVoidLambda()
                );
                if (!result.containsKey(key)) {
                    result.put(key, Integer.valueOf(nextId));
                    nextId++;
                }
            }
        }
        return Map.copyOf(result);
    }

    private static String materializedLambdaKey(final MaterializedLambdaKey key) {
        return key.interfaceOwner()
            + "#" + key.interfaceMethodName()
            + "#" + key.interfaceMethodDescriptor()
            + "#" + key.implementation().display()
            + "#" + key.captureCount()
            + "#" + (key.booleanResult() ? "1" : "0")
            + "#" + (key.voidResult() ? "1" : "0");
    }

    private static String materializedLambdaKey(final LambdaMetafactoryCall lambdaCall) {
        return lambdaCall.interfaceOwner()
            + "#" + lambdaCall.interfaceMethodName()
            + "#" + lambdaCall.samMethodDescriptor()
            + "#" + lambdaCall.implementation().display()
            + "#" + lambdaCall.capturedParameterDescriptors().size()
            + "#" + (lambdaCall.isZeroCaptureMaterializedBooleanLambda() ? "1" : "0")
            + "#" + (lambdaCall.isMaterializedVoidLambda() ? "1" : "0");
    }

    static void lowerDynamicCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> materializedLambdaTargetIds
    ) {
        if (lowerRecordHashCodeDynamic(classes, classFile, method, instruction, instructions, stack, localDeclarations, dispatches)) {
            return;
        }
        if (lowerRecordEqualsDynamic(classes, classFile, method, instruction, instructions, stack, localDeclarations, dispatches)) {
            return;
        }
        final Optional<DynamicRef> maybeDynamicRef = instruction.dynamicRef();
        if (maybeDynamicRef.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final DynamicRef dynamicRef = maybeDynamicRef.orElseThrow();
        if (lowerLambdaMetafactoryDynamic(classes, classFile, method, instruction, stack, dynamicRef, materializedLambdaTargetIds)) {
            return;
        }
        if (!isSupportedStringConcat(dynamicRef)) {
            throw unsupported(classFile, method, instruction);
        }
        final Optional<List<String>> parameterDescriptors = parameterDescriptors(dynamicRef.descriptor());
        if (parameterDescriptors.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final List<IrExpression> arguments = new ArrayList<>();
        final List<String> descriptors = parameterDescriptors.orElseThrow();
        for (int index = descriptors.size() - 1; index >= 0; index--) {
            arguments.addFirst(popStringConcatArgument(classFile, method, instruction, stack, descriptors.get(index)));
        }
        final Optional<String> recipe = stringConcatRecipe(dynamicRef, arguments.size());
        if (recipe.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        stack.add(StackValue.objectExpression(IrExpression.stringConcat(recipe.orElseThrow(), arguments)));
    }

    static boolean lowerLambdaMetafactoryDynamic(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack,
        final DynamicRef dynamicRef,
        final Map<String, Integer> materializedLambdaTargetIds
    ) {
        final Optional<LambdaMetafactoryCall> lambdaCall = LambdaMetafactoryCall.resolve(dynamicRef);
        if (lambdaCall.isEmpty()) {
            return false;
        }
        final LambdaMetafactoryCall resolved = lambdaCall.orElseThrow();
        final MethodRef implementation = resolved.implementation();
        final boolean materializedFunction = resolved.isMaterializedFunctionLambda(classes)
            && FunctionLambdaUse.requiresMaterialization(method, instruction)
            && !FunctionLambdaUse.isProvablyDiscardedZeroCapture(resolved, method, instruction);
        final boolean materializedBoundCustom = resolved.isMaterializedBoundCustomObjectLambda(classes);
        final boolean materializedStaticLongCustom =
            resolved.isZeroCaptureMaterializedLongObjectLambda(classes);
        final boolean materializedCapturedLongCustom =
            resolved.isMaterializedCapturedLongObjectLambda(classes);
        if (resolved.isZeroCaptureMaterializedObjectLambda()
            || materializedStaticLongCustom
            || materializedCapturedLongCustom
            || resolved.isZeroCaptureMaterializedBooleanLambda()
            || resolved.isMaterializedBiFunctionLambda()
            || materializedFunction
            || resolved.isMaterializedVoidLambda()
            || (resolved.isMaterializedSupplierLambda() && shouldMaterializeSupplierLambda(method, instruction))
            || materializedBoundCustom) {
            final Integer targetId = materializedLambdaTargetIds.get(materializedLambdaKey(resolved));
            if (targetId == null) {
                return false;
            }
            final Optional<List<String>> captureDescriptors = parameterDescriptors(dynamicRef.descriptor());
            if (captureDescriptors.isEmpty()) {
                return false;
            }
            final MethodDescriptor captureDescriptor = MethodDescriptor.parse(dynamicRef.descriptor());
            final List<IrExpression> captures = new ArrayList<>();
            final List<IrType> captureTypes = captureDescriptor.parameterTypes();
            for (int index = captureTypes.size() - 1; index >= 0; index--) {
                if (captureTypes.get(index) != IrType.OBJECT) {
                    return false;
                }
                captures.addFirst(popValue(classFile, method, stack, captureTypes.get(index), instruction));
            }
            if (captures.isEmpty()) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    MATERIALIZED_LAMBDA_NEW_SYMBOL,
                    List.of(IrExpression.intLiteral(targetId.intValue()))
                )));
                return true;
            }
            final List<IrExpression> newArguments = new ArrayList<>();
            newArguments.add(IrExpression.intLiteral(targetId.intValue()));
            newArguments.add(IrExpression.intLiteral(captures.size()));
            newArguments.addAll(captures);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                MATERIALIZED_LAMBDA_NEW_WITH_CAPTURES_SYMBOL,
                newArguments
            )));
            return true;
        }
        if (!resolved.isDirectlyLowerable(classes)) {
            return false;
        }
        if (implementation.owner().startsWith("java/")
            && !(resolved.implementationReferenceKind() == 9
            && "java/util/Map".equals(implementation.owner())
            && "get".equals(implementation.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(implementation.descriptor()))) {
            return false;
        }
        if ((resolved.implementationReferenceKind() == 5 || resolved.implementationReferenceKind() == 6)
            && !classes.containsKey(implementation.owner())) {
            return false;
        }
        final Optional<List<String>> captureDescriptors = parameterDescriptors(dynamicRef.descriptor());
        if (captureDescriptors.isEmpty()) {
            return false;
        }
        if (!supportsLambdaImplementationShape(resolved, captureDescriptors.orElseThrow())) {
            return false;
        }
        final MethodDescriptor captureDescriptor = MethodDescriptor.parse(dynamicRef.descriptor());
        final List<IrExpression> captures = new ArrayList<>();
        final List<IrType> captureTypes = captureDescriptor.parameterTypes();
        for (int index = captureTypes.size() - 1; index >= 0; index--) {
            captures.addFirst(popValue(classFile, method, stack, captureTypes.get(index), instruction));
        }
        final DynamicLambda lambda = new DynamicLambda(
            resolved.interfaceOwner(),
            resolved.interfaceMethodName(),
            implementation.owner(),
            implementation.name(),
            implementation.descriptor(),
            resolved.implementationReferenceKind(),
            resolved.instantiatedMethodDescriptor(),
            List.copyOf(captures)
        );
        if (resolved.isPredicate()) {
            stack.add(StackValue.lambdaPredicate(lambda));
            return true;
        }
        if (resolved.isSupplier()) {
            stack.add(StackValue.lambdaSupplier(lambda));
            return true;
        }
        if (resolved.isFunction()) {
            stack.add(StackValue.lambdaFunction(lambda));
            return true;
        }
        return false;
    }

    private static boolean shouldMaterializeSupplierLambda(
        final MethodInfo method,
        final Instruction instruction
    ) {
        if (method.code().isEmpty()) {
            return true;
        }
        final List<Instruction> bytecode = method.code().orElseThrow().instructions();
        for (int index = 0; index + 1 < bytecode.size(); index++) {
            if (bytecode.get(index).offset() != instruction.offset()) {
                continue;
            }
            final Optional<MethodRef> consumer = bytecode.get(index + 1).methodRef();
            if (consumer.isEmpty()) {
                return true;
            }
            return !isInlineSupplierConsumer(consumer.orElseThrow());
        }
        return true;
    }

    private static boolean isInlineSupplierConsumer(final MethodRef target) {
        if (isSupplierGet(target)) {
            return true;
        }
        if ("java/util/Optional".equals(target.owner())) {
            return ("or".equals(target.name()) && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(target.descriptor()))
                || ("orElseGet".equals(target.name()) && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(target.descriptor()))
                || JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(target);
        }
        return "java/util/Objects".equals(target.owner())
            && "requireNonNullElseGet".equals(target.name())
            && "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(target.descriptor());
    }

    private static boolean supportsLambdaImplementationShape(
        final LambdaMetafactoryCall lambdaCall,
        final List<String> captureDescriptors
    ) {
        final MethodRef implementation = lambdaCall.implementation();
        final Optional<List<String>> implementationParameters = parameterDescriptors(implementation.descriptor());
        if (implementationParameters.isEmpty()) {
            return false;
        }
        final List<String> parameters = implementationParameters.orElseThrow();
        if (lambdaCall.implementationReferenceKind() == 5) {
            if (lambdaCall.isSupplier()) {
                return true;
            }
            if (lambdaCall.isFunction() && captureDescriptors.isEmpty()) {
                final Optional<String> input = lambdaCall.inputDescriptor();
                return parameters.isEmpty()
                    && input.isPresent()
                    && ("L" + implementation.owner() + ";").equals(input.orElseThrow());
            }
            if (!(lambdaCall.isPredicate() || lambdaCall.isFunction())
                || captureDescriptors.isEmpty()
                || parameters.size() != captureDescriptors.size()) {
                return false;
            }
            for (int index = 1; index < captureDescriptors.size(); index++) {
                if (!sameOrObjectCompatible(captureDescriptors.get(index), parameters.get(index - 1))) {
                    return false;
                }
            }
            final Optional<String> input = lambdaCall.inputDescriptor();
            return input.isPresent() && sameOrObjectCompatible(input.orElseThrow(), parameters.getLast());
        }
        if (lambdaCall.implementationReferenceKind() == 6) {
            if (lambdaCall.isSupplier()) {
                if (parameters.size() != captureDescriptors.size()) {
                    return false;
                }
                for (int index = 0; index < captureDescriptors.size(); index++) {
                    if (!sameOrObjectCompatible(captureDescriptors.get(index), parameters.get(index))) {
                        return false;
                    }
                }
                return true;
            }
            if (parameters.size() != captureDescriptors.size() + 1) {
                return false;
            }
            for (int index = 0; index < captureDescriptors.size(); index++) {
                if (!sameOrObjectCompatible(captureDescriptors.get(index), parameters.get(index))) {
                    return false;
                }
            }
            final Optional<String> input = lambdaCall.inputDescriptor();
            return input.isPresent() && sameOrObjectCompatible(input.orElseThrow(), parameters.getLast());
        }
        if (lambdaCall.implementationReferenceKind() == 9) {
            if (!"java/util/Map".equals(implementation.owner())
                || !"get".equals(implementation.name())
                || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(implementation.descriptor())) {
                return false;
            }
            return captureDescriptors.size() == 1 && "Ljava/util/Map;".equals(captureDescriptors.getFirst());
        }
        return false;
    }

    private static boolean sameOrObjectCompatible(final String source, final String target) {
        if (source.equals(target)) {
            return true;
        }
        if ("Ljava/lang/Object;".equals(target) && (source.startsWith("L") || source.startsWith("["))) {
            return true;
        }
        return false;
    }

    static boolean hasTopStackKind(final List<StackValue> stack, final StackKind expected) {
        return !stack.isEmpty() && stack.getLast().kind() == expected;
    }

    static boolean hasFunctionLambdaReceiverOnStack(final List<StackValue> stack) {
        return stack.size() >= 2 && stack.get(stack.size() - 2).kind() == StackKind.LAMBDA_FUNCTION;
    }

    static boolean hasPredicateLambdaReceiverOnStack(final List<StackValue> stack) {
        return stack.size() >= 2 && stack.get(stack.size() - 2).kind() == StackKind.LAMBDA_PREDICATE;
    }

    static boolean isFunctionApply(final MethodRef methodRef) {
        return "java/util/function/Function".equals(methodRef.owner())
            && "apply".equals(methodRef.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor());
    }

    static boolean isSupplierGet(final MethodRef methodRef) {
        return "java/util/function/Supplier".equals(methodRef.owner())
            && "get".equals(methodRef.name())
            && "()Ljava/lang/Object;".equals(methodRef.descriptor());
    }

    static boolean isPredicateTest(final MethodRef methodRef) {
        return "java/util/function/Predicate".equals(methodRef.owner())
            && "test".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    static DynamicLambda popDynamicLambda(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack,
        final StackKind expectedKind,
        final String expectedName
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "Expected " + expectedName + " on the bytecode stack, but stack was empty.");
        }
        final StackValue value = pop(stack);
        if (value.kind() != expectedKind || value.dynamicLambda().isEmpty()) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason(expectedName, value.kind()));
        }
        return value.dynamicLambda().orElseThrow();
    }

    static IrExpression invokePredicateLambdaExpression(final DynamicLambda lambda, final IrExpression argument) {
        if (lambda.implementationReferenceKind() == 5 || lambda.implementationReferenceKind() == 6) {
            final List<IrExpression> arguments = new ArrayList<>(lambda.captures());
            arguments.add(argument);
            return IrExpression.intCall(symbol(new EntryPoint(
                lambda.implementationOwner(),
                lambda.implementationName(),
                lambda.implementationDescriptor()
            )), arguments);
        }
        throw new IllegalArgumentException("Unsupported predicate lambda shape: " + lambda.implementationMethodRef().display());
    }

    static IrExpression invokeSupplierLambdaExpression(final DynamicLambda lambda) {
        if (lambda.implementationReferenceKind() == 5 || lambda.implementationReferenceKind() == 6) {
            return IrExpression.objectCall(symbol(new EntryPoint(
                lambda.implementationOwner(),
                lambda.implementationName(),
                lambda.implementationDescriptor()
            )), lambda.captures());
        }
        throw new IllegalArgumentException("Unsupported supplier lambda shape: " + lambda.implementationMethodRef().display());
    }

    static IrExpression invokeFunctionLambdaExpression(final DynamicLambda lambda, final IrExpression argument) {
        if (lambda.implementationReferenceKind() == 5 || lambda.implementationReferenceKind() == 6) {
            final List<IrExpression> arguments = new ArrayList<>(lambda.captures());
            final IrExpression input = lambda.implementationReferenceKind() == 5 && lambda.captures().isEmpty()
                ? IrExpression.objectCall("javan_objects_require_non_null", List.of(argument))
                : argument;
            arguments.add(input);
            final String target = symbol(new EntryPoint(
                lambda.implementationOwner(),
                lambda.implementationName(),
                lambda.implementationDescriptor()
            ));
            if (lambda.implementationReferenceKind() == 5
                && lambda.captures().isEmpty()
                && "()J".equals(lambda.implementationDescriptor())
                && ("(L" + lambda.implementationOwner() + ";)Ljava/lang/Long;")
                    .equals(lambda.instantiatedMethodDescriptor())) {
                return IrExpression.objectCall(
                    "javan_long_value_of",
                    List.of(IrExpression.longCall(target, arguments))
                );
            }
            return IrExpression.objectCall(target, arguments);
        }
        if (lambda.implementationReferenceKind() == 9
            && "java/util/Map".equals(lambda.implementationOwner())
            && "get".equals(lambda.implementationName())
            && lambda.captures().size() == 1) {
            return IrExpression.objectCall("javan_map_get", List.of(lambda.captures().getFirst(), argument));
        }
        throw new IllegalArgumentException("Unsupported function lambda shape: " + lambda.implementationMethodRef().display());
    }

    static String newObjectLocal(final Map<Integer, IrLocal> localDeclarations) {
        final int localIndex = localDeclarations.size();
        final String localName = "object" + localIndex;
        localDeclarations.put(Integer.MIN_VALUE + localIndex, new IrLocal(IrType.OBJECT, localName));
        return localName;
    }

    static String newIntLocal(final Map<Integer, IrLocal> localDeclarations) {
        final int localIndex = localDeclarations.size();
        final String localName = "int" + localIndex;
        localDeclarations.put(Integer.MIN_VALUE + localIndex, new IrLocal(IrType.INT, localName));
        return localName;
    }

    static boolean lowerRecordHashCodeDynamic(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        if (instruction.dynamicRef().isEmpty()) {
            return false;
        }
        final Optional<RecordObjectMethodsCall> recordCall =
            RecordObjectMethodsCall.resolveHashCode(classFile, method, instruction.dynamicRef().orElseThrow());
        if (recordCall.isEmpty()) {
            return false;
        }
        final IrExpression self = popObject(classFile, method, stack);
        final String selfLocal = newObjectLocal(localDeclarations);
        final String resultLocal = newIntLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(selfLocal, self));
        instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intLiteral(0)));
        final IrExpression materializedSelf = IrExpression.objectLocal(selfLocal);
        for (final RecordObjectMethodsCall.Component component : recordCall.orElseThrow().components()) {
            final javan.classfile.FieldInfo field = component.field();
            final IrExpression fieldHash = component.shape().referenceOwner().isPresent()
                ? lowerRecordReferenceFieldHashCode(classes, classFile, component, materializedSelf, dispatches)
                : recordHashCodeFieldExpression(classFile.name(), field, materializedSelf);
            instructions.add(IrInstruction.assignInt(
                resultLocal,
                IrExpression.intCall("javan_record_hash_combine", List.of(IrExpression.intLocal(resultLocal), fieldHash))
            ));
        }
        stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
        return true;
    }

    private static IrExpression lowerRecordReferenceFieldHashCode(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final RecordObjectMethodsCall.Component component,
        final IrExpression self,
        final Map<String, IrDispatch> dispatches
    ) {
        markRecordReferenceObjectMethodDispatch(classes, component.shape(), dispatches, true);
        return IrExpression.intCall(
            "javan_record_shape_hash_code",
            List.of(
                IrExpression.objectField(classFile.name(), component.field().name(), self),
                IrExpression.stringLiteral(recordShapeEncoding(classes, component.shape()))
            )
        );
    }

    private static IrExpression recordHashCodeFieldExpression(
        final String owner,
        final javan.classfile.FieldInfo field,
        final IrExpression self
    ) {
        return switch (field.descriptor()) {
            case "Z" -> IrExpression.intCall("javan_record_boolean_hash_code", List.of(IrExpression.intField(owner, field.name(), self)));
            case "B", "C", "I", "S" -> IrExpression.intField(owner, field.name(), self);
            case "J" -> IrExpression.intCall("javan_record_long_hash_code", List.of(IrExpression.longField(owner, field.name(), self)));
            case "F" -> IrExpression.intCall("javan_record_float_hash_code", List.of(IrExpression.floatField(owner, field.name(), self)));
            case "D" -> IrExpression.intCall("javan_record_double_hash_code", List.of(IrExpression.doubleField(owner, field.name(), self)));
            default -> IrExpression.intCall(
                "javan_record_reference_identity_hash_code",
                List.of(IrExpression.objectField(owner, field.name(), self))
            );
        };
    }

    static boolean lowerRecordEqualsDynamic(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        if (instruction.dynamicRef().isEmpty()) {
            return false;
        }
        final Optional<RecordObjectMethodsCall> recordCall =
            RecordObjectMethodsCall.resolve(classFile, method, instruction.dynamicRef().orElseThrow());
        if (recordCall.isEmpty()) {
            return false;
        }
        final IrExpression other = popObject(classFile, method, stack);
        final IrExpression self = popObject(classFile, method, stack);
        final String selfLocal = newObjectLocal(localDeclarations);
        final String otherLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(selfLocal, self));
        instructions.add(IrInstruction.assignObject(otherLocal, other));
        final IrExpression materializedSelf = IrExpression.objectLocal(selfLocal);
        final IrExpression materializedOther = IrExpression.objectLocal(otherLocal);
        final String resultLocal = newIntLocal(localDeclarations);
        final String doneLabel = "label_record_equals_done_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intLiteral(0)));
        for (final RecordObjectMethodsCall.Component component : recordCall.orElseThrow().components()) {
            if (component.shape().referenceOwner().isEmpty()) {
                continue;
            }
            instructions.add(IrInstruction.callStaticVoid(
                "javan_record_shape_validate",
                List.of(
                    IrExpression.objectField(classFile.name(), component.field().name(), materializedSelf),
                    IrExpression.stringLiteral(recordShapeEncoding(classes, component.shape()))
                )
            ));
        }
        instructions.add(IrInstruction.branchIf(doneLabel, IrExpression.objectComparison("==", materializedOther, IrExpression.objectNull())));
        instructions.add(IrInstruction.branchIf(
            doneLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intCall(
                    "javan_object_type_in",
                    List.of(materializedOther, IrExpression.intLiteral(1), IrExpression.intLiteral(exactTypeId(classes, classFile.name())))
                ),
                IrExpression.intLiteral(0)
            )
        ));
        for (final RecordObjectMethodsCall.Component component : recordCall.orElseThrow().components()) {
            if (component.shape().referenceOwner().isEmpty()) {
                continue;
            }
            instructions.add(IrInstruction.callStaticVoid(
                "javan_record_shape_validate",
                List.of(
                    IrExpression.objectField(classFile.name(), component.field().name(), materializedOther),
                    IrExpression.stringLiteral(recordShapeEncoding(classes, component.shape()))
                )
            ));
        }
        for (final RecordObjectMethodsCall.Component component : recordCall.orElseThrow().components()) {
            final javan.classfile.FieldInfo field = component.field();
            if (component.shape().referenceOwner().isPresent()) {
                lowerRecordReferenceFieldEquals(
                    classes,
                    classFile,
                    component,
                    materializedSelf,
                    materializedOther,
                    instructions,
                    localDeclarations,
                    dispatches,
                    doneLabel
                );
                continue;
            }
            instructions.add(IrInstruction.branchIf(
                doneLabel,
                IrExpression.intComparison(
                    "==",
                    recordObjectEqualsFieldExpression(classFile.name(), field, materializedSelf, materializedOther),
                    IrExpression.intLiteral(0)
                )
            ));
        }
        instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intLiteral(1)));
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
        return true;
    }

    private static void lowerRecordReferenceFieldEquals(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final RecordObjectMethodsCall.Component component,
        final IrExpression self,
        final IrExpression other,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final String doneLabel
    ) {
        final String leftLocal = newObjectLocal(localDeclarations);
        final String rightLocal = newObjectLocal(localDeclarations);
        final IrExpression left = IrExpression.objectLocal(leftLocal);
        final IrExpression right = IrExpression.objectLocal(rightLocal);
        instructions.add(IrInstruction.assignObject(
            leftLocal,
            IrExpression.objectField(classFile.name(), component.field().name(), self)
        ));
        instructions.add(IrInstruction.assignObject(
            rightLocal,
            IrExpression.objectField(classFile.name(), component.field().name(), other)
        ));
        instructions.add(IrInstruction.branchIf(
            doneLabel,
            IrExpression.intComparison(
                "==",
                recordReferenceEqualsExpression(classes, component.shape(), left, right, dispatches),
                IrExpression.intLiteral(0)
            )
        ));
    }

    private static IrExpression recordReferenceEqualsExpression(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final IrExpression left,
        final IrExpression right,
        final Map<String, IrDispatch> dispatches
    ) {
        markRecordReferenceObjectMethodDispatch(classes, shape, dispatches, false);
        return IrExpression.intCall(
            "javan_record_shape_equals_prevalidated",
            List.of(left, right, IrExpression.stringLiteral(recordShapeEncoding(classes, shape)))
        );
    }

    private static final String RECORD_REFERENCE_EQUALS_DISPATCH = "javan_dispatch_record_reference_equals";
    private static final String RECORD_REFERENCE_HASH_CODE_DISPATCH = "javan_dispatch_record_reference_hash_code";

    private static void markRecordReferenceObjectMethodDispatch(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final Map<String, IrDispatch> dispatches,
        final boolean hashCode
    ) {
        markRecordReferenceObjectMethodDispatch(
            classes,
            shape,
            dispatches,
            hashCode,
            RecordObjectMethodsCall.ReferenceContext.DIRECT_COMPONENT
        );
    }

    private static void markRecordReferenceObjectMethodDispatch(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final Map<String, IrDispatch> dispatches,
        final boolean hashCode,
        final RecordObjectMethodsCall.ReferenceContext context
    ) {
        if (!shape.valid() || shape.isArray() || shape.referenceOwner().isEmpty()) {
            return;
        }
        if (shape.isStringMap()) {
            return;
        }
        if (shape.isList()) {
            markRecordReferenceObjectMethodDispatch(
                classes,
                shape.listElement().orElseThrow(),
                dispatches,
                hashCode,
                RecordObjectMethodsCall.ReferenceContext.LIST_ELEMENT
            );
            return;
        }
        final String owner = shape.referenceOwner().orElseThrow();
        if ("java/lang/String".equals(owner) || recordBoxedTypeId(owner) != 0) {
            return;
        }
        final Optional<RecordObjectMethodsCall.DirectReferencePlan> plan =
            RecordObjectMethodsCall.referencePlan(classes, shape, hashCode, context);
        if (plan.isEmpty()) {
            return;
        }
        for (final RecordObjectMethodsCall.ReferenceTarget target : plan.orElseThrow().targets()) {
            addRecordReferenceObjectMethodDispatchTarget(dispatches, target, hashCode);
        }
    }

    private static void addRecordReferenceObjectMethodDispatchTarget(
        final Map<String, IrDispatch> dispatches,
        final RecordObjectMethodsCall.ReferenceTarget target,
        final boolean hashCode
    ) {
        final String symbol = hashCode ? RECORD_REFERENCE_HASH_CODE_DISPATCH : RECORD_REFERENCE_EQUALS_DISPATCH;
        final List<IrParameter> parameters = hashCode
            ? List.of(new IrParameter(IrType.OBJECT, "self"))
            : List.of(new IrParameter(IrType.OBJECT, "self"), new IrParameter(IrType.OBJECT, "arg0"));
        final IrDispatch existing =
            dispatches.getOrDefault(symbol, new IrDispatch(symbol, IrType.INT, parameters, List.of()));
        final List<IrDispatchTarget> targets = new ArrayList<>();
        for (final IrDispatchTarget existingTarget : existing.targets()) {
            if (existingTarget.owner().equals(target.owner())) {
                return;
            }
            targets.add(existingTarget);
        }
        final String functionSymbol = target.executable()
            ? BytecodeToIR.symbol(new EntryPoint(
                target.executableOwner(),
                hashCode ? "hashCode" : "equals",
                hashCode ? "()I" : "(Ljava/lang/Object;)Z"
            ))
            : hashCode ? "javan_record_reference_identity_hash_code" : "javan_record_reference_identity_equals";
        final IrDispatchTarget added = new IrDispatchTarget(target.owner(), functionSymbol);
        int insertionIndex = 0;
        while (insertionIndex < targets.size()
            && Strings2.compareAscii(targets.get(insertionIndex).owner(), target.owner()) <= 0) {
            insertionIndex++;
        }
        targets.add(insertionIndex, added);
        dispatches.put(symbol, new IrDispatch(symbol, IrType.INT, parameters, List.copyOf(targets)));
    }

    static String recordShapeEncoding(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape
    ) {
        return recordShapeEncoding(classes, shape, RecordObjectMethodsCall.ReferenceContext.DIRECT_COMPONENT);
    }

    private static String recordShapeEncoding(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final RecordObjectMethodsCall.ReferenceContext context
    ) {
        if (shape.isStringMap()) {
            return "m";
        }
        if (shape.isList()) {
            final RecordObjectMethodsCall.Shape element = shape.listElement().orElseThrow();
            final Optional<RecordObjectMethodsCall.DirectReferencePlan> directElementPlan =
                RecordObjectMethodsCall.referencePlan(
                    classes,
                    element,
                    false,
                    RecordObjectMethodsCall.ReferenceContext.DIRECT_COMPONENT
                );
            if (directElementPlan.isPresent()
                && directElementPlan.orElseThrow() instanceof RecordObjectMethodsCall.SealedReferenceUnionPlan) {
                throw new IllegalArgumentException("unsupported List record component sealed interface element");
            }
            return "l" + recordShapeEncoding(
                classes,
                element,
                RecordObjectMethodsCall.ReferenceContext.LIST_ELEMENT
            );
        }
        if (shape.isArray()) {
            final String binaryDescriptor = Strings2.replaceChar(shape.descriptor(), '/', '.');
            return "a" + binaryDescriptor.length() + ":" + binaryDescriptor;
        }
        final String owner = shape.referenceOwner().orElseThrow();
        if ("java/lang/String".equals(owner)) {
            return "s";
        }
        final int boxedTypeId = recordBoxedTypeId(owner);
        if (boxedTypeId != 0) {
            return "b" + boxedTypeId + ";";
        }
        Optional<RecordObjectMethodsCall.DirectReferencePlan> plan =
            RecordObjectMethodsCall.referencePlan(classes, shape, false, context);
        if (plan.isEmpty()) {
            plan = RecordObjectMethodsCall.referencePlan(classes, shape, true, context);
        }
        if (plan.isPresent() && plan.orElseThrow() instanceof RecordObjectMethodsCall.SealedReferenceUnionPlan union) {
            final StringBuilder encoding = new StringBuilder("p");
            int previousTypeId = 0;
            for (final RecordObjectMethodsCall.ReferenceTarget target : union.targets()) {
                final int typeId = exactTypeId(classes, target.owner());
                if (typeId <= previousTypeId) {
                    throw new IllegalStateException("sealed record shape type IDs must be strictly ascending");
                }
                encoding.append(typeId).append(';');
                previousTypeId = typeId;
            }
            return encoding.toString();
        }
        final ClassFile ownerClass = classes.get(owner);
        return (ownerClass != null && ownerClass.isEnum() ? "e" : "o")
            + exactTypeId(classes, owner)
            + ";";
    }

    private static int recordBoxedTypeId(final String owner) {
        return switch (owner) {
            case "java/lang/Boolean" -> TYPE_JAVA_LANG_BOOLEAN;
            case "java/lang/Byte" -> TYPE_JAVA_LANG_BYTE;
            case "java/lang/Character" -> TYPE_JAVA_LANG_CHARACTER;
            case "java/lang/Short" -> TYPE_JAVA_LANG_SHORT;
            case "java/lang/Integer" -> TYPE_JAVA_LANG_INTEGER;
            case "java/lang/Long" -> TYPE_JAVA_LANG_LONG;
            case "java/lang/Float" -> TYPE_JAVA_LANG_FLOAT;
            case "java/lang/Double" -> TYPE_JAVA_LANG_DOUBLE;
            default -> 0;
        };
    }

    private static IrExpression recordObjectEqualsFieldExpression(
        final String owner,
        final javan.classfile.FieldInfo field,
        final IrExpression self,
        final IrExpression other
    ) {
        return switch (field.descriptor()) {
            case "B", "C", "I", "S", "Z" -> IrExpression.intComparison(
                "==", IrExpression.intField(owner, field.name(), self), IrExpression.intField(owner, field.name(), other)
            );
            case "J" -> IrExpression.intComparison(
                "==",
                IrExpression.intCall(
                    "javan_lcmp",
                    List.of(IrExpression.longField(owner, field.name(), self), IrExpression.longField(owner, field.name(), other))
                ),
                IrExpression.intLiteral(0)
            );
            case "F" -> IrExpression.intCall(
                "javan_record_float_equals",
                List.of(IrExpression.floatField(owner, field.name(), self), IrExpression.floatField(owner, field.name(), other))
            );
            case "D" -> IrExpression.intCall(
                "javan_record_double_equals",
                List.of(IrExpression.doubleField(owner, field.name(), self), IrExpression.doubleField(owner, field.name(), other))
            );
            default -> IrExpression.objectComparison(
                "==", IrExpression.objectField(owner, field.name(), self), IrExpression.objectField(owner, field.name(), other)
            );
        };
    }

    static IrExpression popStringConcatArgument(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
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
        throw unsupported(classFile, method, instruction);
    }

    static boolean isSupportedStringConcat(final DynamicRef dynamicRef) {
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

    static Optional<String> stringConcatRecipe(final DynamicRef dynamicRef, final int argumentCount) {
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

    static String repeatedConcatPlaceholder(final int count) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append('\u0001');
        }
        return result.toString();
    }

    static Optional<List<String>> parameterDescriptors(final String descriptor) {
        if (!descriptor.startsWith("(")) {
            return Optional.empty();
        }
        final List<String> result = new ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final int start = index;
            final char type = descriptor.charAt(index);
            if ("BCDFIJSZ".indexOf(type) >= 0) {
                result.add(descriptor.substring(start, start + 1));
                index++;
            } else if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    return Optional.empty();
                }
                result.add(descriptor.substring(start, end + 1));
                index = end + 1;
            } else if (type == '[') {
                index = skipParameterArrayDescriptor(descriptor, index);
                if (index < 0) {
                    return Optional.empty();
                }
                result.add(descriptor.substring(start, index));
            } else {
                return Optional.empty();
            }
        }
        if (index >= descriptor.length()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
    }

    static int skipParameterArrayDescriptor(final String descriptor, final int start) {
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index >= descriptor.length()) {
            return -1;
        }
        if ("BCDFIJSZ".indexOf(descriptor.charAt(index)) >= 0) {
            return index + 1;
        }
        if (descriptor.charAt(index) == 'L') {
            final int end = descriptor.indexOf(';', index);
            if (end < 0) {
                return -1;
            }
            return end + 1;
        }
        return -1;
    }

    static IrExpression popValue(
        final ClassFile classFile,
        final MethodInfo method,
        final List<StackValue> stack,
        final IrType type,
        final Instruction instruction
    ) {
        if (type == IrType.INT) {
            return popInt(classFile, method, instruction, stack);
        }
        if (type == IrType.LONG) {
            return popLong(classFile, method, instruction, stack);
        }
        if (type == IrType.FLOAT) {
            return popFloat(classFile, method, instruction, stack);
        }
        if (type == IrType.DOUBLE) {
            return popDouble(classFile, method, instruction, stack);
        }
        if (type == IrType.OBJECT) {
            return popObject(classFile, method, instruction, stack);
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

    static void pushConstant(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (instruction.className().isPresent()) {
            stack.add(StackValue.objectExpression(classLiteralExpression(classes, classFile, method, instruction)));
            return;
        }
        if (instruction.stringValue().isPresent()) {
            final String value = instruction.stringValue().orElseThrow();
            if (value.indexOf('\0') >= 0) {
                throw BytecodeToIR.unsupportedEmbeddedNulStringConstant(
                    classFile,
                    method,
                    instruction
                );
            }
            stack.add(StackValue.objectExpression(IrExpression.stringLiteral(value)));
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
        if (isLiteralOpcode(instruction.opcode())) {
            if (instruction.constantPoolTag().isPresent() && instruction.constantPoolTag().orElseThrow() == 15) {
                throw unsupportedMethodHandleLiteral(classFile, method, instruction);
            }
            if (instruction.constantPoolTag().isPresent() && instruction.constantPoolTag().orElseThrow() == 16) {
                throw unsupportedMethodTypeLiteral(classFile, method, instruction);
            }
            if (instruction.constantPoolTag().isPresent() && instruction.constantPoolTag().orElseThrow() == 17) {
                throw unsupportedDynamicConstant(classFile, method, instruction);
            }
            throw unsupportedLiteralConstant(classFile, method, instruction);
        }
        throw unsupported(classFile, method, instruction);
    }

    private static boolean isLiteralOpcode(final int opcode) {
        return opcode == 2
            || opcode == 3
            || opcode == 4
            || opcode == 5
            || opcode == 6
            || opcode == 7
            || opcode == 8
            || opcode == 9
            || opcode == 10
            || opcode == 11
            || opcode == 12
            || opcode == 13
            || opcode == 14
            || opcode == 15
            || opcode == 16
            || opcode == 17
            || opcode == 18
            || opcode == 19
            || opcode == 20;
    }

    static IrExpression classLiteralExpression(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        final String jvmName = instruction.className().orElseThrow();
        if ("java/lang/String".equals(jvmName)) {
            return IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(
                    IrExpression.stringLiteral(binaryClassName(jvmName)),
                    IrExpression.intLiteral(BytecodeToIR.CLASS_EXACT_STRING),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0)
                )
            );
        }
        if ("java/lang/Object".equals(jvmName)) {
            return IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(
                    IrExpression.stringLiteral(binaryClassName(jvmName)),
                    IrExpression.intLiteral(BytecodeToIR.CLASS_EXACT_OBJECT),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0)
                )
            );
        }
        if ("java/lang/Class".equals(jvmName)) {
            return IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(
                    IrExpression.stringLiteral(binaryClassName(jvmName)),
                    IrExpression.intLiteral(BytecodeToIR.CLASS_EXACT_CLASS),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0)
                )
            );
        }
        if ("java/lang/ClassLoader".equals(jvmName)) {
            return IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(
                    IrExpression.stringLiteral(binaryClassName(jvmName)),
                    IrExpression.intLiteral(BytecodeToIR.CLASS_EXACT_CLASS_LOADER),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0)
                )
            );
        }
        final Optional<Integer> wrapperTypeId = platformWrapperTypeId(jvmName);
        if (wrapperTypeId.isPresent()) {
            return IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(
                    IrExpression.stringLiteral(binaryClassName(jvmName)),
                    IrExpression.intLiteral(wrapperTypeId.orElseThrow()),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0)
                )
            );
        }
        if (jvmName.startsWith("[")) {
            return IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(
                    IrExpression.stringLiteral(binaryClassName(jvmName)),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(0),
                    IrExpression.intLiteral(1),
                    IrExpression.intLiteral(0)
                )
            );
        }
        if (classes.containsKey(jvmName)) {
            final ClassFile target = classes.get(jvmName);
            final List<IrExpression> arguments = new ArrayList<>();
            arguments.add(IrExpression.stringLiteral(binaryClassName(jvmName)));
            arguments.add(IrExpression.intLiteral(sortedTypeId(classes, jvmName)));
            arguments.add(IrExpression.intLiteral(target != null && target.isEnum() ? 1 : 0));
            arguments.add(IrExpression.intLiteral(0));
            final List<Integer> assignableTypeIds = assignableTypeIds(classes, jvmName);
            arguments.add(IrExpression.intLiteral(assignableTypeIds.size()));
            for (final int typeId : assignableTypeIds) {
                arguments.add(IrExpression.intLiteral(typeId));
            }
            return IrExpression.objectCall("javan_runtime_class_literal", arguments);
        }
        return IrExpression.objectCall(
            "javan_runtime_class_literal",
            List.of(
                IrExpression.stringLiteral(binaryClassName(jvmName)),
                IrExpression.intLiteral(0),
                IrExpression.intLiteral(0),
                IrExpression.intLiteral(0),
                IrExpression.intLiteral(0)
            )
        );
    }

    static Optional<IrExpression> supportedPrimitiveClassField(final FieldRef fieldRef) {
        if (!"Ljava/lang/Class;".equals(fieldRef.descriptor()) || !"TYPE".equals(fieldRef.name())) {
            return Optional.empty();
        }
        return switch (fieldRef.owner()) {
            case "java/lang/Boolean" -> Optional.of(primitiveClassLiteral("boolean", BytecodeToIR.CLASS_EXACT_PRIMITIVE_BOOLEAN));
            case "java/lang/Byte" -> Optional.of(primitiveClassLiteral("byte", BytecodeToIR.CLASS_EXACT_PRIMITIVE_BYTE));
            case "java/lang/Short" -> Optional.of(primitiveClassLiteral("short", BytecodeToIR.CLASS_EXACT_PRIMITIVE_SHORT));
            case "java/lang/Character" -> Optional.of(primitiveClassLiteral("char", BytecodeToIR.CLASS_EXACT_PRIMITIVE_CHAR));
            case "java/lang/Integer" -> Optional.of(primitiveClassLiteral("int", BytecodeToIR.CLASS_EXACT_PRIMITIVE_INT));
            case "java/lang/Long" -> Optional.of(primitiveClassLiteral("long", BytecodeToIR.CLASS_EXACT_PRIMITIVE_LONG));
            case "java/lang/Float" -> Optional.of(primitiveClassLiteral("float", BytecodeToIR.CLASS_EXACT_PRIMITIVE_FLOAT));
            case "java/lang/Double" -> Optional.of(primitiveClassLiteral("double", BytecodeToIR.CLASS_EXACT_PRIMITIVE_DOUBLE));
            case "java/lang/Void" -> Optional.of(primitiveClassLiteral("void", BytecodeToIR.CLASS_EXACT_PRIMITIVE_VOID));
            default -> Optional.empty();
        };
    }

    private static IrExpression primitiveClassLiteral(final String binaryName, final int exactTypeId) {
        return IrExpression.objectCall(
            "javan_runtime_class_literal",
            List.of(
                IrExpression.stringLiteral(binaryName),
                IrExpression.intLiteral(exactTypeId),
                IrExpression.intLiteral(0),
                IrExpression.intLiteral(0),
                IrExpression.intLiteral(0)
            )
        );
    }

    static String binaryClassName(final String jvmName) {
        return Strings2.replaceChar(jvmName, '/', '.');
    }

    private static int sortedTypeId(final Map<String, ClassFile> classes, final String jvmName) {
        final List<ClassFile> sorted = sortedClasses(classes);
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).name().equals(jvmName)) {
                return index + 1;
            }
        }
        throw new IllegalStateException("Missing class literal type id for " + jvmName);
    }

    static void newObject(
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
        if (DATE_TIME_FORMATTER_BUILDER_OWNER.equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_datetime_formatter_builder_new", List.of())));
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
        if ("java/net/InetSocketAddress".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectNull()));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/net/Socket".equals(owner) || "java/net/ServerSocket".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectNull()));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/lang/ThreadLocal".equals(owner) || "java/lang/InheritableThreadLocal".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            final String helper = "java/lang/InheritableThreadLocal".equals(owner)
                ? "javan_inheritable_thread_local_new"
                : "javan_thread_local_new";
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall(helper, List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/util/concurrent/ScheduledThreadPoolExecutor".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_scheduled_thread_pool_executor_new", List.of())));
            stack.add(StackValue.scheduledThreadPoolExecutor(local));
            return;
        }
        if (!"java/util/concurrent/ScheduledThreadPoolExecutor".equals(owner)
            && isAssignableTo(classes, owner, "java/util/concurrent/ScheduledThreadPoolExecutor")) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectAllocation(owner)));
            stack.add(StackValue.scheduledThreadPoolExecutor(local));
            return;
        }
        if ("java/util/concurrent/atomic/AtomicLong".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_atomic_long_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/util/concurrent/atomic/AtomicInteger".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_atomic_integer_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/util/concurrent/atomic/AtomicBoolean".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_atomic_boolean_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_atomic_reference_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if ("java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_caller_runs_policy_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if (!"java/lang/Thread".equals(owner) && isAssignableTo(classes, owner, "java/lang/Thread")) {
            throw unsupportedThreadSubclassAllocation(classFile, method, instruction, owner);
        }
        if ("java/lang/Thread".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_thread_new", List.of())));
            stack.add(StackValue.objectExpression(local));
            return;
        }
        if (isJdkSetClass(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_hashset_new", List.of())));
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

    static DiagnosticException unsupportedThreadSubclassAllocation(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final String owner
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN074",
            "Thread subclass allocation is not supported",
            classFile.name(),
            method.name() + method.descriptor(),
            instruction.mnemonic() + " " + owner,
            "The current native thread runtime only models exact java.lang.Thread objects with an optional Runnable target.",
            "Use exact Thread or wait for full Thread subclass support."
        ));
    }

    static boolean pushBuiltinObjectField(final FieldRef fieldRef, final List<StackValue> stack) {
        if ("java/nio/charset/StandardCharsets".equals(fieldRef.owner())
            && "UTF_8".equals(fieldRef.name())
            && "Ljava/nio/charset/Charset;".equals(fieldRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_standard_charset_utf8", List.of())));
            return true;
        }
        if (DATE_TIME_FORMATTER_OWNER.equals(fieldRef.owner())
            && "Ljava/time/format/DateTimeFormatter;".equals(fieldRef.descriptor())) {
            final Optional<Integer> formatterId = dateTimeFormatterBuiltinId(fieldRef.name());
            if (formatterId.isPresent()) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_datetime_formatter_builtin",
                    List.of(IrExpression.intLiteral(formatterId.orElseThrow()))
                )));
                return true;
            }
        }
        if (TEXT_STYLE_OWNER.equals(fieldRef.owner())
            && "SHORT".equals(fieldRef.name())
            && "Ljava/time/format/TextStyle;".equals(fieldRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_text_style_short", List.of())));
            return true;
        }
        if (LOCALE_OWNER.equals(fieldRef.owner())
            && "ENGLISH".equals(fieldRef.name())
            && "Ljava/util/Locale;".equals(fieldRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_locale_english", List.of())));
            return true;
        }
        if (LOCALE_OWNER.equals(fieldRef.owner())
            && "ROOT".equals(fieldRef.name())
            && "Ljava/util/Locale;".equals(fieldRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_locale_root", List.of())));
            return true;
        }
        return false;
    }

    static DiagnosticException unsupportedStandardCharset(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final FieldRef fieldRef
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN031",
            "unsupported standard charset",
            classFile.name(),
            method.name() + method.descriptor(),
            instruction.mnemonic() + " " + fieldRef.owner() + "." + fieldRef.name() + ":" + fieldRef.descriptor(),
            "Only StandardCharsets.UTF_8 is supported by the current native string representation.",
            "Use StandardCharsets.UTF_8 or wait for the length-bearing Java string model."
        ));
    }

    private static Optional<Integer> dateTimeFormatterBuiltinId(final String fieldName) {
        if ("ISO_ZONED_DATE_TIME".equals(fieldName)) {
            return Optional.of(1);
        }
        if ("ISO_OFFSET_DATE_TIME".equals(fieldName)) {
            return Optional.of(2);
        }
        if ("ISO_ORDINAL_DATE".equals(fieldName)) {
            return Optional.of(3);
        }
        if ("RFC_1123_DATE_TIME".equals(fieldName)) {
            return Optional.of(4);
        }
        if ("ISO_LOCAL_DATE_TIME".equals(fieldName)) {
            return Optional.of(5);
        }
        if ("ISO_OFFSET_DATE".equals(fieldName)) {
            return Optional.of(6);
        }
        if ("ISO_LOCAL_TIME".equals(fieldName)) {
            return Optional.of(7);
        }
        if ("ISO_OFFSET_TIME".equals(fieldName)) {
            return Optional.of(8);
        }
        if ("ISO_LOCAL_DATE".equals(fieldName)) {
            return Optional.of(9);
        }
        if ("BASIC_ISO_DATE".equals(fieldName)) {
            return Optional.of(10);
        }
        if ("ISO_DATE_TIME".equals(fieldName)) {
            return Optional.of(11);
        }
        if ("ISO_INSTANT".equals(fieldName)) {
            return Optional.of(12);
        }
        if ("ISO_DATE".equals(fieldName)) {
            return Optional.of(13);
        }
        if ("ISO_TIME".equals(fieldName)) {
            return Optional.of(14);
        }
        if ("ISO_WEEK_DATE".equals(fieldName)) {
            return Optional.of(15);
        }
        return Optional.empty();
    }

    static boolean lowerObjectClone(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!GeneratedObjectCloneSupport.isObjectClone(methodRef)) {
            return false;
        }
        final GeneratedObjectCloneSupport.Status status =
            GeneratedObjectCloneSupport.invocationStatus(classes, classFile);
        if (status != GeneratedObjectCloneSupport.Status.SUPPORTED) {
            throw unsupportedObjectClone(classFile, method, methodRef, status);
        }

        final IrExpression receiver = popObject(classFile, method, stack);
        final IrExpression clonedObject = IrExpression.objectCall(
            "javan_generated_object_clone",
            List.of(receiver)
        );
        stack.add(StackValue.objectExpression(clonedObject));
        return true;
    }

    private static DiagnosticException unsupportedObjectClone(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final GeneratedObjectCloneSupport.Status status
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN050",
            "Object.clone requires a supported Cloneable class",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            GeneratedObjectCloneSupport.reason(status),
            GeneratedObjectCloneSupport.fix(status)
        ));
    }
}
