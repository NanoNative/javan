package javan;

import javan.analysis.EntryPoint;
import javan.build.BindingLanguage;
import javan.build.BuildKind;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.cli.Command;
import javan.cli.Options;
import javan.codegen.CCodegen;
import javan.codegen.BytecodeToIR;
import javan.codegen.MethodDescriptor;
import javan.codegen.NativeLinker;
import javan.compat.BytecodeSupport;
import javan.ir.IrClass;
import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrExpression;
import javan.ir.IrField;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrProgram;
import javan.ir.IrType;
import javan.profile.Profile;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.verify.Diagnostic;
import javan.verify.ForbiddenApiRules;
import javan.classfile.MethodRef;
import javan.verify.StaticVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class CoreBehaviorTest {
    @TempDir
    private Path tempDir;

    @Test
    void optionsParseAllEscapeHatches() {
        final Options options = Options.parse(new String[]{
            "run",
            "app",
            "--main", "com.acme.Main",
            "--classes", "classes",
            "--classpath", "a.jar" + java.io.File.pathSeparator + "b.jar",
            "-o", "native-app",
            "--kind", "sharedlib",
            "--profile", "strict",
            "--export", "com.acme.Math.add",
            "--bindings", "c,rust",
            "--release",
            "--target", "linux-aarch64",
            "--no-build",
            "--",
            "one",
            "two"
        });

        assertThat(options.command()).isEqualTo(Command.RUN);
        assertThat(options.target()).contains(Path.of("app"));
        assertThat(options.mainClass()).contains("com.acme.Main");
        assertThat(options.classFolders()).containsExactly(Path.of("classes"));
        assertThat(options.classpathEntries()).containsExactly(Path.of("a.jar"), Path.of("b.jar"));
        assertThat(options.outputName()).contains("native-app");
        assertThat(options.buildKind()).isEqualTo(BuildKind.SHAREDLIB);
        assertThat(options.profile()).isEqualTo(Profile.STRICT);
        assertThat(options.exports()).containsExactly("com.acme.Math.add");
        assertThat(options.bindings()).containsExactly(BindingLanguage.C, BindingLanguage.RUST);
        assertThat(options.release()).isTrue();
        assertThat(options.targetTriple()).contains("linux-aarch64");
        assertThat(options.noBuild()).isTrue();
        assertThat(options.passthroughArgs()).containsExactly("one", "two");
    }

    @Test
    void optionsParseJarBuildKind() {
        final Options options = Options.parse(new String[]{"build", ".", "--kind", "jar"});

        assertThat(options.buildKind()).isEqualTo(BuildKind.JAR);
    }

    @Test
    void commandRejectsUnknownValue() {
        assertThat(Command.parse("missing")).isEmpty();
    }

    @Test
    void profileParsesCliValues() {
        assertThat(Profile.parse("core")).contains(Profile.CORE);
        assertThat(Profile.parse("service")).contains(Profile.SERVICE);
        assertThat(Profile.parse("library")).contains(Profile.LIBRARY);
        assertThat(Profile.parse("strict")).contains(Profile.STRICT);
        assertThat(Profile.parse("missing")).isEmpty();
    }

    @Test
    void nativeLibraryLinkerReportsSharedAndStaticFailures() throws Exception {
        final Path badC = Files.writeString(tempDir.resolve("bad.c"), "int main(void) { return ;\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void runtime(void) {}\n");
        final NativeLinker linker = new NativeLinker();

        assertThatThrownBy(() -> linker.linkSharedLibrary(tempDir, badC, runtimeC, tempDir.resolve("libbad.dylib")))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Native shared library link failed");
        assertThatThrownBy(() -> linker.linkStaticLibrary(tempDir, badC, runtimeC, tempDir.resolve("libbad.a")))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Native compile failed");
    }

    @Test
    void bytecodeSupportClassifiesNativeRejectedAndUnknownOpcodes() {
        assertThat(BytecodeSupport.classify(190)).isEqualTo(BytecodeSupport.Status.NATIVE_SUPPORTED);
        assertThat(BytecodeSupport.classify(188)).isEqualTo(BytecodeSupport.Status.NATIVE_SUPPORTED);
        assertThat(BytecodeSupport.classify(197)).isEqualTo(BytecodeSupport.Status.RECOGNIZED_REJECTED);
        assertThat(BytecodeSupport.classify(255)).isEqualTo(BytecodeSupport.Status.UNKNOWN_FATAL);
        assertThat(BytecodeSupport.mnemonic(190)).isEqualTo("arraylength");
        assertThat(BytecodeSupport.mnemonic(255)).isEqualTo("opcode_255");
        assertThat(BytecodeSupport.knownOpcodes()).contains(0, 201).doesNotContain(255);
        assertThat(BytecodeSupport.nativeSupportedOpcodes()).contains(47, 48, 49, 80, 81, 82, 186, 188, 190).doesNotContain(197);
    }

    @Test
    void forbiddenRulesRejectDynamicApis() {
        final ForbiddenApiRules rules = new ForbiddenApiRules();

        assertThat(rules.forbiddenReason(new MethodRef("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/ClassLoader$NativeLibrary", "load", "()V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/reflect/Proxy", "newProxyInstance", "()Ljava/lang/Object;"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/reflect/Method", "invoke", "()V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/invoke/MethodHandle", "invokeExact", "()V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/invoke/MethodHandles", "lookup", "()V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/instrument/Instrumentation", "addTransformer", "()V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/io/ObjectInputStream", "readObject", "()Ljava/lang/Object;"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/io/ObjectOutputStream", "writeObject", "(Ljava/lang/Object;)V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/System", "load", "(Ljava/lang/String;)V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/System", "loadLibrary", "(Ljava/lang/String;)V"))).isPresent();
        assertThat(rules.forbiddenReason(new MethodRef("java/lang/System", "currentTimeMillis", "()J"))).isEmpty();
    }

    @Test
    void diagnosticFormatsEmptyFields() {
        final Diagnostic diagnostic = Diagnostic.warning("JAVAN999", "message", "", "", "", "", "");

        assertThat(diagnostic.format()).contains("warning[JAVAN999]", "  -");
    }

    @Test
    void staticVerifierAcceptsPrimitiveNewArrayTypes() {
        for (final int atype : List.of(4, 5, 6, 7, 8, 9, 10, 11)) {
            final List<Diagnostic> diagnostics = verifyNewArray(atype, true);

            assertThat(diagnostics).isEmpty();
        }
    }

    @Test
    void staticVerifierRejectsReachableUnknownNewArrayTypes() {
        final Map<Integer, String> unsupported = Map.of(99, "atype-99");

        for (final Map.Entry<Integer, String> entry : unsupported.entrySet()) {
            final List<Diagnostic> diagnostics = verifyNewArray(entry.getKey(), true);

            assertThat(diagnostics).hasSize(1);
            assertThat(diagnostics.getFirst().error()).isTrue();
            assertThat(diagnostics.getFirst().subject()).isEqualTo("newarray " + entry.getValue());
        }
    }

    @Test
    void staticVerifierWarnsForUnreachableUnknownNewArrayTypes() {
        final List<Diagnostic> diagnostics = verifyNewArray(99, false);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().error()).isFalse();
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN130");
        assertThat(diagnostics.getFirst().subject()).isEqualTo("newarray atype-99");
    }

    @Test
    void staticVerifierRejectsMalformedNewArrayInstruction() {
        final List<Diagnostic> diagnostics = verifyNewArrayOperands(new byte[0], true);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().error()).isTrue();
        assertThat(diagnostics.getFirst().subject()).isEqualTo("newarray atype--1");
    }

    @Test
    void staticVerifierAcceptsOnlyDeterministicStringConcatInvokedynamic() {
        assertThat(verifyInvokedynamic(new DynamicRef(
            "makeConcatWithConstants",
            "(ICFD[Ljava/lang/String;)Ljava/lang/String;",
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "()V",
            List.of("value \u0001 \u0001 \u0001 \u0001 \u0001")
        ), true)).isEmpty();
        assertThat(verifyInvokedynamic(new DynamicRef(
            "makeConcat",
            "(JZ)Ljava/lang/String;",
            "java/lang/invoke/StringConcatFactory",
            "makeConcat",
            "()V",
            List.of()
        ), true)).isEmpty();

        final List<DynamicRef> rejected = List.of(
            new DynamicRef("dyn", "(I)Ljava/lang/String;", "other/Factory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "(ILjava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "(I)I", "java/lang/invoke/StringConcatFactory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "(Ljava/lang/String)Ljava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "([)Ljava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "([Q)Ljava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "([Ljava/lang/String)Ljava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcat", "()V", List.of()),
            new DynamicRef("dyn", "([Ljava/lang/String;)Ljava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "()V", List.of()),
            new DynamicRef("dyn", "(I)Ljava/lang/String;", "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "()V", List.of("\u0002"))
        );

        for (final DynamicRef dynamicRef : rejected) {
            final List<Diagnostic> diagnostics = verifyInvokedynamic(dynamicRef, true);
            assertThat(diagnostics).hasSize(1);
            assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN030");
            assertThat(diagnostics.getFirst().subject()).isEqualTo("invokedynamic");
        }

        final List<Diagnostic> warnings = verifyInvokedynamic(rejected.getFirst(), false);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().code()).isEqualTo("JAVAN130");
    }

    @Test
    void staticVerifierRejectsUnsupportedExceptionHandlerShapes() {
        assertThat(verifyExceptionTable(List.of(
            new Instruction(0, 187, "new", new byte[0], Optional.empty(), Optional.empty(), Optional.of("java/lang/IllegalStateException"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            new Instruction(1, 183, "invokespecial", new byte[0], Optional.of(new MethodRef("java/lang/IllegalStateException", "<init>", "()V")), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            new Instruction(2, 191, "athrow", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        ), new CodeException(0, 3, 3, Optional.of("java/lang/Throwable")))).isEmpty();
        assertThat(verifyExceptionTable(List.of(
            new Instruction(0, 187, "new", new byte[0], Optional.empty(), Optional.empty(), Optional.of("java/lang/AssertionError"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            new Instruction(1, 183, "invokespecial", new byte[0], Optional.of(new MethodRef("java/lang/AssertionError", "<init>", "()V")), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            new Instruction(2, 191, "athrow", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        ), new CodeException(0, 3, 3, Optional.of("java/lang/AssertionError")))).isEmpty();

        final List<Instruction> explicitThrow = List.of(
            new Instruction(0, 187, "new", new byte[0], Optional.empty(), Optional.empty(), Optional.of("java/lang/IllegalStateException"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            new Instruction(1, 183, "invokespecial", new byte[0], Optional.of(new MethodRef("java/lang/IllegalStateException", "<init>", "()V")), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            new Instruction(2, 191, "athrow", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );
        final List<List<Diagnostic>> rejected = List.of(
            verifyExceptionTable(explicitThrow, new CodeException(0, 3, 3, Optional.empty())),
            verifyExceptionTable(explicitThrow, new CodeException(0, 3, 3, Optional.of("java/lang/String"))),
            verifyExceptionTable(List.of(
                new Instruction(0, 183, "invokespecial", new byte[0], Optional.of(new MethodRef("java/lang/Object", "<init>", "()V")), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                new Instruction(1, 191, "athrow", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
            ), new CodeException(0, 2, 2, Optional.of("java/lang/IllegalStateException"))),
            verifyExceptionTable(List.of(
                new Instruction(0, 182, "invokevirtual", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                new Instruction(1, 191, "athrow", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
            ), new CodeException(0, 2, 2, Optional.of("java/lang/IllegalStateException")))
        );

        for (final List<Diagnostic> diagnostics : rejected) {
            assertThat(diagnostics).hasSize(1);
            assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
        }
    }

    @Test
    void filesHelpersHandleMissingAndIgnoredPaths() throws Exception {
        final Path ignored = tempDir.resolve("target/generated.java");
        Files.createDirectories(ignored.getParent());
        Files.writeString(ignored, "class Generated {}\n");

        assertThat(Files2.readStringIfExists(tempDir.resolve("missing.txt"))).isEmpty();
        assertThat(Files2.findJavaSources(tempDir)).isEmpty();
        assertThat(Files2.containsClassFile(tempDir)).isFalse();
        assertThat(Files2.newestModified(List.of(tempDir.resolve("missing")), ".class")).isZero();
        assertThat(Files2.deleteRecursive(tempDir.resolve("missing"))).isEqualTo(tempDir.resolve("missing"));
    }

    private static List<Diagnostic> verifyNewArray(final int atype, final boolean reachable) {
        return verifyNewArrayOperands(new byte[]{(byte) atype}, reachable);
    }

    private static List<Diagnostic> verifyNewArrayOperands(final byte[] operands, final boolean reachable) {
        return verifyInstruction(new Instruction(
            0,
            188,
            "newarray",
            operands,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        ), reachable);
    }

    private static List<Diagnostic> verifyInvokedynamic(final DynamicRef dynamicRef, final boolean reachable) {
        return verifyInstruction(new Instruction(
            0,
            186,
            "invokedynamic",
            new byte[]{0, 1, 0, 0},
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(dynamicRef)
        ), reachable);
    }

    private static List<Diagnostic> verifyExceptionTable(final List<Instruction> instructions, final CodeException exception) {
        final MethodInfo method = new MethodInfo(
            0,
            "main",
            "([Ljava/lang/String;)V",
            Optional.of(new CodeAttribute(
                2,
                1,
                new byte[]{(byte) 191},
                1,
                List.of(exception),
                instructions
            ))
        );
        final ClassFile classFile = new ClassFile(
            69,
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(method),
            Path.of("Main.class"),
            true
        );
        return new StaticVerifier().verify(
            Map.of(classFile.name(), classFile),
            Set.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );
    }

    private static List<Diagnostic> verifyInstruction(final Instruction instruction, final boolean reachable) {
        final MethodInfo method = new MethodInfo(
            0,
            "main",
            "([Ljava/lang/String;)V",
            Optional.of(new CodeAttribute(
                1,
                1,
                new byte[]{(byte) 188},
                0,
                List.of(instruction)
            ))
        );
        final ClassFile classFile = new ClassFile(
            69,
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(method),
            Path.of("Main.class"),
            true
        );
        final Set<EntryPoint> reachableMethods = reachable
            ? Set.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
            : Set.of();
        return new StaticVerifier().verify(Map.of(classFile.name(), classFile), reachableMethods);
    }

    @Test
    void processRunnerReportsTimeoutAndMissingCommand() throws Exception {
        final ProcessRunner runner = new ProcessRunner(Duration.ofMillis(50));

        assertThat(runner.commandExists("definitely-not-a-javan-command")).isFalse();
        assertThat(runner.firstAvailable(List.of("definitely-not-a-javan-command", "java"))).contains("java");
        assertThat(runner.run(tempDir, List.of("sh", "-c", "sleep 1")).exitCode()).isEqualTo(124);
    }

    @Test
    void processRunnerCapturesFailingProcess() throws Exception {
        final ProcessRunner.Result result = new ProcessRunner().run(tempDir, List.of("sh", "-c", "echo out; echo err >&2; exit 7"));

        assertThat(result.exitCode()).isEqualTo(7);
        assertThat(result.stdout()).isEqualTo("out\n");
        assertThat(result.stderr()).isEqualTo("err\n");
    }

    @Test
    void cCodegenEmitsHelperAndEscapesControlCharacters() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(
                new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.callStaticVoid("helper_symbol"),
                    IrInstruction.printlnLiteral("line\nquote\"slash\\tab\tcarriage\rcontrol\u0001"),
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/Helper", "print", "()V", "helper_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.printlnLiteral("helper"),
                    IrInstruction.returnVoid()
                ))
            ),
            "main_symbol"
        );

        final Path generated = new CCodegen().generate(program, tempDir);

        assertThat(Files.readString(generated)).contains(
            "static void helper_symbol(void);",
            "helper_symbol();",
            "line\\nquote\\\"slash\\\\tab\\tcarriage\\rcontrol\\001"
        );
    }

    @Test
    void cCodegenEmitsLibraryExportsInitializersAndDispatches() throws Exception {
        final EntryPoint exportEntry = new EntryPoint("com/acme/Api", "touch", "()V");
        final IrProgram program = new IrProgram(
            List.of(new IrClass(
                "com/acme/Api",
                "javan_class_com_acme_Api",
                List.of(),
                List.of(new IrField(IrType.INT, "count", "field_count"))
            )),
            List.of(
                new IrFunction("com/acme/Api", "<clinit>", "()V", "javan_com_acme_Api__clinit___V", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.assignStaticFieldInt("com/acme/Api", "count", IrExpression.intLiteral(1)),
                    IrInstruction.returnVoid()
                )),
                new IrFunction(exportEntry.className(), exportEntry.methodName(), exportEntry.descriptor(), BytecodeToIR.symbol(exportEntry), IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/Api", "answer", "(Lcom/acme/Api;)I", "javan_com_acme_Api_answer__Lcom_acme_Api__I", IrType.INT, List.of(new IrParameter(IrType.OBJECT, "self")), List.of(), List.of(
                    IrInstruction.returnInt(IrExpression.intLiteral(42))
                ))
            ),
            List.of(new IrDispatch(
                "javan_dispatch_com_acme_Api_answer__Lcom_acme_Api__I",
                IrType.INT,
                List.of(new IrParameter(IrType.OBJECT, "self")),
                List.of(new IrDispatchTarget("com/acme/Api", "javan_com_acme_Api_answer__Lcom_acme_Api__I"))
            )),
            BytecodeToIR.symbol(exportEntry)
        );

        final String generated = Files.readString(new CCodegen().generateLibrary(
            program,
            tempDir,
            List.of(new javan.build.ExportedMethod(exportEntry, "javan_export_com_acme_Api_touch_void", List.of(), javan.build.AbiType.VOID))
        ));

        assertThat(generated).contains(
            "static int javan_static_com_acme_Api_field_count = 0;",
            "static int javan_dispatch_com_acme_Api_answer__Lcom_acme_Api__I(void* self);",
            "javan_dispatch_com_acme_Api_answer__Lcom_acme_Api__I(void* self) {",
            "void javan_export_com_acme_Api_touch_void(void) {",
            "javan_library_init();",
            "javan_com_acme_Api__clinit___V();",
            "javan_com_acme_Api_touch___V();"
        );
    }

    @Test
    void cCodegenRejectsMalformedFieldReadMetadata() {
        for (final String value : List.of("missing-separator", "com/acme/Point#")) {
            final IrProgram program = new IrProgram(
                List.of(new IrClass("com/acme/Point", "javan_class_com_acme_Point", List.of())),
                List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.printlnInt(new IrExpression(
                        IrExpression.Kind.FIELD_INT,
                        IrType.INT,
                        value,
                        List.of(IrExpression.objectLocal("value"))
                    ))
                ))),
                "main_symbol"
            );

            assertThatThrownBy(() -> new CCodegen().generate(program, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid owner field value");
        }
    }

    @Test
    void cCodegenRejectsFieldAssignmentAsValueExpression() {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                IrInstruction.printlnInt(IrExpression.intFieldAssignment(
                    IrExpression.objectLocal("value"),
                    IrExpression.intLiteral(1)
                ))
            ))),
            "main_symbol"
        );

        assertThatThrownBy(() -> new CCodegen().generate(program, tempDir))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("assignment is not a value expression");
    }

    @Test
    void cCodegenRejectsArrayAssignmentAsValueExpression() {
        final List<IrExpression> assignments = List.of(
            IrExpression.objectArrayAssignment(IrExpression.objectLocal("array"), IrExpression.intLiteral(0), IrExpression.objectNull()),
            IrExpression.intArrayAssignment(IrExpression.objectLocal("array"), IrExpression.intLiteral(0), IrExpression.intLiteral(1)),
            IrExpression.byteArrayAssignment(IrExpression.objectLocal("array"), IrExpression.intLiteral(0), IrExpression.intLiteral(1)),
            IrExpression.shortArrayAssignment(IrExpression.objectLocal("array"), IrExpression.intLiteral(0), IrExpression.intLiteral(1)),
            IrExpression.charArrayAssignment(IrExpression.objectLocal("array"), IrExpression.intLiteral(0), IrExpression.intLiteral(1))
        );

        for (final IrExpression assignment : assignments) {
            final IrProgram program = new IrProgram(
                List.of(),
                List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.printlnInt(assignment)
                ))),
                "main_symbol"
            );

            assertThatThrownBy(() -> new CCodegen().generate(program, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignment is not a value expression");
        }
    }

    @Test
    void cCodegenRejectsNonStringConcatArguments() {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                IrInstruction.printlnObject(IrExpression.stringConcat("\u0001", List.of(IrExpression.intLiteral(1))))
            ))),
            "main_symbol"
        );

        assertThatThrownBy(() -> new CCodegen().generate(program, tempDir))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("string concat arguments must be preconverted");
    }

    @Test
    void methodDescriptorCreatesDeterministicParameters() {
        final MethodDescriptor descriptor = MethodDescriptor.parse("(IJLjava/lang/String;)V");

        assertThat(descriptor.parameters())
            .extracting(parameter -> parameter.type() + ":" + parameter.name())
            .containsExactly("INT:arg0", "LONG:arg1", "OBJECT:arg2");
    }

    @Test
    void cCodegenEmitsObjectStructFieldsAssignmentsAndReturns() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(
                new IrClass("com/acme/Empty", "javan_class_com_acme_Empty", List.of()),
                new IrClass("com/acme/Message", "javan_class_com_acme_Message", List.of(
                    new IrField(IrType.OBJECT, "text", "field_text")
                ))
            ),
            List.of(
                new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(
                    new IrLocal(IrType.OBJECT, "message")
                ), List.of(
                    IrInstruction.assignObject("message", IrExpression.objectAllocation("com/acme/Message")),
                    IrInstruction.assignFieldObject("com/acme/Message", "text", IrExpression.objectLocal("message"), IrExpression.stringLiteral("hello")),
                    IrInstruction.printlnObject(IrExpression.objectField("com/acme/Message", "text", IrExpression.objectLocal("message"))),
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/Message", "text", "()Ljava/lang/String;", "message_text", IrType.OBJECT, List.of(), List.of(), List.of(
                    IrInstruction.returnObject(IrExpression.stringLiteral("returned"))
                ))
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "struct javan_class_com_acme_Empty",
            "int _javan_type_id;",
            "char _javan_empty;",
            "void* message_text(void)",
            "message = javan_new_com_acme_Message();",
            "((struct javan_class_com_acme_Message*) message)->field_text = (void*) \"hello\";",
            "javan_println((const char*) ((struct javan_class_com_acme_Message*) message)->field_text);",
            "return (void*) \"returned\";"
        );
    }

    @Test
    void cCodegenEmitsClosedWorldDispatchStubs() throws Exception {
        final List<IrParameter> dispatchParameters = List.of(new IrParameter(IrType.OBJECT, "self"));
        final IrProgram program = new IrProgram(
            List.of(
                new IrClass("com/acme/A", "javan_class_com_acme_A", List.of()),
                new IrClass("com/acme/B", "javan_class_com_acme_B", List.of())
            ),
            List.of(
                new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/A", "v", "()V", "a_void", IrType.VOID, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/A", "i", "()I", "a_int", IrType.INT, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnInt(IrExpression.intLiteral(7))
                )),
                new IrFunction("com/acme/A", "l", "()J", "a_long", IrType.LONG, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnLong(IrExpression.longLiteral(9L))
                )),
                new IrFunction("com/acme/A", "f", "()F", "a_float", IrType.FLOAT, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnFloat(IrExpression.floatLiteral(1.25f))
                )),
                new IrFunction("com/acme/A", "d", "()D", "a_double", IrType.DOUBLE, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnDouble(IrExpression.doubleLiteral(2.5))
                )),
                new IrFunction("com/acme/A", "o", "()Ljava/lang/String;", "a_object", IrType.OBJECT, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnObject(IrExpression.stringLiteral("ok"))
                ))
            ),
            List.of(
                new IrDispatch("dispatch_void", IrType.VOID, dispatchParameters, List.of(new IrDispatchTarget("com/acme/A", "a_void"))),
                new IrDispatch("dispatch_int", IrType.INT, dispatchParameters, List.of(new IrDispatchTarget("com/acme/A", "a_int"))),
                new IrDispatch("dispatch_long", IrType.LONG, dispatchParameters, List.of(new IrDispatchTarget("com/acme/A", "a_long"))),
                new IrDispatch("dispatch_float", IrType.FLOAT, dispatchParameters, List.of(new IrDispatchTarget("com/acme/A", "a_float"))),
                new IrDispatch("dispatch_double", IrType.DOUBLE, dispatchParameters, List.of(new IrDispatchTarget("com/acme/A", "a_double"))),
                new IrDispatch("dispatch_object", IrType.OBJECT, dispatchParameters, List.of(new IrDispatchTarget("com/acme/A", "a_object")))
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "struct javan_object_header",
            "object->_javan_type_id = 1;",
            "static void dispatch_void(void* self)",
            "static int dispatch_int(void* self)",
            "static long long dispatch_long(void* self)",
            "static float dispatch_float(void* self)",
            "static double dispatch_double(void* self)",
            "static void* dispatch_object(void* self)",
            "switch (((struct javan_object_header*) self)->_javan_type_id)",
            "a_void(self); return;",
            "return a_int(self);",
            "return a_long(self);",
            "return a_float(self);",
            "return a_double(self);",
            "return a_object(self);",
            "return;",
            "return 0;",
            "return 0LL;",
            "return 0.0f;",
            "return 0.0;",
            "return (void*) 0;"
        );
    }

    @Test
    void nativeLinkerFailsWhenSourceIsInvalid() throws Exception {
        final Path invalid = tempDir.resolve("invalid.c");
        final Path runtime = tempDir.resolve("runtime.c");
        Files.writeString(invalid, "int main( { return 0; }\n");
        Files.writeString(runtime, "");

        assertThatThrownBy(() -> new NativeLinker().link(tempDir, invalid, runtime, tempDir.resolve("out")))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Native link failed");
    }

    @Test
    void methodDescriptorRejectsUnsupportedShapes() {
        assertThatThrownBy(() -> MethodDescriptor.parse("bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid method descriptor");
        assertThatThrownBy(() -> MethodDescriptor.parse("(Lbad)V"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported method parameter descriptor");
        assertThatThrownBy(() -> MethodDescriptor.parse("()Lbad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported method return descriptor");
        assertThatThrownBy(() -> MethodDescriptor.parse("([)V"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported method parameter descriptor");
        assertThatThrownBy(() -> MethodDescriptor.parse("()["))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported method return descriptor");
    }

    @Test
    void methodDescriptorParsesObjectAndArrayParameters() {
        final MethodDescriptor descriptor = MethodDescriptor.parse("(Lcom/acme/Point;[Ljava/lang/String;[I[Z[B[C[SZJFD)D");

        assertThat(descriptor.parameterTypes()).containsExactly(
            IrType.OBJECT,
            IrType.OBJECT,
            IrType.OBJECT,
            IrType.OBJECT,
            IrType.OBJECT,
            IrType.OBJECT,
            IrType.OBJECT,
            IrType.INT,
            IrType.LONG,
            IrType.FLOAT,
            IrType.DOUBLE
        );
        assertThat(descriptor.returnType()).isEqualTo(IrType.DOUBLE);
    }

    @Test
    void cCodegenEmitsPrimitivePrintlnVariants() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                IrInstruction.printlnBoolean(IrExpression.intLiteral(1)),
                IrInstruction.printlnFloat(IrExpression.floatLiteral(1.25f)),
                IrInstruction.printlnDouble(IrExpression.doubleLiteral(2.5))
            ))),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "javan_println_bool(1);",
            "javan_println_float(1.25);",
            "javan_println_double(2.5);"
        );
    }

    @Test
    void cCodegenEmitsPrimitiveArrayVariants() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(
                    new IrLocal(IrType.OBJECT, "bytes"),
                    new IrLocal(IrType.OBJECT, "shorts"),
                    new IrLocal(IrType.OBJECT, "chars"),
                    new IrLocal(IrType.OBJECT, "longs"),
                    new IrLocal(IrType.OBJECT, "floats"),
                    new IrLocal(IrType.OBJECT, "doubles")
                ),
                List.of(
                    IrInstruction.assignObject("bytes", IrExpression.byteArrayAllocation(IrExpression.intLiteral(2))),
                    IrInstruction.assignObject("shorts", IrExpression.shortArrayAllocation(IrExpression.intLiteral(2))),
                    IrInstruction.assignObject("chars", IrExpression.charArrayAllocation(IrExpression.intLiteral(2))),
                    IrInstruction.assignObject("longs", IrExpression.longArrayAllocation(IrExpression.intLiteral(2))),
                    IrInstruction.assignObject("floats", IrExpression.floatArrayAllocation(IrExpression.intLiteral(2))),
                    IrInstruction.assignObject("doubles", IrExpression.doubleArrayAllocation(IrExpression.intLiteral(2))),
                    IrInstruction.assignArrayByte(IrExpression.objectLocal("bytes"), IrExpression.intLiteral(0), IrExpression.intLiteral(-2)),
                    IrInstruction.assignArrayShort(IrExpression.objectLocal("shorts"), IrExpression.intLiteral(0), IrExpression.intLiteral(300)),
                    IrInstruction.assignArrayChar(IrExpression.objectLocal("chars"), IrExpression.intLiteral(0), IrExpression.intLiteral(65)),
                    IrInstruction.assignArrayLong(IrExpression.objectLocal("longs"), IrExpression.intLiteral(0), IrExpression.longLiteral(7L)),
                    IrInstruction.assignArrayFloat(IrExpression.objectLocal("floats"), IrExpression.intLiteral(0), IrExpression.floatLiteral(1.25f)),
                    IrInstruction.assignArrayDouble(IrExpression.objectLocal("doubles"), IrExpression.intLiteral(0), IrExpression.doubleLiteral(2.5)),
                    IrInstruction.printlnInt(IrExpression.byteArrayLoad(IrExpression.objectLocal("bytes"), IrExpression.intLiteral(0))),
                    IrInstruction.printlnInt(IrExpression.shortArrayLoad(IrExpression.objectLocal("shorts"), IrExpression.intLiteral(0))),
                    IrInstruction.printlnInt(IrExpression.charArrayLoad(IrExpression.objectLocal("chars"), IrExpression.intLiteral(0))),
                    IrInstruction.printlnLong(IrExpression.longArrayLoad(IrExpression.objectLocal("longs"), IrExpression.intLiteral(0))),
                    IrInstruction.printlnFloat(IrExpression.floatArrayLoad(IrExpression.objectLocal("floats"), IrExpression.intLiteral(0))),
                    IrInstruction.printlnDouble(IrExpression.doubleArrayLoad(IrExpression.objectLocal("doubles"), IrExpression.intLiteral(0)))
                )
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "bytes = javan_byte_array_new(2);",
            "shorts = javan_short_array_new(2);",
            "chars = javan_char_array_new(2);",
            "longs = javan_long_array_new(2);",
            "floats = javan_float_array_new(2);",
            "doubles = javan_double_array_new(2);",
            "javan_byte_array_set(bytes, 0, -2);",
            "javan_short_array_set(shorts, 0, 300);",
            "javan_char_array_set(chars, 0, 65);",
            "javan_long_array_set(longs, 0, 7);",
            "javan_float_array_set(floats, 0, 1.25);",
            "javan_double_array_set(doubles, 0, 2.5);",
            "javan_println_int(javan_byte_array_get(bytes, 0));",
            "javan_println_int(javan_short_array_get(shorts, 0));",
            "javan_println_int(javan_char_array_get(chars, 0));",
            "javan_println_long(javan_long_array_get(longs, 0));",
            "javan_println_float(javan_float_array_get(floats, 0));",
            "javan_println_double(javan_double_array_get(doubles, 0));"
        );
    }

    @Test
    void methodDescriptorParsesObjectAndArrayReturns() {
        assertThat(MethodDescriptor.parse("()Ljava/lang/String;").returnType()).isEqualTo(IrType.OBJECT);
        assertThat(MethodDescriptor.parse("()[Ljava/lang/String;").returnType()).isEqualTo(IrType.OBJECT);
    }
}
