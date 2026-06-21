package javan.dependency;

import java.util.List;

/**
 * Parsed {@code javan.mod} model.
 *
 * @param present whether a module file exists
 * @param moduleName declared module name
 * @param javaVersion declared Java feature version
 * @param dependencies dependency declarations
 * @param warnings non-fatal parser/resolver warnings
 */
public record JavanModule(
    boolean present,
    String moduleName,
    String javaVersion,
    List<JavanDependency> dependencies,
    List<String> warnings
) {
    /**
     * Returns an absent module model.
     *
     * @return absent module
     */
    public static JavanModule absent() {
        return new JavanModule(false, "", "", List.of(), List.of());
    }
}
