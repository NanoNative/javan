package javan.build;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves library exports from CLI and {@code javan.toml}.
 */
public final class ExportResolver {
    private static final Pattern CONFIG_METHODS = Pattern.compile("methods\\s*=\\s*\\[(?<values>[^]]*)]", Pattern.DOTALL);
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    /**
     * Resolves export declarations.
     *
     * @param classes parsed classes
     * @param root project root
     * @param cliExports CLI export declarations
     * @return resolved methods
     * @throws IOException when config reading fails
     */
    public List<ExportedMethod> resolve(
        final Map<String, ClassFile> classes,
        final Path root,
        final List<String> cliExports
    ) throws IOException {
        final List<String> declarations = new ArrayList<>();
        declarations.addAll(cliExports);
        declarations.addAll(configExports(root));
        if (declarations.isEmpty()) {
            throw new IllegalArgumentException("Library builds require at least one --export or [exports].methods entry in javan.toml");
        }
        final List<ExportedMethod> result = new ArrayList<>();
        final Set<EntryPoint> seen = new LinkedHashSet<>();
        for (final String declaration : declarations) {
            final ExportedMethod method = resolveOne(classes, declaration.trim());
            if (seen.add(method.entryPoint())) {
                result.add(method);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> configExports(final Path root) throws IOException {
        final Path config = root.resolve("javan.toml");
        if (!Files.exists(config)) {
            return List.of();
        }
        final String text = Files2.readStringIfExists(config);
        final Matcher methods = CONFIG_METHODS.matcher(text);
        if (!methods.find()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        final Matcher quoted = QUOTED.matcher(methods.group("values"));
        while (quoted.find()) {
            result.add(quoted.group(1));
        }
        return List.copyOf(result);
    }

    private static ExportedMethod resolveOne(final Map<String, ClassFile> classes, final String declaration) {
        if (declaration.isBlank()) {
            throw new IllegalArgumentException("Blank export declaration");
        }
        if (declaration.contains("(")) {
            final ParsedDeclaration parsed = ParsedDeclaration.parse(declaration);
            final ClassFile classFile = classFile(classes, parsed.owner());
            final MethodInfo method = classFile.method(parsed.methodName(), parsed.descriptor())
                .orElseThrow(() -> new IllegalArgumentException("Export method not found: " + declaration));
            return validate(classFile, method, parsed.parameterTypes(), parsed.returnType());
        }
        final int dot = declaration.lastIndexOf('.');
        if (dot < 1 || dot == declaration.length() - 1) {
            throw new IllegalArgumentException("Export must look like com.acme.Type.method or com.acme.Type.method(int):int");
        }
        final String owner = declaration.substring(0, dot).replace('.', '/');
        final String methodName = declaration.substring(dot + 1);
        final ClassFile classFile = classFile(classes, owner);
        final List<MethodInfo> matches = classFile.methods().stream()
            .filter(method -> method.name().equals(methodName))
            .filter(method -> !method.name().startsWith("<"))
            .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Export method not found: " + declaration);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous export " + declaration + "; use com.acme.Type.method(int):int form");
        }
        final MethodInfo method = matches.getFirst();
        final ParsedDescriptor descriptor = ParsedDescriptor.parse(method.descriptor());
        return validate(classFile, method, descriptor.parameterTypes(), descriptor.returnType());
    }

    private static ClassFile classFile(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile classFile = classes.get(owner);
        if (classFile == null) {
            throw new IllegalArgumentException("Export class not found: " + owner.replace('/', '.'));
        }
        return classFile;
    }

    private static ExportedMethod validate(
        final ClassFile classFile,
        final MethodInfo method,
        final List<AbiType> parameterTypes,
        final AbiType returnType
    ) {
        if (!method.isStatic()) {
            throw new IllegalArgumentException("Unsupported export " + classFile.name().replace('/', '.') + "." + method.name()
                + method.descriptor() + ": exported methods must be static");
        }
        if (method.isNative() || method.code().isEmpty()) {
            throw new IllegalArgumentException("Unsupported export " + classFile.name().replace('/', '.') + "." + method.name()
                + method.descriptor() + ": method must have Java bytecode");
        }
        final EntryPoint entryPoint = new EntryPoint(classFile.name(), method.name(), method.descriptor());
        return new ExportedMethod(entryPoint, symbol(classFile.name(), method.name(), parameterTypes), parameterTypes, returnType);
    }

    private static String symbol(final String owner, final String methodName, final List<AbiType> parameters) {
        final String suffix = parameters.isEmpty()
            ? "void"
            : String.join("_", parameters.stream().map(AbiType::suffix).toList());
        return "javan_export_" + sanitize(owner) + "_" + sanitize(methodName) + "_" + suffix;
    }

    private static String sanitize(final String value) {
        return value
            .replace('/', '_')
            .replace('.', '_')
            .replace('$', '_')
            .replace('-', '_');
    }

    private record ParsedDeclaration(String owner, String methodName, String descriptor, List<AbiType> parameterTypes, AbiType returnType) {
        static ParsedDeclaration parse(final String declaration) {
            final int open = declaration.indexOf('(');
            final int close = declaration.indexOf(')', open);
            final int colon = declaration.indexOf(':', close);
            if (open < 1 || close < open || colon != close + 1 || colon == declaration.length() - 1) {
                throw new IllegalArgumentException("Invalid export declaration: " + declaration);
            }
            final int dot = declaration.lastIndexOf('.', open);
            if (dot < 1) {
                throw new IllegalArgumentException("Invalid export declaration: " + declaration);
            }
            final String owner = declaration.substring(0, dot).replace('.', '/');
            final String methodName = declaration.substring(dot + 1, open);
            final List<AbiType> parameters = parseTypes(declaration.substring(open + 1, close));
            final AbiType returnType = parseType(declaration.substring(colon + 1).trim(), true);
            return new ParsedDeclaration(owner, methodName, ExportResolver.descriptor(parameters, returnType), parameters, returnType);
        }
    }

    private record ParsedDescriptor(List<AbiType> parameterTypes, AbiType returnType) {
        static ParsedDescriptor parse(final String descriptor) {
            final List<AbiType> parameters = new ArrayList<>();
            int index = 1;
            while (descriptor.charAt(index) != ')') {
                final TypeRead read = readDescriptorType(descriptor, index);
                parameters.add(read.type());
                index = read.nextIndex();
            }
            final TypeRead returnType = readDescriptorType(descriptor, index + 1);
            return new ParsedDescriptor(List.copyOf(parameters), returnType.type());
        }
    }

    private record TypeRead(AbiType type, int nextIndex) {
    }

    private static TypeRead readDescriptorType(final String descriptor, final int index) {
        final char type = descriptor.charAt(index);
        return switch (type) {
            case 'V' -> new TypeRead(AbiType.VOID, index + 1);
            case 'B', 'C', 'I', 'S', 'Z' -> new TypeRead(AbiType.INT, index + 1);
            case 'J' -> new TypeRead(AbiType.LONG, index + 1);
            case 'F' -> new TypeRead(AbiType.FLOAT, index + 1);
            case 'D' -> new TypeRead(AbiType.DOUBLE, index + 1);
            case 'L' -> readObjectDescriptor(descriptor, index);
            case '[' -> readArrayDescriptor(descriptor, index);
            default -> throw new IllegalArgumentException("Unsupported export descriptor: " + descriptor);
        };
    }

    private static TypeRead readObjectDescriptor(final String descriptor, final int index) {
        final int end = descriptor.indexOf(';', index);
        if (end < 0) {
            throw new IllegalArgumentException("Unsupported export descriptor: " + descriptor);
        }
        final String type = descriptor.substring(index + 1, end);
        if ("java/lang/String".equals(type)) {
            return new TypeRead(AbiType.STRING, end + 1);
        }
        throw new IllegalArgumentException("Unsupported export object type: " + type.replace('/', '.'));
    }

    private static TypeRead readArrayDescriptor(final String descriptor, final int index) {
        if (index + 1 < descriptor.length() && descriptor.charAt(index + 1) == 'B') {
            return new TypeRead(AbiType.BYTE_ARRAY, index + 2);
        }
        throw new IllegalArgumentException("Unsupported export array descriptor: " + descriptor);
    }

    private static List<AbiType> parseTypes(final String value) {
        if (value.isBlank()) {
            return List.of();
        }
        final List<AbiType> result = new ArrayList<>();
        for (final String part : value.split(",")) {
            result.add(parseType(part.trim(), false));
        }
        return List.copyOf(result);
    }

    private static AbiType parseType(final String value, final boolean returnPosition) {
        return switch (value) {
            case "void" -> returnPosition ? AbiType.VOID : unsupported(value);
            case "byte", "char", "short", "int", "boolean" -> AbiType.INT;
            case "long" -> AbiType.LONG;
            case "float" -> AbiType.FLOAT;
            case "double" -> AbiType.DOUBLE;
            case "String", "java.lang.String" -> AbiType.STRING;
            case "byte[]" -> AbiType.BYTE_ARRAY;
            default -> throw new IllegalArgumentException("Unsupported export type: " + value);
        };
    }

    private static AbiType unsupported(final String value) {
        throw new IllegalArgumentException("Unsupported export parameter type: " + value);
    }

    private static String descriptor(final List<AbiType> parameters, final AbiType returnType) {
        return "(" + String.join("", parameters.stream().map(ExportResolver::descriptor).toList()) + ")" + descriptor(returnType);
    }

    private static String descriptor(final AbiType type) {
        return switch (type) {
            case VOID -> "V";
            case INT -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case STRING -> "Ljava/lang/String;";
            case BYTE_ARRAY -> "[B";
        };
    }
}
