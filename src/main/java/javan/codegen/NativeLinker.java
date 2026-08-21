package javan.codegen;

import javan.build.NativeLinkInputs;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Links generated C into a native executable with an available C compiler.
 */
public final class NativeLinker {
    // FNV-1a keeps the self-host cache independent of unavailable crypto APIs.
    private static final long FNV_OFFSET_BASIS = -3750763034362895579L;
    private static final long FNV_PRIME = 1099511628211L;

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
        return link(root, mainC, runtimeC, output, NativeLinkInputs.empty(), List.of());
    }

    /**
     * Links generated C sources with explicit native link inputs.
     *
     * @param root working directory
     * @param mainC generated main C path
     * @param runtimeC runtime C path
     * @param output output binary path
     * @param linkInputs validated native link inputs
     * @param importedSymbols immutable native import symbol names
     * @return output binary path
     * @throws IOException when no compiler is available or linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path link(
        final Path root,
        final Path mainC,
        final Path runtimeC,
        final Path output,
        final NativeLinkInputs linkInputs,
        final List<String> importedSymbols
    ) throws IOException, InterruptedException {
        final NativeLinkInputs inputs = Objects.requireNonNull(linkInputs, "linkInputs");
        final List<String> symbols = List.copyOf(Objects.requireNonNull(importedSymbols, "importedSymbols"));
        rejectUnsupportedFrameworks(inputs, System.getProperty("os.name", ""));
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        Files.createDirectories(output.getParent());
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(compilerFlags());
        command.add(mainC.toString());
        command.add(runtimeC.toString());
        appendDirectLinkInputs(command, inputs, runtimeC.getParent());
        command.addAll(platformLinkFlags());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw linkFailure("Native link failed", result, symbols);
        }
        return output;
    }

    /**
     * Links generated application sources while reusing verified generated object files.
     *
     * <p>The final link intentionally remains fresh: only generated C and runtime source objects
     * have a complete compiler-owned input boundary at this stage.</p>
     *
     * @param root working directory
     * @param mainC generated main C path
     * @param runtimeC generated runtime C path
     * @param output output binary path
     * @param cacheDirectory project-local native object cache
     * @param linkInputs validated native link inputs
     * @param importedSymbols immutable native import symbol names
     * @return linked artifact and object-cache evidence
     * @throws IOException when compilation, cache validation, or linking fails
     * @throws InterruptedException when interrupted while compiling or linking
     */
    public CacheLinkResult linkCached(
        final Path root,
        final Path mainC,
        final Path runtimeC,
        final Path output,
        final Path cacheDirectory,
        final NativeLinkInputs linkInputs,
        final List<String> importedSymbols
    ) throws IOException, InterruptedException {
        final NativeLinkInputs inputs = Objects.requireNonNull(linkInputs, "linkInputs");
        final List<String> symbols = List.copyOf(Objects.requireNonNull(importedSymbols, "importedSymbols"));
        rejectUnsupportedFrameworks(inputs, System.getProperty("os.name", ""));
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        final String compilerIdentity = compilerIdentity(root, compiler);
        Files.createDirectories(output.getParent());
        final CachedObject mainObject = cachedObject(root, compiler, compilerIdentity, mainC, cacheDirectory);
        final CachedObject runtimeObject = cachedObject(root, compiler, compilerIdentity, runtimeC, cacheDirectory);
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(compilerFlags());
        command.add(mainObject.linkObject().toString());
        command.add(runtimeObject.linkObject().toString());
        appendDirectLinkInputs(command, inputs, runtimeC.getParent());
        command.addAll(platformLinkFlags());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw linkFailure("Native link failed", result, symbols);
        }
        return new CacheLinkResult(output, List.of(mainObject.entry(), runtimeObject.entry()));
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
        return linkSharedLibrary(root, mainC, runtimeC, output, NativeLinkInputs.empty(), List.of());
    }

    /**
     * Links generated C sources into a shared library with explicit native link inputs.
     *
     * @param root working directory
     * @param mainC generated C path
     * @param runtimeC runtime C path
     * @param output output library path
     * @param linkInputs validated native link inputs
     * @param importedSymbols immutable native import symbol names
     * @return output library path
     * @throws IOException when linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path linkSharedLibrary(
        final Path root,
        final Path mainC,
        final Path runtimeC,
        final Path output,
        final NativeLinkInputs linkInputs,
        final List<String> importedSymbols
    )
        throws IOException, InterruptedException {
        final NativeLinkInputs inputs = Objects.requireNonNull(linkInputs, "linkInputs");
        final List<String> symbols = List.copyOf(Objects.requireNonNull(importedSymbols, "importedSymbols"));
        rejectUnsupportedFrameworks(inputs, System.getProperty("os.name", ""));
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        Files.createDirectories(output.getParent());
        final String osName = System.getProperty("os.name", "");
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(compilerFlags());
        if (isMacHost(osName)) {
            command.add("-dynamiclib");
        } else {
            command.add("-shared");
        }
        command.add("-fPIC");
        command.addAll(sharedUndefinedFlags(osName));
        command.add(mainC.toString());
        command.add(runtimeC.toString());
        appendDirectLinkInputs(command, inputs, runtimeC.getParent());
        command.addAll(platformLinkFlags());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw linkFailure("Native shared library link failed", result, symbols);
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
        return linkStaticLibrary(root, mainC, runtimeC, output, NativeLinkInputs.empty(), List.of());
    }

    /**
     * Links generated C sources into a static library with explicit native link inputs.
     *
     * @param root working directory
     * @param mainC generated C path
     * @param runtimeC runtime C path
     * @param output output library path
     * @param linkInputs validated native link inputs
     * @param importedSymbols immutable native import symbol names
     * @return output library path
     * @throws IOException when static-library inputs are unsupported or linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path linkStaticLibrary(
        final Path root,
        final Path mainC,
        final Path runtimeC,
        final Path output,
        final NativeLinkInputs linkInputs,
        final List<String> importedSymbols
    )
        throws IOException, InterruptedException {
        final NativeLinkInputs inputs = Objects.requireNonNull(linkInputs, "linkInputs");
        final List<String> symbols = List.copyOf(Objects.requireNonNull(importedSymbols, "importedSymbols"));
        rejectUnsupportedFrameworks(inputs, System.getProperty("os.name", ""));
        rejectUnsupportedStaticLibraryInputs(inputs);
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        final String archiver = requiredExecutable(List.of("ar"), "No archiver found. Install ar.");
        Files.createDirectories(output.getParent());
        final Path objects = output.getParent().resolve("objects");
        Files.createDirectories(objects);
        final Path mainObject = objects.resolve("javan_library.o");
        final Path runtimeObject = objects.resolve("javan_runtime.o");
        compileObject(root, compiler, mainC, mainObject);
        compileObject(root, compiler, runtimeC, runtimeObject);
        final List<Path> sourceObjects = compileConfiguredSources(root, compiler, inputs.sources(), objects, runtimeC.getParent());
        final List<String> command = new ArrayList<>();
        command.add(archiver);
        command.add("rcs");
        command.add(output.toString());
        command.add(mainObject.toString());
        command.add(runtimeObject.toString());
        for (final Path sourceObject : sourceObjects) {
            command.add(sourceObject.toString());
        }
        for (final Path object : inputs.objects()) {
            command.add(object.toString());
        }
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw linkFailure("Native static library link failed", result, symbols);
        }
        return output;
    }

    private static void rejectUnsupportedStaticLibraryInputs(final NativeLinkInputs linkInputs) throws IOException {
        if (!linkInputs.librarySearchPaths().isEmpty()) {
            throw new IOException("Static library link does not support library search paths");
        }
        if (!linkInputs.libraries().isEmpty()) {
            throw new IOException("Static library link does not support named libraries");
        }
        if (!linkInputs.frameworks().isEmpty()) {
            throw new IOException("Static library link does not support frameworks");
        }
    }

    private List<Path> compileConfiguredSources(
        final Path root,
        final String compiler,
        final List<Path> sources,
        final Path objectsDirectory,
        final Path generatedHeaderDirectory
    ) throws IOException, InterruptedException {
        final List<Path> objects = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            final Path object = objectsDirectory.resolve("native_input_" + index + ".o");
            compileObject(root, compiler, sources.get(index), object, List.of(generatedHeaderDirectory));
            objects.add(object);
        }
        return List.copyOf(objects);
    }

    private static void appendDirectLinkInputs(
        final List<String> command,
        final NativeLinkInputs linkInputs,
        final Path generatedHeaderDirectory
    ) {
        if (!linkInputs.sources().isEmpty()) {
            command.add("-I");
            command.add(generatedHeaderDirectory.toString());
        }
        for (final Path source : linkInputs.sources()) {
            command.add(source.toString());
        }
        for (final Path object : linkInputs.objects()) {
            command.add(object.toString());
        }
        for (final Path searchPath : linkInputs.librarySearchPaths()) {
            command.add("-L");
            command.add(searchPath.toString());
        }
        for (final String library : linkInputs.libraries()) {
            command.add("-l" + library);
        }
        for (final String framework : linkInputs.frameworks()) {
            command.add("-framework");
            command.add(framework);
        }
    }

    private static void rejectUnsupportedFrameworks(final NativeLinkInputs linkInputs, final String osName) throws IOException {
        if (!linkInputs.frameworks().isEmpty() && !isMacHost(osName)) {
            throw new IOException("macOS frameworks are only supported on macOS hosts");
        }
    }

    private static IOException linkFailure(
        final String prefix,
        final ProcessRunner.Result result,
        final List<String> importedSymbols
    ) {
        final String output = result.stderr() + result.stdout();
        final List<String> unresolvedSymbols = unresolvedImportedSymbols(output, importedSymbols);
        if (unresolvedSymbols.isEmpty()) {
            return new IOException(prefix + "\n" + output);
        }
        final StringBuilder names = new StringBuilder();
        for (int index = 0; index < unresolvedSymbols.size(); index++) {
            if (index > 0) {
                names.append(", ");
            }
            names.append(unresolvedSymbols.get(index));
        }
        return new IOException(prefix + "\nMissing native import symbols: " + names.toString() + "\n" + output);
    }

    private static List<String> unresolvedImportedSymbols(final String output, final List<String> importedSymbols) {
        final List<String> symbols = new ArrayList<>();
        final String normalizedOutput = Strings2.toAsciiLowerCase(output);
        for (final String importedSymbol : importedSymbols) {
            if (reportsUndefinedSymbol(output, normalizedOutput, importedSymbol)) {
                symbols.add(importedSymbol);
            }
        }
        return List.copyOf(symbols);
    }

    private static boolean reportsUndefinedSymbol(
        final String output,
        final String normalizedOutput,
        final String importedSymbol
    ) {
        if (symbolAfterMarker(output, normalizedOutput, "undefined reference to", importedSymbol)) {
            return true;
        }
        if (symbolAfterMarker(output, normalizedOutput, "undefined symbol:", importedSymbol)) {
            return true;
        }
        if (symbolAfterMarker(output, normalizedOutput, "undefined symbols", importedSymbol)) {
            return true;
        }
        return appleUndefinedSectionReports(output, normalizedOutput, importedSymbol);
    }

    private static boolean symbolAfterMarker(
        final String output,
        final String normalizedOutput,
        final String marker,
        final String importedSymbol
    ) {
        int markerStart = normalizedOutput.indexOf(marker);
        while (markerStart >= 0) {
            final int markerEnd = markerStart + marker.length();
            final int lineEnd = lineEnd(output, markerEnd);
            int symbolStart = markerEnd;
            while (symbolStart < lineEnd && !symbolCharacter(output.charAt(symbolStart))) {
                symbolStart++;
            }
            int symbolEnd = symbolStart;
            while (symbolEnd < lineEnd && symbolCharacter(output.charAt(symbolEnd))) {
                symbolEnd++;
            }
            if (matchesImportedSymbol(output, symbolStart, symbolEnd, importedSymbol)) {
                return true;
            }
            markerStart = normalizedOutput.indexOf(marker, markerEnd);
        }
        return false;
    }

    private static boolean appleUndefinedSectionReports(
        final String output,
        final String normalizedOutput,
        final String importedSymbol
    ) {
        final String heading = "undefined symbols";
        int sectionStart = normalizedOutput.indexOf(heading);
        while (sectionStart >= 0) {
            int sectionEnd = normalizedOutput.indexOf("symbol(s) not found", sectionStart + heading.length());
            if (sectionEnd < 0) {
                sectionEnd = output.length();
            }
            int reference = normalizedOutput.indexOf("referenced from:", sectionStart + heading.length());
            while (reference >= 0 && reference < sectionEnd) {
                int symbolEnd = reference;
                while (symbolEnd > sectionStart && !symbolCharacter(output.charAt(symbolEnd - 1))) {
                    symbolEnd--;
                }
                int symbolStart = symbolEnd;
                while (symbolStart > sectionStart && symbolCharacter(output.charAt(symbolStart - 1))) {
                    symbolStart--;
                }
                if (matchesImportedSymbol(output, symbolStart, symbolEnd, importedSymbol)) {
                    return true;
                }
                reference = normalizedOutput.indexOf("referenced from:", reference + 1);
            }
            sectionStart = normalizedOutput.indexOf(heading, sectionStart + heading.length());
        }
        return false;
    }

    private static int lineEnd(final String value, final int start) {
        int end = start;
        while (end < value.length() && value.charAt(end) != '\n' && value.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    private static boolean matchesImportedSymbol(
        final String output,
        final int start,
        final int end,
        final String importedSymbol
    ) {
        int offset = 0;
        if (end - start == importedSymbol.length() + 1 && start < end && output.charAt(start) == '_') {
            offset = 1;
        }
        if (end - start - offset != importedSymbol.length()) {
            return false;
        }
        for (int index = 0; index < importedSymbol.length(); index++) {
            if (output.charAt(start + offset + index) != importedSymbol.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static boolean symbolCharacter(final char value) {
        return (value >= 'A' && value <= 'Z')
            || (value >= 'a' && value <= 'z')
            || (value >= '0' && value <= '9')
            || value == '_';
    }

    private void compileObject(final Path root, final String compiler, final Path source, final Path output)
        throws IOException, InterruptedException {
        compileObject(root, compiler, source, output, List.of());
    }

    private CachedObject cachedObject(
        final Path root,
        final String compiler,
        final String compilerIdentity,
        final Path source,
        final Path cacheDirectory
    ) throws IOException, InterruptedException {
        final String fingerprint = objectFingerprint(compiler, compilerIdentity, source);
        final Path object = cacheDirectory.resolve(fingerprint + ".o");
        final Path checksum = cacheDirectory.resolve(fingerprint + ".fnv64");
        if (Files.isRegularFile(object) && Files.isRegularFile(checksum)
            && objectChecksum(object).equals(Files.readString(checksum).trim())) {
            return new CachedObject(new CacheEntry(source.getFileName().toString(), object, true), object);
        }
        Files.createDirectories(cacheDirectory);
        final Path sourceDirectory = Objects.requireNonNull(source.getParent(), "source parent");
        final Path staging = sourceDirectory.resolve(source.getFileName().toString() + ".object");
        compileObject(root, compiler, source, staging);
        final String objectChecksum = objectChecksum(staging);
        Files.write(object, Files.readAllBytes(staging));
        Files.writeString(checksum, objectChecksum + System.lineSeparator());
        return new CachedObject(new CacheEntry(source.getFileName().toString(), object, false), staging);
    }

    private String compilerIdentity(final Path root, final String compiler) throws IOException, InterruptedException {
        final ProcessRunner.Result result = processRunner.run(root, List.of(compiler, "--version"));
        return result.exitCode() + "\n" + result.stdout() + "\n" + result.stderr();
    }

    private static String objectFingerprint(final String compiler, final String compilerIdentity, final Path source) throws IOException {
        long hash = FNV_OFFSET_BASIS;
        hash = fingerprint(hash, "javan-native-object-v2");
        hash = fingerprint(hash, System.getProperty("os.name", ""));
        hash = fingerprint(hash, System.getProperty("os.arch", ""));
        hash = fingerprint(hash, compiler);
        hash = fingerprint(hash, compilerIdentity);
        final Path compilerPath = Path.of(compiler);
        if (Files.isRegularFile(compilerPath)) {
            hash = fingerprint(hash, compilerPath.toAbsolutePath().normalize().toString());
            hash = fingerprint(hash, Long.toString(Files.size(compilerPath)));
            hash = fingerprint(hash, Long.toString(Files.getLastModifiedTime(compilerPath).toMillis()));
        }
        for (final String flag : compilerFlags()) {
            hash = fingerprint(hash, flag);
        }
        hash = fingerprint(hash, "-fPIC");
        hash = fingerprint(hash, Files.readAllBytes(source));
        final Path directory = source.getParent();
        final Path header = directory == null ? source : directory.resolve("javan_runtime.h");
        if (Files.isRegularFile(header)) {
            hash = fingerprint(hash, header.getFileName().toString());
            hash = fingerprint(hash, Files.readAllBytes(header));
        }
        return Long.toString(hash);
    }

    private static String objectChecksum(final Path object) throws IOException {
        return Long.toString(fingerprint(FNV_OFFSET_BASIS, Files.readAllBytes(object)));
    }

    private static long fingerprint(long hash, final String value) {
        hash = fingerprintByte(hash, 0);
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            hash = fingerprintByte(hash, character);
            hash = fingerprintByte(hash, character >>> 8);
        }
        return hash;
    }

    private static long fingerprint(long hash, final byte[] value) {
        hash = fingerprintByte(hash, 0);
        for (final byte byteValue : value) {
            hash = fingerprintByte(hash, byteValue);
        }
        return hash;
    }

    private static long fingerprintByte(final long hash, final int value) {
        return (hash ^ (value & 255)) * FNV_PRIME;
    }

    private void compileObject(
        final Path root,
        final String compiler,
        final Path source,
        final Path output,
        final List<Path> includeDirectories
    ) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(compilerFlags());
        command.add("-fPIC");
        for (final Path includeDirectory : includeDirectories) {
            command.add("-I");
            command.add(includeDirectory.toString());
        }
        command.add("-c");
        command.add(source.toString());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw new IOException("Native compile failed\n" + result.stderr() + result.stdout());
        }
    }

    private static List<String> compilerFlags() {
        // Generated comparisons preserve explicit IR grouping; their redundant parentheses are intentional.
        final String os = Strings2.toAsciiLowerCase(System.getProperty("os.name", ""));
        if (os.contains("win")) {
            return List.of("-Wno-parentheses");
        }
        return List.of("-pthread", "-Wno-parentheses");
    }

    private static List<String> platformLinkFlags() {
        return platformLinkFlagsForOs(System.getProperty("os.name", ""));
    }

    static List<String> platformLinkFlagsForOs(final String osName) {
        if (isWindowsHost(osName)) {
            return List.of("-lws2_32");
        }
        if (Strings2.toAsciiLowerCase(osName).contains("linux")) {
            return List.of("-lm");
        }
        return List.of();
    }

    private static List<String> compilerCandidates() {
        return compilerCandidatesForOs(System.getProperty("os.name", ""), System.getenv("CC"));
    }

    static List<String> compilerCandidatesForOs(final String osName, final String configuredCompiler) {
        final List<String> result = new ArrayList<>();
        if (!Strings2.isBlank(configuredCompiler)) {
            result.add(configuredCompiler.trim());
        }
        if (isWindowsHost(osName)) {
            result.add("gcc");
            result.add("clang");
            result.add("cc");
            return List.copyOf(result);
        }
        result.add("cc");
        result.add("clang");
        result.add("gcc");
        return List.copyOf(result);
    }

    private String requiredExecutable(final List<String> executables, final String message) throws IOException, InterruptedException {
        final Optional<String> pathExecutable = firstOnPath(executables);
        if (pathExecutable.isPresent()) {
            return pathExecutable.orElseThrow();
        }
        final Optional<String> executable = processRunner.firstAvailable(executables);
        if (executable.isEmpty()) {
            throw new IOException(message);
        }
        return executable.orElseThrow();
    }

    private static Optional<String> firstOnPath(final List<String> executables) {
        return firstOnPathForOs(
            executables,
            System.getenv("PATH"),
            System.getProperty("os.name", ""),
            windowsExecutableExtensions()
        );
    }

    static Optional<String> firstOnPathForOs(
        final List<String> executables,
        final String path,
        final String osName,
        final List<String> windowsExtensions
    ) {
        for (final String executable : executables) {
            final Optional<String> resolved = resolveOnPath(executable, path, osName, windowsExtensions);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveOnPath(final String executable) {
        return resolveOnPath(
            executable,
            System.getenv("PATH"),
            System.getProperty("os.name", ""),
            windowsExecutableExtensions()
        );
    }

    private static Optional<String> resolveOnPath(
        final String executable,
        final String path,
        final String osName,
        final List<String> windowsExtensions
    ) {
        if (Strings2.isBlank(executable)) {
            return Optional.empty();
        }
        if (containsPathSeparator(executable)) {
            return resolveExecutablePathForOs(Path.of(executable), osName, windowsExtensions);
        }
        if (Strings2.isBlank(path)) {
            return Optional.empty();
        }
        final char separator = pathSeparator();
        int start = 0;
        for (int index = 0; index <= path.length(); index++) {
            if (index == path.length() || path.charAt(index) == separator) {
                String directory = Strings2.slice(path, start, index);
                if (Strings2.isBlank(directory)) {
                    directory = ".";
                }
                final Optional<String> resolved = resolveExecutablePathForOs(Path.of(directory).resolve(executable), osName, windowsExtensions);
                if (resolved.isPresent()) {
                    return resolved;
                }
                start = index + 1;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveExecutablePath(final Path candidate) {
        return resolveExecutablePathForOs(candidate, System.getProperty("os.name", ""), windowsExecutableExtensions());
    }

    static Optional<String> resolveExecutablePathForOs(final Path candidate, final String osName, final List<String> windowsExtensions) {
        if (Files.isExecutable(candidate)) {
            return Optional.of(candidate.toString());
        }
        if (!isWindowsHost(osName) || hasExplicitExtension(candidate)) {
            return Optional.empty();
        }
        for (final String extension : windowsExtensions) {
            final Path extended = Path.of(candidate.toString() + extension);
            if (Files.isExecutable(extended)) {
                return Optional.of(extended.toString());
            }
        }
        return Optional.empty();
    }

    private static boolean hasExplicitExtension(final Path candidate) {
        final Path fileName = candidate.getFileName();
        if (fileName == null) {
            return false;
        }
        final String name = fileName.toString();
        final int index = name.lastIndexOf('.');
        return index > 0 && index < name.length() - 1;
    }

    private static List<String> windowsExecutableExtensions() {
        final String pathExt = System.getenv("PATHEXT");
        if (Strings2.isBlank(pathExt)) {
            return List.of(".exe", ".cmd", ".bat", ".com");
        }
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= pathExt.length(); index++) {
            if (index == pathExt.length() || pathExt.charAt(index) == ';') {
                final String extension = Strings2.slice(pathExt, start, index).trim();
                if (!Strings2.isBlank(extension)) {
                    result.add(extension.startsWith(".") ? extension : "." + extension);
                }
                start = index + 1;
            }
        }
        if (result.isEmpty()) {
            return List.of(".exe", ".cmd", ".bat", ".com");
        }
        return List.copyOf(result);
    }

    private static boolean containsPathSeparator(final String executable) {
        for (int index = 0; index < executable.length(); index++) {
            final char ch = executable.charAt(index);
            if (ch == '/' || ch == '\\') {
                return true;
            }
        }
        return false;
    }

    private static char pathSeparator() {
        final String separator = System.getProperty("path.separator", ":");
        if (separator.isEmpty()) {
            return ':';
        }
        return separator.charAt(0);
    }

    private static boolean isWindowsHost() {
        return isWindowsHost(System.getProperty("os.name", ""));
    }

    private static boolean isMacHost(final String osName) {
        final String normalized = Strings2.toAsciiLowerCase(osName);
        return normalized.contains("mac") || normalized.contains("darwin");
    }

    private static List<String> sharedUndefinedFlags(final String osName) {
        if (isMacHost(osName)) {
            return List.of("-Wl,-undefined,error");
        }
        return List.of("-Wl,--no-undefined");
    }

    private static boolean isWindowsHost(final String osName) {
        return Strings2.toAsciiLowerCase(osName).contains("win");
    }

    private record CachedObject(CacheEntry entry, Path linkObject) {
        private CachedObject {
            entry = Objects.requireNonNull(entry, "entry");
            linkObject = Objects.requireNonNull(linkObject, "linkObject");
        }
    }

    /**
     * Native link result with immutable generated-object cache evidence.
     *
     * @param artifact linked native artifact
     * @param objects generated object cache entries
     */
    public record CacheLinkResult(Path artifact, List<CacheEntry> objects) {
        public CacheLinkResult {
            artifact = Objects.requireNonNull(artifact, "artifact");
            objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        }
    }

    /**
     * One generated native object cache decision.
     *
     * @param source generated source file name
     * @param object verified cached object file
     * @param reused whether the object was reused rather than compiled
     */
    public record CacheEntry(String source, Path object, boolean reused) {
        public CacheEntry {
            source = Objects.requireNonNull(source, "source");
            object = Objects.requireNonNull(object, "object");
        }
    }
}
