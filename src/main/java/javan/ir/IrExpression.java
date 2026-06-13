package javan.ir;

import java.util.List;
import java.util.Optional;

/**
 * Expression tree for primitive code generation.
 *
 * @param kind expression kind
 * @param type expression type
 * @param value literal, local name, function symbol, or operator
 * @param arguments child expressions
 */
public record IrExpression(Kind kind, IrType type, String value, List<IrExpression> arguments) {
    /**
     * Creates an int literal.
     *
     * @param value literal value
     * @return expression
     */
    public static IrExpression intLiteral(final int value) {
        return new IrExpression(Kind.INT_LITERAL, IrType.INT, Integer.toString(value), List.of());
    }

    /**
     * Creates an int local reference.
     *
     * @param name local name
     * @return expression
     */
    public static IrExpression intLocal(final String name) {
        return new IrExpression(Kind.LOCAL, IrType.INT, name, List.of());
    }

    /**
     * Creates a long literal.
     *
     * @param value literal value
     * @return expression
     */
    public static IrExpression longLiteral(final long value) {
        return new IrExpression(Kind.LONG_LITERAL, IrType.LONG, Long.toString(value), List.of());
    }

    /**
     * Creates a long local reference.
     *
     * @param name local name
     * @return expression
     */
    public static IrExpression longLocal(final String name) {
        return new IrExpression(Kind.LOCAL, IrType.LONG, name, List.of());
    }

    /**
     * Creates a float literal.
     *
     * @param value literal value
     * @return expression
     */
    public static IrExpression floatLiteral(final float value) {
        return new IrExpression(Kind.FLOAT_LITERAL, IrType.FLOAT, Float.toString(value), List.of());
    }

    /**
     * Creates a float local reference.
     *
     * @param name local name
     * @return expression
     */
    public static IrExpression floatLocal(final String name) {
        return new IrExpression(Kind.LOCAL, IrType.FLOAT, name, List.of());
    }

    /**
     * Creates a double literal.
     *
     * @param value literal value
     * @return expression
     */
    public static IrExpression doubleLiteral(final double value) {
        return new IrExpression(Kind.DOUBLE_LITERAL, IrType.DOUBLE, Double.toString(value), List.of());
    }

    /**
     * Creates a double local reference.
     *
     * @param name local name
     * @return expression
     */
    public static IrExpression doubleLocal(final String name) {
        return new IrExpression(Kind.LOCAL, IrType.DOUBLE, name, List.of());
    }

    /**
     * Creates an object local reference.
     *
     * @param name local name
     * @return expression
     */
    public static IrExpression objectLocal(final String name) {
        return new IrExpression(Kind.LOCAL, IrType.OBJECT, name, List.of());
    }

    /**
     * Creates an object null literal.
     *
     * @return expression
     */
    public static IrExpression objectNull() {
        return new IrExpression(Kind.OBJECT_NULL, IrType.OBJECT, "", List.of());
    }

    /**
     * Creates a string literal represented as an object reference.
     *
     * @param value literal value
     * @return expression
     */
    public static IrExpression stringLiteral(final String value) {
        return new IrExpression(Kind.STRING_LITERAL, IrType.OBJECT, value, List.of());
    }

    /**
     * Creates a recipe-driven string concatenation expression.
     *
     * @param recipe StringConcatFactory recipe
     * @param arguments string-compatible arguments
     * @return expression
     */
    public static IrExpression stringConcat(final String recipe, final List<IrExpression> arguments) {
        return new IrExpression(Kind.STRING_CONCAT, IrType.OBJECT, recipe, List.copyOf(arguments));
    }

    /**
     * Creates a binary int expression.
     *
     * @param operator C-style operator
     * @param left left expression
     * @param right right expression
     * @return expression
     */
    public static IrExpression intBinary(final String operator, final IrExpression left, final IrExpression right) {
        return new IrExpression(Kind.INT_BINARY, IrType.INT, operator, List.of(left, right));
    }

    /**
     * Creates a binary long expression.
     *
     * @param operator C-style operator
     * @param left left expression
     * @param right right expression
     * @return expression
     */
    public static IrExpression longBinary(final String operator, final IrExpression left, final IrExpression right) {
        return new IrExpression(Kind.LONG_BINARY, IrType.LONG, operator, List.of(left, right));
    }

