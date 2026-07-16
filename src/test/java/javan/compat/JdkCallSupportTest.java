package javan.compat;

import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkCallSupportTest {
    @Test
    void threadLocalGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef("java/lang/ThreadLocal", "get", "()Ljava/lang/Object;")))
            .isTrue();
    }

    @Test
    void threadLocalCallsRequireThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef("java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V")))
            .containsExactly("threads");
    }

    @Test
    void threadBuilderUnstartedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "unstarted",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).isTrue();
    }

    @Test
    void genericThreadBuilderStartIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder",
            "start",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).isTrue();
    }

    @Test
    void genericThreadBuilderNameCounterIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder",
            "name",
            "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;"
        ))).isTrue();
    }

    @Test
    void threadBuilderUnstartedRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "unstarted",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).containsExactly("threads");
    }

    @Test
    void genericThreadBuilderFactoryRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder",
            "factory",
            "()Ljava/util/concurrent/ThreadFactory;"
        ))).containsExactly("threads");
    }

    @Test
    void builtinCollectionInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("java/util/Collection"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_COLLECTION);
    }

    @Test
    void builtinMapInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("java/util/Map"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_MAP);
    }

    @Test
    void builtinMapEntryInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("java/util/Map$Entry"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_MAP_ENTRY);
    }

    @Test
    void builtinObjectArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[Ljava/lang/Object;"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_OBJECT_ARRAY);
    }

    @Test
    void builtinIntArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[I"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_INT_ARRAY);
    }

    @Test
    void builtinLongArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[J"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_LONG_ARRAY);
    }

    @Test
    void builtinFloatArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[F"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_FLOAT_ARRAY);
    }

    @Test
    void builtinDoubleArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[D"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_DOUBLE_ARRAY);
    }

    @Test
    void builtinByteArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[B"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_BYTE_ARRAY);
    }

    @Test
    void builtinBooleanArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[Z"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_BOOLEAN_ARRAY);
    }

    @Test
    void builtinShortArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[S"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_SHORT_ARRAY);
    }

    @Test
    void builtinCharArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[C"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_CHAR_ARRAY);
    }

    @Test
    void characterValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "valueOf",
            "(C)Ljava/lang/Character;"
        ))).isTrue();
    }

    @Test
    void characterCharValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "charValue",
            "()C"
        ))).isTrue();
    }

    @Test
    void byteValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Byte",
            "valueOf",
            "(B)Ljava/lang/Byte;"
        ))).isTrue();
    }

    @Test
    void byteByteValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Byte",
            "byteValue",
            "()B"
        ))).isTrue();
    }

    @Test
    void shortValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Short",
            "valueOf",
            "(S)Ljava/lang/Short;"
        ))).isTrue();
    }

    @Test
    void shortShortValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Short",
            "shortValue",
            "()S"
        ))).isTrue();
    }

    @Test
    void virtualThreadPerTaskExecutorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/Executors",
            "newVirtualThreadPerTaskExecutor",
            "()Ljava/util/concurrent/ExecutorService;"
        ))).isTrue();
    }

    @Test
    void threadPerTaskExecutorRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/Executors",
            "newThreadPerTaskExecutor",
            "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
        ))).containsExactly("threads");
    }

    @Test
    void mathAbsFloatIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "abs",
            "(F)F"
        ))).isTrue();
    }

    @Test
    void mathAbsDoubleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "abs",
            "(D)D"
        ))).isTrue();
    }

    @Test
    void executorCloseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "close",
            "()V"
        ))).isTrue();
    }

    @Test
    void dateTimeFormatterBuilderAppendPatternIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/format/DateTimeFormatterBuilder",
            "appendPattern",
            "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;"
        ))).isTrue();
    }

    @Test
    void dateTimeFormatterBuilderToFormatterRequiresTimeRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/time/format/DateTimeFormatterBuilder",
            "toFormatter",
            "(Ljava/util/Locale;)Ljava/time/format/DateTimeFormatter;"
        ))).containsExactly("time");
    }

    @Test
    void executorExecuteRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/Executor",
            "execute",
            "(Ljava/lang/Runnable;)V"
        ))).containsExactly("threads");
    }

    @Test
    void executorSubmitRunnableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "submit",
            "(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"
        ))).isTrue();
    }

    @Test
    void futureCancelRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/Future",
            "cancel",
            "(Z)Z"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledThreadPoolExecutorScheduleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).isTrue();
    }

    @Test
    void scheduledThreadPoolExecutorScheduleAtFixedRateRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "scheduleAtFixedRate",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).containsExactly("threads");
    }

    @Test
    void executorAwaitTerminationIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "awaitTermination",
            "(JLjava/util/concurrent/TimeUnit;)Z"
        ))).isTrue();
    }

    @Test
    void executorShutdownNowRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "shutdownNow",
            "()Ljava/util/List;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledThreadPoolExecutorAwaitTerminationIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "awaitTermination",
            "(JLjava/util/concurrent/TimeUnit;)Z"
        ))).isTrue();
    }

    @Test
    void scheduledThreadPoolExecutorShutdownNowRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "shutdownNow",
            "()Ljava/util/List;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceScheduleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).isTrue();
    }

    @Test
    void scheduledExecutorServiceShutdownRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "shutdown",
            "()V"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceAwaitTerminationIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "awaitTermination",
            "(JLjava/util/concurrent/TimeUnit;)Z"
        ))).isTrue();
    }

    @Test
    void scheduledExecutorServiceShutdownNowRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "shutdownNow",
            "()Ljava/util/List;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceScheduleAtFixedRateRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "scheduleAtFixedRate",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).containsExactly("threads");
    }

    @Test
    void atomicBooleanConstructorWithInitialValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicBoolean",
            "<init>",
            "(Z)V"
        ))).isTrue();
    }

    @Test
    void atomicBooleanGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicBoolean",
            "get",
            "()Z"
        ))).isTrue();
    }

    @Test
    void atomicIntegerConstructorWithInitialValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void atomicIntegerGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "get",
            "()I"
        ))).isTrue();
    }

    @Test
    void atomicReferenceConstructorWithInitialValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "<init>",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void atomicReferenceGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "get",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void atomicReferenceSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "set",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void normalizesInheritedScheduledThreadPoolExecutorCallFromApplicationSubclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void normalizesInheritedScheduledThreadPoolExecutorScheduleAtFixedRateCallFromApplicationSubclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "(I)V", Optional.empty())
            ),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "scheduleAtFixedRate",
                "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "scheduleAtFixedRate",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void normalizeInheritedSupportedJdkCallRejectsMissingClassFileEntry() {
        final Map<String, ClassFile> classes = new java.util.HashMap<>();
        classes.put("com/acme/Scheduler", null);

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            classes,
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).isEmpty();
    }

    @Test
    void normalizeInheritedSupportedJdkCallRejectsSubclassWithoutSuperclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "()V", Optional.empty())),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).isEmpty();
    }

    @Test
    void normalizeInheritedSupportedJdkCallRejectsSubclassOverride() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "(I)V", Optional.empty()),
                new MethodInfo(0, "schedule", "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;", Optional.empty())
            ),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).isEmpty();
    }

    @Test
    void normalizeInheritedSupportedJdkCallResolvesAcrossIntermediateApplicationSuperclass() {
        final ClassFile middle = new ClassFile(
            69,
            "com/acme/Middle",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Middle.class"),
            true
        );
        final ClassFile leaf = new ClassFile(
            69,
            "com/acme/Leaf",
            "com/acme/Middle",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Leaf.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(middle.name(), middle, leaf.name(), leaf),
            new javan.classfile.MethodRef(
                "com/acme/Leaf",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void lockSupportParkIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "park",
            "()V"
        ))).isTrue();
    }

    @Test
    void lockSupportParkNanosIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "parkNanos",
            "(J)V"
        ))).isTrue();
    }

    @Test
    void lockSupportParkUntilIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "parkUntil",
            "(J)V"
        ))).isTrue();
    }

    @Test
    void lockSupportBlockerOverloadIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "parkNanos",
            "(Ljava/lang/Object;J)V"
        ))).isFalse();
    }

    @Test
    void lockSupportUnparkRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "unpark",
            "(Ljava/lang/Thread;)V"
        ))).containsExactly("threads");
    }

    @Test
    void mapOfEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "()Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void booleanEqualsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Boolean",
            "equals",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void socketGetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getSoTimeout",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setSoTimeout",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void socketGetReuseAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getReuseAddress",
            "()Z"
        ))).isTrue();
    }

    @Test
    void socketSetReuseAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setReuseAddress",
            "(Z)V"
        ))).isTrue();
    }

    @Test
    void socketGetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getReceiveBufferSize",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setReceiveBufferSize",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void socketGetSendBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getSendBufferSize",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetSendBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setSendBufferSize",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void serverSocketGetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "getSoTimeout",
            "()I"
        ))).isTrue();
    }

    @Test
    void serverSocketGetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "getReceiveBufferSize",
            "()I"
        ))).isTrue();
    }

    @Test
    void serverSocketSetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "setReceiveBufferSize",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void serverSocketSetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "setSoTimeout",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void consumerAcceptIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "accept",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void zonedDateTimeToInstantIsUnsupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/ZonedDateTime",
            "toInstant",
            "()Ljava/time/Instant;"
        ))).isFalse();
    }

    @Test
    void mapKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "keySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void typedThreadBuilderNameCounterRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "name",
            "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;"
        ))).containsExactly("threads");
    }

    @Test
    void threadFactoryNewThreadRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ThreadFactory",
            "newThread",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).containsExactly("threads");
    }

    @Test
    void stringValueOfIntIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "valueOf",
            "(I)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stringDescribeConstableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "describeConstable",
            "()Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void stringResolveConstantDescStringReturnIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stringResolveConstantDescObjectBridgeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void booleanArraysCopyOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "copyOf",
            "([ZI)[Z"
        ))).isTrue();
    }

    @Test
    void pathsGetRequiresFilesystemRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/nio/file/Paths",
            "get",
            "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"
        ))).containsExactly("filesystem");
    }

    @Test
    void fileNotFoundExceptionIsAssignableToIOException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/io/FileNotFoundException", "java/io/IOException"))
            .isTrue();
    }

    @Test
    void noSuchElementExceptionIsAssignableToRuntimeException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/util/NoSuchElementException", "java/lang/RuntimeException"))
            .isTrue();
    }

    @Test
    void errorIsNotAssignableToException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/lang/Error", "java/lang/Exception"))
            .isFalse();
    }

    @Test
    void applicationThrowableIsNotPlatformAssignable() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("com/acme/ProblemException", "java/lang/Exception"))
            .isFalse();
    }
}
