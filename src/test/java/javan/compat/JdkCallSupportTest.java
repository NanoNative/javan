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
    void objectGetClassIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Object",
            "getClass",
            "()Ljava/lang/Class;"
        ))).isTrue();
    }

    @Test
    void classGetNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void classGetSimpleNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getSimpleName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void classIsArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "isArray",
            "()Z"
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
    void stringValueOfIntIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "valueOf",
            "(I)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stringEqualsIgnoreCaseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "equalsIgnoreCase",
            "(Ljava/lang/String;)Z"
        ))).isTrue();
    }

    @Test
    void stringToLowerCaseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "toLowerCase",
            "()Ljava/lang/String;"
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
    void characterValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "valueOf",
            "(C)Ljava/lang/Character;"
        ))).isTrue();
    }

    @Test
    void numberIntValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Number",
            "intValue",
            "()I"
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
    void objectArraysStreamIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "stream",
            "([Ljava/lang/Object;)Ljava/util/stream/Stream;"
        ))).isTrue();
    }

    @Test
    void setStreamIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "stream",
            "()Ljava/util/stream/Stream;"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapNewKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "newKeySet",
            "()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"
        ))).isTrue();
    }

    @Test
    void setAddIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "add",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void setRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void setSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void setToArrayIntFunctionIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "toArray",
            "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void keySetViewAddIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap$KeySetView",
            "add",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void mapEntrySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "entrySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void mapEntryGetKeyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map$Entry",
            "getKey",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapEntryGetValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map$Entry",
            "getValue",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapForEachIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "forEach",
            "(Ljava/util/function/BiConsumer;)V"
        ))).isTrue();
    }

    @Test
    void throwableAddSuppressedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "addSuppressed",
            "(Ljava/lang/Throwable;)V"
        ))).isTrue();
    }

    @Test
    void throwableGetSuppressedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "getSuppressed",
            "()[Ljava/lang/Throwable;"
        ))).isTrue();
    }

    @Test
    void throwableAddSuppressedWrongDescriptorIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "addSuppressed",
            "()V"
        ))).isFalse();
    }

    @Test
    void throwableGetSuppressedWrongDescriptorIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "getSuppressed",
            "()Ljava/lang/Throwable;"
        ))).isFalse();
    }

    @Test
    void throwableGetMessageWrongDescriptorIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "getMessage",
            "()Ljava/lang/Object;"
        ))).isFalse();
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

    @Test
    void closedWorldDispatchRejectsRunnableWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/lang/Runnable",
            "start",
            "()V"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsRunnableWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/lang/Runnable",
            "run",
            "()Z"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsConsumerWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "accept",
            "()V"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsConsumerWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "consume",
            "(Ljava/lang/Object;)V"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsBiConsumerWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/BiConsumer",
            "accept",
            "(Ljava/lang/Object;)V"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsBiConsumerWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/BiConsumer",
            "consume",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsBooleanSupplierWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/BooleanSupplier",
            "get",
            "()Z"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsBooleanSupplierWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/BooleanSupplier",
            "getAsBoolean",
            "()Ljava/lang/Boolean;"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsFunctionWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "()Ljava/lang/Object;"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsFunctionWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "map",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsPredicateWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "()Z"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsPredicateWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "matches",
            "(Ljava/lang/Object;)Z"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsSupplierWrongName() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Supplier",
            "supply",
            "()Ljava/lang/Object;"
        ))).isFalse();
    }

    @Test
    void closedWorldDispatchRejectsSupplierWrongDescriptor() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/Supplier",
            "get",
            "()Ljava/lang/String;"
        ))).isFalse();
    }
}
