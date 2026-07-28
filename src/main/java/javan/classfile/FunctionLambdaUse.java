package javan.classfile;

import java.util.List;

/**
 * Recognizes bounded stack-only uses of {@code Function} lambda results.
 */
public final class FunctionLambdaUse {
    private FunctionLambdaUse() {
    }

    /**
     * Returns whether a zero-capture lambda result is immediately duplicated under two category-1 values and all aliases are discarded.
     *
     * @param lambda resolved lambda shape
     * @param method method containing the invokedynamic instruction
     * @param invocation invokedynamic instruction to inspect
     * @return true only for zero captures and the exact {@code dup_x2; pop; pop; pop; pop} bytecode window
     */
    public static boolean isProvablyDiscardedZeroCapture(
        final LambdaMetafactoryCall lambda,
        final MethodInfo method,
        final Instruction invocation
    ) {
        if (!lambda.capturedParameterDescriptors().isEmpty() || method.code().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() != invocation.offset()) {
                continue;
            }
            return index + 5 < instructions.size()
                && instructions.get(index + 1).opcode() == 91
                && instructions.get(index + 2).opcode() == 87
                && instructions.get(index + 3).opcode() == 87
                && instructions.get(index + 4).opcode() == 87
                && instructions.get(index + 5).opcode() == 87;
        }
        return false;
    }
}
