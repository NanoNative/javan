package javan.toolchain;

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
        return new JavanSettings(Optional.empty(), Optional.empty(), false);
    }
}
