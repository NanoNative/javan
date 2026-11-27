package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Proves constructor targets for direct class or class-name and parameter literals. */
public final class ReflectionConstructorReachability {
    private ReflectionConstructorReachability() { }

    /**
     * Constructor targets proven by direct literals.
     *
     * @param targets exact constructor identities
     * @param complete whether every reachable lookup has this proven shape
     */
    public record Result(List<EntryPoint> targets, boolean complete) {
        public Result { targets = List.copyOf(targets); }
    }

    /**
     * Finds direct constructor targets in reachable methods.
     *
     * @param classes scanned application and dependency classes
     * @param reachable reachable method identities
     * @return exact targets and whether the analysis covers all lookup sites
     */
    public static Result analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<EntryPoint> result = new ArrayList<>();
        int lookups = 0;
        int proven = 0;
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
                    || !"getDeclaredConstructor".equals(reference.name())
                    || !"([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;".equals(reference.descriptor())) continue;
                lookups++;
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
                final Instruction receiver = code.get(cursor - 2);
                final Optional<String> targetName;
                if (receiver.opcode() == 18 || receiver.opcode() == 19) {
                    targetName = receiver.className();
                } else if (receiver.opcode() == 184) {
                    targetName = ReachabilityAnalyzer.literalClassForNameTarget(method.orElseThrow().code().orElseThrow(), receiver);
                } else {
                    targetName = Optional.empty();
                }
                if (targetName.isEmpty()) continue;
                final String target = targetName.orElseThrow();
                final ClassFile targetClass = classes.get(target);
                if (targetClass == null) continue;
                proven++;
                final StringBuilder descriptor = new StringBuilder("(");
                for (int parameter = reversed.size() - 1; parameter >= 0; parameter--) descriptor.append(reversed.get(parameter));
                descriptor.append(")V");
                if (targetClass.method("<init>", descriptor.toString()).isPresent()) {
                    final EntryPoint candidate = new EntryPoint(target, "<init>", descriptor.toString());
                    if (!result.contains(candidate)) result.add(candidate);
                }
            }
        }
        return new Result(result, lookups > 0 && lookups == proven);
    }
}
