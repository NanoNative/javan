package javan.codegen;

import javan.ir.IrFunction;
import javan.analysis.CMethodSymbols;
import javan.analysis.EntryPoint;
import javan.ir.IrMaterializedLambdaTarget;
import javan.build.AbiType;
import javan.build.ExportedMethod;
import javan.build.NativeInteropConfig;
import javan.classfile.MethodRef;
import javan.ir.IrClass;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.ir.IrSourceLocation;
import javan.ir.IrType;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Emits portable C for the initial javan IR profile.
 */
public final class CCodegen {
    private static final String RUNNABLE_RUN_DISPATCH_SYMBOL = BytecodeToIR.dispatchSymbol(new MethodRef("java/lang/Runnable", "run", "()V"));
    private static final String MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL = "javan_materialized_lambda_apply_object";
    private static final String MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL = "javan_materialized_lambda_apply_long_object";
    private static final String MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL = "javan_materialized_lambda_apply_object2";
    private static final String MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL = "javan_materialized_lambda_apply_supplier";
    private static final String MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL = "javan_materialized_lambda_apply_boolean";
    private static final String MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL = "javan_materialized_lambda_apply_void";
    private static final String MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL = "javan_materialized_lambda_apply_void2";
    private static final String EXACT_ENUM_LOOKUP_SYMBOL = "javan_exact_enum_lookup";
    private static final String EXACT_CATCH_NULL_APPLY_SYMBOL = "javan_exact_catch_null_apply";
    private static final String EXACT_TEMPORAL_OF_UNSUPPORTED_SYMBOL = "javan_exact_temporal_of_unsupported";
    private static final String EXACT_TEMPORAL_STRING_BRIDGE_UNSUPPORTED_SYMBOL = "javan_exact_temporal_string_bridge_unsupported";
    private static final String EXACT_CALENDAR_OF_MILLIS_UNSUPPORTED_SYMBOL = "javan_exact_calendar_of_millis_unsupported";
    private static final String EXACT_CALENDAR_OF_DATE_UNSUPPORTED_SYMBOL = "javan_exact_calendar_of_date_unsupported";
    private static final String EXACT_CALENDAR_OF_LOCAL_TIME_UNSUPPORTED_SYMBOL = "javan_exact_calendar_of_local_time_unsupported";
    private static final String EXACT_THROWABLE_STRING_OF_UNSUPPORTED_SYMBOL = "javan_exact_throwable_string_of_unsupported";
    private static final String TEMPORAL_CONVERSION_LAMBDA_UNSUPPORTED_SYMBOL = "javan_temporal_conversion_lambda_unsupported";
    private static final String GENERATED_ENUM_BY_NAME_SYMBOL = "javan_generated_enum_by_name";
    private static final String GENERATED_ENUM_BY_ORDINAL_SYMBOL = "javan_generated_enum_by_ordinal";
    private static final String GENERATED_OBJECT_CLONE_SYMBOL = "javan_generated_object_clone";
    private static final String RECORD_REFERENCE_EQUALS_DISPATCH = "javan_dispatch_record_reference_equals";
    private static final String RECORD_REFERENCE_HASH_CODE_DISPATCH = "javan_dispatch_record_reference_hash_code";
    private static final String FALLIBLE_APPLY_METHOD_NAME = "applyWithException";

    /**
     * Writes the generated C program.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @return generated main C file
     * @throws IOException when writing fails
     */
    public Path generate(final IrProgram program, final Path generatedDirectory) throws IOException {
        return generate(program, generatedDirectory, NativeInteropConfig.empty());
    }

