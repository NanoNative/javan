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
import javan.codegen.MethodDescriptor;
import javan.ir.IrType;

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
            final int classArgumentSlot = unmodifiedTrailingClassParameterSlot(entry, method.orElseThrow(), instructions);
            for (int index = 1; index < instructions.size(); index++) {
                final Optional<MethodRef> target = instructions.get(index).methodRef();
                if (target.isEmpty() || !"java/lang/Class".equals(target.orElseThrow().owner())
                    || !"getDeclaredFields".equals(target.orElseThrow().name())
                    || !"()[Ljava/lang/reflect/Field;".equals(target.orElseThrow().descriptor())) continue;
                final Instruction receiver = instructions.get(index - 1);
                recordClassArgumentConsumer(classArgumentConsumers, entry, receiver, classArgumentSlot, false);
                needsDeclaredTargets = requiresDeclaredTargets(
                    instructions, index - 1, classArgumentSlot, method.orElseThrow().isPublic(), needsDeclaredTargets);
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
                final int classArgumentSlot = unmodifiedTrailingClassParameterSlot(entry, method.orElseThrow(), instructions);
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
                        instructions, index - 1, classArgumentSlot, method.orElseThrow().isPublic(), needsDeclaredTargets);
                    changed = recordClassArgumentConsumer(classArgumentConsumers, entry, argument, classArgumentSlot, changed);
                }
            }
        } while (changed);
        needsDeclaredTargets = hasUnresolvedConsumers(classArgumentConsumers, calledConsumers, needsDeclaredTargets);
        needsDeclaredTargets = hasMethodReferenceConsumers(classes, reachable, classArgumentConsumers, needsDeclaredTargets);
        if (needsDeclaredTargets) owners.addAll(ReflectionDeclaration.analyze(classes).classes());
        return Set.copyOf(owners);
    }

    private static boolean requiresDeclaredTargets(
        final List<Instruction> instructions, final int sourceIndex, final int classArgumentSlot,
        final boolean publicMethod, final boolean required
    ) {
        if (required) return true;
        final Instruction source = instructions.get(sourceIndex);
        if (ReflectionDeclaration.classLiteralDescriptor(source).isPresent()) return false;
        if (!publicMethod && classArgumentSlot >= 0 && isReferenceParameterLoad(source, classArgumentSlot)) return false;
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
        final int classArgumentSlot, final boolean changed
    ) {
        if (classArgumentSlot < 0) return changed;
        if (!isReferenceParameterLoad(argument, classArgumentSlot)) return changed;
        if (consumers.contains(entry)) return changed;
        consumers.add(entry);
        return true;
    }

    private static int unmodifiedTrailingClassParameterSlot(
        final EntryPoint entry, final MethodInfo method, final List<Instruction> instructions
    ) {
        if (!method.isStatic()) return -1;
        final String descriptor = entry.descriptor();
        final int end = descriptor.indexOf(')');
        final String suffix = "Ljava/lang/Class;";
        final int start = end - suffix.length();
        if (start < 1 || !descriptor.substring(start, end).equals(suffix)) return -1;
        if (start > 1 && descriptor.charAt(start - 1) == '[') return -1;
        final List<IrType> parameters = MethodDescriptor.parse(descriptor).parameterTypes();
        int slot = 0;
        for (int index = 0; index < parameters.size() - 1; index++) slot += parameters.get(index).slotWidth();
        for (final Instruction instruction : instructions) {
            if (writesLocalSlot(instruction, slot)) return -1;
        }
        return slot;
    }

    private static boolean writesLocalSlot(final Instruction instruction, final int slot) {
        final int opcode = instruction.opcode();
        if (opcode == 196) return true;
        int first = -1;
        int width = 1;
        if (opcode >= 54 && opcode <= 58 && instruction.operands().length > 0) {
            first = instruction.operands()[0] & 0xff;
            if (opcode == 55 || opcode == 57) width = 2;
        } else if (opcode >= 59 && opcode <= 78) {
            first = (opcode - 59) % 4;
            final int group = (opcode - 59) / 4;
            if (group == 1 || group == 3) width = 2;
        } else if (opcode == 132 && instruction.operands().length > 0) {
            first = instruction.operands()[0] & 0xff;
        }
        return first >= 0 && first <= slot && slot < first + width;
    }

    private static boolean isReferenceParameterLoad(final Instruction instruction, final int slot) {
        if (instruction.opcode() >= 42 && instruction.opcode() <= 45) return instruction.opcode() - 42 == slot;
        if (instruction.opcode() != 25) return false;
        final byte[] operands = instruction.operands();
        if (operands.length == 0) return false;
        return (operands[0] & 0xff) == slot;
    }

    /**
     * Returns owners of selected static fields when supported reflective primitive access is reachable.
     *
     * @param classes parsed closed-world classes
     * @param reachable reachable methods
     * @return static field owners requiring class initialization
     */
    public static Set<String> staticPrimitiveOwners(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        boolean readsInt = false;
        boolean readsBoolean = false;
        boolean readsByte = false;
        boolean readsShort = false;
        boolean readsChar = false;
        boolean readsLong = false;
        boolean readsDouble = false;
        boolean readsFloat = false;
        boolean writesInt = false;
        boolean writesBoolean = false;
        boolean writesLong = false;
        boolean writesDouble = false;
        boolean writesFloat = false;
        for (final EntryPoint entry : reachable) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) continue;
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<MethodRef> target = instruction.methodRef();
                if (target.isEmpty() || !"java/lang/reflect/Field".equals(target.orElseThrow().owner())) continue;
                if ("getInt".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)I".equals(target.orElseThrow().descriptor())) readsInt = true;
                if ("getBoolean".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)Z".equals(target.orElseThrow().descriptor())) readsBoolean = true;
                if ("getByte".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)B".equals(target.orElseThrow().descriptor())) readsByte = true;
                if ("getShort".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)S".equals(target.orElseThrow().descriptor())) readsShort = true;
                if ("getChar".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)C".equals(target.orElseThrow().descriptor())) readsChar = true;
                if ("getLong".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)J".equals(target.orElseThrow().descriptor())) readsLong = true;
                if ("getDouble".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)D".equals(target.orElseThrow().descriptor())) readsDouble = true;
                if ("getFloat".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;)F".equals(target.orElseThrow().descriptor())) readsFloat = true;
                if ("setInt".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;I)V".equals(target.orElseThrow().descriptor())) writesInt = true;
                if ("setBoolean".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;Z)V".equals(target.orElseThrow().descriptor())) writesBoolean = true;
                if ("setLong".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;J)V".equals(target.orElseThrow().descriptor())) writesLong = true;
                if ("setDouble".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;D)V".equals(target.orElseThrow().descriptor())) writesDouble = true;
                if ("setFloat".equals(target.orElseThrow().name())
                    && "(Ljava/lang/Object;F)V".equals(target.orElseThrow().descriptor())) writesFloat = true;
            }
        }
        if (!readsInt && !readsBoolean && !readsByte && !readsShort && !readsChar && !readsLong && !readsDouble && !readsFloat && !writesInt && !writesBoolean && !writesLong && !writesDouble && !writesFloat) return Set.of();
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
                        || readsBoolean && "Z".equals(candidate.descriptor())
                        || readsByte && "B".equals(candidate.descriptor())
                        || readsShort && List.of("B", "S").contains(candidate.descriptor())
                        || readsChar && "C".equals(candidate.descriptor())
                        || readsLong && List.of("B", "S", "C", "I", "J").contains(candidate.descriptor())
                        || readsDouble && List.of("B", "S", "C", "I", "J", "F", "D").contains(candidate.descriptor())
                        || readsFloat && List.of("B", "S", "C", "I", "J", "F").contains(candidate.descriptor())
                        || writesBoolean && "Z".equals(candidate.descriptor())
                            && (candidate.accessFlags() & 0x0010) == 0
                        || writesInt && List.of("I", "J", "F", "D").contains(candidate.descriptor())
                            && (candidate.accessFlags() & 0x0010) == 0
                        || writesLong && List.of("J", "F", "D").contains(candidate.descriptor())
                            && (candidate.accessFlags() & 0x0010) == 0
                        || writesDouble && "D".equals(candidate.descriptor())
                            && (candidate.accessFlags() & 0x0010) == 0
                        || writesFloat && List.of("F", "D").contains(candidate.descriptor())
                            && (candidate.accessFlags() & 0x0010) == 0)) owners.add(field.owner());
            }
        }
        return Set.copyOf(owners);
    }

}
