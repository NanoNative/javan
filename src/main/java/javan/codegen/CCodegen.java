package javan.codegen;

import javan.ir.IrFunction;
import javan.build.AbiType;
import javan.build.ExportedMethod;
import javan.ir.IrClass;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Emits portable C for the initial javan IR profile.
 */
public final class CCodegen {
    /**
     * Writes the generated C program.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @return generated main C file
     * @throws IOException when writing fails
     */
    public Path generate(final IrProgram program, final Path generatedDirectory) throws IOException {
        final StringBuilder c = new StringBuilder();
        c.append("#include \"javan_runtime.h\"").append(System.lineSeparator()).append(System.lineSeparator());
        emitObjectHeader(c);
        for (final IrClass classInfo : program.classes()) {
            emitStruct(classInfo, c);
        }
        if (!program.classes().isEmpty()) {
            c.append(System.lineSeparator());
        }
        final boolean emittedStaticFields = emitStaticFields(program, c);
        if (emittedStaticFields) {
            c.append(System.lineSeparator());
        }
        for (final IrClass classInfo : program.classes()) {
            c.append("static void* ")
                .append(allocatorSymbol(classInfo.jvmName()))
                .append("(void);")
                .append(System.lineSeparator());
        }
        for (final IrFunction function : program.functions()) {
            if (!function.symbol().equals(program.entryFunction())) {
                emitSignature(function, c, true);
                c.append(";").append(System.lineSeparator());
            }
        }
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatchSignature(dispatch, c);
            c.append(";").append(System.lineSeparator());
        }
        c.append(System.lineSeparator());
        emitAllocators(program, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatch(program, dispatch, c);
        }
        for (final IrFunction function : program.functions()) {
            emitFunction(program, function, c, true);
        }
        return Files2.writeString(generatedDirectory.resolve("main.c"), c.toString());
    }

    /**
     * Writes generated C for a native library.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @param exports library exports
     * @return generated C file
     * @throws IOException when writing fails
     */
    public Path generateLibrary(
        final IrProgram program,
        final Path generatedDirectory,
        final List<ExportedMethod> exports
    ) throws IOException {
        final StringBuilder c = new StringBuilder();
        c.append("#include \"javan_runtime.h\"").append(System.lineSeparator()).append(System.lineSeparator());
        emitObjectHeader(c);
        for (final IrClass classInfo : program.classes()) {
            emitStruct(classInfo, c);
        }
        if (!program.classes().isEmpty()) {
            c.append(System.lineSeparator());
        }
        final boolean emittedStaticFields = emitStaticFields(program, c);
        if (emittedStaticFields) {
            c.append(System.lineSeparator());
        }
        for (final IrClass classInfo : program.classes()) {
            c.append("static void* ")
                .append(allocatorSymbol(classInfo.jvmName()))
                .append("(void);")
                .append(System.lineSeparator());
        }
        for (final IrFunction function : program.functions()) {
            emitSignature(function, c, true);
            c.append(";").append(System.lineSeparator());
        }
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatchSignature(dispatch, c);
            c.append(";").append(System.lineSeparator());
        }
        c.append(System.lineSeparator());
        emitAllocators(program, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatch(program, dispatch, c);
        }
        for (final IrFunction function : program.functions()) {
            emitFunction(program, function, c, false);
        }
        emitLibraryInitializer(program, c);
        for (final ExportedMethod export : exports) {
            emitExportWrapper(export, c);
        }
        return Files2.writeString(generatedDirectory.resolve("library.c"), c.toString());
    }

    private static void emitObjectHeader(final StringBuilder c) {
        c.append("struct javan_object_header {").append(System.lineSeparator());
        c.append("    int _javan_type_id;").append(System.lineSeparator());
        c.append("};").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitStruct(final IrClass classInfo, final StringBuilder c) {
        c.append("struct ").append(classInfo.symbol()).append(" {").append(System.lineSeparator());
        c.append("    int _javan_type_id;").append(System.lineSeparator());
        if (classInfo.fields().isEmpty()) {
            c.append("    char _javan_empty;").append(System.lineSeparator());
        } else {
            for (final javan.ir.IrField field : classInfo.fields()) {
                c.append("    ")
                    .append(field.type().cName())
                    .append(' ')
                    .append(field.symbol())
                    .append(";")
                    .append(System.lineSeparator());
            }
        }
        c.append("};").append(System.lineSeparator());
    }

    private static boolean emitStaticFields(final IrProgram program, final StringBuilder c) {
        boolean emitted = false;
        for (final IrClass classInfo : program.classes()) {
            for (final javan.ir.IrField field : classInfo.staticFields()) {
                c.append("static ")
                    .append(field.type().cName())
                    .append(' ')
                    .append(staticFieldSymbol(classInfo.jvmName(), field.name()))
                    .append(" = 0;")
                    .append(System.lineSeparator());
                emitted = true;
            }
        }
        return emitted;
    }

    private static void emitAllocators(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        for (final IrClass classInfo : program.classes()) {
            c.append("static void* ")
                .append(allocatorSymbol(classInfo.jvmName()))
                .append("(void) {")
                .append(System.lineSeparator());
            c.append("    struct ")
                .append(classInfo.symbol())
                .append("* object = (struct ")
                .append(classInfo.symbol())
                .append("*) javan_alloc(sizeof(struct ")
                .append(classInfo.symbol())
                .append("));")
                .append(System.lineSeparator());
            c.append("    object->_javan_type_id = ")
                .append(typeIds.get(classInfo.jvmName()))
                .append(";")
                .append(System.lineSeparator());
            c.append("    return (void*) object;").append(System.lineSeparator());
            c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        }
    }

    private static void emitDispatch(final IrProgram program, final IrDispatch dispatch, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        emitDispatchSignature(dispatch, c);
        c.append(" {").append(System.lineSeparator());
        c.append("    if (self == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"null dispatch\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    switch (((struct javan_object_header*) self)->_javan_type_id) {").append(System.lineSeparator());
        for (final IrDispatchTarget target : dispatch.targets()) {
            c.append("        case ")
                .append(typeIds.get(target.owner()))
                .append(": ");
            if (dispatch.returnType() == javan.ir.IrType.VOID) {
                c.append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append("); return;");
            } else {
                c.append("return ").append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append(");");
            }
            c.append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported dispatch target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        emitDefaultReturn(dispatch.returnType(), c);
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitFunction(
        final IrProgram program,
        final IrFunction function,
        final StringBuilder c,
        final boolean emitMain
    ) {
        final boolean entry = emitMain && function.symbol().equals(program.entryFunction());
        if (entry) {
            c.append("int main(int argc, char** argv) {").append(System.lineSeparator());
            c.append("    (void) argc;").append(System.lineSeparator());
            c.append("    (void) argv;").append(System.lineSeparator());
            emitEntryParameters(function, c);
            emitClassInitializers(program, c);
        } else {
            emitSignature(function, c, true);
            c.append(" {").append(System.lineSeparator());
        }
        for (final javan.ir.IrLocal local : function.locals()) {
            c.append("    ").append(local.type().cName()).append(' ').append(local.name()).append(" = 0;").append(System.lineSeparator());
        }
        for (final IrInstruction instruction : function.instructions()) {
            emitInstruction(instruction, entry, c);
        }
        if (entry) {
            c.append("    return 0;").append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitLibraryInitializer(final IrProgram program, final StringBuilder c) {
        c.append("static int javan_library_initialized = 0;").append(System.lineSeparator());
        c.append("static void javan_library_init(void) {").append(System.lineSeparator());
        c.append("    if (javan_library_initialized != 0) {").append(System.lineSeparator());
        c.append("        return;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    javan_library_initialized = 1;").append(System.lineSeparator());
        emitClassInitializers(program, c);
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitExportWrapper(final ExportedMethod export, final StringBuilder c) {
        emitExportSignature(export, c);
        c.append(" {").append(System.lineSeparator());
        c.append("    javan_library_init();").append(System.lineSeparator());
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            if (export.parameterTypes().get(index) == AbiType.BYTE_ARRAY) {
                c.append("    void* arg").append(index).append("_array = javan_byte_array_from(arg")
                    .append(index)
                    .append(".data, arg")
                    .append(index)
                    .append(".length);")
                    .append(System.lineSeparator());
            }
        }
        final String call = export.internalSymbol() + "(" + exportArguments(export) + ")";
        switch (export.returnType()) {
            case VOID -> c.append("    ").append(call).append(";").append(System.lineSeparator());
            case STRING -> c.append("    return javan_string_export((const char*) ").append(call).append(");").append(System.lineSeparator());
            case BYTE_ARRAY -> c.append("    return javan_byte_array_export(").append(call).append(");").append(System.lineSeparator());
            case INT, LONG, FLOAT, DOUBLE -> c.append("    return ").append(call).append(";").append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitExportSignature(final ExportedMethod export, final StringBuilder c) {
        c.append(export.returnType().cReturnName()).append(' ').append(export.symbol()).append('(');
        if (export.parameterTypes().isEmpty()) {
            c.append("void");
        } else {
            for (int index = 0; index < export.parameterTypes().size(); index++) {
                if (index > 0) {
                    c.append(", ");
                }
                final AbiType type = export.parameterTypes().get(index);
                c.append(type.cName()).append(" arg").append(index);
            }
        }
        c.append(')');
    }

    private static String exportArguments(final ExportedMethod export) {
        final List<String> arguments = new java.util.ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            final AbiType type = export.parameterTypes().get(index);
            if (type == AbiType.STRING) {
                arguments.add("(void*) arg" + index);
            } else if (type == AbiType.BYTE_ARRAY) {
                arguments.add("arg" + index + "_array");
            } else {
                arguments.add("arg" + index);
            }
        }
        return String.join(", ", arguments);
    }

    private static void emitEntryParameters(final IrFunction function, final StringBuilder c) {
        for (int index = 0; index < function.parameters().size(); index++) {
            final javan.ir.IrParameter parameter = function.parameters().get(index);
            if (index == 0 && parameter.type() == javan.ir.IrType.OBJECT) {
                c.append("    void* ")
                    .append(parameter.name())
                    .append(" = javan_string_array_from_args(argc, argv);")
                    .append(System.lineSeparator());
            }
        }
    }

    private static void emitInstruction(final IrInstruction instruction, final boolean entry, final StringBuilder c) {
        switch (instruction.op()) {
            case PRINTLN_LITERAL -> c.append("    javan_println(\"")
                .append(escapeCString(instruction.value().orElseThrow()))
                .append("\");")
                .append(System.lineSeparator());
            case PRINTLN_INT -> c.append("    javan_println_int(")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case PRINTLN_LONG -> c.append("    javan_println_long(")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case PRINTLN_FLOAT -> c.append("    javan_println_float(")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case PRINTLN_DOUBLE -> c.append("    javan_println_double(")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case PRINTLN_BOOLEAN -> c.append("    javan_println_bool(")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case PRINTLN_OBJECT -> c.append("    javan_println((const char*) ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case CALL_STATIC_VOID -> c.append("    ")
                .append(instruction.expression()
                    .map(CCodegen::emitExpression)
                    .orElseGet(() -> instruction.value().orElseThrow() + "()"))
                .append(";")
                .append(System.lineSeparator());
            case ASSIGN_INT -> c.append("    ")
                .append(instruction.value().orElseThrow())
                .append(" = ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case ASSIGN_LONG -> c.append("    ")
                .append(instruction.value().orElseThrow())
                .append(" = ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case ASSIGN_FLOAT -> c.append("    ")
                .append(instruction.value().orElseThrow())
                .append(" = ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case ASSIGN_DOUBLE -> c.append("    ")
                .append(instruction.value().orElseThrow())
                .append(" = ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case ASSIGN_OBJECT -> c.append("    ")
                .append(instruction.value().orElseThrow())
                .append(" = ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case ASSIGN_FIELD_INT -> {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    ((struct ")
                    .append(classSymbol(ownerField[0]))
                    .append("*) ")
                    .append(emitExpression(arguments.get(0)))
                    .append(")->")
                    .append(fieldSymbol(ownerField[1]))
                    .append(" = ")
                    .append(emitExpression(arguments.get(1)))
                    .append(";")
                    .append(System.lineSeparator());
            }
            case ASSIGN_FIELD_LONG -> {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    ((struct ")
                    .append(classSymbol(ownerField[0]))
                    .append("*) ")
                    .append(emitExpression(arguments.get(0)))
                    .append(")->")
                    .append(fieldSymbol(ownerField[1]))
                    .append(" = ")
                    .append(emitExpression(arguments.get(1)))
                    .append(";")
                    .append(System.lineSeparator());
            }
            case ASSIGN_FIELD_FLOAT -> {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    ((struct ")
                    .append(classSymbol(ownerField[0]))
                    .append("*) ")
                    .append(emitExpression(arguments.get(0)))
                    .append(")->")
                    .append(fieldSymbol(ownerField[1]))
                    .append(" = ")
                    .append(emitExpression(arguments.get(1)))
                    .append(";")
                    .append(System.lineSeparator());
            }
            case ASSIGN_FIELD_DOUBLE -> {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    ((struct ")
                    .append(classSymbol(ownerField[0]))
                    .append("*) ")
                    .append(emitExpression(arguments.get(0)))
                    .append(")->")
                    .append(fieldSymbol(ownerField[1]))
                    .append(" = ")
                    .append(emitExpression(arguments.get(1)))
                    .append(";")
                    .append(System.lineSeparator());
            }
            case ASSIGN_FIELD_OBJECT -> {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    ((struct ")
                    .append(classSymbol(ownerField[0]))
                    .append("*) ")
                    .append(emitExpression(arguments.get(0)))
                    .append(")->")
                    .append(fieldSymbol(ownerField[1]))
                    .append(" = ")
                    .append(emitExpression(arguments.get(1)))
                    .append(";")
                    .append(System.lineSeparator());
            }
            case ASSIGN_STATIC_FIELD_INT, ASSIGN_STATIC_FIELD_LONG, ASSIGN_STATIC_FIELD_FLOAT, ASSIGN_STATIC_FIELD_DOUBLE,
                 ASSIGN_STATIC_FIELD_OBJECT -> {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                c.append("    ")
                    .append(staticFieldSymbol(ownerField[0], ownerField[1]))
                    .append(" = ")
                    .append(emitExpression(instruction.expression().orElseThrow()))
                    .append(";")
                    .append(System.lineSeparator());
            }
            case ASSIGN_ARRAY_OBJECT -> {
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    javan_object_array_set(")
                    .append(emitExpression(arguments.get(0)))
                    .append(", ")
                    .append(emitExpression(arguments.get(1)))
                    .append(", ")
                    .append(emitExpression(arguments.get(2)))
                    .append(");")
                    .append(System.lineSeparator());
            }
            case ASSIGN_ARRAY_INT -> {
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                c.append("    javan_int_array_set(")
                    .append(emitExpression(arguments.get(0)))
                    .append(", ")
                    .append(emitExpression(arguments.get(1)))
                    .append(", ")
                    .append(emitExpression(arguments.get(2)))
                    .append(");")
                    .append(System.lineSeparator());
            }
            case ASSIGN_ARRAY_BYTE -> emitArraySet(c, "javan_byte_array_set", instruction.expression().orElseThrow().arguments());
            case ASSIGN_ARRAY_SHORT -> emitArraySet(c, "javan_short_array_set", instruction.expression().orElseThrow().arguments());
            case ASSIGN_ARRAY_CHAR -> emitArraySet(c, "javan_char_array_set", instruction.expression().orElseThrow().arguments());
            case ASSIGN_ARRAY_LONG -> emitArraySet(c, "javan_long_array_set", instruction.expression().orElseThrow().arguments());
            case ASSIGN_ARRAY_FLOAT -> emitArraySet(c, "javan_float_array_set", instruction.expression().orElseThrow().arguments());
            case ASSIGN_ARRAY_DOUBLE -> emitArraySet(c, "javan_double_array_set", instruction.expression().orElseThrow().arguments());
            case LABEL -> c.append(instruction.value().orElseThrow()).append(":").append(System.lineSeparator());
            case JUMP -> c.append("    goto ")
                .append(instruction.value().orElseThrow())
                .append(";")
                .append(System.lineSeparator());
            case BRANCH_IF -> c.append("    if (")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(") goto ")
                .append(instruction.value().orElseThrow())
                .append(";")
                .append(System.lineSeparator());
            case PANIC -> c.append("    javan_panic((const char*) ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(");")
                .append(System.lineSeparator());
            case RETURN_VOID -> {
                if (!entry) {
                    c.append("    return;").append(System.lineSeparator());
                }
            }
            case RETURN_INT -> c.append("    return ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case RETURN_LONG -> c.append("    return ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case RETURN_FLOAT -> c.append("    return ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case RETURN_DOUBLE -> c.append("    return ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
            case RETURN_OBJECT -> c.append("    return ")
                .append(emitExpression(instruction.expression().orElseThrow()))
                .append(";")
                .append(System.lineSeparator());
        }
    }

    private static void emitSignature(final IrFunction function, final StringBuilder c, final boolean isStatic) {
        if (isStatic) {
            c.append("static ");
        }
        c.append(function.returnType().cName()).append(' ').append(function.symbol()).append('(');
        if (function.parameters().isEmpty()) {
            c.append("void");
        } else {
            for (int index = 0; index < function.parameters().size(); index++) {
                if (index > 0) {
                    c.append(", ");
                }
                final javan.ir.IrParameter parameter = function.parameters().get(index);
                c.append(parameter.type().cName()).append(' ').append(parameter.name());
            }
        }
        c.append(')');
    }

    private static void emitDispatchSignature(final IrDispatch dispatch, final StringBuilder c) {
        c.append("static ").append(dispatch.returnType().cName()).append(' ').append(dispatch.symbol()).append('(');
        for (int index = 0; index < dispatch.parameters().size(); index++) {
            if (index > 0) {
                c.append(", ");
            }
            final javan.ir.IrParameter parameter = dispatch.parameters().get(index);
            c.append(parameter.type().cName()).append(' ').append(parameter.name());
        }
        c.append(')');
    }

    private static String dispatchArguments(final IrDispatch dispatch) {
        return String.join(", ", dispatch.parameters().stream().map(javan.ir.IrParameter::name).toList());
    }

    private static void emitDefaultReturn(final javan.ir.IrType type, final StringBuilder c) {
        switch (type) {
            case VOID -> c.append("    return;").append(System.lineSeparator());
            case INT -> c.append("    return 0;").append(System.lineSeparator());
            case LONG -> c.append("    return 0LL;").append(System.lineSeparator());
            case FLOAT -> c.append("    return 0.0f;").append(System.lineSeparator());
            case DOUBLE -> c.append("    return 0.0;").append(System.lineSeparator());
            case OBJECT -> c.append("    return (void*) 0;").append(System.lineSeparator());
        }
    }

    private static String emitExpression(final javan.ir.IrExpression expression) {
        return switch (expression.kind()) {
            case INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, LOCAL -> expression.value();
            case OBJECT_NULL -> "((void*) 0)";
            case STRING_LITERAL -> "(void*) \"" + escapeCString(expression.value()) + "\"";
            case STRING_CONCAT -> "javan_string_concat(\""
                + escapeCString(expression.value())
                + "\", "
                + expression.arguments().size()
                + ", (const char*[]){"
                + String.join(", ", expression.arguments().stream().map(CCodegen::emitStringArgument).toList())
                + "})";
            case INT_BINARY, LONG_BINARY, FLOAT_BINARY, DOUBLE_BINARY, INT_COMPARE, OBJECT_COMPARE -> "("
                + emitExpression(expression.arguments().get(0))
                + " "
                + expression.value()
                + " "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case CALL -> expression.value()
                + "("
                + String.join(", ", expression.arguments().stream().map(CCodegen::emitExpression).toList())
                + ")";
            case OBJECT_ALLOCATION -> allocatorSymbol(expression.value()) + "()";
            case OBJECT_ARRAY_ALLOCATION -> "javan_object_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case OBJECT_ARRAY_LOAD -> "javan_object_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case INT_ARRAY_ALLOCATION -> "javan_int_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case INT_ARRAY_LOAD -> "javan_int_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case LONG_ARRAY_ALLOCATION -> "javan_long_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case LONG_ARRAY_LOAD -> "javan_long_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case FLOAT_ARRAY_ALLOCATION -> "javan_float_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case FLOAT_ARRAY_LOAD -> "javan_float_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case DOUBLE_ARRAY_ALLOCATION -> "javan_double_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case DOUBLE_ARRAY_LOAD -> "javan_double_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case BYTE_ARRAY_ALLOCATION -> "javan_byte_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case BOOLEAN_ARRAY_ALLOCATION -> "javan_boolean_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case BYTE_ARRAY_LOAD -> "javan_byte_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case SHORT_ARRAY_ALLOCATION -> "javan_short_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case SHORT_ARRAY_LOAD -> "javan_short_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case CHAR_ARRAY_ALLOCATION -> "javan_char_array_new(" + emitExpression(expression.arguments().getFirst()) + ")";
            case CHAR_ARRAY_LOAD -> "javan_char_array_get("
                + emitExpression(expression.arguments().get(0))
                + ", "
                + emitExpression(expression.arguments().get(1))
                + ")";
            case ARRAY_LENGTH -> "javan_array_length(" + emitExpression(expression.arguments().getFirst()) + ")";
            case FIELD_INT -> {
                final String[] ownerField = ownerField(expression.value());
                yield "((struct "
                    + classSymbol(ownerField[0])
                    + "*) "
                    + emitExpression(expression.arguments().getFirst())
                    + ")->"
                    + fieldSymbol(ownerField[1]);
            }
            case FIELD_LONG -> {
                final String[] ownerField = ownerField(expression.value());
                yield "((struct "
                    + classSymbol(ownerField[0])
                    + "*) "
                    + emitExpression(expression.arguments().getFirst())
                    + ")->"
                    + fieldSymbol(ownerField[1]);
            }
            case FIELD_FLOAT -> {
                final String[] ownerField = ownerField(expression.value());
                yield "((struct "
                    + classSymbol(ownerField[0])
                    + "*) "
                    + emitExpression(expression.arguments().getFirst())
                    + ")->"
                    + fieldSymbol(ownerField[1]);
            }
            case FIELD_DOUBLE -> {
                final String[] ownerField = ownerField(expression.value());
                yield "((struct "
                    + classSymbol(ownerField[0])
                    + "*) "
                    + emitExpression(expression.arguments().getFirst())
                    + ")->"
                    + fieldSymbol(ownerField[1]);
            }
            case FIELD_OBJECT -> {
                final String[] ownerField = ownerField(expression.value());
                yield "((struct "
                    + classSymbol(ownerField[0])
                    + "*) "
                    + emitExpression(expression.arguments().getFirst())
                    + ")->"
                    + fieldSymbol(ownerField[1]);
            }
            case STATIC_FIELD_INT, STATIC_FIELD_LONG, STATIC_FIELD_FLOAT, STATIC_FIELD_DOUBLE, STATIC_FIELD_OBJECT -> {
                final String[] ownerField = ownerField(expression.value());
                yield staticFieldSymbol(ownerField[0], ownerField[1]);
            }
            case FIELD_ASSIGN_INT, FIELD_ASSIGN_LONG, FIELD_ASSIGN_FLOAT, FIELD_ASSIGN_DOUBLE, FIELD_ASSIGN_OBJECT,
                 ARRAY_ASSIGN_OBJECT, ARRAY_ASSIGN_INT, ARRAY_ASSIGN_LONG, ARRAY_ASSIGN_FLOAT, ARRAY_ASSIGN_DOUBLE,
                 ARRAY_ASSIGN_BYTE, ARRAY_ASSIGN_SHORT, ARRAY_ASSIGN_CHAR -> throw new IllegalArgumentException("assignment is not a value expression");
        };
    }

    private static String emitStringArgument(final javan.ir.IrExpression expression) {
        if (expression.type() == javan.ir.IrType.OBJECT) {
            return "(const char*) " + emitExpression(expression);
        }
        throw new IllegalArgumentException("string concat arguments must be preconverted to object strings");
    }

    private static void emitClassInitializers(final IrProgram program, final StringBuilder c) {
        program.functions().stream()
            .filter(function -> "<clinit>".equals(function.name()))
            .sorted(java.util.Comparator.comparing(IrFunction::owner))
            .forEach(function -> c.append("    ")
                .append(function.symbol())
                .append("();")
                .append(System.lineSeparator()));
    }

    private static void emitArraySet(
        final StringBuilder c,
        final String function,
        final java.util.List<javan.ir.IrExpression> arguments
    ) {
        c.append("    ")
            .append(function)
            .append("(")
            .append(emitExpression(arguments.get(0)))
            .append(", ")
            .append(emitExpression(arguments.get(1)))
            .append(", ")
            .append(emitExpression(arguments.get(2)))
            .append(");")
            .append(System.lineSeparator());
    }

    private static String[] ownerField(final String value) {
        final int separator = value.indexOf('#');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Invalid owner field value: " + value);
        }
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }

    private static String classSymbol(final String className) {
        return "javan_class_" + sanitize(className);
    }

    private static String fieldSymbol(final String fieldName) {
        return "field_" + sanitize(fieldName);
    }

    private static String staticFieldSymbol(final String owner, final String fieldName) {
        return "javan_static_" + sanitize(owner) + "_" + fieldSymbol(fieldName);
    }

    private static String allocatorSymbol(final String className) {
        return "javan_new_" + sanitize(className);
    }

    private static java.util.Map<String, Integer> typeIds(final IrProgram program) {
        final java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < program.classes().size(); index++) {
            result.put(program.classes().get(index).jvmName(), index + 1);
        }
        return java.util.Map.copyOf(result);
    }

    private static String sanitize(final String value) {
        return value
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }

    private static String escapeCString(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 32 || ch > 126) {
                        final String octal = Integer.toOctalString(ch);
                        result.append('\\');
                        for (int padding = octal.length(); padding < 3; padding++) {
                            result.append('0');
                        }
                        result.append(octal);
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.toString();
    }
}
