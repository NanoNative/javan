package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.FieldInfo;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.LineNumberEntry;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.ir.IrClass;
import javan.ir.IrDispatch;
import javan.ir.IrExpression;
import javan.ir.IrField;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrProgram;
import javan.ir.IrType;
import javan.verify.DiagnosticException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BytecodeToIRTest {
    @TempDir
    private Path tempDir;

    @Test
    void lowersClassMetadataFieldsStaticFieldsEnumConstantsAndSortedClasses() {
        final MethodInfo main = method(0x0008, "main", "()V", 0, 0, plain(0, 177, "return"));
        final ClassFile zeta = classFile(
            "com/acme/Zeta",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0, "name", "Ljava/lang/String;")),
            List.of()
        );
        final ClassFile model = classFile(
            "com/acme/Model",
            "java/lang/Object",
            0,
            List.of(),
            List.of(
                new FieldInfo(0, "count", "I"),
                new FieldInfo(0, "total", "J"),
                new FieldInfo(0, "label", "Ljava/lang/String;"),
                new FieldInfo(0, "ignoredVoid", "V"),
                new FieldInfo(0x0008, "ratio", "F"),
                new FieldInfo(0x0008, "exact", "D"),
                new FieldInfo(0x4008, "READY", "Lcom/acme/Model;")
            ),
            List.of()
        );

        final IrProgram program = lowerProgram(main, zeta, model);

        assertThat(program.classes()).containsExactly(
            new IrClass("com/acme/Main", "javan_class_com_acme_Main", List.of(), List.of(), List.of()),
            new IrClass(
                "com/acme/Model",
                "javan_class_com_acme_Model",
                List.of(
                    new IrField(IrType.INT, "count", "field_count"),
                    new IrField(IrType.LONG, "total", "field_total"),
                    new IrField(IrType.OBJECT, "label", "field_label")
                ),
                List.of(
                    new IrField(IrType.FLOAT, "ratio", "field_ratio"),
                    new IrField(IrType.DOUBLE, "exact", "field_exact"),
                    new IrField(IrType.OBJECT, "READY", "field_READY")
                ),
                List.of("READY")
            ),
            new IrClass(
                "com/acme/Zeta",
                "javan_class_com_acme_Zeta",
                List.of(new IrField(IrType.OBJECT, "name", "field_name")),
                List.of(),
                List.of()
            )
        );
    }

    @Test
    void lowersUncaughtThrowToSourceMappedPanic() {
        final MethodInfo main = new MethodInfo(
            0x0008,
            "main",
            "()V",
            Optional.of(new CodeAttribute(
                1,
                0,
                new byte[0],
                0,
                List.of(),
                List.of(new LineNumberEntry(0, 41), new LineNumberEntry(1, 42)),
                List.of(plain(0, 1, "aconst_null"), plain(1, 191, "athrow"))
            ))
        );

        final IrFunction function = lower(main);

        assertThat(function.instructions()).singleElement().satisfies(instruction -> {
            assertThat(instruction.op()).isEqualTo(IrInstruction.Op.PANIC);
            assertThat(instruction.sourceLocation()).isPresent();
            assertThat(instruction.sourceLocation().orElseThrow().className()).isEqualTo("com/acme/Main");
            assertThat(instruction.sourceLocation().orElseThrow().methodName()).isEqualTo("main");
            assertThat(instruction.sourceLocation().orElseThrow().descriptor()).isEqualTo("()V");
            assertThat(instruction.sourceLocation().orElseThrow().bytecodeOffset()).isEqualTo(1);
            assertThat(instruction.sourceLocation().orElseThrow().lineNumber()).contains(42);
            assertThat(instruction.sourceLocation().orElseThrow().sourceFile()).contains("Main.java");
        });
    }

    @Test
    void lowersProtectedPlatformThrowableToCatchHandlerJump() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            3,
            0,
            List.of(new CodeException(0, 5, 5, Optional.of("java/lang/NullPointerException"))),
            classInstruction(0, 187, "new", "java/lang/NullPointerException"),
            plain(1, 89, "dup"),
            stringConstant(2, "boom"),
            invokeSpecial(3, new MethodRef("java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V")),
            plain(4, 191, "athrow"),
            invokeVirtual(5, new MethodRef("java/lang/NullPointerException", "getMessage", "()Ljava/lang/String;")),
            plain(6, 176, "areturn")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.jump("label_5"),
            IrInstruction.label("label_5"),
            IrInstruction.returnObject(IrExpression.stringLiteral("boom"))
        );
    }

    @Test
    void lowersProtectedPlatformThrowableToCompilerShapedCatchReload() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            3,
            1,
            List.of(new CodeException(0, 5, 5, Optional.of("java/lang/NullPointerException"))),
            classInstruction(0, 187, "new", "java/lang/NullPointerException"),
            plain(1, 89, "dup"),
            stringConstant(2, "boom"),
            invokeSpecial(3, new MethodRef("java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V")),
            plain(4, 191, "athrow"),
            plain(5, 75, "astore_0"),
            plain(6, 42, "aload_0"),
            invokeVirtual(7, new MethodRef("java/lang/NullPointerException", "getMessage", "()Ljava/lang/String;")),
            plain(8, 176, "areturn")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.jump("label_5"),
            IrInstruction.label("label_5"),
            IrInstruction.assignObject("local0", IrExpression.stringLiteral("boom")),
            IrInstruction.returnObject(IrExpression.objectLocal("local0"))
        );
    }

    @Test
    void stackValueExpressionMapsPrintStreamSentinelToSystemOutCall() {
        assertThat(BytecodeToIR.stackValueExpression(BytecodeToIR.StackValue.printStream()))
            .isEqualTo(IrExpression.objectCall("javan_system_out", List.of()));
    }

    @Test
    void stackValueExpressionMapsErrorPrintStreamSentinelToSystemErrCall() {
        assertThat(BytecodeToIR.stackValueExpression(BytecodeToIR.StackValue.errorPrintStream()))
            .isEqualTo(IrExpression.objectCall("javan_system_err", List.of()));
    }

    @Test
    void rejectsPlatformThrowableGetMessageWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/NullPointerException;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/NullPointerException", "getMessage", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/NullPointerException.getMessage()I");
            });
    }

    @Test
    void rejectsProtectedUnknownThrowableTypeForCatchHandler() {
        assertThatThrownBy(() -> lowerMain(methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            List.of(new CodeException(0, 2, 2, Optional.of("java/lang/Throwable"))),
            plain(0, 1, "aconst_null"),
            plain(1, 191, "athrow"),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN014");
                assertThat(exception.diagnostic().subject()).isEqualTo("athrow");
            });
    }

    @Test
    void panicsWhenProtectedThrowHasNoMatchingCatch() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            3,
            0,
            List.of(new CodeException(0, 5, 5, Optional.of("java/lang/IllegalArgumentException"))),
            classInstruction(0, 187, "new", "java/lang/NullPointerException"),
            plain(1, 89, "dup"),
            stringConstant(2, "boom"),
            invokeSpecial(3, new MethodRef("java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V")),
            plain(4, 191, "athrow"),
            invokeVirtual(5, new MethodRef("java/lang/IllegalArgumentException", "getMessage", "()Ljava/lang/String;")),
            plain(6, 176, "areturn")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions().getFirst()).satisfies(instruction -> {
            assertThat(instruction.op()).isEqualTo(IrInstruction.Op.PANIC);
            assertThat(instruction.expression()).contains(IrExpression.stringLiteral("boom"));
            assertThat(instruction.sourceLocation()).isPresent();
            assertThat(instruction.sourceLocation().orElseThrow().bytecodeOffset()).isEqualTo(4);
        });
        assertThat(function.instructions().stream().anyMatch(instruction -> instruction.op() == IrInstruction.Op.JUMP)).isFalse();
    }

    @Test
    void rejectsMutuallyExclusiveThrowsToSamePendingHandlerOffset() {
        assertThatThrownBy(() -> lowerMain(methodWithHandlers(
            0x0008,
            "main",
            "(I)V",
            2,
            2,
            List.of(new CodeException(0, 12, 12, Optional.of("java/lang/NullPointerException"))),
            plain(0, 26, "iload_0"),
            plainOperands(1, 153, "ifeq", 0, 7),
            classInstruction(4, 187, "new", "java/lang/NullPointerException"),
            plain(5, 89, "dup"),
            invokeSpecial(6, new MethodRef("java/lang/NullPointerException", "<init>", "()V")),
            plain(7, 191, "athrow"),
            classInstruction(8, 187, "new", "java/lang/NullPointerException"),
            plain(9, 89, "dup"),
            invokeSpecial(10, new MethodRef("java/lang/NullPointerException", "<init>", "()V")),
            plain(11, 191, "athrow"),
            plain(12, 76, "astore_1"),
            plain(13, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN014");
                assertThat(exception.diagnostic().subject()).isEqualTo("athrow");
            });
    }

    @Test
    void jumpsWhenProtectedThrowHasCatchAllFinallyRethrowHandler() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            3,
            1,
            List.of(new CodeException(0, 5, 5, Optional.empty())),
            classInstruction(0, 187, "new", "java/lang/NullPointerException"),
            plain(1, 89, "dup"),
            stringConstant(2, "boom"),
            invokeSpecial(3, new MethodRef("java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V")),
            plain(4, 191, "athrow"),
            plain(5, 75, "astore_0"),
            plain(6, 42, "aload_0"),
            plain(7, 191, "athrow")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions()).hasSize(4);
        assertThat(function.instructions().subList(0, 3)).containsExactly(
            IrInstruction.jump("label_5"),
            IrInstruction.label("label_5"),
            IrInstruction.assignObject("local0", IrExpression.stringLiteral("boom"))
        );
        assertThat(function.instructions().get(3).op()).isEqualTo(IrInstruction.Op.PANIC);
        assertThat(function.instructions().get(3).expression()).contains(IrExpression.objectLocal("local0"));
    }

    @Test
    void exceptionHandlerReturnsEmptyOutsideProtectedRange() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()V",
            1,
            0,
            List.of(new CodeException(0, 1, 2, Optional.of("java/lang/NullPointerException"))),
            plain(0, 1, "aconst_null"),
            plain(1, 177, "return"),
            plain(2, 177, "return")
        );

        assertThat(BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)),
            main,
            plain(2, 177, "return"),
            BytecodeToIR.StackValue.platformThrowable("java/lang/NullPointerException", IrExpression.stringLiteral("boom")),
            2
        )).isEmpty();
    }

    @Test
    void exceptionHandlerRejectsProtectedThrowWithoutKnownThrowableType() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()V",
            1,
            0,
            List.of(new CodeException(0, 1, 1, Optional.of("java/lang/NullPointerException"))),
            plain(0, 1, "aconst_null"),
            plain(1, 177, "return")
        );

        assertThatThrownBy(() -> BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)),
            main,
            plain(0, 191, "athrow"),
            BytecodeToIR.StackValue.objectExpression(IrExpression.objectNull()),
            0
        )).isInstanceOfSatisfying(DiagnosticException.class, exception ->
            assertThat(exception.diagnostic().code()).isEqualTo("JAVAN014")
        );
    }

    @Test
    void exceptionHandlerSkipsUnsupportedCatchAllFinallyShape() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()V",
            1,
            1,
            List.of(new CodeException(0, 1, 1, Optional.empty())),
            plain(0, 1, "aconst_null"),
            plain(1, 75, "astore_0"),
            plain(2, 177, "return")
        );

        assertThat(BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)),
            main,
            plain(0, 191, "athrow"),
            BytecodeToIR.StackValue.platformThrowable("java/lang/NullPointerException", IrExpression.stringLiteral("boom")),
            0
        )).isEmpty();
    }

    @Test
    void exceptionHandlerPrefersTypedCatchAfterUnsupportedCatchAllFinallyShape() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()V",
            1,
            1,
            List.of(
                new CodeException(0, 1, 1, Optional.empty()),
                new CodeException(0, 1, 3, Optional.of("java/lang/NullPointerException"))
            ),
            plain(0, 1, "aconst_null"),
            plain(1, 75, "astore_0"),
            plain(2, 177, "return"),
            plain(3, 177, "return")
        );

        assertThat(BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)),
            main,
            plain(0, 191, "athrow"),
            BytecodeToIR.StackValue.platformThrowable("java/lang/NullPointerException", IrExpression.stringLiteral("boom")),
            0
        )).contains(3);
    }

    @Test
    void exceptionHandlerReturnsEmptyWhenTypedCatchIsNotAssignable() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()V",
            1,
            0,
            List.of(new CodeException(0, 1, 2, Optional.of("java/lang/IllegalArgumentException"))),
            plain(0, 1, "aconst_null"),
            plain(1, 177, "return"),
            plain(2, 177, "return")
        );

        assertThat(BytecodeToIRControlFlowSupport.exceptionHandler(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)),
            main,
            plain(0, 191, "athrow"),
            BytecodeToIR.StackValue.platformThrowable("java/lang/NullPointerException", IrExpression.stringLiteral("boom")),
            0
        )).isEmpty();
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsTypedCatch() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 1, Optional.of("java/lang/RuntimeException"))),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 177, "return"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsReloadFromDifferentLocal() {
        final CodeAttribute code = new CodeAttribute(
            1,
            2,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 1, Optional.empty())),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 75, "astore_0"), plain(2, 43, "aload_1"), plain(3, 191, "athrow"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void supportedFinallyRethrowHandlerIgnoresReloadBeforeHandlerOffset() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(2, 3, 3, Optional.empty())),
            List.of(),
            List.of(plain(0, 42, "aload_0"), plain(1, 191, "athrow"), plain(2, 177, "return"), plain(3, 75, "astore_0"), plain(4, 177, "return"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsMissingHandlerInstruction() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 9, Optional.empty())),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 177, "return"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsNonAstoreHandlerEntry() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 1, Optional.empty())),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 3, "iconst_0"), plain(2, 191, "athrow"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsHandlerWithoutRethrowLoadPair() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 1, Optional.empty())),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 75, "astore_0"), plain(2, 177, "return"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void preservesThrowableTypeAcrossFinallyRethrowIntoOuterTypedCatch() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            3,
            2,
            List.of(
                new CodeException(0, 5, 5, Optional.empty()),
                new CodeException(0, 8, 8, Optional.of("java/lang/NullPointerException"))
            ),
            classInstruction(0, 187, "new", "java/lang/NullPointerException"),
            plain(1, 89, "dup"),
            stringConstant(2, "boom"),
            invokeSpecial(3, new MethodRef("java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V")),
            plain(4, 191, "athrow"),
            plain(5, 75, "astore_0"),
            plain(6, 42, "aload_0"),
            plain(7, 191, "athrow"),
            plain(8, 76, "astore_1"),
            plain(9, 43, "aload_1"),
            invokeVirtual(10, new MethodRef("java/lang/NullPointerException", "getMessage", "()Ljava/lang/String;")),
            plain(11, 176, "areturn")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.jump("label_5"),
            IrInstruction.label("label_5"),
            IrInstruction.assignObject("local0", IrExpression.stringLiteral("boom")),
            IrInstruction.jump("label_8"),
            IrInstruction.label("label_8"),
            IrInstruction.assignObject("local1_object_1", IrExpression.objectLocal("local0")),
            IrInstruction.returnObject(IrExpression.objectLocal("local1_object_1"))
        );
    }

    @Test
    void localObjectValueRestoresPlatformThrowableFromLocalMetadata() {
        final Map<Integer, IrExpression> locals = new HashMap<>();
        locals.put(0, IrExpression.objectLocal("local0"));
        final Map<Integer, BytecodeToIR.StackKind> objectLocalKinds = new HashMap<>();
        objectLocalKinds.put(0, BytecodeToIR.StackKind.OBJECT);
        final Map<Integer, String> objectLocalThrowableTypes = new HashMap<>();
        objectLocalThrowableTypes.put(0, "java/lang/NullPointerException");

        final BytecodeToIR.StackValue value = BytecodeToIR.localObjectValue(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of()),
            method(0x0008, "main", "()V", 1, 1, plain(0, 177, "return")),
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            0
        );

        assertThat(value.throwableType()).contains("java/lang/NullPointerException");
        assertThat(value.expression()).contains(IrExpression.objectLocal("local0"));
    }

    @Test
    void storeObjectClearsThrowableTypeWhenPlainObjectOverwritesThrowableLocal() {
        final Map<Integer, IrExpression> locals = new HashMap<>();
        final Map<Integer, BytecodeToIR.StackKind> objectLocalKinds = new HashMap<>();
        final Map<Integer, String> objectLocalThrowableTypes = new HashMap<>();
        objectLocalThrowableTypes.put(0, "java/lang/NullPointerException");
        final Map<Integer, IrLocal> localDeclarations = new LinkedHashMap<>();
        final List<IrInstruction> instructions = new ArrayList<>();
        final List<BytecodeToIR.StackValue> stack = new ArrayList<>();
        stack.add(BytecodeToIR.StackValue.objectExpression(IrExpression.objectNull()));

        BytecodeToIR.storeObject(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of()),
            method(0x0008, "main", "()V", 1, 1, plain(0, 177, "return")),
            plain(0, 75, "astore_0"),
            instructions,
            stack,
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            localDeclarations,
            0
        );

        assertThat(objectLocalThrowableTypes).containsEntry(0, null);
        assertThat(BytecodeToIR.localObjectValue(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of()),
            method(0x0008, "main", "()V", 1, 1, plain(0, 177, "return")),
            locals,
            objectLocalKinds,
            objectLocalThrowableTypes,
            0
        ).throwableType()).isEmpty();
    }

    @Test
    void storeObjectIgnoresEmptySyntheticSwitchMapHandlerStore() {
        final MethodInfo clinit = method(0x0008, "<clinit>", "()V", 1, 0, plain(0, 177, "return"));
        final ClassFile classFile = classFile(
            "com/acme/Main$1",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Mode", "[I")),
            List.of(clinit)
        );
        final List<IrInstruction> instructions = new ArrayList<>();

        BytecodeToIR.storeObject(
            classFile,
            clinit,
            plain(0, 75, "astore_0"),
            instructions,
            new ArrayList<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new LinkedHashMap<>(),
            0
        );

        assertThat(instructions).isEmpty();
    }

    @Test
    void storeObjectRejectsEmptyNonSyntheticStore() {
        assertThatThrownBy(() -> BytecodeToIR.storeObject(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of()),
            method(0x0008, "main", "()V", 1, 0, plain(0, 177, "return")),
            plain(0, 75, "astore_0"),
            new ArrayList<>(),
            new ArrayList<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new LinkedHashMap<>(),
            0
        )).isInstanceOfSatisfying(DiagnosticException.class, exception ->
            assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049")
        );
    }

    @Test
    void storeObjectRejectsEmptySyntheticNonHandlerInstruction() {
        final MethodInfo clinit = method(0x0008, "<clinit>", "()V", 1, 0, plain(0, 177, "return"));
        final ClassFile classFile = classFile(
            "com/acme/Main$1",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Mode", "[I")),
            List.of(clinit)
        );

        assertThatThrownBy(() -> BytecodeToIR.storeObject(
            classFile,
            clinit,
            plain(0, 3, "iconst_0"),
            new ArrayList<>(),
            new ArrayList<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new LinkedHashMap<>(),
            0
        )).isInstanceOfSatisfying(DiagnosticException.class, exception ->
            assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049")
        );
    }

    @Test
    void popThrowableRejectsPrimitiveStackValue() {
        final List<BytecodeToIR.StackValue> stack = new ArrayList<>();
        stack.add(BytecodeToIR.StackValue.intExpression(IrExpression.intLiteral(1)));

        assertThatThrownBy(() -> BytecodeToIRControlFlowSupport.popThrowable(
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of()),
            method(0x0008, "main", "()V", 1, 0, plain(0, 172, "ireturn")),
            plain(0, 191, "athrow"),
            stack
        )).isInstanceOfSatisfying(DiagnosticException.class, exception ->
            assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040")
        );
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsWideAstoreWithoutOperands() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 1, Optional.empty())),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 58, "astore"), plain(2, 191, "athrow"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void supportedFinallyRethrowHandlerRejectsWideAloadWithoutOperands() {
        final CodeAttribute code = new CodeAttribute(
            1,
            1,
            new byte[0],
            0,
            List.of(new CodeException(0, 1, 1, Optional.empty())),
            List.of(),
            List.of(plain(0, 177, "return"), plain(1, 75, "astore_0"), plain(2, 25, "aload"), plain(3, 191, "athrow"))
        );

        assertThat(BytecodeToIRControlFlowSupport.supportedFinallyRethrowHandler(
            code,
            code.exceptionTable().getFirst()
        )).isFalse();
    }

    @Test
    void attachesSourceLocationToLineNumberedGeneratedStatement() {
        final MethodInfo main = new MethodInfo(
            0x0008,
            "main",
            "()I",
            Optional.of(new CodeAttribute(
                1,
                0,
                new byte[0],
                0,
                List.of(),
                List.of(new LineNumberEntry(0, 7), new LineNumberEntry(1, 8)),
                List.of(plain(0, 4, "iconst_1"), plain(1, 172, "ireturn"))
            ))
        );

        final IrFunction function = lower(main);

        assertThat(function.instructions()).singleElement().satisfies(instruction -> {
            assertThat(instruction.op()).isEqualTo(IrInstruction.Op.RETURN_INT);
            assertThat(instruction.sourceLocation()).isPresent();
            assertThat(instruction.sourceLocation().orElseThrow().lineNumber()).contains(8);
            assertThat(instruction.sourceLocation().orElseThrow().bytecodeOffset()).isEqualTo(1);
        });
    }

    @Test
    void attachesSourceLineTextWhenSourceIndexProvidesIt() throws Exception {
        final Path sourceRoot = tempDir.resolve("src/main/java");
        final Path source = sourceRoot.resolve("com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            public final class Main {
                public static int main() {
                    return 1;
                }
            }
            """);
        final MethodInfo main = new MethodInfo(
            0x0008,
            "main",
            "()I",
            Optional.of(new CodeAttribute(
                1,
                0,
                new byte[0],
                0,
                List.of(),
                List.of(new LineNumberEntry(0, 5)),
                List.of(plain(0, 4, "iconst_1"), plain(1, 172, "ireturn"))
            ))
        );
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)));
        final EntryPoint entryPoint = new EntryPoint("com/acme/Main", "main", "()I");

        final IrProgram program = new BytecodeToIR().lower(
            classes,
            new CallGraph(entryPoint, List.of(entryPoint), List.of()),
            SourceLineIndex.from(List.of(sourceRoot))
        );

        assertThat(program.functions().getFirst().instructions()).singleElement().satisfies(instruction ->
            assertThat(instruction.sourceLocation().orElseThrow().sourceLine()).contains("        return 1;")
        );
    }

    @Test
    void lowersStaticIntFieldWriteAndRead() {
        final ClassFile state = classFile(
            "com/acme/State",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x0008, "count", "I")),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            plain(0, 8, "iconst_5"),
            fieldInstruction(1, 179, "putstatic", new FieldRef("com/acme/State", "count", "I")),
            fieldInstruction(2, 178, "getstatic", new FieldRef("com/acme/State", "count", "I")),
            plain(3, 172, "ireturn")
        ), state);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignStaticFieldInt("com/acme/State", "count", IrExpression.intLiteral(5)),
            IrInstruction.returnInt(IrExpression.intStaticField("com/acme/State", "count"))
        );
    }

    @Test
    void lowersTypedStaticFieldReadsToTypedIrExpressions() {
        final ClassFile state = classFile(
            "com/acme/State",
            "java/lang/Object",
            0,
            List.of(),
            List.of(
                new FieldInfo(0x0008, "count", "I"),
                new FieldInfo(0x0008, "total", "J"),
                new FieldInfo(0x0008, "ratio", "F"),
                new FieldInfo(0x0008, "exact", "D"),
                new FieldInfo(0x0008, "text", "Ljava/lang/String;")
            ),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            2,
            6,
            fieldInstruction(0, 178, "getstatic", new FieldRef("com/acme/State", "count", "I")),
            plain(1, 59, "istore_0"),
            fieldInstruction(2, 178, "getstatic", new FieldRef("com/acme/State", "total", "J")),
            plain(3, 64, "lstore_1"),
            fieldInstruction(4, 178, "getstatic", new FieldRef("com/acme/State", "ratio", "F")),
            plain(5, 70, "fstore_3"),
            fieldInstruction(6, 178, "getstatic", new FieldRef("com/acme/State", "exact", "D")),
            plainOperands(7, 57, "dstore", 4),
            fieldInstruction(8, 178, "getstatic", new FieldRef("com/acme/State", "text", "Ljava/lang/String;")),
            plain(9, 176, "areturn")
        ), state);

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.INT, "local0"),
            new IrLocal(IrType.LONG, "local1_long_1"),
            new IrLocal(IrType.FLOAT, "local3_float_2"),
            new IrLocal(IrType.DOUBLE, "local4_double_3")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("local0", IrExpression.intStaticField("com/acme/State", "count")),
            IrInstruction.assignLong("local1_long_1", IrExpression.longStaticField("com/acme/State", "total")),
            IrInstruction.assignFloat("local3_float_2", IrExpression.floatStaticField("com/acme/State", "ratio")),
            IrInstruction.assignDouble("local4_double_3", IrExpression.doubleStaticField("com/acme/State", "exact")),
            IrInstruction.returnObject(IrExpression.objectStaticField("com/acme/State", "text"))
        );
    }

    @Test
    void lowersInstanceObjectFieldWriteAndRead() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;Ljava/lang/String;)Ljava/lang/String;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            fieldInstruction(2, 181, "putfield", new FieldRef("com/acme/Box", "text", "Ljava/lang/String;")),
            plain(3, 42, "aload_0"),
            fieldInstruction(4, 180, "getfield", new FieldRef("com/acme/Box", "text", "Ljava/lang/String;")),
            plain(5, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignFieldObject(
                "com/acme/Box",
                "text",
                IrExpression.objectLocal("arg0"),
                IrExpression.objectLocal("arg1")
            ),
            IrInstruction.returnObject(IrExpression.objectField("com/acme/Box", "text", IrExpression.objectLocal("arg0")))
        );
    }

    @Test
    void lowersInstanceLongFieldWriteAndRead() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;J)J",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 31, "lload_1"),
            fieldInstruction(2, 181, "putfield", new FieldRef("com/acme/Box", "total", "J")),
            plain(3, 42, "aload_0"),
            fieldInstruction(4, 180, "getfield", new FieldRef("com/acme/Box", "total", "J")),
            plain(5, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignFieldLong(
                "com/acme/Box",
                "total",
                IrExpression.objectLocal("arg0"),
                IrExpression.longLocal("arg1")
            ),
            IrInstruction.returnLong(IrExpression.longField("com/acme/Box", "total", IrExpression.objectLocal("arg0")))
        );
    }

    @Test
    void lowersInstanceBooleanFieldWriteAndReadAsIntField() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;Z)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            fieldInstruction(2, 181, "putfield", new FieldRef("com/acme/Box", "flag", "Z")),
            plain(3, 42, "aload_0"),
            fieldInstruction(4, 180, "getfield", new FieldRef("com/acme/Box", "flag", "Z")),
            plain(5, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignFieldInt(
                "com/acme/Box",
                "flag",
                IrExpression.objectLocal("arg0"),
                IrExpression.intLocal("arg1")
            ),
            IrInstruction.returnInt(IrExpression.intField("com/acme/Box", "flag", IrExpression.objectLocal("arg0")))
        );
    }

    @Test
    void lowersInstanceFloatFieldWriteAndRead() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;F)F",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 35, "fload_1"),
            fieldInstruction(2, 181, "putfield", new FieldRef("com/acme/Box", "ratio", "F")),
            plain(3, 42, "aload_0"),
            fieldInstruction(4, 180, "getfield", new FieldRef("com/acme/Box", "ratio", "F")),
            plain(5, 174, "freturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignFieldFloat(
                "com/acme/Box",
                "ratio",
                IrExpression.objectLocal("arg0"),
                IrExpression.floatLocal("arg1")
            ),
            IrInstruction.returnFloat(IrExpression.floatField("com/acme/Box", "ratio", IrExpression.objectLocal("arg0")))
        );
    }

    @Test
    void lowersInstanceDoubleFieldWriteAndRead() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;D)D",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 39, "dload_1"),
            fieldInstruction(2, 181, "putfield", new FieldRef("com/acme/Box", "exact", "D")),
            plain(3, 42, "aload_0"),
            fieldInstruction(4, 180, "getfield", new FieldRef("com/acme/Box", "exact", "D")),
            plain(5, 175, "dreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignFieldDouble(
                "com/acme/Box",
                "exact",
                IrExpression.objectLocal("arg0"),
                IrExpression.doubleLocal("arg1")
            ),
            IrInstruction.returnDouble(IrExpression.doubleField("com/acme/Box", "exact", IrExpression.objectLocal("arg0")))
        );
    }

    @Test
    void lowersStaticObjectFieldWriteAndRead() {
        final ClassFile state = classFile(
            "com/acme/State",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x0008, "text", "Ljava/lang/String;")),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            fieldInstruction(1, 179, "putstatic", new FieldRef("com/acme/State", "text", "Ljava/lang/String;")),
            fieldInstruction(2, 178, "getstatic", new FieldRef("com/acme/State", "text", "Ljava/lang/String;")),
            plain(3, 176, "areturn")
        ), state);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignStaticFieldObject("com/acme/State", "text", IrExpression.objectLocal("arg0")),
            IrInstruction.returnObject(IrExpression.objectStaticField("com/acme/State", "text"))
        );
    }

    @Test
    void lowersPrimitiveIntArrayAllocationStoreLoadAndLength() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()I",
            4,
            0,
            plain(0, 6, "iconst_3"),
            plainOperands(1, 188, "newarray", 10),
            plain(2, 89, "dup"),
            plain(3, 3, "iconst_0"),
            plain(4, 8, "iconst_5"),
            plain(5, 79, "iastore"),
            plain(6, 190, "arraylength"),
            plain(7, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.intArrayAllocation(IrExpression.intLiteral(3))),
            IrInstruction.assignArrayInt(IrExpression.objectLocal("object0"), IrExpression.intLiteral(0), IrExpression.intLiteral(5)),
            IrInstruction.returnInt(IrExpression.arrayLength(IrExpression.objectLocal("object0")))
        );
    }

    @Test
    void lowersObjectArrayAllocationStoreAndLoad() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            4,
            0,
            plain(0, 4, "iconst_1"),
            classInstruction(1, 189, "anewarray", "java/lang/String"),
            plain(2, 89, "dup"),
            plain(3, 3, "iconst_0"),
            stringConstant(4, "value"),
            plain(5, 83, "aastore"),
            plain(6, 3, "iconst_0"),
            plain(7, 50, "aaload"),
            plain(8, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectArrayAllocation(IrExpression.intLiteral(1))),
            IrInstruction.assignArrayObject(
                IrExpression.objectLocal("object0"),
                IrExpression.intLiteral(0),
                IrExpression.stringLiteral("value")
            ),
            IrInstruction.returnObject(IrExpression.objectArrayLoad(IrExpression.objectLocal("object0"), IrExpression.intLiteral(0)))
        );
    }

    @Test
    void lowersCheckcastAsNoopPreservingReference() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 192, "checkcast", "java/lang/String"),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectLocal("arg0"))
        );
    }

    @Test
    void ignoresNopInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            plain(0, 0, "nop"),
            plain(1, 4, "iconst_1"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void rejectsUnsupportedOpcodeOutsideNop() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            plain(0, 194, "monitorenter"),
            plain(1, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("monitorenter");
    }

    @Test
    void lowersIincToIntLocalAddAssignment() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            1,
            plain(0, 4, "iconst_1"),
            plain(1, 59, "istore_0"),
            plainOperands(2, 132, "iinc", 0, 2),
            plain(3, 26, "iload_0"),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("local0", IrExpression.intLiteral(1)),
            IrInstruction.assignInt(
                "local0",
                IrExpression.intBinary("+", IrExpression.intLocal("local0"), IrExpression.intLiteral(2))
            ),
            IrInstruction.returnInt(IrExpression.intLocal("local0"))
        );
    }

    @Test
    void lowersIntDivisionToIrExpression() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            2,
            2,
            plain(0, 26, "iload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 108, "idiv"),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intBinary(
                "/",
                IrExpression.intLocal("arg0"),
                IrExpression.intLocal("arg1")
            ))
        );
    }

    @Test
    void lowersIntRemainderToIrExpression() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            2,
            2,
            plain(0, 26, "iload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 112, "irem"),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intBinary(
                "%",
                IrExpression.intLocal("arg0"),
                IrExpression.intLocal("arg1")
            ))
        );
    }

    @Test
    void lowersLongDivisionToIrExpression() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(JJ)J",
            4,
            4,
            plain(0, 30, "lload_0"),
            plainOperands(1, 22, "lload", 2),
            plain(2, 109, "ldiv"),
            plain(3, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longBinary(
                "/",
                IrExpression.longLocal("arg0"),
                IrExpression.longLocal("arg1")
            ))
        );
    }

    @Test
    void lowersLongRemainderToIrExpression() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(JJ)J",
            4,
            4,
            plain(0, 30, "lload_0"),
            plainOperands(1, 22, "lload", 2),
            plain(2, 113, "lrem"),
            plain(3, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longBinary(
                "%",
                IrExpression.longLocal("arg0"),
                IrExpression.longLocal("arg1")
            ))
        );
    }

    @Test
    void lowersFloatDivisionToIrExpression() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(FF)F",
            2,
            2,
            plain(0, 34, "fload_0"),
            plain(1, 35, "fload_1"),
            plain(2, 110, "fdiv"),
            plain(3, 174, "freturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnFloat(IrExpression.floatBinary(
                "/",
                IrExpression.floatLocal("arg0"),
                IrExpression.floatLocal("arg1")
            ))
        );
    }

    @Test
    void lowersDoubleMultiplicationToIrExpression() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(DD)D",
            4,
            4,
            plain(0, 38, "dload_0"),
            plainOperands(1, 24, "dload", 2),
            plain(2, 107, "dmul"),
            plain(3, 175, "dreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnDouble(IrExpression.doubleBinary(
                "*",
                IrExpression.doubleLocal("arg0"),
                IrExpression.doubleLocal("arg1")
            ))
        );
    }

    @Test
    void lowersFloatCompareGreaterNaNToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(FF)I",
            2,
            2,
            plain(0, 34, "fload_0"),
            plain(1, 35, "fload_1"),
            plain(2, 150, "fcmpg"),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_float_compare",
                List.of(IrExpression.floatLocal("arg0"), IrExpression.floatLocal("arg1"), IrExpression.intLiteral(1))
            ))
        );
    }

    @Test
    void lowersDoubleCompareLessNaNToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(DD)I",
            4,
            4,
            plain(0, 38, "dload_0"),
            plainOperands(1, 24, "dload", 2),
            plain(2, 151, "dcmpl"),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_double_compare",
                List.of(IrExpression.doubleLocal("arg0"), IrExpression.doubleLocal("arg1"), IrExpression.intLiteral(-1))
            ))
        );
    }

    @Test
    void rejectsObjectReturnWithoutStackValueWithSourceDiagnostic() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            0,
            0,
            plain(0, 176, "areturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN049]: bytecode stack shape is not supported")
            .hasMessageContaining("An object return did not have a value on the bytecode stack.");
    }

    @Test
    void rejectsIntReturnWithoutStackValueWithSourceDiagnostic() {
        assertMissingPrimitiveReturnValue(172, "ireturn", "()I", "Expected int value on the bytecode stack, but stack was empty.");
    }

    @Test
    void rejectsLongReturnWithoutStackValueWithSourceDiagnostic() {
        assertMissingPrimitiveReturnValue(173, "lreturn", "()J", "Expected long value on the bytecode stack, but stack was empty.");
    }

    @Test
    void rejectsFloatReturnWithoutStackValueWithSourceDiagnostic() {
        assertMissingPrimitiveReturnValue(174, "freturn", "()F", "Expected float value on the bytecode stack, but stack was empty.");
    }

    @Test
    void rejectsDoubleReturnWithoutStackValueWithSourceDiagnostic() {
        assertMissingPrimitiveReturnValue(175, "dreturn", "()D", "Expected double value on the bytecode stack, but stack was empty.");
    }

    @Test
    void rejectsIntReturnWhenStackHasObjectValue() {
        assertWrongReturnValue(stringConstant(0, "x"), 172, "ireturn", "()I", "Expected int value on the bytecode stack, but found object.");
    }

    @Test
    void rejectsLongReturnWhenStackHasIntValue() {
        assertWrongReturnValue(plain(0, 4, "iconst_1"), 173, "lreturn", "()J", "Expected long value on the bytecode stack, but found int.");
    }

    @Test
    void rejectsFloatReturnWhenStackHasIntValue() {
        assertWrongReturnValue(plain(0, 4, "iconst_1"), 174, "freturn", "()F", "Expected float value on the bytecode stack, but found int.");
    }

    @Test
    void rejectsDoubleReturnWhenStackHasIntValue() {
        assertWrongReturnValue(plain(0, 4, "iconst_1"), 175, "dreturn", "()D", "Expected double value on the bytecode stack, but found int.");
    }

    @Test
    void rejectsObjectReturnWhenStackHasIntValue() {
        assertWrongReturnValue(plain(0, 4, "iconst_1"), 176, "areturn", "()Ljava/lang/String;", "Expected object value on the bytecode stack, but found int.");
    }

    @Test
    void rejectsArrayLengthWithoutObjectStackValue() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()I",
            0,
            0,
            plain(0, 190, "arraylength"),
            plain(1, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN049]: bytecode stack shape is not supported")
            .hasMessageContaining("arraylength")
            .hasMessageContaining("An object value was expected on the bytecode stack.");
    }

    @Test
    void rejectsObjectStoreWithoutStackValue() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            0,
            1,
            plain(0, 75, "astore_0"),
            plain(1, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN049]: bytecode stack shape is not supported")
            .hasMessageContaining("astore_0")
            .hasMessageContaining("object store requires a value on the bytecode stack");
    }

    @Test
    void rejectsUnsupportedInstanceFieldReadDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            fieldInstruction(1, 180, "getfield", new FieldRef("com/acme/Box", "nothing", "V")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("getfield com/acme/Box.nothing:V");
    }

    @Test
    void rejectsUnsupportedStaticFieldReadDescriptor() {
        final ClassFile state = classFile(
            "com/acme/State",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x0008, "raw", "V")),
            List.of()
        );

        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            fieldInstruction(0, 178, "getstatic", new FieldRef("com/acme/State", "raw", "V")),
            plain(1, 177, "return")
        ), state))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("getstatic com/acme/State.raw:V");
    }

    @Test
    void rejectsUnsupportedStaticFieldWriteDescriptor() {
        final ClassFile state = classFile(
            "com/acme/State",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x0008, "raw", "V")),
            List.of()
        );

        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            plain(0, 1, "aconst_null"),
            fieldInstruction(1, 179, "putstatic", new FieldRef("com/acme/State", "raw", "V")),
            plain(2, 177, "return")
        ), state))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("putstatic com/acme/State.raw:V");
    }

    @Test
    void rejectsUnsupportedInstanceFieldWriteDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Lcom/acme/Box;Ljava/lang/Object;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            fieldInstruction(2, 181, "putfield", new FieldRef("com/acme/Box", "nothing", "V")),
            plain(3, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("putfield com/acme/Box.nothing:V");
    }

    @Test
    void rejectsUnsupportedCollectionSizeInstanceCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Collection;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/util/Collection", "size", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("invokeinterface java/util/Collection.size()I");
    }

    @Test
    void rejectsUnsupportedStringDefaultConstructor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            2,
            0,
            classInstruction(0, 187, "new", "java/lang/String"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/lang/String", "<init>", "()V")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("invokespecial java/lang/String.<init>()V");
    }

    @Test
    void lowersStringBuilderCapacityConstructorToReserveRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/StringBuilder;",
            3,
            0,
            classInstruction(0, 187, "new", "java/lang/StringBuilder"),
            plain(1, 89, "dup"),
            plain(2, 7, "iconst_4"),
            invokeSpecial(3, new MethodRef("java/lang/StringBuilder", "<init>", "(I)V")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_stringbuilder_new", List.of())
            ),
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_reserve",
                List.of(IrExpression.objectLocal("object0"), IrExpression.intLiteral(4))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderAppendFloatToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;F)Ljava/lang/StringBuilder;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 35, "fload_1"),
            invokeVirtual(2, new MethodRef(
                "java/lang/StringBuilder",
                "append",
                "(F)Ljava/lang/StringBuilder;"
            )),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_append_float",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.floatLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersBooleanArrayCopyOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([ZI)[Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeStatic(2, new MethodRef("java/util/Arrays", "copyOf", "([ZI)[Z")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_arrays_copy_of_boolean",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedMathNegateExact() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/Math", "negateExact", "(I)I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("invokestatic java/lang/Math.negateExact(I)I");
    }

    @Test
    void rejectsUnsupportedSystemIdentityHashCode() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("invokestatic java/lang/System.identityHashCode(Ljava/lang/Object;)I");
    }

    @Test
    void rejectsUnsupportedObjectsEquals() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/util/Objects",
                "equals",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z"
            )),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("invokestatic java/util/Objects.equals(Ljava/lang/Object;Ljava/lang/Object;)Z");
    }

    @Test
    void passesSystemOutAsObjectCallArgument() {
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            invokeStatic(1, new MethodRef("com/acme/Sink", "accept", "(Ljava/lang/Object;)V")),
            plain(2, 177, "return")
        ), sinkClass());

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                symbol("com/acme/Sink", "accept", "(Ljava/lang/Object;)V"),
                List.of(IrExpression.objectCall("javan_system_out", List.of()))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void passesSystemErrAsObjectCallArgument() {
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            invokeStatic(1, new MethodRef("com/acme/Sink", "accept", "(Ljava/lang/Object;)V")),
            plain(2, 177, "return")
        ), sinkClass());

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                symbol("com/acme/Sink", "accept", "(Ljava/lang/Object;)V"),
                List.of(IrExpression.objectCall("javan_system_err", List.of()))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsStaticFieldReadWhenOwnerIsMissing() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("com/acme/Missing", "count", "I")),
            plain(1, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("getstatic com/acme/Missing.count:I");
    }

    @Test
    void rejectsStaticFieldReadWhenFieldIsNotStatic() {
        final ClassFile state = classFile(
            "com/acme/State",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0, "count", "I")),
            List.of()
        );

        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("com/acme/State", "count", "I")),
            plain(1, 172, "ireturn")
        ), state))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("getstatic com/acme/State.count:I");
    }

    @Test
    void rejectsIntCallArgumentWhenStackHasObjectValue() {
        assertWrongCallArgument(
            stringConstant(0, "x"),
            "(I)V",
            "Expected int value on the bytecode stack, but found object."
        );
    }

    @Test
    void rejectsLongCallArgumentWhenStackHasIntValue() {
        assertWrongCallArgument(
            plain(0, 4, "iconst_1"),
            "(J)V",
            "Expected long value on the bytecode stack, but found int."
        );
    }

    @Test
    void rejectsFloatCallArgumentWhenStackHasIntValue() {
        assertWrongCallArgument(
            plain(0, 4, "iconst_1"),
            "(F)V",
            "Expected float value on the bytecode stack, but found int."
        );
    }

    @Test
    void rejectsDoubleCallArgumentWhenStackHasIntValue() {
        assertWrongCallArgument(
            plain(0, 4, "iconst_1"),
            "(D)V",
            "Expected double value on the bytecode stack, but found int."
        );
    }

    @Test
    void rejectsObjectCallArgumentWhenStackHasIntValue() {
        assertWrongCallArgument(
            plain(0, 4, "iconst_1"),
            "(Ljava/lang/Object;)V",
            "Expected object value on the bytecode stack, but found int."
        );
    }

    @Test
    void lowersSystemErrPrintlnObjectToErrorPrintInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            stringConstant(1, "panic"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).contains(
            IrInstruction.printlnErrorObject(IrExpression.stringLiteral("panic")),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintStringToErrorPrintInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            stringConstant(1, "panic"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/String;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printErrorObject(IrExpression.stringLiteral("panic")),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemOutPrintObjectToPrintInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(1, 1, "aconst_null"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printObject(IrExpression.objectNull()),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintObjectToErrorPrintInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 1, "aconst_null"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printErrorObject(IrExpression.objectNull()),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintObjectCallToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;Ljava/lang/Object;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).contains(
            IrInstruction.callStaticVoid(
                "javan_printstream_print_object",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemOutPrintlnWithoutArgumentsToEmptyLineInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            invokeVirtual(1, new MethodRef("java/io/PrintStream", "println", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnObject(IrExpression.stringLiteral("")),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsUnsupportedPrintStreamPrintCharArrayDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(1, 1, "aconst_null"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "([C)V")),
            plain(3, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("invokevirtual java/io/PrintStream.print([C)V");
    }

    @Test
    void rejectsPrintStreamCallWithNonPrintStreamReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            plain(0, 4, "iconst_1"),
            stringConstant(1, "value"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/String;)V")),
            plain(3, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/io/PrintStream.print(Ljava/lang/String;)V");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected PrintStream receiver value on the bytecode stack, but found int.");
            });
    }

    @Test
    void rejectsPrintStreamCallWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            stringConstant(0, "value"),
            invokeVirtual(1, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/String;)V")),
            plain(2, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/io/PrintStream.print(Ljava/lang/String;)V");
                assertThat(exception.diagnostic().reason()).isEqualTo("A PrintStream receiver was expected on the bytecode stack.");
            });
    }

    @Test
    void lowersObjectBackedPrintStreamPrintCallToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;Ljava/lang/String;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/String;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).contains(
            IrInstruction.callStaticVoid(
                "javan_printstream_print_object",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnObjectToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;Ljava/lang/Object;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_object",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnStringToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;Ljava/lang/String;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_object",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemOutPrintStringToPrintInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            stringConstant(1, "value"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "print", "(Ljava/lang/String;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printObject(IrExpression.stringLiteral("value")),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemOutPrintlnStringToPrintlnInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            stringConstant(1, "value"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnObject(IrExpression.stringLiteral("value")),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemOutPrintlnObjectToPrintlnInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            stringConstant(1, "value"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnObject(IrExpression.stringLiteral("value")),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemOutPrintlnNumericAndBooleanOverloads() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            3,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(1, 5, "iconst_2"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(I)V")),
            getStatic(3, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(4, 10, "lconst_1"),
            invokeVirtual(5, new MethodRef("java/io/PrintStream", "println", "(J)V")),
            getStatic(6, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(7, 13, "fconst_2"),
            invokeVirtual(8, new MethodRef("java/io/PrintStream", "println", "(F)V")),
            getStatic(9, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(10, 15, "dconst_1"),
            invokeVirtual(11, new MethodRef("java/io/PrintStream", "println", "(D)V")),
            getStatic(12, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(13, 4, "iconst_1"),
            invokeVirtual(14, new MethodRef("java/io/PrintStream", "println", "(Z)V")),
            plain(15, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnInt(IrExpression.intLiteral(2)),
            IrInstruction.printlnLong(IrExpression.longLiteral(1L)),
            IrInstruction.printlnFloat(IrExpression.floatLiteral(2.0f)),
            IrInstruction.printlnDouble(IrExpression.doubleLiteral(1.0)),
            IrInstruction.printlnBoolean(IrExpression.intLiteral(1)),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintlnIntToErrorIntInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 5, "iconst_2"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(I)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnErrorInt(IrExpression.intLiteral(2)),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintlnLongToErrorLongInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 10, "lconst_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(J)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnErrorLong(IrExpression.longLiteral(1L)),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintlnFloatToErrorFloatInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 12, "fconst_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(F)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnErrorFloat(IrExpression.floatLiteral(1.0f)),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintlnDoubleToErrorDoubleInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 15, "dconst_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(D)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.printlnErrorDouble(IrExpression.doubleLiteral(1.0)),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSystemErrPrintlnBooleanToErrorBooleanInstruction() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/String;)V",
            2,
            1,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 4, "iconst_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Z)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).contains(
            IrInstruction.printlnErrorBoolean(IrExpression.intLiteral(1)),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnIntToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;I)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(I)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_int",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnLongToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;J)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 31, "lload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(J)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_long",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.longLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnFloatToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;F)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 35, "fload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(F)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_float",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.floatLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnDoubleToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;D)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 39, "dload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(D)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_double",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.doubleLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersObjectBackedPrintStreamPrintlnBooleanToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/io/PrintStream;Z)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/io/PrintStream", "println", "(Z)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_printstream_println_bool",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersInstanceOfObjectToNonNullIntrinsic() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "java/lang/Object"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_object_non_null", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersInstanceOfIntegerWrapperToTypeIntrinsic() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "java/lang/Integer"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(-1001))
            ))
        );
    }

    @Test
    void lowersInstanceOfLongWrapperToTypeIntrinsic() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "java/lang/Long"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(-1002))
            ))
        );
    }

    @Test
    void lowersInstanceOfFloatWrapperToTypeIntrinsic() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "java/lang/Float"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(-1003))
            ))
        );
    }

    @Test
    void lowersInstanceOfDoubleWrapperToTypeIntrinsic() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "java/lang/Double"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(-1004))
            ))
        );
    }

    @Test
    void lowersInstanceOfBooleanWrapperToTypeIntrinsic() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "java/lang/Boolean"),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(-1005))
            ))
        );
    }

    @Test
    void lowersInstanceOfDirectInterfaceToAssignableTypeSet() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "com/acme/Marker"),
            plain(2, 172, "ireturn")
        );
        final ClassFile marker = classFile("com/acme/Marker", "java/lang/Object", 0x0200, List.of(), List.of(), List.of());
        final ClassFile worker = classFile("com/acme/Worker", "java/lang/Object", 0, List.of("com/acme/Marker"), List.of(), List.of());

        final IrFunction function = lower(main, marker, worker);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(3))
            ))
        );
    }

    @Test
    void lowersInstanceOfInheritedInterfaceToAssignableTypeSet() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "com/acme/ParentMarker"),
            plain(2, 172, "ireturn")
        );
        final ClassFile parentMarker = classFile("com/acme/ParentMarker", "java/lang/Object", 0x0200, List.of(), List.of(), List.of());
        final ClassFile childMarker = classFile(
            "com/acme/SubMarker",
            "java/lang/Object",
            0x0200,
            List.of("com/acme/ParentMarker"),
            List.of(),
            List.of()
        );
        final ClassFile worker = classFile("com/acme/Worker", "java/lang/Object", 0, List.of("com/acme/SubMarker"), List.of(), List.of());

        final IrFunction function = lower(main, parentMarker, childMarker, worker);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_object_type_in",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(4))
            ))
        );
    }

    @Test
    void lowersInstanceOfKnownCyclicInterfaceWithoutImplementorsToFalseLiteral() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "com/acme/TargetMarker"),
            plain(2, 172, "ireturn")
        );
        final ClassFile targetMarker = classFile("com/acme/TargetMarker", "java/lang/Object", 0x0200, List.of(), List.of(), List.of());
        final ClassFile leftMarker = classFile(
            "com/acme/LeftMarker",
            "java/lang/Object",
            0x0200,
            List.of("com/acme/RightMarker"),
            List.of(),
            List.of()
        );
        final ClassFile rightMarker = classFile(
            "com/acme/RightMarker",
            "java/lang/Object",
            0x0200,
            List.of("com/acme/LeftMarker"),
            List.of(),
            List.of()
        );
        final ClassFile worker = classFile("com/acme/Worker", "java/lang/Object", 0, List.of("com/acme/LeftMarker"), List.of(), List.of());

        final IrFunction function = lower(main, targetMarker, leftMarker, rightMarker, worker);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intLiteral(0))
        );
    }

    @Test
    void lowersInstanceOfKnownInterfaceWithoutImplementorsToFalseLiteral() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "com/acme/Marker"),
            plain(2, 172, "ireturn")
        );
        final ClassFile marker = classFile("com/acme/Marker", "java/lang/Object", 0x0200, List.of(), List.of(), List.of());

        final IrFunction function = lower(main, marker);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intLiteral(0))
        );
    }

    @Test
    void rejectsInstanceOfUnknownTargetWithSourceDiagnostic() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 193, "instanceof", "com/acme/Missing"),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN045]")
            .hasMessageContaining("com/acme/Missing");
    }

    @Test
    void lowersBranchValueSelectionForIntReturn() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 5, "iconst_2"),
            plain(6, 172, "ireturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.INT, "branchValue0_1"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_1",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.assignInt("branchValue0_1", IrExpression.intLiteral(1)),
            IrInstruction.assignInt("branchValue0_1", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal("branchValue0_1"))
        );
    }

    @Test
    void rejectsBranchValueMergeWithMismatchedKinds() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 42, "aload_0"),
            plain(6, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void rejectsBranchValueMergeWhenBothArmsProduceExpressionlessValues() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)Ljava/io/PrintStream;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            getStatic(2, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plainOperands(3, 167, "goto", 0, 3),
            getStatic(5, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(6, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void rejectsBranchValueMergeWhenTargetArmProducesExpressionlessValueOnly() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/io/PrintStream;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            stringConstant(2, "value"),
            plainOperands(3, 167, "goto", 0, 3),
            getStatic(5, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(6, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void lowersBranchValueSelectionForLongReturn() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)J",
            2,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 9, "lconst_0"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 10, "lconst_1"),
            plain(6, 173, "lreturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.LONG, "branchValue0_1"));
        assertThat(function.instructions()).contains(
            IrInstruction.assignLong("branchValue0_1", IrExpression.longLiteral(0L)),
            IrInstruction.assignLong("branchValue0_1", IrExpression.longLiteral(1L)),
            IrInstruction.returnLong(IrExpression.longLocal("branchValue0_1"))
        );
    }

    @Test
    void lowersBranchValueSelectionForFloatReturn() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)F",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 157, "ifgt", 0, 4),
            plain(2, 12, "fconst_1"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 13, "fconst_2"),
            plain(6, 174, "freturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.FLOAT, "branchValue0_1"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_1",
                IrExpression.intComparison(">", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.assignFloat("branchValue0_1", IrExpression.floatLiteral(1.0f)),
            IrInstruction.assignFloat("branchValue0_1", IrExpression.floatLiteral(2.0f)),
            IrInstruction.returnFloat(IrExpression.floatLocal("branchValue0_1"))
        );
    }

    @Test
    void lowersBranchValueSelectionForDoubleReturn() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)D",
            2,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 14, "dconst_0"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 15, "dconst_1"),
            plain(6, 175, "dreturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.DOUBLE, "branchValue0_1"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_1",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.assignDouble("branchValue0_1", IrExpression.doubleLiteral(0.0)),
            IrInstruction.assignDouble("branchValue0_1", IrExpression.doubleLiteral(1.0)),
            IrInstruction.returnDouble(IrExpression.doubleLocal("branchValue0_1"))
        );
    }

    @Test
    void lowersBranchValueSelectionForObjectReturnFromNonNullGuard() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            stringConstant(2, "missing"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 42, "aload_0"),
            plain(6, 176, "areturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.OBJECT, "branchValue0_1"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_1",
                IrExpression.objectComparison("!=", IrExpression.objectLocal("arg0"), IrExpression.objectNull())
            ),
            IrInstruction.assignObject("branchValue0_1", IrExpression.stringLiteral("missing")),
            IrInstruction.assignObject("branchValue0_1", IrExpression.objectLocal("arg0")),
            IrInstruction.returnObject(IrExpression.objectLocal("branchValue0_1"))
        );
    }

    @Test
    void lowersBranchValueSelectionForIntCompareCondition() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            2,
            2,
            plain(0, 26, "iload_0"),
            plain(1, 27, "iload_1"),
            plainOperands(2, 164, "if_icmple", 0, 4),
            plain(3, 4, "iconst_1"),
            plainOperands(4, 167, "goto", 0, 3),
            plain(6, 5, "iconst_2"),
            plain(7, 172, "ireturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.INT, "branchValue0_2"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_2",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLocal("arg1"))
            ),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(1)),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal("branchValue0_2"))
        );
    }

    @Test
    void lowersBranchValueSelectionForObjectCompareCondition() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;Ljava/lang/Object;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plainOperands(2, 166, "if_acmpne", 0, 4),
            plain(3, 4, "iconst_1"),
            plainOperands(4, 167, "goto", 0, 3),
            plain(6, 5, "iconst_2"),
            plain(7, 172, "ireturn")
        ));

        assertThat(function.locals()).contains(new IrLocal(IrType.INT, "branchValue0_2"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_2",
                IrExpression.objectComparison("!=", IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(1)),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal("branchValue0_2"))
        );
    }

    @Test
    void lowersBranchValueSelectionForObjectEqualityCondition() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;Ljava/lang/Object;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plainOperands(2, 165, "if_acmpeq", 0, 4),
            plain(3, 4, "iconst_1"),
            plainOperands(4, 167, "goto", 0, 3),
            plain(6, 5, "iconst_2"),
            plain(7, 172, "ireturn")
        ));

        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_2",
                IrExpression.objectComparison("==", IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(1)),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal("branchValue0_2"))
        );
    }

    @Test
    void lowersBranchValueSelectionForNullCondition() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 198, "ifnull", 0, 4),
            plain(2, 42, "aload_0"),
            plainOperands(3, 167, "goto", 0, 3),
            stringConstant(5, "missing"),
            plain(6, 176, "areturn")
        ));

        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "branch_value_target_1",
                IrExpression.objectComparison("==", IrExpression.objectLocal("arg0"), IrExpression.objectNull())
            ),
            IrInstruction.assignObject("branchValue0_1", IrExpression.objectLocal("arg0")),
            IrInstruction.assignObject("branchValue0_1", IrExpression.stringLiteral("missing")),
            IrInstruction.returnObject(IrExpression.objectLocal("branchValue0_1"))
        );
    }

    @Test
    void fallsBackToRegularBranchWhenNoMergeJumpExists() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 3),
            stringConstant(2, "missing"),
            plain(3, 176, "areturn"),
            plain(4, 42, "aload_0"),
            plain(5, 176, "areturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_4",
                IrExpression.objectComparison("!=", IrExpression.objectLocal("arg0"), IrExpression.objectNull())
            ),
            IrInstruction.returnObject(IrExpression.stringLiteral("missing")),
            IrInstruction.label("label_4"),
            IrInstruction.returnObject(IrExpression.objectLocal("arg0"))
        );
    }

    @Test
    void fallsBackToRegularBranchWhenTargetIsAdjacent() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 1),
            plain(2, 4, "iconst_1"),
            plain(3, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_2",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.label("label_2"),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void fallsBackToRegularBranchWhenTargetIsEarlier() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", -1, -1),
            plain(2, 4, "iconst_1"),
            plain(3, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).containsExactly(
            IrInstruction.label("label_0"),
            IrInstruction.branchIf(
                "label_0",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void fallsBackToRegularBranchWhenDoneOffsetEqualsTarget() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            stringConstant(2, "missing"),
            plainOperands(3, 167, "goto", 0, 2),
            plain(5, 42, "aload_0"),
            plain(6, 176, "areturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_5",
                IrExpression.objectComparison("!=", IrExpression.objectLocal("arg0"), IrExpression.objectNull())
            ),
            IrInstruction.jump("label_5"),
            IrInstruction.label("label_5"),
            IrInstruction.returnObject(IrExpression.objectLocal("arg0"))
        );
    }

    @Test
    void fallsBackToRegularBranchWhenDoneOffsetDoesNotResolve() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 94),
            plain(5, 5, "iconst_2"),
            plain(6, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_5",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_97"),
            IrInstruction.label("label_5"),
            IrInstruction.returnInt(IrExpression.intLiteral(2))
        );
    }

    @Test
    void fallsBackToRegularBranchWhenTargetOffsetDoesNotResolve() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 96),
            plain(2, 4, "iconst_1"),
            plain(3, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_97",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void usesPlainBranchValueSelectionWhenGuardedConsumerIsNotValueConsumer() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            stringConstant(2, "missing"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 42, "aload_0"),
            plain(6, 87, "pop"),
            plain(7, 177, "return")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "branchValue0_1"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "branch_value_target_1",
                IrExpression.objectComparison("!=", IrExpression.objectLocal("arg0"), IrExpression.objectNull())
            ),
            IrInstruction.assignObject("branchValue0_1", IrExpression.stringLiteral("missing")),
            IrInstruction.jump("branch_value_done_1"),
            IrInstruction.label("branch_value_target_1"),
            IrInstruction.assignObject("branchValue0_1", IrExpression.objectLocal("arg0")),
            IrInstruction.label("branch_value_done_1"),
            IrInstruction.label("label_5"),
            IrInstruction.label("label_6"),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void fallsBackToRegularBranchesWhenTargetArmContainsControlTransfer() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 7),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 10),
            plain(6, 27, "iload_1"),
            plainOperands(7, 158, "ifle", 0, 7),
            plain(10, 5, "iconst_2"),
            plain(11, 172, "ireturn"),
            plain(14, 6, "iconst_3"),
            plain(15, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_8",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_14",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLiteral(3))
        );
    }

    @Test
    void fallsBackToRegularBranchesWhenGuardedBranchesDoNotShareOneTarget() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 7),
            plain(2, 27, "iload_1"),
            plainOperands(3, 158, "ifle", 0, 7),
            plain(6, 4, "iconst_1"),
            plainOperands(7, 167, "goto", 0, 7),
            plain(10, 5, "iconst_2"),
            plain(11, 172, "ireturn"),
            plain(14, 6, "iconst_3"),
            plain(15, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_8",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLiteral(3))
        );
    }

    @Test
    void fallsBackToRegularBranchesWhenEarlierBranchAlreadyTargetsDoneOffset() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 8),
            plain(2, 27, "iload_1"),
            plainOperands(3, 158, "ifle", 0, 11),
            plain(6, 4, "iconst_1"),
            plainOperands(7, 167, "goto", 0, 7),
            plain(10, 5, "iconst_2"),
            plain(11, 172, "ireturn"),
            plain(14, 6, "iconst_3"),
            plain(15, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_9",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_14",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLiteral(3))
        );
    }

    @Test
    void fallsBackToRegularBranchesWhenTargetBlockDoesNotProduceSelectedValue() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 7),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 8),
            plain(6, 27, "iload_1"),
            plainOperands(7, 158, "ifle", 0, 7),
            plain(10, 172, "ireturn"),
            plain(14, 6, "iconst_3"),
            plain(15, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_8",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_14",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(1)),
            IrInstruction.returnInt(IrExpression.intLiteral(3))
        );
    }

    @Test
    void rejectsGuardedMergeWhenBothArmsProduceExpressionlessValues() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/io/PrintStream;",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            getStatic(2, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plainOperands(3, 167, "goto", 0, 3),
            getStatic(5, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plainOperands(6, 58, "astore", 1),
            plainOperands(8, 25, "aload", 1),
            plain(10, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void rejectsGuardedMergeWhenArmsProduceDifferentKinds() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(ILjava/lang/Object;)V",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 43, "aload_1"),
            plain(6, 87, "pop"),
            plain(7, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void rejectsGuardedMergeWhenElseArmProducesExpressionlessValueOnly() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/io/PrintStream;",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            getStatic(2, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plainOperands(3, 167, "goto", 0, 3),
            stringConstant(5, "value"),
            plainOperands(6, 58, "astore", 1),
            plainOperands(8, 25, "aload", 1),
            plain(10, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void rejectsGuardedMergeWhenTargetArmProducesExpressionlessValueOnly() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/io/PrintStream;",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            stringConstant(2, "value"),
            plainOperands(3, 167, "goto", 0, 3),
            getStatic(5, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plainOperands(6, 58, "astore", 1),
            plainOperands(8, 25, "aload", 1),
            plain(10, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void fallsBackToRegularBranchWhenElseArmDoesNotLeaveSelectedValue() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 5),
            plain(2, 4, "iconst_1"),
            plain(3, 87, "pop"),
            plainOperands(4, 167, "goto", 0, 3),
            plain(7, 5, "iconst_2"),
            plainOperands(8, 54, "istore", 1),
            plainOperands(10, 21, "iload", 1),
            plain(12, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "local1"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_6",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_7"),
            IrInstruction.label("label_7"),
            IrInstruction.assignInt("local1", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal("local1"))
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenElseArmDoesNotLeaveSelectedValue() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            1,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 8),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 6),
            plain(5, 4, "iconst_1"),
            plain(6, 87, "pop"),
            plainOperands(7, 167, "goto", 0, 4),
            plain(10, 5, "iconst_2"),
            plain(11, 62, "istore_3"),
            plain(12, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "local3"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_11"),
            IrInstruction.label("label_10"),
            IrInstruction.assignInt("local3", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal("arg2"))
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenTargetArmDoesNotLeaveSelectedValue() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            1,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 8),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 6),
            plain(5, 4, "iconst_1"),
            plainOperands(6, 167, "goto", 0, 4),
            plain(9, 5, "iconst_2"),
            plain(10, 87, "pop"),
            plain(11, 62, "istore_3"),
            plain(12, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "local3"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_10"),
            IrInstruction.label("label_10"),
            IrInstruction.assignInt("local3", IrExpression.intLiteral(1)),
            IrInstruction.returnInt(IrExpression.intLocal("arg2"))
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenMultipleMergeJumpsExist() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)V",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 12),
            plain(2, 27, "iload_1"),
            plainOperands(3, 158, "ifle", 0, 10),
            plain(4, 3, "iconst_0"),
            plain(5, 87, "pop"),
            plainOperands(6, 167, "goto", 0, 10),
            plain(9, 4, "iconst_1"),
            plain(10, 87, "pop"),
            plainOperands(11, 167, "goto", 0, 5),
            plain(13, 177, "return"),
            plain(16, 177, "return")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_13",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_13",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_16"),
            IrInstruction.label("label_13"),
            IrInstruction.returnVoid(),
            IrInstruction.label("label_16"),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenValueMergeTargetsUseDifferentOffsets() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            3,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 7),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 6),
            plain(5, 4, "iconst_1"),
            plainOperands(6, 167, "goto", 0, 5),
            plain(9, 0, "nop"),
            plain(10, 5, "iconst_2"),
            plain(11, 62, "istore_3"),
            plain(12, 172, "ireturn")
        ));

        assertThat(function.instructions())
            .allSatisfy(instruction -> assertThat(instruction.value().orElse("")).doesNotStartWith("guarded_value_"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_9",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "branch_value_target_4",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.assignInt("branchValue0_4", IrExpression.intLiteral(1)),
            IrInstruction.assignInt("branchValue0_4", IrExpression.intLiteral(2)),
            IrInstruction.label("label_9"),
            IrInstruction.assignInt("local3_int_1", IrExpression.intLocal("branchValue0_4")),
            IrInstruction.returnInt(IrExpression.intLocal("arg2"))
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenGuardedBranchesUseDifferentTargets() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)V",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 12),
            plain(2, 27, "iload_1"),
            plainOperands(3, 158, "ifle", 0, 7),
            plain(4, 3, "iconst_0"),
            plain(5, 87, "pop"),
            plainOperands(6, 167, "goto", 0, 10),
            plain(10, 177, "return"),
            plain(13, 177, "return"),
            plain(16, 177, "return")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_13",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.label("label_10"),
            IrInstruction.returnVoid(),
            IrInstruction.label("label_13"),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenTargetValueBlockContainsControlTransfer() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            3,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 7),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 5),
            plain(5, 4, "iconst_1"),
            plainOperands(6, 167, "goto", 0, 6),
            plain(9, 5, "iconst_2"),
            plainOperands(10, 167, "goto", 0, 2),
            plain(12, 62, "istore_3"),
            plain(13, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_9",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_9",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_12"),
            IrInstruction.label("label_9"),
            IrInstruction.jump("label_12"),
            IrInstruction.label("label_12"),
            IrInstruction.assignInt("local3", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenTargetBlockRebuildsStackPrefix() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            3,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 7),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 5),
            plain(5, 4, "iconst_1"),
            plainOperands(6, 167, "goto", 0, 6),
            plain(9, 87, "pop"),
            plain(10, 28, "iload_2"),
            plain(11, 5, "iconst_2"),
            plain(12, 62, "istore_3"),
            plain(13, 172, "ireturn")
        ));

        assertNoSynthesizedBranchValue(function);
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_9",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_9",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_12"),
            IrInstruction.label("label_9"),
            IrInstruction.assignInt("local3", IrExpression.intLiteral(2)),
            IrInstruction.label("label_12"),
            IrInstruction.returnInt(IrExpression.intLocal("arg2"))
        );
    }

    @Test
    void fallsBackToRegularGuardedBranchWhenPrefixContainsTableSwitch() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            3,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 25),
            plain(3, 27, "iload_1"),
            plainOperands(4, 170, "tableswitch", tableSwitchOperands(4, 23, 0, 0, 23)),
            plain(24, 4, "iconst_1"),
            plainOperands(25, 167, "goto", 0, 4),
            plain(27, 0, "nop"),
            plain(28, 5, "iconst_2"),
            plain(29, 62, "istore_3"),
            plain(30, 172, "ireturn")
        ));

        assertThat(function.instructions())
            .allSatisfy(instruction -> assertThat(instruction.value().orElse("")).doesNotStartWith("guarded_value_"));
        assertThat(function.instructions()).contains(
            IrInstruction.branchIf(
                "label_27",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.assignInt("switch0", IrExpression.intLocal("arg1")),
            IrInstruction.branchIf(
                "label_27",
                IrExpression.intComparison("==", IrExpression.intLocal("switch0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_27"),
            IrInstruction.label("label_29"),
            IrInstruction.assignInt("local3_int_1", IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void rejectsGuardedBranchValueMergeWithMismatchedKinds() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(IILjava/lang/String;)Ljava/lang/Object;",
            1,
            3,
            plain(0, 44, "aload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 8),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 6),
            plain(5, 4, "iconst_1"),
            plainOperands(6, 167, "goto", 0, 5),
            plain(10, 44, "aload_2"),
            plain(11, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 2");
            });
    }

    @Test
    void rejectsGuardedBranchValueMergeWhenBothArmsProduceExpressionlessValues() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;II)Ljava/io/PrintStream;",
            1,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plainOperands(2, 158, "ifle", 0, 8),
            plain(3, 28, "iload_2"),
            plainOperands(4, 158, "ifle", 0, 6),
            getStatic(5, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plainOperands(6, 167, "goto", 0, 5),
            getStatic(10, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(11, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 2");
            });
    }

    @Test
    void rejectsGuardedBranchValueMergeWhenTargetArmProducesExpressionlessObject() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(II)Ljava/lang/Object;",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 7),
            plain(2, 27, "iload_1"),
            plainOperands(3, 158, "ifle", 0, 5),
            stringConstant(4, "ok"),
            plainOperands(5, 167, "goto", 0, 4),
            getStatic(8, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(9, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN051");
                assertThat(exception.diagnostic().subject()).isEqualTo("bytecode offset 1");
            });
    }

    @Test
    void lowersNestedBranchValueSelectionForIntReturn() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 7),
            plain(2, 27, "iload_1"),
            plainOperands(3, 158, "ifle", 0, 5),
            plain(4, 4, "iconst_1"),
            plainOperands(5, 167, "goto", 0, 4),
            plain(8, 5, "iconst_2"),
            plain(9, 172, "ireturn")
        ));

        final String selectedLocal = function.locals().getFirst().name();
        assertThat(function.locals()).contains(new IrLocal(IrType.INT, selectedLocal));
        assertThat(selectedLocal).startsWith("branchValue");
        assertThat(function.instructions()).contains(
            IrInstruction.assignInt(selectedLocal, IrExpression.intLiteral(1)),
            IrInstruction.assignInt(selectedLocal, IrExpression.intLiteral(2)),
            IrInstruction.returnInt(IrExpression.intLocal(selectedLocal))
        );
    }

    @Test
    void lowersGuardedValueSelectionWithStackPrefixIntoStore() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(III)I",
            3,
            4,
            plain(0, 28, "iload_2"),
            plain(1, 26, "iload_0"),
            plainOperands(2, 158, "ifle", 0, 7),
            plain(3, 27, "iload_1"),
            plainOperands(4, 158, "ifle", 0, 5),
            plain(5, 4, "iconst_1"),
            plainOperands(6, 167, "goto", 0, 4),
            plain(9, 5, "iconst_2"),
            plain(10, 62, "istore_3"),
            plain(11, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.INT, "branchValue0_2"),
            new IrLocal(IrType.INT, "local3_int_1")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.branchIf(
                "guarded_value_target_2",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "guarded_value_target_2",
                IrExpression.intComparison("<=", IrExpression.intLocal("arg1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(1)),
            IrInstruction.jump("guarded_value_done_2"),
            IrInstruction.label("guarded_value_target_2"),
            IrInstruction.assignInt("branchValue0_2", IrExpression.intLiteral(2)),
            IrInstruction.label("guarded_value_done_2"),
            IrInstruction.label("label_9"),
            IrInstruction.label("label_10"),
            IrInstruction.assignInt("local3_int_1", IrExpression.intLocal("branchValue0_2")),
            IrInstruction.returnInt(IrExpression.intLocal("arg2"))
        );
    }

    @Test
    void lowersGuardedValueSelectionIntoGenericIntStore() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 4, "iconst_1"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 5, "iconst_2"),
            plainOperands(6, 54, "istore", 1),
            plainOperands(8, 21, "iload", 1),
            plain(10, 172, "ireturn")
        ));

        assertThat(function.locals()).contains(
            new IrLocal(IrType.INT, "branchValue0_1"),
            new IrLocal(IrType.INT, "local1_int_1")
        );
        assertThat(function.instructions()).contains(
            IrInstruction.assignInt("local1_int_1", IrExpression.intLocal("branchValue0_1")),
            IrInstruction.returnInt(IrExpression.intLocal("local1_int_1"))
        );
    }

    @Test
    void lowersGuardedValueSelectionIntoGenericLongStore() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)J",
            2,
            3,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 9, "lconst_0"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 10, "lconst_1"),
            plainOperands(6, 55, "lstore", 1),
            plainOperands(8, 22, "lload", 1),
            plain(10, 173, "lreturn")
        ));

        assertThat(function.locals()).contains(
            new IrLocal(IrType.LONG, "branchValue0_1"),
            new IrLocal(IrType.LONG, "local1_long_1")
        );
        assertThat(function.instructions()).contains(
            IrInstruction.assignLong("local1_long_1", IrExpression.longLocal("branchValue0_1")),
            IrInstruction.returnLong(IrExpression.longLocal("local1_long_1"))
        );
    }

    @Test
    void lowersGuardedValueSelectionIntoGenericFloatStore() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)F",
            1,
            2,
            plain(0, 26, "iload_0"),
            plainOperands(1, 157, "ifgt", 0, 4),
            plain(2, 12, "fconst_1"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 13, "fconst_2"),
            plainOperands(6, 56, "fstore", 1),
            plainOperands(8, 23, "fload", 1),
            plain(10, 174, "freturn")
        ));

        assertThat(function.locals()).contains(
            new IrLocal(IrType.FLOAT, "branchValue0_1"),
            new IrLocal(IrType.FLOAT, "local1_float_1")
        );
        assertThat(function.instructions()).contains(
            IrInstruction.assignFloat("local1_float_1", IrExpression.floatLocal("branchValue0_1")),
            IrInstruction.returnFloat(IrExpression.floatLocal("local1_float_1"))
        );
    }

    @Test
    void lowersGuardedValueSelectionIntoGenericDoubleStore() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)D",
            2,
            3,
            plain(0, 26, "iload_0"),
            plainOperands(1, 158, "ifle", 0, 4),
            plain(2, 14, "dconst_0"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 15, "dconst_1"),
            plainOperands(6, 57, "dstore", 1),
            plainOperands(8, 24, "dload", 1),
            plain(10, 175, "dreturn")
        ));

        assertThat(function.locals()).contains(
            new IrLocal(IrType.DOUBLE, "branchValue0_1"),
            new IrLocal(IrType.DOUBLE, "local1_double_1")
        );
        assertThat(function.instructions()).contains(
            IrInstruction.assignDouble("local1_double_1", IrExpression.doubleLocal("branchValue0_1")),
            IrInstruction.returnDouble(IrExpression.doubleLocal("local1_double_1"))
        );
    }

    @Test
    void lowersGuardedValueSelectionIntoGenericObjectStore() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            2,
            plain(0, 42, "aload_0"),
            plainOperands(1, 199, "ifnonnull", 0, 4),
            stringConstant(2, "missing"),
            plainOperands(3, 167, "goto", 0, 3),
            plain(5, 42, "aload_0"),
            plainOperands(6, 58, "astore", 1),
            plainOperands(8, 25, "aload", 1),
            plain(10, 176, "areturn")
        ));

        assertThat(function.locals()).contains(
            new IrLocal(IrType.OBJECT, "branchValue0_1"),
            new IrLocal(IrType.OBJECT, "local1_object_1")
        );
        assertThat(function.instructions()).contains(
            IrInstruction.assignObject("local1_object_1", IrExpression.objectLocal("branchValue0_1")),
            IrInstruction.returnObject(IrExpression.objectLocal("local1_object_1"))
        );
    }

    @Test
    void lowersTableSwitchToSelectorLocalBranchesAndDefaultJump() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 170, "tableswitch", tableSwitchOperands(1, 20, -1, 1, 9, 11, 13)),
            plain(10, 2, "iconst_m1"),
            plain(11, 172, "ireturn"),
            plain(12, 3, "iconst_0"),
            plain(13, 172, "ireturn"),
            plain(14, 4, "iconst_1"),
            plain(15, 172, "ireturn"),
            plain(21, 5, "iconst_2"),
            plain(22, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "switch0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("switch0", IrExpression.intLocal("arg0")),
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("==", IrExpression.intLocal("switch0"), IrExpression.intLiteral(-1))
            ),
            IrInstruction.branchIf(
                "label_12",
                IrExpression.intComparison("==", IrExpression.intLocal("switch0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.branchIf(
                "label_14",
                IrExpression.intComparison("==", IrExpression.intLocal("switch0"), IrExpression.intLiteral(1))
            ),
            IrInstruction.jump("label_21"),
            IrInstruction.label("label_10"),
            IrInstruction.returnInt(IrExpression.intLiteral(-1)),
            IrInstruction.label("label_12"),
            IrInstruction.returnInt(IrExpression.intLiteral(0)),
            IrInstruction.label("label_14"),
            IrInstruction.returnInt(IrExpression.intLiteral(1)),
            IrInstruction.label("label_21"),
            IrInstruction.returnInt(IrExpression.intLiteral(2))
        );
    }

    @Test
    void lowersLookupSwitchToSelectorLocalBranchesAndDefaultJump() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            plainOperands(1, 171, "lookupswitch", lookupSwitchOperands(1, 19, -10, 9, 7, 11)),
            plain(10, 2, "iconst_m1"),
            plain(11, 172, "ireturn"),
            plain(12, 4, "iconst_1"),
            plain(13, 172, "ireturn"),
            plain(20, 5, "iconst_2"),
            plain(21, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "switch0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("switch0", IrExpression.intLocal("arg0")),
            IrInstruction.branchIf(
                "label_10",
                IrExpression.intComparison("==", IrExpression.intLocal("switch0"), IrExpression.intLiteral(-10))
            ),
            IrInstruction.branchIf(
                "label_12",
                IrExpression.intComparison("==", IrExpression.intLocal("switch0"), IrExpression.intLiteral(7))
            ),
            IrInstruction.jump("label_20"),
            IrInstruction.label("label_10"),
            IrInstruction.returnInt(IrExpression.intLiteral(-1)),
            IrInstruction.label("label_12"),
            IrInstruction.returnInt(IrExpression.intLiteral(1)),
            IrInstruction.label("label_20"),
            IrInstruction.returnInt(IrExpression.intLiteral(2))
        );
    }

    @Test
    void lowersSingleTargetInterfaceVoidReturnToDirectCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Action;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Action", "run", "()V")),
            plain(2, 177, "return")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Action", "run", "()V"),
            implementationType("com/acme/ActionImpl", "com/acme/Action", "run", "()V")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                symbol("com/acme/ActionImpl", "run", "()V"),
                List.of(IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSingleTargetInterfaceIntReturnToDirectCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()I")),
            plain(2, 172, "ireturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()I"),
            implementationType("com/acme/MetricImpl", "com/acme/Metric", "value", "()I")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                symbol("com/acme/MetricImpl", "value", "()I"),
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersSingleTargetInterfaceLongReturnToDirectCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()J")),
            plain(2, 173, "lreturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()J"),
            implementationType("com/acme/MetricImpl", "com/acme/Metric", "value", "()J")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall(
                symbol("com/acme/MetricImpl", "value", "()J"),
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersSingleTargetInterfaceFloatReturnToDirectCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)F",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()F")),
            plain(2, 174, "freturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()F"),
            implementationType("com/acme/MetricImpl", "com/acme/Metric", "value", "()F")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnFloat(IrExpression.floatCall(
                symbol("com/acme/MetricImpl", "value", "()F"),
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersSingleTargetInterfaceDoubleReturnToDirectCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)D",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()D")),
            plain(2, 175, "dreturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()D"),
            implementationType("com/acme/MetricImpl", "com/acme/Metric", "value", "()D")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnDouble(IrExpression.doubleCall(
                symbol("com/acme/MetricImpl", "value", "()D"),
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersSingleTargetInterfaceObjectReturnToDirectCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Provider;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Provider", "value", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Provider", "value", "()Ljava/lang/Object;"),
            implementationType("com/acme/ProviderImpl", "com/acme/Provider", "value", "()Ljava/lang/Object;")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                symbol("com/acme/ProviderImpl", "value", "()Ljava/lang/Object;"),
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersMultipleTargetInterfaceObjectReturnToDispatchCallAndStub() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Provider;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Provider", "value", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        );

        final IrProgram program = lowerProgram(
            main,
            interfaceType("com/acme/Provider", "value", "()Ljava/lang/Object;"),
            implementationType("com/acme/BProvider", "com/acme/Provider", "value", "()Ljava/lang/Object;"),
            implementationType("com/acme/AProvider", "com/acme/Provider", "value", "()Ljava/lang/Object;")
        );

        final String dispatchSymbol = "javan_dispatch_com_acme_Provider_value___Ljava_lang_Object_";
        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                dispatchSymbol,
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo(dispatchSymbol);
            assertThat(dispatch.returnType()).isEqualTo(IrType.OBJECT);
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/AProvider", "com/acme/BProvider");
        });
    }

    @Test
    void lowersVirtualDispatchSkipsAbstractResolvedTargetsWithoutCode() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Base;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("com/acme/Base", "value", "()I")),
            plain(2, 172, "ireturn")
        );
        final ClassFile base = classFile(
            "com/acme/Base",
            "java/lang/Object",
            0x0400,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0x0401, "value", "()I", Optional.empty()))
        );
        final ClassFile leaf = classFile(
            "com/acme/Leaf",
            "com/acme/Base",
            0,
            List.of(),
            List.of(),
            List.of(method(0, "value", "()I", 1, 1, plain(0, 3, "iconst_0"), plain(1, 172, "ireturn")))
        );

        final IrProgram program = lowerProgram(main, base, leaf);

        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_dispatch_com_acme_Base_value___I",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo("javan_dispatch_com_acme_Base_value___I");
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/Leaf");
        });
    }

    @Test
    void lowerProgramAddsRunnableDispatchForVirtualThreadExecutorExecute() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()V",
            3,
            1,
            invokeStatic(0, new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
            plain(1, 75, "astore_0"),
            plain(2, 42, "aload_0"),
            classInstruction(3, 187, "new", "com/acme/Task"),
            plain(4, 89, "dup"),
            invokeSpecial(5, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(6, new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
            plain(7, 177, "return")
        );
        final ClassFile task = classFile(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                method(0, "<init>", "()V", 0, 1, plain(0, 177, "return")),
                method(0, "run", "()V", 0, 1, plain(0, 177, "return"))
            )
        );
        final EntryPoint entryPoint = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint taskRun = new EntryPoint("com/acme/Task", "run", "()V");
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)));
        classes.put(task.name(), task);

        final IrProgram program = new BytecodeToIR().lower(
            classes,
            new CallGraph(entryPoint, List.of(entryPoint, taskRun), List.of()),
            SourceLineIndex.empty()
        );

        assertThat(program.dispatches()).extracting(IrDispatch::symbol).contains("javan_dispatch_java_lang_Runnable_run___V");
    }

    @Test
    void lowerProgramAddsRunnableDispatchForFactoryBackedThreadPerTaskExecutorExecute() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()V",
            4,
            1,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(1, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            invokeStatic(2, new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")),
            plain(3, 75, "astore_0"),
            plain(4, 42, "aload_0"),
            classInstruction(5, 187, "new", "com/acme/Task"),
            plain(6, 89, "dup"),
            invokeSpecial(7, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(8, new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
            plain(9, 177, "return")
        );
        final ClassFile task = classFile(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                method(0, "<init>", "()V", 0, 1, plain(0, 177, "return")),
                method(0, "run", "()V", 0, 1, plain(0, 177, "return"))
            )
        );
        final EntryPoint entryPoint = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint taskRun = new EntryPoint("com/acme/Task", "run", "()V");
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)));
        classes.put(task.name(), task);

        final IrProgram program = new BytecodeToIR().lower(
            classes,
            new CallGraph(entryPoint, List.of(entryPoint, taskRun), List.of()),
            SourceLineIndex.empty()
        );

        assertThat(program.dispatches()).extracting(IrDispatch::symbol).contains("javan_dispatch_java_lang_Runnable_run___V");
    }

    @Test
    void lowerProgramAddsRunnableDispatchForParameterizedFactoryBackedThreadPerTaskExecutorExecute() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()V",
            5,
            1,
            stringConstant(0, "worker-"),
            plain(1, 10, "lconst_1"),
            invokeStatic(2, new MethodRef("com/acme/Main", "factory", "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;")),
            invokeStatic(3, new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")),
            plain(4, 75, "astore_0"),
            plain(5, 42, "aload_0"),
            classInstruction(6, 187, "new", "com/acme/Task"),
            plain(7, 89, "dup"),
            invokeSpecial(8, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(9, new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
            plain(10, 177, "return")
        );
        final MethodInfo factory = method(
            0x0008,
            "factory",
            "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
            4,
            3,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 42, "aload_0"),
            plain(2, 31, "lload_1"),
            invokeInterface(3, new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(4, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plain(5, 176, "areturn")
        );
        final ClassFile task = classFile(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                method(0, "<init>", "()V", 0, 1, plain(0, 177, "return")),
                method(0, "run", "()V", 0, 1, plain(0, 177, "return"))
            )
        );
        final EntryPoint entryPoint = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint taskRun = new EntryPoint("com/acme/Task", "run", "()V");
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main, factory)));
        classes.put(task.name(), task);

        final IrProgram program = new BytecodeToIR().lower(
            classes,
            new CallGraph(entryPoint, List.of(entryPoint, taskRun), List.of()),
            SourceLineIndex.empty()
        );

        assertThat(program.dispatches()).extracting(IrDispatch::symbol).contains("javan_dispatch_java_lang_Runnable_run___V");
    }

    @Test
    void lowerProgramRejectsUnknownExecutorReceiverForVirtualThreadExecute() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/util/concurrent/ExecutorService;)V",
            3,
            1,
            plain(0, 42, "aload_0"),
            classInstruction(1, 187, "new", "com/acme/Task"),
            plain(2, 89, "dup"),
            invokeSpecial(3, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(4, new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
            plain(5, 177, "return")
        );
        final ClassFile task = classFile(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                method(0, "<init>", "()V", 0, 1, plain(0, 177, "return")),
                method(0, "run", "()V", 0, 1, plain(0, 177, "return"))
            )
        );
        final EntryPoint entryPoint = new EntryPoint("com/acme/Main", "main", "(Ljava/util/concurrent/ExecutorService;)V");
        final EntryPoint taskRun = new EntryPoint("com/acme/Task", "run", "()V");
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)));
        classes.put(task.name(), task);

        assertThatThrownBy(() -> new BytecodeToIR().lower(
            classes,
            new CallGraph(entryPoint, List.of(entryPoint, taskRun), List.of()),
            SourceLineIndex.empty()
        )).isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("Expected virtual-thread executor receiver value on the bytecode stack");
    }

    @Test
    void lowerProgramRejectsUnknownThreadFactoryForVirtualThreadExecutor() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/util/concurrent/ThreadFactory;)V",
            4,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")),
            plain(2, 75, "astore_0"),
            plain(3, 42, "aload_0"),
            classInstruction(4, 187, "new", "com/acme/Task"),
            plain(5, 89, "dup"),
            invokeSpecial(6, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(7, new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
            plain(8, 177, "return")
        );
        final ClassFile task = classFile(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                method(0, "<init>", "()V", 0, 1, plain(0, 177, "return")),
                method(0, "run", "()V", 0, 1, plain(0, 177, "return"))
            )
        );
        final EntryPoint entryPoint = new EntryPoint("com/acme/Main", "main", "(Ljava/util/concurrent/ThreadFactory;)V");
        final EntryPoint taskRun = new EntryPoint("com/acme/Task", "run", "()V");
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(main)));
        classes.put(task.name(), task);

        assertThatThrownBy(() -> new BytecodeToIR().lower(
            classes,
            new CallGraph(entryPoint, List.of(entryPoint, taskRun), List.of()),
            SourceLineIndex.empty()
        )).isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("Expected virtual-thread factory receiver value on the bytecode stack");
    }

    @Test
    void deduplicatesInheritedInterfaceDispatchTargetsThatResolveToSameMethod() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Provider;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Provider", "value", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        );
        final ClassFile provider = interfaceType("com/acme/Provider", "value", "()Ljava/lang/Object;");
        final ClassFile baseProvider = classFile(
            "com/acme/BaseProvider",
            "java/lang/Object",
            0,
            List.of("com/acme/Provider"),
            List.of(),
            List.of(new MethodInfo(0, "value", "()Ljava/lang/Object;", Optional.empty()))
        );
        final ClassFile childProvider = classFile(
            "com/acme/ChildProvider",
            "com/acme/BaseProvider",
            0,
            List.of(),
            List.of(),
            List.of()
        );
        final ClassFile otherProvider = classFile(
            "com/acme/OtherProvider",
            "java/lang/Object",
            0,
            List.of("com/acme/Provider"),
            List.of(),
            List.of(new MethodInfo(0, "value", "()Ljava/lang/Object;", Optional.empty()))
        );

        final IrProgram program = lowerProgram(main, provider, baseProvider, childProvider, otherProvider);

        final String dispatchSymbol = "javan_dispatch_com_acme_Provider_value___Ljava_lang_Object_";
        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                dispatchSymbol,
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo(dispatchSymbol);
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/BaseProvider", "com/acme/OtherProvider");
        });
    }

    @Test
    void lowersMultipleTargetInterfaceVoidReturnToDispatchCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Action;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Action", "run", "()V")),
            plain(2, 177, "return")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Action", "run", "()V"),
            implementationType("com/acme/AAction", "com/acme/Action", "run", "()V"),
            implementationType("com/acme/BAction", "com/acme/Action", "run", "()V")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_dispatch_com_acme_Action_run___V",
                List.of(IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersThreadStartWithoutRunnableTargetsToRuntimeCallOnly() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "start", "()V")),
            plain(2, 177, "return")
        );

        final IrProgram program = lowerProgram(main);

        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_thread_start",
                List.of(IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnVoid()
        );
        assertThat(program.dispatches()).isEmpty();
    }

    @Test
    void lowersThreadStartRunnableDispatchThroughInterfaceInheritance() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()V",
            4,
            1,
            classInstruction(0, 187, "new", "java/lang/Thread"),
            plain(1, 89, "dup"),
            classInstruction(2, 187, "new", "com/acme/TaskImpl"),
            plain(3, 89, "dup"),
            invokeSpecial(4, new MethodRef("com/acme/TaskImpl", "<init>", "()V")),
            invokeSpecial(5, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
            invokeVirtual(6, new MethodRef("java/lang/Thread", "start", "()V")),
            plain(7, 177, "return")
        );
        final ClassFile taskLike = classFile(
            "com/acme/TaskLike",
            "java/lang/Object",
            0x0200,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(new MethodInfo(0x0401, "run", "()V", Optional.empty()))
        );
        final ClassFile taskImpl = classFile(
            "com/acme/TaskImpl",
            "java/lang/Object",
            0,
            List.of("com/acme/TaskLike"),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "()V", Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(plain(0, 177, "return"))))),
                method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
            )
        );

        final IrProgram program = lowerProgram(main, taskLike, taskImpl);

        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo("javan_dispatch_java_lang_Runnable_run___V");
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/TaskImpl");
        });
    }

    @Test
    void lowersThreadStartRunnableDispatchOnlyForConstructedTarget() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()V",
            4,
            1,
            classInstruction(0, 187, "new", "java/lang/Thread"),
            plain(1, 89, "dup"),
            classInstruction(2, 187, "new", "com/acme/Task"),
            plain(3, 89, "dup"),
            invokeSpecial(4, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeSpecial(5, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
            invokeVirtual(6, new MethodRef("java/lang/Thread", "start", "()V")),
            plain(7, 177, "return")
        );
        final ClassFile task = classFile(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "()V", Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(plain(0, 177, "return"))))),
                method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
            )
        );
        final ClassFile otherTask = classFile(
            "com/acme/OtherTask",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
        );

        final IrProgram program = lowerProgram(main, task, otherTask);

        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo("javan_dispatch_java_lang_Runnable_run___V");
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/Task");
        });
    }

    @Test
    void lowersThreadStartRunnableDispatchFallsBackToAllTargetsWhenUnknown() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)V",
            3,
            1,
            classInstruction(0, 187, "new", "java/lang/Thread"),
            plain(1, 89, "dup"),
            plain(2, 42, "aload_0"),
            invokeSpecial(3, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
            invokeVirtual(4, new MethodRef("java/lang/Thread", "start", "()V")),
            plain(5, 177, "return")
        );
        final ClassFile taskA = classFile(
            "com/acme/TaskA",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
        );
        final ClassFile taskB = classFile(
            "com/acme/TaskB",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
        );

        final IrProgram program = lowerProgram(main, taskA, taskB);

        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo("javan_dispatch_java_lang_Runnable_run___V");
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/TaskA", "com/acme/TaskB");
        });
    }

    @Test
    void lowersThreadStartRunnableDispatchFallsBackWhenRunnableTargetReloadsFromLocal() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()V",
            4,
            1,
            classInstruction(0, 187, "new", "com/acme/TaskA"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("com/acme/TaskA", "<init>", "()V")),
            plain(3, 75, "astore_0"),
            classInstruction(4, 187, "new", "com/acme/TaskB"),
            plain(5, 89, "dup"),
            invokeSpecial(6, new MethodRef("com/acme/TaskB", "<init>", "()V")),
            plain(7, 87, "pop"),
            classInstruction(8, 187, "new", "java/lang/Thread"),
            plain(9, 89, "dup"),
            plain(10, 42, "aload_0"),
            invokeSpecial(11, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
            invokeVirtual(12, new MethodRef("java/lang/Thread", "start", "()V")),
            plain(13, 177, "return")
        );
        final ClassFile taskA = classFile(
            "com/acme/TaskA",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "()V", Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(plain(0, 177, "return"))))),
                method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
            )
        );
        final ClassFile taskB = classFile(
            "com/acme/TaskB",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "()V", Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(plain(0, 177, "return"))))),
                method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
            )
        );

        final IrProgram program = lowerProgram(main, taskA, taskB);

        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo("javan_dispatch_java_lang_Runnable_run___V");
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/TaskA", "com/acme/TaskB");
        });
    }

    @Test
    void lowersThreadStartRunnableDispatchSkipsAbstractRunnableTargetsWithoutCode() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)V",
            3,
            1,
            classInstruction(0, 187, "new", "java/lang/Thread"),
            plain(1, 89, "dup"),
            plain(2, 42, "aload_0"),
            invokeSpecial(3, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
            invokeVirtual(4, new MethodRef("java/lang/Thread", "start", "()V")),
            plain(5, 177, "return")
        );
        final ClassFile abstractTask = classFile(
            "com/acme/AbstractTask",
            "java/lang/Object",
            0x0400,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(new MethodInfo(0x0401, "run", "()V", Optional.empty()))
        );
        final ClassFile taskB = classFile(
            "com/acme/TaskB",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            List.of(),
            List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
        );

        final IrProgram program = lowerProgram(main, abstractTask, taskB);

        assertThat(program.dispatches()).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.symbol()).isEqualTo("javan_dispatch_java_lang_Runnable_run___V");
            assertThat(dispatch.targets()).extracting("owner").containsExactly("com/acme/TaskB");
        });
    }

    @Test
    void lowersThreadCurrentThreadStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Thread;",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_thread_current", List.of()))
        );
    }

    @Test
    void lowersLockSupportParkStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            0,
            0,
            invokeStatic(0, new MethodRef("java/util/concurrent/locks/LockSupport", "park", "()V")),
            plain(1, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_park", List.of()),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersLockSupportParkNanosStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)V",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/util/concurrent/locks/LockSupport", "parkNanos", "(J)V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_park_nanos", List.of(IrExpression.longLocal("arg0"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersLockSupportParkUntilStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)V",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/util/concurrent/locks/LockSupport", "parkUntil", "(J)V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_park_until", List.of(IrExpression.longLocal("arg0"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersLockSupportUnparkStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/concurrent/locks/LockSupport", "unpark", "(Ljava/lang/Thread;)V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_unpark", List.of(IrExpression.objectLocal("arg0"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowerLockSupportStaticCallRejectsUnsupportedBlockerOverload() {
        final ClassFile owner = classFile(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of()
        );
        final MethodInfo method = method(0x0008, "main", "()V", 0, 0);
        assertThat(BytecodeToIRInvokeSupport.lowerLockSupportStaticCall(
            owner,
            method,
            new MethodRef("java/util/concurrent/locks/LockSupport", "parkNanos", "(Ljava/lang/Object;J)V"),
            new ArrayList<>(),
            new ArrayList<>()
        )).isFalse();
    }

    @Test
    void lowersThreadLocalConstructorPredicateForSupportedSignature() {
        assertThat(BytecodeToIRInvokeSupport.lowerThreadLocalConstructor(new MethodRef("java/lang/ThreadLocal", "<init>", "()V")))
            .isTrue();
    }

    @Test
    void lowersThreadLocalConstructorPredicateRejectsWrongOwner() {
        assertThat(BytecodeToIRInvokeSupport.lowerThreadLocalConstructor(new MethodRef("java/lang/Object", "<init>", "()V")))
            .isFalse();
    }

    @Test
    void lowersThreadLocalConstructorPredicateRejectsWrongName() {
        assertThat(BytecodeToIRInvokeSupport.lowerThreadLocalConstructor(new MethodRef("java/lang/ThreadLocal", "get", "()V")))
            .isFalse();
    }

    @Test
    void lowersThreadLocalConstructorPredicateRejectsWrongDescriptor() {
        assertThat(BytecodeToIRInvokeSupport.lowerThreadLocalConstructor(new MethodRef("java/lang/ThreadLocal", "<init>", "(I)V")))
            .isFalse();
    }

    @Test
    void lowersThreadLocalGetToRuntimeHelperWithTemporaryLocal() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/ThreadLocal;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/ThreadLocal", "get", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_thread_local_get", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadLocalSetToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/ThreadLocal;Ljava/lang/Object;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_thread_local_set",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersThreadLocalRemoveToRuntimeHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/ThreadLocal;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/ThreadLocal", "remove", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_thread_local_remove",
                List.of(IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowerThreadLocalInstanceCallRejectsUnsupportedMethodShape() {
        assertThat(BytecodeToIRInvokeSupport.lowerThreadLocalInstanceCall(
            new MethodRef("java/lang/ThreadLocal", "initialValue", "()Ljava/lang/Object;"),
            new ArrayList<>(),
            new ArrayList<>(),
            new LinkedHashMap<>(),
            List.of(),
            IrExpression.objectLocal("arg0")
        )).isFalse();
    }

    @Test
    void lowerThreadLocalInstanceCallRejectsWrongOwner() {
        assertThat(BytecodeToIRInvokeSupport.lowerThreadLocalInstanceCall(
            new MethodRef("java/lang/Object", "get", "()Ljava/lang/Object;"),
            new ArrayList<>(),
            new ArrayList<>(),
            new LinkedHashMap<>(),
            List.of(),
            IrExpression.objectLocal("arg0")
        )).isFalse();
    }

    @Test
    void lowersThreadStartVirtualThreadStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_thread_new_virtual", List.of())),
            IrInstruction.callStaticVoid("javan_thread_set_name", List.of(IrExpression.objectLocal("object0"), IrExpression.objectNull())),
            IrInstruction.callStaticVoid("javan_thread_set_target", List.of(IrExpression.objectLocal("object0"), IrExpression.objectLocal("arg0"))),
            IrInstruction.callStaticVoid("javan_thread_start", List.of(IrExpression.objectLocal("object0"))),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void recognizesThreadOfVirtualBuilderUnstartedMethod() {
        assertThat(BytecodeToIRInvokeSupport.isVirtualThreadBuilderUnstarted(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isTrue();
    }

    @Test
    void rejectsNonUnstartedThreadOfVirtualBuilderMethod() {
        assertThat(BytecodeToIRInvokeSupport.isVirtualThreadBuilderUnstarted(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")
        )).isFalse();
    }

    @Test
    void rejectsWrongDescriptorForThreadOfVirtualBuilderUnstartedMethod() {
        assertThat(BytecodeToIRInvokeSupport.isVirtualThreadBuilderUnstarted(
            new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "()Ljava/lang/Thread;")
        )).isFalse();
    }

    @Test
    void lowersDiscardedThreadOfVirtualBuilderCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 87, "pop"),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_virtual_thread_builder_new", List.of()),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersDiscardedThreadOfVirtualNamedBuilderCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            stringConstant(1, "x"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(3, 87, "pop"),
            plain(4, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_virtual_thread_builder_name",
                List.of(
                    IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                    IrExpression.stringLiteral("x")
                )
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersDiscardedThreadOfVirtualFactoryCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(1, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plain(2, 87, "pop"),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_virtual_thread_builder_factory",
                List.of(IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderStartInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            1,
            1,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 42, "aload_0"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_start",
                    List.of(
                        IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                        IrExpression.objectLocal("arg0")
                    )
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderNameStartInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            1,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            stringConstant(1, "x"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(3, 42, "aload_0"),
            invokeInterface(4, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(5, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_start",
                    List.of(
                        IrExpression.objectCall(
                            "javan_virtual_thread_builder_name",
                            List.of(
                                IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                                IrExpression.stringLiteral("x")
                            )
                        ),
                        IrExpression.objectLocal("arg0")
                    )
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderUnstartedInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            1,
            1,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 42, "aload_0"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_unstarted",
                    List.of(
                        IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                        IrExpression.objectLocal("arg0")
                    )
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderUnstartedViaLocalAliasInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            2,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 76, "astore_1"),
            plain(2, 43, "aload_1"),
            plain(3, 42, "aload_0"),
            invokeInterface(4, new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(5, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("local1", IrExpression.objectCall("javan_virtual_thread_builder_new", List.of())),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_unstarted",
                    List.of(IrExpression.objectLocal("local1"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderNameUnstartedInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            1,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            stringConstant(1, "x"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(3, 42, "aload_0"),
            invokeInterface(4, new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(5, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_unstarted",
                    List.of(
                        IrExpression.objectCall(
                            "javan_virtual_thread_builder_name",
                            List.of(
                                IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                                IrExpression.stringLiteral("x")
                            )
                        ),
                        IrExpression.objectLocal("arg0")
                    )
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderFactoryNewThreadInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            1,
            1,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(1, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plain(2, 42, "aload_0"),
            invokeInterface(3, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_virtual_thread_factory_new_thread",
                    List.of(
                        IrExpression.objectCall(
                            "javan_virtual_thread_builder_factory",
                            List.of(IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()))
                        ),
                        IrExpression.objectLocal("arg0")
                    )
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderFactoryNewThreadViaLocalAliasInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            2,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(1, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plain(2, 76, "astore_1"),
            plain(3, 43, "aload_1"),
            plain(4, 42, "aload_0"),
            invokeInterface(5, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(6, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "local1",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_factory",
                    List.of(IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()))
                )
            ),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_factory_new_thread",
                    List.of(IrExpression.objectLocal("local1"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderNamedFactoryNewThreadViaLocalAliasInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            2,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            stringConstant(1, "x"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(3, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plain(4, 76, "astore_1"),
            plain(5, 43, "aload_1"),
            plain(6, 42, "aload_0"),
            invokeInterface(7, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(8, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "local1",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_factory",
                    List.of(
                        IrExpression.objectCall(
                            "javan_virtual_thread_builder_name",
                            List.of(
                                IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                                IrExpression.stringLiteral("x")
                            )
                        )
                    )
                )
            ),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_factory_new_thread",
                    List.of(IrExpression.objectLocal("local1"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderFactoryNewThreadViaLocalAliasSlotThreeInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            4,
            4,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(1, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plain(2, 78, "astore_3"),
            plain(3, 45, "aload_3"),
            plain(4, 42, "aload_0"),
            invokeInterface(5, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(6, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "local3",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_factory",
                    List.of(IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()))
                )
            ),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_factory_new_thread",
                    List.of(IrExpression.objectLocal("local3"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderFactoryNewThreadViaGenericLocalAliasInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            5,
            5,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            invokeInterface(1, new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
            plainOperands(2, 58, "astore", 4),
            plainOperands(4, 25, "aload", 4),
            plain(6, 42, "aload_0"),
            invokeInterface(7, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(8, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "local4",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_factory",
                    List.of(IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()))
                )
            ),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_factory_new_thread",
                    List.of(IrExpression.objectLocal("local4"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void rejectsThreadOfVirtualBuilderFactoryNewThreadWithoutFactoryReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/util/concurrent/ThreadFactory.newThread(Ljava/lang/Runnable;)Ljava/lang/Thread;");
                assertThat(exception.diagnostic().reason()).isEqualTo("A virtual-thread factory receiver was expected on the bytecode stack.");
            });
    }

    @Test
    void rejectsThreadOfVirtualBuilderFactoryNewThreadWithWrongReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            1,
            plain(0, 3, "iconst_0"),
            plain(1, 42, "aload_0"),
            invokeInterface(2, new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/util/concurrent/ThreadFactory.newThread(Ljava/lang/Runnable;)Ljava/lang/Thread;");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected virtual-thread factory receiver value on the bytecode stack, but found int.");
            });
    }

    @Test
    void lowersThreadOfVirtualBuilderStartViaLocalAliasInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            2,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 76, "astore_1"),
            plain(2, 43, "aload_1"),
            plain(3, 42, "aload_0"),
            invokeInterface(4, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(5, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("local1", IrExpression.objectCall("javan_virtual_thread_builder_new", List.of())),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_start",
                    List.of(IrExpression.objectLocal("local1"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void lowersThreadOfVirtualBuilderNameStartViaLocalAliasInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            2,
            2,
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            stringConstant(1, "x"),
            invokeInterface(2, new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(3, 76, "astore_1"),
            plain(4, 43, "aload_1"),
            plain(5, 42, "aload_0"),
            invokeInterface(6, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
            plain(7, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "local1",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_name",
                    List.of(
                        IrExpression.objectCall("javan_virtual_thread_builder_new", List.of()),
                        IrExpression.stringLiteral("x")
                    )
                )
            ),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_virtual_thread_builder_start",
                    List.of(IrExpression.objectLocal("local1"), IrExpression.objectLocal("arg0"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void inferVirtualThreadTargetReturnsEmptyForBuilderAliasSlotMismatch() {
        final List<Instruction> instructions = List.of(
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 76, "astore_1"),
            plain(2, 44, "aload_2"),
            classInstruction(3, 187, "new", "com/acme/Task"),
            plain(4, 89, "dup"),
            invokeSpecial(5, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(6, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Task",
            classFile(
                "com/acme/Task",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
            )
        );

        assertThat(BytecodeToIRInvokeSupport.inferVirtualThreadTarget(classes, instructions, 6)).isEmpty();
    }

    @Test
    void inferVirtualThreadTargetReturnsEmptyForThreadSubclassTask() {
        final List<Instruction> instructions = List.of(
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            classInstruction(1, 187, "new", "com/acme/WorkerThread"),
            plain(2, 89, "dup"),
            invokeSpecial(3, new MethodRef("com/acme/WorkerThread", "<init>", "()V")),
            invokeInterface(4, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/WorkerThread",
            classFile(
                "com/acme/WorkerThread",
                "java/lang/Thread",
                0,
                List.of(),
                List.of(),
                List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
            )
        );

        assertThat(BytecodeToIRInvokeSupport.inferVirtualThreadTarget(classes, instructions, 4)).isEmpty();
    }

    @Test
    void inferVirtualThreadTargetReturnsEmptyForObjectAliasCheckcastWithNonLoadReceiver() {
        final List<Instruction> instructions = List.of(
            invokeStatic(0, new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
            plain(1, 87, "pop"),
            plain(2, 3, "iconst_0"),
            classInstruction(3, 192, "checkcast", "java/lang/Thread$Builder$OfVirtual"),
            classInstruction(4, 187, "new", "com/acme/Task"),
            plain(5, 89, "dup"),
            invokeSpecial(6, new MethodRef("com/acme/Task", "<init>", "()V")),
            invokeInterface(7, new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
        );
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Task",
            classFile(
                "com/acme/Task",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
            )
        );

        assertThat(BytecodeToIRInvokeSupport.inferVirtualThreadTarget(classes, instructions, 7)).isEmpty();
    }

    @Test
    void lowersThreadInterruptedStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Z",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/Thread", "interrupted", "()Z")),
            plain(1, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_thread_interrupted", List.of()))
        );
    }

    @Test
    void lowersThreadSleepStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)V",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).hasSize(10);
        assertThat(function.instructions().get(0)).isEqualTo(IrInstruction.assignInt(
            "int0",
            IrExpression.intCall("javan_thread_interrupted", List.of())
        ));
        assertThat(function.instructions().get(1)).isEqualTo(IrInstruction.branchIf(
            "label_thread_wait_continue_1_0",
            IrExpression.intComparison("==", IrExpression.intLocal("int0"), IrExpression.intLiteral(0))
        ));
        assertThat(function.instructions().get(2)).isEqualTo(IrInstruction.jump("label_thread_wait_interrupted_1_0"));
        assertThat(function.instructions().get(3)).isEqualTo(IrInstruction.label("label_thread_wait_continue_1_0"));
        assertThat(function.instructions().get(4)).isEqualTo(
            IrInstruction.assignInt("int1", IrExpression.intCall("javan_thread_sleep_millis_interruptible", List.of(IrExpression.longLocal("arg0"))))
        );
        assertThat(function.instructions().get(5)).isEqualTo(IrInstruction.branchIf(
            "label_thread_wait_success_1_1",
            IrExpression.intComparison("==", IrExpression.intLocal("int1"), IrExpression.intLiteral(0))
        ));
        assertThat(function.instructions().get(6)).isEqualTo(IrInstruction.label("label_thread_wait_interrupted_1_0"));
        assertThat(function.instructions().get(7)).satisfies(instruction -> {
            assertThat(instruction.op()).isEqualTo(IrInstruction.Op.PANIC);
            assertThat(instruction.expression()).contains(IrExpression.stringLiteral("java/lang/InterruptedException"));
        });
        assertThat(function.instructions().get(8)).isEqualTo(IrInstruction.label("label_thread_wait_success_1_1"));
        assertThat(function.instructions().get(9)).isEqualTo(IrInstruction.returnVoid());
    }

    @Test
    void lowersThreadInterruptInstanceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "interrupt", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_interrupt", List.of(IrExpression.objectLocal("arg0"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersThreadIsInterruptedInstanceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "isInterrupted", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_thread_is_interrupted", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersThreadIsAliveInstanceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "isAlive", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_thread_is_alive", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersThreadIsVirtualInstanceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "isVirtual", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_thread_is_virtual", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersThreadGetNameInstanceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "getName", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_thread_get_name", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersThreadJoinInstanceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "join", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).hasSize(7);
        assertThat(function.instructions().get(0)).isEqualTo(IrInstruction.label("label_thread_wait_continue_1_0"));
        assertThat(function.instructions().get(1)).isEqualTo(
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_thread_join_interruptible", List.of(IrExpression.objectLocal("arg0"))))
        );
        assertThat(function.instructions().get(2)).isEqualTo(IrInstruction.branchIf(
            "label_thread_wait_success_1_0",
            IrExpression.intComparison("==", IrExpression.intLocal("int0"), IrExpression.intLiteral(0))
        ));
        assertThat(function.instructions().get(3)).isEqualTo(IrInstruction.label("label_thread_wait_interrupted_1_0"));
        assertThat(function.instructions().get(4)).satisfies(instruction -> {
            assertThat(instruction.op()).isEqualTo(IrInstruction.Op.PANIC);
            assertThat(instruction.expression()).contains(IrExpression.stringLiteral("java/lang/InterruptedException"));
        });
        assertThat(function.instructions().get(5)).isEqualTo(IrInstruction.label("label_thread_wait_success_1_0"));
        assertThat(function.instructions().get(6)).isEqualTo(IrInstruction.returnVoid());
    }

    @Test
    void lowersInterruptedThreadSleepToSameMethodCatchHandler() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            2,
            1,
            List.of(new CodeException(0, 5, 6, Optional.of("java/lang/InterruptedException"))),
            invokeStatic(0, new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "interrupt", "()V")),
            plain(2, 10, "lconst_1"),
            invokeStatic(3, new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            stringConstant(4, "ok"),
            plain(5, 176, "areturn"),
            plain(6, 75, "astore_0"),
            plain(7, 42, "aload_0"),
            invokeVirtual(8, new MethodRef("java/lang/InterruptedException", "getMessage", "()Ljava/lang/String;")),
            plain(9, 176, "areturn")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_interrupt", List.of(IrExpression.objectCall("javan_thread_current", List.of()))),
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_thread_interrupted", List.of())),
            IrInstruction.branchIf(
                "label_thread_wait_continue_3_0",
                IrExpression.intComparison("==", IrExpression.intLocal("int0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.jump("label_thread_wait_interrupted_3_0"),
            IrInstruction.label("label_thread_wait_continue_3_0"),
            IrInstruction.assignInt("int1", IrExpression.intCall("javan_thread_sleep_millis_interruptible", List.of(IrExpression.longLiteral(1L)))),
            IrInstruction.branchIf(
                "label_thread_wait_success_3_1",
                IrExpression.intComparison("==", IrExpression.intLocal("int1"), IrExpression.intLiteral(0))
            ),
            IrInstruction.label("label_thread_wait_interrupted_3_0"),
            IrInstruction.jump("label_6"),
            IrInstruction.label("label_thread_wait_success_3_1"),
            IrInstruction.returnObject(IrExpression.stringLiteral("ok")),
            IrInstruction.label("label_6"),
            IrInstruction.assignObject("local0_object_2", IrExpression.stringLiteral("sleep interrupted")),
            IrInstruction.returnObject(IrExpression.objectLocal("local0_object_2"))
        );
    }

    @Test
    void lowersInterruptedThreadJoinToSameMethodCatchHandler() {
        final MethodInfo main = methodWithHandlers(
            0x0008,
            "main",
            "(Ljava/lang/Thread;)Ljava/lang/String;",
            1,
            2,
            List.of(new CodeException(0, 5, 6, Optional.of("java/lang/InterruptedException"))),
            invokeStatic(0, new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
            invokeVirtual(1, new MethodRef("java/lang/Thread", "interrupt", "()V")),
            plain(2, 42, "aload_0"),
            invokeVirtual(3, new MethodRef("java/lang/Thread", "join", "()V")),
            stringConstant(4, "ok"),
            plain(5, 176, "areturn"),
            plain(6, 76, "astore_1"),
            plain(7, 43, "aload_1"),
            invokeVirtual(8, new MethodRef("java/lang/InterruptedException", "getMessage", "()Ljava/lang/String;")),
            plain(9, 176, "areturn")
        );

        final IrFunction function = lowerMain(main);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_thread_interrupt", List.of(IrExpression.objectCall("javan_thread_current", List.of()))),
            IrInstruction.label("label_thread_wait_continue_3_0"),
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_thread_join_interruptible", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.branchIf(
                "label_thread_wait_success_3_0",
                IrExpression.intComparison("==", IrExpression.intLocal("int0"), IrExpression.intLiteral(0))
            ),
            IrInstruction.label("label_thread_wait_interrupted_3_0"),
            IrInstruction.jump("label_6"),
            IrInstruction.label("label_thread_wait_success_3_0"),
            IrInstruction.returnObject(IrExpression.stringLiteral("ok")),
            IrInstruction.label("label_6"),
            IrInstruction.assignObject("local1_object_1", IrExpression.objectNull()),
            IrInstruction.returnObject(IrExpression.objectLocal("local1_object_1"))
        );
    }

    @Test
    void lowersInetAddressLoopbackStaticCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/net/InetAddress;",
            1,
            0,
            invokeStatic(0, new MethodRef("java/net/InetAddress", "getLoopbackAddress", "()Ljava/net/InetAddress;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_inet_address_loopback", List.of()))
        );
    }

    @Test
    void lowersInetAddressHostNameCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/InetAddress;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/InetAddress", "getHostName", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_inet_address_get_host_name", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersInetAddressCanonicalHostNameCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/InetAddress;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/InetAddress", "getCanonicalHostName", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_inet_address_get_canonical_host_name", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersSocketGetLocalPortCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "getLocalPort", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt("int0", IrExpression.intCall("javan_socket_get_local_port", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersSocketCloseCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "close", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_socket_close", List.of(IrExpression.objectLocal("arg0"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersServerSocketCloseCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/ServerSocket;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/ServerSocket", "close", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_server_socket_close", List.of(IrExpression.objectLocal("arg0"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSocketInputStreamCloseCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;)V",
            1,
            2,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "getInputStream", "()Ljava/io/InputStream;")),
            plain(2, 76, "astore_1"),
            plain(3, 43, "aload_1"),
            invokeVirtual(4, new MethodRef("java/io/InputStream", "close", "()V")),
            plain(5, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_socket_input_stream", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.assignObject("local1_object_1", IrExpression.objectLocal("object0")),
            IrInstruction.callStaticVoid("javan_socket_input_stream_close", List.of(IrExpression.objectLocal("local1_object_1"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersSocketOutputStreamWriteRangeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;[BII)V",
            4,
            5,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "getOutputStream", "()Ljava/io/OutputStream;")),
            plainOperands(2, 58, "astore", 4),
            plainOperands(3, 25, "aload", 4),
            plain(5, 43, "aload_1"),
            plain(6, 28, "iload_2"),
            plain(7, 29, "iload_3"),
            invokeVirtual(8, new MethodRef("java/io/OutputStream", "write", "([BII)V")),
            plain(9, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_socket_output_stream", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.assignObject("local4_object_1", IrExpression.objectLocal("object0")),
            IrInstruction.callStaticVoid(
                "javan_socket_output_stream_write_bytes_range",
                List.of(
                    IrExpression.objectLocal("local4_object_1"),
                    IrExpression.objectLocal("arg1"),
                    IrExpression.intLocal("arg2"),
                    IrExpression.intLocal("arg3")
                )
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void inferRunnableThreadTargetRejectsTooSmallInstructionPrefix() {
        final Optional<EntryPoint> target = BytecodeToIRInvokeSupport.inferRunnableThreadTarget(
            Map.of(),
            List.of(invokeSpecial(0, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V"))),
            0
        );

        assertThat(target).isEmpty();
    }

    @Test
    void inferRunnableThreadTargetRejectsMissingDupBeforeRunnableConstruction() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Task", classFile(
                "com/acme/Task",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(
                    method(0, "<init>", "()V", 0, 0, plain(0, 177, "return")),
                    method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
                )
            )
        );

        final Optional<EntryPoint> target = BytecodeToIRInvokeSupport.inferRunnableThreadTarget(
            classes,
            List.of(
                classInstruction(0, 187, "new", "java/lang/Thread"),
                plain(1, 89, "dup"),
                classInstruction(2, 187, "new", "com/acme/Task"),
                plain(3, 42, "aload_0"),
                invokeSpecial(4, new MethodRef("com/acme/Task", "<init>", "()V")),
                invokeSpecial(5, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V"))
            ),
            5
        );

        assertThat(target).isEmpty();
    }

    @Test
    void inferRunnableThreadTargetRejectsMismatchedAllocationOwner() {
        final Map<String, ClassFile> classes = Map.ofEntries(
            Map.entry("com/acme/TaskA", classFile(
                "com/acme/TaskA",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(
                    method(0, "<init>", "()V", 0, 0, plain(0, 177, "return")),
                    method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
                )
            )),
            Map.entry("com/acme/TaskB", classFile(
                "com/acme/TaskB",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(
                    method(0, "<init>", "()V", 0, 0, plain(0, 177, "return")),
                    method(0, "run", "()V", 0, 0, plain(0, 177, "return"))
                )
            ))
        );

        final Optional<EntryPoint> target = BytecodeToIRInvokeSupport.inferRunnableThreadTarget(
            classes,
            List.of(
                classInstruction(0, 187, "new", "java/lang/Thread"),
                plain(1, 89, "dup"),
                classInstruction(2, 187, "new", "com/acme/TaskA"),
                plain(3, 89, "dup"),
                invokeSpecial(4, new MethodRef("com/acme/TaskB", "<init>", "()V")),
                invokeSpecial(5, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V"))
            ),
            5
        );

        assertThat(target).isEmpty();
    }

    @Test
    void containsReachableThreadStartRequiresExactSignature() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main", classFile(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                List.of(),
                List.of(method(
                    0x0008,
                    "main",
                    "()V",
                    1,
                    0,
                    invokeVirtual(0, new MethodRef("java/lang/Thread", "start", "(I)V")),
                    plain(1, 177, "return")
                ))
            )
        );

        assertThat(BytecodeToIRInvokeSupport.containsReachableThreadStart(
            classes,
            List.of(new EntryPoint("com/acme/Main", "main", "()V"))
        )).isFalse();
    }

    @Test
    void allRunnableThreadTargetsSkipsThreadSubclassesAndCodeLessTargets() {
        final Map<String, ClassFile> classes = Map.ofEntries(
            Map.entry("com/acme/Task", classFile(
                "com/acme/Task",
                "java/lang/Object",
                0,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
            )),
            Map.entry("com/acme/AbstractTask", classFile(
                "com/acme/AbstractTask",
                "java/lang/Object",
                0x0400,
                List.of("java/lang/Runnable"),
                List.of(),
                List.of(new MethodInfo(0x0401, "run", "()V", Optional.empty()))
            )),
            Map.entry("com/acme/WorkerThread", classFile(
                "com/acme/WorkerThread",
                "java/lang/Thread",
                0,
                List.of(),
                List.of(),
                List.of(method(0, "run", "()V", 0, 0, plain(0, 177, "return")))
            ))
        );

        assertThat(BytecodeToIRInvokeSupport.allRunnableThreadTargets(classes))
            .containsExactly(new EntryPoint("com/acme/Task", "run", "()V"));
    }

    @Test
    void lowerThreadStaticCallReturnsFalseForUnsupportedMethod() {
        final MethodRef methodRef = new MethodRef("java/lang/Thread", "yield", "()V");
        final boolean lowered = BytecodeToIRInvokeSupport.lowerThreadStaticCall(
            sinkClass(),
            method(0x0008, "main", "()V", 0, 0, plain(0, 177, "return")),
            invokeStatic(0, methodRef),
            methodRef,
            new ArrayList<>(),
            new ArrayList<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            SourceLineIndex.empty()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerInetAddressIntrinsicReturnsFalseForUnsupportedMethod() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerInetAddressIntrinsic(
            new MethodRef("java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;"),
            new ArrayList<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerJdkThreadInstanceCallReturnsFalseForUnsupportedOwner() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkThreadInstanceCall(
            Map.of(),
            sinkClass(),
            method(0x0008, "main", "(Ljava/lang/Object;)V", 1, 1, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/lang/Object", "toString", "()Ljava/lang/String;")),
            new MethodRef("java/lang/Object", "toString", "()Ljava/lang/String;"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            SourceLineIndex.empty()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerJdkThreadInstanceCallReturnsFalseForUnsupportedThreadMethod() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkThreadInstanceCall(
            Map.of(),
            sinkClass(),
            method(0x0008, "main", "(Ljava/lang/Thread;)V", 1, 1, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/lang/Thread", "yield", "()V")),
            new MethodRef("java/lang/Thread", "yield", "()V"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            SourceLineIndex.empty()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void containsReachableThreadStartSkipsReachableMethodsWithoutCode() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", "java/lang/Object", 0, List.of(), List.of(), List.of(new MethodInfo(0x0008, "main", "()V", Optional.empty())))
        );

        assertThat(BytecodeToIRInvokeSupport.containsReachableThreadStart(
            classes,
            List.of(new EntryPoint("com/acme/Main", "main", "()V"))
        )).isFalse();
    }

    @Test
    void runnableThreadTargetsReturnEmptyWithoutReachableThreadStart() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                List.of(),
                List.of(method(
                    0x0008,
                    "main",
                    "()V",
                    3,
                    0,
                    classInstruction(0, 187, "new", "java/lang/Thread"),
                    plain(1, 89, "dup"),
                    classInstruction(2, 187, "new", "com/acme/Task"),
                    plain(3, 89, "dup"),
                    invokeSpecial(4, new MethodRef("com/acme/Task", "<init>", "()V")),
                    invokeSpecial(5, new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                    plain(6, 177, "return")
                ))
            )
        );

        assertThat(BytecodeToIRInvokeSupport.runnableThreadTargets(
            classes,
            List.of(new EntryPoint("com/acme/Main", "main", "()V"))
        )).isEmpty();
    }

    @Test
    void lowerThreadStaticCallReturnsFalseForMatchingNameWrongDescriptor() {
        final List<BytecodeToIR.StackValue> stack = new ArrayList<>();
        stack.add(BytecodeToIR.StackValue.longExpression(IrExpression.longLiteral(1L)));
        final MethodRef methodRef = new MethodRef("java/lang/Thread", "sleep", "(I)V");

        final boolean lowered = BytecodeToIRInvokeSupport.lowerThreadStaticCall(
            sinkClass(),
            method(0x0008, "main", "(J)V", 2, 1, plain(0, 177, "return")),
            invokeStatic(0, methodRef),
            methodRef,
            new ArrayList<>(),
            stack,
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            SourceLineIndex.empty()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerJdkThreadInstanceCallReturnsFalseForMatchingNameWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkThreadInstanceCall(
            Map.of(),
            sinkClass(),
            method(0x0008, "main", "(Ljava/lang/Thread;)V", 1, 1, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/lang/Thread", "join", "(J)V")),
            new MethodRef("java/lang/Thread", "join", "(J)V"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            SourceLineIndex.empty()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerJdkThreadInstanceCallReturnsFalseForThreadIsAliveWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkThreadInstanceCall(
            Map.of(),
            sinkClass(),
            method(0x0008, "main", "(Ljava/lang/Thread;)V", 1, 1, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/lang/Thread", "isAlive", "(I)Z")),
            new MethodRef("java/lang/Thread", "isAlive", "(I)Z"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            SourceLineIndex.empty()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerIntegerIntrinsicReturnsFalseForMatchingNameWrongDescriptor() {
        final List<BytecodeToIR.StackValue> stack = new ArrayList<>();
        stack.add(BytecodeToIR.StackValue.intExpression(IrExpression.intLiteral(1)));

        final boolean lowered = BytecodeToIRInvokeSupport.lowerIntegerIntrinsic(
            sinkClass(),
            method(0x0008, "main", "(I)V", 1, 1, plain(0, 177, "return")),
            new MethodRef("java/lang/Integer", "valueOf", "(J)Ljava/lang/Integer;"),
            stack
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerLongIntrinsicReturnsFalseForMatchingNameWrongDescriptor() {
        final List<BytecodeToIR.StackValue> stack = new ArrayList<>();
        stack.add(BytecodeToIR.StackValue.longExpression(IrExpression.longLiteral(1L)));

        final boolean lowered = BytecodeToIRInvokeSupport.lowerLongIntrinsic(
            sinkClass(),
            method(0x0008, "main", "(J)V", 2, 1, plain(0, 177, "return")),
            new MethodRef("java/lang/Long", "valueOf", "(I)Ljava/lang/Long;"),
            stack
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerDurationIntrinsicReturnsFalseForMatchingNameWrongDescriptor() {
        final List<BytecodeToIR.StackValue> stack = new ArrayList<>();
        stack.add(BytecodeToIR.StackValue.longExpression(IrExpression.longLiteral(1L)));

        final boolean lowered = BytecodeToIRInvokeSupport.lowerDurationIntrinsic(
            sinkClass(),
            method(0x0008, "main", "(J)V", 2, 1, plain(0, 177, "return")),
            new MethodRef("java/time/Duration", "ofSeconds", "(I)Ljava/time/Duration;"),
            stack
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerJdkNetworkInstanceCallReturnsFalseForInetAddressMatchingNameWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkNetworkInstanceCall(
            sinkClass(),
            method(0x0008, "main", "(Ljava/net/InetAddress;)V", 1, 1, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/net/InetAddress", "getCanonicalHostName", "(I)Ljava/lang/String;")),
            new MethodRef("java/net/InetAddress", "getCanonicalHostName", "(I)Ljava/lang/String;"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerSocketStreamCallReturnsFalseForInputStreamCloseWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerSocketStreamCall(
            sinkClass(),
            method(0x0008, "main", "(Ljava/io/InputStream;I)V", 2, 2, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/io/InputStream", "close", "(I)V")),
            new MethodRef("java/io/InputStream", "close", "(I)V"),
            new ArrayList<>(),
            new ArrayList<>(List.of(
                BytecodeToIR.StackValue.socketInputStream(IrExpression.objectLocal("arg0")),
                BytecodeToIR.StackValue.intExpression(IrExpression.intLocal("arg1"))
            )),
            new LinkedHashMap<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerSocketStreamCallReturnsFalseForOutputStreamCloseWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerSocketStreamCall(
            sinkClass(),
            method(0x0008, "main", "(Ljava/io/OutputStream;I)V", 2, 2, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/io/OutputStream", "close", "(I)V")),
            new MethodRef("java/io/OutputStream", "close", "(I)V"),
            new ArrayList<>(),
            new ArrayList<>(List.of(
                BytecodeToIR.StackValue.socketOutputStream(IrExpression.objectLocal("arg0")),
                BytecodeToIR.StackValue.intExpression(IrExpression.intLocal("arg1"))
            )),
            new LinkedHashMap<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowersHttpResponseBodyInterfaceCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/http/HttpResponse;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/net/http/HttpResponse", "body", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_http_response_body", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowerJdkHttpInterfaceCallReturnsFalseForHttpResponseBodyWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkHttpInterfaceCall(
            sinkClass(),
            method(0x0008, "main", "(Ljava/net/http/HttpResponse;)V", 1, 1, plain(0, 177, "return")),
            invokeInterface(0, new MethodRef("java/net/http/HttpResponse", "body", "()Ljava/lang/String;")),
            new MethodRef("java/net/http/HttpResponse", "body", "()Ljava/lang/String;"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowersInetSocketAddressToStringCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/InetSocketAddress;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/InetSocketAddress", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_inet_socket_address_to_string", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowerJdkNetworkInstanceCallReturnsFalseForInetSocketAddressToStringWrongDescriptor() {
        final boolean lowered = BytecodeToIRInvokeSupport.lowerJdkNetworkInstanceCall(
            sinkClass(),
            method(0x0008, "main", "(Ljava/net/InetSocketAddress;)V", 1, 1, plain(0, 177, "return")),
            invokeVirtual(0, new MethodRef("java/net/InetSocketAddress", "toString", "(I)Ljava/lang/String;")),
            new MethodRef("java/net/InetSocketAddress", "toString", "(I)Ljava/lang/String;"),
            new ArrayList<>(),
            new ArrayList<>(List.of(BytecodeToIR.StackValue.objectExpression(IrExpression.objectLocal("arg0")))),
            new LinkedHashMap<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowerEnumValuesReturnsFalseForMatchingNameWrongDescriptor() {
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Mode",
            classFile(
                "com/acme/Mode",
                "java/lang/Enum",
                0x4000,
                List.of(),
                List.of(),
                List.of()
            )
        );

        final boolean lowered = BytecodeToIRInvokeSupport.lowerEnumValues(
            classes,
            sinkClass(),
            method(0x0008, "main", "()V", 0, 0, plain(0, 177, "return")),
            new MethodRef("com/acme/Mode", "values", "()[Ljava/lang/Object;"),
            new ArrayList<>(),
            new ArrayList<>(),
            new LinkedHashMap<>()
        );

        assertThat(lowered).isFalse();
    }

    @Test
    void lowersMultipleTargetInterfaceIntReturnToDispatchCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()I")),
            plain(2, 172, "ireturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()I"),
            implementationType("com/acme/AMetric", "com/acme/Metric", "value", "()I"),
            implementationType("com/acme/BMetric", "com/acme/Metric", "value", "()I")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_dispatch_com_acme_Metric_value___I",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersMultipleTargetInterfaceLongReturnToDispatchCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()J")),
            plain(2, 173, "lreturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()J"),
            implementationType("com/acme/AMetric", "com/acme/Metric", "value", "()J"),
            implementationType("com/acme/BMetric", "com/acme/Metric", "value", "()J")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall(
                "javan_dispatch_com_acme_Metric_value___J",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersMultipleTargetInterfaceFloatReturnToDispatchCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)F",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()F")),
            plain(2, 174, "freturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()F"),
            implementationType("com/acme/AMetric", "com/acme/Metric", "value", "()F"),
            implementationType("com/acme/BMetric", "com/acme/Metric", "value", "()F")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnFloat(IrExpression.floatCall(
                "javan_dispatch_com_acme_Metric_value___F",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersMultipleTargetInterfaceDoubleReturnToDispatchCall() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Lcom/acme/Metric;)D",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("com/acme/Metric", "value", "()D")),
            plain(2, 175, "dreturn")
        );

        final IrFunction function = lower(
            main,
            interfaceType("com/acme/Metric", "value", "()D"),
            implementationType("com/acme/AMetric", "com/acme/Metric", "value", "()D"),
            implementationType("com/acme/BMetric", "com/acme/Metric", "value", "()D")
        );

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnDouble(IrExpression.doubleCall(
                "javan_dispatch_com_acme_Metric_value___D",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersProcessRunnerRunToNativeProcessCallAndResultRecordMapping() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljavan/util/ProcessRunner;Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef(
                "javan/util/ProcessRunner",
                "run",
                "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;"
            )),
            plain(4, 176, "areturn")
        );
        final ClassFile resultClass = classFile(
            "javan/util/ProcessRunner$Result",
            "java/lang/Object",
            0,
            List.of(),
            List.of(
                new FieldInfo(0, "exitCode", "I"),
                new FieldInfo(0, "stdout", "Ljava/lang/String;"),
                new FieldInfo(0, "stderr", "Ljava/lang/String;")
            ),
            List.of()
        );

        final IrFunction function = lower(main, resultClass);

        assertThat(function.locals()).containsExactly(
            new IrLocal(javan.ir.IrType.OBJECT, "object0"),
            new IrLocal(javan.ir.IrType.OBJECT, "object1")
        );
        assertThat(function.instructions()).contains(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_process_run",
                    List.of(
                        IrExpression.objectLocal("arg1"),
                        IrExpression.objectLocal("arg2"),
                        new IrExpression(
                            IrExpression.Kind.FIELD_LONG,
                            javan.ir.IrType.LONG,
                            "javan/util/ProcessRunner#timeoutMillis",
                            List.of(IrExpression.objectLocal("arg0"))
                        )
                    )
                )
            ),
            IrInstruction.assignObject("object1", IrExpression.objectAllocation("javan/util/ProcessRunner$Result")),
            IrInstruction.assignFieldInt(
                "javan/util/ProcessRunner$Result",
                "exitCode",
                IrExpression.objectLocal("object1"),
                IrExpression.intCall("javan_process_result_exit_code", List.of(IrExpression.objectLocal("object0")))
            ),
            IrInstruction.assignFieldObject(
                "javan/util/ProcessRunner$Result",
                "stdout",
                IrExpression.objectLocal("object1"),
                IrExpression.objectCall("javan_process_result_stdout", List.of(IrExpression.objectLocal("object0")))
            ),
            IrInstruction.assignFieldObject(
                "javan/util/ProcessRunner$Result",
                "stderr",
                IrExpression.objectLocal("object1"),
                IrExpression.objectCall("javan_process_result_stderr", List.of(IrExpression.objectLocal("object0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object1"))
        );
    }

    @Test
    void rejectsProcessRunnerRunWhenResultClassIsMissing() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "(Ljavan/util/ProcessRunner;Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef(
                "javan/util/ProcessRunner",
                "run",
                "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;"
            )),
            plain(4, 176, "areturn")
        );
        final ClassFile processRunner = classFile(
            "javan/util/ProcessRunner",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0, "timeoutMillis", "J")),
            List.of()
        );

        assertThatThrownBy(() -> lower(main, processRunner))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN048");
                assertThat(exception.diagnostic().message()).isEqualTo("javan process substitution cannot allocate result");
                assertThat(exception.diagnostic().subject()).isEqualTo(
                    "javan/util/ProcessRunner.run(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;"
                );
                assertThat(exception.diagnostic().reason()).isEqualTo(
                    "The native process substitution requires javan.util.ProcessRunner.Result in the closed world."
                );
            });
    }

    @Test
    void lowersStringSubstringRangeToRuntimeHelperWithTemporaryLocal() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            3,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 4, "iconst_1"),
            plain(2, 5, "iconst_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "substring", "(II)Ljava/lang/String;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(javan.ir.IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_string_substring_range",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(2))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringLengthToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "length", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_length",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersStringIsEmptyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "isEmpty", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_is_empty",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersStringCharAtToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;I)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "charAt", "(I)C")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_char_at",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringCharAtWithLongIndexDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;J)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 31, "lload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "charAt", "(J)C")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.charAt(J)C");
            });
    }

    @Test
    void lowersStringIndexOfCharToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;I)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "indexOf", "(I)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_index_of_char",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringIndexOfCharFromIndexToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;II)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "indexOf", "(II)I")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_index_of_char_from",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
            ))
        );
    }

    @Test
    void lowersStringIndexOfStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_index_of_string",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringIndexOfStringFromIndexToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;I)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;I)I")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_index_of_string_from",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.intLocal("arg2"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringIndexOfStringWithLongFromIndexDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;J)I",
            4,
            4,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 32, "lload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;J)I")),
            plain(4, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.indexOf(Ljava/lang/String;J)I");
            });
    }

    @Test
    void lowersStringLastIndexOfCharToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;I)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "lastIndexOf", "(I)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_last_index_of_char",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringLastIndexOfCharFromIndexToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;II)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "lastIndexOf", "(II)I")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_last_index_of_char_from",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
            ))
        );
    }

    @Test
    void lowersStringLastIndexOfStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_last_index_of_string",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringLastIndexOfStringFromIndexToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;I)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "lastIndexOf", "(Ljava/lang/String;I)I")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_last_index_of_string_from",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.intLocal("arg2"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringLastIndexOfStringWithLongFromIndexDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;J)I",
            4,
            4,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 32, "lload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "lastIndexOf", "(Ljava/lang/String;J)I")),
            plain(4, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.lastIndexOf(Ljava/lang/String;J)I");
            });
    }

    @Test
    void lowersStringEqualsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "equals", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_equals",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringEqualsWithStringDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "equals", "(Ljava/lang/String;)Z")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.equals(Ljava/lang/String;)Z");
            });
    }

    @Test
    void lowersStringContainsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_contains",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringContainsWithStringDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "contains", "(Ljava/lang/String;)Z")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.contains(Ljava/lang/String;)Z");
            });
    }

    @Test
    void lowersStringStartsWithToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "startsWith", "(Ljava/lang/String;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_starts_with",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringStartsWithOffsetToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;I)Z",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "startsWith", "(Ljava/lang/String;I)Z")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_starts_with_from",
                List.of(
                    IrExpression.objectLocal("arg0"),
                    IrExpression.objectLocal("arg1"),
                    IrExpression.intLocal("arg2")
                )
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringStartsWithOffsetLongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;J)Z",
            4,
            4,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 32, "lload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "startsWith", "(Ljava/lang/String;J)Z")),
            plain(4, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.startsWith(Ljava/lang/String;J)Z");
            });
    }

    @Test
    void rejectsUnsupportedStringLengthWithBooleanDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "length", "()Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.length()Z");
            });
    }

    @Test
    void rejectsUnsupportedStringIsEmptyWithIntDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "isEmpty", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.isEmpty()I");
            });
    }

    @Test
    void lowersStringEndsWithToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "endsWith", "(Ljava/lang/String;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_string_ends_with",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedStringEndsWithCharSequenceDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "endsWith", "(Ljava/lang/CharSequence;)Z")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.endsWith(Ljava/lang/CharSequence;)Z");
            });
    }

    @Test
    void lowersStringReplaceCharToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;II)Ljava/lang/String;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "replace", "(CC)Ljava/lang/String;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_string_replace_char",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedStringReplaceSequenceDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")),
            plain(4, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo(
                    "invokevirtual java/lang/String.replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"
                );
            });
    }

    @Test
    void lowersStringInternToIdentityReturn() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "intern", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectLocal("arg0"))
        );
    }

    @Test
    void rejectsUnsupportedStringInternWithBooleanDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "intern", "()Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.intern()Z");
            });
    }

    @Test
    void lowersStringTrimToRuntimeHelperWithTemporaryLocal() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/String", "trim", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_string_trim", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedStringTrimWithIntDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;I)Ljava/lang/String;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "trim", "(I)Ljava/lang/String;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.trim(I)Ljava/lang/String;");
            });
    }

    @Test
    void lowersStringSubstringFromBeginToRuntimeHelperWithTemporaryLocal() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            2,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 4, "iconst_1"),
            invokeVirtual(2, new MethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_string_substring",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedStringSubstringRangeWithLongEndDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;IJ)Ljava/lang/String;",
            4,
            4,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 32, "lload_2"),
            invokeVirtual(3, new MethodRef("java/lang/String", "substring", "(IJ)Ljava/lang/String;")),
            plain(4, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/String.substring(IJ)Ljava/lang/String;");
            });
    }

    @Test
    void lowersIntegerIntValueToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Integer;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Integer", "intValue", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_integer_int_value",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsMalformedIntegerIntValueDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Integer;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Integer", "intValue", "()J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Integer.intValue()J");
            });
    }

    @Test
    void lowersBooleanBooleanValueToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Boolean;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Boolean", "booleanValue", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_boolean_boolean_value",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsMalformedBooleanBooleanValueDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Boolean;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Boolean", "booleanValue", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Boolean.booleanValue()Ljava/lang/String;");
            });
    }

    @Test
    void lowersLongLongValueToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Long;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Long", "longValue", "()J")),
            plain(2, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall(
                "javan_long_long_value",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsMalformedLongLongValueDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Long;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Long", "longValue", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Long.longValue()I");
            });
    }

    @Test
    void lowersFloatFloatValueToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Float;)F",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Float", "floatValue", "()F")),
            plain(2, 174, "freturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnFloat(IrExpression.floatCall(
                "javan_float_float_value",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsMalformedFloatFloatValueDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Float;)D",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Float", "floatValue", "()D")),
            plain(2, 175, "dreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Float.floatValue()D");
            });
    }

    @Test
    void lowersDoubleDoubleValueToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Double;)D",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Double", "doubleValue", "()D")),
            plain(2, 175, "dreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnDouble(IrExpression.doubleCall(
                "javan_double_double_value",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsMalformedDoubleDoubleValueDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Double;)F",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Double", "doubleValue", "()F")),
            plain(2, 174, "freturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Double.doubleValue()F");
            });
    }

    @Test
    void rejectsUnsupportedIntegerToStringInstanceCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Integer;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Integer", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Integer.toString()Ljava/lang/String;");
            });
    }

    @Test
    void rejectsUnsupportedLongToStringInstanceCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Long;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Long", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Long.toString()Ljava/lang/String;");
            });
    }

    @Test
    void rejectsUnsupportedFloatToStringInstanceCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Float;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Float", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Float.toString()Ljava/lang/String;");
            });
    }

    @Test
    void rejectsUnsupportedDoubleToStringInstanceCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Double;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Double", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Double.toString()Ljava/lang/String;");
            });
    }

    @Test
    void rejectsUnsupportedBooleanToStringInstanceCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Boolean;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/Boolean", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/Boolean.toString()Ljava/lang/String;");
            });
    }

    @Test
    void lowersEnumOrdinalForConstantFieldToLiteral() {
        final ClassFile mode = classFile(
            "com/acme/Mode",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(
                new FieldInfo(0x4008, "FIRST", "Lcom/acme/Mode;"),
                new FieldInfo(0x4008, "SECOND", "Lcom/acme/Mode;")
            ),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("com/acme/Mode", "SECOND", "Lcom/acme/Mode;")),
            invokeVirtual(1, new MethodRef("com/acme/Mode", "ordinal", "()I")),
            plain(2, 172, "ireturn")
        ), mode);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void lowersEnumValuesToCanonicalStaticFieldArray() {
        final ClassFile mode = classFile(
            "com/acme/Mode",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(
                new FieldInfo(0x4008, "FIRST", "Lcom/acme/Mode;"),
                new FieldInfo(0x4008, "SECOND", "Lcom/acme/Mode;")
            ),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()[Lcom/acme/Mode;",
            1,
            0,
            invokeStatic(0, new MethodRef("com/acme/Mode", "values", "()[Lcom/acme/Mode;")),
            plain(1, 176, "areturn")
        ), mode);

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectArrayAllocation(IrExpression.intLiteral(2))),
            IrInstruction.assignArrayObject(IrExpression.objectLocal("object0"), IrExpression.intLiteral(0), IrExpression.objectStaticField("com/acme/Mode", "FIRST")),
            IrInstruction.assignArrayObject(IrExpression.objectLocal("object0"), IrExpression.intLiteral(1), IrExpression.objectStaticField("com/acme/Mode", "SECOND")),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersEnumToStringIntrinsicToIdentityReturn() {
        final ClassFile mode = classFile(
            "com/acme/Mode",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(new FieldInfo(0x4008, "READY", "Lcom/acme/Mode;")),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            getStatic(0, new FieldRef("com/acme/Mode", "READY", "Lcom/acme/Mode;")),
            invokeVirtual(1, new MethodRef("com/acme/Mode", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ), mode);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectStaticField("com/acme/Mode", "READY"))
        );
    }

    @Test
    void lowersEnumConstantStaticFieldToCanonicalStaticField() {
        final ClassFile mode = classFile(
            "com/acme/Mode",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(new FieldInfo(0x4008, "READY", "Lcom/acme/Mode;")),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()Lcom/acme/Mode;",
            1,
            0,
            getStatic(0, new FieldRef("com/acme/Mode", "READY", "Lcom/acme/Mode;")),
            plain(1, 176, "areturn")
        ), mode);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectStaticField("com/acme/Mode", "READY"))
        );
    }

    @Test
    void lowersEnumNameIntrinsicToIdentityReturn() {
        final ClassFile mode = classFile(
            "com/acme/Mode",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(new FieldInfo(0x4008, "READY", "Lcom/acme/Mode;")),
            List.of()
        );

        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            getStatic(0, new FieldRef("com/acme/Mode", "READY", "Lcom/acme/Mode;")),
            invokeVirtual(1, new MethodRef("com/acme/Mode", "name", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ), mode);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectStaticField("com/acme/Mode", "READY"))
        );
    }

    @Test
    void rejectsEnumOrdinalForUnknownConstantLiteral() {
        final ClassFile mode = classFile(
            "com/acme/Mode",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(new FieldInfo(0x4008, "READY", "Lcom/acme/Mode;")),
            List.of()
        );

        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            stringConstant(0, "MISSING"),
            invokeVirtual(1, new MethodRef("com/acme/Mode", "ordinal", "()I")),
            plain(2, 172, "ireturn")
        ), mode))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN043");
                assertThat(exception.diagnostic().subject()).isEqualTo("com/acme/Mode.MISSING");
            });
    }

    @Test
    void rejectsNonAsciiStringLengthUntilUtf16StringModelExists() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            stringConstant(0, "cafe\u00e9"),
            invokeVirtual(1, new MethodRef("java/lang/String", "length", "()I")),
            plain(2, 172, "ireturn")
        );

        assertThatThrownBy(() -> lowerMain(main))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("JAVAN046")
            .hasMessageContaining("non-ASCII string constants require the UTF-16 string model");
    }

    @Test
    void rejectsPathToStringWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            0,
            0,
            invokeInterface(0, new MethodRef("java/nio/file/Path", "toString", "()Ljava/lang/String;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.toString()Ljava/lang/String;");
                assertThat(exception.diagnostic().reason()).isEqualTo("An object value was expected on the bytecode stack.");
            });
    }

    @Test
    void rejectsPathToStringWithPrimitiveReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            plain(0, 4, "iconst_1"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.toString()Ljava/lang/String;");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected object value on the bytecode stack, but found int.");
            });
    }

    @Test
    void lowersPathToStringToReceiverIdentity() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectLocal("arg0"))
        );
    }

    @Test
    void rejectsUnsupportedPathToStringWithBooleanDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "toString", "()Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.toString()Z");
            });
    }

    @Test
    void rejectsDurationToMillisWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()J",
            0,
            0,
            invokeVirtual(0, new MethodRef("java/time/Duration", "toMillis", "()J")),
            plain(1, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/time/Duration.toMillis()J");
                assertThat(exception.diagnostic().reason()).isEqualTo("An object value was expected on the bytecode stack.");
            });
    }

    @Test
    void rejectsDurationToMillisWithPrimitiveReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()J",
            1,
            0,
            plain(0, 4, "iconst_1"),
            invokeVirtual(1, new MethodRef("java/time/Duration", "toMillis", "()J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/time/Duration.toMillis()J");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected object value on the bytecode stack, but found int.");
            });
    }

    @Test
    void lowersDurationToMillisToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/time/Duration;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/time/Duration", "toMillis", "()J")),
            plain(2, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall(
                "javan_duration_to_millis",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsFileTimeToMillisWithPrimitiveReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()J",
            1,
            0,
            plain(0, 4, "iconst_1"),
            invokeVirtual(1, new MethodRef("java/nio/file/attribute/FileTime", "toMillis", "()J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/nio/file/attribute/FileTime.toMillis()J");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected object value on the bytecode stack, but found int.");
            });
    }

    @Test
    void rejectsPathResolveWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/nio/file/Path;",
            1,
            0,
            stringConstant(0, "child"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.resolve(Ljava/lang/String;)Ljava/nio/file/Path;");
                assertThat(exception.diagnostic().reason()).isEqualTo("An object value was expected on the bytecode stack.");
            });
    }

    @Test
    void lowersPathIsAbsoluteToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "isAbsolute", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_path_is_absolute", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void rejectsUnsupportedPathIsAbsoluteWithIntDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "isAbsolute", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.isAbsolute()I");
            });
    }

    @Test
    void lowersPathToAbsolutePathToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "toAbsolutePath", "()Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_to_absolute",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathToAbsolutePathWithStringDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "toAbsolutePath", "(Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.toAbsolutePath(Ljava/lang/String;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathNormalizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "normalize", "()Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_normalize",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathNormalizeWithStringArgumentDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "normalize", "(Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.normalize(Ljava/lang/String;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void rejectsPathNormalizeWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/nio/file/Path;",
            0,
            0,
            invokeInterface(0, new MethodRef("java/nio/file/Path", "normalize", "()Ljava/nio/file/Path;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.normalize()Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathGetParentToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "getParent", "()Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_get_parent",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathGetParentWithIndexDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;I)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "getParent", "(I)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.getParent(I)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathGetFileNameToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "getFileName", "()Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_get_file_name",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathGetFileNameWithIndexDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;I)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "getFileName", "(I)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.getFileName(I)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathResolvePathArgumentToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "resolve", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_resolve",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathResolveIntDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;I)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "resolve", "(I)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.resolve(I)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathEqualsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "equals", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_path_equals",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathEqualsWithPathDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "equals", "(Ljava/nio/file/Path;)Z")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.equals(Ljava/nio/file/Path;)Z");
            });
    }

    @Test
    void lowersPathStartsWithToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "startsWith", "(Ljava/nio/file/Path;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_path_starts_with",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathStartsWithStringDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/String;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "startsWith", "(Ljava/lang/String;)Z")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.startsWith(Ljava/lang/String;)Z");
            });
    }

    @Test
    void rejectsPathEqualsWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "equals", "(Ljava/lang/Object;)Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.equals(Ljava/lang/Object;)Z");
            });
    }

    @Test
    void rejectsPathToStringWithPrintStreamReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.toString()Ljava/lang/String;");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected object value on the bytecode stack, but found print stream.");
            });
    }

    @Test
    void rejectsDirectoryStreamCloseWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            0,
            0,
            invokeInterface(0, new MethodRef("java/nio/file/DirectoryStream", "close", "()V")),
            plain(1, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/DirectoryStream.close()V");
                assertThat(exception.diagnostic().reason()).isEqualTo("An object value was expected on the bytecode stack.");
            });
    }

    @Test
    void lowersFileSeparatorCharFieldToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("java/io/File", "separatorChar", "C")),
            plain(1, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_file_separator_char", List.of()))
        );
    }

    @Test
    void lowersFilePathSeparatorCharFieldToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("java/io/File", "pathSeparatorChar", "C")),
            plain(1, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_file_path_separator_char", List.of()))
        );
    }

    @Test
    void lowersFilePathSeparatorFieldToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            getStatic(0, new FieldRef("java/io/File", "pathSeparator", "Ljava/lang/String;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_file_path_separator", List.of()))
        );
    }

    @Test
    void rejectsFileSeparatorCharFieldWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("java/io/File", "separatorChar", "I")),
            plain(1, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]: bytecode is not implemented by native code generation")
            .hasMessageContaining("getstatic java/io/File.separatorChar:I");
    }

    @Test
    void lowersSystemOutFieldToRuntimeObjectCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/io/PrintStream;",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_system_out", List.of()))
        );
    }

    @Test
    void lowersSystemErrFieldToRuntimeObjectCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/io/PrintStream;",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_system_err", List.of()))
        );
    }

    @Test
    void rejectsSystemOutFieldWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "out", "I")),
            plain(1, 172, "ireturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]")
            .hasMessageContaining("getstatic java/lang/System.out:I");
    }

    @Test
    void rejectsUnsupportedSystemFieldRead() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/io/PrintStream;",
            1,
            0,
            getStatic(0, new FieldRef("java/lang/System", "in", "Ljava/io/InputStream;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN040]")
            .hasMessageContaining("getstatic java/lang/System.in:Ljava/io/InputStream;");
    }

    @Test
    void lowersSupportedJdkEnumConstantFieldToStringLiteral() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Object;",
            1,
            0,
            getStatic(0, new FieldRef(
                "java/nio/file/LinkOption",
                "NOFOLLOW_LINKS",
                "Ljava/nio/file/LinkOption;"
            )),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.stringLiteral("NOFOLLOW_LINKS"))
        );
    }

    @Test
    void lowersSupportedStandardCopyOptionFieldToStringLiteral() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Object;",
            1,
            0,
            getStatic(0, new FieldRef(
                "java/nio/file/StandardCopyOption",
                "REPLACE_EXISTING",
                "Ljava/nio/file/StandardCopyOption;"
            )),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.stringLiteral("REPLACE_EXISTING"))
        );
    }

    @Test
    void lowersFilesExistsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "exists",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"
            )),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_files_exists",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedFilesExistsWithoutLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "exists", "(Ljava/nio/file/Path;)Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/nio/file/Files.exists(Ljava/nio/file/Path;)Z");
            });
    }

    @Test
    void lowersFilesIsDirectoryToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "isDirectory",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"
            )),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_files_is_directory",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersFilesIsDirectoryWithImplicitEmptyVarargsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            2,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 3, "iconst_0"),
            classInstruction(2, 189, "anewarray", "java/nio/file/LinkOption"),
            invokeStatic(3, new MethodRef("java/nio/file/Files", "isDirectory", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z")),
            plain(4, 172, "ireturn")
        ));

        assertZeroVarargsIntCall(function, "javan_files_is_directory", IrExpression.objectLocal("arg0"));
    }

    @Test
    void rejectsUnsupportedFilesIsDirectoryWithoutLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "isDirectory", "(Ljava/nio/file/Path;)Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/nio/file/Files.isDirectory(Ljava/nio/file/Path;)Z");
            });
    }

    @Test
    void lowersFilesIsRegularFileToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "isRegularFile",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"
            )),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_files_is_regular_file",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedFilesIsRegularFileWithoutLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "isRegularFile", "(Ljava/nio/file/Path;)Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.isRegularFile(Ljava/nio/file/Path;)Z");
            });
    }

    @Test
    void lowersFilesIsExecutableToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "isExecutable", "(Ljava/nio/file/Path;)Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_files_is_executable",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedFilesIsExecutableWithLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Files", "isExecutable", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z")),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.isExecutable(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z");
            });
    }

    @Test
    void lowersFilesCreateDirectoriesWithImplicitEmptyVarargsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            2,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 3, "iconst_0"),
            classInstruction(2, 189, "anewarray", "java/nio/file/attribute/FileAttribute"),
            invokeStatic(3, new MethodRef("java/nio/file/Files", "createDirectories", "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;")),
            plain(4, 176, "areturn")
        ));

        assertZeroVarargsObjectCall(function, "javan_files_create_directories", IrExpression.objectLocal("arg0"));
    }

    @Test
    void lowersFilesDeleteIfExistsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "deleteIfExists", "(Ljava/nio/file/Path;)Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "int0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_files_delete_if_exists", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesDeleteIfExistsWithLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "deleteIfExists",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"
            )),
            plain(3, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.deleteIfExists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z");
            });
    }

    @Test
    void lowersFilesGetLastModifiedTimeWithImplicitEmptyVarargsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/attribute/FileTime;",
            2,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 3, "iconst_0"),
            classInstruction(2, 189, "anewarray", "java/nio/file/LinkOption"),
            invokeStatic(3, new MethodRef(
                "java/nio/file/Files",
                "getLastModifiedTime",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Ljava/nio/file/attribute/FileTime;"
            )),
            plain(4, 176, "areturn")
        ));

        assertZeroVarargsObjectCall(function, "javan_files_get_last_modified_time", IrExpression.objectLocal("arg0"));
    }

    @Test
    void lowersByteArrayCloneToCopyOfByteHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([B)[B",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("[B", "clone", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectLocal("arg0")),
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_arrays_copy_of_byte",
                List.of(
                    IrExpression.objectLocal("object0"),
                    IrExpression.intCall("javan_array_length", List.of(IrExpression.objectLocal("object0")))
                )
            ))
        );
    }

    @Test
    void lowersIntArrayCloneToCopyOfIntHelper() {
        assertArrayCloneLowering("([I)[I", "[I", "javan_arrays_copy_of_int");
    }

    @Test
    void lowersLongArrayCloneToCopyOfLongHelper() {
        assertArrayCloneLowering("([J)[J", "[J", "javan_arrays_copy_of_long");
    }

    @Test
    void lowersShortArrayCloneToCopyOfShortHelper() {
        assertArrayCloneLowering("([S)[S", "[S", "javan_arrays_copy_of_short");
    }

    @Test
    void lowersCharArrayCloneToCopyOfCharHelper() {
        assertArrayCloneLowering("([C)[C", "[C", "javan_arrays_copy_of_char");
    }

    @Test
    void lowersFloatArrayCloneToCopyOfFloatHelper() {
        assertArrayCloneLowering("([F)[F", "[F", "javan_arrays_copy_of_float");
    }

    @Test
    void lowersDoubleArrayCloneToCopyOfDoubleHelper() {
        assertArrayCloneLowering("([D)[D", "[D", "javan_arrays_copy_of_double");
    }

    @Test
    void lowersObjectArrayCloneToCopyOfObjectHelper() {
        assertArrayCloneLowering(
            "([Ljava/lang/String;)[Ljava/lang/String;",
            "[Ljava/lang/String;",
            "javan_arrays_copy_of_object"
        );
    }

    @Test
    void lowersNestedPrimitiveArrayCloneToCopyOfObjectHelper() {
        assertArrayCloneLowering("([[I)[[I", "[[I", "javan_arrays_copy_of_object");
    }

    @Test
    void rejectsBooleanArrayCloneUntilRuntimeHelperExists() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "([Z)[Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("[Z", "clone", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN044]: array clone type is not supported")
            .hasMessageContaining("[Z.clone()Ljava/lang/Object;")
            .hasMessageContaining("The runtime does not have a clone helper for this array kind yet.");
    }

    @Test
    void lowersStringBuilderDefaultConstructorToRuntimeAllocation() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/StringBuilder;",
            2,
            0,
            classInstruction(0, 187, "new", "java/lang/StringBuilder"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/lang/StringBuilder", "<init>", "()V")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_stringbuilder_new", List.of())),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderStringConstructorToAppendRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            3,
            1,
            classInstruction(0, 187, "new", "java/lang/StringBuilder"),
            plain(1, 89, "dup"),
            plain(2, 42, "aload_0"),
            invokeSpecial(3, new MethodRef("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_stringbuilder_new", List.of())),
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_append_string",
                List.of(IrExpression.objectLocal("object0"), IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersChainedStringBuilderFlowToNestedRuntimeStateCalls() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            3,
            0,
            classInstruction(0, 187, "new", "java/lang/StringBuilder"),
            plain(1, 89, "dup"),
            stringConstant(2, "x"),
            invokeSpecial(3, new MethodRef("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V")),
            stringConstant(4, "y"),
            invokeVirtual(5, new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")),
            plain(6, 4, "iconst_1"),
            invokeVirtual(7, new MethodRef("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;")),
            invokeVirtual(8, new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")),
            plain(9, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.OBJECT, "object0"),
            new IrLocal(IrType.OBJECT, "object1"),
            new IrLocal(IrType.OBJECT, "object2"),
            new IrLocal(IrType.OBJECT, "object3")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_stringbuilder_new", List.of())),
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_append_string",
                List.of(IrExpression.objectLocal("object0"), IrExpression.stringLiteral("x"))
            ),
            IrInstruction.assignObject(
                "object1",
                IrExpression.objectCall(
                    "javan_stringbuilder_append_string",
                    List.of(IrExpression.objectLocal("object0"), IrExpression.stringLiteral("y"))
                )
            ),
            IrInstruction.assignObject(
                "object2",
                IrExpression.objectCall(
                    "javan_stringbuilder_append_int",
                    List.of(IrExpression.objectLocal("object1"), IrExpression.intLiteral(1))
                )
            ),
            IrInstruction.assignObject(
                "object3",
                IrExpression.objectCall("javan_stringbuilder_to_string", List.of(IrExpression.objectLocal("object2")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object3"))
        );
    }

    @Test
    void lowersSocketInputStreamReadThroughStoredLocalToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "getInputStream", "()Ljava/io/InputStream;")),
            plain(2, 76, "astore_1"),
            plain(3, 43, "aload_1"),
            invokeVirtual(4, new MethodRef("java/io/InputStream", "read", "()I")),
            plain(5, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.OBJECT, "object0"),
            new IrLocal(IrType.OBJECT, "local1_object_1")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_socket_input_stream", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.assignObject("local1_object_1", IrExpression.objectLocal("object0")),
            IrInstruction.returnInt(IrExpression.intCall("javan_socket_input_stream_read", List.of(IrExpression.objectLocal("local1_object_1"))))
        );
    }

    @Test
    void lowersSocketInputStreamReadRangeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;[B)I",
            4,
            2,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "getInputStream", "()Ljava/io/InputStream;")),
            plain(2, 43, "aload_1"),
            plain(3, 3, "iconst_0"),
            plain(4, 4, "iconst_1"),
            invokeVirtual(5, new MethodRef("java/io/InputStream", "read", "([BII)I")),
            plain(6, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.OBJECT, "object0"),
            new IrLocal(IrType.INT, "int1")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_socket_input_stream", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.assignInt(
                "int1",
                IrExpression.intCall(
                    "javan_socket_input_stream_read_bytes_range",
                    List.of(
                        IrExpression.objectLocal("object0"),
                        IrExpression.objectLocal("arg1"),
                        IrExpression.intLiteral(0),
                        IrExpression.intLiteral(1)
                    )
                )
            ),
            IrInstruction.returnInt(IrExpression.intLocal("int1"))
        );
    }

    @Test
    void lowersSocketOutputStreamWriteAndFlushThroughStoredLocalToRuntimeCalls() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/Socket;[B)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/net/Socket", "getOutputStream", "()Ljava/io/OutputStream;")),
            plain(2, 77, "astore_2"),
            plain(3, 44, "aload_2"),
            plain(4, 43, "aload_1"),
            invokeVirtual(5, new MethodRef("java/io/OutputStream", "write", "([B)V")),
            plain(6, 44, "aload_2"),
            plainOperands(7, 16, "bipush", 7),
            invokeVirtual(8, new MethodRef("java/io/OutputStream", "write", "(I)V")),
            plain(9, 44, "aload_2"),
            invokeVirtual(10, new MethodRef("java/io/OutputStream", "flush", "()V")),
            plain(11, 44, "aload_2"),
            invokeVirtual(12, new MethodRef("java/io/OutputStream", "close", "()V")),
            plain(13, 177, "return")
        ));

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.OBJECT, "object0"),
            new IrLocal(IrType.OBJECT, "local2_object_1")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_socket_output_stream", List.of(IrExpression.objectLocal("arg0")))),
            IrInstruction.assignObject("local2_object_1", IrExpression.objectLocal("object0")),
            IrInstruction.callStaticVoid(
                "javan_socket_output_stream_write_bytes",
                List.of(IrExpression.objectLocal("local2_object_1"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.callStaticVoid(
                "javan_socket_output_stream_write",
                List.of(IrExpression.objectLocal("local2_object_1"), IrExpression.intLiteral(7))
            ),
            IrInstruction.callStaticVoid("javan_socket_output_stream_flush", List.of(IrExpression.objectLocal("local2_object_1"))),
            IrInstruction.callStaticVoid("javan_socket_output_stream_close", List.of(IrExpression.objectLocal("local2_object_1"))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsNonSocketInputStreamReceiverAtLowering() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            1,
            plain(0, 1, "aconst_null"),
            plain(1, 75, "astore_0"),
            plain(2, 42, "aload_0"),
            invokeVirtual(3, new MethodRef("java/io/InputStream", "read", "()I")),
            plain(4, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN062");
                assertThat(exception.diagnostic().subject()).isEqualTo("java/io/InputStream.read()I");
            });
    }

    @Test
    void lowersStringBuilderAppendStringToRuntimeCall() {
        assertStringBuilderAppendObject(
            "(Ljava/lang/StringBuilder;Ljava/lang/String;)Ljava/lang/StringBuilder;",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            "javan_stringbuilder_append_string"
        );
    }

    @Test
    void lowersStringBuilderAppendObjectToRuntimeCall() {
        assertStringBuilderAppendObject(
            "(Ljava/lang/StringBuilder;Ljava/lang/Object;)Ljava/lang/StringBuilder;",
            "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
            "javan_stringbuilder_append_object"
        );
    }

    @Test
    void lowersStringBuilderAppendBooleanToRuntimeCall() {
        assertStringBuilderAppendInt(
            "(Ljava/lang/StringBuilder;Z)Ljava/lang/StringBuilder;",
            "(Z)Ljava/lang/StringBuilder;",
            "javan_stringbuilder_append_boolean"
        );
    }

    @Test
    void lowersStringBuilderAppendCharToRuntimeCall() {
        assertStringBuilderAppendInt(
            "(Ljava/lang/StringBuilder;C)Ljava/lang/StringBuilder;",
            "(C)Ljava/lang/StringBuilder;",
            "javan_stringbuilder_append_char"
        );
    }

    @Test
    void lowersStringBuilderAppendIntToRuntimeCall() {
        assertStringBuilderAppendInt(
            "(Ljava/lang/StringBuilder;I)Ljava/lang/StringBuilder;",
            "(I)Ljava/lang/StringBuilder;",
            "javan_stringbuilder_append_int"
        );
    }

    @Test
    void lowersStringBuilderAppendLongToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;J)Ljava/lang/StringBuilder;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 31, "lload_1"),
            invokeVirtual(2, new MethodRef(
                "java/lang/StringBuilder",
                "append",
                "(J)Ljava/lang/StringBuilder;"
            )),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_append_long",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.longLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderToStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_stringbuilder_to_string", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsStringBuilderToStringWithoutReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            0,
            0,
            invokeVirtual(0, new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/StringBuilder.toString()Ljava/lang/String;");
                assertThat(exception.diagnostic().reason()).isEqualTo("An object value was expected on the bytecode stack.");
            });
    }

    @Test
    void rejectsStringBuilderToStringWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/StringBuilder.toString()Ljava/lang/Object;");
            });
    }

    @Test
    void lowersStringBuilderLengthToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "length", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_length",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsStringBuilderLengthWithPrimitiveReceiverShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()I",
            1,
            0,
            plain(0, 4, "iconst_1"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "length", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected object value on the bytecode stack, but found int.");
            });
    }

    @Test
    void rejectsStringBuilderLengthWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "length", "()J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/StringBuilder.length()J");
            });
    }

    @Test
    void lowersStringBuilderIsEmptyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "isEmpty", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_is_empty",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersStringBuilderCharAtToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;I)C",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "charAt", "(I)C")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_char_at",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringBuilderSubstringBeginToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;I)Ljava/lang/String;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "substring", "(I)Ljava/lang/String;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_substring",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderSubstringRangeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;II)Ljava/lang/String;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "substring", "(II)Ljava/lang/String;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_substring_range",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderIndexOfStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;Ljava/lang/String;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "indexOf", "(Ljava/lang/String;)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_index_of_string",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringBuilderIndexOfStringFromToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;Ljava/lang/String;I)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "indexOf", "(Ljava/lang/String;I)I")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_index_of_string_from",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.intLocal("arg2"))
            ))
        );
    }

    @Test
    void lowersStringBuilderLastIndexOfStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;Ljava/lang/String;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "lastIndexOf", "(Ljava/lang/String;)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_last_index_of_string",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringBuilderLastIndexOfStringFromToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;Ljava/lang/String;I)I",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "lastIndexOf", "(Ljava/lang/String;I)I")),
            plain(4, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_last_index_of_string_from",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.intLocal("arg2"))
            ))
        );
    }

    @Test
    void lowersStringBuilderSubSequenceToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;II)Ljava/lang/CharSequence;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "subSequence", "(II)Ljava/lang/CharSequence;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_substring_range",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderCompareToToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;Ljava/lang/StringBuilder;)I",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "compareTo", "(Ljava/lang/StringBuilder;)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_stringbuilder_compare_to",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersStringBuilderDeleteToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;II)Ljava/lang/StringBuilder;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "delete", "(II)Ljava/lang/StringBuilder;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_delete",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderDeleteCharAtToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;I)Ljava/lang/StringBuilder;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "deleteCharAt", "(I)Ljava/lang/StringBuilder;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_delete_char_at",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderReverseToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)Ljava/lang/StringBuilder;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "reverse", "()Ljava/lang/StringBuilder;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_stringbuilder_reverse", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderInsertStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;ILjava/lang/String;)Ljava/lang/StringBuilder;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "insert", "(ILjava/lang/String;)Ljava/lang/StringBuilder;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_insert_string",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderInsertCharToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;IC)Ljava/lang/StringBuilder;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "insert", "(IC)Ljava/lang/StringBuilder;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_insert_char",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderReplaceStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;IILjava/lang/String;)Ljava/lang/StringBuilder;",
            4,
            4,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            plain(3, 45, "aload_3"),
            invokeVirtual(4, new MethodRef("java/lang/StringBuilder", "replace", "(IILjava/lang/String;)Ljava/lang/StringBuilder;")),
            plain(5, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_replace_string",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"), IrExpression.objectLocal("arg3"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersStringBuilderEnsureCapacityToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;I)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "ensureCapacity", "(I)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_ensure_capacity_public",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersStringBuilderTrimToSizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "trimToSize", "()V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_trim_to_size",
                List.of(IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersStringBuilderSetCharAtToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;IC)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "setCharAt", "(IC)V")),
            plain(4, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_set_char_at",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersStringBuilderInsertBooleanToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;IZ)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "insert", "(IZ)Ljava/lang/StringBuilder;")),
            plain(4, 87, "pop"),
            plain(5, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_insert_boolean",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersStringBuilderInsertIntToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;II)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "insert", "(II)Ljava/lang/StringBuilder;")),
            plain(4, 87, "pop"),
            plain(5, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_insert_int",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.intLocal("arg2"))
                )
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersStringBuilderInsertLongToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;IJ)V",
            4,
            4,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 32, "lload_2"),
            invokeVirtual(3, new MethodRef("java/lang/StringBuilder", "insert", "(IJ)Ljava/lang/StringBuilder;")),
            plain(4, 87, "pop"),
            plain(5, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_insert_long",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.longLocal("arg2"))
                )
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsStringBuilderIsEmptyWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "isEmpty", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/StringBuilder.isEmpty()I");
            });
    }

    @Test
    void lowersStringBuilderSetLengthToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;I)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "setLength", "(I)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_stringbuilder_set_length",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsStringBuilderSetLengthWithoutArgumentShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;)V",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/lang/StringBuilder", "setLength", "(I)V")),
            plain(2, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected int value on the bytecode stack, but found object.");
            });
    }

    @Test
    void rejectsStringBuilderSetLengthWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;Z)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 4, "iconst_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "setLength", "(Z)V")),
            plain(3, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/lang/StringBuilder.setLength(Z)V");
            });
    }

    @Test
    void lowersStringBuilderAppendDoubleToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;D)Ljava/lang/StringBuilder;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 39, "dload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "append", "(D)Ljava/lang/StringBuilder;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_stringbuilder_append_double",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.doubleLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsStringBuilderAppendLongWhenArgumentIsInt() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/StringBuilder;I)Ljava/lang/StringBuilder;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 4, "iconst_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().reason()).isEqualTo("Expected long value on the bytecode stack, but found int.");
            });
    }

    @Test
    void lowersMathAbsIntToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)I",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/Math", "abs", "(I)I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_math_abs_int", List.of(IrExpression.intLocal("arg0"))))
        );
    }

    @Test
    void lowersMathAbsLongToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)J",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/lang/Math", "abs", "(J)J")),
            plain(2, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall("javan_math_abs_long", List.of(IrExpression.longLocal("arg0"))))
        );
    }

    @Test
    void lowersMathAbsFloatToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(F)F",
            1,
            1,
            plain(0, 34, "fload_0"),
            invokeStatic(1, new MethodRef("java/lang/Math", "abs", "(F)F")),
            plain(2, 174, "freturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnFloat(IrExpression.floatCall("javan_math_abs_float", List.of(IrExpression.floatLocal("arg0"))))
        );
    }

    @Test
    void lowersMathAbsDoubleToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(D)D",
            2,
            2,
            plain(0, 38, "dload_0"),
            invokeStatic(1, new MethodRef("java/lang/Math", "abs", "(D)D")),
            plain(2, 175, "dreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnDouble(IrExpression.doubleCall("javan_math_abs_double", List.of(IrExpression.doubleLocal("arg0"))))
        );
    }

    @Test
    void lowersMathMinIntToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            2,
            2,
            plain(0, 26, "iload_0"),
            plain(1, 27, "iload_1"),
            invokeStatic(2, new MethodRef("java/lang/Math", "min", "(II)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_math_min_int",
                List.of(IrExpression.intLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersMathMinLongToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(JJ)J",
            4,
            4,
            plain(0, 30, "lload_0"),
            plain(1, 32, "lload_2"),
            invokeStatic(2, new MethodRef("java/lang/Math", "min", "(JJ)J")),
            plain(3, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall(
                "javan_math_min_long",
                List.of(IrExpression.longLocal("arg0"), IrExpression.longLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersMathMaxIntToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(II)I",
            2,
            2,
            plain(0, 26, "iload_0"),
            plain(1, 27, "iload_1"),
            invokeStatic(2, new MethodRef("java/lang/Math", "max", "(II)I")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_math_max_int",
                List.of(IrExpression.intLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersMathMaxLongToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(JJ)J",
            4,
            4,
            plain(0, 30, "lload_0"),
            plain(1, 32, "lload_2"),
            invokeStatic(2, new MethodRef("java/lang/Math", "max", "(JJ)J")),
            plain(3, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall(
                "javan_math_max_long",
                List.of(IrExpression.longLocal("arg0"), IrExpression.longLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersMathToIntExactToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)I",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/lang/Math", "toIntExact", "(J)I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_math_to_int_exact", List.of(IrExpression.longLocal("arg0"))))
        );
    }

    @Test
    void lowersIntegerToStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/lang/String;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/Integer", "toString", "(I)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_string_value_of_int", List.of(IrExpression.intLocal("arg0"))))
        );
    }

    @Test
    void lowersStringValueOfIntToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/lang/String;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/String", "valueOf", "(I)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_string_value_of_int", List.of(IrExpression.intLocal("arg0"))))
        );
    }

    @Test
    void lowersLongToStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)Ljava/lang/String;",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/lang/Long", "toString", "(J)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_string_value_of_long", List.of(IrExpression.longLocal("arg0"))))
        );
    }

    @Test
    void lowersBooleanToStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Z)Ljava/lang/String;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/Boolean", "toString", "(Z)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_string_value_of_bool", List.of(IrExpression.intLocal("arg0"))))
        );
    }

    @Test
    void lowersFloatToStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(F)Ljava/lang/String;",
            1,
            1,
            plain(0, 34, "fload_0"),
            invokeStatic(1, new MethodRef("java/lang/Float", "toString", "(F)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_string_value_of_float", List.of(IrExpression.floatLocal("arg0"))))
        );
    }

    @Test
    void lowersFloatIntBitsToFloatToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)F",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/Float", "intBitsToFloat", "(I)F")),
            plain(2, 174, "freturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnFloat(IrExpression.floatCall("javan_float_int_bits_to_float", List.of(IrExpression.intLocal("arg0"))))
        );
    }

    @Test
    void lowersDoubleToStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(D)Ljava/lang/String;",
            2,
            2,
            plain(0, 38, "dload_0"),
            invokeStatic(1, new MethodRef("java/lang/Double", "toString", "(D)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_string_value_of_double", List.of(IrExpression.doubleLocal("arg0"))))
        );
    }

    @Test
    void lowersDoubleLongBitsToDoubleToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)D",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/lang/Double", "longBitsToDouble", "(J)D")),
            plain(2, 175, "dreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnDouble(IrExpression.doubleCall("javan_double_long_bits_to_double", List.of(IrExpression.longLocal("arg0"))))
        );
    }

    @Test
    void lowersIntegerValueOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Integer;",
            1,
            0,
            plain(0, 5, "iconst_2"),
            invokeStatic(1, new MethodRef("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_integer_value_of", List.of(IrExpression.intLiteral(2))))
        );
    }

    @Test
    void lowersLongValueOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Long;",
            2,
            0,
            plain(0, 10, "lconst_1"),
            invokeStatic(1, new MethodRef("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_long_value_of", List.of(IrExpression.longLiteral(1L))))
        );
    }

    @Test
    void lowersFloatValueOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Float;",
            1,
            0,
            plain(0, 12, "fconst_1"),
            invokeStatic(1, new MethodRef("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_float_value_of", List.of(IrExpression.floatLiteral(1.0f))))
        );
    }

    @Test
    void rejectsUnsupportedFloatParseFloatIntrinsic() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()F",
            1,
            0,
            stringConstant(0, "1.25"),
            invokeStatic(1, new MethodRef("java/lang/Float", "parseFloat", "(Ljava/lang/String;)F")),
            plain(2, 174, "freturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/Float.parseFloat(Ljava/lang/String;)F");
            });
    }

    @Test
    void lowersDoubleValueOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Double;",
            2,
            0,
            plain(0, 15, "dconst_1"),
            invokeStatic(1, new MethodRef("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_double_value_of", List.of(IrExpression.doubleLiteral(1.0))))
        );
    }

    @Test
    void rejectsUnsupportedDoubleParseDoubleIntrinsic() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()D",
            1,
            0,
            stringConstant(0, "1.25"),
            invokeStatic(1, new MethodRef("java/lang/Double", "parseDouble", "(Ljava/lang/String;)D")),
            plain(2, 175, "dreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/Double.parseDouble(Ljava/lang/String;)D");
            });
    }

    @Test
    void lowersBooleanValueOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Boolean;",
            1,
            0,
            plain(0, 4, "iconst_1"),
            invokeStatic(1, new MethodRef("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_boolean_value_of", List.of(IrExpression.intLiteral(1))))
        );
    }

    @Test
    void rejectsUnsupportedBooleanParseBooleanIntrinsic() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Z",
            1,
            0,
            stringConstant(0, "true"),
            invokeStatic(1, new MethodRef("java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/Boolean.parseBoolean(Ljava/lang/String;)Z");
            });
    }

    @Test
    void rejectsUnsupportedBooleanValueOfStringDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Boolean;",
            1,
            0,
            stringConstant(0, "true"),
            invokeStatic(1, new MethodRef("java/lang/Boolean", "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/Boolean.valueOf(Ljava/lang/String;)Ljava/lang/Boolean;");
            });
    }

    @Test
    void lowersSystemGetPropertyNameToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            stringConstant(0, "java.version"),
            invokeStatic(1, new MethodRef("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_system_get_property",
                List.of(IrExpression.stringLiteral("java.version"))
            ))
        );
    }

    @Test
    void rejectsSystemGetPropertyWithoutArgumentShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            0,
            0,
            invokeStatic(0, new MethodRef("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.getProperty(Ljava/lang/String;)Ljava/lang/String;");
            });
    }

    @Test
    void lowersSystemGetPropertyFallbackToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            2,
            0,
            stringConstant(0, "javan.missing"),
            stringConstant(1, "fallback"),
            invokeStatic(2, new MethodRef("java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_system_get_property_or_default",
                List.of(IrExpression.stringLiteral("javan.missing"), IrExpression.stringLiteral("fallback"))
            ))
        );
    }

    @Test
    void rejectsMalformedSystemGetPropertyFallbackDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;I)Ljava/lang/String;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeStatic(2, new MethodRef("java/lang/System", "getProperty", "(Ljava/lang/String;I)Ljava/lang/String;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.getProperty(Ljava/lang/String;I)Ljava/lang/String;");
            });
    }

    @Test
    void lowersSystemExitToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            plain(0, 3, "iconst_0"),
            invokeStatic(1, new MethodRef("java/lang/System", "exit", "(I)V")),
            plain(2, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid("javan_system_exit", List.of(IrExpression.intLiteral(0))),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsMalformedSystemExitDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            plain(0, 10, "lconst_1"),
            invokeStatic(1, new MethodRef("java/lang/System", "exit", "(J)V")),
            plain(2, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.exit(J)V");
            });
    }

    @Test
    void lowersSystemNanoTimeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()J",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/System", "nanoTime", "()J")),
            plain(1, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall("javan_system_nano_time", List.of()))
        );
    }

    @Test
    void rejectsMalformedSystemNanoTimeDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)J",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/System", "nanoTime", "(I)J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.nanoTime(I)J");
            });
    }

    @Test
    void lowersSystemCurrentTimeMillisToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()J",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/System", "currentTimeMillis", "()J")),
            plain(1, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall("javan_system_current_time_millis", List.of()))
        );
    }

    @Test
    void rejectsMalformedSystemCurrentTimeMillisDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)J",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/System", "currentTimeMillis", "(I)J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.currentTimeMillis(I)J");
            });
    }

    @Test
    void lowersSystemLineSeparatorToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            1,
            0,
            invokeStatic(0, new MethodRef("java/lang/System", "lineSeparator", "()Ljava/lang/String;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_system_line_separator", List.of()))
        );
    }

    @Test
    void rejectsMalformedSystemLineSeparatorDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/lang/String;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/lang/System", "lineSeparator", "(I)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.lineSeparator(I)Ljava/lang/String;");
            });
    }

    @Test
    void lowersSystemGetenvToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_system_getenv",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedSystemGetenvMapDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/util/Map;",
            0,
            0,
            invokeStatic(0, new MethodRef("java/lang/System", "getenv", "()Ljava/util/Map;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.getenv()Ljava/util/Map;");
            });
    }

    @Test
    void lowersSystemArraycopyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;ILjava/lang/Object;II)V",
            5,
            5,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 44, "aload_2"),
            plain(3, 29, "iload_3"),
            plainOperands(4, 21, "iload", 4),
            invokeStatic(5, new MethodRef("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")),
            plain(6, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_system_arraycopy",
                List.of(
                    IrExpression.objectLocal("arg0"),
                    IrExpression.intLocal("arg1"),
                    IrExpression.objectLocal("arg2"),
                    IrExpression.intLocal("arg3"),
                    IrExpression.intLocal("arg4")
                )
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void rejectsMalformedSystemArraycopyDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;ILjava/lang/Object;III)V",
            6,
            6,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 44, "aload_2"),
            plain(3, 29, "iload_3"),
            plainOperands(4, 21, "iload", 4),
            plainOperands(5, 21, "iload", 5),
            invokeStatic(6, new MethodRef("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;III)V")),
            plain(7, 177, "return")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/lang/System.arraycopy(Ljava/lang/Object;ILjava/lang/Object;III)V");
            });
    }

    @Test
    void lowersObjectArrayCopyOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/Object;I)[Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeStatic(2, new MethodRef(
                "java/util/Arrays",
                "copyOf",
                "([Ljava/lang/Object;I)[Ljava/lang/Object;"
            )),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_arrays_copy_of_object",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersByteArrayCopyOfRangeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([B)[B",
            3,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 4, "iconst_1"),
            plain(2, 5, "iconst_2"),
            invokeStatic(3, new MethodRef("java/util/Arrays", "copyOfRange", "([BII)[B")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_arrays_copy_of_range_byte",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(2))
            ))
        );
    }

    @Test
    void lowersObjectArrayCopyOfRangeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/Object;)[Ljava/lang/Object;",
            3,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 4, "iconst_1"),
            plain(2, 5, "iconst_2"),
            invokeStatic(3, new MethodRef(
                "java/util/Arrays",
                "copyOfRange",
                "([Ljava/lang/Object;II)[Ljava/lang/Object;"
            )),
            plain(4, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_arrays_copy_of_range_object",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLiteral(1), IrExpression.intLiteral(2))
            ))
        );
    }

    @Test
    void lowersOptionalOfNullableToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)Ljava/util/Optional;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/Optional", "ofNullable", "(Ljava/lang/Object;)Ljava/util/Optional;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_optional_of_nullable",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersOptionalEmptyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/util/Optional;",
            1,
            0,
            invokeStatic(0, new MethodRef("java/util/Optional", "empty", "()Ljava/util/Optional;")),
            plain(1, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_optional_empty", List.of()))
        );
    }

    @Test
    void rejectsOptionalEmptyWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/util/Optional;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeStatic(1, new MethodRef("java/util/Optional", "empty", "(I)Ljava/util/Optional;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/util/Optional.empty(I)Ljava/util/Optional;");
            });
    }

    @Test
    void lowersOptionalOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)Ljava/util/Optional;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_optional_of",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsOptionalOfWithoutArgumentShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/util/Optional;",
            0,
            0,
            invokeStatic(0, new MethodRef("java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/util/Optional.of(Ljava/lang/Object;)Ljava/util/Optional;");
            });
    }

    @Test
    void rejectsOptionalOfNullableWithoutArgumentShape() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/util/Optional;",
            0,
            0,
            invokeStatic(0, new MethodRef("java/util/Optional", "ofNullable", "(Ljava/lang/Object;)Ljava/util/Optional;")),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/util/Optional.ofNullable(Ljava/lang/Object;)Ljava/util/Optional;");
            });
    }

    @Test
    void lowersOptionalIsPresentToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Optional", "isPresent", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_optional_is_present",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsOptionalIsPresentWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Optional", "isPresent", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/util/Optional.isPresent()I");
            });
    }

    @Test
    void lowersOptionalIsEmptyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Optional", "isEmpty", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_optional_is_empty",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersOptionalOrElseToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;Ljava/lang/Object;)Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_optional_or_else",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersComposedOptionalOfNullableOrElseToNestedRuntimeCalls() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/Optional", "ofNullable", "(Ljava/lang/Object;)Ljava/util/Optional;")),
            plain(2, 43, "aload_1"),
            invokeVirtual(3, new MethodRef("java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_optional_or_else",
                List.of(
                    IrExpression.objectCall("javan_optional_of_nullable", List.of(IrExpression.objectLocal("arg0"))),
                    IrExpression.objectLocal("arg1")
                )
            ))
        );
    }

    @Test
    void lowersOptionalGetToRuntimeThrowingHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Optional", "get", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersOptionalOrElseThrowToRuntimeThrowingHelper() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Optional", "orElseThrow", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_optional_or_else_throw", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedOptionalOrElseThrowSupplier() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;Ljava/util/function/Supplier;)Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef(
                "java/util/Optional",
                "orElseThrow",
                "(Ljava/util/function/Supplier;)Ljava/lang/Object;"
            )),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokevirtual java/util/Optional.orElseThrow(Ljava/util/function/Supplier;)Ljava/lang/Object;");
            });
    }

    @Test
    void rejectsUnsupportedOptionalOrElseGetSupplier() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Optional;Ljava/util/function/Supplier;)Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef(
                "java/util/Optional",
                "orElseGet",
                "(Ljava/util/function/Supplier;)Ljava/lang/Object;"
            )),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokevirtual java/util/Optional.orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;");
            });
    }

    @Test
    void keepsHashMapReceiverAliveAcrossConstructorPutAndGetSequence() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Object;",
            4,
            0,
            classInstruction(0, 187, "new", "java/util/HashMap"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/util/HashMap", "<init>", "()V")),
            plain(3, 89, "dup"),
            stringConstant(4, "k"),
            stringConstant(5, "v"),
            invokeVirtual(6, new MethodRef("java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(7, 87, "pop"),
            stringConstant(8, "k"),
            invokeVirtual(9, new MethodRef("java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(10, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(
            new IrLocal(IrType.OBJECT, "object0"),
            new IrLocal(IrType.OBJECT, "object1"),
            new IrLocal(IrType.OBJECT, "object2")
        );
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_hashmap_new", List.of())),
            IrInstruction.assignObject("object1", IrExpression.objectCall(
                "javan_map_put",
                List.of(
                    IrExpression.objectLocal("object0"),
                    IrExpression.stringLiteral("k"),
                    IrExpression.stringLiteral("v")
                )
            )),
            IrInstruction.assignObject("object2", IrExpression.objectCall(
                "javan_map_get",
                List.of(IrExpression.objectLocal("object0"), IrExpression.stringLiteral("k"))
            )),
            IrInstruction.returnObject(IrExpression.objectLocal("object2"))
        );
    }

    @Test
    void lowersArrayListDefaultConstructorToRuntimeAllocation() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "()Ljava/util/ArrayList;",
            2,
            0,
            classInstruction(0, 187, "new", "java/util/ArrayList"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/util/ArrayList", "<init>", "()V")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_arraylist_new", List.of())),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersArrayListCapacityConstructorToRuntimeAllocation() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/util/ArrayList;",
            3,
            1,
            classInstruction(0, 187, "new", "java/util/ArrayList"),
            plain(1, 89, "dup"),
            plain(2, 26, "iload_0"),
            invokeSpecial(3, new MethodRef("java/util/ArrayList", "<init>", "(I)V")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_arraylist_new", List.of())),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersArrayListCollectionConstructorToRuntimePopulation() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Collection;)Ljava/util/ArrayList;",
            3,
            1,
            classInstruction(0, 187, "new", "java/util/ArrayList"),
            plain(1, 89, "dup"),
            plain(2, 42, "aload_0"),
            invokeSpecial(3, new MethodRef("java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectCall("javan_arraylist_new", List.of())),
            IrInstruction.callStaticVoid(
                "javan_arraylist_add_all",
                List.of(IrExpression.objectLocal("object0"), IrExpression.objectLocal("arg0"))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsArrayListConstructorWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;)Ljava/util/ArrayList;",
            3,
            1,
            classInstruction(0, 187, "new", "java/util/ArrayList"),
            plain(1, 89, "dup"),
            plain(2, 42, "aload_0"),
            invokeSpecial(3, new MethodRef("java/util/ArrayList", "<init>", "(Ljava/lang/Object;)V")),
            plain(4, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokespecial java/util/ArrayList.<init>(Ljava/lang/Object;)V");
            });
    }

    @Test
    void lowersListOfArrayVarargsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "([Ljava/lang/Object;)Ljava/util/List;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/List", "of", "([Ljava/lang/Object;)Ljava/util/List;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_list_of_array",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void lowersListOfFixedArityToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_list_of",
                List.of(IrExpression.intLiteral(2), IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersMapCopyOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;)Ljava/util/Map;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/Map", "copyOf", "(Ljava/util/Map;)Ljava/util/Map;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_map_copy_of",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsMapCopyOfWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Collection;)Ljava/util/Map;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/Map", "copyOf", "(Ljava/util/Collection;)Ljava/util/Map;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/util/Map.copyOf(Ljava/util/Collection;)Ljava/util/Map;");
            });
    }

    @Test
    void lowersListCopyOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Collection;)Ljava/util/List;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/List", "copyOf", "(Ljava/util/Collection;)Ljava/util/List;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_list_copy_of",
                List.of(IrExpression.objectLocal("arg0"))
            ))
        );
    }

    @Test
    void rejectsListCopyOfWithWrongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)Ljava/util/List;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/util/List", "copyOf", "(Ljava/util/List;)Ljava/util/List;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/util/List.copyOf(Ljava/util/List;)Ljava/util/List;");
            });
    }

    @Test
    void lowersListAddToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/List", "add", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "int0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_arraylist_add", List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1")))
            ),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersArrayListAddToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/ArrayList;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/ArrayList", "add", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "int0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_arraylist_add", List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1")))
            ),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersListAddAtToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;ILjava/lang/Object;)V",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/util/List", "add", "(ILjava/lang/Object;)V")),
            plain(4, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_arraylist_add_at",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.objectLocal("arg2"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersListAddAllToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;Ljava/util/Collection;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/List", "addAll", "(Ljava/util/Collection;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "int0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_arraylist_add_all", List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1")))
            ),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersListAddFirstToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;Ljava/lang/Object;)V",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/List", "addFirst", "(Ljava/lang/Object;)V")),
            plain(3, 177, "return")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.callStaticVoid(
                "javan_arraylist_add_first",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersListSetToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;ILjava/lang/Object;)Ljava/lang/Object;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_arraylist_set",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersListRemoveLastToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/List", "removeLast", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_arraylist_remove_last", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersListGetFirstToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/List", "getFirst", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_list_get_first", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersListSizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/List", "size", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_list_size", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersListIsEmptyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/List", "isEmpty", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_list_is_empty", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersListContainsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/List", "contains", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_list_contains",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersCollectionContainsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Collection;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/util/Collection", "contains", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall(
                "javan_list_contains",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersListGetToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;I)Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/util/List", "get", "(I)Ljava/lang/Object;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_list_get",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersListGetLastToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/List", "getLast", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_list_get_last", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersCollectionIteratorToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Collection;)Ljava/util/Iterator;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/util/Collection", "iterator", "()Ljava/util/Iterator;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersListIteratorToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/List;)Ljava/util/Iterator;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/List", "iterator", "()Ljava/util/Iterator;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersIteratorNextToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Iterator;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/util/Iterator", "next", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersIteratorHasNextToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Iterator;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/util/Iterator", "hasNext", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersMapValuesToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;)Ljava/util/Collection;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Map", "values", "()Ljava/util/Collection;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_map_values", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersLinkedHashMapValuesToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/LinkedHashMap;)Ljava/util/Collection;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/LinkedHashMap", "values", "()Ljava/util/Collection;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_map_values", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersMapContainsKeyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;Ljava/lang/Object;)Z",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/Map", "containsKey", "(Ljava/lang/Object;)Z")),
            plain(3, 172, "ireturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.INT, "int0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_map_contains_key", List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1")))
            ),
            IrInstruction.returnInt(IrExpression.intLocal("int0"))
        );
    }

    @Test
    void lowersMapGetToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_map_get", List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersMapPutToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_map_put",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersMapPutIfAbsentToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/util/Map", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_map_put_if_absent",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersHashMapPutIfAbsentToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/util/HashMap", "putIfAbsent", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_map_put_if_absent",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersMapSizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Map", "size", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_map_size", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersMapIsEmptyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;)Z",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/Map", "isEmpty", "()Z")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_map_is_empty", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersMapGetOrDefaultToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeVirtual(3, new MethodRef("java/util/Map", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_map_get_or_default",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void lowersTreeMapSizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/util/TreeMap;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/util/TreeMap", "size", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_map_size", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersPathOfToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Path", "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_of",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersPathsGetToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_of",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathOfUriDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/net/URI;)Ljava/nio/file/Path;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Path", "of", "(Ljava/net/URI;)Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/nio/file/Path.of(Ljava/net/URI;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void rejectsUnsupportedStaticPathResolveOwnerCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Path", "resolve", "(Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Path.resolve(Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathResolveStringArgumentToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_resolve",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void lowersPathRelativizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "relativize", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_relativize",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathRelativizeStringDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "relativize", "(Ljava/lang/String;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokeinterface java/nio/file/Path.relativize(Ljava/lang/String;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersPathGetNameCountToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "getNameCount", "()I")),
            plain(2, 172, "ireturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnInt(IrExpression.intCall("javan_path_get_name_count", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void rejectsUnsupportedPathGetNameCountWithLongDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/Path", "getNameCount", "()J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.getNameCount()J");
            });
    }

    @Test
    void lowersPathGetNameToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;I)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "getName", "(I)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall(
                "javan_path_get_name",
                List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
            ))
        );
    }

    @Test
    void rejectsUnsupportedPathGetNameWithLongIndexDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;J)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 31, "lload_1"),
            invokeInterface(2, new MethodRef("java/nio/file/Path", "getName", "(J)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/nio/file/Path.getName(J)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersIterableIteratorToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Iterable;)Ljava/util/Iterator;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersFilesNewDirectoryStreamToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/DirectoryStream;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "newDirectoryStream", "(Ljava/nio/file/Path;)Ljava/nio/file/DirectoryStream;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_files_new_directory_stream", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesNewDirectoryStreamWithGlobDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/DirectoryStream;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "newDirectoryStream",
                "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/DirectoryStream;"
            )),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.newDirectoryStream(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/DirectoryStream;");
            });
    }

    @Test
    void lowersFilesCreateDirectoriesToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "createDirectories",
                "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"
            )),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_files_create_directories",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesCreateDirectoriesWithoutAttributesDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "createDirectories", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.createDirectories(Ljava/nio/file/Path;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersFilesCopyToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeStatic(3, new MethodRef(
                "java/nio/file/Files",
                "copy",
                "(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/nio/file/Path;"
            )),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_files_copy",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesCopyWithoutCopyOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Files", "copy", "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.copy(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersFilesReadStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "readString", "(Ljava/nio/file/Path;)Ljava/lang/String;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_files_read_string", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesReadStringWithCharsetDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/lang/String;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "readString",
                "(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/lang/String;"
            )),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.readString(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/lang/String;");
            });
    }

    @Test
    void lowersFilesWriteStringToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeStatic(3, new MethodRef(
                "java/nio/file/Files",
                "writeString",
                "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"
            )),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_files_write_string",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesWriteStringWithoutOpenOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;Ljava/lang/CharSequence;)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "writeString",
                "(Ljava/nio/file/Path;Ljava/lang/CharSequence;)Ljava/nio/file/Path;"
            )),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersFilesWriteBytesToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeStatic(3, new MethodRef(
                "java/nio/file/Files",
                "write",
                "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"
            )),
            plain(4, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_files_write_bytes",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"), IrExpression.objectLocal("arg2"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesWriteBytesWithoutOpenOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[B)Ljava/nio/file/Path;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Files", "write", "(Ljava/nio/file/Path;[B)Ljava/nio/file/Path;")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/nio/file/Files.write(Ljava/nio/file/Path;[B)Ljava/nio/file/Path;");
            });
    }

    @Test
    void lowersFilesReadAllBytesToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)[B",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "readAllBytes", "(Ljava/nio/file/Path;)[B")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall("javan_files_read_all_bytes", List.of(IrExpression.objectLocal("arg0")))
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesReadAllBytesWithOpenOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)[B",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Files", "readAllBytes", "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)[B")),
            plain(3, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.readAllBytes(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)[B");
            });
    }

    @Test
    void lowersFilesSizeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef("java/nio/file/Files", "size", "(Ljava/nio/file/Path;)J")),
            plain(2, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall("javan_files_size", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void rejectsUnsupportedFilesSizeWithLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)J",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef("java/nio/file/Files", "size", "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)J")),
            plain(3, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic java/nio/file/Files.size(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)J");
            });
    }

    @Test
    void lowersFilesGetLastModifiedTimeToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Ljava/nio/file/attribute/FileTime;",
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeStatic(2, new MethodRef(
                "java/nio/file/Files",
                "getLastModifiedTime",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Ljava/nio/file/attribute/FileTime;"
            )),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    "javan_files_get_last_modified_time",
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    @Test
    void rejectsUnsupportedFilesGetLastModifiedTimeWithoutLinkOptionsDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/Path;)Ljava/nio/file/attribute/FileTime;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeStatic(1, new MethodRef(
                "java/nio/file/Files",
                "getLastModifiedTime",
                "(Ljava/nio/file/Path;)Ljava/nio/file/attribute/FileTime;"
            )),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject())
                    .isEqualTo("invokestatic java/nio/file/Files.getLastModifiedTime(Ljava/nio/file/Path;)Ljava/nio/file/attribute/FileTime;");
            });
    }

    @Test
    void lowersDirectoryStreamIteratorToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/DirectoryStream;)Ljava/util/Iterator;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/nio/file/DirectoryStream", "iterator", "()Ljava/util/Iterator;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void lowersFileTimeToMillisToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/attribute/FileTime;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/nio/file/attribute/FileTime", "toMillis", "()J")),
            plain(2, 173, "lreturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnLong(IrExpression.longCall("javan_file_time_to_millis", List.of(IrExpression.objectLocal("arg0"))))
        );
    }

    @Test
    void rejectsUnsupportedFileTimeLengthCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/attribute/FileTime;)J",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/nio/file/attribute/FileTime", "length", "()J")),
            plain(2, 173, "lreturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/nio/file/attribute/FileTime.length()J");
            });
    }

    @Test
    void rejectsUnsupportedFileTimeToMillisWithIntDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/nio/file/attribute/FileTime;)I",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef("java/nio/file/attribute/FileTime", "toMillis", "()I")),
            plain(2, 172, "ireturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokevirtual java/nio/file/attribute/FileTime.toMillis()I");
            });
    }

    @Test
    void rejectsUnsupportedIterableSpliteratorCall() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/Iterable;)Ljava/util/Spliterator;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeInterface(1, new MethodRef("java/lang/Iterable", "spliterator", "()Ljava/util/Spliterator;")),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokeinterface java/lang/Iterable.spliterator()Ljava/util/Spliterator;");
            });
    }

    @Test
    void lowersDurationOfMillisToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)Ljava/time/Duration;",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/time/Duration", "ofMillis", "(J)Ljava/time/Duration;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_duration_of_millis", List.of(IrExpression.longLocal("arg0"))))
        );
    }

    @Test
    void lowersDurationOfSecondsToRuntimeCall() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(J)Ljava/time/Duration;",
            2,
            2,
            plain(0, 30, "lload_0"),
            invokeStatic(1, new MethodRef("java/time/Duration", "ofSeconds", "(J)Ljava/time/Duration;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.objectCall("javan_duration_of_seconds", List.of(IrExpression.longLocal("arg0"))))
        );
    }

    @Test
    void ignoresSyntheticEnumSwitchMapHandlerForDollarOneClass() {
        final MethodInfo clinit = methodWithHandlers(
            0x0008,
            "<clinit>",
            "()V",
            2,
            2,
            List.of(new CodeException(0, 4, 4, Optional.of("java/lang/NoSuchFieldError"))),
            classInstruction(0, 187, "new", "java/lang/NoSuchFieldError"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/lang/NoSuchFieldError", "<init>", "()V")),
            plain(3, 191, "athrow"),
            plain(4, 76, "astore_1"),
            plain(5, 177, "return")
        );
        final ClassFile synthetic = classFile("com/acme/SwitchMap$1", "java/lang/Object", 0, List.of(), List.of(), List.of(clinit));

        final IrProgram program = lowerProgram("com/acme/SwitchMap$1", clinit, synthetic);

        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.jump("label_4"),
            IrInstruction.label("label_4"),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void ignoresSyntheticEnumSwitchMapHandlerForGenericAstoreOpcode() {
        assertIgnoredSyntheticEnumSwitchMapHandler("com/acme/SwitchMap$1", 58, "astore", 1);
    }

    @Test
    void ignoresSyntheticEnumSwitchMapHandlerForAstoreZeroOpcode() {
        assertIgnoredSyntheticEnumSwitchMapHandler("com/acme/SwitchMap$1", 75, "astore_0");
    }

    @Test
    void ignoresSyntheticEnumSwitchMapHandlerForAstoreTwoOpcode() {
        assertIgnoredSyntheticEnumSwitchMapHandler("com/acme/SwitchMap$1", 77, "astore_2");
    }

    @Test
    void ignoresSyntheticEnumSwitchMapHandlerForAstoreThreeOpcode() {
        assertIgnoredSyntheticEnumSwitchMapHandler("com/acme/SwitchMap$1", 78, "astore_3");
    }

    @Test
    void doesNotTreatSingleCharacterClassNameAsSyntheticSwitchMapInitializer() {
        assertNonSyntheticEnumSwitchMapHandlerFallsBack("A");
    }

    @Test
    void doesNotTreatPlainSuffixClassNameAsSyntheticSwitchMapInitializer() {
        assertNonSyntheticEnumSwitchMapHandlerFallsBack("AB");
    }

    @Test
    void doesNotTreatDollarTwoClassNameAsSyntheticSwitchMapInitializer() {
        assertNonSyntheticEnumSwitchMapHandlerFallsBack("com/acme/SwitchMap$2");
    }

    @Test
    void ignoresEnumSwitchMapHandlerForSwitchMapFieldAndPopHandler() {
        final MethodInfo clinit = methodWithHandlers(
            0x0008,
            "<clinit>",
            "()V",
            2,
            0,
            List.of(new CodeException(0, 4, 4, Optional.of("java/lang/NoSuchFieldError"))),
            classInstruction(0, 187, "new", "java/lang/NoSuchFieldError"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/lang/NoSuchFieldError", "<init>", "()V")),
            plain(3, 191, "athrow"),
            plain(4, 87, "pop"),
            plain(5, 177, "return")
        );
        final ClassFile switchMapHolder = classFile(
            "com/acme/SwitchMapHolder",
            "java/lang/Object",
            0,
            List.of(),
            List.of(new FieldInfo(0x0008, "$SwitchMap$com$acme$Mode", "[I")),
            List.of(clinit)
        );

        final IrProgram program = lowerProgram("com/acme/SwitchMapHolder", clinit, switchMapHolder);

        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.jump("label_4"),
            IrInstruction.label("label_4"),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void doesNotIgnoreSyntheticEnumSwitchMapHandlerWithWrongCatchType() {
        final MethodInfo clinit = methodWithHandlers(
            0x0008,
            "<clinit>",
            "()V",
            2,
            2,
            List.of(new CodeException(0, 4, 4, Optional.of("java/lang/RuntimeException"))),
            classInstruction(0, 187, "new", "java/lang/NoSuchFieldError"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/lang/NoSuchFieldError", "<init>", "()V")),
            plain(3, 191, "athrow"),
            plain(4, 76, "astore_1"),
            plain(5, 177, "return")
        );
        final ClassFile synthetic = classFile("com/acme/SwitchMap$1", "java/lang/Object", 0, List.of(), List.of(), List.of(clinit));

        final IrProgram program = lowerProgram("com/acme/SwitchMap$1", clinit, synthetic);

        assertThat(program.functions().getFirst().locals()).containsExactly(new IrLocal(IrType.OBJECT, "local1"));
        assertThat(program.functions().getFirst().instructions().getFirst()).satisfies(instruction -> {
            assertThat(instruction.op()).isEqualTo(IrInstruction.Op.PANIC);
            assertThat(instruction.expression()).contains(IrExpression.objectNull());
        });
        assertThat(program.functions().getFirst().instructions()).endsWith(
            IrInstruction.label("label_4"),
            IrInstruction.assignObject("local1", IrExpression.objectNull()),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void lowersInvokeDynamicPrimitiveConcatToStringConcatExpression() {
        final String recipe = "values \u0001:\u0001:\u0001:\u0001:\u0001:\u0001:\u0001";
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(BSCZJFD)Ljava/lang/String;",
            9,
            9,
            plain(0, 26, "iload_0"),
            plain(1, 27, "iload_1"),
            plain(2, 28, "iload_2"),
            plain(3, 29, "iload_3"),
            plainOperands(4, 22, "lload", 4),
            plainOperands(5, 23, "fload", 6),
            plainOperands(6, 24, "dload", 7),
            invokeDynamic(7, new DynamicRef(
                "makeConcatWithConstants",
                "(BSCZJFD)Ljava/lang/String;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "",
                List.of(recipe)
            )),
            plain(8, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.stringConcat(
                recipe,
                List.of(
                    IrExpression.objectCall("javan_string_value_of_int", List.of(IrExpression.intLocal("arg0"))),
                    IrExpression.objectCall("javan_string_value_of_int", List.of(IrExpression.intLocal("arg1"))),
                    IrExpression.objectCall("javan_string_value_of_char", List.of(IrExpression.intLocal("arg2"))),
                    IrExpression.objectCall("javan_string_value_of_bool", List.of(IrExpression.intLocal("arg3"))),
                    IrExpression.objectCall("javan_string_value_of_long", List.of(IrExpression.longLocal("arg4"))),
                    IrExpression.objectCall("javan_string_value_of_float", List.of(IrExpression.floatLocal("arg5"))),
                    IrExpression.objectCall("javan_string_value_of_double", List.of(IrExpression.doubleLocal("arg6")))
                )
            ))
        );
    }

    @Test
    void lowersInvokeDynamicMakeConcatObjectAndArrayArgumentsToPlaceholderRecipe() {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;[I[[Ljava/lang/Object;)Ljava/lang/String;",
            3,
            3,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            plain(2, 44, "aload_2"),
            invokeDynamic(3, new DynamicRef(
                "makeConcat",
                "(Ljava/lang/String;[I[[Ljava/lang/Object;)Ljava/lang/String;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcat",
                "",
                List.of()
            )),
            plain(4, 176, "areturn")
        ));

        assertThat(function.instructions()).containsExactly(
            IrInstruction.returnObject(IrExpression.stringConcat(
                "\u0001\u0001\u0001",
                List.of(
                    IrExpression.objectLocal("arg0"),
                    IrExpression.objectLocal("arg1"),
                    IrExpression.objectLocal("arg2")
                )
            ))
        );
    }

    @Test
    void rejectsInvokeDynamicConcatWithUnsupportedBootstrapOwner() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/String;",
            0,
            0,
            invokeDynamic(0, new DynamicRef(
                "makeConcat",
                "()Ljava/lang/String;",
                "com/acme/Bootstrap",
                "makeConcat",
                "",
                List.of()
            )),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokedynamic");
            });
    }

    @Test
    void rejectsInvokeDynamicConcatWithNonStringReturnType() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "()Ljava/lang/Object;",
            0,
            0,
            invokeDynamic(0, new DynamicRef(
                "makeConcat",
                "()Ljava/lang/Object;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcat",
                "",
                List.of()
            )),
            plain(1, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokedynamic");
            });
    }

    @Test
    void rejectsInvokeDynamicConcatWithMissingRecipeArgument() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/lang/String;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeDynamic(1, new DynamicRef(
                "makeConcatWithConstants",
                "(I)Ljava/lang/String;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "",
                List.of()
            )),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokedynamic");
            });
    }

    @Test
    void rejectsInvokeDynamicConcatWithConstantPlaceholderRecipe() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(I)Ljava/lang/String;",
            1,
            1,
            plain(0, 26, "iload_0"),
            invokeDynamic(1, new DynamicRef(
                "makeConcatWithConstants",
                "(I)Ljava/lang/String;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "",
                List.of("value \u0002")
            )),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokedynamic");
            });
    }

    @Test
    void rejectsInvokeDynamicConcatWithMalformedObjectDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "(Ljava/lang/String;)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeDynamic(1, new DynamicRef(
                "makeConcat",
                "(Ljava/lang/String)Ljava/lang/String;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcat",
                "",
                List.of()
            )),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokedynamic");
            });
    }

    @Test
    void rejectsInvokeDynamicConcatWithMalformedArrayDescriptor() {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            "([I)Ljava/lang/String;",
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeDynamic(1, new DynamicRef(
                "makeConcat",
                "([)Ljava/lang/String;",
                "java/lang/invoke/StringConcatFactory",
                "makeConcat",
                "",
                List.of()
            )),
            plain(2, 176, "areturn")
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN040");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokedynamic");
            });
    }

    private static void assertArrayCloneLowering(final String descriptor, final String owner, final String helper) {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            descriptor,
            1,
            1,
            plain(0, 42, "aload_0"),
            invokeVirtual(1, new MethodRef(owner, "clone", "()Ljava/lang/Object;")),
            plain(2, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject("object0", IrExpression.objectLocal("arg0")),
            IrInstruction.returnObject(IrExpression.objectCall(
                helper,
                List.of(
                    IrExpression.objectLocal("object0"),
                    IrExpression.intCall("javan_array_length", List.of(IrExpression.objectLocal("object0")))
                )
            ))
        );
    }

    private static void assertStringBuilderAppendObject(
        final String descriptor,
        final String methodDescriptor,
        final String helper
    ) {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            descriptor,
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 43, "aload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "append", methodDescriptor)),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    helper,
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.objectLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    private static void assertStringBuilderAppendInt(
        final String descriptor,
        final String methodDescriptor,
        final String helper
    ) {
        final IrFunction function = lowerMain(method(
            0x0008,
            "main",
            descriptor,
            2,
            2,
            plain(0, 42, "aload_0"),
            plain(1, 27, "iload_1"),
            invokeVirtual(2, new MethodRef("java/lang/StringBuilder", "append", methodDescriptor)),
            plain(3, 176, "areturn")
        ));

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.OBJECT, "object0"));
        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignObject(
                "object0",
                IrExpression.objectCall(
                    helper,
                    List.of(IrExpression.objectLocal("arg0"), IrExpression.intLocal("arg1"))
                )
            ),
            IrInstruction.returnObject(IrExpression.objectLocal("object0"))
        );
    }

    private static void assertZeroVarargsIntCall(
        final IrFunction function,
        final String symbol,
        final IrExpression firstArgument
    ) {
        assertThat(function.instructions()).hasSize(2);
        final IrInstruction allocation = function.instructions().get(0);
        assertThat(allocation.op()).isEqualTo(IrInstruction.Op.ASSIGN_OBJECT);
        assertThat(allocation.expression()).contains(IrExpression.objectArrayAllocation(IrExpression.intLiteral(0)));
        final String arrayLocal = allocation.value().orElseThrow();
        assertThat(function.instructions().get(1)).isEqualTo(IrInstruction.returnInt(IrExpression.intCall(
            symbol,
            List.of(firstArgument, IrExpression.objectLocal(arrayLocal))
        )));
    }

    private static void assertZeroVarargsObjectCall(
        final IrFunction function,
        final String symbol,
        final IrExpression firstArgument
    ) {
        assertThat(function.instructions()).hasSize(3);
        final IrInstruction allocation = function.instructions().get(0);
        assertThat(allocation.op()).isEqualTo(IrInstruction.Op.ASSIGN_OBJECT);
        assertThat(allocation.expression()).contains(IrExpression.objectArrayAllocation(IrExpression.intLiteral(0)));
        final String arrayLocal = allocation.value().orElseThrow();
        final IrInstruction call = function.instructions().get(1);
        assertThat(call.op()).isEqualTo(IrInstruction.Op.ASSIGN_OBJECT);
        assertThat(call.expression()).contains(IrExpression.objectCall(
            symbol,
            List.of(firstArgument, IrExpression.objectLocal(arrayLocal))
        ));
        final String resultLocal = call.value().orElseThrow();
        assertThat(function.instructions().get(2)).isEqualTo(IrInstruction.returnObject(IrExpression.objectLocal(resultLocal)));
    }

    private static void assertWrongCallArgument(
        final Instruction producer,
        final String sinkDescriptor,
        final String expectedReason
    ) {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            producer,
            invokeStatic(1, new MethodRef("com/acme/Sink", "accept", sinkDescriptor)),
            plain(2, 177, "return")
        ), sinkClass()))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().subject()).isEqualTo("invokestatic com/acme/Sink.accept" + sinkDescriptor);
                assertThat(exception.diagnostic().reason()).isEqualTo(expectedReason);
            });
    }

    private static void assertMissingPrimitiveReturnValue(
        final int opcode,
        final String mnemonic,
        final String descriptor,
        final String expectedReason
    ) {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            descriptor,
            0,
            0,
            plain(0, opcode, mnemonic)
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().subject()).isEqualTo(mnemonic);
                assertThat(exception.diagnostic().reason()).isEqualTo(expectedReason);
            });
    }

    private static void assertWrongReturnValue(
        final Instruction producer,
        final int opcode,
        final String mnemonic,
        final String descriptor,
        final String expectedReason
    ) {
        assertThatThrownBy(() -> lowerMain(method(
            0x0008,
            "main",
            descriptor,
            1,
            0,
            producer,
            plain(1, opcode, mnemonic)
        )))
            .isInstanceOfSatisfying(DiagnosticException.class, exception -> {
                assertThat(exception.diagnostic().code()).isEqualTo("JAVAN049");
                assertThat(exception.diagnostic().message()).isEqualTo("bytecode stack shape is not supported");
                assertThat(exception.diagnostic().subject()).isEqualTo(mnemonic);
                assertThat(exception.diagnostic().reason()).isEqualTo(expectedReason);
            });
    }

    private static void assertNoSynthesizedBranchValue(final IrFunction function) {
        for (final IrLocal local : function.locals()) {
            assertThat(local.name()).doesNotStartWith("branchValue");
        }
        for (final IrInstruction instruction : function.instructions()) {
            assertThat(instruction.value().orElse("")).doesNotStartWith("branch_value_");
            assertThat(instruction.value().orElse("")).doesNotStartWith("guarded_value_");
        }
    }

    private static IrFunction lowerMain(final MethodInfo method) {
        return lower(method);
    }

    private static IrFunction lower(final MethodInfo main, final ClassFile... extraClasses) {
        return lowerProgram(main, extraClasses).functions().getFirst();
    }

    private static IrProgram lowerProgram(final MethodInfo main, final ClassFile... extraClasses) {
        return lowerProgram("com/acme/Main", main, extraClasses);
    }

    private static IrProgram lowerProgram(final String entryClassName, final MethodInfo main, final ClassFile... extraClasses) {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put(entryClassName, classFile(entryClassName, "java/lang/Object", 0, List.of(), List.of(), List.of(main)));
        for (final ClassFile extraClass : extraClasses) {
            classes.put(extraClass.name(), extraClass);
        }
        final EntryPoint entryPoint = new EntryPoint(entryClassName, main.name(), main.descriptor());
        return new BytecodeToIR().lower(classes, new CallGraph(entryPoint, List.of(entryPoint), List.of()));
    }

    private static ClassFile classFile(
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final List<FieldInfo> fields,
        final List<MethodInfo> methods
    ) {
        return new ClassFile(69, name, superName, accessFlags, interfaces, fields, methods, Path.of(name + ".class"), true);
    }

    private static ClassFile interfaceType(final String name, final String methodName, final String descriptor) {
        return classFile(
            name,
            "java/lang/Object",
            0x0200,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0x0401, methodName, descriptor, Optional.empty()))
        );
    }

    private static ClassFile implementationType(
        final String name,
        final String interfaceName,
        final String methodName,
        final String descriptor
    ) {
        return classFile(
            name,
            "java/lang/Object",
            0,
            List.of(interfaceName),
            List.of(),
            List.of(new MethodInfo(0, methodName, descriptor, Optional.empty()))
        );
    }

    private static ClassFile sinkClass() {
        return classFile("com/acme/Sink", "java/lang/Object", 0, List.of(), List.of(), List.of());
    }

    private static String symbol(final String className, final String methodName, final String descriptor) {
        return BytecodeToIR.symbol(new EntryPoint(className, methodName, descriptor));
    }

    private static MethodInfo method(
        final int accessFlags,
        final String name,
        final String descriptor,
        final int maxStack,
        final int maxLocals,
        final Instruction... instructions
    ) {
        return new MethodInfo(
            accessFlags,
            name,
            descriptor,
            Optional.of(new CodeAttribute(maxStack, maxLocals, new byte[0], 0, List.of(instructions)))
        );
    }

    private static MethodInfo methodWithHandlers(
        final int accessFlags,
        final String name,
        final String descriptor,
        final int maxStack,
        final int maxLocals,
        final List<CodeException> exceptionTable,
        final Instruction... instructions
    ) {
        return new MethodInfo(
            accessFlags,
            name,
            descriptor,
            Optional.of(new CodeAttribute(maxStack, maxLocals, new byte[0], exceptionTable.size(), exceptionTable, List.of(instructions)))
        );
    }

    private static void assertIgnoredSyntheticEnumSwitchMapHandler(
        final String className,
        final int opcode,
        final String mnemonic,
        final int... operands
    ) {
        final IrProgram program = lowerProgram(
            className,
            syntheticEnumSwitchMapInitializer(opcode, mnemonic, operands),
            classFile(className, "java/lang/Object", 0, List.of(), List.of(), List.of(syntheticEnumSwitchMapInitializer(opcode, mnemonic, operands)))
        );

        assertThat(program.functions().getFirst().locals()).isEmpty();
        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.jump("label_4"),
            IrInstruction.label("label_4"),
            IrInstruction.returnVoid()
        );
    }

    private static void assertNonSyntheticEnumSwitchMapHandlerFallsBack(final String className) {
        final IrProgram program = lowerProgram(
            className,
            syntheticEnumSwitchMapInitializer(76, "astore_1"),
            classFile(className, "java/lang/Object", 0, List.of(), List.of(), List.of(syntheticEnumSwitchMapInitializer(76, "astore_1")))
        );

        assertThat(program.functions().getFirst().locals()).containsExactly(new IrLocal(IrType.OBJECT, "local1"));
        assertThat(program.functions().getFirst().instructions()).containsExactly(
            IrInstruction.jump("label_4"),
            IrInstruction.label("label_4"),
            IrInstruction.assignObject("local1", IrExpression.objectNull()),
            IrInstruction.returnVoid()
        );
    }

    private static MethodInfo syntheticEnumSwitchMapInitializer(
        final int opcode,
        final String mnemonic,
        final int... operands
    ) {
        return methodWithHandlers(
            0x0008,
            "<clinit>",
            "()V",
            2,
            2,
            List.of(new CodeException(0, 4, 4, Optional.of("java/lang/NoSuchFieldError"))),
            classInstruction(0, 187, "new", "java/lang/NoSuchFieldError"),
            plain(1, 89, "dup"),
            invokeSpecial(2, new MethodRef("java/lang/NoSuchFieldError", "<init>", "()V")),
            plain(3, 191, "athrow"),
            plainOperands(4, opcode, mnemonic, operands),
            plain(5, 177, "return")
        );
    }

    private static Instruction plain(final int offset, final int opcode, final String mnemonic) {
        return plainOperands(offset, opcode, mnemonic);
    }

    private static Instruction plainOperands(final int offset, final int opcode, final String mnemonic, final int... operands) {
        final byte[] bytes = new byte[operands.length];
        for (int index = 0; index < operands.length; index++) {
            bytes[index] = (byte) operands[index];
        }
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            bytes,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static int[] tableSwitchOperands(
        final int instructionOffset,
        final int defaultTarget,
        final int low,
        final int high,
        final int... targets
    ) {
        final int padding = switchPadding(instructionOffset);
        final int[] result = new int[padding + 12 + targets.length * 4];
        putInt(result, padding, defaultTarget);
        putInt(result, padding + 4, low);
        putInt(result, padding + 8, high);
        int offset = padding + 12;
        for (final int target : targets) {
            putInt(result, offset, target);
            offset += 4;
        }
        return result;
    }

    private static int[] lookupSwitchOperands(final int instructionOffset, final int defaultTarget, final int... pairs) {
        final int padding = switchPadding(instructionOffset);
        final int[] result = new int[padding + 8 + pairs.length * 4];
        putInt(result, padding, defaultTarget);
        putInt(result, padding + 4, pairs.length / 2);
        int offset = padding + 8;
        for (final int value : pairs) {
            putInt(result, offset, value);
            offset += 4;
        }
        return result;
    }

    private static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor - offset - 1;
    }

    private static void putInt(final int[] target, final int offset, final int value) {
        target[offset] = (value >>> 24) & 0xFF;
        target[offset + 1] = (value >>> 16) & 0xFF;
        target[offset + 2] = (value >>> 8) & 0xFF;
        target[offset + 3] = value & 0xFF;
    }

    private static Instruction getStatic(final int offset, final FieldRef fieldRef) {
        return fieldInstruction(offset, 178, "getstatic", fieldRef);
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

    private static Instruction invokeVirtual(final int offset, final MethodRef methodRef) {
        return new Instruction(
            offset,
            182,
            "invokevirtual",
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

    private static Instruction invokeSpecial(final int offset, final MethodRef methodRef) {
        return new Instruction(
            offset,
            183,
            "invokespecial",
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

    private static Instruction invokeStatic(final int offset, final MethodRef methodRef) {
        return new Instruction(
            offset,
            184,
            "invokestatic",
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

    private static Instruction invokeInterface(final int offset, final MethodRef methodRef) {
        return new Instruction(
            offset,
            185,
            "invokeinterface",
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

    private static Instruction invokeDynamic(final int offset, final DynamicRef dynamicRef) {
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

    private static Instruction stringConstant(final int offset, final String value) {
        return new Instruction(
            offset,
            18,
            "ldc",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(value),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
