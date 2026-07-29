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
import javan.ir.IrLocal;
import javan.ir.IrType;
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
final class BytecodeDup2Test {
    @Test
    void twoCategoryOneCallsMaterializeInOperandOrderExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "first", "()I", 1, 0, intConstant(0, 4), plain(1, 172, "ireturn")),
            method(0x0008, "second", "()I", 1, 0, intConstant(0, 3), plain(1, 172, "ireturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()I",
            4,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "first", "()I")),
            invokeStatic(1, new MethodRef("com/acme/Values", "second", "()I")),
            plain(2, 92, "dup2"),
            plain(3, 100, "isub"),
            plain(4, 104, "imul"),
            plain(5, 96, "iadd"),
            plain(6, 172, "ireturn")
        ), values);

        assertThat(new DupEvidence(function.locals(), function.instructions())).isEqualTo(new DupEvidence(
            List.of(new IrLocal(IrType.INT, "int0"), new IrLocal(IrType.INT, "int1")),
            List.of(
                IrInstruction.assignInt("int0", IrExpression.intCall("javan_com_acme_Values_first___I", List.of())),
                IrInstruction.assignInt("int1", IrExpression.intCall("javan_com_acme_Values_second___I", List.of())),
                IrInstruction.returnInt(IrExpression.intBinary(
                    "+",
                    IrExpression.intLocal("int0"),
                    IrExpression.intBinary(
                        "*",
                        IrExpression.intLocal("int1"),
                        IrExpression.intBinary(
                            "-",
                            IrExpression.intLocal("int0"),
                            IrExpression.intLocal("int1")
                        )
                    )
                ))
            )
        ));
    }

    @Test
    void categoryTwoLongCallMaterializesExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "value", "()J", 2, 0, longConstant(0, 7L), plain(1, 173, "lreturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()J",
            4,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "value", "()J")),
            plain(1, 92, "dup2"),
            plain(2, 97, "ladd"),
            plain(3, 173, "lreturn")
        ), values);

        assertThat(new DupEvidence(function.locals(), function.instructions())).isEqualTo(new DupEvidence(
            List.of(new IrLocal(IrType.LONG, "long0")),
            List.of(
                IrInstruction.assignLong("long0", IrExpression.longCall("javan_com_acme_Values_value___J", List.of())),
                IrInstruction.returnLong(IrExpression.longBinary(
                    "+",
                    IrExpression.longLocal("long0"),
                    IrExpression.longLocal("long0")
                ))
            )
        ));
    }

    @Test
    void categoryTwoDoubleCallMaterializesExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "value", "()D", 2, 0, plain(0, 15, "dconst_1"), plain(1, 175, "dreturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()D",
            4,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "value", "()D")),
            plain(1, 92, "dup2"),
            plain(2, 99, "dadd"),
            plain(3, 175, "dreturn")
        ), values);

        assertThat(new DupEvidence(function.locals(), function.instructions())).isEqualTo(new DupEvidence(
            List.of(new IrLocal(IrType.DOUBLE, "double0")),
            List.of(
                IrInstruction.assignDouble(
                    "double0",
                    IrExpression.doubleCall("javan_com_acme_Values_value___D", List.of())
                ),
                IrInstruction.returnDouble(IrExpression.doubleBinary(
                    "+",
                    IrExpression.doubleLocal("double0"),
                    IrExpression.doubleLocal("double0")
                ))
            )
        ));
    }

    @Test
    void opcodeIsPublishedAsNativeSupported() {
        assertThat(new Dup2Support(
            BytecodeSupport.classify(92),
            BytecodeSupport.nativeSupportedOpcodes().contains(Integer.valueOf(92))
        )).isEqualTo(new Dup2Support(BytecodeSupport.Status.NATIVE_SUPPORTED, true));
    }

    @Test
    void emptyStackRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            0,
            0,
            plain(0, 92, "dup2"),
            plain(1, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup2 requires either one category-2 value or two category-1 values");
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
            plain(1, 92, "dup2"),
            plain(2, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup2 requires two category-1 values, but only one is available");
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
            plain(2, 92, "dup2"),
            plain(3, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup2 cannot pair a category-1 top value with a category-2 value beneath it");
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
        return new Instruction(offset, 184, "invokestatic", new byte[0], Optional.of(target), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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

    private record DupEvidence(List<IrLocal> locals, List<IrInstruction> instructions) {
    }

    private record Dup2Support(BytecodeSupport.Status status, boolean published) {
    }
}
