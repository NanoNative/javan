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
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                return false;
            }
        }
        for (final ClassMetadata metadata : projectClasses) {
            if (hasUnknownFatal(metadata.constructors()) || hasUnknownFatal(metadata.methods())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnknownFatal(final List<MemberMetadata> members) {
        for (final MemberMetadata member : members) {
            for (final InstructionMetadata instruction : member.instructions()) {
                if (instruction.support() == BytecodeSupport.Status.UNKNOWN_FATAL) {
                    return true;
                }
            }
        }
        return false;
    }
}
