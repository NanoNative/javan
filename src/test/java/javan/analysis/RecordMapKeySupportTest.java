package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.ClassFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class RecordMapKeySupportTest {
    @TempDir
    private Path tempDir;

    @Test
    void admitsOnlyCanonicalMethodsWithPrimitiveAndStringComponents() throws Exception {
        final Path source = tempDir.resolve("Keys.java");
        Files.writeString(source, """
            record Safe(String name, int number) { }
            record Reference(Object value) { }
            record CustomEquals(String value) {
                public boolean equals(Object other) { System.out.println("side effect"); return true; }
            }
            record CustomHash(String value) {
                public int hashCode() { System.out.println("side effect"); return 1; }
            }
            record SynchronizedHash(String value) {
                public synchronized int hashCode() { return 1; }
            }
            """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", tempDir.toString(), source.toString())).isZero();
        final ClassFileReader reader = new ClassFileReader();
        final ClassFile safe = reader.read(Files.readAllBytes(tempDir.resolve("Safe.class")), source);
        assertThat(RecordMapKeySupport.isSafe(safe)).isTrue();
        for (final String name : new String[] {"Reference", "CustomEquals", "CustomHash", "SynchronizedHash"}) {
            final ClassFile type = reader.read(Files.readAllBytes(tempDir.resolve(name + ".class")), source);
            assertThat(RecordMapKeySupport.isSafe(type)).as(name).isFalse();
        }
    }
}
