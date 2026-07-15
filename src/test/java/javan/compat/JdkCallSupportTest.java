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
