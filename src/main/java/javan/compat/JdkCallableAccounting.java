package javan.compat;

import javan.classfile.MethodRef;
import javan.verify.ForbiddenApiRules;

/**
 * Exact callable-level accounting status for scanned JDK members.
 */
final class JdkCallableAccounting {
    private static final ForbiddenApiRules FORBIDDEN_API_RULES = new ForbiddenApiRules();

    private JdkCallableAccounting() {
    }

    static Status status(final MethodRef methodRef) {
        if (JdkCallSupport.isSupported(methodRef)) {
            return Status.SUPPORTED;
        }
        if (FORBIDDEN_API_RULES.forbiddenReason(methodRef).isPresent() || isExactRejected(methodRef)) {
            return Status.EXPLICIT_REJECTED;
        }
        return Status.UNKNOWN;
    }

    private static boolean isExactRejected(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        final String methodName = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("java/lang/Object".equals(owner)) {
            if ("wait".equals(methodName)) {
                return "()V".equals(descriptor) || "(J)V".equals(descriptor) || "(JI)V".equals(descriptor);
            }
            if ("notify".equals(methodName) || "notifyAll".equals(methodName)) {
                return "()V".equals(descriptor);
            }
            return false;
        }
        if ("java/util/concurrent/Executors".equals(owner)) {
            if ("newSingleThreadExecutor".equals(methodName) || "newCachedThreadPool".equals(methodName)) {
                return "()Ljava/util/concurrent/ExecutorService;".equals(descriptor);
            }
            return false;
        }
        if (owner.startsWith("jdk/jfr/")) {
            return true;
        }
        if ("sun/misc/Unsafe".equals(owner)) {
            return true;
        }
        return "java/lang/InheritableThreadLocal".equals(owner)
            && "<init>".equals(methodName)
            && "()V".equals(descriptor);
    }

    enum Status {
        SUPPORTED,
        EXPLICIT_REJECTED,
        UNKNOWN
    }
}
