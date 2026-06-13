package javan.cli;

import javan.build.BindingLanguage;
import javan.build.BuildKind;
import javan.profile.Profile;

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
 * @param profile selected static profile
 * @param exports native library export declarations
 * @param bindings native library binding languages
 * @param noBuild whether Java build invocation should be skipped
 * @param release whether release optimizations are requested
 * @param targetTriple requested target triple
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
    Profile profile,
    List<String> exports,
    List<BindingLanguage> bindings,
    boolean noBuild,
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
                Profile.CORE,
                List.of(),
                List.of(),
                false,
                false,
                Optional.empty(),
                List.of(java.util.Arrays.copyOfRange(args, 1, args.length))
            );
        }
        Optional<Path> target = Optional.empty();
        Optional<String> mainClass = Optional.empty();
        Optional<String> outputName = Optional.empty();
        BuildKind buildKind = BuildKind.APP;
        Profile profile = Profile.CORE;
        boolean noBuild = false;
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
                buildKind = BuildKind.parse(value).orElseThrow(() -> new IllegalArgumentException("Unsupported build kind: " + value));
            } else if ("--profile".equals(arg)) {
                final String value = requiredValue(args, ++index, arg);
                profile = Profile.parse(value).orElseThrow(() -> new IllegalArgumentException("Unsupported profile: " + value));
            } else if ("--export".equals(arg)) {
                exports.add(requiredValue(args, ++index, arg));
            } else if ("--bindings".equals(arg)) {
                bindings.addAll(parseBindings(requiredValue(args, ++index, arg)));
            } else if ("--no-build".equals(arg)) {
                noBuild = true;
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
            profile,
            List.copyOf(exports),
            bindings.stream().distinct().toList(),
            noBuild,
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
            Profile.CORE,
            List.of(),
            List.of(),
            false,
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
        for (final String entry : value.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry));
            }
        }
        return entries;
    }

    private static List<BindingLanguage> parseBindings(final String value) {
        final List<BindingLanguage> entries = new ArrayList<>();
        for (final String entry : value.split(",")) {
            if (!entry.isBlank()) {
                entries.add(BindingLanguage.parse(entry.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported binding language: " + entry.trim())));
            }
        }
        return entries;
    }
}
