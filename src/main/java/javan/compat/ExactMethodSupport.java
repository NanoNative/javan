package javan.compat;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactoryCall;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.List;
import java.util.Optional;

/**
 * Exact bytecode-shape recognizers for deterministic non-JDK compatibility slices.
 */
public final class ExactMethodSupport {
    private static final String OBJECT_TO_OBJECT_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String FALLIBLE_APPLY_METHOD_NAME = "applyWithException";
    private static final String TEMPORAL_OF_METHOD_NAME = "temporalOf";
    private static final String TO_TIMESTAMP_MS_METHOD_NAME = "toTimestampMs";
    private static final String CONVERT_OBJECT_METHOD_NAME = "convertObj";
    private static final String CALENDAR_OF_METHOD_NAME = "calendarOf";
    private static final String STRING_OF_METHOD_NAME = "stringOf";
    private static final String EXTRACT_CAUSE_METHOD_NAME = "extractCause";
    private static final MethodRef NUMBER_INT_VALUE = new MethodRef("java/lang/Number", "intValue", "()I");
    private static final MethodRef CLASS_GET_ENUM_CONSTANTS = new MethodRef("java/lang/Class", "getEnumConstants", "()[Ljava/lang/Object;");
    private static final MethodRef STRING_VALUE_OF_OBJECT = new MethodRef("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
    private static final MethodRef ENUM_VALUE_OF = new MethodRef("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
    private static final MethodRef DATE_TIME_FORMATTER_PARSE =
        new MethodRef("java/time/format/DateTimeFormatter", "parse", "(Ljava/lang/CharSequence;)Ljava/time/temporal/TemporalAccessor;");
    private static final MethodRef FUNCTION_APPLY =
        new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
    private static final MethodRef LONG_PARSE_LONG = new MethodRef("java/lang/Long", "parseLong", "(Ljava/lang/String;)J");
    private static final MethodRef LONG_VALUE_OF = new MethodRef("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
    private static final MethodRef STRING_BUILDER_INIT = new MethodRef("java/lang/StringBuilder", "<init>", "()V");
    private static final MethodRef STRING_BUILDER_APPEND_OBJECT =
        new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
    private static final MethodRef STRING_BUILDER_APPEND_STRING =
        new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    private static final MethodRef THROWABLE_GET_CAUSE =
        new MethodRef("java/lang/Throwable", "getCause", "()Ljava/lang/Throwable;");
    private static final MethodRef STRING_BUILDER_TO_STRING =
        new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");

    private ExactMethodSupport() {
    }

    /**
     * Returns whether the method belongs to the current exact-lowered compatibility subset.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method is replaced by exact native lowering
     */
    public static boolean isExactLoweredMethod(final ClassFile classFile, final MethodInfo method) {
        return isExactCatchNullEnumLookupMethod(classFile, method)
            || isExactCatchNullFunctionOrNullApplyMethod(classFile, method)
            || isExactTemporalOfLoopFallbackMethod(classFile, method)
            || isExactTemporalStringBridgeMethod(classFile, method)
            || isExactCalendarOfEpochMillisMethod(classFile, method)
            || isExactCalendarOfDateMethod(classFile, method)
            || isExactCalendarOfLocalTimeMethod(classFile, method)
            || isExactThrowableStringOfMethod(classFile, method)
            || isExactUnsupportedTemporalConversionLambdaMethod(classFile, method);
    }

    /**
     * Returns true when the method matches the current exact catch-null enum lookup slice.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted exact bytecode shape
     */
    public static boolean isExactCatchNullEnumLookupMethod(final ClassFile classFile, final MethodInfo method) {
        if (classFile == null || method == null || !method.isStatic()) {
            return false;
        }
        if (!"(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Enum;".equals(method.descriptor())) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty()) {
            return false;
        }
        if (!hasExactCatchNullEnumLookupExceptionTable(code.orElseThrow().exceptionTable())) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() != 31) {
            return false;
        }
        return matchesExactCatchNullEnumLookupSyntheticShape(instructions)
            || matchesExactCatchNullEnumLookupJavacShape(instructions);
    }

    private static boolean matchesExactCatchNullEnumLookupSyntheticShape(final List<Instruction> instructions) {
        return sameInstruction(instructions, 0, 0, 42)
            && sameClassInstruction(instructions, 1, 1, 193, "java/lang/Number")
            && sameInstruction(instructions, 2, 4, 153)
            && sameInstruction(instructions, 3, 7, 42)
            && sameClassInstruction(instructions, 4, 8, 192, "java/lang/Number")
            && sameMethodInstruction(instructions, 5, 11, 182, NUMBER_INT_VALUE)
            && sameInstruction(instructions, 6, 14, 61)
            && sameInstruction(instructions, 7, 15, 43)
            && sameMethodInstruction(instructions, 8, 16, 182, CLASS_GET_ENUM_CONSTANTS)
            && sameClassInstruction(instructions, 9, 19, 192, "[Ljava/lang/Enum;")
            && sameInstruction(instructions, 10, 22, 78)
            && sameInstruction(instructions, 11, 23, 28)
            && sameInstruction(instructions, 12, 24, 155)
            && sameInstruction(instructions, 13, 27, 28)
            && sameInstruction(instructions, 14, 28, 45)
            && sameInstruction(instructions, 15, 29, 190)
            && sameInstruction(instructions, 16, 30, 162)
            && sameInstruction(instructions, 17, 33, 45)
            && sameInstruction(instructions, 18, 34, 28)
            && sameInstruction(instructions, 19, 35, 50)
            && sameInstruction(instructions, 20, 36, 176)
            && sameInstruction(instructions, 21, 37, 1)
            && sameInstruction(instructions, 22, 38, 176)
            && sameInstruction(instructions, 23, 39, 43)
            && sameInstruction(instructions, 24, 40, 42)
            && sameMethodInstruction(instructions, 25, 41, 184, STRING_VALUE_OF_OBJECT)
            && sameMethodInstruction(instructions, 26, 44, 184, ENUM_VALUE_OF)
            && sameInstruction(instructions, 27, 47, 176)
            && sameInstruction(instructions, 28, 48, 77)
            && sameInstruction(instructions, 29, 49, 1)
            && sameInstruction(instructions, 30, 50, 176);
    }

    private static boolean matchesExactCatchNullEnumLookupJavacShape(final List<Instruction> instructions) {
        return sameInstruction(instructions, 0, 0, 42)
            && sameClassInstruction(instructions, 1, 1, 193, "java/lang/Number")
            && sameInstruction(instructions, 2, 4, 153)
            && sameInstruction(instructions, 3, 7, 42)
            && sameClassInstruction(instructions, 4, 8, 192, "java/lang/Number")
            && sameMethodInstruction(instructions, 5, 11, 182, NUMBER_INT_VALUE)
            && sameInstruction(instructions, 6, 14, 61)
            && sameInstruction(instructions, 7, 15, 43)
            && sameMethodInstruction(instructions, 8, 16, 182, CLASS_GET_ENUM_CONSTANTS)
            && sameClassInstruction(instructions, 9, 19, 192, "[Ljava/lang/Enum;")
            && sameInstruction(instructions, 10, 22, 78)
            && sameInstruction(instructions, 11, 23, 28)
            && sameInstruction(instructions, 12, 24, 155)
            && sameInstruction(instructions, 13, 27, 28)
            && sameInstruction(instructions, 14, 28, 45)
            && sameInstruction(instructions, 15, 29, 190)
            && sameInstruction(instructions, 16, 30, 162)
            && sameInstruction(instructions, 17, 33, 45)
            && sameInstruction(instructions, 18, 34, 28)
            && sameInstruction(instructions, 19, 35, 50)
            && sameInstruction(instructions, 20, 36, 167)
            && sameInstruction(instructions, 21, 39, 1)
            && sameInstruction(instructions, 22, 40, 176)
            && sameInstruction(instructions, 23, 41, 43)
            && sameInstruction(instructions, 24, 42, 42)
            && sameMethodInstruction(instructions, 25, 43, 184, STRING_VALUE_OF_OBJECT)
            && sameMethodInstruction(instructions, 26, 46, 184, ENUM_VALUE_OF)
            && sameInstruction(instructions, 27, 49, 176)
            && sameInstruction(instructions, 28, 50, 77)
            && sameInstruction(instructions, 29, 51, 1)
            && sameInstruction(instructions, 30, 52, 176);
    }

    /**
     * Returns true when the method matches the current exact catch-null default functional bridge slice.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted exact bytecode shape
     */
    public static boolean isExactCatchNullFunctionOrNullApplyMethod(final ClassFile classFile, final MethodInfo method) {
        if (classFile == null || method == null || method.isStatic()) {
            return false;
        }
        if (!classFile.isInterface()) {
            return false;
        }
        if (!"apply".equals(method.name()) || !OBJECT_TO_OBJECT_DESCRIPTOR.equals(method.descriptor())) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty()) {
            return false;
        }
        if (!hasExactCatchNullFunctionOrNullApplyExceptionTable(code.orElseThrow().exceptionTable())) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() != 7) {
            return false;
        }
        final MethodRef fallibleApply = new MethodRef(classFile.name(), FALLIBLE_APPLY_METHOD_NAME, OBJECT_TO_OBJECT_DESCRIPTOR);
        return sameInstruction(instructions, 0, 0, 42)
            && sameInstruction(instructions, 1, 1, 43)
            && sameMethodInstruction(instructions, 2, 2, 185, fallibleApply)
            && sameInstruction(instructions, 3, 7, 176)
            && sameInstruction(instructions, 4, 8, 77)
            && sameInstruction(instructions, 5, 9, 1)
            && sameInstruction(instructions, 6, 10, 176);
    }

    /**
     * Returns true when the method matches the current exact String-to-temporal registration bridge shape.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted bytecode bridge shape
     */
    public static boolean isExactTemporalStringBridgeMethod(final ClassFile classFile, final MethodInfo method) {
        if (classFile == null || method == null || !method.isStatic()) {
            return false;
        }
        final Optional<String> targetOwner = objectOwner(returnDescriptor(method.descriptor()).orElse(""));
        if (targetOwner.isEmpty()) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty() || !code.orElseThrow().exceptionTable().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() != 6) {
            return false;
        }
        final String targetDescriptor = "L" + targetOwner.orElseThrow() + ";";
        return sameClassInstruction(instructions, 0, 0, 18, targetOwner.orElseThrow())
            && sameInstruction(instructions, 1, 2, 42)
            && sameTemporalStringBridgeLambdaInstruction(instructions.get(2), classFile.name(), targetDescriptor)
            && sameMethodInstruction(instructions, 3, 8, 184, new MethodRef(
                classFile.name(),
                TEMPORAL_OF_METHOD_NAME,
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/function/Function;)Ljava/lang/Object;"
            ))
            && sameClassInstruction(instructions, 4, 11, 192, targetOwner.orElseThrow())
            && sameInstruction(instructions, 5, 14, 176);
    }

    /**
     * Returns the exact target owner for the current String-to-temporal bridge shape.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return internal JVM owner when this is the exact bridge shape
     */
    public static Optional<String> exactTemporalStringBridgeTargetInternalName(final ClassFile classFile, final MethodInfo method) {
        if (!isExactTemporalStringBridgeMethod(classFile, method)) {
            return Optional.empty();
        }
        return objectOwner(returnDescriptor(method.descriptor()).orElse(""));
    }

    /**
     * Returns true when the method matches the current exact Calendar-from-epoch-millis helper shape.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted helper shape
     */
    public static boolean isExactCalendarOfEpochMillisMethod(final ClassFile classFile, final MethodInfo method) {
        if (!isCalendarHelperMethod(classFile, method, "(J)Ljava/util/Calendar;")) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        if (instructions.size() != 7) {
            return false;
        }
        return sameMethodInstruction(instructions, 0, 0, 184, new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;"))
            && sameInstruction(instructions, 1, 3, 77)
            && sameInstruction(instructions, 2, 4, 44)
            && sameInstruction(instructions, 3, 5, 30)
            && sameMethodInstruction(instructions, 4, 6, 182, new MethodRef("java/util/Calendar", "setTimeInMillis", "(J)V"))
            && sameInstruction(instructions, 5, 9, 44)
            && sameInstruction(instructions, 6, 10, 176);
    }

    /**
     * Returns true when the method matches the current exact Calendar-from-Date helper shape.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted helper shape
     */
    public static boolean isExactCalendarOfDateMethod(final ClassFile classFile, final MethodInfo method) {
        if (!isCalendarHelperMethod(classFile, method, "(Ljava/util/Date;)Ljava/util/Calendar;")) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        if (instructions.size() != 7) {
            return false;
        }
        return sameMethodInstruction(instructions, 0, 0, 184, new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;"))
            && sameInstruction(instructions, 1, 3, 76)
            && sameMethodInstruction(instructions, 2, 4, 184, new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;"))
            && sameInstruction(instructions, 3, 7, 42)
            && sameMethodInstruction(instructions, 4, 8, 182, new MethodRef("java/util/Calendar", "setTime", "(Ljava/util/Date;)V"))
            && sameInstruction(instructions, 5, 11, 43)
            && sameInstruction(instructions, 6, 12, 176);
    }

    /**
     * Returns true when the method matches the current exact Calendar-from-LocalTime helper shape.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted helper shape
     */
    public static boolean isExactCalendarOfLocalTimeMethod(final ClassFile classFile, final MethodInfo method) {
        if (!isCalendarHelperMethod(classFile, method, "(Ljava/time/LocalTime;)Ljava/util/Calendar;")) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        if (instructions.size() != 26) {
            return false;
        }
        return sameMethodInstruction(instructions, 0, 0, 184, new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;"))
            && sameInstruction(instructions, 1, 3, 76)
            && sameInstruction(instructions, 2, 4, 43)
            && sameInstruction(instructions, 3, 5, 16)
            && sameInstruction(instructions, 4, 7, 42)
            && sameMethodInstruction(instructions, 5, 8, 182, new MethodRef("java/time/LocalTime", "getHour", "()I"))
            && sameMethodInstruction(instructions, 6, 11, 182, new MethodRef("java/util/Calendar", "set", "(II)V"))
            && sameInstruction(instructions, 7, 14, 43)
            && sameInstruction(instructions, 8, 15, 16)
            && sameInstruction(instructions, 9, 17, 42)
            && sameMethodInstruction(instructions, 10, 18, 182, new MethodRef("java/time/LocalTime", "getMinute", "()I"))
            && sameMethodInstruction(instructions, 11, 21, 182, new MethodRef("java/util/Calendar", "set", "(II)V"))
            && sameInstruction(instructions, 12, 24, 43)
            && sameInstruction(instructions, 13, 25, 16)
            && sameInstruction(instructions, 14, 27, 42)
            && sameMethodInstruction(instructions, 15, 28, 182, new MethodRef("java/time/LocalTime", "getSecond", "()I"))
            && sameMethodInstruction(instructions, 16, 31, 182, new MethodRef("java/util/Calendar", "set", "(II)V"))
            && sameInstruction(instructions, 17, 34, 43)
            && sameInstruction(instructions, 18, 35, 16)
            && sameInstruction(instructions, 19, 37, 42)
            && sameMethodInstruction(instructions, 20, 38, 182, new MethodRef("java/time/LocalTime", "getNano", "()I"))
            && sameInstruction(instructions, 21, 41, 18)
            && sameInstruction(instructions, 22, 43, 108)
            && sameMethodInstruction(instructions, 23, 44, 182, new MethodRef("java/util/Calendar", "set", "(II)V"))
            && sameInstruction(instructions, 24, 47, 43)
            && sameInstruction(instructions, 25, 48, 176);
    }

    /**
     * Returns true when the method matches the current exact Throwable-to-String helper shape.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted helper shape
     */
    public static boolean isExactThrowableStringOfMethod(final ClassFile classFile, final MethodInfo method) {
        if (classFile == null || method == null || !method.isStatic()) {
            return false;
        }
        if (!STRING_OF_METHOD_NAME.equals(method.name()) || !"(Ljava/lang/Throwable;)Ljava/lang/String;".equals(method.descriptor())) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty() || !code.orElseThrow().exceptionTable().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() != 38) {
            return false;
        }
        return sameClassInstruction(instructions, 0, 0, 187, "java/lang/StringBuilder")
            && sameInstruction(instructions, 1, 3, 89)
            && sameMethodInstruction(instructions, 2, 4, 183, STRING_BUILDER_INIT)
            && sameInstruction(instructions, 3, 7, 76)
            && sameInstruction(instructions, 4, 8, 43)
            && sameInstruction(instructions, 5, 9, 42)
            && sameMethodInstruction(instructions, 6, 10, 182, STRING_BUILDER_APPEND_OBJECT)
            && sameFieldInstruction(instructions, 7, 13, 178, classFile.name(), "LINE_SEPARATOR", "Ljava/lang/String;")
            && sameMethodInstruction(instructions, 8, 16, 182, STRING_BUILDER_APPEND_STRING)
            && sameInstruction(instructions, 9, 19, 87)
            && sameInstruction(instructions, 10, 20, 43)
            && sameInstruction(instructions, 11, 21, 42)
            && sameInstruction(instructions, 12, 22, 3)
            && sameMethodInstruction(instructions, 13, 23, 184, new MethodRef(
                classFile.name(),
                EXTRACT_CAUSE_METHOD_NAME,
                "(Ljava/lang/StringBuilder;Ljava/lang/Throwable;Z)V"
            ))
            && sameInstruction(instructions, 14, 26, 42)
            && sameMethodInstruction(instructions, 15, 27, 182, THROWABLE_GET_CAUSE)
            && sameInstruction(instructions, 16, 30, 77)
            && sameInstruction(instructions, 17, 31, 44)
            && sameInstruction(instructions, 18, 32, 198)
            && sameInstruction(instructions, 19, 35, 43)
            && sameInstruction(instructions, 20, 36, 18)
            && sameMethodInstruction(instructions, 21, 38, 182, STRING_BUILDER_APPEND_STRING)
            && sameInstruction(instructions, 22, 41, 44)
            && sameMethodInstruction(instructions, 23, 42, 182, STRING_BUILDER_APPEND_OBJECT)
            && sameFieldInstruction(instructions, 24, 45, 178, classFile.name(), "LINE_SEPARATOR", "Ljava/lang/String;")
            && sameMethodInstruction(instructions, 25, 48, 182, STRING_BUILDER_APPEND_STRING)
            && sameInstruction(instructions, 26, 51, 87)
            && sameInstruction(instructions, 27, 52, 43)
            && sameInstruction(instructions, 28, 53, 44)
            && sameInstruction(instructions, 29, 54, 3)
            && sameMethodInstruction(instructions, 30, 55, 184, new MethodRef(
                classFile.name(),
                EXTRACT_CAUSE_METHOD_NAME,
                "(Ljava/lang/StringBuilder;Ljava/lang/Throwable;Z)V"
            ))
            && sameInstruction(instructions, 31, 58, 44)
            && sameMethodInstruction(instructions, 32, 59, 182, THROWABLE_GET_CAUSE)
            && sameInstruction(instructions, 33, 62, 77)
            && sameInstruction(instructions, 34, 63, 167)
            && sameInstruction(instructions, 35, 66, 43)
            && sameMethodInstruction(instructions, 36, 67, 182, STRING_BUILDER_TO_STRING)
            && sameInstruction(instructions, 37, 70, 176);
    }

    /**
     * Returns true when the method matches the current linear unsupported temporal/sql conversion-lambda subset.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method is a deterministic unsupported conversion helper shape
     */
    public static boolean isExactUnsupportedTemporalConversionLambdaMethod(final ClassFile classFile, final MethodInfo method) {
        if (classFile == null || method == null || !method.isStatic()) {
            return false;
        }
        if (!method.name().startsWith("lambda$static$")) {
            return false;
        }
        final Optional<String> returnOwner = objectOwner(returnDescriptor(method.descriptor()).orElse(""));
        if (returnOwner.isEmpty()
            || (!isUnsupportedTemporalOwner(returnOwner.orElseThrow())
            && !"java/lang/Long".equals(returnOwner.orElseThrow()))) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty() || !code.orElseThrow().exceptionTable().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.isEmpty()) {
            return false;
        }
        for (final Instruction instruction : instructions) {
            if (!isSupportedUnsupportedTemporalInstruction(classFile.name(), instruction)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true when the method matches the current exact temporalOf loop/fallback slice.
     *
     * @param classFile owning class
     * @param method candidate method
     * @return true when the method matches the admitted exact bytecode shape
     */
    public static boolean isExactTemporalOfLoopFallbackMethod(final ClassFile classFile, final MethodInfo method) {
        if (classFile == null || method == null || !method.isStatic()) {
            return false;
        }
        if (!TEMPORAL_OF_METHOD_NAME.equals(method.name())
            || !"(Ljava/lang/Class;Ljava/lang/String;Ljava/util/function/Function;)Ljava/lang/Object;".equals(method.descriptor())) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty()) {
            return false;
        }
        if (!hasExactTemporalOfLoopFallbackExceptionTable(code.orElseThrow().exceptionTable())) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() != 33) {
            return false;
        }
        return sameFieldInstruction(instructions, 0, 0, 178, classFile.name(), "DATE_TIME_FORMATTERS", "[Ljava/time/format/DateTimeFormatter;")
            && sameInstruction(instructions, 1, 3, 78)
            && sameInstruction(instructions, 2, 4, 45)
            && sameInstruction(instructions, 3, 5, 190)
            && sameInstruction(instructions, 4, 6, 54)
            && sameInstruction(instructions, 5, 8, 3)
            && sameInstruction(instructions, 6, 9, 54)
            && sameInstruction(instructions, 7, 11, 21)
            && sameInstruction(instructions, 8, 13, 21)
            && sameInstruction(instructions, 9, 15, 162)
            && sameInstruction(instructions, 10, 18, 45)
            && sameInstruction(instructions, 11, 19, 21)
            && sameInstruction(instructions, 12, 21, 50)
            && sameInstruction(instructions, 13, 22, 58)
            && sameInstruction(instructions, 14, 24, 44)
            && sameInstruction(instructions, 15, 25, 25)
            && sameInstruction(instructions, 16, 27, 43)
            && sameMethodInstruction(instructions, 17, 28, 182, DATE_TIME_FORMATTER_PARSE)
            && sameMethodInstruction(instructions, 18, 31, 185, FUNCTION_APPLY)
            && sameInstruction(instructions, 19, 36, 176)
            && sameInstruction(instructions, 20, 37, 58)
            && sameInstruction(instructions, 21, 39, 43)
            && sameMethodInstruction(instructions, 22, 40, 184, LONG_PARSE_LONG)
            && sameMethodInstruction(instructions, 23, 43, 184, new MethodRef(classFile.name(), TO_TIMESTAMP_MS_METHOD_NAME, "(J)J"))
            && sameMethodInstruction(instructions, 24, 46, 184, LONG_VALUE_OF)
            && sameInstruction(instructions, 25, 49, 42)
            && sameNamedMethodInstruction(instructions, 26, 50, 184, CONVERT_OBJECT_METHOD_NAME, "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;")
            && sameInstruction(instructions, 27, 53, 176)
            && sameInstruction(instructions, 28, 54, 58)
            && sameInstruction(instructions, 29, 56, 132)
            && sameInstruction(instructions, 30, 59, 167)
            && sameInstruction(instructions, 31, 62, 1)
            && sameInstruction(instructions, 32, 63, 176);
    }

    private static boolean hasExactCatchNullEnumLookupExceptionTable(final List<CodeException> handlers) {
        if (handlers.size() == 3) {
            return sameHandler(handlers.get(0), 0, 36, 48)
                && sameHandler(handlers.get(1), 37, 38, 48)
                && sameHandler(handlers.get(2), 39, 47, 48);
        }
        if (handlers.size() == 2) {
            return sameHandler(handlers.get(0), 0, 40, 50)
                && sameHandler(handlers.get(1), 41, 49, 50);
        }
        return false;
    }

    private static boolean sameHandler(final CodeException handler, final int startPc, final int endPc, final int handlerPc) {
        return handler.startPc() == startPc
            && handler.endPc() == endPc
            && handler.handlerPc() == handlerPc
            && "java/lang/IllegalArgumentException".equals(handler.catchType().orElse(""));
    }

    private static boolean hasExactCatchNullFunctionOrNullApplyExceptionTable(final List<CodeException> handlers) {
        if (handlers.size() != 1) {
            return false;
        }
        final CodeException handler = handlers.getFirst();
        return handler.startPc() == 0
            && handler.endPc() == 7
            && handler.handlerPc() == 8
            && "java/lang/Exception".equals(handler.catchType().orElse(""));
    }

    private static boolean hasExactTemporalOfLoopFallbackExceptionTable(final List<CodeException> handlers) {
        if (handlers.size() != 2) {
            return false;
        }
        final CodeException parseHandler = handlers.get(0);
        final CodeException fallbackHandler = handlers.get(1);
        return parseHandler.startPc() == 24
            && parseHandler.endPc() == 36
            && parseHandler.handlerPc() == 37
            && "java/time/format/DateTimeParseException".equals(parseHandler.catchType().orElse(""))
            && fallbackHandler.startPc() == 39
            && fallbackHandler.endPc() == 53
            && fallbackHandler.handlerPc() == 54
            && "java/lang/Exception".equals(fallbackHandler.catchType().orElse(""));
    }

    private static boolean sameInstruction(final List<Instruction> instructions, final int index, final int offset, final int opcode) {
        if (index < 0 || index >= instructions.size()) {
            return false;
        }
        final Instruction instruction = instructions.get(index);
        return instruction.offset() == offset && instruction.opcode() == opcode;
    }

    private static boolean sameClassInstruction(
        final List<Instruction> instructions,
        final int index,
        final int offset,
        final int opcode,
        final String className
    ) {
        return sameInstruction(instructions, index, offset, opcode)
            && className.equals(instructions.get(index).className().orElse(""));
    }

    private static boolean sameMethodInstruction(
        final List<Instruction> instructions,
        final int index,
        final int offset,
        final int opcode,
        final MethodRef methodRef
    ) {
        return sameInstruction(instructions, index, offset, opcode)
            && methodRef.equals(instructions.get(index).methodRef().orElse(null));
    }

    private static boolean sameNamedMethodInstruction(
        final List<Instruction> instructions,
        final int index,
        final int offset,
        final int opcode,
        final String name,
        final String descriptor
    ) {
        if (!sameInstruction(instructions, index, offset, opcode)) {
            return false;
        }
        final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
        return methodRef.isPresent()
            && name.equals(methodRef.orElseThrow().name())
            && descriptor.equals(methodRef.orElseThrow().descriptor());
    }

    private static boolean sameFieldInstruction(
        final List<Instruction> instructions,
        final int index,
        final int offset,
        final int opcode,
        final String owner,
        final String name,
        final String descriptor
    ) {
        if (!sameInstruction(instructions, index, offset, opcode)) {
            return false;
        }
        final Optional<FieldRef> fieldRef = instructions.get(index).fieldRef();
        if (fieldRef.isEmpty()) {
            return false;
        }
        final FieldRef resolved = fieldRef.orElseThrow();
        return owner.equals(resolved.owner())
            && name.equals(resolved.name())
            && descriptor.equals(resolved.descriptor());
    }

    private static boolean sameTemporalStringBridgeLambdaInstruction(
        final Instruction instruction,
        final String owner,
        final String targetDescriptor
    ) {
        if (instruction.opcode() != 186 || instruction.dynamicRef().isEmpty()) {
            return false;
        }
        final Optional<LambdaMetafactoryCall> lambdaCall = LambdaMetafactoryCall.resolve(instruction.dynamicRef().orElseThrow());
        if (lambdaCall.isEmpty()) {
            return false;
        }
        final LambdaMetafactoryCall resolved = lambdaCall.orElseThrow();
        final MethodRef implementation = resolved.implementation();
        return resolved.isDirectlyLowerable()
            && "java/util/function/Function".equals(resolved.interfaceOwner())
            && "apply".equals(resolved.interfaceMethodName())
            && owner.equals(implementation.owner())
            && implementation.name().startsWith("lambda$")
            && ("(Ljava/time/temporal/TemporalAccessor;)" + targetDescriptor).equals(implementation.descriptor());
    }

    private static boolean isCalendarHelperMethod(final ClassFile classFile, final MethodInfo method, final String descriptor) {
        if (classFile == null || method == null || !method.isStatic()) {
            return false;
        }
        if (!CALENDAR_OF_METHOD_NAME.equals(method.name()) || !descriptor.equals(method.descriptor())) {
            return false;
        }
        return method.code().isPresent() && method.code().orElseThrow().exceptionTable().isEmpty();
    }

    private static boolean isSupportedUnsupportedTemporalInstruction(final String owner, final Instruction instruction) {
        if (instruction.dynamicRef().isPresent()) {
            return false;
        }
        if (instruction.fieldRef().isPresent() && !isUnsupportedTemporalField(instruction.fieldRef().orElseThrow())) {
            return false;
        }
        if (instruction.className().isPresent() && !isUnsupportedTemporalClassLiteral(instruction.className().orElseThrow())) {
            return false;
        }
        if (instruction.methodRef().isPresent() && !isUnsupportedTemporalMethod(owner, instruction.methodRef().orElseThrow())) {
            return false;
        }
        return switch (instruction.opcode()) {
            case 1, 3, 4, 9, 16, 18, 42, 43, 44, 45, 76, 77, 78, 89, 176, 178, 182, 183, 184, 187 -> true;
            default -> false;
        };
    }

    private static boolean isUnsupportedTemporalClassLiteral(final String owner) {
        return isUnsupportedTemporalOwner(owner);
    }

    private static boolean isUnsupportedTemporalField(final FieldRef fieldRef) {
        return isUnsupportedTemporalOwner(fieldRef.owner());
    }

    private static boolean isUnsupportedTemporalMethod(final String owner, final MethodRef methodRef) {
        return isUnsupportedTemporalOwner(methodRef.owner())
            || ("java/lang/Long".equals(methodRef.owner())
            && "valueOf".equals(methodRef.name())
            && "(J)Ljava/lang/Long;".equals(methodRef.descriptor()))
            || (("java/lang/Long".equals(methodRef.owner()) || "java/lang/Number".equals(methodRef.owner()))
            && "longValue".equals(methodRef.name())
            && "()J".equals(methodRef.descriptor()))
            || owner.equals(methodRef.owner());
    }

    private static boolean isUnsupportedTemporalOwner(final String owner) {
        return owner.startsWith("java/time/")
            || owner.startsWith("java/sql/")
            || "java/util/Date".equals(owner)
            || "java/util/Calendar".equals(owner)
            || "java/util/GregorianCalendar".equals(owner);
    }

    private static Optional<String> returnDescriptor(final String descriptor) {
        final int separator = descriptor.indexOf(')');
        if (separator < 0 || separator + 1 >= descriptor.length()) {
            return Optional.empty();
        }
        return Optional.of(descriptor.substring(separator + 1));
    }

    private static Optional<String> objectOwner(final String descriptor) {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
            return Optional.empty();
        }
        return Optional.of(descriptor.substring(1, descriptor.length() - 1));
    }
}
