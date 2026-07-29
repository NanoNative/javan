package javan.classfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    void materializedFunctionAcceptsZeroCaptureStaticTarget() {
        final LambdaMetafactoryCall resolved = materializedFunction(
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        assertThat(resolved.isMaterializedFunctionLambda()).isTrue();
    }

    @Test
    void materializedFunctionAcceptsReferenceCapture() {
        final LambdaMetafactoryCall resolved = materializedFunction(
            "(Ljava/lang/String;)Ljava/util/function/Function;",
            "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;"
        );

        assertThat(resolved.isMaterializedFunctionLambda()).isTrue();
    }

    @Test
    void materializedFunctionRejectsPrimitiveCapture() {
        final LambdaMetafactoryCall resolved = materializedFunction(
            "(I)Ljava/util/function/Function;",
            "(ILjava/lang/Object;)Ljava/lang/String;"
        );

        assertThat(resolved.isMaterializedFunctionLambda()).isFalse();
    }

    @Test
    void materializedFunctionRejectsPrimitiveReturn() {
        final LambdaMetafactoryCall resolved = materializedFunction(
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)I"
        );

        assertThat(resolved.isMaterializedFunctionLambda()).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaAcceptsFinalApplicationOwner() {
        final LambdaMetafactoryCall resolved = materializedBoundCustomObjectLambda(
            "(Lcom/acme/Main;)Lcom/acme/Renderer;",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        assertThat(resolved.isMaterializedBoundCustomObjectLambda(finalMainClass())).isTrue();
    }

    @Test
    void materializedBoundCustomLongLambdaAcceptsFinalApplicationOwner() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "key",
            "(Lcom/acme/Main;)Lcom/acme/KeySource;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(J)Ljava/lang/String;"),
                BootstrapArgument.methodHandle(5, new MethodRef("com/acme/Main", "key", "(J)Ljava/lang/String;")),
                BootstrapArgument.methodType("(J)Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedBoundCustomObjectLambda(finalMainClass())).isTrue();
    }

    @Test
    void zeroCaptureStaticCustomLongLambdaAcceptsApplicationOwner() {
        final LambdaMetafactoryCall resolved = staticCustomLongLambda(
            "()Lcom/acme/KeySource;",
            "(J)Ljava/lang/String;",
            "(J)Ljava/lang/String;"
        );

        assertThat(resolved.isZeroCaptureMaterializedLongObjectLambda(applicationStaticLongSamClasses())).isTrue();
    }

    @Test
    void zeroCaptureStaticCustomLongLambdaRejectsCapturedReference() {
        final LambdaMetafactoryCall resolved = staticCustomLongLambda(
            "(Ljava/lang/String;)Lcom/acme/KeySource;",
            "(J)Ljava/lang/String;",
            "(Ljava/lang/String;J)Ljava/lang/String;"
        );

        assertThat(resolved.isZeroCaptureMaterializedLongObjectLambda(applicationStaticLongSamClasses())).isFalse();
    }

    @Test
    void zeroCaptureStaticCustomLongLambdaRejectsDoubleInput() {
        final LambdaMetafactoryCall resolved = staticCustomLongLambda(
            "()Lcom/acme/KeySource;",
            "(D)Ljava/lang/String;",
            "(D)Ljava/lang/String;"
        );

        assertThat(resolved.isZeroCaptureMaterializedLongObjectLambda(applicationStaticLongSamClasses())).isFalse();
    }

    @Test
    void zeroCaptureStaticCustomLongLambdaRejectsDependencyOwner() {
        final LambdaMetafactoryCall resolved = staticCustomLongLambda(
            "()Lcom/acme/KeySource;",
            "(J)Ljava/lang/String;",
            "(J)Ljava/lang/String;"
        );

        assertThat(resolved.isZeroCaptureMaterializedLongObjectLambda(dependencyMainClass())).isFalse();
    }

    @Test
    void zeroCaptureStaticCustomLongLambdaRejectsDependencyInterface() {
        final LambdaMetafactoryCall resolved = staticCustomLongLambda(
            "()Ldep/KeySource;",
            "(J)Ljava/lang/String;",
            "(J)Ljava/lang/String;"
        );

        assertThat(resolved.isZeroCaptureMaterializedLongObjectLambda(dependencyStaticLongSamInterfaceClasses()))
            .isFalse();
    }

    @Test
    void materializedBoundCustomDoubleLambdaRemainsUnsupported() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "key",
            "(Lcom/acme/Main;)Lcom/acme/KeySource;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(D)Ljava/lang/String;"),
                BootstrapArgument.methodHandle(5, new MethodRef("com/acme/Main", "key", "(D)Ljava/lang/String;")),
                BootstrapArgument.methodType("(D)Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedBoundCustomObjectLambda(finalMainClass())).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaRejectsPrimitiveCapture() {
        final LambdaMetafactoryCall resolved = materializedBoundCustomObjectLambda(
            "(Lcom/acme/Main;I)Lcom/acme/Renderer;",
            "(ILjava/lang/Object;)Ljava/lang/Object;"
        );

        assertThat(resolved.isMaterializedBoundCustomObjectLambda()).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaRejectsPrimitiveReturn() {
        final LambdaMetafactoryCall resolved = materializedBoundCustomObjectLambda(
            "(Lcom/acme/Main;)Lcom/acme/Renderer;",
            "(Ljava/lang/Object;)I"
        );

        assertThat(resolved.isMaterializedBoundCustomObjectLambda()).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaRejectsMismatchedReceiverCapture() {
        final LambdaMetafactoryCall resolved = materializedBoundCustomObjectLambda(
            "(Lcom/acme/Other;)Lcom/acme/Renderer;",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        assertThat(resolved.isMaterializedBoundCustomObjectLambda()).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaRejectsNonFinalOwner() {
        final LambdaMetafactoryCall resolved = materializedBoundCustomObjectLambda(
            "(Lcom/acme/Main;)Lcom/acme/Renderer;",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        assertThat(resolved.isMaterializedBoundCustomObjectLambda(mainClass(0))).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaRejectsDependencyOwner() {
        final LambdaMetafactoryCall resolved = materializedBoundCustomObjectLambda(
            "(Lcom/acme/Main;)Lcom/acme/Renderer;",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        assertThat(resolved.isMaterializedBoundCustomObjectLambda(dependencyMainClass())).isFalse();
    }

    @Test
    void materializedBoundCustomObjectLambdaRejectsStandardFunction() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            "(Lcom/acme/Main;)Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(5, new MethodRef("com/acme/Main", "render", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedBoundCustomObjectLambda(finalMainClass())).isFalse();
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
    void directlyLowerableAcceptsBoundInstancePredicateOnFinalClass() {
        final LambdaMetafactoryCall resolved = boundInstancePredicate("(Lcom/acme/Main;)Ljava/util/function/Predicate;");

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isTrue();
    }

    @Test
    void directlyLowerableRejectsBoundInstancePredicateOnNonFinalClass() {
        final LambdaMetafactoryCall resolved = boundInstancePredicate("(Lcom/acme/Main;)Ljava/util/function/Predicate;");

        assertThat(resolved.isDirectlyLowerable(mainClass(0))).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstancePredicateWithMismatchedReceiverCapture() {
        final LambdaMetafactoryCall resolved = boundInstancePredicate("(Lcom/acme/Other;)Ljava/util/function/Predicate;");

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstancePredicateWithMismatchedInput() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "test",
            "(Lcom/acme/Main;)Ljava/util/function/Predicate;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Z"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "matches", "(I)Z")
                ),
                BootstrapArgument.methodType("(Ljava/lang/String;)Z")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableAcceptsBoundInstanceFunctionOnFinalClass() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isTrue();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceFunctionOnNonFinalClass() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction();

        assertThat(resolved.isDirectlyLowerable(mainClass(0))).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceFunctionWithMismatchedReceiverCapture() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction(
            "(Lcom/acme/Other;Ljava/lang/String;)Ljava/util/function/Function;",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceFunctionWithPrimitiveCapture() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction(
            "(Lcom/acme/Main;I)Ljava/util/function/Function;",
            "(ILjava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceFunctionWithPrimitiveReturn() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction(
            "(Lcom/acme/Main;Ljava/lang/String;)Ljava/util/function/Function;",
            "(Ljava/lang/String;Ljava/lang/String;)I",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceFunctionWithVoidReturn() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction(
            "(Lcom/acme/Main;Ljava/lang/String;)Ljava/util/function/Function;",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceFunctionWithIncompatibleInput() {
        final LambdaMetafactoryCall resolved = boundInstanceFunction(
            "(Lcom/acme/Main;Ljava/lang/String;)Ljava/util/function/Function;",
            "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableAcceptsBoundInstanceSupplierLambdaReferenceKindFive() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;Ljava/lang/String;I)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "lambda$supply$0", "(Ljava/lang/String;I)Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isTrue();
    }

    @Test
    void directlyLowerableAcceptsBoundInstanceSupplierMethodReferenceOnFinalClass() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "supply", "()Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isTrue();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierOnNonFinalClass() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "supply", "()Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(mainClass(0))).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierLambdaWithMismatchedPrimitiveCapture() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;J)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "lambda$supply$0", "(I)Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceCustomSamOnFinalClass() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "load",
            "(Lcom/acme/Main;)Lcom/acme/Loader;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "load", "()Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierWithMalformedImplementationReturnDescriptor() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;Ljava/lang/String;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef(
                        "com/acme/Main",
                        "supply",
                        "(Ljava/lang/String;)Ljava/lang/String;junk"
                    )
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierWithEmptyObjectArrayComponent() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "supply", "()[L;")
                ),
                BootstrapArgument.methodType("()[Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierWithEmptyObjectCapture() {
        final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;L;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "supply", "(L;)Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        ));

        assertThat(resolved).isEmpty();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierWithMalformedImplementationParameter() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "supply", "(L;)Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
    }

    @Test
    void directlyLowerableRejectsBoundInstanceSupplierWithMalformedInstantiatedParameter() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "supply", "()Ljava/lang/String;")
                ),
                BootstrapArgument.methodType("(L;)Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isDirectlyLowerable(finalMainClass())).isFalse();
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
    void materializedSupplierLambdaAcceptsCapturedStaticTarget() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedSupplierLambda()).isTrue();
    }

    @Test
    void materializedSupplierLambdaAcceptsCapturedInstanceTarget() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(Lcom/acme/Main;)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(5, new MethodRef("com/acme/Main", "value", "()Ljava/lang/String;")),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedSupplierLambda()).isTrue();
    }

    @Test
    void materializedSupplierLambdaRejectsPrimitiveCapture() {
        final LambdaMetafactoryCall resolved = LambdaMetafactoryCall.resolve(dynamicRef(
            "get",
            "(I)Ljava/util/function/Supplier;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("()Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(6, new MethodRef("com/acme/Main", "lambda$0", "(I)Ljava/lang/String;")),
                BootstrapArgument.methodType("()Ljava/lang/String;")
            )
        )).orElseThrow();

        assertThat(resolved.isMaterializedSupplierLambda()).isFalse();
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

    private static Map<String, ClassFile> finalMainClass() {
        return mainClass(0x0010);
    }

    private static Map<String, ClassFile> applicationStaticLongSamClasses() {
        return Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", true),
            "com/acme/KeySource",
            classFile("com/acme/KeySource", true)
        );
    }

    private static LambdaMetafactoryCall boundInstancePredicate(final String callSiteDescriptor) {
        return LambdaMetafactoryCall.resolve(dynamicRef(
            "test",
            callSiteDescriptor,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Z"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "matches", "(Ljava/lang/Object;)Z")
                ),
                BootstrapArgument.methodType("(Ljava/lang/String;)Z")
            )
        )).orElseThrow();
    }

    private static LambdaMetafactoryCall boundInstanceFunction() {
        return boundInstanceFunction(
            "(Lcom/acme/Main;Ljava/lang/String;)Ljava/util/function/Function;",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );
    }

    private static LambdaMetafactoryCall boundInstanceFunction(
        final String callSiteDescriptor,
        final String implementationDescriptor,
        final String instantiatedDescriptor
    ) {
        return LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            callSiteDescriptor,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef(
                        "com/acme/Main",
                        "lambda$map$0",
                        implementationDescriptor
                    )
                ),
                BootstrapArgument.methodType(instantiatedDescriptor)
            )
        )).orElseThrow();
    }

    private static LambdaMetafactoryCall materializedFunction(
        final String callSiteDescriptor,
        final String implementationDescriptor
    ) {
        return LambdaMetafactoryCall.resolve(dynamicRef(
            "apply",
            callSiteDescriptor,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    6,
                    new MethodRef("com/acme/Main", "lambda$apply$0", implementationDescriptor)
                ),
                BootstrapArgument.methodType("(Ljava/lang/String;)Ljava/lang/String;")
            )
        )).orElseThrow();
    }

    private static LambdaMetafactoryCall materializedBoundCustomObjectLambda(
        final String callSiteDescriptor,
        final String implementationDescriptor
    ) {
        return LambdaMetafactoryCall.resolve(dynamicRef(
            "render",
            callSiteDescriptor,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    5,
                    new MethodRef("com/acme/Main", "render", implementationDescriptor)
                ),
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
            )
        )).orElseThrow();
    }

    private static LambdaMetafactoryCall staticCustomLongLambda(
        final String callSiteDescriptor,
        final String samDescriptor,
        final String implementationDescriptor
    ) {
        return LambdaMetafactoryCall.resolve(dynamicRef(
            "key",
            callSiteDescriptor,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            List.of(
                BootstrapArgument.methodType(samDescriptor),
                BootstrapArgument.methodHandle(
                    6,
                    new MethodRef("com/acme/Main", "lambda$key$0", implementationDescriptor)
                ),
                BootstrapArgument.methodType(samDescriptor)
            )
        )).orElseThrow();
    }

    private static Map<String, ClassFile> mainClass(final int accessFlags) {
        return Map.of(
            "com/acme/Main",
            new ClassFile(
                69,
                "com/acme/Main",
                "java/lang/Object",
                accessFlags,
                List.of(),
                List.of(),
                List.of(),
                Path.of("Main.class"),
                true
            )
        );
    }

    private static Map<String, ClassFile> dependencyMainClass() {
        return Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", false)
        );
    }

    private static Map<String, ClassFile> dependencyStaticLongSamInterfaceClasses() {
        return Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", true),
            "dep/KeySource",
            classFile("dep/KeySource", false)
        );
    }

    private static ClassFile classFile(final String name, final boolean application) {
        return new ClassFile(
            69,
            name,
            "java/lang/Object",
            0x0010,
            List.of(),
            List.of(),
            List.of(),
            Path.of(name.substring(name.lastIndexOf('/') + 1) + ".class"),
            application
        );
    }
}
