package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.BootstrapArgument;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class BytecodeDupX2Test {
    @Test
    void threeCategoryOneCallsMaterializeBottomToTopExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "first", "()I", 1, 0, intConstant(0, 1), plain(1, 172, "ireturn")),
            method(0x0008, "second", "()I", 1, 0, intConstant(0, 2), plain(1, 172, "ireturn")),
            method(0x0008, "third", "()I", 1, 0, intConstant(0, 3), plain(1, 172, "ireturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()I",
            4,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "first", "()I")),
            invokeStatic(1, new MethodRef("com/acme/Values", "second", "()I")),
            invokeStatic(2, new MethodRef("com/acme/Values", "third", "()I")),
            plain(3, 91, "dup_x2"),
            plain(4, 100, "isub"),
            plain(5, 100, "isub"),
            plain(6, 100, "isub"),
            plain(7, 172, "ireturn")
        ), values);

        assertThat(new DupEvidence(function.locals(), function.instructions())).isEqualTo(new DupEvidence(
            List.of(
                new IrLocal(IrType.INT, "int0"),
                new IrLocal(IrType.INT, "int1"),
                new IrLocal(IrType.INT, "int2")
            ),
            List.of(
                IrInstruction.assignInt("int0", IrExpression.intCall("javan_com_acme_Values_first___I", List.of())),
                IrInstruction.assignInt("int1", IrExpression.intCall("javan_com_acme_Values_second___I", List.of())),
                IrInstruction.assignInt("int2", IrExpression.intCall("javan_com_acme_Values_third___I", List.of())),
                IrInstruction.returnInt(IrExpression.intBinary(
                    "-",
                    IrExpression.intLocal("int2"),
                    IrExpression.intBinary(
                        "-",
                        IrExpression.intLocal("int0"),
                        IrExpression.intBinary("-", IrExpression.intLocal("int1"), IrExpression.intLocal("int2"))
                    )
                ))
            )
        ));
    }

    @Test
    void categoryOneAboveCategoryTwoCallsMaterializeLowerThenTopExactlyOnce() {
        final ClassFile values = type(
            "com/acme/Values",
            method(0x0008, "left", "()J", 2, 0, longConstant(0, 10L), plain(3, 173, "lreturn")),
            method(0x0008, "right", "()I", 1, 0, intConstant(0, 3), plain(1, 172, "ireturn"))
        );
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()I",
            5,
            0,
            invokeStatic(0, new MethodRef("com/acme/Values", "left", "()J")),
            invokeStatic(1, new MethodRef("com/acme/Values", "right", "()I")),
            plain(2, 91, "dup_x2"),
            plain(3, 133, "i2l"),
            plain(4, 97, "ladd"),
            plain(5, 136, "l2i"),
            plain(6, 96, "iadd"),
            plain(7, 172, "ireturn")
        ), values);

        assertThat(function.locals()).containsExactly(new IrLocal(IrType.LONG, "long0"), new IrLocal(IrType.INT, "int1"));
    }

    @Test
    void formOneSystemOutMaterializesOnceAndDirectInvocationReusesLocal() {
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            4,
            0,
            plain(0, 1, "aconst_null"),
            stringConstant(1, "unused"),
            getStatic(2, new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            plain(3, 91, "dup_x2"),
            plain(4, 87, "pop"),
            plain(5, 87, "pop"),
            plain(6, 87, "pop"),
            stringConstant(7, "form-one-out"),
            invokeVirtual(8, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            plain(9, 177, "return")
        ));

        assertThat(new DupEvidence(function.locals(), function.instructions())).isEqualTo(new DupEvidence(
            List.of(new IrLocal(IrType.OBJECT, "stackDup0")),
            List.of(
                IrInstruction.assignObject("stackDup0", IrExpression.objectCall("javan_system_out", List.of())),
                IrInstruction.callStaticVoid(
                    "javan_printstream_println_object",
                    List.of(IrExpression.objectLocal("stackDup0"), IrExpression.stringLiteral("form-one-out"))
                ),
                IrInstruction.returnVoid()
            )
        ));
    }

    @Test
    void formTwoSystemErrMaterializesOnceAndDirectInvocationReusesLocal() {
        final IrFunction function = lower(method(
            0x0008,
            "main",
            "()V",
            4,
            0,
            longConstant(0, 7L),
            getStatic(1, new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            plain(2, 91, "dup_x2"),
            plain(3, 87, "pop"),
            plain(4, 136, "l2i"),
            plain(5, 87, "pop"),
            stringConstant(6, "form-two-err"),
            invokeVirtual(7, new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            plain(8, 177, "return")
        ));

        assertThat(new DupEvidence(function.locals(), function.instructions())).isEqualTo(new DupEvidence(
            List.of(new IrLocal(IrType.OBJECT, "stackDup0")),
            List.of(
                IrInstruction.assignObject("stackDup0", IrExpression.objectCall("javan_system_err", List.of())),
                IrInstruction.callStaticVoid("javan_l2i", List.of(IrExpression.longLiteral(7L))),
                IrInstruction.callStaticVoid(
                    "javan_printstream_println_object",
                    List.of(IrExpression.objectLocal("stackDup0"), IrExpression.stringLiteral("form-two-err"))
                ),
                IrInstruction.returnVoid()
            )
        ));
    }

    @Test
    void opcodeIsPublishedAsNativeSupported() {
        assertThat(new DupX2Support(
            BytecodeSupport.classify(91),
            BytecodeSupport.nativeSupportedOpcodes().contains(Integer.valueOf(91))
        )).isEqualTo(new DupX2Support(BytecodeSupport.Status.NATIVE_SUPPORTED, true));
    }

    @Test
    void emptyStackRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            0,
            0,
            plain(0, 91, "dup_x2"),
            plain(1, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup_x2 requires a category-1 top value above either one category-2 value or two category-1 values");
    }

    @Test
    void categoryTwoTopRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            3,
            0,
            intConstant(0, 0),
            longConstant(1, 0L),
            plain(2, 91, "dup_x2"),
            plain(3, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup_x2 requires a category-1 top value, but found long");
    }

    @Test
    void missingSecondCategoryOneValueRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            2,
            0,
            intConstant(0, 0),
            intConstant(1, 1),
            plain(2, 91, "dup_x2"),
            plain(3, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup_x2 requires two category-1 values beneath its category-1 top value, but only one is available");
    }

    @Test
    void categoryTwoBelowIntermediateValueRejectsDeterministically() {
        assertThatThrownBy(() -> lower(method(
            0x0008,
            "main",
            "()V",
            4,
            0,
            longConstant(0, 0L),
            intConstant(1, 0),
            intConstant(2, 1),
            plain(3, 91, "dup_x2"),
            plain(4, 177, "return")
        )))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("dup_x2 cannot use a category-2 value beneath a category-1 intermediate value");
    }

    @Test
    void deferredLambdaTopPermutesWithoutRuntimeMaterialization() {
        final MethodInfo main = method(
            0x0008,
            "main",
            "()I",
            4,
            0,
            intConstant(0, 0),
            intConstant(1, 1),
            invokeDynamic(2, functionLambda()),
            plain(3, 91, "dup_x2"),
            plain(4, 87, "pop"),
            plain(5, 87, "pop"),
            plain(6, 87, "pop"),
            plain(7, 87, "pop"),
            intConstant(8, 7),
            plain(9, 172, "ireturn")
        );
        final MethodInfo implementation = method(
            0x0008,
            "lambda$main$0",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            1,
            1,
            plain(0, 42, "aload_0"),
            plain(1, 176, "areturn")
        );

        assertThatCode(() -> lowerMainClass(main, implementation)).doesNotThrowAnyException();
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

    private static IrFunction lowerMainClass(final MethodInfo main, final MethodInfo implementation) {
        final ClassFile owner = type("com/acme/Main", main, implementation);
        final EntryPoint entry = new EntryPoint(owner.name(), main.name(), main.descriptor());
        return new BytecodeToIR().lower(Map.of(owner.name(), owner), new CallGraph(entry, List.of(entry), List.of()))
            .functions().getFirst();
    }

    private static DynamicRef functionLambda() {
        final List<BootstrapArgument> arguments = List.of(
            BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            BootstrapArgument.methodHandle(
                6,
                new MethodRef("com/acme/Main", "lambda$main$0", "(Ljava/lang/Object;)Ljava/lang/Object;")
            ),
            BootstrapArgument.methodType("(Ljava/lang/Object;)Ljava/lang/Object;")
        );
        return new DynamicRef(
            "apply",
            "()Ljava/util/function/Function;",
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                + "Ljava/lang/invoke/CallSite;",
            arguments.stream().map(BootstrapArgument::text).toList(),
            arguments
        );
    }

    private static ClassFile type(final String name, final MethodInfo... methods) {
        return new ClassFile(69, name, "java/lang/Object", 0, List.of(), List.of(), List.of(methods), Path.of(name + ".class"), true);
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
        return new Instruction(offset, opcode, mnemonic, new byte[0], Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction getStatic(final int offset, final FieldRef fieldRef) {
        return new Instruction(offset, 178, "getstatic", new byte[0], Optional.empty(), Optional.of(fieldRef),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction invokeVirtual(final int offset, final MethodRef target) {
        return new Instruction(offset, 182, "invokevirtual", new byte[0], Optional.of(target), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction invokeStatic(final int offset, final MethodRef target) {
        return new Instruction(offset, 184, "invokestatic", new byte[0], Optional.of(target), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction invokeDynamic(final int offset, final DynamicRef dynamicRef) {
        return new Instruction(offset, 186, "invokedynamic", new byte[0], Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(dynamicRef));
    }

    private static Instruction longConstant(final int offset, final long value) {
        return new Instruction(offset, 20, "ldc2_w", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(5));
    }

    private static Instruction intConstant(final int offset, final int value) {
        return new Instruction(offset, 18, "ldc", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.of(value), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(3));
    }

    private static Instruction stringConstant(final int offset, final String value) {
        return new Instruction(offset, 18, "ldc", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(value), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private record DupEvidence(List<IrLocal> locals, List<IrInstruction> instructions) {
    }

    private record DupX2Support(BytecodeSupport.Status status, boolean published) {
    }
}
