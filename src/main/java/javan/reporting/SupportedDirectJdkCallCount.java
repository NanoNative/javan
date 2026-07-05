package javan.reporting;

/**
 * Count of reachable JDK call sites accepted directly outside the exact support registry.
 *
 * @param target JVM method reference display string
 * @param count reachable call-site count
 */
public record SupportedDirectJdkCallCount(String target, int count) {
}
