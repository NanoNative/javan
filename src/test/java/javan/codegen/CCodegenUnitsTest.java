package javan.codegen;

import javan.build.NativeInteropConfig;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.ir.IrType;
import javan.optimizer.EscapeAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class CCodegenUnitsTest {
    @TempDir
    private Path tempDir;

    @Test
    void emitsDeterministicOwnerUnitsAndManifestLast() throws Exception {
        final IrProgram program = program("com/acme/Worker", "org/example/Helper");
        final CCodegen codegen = new CCodegen();

        final CCodegen.GeneratedC first = codegen.generateProgram(
            program, tempDir.resolve("first"), NativeInteropConfig.empty(), emptyStackPlan()
        );
        final CCodegen.GeneratedC second = codegen.generateProgram(
            program, tempDir.resolve("second"), NativeInteropConfig.empty(), emptyStackPlan()
        );

        assertThat(first.sources()).hasSize(3);
        assertThat(first.sources().getFirst()).isEqualTo(first.main());
        assertThat(Files.readString(first.manifest())).startsWith("javan-generated-sources-v1\nmain.c\n")
            .contains("units/functions-");
        assertThat(relativeContents(first)).isEqualTo(relativeContents(second));
        assertThat(Files.readString(first.header()))
            .contains("#ifndef JAVAN_PROGRAM_H", "void worker_symbol(void);", "void helper_symbol(void);");
        assertThat(Files.readString(first.main()))
            .contains("#include \"javan_program.h\"", "int JAVAN_PROGRAM_MAIN(int argc, char** argv)")
            .doesNotContain("void worker_symbol(void) {");
        assertThat(first.sources().subList(1, first.sources().size()))
            .allSatisfy(source -> assertThat(read(source)).contains("#include \"../javan_program.h\""));
        assertThat(first.sources().stream().map(CCodegenUnitsTest::read).toList())
            .anySatisfy(source -> assertThat(source).contains("void worker_symbol(void) {") )
            .anySatisfy(source -> assertThat(source).contains("void helper_symbol(void) {") );
    }

    @Test
    void removesUnitsThatAreNoLongerInTheGeneratedProgram() throws Exception {
        final CCodegen codegen = new CCodegen();
        final Path output = tempDir.resolve("generated");
        final CCodegen.GeneratedC first = codegen.generateProgram(
            program("com/acme/Worker", "org/example/Helper"), output,
            NativeInteropConfig.empty(), emptyStackPlan()
        );
        final List<Path> oldUnits = List.copyOf(first.sources().subList(1, first.sources().size()));

        final CCodegen.GeneratedC second = codegen.generateProgram(
            program("com/acme/Worker"), output, NativeInteropConfig.empty(), emptyStackPlan()
        );

        assertThat(second.sources()).hasSize(2);
        assertThat(oldUnits).anySatisfy(old -> {
            if (!second.sources().contains(old)) assertThat(old).doesNotExist();
        });
        assertThat(Files.readAllLines(second.manifest())).hasSize(3);
    }

    private static IrProgram program(final String... workerOwners) {
        final java.util.ArrayList<IrFunction> functions = new java.util.ArrayList<>();
        functions.add(function("com/acme/Main", "main_symbol"));
        for (int index = 0; index < workerOwners.length; index++) {
            final String symbol = index == 0 ? "worker_symbol" : "helper_symbol";
            final List<IrInstruction> instructions = index == 0 && workerOwners.length > 1
                ? List.of(IrInstruction.callStaticVoid("helper_symbol"), IrInstruction.returnVoid())
                : List.of(IrInstruction.returnVoid());
            functions.add(function(workerOwners[index], symbol, instructions));
        }
        return new IrProgram(List.of(), List.copyOf(functions), "main_symbol");
    }

    private static IrFunction function(final String owner, final String symbol) {
        return function(owner, symbol, List.of(IrInstruction.returnVoid()));
    }

    private static IrFunction function(
        final String owner,
        final String symbol,
        final List<IrInstruction> instructions
    ) {
        return new IrFunction(owner, symbol, "()V", symbol, IrType.VOID, List.of(), List.of(),
            instructions);
    }

    private static EscapeAnalyzer.StackAllocationPlan emptyStackPlan() {
        return new EscapeAnalyzer.StackAllocationPlan(List.of());
    }

    private static List<String> relativeContents(final CCodegen.GeneratedC generated) {
        final java.util.ArrayList<String> contents = new java.util.ArrayList<>();
        contents.add(Files.exists(generated.header()) ? read(generated.header()) : "");
        for (final Path source : generated.sources()) contents.add(read(source));
        contents.add(read(generated.manifest()));
        return List.copyOf(contents);
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
