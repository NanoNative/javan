package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.build.NativeInteropConfig;
import javan.build.NativeLinkInputs;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.ir.IrExpression;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrMaterializedLambdaTarget;
import javan.ir.IrProgram;
import javan.ir.IrType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class NativeImportCodegenTest {
    @TempDir
    private Path tempDir;

    @Test
    void appGenerationPreservesPrimitiveArgumentsAndIntReturn() throws Exception {
        final NativeInteropConfig config = config(binding("primitiveInt", "(IJFD)I", "native_primitive_int"));

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).contains(
            "int native_primitive_int(int arg0, long long arg1, float arg2, double arg3);",
            "static int " + wrapper(0)
                + "(int arg0, long long arg1, float arg2, double arg3)",
            "return native_primitive_int(arg0, arg1, arg2, arg3);"
        );
    }

    @Test
    void appGenerationPreservesLongReturn() throws Exception {
        final NativeInteropConfig config = config(binding("primitiveLong", "()J", "native_primitive_long"));

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).contains(
            "long long native_primitive_long(void);",
            "static long long " + wrapper(0) + "(void)",
            "return native_primitive_long();"
        );
    }

    @Test
    void appGenerationPreservesFloatReturn() throws Exception {
        final NativeInteropConfig config = config(binding("primitiveFloat", "()F", "native_primitive_float"));

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).contains(
            "float native_primitive_float(void);",
            "static float " + wrapper(0) + "(void)",
            "return native_primitive_float();"
        );
    }

    @Test
    void appGenerationPreservesDoubleReturn() throws Exception {
        final NativeInteropConfig config = config(binding("primitiveDouble", "()D", "native_primitive_double"));

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).contains(
            "double native_primitive_double(void);",
            "static double " + wrapper(0) + "(void)",
            "return native_primitive_double();"
        );
    }

    @Test
    void appGenerationConvertsBorrowedByteArrayArguments() throws Exception {
        final NativeInteropConfig config = config(binding("consume", "([B)I", "native_consume"));

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).contains(
            "int native_consume(JavanNativeImportedByteArray arg0);",
            "static int " + wrapper(0) + "(void* arg0)",
            "JavanNativeImportedByteArray arg0_native = javan_native_import_byte_array(arg0);",
            "return native_consume(arg0_native);"
        );
    }

    @Test
    void appGenerationRootsInlineByteArrayUntilNativeCallReturns() throws Exception {
        final String wrapperSymbol = wrapper(0);
        final NativeInteropConfig config = config(binding("consume", "([B)V", "native_consume"));
        final IrProgram caller = program(
            IrInstruction.callStaticVoid(
                canonical("consume", "([B)V"),
                List.of(IrExpression.byteArrayAllocation(IrExpression.intLiteral(1)))
            ),
            IrInstruction.returnVoid()
        );

        final String generated = Files.readString(new CCodegen().generate(caller, tempDir, config));
        final String line = System.lineSeparator();

        assertThat(generated).contains(
            "    {" + line
                + "        void* javan_expr_tmp_0 = 0;" + line
                + "        void** javan_expr_roots[] = {" + line
                + "            (void**) &javan_expr_tmp_0" + line
                + "        };" + line
                + "        javan_root_frame_push(javan_expr_roots, 1);" + line
                + "        javan_runtime_lock_enter();" + line
                + "        javan_expr_tmp_0 = javan_byte_array_new(1);" + line
                + "        javan_runtime_lock_leave();" + line
                + "        " + wrapperSymbol + "(javan_expr_tmp_0);" + line
                + "        javan_root_frame_pop(javan_expr_roots);" + line
                + "    }"
        );
    }

    @Test
    void appGenerationRootsEveryInlineByteArrayUntilNativeCallReturns() throws Exception {
        final String wrapperSymbol = wrapper(0);
        final NativeInteropConfig config = config(binding("combine", "([B[B)V", "native_combine"));
        final IrProgram caller = program(
            IrInstruction.callStaticVoid(
                canonical("combine", "([B[B)V"),
                List.of(
                    IrExpression.byteArrayAllocation(IrExpression.intLiteral(1)),
                    IrExpression.byteArrayAllocation(IrExpression.intLiteral(2))
                )
            ),
            IrInstruction.returnVoid()
        );

        final String generated = Files.readString(new CCodegen().generate(caller, tempDir, config));
        final String line = System.lineSeparator();

        assertThat(generated).contains(
            "        void** javan_expr_roots[] = {" + line
                + "            (void**) &javan_expr_tmp_0," + line
                + "            (void**) &javan_expr_tmp_1" + line
                + "        };" + line
                + "        javan_root_frame_push(javan_expr_roots, 2);" + line
                + "        javan_runtime_lock_enter();" + line
                + "        javan_expr_tmp_0 = javan_byte_array_new(1);" + line
                + "        javan_runtime_lock_leave();" + line
                + "        javan_runtime_lock_enter();" + line
                + "        javan_expr_tmp_1 = javan_byte_array_new(2);" + line
                + "        javan_runtime_lock_leave();" + line
                + "        " + wrapperSymbol + "(javan_expr_tmp_0, javan_expr_tmp_1);" + line
                + "        javan_root_frame_pop(javan_expr_roots);"
        );
    }

    @Test
    void appGenerationPreservesVoidNativeReturn() throws Exception {
        final NativeInteropConfig config = config(binding("notify", "(I)V", "native_notify"));

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).contains(
            "void native_notify(int arg0);",
            "static void " + wrapper(0) + "(int arg0) {\n"
                + "    native_notify(arg0);\n"
                + "    return;\n"
                + "}"
        );
    }

    @Test
    void appGenerationPreservesConfiguredImportOrder() throws Exception {
        final NativeInteropConfig config = config(
            binding("zeta", "()I", "native_zeta"),
            binding("alpha", "()I", "native_alpha")
        );

        final String generated = Files.readString(new CCodegen().generate(program(), tempDir, config));

        assertThat(generated).containsSubsequence(
            "int native_zeta(void);",
            "static int " + wrapper(0) + "(void);",
            "int native_alpha(void);",
            "static int " + wrapper(1) + "(void);",
            "static int " + wrapper(0) + "(void) {",
            "static int " + wrapper(1) + "(void) {"
        );
    }

    @Test
    void appGenerationRejectsPrivateNativeWrapperCollisionWithFunctionSymbol() {
        final NativeInteropConfig config = config(binding("call", "()V", "native_call"));
        final IrProgram conflicting = new IrProgram(
            List.of(new IrFunction(
                "com/acme/Generated",
                "generated",
                "()V",
                wrapper(0),
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.returnVoid())
            )),
            wrapper(0)
        );

        assertThatThrownBy(() -> new CCodegen().generate(conflicting, tempDir, config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Native import private wrapper symbol collision: javan_native_import_wrapper_0_fn for "
                    + "com/acme/Native.call()V and com/acme/Generated.generated()V"
            );
    }

    @Test
    void appGenerationRejectsPrivateNativeWrapperCollisionWithDispatchSymbol() {
        final NativeInteropConfig config = config(binding("call", "()V", "native_call"));
        final EntryPoint main = entry("main", "()V");
        final IrProgram conflicting = new IrProgram(
            List.of(new javan.ir.IrClass("com/acme/Target", "javan_class_com_acme_Target", List.of())),
            List.of(new IrFunction(
                main.className(),
                main.methodName(),
                main.descriptor(),
                BytecodeToIR.symbol(main),
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.returnVoid())
            )),
            List.of(new IrDispatch(
                wrapper(0),
                IrType.VOID,
                List.of(new javan.ir.IrParameter(IrType.OBJECT, "self")),
                List.of(new IrDispatchTarget("com/acme/Target", "target"))
            )),
            BytecodeToIR.symbol(main)
        );

        assertThatThrownBy(() -> new CCodegen().generate(conflicting, tempDir, config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Native import private wrapper symbol collision: javan_native_import_wrapper_0_fn for "
                    + "com/acme/Native.call()V and generated dispatch javan_native_import_wrapper_0_fn"
            );
    }

    @Test
    void appGenerationRejectsUnsupportedDirectDescriptor() {
        final NativeInteropConfig config = config(binding("flag", "(Z)I", "native_flag"));

        assertThatThrownBy(() -> new CCodegen().generate(program(), tempDir, config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported native import descriptor: com/acme/Native.flag(Z)I");
    }

    @Test
    void appGenerationRejectsBorrowedByteArrayReturn() {
        final NativeInteropConfig config = config(binding("bytes", "()[B", "native_bytes"));

        assertThatThrownBy(() -> new CCodegen().generate(program(), tempDir, config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported native import descriptor: com/acme/Native.bytes()[B");
    }

    @Test
    void libraryGenerationEmitsNativeExternalWrapper() throws Exception {
        final NativeInteropConfig config = config(binding("measure", "(F)J", "native_measure"));

        final String generated = Files.readString(
            new CCodegen().generateLibrary(program(), tempDir, List.of(), config)
        );

        assertThat(generated).contains(
            "long long native_measure(float arg0);",
            "static long long " + wrapper(0) + "(float arg0) {",
            "return native_measure(arg0);"
        );
    }

    @Test
    void appGenerationRoutesDirectVoidNativeCallsThroughPrivateWrapper() throws Exception {
        final NativeInteropConfig config = config(binding("notify", "()V", "native_notify"));
        final IrProgram caller = program(
            IrInstruction.callStaticVoid(canonical("notify", "()V")),
            IrInstruction.returnVoid()
        );

        final String generated = Files.readString(new CCodegen().generate(caller, tempDir, config));

        assertThat(generated).contains("    " + wrapper(0) + "();");
    }

    @Test
    void appGenerationRejectsStaticNativeImportDispatchTarget() {
        final NativeInteropConfig config = config(binding("notify", "()V", "native_notify"));
        final EntryPoint main = entry("main", "()V");
        final IrProgram caller = new IrProgram(
            List.of(new javan.ir.IrClass("com/acme/Target", "javan_class_com_acme_Target", List.of())),
            List.of(new IrFunction(
                main.className(),
                main.methodName(),
                main.descriptor(),
                BytecodeToIR.symbol(main),
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.returnVoid())
            )),
            List.of(new IrDispatch(
                "javan_dispatch_notify",
                IrType.VOID,
                List.of(new javan.ir.IrParameter(IrType.OBJECT, "self")),
                List.of(new IrDispatchTarget("com/acme/Target", canonical("notify", "()V")))
            )),
            BytecodeToIR.symbol(main)
        );

        assertThatThrownBy(() -> new CCodegen().generate(caller, tempDir, config))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Static native import cannot be a dispatch target: com/acme/Native.notify()V "
                    + "via generated dispatch javan_dispatch_notify"
            );
    }

    @Test
    void appGenerationRoutesMaterializedNativeLambdaTargetsThroughPrivateWrapper() throws Exception {
        final NativeInteropConfig config = config(binding("consume", "([B)V", "native_consume"));
        final EntryPoint main = entry("main", "()V");
        final IrProgram caller = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                main.className(),
                main.methodName(),
                main.descriptor(),
                BytecodeToIR.symbol(main),
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.returnVoid())
            )),
            List.of(),
            BytecodeToIR.symbol(main),
            List.of(new IrMaterializedLambdaTarget(
                7,
                "java/util/function/Consumer",
                "accept",
                "(Ljava/lang/Object;)V",
                canonical("consume", "([B)V"),
                0,
                false,
                true
            )),
            Map.of()
        );

        final String generated = Files.readString(new CCodegen().generate(caller, tempDir, config));

        assertThat(generated).contains("case 7: " + wrapper(0) + "(arg); return;");
    }

    @Test
    void cCodegenLegacyAppOverloadEqualsEmptyNativeConfiguration() throws Exception {
        final CCodegen codegen = new CCodegen();
        final IrProgram program = program();
        final String legacyApp = Files.readString(codegen.generate(program, tempDir.resolve("legacy-app")));
        final String configuredApp = Files.readString(
            codegen.generate(program, tempDir.resolve("configured-app"), NativeInteropConfig.empty())
        );

        assertThat(legacyApp).isEqualTo(configuredApp);
    }

    @Test
    void cCodegenLegacyLibraryOverloadEqualsEmptyNativeConfiguration() throws Exception {
        final CCodegen codegen = new CCodegen();
        final IrProgram program = program();
        final String legacyLibrary = Files.readString(
            codegen.generateLibrary(program, tempDir.resolve("legacy-library"), List.of())
        );
        final String configuredLibrary = Files.readString(
            codegen.generateLibrary(
                program,
                tempDir.resolve("configured-library"),
                List.of(),
                NativeInteropConfig.empty()
            )
        );

        assertThat(legacyLibrary).isEqualTo(configuredLibrary);
    }

    @Test
    void bytecodeToIrLegacyBasicOverloadEqualsEmptyNativeConfiguration() {
        final EntryPoint mainEntry = entry("main", "()V");
        final Map<String, ClassFile> classes = classes(method(0x0008, "main", "()V"));
        final CallGraph callGraph = new CallGraph(mainEntry, List.of(mainEntry), List.of());
        final BytecodeToIR lowerer = new BytecodeToIR();
        final IrProgram configured = lowerer.lower(
            classes,
            callGraph,
            SourceLineIndex.empty(),
            NativeInteropConfig.empty()
        );

        assertThat(lowerer.lower(classes, callGraph)).isEqualTo(configured);
    }

    @Test
    void bytecodeToIrLegacySourceLineOverloadEqualsEmptyNativeConfiguration() {
        final EntryPoint mainEntry = entry("main", "()V");
        final Map<String, ClassFile> classes = classes(method(0x0008, "main", "()V"));
        final CallGraph callGraph = new CallGraph(mainEntry, List.of(mainEntry), List.of());
        final BytecodeToIR lowerer = new BytecodeToIR();
        final IrProgram configured = lowerer.lower(
            classes,
            callGraph,
            SourceLineIndex.empty(),
            NativeInteropConfig.empty()
        );

        assertThat(lowerer.lower(classes, callGraph, SourceLineIndex.empty())).isEqualTo(configured);
    }

    @Test
    void bytecodeToIrOmitsExactlyConfiguredReachableNativeFunction() {
        final EntryPoint mainEntry = entry("main", "()V");
        final EntryPoint nativeEntry = entry("probe", "()I");
        final Map<String, ClassFile> classes = classes(
            method(0x0008, "main", "()V"),
            new MethodInfo(0x0108, "probe", "()I", Optional.empty())
        );
        final CallGraph callGraph = new CallGraph(
            mainEntry,
            List.of(mainEntry, nativeEntry),
            List.of()
        );
        final NativeInteropConfig config = config(
            new NativeInteropConfig.ImportBinding(nativeEntry, "native_probe")
        );

        final IrProgram program = new BytecodeToIR().lower(
            classes,
            callGraph,
            SourceLineIndex.empty(),
            config
        );

        assertThat(program.functions()).extracting(IrFunction::symbol).containsExactly(
            BytecodeToIR.symbol(mainEntry)
        );
    }

    @Test
    void bytecodeToIrStillLowersUnconfiguredRegularMethod() {
        final EntryPoint mainEntry = entry("main", "()V");
        final EntryPoint nativeEntry = entry("probe", "()I");
        final EntryPoint regularEntry = entry("regular", "()V");
        final Map<String, ClassFile> classes = classes(
            method(0x0008, "main", "()V"),
            new MethodInfo(0x0108, "probe", "()I", Optional.empty()),
            method(0x0008, "regular", "()V")
        );
        final CallGraph callGraph = new CallGraph(
            mainEntry,
            List.of(mainEntry, nativeEntry, regularEntry),
            List.of()
        );
        final NativeInteropConfig config = config(
            new NativeInteropConfig.ImportBinding(nativeEntry, "native_probe")
        );

        final IrProgram program = new BytecodeToIR().lower(
            classes,
            callGraph,
            SourceLineIndex.empty(),
            config
        );

        assertThat(program.functions()).extracting(IrFunction::symbol).contains(
            BytecodeToIR.symbol(regularEntry)
        );
    }

    private static IrProgram program(final IrInstruction... instructions) {
        final EntryPoint main = entry("main", "()V");
        return new IrProgram(
            List.of(new IrFunction(
                main.className(),
                main.methodName(),
                main.descriptor(),
                BytecodeToIR.symbol(main),
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(instructions)
            )),
            BytecodeToIR.symbol(main)
        );
    }

    private static NativeInteropConfig config(final NativeInteropConfig.ImportBinding... bindings) {
        return new NativeInteropConfig(List.of(bindings), NativeLinkInputs.empty());
    }

    private static NativeInteropConfig.ImportBinding binding(
        final String methodName,
        final String descriptor,
        final String externalSymbol
    ) {
        return new NativeInteropConfig.ImportBinding(entry(methodName, descriptor), externalSymbol);
    }

    private static EntryPoint entry(final String methodName, final String descriptor) {
        return new EntryPoint("com/acme/Native", methodName, descriptor);
    }

    private static String canonical(final String methodName, final String descriptor) {
        return BytecodeToIR.symbol(entry(methodName, descriptor));
    }

    private static String wrapper(final int ordinal) {
        return "javan_native_import_wrapper_" + ordinal + "_fn";
    }

    private static Map<String, ClassFile> classes(final MethodInfo... methods) {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        classes.put(
            "com/acme/Native",
            new ClassFile(
                69,
                "com/acme/Native",
                "java/lang/Object",
                0x0010,
                List.of(),
                List.of(),
                List.of(methods),
                Path.of("com/acme/Native.class"),
                true
            )
        );
        return classes;
    }

    private static MethodInfo method(final int accessFlags, final String name, final String descriptor) {
        return new MethodInfo(
            accessFlags,
            name,
            descriptor,
            Optional.of(new CodeAttribute(0, 0, new byte[0], 0, List.of(returnInstruction())))
        );
    }

    private static Instruction returnInstruction() {
        return new Instruction(
            0,
            177,
            "return",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
