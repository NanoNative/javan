package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliConcurrentKeySetViewIntegrationTest extends CliIntegrationSupport {
    @Test
    void supportsTheConcreteNoArgumentKeySetViewAsABackedSet() throws Exception {
        final Path project = project("concurrent-keyset-covariant");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                public static void main(String[] args) {
                    var map = new ConcurrentHashMap<String, String>(); map.put("a", "one");
                    var keys = map.keySet();
                    System.out.println(keys.contains("a"));
                    map.put("b", "two"); System.out.println(keys.size());
                    System.out.println(keys.remove("a")); System.out.println(map.containsKey("a"));
                    var iterator = keys.iterator(); System.out.println(iterator.next()); iterator.remove();
                    System.out.println(map.isEmpty());
                    map.put("c", "three"); System.out.println(keys.isEmpty()); keys.clear();
                    System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/concurrent-keyset-covariant").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n2\ntrue\nfalse\nb\ntrue\nfalse\ntrue\n");
    }

    @Test
    void rejectsAddingToTheNoArgumentKeySetView() throws Exception {
        final Path project = project("concurrent-keyset-no-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                public static void main(String[] args) {
                    var map = new ConcurrentHashMap<String, String>(); var keys = map.keySet();
                    try { keys.add("x"); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                    System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/concurrent-keyset-no-add").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("unsupported\ntrue\n");
    }
    @Test
    void rejectsNullKeysInTheConcreteView() throws Exception {
        final Path project = project("concurrent-keyset-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                public static void main(String[] args) {
                    var keys = new ConcurrentHashMap<String, String>().keySet();
                    try { keys.contains(null); } catch (NullPointerException expected) { System.out.println("contains-null"); }
                    try { keys.remove(null); } catch (NullPointerException expected) { System.out.println("remove-null"); }
                    try { keys.add(null); } catch (UnsupportedOperationException expected) { System.out.println("add-unsupported"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/concurrent-keyset-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("contains-null\nremove-null\nadd-unsupported\n");
    }

    @Test
    void transportsNullReceiversForConcreteViewOperations() throws Exception {
        final Path project = project("concurrent-keyset-null-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                public static void main(String[] args) {
                    ConcurrentHashMap.KeySetView<String, String> keys = null;
                    try { keys.contains("x"); } catch (NullPointerException expected) { System.out.println("contains"); }
                    try { keys.remove("x"); } catch (NullPointerException expected) { System.out.println("remove"); }
                    try { keys.add("x"); } catch (NullPointerException expected) { System.out.println("add"); }
                    try { keys.size(); } catch (NullPointerException expected) { System.out.println("size"); }
                    try { keys.iterator(); } catch (NullPointerException expected) { System.out.println("iterator"); }
                    try { keys.isEmpty(); } catch (NullPointerException expected) { System.out.println("isEmpty"); }
                    try { keys.clear(); } catch (NullPointerException expected) { System.out.println("clear"); }
                    ConcurrentHashMap<String, String> map = null;
                    try { map.keySet(); } catch (NullPointerException expected) { System.out.println("keySet"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/concurrent-keyset-null-receiver").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("contains\nremove\nadd\nsize\niterator\nisEmpty\nclear\nkeySet\n");
    }

}