    /**
     * Creates a binary float expression.
     *
     * @param operator C-style operator
     * @param left left expression
     * @param right right expression
     * @return expression
     */
    public static IrExpression floatBinary(final String operator, final IrExpression left, final IrExpression right) {
        return new IrExpression(Kind.FLOAT_BINARY, IrType.FLOAT, operator, List.of(left, right));
    }

    /**
     * Creates a binary double expression.
     *
     * @param operator C-style operator
     * @param left left expression
     * @param right right expression
     * @return expression
     */
    public static IrExpression doubleBinary(final String operator, final IrExpression left, final IrExpression right) {
        return new IrExpression(Kind.DOUBLE_BINARY, IrType.DOUBLE, operator, List.of(left, right));
    }

    /**
     * Creates an int comparison expression.
     *
     * @param operator C-style comparison operator
     * @param left left expression
     * @param right right expression
     * @return expression
     */
    public static IrExpression intComparison(final String operator, final IrExpression left, final IrExpression right) {
        return new IrExpression(Kind.INT_COMPARE, IrType.INT, operator, List.of(left, right));
    }

    /**
     * Creates an object comparison expression.
     *
     * @param operator C-style comparison operator
     * @param left left expression
     * @param right right expression
     * @return expression
     */
    public static IrExpression objectComparison(final String operator, final IrExpression left, final IrExpression right) {
        return new IrExpression(Kind.OBJECT_COMPARE, IrType.INT, operator, List.of(left, right));
    }

    /**
     * Creates an int-returning function call.
     *
     * @param symbol function symbol
     * @param arguments call arguments
     * @return expression
     */
    public static IrExpression intCall(final String symbol, final List<IrExpression> arguments) {
        return new IrExpression(Kind.CALL, IrType.INT, symbol, List.copyOf(arguments));
    }

    /**
     * Creates a long-returning function call.
     *
     * @param symbol function symbol
     * @param arguments call arguments
     * @return expression
     */
    public static IrExpression longCall(final String symbol, final List<IrExpression> arguments) {
        return new IrExpression(Kind.CALL, IrType.LONG, symbol, List.copyOf(arguments));
    }

    /**
     * Creates a float-returning function call.
     *
     * @param symbol function symbol
     * @param arguments call arguments
     * @return expression
     */
    public static IrExpression floatCall(final String symbol, final List<IrExpression> arguments) {
        return new IrExpression(Kind.CALL, IrType.FLOAT, symbol, List.copyOf(arguments));
    }

    /**
     * Creates a double-returning function call.
     *
     * @param symbol function symbol
     * @param arguments call arguments
     * @return expression
     */
    public static IrExpression doubleCall(final String symbol, final List<IrExpression> arguments) {
        return new IrExpression(Kind.CALL, IrType.DOUBLE, symbol, List.copyOf(arguments));
    }

    /**
     * Creates an object-returning function call.
     *
     * @param symbol function symbol
     * @param arguments call arguments
     * @return expression
     */
    public static IrExpression objectCall(final String symbol, final List<IrExpression> arguments) {
        return new IrExpression(Kind.CALL, IrType.OBJECT, symbol, List.copyOf(arguments));
    }

    /**
     * Creates an object allocation expression.
     *
     * @param owner JVM internal class name
     * @return expression
     */
    public static IrExpression objectAllocation(final String owner) {
        return new IrExpression(Kind.OBJECT_ALLOCATION, IrType.OBJECT, owner, List.of());
    }

    /**
     * Creates an object-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression objectArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.OBJECT_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates an object-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression objectArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.OBJECT_ARRAY_LOAD, IrType.OBJECT, "", List.of(array, index));
    }

    /**
     * Creates an int-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression intArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.INT_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates an int-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression intArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.INT_ARRAY_LOAD, IrType.INT, "", List.of(array, index));
    }

    /**
     * Creates a long-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression longArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.LONG_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a long-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression longArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.LONG_ARRAY_LOAD, IrType.LONG, "", List.of(array, index));
    }

    /**
     * Creates a float-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression floatArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.FLOAT_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a float-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression floatArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.FLOAT_ARRAY_LOAD, IrType.FLOAT, "", List.of(array, index));
    }

    /**
     * Creates a double-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression doubleArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.DOUBLE_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a double-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression doubleArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.DOUBLE_ARRAY_LOAD, IrType.DOUBLE, "", List.of(array, index));
    }

    /**
     * Creates a byte-backed array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression byteArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.BYTE_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a boolean array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression booleanArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.BOOLEAN_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a byte-backed array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression byteArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.BYTE_ARRAY_LOAD, IrType.INT, "", List.of(array, index));
    }

    /**
     * Creates a short-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression shortArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.SHORT_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a short-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression shortArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.SHORT_ARRAY_LOAD, IrType.INT, "", List.of(array, index));
    }

    /**
     * Creates a char-array allocation expression.
     *
     * @param length array length expression
     * @return expression
     */
    public static IrExpression charArrayAllocation(final IrExpression length) {
        return new IrExpression(Kind.CHAR_ARRAY_ALLOCATION, IrType.OBJECT, "", List.of(length));
    }

