package javan.ir;

import java.util.Optional;

/**
 * Source-facing location attached to lowered IR.
 *
 * @param className JVM internal class name
 * @param methodName Java method name
 * @param descriptor JVM method descriptor
 * @param bytecodeOffset bytecode offset for deterministic fallback diagnostics
 * @param sourceFile SourceFile attribute or inferred file name
 * @param lineNumber source line number when LineNumberTable metadata exists
 * @param sourceLine source line text when the source file is available
 */
public record IrSourceLocation(
    String className,
    String methodName,
    String descriptor,
    int bytecodeOffset,
    Optional<String> sourceFile,
    Optional<Integer> lineNumber,
    Optional<String> sourceLine
) {
    /**
     * Creates a source location without source line text.
     *
     * @param className JVM internal class name
     * @param methodName Java method name
     * @param descriptor JVM method descriptor
     * @param bytecodeOffset bytecode offset
     * @param sourceFile source file
     * @param lineNumber source line number
     */
    public IrSourceLocation(
        final String className,
        final String methodName,
        final String descriptor,
        final int bytecodeOffset,
        final Optional<String> sourceFile,
        final Optional<Integer> lineNumber
    ) {
        this(className, methodName, descriptor, bytecodeOffset, sourceFile, lineNumber, Optional.empty());
    }
}
