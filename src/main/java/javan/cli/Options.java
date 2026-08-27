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
 * @param libraryFormats requested native library formats
 * @param profile selected static profile
 * @param exports native library export declarations
 * @param bindings native library binding languages
 * @param release whether release optimizations are requested
 * @param targetTriple requested host target assertion for native builds
 * @param jobs requested native compiler worker cap
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
    List<LibraryFormat> libraryFormats,
    Profile profile,
    List<String> exports,
    List<BindingLanguage> bindings,
    boolean release,
    Optional<String> targetTriple,
    Optional<Integer> jobs,
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
        if ("--jn-facade-java".equals(args[0])) {
            return ParseResult.success(facade(Command.FACADE_JAVA, args));
        }
        if ("--jn-facade-javac".equals(args[0])) {
            return ParseResult.success(facade(Command.FACADE_JAVAC, args));
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
                List.of(),
                Profile.CORE,
                List.of(),
                List.of(),
                false,
                Optional.empty(),
                Optional.empty(),
                List.of(java.util.Arrays.copyOfRange(args, 1, args.length))
            ));
        }
        Optional<Path> target = Optional.empty();
        Optional<String> mainClass = Optional.empty();
        Optional<String> outputName = Optional.empty();
        BuildKind buildKind = BuildKind.APP;
        final List<LibraryFormat> libraryFormats = new ArrayList<>();
        Profile profile = Profile.CORE;
        boolean release = false;
        Optional<String> targetTriple = Optional.empty();
        Optional<Integer> jobs = Optional.empty();
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
                final Optional<BuildKind> parsed = BuildKind.parse(value.value());
                if (parsed.isEmpty()) {
                    return ParseResult.failure("Unsupported build kind: " + value.value());
                }
                buildKind = parsed.orElseThrow();
            } else if ("--jar".equals(arg)) {
                buildKind = BuildKind.JAR;
            } else if ("--library".equals(arg) || "--lib".equals(arg)) {
                buildKind = BuildKind.LIBRARY;
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
            } else if ("--jobs".equals(arg)) {
                final ValueResult value = requiredValueResult(args, ++index, arg);
                if (!value.pass()) {
                    return ParseResult.failure(value.error());
                }
                final Optional<Integer> parsed = positiveInteger(value.value());
                if (parsed.isEmpty()) {
                    return ParseResult.failure("--jobs requires a positive integer");
                }
                jobs = parsed;
            } else if (arg.startsWith("-")) {
                return ParseResult.failure("Unknown option: " + arg);
            } else if (target.isEmpty()) {
                target = Optional.of(Path.of(arg));
            } else {
                passthroughArgs.add(arg);
            }
        }

        final FormatResult resolvedFormats = libraryFormatsResult(buildKind, libraryFormats);
        if (!resolvedFormats.pass()) {
            return ParseResult.failure(resolvedFormats.error());
        }
        if (jobs.isPresent() && command != Command.BUILD && command != Command.RUN) {
            return ParseResult.failure("--jobs requires build or run");
        }
        if (jobs.isPresent() && buildKind != BuildKind.APP) {
            return ParseResult.failure("--jobs currently supports native application builds only");
        }
        return ParseResult.success(new Options(
            command,
            target,
            mainClass,
            List.copyOf(classFolders),
            List.copyOf(classpathEntries),
            outputName,
            buildKind,
            resolvedFormats.formats(),
            profile,
            List.copyOf(exports),
            distinctBindings(bindings),
            release,
            targetTriple,
            jobs,
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
            List.of(),
            Profile.CORE,
            List.of(),
            List.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            List.of()
        );
    }

    private static Options facade(final Command command, final String[] args) {
        return new Options(
            command,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            Optional.empty(),
            BuildKind.APP,
            List.of(),
            Profile.CORE,
            List.of(),
            List.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            List.of(java.util.Arrays.copyOfRange(args, 1, args.length))
        );
    }

    private static ValueResult requiredValueResult(final String[] args, final int index, final String option) {
        if (index >= args.length) {
            return new ValueResult(false, "", "Missing value for " + option);
        }
        return new ValueResult(true, args[index], "");
    }

    private static Optional<Integer> positiveInteger(final String value) {
        final int start = value.startsWith("+") ? 1 : 0;
        if (start == value.length()) {
            return Optional.empty();
        }
        int result = 0;
        for (int index = start; index < value.length(); index++) {
            final int digit = value.charAt(index) - '0';
            if (digit < 0 || digit > 9 || result > 214748364 || (result == 214748364 && digit > 7)) {
                return Optional.empty();
            }
            result = result * 10 + digit;
        }
        return result > 0 ? Optional.of(result) : Optional.empty();
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

    private static List<BindingLanguage> distinctBindings(final List<BindingLanguage> bindings) {
        final List<BindingLanguage> distinct = new ArrayList<>();
        for (final BindingLanguage binding : bindings) {
            if (!distinct.contains(binding)) {
                distinct.add(binding);
            }
        }
        return List.copyOf(distinct);
    }

    private static FormatResult libraryFormatsResult(final BuildKind buildKind, final List<LibraryFormat> requested) {
        if (!requested.isEmpty() && !buildKind.library()) {
            return FormatResult.failure("--format requires --library or a library --kind");
        }
        if (!requested.isEmpty()) {
            if (buildKind == BuildKind.STATICLIB && !exactlyOne(requested, LibraryFormat.STATIC)) {
                return FormatResult.failure("--kind staticlib only supports --format static");
            }
            if (buildKind == BuildKind.SHAREDLIB && !exactlyOne(requested, LibraryFormat.SHARED)) {
                return FormatResult.failure("--kind sharedlib only supports --format shared");
            }
            return FormatResult.success(requested);
        }
        if (buildKind == BuildKind.STATICLIB) {
            return FormatResult.success(List.of(LibraryFormat.STATIC));
        }
        if (buildKind == BuildKind.SHAREDLIB) {
            return FormatResult.success(List.of(LibraryFormat.SHARED));
        }
        if (buildKind == BuildKind.LIBRARY) {
            return FormatResult.success(List.of(LibraryFormat.STATIC, LibraryFormat.SHARED));
        }
        if (buildKind == BuildKind.APP || buildKind == BuildKind.JAR) {
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

    private static boolean exactlyOne(final List<LibraryFormat> formats, final LibraryFormat expected) {
        if (formats.size() != 1) {
            return false;
        }
        return expected == formats.getFirst();
    }

    /**
     * Returns true when the build kind creates an application executable.
     *
     * @return true for app builds
     */
    public boolean appBuild() {
        return buildKind == BuildKind.APP;
    }

    /**
     * Returns true when the build kind creates a JVM jar.
     *
     * @return true for jar builds
     */
    public boolean jarBuild() {
        return buildKind == BuildKind.JAR;
    }

    /**
     * Returns true when the build kind creates native library artifacts.
     *
     * @return true for library builds
     */
    public boolean libraryBuild() {
        return buildKind.library();
    }

    /**
     * Returns true when the friendly library build kind was requested.
     *
     * @return true for combined library builds
     */
    public boolean combinedLibraryBuild() {
        return buildKind == BuildKind.LIBRARY;
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
