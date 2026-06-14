package javan.detect;

import javan.classfile.ClassFile;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the Java main class.
 */
public final class MainClassDetector {
    /**
     * Detects the main class from explicit options or class files.
     *
     * @param explicitMain explicit source-style class name
     * @param classes parsed class files keyed by internal name
     * @return internal main class name
     */
    public String detect(final Optional<String> explicitMain, final Map<String, ClassFile> classes) {
        final MainClassDetection detection = find(explicitMain, classes);
        if (!detection.pass()) {
            throw new DiagnosticException(detection.diagnostics().getFirst());
        }
        return detection.mainClass();
    }

    /**
     * Finds the main class without throwing for routine diagnostic failures.
     *
     * @param explicitMain explicit source-style class name
     * @param classes parsed class files keyed by internal name
     * @return main-class detection result
     */
    public MainClassDetection find(final Optional<String> explicitMain, final Map<String, ClassFile> classes) {
        if (explicitMain.isPresent()) {
            final String internal = Strings2.internalClassName(explicitMain.orElseThrow());
            final ClassFile classFile = classes.get(internal);
            if (classFile == null || !hasPublicStaticMain(classFile)) {
                return MainClassDetection.error(Diagnostic.error(
                    "JAVAN021",
                    "explicit main class does not declare public static void main(String[])",
                    explicitMain.orElseThrow(),
                    "main([Ljava/lang/String;)V",
                    internal,
                    "The selected class is missing the Java entrypoint shape.",
                    "Pick a class with public static void main(String[] args)."
                ));
            }
            return MainClassDetection.found(internal);
        }
        final List<String> candidates = mainCandidates(classes);
        if (candidates.isEmpty()) {
            return MainClassDetection.error(Diagnostic.error(
                "JAVAN020",
                "no main class found",
                "",
                "public static void main(String[] args)",
                "scanned class folders and jar inputs",
                "javan needs a single static entry point.",
                "Run with --main com.acme.Main or add a main method."
            ));
        }
        if (candidates.size() > 1) {
            return MainClassDetection.error(Diagnostic.error(
                "JAVAN022",
                "multiple main classes found",
                "",
                "main candidates",
                mainCandidatesMessage(candidates),
                "javan refuses to guess the executable entry point.",
                "Run with --main " + Strings2.externalClassName(candidates.getFirst()) + "."
            ));
        }
        return MainClassDetection.found(candidates.getFirst());
    }

    private static List<String> mainCandidates(final Map<String, ClassFile> classes) {
        final List<String> result = new ArrayList<>();
        for (final ClassFile classFile : classes.values()) {
            if (classFile.application() && hasPublicStaticMain(classFile)) {
                insertCandidate(result, classFile.name());
            }
        }
        return List.copyOf(result);
    }

    private static boolean hasPublicStaticMain(final ClassFile classFile) {
        for (final javan.classfile.MethodInfo method : classFile.methods()) {
            if (method.isPublicStaticMain()) {
                return true;
            }
        }
        return false;
    }

    private static void insertCandidate(final List<String> values, final String value) {
        int index = 0;
        while (index < values.size() && Strings2.compareAscii(values.get(index), value) <= 0) {
            index++;
        }
        values.add(index, value);
    }

    private static String mainCandidatesMessage(final List<String> candidates) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            if (index > 0) {
                result.append(System.lineSeparator()).append("  ");
            }
            result.append(Strings2.externalClassName(candidates.get(index)));
        }
        return result.toString();
    }

    /**
     * Main-class detection result.
     *
     * @param mainClass JVM internal main class, empty when detection failed
     * @param diagnostics fatal detection diagnostics
     */
    public record MainClassDetection(String mainClass, List<Diagnostic> diagnostics) {
        private static MainClassDetection found(final String mainClass) {
            return new MainClassDetection(mainClass, List.of());
        }

        private static MainClassDetection error(final Diagnostic diagnostic) {
            return new MainClassDetection("", List.of(diagnostic));
        }

        /**
         * Reports whether detection found a usable main class.
         *
         * @return true when no diagnostics were emitted
         */
        public boolean pass() {
            return diagnostics.isEmpty();
        }
    }
}
