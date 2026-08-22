package javan.classfile;

import javan.toolchain.CurrentJdkTools;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Sha256;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Extracts jars once into a content-addressed build cache.
 */
public final class JarCache {
    private final ProcessRunner processRunner;

    /**
     * Creates a cache using the local JDK toolchain.
     */
    public JarCache() {
        this(new ProcessRunner());
    }

    /**
     * Creates a cache with an explicit process runner.
     *
     * @param processRunner process runner used for JDK jar extraction
     */
    public JarCache(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Returns the content-addressed extraction directory for a jar.
     *
     * @param jar jar file
     * @param outputDirectory build output directory
     * @return deterministic cache directory
     * @throws IOException when the jar cannot be hashed
     */
    public static Path path(final Path jar, final Path outputDirectory) throws IOException {
        return path(jar, outputDirectory, Sha256.of(jar));
    }

    /**
     * Returns an existing complete extraction or extracts the jar once.
     *
     * @param jar jar file
     * @param outputDirectory build output directory
     * @return complete extraction directory
     * @throws IOException when the jar cannot be extracted
     * @throws InterruptedException when extraction is interrupted
     */
    public Path extract(final Path jar, final Path outputDirectory) throws IOException, InterruptedException {
        return extract(jar, outputDirectory, Sha256.of(jar));
    }

    /**
     * Returns an existing complete extraction or extracts a jar with an already verified digest.
     *
     * @param jar jar file
     * @param outputDirectory build output directory
     * @param checksum lowercase SHA-256 digest of the jar
     * @return complete extraction directory
     * @throws IOException when the jar cannot be extracted
     * @throws InterruptedException when extraction is interrupted
     */
    public Path extract(
        final Path jar,
        final Path outputDirectory,
        final String checksum
    ) throws IOException, InterruptedException {
        final Path cache = path(jar, outputDirectory, checksum);
        final Path complete = complete(cache);
        if (Files.isDirectory(cache) && Files.isRegularFile(complete)) {
            return cache;
        }
        Files2.deleteRecursive(cache);
        Files.deleteIfExists(complete);
        Files.createDirectories(cache);
        final ProcessRunner.Result result = processRunner.run(cache, List.of(
            CurrentJdkTools.jar(),
            "--extract",
            "--file",
            jar.toAbsolutePath().toString()
        ));
        if (result.exitCode() != 0) {
            Files2.deleteRecursive(cache);
            throw new IOException("Unable to extract jar " + jar.toString() + ": " + result.stderr());
        }
        Files2.writeString(complete, "complete\n");
        return cache;
    }

    private static Path complete(final Path cache) {
        return cache.getParent().resolve(cache.getFileName().toString() + ".complete");
    }

    private static Path path(final Path jar, final Path outputDirectory, final String checksum) {
        final Path fileName = jar.getFileName();
        final String base = fileName == null ? "dependency.jar" : fileName.toString();
        return outputDirectory.resolve("jar-cache").resolve(Strings2.executableName(base) + "-" + checksum);
    }
}
