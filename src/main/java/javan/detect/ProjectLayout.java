package javan.detect;

import java.nio.file.Path;
import java.util.List;

/**
 * Detected project layout and build defaults.
 *
 * @param root project root used for generated output
 * @param input input path supplied by the user
 * @param inputKind detected input kind
 * @param buildTool detected build tool
 * @param sourceFolders Java source folders
 * @param resourceFolders Java resource folders
 * @param classFolders application class folders
 * @param classpathEntries extra classpath entries
 * @param outputDirectory internal output directory
 * @param outputName native binary name
 * @param warnings non-fatal detection warnings
 */
public record ProjectLayout(
    Path root,
    Path input,
    InputKind inputKind,
    BuildTool buildTool,
    List<Path> sourceFolders,
    List<Path> resourceFolders,
    List<Path> classFolders,
    List<Path> classpathEntries,
    Path outputDirectory,
    String outputName,
    List<String> warnings
) {
    /**
     * Returns a copy with updated class folders and classpath entries.
     *
     * @param newClassFolders class folders
     * @param newClasspathEntries classpath entries
     * @param newWarnings additional warnings
     * @return updated layout
     */
    public ProjectLayout withClasspath(
        final List<Path> newClassFolders,
        final List<Path> newClasspathEntries,
        final List<String> newWarnings
    ) {
        return new ProjectLayout(
            root,
            input,
            inputKind,
            buildTool,
            sourceFolders,
            resourceFolders,
            List.copyOf(newClassFolders),
            List.copyOf(newClasspathEntries),
            outputDirectory,
            outputName,
            append(warnings, newWarnings)
        );
    }

    private static List<String> append(final List<String> first, final List<String> second) {
        if (second.isEmpty()) {
            return first;
        }
        final java.util.ArrayList<String> result = new java.util.ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }
}
