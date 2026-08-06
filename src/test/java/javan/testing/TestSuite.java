package javan.testing;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Test suites used to select related evidence locally and in CI. */
public final class TestSuite {
    private TestSuite() {
    }

    /** Generates, compiles, and executes native C through the JavaN CLI. */
    @Documented
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    @Tag("native")
    public @interface NativeTest {
    }

    /** Builds and verifies distributable or self-hosted JavaN packages. */
    @Documented
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    @Tag("packaging")
    public @interface PackagingTest {
    }

    /** Uses external probe artifacts, toolchains, or services. */
    @Documented
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    @Tag("external")
    public @interface ExternalTest {
    }

    /** Runs portable JVM-only behavior on every enabled CI operating system and architecture. */
    @Documented
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    @Tag("platform")
    public @interface PlatformTest {
    }

    /**
     * Selects portable runtime checks that CI repeats on Windows while platform support is
     * incomplete. This is a temporary compatibility proof, not a Windows-only test category;
     * remove it when the complete relevant suite runs on every supported Windows target.
     */
    @Documented
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    @Tag("windows-compatibility")
    public @interface WindowsCompatibilityProof {
    }
}
