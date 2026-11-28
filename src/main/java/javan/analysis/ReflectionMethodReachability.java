package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared conservative method targets for statically known and declared reflection lookups. */
public final class ReflectionMethodReachability {
    private ReflectionMethodReachability() {
    }

    /**
     * Collects literal values supplied to supported reflective method lookups.
     *
     * @param classes parsed closed-world classes
     * @param reachable reachable method identities
     * @return method names and proven parameter descriptors traced to lookup arguments
     */
    public static Set<String> literalLookups(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final Set<String> lookups = new HashSet<>();
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
            int[] controlFlowEntries = null;
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
                if (instructions.get(cursor).opcode() == 192
                    && "[Ljava/lang/Class;".equals(instructions.get(cursor).className().orElse(""))
                    && cursor > 0 && instructions.get(cursor - 1).opcode() == 1) cursor--;
                final int opcode = instructions.get(cursor).opcode();
                String parameterPrefix = "";
                if (opcode == 1 || opcode == 25 || opcode >= 42 && opcode <= 45) {
                    if (opcode == 1 && cursor > 0) {
                        if (controlFlowEntries == null) controlFlowEntries = controlFlowEntryCounts(code, method.orElseThrow());
                        if (inlineLookupIsIsolated(controlFlowEntries, cursor - 1, index)) parameterPrefix = "()";
                    }
                    cursor--;
                } else {
                    final List<String> reversed = new ArrayList<>();
                    final List<Integer> indexes = new ArrayList<>();
                    boolean exact = true;
                    while (cursor >= 3 && instructions.get(cursor).opcode() == 83
                        && instructions.get(cursor - 3).opcode() == 89
                        && instructions.get(cursor - 2).intValue().isPresent()) {
                        final Optional<String> parameter = ReflectionDeclaration.classLiteralDescriptor(instructions.get(cursor - 1));
                        exact &= parameter.isPresent();
                        reversed.add(parameter.orElse(""));
                        indexes.add(instructions.get(cursor - 2).intValue().orElseThrow());
                        cursor -= 4;
                    }
                    if (cursor < 1 || instructions.get(cursor).opcode() != 189
                        || !"java/lang/Class".equals(instructions.get(cursor).className().orElse(""))
                        || instructions.get(cursor - 1).intValue().orElse(-1) != reversed.size()) {
                        continue;
                    }
                    for (int position = 0; position < indexes.size(); position++) {
                        exact &= indexes.get(position).intValue() == indexes.size() - position - 1;
                    }
                    if (exact && cursor >= 2) {
                        if (controlFlowEntries == null) controlFlowEntries = controlFlowEntryCounts(code, method.orElseThrow());
                        exact = inlineLookupIsIsolated(controlFlowEntries, cursor - 2, index);
                    } else {
                        exact = false;
                    }
                    if (exact) {
                        final StringBuilder prefix = new StringBuilder("(");
                        for (int parameter = reversed.size() - 1; parameter >= 0; parameter--) prefix.append(reversed.get(parameter));
                        parameterPrefix = prefix.append(')').toString();
                    }
                    cursor -= 2;
                }
                if (cursor >= 0) {
                    for (final String name : ReflectionLiteralFlow.literals(method.orElseThrow(), cursor)) {
                        lookups.add(lookupKey(name, parameterPrefix));
                    }
                }
            }
        }
        return Set.copyOf(lookups);
    }

    // Length-prefix the name so arbitrary method names cannot collide with a signature.
    private static String lookupKey(final String name, final String parameterPrefix) {
        return name.length() + ":" + name + parameterPrefix;
    }

    private static int[] controlFlowEntryCounts(final CodeAttribute code, final MethodInfo method) {
        final BytecodeControlFlow.Result flow = BytecodeControlFlow.analyze(method);
        if (!flow.structurallyValid()) return new int[0];
        final List<Instruction> instructions = code.instructions();
        final boolean[] alternateEntry = new boolean[instructions.size()];
        for (int source = 0; source < instructions.size(); source++) {
            for (final Integer successor : flow.graph().successors(source)) {
                final int target = successor.intValue();
                if (target != source + 1) alternateEntry[target] = true;
            }
        }
        final Set<Integer> handlerOffsets = new HashSet<>();
        for (final javan.classfile.CodeException handler : code.exceptionTable()) handlerOffsets.add(Integer.valueOf(handler.handlerPc()));
        final int[] counts = new int[instructions.size() + 1];
        for (int index = 0; index < instructions.size(); index++) {
            final boolean entry = alternateEntry[index] || handlerOffsets.contains(Integer.valueOf(instructions.get(index).offset()));
            counts[index + 1] = counts[index] + (entry ? 1 : 0);
        }
        return counts;
    }

    private static boolean inlineLookupIsIsolated(final int[] controlFlowEntries, final int start, final int call) {
        return start >= 0 && start < call && call + 1 < controlFlowEntries.length
            && controlFlowEntries[call + 1] == controlFlowEntries[start + 1];
    }

    /**
     * Checks an explicitly declared or conservatively discovered method identity.
     *
     * @param classes scanned closed-world classes
     * @param declaration parsed project declaration
     * @param literalLookups literal method lookups from reachable code
     * @param owner declaring JVM class name
     * @param name method name
     * @param descriptor JVM method descriptor
     * @return whether the method is in the retained set
     */
    public static boolean includes(
        final Map<String, ClassFile> classes,
        final ReflectionDeclaration.Result declaration,
        final Set<String> literalLookups,
        final String owner,
        final String name,
        final String descriptor
    ) {
        final int candidateParameterEnd = descriptor.indexOf(')');
        if (literalLookups.contains(lookupKey(name, ""))
            || candidateParameterEnd >= 0 && literalLookups.contains(lookupKey(name, descriptor.substring(0, candidateParameterEnd + 1)))) {
            return true;
        }
        if (declaration.methods().contains(new EntryPoint(owner, name, descriptor))) return true;
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
