package javan.compat;

import javan.verify.Diagnostic;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of compatibility report generation.
 *
 * @param outputDirectory .javan output directory
 * @param javaVersion runtime Java version
 * @param javaFeatureVersion runtime Java feature version
 * @param projectClasses project and dependency class metadata
 * @param jdkClasses runtime JDK class metadata
 * @param diagnostics static profile diagnostics
 * @param reportFiles generated report files
 */
public record CompatibilityResult(
    Path outputDirectory,
    String javaVersion,
    int javaFeatureVersion,
    List<ClassMetadata> projectClasses,
    List<ClassMetadata> jdkClasses,
    List<Diagnostic> diagnostics,
    List<Path> reportFiles
) {
    /**
     * Returns true when no compiler-fatal compatibility issue was found.
     *
     * @return true when compatible
     */
    public boolean pass() {
        return diagnostics.stream().noneMatch(Diagnostic::error)
            && projectClasses.stream().flatMap(CompatibilityResult::instructions)
                .noneMatch(instruction -> instruction.support() == BytecodeSupport.Status.UNKNOWN_FATAL);
    }

    private static java.util.stream.Stream<InstructionMetadata> instructions(final ClassMetadata metadata) {
        return java.util.stream.Stream.concat(
            metadata.constructors().stream(),
            metadata.methods().stream()
        ).flatMap(member -> member.instructions().stream());
    }
}
