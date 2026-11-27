package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared conservative method targets for an explicit reflection declaration. */
public final class ReflectionMethodReachability {
    private ReflectionMethodReachability() {
    }

    /**
     * Collects literal values supplied to supported reflective method lookups.
     *
     * @param classes parsed closed-world classes
     * @param reachable reachable method identities
     * @return literal method names traced to lookup arguments
     */
    public static Set<String> literalNames(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final Set<String> names = new HashSet<>();
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            if (owner != null && owner.application() && "javanReflectionTargets".equals(entry.methodName())
                && "()[Ljava/lang/reflect/AnnotatedElement;".equals(entry.descriptor())) {
                continue;
            }
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final CodeAttribute code = method.orElseThrow().code().orElseThrow();
            final List<Instruction> instructions = code.instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Instruction instruction = instructions.get(index);
                final Optional<MethodRef> reference = instruction.methodRef();
                if (reference.isEmpty()) {
                    continue;
                }
                final MethodRef target = reference.orElseThrow();
                if (instruction.opcode() != 182 || !"java/lang/Class".equals(target.owner())
                    || !("getDeclaredMethod".equals(target.name()) || "getMethod".equals(target.name()))
                    || !"(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(target.descriptor())) {
                    continue;
                }
                int cursor = index - 1;
                if (cursor < 0) {
                    continue;
                }
                final int opcode = instructions.get(cursor).opcode();
                if (opcode == 1 || opcode == 25 || opcode >= 42 && opcode <= 45) {
                    cursor--;
                } else {
                    int entries = 0;
                    while (cursor >= 3 && instructions.get(cursor).opcode() == 83
                        && instructions.get(cursor - 3).opcode() == 89
                        && instructions.get(cursor - 2).intValue().isPresent()) {
                        entries++;
                        cursor -= 4;
                    }
                    if (cursor < 1 || instructions.get(cursor).opcode() != 189
                        || !"java/lang/Class".equals(instructions.get(cursor).className().orElse(""))
                        || instructions.get(cursor - 1).intValue().orElse(-1) != entries) {
                        continue;
                    }
                    cursor -= 2;
                }
                if (cursor >= 0) {
                    names.addAll(ReflectionStringFlow.literals(method.orElseThrow(), cursor));
                }
            }
        }
        return Set.copyOf(names);
    }

    /**
     * Checks an explicitly declared or conservatively discovered method identity.
     *
     * @param classes scanned closed-world classes
     * @param declaration parsed project declaration
     * @param literalNames literal method names from reachable code
     * @param owner declaring JVM class name
     * @param name method name
     * @param descriptor JVM method descriptor
     * @return whether the method is in the retained set
     */
    public static boolean includes(
        final Map<String, ClassFile> classes,
        final ReflectionDeclaration.Result declaration,
        final Set<String> literalNames,
        final String owner,
        final String name,
        final String descriptor
    ) {
        if (!declaration.present() || literalNames.contains(name)
            || declaration.methods().contains(new EntryPoint(owner, name, descriptor))) {
            return true;
        }
        final ClassFile candidateOwner = classes.get(owner);
        final Optional<MethodInfo> candidate = candidateOwner == null
            ? Optional.empty() : candidateOwner.method(name, descriptor);
        if (candidate.isEmpty() || candidate.orElseThrow().isStatic() || candidate.orElseThrow().isPrivate()) {
            return false;
        }
        for (final EntryPoint declared : declaration.methods()) {
            final int parameterEnd = declared.descriptor().indexOf(')');
            if (!declared.methodName().equals(name)
                || !descriptor.startsWith(declared.descriptor().substring(0, parameterEnd + 1))
                || !ReachabilityAnalyzer.isAssignableTo(classes, owner, declared.className())) {
                continue;
            }
            final ClassFile declaredOwner = classes.get(declared.className());
            final Optional<MethodInfo> base = declaredOwner == null
                ? Optional.empty() : declaredOwner.method(declared.methodName(), declared.descriptor());
            if (base.isPresent() && !base.orElseThrow().isStatic() && !base.orElseThrow().isPrivate()) {
                final boolean samePackage = owner.substring(0, owner.lastIndexOf('/') + 1)
                    .equals(declared.className().substring(0, declared.className().lastIndexOf('/') + 1));
                if ((base.orElseThrow().accessFlags() & 0x0005) != 0 || samePackage) {
                    return true;
                }
            }
        }
        return false;
    }
}
