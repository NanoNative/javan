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
    /**
     * Finds closed-world classes whose declared fields can be enumerated.
     *
     * @param classes parsed closed-world classes
     * @param reachable reachable methods
     * @return classes selected by literal data flow or the project declaration
     */
    public static Set<String> enumeratedOwners(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final Set<String> owners = new HashSet<>();
        boolean needsDeclaredTargets = false;
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 1; index < instructions.size(); index++) {
                final Optional<MethodRef> target = instructions.get(index).methodRef();
                if (target.isEmpty() || !"java/lang/Class".equals(target.orElseThrow().owner())
                    || !"getDeclaredFields".equals(target.orElseThrow().name())
                    || !"()[Ljava/lang/reflect/Field;".equals(target.orElseThrow().descriptor())) continue;
                final Instruction receiver = instructions.get(index - 1);
                final boolean literalClass = ReflectionDeclaration.classLiteralDescriptor(receiver).isPresent();
                final boolean literalName = receiver.opcode() == 184 && receiver.methodRef().isPresent()
                    && "java/lang/Class".equals(receiver.methodRef().orElseThrow().owner())
                    && "forName".equals(receiver.methodRef().orElseThrow().name())
                    && "(Ljava/lang/String;)Ljava/lang/Class;".equals(receiver.methodRef().orElseThrow().descriptor())
                    && index >= 2 && instructions.get(index - 2).stringValue().isPresent();
                needsDeclaredTargets |= !literalClass && !literalName;
                for (final String name : ReflectionLiteralFlow.classNames(method.orElseThrow(), index - 1)) {
                    if (classes.containsKey(name)) owners.add(name);
                }
            }
        }
        if (needsDeclaredTargets) owners.addAll(ReflectionDeclaration.analyze(classes).classes());
        return Set.copyOf(owners);
    }

    /**
     * Returns owners of selected static fields when reflective integer reads or writes are reachable.
     *
     * @param classes parsed closed-world classes
     * @param reachable reachable methods
     * @return static field owners requiring class initialization
     */
    public static Set<String> staticIntOwners(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        boolean readsInt = false;
        boolean writesInt = false;
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<MethodRef> target = instruction.methodRef();
                if (target.isEmpty() || !"java/lang/reflect/Field".equals(target.orElseThrow().owner())) continue;
                if ("getInt".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)I".equals(target.orElseThrow().descriptor())) readsInt = true;
                if ("setInt".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;I)V".equals(target.orElseThrow().descriptor())) writesInt = true;
            }
        }
        if (!readsInt && !writesInt) return Set.of();
        final Set<FieldRef> fields = new HashSet<>(literalLookups(classes, reachable));
        fields.addAll(ReflectionDeclaration.analyze(classes).fields());
        for (final String name : enumeratedOwners(classes, reachable)) {
            final ClassFile owner = classes.get(name);
            if (owner != null) {
                for (final FieldInfo field : owner.fields()) fields.add(new FieldRef(name, field.name(), field.descriptor()));
            }
        }
        final Set<String> owners = new HashSet<>();
        for (final FieldRef field : fields) {
            final ClassFile owner = classes.get(field.owner());
            if (owner == null) continue;
            for (final FieldInfo candidate : owner.fields()) {
                if (candidate.name().equals(field.name()) && candidate.descriptor().equals(field.descriptor())
                    && candidate.isStatic() && (candidate.accessFlags() & 0x0040) == 0
                    && (readsInt && List.of("B", "S", "C", "I").contains(candidate.descriptor())
                        || writesInt && List.of("I", "J", "F", "D").contains(candidate.descriptor())
                            && (candidate.accessFlags() & 0x0010) == 0)) owners.add(field.owner());
            }
        }
        return Set.copyOf(owners);
    }

}
