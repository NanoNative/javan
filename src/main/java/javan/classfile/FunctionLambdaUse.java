package javan.classfile;

import java.util.List;
import java.util.Optional;

/**
 * Recognizes bounded stack-only uses of {@code Function} lambda results.
 */
public final class FunctionLambdaUse {
    private FunctionLambdaUse() {
    }

    /**
     * Returns whether a function result escapes its immediate direct-consumer window.
     *
     * @param method method containing the invokedynamic instruction
     * @param invocation invokedynamic instruction to inspect
     * @return true when the function must be represented by a runtime object
     */
    public static boolean requiresMaterialization(
        final MethodInfo method,
        final Instruction invocation
    ) {
        if (method.code().isEmpty()) {
            return true;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() != invocation.offset()) {
                continue;
            }
            for (int consumerIndex = index + 1; consumerIndex < instructions.size(); consumerIndex++) {
                final Instruction candidate = instructions.get(consumerIndex);
                final Optional<MethodRef> consumer = candidate.methodRef();
                if (consumer.isPresent()) {
                    return !isInlineFunctionConsumer(consumer.orElseThrow());
                }
                if (endsInlineFunctionSearch(candidate.opcode())) {
                    return true;
                }
            }
            return true;
        }
        return true;
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

    /**
     * Returns whether a function result enters an unsupported {@code dup_x2} stack permutation.
     *
     * @param lambda resolved lambda shape
     * @param method method containing the invokedynamic instruction
     * @param invocation invokedynamic instruction to inspect
     * @return true when {@code dup_x2} follows the function and the exact discard shape does not apply
     */
    public static boolean hasUnsupportedDupX2Use(
        final LambdaMetafactoryCall lambda,
        final MethodInfo method,
        final Instruction invocation
    ) {
        if (method.code().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        for (int index = 0; index + 1 < instructions.size(); index++) {
            if (instructions.get(index).offset() != invocation.offset()) {
                continue;
            }
            return instructions.get(index + 1).opcode() == 91
                && !isProvablyDiscardedZeroCapture(lambda, method, invocation);
        }
        return false;
    }

    private static boolean isInlineFunctionConsumer(final MethodRef target) {
        if ("java/util/function/Function".equals(target.owner())
            && "apply".equals(target.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor())) {
            return true;
        }
        if ("computeIfAbsent".equals(target.name())
            && "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(target.descriptor())
            && ("java/util/Map".equals(target.owner())
                || "java/util/HashMap".equals(target.owner())
                || "java/util/LinkedHashMap".equals(target.owner())
                || "java/util/TreeMap".equals(target.owner()))) {
            return true;
        }
        return "java/util/Optional".equals(target.owner())
            && ("map".equals(target.name()) || "flatMap".equals(target.name()))
            && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(target.descriptor());
    }

    private static boolean endsInlineFunctionSearch(final int opcode) {
        return (opcode >= 54 && opcode <= 95)
            || (opcode >= 153 && opcode <= 177)
            || opcode == 179
            || opcode == 181
            || opcode == 186
            || opcode == 191
            || opcode == 194
            || opcode == 195
            || opcode == 198
            || opcode == 199;
    }
}
