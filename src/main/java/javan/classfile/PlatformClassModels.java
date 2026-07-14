package javan.classfile;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adds deterministic synthetic platform classes that javan models as data carriers inside the
 * lowered closed world.
 */
public final class PlatformClassModels {
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_FINAL = 0x0010;

    private PlatformClassModels() {
    }

    /**
     * Returns the input classes plus synthetic platform carrier classes needed by supported native
     * lowering paths.
     *
     * @param classes scanned application and dependency classes
     * @return expanded closed world with synthetic platform models
     */
    public static Map<String, ClassFile> expandedClasses(final Map<String, ClassFile> classes) {
        final Map<String, ClassFile> result = new LinkedHashMap<>();
        for (final ClassFile classFile : classes.values()) {
            result.put(classFile.name(), classFile);
        }
        if (requiresStackTraceElement(classes)) {
            result.putIfAbsent("java/lang/StackTraceElement", stackTraceElementClass());
        }
        if (requiresLogRecord(classes)) {
            result.putIfAbsent("java/util/logging/LogRecord", logRecordClass());
        }
        return Map.copyOf(result);
    }

    private static boolean requiresStackTraceElement(final Map<String, ClassFile> classes) {
        return referencesPlatformOwner(classes, "java/lang/StackTraceElement");
    }

    private static boolean requiresLogRecord(final Map<String, ClassFile> classes) {
        return referencesPlatformOwner(classes, "java/util/logging/LogRecord");
    }

    private static boolean referencesPlatformOwner(final Map<String, ClassFile> classes, final String owner) {
        if (classes.containsKey(owner)) {
            return true;
        }
        for (final ClassFile classFile : classes.values()) {
            if (descriptorMentionsOwner(classFile.superName(), owner)) {
                return true;
            }
            for (final String interfaceName : classFile.interfaces()) {
                if (descriptorMentionsOwner(interfaceName, owner)) {
                    return true;
                }
            }
            for (final FieldInfo field : classFile.fields()) {
                if (descriptorMentionsOwner(field.descriptor(), owner)) {
                    return true;
                }
            }
            for (final MethodInfo method : classFile.methods()) {
                if (descriptorMentionsOwner(method.descriptor(), owner)) {
                    return true;
                }
                if (method.code().isEmpty()) {
                    continue;
                }
                for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                    if (instruction.className().filter(owner::equals).isPresent()) {
                        return true;
                    }
                    if (instruction.methodRef().filter(methodRef -> owner.equals(methodRef.owner())).isPresent()) {
                        return true;
                    }
                    if (instruction.fieldRef().filter(fieldRef -> owner.equals(fieldRef.owner())).isPresent()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean descriptorMentionsOwner(final String descriptor, final String owner) {
        return descriptor != null && descriptor.contains("L" + owner + ";");
    }

    private static ClassFile stackTraceElementClass() {
        return new ClassFile(
            69,
            "java/lang/StackTraceElement",
            "java/lang/Object",
            ACC_PUBLIC | ACC_FINAL,
            List.of(),
            List.of(
                new FieldInfo(0, "declaringClass", "Ljava/lang/String;"),
                new FieldInfo(0, "methodName", "Ljava/lang/String;"),
                new FieldInfo(0, "fileName", "Ljava/lang/String;"),
                new FieldInfo(0, "lineNumber", "I")
            ),
            List.of(
                new MethodInfo(ACC_PUBLIC, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getClassName", "()Ljava/lang/String;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getMethodName", "()Ljava/lang/String;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getLineNumber", "()I", Optional.empty())
            ),
            Optional.of("StackTraceElement.java"),
            Path.of("jdk-platform/java/lang/StackTraceElement.class"),
            false
        );
    }

    private static ClassFile logRecordClass() {
        return new ClassFile(
            69,
            "java/util/logging/LogRecord",
            "java/lang/Object",
            ACC_PUBLIC,
            List.of(),
            List.of(
                new FieldInfo(0, "level", "Ljava/util/logging/Level;"),
                new FieldInfo(0, "message", "Ljava/lang/String;"),
                new FieldInfo(0, "millis", "J"),
                new FieldInfo(0, "parameters", "[Ljava/lang/Object;"),
                new FieldInfo(0, "thrown", "Ljava/lang/Throwable;"),
                new FieldInfo(0, "loggerName", "Ljava/lang/String;")
            ),
            List.of(
                new MethodInfo(
                    ACC_PUBLIC,
                    "<init>",
                    "(Ljava/util/logging/Level;Ljava/lang/String;)V",
                    Optional.empty()
                ),
                new MethodInfo(ACC_PUBLIC, "getLevel", "()Ljava/util/logging/Level;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getMessage", "()Ljava/lang/String;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getMillis", "()J", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getParameters", "()[Ljava/lang/Object;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "setParameters", "([Ljava/lang/Object;)V", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getThrown", "()Ljava/lang/Throwable;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "setThrown", "(Ljava/lang/Throwable;)V", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "getLoggerName", "()Ljava/lang/String;", Optional.empty()),
                new MethodInfo(ACC_PUBLIC, "setLoggerName", "(Ljava/lang/String;)V", Optional.empty())
            ),
            Optional.of("LogRecord.java"),
            Path.of("jdk-platform/java/util/logging/LogRecord.class"),
            false
        );
    }
}
