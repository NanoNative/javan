package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Finds constructor targets from class and class-name flows with literal parameter types. */
public final class ReflectionConstructorReachability {
    private ReflectionConstructorReachability() { }

    /**
     * Finds statically discoverable constructor targets in reachable methods.
     *
     * @param classes scanned application and dependency classes
     * @param reachable reachable method identities
     * @return exact constructor targets discovered at lookup sites
     */
    public static List<EntryPoint> analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            if (owner != null && owner.application() && "javanReflectionTargets".equals(entry.methodName())
                && "()[Ljava/lang/reflect/AnnotatedElement;".equals(entry.descriptor())) continue;
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            final List<Instruction> code = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 0; index < code.size(); index++) {
                final Instruction lookup = code.get(index);
                final MethodRef reference = lookup.methodRef().orElse(null);
                if (lookup.opcode() != 182 || reference == null || !"java/lang/Class".equals(reference.owner())
                    || !("getDeclaredConstructor".equals(reference.name()) || "getConstructor".equals(reference.name()))
                    || !"([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;".equals(reference.descriptor())) continue;
                int cursor = index - 1;
                final List<String> reversed = new ArrayList<>();
                final List<Integer> indexes = new ArrayList<>();
                while (cursor >= 3 && code.get(cursor).opcode() == 83 && code.get(cursor - 3).opcode() == 89) {
                    final Optional<String> parameter = ReflectionDeclaration.classLiteralDescriptor(code.get(cursor - 1));
                    if (parameter.isEmpty() || code.get(cursor - 2).intValue().isEmpty()) break;
                    reversed.add(parameter.orElseThrow());
                    indexes.add(code.get(cursor - 2).intValue().orElseThrow());
                    cursor -= 4;
                }
                if (cursor < 2 || code.get(cursor).opcode() != 189
                    || !"java/lang/Class".equals(code.get(cursor).className().orElse(""))
                    || code.get(cursor - 1).intValue().orElse(-1) != reversed.size()) continue;
                boolean ordered = true;
                for (int position = 0; position < indexes.size(); position++) {
                    ordered &= indexes.get(position).intValue() == indexes.size() - position - 1;
                }
                if (!ordered) continue;
                final Set<String> targets = ReflectionLiteralFlow.classNames(method.orElseThrow(), cursor - 2);
                if (targets.isEmpty()) continue;
                final List<String> sortedTargets = new ArrayList<>();
                for (final String target : targets) sortedTargets.add(target);
                for (int position = 1; position < sortedTargets.size(); position++) {
                    final String target = sortedTargets.get(position);
                    int previous = position;
                    while (previous > 0 && Strings2.compareAscii(sortedTargets.get(previous - 1), target) > 0) {
                        sortedTargets.set(previous, sortedTargets.get(previous - 1));
                        previous--;
                    }
                    sortedTargets.set(previous, target);
                }
                final StringBuilder descriptor = new StringBuilder("(");
                for (int parameter = reversed.size() - 1; parameter >= 0; parameter--) descriptor.append(reversed.get(parameter));
                descriptor.append(")V");
                for (final String target : sortedTargets) {
                    final ClassFile targetClass = classes.get(target);
                    if (targetClass == null) continue;
                    final Optional<MethodInfo> constructor = targetClass.method("<init>", descriptor.toString());
                    if (constructor.isPresent()
                        && (!"getConstructor".equals(reference.name()) || constructor.orElseThrow().isPublic())) {
                        final EntryPoint candidate = new EntryPoint(target, "<init>", descriptor.toString());
                        if (!result.contains(candidate)) result.add(candidate);
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
