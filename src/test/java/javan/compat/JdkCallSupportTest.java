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
    void atomicReferenceGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "get",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void atomicReferenceCompareAndSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "compareAndSet",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void atomicReferenceGetAndSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "getAndSet",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void atomicReferenceCallsRequireThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "set",
            "(Ljava/lang/Object;)V"
        ))).containsExactly("threads");
    }

    @Test
    void atomicLongGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicLong",
            "get",
            "()J"
        ))).isTrue();
    }

    @Test
    void longParseLongIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "parseLong",
            "(Ljava/lang/String;)J"
        ))).isTrue();
    }

    @Test
    void longParseLongRequiresStringsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Long",
            "parseLong",
            "(Ljava/lang/String;)J"
        ))).containsExactly("strings");
    }

    @Test
    void simpleDateFormatConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/text/SimpleDateFormat",
            "<init>",
            "(Ljava/lang/String;)V"
        ))).isTrue();
    }

    @Test
    void simpleDateFormatFormatIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/text/SimpleDateFormat",
            "format",
            "(Ljava/util/Date;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void simpleDateFormatRequiresTimeAndStringsRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/text/SimpleDateFormat",
            "format",
            "(Ljava/util/Date;)Ljava/lang/String;"
        ))).containsExactly("time", "strings");
    }

    @Test
    void atomicLongCallsRequireThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicLong",
            "incrementAndGet",
            "()J"
        ))).containsExactly("threads");
    }

    @Test
    void atomicIntegerUpdateAndGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "updateAndGet",
            "(Ljava/util/function/IntUnaryOperator;)I"
        ))).isTrue();
    }

    @Test
    void sqlTimestampToLocalDateTimeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/sql/Timestamp",
            "toLocalDateTime",
            "()Ljava/time/LocalDateTime;"
        ))).isTrue();
    }

    @Test
    void sqlDateCallsRequireTimeRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/sql/Date",
            "valueOf",
            "(Ljava/time/LocalDate;)Ljava/sql/Date;"
        ))).containsExactly("time");
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
    void classGetCanonicalNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getCanonicalName",
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
    void classIsEnumIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "isEnum",
            "()Z"
        ))).isTrue();
    }

    @Test
    void classIsInstanceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "isInstance",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void httpExchangeGetRequestUriIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/net/httpserver/HttpExchange",
            "getRequestURI",
            "()Ljava/net/URI;"
        ))).isTrue();
    }

    @Test
    void httpExchangeGetRequestMethodIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/net/httpserver/HttpExchange",
            "getRequestMethod",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void httpExchangeGetRequestHeadersIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/net/httpserver/HttpExchange",
            "getRequestHeaders",
            "()Lcom/sun/net/httpserver/Headers;"
        ))).isTrue();
    }

    @Test
    void httpExchangeGetRequestBodyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/net/httpserver/HttpExchange",
            "getRequestBody",
            "()Ljava/io/InputStream;"
        ))).isTrue();
    }

    @Test
    void headersGetFirstIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/net/httpserver/Headers",
            "getFirst",
            "(Ljava/lang/String;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void inputStreamReadAllBytesIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/io/InputStream",
            "readAllBytes",
            "()[B"
        ))).isTrue();
    }

    @Test
    void httpExchangeCallsRequireNetworkAndHttpRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "com/sun/net/httpserver/HttpExchange",
            "getRequestURI",
            "()Ljava/net/URI;"
        ))).containsExactly("network", "http");
    }

    @Test
    void classCastIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "cast",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void uriGetPathIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/URI",
            "getPath",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void uriGetQueryIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/URI",
            "getQuery",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void httpRequestDefaultConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/http/HttpRequest",
            "<init>",
            "()V"
        ))).isTrue();
    }

    @Test
    void charsetDefaultCharsetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/nio/charset/Charset",
            "defaultCharset",
            "()Ljava/nio/charset/Charset;"
        ))).isTrue();
    }

    @Test
    void charsetNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/nio/charset/Charset",
            "name",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void urlDecoderDecodeUtf8IsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/URLDecoder",
            "decode",
            "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stringGetBytesCharsetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "getBytes",
            "(Ljava/nio/charset/Charset;)[B"
        ))).isTrue();
    }

    @Test
    void classIsAssignableFromIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "isAssignableFrom",
            "(Ljava/lang/Class;)Z"
        ))).isTrue();
    }

    @Test
    void stackTraceElementConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/StackTraceElement",
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V"
        ))).isTrue();
    }

    @Test
    void stackTraceElementGetClassNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/StackTraceElement",
            "getClassName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stackTraceElementGetMethodNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/StackTraceElement",
            "getMethodName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stackTraceElementGetLineNumberIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/StackTraceElement",
            "getLineNumber",
            "()I"
        ))).isTrue();
    }

    @Test
    void linkedHashMapPutAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "putAll",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void hashMapCopyConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "<init>",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void treeMapCopyConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/TreeMap",
            "<init>",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void collectionsEmptyMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "emptyMap",
            "()Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfTwoEntriesIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void collectionsUnmodifiableListIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "unmodifiableList",
            "(Ljava/util/List;)Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void linkedHashSetConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "<init>",
            "()V"
        ))).isTrue();
    }

    @Test
    void linkedHashSetCollectionConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "<init>",
            "(Ljava/util/Collection;)V"
        ))).isTrue();
    }

    @Test
    void listClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void enumValueOfClassStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Enum",
            "valueOf",
            "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;"
        ))).isTrue();
    }

    @Test
    void objectsEqualsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "equals",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void arrayListStreamIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "stream",
            "()Ljava/util/stream/Stream;"
        ))).isTrue();
    }

    @Test
    void arrayListReversedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "reversed",
            "()Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void byteArrayOutputStreamCloseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/io/ByteArrayOutputStream",
            "close",
            "()V"
        ))).isTrue();
    }

    @Test
    void byteArrayInputStreamCloseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/io/ByteArrayInputStream",
            "close",
            "()V"
        ))).isTrue();
    }

    @Test
    void collectionSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void runtimeGetRuntimeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "getRuntime",
            "()Ljava/lang/Runtime;"
        ))).isTrue();
    }

    @Test
    void runtimeTotalMemoryIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "totalMemory",
            "()J"
        ))).isTrue();
    }

    @Test
    void runtimeFreeMemoryIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "freeMemory",
            "()J"
        ))).isTrue();
    }

    @Test
    void runtimeMaxMemoryIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "maxMemory",
            "()J"
        ))).isTrue();
    }

    @Test
    void runtimeMetricsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "availableProcessors",
            "()I"
        ))).containsExactly("management");
    }

    @Test
    void runtimeAddShutdownHookIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "addShutdownHook",
            "(Ljava/lang/Thread;)V"
        ))).isTrue();
    }

    @Test
    void runtimeRemoveShutdownHookIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "removeShutdownHook",
            "(Ljava/lang/Thread;)Z"
        ))).isTrue();
    }

    @Test
    void runtimeExitIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "exit",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void runtimeShutdownHookCallsRequireManagementAndThreadsRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "addShutdownHook",
            "(Ljava/lang/Thread;)V"
        ))).containsExactly("management", "threads");
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "removeShutdownHook",
            "(Ljava/lang/Thread;)Z"
        ))).containsExactly("management", "threads");
    }

    @Test
    void runtimeExitRequiresManagementProcessAndThreadsRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Runtime",
            "exit",
            "(I)V"
        ))).containsExactly("management", "process", "threads");
    }

    @Test
    void managementFactoryGetThreadMxBeanIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/ManagementFactory",
            "getThreadMXBean",
            "()Ljava/lang/management/ThreadMXBean;"
        ))).isTrue();
    }

    @Test
    void managementFactoryGetRuntimeMxBeanIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/ManagementFactory",
            "getRuntimeMXBean",
            "()Ljava/lang/management/RuntimeMXBean;"
        ))).isTrue();
    }

    @Test
    void managementFactoryGetMemoryMxBeanIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/ManagementFactory",
            "getMemoryMXBean",
            "()Ljava/lang/management/MemoryMXBean;"
        ))).isTrue();
    }

    @Test
    void managementFactoryGetOperatingSystemMxBeanIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/ManagementFactory",
            "getOperatingSystemMXBean",
            "()Ljava/lang/management/OperatingSystemMXBean;"
        ))).isTrue();
    }

    @Test
    void managementFactoryCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/management/ManagementFactory",
            "getThreadMXBean",
            "()Ljava/lang/management/ThreadMXBean;"
        ))).containsExactly("management");
    }

    @Test
    void threadMxBeanGetThreadCountIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/ThreadMXBean",
            "getThreadCount",
            "()I"
        ))).isTrue();
    }

    @Test
    void threadMxBeanCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/management/ThreadMXBean",
            "getThreadCount",
            "()I"
        ))).containsExactly("management");
    }

    @Test
    void runtimeMxBeanGetStartTimeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/RuntimeMXBean",
            "getStartTime",
            "()J"
        ))).isTrue();
    }

    @Test
    void runtimeMxBeanGetUptimeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/RuntimeMXBean",
            "getUptime",
            "()J"
        ))).isTrue();
    }

    @Test
    void runtimeMxBeanCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/management/RuntimeMXBean",
            "getUptime",
            "()J"
        ))).containsExactly("management");
    }

    @Test
    void memoryMxBeanGetHeapMemoryUsageIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/MemoryMXBean",
            "getHeapMemoryUsage",
            "()Ljava/lang/management/MemoryUsage;"
        ))).isTrue();
    }

    @Test
    void memoryMxBeanCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/management/MemoryMXBean",
            "getHeapMemoryUsage",
            "()Ljava/lang/management/MemoryUsage;"
        ))).containsExactly("management");
    }

    @Test
    void memoryUsageGetUsedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/MemoryUsage",
            "getUsed",
            "()J"
        ))).isTrue();
    }

    @Test
    void memoryUsageGetMaxIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/MemoryUsage",
            "getMax",
            "()J"
        ))).isTrue();
    }

    @Test
    void memoryUsageCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/management/MemoryUsage",
            "getUsed",
            "()J"
        ))).containsExactly("management");
    }

    @Test
    void operatingSystemMxBeanSystemLoadAverageIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/management/OperatingSystemMXBean",
            "getSystemLoadAverage",
            "()D"
        ))).isTrue();
    }

    @Test
    void operatingSystemMxBeanCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/management/OperatingSystemMXBean",
            "getSystemLoadAverage",
            "()D"
        ))).containsExactly("management");
    }

    @Test
    void comSunOperatingSystemMxBeanSystemLoadAverageIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/management/OperatingSystemMXBean",
            "getSystemLoadAverage",
            "()D"
        ))).isTrue();
    }

    @Test
    void operatingSystemMxBeanCpuLoadIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/management/OperatingSystemMXBean",
            "getCpuLoad",
            "()D"
        ))).isTrue();
    }

    @Test
    void comSunOperatingSystemMxBeanProcessCpuLoadIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "com/sun/management/OperatingSystemMXBean",
            "getProcessCpuLoad",
            "()D"
        ))).isTrue();
    }

    @Test
    void comSunOperatingSystemMxBeanCallsRequireManagementRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "com/sun/management/OperatingSystemMXBean",
            "getProcessCpuLoad",
            "()D"
        ))).containsExactly("management");
    }

    @Test
    void processHandleCurrentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/ProcessHandle",
            "current",
            "()Ljava/lang/ProcessHandle;"
        ))).isTrue();
    }

    @Test
    void processHandlePidIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/ProcessHandle",
            "pid",
            "()J"
        ))).isTrue();
    }

    @Test
    void processHandleCallsRequireProcessRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/ProcessHandle",
            "pid",
            "()J"
        ))).containsExactly("process");
    }

    @Test
    void collectionsEmptyListIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "emptyList",
            "()Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void listRemoveObjectIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void listForEachIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).isTrue();
    }

    @Test
    void listContainsAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "containsAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void listToArrayIntFunctionIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "toArray",
            "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void listForEachExposesClosedWorldConsumerDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/List",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "accept",
            "(Ljava/lang/Object;)V"
        ));
    }

    @Test
    void collectionForEachIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).isTrue();
    }

    @Test
    void collectionForEachExposesClosedWorldConsumerDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Collection",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "accept",
            "(Ljava/lang/Object;)V"
        ));
    }

    @Test
    void listAddAtIndexIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "add",
            "(ILjava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void listAddAllCollectionIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "addAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void mapForEachExposesClosedWorldBiConsumerDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Map",
            "forEach",
            "(Ljava/util/function/BiConsumer;)V"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/BiConsumer",
            "accept",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        ));
    }

    @Test
    void streamFilterExposesClosedWorldPredicateDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "filter",
            "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "(Ljava/lang/Object;)Z"
        ));
    }

    @Test
    void collectorsGroupingByExposesClosedWorldFunctionDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Collectors",
            "groupingBy",
            "(Ljava/util/function/Function;Ljava/util/stream/Collector;)Ljava/util/stream/Collector;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ));
    }

    @Test
    void collectorsToCollectionExposesClosedWorldSupplierDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Collectors",
            "toCollection",
            "(Ljava/util/function/Supplier;)Ljava/util/stream/Collector;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Supplier",
            "get",
            "()Ljava/lang/Object;"
        ));
    }

    @Test
    void collectorsToMapExposesClosedWorldCallbackDispatchTargets() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Collectors",
            "toMap",
            "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/BinaryOperator;Ljava/util/function/Supplier;)Ljava/util/stream/Collector;"
        ))).containsExactly(
            new javan.classfile.MethodRef(
                "java/util/function/Function",
                "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            ),
            new javan.classfile.MethodRef(
                "java/util/function/Function",
                "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            ),
            new javan.classfile.MethodRef(
                "java/util/function/BinaryOperator",
                "apply",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
            ),
            new javan.classfile.MethodRef(
                "java/util/function/Supplier",
                "get",
                "()Ljava/lang/Object;"
            )
        );
    }

    @Test
    void streamMapExposesClosedWorldFunctionDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "map",
            "(Ljava/util/function/Function;)Ljava/util/stream/Stream;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ));
    }

    @Test
    void streamMapToIntIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "mapToInt",
            "(Ljava/util/function/ToIntFunction;)Ljava/util/stream/IntStream;"
        ))).isTrue();
    }

    @Test
    void streamMapToIntExposesClosedWorldToIntFunctionDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "mapToInt",
            "(Ljava/util/function/ToIntFunction;)Ljava/util/stream/IntStream;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/ToIntFunction",
            "applyAsInt",
            "(Ljava/lang/Object;)I"
        ));
    }

    @Test
    void streamCollectIsNotGenerallySupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "collect",
            "(Ljava/util/stream/Collector;)Ljava/lang/Object;"
        ))).isFalse();
    }

    @Test
    void objectsNonNullIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "nonNull",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void collectorsToListIsNotGenerallySupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Collectors",
            "toList",
            "()Ljava/util/stream/Collector;"
        ))).isFalse();
    }

    @Test
    void collectorsJoiningIsNotGenerallySupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Collectors",
            "joining",
            "()Ljava/util/stream/Collector;"
        ))).isFalse();
    }

    @Test
    void streamAnyMatchExposesClosedWorldPredicateDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "anyMatch",
            "(Ljava/util/function/Predicate;)Z"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "(Ljava/lang/Object;)Z"
        ));
    }

    @Test
    void streamNoneMatchExposesClosedWorldPredicateDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "noneMatch",
            "(Ljava/util/function/Predicate;)Z"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "(Ljava/lang/Object;)Z"
        ));
    }

    @Test
    void comparatorReverseOrderIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Comparator",
            "reverseOrder",
            "()Ljava/util/Comparator;"
        ))).isTrue();
    }

    @Test
    void comparatorComparingIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Comparator",
            "comparing",
            "(Ljava/util/function/Function;Ljava/util/Comparator;)Ljava/util/Comparator;"
        ))).isTrue();
    }

    @Test
    void streamSortedComparatorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "sorted",
            "(Ljava/util/Comparator;)Ljava/util/stream/Stream;"
        ))).isTrue();
    }

    @Test
    void comparatorComparingExposesClosedWorldFunctionDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Comparator",
            "comparing",
            "(Ljava/util/function/Function;Ljava/util/Comparator;)Ljava/util/Comparator;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ));
    }

    @Test
    void intStreamMaxIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/IntStream",
            "max",
            "()Ljava/util/OptionalInt;"
        ))).isTrue();
    }

    @Test
    void threadRunnableNameConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "<init>",
            "(Ljava/lang/Runnable;Ljava/lang/String;)V"
        ))).isTrue();
    }

    @Test
    void optionalIntOrElseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/OptionalInt",
            "orElse",
            "(I)I"
        ))).isTrue();
    }

    @Test
    void optionalIntCallsRequireOptionalRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/OptionalInt",
            "orElse",
            "(I)I"
        ))).containsExactly("optional");
    }

    @Test
    void intStreamMaxRequiresCollectionAndOptionalRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/stream/IntStream",
            "max",
            "()Ljava/util/OptionalInt;"
        ))).containsExactly("collections", "optional");
    }

    @Test
    void streamCollectRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "collect",
            "(Ljava/util/stream/Collector;)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void streamSortedRequiresCollectionsManagedHeapAndStringsRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "sorted",
            "(Ljava/util/Comparator;)Ljava/util/stream/Stream;"
        ))).containsExactly("collections", "managed-heap", "strings");
    }

    @Test
    void collectorsToListRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/stream/Collectors",
            "toList",
            "()Ljava/util/stream/Collector;"
        ))).containsExactly("collections");
    }

    @Test
    void intStreamNonOptionalSignatureHasNoRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/stream/IntStream",
            "max",
            "()I"
        ))).isEmpty();
    }

    @Test
    void optionalOrElseGetExposesClosedWorldSupplierDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "orElseGet",
            "(Ljava/util/function/Supplier;)Ljava/lang/Object;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Supplier",
            "get",
            "()Ljava/lang/Object;"
        ));
    }

    @Test
    void optionalOrIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Optional",
            "or",
            "(Ljava/util/function/Supplier;)Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void optionalOrExposesClosedWorldSupplierDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "or",
            "(Ljava/util/function/Supplier;)Ljava/util/Optional;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Supplier",
            "get",
            "()Ljava/lang/Object;"
        ));
    }

    @Test
    void optionalFilterExposesClosedWorldPredicateDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "filter",
            "(Ljava/util/function/Predicate;)Ljava/util/Optional;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "(Ljava/lang/Object;)Z"
        ));
    }

    @Test
    void optionalIfPresentExposesClosedWorldConsumerDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "ifPresent",
            "(Ljava/util/function/Consumer;)V"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "accept",
            "(Ljava/lang/Object;)V"
        ));
    }

    @Test
    void optionalFlatMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Optional",
            "flatMap",
            "(Ljava/util/function/Function;)Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void optionalFlatMapExposesClosedWorldFunctionDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "flatMap",
            "(Ljava/util/function/Function;)Ljava/util/Optional;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ));
    }

    @Test
    void optionalMapExposesClosedWorldFunctionDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "map",
            "(Ljava/util/function/Function;)Ljava/util/Optional;"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ));
    }

    @Test
    void optionalIfPresentOrElseExposesBothClosedWorldDispatchTargets() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/Optional",
            "ifPresentOrElse",
            "(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V"
        ))).containsExactly(
            new javan.classfile.MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
            new javan.classfile.MethodRef("java/lang/Runnable", "run", "()V")
        );
    }

    @Test
    void atomicIntegerUpdateAndGetExposesClosedWorldIntUnaryOperatorDispatchTarget() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "updateAndGet",
            "(Ljava/util/function/IntUnaryOperator;)I"
        ))).containsExactly(new javan.classfile.MethodRef(
            "java/util/function/IntUnaryOperator",
            "applyAsInt",
            "(I)I"
        ));
    }

    @Test
    void ordinarySupportedJdkCallHasNoClosedWorldHigherOrderDispatchTargets() {
        assertThat(JdkCallSupport.closedWorldHigherOrderDispatchTargets(new javan.classfile.MethodRef(
            "java/util/List",
            "size",
            "()I"
        ))).isEmpty();
    }

    @Test
    void streamForEachIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).isTrue();
    }

    @Test
    void collectionsSingletonListIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "singletonList",
            "(Ljava/lang/Object;)Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void characterIsWhitespaceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "isWhitespace",
            "(C)Z"
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
    void stringReplaceSequenceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "replace",
            "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
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
    void zoneIdSystemDefaultIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/ZoneId",
            "systemDefault",
            "()Ljava/time/ZoneId;"
        ))).isTrue();
    }

    @Test
    void instantAtZoneRequiresTimeRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/time/Instant",
            "atZone",
            "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"
        ))).containsExactly("time");
    }

    @Test
    void dateFromInstantIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Date",
            "from",
            "(Ljava/time/Instant;)Ljava/util/Date;"
        ))).isTrue();
    }

    @Test
    void calendarGetInstanceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Calendar",
            "getInstance",
            "()Ljava/util/Calendar;"
        ))).isTrue();
    }

    @Test
    void calendarToInstantRequiresTimeRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Calendar",
            "toInstant",
            "()Ljava/time/Instant;"
        ))).containsExactly("time");
    }

    @Test
    void zonedDateTimeToOffsetDateTimeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/ZonedDateTime",
            "toOffsetDateTime",
            "()Ljava/time/OffsetDateTime;"
        ))).isTrue();
    }

    @Test
    void localTimeGetNanoIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/LocalTime",
            "getNano",
            "()I"
        ))).isTrue();
    }

    @Test
    void temporalAccessorIsSupportedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/temporal/TemporalAccessor",
            "isSupported",
            "(Ljava/time/temporal/TemporalField;)Z"
        ))).isTrue();
    }

    @Test
    void temporalAccessorQueryIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/temporal/TemporalAccessor",
            "query",
            "(Ljava/time/temporal/TemporalQuery;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void temporalQueriesZoneIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/temporal/TemporalQueries",
            "zone",
            "()Ljava/time/temporal/TemporalQuery;"
        ))).isTrue();
    }

    @Test
    void localDateFromIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/LocalDate",
            "from",
            "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/LocalDate;"
        ))).isTrue();
    }

    @Test
    void localDateNowRequiresTimeRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/time/LocalDate",
            "now",
            "(Ljava/time/ZoneId;)Ljava/time/LocalDate;"
        ))).containsExactly("time");
    }

    @Test
    void localTimeFromIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/LocalTime",
            "from",
            "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/LocalTime;"
        ))).isTrue();
    }

    @Test
    void zonedDateTimeOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/ZonedDateTime",
            "of",
            "(Ljava/time/LocalDate;Ljava/time/LocalTime;Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"
        ))).isTrue();
    }

    @Test
    void calendarSetTimeInMillisIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Calendar",
            "setTimeInMillis",
            "(J)V"
        ))).isTrue();
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
    void numberLongValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Number",
            "longValue",
            "()J"
        ))).isTrue();
    }

    @Test
    void doubleValueOfStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "valueOf",
            "(Ljava/lang/String;)Ljava/lang/Double;"
        ))).isTrue();
    }

    @Test
    void doubleParseDoubleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "parseDouble",
            "(Ljava/lang/String;)D"
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
    void streamToArrayWithGeneratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/stream/Stream",
            "toArray",
            "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"
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
    void concurrentHashMapDefaultConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "<init>",
            "()V"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
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
    void collectionAddIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
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
    void mapRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "remove",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "clear",
            "()V"
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
    void throwableGetStackTraceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "getStackTrace",
            "()[Ljava/lang/StackTraceElement;"
        ))).isTrue();
    }

    @Test
    void throwableSetStackTraceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "setStackTrace",
            "([Ljava/lang/StackTraceElement;)V"
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
    void throwableGetStackTraceWrongDescriptorIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "getStackTrace",
            "()[Ljava/lang/Throwable;"
        ))).isFalse();
    }

    @Test
    void throwableSetStackTraceWrongDescriptorIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Throwable",
            "setStackTrace",
            "([Ljava/lang/Throwable;)V"
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
    void loggingLevelIntValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/logging/Level",
            "intValue",
            "()I"
        ))).isTrue();
    }

    @Test
    void logRecordConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/logging/LogRecord",
            "<init>",
            "(Ljava/util/logging/Level;Ljava/lang/String;)V"
        ))).isTrue();
    }

    @Test
    void formatterFormatMessageIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/logging/Formatter",
            "formatMessage",
            "(Ljava/util/logging/LogRecord;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void logRecordRequiresTimeStringsAndExceptionsRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/logging/LogRecord",
            "getMillis",
            "()J"
        ))).containsExactly("time", "strings", "exceptions");
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
    void closedWorldDispatchSupportsIntUnaryOperatorApplyAsInt() {
        assertThat(JdkCallSupport.isSupportedClosedWorldDispatchCall(new javan.classfile.MethodRef(
            "java/util/function/IntUnaryOperator",
            "applyAsInt",
            "(I)I"
        ))).isTrue();
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
