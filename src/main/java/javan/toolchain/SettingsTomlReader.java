package javan.toolchain;

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
            text(values, "default_toolchain").or(() -> text(values, "defaults.toolchain")),
            text(values, "default_jdk").or(() -> text(values, "defaults.jdk")),
            bool(values, "auto_install").or(() -> bool(values, "defaults.auto_install")).orElse(false)
        );
    }

    private static Optional<String> text(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Optional<Boolean> bool(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
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
