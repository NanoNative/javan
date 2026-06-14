package javan.build;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves library exports from CLI and {@code javan.toml}.
 */
public final class ExportResolver {
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
        final List<EntryPoint> seen = new ArrayList<>();
        for (final String declaration : declarations) {
            final ExportedMethod method = resolveOne(classes, Strings2.trimAscii(declaration));
            if (!containsEntry(seen, method.entryPoint())) {
                seen.add(method.entryPoint());
                result.add(method);
            }
        }
        return List.copyOf(result);
    }

    private static boolean containsEntry(final List<EntryPoint> entries, final EntryPoint target) {
        for (final EntryPoint entry : entries) {
            if (entry.className().equals(target.className())
                && entry.methodName().equals(target.methodName())
                && entry.descriptor().equals(target.descriptor())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> configExports(final Path root) throws IOException {
        final Path config = root.resolve("javan.toml");
        if (!Files.exists(config)) {
            return List.of();
        }
        final String text = Files2.readStringIfExists(config);
        final int start = methodsArrayStart(text);
        if (start < 0) {
            return List.of();
        }
        final int end = methodsArrayEnd(text, start);
        final List<String> result = new ArrayList<>();
        collectQuoted(text, start + 1, end, result);
        return List.copyOf(result);
    }

    private static int methodsArrayStart(final String text) {
        int index = 0;
        while (index >= 0 && index < text.length()) {
            index = text.indexOf("methods", index);
            if (index < 0) {
                return -1;
            }
            final int afterName = index + "methods".length();
            int cursor = skipWhitespace(text, afterName);
            if (cursor < text.length() && text.charAt(cursor) == '=') {
                cursor = skipWhitespace(text, cursor + 1);
                if (cursor < text.length() && text.charAt(cursor) == '[') {
                    return cursor;
                }
            }
            index = afterName;
        }
        return -1;
    }

    private static int methodsArrayEnd(final String text, final int start) {
        final int end = text.indexOf(']', start + 1);
        if (end < 0) {
            throw new IllegalArgumentException("Invalid [exports].methods in javan.toml");
        }
        return end;
    }

    private static int skipWhitespace(final String text, final int start) {
        int cursor = start;
        while (cursor < text.length()) {
            final char value = text.charAt(cursor);
            if (value != ' ' && value != '\t' && value != '\n' && value != '\r') {
                return cursor;
            }
            cursor++;
        }
        return cursor;
    }

    private static void collectQuoted(final String text, final int start, final int end, final List<String> result) {
        int cursor = start;
        while (cursor < end) {
            if (text.charAt(cursor) != '"') {
                cursor++;
                continue;
            }
            final int close = text.indexOf('"', cursor + 1);
            if (close < 0 || close > end) {
                throw new IllegalArgumentException("Invalid quoted export in javan.toml");
            }
            result.add(text.substring(cursor + 1, close));
            cursor = close + 1;
        }
    }

    private static ExportedMethod resolveOne(final Map<String, ClassFile> classes, final String declaration) {
        if (Strings2.isBlank(declaration)) {
            throw new IllegalArgumentException("Blank export declaration");
        }
        if (declaration.contains("(")) {
            final ParsedDeclaration parsed = ParsedDeclaration.parse(declaration);
            final ClassFile classFile = classFile(classes, parsed.owner());
            final MethodInfo method = classFile.method(parsed.methodName(), parsed.descriptor()).orElse(null);
            if (method == null) {
                throw new IllegalArgumentException("Export method not found: " + declaration);
            }
            return validate(classFile, method, parsed.parameterTypes(), parsed.returnType());
        }
        final int dot = declaration.lastIndexOf('.');
        if (dot < 1 || dot == declaration.length() - 1) {
            throw new IllegalArgumentException("Export must look like com.acme.Type.method or com.acme.Type.method(int):int");
        }
        final String owner = Strings2.replaceChar(declaration.substring(0, dot), '.', '/');
        final String methodName = declaration.substring(dot + 1);
        final ClassFile classFile = classFile(classes, owner);
        final List<MethodInfo> matches = new ArrayList<>();
        for (final MethodInfo method : classFile.methods()) {
            if (method.name().equals(methodName) && !method.name().startsWith("<")) {
                matches.add(method);
            }
        }
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
            throw new IllegalArgumentException("Export class not found: " + Strings2.replaceChar(owner, '/', '.'));
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
            throw new IllegalArgumentException("Unsupported export " + Strings2.replaceChar(classFile.name(), '/', '.') + "." + method.name()
                + method.descriptor() + ": exported methods must be static");
        }
        if (method.isNative() || method.code().isEmpty()) {
            throw new IllegalArgumentException("Unsupported export " + Strings2.replaceChar(classFile.name(), '/', '.') + "." + method.name()
                + method.descriptor() + ": method must have Java bytecode");
        }
        final EntryPoint entryPoint = new EntryPoint(classFile.name(), method.name(), method.descriptor());
        return new ExportedMethod(entryPoint, symbol(classFile.name(), method.name(), parameterTypes), parameterTypes, returnType);
    }

    private static String symbol(final String owner, final String methodName, final List<AbiType> parameters) {
        final String suffix = suffix(parameters);
        return "javan_export_" + sanitize(owner) + "_" + sanitize(methodName) + "_" + suffix;
    }

    private static String suffix(final List<AbiType> parameters) {
        if (parameters.isEmpty()) {
            return "void";
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < parameters.size(); index++) {
            if (index > 0) {
                result.append('_');
            }
            result.append(parameters.get(index).suffix());
        }
        return result.toString();
    }

    private static String sanitize(final String value) {
        return Strings2.replaceChar(
            Strings2.replaceChar(
                Strings2.replaceChar(
                    Strings2.replaceChar(value, '/', '_'),
                    '.', '_'
                ),
                '$', '_'
            ),
            '-', '_'
        );
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
            final String owner = Strings2.replaceChar(declaration.substring(0, dot), '.', '/');
            final String methodName = declaration.substring(dot + 1, open);
            final List<AbiType> parameters = parseTypes(declaration.substring(open + 1, close));
            final AbiType returnType = parseType(Strings2.trimAscii(declaration.substring(colon + 1)), true);
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
        if (type == 'V') {
            return new TypeRead(AbiType.VOID, index + 1);
        }
        if (type == 'B' || type == 'C' || type == 'I' || type == 'S' || type == 'Z') {
            return new TypeRead(AbiType.INT, index + 1);
        }
        if (type == 'J') {
            return new TypeRead(AbiType.LONG, index + 1);
        }
        if (type == 'F') {
            return new TypeRead(AbiType.FLOAT, index + 1);
        }
        if (type == 'D') {
            return new TypeRead(AbiType.DOUBLE, index + 1);
        }
        if (type == 'L') {
            return readObjectDescriptor(descriptor, index);
        }
        if (type == '[') {
            return readArrayDescriptor(descriptor, index);
        }
        throw new IllegalArgumentException("Unsupported export descriptor: " + descriptor);
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
        throw new IllegalArgumentException("Unsupported export object type: " + Strings2.replaceChar(type, '/', '.'));
    }

    private static TypeRead readArrayDescriptor(final String descriptor, final int index) {
        if (index + 1 < descriptor.length() && descriptor.charAt(index + 1) == 'B') {
            return new TypeRead(AbiType.BYTE_ARRAY, index + 2);
        }
        throw new IllegalArgumentException("Unsupported export array descriptor: " + descriptor);
    }

    private static List<AbiType> parseTypes(final String value) {
        if (Strings2.isBlank(value)) {
            return List.of();
        }
        final List<AbiType> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                result.add(parseType(Strings2.trimAscii(value.substring(start, index)), false));
                start = index + 1;
            }
        }
        return List.copyOf(result);
    }

    private static AbiType parseType(final String value, final boolean returnPosition) {
        if ("void".equals(value)) {
            return returnPosition ? AbiType.VOID : unsupported(value);
        }
        if ("byte".equals(value) || "char".equals(value) || "short".equals(value) || "int".equals(value)
            || "boolean".equals(value)) {
            return AbiType.INT;
        }
        if ("long".equals(value)) {
            return AbiType.LONG;
        }
        if ("float".equals(value)) {
            return AbiType.FLOAT;
        }
        if ("double".equals(value)) {
            return AbiType.DOUBLE;
        }
        if ("String".equals(value) || "java.lang.String".equals(value)) {
            return AbiType.STRING;
        }
        if ("byte[]".equals(value)) {
            return AbiType.BYTE_ARRAY;
        }
        throw new IllegalArgumentException("Unsupported export type: " + value);
    }

    private static AbiType unsupported(final String value) {
        throw new IllegalArgumentException("Unsupported export parameter type: " + value);
    }

    private static String descriptor(final List<AbiType> parameters, final AbiType returnType) {
        final StringBuilder result = new StringBuilder();
        result.append('(');
        for (final AbiType parameter : parameters) {
            result.append(descriptor(parameter));
        }
        result.append(')');
        result.append(descriptor(returnType));
        return result.toString();
    }

    private static String descriptor(final AbiType type) {
        if (type == AbiType.VOID) {
            return "V";
        }
        if (type == AbiType.INT) {
            return "I";
        }
        if (type == AbiType.LONG) {
            return "J";
        }
        if (type == AbiType.FLOAT) {
            return "F";
        }
        if (type == AbiType.DOUBLE) {
            return "D";
        }
        if (type == AbiType.STRING) {
            return "Ljava/lang/String;";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "[B";
        }
        throw new IllegalArgumentException("Unsupported ABI type: " + type.name());
    }
}
