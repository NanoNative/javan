package javan.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javan.classfile.ClassFile;
import javan.classfile.FieldInfo;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

/** Discovers field metadata required by statically proven declared-field lookups. */
public final class ReflectionFieldReachability {
    private ReflectionFieldReachability() {
    }

    /**
     * Finds declared fields when both the receiving class and field name are statically known.
     *
     * @param classes compiled closed-world classes
     * @param reachable reachable methods
     * @return discovered exact field identities
     */
    public static Set<FieldRef> literalLookups(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final Set<FieldRef> result = new HashSet<>();
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 2; index < instructions.size(); index++) {
                final Instruction instruction = instructions.get(index);
                final Optional<MethodRef> target = instruction.methodRef();
                if (instruction.opcode() != 182 || target.isEmpty()
                    || !"java/lang/Class".equals(target.orElseThrow().owner())
                    || !"getDeclaredField".equals(target.orElseThrow().name())
                    || !"(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(target.orElseThrow().descriptor())) continue;
                final Set<String> names = ReflectionLiteralFlow.literals(method.orElseThrow(), index - 1);
                final Set<String> types = ReflectionLiteralFlow.classNames(method.orElseThrow(), index - 2);
                for (final String type : types) {
                    final ClassFile fieldOwner = classes.get(type);
                    if (fieldOwner == null) continue;
                    for (final FieldInfo field : fieldOwner.fields()) {
                        if (names.contains(field.name())) result.add(new FieldRef(type, field.name(), field.descriptor()));
                    }
                }
            }
        }
        return Set.copyOf(result);
    }
}
