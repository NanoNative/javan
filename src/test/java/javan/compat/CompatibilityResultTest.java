package javan.compat;

import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class CompatibilityResultTest {
    @Test
    void passReturnsTrueWhenDiagnosticsAndMembersAreCompatible() {
        final CompatibilityResult result = result(
            List.of(Diagnostic.warning("JAVAN199", "warn", "A", "m", "", "", "")),
            List.of(classMetadata(List.of(member(BytecodeSupport.Status.NATIVE_SUPPORTED)), List.of(member(BytecodeSupport.Status.RECOGNIZED_REJECTED))))
        );

        assertThat(result.pass()).isTrue();
    }

    @Test
    void passReturnsFalseWhenFatalDiagnosticIsPresent() {
        final CompatibilityResult result = result(
            List.of(Diagnostic.error("JAVAN001", "fatal", "A", "m", "", "", "")),
            List.of(classMetadata(List.of(member(BytecodeSupport.Status.NATIVE_SUPPORTED)), List.of(member(BytecodeSupport.Status.NATIVE_SUPPORTED))))
        );

        assertThat(result.pass()).isFalse();
    }

    @Test
    void passReturnsFalseWhenConstructorContainsUnknownFatalInstruction() {
        final CompatibilityResult result = result(
            List.of(),
            List.of(classMetadata(List.of(member(BytecodeSupport.Status.UNKNOWN_FATAL)), List.of(member(BytecodeSupport.Status.NATIVE_SUPPORTED))))
        );

        assertThat(result.pass()).isFalse();
    }

    @Test
    void passReturnsFalseWhenMethodContainsUnknownFatalInstruction() {
        final CompatibilityResult result = result(
            List.of(),
            List.of(classMetadata(List.of(member(BytecodeSupport.Status.NATIVE_SUPPORTED)), List.of(member(BytecodeSupport.Status.UNKNOWN_FATAL))))
        );

        assertThat(result.pass()).isFalse();
    }

    private static CompatibilityResult result(
        final List<Diagnostic> diagnostics,
        final List<ClassMetadata> projectClasses
    ) {
        return new CompatibilityResult(
            Path.of("target"),
            "25.0.1",
            25,
            projectClasses,
            List.of(),
            diagnostics,
            List.of()
        );
    }

    private static ClassMetadata classMetadata(
        final List<MemberMetadata> constructors,
        final List<MemberMetadata> methods
    ) {
        return new ClassMetadata(
            Path.of("A.class"),
            true,
            "java.base",
            0,
            69,
            0,
            "com/acme/A",
            "java/lang/Object",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            constructors,
            methods
        );
    }

    private static MemberMetadata member(final BytecodeSupport.Status status) {
        return new MemberMetadata(
            0,
            "member",
            "()V",
            List.of(),
            List.of(new InstructionMetadata(0, 0, "nop", 0, status))
        );
    }
}
