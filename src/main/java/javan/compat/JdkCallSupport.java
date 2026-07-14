package javan.compat;

import javan.classfile.MethodRef;

import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for exact JDK calls that the native backend accepts.
 */
public final class JdkCallSupport {
    private static final String[][] PLATFORM_THROWABLE_PARENTS = new String[][]{
        {"java/lang/Exception", "java/lang/Throwable"},
        {"java/lang/Error", "java/lang/Throwable"},
        {"java/lang/RuntimeException", "java/lang/Exception"},
        {"java/lang/ArithmeticException", "java/lang/RuntimeException"},
        {"java/lang/ArrayStoreException", "java/lang/RuntimeException"},
        {"java/lang/ClassCastException", "java/lang/RuntimeException"},
        {"java/lang/EnumConstantNotPresentException", "java/lang/RuntimeException"},
        {"java/lang/IllegalArgumentException", "java/lang/RuntimeException"},
        {"java/lang/IllegalMonitorStateException", "java/lang/RuntimeException"},
        {"java/lang/IllegalStateException", "java/lang/RuntimeException"},
        {"java/lang/IllegalThreadStateException", "java/lang/RuntimeException"},
        {"java/lang/IndexOutOfBoundsException", "java/lang/RuntimeException"},
        {"java/lang/NegativeArraySizeException", "java/lang/RuntimeException"},
        {"java/lang/NullPointerException", "java/lang/RuntimeException"},
        {"java/lang/NumberFormatException", "java/lang/RuntimeException"},
        {"java/lang/SecurityException", "java/lang/RuntimeException"},
        {"java/lang/StringIndexOutOfBoundsException", "java/lang/RuntimeException"},
        {"java/lang/UnsupportedOperationException", "java/lang/RuntimeException"},
        {"java/util/NoSuchElementException", "java/lang/RuntimeException"},
        {"java/io/IOException", "java/lang/Exception"},
        {"java/io/EOFException", "java/io/IOException"},
        {"java/io/FileNotFoundException", "java/io/IOException"},
        {"java/io/InterruptedIOException", "java/io/IOException"},
        {"java/io/UTFDataFormatException", "java/io/IOException"},
        {"java/lang/ReflectiveOperationException", "java/lang/Exception"},
        {"java/lang/ClassNotFoundException", "java/lang/ReflectiveOperationException"},
        {"java/lang/IllegalAccessException", "java/lang/ReflectiveOperationException"},
        {"java/lang/InstantiationException", "java/lang/ReflectiveOperationException"},
        {"java/lang/NoSuchFieldException", "java/lang/ReflectiveOperationException"},
        {"java/lang/NoSuchMethodException", "java/lang/ReflectiveOperationException"},
        {"java/lang/LinkageError", "java/lang/Error"},
        {"java/lang/ClassCircularityError", "java/lang/LinkageError"},
        {"java/lang/ClassFormatError", "java/lang/LinkageError"},
        {"java/lang/ExceptionInInitializerError", "java/lang/LinkageError"},
        {"java/lang/IncompatibleClassChangeError", "java/lang/LinkageError"},
        {"java/lang/NoClassDefFoundError", "java/lang/LinkageError"},
        {"java/lang/UnsatisfiedLinkError", "java/lang/LinkageError"},
        {"java/lang/VerifyError", "java/lang/LinkageError"},
        {"java/lang/VirtualMachineError", "java/lang/Error"},
        {"java/lang/InternalError", "java/lang/VirtualMachineError"},
        {"java/lang/OutOfMemoryError", "java/lang/VirtualMachineError"},
        {"java/lang/StackOverflowError", "java/lang/VirtualMachineError"},
        {"java/lang/UnknownError", "java/lang/VirtualMachineError"}
    };

