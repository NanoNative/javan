package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JavanNativeSubstitutions;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds concrete closed-world receiver types created by reachable code.
 */
public final class InstantiatedTypeAnalysis {
    /** How a concrete receiver becomes available at runtime. */
    public enum Origin {
        ALLOCATION,
        ENUM,
        EXTERNAL,
        SUBSTITUTION,
        SERVICE
    }

    /**
     * One concrete type and every proven construction origin.
     *
     * @param type JVM internal class name
     * @param origins ordered construction origins
     */
    public record Fact(String type, List<Origin> origins) {
        public Fact {
            origins = List.copyOf(origins);
        }
    }

    /**
     * Immutable instantiated-type facts.
     *
     * @param facts deterministic concrete-type facts
     * @param complete whether the facts came from a completed reachability analysis
     */
    public record Result(List<Fact> facts, boolean complete) {
        public Result {
            facts = List.copyOf(facts);
        }

        /** @return an explicitly unavailable result for hand-built call graphs */
        public static Result unavailable() {
            return new Result(List.of(), false);
        }

        /** @return deterministic JVM internal names */
        public List<String> types() {
            final List<String> result = new ArrayList<>(facts.size());
            for (final Fact fact : facts) {
                result.add(fact.type());
            }
            return List.copyOf(result);
        }
    }

    private InstantiatedTypeAnalysis() {
    }

