package javan.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class JavanModuleParserTest {
    @TempDir
    private Path tempDir;

    @Test
    void readReturnsAbsentWhenModuleFileIsMissing() throws Exception {
        final JavanModule module = new JavanModuleParser().read(tempDir);

        assertThat(module.present()).isFalse();
        assertThat(module.dependencies()).isEmpty();
    }

    @Test
    void parseReadsScopedLocalDependencies() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            module com.acme.app
            java 25
            require libs/main.jar
            require test "libs/test classes"
            require tool tools/codegen.jar # tool only
            """);

        assertThat(module.present()).isTrue();
        assertThat(module.moduleName()).isEqualTo("com.acme.app");
        assertThat(module.javaVersion()).isEqualTo("25");
        assertThat(module.warnings()).isEmpty();
        assertThat(module.dependencies()).hasSize(3);
        assertThat(module.dependencies().get(0).scope()).isEqualTo("main");
        assertThat(module.dependencies().get(0).path()).contains(tempDir.resolve("libs/main.jar").toAbsolutePath().normalize());
        assertThat(module.dependencies().get(1).scope()).isEqualTo("test");
        assertThat(module.dependencies().get(1).path()).contains(tempDir.resolve("libs/test classes").toAbsolutePath().normalize());
        assertThat(module.dependencies().get(2).scope()).isEqualTo("tool");
    }

    @Test
    void parseKeepsCoordinateDependenciesUnsupportedButVisible() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            require com.example:native-lib 2026.1
            require test com.example:test-lib 2026.2
            """);

        assertThat(module.warnings()).isEmpty();
        assertThat(module.dependencies()).hasSize(2);
        assertThat(module.dependencies().get(0).kind()).isEqualTo("coordinate");
        assertThat(module.dependencies().get(0).scope()).isEqualTo("main");
        assertThat(module.dependencies().get(0).notation()).isEqualTo("com.example:native-lib 2026.1");
        assertThat(module.dependencies().get(1).scope()).isEqualTo("test");
    }

    @Test
    void parseWarnsForUnknownDirectiveAndUnknownScopeShape() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            repository central
            require runtime ./libs/app.jar
            """);

        assertThat(module.dependencies()).isEmpty();
        assertThat(module.warnings()).containsExactly(
            "javan.mod line 1: unsupported javan.mod directive: repository",
            "javan.mod line 2: unknown dependency scope or unsupported require form"
        );
    }

    @Test
    void parseWarnsForMalformedModuleJavaAndRequireLines() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            module com.acme app
            java
            require
            require test
            require main one two three
            """);

        assertThat(module.dependencies()).isEmpty();
        assertThat(module.warnings()).containsExactly(
            "javan.mod line 1: module expects exactly one name",
            "javan.mod line 2: java expects exactly one feature version",
            "javan.mod line 3: require expects a dependency",
            "javan.mod line 4: require test expects a dependency",
            "javan.mod line 5: require supports local paths or coordinate plus version"
        );
    }

    @Test
    void parseTreatsPathLikeColonNotationAsLocal() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            require ./libs/a:b.jar
            require dir/nested.jar
            """);

        assertThat(module.warnings()).isEmpty();
        assertThat(module.dependencies()).hasSize(2);
        assertThat(module.dependencies().get(0).kind()).isEqualTo("local");
        assertThat(module.dependencies().get(1).kind()).isEqualTo("local");
    }

    @Test
    void parseTreatsBackslashPathAsLocal() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            require libs\\a:b.jar
            """);

        assertThat(module.warnings()).isEmpty();
        assertThat(module.dependencies()).hasSize(1);
        assertThat(module.dependencies().get(0).kind()).isEqualTo("local");
    }

    @Test
    void parseKeepsHashInsideQuotedPath() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            require "libs/a#b.jar" # real comment
            """);

        assertThat(module.warnings()).isEmpty();
        assertThat(module.dependencies()).hasSize(1);
        assertThat(module.dependencies().get(0).notation()).isEqualTo("libs/a#b.jar");
    }

    @Test
    void parseReadsQuotedLicensePolicyRules() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            license allow "Apache License, Version 2.0"
            license deny "GNU General Public License, version 3"
            """);

        assertThat(module.warnings()).isEmpty();
        assertThat(module.licensePolicy().rules()).containsExactly(
            new LicensePolicy.Rule("allow", "Apache License, Version 2.0", 1),
            new LicensePolicy.Rule("deny", "GNU General Public License, version 3", 2)
        );
        assertThat(module.licensePolicy().decide(new ArtifactMetadata.License(
            "Apache License, Version 2.0", "Apache License, Version 2.0", "", "pom.xml", "dependency.pom"
        )).status()).isEqualTo("allowed");
        assertThat(module.licensePolicy().decide(new ArtifactMetadata.License(
            "GNU General Public License, version 3", "GNU General Public License, version 3", "", "pom.xml", "dependency.pom"
        )).status()).isEqualTo("blocked");
    }

    @Test
    void parseRejectsMalformedOrUnknownLicensePolicyRules() {
        final JavanModule module = new JavanModuleParser().parse(tempDir, """
            license allow
            license review "Apache License, Version 2.0"
            license deny unknown
            """);

        assertThat(module.licensePolicy().rules()).isEmpty();
        assertThat(module.warnings()).containsExactly(
            "javan.mod line 1: license expects allow or deny plus one exact license identifier",
            "javan.mod line 2: license action must be allow or deny",
            "javan.mod line 3: license policy cannot target unknown metadata"
        );
    }

    @Test
    void licensePolicyUsesExactMetadataAndLetsDenyWin() {
        final ArtifactMetadata.License apache = new ArtifactMetadata.License(
            "Apache License, Version 2.0", "Apache License, Version 2.0", "", "pom.xml", "dependency.pom"
        );
        final LicensePolicy policy = new LicensePolicy(List.of(
            new LicensePolicy.Rule("allow", apache.id(), 2),
            new LicensePolicy.Rule("deny", apache.id(), 3)
        ));

        assertThat(LicensePolicy.empty().decide(apache).status()).isEqualTo("known");
        assertThat(LicensePolicy.empty().decide(new ArtifactMetadata.License(
            "unknown", "unknown", "", "none", ""
        )).status()).isEqualTo("warning");
        assertThat(policy.decide(apache)).satisfies(decision -> {
            assertThat(decision.status()).isEqualTo("blocked");
            assertThat(decision.source()).isEqualTo("javan.mod:3");
        });
    }
}
