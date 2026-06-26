package javan.detect;

import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.verify.DiagnosticException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class MainClassDetectorTest {
    @Test
    void detectReturnsExplicitMainWhenClassHasPublicStaticEntryPoint() {
        final MainClassDetector detector = new MainClassDetector();

        final String main = detector.detect(Optional.of("com.acme.Main"), Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", true, method("main", "([Ljava/lang/String;)V", 0x0001 | 0x0008))
        ));

        assertThat(main).isEqualTo("com/acme/Main");
    }

    @Test
    void detectThrowsDiagnosticWhenExplicitMainMissing() {
        final MainClassDetector detector = new MainClassDetector();

        assertThatThrownBy(() -> detector.detect(Optional.of("com.acme.Main"), Map.of()))
            .isInstanceOf(DiagnosticException.class)
            .hasMessageContaining("error[JAVAN021]: explicit main class does not declare public static void main(String[])");
    }

    @Test
    void findRejectsExplicitMainWithoutPublicStaticEntryPoint() {
        final MainClassDetector.MainClassDetection detection = new MainClassDetector().find(Optional.of("com.acme.Main"), Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", true, method("main", "()V", 0x0001 | 0x0008))
        ));

        assertThat(detection.pass()).isFalse();
        assertThat(detection.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN021");
            assertThat(diagnostic.subject()).isEqualTo("com/acme/Main");
        });
    }

    @Test
    void findRejectsExplicitMainWithoutPublicFlag() {
        final MainClassDetector.MainClassDetection detection = new MainClassDetector().find(Optional.of("com.acme.Main"), Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", true, method("main", "([Ljava/lang/String;)V", 0x0008))
        ));

        assertThat(detection.pass()).isFalse();
        assertThat(detection.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN021");
            assertThat(diagnostic.subject()).isEqualTo("com/acme/Main");
        });
    }

    @Test
    void findRejectsExplicitMainWithoutStaticFlag() {
        final MainClassDetector.MainClassDetection detection = new MainClassDetector().find(Optional.of("com.acme.Main"), Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", true, method("main", "([Ljava/lang/String;)V", 0x0001))
        ));

        assertThat(detection.pass()).isFalse();
        assertThat(detection.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN021");
            assertThat(diagnostic.subject()).isEqualTo("com/acme/Main");
        });
    }

    @Test
    void findRejectsWhenNoApplicationMainClassExists() {
        final MainClassDetector.MainClassDetection detection = new MainClassDetector().find(Optional.empty(), Map.of(
            "com/acme/Library",
            classFile("com/acme/Library", false, method("main", "([Ljava/lang/String;)V", 0x0001 | 0x0008))
        ));

        assertThat(detection.pass()).isFalse();
        assertThat(detection.diagnostics().getFirst().code()).isEqualTo("JAVAN020");
    }

    @Test
    void findRejectsWhenMultipleApplicationMainClassesExist() {
        final MainClassDetector.MainClassDetection detection = new MainClassDetector().find(Optional.empty(), Map.of(
            "com/acme/Tool", classFile("com/acme/Tool", true, method("main", "([Ljava/lang/String;)V", 0x0001 | 0x0008)),
            "com/acme/Main", classFile("com/acme/Main", true, method("main", "([Ljava/lang/String;)V", 0x0001 | 0x0008))
        ));

        assertThat(detection.pass()).isFalse();
        assertThat(detection.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN022");
            assertThat(diagnostic.subject()).contains("com.acme.Main", "com.acme.Tool");
            assertThat(diagnostic.fix()).isEqualTo("Run with --main com.acme.Main.");
        });
    }

    @Test
    void findReturnsSingleSortedApplicationMainCandidate() {
        final MainClassDetector.MainClassDetection detection = new MainClassDetector().find(Optional.empty(), Map.of(
            "com/acme/Library", classFile("com/acme/Library", true, method("helper", "()V", 0x0001)),
            "com/acme/Main", classFile("com/acme/Main", true, method("main", "([Ljava/lang/String;)V", 0x0001 | 0x0008))
        ));

        assertThat(detection.pass()).isTrue();
        assertThat(detection.mainClass()).isEqualTo("com/acme/Main");
    }

    private static ClassFile classFile(final String name, final boolean application, final MethodInfo... methods) {
        return new ClassFile(
            65,
            name,
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(methods),
            Path.of(name + ".class"),
            application
        );
    }

    private static MethodInfo method(final String name, final String descriptor, final int accessFlags) {
        return new MethodInfo(accessFlags, name, descriptor, Optional.empty());
    }
}
