package javan;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.analysis.ReachabilityAnalyzer;
import javan.build.BindingLanguage;
import javan.build.BuildKind;
import javan.build.LibraryFormat;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.FieldInfo;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.cli.Command;
import javan.cli.Options;
import javan.codegen.CCodegen;
import javan.codegen.BytecodeToIR;
import javan.codegen.MethodDescriptor;
import javan.codegen.NativeLinker;
import javan.compat.BytecodeSupport;
import javan.compat.JdkCallSupport;
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
import javan.util.Strings2;
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
    void stringsHexLongUsesUnsignedLowercaseText() {
        assertThat(Strings2.hexLong(-1L)).isEqualTo("ffffffffffffffff");
    }

    @Test
    void stringsReplaceCharRewritesOnlyMatchingCharacters() {
        assertThat(Strings2.replaceChar("a/b/c", '/', '.')).isEqualTo("a.b.c");
    }

    @Test
    void optionsParseBuildAndRunArguments() {
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
        assertThat(options.libraryFormats()).containsExactly(LibraryFormat.SHARED);
        assertThat(options.profile()).isEqualTo(Profile.STRICT);
        assertThat(options.exports()).containsExactly("com.acme.Math.add");
        assertThat(options.bindings()).containsExactly(BindingLanguage.C, BindingLanguage.RUST);
        assertThat(options.release()).isTrue();
        assertThat(options.targetTriple()).contains("linux-aarch64");
        assertThat(options.passthroughArgs()).containsExactly("one", "two");
    }

    @Test
    void optionsParseJarBuildKind() {
        final Options options = Options.parse(new String[]{"build", ".", "--kind", "jar"});

        assertThat(options.buildKind()).isEqualTo(BuildKind.JAR);
    }

    @Test
    void optionsParseJarAlias() {
        final Options options = Options.parse(new String[]{"build", ".", "--jar"});

        assertThat(options.buildKind()).isEqualTo(BuildKind.JAR);
    }

    @Test
    void optionsParseLibraryAliasWithBothFormats() {
        final Options options = Options.parse(new String[]{"build", ".", "--library", "--format", "both"});

        assertThat(options.buildKind()).isEqualTo(BuildKind.LIBRARY);
        assertThat(options.libraryFormats()).containsExactly(LibraryFormat.STATIC, LibraryFormat.SHARED);
    }

    @Test
    void optionsParseLibAliasWithStaticFormat() {
        final Options options = Options.parse(new String[]{"build", ".", "--lib", "--format", "static"});

        assertThat(options.buildKind()).isEqualTo(BuildKind.LIBRARY);
        assertThat(options.libraryFormats()).containsExactly(LibraryFormat.STATIC);
    }

    @Test
    void optionsParseLibraryKindWithSharedFormat() {
        final Options options = Options.parse(new String[]{"build", ".", "--kind", "library", "--format", "shared"});

        assertThat(options.buildKind()).isEqualTo(BuildKind.LIBRARY);
        assertThat(options.libraryFormats()).containsExactly(LibraryFormat.SHARED);
    }

    @Test
    void optionsRejectFormatForAppBuild() {
        assertThatThrownBy(() -> Options.parse(new String[]{"build", ".", "--format", "shared"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--format requires --library");
    }

    @Test
    void optionsRejectContradictoryStaticLibraryFormat() {
        assertThatThrownBy(() -> Options.parse(new String[]{"build", ".", "--kind", "staticlib", "--format", "shared"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--kind staticlib only supports --format static");
    }

    @Test
    void optionsRejectContradictorySharedLibraryFormat() {
        assertThatThrownBy(() -> Options.parse(new String[]{"build", ".", "--kind", "sharedlib", "--format", "static"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--kind sharedlib only supports --format shared");
    }

    @Test
    void fileScannerIncludesNestedPackageNamedBuild() throws Exception {
        final Path classFile = tempDir.resolve("classes/javan/build/Foo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[]{0});

        assertThat(Files2.findClassFiles(tempDir.resolve("classes"))).containsExactly(classFile);
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
    void profileParsesAsciiCaseInsensitiveCliValues() {
        assertThat(Profile.parse("CORE")).contains(Profile.CORE);
    }

    @Test
    void profileReportsStableCliName() {
        assertThat(Profile.CORE.cliName()).isEqualTo("core");
        assertThat(Profile.SERVICE.cliName()).isEqualTo("service");
        assertThat(Profile.LIBRARY.cliName()).isEqualTo("library");
        assertThat(Profile.STRICT.cliName()).isEqualTo("strict");
    }

    @Test
    void stringsDetectNullAsBlank() {
        assertThat(Strings2.isBlank(null)).isTrue();
    }

    @Test
    void stringsDetectAsciiWhitespaceAsBlank() {
        assertThat(Strings2.isBlank(" \t\n\r\f")).isTrue();
    }

    @Test
    void stringsDetectVisibleCharactersAsNotBlank() {
        assertThat(Strings2.isBlank(" x ")).isFalse();
    }

    @Test
    void executableNameNormalizesAsciiNames() {
        assertThat(Strings2.executableName("com.acme.Hello Tool")).isEqualTo("hello-tool");
    }

    @Test
    void executableNameFallsBackForBlankValue() {
        assertThat(Strings2.executableName(" \t")).isEqualTo("app");
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
    void nativeLibraryLinkerBuildsSharedLibrary() throws Exception {
        final Path libraryC = Files.writeString(tempDir.resolve("library.c"), "int javan_add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void javan_runtime(void) {}\n");
        final Path output = tempDir.resolve("libok.dylib");

        final Path linked = new NativeLinker().linkSharedLibrary(tempDir, libraryC, runtimeC, output);

        assertThat(linked).isEqualTo(output);
        assertThat(output).isRegularFile();
    }

    @Test
    void nativeLibraryLinkerBuildsStaticLibrary() throws Exception {
        final Path libraryC = Files.writeString(tempDir.resolve("library.c"), "int javan_add(int a, int b) { return a + b; }\n");
        final Path runtimeC = Files.writeString(tempDir.resolve("runtime.c"), "void javan_runtime(void) {}\n");
        final Path output = tempDir.resolve("libok.a");

        final Path linked = new NativeLinker().linkStaticLibrary(tempDir, libraryC, runtimeC, output);

        assertThat(linked).isEqualTo(output);
        assertThat(output).isRegularFile();
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
        assertThat(BytecodeSupport.nativeSupportedOpcodes())
            .contains(47, 48, 49, 80, 81, 82, 126, 146, 165, 166, 170, 171, 186, 188, 190)
            .doesNotContain(197);
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
        final Diagnostic diagnostic = Diagnostic.warning("JAVAN199", "message", "", "", "", "", "");

        assertThat(diagnostic.format()).contains("warning[JAVAN199]", "  -");
    }

    @Test
    void diagnosticFormatsRuntimeFallbackCodeAsError() {
        final Diagnostic diagnostic = Diagnostic.warning("JAVAN900", "message", "Class", "method", "subject", "reason", "fix");

        assertThat(diagnostic.format()).contains("error[JAVAN900]");
    }

    @Test
    void diagnosticFormatsNullCodeAsWarning() {
        final Diagnostic diagnostic = Diagnostic.error(null, "message", "Class", "method", "subject", "reason", "fix");

        assertThat(diagnostic.format()).contains("warning[null]");
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
    void staticVerifierAcceptsSupportedListAddCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 185, "invokeinterface", new MethodRef("java/util/List", "add", "(Ljava/lang/Object;)Z")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsUnsupportedListStreamCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 185, "invokeinterface", new MethodRef("java/util/List", "stream", "()Ljava/util/stream/Stream;")),
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN031");
        assertThat(diagnostics.getFirst().subject()).isEqualTo("java/util/List.stream()Ljava/util/stream/Stream;");
    }

    @Test
    void staticVerifierAcceptsSupportedPathResolveCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 185, "invokeinterface", new MethodRef("java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsUnsupportedPathsGetCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 184, "invokestatic", new MethodRef("java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")),
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN031");
        assertThat(diagnostics.getFirst().subject()).isEqualTo("java/nio/file/Paths.get(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;");
    }

    @Test
    void jdkCallSupportAcceptsArrayListIndexedAdd() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/ArrayList", "add", "(ILjava/lang/Object;)V"))).isTrue();
    }

    @Test
    void jdkCallSupportRejectsUnknownCollectionCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/Collection", "stream", "()Ljava/util/stream/Stream;"))).isFalse();
    }

    @Test
    void jdkCallSupportAcceptsCollectionContains() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/Collection", "contains", "(Ljava/lang/Object;)Z"))).isTrue();
    }

    @Test
    void jdkCallSupportRejectsUnknownIteratorCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/Iterator", "remove", "()V"))).isFalse();
    }

    @Test
    void jdkCallSupportAcceptsMapIsEmpty() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/Map", "isEmpty", "()Z"))).isTrue();
    }

    @Test
    void jdkCallSupportRejectsUnknownMapCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/Map", "entrySet", "()Ljava/util/Set;"))).isFalse();
    }

    @Test
    void jdkCallSupportRejectsUnknownHashMapCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/HashMap", "clear", "()V"))).isFalse();
    }

    @Test
    void jdkCallSupportRejectsUnknownDirectoryStreamCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/nio/file/DirectoryStream", "spliterator", "()Ljava/util/Spliterator;"))).isFalse();
    }

    @Test
    void jdkCallSupportAcceptsThrowableStringConstructor() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsEnumSyntheticConstructor() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V"))).isTrue();
    }

    @Test
    void staticVerifierAcceptsEnumSyntheticConstructorCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 183, "invokespecial", new MethodRef("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierStillWarnsForEnumValueOf() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 184, "invokestatic", new MethodRef("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;")),
            false
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN131");
    }

    @Test
    void staticVerifierSkipsUnreachableGeneratedEnumValueOfBody() {
        final MethodInfo method = generatedEnumValueOfMethod(0x0008, instruction(0, 18, "ldc"));
        final ClassFile enumClass = classWithMethods("com/acme/Color", "java/lang/Enum", 0x4000, List.of(), method);

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(enumClass.name(), enumClass), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierSkipsUnreachableGeneratedEnumValueOfBodyWithResolvedClassLiteral() {
        final MethodInfo method = generatedEnumValueOfMethod(0x0008, classInstruction(0, 18, "ldc", "com/acme/Color"));
        final ClassFile enumClass = classWithMethods("com/acme/Color", "java/lang/Enum", 0x4000, List.of(), method);

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(enumClass.name(), enumClass), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierWarnsForReachableGeneratedEnumValueOfBody() {
        final MethodInfo method = generatedEnumValueOfMethod(0x0008, instruction(0, 18, "ldc"));
        final ClassFile enumClass = classWithMethods("com/acme/Color", "java/lang/Enum", 0x4000, List.of(), method);

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(enumClass.name(), enumClass),
            List.of(new EntryPoint("com/acme/Color", "valueOf", "(Ljava/lang/String;)Lcom/acme/Color;"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN031");
    }

    @Test
    void staticVerifierWarnsForNonStaticEnumValueOfShape() {
        final MethodInfo method = generatedEnumValueOfMethod(0, instruction(0, 18, "ldc"));
        final ClassFile enumClass = classWithMethods("com/acme/Color", "java/lang/Enum", 0x4000, List.of(), method);

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(enumClass.name(), enumClass), List.of());

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN131");
    }

    @Test
    void reachabilityRejectsDirectEnumValueOfEntry() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Color", classWithMethods(
                    "com/acme/Color",
                    "java/lang/Enum",
                    0x4000,
                    List.of(),
                    methodInfo("valueOf", "(Ljava/lang/String;)Lcom/acme/Color;")
                )
            ),
            List.of(new EntryPoint("com/acme/Color", "valueOf", "(Ljava/lang/String;)Lcom/acme/Color;"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN015");
    }

    @Test
    void reachabilityRejectsReachableEnumValueOf() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 184, "invokestatic", new MethodRef("com/acme/Color", "valueOf", "(Ljava/lang/String;)Lcom/acme/Color;")))
                ),
                "com/acme/Color", classWithMethods("com/acme/Color", "java/lang/Enum", 0x4000, List.of())
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN015");
        assertThat(graph.diagnostics().getFirst().subject()).isEqualTo("com/acme/Color.valueOf(Ljava/lang/String;)Lcom/acme/Color;");
    }

    @Test
    void jdkCallSupportRejectsNoopConstructorWithArguments() {
        assertThat(JdkCallSupport.isNoopPlatformConstructor(new MethodRef("java/lang/Object", "<init>", "(Ljava/lang/String;)V"))).isFalse();
    }

    @Test
    void staticVerifierAcceptsAssignableSuperclassInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of("com/acme/Child", typeClass("com/acme/Child", "com/acme/MissingBase", 0, List.of())),
            "com/acme/MissingBase",
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsDirectInterfaceInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of("com/acme/Child", typeClass("com/acme/Child", "java/lang/Object", 0, List.of("com/acme/MissingInterface"))),
            "com/acme/MissingInterface",
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsInheritedInterfaceInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of(
                "com/acme/Child", typeClass("com/acme/Child", "java/lang/Object", 0, List.of("com/acme/KnownInterface")),
                "com/acme/KnownInterface", typeClass("com/acme/KnownInterface", "java/lang/Object", 0x0200, List.of("com/acme/MissingInterface"))
            ),
            "com/acme/MissingInterface",
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsObjectInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(Map.of(), "java/lang/Object", true);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsInstanceofTargetWhenSuperclassChainEnds() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of("com/acme/Child", typeClass("com/acme/Child", "", 0, List.of())),
            "com/acme/MissingBase",
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN045");
    }

    @Test
    void staticVerifierRejectsInstanceofTargetWhenSuperclassCycleCannotProveAssignable() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of(
                "com/acme/A", typeClass("com/acme/A", "com/acme/B", 0, List.of()),
                "com/acme/B", typeClass("com/acme/B", "com/acme/A", 0, List.of())
            ),
            "com/acme/MissingBase",
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN045");
    }

    @Test
    void staticVerifierRejectsInstanceofTargetWhenInterfaceMetadataIsMissing() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of("com/acme/Child", typeClass("com/acme/Child", "java/lang/Object", 0, List.of("com/acme/KnownInterface"))),
            "com/acme/MissingInterface",
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN045");
    }

    @Test
    void staticVerifierRejectsInstanceofTargetWhenInterfaceWasAlreadyVisited() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(
            Map.of(
                "com/acme/Child", typeClass("com/acme/Child", "java/lang/Object", 0, List.of("com/acme/A", "com/acme/B")),
                "com/acme/A", typeClass("com/acme/A", "java/lang/Object", 0x0200, List.of("com/acme/B")),
                "com/acme/B", typeClass("com/acme/B", "java/lang/Object", 0x0200, List.of())
            ),
            "com/acme/MissingInterface",
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN045");
    }

    @Test
    void staticVerifierRejectsUnsupportedReachableInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(Map.of(), "java/util/List", true);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN045");
    }

    @Test
    void staticVerifierWarnsForUnsupportedUnreachableInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(Map.of(), "java/util/List", false);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN145");
    }

    @Test
    void staticVerifierIgnoresInstanceofWithoutClassMetadata() {
        final List<Diagnostic> diagnostics = verifyInstruction(instruction(0, 193, "instanceof"), true);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void reachabilityRejectsEmptyEntryPoints() {
        assertThatThrownBy(() -> new ReachabilityAnalyzer().analyze(Map.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reachability requires at least one entry point");
    }

    @Test
    void reachabilityReportsMissingEntryMethod() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of("com/acme/Main", classWithMethods("com/acme/Main", "java/lang/Object", 0, List.of())),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN011");
    }

    @Test
    void reachabilityReportsMissingApplicationCallTarget() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of("com/acme/Main", classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 184, "invokestatic", new MethodRef("com/acme/Missing", "call", "()V")))
            )),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().subject()).isEqualTo("com/acme/Missing.call()V");
    }

    @Test
    void reachabilityReportsInterfaceCallWithoutImplementation() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of("com/acme/Main", classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 185, "invokeinterface", new MethodRef("com/acme/Handler", "handle", "()V")))
            )),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN012");
    }

    @Test
    void reachabilityResolvesSpecialNonConstructorCall() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 183, "invokespecial", new MethodRef("com/acme/Base", "helper", "()V")))
                ),
                "com/acme/Base", classWithMethods("com/acme/Base", "java/lang/Object", 0, List.of(), methodInfo("helper", "()V"))
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/Base", "helper", "()V"));
    }

    @Test
    void reachabilityResolvesInheritedVirtualTarget() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Base", "value", "()I")))
                ),
                "com/acme/Base", classWithMethods("com/acme/Base", "java/lang/Object", 0, List.of(), methodInfo("value", "()I")),
                "com/acme/Child", classWithMethods("com/acme/Child", "com/acme/Base", 0, List.of())
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/Base", "value", "()I"));
    }

    @Test
    void reachabilityReportsUnresolvedVirtualTarget() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Base", "missing", "()V")))
                ),
                "com/acme/Base", classWithMethods("com/acme/Base", "java/lang/Object", 0, List.of()),
                "com/acme/Child", classWithMethods("com/acme/Child", "com/acme/Base", 0, List.of())
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN012");
    }

    @Test
    void reachabilityAcceptsSupportedPrimitiveArrayClone() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of("com/acme/Main", classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("[I", "clone", "()Ljava/lang/Object;")))
            )),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
    }

    @Test
    void reachabilityReportsUnsupportedBooleanArrayClone() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of("com/acme/Main", classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("[Z", "clone", "()Ljava/lang/Object;")))
            )),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN011");
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
    void staticVerifierAcceptsExplicitThrowRangeWithInstructionBeforeProtectedRange() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 0, "nop"),
            classInstruction(1, 187, "new", "java/lang/IllegalStateException"),
            instruction(2, 183, "invokespecial", new MethodRef("java/lang/IllegalStateException", "<init>", "()V")),
            instruction(3, 191, "athrow")
        ), new CodeException(1, 4, 4, Optional.of("java/lang/IllegalStateException")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsExplicitThrowRangeWithNop() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(explicitThrowInstructions(instruction(1, 0, "nop")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsExplicitThrowRangeWithLdcW() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(explicitThrowInstructions(instruction(1, 19, "ldc_w")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsExplicitThrowRangeWithLdc2W() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(explicitThrowInstructions(instruction(1, 20, "ldc2_w")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsExplicitThrowRangeWithPop() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(explicitThrowInstructions(instruction(1, 87, "pop")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsExplicitThrowConstructorWithoutMethodRef() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            classInstruction(0, 187, "new", "java/lang/IllegalStateException"),
            instruction(1, 183, "invokespecial"),
            instruction(2, 191, "athrow")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/IllegalStateException")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsThrowableConstructorWithCause() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            classInstruction(0, 187, "new", "java/lang/IllegalStateException"),
            instruction(1, 89, "dup"),
            instruction(2, 18, "ldc"),
            classInstruction(3, 187, "new", "java/lang/RuntimeException"),
            instruction(4, 89, "dup"),
            instruction(5, 18, "ldc"),
            instruction(6, 183, "invokespecial", new MethodRef("java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V")),
            instruction(7, 183, "invokespecial", new MethodRef("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V")),
            instruction(8, 191, "athrow")
        ), new CodeException(0, 9, 9, Optional.of("java/lang/IllegalStateException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierAcceptsEnumSwitchMapNoSuchFieldHandler() {
        assertThat(verifyEnumSwitchMapExceptionTable(enumSwitchMapInstructions(5))).isEmpty();
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithoutHandlerInstruction() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(
            enumSwitchMapInstructions(5),
            new CodeException(0, 5, 99, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerThatDoesNotStoreException() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            enumSwitchMapInstructions(5).get(0),
            enumSwitchMapInstructions(5).get(1),
            enumSwitchMapInstructions(5).get(2),
            enumSwitchMapInstructions(5).get(3),
            enumSwitchMapInstructions(5).get(4),
            instruction(5, 177, "return")
        ));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierAcceptsEnumSwitchMapPopHandler() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            enumSwitchMapInstructions(5).get(0),
            enumSwitchMapInstructions(5).get(1),
            enumSwitchMapInstructions(5).get(2),
            enumSwitchMapInstructions(5).get(3),
            enumSwitchMapInstructions(5).get(4),
            instruction(5, 87, "pop")
        ));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithUnsupportedLowStoreOpcode() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            enumSwitchMapInstructions(5).get(0),
            enumSwitchMapInstructions(5).get(1),
            enumSwitchMapInstructions(5).get(2),
            enumSwitchMapInstructions(5).get(3),
            enumSwitchMapInstructions(5).get(4),
            instruction(5, 74, "lstore_3")
        ));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithoutArrayStore() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 178, "getstatic", new FieldRef("com/acme/Main$1", "$SwitchMap$com$acme$Color", "[I")),
            instruction(1, 75, "astore_0")
        ), new CodeException(0, 1, 1, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithEmptyProtectedRange() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(1, 75, "astore_0")
        ), new CodeException(0, 0, 1, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierAcceptsEnumSwitchMapHandlerWithBipushIndex() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 16, "bipush"),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsEnumSwitchMapHandlerWithSipushIndex() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 17, "sipush"),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsEnumSwitchMapHandlerWithWideAloadAndAstore() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 25, "aload"),
            instruction(1, 79, "iastore"),
            instruction(2, 58, "astore")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithUnsupportedProtectedInstruction() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 178, "getstatic", new FieldRef("com/acme/Main$1", "$SwitchMap$com$acme$Color", "[I")),
            instruction(1, 0, "nop"),
            instruction(2, 79, "iastore"),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithGetstaticMissingFieldRef() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 178, "getstatic"),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithInvokevirtualMissingMethodRef() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 182, "invokevirtual"),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithInvokevirtualMissingOwner() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 182, "invokevirtual", new MethodRef("com/acme/MissingColor", "ordinal", "()I")),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithUnsupportedWideLoadOpcode() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 46, "iaload"),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithNonEnumField() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 178, "getstatic", new FieldRef("com/acme/Other", "RED", "Lcom/acme/Other;")),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithNonEnumOrdinalOwner() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(
            Map.of("com/acme/Other", typeClass("com/acme/Other", "java/lang/Object", 0, List.of())),
            List.of(
                instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Other", "ordinal", "()I")),
                instruction(1, 79, "iastore"),
                instruction(2, 75, "astore_0")
            ),
            new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsEnumSwitchMapHandlerWithNonOrdinalMethod() {
        final List<Diagnostic> diagnostics = verifyEnumSwitchMapExceptionTable(List.of(
            instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Color", "name", "()Ljava/lang/String;")),
            instruction(1, 79, "iastore"),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/NoSuchFieldError")));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierAcceptsSyntheticSwitchMapClassShape() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "<clinit>",
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Color", "[I")),
            new CodeException(0, 0, 0, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsSwitchMapClassWhenNotSynthetic() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0,
            "<clinit>",
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Color", "[I")),
            new CodeException(0, 0, 0, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsSyntheticSwitchMapClassWhenInitializerNameDiffers() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "main",
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Color", "[I")),
            new CodeException(0, 0, 0, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsSyntheticSwitchMapClassWithoutSwitchMapFields() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "<clinit>",
            List.of(),
            new CodeException(0, 0, 0, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsSyntheticSwitchMapClassWithWrongFieldName() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "<clinit>",
            List.of(new FieldInfo(0x1008, "values", "[I")),
            new CodeException(0, 0, 0, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsSyntheticSwitchMapClassWithWrongFieldDescriptor() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "<clinit>",
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Color", "[Ljava/lang/String;")),
            new CodeException(0, 0, 0, Optional.of("java/lang/NoSuchFieldError"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsSyntheticSwitchMapClassWithoutCatchType() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "<clinit>",
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Color", "[I")),
            new CodeException(0, 0, 0, Optional.empty())
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
    }

    @Test
    void staticVerifierRejectsSyntheticSwitchMapClassWithWrongCatchType() {
        final List<Diagnostic> diagnostics = verifySyntheticSwitchMapClass(
            0x1000,
            "<clinit>",
            List.of(new FieldInfo(0x1008, "$SwitchMap$com$acme$Color", "[I")),
            new CodeException(0, 0, 0, Optional.of("java/lang/String"))
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN014");
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
        return verifyExceptionTable(Map.of(), instructions, exception);
    }

    private static List<Diagnostic> verifyExceptionTable(final List<Instruction> instructions) {
        return verifyExceptionTable(instructions, new CodeException(0, instructions.size(), instructions.size(), Optional.of("java/lang/IllegalStateException")));
    }

    private static List<Instruction> explicitThrowInstructions(final Instruction protectedInstruction) {
        return List.of(
            classInstruction(0, 187, "new", "java/lang/IllegalStateException"),
            protectedInstruction,
            instruction(2, 87, "pop"),
            instruction(3, 183, "invokespecial", new MethodRef("java/lang/IllegalStateException", "<init>", "()V")),
            instruction(4, 191, "athrow")
        );
    }

    private static List<Diagnostic> verifyEnumSwitchMapExceptionTable(final List<Instruction> instructions) {
        return verifyEnumSwitchMapExceptionTable(
            instructions,
            new CodeException(0, 5, 5, Optional.of("java/lang/NoSuchFieldError"))
        );
    }

    private static List<Diagnostic> verifyEnumSwitchMapExceptionTable(final List<Instruction> instructions, final CodeException exception) {
        return verifyEnumSwitchMapExceptionTable(Map.of(), instructions, exception);
    }

    private static List<Diagnostic> verifyEnumSwitchMapExceptionTable(
        final Map<String, ClassFile> extraClasses,
        final List<Instruction> instructions,
        final CodeException exception
    ) {
        final ClassFile enumClass = new ClassFile(
            69,
            "com/acme/Color",
            "java/lang/Enum",
            0x4000,
            List.of(),
            List.of(new FieldInfo(0x4008, "RED", "Lcom/acme/Color;")),
            List.of(),
            Path.of("Color.class"),
            true
        );
        final ClassFile switchMap = exceptionClassFile("com/acme/Main$1", instructions, exception);
        final Map<String, ClassFile> classes = new java.util.LinkedHashMap<>();
        classes.put(switchMap.name(), switchMap);
        classes.put(enumClass.name(), enumClass);
        classes.putAll(extraClasses);
        return new StaticVerifier().verify(
            classes,
            List.of(new EntryPoint(switchMap.name(), "main", "([Ljava/lang/String;)V"))
        );
    }

    private static List<Diagnostic> verifySyntheticSwitchMapClass(
        final int accessFlags,
        final String methodName,
        final List<FieldInfo> fields,
        final CodeException exception
    ) {
        final MethodInfo method = new MethodInfo(
            0,
            methodName,
            "()V",
            Optional.of(new CodeAttribute(
                1,
                1,
                new byte[]{(byte) 177},
                1,
                List.of(exception),
                List.of(instruction(0, 177, "return"))
            ))
        );
        final ClassFile switchMap = new ClassFile(
            69,
            "com/acme/Main$1",
            "java/lang/Object",
            accessFlags,
            List.of(),
            fields,
            List.of(method),
            Path.of("Main$1.class"),
            true
        );
        return new StaticVerifier().verify(
            Map.of(switchMap.name(), switchMap),
            List.of(new EntryPoint(switchMap.name(), methodName, "()V"))
        );
    }

    private static List<Diagnostic> verifyExceptionTable(
        final Map<String, ClassFile> extraClasses,
        final List<Instruction> instructions,
        final CodeException exception
    ) {
        final ClassFile classFile = exceptionClassFile("com/acme/Main", instructions, exception);
        final Map<String, ClassFile> classes = new java.util.LinkedHashMap<>();
        classes.put(classFile.name(), classFile);
        classes.putAll(extraClasses);
        return new StaticVerifier().verify(
            classes,
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );
    }

    private static ClassFile exceptionClassFile(final String className, final List<Instruction> instructions, final CodeException exception) {
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
        return new ClassFile(
            69,
            className,
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(method),
            Path.of("Main.class"),
            true
        );
    }

    private static List<Instruction> enumSwitchMapInstructions(final int handlerOffset) {
        return List.of(
            instruction(0, 178, "getstatic", new FieldRef("com/acme/Main$1", "$SwitchMap$com$acme$Color", "[I")),
            instruction(1, 178, "getstatic", new FieldRef("com/acme/Color", "RED", "Lcom/acme/Color;")),
            instruction(2, 182, "invokevirtual", new MethodRef("com/acme/Color", "ordinal", "()I")),
            instruction(3, 4, "iconst_1"),
            instruction(4, 79, "iastore"),
            instruction(handlerOffset, 75, "astore_0")
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic, final FieldRef fieldRef) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.of(fieldRef),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic, final MethodRef methodRef) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.of(methodRef),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction classInstruction(final int offset, final int opcode, final String mnemonic, final String className) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.of(className),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static List<Diagnostic> verifyInstruction(final Instruction instruction, final boolean reachable) {
        return verifyInstruction(Map.of(), instruction, reachable);
    }

    private static List<Diagnostic> verifyInstruction(
        final Map<String, ClassFile> extraClasses,
        final Instruction instruction,
        final boolean reachable
    ) {
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
        final List<EntryPoint> reachableMethods = reachable
            ? List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
            : List.of();
        final Map<String, ClassFile> classes = new java.util.LinkedHashMap<>();
        classes.put(classFile.name(), classFile);
        classes.putAll(extraClasses);
        return new StaticVerifier().verify(classes, reachableMethods);
    }

    private static List<Diagnostic> verifyInstanceOf(
        final Map<String, ClassFile> extraClasses,
        final String className,
        final boolean reachable
    ) {
        return verifyInstruction(extraClasses, classInstruction(0, 193, "instanceof", className), reachable);
    }

    private static ClassFile typeClass(
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces
    ) {
        return new ClassFile(
            69,
            name,
            superName,
            accessFlags,
            List.copyOf(interfaces),
            List.of(),
            List.of(),
            Path.of(name + ".class"),
            true
        );
    }

    private static ClassFile classWithMethods(
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final MethodInfo... methods
    ) {
        return new ClassFile(
            69,
            name,
            superName,
            accessFlags,
            List.copyOf(interfaces),
            List.of(),
            List.of(methods),
            Path.of(name + ".class"),
            true
        );
    }

    private static MethodInfo methodInfo(final String name, final String descriptor, final Instruction... instructions) {
        return new MethodInfo(
            0,
            name,
            descriptor,
            Optional.of(new CodeAttribute(2, 1, new byte[0], 0, List.of(instructions)))
        );
    }

    private static MethodInfo generatedEnumValueOfMethod(final int accessFlags, final Instruction firstInstruction) {
        final MethodRef enumValueOf = new MethodRef("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
        return new MethodInfo(
            accessFlags,
            "valueOf",
            "(Ljava/lang/String;)Lcom/acme/Color;",
            Optional.of(new CodeAttribute(
                2,
                1,
                new byte[0],
                0,
                List.of(
                    firstInstruction,
                    instruction(1, 42, "aload_0"),
                    instruction(2, 184, "invokestatic", enumValueOf),
                    classInstruction(3, 192, "checkcast", "com/acme/Color"),
                    instruction(4, 176, "areturn")
                )
            ))
        );
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
    void processRunnerCapturesLargeStderrWithoutDeadlock() throws Exception {
        final ProcessRunner.Result result = new ProcessRunner(Duration.ofSeconds(5)).run(
            tempDir,
            List.of("sh", "-c", "i=0; while [ $i -lt 5000 ]; do echo compiler-warning-$i >&2; i=$((i + 1)); done")
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("compiler-warning-0", "compiler-warning-4999");
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
            "int main(int argc, char** argv) {",
            "static void helper_symbol(void);",
            "helper_symbol();",
            "line\\nquote\\\"slash\\\\tab\\tcarriage\\rcontrol\\001"
        );
    }

    @Test
    void cCodegenEmitsCMainOnlyForProgramEntry() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(
                new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/Helper", "help", "()V", "helper_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.returnVoid()
                ))
            ),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("int main(int argc, char** argv) {");
        assertThat(generated).doesNotContain("static void main_symbol(void)");
        assertThat(generated).contains("static void helper_symbol(void);", "static void helper_symbol(void) {");
    }

    @Test
    void cCodegenChunksLongStringLiterals() throws Exception {
        final String longText = "x".repeat(400);
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                IrInstruction.printlnObject(IrExpression.stringLiteral(longText)),
                IrInstruction.returnVoid()
            ))),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("\"" + "x".repeat(120) + "\"");
        for (final String line : generated.split("\\R")) {
            assertThat(line.length()).isLessThan(220);
        }
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
            "javan_register_generated_type_descriptors();",
            "javan_register_generated_roots();",
            "javan_gc_safe_point();",
            "javan_com_acme_Api__clinit___V();",
            "javan_com_acme_Api_touch___V();"
        );
    }

    @Test
    void cCodegenEmitsStaticRootInventoryForObjectStaticFieldsOnly() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrClass(
                "com/acme/State",
                "javan_class_com_acme_State",
                List.of(),
                List.of(
                    new IrField(IrType.INT, "count", "field_count"),
                    new IrField(IrType.OBJECT, "root", "field_root"),
                    new IrField(IrType.OBJECT, "items", "field_items")
                )
            )),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.returnVoid())
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "static int javan_static_com_acme_State_field_count = 0;",
            "static void* javan_static_com_acme_State_field_root = 0;",
            "static void* javan_static_com_acme_State_field_items = 0;",
            "static void** javan_static_roots[] = {",
            "(void**) &javan_static_com_acme_State_field_root,",
            "(void**) &javan_static_com_acme_State_field_items",
            "javan_register_static_roots(javan_static_roots, 2);",
            "javan_register_generated_roots();",
            "javan_gc_safe_point();"
        );
        assertThat(generated).doesNotContain("&javan_static_com_acme_State_field_count");
        assertThat(generated.indexOf("javan_register_generated_roots();")).isLessThan(generated.indexOf("return 0;"));
    }

    @Test
    void cCodegenEmitsTypeDescriptorsForObjectInstanceFieldsOnly() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrClass(
                "com/acme/Node",
                "javan_class_com_acme_Node",
                List.of(
                    new IrField(IrType.INT, "count", "field_count"),
                    new IrField(IrType.OBJECT, "child", "field_child"),
                    new IrField(IrType.DOUBLE, "weight", "field_weight"),
                    new IrField(IrType.OBJECT, "items", "field_items")
                )
            )),
            List.of(new IrFunction(
                "com/acme/Main",
                "main",
                "([Ljava/lang/String;)V",
                "main_symbol",
                IrType.VOID,
                List.of(),
                List.of(),
                List.of(IrInstruction.returnVoid())
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "#include <stddef.h>",
            "static unsigned long javan_type_fields_com_acme_Node[] = {",
            "(unsigned long) offsetof(struct javan_class_com_acme_Node, field_child),",
            "(unsigned long) offsetof(struct javan_class_com_acme_Node, field_items)",
            "static JavanTypeDescriptor javan_type_descriptors[] = {",
            "{1, \"com/acme/Node\", 2, javan_type_fields_com_acme_Node}",
            "javan_register_type_descriptors(javan_type_descriptors, 1);",
            "javan_register_generated_type_descriptors();",
            "javan_gc_safe_point();"
        );
        assertThat(generated).doesNotContain(
            "offsetof(struct javan_class_com_acme_Node, field_count)",
            "offsetof(struct javan_class_com_acme_Node, field_weight)"
        );
    }

    @Test
    void cCodegenEmitsRootFramesForObjectParametersAndLocalsOnly() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(new IrClass("com/acme/Node", "javan_class_com_acme_Node", List.of())),
            List.of(new IrFunction(
                "com/acme/Node",
                "pick",
                "(Lcom/acme/Node;I)Lcom/acme/Node;",
                "pick_symbol",
                IrType.OBJECT,
                List.of(new IrParameter(IrType.OBJECT, "self"), new IrParameter(IrType.INT, "count")),
                List.of(new IrLocal(IrType.OBJECT, "tmp"), new IrLocal(IrType.INT, "index")),
                List.of(
                    IrInstruction.assignObject("tmp", IrExpression.objectLocal("self")),
                    IrInstruction.returnObject(IrExpression.objectLocal("tmp"))
                )
            )),
            "pick_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void** javan_roots_pick_symbol[] = {",
            "(void**) &self,",
            "(void**) &tmp",
            "javan_root_frame_push(javan_roots_pick_symbol, 2);",
            "javan_gc_safe_point();",
            "void* javan_return_value = tmp;",
            "javan_generated_return_root = javan_return_value;",
            "javan_gc_safe_point();",
            "javan_root_frame_pop(javan_roots_pick_symbol);",
            "javan_generated_return_root = 0;",
            "return javan_return_value;"
        );
        assertThat(generated).doesNotContain("(void**) &count", "(void**) &index");
        assertThat(generated.indexOf("javan_root_frame_push(javan_roots_pick_symbol, 2);"))
            .isLessThan(generated.indexOf("javan_gc_safe_point();"));
        assertThat(generated.indexOf("javan_gc_safe_point();"))
            .isLessThan(generated.indexOf("void* javan_return_value = tmp;"));
        assertThat(generated.indexOf("void* javan_return_value = tmp;"))
            .isLessThan(generated.indexOf("javan_generated_return_root = javan_return_value;"));
        assertThat(generated.indexOf("javan_generated_return_root = javan_return_value;"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_roots_pick_symbol);"));
        assertThat(generated.indexOf("javan_root_frame_pop(javan_roots_pick_symbol);"))
            .isLessThan(generated.lastIndexOf("javan_generated_return_root = 0;"));
    }

    @Test
    void cCodegenFreesByteArrayExportInputsAfterAbiResultIsCreated() throws Exception {
        final EntryPoint exportEntry = new EntryPoint("com/acme/Bytes", "echo", "([B)[B");
        final IrProgram program = new IrProgram(
            List.of(new IrClass("com/acme/Bytes", "javan_class_com_acme_Bytes", List.of())),
            List.of(new IrFunction(
                exportEntry.className(),
                exportEntry.methodName(),
                exportEntry.descriptor(),
                BytecodeToIR.symbol(exportEntry),
                IrType.OBJECT,
                List.of(new IrParameter(IrType.OBJECT, "data")),
                List.of(),
                List.of(IrInstruction.returnObject(IrExpression.objectLocal("data")))
            )),
            BytecodeToIR.symbol(exportEntry)
        );

        final String generated = Files.readString(new CCodegen().generateLibrary(
            program,
            tempDir,
            List.of(new javan.build.ExportedMethod(
                exportEntry,
                "javan_export_com_acme_Bytes_echo_bytes",
                List.of(javan.build.AbiType.BYTE_ARRAY),
                javan.build.AbiType.BYTE_ARRAY
            ))
        ));

        assertThat(generated).contains(
            "void* arg0_array = 0;",
            "void** javan_export_roots[] = {",
            "(void**) &arg0_array",
            "javan_root_frame_push(javan_export_roots, 1);",
            "arg0_array = javan_byte_array_from(arg0.data, arg0.length);",
            "void* javan_export_object_result = javan_com_acme_Bytes_echo___B__B(arg0_array);",
            "void** javan_export_result_roots[] = {",
            "(void**) &javan_export_object_result",
            "javan_root_frame_push(javan_export_result_roots, 1);",
            "JavanByteArray javan_export_result = javan_byte_array_export(javan_export_object_result);",
            "javan_root_frame_pop(javan_export_result_roots);",
            "javan_root_frame_pop(javan_export_roots);",
            "javan_free(arg0_array);",
            "return javan_export_result;"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_export_roots, 1);"))
            .isLessThan(generated.indexOf("arg0_array = javan_byte_array_from(arg0.data, arg0.length);"));
        assertThat(generated.indexOf("void* javan_export_object_result = javan_com_acme_Bytes_echo___B__B(arg0_array);"))
            .isLessThan(generated.indexOf("javan_root_frame_push(javan_export_result_roots, 1);"));
        assertThat(generated.indexOf("javan_root_frame_push(javan_export_result_roots, 1);"))
            .isLessThan(generated.indexOf("javan_byte_array_export"));
        assertThat(generated.indexOf("javan_byte_array_export"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_export_result_roots);"));
        assertThat(generated.indexOf("javan_root_frame_pop(javan_export_result_roots);"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_export_roots);"));
        assertThat(generated.indexOf("javan_root_frame_pop(javan_export_roots);"))
            .isLessThan(generated.indexOf("javan_free(arg0_array);"));
    }

    @Test
    void cCodegenRootsStringExportResultUntilAbiCopyCompletes() throws Exception {
        final EntryPoint exportEntry = new EntryPoint("com/acme/Text", "greet", "(Ljava/lang/String;)Ljava/lang/String;");
        final IrProgram program = new IrProgram(
            List.of(new IrClass("com/acme/Text", "javan_class_com_acme_Text", List.of())),
            List.of(new IrFunction(
                exportEntry.className(),
                exportEntry.methodName(),
                exportEntry.descriptor(),
                BytecodeToIR.symbol(exportEntry),
                IrType.OBJECT,
                List.of(new IrParameter(IrType.OBJECT, "name")),
                List.of(),
                List.of(IrInstruction.returnObject(IrExpression.stringConcat(
                    "\u0001!",
                    List.of(IrExpression.objectLocal("name"))
                )))
            )),
            BytecodeToIR.symbol(exportEntry)
        );

        final String generated = Files.readString(new CCodegen().generateLibrary(
            program,
            tempDir,
            List.of(new javan.build.ExportedMethod(
                exportEntry,
                "javan_export_com_acme_Text_greet_string",
                List.of(javan.build.AbiType.STRING),
                javan.build.AbiType.STRING
            ))
        ));

        assertThat(generated).contains(
            "void* javan_export_object_result = javan_com_acme_Text_greet__Ljava_lang_String__Ljava_lang_String_((void*) arg0);",
            "void** javan_export_result_roots[] = {",
            "(void**) &javan_export_object_result",
            "javan_root_frame_push(javan_export_result_roots, 1);",
            "char* javan_export_result = javan_string_export((const char*) javan_export_object_result);",
            "javan_root_frame_pop(javan_export_result_roots);",
            "return javan_export_result;"
        );
        assertThat(generated.indexOf("void* javan_export_object_result = javan_com_acme_Text_greet__Ljava_lang_String__Ljava_lang_String_((void*) arg0);"))
            .isLessThan(generated.indexOf("javan_root_frame_push(javan_export_result_roots, 1);"));
        assertThat(generated.indexOf("javan_root_frame_push(javan_export_result_roots, 1);"))
            .isLessThan(generated.indexOf("javan_string_export"));
        assertThat(generated.indexOf("javan_string_export"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_export_result_roots);"));
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
            "javan_expr_tmp_0 = javan_new_com_acme_Message();",
            "message = javan_expr_tmp_0;",
            "((struct javan_class_com_acme_Message*) message)->field_text = (void*) \"hello\";",
            "javan_expr_tmp_0 = ((struct javan_class_com_acme_Message*) message)->field_text;",
            "javan_println((const char*) javan_expr_tmp_0);",
            "void* javan_return_value = (void*) \"returned\";",
            "javan_generated_return_root = javan_return_value;",
            "return javan_return_value;"
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
            "javan_expr_tmp_0 = javan_byte_array_new(2);",
            "bytes = javan_expr_tmp_0;",
            "javan_expr_tmp_0 = javan_short_array_new(2);",
            "shorts = javan_expr_tmp_0;",
            "javan_expr_tmp_0 = javan_char_array_new(2);",
            "chars = javan_expr_tmp_0;",
            "javan_expr_tmp_0 = javan_long_array_new(2);",
            "longs = javan_expr_tmp_0;",
            "javan_expr_tmp_0 = javan_float_array_new(2);",
            "floats = javan_expr_tmp_0;",
            "javan_expr_tmp_0 = javan_double_array_new(2);",
            "doubles = javan_expr_tmp_0;",
            "javan_byte_array_set(bytes, 0, -2);",
            "javan_short_array_set(shorts, 0, 300);",
            "javan_char_array_set(chars, 0, 65);",
            "javan_long_array_set(longs, 0, 7LL);",
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
