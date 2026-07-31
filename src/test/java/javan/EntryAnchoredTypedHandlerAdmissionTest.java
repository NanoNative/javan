package javan;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.verify.Diagnostic;
import javan.verify.StaticVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class EntryAnchoredTypedHandlerAdmissionTest {
    private static final CodeException HANDLER =
        new CodeException(0, 14, 15, Optional.of("java/lang/RuntimeException"));

    @Test
    void acceptsCanonicalEntryAnchoredTypedHandler() {
        assertThat(verify(canonicalInstructions(), List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .doesNotContain("JAVAN014");
    }

    @Test
    void rejectsMultipleHandlers() {
        assertThat(verify(
            canonicalInstructions(),
            List.of(HANDLER, new CodeException(0, 14, 15, Optional.of("java/lang/Exception"))),
            2,
            2
        )).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void rejectsCatchAllHandler() {
        assertThat(verify(
            canonicalInstructions(),
            List.of(new CodeException(0, 14, 15, Optional.empty())),
            2,
            2
        )).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void rejectsApplicationCatchType() {
        assertThat(verify(
            canonicalInstructions(),
            List.of(new CodeException(0, 14, 15, Optional.of("com/acme/Failure"))),
            2,
            2
        )).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void rejectsNonEntryProtectedRange() {
        assertThat(verify(
            canonicalInstructions(),
            List.of(new CodeException(1, 14, 15, Optional.of("java/lang/RuntimeException"))),
            2,
            2
        )).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void rejectsEmptyProtectedRange() {
        assertThat(verify(
            canonicalInstructions(),
            List.of(new CodeException(0, 0, 15, Optional.of("java/lang/RuntimeException"))),
            2,
            2
        )).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void rejectsHandlerInsideProtectedRange() {
        assertThat(verify(
            canonicalInstructions(),
            List.of(new CodeException(0, 15, 14, Optional.of("java/lang/RuntimeException"))),
            2,
            2
        )).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void rejectsOversizedOperandStack() {
        assertThat(verify(canonicalInstructions(), List.of(HANDLER), 65, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsOversizedLocalTable() {
        assertThat(verify(canonicalInstructions(), List.of(HANDLER), 2, 257))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsHandlerWithoutCatchStore() {
        final List<Instruction> instructions = replace(canonicalInstructions(), 15, plain(15, 0, "nop"));

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsOverwrittenCatchLocal() {
        final List<Instruction> instructions = List.of(
            plain(0, 42, "aload_0"),
            string(1, "cipher"),
            invoke(3, 185, "invokeinterface", new MethodRef(
                "java/util/Map",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )),
            classInstruction(8, "java/lang/String"),
            invoke(11, 184, "invokestatic", new MethodRef(
                "java/lang/Long",
                "parseLong",
                "(Ljava/lang/String;)J"
            )),
            plain(14, 173, "lreturn"),
            plain(15, 76, "astore_1"),
            plain(16, 1, "aconst_null"),
            plain(17, 76, "astore_1"),
            plain(18, 9, "lconst_0"),
            plain(19, 173, "lreturn")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsProtectedRangeWithoutTypedSite() {
        final List<Instruction> instructions = List.of(
            plain(0, 42, "aload_0"),
            string(1, "cipher"),
            plain(3, 87, "pop"),
            plain(4, 87, "pop"),
            plain(5, 9, "lconst_0"),
            plain(14, 173, "lreturn"),
            plain(15, 76, "astore_1"),
            plain(16, 9, "lconst_0"),
            plain(17, 173, "lreturn")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsBranchInsideProtectedRange() {
        final List<Instruction> instructions = List.of(
            branch(0, 167, "goto", 14),
            plain(3, 0, "nop"),
            plain(14, 173, "lreturn"),
            plain(15, 76, "astore_1"),
            plain(16, 9, "lconst_0"),
            plain(17, 173, "lreturn")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsAthrowInsideProtectedRange() {
        final List<Instruction> instructions = List.of(
            plain(0, 42, "aload_0"),
            string(1, "cipher"),
            invoke(3, 185, "invokeinterface", new MethodRef(
                "java/util/Map",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )),
            plain(8, 191, "athrow"),
            plain(14, 173, "lreturn"),
            plain(15, 76, "astore_1"),
            plain(16, 9, "lconst_0"),
            plain(17, 173, "lreturn")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsMonitorInsideProtectedRange() {
        final List<Instruction> instructions = replace(
            canonicalInstructions(),
            11,
            plain(11, 194, "monitorenter")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsConstructorInsideProtectedRange() {
        final List<Instruction> instructions = replace(
            canonicalInstructions(),
            11,
            invoke(11, 183, "invokespecial", new MethodRef("com/acme/Main", "<init>", "()V"))
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsFieldReadInsideProtectedRange() {
        final List<Instruction> instructions = replace(
            canonicalInstructions(),
            11,
            plain(11, 180, "getfield")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsLocalMutationBeforeFinalTypedSite() {
        final List<Instruction> instructions = List.of(
            plain(0, 42, "aload_0"),
            string(1, "cipher"),
            invoke(3, 185, "invokeinterface", new MethodRef(
                "java/util/Map",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )),
            classInstruction(8, "java/lang/String"),
            plain(11, 76, "astore_1"),
            plain(12, 43, "aload_1"),
            invoke(13, 184, "invokestatic", new MethodRef(
                "java/lang/Long",
                "parseLong",
                "(Ljava/lang/String;)J"
            )),
            plain(16, 173, "lreturn"),
            plain(17, 77, "astore_2"),
            plain(18, 9, "lconst_0"),
            plain(19, 173, "lreturn")
        );
        final CodeException handler =
            new CodeException(0, 16, 17, Optional.of("java/lang/RuntimeException"));

        assertThat(verify(instructions, List.of(handler), 2, 3))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsOperandStackCategoryMismatch() {
        final List<Instruction> instructions = replace(
            canonicalInstructions(),
            0,
            plain(0, 3, "iconst_0")
        );

        assertThat(verify(instructions, List.of(HANDLER), 2, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    @Test
    void rejectsDeclaredMaxStackBelowObservedWidth() {
        assertThat(verify(canonicalInstructions(), List.of(HANDLER), 1, 2))
            .extracting(Diagnostic::code)
            .contains("JAVAN014");
    }

    private static List<Diagnostic> verify(
        final List<Instruction> instructions,
        final List<CodeException> handlers,
        final int maxStack,
        final int maxLocals
    ) {
        final MethodInfo method = new MethodInfo(
            0x0008,
            "parse",
            "(Ljava/util/Map;)J",
            Optional.of(new CodeAttribute(
                maxStack,
                maxLocals,
                new byte[16],
                handlers.size(),
                handlers,
                instructions
            ))
        );
        final ClassFile classFile = new ClassFile(
            69,
            "com/acme/Main",
            "java/lang/Object",
            0x0011,
            List.of(),
            List.of(),
            List.of(method),
            Path.of("Main.class"),
            true
        );
        return new StaticVerifier().verify(
            Map.of(classFile.name(), classFile),
            List.of(new EntryPoint(classFile.name(), method.name(), method.descriptor()))
        );
    }

    private static List<Instruction> canonicalInstructions() {
        return List.of(
            plain(0, 42, "aload_0"),
            string(1, "cipher"),
            invoke(3, 185, "invokeinterface", new MethodRef(
                "java/util/Map",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;"
            )),
            classInstruction(8, "java/lang/String"),
            invoke(11, 184, "invokestatic", new MethodRef(
                "java/lang/Long",
                "parseLong",
                "(Ljava/lang/String;)J"
            )),
            plain(14, 173, "lreturn"),
            plain(15, 76, "astore_1"),
            plain(16, 9, "lconst_0"),
            plain(17, 173, "lreturn")
        );
    }

    private static List<Instruction> replace(
        final List<Instruction> instructions,
        final int offset,
        final Instruction replacement
    ) {
        final List<Instruction> result = new ArrayList<>();
        for (final Instruction instruction : instructions) {
            result.add(instruction.offset() == offset ? replacement : instruction);
        }
        return List.copyOf(result);
    }

    private static Instruction branch(
        final int offset,
        final int opcode,
        final String mnemonic,
        final int target
    ) {
        final int displacement = target - offset;
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[]{(byte) (displacement >>> 8), (byte) displacement},
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction invoke(
        final int offset,
        final int opcode,
        final String mnemonic,
        final MethodRef methodRef
    ) {
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

    private static Instruction string(final int offset, final String value) {
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

    private static Instruction classInstruction(final int offset, final String className) {
        return new Instruction(
            offset,
            192,
            "checkcast",
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
}
