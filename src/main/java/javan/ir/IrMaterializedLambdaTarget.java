package javan.ir;

/**
 * Generated dispatch metadata for uncaptured materialized lambda objects.
 *
 * @param targetId stable runtime target id stored in the lambda object header
 * @param interfaceOwner SAM owner returned by invokedynamic
 * @param interfaceMethodName SAM method name
 * @param interfaceMethodDescriptor instantiated callable descriptor used at dispatch time
 * @param functionSymbol generated C symbol for the implementation method
 * @param booleanResult true when the callable returns a boolean
 */
public record IrMaterializedLambdaTarget(
    int targetId,
    String interfaceOwner,
    String interfaceMethodName,
    String interfaceMethodDescriptor,
    String functionSymbol,
    boolean booleanResult
) {
}
