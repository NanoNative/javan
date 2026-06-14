package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads global settings.toml with a small deterministic parser.
 */
public final class SettingsTomlReader {
    /**
     * Reads settings from the given javan home.
     *
     * @param home javan home
     * @return settings or defaults when settings.toml is missing
     * @throws IOException when the settings file cannot be read
     */
    public JavanSettings read(final Path home) throws IOException {
        Objects.requireNonNull(home, "home");
        final Path settingsFile = home.resolve("settings.toml");
        if (!Files.isRegularFile(settingsFile)) {
            return JavanSettings.defaults();
        }
        return parse(Files.readString(settingsFile));
    }

    /**
     * Parses settings.toml content.
     *
     * @param content settings content
     * @return parsed settings
     */
    public JavanSettings parse(final String content) {
        Objects.requireNonNull(content, "content");
        final Map<String, String> values = SimpleToml.parse(content);
        return new JavanSettings(
            firstText(values, "default_toolchain", "defaults.toolchain"),
            firstText(values, "default_jdk", "defaults.jdk"),
            firstBool(values, "auto_install", "defaults.auto_install")
        );
    }

    private static Optional<String> firstText(
        final Map<String, String> values,
        final String primary,
        final String fallback
    ) {
        final Optional<String> value = text(values, primary);
        if (value.isPresent()) {
            return value;
        }
        return text(values, fallback);
    }

    private static boolean firstBool(final Map<String, String> values, final String primary, final String fallback) {
        Optional<Boolean> value = bool(values, primary);
        if (value.isPresent()) {
            return value.get();
        }
        value = bool(values, fallback);
        if (value.isPresent()) {
            return value.get();
        }
        return false;
    }

    private static Optional<String> text(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Optional<Boolean> bool(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        if ("true".equals(value)) {
            return Optional.of(true);
        }
        if ("false".equals(value)) {
            return Optional.of(false);
        }
        throw new IllegalArgumentException("Expected boolean for " + key + ": " + value);
    }
}
