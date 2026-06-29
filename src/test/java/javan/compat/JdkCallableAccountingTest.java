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
    void marksStringValueOfIntAsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "valueOf", "(I)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }
}
