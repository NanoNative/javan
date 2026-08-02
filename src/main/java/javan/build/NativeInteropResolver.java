package javan.build;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.reporting.RuntimeFootprintReports;
import javan.toolchain.SimpleToml;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves canonical native configuration from {@code javan.toml}.
 */
public final class NativeInteropResolver {
    private static final Set<String> KEYS = Set.of(
        "imports", "sources", "objects", "library-search-paths", "libraries", "frameworks"
    );

    /**
     * Resolves native configuration for the host target.
     *
     * @param classes parsed project and dependency classes
     * @param root project root
     * @return resolved configuration
     * @throws IOException when configuration or configured paths cannot be read
     * @throws NullPointerException when an argument is null
     * @throws IllegalArgumentException when native configuration is invalid
     */
    public NativeInteropConfig resolve(final Map<String, ClassFile> classes, final Path root) throws IOException {
        return resolve(classes, root, RuntimeFootprintReports.hostTarget());
    }

    /**
     * Resolves native configuration for a target overlay.
     *
     * @param classes parsed project and dependency classes
     * @param root project root
     * @param target requested target
     * @return resolved configuration
     * @throws IOException when configuration or configured paths cannot be read
     * @throws NullPointerException when an argument is null
     * @throws IllegalArgumentException when native configuration is invalid
     */
    public NativeInteropConfig resolve(
        final Map<String, ClassFile> classes,
        final Path root,
        final String target
    ) throws IOException {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(target, "target");
        final String normalizedTarget = RuntimeFootprintReports.normalizeTarget(target);
        if (Strings2.isBlank(normalizedTarget)) {
            throw new IllegalArgumentException("Native target must not be blank.");
        }
        final Path projectRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Native project root is not a directory: " + root);
        }
        final Path configuration = projectRoot.resolve("javan.toml");
        if (!Files.exists(configuration, LinkOption.NOFOLLOW_LINKS)) {
            return NativeInteropConfig.empty();
        }
        if (!Files.isRegularFile(configuration, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Native configuration is not a regular file: javan.toml");
        }
        final Map<String, String> values = SimpleToml.parse(Files2.readStringIfExists(configuration));
        validateKeys(values);
        return new NativeInteropConfig(
            imports(classes, values, normalizedTarget),
            linkInputs(projectRoot, values, normalizedTarget)
        );
    }

    private static List<NativeInteropConfig.ImportBinding> imports(
        final Map<String, ClassFile> classes,
        final Map<String, String> values,
        final String target
    ) {
        final List<NativeInteropConfig.ImportBinding> result = new ArrayList<>();
        for (final String declaration : selectedValues(values, target, "imports")) {
            result.add(parseImportBinding(classes, declaration));
        }
        return List.copyOf(result);
    }

    private static NativeLinkInputs linkInputs(final Path root, final Map<String, String> values, final String target) {
        return new NativeLinkInputs(
            resolveFiles(root, selectedValues(values, target, "sources"), "native source", ".c", ".m"),
            resolveFiles(root, selectedValues(values, target, "objects"), "native object", ".o", ".obj"),
            resolveDirectories(root, selectedValues(values, target, "library-search-paths")),
            selectedValues(values, target, "libraries"),
            selectedValues(values, target, "frameworks")
        );
    }

    private static void validateKeys(final Map<String, String> values) {
        for (final String key : values.keySet()) {
            if (key.startsWith("build.native.")) {
                throw new IllegalArgumentException("Legacy native configuration is not supported: " + key);
            }
            if (key.startsWith("native.") && !isKnownNativeKey(key)) {
                throw new IllegalArgumentException("Unknown native configuration key: " + key);
            }
        }
    }

    private static boolean isKnownNativeKey(final String fullKey) {
        final String suffix = fullKey.substring("native.".length());
        if (KEYS.contains(suffix)) {
            return true;
        }
        if (!suffix.startsWith("target.")) {
            return false;
        }
        final String targetAndKey = suffix.substring("target.".length());
        final int keyStart = targetAndKey.lastIndexOf('.');
        if (keyStart < 1) {
            return false;
        }
        final String targetId = targetAndKey.substring(0, keyStart);
        if (!canonicalTargetId(targetId)) {
            throw new IllegalArgumentException("Invalid native target section id: " + targetId);
        }
        return KEYS.contains(targetAndKey.substring(keyStart + 1));
    }

