package javan.compat;

import javan.classfile.BootstrapArgument;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ExactMethodSupportTest {
    @Test
    void catchNullEnumLookupRejectsNonStaticMethod() {
        final MethodInfo method = copyMethod(exactCatchNullEnumLookupMethod(), 0, "enumOf", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Enum;");
        final ClassFile owner = classWithMethods("com/acme/EnumLookupSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullEnumLookupMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullEnumLookupRejectsWrongExceptionTableShape() {
        final MethodInfo base = exactCatchNullEnumLookupMethod();
        final MethodInfo method = new MethodInfo(
            0x0008,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(
                2,
                4,
                new byte[0],
                1,
                List.of(new CodeException(0, 36, 48, Optional.of("java/lang/RuntimeException"))),
                base.code().orElseThrow().instructions()
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/EnumLookupSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullEnumLookupMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullEnumLookupAcceptsJavacTwoHandlerShape() {
        final MethodInfo method = exactCatchNullEnumLookupJavacMethod();
        final ClassFile owner = classWithMethods("com/acme/EnumLookupSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullEnumLookupMethod(owner, method)).isTrue();
    }

    @Test
    void catchNullEnumLookupAcceptsSyntheticThreeHandlerShape() {
        final MethodInfo method = exactCatchNullEnumLookupMethod();
        final ClassFile owner = classWithMethods("com/acme/EnumLookupSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullEnumLookupMethod(owner, method)).isTrue();
    }

    @Test
    void catchNullFunctionApplyRejectsNonInterfaceOwner() {
        final MethodInfo method = exactCatchNullFallibleApplyMethod();
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsNullOwner() {
        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(null, exactCatchNullFallibleApplyMethod())).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsNullMethod() {
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), exactCatchNullFallibleApplyMethod());

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, null)).isFalse();
    }

    @Test
    void catchNullFunctionApplyAcceptsExactInterfaceShape() {
        final MethodInfo method = exactCatchNullFallibleApplyMethod();
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isTrue();
    }

    @Test
    void catchNullFunctionApplyRejectsStaticMethod() {
        final MethodInfo method = copyMethod(exactCatchNullFallibleApplyMethod(), 0x0008, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsWrongMethodName() {
        final MethodInfo method = copyMethod(exactCatchNullFallibleApplyMethod(), 0, "applyOrNull", "(Ljava/lang/Object;)Ljava/lang/Object;");
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsWrongDescriptor() {
        final MethodInfo method = copyMethod(exactCatchNullFallibleApplyMethod(), 0, "apply", "(Ljava/lang/Object;)Ljava/lang/String;");
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsMissingCode() {
        final MethodInfo method = new MethodInfo(0, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", Optional.empty());
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsWrongInstructionCount() {
        final MethodInfo method = new MethodInfo(
            0,
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Optional.of(new CodeAttribute(
                2,
                3,
                new byte[0],
                1,
                List.of(new CodeException(0, 7, 8, Optional.of("java/lang/Exception"))),
                List.of(
                    instruction(0, 42, "aload_0"),
                    instruction(1, 43, "aload_1"),
                    instruction(2, 185, "invokeinterface", new MethodRef("com/acme/FallibleFunction", "applyWithException", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                    instruction(7, 176, "areturn"),
                    instruction(8, 77, "astore_2"),
                    instruction(9, 1, "aconst_null"),
                    instruction(10, 176, "areturn"),
                    instruction(11, 176, "areturn")
                )
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsWrongExceptionHandlerCatchType() {
        final MethodInfo base = exactCatchNullFallibleApplyMethod();
        final MethodInfo method = new MethodInfo(
            0,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(
                2,
                3,
                new byte[0],
                1,
                List.of(new CodeException(0, 7, 8, Optional.of("java/lang/RuntimeException"))),
                base.code().orElseThrow().instructions()
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void catchNullFunctionApplyRejectsWrongInvokeInterfaceTarget() {
        final MethodInfo base = exactCatchNullFallibleApplyMethod();
        final MethodInfo method = new MethodInfo(
            0,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(
                2,
                3,
                new byte[0],
                1,
                List.of(new CodeException(0, 7, 8, Optional.of("java/lang/Exception"))),
                List.of(
                    instruction(0, 42, "aload_0"),
                    instruction(1, 43, "aload_1"),
                    instruction(2, 185, "invokeinterface", new MethodRef("com/acme/FallibleFunction", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                    instruction(7, 176, "areturn"),
                    instruction(8, 77, "astore_2"),
                    instruction(9, 1, "aconst_null"),
                    instruction(10, 176, "areturn")
                )
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/FallibleFunction", "java/lang/Object", 0x0200, List.of(), method);

        assertThat(ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, method)).isFalse();
    }

    @Test
    void temporalStringBridgeRejectsNonEmptyExceptionTable() {
        final MethodInfo base = exactTemporalStringBridgeMethod();
        final MethodInfo method = new MethodInfo(
            0x0008,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(
                2,
                1,
                new byte[0],
                1,
                List.of(new CodeException(0, 8, 8, Optional.of("java/lang/Exception"))),
                base.code().orElseThrow().instructions()
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalStringBridgeMethod(owner, method)).isFalse();
    }

    @Test
    void temporalStringBridgeAcceptsExactShapeAndReportsTargetOwner() {
        final MethodInfo method = exactTemporalStringBridgeMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalStringBridgeMethod(owner, method)).isTrue();
        assertThat(ExactMethodSupport.exactTemporalStringBridgeTargetInternalName(owner, method))
            .contains("java/sql/Timestamp");
    }

    @Test
    void temporalStringBridgeRejectsNonLowerableLambdaBootstrap() {
        final MethodInfo method = exactTemporalStringBridgeMethodWithDynamicRef(new DynamicRef(
            "accept",
            "()Ljava/util/function/Consumer;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                + "Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;)V",
                "invokestatic com/acme/TemporalSupport.lambda$null$133:(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;",
                "(Ljava/time/temporal/TemporalAccessor;)V"
            ),
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)V"),
                BootstrapArgument.methodHandle(
                    6,
                    new MethodRef("com/acme/TemporalSupport", "lambda$null$133", "(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;")
                ),
                BootstrapArgument.methodType("(Ljava/time/temporal/TemporalAccessor;)V")
            )
        ));
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalStringBridgeMethod(owner, method)).isFalse();
    }

    @Test
    void temporalStringBridgeRejectsLowerableLambdaWithWrongImplementationOwner() {
        final MethodInfo method = exactTemporalStringBridgeMethodWithDynamicRef(new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                + "Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                "invokestatic com/acme/OtherSupport.lambda$null$133:(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;",
                "(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;"
            ),
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    6,
                    new MethodRef(
                        "com/acme/OtherSupport",
                        "lambda$null$133",
                        "(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;"
                    )
                ),
                BootstrapArgument.methodType("(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;")
            )
        ));
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalStringBridgeMethod(owner, method)).isFalse();
        assertThat(ExactMethodSupport.exactTemporalStringBridgeTargetInternalName(owner, method)).isEmpty();
    }

    @Test
    void temporalStringBridgeRejectsPrimitiveReturnDescriptor() {
        final MethodInfo method = copyMethod(exactTemporalStringBridgeMethod(), 0x0008, "lambda$static$134", "(Ljava/lang/String;)J");
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalStringBridgeMethod(owner, method)).isFalse();
        assertThat(ExactMethodSupport.exactTemporalStringBridgeTargetInternalName(owner, method)).isEmpty();
    }

    @Test
    void calendarOfDateAcceptsExactShape() {
        final MethodInfo method = exactCalendarOfDateMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCalendarOfDateMethod(owner, method)).isTrue();
    }

    @Test
    void calendarOfLocalTimeAcceptsExactShape() {
        final MethodInfo method = exactCalendarOfLocalTimeMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCalendarOfLocalTimeMethod(owner, method)).isTrue();
    }

    @Test
    void calendarOfEpochMillisAcceptsExactShape() {
        final MethodInfo method = exactCalendarOfEpochMillisMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCalendarOfEpochMillisMethod(owner, method)).isTrue();
    }

    @Test
    void calendarOfEpochMillisRejectsMethodsWithExceptionTable() {
        final MethodInfo base = exactCalendarOfEpochMillisMethod();
        final MethodInfo method = new MethodInfo(
            0x0008,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(
                3,
                3,
                new byte[0],
                1,
                List.of(new CodeException(0, 9, 9, Optional.of("java/lang/Exception"))),
                base.code().orElseThrow().instructions()
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactCalendarOfEpochMillisMethod(owner, method)).isFalse();
    }

    @Test
    void throwableStringOfAcceptsExactShape() {
        final MethodInfo method = exactThrowableStringOfMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactThrowableStringOfMethod(owner, method)).isTrue();
    }

    @Test
    void throwableStringOfRejectsWrongInstructionCount() {
        final MethodInfo base = exactThrowableStringOfMethod();
        final List<Instruction> instructions = base.code().orElseThrow().instructions().subList(0, 37);
        final MethodInfo method = new MethodInfo(
            0x0008,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(3, 3, new byte[0], 0, List.copyOf(instructions)))
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactThrowableStringOfMethod(owner, method)).isFalse();
    }

    @Test
    void unsupportedTemporalConversionLambdaAcceptsExactShape() {
        final MethodInfo method = exactUnsupportedTemporalConversionLambdaMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactUnsupportedTemporalConversionLambdaMethod(owner, method)).isTrue();
    }

    @Test
    void unsupportedTemporalConversionLambdaRejectsDynamicRefInstruction() {
        final MethodInfo method = new MethodInfo(
            0x0008,
            "lambda$static$117",
            "(Ljava/sql/Timestamp;)Ljava/util/Date;",
            Optional.of(new CodeAttribute(
                4,
                1,
                new byte[0],
                0,
                List.of(
                    invokeDynamicInstruction(0, new DynamicRef(
                        "apply",
                        "()Ljava/util/function/Function;",
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                            + "Ljava/lang/invoke/CallSite;",
                        List.of()
                    )),
                    instruction(1, 176, "areturn")
                )
            ))
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactUnsupportedTemporalConversionLambdaMethod(owner, method)).isFalse();
    }

    @Test
    void unsupportedTemporalConversionLambdaRejectsNonLambdaStaticName() {
        final MethodInfo method = copyMethod(
            exactUnsupportedTemporalConversionLambdaMethod(),
            0x0008,
            "helper",
            "(Ljava/sql/Timestamp;)Ljava/util/Date;"
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactUnsupportedTemporalConversionLambdaMethod(owner, method)).isFalse();
    }

    @Test
    void unsupportedTemporalConversionLambdaRejectsNonTemporalReturnOwner() {
        final MethodInfo method = copyMethod(
            exactUnsupportedTemporalConversionLambdaMethod(),
            0x0008,
            "lambda$static$117",
            "(Ljava/sql/Timestamp;)Ljava/lang/String;"
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactUnsupportedTemporalConversionLambdaMethod(owner, method)).isFalse();
    }

    @Test
    void temporalOfLoopFallbackAcceptsExactShape() {
        final MethodInfo method = exactTemporalOfLoopFallbackMethod();
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalOfLoopFallbackMethod(owner, method)).isTrue();
    }

    @Test
    void temporalOfLoopFallbackRejectsWrongFallbackCatchType() {
        final MethodInfo base = exactTemporalOfLoopFallbackMethod();
        final List<CodeException> handlers = List.of(
            new CodeException(24, 36, 37, Optional.of("java/time/format/DateTimeParseException")),
            new CodeException(39, 53, 54, Optional.of("java/lang/RuntimeException"))
        );
        final MethodInfo method = new MethodInfo(
            0x0008,
            base.name(),
            base.descriptor(),
            Optional.of(new CodeAttribute(3, 8, new byte[0], 2, handlers, base.code().orElseThrow().instructions()))
        );
        final ClassFile owner = classWithMethods("com/acme/TemporalSupport", "java/lang/Object", 0, List.of(), method);

        assertThat(ExactMethodSupport.isExactTemporalOfLoopFallbackMethod(owner, method)).isFalse();
    }

    private static ClassFile classWithMethods(
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final MethodInfo... methods
    ) {
        return new ClassFile(
            69,
            name,
            superName,
            accessFlags,
            List.copyOf(interfaces),
            List.of(),
            List.of(methods),
            Path.of(name + ".class"),
            true
        );
    }

    private static MethodInfo exactCatchNullEnumLookupJavacMethod() {
        return new MethodInfo(
            0x0008,
            "enumOf",
            "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Enum;",
            Optional.of(new CodeAttribute(
                2,
                4,
                new byte[0],
                2,
                List.of(
                    new CodeException(0, 40, 50, Optional.of("java/lang/IllegalArgumentException")),
                    new CodeException(41, 49, 50, Optional.of("java/lang/IllegalArgumentException"))
                ),
                List.of(
                    instruction(0, 42, "aload_0"),
                    classInstruction(1, 193, "instanceof", "java/lang/Number"),
                    instruction(4, 153, "ifeq"),
                    instruction(7, 42, "aload_0"),
                    classInstruction(8, 192, "checkcast", "java/lang/Number"),
                    instruction(11, 182, "invokevirtual", new MethodRef("java/lang/Number", "intValue", "()I")),
                    instruction(14, 61, "istore_2"),
                    instruction(15, 43, "aload_1"),
                    instruction(16, 182, "invokevirtual", new MethodRef("java/lang/Class", "getEnumConstants", "()[Ljava/lang/Object;")),
                    classInstruction(19, 192, "checkcast", "[Ljava/lang/Enum;"),
                    instruction(22, 78, "astore_3"),
                    instruction(23, 28, "iload_2"),
                    instruction(24, 155, "iflt"),
                    instruction(27, 28, "iload_2"),
                    instruction(28, 45, "aload_3"),
                    instruction(29, 190, "arraylength"),
                    instruction(30, 162, "if_icmpge"),
                    instruction(33, 45, "aload_3"),
                    instruction(34, 28, "iload_2"),
                    instruction(35, 50, "aaload"),
                    instruction(36, 167, "goto"),
                    instruction(39, 1, "aconst_null"),
                    instruction(40, 176, "areturn"),
                    instruction(41, 43, "aload_1"),
                    instruction(42, 42, "aload_0"),
                    instruction(43, 184, "invokestatic", new MethodRef("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;")),
                    instruction(46, 184, "invokestatic", new MethodRef("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;")),
                    instruction(49, 176, "areturn"),
                    instruction(50, 77, "astore_2"),
                    instruction(51, 1, "aconst_null"),
                    instruction(52, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo copyMethod(
        final MethodInfo method,
        final int accessFlags,
        final String name,
        final String descriptor
    ) {
        return new MethodInfo(accessFlags, name, descriptor, method.code());
    }

    private static MethodInfo exactCatchNullEnumLookupMethod() {
        return new MethodInfo(
            0x0008,
            "enumOf",
            "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Enum;",
            Optional.of(new CodeAttribute(
                2,
                4,
                new byte[0],
                3,
                List.of(
                    new CodeException(0, 36, 48, Optional.of("java/lang/IllegalArgumentException")),
                    new CodeException(37, 38, 48, Optional.of("java/lang/IllegalArgumentException")),
                    new CodeException(39, 47, 48, Optional.of("java/lang/IllegalArgumentException"))
                ),
                List.of(
                    instruction(0, 42, "aload_0"),
                    classInstruction(1, 193, "instanceof", "java/lang/Number"),
                    instruction(4, 153, "ifeq"),
                    instruction(7, 42, "aload_0"),
                    classInstruction(8, 192, "checkcast", "java/lang/Number"),
                    instruction(11, 182, "invokevirtual", new MethodRef("java/lang/Number", "intValue", "()I")),
                    instruction(14, 61, "istore_2"),
                    instruction(15, 43, "aload_1"),
                    instruction(16, 182, "invokevirtual", new MethodRef("java/lang/Class", "getEnumConstants", "()[Ljava/lang/Object;")),
                    classInstruction(19, 192, "checkcast", "[Ljava/lang/Enum;"),
                    instruction(22, 78, "astore_3"),
                    instruction(23, 28, "iload_2"),
                    instruction(24, 155, "iflt"),
                    instruction(27, 28, "iload_2"),
                    instruction(28, 45, "aload_3"),
                    instruction(29, 190, "arraylength"),
                    instruction(30, 162, "if_icmpge"),
                    instruction(33, 45, "aload_3"),
                    instruction(34, 28, "iload_2"),
                    instruction(35, 50, "aaload"),
                    instruction(36, 176, "areturn"),
                    instruction(37, 1, "aconst_null"),
                    instruction(38, 176, "areturn"),
                    instruction(39, 43, "aload_1"),
                    instruction(40, 42, "aload_0"),
                    instruction(41, 184, "invokestatic", new MethodRef("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;")),
                    instruction(44, 184, "invokestatic", new MethodRef("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;")),
                    instruction(47, 176, "areturn"),
                    instruction(48, 77, "astore_2"),
                    instruction(49, 1, "aconst_null"),
                    instruction(50, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactCatchNullFallibleApplyMethod() {
        return new MethodInfo(
            0,
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Optional.of(new CodeAttribute(
                2,
                3,
                new byte[0],
                1,
                List.of(new CodeException(0, 7, 8, Optional.of("java/lang/Exception"))),
                List.of(
                    instruction(0, 42, "aload_0"),
                    instruction(1, 43, "aload_1"),
                    instruction(2, 185, "invokeinterface", new MethodRef("com/acme/FallibleFunction", "applyWithException", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                    instruction(7, 176, "areturn"),
                    instruction(8, 77, "astore_2"),
                    instruction(9, 1, "aconst_null"),
                    instruction(10, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactTemporalOfLoopFallbackMethod() {
        return new MethodInfo(
            0x0008,
            "temporalOf",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/function/Function;)Ljava/lang/Object;",
            Optional.of(new CodeAttribute(
                3,
                8,
                new byte[0],
                2,
                List.of(
                    new CodeException(24, 36, 37, Optional.of("java/time/format/DateTimeParseException")),
                    new CodeException(39, 53, 54, Optional.of("java/lang/Exception"))
                ),
                List.of(
                    fieldInstruction(0, 178, "getstatic", new FieldRef("com/acme/TemporalSupport", "DATE_TIME_FORMATTERS", "[Ljava/time/format/DateTimeFormatter;")),
                    instruction(3, 78, "astore_3"),
                    instruction(4, 45, "aload_3"),
                    instruction(5, 190, "arraylength"),
                    instruction(6, 54, "istore"),
                    instruction(8, 3, "iconst_0"),
                    instruction(9, 54, "istore"),
                    instruction(11, 21, "iload"),
                    instruction(13, 21, "iload"),
                    instruction(15, 162, "if_icmpge"),
                    instruction(18, 45, "aload_3"),
                    instruction(19, 21, "iload"),
                    instruction(21, 50, "aaload"),
                    instruction(22, 58, "astore"),
                    instruction(24, 44, "aload_2"),
                    instruction(25, 25, "aload"),
                    instruction(27, 43, "aload_1"),
                    instruction(28, 182, "invokevirtual", new MethodRef("java/time/format/DateTimeFormatter", "parse", "(Ljava/lang/CharSequence;)Ljava/time/temporal/TemporalAccessor;")),
                    instruction(31, 185, "invokeinterface", new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")),
                    instruction(36, 176, "areturn"),
                    instruction(37, 58, "astore"),
                    instruction(39, 43, "aload_1"),
                    instruction(40, 184, "invokestatic", new MethodRef("java/lang/Long", "parseLong", "(Ljava/lang/String;)J")),
                    instruction(43, 184, "invokestatic", new MethodRef("com/acme/TemporalSupport", "toTimestampMs", "(J)J")),
                    instruction(46, 184, "invokestatic", new MethodRef("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")),
                    instruction(49, 42, "aload_0"),
                    instruction(50, 184, "invokestatic", new MethodRef("com/acme/ValueCoercionSupport", "convertObj", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;")),
                    instruction(53, 176, "areturn"),
                    instruction(54, 58, "astore"),
                    instruction(56, 132, "iinc"),
                    instruction(59, 167, "goto"),
                    instruction(62, 1, "aconst_null"),
                    instruction(63, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactTemporalStringBridgeMethod() {
        return exactTemporalStringBridgeMethodWithDynamicRef(new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                + "Ljava/lang/invoke/CallSite;",
            List.of(
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                "invokestatic com/acme/TemporalSupport.lambda$null$133:(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;",
                "(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;"
            ),
            List.of(
                BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                BootstrapArgument.methodHandle(
                    6,
                    new MethodRef(
                        "com/acme/TemporalSupport",
                        "lambda$null$133",
                        "(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;"
                    )
                ),
                BootstrapArgument.methodType("(Ljava/time/temporal/TemporalAccessor;)Ljava/sql/Timestamp;")
            )
        ));
    }

    private static MethodInfo exactTemporalStringBridgeMethodWithDynamicRef(final DynamicRef dynamicRef) {
        return new MethodInfo(
            0x0008,
            "lambda$static$134",
            "(Ljava/lang/String;)Ljava/sql/Timestamp;",
            Optional.of(new CodeAttribute(
                2,
                1,
                new byte[0],
                0,
                List.of(
                    classInstruction(0, 18, "ldc", "java/sql/Timestamp"),
                    instruction(2, 42, "aload_0"),
                    invokeDynamicInstruction(3, dynamicRef),
                    instruction(8, 184, "invokestatic", new MethodRef(
                        "com/acme/TemporalSupport",
                        "temporalOf",
                        "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/function/Function;)Ljava/lang/Object;"
                    )),
                    classInstruction(11, 192, "checkcast", "java/sql/Timestamp"),
                    instruction(14, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactCalendarOfEpochMillisMethod() {
        return new MethodInfo(
            0x0008,
            "calendarOf",
            "(J)Ljava/util/Calendar;",
            Optional.of(new CodeAttribute(
                3,
                3,
                new byte[0],
                0,
                List.of(
                    instruction(0, 184, "invokestatic", new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;")),
                    instruction(3, 77, "astore_2"),
                    instruction(4, 44, "aload_2"),
                    instruction(5, 30, "lload_0"),
                    instruction(6, 182, "invokevirtual", new MethodRef("java/util/Calendar", "setTimeInMillis", "(J)V")),
                    instruction(9, 44, "aload_2"),
                    instruction(10, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactCalendarOfDateMethod() {
        return new MethodInfo(
            0x0008,
            "calendarOf",
            "(Ljava/util/Date;)Ljava/util/Calendar;",
            Optional.of(new CodeAttribute(
                2,
                2,
                new byte[0],
                0,
                List.of(
                    instruction(0, 184, "invokestatic", new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;")),
                    instruction(3, 76, "astore_1"),
                    instruction(4, 184, "invokestatic", new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;")),
                    instruction(7, 42, "aload_0"),
                    instruction(8, 182, "invokevirtual", new MethodRef("java/util/Calendar", "setTime", "(Ljava/util/Date;)V")),
                    instruction(11, 43, "aload_1"),
                    instruction(12, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactCalendarOfLocalTimeMethod() {
        return new MethodInfo(
            0x0008,
            "calendarOf",
            "(Ljava/time/LocalTime;)Ljava/util/Calendar;",
            Optional.of(new CodeAttribute(
                3,
                2,
                new byte[0],
                0,
                List.of(
                    instruction(0, 184, "invokestatic", new MethodRef("java/util/Calendar", "getInstance", "()Ljava/util/Calendar;")),
                    instruction(3, 76, "astore_1"),
                    instruction(4, 43, "aload_1"),
                    instruction(5, 16, "bipush"),
                    instruction(7, 42, "aload_0"),
                    instruction(8, 182, "invokevirtual", new MethodRef("java/time/LocalTime", "getHour", "()I")),
                    instruction(11, 182, "invokevirtual", new MethodRef("java/util/Calendar", "set", "(II)V")),
                    instruction(14, 43, "aload_1"),
                    instruction(15, 16, "bipush"),
                    instruction(17, 42, "aload_0"),
                    instruction(18, 182, "invokevirtual", new MethodRef("java/time/LocalTime", "getMinute", "()I")),
                    instruction(21, 182, "invokevirtual", new MethodRef("java/util/Calendar", "set", "(II)V")),
                    instruction(24, 43, "aload_1"),
                    instruction(25, 16, "bipush"),
                    instruction(27, 42, "aload_0"),
                    instruction(28, 182, "invokevirtual", new MethodRef("java/time/LocalTime", "getSecond", "()I")),
                    instruction(31, 182, "invokevirtual", new MethodRef("java/util/Calendar", "set", "(II)V")),
                    instruction(34, 43, "aload_1"),
                    instruction(35, 16, "bipush"),
                    instruction(37, 42, "aload_0"),
                    instruction(38, 182, "invokevirtual", new MethodRef("java/time/LocalTime", "getNano", "()I")),
                    instruction(41, 18, "ldc"),
                    instruction(43, 108, "idiv"),
                    instruction(44, 182, "invokevirtual", new MethodRef("java/util/Calendar", "set", "(II)V")),
                    instruction(47, 43, "aload_1"),
                    instruction(48, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactThrowableStringOfMethod() {
        return new MethodInfo(
            0x0008,
            "stringOf",
            "(Ljava/lang/Throwable;)Ljava/lang/String;",
            Optional.of(new CodeAttribute(
                3,
                3,
                new byte[0],
                0,
                List.of(
                    classInstruction(0, 187, "new", "java/lang/StringBuilder"),
                    instruction(3, 89, "dup"),
                    instruction(4, 183, "invokespecial", new MethodRef("java/lang/StringBuilder", "<init>", "()V")),
                    instruction(7, 76, "astore_1"),
                    instruction(8, 43, "aload_1"),
                    instruction(9, 42, "aload_0"),
                    instruction(10, 182, "invokevirtual", new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;")),
                    fieldInstruction(13, 178, "getstatic", new FieldRef("com/acme/TemporalSupport", "LINE_SEPARATOR", "Ljava/lang/String;")),
                    instruction(16, 182, "invokevirtual", new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")),
                    instruction(19, 87, "pop"),
                    instruction(20, 43, "aload_1"),
                    instruction(21, 42, "aload_0"),
                    instruction(22, 3, "iconst_0"),
                    instruction(23, 184, "invokestatic", new MethodRef("com/acme/TemporalSupport", "extractCause", "(Ljava/lang/StringBuilder;Ljava/lang/Throwable;Z)V")),
                    instruction(26, 42, "aload_0"),
                    instruction(27, 182, "invokevirtual", new MethodRef("java/lang/Throwable", "getCause", "()Ljava/lang/Throwable;")),
                    instruction(30, 77, "astore_2"),
                    instruction(31, 44, "aload_2"),
                    instruction(32, 198, "ifnull"),
                    instruction(35, 43, "aload_1"),
                    instruction(36, 18, "ldc"),
                    instruction(38, 182, "invokevirtual", new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")),
                    instruction(41, 44, "aload_2"),
                    instruction(42, 182, "invokevirtual", new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;")),
                    fieldInstruction(45, 178, "getstatic", new FieldRef("com/acme/TemporalSupport", "LINE_SEPARATOR", "Ljava/lang/String;")),
                    instruction(48, 182, "invokevirtual", new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")),
                    instruction(51, 87, "pop"),
                    instruction(52, 43, "aload_1"),
                    instruction(53, 44, "aload_2"),
                    instruction(54, 3, "iconst_0"),
                    instruction(55, 184, "invokestatic", new MethodRef("com/acme/TemporalSupport", "extractCause", "(Ljava/lang/StringBuilder;Ljava/lang/Throwable;Z)V")),
                    instruction(58, 44, "aload_2"),
                    instruction(59, 182, "invokevirtual", new MethodRef("java/lang/Throwable", "getCause", "()Ljava/lang/Throwable;")),
                    instruction(62, 77, "astore_2"),
                    instruction(63, 167, "goto"),
                    instruction(66, 43, "aload_1"),
                    instruction(67, 182, "invokevirtual", new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")),
                    instruction(70, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo exactUnsupportedTemporalConversionLambdaMethod() {
        return new MethodInfo(
            0x0008,
            "lambda$static$117",
            "(Ljava/sql/Timestamp;)Ljava/util/Date;",
            Optional.of(new CodeAttribute(
                4,
                1,
                new byte[0],
                0,
                List.of(
                    classInstruction(0, 187, "new", "java/util/Date"),
                    instruction(3, 89, "dup"),
                    instruction(4, 42, "aload_0"),
                    instruction(5, 182, "invokevirtual", new MethodRef("java/sql/Timestamp", "getTime", "()J")),
                    instruction(8, 183, "invokespecial", new MethodRef("java/util/Date", "<init>", "(J)V")),
                    instruction(11, 176, "areturn")
                )
            ))
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic, final MethodRef methodRef) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.of(methodRef),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction classInstruction(final int offset, final int opcode, final String mnemonic, final String className) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.of(className),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction fieldInstruction(final int offset, final int opcode, final String mnemonic, final FieldRef fieldRef) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.of(fieldRef),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction invokeDynamicInstruction(final int offset, final DynamicRef dynamicRef) {
        return new Instruction(
            offset,
            186,
            "invokedynamic",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(dynamicRef)
        );
    }
}
