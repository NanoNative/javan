package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.FieldInfo;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactorySupport;
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
    static final int TYPE_JAVA_LANG_CHARACTER = -1010;

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
        final LambdaMetafactorySupport.Registry lambdaRegistry = LambdaMetafactorySupport.scan(classes, callGraph.reachableMethods());
        final Map<String, ClassFile> expandedClasses = lambdaRegistry.expandedClasses(classes);
        final List<IrFunction> functions = new ArrayList<>();
        final Map<String, IrDispatch> dispatches = new LinkedHashMap<>();
        final List<EntryPoint> reachableMethods = BytecodeToIRMetadataSupport.sortedEntryPoints(callGraph.reachableMethods());
        final List<EntryPoint> runnableThreadTargets = BytecodeToIRInvokeSupport.runnableThreadTargets(expandedClasses, reachableMethods);
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
            functions.add(lowerFunction(expandedClasses, reachable, dispatches, sourceLines, lambdaRegistry));
        }
        return new IrProgram(BytecodeToIRMetadataSupport.lowerClasses(expandedClasses), List.copyOf(functions), List.copyOf(dispatches.values()), symbol(callGraph.entryPoint()));
    }

    static IrFunction lowerFunction(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint,
        final Map<String, IrDispatch> dispatches,
        final SourceLineIndex sourceLines,
        final LambdaMetafactorySupport.Registry lambdaRegistry
    ) {
        final Optional<LambdaMetafactorySupport.LambdaClosurePlan> lambdaPlan = lambdaRegistry.planForSyntheticOwner(entryPoint.className());
        if (lambdaPlan.isPresent()
            && lambdaPlan.orElseThrow().methodName().equals(entryPoint.methodName())
            && lambdaPlan.orElseThrow().methodDescriptor().equals(entryPoint.descriptor())) {
            return lowerLambdaClosureFunction(classes, dispatches, lambdaPlan.orElseThrow());
        }
        final ClassFile classFile = classes.get(entryPoint.className());
        final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElseThrow();
        final MethodDescriptor descriptor = MethodDescriptor.parse(method.descriptor());
        final List<IrParameter> parameters = BytecodeToIRMetadataSupport.parameters(method, descriptor);
        final List<IrInstruction> instructions = new ArrayList<>();
        final List<StackValue> stack = new ArrayList<>();
        final Map<Integer, IrExpression> locals = new HashMap<>();
        final Map<Integer, StackKind> objectLocalKinds = new HashMap<>();
        final Map<Integer, String> objectLocalThrowableTypes = new HashMap<>();
        final Map<Integer, StackValue> specialObjectLocals = new HashMap<>();
        final Map<Integer, IrLocal> localDeclarations = new LinkedHashMap<>();
        final Map<Integer, StackValue> pendingExceptionHandlerStacks = new HashMap<>();
        final CodeAttribute code = method.code().orElseThrow();
        final List<Instruction> bytecode = code.instructions();
        final List<Integer> ignoredHandlerOffsets = ignoredEnumSwitchMapHandlerOffsets(classes, classFile, method, code);
        final List<Integer> handlerOffsets = exceptionHandlerOffsets(code);
        final List<Integer> branchTargets = branchTargets(code);
        final List<Integer> skippedOffsets = new ArrayList<>();
        final List<Integer> replacementLabelOffsets = new ArrayList<>();
        BytecodeToIRMetadataSupport.bindParameters(method, descriptor, parameters, locals);
        for (int index = 0; index < bytecode.size(); index++) {
            final Instruction instruction = bytecode.get(index);
            if (containsInt(handlerOffsets, instruction.offset())) {
                final StackValue pendingException = pendingExceptionHandlerStacks.get(instruction.offset());
                if (pendingException != null) {
                    BytecodeToIRControlFlowSupport.clearStack(stack);
                    stack.add(pendingException);
                } else if (stack.isEmpty()) {
                    stack.add(StackValue.objectExpression(IrExpression.objectNull()));
                }
            }
            if (containsInt(branchTargets, instruction.offset())
                && !containsInt(replacementLabelOffsets, instruction.offset())) {
                instructions.add(IrInstruction.label(label(instruction.offset())));
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
                objectLocalThrowableTypes,
                specialObjectLocals,
                localDeclarations,
                dispatches,
                skippedOffsets,
                replacementLabelOffsets
            )) {
                BytecodeToIRControlFlowSupport.annotateNewInstructions(instructions, instructionStart, sourceLocation);
                continue;
            }
            if (BytecodeToIRControlFlowSupport.lowerSwitchValueSelection(
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
                specialObjectLocals,
                localDeclarations,
                dispatches,
                skippedOffsets,
                replacementLabelOffsets
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
                objectLocalThrowableTypes,
                specialObjectLocals,
                localDeclarations,
                dispatches,
                sourceLines,
                lambdaRegistry
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

    private static IrFunction lowerLambdaClosureFunction(
        final Map<String, ClassFile> classes,
        final Map<String, IrDispatch> dispatches,
        final LambdaMetafactorySupport.LambdaClosurePlan plan
    ) {
        final MethodDescriptor descriptor = MethodDescriptor.parse(plan.methodDescriptor());
        final MethodDescriptor instantiatedDescriptor = MethodDescriptor.parse(plan.instantiatedMethodDescriptor());
        final List<IrParameter> parameters = new ArrayList<>();
        parameters.add(new IrParameter(IrType.OBJECT, "self"));
        parameters.addAll(descriptor.parameters());

        final List<IrExpression> arguments = new ArrayList<>();
        final List<String> exactArgumentDescriptors = new ArrayList<>();
        int captureStart = 0;
        int parameterStart = 0;
        if (plan.implementationReferenceKind() == 5 || plan.implementationReferenceKind() == 9) {
            if (plan.receiverBinding() == LambdaMetafactorySupport.ReceiverBinding.CAPTURE0) {
                arguments.add(captureFieldExpression(plan.syntheticOwner(), "capture0", plan.captureDescriptors().getFirst(), IrExpression.objectLocal("self")));
                exactArgumentDescriptors.add(plan.captureDescriptors().getFirst());
                captureStart = 1;
            } else if (plan.receiverBinding() == LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER) {
                arguments.add(parameterExpression(descriptor.parameterTypes().getFirst(), "arg0"));
                exactArgumentDescriptors.add(parameterDescriptors(plan.instantiatedMethodDescriptor()).getFirst());
                parameterStart = 1;
            }
        }
        for (int index = captureStart; index < plan.captureDescriptors().size(); index++) {
            arguments.add(captureFieldExpression(
                plan.syntheticOwner(),
                "capture" + index,
                plan.captureDescriptors().get(index),
                IrExpression.objectLocal("self")
            ));
            exactArgumentDescriptors.add(plan.captureDescriptors().get(index));
        }
        for (int index = parameterStart; index < descriptor.parameterTypes().size(); index++) {
            arguments.add(parameterExpression(descriptor.parameterTypes().get(index), "arg" + index));
            exactArgumentDescriptors.add(parameterDescriptors(plan.instantiatedMethodDescriptor()).get(index));
        }

        final List<IrInstruction> instructions = new ArrayList<>();
        final List<IrLocal> locals = new ArrayList<>();
        if (lowerJdkLambdaConstructorBridge(classes, plan, descriptor.returnType(), arguments, exactArgumentDescriptors, instructions, locals)) {
            return new IrFunction(
                plan.syntheticOwner(),
                plan.methodName(),
                plan.methodDescriptor(),
                symbol(plan.wrapperEntryPoint()),
                descriptor.returnType(),
                List.copyOf(parameters),
                List.copyOf(locals),
                List.copyOf(instructions)
            );
        }
        final Optional<IrExpression> jdkBridgeResult = lowerJdkLambdaBridge(plan, arguments, exactArgumentDescriptors, descriptor.returnType());
        if (jdkBridgeResult.isPresent()) {
            final IrExpression result = jdkBridgeResult.orElseThrow();
            final IrType returnType = descriptor.returnType();
            if (returnType == IrType.VOID) {
                instructions.add(IrInstruction.callStaticVoid(result.value(), result.arguments()));
                instructions.add(IrInstruction.returnVoid());
            } else if (returnType == IrType.INT) {
                instructions.add(IrInstruction.returnInt(result));
            } else if (returnType == IrType.LONG) {
                instructions.add(IrInstruction.returnLong(result));
            } else if (returnType == IrType.FLOAT) {
                instructions.add(IrInstruction.returnFloat(result));
            } else if (returnType == IrType.DOUBLE) {
                instructions.add(IrInstruction.returnDouble(result));
            } else if (returnType == IrType.OBJECT) {
                instructions.add(IrInstruction.returnObject(result));
            } else {
                throw new IllegalStateException("unsupported lambda return type: " + returnType);
            }
            return new IrFunction(
                plan.syntheticOwner(),
                plan.methodName(),
                plan.methodDescriptor(),
                symbol(plan.wrapperEntryPoint()),
                descriptor.returnType(),
                List.copyOf(parameters),
                List.copyOf(locals),
                List.copyOf(instructions)
            );
        }

        final String targetSymbol = lambdaImplementationSymbol(classes, dispatches, plan);
        final IrType returnType = descriptor.returnType();
        if (returnType == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(targetSymbol, arguments));
            instructions.add(IrInstruction.returnVoid());
        } else if (returnType == IrType.INT) {
            instructions.add(IrInstruction.returnInt(IrExpression.intCall(targetSymbol, arguments)));
        } else if (returnType == IrType.LONG) {
            instructions.add(IrInstruction.returnLong(IrExpression.longCall(targetSymbol, arguments)));
        } else if (returnType == IrType.FLOAT) {
            instructions.add(IrInstruction.returnFloat(IrExpression.floatCall(targetSymbol, arguments)));
        } else if (returnType == IrType.DOUBLE) {
            instructions.add(IrInstruction.returnDouble(IrExpression.doubleCall(targetSymbol, arguments)));
        } else if (returnType == IrType.OBJECT) {
            instructions.add(IrInstruction.returnObject(IrExpression.objectCall(targetSymbol, arguments)));
        } else {
            throw new IllegalStateException("unsupported lambda return type: " + returnType);
        }
        return new IrFunction(
            plan.syntheticOwner(),
            plan.methodName(),
            plan.methodDescriptor(),
            symbol(plan.wrapperEntryPoint()),
            descriptor.returnType(),
            List.copyOf(parameters),
            List.copyOf(locals),
            List.copyOf(instructions)
        );
    }

    private static boolean lowerJdkLambdaConstructorBridge(
        final Map<String, ClassFile> classes,
        final LambdaMetafactorySupport.LambdaClosurePlan plan,
        final IrType erasedReturnType,
        final List<IrExpression> arguments,
        final List<String> exactArgumentDescriptors,
        final List<IrInstruction> instructions,
        final List<IrLocal> locals
    ) {
        final MethodRef target = plan.implementationTarget();
        if (plan.implementationReferenceKind() != 8
            || !"<init>".equals(target.name())
            || erasedReturnType != IrType.OBJECT) {
            return false;
        }
        final Optional<IrExpression> receiverAllocation = constructorReceiverAllocation(classes, target);
        if (receiverAllocation.isEmpty()) {
            return false;
        }
        final String localName = "object" + locals.size();
        locals.add(new IrLocal(IrType.OBJECT, localName));
        final IrExpression receiver = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, receiverAllocation.orElseThrow()));
        final List<String> implementationParameters = parameterDescriptors(target.descriptor());
        final List<IrExpression> implementationArguments = new ArrayList<>();
        for (int index = 0; index < implementationParameters.size(); index++) {
            implementationArguments.add(adaptBridgeArgument(
                exactArgumentDescriptors.get(index),
                arguments.get(index),
                implementationParameters.get(index)
            ));
        }
        if (JdkCallSupport.isSupported(target)) {
            if (BytecodeToIRInvokeSupport.lowerThreadConstructor(target, instructions, implementationArguments, receiver)
                || BytecodeToIRInvokeSupport.lowerAtomicConstructor(target, instructions, implementationArguments, receiver)
                || BytecodeToIRInvokeSupport.lowerThreadLocalConstructor(target)
                || BytecodeToIRInvokeSupport.lowerStringConstructor(target, instructions, implementationArguments, receiver)
                || BytecodeToIRInvokeSupport.lowerInetSocketAddressConstructor(target, instructions, implementationArguments, receiver)
                || BytecodeToIRInvokeSupport.lowerSocketConstructor(target, instructions, implementationArguments, receiver)
                || BytecodeToIRInvokeSupport.lowerStringBuilderConstructor(target, instructions, new ArrayList<>(), implementationArguments, receiver)
                || BytecodeToIRInvokeSupport.lowerDateTimeFormatterBuilderConstructor(target)
                || BytecodeToIRInvokeSupport.lowerJdkCollectionConstructorCall(target, instructions, implementationArguments, receiver)) {
                instructions.add(IrInstruction.returnObject(receiver));
                return true;
            }
            return false;
        }
        if (classes.containsKey(target.owner())) {
            final List<IrExpression> constructorArguments = new ArrayList<>();
            constructorArguments.add(receiver);
            constructorArguments.addAll(implementationArguments);
            instructions.add(IrInstruction.callStaticVoid(
                symbol(new EntryPoint(target.owner(), target.name(), target.descriptor())),
                constructorArguments
            ));
            instructions.add(IrInstruction.returnObject(receiver));
            return true;
        }
        return false;
    }

    private static Optional<IrExpression> constructorReceiverAllocation(
        final Map<String, ClassFile> classes,
        final MethodRef target
    ) {
        if (JdkCallSupport.isSupported(target)) {
            return jdkConstructorReceiverAllocation(target.owner());
        }
        if (!classes.containsKey(target.owner()) || isAssignableTo(classes, target.owner(), "java/lang/Thread")) {
            return Optional.empty();
        }
        return Optional.of(IrExpression.objectAllocation(target.owner()));
    }

    private static Optional<IrExpression> jdkConstructorReceiverAllocation(final String owner) {
        if ("java/lang/String".equals(owner)
            || "java/net/InetSocketAddress".equals(owner)
            || "java/net/Socket".equals(owner)
            || "java/net/ServerSocket".equals(owner)) {
            return Optional.of(IrExpression.objectNull());
        }
        if ("java/lang/StringBuilder".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_stringbuilder_new", List.of()));
        }
        if ("java/util/concurrent/atomic/AtomicBoolean".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_atomic_boolean_new", List.of()));
        }
        if ("java/util/concurrent/atomic/AtomicInteger".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_atomic_integer_new", List.of()));
        }
        if ("java/util/concurrent/atomic/AtomicLong".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_atomic_long_new", List.of()));
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_atomic_reference_new", List.of()));
        }
        if ("java/lang/ThreadLocal".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_thread_local_new", List.of()));
        }
        if ("java/lang/Thread".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_thread_new", List.of()));
        }
        if ("java/time/format/DateTimeFormatterBuilder".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_datetime_formatter_builder_new", List.of()));
        }
        if ("java/text/SimpleDateFormat".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_simple_date_format_new", List.of()));
        }
        if ("java/lang/StackTraceElement".equals(owner)) {
            return Optional.of(IrExpression.objectAllocation(owner));
        }
        if ("java/util/ArrayList".equals(owner) || "java/util/concurrent/CopyOnWriteArrayList".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_arraylist_new", List.of()));
        }
        if ("java/util/HashSet".equals(owner) || "java/util/LinkedHashSet".equals(owner)) {
            return Optional.of(IrExpression.objectCall("javan_hashset_new", List.of()));
        }
        return Optional.empty();
    }

    private static String lambdaImplementationSymbol(
        final Map<String, ClassFile> classes,
        final Map<String, IrDispatch> dispatches,
        final LambdaMetafactorySupport.LambdaClosurePlan plan
    ) {
        final MethodRef target = plan.implementationTarget();
        if (classes.containsKey(target.owner())) {
            return symbol(new EntryPoint(target.owner(), target.name(), target.descriptor()));
        }
        final List<EntryPoint> targets = plan.implementationReferenceKind() == 9
            ? interfaceTargets(classes, target)
            : virtualTargets(classes, target);
        if (targets.isEmpty()) {
            throw new IllegalStateException("No deterministic bridge targets for " + target.display());
        }
        if (targets.size() == 1) {
            return symbol(targets.getFirst());
        }
        final String targetDispatchSymbol = dispatchSymbol(target);
        dispatches.putIfAbsent(
            targetDispatchSymbol,
            BytecodeToIRInvokeSupport.dispatch(
                targetDispatchSymbol,
                MethodDescriptor.parse(target.descriptor()),
                targets
            )
        );
        return targetDispatchSymbol;
    }

    private static IrExpression captureFieldExpression(
        final String owner,
        final String fieldName,
        final String descriptor,
        final IrExpression self
    ) {
        final IrType fieldType = BytecodeToIRMetadataSupport.fieldType(descriptor).orElseThrow();
        if (fieldType == IrType.INT) {
            return IrExpression.intField(owner, fieldName, self);
        }
        if (fieldType == IrType.LONG) {
            return IrExpression.longField(owner, fieldName, self);
        }
        if (fieldType == IrType.FLOAT) {
            return IrExpression.floatField(owner, fieldName, self);
        }
        if (fieldType == IrType.DOUBLE) {
            return IrExpression.doubleField(owner, fieldName, self);
        }
        if (fieldType == IrType.OBJECT) {
            return IrExpression.objectField(owner, fieldName, self);
        }
        throw new IllegalStateException("void capture is invalid");
    }

    private static IrExpression parameterExpression(final IrType type, final String name) {
        if (type == IrType.INT) {
            return IrExpression.intLocal(name);
        }
        if (type == IrType.LONG) {
            return IrExpression.longLocal(name);
        }
        if (type == IrType.FLOAT) {
            return IrExpression.floatLocal(name);
        }
        if (type == IrType.DOUBLE) {
            return IrExpression.doubleLocal(name);
        }
        if (type == IrType.OBJECT) {
            return IrExpression.objectLocal(name);
        }
        throw new IllegalStateException("void parameter is invalid");
    }

    private static Optional<IrExpression> lowerJdkLambdaBridge(
        final LambdaMetafactorySupport.LambdaClosurePlan plan,
        final List<IrExpression> arguments,
        final List<String> exactArgumentDescriptors,
        final IrType erasedReturnType
    ) {
        final MethodRef target = plan.implementationTarget();
        if (!JdkCallSupport.isSupported(target) || JdkCallSupport.isSupportedClosedWorldDispatchCall(target)) {
            return Optional.empty();
        }
        final List<String> implementationParameters = parameterDescriptors(target.descriptor());
        final List<IrExpression> implementationArguments = new ArrayList<>();
        int implementationStart = 0;
        if (plan.implementationReferenceKind() == 5 || plan.implementationReferenceKind() == 9) {
            implementationArguments.add(arguments.getFirst());
            implementationStart = 1;
        }
        for (int index = 0; index < implementationParameters.size(); index++) {
            implementationArguments.add(adaptBridgeArgument(
                exactArgumentDescriptors.get(index + implementationStart),
                arguments.get(index + implementationStart),
                implementationParameters.get(index)
            ));
        }
        final IrExpression implementationResult = implementationBridgeCall(target, implementationArguments);
        if (implementationResult.type() == IrType.VOID) {
            return Optional.of(implementationResult);
        }
        return Optional.of(adaptBridgeReturn(
            target,
            implementationResult,
            returnDescriptor(target.descriptor()),
            returnDescriptor(plan.instantiatedMethodDescriptor()),
            erasedReturnType
        ));
    }

    private static IrExpression implementationBridgeCall(final MethodRef target, final List<IrExpression> arguments) {
        if ("java/io/PrintStream".equals(target.owner()) && "println".equals(target.name()) && "()V".equals(target.descriptor())) {
            return new IrExpression(IrExpression.Kind.CALL, IrType.VOID, "javan_printstream_println_object", List.of(arguments.getFirst(), IrExpression.stringLiteral("")));
        }
        if ("java/io/PrintStream".equals(target.owner()) && "println".equals(target.name()) && "(Ljava/lang/String;)V".equals(target.descriptor())) {
            return new IrExpression(IrExpression.Kind.CALL, IrType.VOID, "javan_printstream_println_object", arguments);
        }
        if ("java/io/PrintStream".equals(target.owner()) && "println".equals(target.name()) && "(Ljava/lang/Object;)V".equals(target.descriptor())) {
            return new IrExpression(IrExpression.Kind.CALL, IrType.VOID, "javan_printstream_println_object", arguments);
        }
        if ("java/io/PrintStream".equals(target.owner()) && "print".equals(target.name()) && "(Ljava/lang/String;)V".equals(target.descriptor())) {
            return new IrExpression(IrExpression.Kind.CALL, IrType.VOID, "javan_printstream_print_object", arguments);
        }
        if ("java/io/PrintStream".equals(target.owner()) && "print".equals(target.name()) && "(Ljava/lang/Object;)V".equals(target.descriptor())) {
            return new IrExpression(IrExpression.Kind.CALL, IrType.VOID, "javan_printstream_print_object", arguments);
        }
        if ("java/util/Objects".equals(target.owner()) && "nonNull".equals(target.name()) && "(Ljava/lang/Object;)Z".equals(target.descriptor())) {
            return IrExpression.intCall("javan_object_non_null", arguments);
        }
        if ("java/lang/String".equals(target.owner()) && "length".equals(target.name()) && "()I".equals(target.descriptor())) {
            return IrExpression.intCall("javan_string_length", arguments);
        }
        if ("java/lang/Integer".equals(target.owner()) && "valueOf".equals(target.name()) && "(I)Ljava/lang/Integer;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_integer_value_of", arguments);
        }
        if ("java/lang/Integer".equals(target.owner()) && "intValue".equals(target.name()) && "()I".equals(target.descriptor())) {
            return IrExpression.intCall("javan_integer_int_value", arguments);
        }
        if ("java/lang/Long".equals(target.owner()) && "valueOf".equals(target.name()) && "(J)Ljava/lang/Long;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_long_value_of", arguments);
        }
        if ("java/lang/Long".equals(target.owner()) && "longValue".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_long_long_value", arguments);
        }
        if ("java/lang/Float".equals(target.owner()) && "valueOf".equals(target.name()) && "(F)Ljava/lang/Float;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_float_value_of", arguments);
        }
        if ("java/lang/Float".equals(target.owner()) && "floatValue".equals(target.name()) && "()F".equals(target.descriptor())) {
            return IrExpression.floatCall("javan_float_float_value", arguments);
        }
        if ("java/lang/Double".equals(target.owner()) && "valueOf".equals(target.name()) && "(D)Ljava/lang/Double;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_double_value_of", arguments);
        }
        if ("java/lang/Double".equals(target.owner()) && "doubleValue".equals(target.name()) && "()D".equals(target.descriptor())) {
            return IrExpression.doubleCall("javan_double_double_value", arguments);
        }
        if ("java/lang/Boolean".equals(target.owner()) && "valueOf".equals(target.name()) && "(Z)Ljava/lang/Boolean;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_boolean_value_of", arguments);
        }
        if ("java/lang/Boolean".equals(target.owner()) && "booleanValue".equals(target.name()) && "()Z".equals(target.descriptor())) {
            return IrExpression.intCall("javan_boolean_boolean_value", arguments);
        }
        if ("java/lang/Character".equals(target.owner()) && "valueOf".equals(target.name()) && "(C)Ljava/lang/Character;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_character_value_of", arguments);
        }
        if ("java/lang/Character".equals(target.owner()) && "charValue".equals(target.name()) && "()C".equals(target.descriptor())) {
            return IrExpression.intCall("javan_character_char_value", arguments);
        }
        if ("java/lang/Number".equals(target.owner()) && "intValue".equals(target.name()) && "()I".equals(target.descriptor())) {
            return IrExpression.intCall("javan_number_int_value", arguments);
        }
        if ("java/time/ZoneId".equals(target.owner()) && "systemDefault".equals(target.name()) && "()Ljava/time/ZoneId;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zone_id_system_default", arguments);
        }
        if ("java/time/Instant".equals(target.owner()) && "ofEpochMilli".equals(target.name()) && "(J)Ljava/time/Instant;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_instant_of_epoch_millis", arguments);
        }
        if ("java/time/Instant".equals(target.owner()) && "from".equals(target.name()) && "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/Instant;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_instant_from_temporal", arguments);
        }
        if ("java/time/Instant".equals(target.owner()) && "toEpochMilli".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_instant_to_epoch_millis", arguments);
        }
        if ("java/time/Instant".equals(target.owner()) && "atZone".equals(target.name()) && "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_instant_at_zone", arguments);
        }
        if ("java/time/LocalDate".equals(target.owner()) && "ofEpochDay".equals(target.name()) && "(J)Ljava/time/LocalDate;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_of_epoch_day", arguments);
        }
        if ("java/time/LocalDate".equals(target.owner()) && "from".equals(target.name()) && "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/LocalDate;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_from_temporal", arguments);
        }
        if ("java/time/LocalDate".equals(target.owner()) && "now".equals(target.name()) && "(Ljava/time/ZoneId;)Ljava/time/LocalDate;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_now", arguments);
        }
        if ("java/time/LocalDate".equals(target.owner()) && "atStartOfDay".equals(target.name()) && "()Ljava/time/LocalDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_at_start_of_day", arguments);
        }
        if ("java/time/LocalDate".equals(target.owner()) && "atStartOfDay".equals(target.name()) && "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_at_start_of_day_zone", arguments);
        }
        if ("java/time/LocalTime".equals(target.owner()) && "from".equals(target.name()) && "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/LocalTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_time_from_temporal", arguments);
        }
        if ("java/time/LocalDateTime".equals(target.owner()) && "ofInstant".equals(target.name()) && "(Ljava/time/Instant;Ljava/time/ZoneId;)Ljava/time/LocalDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_time_of_instant", arguments);
        }
        if ("java/time/LocalDateTime".equals(target.owner()) && "atZone".equals(target.name()) && "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_time_at_zone", arguments);
        }
        if ("java/time/LocalDateTime".equals(target.owner()) && "toLocalDate".equals(target.name()) && "()Ljava/time/LocalDate;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_time_to_local_date", arguments);
        }
        if ("java/time/LocalDateTime".equals(target.owner()) && "toLocalTime".equals(target.name()) && "()Ljava/time/LocalTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_local_date_time_to_local_time", arguments);
        }
        if ("java/time/ZonedDateTime".equals(target.owner()) && "toInstant".equals(target.name()) && "()Ljava/time/Instant;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zoned_date_time_to_instant", arguments);
        }
        if ("java/time/ZonedDateTime".equals(target.owner()) && "toLocalDate".equals(target.name()) && "()Ljava/time/LocalDate;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zoned_date_time_to_local_date", arguments);
        }
        if ("java/time/ZonedDateTime".equals(target.owner()) && "toLocalTime".equals(target.name()) && "()Ljava/time/LocalTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zoned_date_time_to_local_time", arguments);
        }
        if ("java/time/ZonedDateTime".equals(target.owner()) && "toLocalDateTime".equals(target.name()) && "()Ljava/time/LocalDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zoned_date_time_to_local_date_time", arguments);
        }
        if ("java/time/ZonedDateTime".equals(target.owner()) && "from".equals(target.name()) && "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/ZonedDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zoned_date_time_from_temporal", arguments);
        }
        if ("java/time/ZonedDateTime".equals(target.owner()) && "of".equals(target.name()) && "(Ljava/time/LocalDate;Ljava/time/LocalTime;Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_zoned_date_time_of", arguments);
        }
        if ("java/time/format/DateTimeFormatter".equals(target.owner()) && "parse".equals(target.name()) && "(Ljava/lang/CharSequence;)Ljava/time/temporal/TemporalAccessor;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_datetime_formatter_parse", arguments);
        }
        if ("java/time/temporal/TemporalQueries".equals(target.owner()) && "zone".equals(target.name()) && "()Ljava/time/temporal/TemporalQuery;".equals(target.descriptor())) {
            return IrExpression.stringLiteral("zone");
        }
        if ("java/time/temporal/TemporalQueries".equals(target.owner()) && "localDate".equals(target.name()) && "()Ljava/time/temporal/TemporalQuery;".equals(target.descriptor())) {
            return IrExpression.stringLiteral("localDate");
        }
        if ("java/time/temporal/TemporalQueries".equals(target.owner()) && "localTime".equals(target.name()) && "()Ljava/time/temporal/TemporalQuery;".equals(target.descriptor())) {
            return IrExpression.stringLiteral("localTime");
        }
        if ("java/util/Date".equals(target.owner()) && "from".equals(target.name()) && "(Ljava/time/Instant;)Ljava/util/Date;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_date_from_instant", arguments);
        }
        if ("java/util/Date".equals(target.owner()) && "toInstant".equals(target.name()) && "()Ljava/time/Instant;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_date_to_instant", arguments);
        }
        if ("java/util/Date".equals(target.owner()) && "getTime".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_date_get_time", arguments);
        }
        if ("java/sql/Date".equals(target.owner()) && "valueOf".equals(target.name()) && "(Ljava/time/LocalDate;)Ljava/sql/Date;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_date_value_of_local_date", arguments);
        }
        if ("java/sql/Date".equals(target.owner()) && "getTime".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_sql_date_get_time", arguments);
        }
        if ("java/sql/Date".equals(target.owner()) && "toLocalDate".equals(target.name()) && "()Ljava/time/LocalDate;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_date_to_local_date", arguments);
        }
        if ("java/sql/Time".equals(target.owner()) && "valueOf".equals(target.name()) && "(Ljava/time/LocalTime;)Ljava/sql/Time;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_time_value_of_local_time", arguments);
        }
        if ("java/sql/Time".equals(target.owner()) && "getTime".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_sql_time_get_time", arguments);
        }
        if ("java/sql/Time".equals(target.owner()) && "toLocalTime".equals(target.name()) && "()Ljava/time/LocalTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_time_to_local_time", arguments);
        }
        if ("java/sql/Timestamp".equals(target.owner()) && "from".equals(target.name()) && "(Ljava/time/Instant;)Ljava/sql/Timestamp;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_timestamp_from_instant", arguments);
        }
        if ("java/sql/Timestamp".equals(target.owner()) && "valueOf".equals(target.name()) && "(Ljava/time/LocalDateTime;)Ljava/sql/Timestamp;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_timestamp_value_of_local_date_time", arguments);
        }
        if ("java/sql/Timestamp".equals(target.owner()) && "getTime".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_sql_timestamp_get_time", arguments);
        }
        if ("java/sql/Timestamp".equals(target.owner()) && "toInstant".equals(target.name()) && "()Ljava/time/Instant;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_timestamp_to_instant", arguments);
        }
        if ("java/sql/Timestamp".equals(target.owner()) && "toLocalDateTime".equals(target.name()) && "()Ljava/time/LocalDateTime;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_sql_timestamp_to_local_date_time", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicBoolean".equals(target.owner()) && "get".equals(target.name()) && "()Z".equals(target.descriptor())) {
            return IrExpression.intCall("javan_atomic_boolean_get", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicInteger".equals(target.owner()) && "get".equals(target.name()) && "()I".equals(target.descriptor())) {
            return IrExpression.intCall("javan_atomic_integer_get", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicLong".equals(target.owner()) && "get".equals(target.name()) && "()J".equals(target.descriptor())) {
            return IrExpression.longCall("javan_atomic_long_get", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(target.owner()) && "get".equals(target.name()) && "()Ljava/lang/Object;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_atomic_reference_get", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(target.owner())
            && "set".equals(target.name())
            && "(Ljava/lang/Object;)V".equals(target.descriptor())) {
            return new IrExpression(IrExpression.Kind.CALL, IrType.VOID, "javan_atomic_reference_set", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(target.owner())
            && "compareAndSet".equals(target.name())
            && "(Ljava/lang/Object;Ljava/lang/Object;)Z".equals(target.descriptor())) {
            return IrExpression.intCall("javan_atomic_reference_compare_and_set", arguments);
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(target.owner())
            && "getAndSet".equals(target.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor())) {
            return IrExpression.objectCall("javan_atomic_reference_get_and_set", arguments);
        }
        if ("java/util/Collection".equals(target.owner())
            && "add".equals(target.name())
            && "(Ljava/lang/Object;)Z".equals(target.descriptor())) {
            return IrExpression.intCall("javan_collection_add", arguments);
        }
        if ("java/nio/file/Path".equals(target.owner()) && "toString".equals(target.name()) && "()Ljava/lang/String;".equals(target.descriptor())) {
            return arguments.getFirst();
        }
        throw new IllegalStateException("unsupported JDK lambda bridge target: " + target.display());
    }

    private static IrExpression adaptBridgeArgument(
        final String sourceDescriptor,
        final IrExpression sourceExpression,
        final String implementationDescriptor
    ) {
        if (sourceDescriptor.equals(implementationDescriptor)) {
            return sourceExpression;
        }
        if (isObjectDescriptor(sourceDescriptor) && isObjectDescriptor(implementationDescriptor)) {
            return sourceExpression;
        }
        if (!isPrimitiveDescriptor(implementationDescriptor)) {
            throw new IllegalStateException("unsupported lambda bridge argument adaptation: " + sourceDescriptor + " -> " + implementationDescriptor);
        }
        return switch (implementationDescriptor.charAt(0)) {
            case 'I' -> intUnboxExpression(sourceDescriptor, sourceExpression);
            case 'J' -> longUnboxExpression(sourceDescriptor, sourceExpression);
            case 'F' -> floatUnboxExpression(sourceDescriptor, sourceExpression);
            case 'D' -> doubleUnboxExpression(sourceDescriptor, sourceExpression);
            case 'Z' -> booleanUnboxExpression(sourceDescriptor, sourceExpression);
            case 'C' -> charUnboxExpression(sourceDescriptor, sourceExpression);
            default -> throw new IllegalStateException("unsupported lambda bridge primitive parameter: " + implementationDescriptor);
        };
    }

    private static IrExpression adaptBridgeReturn(
        final MethodRef target,
        final IrExpression implementationResult,
        final String implementationReturnDescriptor,
        final String instantiatedReturnDescriptor,
        final IrType erasedReturnType
    ) {
        if (erasedReturnType == IrType.VOID || "V".equals(instantiatedReturnDescriptor)) {
            return implementationResult;
        }
        if (erasedReturnType == IrType.OBJECT) {
            if (isPrimitiveDescriptor(implementationReturnDescriptor)) {
                return boxPrimitiveExpression(instantiatedReturnDescriptor, implementationReturnDescriptor.charAt(0), implementationResult);
            }
            return implementationResult;
        }
        if (erasedReturnType == implementationResult.type()) {
            return implementationResult;
        }
        throw new IllegalStateException("unsupported lambda bridge return adaptation for " + target.display());
    }

    private static IrExpression boxPrimitiveExpression(
        final String targetDescriptor,
        final char primitive,
        final IrExpression primitiveExpression
    ) {
        return switch (primitive) {
            case 'I' -> IrExpression.objectCall("javan_integer_value_of", List.of(primitiveExpression));
            case 'J' -> IrExpression.objectCall("javan_long_value_of", List.of(primitiveExpression));
            case 'F' -> IrExpression.objectCall("javan_float_value_of", List.of(primitiveExpression));
            case 'D' -> IrExpression.objectCall("javan_double_value_of", List.of(primitiveExpression));
            case 'Z' -> IrExpression.objectCall("javan_boolean_value_of", List.of(primitiveExpression));
            case 'C' -> IrExpression.objectCall("javan_character_value_of", List.of(primitiveExpression));
            default -> throw new IllegalStateException("unsupported lambda bridge boxing target: " + targetDescriptor);
        };
    }

    private static IrExpression intUnboxExpression(final String sourceDescriptor, final IrExpression sourceExpression) {
        if ("Ljava/lang/Integer;".equals(sourceDescriptor)) {
            return IrExpression.intCall("javan_integer_int_value", List.of(sourceExpression));
        }
        if ("Ljava/lang/Number;".equals(sourceDescriptor)) {
            return IrExpression.intCall("javan_number_int_value", List.of(sourceExpression));
        }
        throw new IllegalStateException("unsupported lambda bridge int source: " + sourceDescriptor);
    }

    private static IrExpression longUnboxExpression(final String sourceDescriptor, final IrExpression sourceExpression) {
        if ("Ljava/lang/Long;".equals(sourceDescriptor)) {
            return IrExpression.longCall("javan_long_long_value", List.of(sourceExpression));
        }
        throw new IllegalStateException("unsupported lambda bridge long source: " + sourceDescriptor);
    }

    private static IrExpression floatUnboxExpression(final String sourceDescriptor, final IrExpression sourceExpression) {
        if ("Ljava/lang/Float;".equals(sourceDescriptor)) {
            return IrExpression.floatCall("javan_float_float_value", List.of(sourceExpression));
        }
        throw new IllegalStateException("unsupported lambda bridge float source: " + sourceDescriptor);
    }

    private static IrExpression doubleUnboxExpression(final String sourceDescriptor, final IrExpression sourceExpression) {
        if ("Ljava/lang/Double;".equals(sourceDescriptor)) {
            return IrExpression.doubleCall("javan_double_double_value", List.of(sourceExpression));
        }
        throw new IllegalStateException("unsupported lambda bridge double source: " + sourceDescriptor);
    }

    private static IrExpression booleanUnboxExpression(final String sourceDescriptor, final IrExpression sourceExpression) {
        if ("Ljava/lang/Boolean;".equals(sourceDescriptor)) {
            return IrExpression.intCall("javan_boolean_boolean_value", List.of(sourceExpression));
        }
        throw new IllegalStateException("unsupported lambda bridge boolean source: " + sourceDescriptor);
    }

    private static IrExpression charUnboxExpression(final String sourceDescriptor, final IrExpression sourceExpression) {
        if ("Ljava/lang/Character;".equals(sourceDescriptor)) {
            return IrExpression.intCall("javan_character_char_value", List.of(sourceExpression));
        }
        throw new IllegalStateException("unsupported lambda bridge char source: " + sourceDescriptor);
    }

    private static List<String> parameterDescriptors(final String descriptor) {
        final List<String> result = new ArrayList<>();
        int index = descriptor.indexOf('(') + 1;
        while (index > 0 && index < descriptor.length() && descriptor.charAt(index) != ')') {
            final int start = index;
            while (descriptor.charAt(index) == '[') {
                index++;
            }
            if (descriptor.charAt(index) == 'L') {
                index = descriptor.indexOf(';', index) + 1;
            } else {
                index++;
            }
            result.add(descriptor.substring(start, index));
        }
        return List.copyOf(result);
    }

    private static String returnDescriptor(final String descriptor) {
        final int end = descriptor.indexOf(')');
        return descriptor.substring(end + 1);
    }

    private static boolean isPrimitiveDescriptor(final String descriptor) {
        return descriptor.length() == 1 && "BCDFIJSZV".indexOf(descriptor.charAt(0)) >= 0;
    }

    private static boolean isObjectDescriptor(final String descriptor) {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, StackValue> specialObjectLocals,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final SourceLineIndex sourceLines,
        final LambdaMetafactorySupport.Registry lambdaRegistry
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
                stack.add(localObjectValue(classFile, method, locals, objectLocalKinds, objectLocalThrowableTypes, specialObjectLocals, unsigned(instruction.operands()[0])));
                break;
            case 42:
            case 43:
            case 44:
            case 45:
                stack.add(localObjectValue(classFile, method, locals, objectLocalKinds, objectLocalThrowableTypes, specialObjectLocals, instruction.opcode() - 42));
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
                storeInt(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 55:
                storeLong(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 56:
                storeFloat(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 57:
                storeDouble(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 59:
            case 60:
            case 61:
            case 62:
                storeInt(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, instruction.opcode() - 59);
                break;
            case 63:
            case 64:
            case 65:
            case 66:
                storeLong(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, instruction.opcode() - 63);
                break;
            case 67:
            case 68:
            case 69:
            case 70:
                storeFloat(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, instruction.opcode() - 67);
                break;
            case 71:
            case 72:
            case 73:
            case 74:
                storeDouble(classFile, method, instructions, stack, locals, specialObjectLocals, localDeclarations, instruction.opcode() - 71);
                break;
            case 58:
                storeObject(classFile, method, instruction, instructions, stack, locals, objectLocalKinds, objectLocalThrowableTypes, specialObjectLocals, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 75:
            case 76:
            case 77:
            case 78:
                storeObject(classFile, method, instruction, instructions, stack, locals, objectLocalKinds, objectLocalThrowableTypes, specialObjectLocals, localDeclarations, instruction.opcode() - 75);
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
            case 88:
                if (!stack.isEmpty()) {
                    discardTopTwo(instructions, stack);
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
            case 138:
                stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_l2d", List.of(popLong(classFile, method, stack)))));
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
                BytecodeToIRInvokeSupport.lowerInstanceCall(
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
                BytecodeToIRInvokeSupport.lowerDynamicCall(
                    lambdaRegistry,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations
                );
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
        final Map<Integer, StackValue> specialObjectLocals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popInt(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.INT);
        specialObjectLocals.remove(slot);
        instructions.add(IrInstruction.assignInt(target.value(), value));
    }

    static void storeLong(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackValue> specialObjectLocals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popLong(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.LONG);
        specialObjectLocals.remove(slot);
        instructions.add(IrInstruction.assignLong(target.value(), value));
    }

    static void storeFloat(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackValue> specialObjectLocals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popFloat(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.FLOAT);
        specialObjectLocals.remove(slot);
        instructions.add(IrInstruction.assignFloat(target.value(), value));
    }

    static void storeDouble(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackValue> specialObjectLocals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        final IrExpression value = popDouble(classFile, method, stack);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.DOUBLE);
        specialObjectLocals.remove(slot);
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, StackValue> specialObjectLocals,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        if (stack.isEmpty()) {
            if (isSyntheticSwitchMapInitializer(classFile, method) && isEnumSwitchMapHandlerInstruction(instruction.opcode())) {
                return;
            }
            throw invalidStack(classFile, method, instruction, "object store requires a value on the bytecode stack");
        }
        final StackValue value = pop(stack);
        if (!BytecodeToIRControlFlowSupport.isObjectLike(value.kind())
            && value.kind() != StackKind.OBJECT_STREAM
            && value.kind() != StackKind.INT_STREAM
            && value.kind() != StackKind.STREAM_COLLECTOR
            && value.kind() != StackKind.COMPARATOR) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
        }
        if (value.kind() == StackKind.OBJECT_STREAM
            || value.kind() == StackKind.INT_STREAM
            || value.kind() == StackKind.STREAM_COLLECTOR
            || value.kind() == StackKind.COMPARATOR) {
            localOrCreate(locals, localDeclarations, slot, IrType.OBJECT);
            specialObjectLocals.put(slot, value);
            updateObjectLocalKind(objectLocalKinds, slot, value.kind());
            objectLocalThrowableTypes.put(slot, null);
            return;
        }
        specialObjectLocals.remove(slot);
        final IrExpression target = localOrCreate(locals, localDeclarations, slot, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(target.value(), stackValueExpression(value)));
        updateObjectLocalKind(objectLocalKinds, slot, value.kind());
        if (value.throwableType().isPresent()) {
            objectLocalThrowableTypes.put(slot, value.throwableType().orElseThrow());
        } else {
            objectLocalThrowableTypes.put(slot, null);
        }
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
        return stackValueExpression(popObjectValue(classFile, method, instruction, stack));
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
            if (value.kind() == StackKind.PRINT_STREAM) {
                return StackValue.objectExpression(IrExpression.objectCall("javan_system_out", List.of()));
            }
            if (value.kind() == StackKind.ERROR_PRINT_STREAM) {
                return StackValue.objectExpression(IrExpression.objectCall("javan_system_err", List.of()));
            }
            return value;
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

    static IrExpression stackValueExpression(final StackValue value) {
        if (value.kind() == StackKind.PRINT_STREAM) {
            return IrExpression.objectCall("javan_system_out", List.of());
        }
        if (value.kind() == StackKind.ERROR_PRINT_STREAM) {
            return IrExpression.objectCall("javan_system_err", List.of());
        }
        return value.expression().orElseThrow();
    }

    static Instruction firstInstruction(final MethodInfo method) {
        return method.code().orElseThrow().instructions().getFirst();
    }

    static void discardTop(final List<IrInstruction> instructions, final List<StackValue> stack) {
        final StackValue value = pop(stack);
        emitDiscardedValue(instructions, value);
    }

    static void discardTopTwo(final List<IrInstruction> instructions, final List<StackValue> stack) {
        final StackValue top = pop(stack);
        if (top.kind() == StackKind.LONG || top.kind() == StackKind.DOUBLE) {
            emitDiscardedValue(instructions, top);
            return;
        }
        final StackValue next = pop(stack);
        emitDiscardedValue(instructions, next);
        emitDiscardedValue(instructions, top);
    }

    private static void emitDiscardedValue(final List<IrInstruction> instructions, final StackValue value) {
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, StackValue> specialObjectLocals,
        final int slot
    ) {
        final StackValue special = specialObjectLocals.get(slot);
        if (special != null) {
            return special;
        }
        final IrExpression expression = local(classFile, method, locals, slot, IrType.OBJECT);
        final StackKind kind = objectLocalKinds.getOrDefault(slot, StackKind.OBJECT);
        if (kind == StackKind.OBJECT) {
            final String throwableType = objectLocalThrowableTypes.get(slot);
            if (throwableType != null) {
                return StackValue.platformThrowable(throwableType, expression);
            }
        }
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
        if (kind != StackKind.OBJECT) {
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
        if (isLinkOptionNoFollowLinks(fieldRef)) {
            return true;
        }
        return isSupportedChronoField(fieldRef);
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

    static boolean isSupportedChronoField(final FieldRef fieldRef) {
        if (!"java/time/temporal/ChronoField".equals(fieldRef.owner())) {
            return false;
        }
        if (!"Ljava/time/temporal/ChronoField;".equals(fieldRef.descriptor())) {
            return false;
        }
        return "NANO_OF_SECOND".equals(fieldRef.name())
            || "INSTANT_SECONDS".equals(fieldRef.name())
            || "OFFSET_SECONDS".equals(fieldRef.name())
            || "EPOCH_DAY".equals(fieldRef.name())
            || "NANO_OF_DAY".equals(fieldRef.name());
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
                final Optional<EntryPoint> resolved = lowerableResolvedInvokeVirtualTarget(classes, candidate.name(), methodRef);
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
        return lowerableMethodTarget(classes, resolved.orElseThrow());
    }

    static Optional<EntryPoint> lowerableResolvedInvokeVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef methodRef
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, methodRef);
        if (resolved.isPresent()) {
            return lowerableMethodTarget(classes, resolved.orElseThrow());
        }
        final List<String> inspectedInterfaces = new ArrayList<>();
        for (final String interfaceName : implementedInterfaces(classes, receiver)) {
            if (hasMoreSpecificInterface(classes, inspectedInterfaces, interfaceName)) {
                continue;
            }
            inspectedInterfaces.add(interfaceName);
            final Optional<EntryPoint> interfaceDefault = defaultInterfaceTarget(classes, interfaceName, methodRef, new ArrayList<>());
            if (interfaceDefault.isPresent()) {
                return interfaceDefault;
            }
        }
        return Optional.empty();
    }

    private static Optional<EntryPoint> lowerableMethodTarget(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint
    ) {
        final ClassFile owner = classes.get(entryPoint.className());
        if (owner == null) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(entryPoint.methodName(), entryPoint.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entryPoint);
    }

    private static List<String> implementedInterfaces(final Map<String, ClassFile> classes, final String receiver) {
        final List<String> interfaces = new ArrayList<>();
        String current = receiver;
        while (current != null && !current.isEmpty()) {
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                break;
            }
            collectInterfaceNames(classes, classFile, interfaces, new ArrayList<>());
            current = classFile.superName();
        }
        return List.copyOf(interfaces);
    }

    private static void collectInterfaceNames(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final List<String> interfaces,
        final List<String> visited
    ) {
        for (final String interfaceName : classFile.interfaces()) {
            if (visited.contains(interfaceName)) {
                continue;
            }
            visited.add(interfaceName);
            if (!interfaces.contains(interfaceName)) {
                interfaces.add(interfaceName);
            }
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null) {
                collectInterfaceNames(classes, interfaceClass, interfaces, visited);
            }
        }
    }

    private static boolean hasMoreSpecificInterface(
        final Map<String, ClassFile> classes,
        final List<String> inspectedInterfaces,
        final String candidate
    ) {
        for (final String inspected : inspectedInterfaces) {
            if (isAssignableTo(classes, inspected, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<EntryPoint> defaultInterfaceTarget(
        final Map<String, ClassFile> classes,
        final String interfaceName,
        final MethodRef target,
        final List<String> visited
    ) {
        if (visited.contains(interfaceName)) {
            return Optional.empty();
        }
        visited.add(interfaceName);
        final ClassFile interfaceClass = classes.get(interfaceName);
        if (interfaceClass == null || !interfaceClass.isInterface()) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = interfaceClass.method(target.name(), target.descriptor());
        if (method.isPresent()) {
            if (method.orElseThrow().code().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new EntryPoint(interfaceName, target.name(), target.descriptor()));
        }
        for (final String parentInterface : interfaceClass.interfaces()) {
            final Optional<EntryPoint> resolved = defaultInterfaceTarget(classes, parentInterface, target, visited);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
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
        if ("java/lang/Character".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_CHARACTER);
        }
        return Optional.empty();
    }

    static List<Integer> platformWrapperSuperTypeIds(final String target) {
        if ("java/lang/Number".equals(target)) {
            return List.of(
                TYPE_JAVA_LANG_INTEGER,
                TYPE_JAVA_LANG_LONG,
                TYPE_JAVA_LANG_FLOAT,
                TYPE_JAVA_LANG_DOUBLE
            );
        }
        return List.of();
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
        if ("java/net/http/HttpRequest".equals(methodRef.owner())) {
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

    static boolean isPlatformThrowableAddSuppressed(final MethodRef methodRef) {
        if (!"addSuppressed".equals(methodRef.name())) {
            return false;
        }
        if (!"(Ljava/lang/Throwable;)V".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isPlatformThrowableGetSuppressed(final MethodRef methodRef) {
        if (!"getSuppressed".equals(methodRef.name())) {
            return false;
        }
        if (!"()[Ljava/lang/Throwable;".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isPlatformThrowableGetStackTrace(final MethodRef methodRef) {
        if (!"getStackTrace".equals(methodRef.name())) {
            return false;
        }
        if (!"()[Ljava/lang/StackTraceElement;".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isPlatformThrowableSetStackTrace(final MethodRef methodRef) {
        if (!"setStackTrace".equals(methodRef.name())) {
            return false;
        }
        if (!"([Ljava/lang/StackTraceElement;)V".equals(methodRef.descriptor())) {
            return false;
        }
        return isKnownPlatformThrowable(methodRef.owner());
    }

    static boolean isKnownPlatformThrowable(final String owner) {
        return JdkCallSupport.isPlatformThrowable(owner);
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
        OBJECT_STREAM,
        INT_STREAM,
        STREAM_COLLECTOR,
        COMPARATOR,
        VIRTUAL_THREAD_BUILDER,
        VIRTUAL_THREAD_FACTORY,
        VIRTUAL_THREAD_EXECUTOR,
        PRINT_STREAM,
        ERROR_PRINT_STREAM,
        SOCKET_INPUT_STREAM,
        SOCKET_OUTPUT_STREAM,
        HTTP_INPUT_STREAM,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        OBJECT
    }

    enum StreamOperationKind {
        FILTER,
        MAP
    }

    enum ComparatorKind {
        REVERSE_NATURAL,
        COMPARING
    }

    record StreamOperation(
        StreamOperationKind kind,
        IrExpression function,
        MethodRef interfaceMethod
    ) {
    }

    record ComparatorPlan(
        ComparatorKind kind,
        Optional<IrExpression> function,
        Optional<MethodRef> interfaceMethod,
        Optional<ComparatorPlan> downstream
    ) {
        static ComparatorPlan reverseNatural() {
            return new ComparatorPlan(
                ComparatorKind.REVERSE_NATURAL,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
        }

        static ComparatorPlan comparing(
            final IrExpression function,
            final MethodRef interfaceMethod,
            final ComparatorPlan downstream
        ) {
            return new ComparatorPlan(
                ComparatorKind.COMPARING,
                Optional.of(function),
                Optional.of(interfaceMethod),
                Optional.of(downstream)
            );
        }
    }

    record StreamToIntOperation(
        IrExpression function,
        MethodRef interfaceMethod
    ) {
    }

    record StreamPlan(
        IrExpression source,
        List<StreamOperation> preSortOperations,
        Optional<ComparatorPlan> comparator,
        List<StreamOperation> postSortOperations,
        Optional<StreamToIntOperation> intTerminal
    ) {
        StreamPlan append(final StreamOperation operation) {
            if (comparator.isPresent()) {
                final List<StreamOperation> next = new ArrayList<>(postSortOperations);
                next.add(operation);
                return new StreamPlan(source, preSortOperations, comparator, List.copyOf(next), intTerminal);
            }
            final List<StreamOperation> next = new ArrayList<>(preSortOperations);
            next.add(operation);
            return new StreamPlan(source, List.copyOf(next), comparator, postSortOperations, intTerminal);
        }

        StreamPlan sorted(final ComparatorPlan comparatorPlan) {
            return new StreamPlan(source, preSortOperations, Optional.of(comparatorPlan), postSortOperations, intTerminal);
        }

        StreamPlan mapToInt(final IrExpression function, final MethodRef interfaceMethod) {
            return new StreamPlan(
                source,
                preSortOperations,
                comparator,
                postSortOperations,
                Optional.of(new StreamToIntOperation(function, interfaceMethod))
            );
        }
    }

    enum CollectorKind {
        JOINING,
        TO_LIST,
        COUNTING,
        GROUPING_BY_COUNTING,
        TO_COLLECTION,
        TO_MAP
    }

    record CollectorPlan(
        CollectorKind kind,
        IrExpression delimiter,
        IrExpression prefix,
        IrExpression suffix,
        IrExpression classifier,
        IrExpression keyMapper,
        IrExpression valueMapper,
        IrExpression mergeFunction,
        IrExpression supplier
    ) {
    }

    record BlockResult(List<IrInstruction> instructions, List<StackValue> stack) {
    }

    record StackValue(
        StackKind kind,
        Optional<String> throwableType,
        Optional<IrExpression> expression,
        Optional<StreamPlan> streamPlan,
        Optional<CollectorPlan> collectorPlan,
        Optional<ComparatorPlan> comparatorPlan
    ) {
        static StackValue virtualThreadBuilder() {
            return new StackValue(StackKind.VIRTUAL_THREAD_BUILDER, Optional.empty(), Optional.of(IrExpression.objectNull()), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue virtualThreadBuilder(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_BUILDER, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue virtualThreadFactory(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_FACTORY, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue virtualThreadExecutor(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_EXECUTOR, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue objectStream(final StreamPlan streamPlan) {
            return new StackValue(StackKind.OBJECT_STREAM, Optional.empty(), Optional.empty(), Optional.of(streamPlan), Optional.empty(), Optional.empty());
        }

        static StackValue intStream(final StreamPlan streamPlan) {
            return new StackValue(StackKind.INT_STREAM, Optional.empty(), Optional.empty(), Optional.of(streamPlan), Optional.empty(), Optional.empty());
        }

        static StackValue streamCollector(final CollectorPlan collectorPlan) {
            return new StackValue(StackKind.STREAM_COLLECTOR, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(collectorPlan), Optional.empty());
        }

        static StackValue comparator(final ComparatorPlan comparatorPlan) {
            return new StackValue(StackKind.COMPARATOR, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(comparatorPlan));
        }

        static StackValue printStream() {
            return new StackValue(StackKind.PRINT_STREAM, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue errorPrintStream() {
            return new StackValue(StackKind.ERROR_PRINT_STREAM, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue socketInputStream(final IrExpression expression) {
            return new StackValue(StackKind.SOCKET_INPUT_STREAM, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue socketOutputStream(final IrExpression expression) {
            return new StackValue(StackKind.SOCKET_OUTPUT_STREAM, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue httpInputStream(final IrExpression expression) {
            return new StackValue(StackKind.HTTP_INPUT_STREAM, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue intExpression(final IrExpression expression) {
            return new StackValue(StackKind.INT, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue longExpression(final IrExpression expression) {
            return new StackValue(StackKind.LONG, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue floatExpression(final IrExpression expression) {
            return new StackValue(StackKind.FLOAT, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue doubleExpression(final IrExpression expression) {
            return new StackValue(StackKind.DOUBLE, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue objectExpression(final IrExpression expression) {
            return new StackValue(StackKind.OBJECT, Optional.empty(), Optional.of(expression), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue platformThrowable(final String throwableType, final IrExpression message) {
            return new StackValue(StackKind.OBJECT, Optional.of(throwableType), Optional.of(message), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }
}
