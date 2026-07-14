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
    void scanSkipsMethodsWithoutCodeAttribute() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                new MethodInfo(0, "main", "([Ljava/lang/String;)V", Optional.empty())
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanSkipsInstructionsThatAreNotInvokeDynamic() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "([Ljava/lang/String;)V", plain(0, 177, "return")),
                method("lambda$main$0", "()V")
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanSkipsInvokeDynamicInstructionWithoutDynamicReference() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "([Ljava/lang/String;)V", plain(0, 186, "invokedynamic"))
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRespectsExactScopedMethodMatch() {
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
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(registry.planForSite("com/acme/Main", "main", "([Ljava/lang/String;)V", 7)).isPresent();
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
    void scanCreatesPlanForStaticJdkValueOfBridge() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Function;",
                    invokeDynamic(7, new DynamicRef(
                        "apply",
                        "()Ljava/util/function/Function;",
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        List.of("(Ljava/lang/Object;)Ljava/lang/Object;", "java/lang/Integer.valueOf(I)Ljava/lang/Integer;", "(Ljava/lang/Integer;)Ljava/lang/Integer;"),
                        List.of(
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Ljava/lang/Object;"),
                            BootstrapValue.methodHandle(
                                "java/lang/Integer.valueOf(I)Ljava/lang/Integer;",
                                new MethodRef("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"),
                                6
                            ),
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Integer;)Ljava/lang/Integer;")
                        )
                    ))
                )
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 7)
            .orElseThrow();

        assertThat(plan.methodDescriptor()).isEqualTo("(Ljava/lang/Object;)Ljava/lang/Object;");
        assertThat(plan.instantiatedMethodDescriptor()).isEqualTo("(Ljava/lang/Integer;)Ljava/lang/Integer;");
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.NONE);
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
    void scanCreatesPlanForCapturedInvokeSpecialReceiver() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/TypeList",
            classWithMethods(
                "com/acme/TypeList",
                "java/lang/Object",
                0,
                List.of(),
                method("<init>", "(Ljava/util/Collection;)V", invokeDynamic(7, capturedInvokeSpecialConsumerLambda())),
                method("lambda$new$0", "(Ljava/util/Collection;)V")
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/TypeList", "<init>", "(Ljava/util/Collection;)V", 7)
            .orElseThrow();

        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.CAPTURE0);
        assertThat(plan.captureDescriptors()).containsExactly("Lcom/acme/TypeList;");
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("com/acme/TypeList", "lambda$new$0", "(Ljava/util/Collection;)V"));
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
    void scanCreatesPlanForFirstParameterJdkNumberBridge() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Function;",
                    invokeDynamic(11, new DynamicRef(
                        "apply",
                        "()Ljava/util/function/Function;",
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        List.of("(Ljava/lang/Object;)Ljava/lang/Object;", "java/lang/Number.intValue()I", "(Ljava/lang/Number;)Ljava/lang/Integer;"),
                        List.of(
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Ljava/lang/Object;"),
                            BootstrapValue.methodHandle(
                                "java/lang/Number.intValue()I",
                                new MethodRef("java/lang/Number", "intValue", "()I"),
                                5
                            ),
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Number;)Ljava/lang/Integer;")
                        )
                    ))
                )
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 11)
            .orElseThrow();

        assertThat(plan.instantiatedMethodDescriptor()).isEqualTo("(Ljava/lang/Number;)Ljava/lang/Integer;");
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/lang/Number", "intValue", "()I"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanCreatesPlanForJdkConstructorBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Function;",
                    invokeDynamic(11, new DynamicRef(
                        "apply",
                        "()Ljava/util/function/Function;",
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        List.of(
                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                            "java/lang/StringBuilder.<init>(Ljava/lang/String;)V",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                        ),
                        List.of(
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Ljava/lang/Object;"),
                            BootstrapValue.methodHandle(
                                "java/lang/StringBuilder.<init>(Ljava/lang/String;)V",
                                new MethodRef("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"),
                                8
                            ),
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                        )
                    ))
                )
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 11)
            .orElseThrow();

        assertThat(plan.instantiatedMethodDescriptor()).isEqualTo("(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"));
        assertThat(plan.implementationReferenceKind()).isEqualTo(8);
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.NONE);
    }

    @Test
    void scanCreatesPlanForApplicationConstructorBridgeTarget() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Function;",
                    invokeDynamic(11, new DynamicRef(
                        "apply",
                        "()Ljava/util/function/Function;",
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        List.of(
                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                            "com/acme/Box.<init>(Ljava/lang/String;)V",
                            "(Ljava/lang/String;)Lcom/acme/Box;"
                        ),
                        List.of(
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Ljava/lang/Object;"),
                            BootstrapValue.methodHandle(
                                "com/acme/Box.<init>(Ljava/lang/String;)V",
                                new MethodRef("com/acme/Box", "<init>", "(Ljava/lang/String;)V"),
                                8
                            ),
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/String;)Lcom/acme/Box;")
                        )
                    ))
                )
            ),
            "com/acme/Box",
            classWithMethods(
                "com/acme/Box",
                "java/lang/Object",
                0,
                List.of(),
                method("<init>", "(Ljava/lang/String;)V")
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Function;", 11)
            .orElseThrow();

        assertThat(plan.instantiatedMethodDescriptor()).isEqualTo("(Ljava/lang/String;)Lcom/acme/Box;");
        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("com/acme/Box", "<init>", "(Ljava/lang/String;)V"));
        assertThat(plan.implementationReferenceKind()).isEqualTo(8);
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.NONE);
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
    void scanCreatesPlanForStaticObjectsNonNullPredicateBridge() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Predicate;",
                    invokeDynamic(6, new DynamicRef(
                        "test",
                        "()Ljava/util/function/Predicate;",
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        List.of("(Ljava/lang/Object;)Z", "java/util/Objects.nonNull(Ljava/lang/Object;)Z", "(Ljava/lang/Object;)Z"),
                        List.of(
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Z"),
                            BootstrapValue.methodHandle(
                                "java/util/Objects.nonNull(Ljava/lang/Object;)Z",
                                new MethodRef("java/util/Objects", "nonNull", "(Ljava/lang/Object;)Z"),
                                6
                            ),
                            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Z")
                        )
                    ))
                )
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Predicate;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/Objects", "nonNull", "(Ljava/lang/Object;)Z"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.NONE);
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
    void scanCreatesPlanForJdkConsumerBridgeThatDiscardsBooleanReturn() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Consumer;", invokeDynamic(6, consumerCollectionAddDiscardReturnLambda()))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Consumer;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("java/util/Collection", "add", "(Ljava/lang/Object;)Z"));
        assertThat(plan.receiverBinding()).isEqualTo(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
    }

    @Test
    void scanCreatesPlanForApplicationConsumerBridgeThatDiscardsObjectReturn() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method("main", "()Ljava/util/function/Consumer;", invokeDynamic(6, consumerFluentDiscardReturnLambda()))
            ),
            "com/acme/Recorder",
            classWithMethods(
                "com/acme/Recorder",
                "java/lang/Object",
                0,
                List.of(),
                method("record", "(Ljava/lang/String;)Lcom/acme/Recorder;", plain(0, 42, "aload_0"), plain(1, 176, "areturn"))
            )
        );

        final LambdaMetafactorySupport.LambdaClosurePlan plan = LambdaMetafactorySupport.scan(classes)
            .planForSite("com/acme/Main", "main", "()Ljava/util/function/Consumer;", 6)
            .orElseThrow();

        assertThat(plan.implementationTarget()).isEqualTo(new MethodRef("com/acme/Recorder", "record", "(Ljava/lang/String;)Lcom/acme/Recorder;"));
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
    void scanRejectsBootstrapWhenInstantiatedSamIsNotMethodType() {
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
                            List.of("()V", "com/acme/Main.lambda$main$0()V", "raw"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.STRING, "raw")
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
    void scanRejectsBootstrapWhenImplementationIsNotMethodHandle() {
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
                            List.of("()V", "raw", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                new BootstrapValue(BootstrapValue.Kind.STRING, "raw"),
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
    void scanRejectsBootstrapWhenMethodHandleReferenceKindIsMissing() {
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
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                new BootstrapValue(
                                    BootstrapValue.Kind.METHOD_HANDLE,
                                    "com/acme/Main.lambda$main$0()V",
                                    Optional.of(new MethodRef("com/acme/Main", "lambda$main$0", "()V")),
                                    Optional.empty()
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
    void scanRejectsFunctionalBridgeSamShapeMismatchAfterReceiverBinding() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/BiConsumer;",
                    invokeDynamic(
                        6,
                        new DynamicRef(
                            "accept",
                            "()Ljava/util/function/BiConsumer;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of(
                                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                "java/util/function/BiConsumer.accept(Ljava/lang/Object;Ljava/lang/Object;)V",
                                "(Ljava/lang/Object;I)V"
                            ),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;Ljava/lang/Object;)V"),
                                BootstrapValue.methodHandle(
                                    "java/util/function/BiConsumer.accept(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"),
                                    9
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;I)V")
                            )
                        )
                    )
                )
            ),
            "com/acme/BiConsumerImpl",
            classWithMethods(
                "com/acme/BiConsumerImpl",
                "java/lang/Object",
                0,
                List.of("java/util/function/BiConsumer"),
                method("accept", "(Ljava/lang/Object;Ljava/lang/Object;)V", plain(0, 177, "return"))
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsFunctionalBridgeWhenExtraCapturedArgumentDoesNotMatchImplementation() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "(Ljava/lang/Runnable;I)Ljava/lang/Runnable;",
                    invokeDynamic(
                        6,
                        new DynamicRef(
                            "run",
                            "(Ljava/lang/Runnable;I)Ljava/lang/Runnable;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of("()V", "java/lang/Runnable.run()V", "()V"),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                                BootstrapValue.methodHandle(
                                    "java/lang/Runnable.run()V",
                                    new MethodRef("java/lang/Runnable", "run", "()V"),
                                    9
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
                            )
                        )
                    )
                )
            ),
            "com/acme/RunnableImpl",
            classWithMethods(
                "com/acme/RunnableImpl",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                method("run", "()V", plain(0, 177, "return"))
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsFunctionalBridgeReturnMismatchAfterSuccessfulTargetResolution() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "()Ljava/util/function/Function;",
                    invokeDynamic(
                        6,
                        new DynamicRef(
                            "apply",
                            "()Ljava/util/function/Function;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of(
                                "(Ljava/lang/Object;)Ljava/lang/Object;",
                                "java/util/function/Function.apply(Ljava/lang/Object;)Ljava/lang/Object;",
                                "(Ljava/lang/Object;)I"
                            ),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)Ljava/lang/Object;"),
                                BootstrapValue.methodHandle(
                                    "java/util/function/Function.apply(Ljava/lang/Object;)Ljava/lang/Object;",
                                    new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                                    9
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)I")
                            )
                        )
                    )
                )
            ),
            "com/acme/FunctionImpl",
            classWithMethods(
                "com/acme/FunctionImpl",
                "java/lang/Object",
                0,
                List.of("java/util/function/Function"),
                method(
                    "apply",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    plain(0, 1, "aconst_null"),
                    plain(1, 176, "areturn")
                )
            )
        );

        assertThat(LambdaMetafactorySupport.scan(classes).bySite()).isEmpty();
    }

    @Test
    void scanRejectsBooleanSupplierBridgeReturnMismatchAfterSuccessfulTargetResolution() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                method(
                    "main",
                    "(Ljava/util/function/BooleanSupplier;)Ljava/util/function/Supplier;",
                    invokeDynamic(
                        6,
                        new DynamicRef(
                            "get",
                            "(Ljava/util/function/BooleanSupplier;)Ljava/util/function/Supplier;",
                            "java/lang/invoke/LambdaMetafactory",
                            "metafactory",
                            "",
                            List.of(
                                "()Ljava/lang/Object;",
                                "java/util/function/BooleanSupplier.getAsBoolean()Z",
                                "()Ljava/lang/Object;"
                            ),
                            List.of(
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()Ljava/lang/Object;"),
                                BootstrapValue.methodHandle(
                                    "java/util/function/BooleanSupplier.getAsBoolean()Z",
                                    new MethodRef("java/util/function/BooleanSupplier", "getAsBoolean", "()Z"),
                                    9
                                ),
                                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()Ljava/lang/Object;")
                            )
                        )
                    )
                )
            ),
            "com/acme/FlagSupplier",
            classWithMethods(
                "com/acme/FlagSupplier",
                "java/lang/Object",
                0,
                List.of("java/util/function/BooleanSupplier"),
                method("getAsBoolean", "()Z", plain(0, 4, "iconst_1"), plain(1, 172, "ireturn"))
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
    void scanRejectsBootstrapShapeShorterThanMetafactoryContract() {
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
    void scanRejectsBootstrapValuesWithWrongKinds() {
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
                                new BootstrapValue(BootstrapValue.Kind.STRING, "()V"),
                                BootstrapValue.methodHandle(
                                    "com/acme/Main.lambda$main$0()V",
                                    new MethodRef("com/acme/Main", "lambda$main$0", "()V"),
                                    6
                                ),
                                new BootstrapValue(BootstrapValue.Kind.STRING, "()V")
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
    void parameterDescriptorsRejectUnknownParameterTag() throws Exception {
        assertThat(parameterDescriptors("(Q)V")).isEmpty();
    }

    @Test
    void parameterDescriptorsRejectDescriptorWithoutOpeningParen() throws Exception {
        assertThat(parameterDescriptors("I)V")).isEmpty();
    }

    @Test
    void parameterDescriptorsRejectMalformedObjectDescriptor() throws Exception {
        assertThat(parameterDescriptors("(Ljava/lang/String)V")).isEmpty();
    }

    @Test
    void parameterDescriptorsRejectMalformedArrayObjectDescriptor() throws Exception {
        assertThat(parameterDescriptors("([Ljava/lang/String)V")).isEmpty();
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
    void supportedFunctionalBridgeTargetAcceptsConsumerBridgeTarget() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"))).isTrue();
    }

    @Test
    void supportedFunctionalBridgeTargetRejectsConsumerWithWrongName() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/util/function/Consumer", "consume", "(Ljava/lang/Object;)V"))).isFalse();
    }

    @Test
    void supportedFunctionalBridgeTargetAcceptsRunnableBridgeTarget() throws Exception {
        assertThat(supportedFunctionalBridgeTarget(new MethodRef("java/lang/Runnable", "run", "()V"))).isTrue();
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
        assertThat(receiverBinding(8, List.of(), List.of())).contains(LambdaMetafactorySupport.ReceiverBinding.NONE);
        assertThat(receiverBinding(7, List.of("Ljava/lang/Object;"), List.of(IrType.OBJECT)))
            .contains(LambdaMetafactorySupport.ReceiverBinding.CAPTURE0);
        assertThat(receiverBinding(7, List.of(), List.of(IrType.OBJECT)))
            .contains(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
        assertThat(receiverBinding(5, List.of("Ljava/lang/Object;"), List.of(IrType.OBJECT)))
            .contains(LambdaMetafactorySupport.ReceiverBinding.CAPTURE0);
        assertThat(receiverBinding(5, List.of(), List.of(IrType.OBJECT)))
            .contains(LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER);
        assertThat(receiverBinding(5, List.of(), List.of(IrType.INT))).isEmpty();
        assertThat(receiverBinding(7, List.of(), List.of(IrType.INT))).isEmpty();
    }

    @Test
    void supportedJdkBridgeTargetRejectsParameterMismatch() throws Exception {
        assertThat(supportedJdkBridgeTarget(
            6,
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            List.of(),
            new MethodRef("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"),
            "(Ljava/lang/Long;)Ljava/lang/Integer;"
        )).isFalse();
    }

    @Test
    void supportedJdkBridgeTargetRejectsReturnMismatch() throws Exception {
        assertThat(supportedJdkBridgeTarget(
            6,
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            List.of(),
            new MethodRef("java/lang/Integer", "intValue", "()I"),
            "()Ljava/lang/Long;"
        )).isFalse();
    }

    @Test
    void supportedJdkBridgeTargetRejectsConstructorReturnMismatch() throws Exception {
        assertThat(supportedJdkBridgeTarget(
            8,
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            List.of(),
            new MethodRef("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"),
            "(Ljava/lang/String;)I"
        )).isFalse();
    }

    @Test
    void jdkBridgeParametersMatchRejectsEmptyCaptureReceiver() throws Exception {
        assertThat(jdkBridgeParametersMatch(
            LambdaMetafactorySupport.ReceiverBinding.CAPTURE0,
            List.of(),
            List.of(),
            List.of("Ljava/lang/Object;")
        )).isFalse();
    }

    @Test
    void jdkBridgeParametersMatchRejectsEmptyFirstParameterReceiver() throws Exception {
        assertThat(jdkBridgeParametersMatch(
            LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER,
            List.of(),
            List.of(),
            List.of("Ljava/lang/Object;")
        )).isFalse();
    }

    @Test
    void jdkBridgeParametersMatchRejectsSizeMismatch() throws Exception {
        assertThat(jdkBridgeParametersMatch(
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            List.of("Ljava/lang/String;"),
            List.of(),
            List.of("Ljava/lang/String;", "Ljava/lang/String;")
        )).isFalse();
    }

    @Test
    void jdkBridgeParametersMatchRejectsDescriptorMismatch() throws Exception {
        assertThat(jdkBridgeParametersMatch(
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            List.of("Ljava/lang/String;"),
            List.of(),
            List.of("Q")
        )).isFalse();
    }

    @Test
    void jdkBridgeParameterMatchesRejectsNonPrimitiveImplementationDescriptor() throws Exception {
        assertThat(jdkBridgeParameterMatches("I", "Q")).isFalse();
    }

    @Test
    void boxedPrimitiveMatchesRejectsWideDescriptor() throws Exception {
        assertThat(boxedPrimitiveMatches("Ljava/lang/Integer;", "II")).isFalse();
    }

    @Test
    void boxedPrimitiveMatchesRejectsUnknownPrimitiveDescriptor() throws Exception {
        assertThat(boxedPrimitiveMatches("Ljava/lang/Integer;", "B")).isFalse();
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
    void returnIrTypeRejectsDescriptorWithoutClosingParen() {
        assertThatThrownBy(() -> returnIrType("I"))
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid descriptor");
    }

    @Test
    void returnDescriptorRejectsMissingReturnType() {
        assertThatThrownBy(() -> returnDescriptor("()"))
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid descriptor");
    }

    @Test
    void returnDescriptorRejectsDescriptorWithoutClosingParen() {
        assertThatThrownBy(() -> returnDescriptor("I"))
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid descriptor");
    }

    @Test
    void isObjectDescriptorRejectsPrimitiveAndAcceptsArray() throws Exception {
        assertThat(isObjectDescriptor("I")).isFalse();
        assertThat(isObjectDescriptor("[I")).isTrue();
    }

    @Test
    void lambdaClosurePlanMatchesSamRejectsNameAndDescriptorMismatch() {
        final LambdaMetafactorySupport.LambdaClosurePlan plan = new LambdaMetafactorySupport.LambdaClosurePlan(
            "com/acme/Synthetic",
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
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
    void lambdaClosurePlanMatchesSamRejectsOwnerMismatch() {
        final LambdaMetafactorySupport.LambdaClosurePlan plan = new LambdaMetafactorySupport.LambdaClosurePlan(
            "com/acme/Synthetic",
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of(),
            new MethodRef("com/acme/Main", "lambda$main$0", "(Ljava/lang/Object;)Ljava/lang/Object;"),
            6,
            LambdaMetafactorySupport.ReceiverBinding.NONE,
            Path.of("com/acme/Main.class"),
            true
        );

        assertThat(plan.matchesSam(new MethodRef("java/util/function/Supplier", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"))).isFalse();
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
    void returnObjectOwnerRejectsDescriptorWithoutReturnToken() throws Exception {
        assertThat(returnObjectOwner("()")).isEmpty();
    }

    @Test
    void parameterDescriptorsRejectDescriptorWithoutClosingParen() throws Exception {
        assertThat(parameterDescriptors("(I")).isEmpty();
    }

    @Test
    void skipArrayDescriptorRejectsArrayWithUnknownLeafTag() throws Exception {
        assertThat(skipArrayDescriptor("[Q", 0)).isEqualTo(-1);
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
    void lowerableResolvedVirtualTargetRejectsMissingReceiverDefinition() throws Exception {
        assertThat(lowerableResolvedVirtualTarget(Map.of(), "com/acme/Missing", new MethodRef("java/lang/Runnable", "run", "()V")))
            .isEmpty();
    }

    @Test
    void lowerableResolvedInvokeVirtualTargetResolvesInheritedDefaultMethodOnConcreteOwner() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/TypeInfo",
            classWithMethods(
                "com/acme/TypeInfo",
                "java/lang/Object",
                0x0200,
                List.of(),
                method("isPresent", "([Ljava/lang/Object;)Z", plain(0, 4, "iconst_1"), plain(1, 172, "ireturn"))
            ),
            "com/acme/Type",
            classWithMethods("com/acme/Type", "java/lang/Object", 0, List.of("com/acme/TypeInfo"))
        );

        assertThat(lowerableResolvedInvokeVirtualTarget(classes, "com/acme/Type", new MethodRef("com/acme/Type", "isPresent", "([Ljava/lang/Object;)Z")))
            .contains(new EntryPoint("com/acme/TypeInfo", "isPresent", "([Ljava/lang/Object;)Z"));
    }

    @Test
    void lowerableResolvedInvokeVirtualTargetSkipsLessSpecificInterfaceWithoutDefaultMatch() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/General",
            classWithMethods("com/acme/General", "java/lang/Object", 0x0200, List.of()),
            "com/acme/Specific",
            classWithMethods("com/acme/Specific", "java/lang/Object", 0x0200, List.of("com/acme/General")),
            "com/acme/Type",
            classWithMethods("com/acme/Type", "java/lang/Object", 0, List.of("com/acme/Specific", "com/acme/General"))
        );

        assertThat(lowerableResolvedInvokeVirtualTarget(
            classes,
            "com/acme/Type",
            new MethodRef("java/lang/Runnable", "run", "()V")
        )).isEmpty();
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
    void lowerableMethodTargetRejectsMissingOwnerAndMethodWithoutCode() throws Exception {
        assertThat(lowerableMethodTarget(Map.of(), new EntryPoint("com/acme/Missing", "run", "()V"))).isEmpty();

        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Owner",
            classWithMethods("com/acme/Owner", "java/lang/Object", 0, List.of(), new MethodInfo(0, "run", "()V", Optional.empty()))
        );

        assertThat(lowerableMethodTarget(classes, new EntryPoint("com/acme/Owner", "run", "()V"))).isEmpty();
    }

    @Test
    void lowerableResolvedInterfaceTargetRejectsAbstractChildRedeclarationOfParentDefault() throws Exception {
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
            classWithMethods(
                "com/acme/ChildRunnable",
                "java/lang/Object",
                0x0200,
                List.of("com/acme/ParentRunnable"),
                new MethodInfo(0, "run", "()V", Optional.empty())
            ),
            "com/acme/RunnableImpl",
            classWithMethods("com/acme/RunnableImpl", "java/lang/Object", 0, List.of("com/acme/ChildRunnable"))
        );

        assertThat(lowerableResolvedInterfaceTarget(classes, "com/acme/RunnableImpl", new MethodRef("java/lang/Runnable", "run", "()V")))
            .isEmpty();
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
    void lowerableResolvedInterfaceTargetRejectsUnassignableInterfacesWithoutMatch() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Noise",
            classWithMethods("com/acme/Noise", "java/lang/Object", 0x0200, List.of()),
            "com/acme/RunnableImpl",
            classWithMethods("com/acme/RunnableImpl", "java/lang/Object", 0, List.of("com/acme/Noise"))
        );

        assertThat(lowerableResolvedInterfaceTarget(classes, "com/acme/RunnableImpl", new MethodRef("java/lang/Runnable", "run", "()V")))
            .isEmpty();
    }

    @Test
    void lowerableBridgeTargetsExistResolvesInterfaceImplementation() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/SupplierImpl",
            classWithMethods(
                "com/acme/SupplierImpl",
                "java/lang/Object",
                0,
                List.of("java/util/function/Supplier"),
                method("get", "()Ljava/lang/Object;", plain(0, 1, "aconst_null"), plain(1, 176, "areturn"))
            )
        );

        assertThat(lowerableBridgeTargetsExist(classes, new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"), 9)).isTrue();
    }

    @Test
    void lowerableBridgeTargetsExistRejectsInterfaceImplementationWithoutLowerableTarget() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/SupplierImpl",
            classWithMethods(
                "com/acme/SupplierImpl",
                "java/lang/Object",
                0,
                List.of("java/util/function/Supplier"),
                new MethodInfo(0, "get", "()Ljava/lang/Object;", Optional.empty())
            )
        );

        assertThat(lowerableBridgeTargetsExist(classes, new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"), 9)).isFalse();
    }

    @Test
    void lowerableBridgeTargetsExistRejectsVirtualSubtypeWithoutLowerableBody() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Task",
            classWithMethods("com/acme/Task", "java/lang/Object", 0, List.of(), new MethodInfo(0, "run", "()V", Optional.empty())),
            "com/acme/Leaf",
            classWithMethods("com/acme/Leaf", "com/acme/Task", 0, List.of())
        );

        assertThat(lowerableBridgeTargetsExist(classes, new MethodRef("com/acme/Task", "run", "()V"), 5)).isFalse();
    }

    @Test
    void supportedBridgeTargetAcceptsResolvableInterfaceImplementation() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/SupplierImpl",
            classWithMethods(
                "com/acme/SupplierImpl",
                "java/lang/Object",
                0,
                List.of("java/util/function/Supplier"),
                method("get", "()Ljava/lang/Object;", plain(0, 1, "aconst_null"), plain(1, 176, "areturn"))
            )
        );

        assertThat(supportedBridgeTarget(
            classes,
            new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"),
            9
        )).isTrue();
    }

    @Test
    void supportedBridgeTargetRejectsUnsupportedFunctionalOwnerWithoutSearchingLowerableTargets() throws Exception {
        assertThat(supportedBridgeTarget(
            Map.of(),
            new MethodRef("com/acme/Unknown", "run", "()V"),
            6
        )).isFalse();
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
    void implementedInterfacesReturnsEmptyWhenReceiverDefinitionIsMissing() throws Exception {
        assertThat(implementedInterfaces(Map.of(), "com/acme/Missing")).isEmpty();
    }

    @Test
    void implementedInterfacesReturnsEmptyWhenReceiverNameIsEmpty() throws Exception {
        assertThat(implementedInterfaces(Map.of(), "")).isEmpty();
    }

    @Test
    void implementedInterfacesReturnsEmptyWhenReceiverNameIsNull() throws Exception {
        assertThat(implementedInterfaces(Map.of(), null)).isEmpty();
    }

    @Test
    void implementedInterfacesAvoidsDuplicateEntriesFromSuperclassAndDirectDeclaration() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/BaseRunnable",
            classWithMethods("com/acme/BaseRunnable", "java/lang/Object", 0x0200, List.of()),
            "com/acme/Base",
            classWithMethods("com/acme/Base", "java/lang/Object", 0, List.of("com/acme/BaseRunnable")),
            "com/acme/Impl",
            classWithMethods("com/acme/Impl", "com/acme/Base", 0, List.of("com/acme/BaseRunnable"))
        );

        assertThat(implementedInterfaces(classes, "com/acme/Impl"))
            .containsExactly("com/acme/BaseRunnable");
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
    void defaultInterfaceTargetRejectsMissingInterfaceDefinition() throws Exception {
        assertThat(defaultInterfaceTarget(
            Map.of(),
            "com/acme/Missing",
            new MethodRef("java/lang/Runnable", "run", "()V"),
            new java.util.ArrayList<>()
        )).isEmpty();
    }

    @Test
    void defaultInterfaceTargetReturnsDirectDefaultMethod() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/BridgeRunnable",
            classWithMethods(
                "com/acme/BridgeRunnable",
                "java/lang/Object",
                0x0200,
                List.of("java/lang/Runnable"),
                method("run", "()V", plain(0, 177, "return"))
            )
        );

        assertThat(defaultInterfaceTarget(
            classes,
            "com/acme/BridgeRunnable",
            new MethodRef("java/lang/Runnable", "run", "()V"),
            new java.util.ArrayList<>()
        ))
            .contains(new EntryPoint("com/acme/BridgeRunnable", "run", "()V"));
    }

    @Test
    void lowerableMethodTargetRejectsMissingMethodOnExistingOwner() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Owner",
            classWithMethods("com/acme/Owner", "java/lang/Object", 0, List.of(), method("other", "()V", plain(0, 177, "return")))
        );

        assertThat(lowerableMethodTarget(classes, new EntryPoint("com/acme/Owner", "run", "()V"))).isEmpty();
    }

    @Test
    void isAssignableToRejectsNullCandidateAndBreaksInterfaceCycles() throws Exception {
        assertThat(isAssignableTo(Map.of(), null, "java/lang/Runnable")).isFalse();

        final Map<String, ClassFile> classes = Map.of(
            "com/acme/LoopA",
            classWithMethods("com/acme/LoopA", "java/lang/Object", 0x0200, List.of("com/acme/LoopB")),
            "com/acme/LoopB",
            classWithMethods("com/acme/LoopB", "java/lang/Object", 0x0200, List.of("com/acme/LoopA")),
            "com/acme/Impl",
            classWithMethods("com/acme/Impl", "java/lang/Object", 0, List.of("com/acme/LoopA"))
        );

        assertThat(isAssignableTo(classes, "com/acme/Impl", "java/lang/Runnable")).isFalse();
    }

    @Test
    void defaultInterfaceTargetReturnsEmptyWhenParentInterfacesDoNotDefineTarget() throws Exception {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/ParentRunnable",
            classWithMethods("com/acme/ParentRunnable", "java/lang/Object", 0x0200, List.of()),
            "com/acme/ChildRunnable",
            classWithMethods("com/acme/ChildRunnable", "java/lang/Object", 0x0200, List.of("com/acme/ParentRunnable"))
        );

        assertThat(defaultInterfaceTarget(
            classes,
            "com/acme/ChildRunnable",
            new MethodRef("java/lang/Runnable", "run", "()V"),
            new java.util.ArrayList<>()
        )).isEmpty();
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

    @Test
    void isAssignableToReturnsFalseWhenCandidateNameIsEmpty() throws Exception {
        assertThat(isAssignableTo(Map.of(), "", "java/lang/Runnable")).isFalse();
    }

    @Test
    void implementationMatchesRejectsMissingCapturedReceiverValue() throws Exception {
        assertThat(implementationMatches(
            LambdaMetafactorySupport.ReceiverBinding.CAPTURE0,
            List.of(),
            List.of(),
            List.of()
        )).isFalse();
    }

    @Test
    void implementationMatchesRejectsMissingFirstSamParameter() throws Exception {
        assertThat(implementationMatches(
            LambdaMetafactorySupport.ReceiverBinding.FIRST_PARAMETER,
            List.of(),
            List.of(),
            List.of()
        )).isFalse();
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

    private static DynamicRef capturedInvokeSpecialConsumerLambda() {
        return new DynamicRef(
            "accept",
            "(Lcom/acme/TypeList;)Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of("(Ljava/lang/Object;)V", "com/acme/TypeList.lambda$new$0(Ljava/util/Collection;)V", "(Ljava/util/Collection;)V"),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;)V"),
                BootstrapValue.methodHandle(
                    "com/acme/TypeList.lambda$new$0(Ljava/util/Collection;)V",
                    new MethodRef("com/acme/TypeList", "lambda$new$0", "(Ljava/util/Collection;)V"),
                    7
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/Collection;)V")
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

    private static DynamicRef consumerCollectionAddDiscardReturnLambda() {
        return new DynamicRef(
            "accept",
            "()Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                "java/util/Collection.add(Ljava/lang/Object;)Z",
                "(Ljava/util/Collection;Ljava/lang/String;)V"
            ),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;Ljava/lang/Object;)V"),
                BootstrapValue.methodHandle(
                    "java/util/Collection.add(Ljava/lang/Object;)Z",
                    new MethodRef("java/util/Collection", "add", "(Ljava/lang/Object;)Z"),
                    9
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/util/Collection;Ljava/lang/String;)V")
            )
        );
    }

    private static DynamicRef consumerFluentDiscardReturnLambda() {
        return new DynamicRef(
            "accept",
            "()Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                "com/acme/Recorder.record(Ljava/lang/String;)Lcom/acme/Recorder;",
                "(Lcom/acme/Recorder;Ljava/lang/String;)V"
            ),
            List.of(
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Ljava/lang/Object;Ljava/lang/Object;)V"),
                BootstrapValue.methodHandle(
                    "com/acme/Recorder.record(Ljava/lang/String;)Lcom/acme/Recorder;",
                    new MethodRef("com/acme/Recorder", "record", "(Ljava/lang/String;)Lcom/acme/Recorder;"),
                    5
                ),
                new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "(Lcom/acme/Recorder;Ljava/lang/String;)V")
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
    private static Optional<EntryPoint> lowerableResolvedInvokeVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "lowerableResolvedInvokeVirtualTarget",
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

    @SuppressWarnings("unchecked")
    private static Optional<EntryPoint> lowerableMethodTarget(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "lowerableMethodTarget",
            Map.class,
            EntryPoint.class
        );
        method.setAccessible(true);
        return (Optional<EntryPoint>) method.invoke(null, classes, entryPoint);
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

    private static boolean supportedBridgeTarget(
        final Map<String, ClassFile> classes,
        final MethodRef implementationTarget,
        final int referenceKind
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "supportedBridgeTarget",
            Map.class,
            MethodRef.class,
            int.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, classes, implementationTarget, referenceKind);
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

    private static String returnDescriptor(final String descriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("returnDescriptor", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, descriptor);
    }

    private static boolean implementationMatches(
        final LambdaMetafactorySupport.ReceiverBinding receiverBinding,
        final List<String> captureDescriptors,
        final List<IrType> implementationParameters,
        final List<IrType> samParameters
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "implementationMatches",
            LambdaMetafactorySupport.ReceiverBinding.class,
            List.class,
            List.class,
            List.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, receiverBinding, captureDescriptors, implementationParameters, samParameters);
    }

    private static boolean supportedJdkBridgeTarget(
        final int referenceKind,
        final LambdaMetafactorySupport.ReceiverBinding receiverBinding,
        final List<String> captureDescriptors,
        final MethodRef implementationTarget,
        final String instantiatedSamDescriptor
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "supportedJdkBridgeTarget",
            int.class,
            LambdaMetafactorySupport.ReceiverBinding.class,
            List.class,
            MethodRef.class,
            String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, referenceKind, receiverBinding, captureDescriptors, implementationTarget, instantiatedSamDescriptor);
    }

    private static boolean jdkBridgeParametersMatch(
        final LambdaMetafactorySupport.ReceiverBinding receiverBinding,
        final List<String> captureDescriptors,
        final List<String> instantiatedParameters,
        final List<String> implementationParameters
    ) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "jdkBridgeParametersMatch",
            LambdaMetafactorySupport.ReceiverBinding.class,
            List.class,
            List.class,
            List.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, receiverBinding, captureDescriptors, instantiatedParameters, implementationParameters);
    }

    private static boolean jdkBridgeParameterMatches(final String sourceDescriptor, final String implementationDescriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "jdkBridgeParameterMatches",
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, sourceDescriptor, implementationDescriptor);
    }

    private static boolean boxedPrimitiveMatches(final String objectDescriptor, final String primitiveDescriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod(
            "boxedPrimitiveMatches",
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, objectDescriptor, primitiveDescriptor);
    }

    private static boolean isObjectDescriptor(final String descriptor) throws Exception {
        final Method method = LambdaMetafactorySupport.class.getDeclaredMethod("isObjectDescriptor", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, descriptor);
    }
}
