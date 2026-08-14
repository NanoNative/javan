package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.MethodInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ClassInitializationOrderTest {
    @Test
    void ordersSuperclassesAndQualifyingDefaultInterfacesWithoutInitializingInterfaceParentsDirectly() {
        final ClassFile parentDefault = type("com/acme/ParentDefault", "java/lang/Object", 0x0600, List.of(), List.of(defaultMethod()));
        final ClassFile parent = type("com/acme/Parent", "java/lang/Object", 0, List.of(parentDefault.name()), List.of());
        final ClassFile rootDefault = type("com/acme/RootDefault", "java/lang/Object", 0x0600, List.of(), List.of(defaultMethod()));
        final ClassFile childDefault = type("com/acme/ChildDefault", "java/lang/Object", 0x0600, List.of(rootDefault.name()), List.of());
        final ClassFile otherDefault = type("com/acme/OtherDefault", "java/lang/Object", 0x0600, List.of(), List.of(defaultMethod()));
        final ClassFile child = type(
            "com/acme/Child",
            parent.name(),
            0,
            List.of(childDefault.name(), otherDefault.name()),
            List.of()
        );
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        for (final ClassFile classFile : List.of(parentDefault, parent, rootDefault, childDefault, otherDefault, child)) {
            classes.put(classFile.name(), classFile);
        }

        assertThat(ClassInitializationOrder.order(classes, child.name())).containsExactly(
            parentDefault.name(),
            parent.name(),
            rootDefault.name(),
            otherDefault.name(),
            child.name()
        );
        assertThat(ClassInitializationOrder.dependencies(classes, child)).containsExactly(
            parent.name(),
            rootDefault.name(),
            otherDefault.name()
        );
        assertThat(ClassInitializationOrder.order(classes, childDefault.name())).containsExactly(childDefault.name());
    }

    private static ClassFile type(
        final String name,
        final String superName,
        final int accessFlags,
        final List<String> interfaces,
        final List<MethodInfo> methods
    ) {
        return new ClassFile(69, name, superName, accessFlags, interfaces, List.of(), methods, Path.of(name + ".class"), true);
    }

    private static MethodInfo defaultMethod() {
        return new MethodInfo(0x0001, "touch", "()V", Optional.of(new CodeAttribute(0, 1, new byte[0], 0, List.of())));
    }
}
