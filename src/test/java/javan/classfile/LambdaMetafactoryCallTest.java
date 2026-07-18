package javan.classfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class LambdaMetafactoryCallTest {
    @Test
    void resolveRejectsWrongBootstrapOwner() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "com/acme/Bootstrap",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ))).isEmpty();
    }

    @Test
    void resolveRejectsWrongBootstrapName() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "altfactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ))).isEmpty();
    }

    @Test
    void resolveRejectsWrongBootstrapDescriptor() {
        final DynamicRef dynamicRef = new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "()V",
            List.of(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                "invokestatic com/acme/Main.lambda$0:(Ljava/lang/Object;)Ljava/lang/Object;",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            ),
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        );

        assertThat(LambdaMetafactoryCall.resolve(dynamicRef)).isEmpty();
    }

    @Test
    void resolveRejectsWrongBootstrapArgumentCount() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;"))
            )
        ))).isEmpty();
    }

    @Test
    void resolveRejectsWrongFirstBootstrapArgumentKind() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.string("wrong"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ))).isEmpty();
    }

    @Test
    void resolveRejectsMethodHandleArgumentWithoutResolvedMethodRef() {
        final DynamicRef dynamicRef = new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            metafactoryDescriptor(),
            List.of(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                "invokestatic com/acme/Main.lambda$0:(Ljava/lang/Object;)Ljava/lang/Object;",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            ),
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                new BootstrapArgument(BootstrapArgument.Kind.METHOD_HANDLE, "broken", Optional.empty(), 6),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        );

        assertThat(LambdaMetafactoryCall.resolve(dynamicRef)).isEmpty();
    }

    @Test
    void resolveRejectsWrongThirdBootstrapArgumentKind() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.string("wrong")
            )
        ))).isEmpty();
    }

    @Test
    void resolveRejectsMalformedCapturedDescriptorWhenNotZeroCapture() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "(Ljava/lang/String)Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ))).isEmpty();
    }

    @Test
    void resolveRejectsPrimitiveReturnDescriptor() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()I",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ))).isEmpty();
    }

    @Test
    void resolveAcceptsArrayCaptureDescriptors() {
        final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "([Ljava/lang/String;[[I)Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "([Ljava/lang/String;[[ILjava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ));

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().capturedParameterDescriptors()).containsExactly("[Ljava/lang/String;", "[[I");
    }

    @Test
    void resolveRejectsMalformedArrayCapturedDescriptor() {
        assertThat(LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "([Q)Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ))).isEmpty();
    }

    @Test
    void resolveAcceptsCustomSamObjectLambda() {
        final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "convert",
            "()Lcom/acme/Converter;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        ));

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().interfaceOwner()).isEqualTo("com/acme/Converter");
        assertThat(resolved.orElseThrow().isZeroCaptureMaterializedObjectLambda()).isTrue();
    }

    @Test
    void resolveAcceptsCustomSamArrayReturnLambdaAsMaterializedObject() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "convert",
            "()Lcom/acme/ArrayConverter;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)[Ljava/lang/String;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)[Ljava/lang/String;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)[Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isZeroCaptureMaterializedObjectLambda()).isTrue();
    }

    @Test
    void resolveAcceptsZeroCaptureBiFunctionAsMaterializedLambda() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/BiFunction;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedBiFunctionLambda()).isTrue();
    }

    @Test
    void directlyLowerableAcceptsInterfacePredicateReferenceKindNine() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "test",
            "()Ljava/util/function/Predicate;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Z"),
                BootstrapArgument.methodHandle(9, new MethodRef("com/acme/PredicateLike", "test", "(Ljava/lang/Object;)Z")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Z")
            )
        )).orElseThrow();

        assertThat(resolved.isPredicate()).isTrue();
        assertThat(resolved.isDirectlyLowerable()).isTrue();
    }

    @Test
    void directlyLowerableRejectsConsumerShape() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "accept",
            "()Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)V"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)V")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)V")
            )
        )).orElseThrow();

        assertThat(resolved.isConsumer()).isTrue();
        assertThat(resolved.isDirectlyLowerable()).isFalse();
    }

    @Test
    void directlyLowerableRejectsUnsupportedReferenceKind() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(5, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();

        assertThat(resolved.isFunction()).isTrue();
        assertThat(resolved.isDirectlyLowerable()).isFalse();
    }

    @Test
    void exactFunctionOrNullMaterializationAcceptsZeroCaptureStaticShape() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "applyWithException",
            "()Lcom/acme/FunctionOrNull;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();

        assertThat(resolved.isExactFunctionOrNullMaterialization()).isTrue();
    }

    @Test
    void materializedConsumerLambdaAcceptsObjectAndArrayCaptures() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "accept",
            "(Ljava/lang/String;[I)Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)V"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/String;[ILjava/lang/Object;)V")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)V")
            )
        )).orElseThrow();

        assertThat(resolved.capturedParameterDescriptors()).containsExactly("Ljava/lang/String;", "[I");
        assertThat(resolved.isMaterializedConsumerLambda()).isTrue();
    }

    @Test
    void materializedConsumerLambdaRejectsPrimitiveCapture() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "accept",
            "(I)Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)V"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(ILjava/lang/Object;)V")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)V")
            )
        )).orElseThrow();

        assertThat(resolved.isConsumer()).isTrue();
        assertThat(resolved.isMaterializedConsumerLambda()).isFalse();
    }

    @Test
    void zeroCaptureMaterializedBooleanLambdaAcceptsCustomBooleanSam() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "matches",
            "()Lcom/acme/BooleanLike;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Z"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;)Z")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Z")
            )
        )).orElseThrow();

        assertThat(resolved.isZeroCaptureMaterializedBooleanLambda()).isTrue();
    }

    @Test
    void zeroCaptureMaterializedObjectLambdaRejectsCapturedObject() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "convert",
            "(Ljava/lang/String;)Lcom/acme/Converter;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();

        assertThat(resolved.capturedParameterDescriptors()).containsExactly("Ljava/lang/String;");
        assertThat(resolved.isZeroCaptureMaterializedObjectLambda()).isFalse();
    }

    @Test
    void exactFunctionOrNullMaterializationRequiresZeroCapture() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "applyWithException",
            "(Ljava/lang/Object;)Lcom/acme/FunctionOrNull;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();

        assertThat(resolved.capturedParameterDescriptors()).containsExactly("Ljava/lang/Object;");
        assertThat(resolved.isExactFunctionOrNullMaterialization()).isFalse();
    }

    private static DynamicRef dynamicRef(
        final String name,
        final String descriptor,
        final String bootstrapOwner,
        final String bootstrapName,
        final List<BootstrapArgument> arguments
    ) {
        return new DynamicRef(
            name,
            descriptor,
            bootstrapOwner,
            bootstrapName,
            metafactoryDescriptor(),
            arguments.stream().map(BootstrapArgument::text).toList(),
            List.copyOf(arguments)
        );
    }

    private static String metafactoryDescriptor() {
        return "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
            + "Ljava/lang/invoke/CallSite;";
    }
}
