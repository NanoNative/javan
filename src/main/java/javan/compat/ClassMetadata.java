package javan.compat;

import java.nio.file.Path;
import java.util.List;

/**
 * Deterministic classfile inventory entry.
 *
 * @param source source path
 * @param application whether the class belongs to the direct application input
 * @param moduleName JDK module name when known
 * @param minorVersion classfile minor version
 * @param majorVersion classfile major version
 * @param accessFlags JVM access flags
 * @param name JVM internal class name
 * @param superName JVM internal superclass name
 * @param interfaces JVM internal interface names
 * @param constantPoolTags constant pool tags encountered
 * @param attributes class attribute names
 * @param bootstrapMethods normalized bootstrap method patterns
 * @param fields fields
 * @param constructors constructors
 * @param methods methods excluding constructors
 */
public record ClassMetadata(
    Path source,
    boolean application,
    String moduleName,
    int minorVersion,
    int majorVersion,
    int accessFlags,
    String name,
    String superName,
    List<String> interfaces,
    List<Integer> constantPoolTags,
    List<String> attributes,
    List<String> bootstrapMethods,
    List<MemberMetadata> fields,
    List<MemberMetadata> constructors,
    List<MemberMetadata> methods
) {
    private static final int PREVIEW_MINOR_VERSION = 65_535;

    /**
     * Returns the package name using dot notation.
     *
     * @return package name or empty string
     */
    public String packageName() {
        final int slash = name.lastIndexOf('/');
        return slash < 0 ? "" : name.substring(0, slash).replace('/', '.');
    }

    /**
     * Returns true when the classfile was compiled with preview minor version.
     *
     * @return true for preview classfiles
     */
    public boolean preview() {
        return minorVersion == PREVIEW_MINOR_VERSION;
    }

    /**
     * Returns true when the class is deprecated.
     *
     * @return true when deprecated
     */
    public boolean deprecated() {
        return attributes.contains("Deprecated");
    }

    /**
     * Returns a copy tagged with a module name.
     *
     * @param module module name
     * @return tagged metadata
     */
    public ClassMetadata withModuleName(final String module) {
        return new ClassMetadata(
            source,
            application,
            module,
            minorVersion,
            majorVersion,
            accessFlags,
            name,
            superName,
            interfaces,
            constantPoolTags,
            attributes,
            bootstrapMethods,
            fields,
            constructors,
            methods
        );
    }

    /**
     * Returns a copy tagged as application or dependency code.
     *
     * @param value application flag
     * @return tagged metadata
     */
    public ClassMetadata withApplication(final boolean value) {
        return new ClassMetadata(
            source,
            value,
            moduleName,
            minorVersion,
            majorVersion,
            accessFlags,
            name,
            superName,
            interfaces,
            constantPoolTags,
            attributes,
            bootstrapMethods,
            fields,
            constructors,
            methods
        );
    }
}
