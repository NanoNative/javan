package javan;

import javan.analysis.CallGraph;
import javan.analysis.CallEdge;
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
import javan.compat.NetworkApiSupport;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    void buildResultWithoutArtifactDoesNotPass() {
        final Javan.BuildResult result = new Javan.BuildResult(Optional.empty(), List.of());

        assertThat(result.pass()).isFalse();
    }

    @Test
    void buildResultWithFatalDiagnosticDoesNotPass() {
        final Javan.BuildResult result = new Javan.BuildResult(
            Optional.of(tempDir.resolve("app")),
            List.of(Diagnostic.error(
                "JAVAN031",
                "unsupported reachable JDK call",
                "com/acme/Main",
                "main()V",
                "java/lang/System.exit(I)V",
                "unsupported",
                "remove the call"
            ))
        );

        assertThat(result.pass()).isFalse();
    }

    @Test
    void buildResultWithArtifactAndWarningsPasses() {
        final Javan.BuildResult result = new Javan.BuildResult(
            Optional.of(tempDir.resolve("app")),
            List.of(Diagnostic.warning(
                "JAVAN130",
                "unsupported bytecode in unreachable code",
                "com/acme/Main",
                "dead()V",
                "invokedynamic",
                "unreachable",
                "remove dead code"
            ))
        );

        assertThat(result.pass()).isTrue();
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
        assertThat(BytecodeSupport.mnemonic(-1)).isEqualTo("opcode_-1");
        assertThat(BytecodeSupport.knownOpcodes()).contains(0, 201).doesNotContain(255);
        assertThat(BytecodeSupport.nativeSupportedOpcodes())
            .contains(47, 48, 49, 80, 81, 82, 126, 146, 165, 166, 170, 171, 186, 188, 190)
            .doesNotContain(197);
        assertThat(BytecodeSupport.knownOpcodes()).containsExactlyElementsOf(BytecodeSupport.knownOpcodes());
    }

    @Test
    void bytecodeSupportOpcodeListsAreSortedAndReadOnly() {
        final List<Integer> opcodes = BytecodeSupport.knownOpcodes();

        assertThat(opcodes).hasSize(202);
        assertThat(opcodes.isEmpty()).isFalse();
        assertThat(opcodes.contains(Integer.valueOf(0))).isTrue();
        assertThat(opcodes.contains(Integer.valueOf(255))).isFalse();
        assertThat(opcodes.contains("0")).isFalse();
        assertThat(opcodes.containsAll(List.of(0, 201))).isTrue();
        assertThat(opcodes.containsAll(List.of(0, 255))).isFalse();
        assertThat(opcodes.toArray()).hasSize(202);
        assertThat(opcodes.toArray(new Object[0])).hasSize(202);
        final Integer[] padded = opcodes.toArray(new Integer[203]);
        assertThat(padded[0]).isEqualTo(0);
        assertThat(padded[201]).isEqualTo(201);
        assertThat(padded[202]).isNull();
        assertThat(opcodes).containsExactlyElementsOf(BytecodeSupport.knownOpcodes());
        assertThatThrownBy(() -> opcodes.add(255)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opcodes.remove(Integer.valueOf(0))).isInstanceOf(UnsupportedOperationException.class);
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
    void staticVerifierRejectsReachableSocketCallWithNetworkDiagnostic() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 183, "invokespecial", new MethodRef("java/net/Socket", "<init>", "()V")),
            true
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN061");
        assertThat(diagnostics.getFirst().message()).isEqualTo("unsupported reachable network API");
        assertThat(diagnostics.getFirst().subject()).isEqualTo("java/net/Socket.<init>()V");
        assertThat(diagnostics.getFirst().reason()).contains("network/socket");
    }

    @Test
    void staticVerifierAcceptsSupportedHttpCallEvenWhenUnreachable() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 184, "invokestatic", new MethodRef("java/net/http/HttpClient", "newHttpClient", "()Ljava/net/http/HttpClient;")),
            false
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void networkApiSupportClassifiesSocketHttpAndJdkHttpServerOwners() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/ServerSocket", "<init>", "()V")))
            .containsExactly("network", "socket");
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/http/HttpClient", "newHttpClient", "()Ljava/net/http/HttpClient;")))
            .containsExactly("network", "http");
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("com/sun/net/httpserver/HttpServer", "create", "()Lcom/sun/net/httpserver/HttpServer;")))
            .containsExactly("network", "http");
    }

    @Test
    void networkApiSupportClassifiesSocketAddressOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/SocketAddress", "toString", "()Ljava/lang/String;")))
            .containsExactly("network", "socket");
    }

    @Test
    void networkApiSupportClassifiesInetSocketAddressOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/InetSocketAddress", "<init>", "(Ljava/lang/String;I)V")))
            .containsExactly("network", "socket");
    }

    @Test
    void networkApiSupportClassifiesInetAddressOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;")))
            .containsExactly("network", "socket");
    }

    @Test
    void jdkCallSupportAcceptsInetAddressLoopbackCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/InetAddress", "getLoopbackAddress", "()Ljava/net/InetAddress;"))).isTrue();
    }

    @Test
    void jdkCallSupportClassifiesInetSocketAddressRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/net/InetSocketAddress", "getPort", "()I")))
            .containsExactly("network", "socket");
    }

    @Test
    void jdkCallSupportAcceptsSocketStringPortConstructor() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/Socket", "<init>", "(Ljava/lang/String;I)V"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsSocketInetAddressPortConstructor() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/Socket", "<init>", "(Ljava/net/InetAddress;I)V"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsServerSocketPortConstructor() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/ServerSocket", "<init>", "(I)V"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsSocketInputStreamReadCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/io/InputStream", "read", "()I"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsSocketOutputStreamWriteByteArrayCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/io/OutputStream", "write", "([B)V"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsHttpClientSendCalls() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/URI", "create", "(Ljava/lang/String;)Ljava/net/URI;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpClient", "newHttpClient", "()Ljava/net/http/HttpClient;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest", "newBuilder", "(Ljava/net/URI;)Ljava/net/http/HttpRequest$Builder;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$Builder", "GET", "()Ljava/net/http/HttpRequest$Builder;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$Builder", "header", "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/http/HttpRequest$Builder;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$BodyPublishers", "ofString", "(Ljava/lang/String;)Ljava/net/http/HttpRequest$BodyPublisher;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$Builder", "POST", "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$BodyPublishers", "ofByteArray", "([B)Ljava/net/http/HttpRequest$BodyPublisher;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$Builder", "PUT", "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpRequest$Builder", "build", "()Ljava/net/http/HttpRequest;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpResponse$BodyHandlers", "ofString", "()Ljava/net/http/HttpResponse$BodyHandler;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpResponse$BodyHandlers", "ofByteArray", "()Ljava/net/http/HttpResponse$BodyHandler;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpClient", "send", "(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpResponse", "statusCode", "()I"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/net/http/HttpResponse", "body", "()Ljava/lang/Object;"))).isTrue();
    }

    @Test
    void staticVerifierAcceptsSupportedSocketConstructorCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 183, "invokespecial", new MethodRef("java/net/Socket", "<init>", "(Ljava/lang/String;I)V")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedSocketInetAddressConstructorCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 183, "invokespecial", new MethodRef("java/net/Socket", "<init>", "(Ljava/net/InetAddress;I)V")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedServerSocketConstructorCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 183, "invokespecial", new MethodRef("java/net/ServerSocket", "<init>", "(I)V")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedSocketInputStreamReadCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 182, "invokevirtual", new MethodRef("java/io/InputStream", "read", "()I")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedSocketOutputStreamWriteCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 182, "invokevirtual", new MethodRef("java/io/OutputStream", "write", "([B)V")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedInetAddressLoopbackCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 184, "invokestatic", new MethodRef("java/net/InetAddress", "getLoopbackAddress", "()Ljava/net/InetAddress;")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedHttpClientSendCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 182, "invokevirtual", new MethodRef("java/net/http/HttpClient", "send", "(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedHttpRequestPostCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 185, "invokeinterface", new MethodRef("java/net/http/HttpRequest$Builder", "POST", "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsSupportedHttpRequestPutCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 185, "invokeinterface", new MethodRef("java/net/http/HttpRequest$Builder", "PUT", "(Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void networkApiSupportClassifiesDatagramSocketOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/DatagramSocket", "<init>", "()V")))
            .containsExactly("network", "socket");
    }

    @Test
    void networkApiSupportClassifiesDatagramPacketOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/net/DatagramPacket", "<init>", "([BI)V")))
            .containsExactly("network", "socket");
    }

    @Test
    void networkApiSupportClassifiesTlsOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("javax/net/ssl/SSLSocketFactory", "getDefault", "()Ljavax/net/SocketFactory;")))
            .containsExactly("network", "tls");
    }

    @Test
    void networkApiSupportClassifiesCertificateOwner() {
        assertThat(NetworkApiSupport.runtimeModules(new MethodRef("java/security/cert/CertificateFactory", "getInstance", "(Ljava/lang/String;)Ljava/security/cert/CertificateFactory;")))
            .containsExactly("network", "certificates");
    }

    @Test
    void networkApiSupportIgnoresNonNetworkOwner() {
        final MethodRef methodRef = new MethodRef("java/lang/String", "length", "()I");

        assertThat(NetworkApiSupport.runtimeModules(methodRef)).isEmpty();
        assertThat(NetworkApiSupport.isNetworkCall(methodRef)).isFalse();
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
    void staticVerifierAcceptsSupportedPathsGetCall() {
        final List<Diagnostic> diagnostics = verifyInstruction(
            instruction(0, 184, "invokestatic", new MethodRef("java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")),
            true
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void jdkCallSupportAcceptsArrayListIndexedAdd() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/util/ArrayList", "add", "(ILjava/lang/Object;)V"))).isTrue();
    }

    @Test
    void jdkCallSupportClassifiesListRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/util/List", "of", "()Ljava/util/List;")))
            .containsExactly("collections");
    }

    @Test
    void jdkCallSupportClassifiesMapRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")))
            .containsExactly("maps");
    }

    @Test
    void jdkCallSupportClassifiesOptionalRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;")))
            .containsExactly("optional");
    }

    @Test
    void jdkCallSupportClassifiesFilesystemRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/nio/file/Files", "readString", "(Ljava/nio/file/Path;)Ljava/lang/String;")))
            .containsExactly("filesystem");
    }

    @Test
    void jdkCallSupportClassifiesFileTimeRuntimeModules() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/nio/file/attribute/FileTime", "toMillis", "()J")))
            .containsExactly("filesystem", "time");
    }

    @Test
    void jdkCallSupportClassifiesDurationRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/time/Duration", "ofMillis", "(J)Ljava/time/Duration;")))
            .containsExactly("time");
    }

    @Test
    void jdkCallSupportClassifiesThreadRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")))
            .containsExactly("threads");
    }

    @Test
    void jdkCallSupportClassifiesThreadConstructorRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")))
            .containsExactly("threads");
    }

    @Test
    void jdkCallSupportClassifiesThreadSleepRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/Thread", "sleep", "(J)V")))
            .containsExactly("threads");
    }

    @Test
    void jdkCallSupportClassifiesThreadIsAliveRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/Thread", "isAlive", "()Z")))
            .containsExactly("threads");
    }

    @Test
    void jdkCallSupportClassifiesEnvironmentRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")))
            .containsExactly("environment");
    }

    @Test
    void jdkCallSupportClassifiesArraycopyRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")))
            .containsExactly("arrays");
    }

    @Test
    void jdkCallSupportClassifiesMathRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/Math", "abs", "(I)I")))
            .containsExactly("math");
    }

    @Test
    void jdkCallSupportClassifiesIoRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")))
            .containsExactly("io");
    }

    @Test
    void jdkCallSupportClassifiesExceptionRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V")))
            .containsExactly("exceptions");
    }

    @Test
    void jdkCallSupportClassifiesManagedHeapRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")))
            .containsExactly("managed-heap");
    }

    @Test
    void jdkCallSupportClassifiesProcessRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/lang/System", "exit", "(I)V")))
            .containsExactly("process");
    }

    @Test
    void jdkCallSupportIgnoresUnsupportedRuntimeModuleCandidate() {
        assertThat(JdkCallSupport.runtimeModules(new MethodRef("java/util/Collection", "stream", "()Ljava/util/stream/Stream;")))
            .isEmpty();
    }

    @Test
    void nativeSubstitutionsClassifyProcessRuntimeModule() {
        assertThat(javan.compat.JavanNativeSubstitutions.runtimeModules(new MethodRef(
            "javan/util/ProcessRunner",
            "run",
            "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;"
        )))
            .containsExactly("process");
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
    void jdkCallSupportAcceptsFileTimeToMillis() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/nio/file/attribute/FileTime", "toMillis", "()J"))).isTrue();
    }

    @Test
    void jdkCallSupportRejectsUnknownFileTimeCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/nio/file/attribute/FileTime", "toInstant", "()Ljava/time/Instant;"))).isFalse();
    }

    @Test
    void jdkCallSupportAcceptsDurationOfMillis() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/time/Duration", "ofMillis", "(J)Ljava/time/Duration;"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsDurationOfSeconds() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/time/Duration", "ofSeconds", "(J)Ljava/time/Duration;"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsDurationToMillis() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/time/Duration", "toMillis", "()J"))).isTrue();
    }

    @Test
    void jdkCallSupportAcceptsCurrentThreadInterruptStateCalls() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "<init>", "()V"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "sleep", "(J)V"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "interrupted", "()Z"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "interrupt", "()V"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "isInterrupted", "()Z"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "isAlive", "()Z"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "start", "()V"))).isTrue();
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "join", "()V"))).isTrue();
    }

    @Test
    void jdkCallSupportRejectsThreadIsAliveWrongDescriptor() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/lang/Thread", "isAlive", "(I)Z"))).isFalse();
    }

    @Test
    void jdkCallSupportRejectsUnknownDurationCall() {
        assertThat(JdkCallSupport.isSupported(new MethodRef("java/time/Duration", "ofNanos", "(J)Ljava/time/Duration;"))).isFalse();
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
    void staticVerifierAcceptsBooleanWrapperInstanceofTarget() {
        final List<Diagnostic> diagnostics = verifyInstanceOf(Map.of(), "java/lang/Boolean", true);

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
    void reachabilityReportsMissingEntryClass() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).hasSize(1);
        assertThat(graph.diagnostics().getFirst().code()).isEqualTo("JAVAN011");
        assertThat(graph.diagnostics().getFirst().subject()).isEqualTo("com/acme/Main.main([Ljava/lang/String;)V");
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
    void reachabilityRecordsDirectCallEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint helper = new EntryPoint("com/acme/Base", "helper", "()V");
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
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, helper, CallEdge.Kind.CALL));
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
    void reachabilitySkipsAbstractVirtualTargetWithoutCode() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Base", "value", "()I")))
                ),
                "com/acme/Base", classWithMethods(
                    "com/acme/Base",
                    "java/lang/Object",
                    0x0400,
                    List.of(),
                    new MethodInfo(0x0401, "value", "()I", Optional.empty())
                ),
                "com/acme/Leaf", classWithMethods(
                    "com/acme/Leaf",
                    "com/acme/Base",
                    0,
                    List.of(),
                    methodInfo("value", "()I")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/Leaf", "value", "()I"));
        assertThat(graph.reachableMethods()).doesNotContain(new EntryPoint("com/acme/Base", "value", "()I"));
    }

    @Test
    void reachabilityResolvesInterfaceCallWithConcreteImplementation() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 185, "invokeinterface", new MethodRef("com/acme/Handler", "handle", "()V")))
                ),
                "com/acme/Handler", classWithMethods(
                    "com/acme/Handler",
                    "java/lang/Object",
                    0x0200,
                    List.of(),
                    methodInfo("handle", "()V")
                ),
                "com/acme/HandlerImpl", classWithMethods(
                    "com/acme/HandlerImpl",
                    "java/lang/Object",
                    0,
                    List.of("com/acme/Handler"),
                    methodInfo("handle", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/HandlerImpl", "handle", "()V"));
    }

    @Test
    void reachabilityResolvesVirtualCallToFinalClassExactly() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("com/acme/Leaf", "value", "()I")))
                ),
                "com/acme/Leaf", classWithMethods(
                    "com/acme/Leaf",
                    "java/lang/Object",
                    0x0010,
                    List.of(),
                    methodInfo("value", "()I")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/Leaf", "value", "()I"));
    }

    @Test
    void reachabilityEnqueuesClassInitializerForStaticCall() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 184, "invokestatic", new MethodRef("com/acme/Util", "helper", "()V")))
                ),
                "com/acme/Util", classWithMethods(
                    "com/acme/Util",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo("<clinit>", "()V"),
                    methodInfo("helper", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(
            new EntryPoint("com/acme/Util", "<clinit>", "()V"),
            new EntryPoint("com/acme/Util", "helper", "()V")
        );
    }

    @Test
    void reachabilityKeepsThreadStartWithoutRunnableTargetsAtEntryOnly() {
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of("com/acme/Main", classWithMethods(
                "com/acme/Main",
                "java/lang/Object",
                0,
                List.of(),
                methodInfo("main", "([Ljava/lang/String;)V", instruction(0, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")))
            )),
            List.of(entry)
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).containsExactly(entry);
    }

    @Test
    void reachabilityFindsRunnableTargetsThroughInterfaceInheritanceForThreadStart() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        classInstruction(0, 187, "new", "java/lang/Thread"),
                        instruction(1, 89, "dup"),
                        classInstruction(2, 187, "new", "com/acme/TaskImpl"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/TaskImpl", "<init>", "()V")),
                        instruction(5, 183, "invokespecial", new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                        instruction(6, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(7, 177, "return")
                    )
                ),
                "com/acme/TaskLike", classWithMethods(
                    "com/acme/TaskLike",
                    "java/lang/Object",
                    0x0200,
                    List.of("java/lang/Runnable"),
                    methodInfo("run", "()V")
                ),
                "com/acme/TaskImpl", classWithMethods(
                    "com/acme/TaskImpl",
                    "java/lang/Object",
                    0,
                    List.of("com/acme/TaskLike"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/TaskImpl", "run", "()V"));
    }

    @Test
    void reachabilityRecordsThreadStartTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        classInstruction(0, 187, "new", "java/lang/Thread"),
                        instruction(1, 89, "dup"),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 183, "invokespecial", new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                        instruction(6, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(7, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsVirtualThreadStartTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 184, "invokestatic", new MethodRef("java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderStartTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualNamedBuilderStartTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderUnstartedTaskEdgeAfterStart() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(6, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualNamedBuilderAliasUnstartedTaskEdgeAfterStart() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 43, "aload_1"),
                        classInstruction(5, 187, "new", "com/acme/Task"),
                        instruction(6, 89, "dup"),
                        instruction(7, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(9, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(10, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualFactoryNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(7, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualFactoryAliasNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 76, "astore_1"),
                        instruction(3, 43, "aload_1"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(9, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualFactoryAliasSlotZeroNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(9, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualFactoryAliasSlotTwoNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 77, "astore_2"),
                        instruction(3, 44, "aload_2"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(9, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualNamedFactoryAliasNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(4, 76, "astore_1"),
                        instruction(5, 43, "aload_1"),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(11, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualFactoryAliasSlotThreeNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 78, "astore_3"),
                        instruction(3, 45, "aload_3"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(9, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualFactoryGenericAliasNewThreadTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instructionOperands(2, 58, "astore", 4),
                        instructionOperands(4, 25, "aload", 4),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(11, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderLocalAliasTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualNamedBuilderLocalAliasTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 43, "aload_1"),
                        classInstruction(5, 187, "new", "com/acme/Task"),
                        instruction(6, 89, "dup"),
                        instruction(7, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(9, 87, "pop"),
                        instruction(10, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderLocalAliasSlotZeroTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderLocalAliasSlotTwoTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 77, "astore_2"),
                        instruction(2, 44, "aload_2"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderLocalAliasSlotThreeTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 78, "astore_3"),
                        instruction(2, 45, "aload_3"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityRecordsThreadOfVirtualBuilderGenericAliasSlotTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instructionOperands(1, 58, "astore", 5),
                        instructionOperands(2, 25, "aload", 5),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualBuilderAliasSlotMismatch() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 44, "aload_2"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualBuilderUnstartedAliasSlotMismatchAfterStart() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 44, "aload_2"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualFactoryAliasSlotMismatch() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 76, "astore_1"),
                        instruction(3, 44, "aload_2"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 75, "astore_0"),
                        instruction(9, 42, "aload_0"),
                        instruction(10, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(11, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualFactoryNewThreadWithRunnableParameter() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "(Ljava/lang/Runnable;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "(Ljava/lang/Runnable;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(4, 75, "astore_0"),
                        instruction(5, 42, "aload_0"),
                        instruction(6, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(7, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityTracksThreadOfVirtualStartWithPrebuiltRunnableAlias() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(5, 43, "aload_1"),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 75, "astore_0"),
                        instruction(8, 42, "aload_0"),
                        instruction(9, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(10, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityTracksThreadOfVirtualFactoryNewThreadWithPrebuiltRunnableAlias() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(6, 43, "aload_1"),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 75, "astore_0"),
                        instruction(9, 42, "aload_0"),
                        instruction(10, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(11, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityTracksThreadOfVirtualStartViaStaticBuilderHelper() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint helper = new EntryPoint("com/acme/Main", "builder", "()Ljava/lang/Thread$Builder$OfVirtual;");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("com/acme/Main", "builder", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 75, "astore_0"),
                        instruction(6, 42, "aload_0"),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(8, 177, "return")
                    ),
                    methodInfo(
                        "builder",
                        "()Ljava/lang/Thread$Builder$OfVirtual;",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.reachableMethods()).contains(helper);
        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityTracksThreadOfVirtualFactoryViaStaticHelper() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint helper = new EntryPoint("com/acme/Main", "factory", "()Ljava/util/concurrent/ThreadFactory;");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("com/acme/Main", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 75, "astore_0"),
                        instruction(6, 42, "aload_0"),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(8, 177, "return")
                    ),
                    methodInfo(
                        "factory",
                        "()Ljava/util/concurrent/ThreadFactory;",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 176, "areturn")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.reachableMethods()).contains(helper);
        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityTracksThreadOfVirtualStartViaParameterizedStaticBuilderHelper() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint helper = new EntryPoint("com/acme/Main", "builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 18, "ldc"),
                        instruction(1, 184, "invokestatic", new MethodRef("com/acme/Main", "builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 75, "astore_0"),
                        instruction(7, 42, "aload_0"),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(9, 177, "return")
                    ),
                    new MethodInfo(
                        0x0008,
                        "builder",
                        "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;",
                        Optional.of(new CodeAttribute(
                            2,
                            1,
                            new byte[0],
                            0,
                            List.of(
                                instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                                instruction(1, 42, "aload_0"),
                                instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                                instruction(3, 176, "areturn")
                            )
                        ))
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.reachableMethods()).contains(helper);
        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityTracksThreadOfVirtualFactoryViaParameterizedStaticHelper() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint helper = new EntryPoint("com/acme/Main", "factory", "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 18, "ldc"),
                        instruction(1, 10, "lconst_1"),
                        instruction(2, 184, "invokestatic", new MethodRef("com/acme/Main", "factory", "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 75, "astore_0"),
                        instruction(8, 42, "aload_0"),
                        instruction(9, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(10, 177, "return")
                    ),
                    new MethodInfo(
                        0x0008,
                        "factory",
                        "(Ljava/lang/String;J)Ljava/util/concurrent/ThreadFactory;",
                        Optional.of(new CodeAttribute(
                            4,
                            3,
                            new byte[0],
                            0,
                            List.of(
                                instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                                instruction(1, 42, "aload_0"),
                                instruction(2, 31, "lload_1"),
                                instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                                instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                                instruction(5, 176, "areturn")
                            )
                        ))
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.reachableMethods()).contains(helper);
        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualStartWithRunnableParameterAlias() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "(Ljava/lang/Runnable;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "(Ljava/lang/Runnable;)V",
                        instruction(0, 42, "aload_0"),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 43, "aload_1"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 75, "astore_0"),
                        instruction(6, 42, "aload_0"),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualFactoryNewThreadWithoutDup() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 75, "astore_0"),
                        instruction(6, 42, "aload_0"),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualNamedFactoryAliasSlotMismatch() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(4, 76, "astore_1"),
                        instruction(5, 44, "aload_2"),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 75, "astore_0"),
                        instruction(11, 42, "aload_0"),
                        instruction(12, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(13, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualFactoryReceiverWithoutStore() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 43, "aload_1"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 75, "astore_0"),
                        instruction(8, 42, "aload_0"),
                        instruction(9, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(10, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualFactoryReceiverWithoutLoad() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 3, "iconst_0"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 75, "astore_0"),
                        instruction(8, 42, "aload_0"),
                        instruction(9, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(10, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForThreadOfVirtualFactoryNewWithoutClassMetadata() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 187, "new"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 75, "astore_0"),
                        instruction(7, 42, "aload_0"),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(9, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilitySkipsThreadOfVirtualFactoryThreadSubclassTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint run = new EntryPoint("com/acme/WorkerThread", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(2, 187, "new", "com/acme/WorkerThread"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/WorkerThread", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 75, "astore_0"),
                        instruction(7, 42, "aload_0"),
                        instruction(8, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(9, 177, "return")
                    )
                ),
                "com/acme/WorkerThread", classWithMethods(
                    "com/acme/WorkerThread",
                    "java/lang/Thread",
                    0,
                    List.of(),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).doesNotContain(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForDirectThreadFactoryNewThreadWithoutStore() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "(Ljava/util/concurrent/ThreadFactory;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "(Ljava/util/concurrent/ThreadFactory;)V",
                        instruction(0, 42, "aload_0"),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 75, "astore_0"),
                        instruction(6, 42, "aload_0"),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityFallsBackToRunnableTargetsForDirectThreadFactoryNewThreadWithNonConstructorInvoke() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "(Ljava/util/concurrent/ThreadFactory;)V");
        final EntryPoint run = new EntryPoint("com/acme/Task", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "(Ljava/util/concurrent/ThreadFactory;)V",
                        instruction(0, 42, "aload_0"),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 182, "invokevirtual", new MethodRef("com/acme/Task", "run", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 75, "astore_0"),
                        instruction(6, 42, "aload_0"),
                        instruction(7, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(8, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).contains(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilitySkipsThreadOfVirtualBuilderThreadSubclassTaskEdge() {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final EntryPoint run = new EntryPoint("com/acme/WorkerThread", "run", "()V");
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(1, 187, "new", "com/acme/WorkerThread"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/WorkerThread", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ),
                "com/acme/WorkerThread", classWithMethods(
                    "com/acme/WorkerThread",
                    "java/lang/Thread",
                    0,
                    List.of(),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(main)
        );

        assertThat(graph.callEdges()).doesNotContain(new CallEdge(main, run, CallEdge.Kind.THREAD_START_TASK));
    }

    @Test
    void reachabilityKeepsOnlyConstructedRunnableTargetsForThreadStart() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "([Ljava/lang/String;)V",
                        classInstruction(0, 187, "new", "java/lang/Thread"),
                        instruction(1, 89, "dup"),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 183, "invokespecial", new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                        instruction(6, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(7, 177, "return")
                    )
                ),
                "com/acme/Task", classWithMethods(
                    "com/acme/Task",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                ),
                "com/acme/OtherTask", classWithMethods(
                    "com/acme/OtherTask",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/Task", "run", "()V"));
        assertThat(graph.reachableMethods()).doesNotContain(new EntryPoint("com/acme/OtherTask", "run", "()V"));
    }

    @Test
    void reachabilityFallsBackToAllRunnableTargetsWhenThreadRunnableTargetIsUnknown() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "(Ljava/lang/Runnable;)V",
                        classInstruction(0, 187, "new", "java/lang/Thread"),
                        instruction(1, 89, "dup"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 183, "invokespecial", new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                        instruction(4, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(5, 177, "return")
                    )
                ),
                "com/acme/TaskA", classWithMethods(
                    "com/acme/TaskA",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("run", "()V")
                ),
                "com/acme/TaskB", classWithMethods(
                    "com/acme/TaskB",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "(Ljava/lang/Runnable;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(
            new EntryPoint("com/acme/TaskA", "run", "()V"),
            new EntryPoint("com/acme/TaskB", "run", "()V")
        );
    }

    @Test
    void reachabilityFallsBackToAllRunnableTargetsWhenThreadRunnableTargetReloadsFromLocal() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "()V",
                        classInstruction(0, 187, "new", "com/acme/TaskA"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/TaskA", "<init>", "()V")),
                        instruction(3, 75, "astore_0"),
                        classInstruction(4, 187, "new", "com/acme/TaskB"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/TaskB", "<init>", "()V")),
                        instruction(7, 87, "pop"),
                        classInstruction(8, 187, "new", "java/lang/Thread"),
                        instruction(9, 89, "dup"),
                        instruction(10, 42, "aload_0"),
                        instruction(11, 183, "invokespecial", new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                        instruction(12, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(13, 177, "return")
                    )
                ),
                "com/acme/TaskA", classWithMethods(
                    "com/acme/TaskA",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                ),
                "com/acme/TaskB", classWithMethods(
                    "com/acme/TaskB",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("<init>", "()V"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "()V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(
            new EntryPoint("com/acme/TaskA", "run", "()V"),
            new EntryPoint("com/acme/TaskB", "run", "()V")
        );
    }

    @Test
    void reachabilityFallbackSkipsAbstractRunnableTargetsWithoutCode() {
        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(
                "com/acme/Main", classWithMethods(
                    "com/acme/Main",
                    "java/lang/Object",
                    0,
                    List.of(),
                    methodInfo(
                        "main",
                        "(Ljava/lang/Runnable;)V",
                        classInstruction(0, 187, "new", "java/lang/Thread"),
                        instruction(1, 89, "dup"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 183, "invokespecial", new MethodRef("java/lang/Thread", "<init>", "(Ljava/lang/Runnable;)V")),
                        instruction(4, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(5, 177, "return")
                    )
                ),
                "com/acme/AbstractTask", classWithMethods(
                    "com/acme/AbstractTask",
                    "java/lang/Object",
                    0x0400,
                    List.of("java/lang/Runnable"),
                    new MethodInfo(0x0401, "run", "()V", Optional.empty())
                ),
                "com/acme/TaskB", classWithMethods(
                    "com/acme/TaskB",
                    "java/lang/Object",
                    0,
                    List.of("java/lang/Runnable"),
                    methodInfo("run", "()V")
                )
            ),
            List.of(new EntryPoint("com/acme/Main", "main", "(Ljava/lang/Runnable;)V"))
        );

        assertThat(graph.diagnostics()).isEmpty();
        assertThat(graph.reachableMethods()).contains(new EntryPoint("com/acme/TaskB", "run", "()V"));
        assertThat(graph.reachableMethods()).doesNotContain(new EntryPoint("com/acme/AbstractTask", "run", "()V"));
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
    void staticVerifierIgnoresUnreachableSubstitutedProcessRunnerRunBody() {
        final ClassFile processRunner = classWithMethods(
            "javan/util/ProcessRunner",
            "java/lang/Object",
            0,
            List.of(),
            processRunnerRunFallbackMethod()
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(processRunner.name(), processRunner), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierStillRejectsReachableSubstitutedProcessRunnerRunBody() {
        final ClassFile processRunner = classWithMethods(
            "javan/util/ProcessRunner",
            "java/lang/Object",
            0,
            List.of(),
            processRunnerRunFallbackMethod()
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(processRunner.name(), processRunner),
            List.of(new EntryPoint(
                "javan/util/ProcessRunner",
                "run",
                "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;"
            ))
        );

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014", "JAVAN031", "JAVAN031", "JAVAN031");
    }

    @Test
    void staticVerifierIgnoresUnreachableHostOnlyClassFileReaderInputStreamOverload() {
        final ClassFile reader = classWithMethods(
            "javan/classfile/ClassFileReader",
            "java/lang/Object",
            0,
            List.of(),
            hostOnlyInputStreamReadMethod("(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/classfile/ClassFile;")
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(reader.name(), reader), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierIgnoresUnreachableHostOnlyClassMetadataReaderInputStreamOverload() {
        final ClassFile reader = classWithMethods(
            "javan/compat/ClassMetadataReader",
            "java/lang/Object",
            0,
            List.of(),
            hostOnlyInputStreamReadMethod("(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/compat/ClassMetadata;")
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(reader.name(), reader), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableHostOnlyInputStreamOverload() {
        final String descriptor = "(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/classfile/ClassFile;";
        final ClassFile reader = classWithMethods(
            "javan/classfile/ClassFileReader",
            "java/lang/Object",
            0,
            List.of(),
            hostOnlyInputStreamReadMethod(descriptor)
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(reader.name(), reader),
            List.of(new EntryPoint(reader.name(), "read", descriptor))
        );

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN031");
    }

    @Test
    void staticVerifierIgnoresUnreachableHostOnlyJavanHomePropertiesHelper() {
        final ClassFile javanHome = classWithMethods(
            "javan/toolchain/JavanHome",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo("property", "(Ljava/util/Properties;)Ljava/lang/String;", instruction(
                0,
                182,
                "invokevirtual",
                new MethodRef("java/util/Properties", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")
            ))
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(javanHome.name(), javanHome), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableHostOnlyJavanHomePropertiesHelper() {
        final String descriptor = "(Ljava/util/Properties;)Ljava/lang/String;";
        final ClassFile javanHome = classWithMethods(
            "javan/toolchain/JavanHome",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo("property", descriptor, instruction(
                0,
                182,
                "invokevirtual",
                new MethodRef("java/util/Properties", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")
            ))
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(javanHome.name(), javanHome),
            List.of(new EntryPoint(javanHome.name(), "property", descriptor))
        );

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN031");
    }

    @Test
    void staticVerifierIgnoresUnreachableHostOnlyToolchainMetadataCauseConstructor() {
        final ClassFile exception = classWithMethods(
            "javan/toolchain/ToolchainMetadataException",
            "java/lang/RuntimeException",
            0,
            List.of(),
            methodInfo("<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", instruction(
                0,
                183,
                "invokespecial",
                new MethodRef("java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V")
            ))
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(exception.name(), exception), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableHostOnlyToolchainMetadataCauseConstructor() {
        final String descriptor = "(Ljava/lang/String;Ljava/lang/Throwable;)V";
        final ClassFile exception = classWithMethods(
            "javan/toolchain/ToolchainMetadataException",
            "java/lang/RuntimeException",
            0,
            List.of(),
            methodInfo("<init>", descriptor, instruction(
                0,
                183,
                "invokespecial",
                new MethodRef("java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V")
            ))
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(exception.name(), exception),
            List.of(new EntryPoint(exception.name(), "<init>", descriptor))
        );

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN031");
    }

    @Test
    void staticVerifierIgnoresUnreachableHostOnlyCliRunFacade() {
        final String descriptor = "(Ljava/nio/file/Path;Ljava/io/PrintStream;Ljava/io/PrintStream;[Ljava/lang/String;)I";
        final ClassFile cli = classWithMethods(
            "javan/cli/Cli",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0,
                "run",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    5,
                    new byte[0],
                    1,
                    List.of(new CodeException(0, 4, 4, Optional.of("java/lang/InterruptedException"))),
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "interrupt", "()V")),
                        instruction(2, 5, "iconst_2"),
                        instruction(3, 172, "ireturn"),
                        instruction(4, 6, "iconst_3")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(cli.name(), cli), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableHostOnlyCliRunFacade() {
        final String descriptor = "(Ljava/nio/file/Path;Ljava/io/PrintStream;Ljava/io/PrintStream;[Ljava/lang/String;)I";
        final ClassFile cli = classWithMethods(
            "javan/cli/Cli",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0,
                "run",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    5,
                    new byte[0],
                    1,
                    List.of(new CodeException(0, 4, 4, Optional.of("java/lang/InterruptedException"))),
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "interrupt", "()V")),
                        instruction(2, 5, "iconst_2"),
                        instruction(3, 172, "ireturn"),
                        instruction(4, 6, "iconst_3")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(cli.name(), cli),
            List.of(new EntryPoint(cli.name(), "run", descriptor))
        );

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN014");
    }

    @Test
    void staticVerifierRejectsReachableCurrentThreadStart() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN075");
            assertThat(diagnostic.subject()).isEqualTo("Thread.currentThread().start()");
        });
    }

    @Test
    void staticVerifierWarnsAboutUnreachableCurrentThreadStartInApplicationCode() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(main.name(), main), List.of());

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN175");
            assertThat(diagnostic.subject()).isEqualTo("Thread.currentThread().start()");
        });
    }

    @Test
    void staticVerifierRejectsReachableCurrentThreadJoin() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN075");
            assertThat(diagnostic.subject()).isEqualTo("Thread.currentThread().join()");
        });
    }

    @Test
    void staticVerifierWarnsAboutUnreachableCurrentThreadJoinInApplicationCode() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(main.name(), main), List.of());

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN175");
            assertThat(diagnostic.subject()).isEqualTo("Thread.currentThread().join()");
        });
    }

    @Test
    void staticVerifierRejectsReachableDuplicateThreadStartOnSameLocal() {
        final String descriptor = "(Ljava/lang/Thread;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "startTwice",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "startTwice", descriptor))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN075");
            assertThat(diagnostic.subject()).isEqualTo("duplicate Thread.start() on local 0");
        });
    }

    @Test
    void staticVerifierWarnsAboutUnreachableDuplicateThreadStartOnSameLocal() {
        final String descriptor = "(Ljava/lang/Thread;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "startTwice",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(main.name(), main), List.of());

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN175");
            assertThat(diagnostic.subject()).isEqualTo("duplicate Thread.start() on local 0");
        });
    }

    @Test
    void staticVerifierWarnsAboutReachableThreadSleepBlockingWait() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 9, "lconst_0"),
                        instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN178");
            assertThat(diagnostic.subject()).isEqualTo("Thread.sleep(long)");
        });
    }

    @Test
    void staticVerifierWarnsAboutReachableThreadJoinBlockingWait() {
        final String descriptor = "(Ljava/lang/Thread;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "joinWorker",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "joinWorker", descriptor))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN178");
            assertThat(diagnostic.subject()).isEqualTo("Thread.join()");
        });
    }

    @Test
    void staticVerifierAcceptsBranchExclusiveThreadStartOnSameLocal() {
        final String descriptor = "(Ljava/lang/Thread;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "startOncePerBranch",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 3, "iconst_0"),
                        instruction(1, 153, "ifeq"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(4, 167, "goto"),
                        instruction(5, 42, "aload_0"),
                        instruction(6, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "startOncePerBranch", descriptor))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsBackToBackThreadStartsOnDifferentLocals() {
        final String descriptor = "(Ljava/lang/Thread;Ljava/lang/Thread;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "startDifferentThreads",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(2, 43, "aload_1"),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "startDifferentThreads", descriptor))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsCurrentThreadStoredBeforeStart() {
        final String descriptor = "()V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 58, "astore"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", descriptor))
        );

        assertThat(diagnostics).isEmpty();
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
    void staticVerifierIgnoresUnreachableJavacRecordObjectMethods() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod("java/lang/Record", "toString", "()Ljava/lang/String;", false);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierIgnoresUnreachableJavacRecordHashCodeMethod() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod("java/lang/Record", "hashCode", "()I", false);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierIgnoresUnreachableJavacRecordEqualsMethod() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod("java/lang/Record", "equals", "(Ljava/lang/Object;)Z", false);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableJavacRecordObjectMethods() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod("java/lang/Record", "toString", "()Ljava/lang/String;", true);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN030");
    }

    @Test
    void staticVerifierWarnsForUnreachableObjectMethodsBootstrapOutsideRecords() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod("java/lang/Object", "toString", "()Ljava/lang/String;", false);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN130");
    }

    @Test
    void staticVerifierWarnsForUnreachableObjectMethodsBootstrapOnNonObjectRecordMethod() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod("java/lang/Record", "describe", "()Ljava/lang/String;", false);

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN130");
    }

    @Test
    void staticVerifierWarnsForUnreachableRecordObjectMethodWithoutDynamicMetadata() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod(
            "java/lang/Record",
            "toString",
            "()Ljava/lang/String;",
            Optional.empty(),
            false
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN130");
    }

    @Test
    void staticVerifierWarnsForUnreachableRecordObjectMethodWithDifferentBootstrapOwner() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod(
            "java/lang/Record",
            "toString",
            "()Ljava/lang/String;",
            Optional.of(new DynamicRef("toString", "()Ljava/lang/String;", "other/Factory", "bootstrap", "()V", List.of())),
            false
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN130");
    }

    @Test
    void staticVerifierWarnsForUnreachableRecordObjectMethodWithDifferentBootstrapName() {
        final List<Diagnostic> diagnostics = verifyRecordObjectMethod(
            "java/lang/Record",
            "toString",
            "()Ljava/lang/String;",
            Optional.of(new DynamicRef("toString", "()Ljava/lang/String;", "java/lang/runtime/ObjectMethods", "other", "()V", List.of())),
            false
        );

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().code()).isEqualTo("JAVAN130");
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringLengthCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "length", "()I"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 182, "invokevirtual", new MethodRef("java/lang/String", "length", "()I")),
            instruction(2, 87, "pop"),
            instruction(3, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.length()I");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringCharAtCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "charAt", "(I)C"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 3, "iconst_0"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/lang/String", "charAt", "(I)C")),
            instruction(3, 87, "pop"),
            instruction(4, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.charAt(I)C");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringSubstringStartCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 3, "iconst_0"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;")),
            instruction(3, 87, "pop"),
            instruction(4, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.substring(I)Ljava/lang/String;");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringSubstringRangeCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "substring", "(II)Ljava/lang/String;"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 3, "iconst_0"),
            instruction(2, 4, "iconst_1"),
            instruction(3, 182, "invokevirtual", new MethodRef("java/lang/String", "substring", "(II)Ljava/lang/String;")),
            instruction(4, 87, "pop"),
            instruction(5, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.substring(II)Ljava/lang/String;");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringIndexOfCharCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "indexOf", "(I)I"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 3, "iconst_0"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/lang/String", "indexOf", "(I)I")),
            instruction(3, 87, "pop"),
            instruction(4, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.indexOf(I)I");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringIndexOfCharFromIndexCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "indexOf", "(II)I"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 3, "iconst_0"),
            instruction(2, 4, "iconst_1"),
            instruction(3, 182, "invokevirtual", new MethodRef("java/lang/String", "indexOf", "(II)I")),
            instruction(4, 87, "pop"),
            instruction(5, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.indexOf(II)I");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringIndexOfSubstringCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;)I"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            stringInstruction(1, "a"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;)I")),
            instruction(3, 87, "pop"),
            instruction(4, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.indexOf(Ljava/lang/String;)I");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringIndexOfSubstringFromIndexCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;I)I"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            stringInstruction(1, "a"),
            instruction(2, 3, "iconst_0"),
            instruction(3, 182, "invokevirtual", new MethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;I)I")),
            instruction(4, 87, "pop"),
            instruction(5, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.indexOf(Ljava/lang/String;I)I");
        });
    }

    @Test
    void staticVerifierRejectsReachableNonAsciiStringLastIndexOfCall() {
        final List<Diagnostic> diagnostics = verifyStringSemanticMethod(
            new MethodRef("java/lang/String", "lastIndexOf", "(I)I"),
            true,
            stringInstruction(0, "cafe\u00e9"),
            instruction(1, 3, "iconst_0"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/lang/String", "lastIndexOf", "(I)I")),
            instruction(3, 87, "pop"),
            instruction(4, 177, "return")
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN046");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/String.lastIndexOf(I)I");
        });
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
    void staticVerifierAcceptsExplicitThrowRangeWithFinallyRethrowHandler() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            classInstruction(0, 187, "new", "java/lang/IllegalStateException"),
            instruction(1, 89, "dup"),
            instruction(2, 18, "ldc"),
            instruction(3, 183, "invokespecial", new MethodRef("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V")),
            instruction(4, 191, "athrow"),
            instruction(5, 75, "astore_0"),
            instruction(6, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(7, 18, "ldc"),
            instruction(8, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(9, 42, "aload_0"),
            instruction(10, 191, "athrow")
        ), new CodeException(0, 5, 5, Optional.empty()));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsTypedCatchWrappingFinallyRethrowHandler() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            classInstruction(0, 187, "new", "java/lang/IllegalStateException"),
            instruction(3, 89, "dup"),
            instruction(4, 18, "ldc"),
            instruction(6, 183, "invokespecial", new MethodRef("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V")),
            instruction(9, 191, "athrow"),
            instruction(10, 76, "astore_1"),
            instruction(11, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(14, 18, "ldc"),
            instruction(16, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(19, 43, "aload_1"),
            instruction(20, 191, "athrow"),
            instruction(21, 76, "astore_1"),
            instruction(22, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(25, 43, "aload_1"),
            instruction(26, 182, "invokevirtual", new MethodRef("java/lang/IllegalStateException", "getMessage", "()Ljava/lang/String;")),
            instruction(29, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(32, 177, "return")
        ), List.of(
            new CodeException(0, 11, 10, Optional.empty()),
            new CodeException(0, 21, 21, Optional.of("java/lang/IllegalStateException"))
        ));

        assertThat(diagnostics).isEmpty();
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
    void staticVerifierAcceptsInterruptedSleepHandlerShape() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 18, "ldc"),
            instruction(4, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(5, 75, "astore_0")
        ), new CodeException(0, 5, 5, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedSleepHandlerCaughtAsException() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/Exception")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedSleepHandlerWithLongLoad() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 30, "lload_0"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedSleepHandlerWithLongConstOpcode() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 10, "lconst_1"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedJoinHandlerShape() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 18, "ldc"),
            instruction(4, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(5, 75, "astore_0")
        ), new CodeException(0, 5, 5, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedJoinHandlerWithCurrentThreadAndBooleanPrintln() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "()V")),
            instruction(2, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
            instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Thread", "isInterrupted", "()Z")),
            instruction(4, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(5, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Z)V")),
            instruction(6, 75, "astore_0")
        ), new CodeException(0, 6, 6, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedObjectWaitHandlerShape() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Object", "wait", "()V")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN076");
    }

    @Test
    void staticVerifierAcceptsInterruptedTimedObjectWaitHandlerShape() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 10, "lconst_1"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/lang/Object", "wait", "(J)V")),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN076");
    }

    @Test
    void staticVerifierAcceptsInterruptedHandlerWithSystemErr() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "err", "Ljava/io/PrintStream;")),
            instruction(3, 18, "ldc"),
            instruction(4, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(5, 75, "astore_0")
        ), new CodeException(0, 5, 5, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedHandlerWithObjectPrintln() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 42, "aload_0"),
            instruction(4, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")),
            instruction(5, 75, "astore_0")
        ), new CodeException(0, 5, 5, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedHandlerWithLongPrintln() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 10, "lconst_1"),
            instruction(4, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(J)V")),
            instruction(5, 75, "astore_0")
        ), new CodeException(0, 5, 5, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierAcceptsInterruptedHandlerWithIntPrintln() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 4, "iconst_1"),
            instruction(4, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(I)V")),
            instruction(5, 75, "astore_0")
        ), new CodeException(0, 5, 5, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).containsExactly("JAVAN178");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithoutWaitCall() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(1, 18, "ldc"),
            instruction(2, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsReachableNotifyAllAsSynchronization() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "java/lang/Object"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("java/lang/Object", "<init>", "()V")),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/Object", "notifyAll", "()V")),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN076");
            assertThat(diagnostic.subject()).isEqualTo("Object.notifyAll()");
        });
    }

    @Test
    void staticVerifierAcceptsUnreachableThreadLocalConstructor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "java/lang/ThreadLocal"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("java/lang/ThreadLocal", "<init>", "()V")),
                        instruction(3, 87, "pop"),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(main.name(), main), List.of());

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierWarnsAboutUnreachableInheritableThreadLocalConstructor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "java/lang/InheritableThreadLocal"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("java/lang/InheritableThreadLocal", "<init>", "()V")),
                        instruction(3, 87, "pop"),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(main.name(), main), List.of());

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN177");
            assertThat(diagnostic.subject()).isEqualTo("InheritableThreadLocal.<init>()");
        });
    }

    @Test
    void staticVerifierWarnsAboutUnreachableUnsupportedThreadLocalGetDescriptor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "java/lang/ThreadLocal"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("java/lang/ThreadLocal", "<init>", "()V")),
                        instruction(3, 182, "invokevirtual", new MethodRef("java/lang/ThreadLocal", "get", "()I")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(main.name(), main), List.of());

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN177");
            assertThat(diagnostic.subject()).isEqualTo("java/lang/ThreadLocal.get()I");
        });
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderStartViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderNameStart() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualStartViaStaticBuilderHelper() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo(
                "main",
                "()V",
                instruction(0, 184, "invokestatic", new MethodRef("com/acme/Main", "builder", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                classInstruction(1, 187, "new", "com/acme/Task"),
                instruction(2, 89, "dup"),
                instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                instruction(5, 87, "pop"),
                instruction(6, 177, "return")
            ),
            new MethodInfo(
                0x0008,
                "builder",
                "()Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 176, "areturn")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"), new EntryPoint(main.name(), "builder", "()Ljava/lang/Thread$Builder$OfVirtual;"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryViaStaticHelper() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo(
                "main",
                "()V",
                instruction(0, 184, "invokestatic", new MethodRef("com/acme/Main", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                classInstruction(1, 187, "new", "com/acme/Task"),
                instruction(2, 89, "dup"),
                instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                instruction(5, 87, "pop"),
                instruction(6, 177, "return")
            ),
            new MethodInfo(
                0x0008,
                "factory",
                "()Ljava/util/concurrent/ThreadFactory;",
                Optional.of(new CodeAttribute(
                    2,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 176, "areturn")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(
                new EntryPoint(main.name(), "main", "()V"),
                new EntryPoint(main.name(), "factory", "()Ljava/util/concurrent/ThreadFactory;")
            )
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderNameStartViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 43, "aload_1"),
                        classInstruction(5, 187, "new", "com/acme/Task"),
                        instruction(6, 89, "dup"),
                        instruction(7, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(9, 87, "pop"),
                        instruction(10, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderNameCounterStart() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 10, "lconst_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableGenericThreadBuilderNameCounterStartViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 10, "lconst_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;")),
                        instruction(4, 76, "astore_1"),
                        instruction(5, 43, "aload_1"),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 87, "pop"),
                        instruction(11, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualDiscardedNameMutationStartViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        instruction(3, 18, "ldc"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 43, "aload_1"),
                        classInstruction(7, 187, "new", "com/acme/Task"),
                        instruction(8, 89, "dup"),
                        instruction(9, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(10, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(11, 87, "pop"),
                        instruction(12, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualDiscardedNameCounterMutationUnstartedViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        instruction(3, 18, "ldc"),
                        instruction(4, 10, "lconst_1"),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(6, 87, "pop"),
                        instruction(7, 43, "aload_1"),
                        classInstruction(8, 187, "new", "com/acme/Task"),
                        instruction(9, 89, "dup"),
                        instruction(10, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(11, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(12, 87, "pop"),
                        instruction(13, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderUnstarted() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderUnstartedViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderNameUnstartedViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 43, "aload_1"),
                        classInstruction(5, 187, "new", "com/acme/Task"),
                        instruction(6, 89, "dup"),
                        instruction(7, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(9, 87, "pop"),
                        instruction(10, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderStartViaLocalAliasSlotZero() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderStartViaLocalAliasSlotTwo() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 77, "astore_2"),
                        instruction(2, 44, "aload_2"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderStartViaLocalAliasSlotThree() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    4,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 78, "astore_3"),
                        instruction(2, 45, "aload_3"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderStartViaGenericLocalAliasSlot() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    6,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instructionOperands(1, 58, "astore", 5),
                        instructionOperands(2, 25, "aload", 5),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableGenericThreadBuilderStartViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableGenericThreadBuilderFactoryNewThreadViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 43, "aload_1"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThread() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 87, "pop"),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualNamedFactoryNewThread() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadViaWideLocalAliasSlot() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    6,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instructionOperands(2, 58, "astore", 5),
                        instructionOperands(3, 25, "aload", 5),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadViaLocalAliasSlotZero() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadViaLocalAliasSlotTwo() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 77, "astore_2"),
                        instruction(3, 44, "aload_2"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualNamedFactoryNewThreadViaLocalAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(4, 76, "astore_1"),
                        instruction(5, 43, "aload_1"),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 87, "pop"),
                        instruction(11, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadViaLocalAliasSlotThree() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    4,
                    4,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 78, "astore_3"),
                        instruction(3, 45, "aload_3"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadViaGenericLocalAliasSlot() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    5,
                    5,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instructionOperands(2, 58, "astore", 4),
                        instructionOperands(4, 25, "aload", 4),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 87, "pop"),
                        instruction(11, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableGenericThreadBuilderStartParameter() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Thread$Builder;Ljava/lang/Runnable;)V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 43, "aload_1"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(3, 87, "pop"),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Thread$Builder;Ljava/lang/Runnable;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN077", "Thread.Builder.start(Runnable)"));
    }

    @Test
    void staticVerifierRejectsReachableGenericThreadBuilderUnstartedParameter() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Thread$Builder;Ljava/lang/Runnable;)V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 43, "aload_1"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(3, 87, "pop"),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Thread$Builder;Ljava/lang/Runnable;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN077", "Thread.Builder.unstarted(Runnable)"));
    }

    @Test
    void staticVerifierRejectsReachableGenericThreadBuilderNameParameter() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Thread$Builder;)V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder;")),
                        instruction(3, 87, "pop"),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Thread$Builder;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN077", "Thread.Builder.name(...)"));
    }

    @Test
    void staticVerifierRejectsReachableGenericThreadBuilderFactoryParameter() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Thread$Builder;)V",
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 87, "pop"),
                        instruction(3, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Thread$Builder;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN077", "Thread.Builder.factory()"));
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryAliasSlotMismatch() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 76, "astore_1"),
                        instruction(3, 44, "aload_2"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualStartWithPrebuiltRunnableAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(5, 43, "aload_1"),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualUnstartedWithPrebuiltRunnableAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(5, 43, "aload_1"),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadWithPrebuiltRunnableAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(6, 43, "aload_1"),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualStartWithNestedRunnableAlias() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        classInstruction(0, 187, "new", "com/acme/Task"),
                        instruction(1, 89, "dup"),
                        instruction(2, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(3, 76, "astore_1"),
                        instruction(4, 43, "aload_1"),
                        instruction(5, 77, "astore_2"),
                        instruction(6, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(7, 44, "aload_2"),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(9, 87, "pop"),
                        instruction(10, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableDiscardedThreadOfVirtualBuilderExpression() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo(
                "main",
                "()V",
                instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                instruction(1, 87, "pop"),
                instruction(2, 177, "return")
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableDiscardedThreadOfVirtualNamedBuilderExpression() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo(
                "main",
                "()V",
                instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                instruction(1, 18, "ldc"),
                instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                instruction(3, 87, "pop"),
                instruction(4, 177, "return")
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableDiscardedThreadOfVirtualFactoryExpression() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo(
                "main",
                "()V",
                instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                instruction(2, 87, "pop"),
                instruction(3, 177, "return")
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualStartThroughObjectAliasCheckcast() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        classInstruction(3, 192, "checkcast", "java/lang/Thread$Builder$OfVirtual"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualFactoryNewThreadThroughObjectAliasCheckcast() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        classInstruction(4, 192, "checkcast", "java/util/concurrent/ThreadFactory"),
                        classInstruction(5, 187, "new", "com/acme/Task"),
                        instruction(6, 89, "dup"),
                        instruction(7, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(9, 87, "pop"),
                        instruction(10, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadExecutorFromObjectAliasCheckcast() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        classInstruction(4, 192, "checkcast", "java/util/concurrent/ThreadFactory"),
                        instruction(5, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")),
                        instruction(6, 76, "astore_1"),
                        instruction(7, 43, "aload_1"),
                        classInstruction(8, 187, "new", "com/acme/Task"),
                        instruction(9, 89, "dup"),
                        instruction(10, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(11, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
                        instruction(12, 43, "aload_1"),
                        instruction(13, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "shutdown", "()V")),
                        instruction(14, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualStartThroughObjectAliasCheckcastWithNonLoadReceiver() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 87, "pop"),
                        instruction(2, 3, "iconst_0"),
                        classInstruction(3, 192, "checkcast", "java/lang/Thread$Builder$OfVirtual"),
                        classInstruction(4, 187, "new", "com/acme/Task"),
                        instruction(5, 89, "dup"),
                        instruction(6, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(7, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(8, 87, "pop"),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN077");
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryNewThreadWithRunnableParameter() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Runnable;)V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Runnable;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryNewThreadWithRunnableParameterAlias() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Runnable;)V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(4, 43, "aload_1"),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 87, "pop"),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Runnable;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualStartViaParameterizedBuilderHelper() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            methodInfo(
                "main",
                "()V",
                instruction(0, 18, "ldc"),
                instruction(1, 184, "invokestatic", new MethodRef("com/acme/Main", "builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                classInstruction(2, 187, "new", "com/acme/Task"),
                instruction(3, 89, "dup"),
                instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                instruction(5, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                instruction(6, 87, "pop"),
                instruction(7, 177, "return")
            ),
            new MethodInfo(
                0x0008,
                "builder",
                "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 42, "aload_0"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 176, "areturn")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(
                new EntryPoint(main.name(), "main", "()V"),
                new EntryPoint(main.name(), "builder", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")
            )
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualStartWithRunnableParameterAlias() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "(Ljava/lang/Runnable;)V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 43, "aload_1"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "(Ljava/lang/Runnable;)V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.start(Runnable)")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryNewThreadWithoutDup() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(2, 187, "new", "com/acme/Task"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualNamedFactoryAliasSlotMismatch() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 18, "ldc"),
                        instruction(2, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "name", "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(4, 76, "astore_1"),
                        instruction(5, 44, "aload_2"),
                        classInstruction(6, 187, "new", "com/acme/Task"),
                        instruction(7, 89, "dup"),
                        instruction(8, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(9, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(10, 87, "pop"),
                        instruction(11, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.name(...)"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryReceiverWithoutStore() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 43, "aload_1"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryReceiverWithoutLoad() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 3, "iconst_0"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryNewWithoutClassMetadata() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 187, "new"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 87, "pop"),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualFactoryThreadSubclassTask() {
        final ClassFile task = classWithMethods(
            "com/acme/WorkerThread",
            "java/lang/Thread",
            0,
            List.of(),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    2,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        classInstruction(2, 187, "new", "com/acme/WorkerThread"),
                        instruction(3, 89, "dup"),
                        instruction(4, 183, "invokespecial", new MethodRef("com/acme/WorkerThread", "<init>", "()V")),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "newThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(6, 87, "pop"),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.factory()")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualBuilderAliasSlotMismatch() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 44, "aload_2"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.start(Runnable)")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualBuilderUnstartedAliasSlotMismatch() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    3,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 76, "astore_1"),
                        instruction(2, 44, "aload_2"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "unstarted", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(7, 87, "pop"),
                        instruction(8, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.unstarted(Runnable)")
            );
    }

    @Test
    void staticVerifierRejectsReachableThreadOfVirtualBuilderThreadSubclassTask() {
        final ClassFile task = classWithMethods(
            "com/acme/WorkerThread",
            "java/lang/Thread",
            0,
            List.of(),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        classInstruction(1, 187, "new", "com/acme/WorkerThread"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/WorkerThread", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactlyInAnyOrder(
                tuple("JAVAN077", "Thread.ofVirtual()"),
                tuple("JAVAN077", "Thread.Builder.OfVirtual.start(Runnable)")
            );
    }

    @Test
    void reachabilityTracksVirtualThreadExecutorExecuteTask() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(graph.reachableMethods()).contains(new EntryPoint(task.name(), "run", "()V"));
        assertThat(graph.callEdges()).contains(new CallEdge(
            new EntryPoint(main.name(), "main", "()V"),
            new EntryPoint(task.name(), "run", "()V"),
            CallEdge.Kind.THREAD_START_TASK
        ));
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadExecutorExecute() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        classInstruction(3, 187, "new", "com/acme/Task"),
                        instruction(4, 89, "dup"),
                        instruction(5, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(6, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadExecutorClose() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "close", "()V")),
                        instruction(4, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableLockSupportParkAndUnpark() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 184, "invokestatic", new MethodRef("java/util/concurrent/locks/LockSupport", "unpark", "(Ljava/lang/Thread;)V")),
                        instruction(4, 184, "invokestatic", new MethodRef("java/util/concurrent/locks/LockSupport", "park", "()V")),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN178", "LockSupport.park()"));
    }

    @Test
    void staticVerifierAcceptsReachableLockSupportParkNanos() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 9, "lconst_0"),
                        instruction(1, 184, "invokestatic", new MethodRef("java/util/concurrent/locks/LockSupport", "parkNanos", "(J)V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN178", "LockSupport.parkNanos(long)"));
    }

    @Test
    void staticVerifierAcceptsReachableLockSupportParkUntil() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 9, "lconst_0"),
                        instruction(1, 184, "invokestatic", new MethodRef("java/util/concurrent/locks/LockSupport", "parkUntil", "(J)V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN178", "LockSupport.parkUntil(long)"));
    }

    @Test
    void staticVerifierRejectsReachableLockSupportBlockerOverload() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 1, "aconst_null"),
                        instruction(1, 9, "lconst_0"),
                        instruction(2, 184, "invokestatic", new MethodRef("java/util/concurrent/locks/LockSupport", "parkNanos", "(Ljava/lang/Object;J)V")),
                        instruction(3, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .containsExactly(tuple("JAVAN031", "java/util/concurrent/locks/LockSupport.parkNanos(Ljava/lang/Object;J)V"));
    }

    @Test
    void staticVerifierAcceptsReachableFactoryBackedThreadPerTaskExecutorExecute() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    4,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")),
                        instruction(3, 75, "astore_0"),
                        instruction(4, 42, "aload_0"),
                        classInstruction(5, 187, "new", "com/acme/Task"),
                        instruction(6, 89, "dup"),
                        instruction(7, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(8, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
                        instruction(9, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderToString() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "toString", "()Ljava/lang/String;")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderHashCode() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "hashCode", "()I")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableThreadOfVirtualBuilderEquals() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 42, "aload_0"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "equals", "(Ljava/lang/Object;)Z")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadFactoryToString() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "toString", "()Ljava/lang/String;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadFactoryHashCode() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "hashCode", "()I")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadFactoryEquals() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        instruction(4, 42, "aload_0"),
                        instruction(5, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "equals", "(Ljava/lang/Object;)Z")),
                        instruction(6, 87, "pop"),
                        instruction(7, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadExecutorToString() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "toString", "()Ljava/lang/String;")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadExecutorHashCode() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "hashCode", "()I")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierAcceptsReachableVirtualThreadExecutorEquals() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 42, "aload_0"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "equals", "(Ljava/lang/Object;)Z")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void staticVerifierRejectsMalformedVirtualThreadBuilderToStringDescriptor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "toString", "()I")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .contains(tuple("JAVAN077", "java/lang/Thread$Builder$OfVirtual.toString()I"));
    }

    @Test
    void staticVerifierRejectsMalformedVirtualThreadBuilderHashCodeDescriptor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "hashCode", "()Ljava/lang/String;")),
                        instruction(4, 87, "pop"),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .contains(tuple("JAVAN077", "java/lang/Thread$Builder$OfVirtual.hashCode()Ljava/lang/String;"));
    }

    @Test
    void staticVerifierRejectsMalformedVirtualThreadBuilderEqualsDescriptor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 75, "astore_0"),
                        instruction(2, 42, "aload_0"),
                        instruction(3, 1, "aconst_null"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "equals", "(Ljava/lang/Object;)Ljava/lang/String;")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .contains(tuple("JAVAN077", "java/lang/Thread$Builder$OfVirtual.equals(Ljava/lang/Object;)Ljava/lang/String;"));
    }

    @Test
    void staticVerifierRejectsMalformedVirtualThreadFactoryToStringDescriptor() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "ofVirtual", "()Ljava/lang/Thread$Builder$OfVirtual;")),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "factory", "()Ljava/util/concurrent/ThreadFactory;")),
                        instruction(2, 75, "astore_0"),
                        instruction(3, 42, "aload_0"),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ThreadFactory", "toString", "()I")),
                        instruction(5, 87, "pop"),
                        instruction(6, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics)
            .extracting(Diagnostic::code, Diagnostic::subject)
            .contains(tuple("JAVAN031", "java/util/concurrent/ThreadFactory.toString()I"));
    }

    @Test
    void reachabilityConservativelyTracksExecutorExecuteTaskForUnknownExecutorReceiver() {
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );
        final String descriptor = "(Ljava/util/concurrent/ExecutorService;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                descriptor,
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );

        final CallGraph graph = new ReachabilityAnalyzer().analyze(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", descriptor))
        );

        assertThat(graph.reachableMethods()).contains(new EntryPoint(task.name(), "run", "()V"));
    }

    @Test
    void staticVerifierRejectsReachableExecutorExecuteWithUnknownReceiver() {
        final String descriptor = "(Ljava/util/concurrent/ExecutorService;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                descriptor,
                Optional.of(new CodeAttribute(
                    3,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        classInstruction(1, 187, "new", "com/acme/Task"),
                        instruction(2, 89, "dup"),
                        instruction(3, 183, "invokespecial", new MethodRef("com/acme/Task", "<init>", "()V")),
                        instruction(4, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "execute", "(Ljava/lang/Runnable;)V")),
                        instruction(5, 177, "return")
                    )
                ))
            )
        );
        final ClassFile task = classWithMethods(
            "com/acme/Task",
            "java/lang/Object",
            0,
            List.of("java/lang/Runnable"),
            methodInfo("<init>", "()V"),
            methodInfo("run", "()V")
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main, task.name(), task),
            List.of(new EntryPoint(main.name(), "main", descriptor))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN077");
            assertThat(diagnostic.subject()).isEqualTo("Executor.execute(Runnable)");
        });
    }

    @Test
    void staticVerifierRejectsReachableThreadPerTaskExecutorWithUnknownFactory() {
        final String descriptor = "(Ljava/util/concurrent/ThreadFactory;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                descriptor,
                Optional.of(new CodeAttribute(
                    2,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newThreadPerTaskExecutor", "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;")),
                        instruction(2, 87, "pop"),
                        instruction(3, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", descriptor))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN077");
            assertThat(diagnostic.subject()).isEqualTo("Executors.newThreadPerTaskExecutor(ThreadFactory)");
        });
    }

    @Test
    void staticVerifierRejectsReachableExecutorServiceCloseWithUnknownReceiver() {
        final String descriptor = "(Ljava/util/concurrent/ExecutorService;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "close", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", descriptor))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN077");
            assertThat(diagnostic.subject()).isEqualTo("ExecutorService.close()");
        });
    }

    @Test
    void staticVerifierRejectsReachableExecutorsFactoryAsConcurrencyRuntimeApi() {
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                "()V",
                Optional.of(new CodeAttribute(
                    1,
                    0,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/util/concurrent/Executors", "newSingleThreadExecutor", "()Ljava/util/concurrent/ExecutorService;")),
                        instruction(1, 87, "pop"),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", "()V"))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN077");
            assertThat(diagnostic.subject()).isEqualTo("Executors.newSingleThreadExecutor()");
        });
    }

    @Test
    void staticVerifierRejectsReachableExecutorServiceMethodAsConcurrencyRuntimeApi() {
        final String descriptor = "(Ljava/util/concurrent/ExecutorService;)V";
        final ClassFile main = classWithMethods(
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            new MethodInfo(
                0x0008,
                "main",
                descriptor,
                Optional.of(new CodeAttribute(
                    1,
                    1,
                    new byte[0],
                    0,
                    List.of(
                        instruction(0, 42, "aload_0"),
                        instruction(1, 185, "invokeinterface", new MethodRef("java/util/concurrent/ExecutorService", "shutdown", "()V")),
                        instruction(2, 177, "return")
                    )
                ))
            )
        );

        final List<Diagnostic> diagnostics = new StaticVerifier().verify(
            Map.of(main.name(), main),
            List.of(new EntryPoint(main.name(), "main", descriptor))
        );

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("JAVAN077");
            assertThat(diagnostic.subject()).isEqualTo("ExecutorService.shutdown()");
        });
    }

    @Test
    void staticVerifierRejectsMalformedSynchronizedMonitorHandler() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 194, "monitorenter"),
            instruction(2, 42, "aload_0"),
            instruction(3, 195, "monitorexit"),
            instruction(4, 177, "return"),
            instruction(5, 76, "astore_1")
        ), new CodeException(0, 4, 5, Optional.empty()));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN076", "JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithUnsupportedPrintlnDescriptor() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 182, "invokevirtual", new MethodRef("java/io/PrintStream", "println", "(F)V")),
            instruction(4, 75, "astore_0")
        ), new CodeException(0, 4, 4, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithMissingFieldRef() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic"),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithNonSystemFieldOwner() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("com/acme/Console", "out", "Ljava/io/PrintStream;")),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithWrongSystemField() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "in", "Ljava/io/InputStream;")),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithUnsupportedStaticMethod() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 184, "invokestatic", new MethodRef("java/lang/System", "currentTimeMillis", "()J")),
            instruction(3, 75, "astore_0")
        ), new CodeException(0, 3, 3, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithWrongSleepDescriptor() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 4, "iconst_1"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(I)V")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithWrongJoinDescriptor() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "join", "(J)V")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithMissingPrintlnMethodRef() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 20, "ldc2_w"),
            instruction(1, 184, "invokestatic", new MethodRef("java/lang/Thread", "sleep", "(J)V")),
            instruction(2, 178, "getstatic", new FieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")),
            instruction(3, 182, "invokevirtual"),
            instruction(4, 75, "astore_0")
        ), new CodeException(0, 4, 4, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
    }

    @Test
    void staticVerifierRejectsInterruptedHandlerWithUnsupportedProtectedCall() {
        final List<Diagnostic> diagnostics = verifyExceptionTable(List.of(
            instruction(0, 42, "aload_0"),
            instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Object", "toString", "()Ljava/lang/String;")),
            instruction(2, 75, "astore_0")
        ), new CodeException(0, 2, 2, Optional.of("java/lang/InterruptedException")));

        assertThat(diagnostics).extracting(Diagnostic::code).contains("JAVAN014");
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

    private static List<Diagnostic> verifyRecordObjectMethod(
        final String superName,
        final String methodName,
        final String descriptor,
        final boolean reachable
    ) {
        return verifyRecordObjectMethod(superName, methodName, descriptor, Optional.of(new DynamicRef(
            methodName,
            descriptor,
            "java/lang/runtime/ObjectMethods",
            "bootstrap",
            "()V",
            List.of("field")
        )), reachable);
    }

    private static List<Diagnostic> verifyRecordObjectMethod(
        final String superName,
        final String methodName,
        final String descriptor,
        final Optional<DynamicRef> dynamicRef,
        final boolean reachable
    ) {
        final MethodInfo method = methodInfo(methodName, descriptor, new Instruction(
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
            dynamicRef
        ));
        final ClassFile classFile = classWithMethods("com/acme/Message", superName, 0, List.of(), method);
        final List<EntryPoint> reachableMethods;
        if (reachable) {
            reachableMethods = List.of(new EntryPoint(classFile.name(), methodName, descriptor));
        } else {
            reachableMethods = List.of();
        }
        return new StaticVerifier().verify(Map.of(classFile.name(), classFile), reachableMethods);
    }

    private static List<Diagnostic> verifyStringSemanticMethod(
        final MethodRef target,
        final boolean reachable,
        final Instruction... instructions
    ) {
        final MethodInfo method = new MethodInfo(
            0,
            "main",
            "([Ljava/lang/String;)V",
            Optional.of(new CodeAttribute(4, 1, new byte[0], 0, List.of(instructions)))
        );
        final ClassFile classFile = classWithMethods("com/acme/Main", "java/lang/Object", 0, List.of(), method);
        final List<EntryPoint> reachableMethods = reachable
            ? List.of(new EntryPoint(classFile.name(), method.name(), method.descriptor()))
            : List.of();
        final List<Diagnostic> diagnostics = new StaticVerifier().verify(Map.of(classFile.name(), classFile), reachableMethods);
        assertThat(diagnostics).allMatch(diagnostic -> diagnostic.subject().equals(target.display()));
        return diagnostics;
    }

    private static List<Diagnostic> verifyExceptionTable(final List<Instruction> instructions, final CodeException exception) {
        return verifyExceptionTable(Map.of(), instructions, exception);
    }

    private static List<Diagnostic> verifyExceptionTable(final List<Instruction> instructions, final List<CodeException> exceptions) {
        return verifyExceptionTable(Map.of(), instructions, exceptions);
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
        return verifyExceptionTable(extraClasses, instructions, List.of(exception));
    }

    private static List<Diagnostic> verifyExceptionTable(
        final Map<String, ClassFile> extraClasses,
        final List<Instruction> instructions,
        final List<CodeException> exceptions
    ) {
        final ClassFile classFile = exceptionClassFile("com/acme/Main", instructions, exceptions);
        final Map<String, ClassFile> classes = new java.util.LinkedHashMap<>();
        classes.put(classFile.name(), classFile);
        classes.putAll(extraClasses);
        return new StaticVerifier().verify(
            classes,
            List.of(new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"))
        );
    }

    private static ClassFile exceptionClassFile(final String className, final List<Instruction> instructions, final CodeException exception) {
        return exceptionClassFile(className, instructions, List.of(exception));
    }

    private static ClassFile exceptionClassFile(final String className, final List<Instruction> instructions, final List<CodeException> exceptions) {
        final MethodInfo method = new MethodInfo(
            0,
            "main",
            "([Ljava/lang/String;)V",
            Optional.of(new CodeAttribute(
                2,
                1,
                new byte[]{(byte) 191},
                exceptions.size(),
                exceptions,
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

    private static Instruction instructionOperands(final int offset, final int opcode, final String mnemonic, final int... operands) {
        final byte[] bytes = new byte[operands.length];
        for (int index = 0; index < operands.length; index++) {
            bytes[index] = (byte) operands[index];
        }
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            bytes,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction stringInstruction(final int offset, final String value) {
        return new Instruction(
            offset,
            18,
            "ldc",
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(value),
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

    private static MethodInfo processRunnerRunFallbackMethod() {
        return new MethodInfo(
            0,
            "run",
            "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;",
            Optional.of(new CodeAttribute(
                4,
                5,
                new byte[0],
                1,
                List.of(new CodeException(0, 4, 4, Optional.of("java/io/IOException"))),
                List.of(
                    instruction(0, 184, "invokestatic", new MethodRef(
                        "java/nio/file/Files",
                        "createTempFile",
                        "(Ljava/lang/String;Ljava/lang/String;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"
                    )),
                    instruction(1, 183, "invokespecial", new MethodRef("java/lang/ProcessBuilder", "<init>", "(Ljava/util/List;)V")),
                    instruction(2, 182, "invokevirtual", new MethodRef("java/lang/ProcessBuilder", "start", "()Ljava/lang/Process;")),
                    instruction(3, 176, "areturn"),
                    instruction(4, 176, "areturn")
                )
            ))
        );
    }

    private static MethodInfo hostOnlyInputStreamReadMethod(final String descriptor) {
        return new MethodInfo(
            0,
            "read",
            descriptor,
            Optional.of(new CodeAttribute(
                2,
                3,
                new byte[0],
                0,
                List.of(
                    instruction(0, 42, "aload_0"),
                    instruction(1, 182, "invokevirtual", new MethodRef("java/io/InputStream", "readAllBytes", "()[B")),
                    instruction(2, 176, "areturn")
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
        assertThat(generated).doesNotContain("javan_free(arg0_array);");
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
            "void* arg0_string = 0;",
            "void** javan_export_roots[] = {",
            "(void**) &arg0_string",
            "javan_root_frame_push(javan_export_roots, 1);",
            "arg0_string = javan_string_from(arg0);",
            "void* javan_export_object_result = javan_com_acme_Text_greet__Ljava_lang_String__Ljava_lang_String_(arg0_string);",
            "void** javan_export_result_roots[] = {",
            "(void**) &javan_export_object_result",
            "javan_root_frame_push(javan_export_result_roots, 1);",
            "char* javan_export_result = javan_string_export((const char*) javan_export_object_result);",
            "javan_root_frame_pop(javan_export_result_roots);",
            "javan_root_frame_pop(javan_export_roots);",
            "return javan_export_result;"
        );
        assertThat(generated.indexOf("javan_root_frame_push(javan_export_roots, 1);"))
            .isLessThan(generated.indexOf("arg0_string = javan_string_from(arg0);"));
        assertThat(generated.indexOf("arg0_string = javan_string_from(arg0);"))
            .isLessThan(generated.indexOf("void* javan_export_object_result = javan_com_acme_Text_greet__Ljava_lang_String__Ljava_lang_String_(arg0_string);"));
        assertThat(generated.indexOf("void* javan_export_object_result = javan_com_acme_Text_greet__Ljava_lang_String__Ljava_lang_String_(arg0_string);"))
            .isLessThan(generated.indexOf("javan_root_frame_push(javan_export_result_roots, 1);"));
        assertThat(generated.indexOf("javan_root_frame_push(javan_export_result_roots, 1);"))
            .isLessThan(generated.indexOf("javan_string_export"));
        assertThat(generated.indexOf("javan_string_export"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_export_result_roots);"));
        assertThat(generated.indexOf("javan_root_frame_pop(javan_export_result_roots);"))
            .isLessThan(generated.indexOf("javan_root_frame_pop(javan_export_roots);"));
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
            "javan_println_object_value(javan_expr_tmp_0);",
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
    void cCodegenEmitsThreadRunnableBridgeUsingRunnableDispatch() throws Exception {
        final List<IrParameter> dispatchParameters = List.of(new IrParameter(IrType.OBJECT, "self"));
        final IrProgram program = new IrProgram(
            List.of(new IrClass("com/acme/Task", "javan_class_com_acme_Task", List.of())),
            List.of(
                new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                    IrInstruction.returnVoid()
                )),
                new IrFunction("com/acme/Task", "run", "()V", "task_run", IrType.VOID, dispatchParameters, List.of(), List.of(
                    IrInstruction.returnVoid()
                ))
            ),
            List.of(new IrDispatch(
                "javan_dispatch_java_lang_Runnable_run___V",
                IrType.VOID,
                dispatchParameters,
                List.of(new IrDispatchTarget("com/acme/Task", "task_run"))
            )),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void javan_thread_run_target(void* target);",
            "void javan_thread_run_target(void* target) {",
            "javan_dispatch_java_lang_Runnable_run___V(target);"
        );
    }

    @Test
    void cCodegenEmitsThreadRunnableBridgePanicWhenRunnableDispatchIsAbsent() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                IrInstruction.returnVoid()
            ))),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains(
            "void javan_thread_run_target(void* target);",
            "void javan_thread_run_target(void* target) {",
            "javan_panic(\"Thread.start with Runnable target has no closed-world Runnable.run implementation\")"
        );
    }

    @Test
    void cCodegenEmitsLiveThreadDrainBeforeMainReturn() throws Exception {
        final IrProgram program = new IrProgram(
            List.of(),
            List.of(new IrFunction("com/acme/Main", "main", "([Ljava/lang/String;)V", "main_symbol", IrType.VOID, List.of(), List.of(), List.of(
                IrInstruction.returnVoid()
            ))),
            "main_symbol"
        );

        final String generated = Files.readString(new CCodegen().generate(program, tempDir));

        assertThat(generated).contains("javan_wait_for_non_current_threads();");
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
