package javan.compat;

import org.junit.jupiter.api.Test;

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
