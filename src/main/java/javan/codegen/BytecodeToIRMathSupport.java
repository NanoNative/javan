package javan.codegen;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrType;

import java.util.List;
import java.util.Map;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRInvokeSupport.*;

final class BytecodeToIRMathSupport {
    private BytecodeToIRMathSupport() {
    }

    static boolean lowerMathIntrinsic(
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
        if ("atan2".equals(methodRef.name()) && "(DD)D".equals(methodRef.descriptor())) {
            final IrExpression x = popDouble(classFile, method, stack);
            final IrExpression y = popDouble(classFile, method, stack);
            final String yLocal = newDoubleLocal(localDeclarations);
            instructions.add(IrInstruction.assignDouble(yLocal, y));
            final String xLocal = newDoubleLocal(localDeclarations);
            instructions.add(IrInstruction.assignDouble(xLocal, x));
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(
                "atan2",
                List.of(IrExpression.doubleLocal(yLocal), IrExpression.doubleLocal(xLocal))
            )));
            return true;
        }
        if (lowerPureMathIntrinsic(classFile, method, methodRef, stack)) {
            return true;
        }
        if ("addExact".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            lowerMathAddExactInt(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right
            );
            return true;
        }
        if ("addExact".equals(methodRef.name()) && "(JJ)J".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            lowerMathAddExactLong(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right
            );
            return true;
        }
        if ("subtractExact".equals(methodRef.name()) && "(JJ)J".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            lowerMathSubtractExactLong(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right
            );
            return true;
        }
        if ("subtractExact".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            lowerMathSubtractExactInt(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right
            );
            return true;
        }
        if ("multiplyExact".equals(methodRef.name()) && "(II)I".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popInt(classFile, method, stack);
            lowerMathMultiplyExactInt(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right
            );
            return true;
        }
        if ("multiplyExact".equals(methodRef.name()) && "(JI)J".equals(methodRef.descriptor())) {
            final IrExpression right = popInt(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            lowerMathMultiplyExact(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right,
                IrType.INT,
                "javan_math_multiply_exact_long_int_overflows",
                "javan_math_multiply_exact_long_int"
            );
            return true;
        }
        if ("multiplyExact".equals(methodRef.name()) && "(JJ)J".equals(methodRef.descriptor())) {
            final IrExpression right = popLong(classFile, method, stack);
            final IrExpression left = popLong(classFile, method, stack);
            lowerMathMultiplyExact(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                left,
                right,
                IrType.LONG,
                "javan_math_multiply_exact_long_long_overflows",
                "javan_math_multiply_exact_long_long"
            );
            return true;
        }
        if ("incrementExact".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            lowerMathExactIntUnary(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popInt(classFile, method, stack),
                "javan_math_increment_exact_int_overflows",
                "javan_math_increment_exact_int",
                "label_math_increment_exact_int_success_"
            );
            return true;
        }
        if ("incrementExact".equals(methodRef.name()) && "(J)J".equals(methodRef.descriptor())) {
            lowerMathExactLongUnary(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popLong(classFile, method, stack),
                "javan_math_increment_exact_long_overflows",
                "javan_math_increment_exact_long",
                "label_math_increment_exact_long_success_"
            );
            return true;
        }
        if ("decrementExact".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            lowerMathExactIntUnary(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popInt(classFile, method, stack),
                "javan_math_decrement_exact_int_overflows",
                "javan_math_decrement_exact_int",
                "label_math_decrement_exact_int_success_"
            );
            return true;
        }
        if ("decrementExact".equals(methodRef.name()) && "(J)J".equals(methodRef.descriptor())) {
            lowerMathExactLongUnary(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popLong(classFile, method, stack),
                "javan_math_decrement_exact_long_overflows",
                "javan_math_decrement_exact_long",
                "label_math_decrement_exact_long_success_"
            );
            return true;
        }
        if ("negateExact".equals(methodRef.name()) && "(I)I".equals(methodRef.descriptor())) {
            lowerMathExactIntUnary(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popInt(classFile, method, stack),
                "javan_math_negate_exact_int_overflows",
                "javan_math_negate_exact_int",
                "label_math_negate_exact_int_success_"
            );
            return true;
        }
        if ("negateExact".equals(methodRef.name()) && "(J)J".equals(methodRef.descriptor())) {
            lowerMathExactLongUnary(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popLong(classFile, method, stack),
                "javan_math_negate_exact_long_overflows",
                "javan_math_negate_exact_long",
                "label_math_negate_exact_long_success_"
            );
            return true;
        }
        if ("toIntExact".equals(methodRef.name()) && "(J)I".equals(methodRef.descriptor())) {
            lowerMathToIntExact(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                popLong(classFile, method, stack)
            );
            return true;
        }
        return false;
    }

    static void lowerMathToIntExact(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression value
    ) {
        final int valueLocalIndex = localDeclarations.size();
        final String valueLocalName = "long" + valueLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + valueLocalIndex, new IrLocal(IrType.LONG, valueLocalName));
        instructions.add(IrInstruction.assignLong(valueLocalName, value));

        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(
                "javan_math_to_int_exact_overflows",
                List.of(IrExpression.longLocal(valueLocalName))
            )
        ));

        final String successLabel = "label_math_to_int_exact_success_"
            + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("integer overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_math_to_int_exact",
            List.of(IrExpression.longLocal(valueLocalName))
        )));
    }

    static void lowerMathExactIntUnary(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression value,
        final String overflowSymbol,
        final String resultSymbol,
        final String labelPrefix
    ) {
        final int valueLocalIndex = localDeclarations.size();
        final String valueLocalName = "int" + valueLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + valueLocalIndex, new IrLocal(IrType.INT, valueLocalName));
        instructions.add(IrInstruction.assignInt(valueLocalName, value));

        final IrExpression checkedValue = IrExpression.intLocal(valueLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(overflowSymbol, List.of(checkedValue))
        ));

        final String successLabel = labelPrefix + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("integer overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.intExpression(IrExpression.intCall(resultSymbol, List.of(checkedValue))));
    }

    static void lowerMathExactLongUnary(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression value,
        final String overflowSymbol,
        final String resultSymbol,
        final String labelPrefix
    ) {
        final int valueLocalIndex = localDeclarations.size();
        final String valueLocalName = "long" + valueLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + valueLocalIndex, new IrLocal(IrType.LONG, valueLocalName));
        instructions.add(IrInstruction.assignLong(valueLocalName, value));

        final IrExpression checkedValue = IrExpression.longLocal(valueLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(overflowSymbol, List.of(checkedValue))
        ));

        final String successLabel = labelPrefix + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("long overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.longExpression(IrExpression.longCall(resultSymbol, List.of(checkedValue))));
    }

    static void lowerMathAddExactInt(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression left,
        final IrExpression right
    ) {
        final int leftLocalIndex = localDeclarations.size();
        final String leftLocalName = "int" + leftLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + leftLocalIndex, new IrLocal(IrType.INT, leftLocalName));
        instructions.add(IrInstruction.assignInt(leftLocalName, left));

        final int rightLocalIndex = localDeclarations.size();
        final String rightLocalName = "int" + rightLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + rightLocalIndex, new IrLocal(IrType.INT, rightLocalName));
        instructions.add(IrInstruction.assignInt(rightLocalName, right));

        final IrExpression checkedLeft = IrExpression.intLocal(leftLocalName);
        final IrExpression checkedRight = IrExpression.intLocal(rightLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall("javan_math_add_exact_int_overflows", List.of(checkedLeft, checkedRight))
        ));

        final String successLabel = "label_math_add_exact_success_" + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("integer overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_math_add_exact_int",
            List.of(checkedLeft, checkedRight)
        )));
    }

    static void lowerMathAddExactLong(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression left,
        final IrExpression right
    ) {
        final int leftLocalIndex = localDeclarations.size();
        final String leftLocalName = "long" + leftLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + leftLocalIndex, new IrLocal(IrType.LONG, leftLocalName));
        instructions.add(IrInstruction.assignLong(leftLocalName, left));

        final int rightLocalIndex = localDeclarations.size();
        final String rightLocalName = "long" + rightLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + rightLocalIndex, new IrLocal(IrType.LONG, rightLocalName));
        instructions.add(IrInstruction.assignLong(rightLocalName, right));

        final IrExpression checkedLeft = IrExpression.longLocal(leftLocalName);
        final IrExpression checkedRight = IrExpression.longLocal(rightLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall("javan_math_add_exact_long_overflows", List.of(checkedLeft, checkedRight))
        ));

        final String successLabel = "label_math_add_exact_long_success_" + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("long overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.longExpression(IrExpression.longCall(
            "javan_math_add_exact_long",
            List.of(checkedLeft, checkedRight)
        )));
    }

    static void lowerMathSubtractExactInt(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression left,
        final IrExpression right
    ) {
        final int leftLocalIndex = localDeclarations.size();
        final String leftLocalName = "int" + leftLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + leftLocalIndex, new IrLocal(IrType.INT, leftLocalName));
        instructions.add(IrInstruction.assignInt(leftLocalName, left));

        final int rightLocalIndex = localDeclarations.size();
        final String rightLocalName = "int" + rightLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + rightLocalIndex, new IrLocal(IrType.INT, rightLocalName));
        instructions.add(IrInstruction.assignInt(rightLocalName, right));

        final IrExpression checkedLeft = IrExpression.intLocal(leftLocalName);
        final IrExpression checkedRight = IrExpression.intLocal(rightLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(
                "javan_math_subtract_exact_int_overflows",
                List.of(checkedLeft, checkedRight)
            )
        ));

        final String successLabel = "label_math_subtract_exact_int_success_"
            + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("integer overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_math_subtract_exact_int",
            List.of(checkedLeft, checkedRight)
        )));
    }

    static void lowerMathSubtractExactLong(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression left,
        final IrExpression right
    ) {
        final int leftLocalIndex = localDeclarations.size();
        final String leftLocalName = "long" + leftLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + leftLocalIndex, new IrLocal(IrType.LONG, leftLocalName));
        instructions.add(IrInstruction.assignLong(leftLocalName, left));

        final int rightLocalIndex = localDeclarations.size();
        final String rightLocalName = "long" + rightLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + rightLocalIndex, new IrLocal(IrType.LONG, rightLocalName));
        instructions.add(IrInstruction.assignLong(rightLocalName, right));

        final IrExpression checkedLeft = IrExpression.longLocal(leftLocalName);
        final IrExpression checkedRight = IrExpression.longLocal(rightLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(
                "javan_math_subtract_exact_long_overflows",
                List.of(checkedLeft, checkedRight)
            )
        ));

        final String successLabel = "label_math_subtract_exact_long_success_"
            + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("long overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.longExpression(IrExpression.longCall(
            "javan_math_subtract_exact_long",
            List.of(checkedLeft, checkedRight)
        )));
    }

    static void lowerMathMultiplyExactInt(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression left,
        final IrExpression right
    ) {
        final int leftLocalIndex = localDeclarations.size();
        final String leftLocalName = "int" + leftLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + leftLocalIndex, new IrLocal(IrType.INT, leftLocalName));
        instructions.add(IrInstruction.assignInt(leftLocalName, left));

        final int rightLocalIndex = localDeclarations.size();
        final String rightLocalName = "int" + rightLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + rightLocalIndex, new IrLocal(IrType.INT, rightLocalName));
        instructions.add(IrInstruction.assignInt(rightLocalName, right));

        final IrExpression checkedLeft = IrExpression.intLocal(leftLocalName);
        final IrExpression checkedRight = IrExpression.intLocal(rightLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(
                "javan_math_multiply_exact_int_overflows",
                List.of(checkedLeft, checkedRight)
            )
        ));

        final String successLabel = "label_math_multiply_exact_int_success_"
            + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("integer overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.intExpression(IrExpression.intCall(
            "javan_math_multiply_exact_int",
            List.of(checkedLeft, checkedRight)
        )));
    }

    static void lowerMathMultiplyExact(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final IrExpression left,
        final IrExpression right,
        final IrType rightType,
        final String overflowSymbol,
        final String productSymbol
    ) {
        final int leftLocalIndex = localDeclarations.size();
        final String leftLocalName = "long" + leftLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + leftLocalIndex, new IrLocal(IrType.LONG, leftLocalName));
        instructions.add(IrInstruction.assignLong(leftLocalName, left));

        final int rightLocalIndex = localDeclarations.size();
        final String rightLocalName = (rightType == IrType.LONG ? "long" : "int") + rightLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + rightLocalIndex, new IrLocal(rightType, rightLocalName));
        if (rightType == IrType.LONG) {
            instructions.add(IrInstruction.assignLong(rightLocalName, right));
        } else {
            instructions.add(IrInstruction.assignInt(rightLocalName, right));
        }

        final IrExpression checkedLeft = IrExpression.longLocal(leftLocalName);
        final IrExpression checkedRight = rightType == IrType.LONG
            ? IrExpression.longLocal(rightLocalName)
            : IrExpression.intLocal(rightLocalName);
        final int overflowLocalIndex = localDeclarations.size();
        final String overflowLocalName = "int" + overflowLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + overflowLocalIndex, new IrLocal(IrType.INT, overflowLocalName));
        instructions.add(IrInstruction.assignInt(
            overflowLocalName,
            IrExpression.intCall(
                overflowSymbol,
                List.of(checkedLeft, checkedRight)
            )
        ));

        final String successLabel = "label_math_multiply_exact_success_"
            + instruction.offset() + "_" + overflowLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison(
                "==",
                IrExpression.intLocal(overflowLocalName),
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
            "java/lang/ArithmeticException",
            IrExpression.stringLiteral("long overflow")
        );
        instructions.add(IrInstruction.label(successLabel));
        stack.addAll(successStack);
        stack.add(StackValue.longExpression(IrExpression.longCall(
            productSymbol,
            List.of(checkedLeft, checkedRight)
        )));
    }

    static boolean lowerPureMathIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("round".equals(methodRef.name()) && "(F)I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_math_round_float",
                List.of(popFloat(classFile, method, stack))
            )));
            return true;
        }
        if ("round".equals(methodRef.name()) && "(D)J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                    "javan_math_round_double",
                    List.of(popDouble(classFile, method, stack))
            )));
            return true;
        }
        if ("floor".equals(methodRef.name()) && "(D)D".equals(methodRef.descriptor())) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(
                "javan_math_floor_double",
                List.of(popDouble(classFile, method, stack))
            )));
            return true;
        }
        if ("ceil".equals(methodRef.name()) && "(D)D".equals(methodRef.descriptor())) {
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall(
                "javan_math_ceil_double",
                List.of(popDouble(classFile, method, stack))
            )));
            return true;
        }
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
        if ("min".equals(methodRef.name()) && "(FF)F".equals(methodRef.descriptor())) {
            final IrExpression right = popFloat(classFile, method, stack);
            final IrExpression left = popFloat(classFile, method, stack);
            stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_math_min_float", List.of(left, right))));
            return true;
        }
        if ("min".equals(methodRef.name()) && "(DD)D".equals(methodRef.descriptor())) {
            final IrExpression right = popDouble(classFile, method, stack);
            final IrExpression left = popDouble(classFile, method, stack);
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_math_min_double", List.of(left, right))));
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
        if ("max".equals(methodRef.name()) && "(FF)F".equals(methodRef.descriptor())) {
            final IrExpression right = popFloat(classFile, method, stack);
            final IrExpression left = popFloat(classFile, method, stack);
            stack.add(StackValue.floatExpression(IrExpression.floatCall("javan_math_max_float", List.of(left, right))));
            return true;
        }
        if ("max".equals(methodRef.name()) && "(DD)D".equals(methodRef.descriptor())) {
            final IrExpression right = popDouble(classFile, method, stack);
            final IrExpression left = popDouble(classFile, method, stack);
            stack.add(StackValue.doubleExpression(IrExpression.doubleCall("javan_math_max_double", List.of(left, right))));
            return true;
        }
        return false;
    }

    private static String newDoubleLocal(final Map<Integer, IrLocal> localDeclarations) {
        final int localIndex = localDeclarations.size();
        final String localName = "double" + localIndex;
        localDeclarations.put(Integer.MIN_VALUE + localIndex, new IrLocal(IrType.DOUBLE, localName));
        return localName;
    }
}