    /**
     * Writes the generated C program with declared native imports.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @param nativeInterop declared native imports
     * @return generated main C file
     * @throws IOException when writing fails
     * @throws IllegalArgumentException when a native import descriptor is unsupported
     */
    public Path generate(
        final IrProgram program,
        final Path generatedDirectory,
        final NativeInteropConfig nativeInterop
    ) throws IOException {
        validateNativeWrapperNamespace(program, nativeInterop);
        validateImportedNativeDescriptors(nativeInterop);
        final NativeWrapperSymbols nativeWrapperSymbols = NativeWrapperSymbols.create(nativeInterop);
        final CodegenFeatures features = codegenFeatures(program);
        final List<String> objectResultSymbols = objectResultSymbols(program);
        final StringBuilder c = new StringBuilder();
        c.append("#include \"javan_runtime.h\"").append(System.lineSeparator());
        if (features.nonFiniteFloatingLiteral()) {
            c.append("#include <math.h>").append(System.lineSeparator());
        }
        c.append("#include <stddef.h>").append(System.lineSeparator());
        c.append("#include <stdio.h>").append(System.lineSeparator()).append(System.lineSeparator());
        emitObjectHeader(c);
        for (final IrClass classInfo : program.classes()) {
            emitStruct(classInfo, c);
        }
        if (!program.classes().isEmpty()) {
            c.append(System.lineSeparator());
        }
        emitTypeDescriptors(program, c);
        c.append(System.lineSeparator());
        final boolean emittedStaticFields = emitStaticFields(program, c);
        if (emittedStaticFields) {
            c.append(System.lineSeparator());
        }
        emitStaticRootInventory(program, c);
        c.append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            c.append("static void* ")
                .append(allocatorSymbol(classInfo.jvmName()))
                .append("(void);")
                .append(System.lineSeparator());
        }
        for (final IrFunction function : program.functions()) {
            if (!function.symbol().equals(program.entryFunction())) {
                emitSignature(function, c, true);
                c.append(";").append(System.lineSeparator());
            }
        }
        emitImportedNativeSignatures(nativeInterop, nativeWrapperSymbols, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatchSignature(dispatch, c);
            c.append(";").append(System.lineSeparator());
        }
        emitRuntimeHelperPrototypes(features, c);
        if (!program.materializedLambdaTargets().isEmpty()) {
            c.append("static void ").append(MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL).append("(void** result, void* self, void* arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL).append("(void** result, void* self, int64_t arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL).append("(void** result, void* self, void* first_arg, void* second_arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL).append("(void** result, void* self);").append(System.lineSeparator());
            c.append("static int ").append(MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL).append("(void* self, void* arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL).append("(void* self, void* arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL).append("(void* self, void* first_arg, void* second_arg);").append(System.lineSeparator());
        }
        c.append(System.lineSeparator());
        emitAllocators(program, c);
        emitGeneratedObjectHelpers(program, features, c);
        emitRecordShapeExactTypeHelper(program, c);
        emitEnumOrdinalHelpers(program, c);
        emitGeneratedEnumOrdinalHelper(program, c);
        emitExactEnumLookupHelpers(program, c);
        emitExactFunctionOrNullHelpers(program, c);
        emitExactTemporalBridgeHelpers(c);
        emitThreadHelpers(program, c);
        emitMaterializedLambdaHelpers(program, nativeWrapperSymbols, c);
        emitImportedNativeWrappers(nativeInterop, nativeWrapperSymbols, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatch(program, dispatch, c);
        }
        for (final IrFunction function : program.functions()) {
            emitFunction(program, function, objectResultSymbols, nativeWrapperSymbols, c, true);
        }
        return Files2.writeString(generatedDirectory.resolve("main.c"), c.toString());
    }

    /**
     * Writes generated C for a native library.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @param exports library exports
     * @return generated C file
     * @throws IOException when writing fails
     */
    public Path generateLibrary(
        final IrProgram program,
        final Path generatedDirectory,
        final List<ExportedMethod> exports
    ) throws IOException {
        return generateLibrary(program, generatedDirectory, exports, NativeInteropConfig.empty());
    }

    /**
     * Writes generated C for a native library with declared native imports.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @param exports library exports
     * @param nativeInterop declared native imports
     * @return generated C file
     * @throws IOException when writing fails
     * @throws IllegalArgumentException when a native import descriptor is unsupported
     */
    public Path generateLibrary(
        final IrProgram program,
        final Path generatedDirectory,
        final List<ExportedMethod> exports,
        final NativeInteropConfig nativeInterop
    ) throws IOException {
        validateNativeWrapperNamespace(program, nativeInterop);
        validateImportedNativeDescriptors(nativeInterop);
        final NativeWrapperSymbols nativeWrapperSymbols = NativeWrapperSymbols.create(nativeInterop);
        final CodegenFeatures features = codegenFeatures(program);
        final List<String> objectResultSymbols = objectResultSymbols(program);
        final StringBuilder c = new StringBuilder();
        c.append("#include \"javan_runtime.h\"").append(System.lineSeparator());
        if (features.nonFiniteFloatingLiteral()) {
            c.append("#include <math.h>").append(System.lineSeparator());
        }
        c.append("#include <stddef.h>").append(System.lineSeparator());
        c.append("#include <stdio.h>").append(System.lineSeparator()).append(System.lineSeparator());
        emitObjectHeader(c);
        for (final IrClass classInfo : program.classes()) {
            emitStruct(classInfo, c);
        }
        if (!program.classes().isEmpty()) {
            c.append(System.lineSeparator());
        }
        emitTypeDescriptors(program, c);
        c.append(System.lineSeparator());
        final boolean emittedStaticFields = emitStaticFields(program, c);
        if (emittedStaticFields) {
            c.append(System.lineSeparator());
        }
        emitStaticRootInventory(program, c);
        c.append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            c.append("static void* ")
                .append(allocatorSymbol(classInfo.jvmName()))
                .append("(void);")
                .append(System.lineSeparator());
        }
        for (final IrFunction function : program.functions()) {
            emitSignature(function, c, true);
            c.append(";").append(System.lineSeparator());
        }
        emitImportedNativeSignatures(nativeInterop, nativeWrapperSymbols, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatchSignature(dispatch, c);
            c.append(";").append(System.lineSeparator());
        }
        emitRuntimeHelperPrototypes(features, c);
        if (!program.materializedLambdaTargets().isEmpty()) {
            c.append("static void ").append(MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL).append("(void** result, void* self, void* arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL).append("(void** result, void* self, int64_t arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL).append("(void** result, void* self, void* first_arg, void* second_arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL).append("(void** result, void* self);").append(System.lineSeparator());
            c.append("static int ").append(MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL).append("(void* self, void* arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL).append("(void* self, void* arg);").append(System.lineSeparator());
            c.append("static void ").append(MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL).append("(void* self, void* first_arg, void* second_arg);").append(System.lineSeparator());
        }
        c.append(System.lineSeparator());
        emitAllocators(program, c);
        emitGeneratedObjectHelpers(program, features, c);
        emitRecordShapeExactTypeHelper(program, c);
        emitEnumOrdinalHelpers(program, c);
        emitGeneratedEnumOrdinalHelper(program, c);
        emitExactEnumLookupHelpers(program, c);
        emitExactFunctionOrNullHelpers(program, c);
        emitThreadHelpers(program, c);
        emitMaterializedLambdaHelpers(program, nativeWrapperSymbols, c);
        emitImportedNativeWrappers(nativeInterop, nativeWrapperSymbols, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatch(program, dispatch, c);
        }
        for (final IrFunction function : program.functions()) {
            emitFunction(program, function, objectResultSymbols, nativeWrapperSymbols, c, false);
        }
        emitLibraryInitializer(program, nativeWrapperSymbols, c);
        for (final ExportedMethod export : exports) {
            emitExportWrapper(export, nativeWrapperSymbols, c);
            emitResultWrapper(export, c);
        }
        return Files2.writeString(generatedDirectory.resolve("library.c"), c.toString());
    }

    private static void emitObjectHeader(final StringBuilder c) {
        // Declared in javan_runtime.h so runtime helpers and generated code share one object layout.
    }

    private static CodegenFeatures codegenFeatures(final IrProgram program) {
        return new CodegenFeatures(usesGeneratedObjectClone(program), usesNonFiniteFloatingLiteral(program));
    }

    private static List<String> objectResultSymbols(final IrProgram program) {
        final List<String> result = new java.util.ArrayList<>();
        for (final IrFunction function : program.functions()) {
            if (function.returnType() == javan.ir.IrType.OBJECT) {
                result.add(function.symbol());
            }
        }
        for (final IrDispatch dispatch : program.dispatches()) {
            if (dispatch.returnType() == javan.ir.IrType.OBJECT) {
                result.add(dispatch.symbol());
            }
        }
        result.add(EXACT_CATCH_NULL_APPLY_SYMBOL);
        if (!program.materializedLambdaTargets().isEmpty()) {
            result.add(MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL);
            result.add(MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL);
            result.add(MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL);
            result.add(MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL);
        }
        return List.copyOf(result);
    }

    private static void emitRuntimeHelperPrototypes(final CodegenFeatures features, final StringBuilder c) {
        c.append("void javan_thread_run_target(void* target);").append(System.lineSeparator());
        if (features.generatedObjectClone()) {
            c.append("static void ")
                .append(GENERATED_OBJECT_CLONE_SYMBOL)
                .append("(void** result, void* value);")
                .append(System.lineSeparator());
        }
    }

    private static void emitGeneratedObjectHelpers(
        final IrProgram program,
        final CodegenFeatures features,
        final StringBuilder c
    ) {
        emitGeneratedObjectClassHelpers(program, c);
        if (features.generatedObjectClone()) {
            emitGeneratedObjectCloneHelpers(program, c);
        }
    }

    private static void emitStruct(final IrClass classInfo, final StringBuilder c) {
        c.append("struct ").append(classInfo.symbol()).append(" {").append(System.lineSeparator());
        c.append("    int _javan_type_id;").append(System.lineSeparator());
        c.append("    void* _javan_runtime_state;").append(System.lineSeparator());
        c.append("    int _javan_runtime_kind;").append(System.lineSeparator());
        c.append("    int _javan_runtime_reserved;").append(System.lineSeparator());
        if (classInfo.fields().isEmpty()) {
            c.append("    char _javan_empty;").append(System.lineSeparator());
        } else {
            for (final javan.ir.IrField field : classInfo.fields()) {
                c.append("    ")
                    .append(field.type().cName())
                    .append(' ')
                    .append(field.symbol())
                    .append(";")
                    .append(System.lineSeparator());
            }
        }
        c.append("};").append(System.lineSeparator());
    }

    private static void emitTypeDescriptors(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> ids = typeIds(program);
        for (final IrClass classInfo : program.classes()) {
            final List<javan.ir.IrField> objectFields = objectFields(classInfo.fields());
            if (objectFields.isEmpty()) {
                continue;
            }
            c.append("static unsigned long ")
                .append(typeFieldOffsetsSymbol(classInfo.jvmName()))
                .append("[] = {")
                .append(System.lineSeparator());
            for (int index = 0; index < objectFields.size(); index++) {
                c.append("    (unsigned long) offsetof(struct ")
                    .append(classInfo.symbol())
                    .append(", ")
                    .append(objectFields.get(index).symbol())
                    .append(")");
                if (index < objectFields.size() - 1) {
                    c.append(',');
                }
                c.append(System.lineSeparator());
            }
            c.append("};").append(System.lineSeparator());
        }
        if (!program.classes().isEmpty()) {
            c.append("static JavanTypeDescriptor javan_type_descriptors[] = {").append(System.lineSeparator());
            for (int index = 0; index < program.classes().size(); index++) {
                final IrClass classInfo = program.classes().get(index);
                final List<javan.ir.IrField> objectFields = objectFields(classInfo.fields());
                c.append("    {")
                    .append(ids.get(classInfo.jvmName()).intValue())
                    .append(", \"")
                    .append(escapeCString(displayClassName(classInfo.jvmName())))
                    .append("\", ")
                    .append(classInfo.enumClass() ? 1 : 0)
                    .append(", ")
                    .append(objectFields.size())
                    .append(", ");
                if (objectFields.isEmpty()) {
                    c.append("(unsigned long*) 0");
                } else {
                    c.append(typeFieldOffsetsSymbol(classInfo.jvmName()));
                }
                c.append("}");
                if (index < program.classes().size() - 1) {
                    c.append(',');
                }
                c.append(System.lineSeparator());
            }
            c.append("};").append(System.lineSeparator());
        }
        c.append("static void javan_register_generated_type_descriptors(void) {").append(System.lineSeparator());
        if (program.classes().isEmpty()) {
            c.append("    javan_register_type_descriptors((JavanTypeDescriptor*) 0, 0);").append(System.lineSeparator());
        } else {
            c.append("    javan_register_type_descriptors(javan_type_descriptors, ")
                .append(program.classes().size())
                .append(");")
                .append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator());
    }

    private static List<javan.ir.IrField> objectFields(final List<javan.ir.IrField> fields) {
        final List<javan.ir.IrField> result = new java.util.ArrayList<>();
        for (final javan.ir.IrField field : fields) {
            if (field.type() == javan.ir.IrType.OBJECT) {
                result.add(field);
            }
        }
        return List.copyOf(result);
    }

    private static boolean emitStaticFields(final IrProgram program, final StringBuilder c) {
        boolean emitted = false;
        for (final IrClass classInfo : program.classes()) {
            for (final javan.ir.IrField field : classInfo.staticFields()) {
                c.append("static ")
                    .append(field.type().cName())
                    .append(' ')
                    .append(staticFieldSymbol(classInfo.jvmName(), field.name()))
                    .append(" = 0;")
                    .append(System.lineSeparator());
                emitted = true;
            }
        }
        return emitted;
    }

    private static void emitStaticRootInventory(final IrProgram program, final StringBuilder c) {
        final List<String> roots = staticObjectRootSymbols(program);
        if (!roots.isEmpty()) {
            c.append("static void** javan_static_roots[] = {").append(System.lineSeparator());
            for (int index = 0; index < roots.size(); index++) {
                c.append("    (void**) &").append(roots.get(index));
                if (index < roots.size() - 1) {
                    c.append(',');
                }
                c.append(System.lineSeparator());
            }
            c.append("};").append(System.lineSeparator());
        }
        c.append("static void javan_register_generated_roots(void) {").append(System.lineSeparator());
        if (roots.isEmpty()) {
            c.append("    javan_register_static_roots((void***) 0, 0);").append(System.lineSeparator());
        } else {
            c.append("    javan_register_static_roots(javan_static_roots, ")
                .append(roots.size())
                .append(");")
                .append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator());
    }

    private static List<String> staticObjectRootSymbols(final IrProgram program) {
        final List<String> result = new java.util.ArrayList<>();
        for (final IrClass classInfo : program.classes()) {
            for (final javan.ir.IrField field : classInfo.staticFields()) {
                if (field.type() == javan.ir.IrType.OBJECT) {
                    result.add(staticFieldSymbol(classInfo.jvmName(), field.name()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void emitAllocators(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        for (final IrClass classInfo : program.classes()) {
            c.append("static void* ")
                .append(allocatorSymbol(classInfo.jvmName()))
                .append("(void) {")
                .append(System.lineSeparator());
            c.append("    struct ")
                .append(classInfo.symbol())
                .append("* object = (struct ")
                .append(classInfo.symbol())
                .append("*) javan_alloc(sizeof(struct ")
                .append(classInfo.symbol())
                .append("));")
                .append(System.lineSeparator());
            c.append("    object->_javan_type_id = ")
                .append(typeIds.get(classInfo.jvmName()).intValue())
                .append(";")
                .append(System.lineSeparator());
            c.append("    object->_javan_runtime_state = (void*) 0;").append(System.lineSeparator());
            c.append("    object->_javan_runtime_kind = 0;").append(System.lineSeparator());
            c.append("    object->_javan_runtime_reserved = 0;").append(System.lineSeparator());
            c.append("    javan_register_object((void*) object, ")
                .append(typeIds.get(classInfo.jvmName()).intValue())
                .append(");")
                .append(System.lineSeparator());
            c.append("    return (void*) object;").append(System.lineSeparator());
            c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        }
    }

    private static void emitEnumOrdinalHelpers(final IrProgram program, final StringBuilder c) {
        for (final IrClass classInfo : program.classes()) {
            if (classInfo.enumConstants().isEmpty()) {
                continue;
            }
            c.append("static int ")
                .append(enumOrdinalSymbol(classInfo.jvmName()))
                .append("(void* value) {")
                .append(System.lineSeparator());
            for (int index = 0; index < classInfo.enumConstants().size(); index++) {
                c.append("    if (javan_string_equals((const char*) value, \"")
                    .append(escapeCString(classInfo.enumConstants().get(index)))
                    .append("\")) { return ")
                    .append(index)
                    .append("; }")
                    .append(System.lineSeparator());
            }
            c.append("    javan_panic(\"invalid enum constant\");").append(System.lineSeparator());
            c.append("    return -1;").append(System.lineSeparator());
            c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        }
    }

    private static void emitGeneratedEnumOrdinalHelper(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        c.append("static int javan_generated_enum_ordinal(int enum_type_id, void* value) {").append(System.lineSeparator());
        c.append("    switch (enum_type_id) {").append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            if (classInfo.enumConstants().isEmpty()) {
                continue;
            }
            c.append("        case ").append(typeIds.get(classInfo.jvmName()).intValue()).append(":")
                .append(System.lineSeparator());
            for (int index = 0; index < classInfo.enumConstants().size(); index++) {
                c.append("            if (value == ")
                    .append(staticFieldSymbol(classInfo.jvmName(), classInfo.enumConstants().get(index)))
                    .append(") { return ")
                    .append(index)
                    .append("; }")
                    .append(System.lineSeparator());
            }
            c.append("            return -1;").append(System.lineSeparator());
        }
        c.append("        default: return -1;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitRecordShapeExactTypeHelper(final IrProgram program, final StringBuilder c) {
        if (!hasRecordShapeEnumResolver(program)) {
            return;
        }
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        c.append("static int javan_generated_record_shape_exact_type(void* value, int expected_type_id) {")
            .append(System.lineSeparator());
        c.append("    if (value == 0) { return 1; }").append(System.lineSeparator());
        c.append("    switch (expected_type_id) {").append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            if (classInfo.enumConstants().isEmpty() || !recordShapeUsesOwner(program, classInfo.jvmName())) {
                continue;
            }
            c.append("        case ")
                .append(typeIds.get(classInfo.jvmName()).intValue())
                .append(": return ");
            for (int index = 0; index < classInfo.enumConstants().size(); index++) {
                if (index > 0) {
                    c.append(" || ");
                }
                c.append("value == ")
                    .append(staticFieldSymbol(classInfo.jvmName(), classInfo.enumConstants().get(index)));
            }
            c.append(";").append(System.lineSeparator());
        }
        c.append("        default: return 0;")
            .append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static boolean hasRecordShapeEnumResolver(final IrProgram program) {
        for (final IrClass classInfo : program.classes()) {
            if (!classInfo.enumConstants().isEmpty() && recordShapeUsesOwner(program, classInfo.jvmName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean recordShapeUsesOwner(final IrProgram program, final String owner) {
        for (final IrDispatch dispatch : program.dispatches()) {
            if (!RECORD_REFERENCE_EQUALS_DISPATCH.equals(dispatch.symbol())
                && !RECORD_REFERENCE_HASH_CODE_DISPATCH.equals(dispatch.symbol())) {
                continue;
            }
            for (final IrDispatchTarget target : dispatch.targets()) {
                if (owner.equals(target.owner())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void emitExactEnumLookupHelpers(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        c.append("static void* ").append(GENERATED_ENUM_BY_NAME_SYMBOL).append("(void* class_value, void* name_value) {").append(System.lineSeparator());
        c.append("    void* printable = javan_printable_object_string(name_value);").append(System.lineSeparator());
        c.append("    switch (javan_class_exact_type_id(class_value)) {").append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            if (classInfo.enumConstants().isEmpty()) {
                continue;
            }
            c.append("        case ").append(typeIds.get(classInfo.jvmName()).intValue()).append(":").append(System.lineSeparator());
            for (final String constant : classInfo.enumConstants()) {
                c.append("            if (javan_string_equals((const char*) printable, ")
                    .append(emitCStringLiteral(constant))
                    .append(")) { return ")
                    .append(staticFieldSymbol(classInfo.jvmName(), constant))
                    .append("; }")
                    .append(System.lineSeparator());
            }
            c.append("            return 0;").append(System.lineSeparator());
        }
        c.append("        default: return 0;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(GENERATED_ENUM_BY_ORDINAL_SYMBOL).append("(void* class_value, int ordinal) {").append(System.lineSeparator());
        c.append("    switch (javan_class_exact_type_id(class_value)) {").append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            if (classInfo.enumConstants().isEmpty()) {
                continue;
            }
            c.append("        case ").append(typeIds.get(classInfo.jvmName()).intValue()).append(":").append(System.lineSeparator());
            c.append("            switch (ordinal) {").append(System.lineSeparator());
            for (int index = 0; index < classInfo.enumConstants().size(); index++) {
                final String constant = classInfo.enumConstants().get(index);
                c.append("                case ").append(index).append(": return ")
                    .append(staticFieldSymbol(classInfo.jvmName(), constant))
                    .append(";")
                    .append(System.lineSeparator());
            }
            c.append("                default: return 0;").append(System.lineSeparator());
            c.append("            }").append(System.lineSeparator());
        }
        c.append("        default: return 0;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(EXACT_ENUM_LOOKUP_SYMBOL).append("(void* value, void* class_value) {").append(System.lineSeparator());
        c.append("    if (javan_is_supported_number(value) != 0) {").append(System.lineSeparator());
        c.append("        return ").append(GENERATED_ENUM_BY_ORDINAL_SYMBOL)
            .append("(class_value, javan_number_int_value(value));")
            .append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return ").append(GENERATED_ENUM_BY_NAME_SYMBOL).append("(class_value, value);").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitExactFunctionOrNullHelpers(final IrProgram program, final StringBuilder c) {
        final java.util.Set<String> bridgeOwners = new java.util.LinkedHashSet<>();
        for (final IrFunction function : program.functions()) {
            if ("apply".equals(function.name()) && isExactCatchNullBridge(function)) {
                bridgeOwners.add(function.owner());
            }
        }
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        final List<IrFunction> concreteTargets = new java.util.ArrayList<>();
        for (final IrFunction function : program.functions()) {
            if (bridgeOwners.contains(function.owner())) {
                continue;
            }
            if (!FALLIBLE_APPLY_METHOD_NAME.equals(function.name())) {
                continue;
            }
            if (!hasUnaryReferenceObjectShape(function)) {
                continue;
            }
            concreteTargets.add(function);
        }
        c.append("static void ").append(EXACT_CATCH_NULL_APPLY_SYMBOL).append("(void** result, void* self, void* arg) {").append(System.lineSeparator());
        c.append("    if (result == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"invalid catch-null function result\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    if (self == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"catch-null function receiver is null\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    struct javan_object_header* header = (struct javan_object_header*) self;").append(System.lineSeparator());
        if (!program.materializedLambdaTargets().isEmpty()) {
            c.append("    if (header->_javan_type_id == 0) {").append(System.lineSeparator());
            c.append("        if (header->_javan_runtime_kind != JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA) {").append(System.lineSeparator());
            c.append("            javan_panic(\"unsupported catch-null function receiver shape\");").append(System.lineSeparator());
            c.append("        }").append(System.lineSeparator());
            emitRecoverableFunctionOrNullCall(c, MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL, List.of("self", "arg"));
            c.append("    }").append(System.lineSeparator());
        } else {
            c.append("    if (header->_javan_type_id == 0) {").append(System.lineSeparator());
            c.append("        javan_panic(\"unsupported catch-null function receiver shape\");").append(System.lineSeparator());
            c.append("    }").append(System.lineSeparator());
        }
        c.append("    switch (header->_javan_type_id) {").append(System.lineSeparator());
        for (final IrFunction target : concreteTargets) {
            final Integer typeId = typeIds.get(target.owner());
            if (typeId == null) {
                continue;
            }
            c.append("        case ").append(typeId.intValue()).append(": {").append(System.lineSeparator());
            emitRecoverableFunctionOrNullCall(c, target.symbol(), List.of("self", "arg"), 12);
            c.append("        }").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported catch-null function receiver shape\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static boolean isExactCatchNullBridge(final IrFunction function) {
        if (!hasUnaryReferenceObjectShape(function)) {
            return false;
        }
        if (function.instructions().size() != 1) {
            return false;
        }
        final IrInstruction instruction = function.instructions().getFirst();
        if (instruction.op() != IrInstruction.Op.RETURN_OBJECT || instruction.expression().isEmpty()) {
            return false;
        }
        final IrExpression expression = instruction.expression().orElseThrow();
        return expression.kind() == IrExpression.Kind.CALL
            && EXACT_CATCH_NULL_APPLY_SYMBOL.equals(expression.value());
    }

    private static boolean hasUnaryReferenceObjectShape(final IrFunction function) {
        return function.parameters().size() == 2
            && function.parameters().get(0).type() == IrType.OBJECT
            && function.parameters().get(1).type() == IrType.OBJECT
            && function.returnType() == IrType.OBJECT;
    }

    private static void emitExactTemporalBridgeHelpers(final StringBuilder c) {
        c.append("static void* ").append(EXACT_TEMPORAL_OF_UNSUPPORTED_SYMBOL)
            .append("(void* class_value, void* text, void* function) {").append(System.lineSeparator());
        c.append("    (void) text;").append(System.lineSeparator());
        c.append("    (void) function;").append(System.lineSeparator());
        c.append("    const char* class_name = (const char*) javan_printable_object_string(class_value);").append(System.lineSeparator());
        c.append("    char message[256];").append(System.lineSeparator());
        c.append("    if (class_name != 0) {").append(System.lineSeparator());
        c.append("        snprintf(message, sizeof(message), \"unsupported exact temporal conversion runtime for %s\", class_name);").append(System.lineSeparator());
        c.append("    } else {").append(System.lineSeparator());
        c.append("        snprintf(message, sizeof(message), \"unsupported exact temporal conversion runtime\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    javan_panic(message);").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(EXACT_TEMPORAL_STRING_BRIDGE_UNSUPPORTED_SYMBOL)
            .append("(void* text, void* target_name) {").append(System.lineSeparator());
        c.append("    (void) text;").append(System.lineSeparator());
        c.append("    const char* target = (const char*) javan_printable_object_string(target_name);").append(System.lineSeparator());
        c.append("    char message[256];").append(System.lineSeparator());
        c.append("    if (target != 0) {").append(System.lineSeparator());
        c.append("        snprintf(message, sizeof(message), \"unsupported exact temporal registration bridge runtime for %s\", target);").append(System.lineSeparator());
        c.append("    } else {").append(System.lineSeparator());
        c.append("        snprintf(message, sizeof(message), \"unsupported exact temporal registration bridge runtime\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    javan_panic(message);").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(EXACT_CALENDAR_OF_MILLIS_UNSUPPORTED_SYMBOL)
            .append("(long long value) {").append(System.lineSeparator());
        c.append("    (void) value;").append(System.lineSeparator());
        c.append("    javan_panic(\"unsupported exact Calendar conversion runtime from epoch millis\");").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(EXACT_CALENDAR_OF_DATE_UNSUPPORTED_SYMBOL)
            .append("(void* value) {").append(System.lineSeparator());
        c.append("    (void) value;").append(System.lineSeparator());
        c.append("    javan_panic(\"unsupported exact Calendar conversion runtime from java.util.Date\");").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(EXACT_CALENDAR_OF_LOCAL_TIME_UNSUPPORTED_SYMBOL)
            .append("(void* value) {").append(System.lineSeparator());
        c.append("    (void) value;").append(System.lineSeparator());
        c.append("    javan_panic(\"unsupported exact Calendar conversion runtime from java.time.LocalTime\");").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(EXACT_THROWABLE_STRING_OF_UNSUPPORTED_SYMBOL)
            .append("(void* value) {").append(System.lineSeparator());
        c.append("    (void) value;").append(System.lineSeparator());
        c.append("    javan_panic(\"unsupported exact Throwable string rendering runtime\");").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void* ").append(TEMPORAL_CONVERSION_LAMBDA_UNSUPPORTED_SYMBOL)
            .append("(void* method_display) {").append(System.lineSeparator());
        c.append("    char message[512];").append(System.lineSeparator());
        c.append("    const char* method_text = (const char*) javan_printable_object_string(method_display);").append(System.lineSeparator());
        c.append("    snprintf(message, sizeof(message), \"unsupported temporal conversion lambda runtime: %s\", method_text);")
            .append(System.lineSeparator());
        c.append("    javan_panic(message);").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitRecoverableFunctionOrNullCall(
        final StringBuilder c,
        final String symbol,
        final List<String> arguments
    ) {
        emitRecoverableFunctionOrNullCall(c, symbol, arguments, 8);
    }

    private static void emitRecoverableFunctionOrNullCall(
        final StringBuilder c,
        final String symbol,
        final List<String> arguments,
        final int indent
    ) {
        final StringBuilder paddingBuilder = new StringBuilder();
        for (int index = 0; index < indent; index++) {
            paddingBuilder.append(' ');
        }
        final String padding = paddingBuilder.toString();
        final StringBuilder argumentsBuilder = new StringBuilder();
        for (int index = 0; index < arguments.size(); index++) {
            if (index > 0) {
                argumentsBuilder.append(", ");
            }
            argumentsBuilder.append(arguments.get(index));
        }
        c.append(padding).append("JavanPanicScope javan_function_or_null_scope;").append(System.lineSeparator());
        c.append(padding).append("jmp_buf javan_function_or_null_target;").append(System.lineSeparator());
        c.append(padding).append("void* javan_function_or_null_result = 0;").append(System.lineSeparator());
        c.append(padding).append("void** javan_function_or_null_roots[] = {").append(System.lineSeparator());
        c.append(padding).append("    (void**) &javan_function_or_null_result").append(System.lineSeparator());
        c.append(padding).append("};").append(System.lineSeparator());
        c.append(padding).append("javan_root_frame_push(javan_function_or_null_roots, 1);").append(System.lineSeparator());
        c.append(padding).append("javan_panic_scope_push(&javan_function_or_null_scope, &javan_function_or_null_target);").append(System.lineSeparator());
        c.append(padding).append("if (setjmp(javan_function_or_null_target) != 0) {").append(System.lineSeparator());
        c.append(padding).append("    javan_root_frame_pop(javan_function_or_null_roots);").append(System.lineSeparator());
        c.append(padding).append("    javan_clear_error();").append(System.lineSeparator());
        c.append(padding).append("    return;").append(System.lineSeparator());
        c.append(padding).append("}").append(System.lineSeparator());
        c.append(padding).append(symbol)
            .append("((void**) &javan_function_or_null_result")
            .append(argumentsBuilder.length() == 0 ? "" : ", ")
            .append(argumentsBuilder.toString())
            .append(");")
            .append(System.lineSeparator());
        c.append(padding).append("if (javan_pending_has() != 0) {").append(System.lineSeparator());
        c.append(padding).append("    if (javan_pending_type_assignable_to((void*) \"java/lang/Exception\") != 0) {")
            .append(System.lineSeparator());
        c.append(padding).append("        javan_pending_clear();").append(System.lineSeparator());
        c.append(padding).append("        javan_panic_scope_pop(&javan_function_or_null_scope);")
            .append(System.lineSeparator());
        c.append(padding).append("        javan_root_frame_pop(javan_function_or_null_roots);")
            .append(System.lineSeparator());
        c.append(padding).append("        return;").append(System.lineSeparator());
        c.append(padding).append("    }").append(System.lineSeparator());
        c.append(padding).append("    javan_panic_scope_pop(&javan_function_or_null_scope);")
            .append(System.lineSeparator());
        c.append(padding).append("    javan_root_frame_pop(javan_function_or_null_roots);")
            .append(System.lineSeparator());
        c.append(padding).append("    javan_pending_panic();").append(System.lineSeparator());
        c.append(padding).append("    return;").append(System.lineSeparator());
        c.append(padding).append("}").append(System.lineSeparator());
        c.append(padding).append("javan_panic_scope_pop(&javan_function_or_null_scope);").append(System.lineSeparator());
        c.append(padding).append("javan_runtime_lock_enter();").append(System.lineSeparator());
        c.append(padding).append("*result = javan_function_or_null_result;").append(System.lineSeparator());
        c.append(padding).append("javan_runtime_lock_leave();").append(System.lineSeparator());
        c.append(padding).append("javan_root_frame_pop(javan_function_or_null_roots);").append(System.lineSeparator());
        c.append(padding).append("return;").append(System.lineSeparator());
    }

    private static void emitGeneratedObjectClassHelpers(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        c.append("void* javan_generated_object_get_class(void* value) {").append(System.lineSeparator());
        c.append("    if (value == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"null object has no class\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    switch (((struct javan_object_header*) value)->_javan_type_id) {").append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            final int typeId = typeIds.get(classInfo.jvmName()).intValue();
            c.append("        case ").append(typeId).append(": return javan_runtime_class_literal(")
                .append(emitCStringLiteral(displayClassName(classInfo.jvmName())))
                .append(", ")
                .append(typeId)
                .append(", ")
                .append(classInfo.enumClass() ? 1 : 0)
                .append(", 0, 1, ")
                .append(typeId)
                .append(");")
                .append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported generated object type\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitDispatch(
        final IrProgram program,
        final IrDispatch dispatch,
        final StringBuilder c
    ) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        emitDispatchSignature(dispatch, c);
        c.append(" {").append(System.lineSeparator());
        c.append("    if (self == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"null dispatch\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        if (!RECORD_REFERENCE_EQUALS_DISPATCH.equals(dispatch.symbol())
            && !RECORD_REFERENCE_HASH_CODE_DISPATCH.equals(dispatch.symbol())) {
            for (final IrDispatchTarget target : dispatch.targets()) {
                final String constant = program.enumDispatchConstants().get(target.owner());
                if (constant == null) {
                    continue;
                }
                c.append("    if (javan_string_equals((const char*) javan_printable_object_string(self), ")
                    .append(emitCStringLiteral(constant))
                    .append(") != 0) {").append(System.lineSeparator());
                if (dispatch.returnType() == javan.ir.IrType.VOID) {
                    c.append("        ").append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append("); return;");
                } else if (dispatch.returnType() == javan.ir.IrType.OBJECT) {
                    c.append("        ").append(target.functionSymbol()).append("(").append(dispatchResultArguments(dispatch)).append("); return;");
                } else {
                    c.append("        return ").append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append(");");
                }
                c.append(System.lineSeparator()).append("    }").append(System.lineSeparator());
            }
        }
        c.append("    switch (((struct javan_object_header*) self)->_javan_type_id) {").append(System.lineSeparator());
        for (final IrDispatchTarget target : dispatch.targets()) {
            c.append("        case ")
                .append(typeIds.get(target.owner()).intValue())
                .append(": ");
            if (dispatch.returnType() == javan.ir.IrType.VOID) {
                c.append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append("); return;");
            } else if (dispatch.returnType() == javan.ir.IrType.OBJECT) {
                c.append(target.functionSymbol()).append("(").append(dispatchResultArguments(dispatch)).append("); return;");
            } else {
                c.append("return ").append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append(");");
            }
            c.append(System.lineSeparator());
        }
        if (RECORD_REFERENCE_EQUALS_DISPATCH.equals(dispatch.symbol())) {
            c.append("        default: return javan_record_reference_identity_equals(self, arg0);")
                .append(System.lineSeparator());
        } else if (RECORD_REFERENCE_HASH_CODE_DISPATCH.equals(dispatch.symbol())) {
            c.append("        default: return javan_record_reference_identity_hash_code(self);")
                .append(System.lineSeparator());
        } else {
            c.append("        default: javan_panic(\"unsupported dispatch target\");").append(System.lineSeparator());
        }
        c.append("    }").append(System.lineSeparator());
        emitDefaultReturn(dispatch.returnType(), c);
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitThreadHelpers(final IrProgram program, final StringBuilder c) {
        c.append("void javan_thread_run_target(void* target) {").append(System.lineSeparator());
        c.append("    if (target == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"Thread.start target is null\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        if (hasDispatch(program, RUNNABLE_RUN_DISPATCH_SYMBOL)) {
            c.append("    ").append(RUNNABLE_RUN_DISPATCH_SYMBOL).append("(target);").append(System.lineSeparator());
        } else {
            c.append("    javan_panic(\"Thread.start with Runnable target has no closed-world Runnable.run implementation\");").append(System.lineSeparator());
        }
        c.append("    if (javan_pending_has() != 0) {").append(System.lineSeparator());
        c.append("        javan_pending_panic();").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitMaterializedLambdaHelpers(
        final IrProgram program,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        if (program.materializedLambdaTargets().isEmpty()) {
            return;
        }
        c.append("static void ").append(MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL).append("(void** result, void* self, void* arg) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (target.booleanResult() || target.voidResult()) {
                continue;
            }
            if (!materializedLambdaSingleObjectArgument(target)) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": ");
            emitMaterializedLambdaInvocation(c, target, "result", "self", List.of("arg"), nativeWrapperSymbols);
            c.append("; return;").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized object lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void ").append(MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL).append("(void** result, void* self, int64_t arg) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (target.booleanResult() || target.voidResult() || !materializedLambdaSingleLongArgument(target)) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": ");
            emitMaterializedLambdaInvocation(c, target, "result", "self", List.of("arg"), nativeWrapperSymbols);
            c.append("; return;").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized long object lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void ").append(MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL).append("(void** result, void* self) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (target.booleanResult()
                || target.voidResult()
                || materializedLambdaArity(target) != 0
                || !"java/util/function/Supplier".equals(target.interfaceOwner())
                || !"get".equals(target.interfaceMethodName())
                || !"()Ljava/lang/Object;".equals(target.interfaceMethodDescriptor())) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": ");
            emitMaterializedLambdaInvocation(c, target, "result", "self", List.of(), nativeWrapperSymbols);
            c.append("; return;").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized supplier lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void ").append(MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL).append("(void** result, void* self, void* first_arg, void* second_arg) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (target.booleanResult() || target.voidResult()) {
                continue;
            }
            if (materializedLambdaArity(target) != 2) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": ");
            emitMaterializedLambdaInvocation(c, target, "result", "self", List.of("first_arg", "second_arg"), nativeWrapperSymbols);
            c.append("; return;").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized two-argument object lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static int ").append(MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL).append("(void* self, void* arg) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (!target.booleanResult() || target.voidResult()) {
                continue;
            }
            if (materializedLambdaArity(target) != 1) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": return ");
            emitMaterializedLambdaInvocation(c, target, "", "self", List.of("arg"), nativeWrapperSymbols);
            c.append(";").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized boolean lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    return 0;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void ").append(MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL).append("(void* self, void* arg) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (!target.voidResult()) {
                continue;
            }
            if (materializedLambdaArity(target) != 1) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": ");
            emitMaterializedLambdaInvocation(c, target, "", "self", List.of("arg"), nativeWrapperSymbols);
            c.append("; return;").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized void lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());

        c.append("static void ").append(MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL).append("(void* self, void* first_arg, void* second_arg) {")
            .append(System.lineSeparator());
        c.append("    switch (javan_materialized_lambda_target_id(self)) {").append(System.lineSeparator());
        for (final IrMaterializedLambdaTarget target : program.materializedLambdaTargets()) {
            if (!target.voidResult()) {
                continue;
            }
            if (materializedLambdaArity(target) != 2) {
                continue;
            }
            c.append("        case ").append(target.targetId()).append(": ");
            emitMaterializedLambdaInvocation(c, target, "", "self", List.of("first_arg", "second_arg"), nativeWrapperSymbols);
            c.append("; return;").append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported materialized two-argument void lambda target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitMaterializedLambdaInvocation(
        final StringBuilder c,
        final IrMaterializedLambdaTarget target,
        final String resultExpression,
        final String selfExpression,
        final List<String> argumentExpressions,
        final NativeWrapperSymbols nativeWrapperSymbols
    ) {
        c.append(nativeWrapperSymbols.resolve(target.functionSymbol())).append("(");
        boolean first = resultExpression.length() == 0;
        if (!first) {
            c.append(resultExpression);
        }
        for (int index = 0; index < target.captureCount(); index++) {
            if (!first) {
                c.append(", ");
            }
            c.append("javan_materialized_lambda_capture(").append(selfExpression).append(", ").append(index).append(")");
            first = false;
        }
        if (!first && !argumentExpressions.isEmpty()) {
            c.append(", ");
        }
        for (int index = 0; index < argumentExpressions.size(); index++) {
            if (index > 0) {
                c.append(", ");
            }
            c.append(argumentExpressions.get(index));
        }
        c.append(")");
    }

    private static int materializedLambdaArity(final IrMaterializedLambdaTarget target) {
        return MethodDescriptor.parse(target.interfaceMethodDescriptor()).parameterTypes().size();
    }

    private static boolean materializedLambdaSingleObjectArgument(final IrMaterializedLambdaTarget target) {
        final List<IrType> parameterTypes = MethodDescriptor.parse(target.interfaceMethodDescriptor()).parameterTypes();
        return parameterTypes.size() == 1 && parameterTypes.getFirst() == IrType.OBJECT;
    }

    private static boolean materializedLambdaSingleLongArgument(final IrMaterializedLambdaTarget target) {
        final List<IrType> parameterTypes = MethodDescriptor.parse(target.interfaceMethodDescriptor()).parameterTypes();
        return parameterTypes.size() == 1 && parameterTypes.getFirst() == IrType.LONG;
    }

    private static void emitFunction(
        final IrProgram program,
        final IrFunction function,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c,
        final boolean emitMain
    ) {
        final boolean entry = appEntry(emitMain, function, program);
        if (entry) {
            c.append("int main(int argc, char** argv) {").append(System.lineSeparator());
            c.append("    javan_runtime_set_executable_path(argc > 0 ? argv[0] : NULL);").append(System.lineSeparator());
            c.append("    javan_runtime_validate_floating_layout();").append(System.lineSeparator());
            c.append("    javan_runtime_profile_consume_args(&argc, &argv);").append(System.lineSeparator());
            c.append("    (void) argc;").append(System.lineSeparator());
            c.append("    (void) argv;").append(System.lineSeparator());
            emitEntryParameters(function, c);
        } else {
            emitSignature(function, c, true);
            c.append(" {").append(System.lineSeparator());
        }
        for (final javan.ir.IrLocal local : function.locals()) {
            c.append("    ").append(local.type().cName()).append(' ').append(local.name()).append(" = 0;").append(System.lineSeparator());
        }
        final List<String> rootNames = objectRootNames(function);
        final String rootFrameSymbol = rootFrameSymbol(function);
        final RootLivenessPlan rootLiveness = RootLivenessPlan.forFunction(function);
        emitRootFramePush(rootFrameSymbol, rootNames, c);
        if (entry) {
            c.append("    javan_register_generated_type_descriptors();").append(System.lineSeparator());
            c.append("    javan_register_generated_roots();").append(System.lineSeparator());
            c.append("    javan_register_enum_ordinal_resolver(javan_generated_enum_ordinal);")
                .append(System.lineSeparator());
            emitRecordReferenceObjectMethodResolverRegistration(program, c);
            emitClassInitializers(program, nativeWrapperSymbols, c);
            c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        } else {
            c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        }
        for (int index = 0; index < function.instructions().size(); index++) {
            final IrInstruction instruction = function.instructions().get(index);
            emitInstruction(
                index,
                instruction,
                entry,
                function.returnType(),
                rootFrameSymbol,
                !rootNames.isEmpty(),
                objectResultSymbols,
                nativeWrapperSymbols,
                c
            );
            if (hasStatementSafePoint(instruction)) {
                rootLiveness.emitClearsAfter(index, c);
            }
            emitStatementSafePoint(instruction, c);
        }
        if (entry) {
            c.append("javan_entry_epilogue:").append(System.lineSeparator());
            c.append("    javan_wait_for_non_current_threads();").append(System.lineSeparator());
            emitRootFramePop(rootFrameSymbol, !rootNames.isEmpty(), c);
            c.append("    return 0;").append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static boolean hasDispatch(final IrProgram program, final String symbol) {
        for (final IrDispatch dispatch : program.dispatches()) {
            if (dispatch.symbol().equals(symbol)) {
                return true;
            }
        }
        return false;
    }

    private static void emitRecordReferenceObjectMethodResolverRegistration(
        final IrProgram program,
        final StringBuilder c
    ) {
        if (!hasDispatch(program, RECORD_REFERENCE_EQUALS_DISPATCH)
            && !hasDispatch(program, RECORD_REFERENCE_HASH_CODE_DISPATCH)) {
            return;
        }
        c.append("    javan_register_record_object_method_resolvers(")
            .append(hasDispatch(program, RECORD_REFERENCE_EQUALS_DISPATCH)
                ? RECORD_REFERENCE_EQUALS_DISPATCH
                : "(int (*)(void*, void*)) 0")
            .append(", ")
            .append(hasDispatch(program, RECORD_REFERENCE_HASH_CODE_DISPATCH)
                ? RECORD_REFERENCE_HASH_CODE_DISPATCH
                : "(int (*)(void*)) 0")
            .append(", ")
            .append(hasRecordShapeEnumResolver(program)
                ? "javan_generated_record_shape_exact_type"
                : "(int (*)(void*, int)) 0")
            .append(");")
            .append(System.lineSeparator());
    }

    private static boolean appEntry(final boolean emitMain, final IrFunction function, final IrProgram program) {
        if (!emitMain) {
            return false;
        }
        if (!function.symbol().equals(program.entryFunction())) {
            return false;
        }
        return true;
    }

    private static void emitLibraryInitializer(
        final IrProgram program,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        c.append("static int javan_library_initialized = 0;").append(System.lineSeparator());
        c.append("static void javan_library_init(void) {").append(System.lineSeparator());
        c.append("    if (javan_library_initialized != 0) {").append(System.lineSeparator());
        c.append("        return;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    javan_runtime_validate_floating_layout();").append(System.lineSeparator());
        c.append("    javan_register_generated_type_descriptors();").append(System.lineSeparator());
        c.append("    javan_register_generated_roots();").append(System.lineSeparator());
        c.append("    javan_register_enum_ordinal_resolver(javan_generated_enum_ordinal);")
            .append(System.lineSeparator());
        emitRecordReferenceObjectMethodResolverRegistration(program, c);
        emitClassInitializers(program, nativeWrapperSymbols, c);
        c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        c.append("    javan_library_initialized = 1;").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitExportWrapper(
        final ExportedMethod export,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        emitExportSignature(export, c);
        c.append(" {").append(System.lineSeparator());
        c.append("    jmp_buf javan_export_panic_target;").append(System.lineSeparator());
        c.append("    javan_panic_set_target(&javan_export_panic_target);").append(System.lineSeparator());
        c.append("    if (setjmp(javan_export_panic_target) != 0) {").append(System.lineSeparator());
        c.append("        javan_panic_clear_target(&javan_export_panic_target);").append(System.lineSeparator());
        emitExportWrapperDefaultReturn(export.returnType(), c);
        c.append("    }").append(System.lineSeparator());
        c.append("    javan_library_init();").append(System.lineSeparator());
        final List<Integer> objectArguments = objectExportArgumentIndexes(export);
        final AbiType returnType = export.returnType();
        final boolean objectReturn = returnType == AbiType.STRING
            || returnType == AbiType.BYTE_ARRAY
            || returnType == AbiType.OBJECT;
        for (final int index : objectArguments) {
            c.append("    void* ")
                .append(convertedExportArgumentName(export.parameterTypes().get(index), index))
                .append(" = 0;")
                .append(System.lineSeparator());
        }
        if (objectReturn) {
            c.append("    void* javan_export_object_result = 0;").append(System.lineSeparator());
        }
        emitExportWrapperRootFramePush(export, objectArguments, objectReturn, c);
        for (final int index : objectArguments) {
            final AbiType type = export.parameterTypes().get(index);
            c.append("    javan_runtime_lock_enter();").append(System.lineSeparator());
            if (type == AbiType.STRING) {
                c.append("    arg").append(index).append("_string = javan_string_from(arg")
                    .append(index)
                    .append(");")
                    .append(System.lineSeparator());
            } else if (type == AbiType.BYTE_ARRAY) {
                c.append("    arg").append(index).append("_array = javan_byte_array_from(arg")
                    .append(index)
                    .append(".data, arg")
                    .append(index)
                    .append(".length);")
                    .append(System.lineSeparator());
            }
            c.append("    javan_runtime_lock_leave();").append(System.lineSeparator());
        }
        final String internalSymbol = nativeWrapperSymbols.resolve(export.internalSymbol());
        final String call = internalSymbol + "(" + exportArguments(export) + ")";
        final String objectCall = internalSymbol
            + "((void**) &javan_export_object_result"
            + (export.parameterTypes().isEmpty() ? "" : ", " + exportArguments(export))
            + ")";
        if (returnType == AbiType.VOID) {
            c.append("    ").append(call).append(";").append(System.lineSeparator());
            emitExportWrapperCleanup(objectArguments, objectReturn, c);
            c.append("    javan_panic_clear_target(&javan_export_panic_target);").append(System.lineSeparator());
        } else if (returnType == AbiType.STRING) {
            c.append("    ").append(objectCall).append(";").append(System.lineSeparator());
            c.append("    char* javan_export_result = javan_string_export((const char*) javan_export_object_result);").append(System.lineSeparator());
            emitExportWrapperCleanup(objectArguments, objectReturn, c);
            c.append("    javan_panic_clear_target(&javan_export_panic_target);").append(System.lineSeparator());
            c.append("    return javan_export_result;").append(System.lineSeparator());
        } else if (returnType == AbiType.BYTE_ARRAY) {
            c.append("    ").append(objectCall).append(";").append(System.lineSeparator());
            c.append("    JavanByteArray javan_export_result = javan_byte_array_export(javan_export_object_result);").append(System.lineSeparator());
            emitExportWrapperCleanup(objectArguments, objectReturn, c);
            c.append("    javan_panic_clear_target(&javan_export_panic_target);").append(System.lineSeparator());
            c.append("    return javan_export_result;").append(System.lineSeparator());
        } else if (returnType == AbiType.OBJECT) {
            c.append("    ").append(objectCall).append(";").append(System.lineSeparator());
            c.append("    JavanObjectHandle* javan_export_result = javan_object_handle_new(javan_export_object_result);").append(System.lineSeparator());
            emitExportWrapperCleanup(objectArguments, objectReturn, c);
            c.append("    javan_panic_clear_target(&javan_export_panic_target);").append(System.lineSeparator());
            c.append("    return javan_export_result;").append(System.lineSeparator());
        } else {
            c.append("    ").append(returnType.cReturnName()).append(" javan_export_result = ").append(call).append(";").append(System.lineSeparator());
            emitExportWrapperCleanup(objectArguments, objectReturn, c);
            c.append("    javan_panic_clear_target(&javan_export_panic_target);").append(System.lineSeparator());
            c.append("    return javan_export_result;").append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitExportWrapperDefaultReturn(final AbiType type, final StringBuilder c) {
        if (type == AbiType.VOID) {
            c.append("        return;").append(System.lineSeparator());
        } else if (type == AbiType.STRING) {
            c.append("        return NULL;").append(System.lineSeparator());
        } else if (type == AbiType.BYTE_ARRAY) {
            c.append("        JavanByteArray javan_export_error_result;").append(System.lineSeparator());
            c.append("        javan_export_error_result.data = NULL;").append(System.lineSeparator());
            c.append("        javan_export_error_result.length = 0;").append(System.lineSeparator());
            c.append("        return javan_export_error_result;").append(System.lineSeparator());
        } else if (type == AbiType.OBJECT) {
            c.append("        return NULL;").append(System.lineSeparator());
        } else if (type == AbiType.LONG) {
            c.append("        return 0LL;").append(System.lineSeparator());
        } else if (type == AbiType.FLOAT) {
            c.append("        return 0.0f;").append(System.lineSeparator());
        } else if (type == AbiType.DOUBLE) {
            c.append("        return 0.0;").append(System.lineSeparator());
        } else {
            c.append("        return 0;").append(System.lineSeparator());
        }
    }

    private static void emitResultWrapper(final ExportedMethod export, final StringBuilder c) {
        emitResultWrapperSignature(export, c);
        c.append(" {").append(System.lineSeparator());
        if (export.returnType() != AbiType.VOID) {
            c.append("    if (out == NULL) {").append(System.lineSeparator());
            c.append("        return javan_result_error_message(\"JAVAN-ABI-NULL-OUT\", \"invalid native ABI call\", \"result output pointer is null\");").append(System.lineSeparator());
            c.append("    }").append(System.lineSeparator());
            emitResultWrapperDefaultOut(export.returnType(), c);
        }
        final String call = export.symbol() + "(" + rawExportArguments(export) + ")";
        if (export.returnType() == AbiType.VOID) {
            c.append("    ").append(call).append(";").append(System.lineSeparator());
        } else {
            c.append("    ").append(export.returnType().cReturnName()).append(" javan_try_value = ").append(call).append(";").append(System.lineSeparator());
        }
        c.append("    if (javan_last_error() != NULL) {").append(System.lineSeparator());
        c.append("        return javan_result_error_from_last_error();").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        if (export.returnType() != AbiType.VOID) {
            c.append("    *out = javan_try_value;").append(System.lineSeparator());
        }
        c.append("    return javan_result_ok();").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitResultWrapperSignature(final ExportedMethod export, final StringBuilder c) {
        c.append("JavanResult ").append(export.trySymbol()).append('(');
        boolean emitted = false;
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            if (emitted) {
                c.append(", ");
            }
            final AbiType type = export.parameterTypes().get(index);
            c.append(type.cName()).append(" arg").append(index);
            emitted = true;
        }
        if (export.returnType() != AbiType.VOID) {
            if (emitted) {
                c.append(", ");
            }
            c.append(export.returnType().cReturnName()).append("* out");
            emitted = true;
        }
        if (!emitted) {
            c.append("void");
        }
        c.append(')');
    }

    private static void emitResultWrapperDefaultOut(final AbiType type, final StringBuilder c) {
        if (type == AbiType.STRING) {
            c.append("    *out = NULL;").append(System.lineSeparator());
        } else if (type == AbiType.BYTE_ARRAY) {
            c.append("    out->data = NULL;").append(System.lineSeparator());
            c.append("    out->length = 0;").append(System.lineSeparator());
        } else if (type == AbiType.OBJECT) {
            c.append("    *out = NULL;").append(System.lineSeparator());
        } else if (type == AbiType.LONG) {
            c.append("    *out = 0LL;").append(System.lineSeparator());
        } else if (type == AbiType.FLOAT) {
            c.append("    *out = 0.0f;").append(System.lineSeparator());
        } else if (type == AbiType.DOUBLE) {
            c.append("    *out = 0.0;").append(System.lineSeparator());
        } else {
            c.append("    *out = 0;").append(System.lineSeparator());
        }
    }

    private static List<Integer> objectExportArgumentIndexes(final ExportedMethod export) {
        final List<Integer> result = new java.util.ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            final AbiType type = export.parameterTypes().get(index);
            if (type == AbiType.STRING || type == AbiType.BYTE_ARRAY) {
                result.add(index);
            }
        }
        return List.copyOf(result);
    }

    private static String rawExportArguments(final ExportedMethod export) {
        if (export.parameterTypes().isEmpty()) {
            return "";
        }
        final StringBuilder arguments = new StringBuilder();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            if (index > 0) {
                arguments.append(", ");
            }
            arguments.append("arg").append(index);
        }
        return arguments.toString();
    }

    private static String convertedExportArgumentName(final AbiType type, final int index) {
        if (type == AbiType.STRING) {
            return "arg" + index + "_string";
        }
        if (type == AbiType.BYTE_ARRAY) {
            return "arg" + index + "_array";
        }
        throw new IllegalArgumentException("ABI type is not converted through a Java object slot: " + type.name());
    }

    private static void emitExportWrapperRootFramePush(
        final ExportedMethod export,
        final List<Integer> objectArguments,
        final boolean objectReturn,
        final StringBuilder c
    ) {
        if (objectArguments.isEmpty() && !objectReturn) {
            return;
        }
        c.append("    void** javan_export_roots[] = {").append(System.lineSeparator());
        for (int position = 0; position < objectArguments.size(); position++) {
            final int argumentIndex = objectArguments.get(position);
            c.append("        (void**) &")
                .append(convertedExportArgumentName(export.parameterTypes().get(argumentIndex), argumentIndex));
            if (position < objectArguments.size() - 1 || objectReturn) {
                c.append(',');
            }
            c.append(System.lineSeparator());
        }
        if (objectReturn) {
            c.append("        (void**) &javan_export_object_result").append(System.lineSeparator());
        }
        c.append("    };").append(System.lineSeparator());
        c.append("    javan_root_frame_push(javan_export_roots, ")
            .append(objectArguments.size() + (objectReturn ? 1 : 0))
            .append(");")
            .append(System.lineSeparator());
    }

    private static void emitExportWrapperCleanup(
        final List<Integer> objectArguments,
        final boolean objectReturn,
        final StringBuilder c
    ) {
        if (objectArguments.isEmpty() && !objectReturn) {
            return;
        }
        c.append("    javan_root_frame_pop(javan_export_roots);").append(System.lineSeparator());
    }

    private static void emitExportSignature(final ExportedMethod export, final StringBuilder c) {
        c.append(export.returnType().cReturnName()).append(' ').append(export.symbol()).append('(');
        if (export.parameterTypes().isEmpty()) {
            c.append("void");
        } else {
            for (int index = 0; index < export.parameterTypes().size(); index++) {
                if (index > 0) {
                    c.append(", ");
                }
                final AbiType type = export.parameterTypes().get(index);
                c.append(type.cName()).append(" arg").append(index);
            }
        }
        c.append(')');
    }

    private static String exportArguments(final ExportedMethod export) {
        final StringBuilder arguments = new StringBuilder();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            if (index > 0) {
                arguments.append(", ");
            }
            final AbiType type = export.parameterTypes().get(index);
            if (type == AbiType.STRING) {
                arguments.append("arg").append(index).append("_string");
            } else if (type == AbiType.BYTE_ARRAY) {
                arguments.append("arg").append(index).append("_array");
            } else if (type == AbiType.OBJECT) {
                arguments.append("javan_object_handle_value(arg").append(index).append(")");
            } else {
                arguments.append("arg").append(index);
            }
        }
        return arguments.toString();
    }

    private static void emitEntryParameters(final IrFunction function, final StringBuilder c) {
        for (int index = 0; index < function.parameters().size(); index++) {
            final javan.ir.IrParameter parameter = function.parameters().get(index);
            if (index == 0 && parameter.type() == javan.ir.IrType.OBJECT) {
                c.append("    void* ")
                    .append(parameter.name())
                    .append(" = javan_string_array_from_args(argc, argv);")
                    .append(System.lineSeparator());
            }
        }
    }

    private static List<String> objectRootNames(final IrFunction function) {
        final List<String> result = new java.util.ArrayList<>();
        for (final javan.ir.IrParameter parameter : function.parameters()) {
            if (parameter.type() == javan.ir.IrType.OBJECT) {
                result.add(parameter.name());
            }
        }
        for (final javan.ir.IrLocal local : function.locals()) {
            if (local.type() == javan.ir.IrType.OBJECT) {
                result.add(local.name());
            }
        }
        return List.copyOf(result);
    }

    private static final class RootLivenessPlan {
        private final java.util.Map<Integer, List<String>> clearsAfter;

        private RootLivenessPlan(final java.util.Map<Integer, List<String>> clearsAfter) {
            this.clearsAfter = clearsAfter;
        }

        static RootLivenessPlan forFunction(final IrFunction function) {
            final java.util.Map<Integer, List<String>> clears = new java.util.LinkedHashMap<>();
            final List<IrInstruction> instructions = function.instructions();
            final List<String> roots = objectRootNames(function);
            if (roots.isEmpty()) {
                return new RootLivenessPlan(clears);
            }
            if (!hasValidControlFlow(instructions)) {
                return new RootLivenessPlan(clears);
            }

            final List<List<String>> uses = emptyStringSets(instructions.size());
            final List<List<String>> defs = emptyStringSets(instructions.size());
            for (int index = 0; index < instructions.size(); index++) {
                final IrInstruction instruction = instructions.get(index);
                final java.util.Optional<javan.ir.IrExpression> expression = instruction.expression();
                if (expression.isPresent()) {
                    collectRootUses(expression.get(), roots, uses.get(index));
                }
                final java.util.Optional<String> assignedRoot = assignedObjectRoot(instruction, roots);
                if (assignedRoot.isPresent()) {
                    defs.get(index).add(assignedRoot.get());
                }
            }

            final List<List<Integer>> successors = successors(instructions);
            final Liveness liveness = liveness(instructions, uses, defs, successors);
            final List<List<String>> clearSets = clearSets(function, instructions, roots, defs, successors, liveness);
            for (int index = 0; index < clearSets.size(); index++) {
                if (!clearSets.get(index).isEmpty()) {
                    clears.put(Integer.valueOf(index), clearSets.get(index));
                }
            }
            return new RootLivenessPlan(clears);
        }

        void emitClearsAfter(final int instructionIndex, final StringBuilder c) {
            final List<String> roots = clearsAfter.get(instructionIndex);
            if (roots == null) {
                return;
            }
            c.append("    javan_runtime_lock_enter();").append(System.lineSeparator());
            for (final String root : roots) {
                c.append("    ").append(root).append(" = 0;").append(System.lineSeparator());
            }
            c.append("    javan_runtime_lock_leave();").append(System.lineSeparator());
        }

        private static void collectRootUses(
            final javan.ir.IrExpression expression,
            final List<String> roots,
            final List<String> result
        ) {
            if (expression.kind() == javan.ir.IrExpression.Kind.LOCAL
                && expression.type() == javan.ir.IrType.OBJECT
                && contains(roots, expression.value())) {
                addUnique(result, expression.value());
            }
            for (final javan.ir.IrExpression argument : expression.arguments()) {
                collectRootUses(argument, roots, result);
            }
        }

        private static java.util.Optional<String> assignedObjectRoot(
            final IrInstruction instruction,
            final List<String> roots
        ) {
            if (instruction.op() == IrInstruction.Op.ASSIGN_OBJECT) {
                final String target = instruction.value().orElseThrow();
                if (contains(roots, target)) {
                    return java.util.Optional.of(target);
                }
            }
            return java.util.Optional.empty();
        }

        private static Liveness liveness(
            final List<IrInstruction> instructions,
            final List<List<String>> uses,
            final List<List<String>> defs,
            final List<List<Integer>> successors
        ) {
            final List<List<String>> liveIn = emptyStringSets(instructions.size());
            final List<List<String>> liveOut = emptyStringSets(instructions.size());
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int index = instructions.size() - 1; index >= 0; index--) {
                    final List<String> nextOut = new java.util.ArrayList<>();
                    for (final Integer successor : successors.get(index)) {
                        addAllUnique(nextOut, liveIn.get(successor.intValue()));
                    }
                    final List<String> nextIn = copyOf(uses.get(index));
                    for (final String root : nextOut) {
                        if (!contains(defs.get(index), root)) {
                            addUnique(nextIn, root);
                        }
                    }
                    if (!sameValues(liveOut.get(index), nextOut)) {
                        liveOut.set(index, nextOut);
                        changed = true;
                    }
                    if (!sameValues(liveIn.get(index), nextIn)) {
                        liveIn.set(index, nextIn);
                        changed = true;
                    }
                }
            }
            return new Liveness(liveIn, liveOut);
        }

        private static List<List<String>> clearSets(
            final IrFunction function,
            final List<IrInstruction> instructions,
            final List<String> roots,
            final List<List<String>> defs,
            final List<List<Integer>> successors,
            final Liveness liveness
        ) {
            final List<List<Integer>> predecessors = predecessors(instructions.size(), successors);
            final List<List<String>> mayIn = emptyStringSets(instructions.size());
            final List<List<String>> mayOut = emptyStringSets(instructions.size());
            final List<List<String>> clears = emptyStringSets(instructions.size());
            final List<String> parameterRoots = objectParameterRoots(function);
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int index = 0; index < instructions.size(); index++) {
                    final List<String> nextIn = new java.util.ArrayList<>();
                    if (index == 0) {
                        addAllUnique(nextIn, parameterRoots);
                    }
                    for (final Integer predecessor : predecessors.get(index)) {
                        addAllUnique(nextIn, mayOut.get(predecessor.intValue()));
                    }
                    List<String> nextOut = copyOf(nextIn);
                    nextOut = applyAssignmentMayState(instructions.get(index), defs.get(index), nextOut);
                    final List<String> nextClears = rootsToClearAfter(
                        instructions.get(index),
                        index,
                        roots,
                        nextOut,
                        liveness,
                        successors
                    );
                    for (final String root : nextClears) {
                        if (instructions.get(index).op() != IrInstruction.Op.BRANCH_IF) {
                            nextOut = withoutValue(nextOut, root);
                        }
                    }
                    if (!sameValues(mayIn.get(index), nextIn)) {
                        mayIn.set(index, nextIn);
                        changed = true;
                    }
                    if (!sameValues(mayOut.get(index), nextOut)) {
                        mayOut.set(index, nextOut);
                        changed = true;
                    }
                    if (!sameValues(clears.get(index), nextClears)) {
                        clears.set(index, nextClears);
                        changed = true;
                    }
                }
            }
            return clears;
        }

        private static List<String> applyAssignmentMayState(
            final IrInstruction instruction,
            final List<String> defs,
            final List<String> state
        ) {
            List<String> result = state;
            for (final String root : defs) {
                result = withoutValue(result, root);
                if (!assignsObjectNull(instruction)) {
                    addUnique(result, root);
                }
            }
            return result;
        }

        private static List<String> rootsToClearAfter(
            final IrInstruction instruction,
            final int instructionIndex,
            final List<String> roots,
            final List<String> mayOut,
            final Liveness liveness,
            final List<List<Integer>> successors
        ) {
            final List<String> result = new java.util.ArrayList<>();
            if (!hasStatementSafePoint(instruction)) {
                return result;
            }
            final List<String> requiredAfterClear = requiredAfterFallthroughClear(
                instruction,
                instructionIndex,
                liveness,
                successors
            );
            for (final String root : roots) {
                if (contains(mayOut, root) && !contains(requiredAfterClear, root)) {
                    result.add(root);
                }
            }
            return result;
        }

        private static List<String> requiredAfterFallthroughClear(
            final IrInstruction instruction,
            final int instructionIndex,
            final Liveness liveness,
            final List<List<Integer>> successors
        ) {
            if (instruction.op() != IrInstruction.Op.BRANCH_IF) {
                return liveness.liveOut().get(instructionIndex);
            }
            final Integer fallthrough = fallthroughSuccessor(instructionIndex, successors);
            if (fallthrough == null) {
                return List.of();
            }
            return liveness.liveIn().get(fallthrough.intValue());
        }

        private static Integer fallthroughSuccessor(final int instructionIndex, final List<List<Integer>> successors) {
            final int fallthroughIndex = instructionIndex + 1;
            for (final Integer successor : successors.get(instructionIndex)) {
                if (successor.intValue() == fallthroughIndex) {
                    return successor;
                }
            }
            return null;
        }

        private static List<List<Integer>> successors(final List<IrInstruction> instructions) {
            final java.util.Map<String, Integer> labelTargets = labelTargets(instructions);
            final List<List<Integer>> result = emptyIntegerSets(instructions.size());
            for (int index = 0; index < instructions.size(); index++) {
                final IrInstruction instruction = instructions.get(index);
                switch (instruction.op()) {
                    case JUMP:
                        addLabelSuccessor(result.get(index), labelTargets, instruction.value().orElseThrow());
                        break;
                    case BRANCH_IF:
                        addLabelSuccessor(result.get(index), labelTargets, instruction.value().orElseThrow());
                        addNextSuccessor(result.get(index), index, instructions.size());
                        break;
                    case PANIC:
                    case THROW_PENDING:
                    case PROPAGATE_PENDING:
                    case RETURN_VOID:
                    case RETURN_INT:
                    case RETURN_LONG:
                    case RETURN_FLOAT:
                    case RETURN_DOUBLE:
                    case RETURN_OBJECT:
                        break;
                    default:
                        addNextSuccessor(result.get(index), index, instructions.size());
                        break;
                }
            }
            return result;
        }

        private static boolean hasValidControlFlow(final List<IrInstruction> instructions) {
            final java.util.Map<String, Integer> labelTargets = new java.util.LinkedHashMap<>();
            for (int index = 0; index < instructions.size(); index++) {
                final IrInstruction instruction = instructions.get(index);
                if (instruction.op() == IrInstruction.Op.LABEL) {
                    final String label = instruction.value().orElseThrow();
                    if (labelTargets.get(label) != null) {
                        return false;
                    }
                    labelTargets.put(label, Integer.valueOf(index));
                }
            }
            for (final IrInstruction instruction : instructions) {
                switch (instruction.op()) {
                    case JUMP:
                    case BRANCH_IF:
                        if (labelTargets.get(instruction.value().orElseThrow()) == null) {
                            return false;
                        }
                        break;
                    default:
                        break;
                }
            }
            return true;
        }

        private static List<List<Integer>> predecessors(final int size, final List<List<Integer>> successors) {
            final List<List<Integer>> result = emptyIntegerSets(size);
            for (int index = 0; index < successors.size(); index++) {
                for (final Integer successor : successors.get(index)) {
                    addUniqueInteger(result.get(successor.intValue()), Integer.valueOf(index));
                }
            }
            return result;
        }

        private static java.util.Map<String, Integer> labelTargets(final List<IrInstruction> instructions) {
            final java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
            for (int index = 0; index < instructions.size(); index++) {
                final IrInstruction instruction = instructions.get(index);
                if (instruction.op() == IrInstruction.Op.LABEL) {
                    result.put(instruction.value().orElseThrow(), Integer.valueOf(index));
                }
            }
            return result;
        }

        private static void addLabelSuccessor(
            final List<Integer> result,
            final java.util.Map<String, Integer> labelTargets,
            final String label
        ) {
            final Integer target = labelTargets.get(label);
            if (target != null) {
                addUniqueInteger(result, target);
            }
        }

        private static void addNextSuccessor(final List<Integer> result, final int index, final int size) {
            if (index + 1 < size) {
                addUniqueInteger(result, Integer.valueOf(index + 1));
            }
        }

        private static List<String> objectParameterRoots(final IrFunction function) {
            final List<String> result = new java.util.ArrayList<>();
            for (final javan.ir.IrParameter parameter : function.parameters()) {
                if (parameter.type() == javan.ir.IrType.OBJECT) {
                    result.add(parameter.name());
                }
            }
            return result;
        }

        private static List<List<String>> emptyStringSets(final int size) {
            final List<List<String>> result = new java.util.ArrayList<>();
            for (int index = 0; index < size; index++) {
                result.add(new java.util.ArrayList<>());
            }
            return result;
        }

        private static List<List<Integer>> emptyIntegerSets(final int size) {
            final List<List<Integer>> result = new java.util.ArrayList<>();
            for (int index = 0; index < size; index++) {
                result.add(new java.util.ArrayList<>());
            }
            return result;
        }

        private static List<String> copyOf(final List<String> values) {
            final List<String> result = new java.util.ArrayList<>();
            addAllUnique(result, values);
            return result;
        }

        private static void addAllUnique(final List<String> values, final List<String> additions) {
            for (final String addition : additions) {
                addUnique(values, addition);
            }
        }

        private static void addUnique(final List<String> values, final String value) {
            if (!contains(values, value)) {
                values.add(value);
            }
        }

        private static void addUniqueInteger(final List<Integer> values, final Integer value) {
            if (!containsInteger(values, value)) {
                values.add(value);
            }
        }

        private static List<String> withoutValue(final List<String> values, final String value) {
            final List<String> result = new java.util.ArrayList<>();
            for (final String current : values) {
                if (!current.equals(value)) {
                    result.add(current);
                }
            }
            return result;
        }

        private static boolean contains(final List<String> values, final String value) {
            for (final String current : values) {
                if (current.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsInteger(final List<Integer> values, final Integer value) {
            for (final Integer current : values) {
                if (current.intValue() == value.intValue()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean sameValues(final List<String> left, final List<String> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (final String value : left) {
                if (!contains(right, value)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean assignsObjectNull(final IrInstruction instruction) {
            final java.util.Optional<javan.ir.IrExpression> expression = instruction.expression();
            return expression.isPresent() && expression.get().kind() == javan.ir.IrExpression.Kind.OBJECT_NULL;
        }

        private record Liveness(List<List<String>> liveIn, List<List<String>> liveOut) {
        }
    }

    private static void emitRootFramePush(final String rootFrameSymbol, final List<String> rootNames, final StringBuilder c) {
        if (rootNames.isEmpty()) {
            return;
        }
        c.append("    void** ").append(rootFrameSymbol).append("[] = {").append(System.lineSeparator());
        for (int index = 0; index < rootNames.size(); index++) {
            c.append("        (void**) &").append(rootNames.get(index));
            if (index < rootNames.size() - 1) {
                c.append(',');
            }
            c.append(System.lineSeparator());
        }
        c.append("    };").append(System.lineSeparator());
        c.append("    javan_root_frame_push(")
            .append(rootFrameSymbol)
            .append(", ")
            .append(rootNames.size())
            .append(");")
            .append(System.lineSeparator());
    }

    private static void emitRootFramePop(final String rootFrameSymbol, final boolean hasRootFrame, final StringBuilder c) {
        emitRootFramePop(rootFrameSymbol, hasRootFrame, c, "    ");
    }

    private static void emitRootFramePop(
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final StringBuilder c,
        final String indent
    ) {
        if (hasRootFrame) {
            c.append(indent).append("javan_root_frame_pop(").append(rootFrameSymbol).append(");").append(System.lineSeparator());
        }
    }

    private static String emitExpressionScopeStart(final ExpressionPlan plan, final StringBuilder c) {
        if (plan.isEmpty()) {
            return "    ";
        }
        c.append("    {").append(System.lineSeparator());
        for (final ExpressionPlan.Temporary temporary : plan.temporaries()) {
            c.append("        ")
                .append(temporary.type().cName())
                .append(' ')
                .append(temporary.name())
                .append(" = 0;")
                .append(System.lineSeparator());
        }
        final java.util.List<String> rootTemporaries = plan.rootTemporaries();
        if (!rootTemporaries.isEmpty()) {
            c.append("        void** javan_expr_roots[] = {").append(System.lineSeparator());
            for (int index = 0; index < rootTemporaries.size(); index++) {
                c.append("            (void**) &").append(rootTemporaries.get(index));
                if (index < rootTemporaries.size() - 1) {
                    c.append(',');
                }
                c.append(System.lineSeparator());
            }
            c.append("        };").append(System.lineSeparator());
            c.append("        javan_root_frame_push(javan_expr_roots, ")
                .append(rootTemporaries.size())
                .append(");")
                .append(System.lineSeparator());
        }
        for (final ExpressionPlan.Assignment assignment : plan.assignments()) {
            if (assignment.collectorVisibleRootWrite()) {
                c.append("        javan_runtime_lock_enter();").append(System.lineSeparator());
            }
            c.append("        ").append(assignment.code()).append(System.lineSeparator());
            if (assignment.collectorVisibleRootWrite()) {
                c.append("        javan_runtime_lock_leave();").append(System.lineSeparator());
            }
        }
        return "        ";
    }

    private static void emitExpressionScopeEnd(final ExpressionPlan plan, final StringBuilder c) {
        if (!plan.isEmpty()) {
            if (plan.hasRootFrame()) {
                c.append("        javan_root_frame_pop(javan_expr_roots);").append(System.lineSeparator());
            }
            c.append("    }").append(System.lineSeparator());
        }
    }

    private static void emitPrintCall(
        final StringBuilder c,
        final String function,
        final String cast,
        final javan.ir.IrExpression expression,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String value = plan.expression(expression);
        final String indent = emitExpressionScopeStart(plan, c);
        c.append(indent)
            .append(function)
            .append("(")
            .append(cast)
            .append(value)
            .append(");")
            .append(System.lineSeparator());
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitAssignment(
        final StringBuilder c,
        final String target,
        final javan.ir.IrExpression expression,
        final boolean collectorVisibleRootWrite,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String value = plan.expression(expression);
        final String indent = emitExpressionScopeStart(plan, c);
        if (collectorVisibleRootWrite) {
            c.append(indent).append("javan_runtime_lock_enter();").append(System.lineSeparator());
        }
        c.append(indent)
            .append(target)
            .append(" = ")
            .append(value)
            .append(";")
            .append(System.lineSeparator());
        if (collectorVisibleRootWrite) {
            c.append(indent).append("javan_runtime_lock_leave();").append(System.lineSeparator());
        }
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitFieldAssignment(
        final StringBuilder c,
        final String[] ownerField,
        final java.util.List<javan.ir.IrExpression> arguments,
        final boolean collectorVisibleReferenceWrite,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String receiver = plan.expression(arguments.get(0));
        final String value = plan.expression(arguments.get(1));
        final String indent = emitExpressionScopeStart(plan, c);
        if (collectorVisibleReferenceWrite) {
            c.append(indent).append("javan_runtime_lock_enter();").append(System.lineSeparator());
        }
        c.append(indent)
            .append("((struct ")
            .append(classSymbol(ownerField[0]))
            .append("*) ")
            .append(receiver)
            .append(")->")
            .append(fieldSymbol(ownerField[1]))
            .append(" = ")
            .append(value)
            .append(";")
            .append(System.lineSeparator());
        if (collectorVisibleReferenceWrite) {
            c.append(indent).append("javan_runtime_lock_leave();").append(System.lineSeparator());
        }
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitArraySet(
        final StringBuilder c,
        final String function,
        final java.util.List<javan.ir.IrExpression> arguments,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String array = plan.expression(arguments.get(0));
        final String index = plan.expression(arguments.get(1));
        final String value = plan.expression(arguments.get(2));
        final String indent = emitExpressionScopeStart(plan, c);
        c.append(indent)
            .append(function)
            .append("(")
            .append(array)
            .append(", ")
            .append(index)
            .append(", ")
            .append(value)
            .append(");")
            .append(System.lineSeparator());
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitBranchIf(
        final StringBuilder c,
        final String label,
        final javan.ir.IrExpression condition,
        final String sourceContextSymbol,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String value = plan.expression(condition);
        if (plan.isEmpty()) {
            if (sourceContextSymbol.length() == 0) {
                c.append("    if (")
                    .append(value)
                    .append(") goto ")
                    .append(label)
                    .append(";")
                    .append(System.lineSeparator());
                return;
            }
            c.append("    if (")
                .append(value)
                .append(") {")
                .append(System.lineSeparator());
            emitSourceContextClear(c, "        ", sourceContextSymbol);
            c.append("        goto ")
                .append(label)
                .append(";")
                .append(System.lineSeparator());
            c.append("    }").append(System.lineSeparator());
            emitSourceContextClear(c, "    ", sourceContextSymbol);
            return;
        }
        final String indent = emitExpressionScopeStart(plan, c);
        c.append(indent)
            .append("if (")
            .append(value)
            .append(") {")
            .append(System.lineSeparator());
        if (sourceContextSymbol.length() > 0) {
            emitSourceContextClear(c, indent + "    ", sourceContextSymbol);
        }
        if (plan.hasRootFrame()) {
            c.append(indent).append("    javan_root_frame_pop(javan_expr_roots);").append(System.lineSeparator());
        }
        c.append(indent).append("    goto ").append(label).append(";").append(System.lineSeparator());
        c.append(indent).append("}").append(System.lineSeparator());
        if (sourceContextSymbol.length() > 0) {
            emitSourceContextClear(c, indent, sourceContextSymbol);
        }
        emitExpressionScopeEnd(plan, c);
    }

    private static final class ExpressionPlan {
        private final List<String> objectResultSymbols;
        private final NativeWrapperSymbols nativeWrapperSymbols;
        private final java.util.List<Temporary> temporaries = new java.util.ArrayList<>();
        private final java.util.List<Assignment> assignments = new java.util.ArrayList<>();

        private ExpressionPlan(
            final List<String> objectResultSymbols,
            final NativeWrapperSymbols nativeWrapperSymbols
        ) {
            this.objectResultSymbols = objectResultSymbols;
            this.nativeWrapperSymbols = nativeWrapperSymbols;
        }

        String expression(final javan.ir.IrExpression expression) {
            if (isGeneratedObjectResultCall(expression)) {
                final String arguments = expressionArguments(expression.arguments());
                final String temporary = "javan_expr_tmp_" + temporaries.size();
                temporaries.add(new Temporary(expression.type(), temporary));
                assignments.add(new Assignment(
                    expression.value()
                        + "((void**) &"
                        + temporary
                        + (arguments.isEmpty() ? "" : ", " + arguments)
                        + ");",
                    false
                ));
                return temporary;
            }
            if (usesRootedResultCall(expression)) {
                final String arguments = expressionArguments(expression.arguments());
                final String temporary = "javan_expr_tmp_" + temporaries.size();
                temporaries.add(new Temporary(expression.type(), temporary));
                assignments.add(new Assignment(
                    rootedResultCallSymbol(expression)
                        + "((void**) &"
                        + temporary
                        + (arguments.isEmpty() ? "" : ", " + arguments)
                        + ");",
                    false
                ));
                return temporary;
            }
            if (expression.kind() == javan.ir.IrExpression.Kind.STRING_CONCAT) {
                final String arguments = stringArguments(expression.arguments());
                final String temporary = "javan_expr_tmp_" + temporaries.size();
                temporaries.add(new Temporary(expression.type(), temporary));
                assignments.add(new Assignment(
                    "javan_string_concat_into((void**) &"
                        + temporary
                        + ", "
                        + emitCStringLiteral(expression.value())
                        + ", "
                        + expression.arguments().size()
                        + ", (const char*[]){"
                        + arguments
                        + "});",
                    false
                ));
                return temporary;
            }
            final String raw = rawExpression(expression);
            if (!needsTemporary(expression)) {
                return raw;
            }
            final String temporary = "javan_expr_tmp_" + temporaries.size();
            temporaries.add(new Temporary(expression.type(), temporary));
            assignments.add(new Assignment(
                temporary + " = " + raw + ";",
                requiresCollectorVisibleRootWrite(expression)
            ));
            return temporary;
        }

        private static boolean requiresCollectorVisibleRootWrite(final javan.ir.IrExpression expression) {
            if (expression.type() != javan.ir.IrType.OBJECT) {
                return false;
            }
            if (expression.kind() != javan.ir.IrExpression.Kind.CALL) {
                return true;
            }
            return "javan_atomic_reference_get".equals(expression.value());
        }

        private boolean isGeneratedObjectResultCall(final javan.ir.IrExpression expression) {
            return expression.kind() == javan.ir.IrExpression.Kind.CALL
                && expression.type() == javan.ir.IrType.OBJECT
                && objectResultSymbols.contains(expression.value());
        }

        boolean isEmpty() {
            return temporaries.isEmpty();
        }

        java.util.List<Temporary> temporaries() {
            return java.util.List.copyOf(temporaries);
        }

        java.util.List<Assignment> assignments() {
            return java.util.List.copyOf(assignments);
        }

        boolean hasRootFrame() {
            for (final Temporary temporary : temporaries) {
                if (temporary.type() == javan.ir.IrType.OBJECT) {
                    return true;
                }
            }
            return false;
        }

        java.util.List<String> rootTemporaries() {
            final java.util.List<String> result = new java.util.ArrayList<>();
            for (final Temporary temporary : temporaries) {
                if (temporary.type() == javan.ir.IrType.OBJECT) {
                    result.add(temporary.name());
                }
            }
            return java.util.List.copyOf(result);
        }

        private String rawExpression(final javan.ir.IrExpression expression) {
            switch (expression.kind()) {
                case INT_LITERAL:
                    return intLiteral(expression.value());
                case LONG_LITERAL:
                    return longLiteral(expression.value());
                case FLOAT_LITERAL:
                case DOUBLE_LITERAL:
                    return floatAndDoubleLiteral(expression.value());
                case LOCAL:
                    return expression.value();
                case OBJECT_NULL:
                    return "((void*) 0)";
                case STRING_LITERAL:
                    return "(void*) " + emitCStringLiteral(expression.value());
                case STRING_CONCAT:
                    return "javan_string_concat("
                        + emitCStringLiteral(expression.value())
                        + ", "
                        + expression.arguments().size()
                        + ", (const char*[]){"
                        + stringArguments(expression.arguments())
                        + "})";
                case INT_BINARY:
                case LONG_BINARY:
                case FLOAT_BINARY:
                case DOUBLE_BINARY:
                case INT_COMPARE:
                case OBJECT_COMPARE:
                    return "("
                        + expression(expression.arguments().get(0))
                        + " "
                        + expression.value()
                        + " "
                        + expression(expression.arguments().get(1))
                        + ")";
                case CALL:
                    return nativeWrapperSymbols.resolve(expression.value())
                        + "(" + expressionArguments(expression.arguments()) + ")";
                case OBJECT_ALLOCATION:
                    return allocatorSymbol(expression.value()) + "()";
                case OBJECT_ARRAY_ALLOCATION:
                    return "javan_object_array_new("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + emitCStringLiteral(Strings2.isBlank(expression.value()) ? "[Ljava.lang.Object;" : expression.value())
                        + ")";
                case OBJECT_ARRAY_LOAD:
                    return "javan_object_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case INT_ARRAY_ALLOCATION:
                    return "javan_int_array_new(" + expression(expression.arguments().get(0)) + ")";
                case INT_ARRAY_LOAD:
                    return "javan_int_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case LONG_ARRAY_ALLOCATION:
                    return "javan_long_array_new(" + expression(expression.arguments().get(0)) + ")";
                case LONG_ARRAY_LOAD:
                    return "javan_long_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case FLOAT_ARRAY_ALLOCATION:
                    return "javan_float_array_new(" + expression(expression.arguments().get(0)) + ")";
                case FLOAT_ARRAY_LOAD:
                    return "javan_float_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case DOUBLE_ARRAY_ALLOCATION:
                    return "javan_double_array_new(" + expression(expression.arguments().get(0)) + ")";
                case DOUBLE_ARRAY_LOAD:
                    return "javan_double_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case BYTE_ARRAY_ALLOCATION:
                    return "javan_byte_array_new(" + expression(expression.arguments().get(0)) + ")";
                case BOOLEAN_ARRAY_ALLOCATION:
                    return "javan_boolean_array_new(" + expression(expression.arguments().get(0)) + ")";
                case BYTE_ARRAY_LOAD:
                    return "javan_byte_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case SHORT_ARRAY_ALLOCATION:
                    return "javan_short_array_new(" + expression(expression.arguments().get(0)) + ")";
                case SHORT_ARRAY_LOAD:
                    return "javan_short_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case CHAR_ARRAY_ALLOCATION:
                    return "javan_char_array_new(" + expression(expression.arguments().get(0)) + ")";
                case CHAR_ARRAY_LOAD:
                    return "javan_char_array_get("
                        + expression(expression.arguments().get(0))
                        + ", "
                        + expression(expression.arguments().get(1))
                        + ")";
                case ARRAY_LENGTH:
                    return "javan_array_length(" + expression(expression.arguments().get(0)) + ")";
                case FIELD_INT:
                case FIELD_LONG:
                case FIELD_FLOAT:
                case FIELD_DOUBLE:
                case FIELD_OBJECT:
                    return fieldAccess(expression);
                case STATIC_FIELD_INT:
                case STATIC_FIELD_LONG:
                case STATIC_FIELD_FLOAT:
                case STATIC_FIELD_DOUBLE:
                case STATIC_FIELD_OBJECT: {
                    final String[] ownerField = ownerField(expression.value());
                    return staticFieldSymbol(ownerField[0], ownerField[1]);
                }
                case FIELD_ASSIGN_INT:
                case FIELD_ASSIGN_LONG:
                case FIELD_ASSIGN_FLOAT:
                case FIELD_ASSIGN_DOUBLE:
                case FIELD_ASSIGN_OBJECT:
                case ARRAY_ASSIGN_OBJECT:
                case ARRAY_ASSIGN_INT:
                case ARRAY_ASSIGN_LONG:
                case ARRAY_ASSIGN_FLOAT:
                case ARRAY_ASSIGN_DOUBLE:
                case ARRAY_ASSIGN_BYTE:
                case ARRAY_ASSIGN_SHORT:
                case ARRAY_ASSIGN_CHAR:
                    throw new IllegalArgumentException("assignment is not a value expression");
                default:
                    throw new IllegalStateException("Unsupported IR expression kind");
            }
        }

        private String expressionArguments(final List<javan.ir.IrExpression> arguments) {
            final StringBuilder result = new StringBuilder();
            for (int index = 0; index < arguments.size(); index++) {
                if (index > 0) {
                    result.append(", ");
                }
                result.append(expression(arguments.get(index)));
            }
            return result.toString();
        }

        private String stringArguments(final List<javan.ir.IrExpression> arguments) {
            final StringBuilder result = new StringBuilder();
            for (int index = 0; index < arguments.size(); index++) {
                if (index > 0) {
                    result.append(", ");
                }
                result.append(stringArgument(arguments.get(index)));
            }
            return result.toString();
        }

        private String fieldAccess(final javan.ir.IrExpression expression) {
            final String[] ownerField = ownerField(expression.value());
            return "((struct "
                + classSymbol(ownerField[0])
                + "*) "
                + expression(expression.arguments().get(0))
                + ")->"
                + fieldSymbol(ownerField[1]);
        }

        private String stringArgument(final javan.ir.IrExpression expression) {
            if (expression.type() == javan.ir.IrType.OBJECT) {
                return "(const char*) " + expression(expression);
            }
            throw new IllegalArgumentException("string concat arguments must be preconverted to object strings");
        }

        private static boolean needsTemporary(final javan.ir.IrExpression expression) {
            if (expression.type() == javan.ir.IrType.OBJECT) {
                switch (expression.kind()) {
                    case LOCAL:
                    case OBJECT_NULL:
                    case STRING_LITERAL:
                        return false;
                    default:
                        return true;
                }
            }
            if (expression.type() == javan.ir.IrType.VOID) {
                return false;
            }
            return expression.kind() == javan.ir.IrExpression.Kind.CALL;
        }

        private record Temporary(javan.ir.IrType type, String name) {
        }

        private record Assignment(String code, boolean collectorVisibleRootWrite) {
        }
    }

    private static String intLiteral(final String value) {
        if ("-2147483648".equals(value)) {
            return "(-2147483647 - 1)";
        }
        return value;
    }

    private static String longLiteral(final String value) {
        if ("-9223372036854775808".equals(value)) {
            return "(-9223372036854775807LL - 1LL)";
        }
        return value + "LL";
    }

    private static void emitInstruction(
        final int index,
        final IrInstruction instruction,
        final boolean entry,
        final javan.ir.IrType functionReturnType,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        final boolean sourceContext = shouldEmitSourceContext(instruction);
        final String sourceContextSymbol = sourceContext ? sourceContextSymbol(index) : "";
        if (sourceContext) {
            emitSourceContextEnter(c, "    ", sourceContextSymbol, instruction.sourceLocation().orElseThrow());
        }
        switch (instruction.op()) {
            case PRINTLN_LITERAL:
                c.append("    javan_println(\"")
                    .append(escapeCString(instruction.value().orElseThrow()))
                    .append("\");")
                    .append(System.lineSeparator());
                break;
            case PRINTLN_INT:
                emitPrintCall(c, "javan_println_int", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_ERROR_INT:
                emitPrintCall(c, "javan_eprintln_int", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_LONG:
                emitPrintCall(c, "javan_println_long", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_ERROR_LONG:
                emitPrintCall(c, "javan_eprintln_long", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_FLOAT:
                emitPrintCall(c, "javan_println_float", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_ERROR_FLOAT:
                emitPrintCall(c, "javan_eprintln_float", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_DOUBLE:
                emitPrintCall(c, "javan_println_double", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_ERROR_DOUBLE:
                emitPrintCall(c, "javan_eprintln_double", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_BOOLEAN:
                emitPrintCall(c, "javan_println_bool", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_ERROR_BOOLEAN:
                emitPrintCall(c, "javan_eprintln_bool", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_OBJECT:
                emitPrintCall(c, "javan_println_object_value", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINTLN_ERROR_OBJECT:
                emitPrintCall(c, "javan_eprintln_object_value", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINT_OBJECT:
                emitPrintCall(c, "javan_print_object_value", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case PRINT_ERROR_OBJECT:
                emitPrintCall(c, "javan_eprint_object_value", "", instruction.expression().orElseThrow(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case CALL_STATIC_VOID:
                if (instruction.expression().isPresent()) {
                    final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
                    final String call = plan.expression(instruction.expression().orElseThrow());
                    final String indent = emitExpressionScopeStart(plan, c);
                    c.append(indent).append(call).append(";").append(System.lineSeparator());
                    emitExpressionScopeEnd(plan, c);
                } else {
                    c.append("    ").append(nativeWrapperSymbols.resolve(instruction.value().orElseThrow())).append("();").append(System.lineSeparator());
                }
                break;
            case ASSIGN_INT:
            case ASSIGN_LONG:
            case ASSIGN_FLOAT:
            case ASSIGN_DOUBLE:
                emitAssignment(
                    c,
                    instruction.value().orElseThrow(),
                    instruction.expression().orElseThrow(),
                    false,
                    objectResultSymbols,
                    nativeWrapperSymbols
                );
                break;
            case ASSIGN_OBJECT:
                emitAssignment(
                    c,
                    instruction.value().orElseThrow(),
                    instruction.expression().orElseThrow(),
                    true,
                    objectResultSymbols,
                    nativeWrapperSymbols
                );
                break;
            case ASSIGN_FIELD_INT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments, false, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_FIELD_LONG: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments, false, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_FIELD_FLOAT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments, false, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_FIELD_DOUBLE: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments, false, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_FIELD_OBJECT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments, true, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_STATIC_FIELD_INT:
            case ASSIGN_STATIC_FIELD_LONG:
            case ASSIGN_STATIC_FIELD_FLOAT:
            case ASSIGN_STATIC_FIELD_DOUBLE: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                emitAssignment(
                    c,
                    staticFieldSymbol(ownerField[0], ownerField[1]),
                    instruction.expression().orElseThrow(),
                    false,
                    objectResultSymbols,
                    nativeWrapperSymbols
                );
                break;
            }
            case ASSIGN_STATIC_FIELD_OBJECT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                emitAssignment(
                    c,
                    staticFieldSymbol(ownerField[0], ownerField[1]),
                    instruction.expression().orElseThrow(),
                    true,
                    objectResultSymbols,
                    nativeWrapperSymbols
                );
                break;
            }
            case ASSIGN_ARRAY_OBJECT: {
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitArraySet(c, "javan_object_array_set", arguments, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_ARRAY_INT: {
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitArraySet(c, "javan_int_array_set", arguments, objectResultSymbols, nativeWrapperSymbols);
                break;
            }
            case ASSIGN_ARRAY_BYTE:
                emitArraySet(c, "javan_byte_array_set", instruction.expression().orElseThrow().arguments(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case ASSIGN_ARRAY_SHORT:
                emitArraySet(c, "javan_short_array_set", instruction.expression().orElseThrow().arguments(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case ASSIGN_ARRAY_CHAR:
                emitArraySet(c, "javan_char_array_set", instruction.expression().orElseThrow().arguments(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case ASSIGN_ARRAY_LONG:
                emitArraySet(c, "javan_long_array_set", instruction.expression().orElseThrow().arguments(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case ASSIGN_ARRAY_FLOAT:
                emitArraySet(c, "javan_float_array_set", instruction.expression().orElseThrow().arguments(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case ASSIGN_ARRAY_DOUBLE:
                emitArraySet(c, "javan_double_array_set", instruction.expression().orElseThrow().arguments(), objectResultSymbols, nativeWrapperSymbols);
                break;
            case LABEL:
                c.append(instruction.value().orElseThrow()).append(":").append(System.lineSeparator());
                break;
            case JUMP:
                c.append("    goto ")
                    .append(instruction.value().orElseThrow())
                    .append(";")
                    .append(System.lineSeparator());
                break;
            case BRANCH_IF:
                emitBranchIf(c, instruction.value().orElseThrow(), instruction.expression().orElseThrow(), sourceContextSymbol, objectResultSymbols, nativeWrapperSymbols);
                break;
            case PANIC: {
                final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
                final String value = plan.expression(instruction.expression().orElseThrow());
                final String indent = emitExpressionScopeStart(plan, c);
                if (instruction.sourceLocation().isPresent()) {
                    emitPanicAt(c, indent, value, instruction.sourceLocation().orElseThrow());
                } else {
                    c.append(indent)
                        .append("javan_panic((const char*) ")
                        .append(value)
                        .append(");")
                        .append(System.lineSeparator());
                }
                emitExpressionScopeEnd(plan, c);
                break;
            }
            case SET_PENDING:
                emitSetPending(instruction, objectResultSymbols, nativeWrapperSymbols, c);
                break;
            case THROW_PENDING:
                emitThrowPending(
                    instruction,
                    entry,
                    functionReturnType,
                    rootFrameSymbol,
                    hasRootFrame,
                    sourceContextSymbol,
                    objectResultSymbols,
                    nativeWrapperSymbols,
                    c
                );
                break;
            case PROPAGATE_PENDING:
                emitPendingPropagation(
                    entry,
                    functionReturnType,
                    rootFrameSymbol,
                    hasRootFrame,
                    sourceContextSymbol,
                    c
                );
                break;
            case RETURN_VOID:
                if (sourceContext) {
                    emitSourceContextClear(c, "    ", sourceContextSymbol);
                }
                if (entry) {
                    c.append("    goto javan_entry_epilogue;").append(System.lineSeparator());
                } else {
                    emitRootFramePop(rootFrameSymbol, hasRootFrame, c);
                    c.append("    return;").append(System.lineSeparator());
                }
                break;
            case RETURN_INT:
            case RETURN_LONG:
            case RETURN_FLOAT:
            case RETURN_DOUBLE:
            case RETURN_OBJECT:
                emitReturnValue(instruction.expression().orElseThrow(), rootFrameSymbol, hasRootFrame, sourceContextSymbol, objectResultSymbols, nativeWrapperSymbols, c);
                break;
        }
        if (sourceContext && shouldClearSourceContextAfterInstruction(instruction)) {
            emitSourceContextClear(c, "    ", sourceContextSymbol);
        }
    }

    private static void emitThrowPending(
        final IrInstruction instruction,
        final boolean entry,
        final javan.ir.IrType functionReturnType,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final String sourceContextSymbol,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        emitSetPending(instruction, objectResultSymbols, nativeWrapperSymbols, c);
        emitPendingPropagation(
            entry,
            functionReturnType,
            rootFrameSymbol,
            hasRootFrame,
            sourceContextSymbol,
            c
        );
    }

    private static void emitSetPending(
        final IrInstruction instruction,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String message = plan.expression(instruction.expression().orElseThrow());
        final String indent = emitExpressionScopeStart(plan, c);
        final IrSourceLocation location = instruction.sourceLocation().orElseThrow();
        c.append(indent)
            .append("javan_pending_throw(")
            .append(emitCStringLiteral(instruction.value().orElseThrow()))
            .append(", (void*) ")
            .append(message)
            .append(", ")
            .append(emitCStringLiteral(displayClassName(location.className())))
            .append(", ")
            .append(emitCStringLiteral(location.methodName() + location.descriptor()))
            .append(", ")
            .append(emitCStringLiteral(location.sourceFile().orElse("")))
            .append(", ")
            .append(sourceLineNumber(location))
            .append(", ")
            .append(location.bytecodeOffset())
            .append(", ")
            .append(emitCStringLiteral(location.sourceLine().orElse("")))
            .append(");")
            .append(System.lineSeparator());
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitPendingPropagation(
        final boolean entry,
        final javan.ir.IrType functionReturnType,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final String sourceContextSymbol,
        final StringBuilder c
    ) {
        if (entry) {
            c.append("    javan_pending_panic();").append(System.lineSeparator());
            return;
        }
        if (sourceContextSymbol.length() > 0) {
            emitSourceContextClear(c, "    ", sourceContextSymbol);
        }
        emitRootFramePop(rootFrameSymbol, hasRootFrame, c);
        emitDefaultReturn(functionReturnType, c);
    }

    private static void emitStatementSafePoint(final IrInstruction instruction, final StringBuilder c) {
        if (!hasStatementSafePoint(instruction)) {
            return;
        }
        c.append("    javan_gc_safe_point();").append(System.lineSeparator());
    }

    private static boolean hasStatementSafePoint(final IrInstruction instruction) {
        switch (instruction.op()) {
            case JUMP:
            case PANIC:
            case THROW_PENDING:
            case PROPAGATE_PENDING:
            case RETURN_VOID:
            case RETURN_INT:
            case RETURN_LONG:
            case RETURN_FLOAT:
            case RETURN_DOUBLE:
            case RETURN_OBJECT:
                return false;
            default:
                return true;
        }
    }

    private static boolean shouldEmitSourceContext(final IrInstruction instruction) {
        if (instruction.sourceLocation().isEmpty()) {
            return false;
        }
        switch (instruction.op()) {
            case LABEL:
            case JUMP:
            case PANIC:
                return false;
            default:
                return true;
        }
    }

    private static boolean shouldClearSourceContextAfterInstruction(final IrInstruction instruction) {
        switch (instruction.op()) {
            case BRANCH_IF:
            case THROW_PENDING:
            case PROPAGATE_PENDING:
            case RETURN_VOID:
            case RETURN_INT:
            case RETURN_LONG:
            case RETURN_FLOAT:
            case RETURN_DOUBLE:
            case RETURN_OBJECT:
                return false;
            default:
                return true;
        }
    }

    private static void emitSourceContextEnter(
        final StringBuilder c,
        final String indent,
        final String symbol,
        final IrSourceLocation location
    ) {
        c.append(indent)
            .append("JavanSourceContext ")
            .append(symbol)
            .append(";")
            .append(System.lineSeparator());
        c.append(indent)
            .append("javan_source_enter(&")
            .append(symbol)
            .append(", ")
            .append(emitCStringLiteral("JAVAN-RUNTIME-PANIC"))
            .append(", ")
            .append(emitCStringLiteral("runtime helper failure"))
            .append(", ")
            .append(emitCStringLiteral(displayClassName(location.className())))
            .append(", ")
            .append(emitCStringLiteral(location.methodName() + location.descriptor()))
            .append(", ")
            .append(emitCStringLiteral(location.sourceFile().orElse("")))
            .append(", ")
            .append(sourceLineNumber(location))
            .append(", ")
            .append(location.bytecodeOffset())
            .append(", ")
            .append(emitCStringLiteral(location.sourceLine().orElse("")))
            .append(", ")
            .append(emitCStringLiteral("Generated native code called a runtime helper that rejected the current value."))
            .append(", ")
            .append(emitCStringLiteral("Check the source expression and guard values before this operation."))
            .append(");")
            .append(System.lineSeparator());
    }

    private static void emitSourceContextClear(final StringBuilder c, final String indent, final String symbol) {
        c.append(indent).append("javan_source_clear(&").append(symbol).append(");").append(System.lineSeparator());
    }

    private static String sourceContextSymbol(final int index) {
        return "javan_source_context_" + index;
    }

    private static void emitReturnValue(
        final javan.ir.IrExpression expression,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final String sourceContextSymbol,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        if (expression.type() == javan.ir.IrType.OBJECT) {
            emitObjectReturnValue(expression, rootFrameSymbol, hasRootFrame, sourceContextSymbol, objectResultSymbols, nativeWrapperSymbols, c);
            return;
        }
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String value = plan.expression(expression);
        if (!hasRootFrame && plan.isEmpty() && sourceContextSymbol.length() == 0) {
            c.append("    return ")
                .append(value)
                .append(";")
                .append(System.lineSeparator());
            return;
        }
        final String indent;
        if (plan.isEmpty()) {
            c.append("    {").append(System.lineSeparator());
            indent = "        ";
        } else {
            indent = emitExpressionScopeStart(plan, c);
        }
        c.append(indent)
            .append(expression.type().cName())
            .append(" javan_return_value = ")
            .append(value)
            .append(";")
            .append(System.lineSeparator());
        if (sourceContextSymbol.length() > 0) {
            emitSourceContextClear(c, indent, sourceContextSymbol);
        }
        c.append(indent).append("javan_gc_safe_point();").append(System.lineSeparator());
        if (plan.hasRootFrame()) {
            c.append(indent).append("javan_root_frame_pop(javan_expr_roots);").append(System.lineSeparator());
        }
        emitRootFramePop(rootFrameSymbol, hasRootFrame, c, indent);
        c.append(indent).append("return javan_return_value;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
    }

    private static void emitObjectReturnValue(
        final javan.ir.IrExpression expression,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final String sourceContextSymbol,
        final List<String> objectResultSymbols,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        final ExpressionPlan plan = new ExpressionPlan(objectResultSymbols, nativeWrapperSymbols);
        final String value = plan.expression(expression);
        final String indent;
        if (plan.isEmpty()) {
            c.append("    {").append(System.lineSeparator());
            indent = "        ";
        } else {
            indent = emitExpressionScopeStart(plan, c);
        }
        c.append(indent)
            .append("void* javan_return_value = ")
            .append(value)
            .append(";")
            .append(System.lineSeparator());
        c.append(indent).append("javan_runtime_lock_enter();").append(System.lineSeparator());
        c.append(indent).append("*result = javan_return_value;").append(System.lineSeparator());
        c.append(indent).append("javan_runtime_lock_leave();").append(System.lineSeparator());
        if (sourceContextSymbol.length() > 0) {
            emitSourceContextClear(c, indent, sourceContextSymbol);
        }
        c.append(indent).append("javan_gc_safe_point();").append(System.lineSeparator());
        if (plan.hasRootFrame()) {
            c.append(indent).append("javan_root_frame_pop(javan_expr_roots);").append(System.lineSeparator());
        }
        emitRootFramePop(rootFrameSymbol, hasRootFrame, c, indent);
        c.append(indent).append("return;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
    }

    private static void validateImportedNativeDescriptors(final NativeInteropConfig nativeInterop) {
        for (final NativeInteropConfig.ImportBinding binding : nativeInterop.imports()) {
            importedNativeSignature(binding);
        }
    }

    private static void validateNativeWrapperNamespace(
        final IrProgram program,
        final NativeInteropConfig nativeInterop
    ) {
        for (final NativeInteropConfig.ImportBinding binding : nativeInterop.imports()) {
            final String wrapper = CMethodSymbols.symbol(binding.entryPoint());
            for (final IrFunction function : program.functions()) {
                if (wrapper.equals(function.symbol())) {
                    throw nativeWrapperCollision(
                        wrapper,
                        binding.entryPoint(),
                        new EntryPoint(function.owner(), function.name(), function.descriptor())
                    );
                }
            }
            for (final IrDispatch dispatch : program.dispatches()) {
                if (wrapper.equals(dispatch.symbol())) {
                    throw new IllegalArgumentException(
                        "Native import wrapper symbol collision: " + wrapper + " for "
                            + binding.entryPoint().display() + " and generated dispatch " + dispatch.symbol()
                    );
                }
                for (final IrDispatchTarget target : dispatch.targets()) {
                    if (wrapper.equals(target.functionSymbol())) {
                        throw new IllegalArgumentException(
                            "Static native import cannot be a dispatch target: " + binding.entryPoint().display()
                                + " via generated dispatch " + dispatch.symbol()
                        );
                    }
                }
            }
        }
        for (int index = 0; index < nativeInterop.imports().size(); index++) {
            final NativeInteropConfig.ImportBinding binding = nativeInterop.imports().get(index);
            final String wrapper = nativeWrapperSymbol(index);
            for (final IrFunction function : program.functions()) {
                if (wrapper.equals(function.symbol())) {
                    throw new IllegalArgumentException(
                        "Native import private wrapper symbol collision: " + wrapper + " for "
                            + binding.entryPoint().display() + " and "
                            + new EntryPoint(function.owner(), function.name(), function.descriptor()).display()
                    );
                }
            }
            for (final IrDispatch dispatch : program.dispatches()) {
                if (wrapper.equals(dispatch.symbol())) {
                    throw new IllegalArgumentException(
                        "Native import private wrapper symbol collision: " + wrapper + " for "
                            + binding.entryPoint().display() + " and generated dispatch " + dispatch.symbol()
                    );
                }
            }
        }
    }

    private static String nativeWrapperSymbol(final int ordinal) {
        return "javan_native_import_wrapper_" + ordinal + "_fn";
    }

    private static IllegalArgumentException nativeWrapperCollision(
        final String symbol,
        final EntryPoint nativeMethod,
        final EntryPoint emittedMethod
    ) {
        return new IllegalArgumentException(
            "Native import wrapper symbol collision: " + symbol + " for "
                + nativeMethod.display() + " and " + emittedMethod.display()
        );
    }

    private static final class NativeWrapperSymbols {
        private final java.util.Map<String, String> wrappers;

        private NativeWrapperSymbols(final java.util.Map<String, String> wrappers) {
            this.wrappers = java.util.Map.copyOf(wrappers);
        }

        private static NativeWrapperSymbols create(final NativeInteropConfig nativeInterop) {
            final java.util.Map<String, String> wrappers = new java.util.LinkedHashMap<>();
            for (int index = 0; index < nativeInterop.imports().size(); index++) {
                final NativeInteropConfig.ImportBinding binding = nativeInterop.imports().get(index);
                wrappers.put(CMethodSymbols.symbol(binding.entryPoint()), nativeWrapperSymbol(index));
            }
            return new NativeWrapperSymbols(wrappers);
        }

        private String resolve(final String canonicalSymbol) {
            final String wrapper = wrappers.get(canonicalSymbol);
            return wrapper == null ? canonicalSymbol : wrapper;
        }

        private String wrapper(final NativeInteropConfig.ImportBinding binding) {
            return resolve(CMethodSymbols.symbol(binding.entryPoint()));
        }
    }

    private static void emitImportedNativeSignatures(
        final NativeInteropConfig nativeInterop,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        for (final NativeInteropConfig.ImportBinding binding : nativeInterop.imports()) {
            final ImportedNativeSignature signature = importedNativeSignature(binding);
            emitImportedNativeExternalSignature(binding, signature, c);
            c.append(';').append(System.lineSeparator());
            emitImportedNativeWrapperSignature(binding, signature, nativeWrapperSymbols, c);
            c.append(';').append(System.lineSeparator());
        }
    }

    private static void emitImportedNativeWrappers(
        final NativeInteropConfig nativeInterop,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        if (nativeInterop.imports().isEmpty()) {
            return;
        }
        c.append(System.lineSeparator());
        for (final NativeInteropConfig.ImportBinding binding : nativeInterop.imports()) {
            emitImportedNativeWrapper(binding, importedNativeSignature(binding), nativeWrapperSymbols, c);
        }
    }

    private static void emitImportedNativeWrapper(
        final NativeInteropConfig.ImportBinding binding,
        final ImportedNativeSignature signature,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        emitImportedNativeWrapperSignature(binding, signature, nativeWrapperSymbols, c);
        c.append(" {").append(System.lineSeparator());
        for (int index = 0; index < signature.parameterTypes().size(); index++) {
            if (signature.parameterTypes().get(index) == ImportedNativeAbiType.BYTE_ARRAY) {
                c.append("    JavanNativeImportedByteArray arg")
                    .append(index)
                    .append("_native = javan_native_import_byte_array(arg")
                    .append(index)
                    .append(");")
                    .append(System.lineSeparator());
            }
        }
        final String call = binding.externalSymbol() + "(" + importedNativeCallArguments(signature.parameterTypes()) + ")";
        if (signature.returnType() == ImportedNativeAbiType.VOID) {
            c.append("    ").append(call).append(';').append(System.lineSeparator());
            c.append("    return;").append(System.lineSeparator());
        } else {
            c.append("    return ").append(call).append(';').append(System.lineSeparator());
        }
        c.append('}').append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitImportedNativeExternalSignature(
        final NativeInteropConfig.ImportBinding binding,
        final ImportedNativeSignature signature,
        final StringBuilder c
    ) {
        c.append(signature.returnType().externalCName())
            .append(' ')
            .append(binding.externalSymbol())
            .append('(');
        emitImportedNativeParameters(signature.parameterTypes(), c, true);
        c.append(')');
    }

    private static void emitImportedNativeWrapperSignature(
        final NativeInteropConfig.ImportBinding binding,
        final ImportedNativeSignature signature,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        c.append("static ")
            .append(signature.returnType().wrapperCName())
            .append(' ')
            .append(nativeWrapperSymbols.wrapper(binding))
            .append('(');
        emitImportedNativeParameters(signature.parameterTypes(), c, false);
        c.append(')');
    }

    private static void emitImportedNativeParameters(
        final List<ImportedNativeAbiType> parameterTypes,
        final StringBuilder c,
        final boolean external
    ) {
        if (parameterTypes.isEmpty()) {
            c.append("void");
            return;
        }
        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0) {
                c.append(", ");
            }
            final ImportedNativeAbiType type = parameterTypes.get(index);
            c.append(external ? type.externalCName() : type.wrapperCName())
                .append(" arg")
                .append(index);
        }
    }

    private static String importedNativeCallArguments(final List<ImportedNativeAbiType> parameterTypes) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append("arg").append(index);
            if (parameterTypes.get(index) == ImportedNativeAbiType.BYTE_ARRAY) {
                result.append("_native");
            }
        }
        return result.toString();
    }

    private static ImportedNativeSignature importedNativeSignature(
        final NativeInteropConfig.ImportBinding binding
    ) {
        final String descriptor = binding.entryPoint().descriptor();
        if (descriptor.length() < 3 || descriptor.charAt(0) != '(') {
            throw unsupportedImportedNativeDescriptor(binding);
        }
        final List<ImportedNativeAbiType> parameterTypes = new java.util.ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final char value = descriptor.charAt(index);
            if (value == 'I') {
                parameterTypes.add(ImportedNativeAbiType.INT);
                index++;
            } else if (value == 'J') {
                parameterTypes.add(ImportedNativeAbiType.LONG);
                index++;
            } else if (value == 'F') {
                parameterTypes.add(ImportedNativeAbiType.FLOAT);
                index++;
            } else if (value == 'D') {
                parameterTypes.add(ImportedNativeAbiType.DOUBLE);
                index++;
            } else if (value == '['
                && index + 1 < descriptor.length()
                && descriptor.charAt(index + 1) == 'B') {
                parameterTypes.add(ImportedNativeAbiType.BYTE_ARRAY);
                index += 2;
            } else {
                throw unsupportedImportedNativeDescriptor(binding);
            }
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            throw unsupportedImportedNativeDescriptor(binding);
        }
        index++;
        if (index != descriptor.length() - 1) {
            throw unsupportedImportedNativeDescriptor(binding);
        }
        final ImportedNativeAbiType returnType;
        final char result = descriptor.charAt(index);
        if (result == 'V') {
            returnType = ImportedNativeAbiType.VOID;
        } else if (result == 'I') {
            returnType = ImportedNativeAbiType.INT;
        } else if (result == 'J') {
            returnType = ImportedNativeAbiType.LONG;
        } else if (result == 'F') {
            returnType = ImportedNativeAbiType.FLOAT;
        } else if (result == 'D') {
            returnType = ImportedNativeAbiType.DOUBLE;
        } else {
            throw unsupportedImportedNativeDescriptor(binding);
        }
        return new ImportedNativeSignature(parameterTypes, returnType);
    }

    private static IllegalArgumentException unsupportedImportedNativeDescriptor(
        final NativeInteropConfig.ImportBinding binding
    ) {
        return new IllegalArgumentException("Unsupported native import descriptor: " + binding.entryPoint().display());
    }

    private enum ImportedNativeAbiType {
        VOID("void", "void"),
        INT("int", "int"),
        LONG("long long", "long long"),
        FLOAT("float", "float"),
        DOUBLE("double", "double"),
        BYTE_ARRAY("JavanNativeImportedByteArray", "void*");

        private final String externalCName;
        private final String wrapperCName;

        ImportedNativeAbiType(final String externalCName, final String wrapperCName) {
            this.externalCName = externalCName;
            this.wrapperCName = wrapperCName;
        }

        private String externalCName() {
            return externalCName;
        }

        private String wrapperCName() {
            return wrapperCName;
        }
    }

    private record ImportedNativeSignature(
        List<ImportedNativeAbiType> parameterTypes,
        ImportedNativeAbiType returnType
    ) {
        private ImportedNativeSignature {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    private static void emitSignature(final IrFunction function, final StringBuilder c, final boolean isStatic) {
        if (isStatic) {
            c.append("static ");
        }
        final boolean objectResult = function.returnType() == javan.ir.IrType.OBJECT;
        c.append(objectResult ? "void" : function.returnType().cName()).append(' ').append(function.symbol()).append('(');
        if (objectResult) {
            c.append("void** result");
        }
        if (function.parameters().isEmpty() && !objectResult) {
            c.append("void");
        } else {
            for (int index = 0; index < function.parameters().size(); index++) {
                if (index > 0 || objectResult) {
                    c.append(", ");
                }
                final javan.ir.IrParameter parameter = function.parameters().get(index);
                c.append(parameter.type().cName()).append(' ').append(parameter.name());
            }
        }
        c.append(')');
    }

    private static void emitDispatchSignature(final IrDispatch dispatch, final StringBuilder c) {
        final boolean objectResult = dispatch.returnType() == javan.ir.IrType.OBJECT;
        c.append("static ").append(objectResult ? "void" : dispatch.returnType().cName()).append(' ').append(dispatch.symbol()).append('(');
        if (objectResult) {
            c.append("void** result");
        }
        for (int index = 0; index < dispatch.parameters().size(); index++) {
            if (index > 0 || objectResult) {
                c.append(", ");
            }
            final javan.ir.IrParameter parameter = dispatch.parameters().get(index);
            c.append(parameter.type().cName()).append(' ').append(parameter.name());
        }
        c.append(')');
    }

    private static String dispatchArguments(final IrDispatch dispatch) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < dispatch.parameters().size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(dispatch.parameters().get(index).name());
        }
        return result.toString();
    }

    private static String dispatchResultArguments(final IrDispatch dispatch) {
        final String arguments = dispatchArguments(dispatch);
        return arguments.isEmpty() ? "result" : "result, " + arguments;
    }

    private static void emitDefaultReturn(final javan.ir.IrType type, final StringBuilder c) {
        switch (type) {
            case VOID:
                c.append("    return;").append(System.lineSeparator());
                break;
            case INT:
                c.append("    return 0;").append(System.lineSeparator());
                break;
            case LONG:
                c.append("    return 0LL;").append(System.lineSeparator());
                break;
            case FLOAT:
                c.append("    return 0.0f;").append(System.lineSeparator());
                break;
            case DOUBLE:
                c.append("    return 0.0;").append(System.lineSeparator());
                break;
            case OBJECT:
                c.append("    return;").append(System.lineSeparator());
                break;
        }
    }

    private static void emitClassInitializers(
        final IrProgram program,
        final NativeWrapperSymbols nativeWrapperSymbols,
        final StringBuilder c
    ) {
        emitEnumConstantInitializers(program, c);
        final List<IrFunction> initializers = new java.util.ArrayList<>();
        for (final IrFunction function : program.functions()) {
            if ("<clinit>".equals(function.name())) {
                insertInitializer(initializers, function);
            }
        }
        for (final IrFunction function : initializers) {
            c.append("    ")
                .append(nativeWrapperSymbols.resolve(function.symbol()))
                .append("();")
                .append(System.lineSeparator());
        }
    }

    private static void emitEnumConstantInitializers(final IrProgram program, final StringBuilder c) {
        for (final IrClass classInfo : program.classes()) {
            if (classInfo.enumConstants().isEmpty()) {
                continue;
            }
            for (final String constant : classInfo.enumConstants()) {
                c.append("    ")
                    .append(staticFieldSymbol(classInfo.jvmName(), constant))
                    .append(" = javan_string_from(\"")
                    .append(escapeCString(constant))
                    .append("\");")
                    .append(System.lineSeparator());
            }
        }
    }

    private static void insertInitializer(final List<IrFunction> initializers, final IrFunction function) {
        int index = initializers.size();
        while (index > 0 && Strings2.compareAscii(initializers.get(index - 1).owner(), function.owner()) > 0) {
            index--;
        }
        initializers.add(index, function);
    }

    private static String[] ownerField(final String value) {
        final int separator = value.indexOf('#');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Invalid owner field value: " + value);
        }
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }

    private static String classSymbol(final String className) {
        return "javan_class_" + sanitize(className);
    }

    private static String fieldSymbol(final String fieldName) {
        return "field_" + sanitize(fieldName);
    }

    private static String staticFieldSymbol(final String owner, final String fieldName) {
        return "javan_static_" + sanitize(owner) + "_" + fieldSymbol(fieldName);
    }

    private static String typeFieldOffsetsSymbol(final String className) {
        return "javan_type_fields_" + sanitize(className);
    }

    private static String allocatorSymbol(final String className) {
        return "javan_new_" + sanitize(className);
    }

    private static String cloneSymbol(final String className) {
        return "javan_clone_" + sanitize(className);
    }

    private static String rootFrameSymbol(final IrFunction function) {
        return "javan_roots_" + sanitize(function.symbol());
    }

    private static String enumOrdinalSymbol(final String className) {
        return "javan_enum_ordinal_" + sanitize(className);
    }

    private static void emitPanicAt(
        final StringBuilder c,
        final String indent,
        final String value,
        final IrSourceLocation location
    ) {
        c.append(indent)
            .append("javan_panic_at(")
            .append(emitCStringLiteral("JAVAN-RUNTIME-PANIC"))
            .append(", ")
            .append(emitCStringLiteral("uncaught Java exception"))
            .append(", ")
            .append(emitCStringLiteral(displayClassName(location.className())))
            .append(", ")
            .append(emitCStringLiteral(location.methodName() + location.descriptor()))
            .append(", ")
            .append(emitCStringLiteral(location.sourceFile().orElse("")))
            .append(", ")
            .append(sourceLineNumber(location))
            .append(", ")
            .append(location.bytecodeOffset())
            .append(", ")
            .append(emitCStringLiteral(location.sourceLine().orElse("")))
            .append(", ")
            .append(emitCStringLiteral("An exception reached the native boundary without a supported catch block."))
            .append(", ")
            .append(emitCStringLiteral("Catch it in Java or let the application terminate intentionally."))
            .append(", (const char*) ")
            .append(value)
            .append(");")
            .append(System.lineSeparator());
    }

    private static String displayClassName(final String className) {
        return Strings2.replaceChar(className, '/', '.');
    }

    private static int sourceLineNumber(final IrSourceLocation location) {
        if (location.lineNumber().isEmpty()) {
            return -1;
        }
        return location.lineNumber().orElseThrow().intValue();
    }

    private static java.util.Map<String, Integer> typeIds(final IrProgram program) {
        final java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < program.classes().size(); index++) {
            result.put(program.classes().get(index).jvmName(), index + 1);
        }
        return java.util.Map.copyOf(result);
    }

    private static String sanitize(final String value) {
        return value
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }

    private static String escapeCString(final String value) {
        final StringBuilder result = new StringBuilder();
        final int valueLength = value.length();
        for (int index = 0; index < valueLength; index++) {
            final char current = value.charAt(index);
            if (current <= 0x7F) {
                appendEscapedCByte(result, current);
            } else if (current <= 0x7FF) {
                appendEscapedCByte(result, 0xC0 | (current >> 6));
                appendEscapedCByte(result, 0x80 | (current & 0x3F));
            } else if (isHighSurrogate(current)
                && index + 1 < valueLength
                && isLowSurrogate(value.charAt(index + 1))) {
                final char low = value.charAt(++index);
                final int codePoint = 0x10000 + ((current - 0xD800) << 10) + (low - 0xDC00);
                appendEscapedCByte(result, 0xF0 | (codePoint >> 18));
                appendEscapedCByte(result, 0x80 | ((codePoint >> 12) & 0x3F));
                appendEscapedCByte(result, 0x80 | ((codePoint >> 6) & 0x3F));
                appendEscapedCByte(result, 0x80 | (codePoint & 0x3F));
            } else {
                appendEscapedCByte(result, 0xE0 | (current >> 12));
                appendEscapedCByte(result, 0x80 | ((current >> 6) & 0x3F));
                appendEscapedCByte(result, 0x80 | (current & 0x3F));
            }
        }
        return result.toString();
    }

    private static String emitCStringLiteral(final String value) {
        final int maxChunkLength = 120;
        final String escaped = escapeCString(value);
        final int escapedLength = escaped.length();
        final StringBuilder result = new StringBuilder(escapedLength + 16);
        StringBuilder chunk = new StringBuilder(Math.min(escapedLength, maxChunkLength));
        for (int index = 0; index < escapedLength;) {
            final int tokenLength = escapedCStringTokenLength(escaped, escapedLength, index);
            if (!chunk.isEmpty() && chunk.length() + tokenLength > maxChunkLength) {
                appendCStringChunk(result, chunk);
                chunk = new StringBuilder(Math.min(escapedLength - index, maxChunkLength));
            }
            for (int offset = 0; offset < tokenLength; offset++) {
                chunk.append(escaped.charAt(index + offset));
            }
            index += tokenLength;
        }
        appendCStringChunk(result, chunk);
        return result.toString();
    }

    private static int escapedCStringTokenLength(final String escaped, final int escapedLength, final int index) {
        if (escaped.charAt(index) != '\\') {
            return 1;
        }
        if (index + 1 < escapedLength) {
            final char next = escaped.charAt(index + 1);
            if (next >= '0' && next <= '7') {
                return 4;
            }
        }
        return 2;
    }

    private static boolean isHighSurrogate(final char value) {
        return value >= 0xD800 && value <= 0xDBFF;
    }

    private static boolean isLowSurrogate(final char value) {
        return value >= 0xDC00 && value <= 0xDFFF;
    }

    private static void appendCStringChunk(final StringBuilder result, final StringBuilder chunk) {
        if (!result.isEmpty()) {
            result.append(System.lineSeparator()).append("        ");
        }
        result.append('"').append(chunk.toString()).append('"');
    }

    private static void appendEscapedCByte(final StringBuilder result, final int value) {
        switch (value) {
            case '\\':
                result.append('\\').append('\\');
                return;
            case '"':
                result.append('\\').append('"');
                return;
            case '\n':
                result.append('\\').append('n');
                return;
            case '\r':
                result.append('\\').append('r');
                return;
            case '\t':
                result.append('\\').append('t');
                return;
            default:
                if (value < 32 || value > 126) {
                    result.append('\\');
                    appendEscapedOctal(result, value);
                    return;
                }
                result.append((char) value);
        }
    }

    private static void appendEscapedOctal(final StringBuilder result, final int value) {
        result.append((char) ('0' + ((value >> 6) & 7)));
        result.append((char) ('0' + ((value >> 3) & 7)));
        result.append((char) ('0' + (value & 7)));
    }

    private static boolean usesGeneratedObjectClone(final IrProgram program) {
        for (final IrFunction function : program.functions()) {
            for (final IrInstruction instruction : function.instructions()) {
                if (instruction.expression().isPresent()
                    && usesGeneratedObjectClone(instruction.expression().orElseThrow())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean usesGeneratedObjectClone(final IrExpression expression) {
        if (expression.kind() == IrExpression.Kind.CALL && GENERATED_OBJECT_CLONE_SYMBOL.equals(expression.value())) {
            return true;
        }
        for (final IrExpression argument : expression.arguments()) {
            if (usesGeneratedObjectClone(argument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesRootedResultCall(final IrExpression expression) {
        if (expression.kind() != IrExpression.Kind.CALL || expression.type() != javan.ir.IrType.OBJECT) {
            return false;
        }
        return GENERATED_OBJECT_CLONE_SYMBOL.equals(expression.value())
            || isArrayCopyIntoCall(expression.value())
            || isStringResultIntoCall(expression.value())
            || isThreadResultIntoCall(expression.value());
    }

    private static String rootedResultCallSymbol(final IrExpression expression) {
        if (GENERATED_OBJECT_CLONE_SYMBOL.equals(expression.value())) {
            return GENERATED_OBJECT_CLONE_SYMBOL;
        }
        return expression.value() + "_into";
    }

    private static boolean isArrayCopyIntoCall(final String symbol) {
        return switch (symbol) {
            case "javan_arrays_copy_of_object",
                "javan_arrays_copy_of_boolean",
                "javan_arrays_copy_of_int",
                "javan_arrays_copy_of_long",
                "javan_arrays_copy_of_float",
                "javan_arrays_copy_of_double",
                "javan_arrays_copy_of_byte",
                "javan_arrays_copy_of_short",
                "javan_arrays_copy_of_char" -> true;
            default -> false;
        };
    }

    private static boolean isStringResultIntoCall(final String symbol) {
        return switch (symbol) {
            case "javan_string_value_of_int",
                "javan_string_value_of_long",
                "javan_string_value_of_float",
                "javan_string_value_of_double",
                "javan_string_value_of_bool",
                "javan_string_value_of_char" -> true;
            default -> false;
        };
    }

    private static boolean isThreadResultIntoCall(final String symbol) {
        return switch (symbol) {
            case "javan_thread_new",
                "javan_thread_new_virtual",
                "javan_virtual_thread_builder_start",
                "javan_virtual_thread_builder_unstarted",
                "javan_virtual_thread_factory_new_thread",
                "javan_virtual_thread_executor_submit",
                "javan_scheduled_thread_pool_executor_schedule",
                "javan_scheduled_thread_pool_executor_schedule_at_fixed_rate",
                "javan_scheduled_thread_pool_executor_schedule_with_fixed_delay" -> true;
            default -> false;
        };
    }

    private static void emitGeneratedObjectCloneHelpers(final IrProgram program, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        for (final IrClass classInfo : program.classes()) {
            if (!classInfo.cloneable()) {
                continue;
            }
            emitGeneratedObjectCloneHelper(classInfo, c);
        }

        emitGeneratedObjectCloneDispatch(program, typeIds, c);
    }

    private static void emitGeneratedObjectCloneHelper(final IrClass classInfo, final StringBuilder c) {
        final String classSymbol = classInfo.symbol();
        final String functionSymbol = cloneSymbol(classInfo.jvmName());

        c.append("static void ")
            .append(functionSymbol)
            .append("(void** result, void* value) {")
            .append(System.lineSeparator())
            .append("\tif (result == 0) {")
            .append(System.lineSeparator())
            .append("\t\tjavan_panic(\"invalid object clone result\");")
            .append(System.lineSeparator())
            .append("\t}")
            .append(System.lineSeparator())
            .append("\tstruct ")
            .append(classSymbol)
            .append("* source = (struct ")
            .append(classSymbol)
            .append("*) value;")
            .append(System.lineSeparator())
            .append("\tstruct ")
            .append(classSymbol)
            .append("* copy = (struct ")
            .append(classSymbol)
            .append("*) 0;")
            .append(System.lineSeparator())
            .append("\tvoid** javan_clone_roots[] = {")
            .append(System.lineSeparator())
            .append("\t\t(void**) &source,")
            .append(System.lineSeparator())
            .append("\t\tresult")
            .append(System.lineSeparator())
            .append("\t};")
            .append(System.lineSeparator())
            .append("\tjavan_root_frame_push(javan_clone_roots, 2);")
            .append(System.lineSeparator())
            .append("\tjavan_runtime_lock_enter();")
            .append(System.lineSeparator())
            .append("\t*result = ")
            .append(allocatorSymbol(classInfo.jvmName()))
            .append("();")
            .append(System.lineSeparator())
            .append("\tcopy = (struct ")
            .append(classSymbol)
            .append("*) *result;")
            .append(System.lineSeparator());
        for (final javan.ir.IrField field : classInfo.fields()) {
            emitCloneFieldCopy(field, c);
        }
        c.append("\tjavan_runtime_lock_leave();").append(System.lineSeparator());
        c.append("\tjavan_root_frame_pop(javan_clone_roots);").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitCloneFieldCopy(final javan.ir.IrField field, final StringBuilder c) {
        c.append("\tcopy->")
            .append(field.symbol())
            .append(" = source->")
            .append(field.symbol())
            .append(";")
            .append(System.lineSeparator());
    }

    private static void emitGeneratedObjectCloneDispatch(
        final IrProgram program,
        final java.util.Map<String, Integer> typeIds,
        final StringBuilder c
    ) {
        c.append("static void ")
            .append(GENERATED_OBJECT_CLONE_SYMBOL)
            .append("(void** result, void* value) {")
            .append(System.lineSeparator());
        c.append("\tif (result == 0) {").append(System.lineSeparator());
        c.append("\t\tjavan_panic(\"invalid object clone result\");").append(System.lineSeparator());
        c.append("\t}").append(System.lineSeparator());
        c.append("\tif (value == 0) {").append(System.lineSeparator());
        c.append("\t\tjavan_panic(\"null object clone\");").append(System.lineSeparator());
        c.append("\t}").append(System.lineSeparator());
        c.append("\tstruct javan_object_header* header = (struct javan_object_header*) value;").append(System.lineSeparator());
        c.append("\tif (header->_javan_runtime_state != 0 || header->_javan_runtime_kind != 0) {").append(System.lineSeparator());
        c.append("\t\tjavan_panic(\"runtime-attached object clone is not supported\");").append(System.lineSeparator());
        c.append("\t}").append(System.lineSeparator());
        c.append("\tswitch (header->_javan_type_id) {").append(System.lineSeparator());
        for (final IrClass classInfo : program.classes()) {
            if (!classInfo.cloneable()) {
                continue;
            }
            emitGeneratedObjectCloneDispatchCase(classInfo, typeIds, c);
        }
        c.append("\t\tdefault:").append(System.lineSeparator());
        c.append("\t\t\tjavan_panic(\"CloneNotSupportedException\");").append(System.lineSeparator());
        c.append("\t}").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitGeneratedObjectCloneDispatchCase(
        final IrClass classInfo,
        final java.util.Map<String, Integer> typeIds,
        final StringBuilder c
    ) {
        final int typeId = typeIds.get(classInfo.jvmName()).intValue();
        c.append("\t\tcase ").append(typeId).append(":").append(System.lineSeparator());
        c.append("\t\t\t")
            .append(cloneSymbol(classInfo.jvmName()))
            .append("(result, value);")
            .append(System.lineSeparator());
        c.append("\t\t\treturn;")
            .append(System.lineSeparator());
    }

    private static boolean usesNonFiniteFloatingLiteral(final IrProgram program) {
        for (final IrFunction function : program.functions()) {
            for (final IrInstruction instruction : function.instructions()) {
                final java.util.Optional<IrExpression> expression = instruction.expression();
                if (expression.isPresent() && usesNonFiniteFloatingLiteral(expression.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean usesNonFiniteFloatingLiteral(final IrExpression expression) {
        if (isNonFiniteFloatingLiteral(expression.value()) && (expression.kind() == IrExpression.Kind.FLOAT_LITERAL || expression.kind() == IrExpression.Kind.DOUBLE_LITERAL)) {
            return true;
        }
        for (final IrExpression argument : expression.arguments()) {
            if (usesNonFiniteFloatingLiteral(argument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNonFiniteFloatingLiteral(final String value) {
        return "Infinity".equals(value) || "-Infinity".equals(value) || "NaN".equals(value);
    }

    private static String floatAndDoubleLiteral(final String value) {
        if ("Infinity".equals(value)) {
            return "INFINITY";
        }
        if ("-Infinity".equals(value)) {
            return "-INFINITY";
        }
        if ("NaN".equals(value)) {
            return "NAN";
        }
        return value;
    }

    private record CodegenFeatures(boolean generatedObjectClone, boolean nonFiniteFloatingLiteral) {
    }
}
