package javan.build;

import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates C-first native library bindings.
 */
public final class BindingGenerator {
    private static final int ABI_VERSION = 1;

    /**
     * Writes requested bindings. C headers are always generated because every other binding depends on them.
     *
     * @param outputDirectory javan output directory
     * @param libraryName logical library name
     * @param exports resolved exports
     * @param requested requested binding languages
     * @param artifacts native library artifacts to copy into language folders
     * @return generated files
     * @throws IOException when writing fails
     */
    public List<Path> generate(
        final Path outputDirectory,
        final String libraryName,
        final List<ExportedMethod> exports,
        final List<BindingLanguage> requested,
        final List<Path> artifacts
    ) throws IOException {
        final List<BindingLanguage> languages = new ArrayList<>();
        languages.add(BindingLanguage.C);
        for (final BindingLanguage language : requested) {
            if (!containsLanguage(languages, language)) {
                languages.add(language);
            }
        }
        final List<Path> files = new ArrayList<>();
        if (containsLanguage(languages, BindingLanguage.C)) {
            files.add(Files2.writeString(cHeader(outputDirectory, libraryName), cHeader(libraryName, exports)));
            files.add(Files2.writeString(cAbiTest(outputDirectory, libraryName), cAbiTest(libraryName)));
        }
        if (containsLanguage(languages, BindingLanguage.RUST)) {
            files.add(Files2.writeString(outputDirectory.resolve("dist/bindings/rust/lib.rs"), rust(libraryName, exports)));
        }
        if (containsLanguage(languages, BindingLanguage.GO)) {
            files.add(Files2.writeString(outputDirectory.resolve("dist/bindings/go/" + safePackage(libraryName) + ".go"), go(libraryName, exports)));
        }
        if (containsLanguage(languages, BindingLanguage.PYTHON)) {
            files.add(Files2.writeString(outputDirectory.resolve("dist/bindings/python/" + safePackage(libraryName) + ".py"), python(libraryName, exports)));
        }
        files.addAll(generateLanguagePackages(outputDirectory, libraryName, exports, languages, artifacts));
        return List.copyOf(files);
    }

    /**
     * Writes requested bindings without native artifacts.
     *
     * @param outputDirectory javan output directory
     * @param libraryName logical library name
     * @param exports resolved exports
     * @param requested requested binding languages
     * @return generated files
     * @throws IOException when writing fails
     */
    public List<Path> generate(
        final Path outputDirectory,
        final String libraryName,
        final List<ExportedMethod> exports,
        final List<BindingLanguage> requested
    ) throws IOException {
        return generate(outputDirectory, libraryName, exports, requested, List.of());
    }

    private static Path cHeader(final Path outputDirectory, final String libraryName) {
        return outputDirectory.resolve("dist/bindings/c/" + libraryName + ".h");
    }

    private static Path cAbiTest(final Path outputDirectory, final String libraryName) {
        return outputDirectory.resolve("dist/bindings/c/" + libraryName + "_abi_test.c");
    }

