package javan.codegen;

import javan.ir.IrClass;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrProgram;
import javan.ir.IrType;
import javan.ir.IrExpression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
