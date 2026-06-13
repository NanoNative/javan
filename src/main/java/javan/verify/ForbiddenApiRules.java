package javan.verify;

import javan.classfile.MethodRef;

import java.util.Optional;

/**
 * Static-profile forbidden API rules.
 */
public final class ForbiddenApiRules {
    /**
     * Checks a method reference against forbidden dynamic APIs.
     *
     * @param methodRef method reference
     * @return reason when forbidden
     */
    public Optional<String> forbiddenReason(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        if ("java/lang/Class".equals(owner) && "forName".equals(name)) {
            return Optional.of("dynamic class loading is not supported");
        }
        if ("java/lang/ClassLoader".equals(owner) || owner.startsWith("java/lang/ClassLoader$")) {
            return Optional.of("class loaders are dynamic runtime infrastructure");
        }
        if ("java/lang/reflect/Proxy".equals(owner)) {
            return Optional.of("dynamic proxies require runtime type generation");
        }
        if (owner.startsWith("java/lang/reflect/")) {
            return Optional.of("reflection is outside the static native profile");
        }
        if (owner.startsWith("java/lang/invoke/MethodHandle") || owner.startsWith("java/lang/invoke/MethodHandles")) {
            return Optional.of("method handles are dynamic invocation machinery");
        }
        if (owner.startsWith("java/lang/instrument/")) {
            return Optional.of("Java agents and instrumentation are not supported");
        }
        if ("java/io/ObjectInputStream".equals(owner) || "java/io/ObjectOutputStream".equals(owner)) {
            return Optional.of("Java object serialization depends on dynamic metadata");
        }
        if ("java/lang/System".equals(owner) && ("load".equals(name) || "loadLibrary".equals(name))) {
            return Optional.of("loading native libraries at runtime breaks closed-world native compilation");
        }
        return Optional.empty();
    }
}
