package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.ClassInitializationGraph;
import javan.analysis.CMethodSymbols;
import javan.analysis.EntryPoint;
import javan.analysis.FunctionValueFlow;
import javan.build.NativeInteropConfig;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldRef;
import javan.classfile.FieldInfo;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.compat.ExactMethodSupport;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrFunction;
import javan.ir.IrMaterializedLambdaTarget;
import javan.ir.IrExpression;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers the initial supported bytecode subset to javan IR.
 */
public final class BytecodeToIR {
    static final int TYPE_JAVA_LANG_INTEGER = -1001;
    static final int TYPE_JAVA_LANG_LONG = -1002;
    static final int TYPE_JAVA_LANG_FLOAT = -1003;
    static final int TYPE_JAVA_LANG_DOUBLE = -1004;
    static final int TYPE_JAVA_LANG_BOOLEAN = -1005;
    static final int TYPE_JAVA_LANG_BYTE = -1015;
    static final int TYPE_JAVA_LANG_SHORT = -1016;
    static final int TYPE_JAVA_LANG_CHARACTER = -1014;
    static final int CLASS_EXACT_STRING = -2001;
    static final int CLASS_EXACT_OBJECT = -2002;
    static final int CLASS_EXACT_CLASS = -2003;
    static final int CLASS_EXACT_CLASS_LOADER = -2004;
    static final int CLASS_EXACT_ARRAY_LIST = -2005;
    static final int CLASS_EXACT_HASH_MAP = -2006;
    static final int CLASS_EXACT_PRIMITIVE_BOOLEAN = -2007;
    static final int CLASS_EXACT_PRIMITIVE_BYTE = -2008;
    static final int CLASS_EXACT_PRIMITIVE_SHORT = -2009;
    static final int CLASS_EXACT_PRIMITIVE_CHAR = -2010;
    static final int CLASS_EXACT_PRIMITIVE_INT = -2011;
    static final int CLASS_EXACT_PRIMITIVE_LONG = -2012;
    static final int CLASS_EXACT_PRIMITIVE_FLOAT = -2013;
    static final int CLASS_EXACT_PRIMITIVE_DOUBLE = -2014;
    static final int CLASS_EXACT_PRIMITIVE_VOID = -2015;

    /**
     * Lowers reachable methods to IR.
     *
     * @param classes parsed classes
     * @param callGraph reachable call graph
     * @return lowered IR program
     */
    public IrProgram lower(final Map<String, ClassFile> classes, final CallGraph callGraph) {
        return lower(classes, callGraph, SourceLineIndex.empty(), NativeInteropConfig.empty());
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
        return lower(classes, callGraph, sourceLines, NativeInteropConfig.empty());
    }

    /**
     * Lowers reachable methods to IR with source-line lookup and declared native imports.
     *
     * @param classes parsed classes
     * @param callGraph reachable call graph
     * @param sourceLines source-line index
     * @param nativeInterop declared native imports
     * @return lowered IR program
     */
    public IrProgram lower(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final SourceLineIndex sourceLines,
        final NativeInteropConfig nativeInterop
    ) {
        final List<EntryPoint> reachableMethods = BytecodeToIRMetadataSupport.sortedEntryPoints(callGraph.reachableMethods());
        return lower(
            classes,
            callGraph,
            sourceLines,
            nativeInterop,
            ClassInitializationGraph.analyze(classes, reachableMethods)
        );
    }

    /**
     * Lowers reachable methods using the class-initialization model produced by {@code check}.
     *
     * @param classes parsed classes
     * @param callGraph reachable call graph
     * @param sourceLines source-line index
     * @param nativeInterop declared native imports
     * @param classInitialization checked runtime initialization model
     * @return lowered IR program
     */
    public IrProgram lower(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final SourceLineIndex sourceLines,
        final NativeInteropConfig nativeInterop,
        final ClassInitializationGraph.Result classInitialization
    ) {
        final List<IrFunction> functions = new ArrayList<>();
        final Map<String, IrDispatch> dispatches = new LinkedHashMap<>();
        final List<EntryPoint> reachableMethods = BytecodeToIRMetadataSupport.sortedEntryPoints(callGraph.reachableMethods());
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets =
            BytecodeToIRDynamicSupport.functionOrNullTargets(classes, reachableMethods);
        final Map<String, Integer> functionOrNullTargetIds =
            BytecodeToIRDynamicSupport.functionOrNullTargetIds(classes, reachableMethods);
        final Map<MethodRef, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods =
            BytecodeToIRDynamicSupport.materializedLambdaMethods(classes, reachableMethods);
        final FunctionValueFlow.Result functionValueFlow = callGraph.functionValueFlow().complete()
            ? callGraph.functionValueFlow()
            : FunctionValueFlow.analyze(classes, reachableMethods, nativeInterop.nativeEntryPoints());
        final List<EntryPoint> runnableThreadTargets = BytecodeToIRThreadSupport.runnableThreadTargets(classes, reachableMethods);
        final Map<String, List<String>> transportedThrowableTypes =
            transportedThrowableTypes(classes, callGraph, reachableMethods, materializedLambdaTargets);
        if (!runnableThreadTargets.isEmpty()) {
            final MethodRef runnableRun = BytecodeToIRThreadSupport.runnableRunMethodRef();
            final String dispatchSymbol = dispatchSymbol(runnableRun);
            dispatches.putIfAbsent(
                dispatchSymbol,
                BytecodeToIRDynamicSupport.dispatch(
                    dispatchSymbol,
                    MethodDescriptor.parse(runnableRun.descriptor()),
                    runnableThreadTargets
                )
            );
        }
        for (final EntryPoint reachable : reachableMethods) {
            if (configuredNativeLeaf(classes, reachable, nativeInterop)) {
                continue;
            }
            functions.add(lowerFunction(
                classes,
                reachable,
                dispatches,
                functionOrNullTargetIds,
                materializedLambdaMethods,
                functionValueFlow,
                transportedThrowableTypes,
                classInitialization,
                sourceLines
            ));
        }
        return new IrProgram(
            BytecodeToIRMetadataSupport.lowerClasses(classes),
            List.copyOf(functions),
            List.copyOf(dispatches.values()),
            symbol(callGraph.entryPoint()),
            List.copyOf(materializedLambdaTargets),
            classInitialization.dependencies(),
            enumDispatchConstants(classes)
        );
    }