    private static List<String> selectedValues(final Map<String, String> values, final String target, final String key) {
        final List<String> result = new ArrayList<>();
        final String commonKey = "native." + key;
        result.addAll(rawList(values.get(commonKey), commonKey));
        final String platform = targetPlatform(target);
        if (!platform.equals(target)) {
            final String platformKey = "native.target." + platform + "." + key;
            result.addAll(rawList(values.get(platformKey), platformKey));
        }
        final String targetKey = "native.target." + target + "." + key;
        result.addAll(rawList(values.get(targetKey), targetKey));
        return List.copyOf(result);
    }

    private static String targetPlatform(final String target) {
        final int separator = target.indexOf('-');
        return separator < 1 ? target : target.substring(0, separator);
    }

    private static NativeInteropConfig.ImportBinding parseImportBinding(
        final Map<String, ClassFile> classes,
        final String declaration
    ) {
        final int arrow = declaration.indexOf("->");
        if (arrow < 1 || declaration.indexOf("->", arrow + 2) >= 0) {
            throw invalidImport(declaration);
        }
        final String methodDeclaration = Strings2.trimAscii(declaration.substring(0, arrow));
        final String externalSymbol = Strings2.trimAscii(declaration.substring(arrow + 2));
        final EntryPoint entryPoint = parseEntryPoint(methodDeclaration);
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            throw new IllegalArgumentException("Native import class not found: " + displayClassName(entryPoint.className()));
        }
        final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElse(null);
        if (method == null) {
            throw new IllegalArgumentException("Native import method not found: " + methodDeclaration);
        }
        if (!method.isNative()) {
            throw new IllegalArgumentException("Declared native import is not native: " + methodDeclaration);
        }
        return new NativeInteropConfig.ImportBinding(entryPoint, externalSymbol);
    }

    private static EntryPoint parseEntryPoint(final String declaration) {
        final int open = declaration.indexOf('(');
        final int close = declaration.indexOf(')', open + 1);
        final int colon = declaration.indexOf(':', close + 1);
        if (open < 1 || close < open || colon != close + 1 || colon == declaration.length() - 1) {
            throw invalidImport(declaration);
        }
        final int dot = declaration.lastIndexOf('.', open);
        if (dot < 1 || dot == open - 1) {
            throw invalidImport(declaration);
        }
        final String owner = declaration.substring(0, dot).replace('.', '/');
        final String method = declaration.substring(dot + 1, open);
        if (Strings2.isBlank(method)) {
            throw invalidImport(declaration);
        }
        return new EntryPoint(owner, method, descriptor(types(declaration.substring(open + 1, close)), declaration.substring(colon + 1)));
    }

    private static IllegalArgumentException invalidImport(final String declaration) {
        return new IllegalArgumentException("Invalid native import declaration: " + declaration);
    }

    private static List<String> types(final String declaration) {
        if (Strings2.isBlank(declaration)) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= declaration.length(); index++) {
            if (index < declaration.length() && declaration.charAt(index) != ',') {
                continue;
            }
            final String type = Strings2.trimAscii(declaration.substring(start, index));
            if (Strings2.isBlank(type)) {
                throw invalidImport(declaration);
            }
            result.add(type);
            start = index + 1;
        }
        return List.copyOf(result);
    }

    private static String descriptor(final List<String> parameters, final String returnType) {
        final StringBuilder descriptor = new StringBuilder("(");
        for (final String parameter : parameters) {
            descriptor.append(descriptorType(parameter));
        }
        return descriptor.append(')').append(descriptorType(returnType)).toString();
    }

    private static String descriptorType(final String declaredType) {
        final String type = Strings2.trimAscii(declaredType);
        return switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            case "String", "java.lang.String" -> "Ljava/lang/String;";
            default -> arrayOrObjectDescriptor(type, declaredType);
        };
    }

    private static String arrayOrObjectDescriptor(final String type, final String declaredType) {
        if (type.endsWith("[]")) {
            return "[" + descriptorType(type.substring(0, type.length() - 2));
        }
        if (type.indexOf('.') > 0) {
            return "L" + type.replace('.', '/') + ";";
        }
        throw new IllegalArgumentException("Unsupported native import declaration type: " + declaredType);
    }

    private static List<Path> resolveFiles(
        final Path root,
        final List<String> values,
        final String label,
        final String firstExtension,
        final String secondExtension
    ) {
        final List<Path> result = new ArrayList<>();
        for (final String value : values) {
            final Path path = resolvePath(root, value, label, false);
            final String name = path.getFileName().toString();
            if (!name.endsWith(firstExtension) && !name.endsWith(secondExtension)) {
                throw new IllegalArgumentException("Unsupported " + label + " extension: " + value);
            }
            result.add(path);
        }
        return List.copyOf(result);
    }

    private static List<Path> resolveDirectories(final Path root, final List<String> values) {
        final List<Path> result = new ArrayList<>();
        for (final String value : values) {
            result.add(resolvePath(root, value, "native library search path", true));
        }
        return List.copyOf(result);
    }

    private static Path resolvePath(final Path root, final String value, final String label, final boolean directory) {
        if (Strings2.isBlank(value)) {
            throw new IllegalArgumentException("Blank " + label + " entry");
        }
        final Path relative = Path.of(value);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException(capitalize(label) + " must be relative: " + value);
        }
        for (int index = 0; index < relative.getNameCount(); index++) {
            if ("..".equals(relative.getName(index).toString())) {
                throw new IllegalArgumentException(capitalize(label) + " must not contain parent traversal: " + value);
            }
        }
        final Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(capitalize(label) + " escapes project root: " + value);
        }
        requireDirectoryParents(root, relative, label, value);
        if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(capitalize(label) + " does not exist: " + value);
        }
        if (directory && !Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(capitalize(label) + " is not a directory: " + value);
        }
        if (!directory && !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(capitalize(label) + " is not a regular file: " + value);
        }
        return resolved;
    }

    private static void requireDirectoryParents(
        final Path root,
        final Path relative,
        final String label,
        final String value
    ) {
        Path parent = root;
        for (int index = 0; index < relative.getNameCount() - 1; index++) {
            parent = parent.resolve(relative.getName(index)).normalize();
            if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(capitalize(label) + " does not exist: " + value);
            }
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(capitalize(label) + " parent is not a directory: " + value);
            }
        }
    }

    private static List<String> rawList(final String raw, final String key) {
        if (raw == null) {
            return List.of();
        }
        final String value = Strings2.trimAscii(raw);
        if (!value.startsWith("[") || !value.endsWith("]")) {
            throw invalidStringArray(key);
        }
        final List<String> result = new ArrayList<>();
        final int end = value.length() - 1;
        int index = skipListWhitespace(value, 1, end);
        if (index == end) {
            return List.of();
        }
        while (index < end) {
            if (value.charAt(index) != '"') {
                throw invalidStringArray(key);
            }
            final int itemStart = ++index;
            boolean escaped = false;
            while (index < end) {
                final char character = value.charAt(index);
                if (character == '\n' || character == '\r') {
                    throw invalidStringArray(key);
                }
                if (character == '"' && !escaped) {
                    break;
                }
                escaped = character == '\\' && !escaped;
                index++;
            }
            if (index == end) {
                throw invalidStringArray(key);
            }
            result.add(value.substring(itemStart, index));
            index = skipListWhitespace(value, index + 1, end);
            if (index == end) {
                break;
            }
            if (value.charAt(index) != ',') {
                throw invalidStringArray(key);
            }
            index = skipListWhitespace(value, index + 1, end);
            if (index == end) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static int skipListWhitespace(final String value, final int start, final int end) {
        int index = start;
        while (index < end && listWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean listWhitespace(final char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r';
    }

    private static IllegalArgumentException invalidStringArray(final String key) {
        return new IllegalArgumentException("Native configuration value must be an array of quoted strings: " + key);
    }

    private static boolean validTargetId(final String value) {
        if (value.isEmpty() || value.charAt(0) == '-' || value.charAt(value.length() - 1) == '-') {
            return false;
        }
        boolean previousHyphen = false;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                previousHyphen = false;
                continue;
            }
            if (character != '-' || previousHyphen) {
                return false;
            }
            previousHyphen = true;
        }
        return true;
    }

    private static boolean canonicalTargetId(final String value) {
        if (!validTargetId(value) || !RuntimeFootprintReports.normalizeTarget(value).equals(value)) {
            return false;
        }
        final String projected = value + "-x64";
        final String normalizedProjection = RuntimeFootprintReports.normalizeTarget(projected);
        return normalizedProjection.equals(projected) || normalizedProjection.equals(value);
    }

    private static String displayClassName(final String internalName) {
        return internalName.replace('/', '.');
    }

    private static String capitalize(final String value) {
        if (value.isEmpty()) {
            return value;
        }
        final char first = value.charAt(0);
        return first >= 'a' && first <= 'z' ? (char) (first - 32) + value.substring(1) : value;
    }
}
