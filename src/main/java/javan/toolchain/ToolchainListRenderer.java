package javan.toolchain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Renders installed toolchains deterministically.
 */
public final class ToolchainListRenderer {
    /**
     * Renders installed toolchains.
     *
     * @param toolchains installed toolchains
     * @return human-readable toolchain list
     */
    public String render(final List<ToolchainMetadata> toolchains) {
        Objects.requireNonNull(toolchains, "toolchains");
        final List<ToolchainMetadata> sorted = toolchains.stream()
            .sorted(Comparator
                .comparing(ToolchainMetadata::id)
                .thenComparing(metadata -> metadata.kind().value())
                .thenComparing(ToolchainMetadata::version)
                .thenComparing(metadata -> metadata.home().toString()))
            .toList();

        final StringBuilder report = new StringBuilder();
        report.append("Toolchains").append(System.lineSeparator());
        if (sorted.isEmpty()) {
            report.append("  (none)");
            return report.toString();
        }
        for (final ToolchainMetadata metadata : sorted) {
            report.append("  ")
                .append(metadata.id())
                .append(" | ")
                .append(metadata.kind().value())
                .append(" | ")
                .append(metadata.version())
                .append(" | ")
                .append(metadata.javacExecutable())
                .append(System.lineSeparator());
        }
        return report.toString().stripTrailing();
    }
}
