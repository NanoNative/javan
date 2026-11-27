package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImplicitMethodReflectionIntegrationTest extends CliIntegrationSupport {
    @Test
    void literalMethodInvocationDoesNotRetainUnrelatedMethods() throws Exception {
        final Path project = project("reflection-implicit-method-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(Target.class.getDeclaredMethod("run").invoke(null));
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { public static String run() { return \"ok\"; } public static void unused() { System.setProperty(\"unused\", \"never\"); } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { public static void unused() { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-implicit-method-bounds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("ok\n");
        assertThat(generatedProgramSource(project)).doesNotContain("javan_class_com_acme_Unrelated");
        assertThat(Files.readString(project.resolve(".javan/reports/call-graph.json")))
            .doesNotContain("com/acme/Target.unused()V", "com/acme/Unrelated.unused()V");
    }

    @Test
    void methodNameLiteralsThroughBranchesNeedNoDeclaration() throws Exception {
        final Path project = project("reflection-implicit-method-flow");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    String name = args.length == 0 ? "run" : "alternate";
                    String alias = name;
                    System.out.println(Target.class.getDeclaredMethod(alias).invoke(null));
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { public static String run() { return \"one\"; } public static String alternate() { return \"two\"; } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/reflection-implicit-method-flow").toString();
        final ProcessResult first = process(project, List.of(binary));
        final ProcessResult second = process(project, List.of(binary, "choose-alternate"));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo("one\n");
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo("two\n");
    }

    @Test
    void externalMethodNameRequiresAStaticallyKnownOrDeclaredTarget() throws Exception {
        final Path project = project("reflection-implicit-external-method");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    String name = args[0];
                    System.out.println(Target.class.getDeclaredMethod(name).invoke(null));
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { public static String hidden() { return \"unused-value\"; } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-implicit-external-method").toString(), "hidden"));
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("NoSuchMethodException");
        assertThat(Files.readString(project.resolve(".javan/reports/call-graph.json")))
            .doesNotContain("com/acme/Target.hidden()Ljava/lang/String;");
    }
}
