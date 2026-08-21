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
 * @param sourceFile SourceFile attribute value when present
 * @param recordComponents Record attribute components when the attribute is present
 * @param permittedSubclasses PermittedSubclasses attribute owners in source order, empty when absent
 * @param nestHost JVM name of the nest host, equal to {@code name} for a nest host
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
    Optional<String> sourceFile,
    Optional<List<RecordComponentInfo>> recordComponents,
    List<String> permittedSubclasses,
    String nestHost,
    Path source,
    boolean application
) {
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_ABSTRACT = 0x0400;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_ENUM = 0x4000;

    /**
     * Creates a class file without parsed source-file metadata.
     *
     * @param majorVersion class file major version
     * @param name JVM internal class name
     * @param superName JVM internal superclass name
     * @param accessFlags class access flags
     * @param interfaces JVM internal interface names implemented by this class
     * @param fields fields declared by the class
     * @param methods methods declared by the class
     * @param source source class file path
     * @param application whether the class belongs to the application input
     */
    public ClassFile(
        final int majorVersion,
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final List<FieldInfo> fields,
        final List<MethodInfo> methods,
        final Path source,
        final boolean application
    ) {
        this(
            majorVersion,
            name,
            superName,
            accessFlags,
            interfaces,
            fields,
            methods,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            name,
            source,
            application
        );
    }

    /**
     * Creates a class file without a parsed Record attribute.
     *
     * @param majorVersion class file major version
     * @param name JVM internal class name
     * @param superName JVM internal superclass name
     * @param accessFlags class access flags
     * @param interfaces JVM internal interface names implemented by this class
     * @param fields fields declared by the class
     * @param methods methods declared by the class
     * @param sourceFile parsed source-file metadata
     * @param source source class file path
     * @param application whether the class belongs to the application input
     */
    public ClassFile(
        final int majorVersion,
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final List<FieldInfo> fields,
        final List<MethodInfo> methods,
        final Optional<String> sourceFile,
        final Path source,
        final boolean application
    ) {
        this(
            majorVersion,
            name,
            superName,
            accessFlags,
            interfaces,
            fields,
            methods,
            sourceFile,
            Optional.empty(),
            List.of(),
            name,
            source,
            application
        );
    }

    public ClassFile {
        if (recordComponents.isPresent()) {
            recordComponents = Optional.of(List.copyOf(recordComponents.orElseThrow()));
        }
        permittedSubclasses = List.copyOf(permittedSubclasses);
    }

    /**
     * Creates a class file without parsed nest-host metadata.
     *
     * @param majorVersion class file major version
     * @param name JVM internal class name
     * @param superName JVM internal superclass name
     * @param accessFlags class access flags
     * @param interfaces JVM internal interface names implemented by this class
     * @param fields fields declared by the class
     * @param methods methods declared by the class
     * @param sourceFile parsed source-file metadata
     * @param recordComponents parsed record metadata
     * @param permittedSubclasses parsed permitted subclasses
     * @param source source class file path
     * @param application whether the class belongs to the application input
     */
    public ClassFile(
        final int majorVersion,
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final List<FieldInfo> fields,
        final List<MethodInfo> methods,
        final Optional<String> sourceFile,
        final Optional<List<RecordComponentInfo>> recordComponents,
        final List<String> permittedSubclasses,
        final Path source,
        final boolean application
    ) {
        this(
            majorVersion,
            name,
            superName,
            accessFlags,
            interfaces,
            fields,
            methods,
            sourceFile,
            recordComponents,
            permittedSubclasses,
            name,
            source,
            application
        );
    }

    /**
     * Creates a class file without parsed permitted-subclass metadata.
     *
     * @param majorVersion class file major version
     * @param name JVM internal class name
     * @param superName JVM internal superclass name
     * @param accessFlags class access flags
     * @param interfaces JVM internal interface names implemented by this class
     * @param fields fields declared by the class
     * @param methods methods declared by the class
     * @param sourceFile parsed source-file metadata
     * @param recordComponents parsed record metadata
     * @param source source class file path
     * @param application whether the class belongs to the application input
     */
    public ClassFile(
        final int majorVersion,
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final List<FieldInfo> fields,
        final List<MethodInfo> methods,
        final Optional<String> sourceFile,
        final Optional<List<RecordComponentInfo>> recordComponents,
        final Path source,
        final boolean application
    ) {
        this(
            majorVersion,
            name,
            superName,
            accessFlags,
            interfaces,
            fields,
            methods,
            sourceFile,
            recordComponents,
            List.of(),
            name,
            source,
            application
        );
    }

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
     * Returns true when this class or interface is public.
     *
     * @return true when public
     */
    public boolean isPublic() {
        return (accessFlags & ACC_PUBLIC) != 0;
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
     * Returns true when this class file describes an abstract class or interface.
     *
     * @return true when abstract
     */
    public boolean isAbstract() {
        if ((accessFlags & ACC_ABSTRACT) == 0) {
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
     * Returns true when the classfile contains an authoritative Record attribute.
     *
     * @return true when record metadata is present
     */
    public boolean isRecord() {
        return recordComponents.isPresent();
    }

    /**
     * Returns a copy with an explicit application/dependency flag.
     *
     * @param value true for application classes
     * @return updated class file
     */
    public ClassFile withApplication(final boolean value) {
        return new ClassFile(
            majorVersion,
            name,
            superName,
            accessFlags,
            interfaces,
            fields,
            methods,
            sourceFile,
            recordComponents,
            permittedSubclasses,
            nestHost,
            source,
            value
        );
    }
}
