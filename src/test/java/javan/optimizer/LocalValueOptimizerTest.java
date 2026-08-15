package javan.optimizer;

import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrProgram;
import javan.ir.IrType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

        final LocalValueOptimizer.Result result = optimize(new IrProgram(List.of(function), function.symbol()), true);
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

        final LocalValueOptimizer.Result result = optimize(program, false);

        assertThat(result.program()).isEqualTo(program);
        assertThat(result.report().redundantNullChecks()).isZero();
        assertThat(result.proofs()).hasSize(1);
        assertThat(result.facts().arrayLengths()).isPositive();
        assertThat(result.facts().exactTypes()).isPositive();
    }

    @Test
    void functionsWithoutOptimizationCandidatesAvoidDataflow() {
        final IrFunction function = function(
            List.of(),
            List.of(new IrLocal(IrType.INT, "value")),
            List.of(
                IrInstruction.assignInt("value", IrExpression.intLiteral(7)),
                IrInstruction.returnInt(IrExpression.intLocal("value"))
            )
        );
        final IrProgram program = new IrProgram(List.of(function), function.symbol());

        final LocalValueOptimizer.Result result = optimize(program, false);

        assertThat(result.program()).isSameAs(program);
        assertThat(result.facts()).isEqualTo(new LocalValueOptimizer.FactSummary(0, 0, 0, 0, 0, 0, 0));
        assertThat(result.proofs()).isEmpty();
    }

    @Test
    void largeFunctionsAreSkippedConservatively() {
        final List<IrLocal> locals = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            locals.add(new IrLocal(IrType.INT, "value" + index));
        }
        final List<IrInstruction> instructions = new ArrayList<>();
        instructions.add(IrInstruction.assignInt("value0", IrExpression.intLiteral(1)));
        for (int index = 1; index < 200; index++) {
            instructions.add(IrInstruction.assignInt("value" + index, IrExpression.intLocal("value" + (index - 1))));
        }
        for (int index = 0; index < 500; index++) {
            instructions.add(IrInstruction.assignInt("value0", IrExpression.intLiteral(index)));
        }
        instructions.add(IrInstruction.branchIf("done", IrExpression.intLocal("value199")));
        instructions.add(IrInstruction.label("done"));
        instructions.add(IrInstruction.returnInt(IrExpression.intLocal("value0")));
        final IrFunction function = function(List.of(), locals, instructions);
        final IrProgram program = new IrProgram(List.of(function), function.symbol());

        final LocalValueOptimizer.Result result = optimize(program, true);

        assertThat(result.program().functions().getFirst().instructions()).isEqualTo(instructions);
        assertThat(result.report().skippedCandidates()).isEqualTo(1);
        assertThat(result.proofs()).isEmpty();
    }

    @Test
    void loopRangesConvergeWithoutInventingAConstantBranch() {
        final IrFunction function = function(
            List.of(),
            List.of(new IrLocal(IrType.INT, "counter")),
            List.of(
                IrInstruction.assignInt("counter", IrExpression.intLiteral(0)),
                IrInstruction.label("loop"),
                IrInstruction.assignInt("counter", IrExpression.intBinary(
                    "+",
                    IrExpression.intLocal("counter"),
                    IrExpression.intLiteral(1)
                )),
                IrInstruction.branchIf("loop", IrExpression.intComparison(
                    "<",
                    IrExpression.intLocal("counter"),
                    IrExpression.intLiteral(100)
                )),
                IrInstruction.returnInt(IrExpression.intLocal("counter"))
            )
        );

        final LocalValueOptimizer.Result result = optimize(
            new IrProgram(List.of(function), function.symbol()),
            true
        );

        assertThat(result.report().skippedCandidates()).isZero();
        assertThat(result.program().functions().getFirst().instructions())
            .anyMatch(instruction -> instruction.op() == IrInstruction.Op.BRANCH_IF);
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

        final LocalValueOptimizer.Result result = optimize(new IrProgram(List.of(function), function.symbol()), true);

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

        final LocalValueOptimizer.Result result = optimize(new IrProgram(List.of(function), function.symbol()), true);

        assertThat(result.program().functions().getFirst().instructions())
            .anyMatch(instruction -> instruction.value().filter("maybe"::equals).isPresent());
    }

    @Test
    void pureCallsPreserveFieldFactsAndUnknownCallsInvalidateThem() {
        final IrFunction pure = new IrFunction(
            "example/Main",
            "pure",
            "()V",
            "example_Main_pure",
            IrType.VOID,
            List.of(),
            List.of(),
            List.of(IrInstruction.returnVoid())
        );
        final IrProgram pureProgram = fieldBranchProgram(pure.symbol(), pure, false);
        final IrProgram unknownProgram = fieldBranchProgram("external_unknown", pure, false);
        final IrProgram reassignedProgram = fieldBranchProgram(pure.symbol(), pure, true);

        final LocalValueOptimizer.Result preserved = optimize(pureProgram, true);
        final LocalValueOptimizer.Result invalidated = optimize(unknownProgram, true);
        final LocalValueOptimizer.Result reassigned = optimize(reassignedProgram, true);

        assertThat(preserved.report().deadBranches()).isEqualTo(1);
        assertThat(preserved.program().functions().getFirst().instructions())
            .noneMatch(instruction -> instruction.op() == IrInstruction.Op.BRANCH_IF);
        assertThat(invalidated.report().deadBranches()).isZero();
        assertThat(invalidated.program().functions().getFirst().instructions())
            .anyMatch(instruction -> instruction.op() == IrInstruction.Op.BRANCH_IF);
        assertThat(reassigned.report().deadBranches()).isZero();
        assertThat(reassigned.program().functions().getFirst().instructions())
            .anyMatch(instruction -> instruction.op() == IrInstruction.Op.BRANCH_IF);
    }

    private static IrProgram fieldBranchProgram(
        final String call,
        final IrFunction pure,
        final boolean reassignReceiver
    ) {
        final List<IrInstruction> instructions = new ArrayList<>(List.of(
            IrInstruction.assignObject("box", IrExpression.objectAllocation("example/Box")),
            IrInstruction.assignFieldInt(
                "example/Box",
                "number",
                IrExpression.objectLocal("box"),
                IrExpression.intLiteral(7)
            ),
            IrInstruction.callStaticVoid(call)
        ));
        if (reassignReceiver) {
            instructions.add(IrInstruction.assignObject("box", IrExpression.objectAllocation("example/Box")));
        }
        instructions.addAll(List.of(
            IrInstruction.assignInt("number", IrExpression.intField(
                "example/Box",
                "number",
                IrExpression.objectLocal("box")
            )),
            IrInstruction.branchIf("done", IrExpression.intComparison(
                "==",
                IrExpression.intLocal("number"),
                IrExpression.intLiteral(7)
            )),
            IrInstruction.returnInt(IrExpression.intLiteral(0)),
            IrInstruction.label("done"),
            IrInstruction.returnInt(IrExpression.intLiteral(1))
        ));
        final IrFunction caller = new IrFunction(
            "example/Main",
            "fieldBranch",
            "()I",
            "example_Main_fieldBranch",
            IrType.INT,
            List.of(),
            List.of(new IrLocal(IrType.OBJECT, "box"), new IrLocal(IrType.INT, "number")),
            List.copyOf(instructions)
        );
        return new IrProgram(List.of(caller, pure), caller.symbol());
    }

    private LocalValueOptimizer.Result optimize(final IrProgram program, final boolean release) {
        return optimizer.optimize(program, release, new MethodEffectAnalyzer().analyze(program));
    }

    private static IrFunction function(
        final List<IrParameter> parameters,
        final List<IrLocal> locals,
        final List<IrInstruction> instructions
    ) {
        return new IrFunction("example/Main", "run", "()I", "example_Main_run", IrType.INT, parameters, locals, instructions);
    }
}
