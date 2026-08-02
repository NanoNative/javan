package javan.build;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.codegen.BytecodeToIR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class NativeInteropResolverTest {
    @TempDir
    private Path root;

    @Test
    void resolvesNoConfigurationAsEmpty() throws Exception {
        assertThat(new NativeInteropResolver().resolve(Map.of(), root)).isEqualTo(NativeInteropConfig.empty());
    }

    @Test
    void resolvesCommonConfiguration() throws Exception {
        final Path source = source("common.c");
        final Path object = object("common.o");
        final Path libraryDirectory = directory("lib");
        write("""
            [native]
            imports = ["sample.NativeApi.probe(int,java.lang.String[]):long -> sample_probe"]
            sources = ["native/common.c"]
            objects = ["native/common.o"]
            library-search-paths = ["native/lib"]
            libraries = ["sample"]
            frameworks = ["AppKit"]
            """);

        assertThat(new NativeInteropResolver().resolve(classes(nativeMethod("probe", "(I[Ljava/lang/String;)J")), root))
            .isEqualTo(new NativeInteropConfig(
                List.of(new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "(I[Ljava/lang/String;)J"), "sample_probe")),
                new NativeLinkInputs(List.of(source), List.of(object), List.of(libraryDirectory), List.of("sample"), List.of("AppKit"))
            ));
    }

    @Test
    void resolvesDocumentedMultilineImportsArrayWithTrailingComma() throws Exception {
        final ClassFile nativeApi = type("com/acme/NativeApi", nativeMethod("mix", "(IJFD[B)J"));
        write("""
            [native]
            imports = [
              "com.acme.NativeApi.mix(int,long,float,double,byte[]):long -> acme_mix",
            ]
            """);

        assertThat(new NativeInteropResolver().resolve(
            Map.of(nativeApi.name(), nativeApi),
            root
        ).externalSymbols()).containsExactly("acme_mix");
    }

    @Test
    void rejectsPhysicalNewlineWithinQuotedImport() throws Exception {
        write("""
            [native]
            imports = [
              "sample.NativeApi.probe():int
            -> sample_probe",
            ]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()I")), root))
            .hasMessage("Native configuration value must be an array of quoted strings: native.imports");
    }

    @Test
    void resolvesPrimitiveAndObjectArrayImportSignature() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe(boolean,byte,char,short,float,double,java.lang.Object[][]):void -> sample_probe"]
            """);

        assertThat(new NativeInteropResolver().resolve(
            classes(nativeMethod("probe", "(ZBCSFD[[Ljava/lang/Object;)V")),
            root
        ).externalSymbols()).containsExactly("sample_probe");
    }

    @Test
    void appendsCommonPlatformAndExactTargetConfigurationInOrder() throws Exception {
        final Path common = source("common.c");
        final Path platform = source("platform.m");
        final Path exact = source("exact.c");
        write("""
            [native]
            sources = ["native/common.c"]

            [native.target.macos]
            sources = ["native/platform.m"]

            [native.target.macos-aarch64]
            sources = ["native/exact.c"]
            """);

        assertThat(new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64").linkInputs().sources())
            .containsExactly(common, platform, exact);
    }

    @Test
    void normalizesTargetBeforeSelectingOverlay() throws Exception {
        final Path source = source("darwin.c");
        write("""
            [native.target.macos-aarch64]
            sources = ["native/darwin.c"]
            """);

        assertThat(new NativeInteropResolver().resolve(Map.of(), root, "aarch64-apple-darwin").linkInputs().sources())
            .containsExactly(source);
    }

    @Test
    void ignoresValidOtherPlatformOverlay() throws Exception {
        write("""
            [native.target.windows-x64]
            libraries = ["d3d11"]
            """);

        assertThat(new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64")).isEqualTo(NativeInteropConfig.empty());
    }

    @Test
    void rejectsDuplicateImportWithinLayer() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():int -> first_probe", "sample.NativeApi.probe():int -> second_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()I")), root))
            .hasMessageContaining("Duplicate native import declaration");
    }

    @Test
    void rejectsDuplicateImportAcrossLayers() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():int -> common_probe"]

            [native.target.macos]
            imports = ["sample.NativeApi.probe():int -> platform_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()I")), root, "macos-aarch64"))
            .hasMessageContaining("Duplicate native import declaration");
    }

    @Test
    void rejectsDuplicateExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.first():int -> shared_probe", "sample.NativeApi.second():int -> shared_probe"]
            """);
        final ClassFile type = type(nativeMethod("first", "()I"), nativeMethod("second", "()I"));

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(type.name(), type), root))
            .hasMessage("Duplicate native import symbol: shared_probe");
    }

    @Test
    void rejectsDuplicateNormalizedPathInput() throws Exception {
        source("duplicate.c");
        write("""
            [native]
            sources = ["native/duplicate.c", "native/./duplicate.c"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessageContaining("Duplicate native source");
    }

    @Test
    void rejectsDuplicateLibraryName() throws Exception {
        write("""
            [native]
            libraries = ["sample", "sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessageContaining("Duplicate native library");
    }

    @Test
    void rejectsUnknownNativeKey() throws Exception {
        write("""
            [native]
            compiler = "cc"
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Unknown native configuration key: native.compiler");
    }

    @Test
    void rejectsUnknownKeyInUnselectedTargetSection() throws Exception {
        write("""
            [native.target.windows-x64]
            compiler = "cl"
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Unknown native configuration key: native.target.windows-x64.compiler");
    }

    @Test
    void rejectsMalformedNativeTargetSectionId() throws Exception {
        write("""
            [native.target.macos.aarch64]
            libraries = ["sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: macos.aarch64");
    }

    @Test
    void rejectsNonCanonicalNativeTargetSectionId() throws Exception {
        write("""
            [native.target.arm64-apple-darwin]
            libraries = ["sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: arm64-apple-darwin");
    }

    @Test
    void rejectsPlatformAliasNativeTargetSectionId() throws Exception {
        write("""
            [native.target.darwin]
            libraries = ["sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: darwin");
    }

    @Test
    void rejectsLegacyNativeSection() throws Exception {
        write("""
            [build.native]
            libraries = ["sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Legacy native configuration is not supported: build.native.libraries");
    }

    @Test
    void rejectsAbsoluteNativePath() throws Exception {
        write("""
            [native]
            sources = ["/tmp/backend.c"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessageContaining("must be relative");
    }

    @Test
    void rejectsParentTraversalNativePath() throws Exception {
        write("""
            [native]
            sources = ["../backend.c"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessageContaining("must not contain parent traversal");
    }

    @Test
    void rejectsMissingNativePath() throws Exception {
        write("""
            [native]
            objects = ["native/missing.o"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native object does not exist: native/missing.o");
    }

    @Test
    void rejectsSymbolicLinkNativeSource() throws Exception {
        final Path outside = Files.createTempFile("native-interop-outside", ".c");
        final Path link = root.resolve("native/escape.c");
        Files.createDirectories(link.getParent());
        createSymbolicLinkOrSkip(link, outside);
        write("""
            [native]
            sources = ["native/escape.c"]
            """);

        try {
            assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
                .hasMessage("Native source is not a regular file: native/escape.c");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rejectsSymbolicLinkNativeConfiguration() throws Exception {
        final Path outside = Files.createTempFile("native-interop-config-outside", ".toml");
        Files.writeString(outside, "[native]\nlibraries = [\"sample\"]\n");
        createSymbolicLinkOrSkip(root.resolve("javan.toml"), outside);

        try {
            assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
                .hasMessage("Native configuration is not a regular file: javan.toml");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rejectsConfigurationDirectory() throws Exception {
        Files.createDirectory(root.resolve("javan.toml"));

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration is not a regular file: javan.toml");
    }

    @Test
    void rejectsBrokenConfigurationSymlink() throws Exception {
        createSymbolicLinkOrSkip(root.resolve("javan.toml"), root.resolve("missing.toml"));

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration is not a regular file: javan.toml");
    }

    @Test
    void rejectsSymbolicLinkNativeSourceParent() throws Exception {
        final Path outside = Files.createTempDirectory("native-interop-parent-outside");
        Files.writeString(outside.resolve("backend.c"), "int native_entry(void) { return 0; }\n");
        createSymbolicLinkOrSkip(root.resolve("linked-native"), outside);
        write("""
            [native]
            sources = ["linked-native/backend.c"]
            """);

        try {
            assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
                .hasMessage("Native source parent is not a directory: linked-native/backend.c");
        } finally {
            Files.deleteIfExists(outside.resolve("backend.c"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rejectsUnsupportedSourceExtension() throws Exception {
        source("backend.cpp");
        write("""
            [native]
            sources = ["native/backend.cpp"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Unsupported native source extension: native/backend.cpp");
    }

    @Test
    void rejectsUppercaseSourceExtension() throws Exception {
        source("backend.C");
        write("""
            [native]
            sources = ["native/backend.C"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Unsupported native source extension: native/backend.C");
    }

    @Test
    void rejectsDirectoryConfiguredAsNativeSource() throws Exception {
        directory("source.c");
        write("""
            [native]
            sources = ["native/source.c"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native source is not a regular file: native/source.c");
    }

    @Test
    void rejectsFileConfiguredAsLibrarySearchPath() throws Exception {
        source("library-path");
        write("""
            [native]
            library-search-paths = ["native/library-path"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native library search path is not a directory: native/library-path");
    }

    @Test
    void rejectsUnsupportedObjectExtension() throws Exception {
        object("backend.a");
        write("""
            [native]
            objects = ["native/backend.a"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Unsupported native object extension: native/backend.a");
    }

    @Test
    void rejectsInvalidLibraryName() throws Exception {
        write("""
            [native]
            libraries = ["sample/library"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native library name: sample/library");
    }

    @Test
    void rejectsPunctuationLeadingLibraryName() throws Exception {
        write("""
            [native]
            libraries = ["-sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native library name: -sample");
    }

    @Test
    void rejectsMissingNativeImportClass() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> sample_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native import class not found: sample.NativeApi");
    }

    @Test
    void rejectsMissingNativeImportMethod() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.missing():void -> missing_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Native import method not found: sample.NativeApi.missing():void");
    }

    @Test
    void rejectsNonNativeImportMethod() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> sample_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(method("probe", "()V")), root))
            .hasMessage("Declared native import is not native: sample.NativeApi.probe():void");
    }

    @Test
    void rejectsInvalidExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> probe-symbol"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Invalid native import symbol: probe-symbol");
    }

    @Test
    void rejectsProgramEntryExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> main"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Reserved native import symbol: main");
    }

    @Test
    void rejectsRuntimePrefixedExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> javan_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Reserved native import symbol: javan_probe");
    }

    @Test
    void rejectsGeneratedTypedefStyleExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> JavanByteArray"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Reserved native import symbol: JavanByteArray");
    }

    @Test
    void rejectsGeneratedMacroStyleExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> JAVAN_RUNTIME"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Reserved native import symbol: JAVAN_RUNTIME");
    }

    @Test
    void acceptsConsumerPrefixedExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> client_render"]
            """);

        assertThat(new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root).externalSymbols())
            .containsExactly("client_render");
    }

    @Test
    void rejectsCKeywordExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> return"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Reserved native import symbol: return");
    }

    @Test
    void rejectsReservedCIdentifierExternalSymbol() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> _native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Reserved native import symbol: _native_probe");
    }

    @Test
    void rejectsUnquotedNativeStringArrayElement() throws Exception {
        write("""
            [native]
            libraries = [sample]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration value must be an array of quoted strings: native.libraries");
    }

    @Test
    void rejectsMixedQuotedAndUnquotedNativeStringArrayElements() throws Exception {
        write("""
            [native]
            libraries = ["sample", other]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration value must be an array of quoted strings: native.libraries");
    }

    @Test
    void rejectsScalarNativeStringArrayValue() throws Exception {
        write("""
            [native]
            libraries = "sample"
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration value must be an array of quoted strings: native.libraries");
    }

    @Test
    void returnsImmutableResolvedLists() throws Exception {
        source("immutable.c");
        write("""
            [native]
            sources = ["native/immutable.c"]
            """);
        final NativeInteropConfig config = new NativeInteropResolver().resolve(Map.of(), root);

        assertThatThrownBy(() -> config.linkInputs().sources().add(root)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesNativeInteropConstructorImports() {
        final List<NativeInteropConfig.ImportBinding> imports = new ArrayList<>();
        final NativeInteropConfig config = new NativeInteropConfig(imports, NativeLinkInputs.empty());
        imports.add(new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "()V"), "sample_probe"));

        assertThat(config.imports()).isEmpty();
    }

    @Test
    void filtersImportsInDeclarationOrderAndPreservesLinkInputs() {
        final NativeInteropConfig.ImportBinding first = new NativeInteropConfig.ImportBinding(
            new EntryPoint("sample/NativeApi", "first", "()V"),
            "native_first"
        );
        final NativeInteropConfig.ImportBinding second = new NativeInteropConfig.ImportBinding(
            new EntryPoint("sample/NativeApi", "second", "()V"),
            "native_second"
        );
        final NativeInteropConfig.ImportBinding third = new NativeInteropConfig.ImportBinding(
            new EntryPoint("sample/NativeApi", "third", "()V"),
            "native_third"
        );
        final NativeLinkInputs linkInputs = new NativeLinkInputs(
            List.of(),
            List.of(),
            List.of(),
            List.of("native-support"),
            List.of()
        );
        final NativeInteropConfig config = new NativeInteropConfig(List.of(first, second, third), linkInputs);

        assertThat(config.forReachableMethods(List.of(third.entryPoint(), first.entryPoint())))
            .isEqualTo(new NativeInteropConfig(List.of(first, third), linkInputs));
    }

    @Test
    void rejectsNullReachableMethodProjection() {
        assertThatThrownBy(() -> NativeInteropConfig.empty().forReachableMethods(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void copiesNativeLinkInputsConstructorSources() {
        final List<Path> sources = new ArrayList<>();
        final NativeLinkInputs inputs = new NativeLinkInputs(sources, List.of(), List.of(), List.of(), List.of());
        sources.add(Path.of("native.c"));

        assertThat(inputs.sources()).isEmpty();
    }

    @Test
    void rejectsNullNativeInteropImports() {
        assertThatThrownBy(() -> new NativeInteropConfig(null, NativeLinkInputs.empty()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeInteropLinkInputs() {
        assertThatThrownBy(() -> new NativeInteropConfig(List.of(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkSources() {
        assertThatThrownBy(() -> new NativeLinkInputs(null, List.of(), List.of(), List.of(), List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkObjects() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), null, List.of(), List.of(), List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkLibrarySearchPaths() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), null, List.of(), List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkLibraries() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), List.of(), null, List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkFrameworks() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullImportBindingEntryPoint() {
        assertThatThrownBy(() -> new NativeInteropConfig.ImportBinding(null, "sample_probe"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullImportBindingExternalSymbol() {
        assertThatThrownBy(() -> new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "()V"), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidDirectImportBindingExternalSymbol() {
        assertThatThrownBy(() -> new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "()V"), "sample-symbol"))
            .hasMessage("Invalid native import symbol: sample-symbol");
    }

    @Test
    void rejectsDuplicateDirectImportBindingEntryPoint() {
        final EntryPoint entryPoint = new EntryPoint("sample/NativeApi", "probe", "()V");

        assertThatThrownBy(() -> new NativeInteropConfig(List.of(
            new NativeInteropConfig.ImportBinding(entryPoint, "first_probe"),
            new NativeInteropConfig.ImportBinding(entryPoint, "second_probe")
        ), NativeLinkInputs.empty())).hasMessage("Duplicate native import declaration: sample/NativeApi.probe()V");
    }

    @Test
    void rejectsDuplicateDirectImportBindingExternalSymbol() {
        assertThatThrownBy(() -> new NativeInteropConfig(List.of(
            new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "first", "()V"), "shared_probe"),
            new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "second", "()V"), "shared_probe")
        ), NativeLinkInputs.empty())).hasMessage("Duplicate native import symbol: shared_probe");
    }

    @Test
    void rejectsCollidingNativeWrapperSymbols() {
        final EntryPoint first = new EntryPoint("sample/A$B", "probe", "()V");
        final EntryPoint second = new EntryPoint("sample/A_B", "probe", "()V");
        final String wrapper = BytecodeToIR.symbol(first);

        assertThatThrownBy(() -> new NativeInteropConfig(List.of(
            new NativeInteropConfig.ImportBinding(first, "client_first"),
            new NativeInteropConfig.ImportBinding(second, "client_second")
        ), NativeLinkInputs.empty())).hasMessage(
            "Native import wrapper symbol collision: " + wrapper + " for " + first.display() + " and " + second.display()
        );
    }

    @Test
    void rejectsDuplicateDirectNativeLinkSource() {
        final Path source = Path.of("native/source.c");

        assertThatThrownBy(() -> new NativeLinkInputs(List.of(source, source), List.of(), List.of(), List.of(), List.of()))
            .hasMessage("Duplicate native source: " + source);
    }

    @Test
    void rejectsInvalidDirectNativeLibraryName() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("-sample"), List.of()))
            .hasMessage("Invalid native library name: -sample");
    }

    @Test
    void rejectsInvalidDirectNativeFrameworkName() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(), List.of("-AppKit")))
            .hasMessage("Invalid native framework name: -AppKit");
    }

    @Test
    void rejectsBlankExplicitNativeTarget() {
        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "  "))
            .hasMessage("Native target must not be blank.");
    }

    @Test
    void rejectsFileAsNativeProjectRoot() throws Exception {
        final Path projectFile = Files.writeString(root.resolve("project-file"), "not a project root\n");

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), projectFile))
            .hasMessage("Native project root is not a directory: " + projectFile);
    }

    @Test
    void resolvesPlatformOnlyNativeTargetConfiguration() throws Exception {
        write("""
            [native.target.macos]
            libraries = ["AppKit"]
            """);

        assertThat(new NativeInteropResolver().resolve(Map.of(), root, "macos").linkInputs().libraries())
            .containsExactly("AppKit");
    }

    @Test
    void rejectsNativeTargetSectionWithoutAConfigurationKey() throws Exception {
        write("""
            [native.target]
            macos = ["AppKit"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Unknown native configuration key: native.target.macos");
    }

    @Test
    void rejectsNativeImportWithMultipleBindingArrows() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void -> first -> second"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(classes(nativeMethod("probe", "()V")), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.probe():void -> first -> second");
    }

    @Test
    void rejectsNativeImportWithoutOwnerMethodSeparator() throws Exception {
        write("""
            [native]
            imports = ["NativeApi():void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: NativeApi():void");
    }

    @Test
    void rejectsNativeImportWithoutMethodName() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.():void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.():void");
    }

    @Test
    void rejectsNativeImportWithBlankMethodName() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi. ():void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi. ():void");
    }

    @Test
    void rejectsNativeImportWithEmptyParameterDeclaration() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe(int,,long):void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: int,,long");
    }

    @Test
    void rejectsBlankNativeSourcePath() throws Exception {
        write("""
            [native]
            sources = [""]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Blank native source entry");
    }

    @Test
    void rejectsNativeStringArrayWithoutElementSeparator() throws Exception {
        write("""
            [native]
            libraries = ["first" "second"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration value must be an array of quoted strings: native.libraries");
    }

    @Test
    void rejectsNativeTargetSectionWithLeadingHyphen() throws Exception {
        write("""
            [native.target.-macos]
            libraries = ["AppKit"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: -macos");
    }

    @Test
    void rejectsNativeTargetSectionWithAdjacentHyphens() throws Exception {
        write("""
            [native.target.macos--aarch64]
            libraries = ["AppKit"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: macos--aarch64");
    }

    @Test
    void findsConfiguredNativeImportBinding() {
        final EntryPoint entryPoint = new EntryPoint("sample/NativeApi", "probe", "()V");
        final NativeInteropConfig config = new NativeInteropConfig(
            List.of(new NativeInteropConfig.ImportBinding(entryPoint, "sample_probe")),
            NativeLinkInputs.empty()
        );

        assertThat(config.importBinding(entryPoint)).contains(new NativeInteropConfig.ImportBinding(entryPoint, "sample_probe"));
    }

    @Test
    void reportsMissingNativeImportBinding() {
        final NativeInteropConfig config = new NativeInteropConfig(
            List.of(new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "()V"), "sample_probe")),
            NativeLinkInputs.empty()
        );

        assertThat(config.importBinding(new EntryPoint("sample/NativeApi", "missing", "()V"))).isEmpty();
    }

    @Test
    void reportsEmptyNativeLinkInputs() {
        assertThat(NativeLinkInputs.empty().emptyInputs()).isTrue();
    }

    @Test
    void reportsNativeSourcesAsConfiguredLinkInputs() {
        assertThat(new NativeLinkInputs(List.of(Path.of("native/source.c")), List.of(), List.of(), List.of(), List.of()).emptyInputs())
            .isFalse();
    }

    @Test
    void reportsNativeObjectsAsConfiguredLinkInputs() {
        assertThat(new NativeLinkInputs(List.of(), List.of(Path.of("native/object.o")), List.of(), List.of(), List.of()).emptyInputs())
            .isFalse();
    }

    @Test
    void reportsNativeLibrarySearchPathsAsConfiguredLinkInputs() {
        assertThat(new NativeLinkInputs(List.of(), List.of(), List.of(Path.of("native/lib")), List.of(), List.of()).emptyInputs())
            .isFalse();
    }

    @Test
    void reportsNativeLibrariesAsConfiguredLinkInputs() {
        assertThat(new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("native"), List.of()).emptyInputs())
            .isFalse();
    }

    @Test
    void reportsNativeFrameworksAsConfiguredLinkInputs() {
        assertThat(new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(), List.of("AppKit")).emptyInputs())
            .isFalse();
    }

    @Test
    void acceptsNativeLibraryNameWithPortablePunctuation() {
        assertThat(new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("c++-runtime.1"), List.of()).libraries())
            .containsExactly("c++-runtime.1");
    }

    @Test
    void rejectsNativeImportWithoutBindingArrow() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():void"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.probe():void");
    }

    @Test
    void rejectsNativeImportWithoutParameterList() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe:void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.probe:void");
    }

    @Test
    void rejectsNativeImportWithoutClosingParameterList() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe(:void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.probe(:void");
    }

    @Test
    void rejectsNativeImportWithTextBetweenParametersAndReturnType() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe()x:void -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.probe()x:void");
    }

    @Test
    void rejectsNativeImportWithoutReturnType() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe(): -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native import declaration: sample.NativeApi.probe():");
    }

    @Test
    void rejectsNativeImportWithUnqualifiedReferenceType() throws Exception {
        write("""
            [native]
            imports = ["sample.NativeApi.probe():Object -> native_probe"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Unsupported native import declaration type: Object");
    }

    @Test
    void rejectsNativeStringArrayWithTrailingText() throws Exception {
        write("""
            [native]
            libraries = ["sample"] trailing
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Native configuration value must be an array of quoted strings: native.libraries");
    }

    @Test
    void rejectsEscapedBackslashInNativeStringArray() throws Exception {
        write("""
            [native]
            libraries = ["sample\\\\name"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native library name: sample\\\\name");
    }

    @Test
    void rejectsEscapedQuoteInNativeStringArray() throws Exception {
        write("[native]\nlibraries = [\"sample\\\"name\"]\n");

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root))
            .hasMessage("Invalid native library name: sample\\\"name");
    }

    @Test
    void acceptsTabWhitespaceInNativeStringArray() throws Exception {
        write("""
            [native]
            libraries = [\t"sample"\t]
            """);

        assertThat(new NativeInteropResolver().resolve(Map.of(), root).linkInputs().libraries()).containsExactly("sample");
    }

    @Test
    void rejectsNativeTargetSectionWithTrailingHyphen() throws Exception {
        write("""
            [native.target.macos-]
            libraries = ["AppKit"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: macos-");
    }

    @Test
    void rejectsNativeTargetSectionWithUppercaseCharacter() throws Exception {
        write("""
            [native.target.macOS]
            libraries = ["AppKit"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "macos-aarch64"))
            .hasMessage("Invalid native target section id: macOS");
    }

    @Test
    void rejectsNonCanonicalNativeTargetSectionWithNumericSuffix() throws Exception {
        write("""
            [native.target.linux2]
            libraries = ["sample"]
            """);

        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, "linux2"))
            .hasMessage("Invalid native target section id: linux2");
    }

    @Test
    void rejectsEmptyDirectNativeImportSymbol() {
        assertThatThrownBy(() -> new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "()V"), ""))
            .hasMessage("Invalid native import symbol: ");
    }

    @Test
    void rejectsDigitLeadingDirectNativeImportSymbol() {
        assertThatThrownBy(() -> new NativeInteropConfig.ImportBinding(new EntryPoint("sample/NativeApi", "probe", "()V"), "2probe"))
            .hasMessage("Invalid native import symbol: 2probe");
    }

    @Test
    void rejectsEmptyDirectNativeLibraryName() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(""), List.of()))
            .hasMessage("Invalid native library name: ");
    }

    @Test
    void acceptsUnderscoreAndDigitInDirectNativeLibraryName() {
        assertThat(new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("_native2"), List.of()).libraries())
            .containsExactly("_native2");
    }

    @Test
    void rejectsDirectNativeLibraryNameWithPunctuationAfterPrefix() {
        assertThatThrownBy(() -> new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("native:"), List.of()))
            .hasMessage("Invalid native library name: native:");
    }

    @Test
    void rejectsNullResolverClasses() {
        assertThatThrownBy(() -> new NativeInteropResolver().resolve(null, root))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("classes");
    }

    @Test
    void rejectsNullResolverRoot() {
        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("root");
    }

    @Test
    void rejectsNullResolverTarget() {
        assertThatThrownBy(() -> new NativeInteropResolver().resolve(Map.of(), root, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("target");
    }

    @Test
    void rejectsNullNativeInteropImportElement() {
        assertThatThrownBy(() -> new NativeInteropConfig(
            java.util.Collections.singletonList(null),
            NativeLinkInputs.empty()
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkSourceElement() {
        assertThatThrownBy(() -> new NativeLinkInputs(
            java.util.Collections.singletonList(null),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNativeLinkLibraryElement() {
        assertThatThrownBy(() -> new NativeLinkInputs(
            List.of(),
            List.of(),
            List.of(),
            java.util.Collections.singletonList(null),
            List.of()
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullReachableMethodElement() {
        assertThatThrownBy(() -> NativeInteropConfig.empty().forReachableMethods(
            java.util.Collections.singletonList(null)
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullImportBindingLookup() {
        assertThatThrownBy(() -> NativeInteropConfig.empty().importBinding(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("entryPoint");
    }

    private void write(final String content) throws Exception {
        Files.writeString(root.resolve("javan.toml"), content);
    }

    private static void createSymbolicLinkOrSkip(final Path link, final Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (final UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        } catch (final FileSystemException exception) {
            assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        }
    }

    private Path source(final String name) throws Exception {
        final Path source = root.resolve("native").resolve(name);
        Files.createDirectories(source.getParent());
        return Files.writeString(source, "int native_entry(void) { return 0; }\n").toAbsolutePath().normalize();
    }

    private Path object(final String name) throws Exception {
        final Path object = root.resolve("native").resolve(name);
        Files.createDirectories(object.getParent());
        return Files.writeString(object, "object\n").toAbsolutePath().normalize();
    }

    private Path directory(final String name) throws Exception {
        return Files.createDirectories(root.resolve("native").resolve(name)).toAbsolutePath().normalize();
    }

    private static Map<String, ClassFile> classes(final MethodInfo method) {
        final ClassFile type = type(method);
        return Map.of(type.name(), type);
    }

    private static ClassFile type(final MethodInfo... methods) {
        return type("sample/NativeApi", methods);
    }

    private static ClassFile type(final String name, final MethodInfo... methods) {
        return new ClassFile(
            69,
            name,
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(methods),
            Path.of("sample/NativeApi.class"),
            true
        );
    }

    private static MethodInfo nativeMethod(final String name, final String descriptor) {
        return method(0x0108, name, descriptor);
    }

    private static MethodInfo method(final String name, final String descriptor) {
        return method(0x0008, name, descriptor);
    }

    private static MethodInfo method(final int accessFlags, final String name, final String descriptor) {
        return new MethodInfo(accessFlags, name, descriptor, Optional.empty());
    }
}
