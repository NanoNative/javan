package javan.classfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Parsed Java class file.
 *
 * @param majorVersion class file major version
 * @param name JVM internal class name
 * @param superName JVM internal superclass name
 * @param accessFlags class access flags
 * @param interfaces JVM internal interface names implemented by this class
 * @param fields fields declared by the class
 * @param methods methods declared by the class
 * @param source source class file path
 * @param application whether the class belongs to the application input rather than a dependency
 */
public record ClassFile(
    int majorVersion,
    String name,
    String superName,
    int accessFlags,
    List<String> interfaces,
    List<FieldInfo> fields,
    List<MethodInfo> methods,
    Path source,
    boolean application
) {
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_ENUM = 0x4000;

    /**
     * Finds a method by name and descriptor.
     *
     * @param methodName method name
     * @param descriptor method descriptor
     * @return matching method
     */
    public Optional<MethodInfo> method(final String methodName, final String descriptor) {
        return methods.stream()
            .filter(method -> method.name().equals(methodName) && method.descriptor().equals(descriptor))
            .findFirst();
    }

    /**
     * Returns true when this class cannot have subclasses.
     *
     * @return true when final
     */
    public boolean isFinal() {
        return (accessFlags & ACC_FINAL) != 0;
    }

    /**
     * Returns true when this class file describes an interface.
     *
     * @return true when interface
     */
    public boolean isInterface() {
        return (accessFlags & ACC_INTERFACE) != 0;
    }

    /**
     * Returns true when this class file describes an enum type.
     *
     * @return true when enum
     */
    public boolean isEnum() {
        return (accessFlags & ACC_ENUM) != 0;
    }

    /**
     * Returns a copy with an explicit application/dependency flag.
     *
     * @param value true for application classes
     * @return updated class file
     */
    public ClassFile withApplication(final boolean value) {
        return new ClassFile(majorVersion, name, superName, accessFlags, interfaces, fields, methods, source, value);
    }
}
