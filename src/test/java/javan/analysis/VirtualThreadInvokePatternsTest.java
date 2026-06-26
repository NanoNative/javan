package javan.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldInfo;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;

final class VirtualThreadInvokePatternsTest {
    @Test
    void detectsGenericAndTypedVirtualThreadBuilderNameOverloads() {
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(
            new MethodRef("java/lang/Thread$Builder", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(
            new MethodRef("java/lang/Thread$Builder", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(
            new MethodRef("java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;")
        )).isFalse();
    }

    @Test
    void computesRunnableStackWidthForLocalAliasAndInlineConstructor() {
        final List<Instruction> localAlias = List.of(
            instruction(0, 43, "aload_1"),
            instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );
        final List<Instruction> inlineConstructor = List.of(
            classInstruction(0, 187, "new", "com/acme/Task"),
            instruction(1, 89, "dup"),
            instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
            instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );

        assertThat(VirtualThreadInvokePatterns.runnableArgumentStackWidth(localAlias, 1)).isEqualTo(1);
        assertThat(VirtualThreadInvokePatterns.runnableArgumentStackWidth(inlineConstructor, 3)).isEqualTo(3);
    }

    @Test
    void computesRunnableStackWidthForWideLocalAlias() {
        final List<Instruction> localAlias = List.of(
            instructionOperands(0, 25, "aload", 5),
            instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );

        assertThat(VirtualThreadInvokePatterns.runnableArgumentStackWidth(localAlias, 1)).isEqualTo(1);
    }

    @Test
    void rejectsMalformedRunnableProducerShapesAndInvalidIndices() {
        final List<Instruction> malformedConstructor = List.of(
            classInstruction(0, 187, "new", "com/acme/Task"),
            instruction(1, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
            instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );
        final List<Instruction> unrelatedProducer = List.of(
            instruction(0, 3, "iconst_0"),
            instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );

        assertThat(VirtualThreadInvokePatterns.runnableArgumentStackWidth(malformedConstructor, 2)).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.runnableArgumentStackWidth(unrelatedProducer, 1)).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.runnableArgumentStackWidth(unrelatedProducer, 0)).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(unrelatedProducer, 1)).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(unrelatedProducer, 3)).isEqualTo(-1);
    }

    @Test
    void computesReceiverProducerIndexForInlineAndLocalAliasArguments() {
        final List<Instruction> localAlias = List.of(
            instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            instruction(1, 43, "aload_1"),
            instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );
        final List<Instruction> inlineConstructor = List.of(
            instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            classInstruction(1, 187, "new", "com/acme/Task"),
            instruction(2, 89, "dup"),
            instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
            instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );

        assertThat(VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(localAlias, 2)).isEqualTo(0);
        assertThat(VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(inlineConstructor, 4)).isEqualTo(0);
    }

