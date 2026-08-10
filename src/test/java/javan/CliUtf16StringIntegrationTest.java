package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@NativeTest
@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliUtf16StringIntegrationTest extends CliIntegrationSupport {
    @Test
    void stringAndBuilderOperationsPreserveUtf16CodeUnits() throws Exception {
        final Path project = project("utf16-string-operations");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char euro = '\\u20ac';
                    final char nul = '\\0';
                    final char high = '\\ud83d';
                    final char low = '\\ude00';
                    final String value = new String(new char[]{'a', euro, nul, 'b', high, low});

                    System.out.println(value.length());
                    for (int index = 0; index < value.length(); index++) {
                        System.out.println((int) value.charAt(index));
                    }
                    System.out.println(value.indexOf(euro));
                    System.out.println(value.indexOf(nul));
                    System.out.println(value.indexOf(String.valueOf(euro)));
                    System.out.println(value.substring(1, 5).length());
                    System.out.println(value.replace(euro, 'x').charAt(1));

                    final StringBuilder builder = new StringBuilder();
                    builder.append(euro).append(nul).append(high).append(low);
                    builder.insert(1, 'x');
                    builder.setCharAt(1, euro);
                    builder.deleteCharAt(2);
                    System.out.println(builder.length());
                    for (int index = 0; index < builder.length(); index++) {
                        System.out.println((int) builder.charAt(index));
                    }
                    System.out.println(builder.substring(1).length());
                    System.out.println(builder.indexOf(String.valueOf(euro)));
                    System.out.print(String.valueOf(nul));
                    System.out.println(42);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/utf16-string-operations").toString())).stdout())
            .isEqualTo(jvmOutput);
    }
}
