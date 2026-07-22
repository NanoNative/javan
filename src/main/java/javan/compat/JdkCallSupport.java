package javan.compat;

import javan.classfile.ClassFile;
import javan.classfile.MethodRef;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single source of truth for exact JDK calls that the native backend accepts.
 */
public final class JdkCallSupport {
    public static final int BUILTIN_INSTANCEOF_COLLECTION = 1;
    public static final int BUILTIN_INSTANCEOF_MAP = 2;
    public static final int BUILTIN_INSTANCEOF_MAP_ENTRY = 3;
    public static final int BUILTIN_INSTANCEOF_OBJECT_ARRAY = 4;
    public static final int BUILTIN_INSTANCEOF_INT_ARRAY = 5;
    public static final int BUILTIN_INSTANCEOF_LONG_ARRAY = 6;
    public static final int BUILTIN_INSTANCEOF_FLOAT_ARRAY = 7;
    public static final int BUILTIN_INSTANCEOF_DOUBLE_ARRAY = 8;
    public static final int BUILTIN_INSTANCEOF_BYTE_ARRAY = 9;
    public static final int BUILTIN_INSTANCEOF_BOOLEAN_ARRAY = 10;
    public static final int BUILTIN_INSTANCEOF_SHORT_ARRAY = 11;
    public static final int BUILTIN_INSTANCEOF_CHAR_ARRAY = 12;

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
        intrinsic("Objects.requireNonNull", "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),
        intrinsic("Objects.requireNonNullElse", "java/util/Objects", "requireNonNullElse", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        intrinsic("Objects.requireNonNullElseGet", "java/util/Objects", "requireNonNullElseGet", "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"),
        intrinsic("Objects.isNull", "java/util/Objects", "isNull", "(Ljava/lang/Object;)Z"),
        intrinsic("Objects.nonNull", "java/util/Objects", "nonNull", "(Ljava/lang/Object;)Z"),
        intrinsic("Objects.toString", "java/util/Objects", "toString", "(Ljava/lang/Object;)Ljava/lang/String;", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"),
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
        runtime("Object.equals", "java/lang/Object", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Object.getClass", "java/lang/Object", "getClass", "()Ljava/lang/Class;"),
        runtime("Class.isInstance", "java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z"),
        runtime("Class.cast", "java/lang/Class", "cast", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Class.isEnum", "java/lang/Class", "isEnum", "()Z"),
        runtime("Class.isArray", "java/lang/Class", "isArray", "()Z"),
        runtime("Class.isPrimitive", "java/lang/Class", "isPrimitive", "()Z"),
        runtime("Class.isAssignableFrom", "java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z"),
        runtime("Class.getName", "java/lang/Class", "getName", "()Ljava/lang/String;"),
        runtime("Class.getSimpleName", "java/lang/Class", "getSimpleName", "()Ljava/lang/String;"),
        runtime("Class.getPackageName", "java/lang/Class", "getPackageName", "()Ljava/lang/String;"),
        runtime("Class.getTypeName", "java/lang/Class", "getTypeName", "()Ljava/lang/String;"),
        runtime("Class.getComponentType", "java/lang/Class", "getComponentType", "()Ljava/lang/Class;"),
        runtime("Class.getResourceAsStream", "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;"),
        runtime("ClassLoader.getSystemClassLoader", "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;"),
        runtime("ClassLoader.getSystemResourceAsStream", "java/lang/ClassLoader", "getSystemResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;"),
        runtime("ClassLoader.getResourceAsStream", "java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;"),
        runtime("Class.descriptorString", "java/lang/Class", "descriptorString", "()Ljava/lang/String;"),
        runtime("Class.componentType", "java/lang/Class", "componentType", "()Ljava/lang/Class;"),
        runtime("Class.arrayType", "java/lang/Class", "arrayType", "()Ljava/lang/Class;"),
        runtime("Thread.<init>", "java/lang/Thread", "<init>", "()V", "(Ljava/lang/Runnable;)V", "(Ljava/lang/String;)V", "(Ljava/lang/Runnable;Ljava/lang/String;)V"),
        runtime("Thread.ofVirtual", "java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;"),
        runtime("Thread.startVirtualThread", "java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.name", "java/lang/Thread$Builder", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder;", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;"),
        runtime("Thread.Builder.inheritInheritableThreadLocals", "java/lang/Thread$Builder", "inheritInheritableThreadLocals", "(Z)Ljava/lang/Thread$Builder;"),
        runtime("Thread.Builder.start", "java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.unstarted", "java/lang/Thread$Builder", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.factory", "java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;"),
        runtime("Thread.Builder.toString", "java/lang/Thread$Builder", "toString", "()Ljava/lang/String;"),
        runtime("Thread.Builder.hashCode", "java/lang/Thread$Builder", "hashCode", "()I"),
        runtime("Thread.Builder.equals", "java/lang/Thread$Builder", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Thread.Builder.OfVirtual.name", "java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;"),
        runtime("Thread.Builder.OfVirtual.inheritInheritableThreadLocals", "java/lang/Thread$Builder$OfVirtual", "inheritInheritableThreadLocals", "(Z)Ljava/lang/Thread$Builder$OfVirtual;"),
        runtime("Thread.Builder.OfVirtual.factory", "java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;"),
        runtime("Thread.Builder.OfVirtual.start", "java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.OfVirtual.unstarted", "java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.OfVirtual.toString", "java/lang/Thread$Builder$OfVirtual", "toString", "()Ljava/lang/String;"),
        runtime("Thread.Builder.OfVirtual.hashCode", "java/lang/Thread$Builder$OfVirtual", "hashCode", "()I"),
        runtime("Thread.Builder.OfVirtual.equals", "java/lang/Thread$Builder$OfVirtual", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Executors.newVirtualThreadPerTaskExecutor", "java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;"),
        runtime("Executors.newThreadPerTaskExecutor", "java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"),
        runtime("ThreadFactory.newThread", "java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("ThreadFactory.toString", "java/util/concurrent/ThreadFactory", "toString", "()Ljava/lang/String;"),
        runtime("ThreadFactory.hashCode", "java/util/concurrent/ThreadFactory", "hashCode", "()I"),
        runtime("ThreadFactory.equals", "java/util/concurrent/ThreadFactory", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Executor.execute", "java/util/concurrent/Executor", "execute", "(Ljava/lang/Runnable;)V"),
        runtime("ExecutorService.execute", "java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V"),
        runtime("ExecutorService.submit", "java/util/concurrent/ExecutorService", "submit", "(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"),
        runtime("ExecutorService.shutdown", "java/util/concurrent/ExecutorService", "shutdown", "()V"),
        runtime("ExecutorService.close", "java/util/concurrent/ExecutorService", "close", "()V"),
        runtime("ExecutorService.awaitTermination", "java/util/concurrent/ExecutorService", "awaitTermination", "(JLjava/util/concurrent/TimeUnit;)Z"),
        runtime("ExecutorService.shutdownNow", "java/util/concurrent/ExecutorService", "shutdownNow", "()Ljava/util/List;"),
        runtime("ExecutorService.toString", "java/util/concurrent/ExecutorService", "toString", "()Ljava/lang/String;"),
        runtime("ExecutorService.hashCode", "java/util/concurrent/ExecutorService", "hashCode", "()I"),
        runtime("ExecutorService.equals", "java/util/concurrent/ExecutorService", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Future.cancel", "java/util/concurrent/Future", "cancel", "(Z)Z"),
        runtime("Future.isDone", "java/util/concurrent/Future", "isDone", "()Z"),
        runtime("Future.isCancelled", "java/util/concurrent/Future", "isCancelled", "()Z"),
        runtime("ScheduledThreadPoolExecutor.<init>", "java/util/concurrent/ScheduledThreadPoolExecutor", "<init>", "(I)V"),
        runtime("ScheduledThreadPoolExecutor.<init>", "java/util/concurrent/ScheduledThreadPoolExecutor", "<init>", "(ILjava/util/concurrent/ThreadFactory;Ljava/util/concurrent/RejectedExecutionHandler;)V"),
        runtime("ScheduledThreadPoolExecutor.awaitTermination", "java/util/concurrent/ScheduledThreadPoolExecutor", "awaitTermination", "(JLjava/util/concurrent/TimeUnit;)Z"),
        runtime("ScheduledThreadPoolExecutor.shutdownNow", "java/util/concurrent/ScheduledThreadPoolExecutor", "shutdownNow", "()Ljava/util/List;"),
        runtime("ScheduledThreadPoolExecutor.schedule", "java/util/concurrent/ScheduledThreadPoolExecutor", "schedule", "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
        runtime("ScheduledThreadPoolExecutor.scheduleAtFixedRate", "java/util/concurrent/ScheduledThreadPoolExecutor", "scheduleAtFixedRate", "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
        runtime("ScheduledThreadPoolExecutor.scheduleWithFixedDelay", "java/util/concurrent/ScheduledThreadPoolExecutor", "scheduleWithFixedDelay", "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
        runtime("ScheduledThreadPoolExecutor.shutdown", "java/util/concurrent/ScheduledThreadPoolExecutor", "shutdown", "()V"),
        runtime("ScheduledExecutorService.shutdown", "java/util/concurrent/ScheduledExecutorService", "shutdown", "()V"),
        runtime("ScheduledExecutorService.awaitTermination", "java/util/concurrent/ScheduledExecutorService", "awaitTermination", "(JLjava/util/concurrent/TimeUnit;)Z"),
        runtime("ScheduledExecutorService.shutdownNow", "java/util/concurrent/ScheduledExecutorService", "shutdownNow", "()Ljava/util/List;"),
        runtime("ScheduledExecutorService.schedule", "java/util/concurrent/ScheduledExecutorService", "schedule", "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
        runtime("ScheduledExecutorService.scheduleAtFixedRate", "java/util/concurrent/ScheduledExecutorService", "scheduleAtFixedRate", "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
        runtime("ScheduledExecutorService.scheduleWithFixedDelay", "java/util/concurrent/ScheduledExecutorService", "scheduleWithFixedDelay", "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
        runtime("ThreadPoolExecutor.CallerRunsPolicy.<init>", "java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy", "<init>", "()V"),
        runtime("AtomicBoolean.<init>", "java/util/concurrent/atomic/AtomicBoolean", "<init>", "()V", "(Z)V"),
        runtime("AtomicBoolean.get", "java/util/concurrent/atomic/AtomicBoolean", "get", "()Z"),
        runtime("AtomicBoolean.set", "java/util/concurrent/atomic/AtomicBoolean", "set", "(Z)V"),
        runtime("AtomicInteger.<init>", "java/util/concurrent/atomic/AtomicInteger", "<init>", "()V", "(I)V"),
        runtime("AtomicInteger.get", "java/util/concurrent/atomic/AtomicInteger", "get", "()I"),
        runtime("AtomicInteger.set", "java/util/concurrent/atomic/AtomicInteger", "set", "(I)V"),
        runtime("AtomicInteger.getAndIncrement", "java/util/concurrent/atomic/AtomicInteger", "getAndIncrement", "()I"),
        runtime("AtomicInteger.incrementAndGet", "java/util/concurrent/atomic/AtomicInteger", "incrementAndGet", "()I"),
        runtime("AtomicInteger.decrementAndGet", "java/util/concurrent/atomic/AtomicInteger", "decrementAndGet", "()I"),
        runtime("AtomicLong.<init>", "java/util/concurrent/atomic/AtomicLong", "<init>", "(J)V"),
        runtime("AtomicLong.get", "java/util/concurrent/atomic/AtomicLong", "get", "()J"),
        runtime("AtomicLong.set", "java/util/concurrent/atomic/AtomicLong", "set", "(J)V"),
        runtime("AtomicLong.incrementAndGet", "java/util/concurrent/atomic/AtomicLong", "incrementAndGet", "()J"),
        runtime("AtomicLong.decrementAndGet", "java/util/concurrent/atomic/AtomicLong", "decrementAndGet", "()J"),
        runtime("AtomicReference.<init>", "java/util/concurrent/atomic/AtomicReference", "<init>", "()V", "(Ljava/lang/Object;)V"),
        runtime("AtomicReference.get", "java/util/concurrent/atomic/AtomicReference", "get", "()Ljava/lang/Object;"),
        runtime("AtomicReference.compareAndSet", "java/util/concurrent/atomic/AtomicReference", "compareAndSet", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("AtomicReference.set", "java/util/concurrent/atomic/AtomicReference", "set", "(Ljava/lang/Object;)V"),
        runtime("Thread.currentThread", "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;"),
        runtime("Thread.yield", "java/lang/Thread", "yield", "()V"),
        runtime("Thread.onSpinWait", "java/lang/Thread", "onSpinWait", "()V"),
        runtime("Thread.getPriority", "java/lang/Thread", "getPriority", "()I"),
        runtime("Thread.setPriority", "java/lang/Thread", "setPriority", "(I)V"),
        runtime("Thread.setName", "java/lang/Thread", "setName", "(Ljava/lang/String;)V"),
        runtime("Thread.sleep", "java/lang/Thread", "sleep", "(J)V", "(JI)V", "(Ljava/time/Duration;)V"),
        runtime("Thread.interrupted", "java/lang/Thread", "interrupted", "()Z"),
        runtime("Thread.interrupt", "java/lang/Thread", "interrupt", "()V"),
        runtime("Thread.setDaemon", "java/lang/Thread", "setDaemon", "(Z)V"),
        runtime("Thread.isDaemon", "java/lang/Thread", "isDaemon", "()Z"),
        runtime("Thread.isInterrupted", "java/lang/Thread", "isInterrupted", "()Z"),
        runtime("Thread.isAlive", "java/lang/Thread", "isAlive", "()Z"),
        runtime("Thread.isVirtual", "java/lang/Thread", "isVirtual", "()Z"),
        runtime("Thread.getName", "java/lang/Thread", "getName", "()Ljava/lang/String;"),
        runtime("Thread.getId", "java/lang/Thread", "getId", "()J"),
        runtime("Thread.threadId", "java/lang/Thread", "threadId", "()J"),
        runtime("Thread.start", "java/lang/Thread", "start", "()V"),
        runtime("Thread.join", "java/lang/Thread", "join", "()V", "(J)V", "(JI)V", "(Ljava/time/Duration;)Z"),
        runtime("LockSupport.park", "java/util/concurrent/locks/LockSupport", "park", "()V"),
        runtime("LockSupport.parkNanos", "java/util/concurrent/locks/LockSupport", "parkNanos", "(J)V"),
        runtime("LockSupport.parkUntil", "java/util/concurrent/locks/LockSupport", "parkUntil", "(J)V"),
        runtime("LockSupport.unpark", "java/util/concurrent/locks/LockSupport", "unpark", "(Ljava/lang/Thread;)V"),
        runtime("ThreadLocal.<init>", "java/lang/ThreadLocal", "<init>", "()V"),
        runtime("ThreadLocal.get", "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;"),
        runtime("ThreadLocal.set", "java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V"),
        runtime("ThreadLocal.remove", "java/lang/ThreadLocal", "remove", "()V"),
        runtime("InheritableThreadLocal.<init>", "java/lang/InheritableThreadLocal", "<init>", "()V"),
        runtime("InheritableThreadLocal.get", "java/lang/InheritableThreadLocal", "get", "()Ljava/lang/Object;"),
        runtime("InheritableThreadLocal.set", "java/lang/InheritableThreadLocal", "set", "(Ljava/lang/Object;)V"),
        runtime("InheritableThreadLocal.remove", "java/lang/InheritableThreadLocal", "remove", "()V"),
        runtime("CharSequence.length", "java/lang/CharSequence", "length", "()I"),
        runtime("CharSequence.charAt", "java/lang/CharSequence", "charAt", "(I)C"),
        intrinsic("Character.isWhitespace", "java/lang/Character", "isWhitespace", "(C)Z"),
        runtime("Collections.unmodifiableCollection", "java/util/Collections", "unmodifiableCollection", "(Ljava/util/Collection;)Ljava/util/Collection;"),
        runtime("Collections.unmodifiableSet", "java/util/Collections", "unmodifiableSet", "(Ljava/util/Set;)Ljava/util/Set;"),
        runtime("Collections.unmodifiableList", "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;"),
        runtime("Collections.emptySet", "java/util/Collections", "emptySet", "()Ljava/util/Set;"),
        runtime("Collections.singleton", "java/util/Collections", "singleton", "(Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Collections.singletonList", "java/util/Collections", "singletonList", "(Ljava/lang/Object;)Ljava/util/List;"),
        runtime("Collections.emptyList", "java/util/Collections", "emptyList", "()Ljava/util/List;"),
        runtime("Collections.singletonMap", "java/util/Collections", "singletonMap", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Collections.emptyMap", "java/util/Collections", "emptyMap", "()Ljava/util/Map;"),
        runtime("Collections.unmodifiableMap", "java/util/Collections", "unmodifiableMap", "(Ljava/util/Map;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "()Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.of", "java/util/Map", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
        runtime("Map.ofEntries", "java/util/Map", "ofEntries", "([Ljava/util/Map$Entry;)Ljava/util/Map;"),
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
        intrinsic("Integer.toString", "java/lang/Integer", "toString", "(I)Ljava/lang/String;"),
        runtime("Integer.toString.instance", "java/lang/Integer", "toString", "()Ljava/lang/String;"),
        runtime("Integer.valueOf", "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"),
        runtime("Integer.intValue", "java/lang/Integer", "intValue", "()I"),
        runtime(
            "Integer.describeConstable",
            "java/lang/Integer",
            "describeConstable",
            "()Ljava/util/Optional;"
        ),
        runtime(
            "Integer.resolveConstantDesc",
            "java/lang/Integer",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Integer;",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ),
        intrinsic("Long.toString", "java/lang/Long", "toString", "(J)Ljava/lang/String;"),
        runtime("Long.toString.instance", "java/lang/Long", "toString", "()Ljava/lang/String;"),
        runtime("Long.valueOf", "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;"),
        runtime("Long.longValue", "java/lang/Long", "longValue", "()J"),
        runtime(
            "Long.describeConstable",
            "java/lang/Long",
            "describeConstable",
            "()Ljava/util/Optional;"
        ),
        runtime(
            "Long.resolveConstantDesc",
            "java/lang/Long",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Long;",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ),
        intrinsic("Float.toString", "java/lang/Float", "toString", "(F)Ljava/lang/String;"),
        runtime("Float.toString.instance", "java/lang/Float", "toString", "()Ljava/lang/String;"),
        runtime("Float.valueOf", "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;"),
        runtime("Float.floatValue", "java/lang/Float", "floatValue", "()F"),
        runtime(
            "Float.describeConstable",
            "java/lang/Float",
            "describeConstable",
            "()Ljava/util/Optional;"
        ),
        runtime(
            "Float.resolveConstantDesc",
            "java/lang/Float",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Float;",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ),
        intrinsic("Float.intBitsToFloat", "java/lang/Float", "intBitsToFloat", "(I)F"),
        intrinsic("Double.toString", "java/lang/Double", "toString", "(D)Ljava/lang/String;"),
        runtime("Double.toString.instance", "java/lang/Double", "toString", "()Ljava/lang/String;"),
        runtime("Double.valueOf", "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;"),
        runtime("Double.doubleValue", "java/lang/Double", "doubleValue", "()D"),
        runtime(
            "Double.describeConstable",
            "java/lang/Double",
            "describeConstable",
            "()Ljava/util/Optional;"
        ),
        runtime(
            "Double.resolveConstantDesc",
            "java/lang/Double",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Double;",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ),
        intrinsic("Double.longBitsToDouble", "java/lang/Double", "longBitsToDouble", "(J)D"),
        intrinsic("Boolean.toString", "java/lang/Boolean", "toString", "(Z)Ljava/lang/String;"),
        runtime("Boolean.toString.instance", "java/lang/Boolean", "toString", "()Ljava/lang/String;"),
        runtime("Boolean.valueOf", "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"),
        runtime("Boolean.booleanValue", "java/lang/Boolean", "booleanValue", "()Z"),
        runtime("Boolean.equals", "java/lang/Boolean", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Byte.toString.instance", "java/lang/Byte", "toString", "()Ljava/lang/String;"),
        runtime("Byte.valueOf", "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;"),
        runtime("Byte.byteValue", "java/lang/Byte", "byteValue", "()B"),
        runtime("Short.toString.instance", "java/lang/Short", "toString", "()Ljava/lang/String;"),
        runtime("Short.valueOf", "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;"),
        runtime("Short.shortValue", "java/lang/Short", "shortValue", "()S"),
        runtime("Character.toString.instance", "java/lang/Character", "toString", "()Ljava/lang/String;"),
        runtime("Character.valueOf", "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;"),
        runtime("Character.charValue", "java/lang/Character", "charValue", "()C"),
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
        runtime("DateTimeFormatterBuilder.<init>", "java/time/format/DateTimeFormatterBuilder", "<init>", "()V"),
        runtime("DateTimeFormatterBuilder.parseCaseInsensitive", "java/time/format/DateTimeFormatterBuilder", "parseCaseInsensitive", "()Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.appendPattern", "java/time/format/DateTimeFormatterBuilder", "appendPattern", "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.appendZoneText", "java/time/format/DateTimeFormatterBuilder", "appendZoneText", "(Ljava/time/format/TextStyle;)Ljava/time/format/DateTimeFormatterBuilder;"),
        runtime("DateTimeFormatterBuilder.toFormatter", "java/time/format/DateTimeFormatterBuilder", "toFormatter", "(Ljava/util/Locale;)Ljava/time/format/DateTimeFormatter;"),
        runtime("Duration.ofMillis", "java/time/Duration", "ofMillis", "(J)Ljava/time/Duration;"),
        runtime("Duration.ofSeconds", "java/time/Duration", "ofSeconds", "(J)Ljava/time/Duration;"),
        runtime("Duration.toMillis", "java/time/Duration", "toMillis", "()J"),
        runtime("PrintStream.print", "java/io/PrintStream", "print", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "([C)V", "(C)V", "(Z)V", "(I)V", "(J)V", "(F)V", "(D)V"),
        runtime("PrintStream.println", "java/io/PrintStream", "println", "()V", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "([C)V", "(I)V", "(J)V", "(F)V", "(D)V", "(Z)V", "(C)V"),
        runtime("String.<init>", "java/lang/String", "<init>", "()V", "(Ljava/lang/String;)V", "(Ljava/lang/StringBuilder;)V", "([C)V", "([CII)V"),
        runtime("String.length", "java/lang/String", "length", "()I"),
        runtime("String.hashCode", "java/lang/String", "hashCode", "()I"),
        runtime("String.isEmpty", "java/lang/String", "isEmpty", "()Z"),
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
        runtime("String.contains", "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z"),
        runtime("String.startsWith", "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", "(Ljava/lang/String;I)Z"),
        runtime("String.endsWith", "java/lang/String", "endsWith", "(Ljava/lang/String;)Z"),
        runtime("String.replace", "java/lang/String", "replace", "(CC)Ljava/lang/String;"),
        runtime("String.repeat", "java/lang/String", "repeat", "(I)Ljava/lang/String;"),
        runtime("String.intern", "java/lang/String", "intern", "()Ljava/lang/String;"),
        runtime("String.toString", "java/lang/String", "toString", "()Ljava/lang/String;"),
        runtime("String.concat", "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;"),
        runtime("String.toLowerCase", "java/lang/String", "toLowerCase", "()Ljava/lang/String;"),
        runtime("String.toUpperCase", "java/lang/String", "toUpperCase", "()Ljava/lang/String;"),
        runtime("String.describeConstable", "java/lang/String", "describeConstable", "()Ljava/util/Optional;"),
        runtime(
            "String.resolveConstantDesc",
            "java/lang/String",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ),
        runtime("String.trim", "java/lang/String", "trim", "()Ljava/lang/String;"),
        runtime("String.strip", "java/lang/String", "strip", "()Ljava/lang/String;"),
        runtime("String.stripLeading", "java/lang/String", "stripLeading", "()Ljava/lang/String;"),
        runtime("String.stripTrailing", "java/lang/String", "stripTrailing", "()Ljava/lang/String;"),
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
        runtime("ArrayList.removeAll", "java/util/ArrayList", "removeAll", "(Ljava/util/Collection;)Z"),
        runtime("ArrayList.retainAll", "java/util/ArrayList", "retainAll", "(Ljava/util/Collection;)Z"),
        runtime("ArrayList.size", "java/util/ArrayList", "size", "()I"),
        runtime("ArrayList.isEmpty", "java/util/ArrayList", "isEmpty", "()Z"),
        runtime("ArrayList.contains", "java/util/ArrayList", "contains", "(Ljava/lang/Object;)Z"),
        runtime("ArrayList.get", "java/util/ArrayList", "get", "(I)Ljava/lang/Object;"),
        runtime("ArrayList.getFirst", "java/util/ArrayList", "getFirst", "()Ljava/lang/Object;"),
        runtime("ArrayList.getLast", "java/util/ArrayList", "getLast", "()Ljava/lang/Object;"),
        runtime("ArrayList.indexOf", "java/util/ArrayList", "indexOf", "(Ljava/lang/Object;)I"),
        runtime("ArrayList.lastIndexOf", "java/util/ArrayList", "lastIndexOf", "(Ljava/lang/Object;)I"),
        runtime("ArrayList.set", "java/util/ArrayList", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"),
        runtime("ArrayList.remove", "java/util/ArrayList", "remove", "(I)Ljava/lang/Object;"),
        runtime("ArrayList.removeLast", "java/util/ArrayList", "removeLast", "()Ljava/lang/Object;"),
        runtime("ArrayList.addFirst", "java/util/ArrayList", "addFirst", "(Ljava/lang/Object;)V"),
        runtime("ArrayList.addLast", "java/util/ArrayList", "addLast", "(Ljava/lang/Object;)V"),
        runtime("ArrayList.removeFirst", "java/util/ArrayList", "removeFirst", "()Ljava/lang/Object;"),
        runtime("ArrayList.removeIf", "java/util/ArrayList", "removeIf", "(Ljava/util/function/Predicate;)Z"),
        runtime("ArrayList.iterator", "java/util/ArrayList", "iterator", "()Ljava/util/Iterator;"),
        runtime("ArrayList.listIterator", "java/util/ArrayList", "listIterator", "()Ljava/util/ListIterator;", "(I)Ljava/util/ListIterator;"),
        runtime("ArrayList.toArray", "java/util/ArrayList", "toArray", "()[Ljava/lang/Object;"),
        runtime("AbstractList.add", "java/util/AbstractList", "add", "(Ljava/lang/Object;)Z"),
        runtime("AbstractList.add", "java/util/AbstractList", "add", "(ILjava/lang/Object;)V"),
        runtime("AbstractList.addAll", "java/util/AbstractList", "addAll", "(ILjava/util/Collection;)Z"),
        runtime("AbstractList.clear", "java/util/AbstractList", "clear", "()V"),
        runtime("AbstractList.get", "java/util/AbstractList", "get", "(I)Ljava/lang/Object;"),
        runtime("AbstractList.indexOf", "java/util/AbstractList", "indexOf", "(Ljava/lang/Object;)I"),
        runtime("AbstractList.iterator", "java/util/AbstractList", "iterator", "()Ljava/util/Iterator;"),
        runtime("AbstractList.listIterator", "java/util/AbstractList", "listIterator", "()Ljava/util/ListIterator;", "(I)Ljava/util/ListIterator;"),
        runtime("AbstractList.lastIndexOf", "java/util/AbstractList", "lastIndexOf", "(Ljava/lang/Object;)I"),
        runtime("AbstractList.size", "java/util/AbstractList", "size", "()I"),
        runtime("AbstractList.remove", "java/util/AbstractList", "remove", "(I)Ljava/lang/Object;"),
        runtime("AbstractList.set", "java/util/AbstractList", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"),
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
        runtime("Set.copyOf", "java/util/Set", "copyOf", "(Ljava/util/Collection;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "()Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("Set.of", "java/util/Set", "of", "([Ljava/lang/Object;)Ljava/util/Set;"),
        runtime("List.add", "java/util/List", "add", "(Ljava/lang/Object;)Z"),
        runtime("List.add", "java/util/List", "add", "(ILjava/lang/Object;)V"),
        runtime("ArrayList.add", "java/util/ArrayList", "add", "(ILjava/lang/Object;)V"),
        runtime("List.addAll", "java/util/List", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("List.removeAll", "java/util/List", "removeAll", "(Ljava/util/Collection;)Z"),
        runtime("List.retainAll", "java/util/List", "retainAll", "(Ljava/util/Collection;)Z"),
        runtime("List.removeIf", "java/util/List", "removeIf", "(Ljava/util/function/Predicate;)Z"),
        runtime("List.size", "java/util/List", "size", "()I"),
        runtime("List.isEmpty", "java/util/List", "isEmpty", "()Z"),
        runtime("List.contains", "java/util/List", "contains", "(Ljava/lang/Object;)Z"),
        runtime("List.containsAll", "java/util/List", "containsAll", "(Ljava/util/Collection;)Z"),
        runtime("List.toArray", "java/util/List", "toArray", "()[Ljava/lang/Object;"),
        runtime("List.indexOf", "java/util/List", "indexOf", "(Ljava/lang/Object;)I"),
        runtime("List.listIterator", "java/util/List", "listIterator", "()Ljava/util/ListIterator;", "(I)Ljava/util/ListIterator;"),
        runtime("List.lastIndexOf", "java/util/List", "lastIndexOf", "(Ljava/lang/Object;)I"),
        runtime("Collection.size", "java/util/Collection", "size", "()I"),
        runtime("Collection.isEmpty", "java/util/Collection", "isEmpty", "()Z"),
        runtime("Collection.contains", "java/util/Collection", "contains", "(Ljava/lang/Object;)Z"),
        runtime("Collection.add", "java/util/Collection", "add", "(Ljava/lang/Object;)Z"),
        runtime("Collection.addAll", "java/util/Collection", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("Collection.removeAll", "java/util/Collection", "removeAll", "(Ljava/util/Collection;)Z"),
        runtime("Collection.retainAll", "java/util/Collection", "retainAll", "(Ljava/util/Collection;)Z"),
        runtime("Collection.remove", "java/util/Collection", "remove", "(Ljava/lang/Object;)Z"),
        runtime("Collection.removeIf", "java/util/Collection", "removeIf", "(Ljava/util/function/Predicate;)Z"),
        runtime("Collection.containsAll", "java/util/Collection", "containsAll", "(Ljava/util/Collection;)Z"),
        runtime("Collection.clear", "java/util/Collection", "clear", "()V"),
        runtime("Collection.toArray", "java/util/Collection", "toArray", "()[Ljava/lang/Object;"),
        runtime("Set.add", "java/util/Set", "add", "(Ljava/lang/Object;)Z"),
        runtime("Set.addAll", "java/util/Set", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("Set.removeAll", "java/util/Set", "removeAll", "(Ljava/util/Collection;)Z"),
        runtime("Set.retainAll", "java/util/Set", "retainAll", "(Ljava/util/Collection;)Z"),
        runtime("Set.contains", "java/util/Set", "contains", "(Ljava/lang/Object;)Z"),
        runtime("Set.remove", "java/util/Set", "remove", "(Ljava/lang/Object;)Z"),
        runtime("Set.removeIf", "java/util/Set", "removeIf", "(Ljava/util/function/Predicate;)Z"),
        runtime("Set.containsAll", "java/util/Set", "containsAll", "(Ljava/util/Collection;)Z"),
        runtime("Set.clear", "java/util/Set", "clear", "()V"),
        runtime("Set.size", "java/util/Set", "size", "()I"),
        runtime("Set.isEmpty", "java/util/Set", "isEmpty", "()Z"),
        runtime("Set.iterator", "java/util/Set", "iterator", "()Ljava/util/Iterator;"),
        runtime("Set.toArray", "java/util/Set", "toArray", "()[Ljava/lang/Object;"),
        runtime("List.get", "java/util/List", "get", "(I)Ljava/lang/Object;"),
        runtime("List.reversed", "java/util/List", "reversed", "()Ljava/util/List;"),
        runtime("List.getFirst", "java/util/List", "getFirst", "()Ljava/lang/Object;"),
        runtime("List.getLast", "java/util/List", "getLast", "()Ljava/lang/Object;"),
        runtime("List.remove", "java/util/List", "remove", "(I)Ljava/lang/Object;"),
        runtime("List.removeLast", "java/util/List", "removeLast", "()Ljava/lang/Object;"),
        runtime("List.removeFirst", "java/util/List", "removeFirst", "()Ljava/lang/Object;"),
        runtime("List.set", "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"),
        runtime("List.addFirst", "java/util/List", "addFirst", "(Ljava/lang/Object;)V"),
        runtime("List.addLast", "java/util/List", "addLast", "(Ljava/lang/Object;)V"),
        runtime("List.iterator", "java/util/List", "iterator", "()Ljava/util/Iterator;"),
        runtime("Collection.iterator", "java/util/Collection", "iterator", "()Ljava/util/Iterator;"),
        runtime("HashSet.<init>", "java/util/HashSet", "<init>", "()V", "(I)V", "(IF)V", "(Ljava/util/Collection;)V"),
        runtime("HashSet.newHashSet", "java/util/HashSet", "newHashSet", "(I)Ljava/util/HashSet;"),
        runtime("LinkedHashSet.<init>", "java/util/LinkedHashSet", "<init>", "()V", "(I)V", "(IF)V", "(Ljava/util/Collection;)V"),
        runtime("LinkedHashSet.newLinkedHashSet", "java/util/LinkedHashSet", "newLinkedHashSet", "(I)Ljava/util/LinkedHashSet;"),
        runtime("HashSet.add", "java/util/HashSet", "add", "(Ljava/lang/Object;)Z"),
        runtime("HashSet.addAll", "java/util/HashSet", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("HashSet.removeAll", "java/util/HashSet", "removeAll", "(Ljava/util/Collection;)Z"),
        runtime("HashSet.retainAll", "java/util/HashSet", "retainAll", "(Ljava/util/Collection;)Z"),
        runtime("HashSet.contains", "java/util/HashSet", "contains", "(Ljava/lang/Object;)Z"),
        runtime("HashSet.remove", "java/util/HashSet", "remove", "(Ljava/lang/Object;)Z"),
        runtime("HashSet.removeIf", "java/util/HashSet", "removeIf", "(Ljava/util/function/Predicate;)Z"),
        runtime("HashSet.containsAll", "java/util/HashSet", "containsAll", "(Ljava/util/Collection;)Z"),
        runtime("HashSet.clear", "java/util/HashSet", "clear", "()V"),
        runtime("HashSet.size", "java/util/HashSet", "size", "()I"),
        runtime("HashSet.isEmpty", "java/util/HashSet", "isEmpty", "()Z"),
        runtime("HashSet.iterator", "java/util/HashSet", "iterator", "()Ljava/util/Iterator;"),
        runtime("HashSet.toArray", "java/util/HashSet", "toArray", "()[Ljava/lang/Object;"),
        runtime("LinkedHashSet.add", "java/util/LinkedHashSet", "add", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashSet.addAll", "java/util/LinkedHashSet", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("LinkedHashSet.removeAll", "java/util/LinkedHashSet", "removeAll", "(Ljava/util/Collection;)Z"),
        runtime("LinkedHashSet.retainAll", "java/util/LinkedHashSet", "retainAll", "(Ljava/util/Collection;)Z"),
        runtime("LinkedHashSet.contains", "java/util/LinkedHashSet", "contains", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashSet.remove", "java/util/LinkedHashSet", "remove", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashSet.removeIf", "java/util/LinkedHashSet", "removeIf", "(Ljava/util/function/Predicate;)Z"),
        runtime("LinkedHashSet.containsAll", "java/util/LinkedHashSet", "containsAll", "(Ljava/util/Collection;)Z"),
        runtime("LinkedHashSet.clear", "java/util/LinkedHashSet", "clear", "()V"),
        runtime("LinkedHashSet.size", "java/util/LinkedHashSet", "size", "()I"),
        runtime("LinkedHashSet.isEmpty", "java/util/LinkedHashSet", "isEmpty", "()Z"),
        runtime("LinkedHashSet.iterator", "java/util/LinkedHashSet", "iterator", "()Ljava/util/Iterator;"),
        runtime("LinkedHashSet.toArray", "java/util/LinkedHashSet", "toArray", "()[Ljava/lang/Object;"),
        runtime("Iterator.hasNext", "java/util/Iterator", "hasNext", "()Z"),
        runtime("Iterator.next", "java/util/Iterator", "next", "()Ljava/lang/Object;"),
        runtime("Iterator.remove", "java/util/Iterator", "remove", "()V"),
        runtime("Iterator.forEachRemaining", "java/util/Iterator", "forEachRemaining", "(Ljava/util/function/Consumer;)V"),
        runtime("ListIterator.hasNext", "java/util/ListIterator", "hasNext", "()Z"),
        runtime("ListIterator.next", "java/util/ListIterator", "next", "()Ljava/lang/Object;"),
        runtime("ListIterator.hasPrevious", "java/util/ListIterator", "hasPrevious", "()Z"),
        runtime("ListIterator.previous", "java/util/ListIterator", "previous", "()Ljava/lang/Object;"),
        runtime("ListIterator.nextIndex", "java/util/ListIterator", "nextIndex", "()I"),
        runtime("ListIterator.previousIndex", "java/util/ListIterator", "previousIndex", "()I"),
        runtime("ListIterator.remove", "java/util/ListIterator", "remove", "()V"),
        runtime("ListIterator.set", "java/util/ListIterator", "set", "(Ljava/lang/Object;)V"),
        runtime("ListIterator.add", "java/util/ListIterator", "add", "(Ljava/lang/Object;)V"),
        runtime("Map.Entry.getKey", "java/util/Map$Entry", "getKey", "()Ljava/lang/Object;"),
        runtime("Map.Entry.getValue", "java/util/Map$Entry", "getValue", "()Ljava/lang/Object;"),
        runtime("Map.Entry.setValue", "java/util/Map$Entry", "setValue", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.<init>", "java/util/HashMap", "<init>", "()V"),
        runtime("HashMap.<init>", "java/util/HashMap", "<init>", "(I)V"),
        runtime("HashMap.<init>", "java/util/HashMap", "<init>", "(IF)V"),
        runtime("HashMap.<init>", "java/util/HashMap", "<init>", "(Ljava/util/Map;)V"),
        runtime("HashMap.newHashMap", "java/util/HashMap", "newHashMap", "(I)Ljava/util/HashMap;"),
        runtime("LinkedHashMap.<init>", "java/util/LinkedHashMap", "<init>", "()V"),
        runtime("LinkedHashMap.<init>", "java/util/LinkedHashMap", "<init>", "(I)V"),
        runtime("LinkedHashMap.<init>", "java/util/LinkedHashMap", "<init>", "(IF)V"),
        runtime("LinkedHashMap.<init>", "java/util/LinkedHashMap", "<init>", "(Ljava/util/Map;)V"),
        runtime("LinkedHashMap.newLinkedHashMap", "java/util/LinkedHashMap", "newLinkedHashMap", "(I)Ljava/util/LinkedHashMap;"),
        runtime("TreeMap.<init>", "java/util/TreeMap", "<init>", "()V"),
        runtime("ConcurrentHashMap.<init>", "java/util/concurrent/ConcurrentHashMap", "<init>", "()V"),
        runtime("ConcurrentHashMap.<init>", "java/util/concurrent/ConcurrentHashMap", "<init>", "(I)V"),
        runtime("ConcurrentHashMap.<init>", "java/util/concurrent/ConcurrentHashMap", "<init>", "(Ljava/util/Map;)V"),
        runtime("ConcurrentHashMap.<init>", "java/util/concurrent/ConcurrentHashMap", "<init>", "(IF)V"),
        runtime("ConcurrentHashMap.<init>", "java/util/concurrent/ConcurrentHashMap", "<init>", "(IFI)V"),
        runtime("Map.copyOf", "java/util/Map", "copyOf", "(Ljava/util/Map;)Ljava/util/Map;"),
        runtime("Map.get", "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.get", "java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.get", "java/util/LinkedHashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.get", "java/util/TreeMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.get", "java/util/concurrent/ConcurrentHashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.getOrDefault", "java/util/Map", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.put", "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.putIfAbsent", "java/util/Map", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.replace", "java/util/Map", "replace", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.replace", "java/util/Map", "replace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("Map.clear", "java/util/Map", "clear", "()V"),
        runtime("Map.putAll", "java/util/Map", "putAll", "(Ljava/util/Map;)V"),
        runtime("Map.forEach", "java/util/Map", "forEach", "(Ljava/util/function/BiConsumer;)V"),
        runtime("Map.remove", "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.remove", "java/util/Map", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("Map.computeIfAbsent", "java/util/Map", "computeIfAbsent", "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        runtime("Map.computeIfPresent", "java/util/Map", "computeIfPresent", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("Map.compute", "java/util/Map", "compute", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("Map.merge", "java/util/Map", "merge", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("Map.entry", "java/util/Map", "entry", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;"),
        runtime("Map.containsValue", "java/util/Map", "containsValue", "(Ljava/lang/Object;)Z"),
        runtime("HashMap.put", "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.put", "java/util/LinkedHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.put", "java/util/TreeMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.put", "java/util/concurrent/ConcurrentHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.putIfAbsent", "java/util/HashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.computeIfAbsent", "java/util/HashMap", "computeIfAbsent", "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        runtime("HashMap.computeIfPresent", "java/util/HashMap", "computeIfPresent", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("HashMap.compute", "java/util/HashMap", "compute", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("HashMap.merge", "java/util/HashMap", "merge", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.putIfAbsent", "java/util/LinkedHashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.computeIfAbsent", "java/util/LinkedHashMap", "computeIfAbsent", "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.computeIfPresent", "java/util/LinkedHashMap", "computeIfPresent", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.compute", "java/util/LinkedHashMap", "compute", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.merge", "java/util/LinkedHashMap", "merge", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("TreeMap.putIfAbsent", "java/util/TreeMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.computeIfAbsent", "java/util/TreeMap", "computeIfAbsent", "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        runtime("TreeMap.computeIfPresent", "java/util/TreeMap", "computeIfPresent", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("TreeMap.compute", "java/util/TreeMap", "compute", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("TreeMap.merge", "java/util/TreeMap", "merge", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.putIfAbsent", "java/util/concurrent/ConcurrentHashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.computeIfPresent", "java/util/concurrent/ConcurrentHashMap", "computeIfPresent", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("HashMap.replace", "java/util/HashMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.replace", "java/util/HashMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("LinkedHashMap.replace", "java/util/LinkedHashMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.replace", "java/util/LinkedHashMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("TreeMap.replace", "java/util/TreeMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.replace", "java/util/TreeMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("ConcurrentHashMap.replace", "java/util/concurrent/ConcurrentHashMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.replace", "java/util/concurrent/ConcurrentHashMap", "replace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("HashMap.clear", "java/util/HashMap", "clear", "()V"),
        runtime("LinkedHashMap.clear", "java/util/LinkedHashMap", "clear", "()V"),
        runtime("TreeMap.clear", "java/util/TreeMap", "clear", "()V"),
        runtime("ConcurrentHashMap.clear", "java/util/concurrent/ConcurrentHashMap", "clear", "()V"),
        runtime("HashMap.putAll", "java/util/HashMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("LinkedHashMap.putAll", "java/util/LinkedHashMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("TreeMap.putAll", "java/util/TreeMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("ConcurrentHashMap.putAll", "java/util/concurrent/ConcurrentHashMap", "putAll", "(Ljava/util/Map;)V"),
        runtime("HashMap.remove", "java/util/HashMap", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.remove", "java/util/HashMap", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("LinkedHashMap.remove", "java/util/LinkedHashMap", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.remove", "java/util/LinkedHashMap", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("TreeMap.remove", "java/util/TreeMap", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.remove", "java/util/TreeMap", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("ConcurrentHashMap.remove", "java/util/concurrent/ConcurrentHashMap", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("ConcurrentHashMap.remove", "java/util/concurrent/ConcurrentHashMap", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("Map.containsKey", "java/util/Map", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("HashMap.containsKey", "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashMap.containsKey", "java/util/LinkedHashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("TreeMap.containsKey", "java/util/TreeMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("ConcurrentHashMap.containsKey", "java/util/concurrent/ConcurrentHashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("HashMap.containsValue", "java/util/HashMap", "containsValue", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashMap.containsValue", "java/util/LinkedHashMap", "containsValue", "(Ljava/lang/Object;)Z"),
        runtime("TreeMap.containsValue", "java/util/TreeMap", "containsValue", "(Ljava/lang/Object;)Z"),
        runtime("ConcurrentHashMap.containsValue", "java/util/concurrent/ConcurrentHashMap", "containsValue", "(Ljava/lang/Object;)Z"),
        runtime("Map.size", "java/util/Map", "size", "()I"),
        runtime("HashMap.size", "java/util/HashMap", "size", "()I"),
        runtime("LinkedHashMap.size", "java/util/LinkedHashMap", "size", "()I"),
        runtime("TreeMap.size", "java/util/TreeMap", "size", "()I"),
        runtime("ConcurrentHashMap.size", "java/util/concurrent/ConcurrentHashMap", "size", "()I"),
        runtime("Map.isEmpty", "java/util/Map", "isEmpty", "()Z"),
        runtime("Map.keySet", "java/util/Map", "keySet", "()Ljava/util/Set;"),
        runtime("Map.entrySet", "java/util/Map", "entrySet", "()Ljava/util/Set;"),
        runtime("HashMap.keySet", "java/util/HashMap", "keySet", "()Ljava/util/Set;"),
        runtime("HashMap.entrySet", "java/util/HashMap", "entrySet", "()Ljava/util/Set;"),
        runtime("HashMap.isEmpty", "java/util/HashMap", "isEmpty", "()Z"),
        runtime("LinkedHashMap.keySet", "java/util/LinkedHashMap", "keySet", "()Ljava/util/Set;"),
        runtime("LinkedHashMap.entrySet", "java/util/LinkedHashMap", "entrySet", "()Ljava/util/Set;"),
        runtime("LinkedHashMap.isEmpty", "java/util/LinkedHashMap", "isEmpty", "()Z"),
        runtime("TreeMap.keySet", "java/util/TreeMap", "keySet", "()Ljava/util/Set;"),
        runtime("TreeMap.entrySet", "java/util/TreeMap", "entrySet", "()Ljava/util/Set;"),
        runtime("TreeMap.isEmpty", "java/util/TreeMap", "isEmpty", "()Z"),
        runtime("ConcurrentHashMap.keySet", "java/util/concurrent/ConcurrentHashMap", "keySet", "()Ljava/util/Set;"),
        runtime("ConcurrentHashMap.entrySet", "java/util/concurrent/ConcurrentHashMap", "entrySet", "()Ljava/util/Set;"),
        runtime("ConcurrentHashMap.isEmpty", "java/util/concurrent/ConcurrentHashMap", "isEmpty", "()Z"),
        runtime("Map.values", "java/util/Map", "values", "()Ljava/util/Collection;"),
        runtime("HashMap.values", "java/util/HashMap", "values", "()Ljava/util/Collection;"),
        runtime("LinkedHashMap.values", "java/util/LinkedHashMap", "values", "()Ljava/util/Collection;"),
        runtime("TreeMap.values", "java/util/TreeMap", "values", "()Ljava/util/Collection;"),
        runtime("ConcurrentHashMap.values", "java/util/concurrent/ConcurrentHashMap", "values", "()Ljava/util/Collection;"),
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
        runtime("InetAddress.getByName", "java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;"),
        runtime("InetAddress.getAllByName", "java/net/InetAddress", "getAllByName", "(Ljava/lang/String;)[Ljava/net/InetAddress;"),
        runtime("InetAddress.getByAddress", "java/net/InetAddress", "getByAddress", "([B)Ljava/net/InetAddress;", "(Ljava/lang/String;[B)Ljava/net/InetAddress;"),
        runtime("InetAddress.getAddress", "java/net/InetAddress", "getAddress", "()[B"),
        runtime("InetAddress.getHostAddress", "java/net/InetAddress", "getHostAddress", "()Ljava/lang/String;"),
        runtime("InetAddress.getHostName", "java/net/InetAddress", "getHostName", "()Ljava/lang/String;"),
        runtime("InetAddress.getCanonicalHostName", "java/net/InetAddress", "getCanonicalHostName", "()Ljava/lang/String;"),
        runtime("InetSocketAddress.<init>", "java/net/InetSocketAddress", "<init>", "(Ljava/lang/String;I)V", "(Ljava/net/InetAddress;I)V"),
        runtime("InetSocketAddress.getPort", "java/net/InetSocketAddress", "getPort", "()I"),
        runtime("InetSocketAddress.getHostString", "java/net/InetSocketAddress", "getHostString", "()Ljava/lang/String;"),
        runtime("InetSocketAddress.getAddress", "java/net/InetSocketAddress", "getAddress", "()Ljava/net/InetAddress;"),
        runtime("InetSocketAddress.toString", "java/net/InetSocketAddress", "toString", "()Ljava/lang/String;"),
        runtime(
            "Socket.<init>",
            "java/net/Socket",
            "<init>",
            "()V",
            "(Ljava/lang/String;I)V",
            "(Ljava/net/InetAddress;I)V",
            "(Ljava/lang/String;ILjava/net/InetAddress;I)V",
            "(Ljava/net/InetAddress;ILjava/net/InetAddress;I)V"
        ),
        runtime("Socket.connect", "java/net/Socket", "connect", "(Ljava/net/SocketAddress;)V", "(Ljava/net/SocketAddress;I)V"),
        runtime("Socket.isConnected", "java/net/Socket", "isConnected", "()Z"),
        runtime("Socket.isClosed", "java/net/Socket", "isClosed", "()Z"),
        runtime("Socket.isBound", "java/net/Socket", "isBound", "()Z"),
        runtime("Socket.isInputShutdown", "java/net/Socket", "isInputShutdown", "()Z"),
        runtime("Socket.isOutputShutdown", "java/net/Socket", "isOutputShutdown", "()Z"),
        runtime("Socket.getPort", "java/net/Socket", "getPort", "()I"),
        runtime("Socket.getLocalPort", "java/net/Socket", "getLocalPort", "()I"),
        runtime("Socket.getSoTimeout", "java/net/Socket", "getSoTimeout", "()I"),
        runtime("Socket.setSoTimeout", "java/net/Socket", "setSoTimeout", "(I)V"),
        runtime("Socket.getSoLinger", "java/net/Socket", "getSoLinger", "()I"),
        runtime("Socket.setSoLinger", "java/net/Socket", "setSoLinger", "(ZI)V"),
        runtime("Socket.getOOBInline", "java/net/Socket", "getOOBInline", "()Z"),
        runtime("Socket.setOOBInline", "java/net/Socket", "setOOBInline", "(Z)V"),
        runtime("Socket.getTrafficClass", "java/net/Socket", "getTrafficClass", "()I"),
        runtime("Socket.setTrafficClass", "java/net/Socket", "setTrafficClass", "(I)V"),
        runtime("Socket.getTcpNoDelay", "java/net/Socket", "getTcpNoDelay", "()Z"),
        runtime("Socket.setTcpNoDelay", "java/net/Socket", "setTcpNoDelay", "(Z)V"),
        runtime("Socket.getKeepAlive", "java/net/Socket", "getKeepAlive", "()Z"),
        runtime("Socket.setKeepAlive", "java/net/Socket", "setKeepAlive", "(Z)V"),
        runtime("Socket.getReuseAddress", "java/net/Socket", "getReuseAddress", "()Z"),
        runtime("Socket.setReuseAddress", "java/net/Socket", "setReuseAddress", "(Z)V"),
        runtime("Socket.getReceiveBufferSize", "java/net/Socket", "getReceiveBufferSize", "()I"),
        runtime("Socket.setReceiveBufferSize", "java/net/Socket", "setReceiveBufferSize", "(I)V"),
        runtime("Socket.getSendBufferSize", "java/net/Socket", "getSendBufferSize", "()I"),
        runtime("Socket.setSendBufferSize", "java/net/Socket", "setSendBufferSize", "(I)V"),
        runtime("Socket.getLocalAddress", "java/net/Socket", "getLocalAddress", "()Ljava/net/InetAddress;"),
        runtime("Socket.getInetAddress", "java/net/Socket", "getInetAddress", "()Ljava/net/InetAddress;"),
        runtime("Socket.getLocalSocketAddress", "java/net/Socket", "getLocalSocketAddress", "()Ljava/net/SocketAddress;"),
        runtime("Socket.getRemoteSocketAddress", "java/net/Socket", "getRemoteSocketAddress", "()Ljava/net/SocketAddress;"),
        runtime("Socket.getChannel", "java/net/Socket", "getChannel", "()Ljava/nio/channels/SocketChannel;"),
        runtime("Socket.getInputStream", "java/net/Socket", "getInputStream", "()Ljava/io/InputStream;"),
        runtime("Socket.getOutputStream", "java/net/Socket", "getOutputStream", "()Ljava/io/OutputStream;"),
        runtime("Socket.shutdownInput", "java/net/Socket", "shutdownInput", "()V"),
        runtime("Socket.shutdownOutput", "java/net/Socket", "shutdownOutput", "()V"),
        runtime("Socket.close", "java/net/Socket", "close", "()V"),
        runtime("ServerSocket.<init>", "java/net/ServerSocket", "<init>", "()V", "(I)V", "(II)V", "(IILjava/net/InetAddress;)V"),
        runtime("ServerSocket.bind", "java/net/ServerSocket", "bind", "(Ljava/net/SocketAddress;)V", "(Ljava/net/SocketAddress;I)V"),
        runtime("ServerSocket.isBound", "java/net/ServerSocket", "isBound", "()Z"),
        runtime("ServerSocket.isClosed", "java/net/ServerSocket", "isClosed", "()Z"),
        runtime("ServerSocket.getInetAddress", "java/net/ServerSocket", "getInetAddress", "()Ljava/net/InetAddress;"),
        runtime("ServerSocket.getLocalPort", "java/net/ServerSocket", "getLocalPort", "()I"),
        runtime("ServerSocket.getSoTimeout", "java/net/ServerSocket", "getSoTimeout", "()I"),
        runtime("ServerSocket.setSoTimeout", "java/net/ServerSocket", "setSoTimeout", "(I)V"),
        runtime("ServerSocket.getReuseAddress", "java/net/ServerSocket", "getReuseAddress", "()Z"),
        runtime("ServerSocket.setReuseAddress", "java/net/ServerSocket", "setReuseAddress", "(Z)V"),
        runtime("ServerSocket.getReceiveBufferSize", "java/net/ServerSocket", "getReceiveBufferSize", "()I"),
        runtime("ServerSocket.setReceiveBufferSize", "java/net/ServerSocket", "setReceiveBufferSize", "(I)V"),
        runtime("ServerSocket.getLocalSocketAddress", "java/net/ServerSocket", "getLocalSocketAddress", "()Ljava/net/SocketAddress;"),
        runtime("ServerSocket.getChannel", "java/net/ServerSocket", "getChannel", "()Ljava/nio/channels/ServerSocketChannel;"),
        runtime("ServerSocket.accept", "java/net/ServerSocket", "accept", "()Ljava/net/Socket;"),
        runtime("ServerSocket.close", "java/net/ServerSocket", "close", "()V"),
        runtime("URI.create", "java/net/URI", "create", "(Ljava/lang/String;)Ljava/net/URI;"),
        runtime("URI.getPath", "java/net/URI", "getPath", "()Ljava/lang/String;"),
        runtime("URI.getQuery", "java/net/URI", "getQuery", "()Ljava/lang/String;"),
        runtime("URI.getRawPath", "java/net/URI", "getRawPath", "()Ljava/lang/String;"),
        runtime("URI.getRawQuery", "java/net/URI", "getRawQuery", "()Ljava/lang/String;"),
        runtime("HttpServer.create", "com/sun/net/httpserver/HttpServer", "create", "(Ljava/net/InetSocketAddress;I)Lcom/sun/net/httpserver/HttpServer;"),
        runtime("HttpServer.createContext", "com/sun/net/httpserver/HttpServer", "createContext", "(Ljava/lang/String;Lcom/sun/net/httpserver/HttpHandler;)Lcom/sun/net/httpserver/HttpContext;"),
        runtime("HttpServer.removeContext", "com/sun/net/httpserver/HttpServer", "removeContext", "(Ljava/lang/String;)V"),
        runtime("HttpServer.getAddress", "com/sun/net/httpserver/HttpServer", "getAddress", "()Ljava/net/InetSocketAddress;"),
        runtime("HttpServer.setExecutor", "com/sun/net/httpserver/HttpServer", "setExecutor", "(Ljava/util/concurrent/Executor;)V"),
        runtime("HttpServer.start", "com/sun/net/httpserver/HttpServer", "start", "()V"),
        runtime("HttpServer.stop", "com/sun/net/httpserver/HttpServer", "stop", "(I)V"),
        runtime("HttpExchange.sendResponseHeaders", "com/sun/net/httpserver/HttpExchange", "sendResponseHeaders", "(IJ)V"),
        runtime("HttpExchange.getRequestMethod", "com/sun/net/httpserver/HttpExchange", "getRequestMethod", "()Ljava/lang/String;"),
        runtime("HttpExchange.getRequestURI", "com/sun/net/httpserver/HttpExchange", "getRequestURI", "()Ljava/net/URI;"),
        runtime("HttpExchange.getRemoteAddress", "com/sun/net/httpserver/HttpExchange", "getRemoteAddress", "()Ljava/net/InetSocketAddress;"),
        runtime("HttpExchange.getLocalAddress", "com/sun/net/httpserver/HttpExchange", "getLocalAddress", "()Ljava/net/InetSocketAddress;"),
        runtime("HttpExchange.getResponseCode", "com/sun/net/httpserver/HttpExchange", "getResponseCode", "()I"),
        runtime("HttpExchange.getProtocol", "com/sun/net/httpserver/HttpExchange", "getProtocol", "()Ljava/lang/String;"),
        runtime("HttpExchange.getRequestHeaders", "com/sun/net/httpserver/HttpExchange", "getRequestHeaders", "()Lcom/sun/net/httpserver/Headers;"),
        runtime("Headers.getFirst", "com/sun/net/httpserver/Headers", "getFirst", "(Ljava/lang/String;)Ljava/lang/String;"),
        runtime("Headers.get", "com/sun/net/httpserver/Headers", "get", "(Ljava/lang/Object;)Ljava/util/List;"),
        runtime("Headers.getOrDefault", "com/sun/net/httpserver/Headers", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Headers.keySet", "com/sun/net/httpserver/Headers", "keySet", "()Ljava/util/Set;"),
        runtime("Headers.entrySet", "com/sun/net/httpserver/Headers", "entrySet", "()Ljava/util/Set;"),
        runtime("Headers.values", "com/sun/net/httpserver/Headers", "values", "()Ljava/util/Collection;"),
        runtime("Headers.containsKey", "com/sun/net/httpserver/Headers", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("Headers.remove", "com/sun/net/httpserver/Headers", "remove", "(Ljava/lang/Object;)Ljava/util/List;", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Headers.remove", "com/sun/net/httpserver/Headers", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("Headers.clear", "com/sun/net/httpserver/Headers", "clear", "()V"),
        runtime("Headers.size", "com/sun/net/httpserver/Headers", "size", "()I"),
        runtime("Headers.isEmpty", "com/sun/net/httpserver/Headers", "isEmpty", "()Z"),
        runtime("Headers.put", "com/sun/net/httpserver/Headers", "put", "(Ljava/lang/String;Ljava/util/List;)Ljava/util/List;", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Headers.replace", "com/sun/net/httpserver/Headers", "replace", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Headers.replace", "com/sun/net/httpserver/Headers", "replace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
        runtime("Headers.computeIfAbsent", "com/sun/net/httpserver/Headers", "computeIfAbsent", "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        runtime("Headers.computeIfPresent", "com/sun/net/httpserver/Headers", "computeIfPresent", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("Headers.compute", "com/sun/net/httpserver/Headers", "compute", "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("Headers.merge", "com/sun/net/httpserver/Headers", "merge", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"),
        runtime("Headers.putIfAbsent", "com/sun/net/httpserver/Headers", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Headers.putAll", "com/sun/net/httpserver/Headers", "putAll", "(Ljava/util/Map;)V"),
        runtime("Headers.containsValue", "com/sun/net/httpserver/Headers", "containsValue", "(Ljava/lang/Object;)Z"),
        runtime("HttpExchange.getResponseHeaders", "com/sun/net/httpserver/HttpExchange", "getResponseHeaders", "()Lcom/sun/net/httpserver/Headers;"),
        runtime("Headers.set", "com/sun/net/httpserver/Headers", "set", "(Ljava/lang/String;Ljava/lang/String;)V"),
        runtime("Headers.add", "com/sun/net/httpserver/Headers", "add", "(Ljava/lang/String;Ljava/lang/String;)V"),
        runtime("HttpExchange.getRequestBody", "com/sun/net/httpserver/HttpExchange", "getRequestBody", "()Ljava/io/InputStream;"),
        runtime("HttpExchange.getResponseBody", "com/sun/net/httpserver/HttpExchange", "getResponseBody", "()Ljava/io/OutputStream;"),
        runtime("HttpExchange.close", "com/sun/net/httpserver/HttpExchange", "close", "()V"),
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
        runtime("InputStream.read", "java/io/InputStream", "read", "()I", "([B)I", "([BII)I"),
        runtime("InputStream.readAllBytes", "java/io/InputStream", "readAllBytes", "()[B"),
        runtime("InputStream.readNBytes", "java/io/InputStream", "readNBytes", "(I)[B", "([BII)I"),
        runtime("InputStream.available", "java/io/InputStream", "available", "()I"),
        runtime("InputStream.skip", "java/io/InputStream", "skip", "(J)J"),
        runtime("InputStream.markSupported", "java/io/InputStream", "markSupported", "()Z"),
        runtime("InputStream.mark", "java/io/InputStream", "mark", "(I)V"),
        runtime("InputStream.reset", "java/io/InputStream", "reset", "()V"),
        runtime("InputStream.close", "java/io/InputStream", "close", "()V"),
        runtime("OutputStream.write", "java/io/OutputStream", "write", "(I)V", "([B)V", "([BII)V"),
        runtime("OutputStream.flush", "java/io/OutputStream", "flush", "()V"),
        runtime("OutputStream.close", "java/io/OutputStream", "close", "()V"),
        runtime("Iterable.iterator", "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;"),
        runtime("Iterable.forEach", "java/lang/Iterable", "forEach", "(Ljava/util/function/Consumer;)V"),
        runtime("DirectoryStream.iterator", "java/nio/file/DirectoryStream", "iterator", "()Ljava/util/Iterator;"),
        runtime("DirectoryStream.close", "java/nio/file/DirectoryStream", "close", "()V"),
        runtime("Optional.empty", "java/util/Optional", "empty", "()Ljava/util/Optional;"),
        runtime("Optional.of", "java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;"),
        runtime("Optional.ofNullable", "java/util/Optional", "ofNullable", "(Ljava/lang/Object;)Ljava/util/Optional;"),
        runtime("Optional.isPresent", "java/util/Optional", "isPresent", "()Z"),
        runtime("Optional.isEmpty", "java/util/Optional", "isEmpty", "()Z"),
        runtime("Optional.get", "java/util/Optional", "get", "()Ljava/lang/Object;"),
        runtime("Optional.ifPresent", "java/util/Optional", "ifPresent", "(Ljava/util/function/Consumer;)V"),
        runtime("Optional.orElse", "java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Optional.or", "java/util/Optional", "or", "(Ljava/util/function/Supplier;)Ljava/util/Optional;"),
        runtime("Optional.orElseGet", "java/util/Optional", "orElseGet", "(Ljava/util/function/Supplier;)Ljava/lang/Object;"),
        runtime("Optional.orElseThrow", "java/util/Optional", "orElseThrow", "()Ljava/lang/Object;"),
        runtime("Supplier.get", "java/util/function/Supplier", "get", "()Ljava/lang/Object;"),
        runtime("Function.apply", "java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Consumer.accept", "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"),
        runtime("BiConsumer.accept", "java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"),
        runtime("BiFunction.apply", "java/util/function/BiFunction", "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Predicate.test", "java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z"),
        runtime("Optional.filter", "java/util/Optional", "filter", "(Ljava/util/function/Predicate;)Ljava/util/Optional;"),
        runtime("Optional.map", "java/util/Optional", "map", "(Ljava/util/function/Function;)Ljava/util/Optional;"),
        runtime("Optional.flatMap", "java/util/Optional", "flatMap", "(Ljava/util/function/Function;)Ljava/util/Optional;")
    );
    private static final SupportedCallIndex SUPPORTED_CALL_INDEX = new SupportedCallIndex(SUPPORTED_CALLS);

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
        if ("java/util/List".equals(methodRef.owner())) {
            return isSupportedListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/ArrayList".equals(methodRef.owner())) {
            return isSupportedArrayListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/AbstractList".equals(methodRef.owner())) {
            return isSupportedAbstractListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Collection".equals(methodRef.owner())) {
            return isSupportedCollectionCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Iterator".equals(methodRef.owner())) {
            return isSupportedIteratorCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/ListIterator".equals(methodRef.owner())) {
            return isSupportedListIteratorCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Map".equals(methodRef.owner())) {
            return isSupportedMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/HashMap".equals(methodRef.owner())) {
            return isSupportedHashMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/LinkedHashMap".equals(methodRef.owner())) {
            return isSupportedLinkedHashMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/TreeMap".equals(methodRef.owner())) {
            return isSupportedTreeMapCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(methodRef.owner())) {
            return isSupportedConcurrentHashMapCall(methodRef.name(), methodRef.descriptor());
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
        if ("java/lang/Iterable".equals(methodRef.owner())) {
            return isSupportedIterableCall(methodRef.name(), methodRef.descriptor());
        }
        return false;
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
            return "(Ljava/util/Collection;)Z".equals(descriptor)
                || "(ILjava/util/Collection;)Z".equals(descriptor);
        }
        if ("removeAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("retainAll".equals(name)) {
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
        if ("indexOf".equals(name)) {
            return "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("lastIndexOf".equals(name)) {
            return "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor)
                || "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("removeLast".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("removeFirst".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("set".equals(name)) {
            return "(ILjava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("addFirst".equals(name)) {
            return "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("addLast".equals(name)) {
            return "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("toArray".equals(name)) {
            return "()[Ljava/lang/Object;".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("listIterator".equals(name)) {
            return "()Ljava/util/ListIterator;".equals(descriptor)
                || "(I)Ljava/util/ListIterator;".equals(descriptor);
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
        if ("addAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor)
                || "(ILjava/util/Collection;)Z".equals(descriptor);
        }
        if ("removeAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("retainAll".equals(name)) {
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
        if ("get".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor);
        }
        if ("getFirst".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("getLast".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("indexOf".equals(name)) {
            return "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("lastIndexOf".equals(name)) {
            return "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("set".equals(name)) {
            return "(ILjava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor)
                || "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("removeLast".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("addFirst".equals(name)) {
            return "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("addLast".equals(name)) {
            return "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("removeFirst".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("listIterator".equals(name)) {
            return "()Ljava/util/ListIterator;".equals(descriptor)
                || "(I)Ljava/util/ListIterator;".equals(descriptor);
        }
        if ("toArray".equals(name)) {
            return "()[Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedAbstractListCall(final String name, final String descriptor) {
        if ("add".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor)
                || "(ILjava/lang/Object;)V".equals(descriptor);
        }
        if ("addAll".equals(name)) {
            return "(ILjava/util/Collection;)Z".equals(descriptor);
        }
        if ("clear".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("get".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor);
        }
        if ("indexOf".equals(name)) {
            return "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("listIterator".equals(name)) {
            return "()Ljava/util/ListIterator;".equals(descriptor)
                || "(I)Ljava/util/ListIterator;".equals(descriptor);
        }
        if ("lastIndexOf".equals(name)) {
            return "(Ljava/lang/Object;)I".equals(descriptor);
        }
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor);
        }
        if ("set".equals(name)) {
            return "(ILjava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedCollectionCall(final String name, final String descriptor) {
        if ("size".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("isEmpty".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("contains".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("add".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("addAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("removeAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("retainAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("containsAll".equals(name)) {
            return "(Ljava/util/Collection;)Z".equals(descriptor);
        }
        if ("clear".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("toArray".equals(name)) {
            return "()[Ljava/lang/Object;".equals(descriptor);
        }
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
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
        if ("remove".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("forEachRemaining".equals(name)) {
            return "(Ljava/util/function/Consumer;)V".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedListIteratorCall(final String name, final String descriptor) {
        if ("hasNext".equals(name) || "hasPrevious".equals(name)) {
            return "()Z".equals(descriptor);
        }
        if ("next".equals(name) || "previous".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("nextIndex".equals(name) || "previousIndex".equals(name)) {
            return "()I".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("set".equals(name) || "add".equals(name)) {
            return "(Ljava/lang/Object;)V".equals(descriptor);
        }
        return false;
    }

    private static boolean isSupportedMapCall(final String name, final String descriptor) {
        if ("copyOf".equals(name)) {
            return "(Ljava/util/Map;)Ljava/util/Map;".equals(descriptor);
        }
        if ("get".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("getOrDefault".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("put".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("putIfAbsent".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("clear".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor)
                || "(Ljava/lang/Object;Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("containsKey".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("keySet".equals(name) || "entrySet".equals(name)) {
            return "()Ljava/util/Set;".equals(descriptor);
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
        return false;
    }

    private static boolean isSupportedHashMapCall(final String name, final String descriptor) {
        if ("<init>".equals(name)) {
            return "()V".equals(descriptor)
                || "(I)V".equals(descriptor)
                || "(IF)V".equals(descriptor)
                || "(Ljava/util/Map;)V".equals(descriptor);
        }
        if ("newHashMap".equals(name)) {
            return "(I)Ljava/util/HashMap;".equals(descriptor);
        }
        return isSupportedMutableMapInstanceCall(name, descriptor);
    }

    private static boolean isSupportedLinkedHashMapCall(final String name, final String descriptor) {
        if ("<init>".equals(name)) {
            return "()V".equals(descriptor)
                || "(I)V".equals(descriptor)
                || "(IF)V".equals(descriptor)
                || "(Ljava/util/Map;)V".equals(descriptor);
        }
        if ("newLinkedHashMap".equals(name)) {
            return "(I)Ljava/util/LinkedHashMap;".equals(descriptor);
        }
        return isSupportedMutableMapInstanceCall(name, descriptor);
    }

    private static boolean isSupportedTreeMapCall(final String name, final String descriptor) {
        if ("<init>".equals(name)) {
            return "()V".equals(descriptor);
        }
        return isSupportedMutableMapInstanceCall(name, descriptor);
    }

    private static boolean isSupportedConcurrentHashMapCall(final String name, final String descriptor) {
        if ("<init>".equals(name)) {
            return "()V".equals(descriptor)
                || "(I)V".equals(descriptor)
                || "(Ljava/util/Map;)V".equals(descriptor)
                || "(IF)V".equals(descriptor)
                || "(IFI)V".equals(descriptor);
        }
        return isSupportedMutableMapInstanceCall(name, descriptor);
    }

    private static boolean isSupportedMutableMapInstanceCall(final String name, final String descriptor) {
        if ("get".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("put".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("putIfAbsent".equals(name)) {
            return "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("clear".equals(name)) {
            return "()V".equals(descriptor);
        }
        if ("remove".equals(name)) {
            return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor)
                || "(Ljava/lang/Object;Ljava/lang/Object;)Z".equals(descriptor);
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

    private static boolean isSupportedIterableCall(final String name, final String descriptor) {
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
        }
        if ("forEach".equals(name)) {
            return "(Ljava/util/function/Consumer;)V".equals(descriptor);
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
        for (final SupportedCall call : SUPPORTED_CALL_INDEX.bucket(methodRef.owner(), methodRef.name())) {
            if (call.containsDescriptor(methodRef.descriptor())) {
                return Optional.of(call);
            }
        }
        return Optional.empty();
    }

    /**
     * Normalizes an inherited application-subclass call to a directly supported JDK call when the
     * subclass does not override the method and the first supported superclass declaration is part
     * of the JDK.
     *
     * @param classes parsed closed-world classes
     * @param methodRef original bytecode method reference
     * @return directly supported JDK method reference when normalizable
     */
    public static Optional<MethodRef> normalizeInheritedSupportedJdkCall(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef
    ) {
        if (isJdkCall(methodRef)) {
            return Optional.of(methodRef);
        }
        String current = methodRef.owner();
        while (classes.containsKey(current)) {
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                return Optional.empty();
            }
            if (classFile.method(methodRef.name(), methodRef.descriptor()).isPresent()) {
                return Optional.empty();
            }
            final String superName = classFile.superName();
            if (superName == null || superName.isEmpty()) {
                return Optional.empty();
            }
            final MethodRef inherited = new MethodRef(superName, methodRef.name(), methodRef.descriptor());
            if (isSupported(inherited)) {
                return Optional.of(inherited);
            }
            current = superName;
        }
        return Optional.empty();
    }

    public static Optional<Integer> builtinInstanceOfTargetId(final String target) {
        if ("java/util/Collection".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_COLLECTION);
        }
        if ("java/util/Map".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_MAP);
        }
        if ("java/util/Map$Entry".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_MAP_ENTRY);
        }
        if ("[Ljava/lang/Object;".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_OBJECT_ARRAY);
        }
        if ("[I".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_INT_ARRAY);
        }
        if ("[J".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_LONG_ARRAY);
        }
        if ("[F".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_FLOAT_ARRAY);
        }
        if ("[D".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_DOUBLE_ARRAY);
        }
        if ("[B".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_BYTE_ARRAY);
        }
        if ("[Z".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_BOOLEAN_ARRAY);
        }
        if ("[S".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_SHORT_ARRAY);
        }
        if ("[C".equals(target)) {
            return Optional.of(BUILTIN_INSTANCEOF_CHAR_ARRAY);
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
        if (!isSupported(methodRef)) {
            return List.of();
        }
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        if ("java/lang/System".equals(owner)) {
            return systemRuntimeModules(name);
        }
        if ("java/lang/Math".equals(owner)) {
            return List.of("math");
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
        if ("java/util/concurrent/Future".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/ScheduledThreadPoolExecutor".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/ScheduledExecutorService".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/locks/LockSupport".equals(owner)) {
            return List.of("threads");
        }
        if ("java/lang/ThreadLocal".equals(owner) || "java/lang/InheritableThreadLocal".equals(owner)) {
            return List.of("threads");
        }
        if ("java/util/concurrent/ThreadFactory".equals(owner)) {
            return List.of("threads");
        }
        if ("java/lang/Class".equals(owner) && "getResourceAsStream".equals(name)) {
            return List.of("resources");
        }
        if ("java/lang/ClassLoader".equals(owner)
            && ("getSystemClassLoader".equals(name)
            || "getSystemResourceAsStream".equals(name)
            || "getResourceAsStream".equals(name))) {
            return List.of("resources");
        }
        if ("java/util/Arrays".equals(owner)) {
            return List.of("arrays");
        }
        if ("java/util/Objects".equals(owner) && "toString".equals(name)) {
            return List.of("strings");
        }
        if (isStringRuntimeOwner(owner) || isNumberToStringCall(owner, name)) {
            return List.of("strings");
        }
        if (isBoxedPrimitiveOwner(owner)) {
            return List.of("managed-heap");
        }
        if ("java/time/Duration".equals(owner)) {
            return List.of("time");
        }
        if ("java/time/format/DateTimeFormatterBuilder".equals(owner)) {
            return List.of("time");
        }
        if ("java/nio/file/attribute/FileTime".equals(owner)) {
            return List.of("filesystem", "time");
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
        if ("java/lang/Byte".equals(owner)) {
            return true;
        }
        if ("java/lang/Short".equals(owner)) {
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
        if ("java/util/Collections".equals(owner)) {
            return true;
        }
        if ("java/util/List".equals(owner)) {
            return true;
        }
        if ("java/util/ArrayList".equals(owner)) {
            return true;
        }
        if ("java/util/AbstractList".equals(owner)) {
            return true;
        }
        if ("java/util/Collection".equals(owner)) {
            return true;
        }
        if ("java/util/Set".equals(owner)) {
            return true;
        }
        if ("java/util/HashSet".equals(owner)) {
            return true;
        }
        if ("java/util/LinkedHashSet".equals(owner)) {
            return true;
        }
        if ("java/lang/Iterable".equals(owner)) {
            return true;
        }
        if ("java/util/function/Predicate".equals(owner)) {
            return true;
        }
        return "java/util/Iterator".equals(owner)
            || "java/util/ListIterator".equals(owner);
    }

    private static boolean isMapRuntimeOwner(final String owner) {
        if ("java/util/Collections".equals(owner)) {
            return true;
        }
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

    private static final class SupportedCallIndex {
        private final List<String> owners = new java.util.ArrayList<>();
        private final List<List<MethodBucket>> ownerBuckets = new java.util.ArrayList<>();

        private SupportedCallIndex(final List<SupportedCall> calls) {
            for (final SupportedCall call : calls) {
                methodBucket(call.owner(), call.methodName()).calls().add(call);
            }
        }

        private List<SupportedCall> bucket(final String owner, final String methodName) {
            final List<MethodBucket> ownerBucket = existingOwnerBucket(owner);
            if (ownerBucket == null) {
                return List.of();
            }
            for (final MethodBucket bucket : ownerBucket) {
                if (bucket.methodName().equals(methodName)) {
                    return bucket.calls();
                }
            }
            return List.of();
        }

        private MethodBucket methodBucket(final String owner, final String methodName) {
            final List<MethodBucket> ownerBucket = ownerBucket(owner);
            for (final MethodBucket bucket : ownerBucket) {
                if (bucket.methodName().equals(methodName)) {
                    return bucket;
                }
            }
            final MethodBucket bucket = new MethodBucket(methodName, new java.util.ArrayList<>());
            ownerBucket.add(bucket);
            return bucket;
        }

        private List<MethodBucket> ownerBucket(final String owner) {
            final List<MethodBucket> existing = existingOwnerBucket(owner);
            if (existing != null) {
                return existing;
            }
            final List<MethodBucket> bucket = new java.util.ArrayList<>();
            owners.add(owner);
            ownerBuckets.add(bucket);
            return bucket;
        }

        private List<MethodBucket> existingOwnerBucket(final String owner) {
            for (int index = 0; index < owners.size(); index++) {
                if (owners.get(index).equals(owner)) {
                    return ownerBuckets.get(index);
                }
            }
            return null;
        }
    }

    private record MethodBucket(String methodName, List<SupportedCall> calls) {
    }

    /**
     * Supported JDK call kind.
     */
    public enum Kind {
        INTRINSIC,
        RUNTIME
    }
}
