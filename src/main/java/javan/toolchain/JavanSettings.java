package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Global javan settings loaded from settings.toml.
 *
 * @param defaultToolchain preferred installed toolchain id
 * @param defaultJdk preferred JDK version
 * @param autoInstall true when missing toolchains may be installed automatically
 */
public record JavanSettings(
    Optional<String> defaultToolchain,
    Optional<String> defaultJdk,
    boolean autoInstall
) {
    public static final String DEFAULT_JDK = "25";
    /**
     * Creates validated settings.
     */
    public JavanSettings {
        defaultToolchain = Objects.requireNonNull(defaultToolchain, "defaultToolchain");
        defaultJdk = Objects.requireNonNull(defaultJdk, "defaultJdk");
    }

    /**
     * Returns the deterministic settings defaults.
     *
     * @return default settings
     */
    public static JavanSettings defaults() {
        return new JavanSettings(Optional.empty(), Optional.of(DEFAULT_JDK), false);
    }

    /**
     * Reads settings from a Javan home, returning defaults when no file exists.
     *
     * @param home Javan home
     * @return configured or default settings
     * @throws IOException when settings cannot be read
     */
    static JavanSettings read(final Path home) throws IOException {
        final Path file = Objects.requireNonNull(home, "home").resolve("settings.toml");
        return Files.isRegularFile(file) ? parse(Files.readString(file)) : defaults();
    }

    static JavanSettings parse(final String content) {
        final Map<String, String> values = SimpleToml.parse(Objects.requireNonNull(content, "content"));
        return new JavanSettings(
            text(values, "default_toolchain", "defaults.toolchain"),
            text(values, "default_jdk", "defaults.jdk"),
            bool(values, "auto_install", "defaults.auto_install")
        );
    }

    private static Optional<String> text(final Map<String, String> values, final String primary, final String fallback) {
        final String value = values.containsKey(primary) ? values.get(primary) : values.get(fallback);
        return Strings2.isBlank(value) ? Optional.empty() : Optional.of(value);
    }

    private static boolean bool(final Map<String, String> values, final String primary, final String fallback) {
        final String key = values.containsKey(primary) ? primary : fallback;
        final String value = values.get(key);
        if (Strings2.isBlank(value)) {
            return false;
        }
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Expected boolean for " + key + ": " + value);
    }

    /**
     * Returns the configured default JDK selector.
     *
     * @return provider/version selector used by {@code javan install}
     */
    public String defaultJdkSelector() {
        return defaultJdk.orElse(DEFAULT_JDK);
    }
}
