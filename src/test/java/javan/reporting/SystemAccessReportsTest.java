package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
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

final class SystemAccessReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void reportsReachableSystemAccessWithoutRecordingValuesOrCommandArguments() throws Exception {
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                literal("API_TOKEN"), staticMethod("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;"),
                load(1), staticMethod("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;"),
                literal("app.home"), literal("default-home"), staticMethod("java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
                literal("mode"), staticMethod("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;"),
                load(3), staticMethod("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;"),
                literal("com.acme.Plugin"), staticMethod("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;"),
                literal("com.acme.Plugin"), zero(), load(4), staticMethod(
                    "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"
                ),
                load(6), literal("com.acme.ModulePlugin"), staticMethod(
                    "java/lang/Class", "forName", "(Ljava/lang/Module;Ljava/lang/String;)Ljava/lang/Class;"
                ),
                load(4), literal("com.acme.Custom"), instanceMethod("java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"),
                load(4), literal("com.acme.Resolve"), zero(), instanceMethod(
                    "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;"
                ),
                load(4), instanceMethod("java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"),
                literal("danger"), staticMethod("java/lang/System", "loadLibrary", "(Ljava/lang/String;)V"),
                load(5), staticMethod("java/lang/System", "load", "(Ljava/lang/String;)V"),
                literal("git status --short"), instanceMethod("java/lang/Runtime", "exec", "(Ljava/lang/String;)Ljava/lang/Process;"),
                load(2), instanceMethod("java/lang/Runtime", "exec", "(Ljava/lang/String;)Ljava/lang/Process;"),
                instanceMethod("java/lang/ProcessBuilder", "<init>", "(Ljava/util/List;)V"),
                instanceMethod("java/lang/ProcessBuilder", "start", "()Ljava/lang/Process;")
            )),
            "com/acme/Hidden",
            classFile("com/acme/Hidden", method(
                "hidden", "()V", literal("SECRET_TOKEN"), staticMethod("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            ))
        );
        final SystemAccessReports reports = new SystemAccessReports();
        final SystemAccessReports.Report report = reports.analyze(classes, List.of(entry));

        final List<Path> written = reports.write(tempDir, classes, new CallGraph(entry, List.of(entry), List.of()));

        assertThat(report.reachableProcessApiCallSiteCount()).isEqualTo(4);
        assertThat(report.processLaunchCallSiteCount()).isEqualTo(3);
        assertThat(report.processBuilderConfigurationCallSiteCount()).isEqualTo(1);
        assertThat(report.unknownExecutableLaunchCallSiteCount()).isEqualTo(2);
        assertThat(report.knownExecutables()).containsExactly(new SystemAccessReports.NameCount("git", 1));
        assertThat(report.environmentLookupCallSiteCount()).isEqualTo(2);
        assertThat(report.unknownEnvironmentLookupCallSiteCount()).isEqualTo(1);
        assertThat(report.environmentVariables()).containsExactly(new SystemAccessReports.NameCount("API_TOKEN", 1));
        assertThat(report.propertyLookupCallSiteCount()).isEqualTo(3);
        assertThat(report.unknownPropertyLookupCallSiteCount()).isEqualTo(1);
        assertThat(report.propertyKeys()).containsExactly(
            new SystemAccessReports.NameCount("app.home", 1),
            new SystemAccessReports.NameCount("mode", 1)
        );
        assertThat(report.classLoadCallSiteCount()).isEqualTo(6);
        assertThat(report.unknownClassLoadCallSiteCount()).isEqualTo(3);
        assertThat(report.classLoadTargets()).containsExactly(
            new SystemAccessReports.NameCount("com.acme.Custom", 1),
            new SystemAccessReports.NameCount("com.acme.ModulePlugin", 1),
            new SystemAccessReports.NameCount("com.acme.Plugin", 1)
        );
        assertThat(report.nativeLibraryLoadCallSiteCount()).isEqualTo(2);
        assertThat(report.unknownNativeLibraryLoadCallSiteCount()).isEqualTo(1);
        assertThat(report.nativeLibraryLoadTargets()).containsExactly(new SystemAccessReports.NameCount("danger", 1));
        assertThat(written).containsExactly(
            tempDir.resolve("reports/system-access.json"),
            tempDir.resolve("reports/system-access.md")
        );
        assertThat(Files.readString(tempDir.resolve("reports/system-access.json"))).contains(
            "\"knownExecutableCount\": 1",
            "\"knownEnvironmentVariableCount\": 1",
            "\"knownPropertyKeyCount\": 2",
            "\"knownClassLoadTargetCount\": 3",
            "\"knownNativeLibraryLoadTargetCount\": 1",
            "{\"name\": \"git\", \"count\": 1}",
            "{\"name\": \"API_TOKEN\", \"count\": 1}",
            "{\"name\": \"app.home\", \"count\": 1}",
            "{\"name\": \"com.acme.Plugin\", \"count\": 1}",
            "{\"name\": \"com.acme.ModulePlugin\", \"count\": 1}",
            "{\"name\": \"danger\", \"count\": 1}"
        ).doesNotContain("status --short", "default-home", "SECRET_TOKEN");
        assertThat(Files.readString(tempDir.resolve("reports/system-access.md"))).contains(
            "# Reachable System Access",
            "| `git` | 1 |",
            "| `API_TOKEN` | 1 |",
            "| `app.home` | 1 |",
            "| `com.acme.Plugin` | 1 |",
            "| `danger` | 1 |"
        ).doesNotContain("status --short", "default-home", "SECRET_TOKEN");
    }

    private static ClassFile classFile(final String name, final MethodInfo method) {
        return new ClassFile(65, name, "java/lang/Object", 0, List.of(), List.of(), List.of(method), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final String name, final String descriptor, final Instruction... instructions) {
        return new MethodInfo(0, name, descriptor, Optional.of(new CodeAttribute(2, 4, new byte[0], 0, List.of(instructions))));
    }

    private static Instruction staticMethod(final String owner, final String name, final String descriptor) {
        return instruction(184, "invokestatic", new MethodRef(owner, name, descriptor));
    }

    private static Instruction instanceMethod(final String owner, final String name, final String descriptor) {
        return instruction(182, "invokevirtual", new MethodRef(owner, name, descriptor));
    }

    private static Instruction literal(final String value) {
        return new Instruction(
            0, 18, "ldc", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction load(final int index) {
        return new Instruction(
            0, 25, "aload", new byte[]{(byte) index}, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction zero() {
        return new Instruction(
            0, 3, "iconst_0", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(0),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction instruction(final int opcode, final String mnemonic, final MethodRef reference) {
        return new Instruction(
            0, opcode, mnemonic, new byte[0], Optional.of(reference), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
