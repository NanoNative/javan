package javan.cli;

import javan.build.BindingLanguage;
import javan.build.BuildKind;
import javan.build.LibraryFormat;
import javan.profile.Profile;
import javan.util.Strings2;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed CLI options.
 *
 * @param command selected command
 * @param target optional project, classes, jar, or source target
 * @param mainClass explicit main class
 * @param classFolders explicit class folders
 * @param classpathEntries explicit classpath entries
 * @param outputName explicit output binary name
 * @param buildKind native artifact kind
 * @param buildKindName canonical native artifact kind name
 * @param libraryFormats requested native library formats
 * @param profile selected static profile
 * @param exports native library export declarations
 * @param bindings native library binding languages
 * @param release whether release optimizations are requested
 * @param targetTriple requested host target assertion for native builds
 * @param passthroughArgs arguments passed to a built program by {@code run}
 */
public record Options(
    Command command,
    Optional<Path> target,
    Optional<String> mainClass,
    List<Path> classFolders,
    List<Path> classpathEntries,
    Optional<String> outputName,
    BuildKind buildKind,
    String buildKindName,
    List<LibraryFormat> libraryFormats,
    Profile profile,
    List<String> exports,
    List<BindingLanguage> bindings,
    boolean release,
    Optional<String> targetTriple,
    List<String> passthroughArgs
) {
    /**
     * Parses command line arguments.
     *
     * @param args raw command line arguments
     * @return parsed options
     */
    public static Options parse(final String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            return empty(Command.HELP);
        }
        if ("--version".equals(args[0]) || "-V".equals(args[0])) {
            return empty(Command.VERSION);
        }
        final Command command = Command.parse(args[0]).orElse(Command.HELP);
        if (command == Command.JAVAC) {
            return new Options(
                command,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.empty(),
                BuildKind.APP,
                "APP",
                List.of(),
                Profile.CORE,
                List.of(),
                List.of(),
                false,
                Optional.empty(),
                List.of(java.util.Arrays.copyOfRange(args, 1, args.length))
            );
        }
        Optional<Path> target = Optional.empty();
        Optional<String> mainClass = Optional.empty();
        Optional<String> outputName = Optional.empty();
        BuildKind buildKind = BuildKind.APP;
        String buildKindName = "APP";
        final List<LibraryFormat> libraryFormats = new ArrayList<>();
        Profile profile = Profile.CORE;
        boolean release = false;
        Optional<String> targetTriple = Optional.empty();
        final List<Path> classFolders = new ArrayList<>();
        final List<Path> classpathEntries = new ArrayList<>();
        final List<String> exports = new ArrayList<>();
        final List<BindingLanguage> bindings = new ArrayList<>();
        final List<String> passthroughArgs = new ArrayList<>();
        boolean passthrough = false;

        for (int index = 1; index < args.length; index++) {
            final String arg = args[index];
            if (passthrough) {
                passthroughArgs.add(arg);
            } else if ("--".equals(arg)) {
                passthrough = true;
            } else if ("--main".equals(arg)) {
                mainClass = Optional.of(requiredValue(args, ++index, "--main"));
            } else if ("--classes".equals(arg)) {
                classFolders.add(Path.of(requiredValue(args, ++index, "--classes")));
            } else if ("--classpath".equals(arg) || "-cp".equals(arg)) {
                classpathEntries.addAll(parseClasspath(requiredValue(args, ++index, arg)));
            } else if ("--output".equals(arg) || "-o".equals(arg)) {
                outputName = Optional.of(requiredValue(args, ++index, arg));
            } else if ("--kind".equals(arg)) {
                final String value = requiredValue(args, ++index, arg);
                buildKindName = canonicalBuildKindName(value);
                buildKind = parseBuildKind(value);
            } else if ("--jar".equals(arg)) {
                buildKind = BuildKind.JAR;
                buildKindName = "JAR";
            } else if ("--library".equals(arg) || "--lib".equals(arg)) {
                buildKind = BuildKind.LIBRARY;
                buildKindName = "LIBRARY";
            } else if ("--format".equals(arg)) {
                libraryFormats.addAll(LibraryFormat.parseList(requiredValue(args, ++index, arg)));
            } else if ("--profile".equals(arg)) {
                final String value = requiredValue(args, ++index, arg);
                profile = parseProfile(value);
            } else if ("--export".equals(arg)) {
                exports.add(requiredValue(args, ++index, arg));
            } else if ("--bindings".equals(arg)) {
                bindings.addAll(parseBindings(requiredValue(args, ++index, arg)));
            } else if ("--release".equals(arg)) {
                release = true;
            } else if ("--target".equals(arg)) {
                targetTriple = Optional.of(requiredValue(args, ++index, arg));
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else if (target.isEmpty()) {
                target = Optional.of(Path.of(arg));
            } else {
                passthroughArgs.add(arg);
            }
        }

        return new Options(
            command,
            target,
            mainClass,
            List.copyOf(classFolders),
            List.copyOf(classpathEntries),
            outputName,
            buildKind,
            buildKindName,
            libraryFormats(buildKindName, libraryFormats),
            profile,
            List.copyOf(exports),
            distinctBindings(bindings),
            release,
            targetTriple,
            List.copyOf(passthroughArgs)
        );
    }

    private static Options empty(final Command command) {
        return new Options(
            command,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            Optional.empty(),
            BuildKind.APP,
            "APP",
            List.of(),
            Profile.CORE,
            List.of(),
            List.of(),
            false,
            Optional.empty(),
            List.of()
        );
    }

    private static String requiredValue(final String[] args, final int index, final String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static List<Path> parseClasspath(final String value) {
        final List<Path> entries = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == File.pathSeparatorChar) {
                final String entry = Strings2.trimAscii(Strings2.slice(value, start, index));
                if (!Strings2.isBlank(entry)) {
                    entries.add(Path.of(entry));
                }
                start = index + 1;
            }
        }
        return entries;
    }

    private static List<BindingLanguage> parseBindings(final String value) {
        final List<BindingLanguage> entries = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                final String entry = Strings2.trimAscii(Strings2.slice(value, start, index));
                if (!Strings2.isBlank(entry)) {
                    entries.add(parseBindingLanguage(entry));
                }
                start = index + 1;
            }
        }
        return entries;
    }

    private static BuildKind parseBuildKind(final String value) {
        final String name = canonicalBuildKindName(value);
        if ("APP".equals(name)) {
            return BuildKind.APP;
        }
        if ("JAR".equals(name)) {
            return BuildKind.JAR;
        }
        if ("LIBRARY".equals(name)) {
            return BuildKind.LIBRARY;
        }
        if ("STATICLIB".equals(name)) {
            return BuildKind.STATICLIB;
        }
        if ("SHAREDLIB".equals(name)) {
            return BuildKind.SHAREDLIB;
        }
        throw new IllegalArgumentException("Unsupported build kind: " + value);
    }

    private static String canonicalBuildKindName(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiUpperCase(value), '-', '_');
        if ("LIB".equals(normalized)) {
            return "LIBRARY";
        }
        if ("APP".equals(normalized)) {
            return normalized;
        }
        if ("JAR".equals(normalized)) {
            return normalized;
        }
        if ("LIBRARY".equals(normalized)) {
            return normalized;
        }
        if ("STATICLIB".equals(normalized)) {
            return normalized;
        }
        if ("SHAREDLIB".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported build kind: " + value);
    }

    private static Profile parseProfile(final String value) {
        final Profile parsed = Profile.parse(value).orElse(null);
        if (parsed == null) {
            throw new IllegalArgumentException("Unsupported profile: " + value);
        }
        return parsed;
    }

    private static BindingLanguage parseBindingLanguage(final String value) {
        final BindingLanguage parsed = BindingLanguage.parse(value).orElse(null);
        if (parsed == null) {
            throw new IllegalArgumentException("Unsupported binding language: " + value);
        }
        return parsed;
    }

    private static List<BindingLanguage> distinctBindings(final List<BindingLanguage> bindings) {
        final List<BindingLanguage> distinct = new ArrayList<>();
        for (final BindingLanguage binding : bindings) {
            if (!distinct.contains(binding)) {
                distinct.add(binding);
            }
        }
        return List.copyOf(distinct);
    }

    private static List<LibraryFormat> libraryFormats(final String buildKindName, final List<LibraryFormat> requested) {
        if (!requested.isEmpty() && !libraryBuildName(buildKindName)) {
            throw new IllegalArgumentException("--format requires --library or a library --kind");
        }
        if (!requested.isEmpty()) {
            if ("STATICLIB".equals(buildKindName) && !exactlyOne(requested, "STATIC")) {
                throw new IllegalArgumentException("--kind staticlib only supports --format static");
            }
            if ("SHAREDLIB".equals(buildKindName) && !exactlyOne(requested, "SHARED")) {
                throw new IllegalArgumentException("--kind sharedlib only supports --format shared");
            }
            return List.copyOf(requested);
        }
        if ("STATICLIB".equals(buildKindName)) {
            return List.of(LibraryFormat.STATIC);
        }
        if ("SHAREDLIB".equals(buildKindName)) {
            return List.of(LibraryFormat.SHARED);
        }
        if ("LIBRARY".equals(buildKindName)) {
            return List.of(LibraryFormat.STATIC, LibraryFormat.SHARED);
        }
        if ("APP".equals(buildKindName) || "JAR".equals(buildKindName)) {
            return List.of();
        }
        throw new IllegalStateException("Unsupported build kind");
    }

    private static boolean libraryBuildName(final String buildKindName) {
        if ("LIBRARY".equals(buildKindName)) {
            return true;
        }
        if ("STATICLIB".equals(buildKindName)) {
            return true;
        }
        if ("SHAREDLIB".equals(buildKindName)) {
            return true;
        }
        return false;
    }

    private static boolean exactlyOne(final List<LibraryFormat> formats, final String expected) {
        if (formats.size() != 1) {
            return false;
        }
        return expected.equals(formatName(formats.getFirst()));
    }

    /**
     * Returns true when the build kind creates an application executable.
     *
     * @return true for app builds
     */
    public boolean appBuild() {
        return "APP".equals(buildKindName);
    }

    /**
     * Returns true when the build kind creates a JVM jar.
     *
     * @return true for jar builds
     */
    public boolean jarBuild() {
        return "JAR".equals(buildKindName);
    }

    /**
     * Returns true when the build kind creates native library artifacts.
     *
     * @return true for library builds
     */
    public boolean libraryBuild() {
        return libraryBuildName(buildKindName);
    }

    /**
     * Returns true when the friendly library build kind was requested.
     *
     * @return true for combined library builds
     */
    public boolean combinedLibraryBuild() {
        return "LIBRARY".equals(buildKindName);
    }

    /**
     * Returns a stable name for a library format.
     *
     * @param format format enum
     * @return stable format name
     */
    public static String formatName(final LibraryFormat format) {
        return format.name();
    }
}
