package javan.reporting;

import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.ir.IrSourceLocation;
import javan.ir.IrType;
import javan.ir.IrExpression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ExceptionReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesExceptionReportsForSourceMappedPanicSites() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "javan_fn_com_acme_Main_main",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.panic(
                    IrExpression.stringLiteral("boom"),
                    new IrSourceLocation(
                        "com/acme/Main",
                        "main",
                        "()V",
                        12,
                        Optional.of("Main.java"),
                        Optional.of(9),
                        Optional.of("throw new IllegalStateException(\"boom\");")
                    )
                ))
            )),
            "javan_fn_com_acme_Main_main"
        );

        new ExceptionReports().write(tempDir, program);

        assertThat(Files.readString(tempDir.resolve("reports/exceptions.json"))).contains(
            "\"panicSites\": 1",
            "\"class\": \"com.acme.Main\"",
            "\"method\": \"main()V\"",
            "\"sourceFile\": \"Main.java\"",
            "\"line\": 9",
            "\"sourceLine\": \"throw new IllegalStateException(\\\"boom\\\");\"",
            "\"bytecodeOffset\": 12"
        );
        assertThat(Files.readString(tempDir.resolve("reports/exceptions.md"))).contains(
            "| `panic-1` | `JAVAN-RUNTIME-PANIC` | `com.acme.Main.main()V(Main.java:9)` | `12` |",
            "## Source Lines",
            "throw new IllegalStateException(\"boom\");"
        );
        assertThat(Files.readString(tempDir.resolve("reports/debug-map.json"))).contains(
            "\"debugEntries\": 1",
            "\"generatedFunction\": \"javan_fn_com_acme_Main_main\"",
            "\"sourceLine\": \"throw new IllegalStateException(\\\"boom\\\");\""
        );
    }

    @Test
    void writesEmptyExceptionReportsWhenNoPanicSitesExist() throws Exception {
        final IrProgram program = new IrProgram(List.of(), "main_symbol");

        new ExceptionReports().write(tempDir, program);

        assertThat(Files.readString(tempDir.resolve("reports/exceptions.json"))).contains("\"panicSites\": 0");
        assertThat(Files.readString(tempDir.resolve("reports/exceptions.md"))).contains("| none | - | - | - |");
        assertThat(Files.readString(tempDir.resolve("reports/debug-map.json"))).contains("\"debugEntries\": 0");
    }

    @Test
    void writesNullSourceFieldsWhenPanicHasNoSourceLocation() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "javan_fn_com_acme_Main_main",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.panic(IrExpression.stringLiteral("boom")))
            )),
            "javan_fn_com_acme_Main_main"
        );

        new ExceptionReports().write(tempDir, program);

        assertThat(Files.readString(tempDir.resolve("reports/exceptions.json"))).contains(
            "\"sourceFile\": null",
            "\"line\": null",
            "\"sourceLine\": null",
            "\"bytecodeOffset\": -1"
        );
        assertThat(Files.readString(tempDir.resolve("reports/exceptions.md")))
            .doesNotContain("## Source Lines");
    }

    @Test
    void writesSourceFileSuffixWithoutLineWhenLineIsMissing() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "javan_fn_com_acme_Main_main",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.panic(
                    IrExpression.stringLiteral("boom"),
                    new IrSourceLocation(
                        "com/acme/Main",
                        "main",
                        "()V",
                        12,
                        Optional.of("Main.java"),
                        Optional.empty()
                    )
                ))
            )),
            "javan_fn_com_acme_Main_main"
        );

        new ExceptionReports().write(tempDir, program);

        assertThat(Files.readString(tempDir.resolve("reports/exceptions.md"))).contains(
            "| `panic-1` | `JAVAN-RUNTIME-PANIC` | `com.acme.Main.main()V(Main.java)` | `12` |"
        );
    }

    @Test
    void writesDeterministicCommaSeparatedPanicSites() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "javan_fn_com_acme_Main_main",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(
                    IrInstruction.panic(IrExpression.stringLiteral("first")),
                    IrInstruction.panic(IrExpression.stringLiteral("second"))
                )
            )),
            "javan_fn_com_acme_Main_main"
        );

        new ExceptionReports().write(tempDir, program);

        assertThat(Files.readString(tempDir.resolve("reports/exceptions.json"))).contains(
            "\"id\": \"panic-1\"",
            "    },\n    {\n",
            "\"id\": \"panic-2\""
        );
        assertThat(Files.readString(tempDir.resolve("reports/debug-map.json"))).contains(
            "\"id\": \"panic-1\"",
            "    },\n    {\n",
            "\"id\": \"panic-2\""
        );
    }

    @Test
    void skipsOnlySitesWithoutSourceLineInSourceSection() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "javan_fn_com_acme_Main_main",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(
                    IrInstruction.panic(IrExpression.stringLiteral("first")),
                    IrInstruction.panic(
                        IrExpression.stringLiteral("second"),
                        new IrSourceLocation(
                            "com/acme/Main",
                            "main",
                            "()V",
                            12,
                            Optional.of("Main.java"),
                            Optional.of(9),
                            Optional.of("throw boom;")
                        )
                    )
                )
            )),
            "javan_fn_com_acme_Main_main"
        );

        new ExceptionReports().write(tempDir, program);

        assertThat(Files.readString(tempDir.resolve("reports/exceptions.md"))).contains(
            "## Source Lines",
            "### `panic-2`",
            "throw boom;"
        ).doesNotContain("### `panic-1`");
    }
}
