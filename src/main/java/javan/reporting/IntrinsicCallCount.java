package javan.reporting;

/**
 * Count of bytecode call sites for a stable intrinsic name.
 *
 * @param name stable intrinsic name
 * @param count reachable call-site count
 */
public record IntrinsicCallCount(String name, int count) {
}
