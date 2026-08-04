package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliDupX2IntegrationTest extends CliIntegrationSupport {
    private static final ClassDesc FORM_ONE_CLASS = ClassDesc.of("dep.DupX2FormOne");
    private static final ClassDesc FORM_TWO_CLASS = ClassDesc.of("dep.DupX2FormTwo");
    private static final ClassDesc FORM_ONE_SYSTEM_OUT_CLASS = ClassDesc.of("dep.DupX2FormOneSystemOut");
    private static final ClassDesc FORM_ONE_SYSTEM_ERR_CLASS = ClassDesc.of("dep.DupX2FormOneSystemErr");
    private static final ClassDesc FORM_TWO_SYSTEM_OUT_CLASS = ClassDesc.of("dep.DupX2FormTwoSystemOut");
    private static final ClassDesc FORM_TWO_SYSTEM_ERR_CLASS = ClassDesc.of("dep.DupX2FormTwoSystemErr");
    private static final ClassDesc FORM_ONE_SYSTEM_OUT_PRINT_CLASS = ClassDesc.of("dep.DupX2FormOneSystemOutPrint");
    private static final ClassDesc FORM_ONE_SYSTEM_ERR_PRINT_CLASS = ClassDesc.of("dep.DupX2FormOneSystemErrPrint");
    private static final ClassDesc FORM_TWO_SYSTEM_OUT_PRINT_CLASS = ClassDesc.of("dep.DupX2FormTwoSystemOutPrint");
    private static final ClassDesc FORM_TWO_SYSTEM_ERR_PRINT_CLASS = ClassDesc.of("dep.DupX2FormTwoSystemErrPrint");
    private static final ClassDesc LOCAL_SNAPSHOT_CLASS = ClassDesc.of("dep.DupX2LocalSnapshot");
    private static final ClassDesc DEFERRED_CALL_CLASS = ClassDesc.of("dep.DupX2DeferredCall");
    private static final ClassDesc DEFERRED_LAMBDA_CLASS = ClassDesc.of("dep.DupX2DeferredLambda");
    private static final ClassDesc DEFERRED_LAMBDA_INVOKE_CLASS = ClassDesc.of("dep.DupX2DeferredLambdaInvoke");
    private static final ClassDesc DEFERRED_CAPTURED_LAMBDA_CLASS = ClassDesc.of("dep.DupX2DeferredCapturedLambda");
    private static final ClassDesc SYSTEM = ClassDesc.of("java.lang.System");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc LONG = ClassDesc.ofDescriptor("J");
    private static final ClassDesc OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc FUNCTION = ClassDesc.of("java.util.function.Function");
    private static final ClassDesc PRINT_STREAM = ClassDesc.of("java.io.PrintStream");
    private static final ClassDesc STRING = ClassDesc.of("java.lang.String");

    @Test
    void threeCategoryOneValuesPreserveValueAndOperandOrder() throws Exception {
        final Path dependency = formOneDependency();
        final Path project = project("dup-x2-category-one");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormOne;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormOne.evaluate());
                    System.out.println(DupX2FormOne.order());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-category-one", List.of(dependency)))
            .isEqualTo(runJvm(project, "com.acme.Main", List.of(dependency)));
    }

    @Test
    void categoryOneAboveCategoryTwoPreservesValueAndOperandOrder() throws Exception {
        final Path dependency = formTwoDependency();
        final Path project = project("dup-x2-category-two");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormTwo;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormTwo.evaluate());
                    System.out.println(DupX2FormTwo.order());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-category-two", List.of(dependency)))
            .isEqualTo(runJvm(project, "com.acme.Main", List.of(dependency)));
    }

    @Test
    void threeCategoryOneReferencesWithSystemOutReturnTheDuplicatedStream() throws Exception {
        final Path dependency = formOneStreamDependency(
            "dup-x2-form-one-system-out.jar",
            FORM_ONE_SYSTEM_OUT_CLASS,
            "err",
            "err",
            "out"
        );
        final Path project = project("dup-x2-form-one-system-out");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormOneSystemOut;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormOneSystemOut.stream() == System.out);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-form-one-system-out", List.of(dependency))).isEqualTo("true\n");
    }

    @Test
    void threeCategoryOneReferencesWithSystemErrReturnTheDuplicatedStream() throws Exception {
        final Path dependency = formOneStreamDependency(
            "dup-x2-form-one-system-err.jar",
            FORM_ONE_SYSTEM_ERR_CLASS,
            "out",
            "out",
            "err"
        );
        final Path project = project("dup-x2-form-one-system-err");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormOneSystemErr;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormOneSystemErr.stream() == System.err);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-form-one-system-err", List.of(dependency))).isEqualTo("true\n");
    }

    @Test
    void categoryOneReferenceAboveCategoryTwoWithSystemOutReturnsTheDuplicatedStream() throws Exception {
        final Path dependency = formTwoStreamDependency(
            "dup-x2-form-two-system-out.jar",
            FORM_TWO_SYSTEM_OUT_CLASS,
            "out"
        );
        final Path project = project("dup-x2-form-two-system-out");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormTwoSystemOut;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormTwoSystemOut.stream() == System.out);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-form-two-system-out", List.of(dependency))).isEqualTo("true\n");
    }

    @Test
    void categoryOneReferenceAboveCategoryTwoWithSystemErrReturnsTheDuplicatedStream() throws Exception {
        final Path dependency = formTwoStreamDependency(
            "dup-x2-form-two-system-err.jar",
            FORM_TWO_SYSTEM_ERR_CLASS,
            "err"
        );
        final Path project = project("dup-x2-form-two-system-err");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormTwoSystemErr;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormTwoSystemErr.stream() == System.err);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-form-two-system-err", List.of(dependency))).isEqualTo("true\n");
    }

    @Test
    void threeCategoryOneReferencesWithSystemOutInvokeOnTheDuplicatedStream() throws Exception {
        final Path dependency = formOneDirectPrintDependency(
            "dup-x2-form-one-system-out-print.jar",
            FORM_ONE_SYSTEM_OUT_PRINT_CLASS,
            "out",
            "form-one-out"
        );
        final Path project = project("dup-x2-form-one-system-out-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormOneSystemOutPrint;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    DupX2FormOneSystemOutPrint.print();
                }
            }
            """);

        assertThat(buildAndRunOutput(project, "dup-x2-form-one-system-out-print", List.of(dependency)))
            .isEqualTo(new NativeOutput(0, "form-one-out\n", ""));
    }

    @Test
    void threeCategoryOneReferencesWithSystemErrInvokeOnTheDuplicatedStream() throws Exception {
        final Path dependency = formOneDirectPrintDependency(
            "dup-x2-form-one-system-err-print.jar",
            FORM_ONE_SYSTEM_ERR_PRINT_CLASS,
            "err",
            "form-one-err"
        );
        final Path project = project("dup-x2-form-one-system-err-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormOneSystemErrPrint;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    DupX2FormOneSystemErrPrint.print();
                }
            }
            """);

        assertThat(buildAndRunOutput(project, "dup-x2-form-one-system-err-print", List.of(dependency)))
            .isEqualTo(new NativeOutput(0, "", "form-one-err\n"));
    }

    @Test
    void categoryOneReferenceAboveCategoryTwoWithSystemOutInvokesOnTheDuplicatedStream() throws Exception {
        final Path dependency = formTwoDirectPrintDependency(
            "dup-x2-form-two-system-out-print.jar",
            FORM_TWO_SYSTEM_OUT_PRINT_CLASS,
            "out",
            "form-two-out"
        );
        final Path project = project("dup-x2-form-two-system-out-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormTwoSystemOutPrint;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    DupX2FormTwoSystemOutPrint.print();
                }
            }
            """);

        assertThat(buildAndRunOutput(project, "dup-x2-form-two-system-out-print", List.of(dependency)))
            .isEqualTo(new NativeOutput(0, "form-two-out\n", ""));
    }

    @Test
    void categoryOneReferenceAboveCategoryTwoWithSystemErrInvokesOnTheDuplicatedStream() throws Exception {
        final Path dependency = formTwoDirectPrintDependency(
            "dup-x2-form-two-system-err-print.jar",
            FORM_TWO_SYSTEM_ERR_PRINT_CLASS,
            "err",
            "form-two-err"
        );
        final Path project = project("dup-x2-form-two-system-err-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormTwoSystemErrPrint;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    DupX2FormTwoSystemErrPrint.print();
                }
            }
            """);

        assertThat(buildAndRunOutput(project, "dup-x2-form-two-system-err-print", List.of(dependency)))
            .isEqualTo(new NativeOutput(0, "", "form-two-err\n"));
    }

    @Test
    void bottomCategoryOneLocalKeepsItsStackSnapshotAfterTheLocalChanges() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("bottomInt", "dup-x2-bottom-local-snapshot");

        assertThat(buildAndRun(project, "dup-x2-bottom-local-snapshot", List.of(dependency))).isEqualTo("4\n");
    }

    @Test
    void middleCategoryOneLocalKeepsItsStackSnapshotAfterTheLocalChanges() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("middleInt", "dup-x2-middle-local-snapshot");

        assertThat(buildAndRun(project, "dup-x2-middle-local-snapshot", List.of(dependency))).isEqualTo("4\n");
    }

    @Test
    void topCategoryOneLocalKeepsItsStackSnapshotAfterTheLocalChanges() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("topInt", "dup-x2-top-local-snapshot");

        assertThat(buildAndRun(project, "dup-x2-top-local-snapshot", List.of(dependency))).isEqualTo("2\n");
    }

    @Test
    void categoryTwoLocalKeepsItsStackSnapshotAfterTheLocalChanges() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("lowerLong", "dup-x2-long-local-snapshot");

        assertThat(buildAndRun(project, "dup-x2-long-local-snapshot", List.of(dependency))).isEqualTo("13\n");
    }

    @Test
    void objectLocalKeepsItsStackSnapshotAfterTheLocalChanges() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = project("dup-x2-object-local-snapshot");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2LocalSnapshot;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2LocalSnapshot.object() == System.out);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-object-local-snapshot", List.of(dependency))).isEqualTo("true\n");
    }

    @Test
    void localLoadedBeforeAStoreKeepsItsOriginalStackValue() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("localBeforeStore", "dup-x2-local-before-store");

        assertThat(buildAndRun(project, "dup-x2-local-before-store", List.of(dependency))).isEqualTo("3\n");
    }

    @Test
    void parameterLoadedBeforeAStoreKeepsItsOriginalStackValue() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = project("dup-x2-parameter-before-store");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2LocalSnapshot;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2LocalSnapshot.parameterBeforeStore(1));
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-parameter-before-store", List.of(dependency))).isEqualTo("3\n");
    }

    @Test
    void staticFieldLoadedBeforeMutationKeepsItsOriginalStackValue() throws Exception {
        final Path dependency = deferredCallDependency();
        final Path project = localSnapshotProject(
            DEFERRED_CALL_CLASS.displayName(),
            "fieldBeforeMutation",
            "dup-x2-field-before-mutation"
        );

        assertThat(buildAndRun(project, "dup-x2-field-before-mutation", List.of(dependency))).isEqualTo("3\n");
    }

    @Test
    void arrayElementLoadedBeforeMutationKeepsItsOriginalStackValue() throws Exception {
        final Path dependency = deferredCallDependency();
        final Path project = project("dup-x2-array-before-mutation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2DeferredCall;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2DeferredCall.arrayBeforeMutation(new int[]{1}));
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-array-before-mutation", List.of(dependency))).isEqualTo("3\n");
    }

    @Test
    void branchArmKeepsItsOperandStackSnapshot() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = project("dup-x2-branch-arm-snapshot");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2LocalSnapshot;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2LocalSnapshot.branchArm(true));
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-branch-arm-snapshot", List.of(dependency))).isEqualTo("3\n");
    }

    @Test
    void switchArmKeepsItsOperandStackSnapshot() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("switchArm", "dup-x2-switch-arm-snapshot");

        assertThat(buildAndRun(project, "dup-x2-switch-arm-snapshot", List.of(dependency))).isEqualTo("3\n");
    }

    @Test
    void floatCategoryOneValuesPreserveOperandOrder() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("floatFormOne", "dup-x2-float-form-one");

        assertThat(buildAndRun(project, "dup-x2-float-form-one", List.of(dependency))).isEqualTo("1.0\n");
    }

    @Test
    void doubleCategoryTwoValuePreservesOperandOrder() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("doubleFormTwo", "dup-x2-double-form-two");

        assertThat(buildAndRun(project, "dup-x2-double-form-two", List.of(dependency))).isEqualTo("13.0\n");
    }

    @Test
    void twoDupX2InstructionsInOneMethodPreserveBothPermutations() throws Exception {
        final Path dependency = localSnapshotDependency();
        final Path project = localSnapshotProject("repeated", "dup-x2-repeated");

        assertThat(buildAndRun(project, "dup-x2-repeated", List.of(dependency))).isEqualTo("1\n");
    }

    @Test
    void duplicatedCallResultExecutesTheCallOnce() throws Exception {
        final Path dependency = deferredCallDependency();
        final Path project = localSnapshotProject(
            DEFERRED_CALL_CLASS.displayName(),
            "aliasCount",
            "dup-x2-deferred-call-alias"
        );

        assertThat(buildAndRun(project, "dup-x2-deferred-call-alias", List.of(dependency))).isEqualTo("1\n");
    }

    @Test
    void callResultExecutesBeforeALaterVoidCall() throws Exception {
        final Path dependency = deferredCallDependency();
        final Path project = localSnapshotProject(
            DEFERRED_CALL_CLASS.displayName(),
            "ordered",
            "dup-x2-deferred-call-order"
        );

        assertThat(buildAndRun(project, "dup-x2-deferred-call-order", List.of(dependency))).isEqualTo("12\n");
    }

    @Test
    void deferredFunctionValueCanBePermutedAndDiscardedAfterDupX2() throws Exception {
        final Path dependency = deferredLambdaDependency();
        final Path project = project("dup-x2-deferred-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2DeferredLambda;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2DeferredLambda.evaluate());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-deferred-lambda", List.of(dependency))).isEqualTo("7\n");
    }

    @Test
    void discardedFunctionDoesNotAuthorizeASeparateFunctionApply() throws Exception {
        final Path dependency = deferredLambdaDependency();
        final Path project = project("dup-x2-discarded-lambda-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2DeferredLambda;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2DeferredLambda.evaluate());
                    System.out.println(apply(null));
                }

                private static Object apply(final Function<Object, Object> function) {
                    return function.apply(null);
                }
            }
            """);

        assertThat(buildAndRun(
            project,
            "dup-x2-discarded-lambda-isolation",
            List.of(dependency)
        )).contains("error[JAVAN012]", "java/util/function/Function.apply")
            .doesNotContain("error[JAVAN040]");
    }

    @Test
    void dependencyFunctionValueInvokedAfterDupX2RemainsRejected() throws Exception {
        final Path dependency = deferredLambdaInvocationDependency();
        final Path project = project("dup-x2-deferred-lambda-invoke");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2DeferredLambdaInvoke;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2DeferredLambdaInvoke.evaluate());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-deferred-lambda-invoke", List.of(dependency)))
            .contains("error[JAVAN030]", "dep/DupX2DeferredLambdaInvoke", "invokedynamic");
    }

    @Test
    void discardedCapturedDependencyFunctionRemainsRejected() throws Exception {
        final Path dependency = deferredCapturedLambdaDependency();
        final Path project = project("dup-x2-deferred-captured-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2DeferredCapturedLambda;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2DeferredCapturedLambda.evaluate());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-deferred-captured-lambda", List.of(dependency)))
            .contains("error[JAVAN030]", "dep/DupX2DeferredCapturedLambda", "invokedynamic");
    }

    private String buildAndRun(final Path project, final String name, final List<Path> classpath) {
        final java.util.ArrayList<String> arguments = new java.util.ArrayList<>();
        arguments.add("build");
        arguments.add(project.toString());
        for (final Path entry : classpath) {
            arguments.add("--classpath");
            arguments.add(entry.toString());
        }
        final CliRun build = run(tempDir, arguments.toArray(String[]::new));
        if (build.exitCode() != 0) {
            return build.stderr();
        }
        return process(project, List.of(project.resolve(".javan/bin").resolve(name).toString())).stdout();
    }

    private NativeOutput buildAndRunOutput(final Path project, final String name, final List<Path> classpath) {
        final java.util.ArrayList<String> arguments = new java.util.ArrayList<>();
        arguments.add("build");
        arguments.add(project.toString());
        for (final Path entry : classpath) {
            arguments.add("--classpath");
            arguments.add(entry.toString());
        }
        final CliRun build = run(tempDir, arguments.toArray(String[]::new));
        if (build.exitCode() != 0) {
            return new NativeOutput(build.exitCode(), build.stdout(), build.stderr());
        }
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin").resolve(name).toString())
        );
        return new NativeOutput(nativeRun.exitCode(), nativeRun.stdout(), nativeRun.stderr());
    }

    private Path localSnapshotProject(final String method, final String name) throws Exception {
        return localSnapshotProject(LOCAL_SNAPSHOT_CLASS.displayName(), method, name);
    }

    private Path localSnapshotProject(final String dependencyClass, final String method, final String name)
        throws Exception {
        final Path project = project(name);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.%s;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(%s.%s());
                }
            }
            """.formatted(dependencyClass, dependencyClass, method));
        return project;
    }

    private Path formTwoDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(FORM_TWO_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("order", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withMethodBody(
                "left",
                MethodTypeDesc.of(LONG),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .putstatic(FORM_TWO_CLASS, "order", INT)
                    .ldc(Long.valueOf(10L))
                    .lreturn()
            )
            .withMethodBody(
                "right",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(FORM_TWO_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_2()
                    .iadd()
                    .putstatic(FORM_TWO_CLASS, "order", INT)
                    .iconst_3()
                    .ireturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .invokestatic(FORM_TWO_CLASS, "left", MethodTypeDesc.of(LONG))
                    .invokestatic(FORM_TWO_CLASS, "right", MethodTypeDesc.of(INT))
                    .dup_x2()
                    .i2l()
                    .ladd()
                    .l2i()
                    .iadd()
                    .ireturn()
            )
            .withMethodBody(
                "order",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.getstatic(FORM_TWO_CLASS, "order", INT).ireturn()
            ));
        return jar("dup-x2-form-two.jar", FORM_TWO_CLASS, bytes);
    }

    private Path formOneStreamDependency(
        final String name,
        final ClassDesc classDesc,
        final String bottom,
        final String middle,
        final String top
    ) throws Exception {
        final byte[] bytes = ClassFile.of().build(classDesc, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "stream",
                MethodTypeDesc.of(PRINT_STREAM),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(SYSTEM, bottom, PRINT_STREAM)
                    .getstatic(SYSTEM, middle, PRINT_STREAM)
                    .getstatic(SYSTEM, top, PRINT_STREAM)
                    .dup_x2()
                    .astore(3)
                    .astore(2)
                    .astore(1)
                    .astore(0)
                    .aload(0)
                    .areturn()
            ));
        return jar(name, classDesc, bytes);
    }

    private Path formTwoStreamDependency(final String name, final ClassDesc classDesc, final String stream) throws Exception {
        final byte[] bytes = ClassFile.of().build(classDesc, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "stream",
                MethodTypeDesc.of(PRINT_STREAM),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .ldc(Long.valueOf(7L))
                    .getstatic(SYSTEM, stream, PRINT_STREAM)
                    .dup_x2()
                    .astore(2)
                    .lstore(0)
                    .astore(3)
                    .aload(3)
                    .areturn()
            ));
        return jar(name, classDesc, bytes);
    }

    private Path formOneDirectPrintDependency(
        final String name,
        final ClassDesc classDesc,
        final String stream,
        final String message
    ) throws Exception {
        final byte[] bytes = ClassFile.of().build(classDesc, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "print",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .aconst_null()
                    .ldc("unused")
                    .getstatic(SYSTEM, stream, PRINT_STREAM)
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .ldc(message)
                    .invokevirtual(PRINT_STREAM, "println", MethodTypeDesc.of(ClassDesc.ofDescriptor("V"), STRING))
                    .iconst_0()
                    .ireturn()
            ));
        return jar(name, classDesc, bytes);
    }

    private Path formTwoDirectPrintDependency(
        final String name,
        final ClassDesc classDesc,
        final String stream,
        final String message
    ) throws Exception {
        final byte[] bytes = ClassFile.of().build(classDesc, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "print",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .ldc(Long.valueOf(7L))
                    .getstatic(SYSTEM, stream, PRINT_STREAM)
                    .dup_x2()
                    .pop()
                    .l2i()
                    .pop()
                    .ldc(message)
                    .invokevirtual(PRINT_STREAM, "println", MethodTypeDesc.of(ClassDesc.ofDescriptor("V"), STRING))
                    .iconst_0()
                    .ireturn()
            ));
        return jar(name, classDesc, bytes);
    }

    private Path formOneDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(FORM_ONE_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("order", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withMethodBody(
                "first",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code.iconst_1().putstatic(FORM_ONE_CLASS, "order", INT).iconst_1().ireturn()
            )
            .withMethodBody(
                "second",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(FORM_ONE_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_2()
                    .iadd()
                    .putstatic(FORM_ONE_CLASS, "order", INT)
                    .iconst_2()
                    .ireturn()
            )
            .withMethodBody(
                "third",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(FORM_ONE_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_3()
                    .iadd()
                    .putstatic(FORM_ONE_CLASS, "order", INT)
                    .iconst_3()
                    .ireturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .invokestatic(FORM_ONE_CLASS, "first", MethodTypeDesc.of(INT))
                    .invokestatic(FORM_ONE_CLASS, "second", MethodTypeDesc.of(INT))
                    .invokestatic(FORM_ONE_CLASS, "third", MethodTypeDesc.of(INT))
                    .dup_x2()
                    .isub()
                    .isub()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "order",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.getstatic(FORM_ONE_CLASS, "order", INT).ireturn()
            ));
        return jar("dup-x2-form-one.jar", FORM_ONE_CLASS, bytes);
    }

    private Path deferredLambdaDependency() throws Exception {
        final MethodTypeDesc functionMethod = MethodTypeDesc.of(OBJECT, OBJECT);
        final DirectMethodHandleDesc bootstrap = ConstantDescs.ofCallsiteBootstrap(
            ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
            "metafactory",
            ConstantDescs.CD_CallSite,
            ConstantDescs.CD_MethodType,
            ConstantDescs.CD_MethodHandle,
            ConstantDescs.CD_MethodType
        );
        final MethodHandleDesc implementation = MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC,
            DEFERRED_LAMBDA_CLASS,
            "identity",
            functionMethod
        );
        final DynamicCallSiteDesc lambda = DynamicCallSiteDesc.of(
            bootstrap,
            "apply",
            MethodTypeDesc.of(FUNCTION),
            functionMethod,
            implementation,
            functionMethod
        );
        final byte[] bytes = ClassFile.of().build(DEFERRED_LAMBDA_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "identity",
                functionMethod,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code.aload(0).areturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_0()
                    .iconst_1()
                    .invokedynamic(lambda)
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .pop()
                    .bipush(7)
                    .ireturn()
            ));
        return jar("dup-x2-deferred-lambda.jar", DEFERRED_LAMBDA_CLASS, bytes);
    }

    private Path deferredLambdaInvocationDependency() throws Exception {
        final MethodTypeDesc functionMethod = MethodTypeDesc.of(OBJECT, OBJECT);
        final DirectMethodHandleDesc bootstrap = ConstantDescs.ofCallsiteBootstrap(
            ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
            "metafactory",
            ConstantDescs.CD_CallSite,
            ConstantDescs.CD_MethodType,
            ConstantDescs.CD_MethodHandle,
            ConstantDescs.CD_MethodType
        );
        final MethodHandleDesc implementation = MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC,
            DEFERRED_LAMBDA_INVOKE_CLASS,
            "identity",
            functionMethod
        );
        final DynamicCallSiteDesc lambda = DynamicCallSiteDesc.of(
            bootstrap,
            "apply",
            MethodTypeDesc.of(FUNCTION),
            functionMethod,
            implementation,
            functionMethod
        );
        final byte[] bytes = ClassFile.of().build(DEFERRED_LAMBDA_INVOKE_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "identity",
                functionMethod,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code.aload(0).areturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_0()
                    .iconst_1()
                    .invokedynamic(lambda)
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .aconst_null()
                    .invokeinterface(FUNCTION, "apply", MethodTypeDesc.of(OBJECT, OBJECT))
                    .pop()
                    .bipush(7)
                    .ireturn()
            ));
        return jar("dup-x2-deferred-lambda-invoke.jar", DEFERRED_LAMBDA_INVOKE_CLASS, bytes);
    }

    private Path deferredCapturedLambdaDependency() throws Exception {
        final MethodTypeDesc functionMethod = MethodTypeDesc.of(OBJECT, OBJECT);
        final MethodTypeDesc capturedImplementation = MethodTypeDesc.of(OBJECT, OBJECT, OBJECT);
        final DirectMethodHandleDesc bootstrap = ConstantDescs.ofCallsiteBootstrap(
            ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
            "metafactory",
            ConstantDescs.CD_CallSite,
            ConstantDescs.CD_MethodType,
            ConstantDescs.CD_MethodHandle,
            ConstantDescs.CD_MethodType
        );
        final MethodHandleDesc implementation = MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC,
            DEFERRED_CAPTURED_LAMBDA_CLASS,
            "identity",
            capturedImplementation
        );
        final DynamicCallSiteDesc lambda = DynamicCallSiteDesc.of(
            bootstrap,
            "apply",
            MethodTypeDesc.of(FUNCTION, OBJECT),
            functionMethod,
            implementation,
            functionMethod
        );
        final byte[] bytes = ClassFile.of().build(DEFERRED_CAPTURED_LAMBDA_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "identity",
                capturedImplementation,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code.aload(1).areturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_0()
                    .iconst_1()
                    .aconst_null()
                    .invokedynamic(lambda)
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .pop()
                    .bipush(7)
                    .ireturn()
            ));
        return jar("dup-x2-deferred-captured-lambda.jar", DEFERRED_CAPTURED_LAMBDA_CLASS, bytes);
    }

    private Path localSnapshotDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(LOCAL_SNAPSHOT_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody(
                "bottomInt",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .istore(0)
                    .iload(0)
                    .iconst_2()
                    .iconst_3()
                    .dup_x2()
                    .istore(0)
                    .isub()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "middleInt",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_2()
                    .istore(0)
                    .iconst_1()
                    .iload(0)
                    .iconst_3()
                    .dup_x2()
                    .istore(0)
                    .isub()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "topInt",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_3()
                    .istore(0)
                    .iconst_1()
                    .iconst_2()
                    .iload(0)
                    .dup_x2()
                    .istore(1)
                    .istore(0)
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "lowerLong",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .ldc(Long.valueOf(10L))
                    .lstore(0)
                    .lload(0)
                    .iconst_3()
                    .dup_x2()
                    .istore(2)
                    .ldc(Long.valueOf(20L))
                    .lstore(0)
                    .l2i()
                    .iadd()
                    .ireturn()
            )
            .withMethodBody(
                "object",
                MethodTypeDesc.of(PRINT_STREAM),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(SYSTEM, "out", PRINT_STREAM)
                    .astore(0)
                    .aload(0)
                    .aconst_null()
                    .ldc("unused")
                    .dup_x2()
                    .pop()
                    .getstatic(SYSTEM, "err", PRINT_STREAM)
                    .astore(0)
                    .pop()
                    .astore(1)
                    .pop()
                    .aload(1)
                    .areturn()
            )
            .withMethodBody(
                "localBeforeStore",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .istore(0)
                    .iload(0)
                    .iconst_2()
                    .istore(0)
                    .iconst_3()
                    .iconst_4()
                    .dup_x2()
                    .pop()
                    .pop()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "parameterBeforeStore",
                MethodTypeDesc.of(INT, INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iload(0)
                    .iconst_2()
                    .istore(0)
                    .iconst_3()
                    .iconst_4()
                    .dup_x2()
                    .pop()
                    .pop()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "branchArm",
                MethodTypeDesc.of(INT, ClassDesc.ofDescriptor("Z")),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> {
                    final var target = code.newLabel();
                    final var done = code.newLabel();
                    code.iload(0)
                        .ifeq(target)
                        .iconst_1()
                        .istore(1)
                        .iload(1)
                        .iconst_2()
                        .istore(1)
                        .iconst_3()
                        .iconst_4()
                        .dup_x2()
                        .pop()
                        .pop()
                        .isub()
                        .goto_(done)
                        .labelBinding(target)
                        .iconst_3()
                        .labelBinding(done)
                        .ireturn();
                }
            )
            .withMethodBody(
                "switchArm",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> {
                    final var first = code.newLabel();
                    final var second = code.newLabel();
                    final var fallback = code.newLabel();
                    final var done = code.newLabel();
                    code.iconst_1()
                        .lookupswitch(
                            fallback,
                            List.of(SwitchCase.of(1, first), SwitchCase.of(2, second))
                        )
                        .labelBinding(first)
                        .iconst_1()
                        .istore(0)
                        .iload(0)
                        .iconst_2()
                        .istore(0)
                        .iconst_3()
                        .iconst_4()
                        .dup_x2()
                        .pop()
                        .pop()
                        .isub()
                        .goto_(done)
                        .labelBinding(second)
                        .iconst_3()
                        .goto_(done)
                        .labelBinding(fallback)
                        .iconst_3()
                        .labelBinding(done)
                        .ireturn();
                }
            )
            .withMethodBody(
                "floatFormOne",
                MethodTypeDesc.of(ClassDesc.ofDescriptor("F")),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .fconst_1()
                    .fconst_2()
                    .ldc(Float.valueOf(3.0f))
                    .dup_x2()
                    .fsub()
                    .fsub()
                    .fsub()
                    .freturn()
            )
            .withMethodBody(
                "doubleFormTwo",
                MethodTypeDesc.of(ClassDesc.ofDescriptor("D")),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .ldc(Double.valueOf(10.0d))
                    .iconst_3()
                    .dup_x2()
                    .i2d()
                    .dadd()
                    .dstore(0)
                    .pop()
                    .dload(0)
                    .dreturn()
            )
            .withMethodBody(
                "repeated",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .iconst_2()
                    .iconst_3()
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .pop()
                    .iconst_4()
                    .iconst_5()
                    .bipush(6)
                    .dup_x2()
                    .isub()
                    .isub()
                    .isub()
                    .ireturn()
            ));
        return jar("dup-x2-local-snapshot.jar", LOCAL_SNAPSHOT_CLASS, bytes);
    }

    private Path deferredCallDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(DEFERRED_CALL_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("counter", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withField("order", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withMethodBody(
                "increment",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(DEFERRED_CALL_CLASS, "counter", INT)
                    .iconst_1()
                    .iadd()
                    .putstatic(DEFERRED_CALL_CLASS, "counter", INT)
                    .getstatic(DEFERRED_CALL_CLASS, "counter", INT)
                    .ireturn()
            )
            .withMethodBody(
                "aliasCount",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_0()
                    .putstatic(DEFERRED_CALL_CLASS, "counter", INT)
                    .invokestatic(DEFERRED_CALL_CLASS, "increment", MethodTypeDesc.of(INT))
                    .dup()
                    .iconst_0()
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .pop()
                    .getstatic(DEFERRED_CALL_CLASS, "counter", INT)
                    .ireturn()
            )
            .withMethodBody(
                "first",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .putstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .bipush(7)
                    .ireturn()
            )
            .withMethodBody(
                "second",
                MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_2()
                    .iadd()
                    .putstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .return_()
            )
            .withMethodBody(
                "ordered",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_0()
                    .putstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .invokestatic(DEFERRED_CALL_CLASS, "first", MethodTypeDesc.of(INT))
                    .invokestatic(
                        DEFERRED_CALL_CLASS,
                        "second",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V"))
                    )
                    .iconst_2()
                    .iconst_3()
                    .dup_x2()
                    .pop()
                    .pop()
                    .pop()
                    .pop()
                    .getstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .ireturn()
            )
            .withMethodBody(
                "mutateField",
                MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_2()
                    .putstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .return_()
            )
            .withMethodBody(
                "fieldBeforeMutation",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .putstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .getstatic(DEFERRED_CALL_CLASS, "order", INT)
                    .invokestatic(
                        DEFERRED_CALL_CLASS,
                        "mutateField",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V"))
                    )
                    .iconst_3()
                    .iconst_4()
                    .dup_x2()
                    .pop()
                    .pop()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "mutateArray",
                MethodTypeDesc.of(ClassDesc.ofDescriptor("V"), ClassDesc.ofDescriptor("[I")),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .aload(0)
                    .iconst_0()
                    .iconst_2()
                    .iastore()
                    .return_()
            )
            .withMethodBody(
                "arrayBeforeMutation",
                MethodTypeDesc.of(INT, ClassDesc.ofDescriptor("[I")),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .aload(0)
                    .iconst_0()
                    .iaload()
                    .aload(0)
                    .invokestatic(
                        DEFERRED_CALL_CLASS,
                        "mutateArray",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V"), ClassDesc.ofDescriptor("[I"))
                    )
                    .iconst_3()
                    .iconst_4()
                    .dup_x2()
                    .pop()
                    .pop()
                    .isub()
                    .ireturn()
            ));
        return jar("dup-x2-deferred-call.jar", DEFERRED_CALL_CLASS, bytes);
    }

    private Path jar(final String name, final ClassDesc classDesc, final byte[] bytes) throws Exception {
        final Path jar = tempDir.resolve(name);
        try (OutputStream stream = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(stream)) {
            archive.putNextEntry(new JarEntry(classDesc.packageName().replace('.', '/') + "/" + classDesc.displayName() + ".class"));
            archive.write(bytes);
            archive.closeEntry();
        }
        return jar;
    }

    private record NativeOutput(int exitCode, String stdout, String stderr) {
    }
}
