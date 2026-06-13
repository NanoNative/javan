package javan.reporting;

/**
 * Reachable JDK method call that is not one of the planned intrinsic names.
 *
 * @param target JVM method reference display string
 * @param count reachable call-site count
 */
public record UnsupportedJdkCallCandidate(String target, int count) {
}
