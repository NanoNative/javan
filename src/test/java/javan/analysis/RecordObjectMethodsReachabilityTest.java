package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.ClassFileReader;
import javan.codegen.BytecodeToIR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class RecordObjectMethodsReachabilityTest {
    @TempDir
    private Path tempDir;

    @Test
    void exactConcreteComponentAddsOnlyItsEqualsTarget() throws Exception {
        final CallGraph graph = analyze("exact-equals", """
            package com.acme;

            public final class Main {
                record Leaf(int value) {
                }

                record Holder(Leaf value) {
                }

                record Unrelated(int value) {
                }

                public static void main(final String[] args) {
                    System.out.println(new Holder(new Leaf(7)).equals(new Holder(new Leaf(7))));
                }
            }
            """);

        assertThat(recordObjectMethods(graph, "equals"))
            .containsExactly("com/acme/Main$Holder.equals(Ljava/lang/Object;)Z", "com/acme/Main$Leaf.equals(Ljava/lang/Object;)Z");
    }

    @Test
    void exactConcreteComponentAddsOnlyItsHashCodeTarget() throws Exception {
        final CallGraph graph = analyze("exact-hash", """
            package com.acme;

            public final class Main {
                record Leaf(int value) {
                }

                record Holder(Leaf value) {
                }

                record Unrelated(int value) {
                }

                public static void main(final String[] args) {
                    System.out.println(new Holder(new Leaf(7)).hashCode());
                }
            }
            """);

        assertThat(recordObjectMethods(graph, "hashCode"))
            .containsExactly("com/acme/Main$Holder.hashCode()I", "com/acme/Main$Leaf.hashCode()I");
    }

    @Test
    void unsafeObjectComponentDoesNotExpandObjectMethodReachability() throws Exception {
        final CallGraph graph = analyze("unsafe-object", """
            package com.acme;

            public final class Main {
                record Unsafe(Object value) {
                }

                record Unrelated(int value) {
                }

                public static void main(final String[] args) {
                    System.out.println(new Unsafe(new Unrelated(7)).equals(new Unsafe(new Unrelated(7))));
                }
            }
            """);

        assertThat(recordObjectMethods(graph, "equals"))
            .containsExactly("com/acme/Main$Unsafe.equals(Ljava/lang/Object;)Z");
    }

    @Test
    void listComponentAddsOnlyConstructedElementEqualsTargets() throws Exception {
        final CallGraph graph = analyze("list-equals", """
            package com.acme;

            import java.util.List;

            public final class Main {
                record Child(int value) {
                }

                record Holder(List<Child> value) {
                }

                record Unrelated(int value) {
                }

                public static void main(final String[] args) {
                    System.out.println(
                        new Holder(List.of(new Child(7))).equals(new Holder(List.of(new Child(7))))
                    );
                    System.out.println(new Unrelated(9).value());
                }
            }
            """);

        assertThat(recordObjectMethods(graph, "equals"))
            .containsExactly("com/acme/Main$Child.equals(Ljava/lang/Object;)Z", "com/acme/Main$Holder.equals(Ljava/lang/Object;)Z");
    }

    @Test
    void listComponentDispatchContainsOnlyItsExactApplicationElement() throws Exception {
        final Map<String, ClassFile> classes = compile("list-dispatch", """
            package com.acme;

            import java.util.List;

            public final class Main {
                record Child(int value) {
                }

                record Holder(List<Child> value) {
                }

                record Unrelated(int value) {
                }

                public static void main(final String[] args) {
                    System.out.println(
                        new Holder(List.of(new Child(7))).equals(new Holder(List.of(new Child(7))))
                    );
                    System.out.println(new Unrelated(9).value());
                }
            }
            """);
        final CallGraph graph = new ReachabilityAnalyzer().analyze(classes, "com/acme/Main");

        assertThat(new BytecodeToIR().lower(classes, graph).dispatches().stream()
            .filter(dispatch -> dispatch.symbol().equals("javan_dispatch_record_reference_equals"))
            .flatMap(dispatch -> dispatch.targets().stream())
            .map(target -> target.owner())
            .toList()).containsExactly("com/acme/Main$Child");
    }

    @Test
    void cyclicConcreteComponentHierarchyDoesNotLoopOrAddTarget() throws Exception {
        final Map<String, ClassFile> classes = compile("cyclic-component", """
            package com.acme;

            public final class Main {
                static final class Leaf {
                }

                record Holder(Leaf value) {
                }

                public static void main(final String[] args) {
                    System.out.println(new Holder(new Leaf()).equals(new Holder(new Leaf())));
                }
            }
            """);
        final ClassFile leaf = classes.get("com/acme/Main$Leaf");
        classes.put(leaf.name(), withSuperName(leaf, "com/acme/Cycle"));
        classes.put("com/acme/Cycle", hierarchyClass("com/acme/Cycle", leaf.name()));

        final CallGraph graph = new ReachabilityAnalyzer().analyze(classes, "com/acme/Main");

        assertThat(recordObjectMethods(graph, "equals"))
            .containsExactly("com/acme/Main$Holder.equals(Ljava/lang/Object;)Z");
    }

    private CallGraph analyze(final String name, final String source) throws Exception {
        return new ReachabilityAnalyzer().analyze(compile(name, source), "com/acme/Main");
    }

    private Map<String, ClassFile> compile(final String name, final String source) throws Exception {
        final Path root = tempDir.resolve(name);
        final Path sourceFile = root.resolve("src/com/acme/Main.java");
        final Path classesRoot = root.resolve("classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(sourceFile, source);
        final int exitCode = ToolProvider.getSystemJavaCompiler().run(
            null,
            null,
            null,
            "-d",
            classesRoot.toString(),
            sourceFile.toString()
        );
        if (exitCode != 0) {
            throw new IllegalStateException("javac failed with exit code " + exitCode);
        }
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        final ClassFileReader reader = new ClassFileReader();
        try (var paths = Files.walk(classesRoot)) {
            for (final Path classFile : paths.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                final ClassFile parsed = reader.read(Files.readAllBytes(classFile), classFile);
                classes.put(parsed.name(), parsed);
            }
        }
        classes.put("java/lang/Object", platformClass("java/lang/Object", ""));
        classes.put("java/lang/Record", platformClass("java/lang/Record", "java/lang/Object"));
        return classes;
    }

    private static ClassFile platformClass(final String name, final String superName) {
        return new ClassFile(
            69,
            name,
            superName,
            0,
            List.of(),
            List.of(),
            List.of(),
            Path.of(name + ".class"),
            false
        );
    }

    private static ClassFile hierarchyClass(final String name, final String superName) {
        return new ClassFile(
            69,
            name,
            superName,
            0x0010,
            List.of(),
            List.of(),
            List.of(),
            Path.of(name + ".class"),
            true
        );
    }

    private static ClassFile withSuperName(final ClassFile classFile, final String superName) {
        return new ClassFile(
            classFile.majorVersion(),
            classFile.name(),
            superName,
            classFile.accessFlags(),
            classFile.interfaces(),
            classFile.fields(),
            classFile.methods(),
            classFile.sourceFile(),
            classFile.recordComponents(),
            classFile.source(),
            classFile.application()
        );
    }

    private static List<String> recordObjectMethods(final CallGraph graph, final String methodName) {
        return graph.reachableMethods().stream()
            .filter(entry -> entry.methodName().equals(methodName))
            .map(EntryPoint::display)
            .sorted()
            .toList();
    }
}