    private static final List<SupportedCall> SUPPORTED_CALLS = List.of(
        intrinsic(
            "Objects.requireNonNull",
            "java/util/Objects",
            "requireNonNull",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
        ),
        intrinsic("Objects.nonNull", "java/util/Objects", "nonNull", "(Ljava/lang/Object;)Z"),
        intrinsic("Objects.equals", "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        intrinsic("Math.abs", "java/lang/Math", "abs", "(I)I", "(J)J", "(F)F", "(D)D"),
        intrinsic("Math.min", "java/lang/Math", "min", "(II)I", "(JJ)J"),
        intrinsic("Math.max", "java/lang/Math", "max", "(II)I", "(JJ)J"),
        intrinsic("Math.toIntExact", "java/lang/Math", "toIntExact", "(J)I"),
        intrinsic("System.nanoTime", "java/lang/System", "nanoTime", "()J"),
        intrinsic("System.currentTimeMillis", "java/lang/System", "currentTimeMillis", "()J"),
        intrinsic("System.lineSeparator", "java/lang/System", "lineSeparator", "()Ljava/lang/String;"),
        intrinsic("System.getenv", "java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;"),
        intrinsic("System.getProperty", "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        intrinsic("System.arraycopy", "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"),
        intrinsic("System.exit", "java/lang/System", "exit", "(I)V"),
        runtime("Runtime.getRuntime", "java/lang/Runtime", "getRuntime", "()Ljava/lang/Runtime;"),
        runtime("Runtime.totalMemory", "java/lang/Runtime", "totalMemory", "()J"),
        runtime("Runtime.freeMemory", "java/lang/Runtime", "freeMemory", "()J"),
        runtime("Runtime.maxMemory", "java/lang/Runtime", "maxMemory", "()J"),
        runtime("Runtime.availableProcessors", "java/lang/Runtime", "availableProcessors", "()I"),
        runtime("Runtime.addShutdownHook", "java/lang/Runtime", "addShutdownHook", "(Ljava/lang/Thread;)V"),
        runtime("Runtime.removeShutdownHook", "java/lang/Runtime", "removeShutdownHook", "(Ljava/lang/Thread;)Z"),
        runtime("Runtime.exit", "java/lang/Runtime", "exit", "(I)V"),
        runtime("ManagementFactory.getThreadMXBean", "java/lang/management/ManagementFactory", "getThreadMXBean", "()Ljava/lang/management/ThreadMXBean;"),
        runtime("ThreadMXBean.getThreadCount", "java/lang/management/ThreadMXBean", "getThreadCount", "()I"),
        runtime("ManagementFactory.getRuntimeMXBean", "java/lang/management/ManagementFactory", "getRuntimeMXBean", "()Ljava/lang/management/RuntimeMXBean;"),
        runtime("RuntimeMXBean.getUptime", "java/lang/management/RuntimeMXBean", "getUptime", "()J"),
        runtime("RuntimeMXBean.getStartTime", "java/lang/management/RuntimeMXBean", "getStartTime", "()J"),
        runtime("ManagementFactory.getMemoryMXBean", "java/lang/management/ManagementFactory", "getMemoryMXBean", "()Ljava/lang/management/MemoryMXBean;"),
        runtime("MemoryMXBean.getHeapMemoryUsage", "java/lang/management/MemoryMXBean", "getHeapMemoryUsage", "()Ljava/lang/management/MemoryUsage;"),
        runtime("MemoryUsage.getUsed", "java/lang/management/MemoryUsage", "getUsed", "()J"),
        runtime("MemoryUsage.getMax", "java/lang/management/MemoryUsage", "getMax", "()J"),
        runtime("ManagementFactory.getOperatingSystemMXBean", "java/lang/management/ManagementFactory", "getOperatingSystemMXBean", "()Ljava/lang/management/OperatingSystemMXBean;"),
        runtime("OperatingSystemMXBean.getSystemLoadAverage", "java/lang/management/OperatingSystemMXBean", "getSystemLoadAverage", "()D"),
        runtime("OperatingSystemMXBean.getSystemLoadAverage", "com/sun/management/OperatingSystemMXBean", "getSystemLoadAverage", "()D"),
        runtime("OperatingSystemMXBean.getProcessCpuLoad", "com/sun/management/OperatingSystemMXBean", "getProcessCpuLoad", "()D"),
        runtime("OperatingSystemMXBean.getCpuLoad", "com/sun/management/OperatingSystemMXBean", "getCpuLoad", "()D"),
        runtime("ProcessHandle.current", "java/lang/ProcessHandle", "current", "()Ljava/lang/ProcessHandle;"),
        runtime("ProcessHandle.pid", "java/lang/ProcessHandle", "pid", "()J"),
        runtime("Thread.<init>", "java/lang/Thread", "<init>", "()V", "(Ljava/lang/Runnable;)V", "(Ljava/lang/Runnable;Ljava/lang/String;)V"),
        runtime("Thread.ofVirtual", "java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;"),
        runtime("Thread.startVirtualThread", "java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.name", "java/lang/Thread$Builder", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder;", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;"),
        runtime("Thread.Builder.start", "java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.unstarted", "java/lang/Thread$Builder", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.factory", "java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;"),
        runtime("Thread.Builder.toString", "java/lang/Thread$Builder", "toString", "()Ljava/lang/String;"),
        runtime("Thread.Builder.hashCode", "java/lang/Thread$Builder", "hashCode", "()I"),
        runtime("Thread.Builder.equals", "java/lang/Thread$Builder", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Thread.Builder.getClass", "java/lang/Thread$Builder", "getClass", "()Ljava/lang/Class;"),
        runtime("Thread.Builder.OfVirtual.name", "java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;"),
        runtime("Thread.Builder.OfVirtual.factory", "java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;"),
        runtime("Thread.Builder.OfVirtual.start", "java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.OfVirtual.unstarted", "java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.OfVirtual.toString", "java/lang/Thread$Builder$OfVirtual", "toString", "()Ljava/lang/String;"),
        runtime("Thread.Builder.OfVirtual.hashCode", "java/lang/Thread$Builder$OfVirtual", "hashCode", "()I"),
        runtime("Thread.Builder.OfVirtual.equals", "java/lang/Thread$Builder$OfVirtual", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Thread.Builder.OfVirtual.getClass", "java/lang/Thread$Builder$OfVirtual", "getClass", "()Ljava/lang/Class;"),
        runtime("Executors.newVirtualThreadPerTaskExecutor", "java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;"),
        runtime("Executors.newThreadPerTaskExecutor", "java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"),
        runtime("ThreadFactory.newThread", "java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("ThreadFactory.toString", "java/util/concurrent/ThreadFactory", "toString", "()Ljava/lang/String;"),
        runtime("ThreadFactory.hashCode", "java/util/concurrent/ThreadFactory", "hashCode", "()I"),
        runtime("ThreadFactory.equals", "java/util/concurrent/ThreadFactory", "equals", "(Ljava/lang/Object;)Z"),
        runtime("ThreadFactory.getClass", "java/util/concurrent/ThreadFactory", "getClass", "()Ljava/lang/Class;"),
        runtime("Executor.execute", "java/util/concurrent/Executor", "execute", "(Ljava/lang/Runnable;)V"),
        runtime("ExecutorService.execute", "java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V"),
        runtime("ExecutorService.shutdown", "java/util/concurrent/ExecutorService", "shutdown", "()V"),
        runtime("ExecutorService.close", "java/util/concurrent/ExecutorService", "close", "()V"),
        runtime("ExecutorService.toString", "java/util/concurrent/ExecutorService", "toString", "()Ljava/lang/String;"),
        runtime("ExecutorService.hashCode", "java/util/concurrent/ExecutorService", "hashCode", "()I"),
        runtime("ExecutorService.equals", "java/util/concurrent/ExecutorService", "equals", "(Ljava/lang/Object;)Z"),
        runtime("ExecutorService.getClass", "java/util/concurrent/ExecutorService", "getClass", "()Ljava/lang/Class;"),
        runtime("Thread.currentThread", "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;"),
        runtime("Thread.sleep", "java/lang/Thread", "sleep", "(J)V"),
        runtime("Thread.interrupted", "java/lang/Thread", "interrupted", "()Z"),
        runtime("Thread.interrupt", "java/lang/Thread", "interrupt", "()V"),
        runtime("Thread.isInterrupted", "java/lang/Thread", "isInterrupted", "()Z"),
        runtime("Thread.isAlive", "java/lang/Thread", "isAlive", "()Z"),
        runtime("Thread.isVirtual", "java/lang/Thread", "isVirtual", "()Z"),
        runtime("Thread.getName", "java/lang/Thread", "getName", "()Ljava/lang/String;"),
        runtime("Thread.start", "java/lang/Thread", "start", "()V"),
        runtime("Thread.join", "java/lang/Thread", "join", "()V"),
        runtime("LockSupport.park", "java/util/concurrent/locks/LockSupport", "park", "()V"),
        runtime("LockSupport.parkNanos", "java/util/concurrent/locks/LockSupport", "parkNanos", "(J)V"),
        runtime("LockSupport.parkUntil", "java/util/concurrent/locks/LockSupport", "parkUntil", "(J)V"),
        runtime("LockSupport.unpark", "java/util/concurrent/locks/LockSupport", "unpark", "(Ljava/lang/Thread;)V"),
        runtime("ThreadLocal.<init>", "java/lang/ThreadLocal", "<init>", "()V"),
        runtime("ThreadLocal.get", "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;"),
        runtime("ThreadLocal.set", "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V"),
        runtime("ThreadLocal.remove", "java/lang/ThreadLocal", "remove", "()V"),
        runtime("AtomicBoolean.<init>", "java/util/concurrent/atomic/AtomicBoolean", "<init>", "()V", "(Z)V"),
        runtime("AtomicBoolean.get", "java/util/concurrent/atomic/AtomicBoolean", "get", "()Z"),
        runtime("AtomicBoolean.getPlain", "java/util/concurrent/atomic/AtomicBoolean", "getPlain", "()Z"),
        runtime("AtomicBoolean.set", "java/util/concurrent/atomic/AtomicBoolean", "set", "(Z)V"),
        runtime("AtomicBoolean.compareAndSet", "java/util/concurrent/atomic/AtomicBoolean", "compareAndSet", "(ZZ)Z"),
        runtime("AtomicInteger.<init>", "java/util/concurrent/atomic/AtomicInteger", "<init>", "()V", "(I)V"),
        runtime("AtomicInteger.get", "java/util/concurrent/atomic/AtomicInteger", "get", "()I"),
        runtime("AtomicInteger.getAndIncrement", "java/util/concurrent/atomic/AtomicInteger", "getAndIncrement", "()I"),
        runtime("AtomicInteger.incrementAndGet", "java/util/concurrent/atomic/AtomicInteger", "incrementAndGet", "()I"),
        runtime("AtomicInteger.decrementAndGet", "java/util/concurrent/atomic/AtomicInteger", "decrementAndGet", "()I"),
        runtime("AtomicInteger.updateAndGet", "java/util/concurrent/atomic/AtomicInteger", "updateAndGet", "(Ljava/util/function/IntUnaryOperator;)I"),
        runtime("AtomicLong.<init>", "java/util/concurrent/atomic/AtomicLong", "<init>", "()V", "(J)V"),
        runtime("AtomicLong.get", "java/util/concurrent/atomic/AtomicLong", "get", "()J"),
        runtime("AtomicLong.incrementAndGet", "java/util/concurrent/atomic/AtomicLong", "incrementAndGet", "()J"),
        runtime("AtomicLong.decrementAndGet", "java/util/concurrent/atomic/AtomicLong", "decrementAndGet", "()J"),
        runtime("AtomicReference.<init>", "java/util/concurrent/atomic/AtomicReference", "<init>", "()V", "(Ljava/lang/Object;)V"),
        runtime("AtomicReference.get", "java/util/concurrent/atomic/AtomicReference", "get", "()Ljava/lang/Object;"),
        runtime("AtomicReference.set", "java/util/concurrent/atomic/AtomicReference", "set", "(Ljava/lang/Object;)V"),
        runtime("AtomicReference.compareAndSet", "java/util/concurrent/atomic/AtomicReference", "compareAndSet", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("AtomicReference.getAndSet", "java/util/concurrent/atomic/AtomicReference", "getAndSet", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Collections.singletonList", "java/util/Collections", "singletonList", "(Ljava/lang/Object;)Ljava/util/List;"),
        runtime("Collections.unmodifiableList", "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;"),
        runtime("Character.isWhitespace", "java/lang/Character", "isWhitespace", "(C)Z"),
        runtime("Enum.valueOf", "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;"),
        runtime("Object.getClass", "java/lang/Object", "getClass", "()Ljava/lang/Class;"),
        runtime("Class.getName", "java/lang/Class", "getName", "()Ljava/lang/String;"),
        runtime("Class.getCanonicalName", "java/lang/Class", "getCanonicalName", "()Ljava/lang/String;"),
        runtime("Class.getSimpleName", "java/lang/Class", "getSimpleName", "()Ljava/lang/String;"),
        runtime("Class.isArray", "java/lang/Class", "isArray", "()Z"),
        runtime("Class.isEnum", "java/lang/Class", "isEnum", "()Z"),
        runtime("Class.isInstance", "java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z"),
        runtime("Class.cast", "java/lang/Class", "cast", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Class.isAssignableFrom", "java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z"),
        runtime(
            "StackTraceElement.<init>",
            "java/lang/StackTraceElement",
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V"
        ),
        runtime("StackTraceElement.getClassName", "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;"),
        runtime("StackTraceElement.getMethodName", "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;"),
        runtime("StackTraceElement.getLineNumber", "java/lang/StackTraceElement", "getLineNumber", "()I"),
        runtime("Level.intValue", "java/util/logging/Level", "intValue", "()I"),
        runtime("Level.toString", "java/util/logging/Level", "toString", "()Ljava/lang/String;"),
        runtime(
            "LogRecord.<init>",
            "java/util/logging/LogRecord",
            "<init>",
            "(Ljava/util/logging/Level;Ljava/lang/String;)V"
        ),
        runtime("LogRecord.getLevel", "java/util/logging/LogRecord", "getLevel", "()Ljava/util/logging/Level;"),
        runtime("LogRecord.getMessage", "java/util/logging/LogRecord", "getMessage", "()Ljava/lang/String;"),
        runtime("LogRecord.getMillis", "java/util/logging/LogRecord", "getMillis", "()J"),
        runtime("LogRecord.getParameters", "java/util/logging/LogRecord", "getParameters", "()[Ljava/lang/Object;"),
        runtime("LogRecord.setParameters", "java/util/logging/LogRecord", "setParameters", "([Ljava/lang/Object;)V"),
        runtime("LogRecord.getThrown", "java/util/logging/LogRecord", "getThrown", "()Ljava/lang/Throwable;"),
        runtime("LogRecord.setThrown", "java/util/logging/LogRecord", "setThrown", "(Ljava/lang/Throwable;)V"),
        runtime("LogRecord.getLoggerName", "java/util/logging/LogRecord", "getLoggerName", "()Ljava/lang/String;"),
        runtime("LogRecord.setLoggerName", "java/util/logging/LogRecord", "setLoggerName", "(Ljava/lang/String;)V"),
        runtime("Formatter.format", "java/util/logging/Formatter", "format", "(Ljava/util/logging/LogRecord;)Ljava/lang/String;"),
        runtime(
            "Formatter.formatMessage",
            "java/util/logging/Formatter",
            "formatMessage",
            "(Ljava/util/logging/LogRecord;)Ljava/lang/String;"
        ),
        runtime("SimpleDateFormat.<init>", "java/text/SimpleDateFormat", "<init>", "(Ljava/lang/String;)V"),
        runtime("SimpleDateFormat.format", "java/text/SimpleDateFormat", "format", "(Ljava/util/Date;)Ljava/lang/String;"),
        runtime("UUID.randomUUID", "java/util/UUID", "randomUUID", "()Ljava/util/UUID;"),
        runtime("UUID.toString", "java/util/UUID", "toString", "()Ljava/lang/String;"),
        intrinsic(
            "Arrays.copyOf",
            "java/util/Arrays",
            "copyOf",
            "([ZI)[Z",
            "([II)[I",
            "([JI)[J",
            "([BI)[B",
            "([SI)[S",
            "([CI)[C",
            "([FI)[F",
            "([DI)[D",
            "([Ljava/lang/Object;I)[Ljava/lang/Object;"
        ),
        intrinsic(
            "Arrays.copyOfRange",
            "java/util/Arrays",
            "copyOfRange",
            "([BII)[B",
            "([Ljava/lang/Object;II)[Ljava/lang/Object;"
        ),
        intrinsic(
            "Arrays.stream",
            "java/util/Arrays",
            "stream",
            "([Ljava/lang/Object;)Ljava/util/stream/Stream;"
        ),
        intrinsic("Integer.toString", "java/lang/Integer", "toString", "(I)Ljava/lang/String;"),
        runtime("Integer.valueOf", "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"),
        runtime("Integer.intValue", "java/lang/Integer", "intValue", "()I"),
        runtime("Number.intValue", "java/lang/Number", "intValue", "()I"),
        intrinsic("Long.toString", "java/lang/Long", "toString", "(J)Ljava/lang/String;"),
        intrinsic("Long.parseLong", "java/lang/Long", "parseLong", "(Ljava/lang/String;)J"),
        runtime("Long.valueOf", "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;"),
        runtime("Long.longValue", "java/lang/Long", "longValue", "()J"),
        intrinsic("Float.toString", "java/lang/Float", "toString", "(F)Ljava/lang/String;"),
        runtime("Float.valueOf", "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;"),
        runtime("Float.floatValue", "java/lang/Float", "floatValue", "()F"),
        intrinsic("Float.intBitsToFloat", "java/lang/Float", "intBitsToFloat", "(I)F"),
        intrinsic("Double.toString", "java/lang/Double", "toString", "(D)Ljava/lang/String;"),
        runtime("Double.valueOf", "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;"),
        runtime("Double.doubleValue", "java/lang/Double", "doubleValue", "()D"),
        intrinsic("Double.valueOf(String)", "java/lang/Double", "valueOf", "(Ljava/lang/String;)Ljava/lang/Double;"),
        intrinsic("Double.parseDouble", "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D"),
        intrinsic("Double.longBitsToDouble", "java/lang/Double", "longBitsToDouble", "(J)D"),
        intrinsic("Boolean.toString", "java/lang/Boolean", "toString", "(Z)Ljava/lang/String;"),
        runtime("Boolean.valueOf", "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"),
        runtime("Boolean.booleanValue", "java/lang/Boolean", "booleanValue", "()Z"),
        runtime("Character.valueOf", "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;"),
        runtime("Character.charValue", "java/lang/Character", "charValue", "()C"),
        runtime("Character.toString", "java/lang/Character", "toString", "()Ljava/lang/String;"),
        runtime("Number.longValue", "java/lang/Number", "longValue", "()J"),
        intrinsic(
            "String.valueOf",
            "java/lang/String",
            "valueOf",
            "(Ljava/lang/Object;)Ljava/lang/String;",
            "([C)Ljava/lang/String;",
            "([CII)Ljava/lang/String;",
            "(I)Ljava/lang/String;",
            "(J)Ljava/lang/String;",
            "(F)Ljava/lang/String;",
            "(D)Ljava/lang/String;",
            "(Z)Ljava/lang/String;",
            "(C)Ljava/lang/String;"
        ),
        intrinsic(
            "String.copyValueOf",
            "java/lang/String",
            "copyValueOf",
            "([C)Ljava/lang/String;",
            "([CII)Ljava/lang/String;"
        ),
        runtime("Duration.ofMillis", "java/time/Duration", "ofMillis", "(J)Ljava/time/Duration;"),
        runtime("Duration.ofSeconds", "java/time/Duration", "ofSeconds", "(J)Ljava/time/Duration;"),
        runtime("Duration.toMillis", "java/time/Duration", "toMillis", "()J"),
        runtime("ZoneId.systemDefault", "java/time/ZoneId", "systemDefault", "()Ljava/time/ZoneId;"),
        runtime("Instant.now", "java/time/Instant", "now", "()Ljava/time/Instant;"),
        runtime("Instant.ofEpochMilli", "java/time/Instant", "ofEpochMilli", "(J)Ljava/time/Instant;"),
        runtime("Instant.toEpochMilli", "java/time/Instant", "toEpochMilli", "()J"),
        runtime("Instant.atZone", "java/time/Instant", "atZone", "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"),
        runtime("OffsetDateTime.toInstant", "java/time/OffsetDateTime", "toInstant", "()Ljava/time/Instant;"),
        runtime("LocalDate.ofEpochDay", "java/time/LocalDate", "ofEpochDay", "(J)Ljava/time/LocalDate;"),
        runtime("LocalDate.from", "java/time/LocalDate", "from", "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/LocalDate;"),
        runtime("LocalDate.now", "java/time/LocalDate", "now", "(Ljava/time/ZoneId;)Ljava/time/LocalDate;"),
        runtime("LocalDate.atStartOfDay", "java/time/LocalDate", "atStartOfDay", "()Ljava/time/LocalDateTime;", "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"),
        runtime("LocalTime.from", "java/time/LocalTime", "from", "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/LocalTime;"),
        runtime("LocalTime.getHour", "java/time/LocalTime", "getHour", "()I"),
        runtime("LocalTime.getMinute", "java/time/LocalTime", "getMinute", "()I"),
        runtime("LocalTime.getSecond", "java/time/LocalTime", "getSecond", "()I"),
        runtime("LocalTime.getNano", "java/time/LocalTime", "getNano", "()I"),
        runtime("LocalDateTime.ofInstant", "java/time/LocalDateTime", "ofInstant", "(Ljava/time/Instant;Ljava/time/ZoneId;)Ljava/time/LocalDateTime;"),
        runtime("LocalDateTime.atZone", "java/time/LocalDateTime", "atZone", "(Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"),
        runtime("LocalDateTime.toLocalDate", "java/time/LocalDateTime", "toLocalDate", "()Ljava/time/LocalDate;"),
        runtime("LocalDateTime.toLocalTime", "java/time/LocalDateTime", "toLocalTime", "()Ljava/time/LocalTime;"),
        runtime("ZonedDateTime.of", "java/time/ZonedDateTime", "of", "(Ljava/time/LocalDate;Ljava/time/LocalTime;Ljava/time/ZoneId;)Ljava/time/ZonedDateTime;"),
        runtime("ZonedDateTime.toOffsetDateTime", "java/time/ZonedDateTime", "toOffsetDateTime", "()Ljava/time/OffsetDateTime;"),
        runtime("ZonedDateTime.toInstant", "java/time/ZonedDateTime", "toInstant", "()Ljava/time/Instant;"),
        runtime("ZonedDateTime.toLocalDate", "java/time/ZonedDateTime", "toLocalDate", "()Ljava/time/LocalDate;"),
        runtime("ZonedDateTime.toLocalTime", "java/time/ZonedDateTime", "toLocalTime", "()Ljava/time/LocalTime;"),
        runtime("ZonedDateTime.toLocalDateTime", "java/time/ZonedDateTime", "toLocalDateTime", "()Ljava/time/LocalDateTime;"),
        runtime("TemporalQueries.zone", "java/time/temporal/TemporalQueries", "zone", "()Ljava/time/temporal/TemporalQuery;"),
        runtime("TemporalQueries.localDate", "java/time/temporal/TemporalQueries", "localDate", "()Ljava/time/temporal/TemporalQuery;"),
        runtime("TemporalQueries.localTime", "java/time/temporal/TemporalQueries", "localTime", "()Ljava/time/temporal/TemporalQuery;"),
        runtime("TemporalAccessor.isSupported", "java/time/temporal/TemporalAccessor", "isSupported", "(Ljava/time/temporal/TemporalField;)Z"),
        runtime("TemporalAccessor.query", "java/time/temporal/TemporalAccessor", "query", "(Ljava/time/temporal/TemporalQuery;)Ljava/lang/Object;"),
        runtime("Calendar.getInstance", "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;"),
        runtime("Calendar.setTime", "java/util/Calendar", "setTime", "(Ljava/util/Date;)V"),
        runtime("Calendar.setTimeInMillis", "java/util/Calendar", "setTimeInMillis", "(J)V"),
        runtime("Calendar.set", "java/util/Calendar", "set", "(II)V"),
        runtime("Calendar.getTimeInMillis", "java/util/Calendar", "getTimeInMillis", "()J"),
        runtime("Calendar.toInstant", "java/util/Calendar", "toInstant", "()Ljava/time/Instant;"),
        runtime("Date.<init>", "java/util/Date", "<init>", "()V", "(J)V"),
        runtime("Date.from", "java/util/Date", "from", "(Ljava/time/Instant;)Ljava/util/Date;"),
        runtime("Date.toInstant", "java/util/Date", "toInstant", "()Ljava/time/Instant;"),
        runtime("Date.getTime", "java/util/Date", "getTime", "()J"),
        runtime("SqlDate.<init>", "java/sql/Date", "<init>", "(J)V"),
        runtime("SqlDate.valueOf", "java/sql/Date", "valueOf", "(Ljava/time/LocalDate;)Ljava/sql/Date;"),
        runtime("SqlDate.getTime", "java/sql/Date", "getTime", "()J"),
        runtime("SqlDate.toLocalDate", "java/sql/Date", "toLocalDate", "()Ljava/time/LocalDate;"),
        runtime("SqlTime.<init>", "java/sql/Time", "<init>", "(J)V"),
        runtime("SqlTime.valueOf", "java/sql/Time", "valueOf", "(Ljava/time/LocalTime;)Ljava/sql/Time;"),
        runtime("SqlTime.getTime", "java/sql/Time", "getTime", "()J"),
        runtime("SqlTime.toLocalTime", "java/sql/Time", "toLocalTime", "()Ljava/time/LocalTime;"),
        runtime("SqlTimestamp.<init>", "java/sql/Timestamp", "<init>", "(J)V"),
        runtime("SqlTimestamp.from", "java/sql/Timestamp", "from", "(Ljava/time/Instant;)Ljava/sql/Timestamp;"),
        runtime("SqlTimestamp.valueOf", "java/sql/Timestamp", "valueOf", "(Ljava/time/LocalDateTime;)Ljava/sql/Timestamp;"),
        runtime("SqlTimestamp.getTime", "java/sql/Timestamp", "getTime", "()J"),
        runtime("SqlTimestamp.toInstant", "java/sql/Timestamp", "toInstant", "()Ljava/time/Instant;"),
        runtime("SqlTimestamp.toLocalDateTime", "java/sql/Timestamp", "toLocalDateTime", "()Ljava/time/LocalDateTime;"),
        runtime("DateTimeFormatterBuilder.<init>", "java/time/format/DateTimeFormatterBuilder", "<init>", "()V"),
        runtime("DateTimeFormatterBuilder.parseCaseInsensitive", "java/time/format/DateTimeFormatterBuilder", "parseCaseInsensitive", "()Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.appendPattern", "java/time/format/DateTimeFormatterBuilder", "appendPattern", "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.optionalStart", "java/time/format/DateTimeFormatterBuilder", "optionalStart", "()Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.appendFraction", "java/time/format/DateTimeFormatterBuilder", "appendFraction", "(Ljava/time/temporal/TemporalField;IIZ)Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.optionalEnd", "java/time/format/DateTimeFormatterBuilder", "optionalEnd", "()Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.toFormatter", "java/time/format/DateTimeFormatterBuilder", "toFormatter", "(Ljava/util/Locale;)Ljava/time/format/DateTimeFormatter;"),
        runtime("DateTimeFormatter.parse", "java/time/format/DateTimeFormatter", "parse", "(Ljava/lang/CharSequence;)Ljava/time/temporal/TemporalAccessor;"),
        runtime("Instant.from", "java/time/Instant", "from", "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/Instant;"),
        runtime("ZonedDateTime.from", "java/time/ZonedDateTime", "from", "(Ljava/time/temporal/TemporalAccessor;)Ljava/time/ZonedDateTime;"),
        runtime("PrintStream.print", "java/io/PrintStream", "print", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "([C)V", "(C)V", "(Z)V", "(I)V", "(J)V", "(F)V", "(D)V"),
        runtime("PrintStream.println", "java/io/PrintStream", "println", "()V", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "([C)V", "(I)V", "(J)V", "(F)V", "(D)V", "(Z)V", "(C)V"),
        runtime("String.<init>", "java/lang/String", "<init>", "()V", "(Ljava/lang/String;)V", "(Ljava/lang/StringBuilder;)V", "([C)V", "([CII)V"),
        runtime("String.length", "java/lang/String", "length", "()I"),
        runtime("String.isEmpty", "java/lang/String", "isEmpty", "()Z"),
        runtime("String.isBlank", "java/lang/String", "isBlank", "()Z"),
        runtime("String.charAt", "java/lang/String", "charAt", "(I)C"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(I)I"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(II)I"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(Ljava/lang/String;)I"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(Ljava/lang/String;I)I"),
        runtime("String.lastIndexOf", "java/lang/String", "lastIndexOf", "(I)I"),
        runtime("String.lastIndexOf", "java/lang/String", "lastIndexOf", "(II)I"),
        runtime("String.lastIndexOf", "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I"),
        runtime("String.lastIndexOf", "java/lang/String", "lastIndexOf", "(Ljava/lang/String;I)I"),
        runtime("String.equals", "java/lang/String", "equals", "(Ljava/lang/Object;)Z"),
        runtime("String.equalsIgnoreCase", "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z"),
        runtime("String.contains", "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z"),
        runtime("String.startsWith", "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", "(Ljava/lang/String;I)Z"),
        runtime("String.endsWith", "java/lang/String", "endsWith", "(Ljava/lang/String;)Z"),
        runtime("String.replace", "java/lang/String", "replace", "(CC)Ljava/lang/String;"),
        runtime("String.replace", "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"),
        runtime("String.repeat", "java/lang/String", "repeat", "(I)Ljava/lang/String;"),
        runtime("String.intern", "java/lang/String", "intern", "()Ljava/lang/String;"),
        runtime("String.toString", "java/lang/String", "toString", "()Ljava/lang/String;"),
        runtime("String.concat", "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;"),
        runtime("String.describeConstable", "java/lang/String", "describeConstable", "()Ljava/util/Optional;"),
        runtime(
            "String.resolveConstantDesc",
            "java/lang/String",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ),
        runtime("String.toLowerCase", "java/lang/String", "toLowerCase", "()Ljava/lang/String;"),
        runtime("String.trim", "java/lang/String", "trim", "()Ljava/lang/String;"),
        runtime("String.substring", "java/lang/String", "substring", "(I)Ljava/lang/String;"),
        runtime("String.substring", "java/lang/String", "substring", "(II)Ljava/lang/String;"),
        runtime("String.subSequence", "java/lang/String", "subSequence", "(II)Ljava/lang/CharSequence;"),
        runtime("StringBuilder.<init>", "java/lang/StringBuilder", "<init>", "()V", "(I)V", "(Ljava/lang/String;)V"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", "([C)Ljava/lang/StringBuilder;", "([CII)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(F)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(D)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.toString", "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
        runtime("StringBuilder.length", "java/lang/StringBuilder", "length", "()I"),
        runtime("StringBuilder.isEmpty", "java/lang/StringBuilder", "isEmpty", "()Z"),
        runtime("StringBuilder.charAt", "java/lang/StringBuilder", "charAt", "(I)C"),
        runtime("StringBuilder.substring", "java/lang/StringBuilder", "substring", "(I)Ljava/lang/String;", "(II)Ljava/lang/String;"),
        runtime("StringBuilder.subSequence", "java/lang/StringBuilder", "subSequence", "(II)Ljava/lang/CharSequence;"),
        runtime("StringBuilder.indexOf", "java/lang/StringBuilder", "indexOf", "(Ljava/lang/String;)I", "(Ljava/lang/String;I)I"),
        runtime("StringBuilder.lastIndexOf", "java/lang/StringBuilder", "lastIndexOf", "(Ljava/lang/String;)I", "(Ljava/lang/String;I)I"),
        runtime("StringBuilder.compareTo", "java/lang/StringBuilder", "compareTo", "(Ljava/lang/StringBuilder;)I"),
        runtime("StringBuilder.delete", "java/lang/StringBuilder", "delete", "(II)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.deleteCharAt", "java/lang/StringBuilder", "deleteCharAt", "(I)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.insert", "java/lang/StringBuilder", "insert", "(ILjava/lang/String;)Ljava/lang/StringBuilder;", "(ILjava/lang/Object;)Ljava/lang/StringBuilder;", "(IZ)Ljava/lang/StringBuilder;", "(IC)Ljava/lang/StringBuilder;", "(II)Ljava/lang/StringBuilder;", "(IJ)Ljava/lang/StringBuilder;", "(IF)Ljava/lang/StringBuilder;", "(ID)Ljava/lang/StringBuilder;", "(I[C)Ljava/lang/StringBuilder;", "(I[CII)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.replace", "java/lang/StringBuilder", "replace", "(IILjava/lang/String;)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.reverse", "java/lang/StringBuilder", "reverse", "()Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.ensureCapacity", "java/lang/StringBuilder", "ensureCapacity", "(I)V"),
        runtime("StringBuilder.trimToSize", "java/lang/StringBuilder", "trimToSize", "()V"),
        runtime("StringBuilder.setCharAt", "java/lang/StringBuilder", "setCharAt", "(IC)V"),
        runtime("StringBuilder.setLength", "java/lang/StringBuilder", "setLength", "(I)V"),
        runtime("StringBuilder.capacity", "java/lang/StringBuilder", "capacity", "()I"),
        runtime("ArrayList.<init>", "java/util/ArrayList", "<init>", "()V", "(I)V", "(Ljava/util/Collection;)V"),
        runtime("ArrayList.add", "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z"),
        runtime("ArrayList.addAll", "java/util/ArrayList", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("CopyOnWriteArrayList.<init>", "java/util/concurrent/CopyOnWriteArrayList", "<init>", "()V"),
        runtime("CopyOnWriteArrayList.add", "java/util/concurrent/CopyOnWriteArrayList", "add", "(Ljava/lang/Object;)Z"),
        runtime("CopyOnWriteArrayList.size", "java/util/concurrent/CopyOnWriteArrayList", "size", "()I"),
        runtime("CopyOnWriteArrayList.get", "java/util/concurrent/CopyOnWriteArrayList", "get", "(I)Ljava/lang/Object;"),
        runtime("Collections.emptyList", "java/util/Collections", "emptyList", "()Ljava/util/List;"),
        runtime("Collections.emptyMap", "java/util/Collections", "emptyMap", "()Ljava/util/Map;"),
        runtime("List.of", "java/util/List", "of", "()Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.of", "java/util/List", "of", "([Ljava/lang/Object;)Ljava/util/List;"),
        runtime("List.copyOf", "java/util/List", "copyOf", "(Ljava/util/Collection;)Ljava/util/List;"),
        runtime("List.add", "java/util/List", "add", "(Ljava/lang/Object;)Z"),
        runtime("List.add", "java/util/List", "add", "(ILjava/lang/Object;)V"),
        runtime("ArrayList.add", "java/util/ArrayList", "add", "(ILjava/lang/Object;)V"),
        runtime("ArrayList.reversed", "java/util/ArrayList", "reversed", "()Ljava/util/List;"),
        runtime("List.addAll", "java/util/List", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("List.clear", "java/util/List", "clear", "()V"),
        runtime("List.size", "java/util/List", "size", "()I"),
        runtime("List.isEmpty", "java/util/List", "isEmpty", "()Z"),
        runtime("List.contains", "java/util/List", "contains", "(Ljava/lang/Object;)Z"),
        runtime("List.containsAll", "java/util/List", "containsAll", "(Ljava/util/Collection;)Z"),
        runtime("Collection.contains", "java/util/Collection", "contains", "(Ljava/lang/Object;)Z"),
        runtime("List.stream", "java/util/List", "stream", "()Ljava/util/stream/Stream;"),
        runtime("Collection.stream", "java/util/Collection", "stream", "()Ljava/util/stream/Stream;"),
        runtime("Collection.forEach", "java/util/Collection", "forEach", "(Ljava/util/function/Consumer;)V"),
        runtime("Set.stream", "java/util/Set", "stream", "()Ljava/util/stream/Stream;"),
        runtime("HashSet.<init>", "java/util/HashSet", "<init>", "()V", "(Ljava/util/Collection;)V"),
        runtime("LinkedHashSet.<init>", "java/util/LinkedHashSet", "<init>", "()V", "(Ljava/util/Collection;)V"),
        runtime("HashSet.add", "java/util/HashSet", "add", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashSet.add", "java/util/LinkedHashSet", "add", "(Ljava/lang/Object;)Z"),
        runtime("HashSet.remove", "java/util/HashSet", "remove", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashSet.remove", "java/util/LinkedHashSet", "remove", "(Ljava/lang/Object;)Z"),
        runtime("HashSet.size", "java/util/HashSet", "size", "()I"),
        runtime("LinkedHashSet.size", "java/util/LinkedHashSet", "size", "()I"),
        runtime("HashSet.isEmpty", "java/util/HashSet", "isEmpty", "()Z"),
        runtime("LinkedHashSet.isEmpty", "java/util/LinkedHashSet", "isEmpty", "()Z"),
        runtime("HashSet.contains", "java/util/HashSet", "contains", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashSet.contains", "java/util/LinkedHashSet", "contains", "(Ljava/lang/Object;)Z"),
        runtime("HashSet.iterator", "java/util/HashSet", "iterator", "()Ljava/util/Iterator;"),
        runtime("LinkedHashSet.iterator", "java/util/LinkedHashSet", "iterator", "()Ljava/util/Iterator;"),
        runtime("HashSet.toArray", "java/util/HashSet", "toArray", "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"),
        runtime("LinkedHashSet.toArray", "java/util/LinkedHashSet", "toArray", "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"),
        runtime("HashSet.stream", "java/util/HashSet", "stream", "()Ljava/util/stream/Stream;"),
        runtime("LinkedHashSet.stream", "java/util/LinkedHashSet", "stream", "()Ljava/util/stream/Stream;"),
        runtime("ConcurrentHashMap.newKeySet", "java/util/concurrent/ConcurrentHashMap", "newKeySet", "()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"),
        runtime("ConcurrentHashMap.<init>", "java/util/concurrent/ConcurrentHashMap", "<init>", "()V"),
        runtime("List.get", "java/util/List", "get", "(I)Ljava/lang/Object;"),
        runtime("List.getFirst", "java/util/List", "getFirst", "()Ljava/lang/Object;"),
        runtime("List.getLast", "java/util/List", "getLast", "()Ljava/lang/Object;"),
        runtime("List.remove", "java/util/List", "remove", "(Ljava/lang/Object;)Z"),
        runtime("List.removeLast", "java/util/List", "removeLast", "()Ljava/lang/Object;"),
        runtime("List.set", "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"),
        runtime("List.addFirst", "java/util/List", "addFirst", "(Ljava/lang/Object;)V"),
        runtime("List.forEach", "java/util/List", "forEach", "(Ljava/util/function/Consumer;)V"),
        runtime("List.iterator", "java/util/List", "iterator", "()Ljava/util/Iterator;"),
        runtime("List.toArray", "java/util/List", "toArray", "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;"),
        runtime("Collection.iterator", "java/util/Collection", "iterator", "()Ljava/util/Iterator;"),
        runtime("Iterator.hasNext", "java/util/Iterator", "hasNext", "()Z"),
        runtime("Iterator.next", "java/util/Iterator", "next", "()Ljava/lang/Object;"),
        runtime("Stream.filter", "java/util/stream/Stream", "filter", "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"),
        runtime("Stream.map", "java/util/stream/Stream", "map", "(Ljava/util/function/Function;)Ljava/util/stream/Stream;"),
        runtime("Stream.forEach", "java/util/stream/Stream", "forEach", "(Ljava/util/function/Consumer;)V"),
        runtime("Stream.toList", "java/util/stream/Stream", "toList", "()Ljava/util/List;"),
        runtime("Stream.findFirst", "java/util/stream/Stream", "findFirst", "()Ljava/util/Optional;"),
        runtime("Stream.anyMatch", "java/util/stream/Stream", "anyMatch", "(Ljava/util/function/Predicate;)Z"),
        runtime("Stream.noneMatch", "java/util/stream/Stream", "noneMatch", "(Ljava/util/function/Predicate;)Z"),
        runtime("HashMap.<init>", "java/util/HashMap", "<init>", "()V", "(Ljava/util/Map;)V"),
        runtime("LinkedHashMap.<init>", "java/util/LinkedHashMap", "<init>", "()V"),
        runtime("TreeMap.<init>", "java/util/TreeMap", "<init>", "()V", "(Ljava/util/Map;)V"),
        runtime("Map.copyOf", "java/util/Map", "copyOf", "(Ljava/util/Map;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "()Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.get", "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.computeIfAbsent", "java/util/Map", "computeIfAbsent", "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        runtime("Map.forEach", "java/util/Map", "forEach", "(Ljava/util/function/BiConsumer;)V"),
        runtime("HashMap.get", "java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.get", "java/util/LinkedHashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.get", "java/util/TreeMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.get", "java/util/concurrent/ConcurrentHashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.getOrDefault", "java/util/Map", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.putAll", "java/util/Map", "putAll", "(Ljava/util/Map;)V"),
        runtime("Map.put", "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.putIfAbsent", "java/util/Map", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.putAll", "java/util/HashMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("LinkedHashMap.putAll", "java/util/LinkedHashMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("TreeMap.putAll", "java/util/TreeMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("ConcurrentHashMap.putAll", "java/util/concurrent/ConcurrentHashMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("HashMap.put", "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.put", "java/util/LinkedHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.put", "java/util/TreeMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.put", "java/util/concurrent/ConcurrentHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.putIfAbsent", "java/util/HashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.putIfAbsent", "java/util/LinkedHashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.putIfAbsent", "java/util/TreeMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.putIfAbsent", "java/util/concurrent/ConcurrentHashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.containsKey", "java/util/Map", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("HashMap.containsKey", "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashMap.containsKey", "java/util/LinkedHashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("TreeMap.containsKey", "java/util/TreeMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("ConcurrentHashMap.containsKey", "java/util/concurrent/ConcurrentHashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("Map.size", "java/util/Map", "size", "()I"),
        runtime("HashMap.size", "java/util/HashMap", "size", "()I"),
        runtime("LinkedHashMap.size", "java/util/LinkedHashMap", "size", "()I"),
        runtime("TreeMap.size", "java/util/TreeMap", "size", "()I"),
        runtime("ConcurrentHashMap.size", "java/util/concurrent/ConcurrentHashMap", "size", "()I"),
        runtime("Map.isEmpty", "java/util/Map", "isEmpty", "()Z"),
        runtime("HashMap.isEmpty", "java/util/HashMap", "isEmpty", "()Z"),
        runtime("LinkedHashMap.isEmpty", "java/util/LinkedHashMap", "isEmpty", "()Z"),
        runtime("TreeMap.isEmpty", "java/util/TreeMap", "isEmpty", "()Z"),
        runtime("ConcurrentHashMap.isEmpty", "java/util/concurrent/ConcurrentHashMap", "isEmpty", "()Z"),
        runtime("Map.entrySet", "java/util/Map", "entrySet", "()Ljava/util/Set;"),
        runtime("HashMap.entrySet", "java/util/HashMap", "entrySet", "()Ljava/util/Set;"),
        runtime("LinkedHashMap.entrySet", "java/util/LinkedHashMap", "entrySet", "()Ljava/util/Set;"),
        runtime("TreeMap.entrySet", "java/util/TreeMap", "entrySet", "()Ljava/util/Set;"),
        runtime("ConcurrentHashMap.entrySet", "java/util/concurrent/ConcurrentHashMap", "entrySet", "()Ljava/util/Set;"),
        runtime("Map.values", "java/util/Map", "values", "()Ljava/util/Collection;"),
        runtime("HashMap.values", "java/util/HashMap", "values", "()Ljava/util/Collection;"),
        runtime("LinkedHashMap.values", "java/util/LinkedHashMap", "values", "()Ljava/util/Collection;"),
        runtime("TreeMap.values", "java/util/TreeMap", "values", "()Ljava/util/Collection;"),
        runtime("ConcurrentHashMap.values", "java/util/concurrent/ConcurrentHashMap", "values", "()Ljava/util/Collection;"),
        runtime("Map.Entry.getKey", "java/util/Map$Entry", "getKey", "()Ljava/lang/Object;"),
        runtime("Map.Entry.getValue", "java/util/Map$Entry", "getValue", "()Ljava/lang/Object;"),
        runtime("HashMap.getOrDefault", "java/util/HashMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.getOrDefault", "java/util/LinkedHashMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.getOrDefault", "java/util/TreeMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.getOrDefault", "java/util/concurrent/ConcurrentHashMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Path.of", "java/nio/file/Path", "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"),
        runtime("Paths.get", "java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"),
        runtime("Path.resolve", "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;"),
        runtime("Path.resolve", "java/nio/file/Path", "resolve", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;"),
        runtime("Path.toAbsolutePath", "java/nio/file/Path", "toAbsolutePath", "()Ljava/nio/file/Path;"),
        runtime("Path.normalize", "java/nio/file/Path", "normalize", "()Ljava/nio/file/Path;"),
        runtime("Path.getParent", "java/nio/file/Path", "getParent", "()Ljava/nio/file/Path;"),
        runtime("Path.getFileName", "java/nio/file/Path", "getFileName", "()Ljava/nio/file/Path;"),
        runtime("Path.relativize", "java/nio/file/Path", "relativize", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;"),
        runtime("Path.startsWith", "java/nio/file/Path", "startsWith", "(Ljava/nio/file/Path;)Z"),
        runtime("Path.equals", "java/nio/file/Path", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Path.isAbsolute", "java/nio/file/Path", "isAbsolute", "()Z"),
        runtime("Path.getNameCount", "java/nio/file/Path", "getNameCount", "()I"),
        runtime("Path.getName", "java/nio/file/Path", "getName", "(I)Ljava/nio/file/Path;"),
        runtime("Path.toString", "java/nio/file/Path", "toString", "()Ljava/lang/String;"),
        runtime("Files.exists", "java/nio/file/Files", "exists", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"),
        runtime("Files.isDirectory", "java/nio/file/Files", "isDirectory", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"),
        runtime("Files.isRegularFile", "java/nio/file/Files", "isRegularFile", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"),
        runtime("Files.isExecutable", "java/nio/file/Files", "isExecutable", "(Ljava/nio/file/Path;)Z"),
        runtime("Files.createDirectories", "java/nio/file/Files", "createDirectories", "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"),
        runtime("Files.copy", "java/nio/file/Files", "copy", "(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;"),
        runtime("Files.readString", "java/nio/file/Files", "readString", "(Ljava/nio/file/Path;)Ljava/lang/String;"),
        runtime("Files.writeString", "java/nio/file/Files", "writeString", "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"),
        runtime("Files.write", "java/nio/file/Files", "write", "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"),
        runtime("Files.readAllBytes", "java/nio/file/Files", "readAllBytes", "(Ljava/nio/file/Path;)[B"),
        runtime("Files.deleteIfExists", "java/nio/file/Files", "deleteIfExists", "(Ljava/nio/file/Path;)Z"),
        runtime("Files.size", "java/nio/file/Files", "size", "(Ljava/nio/file/Path;)J"),
        runtime("Files.getLastModifiedTime", "java/nio/file/Files", "getLastModifiedTime", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Ljava/nio/file/attribute/FileTime;"),
        runtime("FileTime.toMillis", "java/nio/file/attribute/FileTime", "toMillis", "()J"),
        runtime("Files.newDirectoryStream", "java/nio/file/Files", "newDirectoryStream", "(Ljava/nio/file/Path;)Ljava/nio/file/DirectoryStream;"),
        runtime("InetAddress.getLoopbackAddress", "java/net/InetAddress", "getLoopbackAddress", "()Ljava/net/InetAddress;"),
        runtime("InetAddress.getHostAddress", "java/net/InetAddress", "getHostAddress", "()Ljava/lang/String;"),
        runtime("InetAddress.getHostName", "java/net/InetAddress", "getHostName", "()Ljava/lang/String;"),
        runtime("InetAddress.getCanonicalHostName", "java/net/InetAddress", "getCanonicalHostName", "()Ljava/lang/String;"),
        runtime("InetSocketAddress.<init>", "java/net/InetSocketAddress", "<init>", "(Ljava/lang/String;I)V", "(Ljava/net/InetAddress;I)V"),
        runtime("InetSocketAddress.getPort", "java/net/InetSocketAddress", "getPort", "()I"),
        runtime("InetSocketAddress.getHostString", "java/net/InetSocketAddress", "getHostString", "()Ljava/lang/String;"),
        runtime("InetSocketAddress.getAddress", "java/net/InetSocketAddress", "getAddress", "()Ljava/net/InetAddress;"),
        runtime("InetSocketAddress.toString", "java/net/InetSocketAddress", "toString", "()Ljava/lang/String;"),
        runtime("Socket.<init>", "java/net/Socket", "<init>", "(Ljava/lang/String;I)V", "(Ljava/net/InetAddress;I)V"),
        runtime("Socket.isConnected", "java/net/Socket", "isConnected", "()Z"),
        runtime("Socket.isClosed", "java/net/Socket", "isClosed", "()Z"),
        runtime("Socket.getPort", "java/net/Socket", "getPort", "()I"),
        runtime("Socket.getLocalPort", "java/net/Socket", "getLocalPort", "()I"),
        runtime("Socket.getInetAddress", "java/net/Socket", "getInetAddress", "()Ljava/net/InetAddress;"),
        runtime("Socket.getInputStream", "java/net/Socket", "getInputStream", "()Ljava/io/InputStream;"),
        runtime("Socket.getOutputStream", "java/net/Socket", "getOutputStream", "()Ljava/io/OutputStream;"),
        runtime("Socket.close", "java/net/Socket", "close", "()V"),
        runtime("ServerSocket.<init>", "java/net/ServerSocket", "<init>", "(I)V"),
        runtime("ServerSocket.getLocalPort", "java/net/ServerSocket", "getLocalPort", "()I"),
        runtime("ServerSocket.accept", "java/net/ServerSocket", "accept", "()Ljava/net/Socket;"),
        runtime("ServerSocket.close", "java/net/ServerSocket", "close", "()V"),
        runtime("URI.create", "java/net/URI", "create", "(Ljava/lang/String;)Ljava/net/URI;"),
        runtime("URI.getPath", "java/net/URI", "getPath", "()Ljava/lang/String;"),
        runtime("URI.getQuery", "java/net/URI", "getQuery", "()Ljava/lang/String;"),
        runtime("HttpExchange.getRequestURI", "com/sun/net/httpserver/HttpExchange", "getRequestURI", "()Ljava/net/URI;"),
        runtime("HttpExchange.getRequestMethod", "com/sun/net/httpserver/HttpExchange", "getRequestMethod", "()Ljava/lang/String;"),
        runtime("HttpExchange.getRequestHeaders", "com/sun/net/httpserver/HttpExchange", "getRequestHeaders", "()Lcom/sun/net/httpserver/Headers;"),
        runtime("HttpExchange.getRequestBody", "com/sun/net/httpserver/HttpExchange", "getRequestBody", "()Ljava/io/InputStream;"),
        runtime("Headers.getFirst", "com/sun/net/httpserver/Headers", "getFirst", "(Ljava/lang/String;)Ljava/lang/String;"),
        runtime("HttpRequest.<init>", "java/net/http/HttpRequest", "<init>", "()V"),
        runtime("HttpClient.newHttpClient", "java/net/http/HttpClient", "newHttpClient", "()Ljava/net/http/HttpClient;"),
        runtime("HttpRequest.newBuilder", "java/net/http/HttpRequest", "newBuilder", "(Ljava/net/URI;)Ljava/net/http/HttpRequest$Builder;"),
        runtime("HttpRequest.Builder.GET", "java/net/http/HttpRequest$Builder", "GET", "()Ljava/net/http/HttpRequest$Builder;"),
        runtime("HttpRequest.Builder.header", "java/net/http/HttpRequest$Builder", "header", "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/http/HttpRequest$Builder;"),
        runtime("HttpRequest.Builder.POST", "java/net/http/HttpRequest$Builder", "POST", "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;"),
        runtime("HttpRequest.Builder.PUT", "java/net/http/HttpRequest$Builder", "PUT", "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;"),
        runtime("HttpRequest.Builder.build", "java/net/http/HttpRequest$Builder", "build", "()Ljava/net/http/HttpRequest;"),
        runtime("HttpRequest.BodyPublishers.ofString", "java/net/http/HttpRequest$BodyPublishers", "ofString", "(Ljava/lang/String;)Ljava/net/http/HttpRequest$BodyPublisher;"),
        runtime("HttpRequest.BodyPublishers.ofByteArray", "java/net/http/HttpRequest$BodyPublishers", "ofByteArray", "([B)Ljava/net/http/HttpRequest$BodyPublisher;"),
        runtime("HttpResponse.BodyHandlers.ofString", "java/net/http/HttpResponse$BodyHandlers", "ofString", "()Ljava/net/http/HttpResponse$BodyHandler;"),
        runtime("HttpResponse.BodyHandlers.ofByteArray", "java/net/http/HttpResponse$BodyHandlers", "ofByteArray", "()Ljava/net/http/HttpResponse$BodyHandler;"),
        runtime("HttpClient.send", "java/net/http/HttpClient", "send", "(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;"),
        runtime("HttpResponse.statusCode", "java/net/http/HttpResponse", "statusCode", "()I"),
        runtime("HttpResponse.body", "java/net/http/HttpResponse", "body", "()Ljava/lang/Object;"),
        runtime("Charset.defaultCharset", "java/nio/charset/Charset", "defaultCharset", "()Ljava/nio/charset/Charset;"),
        runtime("Charset.name", "java/nio/charset/Charset", "name", "()Ljava/lang/String;"),
        runtime("URLDecoder.decode", "java/net/URLDecoder", "decode", "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;"),
        runtime("String.getBytes(Charset)", "java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B"),
        runtime("String.<init>(byte[],Charset)", "java/lang/String", "<init>", "([BLjava/nio/charset/Charset;)V"),
        runtime("String.<init>(byte[],int,int,Charset)", "java/lang/String", "<init>", "([BIILjava/nio/charset/Charset;)V"),
        runtime("InputStream.read", "java/io/InputStream", "read", "()I", "([B)I", "([BII)I"),
        runtime("InputStream.readAllBytes", "java/io/InputStream", "readAllBytes", "()[B"),
        runtime("InputStream.close", "java/io/InputStream", "close", "()V"),
        runtime("OutputStream.write", "java/io/OutputStream", "write", "(I)V", "([B)V", "([BII)V"),
        runtime("OutputStream.flush", "java/io/OutputStream", "flush", "()V"),
        runtime("OutputStream.close", "java/io/OutputStream", "close", "()V"),
        runtime("Iterable.iterator", "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;"),
        runtime("DirectoryStream.iterator", "java/nio/file/DirectoryStream", "iterator", "()Ljava/util/Iterator;"),
        runtime("DirectoryStream.close", "java/nio/file/DirectoryStream", "close", "()V"),
        runtime("Optional.empty", "java/util/Optional", "empty", "()Ljava/util/Optional;"),
        runtime("Optional.of", "java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;"),
        runtime("Optional.ofNullable", "java/util/Optional", "ofNullable", "(Ljava/lang/Object;)Ljava/util/Optional;"),
        runtime("Optional.isPresent", "java/util/Optional", "isPresent", "()Z"),
        runtime("Optional.isEmpty", "java/util/Optional", "isEmpty", "()Z"),
        runtime("Optional.get", "java/util/Optional", "get", "()Ljava/lang/Object;"),
        runtime("Optional.orElse", "java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Optional.or", "java/util/Optional", "or", "(Ljava/util/function/Supplier;)Ljava/util/Optional;"),
        runtime("Optional.orElseGet", "java/util/Optional", "orElseGet", "(Ljava/util/function/Supplier;)Ljava/lang/Object;"),
        runtime("Optional.orElseThrow", "java/util/Optional", "orElseThrow", "()Ljava/lang/Object;"),
        runtime("Optional.filter", "java/util/Optional", "filter", "(Ljava/util/function/Predicate;)Ljava/util/Optional;"),
        runtime("Optional.ifPresent", "java/util/Optional", "ifPresent", "(Ljava/util/function/Consumer;)V"),
        runtime("Optional.map", "java/util/Optional", "map", "(Ljava/util/function/Function;)Ljava/util/Optional;"),
        runtime("Optional.flatMap", "java/util/Optional", "flatMap", "(Ljava/util/function/Function;)Ljava/util/Optional;"),
        runtime("Optional.ifPresentOrElse", "java/util/Optional", "ifPresentOrElse", "(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V"),
        runtime("OptionalInt.orElse", "java/util/OptionalInt", "orElse", "(I)I")
    );

    private JdkCallSupport() {
    }

    /**
     * Checks whether a method owner belongs to the JDK namespace.
     *
     * @param methodRef method reference
     * @return true for java, jdk, and sun owners
     */
    public static boolean isJdkCall(final MethodRef methodRef) {
        if (methodRef.owner().startsWith("java/")) {
            return true;
        }
        if (methodRef.owner().startsWith("jdk/")) {
            return true;
        }
        return methodRef.owner().startsWith("sun/");
    }

    /**
     * Checks whether a JDK call has a native backend implementation.
     *
     * @param methodRef method reference
     * @return true when the verifier and lowering both support the call
     */
    public static boolean isSupported(final MethodRef methodRef) {
        if (isDirectlySupported(methodRef)) {
            return true;
        }
        if (supportedCall(methodRef).isPresent()) {
            return true;
        }
        if (isSupportedThrowableCall(methodRef)) {
            return true;
        }
        return isNoopPlatformConstructor(methodRef);
    }

    private static boolean isDirectlySupported(final MethodRef methodRef) {
        if (isSupportedClosedWorldDispatchCall(methodRef)) {
            return true;
        }
        if ("java/util/List".equals(methodRef.owner())) {
            return isSupportedListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/ArrayList".equals(methodRef.owner())) {
            return isSupportedArrayListCall(methodRef.name(), methodRef.descriptor())
                || isSupportedExactArrayListInheritedCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/concurrent/CopyOnWriteArrayList".equals(methodRef.owner())) {
            return isSupportedArrayListCall(methodRef.name(), methodRef.descriptor())
                || isSupportedListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/lang/Enum".equals(methodRef.owner())) {
            return "valueOf".equals(methodRef.name())
                && "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;".equals(methodRef.descriptor());
        }
        if ("java/util/Collections".equals(methodRef.owner())) {
            return ("emptyList".equals(methodRef.name()) && "()Ljava/util/List;".equals(methodRef.descriptor()))
                || ("emptyMap".equals(methodRef.name()) && "()Ljava/util/Map;".equals(methodRef.descriptor()))
                || ("singletonList".equals(methodRef.name()) && "(Ljava/lang/Object;)Ljava/util/List;".equals(methodRef.descriptor()))
                || ("unmodifiableList".equals(methodRef.name()) && "(Ljava/util/List;)Ljava/util/List;".equals(methodRef.descriptor()));
        }
        if ("java/util/Collection".equals(methodRef.owner())) {
            return isSupportedCollectionCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/io/ByteArrayInputStream".equals(methodRef.owner())
            || "java/io/ByteArrayOutputStream".equals(methodRef.owner())) {
            return "close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor());
        }
        if ("java/util/HashSet".equals(methodRef.owner()) || "java/util/LinkedHashSet".equals(methodRef.owner())) {
            return isSupportedConcreteSetCall(methodRef.name(), methodRef.descriptor());
        }
        if (isSetRuntimeOwner(methodRef.owner())) {
            return isSupportedSetCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Iterator".equals(methodRef.owner())) {
            return isSupportedIteratorCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/stream/Stream".equals(methodRef.owner())) {
            return isSupportedStreamCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Comparator".equals(methodRef.owner())) {
            return isSupportedComparatorCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/stream/Collectors".equals(methodRef.owner())) {
            return isSupportedCollectorsCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/stream/IntStream".equals(methodRef.owner())) {
            return isSupportedIntStreamCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Map".equals(methodRef.owner())) {
            return isSupportedMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/HashMap".equals(methodRef.owner())) {
            return isSupportedHashMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/LinkedHashMap".equals(methodRef.owner())) {
            return isSupportedHashMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/TreeMap".equals(methodRef.owner())) {
            return isSupportedHashMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(methodRef.owner())) {
            return isSupportedHashMapCall(methodRef.name(), methodRef.descriptor())
                || isSupportedConcurrentHashMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Map$Entry".equals(methodRef.owner())) {
            return isSupportedMapEntryCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/nio/file/Path".equals(methodRef.owner())) {
            return isSupportedPathCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/nio/file/Paths".equals(methodRef.owner())) {
            return "get".equals(methodRef.name())
                && "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodRef.descriptor());
        }
        if ("java/nio/file/DirectoryStream".equals(methodRef.owner())) {
            return isSupportedDirectoryStreamCall(methodRef.name(), methodRef.descriptor());
        }
        return false;
    }

    /**
     * Returns whether this JDK-owned single-abstract-method call can be lowered as closed-world
     * interface dispatch instead of requiring a dedicated JDK runtime intrinsic.
     *
     * @param methodRef invoked JDK method
     * @return {@code true} when javan may lower the call through concrete closed-world targets
     */
    public static boolean isSupportedClosedWorldDispatchCall(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("java/lang/Runnable".equals(owner)) {
            return "run".equals(name) && "()V".equals(descriptor);
        }
        if ("java/util/function/Consumer".equals(owner)) {
            return "accept".equals(name) && "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("java/util/function/BiConsumer".equals(owner)) {
            return "accept".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("java/util/function/BooleanSupplier".equals(owner)) {
            return "getAsBoolean".equals(name) && "()Z".equals(descriptor);
        }
        if ("java/util/function/Function".equals(owner)) {
            return "apply".equals(name) && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("java/util/function/BiFunction".equals(owner)) {
            return "apply".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("java/util/function/BinaryOperator".equals(owner)) {
            return "apply".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("java/util/function/ToIntFunction".equals(owner)) {
            return "applyAsInt".equals(name) && "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("java/util/function/IntUnaryOperator".equals(owner)) {
            return "applyAsInt".equals(name) && "(I)I".equals(descriptor);
        }
        if ("java/util/function/Predicate".equals(owner)) {
            return "test".equals(name) && "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("java/util/function/Supplier".equals(owner)) {
            return "get".equals(name) && "()Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    /**
     * Returns callback interface methods that a supported JDK higher-order call lowers through
     * closed-world dispatch.
     *
     * @param methodRef supported JDK call
     * @return callback interface methods lowered through closed-world dispatch
     */
    public static List<MethodRef> closedWorldHigherOrderDispatchTargets(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if (("java/util/List".equals(owner) || "java/util/Collection".equals(owner))
            && "forEach".equals(name)
            && "(Ljava/util/function/Consumer;)V".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"));
        }
        if ("java/util/Map".equals(owner)
            && "computeIfAbsent".equals(name)
            && "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
        }
        if ("java/util/Map".equals(owner)
            && "forEach".equals(name)
            && "(Ljava/util/function/BiConsumer;)V".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
        }
        if ("java/util/stream/Stream".equals(owner)) {
            if ("forEach".equals(name) && "(Ljava/util/function/Consumer;)V".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"));
            }
            if ("filter".equals(name) && "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"));
            }
            if ("map".equals(name) && "(Ljava/util/function/Function;)Ljava/util/stream/Stream;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
            }
            if ("mapToInt".equals(name) && "(Ljava/util/function/ToIntFunction;)Ljava/util/stream/IntStream;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/ToIntFunction", "applyAsInt", "(Ljava/lang/Object;)I"));
            }
            if ("anyMatch".equals(name) && "(Ljava/util/function/Predicate;)Z".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"));
            }
            if ("noneMatch".equals(name) && "(Ljava/util/function/Predicate;)Z".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"));
            }
        }
        if ("java/util/Comparator".equals(owner)
            && "comparing".equals(name)
            && "(Ljava/util/function/Function;Ljava/util/Comparator;)Ljava/util/Comparator;".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
        }
        if ("java/util/stream/Collectors".equals(owner)
            && "toCollection".equals(name)
            && "(Ljava/util/function/Supplier;)Ljava/util/stream/Collector;".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"));
        }
        if ("java/util/stream/Collectors".equals(owner)
            && "toMap".equals(name)
            && "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/BinaryOperator;Ljava/util/function/Supplier;)Ljava/util/stream/Collector;"
            .equals(descriptor)) {
            return List.of(
                new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                new MethodRef("java/util/function/BinaryOperator", "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;")
            );
        }
        if ("java/util/stream/Collectors".equals(owner)
            && "groupingBy".equals(name)
            && "(Ljava/util/function/Function;Ljava/util/stream/Collector;)Ljava/util/stream/Collector;".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
        }
        if ("java/util/Optional".equals(owner)) {
            if ("or".equals(name) && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"));
            }
            if ("orElseGet".equals(name) && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;"));
            }
            if ("filter".equals(name) && "(Ljava/util/function/Predicate;)Ljava/util/Optional;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"));
            }
            if ("ifPresent".equals(name) && "(Ljava/util/function/Consumer;)V".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"));
            }
            if ("map".equals(name) && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
            }
            if ("flatMap".equals(name) && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(descriptor)) {
                return List.of(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
            }
            if ("ifPresentOrElse".equals(name) && "(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V".equals(descriptor)) {
                return List.of(
                    new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
                    new MethodRef("java/lang/Runnable", "run", "()V")
                );
            }
        }
        if ("java/util/concurrent/atomic/AtomicInteger".equals(owner)
            && "updateAndGet".equals(name)
            && "(Ljava/util/function/IntUnaryOperator;)I".equals(descriptor)) {
            return List.of(new MethodRef("java/util/function/IntUnaryOperator", "applyAsInt", "(I)I"));
        }
        return List.of();
    }

    private static boolean isSupportedListCall(final String name, final String descriptor) {
        if ("of".equals(name)) {
            return descriptor.endsWith(")Ljava/util/List;");
        }
        if ("copyOf".equals(name)) {
            return "(Ljava/util/Collection;)Ljava/util/List;".equals(descriptor);
        }
        if ("add".equals(name) && "(Ljava/lang/Object;)Z".equals(descriptor)) {
            return true;
        }
        if ("add".equals(name) && "(ILjava/lang/Object;)V".equals(descriptor)) {
            return true;
        }
        if ("addAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("isEmpty".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("contains".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("containsAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("get".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor);
        }
        if ("getFirst".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("getLast".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("removeLast".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("set".equals(name)) {
            return "(ILjava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("addFirst".equals(name)) {
            return "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("forEach".equals(name)) {
            return "(Ljava/util/function/Consumer;)V".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("toArray".equals(name)) {
            return "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedArrayListCall(final String name, final String descriptor) {
        if ("<init>".equals(name) && "()V".equals(descriptor)) {
            return true;
        }
        if ("<init>".equals(name) && "(I)V".equals(descriptor)) {
            return true;
        }
        if ("<init>".equals(name) && "(Ljava/util/Collection;)V".equals(descriptor)) {
            return true;
        }
        if ("add".equals(name) && "(Ljava/lang/Object;)Z".equals(descriptor)) {
            return true;
        }
        if ("add".equals(name) && "(ILjava/lang/Object;)V".equals(descriptor)) {
            return true;
        }
        if ("reversed".equals(name) && "()Ljava/util/List;".equals(descriptor)) {
            return true;
        }
        if (!"addAll".equals(name)) {
            return false;
        }
        return "(Ljava/util/Collection;)Z".equals(descriptor);
    }

    private static boolean isSupportedExactArrayListInheritedCall(final String name, final String descriptor) {
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("isEmpty".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("contains".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("get".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("stream".equals(name)) {
            return "()Ljava/util/stream/Stream;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedCollectionCall(final String name, final String descriptor) {
        if ("add".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("contains".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("stream".equals(name)) {
            return "()Ljava/util/stream/Stream;".equals(descriptor);
        }
        if ("forEach".equals(name)) {
            return "(Ljava/util/function/Consumer;)V".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedSetCall(final String name, final String descriptor) {
        if ("add".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("isEmpty".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("contains".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("toArray".equals(name)) {
            return "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(descriptor);
        }
        if ("stream".equals(name)) {
            return "()Ljava/util/stream/Stream;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedConcreteSetCall(final String name, final String descriptor) {
        if ("<init>".equals(name)) {
            return "()V".equals(descriptor);
        }
        return isSupportedSetCall(name, descriptor);
    }

    private static boolean isSupportedStreamCall(final String name, final String descriptor) {
        if ("forEach".equals(name)) {
            return "(Ljava/util/function/Consumer;)V".equals(descriptor);
        }
        if ("filter".equals(name)) {
            return "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;".equals(descriptor);
        }
        if ("map".equals(name)) {
            return "(Ljava/util/function/Function;)Ljava/util/stream/Stream;".equals(descriptor);
        }
        if ("mapToInt".equals(name)) {
            return "(Ljava/util/function/ToIntFunction;)Ljava/util/stream/IntStream;".equals(descriptor);
        }
        if ("sorted".equals(name)) {
            return "(Ljava/util/Comparator;)Ljava/util/stream/Stream;".equals(descriptor);
        }
        if ("toList".equals(name)) {
            return "()Ljava/util/List;".equals(descriptor);
        }
        if ("findFirst".equals(name)) {
            return "()Ljava/util/Optional;".equals(descriptor);
        }
        if ("anyMatch".equals(name) || "noneMatch".equals(name)) {
            return "(Ljava/util/function/Predicate;)Z".equals(descriptor);
        }
        if ("toArray".equals(name)) {
            return "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedComparatorCall(final String name, final String descriptor) {
        if ("reverseOrder".equals(name)) {
            return "()Ljava/util/Comparator;".equals(descriptor);
        }
        if ("comparing".equals(name)) {
            return "(Ljava/util/function/Function;Ljava/util/Comparator;)Ljava/util/Comparator;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedCollectorsCall(final String name, final String descriptor) {
        return false;
    }

    private static boolean isSupportedIntStreamCall(final String name, final String descriptor) {
        if ("max".equals(name)) {
            return "()Ljava/util/OptionalInt;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedIteratorCall(final String name, final String descriptor) {
        if ("hasNext".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("next".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedMapCall(final String name, final String descriptor) {
        if ("copyOf".equals(name)) {
            return "(Ljava/util/Map;)Ljava/util/Map;".equals(descriptor);
        }
        if ("of".equals(name)) {
            return "()Ljava/util/Map;".equals(descriptor)
                || "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor);
        }
        if ("get".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("computeIfAbsent".equals(name)) {
            return "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("forEach".equals(name)) {
            return "(Ljava/util/function/BiConsumer;)V".equals(descriptor);
        }
        if ("getOrDefault".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("putAll".equals(name)) {
            return "(Ljava/util/Map;)V".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("clear".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("put".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("putIfAbsent".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("containsKey".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("isEmpty".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("values".equals(name)) {
            return "()Ljava/util/Collection;".equals(descriptor);
        }
        if ("entrySet".equals(name)) {
            return "()Ljava/util/Set;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedMapEntryCall(final String name, final String descriptor) {
        if ("getKey".equals(name) || "getValue".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedHashMapCall(final String name, final String descriptor) {
        if ("<init>".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("get".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("put".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("putIfAbsent".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("putAll".equals(name)) {
            return "(Ljava/util/Map;)V".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("clear".equals(name)) {
            return "()V".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedConcurrentHashMapCall(final String name, final String descriptor) {
        if ("containsKey".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("isEmpty".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("entrySet".equals(name)) {
            return "()Ljava/util/Set;".equals(descriptor);
        }
        if ("values".equals(name)) {
            return "()Ljava/util/Collection;".equals(descriptor);
        }
        if ("getOrDefault".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedPathCall(final String name, final String descriptor) {
        if ("of".equals(name)) {
            return "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("resolve".equals(name) && "(Ljava/lang/String;)Ljava/nio/file/Path;".equals(descriptor)) {
            return true;
        }
        if ("resolve".equals(name) && "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(descriptor)) {
            return true;
        }
        if ("toAbsolutePath".equals(name)) {
            return "()Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("normalize".equals(name)) {
            return "()Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("getParent".equals(name)) {
            return "()Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("getFileName".equals(name)) {
            return "()Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("relativize".equals(name)) {
            return "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("startsWith".equals(name)) {
            return "(Ljava/nio/file/Path;)Z".equals(descriptor);
        }
        if ("equals".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("isAbsolute".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("getNameCount".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("getName".equals(name)) {
            return "(I)Ljava/nio/file/Path;".equals(descriptor);
        }
        if ("toString".equals(name)) {
            return "()Ljava/lang/String;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedDirectoryStreamCall(final String name, final String descriptor) {
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("close".equals(name)) {
            return "()V".equals(descriptor);
        }
        return false;
    }

    /**
     * Lists exact supported intrinsics in deterministic report order.
     *
     * @return intrinsic calls
     */
    public static List<SupportedCall> intrinsics() {
        final java.util.ArrayList<SupportedCall> result = new java.util.ArrayList<>();
        for (final SupportedCall call : SUPPORTED_CALLS) {
            if (call.kind() == Kind.INTRINSIC) {
                result.add(call);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Lists exact supported runtime-registry calls in deterministic report order.
     *
     * @return runtime-registry calls
     */
    public static List<SupportedCall> runtimes() {
        final java.util.ArrayList<SupportedCall> result = new java.util.ArrayList<>();
        for (final SupportedCall call : SUPPORTED_CALLS) {
            if (call.kind() == Kind.RUNTIME) {
                result.add(call);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Finds the exact supported call metadata.
     *
     * @param methodRef method reference
     * @return supported call metadata
     */
    public static Optional<SupportedCall> supportedCall(final MethodRef methodRef) {
        for (final SupportedCall call : SUPPORTED_CALLS) {
            if (call.matches(methodRef)) {
                return Optional.of(call);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns runtime modules required by a reachable JDK call.
     *
     * @param methodRef method reference
     * @return ordered runtime modules
     */
    public static List<String> runtimeModules(final MethodRef methodRef) {
        final List<String> network = NetworkApiSupport.runtimeModules(methodRef);
        if (!network.isEmpty()) {
            return network;
        }
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("java/util/stream/Stream".equals(owner)
            && "collect".equals(name)
            && "(Ljava/util/stream/Collector;)Ljava/lang/Object;".equals(descriptor)) {
            return List.of("collections");
        }
        if ("java/util/stream/Collectors".equals(owner)) {
            if ("toList".equals(name) && "()Ljava/util/stream/Collector;".equals(descriptor)) {
                return List.of("collections");
            }
            if ("toCollection".equals(name)
                && "(Ljava/util/function/Supplier;)Ljava/util/stream/Collector;".equals(descriptor)) {
                return List.of("collections");
            }
            if ("toMap".equals(name)
                && "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/BinaryOperator;Ljava/util/function/Supplier;)Ljava/util/stream/Collector;"
                .equals(descriptor)) {
                return List.of("collections");
            }
            if ("counting".equals(name) && "()Ljava/util/stream/Collector;".equals(descriptor)) {
                return List.of("collections");
            }
            if ("groupingBy".equals(name)
                && "(Ljava/util/function/Function;Ljava/util/stream/Collector;)Ljava/util/stream/Collector;".equals(descriptor)) {
                return List.of("collections");
            }
            if ("joining".equals(name)
                && ("()Ljava/util/stream/Collector;".equals(descriptor)
                || "(Ljava/lang/CharSequence;)Ljava/util/stream/Collector;".equals(descriptor)
                || "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/util/stream/Collector;".equals(descriptor))) {
                return List.of("collections");
            }
        }
        if (!isSupported(methodRef)) {
            return List.of();
        }
        if ("java/lang/System".equals(owner)) {
            return systemRuntimeModules(name);
        }
        if ("java/lang/Math".equals(owner)) {
            return List.of("math");
        }
        if ("java/lang/Runtime".equals(owner)) {
            if ("addShutdownHook".equals(name) || "removeShutdownHook".equals(name)) {
                return List.of("management", "threads");
            }
            if ("exit".equals(name)) {
                return List.of("management", "process", "threads");
            }
            return List.of("management");
        }
        if ("java/lang/management/ManagementFactory".equals(owner)) {
            return List.of("management");
        }
        if ("java/lang/management/ThreadMXBean".equals(owner)) {
            return List.of("management");
        }
        if ("java/lang/management/RuntimeMXBean".equals(owner)) {
            return List.of("management");
        }
        if ("java/lang/management/MemoryMXBean".equals(owner)) {
            return List.of("management");
        }
        if ("java/lang/management/MemoryUsage".equals(owner)) {
            return List.of("management");
        }
        if ("java/lang/management/OperatingSystemMXBean".equals(owner)) {
            return List.of("management");
        }
        if ("com/sun/management/OperatingSystemMXBean".equals(owner)) {
            return List.of("management");
        }
        if ("java/lang/ProcessHandle".equals(owner)) {
            return List.of("process");
        }
        if ("java/lang/Thread".equals(owner)) {
            return List.of("threads");
        }
        if ("java/lang/Thread$Builder".equals(owner)) {
            return List.of("threads");
        }
        if ("java/lang/Thread$Builder$OfVirtual".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/Executors".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/Executor".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/ExecutorService".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/locks/LockSupport".equals(owner)) {
            return List.of("threads");
        }
        if ("java/lang/ThreadLocal".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/atomic/AtomicBoolean".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/atomic/AtomicInteger".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/atomic/AtomicLong".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/atomic/AtomicReference".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/ThreadFactory".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/Arrays".equals(owner)) {
            return List.of("arrays");
        }
        if (isStringRuntimeOwner(owner) || isNumberToStringCall(owner, name)) {
            return List.of("strings");
        }
        if ("java/lang/Long".equals(owner)
            && "parseLong".equals(name)
            && "(Ljava/lang/String;)J".equals(descriptor)) {
            return List.of("strings");
        }
        if (isBoxedPrimitiveOwner(owner)) {
            return List.of("managed-heap");
        }
        if ("java/time/Duration".equals(owner)) {
            return List.of("time");
        }
        if ("java/time/ZoneId".equals(owner)
            || "java/time/Instant".equals(owner)
            || "java/time/OffsetDateTime".equals(owner)
            || "java/time/LocalDate".equals(owner)
            || "java/time/LocalTime".equals(owner)
            || "java/time/LocalDateTime".equals(owner)
            || "java/time/ZonedDateTime".equals(owner)
            || "java/util/Calendar".equals(owner)
            || "java/util/Date".equals(owner)
            || "java/sql/Date".equals(owner)
            || "java/sql/Time".equals(owner)
            || "java/sql/Timestamp".equals(owner)) {
            return List.of("time");
        }
        if ("java/time/format/DateTimeFormatterBuilder".equals(owner)) {
            return List.of("time");
        }
        if ("java/time/format/DateTimeFormatter".equals(owner)) {
            return List.of("time");
        }
        if ("java/util/logging/Level".equals(owner)) {
            return List.of("strings");
        }
        if ("java/util/logging/LogRecord".equals(owner)) {
            return List.of("time", "strings", "exceptions");
        }
        if ("java/util/logging/Formatter".equals(owner)) {
            return List.of("strings");
        }
        if ("java/text/SimpleDateFormat".equals(owner)) {
            return List.of("time", "strings");
        }
        if ("java/util/UUID".equals(owner)) {
            return List.of("random", "strings");
        }
        if ("java/nio/file/attribute/FileTime".equals(owner)) {
            return List.of("filesystem", "time");
        }
        if ("java/util/stream/IntStream".equals(owner)
            && "max".equals(name)
            && "()Ljava/util/OptionalInt;".equals(methodRef.descriptor())) {
            return List.of("collections", "optional");
        }
        if ("java/util/stream/Stream".equals(owner)
            && "sorted".equals(name)
            && "(Ljava/util/Comparator;)Ljava/util/stream/Stream;".equals(descriptor)) {
            return List.of("collections", "managed-heap", "strings");
        }
        if ("java/util/Comparator".equals(owner)) {
            if ("reverseOrder".equals(name) && "()Ljava/util/Comparator;".equals(descriptor)) {
                return List.of("collections", "managed-heap", "strings");
            }
            if ("comparing".equals(name)
                && "(Ljava/util/function/Function;Ljava/util/Comparator;)Ljava/util/Comparator;".equals(descriptor)) {
                return List.of("collections", "managed-heap", "strings");
            }
        }
        if ("java/util/stream/Collectors".equals(owner)) {
            return List.of("collections");
        }
        if (isFileRuntimeOwner(owner)) {
            return List.of("filesystem");
        }
        if (isCollectionRuntimeOwner(owner)) {
            return List.of("collections");
        }
        if (isMapRuntimeOwner(owner)) {
            return List.of("maps");
        }
        if ("java/util/Optional".equals(owner)) {
            return List.of("optional");
        }
        if ("java/util/OptionalInt".equals(owner)) {
            return List.of("optional");
        }
        if ("java/io/PrintStream".equals(owner)) {
            return List.of("io");
        }
        if (isPlatformThrowable(owner)) {
            return List.of("exceptions");
        }
        return List.of();
    }

    /**
     * Checks whether an owner is one of the throwable classes handled by the panic runtime.
     *
     * @param owner JVM internal owner
     * @return true when supported as a throwable
     */
    public static boolean isPlatformThrowable(final String owner) {
        if ("java/lang/Throwable".equals(owner)) {
            return true;
        }
        if (!startsWithAscii(owner, "java/")) {
            if (!startsWithAscii(owner, "javax/")) {
                return false;
            }
        }
        if (endsWithAscii(owner, "Exception")) {
            return true;
        }
        return endsWithAscii(owner, "Error");
    }

    /**
     * Checks whether a supported platform throwable can be caught by the requested catch type.
     *
     * @param thrownType JVM internal name for the directly thrown type
     * @param catchType JVM internal name for the catch type
     * @return true when the native exception router can prove assignability
     */
    public static boolean isPlatformThrowableAssignable(final String thrownType, final String catchType) {
        if (!isPlatformThrowable(thrownType)) {
            return false;
        }
        if (!isPlatformThrowable(catchType)) {
            return false;
        }
        String current = thrownType;
        while (!current.isEmpty()) {
            if (current.equals(catchType)) {
                return true;
            }
            current = platformThrowableParent(current);
        }
        return false;
    }

    private static String platformThrowableParent(final String owner) {
        if ("java/lang/Throwable".equals(owner)) {
            return "";
        }
        for (final String[] parent : PLATFORM_THROWABLE_PARENTS) {
            if (owner.equals(parent[0])) {
                return parent[1];
            }
        }
        if (endsWithAscii(owner, "Exception")) {
            return "java/lang/Exception";
        }
        if (endsWithAscii(owner, "Error")) {
            return "java/lang/Error";
        }
        return "";
    }

    private static boolean startsWithAscii(final String value, final String prefix) {
        if (value.length() < prefix.length()) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (value.charAt(index) != prefix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> systemRuntimeModules(final String name) {
        if ("nanoTime".equals(name) || "currentTimeMillis".equals(name)) {
            return List.of("time");
        }
        if ("lineSeparator".equals(name)) {
            return List.of("strings", "environment");
        }
        if ("getenv".equals(name) || "getProperty".equals(name)) {
            return List.of("environment");
        }
        if ("arraycopy".equals(name)) {
            return List.of("arrays");
        }
        if ("exit".equals(name)) {
            return List.of("process");
        }
        return List.of();
    }

    private static boolean isStringRuntimeOwner(final String owner) {
        if ("java/lang/String".equals(owner)) {
            return true;
        }
        return "java/lang/StringBuilder".equals(owner);
    }

    private static boolean isNumberToStringCall(final String owner, final String name) {
        if (!"toString".equals(name)) {
            return false;
        }
        if ("java/lang/Integer".equals(owner)) {
            return true;
        }
        if ("java/lang/Long".equals(owner)) {
            return true;
        }
        if ("java/lang/Float".equals(owner)) {
            return true;
        }
        return "java/lang/Double".equals(owner);
    }

    private static boolean isBoxedPrimitiveOwner(final String owner) {
        if ("java/lang/Integer".equals(owner)) {
            return true;
        }
        if ("java/lang/Long".equals(owner)) {
            return true;
        }
        if ("java/lang/Float".equals(owner)) {
            return true;
        }
        if ("java/lang/Double".equals(owner)) {
            return true;
        }
        return "java/lang/Boolean".equals(owner);
    }

    private static boolean isFileRuntimeOwner(final String owner) {
        if ("java/nio/file/Path".equals(owner)) {
            return true;
        }
        if ("java/nio/file/Paths".equals(owner)) {
            return true;
        }
        if ("java/nio/file/Files".equals(owner)) {
            return true;
        }
        return "java/nio/file/DirectoryStream".equals(owner);
    }

    private static boolean isCollectionRuntimeOwner(final String owner) {
        if ("java/util/List".equals(owner)) {
            return true;
        }
        if ("java/util/ArrayList".equals(owner)) {
            return true;
        }
        if ("java/util/concurrent/CopyOnWriteArrayList".equals(owner)) {
            return true;
        }
        if ("java/util/Collection".equals(owner)) {
            return true;
        }
        if (isSetRuntimeOwner(owner)) {
            return true;
        }
        if ("java/util/Map$Entry".equals(owner)) {
            return true;
        }
        if ("java/util/stream/Stream".equals(owner)) {
            return true;
        }
        if ("java/util/stream/IntStream".equals(owner)) {
            return true;
        }
        if ("java/lang/Iterable".equals(owner)) {
            return true;
        }
        return "java/util/Iterator".equals(owner);
    }

    private static boolean isMapRuntimeOwner(final String owner) {
        if ("java/util/Map".equals(owner)) {
            return true;
        }
        if ("java/util/HashMap".equals(owner)) {
            return true;
        }
        if ("java/util/LinkedHashMap".equals(owner)) {
            return true;
        }
        if ("java/util/TreeMap".equals(owner)) {
            return true;
        }
        return "java/util/concurrent/ConcurrentHashMap".equals(owner);
    }

    private static boolean isSetRuntimeOwner(final String owner) {
        if ("java/util/Set".equals(owner)) {
            return true;
        }
        if ("java/util/HashSet".equals(owner)) {
            return true;
        }
        if ("java/util/LinkedHashSet".equals(owner)) {
            return true;
        }
        return "java/util/concurrent/ConcurrentHashMap$KeySetView".equals(owner);
    }

    private static boolean endsWithAscii(final String value, final String suffix) {
        if (value.length() < suffix.length()) {
            return false;
        }
        final int offset = value.length() - suffix.length();
        for (int index = 0; index < suffix.length(); index++) {
            if (value.charAt(offset + index) != suffix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether a constructor has no observable runtime work in the current native model.
     *
     * @param methodRef method reference
     * @return true for Object, Record, and supported Enum superclass constructors
     */
    public static boolean isNoopPlatformConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Object".equals(methodRef.owner())) {
            return "()V".equals(methodRef.descriptor());
        }
        if ("java/lang/Record".equals(methodRef.owner())) {
            return "()V".equals(methodRef.descriptor());
        }
        if ("java/lang/Enum".equals(methodRef.owner())) {
            return "(Ljava/lang/String;I)V".equals(methodRef.descriptor());
        }
        if ("java/util/logging/Formatter".equals(methodRef.owner())) {
            return "()V".equals(methodRef.descriptor());
        }
        return false;
    }

    private static boolean isSupportedThrowableCall(final MethodRef methodRef) {
        if (!isPlatformThrowable(methodRef.owner())) {
            return false;
        }
        if ("<init>".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("<init>".equals(methodRef.name()) && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("addSuppressed".equals(methodRef.name()) && "(Ljava/lang/Throwable;)V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("getSuppressed".equals(methodRef.name()) && "()[Ljava/lang/Throwable;".equals(methodRef.descriptor())) {
            return true;
        }
        if ("getStackTrace".equals(methodRef.name()) && "()[Ljava/lang/StackTraceElement;".equals(methodRef.descriptor())) {
            return true;
        }
        if ("setStackTrace".equals(methodRef.name()) && "([Ljava/lang/StackTraceElement;)V".equals(methodRef.descriptor())) {
            return true;
        }
        if (!"getMessage".equals(methodRef.name())) {
            return false;
        }
        if (!"()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return false;
        }
        return true;
    }

    private static SupportedCall intrinsic(
        final String name,
        final String owner,
        final String methodName,
        final String... descriptors
    ) {
        return new SupportedCall(name, owner, methodName, List.of(descriptors), Kind.INTRINSIC);
    }

    private static SupportedCall runtime(
        final String name,
        final String owner,
        final String methodName,
        final String... descriptors
    ) {
        return new SupportedCall(name, owner, methodName, List.of(descriptors), Kind.RUNTIME);
    }

    /**
     * Supported JDK call metadata.
     *
     * @param name report name
     * @param owner JVM owner
     * @param methodName method name
     * @param descriptors exact descriptors
     * @param kind supported call kind
     */
    public record SupportedCall(String name, String owner, String methodName, List<String> descriptors, Kind kind) {
        /**
         * Checks an exact method reference match.
         *
         * @param methodRef method reference
         * @return true when owner, name, and descriptor match
         */
        public boolean matches(final MethodRef methodRef) {
            if (!owner.equals(methodRef.owner())) {
                return false;
            }
            if (!methodName.equals(methodRef.name())) {
                return false;
            }
            if (!containsDescriptor(methodRef.descriptor())) {
                return false;
            }
            return true;
        }

        private boolean containsDescriptor(final String descriptor) {
            for (final String value : descriptors) {
                if (value.equals(descriptor)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Supported JDK call kind.
     */
    public enum Kind {
        INTRINSIC,
        RUNTIME
    }
}
