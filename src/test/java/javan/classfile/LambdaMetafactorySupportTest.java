package javan.classfile;

import javan.analysis.EntryPoint;
import javan.ir.IrType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class LambdaMetafactorySupportTest {
    @Test
    void scanSkipsMethodsOutsideScope() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "([Ljava/lang/String;)V", invokeDynamic(7, runnableLambda("com/acme/Main", "lambda$main$0", "()V", 6))),
                method("lambda$main$0", "()V")
            )
        );

        final LambdaMetafactorySupport.Registry registry = LambdaMetafactorySupport.scan(
            classes,
            List.of(new EntryPoint("com/acme/Main", "helper", "()V"))
        );

        assertThat(registry.bySite()).isEmpty();
        assertThat(registry.bySyntheticOwner()).isEmpty();
    }

    @Test
    void scanCreatesPlanForStaticImplementationTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "([Ljava/lang/String;)V", invokeDynamic(7, runnableLambda("com/acme/Main", "lambda$main$0", "()V", 6))),
                method("lambda$main$0", "()V")
            )
        );

        final LambdaMetafactorySupport.Registry registry = LambdaMetafactorySupport.scan(classes);
        final LambdaMetafactorySupport.LambdaClosurePlan plan = registry
            .planForSite("com/acme/Main", "main", "([Ljava/lang/String;)V", 7)
            .orElseThrow();

        assertThat(plan.syntheticOwner()).isEqualTo("com/acme/Main$$javan$lambda$main$run$7");
        assertThat(plan.interfaceOwner()).isEqualTo("java/lang/Runnable");
        assertThat(plan.methodName()).isEqualTo("run");
        assertThat(plan.methodDescriptor()).isEqualTo("()V");
        assertThat(plan.captureDescriptors()).isEmpty();
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("com/acme/Main", "lambda$main$0", "()V"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.NONE);
        assertThat(plan.matchesSam(new MethodRef("java/lang/Runnable", "run", "()V"))).isTrue();
        assertThat(plan.matchesSam(new MethodRef("java/lang/Runnable", "run", "(I)V"))).isFalse();
    }

    @Test
    void expandedClassesAddsSyntheticClassForStaticPlan() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "(I)Ljava/lang/Runnable;", invokeDynamic(3, capturedStaticRunnableLambda("com/acme/Main", "lambda$main$0", "(I)V"))),
                method("lambda$main$0", "(I)V")
            )
        );

        final LambdaMetafactorySupport.Registry registry = LambdaMetafactorySupport.scan(classes);
        final ClassFile syntheticClass = registry
            .expandedClasses(classes)
            .get("com/acme/Main$$javan$lambda$main$run$3");

        assertThat(syntheticClass).isNotNull();
        assertThat(syntheticClass.interfaces()).containsExactly("java/lang/Runnable");
        assertThat(syntheticClass.fields()).extracting(FieldInfo::name, FieldInfo::descriptor)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("capture0", "I"));
        assertThat(syntheticClass.methods()).extracting(MethodInfo::name, MethodInfo::descriptor)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("run", "()V"));
    }

    @Test
    void scanCreatesPlanForCapturedVirtualReceiver() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "(Lcom/acme/Greeter;)Ljava/lang/Runnable;", invokeDynamic(9, capturedVirtualRunnableLambda()))
            ),
            "com/acme/Greeter",
            classWithMethods(
                "com/acme/Greeter",
                "java/lang/Object",
                0,
                List.of(),
                method("runTask", "()V")
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "(Lcom/acme/Greeter;)Ljava/lang/Runnable;", 9)
            .orElseThrow();

        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.CAPTURE0);
        assertThat(plan.captureDescriptors()).containsExactly("Lcom/acme/Greeter;");
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("com/acme/Greeter", "runTask", "()V"));
    }

    @Test
    void scanCreatesPlanForFirstParameterVirtualReceiver() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Consumer;", invokeDynamic(11, consumerVirtualLambda()))
            ),
            "com/acme/Greeter",
            classWithMethods(
                "com/acme/Greeter",
                "java/lang/Object",
                0,
                List.of(),
                method("runTask", "()V")
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Consumer;", 11)
            .orElseThrow();

        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
        assertThat(plan.captureDescriptors()).isEmpty();
        assertThat(plan.methodName()).isEqualTo("accept");
        assertThat(plan.methodDescriptor()).isEqualTo("(Ljava/lang/Object;)V");
    }

    @Test
    void scanCreatesPlanForSupportedSupplierBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Function;", invokeDynamic(5, supplierBridgeLambda()))
            ),
            "com/acme/StringSupplier",
            classWithMethods(
                "com/acme/StringSupplier",
                "java/lang/Object",
                0,
                List.of("java/util/function/Supplier"),
                method("get", "()Ljava/lang/Object;", plain(0, 1, "aconst_null"), plain(1, 176, "areturn"))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 5)
            .orElseThrow();

        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"));
        assertThat(plan.wrapperEntryPoint())
            .isEqualTo(new EntryPoint("com/acme/Main$$javan$lambda$main$apply$5", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
    }

    @Test
    void scanCreatesPlanForSupportedBooleanSupplierBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Function;", invokeDynamic(6, booleanSupplierBridgeLambda()))
            ),
            "com/acme/FlagSupplier",
            classWithMethods(
                "com/acme/FlagSupplier",
                "java/lang/Object",
                0,
                List.of("java/util/function/BooleanSupplier"),
                method("getAsBoolean", "()Z", plain(0, 3, "iconst_0"), plain(1, 172, "ireturn"))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/function/BooleanSupplier", "getAsBoolean", "()Z"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanCreatesPlanForSupportedPredicateBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Function;", invokeDynamic(6, predicateBridgeLambda()))
            ),
            "com/acme/NamePredicate",
            classWithMethods(
                "com/acme/NamePredicate",
                "java/lang/Object",
                0,
                List.of("java/util/function/Predicate"),
                method("test", "(Ljava/lang/Object;)Z", plain(0, 3, "iconst_0"), plain(1, 172, "ireturn"))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanCreatesPlanForSupportedFunctionBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Function;", invokeDynamic(6, functionBridgeLambda()))
            ),
            "com/acme/Mapper",
            classWithMethods(
                "com/acme/Mapper",
                "java/lang/Object",
                0,
                List.of("java/util/function/Function"),
                method("apply", "(Ljava/lang/Object;)Ljava/lang/Object;", plain(0, 1, "aconst_null"), plain(1, 176, "areturn"))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanCreatesPlanForSupportedBiConsumerBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Function;", invokeDynamic(6, biConsumerBridgeLambda()))
            ),
            "com/acme/PairConsumer",
            classWithMethods(
                "com/acme/PairConsumer",
                "java/lang/Object",
                0,
                List.of("java/util/function/BiConsumer"),
                method("accept", "(Ljava/lang/Object;Ljava/lang/Object;)V", plain(0, 177, "return"))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanCreatesPlanForSupportedRunnableBridgeDefaultInterfaceTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Consumer;", invokeDynamic(8, runnableBridgeLambda()))
            ),
            "com/acme/BridgeRunnable",
            classWithMethods(
                "com/acme/BridgeRunnable",
                "java/lang/Object",
                0x0200,
                List.of("java/lang/Runnable"),
                method("run", "()V", plain(0, 177, "return"))
            ),
            "com/acme/RunnableImpl",
            classWithMethods(
                "com/acme/RunnableImpl",
                "java/lang/Object",
                0,
                List.of("com/acme/BridgeRunnable")
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Consumer;", 8)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/lang/Runnable", "run", "()V"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanRejectsSupportedBridgeTargetWithoutLowerableImplementation() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Consumer;", invokeDynamic(8, runnableBridgeLambda()))
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsNonLambdaBootstrapOwner() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(1, new DynamicRef("run", "()Ljava/lang/Runnable;", "com/acme/Bootstrap", "bootstrap", "", List.of()))
                )
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsLambdaSiteWithTooFewBootstrapValues() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        2,
                        new DynamicRef(
                            "run",
                            "()Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V"),
                            List.of(new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"))
                        )
                    )
                )
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsUnsupportedBootstrapShape() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        2,
                        new DynamicRef(
                            "run",
                            "()Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.STRING, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsVirtualReferenceWithoutReceiverSource() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        3,
                        new DynamicRef(
                            "run",
                            "()Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Greeter.runTask()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Greeter.runTask()V",
                                    new MethodRef("com/acme/Greeter", "runTask", "()V"),
                                    5
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                )
            ),
            "com/acme/Greeter",
            classWithMethods("com/acme/Greeter", "java/lang/Object", 0, List.of(), method("runTask", "()V"))
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsUnsupportedReferenceKind() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "([Ljava/lang/String;)V", invokeDynamic(4, runnableLambda("com/acme/Main", "lambda$main$0", "()V", 8))),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsImplementationParameterMismatchAfterCaptureBinding() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "(I)Ljava/lang/Runnable;",
                    invokeDynamic(
                        4,
                        new DynamicRef(
                            "run",
                            "(I)Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Main.lambda$main$0(J)V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0(J)V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "(J)V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "(J)V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsMismatchedSamParameterShapes() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Consumer;",
                    invokeDynamic(
                        6,
                        new DynamicRef(
                            "accept",
                            "()Ljava/util/function/Consumer;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("(Ljava/lang/Object;)V", "com/acme/Main.lambda$main$0(I)V", "(I)V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0(I)V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "(I)V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(I)V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "(I)V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsImplementationReturnMismatch() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Supplier;",
                    invokeDynamic(
                        9,
                        new DynamicRef(
                            "get",
                            "()Ljava/util/function/Supplier;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()Ljava/lang/Object;", "com/acme/Main.lambda$main$0()I", "()Ljava/lang/Object;"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()Ljava/lang/Object;"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()I",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()I"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()Ljava/lang/Object;")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()I", plain(0, 3, "iconst_0"), plain(1, 172, "ireturn"))
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsMalformedObjectFactoryDescriptor() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        10,
                        new DynamicRef(
                            "run",
                            "()Lbroken",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsMalformedArrayFactoryDescriptor() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        10,
                        new DynamicRef(
                            "run",
                            "()[",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsDescriptorWithoutOpeningParen() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        10,
                        new DynamicRef(
                            "run",
                            "Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsFactoryDescriptorWithoutObjectReturn() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()I",
                    invokeDynamic(
                        1,
                        new DynamicRef(
                            "run",
                            "()I",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void dynamicRefRecognizesOnlyStandardLambdaMetafactory() {
        assertThat(new DynamicRef("run", "()Ljava/lang/Runnable;", "java/lang/invoke/LambdaMetafactory", "metafactory", "", List.of()).isLambdaMetafactory())
            .isTrue();
        assertThat(new DynamicRef("run", "()Ljava/lang/Runnable;", "java/lang/invoke/LambdaMetafactory", "altMetafactory", "", List.of()).isLambdaMetafactory())
            .isFalse();
    }

    @Test
    void dynamicRefReturnsEmptyImplementationTargetWhenBootstrapShapeIsTooShort() {
        final DynamicRef dynamicRef = new DynamicRef(
            "run",
            "()Ljava/lang/Runnable;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "",
            List.of("()V"),
            List.of(new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"))
        );

        assertThat(dynamicRef.lambdaImplementationTarget()).isEmpty();
    }

    @Test
    void parameterDescriptorsRejectUnknownParameterTag() throws Exception {
        assertThat(parameterDescriptors("(Q)V")).isEmpty();
    }

    @Test
    void parameterDescriptorsRejectMalformedObjectDescriptor() throws Exception {
        assertThat(parameterDescriptors("(Ljava/lang/String)V")).isEmpty();
    }

    @Test
    void parameterDescriptorsParsesPrimitiveObjectAndArrayDescriptors() throws Exception {
        assertThat(parameterDescriptors("(I[Ljava/lang/String;[[IZ)V"))
            .containsExactly("I", "[Ljava/lang/String;", "[[I", "Z");
    }

    @Test
    void skipArrayDescriptorRejectsDanglingArrayDescriptor() throws Exception {
        assertThat(skipArrayDescriptor("[", 0)).isEqualTo(-1);
        assertThat(skipArrayDescriptor("[[", 0)).isEqualTo(-1);
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsConsumerWithWrongDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Consumer", "accept", "(I)V"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsRunnableWithWrongName() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/lang/Runnable", "call", "()V"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsUnknownOwner() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("com/acme/Unknown", "run", "()V"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsSupplierWithWrongNameAndDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Supplier", "fetch", "()Ljava/lang/Object;"))).isFalse();
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Supplier", "get", "()I"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsBooleanSupplierWithWrongNameAndDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/BooleanSupplier", "fetch", "()Z"))).isFalse();
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/BooleanSupplier", "getAsBoolean", "()I"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsPredicateWithWrongNameAndDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Predicate", "accept", "(Ljava/lang/Object;)Z"))).isFalse();
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Predicate", "test", "()Z"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsFunctionWithWrongNameAndDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Function", "map", "(Ljava/lang/Object;)Ljava/lang/Object;"))).isFalse();
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Function", "apply", "()Ljava/lang/Object;"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsBiConsumerWithWrongNameAndDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/BiConsumer", "consume", "(Ljava/lang/Object;Ljava/lang/Object;)V"))).isFalse();
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;)V"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsRunnableWithWrongDescriptor() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/lang/Runnable", "run", "()I"))).isFalse();
    }

    @Test
    void receiverBindingCoversStaticUnsupportedCapturedAndPrimitiveSamCases() throws Exception {
        assertThat(receiverBinding(6, List.of(), List.of())).contains(LambdaMetafactorySupport.ReceiverBinding.NONE);
        assertThat(receiverBinding(7, List.of(), List.of())).isEmpty();
        assertThat(receiverBinding(5, List.of("Ljava/lang/Object;"), List.of(IrType.OBJECT)))
            .contains(LambdaMetafactorySupport.ReceiverBinding.CAPTURE0);
        assertThat(receiverBinding(5, List.of(), List.of(IrType.OBJECT)))
            .contains(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
        assertThat(receiverBinding(5, List.of(), List.of(IrType.INT))).isEmpty();
    }

    @Test
    void sameIrTypesRejectsSizeAndElementMismatch() throws Exception {
        assertThat(sameIrTypes(List.of(IrType.INT), List.of())).isFalse();
        assertThat(sameIrTypes(List.of(IrType.INT), List.of(IrType.OBJECT))).isFalse();
        assertThat(sameIrTypes(List.of(IrType.INT, IrType.OBJECT), List.of(IrType.INT, IrType.OBJECT))).isTrue();
    }

    @Test
    void irTypeCoversAllSupportedPrimitiveFamiliesAndRejectsUnknownDescriptor() throws Exception {
        assertThat(irType("J")).isEqualTo(IrType.LONG);
        assertThat(irType("F")).isEqualTo(IrType.FLOAT);
        assertThat(irType("D")).isEqualTo(IrType.DOUBLE);
        assertThat(irType("Ljava/lang/String;")).isEqualTo(IrType.OBJECT);
        assertThat(irType("[I")).isEqualTo(IrType.OBJECT);
        assertThat(irType("C")).isEqualTo(IrType.INT);
        assertThatThrownBy(() -> irType("Q"))
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported descriptor");
    }

    @Test
    void returnIrTypeRejectsMissingReturnTypeAndMapsVoid() throws Exception {
        assertThat(returnIrType("()V")).isEqualTo(IrType.VOID);
        assertThatThrownBy(() -> returnIrType("()"))
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid descriptor");
    }

    @Test
    void lambdaClosurePlanMatchesSamRejectsNameAndDescriptorMismatch() {
        final LambdaMetafactorySupport.LambdaClosurePlan plan = new LambdaMetafactorySupport.LambdaClosurePlan(
            "com/acme/Synthetic",
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of(),
            new MethodRef("com/acme/Main", "lambda$main$0", "(Ljava/lang/Object;)Ljava/lang/Object;"),
            6,
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            Path.of("com/acme/Main.class"),
            true
        );

        assertThat(plan.matchesSam(new MethodRef("java/util/function/Function", "accept", "(Ljava/lang/Object;)Ljava/lang/Object;"))).isFalse();
        assertThat(plan.matchesSam(new MethodRef("java/util/function/Function", "apply", "()Ljava/lang/Object;"))).isFalse();
    }

    @Test
    void planRejectsBootstrapValuesWithoutMethodHandleFacts() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/lang/Runnable;",
                    invokeDynamic(
                        1,
                        new DynamicRef(
                            "run",
                            "()Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "raw", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_HANDLE, "raw"),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                )
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void planRejectsInterfaceDescriptorWithoutObjectOwner() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()I",
                    invokeDynamic(
                        1,
                        new DynamicRef(
                            "run",
                            "()I",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                ),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void lowerableBridgeTargetsExistCoversVirtualSubtypeSuccessAndFailure() throws Exception {
        final Map<String, ClassFile> success = Map.of(
            "com/acme/Task",
            classWithMethods("com/acme/Task", "java/lang/Object", 0, List.of(), method("run", "()V", plain(0, 177, "return"))),
            "com/acme/Leaf",
            classWithMethods("com/acme/Leaf", "com/acme/Task", 0, List.of())
        );
        final Map<String, ClassFile> failure = Map.of(
            "com/acme/Task",
            classWithMethods("com/acme/Task", "java/lang/Object", 0, List.of()),
            "com/acme/Leaf",
            classWithMethods("com/acme/Leaf", "java/lang/Object", 0, List.of())
        );

        assertThat(lowerableBridgeTargetsExist(success, new MethodRef("com/acme/Task", "run", "()V"), 5)).isTrue();
        assertThat(lowerableBridgeTargetsExist(failure, new MethodRef("com/acme/Task", "run", "()V"), 5)).isFalse();
    }

    @Test
    void returnObjectOwnerRejectsMissingParenAndPrimitiveReturnAndAcceptsObject() throws Exception {
        assertThat(returnObjectOwner("Ljava/lang/String;")).isEmpty();
        assertThat(returnObjectOwner("()I")).isEmpty();
        assertThat(returnObjectOwner("()Ljava/lang/String;")).contains("java/lang/String");
    }

    @Test
    void lowerableResolvedVirtualTargetRejectsAbstractMethodBody() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/RunnableImpl",
            classWithMethods(
                "com/acme/RunnableImpl",
                "java/lang/Runnable",
                0,
                List.of(),
                new MethodInfo(0, "run", "()V", Optional.empty())
            )
        );

        assertThat(lowerableResolvedVirtualTarget(classes, "com/acme/RunnableImpl", new MethodRef("java/lang/Runnable", "run", "()V")))
            .isEmpty();
    }

    @Test
    void lowerableResolvedInterfaceTargetResolvesParentDefaultMethod() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/ParentRunnable",
            classWithMethods(
                "com/acme/ParentRunnable",
                "java/lang/Object",
                0x0200,
                List.of("java/lang/Runnable"),
                method("run", "()V", plain(0, 177, "return"))
            ),
            "com/acme/ChildRunnable",
            classWithMethods("com/acme/ChildRunnable", "java/lang/Object", 0x0200, List.of("com/acme/ParentRunnable")),
            "com/acme/RunnableImpl",
            classWithMethods("com/acme/RunnableImpl", "java/lang/Object", 0, List.of("com/acme/ChildRunnable"))
        );

        assertThat(lowerableResolvedInterfaceTarget(classes, "com/acme/RunnableImpl", new MethodRef("java/lang/Runnable", "run", "()V")))
            .contains(new EntryPoint("com/acme/ParentRunnable", "run", "()V"));
    }

    @Test
    void lowerableResolvedInterfaceTargetSkipsUnassignableInterfaceBeforeMatch() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Noise",
            classWithMethods("com/acme/Noise", "java/lang/Object", 0x0200, List.of()),
            "com/acme/BridgeRunnable",
            classWithMethods(
                "com/acme/BridgeRunnable",
                "java/lang/Object",
                0x0200,
                List.of("java/lang/Runnable"),
                method("run", "()V", plain(0, 177, "return"))
            ),
            "com/acme/RunnableImpl",
            classWithMethods("com/acme/RunnableImpl", "java/lang/Object", 0, List.of("com/acme/Noise", "com/acme/BridgeRunnable"))
        );

        assertThat(lowerableResolvedInterfaceTarget(classes, "com/acme/RunnableImpl", new MethodRef("java/lang/Runnable", "run", "()V")))
            .contains(new EntryPoint("com/acme/BridgeRunnable", "run", "()V"));
    }

    @Test
    void isSubtypeOfTraversesSuperclassChain() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Leaf",
            classWithMethods("com/acme/Leaf", "com/acme/Mid", 0, List.of()),
            "com/acme/Mid",
            classWithMethods("com/acme/Mid", "java/lang/Runnable", 0, List.of())
        );

        assertThat(isSubtypeOf(classes, "com/acme/Leaf", "java/lang/Runnable")).isTrue();
        assertThat(isSubtypeOf(classes, "com/acme/Leaf", "java/lang/Thread")).isFalse();
    }

    @Test
    void implementedInterfacesDeduplicatesTransitiveInterfacesAndStopsAtMissingSuperclass() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/BaseRunnable",
            classWithMethods("com/acme/BaseRunnable", "java/lang/Object", 0x0200, List.of("java/lang/Runnable")),
            "com/acme/ChildRunnable",
            classWithMethods("com/acme/ChildRunnable", "java/lang/Object", 0x0200, List.of("com/acme/BaseRunnable")),
            "com/acme/Impl",
            classWithMethods("com/acme/Impl", "com/acme/Missing", 0, List.of("com/acme/BaseRunnable", "com/acme/ChildRunnable"))
        );

        assertThat(implementedInterfaces(classes, "com/acme/Impl"))
            .containsExactly("com/acme/BaseRunnable", "java/lang/Runnable", "com/acme/ChildRunnable");
    }

    @Test
    void defaultInterfaceTargetRejectsVisitedCyclesAndNonInterfaceDefinitions() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Loop",
            classWithMethods("com/acme/Loop", "java/lang/Object", 0x0200, List.of("com/acme/Loop")),
            "com/acme/Concrete",
            classWithMethods("com/acme/Concrete", "java/lang/Object", 0, List.of())
        );

        assertThat(defaultInterfaceTarget(
            classes,
            "com/acme/Loop",
            new MethodRef("java/lang/Runnable", "run", "()V"),
            new java.util.ArrayList<>(List.of("com/acme/Loop"))
        ))
            .isEmpty();
        assertThat(defaultInterfaceTarget(
            classes,
            "com/acme/Concrete",
            new MethodRef("java/lang/Runnable", "run", "()V"),
            new java.util.ArrayList<>()
        ))
            .isEmpty();
    }

    @Test
    void isAssignableToHandlesMissingExactOwnersAndVisitedSuperclassCycles() throws Exception {
        final Map<String, ClassFile> cyclic = Map.of(
            "com/acme/A",
            classWithMethods("com/acme/A", "com/acme/B", 0, List.of()),
            "com/acme/B",
            classWithMethods("com/acme/B", "com/acme/A", 0, List.of())
        );

        assertThat(isAssignableTo(Map.of(), "com/acme/Missing", "com/acme/Missing")).isTrue();
        assertThat(isAssignableTo(cyclic, "com/acme/A", "java/lang/Runnable")).isFalse();
    }

    private static DynamicRef runnableLambda(
        final String owner,
        final String methodName,
        final String descriptor,
        final int referenceKind
    ) {
        return new DynamicRef(
            "run",
            "()Ljava/lang/Runnable;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("()V", owner + "." + methodName + descriptor, "()V"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                BootstrapValue.methodHandle(owner + "." + methodName + descriptor, new MethodRef(owner, methodName, descriptor), referenceKind),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
            )
        );
    }

    private static DynamicRef capturedStaticRunnableLambda(
        final String owner,
        final String methodName,
        final String descriptor
    ) {
        return new DynamicRef(
            "run",
            "(I)Ljava/lang/Runnable;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("()V", owner + "." + methodName + descriptor, "()V"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                BootstrapValue.methodHandle(owner + "." + methodName + descriptor, new MethodRef(owner, methodName, descriptor), 6),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
            )
        );
    }

    private static DynamicRef capturedVirtualRunnableLambda() {
        return new DynamicRef(
            "run",
            "(Lcom/acme/Greeter;)Ljava/lang/Runnable;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("()V", "com/acme/Greeter.runTask()V", "()V"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                BootstrapValue.methodHandle("com/acme/Greeter.runTask()V", new MethodRef("com/acme/Greeter", "runTask", "()V"), 5),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
            )
        );
    }

    private static DynamicRef consumerVirtualLambda() {
        return new DynamicRef(
            "accept",
            "()Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("(Ljava/lang/Object;)V", "com/acme/Greeter.runTask()V", "(Ljava/lang/Object;)V"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)V"),
                BootstrapValue.methodHandle("com/acme/Greeter.runTask()V", new MethodRef("com/acme/Greeter", "runTask", "()V"), 5),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)V")
            )
        );
    }

    private static DynamicRef supplierBridgeLambda() {
        return new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("(Ljava/lang/Object;)Ljava/lang/Object;", "java/util/function/Supplier.get()Ljava/lang/Object;", "(Ljava/util/function/Supplier;)Ljava/lang/Object;"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapValue.methodHandle(
                    "java/util/function/Supplier.get()Ljava/lang/Object;",
                    new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/function/Supplier;)Ljava/lang/Object;")
            )
        );
    }

    private static DynamicRef booleanSupplierBridgeLambda() {
        return new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("(Ljava/lang/Object;)Z", "java/util/function/BooleanSupplier.getAsBoolean()Z", "(Ljava/util/function/BooleanSupplier;)Z"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Z"),
                BootstrapValue.methodHandle(
                    "java/util/function/BooleanSupplier.getAsBoolean()Z",
                    new MethodRef("java/util/function/BooleanSupplier", "getAsBoolean", "()Z"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/function/BooleanSupplier;)Z")
            )
        );
    }

    private static DynamicRef predicateBridgeLambda() {
        return new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                "java/util/function/Predicate.test(Ljava/lang/Object;)Z",
                "(Ljava/util/function/Predicate;Ljava/lang/Object;)Z"
            ),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                BootstrapValue.methodHandle(
                    "java/util/function/Predicate.test(Ljava/lang/Object;)Z",
                    new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/function/Predicate;Ljava/lang/Object;)Z")
            )
        );
    }

    private static DynamicRef functionBridgeLambda() {
        return new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", "java/util/function/Function.apply(Ljava/lang/Object;)Ljava/lang/Object;", "(Ljava/util/function/Function;Ljava/lang/Object;)Ljava/lang/Object;"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapValue.methodHandle(
                    "java/util/function/Function.apply(Ljava/lang/Object;)Ljava/lang/Object;",
                    new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/function/Function;Ljava/lang/Object;)Ljava/lang/Object;")
            )
        );
    }

    private static DynamicRef biConsumerBridgeLambda() {
        return new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", "java/util/function/BiConsumer.accept(Ljava/lang/Object;Ljava/lang/Object;)V", "(Ljava/util/function/BiConsumer;Ljava/lang/Object;Ljava/lang/Object;)V"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"),
                BootstrapValue.methodHandle(
                    "java/util/function/BiConsumer.accept(Ljava/lang/Object;Ljava/lang/Object;)V",
                    new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/function/BiConsumer;Ljava/lang/Object;Ljava/lang/Object;)V")
            )
        );
    }

    private static DynamicRef runnableBridgeLambda() {
        return new DynamicRef(
            "accept",
            "()Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;)V",
                "java/lang/Runnable.run()V",
                "(Ljava/lang/Runnable;)V"
            ),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)V"),
                BootstrapValue.methodHandle(
                    "java/lang/Runnable.run()V",
                    new MethodRef("java/lang/Runnable", "run", "()V"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Runnable;)V")
            )
        );
    }

    private static Instruction plain(final int offset, final int opcode, final String mnemonic) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction invokeDynamic(final int offset, final DynamicRef dynamicRef) {
        return new Instruction(
            offset,
            186,
            "invokedynamic",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(dynamicRef)
        );
    }

    private static MethodInfo method(final String name, final String descriptor, final Instruction... instructions) {
        return new MethodInfo(
            0,
            name,
            descriptor,
            Optional.of(new CodeAttribute(2, 1, new byte[0], 0, List.of(instructions)))
        );
    }

    private static ClassFile classWithMethods(
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final MethodInfo... methods
    ) {
        return new ClassFile(
            69,
            name,
            superName,
            accessFlags,
            List.copyOf(interfaces),
            List.of(),
            List.of(methods),
            Path.of(name + ".class"),
            true
        );
    }

    @SuppressWarnings("unchecked")
    private static List<String> parameterDescriptors(final String descriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("parameterDescriptors", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, descriptor);
    }

    private static int skipArrayDescriptor(final String descriptor, final int start) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("skipArrayDescriptor", String.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, descriptor, start);
    }

    private static boolean supportedFunctionalBridgeTarget(final MethodRef target) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("supportedFunctionalBridgeTarget", MethodRef.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, target);
    }

    @SuppressWarnings("unchecked")
    private static Optional<EntryPoint> lowerableResolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "lowerableResolvedVirtualTarget",
            Map.class,
            String.class,
            MethodRef.class
        );
        method.setAccessible(true);
        return (Optional<EntryPoint>) method.invoke(null, classes, receiver, target);
    }

    @SuppressWarnings("unchecked")
    private static Optional<EntryPoint> lowerableResolvedInterfaceTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "lowerableResolvedInterfaceTarget",
            Map.class,
            String.class,
            MethodRef.class
        );
        method.setAccessible(true);
        return (Optional<EntryPoint>) method.invoke(null, classes, receiver, target);
    }

    private static boolean isSubtypeOf(
        final Map<String, ClassFile> classes,
        final String candidate,
        final String expectedSuper
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "isSubtypeOf",
            Map.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, classes, candidate, expectedSuper);
    }

    @SuppressWarnings("unchecked")
    private static List<String> implementedInterfaces(final Map<String, ClassFile> classes, final String receiver) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "implementedInterfaces",
            Map.class,
            String.class
        );
        method.setAccessible(true);
        return (List<String>) method.invoke(null, classes, receiver);
    }

    @SuppressWarnings("unchecked")
    private static Optional<EntryPoint> defaultInterfaceTarget(
        final Map<String, ClassFile> classes,
        final String interfaceName,
        final MethodRef target,
        final List<String> visited
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "defaultInterfaceTarget",
            Map.class,
            String.class,
            MethodRef.class,
            List.class
        );
        method.setAccessible(true);
        return (Optional<EntryPoint>) method.invoke(null, classes, interfaceName, target, visited);
    }

    private static boolean isAssignableTo(
        final Map<String, ClassFile> classes,
        final String candidate,
        final String expected
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "isAssignableTo",
            Map.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, classes, candidate, expected);
    }

    private static boolean lowerableBridgeTargetsExist(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final int referenceKind
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "lowerableBridgeTargetsExist",
            Map.class,
            MethodRef.class,
            int.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, classes, target, referenceKind);
    }

    @SuppressWarnings("unchecked")
    private static Optional<LambdaMetafactorySupport.ReceiverBinding> receiverBinding(
        final int referenceKind,
        final List<String> captureDescriptors,
        final List<IrType> samParameters
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "receiverBinding",
            int.class,
            List.class,
            List.class
        );
        method.setAccessible(true);
        return (Optional<LambdaMetafactorySupport.ReceiverBinding>) method.invoke(
            null,
            referenceKind,
            captureDescriptors,
            samParameters
        );
    }

    private static boolean sameIrTypes(final List<IrType> left, final List<IrType> right) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("sameIrTypes", List.class, List.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, left, right);
    }

    private static IrType irType(final String descriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("irType", String.class);
        method.setAccessible(true);
        return (IrType) method.invoke(null, descriptor);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> returnObjectOwner(final String descriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("returnObjectOwner", String.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(null, descriptor);
    }

    private static IrType returnIrType(final String descriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("returnIrType", String.class);
        method.setAccessible(true);
        return (IrType) method.invoke(null, descriptor);
    }
}
