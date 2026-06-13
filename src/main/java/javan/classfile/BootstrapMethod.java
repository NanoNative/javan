package javan.classfile;

import java.util.List;

/**
 * Parsed BootstrapMethods entry.
 *
 * @param methodHandleIndex constant-pool method handle index
 * @param argumentIndexes constant-pool argument indexes
 */
public record BootstrapMethod(int methodHandleIndex, List<Integer> argumentIndexes) {
}
