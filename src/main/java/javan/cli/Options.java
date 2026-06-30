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
        final ParseResult result = parseResult(args);
        if (!result.pass()) {
            throw new IllegalArgumentException(result.error());
        }
        return result.options();
    }

    /**
     * Parses command line arguments without throwing for user input errors.
     *
     * @param args raw command line arguments
     * @return parse result
     */
    public static ParseResult parseResult(final String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            return ParseResult.success(empty(Command.HELP));
        }
        if ("--version".equals(args[0]) || "-V".equals(args[0])) {
            return ParseResult.success(empty(Command.VERSION));
        }
        final Command command = Command.parse(args[0]).orElse(Command.HELP);
        if (command == Command.JAVAC) {
            return ParseResult.success(new Options(
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
            ));
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
                final ValueResult value = requiredValueResult(args, ++index, "--main");
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                mainClass = Optional.of(value.value());
            } else if ("--classes".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, "--classes");
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                classFolders.add(Path.of(value.value()));
            } else if ("--classpath".equals(arg) || "-cp".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                classpathEntries.addAll(parseClasspath(value.value()));
            } else if ("--output".equals(arg) || "-o".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                outputName = Optional.of(value.value());
            } else if ("--kind".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                final BuildKindResult parsed = parseBuildKindResult(value.value());
                if (!parsed.pass()) {
                    return ParseResult.failure(parsed.error());
                }
                buildKindName = parsed.name();
                buildKind = parsed.kind();
            } else if ("--jar".equals(arg)) {
                buildKind = BuildKind.JAR;
                buildKindName = "JAR";
            } else if ("--library".equals(arg) || "--lib".equals(arg)) {
                buildKind = BuildKind.LIBRARY;
                buildKindName = "LIBRARY";
            } else if ("--format".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                final FormatResult parsed = parseLibraryFormats(value.value());
                if (!parsed.pass()) {
                    return ParseResult.failure(parsed.error());
                }
                libraryFormats.addAll(parsed.formats());
            } else if ("--profile".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                final Optional<Profile> parsed = Profile.parse(value.value());
                if (parsed.isEmpty()) {
                    return ParseResult.failure("Unsupported profile: " + value.value());
                }
                profile = parsed.orElseThrow();
            } else if ("--export".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                exports.add(value.value());
            } else if ("--bindings".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                final BindingResult parsed = parseBindingsResult(value.value());
                if (!parsed.pass()) {
                    return ParseResult.failure(parsed.error());
                }
                bindings.addAll(parsed.bindings());
            } else if ("--release".equals(arg)) {
                release = true;
            } else if ("--target".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                targetTriple = Optional.of(value.value());
            } else if (arg.startsWith("-")) {
                return ParseResult.failure("Unknown option: " + arg);
            } else if (target.isEmpty()) {
                target = Optional.of(Path.of(arg));
            } else {
                passthroughArgs.add(arg);
            }
        }

        final FormatResult resolvedFormats = libraryFormatsResult(buildKindName, libraryFormats);
        if (!resolvedFormats.pass()) {
            return ParseResult.failure(resolvedFormats.error());
        }
        return ParseResult.success(new Options(
            command,
            target,
            mainClass,
            List.copyOf(classFolders),
            List.copyOf(classpathEntries),
            outputName,
            buildKind,
            buildKindName,
            resolvedFormats.formats(),
            profile,
            List.copyOf(exports),
            distinctBindings(bindings),
            release,
            targetTriple,
            List.copyOf(passthroughArgs)
        ));
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

    private static ValueResult requiredValueResult(final String[] args, final int index, final String option) {
        if (index >= args.length) {
            return new ValueResult(false, "", "Missing value for " + option);
        }
        return new ValueResult(true, args[index], "");
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

    private static BindingResult parseBindingsResult(final String value) {
        final List<BindingLanguage> entries = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                final String entry = Strings2.trimAscii(Strings2.slice(value, start, index));
                if (!Strings2.isBlank(entry)) {
                    final Optional<BindingLanguage> parsed = BindingLanguage.parse(entry);
                    if (parsed.isEmpty()) {
                        return BindingResult.failure("Unsupported binding language: " + entry);
                    }
                    entries.add(parsed.orElseThrow());
                }
                start = index + 1;
            }
        }
        return BindingResult.success(entries);
    }

    private static BuildKindResult parseBuildKindResult(final String value) {
        final String name = canonicalBuildKindNameResult(value);
        if ("APP".equals(name)) {
            return BuildKindResult.success(BuildKind.APP, name);
        }
        if ("JAR".equals(name)) {
            return BuildKindResult.success(BuildKind.JAR, name);
        }
        if ("LIBRARY".equals(name)) {
            return BuildKindResult.success(BuildKind.LIBRARY, name);
        }
        if ("STATICLIB".equals(name)) {
            return BuildKindResult.success(BuildKind.STATICLIB, name);
        }
        if ("SHAREDLIB".equals(name)) {
            return BuildKindResult.success(BuildKind.SHAREDLIB, name);
        }
        return BuildKindResult.failure("Unsupported build kind: " + value);
    }

    private static String canonicalBuildKindNameResult(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiUpperCase(value), '-', '_');
        if ("LIB".equals(normalized)) {
            return "LIBRARY";
        }
        if ("STATIC_LIB".equals(normalized)) {
            return "STATICLIB";
        }
        if ("SHARED_LIB".equals(normalized)) {
            return "SHAREDLIB";
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
        return "";
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

    private static FormatResult libraryFormatsResult(final String buildKindName, final List<LibraryFormat> requested) {
        if (!requested.isEmpty() && !libraryBuildName(buildKindName)) {
            return FormatResult.failure("--format requires --library or a library --kind");
        }
        if (!requested.isEmpty()) {
            if ("STATICLIB".equals(buildKindName) && !exactlyOne(requested, "STATIC")) {
                return FormatResult.failure("--kind staticlib only supports --format static");
            }
            if ("SHAREDLIB".equals(buildKindName) && !exactlyOne(requested, "SHARED")) {
                return FormatResult.failure("--kind sharedlib only supports --format shared");
            }
            return FormatResult.success(requested);
        }
        if ("STATICLIB".equals(buildKindName)) {
            return FormatResult.success(List.of(LibraryFormat.STATIC));
        }
        if ("SHAREDLIB".equals(buildKindName)) {
            return FormatResult.success(List.of(LibraryFormat.SHARED));
        }
        if ("LIBRARY".equals(buildKindName)) {
            return FormatResult.success(List.of(LibraryFormat.STATIC, LibraryFormat.SHARED));
        }
        if ("APP".equals(buildKindName) || "JAR".equals(buildKindName)) {
            return FormatResult.success(List.of());
        }
        return FormatResult.failure("Unsupported build kind");
    }

    private static FormatResult parseLibraryFormats(final String value) {
        final String trimmed = Strings2.trimAscii(value);
        if (Strings2.equalsAsciiIgnoreCase("both", trimmed) || Strings2.equalsAsciiIgnoreCase("all", trimmed)) {
            return FormatResult.success(List.of(LibraryFormat.STATIC, LibraryFormat.SHARED));
        }
        final List<LibraryFormat> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                final String entry = Strings2.trimAscii(Strings2.slice(value, start, index));
                if (!Strings2.isBlank(entry)) {
                    final Optional<LibraryFormat> parsed = LibraryFormat.parse(entry);
                    if (parsed.isEmpty()) {
                        return FormatResult.failure("Unsupported library format: " + entry);
                    }
                    final LibraryFormat format = parsed.orElseThrow();
                    if (!containsFormat(result, format)) {
                        result.add(format);
                    }
                }
                start = index + 1;
            }
        }
        return FormatResult.success(result);
    }

    private static boolean containsFormat(final List<LibraryFormat> formats, final LibraryFormat target) {
        for (final LibraryFormat format : formats) {
            if (format == target) {
                return true;
            }
        }
        return false;
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

    /**
     * Result of parsing CLI options without throwing for user input errors.
     *
     * @param pass whether parsing succeeded
     * @param options parsed options, or help defaults when parsing failed
     * @param error user-facing error text without severity prefix
     */
    public record ParseResult(boolean pass, Options options, String error) {
        private static ParseResult success(final Options options) {
            return new ParseResult(true, options, "");
        }

        private static ParseResult failure(final String error) {
            return new ParseResult(false, empty(Command.HELP), error);
        }
    }

    private record ValueResult(boolean pass, String value, String error) {
    }

    private record BuildKindResult(boolean pass, BuildKind kind, String name, String error) {
        private static BuildKindResult success(final BuildKind kind, final String name) {
            return new BuildKindResult(true, kind, name, "");
        }

        private static BuildKindResult failure(final String error) {
            return new BuildKindResult(false, BuildKind.APP, "", error);
        }
    }

    private record FormatResult(boolean pass, List<LibraryFormat> formats, String error) {
        private static FormatResult success(final List<LibraryFormat> formats) {
            return new FormatResult(true, List.copyOf(formats), "");
        }

        private static FormatResult failure(final String error) {
            return new FormatResult(false, List.of(), error);
        }
    }

    private record BindingResult(boolean pass, List<BindingLanguage> bindings, String error) {
        private static BindingResult success(final List<BindingLanguage> bindings) {
            return new BindingResult(true, List.copyOf(bindings), "");
        }

        private static BindingResult failure(final String error) {
            return new BindingResult(false, List.of(), error);
        }
    }
}
