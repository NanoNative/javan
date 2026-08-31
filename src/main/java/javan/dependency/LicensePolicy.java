package javan.dependency;

import java.util.List;
import java.util.Optional;

/**
 * Evaluates explicit project license rules against exact dependency metadata.
 *
 * <p>The policy does not identify licenses or make legal conclusions. Rules only match the exact
 * identifier that artifact metadata already supplied.</p>
 *
 * @param rules ordered project rules from {@code javan.mod}
 */
public record LicensePolicy(List<Rule> rules) {
    /**
     * Copies policy rules into an immutable policy.
     *
     * @param rules ordered project rules from {@code javan.mod}
     */
    public LicensePolicy {
        rules = List.copyOf(rules);
    }

    /**
     * Returns the policy used when a project declares no license rules.
     *
     * @return empty policy
     */
    public static LicensePolicy empty() {
        return new LicensePolicy(List.of());
    }

    /**
     * Classifies one detected dependency license without guessing its identity.
     *
     * @param license detected artifact metadata
     * @return deterministic report decision
     */
    public Decision decide(final ArtifactMetadata.License license) {
        if (!license.known()) {
            return new Decision("warning", Optional.empty());
        }
        final Optional<Rule> denied = rule("deny", license.id());
        if (denied.isPresent()) {
            return new Decision("blocked", denied);
        }
        final Optional<Rule> allowed = rule("allow", license.id());
        if (allowed.isPresent()) {
            return new Decision("allowed", allowed);
        }
        return new Decision("known", Optional.empty());
    }

    private Optional<Rule> rule(final String action, final String license) {
        for (final Rule rule : rules) {
            if (action.equals(rule.action()) && license.equals(rule.license())) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * One exact license rule from {@code javan.mod}.
     *
     * @param action either {@code allow} or {@code deny}
     * @param license exact detected license identifier
     * @param line one-based source line in {@code javan.mod}
     */
    public record Rule(String action, String license, int line) {
        /**
         * Validates one exact license rule.
         *
         * @param action either {@code allow} or {@code deny}
         * @param license exact detected license identifier
         * @param line one-based source line in {@code javan.mod}
         */
        public Rule {
            if (!"allow".equals(action) && !"deny".equals(action)) {
                throw new IllegalArgumentException("License policy action must be allow or deny");
            }
            if (license == null || license.isBlank() || "unknown".equals(license)) {
                throw new IllegalArgumentException("License policy requires a known exact identifier");
            }
            if (line < 1) {
                throw new IllegalArgumentException("License policy line must be positive");
            }
        }

        /**
         * Returns the deterministic source location shown in reports and diagnostics.
         *
         * @return source location
         */
        public String source() {
            return "javan.mod:" + line;
        }
    }

    /**
     * One report classification, optionally tied to a matching project rule.
     *
     * @param status report policy status
     * @param rule matching project rule when present
     */
    public record Decision(String status, Optional<Rule> rule) {
        /**
         * Returns whether the project explicitly denied the detected license.
         *
         * @return true for an advisory blocked report row
         */
        public boolean blocked() {
            return "blocked".equals(status);
        }

        /**
         * Returns the matching project-rule location, or empty when none matched.
         *
         * @return deterministic policy source
         */
        public String source() {
            return rule.map(Rule::source).orElse("");
        }
    }
}
