package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImplicitReflectionBoundsIntegrationTest extends CliIntegrationSupport {
    @Test
    void externalClassNamesResolveOnlyToClassesKnownFromReachableCode() throws Exception {
        final Path project = project("reflection-implicit-class-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(Known.class.getName());
                    String name = args[0];
                    Class<?> result = null;
                    try { result = Class.forName(name); }
                    catch (ClassNotFoundException expected) { System.out.println("missing"); }
                    if (result != null) System.out.println(result.getName());
                }
            }
            """);
        writeJava(project, "com.acme.Known", "package com.acme; public final class Known { static { System.out.println(\"initialized\"); } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { static { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/reflection-implicit-class-bounds").toString();
        final ProcessResult known = process(project, List.of(binary, "com.acme.Known"));
        final ProcessResult unknown = process(project, List.of(binary, "com.acme.Unrelated"));
        assertThat(known.exitCode()).as(known.stderr()).isZero();
        assertThat(known.stdout()).isEqualTo("com.acme.Known\ninitialized\ncom.acme.Known\n");
        assertThat(unknown.exitCode()).as(unknown.stderr()).isZero();
        assertThat(unknown.stdout()).isEqualTo("com.acme.Known\nmissing\n");
        assertThat(generatedProgramSource(project)).doesNotContain("unused", "never");
    }

    @Test
    void literalClassNamesFlowToLookupWithoutADeclaration() throws Exception {
        final Path project = project("reflection-implicit-literal-flow");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    String name = args.length == 0 ? "com.acme.Literal" : args[0];
                    System.out.println(Class.forName(name).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Literal", "package com.acme; public final class Literal { }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { static { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-implicit-literal-flow").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("com.acme.Literal\n");
        assertThat(generatedProgramSource(project)).doesNotContain("unused", "never");
    }

    @Test
    void systemLoaderResolvesKnownNamesWithoutInitializingThem() throws Exception {
        final Path project = project("reflection-implicit-system-loader");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(Known.class.getName());
                    String name = args[0];
                    ClassLoader loader = ClassLoader.getSystemClassLoader();
                    try { System.out.println(Class.forName(name, false, loader).getName()); }
                    catch (ClassNotFoundException expected) { System.out.println("missing"); }
                }
            }
            """);
        writeJava(project, "com.acme.Known", "package com.acme; public final class Known { static { System.out.println(\"initialized\"); } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/reflection-implicit-system-loader").toString();
        final ProcessResult known = process(project, List.of(binary, "com.acme.Known"));
        final ProcessResult missing = process(project, List.of(binary, "com.acme.Unrelated"));
        assertThat(known.exitCode()).as(known.stderr()).isZero();
        assertThat(known.stdout()).isEqualTo("com.acme.Known\ncom.acme.Known\n");
        assertThat(missing.exitCode()).as(missing.stderr()).isZero();
        assertThat(missing.stdout()).isEqualTo("com.acme.Known\nmissing\n");
    }

    @Test
    void publicDeclarationAddsAnExternalClassNameWithoutEagerInitialization() throws Exception {
        final Path project = project("reflection-declared-external-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Dynamic.class }; }
                public static void main(String[] args) throws Exception {
                    System.out.println(javanReflectionTargets().length);
                    System.out.println(Class.forName(args[0]).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Dynamic", "package com.acme; public final class Dynamic { static { System.out.println(\"initialized\"); } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { static { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-external-name").toString(), "com.acme.Dynamic"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\ninitialized\ncom.acme.Dynamic\n");
        assertThat(generatedProgramSource(project)).doesNotContain("unused", "never");
    }
}
