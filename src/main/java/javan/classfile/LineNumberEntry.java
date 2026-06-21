package javan.classfile;

/**
 * One entry from a JVM LineNumberTable attribute.
 *
 * @param startPc bytecode offset where this source line becomes active
 * @param line source line number
 */
public record LineNumberEntry(int startPc, int line) {
}