    private static String cHeader(final String libraryName, final List<ExportedMethod> exports) {
        final String guard = "JAVAN_BINDINGS_" + Strings2.toAsciiUpperCase(safePackage(libraryName)) + "_H";
        final StringBuilder header = new StringBuilder();
        header.append("#ifndef ").append(guard).append(System.lineSeparator());
        header.append("#define ").append(guard).append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#include <stdint.h>").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#define JAVAN_ABI_VERSION ").append(ABI_VERSION).append(System.lineSeparator());
        header.append("#define JAVAN_ABI_STRING_UTF8 1").append(System.lineSeparator());
        header.append("#define JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH 1").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#ifdef __cplusplus").append(System.lineSeparator());
        header.append("extern \"C\" {").append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("typedef struct {").append(System.lineSeparator());
        header.append("    int8_t* data;").append(System.lineSeparator());
        header.append("    int length;").append(System.lineSeparator());
        header.append("} JavanByteArray;").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("/* Frees memory returned by javan-owned String and byte[] exports. */").append(System.lineSeparator());
        header.append("void javan_free(void* value);").append(System.lineSeparator()).append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            header.append("/* Exported from ").append(export.display())
                .append(". Returned char* and JavanByteArray.data are javan-owned; release with javan_free. */")
                .append(System.lineSeparator());
            header.append(signature(export)).append(";").append(System.lineSeparator()).append(System.lineSeparator());
        }
        header.append("#ifdef __cplusplus").append(System.lineSeparator());
        header.append("}").append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator());
        return header.toString();
    }

    private static String cAbiTest(final String libraryName) {
        final StringBuilder test = new StringBuilder();
        test.append("#include <stddef.h>").append(System.lineSeparator());
        test.append("#include \"").append(libraryName).append(".h\"").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("#if JAVAN_ABI_VERSION != ").append(ABI_VERSION).append(System.lineSeparator());
        test.append("#error Unsupported Javan ABI version").append(System.lineSeparator());
        test.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("_Static_assert(offsetof(JavanByteArray, data) == 0, \"JavanByteArray.data must be first\");").append(System.lineSeparator());
        test.append("_Static_assert(sizeof(((JavanByteArray*)0)->length) == sizeof(int), \"JavanByteArray.length must be int\");").append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_STRING_UTF8 == 1, \"String ABI must be UTF-8 char pointer\");").append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH == 1, \"byte[] ABI must be pointer plus length\");").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("int main(void) {").append(System.lineSeparator());
        test.append("    void (*free_fn)(void*) = javan_free;").append(System.lineSeparator());
        test.append("    return free_fn == 0;").append(System.lineSeparator());
        test.append("}").append(System.lineSeparator());
        return test.toString();
    }

    private static String rust(final String libraryName, final List<ExportedMethod> exports) {
        final StringBuilder rust = new StringBuilder();
        rust.append("#![allow(non_camel_case_types)]").append(System.lineSeparator());
        rust.append("use std::ffi::c_char;").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("#[repr(C)]").append(System.lineSeparator());
        rust.append("pub struct JavanByteArray {").append(System.lineSeparator());
        rust.append("    pub data: *mut i8,").append(System.lineSeparator());
        rust.append("    pub length: i32,").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("#[link(name = \"").append(libraryName).append("\")]").append(System.lineSeparator());
        rust.append("unsafe extern \"C\" {").append(System.lineSeparator());
        rust.append("    pub fn javan_free(value: *mut std::ffi::c_void);").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            rust.append("    pub fn ").append(export.symbol()).append("(")
                .append(rustParameters(export))
                .append(") -> ")
                .append(rustReturn(export.returnType()))
                .append(";")
                .append(System.lineSeparator());
        }
        rust.append("}").append(System.lineSeparator());
        rust.append(System.lineSeparator());
        rust.append("// Returned strings and byte arrays are owned by javan. Release them with javan_free.").append(System.lineSeparator());
        return rust.toString();
    }

    private static String go(final String libraryName, final List<ExportedMethod> exports) {
        final StringBuilder go = new StringBuilder();
        go.append("package ").append(safePackage(libraryName)).append(System.lineSeparator()).append(System.lineSeparator());
        go.append("/*").append(System.lineSeparator());
        go.append("#cgo CFLAGS: -I../c").append(System.lineSeparator());
        go.append("#cgo LDFLAGS: -L../.. -l").append(libraryName).append(System.lineSeparator());
        go.append("#include \"").append(libraryName).append(".h\"").append(System.lineSeparator());
        go.append("*/").append(System.lineSeparator());
        go.append("import \"C\"").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("type JavanByteArray = C.JavanByteArray").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// Returned C strings and byte buffers are owned by javan. Release them with C.javan_free.").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            go.append(goWrapper(export)).append(System.lineSeparator());
        }
        return go.toString();
    }

    private static String goPackaged(final String libraryName, final List<ExportedMethod> exports) {
        final StringBuilder go = new StringBuilder();
        go.append("package ").append(safePackage(libraryName)).append(System.lineSeparator()).append(System.lineSeparator());
        go.append("/*").append(System.lineSeparator());
        go.append("#cgo CFLAGS: -I.").append(System.lineSeparator());
        go.append("#cgo LDFLAGS: -L. -l").append(libraryName).append(System.lineSeparator());
        go.append("#include \"").append(libraryName).append(".h\"").append(System.lineSeparator());
        go.append("*/").append(System.lineSeparator());
        go.append("import \"C\"").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("type JavanByteArray = C.JavanByteArray").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// Returned C strings and byte buffers are owned by javan. Release them with C.javan_free.").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            go.append(goWrapper(export)).append(System.lineSeparator());
        }
        return go.toString();
    }

    private static String python(final String libraryName, final List<ExportedMethod> exports) {
        final StringBuilder python = new StringBuilder();
        python.append("from __future__ import annotations").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("import ctypes").append(System.lineSeparator());
        python.append("from pathlib import Path").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("class JavanByteArray(ctypes.Structure):").append(System.lineSeparator());
        python.append("    _fields_ = [(\"data\", ctypes.POINTER(ctypes.c_int8)), (\"length\", ctypes.c_int)]").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def load(path: str | Path):").append(System.lineSeparator());
        python.append("    lib = ctypes.CDLL(str(path))").append(System.lineSeparator());
        python.append("    lib.javan_free.argtypes = [ctypes.c_void_p]").append(System.lineSeparator());
        python.append("    lib.javan_free.restype = None").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            python.append("    lib.").append(export.symbol()).append(".argtypes = [")
                .append(pythonParameters(export))
                .append("]").append(System.lineSeparator());
            python.append("    lib.").append(export.symbol()).append(".restype = ")
                .append(pythonReturn(export.returnType()))
                .append(System.lineSeparator());
        }
        python.append("    return lib").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("# Returned strings and byte arrays are owned by javan. Release them with lib.javan_free.").append(System.lineSeparator());
        return python.toString();
    }

    private static List<Path> generateLanguagePackages(
        final Path outputDirectory,
        final String libraryName,
        final List<ExportedMethod> exports,
        final List<BindingLanguage> languages,
        final List<Path> artifacts
    ) throws IOException {
        final List<Path> files = new ArrayList<>();
        final Path root = outputDirectory.resolve("dist/lib").resolve(libraryName);
        if (containsLanguage(languages, BindingLanguage.C)) {
            final Path c = root.resolve("c");
            files.add(Files2.writeString(c.resolve(libraryName + ".h"), cHeader(libraryName, exports)));
            files.add(Files2.writeString(c.resolve(libraryName + "_abi_test.c"), cAbiTest(libraryName)));
            files.addAll(copyArtifacts(artifacts, c));
        }
        if (containsLanguage(languages, BindingLanguage.RUST)) {
            final Path rust = root.resolve("rust");
            files.add(Files2.writeString(rust.resolve("lib.rs"), rust(libraryName, exports)));
            files.addAll(copyArtifacts(artifacts, rust));
        }
        if (containsLanguage(languages, BindingLanguage.GO)) {
            final Path go = root.resolve("go");
            files.add(Files2.writeString(go.resolve(libraryName + ".h"), cHeader(libraryName, exports)));
            files.add(Files2.writeString(go.resolve(safePackage(libraryName) + ".go"), goPackaged(libraryName, exports)));
            files.addAll(copyArtifacts(artifacts, go));
        }
        if (containsLanguage(languages, BindingLanguage.PYTHON)) {
            final Path python = root.resolve("python");
            files.add(Files2.writeString(python.resolve(safePackage(libraryName) + ".py"), python(libraryName, exports)));
            files.addAll(copyArtifacts(artifacts, python));
        }
        return files;
    }

    private static boolean containsLanguage(final List<BindingLanguage> languages, final BindingLanguage target) {
        for (final BindingLanguage language : languages) {
            if (language == target) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> copyArtifacts(final List<Path> artifacts, final Path targetDirectory) throws IOException {
        final List<Path> files = new ArrayList<>();
        for (final Path artifact : artifacts) {
            if (Files.exists(artifact)) {
                final Path target = targetDirectory.resolve(artifact.getFileName().toString());
                Files.createDirectories(target.getParent());
                Files.copy(artifact, target, StandardCopyOption.REPLACE_EXISTING);
                files.add(target);
            }
        }
        return files;
    }

    private static String signature(final ExportedMethod export) {
        return cReturnName(export.returnType()) + " " + export.symbol() + "(" + cParameters(export) + ")";
    }

    private static String cParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add(cName(export.parameterTypes().get(index)) + " arg" + index);
        }
        return parameters.isEmpty() ? "void" : joinComma(parameters);
    }

    private static String rustParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add("arg" + index + ": " + rustParameter(export.parameterTypes().get(index)));
        }
        return joinComma(parameters);
    }

    private static String pythonParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (final AbiType type : export.parameterTypes()) {
            parameters.add(pythonParameter(type));
        }
        return joinComma(parameters);
    }

    private static String rustParameter(final AbiType type) {
        if (type == AbiType.VOID) {
            return "()";
        }
        if (type == AbiType.INT) {
            return "i32";
        }
        if (type == AbiType.LONG) {
            return "i64";
        }
        if (type == AbiType.FLOAT) {
            return "f32";
        }
        if (type == AbiType.DOUBLE) {
            return "f64";
        }
        if (type == AbiType.STRING) {
            return "*const c_char";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String rustReturn(final AbiType type) {
        if (type == AbiType.VOID) {
            return "()";
        }
        if (type == AbiType.INT) {
            return "i32";
        }
        if (type == AbiType.LONG) {
            return "i64";
        }
        if (type == AbiType.FLOAT) {
            return "f32";
        }
        if (type == AbiType.DOUBLE) {
            return "f64";
        }
        if (type == AbiType.STRING) {
            return "*mut c_char";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String goWrapper(final ExportedMethod export) {
        final StringBuilder go = new StringBuilder();
        go.append("func ").append(goFunction(export.symbol())).append("(");
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add("arg" + index + " " + goType(export.parameterTypes().get(index)));
        }
        go.append(joinComma(parameters)).append(") ").append(goType(export.returnType())).append(" {").append(System.lineSeparator());
        final String call = "C." + export.symbol() + "(" + goArguments(export) + ")";
        if (export.returnType() == AbiType.VOID) {
            go.append("    ").append(call).append(System.lineSeparator());
        } else {
            go.append("    return ").append(goReturn(export.returnType(), call)).append(System.lineSeparator());
        }
        go.append("}").append(System.lineSeparator());
        return go.toString();
    }

    private static String goArguments(final ExportedMethod export) {
        final List<String> arguments = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            final AbiType type = export.parameterTypes().get(index);
            arguments.add(goArgument(type, "arg" + index));
        }
        return joinComma(arguments);
    }

    private static String goArgument(final AbiType type, final String name) {
        if (type == AbiType.VOID) {
            return "";
        }
        if (type == AbiType.INT) {
            return "C.int(" + name + ")";
        }
        if (type == AbiType.LONG) {
            return "C.longlong(" + name + ")";
        }
        if (type == AbiType.FLOAT) {
            return "C.float(" + name + ")";
        }
        if (type == AbiType.DOUBLE) {
            return "C.double(" + name + ")";
        }
        if (type == AbiType.STRING) {
            return name;
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "C.JavanByteArray(" + name + ")";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String goReturn(final AbiType type, final String call) {
        if (type == AbiType.VOID) {
            return "";
        }
        if (type == AbiType.INT) {
            return "int32(" + call + ")";
        }
        if (type == AbiType.LONG) {
            return "int64(" + call + ")";
        }
        if (type == AbiType.FLOAT) {
            return "float32(" + call + ")";
        }
        if (type == AbiType.DOUBLE) {
            return "float64(" + call + ")";
        }
        if (type == AbiType.STRING) {
            return call;
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray(" + call + ")";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String goType(final AbiType type) {
        if (type == AbiType.VOID) {
            return "";
        }
        if (type == AbiType.INT) {
            return "int32";
        }
        if (type == AbiType.LONG) {
            return "int64";
        }
        if (type == AbiType.FLOAT) {
            return "float32";
        }
        if (type == AbiType.DOUBLE) {
            return "float64";
        }
        if (type == AbiType.STRING) {
            return "*C.char";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String goFunction(final String symbol) {
        final StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (int index = 0; index < symbol.length(); index++) {
            final char ch = symbol.charAt(index);
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                result.append(asciiUpper(ch));
                upper = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static String pythonParameter(final AbiType type) {
        if (type == AbiType.VOID) {
            return "None";
        }
        if (type == AbiType.INT) {
            return "ctypes.c_int";
        }
        if (type == AbiType.LONG) {
            return "ctypes.c_longlong";
        }
        if (type == AbiType.FLOAT) {
            return "ctypes.c_float";
        }
        if (type == AbiType.DOUBLE) {
            return "ctypes.c_double";
        }
        if (type == AbiType.STRING) {
            return "ctypes.c_char_p";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String pythonReturn(final AbiType type) {
        if (type == AbiType.VOID) {
            return "None";
        }
        if (type == AbiType.INT) {
            return "ctypes.c_int";
        }
        if (type == AbiType.LONG) {
            return "ctypes.c_longlong";
        }
        if (type == AbiType.FLOAT) {
            return "ctypes.c_float";
        }
        if (type == AbiType.DOUBLE) {
            return "ctypes.c_double";
        }
        if (type == AbiType.STRING) {
            return "ctypes.c_void_p";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String safePackage(final String value) {
        final String safe = safePackageToken(value);
        if (Strings2.isBlank(safe) || isDigit(safe.charAt(0))) {
            return "javan_" + safe;
        }
        return safe;
    }

    private static String cName(final AbiType type) {
        return type.cName();
    }

    private static String cReturnName(final AbiType type) {
        return type.cReturnName();
    }

    private static String joinComma(final List<String> values) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static String safePackageToken(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || isDigit(ch) || ch == '_') {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }

    private static char asciiUpper(final char value) {
        if (value >= 'a' && value <= 'z') {
            return (char) (value - ('a' - 'A'));
        }
        return value;
    }

    private static boolean isDigit(final char value) {
        return value >= '0' && value <= '9';
    }
}
