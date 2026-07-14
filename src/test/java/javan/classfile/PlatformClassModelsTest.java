package javan.classfile;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class PlatformClassModelsTest {
    @Test
    void expandedClassesAddsStackTraceElementWhenAClassReferencesIt() {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", new ClassFile(
            69,
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(
                0x0008,
                "main",
                "()I",
                Optional.of(new CodeAttribute(2, 1, new byte[0], 0, List.of(
                    new Instruction(
                        0,
                        187,
                        "new",
                        new byte[0],
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("java/lang/StackTraceElement"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    new Instruction(
                        1,
                        176,
                        "areturn",
                        new byte[0],
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    )
                ))
            ))),
            Path.of("com/acme/Main.class"),
            true
        ));

        final Map<String, ClassFile> expanded = PlatformClassModels.expandedClasses(classes);

        assertThat(expanded).containsKey("java/lang/StackTraceElement");
        assertThat(expanded.get("java/lang/StackTraceElement").fields())
            .extracting(FieldInfo::name, FieldInfo::descriptor)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("declaringClass", "Ljava/lang/String;"),
                org.assertj.core.groups.Tuple.tuple("methodName", "Ljava/lang/String;"),
                org.assertj.core.groups.Tuple.tuple("fileName", "Ljava/lang/String;"),
                org.assertj.core.groups.Tuple.tuple("lineNumber", "I")
            );
    }

    @Test
    void expandedClassesAddsLogRecordWhenAClassReferencesIt() {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put("com/acme/Main", new ClassFile(
            69,
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(
                0x0008,
                "main",
                "()Ljava/util/logging/LogRecord;",
                Optional.of(new CodeAttribute(4, 0, new byte[0], 0, List.of(
                    new Instruction(
                        0,
                        187,
                        "new",
                        new byte[0],
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("java/util/logging/LogRecord"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    new Instruction(
                        1,
                        176,
                        "areturn",
                        new byte[0],
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    )
                ))
            ))),
            Path.of("com/acme/Main.class"),
            true
        ));

        final Map<String, ClassFile> expanded = PlatformClassModels.expandedClasses(classes);

        assertThat(expanded).containsKey("java/util/logging/LogRecord");
        assertThat(expanded.get("java/util/logging/LogRecord").fields())
            .extracting(FieldInfo::name, FieldInfo::descriptor)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("level", "Ljava/util/logging/Level;"),
                org.assertj.core.groups.Tuple.tuple("message", "Ljava/lang/String;"),
                org.assertj.core.groups.Tuple.tuple("millis", "J"),
                org.assertj.core.groups.Tuple.tuple("parameters", "[Ljava/lang/Object;"),
                org.assertj.core.groups.Tuple.tuple("thrown", "Ljava/lang/Throwable;"),
                org.assertj.core.groups.Tuple.tuple("loggerName", "Ljava/lang/String;")
            );
    }
}
