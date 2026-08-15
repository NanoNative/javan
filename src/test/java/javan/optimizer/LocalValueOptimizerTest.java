package javan.optimizer;

import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrProgram;
import javan.ir.IrType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class LocalValueOptimizerTest {
    private final LocalValueOptimizer optimizer = new LocalValueOptimizer();

    @Test
    void releaseRemovesOnlyProvenGuardsAndBranches() {
        final IrFunction function = function(
            List.of(new IrParameter(IrType.INT, "choice")),
            List.of(new IrLocal(IrType.OBJECT, "text"), new IrLocal(IrType.INT, "length"), new IrLocal(IrType.INT, "value")),
            List.of(
                IrInstruction.assignObject("text", IrExpression.stringLiteral("abc")),
                IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal("text"))),
                IrInstruction.assignInt("length", IrExpression.intCall("javan_string_length", List.of(IrExpression.objectLocal("text")))),
                IrInstruction.branchIf("three", IrExpression.intComparison("==", IrExpression.intLocal("length"), IrExpression.intLiteral(3))),
                IrInstruction.panic(IrExpression.stringLiteral("unreachable")),
                IrInstruction.label("three"),
                IrInstruction.branchIf("high", IrExpression.intComparison("!=", IrExpression.intLocal("choice"), IrExpression.intLiteral(0))),
                IrInstruction.assignInt("value", IrExpression.intLiteral(1)),
                IrInstruction.jump("merged"),
                IrInstruction.label("high"),
                IrInstruction.assignInt("value", IrExpression.intLiteral(3)),
                IrInstruction.label("merged"),
                IrInstruction.branchIf("impossible", IrExpression.intComparison(">", IrExpression.intLocal("value"), IrExpression.intLiteral(5))),
                IrInstruction.returnInt(IrExpression.intLocal("value")),
                IrInstruction.label("impossible"),
                IrInstruction.panic(IrExpression.stringLiteral("also unreachable"))
            )
        );

        final LocalValueOptimizer.Result result = optimizer.optimize(new IrProgram(List.of(function), function.symbol()), true);
        final List<IrInstruction> instructions = result.program().functions().getFirst().instructions();

        assertThat(instructions)
            .noneMatch(instruction -> instruction.value().filter("javan_objects_require_non_null"::equals).isPresent())
            .noneMatch(instruction -> instruction.op() == IrInstruction.Op.PANIC)
            .noneMatch(instruction -> instruction.value().filter("impossible"::equals).isPresent());
        assertThat(result.report().redundantNullChecks()).isEqualTo(1);
        assertThat(result.report().deadBranches()).isEqualTo(2);
        assertThat(result.facts().stringLengths()).isPositive();
        assertThat(result.facts().integerRanges()).isPositive();
        assertThat(result.proofs()).extracting(LocalValueOptimizer.Proof::kind)
            .containsExactly("null-check", "branch", "branch");
    }

    @Test
    void debugReportsProofsWithoutChangingInstructions() {
        final IrFunction function = function(
            List.of(),
            List.of(new IrLocal(IrType.OBJECT, "array"), new IrLocal(IrType.INT, "length")),
            List.of(
                IrInstruction.assignObject("array", IrExpression.intArrayAllocation(IrExpression.intLiteral(4))),
                IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal("array"))),
                IrInstruction.assignInt("length", IrExpression.arrayLength(IrExpression.objectLocal("array"))),
                IrInstruction.returnInt(IrExpression.intLocal("length"))
            )
        );
        final IrProgram program = new IrProgram(List.of(function), function.symbol());

        final LocalValueOptimizer.Result result = optimizer.optimize(program, false);

        assertThat(result.program()).isEqualTo(program);
        assertThat(result.report().redundantNullChecks()).isZero();
        assertThat(result.proofs()).hasSize(1);
        assertThat(result.facts().arrayLengths()).isPositive();
        assertThat(result.facts().exactTypes()).isPositive();
    }

    @Test
    void branchEdgesRefineParameterNullnessAndRanges() {
        final IrFunction function = function(
            List.of(new IrParameter(IrType.OBJECT, "value"), new IrParameter(IrType.INT, "number")),
            List.of(),
            List.of(
                IrInstruction.branchIf("nonnull", IrExpression.objectComparison(
                    "!=", IrExpression.objectLocal("value"), IrExpression.objectNull()
                )),
                IrInstruction.returnInt(IrExpression.intLiteral(0)),
                IrInstruction.label("nonnull"),
                IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal("value"))),
                IrInstruction.branchIf("negative", IrExpression.intComparison(
                    "<", IrExpression.intLocal("number"), IrExpression.intLiteral(0)
                )),
                IrInstruction.branchIf("impossible", IrExpression.intComparison(
                    "<", IrExpression.intLocal("number"), IrExpression.intLiteral(0)
                )),
                IrInstruction.returnInt(IrExpression.intLiteral(1)),
                IrInstruction.label("negative"),
                IrInstruction.returnInt(IrExpression.intLiteral(2)),
                IrInstruction.label("impossible"),
                IrInstruction.returnInt(IrExpression.intLiteral(3))
            )
        );

        final LocalValueOptimizer.Result result = optimizer.optimize(new IrProgram(List.of(function), function.symbol()), true);

        assertThat(result.report().redundantNullChecks()).isEqualTo(1);
        assertThat(result.report().deadBranches()).isEqualTo(1);
        assertThat(result.program().functions().getFirst().instructions())
            .noneMatch(instruction -> instruction.value().filter("impossible"::equals).isPresent());
    }

    @Test
    void overlappingRangesRemainUnknownWithoutUnboxingNull() {
        final IrFunction function = function(
            List.of(new IrParameter(IrType.INT, "choice")),
            List.of(new IrLocal(IrType.INT, "value")),
            List.of(
                IrInstruction.branchIf("high", IrExpression.intComparison(
                    "!=", IrExpression.intLocal("choice"), IrExpression.intLiteral(0)
                )),
                IrInstruction.assignInt("value", IrExpression.intLiteral(1)),
                IrInstruction.jump("merged"),
                IrInstruction.label("high"),
                IrInstruction.assignInt("value", IrExpression.intLiteral(3)),
                IrInstruction.label("merged"),
                IrInstruction.branchIf("maybe", IrExpression.intComparison(
                    "<=", IrExpression.intLocal("value"), IrExpression.intLiteral(2)
                )),
                IrInstruction.returnInt(IrExpression.intLiteral(0)),
                IrInstruction.label("maybe"),
                IrInstruction.returnInt(IrExpression.intLiteral(1))
            )
        );

        final LocalValueOptimizer.Result result = optimizer.optimize(new IrProgram(List.of(function), function.symbol()), true);

        assertThat(result.program().functions().getFirst().instructions())
            .anyMatch(instruction -> instruction.value().filter("maybe"::equals).isPresent());
    }

    private static IrFunction function(
        final List<IrParameter> parameters,
        final List<IrLocal> locals,
        final List<IrInstruction> instructions
    ) {
        return new IrFunction("example/Main", "run", "()I", "example_Main_run", IrType.INT, parameters, locals, instructions);
    }
}
