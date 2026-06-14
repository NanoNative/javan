package javan.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class CompatibilityReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeOrdersJdkInventoryClassesByName() throws Exception {
        final List<ClassMetadata> jdkClasses = new ArrayList<>();
        for (int index = 25; index >= 0; index--) {
            jdkClasses.add(metadata("java.base", "java/lang/C" + twoDigits(index)));
        }

        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            jdkClasses,
            List.of()
        );

        final int feature = Runtime.version().feature();
        final String inventory = Files.readString(tempDir.resolve(".javan/reports/jdk-" + feature + "-inventory.json"));
        assertThat(inventory.indexOf("\"name\": \"java/lang/C00\""))
            .isLessThan(inventory.indexOf("\"name\": \"java/lang/C25\""));
    }

    private static ClassMetadata metadata(final String moduleName, final String className) {
        return new ClassMetadata(
            Path.of(className + ".class"),
            true,
            moduleName,
            0,
            69,
            0,
            className,
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

    private static String twoDigits(final int value) {
        if (value < 10) {
            return "0" + value;
        }
        return Integer.toString(value);
    }
}
