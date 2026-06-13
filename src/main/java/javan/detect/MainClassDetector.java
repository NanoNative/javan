package javan.detect;

import javan.classfile.ClassFile;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.Comparator;
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
        if (explicitMain.isPresent()) {
            final String internal = Strings2.internalClassName(explicitMain.orElseThrow());
            final ClassFile classFile = classes.get(internal);
            if (classFile == null || classFile.methods().stream().noneMatch(method -> method.isPublicStaticMain())) {
                throw new DiagnosticException(Diagnostic.error(
                    "JAVAN021",
                    "explicit main class does not declare public static void main(String[])",
                    explicitMain.orElseThrow(),
                    "main([Ljava/lang/String;)V",
                    internal,
                    "The selected class is missing the Java entrypoint shape.",
                    "Pick a class with public static void main(String[] args)."
                ));
            }
            return internal;
        }
        final List<String> candidates = classes.values().stream()
            .filter(ClassFile::application)
            .filter(classFile -> classFile.methods().stream().anyMatch(method -> method.isPublicStaticMain()))
            .map(ClassFile::name)
            .sorted(Comparator.naturalOrder())
            .toList();
        if (candidates.isEmpty()) {
            throw new DiagnosticException(Diagnostic.error(
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
            throw new DiagnosticException(Diagnostic.error(
                "JAVAN022",
                "multiple main classes found",
                "",
                "main candidates",
                String.join(System.lineSeparator() + "  ", candidates.stream().map(Strings2::externalClassName).toList()),
                "javan refuses to guess the executable entry point.",
                "Run with --main " + Strings2.externalClassName(candidates.getFirst()) + "."
            ));
        }
        return candidates.getFirst();
    }
}
