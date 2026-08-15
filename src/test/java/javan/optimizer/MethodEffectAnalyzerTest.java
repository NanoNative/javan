package javan.optimizer;

import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrProgram;
import javan.ir.IrType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class MethodEffectAnalyzerTest {
    private final MethodEffectAnalyzer analyzer = new MethodEffectAnalyzer();

    @Test
    void propagatesEffectsThroughApplicationCallsAndRecursion() {
        final IrFunction write = function(
            "write",
            IrInstruction.assignStaticFieldInt("example/State", "value", IrExpression.intLiteral(1)),
            IrInstruction.returnVoid()
        );
        final IrFunction allocate = function(
            "allocate",
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Value")),
            IrInstruction.returnVoid()
        );
        final IrFunction left = function(
            "left",
            IrInstruction.callStaticVoid("right"),
            IrInstruction.returnVoid()
        );
        final IrFunction right = function(
            "right",
            IrInstruction.callStaticVoid("left"),
            IrInstruction.callStaticVoid(write.symbol()),
            IrInstruction.callStaticVoid(allocate.symbol()),
            IrInstruction.returnVoid()
        );

        final MethodEffectAnalyzer.Analysis result = analyzer.analyze(program(write, allocate, left, right));

        assertThat(result.effect(left.symbol()).writes()).isTrue();
        assertThat(result.effect(left.symbol()).allocates()).isTrue();
        assertThat(result.effect(left.symbol()).unknown()).isFalse();
        assertThat(result.preservesMemoryFacts(left.symbol())).isFalse();
    }

    @Test
    void distinguishesPureThrowingReadingAndUnknownCalls() {
        final IrFunction pure = function(
            "pure",
            IrInstruction.returnInt(IrExpression.intLiteral(7))
        );
        final IrFunction throwing = function(
            "throwing",
            IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectNull())),
            IrInstruction.returnVoid()
        );
        final IrFunction reading = function(
            "reading",
            IrInstruction.returnInt(IrExpression.intField(
                "example/Value",
                "number",
                IrExpression.objectLocal("receiver")
            ))
        );
        final IrFunction unknown = function(
            "unknown",
            IrInstruction.callStaticVoid("external_native"),
            IrInstruction.returnVoid()
        );

        final MethodEffectAnalyzer.Analysis result = analyzer.analyze(program(pure, throwing, reading, unknown));

        assertThat(result.effect(pure.symbol()).pure()).isTrue();
        assertThat(result.effect(pure.symbol()).mayThrow()).isFalse();
        assertThat(result.effect(throwing.symbol()).pure()).isTrue();
        assertThat(result.effect(throwing.symbol()).mayThrow()).isTrue();
        assertThat(result.effect(reading.symbol()).reads()).isTrue();
        assertThat(result.preservesMemoryFacts(reading.symbol())).isTrue();
        assertThat(result.effect(unknown.symbol()).unknown()).isTrue();
        assertThat(result.effect(unknown.symbol()).mayThrow()).isTrue();
        assertThat(result.preservesMemoryFacts(unknown.symbol())).isFalse();
    }

    @Test
    void joinsEveryClosedWorldDispatchTarget() {
        final IrFunction pure = function("pureTarget", IrInstruction.returnVoid());
        final IrFunction writer = function(
            "writeTarget",
            IrInstruction.assignStaticFieldInt("example/State", "value", IrExpression.intLiteral(1)),
            IrInstruction.returnVoid()
        );
        final IrFunction caller = function(
            "caller",
            IrInstruction.callStaticVoid("dispatch_run"),
            IrInstruction.returnVoid()
        );
        final IrDispatch dispatch = new IrDispatch(
            "dispatch_run",
            IrType.VOID,
            List.of(),
            List.of(
                new IrDispatchTarget("example/Pure", pure.symbol()),
                new IrDispatchTarget("example/Writer", writer.symbol())
            )
        );
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(caller, pure, writer),
            List.of(dispatch),
            caller.symbol(),
            List.of(),
            Map.of(),
            Map.of()
        );

        final MethodEffectAnalyzer.Effect effect = analyzer.analyze(program).effect(caller.symbol());

        assertThat(effect.writes()).isTrue();
        assertThat(effect.unknown()).isFalse();
    }

    @Test
    void incompleteDispatchFallsBackToUnknown() {
        final IrFunction caller = function(
            "caller",
            IrInstruction.callStaticVoid("dispatch_run"),
            IrInstruction.returnVoid()
        );
        final IrDispatch dispatch = new IrDispatch(
            "dispatch_run",
            IrType.VOID,
            List.of(),
            List.of(new IrDispatchTarget("example/Missing", "missing_target"))
        );
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(caller),
            List.of(dispatch),
            caller.symbol(),
            List.of(),
            Map.of(),
            Map.of()
        );

        assertThat(analyzer.analyze(program).effect(caller.symbol()).unknown()).isTrue();
    }

    private static IrProgram program(final IrFunction... functions) {
        return new IrProgram(List.of(functions), functions[0].symbol());
    }

    private static IrFunction function(final String symbol, final IrInstruction... instructions) {
        return new IrFunction(
            "example/Main",
            symbol,
            "()V",
            symbol,
            instructions[instructions.length - 1].op() == IrInstruction.Op.RETURN_INT ? IrType.INT : IrType.VOID,
            List.of(),
            List.of(new IrLocal(IrType.OBJECT, "value"), new IrLocal(IrType.OBJECT, "receiver")),
            List.of(instructions)
        );
    }
}