    /**
     * Creates a char-array load expression.
     *
     * @param array array expression
     * @param index index expression
     * @return expression
     */
    public static IrExpression charArrayLoad(final IrExpression array, final IrExpression index) {
        return new IrExpression(Kind.CHAR_ARRAY_LOAD, IrType.INT, "", List.of(array, index));
    }

    /**
     * Creates an array-length expression.
     *
     * @param array array expression
     * @return expression
     */
    public static IrExpression arrayLength(final IrExpression array) {
        return new IrExpression(Kind.ARRAY_LENGTH, IrType.INT, "", List.of(array));
    }

    /**
     * Creates an int field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver expression
     * @return expression
     */
    public static IrExpression intField(final String owner, final String field, final IrExpression receiver) {
        return new IrExpression(Kind.FIELD_INT, IrType.INT, owner + "#" + field, List.of(receiver));
    }

    /**
     * Creates a long field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver expression
     * @return expression
     */
    public static IrExpression longField(final String owner, final String field, final IrExpression receiver) {
        return new IrExpression(Kind.FIELD_LONG, IrType.LONG, owner + "#" + field, List.of(receiver));
    }

    /**
     * Creates a float field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver expression
     * @return expression
     */
    public static IrExpression floatField(final String owner, final String field, final IrExpression receiver) {
        return new IrExpression(Kind.FIELD_FLOAT, IrType.FLOAT, owner + "#" + field, List.of(receiver));
    }

    /**
     * Creates a double field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver expression
     * @return expression
     */
    public static IrExpression doubleField(final String owner, final String field, final IrExpression receiver) {
        return new IrExpression(Kind.FIELD_DOUBLE, IrType.DOUBLE, owner + "#" + field, List.of(receiver));
    }

    /**
     * Creates an object field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @param receiver receiver expression
     * @return expression
     */
    public static IrExpression objectField(final String owner, final String field, final IrExpression receiver) {
        return new IrExpression(Kind.FIELD_OBJECT, IrType.OBJECT, owner + "#" + field, List.of(receiver));
    }

    /**
     * Creates an int static field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @return expression
     */
    public static IrExpression intStaticField(final String owner, final String field) {
        return new IrExpression(Kind.STATIC_FIELD_INT, IrType.INT, owner + "#" + field, List.of());
    }

    /**
     * Creates a long static field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @return expression
     */
    public static IrExpression longStaticField(final String owner, final String field) {
        return new IrExpression(Kind.STATIC_FIELD_LONG, IrType.LONG, owner + "#" + field, List.of());
    }

    /**
     * Creates a float static field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @return expression
     */
    public static IrExpression floatStaticField(final String owner, final String field) {
        return new IrExpression(Kind.STATIC_FIELD_FLOAT, IrType.FLOAT, owner + "#" + field, List.of());
    }

    /**
     * Creates a double static field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @return expression
     */
    public static IrExpression doubleStaticField(final String owner, final String field) {
        return new IrExpression(Kind.STATIC_FIELD_DOUBLE, IrType.DOUBLE, owner + "#" + field, List.of());
    }

    /**
     * Creates an object static field read expression.
     *
     * @param owner JVM internal owner class
     * @param field field name
     * @return expression
     */
    public static IrExpression objectStaticField(final String owner, final String field) {
        return new IrExpression(Kind.STATIC_FIELD_OBJECT, IrType.OBJECT, owner + "#" + field, List.of());
    }

