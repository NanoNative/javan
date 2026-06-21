package javan.codegen;

import javan.analysis.EntryPoint;
import javan.build.AbiType;
import javan.build.ExportedMethod;
import javan.ir.IrClass;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
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

final class CCodegenMemoryTest {
    @TempDir
    private Path tempDir;

    @Test
    void emitsStatementSafePointAfterCompletedAssignment() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void* javan_expr_tmp_0 = 0;",
            "javan_root_frame_push(javan_expr_roots, 1);",
            "javan_expr_tmp_0 = javan_new_com_acme_Node();",
            "tmp = javan_expr_tmp_0;",
            "javan_root_frame_pop(javan_expr_roots);",
            "javan_gc_safe_point();"
        );
    }

    @Test
    void clearsUnusedAssignedObjectLocalBeforeStatementSafePoint() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "tmp = javan_expr_tmp_0;",
            "javan_root_frame_pop(javan_expr_roots);",
            "\n    tmp = 0;\n",
            "javan_gc_safe_point();"
        );
        final int clear = generated.indexOf("\n    tmp = 0;\n");
        assertThat(generated.indexOf("tmp = javan_expr_tmp_0;")).isLessThan(clear);
        assertThat(clear).isLessThan(generated.indexOf("javan_gc_safe_point();", clear));
    }

    @Test
    void clearsObjectLocalAfterLastStraightLineUseBeforeStatementSafePoint() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.printlnObject(IrExpression.objectLocal("tmp")),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_println((const char*) tmp);",
            "\n    tmp = 0;\n",
            "javan_gc_safe_point();"
        );
        final int clear = generated.indexOf("\n    tmp = 0;\n");
        assertThat(generated.indexOf("javan_println((const char*) tmp);")).isLessThan(clear);
        assertThat(clear).isLessThan(generated.indexOf("javan_gc_safe_point();", clear));
    }

    @Test
    void keepsLoopCarriedRootLiveAcrossBackedge() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.label("again"),
                    IrInstruction.printlnObject(IrExpression.objectLocal("tmp")),
                    IrInstruction.branchIf("again", IrExpression.intLiteral(0)),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("javan_println((const char*) tmp);");
        assertThat(generated).contains("again:\n    javan_gc_safe_point();\n    javan_println((const char*) tmp);");
        assertThat(generated).doesNotContain("again:\n    tmp = 0;\n");
    }

    @Test
    void clearsDeadLoopLocalBeforeBackedgeSafePoint() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.label("again"),
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.printlnObject(IrExpression.objectLocal("tmp")),
                    IrInstruction.branchIf("again", IrExpression.intLiteral(0)),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_println((const char*) tmp);",
            "\n    tmp = 0;\n",
            "javan_gc_safe_point();",
            "if (0) goto again;"
        );
        final int clear = generated.indexOf("\n    tmp = 0;\n");
        assertThat(generated.indexOf("javan_println((const char*) tmp);")).isLessThan(clear);
        assertThat(clear).isLessThan(generated.indexOf("if (0) goto again;"));
    }

    @Test
    void clearsDeadRootAtJoinLabelSafePoint() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.branchIf("done", IrExpression.intLiteral(1)),
                    IrInstruction.printlnObject(IrExpression.objectLocal("tmp")),
                    IrInstruction.label("done"),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("done:\n    tmp = 0;\n    javan_gc_safe_point();");
    }

    @Test
    void clearsBranchFallthroughWhenRootLiveOnlyOnTakenEdge() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.branchIf("use", IrExpression.intLiteral(1)),
                    IrInstruction.printlnInt(IrExpression.intLiteral(3)),
                    IrInstruction.jump("done"),
                    IrInstruction.label("use"),
                    IrInstruction.printlnObject(IrExpression.objectLocal("tmp")),
                    IrInstruction.label("done"),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("if (1) goto use;\n    tmp = 0;\n    javan_gc_safe_point();");
        assertThat(generated).doesNotContain("use:\n    tmp = 0;\n");
    }

    @Test
    void doesNotClearAtJoinLabelWhenRootLiveAfterJoin() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.branchIf("join", IrExpression.intLiteral(1)),
                    IrInstruction.printlnInt(IrExpression.intLiteral(5)),
                    IrInstruction.label("join"),
                    IrInstruction.printlnObject(IrExpression.objectLocal("tmp")),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("join:\n    javan_gc_safe_point();\n    javan_println((const char*) tmp);");
    }

    @Test
    void doesNotClearRootsWhenControlFlowTargetIsMissing() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "tmp")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.branchIf("missing", IrExpression.intLiteral(0)),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("if (0) goto missing;");
        assertThat(generated).doesNotContain("\n    tmp = 0;\n");
    }

    @Test
    void skipsStatementSafePointAfterTerminalJump() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(
                    IrInstruction.jump("done"),
                    IrInstruction.label("done"),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).doesNotContain("goto done;\n    javan_gc_safe_point();");
    }

    @Test
    void rootsObjectReturnThroughCalleeSafePointWithoutFunctionRoots() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                new IrFunction(
                    "com/acme/Main",
                    "main",
                    "([Ljava/lang/String;)V",
                    "main_symbol",
                    IrType.VOID,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                ),
                new IrFunction(
                    "com/acme/Factory",
                    "make",
                    "()Lcom/acme/Node;",
                    "make_symbol",
                    IrType.OBJECT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Node")))
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_expr_tmp_0 = javan_new_com_acme_Node();",
            "void* javan_return_value = javan_expr_tmp_0;",
            "javan_generated_return_root = javan_return_value;",
            "javan_gc_safe_point();",
            "javan_root_frame_pop(javan_expr_roots);",
            "javan_generated_return_root = 0;",
            "return javan_return_value;"
        );
        assertThat(generated.indexOf("javan_expr_tmp_0 = javan_new_com_acme_Node();"))
            .isLessThan(generated.indexOf("void* javan_return_value = javan_expr_tmp_0;"));
        assertThat(generated.indexOf("void* javan_return_value = javan_expr_tmp_0;"))
            .isLessThan(generated.indexOf("javan_generated_return_root = javan_return_value;"));
        assertThat(generated.indexOf("javan_generated_return_root = javan_return_value;"))
            .isLessThan(generated.lastIndexOf("javan_generated_return_root = 0;"));
    }

    @Test
    void popsFunctionRootsAfterReturnRoots() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                new IrFunction(
                    "com/acme/Main",
                    "main",
                    "([Ljava/lang/String;)V",
                    "main_symbol",
                    IrType.VOID,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                ),
                new IrFunction(
                    "com/acme/Factory",
                    "pick",
                    "(Lcom/acme/Node;)Lcom/acme/Node;",
                    "pick_symbol",
                    IrType.OBJECT,
                    List.of(new IrParameter(IrType.OBJECT, "self")),
                    List.of(new IrLocal(IrType.OBJECT, "tmp")),
                    List.of(
                        IrInstruction.assignObject("tmp", IrExpression.objectLocal("self")),
                        IrInstruction.returnObject(IrExpression.objectLocal("tmp"))
                    )
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_generated_return_root = javan_return_value;\n        javan_gc_safe_point();\n        javan_root_frame_pop(javan_roots_pick_symbol);\n        javan_generated_return_root = 0;\n        return javan_return_value;"
        );
    }

    @Test
    void includesObjectReturnSlotInStaticRootInventory() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                new IrFunction(
                    "com/acme/Main",
                    "main",
                    "([Ljava/lang/String;)V",
                    "main_symbol",
                    IrType.VOID,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                ),
                new IrFunction(
                    "com/acme/Factory",
                    "make",
                    "()Lcom/acme/Node;",
                    "make_symbol",
                    IrType.OBJECT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Node")))
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "static void* javan_generated_return_root = 0;",
            "(void**) &javan_generated_return_root",
            "javan_register_static_roots(javan_static_roots, 1);"
        );
    }

    @Test
    void rootsNestedObjectCallArgumentsUntilStatementCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid(
                    "consume_symbol",
                    List.of(
                        IrExpression.objectCall("make_symbol", List.of()),
                        IrExpression.objectCall("make_symbol", List.of())
                    )
                )),
                makeFunction(),
                new IrFunction(
                    "com/acme/Consumer",
                    "consume",
                    "(Lcom/acme/Node;Lcom/acme/Node;)V",
                    "consume_symbol",
                    IrType.VOID,
                    List.of(new IrParameter(IrType.OBJECT, "left"), new IrParameter(IrType.OBJECT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void* javan_expr_tmp_0 = 0;",
            "void* javan_expr_tmp_1 = 0;",
            "javan_root_frame_push(javan_expr_roots, 2);",
            "javan_expr_tmp_0 = make_symbol();",
            "javan_expr_tmp_1 = make_symbol();",
            "consume_symbol(javan_expr_tmp_0, javan_expr_tmp_1);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_expr_roots, 2);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_0 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = make_symbol();"))
            .isLessThan(generated.indexOf("consume_symbol(javan_expr_tmp_0, javan_expr_tmp_1);"));
    }

    @Test
    void sequencesPrimitiveCallArgumentsBeforeOuterCall() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid(
                    "consume_ints_symbol",
                    List.of(
                        IrExpression.intCall("next_int_symbol", List.of()),
                        IrExpression.intCall("next_int_symbol", List.of())
                    )
                )),
                new IrFunction(
                    "com/acme/Numbers",
                    "next",
                    "()I",
                    "next_int_symbol",
                    IrType.INT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnInt(IrExpression.intLiteral(1)))
                ),
                new IrFunction(
                    "com/acme/Numbers",
                    "consume",
                    "(II)V",
                    "consume_ints_symbol",
                    IrType.VOID,
                    List.of(new IrParameter(IrType.INT, "left"), new IrParameter(IrType.INT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "int javan_expr_tmp_0 = 0;",
            "int javan_expr_tmp_1 = 0;",
            "javan_expr_tmp_0 = next_int_symbol();",
            "javan_expr_tmp_1 = next_int_symbol();",
            "consume_ints_symbol(javan_expr_tmp_0, javan_expr_tmp_1);"
        );
        assertThat(generated.indexOf("javan_expr_tmp_0 = next_int_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = next_int_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = next_int_symbol();"))
            .isLessThan(generated.indexOf("consume_ints_symbol(javan_expr_tmp_0, javan_expr_tmp_1);"));
    }

    @Test
    void rootsFieldStoreReceiverAndObjectValueUntilStoreCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClassWithNext()),
            List.of(
                mainWithInstruction(IrInstruction.assignFieldObject(
                    "com/acme/Node",
                    "next",
                    IrExpression.objectCall("make_symbol", List.of()),
                    IrExpression.objectCall("make_symbol", List.of())
                )),
                makeFunction()
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_root_frame_push(javan_expr_roots, 2);",
            "javan_expr_tmp_0 = make_symbol();",
            "javan_expr_tmp_1 = make_symbol();",
            "((struct javan_class_com_acme_Node*) javan_expr_tmp_0)->field_next = javan_expr_tmp_1;",
            "javan_root_frame_pop(javan_expr_roots);"
        );
    }

    @Test
    void rootsObjectFieldLoadReceiverAndResultUntilCallCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass(), holderClass()),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid(
                    "consume_symbol",
                    List.of(
                        IrExpression.objectField(
                            "com/acme/Holder",
                            "child",
                            IrExpression.objectCall("make_holder_symbol", List.of())
                        ),
                        IrExpression.objectCall("make_symbol", List.of())
                    )
                )),
                new IrFunction(
                    "com/acme/Factory",
                    "makeHolder",
                    "()Lcom/acme/Holder;",
                    "make_holder_symbol",
                    IrType.OBJECT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Holder")))
                ),
                makeFunction(),
                new IrFunction(
                    "com/acme/Consumer",
                    "consume",
                    "(Lcom/acme/Node;Lcom/acme/Node;)V",
                    "consume_symbol",
                    IrType.VOID,
                    List.of(new IrParameter(IrType.OBJECT, "left"), new IrParameter(IrType.OBJECT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void* javan_expr_tmp_0 = 0;",
            "void* javan_expr_tmp_1 = 0;",
            "void* javan_expr_tmp_2 = 0;",
            "javan_root_frame_push(javan_expr_roots, 3);",
            "javan_expr_tmp_0 = make_holder_symbol();",
            "javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;",
            "javan_expr_tmp_2 = make_symbol();",
            "consume_symbol(javan_expr_tmp_1, javan_expr_tmp_2);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_expr_roots, 3);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_0 = make_holder_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_holder_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;"))
            .isLessThan(generated.indexOf("javan_expr_tmp_2 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_2 = make_symbol();"))
            .isLessThan(generated.indexOf("consume_symbol(javan_expr_tmp_1, javan_expr_tmp_2);"));
        assertThat(generated.indexOf("consume_symbol(javan_expr_tmp_1, javan_expr_tmp_2);"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_expr_roots);"));
    }

    @Test
    void rootsChainedObjectFieldLoadReceiversAndResultUntilCallCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClassWithNext(), holderClass()),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid(
                    "consume_symbol",
                    List.of(
                        IrExpression.objectField(
                            "com/acme/Node",
                            "next",
                            IrExpression.objectField(
                                "com/acme/Holder",
                                "child",
                                IrExpression.objectCall("make_holder_symbol", List.of())
                            )
                        ),
                        IrExpression.objectCall("make_symbol", List.of())
                    )
                )),
                new IrFunction(
                    "com/acme/Factory",
                    "makeHolder",
                    "()Lcom/acme/Holder;",
                    "make_holder_symbol",
                    IrType.OBJECT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Holder")))
                ),
                makeFunction(),
                new IrFunction(
                    "com/acme/Consumer",
                    "consume",
                    "(Lcom/acme/Node;Lcom/acme/Node;)V",
                    "consume_symbol",
                    IrType.VOID,
                    List.of(new IrParameter(IrType.OBJECT, "left"), new IrParameter(IrType.OBJECT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void* javan_expr_tmp_0 = 0;",
            "void* javan_expr_tmp_1 = 0;",
            "void* javan_expr_tmp_2 = 0;",
            "void* javan_expr_tmp_3 = 0;",
            "javan_root_frame_push(javan_expr_roots, 4);",
            "javan_expr_tmp_0 = make_holder_symbol();",
            "javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;",
            "javan_expr_tmp_2 = ((struct javan_class_com_acme_Node*) javan_expr_tmp_1)->field_next;",
            "javan_expr_tmp_3 = make_symbol();",
            "consume_symbol(javan_expr_tmp_2, javan_expr_tmp_3);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_expr_roots, 4);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_0 = make_holder_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_holder_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;"))
            .isLessThan(generated.indexOf("javan_expr_tmp_2 = ((struct javan_class_com_acme_Node*) javan_expr_tmp_1)->field_next;"));
        assertThat(generated.indexOf("javan_expr_tmp_2 = ((struct javan_class_com_acme_Node*) javan_expr_tmp_1)->field_next;"))
            .isLessThan(generated.indexOf("javan_expr_tmp_3 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_3 = make_symbol();"))
            .isLessThan(generated.indexOf("consume_symbol(javan_expr_tmp_2, javan_expr_tmp_3);"));
        assertThat(generated.indexOf("consume_symbol(javan_expr_tmp_2, javan_expr_tmp_3);"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_expr_roots);"));
    }

    @Test
    void rootsChainedObjectCallReceiversArgumentsAndResultUntilOuterCallCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClassWithNext(), holderClass()),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid(
                    "consume_symbol",
                    List.of(
                        IrExpression.objectCall(
                            "link_symbol",
                            List.of(
                                IrExpression.objectField(
                                    "com/acme/Holder",
                                    "child",
                                    IrExpression.objectCall("make_holder_symbol", List.of())
                                ),
                                IrExpression.objectCall("make_symbol", List.of())
                            )
                        ),
                        IrExpression.objectCall("make_symbol", List.of())
                    )
                )),
                new IrFunction(
                    "com/acme/Factory",
                    "makeHolder",
                    "()Lcom/acme/Holder;",
                    "make_holder_symbol",
                    IrType.OBJECT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Holder")))
                ),
                new IrFunction(
                    "com/acme/Node",
                    "link",
                    "(Lcom/acme/Node;)Lcom/acme/Node;",
                    "link_symbol",
                    IrType.OBJECT,
                    List.of(new IrParameter(IrType.OBJECT, "receiver"), new IrParameter(IrType.OBJECT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Node")))
                ),
                makeFunction(),
                new IrFunction(
                    "com/acme/Consumer",
                    "consume",
                    "(Lcom/acme/Node;Lcom/acme/Node;)V",
                    "consume_symbol",
                    IrType.VOID,
                    List.of(new IrParameter(IrType.OBJECT, "left"), new IrParameter(IrType.OBJECT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void* javan_expr_tmp_0 = 0;",
            "void* javan_expr_tmp_1 = 0;",
            "void* javan_expr_tmp_2 = 0;",
            "void* javan_expr_tmp_3 = 0;",
            "void* javan_expr_tmp_4 = 0;",
            "javan_root_frame_push(javan_expr_roots, 5);",
            "javan_expr_tmp_0 = make_holder_symbol();",
            "javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;",
            "javan_expr_tmp_2 = make_symbol();",
            "javan_expr_tmp_3 = link_symbol(javan_expr_tmp_1, javan_expr_tmp_2);",
            "javan_expr_tmp_4 = make_symbol();",
            "consume_symbol(javan_expr_tmp_3, javan_expr_tmp_4);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_expr_roots, 5);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_0 = make_holder_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_holder_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = ((struct javan_class_com_acme_Holder*) javan_expr_tmp_0)->field_child;"))
            .isLessThan(generated.indexOf("javan_expr_tmp_2 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_2 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_3 = link_symbol(javan_expr_tmp_1, javan_expr_tmp_2);"));
        assertThat(generated.indexOf("javan_expr_tmp_3 = link_symbol(javan_expr_tmp_1, javan_expr_tmp_2);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_4 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_4 = make_symbol();"))
            .isLessThan(generated.indexOf("consume_symbol(javan_expr_tmp_3, javan_expr_tmp_4);"));
        assertThat(generated.indexOf("consume_symbol(javan_expr_tmp_3, javan_expr_tmp_4);"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_expr_roots);"));
    }

    @Test
    void rootsObjectPrintOperandUntilPrintCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.printlnObject(IrExpression.objectCall("make_symbol", List.of()))),
                makeFunction()
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_root_frame_push(javan_expr_roots, 1);",
            "javan_expr_tmp_0 = make_symbol();",
            "javan_println((const char*) javan_expr_tmp_0);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_println((const char*) javan_expr_tmp_0);"));
        assertThat(generated.indexOf("javan_println((const char*) javan_expr_tmp_0);"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_expr_roots);"));
    }

    @Test
    void rootsObjectArrayStoreReceiverAndValueUntilStoreCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.assignArrayObject(
                    IrExpression.objectArrayAllocation(IrExpression.intLiteral(1)),
                    IrExpression.intLiteral(0),
                    IrExpression.objectCall("make_symbol", List.of())
                )),
                makeFunction()
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_root_frame_push(javan_expr_roots, 2);",
            "javan_expr_tmp_0 = javan_object_array_new(1);",
            "javan_expr_tmp_1 = make_symbol();",
            "javan_object_array_set(javan_expr_tmp_0, 0, javan_expr_tmp_1);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_expr_tmp_0 = javan_object_array_new(1);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_object_array_set(javan_expr_tmp_0, 0, javan_expr_tmp_1);"));
    }

    @Test
    void branchWithObjectCallConditionPopsTemporaryRootsBeforeJump() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.branchIf("done", IrExpression.objectCall("make_symbol", List.of()))),
                makeFunction()
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_root_frame_push(javan_expr_roots, 1);",
            "javan_expr_tmp_0 = make_symbol();",
            "if (javan_expr_tmp_0) {",
            "javan_root_frame_pop(javan_expr_roots);",
            "goto done;",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("if (javan_expr_tmp_0) {"))
            .isLessThan(generated.indexOf("goto done;"));
    }

    @Test
    void rootsObjectCompareOperandsUntilBranchCompletes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.branchIf(
                    "done",
                    IrExpression.objectComparison(
                        "!=",
                        IrExpression.objectCall("make_symbol", List.of()),
                        IrExpression.objectCall("make_symbol", List.of())
                    )
                )),
                makeFunction()
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void* javan_expr_tmp_0 = 0;",
            "void* javan_expr_tmp_1 = 0;",
            "void** javan_expr_roots[] = {",
            "(void**) &javan_expr_tmp_0",
            "(void**) &javan_expr_tmp_1",
            "javan_root_frame_push(javan_expr_roots, 2);",
            "javan_expr_tmp_0 = make_symbol();",
            "javan_expr_tmp_1 = make_symbol();",
            "if ((javan_expr_tmp_0 != javan_expr_tmp_1)) {",
            "javan_root_frame_pop(javan_expr_roots);",
            "goto done;"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_expr_roots, 2);"))
            .isLessThan(generated.indexOf("javan_expr_tmp_0 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = make_symbol();"))
            .isLessThan(generated.indexOf("if ((javan_expr_tmp_0 != javan_expr_tmp_1)) {"));
        assertThat(generated.indexOf("if ((javan_expr_tmp_0 != javan_expr_tmp_1)) {"))
            .isLessThan(generated.indexOf("goto done;"));
    }

    @Test
    void emitsStderrPrintVariantsThroughPublicIrGeneration() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(
                    IrInstruction.printlnErrorInt(IrExpression.intLiteral(1)),
                    IrInstruction.printlnErrorLong(IrExpression.longLiteral(2L)),
                    IrInstruction.printlnErrorFloat(IrExpression.floatLiteral(3.0f)),
                    IrInstruction.printlnErrorDouble(IrExpression.doubleLiteral(4.0d)),
                    IrInstruction.printlnErrorBoolean(IrExpression.intLiteral(1)),
                    IrInstruction.printlnErrorObject(IrExpression.stringLiteral("line")),
                    IrInstruction.printErrorObject(IrExpression.stringLiteral("part")),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_eprintln_int(1);",
            "javan_eprintln_long(2LL);",
            "javan_eprintln_float(3.0);",
            "javan_eprintln_double(4.0);",
            "javan_eprintln_bool(1);",
            "javan_eprintln((const char*) (void*) \"line\");",
            "javan_eprint((const char*) (void*) \"part\");"
        );
    }

    @Test
    void emitsPortableMinimumIntegerLiterals() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(
                    IrInstruction.printlnInt(IrExpression.intLiteral(Integer.MIN_VALUE)),
                    IrInstruction.printlnLong(IrExpression.longLiteral(Long.MIN_VALUE)),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_println_int((-2147483647 - 1));",
            "javan_println_long((-9223372036854775807LL - 1LL));"
        );
        assertThat(generated).doesNotContain("-9223372036854775808");
    }

    @Test
    void emitsPanicExpressionsForSupportedRawExpressionShapes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClassWithNext()),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(new IrLocal(IrType.OBJECT, "node")),
                List.of(
                    IrInstruction.panic(IrExpression.objectNull()),
                    IrInstruction.panic(IrExpression.stringConcat(
                        "\u0001-\u0001",
                        List.of(IrExpression.stringLiteral("left"), IrExpression.stringLiteral("right"))
                    )),
                    IrInstruction.panic(IrExpression.intComparison(">", IrExpression.intLiteral(2), IrExpression.intLiteral(1))),
                    IrInstruction.panic(IrExpression.intCall("sum_symbol", List.of(IrExpression.intLiteral(1), IrExpression.intLiteral(2)))),
                    IrInstruction.panic(IrExpression.objectComparison("!=", IrExpression.objectLocal("node"), IrExpression.objectNull())),
                    IrInstruction.panic(IrExpression.objectAllocation("com/acme/Node")),
                    IrInstruction.panic(IrExpression.objectArrayAllocation(IrExpression.intLiteral(1))),
                    IrInstruction.panic(IrExpression.objectArrayLoad(IrExpression.objectLocal("node"), IrExpression.intLiteral(0))),
                    IrInstruction.panic(IrExpression.objectField("com/acme/Node", "next", IrExpression.objectLocal("node"))),
                    IrInstruction.panic(IrExpression.objectStaticField("com/acme/Node", "next")),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_panic((const char*) ((void*) 0));",
            "javan_root_frame_push(javan_expr_roots, 1);",
            "javan_expr_tmp_0 = javan_string_concat(\"\\001-\\001\", 2, (const char*[]){(const char*) (void*) \"left\", (const char*) (void*) \"right\"});",
            "javan_panic((const char*) javan_expr_tmp_0);",
            "javan_panic((const char*) (2 > 1));",
            "int javan_expr_tmp_0 = 0;",
            "javan_expr_tmp_0 = sum_symbol(1, 2);",
            "javan_panic((const char*) javan_expr_tmp_0);",
            "javan_panic((const char*) (node != ((void*) 0)));",
            "javan_expr_tmp_0 = javan_new_com_acme_Node();",
            "javan_expr_tmp_0 = javan_object_array_new(1);",
            "javan_expr_tmp_0 = javan_object_array_get(node, 0);",
            "javan_expr_tmp_0 = ((struct javan_class_com_acme_Node*) node)->field_next;",
            "javan_expr_tmp_0 = javan_static_com_acme_Node_field_next;",
            "javan_root_frame_pop(javan_expr_roots);"
        );
    }

    @Test
    void emitsSourceMappedPanicCallWhenPanicCarriesSourceLocation() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "main_symbol",
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
                        Optional.of(9)
                    )
                ))
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_panic_at(\"JAVAN-RUNTIME-PANIC\", \"uncaught Java exception\", \"com.acme.Main\", \"main()V\", \"Main.java\", 9, 12, \"\",",
            "\"An exception reached the native boundary without a supported catch block.\"",
            "\"Catch it in Java or let the application terminate intentionally.\", (const char*) (void*) \"boom\");"
        );
    }

    @Test
    void emitsSourceContextAroundSourceMappedGeneratedStatement() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid("touch_symbol", List.of()).withSourceLocation(
                    new IrSourceLocation(
                        "com/acme/Main",
                        "main",
                        "([Ljava/lang/String;)V",
                        12,
                        Optional.of("Main.java"),
                        Optional.of(9)
                    )
                )),
                new IrFunction(
                    "com/acme/Main",
                    "touch",
                    "()V",
                    "touch_symbol",
                    IrType.VOID,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "JavanSourceContext javan_source_context_0;",
            "javan_source_enter(&javan_source_context_0, \"JAVAN-RUNTIME-PANIC\", \"runtime helper failure\", \"com.acme.Main\", \"main([Ljava/lang/String;)V\", \"Main.java\", 9, 12, \"\",",
            "\"Generated native code called a runtime helper that rejected the current value.\"",
            "\"Check the source expression and guard values before this operation.\");",
            "touch_symbol();",
            "javan_source_clear(&javan_source_context_0);"
        );
        assertThat(generated.indexOf("javan_source_enter(&javan_source_context_0"))
            .isLessThan(generated.indexOf("touch_symbol();"));
        assertThat(generated.indexOf("touch_symbol();"))
            .isLessThan(generated.indexOf("javan_source_clear(&javan_source_context_0);"));
    }

    @Test
    void emitsMinusOneSourceLineWhenSourceMappedGeneratedStatementHasNoLineNumber() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(
                mainWithInstruction(IrInstruction.callStaticVoid("touch_symbol", List.of()).withSourceLocation(
                    new IrSourceLocation(
                        "com/acme/Main",
                        "main",
                        "([Ljava/lang/String;)V",
                        12,
                        Optional.of("Main.java"),
                        Optional.empty()
                    )
                )),
                new IrFunction(
                    "com/acme/Main",
                    "touch",
                    "()V",
                    "touch_symbol",
                    IrType.VOID,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnVoid())
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_source_enter(&javan_source_context_0, \"JAVAN-RUNTIME-PANIC\", \"runtime helper failure\", \"com.acme.Main\", \"main([Ljava/lang/String;)V\", \"Main.java\", -1, 12, \"\","
        );
    }

    @Test
    void emitsSourceContextClearForBothBranchOutcomes() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "()V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(
                    IrInstruction.branchIf("done", IrExpression.intLiteral(1)).withSourceLocation(
                        new IrSourceLocation(
                            "com/acme/Main",
                            "main",
                            "()V",
                            3,
                            Optional.of("Main.java"),
                            Optional.of(12)
                        )
                    ),
                    IrInstruction.label("done"),
                    IrInstruction.returnVoid()
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "if (1) {",
            "        javan_source_clear(&javan_source_context_0);",
            "        goto done;",
            "    }",
            "    javan_source_clear(&javan_source_context_0);"
        );
        assertThat(generated.indexOf("        javan_source_clear(&javan_source_context_0);"))
            .isLessThan(generated.indexOf("        goto done;"));
        assertThat(generated.indexOf("        goto done;"))
            .isLessThan(generated.lastIndexOf("    javan_source_clear(&javan_source_context_0);"));
    }

    @Test
    void rootsPanicStringConcatObjectOperandsUntilPanicCall() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.panic(IrExpression.stringConcat(
                    "\u0001:\u0001",
                    List.of(IrExpression.objectCall("make_symbol", List.of()), IrExpression.objectCall("make_symbol", List.of()))
                ))),
                makeFunction()
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_root_frame_push(javan_expr_roots, 3);",
            "javan_expr_tmp_0 = make_symbol();",
            "javan_expr_tmp_1 = make_symbol();",
            "javan_expr_tmp_2 = javan_string_concat(\"\\001:\\001\", 2, (const char*[]){(const char*) javan_expr_tmp_0, (const char*) javan_expr_tmp_1});",
            "javan_panic((const char*) javan_expr_tmp_2);",
            "javan_root_frame_pop(javan_expr_roots);"
        );
        assertThat(generated.indexOf("javan_expr_tmp_0 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_1 = make_symbol();"));
        assertThat(generated.indexOf("javan_expr_tmp_1 = make_symbol();"))
            .isLessThan(generated.indexOf("javan_expr_tmp_2 = javan_string_concat"));
        assertThat(generated.indexOf("javan_expr_tmp_2 = javan_string_concat"))
            .isLessThan(generated.indexOf("javan_panic((const char*) javan_expr_tmp_2);"));
    }

    @Test
    void rootsObjectReturnOperandArgumentsBeforeReturnRoot() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(nodeClass()),
            List.of(
                mainWithInstruction(IrInstruction.returnVoid()),
                makeFunction(),
                new IrFunction(
                    "com/acme/Factory",
                    "choose",
                    "(Lcom/acme/Node;Lcom/acme/Node;)Lcom/acme/Node;",
                    "choose_symbol",
                    IrType.OBJECT,
                    List.of(new IrParameter(IrType.OBJECT, "left"), new IrParameter(IrType.OBJECT, "right")),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectLocal("left")))
                ),
                new IrFunction(
                    "com/acme/Factory",
                    "combined",
                    "()Lcom/acme/Node;",
                    "combined_symbol",
                    IrType.OBJECT,
                    List.of(),
                    List.of(),
                    List.of(IrInstruction.returnObject(IrExpression.objectCall(
                        "choose_symbol",
                        List.of(IrExpression.objectCall("make_symbol", List.of()), IrExpression.objectCall("make_symbol", List.of()))
                    )))
                )
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_root_frame_push(javan_expr_roots, 3);",
            "javan_expr_tmp_0 = make_symbol();",
            "javan_expr_tmp_1 = make_symbol();",
            "javan_expr_tmp_2 = choose_symbol(javan_expr_tmp_0, javan_expr_tmp_1);",
            "void* javan_return_value = javan_expr_tmp_2;",
            "javan_generated_return_root = javan_return_value;",
            "javan_root_frame_pop(javan_expr_roots);",
            "return javan_return_value;"
        );
        assertThat(generated.indexOf("javan_generated_return_root = javan_return_value;"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_expr_roots);"));
    }

    @Test
    void libraryWrapperRootsAllByteArrayInputsBeforeAnyInputCopy() throws Exception {
        final EntryPoint entry = new EntryPoint("com/acme/Bytes", "merge", "([B[B)[B");
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                entry.className(),
                entry.methodName(),
                entry.descriptor(),
                BytecodeToIR.symbol(entry),
                IrType.OBJECT,
                List.of(new IrParameter(IrType.OBJECT, "left"), new IrParameter(IrType.OBJECT, "right")),
                List.of(),
                List.of(IrInstruction.returnObject(IrExpression.objectLocal("left")))
            )),
            BytecodeToIR.symbol(entry)
        );

        final String generated = Files.readString(new CCodegen().generateLibrary(
            program,
            tempDir,
            List.of(new ExportedMethod(
                entry,
                "javan_export_com_acme_Bytes_merge_bytes_bytes",
                List.of(AbiType.BYTE_ARRAY, AbiType.BYTE_ARRAY),
                AbiType.BYTE_ARRAY
            ))
        ));

        assertThat(generated).contains(
            "jmp_buf javan_export_panic_target;",
            "javan_panic_set_target(&javan_export_panic_target);",
            "if (setjmp(javan_export_panic_target) != 0) {",
            "JavanByteArray javan_export_error_result;",
            "javan_export_error_result.data = NULL;",
            "javan_export_error_result.length = 0;",
            "return javan_export_error_result;",
            "void* arg0_array = 0;",
            "void* arg1_array = 0;",
            "void** javan_export_roots[] = {",
            "        (void**) &arg0_array,",
            "        (void**) &arg1_array",
            "javan_root_frame_push(javan_export_roots, 2);",
            "arg0_array = javan_byte_array_from(arg0.data, arg0.length);",
            "arg1_array = javan_byte_array_from(arg1.data, arg1.length);",
            BytecodeToIR.symbol(entry) + "(arg0_array, arg1_array);",
            "JavanByteArray javan_export_result = javan_byte_array_export(javan_export_object_result);",
            "javan_root_frame_pop(javan_export_roots);",
            "JavanResult javan_try_com_acme_Bytes_merge_bytes_bytes(JavanByteArray arg0, JavanByteArray arg1, JavanByteArray* out) {",
            "if (out == NULL) {",
            "return javan_result_error_message(\"JAVAN-ABI-NULL-OUT\", \"invalid native ABI call\", \"result output pointer is null\");",
            "out->data = NULL;",
            "out->length = 0;",
            "JavanByteArray javan_try_value = javan_export_com_acme_Bytes_merge_bytes_bytes(arg0, arg1);",
            "if (javan_last_error() != NULL) {",
            "return javan_result_error_from_last_error();",
            "*out = javan_try_value;",
            "return javan_result_ok();"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_export_roots, 2);"))
            .isLessThan(generated.indexOf("arg0_array = javan_byte_array_from(arg0.data, arg0.length);"));
        assertThat(generated.indexOf("arg0_array = javan_byte_array_from(arg0.data, arg0.length);"))
            .isLessThan(generated.indexOf("arg1_array = javan_byte_array_from(arg1.data, arg1.length);"));
        assertThat(generated.indexOf("arg1_array = javan_byte_array_from(arg1.data, arg1.length);"))
            .isLessThan(generated.indexOf(BytecodeToIR.symbol(entry) + "(arg0_array, arg1_array);"));
        assertThat(generated.indexOf("javan_root_frame_pop(javan_export_roots);"))
            .isLessThan(generated.indexOf("javan_panic_clear_target(&javan_export_panic_target);", generated.indexOf("javan_root_frame_pop(javan_export_roots);")));
        assertThat(generated)
            .doesNotContain("javan_free(arg0_array);")
            .doesNotContain("javan_free(arg1_array);");
    }

    private static IrClass nodeClass() {
        return new IrClass("com/acme/Node", "javan_class_com_acme_Node", List.of());
    }

    private static IrClass nodeClassWithNext() {
        return new IrClass(
            "com/acme/Node",
            "javan_class_com_acme_Node",
            List.of(new javan.ir.IrField(IrType.OBJECT, "next", "field_next"))
        );
    }

    private static IrClass holderClass() {
        return new IrClass(
            "com/acme/Holder",
            "javan_class_com_acme_Holder",
            List.of(new javan.ir.IrField(IrType.OBJECT, "child", "field_child"))
        );
    }

    private static IrFunction mainWithInstruction(final IrInstruction instruction) {
        return new IrFunction(
            "com/acme/Main",
            "main",
            "([Ljava/lang/String;)V",
            "main_symbol",
            IrType.VOID,
            List.of(),
            List.of(),
            List.of(instruction, IrInstruction.returnVoid())
        );
    }

    private static IrFunction makeFunction() {
        return new IrFunction(
            "com/acme/Factory",
            "make",
            "()Lcom/acme/Node;",
            "make_symbol",
            IrType.OBJECT,
            List.of(),
            List.of(),
            List.of(IrInstruction.returnObject(IrExpression.objectAllocation("com/acme/Node")))
        );
    }
}
