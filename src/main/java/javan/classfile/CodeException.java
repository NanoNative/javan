package javan.classfile;

import java.util.Optional;

/**
 * One JVM exception table entry.
 *
 * @param startPc inclusive protected bytecode offset
 * @param endPc exclusive protected bytecode offset
 * @param handlerPc handler bytecode offset
 * @param catchType caught class internal name, empty for catch-all
 */
public record CodeException(int startPc, int endPc, int handlerPc, Optional<String> catchType) {
}
