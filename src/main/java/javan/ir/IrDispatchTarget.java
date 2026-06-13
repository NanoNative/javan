package javan.ir;

/**
 * One concrete target in a closed-world dispatch stub.
 *
 * @param owner JVM internal concrete receiver class
 * @param functionSymbol target C function symbol
 */
public record IrDispatchTarget(String owner, String functionSymbol) {
}
