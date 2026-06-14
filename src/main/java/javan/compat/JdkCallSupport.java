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
        intrinsic("Math.abs", "java/lang/Math", "abs", "(I)I", "(J)J"),
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
        intrinsic(
            "Arrays.copyOf",
            "java/util/Arrays",
            "copyOf",
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
        runtime("PrintStream.print", "java/io/PrintStream", "print", "(Ljava/lang/String;)V"),
        runtime("PrintStream.println", "java/io/PrintStream", "println", "(Ljava/lang/String;)V", "(Ljava/lang/Object;)V", "(I)V", "(J)V", "(F)V", "(D)V", "(Z)V"),
        runtime("String.<init>", "java/lang/String", "<init>", "([CII)V"),
        runtime("String.length", "java/lang/String", "length", "()I"),
        runtime("String.isEmpty", "java/lang/String", "isEmpty", "()Z"),
        runtime("String.charAt", "java/lang/String", "charAt", "(I)C"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(I)I"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(II)I"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(Ljava/lang/String;)I"),
        runtime("String.indexOf", "java/lang/String", "indexOf", "(Ljava/lang/String;I)I"),
        runtime("String.lastIndexOf", "java/lang/String", "lastIndexOf", "(I)I"),
        runtime("String.lastIndexOf", "java/lang/String", "lastIndexOf", "(II)I"),
        runtime("String.equals", "java/lang/String", "equals", "(Ljava/lang/Object;)Z"),
        runtime("String.contains", "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z"),
        runtime("String.startsWith", "java/lang/String", "startsWith", "(Ljava/lang/String;)Z"),
        runtime("String.endsWith", "java/lang/String", "endsWith", "(Ljava/lang/String;)Z"),
        runtime("String.replace", "java/lang/String", "replace", "(CC)Ljava/lang/String;"),
        runtime("String.intern", "java/lang/String", "intern", "()Ljava/lang/String;"),
        runtime("String.trim", "java/lang/String", "trim", "()Ljava/lang/String;"),
        runtime("String.substring", "java/lang/String", "substring", "(I)Ljava/lang/String;"),
        runtime("String.substring", "java/lang/String", "substring", "(II)Ljava/lang/String;"),
        runtime("StringBuilder.<init>", "java/lang/StringBuilder", "<init>", "()V", "(Ljava/lang/String;)V"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.append", "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;"),
        runtime("StringBuilder.toString", "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
        runtime("StringBuilder.length", "java/lang/StringBuilder", "length", "()I"),
        runtime("StringBuilder.isEmpty", "java/lang/StringBuilder", "isEmpty", "()Z"),
        runtime("StringBuilder.setLength", "java/lang/StringBuilder", "setLength", "(I)V"),
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
        runtime("Map.size", "java/util/Map", "size", "()I"),
        runtime("Map.isEmpty", "java/util/Map", "isEmpty", "()Z"),
        runtime("Map.values", "java/util/Map", "values", "()Ljava/util/Collection;"),
        runtime("Path.of", "java/nio/file/Path", "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"),
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
        runtime("Files.newDirectoryStream", "java/nio/file/Files", "newDirectoryStream", "(Ljava/nio/file/Path;)Ljava/nio/file/DirectoryStream;"),
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
