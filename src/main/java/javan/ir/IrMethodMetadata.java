package javan.ir;

import java.util.List;

/**
 * Closed-world metadata for one declared Java method.
 *
 * @param name Java method name
 * @param parameterDescriptors exact JVM parameter descriptors in declaration order
 */
public record IrMethodMetadata(String name, List<String> parameterDescriptors) {
    /** Copies parameter metadata so the lowered program remains immutable. */
    public IrMethodMetadata {
        parameterDescriptors = List.copyOf(parameterDescriptors);
    }
}
