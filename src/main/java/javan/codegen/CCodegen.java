package javan.codegen;

import javan.ir.IrFunction;
import javan.build.AbiType;
import javan.build.ExportedMethod;
import javan.ir.IrClass;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Emits portable C for the initial javan IR profile.
 */
public final class CCodegen {
    /**
     * Writes the generated C program.
     *
     * @param program IR program
     * @param generatedDirectory output directory
     * @return generated main C file
     * @throws IOException when writing fails
     */
    public Path generate(final IrProgram program, final Path generatedDirectory) throws IOException {
        final StringBuilder c = new StringBuilder();
        c.append("#include \"javan_runtime.h\"").append(System.lineSeparator());
        c.append("#include <stddef.h>").append(System.lineSeparator()).append(System.lineSeparator());
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
        final boolean emittedReturnRoot = emitReturnRootSlot(program, c);
        if (emittedReturnRoot) {
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
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatchSignature(dispatch, c);
            c.append(";").append(System.lineSeparator());
        }
        c.append(System.lineSeparator());
        emitAllocators(program, c);
        emitEnumOrdinalHelpers(program, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatch(program, dispatch, c);
        }
        for (final IrFunction function : program.functions()) {
            emitFunction(program, function, c, true);
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
        final StringBuilder c = new StringBuilder();
        c.append("#include \"javan_runtime.h\"").append(System.lineSeparator());
        c.append("#include <stddef.h>").append(System.lineSeparator()).append(System.lineSeparator());
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
        final boolean emittedReturnRoot = emitReturnRootSlot(program, c);
        if (emittedReturnRoot) {
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
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatchSignature(dispatch, c);
            c.append(";").append(System.lineSeparator());
        }
        c.append(System.lineSeparator());
        emitAllocators(program, c);
        emitEnumOrdinalHelpers(program, c);
        for (final IrDispatch dispatch : program.dispatches()) {
            emitDispatch(program, dispatch, c);
        }
        for (final IrFunction function : program.functions()) {
            emitFunction(program, function, c, false);
        }
        emitLibraryInitializer(program, c);
        for (final ExportedMethod export : exports) {
            emitExportWrapper(export, c);
        }
        return Files2.writeString(generatedDirectory.resolve("library.c"), c.toString());
    }

    private static void emitObjectHeader(final StringBuilder c) {
        c.append("struct javan_object_header {").append(System.lineSeparator());
        c.append("    int _javan_type_id;").append(System.lineSeparator());
        c.append("};").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitStruct(final IrClass classInfo, final StringBuilder c) {
        c.append("struct ").append(classInfo.symbol()).append(" {").append(System.lineSeparator());
        c.append("    int _javan_type_id;").append(System.lineSeparator());
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
                    .append(escapeCString(classInfo.jvmName()))
                    .append("\", ")
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

    private static boolean emitReturnRootSlot(final IrProgram program, final StringBuilder c) {
        if (!hasObjectReturn(program)) {
            return false;
        }
        c.append("static void* ").append(returnRootSymbol()).append(" = 0;").append(System.lineSeparator());
        return true;
    }

    private static boolean hasObjectReturn(final IrProgram program) {
        for (final IrFunction function : program.functions()) {
            if (function.returnType() == javan.ir.IrType.OBJECT) {
                return true;
            }
        }
        for (final IrDispatch dispatch : program.dispatches()) {
            if (dispatch.returnType() == javan.ir.IrType.OBJECT) {
                return true;
            }
        }
        return false;
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
        if (hasObjectReturn(program)) {
            result.add(returnRootSymbol());
        }
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

    private static void emitDispatch(final IrProgram program, final IrDispatch dispatch, final StringBuilder c) {
        final java.util.Map<String, Integer> typeIds = typeIds(program);
        emitDispatchSignature(dispatch, c);
        c.append(" {").append(System.lineSeparator());
        c.append("    if (self == 0) {").append(System.lineSeparator());
        c.append("        javan_panic(\"null dispatch\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    switch (((struct javan_object_header*) self)->_javan_type_id) {").append(System.lineSeparator());
        for (final IrDispatchTarget target : dispatch.targets()) {
            c.append("        case ")
                .append(typeIds.get(target.owner()).intValue())
                .append(": ");
            if (dispatch.returnType() == javan.ir.IrType.VOID) {
                c.append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append("); return;");
            } else {
                c.append("return ").append(target.functionSymbol()).append("(").append(dispatchArguments(dispatch)).append(");");
            }
            c.append(System.lineSeparator());
        }
        c.append("        default: javan_panic(\"unsupported dispatch target\");").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        emitDefaultReturn(dispatch.returnType(), c);
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitFunction(
        final IrProgram program,
        final IrFunction function,
        final StringBuilder c,
        final boolean emitMain
    ) {
        final boolean entry = appEntry(emitMain, function, program);
        if (entry) {
            c.append("int main(int argc, char** argv) {").append(System.lineSeparator());
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
        emitRootFramePush(rootFrameSymbol, rootNames, c);
        if (entry) {
            c.append("    javan_register_generated_type_descriptors();").append(System.lineSeparator());
            c.append("    javan_register_generated_roots();").append(System.lineSeparator());
            emitClassInitializers(program, c);
            c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        } else {
            c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        }
        for (final IrInstruction instruction : function.instructions()) {
            emitInstruction(instruction, entry, rootFrameSymbol, !rootNames.isEmpty(), c);
            emitStatementSafePoint(instruction, c);
        }
        if (entry) {
            emitRootFramePop(rootFrameSymbol, !rootNames.isEmpty(), c);
            c.append("    return 0;").append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
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

    private static void emitLibraryInitializer(final IrProgram program, final StringBuilder c) {
        c.append("static int javan_library_initialized = 0;").append(System.lineSeparator());
        c.append("static void javan_library_init(void) {").append(System.lineSeparator());
        c.append("    if (javan_library_initialized != 0) {").append(System.lineSeparator());
        c.append("        return;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
        c.append("    javan_library_initialized = 1;").append(System.lineSeparator());
        c.append("    javan_register_generated_type_descriptors();").append(System.lineSeparator());
        c.append("    javan_register_generated_roots();").append(System.lineSeparator());
        emitClassInitializers(program, c);
        c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static void emitExportWrapper(final ExportedMethod export, final StringBuilder c) {
        emitExportSignature(export, c);
        c.append(" {").append(System.lineSeparator());
        c.append("    javan_library_init();").append(System.lineSeparator());
        final List<Integer> byteArrayArguments = byteArrayExportArgumentIndexes(export);
        for (final int index : byteArrayArguments) {
            c.append("    void* arg").append(index).append("_array = 0;").append(System.lineSeparator());
        }
        emitExportWrapperRootFramePush(byteArrayArguments, c);
        for (final int index : byteArrayArguments) {
            c.append("    arg").append(index).append("_array = javan_byte_array_from(arg")
                .append(index)
                .append(".data, arg")
                .append(index)
                .append(".length);")
                .append(System.lineSeparator());
        }
        final String call = export.internalSymbol() + "(" + exportArguments(export) + ")";
        final AbiType returnType = export.returnType();
        if (returnType == AbiType.VOID) {
            c.append("    ").append(call).append(";").append(System.lineSeparator());
            emitExportWrapperCleanup(byteArrayArguments, c);
        } else if (returnType == AbiType.STRING) {
            c.append("    void* javan_export_object_result = ").append(call).append(";").append(System.lineSeparator());
            emitExportWrapperReturnRootFramePush(c);
            c.append("    char* javan_export_result = javan_string_export((const char*) javan_export_object_result);").append(System.lineSeparator());
            emitExportWrapperReturnRootFramePop(c);
            emitExportWrapperCleanup(byteArrayArguments, c);
            c.append("    return javan_export_result;").append(System.lineSeparator());
        } else if (returnType == AbiType.BYTE_ARRAY) {
            c.append("    void* javan_export_object_result = ").append(call).append(";").append(System.lineSeparator());
            emitExportWrapperReturnRootFramePush(c);
            c.append("    JavanByteArray javan_export_result = javan_byte_array_export(javan_export_object_result);").append(System.lineSeparator());
            emitExportWrapperReturnRootFramePop(c);
            emitExportWrapperCleanup(byteArrayArguments, c);
            c.append("    return javan_export_result;").append(System.lineSeparator());
        } else {
            c.append("    ").append(returnType.cReturnName()).append(" javan_export_result = ").append(call).append(";").append(System.lineSeparator());
            emitExportWrapperCleanup(byteArrayArguments, c);
            c.append("    return javan_export_result;").append(System.lineSeparator());
        }
        c.append("}").append(System.lineSeparator()).append(System.lineSeparator());
    }

    private static List<Integer> byteArrayExportArgumentIndexes(final ExportedMethod export) {
        final List<Integer> result = new java.util.ArrayList<>();
        for (int index = 0; index < export.parameterTypes().size(); index++) {
            if (export.parameterTypes().get(index) == AbiType.BYTE_ARRAY) {
                result.add(index);
            }
        }
        return List.copyOf(result);
    }

    private static void emitExportWrapperReturnRootFramePush(final StringBuilder c) {
        c.append("    void** javan_export_result_roots[] = {").append(System.lineSeparator());
        c.append("        (void**) &javan_export_object_result").append(System.lineSeparator());
        c.append("    };").append(System.lineSeparator());
        c.append("    javan_root_frame_push(javan_export_result_roots, 1);").append(System.lineSeparator());
    }

    private static void emitExportWrapperReturnRootFramePop(final StringBuilder c) {
        c.append("    javan_root_frame_pop(javan_export_result_roots);").append(System.lineSeparator());
    }

    private static void emitExportWrapperRootFramePush(final List<Integer> byteArrayArguments, final StringBuilder c) {
        if (byteArrayArguments.isEmpty()) {
            return;
        }
        c.append("    void** javan_export_roots[] = {").append(System.lineSeparator());
        for (int position = 0; position < byteArrayArguments.size(); position++) {
            final int argumentIndex = byteArrayArguments.get(position);
            c.append("        (void**) &arg").append(argumentIndex).append("_array");
            if (position < byteArrayArguments.size() - 1) {
                c.append(',');
            }
            c.append(System.lineSeparator());
        }
        c.append("    };").append(System.lineSeparator());
        c.append("    javan_root_frame_push(javan_export_roots, ")
            .append(byteArrayArguments.size())
            .append(");")
            .append(System.lineSeparator());
    }

    private static void emitExportWrapperCleanup(final List<Integer> byteArrayArguments, final StringBuilder c) {
        if (byteArrayArguments.isEmpty()) {
            return;
        }
        c.append("    javan_root_frame_pop(javan_export_roots);").append(System.lineSeparator());
        for (final int index : byteArrayArguments) {
            c.append("    javan_free(arg").append(index).append("_array);").append(System.lineSeparator());
        }
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
                arguments.append("(void*) arg").append(index);
            } else if (type == AbiType.BYTE_ARRAY) {
                arguments.append("arg").append(index).append("_array");
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
        for (final String assignment : plan.assignments()) {
            c.append("        ").append(assignment).append(System.lineSeparator());
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
        final javan.ir.IrExpression expression
    ) {
        final ExpressionPlan plan = new ExpressionPlan();
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
        final javan.ir.IrExpression expression
    ) {
        final ExpressionPlan plan = new ExpressionPlan();
        final String value = plan.expression(expression);
        final String indent = emitExpressionScopeStart(plan, c);
        c.append(indent)
            .append(target)
            .append(" = ")
            .append(value)
            .append(";")
            .append(System.lineSeparator());
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitFieldAssignment(
        final StringBuilder c,
        final String[] ownerField,
        final java.util.List<javan.ir.IrExpression> arguments
    ) {
        final ExpressionPlan plan = new ExpressionPlan();
        final String receiver = plan.expression(arguments.get(0));
        final String value = plan.expression(arguments.get(1));
        final String indent = emitExpressionScopeStart(plan, c);
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
        emitExpressionScopeEnd(plan, c);
    }

    private static void emitArraySet(
        final StringBuilder c,
        final String function,
        final java.util.List<javan.ir.IrExpression> arguments
    ) {
        final ExpressionPlan plan = new ExpressionPlan();
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
        final javan.ir.IrExpression condition
    ) {
        final ExpressionPlan plan = new ExpressionPlan();
        final String value = plan.expression(condition);
        if (plan.isEmpty()) {
            c.append("    if (")
                .append(value)
                .append(") goto ")
                .append(label)
                .append(";")
                .append(System.lineSeparator());
            return;
        }
        final String indent = emitExpressionScopeStart(plan, c);
        c.append(indent)
            .append("if (")
            .append(value)
            .append(") {")
            .append(System.lineSeparator());
        if (plan.hasRootFrame()) {
            c.append(indent).append("    javan_root_frame_pop(javan_expr_roots);").append(System.lineSeparator());
        }
        c.append(indent).append("    goto ").append(label).append(";").append(System.lineSeparator());
        c.append(indent).append("}").append(System.lineSeparator());
        emitExpressionScopeEnd(plan, c);
    }

    private static final class ExpressionPlan {
        private final java.util.List<Temporary> temporaries = new java.util.ArrayList<>();
        private final java.util.List<String> assignments = new java.util.ArrayList<>();

        String expression(final javan.ir.IrExpression expression) {
            final String raw = rawExpression(expression);
            if (!needsTemporary(expression)) {
                return raw;
            }
            final String temporary = "javan_expr_tmp_" + temporaries.size();
            temporaries.add(new Temporary(expression.type(), temporary));
            assignments.add(temporary + " = " + raw + ";");
            return temporary;
        }

        boolean isEmpty() {
            return temporaries.isEmpty();
        }

        java.util.List<Temporary> temporaries() {
            return java.util.List.copyOf(temporaries);
        }

        java.util.List<String> assignments() {
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
                    return expression.value() + "(" + expressionArguments(expression.arguments()) + ")";
                case OBJECT_ALLOCATION:
                    return allocatorSymbol(expression.value()) + "()";
                case OBJECT_ARRAY_ALLOCATION:
                    return "javan_object_array_new(" + expression(expression.arguments().get(0)) + ")";
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
        final IrInstruction instruction,
        final boolean entry,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final StringBuilder c
    ) {
        switch (instruction.op()) {
            case PRINTLN_LITERAL:
                c.append("    javan_println(\"")
                    .append(escapeCString(instruction.value().orElseThrow()))
                    .append("\");")
                    .append(System.lineSeparator());
                break;
            case PRINTLN_INT:
                emitPrintCall(c, "javan_println_int", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_ERROR_INT:
                emitPrintCall(c, "javan_eprintln_int", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_LONG:
                emitPrintCall(c, "javan_println_long", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_ERROR_LONG:
                emitPrintCall(c, "javan_eprintln_long", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_FLOAT:
                emitPrintCall(c, "javan_println_float", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_ERROR_FLOAT:
                emitPrintCall(c, "javan_eprintln_float", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_DOUBLE:
                emitPrintCall(c, "javan_println_double", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_ERROR_DOUBLE:
                emitPrintCall(c, "javan_eprintln_double", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_BOOLEAN:
                emitPrintCall(c, "javan_println_bool", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_ERROR_BOOLEAN:
                emitPrintCall(c, "javan_eprintln_bool", "", instruction.expression().orElseThrow());
                break;
            case PRINTLN_OBJECT:
                emitPrintCall(c, "javan_println", "(const char*) ", instruction.expression().orElseThrow());
                break;
            case PRINTLN_ERROR_OBJECT:
                emitPrintCall(c, "javan_eprintln", "(const char*) ", instruction.expression().orElseThrow());
                break;
            case PRINT_OBJECT:
                emitPrintCall(c, "javan_print", "(const char*) ", instruction.expression().orElseThrow());
                break;
            case PRINT_ERROR_OBJECT:
                emitPrintCall(c, "javan_eprint", "(const char*) ", instruction.expression().orElseThrow());
                break;
            case CALL_STATIC_VOID:
                if (instruction.expression().isPresent()) {
                    final ExpressionPlan plan = new ExpressionPlan();
                    final String call = plan.expression(instruction.expression().orElseThrow());
                    final String indent = emitExpressionScopeStart(plan, c);
                    c.append(indent).append(call).append(";").append(System.lineSeparator());
                    emitExpressionScopeEnd(plan, c);
                } else {
                    c.append("    ").append(instruction.value().orElseThrow()).append("();").append(System.lineSeparator());
                }
                break;
            case ASSIGN_INT:
            case ASSIGN_LONG:
            case ASSIGN_FLOAT:
            case ASSIGN_DOUBLE:
            case ASSIGN_OBJECT:
                emitAssignment(c, instruction.value().orElseThrow(), instruction.expression().orElseThrow());
                break;
            case ASSIGN_FIELD_INT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments);
                break;
            }
            case ASSIGN_FIELD_LONG: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments);
                break;
            }
            case ASSIGN_FIELD_FLOAT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments);
                break;
            }
            case ASSIGN_FIELD_DOUBLE: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments);
                break;
            }
            case ASSIGN_FIELD_OBJECT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitFieldAssignment(c, ownerField, arguments);
                break;
            }
            case ASSIGN_STATIC_FIELD_INT:
            case ASSIGN_STATIC_FIELD_LONG:
            case ASSIGN_STATIC_FIELD_FLOAT:
            case ASSIGN_STATIC_FIELD_DOUBLE:
            case ASSIGN_STATIC_FIELD_OBJECT: {
                final String[] ownerField = ownerField(instruction.value().orElseThrow());
                emitAssignment(c, staticFieldSymbol(ownerField[0], ownerField[1]), instruction.expression().orElseThrow());
                break;
            }
            case ASSIGN_ARRAY_OBJECT: {
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitArraySet(c, "javan_object_array_set", arguments);
                break;
            }
            case ASSIGN_ARRAY_INT: {
                final java.util.List<javan.ir.IrExpression> arguments = instruction.expression().orElseThrow().arguments();
                emitArraySet(c, "javan_int_array_set", arguments);
                break;
            }
            case ASSIGN_ARRAY_BYTE:
                emitArraySet(c, "javan_byte_array_set", instruction.expression().orElseThrow().arguments());
                break;
            case ASSIGN_ARRAY_SHORT:
                emitArraySet(c, "javan_short_array_set", instruction.expression().orElseThrow().arguments());
                break;
            case ASSIGN_ARRAY_CHAR:
                emitArraySet(c, "javan_char_array_set", instruction.expression().orElseThrow().arguments());
                break;
            case ASSIGN_ARRAY_LONG:
                emitArraySet(c, "javan_long_array_set", instruction.expression().orElseThrow().arguments());
                break;
            case ASSIGN_ARRAY_FLOAT:
                emitArraySet(c, "javan_float_array_set", instruction.expression().orElseThrow().arguments());
                break;
            case ASSIGN_ARRAY_DOUBLE:
                emitArraySet(c, "javan_double_array_set", instruction.expression().orElseThrow().arguments());
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
                emitBranchIf(c, instruction.value().orElseThrow(), instruction.expression().orElseThrow());
                break;
            case PANIC: {
                final ExpressionPlan plan = new ExpressionPlan();
                final String value = plan.expression(instruction.expression().orElseThrow());
                final String indent = emitExpressionScopeStart(plan, c);
                c.append(indent)
                    .append("javan_panic((const char*) ")
                    .append(value)
                    .append(");")
                    .append(System.lineSeparator());
                emitExpressionScopeEnd(plan, c);
                break;
            }
            case RETURN_VOID:
                if (!entry) {
                    emitRootFramePop(rootFrameSymbol, hasRootFrame, c);
                    c.append("    return;").append(System.lineSeparator());
                }
                break;
            case RETURN_INT:
            case RETURN_LONG:
            case RETURN_FLOAT:
            case RETURN_DOUBLE:
            case RETURN_OBJECT:
                emitReturnValue(instruction.expression().orElseThrow(), rootFrameSymbol, hasRootFrame, c);
                break;
        }
    }

    private static void emitStatementSafePoint(final IrInstruction instruction, final StringBuilder c) {
        switch (instruction.op()) {
            case JUMP:
            case PANIC:
            case RETURN_VOID:
            case RETURN_INT:
            case RETURN_LONG:
            case RETURN_FLOAT:
            case RETURN_DOUBLE:
            case RETURN_OBJECT:
                return;
            default:
                c.append("    javan_gc_safe_point();").append(System.lineSeparator());
        }
    }

    private static void emitReturnValue(
        final javan.ir.IrExpression expression,
        final String rootFrameSymbol,
        final boolean hasRootFrame,
        final StringBuilder c
    ) {
        if (expression.type() == javan.ir.IrType.OBJECT) {
            emitObjectReturnValue(expression, rootFrameSymbol, hasRootFrame, c);
            return;
        }
        final ExpressionPlan plan = new ExpressionPlan();
        final String value = plan.expression(expression);
        if (!hasRootFrame && plan.isEmpty()) {
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
        final StringBuilder c
    ) {
        final ExpressionPlan plan = new ExpressionPlan();
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
        c.append(indent).append(returnRootSymbol()).append(" = javan_return_value;").append(System.lineSeparator());
        c.append(indent).append("javan_gc_safe_point();").append(System.lineSeparator());
        if (plan.hasRootFrame()) {
            c.append(indent).append("javan_root_frame_pop(javan_expr_roots);").append(System.lineSeparator());
        }
        emitRootFramePop(rootFrameSymbol, hasRootFrame, c, indent);
        c.append(indent).append(returnRootSymbol()).append(" = 0;").append(System.lineSeparator());
        c.append(indent).append("return javan_return_value;").append(System.lineSeparator());
        c.append("    }").append(System.lineSeparator());
    }

    private static void emitSignature(final IrFunction function, final StringBuilder c, final boolean isStatic) {
        if (isStatic) {
            c.append("static ");
        }
        c.append(function.returnType().cName()).append(' ').append(function.symbol()).append('(');
        if (function.parameters().isEmpty()) {
            c.append("void");
        } else {
            for (int index = 0; index < function.parameters().size(); index++) {
                if (index > 0) {
                    c.append(", ");
                }
                final javan.ir.IrParameter parameter = function.parameters().get(index);
                c.append(parameter.type().cName()).append(' ').append(parameter.name());
            }
        }
        c.append(')');
    }

    private static void emitDispatchSignature(final IrDispatch dispatch, final StringBuilder c) {
        c.append("static ").append(dispatch.returnType().cName()).append(' ').append(dispatch.symbol()).append('(');
        for (int index = 0; index < dispatch.parameters().size(); index++) {
            if (index > 0) {
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
                c.append("    return (void*) 0;").append(System.lineSeparator());
                break;
        }
    }

    private static void emitClassInitializers(final IrProgram program, final StringBuilder c) {
        final List<IrFunction> initializers = new java.util.ArrayList<>();
        for (final IrFunction function : program.functions()) {
            if ("<clinit>".equals(function.name())) {
                insertInitializer(initializers, function);
            }
        }
        for (final IrFunction function : initializers) {
            c.append("    ")
                .append(function.symbol())
                .append("();")
                .append(System.lineSeparator());
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

    private static String rootFrameSymbol(final IrFunction function) {
        return "javan_roots_" + sanitize(function.symbol());
    }

    private static String returnRootSymbol() {
        return "javan_generated_return_root";
    }

    private static String enumOrdinalSymbol(final String className) {
        return "javan_enum_ordinal_" + sanitize(className);
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
        for (int index = 0; index < value.length(); index++) {
            result.append(escapeCChar(value.charAt(index)));
        }
        return result.toString();
    }

    private static String emitCStringLiteral(final String value) {
        final int maxChunkLength = 120;
        final StringBuilder result = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final String escaped = escapeCChar(value.charAt(index));
            if (!chunk.isEmpty() && chunk.length() + escaped.length() > maxChunkLength) {
                appendCStringChunk(result, chunk);
                chunk = new StringBuilder();
            }
            chunk.append(escaped);
        }
        appendCStringChunk(result, chunk);
        return result.toString();
    }

    private static void appendCStringChunk(final StringBuilder result, final StringBuilder chunk) {
        if (!result.isEmpty()) {
            result.append(System.lineSeparator()).append("        ");
        }
        result.append('"').append(chunk.toString()).append('"');
    }

    private static String escapeCChar(final char ch) {
        final StringBuilder result = new StringBuilder();
        switch (ch) {
            case '\\':
                return "\\\\";
            case '"':
                return "\\\"";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            default:
                if (ch < 32 || ch > 126) {
                    result.append('\\');
                    appendOctal(result, ch);
                    return result.toString();
                }
                result.append(ch);
                return result.toString();
        }
    }

    private static void appendOctal(final StringBuilder result, final int value) {
        result.append((char) ('0' + ((value >> 6) & 7)));
        result.append((char) ('0' + ((value >> 3) & 7)));
        result.append((char) ('0' + (value & 7)));
    }
}
