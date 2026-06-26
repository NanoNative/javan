package javan.build;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BuildSurfaceTest {
    @TempDir
    private Path tempDir;

    @Test
    void buildKindParsesAliasAndReportsLibraryKinds() {
        assertThat(BuildKind.parse("lib")).contains(BuildKind.LIBRARY);
        assertThat(BuildKind.parse("staticlib")).contains(BuildKind.STATICLIB);
        assertThat(BuildKind.parse("sharedlib")).contains(BuildKind.SHAREDLIB);
        assertThat(BuildKind.parse("weird")).isEmpty();
        assertThat(BuildKind.APP.library()).isFalse();
        assertThat(BuildKind.JAR.library()).isFalse();
        assertThat(BuildKind.LIBRARY.library()).isTrue();
        assertThat(BuildKind.STATICLIB.library()).isTrue();
        assertThat(BuildKind.SHAREDLIB.library()).isTrue();
    }

    @Test
    void buildKindArtifactPathsMatchKindShape() {
        final Path out = tempDir.resolve(".javan");

        assertThat(BuildKind.APP.artifactPath(out, "demo")).isEqualTo(out.resolve("bin/demo"));
        assertThat(BuildKind.JAR.artifactPath(out, "demo")).isEqualTo(out.resolve("dist/demo.jar"));
        assertThat(BuildKind.LIBRARY.artifactPath(out, "demo")).isEqualTo(out.resolve("dist/lib/demo"));
        assertThat(BuildKind.STATICLIB.artifactPath(out, "demo")).isEqualTo(out.resolve("dist/libdemo.a"));
        assertThat(BuildKind.SHAREDLIB.artifactPath(out, "demo").getFileName().toString())
            .isIn("libdemo.so", "libdemo.dylib", "demo.dll");
    }

    @Test
    void buildKindSharedLibraryNamingFollowsCurrentOs() {
        final Path out = tempDir.resolve(".javan");
        final String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            assertThat(BuildKind.SHAREDLIB.artifactPath(out, "demo").getFileName().toString()).isEqualTo("demo.dll");
            System.setProperty("os.name", "Mac OS X");
            assertThat(BuildKind.SHAREDLIB.artifactPath(out, "demo").getFileName().toString()).isEqualTo("libdemo.dylib");
            System.setProperty("os.name", "Linux");
            assertThat(BuildKind.SHAREDLIB.artifactPath(out, "demo").getFileName().toString()).isEqualTo("libdemo.so");
        } finally {
            restoreOsName(previous);
        }
    }

    @Test
    void libraryFormatParsesListsAliasesAndRejectsUnknownEntries() {
        assertThat(LibraryFormat.parse("static")).contains(LibraryFormat.STATIC);
        assertThat(LibraryFormat.parse("unknown")).isEmpty();
        assertThat(LibraryFormat.parseList("all")).containsExactly(LibraryFormat.STATIC, LibraryFormat.SHARED);
        assertThat(LibraryFormat.parseList("both")).containsExactly(LibraryFormat.STATIC, LibraryFormat.SHARED);
        assertThat(LibraryFormat.parseList("shared, static, shared")).containsExactly(LibraryFormat.SHARED, LibraryFormat.STATIC);
        assertThat(LibraryFormat.parseList(" shared ,, static ,")).containsExactly(LibraryFormat.SHARED, LibraryFormat.STATIC);
        assertThatThrownBy(() -> LibraryFormat.parseList("weird"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported library format: weird");
    }

    @Test
    void abiTypesExposeStableNamesAndSuffixes() {
        assertThat(AbiType.VOID.cName()).isEqualTo("void");
        assertThat(AbiType.INT.cName()).isEqualTo("int");
        assertThat(AbiType.LONG.cName()).isEqualTo("long long");
        assertThat(AbiType.FLOAT.cName()).isEqualTo("float");
        assertThat(AbiType.DOUBLE.cName()).isEqualTo("double");
        assertThat(AbiType.STRING.cName()).isEqualTo("const char*");
        assertThat(AbiType.STRING.cReturnName()).isEqualTo("char*");
        assertThat(AbiType.BYTE_ARRAY.cName()).isEqualTo("JavanByteArray");
        assertThat(AbiType.INT.suffix()).isEqualTo("int");
        assertThat(AbiType.LONG.suffix()).isEqualTo("long");
        assertThat(AbiType.FLOAT.suffix()).isEqualTo("float");
        assertThat(AbiType.DOUBLE.suffix()).isEqualTo("double");
        assertThat(AbiType.STRING.suffix()).isEqualTo("string");
        assertThat(AbiType.BYTE_ARRAY.suffix()).isEqualTo("bytes");
    }

    @Test
    void libraryFormatArtifactPathsFollowCurrentOsNaming() {
        final Path out = tempDir.resolve(".javan");
        final String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            assertThat(LibraryFormat.SHARED.artifactPath(out, "demo").getFileName().toString()).isEqualTo("demo.dll");
            System.setProperty("os.name", "Mac OS X");
            assertThat(LibraryFormat.SHARED.artifactPath(out, "demo").getFileName().toString()).isEqualTo("libdemo.dylib");
            System.setProperty("os.name", "Linux");
            assertThat(LibraryFormat.SHARED.artifactPath(out, "demo").getFileName().toString()).isEqualTo("libdemo.so");
            assertThat(LibraryFormat.STATIC.artifactPath(out, "demo")).isEqualTo(out.resolve("dist/libdemo.a"));
        } finally {
            restoreOsName(previous);
        }
    }

    @Test
    void bindingLanguageParsesKnownValuesAndRejectsUnknown() {
        assertThat(BindingLanguage.parse("python")).contains(BindingLanguage.PYTHON);
        assertThat(BindingLanguage.parse("rust")).contains(BindingLanguage.RUST);
        assertThat(BindingLanguage.parse("ruby")).isEmpty();
    }

    @Test
    void exportResolverResolvesSingleStaticMethodFromShortDeclaration() throws Exception {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));

        final ExportedMethod export = new ExportResolver()
            .resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch"))
            .getFirst();

        assertThat(export.entryPoint()).isEqualTo(new EntryPoint("com/acme/Api", "touch", "()V"));
        assertThat(export.symbol()).isEqualTo("javan_export_com_acme_Api_touch_void");
        assertThat(export.trySymbol()).isEqualTo("javan_try_com_acme_Api_touch_void");
        assertThat(export.parameterTypes()).isEmpty();
        assertThat(export.returnType()).isEqualTo(AbiType.VOID);
    }

    @Test
    void exportResolverResolvesExplicitSignatureWithStringAndByteArrayTypes() throws Exception {
        final ClassFile api = classFile("com/acme/Text", method(0x0008, "echo", "(Ljava/lang/String;[B)Ljava/lang/String;"));

        final ExportedMethod export = new ExportResolver()
            .resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Text.echo(String, byte[]):java.lang.String"))
            .getFirst();

        assertThat(export.entryPoint()).isEqualTo(new EntryPoint("com/acme/Text", "echo", "(Ljava/lang/String;[B)Ljava/lang/String;"));
        assertThat(export.symbol()).isEqualTo("javan_export_com_acme_Text_echo_string_bytes");
        assertThat(export.parameterTypes()).containsExactly(AbiType.STRING, AbiType.BYTE_ARRAY);
        assertThat(export.returnType()).isEqualTo(AbiType.STRING);
    }

    @Test
    void exportResolverResolvesExplicitPrimitiveMatrix() throws Exception {
        final ClassFile api = classFile("com/acme/Math", method(0x0008, "mix", "(IJFDLjava/lang/String;[B)D"));

        final ExportedMethod export = new ExportResolver()
            .resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Math.mix(int,long,float,double,String,byte[]):double"))
            .getFirst();

        assertThat(export.entryPoint()).isEqualTo(new EntryPoint("com/acme/Math", "mix", "(IJFDLjava/lang/String;[B)D"));
        assertThat(export.parameterTypes()).containsExactly(AbiType.INT, AbiType.LONG, AbiType.FLOAT, AbiType.DOUBLE, AbiType.STRING, AbiType.BYTE_ARRAY);
        assertThat(export.returnType()).isEqualTo(AbiType.DOUBLE);
    }

    @Test
    void exportResolverResolvesExplicitZeroArgVoidDeclaration() throws Exception {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));

        final ExportedMethod export = new ExportResolver()
            .resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch():void"))
            .getFirst();

        assertThat(export.entryPoint()).isEqualTo(new EntryPoint("com/acme/Api", "touch", "()V"));
        assertThat(export.parameterTypes()).isEmpty();
        assertThat(export.returnType()).isEqualTo(AbiType.VOID);
    }

    @Test
    void exportResolverRejectsAmbiguousShortDeclaration() {
        final ClassFile api = classFile(
            "com/acme/Api",
            method(0x0008, "sum", "(I)I"),
            method(0x0008, "sum", "(J)J")
        );

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.sum")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Ambiguous export com.acme.Api.sum; use com.acme.Type.method(int):int form");
    }

    @Test
    void exportResolverRejectsInstanceExports() {
        final ClassFile api = classFile("com/acme/Api", method(0, "touch", "()V"));

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export com.acme.Api.touch()V: exported methods must be static");
    }

    @Test
    void exportResolverRejectsUnsupportedParameterType() {
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(), tempDir, List.of("com.acme.Api.touch(Object):void")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export type: Object");
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(), tempDir, List.of("com.acme.Api.touch(void):void")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export parameter type: void");
    }

    @Test
    void exportResolverRejectsMissingExports() {
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(), tempDir, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Library builds require at least one --export or [exports].methods entry in javan.toml");
    }

    @Test
    void exportResolverRejectsBlankDeclaration() {
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(), tempDir, List.of("  ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Blank export declaration");
    }

    @Test
    void exportResolverRejectsMissingClassAndMethod() {
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(), tempDir, List.of("com.acme.Api.touch")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Export class not found: com.acme.Api");

        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.missing")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Export method not found: com.acme.Api.missing");
    }

    @Test
    void exportResolverRejectsMethodsWithoutBytecode() {
        final ClassFile api = new ClassFile(
            69,
            "com/acme/Api",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0x0108, "touch", "()V", Optional.empty())),
            Path.of("Api.class"),
            true
        );

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export com.acme.Api.touch()V: method must have Java bytecode");
    }

    @Test
    void exportResolverRejectsNativeMethods() {
        final ClassFile api = new ClassFile(
            69,
            "com/acme/Api",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0x0108, "touch", "()V", Optional.of(new CodeAttribute(0, 0, new byte[0], 0, List.of())))),
            Path.of("Api.class"),
            true
        );

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export com.acme.Api.touch()V: method must have Java bytecode");
    }

    @Test
    void exportResolverReadsConfigMethodsAndDeduplicatesCliEntries() throws Exception {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"), method(0x0008, "ping", "()V"));
        Files.writeString(tempDir.resolve("javan.toml"), """
            [exports]
            methods = ["com.acme.Api.ping", "com.acme.Api.touch"]
            """);

        final List<ExportedMethod> exports = new ExportResolver().resolve(
            Map.of(api.name(), api),
            tempDir,
            List.of("com.acme.Api.touch")
        );

        assertThat(exports).extracting(ExportedMethod::entryPoint)
            .containsExactly(
                new EntryPoint("com/acme/Api", "touch", "()V"),
                new EntryPoint("com/acme/Api", "ping", "()V")
            );
    }

    @Test
    void exportResolverIgnoresConfigMethodsWithoutArraySyntax() throws Exception {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));
        Files.writeString(tempDir.resolve("javan.toml"), """
            [exports]
            methods = "com.acme.Api.touch"
            """);

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Library builds require at least one --export or [exports].methods entry in javan.toml");
    }

    @Test
    void exportResolverRejectsMalformedConfigExports() throws Exception {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));

        Files.writeString(tempDir.resolve("javan.toml"), """
            [exports]
            methods = ["com.acme.Api.touch"
            """);
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid [exports].methods in javan.toml");

        Files.writeString(tempDir.resolve("javan.toml"), """
            [exports]
            methods = ["com.acme.Api.touch]
            """);
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid quoted export in javan.toml");
    }

    @Test
    void exportResolverSkipsStrayMethodsTokenBeforeRealArray() throws Exception {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));
        Files.writeString(tempDir.resolve("javan.toml"), """
            note = "methods = broken"

            [exports]
            methods = ["com.acme.Api.touch"]
            """);

        final List<ExportedMethod> exports = new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of());

        assertThat(exports).hasSize(1);
    }

    @Test
    void exportResolverRejectsInvalidExplicitDeclarationForms() {
        final ClassFile api = classFile("com/acme/Api", method(0x0008, "touch", "()V"));

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch(int)")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid export declaration: com.acme.Api.touch(int)");
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.(int):int")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Export method not found: com.acme.Api.(int):int");
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("broken.")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Export must look like com.acme.Type.method or com.acme.Type.method(int):int");
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("touch():void")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid export declaration: touch():void");
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.touch():")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid export declaration: com.acme.Api.touch():");
    }

    @Test
    void exportResolverRejectsUnsupportedDescriptorTypesFromShortDeclaration() {
        final ClassFile objectReturn = classFile("com/acme/Api", method(0x0008, "touch", "()Ljava/lang/Object;"));
        final ClassFile intArrayReturn = classFile("com/acme/Bytes", method(0x0008, "read", "()[I"));

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(objectReturn.name(), objectReturn), tempDir, List.of("com.acme.Api.touch")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export object type: java.lang.Object");
        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(intArrayReturn.name(), intArrayReturn), tempDir, List.of("com.acme.Bytes.read")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export array descriptor: ()[I");
    }

    @Test
    void exportResolverResolvesShortDeclarationsForLongFloatAndDoubleDescriptors() throws Exception {
        final ClassFile api = classFile(
            "com/acme/Numbers",
            method(0x0008, "longValue", "(J)J"),
            method(0x0008, "floatValue", "(F)F"),
            method(0x0008, "doubleValue", "(D)D")
        );

        final List<ExportedMethod> exports = new ExportResolver().resolve(
            Map.of(api.name(), api),
            tempDir,
            List.of("com.acme.Numbers.longValue", "com.acme.Numbers.floatValue", "com.acme.Numbers.doubleValue")
        );

        assertThat(exports).extracting(ExportedMethod::returnType)
            .containsExactly(AbiType.LONG, AbiType.FLOAT, AbiType.DOUBLE);
    }

    @Test
    void exportResolverResolvesExplicitByteCharShortAndBooleanAsInts() throws Exception {
        final ClassFile api = classFile(
            "com/acme/Numbers",
            method(0x0008, "mix", "(IIII)I")
        );

        final ExportedMethod export = new ExportResolver().resolve(
            Map.of(api.name(), api),
            tempDir,
            List.of("com.acme.Numbers.mix(byte,char,short,boolean):int")
        ).getFirst();

        assertThat(export.parameterTypes()).containsExactly(AbiType.INT, AbiType.INT, AbiType.INT, AbiType.INT);
        assertThat(export.returnType()).isEqualTo(AbiType.INT);
    }

    @Test
    void exportResolverRejectsMalformedObjectDescriptorFromShortDeclaration() {
        final ClassFile broken = classFile("com/acme/Api", method(0x0008, "touch", "()Ljava/lang/String"));

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(broken.name(), broken), tempDir, List.of("com.acme.Api.touch")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported export descriptor: ()Ljava/lang/String");
    }

    @Test
    void exportResolverIgnoresConstructorLikeNamesInShortLookup() {
        final ClassFile api = classFile(
            "com/acme/Api",
            method(0x0008, "<init>", "()V"),
            method(0x0008, "<clinit>", "()V")
        );

        assertThatThrownBy(() -> new ExportResolver().resolve(Map.of(api.name(), api), tempDir, List.of("com.acme.Api.<init>")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Export method not found: com.acme.Api.<init>");
    }

    @Test
    void runtimeFeatureSelectionReadsDefaultsWhenConfigIsMissing() throws Exception {
        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection().read(tempDir);

        assertThat(settings.configPath()).isEmpty();
        assertThat(settings.disabledRuntimeModules()).isEmpty();
        assertThat(settings.containment()).isEqualTo("system-linked");
        assertThat(settings.optimize()).isEqualTo("balanced");
        assertThat(settings.debug()).isFalse();
        assertThat(settings.profiling()).isFalse();
    }

    @Test
    void runtimeFeatureSelectionReadsLegacyAndBuildScopedKeys() throws Exception {
        Files.writeString(tempDir.resolve("javan.toml"), """
            [build.runtime]
            disabled = [" Strings ", "THREADS"]
            containment = "self_contained"
            optimize = "size_first"
            debug = true
            profiling = true
            """);

        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection().read(tempDir);

        assertThat(settings.disabledRuntimeModules()).containsExactly("strings", "threads");
        assertThat(settings.containment()).isEqualTo("self-contained");
        assertThat(settings.optimize()).isEqualTo("size-first");
        assertThat(settings.debug()).isTrue();
        assertThat(settings.profiling()).isTrue();
    }

    @Test
    void runtimeFeatureSelectionReadsScalarDisabledListAndFalseBooleans() throws Exception {
        Files.writeString(tempDir.resolve("javan.toml"), """
            disabled = strings
            debug = false
            profiling = false
            """);

        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection().read(tempDir);

        assertThat(settings.disabledRuntimeModules()).containsExactly("strings");
        assertThat(settings.debug()).isFalse();
        assertThat(settings.profiling()).isFalse();
    }

    @Test
    void runtimeFeatureSelectionPrefersRuntimeScopedKeysAndNormalizesDisabledModules() throws Exception {
        Files.writeString(tempDir.resolve("javan.toml"), """
            [runtime]
            disabled = [" Strings ", "", "strings", "debug_symbols"]
            containment = "self_contained"
            optimize = "speed_first"

            [build.runtime]
            disabled = ["threads"]
            containment = "system_linked"
            optimize = "size_first"
            """);

        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection().read(tempDir);

        assertThat(settings.disabledRuntimeModules()).containsExactly("debug-symbols", "strings");
        assertThat(settings.containment()).isEqualTo("self-contained");
        assertThat(settings.optimize()).isEqualTo("speed-first");
    }

    @Test
    void runtimeFeatureSelectionRejectsInvalidBooleanValue() throws Exception {
        Files.writeString(tempDir.resolve("javan.toml"), """
            [runtime]
            debug = maybe
            """);

        assertThatThrownBy(() -> new RuntimeFeatureSelection().read(tempDir))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expected boolean runtime setting: maybe");
    }

    @Test
    void runtimeFeatureSelectionWritesFailingReportForReachableAndUnknownDisabledModules() throws Exception {
        Files.writeString(tempDir.resolve("javan.toml"), """
            [runtime]
            disabled = ["strings", "mystery"]
            """);
        final Path output = tempDir.resolve(".javan");

        final RuntimeFeatureSelection.Report report = new RuntimeFeatureSelection().write(
            tempDir,
            output,
            new javan.build.DeduplicationPlanner.Plan(
                List.of("core", "strings"),
                0,
                List.of(),
                List.of()
            )
        );

        assertThat(report.diagnostics()).hasSize(2);
        assertThat(report.disabledReachableRuntimeModules()).containsExactly("strings");
        assertThat(report.unknownDisabledRuntimeModules()).containsExactly("mystery");
        assertThat(Files.readString(report.jsonPath())).contains("\"status\": \"fail\"");
        assertThat(Files.readString(report.markdownPath())).contains("- status: `fail`");
    }

    @Test
    void runtimeFeatureSelectionWritesPassingReportWhenNoDisabledModulesAreReachable() throws Exception {
        Files.writeString(tempDir.resolve("javan.toml"), """
            [runtime]
            disabled = ["threads"]
            """);

        final RuntimeFeatureSelection.Report report = new RuntimeFeatureSelection().write(
            tempDir,
            tempDir.resolve(".javan"),
            new DeduplicationPlanner.Plan(List.of("core", "strings"), 0, List.of(), List.of())
        );

        assertThat(report.diagnostics()).isEmpty();
        assertThat(report.disabledReachableRuntimeModules()).isEmpty();
        assertThat(Files.readString(report.jsonPath())).contains("\"status\": \"pass\"");
        assertThat(Files.readString(report.markdownPath())).contains("- status: `pass`");
    }

    @Test
    void runtimeFeatureSelectionWritesDisabledRuntimeProfilingReportByDefault() throws Exception {
        new RuntimeFeatureSelection().write(
            tempDir,
            tempDir.resolve(".javan"),
            new DeduplicationPlanner.Plan(List.of("core"), 0, List.of(), List.of())
        );

        assertThat(Files.readString(tempDir.resolve(".javan/reports/runtime-profiling.json"))).contains(
            "\"status\": \"disabled\"",
            "\"requested\": false",
            "\"enabled\": false",
            "\"collectionState\": \"disabled\"",
            "\"disabledProfilingModules\": []"
        );
        assertThat(Files.readString(tempDir.resolve(".javan/reports/runtime-profiling.md"))).contains(
            "# Runtime Profiling",
            "- status: `disabled`",
            "- requested: `false`"
        );
    }

    @Test
    void libraryBuildReportsWriteDeterministicMetricsAndRuntimeModules() throws Exception {
        final ClassFile main = classFile(
            "com/acme/Main",
            method(0x0008, "main", "([Ljava/lang/String;)V"),
            method(0x0008, "helper", "([B)Ljava/lang/String;")
        );
        final EntryPoint entry = new EntryPoint("com/acme/Main", "helper", "([B)Ljava/lang/String;");
        final Path output = tempDir.resolve(".javan");
        final Path artifact = output.resolve("dist/libdemo.a");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "binary");

        final List<Path> reports = new LibraryBuildReports().write(
            output,
            Map.of(main.name(), main),
            new CallGraph(entry, List.of(entry), List.of()),
            List.of(new ExportedMethod(entry, "javan_export_demo", List.of(AbiType.BYTE_ARRAY), AbiType.STRING)),
            List.of(artifact),
            List.of(output.resolve("dist/bindings/c/demo.h"))
        );

        assertThat(reports).hasSize(2);
        assertThat(Files.readString(reports.getFirst())).contains(
            "\"abiVersion\": 2",
            "\"errorResultAbi\": \"abi-v2-c-owned-javanresult-try-wrappers-v1-direct-exports-compatible\"",
            "\"reachableMethodsFromExports\": 1",
            "\"runtimeModulesLinked\": [\"core\", \"strings\", \"arrays\"]"
        );
        assertThat(Files.readString(reports.get(1))).contains(
            "- ABI version: `2`",
            "- error/result ABI: `abi-v2-c-owned-javanresult-try-wrappers-v1-direct-exports-compatible`",
            "- exported methods: `1`",
            "- runtime modules linked: `core, strings, arrays`"
        );
    }

    @Test
    void libraryBuildReportsDeduplicateReachableClassesAndIgnoreMissingArtifacts() throws Exception {
        final ClassFile main = classFile(
            "com/acme/Main",
            method(0x0008, "first", "()V"),
            method(0x0008, "second", "(Ljava/lang/String;)V")
        );
        final EntryPoint first = new EntryPoint("com/acme/Main", "first", "()V");
        final EntryPoint second = new EntryPoint("com/acme/Main", "second", "(Ljava/lang/String;)V");
        final Path output = tempDir.resolve(".javan");
        final Path existing = output.resolve("dist/libdemo.a");
        final Path missing = output.resolve("dist/libmissing.a");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "abcd");

        final List<Path> reports = new LibraryBuildReports().write(
            output,
            Map.of(main.name(), main),
            new CallGraph(first, List.of(first, second), List.of()),
            List.of(new ExportedMethod(first, "javan_export_demo", List.of(), AbiType.VOID)),
            List.of(existing, missing),
            List.of()
        );

        assertThat(Files.readString(reports.getFirst())).contains(
            "\"reachableClassesFromExports\": 1",
            "\"artifactBytes\": 4"
        );
    }

    @Test
    void bindingGeneratorWritesLanguageBindingsAndCopiesArtifacts() throws Exception {
        final Path output = tempDir.resolve(".javan");
        final Path shared = output.resolve("dist/libdemo.so");
        Files.createDirectories(shared.getParent());
        Files.writeString(shared, "so");
        final List<ExportedMethod> exports = List.of(
            new ExportedMethod(
                new EntryPoint("com/acme/Api", "sum", "(I)I"),
                "javan_export_com_acme_Api_sum_int",
                List.of(AbiType.INT),
                AbiType.INT
            ),
            new ExportedMethod(
                new EntryPoint("com/acme/Text", "echo", "(Ljava/lang/String;[B)Ljava/lang/String;"),
                "javan_export_com_acme_Text_echo_string_bytes",
                List.of(AbiType.STRING, AbiType.BYTE_ARRAY),
                AbiType.STRING
            )
        );

        final List<Path> files = new BindingGenerator().generate(
            output,
            "demo",
            exports,
            List.of(BindingLanguage.RUST, BindingLanguage.GO, BindingLanguage.PYTHON, BindingLanguage.C),
            List.of(shared)
        );

        assertThat(files).contains(
            output.resolve("dist/bindings/c/demo.h"),
            output.resolve("dist/bindings/c/demo_abi_test.c"),
            output.resolve("dist/bindings/rust/lib.rs"),
            output.resolve("dist/bindings/go/demo.go"),
            output.resolve("dist/bindings/python/demo.py"),
            output.resolve("dist/lib/demo/c/demo.h"),
            output.resolve("dist/lib/demo/rust/lib.rs"),
            output.resolve("dist/lib/demo/go/demo.go"),
            output.resolve("dist/lib/demo/python/demo.py")
        );
        assertThat(Files.readString(output.resolve("dist/bindings/c/demo.h"))).contains(
            "#define JAVAN_ABI_VERSION 2",
            "#define JAVAN_ABI_V1_DIRECT_EXPORTS 1",
            "#define JAVAN_ABI_RUNTIME_DIAGNOSTICS 1",
            "#define JAVAN_ABI_STRUCTURED_ERROR 1",
            "#define JAVAN_ABI_RESULT_WRAPPERS 1",
            "typedef struct {",
            "int ok;",
            "char* code;",
            "char* message;",
            "char* class_name;",
            "int bytecode_offset;",
            "} JavanResult;",
            "void javan_result_free(JavanResult* result);",
            "const char* javan_last_error(void);",
            "const char* javan_last_error_code(void);",
            "const char* javan_last_error_summary(void);",
            "const char* javan_last_error_class(void);",
            "const char* javan_last_error_method(void);",
            "const char* javan_last_error_file(void);",
            "int javan_last_error_line(void);",
            "int javan_last_error_bytecode_offset(void);",
            "const char* javan_last_error_source_line(void);",
            "const char* javan_last_error_why(void);",
            "const char* javan_last_error_fix(void);",
            "const char* javan_last_error_detail(void);",
            "void javan_clear_error(void);",
            "char* javan_export_com_acme_Text_echo_string_bytes(const char* arg0, JavanByteArray arg1);",
            "JavanResult javan_try_com_acme_Api_sum_int(int arg0, int* out);",
            "JavanResult javan_try_com_acme_Text_echo_string_bytes(const char* arg0, JavanByteArray arg1, char** out);"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/c/demo_abi_test.c"))).contains(
            "_Static_assert(offsetof(JavanResult, ok) == 0",
            "_Static_assert(JAVAN_ABI_V1_DIRECT_EXPORTS == 1",
            "_Static_assert(JAVAN_ABI_STRUCTURED_ERROR == 1",
            "_Static_assert(JAVAN_ABI_RESULT_WRAPPERS == 1",
            "void (*result_free_fn)(JavanResult*) = javan_result_free;",
            "const char* (*error_code_fn)(void) = javan_last_error_code;",
            "int (*error_line_fn)(void) = javan_last_error_line;",
            "const char* (*error_detail_fn)(void) = javan_last_error_detail;",
            "JavanResult (*try_javan_try_com_acme_Api_sum_int_fn)(int arg0, int* out) = javan_try_com_acme_Api_sum_int;",
            "JavanResult (*try_javan_try_com_acme_Text_echo_string_bytes_fn)(const char* arg0, JavanByteArray arg1, char** out) = javan_try_com_acme_Text_echo_string_bytes;"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/rust/lib.rs"))).contains(
            "#[derive(Clone, Copy)]",
            "pub struct JavanResult {",
            "pub struct JavanError {",
            "pub fn javan_result_free(result: *mut JavanResult);",
            "pub fn javan_export_com_acme_Api_sum_int(arg0: i32) -> i32;",
            "pub fn javan_export_com_acme_Text_echo_string_bytes(arg0: *const c_char, arg1: JavanByteArray) -> *mut c_char;",
            "pub fn javan_try_com_acme_Api_sum_int(arg0: i32, out: *mut i32) -> JavanResult;",
            "pub fn javan_try_com_acme_Text_echo_string_bytes(arg0: *const c_char, arg1: JavanByteArray, out: *mut *mut c_char) -> JavanResult;",
            "pub fn javan_last_error() -> *const c_char;",
            "pub fn javan_last_error_code() -> *const c_char;",
            "pub fn javan_last_error_line() -> i32;",
            "pub fn javan_last_error_detail() -> *const c_char;",
            "pub fn javan_clear_error();",
            "pub unsafe fn javan_free_string(value: *mut c_char)",
            "pub unsafe fn javan_free_byte_array(value: JavanByteArray)",
            "pub unsafe fn javan_free_result(result: &mut JavanResult)",
            "pub unsafe fn try_javan_export_com_acme_Api_sum_int(arg0: i32) -> Result<i32, JavanError>",
            "pub unsafe fn try_javan_export_com_acme_Text_echo_string_bytes(arg0: *const c_char, arg1: JavanByteArray) -> Result<Option<String>, JavanError>",
            "unsafe fn javan_owned_string(value: *mut c_char) -> Option<String>",
            "unsafe fn javan_owned_byte_array(value: JavanByteArray) -> Vec<i8>"
        );
        assertThat(Files.readString(output.resolve("dist/lib/demo/go/demo.go"))).contains(
            "#include \"demo.h\"",
            "type JavanResult = C.JavanResult",
            "type JavanError struct {",
            "func JavanResultFree(result *JavanResult) {",
            "func JavanFree(value unsafe.Pointer) {",
            "func JavanFreeByteArray(value JavanByteArray) {",
            "func JavanLastError() *C.char {",
            "func JavanLastErrorCode() *C.char {",
            "func JavanLastErrorLine() int32 {",
            "func JavanLastErrorDetail() *C.char {",
            "func JavanClearError() {",
            "func JavanExportComAcmeTextEchoStringBytes(arg0 *C.char, arg1 JavanByteArray) *C.char {",
            "func TryJavanExportComAcmeApiSumInt(arg0 int32) (int32, error) {",
            "func TryJavanExportComAcmeTextEchoStringBytes(arg0 *C.char, arg1 JavanByteArray) (*string, error) {",
            "result := C.javan_try_com_acme_Text_echo_string_bytes(arg0, C.JavanByteArray(arg1), &out)",
            "value := javanOwnedString(out)"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/python/demo.py"))).contains(
            "class JavanResult(ctypes.Structure):",
            "class JavanError(Exception):",
            "lib.javan_result_free.argtypes = [ctypes.POINTER(JavanResult)]",
            "lib.javan_last_error.restype = ctypes.c_char_p",
            "lib.javan_last_error_code.restype = ctypes.c_char_p",
            "lib.javan_last_error_line.restype = ctypes.c_int",
            "lib.javan_last_error_detail.restype = ctypes.c_char_p",
            "lib.javan_try_com_acme_Api_sum_int.argtypes = [ctypes.c_int, ctypes.POINTER(ctypes.c_int)]",
            "lib.javan_try_com_acme_Api_sum_int.restype = JavanResult",
            "def free(lib, value) -> None:",
            "def free_byte_array(lib, value: JavanByteArray) -> None:",
            "def free_result(lib, result: JavanResult) -> None:",
            "def last_error(lib) -> bytes | None:",
            "def last_error_code(lib) -> bytes | None:",
            "def last_error_line(lib) -> int:",
            "def last_error_detail(lib) -> bytes | None:",
            "def clear_error(lib) -> None:",
            "def try_javan_export_com_acme_Api_sum_int(lib, arg0):",
            "def try_javan_export_com_acme_Text_echo_string_bytes(lib, arg0, arg1):",
            "return _owned_string(lib, out.value)"
        );
        assertThat(output.resolve("dist/lib/demo/python/libdemo.so")).exists();
    }

    @Test
    void bindingGeneratorSupportsVoidOnlyGenerationAndSkipsMissingArtifacts() throws Exception {
        final Path output = tempDir.resolve(".javan");
        final ExportedMethod export = new ExportedMethod(
            new EntryPoint("com/acme/Api", "touch", "()V"),
            "javan_export_com_acme_Api_touch_void",
            List.of(),
            AbiType.VOID
        );

        final List<Path> files = new BindingGenerator().generate(output, "demo", List.of(export), List.of());

        assertThat(files).contains(
            output.resolve("dist/bindings/c/demo.h"),
            output.resolve("dist/bindings/c/demo_abi_test.c"),
            output.resolve("dist/lib/demo/c/demo.h"),
            output.resolve("dist/lib/demo/c/demo_abi_test.c")
        );
        assertThat(Files.readString(output.resolve("dist/bindings/c/demo.h"))).contains(
            "void javan_export_com_acme_Api_touch_void(void);",
            "JavanResult javan_try_com_acme_Api_touch_void(void);"
        );
        assertThat(output.resolve("dist/lib/demo/c/libdemo.so")).doesNotExist();
    }

    @Test
    void bindingGeneratorWritesPrimitiveMappingsForRustGoAndPython() throws Exception {
        final Path output = tempDir.resolve(".javan");
        final List<ExportedMethod> exports = List.of(
            new ExportedMethod(new EntryPoint("com/acme/Api", "keep", "(JFD)[B"), "javan_export_keep_long_float_double", List.of(AbiType.LONG, AbiType.FLOAT, AbiType.DOUBLE), AbiType.BYTE_ARRAY),
            new ExportedMethod(new EntryPoint("com/acme/Api", "ratio", "()F"), "javan_export_ratio_float", List.of(), AbiType.FLOAT),
            new ExportedMethod(new EntryPoint("com/acme/Api", "total", "()D"), "javan_export_total_double", List.of(), AbiType.DOUBLE),
            new ExportedMethod(new EntryPoint("com/acme/Api", "touch", "()V"), "javan_export_touch_void", List.of(), AbiType.VOID)
        );

        new BindingGenerator().generate(output, "demo", exports, List.of(BindingLanguage.RUST, BindingLanguage.GO, BindingLanguage.PYTHON));

        assertThat(Files.readString(output.resolve("dist/bindings/rust/lib.rs"))).contains(
            "pub fn javan_export_keep_long_float_double(arg0: i64, arg1: f32, arg2: f64) -> JavanByteArray;",
            "pub fn javan_export_ratio_float() -> f32;",
            "pub fn javan_export_total_double() -> f64;",
            "pub fn javan_export_touch_void() -> ();",
            "pub fn javan_try_keep_long_float_double(arg0: i64, arg1: f32, arg2: f64, out: *mut JavanByteArray) -> JavanResult;",
            "pub fn javan_try_touch_void() -> JavanResult;",
            "pub unsafe fn try_javan_export_keep_long_float_double(arg0: i64, arg1: f32, arg2: f64) -> Result<Vec<i8>, JavanError>",
            "pub unsafe fn try_javan_export_ratio_float() -> Result<f32, JavanError>",
            "pub unsafe fn try_javan_export_total_double() -> Result<f64, JavanError>",
            "pub unsafe fn try_javan_export_touch_void() -> Result<(), JavanError>"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/go/demo.go"))).contains(
            "import \"unsafe\"",
            "func JavanFree(value unsafe.Pointer) {",
            "func JavanFreeByteArray(value JavanByteArray) {",
            "func JavanExportKeepLongFloatDouble(arg0 int64, arg1 float32, arg2 float64) JavanByteArray {",
            "return JavanByteArray(C.javan_export_keep_long_float_double(C.longlong(arg0), C.float(arg1), C.double(arg2)))",
            "func JavanExportRatioFloat() float32 {",
            "return float32(C.javan_export_ratio_float())",
            "func JavanExportTotalDouble() float64 {",
            "return float64(C.javan_export_total_double())",
            "func JavanExportTouchVoid()  {",
            "C.javan_export_touch_void()",
            "func TryJavanExportKeepLongFloatDouble(arg0 int64, arg1 float32, arg2 float64) ([]byte, error) {",
            "func TryJavanExportRatioFloat() (float32, error) {",
            "func TryJavanExportTotalDouble() (float64, error) {",
            "func TryJavanExportTouchVoid() error {"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/python/demo.py"))).contains(
            "lib.javan_export_keep_long_float_double.argtypes = [ctypes.c_longlong, ctypes.c_float, ctypes.c_double]",
            "lib.javan_export_keep_long_float_double.restype = JavanByteArray",
            "lib.javan_export_ratio_float.restype = ctypes.c_float",
            "lib.javan_export_total_double.restype = ctypes.c_double",
            "lib.javan_export_touch_void.restype = None",
            "lib.javan_try_keep_long_float_double.argtypes = [ctypes.c_longlong, ctypes.c_float, ctypes.c_double, ctypes.POINTER(JavanByteArray)]",
            "lib.javan_try_touch_void.argtypes = []",
            "def try_javan_export_keep_long_float_double(lib, arg0, arg1, arg2):",
            "def try_javan_export_ratio_float(lib):",
            "def try_javan_export_total_double(lib):",
            "def try_javan_export_touch_void(lib):"
        );
    }

    @Test
    void bindingGeneratorSanitizesDigitPrefixedAndPunctuatedLibraryNames() throws Exception {
        final Path output = tempDir.resolve(".javan");
        final ExportedMethod export = new ExportedMethod(
            new EntryPoint("com/acme/Api", "touch", "()V"),
            "Echo_name",
            List.of(),
            AbiType.LONG
        );

        new BindingGenerator().generate(output, "9Demo-lib", List.of(export), List.of(BindingLanguage.GO, BindingLanguage.PYTHON, BindingLanguage.RUST));

        assertThat(output.resolve("dist/bindings/go/javan_9Demo_lib.go")).exists();
        assertThat(output.resolve("dist/bindings/python/javan_9Demo_lib.py")).exists();
        assertThat(output.resolve("dist/lib/9Demo-lib/go/javan_9Demo_lib.go")).exists();
        assertThat(Files.readString(output.resolve("dist/bindings/go/javan_9Demo_lib.go"))).contains(
            "package javan_9Demo_lib",
            "func EchoName() int64 {",
            "return int64(C.Echo_name())"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/rust/lib.rs"))).contains(
            "pub fn Echo_name() -> i64;"
        );
        assertThat(Files.readString(output.resolve("dist/bindings/python/javan_9Demo_lib.py"))).contains(
            "lib.Echo_name.restype = ctypes.c_longlong"
        );
    }

    @Test
    void bindingGeneratorDeduplicatesRequestedLanguagesAndPreservesUppercaseRunsInGoNames() throws Exception {
        final Path output = tempDir.resolve(".javan");
        final ExportedMethod export = new ExportedMethod(
            new EntryPoint("com/acme/Api", "touch", "()V"),
            "HTTP_ping_now",
            List.of(),
            AbiType.VOID
        );

        final List<Path> files = new BindingGenerator().generate(
            output,
            "demo_name",
            List.of(export),
            List.of(BindingLanguage.GO, BindingLanguage.GO, BindingLanguage.PYTHON, BindingLanguage.GO)
        );

        assertThat(files.stream().filter(path -> path.equals(output.resolve("dist/bindings/go/demo_name.go")))).hasSize(1);
        assertThat(files.stream().filter(path -> path.equals(output.resolve("dist/bindings/python/demo_name.py")))).hasSize(1);
        assertThat(Files.readString(output.resolve("dist/bindings/go/demo_name.go"))).contains("func HTTPPingNow()  {");
    }

    @Test
    void bindingGeneratorIgnoresMissingArtifactsAcrossPackagedLanguages() throws Exception {
        final Path output = tempDir.resolve(".javan");
        final Path existing = output.resolve("dist/libdemo.so");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "so");
        final Path missing = output.resolve("dist/libmissing.so");
        final ExportedMethod export = new ExportedMethod(
            new EntryPoint("com/acme/Api", "touch", "()V"),
            "javan_export_touch_void",
            List.of(),
            AbiType.VOID
        );

        final List<Path> files = new BindingGenerator().generate(
            output,
            "demo",
            List.of(export),
            List.of(BindingLanguage.RUST, BindingLanguage.GO, BindingLanguage.PYTHON, BindingLanguage.C),
            List.of(existing, missing)
        );

        assertThat(files).contains(output.resolve("dist/lib/demo/rust/libdemo.so"));
        assertThat(output.resolve("dist/lib/demo/rust/libmissing.so")).doesNotExist();
        assertThat(output.resolve("dist/lib/demo/go/libdemo.so")).exists();
        assertThat(output.resolve("dist/lib/demo/python/libdemo.so")).exists();
        assertThat(output.resolve("dist/lib/demo/c/libdemo.so")).exists();
    }

    private static ClassFile classFile(final String name, final MethodInfo... methods) {
        return new ClassFile(69, name, "java/lang/Object", 0, List.of(), List.of(), List.of(methods), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final int accessFlags, final String name, final String descriptor) {
        return new MethodInfo(accessFlags, name, descriptor, Optional.of(new CodeAttribute(0, 0, new byte[0], 0, List.of())));
    }

    private static void restoreOsName(final String value) {
        if (value == null) {
            System.clearProperty("os.name");
            return;
        }
        System.setProperty("os.name", value);
    }
}
