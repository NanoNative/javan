package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.MethodRef;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines the exact generated-object subset supported by {@code Object.clone()}.
 */
public final class GeneratedObjectCloneSupport {
    private static final String CLONEABLE = "java/lang/Cloneable";
    private static final String OBJECT = "java/lang/Object";

    private GeneratedObjectCloneSupport() {
    }

    /**
     * Describes whether a generated class can use the object-clone lowering.
     */
    public enum Status {
        SUPPORTED,
        NOT_CLONEABLE,
        EXTERNAL_SUPERCLASS,
        CYCLIC_HIERARCHY
    }

    /**
     * Classifies a generated class for object-clone lowering.
     *
     * @param classes generated class metadata
     * @param classFile class containing the exact {@code Object.clone()} call
     * @return deterministic support status
     */
    public static Status status(final Map<String, ClassFile> classes, final ClassFile classFile) {
        if (!hasInterface(classes, classFile, CLONEABLE, new HashSet<>())) {
            return Status.NOT_CLONEABLE;
        }
        return layoutStatus(classes, classFile);
    }

    /**
     * Classifies an exact {@code Object.clone()} call in a generated method.
     *
     * <p>A non-cloneable base may legally declare the call when a generated runtime subtype
     * implements {@code Cloneable}; the runtime dispatch performs the receiver check.</p>
     *
     * @param classes generated class metadata
     * @param classFile class containing the exact {@code Object.clone()} call
     * @return deterministic invocation support status
     */
    public static Status invocationStatus(
        final Map<String, ClassFile> classes,
        final ClassFile classFile
    ) {
        final Status direct = status(classes, classFile);
        if (direct != Status.NOT_CLONEABLE) {
            return direct;
        }
        boolean externalLayout = false;
        boolean cyclicLayout = false;
        for (final ClassFile candidate : classes.values()) {
            if (!hasGeneratedSuperclass(classes, candidate, classFile.name())) {
                continue;
            }
            final Status candidateStatus = status(classes, candidate);
            if (candidateStatus == Status.SUPPORTED) {
                return Status.SUPPORTED;
            }
            externalLayout |= candidateStatus == Status.EXTERNAL_SUPERCLASS;
            cyclicLayout |= candidateStatus == Status.CYCLIC_HIERARCHY;
        }
        if (cyclicLayout) {
            return Status.CYCLIC_HIERARCHY;
        }
        if (externalLayout) {
            return Status.EXTERNAL_SUPERCLASS;
        }
        return Status.NOT_CLONEABLE;
    }

    /**
     * Tests whether a method reference is the exact object-clone method.
     *
     * @param methodRef referenced method
     * @return whether this is {@code Object.clone()}
     */
    public static boolean isObjectClone(final MethodRef methodRef) {
        return OBJECT.equals(methodRef.owner())
            && "clone".equals(methodRef.name())
            && "()Ljava/lang/Object;".equals(methodRef.descriptor());
    }

    /**
     * Explains an unsupported status.
     *
     * @param status unsupported support status
     * @return deterministic diagnostic reason
     */
    public static String reason(final Status status) {
        if (status == Status.NOT_CLONEABLE) {
            return "Neither the containing class nor a generated runtime subtype implements java.lang.Cloneable through a supported generated hierarchy.";
        }
        if (status == Status.EXTERNAL_SUPERCLASS) {
            return "The containing class has an external superclass whose Java fields or runtime-attached native state cannot be copied safely.";
        }
        if (status == Status.CYCLIC_HIERARCHY) {
            return "The containing class has a cyclic superclass hierarchy, so a stable generated object layout cannot be proven.";
        }
        throw new IllegalArgumentException("supported object clone has no diagnostic");
    }

    /**
     * Suggests the correction for an unsupported status.
     *
     * @param status unsupported support status
     * @return deterministic diagnostic fix
     */
    public static String fix(final Status status) {
        if (status == Status.NOT_CLONEABLE) {
            return "Implement Cloneable on the containing generated class or a generated runtime subtype, or remove the Object.clone call.";
        }
        if (status == Status.EXTERNAL_SUPERCLASS) {
            return "Keep cloneable generated classes on a generated superclass chain ending at java.lang.Object, or keep this clone path on the JVM.";
        }
        if (status == Status.CYCLIC_HIERARCHY) {
            return "Fix the superclass cycle before compiling this clone path.";
        }
        throw new IllegalArgumentException("supported object clone has no diagnostic");
    }

    private static Status layoutStatus(final Map<String, ClassFile> classes, final ClassFile classFile) {
        final Set<String> visited = new HashSet<>();
        ClassFile current = classFile;
        while (true) {
            if (!visited.add(current.name())) {
                return Status.CYCLIC_HIERARCHY;
            }
            final String superName = current.superName();
            if (OBJECT.equals(superName)) {
                return Status.SUPPORTED;
            }
            if (superName == null || superName.isEmpty()) {
                return Status.EXTERNAL_SUPERCLASS;
            }
            final ClassFile superClass = classes.get(superName);
            if (superClass == null) {
                return Status.EXTERNAL_SUPERCLASS;
            }
            current = superClass;
        }
    }

    private static boolean hasInterface(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final String expected,
        final Set<String> visited
    ) {
        if (!visited.add(classFile.name())) {
            return false;
        }
        if (classFile.interfaces().contains(expected)) {
            return true;
        }
        for (final String interfaceName : classFile.interfaces()) {
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null && hasInterface(classes, interfaceClass, expected, visited)) {
                return true;
            }
        }
        final String superName = classFile.superName();
        if (superName == null || superName.isEmpty()) {
            return false;
        }
        final ClassFile superClass = classes.get(superName);
        return superClass != null && hasInterface(classes, superClass, expected, visited);
    }

    private static boolean hasGeneratedSuperclass(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final String expected
    ) {
        final Set<String> visited = new HashSet<>();
        ClassFile current = classFile;
        while (visited.add(current.name())) {
            if (expected.equals(current.name())) {
                return true;
            }
            final String superName = current.superName();
            if (superName == null || superName.isEmpty()) {
                return false;
            }
            final ClassFile superClass = classes.get(superName);
            if (superClass == null) {
                return false;
            }
            current = superClass;
        }
        return false;
    }
}
