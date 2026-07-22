package javan.compat;

import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class JavanNativeSubstitutionsTest {
    private static final String PROCESS_RUNNER_DESCRIPTOR =
        "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;";
    private static final String PROCESS_RUNNER_ATTACHED_DESCRIPTOR =
        "(Ljava/nio/file/Path;Ljava/util/List;Ljava/io/PrintStream;Ljava/io/PrintStream;)Ljavan/util/ProcessRunner$Result;";

    @Test
    void isSubstitutedCallAcceptsProcessRunnerRun() {
        assertThat(JavanNativeSubstitutions.isSubstitutedCall(processRunnerRunRef())).isTrue();
    }

    @Test
    void isSubstitutedCallAcceptsProcessRunnerRunAttached() {
        assertThat(JavanNativeSubstitutions.isSubstitutedCall(new MethodRef(
            "javan/util/ProcessRunner", "runAttached", PROCESS_RUNNER_ATTACHED_DESCRIPTOR
        ))).isTrue();
    }

    @Test
    void isSubstitutedCallRejectsDifferentOwner() {
        assertThat(JavanNativeSubstitutions.isSubstitutedCall(new MethodRef(
            "java/lang/ProcessBuilder",
            "run",
            PROCESS_RUNNER_DESCRIPTOR
        ))).isFalse();
    }

    @Test
    void isSubstitutedCallRejectsDifferentName() {
        assertThat(JavanNativeSubstitutions.isSubstitutedCall(new MethodRef(
            "javan/util/ProcessRunner",
            "commandExists",
            PROCESS_RUNNER_DESCRIPTOR
        ))).isFalse();
    }

    @Test
    void isSubstitutedCallRejectsDifferentDescriptor() {
        assertThat(JavanNativeSubstitutions.isSubstitutedCall(new MethodRef(
            "javan/util/ProcessRunner",
            "run",
            "(Ljava/nio/file/Path;)Ljavan/util/ProcessRunner$Result;"
        ))).isFalse();
    }

    @Test
    void isSubstitutedFallbackMethodAcceptsProcessRunnerRun() {
        assertThat(JavanNativeSubstitutions.isSubstitutedFallbackMethod(
            "javan/util/ProcessRunner",
            processRunnerRunMethod()
        )).isTrue();
    }

    @Test
    void isSubstitutedFallbackMethodRejectsDifferentOwner() {
        assertThat(JavanNativeSubstitutions.isSubstitutedFallbackMethod(
            "javan/util/OtherRunner",
            processRunnerRunMethod()
        )).isFalse();
    }

    @Test
    void isSubstitutedFallbackMethodRejectsDifferentName() {
        assertThat(JavanNativeSubstitutions.isSubstitutedFallbackMethod(
            "javan/util/ProcessRunner",
            new MethodInfo(0, "firstAvailable", PROCESS_RUNNER_DESCRIPTOR, Optional.empty())
        )).isFalse();
    }

    @Test
    void isSubstitutedFallbackMethodRejectsDifferentDescriptor() {
        assertThat(JavanNativeSubstitutions.isSubstitutedFallbackMethod(
            "javan/util/ProcessRunner",
            new MethodInfo(0, "run", "(Ljava/nio/file/Path;)Ljavan/util/ProcessRunner$Result;", Optional.empty())
        )).isFalse();
    }

    @Test
    void reportLinesDescribeProcessRunnerNativeLowering() {
        assertThat(JavanNativeSubstitutions.reportLines()).containsExactly(
            "javan/util/ProcessRunner.run(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result; -> javan_process_run",
            "javan/util/ProcessRunner.runAttached(Ljava/nio/file/Path;Ljava/util/List;Ljava/io/PrintStream;Ljava/io/PrintStream;)Ljavan/util/ProcessRunner$Result; -> javan_process_run + captured stream forwarding"
        );
    }

    private static MethodRef processRunnerRunRef() {
        return new MethodRef("javan/util/ProcessRunner", "run", PROCESS_RUNNER_DESCRIPTOR);
    }

    private static MethodInfo processRunnerRunMethod() {
        return new MethodInfo(0, "run", PROCESS_RUNNER_DESCRIPTOR, Optional.empty());
    }
}
