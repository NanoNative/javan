package javan.toolchain;

import javan.util.Strings2;

import java.util.ArrayList;
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
        final List<ToolchainMetadata> sorted = sorted(toolchains);

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
        return Strings2.stripTrailingAscii(report.toString());
    }

    private static List<ToolchainMetadata> sorted(final List<ToolchainMetadata> toolchains) {
        final List<ToolchainMetadata> result = new ArrayList<>();
        for (final ToolchainMetadata metadata : toolchains) {
            int index = 0;
            while (index < result.size() && compare(result.get(index), metadata) <= 0) {
                index++;
            }
            result.add(index, metadata);
        }
        return List.copyOf(result);
    }

    private static int compare(final ToolchainMetadata left, final ToolchainMetadata right) {
        int result = Strings2.compareAscii(left.id(), right.id());
        if (result != 0) {
            return result;
        }
        result = Strings2.compareAscii(left.kind().value(), right.kind().value());
        if (result != 0) {
            return result;
        }
        result = Strings2.compareAscii(left.version(), right.version());
        if (result != 0) {
            return result;
        }
        return Strings2.compareAscii(left.home().toString(), right.home().toString());
    }
}
