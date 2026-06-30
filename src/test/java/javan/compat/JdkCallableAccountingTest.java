package javan.compat;

import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkCallableAccountingTest {
    @Test
    void marksSupportedCallableAsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksForbiddenDynamicApiAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectWaitAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "wait", "(J)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectWaitLongIntAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "wait", "(JI)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectNotifyAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "notify", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectNotifyAllAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "notifyAll", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksUnsupportedExecutorFactoryAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/Executors", "newSingleThreadExecutor", "()Ljava/util/concurrent/ExecutorService;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksInheritableThreadLocalConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/InheritableThreadLocal", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksJfrCallableAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("jdk/jfr/FlightRecorder", "isAvailable", "()Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksUnsafeCallableAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("sun/misc/Unsafe", "getUnsafe", "()Lsun/misc/Unsafe;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringMatchesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "matches", "(Ljava/lang/String;)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringReplaceFirstAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "replaceFirst", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringReplaceAllAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringSplitRegexLimitAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "split", "(Ljava/lang/String;I)[Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringSplitRegexAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringSplitWithDelimitersAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "splitWithDelimiters", "(Ljava/lang/String;I)[Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringFormatVarargsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringFormatLocaleVarargsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "format", "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringFormattedAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "formatted", "([Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringToLowerCaseAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "toLowerCase", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringToUpperCaseLocaleAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "toUpperCase", "(Ljava/util/Locale;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringStripAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "strip", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringIsBlankAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "isBlank", "()Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringLinesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "lines", "()Ljava/util/stream/Stream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringIndentAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "indent", "(I)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringTranslateEscapesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "translateEscapes", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringValueOfIntAsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "valueOf", "(I)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }
}
