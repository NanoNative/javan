package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Calculates the generated-class initialization order required by JVM active use.
 */
public final class ClassInitializationOrder {
    private ClassInitializationOrder() {
    }

    /**
     * Returns the generated classes that initialize for active use of {@code className}.
     *
     * @param classes parsed closed-world classes
     * @param className JVM internal class name
     * @return root-to-leaf initialization order, or empty when the class is external
     */
    public static List<String> order(final Map<String, ClassFile> classes, final String className) {
        final ClassFile classFile = classes.get(className);
        if (classFile == null) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        appendClassOrder(classes, classFile, new HashSet<>(), new HashSet<>(), result);
        return List.copyOf(result);
    }

    /**
     * Returns the generated classes that must initialize before {@code classFile} itself.
     *
     * @param classes parsed closed-world classes
     * @param classFile generated class metadata source
     * @return direct superclass followed by qualifying default-method interfaces
     */
    public static List<String> dependencies(final Map<String, ClassFile> classes, final ClassFile classFile) {
        if (classFile.isInterface()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        final ClassFile superClass = classes.get(classFile.superName());
        if (superClass != null) {
            result.add(superClass.name());
        }
        final Set<String> initializedInterfaces = new HashSet<>();
        for (final String interfaceName : classFile.interfaces()) {
            appendDefaultInterfaceOrder(classes, interfaceName, initializedInterfaces, result);
        }
        return List.copyOf(result);
    }

    /**
     * Returns the class actively used by an instruction, when the instruction triggers JVM initialization.
     *
     * @param instruction decoded JVM instruction
     * @return referenced class owner for {@code getstatic}, {@code putstatic}, {@code invokestatic}, or {@code new}
     */
    public static Optional<String> activeUseOwner(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 178, 179 -> instruction.fieldRef().isEmpty()
                ? Optional.empty()
                : Optional.of(instruction.fieldRef().orElseThrow().owner());
            case 184 -> instruction.methodRef().isEmpty()
                ? Optional.empty()
                : Optional.of(instruction.methodRef().orElseThrow().owner());
            case 187 -> instruction.className();
            default -> Optional.empty();
        };
    }

    private static void appendClassOrder(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final Set<String> initializedClasses,
        final Set<String> initializedInterfaces,
        final List<String> result
    ) {
        if (!initializedClasses.add(classFile.name())) {
            return;
        }
        if (classFile.isInterface()) {
            result.add(classFile.name());
            return;
        }
        final ClassFile superClass = classes.get(classFile.superName());
        if (superClass != null) {
            appendClassOrder(classes, superClass, initializedClasses, initializedInterfaces, result);
        }
        for (final String interfaceName : classFile.interfaces()) {
            appendDefaultInterfaceOrder(classes, interfaceName, initializedInterfaces, result);
        }
        result.add(classFile.name());
    }

    private static void appendDefaultInterfaceOrder(
        final Map<String, ClassFile> classes,
        final String interfaceName,
        final Set<String> initializedInterfaces,
        final List<String> result
    ) {
        if (!initializedInterfaces.add(interfaceName)) {
            return;
        }
        final ClassFile interfaceClass = classes.get(interfaceName);
        if (interfaceClass == null || !interfaceClass.isInterface()) {
            return;
        }
        for (final String superInterface : interfaceClass.interfaces()) {
            appendDefaultInterfaceOrder(classes, superInterface, initializedInterfaces, result);
        }
        if (declaresDefaultMethod(interfaceClass)) {
            result.add(interfaceClass.name());
        }
    }

    private static boolean declaresDefaultMethod(final ClassFile classFile) {
        for (final MethodInfo method : classFile.methods()) {
            if (method.code().isPresent() && !method.isStatic() && (method.accessFlags() & 0x0002) == 0) {
                return true;
            }
        }
        return false;
    }
}
