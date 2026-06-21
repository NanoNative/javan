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
    private static final int ABI_VERSION = 2;

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
            files.add(Files2.writeString(cAbiTest(outputDirectory, libraryName), cAbiTest(libraryName, exports)));
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
        header.append("#define JAVAN_ABI_V1_DIRECT_EXPORTS 1").append(System.lineSeparator());
        header.append("#define JAVAN_ABI_STRING_UTF8 1").append(System.lineSeparator());
        header.append("#define JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH 1").append(System.lineSeparator());
        header.append("#define JAVAN_ABI_RUNTIME_DIAGNOSTICS 1").append(System.lineSeparator());
        header.append("#define JAVAN_ABI_STRUCTURED_ERROR 1").append(System.lineSeparator());
        header.append("#define JAVAN_ABI_RESULT_WRAPPERS 1").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#ifdef __cplusplus").append(System.lineSeparator());
        header.append("extern \"C\" {").append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("typedef struct {").append(System.lineSeparator());
        header.append("    int8_t* data;").append(System.lineSeparator());
        header.append("    int length;").append(System.lineSeparator());
        header.append("} JavanByteArray;").append(System.lineSeparator()).append(System.lineSeparator());
        appendResultStruct(header);
        header.append("/* Frees memory returned by javan-owned String and byte[] exports. */").append(System.lineSeparator());
        header.append("void javan_free(void* value);").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("/* Frees owned strings inside a JavanResult returned by javan_try_* wrappers. */").append(System.lineSeparator());
        header.append("void javan_result_free(JavanResult* result);").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("/* Returns the last caught Javan runtime error for this library, or NULL if none is set. */").append(System.lineSeparator());
        header.append("const char* javan_last_error(void);").append(System.lineSeparator());
        appendCStructuredErrorDeclarations(header);
        header.append("void javan_clear_error(void);").append(System.lineSeparator()).append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            header.append("/* Exported from ").append(export.display())
                .append(". Returned char* and JavanByteArray.data are javan-owned; release with javan_free. */")
                .append(System.lineSeparator());
            header.append(signature(export)).append(";").append(System.lineSeparator()).append(System.lineSeparator());
            header.append("/* ABI v2 result wrapper for ").append(export.display())
                .append(". Diagnostic strings inside JavanResult are owned; release with javan_result_free. */")
                .append(System.lineSeparator());
            header.append(trySignature(export)).append(";").append(System.lineSeparator()).append(System.lineSeparator());
        }
        header.append("#ifdef __cplusplus").append(System.lineSeparator());
        header.append("}").append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        header.append("#endif").append(System.lineSeparator());
        return header.toString();
    }

    private static String cAbiTest(final String libraryName, final List<ExportedMethod> exports) {
        final StringBuilder test = new StringBuilder();
        test.append("#include <stddef.h>").append(System.lineSeparator());
        test.append("#include \"").append(libraryName).append(".h\"").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("#if JAVAN_ABI_VERSION != ").append(ABI_VERSION).append(System.lineSeparator());
        test.append("#error Unsupported Javan ABI version").append(System.lineSeparator());
        test.append("#endif").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("_Static_assert(offsetof(JavanByteArray, data) == 0, \"JavanByteArray.data must be first\");").append(System.lineSeparator());
        test.append("_Static_assert(sizeof(((JavanByteArray*)0)->length) == sizeof(int), \"JavanByteArray.length must be int\");").append(System.lineSeparator());
        test.append("_Static_assert(offsetof(JavanResult, ok) == 0, \"JavanResult.ok must be first\");").append(System.lineSeparator());
        test.append("_Static_assert(sizeof(((JavanResult*)0)->line) == sizeof(int), \"JavanResult.line must be int\");").append(System.lineSeparator());
        test.append("_Static_assert(sizeof(((JavanResult*)0)->bytecode_offset) == sizeof(int), \"JavanResult.bytecode_offset must be int\");").append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_V1_DIRECT_EXPORTS == 1, \"ABI v1 direct exports must remain available\");").append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_STRING_UTF8 == 1, \"String ABI must be UTF-8 char pointer\");").append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH == 1, \"byte[] ABI must be pointer plus length\");").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_RUNTIME_DIAGNOSTICS == 1, \"runtime diagnostics ABI must be available\");").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_STRUCTURED_ERROR == 1, \"structured error ABI must be available\");").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("_Static_assert(JAVAN_ABI_RESULT_WRAPPERS == 1, \"result wrapper ABI must be available\");").append(System.lineSeparator()).append(System.lineSeparator());
        test.append("int main(void) {").append(System.lineSeparator());
        test.append("    void (*free_fn)(void*) = javan_free;").append(System.lineSeparator());
        test.append("    void (*result_free_fn)(JavanResult*) = javan_result_free;").append(System.lineSeparator());
        test.append("    const char* (*error_fn)(void) = javan_last_error;").append(System.lineSeparator());
        test.append("    const char* (*error_code_fn)(void) = javan_last_error_code;").append(System.lineSeparator());
        test.append("    const char* (*error_summary_fn)(void) = javan_last_error_summary;").append(System.lineSeparator());
        test.append("    const char* (*error_class_fn)(void) = javan_last_error_class;").append(System.lineSeparator());
        test.append("    const char* (*error_method_fn)(void) = javan_last_error_method;").append(System.lineSeparator());
        test.append("    const char* (*error_file_fn)(void) = javan_last_error_file;").append(System.lineSeparator());
        test.append("    int (*error_line_fn)(void) = javan_last_error_line;").append(System.lineSeparator());
        test.append("    int (*error_bytecode_fn)(void) = javan_last_error_bytecode_offset;").append(System.lineSeparator());
        test.append("    const char* (*error_source_fn)(void) = javan_last_error_source_line;").append(System.lineSeparator());
        test.append("    const char* (*error_why_fn)(void) = javan_last_error_why;").append(System.lineSeparator());
        test.append("    const char* (*error_fix_fn)(void) = javan_last_error_fix;").append(System.lineSeparator());
        test.append("    const char* (*error_detail_fn)(void) = javan_last_error_detail;").append(System.lineSeparator());
        test.append("    void (*clear_fn)(void) = javan_clear_error;").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            test.append("    ").append(tryFunctionPointer(export)).append(" = ").append(export.trySymbol()).append(";").append(System.lineSeparator());
        }
        test.append("    return free_fn == 0 || result_free_fn == 0 || error_fn == 0 || error_code_fn == 0 || error_summary_fn == 0").append(System.lineSeparator());
        test.append("        || error_class_fn == 0 || error_method_fn == 0 || error_file_fn == 0").append(System.lineSeparator());
        test.append("        || error_line_fn == 0 || error_bytecode_fn == 0 || error_source_fn == 0").append(System.lineSeparator());
        test.append("        || error_why_fn == 0 || error_fix_fn == 0 || error_detail_fn == 0 || clear_fn == 0");
        for (final ExportedMethod export : exports) {
            test.append(System.lineSeparator()).append("        || try_").append(export.trySymbol()).append("_fn == 0");
        }
        test.append(";").append(System.lineSeparator());
        test.append("}").append(System.lineSeparator());
        return test.toString();
    }

    private static void appendResultStruct(final StringBuilder header) {
        header.append("typedef struct {").append(System.lineSeparator());
        header.append("    int ok;").append(System.lineSeparator());
        header.append("    char* code;").append(System.lineSeparator());
        header.append("    char* message;").append(System.lineSeparator());
        header.append("    char* summary;").append(System.lineSeparator());
        header.append("    char* class_name;").append(System.lineSeparator());
        header.append("    char* method;").append(System.lineSeparator());
        header.append("    char* file;").append(System.lineSeparator());
        header.append("    int line;").append(System.lineSeparator());
        header.append("    int bytecode_offset;").append(System.lineSeparator());
        header.append("    char* source_line;").append(System.lineSeparator());
        header.append("    char* why;").append(System.lineSeparator());
        header.append("    char* fix;").append(System.lineSeparator());
        header.append("    char* detail;").append(System.lineSeparator());
        header.append("} JavanResult;").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendRustResultStruct(final StringBuilder rust) {
        rust.append("#[repr(C)]").append(System.lineSeparator());
        rust.append("#[derive(Clone, Copy)]").append(System.lineSeparator());
        rust.append("pub struct JavanResult {").append(System.lineSeparator());
        rust.append("    pub ok: i32,").append(System.lineSeparator());
        rust.append("    pub code: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub message: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub summary: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub class_name: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub method: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub file: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub line: i32,").append(System.lineSeparator());
        rust.append("    pub bytecode_offset: i32,").append(System.lineSeparator());
        rust.append("    pub source_line: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub why: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub fix: *mut c_char,").append(System.lineSeparator());
        rust.append("    pub detail: *mut c_char,").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendRustErrorHelpers(final StringBuilder rust) {
        rust.append("#[derive(Debug, Clone, PartialEq, Eq)]").append(System.lineSeparator());
        rust.append("pub struct JavanError {").append(System.lineSeparator());
        rust.append("    pub code: Option<String>,").append(System.lineSeparator());
        rust.append("    pub message: Option<String>,").append(System.lineSeparator());
        rust.append("    pub summary: Option<String>,").append(System.lineSeparator());
        rust.append("    pub class_name: Option<String>,").append(System.lineSeparator());
        rust.append("    pub method: Option<String>,").append(System.lineSeparator());
        rust.append("    pub file: Option<String>,").append(System.lineSeparator());
        rust.append("    pub line: i32,").append(System.lineSeparator());
        rust.append("    pub bytecode_offset: i32,").append(System.lineSeparator());
        rust.append("    pub source_line: Option<String>,").append(System.lineSeparator());
        rust.append("    pub why: Option<String>,").append(System.lineSeparator());
        rust.append("    pub fix: Option<String>,").append(System.lineSeparator());
        rust.append("    pub detail: Option<String>,").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("impl std::fmt::Display for JavanError {").append(System.lineSeparator());
        rust.append("    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {").append(System.lineSeparator());
        rust.append("        let text = self.detail.as_deref()").append(System.lineSeparator());
        rust.append("            .or(self.message.as_deref())").append(System.lineSeparator());
        rust.append("            .or(self.summary.as_deref())").append(System.lineSeparator());
        rust.append("            .or(self.code.as_deref())").append(System.lineSeparator());
        rust.append("            .unwrap_or(\"javan call failed\");").append(System.lineSeparator());
        rust.append("        write!(formatter, \"{}\", text)").append(System.lineSeparator());
        rust.append("    }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("impl std::error::Error for JavanError {}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("impl JavanError {").append(System.lineSeparator());
        rust.append("    unsafe fn from_result(result: &JavanResult) -> JavanError {").append(System.lineSeparator());
        rust.append("        JavanError {").append(System.lineSeparator());
        rust.append("            code: unsafe { javan_result_text(result.code) },").append(System.lineSeparator());
        rust.append("            message: unsafe { javan_result_text(result.message) },").append(System.lineSeparator());
        rust.append("            summary: unsafe { javan_result_text(result.summary) },").append(System.lineSeparator());
        rust.append("            class_name: unsafe { javan_result_text(result.class_name) },").append(System.lineSeparator());
        rust.append("            method: unsafe { javan_result_text(result.method) },").append(System.lineSeparator());
        rust.append("            file: unsafe { javan_result_text(result.file) },").append(System.lineSeparator());
        rust.append("            line: result.line,").append(System.lineSeparator());
        rust.append("            bytecode_offset: result.bytecode_offset,").append(System.lineSeparator());
        rust.append("            source_line: unsafe { javan_result_text(result.source_line) },").append(System.lineSeparator());
        rust.append("            why: unsafe { javan_result_text(result.why) },").append(System.lineSeparator());
        rust.append("            fix: unsafe { javan_result_text(result.fix) },").append(System.lineSeparator());
        rust.append("            detail: unsafe { javan_result_text(result.detail) },").append(System.lineSeparator());
        rust.append("        }").append(System.lineSeparator());
        rust.append("    }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("unsafe fn javan_result_text(value: *mut c_char) -> Option<String> {").append(System.lineSeparator());
        rust.append("    if value.is_null() {").append(System.lineSeparator());
        rust.append("        None").append(System.lineSeparator());
        rust.append("    } else {").append(System.lineSeparator());
        rust.append("        Some(unsafe { CStr::from_ptr(value as *const c_char) }.to_string_lossy().into_owned())").append(System.lineSeparator());
        rust.append("    }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendCStructuredErrorDeclarations(final StringBuilder header) {
        header.append("const char* javan_last_error_code(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_summary(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_class(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_method(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_file(void);").append(System.lineSeparator());
        header.append("int javan_last_error_line(void);").append(System.lineSeparator());
        header.append("int javan_last_error_bytecode_offset(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_source_line(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_why(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_fix(void);").append(System.lineSeparator());
        header.append("const char* javan_last_error_detail(void);").append(System.lineSeparator());
    }

    private static String rust(final String libraryName, final List<ExportedMethod> exports) {
        final StringBuilder rust = new StringBuilder();
        rust.append("#![allow(non_camel_case_types)]").append(System.lineSeparator());
        rust.append("use std::ffi::{c_char, c_void, CStr};").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("#[repr(C)]").append(System.lineSeparator());
        rust.append("#[derive(Clone, Copy)]").append(System.lineSeparator());
        rust.append("pub struct JavanByteArray {").append(System.lineSeparator());
        rust.append("    pub data: *mut i8,").append(System.lineSeparator());
        rust.append("    pub length: i32,").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        appendRustResultStruct(rust);
        appendRustErrorHelpers(rust);
        rust.append("#[link(name = \"").append(libraryName).append("\")]").append(System.lineSeparator());
        rust.append("unsafe extern \"C\" {").append(System.lineSeparator());
        rust.append("    pub fn javan_free(value: *mut c_void);").append(System.lineSeparator());
        rust.append("    pub fn javan_result_free(result: *mut JavanResult);").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_code() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_summary() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_class() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_method() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_file() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_line() -> i32;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_bytecode_offset() -> i32;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_source_line() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_why() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_fix() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_last_error_detail() -> *const c_char;").append(System.lineSeparator());
        rust.append("    pub fn javan_clear_error();").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            rust.append("    pub fn ").append(export.symbol()).append("(")
                .append(rustParameters(export))
                .append(") -> ")
                .append(rustReturn(export.returnType()))
                .append(";")
                .append(System.lineSeparator());
            rust.append("    pub fn ").append(export.trySymbol()).append("(")
                .append(rustTryParameters(export))
                .append(") -> JavanResult;")
                .append(System.lineSeparator());
        }
        rust.append("}").append(System.lineSeparator());
        rust.append(System.lineSeparator());
        rust.append("/// Frees a javan-owned string returned by an exported method.").append(System.lineSeparator());
        rust.append("pub unsafe fn javan_free_string(value: *mut c_char) {").append(System.lineSeparator());
        rust.append("    unsafe { javan_free(value.cast()); }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("/// Frees the data pointer of a javan-owned byte array returned by an exported method.").append(System.lineSeparator());
        rust.append("pub unsafe fn javan_free_byte_array(value: JavanByteArray) {").append(System.lineSeparator());
        rust.append("    unsafe { javan_free(value.data.cast()); }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("unsafe fn javan_owned_string(value: *mut c_char) -> Option<String> {").append(System.lineSeparator());
        rust.append("    if value.is_null() {").append(System.lineSeparator());
        rust.append("        None").append(System.lineSeparator());
        rust.append("    } else {").append(System.lineSeparator());
        rust.append("        let text = unsafe { CStr::from_ptr(value as *const c_char) }.to_string_lossy().into_owned();").append(System.lineSeparator());
        rust.append("        unsafe { javan_free(value.cast()); }").append(System.lineSeparator());
        rust.append("        Some(text)").append(System.lineSeparator());
        rust.append("    }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("unsafe fn javan_owned_byte_array(value: JavanByteArray) -> Vec<i8> {").append(System.lineSeparator());
        rust.append("    if value.data.is_null() || value.length <= 0 {").append(System.lineSeparator());
        rust.append("        Vec::new()").append(System.lineSeparator());
        rust.append("    } else {").append(System.lineSeparator());
        rust.append("        let slice = unsafe { std::slice::from_raw_parts(value.data, value.length as usize) };").append(System.lineSeparator());
        rust.append("        let bytes = slice.to_vec();").append(System.lineSeparator());
        rust.append("        unsafe { javan_free(value.data.cast()); }").append(System.lineSeparator());
        rust.append("        bytes").append(System.lineSeparator());
        rust.append("    }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        rust.append("/// Frees owned diagnostic strings inside a JavanResult returned by javan_try_* wrappers.").append(System.lineSeparator());
        rust.append("pub unsafe fn javan_free_result(result: &mut JavanResult) {").append(System.lineSeparator());
        rust.append("    unsafe { javan_result_free(result as *mut JavanResult); }").append(System.lineSeparator());
        rust.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            rust.append(rustTryWrapper(export)).append(System.lineSeparator());
        }
        rust.append("// Direct export failures return safe zero/null values and expose the message through javan_last_error.").append(System.lineSeparator());
        rust.append("// try_javan_export_* wrappers return Result<T, JavanError> and free JavanResult diagnostics before returning.").append(System.lineSeparator());
        rust.append("// Returned strings and byte arrays are owned by javan. Release them with javan_free_string or javan_free_byte_array.").append(System.lineSeparator());
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
        go.append("import \"C\"").append(System.lineSeparator());
        go.append("import \"unsafe\"").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("type JavanByteArray = C.JavanByteArray").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("type JavanResult = C.JavanResult").append(System.lineSeparator()).append(System.lineSeparator());
        appendGoResultHelpers(go);
        go.append("// JavanFree releases a javan-owned pointer returned by an exported method.").append(System.lineSeparator());
        go.append("func JavanFree(value unsafe.Pointer) {").append(System.lineSeparator());
        go.append("    C.javan_free(value)").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// JavanFreeByteArray releases the data pointer of a javan-owned byte array.").append(System.lineSeparator());
        go.append("func JavanFreeByteArray(value JavanByteArray) {").append(System.lineSeparator());
        go.append("    C.javan_free(unsafe.Pointer(value.data))").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// JavanLastError returns the last caught Javan runtime error, or nil when no error is set.").append(System.lineSeparator());
        go.append("func JavanLastError() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        appendGoStructuredErrorFunctions(go);
        go.append("func JavanClearError() {").append(System.lineSeparator());
        go.append("    C.javan_clear_error()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// Export failures return safe zero/null values and expose the message through JavanLastError.").append(System.lineSeparator());
        go.append("// Returned C strings and byte buffers are owned by javan. Release them with JavanFree or JavanFreeByteArray.").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            go.append(goWrapper(export)).append(System.lineSeparator());
            go.append(goTryWrapper(export)).append(System.lineSeparator());
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
        go.append("import \"C\"").append(System.lineSeparator());
        go.append("import \"unsafe\"").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("type JavanByteArray = C.JavanByteArray").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("type JavanResult = C.JavanResult").append(System.lineSeparator()).append(System.lineSeparator());
        appendGoResultHelpers(go);
        go.append("// JavanFree releases a javan-owned pointer returned by an exported method.").append(System.lineSeparator());
        go.append("func JavanFree(value unsafe.Pointer) {").append(System.lineSeparator());
        go.append("    C.javan_free(value)").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// JavanFreeByteArray releases the data pointer of a javan-owned byte array.").append(System.lineSeparator());
        go.append("func JavanFreeByteArray(value JavanByteArray) {").append(System.lineSeparator());
        go.append("    C.javan_free(unsafe.Pointer(value.data))").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// JavanLastError returns the last caught Javan runtime error, or nil when no error is set.").append(System.lineSeparator());
        go.append("func JavanLastError() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        appendGoStructuredErrorFunctions(go);
        go.append("func JavanClearError() {").append(System.lineSeparator());
        go.append("    C.javan_clear_error()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("// Export failures return safe zero/null values and expose the message through JavanLastError.").append(System.lineSeparator());
        go.append("// Returned C strings and byte buffers are owned by javan. Release them with JavanFree or JavanFreeByteArray.").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            go.append(goWrapper(export)).append(System.lineSeparator());
            go.append(goTryWrapper(export)).append(System.lineSeparator());
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
        python.append("class JavanResult(ctypes.Structure):").append(System.lineSeparator());
        python.append("    _fields_ = [").append(System.lineSeparator());
        python.append("        (\"ok\", ctypes.c_int),").append(System.lineSeparator());
        python.append("        (\"code\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"message\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"summary\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"class_name\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"method\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"file\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"line\", ctypes.c_int),").append(System.lineSeparator());
        python.append("        (\"bytecode_offset\", ctypes.c_int),").append(System.lineSeparator());
        python.append("        (\"source_line\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"why\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"fix\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("        (\"detail\", ctypes.c_char_p),").append(System.lineSeparator());
        python.append("    ]").append(System.lineSeparator()).append(System.lineSeparator());
        appendPythonResultHelpers(python);
        python.append("def load(path: str | Path):").append(System.lineSeparator());
        python.append("    lib = ctypes.CDLL(str(path))").append(System.lineSeparator());
        python.append("    lib.javan_free.argtypes = [ctypes.c_void_p]").append(System.lineSeparator());
        python.append("    lib.javan_free.restype = None").append(System.lineSeparator());
        python.append("    lib.javan_result_free.argtypes = [ctypes.POINTER(JavanResult)]").append(System.lineSeparator());
        python.append("    lib.javan_result_free.restype = None").append(System.lineSeparator());
        python.append("    lib.javan_last_error.argtypes = []").append(System.lineSeparator());
        python.append("    lib.javan_last_error.restype = ctypes.c_char_p").append(System.lineSeparator());
        appendPythonStructuredErrorLoad(python);
        python.append("    lib.javan_clear_error.argtypes = []").append(System.lineSeparator());
        python.append("    lib.javan_clear_error.restype = None").append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            python.append("    lib.").append(export.symbol()).append(".argtypes = [")
                .append(pythonParameters(export))
                .append("]").append(System.lineSeparator());
            python.append("    lib.").append(export.symbol()).append(".restype = ")
                .append(pythonReturn(export.returnType()))
                .append(System.lineSeparator());
            python.append("    lib.").append(export.trySymbol()).append(".argtypes = [")
                .append(pythonTryParameters(export))
                .append("]").append(System.lineSeparator());
            python.append("    lib.").append(export.trySymbol()).append(".restype = JavanResult").append(System.lineSeparator());
        }
        python.append("    return lib").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def free(lib, value) -> None:").append(System.lineSeparator());
        python.append("    if value:").append(System.lineSeparator());
        python.append("        lib.javan_free(value)").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def free_byte_array(lib, value: JavanByteArray) -> None:").append(System.lineSeparator());
        python.append("    if value.data:").append(System.lineSeparator());
        python.append("        lib.javan_free(ctypes.cast(value.data, ctypes.c_void_p))").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def free_result(lib, result: JavanResult) -> None:").append(System.lineSeparator());
        python.append("    lib.javan_result_free(ctypes.byref(result))").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error()").append(System.lineSeparator()).append(System.lineSeparator());
        appendPythonStructuredErrorFunctions(python);
        python.append("def clear_error(lib) -> None:").append(System.lineSeparator());
        python.append("    lib.javan_clear_error()").append(System.lineSeparator()).append(System.lineSeparator());
        for (final ExportedMethod export : exports) {
            python.append(pythonTryWrapper(export)).append(System.lineSeparator());
        }
        python.append("# Export failures return safe zero/null values and expose the message through last_error.").append(System.lineSeparator());
        python.append("# try_javan_export_* wrappers raise JavanError and free JavanResult diagnostics before returning.").append(System.lineSeparator());
        python.append("# Returned strings and byte arrays are owned by javan. Release them with free or free_byte_array.").append(System.lineSeparator());
        return python.toString();
    }

    private static void appendGoResultHelpers(final StringBuilder go) {
        go.append("type JavanError struct {").append(System.lineSeparator());
        go.append("    Code string").append(System.lineSeparator());
        go.append("    Message string").append(System.lineSeparator());
        go.append("    Summary string").append(System.lineSeparator());
        go.append("    ClassName string").append(System.lineSeparator());
        go.append("    Method string").append(System.lineSeparator());
        go.append("    File string").append(System.lineSeparator());
        go.append("    Line int32").append(System.lineSeparator());
        go.append("    BytecodeOffset int32").append(System.lineSeparator());
        go.append("    SourceLine string").append(System.lineSeparator());
        go.append("    Why string").append(System.lineSeparator());
        go.append("    Fix string").append(System.lineSeparator());
        go.append("    Detail string").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func (err JavanError) Error() string {").append(System.lineSeparator());
        go.append("    if err.Detail != \"\" {").append(System.lineSeparator());
        go.append("        return err.Detail").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    if err.Message != \"\" {").append(System.lineSeparator());
        go.append("        return err.Message").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    if err.Summary != \"\" {").append(System.lineSeparator());
        go.append("        return err.Summary").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    if err.Code != \"\" {").append(System.lineSeparator());
        go.append("        return err.Code").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    return \"javan call failed\"").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanResultFree(result *JavanResult) {").append(System.lineSeparator());
        go.append("    C.javan_result_free(result)").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func javanResultText(value *C.char) string {").append(System.lineSeparator());
        go.append("    if value == nil {").append(System.lineSeparator());
        go.append("        return \"\"").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    return C.GoString(value)").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func javanErrorFromResult(result C.JavanResult) JavanError {").append(System.lineSeparator());
        go.append("    return JavanError{").append(System.lineSeparator());
        go.append("        Code: javanResultText(result.code),").append(System.lineSeparator());
        go.append("        Message: javanResultText(result.message),").append(System.lineSeparator());
        go.append("        Summary: javanResultText(result.summary),").append(System.lineSeparator());
        go.append("        ClassName: javanResultText(result.class_name),").append(System.lineSeparator());
        go.append("        Method: javanResultText(result.method),").append(System.lineSeparator());
        go.append("        File: javanResultText(result.file),").append(System.lineSeparator());
        go.append("        Line: int32(result.line),").append(System.lineSeparator());
        go.append("        BytecodeOffset: int32(result.bytecode_offset),").append(System.lineSeparator());
        go.append("        SourceLine: javanResultText(result.source_line),").append(System.lineSeparator());
        go.append("        Why: javanResultText(result.why),").append(System.lineSeparator());
        go.append("        Fix: javanResultText(result.fix),").append(System.lineSeparator());
        go.append("        Detail: javanResultText(result.detail),").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func javanOwnedString(value *C.char) *string {").append(System.lineSeparator());
        go.append("    if value == nil {").append(System.lineSeparator());
        go.append("        return nil").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    text := C.GoString(value)").append(System.lineSeparator());
        go.append("    C.javan_free(unsafe.Pointer(value))").append(System.lineSeparator());
        go.append("    return &text").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func javanOwnedByteArray(value C.JavanByteArray) []byte {").append(System.lineSeparator());
        go.append("    if value.data == nil || value.length <= 0 {").append(System.lineSeparator());
        go.append("        return []byte{}").append(System.lineSeparator());
        go.append("    }").append(System.lineSeparator());
        go.append("    bytes := C.GoBytes(unsafe.Pointer(value.data), value.length)").append(System.lineSeparator());
        go.append("    C.javan_free(unsafe.Pointer(value.data))").append(System.lineSeparator());
        go.append("    return bytes").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendGoStructuredErrorFunctions(final StringBuilder go) {
        go.append("// Structured last-error fields are borrowed and must not be freed.").append(System.lineSeparator());
        go.append("func JavanLastErrorCode() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_code()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorSummary() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_summary()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorClass() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_class()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorMethod() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_method()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorFile() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_file()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorLine() int32 {").append(System.lineSeparator());
        go.append("    return int32(C.javan_last_error_line())").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorBytecodeOffset() int32 {").append(System.lineSeparator());
        go.append("    return int32(C.javan_last_error_bytecode_offset())").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorSourceLine() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_source_line()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorWhy() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_why()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorFix() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_fix()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        go.append("func JavanLastErrorDetail() *C.char {").append(System.lineSeparator());
        go.append("    return C.javan_last_error_detail()").append(System.lineSeparator());
        go.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendPythonResultHelpers(final StringBuilder python) {
        python.append("class JavanError(Exception):").append(System.lineSeparator());
        python.append("    def __init__(self, result: JavanResult):").append(System.lineSeparator());
        python.append("        self.code = _text(result.code)").append(System.lineSeparator());
        python.append("        self.message = _text(result.message)").append(System.lineSeparator());
        python.append("        self.summary = _text(result.summary)").append(System.lineSeparator());
        python.append("        self.class_name = _text(result.class_name)").append(System.lineSeparator());
        python.append("        self.method = _text(result.method)").append(System.lineSeparator());
        python.append("        self.file = _text(result.file)").append(System.lineSeparator());
        python.append("        self.line = int(result.line)").append(System.lineSeparator());
        python.append("        self.bytecode_offset = int(result.bytecode_offset)").append(System.lineSeparator());
        python.append("        self.source_line = _text(result.source_line)").append(System.lineSeparator());
        python.append("        self.why = _text(result.why)").append(System.lineSeparator());
        python.append("        self.fix = _text(result.fix)").append(System.lineSeparator());
        python.append("        self.detail = _text(result.detail)").append(System.lineSeparator());
        python.append("        super().__init__(self.detail or self.message or self.summary or self.code or \"javan call failed\")").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def _text(value) -> str | None:").append(System.lineSeparator());
        python.append("    if value is None:").append(System.lineSeparator());
        python.append("        return None").append(System.lineSeparator());
        python.append("    if isinstance(value, bytes):").append(System.lineSeparator());
        python.append("        return value.decode(\"utf-8\", errors=\"replace\")").append(System.lineSeparator());
        python.append("    return ctypes.string_at(value).decode(\"utf-8\", errors=\"replace\")").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def _owned_string(lib, value) -> str | None:").append(System.lineSeparator());
        python.append("    if not value:").append(System.lineSeparator());
        python.append("        return None").append(System.lineSeparator());
        python.append("    text = ctypes.string_at(value).decode(\"utf-8\", errors=\"replace\")").append(System.lineSeparator());
        python.append("    lib.javan_free(value)").append(System.lineSeparator());
        python.append("    return text").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def _owned_bytes(lib, value: JavanByteArray) -> bytes:").append(System.lineSeparator());
        python.append("    if not value.data or value.length <= 0:").append(System.lineSeparator());
        python.append("        return b\"\"").append(System.lineSeparator());
        python.append("    data = ctypes.string_at(value.data, value.length)").append(System.lineSeparator());
        python.append("    free_byte_array(lib, value)").append(System.lineSeparator());
        python.append("    return data").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void appendPythonStructuredErrorLoad(final StringBuilder python) {
        appendPythonErrorTextBinding(python, "javan_last_error_code");
        appendPythonErrorTextBinding(python, "javan_last_error_summary");
        appendPythonErrorTextBinding(python, "javan_last_error_class");
        appendPythonErrorTextBinding(python, "javan_last_error_method");
        appendPythonErrorTextBinding(python, "javan_last_error_file");
        python.append("    lib.javan_last_error_line.argtypes = []").append(System.lineSeparator());
        python.append("    lib.javan_last_error_line.restype = ctypes.c_int").append(System.lineSeparator());
        python.append("    lib.javan_last_error_bytecode_offset.argtypes = []").append(System.lineSeparator());
        python.append("    lib.javan_last_error_bytecode_offset.restype = ctypes.c_int").append(System.lineSeparator());
        appendPythonErrorTextBinding(python, "javan_last_error_source_line");
        appendPythonErrorTextBinding(python, "javan_last_error_why");
        appendPythonErrorTextBinding(python, "javan_last_error_fix");
        appendPythonErrorTextBinding(python, "javan_last_error_detail");
    }

    private static void appendPythonErrorTextBinding(final StringBuilder python, final String name) {
        python.append("    lib.").append(name).append(".argtypes = []").append(System.lineSeparator());
        python.append("    lib.").append(name).append(".restype = ctypes.c_char_p").append(System.lineSeparator());
    }

    private static void appendPythonStructuredErrorFunctions(final StringBuilder python) {
        python.append("def last_error_code(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_code()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_summary(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_summary()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_class(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_class()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_method(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_method()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_file(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_file()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_line(lib) -> int:").append(System.lineSeparator());
        python.append("    return int(lib.javan_last_error_line())").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_bytecode_offset(lib) -> int:").append(System.lineSeparator());
        python.append("    return int(lib.javan_last_error_bytecode_offset())").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_source_line(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_source_line()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_why(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_why()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_fix(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_fix()").append(System.lineSeparator()).append(System.lineSeparator());
        python.append("def last_error_detail(lib) -> bytes | None:").append(System.lineSeparator());
        python.append("    return lib.javan_last_error_detail()").append(System.lineSeparator()).append(System.lineSeparator());
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
            files.add(Files2.writeString(c.resolve(libraryName + "_abi_test.c"), cAbiTest(libraryName, exports)));
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

    private static String trySignature(final ExportedMethod export) {
        return "JavanResult " + export.trySymbol() + "(" + tryParameters(export) + ")";
    }

    private static String tryFunctionPointer(final ExportedMethod export) {
        return "JavanResult (*try_" + export.trySymbol() + "_fn)(" + tryParameters(export) + ")";
    }

    private static String cParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add(cName(export.parameterTypes().get(index)) + " arg" + index);
        }
        return parameters.isEmpty() ? "void" : joinComma(parameters);
    }

    private static String tryParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add(cName(export.parameterTypes().get(index)) + " arg" + index);
        }
        if (export.returnType() != AbiType.VOID) {
            parameters.add(cReturnName(export.returnType()) + "* out");
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

    private static String rustTryParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add("arg" + index + ": " + rustParameter(export.parameterTypes().get(index)));
        }
        if (export.returnType() != AbiType.VOID) {
            parameters.add("out: *mut " + rustReturn(export.returnType()));
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

    private static String pythonTryParameters(final ExportedMethod export) {
        final List<String> parameters = new ArrayList<>();
        for (final AbiType type : export.parameterTypes()) {
            parameters.add(pythonParameter(type));
        }
        if (export.returnType() != AbiType.VOID) {
            parameters.add("ctypes.POINTER(" + pythonOutType(export.returnType()) + ")");
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

    private static String rustResultReturn(final AbiType type) {
        if (type == AbiType.VOID) {
            return "()";
        }
        if (type == AbiType.STRING) {
            return "Option<String>";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "Vec<i8>";
        }
        return rustReturn(type);
    }

    private static String rustOutDefault(final AbiType type) {
        if (type == AbiType.INT) {
            return "0_i32";
        }
        if (type == AbiType.LONG) {
            return "0_i64";
        }
        if (type == AbiType.FLOAT) {
            return "0.0_f32";
        }
        if (type == AbiType.DOUBLE) {
            return "0.0_f64";
        }
        if (type == AbiType.STRING) {
            return "std::ptr::null_mut()";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray { data: std::ptr::null_mut(), length: 0 }";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String rustSuccessValue(final AbiType type) {
        if (type == AbiType.STRING) {
            return "unsafe { javan_owned_string(out) }";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "unsafe { javan_owned_byte_array(out) }";
        }
        return "out";
    }

    private static String rustTryWrapper(final ExportedMethod export) {
        final StringBuilder rust = new StringBuilder();
        rust.append("pub unsafe fn try_").append(export.symbol()).append("(").append(rustParameters(export)).append(") -> Result<")
            .append(rustResultReturn(export.returnType()))
            .append(", JavanError> {")
            .append(System.lineSeparator());
        if (export.returnType() == AbiType.VOID) {
            rust.append("    let mut result = unsafe { ").append(export.trySymbol()).append("(").append(rustArguments(export)).append(") };").append(System.lineSeparator());
            rust.append("    if result.ok == 1 {").append(System.lineSeparator());
            rust.append("        unsafe { javan_result_free(&mut result as *mut JavanResult); }").append(System.lineSeparator());
            rust.append("        Ok(())").append(System.lineSeparator());
            rust.append("    } else {").append(System.lineSeparator());
            rust.append("        let error = unsafe { JavanError::from_result(&result) };").append(System.lineSeparator());
            rust.append("        unsafe { javan_result_free(&mut result as *mut JavanResult); }").append(System.lineSeparator());
            rust.append("        Err(error)").append(System.lineSeparator());
            rust.append("    }").append(System.lineSeparator());
        } else {
            rust.append("    let mut out: ").append(rustReturn(export.returnType())).append(" = ").append(rustOutDefault(export.returnType())).append(";").append(System.lineSeparator());
            rust.append("    let mut result = unsafe { ").append(export.trySymbol()).append("(").append(rustTryArguments(export)).append(") };").append(System.lineSeparator());
            rust.append("    if result.ok == 1 {").append(System.lineSeparator());
            rust.append("        let value = ").append(rustSuccessValue(export.returnType())).append(";").append(System.lineSeparator());
            rust.append("        unsafe { javan_result_free(&mut result as *mut JavanResult); }").append(System.lineSeparator());
            rust.append("        Ok(value)").append(System.lineSeparator());
            rust.append("    } else {").append(System.lineSeparator());
            rust.append("        let error = unsafe { JavanError::from_result(&result) };").append(System.lineSeparator());
            rust.append("        unsafe { javan_result_free(&mut result as *mut JavanResult); }").append(System.lineSeparator());
            rust.append("        Err(error)").append(System.lineSeparator());
            rust.append("    }").append(System.lineSeparator());
        }
        rust.append("}").append(System.lineSeparator());
        return rust.toString();
    }

    private static String rustArguments(final ExportedMethod export) {
        final List<String> arguments = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            arguments.add("arg" + index);
        }
        return joinComma(arguments);
    }

    private static String rustTryArguments(final ExportedMethod export) {
        final List<String> arguments = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            arguments.add("arg" + index);
        }
        arguments.add("&mut out as *mut " + rustReturn(export.returnType()));
        return joinComma(arguments);
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

    private static String goTryWrapper(final ExportedMethod export) {
        final StringBuilder go = new StringBuilder();
        go.append("func Try").append(goFunction(export.symbol())).append("(");
        final List<String> parameters = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            parameters.add("arg" + index + " " + goType(export.parameterTypes().get(index)));
        }
        go.append(joinComma(parameters)).append(") ");
        if (export.returnType() == AbiType.VOID) {
            go.append("error");
        } else {
            go.append("(").append(goResultType(export.returnType())).append(", error)");
        }
        go.append(" {").append(System.lineSeparator());
        if (export.returnType() == AbiType.VOID) {
            go.append("    result := C.").append(export.trySymbol()).append("(").append(goArguments(export)).append(")").append(System.lineSeparator());
            go.append("    if result.ok != 1 {").append(System.lineSeparator());
            go.append("        err := javanErrorFromResult(result)").append(System.lineSeparator());
            go.append("        C.javan_result_free(&result)").append(System.lineSeparator());
            go.append("        return err").append(System.lineSeparator());
            go.append("    }").append(System.lineSeparator());
            go.append("    C.javan_result_free(&result)").append(System.lineSeparator());
            go.append("    return nil").append(System.lineSeparator());
        } else {
            go.append("    var out ").append(goCType(export.returnType())).append(System.lineSeparator());
            go.append("    result := C.").append(export.trySymbol()).append("(").append(goTryArguments(export)).append(")").append(System.lineSeparator());
            go.append("    if result.ok != 1 {").append(System.lineSeparator());
            go.append("        err := javanErrorFromResult(result)").append(System.lineSeparator());
            go.append("        C.javan_result_free(&result)").append(System.lineSeparator());
            go.append("        return ").append(goErrorReturn(export.returnType())).append(", err").append(System.lineSeparator());
            go.append("    }").append(System.lineSeparator());
            go.append("    value := ").append(goResultValue(export.returnType(), "out")).append(System.lineSeparator());
            go.append("    C.javan_result_free(&result)").append(System.lineSeparator());
            go.append("    return value, nil").append(System.lineSeparator());
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

    private static String goTryArguments(final ExportedMethod export) {
        final List<String> arguments = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            final AbiType type = export.parameterTypes().get(index);
            arguments.add(goArgument(type, "arg" + index));
        }
        arguments.add("&out");
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

    private static String goCType(final AbiType type) {
        if (type == AbiType.INT) {
            return "C.int";
        }
        if (type == AbiType.LONG) {
            return "C.longlong";
        }
        if (type == AbiType.FLOAT) {
            return "C.float";
        }
        if (type == AbiType.DOUBLE) {
            return "C.double";
        }
        if (type == AbiType.STRING) {
            return "*C.char";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "C.JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String goResultType(final AbiType type) {
        if (type == AbiType.STRING) {
            return "*string";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "[]byte";
        }
        return goType(type);
    }

    private static String goResultValue(final AbiType type, final String value) {
        if (type == AbiType.INT) {
            return "int32(" + value + ")";
        }
        if (type == AbiType.LONG) {
            return "int64(" + value + ")";
        }
        if (type == AbiType.FLOAT) {
            return "float32(" + value + ")";
        }
        if (type == AbiType.DOUBLE) {
            return "float64(" + value + ")";
        }
        if (type == AbiType.STRING) {
            return "javanOwnedString(" + value + ")";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "javanOwnedByteArray(" + value + ")";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    private static String goErrorReturn(final AbiType type) {
        if (type == AbiType.INT || type == AbiType.LONG) {
            return "0";
        }
        if (type == AbiType.FLOAT || type == AbiType.DOUBLE) {
            return "0";
        }
        if (type == AbiType.STRING || type == AbiType.BYTE_ARRAY) {
            return "nil";
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

    private static String pythonOutType(final AbiType type) {
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

    private static String pythonOutInitializer(final AbiType type) {
        if (type == AbiType.BYTE_ARRAY) {
            return "JavanByteArray()";
        }
        return pythonOutType(type) + "()";
    }

    private static String pythonResultValue(final AbiType type) {
        if (type == AbiType.STRING) {
            return "_owned_string(lib, out.value)";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "_owned_bytes(lib, out)";
        }
        return "out.value";
    }

    private static String pythonTryWrapper(final ExportedMethod export) {
        final StringBuilder python = new StringBuilder();
        python.append("def try_").append(export.symbol()).append("(lib");
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            python.append(", arg").append(index);
        }
        python.append("):").append(System.lineSeparator());
        if (export.returnType() == AbiType.VOID) {
            python.append("    result = lib.").append(export.trySymbol()).append("(").append(pythonArgumentList(export)).append(")").append(System.lineSeparator());
            python.append("    try:").append(System.lineSeparator());
            python.append("        if result.ok != 1:").append(System.lineSeparator());
            python.append("            raise JavanError(result)").append(System.lineSeparator());
            python.append("        return None").append(System.lineSeparator());
            python.append("    finally:").append(System.lineSeparator());
            python.append("        free_result(lib, result)").append(System.lineSeparator());
        } else {
            python.append("    out = ").append(pythonOutInitializer(export.returnType())).append(System.lineSeparator());
            python.append("    result = lib.").append(export.trySymbol()).append("(").append(pythonTryArgumentList(export)).append(")").append(System.lineSeparator());
            python.append("    try:").append(System.lineSeparator());
            python.append("        if result.ok != 1:").append(System.lineSeparator());
            python.append("            raise JavanError(result)").append(System.lineSeparator());
            python.append("        return ").append(pythonResultValue(export.returnType())).append(System.lineSeparator());
            python.append("    finally:").append(System.lineSeparator());
            python.append("        free_result(lib, result)").append(System.lineSeparator());
        }
        return python.toString();
    }

    private static String pythonArgumentList(final ExportedMethod export) {
        final List<String> arguments = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            arguments.add("arg" + index);
        }
        return joinComma(arguments);
    }

    private static String pythonTryArgumentList(final ExportedMethod export) {
        final List<String> arguments = new ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            arguments.add("arg" + index);
        }
        arguments.add("ctypes.byref(out)");
        return joinComma(arguments);
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
