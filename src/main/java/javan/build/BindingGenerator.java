package javan.build;

import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates C-first native library bindings.
 */
public final class BindingGenerator {
    /**
     * Writes requested bindings. C headers are always generated because every other binding depends on them.
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
        final Set<BindingLanguage> languages = new LinkedHashSet<>();
        languages.add(BindingLanguage.C);
        languages.addAll(requested);
        final List<Path> files = new ArrayList<>();
        if (languages.contains(BindingLanguage.C)) {
            files.add(Files2.writeString(cHeader(outputDirectory, libraryName), cHeader(libraryName, exports)));
        }
        if (languages.contains(BindingLanguage.RUST)) {
            files.add(Files2.writeString(outputDirectory.resolve("dist/bindings/rust/lib.rs"), rust(libraryName, exports)));
        }
        if (languages.contains(BindingLanguage.GO)) {
            files.add(Files2.writeString(outputDirectory.resolve("dist/bindings/go/" + safePackage(libraryName) + ".go"), go(libraryName, exports)));
        }
        if (languages.contains(BindingLanguage.PYTHON)) {
            files.add(Files2.writeString(outputDirectory.resolve("dist/bindings/python/" + safePackage(libraryName) + ".py"), python(libraryName, exports)));
        }
        return List.copyOf(files);
    }

    private static Path cHeader(final Path outputDirectory, final String libraryName) {
        return outputDirectory.resolve("dist/bindings/c/" + libraryName + ".h");
    }

    private static String cHeader(final String libraryName, final List<ExportedMethod> exports) {
        final String guard = "JAVAN_BINDINGS_" + safePackage(libraryName).toUpperCase(java.util.Locale.ROOT) + "_H";
        final StringBuilder header = new StringBuilder();
        header.append("#ifndef ").append(guard).append(System.lineSeparator());
        header.append("#define ").append(guard).append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#include <stdint.h>").append(System.lineSeparator()).append(System.lineSeparator());
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
            header.append("/* Exported from ").append(export.display()).append(". Caller owns returned char* and JavanByteArray.data. */")
                .append(System.lineSeparator());
            header.append(signature(export)).append(";").append(System.lineSeparator()).append(System.lineSeparator());
        }
        header.append("#ifdef __cplusplus").append(System.lineSeparator());
        header.append("}").append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator());
        return header.toString();
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

    private static String signature(final ExportedMethod export) {
        return export.returnType().cReturnName() + " " + export.symbol() + "(" + parameters(export, AbiType::cName) + ")";
    }

    private static String parameters(final ExportedMethod export, final java.util.function.Function<AbiType, String> mapper) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add(mapper.apply(export.parameterTypes().get(index)) + " arg" + index);
        }
        return parameters.isEmpty() ? "void" : String.join(", ", parameters);
    }

    private static String rustParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add("arg" + index + ": " + rustParameter(export.parameterTypes().get(index)));
        }
        return String.join(", ", parameters);
    }

    private static String pythonParameters(final ExportedMethod export) {
        return String.join(", ", export.parameterTypes().stream().map(BindingGenerator::pythonParameter).toList());
    }

    private static String rustParameter(final AbiType type) {
        return switch (type) {
            case VOID -> "()";
            case INT -> "i32";
            case LONG -> "i64";
            case FLOAT -> "f32";
            case DOUBLE -> "f64";
            case STRING -> "*const c_char";
            case BYTE_ARRAY -> "JavanByteArray";
        };
    }

    private static String rustReturn(final AbiType type) {
        return switch (type) {
            case VOID -> "()";
            case INT -> "i32";
            case LONG -> "i64";
            case FLOAT -> "f32";
            case DOUBLE -> "f64";
            case STRING -> "*mut c_char";
            case BYTE_ARRAY -> "JavanByteArray";
        };
    }

    private static String goWrapper(final ExportedMethod export) {
        final StringBuilder go = new StringBuilder();
        go.append("func ").append(goFunction(export.symbol())).append("(");
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add("arg" + index + " " + goType(export.parameterTypes().get(index)));
        }
        go.append(String.join(", ", parameters)).append(") ").append(goType(export.returnType())).append(" {").append(System.lineSeparator());
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
        return String.join(", ", arguments);
    }

    private static String goArgument(final AbiType type, final String name) {
        return switch (type) {
            case VOID -> "";
            case INT -> "C.int(" + name + ")";
            case LONG -> "C.longlong(" + name + ")";
            case FLOAT -> "C.float(" + name + ")";
            case DOUBLE -> "C.double(" + name + ")";
            case STRING -> name;
            case BYTE_ARRAY -> "C.JavanByteArray(" + name + ")";
        };
    }

    private static String goReturn(final AbiType type, final String call) {
        return switch (type) {
            case VOID -> "";
            case INT -> "int32(" + call + ")";
            case LONG -> "int64(" + call + ")";
            case FLOAT -> "float32(" + call + ")";
            case DOUBLE -> "float64(" + call + ")";
            case STRING -> call;
            case BYTE_ARRAY -> "JavanByteArray(" + call + ")";
        };
    }

    private static String goType(final AbiType type) {
        return switch (type) {
            case VOID -> "";
            case INT -> "int32";
            case LONG -> "int64";
            case FLOAT -> "float32";
            case DOUBLE -> "float64";
            case STRING -> "*C.char";
            case BYTE_ARRAY -> "JavanByteArray";
        };
    }

    private static String goFunction(final String symbol) {
        final StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (int index = 0; index < symbol.length(); index++) {
            final char ch = symbol.charAt(index);
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                result.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static String pythonParameter(final AbiType type) {
        return switch (type) {
            case VOID -> "None";
            case INT -> "ctypes.c_int";
            case LONG -> "ctypes.c_longlong";
            case FLOAT -> "ctypes.c_float";
            case DOUBLE -> "ctypes.c_double";
            case STRING -> "ctypes.c_char_p";
            case BYTE_ARRAY -> "JavanByteArray";
        };
    }

    private static String pythonReturn(final AbiType type) {
        return switch (type) {
            case VOID -> "None";
            case INT -> "ctypes.c_int";
            case LONG -> "ctypes.c_longlong";
            case FLOAT -> "ctypes.c_float";
            case DOUBLE -> "ctypes.c_double";
            case STRING -> "ctypes.c_void_p";
            case BYTE_ARRAY -> "JavanByteArray";
        };
    }

    private static String safePackage(final String value) {
        final String safe = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isBlank() || Character.isDigit(safe.charAt(0))) {
            return "javan_" + safe;
        }
        return safe;
    }
}