    /**
     * Creates an int field write expression.
     *
     * @param receiver receiver expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression intFieldAssignment(final IrExpression receiver, final IrExpression value) {
        return new IrExpression(Kind.FIELD_ASSIGN_INT, IrType.VOID, "", List.of(receiver, value));
    }

    /**
     * Creates a long field write expression.
     *
     * @param receiver receiver expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression longFieldAssignment(final IrExpression receiver, final IrExpression value) {
        return new IrExpression(Kind.FIELD_ASSIGN_LONG, IrType.VOID, "", List.of(receiver, value));
    }

    /**
     * Creates a float field write expression.
     *
     * @param receiver receiver expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression floatFieldAssignment(final IrExpression receiver, final IrExpression value) {
        return new IrExpression(Kind.FIELD_ASSIGN_FLOAT, IrType.VOID, "", List.of(receiver, value));
    }

    /**
     * Creates a double field write expression.
     *
     * @param receiver receiver expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression doubleFieldAssignment(final IrExpression receiver, final IrExpression value) {
        return new IrExpression(Kind.FIELD_ASSIGN_DOUBLE, IrType.VOID, "", List.of(receiver, value));
    }

    /**
     * Creates an object field write expression.
     *
     * @param receiver receiver expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression objectFieldAssignment(final IrExpression receiver, final IrExpression value) {
        return new IrExpression(Kind.FIELD_ASSIGN_OBJECT, IrType.VOID, "", List.of(receiver, value));
    }

    /**
     * Creates an object-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression objectArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_OBJECT, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates an int-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression intArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_INT, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates a long-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression longArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_LONG, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates a float-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression floatArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_FLOAT, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates a double-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression doubleArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_DOUBLE, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates a byte-backed array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression byteArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_BYTE, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates a short-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression shortArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_SHORT, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Creates a char-array store expression.
     *
     * @param array array expression
     * @param index index expression
     * @param value value expression
     * @return expression
     */
    public static IrExpression charArrayAssignment(final IrExpression array, final IrExpression index, final IrExpression value) {
        return new IrExpression(Kind.ARRAY_ASSIGN_CHAR, IrType.VOID, "", List.of(array, index, value));
    }

    /**
     * Optional expression value.
     *
     * @return expression value
     */
    public Optional<String> valueOpt() {
        return Optional.ofNullable(value);
    }

    /**
     * Expression kind.
     */
    public enum Kind {
        INT_LITERAL,
        LONG_LITERAL,
        FLOAT_LITERAL,
        DOUBLE_LITERAL,
        OBJECT_NULL,
        STRING_LITERAL,
        STRING_CONCAT,
        LOCAL,
        INT_BINARY,
        LONG_BINARY,
        FLOAT_BINARY,
        DOUBLE_BINARY,
        INT_COMPARE,
        OBJECT_COMPARE,
        CALL,
        OBJECT_ALLOCATION,
        OBJECT_ARRAY_ALLOCATION,
        OBJECT_ARRAY_LOAD,
        INT_ARRAY_ALLOCATION,
        INT_ARRAY_LOAD,
        LONG_ARRAY_ALLOCATION,
        LONG_ARRAY_LOAD,
        FLOAT_ARRAY_ALLOCATION,
        FLOAT_ARRAY_LOAD,
        DOUBLE_ARRAY_ALLOCATION,
        DOUBLE_ARRAY_LOAD,
        BYTE_ARRAY_ALLOCATION,
        BOOLEAN_ARRAY_ALLOCATION,
        BYTE_ARRAY_LOAD,
        SHORT_ARRAY_ALLOCATION,
        SHORT_ARRAY_LOAD,
        CHAR_ARRAY_ALLOCATION,
        CHAR_ARRAY_LOAD,
        ARRAY_LENGTH,
        FIELD_INT,
        FIELD_LONG,
        FIELD_FLOAT,
        FIELD_DOUBLE,
        FIELD_OBJECT,
        STATIC_FIELD_INT,
        STATIC_FIELD_LONG,
        STATIC_FIELD_FLOAT,
        STATIC_FIELD_DOUBLE,
        STATIC_FIELD_OBJECT,
        FIELD_ASSIGN_INT,
        FIELD_ASSIGN_LONG,
        FIELD_ASSIGN_FLOAT,
        FIELD_ASSIGN_DOUBLE,
        FIELD_ASSIGN_OBJECT,
        ARRAY_ASSIGN_OBJECT,
        ARRAY_ASSIGN_INT,
        ARRAY_ASSIGN_LONG,
        ARRAY_ASSIGN_FLOAT,
        ARRAY_ASSIGN_DOUBLE,
        ARRAY_ASSIGN_BYTE,
        ARRAY_ASSIGN_SHORT,
        ARRAY_ASSIGN_CHAR
    }
}
