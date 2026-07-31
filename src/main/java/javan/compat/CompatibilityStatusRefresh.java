package javan.compat;

import javan.cli.Cli;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Refreshes repository compatibility status from the canonical {@code javan compat} output.
 */
public final class CompatibilityStatusRefresh {
    private static final List<Path> MATRIX_FILES = CompatibilityReports.MATRIX_STATUS_FILES;
    private static final Path JDK_STATUS_FILE = CompatibilityReports.JDK_STATUS_FILE;
    private static final List<Path> GENERATED_STATUS_FILES = CompatibilityReports.STATUS_FILES;
    private static final String CANONICAL_OS = "Linux";
    private static final String CANONICAL_ARCH = "amd64";

    private CompatibilityStatusRefresh() {
    }

    /**
     * Runs compatibility generation against already compiled Javan classes and synchronizes
     * the tracked status documents.
     *
     * @param args repository root, compiled classes directory, and required JDK feature
     * @throws IOException when generation or synchronization fails
     * @throws InterruptedException when compatibility generation is interrupted
     */
    public static void main(final String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Expected repository root, compiled classes directory, and required JDK feature; received "
                    + args.length + " arguments."
            );
        }
        final String actualJavaVersion = System.getProperty("java.version", "");
        final String actualOsName = System.getProperty("os.name", "");
        final String actualOsArch = System.getProperty("os.arch", "");
        final int requiredFeature = CompatibilityReports.javaFeature(args[2]);
        final int actualFeature = CompatibilityReports.javaFeature(actualJavaVersion);
        if (requiredFeature < 1) {
            throw new IllegalArgumentException("Invalid required JDK feature: " + args[2]);
        }
        if (actualFeature != requiredFeature) {
            throw new IllegalStateException(
                "Compatibility status requires JDK " + requiredFeature
                    + ", but Maven is running JDK " + actualFeature + " (" + actualJavaVersion + "). "
                    + "Run mvn verify with JDK " + requiredFeature + " so matrix keys stay canonical."
            );
        }
        final boolean canonicalPlatform = isCanonicalPlatform(actualOsName, actualOsArch);
        final Path root = Path.of(args[0]).toAbsolutePath().normalize();
        final Path configuredClasses = Path.of(args[1]);
        final Path classes = (configuredClasses.isAbsolute() ? configuredClasses : root.resolve(configuredClasses))
            .toAbsolutePath()
            .normalize();
        final Path entrypoint = classes.resolve("javan/Main.class");
        if (!Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IOException("Missing Maven project descriptor: " + displayPath(root, root.resolve("pom.xml")));
        }
        if (!Files.isRegularFile(entrypoint)) {
            throw new IOException("Missing compiled Javan entrypoint: " + displayPath(root, entrypoint));
        }

        final int generationExitCode = new Cli().runProcess(
            root,
            System.out,
            System.err,
            "compat",
            classes.toString(),
            "--main",
            "javan.Main"
        );
        if (generationExitCode != 0) {
            throw new IOException("Compatibility report generation failed with exit code " + generationExitCode + ".");
        }

        failWhenStatusWasStale(synchronize(root, classes, System.out, canonicalPlatform));
    }

    static boolean isCanonicalPlatform(final String osName, final String osArch) {
        return CANONICAL_OS.equals(osName) && CANONICAL_ARCH.equals(osArch);
    }

    static RefreshResult synchronize(
        final Path root,
        final Path classes,
        final PrintStream out,
        final boolean canonicalPlatform
    ) throws IOException {
        validateGenerated(root, classes);
        final List<Path> changed = new ArrayList<>();
        for (final Path relative : trackedFiles(canonicalPlatform)) {
            final Path generated = classes.resolve(relative);
            final Path tracked = root.resolve(relative);
            final String generatedText = Files2.readStringIfExists(generated);
            if (Files.isRegularFile(tracked) && generatedText.equals(Files2.readStringIfExists(tracked))) {
                continue;
            }
            Files2.writeString(tracked, generatedText);
            changed.add(relative);
        }
        printResult(root, classes, out, changed, canonicalPlatform);
        return new RefreshResult(List.copyOf(changed));
    }

    private static void validateGenerated(final Path root, final Path classes) throws IOException {
        for (final Path relative : GENERATED_STATUS_FILES) {
            final Path generated = classes.resolve(relative);
            if (!Files.isRegularFile(generated)) {
                throw new IOException("Compatibility generator did not write " + displayPath(root, generated) + ".");
            }
        }
    }

    private static String displayPath(final Path root, final Path path) {
        final Path displayed;
        if (path.startsWith(root)) {
            displayed = root.relativize(path);
        } else {
            displayed = path;
        }
        return Strings2.replaceChar(displayed.toString(), File.separatorChar, '/');
    }

    static void failWhenStatusWasStale(final RefreshResult result) {
        if (result.statusChanged()) {
            throw new IllegalStateException(
                "Compatibility status was stale and has been regenerated. "
                    + "Review the generated changes, then rerun mvn verify."
            );
        }
    }

    private static List<Path> trackedFiles(final boolean canonicalPlatform) {
        if (canonicalPlatform) {
            return GENERATED_STATUS_FILES;
        }
        return MATRIX_FILES;
    }

    private static void printResult(
        final Path root,
        final Path classes,
        final PrintStream out,
        final List<Path> changed,
        final boolean canonicalPlatform
    ) {
        if (changed.isEmpty()) {
            out.println("Compatibility status documents are current.");
        } else {
            out.println("Refreshed compatibility status documents:");
            for (final Path path : changed) {
                out.println("  " + Strings2.replaceChar(path.toString(), File.separatorChar, '/'));
            }
        }
        if (!canonicalPlatform) {
            out.println("Active JDK report generated at " + displayPath(root, classes.resolve(JDK_STATUS_FILE)) + ".");
            out.println("Tracked JDK compatibility remains owned by the canonical platform.");
        }
    }

    record RefreshResult(List<Path> changedFiles) {
        boolean statusChanged() {
            return !changedFiles.isEmpty();
        }
    }
}
