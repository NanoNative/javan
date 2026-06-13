package javan.codegen;

import javan.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Links generated C into a native executable with an available C compiler.
 */
public final class NativeLinker {
    private final ProcessRunner processRunner;

    /**
     * Creates a native linker.
     */
    public NativeLinker() {
        this(new ProcessRunner());
    }

    /**
     * Creates a native linker.
     *
     * @param processRunner process runner
     */
    public NativeLinker(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Links generated C sources.
     *
     * @param root working directory
     * @param mainC generated main C path
     * @param runtimeC runtime C path
     * @param output output binary path
     * @return output binary path
     * @throws IOException when no compiler is available or linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path link(final Path root, final Path mainC, final Path runtimeC, final Path output) throws IOException, InterruptedException {
        final String compiler = processRunner.firstAvailable(List.of("cc", "clang", "gcc"))
            .orElseThrow(() -> new IOException("No C compiler found. Install cc, clang, or gcc."));
        Files.createDirectories(output.getParent());
        final List<String> command = List.of(compiler, mainC.toString(), runtimeC.toString(), "-o", output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw new IOException("Native link failed\n" + result.stderr() + result.stdout());
        }
        return output;
    }

    /**
     * Links generated C sources into a shared library.
     *
     * @param root working directory
     * @param mainC generated C path
     * @param runtimeC runtime C path
     * @param output output library path
     * @return output library path
     * @throws IOException when linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path linkSharedLibrary(final Path root, final Path mainC, final Path runtimeC, final Path output)
        throws IOException, InterruptedException {
        final String compiler = processRunner.firstAvailable(List.of("cc", "clang", "gcc"))
            .orElseThrow(() -> new IOException("No C compiler found. Install cc, clang, or gcc."));
        Files.createDirectories(output.getParent());
        final boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        final List<String> command = mac
            ? List.of(compiler, "-dynamiclib", "-fPIC", mainC.toString(), runtimeC.toString(), "-o", output.toString())
            : List.of(compiler, "-shared", "-fPIC", mainC.toString(), runtimeC.toString(), "-o", output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw new IOException("Native shared library link failed\n" + result.stderr() + result.stdout());
        }
        return output;
    }

    /**
     * Links generated C sources into a static library.
     *
     * @param root working directory
     * @param mainC generated C path
     * @param runtimeC runtime C path
     * @param output output library path
     * @return output library path
     * @throws IOException when linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path linkStaticLibrary(final Path root, final Path mainC, final Path runtimeC, final Path output)
        throws IOException, InterruptedException {
        final String compiler = processRunner.firstAvailable(List.of("cc", "clang", "gcc"))
            .orElseThrow(() -> new IOException("No C compiler found. Install cc, clang, or gcc."));
        final String archiver = processRunner.firstAvailable(List.of("ar"))
            .orElseThrow(() -> new IOException("No archiver found. Install ar."));
        Files.createDirectories(output.getParent());
        final Path objects = output.getParent().resolve("objects");
        Files.createDirectories(objects);
        final Path mainObject = objects.resolve("javan_library.o");
        final Path runtimeObject = objects.resolve("javan_runtime.o");
        compileObject(root, compiler, mainC, mainObject);
        compileObject(root, compiler, runtimeC, runtimeObject);
        final ProcessRunner.Result result = processRunner.run(root, List.of(
            archiver,
            "rcs",
            output.toString(),
            mainObject.toString(),
            runtimeObject.toString()
        ));
        if (result.exitCode() != 0) {
            throw new IOException("Native static library link failed\n" + result.stderr() + result.stdout());
        }
        return output;
    }

    private void compileObject(final Path root, final String compiler, final Path source, final Path output)
        throws IOException, InterruptedException {
        final ProcessRunner.Result result = processRunner.run(root, List.of(
            compiler,
            "-fPIC",
            "-c",
            source.toString(),
            "-o",
            output.toString()
        ));
        if (result.exitCode() != 0) {
            throw new IOException("Native compile failed\n" + result.stderr() + result.stdout());
        }
    }
}
