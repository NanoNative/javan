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
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_ENUM = 0x4000;

    /**
     * Finds a method by name and descriptor.
     *
     * @param methodName method name
     * @param descriptor method descriptor
     * @return matching method
     */
    public Optional<MethodInfo> method(final String methodName, final String descriptor) {
        for (final MethodInfo method : methods) {
            if (method.name().equals(methodName) && method.descriptor().equals(descriptor)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns true when this class cannot have subclasses.
     *
     * @return true when final
     */
    public boolean isFinal() {
        if ((accessFlags & ACC_FINAL) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when this class file describes an interface.
     *
     * @return true when interface
     */
    public boolean isInterface() {
        if ((accessFlags & ACC_INTERFACE) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when this class file is compiler-generated synthetic code.
     *
     * @return true when synthetic
     */
    public boolean isSynthetic() {
        if ((accessFlags & ACC_SYNTHETIC) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when this class file describes an enum type.
     *
     * @return true when enum
     */
    public boolean isEnum() {
        if ((accessFlags & ACC_ENUM) == 0) {
            return false;
        }
        return true;
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
