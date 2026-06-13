package javan.build;

import javan.detect.ProjectLayout;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies classpath resources into deterministic javan output folders and reports them.
 */
public final class ResourceBundler {
    /**
     * Copies resources from class folders to .javan/resources and .javan/dist/resources.
     *
     * @param layout project layout
     * @return copied resources
     * @throws IOException when resources cannot be copied
     */
    public List<ResourceFile> bundle(final ProjectLayout layout) throws IOException {
        final List<ResourceFile> resources = collect(layout);
        copy(resources, layout.outputDirectory().resolve("resources"));
        copy(resources, layout.outputDirectory().resolve("dist/resources"));
        writeReports(layout, resources);
        return resources;
    }

    /**
     * Finds non-class resources in class folders.
     *
     * @param layout project layout
     * @return resources
     * @throws IOException when scanning fails
     */
    public List<ResourceFile> collect(final ProjectLayout layout) throws IOException {
        final LinkedHashMap<String, ResourceFile> result = new LinkedHashMap<>();
        for (final Path classFolder : layout.classFolders()) {
            if (!Files.isDirectory(classFolder)) {
                continue;
            }
            for (final Path file : Files2.findFiles(classFolder, ResourceBundler::resourceFile)) {
                final String path = classFolder.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                result.putIfAbsent(path, new ResourceFile(path, file, Files.size(file)));
            }
        }
        return result.values().stream().sorted(java.util.Comparator.comparing(ResourceFile::path)).toList();
    }

    private static void copy(final List<ResourceFile> resources, final Path targetRoot) throws IOException {
        Files2.deleteRecursive(targetRoot);
        for (final ResourceFile resource : resources) {
            final Path target = targetRoot.resolve(resource.path()).normalize();
            if (!target.startsWith(targetRoot)) {
                throw new IOException("Resource path escapes output directory: " + resource.path());
            }
            Files.createDirectories(target.getParent());
            Files.copy(resource.source(), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeReports(final ProjectLayout layout, final List<ResourceFile> resources) throws IOException {
        final String json = "{\n"
            + "  \"resourceCount\": " + resources.size() + ",\n"
            + "  \"resources\": [\n"
            + String.join(",\n", resources.stream()
                .map(resource -> "    {\"path\": " + Json.string(resource.path())
                    + ", \"size\": " + resource.size() + "}")
                .toList())
            + "\n  ]\n"
            + "}\n";
        Files2.writeString(layout.outputDirectory().resolve("reports/resources.json"), json);

        final StringBuilder markdown = new StringBuilder();
        markdown.append("# Resources").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("Resource files copied: ").append(resources.size()).append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("| path | bytes |").append(System.lineSeparator());
        markdown.append("| --- | ---: |").append(System.lineSeparator());
        for (final ResourceFile resource : resources) {
            markdown.append("| `").append(resource.path()).append("` | ").append(resource.size()).append(" |").append(System.lineSeparator());
        }
        Files2.writeString(layout.outputDirectory().resolve("reports/resources.md"), markdown.toString());
    }

    private static boolean resourceFile(final Path file) {
        final String name = file.getFileName().toString();
        return !name.endsWith(".class") && !name.equals("module-info.class");
    }

    /**
     * Resource copied from classpath output.
     *
     * @param path classpath-relative resource path
     * @param source source file
     * @param size byte size
     */
    public record ResourceFile(String path, Path source, long size) {
    }
}
