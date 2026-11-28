package javan.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javan.classfile.ClassFile;
import javan.classfile.FieldInfo;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactoryCall;
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
        final List<EntryPoint> classArgumentConsumers = new ArrayList<>();
        final List<EntryPoint> calledConsumers = new ArrayList<>();
        boolean needsDeclaredTargets = false;
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            final boolean unchangedArgument = hasUnmodifiedClassParameter(entry, method.orElseThrow(), instructions);
            for (int index = 1; index < instructions.size(); index++) {
                final Optional<MethodRef> target = instructions.get(index).methodRef();
                if (target.isEmpty() || !"java/lang/Class".equals(target.orElseThrow().owner())
                    || !"getDeclaredFields".equals(target.orElseThrow().name())
                    || !"()[Ljava/lang/reflect/Field;".equals(target.orElseThrow().descriptor())) continue;
                final Instruction receiver = instructions.get(index - 1);
                recordClassArgumentConsumer(classArgumentConsumers, entry, receiver, unchangedArgument, false);
                needsDeclaredTargets = requiresDeclaredTargets(
                    instructions, index - 1, unchangedArgument && !method.orElseThrow().isPublic(), needsDeclaredTargets);
                for (final String name : ReflectionLiteralFlow.classNames(method.orElseThrow(), index - 1)) {
                    if (classes.containsKey(name)) owners.add(name);
                }
            }
        }
        boolean changed;
        do {
            changed = false;
            for (final EntryPoint entry : reachable) {
                final ClassFile owner = classes.get(entry.className());
                final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
                if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
                final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
                final boolean unchangedArgument = hasUnmodifiedClassParameter(entry, method.orElseThrow(), instructions);
                for (int index = 1; index < instructions.size(); index++) {
                    final Instruction call = instructions.get(index);
                    if (call.opcode() != 184 || call.methodRef().isEmpty()) continue;
                    final MethodRef target = call.methodRef().orElseThrow();
                    final EntryPoint consumer = new EntryPoint(target.owner(), target.name(), target.descriptor());
                    if (!classArgumentConsumers.contains(consumer)) continue;
                    calledConsumers.add(consumer);
                    owners.addAll(ReflectionLiteralFlow.classNames(method.orElseThrow(), index - 1));
                    final Instruction argument = instructions.get(index - 1);
                    needsDeclaredTargets = requiresDeclaredTargets(
                        instructions, index - 1, unchangedArgument && !method.orElseThrow().isPublic(), needsDeclaredTargets);
                    changed = recordClassArgumentConsumer(classArgumentConsumers, entry, argument, unchangedArgument, changed);
                }
            }
        } while (changed);
        needsDeclaredTargets = hasUnresolvedConsumers(classArgumentConsumers, calledConsumers, needsDeclaredTargets);
        needsDeclaredTargets = hasMethodReferenceConsumers(classes, reachable, classArgumentConsumers, needsDeclaredTargets);
        if (needsDeclaredTargets) owners.addAll(ReflectionDeclaration.analyze(classes).classes());
        return Set.copyOf(owners);
    }

    private static boolean requiresDeclaredTargets(
        final List<Instruction> instructions, final int sourceIndex, final boolean mayForward, final boolean required
    ) {
        if (required) return true;
        final Instruction source = instructions.get(sourceIndex);
        if (ReflectionDeclaration.classLiteralDescriptor(source).isPresent()) return false;
        if (mayForward && isFirstReferenceParameterLoad(source)) return false;
        if (source.opcode() != 184 || source.methodRef().isEmpty() || sourceIndex == 0) return true;
        final MethodRef target = source.methodRef().orElseThrow();
        if (!"java/lang/Class".equals(target.owner()) || !"forName".equals(target.name())
            || !"(Ljava/lang/String;)Ljava/lang/Class;".equals(target.descriptor())) return true;
        return instructions.get(sourceIndex - 1).stringValue().isEmpty();
    }

    private static boolean hasUnresolvedConsumers(
        final List<EntryPoint> consumers, final List<EntryPoint> called, final boolean required
    ) {
        if (required) return true;
        for (final EntryPoint consumer : consumers) if (!called.contains(consumer)) return true;
        return false;
    }

    private static boolean hasMethodReferenceConsumers(
        final Map<String, ClassFile> classes, final List<EntryPoint> reachable,
        final List<EntryPoint> consumers, final boolean required
    ) {
        if (required) return true;
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.dynamicRef().isEmpty()) continue;
                final var lambda = LambdaMetafactoryCall.resolve(instruction.dynamicRef().orElseThrow());
                if (lambda.isEmpty()) continue;
                final MethodRef implementation = lambda.orElseThrow().implementation();
                if (consumers.contains(new EntryPoint(implementation.owner(), implementation.name(), implementation.descriptor()))) return true;
            }
        }
        return false;
    }

    private static boolean recordClassArgumentConsumer(
        final List<EntryPoint> consumers, final EntryPoint entry, final Instruction argument,
        final boolean unchangedArgument, final boolean changed
    ) {
        if (!unchangedArgument) return changed;
        if (!isFirstReferenceParameterLoad(argument)) return changed;
        if (consumers.contains(entry)) return changed;
        consumers.add(entry);
        return true;
    }

    private static boolean hasUnmodifiedClassParameter(
        final EntryPoint entry, final MethodInfo method, final List<Instruction> instructions
    ) {
        if (!method.isStatic() || !entry.descriptor().startsWith("(Ljava/lang/Class;)")) return false;
        for (final Instruction instruction : instructions) {
            final int opcode = instruction.opcode();
            if (opcode == 196) return false;
            if (opcode >= 54 && opcode <= 58 && instruction.operands().length > 0
                && (instruction.operands()[0] & 0xff) == 0) return false;
            if (opcode >= 59 && opcode <= 78 && (opcode - 59) % 4 == 0) return false;
            if (opcode == 132 && instruction.operands().length > 0 && (instruction.operands()[0] & 0xff) == 0) return false;
        }
        return true;
    }

    private static boolean isFirstReferenceParameterLoad(final Instruction instruction) {
        if (instruction.opcode() == 42) return true;
        if (instruction.opcode() != 25) return false;
        final byte[] operands = instruction.operands();
        if (operands.length == 0) return false;
        return (operands[0] & 0xff) == 0;
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
