package javan.cli;

import javan.build.BindingLanguage;
import javan.build.BuildKind;
import javan.build.LibraryFormat;
import javan.profile.Profile;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class OptionsTest {
    @Test
    void parseResultDefaultsToHelpWhenNoArgumentsAreProvided() {
        final Options.ParseResult result = Options.parseResult(new String[]{});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().command()).isEqualTo(Command.HELP);
    }

    @Test
    void parseResultSupportsShortHelpFlag() {
        final Options.ParseResult result = Options.parseResult(new String[]{"-h"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().command()).isEqualTo(Command.HELP);
    }

    @Test
    void parseResultSupportsShortVersionFlag() {
        final Options.ParseResult result = Options.parseResult(new String[]{"-V"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().command()).isEqualTo(Command.VERSION);
    }

    @Test
    void parseResultPreservesJavacPassthroughArguments() {
        final Options.ParseResult result = Options.parseResult(new String[]{"javac", "--release", "25", "Main.java"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().command()).isEqualTo(Command.JAVAC);
        assertThat(result.options().passthroughArgs()).containsExactly("--release", "25", "Main.java");
    }

    @Test
    void parseResultRejectsMissingClassesValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"check", "--classes"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --classes");
    }

    @Test
    void parseResultRejectsMissingClasspathValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"check", "--classpath"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --classpath");
    }

    @Test
    void parseResultRejectsMissingOutputValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--output"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --output");
    }

    @Test
    void parseResultRejectsMissingKindValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--kind"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --kind");
    }

    @Test
    void parseResultRejectsMissingFormatValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--library", "--format"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --format");
    }

    @Test
    void parseResultRejectsMissingProfileValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"check", "--profile"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --profile");
    }

    @Test
    void parseResultRejectsMissingExportValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--library", "--export"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --export");
    }

    @Test
    void parseResultRejectsMissingBindingsValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--library", "--bindings"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --bindings");
    }

    @Test
    void parseResultRejectsMissingTargetValue() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--target"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Missing value for --target");
    }

    @Test
    void parseResultRejectsUnsupportedBuildKind() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--kind", "plugin"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Unsupported build kind: plugin");
    }

    @Test
    void parseResultRejectsUnsupportedLibraryFormat() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--library", "--format", "dynamic"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Unsupported library format: dynamic");
    }

    @Test
    void parseResultRejectsUnsupportedBindingLanguage() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--library", "--bindings", "zig"});

        assertThat(result.pass()).isFalse();
        assertThat(result.error()).isEqualTo("Unsupported binding language: zig");
    }

    @Test
    void parseResultDeduplicatesLibraryFormats() {
        final Options.ParseResult result = Options.parseResult(new String[]{
            "build",
            "--library",
            "--format",
            "static,static,shared"
        });

        assertThat(result.pass()).isTrue();
        assertThat(result.options().libraryFormats()).containsExactly(LibraryFormat.STATIC, LibraryFormat.SHARED);
    }

    @Test
    void parseResultDeduplicatesBindingLanguages() {
        final Options.ParseResult result = Options.parseResult(new String[]{
            "build",
            "--library",
            "--bindings",
            "c,c,rust"
        });

        assertThat(result.pass()).isTrue();
        assertThat(result.options().bindings()).containsExactly(BindingLanguage.C, BindingLanguage.RUST);
    }

    @Test
    void parseResultIgnoresBlankClasspathEntries() {
        final Options.ParseResult result = Options.parseResult(new String[]{
            "check",
            "--classpath",
            "a.jar" + File.pathSeparator + " " + File.pathSeparator + "b.jar"
        });

        assertThat(result.pass()).isTrue();
        assertThat(result.options().classpathEntries()).containsExactly(Path.of("a.jar"), Path.of("b.jar"));
    }

    @Test
    void parseResultTreatsSecondPathAsPassthroughArgument() {
        final Options.ParseResult result = Options.parseResult(new String[]{"run", "app", "one"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().target()).contains(Path.of("app"));
        assertThat(result.options().passthroughArgs()).containsExactly("one");
    }

    @Test
    void parseResultAcceptsLibBuildKindAlias() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--kind", "lib"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().buildKind()).isEqualTo(BuildKind.LIBRARY);
    }

    @Test
    void parseResultAcceptsStaticLibHyphenatedBuildKind() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--kind", "static-lib"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().buildKind()).isEqualTo(BuildKind.STATICLIB);
    }

    @Test
    void parseResultAcceptsSharedLibHyphenatedBuildKind() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--kind", "shared-lib"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().buildKind()).isEqualTo(BuildKind.SHAREDLIB);
    }

    @Test
    void parseResultAcceptsAllLibraryFormatsAlias() {
        final Options.ParseResult result = Options.parseResult(new String[]{"build", "--library", "--format", "all"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().libraryFormats()).containsExactly(LibraryFormat.STATIC, LibraryFormat.SHARED);
    }

    @Test
    void parseResultKeepsCoreProfileByDefault() {
        final Options.ParseResult result = Options.parseResult(new String[]{"check"});

        assertThat(result.pass()).isTrue();
        assertThat(result.options().profile()).isEqualTo(Profile.CORE);
    }
}
