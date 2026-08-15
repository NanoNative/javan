package javan.optimizer;

import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrProgram;
import javan.ir.IrType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class EscapeAnalyzerTest {
    private final EscapeAnalyzer analyzer = new EscapeAnalyzer();

    @Test
    void classifiesLocalArgumentAndReturnedAllocations() {
        final IrFunction function = function(
            local("local"), local("argument"), local("global"),
            IrInstruction.assignObject("local", IrExpression.objectAllocation("example/Local")),
            IrInstruction.assignObject("argument", IrExpression.intArrayAllocation(IrExpression.intLiteral(2))),
            IrInstruction.callStaticVoid("external", List.of(IrExpression.objectLocal("argument"))),
            IrInstruction.assignObject("global", IrExpression.objectAllocation("example/Global")),
            IrInstruction.returnObject(IrExpression.objectLocal("global"))
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape).containsExactly(
            EscapeAnalyzer.Escape.NO_ESCAPE,
            EscapeAnalyzer.Escape.ARGUMENT_ESCAPE,
            EscapeAnalyzer.Escape.GLOBAL_ESCAPE
        );
    }

    @Test
    void followsCopiesAndControlFlowWithoutMergingOverwrittenValues() {
        final IrFunction function = function(
            local("value"), local("copy"),
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Unused")),
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Returned")),
            IrInstruction.branchIf("copy", IrExpression.intLiteral(1)),
            IrInstruction.returnVoid(),
            IrInstruction.label("copy"),
            IrInstruction.assignObject("copy", IrExpression.objectLocal("value")),
            IrInstruction.returnObject(IrExpression.objectLocal("copy"))
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape).containsExactly(
            EscapeAnalyzer.Escape.NO_ESCAPE,
            EscapeAnalyzer.Escape.GLOBAL_ESCAPE
        );
    }

    @Test
    void convergesAcrossLoops() {
        final IrFunction function = function(
            local("value"),
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Value")),
            IrInstruction.label("loop"),
            IrInstruction.branchIf("done", IrExpression.intLocal("condition")),
            IrInstruction.jump("loop"),
            IrInstruction.label("done"),
            IrInstruction.returnObject(IrExpression.objectLocal("value"))
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape)
            .containsExactly(EscapeAnalyzer.Escape.GLOBAL_ESCAPE);
    }

    @Test
    void treatsHeapAndStaticStoresAsGlobalEscapes() {
        final IrFunction function = function(
            local("receiver"), local("fieldValue"), local("staticValue"),
            IrInstruction.assignObject("receiver", IrExpression.objectAllocation("example/Holder")),
            IrInstruction.assignObject("fieldValue", IrExpression.objectAllocation("example/FieldValue")),
            IrInstruction.assignFieldObject(
                "example/Holder", "value", IrExpression.objectLocal("receiver"), IrExpression.objectLocal("fieldValue")
            ),
            IrInstruction.assignObject("staticValue", IrExpression.objectArrayAllocation(IrExpression.intLiteral(1))),
            IrInstruction.assignStaticFieldObject("example/Main", "value", IrExpression.objectLocal("staticValue")),
            IrInstruction.returnVoid()
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape).containsExactly(
            EscapeAnalyzer.Escape.NO_ESCAPE,
            EscapeAnalyzer.Escape.GLOBAL_ESCAPE,
            EscapeAnalyzer.Escape.GLOBAL_ESCAPE
        );
    }

    @Test
    void treatsObjectArrayStoresAsGlobalEscapes() {
        final IrFunction function = function(
            local("array"), local("value"),
            IrInstruction.assignObject("array", IrExpression.objectArrayAllocation(IrExpression.intLiteral(1))),
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Value")),
            IrInstruction.assignArrayObject(
                IrExpression.objectLocal("array"), IrExpression.intLiteral(0), IrExpression.objectLocal("value")
            ),
            IrInstruction.returnVoid()
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape).containsExactly(
            EscapeAnalyzer.Escape.NO_ESCAPE,
            EscapeAnalyzer.Escape.GLOBAL_ESCAPE
        );
    }

    @Test
    void classifiesConcatArgumentsAndResultSeparately() {
        final IrFunction function = function(
            local("part"), local("text"),
            IrInstruction.assignObject("part", IrExpression.objectAllocation("example/Part")),
            IrInstruction.assignObject("text", IrExpression.stringConcat("\u0001", List.of(IrExpression.objectLocal("part")))),
            IrInstruction.returnVoid()
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape).containsExactly(
            EscapeAnalyzer.Escape.ARGUMENT_ESCAPE,
            EscapeAnalyzer.Escape.NO_ESCAPE
        );
    }

    @Test
    void fallsBackToGlobalEscapeWhenAnalysisWouldExceedItsBound() {
        final List<IrLocal> locals = new java.util.ArrayList<>();
        for (int index = 0; index < 101; index++) {
            locals.add(local("value" + index));
        }
        final List<IrInstruction> instructions = new java.util.ArrayList<>();
        instructions.add(IrInstruction.assignObject("value0", IrExpression.objectAllocation("example/Value")));
        for (int index = 0; index < 200; index++) {
            instructions.add(IrInstruction.assignObject("value1", IrExpression.objectNull()));
        }
        instructions.add(IrInstruction.returnVoid());
        final IrFunction function = new IrFunction(
            "example/Main", "large", "()V", "large", IrType.VOID, List.of(), locals, instructions
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(program(function));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape)
            .containsExactly(EscapeAnalyzer.Escape.GLOBAL_ESCAPE);
    }

    private static IrLocal local(final String name) {
        return new IrLocal(IrType.OBJECT, name);
    }

    private static IrFunction function(final Object... values) {
        final List<IrLocal> locals = java.util.Arrays.stream(values)
            .filter(IrLocal.class::isInstance).map(IrLocal.class::cast).toList();
        final List<IrInstruction> instructions = java.util.Arrays.stream(values)
            .filter(IrInstruction.class::isInstance).map(IrInstruction.class::cast).toList();
        return new IrFunction("example/Main", "run", "()Ljava/lang/Object;", "run", IrType.OBJECT,
            List.of(), locals, instructions);
    }

    private static IrProgram program(final IrFunction function) {
        return new IrProgram(List.of(function), "run");
    }
}
