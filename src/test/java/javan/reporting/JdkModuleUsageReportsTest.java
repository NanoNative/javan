package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.compat.ClassMetadata;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkModuleUsageReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void reportsDistinctDirectReachableReferencesWithExactCurrentJdkModuleMetadata() throws Exception {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final List<EntryPoint> reachable = List.of(
            main,
            new EntryPoint("com/acme/Main", "helper", "()V")
        );
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile(
                "com/acme/Main",
                "java/lang/Object",
                List.of("java/util/List"),
                method("main", "([Ljava/lang/String;)V", List.of(
                    invocation("java/lang/String", "valueOf", "(I)Ljava/lang/String;"),
                    field("java/lang/System", "out", "Ljava/io/PrintStream;"),
                    classReference("java/sql/Driver"),
                    invocation("java/util/Missing", "value", "()V")
                )),
                method("helper", "()V", List.of(invocation("java/util/NoModule", "value", "()V")))
            ),
            "com/acme/Hidden",
            classFile(
                "com/acme/Hidden",
                "java/lang/Object",
                List.of(),
                method("hidden", "()V", List.of(classReference("java/net/http/HttpClient")))
            )
        );
        final List<ClassMetadata> jdkClasses = List.of(
            metadata("java.base", "java/lang/String"),
            metadata("java.base", "java/lang/Object"),
            metadata("java.base", "java/lang/System"),
            metadata("java.base", "java/util/List"),
            metadata("java.sql", "java/sql/Driver"),
            metadata("", "java/util/NoModule"),
            metadata("java.net.http", "java/net/http/HttpClient")
        );
        final JdkModuleUsageReports reports = new JdkModuleUsageReports();

        final JdkModuleUsageReports.Report report = reports.analyze(classes, reachable, jdkClasses);
        final List<Path> written = reports.write(tempDir, classes, new CallGraph(main, reachable, List.of()), jdkClasses);

        assertThat(report.reachableDirectJdkClassCount()).isEqualTo(5);
        assertThat(report.modules()).containsExactly(
            new JdkModuleUsageReports.ModuleCount("java.base", 4),
            new JdkModuleUsageReports.ModuleCount("java.sql", 1)
        );
        assertThat(written).containsExactly(
            tempDir.resolve("reports/jdk-module-usage.json"),
            tempDir.resolve("reports/jdk-module-usage.md")
        );
        assertThat(Files.readString(tempDir.resolve("reports/jdk-module-usage.json"))).contains(
            "\"reachableDirectJdkClassCount\": 5",
            "\"usedJdkModuleCount\": 2",
            "{\"name\": \"java.base\", \"reachableClassCount\": 4}",
            "{\"name\": \"java.sql\", \"reachableClassCount\": 1}"
        ).doesNotContain("com/acme/Main", "java/util/Missing", "java/util/NoModule", "java/net/http/HttpClient");
        assertThat(Files.readString(tempDir.resolve("reports/jdk-module-usage.md"))).contains(
            "# Reachable JDK Modules",
            "reachable bytecode and class supertypes",
            "| `java.base` | 4 |",
            "| `java.sql` | 1 |"
        ).doesNotContain("com/acme/Main", "java/util/Missing", "java/util/NoModule", "java/net/http/HttpClient");
    }

    private static ClassFile classFile(
        final String name,
        final String superName,
        final List<String> interfaces,
        final MethodInfo... methods
    ) {
        return new ClassFile(65, name, superName, 0, interfaces, List.of(), List.of(methods), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final String name, final String descriptor, final List<Instruction> instructions) {
        return new MethodInfo(0, name, descriptor, Optional.of(new CodeAttribute(1, 1, new byte[0], 0, instructions)));
    }

    private static Instruction invocation(final String owner, final String name, final String descriptor) {
        return instruction(Optional.of(new MethodRef(owner, name, descriptor)), Optional.empty(), Optional.empty());
    }

    private static Instruction field(final String owner, final String name, final String descriptor) {
        return instruction(Optional.empty(), Optional.of(new FieldRef(owner, name, descriptor)), Optional.empty());
    }

    private static Instruction classReference(final String name) {
        return instruction(Optional.empty(), Optional.empty(), Optional.of(name));
    }

    private static Instruction instruction(
        final Optional<MethodRef> method,
        final Optional<FieldRef> field,
        final Optional<String> className
    ) {
        return new Instruction(
            0,
            0,
            "reference",
            new byte[0],
            method,
            field,
            className,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static ClassMetadata metadata(final String module, final String name) {
        return new ClassMetadata(
            Path.of(name + ".class"),
            false,
            module,
            0,
            65,
            0,
            name,
            "java/lang/Object",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }
}
