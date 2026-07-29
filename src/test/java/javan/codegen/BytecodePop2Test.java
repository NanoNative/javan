package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.BytecodeSupport;
import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.verify.DiagnosticException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class BytecodePop2Test {
    @Test
    void categoryTwoLongCallIsDiscardedExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "value", "()J", 2, 0, longConstant(0, 7L), plain(1, 173, "lreturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "value", "()J")),
            plain(1, 88, "pop2"),
            plain(2, 177, "return")
        ), values);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignLong(
                "long0",
                IrExpression.longCall("javan_com_acme_Values_value___J", List.of())
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void categoryTwoDoubleCallIsDiscardedExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "value", "()D", 2, 0, plain(0, 15, "dconst_1"), plain(1, 175, "dreturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "value", "()D")),
            plain(1, 88, "pop2"),
            plain(2, 177, "return")
        ), values);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignDouble(
                "double0",
                IrExpression.doubleCall("javan_com_acme_Values_value___D", List.of())
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void twoCategoryOneCallsAreDiscardedInProducerOrder() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "first", "()I", 1, 0, intConstant(0, 1), plain(1, 172, "ireturn")),
            method(0x0008, "second", "()I", 1, 0, intConstant(0, 2), plain(1, 172, "ireturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "first", "()I")),
            invokeStatic(1, new MethodRef("com/acme/Values", "second", "()I")),
            plain(2, 88, "pop2"),
            plain(3, 177, "return")
        ), values);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_com_acme_Values_first___I", List.of())
            ),
            IrInstruction.assignInt(
                "int1",
                IrExpression.intCall("javan_com_acme_Values_second___I", List.of())
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void duplicatedCategoryOneCallIsDiscardedExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "value", "()I", 1, 0, intConstant(0, 1), plain(1, 172, "ireturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "value", "()I")),
            plain(1, 89, "dup"),
            plain(2, 88, "pop2"),
            plain(3, 177, "return")
        ), values);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignInt(
                "int0",
                IrExpression.intCall("javan_com_acme_Values_value___I", List.of())
            ),
            IrInstruction.returnVoid()
        );
    }

    @Test
    void categoryTwoTopLeavesLowerValueAvailable() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "value", "()J", 2, 0, longConstant(0, 7L), plain(1, 173, "lreturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()I",
            3,
            0,
            intConstant(0, 1),
            invokeStatic(1, new MethodRef("com/acme/Values", "value", "()J")),
            plain(2, 88, "pop2"),
            plain(3, 172, "ireturn")
        ), values);

        assertThat(function.instructions()).containsExactly(
            IrInstruction.assignLong(
                "long0",
                IrExpression.longCall("javan_com_acme_Values_value___J", List.of())
            ),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        );
    }

    @Test
    void opcodeIsPublishedAsNativeSupported() {
        assertThat(new Pop2Support(
            BytecodeSupport.classify(88),
            BytecodeSupport.nativeSupportedOpcodes().contains(Integer.valueOf(88))
        )).isEqualTo(new Pop2Support(BytecodeSupport.Status.NATIVE_SUPPORTED, true));
    }

    @Test
    void emptyStackRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            0,
            0,
            plain(0, 88, "pop2"),
            plain(1, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("pop2 requires either one category-2 value or two category-1 values");
    }

    @Test
    void singleCategoryOneValueRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            1,
            0,
            intConstant(0, 1),
            plain(1, 88, "pop2"),
            plain(2, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("pop2 requires two category-1 values, but only one is available");
    }

    @Test
    void categoryOneAboveCategoryTwoRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            3,
            0,
            longConstant(0, 1L),
            intConstant(1, 2),
            plain(2, 88, "pop2"),
            plain(3, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("pop2 cannot pair a category-1 top value with a category-2 value beneath it");
    }

    private static IrFunction lower(final MethodInfo main, final ClassFile... extraClasses) {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", type("com/acme/Main", main));
        for (final ClassFile extraClass : extraClasses) {
            classes.put(extraClass.name(), extraClass);
        }
        final EntryPoint entry = new EntryPoint("com/acme/Main", main.name(), main.descriptor());
        return new BytecodeToIR().lower(classes, new CallGraph(entry, List.of(entry), List.of()))
            .functions().stream()
            .filter(function -> function.name().equals(main.name()))
            .findFirst()
            .orElseThrow();
    }

    private static ClassFile type(final String name, final MethodInfo... methods) {
        return new ClassFile(
            69,
            name,
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(methods),
            Path.of(name + ".class"),
            true
        );
    }

    private static MethodInfo method(
        final int flags,
        final String name,
        final String descriptor,
        final int maxStack,
        final int maxLocals,
        final Instruction... instructions
    ) {
        return new MethodInfo(
            flags,
            name,
            descriptor,
            Optional.of(new CodeAttribute(maxStack, maxLocals, new byte[0], 0, List.of(instructions)))
        );
    }

    private static Instruction plain(final int offset, final int opcode, final String mnemonic) {
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

    private static Instruction invokeStatic(final int offset, final MethodRef target) {
        return new Instruction(
            offset,
            184,
            "invokestatic",
            new byte[0],
            Optional.of(target),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction longConstant(final int offset, final long value) {
        return new Instruction(
            offset,
            20,
            "ldc2_w",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(value),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(5)
        );
    }

    private static Instruction intConstant(final int offset, final int value) {
        return new Instruction(
            offset,
            18,
            "ldc",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(value),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(3)
        );
    }

    private record Pop2Support(BytecodeSupport.Status status, boolean published) {
    }
}