    private static boolean configuredNativeLeaf(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint,
        final NativeInteropConfig nativeInterop
    ) {
        if (nativeInterop.importBinding(entryPoint).isEmpty()) {
            return false;
        }
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return false;
        }
        final Optional<MethodInfo> resolved = classFile.method(entryPoint.methodName(), entryPoint.descriptor());
        if (resolved.isEmpty()) {
            return false;
        }
        final MethodInfo method = resolved.orElseThrow();
        return method.isNative() && method.isStatic() && method.code().isEmpty();
    }

    private static Map<String, List<String>> transportedThrowableTypes(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final List<EntryPoint> reachableMethods,
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets
    ) {
        final Map<EntryPoint, Set<String>> typesByMethod = new LinkedHashMap<>();
        for (final EntryPoint entryPoint : reachableMethods) {
            final ClassFile classFile = classes.get(entryPoint.className());
            final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElseThrow();
            typesByMethod.put(entryPoint, directEscapingThrowableTypes(method));
        }
        final Map<String, EntryPoint> entryPointsBySymbol = new HashMap<>();
        for (final EntryPoint entryPoint : reachableMethods) {
            entryPointsBySymbol.put(symbol(entryPoint), entryPoint);
        }
        final Map<EntryPoint, List<EntryPoint>> calleesByCaller = callCalleesByCaller(callGraph);
        boolean changed;
        do {
            changed = false;
            for (final EntryPoint caller : reachableMethods) {
                final ClassFile classFile = classes.get(caller.className());
                final MethodInfo method = classFile.method(caller.methodName(), caller.descriptor()).orElseThrow();
                if (method.code().isEmpty()) {
                    continue;
                }
                final Set<String> callerTypes = typesByMethod.get(caller);
                for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                    if (instruction.methodRef().isEmpty()) {
                        continue;
                    }
                    final MethodRef calledMethod = instruction.methodRef().orElseThrow();
                    for (final EntryPoint callee : calleesByCaller.getOrDefault(caller, List.of())) {
                        if (!calledMethod.name().equals(callee.methodName())
                            || !calledMethod.descriptor().equals(callee.descriptor())) {
                            continue;
                        }
                        for (final String throwableType : typesByMethod.getOrDefault(callee, Set.of())) {
                            if (!caughtBy(method, instruction.offset(), throwableType) && callerTypes.add(throwableType)) {
                                changed = true;
                            }
                        }
                    }
                    for (final IrMaterializedLambdaTarget target : materializedLambdaTargets) {
                        if (!matchesMaterializedLambdaCall(calledMethod, target)) {
                            continue;
                        }
                        final EntryPoint implementation = entryPointsBySymbol.get(target.functionSymbol());
                        if (implementation == null) {
                            continue;
                        }
                        for (final String throwableType : typesByMethod.getOrDefault(implementation, Set.of())) {
                            if (!caughtBy(method, instruction.offset(), throwableType)
                                && callerTypes.add(throwableType)) {
                                changed = true;
                            }
                        }
                    }
                }
            }
        } while (changed);
        final Map<String, List<String>> result = new LinkedHashMap<>();
        for (final EntryPoint entryPoint : reachableMethods) {
            result.put(symbol(entryPoint), orderedThrowableTypes(typesByMethod.get(entryPoint)));
        }
        final Map<String, Set<String>> materializedTypes = new LinkedHashMap<>();
        for (final IrMaterializedLambdaTarget target : materializedLambdaTargets) {
            final String applySymbol = materializedLambdaApplySymbol(target);
            if (applySymbol.isEmpty()) {
                continue;
            }
            Set<String> applyTypes = materializedTypes.get(applySymbol);
            if (applyTypes == null) {
                applyTypes = new LinkedHashSet<>();
                materializedTypes.put(applySymbol, applyTypes);
            }
            applyTypes.addAll(result.getOrDefault(target.functionSymbol(), List.of()));
        }
        for (final Map.Entry<String, Set<String>> entry : materializedTypes.entrySet()) {
            result.put(entry.getKey(), orderedThrowableTypes(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<EntryPoint, List<EntryPoint>> callCalleesByCaller(final CallGraph callGraph) {
        final Map<EntryPoint, List<EntryPoint>> result = new HashMap<>();
        for (final javan.analysis.CallEdge edge : callGraph.callEdges()) {
            if (edge.kind() != javan.analysis.CallEdge.Kind.CALL) {
                continue;
            }
            List<EntryPoint> callees = result.get(edge.caller());
            if (callees == null) {
                callees = new ArrayList<>();
                result.put(edge.caller(), callees);
            }
            callees.add(edge.callee());
        }
        return result;
    }

    private static boolean matchesMaterializedLambdaCall(
        final MethodRef calledMethod,
        final IrMaterializedLambdaTarget target
    ) {
        return calledMethod.owner().equals(target.interfaceOwner())
            && calledMethod.name().equals(target.interfaceMethodName())
            && calledMethod.descriptor().equals(target.interfaceMethodDescriptor());
    }

    private static String materializedLambdaApplySymbol(final IrMaterializedLambdaTarget target) {
        final List<IrType> parameters =
            MethodDescriptor.parse(target.interfaceMethodDescriptor()).parameterTypes();
        if (target.voidResult()) {
            if (parameters.size() == 1) {
                return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL;
            }
            if (parameters.size() == 2) {
                return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL;
            }
            return "";
        }
        if (target.booleanResult()) {
            if (parameters.size() == 1) {
                return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL;
            }
            return "";
        }
        if ("java/util/function/Supplier".equals(target.interfaceOwner())
            && "get".equals(target.interfaceMethodName())
            && "()Ljava/lang/Object;".equals(target.interfaceMethodDescriptor())) {
            return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL;
        }
        if (parameters.size() == 1 && parameters.getFirst() == IrType.OBJECT) {
            return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL;
        }
        if (parameters.size() == 1 && parameters.getFirst() == IrType.LONG) {
            return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL;
        }
        if (parameters.size() == 2) {
            return BytecodeToIRInvokeSupport.MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL;
        }
        return "";
    }

    private static List<String> orderedThrowableTypes(final Set<String> throwableTypes) {
        final List<String> result = new ArrayList<>();
        if (throwableTypes.contains("java/lang/Throwable")) {
            result.add("java/lang/Throwable");
        }
        for (final JdkCallSupport.PlatformThrowableParent parent : JdkCallSupport.platformThrowableParents()) {
            if (throwableTypes.contains(parent.type())) {
                result.add(parent.type());
            }
        }
        for (final String throwableType : throwableTypes) {
            if (!result.contains(throwableType)) {
                result.add(throwableType);
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> directEscapingThrowableTypes(final MethodInfo method) {
        if (method.code().isEmpty()) {
            return new LinkedHashSet<>();
        }
        final CodeAttribute code = method.code().orElseThrow();
        final Set<String> allocatedTypes = new LinkedHashSet<>();
        final Set<String> result = new LinkedHashSet<>();
        for (final Instruction instruction : code.instructions()) {
            if (instruction.methodRef().isPresent()) {
                for (final String throwableType : JdkCallSupport.transportedPlatformThrowableTypes(
                    instruction.methodRef().orElseThrow()
                )) {
                    if (!caughtBy(method, instruction.offset(), throwableType)) {
                        result.add(throwableType);
                    }
                }
            }
            if (instruction.opcode() == 187
                && instruction.className().isPresent()
                && JdkCallSupport.isPlatformThrowable(instruction.className().orElseThrow())) {
                allocatedTypes.add(instruction.className().orElseThrow());
                continue;
            }
            if (instruction.opcode() != 191) {
                continue;
            }
            if (!allocatedTypes.isEmpty()) {
                for (final String throwableType : allocatedTypes) {
                    if (!caughtBy(method, instruction.offset(), throwableType)) {
                        result.add(throwableType);
                    }
                }
                allocatedTypes.clear();
            }
        }
        return result;
    }

    private static boolean caughtBy(final MethodInfo method, final int offset, final String throwableType) {
        if (method.code().isEmpty()) {
            return false;
        }
        for (final javan.classfile.CodeException handler : method.code().orElseThrow().exceptionTable()) {
            if (offset < handler.startPc() || offset >= handler.endPc()) {
                continue;
            }
            if (handler.catchType().isEmpty()
                || JdkCallSupport.isPlatformThrowableAssignable(throwableType, handler.catchType().orElseThrow())) {
                return !BytecodeToIRControlFlowSupport.handlerMayThrow(
                    method.code().orElseThrow(),
                    handler
                );
            }
        }
        return false;
    }

    private static Map<String, String> enumDispatchConstants(final Map<String, ClassFile> classes) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final ClassFile enumClass : classes.values()) {
            if (!enumClass.isEnum()) {
                continue;
            }
            final Optional<MethodInfo> initializer = enumClass.method("<clinit>", "()V");
            if (initializer.isEmpty() || initializer.orElseThrow().code().isEmpty()) {
                continue;
            }
            String allocatedClass = "";
            for (final Instruction instruction : initializer.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.opcode() == 187 && instruction.className().isPresent()) {
                    allocatedClass = instruction.className().orElseThrow();
                }
                if (instruction.opcode() == 179 && instruction.fieldRef().isPresent()) {
                    final FieldRef field = instruction.fieldRef().orElseThrow();
                    if (enumClass.name().equals(field.owner()) && !allocatedClass.isEmpty()) {
                        result.put(allocatedClass, field.name());
                    }
                    allocatedClass = "";
                }
            }
        }
        return Map.copyOf(result);
    }

    static IrFunction lowerFunction(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<MethodRef, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final Map<String, List<String>> transportedThrowableTypes,
        final ClassInitializationGraph.Result classInitialization,
        final SourceLineIndex sourceLines
    ) {
        final ClassFile classFile = classes.get(entryPoint.className());
        final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElseThrow();
        final MethodDescriptor descriptor = MethodDescriptor.parse(method.descriptor());
        final List<IrParameter> parameters = BytecodeToIRMetadataSupport.parameters(method, descriptor);
        if (ExactMethodSupport.isExactCatchNullEnumLookupMethod(classFile, method)) {
            return lowerExactCatchNullEnumLookupFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(classFile, method)) {
            return lowerExactCatchNullFunctionOrNullApplyFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactTemporalOfLoopFallbackMethod(classFile, method)) {
            return lowerExactTemporalOfFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactTemporalStringBridgeMethod(classFile, method)) {
            return lowerExactTemporalStringBridgeFunction(classFile, method, entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactCalendarOfEpochMillisMethod(classFile, method)) {
            return lowerExactCalendarOfEpochMillisFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactCalendarOfDateMethod(classFile, method)) {
            return lowerExactCalendarOfDateFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactCalendarOfLocalTimeMethod(classFile, method)) {
            return lowerExactCalendarOfLocalTimeFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactThrowableStringOfMethod(classFile, method)) {
            return lowerExactThrowableStringOfFunction(entryPoint, descriptor, parameters);
        }
        if (ExactMethodSupport.isExactUnsupportedTemporalConversionLambdaMethod(classFile, method)) {
            return lowerExactUnsupportedTemporalConversionLambdaFunction(entryPoint, descriptor, parameters);
        }
        final List<IrInstruction> instructions = new ArrayList<>();
        final List<StackValue> stack = new ArrayList<>();
        final Map<Integer, IrExpression> locals = new HashMap<>();
        final Map<Integer, StackKind> objectLocalKinds = new HashMap<>();
        final Map<Integer, String> objectLocalThrowableTypes = new HashMap<>();
        final Map<Integer, DynamicLambda> objectLocalLambdas = new HashMap<>();
        final Map<Integer, IrLocal> localDeclarations = new LinkedHashMap<>();
        final Map<Integer, StackValue> pendingExceptionHandlerStacks = new HashMap<>();
        final CodeAttribute code = method.code().orElseThrow();
        final List<Instruction> bytecode = code.instructions();
        final int enumBootstrapEndOffset = enumBootstrapEndOffset(classFile, method, bytecode);
        final int lastMaterializingDuplicateOffset = lastMaterializingDuplicateOffset(bytecode);
        final List<Integer> ignoredHandlerOffsets = ignoredEnumSwitchMapHandlerOffsets(classes, classFile, method, code);
        final List<Integer> handlerOffsets = exceptionHandlerOffsets(code);
        final List<Integer> branchTargets = branchTargets(code);
        final List<Integer> skippedOffsets = new ArrayList<>();
        final List<Integer> replacementLabelOffsets = new ArrayList<>();
        BytecodeToIRMetadataSupport.bindParameters(method, descriptor, parameters, locals);
        for (int index = 0; index < bytecode.size(); index++) {
            final Instruction instruction = bytecode.get(index);
            if (instruction.offset() <= enumBootstrapEndOffset) {
                continue;
            }
            final StackValue pendingException = containsInt(handlerOffsets, instruction.offset())
                ? pendingExceptionHandlerStacks.get(instruction.offset())
                : null;
            if (containsInt(branchTargets, instruction.offset())
                && !containsInt(replacementLabelOffsets, instruction.offset())) {
                instructions.add(IrInstruction.label(label(instruction.offset())));
            }
            if (containsInt(handlerOffsets, instruction.offset())) {
                if (containsInt(ignoredHandlerOffsets, instruction.offset())) {
                    if (pendingException != null) {
                        BytecodeToIRControlFlowSupport.clearStack(stack);
                        instructions.add(IrInstruction.callStaticVoid("javan_pending_clear"));
                    }
                } else if (pendingException != null) {
                    BytecodeToIRControlFlowSupport.clearStack(stack);
                    stack.add(materializePendingHandlerException(pendingException, instructions, localDeclarations));
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
                objectLocalThrowableTypes,
                objectLocalLambdas,
                localDeclarations,
                dispatches,
                functionOrNullTargetIds,
                materializedLambdaMethods,
                functionValueFlow,
                skippedOffsets,
                replacementLabelOffsets
            )) {
                appendPendingExceptionTransport(
                    method,
                    instruction,
                    instructions,
                    instructionStart,
                    dispatches,
                    transportedThrowableTypes,
                    pendingExceptionHandlerStacks
                );
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
                objectLocalLambdas,
                localDeclarations,
                dispatches,
                functionOrNullTargetIds,
                materializedLambdaMethods,
                functionValueFlow,
                pendingExceptionHandlerStacks,
                sourceLines,
                skippedOffsets,
                replacementLabelOffsets
            )) {
                appendPendingExceptionTransport(
                    method,
                    instruction,
                    instructions,
                    instructionStart,
                    dispatches,
                    transportedThrowableTypes,
                    pendingExceptionHandlerStacks
                );
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
                objectLocalLambdas,
                localDeclarations,
                dispatches,
                functionOrNullTargetIds,
                materializedLambdaMethods,
                functionValueFlow,
                classInitialization,
                sourceLines,
                lastMaterializingDuplicateOffset
            );
            appendPendingExceptionTransport(
                method,
                instruction,
                instructions,
                instructionStart,
                dispatches,
                transportedThrowableTypes,
                pendingExceptionHandlerStacks
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

    private static int enumBootstrapEndOffset(
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> bytecode
    ) {
        if (!classFile.isEnum() || !"<clinit>".equals(method.name()) || !"()V".equals(method.descriptor())) {
            return -1;
        }
        final String valuesDescriptor = "[L" + classFile.name() + ";";
        for (final Instruction instruction : bytecode) {
            if (instruction.opcode() != 179 || instruction.fieldRef().isEmpty()) {
                continue;
            }
            final FieldRef field = instruction.fieldRef().orElseThrow();
            if (classFile.name().equals(field.owner())
                && "$VALUES".equals(field.name())
                && valuesDescriptor.equals(field.descriptor())) {
                return instruction.offset();
            }
        }
        return -1;
    }

    private static void initializeClass(
        final Map<String, ClassFile> classes,
        final String currentOwner,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final ClassInitializationGraph.Result classInitialization
    ) {
        final String owner = classInitializationOwner(classes, instruction);
        if (!owner.equals(currentOwner) && classInitialization.initializes(owner)) {
            instructions.add(IrInstruction.initializeClass(owner));
        }
    }

    private static boolean initializesClass(
        final Map<String, ClassFile> classes,
        final String currentOwner,
        final Instruction instruction,
        final ClassInitializationGraph.Result classInitialization
    ) {
        final String owner = classInitializationOwner(classes, instruction);
        return !owner.equals(currentOwner) && classInitialization.initializes(owner);
    }

    private static String classInitializationOwner(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        final String owner;
        if (instruction.opcode() == 178 || instruction.opcode() == 179) {
            final FieldRef field = instruction.fieldRef().orElseThrow();
            owner = ClassInitializationGraph.staticFieldOwner(classes, field).orElse(field.owner());
        } else if (instruction.opcode() == 184) {
            final MethodRef method = instruction.methodRef().orElseThrow();
            owner = ClassInitializationGraph.staticMethodOwner(classes, method).orElse(method.owner());
        } else {
            owner = instruction.className().orElseThrow();
        }
        return owner;
    }

    private static StackValue materializePendingHandlerException(
        final StackValue pendingException,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (pendingException.kind() != StackKind.CAUGHT_THROWABLE
            || pendingException.expression().isEmpty()
            || pendingException.expression().orElseThrow().kind() != IrExpression.Kind.CALL
            || !"javan_pending_catch".equals(pendingException.expression().orElseThrow().value())) {
            return pendingException;
        }
        final String localName = "pendingException" + localDeclarations.size();
        localDeclarations.put(
            Integer.MIN_VALUE + localDeclarations.size(),
            new IrLocal(IrType.OBJECT, localName)
        );
        instructions.add(IrInstruction.assignObject(localName, pendingException.expression().orElseThrow()));
        return StackValue.caughtThrowable(IrExpression.objectLocal(localName));
    }

    private static void appendPendingExceptionTransport(
        final MethodInfo method,
        final Instruction bytecodeInstruction,
        final List<IrInstruction> instructions,
        final int instructionStart,
        final Map<String, IrDispatch> dispatches,
        final Map<String, List<String>> transportedThrowableTypes,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks
    ) {
        final Set<String> possibleTypes = new LinkedHashSet<>();
        for (int index = instructionStart; index < instructions.size(); index++) {
            collectTransportedThrowableTypes(
                instructions.get(index),
                dispatches,
                transportedThrowableTypes,
                possibleTypes
            );
        }
        if (possibleTypes.isEmpty()) {
            return;
        }
        BytecodeToIRControlFlowSupport.appendPendingExceptionDispatch(
            method,
            bytecodeInstruction,
            instructions,
            List.copyOf(possibleTypes),
            pendingExceptionHandlerStacks
        );
    }

    private static void collectTransportedThrowableTypes(
        final IrInstruction instruction,
        final Map<String, IrDispatch> dispatches,
        final Map<String, List<String>> transportedThrowableTypes,
        final Set<String> result
    ) {
        if (instruction.op() == IrInstruction.Op.CALL_STATIC_VOID && instruction.expression().isEmpty()) {
            addTransportedThrowableTypes(instruction.value().orElseThrow(), dispatches, transportedThrowableTypes, result);
        }
        if (instruction.expression().isPresent()) {
            collectTransportedThrowableTypes(
                instruction.expression().orElseThrow(),
                dispatches,
                transportedThrowableTypes,
                result
            );
        }
    }

    private static void collectTransportedThrowableTypes(
        final IrExpression expression,
        final Map<String, IrDispatch> dispatches,
        final Map<String, List<String>> transportedThrowableTypes,
        final Set<String> result
    ) {
        if (expression.kind() == IrExpression.Kind.CALL) {
            addTransportedThrowableTypes(expression.value(), dispatches, transportedThrowableTypes, result);
        }
        for (final IrExpression argument : expression.arguments()) {
            collectTransportedThrowableTypes(argument, dispatches, transportedThrowableTypes, result);
        }
    }

    private static void addTransportedThrowableTypes(
        final String symbol,
        final Map<String, IrDispatch> dispatches,
        final Map<String, List<String>> transportedThrowableTypes,
        final Set<String> result
    ) {
        result.addAll(transportedThrowableTypes.getOrDefault(symbol, List.of()));
        final IrDispatch dispatch = dispatches.get(symbol);
        if (dispatch == null) {
            return;
        }
        for (final IrDispatchTarget target : dispatch.targets()) {
            result.addAll(transportedThrowableTypes.getOrDefault(target.functionSymbol(), List.of()));
        }
    }

    private static IrFunction lowerExactCatchNullEnumLookupFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_enum_lookup",
                List.of(
                    IrExpression.objectLocal(parameters.get(0).name()),
                    IrExpression.objectLocal(parameters.get(1).name())
                )
            )))
        );
    }

    private static IrFunction lowerExactCatchNullFunctionOrNullApplyFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_catch_null_apply",
                List.of(
                    IrExpression.objectLocal(parameters.get(0).name()),
                    IrExpression.objectLocal(parameters.get(1).name())
                )
            )))
        );
    }

    private static IrFunction lowerExactTemporalOfFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_temporal_of_unsupported",
                List.of(
                    IrExpression.objectLocal(parameters.get(0).name()),
                    IrExpression.objectLocal(parameters.get(1).name()),
                    IrExpression.objectLocal(parameters.get(2).name())
                )
            )))
        );
    }

    private static IrFunction lowerExactTemporalStringBridgeFunction(
        final ClassFile classFile,
        final MethodInfo method,
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        final Optional<String> targetOwner = ExactMethodSupport.exactTemporalStringBridgeTargetInternalName(classFile, method);
        if (targetOwner.isEmpty()) {
            throw new IllegalArgumentException("exact temporal string bridge target is missing");
        }
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_temporal_string_bridge_unsupported",
                List.of(
                    IrExpression.objectLocal(parameters.getFirst().name()),
                    IrExpression.stringLiteral(targetOwner.orElseThrow().replace('/', '.'))
                )
            )))
        );
    }

    private static IrFunction lowerExactCalendarOfEpochMillisFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_calendar_of_millis_unsupported",
                List.of(IrExpression.longLocal(parameters.getFirst().name()))
            )))
        );
    }

    private static IrFunction lowerExactCalendarOfDateFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_calendar_of_date_unsupported",
                List.of(IrExpression.objectLocal(parameters.getFirst().name()))
            )))
        );
    }

    private static IrFunction lowerExactCalendarOfLocalTimeFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_calendar_of_local_time_unsupported",
                List.of(IrExpression.objectLocal(parameters.getFirst().name()))
            )))
        );
    }

    private static IrFunction lowerExactThrowableStringOfFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_exact_throwable_string_of_unsupported",
                List.of(IrExpression.objectLocal(parameters.getFirst().name()))
            )))
        );
    }

    private static IrFunction lowerExactUnsupportedTemporalConversionLambdaFunction(
        final EntryPoint entryPoint,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters
    ) {
        return new IrFunction(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            symbol(entryPoint),
            descriptor.returnType(),
            parameters,
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectCall(
                "javan_temporal_conversion_lambda_unsupported",
                List.of(IrExpression.stringLiteral(entryPoint.display()))
            )))
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<MethodRef, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final SourceLineIndex sourceLines,
        final int lastMaterializingDuplicateOffset
    ) {
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
            objectLocalLambdas,
            localDeclarations,
            dispatches,
            functionOrNullTargetIds,
            materializedLambdaMethods,
            functionValueFlow,
            ClassInitializationGraph.analyze(
                classes,
                List.of(new EntryPoint(classFile.name(), method.name(), method.descriptor()))
            ),
            sourceLines,
            lastMaterializingDuplicateOffset
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<String, Integer> functionOrNullTargetIds,
        final Map<MethodRef, BytecodeToIRInvokeSupport.MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final ClassInitializationGraph.Result classInitialization,
        final SourceLineIndex sourceLines,
        final int lastMaterializingDuplicateOffset
    ) {
        BytecodeToIRInvokeSupport.guardTypedReceiver(
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
                BytecodeToIRDynamicSupport.pushConstant(classes, classFile, method, instruction, stack);
                break;
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                BytecodeToIRDynamicSupport.pushConstant(classes, classFile, method, instruction, stack);
                break;
            case 16:
            case 17:
                BytecodeToIRDynamicSupport.pushConstant(classes, classFile, method, instruction, stack);
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
                stack.add(localObjectValue(classFile, method, locals, objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, unsigned(instruction.operands()[0])));
                break;
            case 42:
            case 43:
            case 44:
            case 45:
                stack.add(localObjectValue(classFile, method, locals, objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, instruction.opcode() - 42));
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
                storeObject(classFile, method, instruction, instructions, stack, locals, objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, localDeclarations, unsigned(instruction.operands()[0]));
                break;
            case 75:
            case 76:
            case 77:
            case 78:
                storeObject(classFile, method, instruction, instructions, stack, locals, objectLocalKinds, objectLocalThrowableTypes, objectLocalLambdas, localDeclarations, instruction.opcode() - 75);
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
            case 91:
                duplicateTopSlotUnderTwoSlots(
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    locals,
                    localDeclarations
                );
                break;
            case 92:
                duplicateTopTwoSlots(
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    locals,
                    localDeclarations
                );
                break;
            case 87:
                if (!stack.isEmpty()) {
                    discardTop(instructions, stack);
                }
                break;
            case 88:
                discardTopTwoSlots(classFile, method, instruction, instructions, stack);
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
            case 116:
                unaryIntNeg(classFile, method, stack);
                break;
            case 117:
                unaryLongNeg(classFile, method, stack);
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
            case 141:
                stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_f2d", List.of(popFloat(classFile, method, stack)))));
                break;
            case 138:
                stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_l2d", List.of(popLong(classFile, method, stack)))));
                break;
            case 142:
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_double_to_int", List.of(popDouble(classFile, method, stack)))));
                break;
            case 143:
                stack.add(StackValue.longExpression(IrExpression.longCall("javan_double_to_long", List.of(popDouble(classFile, method, stack)))));
                break;
            case 144:
                stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_d2f", List.of(popDouble(classFile, method, stack)))));
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
            case 200:
                instructions.add(IrInstruction.jump(label(wideBranchTarget(instruction))));
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
                initializeClass(classes, classFile.name(), instruction, instructions, classInitialization);
                BytecodeToIRInvokeSupport.pushField(classes, classFile, method, instruction, stack);
                break;
            case 179:
                if (initializesClass(classes, classFile.name(), instruction, classInitialization)) {
                    snapshotOperandStack(instructions, stack, locals, localDeclarations);
                    initializeClass(classes, classFile.name(), instruction, instructions, classInitialization);
                }
                BytecodeToIRInvokeSupport.assignStaticField(classes, classFile, method, instruction, instructions, stack);
                break;
            case 18:
            case 19:
            case 20:
                BytecodeToIRDynamicSupport.pushConstant(classes, classFile, method, instruction, stack);
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
                    materializedLambdaMethods,
                    functionValueFlow,
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
                    localDeclarations
                );
                break;
            case 184:
                if (initializesClass(classes, classFile.name(), instruction, classInitialization)) {
                    snapshotOperandStack(instructions, stack, locals, localDeclarations);
                    initializeClass(classes, classFile.name(), instruction, instructions, classInitialization);
                }
                BytecodeToIRInvokeSupport.lowerStaticCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations,
                    dispatches,
                    materializedLambdaMethods,
                    pendingExceptionHandlerStacks,
                    sourceLines
                );
                break;
            case 185:
                BytecodeToIRThreadSupport.lowerInterfaceCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations,
                    dispatches,
                    materializedLambdaMethods,
                    functionValueFlow
                );
                break;
            case 186:
                BytecodeToIRDynamicSupport.lowerDynamicCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations,
                    dispatches,
                    functionOrNullTargetIds
                );
                break;
            case 187:
                initializeClass(classes, classFile.name(), instruction, instructions, classInitialization);
                BytecodeToIRDynamicSupport.newObject(classes, classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 188:
                newPrimitiveArray(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 189:
                newObjectArray(classFile, method, instruction, instructions, stack, localDeclarations);
                break;
            case 190:
                arrayLength(classFile, method, stack);
                break;
            case 191:
                BytecodeToIRControlFlowSupport.lowerThrow(classFile, method, instruction, instructions, stack, pendingExceptionHandlerStacks, sourceLines);
                break;
            case 192:
                if (BytecodeToIRInvokeSupport.lowerSupportedCheckcast(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    stack,
                    localDeclarations,
                    pendingExceptionHandlerStacks,
                    sourceLines
                )) {
                    break;
                }
                if (instruction.className().isPresent()
                    && ("java/util/Locale".equals(instruction.className().orElseThrow())
                    || "java/nio/charset/Charset".equals(instruction.className().orElseThrow()))) {
                    throw unsupportedCheckcastTarget(
                        classFile,
                        method,
                        instruction,
                        instruction.className().orElseThrow()
                    );
                }
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
        if (instruction.offset() < lastMaterializingDuplicateOffset) {
            snapshotOperandStack(instructions, stack, locals, localDeclarations);
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

    static void unaryIntNeg(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_int_neg",
            List.of(popInt(classFile, method, stack))
        )));
    }

    static void unaryLongNeg(final ClassFile classFile, final MethodInfo method, final List<StackValue> stack) {
        stack.add(StackValue.longExpression(IrExpression.longCall(
            "javan_long_neg",
            List.of(popLong(classFile, method, stack))
        )));
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
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        storeObject(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            new HashMap<>(),
            localDeclarations,
            slot
        );
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
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final Map<Integer, IrLocal> localDeclarations,
        final int slot
    ) {
        if (stack.isEmpty()) {
            if (isSyntheticSwitchMapInitializer(classFile, method) && isEnumSwitchMapHandlerInstruction(instruction.opcode())) {
                return;
            }
            throw invalidStack(classFile, method, instruction, "object store requires a value on the bytecode stack");
        }
        final StackValue top = stack.getLast();
        if (top.dynamicLambda().isPresent()) {
            stack.removeLast();
            objectLocalLambdas.put(slot, top.dynamicLambda().orElseThrow());
            objectLocalKinds.put(slot, top.kind());
            objectLocalThrowableTypes.remove(slot);
            locals.remove(slot);
            return;
        }
        final StackValue value = popObjectValue(classFile, method, instruction, stack);
        objectLocalLambdas.remove(slot);
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
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final IrExpression length = popInt(classFile, method, stack);
        final String componentJvmName = instruction.className().orElseThrow();
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression local = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(
            localName,
            IrExpression.objectArrayAllocation(length, objectArrayBinaryName(componentJvmName))
        ));
        stack.add(StackValue.objectExpression(local));
    }

    private static String objectArrayBinaryName(final String componentJvmName) {
        final String componentBinaryName = BytecodeToIRDynamicSupport.binaryClassName(componentJvmName);
        if (componentJvmName.startsWith("[")) {
            return "[" + componentBinaryName;
        }
        return "[L" + componentBinaryName + ";";
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
        final StackValue value = popObjectValue(classFile, method, instruction, stack);
        if (value.kind() == StackKind.CAUGHT_THROWABLE) {
            throw unsupportedCaughtThrowableEscape(classFile, method, instruction);
        }
        return stackValueExpression(value);
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
        discardValue(instructions, pop(stack));
    }

    private static void discardTopTwoSlots(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "pop2 requires either one category-2 value or two category-1 values."
            );
        }
        final StackValue top = stack.getLast();
        if (isCategoryTwo(top.kind())) {
            pop(stack);
            discardValue(instructions, top);
            return;
        }
        if (stack.size() < 2) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "pop2 requires two category-1 values, but only one is available."
            );
        }
        final StackValue lower = stack.get(stack.size() - 2);
        if (isCategoryTwo(lower.kind())) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "pop2 cannot pair a category-1 top value with a category-2 value beneath it."
            );
        }
        pop(stack);
        pop(stack);
        discardValue(instructions, lower);
        if (top != lower) {
            discardValue(instructions, top);
        }
    }

    private static void discardValue(final List<IrInstruction> instructions, final StackValue value) {
        if (value.expression().isPresent()) {
            final IrExpression expression = value.expression().orElseThrow();
            if (expression.kind() == IrExpression.Kind.CALL) {
                instructions.add(IrInstruction.callStaticVoid(expression.value(), expression.arguments()));
            }
        }
    }

    private static void duplicateTopSlotUnderTwoSlots(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (stack.size() < 2) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup_x2 requires a category-1 top value above either one category-2 value or two category-1 values."
            );
        }
        final int topIndex = stack.size() - 1;
        final int lowerIndex = topIndex - 1;
        final StackValue top = stack.get(topIndex);
        final StackValue lower = stack.get(lowerIndex);
        if (isCategoryTwo(top.kind())) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup_x2 requires a category-1 top value, but found " + stackKindName(top.kind()) + "."
            );
        }
        if (isCategoryTwo(lower.kind())) {
            requireMaterializableDuplicateValue(classFile, method, instruction, lower);
            requireMaterializableDuplicateValue(classFile, method, instruction, top);
            final StackValue materializedLower =
                materializeDuplicateValueIfNeeded(lower, instructions, locals, localDeclarations);
            final StackValue materializedTop = top == lower
                ? materializedLower
                : materializeDuplicateValueIfNeeded(top, instructions, locals, localDeclarations);
            stack.set(lowerIndex, materializedTop);
            stack.set(topIndex, materializedLower);
            stack.add(materializedTop);
            return;
        }
        if (stack.size() < 3) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup_x2 requires two category-1 values beneath its category-1 top value, but only one is available."
            );
        }
        final int bottomIndex = lowerIndex - 1;
        final StackValue bottom = stack.get(bottomIndex);
        if (isCategoryTwo(bottom.kind())) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup_x2 cannot use a category-2 value beneath a category-1 intermediate value."
            );
        }
        requireMaterializableDuplicateValue(classFile, method, instruction, bottom);
        requireMaterializableDuplicateValue(classFile, method, instruction, lower);
        requireMaterializableDuplicateValue(classFile, method, instruction, top);
        final StackValue materializedBottom =
            materializeDuplicateValueIfNeeded(bottom, instructions, locals, localDeclarations);
        final StackValue materializedLower = lower == bottom
            ? materializedBottom
            : materializeDuplicateValueIfNeeded(lower, instructions, locals, localDeclarations);
        final StackValue materializedTop = top == bottom
            ? materializedBottom
            : top == lower
                ? materializedLower
                : materializeDuplicateValueIfNeeded(top, instructions, locals, localDeclarations);
        stack.set(bottomIndex, materializedTop);
        stack.set(lowerIndex, materializedBottom);
        stack.set(topIndex, materializedLower);
        stack.add(materializedTop);
    }

    private static void duplicateTopTwoSlots(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup2 requires either one category-2 value or two category-1 values."
            );
        }
        final int topIndex = stack.size() - 1;
        final StackValue top = stack.get(topIndex);
        requireMaterializableDuplicateValue(classFile, method, instruction, top);
        if (isCategoryTwo(top.kind())) {
            final StackValue materializedTop =
                materializeDuplicateValueIfNeeded(top, instructions, locals, localDeclarations);
            stack.set(topIndex, materializedTop);
            stack.add(materializedTop);
            return;
        }
        if (stack.size() < 2) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup2 requires two category-1 values, but only one is available."
            );
        }
        final int lowerIndex = topIndex - 1;
        final StackValue lower = stack.get(lowerIndex);
        if (isCategoryTwo(lower.kind())) {
            throw invalidStack(
                classFile,
                method,
                instruction,
                "dup2 cannot pair a category-1 top value with a category-2 value beneath it."
            );
        }
        requireMaterializableDuplicateValue(classFile, method, instruction, lower);
        final StackValue materializedLower =
            materializeDuplicateValueIfNeeded(lower, instructions, locals, localDeclarations);
        final StackValue materializedTop = top == lower
            ? materializedLower
            : materializeDuplicateValueIfNeeded(top, instructions, locals, localDeclarations);
        stack.set(lowerIndex, materializedLower);
        stack.set(topIndex, materializedTop);
        stack.add(materializedLower);
        stack.add(materializedTop);
    }

    private static void requireMaterializableDuplicateValue(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final StackValue value
    ) {
        if (isMaterializableDuplicateStackKind(value.kind()) && hasMaterializableDuplicateExpression(value)) {
            return;
        }
        throw invalidStack(
            classFile,
            method,
            instruction,
            instruction.mnemonic() + " cannot duplicate deferred compiler value " + stackKindName(value.kind()) + "."
        );
    }

    private static boolean hasMaterializableDuplicateExpression(final StackValue value) {
        return value.expression().isPresent()
            || value.dynamicLambda().isPresent()
            || value.kind() == StackKind.PRINT_STREAM
            || value.kind() == StackKind.ERROR_PRINT_STREAM;
    }

    private static StackValue materializeDuplicateValueIfNeeded(
        final StackValue value,
        final List<IrInstruction> instructions,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (value.dynamicLambda().isPresent()) {
            return value;
        }
        final IrExpression expression = stackValueExpression(value);
        if (isRepeatableDuplicateExpression(expression, locals)) {
            return value;
        }
        final IrType type = BytecodeToIRControlFlowSupport.stackKindType(value.kind());
        final String localName = "stackDup" + localDeclarations.size();
        final IrLocal local = new IrLocal(type, localName);
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), local);
        instructions.add(BytecodeToIRControlFlowSupport.assignLocal(value.kind(), localName, expression));
        final IrExpression materializedExpression = localExpression(type, local);
        if (value.kind() == StackKind.PRINT_STREAM || value.kind() == StackKind.ERROR_PRINT_STREAM) {
            return StackValue.objectExpression(materializedExpression);
        }
        return new StackValue(value.kind(), value.throwableType(), Optional.of(materializedExpression), value.dynamicLambda());
    }

    private static void snapshotOperandStack(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final List<StackValue> originalStack = new ArrayList<>(stack);
        for (int index = 0; index < stack.size(); index++) {
            final StackValue value = originalStack.get(index);
            final int aliasIndex = earlierIdentityIndex(originalStack, index, value);
            if (aliasIndex >= 0) {
                stack.set(index, stack.get(aliasIndex));
                continue;
            }
            if (!isMaterializableDuplicateStackKind(value.kind()) || !hasMaterializableDuplicateExpression(value)) {
                continue;
            }
            stack.set(
                index,
                materializeDuplicateValueIfNeeded(value, instructions, locals, localDeclarations)
            );
        }
    }

    private static int earlierIdentityIndex(
        final List<StackValue> values,
        final int endIndex,
        final StackValue target
    ) {
        for (int index = 0; index < endIndex; index++) {
            if (values.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    static int lastMaterializingDuplicateOffset(final List<Instruction> instructions) {
        for (int index = instructions.size() - 1; index >= 0; index--) {
            if (instructions.get(index).opcode() == 91 || instructions.get(index).opcode() == 92) {
                return instructions.get(index).offset();
            }
        }
        return -1;
    }

    private static boolean isRepeatableDuplicateExpression(
        final IrExpression expression,
        final Map<Integer, IrExpression> locals
    ) {
        return switch (expression.kind()) {
            case INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, OBJECT_NULL, STRING_LITERAL -> true;
            case LOCAL -> !locals.containsValue(expression);
            default -> false;
        };
    }

    private static boolean isMaterializableDuplicateStackKind(final StackKind kind) {
        return kind == StackKind.INT
            || kind == StackKind.LONG
            || kind == StackKind.FLOAT
            || kind == StackKind.DOUBLE
            || kind == StackKind.LAMBDA_FUNCTION
            || kind == StackKind.LAMBDA_PREDICATE
            || kind == StackKind.LAMBDA_SUPPLIER
            || BytecodeToIRControlFlowSupport.isObjectLike(kind);
    }

    private static boolean isCategoryTwo(final StackKind kind) {
        return kind == StackKind.LONG || kind == StackKind.DOUBLE;
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
        final int slot
    ) {
        return localObjectValue(
            classFile,
            method,
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            new HashMap<>(),
            slot
        );
    }

    static StackValue localObjectValue(
        final ClassFile classFile,
        final MethodInfo method,
        final Map<Integer, IrExpression> locals,
        final Map<Integer, StackKind> objectLocalKinds,
        final Map<Integer, String> objectLocalThrowableTypes,
        final Map<Integer, DynamicLambda> objectLocalLambdas,
        final int slot
    ) {
        final DynamicLambda lambda = objectLocalLambdas.get(slot);
        if (lambda != null) {
            final StackKind kind = objectLocalKinds.getOrDefault(slot, StackKind.OBJECT);
            if (kind == StackKind.LAMBDA_FUNCTION) {
                return StackValue.lambdaFunction(lambda);
            }
            if (kind == StackKind.LAMBDA_PREDICATE) {
                return StackValue.lambdaPredicate(lambda);
            }
            if (kind == StackKind.LAMBDA_SUPPLIER) {
                return StackValue.lambdaSupplier(lambda);
            }
            throw new IllegalStateException("Unsupported lambda local kind: " + kind);
        }
        final IrExpression expression = local(classFile, method, locals, slot, IrType.OBJECT);
        final StackKind kind = objectLocalKinds.getOrDefault(slot, StackKind.OBJECT);
        if (kind == StackKind.CAUGHT_THROWABLE) {
            return StackValue.caughtThrowable(expression);
        }
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
        if (kind == StackKind.CAUGHT_THROWABLE
            || kind == StackKind.SOCKET_INPUT_STREAM
            || kind == StackKind.RESOURCE_INPUT_STREAM
            || kind == StackKind.SOCKET_OUTPUT_STREAM
            || kind == StackKind.VIRTUAL_THREAD_BUILDER
            || kind == StackKind.VIRTUAL_THREAD_FACTORY
            || kind == StackKind.VIRTUAL_THREAD_EXECUTOR
            || kind == StackKind.THREAD_FUTURE
            || kind == StackKind.SCHEDULED_THREAD_POOL_EXECUTOR) {
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
        if (opcode == 200) {
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
        if (instruction.opcode() == 200) {
            return wideBranchTarget(instruction);
        }
        return instruction.offset() + signedShort(instruction.operands());
    }

    static int wideBranchTarget(final Instruction instruction) {
        return instruction.offset() + int32(instruction.operands(), 0);
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

    static DiagnosticException unsupportedCaughtThrowableEscape(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN040",
            "caught throwable escape is not supported",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "A managed caught throwable cannot escape its catch method through a return, argument, field, array, or unrelated object operation.",
            "Read its message, rethrow it, pass it as a supported platform constructor cause, or keep this path on the JVM."
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

    static DiagnosticException unsupportedEmbeddedNulStringConstant(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN046",
            "embedded NUL string constants require the length-aware string model",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "The current native runtime stores strings as NUL-terminated UTF-8. Accepting this constant would silently truncate Java content and change parsing, equality, length, indexing, and ABI behavior.",
            "Remove the embedded U+0000 value or keep this code on the JVM until Javan's length-aware String object model is implemented."
        ));
    }

    static DiagnosticException unsupportedLiteralConstant(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN040",
            "literal bytecode is missing decoded constant metadata",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "This instruction should already carry a decoded constant value before native lowering starts. The classfile is malformed or the decoder does not understand this literal shape yet.",
            "Use a valid classfile for this target JDK version, or keep this code on the JVM until Javan decodes this literal form."
        ));
    }

    static DiagnosticException unsupportedDynamicConstant(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN040",
            "constant dynamic literal is not implemented by native code generation",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "This literal comes from a CONSTANT_Dynamic constant-pool entry. Javan does not yet evaluate or substitute dynamic constants safely during native lowering.",
            "Keep this code on the JVM for now, or rewrite the reachable constant to a plain string/class/int/float/long/double literal."
        ));
    }

    static DiagnosticException unsupportedMethodTypeLiteral(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN040",
            "method type literals are not implemented by native code generation",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "This literal comes from a CONSTANT_MethodType constant-pool entry. Javan does not yet model java.lang.invoke.MethodType objects in the native runtime.",
            "Keep this code on the JVM for now, or remove the reachable MethodType literal from the native closed world."
        ));
    }

    static DiagnosticException unsupportedMethodHandleLiteral(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN040",
            "method handle literals are not implemented by native code generation",
            classFile.name(),
            method.name() + method.descriptor(),
            instructionSubject(instruction),
            "This literal comes from a CONSTANT_MethodHandle constant-pool entry. Javan does not yet model java.lang.invoke.MethodHandle objects in the native runtime.",
            "Keep this code on the JVM for now, or remove the reachable MethodHandle literal from the native closed world."
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
        return isTimeUnitConstant(fieldRef);
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

    static boolean isTimeUnitConstant(final FieldRef fieldRef) {
        if (!"java/util/concurrent/TimeUnit".equals(fieldRef.owner())) {
            return false;
        }
        return "Ljava/util/concurrent/TimeUnit;".equals(fieldRef.descriptor());
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
        final Optional<String> resolvedOwner = ClassInitializationGraph.staticFieldOwner(classes, fieldRef);
        if (resolvedOwner.isEmpty()) {
            return Optional.empty();
        }
        final ClassFile owner = classes.get(resolvedOwner.orElseThrow());
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
        if ("java/lang/Byte".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_BYTE);
        }
        if ("java/lang/Short".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_SHORT);
        }
        if ("java/lang/Character".equals(target)) {
            return Optional.of(TYPE_JAVA_LANG_CHARACTER);
        }
        return Optional.empty();
    }

    static int exactTypeId(final Map<String, ClassFile> classes, final String target) {
        final List<ClassFile> sorted = BytecodeToIRMetadataSupport.sortedClasses(classes);
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).name().equals(target)) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("Unknown class type id: " + target);
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
            "The native runtime only has deterministic instanceof support for application classes, supported boxed primitive wrappers, primitive arrays, Object[], and the built-in Collection/Map runtime objects.",
            "Keep instanceof targets to application classes/interfaces, Object, supported wrappers, primitive arrays, Object[], or the currently admitted Collection/Map runtime targets."
        ));
    }

    private static DiagnosticException unsupportedCheckcastTarget(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final String target
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN045",
            "checkcast target is not supported",
            classFile.name(),
            method.name() + method.descriptor(),
            instruction.mnemonic() + " " + target,
            "The native runtime cannot perform a deterministic checkcast to this built-in singleton type or transport the required ClassCastException.",
            "Keep built-in singleton values statically typed and pass the supported constants directly."
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
        return CMethodSymbols.symbol(entryPoint);
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
        THREAD_FUTURE,
        SCHEDULED_THREAD_POOL_EXECUTOR,
        LAMBDA_FUNCTION,
        LAMBDA_PREDICATE,
        LAMBDA_SUPPLIER,
        PRINT_STREAM,
        ERROR_PRINT_STREAM,
        SOCKET_INPUT_STREAM,
        RESOURCE_INPUT_STREAM,
        SOCKET_OUTPUT_STREAM,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        CAUGHT_THROWABLE,
        OBJECT
    }

    record BlockResult(List<IrInstruction> instructions, List<StackValue> stack) {
    }

    record DynamicLambda(
        String interfaceOwner,
        String interfaceMethodName,
        String implementationOwner,
        String implementationName,
        String implementationDescriptor,
        int implementationReferenceKind,
        String instantiatedMethodDescriptor,
        List<IrExpression> captures
    ) {
        MethodRef implementationMethodRef() {
            return new MethodRef(implementationOwner, implementationName, implementationDescriptor);
        }
    }

    record StackValue(
        StackKind kind,
        Optional<String> throwableType,
        Optional<IrExpression> expression,
        Optional<DynamicLambda> dynamicLambda
    ) {
        static StackValue virtualThreadBuilder() {
            return new StackValue(StackKind.VIRTUAL_THREAD_BUILDER, Optional.empty(), Optional.of(IrExpression.objectNull()), Optional.empty());
        }

        static StackValue virtualThreadBuilder(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_BUILDER, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue virtualThreadFactory(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_FACTORY, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue virtualThreadExecutor(final IrExpression expression) {
            return new StackValue(StackKind.VIRTUAL_THREAD_EXECUTOR, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue threadFuture(final IrExpression expression) {
            return new StackValue(StackKind.THREAD_FUTURE, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue scheduledThreadPoolExecutor(final IrExpression expression) {
            return new StackValue(StackKind.SCHEDULED_THREAD_POOL_EXECUTOR, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue lambdaFunction(final DynamicLambda dynamicLambda) {
            return new StackValue(StackKind.LAMBDA_FUNCTION, Optional.empty(), Optional.empty(), Optional.of(dynamicLambda));
        }

        static StackValue lambdaPredicate(final DynamicLambda dynamicLambda) {
            return new StackValue(StackKind.LAMBDA_PREDICATE, Optional.empty(), Optional.empty(), Optional.of(dynamicLambda));
        }

        static StackValue lambdaSupplier(final DynamicLambda dynamicLambda) {
            return new StackValue(StackKind.LAMBDA_SUPPLIER, Optional.empty(), Optional.empty(), Optional.of(dynamicLambda));
        }

        static StackValue printStream() {
            return new StackValue(StackKind.PRINT_STREAM, Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue errorPrintStream() {
            return new StackValue(StackKind.ERROR_PRINT_STREAM, Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackValue socketInputStream(final IrExpression expression) {
            return new StackValue(StackKind.SOCKET_INPUT_STREAM, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue resourceInputStream(final IrExpression expression) {
            return new StackValue(StackKind.RESOURCE_INPUT_STREAM, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue socketOutputStream(final IrExpression expression) {
            return new StackValue(StackKind.SOCKET_OUTPUT_STREAM, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue intExpression(final IrExpression expression) {
            return new StackValue(StackKind.INT, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue longExpression(final IrExpression expression) {
            return new StackValue(StackKind.LONG, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue floatExpression(final IrExpression expression) {
            return new StackValue(StackKind.FLOAT, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue doubleExpression(final IrExpression expression) {
            return new StackValue(StackKind.DOUBLE, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue objectExpression(final IrExpression expression) {
            return new StackValue(StackKind.OBJECT, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue caughtThrowable(final IrExpression expression) {
            return new StackValue(StackKind.CAUGHT_THROWABLE, Optional.empty(), Optional.of(expression), Optional.empty());
        }

        static StackValue platformThrowable(final String throwableType, final IrExpression message) {
            return new StackValue(StackKind.OBJECT, Optional.of(throwableType), Optional.of(message), Optional.empty());
        }
    }
}
