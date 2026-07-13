package javan.codegen;

import javan.analysis.ExplicitThrowSummarySupport;
import javan.analysis.VirtualThreadInvokePatterns;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactorySupport;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.compat.JavanNativeSubstitutions;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrType;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRMetadataSupport.*;

final class BytecodeToIRInvokeSupport {
    private static final MethodRef RUNNABLE_RUN = new MethodRef("java/lang/Runnable", "run", "()V");

    static void lowerInstanceOf(
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
        final List<Integer> wrapperSuperTypeIds = platformWrapperSuperTypeIds(target);
        final List<IrExpression> arguments = new ArrayList<>();
        arguments.add(value);
        if (wrapperTypeId.isPresent()) {
            arguments.add(IrExpression.intLiteral(1));
            arguments.add(IrExpression.intLiteral(wrapperTypeId.orElseThrow()));
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_object_type_in", arguments)));
            return;
        }
        if (!wrapperSuperTypeIds.isEmpty()) {
            arguments.add(IrExpression.intLiteral(wrapperSuperTypeIds.size()));
            for (final int typeId : wrapperSuperTypeIds) {
                arguments.add(IrExpression.intLiteral(typeId));
            }
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
    static void pushField(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        final FieldRef fieldRef = instruction.fieldRef().orElseThrow();
        if ("java/util/Locale".equals(fieldRef.owner())
            && "ROOT".equals(fieldRef.name())
            && "Ljava/util/Locale;".equals(fieldRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_locale_root", List.of())));
            return;
        }
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
        if (isSupportedJdkEnumConstant(fieldRef)) {
            stack.add(StackValue.objectExpression(enumConstantExpression(classes, fieldRef)));
            return;
        }
        if (isEnumConstant(classes, fieldRef)) {
            stack.add(StackValue.objectExpression(enumConstantExpression(classes, fieldRef)));
            return;
        }
        final Optional<IrType> type = staticFieldType(classes, fieldRef);
        if (type.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        switch (type.orElseThrow()) {
            case INT -> stack.add(StackValue.intExpression(IrExpression.intStaticField(fieldRef.owner(), fieldRef.name())));
            case LONG -> stack.add(StackValue.longExpression(IrExpression.longStaticField(fieldRef.owner(), fieldRef.name())));
            case FLOAT -> stack.add(StackValue.floatExpression(IrExpression.floatStaticField(fieldRef.owner(), fieldRef.name())));
            case DOUBLE -> stack.add(StackValue.doubleExpression(IrExpression.doubleStaticField(fieldRef.owner(), fieldRef.name())));
            case OBJECT -> stack.add(StackValue.objectExpression(IrExpression.objectStaticField(fieldRef.owner(), fieldRef.name())));
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
        switch (type) {
            case INT -> instructions.add(IrInstruction.assignStaticFieldInt(fieldRef.owner(), fieldRef.name(), popInt(classFile, method, stack)));
            case LONG -> instructions.add(IrInstruction.assignStaticFieldLong(fieldRef.owner(), fieldRef.name(), popLong(classFile, method, stack)));
            case FLOAT -> instructions.add(IrInstruction.assignStaticFieldFloat(fieldRef.owner(), fieldRef.name(), popFloat(classFile, method, stack)));
            case DOUBLE -> instructions.add(IrInstruction.assignStaticFieldDouble(fieldRef.owner(), fieldRef.name(), popDouble(classFile, method, stack)));
            case OBJECT -> instructions.add(IrInstruction.assignStaticFieldObject(fieldRef.owner(), fieldRef.name(), popObject(classFile, method, stack)));
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
        final SourceLineIndex sourceLines
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (isObjectGetClass(methodRef)) {
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_object_get_class", List.of(receiver))));
            return;
        }
        if (isRuntimeClassGetName(methodRef)) {
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_runtime_class_get_name", List.of(receiver))));
            return;
        }
        if (isRuntimeClassGetSimpleName(methodRef)) {
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_runtime_class_get_simple_name", List.of(receiver))));
            return;
        }
        if (isRuntimeClassIsArray(methodRef)) {
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_runtime_class_is_array", List.of(receiver))));
            return;
        }
        if (lowerPrintStreamCall(classFile, method, instruction, methodRef, instructions, stack)) {
            return;
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
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_length", List.of(receiver))));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner()) && "isEmpty".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_is_empty", List.of(popObject(classFile, method, stack)))));
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
            && "equalsIgnoreCase".equals(methodRef.name())
            && "(Ljava/lang/String;)Z".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            rejectUnsupportedStringSemantic(classFile, method, instruction, argument);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_string_equals_ignore_case", List.of(receiver, argument))));
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
        if ("java/lang/String".equals(methodRef.owner())
            && "resolveConstantDesc".equals(methodRef.name())
            && ("(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;".equals(methodRef.descriptor())
            || "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;".equals(methodRef.descriptor()))) {
            popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(popObject(classFile, method, stack)));
            return;
        }
        if ("java/lang/String".equals(methodRef.owner())
            && "toLowerCase".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            rejectUnsupportedStringSemantic(classFile, method, instruction, receiver);
            pushObjectCall(instructions, stack, localDeclarations, "javan_string_to_lower_case", List.of(receiver));
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
        if (lowerOptionalInstanceCall(classes, classFile, method, instruction, methodRef, instructions, stack, localDeclarations, dispatches)) {
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
        if (lowerThreadLocalInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerAtomicInstanceCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkCollectionInstanceCall(classes, classFile, method, instruction, methodRef, instructions, stack, localDeclarations, dispatches)) {
            return;
        }
        if (isPlatformThrowableGetMessage(methodRef)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_throwable_get_message",
                List.of(popObject(classFile, method, stack))
            )));
            return;
        }
        if (isPlatformThrowableAddSuppressed(methodRef)) {
            final List<IrExpression> throwableArguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            final IrExpression throwableReceiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_throwable_add_suppressed",
                List.of(throwableReceiver, throwableArguments.getFirst())
            ));
            return;
        }
        if (isPlatformThrowableGetSuppressed(methodRef)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_throwable_get_suppressed",
                List.of(popObject(classFile, method, stack))
            )));
            return;
        }
        if (isConcreteExactCallTarget(classes, methodRef.owner())) {
            lowerInstanceCall(classes, classFile, method, instruction, instructions, stack, localDeclarations, pendingExceptionHandlerStacks, sourceLines);
            return;
        }
        final List<EntryPoint> targets = virtualTargets(classes, methodRef);
        if (!targets.isEmpty()) {
            lowerDispatchCall(classFile, method, instruction, instructions, stack, dispatches, methodRef, targets);
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
            if (receiver.kind() != StackKind.SOCKET_INPUT_STREAM) {
                throw unsupportedSocketStreamReceiver(classFile, method, methodRef, "Socket.getInputStream()");
            }
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
            return false;
        }
        if (!"java/io/OutputStream".equals(methodRef.owner())) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        final StackValue receiver = popObjectValue(classFile, method, instruction, stack);
        if (receiver.kind() != StackKind.SOCKET_OUTPUT_STREAM) {
            throw unsupportedSocketStreamReceiver(classFile, method, methodRef, "Socket.getOutputStream()");
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
    static DiagnosticException unsupportedSocketStreamReceiver(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final String expectedSource
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN062",
            "supported stream call requires a socket-derived stream",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            "This release only supports " + methodRef.owner().replace('/', '.') + " calls when the receiver comes from " + expectedSource + ".",
            "Use streams returned by java.net.Socket directly, or keep this code on the JVM until generic stream support lands."
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
        if (!JavanNativeSubstitutions.isSubstitutedCall(methodRef)) {
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
        if ("java/lang/Boolean".equals(methodRef.owner()) && "booleanValue".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_boolean_boolean_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Character".equals(methodRef.owner()) && "charValue".equals(methodRef.name()) && "()C".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_character_char_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        if ("java/lang/Number".equals(methodRef.owner()) && "intValue".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_number_int_value", List.of(popObject(classFile, method, stack)))));
            return true;
        }
        return false;
    }
    static void lowerInstanceCall(
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
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (isZeroArgNoopPlatformConstructor(methodRef)) {
            popObject(classFile, method, stack);
            return;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = descriptor.parameterTypes().isEmpty()
            ? List.of()
            : popArguments(classFile, method, stack, descriptor);
        final IrExpression receiver = popObject(classFile, method, stack);
        if (isPlatformThrowableStringConstructor(methodRef)) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_throwable_set_message",
                List.of(receiver, arguments.getFirst())
            ));
            return;
        }
        if (isPlatformThrowableDefaultConstructor(methodRef)) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_throwable_set_message",
                List.of(receiver, IrExpression.objectNull())
            ));
            return;
        }
        if (isNoopPlatformConstructor(methodRef)) {
            return;
        }
        if (lowerThreadConstructor(methodRef, instructions, arguments, receiver)) {
            return;
        }
        if (lowerAtomicConstructor(methodRef, instructions, arguments, receiver)) {
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
        if (lowerDateTimeFormatterBuilderConstructor(methodRef)) {
            return;
        }
        if (lowerJdkCollectionConstructorCall(methodRef, instructions, arguments, receiver)) {
            return;
        }
        final Optional<EntryPoint> resolved = BytecodeToIR.lowerableResolvedInvokeVirtualTarget(classes, methodRef.owner(), methodRef);
        if (resolved.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final Optional<ExplicitThrowSummarySupport.DirectPlatformThrow> directThrow =
            ExplicitThrowSummarySupport.directPlatformThrow(classes, resolved.orElseThrow());
        if (directThrow.isPresent()) {
            routeDirectPlatformThrow(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                sourceLines,
                directThrow.orElseThrow(),
                localDeclarations
            );
            return;
        }
        final List<IrExpression> callArguments = new ArrayList<>(arguments);
        callArguments.addFirst(receiver);
        final String symbol = symbol(resolved.orElseThrow());
        appendCallResult(instructions, stack, descriptor.returnType(), symbol, callArguments);
    }


    private static boolean isZeroArgNoopPlatformConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name()) || !"()V".equals(methodRef.descriptor())) {
            return false;
        }
        return "java/lang/Object".equals(methodRef.owner()) || "java/lang/Record".equals(methodRef.owner());
    }
    static void lowerStaticCall(
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
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (lowerEnumValueOf(classes, classFile, method, methodRef, stack)) {
            return;
        }
        if (lowerEnumValues(classes, classFile, method, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkStaticIntrinsic(
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
        if (lowerJdkCollectionStaticCall(classFile, method, methodRef, instructions, stack, localDeclarations)) {
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
        final String symbol = symbol(new EntryPoint(methodRef.owner(), methodRef.name(), methodRef.descriptor()));
        if (VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(classes, methodRef)) {
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        if (VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(classes, methodRef)) {
            stack.add(StackValue.virtualThreadFactory(IrExpression.objectCall(symbol, arguments)));
            return;
        }
        appendCallResult(instructions, stack, descriptor.returnType(), symbol, arguments);
    }

    static boolean lowerEnumValueOf(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        final ClassFile enumClass = classes.get(methodRef.owner());
        if (enumClass == null || !enumClass.isEnum()) {
            return false;
        }
        if (!"valueOf".equals(methodRef.name())
            || !methodRef.descriptor().equals("(Ljava/lang/String;)L" + methodRef.owner() + ";")) {
            return false;
        }
        final IrExpression name = popObject(classFile, method, stack);
        stack.add(StackValue.objectExpression(IrExpression.objectCall(enumValueOfSymbol(methodRef.owner()), List.of(name))));
        return true;
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
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectArrayAllocation(IrExpression.intLiteral(constants.size()))));
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

    static String enumValueOfSymbol(final String owner) {
        return "javan_enum_value_of_" + sanitize(owner);
    }
    static boolean lowerJdkStaticIntrinsic(
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
        if ("java/util/stream/Collectors".equals(methodRef.owner())) {
            return lowerCollectorsIntrinsic(classFile, method, instruction, methodRef, instructions, stack, localDeclarations);
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
        if ("java/lang/Boolean".equals(methodRef.owner())) {
            return lowerBooleanIntrinsic(classFile, method, methodRef, stack);
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
        if ("java/time/Duration".equals(methodRef.owner())
            || "java/time/ZoneId".equals(methodRef.owner())
            || "java/time/Instant".equals(methodRef.owner())
            || "java/time/LocalDate".equals(methodRef.owner())
            || "java/time/LocalDateTime".equals(methodRef.owner())
            || "java/util/Date".equals(methodRef.owner())) {
            return lowerTimeStaticIntrinsic(classFile, method, instruction, methodRef, stack);
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
        return false;
    }

    static boolean lowerCollectorsIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/util/stream/Collectors".equals(methodRef.owner()) || !"joining".equals(methodRef.name())) {
            return false;
        }
        if ("()Ljava/util/stream/Collector;".equals(methodRef.descriptor())) {
            stack.add(StackValue.streamCollector(new CollectorPlan(
                IrExpression.stringLiteral(""),
                IrExpression.stringLiteral(""),
                IrExpression.stringLiteral("")
            )));
            return true;
        }
        if ("(Ljava/lang/CharSequence;)Ljava/util/stream/Collector;".equals(methodRef.descriptor())) {
            final IrExpression delimiter = printableCollectorStringArg(classFile, method, instruction, instructions, stack, localDeclarations);
            stack.add(StackValue.streamCollector(new CollectorPlan(
                delimiter,
                IrExpression.stringLiteral(""),
                IrExpression.stringLiteral("")
            )));
            return true;
        }
        if ("(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/util/stream/Collector;".equals(methodRef.descriptor())) {
            final IrExpression suffix = printableCollectorStringArg(classFile, method, instruction, instructions, stack, localDeclarations);
            final IrExpression prefix = printableCollectorStringArg(classFile, method, instruction, instructions, stack, localDeclarations);
            final IrExpression delimiter = printableCollectorStringArg(classFile, method, instruction, instructions, stack, localDeclarations);
            stack.add(StackValue.streamCollector(new CollectorPlan(delimiter, prefix, suffix)));
            return true;
        }
        return false;
    }

    private static IrExpression printableCollectorStringArg(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final IrExpression value = popObject(classFile, method, instruction, stack);
        final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(valueLocal, value));
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(valueLocal))));
        return IrExpression.objectCall("javan_printable_object_string", List.of(IrExpression.objectLocal(valueLocal)));
    }
    static boolean lowerMathIntrinsic(
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
        if ("abs".equals(methodRef.name()) && "(F)F".equals(methodRef.descriptor())) {
            stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_math_abs_float", List.of(popFloat(classFile, method, stack)))));
            return true;
        }
        if ("abs".equals(methodRef.name()) && "(D)D".equals(methodRef.descriptor())) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_math_abs_double", List.of(popDouble(classFile, method, stack)))));
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
    static boolean lowerLongIntrinsic(
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
        if ("valueOf".equals(methodRef.name()) && "(D)Ljava/lang/Double;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_double_value_of", List.of(popDouble(classFile, method, stack)))));
            return true;
        }
        return false;
    }
    static boolean lowerBooleanIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("toString".equals(methodRef.name()) && "(Z)Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_string_value_of_bool", List.of(popInt(classFile, method, stack)))));
            return true;
        }
        if ("valueOf".equals(methodRef.name()) && "(Z)Ljava/lang/Boolean;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_boolean_value_of", List.of(popInt(classFile, method, stack)))));
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
        if ("valueOf".equals(methodRef.name()) && "(C)Ljava/lang/Character;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_character_value_of", List.of(popInt(classFile, method, stack)))));
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

    static boolean lowerTimeStaticIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/time/Duration".equals(methodRef.owner())) {
            return lowerDurationIntrinsic(classFile, method, methodRef, stack);
        }
        if ("java/time/ZoneId".equals(methodRef.owner())
            && "systemDefault".equals(methodRef.name())
            && "()Ljava/time/ZoneId;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_zone_id_system_default", List.of())));
            return true;
        }
        if ("java/time/Instant".equals(methodRef.owner())) {
            if ("ofEpochMilli".equals(methodRef.name()) && "(J)Ljava/time/Instant;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_instant_of_epoch_millis", List.of(popLong(classFile, method, stack)))));
                return true;
            }
            if ("now".equals(methodRef.name()) && "()Ljava/time/Instant;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_instant_now", List.of())));
                return true;
            }
            return false;
        }
        if ("java/time/LocalDate".equals(methodRef.owner())
            && "ofEpochDay".equals(methodRef.name())
            && "(J)Ljava/time/LocalDate;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_local_date_of_epoch_day", List.of(popLong(classFile, method, stack)))));
            return true;
        }
        if ("java/time/LocalDateTime".equals(methodRef.owner())
            && "ofInstant".equals(methodRef.name())
            && "(Ljava/time/Instant;Ljava/time/ZoneId;)Ljava/time/LocalDateTime;".equals(methodRef.descriptor())) {
            final IrExpression zone = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression instant = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_local_date_time_of_instant", List.of(instant, zone))));
            return true;
        }
        if ("java/util/Date".equals(methodRef.owner())
            && "from".equals(methodRef.name())
            && "(Ljava/time/Instant;)Ljava/util/Date;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_date_from_instant",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        return false;
    }

    static boolean lowerDateTimeFormatterBuilderConstructor(final MethodRef methodRef) {
        return "java/time/format/DateTimeFormatterBuilder".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
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
    static boolean lowerArraysIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("stream".equals(methodRef.name()) && "([Ljava/lang/Object;)Ljava/util/stream/Stream;".equals(methodRef.descriptor())) {
            final IrExpression source = popObject(classFile, method, stack);
            stack.add(StackValue.objectStream(new StreamPlan(
                IrExpression.objectCall("javan_list_of_array", List.of(source)),
                List.of()
            )));
            return true;
        }
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
    static Optional<String> arraysCopyOfSymbol(final String descriptor) {
        if ("([II)[I".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_int");
        }
        if ("([ZI)[Z".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_boolean");
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
    static boolean lowerObjectsIntrinsic(
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
        if ("java/net/ServerSocket".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.assignObject(
                receiver.value(),
                IrExpression.objectCall("javan_server_socket_bind", arguments)
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
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        if (!"java/util/Optional".equals(methodRef.owner()) || JdkCallSupport.supportedCall(methodRef).isEmpty()) {
            return false;
        }
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        final IrExpression receiver = popObject(classFile, method, stack);
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
        if ("orElseGet".equals(name) && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)) {
            final IrExpression supplier = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(supplier)));
            final String optionalLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(optionalLocal, receiver));
            final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectNull()));
            final String presentLocal = declareLocal(localDeclarations, IrType.INT);
            instructions.add(IrInstruction.assignInt(
                presentLocal,
                IrExpression.intCall("javan_optional_is_present", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String presentLabel = "label_optional_or_else_get_present_" + instruction.offset() + "_" + localDeclarations.size();
            final String emptyLabel = "label_optional_or_else_get_empty_" + instruction.offset() + "_" + localDeclarations.size();
            final String doneLabel = "label_optional_or_else_get_done_" + instruction.offset() + "_" + localDeclarations.size();
            instructions.add(IrInstruction.branchIf(
                presentLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(presentLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(emptyLabel));
            instructions.add(IrInstruction.label(presentLabel));
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            instructions.add(IrInstruction.jump(doneLabel));
            instructions.add(IrInstruction.label(emptyLabel));
            appendInterfaceObjectCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"),
                List.of(supplier),
                instructions,
                resultLocal
            );
            instructions.add(IrInstruction.label(doneLabel));
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return true;
        }
        if ("get".equals(name) || "orElseThrow".equals(name)) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_optional_or_else_throw", List.of(receiver));
            return true;
        }
        if ("filter".equals(name) && "(Ljava/util/function/Predicate;)Ljava/util/Optional;".equals(descriptor)) {
            final IrExpression predicate = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(predicate)));
            final String optionalLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(optionalLocal, receiver));
            final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(optionalLocal)));
            final String presentLocal = declareLocal(localDeclarations, IrType.INT);
            instructions.add(IrInstruction.assignInt(
                presentLocal,
                IrExpression.intCall("javan_optional_is_present", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String evaluateLabel = "label_optional_filter_eval_" + instruction.offset() + "_" + localDeclarations.size();
            final String doneLabel = "label_optional_filter_done_" + instruction.offset() + "_" + localDeclarations.size();
            instructions.add(IrInstruction.branchIf(
                evaluateLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(presentLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(doneLabel));
            instructions.add(IrInstruction.label(evaluateLabel));
            final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(
                valueLocal,
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String predicateResultLocal = declareLocal(localDeclarations, IrType.INT);
            appendInterfaceIntCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"),
                List.of(predicate, IrExpression.objectLocal(valueLocal)),
                instructions,
                predicateResultLocal
            );
            instructions.add(IrInstruction.branchIf(
                doneLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(predicateResultLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall("javan_optional_empty", List.of())));
            instructions.add(IrInstruction.label(doneLabel));
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return true;
        }
        if ("ifPresent".equals(name) && "(Ljava/util/function/Consumer;)V".equals(descriptor)) {
            final IrExpression consumer = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            final String optionalLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(optionalLocal, receiver));
            final String presentLocal = declareLocal(localDeclarations, IrType.INT);
            instructions.add(IrInstruction.assignInt(
                presentLocal,
                IrExpression.intCall("javan_optional_is_present", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String callLabel = "label_optional_if_present_call_" + instruction.offset() + "_" + localDeclarations.size();
            final String doneLabel = "label_optional_if_present_done_" + instruction.offset() + "_" + localDeclarations.size();
            instructions.add(IrInstruction.branchIf(
                callLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(presentLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(doneLabel));
            instructions.add(IrInstruction.label(callLabel));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(consumer)));
            final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(
                valueLocal,
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            appendInterfaceVoidCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
                List.of(consumer, IrExpression.objectLocal(valueLocal)),
                instructions
            );
            instructions.add(IrInstruction.label(doneLabel));
            return true;
        }
        if ("map".equals(name) && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)) {
            final IrExpression function = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(function)));
            final String optionalLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(optionalLocal, receiver));
            final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall("javan_optional_empty", List.of())));
            final String presentLocal = declareLocal(localDeclarations, IrType.INT);
            instructions.add(IrInstruction.assignInt(
                presentLocal,
                IrExpression.intCall("javan_optional_is_present", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String applyLabel = "label_optional_map_apply_" + instruction.offset() + "_" + localDeclarations.size();
            final String doneLabel = "label_optional_map_done_" + instruction.offset() + "_" + localDeclarations.size();
            instructions.add(IrInstruction.branchIf(
                applyLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(presentLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(doneLabel));
            instructions.add(IrInstruction.label(applyLabel));
            final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(
                valueLocal,
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String mappedLocal = declareLocal(localDeclarations, IrType.OBJECT);
            appendInterfaceObjectCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                List.of(function, IrExpression.objectLocal(valueLocal)),
                instructions,
                mappedLocal
            );
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall("javan_optional_of_nullable", List.of(IrExpression.objectLocal(mappedLocal)))
            ));
            instructions.add(IrInstruction.label(doneLabel));
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return true;
        }
        if ("ifPresentOrElse".equals(name) && "(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V".equals(descriptor)) {
            final IrExpression emptyAction = arguments.get(1);
            final IrExpression consumer = arguments.getFirst();
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(receiver)));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(consumer)));
            instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(emptyAction)));
            final String optionalLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(optionalLocal, receiver));
            final String presentLocal = declareLocal(localDeclarations, IrType.INT);
            instructions.add(IrInstruction.assignInt(
                presentLocal,
                IrExpression.intCall("javan_optional_is_present", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            final String presentLabel = "label_optional_if_present_or_else_present_" + instruction.offset() + "_" + localDeclarations.size();
            final String emptyLabel = "label_optional_if_present_or_else_empty_" + instruction.offset() + "_" + localDeclarations.size();
            final String doneLabel = "label_optional_if_present_or_else_done_" + instruction.offset() + "_" + localDeclarations.size();
            instructions.add(IrInstruction.branchIf(
                presentLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(presentLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(emptyLabel));
            instructions.add(IrInstruction.label(presentLabel));
            final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(
                valueLocal,
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal(optionalLocal)))
            ));
            appendInterfaceVoidCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
                List.of(consumer, IrExpression.objectLocal(valueLocal)),
                instructions
            );
            instructions.add(IrInstruction.jump(doneLabel));
            instructions.add(IrInstruction.label(emptyLabel));
            appendInterfaceVoidCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/lang/Runnable", "run", "()V"),
                List.of(emptyAction),
                instructions
            );
            instructions.add(IrInstruction.label(doneLabel));
            return true;
        }
        return false;
    }

    private static void appendInterfaceIntCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final MethodRef methodRef,
        final List<IrExpression> arguments,
        final List<IrInstruction> instructions,
        final String localName
    ) {
        instructions.add(IrInstruction.assignInt(
            localName,
            IrExpression.intCall(interfaceCallSymbol(classes, classFile, method, instruction, dispatches, methodRef), arguments)
        ));
    }

    private static void appendInterfaceVoidCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final MethodRef methodRef,
        final List<IrExpression> arguments,
        final List<IrInstruction> instructions
    ) {
        instructions.add(IrInstruction.callStaticVoid(
            interfaceCallSymbol(classes, classFile, method, instruction, dispatches, methodRef),
            arguments
        ));
    }

    private static void appendInterfaceObjectCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final MethodRef methodRef,
        final List<IrExpression> arguments,
        final List<IrInstruction> instructions,
        final String localName
    ) {
        instructions.add(IrInstruction.assignObject(
            localName,
            IrExpression.objectCall(interfaceCallSymbol(classes, classFile, method, instruction, dispatches, methodRef), arguments)
        ));
    }

    private static String interfaceCallSymbol(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final MethodRef methodRef
    ) {
        final List<EntryPoint> targets = BytecodeToIR.interfaceTargets(classes, methodRef);
        if (targets.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        if (targets.size() == 1) {
            return symbol(targets.getFirst());
        }
        final String dispatchSymbol = dispatchSymbol(methodRef);
        dispatches.putIfAbsent(dispatchSymbol, dispatch(dispatchSymbol, MethodDescriptor.parse(methodRef.descriptor()), targets));
        return dispatchSymbol;
    }

    private static String declareLocal(final Map<Integer, IrLocal> localDeclarations, final IrType type) {
        final int index = localDeclarations.size();
        final String prefix;
        if (type == IrType.VOID) {
            throw new IllegalArgumentException("Cannot declare local for void type");
        } else if (type == IrType.INT) {
            prefix = "int";
        } else if (type == IrType.LONG) {
            prefix = "long";
        } else if (type == IrType.FLOAT) {
            prefix = "float";
        } else if (type == IrType.DOUBLE) {
            prefix = "double";
        } else {
            prefix = "object";
        }
        final String name = prefix + index;
        localDeclarations.put(Integer.MIN_VALUE + index, new IrLocal(type, name));
        return name;
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
    static boolean lowerJdkCollectionStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("java/util/Map".equals(owner)) {
            if ("of".equals(name) && "()Ljava/util/Map;".equals(descriptor)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_map_copy_of",
                    List.of(IrExpression.objectCall("javan_hashmap_new", List.of()))
                )));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                final String mapLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(mapLocal, IrExpression.objectCall("javan_hashmap_new", List.of())));
                final String ignoredPutLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(
                    ignoredPutLocal,
                    IrExpression.objectCall("javan_map_put", List.of(IrExpression.objectLocal(mapLocal), arguments.get(0), arguments.get(1)))
                ));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_copy_of", List.of(IrExpression.objectLocal(mapLocal)))));
                return true;
            }
            if (!"copyOf".equals(name) || !"(Ljava/util/Map;)Ljava/util/Map;".equals(descriptor)) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_copy_of", arguments)));
            return true;
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(owner)) {
            if (!"newKeySet".equals(name) || !"()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;".equals(descriptor)) {
                return false;
            }
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_hashset_new", List.of())));
            return true;
        }
        if ("java/util/Collections".equals(owner)) {
            if (!"emptyList".equals(name) || !"()Ljava/util/List;".equals(descriptor)) {
                return false;
            }
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of", List.of(IrExpression.intLiteral(0)))));
            return true;
        }
        if (!"java/util/List".equals(owner)) {
            return false;
        }
        if ("copyOf".equals(name)) {
            if (!"(Ljava/util/Collection;)Ljava/util/List;".equals(descriptor)) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_copy_of", arguments)));
            return true;
        }
        if (!"of".equals(name)) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        if ("([Ljava/lang/Object;)Ljava/util/List;".equals(descriptor)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of_array", arguments)));
            return true;
        }
        final List<IrExpression> callArguments = new ArrayList<>();
        callArguments.add(IrExpression.intLiteral(arguments.size()));
        callArguments.addAll(arguments);
        stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of", callArguments)));
        return true;
    }
    static boolean lowerJdkCollectionInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        if (!isJdkCollectionOwner(methodRef.owner())) {
            return false;
        }
        if (lowerSupportedReferenceStreamCall(classes, classFile, method, instruction, methodRef, instructions, stack, localDeclarations, dispatches)) {
            return true;
        }
        if (!JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        return lowerJdkCollectionInstanceCall(
            classes,
            classFile,
            method,
            instruction,
            methodRef,
            instructions,
            stack,
            localDeclarations,
            dispatches,
            arguments,
            receiver
        );
    }

    private static boolean lowerSupportedReferenceStreamCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches
    ) {
        final String signature = methodRef.name() + methodRef.descriptor();
        if (isJdkListOrCollection(methodRef.owner()) && "stream()Ljava/util/stream/Stream;".equals(signature)) {
            final IrExpression receiver = popObject(classFile, method, instruction, stack);
            stack.add(StackValue.objectStream(new StreamPlan(receiver, List.of())));
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner()) && "filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;".equals(signature)) {
            final IrExpression predicate = popObject(classFile, method, instruction, stack);
            final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
            stack.add(StackValue.objectStream(streamPlan.append(new StreamOperation(
                StreamOperationKind.FILTER,
                predicate,
                new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z")
            ))));
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner()) && "map(Ljava/util/function/Function;)Ljava/util/stream/Stream;".equals(signature)) {
            final IrExpression function = popObject(classFile, method, instruction, stack);
            final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
            stack.add(StackValue.objectStream(streamPlan.append(new StreamOperation(
                StreamOperationKind.MAP,
                function,
                new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")
            ))));
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner()) && "()Ljava/util/List;".equals(methodRef.descriptor()) && "toList".equals(methodRef.name())) {
            final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
            materializeReferenceStreamToList(classes, classFile, method, instruction, dispatches, streamPlan, instructions, localDeclarations, stack);
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner()) && "()Ljava/util/Optional;".equals(methodRef.descriptor()) && "findFirst".equals(methodRef.name())) {
            final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
            materializeReferenceStreamFindFirst(classes, classFile, method, instruction, dispatches, streamPlan, instructions, localDeclarations, stack);
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner())
            && "forEach".equals(methodRef.name())
            && "(Ljava/util/function/Consumer;)V".equals(methodRef.descriptor())) {
            final IrExpression consumer = popObject(classFile, method, instruction, stack);
            final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
            materializeReferenceStreamForEach(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                streamPlan,
                consumer,
                instructions,
                localDeclarations
            );
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner())
            && "collect".equals(methodRef.name())
            && "(Ljava/util/stream/Collector;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final CollectorPlan collectorPlan = popStreamCollector(classFile, method, instruction, stack);
            final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
            materializeReferenceStreamCollect(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                streamPlan,
                collectorPlan,
                instructions,
                localDeclarations,
                stack
            );
            return true;
        }
        if ("java/util/stream/Stream".equals(methodRef.owner()) && "(Ljava/util/function/Predicate;)Z".equals(methodRef.descriptor())) {
            if ("anyMatch".equals(methodRef.name()) || "noneMatch".equals(methodRef.name())) {
                final IrExpression predicate = popObject(classFile, method, instruction, stack);
                final StreamPlan streamPlan = popObjectStream(classFile, method, instruction, stack);
                materializeReferenceStreamMatch(
                    classes,
                    classFile,
                    method,
                    instruction,
                    dispatches,
                    streamPlan,
                    predicate,
                    "anyMatch".equals(methodRef.name()),
                    instructions,
                    localDeclarations,
                    stack
                );
                return true;
            }
        }
        return false;
    }
    static boolean lowerJdkCollectionConstructorCall(
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
        if ("java/util/concurrent/CopyOnWriteArrayList".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor())) {
            return true;
        }
        if (isJdkMapClass(methodRef.owner()) && "<init>".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            return true;
        }
        return false;
    }
    static boolean lowerThreadConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/lang/Thread".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("(Ljava/lang/Runnable;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_target", List.of(receiver, arguments.getFirst())));
            return true;
        }
        return false;
    }

    static boolean lowerThreadLocalConstructor(final MethodRef methodRef) {
        return "java/lang/ThreadLocal".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }

    static boolean lowerAtomicConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if ("java/util/concurrent/atomic/AtomicBoolean".equals(methodRef.owner()) && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(Z)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_atomic_boolean_init", List.of(receiver, arguments.getFirst())));
                return true;
            }
            return false;
        }
        if ("java/util/concurrent/atomic/AtomicInteger".equals(methodRef.owner()) && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(I)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_atomic_integer_init", List.of(receiver, arguments.getFirst())));
                return true;
            }
            return false;
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(methodRef.owner()) && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_atomic_reference_init", List.of(receiver, arguments.getFirst())));
                return true;
            }
            return false;
        }
        return false;
    }

    static boolean lowerThreadLocalInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/lang/ThreadLocal".equals(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        return lowerThreadLocalInstanceCall(methodRef, instructions, stack, localDeclarations, arguments, receiver);
    }

    static boolean lowerThreadLocalInstanceCall(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/lang/ThreadLocal".equals(methodRef.owner())) {
            return false;
        }
        if ("get".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_thread_local_get", List.of(receiver));
            return true;
        }
        if ("set".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_local_set", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("remove".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_local_remove", List.of(receiver)));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/util/concurrent/atomic/AtomicBoolean".equals(methodRef.owner())) {
            final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
            final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
            final IrExpression receiver = popObject(classFile, method, stack);
            if ("get".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_atomic_boolean_get", List.of(receiver));
                return true;
            }
            if ("getPlain".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_atomic_boolean_get_plain", List.of(receiver));
                return true;
            }
            if ("set".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_atomic_boolean_set", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("compareAndSet".equals(methodRef.name()) && "(ZZ)Z".equals(methodRef.descriptor())) {
                pushIntCall(
                    instructions,
                    stack,
                    localDeclarations,
                    "javan_atomic_boolean_compare_and_set",
                    List.of(receiver, arguments.get(0), arguments.get(1))
                );
                return true;
            }
            return false;
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(methodRef.owner())) {
            final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
            final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
            final IrExpression receiver = popObject(classFile, method, stack);
            if ("get".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_atomic_reference_get", List.of(receiver));
                return true;
            }
            if ("set".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_atomic_reference_set", List.of(receiver, arguments.getFirst())));
                return true;
            }
            return false;
        }
        if (!"java/util/concurrent/atomic/AtomicInteger".equals(methodRef.owner())) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        if (!arguments.isEmpty()) {
            return false;
        }
        final IrExpression receiver = popObject(classFile, method, stack);
        if ("get".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_atomic_integer_get", List.of(receiver));
            return true;
        }
        if ("getAndIncrement".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_atomic_integer_get_and_increment", List.of(receiver));
            return true;
        }
        if ("incrementAndGet".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_atomic_integer_increment_and_get", List.of(receiver));
            return true;
        }
        if ("decrementAndGet".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_atomic_integer_decrement_and_get", List.of(receiver));
            return true;
        }
        return false;
    }

    static boolean lowerJdkCollectionInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        final String signature = methodRef.name() + methodRef.descriptor();
        if (isJdkListClass(methodRef.owner())) {
            if ("add(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("add(ILjava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_at", List.of(receiver, arguments.get(0), arguments.get(1))));
                return true;
            }
            if ("addAll(Ljava/util/Collection;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add_all", List.of(receiver, arguments.getFirst()));
                return true;
            }
        }
        if (isJdkListOrCollection(methodRef.owner())) {
            if ("contains(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_contains", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("iterator()Ljava/util/Iterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
        }
        if (isJdkSetOwner(methodRef.owner())) {
            if ("add(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_set_add", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("remove(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_set_remove", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_is_empty", List.of(receiver))));
                return true;
            }
            if ("toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(arguments.getFirst())));
                pushObjectCall(instructions, stack, localDeclarations, "javan_set_to_array", List.of(receiver));
                return true;
            }
        }
        if (isJdkListClass(methodRef.owner())) {
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_is_empty", List.of(receiver))));
                return true;
            }
            if ("get(I)Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get", List.of(receiver, arguments.getFirst()))));
                return true;
            }
        }
        if ("java/util/List".equals(methodRef.owner())) {
            if ("addFirst(Ljava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_first", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("set(ILjava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_set", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("removeLast()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_last", List.of(receiver));
                return true;
            }
            if ("getFirst()Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get_first", List.of(receiver))));
                return true;
            }
            if ("getLast()Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get_last", List.of(receiver))));
                return true;
            }
            if ("remove(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_list_remove", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("forEach(Ljava/util/function/Consumer;)V".equals(signature)) {
                final IrExpression consumer = arguments.getFirst();
                instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(consumer)));
                final String consumerLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String nextLabel = "label_list_for_each_next_" + instruction.offset() + "_" + localDeclarations.size();
                final String bodyLabel = "label_list_for_each_body_" + instruction.offset() + "_" + localDeclarations.size();
                final String doneLabel = "label_list_for_each_done_" + instruction.offset() + "_" + localDeclarations.size();
                instructions.add(IrInstruction.assignObject(consumerLocal, consumer));
                instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                instructions.add(IrInstruction.label(nextLabel));
                instructions.add(IrInstruction.branchIf(
                    bodyLabel,
                    IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
                ));
                instructions.add(IrInstruction.jump(doneLabel));
                instructions.add(IrInstruction.label(bodyLabel));
                instructions.add(IrInstruction.assignObject(valueLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
                appendInterfaceVoidCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    dispatches,
                    new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
                    List.of(IrExpression.objectLocal(consumerLocal), IrExpression.objectLocal(valueLocal)),
                    instructions
                );
                instructions.add(IrInstruction.jump(nextLabel));
                instructions.add(IrInstruction.label(doneLabel));
                return true;
            }
        }
        if ("java/util/Iterator".equals(methodRef.owner())) {
            if ("hasNext()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_iterator_has_next", List.of(receiver))));
                return true;
            }
            if ("next()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_iterator_next", List.of(receiver));
                return true;
            }
        }
        if ("java/util/Map$Entry".equals(methodRef.owner())) {
            if ("getKey()Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_object_array_get",
                    List.of(receiver, IrExpression.intLiteral(0))
                )));
                return true;
            }
            if ("getValue()Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_object_array_get",
                    List.of(receiver, IrExpression.intLiteral(1))
                )));
                return true;
            }
        }
        if (isJdkMapOwner(methodRef.owner())) {
            if ("get(Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_get", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("remove(Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_remove", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("clear()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_map_clear", List.of(receiver)));
                return true;
            }
            if ("computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(signature)) {
                final IrExpression key = arguments.getFirst();
                final IrExpression function = arguments.get(1);
                instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(function)));
                final String receiverLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(receiverLocal, receiver));
                final String keyLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(keyLocal, key));
                final String functionLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(functionLocal, function));
                final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(
                    resultLocal,
                    IrExpression.objectCall("javan_map_get", List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(keyLocal)))
                ));
                final String doneLabel = "label_map_compute_if_absent_done_" + instruction.offset() + "_" + localDeclarations.size();
                final String containsLabel = "label_map_compute_if_absent_contains_" + instruction.offset() + "_" + localDeclarations.size();
                final String applyLabel = "label_map_compute_if_absent_apply_" + instruction.offset() + "_" + localDeclarations.size();
                instructions.add(IrInstruction.branchIf(
                    doneLabel,
                    IrExpression.objectComparison("!=", IrExpression.objectLocal(resultLocal), IrExpression.objectNull())
                ));
                final String containsLocal = declareLocal(localDeclarations, IrType.INT);
                instructions.add(IrInstruction.assignInt(
                    containsLocal,
                    IrExpression.intCall("javan_map_contains_key", List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(keyLocal)))
                ));
                instructions.add(IrInstruction.branchIf(
                    containsLabel,
                    IrExpression.intComparison("!=", IrExpression.intLocal(containsLocal), IrExpression.intLiteral(0))
                ));
                instructions.add(IrInstruction.jump(applyLabel));
                instructions.add(IrInstruction.label(containsLabel));
                instructions.add(IrInstruction.jump(doneLabel));
                instructions.add(IrInstruction.label(applyLabel));
                final String mappedLocal = declareLocal(localDeclarations, IrType.OBJECT);
                appendInterfaceObjectCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    dispatches,
                    new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                    List.of(IrExpression.objectLocal(functionLocal), IrExpression.objectLocal(keyLocal)),
                    instructions,
                    mappedLocal
                );
                instructions.add(IrInstruction.branchIf(
                    doneLabel,
                    IrExpression.objectComparison("==", IrExpression.objectLocal(mappedLocal), IrExpression.objectNull())
                ));
                final String ignoredPutLocal = declareLocal(localDeclarations, IrType.OBJECT);
                instructions.add(IrInstruction.assignObject(
                    ignoredPutLocal,
                    IrExpression.objectCall(
                        "javan_map_put",
                        List.of(IrExpression.objectLocal(receiverLocal), IrExpression.objectLocal(keyLocal), IrExpression.objectLocal(mappedLocal))
                    )
                ));
                instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(mappedLocal)));
                instructions.add(IrInstruction.label(doneLabel));
                stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
                return true;
            }
            if ("forEach(Ljava/util/function/BiConsumer;)V".equals(signature)) {
                final IrExpression consumer = arguments.getFirst();
                instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(consumer)));
                final String entrySetLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String consumerLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String entryLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String keyLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String valueLocal = declareLocal(localDeclarations, IrType.OBJECT);
                final String nextLabel = "label_map_for_each_next_" + instruction.offset() + "_" + localDeclarations.size();
                final String bodyLabel = "label_map_for_each_body_" + instruction.offset() + "_" + localDeclarations.size();
                final String doneLabel = "label_map_for_each_done_" + instruction.offset() + "_" + localDeclarations.size();
                instructions.add(IrInstruction.assignObject(entrySetLocal, IrExpression.objectCall("javan_map_entry_set", List.of(receiver))));
                instructions.add(IrInstruction.assignObject(consumerLocal, consumer));
                instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal(entrySetLocal)))));
                instructions.add(IrInstruction.label(nextLabel));
                instructions.add(IrInstruction.branchIf(
                    bodyLabel,
                    IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
                ));
                instructions.add(IrInstruction.jump(doneLabel));
                instructions.add(IrInstruction.label(bodyLabel));
                instructions.add(IrInstruction.assignObject(entryLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
                instructions.add(IrInstruction.assignObject(keyLocal, IrExpression.objectCall("javan_object_array_get", List.of(IrExpression.objectLocal(entryLocal), IrExpression.intLiteral(0)))));
                instructions.add(IrInstruction.assignObject(valueLocal, IrExpression.objectCall("javan_object_array_get", List.of(IrExpression.objectLocal(entryLocal), IrExpression.intLiteral(1)))));
                appendInterfaceVoidCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    dispatches,
                    new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"),
                    List.of(IrExpression.objectLocal(consumerLocal), IrExpression.objectLocal(keyLocal), IrExpression.objectLocal(valueLocal)),
                    instructions
                );
                instructions.add(IrInstruction.jump(nextLabel));
                instructions.add(IrInstruction.label(doneLabel));
                return true;
            }
            if ("getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_get_or_default", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_put", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_put_if_absent", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("containsKey(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_map_contains_key", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_map_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_map_is_empty", List.of(receiver))));
                return true;
            }
            if ("entrySet()Ljava/util/Set;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_entry_set", List.of(receiver))));
                return true;
            }
            if ("values()Ljava/util/Collection;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_values", List.of(receiver))));
                return true;
            }
        }
        throw collectionLoweringRegistryMismatch(classFile, method, methodRef);
    }

    private static CollectorPlan popStreamCollector(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A stream collector value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() != StackKind.STREAM_COLLECTOR || value.collectorPlan().isEmpty()) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("stream collector", value.kind()));
        }
        return value.collectorPlan().orElseThrow();
    }
    static DiagnosticException collectionLoweringRegistryMismatch(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN047",
            "declared supported collection call has no lowering",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            "The JDK support registry and bytecode lowering are out of sync.",
            "Add the missing lowering or remove the call from the support registry."
        ));
    }

    private static StreamPlan popObjectStream(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A reference stream value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() != StackKind.OBJECT_STREAM || value.streamPlan().isEmpty()) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("reference stream", value.kind()));
        }
        return value.streamPlan().orElseThrow();
    }

    private static void materializeReferenceStreamToList(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final StreamPlan streamPlan,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations,
        final List<StackValue> stack
    ) {
        final String sourceLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(sourceLocal, streamPlan.source()));
        final List<BoundStreamOperation> operations = bindStreamOperations(streamPlan.operations(), instructions, localDeclarations);
        final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String addIgnoredLocal = declareLocal(localDeclarations, IrType.INT);
        final String elementLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String nextLabel = "label_stream_to_list_next_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_stream_to_list_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String doneLabel = "label_stream_to_list_done_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal(sourceLocal)))));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall("javan_arraylist_new", List.of())));
        instructions.add(IrInstruction.label(nextLabel));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(elementLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
        final Optional<IrExpression> current = applyReferenceStreamOperations(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            operations,
            instructions,
            localDeclarations,
            IrExpression.objectLocal(elementLocal),
            nextLabel
        );
        if (current.isPresent()) {
            instructions.add(IrInstruction.assignInt(
                addIgnoredLocal,
                IrExpression.intCall("javan_arraylist_add", List.of(IrExpression.objectLocal(resultLocal), current.orElseThrow()))
            ));
        }
        instructions.add(IrInstruction.jump(nextLabel));
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void materializeReferenceStreamFindFirst(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final StreamPlan streamPlan,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations,
        final List<StackValue> stack
    ) {
        final String sourceLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(sourceLocal, streamPlan.source()));
        final List<BoundStreamOperation> operations = bindStreamOperations(streamPlan.operations(), instructions, localDeclarations);
        final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String candidateLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String nextLabel = "label_stream_find_first_next_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_stream_find_first_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String doneLabel = "label_stream_find_first_done_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal(sourceLocal)))));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall("javan_optional_empty", List.of())));
        instructions.add(IrInstruction.label(nextLabel));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(candidateLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
        final Optional<IrExpression> current = applyReferenceStreamOperations(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            operations,
            instructions,
            localDeclarations,
            IrExpression.objectLocal(candidateLocal),
            nextLabel
        );
        if (current.isPresent()) {
            instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectCall("javan_optional_of_nullable", List.of(current.orElseThrow()))));
            instructions.add(IrInstruction.jump(doneLabel));
        }
        instructions.add(IrInstruction.jump(nextLabel));
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void materializeReferenceStreamCollect(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final StreamPlan streamPlan,
        final CollectorPlan collectorPlan,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations,
        final List<StackValue> stack
    ) {
        final String sourceLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(sourceLocal, streamPlan.source()));
        final List<BoundStreamOperation> operations = bindStreamOperations(streamPlan.operations(), instructions, localDeclarations);
        final String delimiterLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String prefixLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String suffixLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(delimiterLocal, collectorPlan.delimiter()));
        instructions.add(IrInstruction.assignObject(prefixLocal, collectorPlan.prefix()));
        instructions.add(IrInstruction.assignObject(suffixLocal, collectorPlan.suffix()));
        final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String builderLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String firstLocal = declareLocal(localDeclarations, IrType.INT);
        final String candidateLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String stringLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String resultLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String nextLabel = "label_stream_collect_next_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_stream_collect_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String firstValueLabel = "label_stream_collect_first_" + instruction.offset() + "_" + localDeclarations.size();
        final String appendValueLabel = "label_stream_collect_append_" + instruction.offset() + "_" + localDeclarations.size();
        final String doneLabel = "label_stream_collect_done_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal(sourceLocal)))));
        instructions.add(IrInstruction.assignObject(builderLocal, IrExpression.objectCall("javan_stringbuilder_new", List.of())));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_stringbuilder_append_string",
            List.of(IrExpression.objectLocal(builderLocal), IrExpression.objectLocal(prefixLocal))
        ));
        instructions.add(IrInstruction.assignInt(firstLocal, IrExpression.intLiteral(1)));
        instructions.add(IrInstruction.label(nextLabel));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(candidateLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
        final Optional<IrExpression> current = applyReferenceStreamOperations(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            operations,
            instructions,
            localDeclarations,
            IrExpression.objectLocal(candidateLocal),
            nextLabel
        );
        if (current.isPresent()) {
            instructions.add(IrInstruction.branchIf(
                firstValueLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(firstLocal), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.callStaticVoid(
                "javan_stringbuilder_append_string",
                List.of(IrExpression.objectLocal(builderLocal), IrExpression.objectLocal(delimiterLocal))
            ));
            instructions.add(IrInstruction.jump(appendValueLabel));
            instructions.add(IrInstruction.label(firstValueLabel));
            instructions.add(IrInstruction.assignInt(firstLocal, IrExpression.intLiteral(0)));
            instructions.add(IrInstruction.label(appendValueLabel));
            instructions.add(IrInstruction.assignObject(
                stringLocal,
                IrExpression.objectCall("javan_printable_object_string", List.of(current.orElseThrow()))
            ));
            instructions.add(IrInstruction.callStaticVoid(
                "javan_stringbuilder_append_string",
                List.of(IrExpression.objectLocal(builderLocal), IrExpression.objectLocal(stringLocal))
            ));
        }
        instructions.add(IrInstruction.jump(nextLabel));
        instructions.add(IrInstruction.label(doneLabel));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_stringbuilder_append_string",
            List.of(IrExpression.objectLocal(builderLocal), IrExpression.objectLocal(suffixLocal))
        ));
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            IrExpression.objectCall("javan_stringbuilder_to_string", List.of(IrExpression.objectLocal(builderLocal)))
        ));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void materializeReferenceStreamForEach(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final StreamPlan streamPlan,
        final IrExpression consumer,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(consumer)));
        final String sourceLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(sourceLocal, streamPlan.source()));
        final List<BoundStreamOperation> operations = bindStreamOperations(streamPlan.operations(), instructions, localDeclarations);
        final String consumerLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String candidateLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String nextLabel = "label_stream_for_each_next_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_stream_for_each_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String doneLabel = "label_stream_for_each_done_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(consumerLocal, consumer));
        instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal(sourceLocal)))));
        instructions.add(IrInstruction.label(nextLabel));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(candidateLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
        final Optional<IrExpression> current = applyReferenceStreamOperations(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            operations,
            instructions,
            localDeclarations,
            IrExpression.objectLocal(candidateLocal),
            nextLabel
        );
        if (current.isPresent()) {
            appendInterfaceVoidCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
                List.of(IrExpression.objectLocal(consumerLocal), current.orElseThrow()),
                instructions
            );
        }
        instructions.add(IrInstruction.jump(nextLabel));
        instructions.add(IrInstruction.label(doneLabel));
    }

    private static void materializeReferenceStreamMatch(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final StreamPlan streamPlan,
        final IrExpression predicate,
        final boolean anyMatch,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations,
        final List<StackValue> stack
    ) {
        final String sourceLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(sourceLocal, streamPlan.source()));
        final List<BoundStreamOperation> operations = bindStreamOperations(streamPlan.operations(), instructions, localDeclarations);
        final String predicateLocal = declareLocal(localDeclarations, IrType.OBJECT);
        instructions.add(IrInstruction.assignObject(predicateLocal, predicate));
        final String iteratorLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String candidateLocal = declareLocal(localDeclarations, IrType.OBJECT);
        final String predicateResultLocal = declareLocal(localDeclarations, IrType.INT);
        final String resultLocal = declareLocal(localDeclarations, IrType.INT);
        final String nextLabel = "label_stream_match_next_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_stream_match_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String doneLabel = "label_stream_match_done_" + instruction.offset() + "_" + localDeclarations.size();
        final String matchedLabel = "label_stream_match_matched_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(iteratorLocal, IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal(sourceLocal)))));
        instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intLiteral(anyMatch ? 0 : 1)));
        instructions.add(IrInstruction.label(nextLabel));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal))), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(candidateLocal, IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))));
        final Optional<IrExpression> current = applyReferenceStreamOperations(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            operations,
            instructions,
            localDeclarations,
            IrExpression.objectLocal(candidateLocal),
            nextLabel
        );
        if (current.isPresent()) {
            appendInterfaceIntCall(
                classes,
                classFile,
                method,
                instruction,
                dispatches,
                new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"),
                List.of(IrExpression.objectLocal(predicateLocal), current.orElseThrow()),
                instructions,
                predicateResultLocal
            );
            instructions.add(IrInstruction.branchIf(
                matchedLabel,
                IrExpression.intComparison("!=", IrExpression.intLocal(predicateResultLocal), IrExpression.intLiteral(0))
            ));
        }
        instructions.add(IrInstruction.jump(nextLabel));
        instructions.add(IrInstruction.label(matchedLabel));
        instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intLiteral(anyMatch ? 1 : 0)));
        instructions.add(IrInstruction.jump(doneLabel));
        instructions.add(IrInstruction.label(doneLabel));
        stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
    }

    private static List<BoundStreamOperation> bindStreamOperations(
        final List<StreamOperation> operations,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final List<BoundStreamOperation> result = new ArrayList<>();
        for (final StreamOperation operation : operations) {
            final String functionLocal = declareLocal(localDeclarations, IrType.OBJECT);
            instructions.add(IrInstruction.assignObject(functionLocal, operation.function()));
            result.add(new BoundStreamOperation(operation.kind(), functionLocal, operation.interfaceMethod()));
        }
        return List.copyOf(result);
    }

    private static Optional<IrExpression> applyReferenceStreamOperations(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final List<BoundStreamOperation> operations,
        final List<IrInstruction> instructions,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression seed,
        final String skipLabel
    ) {
        IrExpression current = seed;
        for (final BoundStreamOperation operation : operations) {
            if (operation.kind() == StreamOperationKind.FILTER) {
                final String predicateResultLocal = declareLocal(localDeclarations, IrType.INT);
                appendInterfaceIntCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    dispatches,
                    operation.interfaceMethod(),
                    List.of(IrExpression.objectLocal(operation.functionLocal()), current),
                    instructions,
                    predicateResultLocal
                );
                instructions.add(IrInstruction.branchIf(
                    skipLabel,
                    IrExpression.intComparison("==", IrExpression.intLocal(predicateResultLocal), IrExpression.intLiteral(0))
                ));
                continue;
            }
            if (operation.kind() == StreamOperationKind.MAP) {
                final String mappedLocal = declareLocal(localDeclarations, IrType.OBJECT);
                appendInterfaceObjectCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    dispatches,
                    operation.interfaceMethod(),
                    List.of(IrExpression.objectLocal(operation.functionLocal()), current),
                    instructions,
                    mappedLocal
                );
                current = IrExpression.objectLocal(mappedLocal);
                continue;
            }
            throw new IllegalArgumentException("Unsupported reference stream operation: " + operation.kind());
        }
        return Optional.of(current);
    }

    private record BoundStreamOperation(
        StreamOperationKind kind,
        String functionLocal,
        MethodRef interfaceMethod
    ) {
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
    static void lowerInterruptAwareThreadWait(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final boolean precheckInterrupt,
        final IrExpression interruptedMessage,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        final String continueLabel = "label_thread_wait_continue_" + instruction.offset() + "_" + localDeclarations.size();
        final String interruptedLabel = "label_thread_wait_interrupted_" + instruction.offset() + "_" + localDeclarations.size();
        if (precheckInterrupt) {
            final int localIndex = localDeclarations.size();
            final String interruptedLocalName = "int" + localIndex;
            localDeclarations.put(Integer.MIN_VALUE + localIndex, new IrLocal(IrType.INT, interruptedLocalName));
            instructions.add(IrInstruction.assignInt(
                interruptedLocalName,
                IrExpression.intCall("javan_thread_interrupted", List.of())
            ));
            instructions.add(IrInstruction.branchIf(
                continueLabel,
                IrExpression.intComparison("==", IrExpression.intLocal(interruptedLocalName), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(interruptedLabel));
        }
        instructions.add(IrInstruction.label(continueLabel));
        final int interruptedResultLocalIndex = localDeclarations.size();
        final String interruptedResultLocalName = "int" + interruptedResultLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + interruptedResultLocalIndex, new IrLocal(IrType.INT, interruptedResultLocalName));
        instructions.add(IrInstruction.assignInt(
            interruptedResultLocalName,
            IrExpression.intCall(symbol, arguments)
        ));
        final String successLabel = "label_thread_wait_success_" + instruction.offset() + "_" + interruptedResultLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison("==", IrExpression.intLocal(interruptedResultLocalName), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.label(interruptedLabel));
        routePendingInterruptedException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            interruptedMessage
        );
        instructions.add(IrInstruction.label(successLabel));
    }
    static void routePendingInterruptedException(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression interruptedMessage
    ) {
        final StackValue thrownValue = StackValue.platformThrowable(
            "java/lang/InterruptedException",
            IrExpression.objectCall(
                "javan_throwable_new_with_message",
                List.of(IrExpression.stringLiteral("java/lang/InterruptedException"), interruptedMessage)
            )
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
            if (pendingExceptionHandlerStacks.containsKey(handlerOffset)) {
                throw unsupportedTypedExceptionHandler(classFile, method, instruction);
            }
            pendingExceptionHandlerStacks.put(handlerOffset, thrownValue);
            instructions.add(IrInstruction.jump(label(handlerOffset)));
            BytecodeToIRControlFlowSupport.clearStack(stack);
            return;
        }
        instructions.add(IrInstruction.panic(
            IrExpression.stringLiteral("java/lang/InterruptedException"),
            BytecodeToIRControlFlowSupport.sourceLocation(classFile, method, instruction, sourceLines)
        ));
        BytecodeToIRControlFlowSupport.clearStack(stack);
    }

    private static void routeDirectPlatformThrow(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final ExplicitThrowSummarySupport.DirectPlatformThrow summary,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression local = IrExpression.objectLocal(localName);
        final IrExpression allocation = summary.message().isPresent()
            ? IrExpression.objectCall(
                "javan_throwable_new_with_message",
                List.of(IrExpression.stringLiteral(summary.throwableType()), IrExpression.stringLiteral(summary.message().orElseThrow()))
            )
            : IrExpression.objectCall("javan_throwable_new", List.of(IrExpression.stringLiteral(summary.throwableType())));
        instructions.add(IrInstruction.assignObject(localName, allocation));
        BytecodeToIRControlFlowSupport.lowerThrownValue(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            StackValue.platformThrowable(summary.throwableType(), local)
        );
    }
    static boolean isJdkCollectionOwner(final String owner) {
        if (isJdkListOrCollection(owner)) {
            return true;
        }
        if ("java/util/stream/Stream".equals(owner)) {
            return true;
        }
        if ("java/util/Iterator".equals(owner)) {
            return true;
        }
        if ("java/util/Map$Entry".equals(owner)) {
            return true;
        }
        return isJdkMapOwner(owner);
    }
    static boolean isJdkListClass(final String owner) {
        if ("java/util/List".equals(owner)) {
            return true;
        }
        if ("java/util/ArrayList".equals(owner)) {
            return true;
        }
        return "java/util/concurrent/CopyOnWriteArrayList".equals(owner);
    }
    static boolean isJdkListOrCollection(final String owner) {
        if (isJdkListClass(owner)) {
            return true;
        }
        if (isJdkSetOwner(owner)) {
            return true;
        }
        return "java/util/Collection".equals(owner);
    }
    static boolean isJdkSetOwner(final String owner) {
        if ("java/util/Set".equals(owner)) {
            return true;
        }
        return "java/util/concurrent/ConcurrentHashMap$KeySetView".equals(owner);
    }
    static boolean isJdkMapOwner(final String owner) {
        if ("java/util/Map".equals(owner)) {
            return true;
        }
        return isJdkMapClass(owner);
    }
    static boolean isJdkMapClass(final String owner) {
        if ("java/util/HashMap".equals(owner)) {
            return true;
        }
        if ("java/util/LinkedHashMap".equals(owner)) {
            return true;
        }
        if ("java/util/TreeMap".equals(owner)) {
            return true;
        }
        return "java/util/concurrent/ConcurrentHashMap".equals(owner);
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
        if ("java/time/format/DateTimeFormatterBuilder".equals(methodRef.owner())) {
            return lowerDateTimeFormatterBuilderInstanceCall(classFile, method, instruction, methodRef, stack);
        }
        if ("java/time/Duration".equals(methodRef.owner())
            && "toMillis".equals(methodRef.name())
            && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                "javan_duration_to_millis",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("java/time/Instant".equals(methodRef.owner())) {
            if ("toEpochMilli".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
                stack.add(StackValue.longExpression(IrExpression.longCall(
                    "javan_instant_to_epoch_millis",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("atZone".equals(methodRef.name()) && "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(methodRef.descriptor())) {
                final IrExpression zone = popObjectForJdkCall(classFile, method, instruction, stack);
                final IrExpression instant = popObjectForJdkCall(classFile, method, instruction, stack);
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_instant_at_zone", List.of(instant, zone))));
                return true;
            }
        }
        if ("java/util/Date".equals(methodRef.owner())) {
            if ("toInstant".equals(methodRef.name()) && "()Ljava/time/Instant;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_date_to_instant",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("getTime".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
                stack.add(StackValue.longExpression(IrExpression.longCall(
                    "javan_date_get_time",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
        }
        if ("java/time/LocalDate".equals(methodRef.owner())) {
            if ("atStartOfDay".equals(methodRef.name()) && "()Ljava/time/LocalDateTime;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_local_date_at_start_of_day",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("atStartOfDay".equals(methodRef.name()) && "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(methodRef.descriptor())) {
                final IrExpression zone = popObjectForJdkCall(classFile, method, instruction, stack);
                final IrExpression localDate = popObjectForJdkCall(classFile, method, instruction, stack);
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_local_date_at_start_of_day_zone",
                    List.of(localDate, zone)
                )));
                return true;
            }
        }
        if ("java/time/LocalDateTime".equals(methodRef.owner())) {
            if ("atZone".equals(methodRef.name()) && "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;".equals(methodRef.descriptor())) {
                final IrExpression zone = popObjectForJdkCall(classFile, method, instruction, stack);
                final IrExpression localDateTime = popObjectForJdkCall(classFile, method, instruction, stack);
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_local_date_time_at_zone",
                    List.of(localDateTime, zone)
                )));
                return true;
            }
            if ("toLocalDate".equals(methodRef.name()) && "()Ljava/time/LocalDate;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_local_date_time_to_local_date",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("toLocalTime".equals(methodRef.name()) && "()Ljava/time/LocalTime;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_local_date_time_to_local_time",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
        }
        if ("java/time/ZonedDateTime".equals(methodRef.owner())) {
            if ("toInstant".equals(methodRef.name()) && "()Ljava/time/Instant;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_zoned_date_time_to_instant",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("toLocalDate".equals(methodRef.name()) && "()Ljava/time/LocalDate;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_zoned_date_time_to_local_date",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("toLocalTime".equals(methodRef.name()) && "()Ljava/time/LocalTime;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_zoned_date_time_to_local_time",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
            if ("toLocalDateTime".equals(methodRef.name()) && "()Ljava/time/LocalDateTime;".equals(methodRef.descriptor())) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall(
                    "javan_zoned_date_time_to_local_date_time",
                    List.of(popObjectForJdkCall(classFile, method, instruction, stack))
                )));
                return true;
            }
        }
        return false;
    }

    static boolean lowerDateTimeFormatterBuilderInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("parseCaseInsensitive".equals(methodRef.name())
            && "()Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_datetime_formatter_builder_parse_case_insensitive",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("appendPattern".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            final IrExpression pattern = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_datetime_formatter_builder_append_pattern",
                List.of(receiver, pattern)
            )));
            return true;
        }
        if ("optionalStart".equals(methodRef.name())
            && "()Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_datetime_formatter_builder_optional_start",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("appendFraction".equals(methodRef.name())
            && "(Ljava/time/temporal/TemporalField;IIZ)Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            final IrExpression decimalPoint = popInt(classFile, method, stack);
            final IrExpression maxWidth = popInt(classFile, method, stack);
            final IrExpression minWidth = popInt(classFile, method, stack);
            final IrExpression field = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_datetime_formatter_builder_append_fraction",
                List.of(receiver, field, minWidth, maxWidth, decimalPoint)
            )));
            return true;
        }
        if ("optionalEnd".equals(methodRef.name())
            && "()Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_datetime_formatter_builder_optional_end",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("toFormatter".equals(methodRef.name())
            && "(Ljava/util/Locale;)Ljava/time/format/DateTimeFormatter;".equals(methodRef.descriptor())) {
            final IrExpression locale = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_datetime_formatter_builder_to_formatter",
                List.of(receiver, locale)
            )));
            return true;
        }
        return false;
    }
    static boolean lowerJdkThreadInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final Map<String, IrDispatch> dispatches,
        final SourceLineIndex sourceLines
    ) {
        if (!"java/lang/Thread".equals(methodRef.owner())) {
            return false;
        }
        final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
        if ("interrupt".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_interrupt", List.of(receiver)));
            return true;
        }
        if ("isInterrupted".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_interrupted", List.of(receiver));
            return true;
        }
        if ("isAlive".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_alive", List.of(receiver));
            return true;
        }
        if ("isVirtual".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_virtual", List.of(receiver));
            return true;
        }
        if ("getName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_thread_get_name", List.of(receiver));
            return true;
        }
        if ("start".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_start", List.of(receiver)));
            return true;
        }
        if ("join".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                false,
                IrExpression.objectNull(),
                "javan_thread_join_interruptible",
                List.of(receiver)
            );
            return true;
        }
        return false;
    }
    static MethodRef runnableRunMethodRef() {
        return RUNNABLE_RUN;
    }
    static List<EntryPoint> runnableThreadTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        if (!containsReachableThreadStart(classes, reachableMethods)) {
            return List.of();
        }
        boolean sawRunnableThreadConstruction = false;
        boolean unknownRunnableTarget = false;
        final List<EntryPoint> result = new ArrayList<>();
        for (final EntryPoint reachable : reachableMethods) {
            final ClassFile owner = classes.get(reachable.className());
            if (owner == null) {
                continue;
            }
            final Optional<MethodInfo> method = owner.method(reachable.methodName(), reachable.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
                if (methodRef.isPresent()
                    && (isVirtualThreadStart(methodRef.orElseThrow())
                    || isVirtualThreadBuilderStart(methodRef.orElseThrow())
                    || isVirtualThreadBuilderUnstarted(methodRef.orElseThrow())
                    || isVirtualThreadFactoryNewThread(methodRef.orElseThrow())
                    || VirtualThreadInvokePatterns.isExecutorExecute(methodRef.orElseThrow()))) {
                    sawRunnableThreadConstruction = true;
                    final Optional<EntryPoint> resolved = inferVirtualThreadTarget(classes, instructions, index);
                    if (resolved.isPresent()) {
                        final EntryPoint entryPoint = resolved.orElseThrow();
                        if (!result.contains(entryPoint)) {
                            result.add(entryPoint);
                        }
                    } else {
                        unknownRunnableTarget = true;
                    }
                    continue;
                }
                if (methodRef.isEmpty() || (!isRunnableThreadConstructor(methodRef.orElseThrow())
                    && !isVirtualThreadBuilderUnstarted(methodRef.orElseThrow()))) {
                    continue;
                }
                sawRunnableThreadConstruction = true;
                final Optional<EntryPoint> resolved = inferRunnableThreadTarget(classes, instructions, index);
                if (resolved.isPresent()) {
                    final EntryPoint entryPoint = resolved.orElseThrow();
                    if (!result.contains(entryPoint)) {
                        result.add(entryPoint);
                    }
                } else {
                    unknownRunnableTarget = true;
                }
            }
        }
        if (!sawRunnableThreadConstruction) {
            return List.of();
        }
        if (!unknownRunnableTarget && !result.isEmpty()) {
            return List.copyOf(result);
        }
        return allRunnableThreadTargets(classes);
    }
    static boolean containsReachableThreadStart(final Map<String, ClassFile> classes, final List<EntryPoint> reachableMethods) {
        for (final EntryPoint reachable : reachableMethods) {
            final ClassFile owner = classes.get(reachable.className());
            if (owner == null) {
                continue;
            }
            final Optional<MethodInfo> method = owner.method(reachable.methodName(), reachable.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<MethodRef> methodRef = instruction.methodRef();
                if (methodRef.isPresent() && (isThreadStart(methodRef.orElseThrow())
                    || isVirtualThreadStart(methodRef.orElseThrow())
                    || isVirtualThreadBuilderStart(methodRef.orElseThrow())
                    || VirtualThreadInvokePatterns.isExecutorExecute(methodRef.orElseThrow()))) {
                    return true;
                }
            }
        }
        return false;
    }
    static boolean isThreadStart(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "start".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }
    static boolean isVirtualThreadStart(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "startVirtualThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }
    static boolean isVirtualThreadBuilderStart(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "start".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }
    static boolean isVirtualThreadBuilderUnstarted(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "unstarted".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }
    static boolean isVirtualThreadBuilderOwner(final String owner) {
        return "java/lang/Thread$Builder".equals(owner)
            || "java/lang/Thread$Builder$OfVirtual".equals(owner);
    }
    static boolean isRunnableThreadConstructor(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)V".equals(methodRef.descriptor());
    }
    static Optional<EntryPoint> inferVirtualThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        if (startIndex < 3) {
            return Optional.empty();
        }
        final Instruction runnableConstructor = instructions.get(startIndex - 1);
        final Optional<MethodRef> runnableConstructorRef = runnableConstructor.methodRef();
        if (runnableConstructorRef.isEmpty()) {
            return Optional.empty();
        }
        final MethodRef target = runnableConstructorRef.orElseThrow();
        if (!"<init>".equals(target.name())
            || !isAssignableTo(classes, target.owner(), RUNNABLE_RUN.owner())
            || isAssignableTo(classes, target.owner(), "java/lang/Thread")) {
            return Optional.empty();
        }
        if (instructions.get(startIndex - 2).opcode() != 89) {
            return Optional.empty();
        }
        final Instruction allocation = instructions.get(startIndex - 3);
        final Optional<String> className = allocation.className();
        if (allocation.opcode() != 187
            || className.isEmpty()
            || !className.orElseThrow().equals(target.owner())) {
            return Optional.empty();
        }
        final Optional<MethodRef> startRef = instructions.get(startIndex).methodRef();
        if (startRef.isPresent()) {
            if (isVirtualThreadBuilderStart(startRef.orElseThrow())
                && !supportedVirtualThreadBuilderReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if (isVirtualThreadBuilderUnstarted(startRef.orElseThrow())
                && !supportedVirtualThreadBuilderReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if (isVirtualThreadFactoryNewThread(startRef.orElseThrow())
                && !supportedVirtualThreadFactoryReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if (VirtualThreadInvokePatterns.isExecutorExecute(startRef.orElseThrow())
                && !supportedVirtualThreadExecutorReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
        }
        return lowerableResolvedVirtualTarget(classes, target.owner(), RUNNABLE_RUN);
    }

    private static boolean supportedVirtualThreadBuilderReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, startIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedVirtualThreadBuilderProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (isThreadOfVirtual(methodRef.orElseThrow())) {
                return true;
            }
            if (producer.opcode() == 184
                && VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(classes, methodRef.orElseThrow())) {
                return true;
            }
            if (isThreadBuilderOfVirtualName(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(
                    classes,
                    instructions,
                    transparentProducerIndex - VirtualThreadInvokePatterns.virtualThreadBuilderNameProducerOffset(methodRef.orElseThrow())
                );
            }
        }
        if (transparentProducerIndex < 1) {
            return false;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedVirtualThreadFactoryReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, startIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedVirtualThreadFactoryProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent() && isVirtualThreadBuilderFactory(methodRef.orElseThrow())) {
            return supportedVirtualThreadBuilderProducer(classes, instructions, transparentProducerIndex - 1);
        }
        if (methodRef.isPresent()
            && producer.opcode() == 184
            && VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(classes, methodRef.orElseThrow())) {
            return true;
        }
        if (transparentProducerIndex < 1) {
            return false;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedVirtualThreadExecutorReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, instructionIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadExecutorProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedVirtualThreadExecutorProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(methodRef.orElseThrow())) {
                return true;
            }
            if (VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(methodRef.orElseThrow())) {
                return supportedVirtualThreadFactoryProducer(classes, instructions, transparentProducerIndex - 1);
            }
        }
        if (transparentProducerIndex < 1) {
            return false;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadExecutorProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean isThreadOfVirtual(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "ofVirtual".equals(methodRef.name())
            && "()Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    private static boolean isThreadBuilderOfVirtualName(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(methodRef);
    }

    private static int localLoadSlot(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 25 -> instruction.operands()[0] & 0xFF;
            case 42 -> 0;
            case 43 -> 1;
            case 44 -> 2;
            case 45 -> 3;
            default -> -1;
        };
    }

    static Optional<EntryPoint> inferRunnableThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int threadConstructorIndex
    ) {
        final Optional<MethodRef> targetRef = instructions.get(threadConstructorIndex).methodRef();
        if (targetRef.isPresent() && isVirtualThreadBuilderUnstarted(targetRef.orElseThrow())) {
            return inferVirtualThreadTarget(classes, instructions, threadConstructorIndex);
        }
        if (threadConstructorIndex < 3) {
            return Optional.empty();
        }
        final Instruction runnableConstructor = instructions.get(threadConstructorIndex - 1);
        final Optional<MethodRef> runnableConstructorRef = runnableConstructor.methodRef();
        if (runnableConstructorRef.isEmpty()) {
            return Optional.empty();
        }
        final MethodRef target = runnableConstructorRef.orElseThrow();
        if (!"<init>".equals(target.name())
            || !isAssignableTo(classes, target.owner(), RUNNABLE_RUN.owner())
            || isAssignableTo(classes, target.owner(), "java/lang/Thread")) {
            return Optional.empty();
        }
        if (instructions.get(threadConstructorIndex - 2).opcode() != 89) {
            return Optional.empty();
        }
        final Instruction allocation = instructions.get(threadConstructorIndex - 3);
        final Optional<String> className = allocation.className();
        if (allocation.opcode() != 187
            || className.isEmpty()
            || !className.orElseThrow().equals(target.owner())) {
            return Optional.empty();
        }
        return lowerableResolvedVirtualTarget(classes, target.owner(), RUNNABLE_RUN);
    }
    static List<EntryPoint> allRunnableThreadTargets(final Map<String, ClassFile> classes) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()
                || !isAssignableTo(classes, candidate.name(), RUNNABLE_RUN.owner())
                || isAssignableTo(classes, candidate.name(), "java/lang/Thread")) {
                continue;
            }
            final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(classes, candidate.name(), RUNNABLE_RUN);
            if (resolved.isPresent() && !result.contains(resolved.orElseThrow())) {
                result.add(resolved.orElseThrow());
            }
        }
        return List.copyOf(result);
    }
    private static Optional<EntryPoint> lowerableResolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, target);
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
    static boolean lowerJdkNetworkInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/net/InetAddress".equals(methodRef.owner())) {
            if (!"getHostAddress".equals(methodRef.name())
                && !"getHostName".equals(methodRef.name())
                && !"getCanonicalHostName".equals(methodRef.name())) {
                return false;
            }
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            if ("getHostAddress".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_host_address", List.of(receiver));
                return true;
            }
            if ("getHostName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_host_name", List.of(receiver));
                return true;
            }
            if ("getCanonicalHostName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_canonical_host_name", List.of(receiver));
                return true;
            }
            return false;
        }
        if (!"java/net/InetSocketAddress".equals(methodRef.owner())) {
            return lowerJdkTcpSocketInstanceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations);
        }
        if (!"getPort".equals(methodRef.name())
            && !"getHostString".equals(methodRef.name())
            && !"getAddress".equals(methodRef.name())
            && !"toString".equals(methodRef.name())) {
            return false;
        }
        final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
        if ("getPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_inet_socket_address_get_port", List.of(receiver));
            return true;
        }
        if ("getHostString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_inet_socket_address_get_host_string", List.of(receiver));
            return true;
        }
        if ("getAddress".equals(methodRef.name()) && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_inet_socket_address_get_address", List.of(receiver));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_inet_socket_address_to_string", List.of(receiver));
            return true;
        }
        return false;
    }
    static boolean lowerJdkTcpSocketInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/net/Socket".equals(methodRef.owner())) {
            if (!"isConnected".equals(methodRef.name())
                && !"isClosed".equals(methodRef.name())
                && !"getPort".equals(methodRef.name())
                && !"getLocalPort".equals(methodRef.name())
                && !"getInetAddress".equals(methodRef.name())
                && !"getInputStream".equals(methodRef.name())
                && !"getOutputStream".equals(methodRef.name())
                && !"close".equals(methodRef.name())) {
                return false;
            }
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            if ("isConnected".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_connected", List.of(receiver));
                return true;
            }
            if ("isClosed".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_closed", List.of(receiver));
                return true;
            }
            if ("getPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_port", List.of(receiver));
                return true;
            }
            if ("getLocalPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_local_port", List.of(receiver));
                return true;
            }
            if ("getInetAddress".equals(methodRef.name()) && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_socket_get_inet_address", List.of(receiver));
                return true;
            }
            if ("getInputStream".equals(methodRef.name()) && "()Ljava/io/InputStream;".equals(methodRef.descriptor())) {
                final String localName = "object" + localDeclarations.size();
                localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
                instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_socket_input_stream", List.of(receiver))));
                stack.add(StackValue.socketInputStream(IrExpression.objectLocal(localName)));
                return true;
            }
            if ("getOutputStream".equals(methodRef.name()) && "()Ljava/io/OutputStream;".equals(methodRef.descriptor())) {
                final String localName = "object" + localDeclarations.size();
                localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
                instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_socket_output_stream", List.of(receiver))));
                stack.add(StackValue.socketOutputStream(IrExpression.objectLocal(localName)));
                return true;
            }
            if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_socket_close", List.of(receiver)));
                return true;
            }
            return false;
        }
        if (!"java/net/ServerSocket".equals(methodRef.owner())) {
            return false;
        }
        if (!"getLocalPort".equals(methodRef.name())
            && !"accept".equals(methodRef.name())
            && !"close".equals(methodRef.name())) {
            return false;
        }
        final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
        if ("getLocalPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_get_local_port", List.of(receiver));
            return true;
        }
        if ("accept".equals(methodRef.name()) && "()Ljava/net/Socket;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_server_socket_accept", List.of(receiver));
            return true;
        }
        if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_close", List.of(receiver)));
            return true;
        }
        return false;
    }
    static boolean lowerJdkFileInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/nio/file/attribute/FileTime".equals(methodRef.owner())
            && "toMillis".equals(methodRef.name())
            && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                "javan_file_time_to_millis",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if (isDirectoryStreamClose(methodRef)) {
            popObjectForJdkCall(classFile, method, instruction, stack);
            return true;
        }
        if (isDirectoryStreamIterator(methodRef)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_list_iterator",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        return false;
    }
    static boolean isDirectoryStreamClose(final MethodRef methodRef) {
        if (!"java/nio/file/DirectoryStream".equals(methodRef.owner())) {
            return false;
        }
        if (!"close".equals(methodRef.name())) {
            return false;
        }
        return "()V".equals(methodRef.descriptor());
    }
    static boolean isDirectoryStreamIterator(final MethodRef methodRef) {
        if (!isIterableOrDirectoryStream(methodRef.owner())) {
            return false;
        }
        if (!"iterator".equals(methodRef.name())) {
            return false;
        }
        return "()Ljava/util/Iterator;".equals(methodRef.descriptor());
    }
    static boolean isIterableOrDirectoryStream(final String owner) {
        if ("java/lang/Iterable".equals(owner)) {
            return true;
        }
        return "java/nio/file/DirectoryStream".equals(owner);
    }
    static boolean lowerJdkPathInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"java/nio/file/Path".equals(methodRef.owner())) {
            return false;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(popObjectForJdkCall(classFile, method, instruction, stack)));
            return true;
        }
        if ("toAbsolutePath".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_to_absolute",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("normalize".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_normalize",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getParent".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_get_parent",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getFileName".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_get_file_name",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("isAbsolute".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_path_is_absolute",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getNameCount".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_path_get_name_count",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getName".equals(methodRef.name()) && "(I)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_get_name",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack), index)
            )));
            return true;
        }
        if ("equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_equals", List.of(receiver, other))));
            return true;
        }
        if ("startsWith".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Z".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_starts_with", List.of(receiver, other))));
            return true;
        }
        if ("relativize".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_relativize", List.of(receiver, other))));
            return true;
        }
        if ("resolve".equals(methodRef.name())
            && ("(Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodRef.descriptor())
            || "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(methodRef.descriptor()))) {
            final IrExpression child = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_resolve", List.of(receiver, child))));
            return true;
        }
        return false;
    }
    static IrExpression popObjectForJdkCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() != StackKind.OBJECT) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
        }
        return value.expression().orElseThrow();
    }

    private static IrExpression popPrintableObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        return printableObjectExpression(classFile, method, instruction, pop(stack));
    }

    private static IrExpression printableObjectExpression(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final StackValue value
    ) {
        if (value.kind() == StackKind.OBJECT) {
            return value.expression().orElseThrow();
        }
        if (value.kind() == StackKind.VIRTUAL_THREAD_BUILDER) {
            return IrExpression.objectCall("javan_virtual_thread_builder_to_string", List.of(value.expression().orElse(IrExpression.objectNull())));
        }
        if (value.kind() == StackKind.VIRTUAL_THREAD_FACTORY) {
            return IrExpression.objectCall("javan_virtual_thread_factory_to_string", List.of(value.expression().orElse(IrExpression.objectNull())));
        }
        if (value.kind() == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            return IrExpression.objectCall("javan_virtual_thread_executor_to_string", List.of(value.expression().orElse(IrExpression.objectNull())));
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
    }

    private static IrExpression popVirtualThreadComparableObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.OBJECT
            || value.kind() == StackKind.VIRTUAL_THREAD_BUILDER
            || value.kind() == StackKind.VIRTUAL_THREAD_FACTORY
            || value.kind() == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            return value.expression().orElseThrow();
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
    }

    static void lowerInterfaceCall(
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
        if (lowerVirtualThreadObservationInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerVirtualThreadBuilderInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerVirtualThreadExecutorInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkPathInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkHttpInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkCollectionInstanceCall(classes, classFile, method, instruction, methodRef, instructions, stack, localDeclarations, dispatches)) {
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
        appendCallResult(instructions, stack, descriptor.returnType(), symbol, arguments);
    }

    private static boolean lowerVirtualThreadBuilderInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (isVirtualThreadBuilderName(methodRef)) {
            final IrExpression name = popObject(classFile, method, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(
                "javan_virtual_thread_builder_name",
                List.of(builder.expression().orElse(IrExpression.objectNull()), name)
            )));
            return true;
        }
        if (isVirtualThreadBuilderNameCounter(methodRef)) {
            final IrExpression start = popLong(classFile, method, stack);
            final IrExpression prefix = popObject(classFile, method, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(
                "javan_virtual_thread_builder_name_counter",
                List.of(builder.expression().orElse(IrExpression.objectNull()), prefix, start)
            )));
            return true;
        }
        if (isVirtualThreadBuilderFactory(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadFactory(IrExpression.objectCall(
                "javan_virtual_thread_builder_factory",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            )));
            return true;
        }
        if (isVirtualThreadFactoryNewThread(methodRef)) {
            final IrExpression runnable = popObject(classFile, method, instruction, stack);
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_factory_new_thread",
                List.of(factory.expression().orElse(IrExpression.objectNull()), runnable)
            );
            return true;
        }
        if (!isVirtualThreadBuilderStart(methodRef) && !isVirtualThreadBuilderUnstarted(methodRef)) {
            return false;
        }
        final IrExpression runnable = popObject(classFile, method, instruction, stack);
        final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
        pushObjectCall(
            instructions,
            stack,
            localDeclarations,
            isVirtualThreadBuilderStart(methodRef)
                ? "javan_virtual_thread_builder_start"
                : "javan_virtual_thread_builder_unstarted",
            List.of(builder.expression().orElse(IrExpression.objectNull()), runnable)
        );
        return true;
    }

    private static boolean lowerVirtualThreadExecutorInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (VirtualThreadInvokePatterns.isExecutorExecute(methodRef)) {
            final IrExpression runnable = popObject(classFile, method, instruction, stack);
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_virtual_thread_executor_execute",
                List.of(executor.expression().orElse(IrExpression.objectNull()), runnable)
            ));
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceShutdown(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_virtual_thread_executor_shutdown",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            ));
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceClose(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_virtual_thread_executor_close",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            ));
            return true;
        }
        return false;
    }

    private static boolean lowerVirtualThreadObservationInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (isVirtualThreadBuilderToString(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_builder_to_string",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadBuilderHashCode(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_hash_code",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadBuilderEquals(methodRef)) {
            final IrExpression other = popVirtualThreadComparableObject(classFile, method, instruction, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_equals",
                List.of(builder.expression().orElse(IrExpression.objectNull()), other)
            );
            return true;
        }
        if (isVirtualThreadBuilderGetClass(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_builder_get_class",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadFactoryToString(methodRef)) {
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_factory_to_string",
                List.of(factory.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadFactoryHashCode(methodRef)) {
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_hash_code",
                List.of(factory.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadFactoryEquals(methodRef)) {
            final IrExpression other = popVirtualThreadComparableObject(classFile, method, instruction, stack);
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_equals",
                List.of(factory.expression().orElse(IrExpression.objectNull()), other)
            );
            return true;
        }
        if (isVirtualThreadFactoryGetClass(methodRef)) {
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_factory_get_class",
                List.of(factory.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadExecutorToString(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_executor_to_string",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadExecutorHashCode(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_hash_code",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadExecutorEquals(methodRef)) {
            final IrExpression other = popVirtualThreadComparableObject(classFile, method, instruction, stack);
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_equals",
                List.of(executor.expression().orElse(IrExpression.objectNull()), other)
            );
            return true;
        }
        if (isVirtualThreadExecutorGetClass(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_executor_get_class",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        return false;
    }

    private static void startVirtualThread(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression runnable
    ) {
        newVirtualThread(instructions, stack, localDeclarations, runnable, IrExpression.objectNull(), true);
    }

    private static void startVirtualThread(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression runnable,
        final IrExpression name
    ) {
        newVirtualThread(instructions, stack, localDeclarations, runnable, name, true);
    }

    private static void newVirtualThread(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression runnable,
        final IrExpression name,
        final boolean start
    ) {
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression thread = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_thread_new_virtual", List.of())));
        instructions.add(IrInstruction.callStaticVoid("javan_thread_set_name", List.of(thread, name)));
        instructions.add(IrInstruction.callStaticVoid("javan_thread_set_target", List.of(thread, runnable)));
        if (start) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_start", List.of(thread)));
        }
        stack.add(StackValue.objectExpression(thread));
    }

    private static StackValue popVirtualThreadBuilder(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A virtual-thread builder receiver was expected on the bytecode stack.");
        }
        final StackValue builder = pop(stack);
        if (builder.kind() != StackKind.VIRTUAL_THREAD_BUILDER) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("virtual-thread builder receiver", builder.kind()));
        }
        return builder;
    }

    private static StackValue popVirtualThreadFactory(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A virtual-thread factory receiver was expected on the bytecode stack.");
        }
        final StackValue factory = pop(stack);
        if (factory.kind() != StackKind.VIRTUAL_THREAD_FACTORY) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("virtual-thread factory receiver", factory.kind()));
        }
        return factory;
    }

    private static StackValue popVirtualThreadExecutor(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A virtual-thread executor receiver was expected on the bytecode stack.");
        }
        final StackValue executor = pop(stack);
        if (executor.kind() != StackKind.VIRTUAL_THREAD_EXECUTOR) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("virtual-thread executor receiver", executor.kind()));
        }
        return executor;
    }

    private static boolean isVirtualThreadBuilderName(final MethodRef methodRef) {
        if (!isVirtualThreadBuilderOwner(methodRef.owner())) {
            return false;
        }
        if (!"name".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Thread$Builder".equals(methodRef.owner())) {
            return "(Ljava/lang/String;)Ljava/lang/Thread$Builder;".equals(methodRef.descriptor());
        }
        return "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderNameCounter(final MethodRef methodRef) {
        if (!isVirtualThreadBuilderOwner(methodRef.owner())) {
            return false;
        }
        if (!"name".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Thread$Builder".equals(methodRef.owner())) {
            return "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;".equals(methodRef.descriptor());
        }
        return "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderFactory(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "factory".equals(methodRef.name())
            && "()Ljava/util/concurrent/ThreadFactory;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryNewThread(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "newThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderToString(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderHashCode(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "hashCode".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderEquals(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderGetClass(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "getClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryToString(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryHashCode(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "hashCode".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryEquals(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryGetClass(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "getClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorToString(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorHashCode(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "hashCode".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorEquals(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorGetClass(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "getClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor());
    }
    static void lowerDispatchCall(
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
        appendCallResult(instructions, stack, descriptor.returnType(), dispatchSymbol, arguments);
    }
    static void appendCallResult(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final IrType returnType,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        switch (returnType) {
            case VOID -> instructions.add(IrInstruction.callStaticVoid(symbol, arguments));
            case INT -> stack.add(StackValue.intExpression(IrExpression.intCall(symbol, arguments)));
            case LONG -> stack.add(StackValue.longExpression(IrExpression.longCall(symbol, arguments)));
            case FLOAT -> stack.add(StackValue.floatExpression(IrExpression.floatCall(symbol, arguments)));
            case DOUBLE -> stack.add(StackValue.doubleExpression(IrExpression.doubleCall(symbol, arguments)));
            case OBJECT -> stack.add(StackValue.objectExpression(IrExpression.objectCall(symbol, arguments)));
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
    static void lowerDynamicCall(
        final LambdaMetafactorySupport.Registry lambdaRegistry,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final Optional<DynamicRef> maybeDynamicRef = instruction.dynamicRef();
        if (maybeDynamicRef.isEmpty()) {
            throw unsupported(classFile, method, instruction);
        }
        final DynamicRef dynamicRef = maybeDynamicRef.orElseThrow();
        final Optional<LambdaMetafactorySupport.LambdaClosurePlan> lambdaPlan = lambdaRegistry.planForSite(
            classFile.name(),
            method.name(),
            method.descriptor(),
            instruction.offset()
        );
        if (lambdaPlan.isPresent()) {
            lowerSupportedLambdaClosureCall(classFile, method, instruction, instructions, stack, localDeclarations, lambdaPlan.orElseThrow());
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

    private static void lowerSupportedLambdaClosureCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final LambdaMetafactorySupport.LambdaClosurePlan plan
    ) {
        final String objectName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, objectName));
        final IrExpression closure = IrExpression.objectLocal(objectName);
        instructions.add(IrInstruction.assignObject(objectName, IrExpression.objectAllocation(plan.syntheticOwner())));

        final List<IrExpression> captures = new ArrayList<>();
        for (int index = plan.captureDescriptors().size() - 1; index >= 0; index--) {
            captures.add(0, popValue(
                classFile,
                method,
                stack,
                requiredIrType(fieldType(plan.captureDescriptors().get(index)), classFile, method, instruction),
                instruction
            ));
        }
        for (int index = 0; index < plan.captureDescriptors().size(); index++) {
            final String descriptor = plan.captureDescriptors().get(index);
            final IrExpression value = captures.get(index);
            switch (requiredIrType(fieldType(descriptor), classFile, method, instruction)) {
                case INT -> instructions.add(IrInstruction.assignFieldInt(plan.syntheticOwner(), "capture" + index, closure, value));
                case LONG -> instructions.add(IrInstruction.assignFieldLong(plan.syntheticOwner(), "capture" + index, closure, value));
                case FLOAT -> instructions.add(IrInstruction.assignFieldFloat(plan.syntheticOwner(), "capture" + index, closure, value));
                case DOUBLE -> instructions.add(IrInstruction.assignFieldDouble(plan.syntheticOwner(), "capture" + index, closure, value));
                case OBJECT -> instructions.add(IrInstruction.assignFieldObject(plan.syntheticOwner(), "capture" + index, closure, value));
                case VOID -> throw unsupported(classFile, method, instruction);
            }
        }
        stack.add(StackValue.objectExpression(closure));
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
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (instruction.className().isPresent()) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_runtime_class_literal",
                List.of(IrExpression.stringLiteral(runtimeBinaryClassName(instruction.className().orElseThrow())))
            )));
            return;
        }
        if (instruction.stringValue().isPresent()) {
            final String value = instruction.stringValue().orElseThrow();
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
        throw unsupported(classFile, method, instruction);
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
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(
                localName,
                IrExpression.objectCall("javan_throwable_new", List.of(IrExpression.stringLiteral(owner)))
            ));
            stack.add(StackValue.platformThrowable(owner, local));
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
        if ("java/util/concurrent/CopyOnWriteArrayList".equals(owner)) {
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
        if ("java/lang/ThreadLocal".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_thread_local_new", List.of())));
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
        if ("java/util/concurrent/atomic/AtomicInteger".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_atomic_integer_new", List.of())));
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
        if ("java/time/format/DateTimeFormatterBuilder".equals(owner)) {
            final String localName = "object" + localDeclarations.size();
            localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
            final IrExpression local = IrExpression.objectLocal(localName);
            instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_datetime_formatter_builder_new", List.of())));
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

    private static boolean isObjectGetClass(final MethodRef methodRef) {
        return "java/lang/Object".equals(methodRef.owner())
            && "getClass".equals(methodRef.name())
            && "()Ljava/lang/Class;".equals(methodRef.descriptor());
    }

    private static boolean isRuntimeClassGetName(final MethodRef methodRef) {
        return "java/lang/Class".equals(methodRef.owner())
            && "getName".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isRuntimeClassGetSimpleName(final MethodRef methodRef) {
        return "java/lang/Class".equals(methodRef.owner())
            && "getSimpleName".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isRuntimeClassIsArray(final MethodRef methodRef) {
        return "java/lang/Class".equals(methodRef.owner())
            && "isArray".equals(methodRef.name())
            && "()Z".equals(methodRef.descriptor());
    }

    private static String runtimeBinaryClassName(final String className) {
        return Strings2.replaceChar(className, '/', '.');
    }

}
