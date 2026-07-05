package javan.reporting;

/**
 * Count of reachable exact supported runtime-registry JDK call sites.
 *
 * @param name stable runtime call name
 * @param count reachable call-site count
 */
public record RuntimeJdkCallCount(String name, int count) {
}