    /**
     * Derives construction facts from methods proven reachable in the current fixpoint.
     *
     * @param classes parsed closed-world classes
     * @param reachableMethods reachable methods to inspect
     * @param roots externally callable roots whose reference parameters remain conservative
     * @return deterministic instantiated-type facts
     */
    public static Result analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods,
        final List<EntryPoint> roots
    ) {
        return analyze(classes, reachableMethods, roots, List.of());
    }

    /** Derives construction facts including configured service providers. */
    public static Result analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods,
        final List<EntryPoint> roots,
        final List<String> serviceProviders
    ) {
        final Map<String, List<Origin>> origins = new LinkedHashMap<>();
        for (final String provider : serviceProviders) {
            addConcrete(classes, origins, provider, Origin.SERVICE);
        }
        for (final EntryPoint root : roots) {
            final ClassFile owner = classes.get(root.className());
            final Optional<MethodInfo> rootMethod = owner == null
                ? Optional.empty()
                : owner.method(root.methodName(), root.descriptor());
            if (rootMethod.isPresent() && !rootMethod.orElseThrow().isStatic()) {
                addExternalTypes(classes, origins, root.className());
            }
            for (final String parameterType : objectParameterTypes(root.descriptor())) {
                addExternalTypes(classes, origins, parameterType);
            }
        }
        for (final EntryPoint entryPoint : reachableMethods) {
            final ClassFile classFile = classes.get(entryPoint.className());
            if (classFile == null) {
                continue;
            }
            if (classFile.isEnum()) {
                addEnumTypes(classes, origins, classFile.name());
            }
            final Optional<MethodInfo> method = classFile.method(entryPoint.methodName(), entryPoint.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.fieldRef().isPresent()) {
                    addEnumTypes(classes, origins, instruction.fieldRef().orElseThrow().owner());
                }
                if (instruction.opcode() == 187 && instruction.className().isPresent()) {
                    addConcrete(classes, origins, instruction.className().orElseThrow(), Origin.ALLOCATION);
                }
                if (instruction.methodRef().isPresent()) {
                    final MethodRef called = instruction.methodRef().orElseThrow();
                    addEnumTypes(classes, origins, called.owner());
                    if (JavanNativeSubstitutions.isSubstitutedCall(called)) {
                        returnType(called.descriptor()).ifPresent(type ->
                            addConcrete(classes, origins, type, Origin.SUBSTITUTION));
                    }
                }
            }
        }
        final List<Fact> facts = new ArrayList<>();
        for (final Map.Entry<String, List<Origin>> entry : origins.entrySet()) {
            final Fact fact = new Fact(entry.getKey(), ordered(entry.getValue()));
            int index = 0;
            while (index < facts.size() && Strings2.compareAscii(facts.get(index).type(), fact.type()) <= 0) {
                index++;
            }
            facts.add(index, fact);
        }
        return new Result(List.copyOf(facts), true);
    }

    private static void addExternalTypes(
        final Map<String, ClassFile> classes,
        final Map<String, List<Origin>> origins,
        final String declaredType
    ) {
        for (final ClassFile candidate : classes.values()) {
            if (!candidate.isInterface()
                && !candidate.isAbstract()
                && isAssignableTo(classes, candidate.name(), declaredType)) {
                add(origins, candidate.name(), Origin.EXTERNAL);
            }
        }
    }

    private static void addEnumTypes(
        final Map<String, ClassFile> classes,
        final Map<String, List<Origin>> origins,
        final String owner
    ) {
        final ClassFile enumClass = classes.get(owner);
        if (enumClass == null || !enumClass.isEnum()) {
            return;
        }
        final Optional<MethodInfo> initializer = enumClass.method("<clinit>", "()V");
        if (initializer.isEmpty() || initializer.orElseThrow().code().isEmpty()) {
            addConcrete(classes, origins, owner, Origin.ENUM);
            return;
        }
        for (final Instruction instruction : initializer.orElseThrow().code().orElseThrow().instructions()) {
            if (instruction.opcode() == 187 && instruction.className().isPresent()) {
                addConcrete(classes, origins, instruction.className().orElseThrow(), Origin.ENUM);
            }
        }
    }

    private static List<String> objectParameterTypes(final String descriptor) {
        final List<String> result = new ArrayList<>();
        int index = descriptor.indexOf('(') + 1;
        final int end = descriptor.indexOf(')');
        while (index > 0 && index < end) {
            boolean array = false;
            while (descriptor.charAt(index) == '[') {
                array = true;
                index++;
            }
            if (descriptor.charAt(index) == 'L') {
                final int close = descriptor.indexOf(';', index);
                if (close < 0 || close > end) {
                    break;
                }
                if (!array) {
                    result.add(descriptor.substring(index + 1, close));
                }
                index = close + 1;
            } else {
                index++;
            }
        }
        return List.copyOf(result);
    }

    private static boolean isAssignableTo(
        final Map<String, ClassFile> classes,
        final String candidate,
        final String expected
    ) {
        return isAssignableTo(classes, candidate, expected, new HashSet<>());
    }

    private static boolean isAssignableTo(
        final Map<String, ClassFile> classes,
        final String candidate,
        final String expected,
        final Set<String> visited
    ) {
        if (candidate.equals(expected)) {
            return true;
        }
        if (!visited.add(candidate)) {
            return false;
        }
        final ClassFile classFile = classes.get(candidate);
        if (classFile == null) {
            return false;
        }
        for (final String interfaceName : classFile.interfaces()) {
            if (isAssignableTo(classes, interfaceName, expected, visited)) {
                return true;
            }
        }
        return classFile.superName() != null
            && !classFile.superName().isEmpty()
            && isAssignableTo(classes, classFile.superName(), expected, visited);
    }

    private static void addConcrete(
        final Map<String, ClassFile> classes,
        final Map<String, List<Origin>> origins,
        final String type,
        final Origin origin
    ) {
        final ClassFile classFile = classes.get(type);
        if (classFile != null && !classFile.isInterface() && !classFile.isAbstract()) {
            add(origins, type, origin);
        }
    }

    private static void add(
        final Map<String, List<Origin>> origins,
        final String type,
        final Origin origin
    ) {
        List<Origin> values = origins.get(type);
        if (values == null) {
            values = new ArrayList<>();
            origins.put(type, values);
        }
        if (!values.contains(origin)) {
            values.add(origin);
        }
    }

    private static Optional<String> returnType(final String descriptor) {
        final int close = descriptor.lastIndexOf(')');
        if (close < 0 || close + 2 >= descriptor.length() || descriptor.charAt(close + 1) != 'L') {
            return Optional.empty();
        }
        final int end = descriptor.indexOf(';', close + 2);
        return end < 0 ? Optional.empty() : Optional.of(descriptor.substring(close + 2, end));
    }

    private static List<Origin> ordered(final List<Origin> origins) {
        final List<Origin> result = new ArrayList<>(origins.size());
        if (origins.contains(Origin.ALLOCATION)) {
            result.add(Origin.ALLOCATION);
        }
        if (origins.contains(Origin.ENUM)) {
            result.add(Origin.ENUM);
        }
        if (origins.contains(Origin.EXTERNAL)) {
            result.add(Origin.EXTERNAL);
        }
        if (origins.contains(Origin.SUBSTITUTION)) {
            result.add(Origin.SUBSTITUTION);
        }
        if (origins.contains(Origin.SERVICE)) {
            result.add(Origin.SERVICE);
        }
        return List.copyOf(result);
    }
}