    @Test
    void transparentReferenceProducerIndexSkipsSingleAndNestedCheckcasts() {
        final List<Instruction> instructions = List.of(
            instruction(0, 42, "aload_0"),
            classInstruction(1, 192, "checkcast", "java/lang/Object"),
            classInstruction(2, 192, "checkcast", "java/lang/Thread$Builder$OfVirtual"),
            instruction(3, 177, "return")
        );

        assertThat(VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, 0)).isEqualTo(0);
        assertThat(VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, 1)).isEqualTo(0);
        assertThat(VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, 2)).isEqualTo(0);
        assertThat(VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, 3)).isEqualTo(3);
        assertThat(VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, -1)).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, 8)).isEqualTo(8);
    }

    @Test
    void detectsVirtualThreadExecutorMethodRefs() {
        assertThat(VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(
            new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(
            new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isExecutorExecute(
            new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isExecutorServiceShutdown(
            new MethodRef("java/util/concurrent/ExecutorService", "shutdown", "()V")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isExecutorServiceClose(
            new MethodRef("java/util/concurrent/ExecutorService", "close", "()V")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isExecutorServiceClose(
            new MethodRef("java/util/concurrent/ExecutorService", "shutdown", "()V")
        )).isFalse();
    }

    @Test
    void detectsVirtualThreadStartFactoryAndNewThreadMethodRefs() {
        assertThat(VirtualThreadInvokePatterns.isThreadOfVirtual(
            new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(
            new MethodRef("java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualUnstarted(
            new MethodRef("java/lang/Thread$Builder", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderVirtualFactory(
            new MethodRef("java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadFactoryNewThread(
            new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "stop", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualUnstarted(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "()Ljava/lang/Thread;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderVirtualFactory(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/lang/Object;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadFactoryNewThread(
            new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "()Ljava/lang/Thread;")
        )).isFalse();
    }

    @Test
    void rejectsThreadFactoryAndExecutorPredicatesOnDescriptorMismatch() {
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(
            new MethodRef("java/lang/Thread$Builder", "start", "()Ljava/lang/Thread;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadFactoryNewThread(
            new MethodRef("java/util/concurrent/ThreadFactory", "spawn", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(
            new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorServiceShutdown(
            new MethodRef("java/util/concurrent/ExecutorService", "shutdown", "(J)V")
        )).isFalse();
    }

    @Test
    void rejectsMismatchedVirtualThreadPredicateDescriptorsAndOwners() {
        assertThat(VirtualThreadInvokePatterns.isThreadOfVirtual(
            new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Object;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(
            new MethodRef("java/lang/Object", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isThreadFactoryNewThread(
            new MethodRef("java/lang/Object", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(
            new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/lang/Object;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(
            new MethodRef("java/lang/Object", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorExecute(
            new MethodRef("java/lang/Object", "execute", "(Ljava/lang/Runnable;)V")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorExecute(
            new MethodRef("java/util/concurrent/Executor", "execute", "()V")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorServiceShutdown(
            new MethodRef("java/lang/Object", "shutdown", "()V")
        )).isFalse();
        assertThat(VirtualThreadInvokePatterns.isExecutorServiceClose(
            new MethodRef("java/util/concurrent/ExecutorService", "close", "(I)V")
        )).isFalse();
    }

    @Test
    void findsPreviousLocalStoreAndLoadStoreSlots() {
        final List<Instruction> instructions = List.of(
            instruction(0, 76, "astore_1"),
            instruction(1, 43, "aload_1"),
            instructionOperands(2, 58, "astore", 5),
            instructionOperands(3, 25, "aload", 5)
        );

        assertThat(VirtualThreadInvokePatterns.localLoadSlot(instructions.get(1))).isEqualTo(1);
        assertThat(VirtualThreadInvokePatterns.localLoadSlot(instructions.get(3))).isEqualTo(5);
        assertThat(VirtualThreadInvokePatterns.localLoadSlot(instruction(4, 3, "iconst_0"))).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.localStoreSlot(instructions.get(0))).isEqualTo(1);
        assertThat(VirtualThreadInvokePatterns.localStoreSlot(instructions.get(2))).isEqualTo(5);
        assertThat(VirtualThreadInvokePatterns.localStoreSlot(instruction(5, 177, "return"))).isEqualTo(-1);
        assertThat(VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, 3, 5)).isEqualTo(2);
        assertThat(VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, 1, 2)).isEqualTo(-1);
    }

    @Test
    void detectsSupportedStaticBuilderWrapperMethod() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(
            Map.of(owner.name(), owner),
            new MethodRef(owner.name(), "builder", "()Ljava/lang/Thread$Builder$OfVirtual;")
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithCounterNameChain() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        stringInstruction(1, "worker-"),
                        longInstruction(2, 9, "lconst_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "()Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(
            Map.of(owner.name(), owner),
            new MethodRef(owner.name(), "factory", "()Ljava/util/concurrent/ThreadFactory;")
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticBuilderWrapperMethodWithStringNameChain() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    3,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        stringInstruction(1, "worker"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticBuilderWrapperMethodWithStringParameterPassThrough() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(
            Map.of(owner.name(), owner),
            new MethodRef(owner.name(), "builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithParameterPassThrough() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 31, "lload_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(
            Map.of(owner.name(), owner),
            new MethodRef(owner.name(), "factory", "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;")
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticBuilderWrapperMethodWithGenericBuilderReturnAndParameterCounterChain() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;",
                Optional.of(new CodeAttribute(
                    4,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 31, "lload_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;")),
                        instruction(4, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;").orElseThrow()
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithWideParameterSlots() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(IILjava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    5,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instructionOperands(1, 25, "aload", 2),
                        instructionOperands(2, 22, "lload", 3),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(IILjava/lang/String;J)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithWideConstantOpcodes() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        new Instruction(1, 19, "ldc_w", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("wide-worker"), Optional.empty(), Optional.empty(), Optional.empty()),
                        longInstruction(2, 20, "ldc2_w"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "()Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithLload0CounterParameter() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(JLjava/lang/String;)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 44, "aload_2"),
                        instruction(2, 30, "lload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(JLjava/lang/String;)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithLload2CounterParameter() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(ILjava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    4,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 43, "aload_1"),
                        instruction(2, 32, "lload_2"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(ILjava/lang/String;J)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
    }

    @Test
    void detectsSupportedStaticFactoryWrapperMethodWithLload3CounterParameter() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(IILjava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    5,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 44, "aload_2"),
                        instruction(2, 33, "lload_3"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(IILjava/lang/String;J)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isTrue();
    }

    @Test
    void rejectsBuilderWrapperMethodWithTooFewInstructions() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(instruction(0, 176, "areturn"))
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperMethodWithExceptionTableEntries() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    1,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperMethodWithoutMethodRefRootProducer() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        stringInstruction(0, "worker"),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperMethodWithUnsupportedRootInvokeOpcode() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Main", "builder", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperWithoutMethodRefBeforeReturn() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 87, "pop"),
                        instruction(2, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "()Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperMethodWithUnresolvedStaticHelperRoot() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("com/acme/Helper", "builder", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperWithNonFactoryTerminalMethodRef() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 184, "invokestatic", new MethodRef("com/acme/Helper", "noop", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "()Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperWithTrailingStringConstantWithoutNameCall() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        stringInstruction(1, "orphan"),
                        instruction(2, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperWithLongParameterLoadedAsObject() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "(J)Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "(J)Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperWithObjectParameterLoadedAsLong() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 31, "lload_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperWithNonLongInstructionForCounterParameter() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 43, "aload_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(5, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperWithOutOfRangeParameterSlot() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instructionOperands(1, 25, "aload", 5),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperWithoutTerminalFactoryCall() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        stringInstruction(1, "worker-"),
                        longInstruction(2, 9, "lconst_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "()Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperWithNonStringNameProducer() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    3,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 3, "iconst_0"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsNonStaticBuilderWrapperMethod() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperCallWhenMethodIsMissing() {
        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(
            Map.of(),
            new MethodRef("com/acme/Missing", "builder", "()Ljava/lang/Thread$Builder$OfVirtual;")
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperCallWhenMethodIsMissing() {
        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(
            Map.of(),
            new MethodRef("com/acme/Missing", "factory", "()Ljava/util/concurrent/ThreadFactory;")
        )).isFalse();
    }

    @Test
    void rejectsFactoryWrapperCallWhenMethodIsAbsentOnExistingClass() {
        final ClassFile owner = classFile("com/acme/Main");

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(
            Map.of(owner.name(), owner),
            new MethodRef(owner.name(), "factory", "()Ljava/util/concurrent/ThreadFactory;")
        )).isFalse();
    }

    @Test
    void detectsSupportedBuilderWrapperCallThroughAnotherWrapper() {
        final MethodInfo root = new MethodInfo(
            0x0008,
            "root",
            "()Ljava/lang/Thread$Builder$OfVirtual;",
            Optional.of(new CodeAttribute(
                2,
                0,
                new byte[0],
                0,
                List.of(
                    instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                    instruction(1, 176, "areturn")
                )
            ))
        );
        final MethodInfo wrapper = new MethodInfo(
            0x0008,
            "wrapper",
            "()Ljava/lang/Thread$Builder$OfVirtual;",
            Optional.of(new CodeAttribute(
                2,
                0,
                new byte[0],
                0,
                List.of(
                    instruction(0, 184, "invokestatic", new MethodRef("com/acme/Main", "root", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                    instruction(1, 176, "areturn")
                )
            ))
        );
        final ClassFile owner = classFile("com/acme/Main", root, wrapper);

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(
            Map.of(owner.name(), owner),
            new MethodRef(owner.name(), "wrapper", "()Ljava/lang/Thread$Builder$OfVirtual;")
        )).isTrue();
    }

    @Test
    void rejectsBuilderWrapperMethodWithoutCode() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(0x0008, "builder", "()Ljava/lang/Thread$Builder$OfVirtual;", Optional.empty())
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void rejectsBuilderWrapperMethodWithoutObjectReturn() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 177, "return")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isFalse();
    }

    @Test
    void detectsSupportedBuilderWrapperMethodWithLongNameCounter() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    4,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        stringInstruction(1, "worker-"),
                        longInstruction(2, 10, "lconst_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("builder", "()Ljava/lang/Thread$Builder$OfVirtual;").orElseThrow()
        )).isTrue();
    }

    @Test
    void rejectsFactoryWrapperMethodWithoutFactoryStep() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    4,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        stringInstruction(1, "worker-"),
                        longInstruction(2, 20, "ldc2_w"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(4, 176, "areturn")
                    )
                ))
            )
        );

        assertThat(VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(
            Map.of(owner.name(), owner),
            owner,
            owner.method("factory", "()Ljava/util/concurrent/ThreadFactory;").orElseThrow()
        )).isFalse();
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic) {
        return new Instruction(offset, opcode, mnemonic, new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic, final MethodRef methodRef) {
        return new Instruction(offset, opcode, mnemonic, new byte[0], Optional.of(methodRef), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction classInstruction(final int offset, final int opcode, final String mnemonic, final String className) {
        return new Instruction(offset, opcode, mnemonic, new byte[0], Optional.empty(), Optional.empty(), Optional.of(className), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction instructionOperands(final int offset, final int opcode, final String mnemonic, final int... operands) {
        final byte[] bytes = new byte[operands.length];
        for (int index = 0; index < operands.length; index++) {
            bytes[index] = (byte) operands[index];
        }
        return new Instruction(offset, opcode, mnemonic, bytes, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction stringInstruction(final int offset, final String value) {
        return new Instruction(offset, 18, "ldc", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction longInstruction(final int offset, final int opcode, final String mnemonic) {
        return new Instruction(offset, opcode, mnemonic, new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ClassFile classFile(final String name, final MethodInfo... methods) {
        return new ClassFile(
            65,
            name,
            "java/lang/Object",
            0,
            List.of(),
            List.<FieldInfo>of(),
            List.of(methods),
            Path.of(name + ".class"),
            true
        );
    }
}
