package javan.ir;

import java.util.Optional;

/**
 * Small instruction set for the initial native profile.
 *
 * @param op operation kind
 * @param value literal value or symbol
 * @param expression optional expression
 */
public record IrInstruction(Op op, Optional<String> value, Optional<IrExpression> expression) {
    /**
     * Creates a println-literal instruction.
     *
     * @param value string literal
     * @return IR instruction
     */
    public static IrInstruction printlnLiteral(final String value) {
        return new IrInstruction(Op.PRINTLN_LITERAL, Optional.of(value), Optional.empty());
    }

    /**
     * Creates a println-int instruction.
     *
     * @param expression int expression
     * @return IR instruction
     */
    public static IrInstruction printlnInt(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_INT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr println-int instruction.
     *
     * @param expression int expression
     * @return IR instruction
     */
    public static IrInstruction printlnErrorInt(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_ERROR_INT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a println-long instruction.
     *
     * @param expression long expression
     * @return IR instruction
     */
    public static IrInstruction printlnLong(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_LONG, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr println-long instruction.
     *
     * @param expression long expression
     * @return IR instruction
     */
    public static IrInstruction printlnErrorLong(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_ERROR_LONG, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a println-float instruction.
     *
     * @param expression float expression
     * @return IR instruction
     */
    public static IrInstruction printlnFloat(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_FLOAT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr println-float instruction.
     *
     * @param expression float expression
     * @return IR instruction
     */
    public static IrInstruction printlnErrorFloat(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_ERROR_FLOAT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a println-double instruction.
     *
     * @param expression double expression
     * @return IR instruction
     */
    public static IrInstruction printlnDouble(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_DOUBLE, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr println-double instruction.
     *
     * @param expression double expression
     * @return IR instruction
     */
    public static IrInstruction printlnErrorDouble(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_ERROR_DOUBLE, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a println-boolean instruction.
     *
     * @param expression boolean expression represented as an int
     * @return IR instruction
     */
    public static IrInstruction printlnBoolean(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_BOOLEAN, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr println-boolean instruction.
     *
     * @param expression boolean expression represented as an int
     * @return IR instruction
     */
    public static IrInstruction printlnErrorBoolean(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_ERROR_BOOLEAN, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a println-object instruction for string-compatible object values.
     *
     * @param expression object expression
     * @return IR instruction
     */
    public static IrInstruction printlnObject(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_OBJECT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr println-object instruction for string-compatible object values.
     *
     * @param expression object expression
     * @return IR instruction
     */
    public static IrInstruction printlnErrorObject(final IrExpression expression) {
        return new IrInstruction(Op.PRINTLN_ERROR_OBJECT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a print-object instruction for string-compatible object values.
     *
     * @param expression object expression
     * @return IR instruction
     */
    public static IrInstruction printObject(final IrExpression expression) {
        return new IrInstruction(Op.PRINT_OBJECT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a stderr print-object instruction for string-compatible object values.
     *
     * @param expression object expression
     * @return IR instruction
     */
    public static IrInstruction printErrorObject(final IrExpression expression) {
        return new IrInstruction(Op.PRINT_ERROR_OBJECT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a static function call instruction.
     *
     * @param symbol C function symbol
     * @return IR instruction
     */
    public static IrInstruction callStaticVoid(final String symbol) {
        return new IrInstruction(Op.CALL_STATIC_VOID, Optional.of(symbol), Optional.empty());
    }

    /**
     * Creates a static void function call instruction.
     *
     * @param symbol C function symbol
     * @param arguments call arguments
     * @return IR instruction
     */
    public static IrInstruction callStaticVoid(final String symbol, final java.util.List<IrExpression> arguments) {
        return new IrInstruction(Op.CALL_STATIC_VOID, Optional.of(symbol), Optional.of(new IrExpression(
            IrExpression.Kind.CALL,
            IrType.VOID,
            symbol,
            java.util.List.copyOf(arguments)
        )));
    }

    /**
     * Creates an int assignment instruction.
     *
     * @param localName target local name
     * @param expression assigned expression
     * @return IR instruction
     */
    public static IrInstruction assignInt(final String localName, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_INT, Optional.of(localName), Optional.of(expression));
    }

    /**
     * Creates a long assignment instruction.
     *
     * @param localName target local name
     * @param expression assigned expression
     * @return IR instruction
     */
    public static IrInstruction assignLong(final String localName, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_LONG, Optional.of(localName), Optional.of(expression));
    }

    /**
     * Creates a float assignment instruction.
     *
     * @param localName target local name
     * @param expression assigned expression
     * @return IR instruction
     */
    public static IrInstruction assignFloat(final String localName, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_FLOAT, Optional.of(localName), Optional.of(expression));
    }

    /**
     * Creates a double assignment instruction.
     *
     * @param localName target local name
     * @param expression assigned expression
     * @return IR instruction
     */
    public static IrInstruction assignDouble(final String localName, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_DOUBLE, Optional.of(localName), Optional.of(expression));
    }

    /**
     * Creates an object assignment instruction.
     *
     * @param localName target local name
     * @param expression assigned expression
     * @return IR instruction
     */
    public static IrInstruction assignObject(final String localName, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_OBJECT, Optional.of(localName), Optional.of(expression));
    }

    /**
     * Creates an int field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver object
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignFieldInt(
        final String owner,
        final String field,
        final IrExpression receiver,
        final IrExpression expression
    ) {
        return new IrInstruction(
            Op.ASSIGN_FIELD_INT,
            Optional.of(owner + "#" + field),
            Optional.of(IrExpression.intFieldAssignment(receiver, expression))
        );
    }

    /**
     * Creates a long field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver object
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignFieldLong(
        final String owner,
        final String field,
        final IrExpression receiver,
        final IrExpression expression
    ) {
        return new IrInstruction(
            Op.ASSIGN_FIELD_LONG,
            Optional.of(owner + "#" + field),
            Optional.of(IrExpression.longFieldAssignment(receiver, expression))
        );
    }

    /**
     * Creates a float field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver object
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignFieldFloat(
        final String owner,
        final String field,
        final IrExpression receiver,
        final IrExpression expression
    ) {
        return new IrInstruction(
            Op.ASSIGN_FIELD_FLOAT,
            Optional.of(owner + "#" + field),
            Optional.of(IrExpression.floatFieldAssignment(receiver, expression))
        );
    }

    /**
     * Creates a double field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver object
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignFieldDouble(
        final String owner,
        final String field,
        final IrExpression receiver,
        final IrExpression expression
    ) {
        return new IrInstruction(
            Op.ASSIGN_FIELD_DOUBLE,
            Optional.of(owner + "#" + field),
            Optional.of(IrExpression.doubleFieldAssignment(receiver, expression))
        );
    }

    /**
     * Creates an object field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver object
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignFieldObject(
        final String owner,
        final String field,
        final IrExpression receiver,
        final IrExpression expression
    ) {
        return new IrInstruction(
            Op.ASSIGN_FIELD_OBJECT,
            Optional.of(owner + "#" + field),
            Optional.of(IrExpression.objectFieldAssignment(receiver, expression))
        );
    }

    /**
     * Creates an int static field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignStaticFieldInt(final String owner, final String field, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_STATIC_FIELD_INT, Optional.of(owner + "#" + field), Optional.of(expression));
    }

    /**
     * Creates a long static field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignStaticFieldLong(final String owner, final String field, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_STATIC_FIELD_LONG, Optional.of(owner + "#" + field), Optional.of(expression));
    }

    /**
     * Creates a float static field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignStaticFieldFloat(final String owner, final String field, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_STATIC_FIELD_FLOAT, Optional.of(owner + "#" + field), Optional.of(expression));
    }

    /**
     * Creates a double static field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignStaticFieldDouble(final String owner, final String field, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_STATIC_FIELD_DOUBLE, Optional.of(owner + "#" + field), Optional.of(expression));
    }

    /**
     * Creates an object static field assignment instruction.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignStaticFieldObject(final String owner, final String field, final IrExpression expression) {
        return new IrInstruction(Op.ASSIGN_STATIC_FIELD_OBJECT, Optional.of(owner + "#" + field), Optional.of(expression));
    }

    /**
     * Creates an object-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayObject(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_OBJECT,
            Optional.empty(),
            Optional.of(IrExpression.objectArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates an int-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayInt(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_INT,
            Optional.empty(),
            Optional.of(IrExpression.intArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a long-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayLong(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_LONG,
            Optional.empty(),
            Optional.of(IrExpression.longArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a float-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayFloat(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_FLOAT,
            Optional.empty(),
            Optional.of(IrExpression.floatArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a double-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayDouble(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_DOUBLE,
            Optional.empty(),
            Optional.of(IrExpression.doubleArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a byte-backed array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayByte(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_BYTE,
            Optional.empty(),
            Optional.of(IrExpression.byteArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a short-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayShort(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_SHORT,
            Optional.empty(),
            Optional.of(IrExpression.shortArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a char-array assignment instruction.
     *
     * @param array array expression
     * @param index index expression
     * @param expression assigned value
     * @return IR instruction
     */
    public static IrInstruction assignArrayChar(final IrExpression array, final IrExpression index, final IrExpression expression) {
        return new IrInstruction(
            Op.ASSIGN_ARRAY_CHAR,
            Optional.empty(),
            Optional.of(IrExpression.charArrayAssignment(array, index, expression))
        );
    }

    /**
     * Creates a label instruction.
     *
     * @param label target label
     * @return IR instruction
     */
    public static IrInstruction label(final String label) {
        return new IrInstruction(Op.LABEL, Optional.of(label), Optional.empty());
    }

    /**
     * Creates an unconditional jump.
     *
     * @param label target label
     * @return IR instruction
     */
    public static IrInstruction jump(final String label) {
        return new IrInstruction(Op.JUMP, Optional.of(label), Optional.empty());
    }

    /**
     * Creates a conditional jump.
     *
     * @param label target label
     * @param condition condition expression
     * @return IR instruction
     */
    public static IrInstruction branchIf(final String label, final IrExpression condition) {
        return new IrInstruction(Op.BRANCH_IF, Optional.of(label), Optional.of(condition));
    }

    /**
     * Creates a native panic instruction.
     *
     * @param expression message expression
     * @return IR instruction
     */
    public static IrInstruction panic(final IrExpression expression) {
        return new IrInstruction(Op.PANIC, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a return-void instruction.
     *
     * @return IR instruction
     */
    public static IrInstruction returnVoid() {
        return new IrInstruction(Op.RETURN_VOID, Optional.empty(), Optional.empty());
    }

    /**
     * Creates a return-int instruction.
     *
     * @param expression returned expression
     * @return IR instruction
     */
    public static IrInstruction returnInt(final IrExpression expression) {
        return new IrInstruction(Op.RETURN_INT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a return-long instruction.
     *
     * @param expression returned expression
     * @return IR instruction
     */
    public static IrInstruction returnLong(final IrExpression expression) {
        return new IrInstruction(Op.RETURN_LONG, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a return-float instruction.
     *
     * @param expression returned expression
     * @return IR instruction
     */
    public static IrInstruction returnFloat(final IrExpression expression) {
        return new IrInstruction(Op.RETURN_FLOAT, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a return-double instruction.
     *
     * @param expression returned expression
     * @return IR instruction
     */
    public static IrInstruction returnDouble(final IrExpression expression) {
        return new IrInstruction(Op.RETURN_DOUBLE, Optional.empty(), Optional.of(expression));
    }

    /**
     * Creates a return-object instruction.
     *
     * @param expression returned expression
     * @return IR instruction
     */
    public static IrInstruction returnObject(final IrExpression expression) {
        return new IrInstruction(Op.RETURN_OBJECT, Optional.empty(), Optional.of(expression));
    }

    /**
     * IR operation kind.
     */
    public enum Op {
        PRINTLN_LITERAL,
        PRINTLN_INT,
        PRINTLN_ERROR_INT,
        PRINTLN_LONG,
        PRINTLN_ERROR_LONG,
        PRINTLN_FLOAT,
        PRINTLN_ERROR_FLOAT,
        PRINTLN_DOUBLE,
        PRINTLN_ERROR_DOUBLE,
        PRINTLN_BOOLEAN,
        PRINTLN_ERROR_BOOLEAN,
        PRINTLN_OBJECT,
        PRINTLN_ERROR_OBJECT,
        PRINT_OBJECT,
        PRINT_ERROR_OBJECT,
        CALL_STATIC_VOID,
        ASSIGN_INT,
        ASSIGN_LONG,
        ASSIGN_FLOAT,
        ASSIGN_DOUBLE,
        ASSIGN_OBJECT,
        ASSIGN_FIELD_INT,
        ASSIGN_FIELD_LONG,
        ASSIGN_FIELD_FLOAT,
        ASSIGN_FIELD_DOUBLE,
        ASSIGN_FIELD_OBJECT,
        ASSIGN_STATIC_FIELD_INT,
        ASSIGN_STATIC_FIELD_LONG,
        ASSIGN_STATIC_FIELD_FLOAT,
        ASSIGN_STATIC_FIELD_DOUBLE,
        ASSIGN_STATIC_FIELD_OBJECT,
        ASSIGN_ARRAY_OBJECT,
        ASSIGN_ARRAY_INT,
        ASSIGN_ARRAY_LONG,
        ASSIGN_ARRAY_FLOAT,
        ASSIGN_ARRAY_DOUBLE,
        ASSIGN_ARRAY_BYTE,
        ASSIGN_ARRAY_SHORT,
        ASSIGN_ARRAY_CHAR,
        LABEL,
        JUMP,
        BRANCH_IF,
        PANIC,
        RETURN_VOID,
        RETURN_INT,
        RETURN_LONG,
        RETURN_FLOAT,
        RETURN_DOUBLE,
        RETURN_OBJECT
    }
}
