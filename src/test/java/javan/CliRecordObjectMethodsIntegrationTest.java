package javan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliRecordObjectMethodsIntegrationTest extends CliIntegrationSupport {
    private Path project;
    private Path jvmClasses;
    private Path nativeBinary;

    @BeforeAll
    void buildRealJavacFixture(@TempDir final Path fixtureTempDir) throws Exception {
        tempDir = fixtureTempDir;
        project = project("record-object-methods");
        writeJava(project, "com.acme.Main", source());
        jvmClasses = project.resolve("jvm-classes");
        Files.createDirectories(jvmClasses);
        requireSuccess(processSlow(project, List.of(
            CliTestHarness.currentJavacCommand(),
            "-d",
            jvmClasses.toString(),
            project.resolve("src/main/java/com/acme/Main.java").toString()
        )));
        final CliRun build = runSlow(tempDir, "build", project.toString());
        if (build.exitCode() != 0) {
            throw new IllegalStateException(build.stderr());
        }
        nativeBinary = project.resolve(".javan/bin/record-object-methods");
    }

    @Test
    void booleanEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("boolean")).containsExactly("true\n1231\n", "true\n1231\n");
    }

    @Test
    void byteEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("byte")).containsExactly("true\n-7\n", "true\n-7\n");
    }

    @Test
    void shortEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("short")).containsExactly("true\n32000\n", "true\n32000\n");
    }

    @Test
    void charEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("char")).containsExactly("true\n90\n", "true\n90\n");
    }

    @Test
    void intEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("int")).containsExactly("true\n123456789\n", "true\n123456789\n");
    }

    @Test
    void longEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("long")).containsExactly("true\n0\n", "true\n0\n");
    }

    @Test
    void floatEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("float")).containsExactly("true\n1069547520\n", "true\n1069547520\n");
    }

    @Test
    void doubleEqualsAndHashCodeMatchJdk() {
        assertThat(outputs("double")).containsExactly("true\n1073217536\n", "true\n1073217536\n");
    }

    @Test
    void floatNaNPayloadsUseCanonicalEqualityAndHashCode() {
        assertThat(outputs("float-nan"))
            .containsExactly("true\n2143289344\n2143289344\n", "true\n2143289344\n2143289344\n");
    }

    @Test
    void doubleNaNPayloadsUseCanonicalEqualityAndHashCode() {
        assertThat(outputs("double-nan"))
            .containsExactly("true\n2146959360\n2146959360\n", "true\n2146959360\n2146959360\n");
    }

    @Test
    void floatSignedZeroRemainsDistinct() {
        assertThat(outputs("float-zero"))
            .containsExactly("false\n0\n-2147483648\n", "false\n0\n-2147483648\n");
    }

    @Test
    void doubleSignedZeroRemainsDistinct() {
        assertThat(outputs("double-zero"))
            .containsExactly("false\n0\n-2147483648\n", "false\n0\n-2147483648\n");
    }

    @Test
    void nullReferenceComponentUsesZeroHashCode() {
        assertThat(outputs("null")).containsExactly("true\n0\n", "true\n0\n");
    }

    @Test
    void nonIdenticalStringsUseContentEqualityAndHashCode() {
        assertThat(outputs("string")).containsExactly("true\n3522662\n", "true\n3522662\n");
    }

    @Test
    void referenceArraysUseIdentityEquality() {
        assertThat(outputs("array")).containsExactly("true\nfalse\ntrue\n", "true\nfalse\ntrue\n");
    }

    @Test
    void enumComponentsUseIdentityEqualityAndHashCode() {
        assertThat(outputs("enum")).containsExactly("true\nfalse\ntrue\n", "true\nfalse\ntrue\n");
    }

    @Test
    void nestedRecordsUseReachableObjectMethods() {
        assertThat(outputs("nested")).containsExactly("true\n1842462192\n", "true\n1842462192\n");
    }

    @Test
    void concreteFinalComponentsUseReachableObjectMethods() {
        assertThat(outputs("concrete")).containsExactly("true\n48\n", "true\n48\n");
    }

    @Test
    void parameterizedFinalComponentsUseErasedClosedWorldObjectMethods() throws Exception {
        final String projectName = "parameterized-final-record-component";
        final Path genericProject = project(projectName);
        writeJava(genericProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                record Holder(Box<String> value) {
                }

                static final class Box<T> {
                    private final int value;

                    Box(final int value) {
                        this.value = value;
                    }

                    @Override
                    public boolean equals(final Object other) {
                        return other instanceof Box<?> candidate && value == candidate.value;
                    }

                    @Override
                    public int hashCode() {
                        return value;
                    }
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Holder(new Box<String>(7)).equals(
                        new Holder(new Box<String>(7))
                    ));
                    System.out.println(new Holder(new Box<String>(7)).equals(
                        new Holder(new Box<String>(8))
                    ));
                    System.out.println(new Holder(new Box<String>(7)).hashCode());
                }
            }
            """);
        final String jvmOutput = runJvm(genericProject, "com.acme.Main");
        final CliRun build = runSlow(tempDir, "build", genericProject.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? processSlow(genericProject, List.of(
                genericProject.resolve(".javan/bin/" + projectName).toString()
            ))
            : new ProcessResult(-1, "", "native build did not run");

        assertThat(build.exitCode() + "\n" + nativeRun.exitCode() + "\n" + nativeRun.stdout())
            .as(build.stderr() + "\n" + nativeRun.stderr())
            .isEqualTo("0\n0\n" + jvmOutput);
    }

    @Test
    void sealedValueLeafUsesValueEquality() {
        assertThat(outputs("sealed-value-equality")).containsExactly("true\n", "true\n");
    }

    @Test
    void sealedIdentityLeavesRemainDistinct() {
        assertThat(outputs("sealed-distinct-identity")).containsExactly("false\n", "false\n");
    }

    @Test
    void sealedSharedIdentityRemainsEqual() {
        assertThat(outputs("sealed-shared-identity")).containsExactly("true\n", "true\n");
    }

    @Test
    void sealedValueAndIdentityLeavesRemainUnequal() {
        assertThat(outputs("sealed-cross-leaf")).containsExactly("false\n", "false\n");
    }

    @Test
    void sealedValueLeafUsesValueHashCode() {
        assertThat(outputs("sealed-value-hash")).containsExactly("7\n", "7\n");
    }

    @Test
    void sealedInheritedValueLeafUsesCustomInheritedEquality() {
        assertThat(outputs("sealed-inherited-equality")).containsExactly("true\n", "true\n");
    }

    @Test
    void sealedInheritedValueLeafUsesCustomInheritedHashCode() {
        assertThat(outputs("sealed-inherited-hash")).containsExactly("7\n", "7\n");
    }

    @Test
    void sealedIdentityLeafHashCodeIsStableForOneObject() {
        assertThat(outputs("sealed-repeated-identity-hash")).containsExactly("true\n", "true\n");
    }

    @Test
    void sealedNullLeafUsesNullEquality() {
        assertThat(outputs("sealed-null-equality")).containsExactly("true\n", "true\n");
    }

    @Test
    void sealedNullLeafUsesZeroHashCode() {
        assertThat(outputs("sealed-null-hash")).containsExactly("0\n", "0\n");
    }

    @Test
    void recordEqualsAcceptsSelf() {
        assertThat(outputs("self")).containsExactly("true\n", "true\n");
    }

    @Test
    void recordEqualsRejectsDifferentRecordType() {
        assertThat(outputs("type-mismatch")).containsExactly("false\n", "false\n");
    }

    @Test
    void listComponentsUseElementEqualityAndThirtyOneFoldHashCode() {
        assertThat(outputs("list")).containsExactly("true\n4066\n", "true\n4066\n");
    }

    @Test
    void arrayListComponentsUseElementEqualityAndThirtyOneFoldHashCode() {
        assertThat(outputs("array-list")).containsExactly("true\n4066\n", "true\n4066\n");
    }

    @Test
    void mapComponentsUseEntryEqualityAndOrderIndependentHashCode() {
        assertThat(outputs("map")).containsExactly(
            "true\nfalse\nfalse\nfalse\n-1004803451\n-1004803451\n",
            "true\nfalse\nfalse\nfalse\n-1004803451\n-1004803451\n"
        );
    }

    @Test
    void nullMapComponentsUseNullEqualityAndZeroHashCode() {
        assertThat(outputs("null-map")).containsExactly("true\n0\n", "true\n0\n");
    }

    @Test
    void emptyMapComponentsUseValueEqualityAndZeroHashCode() {
        assertThat(outputs("empty-map")).containsExactly("true\n0\n", "true\n0\n");
    }

    @Test
    void recordsContainingStringMapsComposeInsideLists() {
        assertThat(outputs("map-record-list"))
            .containsExactly("true\n112004941\n", "true\n112004941\n");
    }

    @Test
    void unsafeRawMapInsertionFailsWithStableRuntimeDiagnostic() throws Exception {
        assertThat(unsafeRawMapRuntimeFailure(
            "unsafe-raw-map-value-hash-runtime",
            "raw.put(\"safe\", Integer.valueOf(7));",
            "System.out.println(new Strings(unsafe).hashCode());"
        )).contains("record generic value does not match declared shape");
    }

    @Test
    void unsafeRawMapKeyFailsDuringEqualsWithStableRuntimeDiagnostic() throws Exception {
        assertThat(unsafeRawMapRuntimeFailure(
            "unsafe-raw-map-key-equals-runtime",
            "raw.put(Integer.valueOf(7), \"safe\");",
            "System.out.println(new Strings(unsafe).equals(new Strings(Map.of(\"safe\", \"safe\"))));"
        )).contains("record generic value does not match declared shape");
    }

    @Test
    void listsOfFinalValueRecordsUseElementObjectMethods() {
        assertThat(outputs("record-list")).containsExactly("true\n3752\n", "true\n3752\n");
    }

    @Test
    void listsOfBoxedIntegersUseValueSemantics() {
        assertThat(outputs("boxed-list")).containsExactly("true\n1186\n", "true\n1186\n");
    }

    @Test
    void listsOfEnumsUseIdentityElementSemantics() {
        assertThat(outputs("enum-list")).containsExactly("true\ntrue\n", "true\ntrue\n");
    }

    @Test
    void listsOfPrimitiveArraysUseIdentityElementSemantics() {
        assertThat(outputs("array-element-list"))
            .containsExactly("true\nfalse\ntrue\n", "true\nfalse\ntrue\n");
    }

    @Test
    void listsOfReferenceArraysAllowCovariantRuntimeArrays() {
        assertThat(outputs("reference-array-element-list"))
            .containsExactly("true\nfalse\ntrue\n", "true\nfalse\ntrue\n");
    }

    @Test
    void nestedListsOfFinalRecordsUseRecursiveValueSemantics() {
        assertThat(outputs("nested-list")).containsExactly("true\n3783\n", "true\n3783\n");
    }

    @Test
    void nullListElementsUseNullEqualityAndZeroHash() {
        assertThat(outputs("null-list-element")).containsExactly("true\n31\n", "true\n31\n");
    }

    @Test
    void listObjectComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("list-object", """
            import java.util.List;
            import java.util.Optional;

            record Unsafe(List<Object> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(
                        new Unsafe(List.of(Optional.of("x"))).equals(
                            new Unsafe(List.of(Optional.of("x")))
                        )
                    );
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void rawListComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("raw-list", """
            import java.util.List;

            record Unsafe(List value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(List.of("x")).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void wildcardListComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("wildcard-list", """
            import java.util.List;

            record Unsafe(List<?> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(List.of("x")).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void typeVariableListComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("type-variable-list", """
            import java.util.List;

            record Unsafe<T>(List<T> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe<>(List.of("x")).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void typeVariableArrayComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("type-variable-array", """
            record Unsafe<T>(T[] value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe<String>(new String[] {"x"}).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void wildcardListArrayComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("wildcard-list-array", """
            import java.util.List;

            record Unsafe(List<?>[] value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(new List<?>[] {List.of("x")}).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void typeVariableParameterizedFinalComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("type-variable-parameterized-final", """
            final class Box<T> {
            }

            record Unsafe<T>(Box<T> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe<String>(new Box<String>()).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void wildcardParameterizedFinalComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("wildcard-parameterized-final", """
            final class Box<T> {
            }

            record Unsafe(Box<?> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(new Box<String>()).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void boundedWildcardParameterizedFinalComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("bounded-wildcard-parameterized-final", """
            final class Box<T> {
            }

            record Unsafe(Box<? extends CharSequence> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(new Box<String>()).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void nestedWildcardParameterizedFinalComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("nested-wildcard-parameterized-final", """
            import java.util.List;

            final class Box<T> {
            }

            record Unsafe(Box<List<?>> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(new Box<List<?>>()).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void parameterizedInterfaceComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("parameterized-interface-component", """
            interface Carrier<T> {
            }

            record Unsafe(Carrier<String> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(null).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "final closed-world class");
    }

    @Test
    void nestedUnsupportedRecordLeafIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("nested-unsupported-list", """
            import java.util.List;
            import java.util.Map;

            record Unsupported(Map<String, Integer> value) {
            }

            record Unsafe(List<Unsupported> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(
                        new Unsafe(List.of(new Unsupported(Map.of("value", Integer.valueOf(7))))).hashCode()
                    );
                }
            }
        """)).contains("unsupported record component type");
    }

    @Test
    void rawMapComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("raw-map", """
            import java.util.Map;

            record Unsafe(Map value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(Map.of("key", "value")).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void wildcardStringMapComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("wildcard-string-map", """
            import java.util.Map;

            record Unsafe(Map<? extends String, String> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(Map.of("key", "value")).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void concreteHashMapComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("concrete-hash-map", """
            import java.util.HashMap;
            import java.util.Map;

            record Unsafe(HashMap<String, String> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(new HashMap<>(Map.of("key", "value"))).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void nestedStringMapListComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("nested-string-map-list", """
            import java.util.List;
            import java.util.Map;

            record Unsafe(List<Map<String, String>> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Unsafe(List.of(Map.of("key", "value"))).hashCode());
                }
            }
            """)).contains("unsupported record component type");
    }

    @Test
    void unsafeRawListInsertionFailsWithStableRuntimeDiagnostic() throws Exception {
        assertThat(unsafeRawListRuntimeFailure(
            "unsafe-raw-list-hash-runtime",
            "System.out.println(new Strings(unsafe).hashCode());"
        ))
            .contains("record generic value does not match declared shape");
    }

    @Test
    void unsafeRawListInsertionFailsBeforeEqualsReturns() throws Exception {
        assertThat(unsafeRawListRuntimeFailure(
            "unsafe-raw-list-equals-runtime",
            "System.out.println(new Strings(unsafe).equals(new Strings(List.of(\"x\"))));"
        ))
            .contains("record generic value does not match declared shape");
    }

    @Test
    void unsafeLaterRawListInsertionFailsBeforeEarlierEqualsMismatchReturns() {
        assertThat(nativeFailure("unsafe-later-list-component"))
            .contains("record generic value does not match declared shape");
    }

    @Test
    void unsafeNestedListCycleFailsWithStableRuntimeDiagnostic() {
        assertThat(nativeFailure("unsafe-nested-list-cycle"))
            .contains("record generic value does not match declared shape");
    }

    @Test
    void customListCarrierFailsWithStableRuntimeDiagnostic() throws Exception {
        assertThat(customListCarrierFailure())
            .contains("record generic value does not match declared shape");
    }

    @Test
    void declaredObjectComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedRecordBuild("unsafe-object", "Object", "\"value\""))
            .contains("unsupported record component type", "java/lang/Object");
    }

    @Test
    void declaredInterfaceComponentIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedRecordBuild("unsafe-interface", "CharSequence", "\"value\""))
            .contains("unsupported record component type", "java/lang/CharSequence", "final closed-world class");
    }

    @Test
    void nonFinalSealedLeafIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("non-final-sealed-leaf", """
            sealed interface Part permits Value {
            }

            non-sealed class Value implements Part {
            }

            record Holder(Part value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Holder(new Value()).hashCode());
                }
            }
            """)).contains(
                "error[JAVAN030]",
                "unsupported record component type",
                "direct sealed interface"
            );
    }

    @Test
    void incompleteDirectSealedClosureIsRejectedByJavan() throws Exception {
        final Path dependency = dependencyJar("incomplete-sealed-closure", "com.acme.Part", """
            package com.acme;

            sealed interface Part permits Value, Missing {
            }

            final class Value implements Part {
            }

            final class Missing implements Part {
            }
            """);
        final Path extractedDependency = tempDir.resolve("incomplete-sealed-closure-extracted");
        final Path incompleteDependency = tempDir.resolve("incomplete-sealed-closure.jar");
        final ProcessResult extractDependency = process(tempDir, List.of(
            CliTestHarness.currentJarCommand(),
            "--extract",
            "--file",
            dependency.toString(),
            "--dir",
            extractedDependency.toString()
        ));
        if (extractDependency.exitCode() != 0) {
            throw new IllegalStateException(extractDependency.stderr());
        }
        Files.delete(extractedDependency.resolve("com/acme/Missing.class"));
        final ProcessResult repackDependency = process(tempDir, List.of(
            CliTestHarness.currentJarCommand(),
            "--create",
            "--file",
            incompleteDependency.toString(),
            "-C",
            extractedDependency.toString(),
            "."
        ));
        if (repackDependency.exitCode() != 0) {
            throw new IllegalStateException(repackDependency.stderr());
        }
        final Path rejectedProject = project("incomplete-direct-sealed-closure");
        writeJava(rejectedProject, "com.acme.Main", """
            package com.acme;

            record Holder(Part value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Holder(null).hashCode());
                }
            }
            """);

        final CliRun build = runSlow(
            tempDir,
            "build",
            rejectedProject.toString(),
            "--classpath",
            incompleteDependency.toString()
        );

        assertThat(build.exitCode() + "\n" + build.stderr()).contains(
            "2\nerror[JAVAN030]",
            "unsupported record component type",
            "direct sealed interface"
        );
    }

    @Test
    void listOfSealedInterfaceIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("list-of-sealed-interface", """
            import java.util.List;

            sealed interface Part permits Value {
            }

            record Value(int value) implements Part {
            }

            record Holder(List<Part> value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Holder(List.of(new Value(7))).hashCode());
                }
            }
        """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void sealedInterfaceWithEnumLeafIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("sealed-enum-leaf", """
            sealed interface Part permits Value {
            }

            enum Value implements Part {
                INSTANCE
            }

            record Holder(Part value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Holder(Value.INSTANCE).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void recursiveSealedHierarchyIsRejectedBeforeCodeGeneration() throws Exception {
        assertThat(rejectedGenericRecordBuild("recursive-sealed-hierarchy", """
            sealed interface Part permits Middle {
            }

            sealed interface Middle extends Part permits Leaf {
            }

            final class Leaf implements Middle {
            }

            record Holder(Part value) {
            }

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Holder(new Leaf()).hashCode());
                }
            }
            """)).contains("error[JAVAN030]", "unsupported record component type");
    }

    @Test
    void recordToStringRemainsUnsupported() throws Exception {
        assertThat(rejectedRecordToStringBuild()).contains("error[JAVAN030]", "toString");
    }

    private List<String> outputs(final String scenario) {
        final ProcessResult jvm = processSlow(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            jvmClasses.toString(),
            "com.acme.Main",
            scenario
        ));
        final ProcessResult nativeRun = processSlow(project, List.of(nativeBinary.toString(), scenario));
        requireSuccess(jvm);
        requireSuccess(nativeRun);
        return List.of(jvm.stdout(), nativeRun.stdout());
    }

    private String nativeFailure(final String scenario) {
        final ProcessResult result = processSlow(project, List.of(nativeBinary.toString(), scenario));
        if (result.exitCode() == 0) {
            throw new IllegalStateException("unsafe generic record value was accepted");
        }
        return result.stderr();
    }

    private String rejectedRecordBuild(
        final String projectName,
        final String componentType,
        final String value
    ) throws Exception {
        final Path rejectedProject = project(projectName);
        writeJava(rejectedProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                record Unsafe(%s value) {
                }

                public static void main(final String[] args) {
                    System.out.println(new Unsafe(%s).equals(new Unsafe(%s)));
                }
            }
            """.formatted(componentType, value, value));
        final CliRun build = runSlow(tempDir, "build", rejectedProject.toString());
        if (build.exitCode() == 0) {
            throw new IllegalStateException("unsafe record shape was accepted");
        }
        return build.stderr();
    }

    private String rejectedRecordToStringBuild() throws Exception {
        final Path rejectedProject = project("record-to-string");
        writeJava(rejectedProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                record Value(int value) {
                }

                public static void main(final String[] args) {
                    System.out.println(new Value(7).toString());
                }
            }
            """);
        final CliRun build = runSlow(tempDir, "build", rejectedProject.toString());
        if (build.exitCode() == 0) {
            throw new IllegalStateException("record toString was accepted");
        }
        return build.stderr();
    }

    private String rejectedGenericRecordBuild(final String projectName, final String body) throws Exception {
        final Path rejectedProject = project(projectName);
        writeJava(rejectedProject, "com.acme.Main", """
            package com.acme;

            %s
            """.formatted(body));
        final CliRun build = runSlow(tempDir, "build", rejectedProject.toString());
        if (build.exitCode() == 0) {
            throw new IllegalStateException("unsafe generic record shape was accepted");
        }
        return build.stderr();
    }

    private String unsafeRawListRuntimeFailure(
        final String projectName,
        final String recordOperation
    ) throws Exception {
        final Path unsafeProject = project(projectName);
        writeJava(unsafeProject, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                record Strings(List<String> value) {
                }

                private Main() {
                }

                @SuppressWarnings({"rawtypes", "unchecked"})
                public static void main(final String[] args) {
                    final List raw = new ArrayList();
                    raw.add(Integer.valueOf(7));
                    final List<String> unsafe = (List<String>) raw;
                    %s
                }
            }
            """.formatted(recordOperation));
        final CliRun build = runSlow(tempDir, "build", unsafeProject.toString());
        if (build.exitCode() != 0) {
            throw new IllegalStateException(build.stderr());
        }
        final ProcessResult result = processSlow(
            unsafeProject,
            List.of(unsafeProject.resolve(".javan/bin/" + projectName).toString())
        );
        if (result.exitCode() == 0) {
            throw new IllegalStateException("unsafe generic record value was accepted");
        }
        return result.stderr();
    }

    private String unsafeRawMapRuntimeFailure(
        final String projectName,
        final String insertion,
        final String recordOperation
    ) throws Exception {
        final Path unsafeProject = project(projectName);
        writeJava(unsafeProject, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                record Strings(Map<String, String> value) {
                }

                private Main() {
                }

                @SuppressWarnings({"rawtypes", "unchecked"})
                public static void main(final String[] args) {
                    final Map raw = new LinkedHashMap();
                    %s
                    final Map<String, String> unsafe = (Map<String, String>) raw;
                    %s
                }
            }
            """.formatted(insertion, recordOperation));
        final CliRun build = runSlow(tempDir, "build", unsafeProject.toString());
        if (build.exitCode() != 0) {
            throw new IllegalStateException(build.stderr());
        }
        final ProcessResult result = processSlow(
            unsafeProject,
            List.of(unsafeProject.resolve(".javan/bin/" + projectName).toString())
        );
        if (result.exitCode() == 0) {
            throw new IllegalStateException("unsafe generic record value was accepted");
        }
        return result.stderr();
    }

    private String customListCarrierFailure() throws Exception {
        final String projectName = "custom-list-carrier-runtime";
        final Path unsafeProject = project(projectName);
        writeJava(unsafeProject, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                record Strings(List<String> value) {
                }

                static final class CustomList extends ArrayList<String> {
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new CustomList();
                    System.out.println(new Strings(values).hashCode());
                }
            }
            """);
        final CliRun build = runSlow(tempDir, "build", unsafeProject.toString());
        if (build.exitCode() != 0) {
            throw new IllegalStateException(build.stderr());
        }
        final ProcessResult result = processSlow(
            unsafeProject,
            List.of(unsafeProject.resolve(".javan/bin/" + projectName).toString())
        );
        if (result.exitCode() == 0) {
            throw new IllegalStateException("custom List carrier was accepted");
        }
        return result.stderr();
    }

    private static void requireSuccess(final ProcessResult result) {
        if (result.exitCode() != 0) {
            throw new IllegalStateException(result.stderr());
        }
    }

    private static String source() {
        return """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;

            sealed interface SealedPart permits SealedValue, SealedInheritedValue, SealedIdentity {
            }

            record SealedValue(int value) implements SealedPart {
            }

            class SealedValueBase {
                private final int value;

                SealedValueBase(final int value) {
                    this.value = value;
                }

                @Override
                public boolean equals(final Object other) {
                    return other instanceof SealedValueBase candidate && value == candidate.value;
                }

                @Override
                public int hashCode() {
                    return value;
                }
            }

            final class SealedInheritedValue extends SealedValueBase implements SealedPart {
                SealedInheritedValue(final int value) {
                    super(value);
                }
            }

            final class SealedIdentity implements SealedPart {
            }

            record SealedHolder(SealedPart value) {
            }

            public final class Main {
                record BooleanValue(boolean value) {
                }

                record ByteValue(byte value) {
                }

                record ShortValue(short value) {
                }

                record CharValue(char value) {
                }

                record IntValue(int value) {
                }

                record LongValue(long value) {
                }

                record FloatValue(float value) {
                }

                record DoubleValue(double value) {
                }

                record StringValue(String value) {
                }

                record ArrayValue(Object[] value) {
                }

                record EnumValue(State value) {
                }

                record Child(String value, int count) {
                }

                record Parent(Child child) {
                }

                record ConcreteValue(Value value) {
                }

                record ListValue(List<String> value) {
                }

                record ArrayListValue(ArrayList<String> value) {
                }

                record MapValue(Map<String, String> value) {
                }

                record MapRecordListValue(List<MapValue> value) {
                }

                record RecordListValue(List<Child> value) {
                }

                record BoxedListValue(List<Integer> value) {
                }

                record EnumListValue(List<State> value) {
                }

                record ArrayElementListValue(List<int[]> value) {
                }

                record ReferenceArrayElementListValue(List<Object[]> value) {
                }

                record NestedListValue(List<List<Child>> value) {
                }

                record NullableListValue(List<String> value) {
                }

                record PairListValue(List<String> first, List<String> second) {
                }

                record NestedStringsValue(List<List<String>> value) {
                }

                record Other(int value) {
                }

                enum State {
                    FIRST,
                    SECOND
                }

                static final class Value {
                    private final int value;

                    Value(final int value) {
                        this.value = value;
                    }

                    @Override
                    public boolean equals(final Object other) {
                        return other instanceof Value candidate && value == candidate.value;
                    }

                    @Override
                    public int hashCode() {
                        return 41 + value;
                    }
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args[0];
                    if (scenario.equals("boolean")) {
                        final BooleanValue value = new BooleanValue(true);
                        System.out.println(value.equals(new BooleanValue(true)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("byte")) {
                        final ByteValue value = new ByteValue((byte) -7);
                        System.out.println(value.equals(new ByteValue((byte) -7)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("short")) {
                        final ShortValue value = new ShortValue((short) 32000);
                        System.out.println(value.equals(new ShortValue((short) 32000)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("char")) {
                        final CharValue value = new CharValue('Z');
                        System.out.println(value.equals(new CharValue('Z')));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("int")) {
                        final IntValue value = new IntValue(123456789);
                        System.out.println(value.equals(new IntValue(123456789)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("long")) {
                        final LongValue value = new LongValue(0x100000001L);
                        System.out.println(value.equals(new LongValue(0x100000001L)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("float")) {
                        final FloatValue value = new FloatValue(1.5f);
                        System.out.println(value.equals(new FloatValue(1.5f)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("double")) {
                        final DoubleValue value = new DoubleValue(1.5d);
                        System.out.println(value.equals(new DoubleValue(1.5d)));
                        System.out.println(value.hashCode());
                        return;
                    }
                    if (scenario.equals("float-nan")) {
                        final FloatValue first = new FloatValue(Float.intBitsToFloat(0x7fc00001));
                        final FloatValue second = new FloatValue(Float.intBitsToFloat(0x7fc00002));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        System.out.println(second.hashCode());
                        return;
                    }
                    if (scenario.equals("double-nan")) {
                        final DoubleValue first = new DoubleValue(Double.longBitsToDouble(0x7ff8000000000001L));
                        final DoubleValue second = new DoubleValue(Double.longBitsToDouble(0x7ff8000000000002L));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        System.out.println(second.hashCode());
                        return;
                    }
                    if (scenario.equals("float-zero")) {
                        final FloatValue positive = new FloatValue(0.0f);
                        final FloatValue negative = new FloatValue(-0.0f);
                        System.out.println(positive.equals(negative));
                        System.out.println(positive.hashCode());
                        System.out.println(negative.hashCode());
                        return;
                    }
                    if (scenario.equals("double-zero")) {
                        final DoubleValue positive = new DoubleValue(0.0d);
                        final DoubleValue negative = new DoubleValue(-0.0d);
                        System.out.println(positive.equals(negative));
                        System.out.println(positive.hashCode());
                        System.out.println(negative.hashCode());
                        return;
                    }
                    if (scenario.equals("null")) {
                        final StringValue first = new StringValue(null);
                        System.out.println(first.equals(new StringValue(null)));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("string")) {
                        final StringValue first = new StringValue(new String("same"));
                        final StringValue second = new StringValue(new String("same"));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("array")) {
                        final Object[] shared = new Object[]{"value"};
                        final ArrayValue first = new ArrayValue(shared);
                        System.out.println(first.equals(new ArrayValue(shared)));
                        System.out.println(first.equals(new ArrayValue(new Object[]{"value"})));
                        System.out.println(first.hashCode() == new ArrayValue(shared).hashCode());
                        return;
                    }
                    if (scenario.equals("enum")) {
                        final EnumValue first = new EnumValue(State.FIRST);
                        System.out.println(first.equals(new EnumValue(State.FIRST)));
                        System.out.println(first.equals(new EnumValue(State.SECOND)));
                        System.out.println(first.hashCode() == new EnumValue(State.FIRST).hashCode());
                        return;
                    }
                    if (scenario.equals("nested")) {
                        final Parent first = new Parent(new Child("nested", 7));
                        final Parent second = new Parent(new Child("nested", 7));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("concrete")) {
                        final ConcreteValue first = new ConcreteValue(new Value(7));
                        final ConcreteValue second = new ConcreteValue(new Value(7));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("sealed-value-equality")) {
                        System.out.println(new SealedHolder(new SealedValue(7)).equals(
                            new SealedHolder(new SealedValue(7))
                        ));
                        return;
                    }
                    if (scenario.equals("sealed-distinct-identity")) {
                        System.out.println(new SealedHolder(new SealedIdentity()).equals(
                            new SealedHolder(new SealedIdentity())
                        ));
                        return;
                    }
                    if (scenario.equals("sealed-shared-identity")) {
                        final SealedIdentity identity = new SealedIdentity();
                        System.out.println(new SealedHolder(identity).equals(new SealedHolder(identity)));
                        return;
                    }
                    if (scenario.equals("sealed-cross-leaf")) {
                        System.out.println(new SealedHolder(new SealedValue(7)).equals(
                            new SealedHolder(new SealedIdentity())
                        ));
                        return;
                    }
                    if (scenario.equals("sealed-value-hash")) {
                        System.out.println(new SealedHolder(new SealedValue(7)).hashCode());
                        return;
                    }
                    if (scenario.equals("sealed-inherited-equality")) {
                        System.out.println(new SealedHolder(new SealedInheritedValue(7)).equals(
                            new SealedHolder(new SealedInheritedValue(7))
                        ));
                        return;
                    }
                    if (scenario.equals("sealed-inherited-hash")) {
                        System.out.println(new SealedHolder(new SealedInheritedValue(7)).hashCode());
                        return;
                    }
                    if (scenario.equals("sealed-repeated-identity-hash")) {
                        final SealedIdentity identity = new SealedIdentity();
                        final SealedHolder holder = new SealedHolder(identity);
                        System.out.println(holder.hashCode() == holder.hashCode());
                        return;
                    }
                    if (scenario.equals("sealed-null-equality")) {
                        System.out.println(new SealedHolder(null).equals(new SealedHolder(null)));
                        return;
                    }
                    if (scenario.equals("sealed-null-hash")) {
                        System.out.println(new SealedHolder(null).hashCode());
                        return;
                    }
                    if (scenario.equals("self")) {
                        final IntValue value = new IntValue(7);
                        System.out.println(value.equals(value));
                        return;
                    }
                    if (scenario.equals("type-mismatch")) {
                        System.out.println(new IntValue(7).equals(new Other(7)));
                        return;
                    }
                    if (scenario.equals("list")) {
                        final ListValue first = new ListValue(List.of(new String("a"), new String("b")));
                        final ListValue second = new ListValue(List.of(new String("a"), new String("b")));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("array-list")) {
                        final ArrayListValue first = new ArrayListValue(new ArrayList<>(List.of("a", "b")));
                        final ArrayListValue second = new ArrayListValue(new ArrayList<>(List.of("a", "b")));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("map")) {
                        final Map<String, String> firstValues = new LinkedHashMap<>();
                        firstValues.put(new String("first"), new String("value"));
                        firstValues.put(null, new String("null-key"));
                        firstValues.put(new String("null-value"), null);
                        final Map<String, String> equalValues = new LinkedHashMap<>();
                        equalValues.put(new String("null-value"), null);
                        equalValues.put(null, new String("null-key"));
                        equalValues.put(new String("first"), new String("value"));
                        final Map<String, String> equalView = Collections.unmodifiableMap(equalValues);
                        final Map<String, String> changedValues = new LinkedHashMap<>();
                        changedValues.put(new String("null-value"), null);
                        changedValues.put(null, new String("null-key"));
                        changedValues.put(new String("first"), new String("changed"));
                        final Map<String, String> shortValues = new LinkedHashMap<>();
                        shortValues.put(new String("first"), new String("value"));
                        shortValues.put(null, new String("null-key"));
                        final Map<String, String> missingKeyValues = new LinkedHashMap<>();
                        missingKeyValues.put(new String("other"), new String("value"));
                        missingKeyValues.put(null, new String("null-key"));
                        missingKeyValues.put(new String("null-value"), null);
                        final MapValue first = new MapValue(firstValues);
                        final MapValue equal = new MapValue(equalView);
                        System.out.println(first.equals(equal));
                        System.out.println(first.equals(new MapValue(changedValues)));
                        System.out.println(first.equals(new MapValue(shortValues)));
                        System.out.println(first.equals(new MapValue(missingKeyValues)));
                        System.out.println(first.hashCode());
                        System.out.println(equal.hashCode());
                        return;
                    }
                    if (scenario.equals("null-map")) {
                        final MapValue first = new MapValue(null);
                        System.out.println(first.equals(new MapValue(null)));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("empty-map")) {
                        final MapValue first = new MapValue(Map.of());
                        System.out.println(first.equals(new MapValue(Map.of())));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("map-record-list")) {
                        final MapRecordListValue first = new MapRecordListValue(
                            List.of(new MapValue(Map.of("key", "value")))
                        );
                        final MapRecordListValue equal = new MapRecordListValue(
                            List.of(new MapValue(Map.of("key", "value")))
                        );
                        System.out.println(first.equals(equal));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("record-list")) {
                        final RecordListValue first = new RecordListValue(List.of(new Child("x", 1)));
                        final RecordListValue second = new RecordListValue(List.of(new Child("x", 1)));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("boxed-list")) {
                        final BoxedListValue first = new BoxedListValue(List.of(7, 8));
                        final BoxedListValue second = new BoxedListValue(List.of(7, 8));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("enum-list")) {
                        final EnumListValue first = new EnumListValue(List.of(State.FIRST, State.SECOND));
                        final EnumListValue second = new EnumListValue(List.of(State.FIRST, State.SECOND));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode() == new EnumListValue(List.of(State.FIRST, State.SECOND)).hashCode());
                        return;
                    }
                    if (scenario.equals("array-element-list")) {
                        final int[] firstValue = new int[]{1};
                        final int[] secondValue = new int[]{2};
                        final ArrayElementListValue first = new ArrayElementListValue(List.of(firstValue, secondValue));
                        System.out.println(first.equals(new ArrayElementListValue(List.of(firstValue, secondValue))));
                        System.out.println(first.equals(new ArrayElementListValue(List.of(new int[]{1}, new int[]{2}))));
                        System.out.println(first.hashCode()
                            == new ArrayElementListValue(List.of(firstValue, secondValue)).hashCode());
                        return;
                    }
                    if (scenario.equals("reference-array-element-list")) {
                        final String[] firstValue = new String[]{"x"};
                        final String[] secondValue = new String[]{"y"};
                        final ReferenceArrayElementListValue first =
                            new ReferenceArrayElementListValue(List.of(firstValue, secondValue));
                        System.out.println(first.equals(
                            new ReferenceArrayElementListValue(List.of(firstValue, secondValue))
                        ));
                        System.out.println(first.equals(
                            new ReferenceArrayElementListValue(List.of(new String[]{"x"}, new String[]{"y"}))
                        ));
                        System.out.println(first.hashCode() == new ReferenceArrayElementListValue(
                            List.of(firstValue, secondValue)
                        ).hashCode());
                        return;
                    }
                    if (scenario.equals("nested-list")) {
                        final NestedListValue first = new NestedListValue(List.of(List.of(new Child("x", 1))));
                        final NestedListValue second = new NestedListValue(List.of(List.of(new Child("x", 1))));
                        System.out.println(first.equals(second));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("null-list-element")) {
                        final ArrayList<String> firstValues = new ArrayList<>();
                        firstValues.add(null);
                        final ArrayList<String> secondValues = new ArrayList<>();
                        secondValues.add(null);
                        final NullableListValue first = new NullableListValue(firstValues);
                        System.out.println(first.equals(new NullableListValue(secondValues)));
                        System.out.println(first.hashCode());
                        return;
                    }
                    if (scenario.equals("unsafe-later-list-component")) {
                        final List raw = new ArrayList();
                        raw.add(Integer.valueOf(7));
                        final List<String> unsafe = (List<String>) raw;
                        final PairListValue first =
                            new PairListValue(List.of("first"), List.of("safe"));
                        final PairListValue second =
                            new PairListValue(List.of("different"), unsafe);
                        System.out.println(first.equals(second));
                        return;
                    }
                    if (scenario.equals("unsafe-nested-list-cycle")) {
                        final List raw = new ArrayList();
                        raw.add(raw);
                        final List<List<String>> unsafe = (List<List<String>>) raw;
                        System.out.println(new NestedStringsValue(unsafe).hashCode());
                    }
                }
            }
            """;
    }
}
