package javan.codegen;

import javan.analysis.FunctionValueFlow;
import javan.analysis.InstantiatedTypeAnalysis;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.ir.IrDispatch;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrType;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRCollectionSupport.*;
import static javan.codegen.BytecodeToIRDynamicSupport.*;
import static javan.codegen.BytecodeToIRInvokeSupport.*;
import static javan.codegen.BytecodeToIRThreadSupport.*;
import static javan.codegen.BytecodeToIRMetadataSupport.*;

final class BytecodeToIRLangSupport {
    private BytecodeToIRLangSupport() {
    }

    static boolean lowerObjectsIntrinsic(
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
        final InstantiatedTypeAnalysis.Result instantiatedTypes
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
        if ("requireNonNullElse".equals(methodRef.name()) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression fallback = popObject(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_objects_require_non_null_else", List.of(value, fallback));
            return true;
        }
        if ("requireNonNullElseGet".equals(methodRef.name()) && "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            if (hasTopStackKind(stack, StackKind.LAMBDA_SUPPLIER)) {
                lowerObjectsRequireNonNullElseGetLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
                return true;
            }
            final IrExpression supplier = popObject(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            lowerObjectsRequireNonNullElseGetCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                stack,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                localDeclarations,
                value,
                supplier
            );
            return true;
        }
        if ("isNull".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression value = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.objectComparison("==", value, IrExpression.objectNull())));
            return true;
        }
        if ("nonNull".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression value = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.objectComparison("!=", value, IrExpression.objectNull())));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression value = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_printable_object_string", List.of(value));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression defaultValue = popObject(classFile, method, stack);
            final IrExpression value = popObject(classFile, method, stack);
            pushObjectCall(instructions, stack, localDeclarations, "javan_objects_to_string_default", List.of(value, defaultValue));
            return true;
        }
        return false;
    }

    private static void lowerObjectsRequireNonNullElseGetLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_SUPPLIER, "supplier lambda");
        final IrExpression value = popObject(classFile, method, instruction, stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(valueLocal, value));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String presentLabel = "label_objects_require_non_null_else_get_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_objects_require_non_null_else_get_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            presentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            invokeSupplierLambdaExpression(lambda)
        ));
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(resultLocal))));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(presentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(valueLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerObjectsRequireNonNullElseGetCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression value,
        final IrExpression supplier
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(valueLocal, value));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String presentLabel = "label_objects_require_non_null_else_get_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_objects_require_non_null_else_get_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            presentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(supplier)));
        lowerSupplierGetCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            supplier,
            resultLocal
        );
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(resultLocal))));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(presentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(valueLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    static void rejectUnsupportedStringSemantic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final IrExpression value
    ) {
        if (value.kind() != IrExpression.Kind.STRING_LITERAL) {
            return;
        }
        if (Strings2.isRuntimeAsciiStringConstant(value.value())) {
            return;
        }
        throw unsupportedStringConstant(classFile, method, instruction);
    }

    static void lowerStringGetBytesCharset(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression receiver,
        final IrExpression charset
    ) {
        final String receiverLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
        final String charsetLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(charsetLocal, charset));
        final List<StackValue> successStack = List.copyOf(stack);
        final String receiverPresentLabel = "label_string_get_bytes_receiver_present_"
            + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            receiverPresentLabel,
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
            IrExpression.stringLiteral("Cannot invoke java/lang/String.getBytes on null")
        );
        instructions.add(IrInstruction.label(receiverPresentLabel));
        stack.addAll(successStack);
        final String charsetPresentLabel = "label_string_get_bytes_charset_present_"
            + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            charsetPresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(charsetLocal), IrExpression.objectNull())
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
            IrExpression.stringLiteral("charset")
        );
        instructions.add(IrInstruction.label(charsetPresentLabel));
        stack.addAll(successStack);
        pushObjectCall(
            instructions,
            stack,
            localDeclarations,
            "javan_string_get_bytes_charset",
            List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(charsetLocal))
        );
    }

    static void lowerStringToLowerCaseLocale(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression receiver,
        final IrExpression locale
    ) {
        final String localeLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(localeLocal, locale));
        final String receiverLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
        final List<StackValue> successStack = List.copyOf(stack);
        final String receiverPresentLabel = "label_string_lower_receiver_present_"
            + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            receiverPresentLabel,
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
            IrExpression.stringLiteral("string")
        );
        instructions.add(IrInstruction.label(receiverPresentLabel));
        stack.addAll(successStack);
        final String localePresentLabel = "label_string_lower_locale_present_"
            + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            localePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(localeLocal), IrExpression.objectNull())
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
            IrExpression.objectNull()
        );
        instructions.add(IrInstruction.label(localePresentLabel));
        stack.addAll(successStack);
        pushObjectCall(
            instructions,
            stack,
            localDeclarations,
            "javan_string_to_lower_case_locale",
            List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(localeLocal))
        );
    }

    static boolean lowerStringBuilderConstructor(
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
        if ("(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_reserve", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_reserve_for_string", List.of(receiver, arguments.getFirst())));
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_append_string", List.of(receiver, arguments.getFirst())));
            return true;
        }
        return false;
    }

    static boolean lowerStringConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/lang/String".equals(methodRef.owner()) || !"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_string_from", List.of(IrExpression.stringLiteral("")))
            ));
            return true;
        }
        if ("(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression value = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(value)));
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_string_from", List.of(value))
            ));
            return true;
        }
        if ("(Ljava/lang/StringBuilder;)V".equals(methodRef.descriptor())) {
            final IrExpression value = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(value)));
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_stringbuilder_to_string", List.of(value))
            ));
            return true;
        }
        if ("([C)V".equals(methodRef.descriptor())) {
            final IrExpression array = arguments.getFirst();
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall(
                    "javan_string_from_chars",
                    List.of(array, IrExpression.intLiteral(0), IrExpression.intCall("javan_array_length", List.of(array)))
                )
            ));
            return true;
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

    static boolean lowerInetSocketAddressConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/net/InetSocketAddress".equals(methodRef.owner()) || !"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("(Ljava/lang/String;I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_inet_socket_address_from_host", arguments)
            ));
            return true;
        }
        if ("(Ljava/net/InetAddress;I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_inet_socket_address_from_address", arguments)
            ));
            return true;
        }
        return false;
    }

    static boolean lowerSocketConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if ("java/net/Socket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_socket_new", List.of())
            ));
            return true;
        }
        if ("java/net/Socket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/lang/String;I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_socket_connect_host", arguments)
            ));
            return true;
        }
        if ("java/net/Socket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/net/InetAddress;I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall(
                    "javan_socket_connect_host",
                    List.of(
                        IrExpression.objectCall("javan_inet_address_get_host_address", List.of(arguments.getFirst())),
                        arguments.get(1)
                    )
                )
            ));
            return true;
        }
        if ("java/net/Socket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/lang/String;ILjava/net/InetAddress;I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_socket_connect_host_config", arguments)
            ));
            return true;
        }
        if ("java/net/Socket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/net/InetAddress;ILjava/net/InetAddress;I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_socket_connect_address_config", arguments)
            ));
            return true;
        }
        if ("java/net/ServerSocket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_server_socket_new", List.of())
            ));
            return true;
        }
        if ("java/net/ServerSocket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_server_socket_bind", arguments)
            ));
            return true;
        }
        if ("java/net/ServerSocket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(II)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall(
                    "javan_server_socket_bind_config",
                    List.of(IrExpression.objectNull(), arguments.get(0), arguments.get(1))
                )
            ));
            return true;
        }
        if ("java/net/ServerSocket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(IILjava/net/InetAddress;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall(
                    "javan_server_socket_bind_config",
                    List.of(
                        IrExpression.objectCall("javan_inet_address_get_host_address", List.of(arguments.get(2))),
                        arguments.get(0),
                        arguments.get(1)
                    )
                )
            ));
            return true;
        }
        return false;
    }

    static boolean lowerOptionalStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"java/util/Optional".equals(methodRef.owner()) || JdkCallSupport.supportedCall(methodRef).isEmpty()) {
            return false;
        }
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("empty".equals(name)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_empty", List.of())));
            return true;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        if ("of".equals(name)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_of", arguments)));
            return true;
        }
        if ("ofNullable".equals(name)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_of_nullable", arguments)));
            return true;
        }
        return false;
    }

    static boolean lowerOptionalInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        if (!"java/util/Optional".equals(methodRef.owner())
            || (JdkCallSupport.supportedCall(methodRef).isEmpty()
                && !isContextLimitedOptionalOrElseThrowCall(instruction, methodRef))) {
            return false;
        }
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("orElseThrow".equals(name)
            && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)
            && hasTopStackKind(stack, StackKind.LAMBDA_SUPPLIER)) {
            lowerOptionalOrElseThrowSupplierLambdaCall(
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
            return true;
        }
        if (JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(methodRef)) {
            throw unsupported(classFile, method, instruction);
        }
        if ("filter".equals(name)
            && "(Ljava/util/function/Predicate;)Ljava/util/Optional;".equals(descriptor)
            && hasTopStackKind(stack, StackKind.LAMBDA_PREDICATE)) {
            lowerOptionalFilterLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        if ("map".equals(name)
            && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)
            && hasTopStackKind(stack, StackKind.LAMBDA_FUNCTION)) {
            lowerOptionalMapLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        if ("flatMap".equals(name)
            && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)
            && hasTopStackKind(stack, StackKind.LAMBDA_FUNCTION)) {
            lowerOptionalFlatMapLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        if ("or".equals(name)
            && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(descriptor)
            && hasTopStackKind(stack, StackKind.LAMBDA_SUPPLIER)) {
            lowerOptionalOrLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        if ("orElseGet".equals(name)
            && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)
            && hasTopStackKind(stack, StackKind.LAMBDA_SUPPLIER)) {
            lowerOptionalOrElseGetLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        final IrExpression receiver = popObject(classFile, method, stack);
        if ("filter".equals(name) && "(Ljava/util/function/Predicate;)Ljava/util/Optional;".equals(descriptor)) {
            lowerOptionalFilterCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                stack,
                localDeclarations,
                receiver,
                arguments.getFirst()
            );
            return true;
        }
        if ("map".equals(name) && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)) {
            lowerOptionalMapCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                functionValueFlow.isMaterializedFunction(
                    classFile.name(),
                    method.name(),
                    method.descriptor(),
                    instruction.offset()
                ),
                stack,
                localDeclarations,
                receiver,
                arguments.getFirst()
            );
            return true;
        }
        if ("flatMap".equals(name) && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)) {
            lowerOptionalFlatMapCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                functionValueFlow.isMaterializedFunction(
                    classFile.name(),
                    method.name(),
                    method.descriptor(),
                    instruction.offset()
                ),
                stack,
                localDeclarations,
                receiver,
                arguments.getFirst()
            );
            return true;
        }
        if ("ifPresent".equals(name) && "(Ljava/util/function/Consumer;)V".equals(descriptor)) {
            lowerOptionalIfPresentCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                localDeclarations,
                receiver,
                arguments.getFirst()
            );
            return true;
        }
        if ("or".equals(name) && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(descriptor)) {
            lowerOptionalOrCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                stack,
                localDeclarations,
                receiver,
                arguments.getFirst()
            );
            return true;
        }
        if ("orElseGet".equals(name) && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)) {
            lowerOptionalOrElseGetCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                instantiatedTypes,
                stack,
                localDeclarations,
                receiver,
                arguments.getFirst()
            );
            return true;
        }
        if ("isPresent".equals(name)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_optional_is_present", List.of(receiver))));
            return true;
        }
        if ("isEmpty".equals(name)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_optional_is_empty", List.of(receiver))));
            return true;
        }
        if ("orElse".equals(name)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_optional_or_else", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("get".equals(name) || "orElseThrow".equals(name)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_optional_or_else_throw", List.of(receiver));
            return true;
        }
        return false;
    }

    private static void lowerOptionalOrElseThrowSupplierLambdaCall(
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
        final DynamicLambda lambda = popDynamicLambda(
            classFile,
            method,
            instruction,
            stack,
            StackKind.LAMBDA_SUPPLIER,
            "supplier lambda"
        );
        final Optional<String> suppliedThrowableType = supplierPlatformThrowableType(lambda);
        if (suppliedThrowableType.isEmpty()) {
            throw new IllegalStateException(
                "Verifier admitted Optional.orElseThrow supplier without a platform Throwable result."
            );
        }
        final OptionalSupplierResultKind supplierResultKind =
            optionalSupplierResultKind(
                classes,
                lambda.implementationMethodRef(),
                suppliedThrowableType.orElseThrow(),
                Set.of()
            );
        if (supplierResultKind == OptionalSupplierResultKind.INVALID) {
            throw new IllegalStateException(
                "Verifier admitted Optional.orElseThrow supplier without an exact result shape."
            );
        }
        final IrExpression receiver = popObject(
            classFile,
            method,
            instruction,
            stack
        );
        final List<StackValue> preservedStack = List.copyOf(stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall(
                "javan_optional_or_else",
                List.of(receiver, IrExpression.objectNull())
            )
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel =
            "label_optional_or_else_throw_value_present_"
                + instruction.offset()
                + "_"
                + localDeclarations.size();
        final String supplierReturnedLabel =
            "label_optional_or_else_throw_supplier_returned_"
                + instruction.offset()
                + "_"
                + localDeclarations.size();
        final String endLabel =
            "label_optional_or_else_throw_end_"
                + instruction.offset()
                + "_"
                + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison(
                "!=",
                IrExpression.objectLocal(valueLocal),
                IrExpression.objectNull()
            )
        ));
        final String throwableLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            throwableLocal,
            invokeSupplierLambdaExpression(lambda)
        ));
        instructions.add(IrInstruction.branchIf(
            supplierReturnedLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intCall("javan_pending_has", List.of()),
                IrExpression.intLiteral(0)
            )
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(supplierReturnedLabel));
        if (supplierResultKind == OptionalSupplierResultKind.THROWABLE) {
            routePendingPlatformException(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                sourceLines,
                suppliedThrowableType.orElseThrow(),
                IrExpression.objectLocal(throwableLocal)
            );
        } else {
            routePendingPlatformException(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                sourceLines,
                "java/lang/NullPointerException",
                IrExpression.stringLiteral(
                    "Cannot throw exception because the return value of "
                        + "\"java.util.function.Supplier.get()\" is null"
                )
            );
        }
        instructions.add(IrInstruction.label(valuePresentLabel));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectLocal(valueLocal)
        ));
        instructions.add(IrInstruction.label(endLabel));
        stack.addAll(preservedStack);
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static Optional<String> supplierPlatformThrowableType(final DynamicLambda lambda) {
        final String descriptor = lambda.implementationDescriptor();
        final int close = descriptor.lastIndexOf(')');
        if (close < 0 || close + 3 > descriptor.length()
            || descriptor.charAt(close + 1) != 'L'
            || descriptor.charAt(descriptor.length() - 1) != ';') {
            return Optional.empty();
        }
        final String returnDescriptor = descriptor.substring(close + 1);
        if (!("()" + returnDescriptor).equals(lambda.instantiatedMethodDescriptor())) {
            return Optional.empty();
        }
        final String throwableType = returnDescriptor.substring(
            1,
            returnDescriptor.length() - 1
        );
        if (!JdkCallSupport.isPlatformThrowable(throwableType)) {
            return Optional.empty();
        }
        return Optional.of(throwableType);
    }

    private static OptionalSupplierResultKind optionalSupplierResultKind(
        final Map<String, ClassFile> classes,
        final MethodRef implementation,
        final String expectedType,
        final Set<MethodRef> visiting
    ) {
        if (visiting.contains(implementation)) {
            return OptionalSupplierResultKind.INVALID;
        }
        final ClassFile owner = classes.get(implementation.owner());
        if (owner == null || !owner.application()) {
            return OptionalSupplierResultKind.INVALID;
        }
        final Optional<MethodInfo> resolved = owner.method(
            implementation.name(),
            implementation.descriptor()
        );
        if (resolved.isEmpty()
            || !resolved.orElseThrow().isStatic()
            || resolved.orElseThrow().code().isEmpty()) {
            return OptionalSupplierResultKind.INVALID;
        }
        final Set<MethodRef> path = new HashSet<>(visiting);
        path.add(implementation);
        final List<Instruction> implementationInstructions =
            resolved.orElseThrow().code().orElseThrow().instructions();
        OptionalSupplierResultKind result = OptionalSupplierResultKind.THROWS_ONLY;
        for (int index = 0; index < implementationInstructions.size(); index++) {
            if (implementationInstructions.get(index).opcode() == 192) {
                return OptionalSupplierResultKind.INVALID;
            }
            if (implementationInstructions.get(index).opcode() != 176) {
                continue;
            }
            if (index == 0) {
                return OptionalSupplierResultKind.INVALID;
            }
            final OptionalSupplierResultKind candidate =
                optionalSupplierProducerKind(
                    classes,
                    implementationInstructions.get(index - 1),
                    expectedType,
                    path
                );
            if (candidate == OptionalSupplierResultKind.INVALID) {
                return candidate;
            }
            if (result == OptionalSupplierResultKind.THROWS_ONLY) {
                result = candidate;
            } else if (candidate != result) {
                return OptionalSupplierResultKind.INVALID;
            }
        }
        if (result == OptionalSupplierResultKind.THROWS_ONLY) {
            for (final Instruction candidate : implementationInstructions) {
                if (!isOptionalSupplierStaticHelperCall(candidate, expectedType)) {
                    continue;
                }
                if (optionalSupplierResultKind(
                    classes,
                    candidate.methodRef().orElseThrow(),
                    expectedType,
                    path
                ) == OptionalSupplierResultKind.INVALID) {
                    return OptionalSupplierResultKind.INVALID;
                }
            }
        }
        return result;
    }

    private static boolean isOptionalSupplierStaticHelperCall(
        final Instruction instruction,
        final String expectedType
    ) {
        return instruction.opcode() == 184
            && "invokestatic".equals(instruction.mnemonic())
            && instruction.methodRef().isPresent()
            && instruction.methodRef().orElseThrow().descriptor()
                .endsWith(")L" + expectedType + ";");
    }

    private static OptionalSupplierResultKind optionalSupplierProducerKind(
        final Map<String, ClassFile> classes,
        final Instruction producer,
        final String expectedType,
        final Set<MethodRef> visiting
    ) {
        if (producer.opcode() == 1 && "aconst_null".equals(producer.mnemonic())) {
            return OptionalSupplierResultKind.NULL;
        }
        if (producer.methodRef().isEmpty()) {
            return OptionalSupplierResultKind.INVALID;
        }
        final MethodRef target = producer.methodRef().orElseThrow();
        if (producer.opcode() == 183
            && "invokespecial".equals(producer.mnemonic())
            && "<init>".equals(target.name())
            && expectedType.equals(target.owner())) {
            return OptionalSupplierResultKind.THROWABLE;
        }
        if (producer.opcode() != 184
            || !"invokestatic".equals(producer.mnemonic())
            || !target.descriptor().endsWith(")L" + expectedType + ";")) {
            return OptionalSupplierResultKind.INVALID;
        }
        return optionalSupplierResultKind(classes, target, expectedType, visiting);
    }

    private static boolean isContextLimitedOptionalOrElseThrowCall(
        final Instruction instruction,
        final MethodRef methodRef
    ) {
        return instruction.opcode() == 182
            && "invokevirtual".equals(instruction.mnemonic())
            && JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(methodRef);
    }

    private enum OptionalSupplierResultKind {
        NULL,
        THROWABLE,
        THROWS_ONLY,
        INVALID
    }

    private static void lowerOptionalFilterLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_PREDICATE, "predicate lambda");
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_filter_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String keepLabel = "label_optional_filter_keep_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_filter_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        final String predicateLocal = newIntLocal(localDeclarations);
        instructions.add(IrInstruction.assignInt(
            predicateLocal,
            invokePredicateLambdaExpression(lambda, IrExpression.objectLocal(valueLocal))
        ));
        instructions.add(IrInstruction.branchIf(
            keepLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(predicateLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(keepLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, receiver));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalMapLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_FUNCTION, "function lambda");
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_map_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_map_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        final String mappedLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            mappedLocal,
            invokeFunctionLambdaExpression(lambda, IrExpression.objectLocal(valueLocal))
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_of_nullable", List.of(IrExpression.objectLocal(mappedLocal)))
        ));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalOrElseGetLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_SUPPLIER, "supplier lambda");
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_or_else_get_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_or_else_get_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            invokeSupplierLambdaExpression(lambda)
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(valueLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalOrLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_SUPPLIER, "supplier lambda");
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_or_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_or_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            invokeSupplierLambdaExpression(lambda)
        ));
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(resultLocal))));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, receiver));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalFlatMapLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_FUNCTION, "function lambda");
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_flat_map_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_flat_map_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        final String mappedLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            mappedLocal,
            invokeFunctionLambdaExpression(lambda, IrExpression.objectLocal(valueLocal))
        ));
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(mappedLocal))));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(mappedLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalFilterCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression receiver,
        final IrExpression predicate
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_filter_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String keepLabel = "label_optional_filter_keep_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_filter_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        final String predicateLocal = newIntLocal(localDeclarations);
        lowerPredicateTestCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            predicate,
            IrExpression.objectLocal(valueLocal),
            predicateLocal
        );
        instructions.add(IrInstruction.branchIf(
            keepLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(predicateLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(keepLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, receiver));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalOrCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression receiver,
        final IrExpression supplier
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_or_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_or_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        lowerSupplierGetCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            supplier,
            resultLocal
        );
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(resultLocal))));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, receiver));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalOrElseGetCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression receiver,
        final IrExpression supplier
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_or_else_get_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_or_else_get_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        lowerSupplierGetCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            supplier,
            resultLocal
        );
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(valueLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalFlatMapCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final boolean materializedFunction,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression receiver,
        final IrExpression function
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_flat_map_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_flat_map_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        final String mappedLocal = newObjectLocal(localDeclarations);
        lowerFunctionApplyCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            materializedFunction,
            function,
            IrExpression.objectLocal(valueLocal),
            mappedLocal
        );
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(mappedLocal))));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(mappedLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerOptionalIfPresentCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression receiver,
        final IrExpression consumer
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String valuePresentLabel = "label_optional_if_present_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_if_present_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        lowerConsumerAcceptCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            consumer,
            IrExpression.objectLocal(valueLocal)
        );
        instructions.add(IrInstruction.label(endLabel));
    }

    private static void lowerOptionalMapCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final boolean materializedFunction,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression receiver,
        final IrExpression function
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_optional_or_else", List.of(receiver, IrExpression.objectNull()))
        ));
        final String resultLocal = newObjectLocal(localDeclarations);
        final String valuePresentLabel = "label_optional_map_value_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_optional_map_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            valuePresentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(valueLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_empty", List.of())
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(valuePresentLabel));
        final String mappedLocal = newObjectLocal(localDeclarations);
        lowerFunctionApplyCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            materializedFunction,
            function,
            IrExpression.objectLocal(valueLocal),
            mappedLocal
        );
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_optional_of_nullable", List.of(IrExpression.objectLocal(mappedLocal)))
        ));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }


    static boolean lowerStringBuilderCall(
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
        final String name = methodRef.name();
        final String descriptorText = methodRef.descriptor();
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        if ("append".equals(name)) {
            if ("(Ljava/lang/String;)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_string", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("([C)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_chars", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("([CII)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_chars_range", List.of(receiver, arguments.getFirst(), arguments.get(1), arguments.get(2)));
                return true;
            }
            if ("(Ljava/lang/Object;)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_object", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("(Z)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_boolean", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("(C)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_char", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("(I)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_int", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("(J)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_long", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("(F)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_float", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("(D)Ljava/lang/StringBuilder;".equals(descriptorText)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_append_double", List.of(receiver, arguments.getFirst()));
                return true;
            }
            return false;
        }
        if ("toString".equals(name)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_to_string", List.of(receiver));
            return true;
        }
        if ("length".equals(name)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_length", List.of(receiver))));
            return true;
        }
        if ("capacity".equals(name) && "()I".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_capacity", List.of(receiver))));
            return true;
        }
        if ("isEmpty".equals(name)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_is_empty", List.of(receiver))));
            return true;
        }
        if ("charAt".equals(name) && "(I)C".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_char_at", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("substring".equals(name) && "(I)Ljava/lang/String;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_substring", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("substring".equals(name) && "(II)Ljava/lang/String;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_substring_range", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("subSequence".equals(name) && "(II)Ljava/lang/CharSequence;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_substring_range", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("indexOf".equals(name) && "(Ljava/lang/String;)I".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_index_of_string", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("indexOf".equals(name) && "(Ljava/lang/String;I)I".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_index_of_string_from", List.of(receiver, arguments.getFirst(), arguments.get(1)))));
            return true;
        }
        if ("lastIndexOf".equals(name) && "(Ljava/lang/String;)I".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_last_index_of_string", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("lastIndexOf".equals(name) && "(Ljava/lang/String;I)I".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_last_index_of_string_from", List.of(receiver, arguments.getFirst(), arguments.get(1)))));
            return true;
        }
        if ("compareTo".equals(name) && "(Ljava/lang/StringBuilder;)I".equals(descriptorText)) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_stringbuilder_compare_to", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("delete".equals(name) && "(II)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_delete", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("deleteCharAt".equals(name) && "(I)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_delete_char_at", List.of(receiver, arguments.getFirst()));
            return true;
        }
        if ("insert".equals(name) && "(ILjava/lang/String;)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_string", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(ILjava/lang/Object;)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_stringbuilder_insert_string",
                List.of(
                    receiver,
                    arguments.getFirst(),
                    IrExpression.objectCall("javan_printable_object_string", List.of(arguments.get(1)))
                )
            );
            return true;
        }
        if ("insert".equals(name) && "(IZ)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_boolean", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(IC)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_char", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(II)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_int", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(IJ)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_long", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(IF)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_float", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(ID)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_double", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(I[C)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_chars", List.of(receiver, arguments.getFirst(), arguments.get(1)));
            return true;
        }
        if ("insert".equals(name) && "(I[CII)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_insert_chars_range", List.of(receiver, arguments.getFirst(), arguments.get(1), arguments.get(2), arguments.get(3)));
            return true;
        }
        if ("replace".equals(name) && "(IILjava/lang/String;)Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_replace_string", List.of(receiver, arguments.getFirst(), arguments.get(1), arguments.get(2)));
            return true;
        }
        if ("reverse".equals(name) && "()Ljava/lang/StringBuilder;".equals(descriptorText)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_stringbuilder_reverse", List.of(receiver));
            return true;
        }
        if ("ensureCapacity".equals(name) && "(I)V".equals(descriptorText)) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_ensure_capacity_public", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("trimToSize".equals(name) && "()V".equals(descriptorText)) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_trim_to_size", List.of(receiver)));
            return true;
        }
        if ("setCharAt".equals(name) && "(IC)V".equals(descriptorText)) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_set_char_at", List.of(receiver, arguments.getFirst(), arguments.get(1))));
            return true;
        }
        if ("setLength".equals(name)) {
            instructions.add(IrInstruction.callStaticVoid("javan_stringbuilder_set_length", List.of(receiver, arguments.getFirst())));
            return true;
        }
        return false;
    }
}
