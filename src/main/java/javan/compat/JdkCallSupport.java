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
        intrinsic("Objects.requireNonNull", "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),
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
        runtime("Thread.<init>", "java/lang/Thread", "<init>", "()V", "(Ljava/lang/Runnable;)V"),
        runtime("Thread.ofVirtual", "java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;"),
        runtime("Thread.startVirtualThread", "java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.name", "java/lang/Thread$Builder", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder;", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;"),
        runtime("Thread.Builder.start", "java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.unstarted", "java/lang/Thread$Builder", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"),
        runtime("Thread.Builder.factory", "java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;"),
        runtime("Thread.Builder.toString", "java/lang/Thread$Builder", "toString", "()Ljava/lang/String;"),
        runtime("Thread.Builder.hashCode", "java/lang/Thread$Builder", "hashCode", "()I"),
        runtime("Thread.Builder.equals", "java/lang/Thread$Builder", "equals", "(Ljava/lang/Object;)Z"),
        runtime("Thread.Builder.OfVirtual.name", "java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;"),
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
        runtime("ExecutorService.shutdown", "java/util/concurrent/ExecutorService", "shutdown", "()V"),
        runtime("ExecutorService.close", "java/util/concurrent/ExecutorService", "close", "()V"),
        runtime("ExecutorService.toString", "java/util/concurrent/ExecutorService", "toString", "()Ljava/lang/String;"),
        runtime("ExecutorService.hashCode", "java/util/concurrent/ExecutorService", "hashCode", "()I"),
        runtime("ExecutorService.equals", "java/util/concurrent/ExecutorService", "equals", "(Ljava/lang/Object;)Z"),
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
        runtime("Integer.valueOf", "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"),
        runtime("Integer.intValue", "java/lang/Integer", "intValue", "()I"),
        intrinsic("Long.toString", "java/lang/Long", "toString", "(J)Ljava/lang/String;"),
        runtime("Long.valueOf", "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;"),
        runtime("Long.longValue", "java/lang/Long", "longValue", "()J"),
        intrinsic("Float.toString", "java/lang/Float", "toString", "(F)Ljava/lang/String;"),
        runtime("Float.valueOf", "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;"),
        runtime("Float.floatValue", "java/lang/Float", "floatValue", "()F"),
        intrinsic("Float.intBitsToFloat", "java/lang/Float", "intBitsToFloat", "(I)F"),
        intrinsic("Double.toString", "java/lang/Double", "toString", "(D)Ljava/lang/String;"),
        runtime("Double.valueOf", "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;"),
        runtime("Double.doubleValue", "java/lang/Double", "doubleValue", "()D"),
        intrinsic("Double.longBitsToDouble", "java/lang/Double", "longBitsToDouble", "(J)D"),
        intrinsic("Boolean.toString", "java/lang/Boolean", "toString", "(Z)Ljava/lang/String;"),
        runtime("Boolean.valueOf", "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"),
        runtime("Boolean.booleanValue", "java/lang/Boolean", "booleanValue", "()Z"),
        intrinsic(
            "String.valueOf",
            "java/lang/String",
            "valueOf",
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
        runtime("PrintStream.print", "java/io/PrintStream", "print", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "([C)V", "(C)V", "(Z)V", "(I)V", "(J)V", "(F)V", "(D)V"),
        runtime("PrintStream.println", "java/io/PrintStream", "println", "()V", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "([C)V", "(I)V", "(J)V", "(F)V", "(D)V", "(Z)V", "(C)V"),
        runtime("String.<init>", "java/lang/String", "<init>", "()V", "(Ljava/lang/String;)V", "([C)V", "([CII)V"),
        runtime("String.length", "java/lang/String", "length", "()I"),
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
        runtime("String.trim", "java/lang/String", "trim", "()Ljava/lang/String;"),
        runtime("String.substring", "java/lang/String", "substring", "(I)Ljava/lang/String;"),
        runtime("String.substring", "java/lang/String", "substring", "(II)Ljava/lang/String;"),
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
        runtime("StringBuilder.insert", "java/lang/StringBuilder", "insert", "(ILjava/lang/String;)Ljava/lang/StringBuilder;", "(IZ)Ljava/lang/StringBuilder;", "(IC)Ljava/lang/StringBuilder;", "(II)Ljava/lang/StringBuilder;", "(IJ)Ljava/lang/StringBuilder;", "(IF)Ljava/lang/StringBuilder;", "(ID)Ljava/lang/StringBuilder;", "(I[C)Ljava/lang/StringBuilder;", "(I[CII)Ljava/lang/StringBuilder;"),
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
        runtime("List.addAll", "java/util/List", "addAll", "(Ljava/util/Collection;)Z"),
        runtime("List.size", "java/util/List", "size", "()I"),
        runtime("List.isEmpty", "java/util/List", "isEmpty", "()Z"),
        runtime("List.contains", "java/util/List", "contains", "(Ljava/lang/Object;)Z"),
        runtime("Collection.contains", "java/util/Collection", "contains", "(Ljava/lang/Object;)Z"),
        runtime("List.get", "java/util/List", "get", "(I)Ljava/lang/Object;"),
        runtime("List.getFirst", "java/util/List", "getFirst", "()Ljava/lang/Object;"),
        runtime("List.getLast", "java/util/List", "getLast", "()Ljava/lang/Object;"),
        runtime("List.removeLast", "java/util/List", "removeLast", "()Ljava/lang/Object;"),
        runtime("List.set", "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"),
        runtime("List.addFirst", "java/util/List", "addFirst", "(Ljava/lang/Object;)V"),
        runtime("List.iterator", "java/util/List", "iterator", "()Ljava/util/Iterator;"),
        runtime("Collection.iterator", "java/util/Collection", "iterator", "()Ljava/util/Iterator;"),
        runtime("Iterator.hasNext", "java/util/Iterator", "hasNext", "()Z"),
        runtime("Iterator.next", "java/util/Iterator", "next", "()Ljava/lang/Object;"),
        runtime("HashMap.<init>", "java/util/HashMap", "<init>", "()V"),
        runtime("LinkedHashMap.<init>", "java/util/LinkedHashMap", "<init>", "()V"),
        runtime("TreeMap.<init>", "java/util/TreeMap", "<init>", "()V"),
        runtime("Map.copyOf", "java/util/Map", "copyOf", "(Ljava/util/Map;)Ljava/util/Map;"),
        runtime("Map.get", "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.get", "java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.get", "java/util/LinkedHashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.get", "java/util/TreeMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.getOrDefault", "java/util/Map", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.put", "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.putIfAbsent", "java/util/Map", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.put", "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.put", "java/util/LinkedHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.put", "java/util/TreeMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("HashMap.putIfAbsent", "java/util/HashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.putIfAbsent", "java/util/LinkedHashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.putIfAbsent", "java/util/TreeMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("Map.containsKey", "java/util/Map", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("HashMap.containsKey", "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("LinkedHashMap.containsKey", "java/util/LinkedHashMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("TreeMap.containsKey", "java/util/TreeMap", "containsKey", "(Ljava/lang/Object;)Z"),
        runtime("Map.size", "java/util/Map", "size", "()I"),
        runtime("HashMap.size", "java/util/HashMap", "size", "()I"),
        runtime("LinkedHashMap.size", "java/util/LinkedHashMap", "size", "()I"),
        runtime("TreeMap.size", "java/util/TreeMap", "size", "()I"),
        runtime("Map.isEmpty", "java/util/Map", "isEmpty", "()Z"),
        runtime("HashMap.isEmpty", "java/util/HashMap", "isEmpty", "()Z"),
        runtime("LinkedHashMap.isEmpty", "java/util/LinkedHashMap", "isEmpty", "()Z"),
        runtime("TreeMap.isEmpty", "java/util/TreeMap", "isEmpty", "()Z"),
        runtime("Map.values", "java/util/Map", "values", "()Ljava/util/Collection;"),
        runtime("HashMap.values", "java/util/HashMap", "values", "()Ljava/util/Collection;"),
        runtime("LinkedHashMap.values", "java/util/LinkedHashMap", "values", "()Ljava/util/Collection;"),
        runtime("TreeMap.values", "java/util/TreeMap", "values", "()Ljava/util/Collection;"),
        runtime("HashMap.getOrDefault", "java/util/HashMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("LinkedHashMap.getOrDefault", "java/util/LinkedHashMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        runtime("TreeMap.getOrDefault", "java/util/TreeMap", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
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
        runtime("Socket.<init>", "java/net/Socket", "<init>", "(Ljava/lang/String;I)V"),
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
        runtime("Optional.orElseThrow", "java/util/Optional", "orElseThrow", "()Ljava/lang/Object;")
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
        if ("java/util/List".equals(methodRef.owner())) {
            return isSupportedListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/ArrayList".equals(methodRef.owner())) {
            return isSupportedArrayListCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Collection".equals(methodRef.owner())) {
            return isSupportedCollectionCall(methodRef.name(), methodRef.descriptor());
        }
        if ("java/util/Iterator".equals(methodRef.owner())) {
            return isSupportedIteratorCall(methodRef.name(), methodRef.descriptor());
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
        if ("get".equals(name)) {
            return "(I)Ljava/lang/Object;".equals(descriptor);
        }
        if ("getFirst".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("getLast".equals(name)) {
            return "()Ljava/lang/Object;".equals(descriptor);
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
        if ("iterator".equals(name)) {
            return "()Ljava/util/Iterator;".equals(descriptor);
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
        if (!"addAll".equals(name)) {
            return false;
        }
        return "(Ljava/util/Collection;)Z".equals(descriptor);
    }

    private static boolean isSupportedCollectionCall(final String name, final String descriptor) {
        if ("contains".equals(name)) {
            return "(Ljava/lang/Object;)Z".equals(descriptor);
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
        if ("java/util/concurrent/locks/LockSupport".equals(owner)) {
            return List.of("threads");
        }
        if ("java/lang/ThreadLocal".equals(owner)) {
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
        if (isBoxedPrimitiveOwner(owner)) {
            return List.of("managed-heap");
        }
        if ("java/time/Duration".equals(owner)) {
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
        if ("java/util/Collection".equals(owner)) {
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
        return "java/util/TreeMap".equals(owner);
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

    /**
     * Supported JDK call kind.
     */
    public enum Kind {
        INTRINSIC,
        RUNTIME
    }
}
