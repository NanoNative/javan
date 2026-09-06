package javan.optimizer;

import javan.ir.IrClass;
import javan.ir.IrExpression;
import javan.ir.IrField;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
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
    void keepsAllocationLocalAcrossExactNonCapturingCall() {
        final IrFunction callee = new IrFunction(
            "example/Box", "<init>", "()V", "box_init", IrType.VOID,
            List.of(new IrParameter(IrType.OBJECT, "self")), List.of(),
            List.of(IrInstruction.returnVoid())
        );
        final IrFunction caller = function(
            local("value"),
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Box")),
            IrInstruction.callStaticVoid("box_init", List.of(IrExpression.objectLocal("value"))),
            IrInstruction.returnVoid()
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(new IrProgram(List.of(callee, caller), "run"));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape)
            .containsExactly(EscapeAnalyzer.Escape.NO_ESCAPE);
    }

    @Test
    void followsArgumentCaptureThroughExactCalls() {
        final IrFunction callee = new IrFunction(
            "example/Box", "publish", "(Ljava/lang/Object;)Ljava/lang/Object;", "publish", IrType.OBJECT,
            List.of(new IrParameter(IrType.OBJECT, "value")), List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectLocal("value")))
        );
        final IrFunction bridge = new IrFunction(
            "example/Box", "bridge", "(Ljava/lang/Object;)V", "bridge", IrType.VOID,
            List.of(new IrParameter(IrType.OBJECT, "value")), List.of(),
            List.of(
                IrInstruction.callStaticVoid("publish", List.of(IrExpression.objectLocal("value"))),
                IrInstruction.returnVoid()
            )
        );
        final IrFunction caller = function(
            local("value"),
            IrInstruction.assignObject("value", IrExpression.objectAllocation("example/Box")),
            IrInstruction.callStaticVoid("bridge", List.of(IrExpression.objectLocal("value"))),
            IrInstruction.returnVoid()
        );

        final EscapeAnalyzer.Analysis result = analyzer.analyze(new IrProgram(List.of(callee, bridge, caller), "run"));

        assertThat(result.sites()).extracting(EscapeAnalyzer.AllocationSite::escape)
            .containsExactly(EscapeAnalyzer.Escape.GLOBAL_ESCAPE);
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

    @Test
    void plansBoundedConstantPrimitiveArrayForReleaseStackAllocation() {
        final IrFunction function = function(
            local("value"),
            IrInstruction.assignObject("value", IrExpression.intArrayAllocation(IrExpression.intLiteral(4))),
            IrInstruction.assignArrayInt(
                IrExpression.objectLocal("value"), IrExpression.intLiteral(0), IrExpression.intLiteral(7)
            ),
            IrInstruction.returnVoid()
        );
        final IrProgram program = program(function);
        final EscapeAnalyzer.Analysis analysis = analyzer.analyze(program);

        final EscapeAnalyzer.StackAllocationPlan plan = analyzer.planStackAllocations(program, analysis, true);

        assertThat(plan.sites()).containsExactly(new EscapeAnalyzer.StackAllocationSite(
            "example/Main", "run", "()Ljava/lang/Object;", 0,
            IrExpression.Kind.INT_ARRAY_ALLOCATION, 4
        ));
    }

    @Test
    void plansBoundedObjectsIncludingManagedReferenceFields() {
        final List<IrField> oversizedFields = java.util.stream.IntStream.range(0, 600)
            .mapToObj(index -> new IrField(IrType.INT, "value" + index, "field_value_" + index))
            .toList();
        final IrFunction function = function(
            local("plain"), local("referenced"), local("oversized"),
            IrInstruction.assignObject("plain", IrExpression.objectAllocation("example/Plain")),
            IrInstruction.assignObject("referenced", IrExpression.objectAllocation("example/Referenced")),
            IrInstruction.assignObject("oversized", IrExpression.objectAllocation("example/Oversized")),
            IrInstruction.returnVoid()
        );
        final IrProgram program = new IrProgram(
            List.of(
                new IrClass("example/Plain", "plain", List.of(new IrField(IrType.INT, "value", "field_value"))),
                new IrClass(
                    "example/Referenced", "referenced",
                    List.of(new IrField(IrType.OBJECT, "value", "field_value"))
                ),
                new IrClass("example/Oversized", "oversized", oversizedFields)
            ),
            List.of(function),
            "run"
        );

        final EscapeAnalyzer.StackAllocationPlan plan = analyzer.planStackAllocations(
            program, analyzer.analyze(program), true
        );

        assertThat(plan.sites()).containsExactly(
            new EscapeAnalyzer.StackAllocationSite(
                "example/Main", "run", "()Ljava/lang/Object;", 0,
                IrExpression.Kind.OBJECT_ALLOCATION, 0
            ),
            new EscapeAnalyzer.StackAllocationSite(
                "example/Main", "run", "()Ljava/lang/Object;", 1,
                IrExpression.Kind.OBJECT_ALLOCATION, 0
            )
        );
    }

    @Test
    void plansStackAllocationOutsideAnEarlierControlFlowCycle() {
        final IrFunction function = function(
            local("value"),
            IrInstruction.label("loop"),
            IrInstruction.branchIf("done", IrExpression.intLiteral(1)),
            IrInstruction.jump("loop"),
            IrInstruction.label("done"),
            IrInstruction.assignObject("value", IrExpression.intArrayAllocation(IrExpression.intLiteral(4))),
            IrInstruction.returnVoid()
        );
        final IrProgram program = program(function);

        final EscapeAnalyzer.StackAllocationPlan plan = analyzer.planStackAllocations(
            program, analyzer.analyze(program), true
        );

        assertThat(plan.sites()).containsExactly(new EscapeAnalyzer.StackAllocationSite(
            "example/Main", "run", "()Ljava/lang/Object;", 4,
            IrExpression.Kind.INT_ARRAY_ALLOCATION, 4
        ));
    }

    @Test
    void keepsStackAllocationDisabledOutsideSafeReleaseShape() {
        final IrFunction looped = function(
            local("value"),
            IrInstruction.label("loop"),
            IrInstruction.assignObject("value", IrExpression.intArrayAllocation(IrExpression.intLiteral(4))),
            IrInstruction.branchIf("done", IrExpression.intLiteral(1)),
            IrInstruction.jump("loop"),
            IrInstruction.label("done"),
            IrInstruction.returnVoid()
        );
        final IrProgram loopedProgram = program(looped);
        final EscapeAnalyzer.Analysis loopedAnalysis = analyzer.analyze(loopedProgram);

        assertThat(analyzer.planStackAllocations(loopedProgram, loopedAnalysis, true).sites()).isEmpty();
        assertThat(analyzer.planStackAllocations(loopedProgram, loopedAnalysis, false).sites()).isEmpty();

        final IrFunction dynamic = function(
            local("value"),
            IrInstruction.assignObject("value", IrExpression.intArrayAllocation(IrExpression.intLocal("length"))),
            IrInstruction.returnVoid()
        );
        final IrProgram dynamicProgram = program(dynamic);
        assertThat(analyzer.planStackAllocations(
            dynamicProgram, analyzer.analyze(dynamicProgram), true
        ).sites()).isEmpty();

        final IrFunction oversized = function(
            local("value"),
            IrInstruction.assignObject("value", IrExpression.longArrayAllocation(IrExpression.intLiteral(1_000))),
            IrInstruction.returnVoid()
        );
        final IrProgram oversizedProgram = program(oversized);
        assertThat(analyzer.planStackAllocations(
            oversizedProgram, analyzer.analyze(oversizedProgram), true
        ).sites()).isEmpty();

        final IrFunction cumulative = function(
            local("first"), local("second"),
            IrInstruction.assignObject("first", IrExpression.intArrayAllocation(IrExpression.intLiteral(600))),
            IrInstruction.assignObject("second", IrExpression.intArrayAllocation(IrExpression.intLiteral(600))),
            IrInstruction.returnVoid()
        );
        final IrProgram cumulativeProgram = program(cumulative);
        assertThat(analyzer.planStackAllocations(
            cumulativeProgram, analyzer.analyze(cumulativeProgram), true
        ).sites()).hasSize(1);
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
