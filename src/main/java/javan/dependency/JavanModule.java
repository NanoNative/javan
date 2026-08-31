package javan.dependency;

import java.util.List;

/**
 * Parsed {@code javan.mod} model.
 *
 * @param present whether a module file exists
 * @param moduleName declared module name
 * @param javaVersion declared Java feature version
 * @param dependencies dependency declarations
 * @param licensePolicy explicit dependency-license policy
 * @param warnings non-fatal parser/resolver warnings
 */
public record JavanModule(
    boolean present,
    String moduleName,
    String javaVersion,
    List<JavanDependency> dependencies,
    LicensePolicy licensePolicy,
    List<String> warnings
) {
    /**
     * Creates a module without explicit license rules.
     *
     * @param present whether a module file exists
     * @param moduleName declared module name
     * @param javaVersion declared Java feature version
     * @param dependencies dependency declarations
     * @param warnings non-fatal parser/resolver warnings
     */
    public JavanModule(
        final boolean present,
        final String moduleName,
        final String javaVersion,
        final List<JavanDependency> dependencies,
        final List<String> warnings
    ) {
        this(present, moduleName, javaVersion, dependencies, LicensePolicy.empty(), warnings);
    }

    /**
     * Returns an absent module model.
     *
     * @return absent module
     */
    public static JavanModule absent() {
        return new JavanModule(false, "", "", List.of(), LicensePolicy.empty(), List.of());
    }
}
