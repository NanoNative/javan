package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds the deterministic runtime trigger and dependency graph for JVM class initialization. */
public final class ClassInitializationGraph {
    /** Active-use bytecode kinds that can trigger initialization. */
    public enum TriggerKind {
        GET_STATIC,
        PUT_STATIC,
        INVOKE_STATIC,
        NEW
    }

    /** One reachable active use and the class or interface it initializes. */
    public record Trigger(EntryPoint method, int offset, TriggerKind kind, String target) {
    }

    /**
     * Canonical class-initialization model.
     *
     * @param dependencies initialization owner to ordered prerequisite owners
     * @param triggers reachable active-use triggers in deterministic method/offset order
     */
    public record Result(Map<String, List<String>> dependencies, List<Trigger> triggers) {
        public Result {
            final Map<String, List<String>> copied = new LinkedHashMap<>();
            for (final Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
                copied.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            dependencies = Collections.unmodifiableMap(copied);
            triggers = List.copyOf(triggers);
        }

        /** Returns whether this owner has initializer work or ordered dependencies. */
        public boolean initializes(final String owner) {
            return dependencies.containsKey(owner);
        }
    }

    private ClassInitializationGraph() {
    }

    /**
     * Builds the runtime initialization model for reachable bytecode.
     *
     * @param classes parsed closed-world classes
     * @param reachableMethods reachable methods to inspect for active uses
     * @return deterministic initialization dependencies and trigger sites
     */
    public static Result analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachableMethods) {
        final List<EntryPoint> methods = sortedMethods(reachableMethods);
        final List<Trigger> triggers = new ArrayList<>();
        final Set<String> roots = new HashSet<>();
        for (final EntryPoint entry : methods) {
            if ("<clinit>".equals(entry.methodName()) && supportsRuntimeInitialization(classes, entry.className())) {
                roots.add(entry.className());
            }
            final ClassFile classFile = classes.get(entry.className());
            if (classFile == null) {
                continue;
            }
            final Optional<MethodInfo> resolved = classFile.method(entry.methodName(), entry.descriptor());
            if (resolved.isEmpty() || resolved.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : resolved.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<String> target = triggerTarget(classes, instruction);
                if (target.isEmpty() || !supportsRuntimeInitialization(classes, target.orElseThrow())) {
                    continue;
                }
                final String owner = target.orElseThrow();
                triggers.add(new Trigger(entry, instruction.offset(), triggerKind(instruction.opcode()), owner));
                roots.add(owner);
            }
        }
        final Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (final String root : sortedStrings(roots)) {
            addNode(classes, root, dependencies, new ArrayList<>());
        }
        return new Result(dependencies, triggers);
    }

    /**
     * Returns ordered initializer owners required by one active use, including the target when it has work.
     *
     * @param classes parsed closed-world classes
     * @param owner active-use owner
     * @return dependency-first initializer owners
     */
    public static List<String> initializerOwners(final Map<String, ClassFile> classes, final String owner) {
        if (!supportsRuntimeInitialization(classes, owner)) {
            return List.of();
        }
        final Map<String, List<String>> graph = new LinkedHashMap<>();
        addNode(classes, owner, graph, new ArrayList<>());
        return List.copyOf(graph.keySet());
    }

    /**
     * Returns ordered direct runtime initialization dependencies for an owner.
     *
     * @param classes parsed closed-world classes
     * @param owner class or interface owner
     * @return direct superclass and applicable default-interface dependencies
     */
    public static List<String> dependencies(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile classFile = classes.get(owner);
        if (classFile == null || classFile.isEnum() || classFile.isInterface()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        if (supportsRuntimeInitialization(classes, classFile.superName())) {
            result.add(classFile.superName());
        }
        for (final String interfaceName : classFile.interfaces()) {
            addDefaultInterfaces(classes, interfaceName, result, new ArrayList<>());
        }
        return List.copyOf(result);
    }

    /**
     * Resolves the class or interface that declares a referenced static field.
     *
     * @param classes parsed closed-world classes
     * @param field symbolic field reference
     * @return declaring owner when present in the closed world
     */
    public static Optional<String> staticFieldOwner(final Map<String, ClassFile> classes, final FieldRef field) {
        return staticFieldOwner(classes, field.owner(), field, new ArrayList<>());
    }

    /**
     * Resolves the class or interface that declares a referenced static method.
     *
     * @param classes parsed closed-world classes
     * @param method symbolic method reference
     * @return declaring owner when present in the closed world
     */
    public static Optional<String> staticMethodOwner(final Map<String, ClassFile> classes, final MethodRef method) {
        String owner = method.owner();
        while (owner != null && !owner.isEmpty()) {
            final ClassFile classFile = classes.get(owner);
            if (classFile == null) {
                return Optional.empty();
            }
            final Optional<MethodInfo> declared = classFile.method(method.name(), method.descriptor());
            if (declared.isPresent() && declared.orElseThrow().isStatic()) {
                return Optional.of(owner);
            }
            if (classFile.isInterface()) {
                return Optional.empty();
            }
            owner = classFile.superName();
        }
        return Optional.empty();
    }

    private static void addNode(
        final Map<String, ClassFile> classes,
        final String owner,
        final Map<String, List<String>> graph,
        final List<String> path
    ) {
        if (!supportsRuntimeInitialization(classes, owner) || graph.containsKey(owner) || path.contains(owner)) {
            return;
        }
        path.add(owner);
        final List<String> direct = dependencies(classes, owner);
        for (final String dependency : direct) {
            addNode(classes, dependency, graph, path);
        }
        path.remove(path.size() - 1);
        final ClassFile classFile = classes.get(owner);
        final List<String> required = new ArrayList<>();
        for (final String dependency : direct) {
            if (graph.containsKey(dependency)) {
                required.add(dependency);
            }
        }
        if (classFile.method("<clinit>", "()V").isPresent() || !required.isEmpty()) {
            graph.put(owner, List.copyOf(required));
        }
    }

    private static void addDefaultInterfaces(
        final Map<String, ClassFile> classes,
        final String owner,
        final List<String> result,
        final List<String> path
    ) {
        final ClassFile interfaceFile = classes.get(owner);
        if (interfaceFile == null || !interfaceFile.isInterface() || path.contains(owner)) {
            return;
        }
        path.add(owner);
        for (final String parent : interfaceFile.interfaces()) {
            addDefaultInterfaces(classes, parent, result, path);
        }
        path.remove(path.size() - 1);
        if (declaresDefaultMethod(interfaceFile) && !result.contains(owner)) {
            result.add(owner);
        }
    }

    private static boolean declaresDefaultMethod(final ClassFile classFile) {
        for (final MethodInfo method : classFile.methods()) {
            if (!method.isStatic()
                && !method.isPrivate()
                && method.code().isPresent()
                && !"<init>".equals(method.name())
                && !"<clinit>".equals(method.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsRuntimeInitialization(final Map<String, ClassFile> classes, final String owner) {
        if (owner == null || owner.isEmpty()) {
            return false;
        }
        final ClassFile classFile = classes.get(owner);
        return classFile != null;
    }

    private static Optional<String> triggerTarget(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        if (instruction.opcode() == 178 || instruction.opcode() == 179) {
            final FieldRef field = instruction.fieldRef().orElseThrow();
            final Optional<String> resolved = staticFieldOwner(classes, field);
            return resolved.isPresent() ? resolved : Optional.of(field.owner());
        }
        if (instruction.opcode() == 184) {
            final MethodRef method = instruction.methodRef().orElseThrow();
            final Optional<String> resolved = staticMethodOwner(classes, method);
            return resolved.isPresent() ? resolved : Optional.of(method.owner());
        }
        if (instruction.opcode() == 187) {
            return instruction.className();
        }
        return Optional.empty();
    }

    private static Optional<String> staticFieldOwner(
        final Map<String, ClassFile> classes,
        final String owner,
        final FieldRef target,
        final List<String> visited
    ) {
        if (owner == null || owner.isEmpty()) {
            return Optional.empty();
        }
        final ClassFile classFile = classes.get(owner);
        if (classFile == null || visited.contains(owner)) {
            return Optional.empty();
        }
        visited.add(owner);
        for (final javan.classfile.FieldInfo field : classFile.fields()) {
            if (field.isStatic()
                && field.name().equals(target.name())
                && field.descriptor().equals(target.descriptor())) {
                return Optional.of(owner);
            }
        }
        for (final String interfaceName : classFile.interfaces()) {
            final Optional<String> resolved = staticFieldOwner(classes, interfaceName, target, visited);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return staticFieldOwner(classes, classFile.superName(), target, visited);
    }

    private static TriggerKind triggerKind(final int opcode) {
        if (opcode == 178) return TriggerKind.GET_STATIC;
        if (opcode == 179) return TriggerKind.PUT_STATIC;
        if (opcode == 184) return TriggerKind.INVOKE_STATIC;
        return TriggerKind.NEW;
    }

    private static List<EntryPoint> sortedMethods(final List<EntryPoint> methods) {
        final List<EntryPoint> result = new ArrayList<>();
        final List<EntryPoint> scratch = new ArrayList<>();
        for (final EntryPoint method : methods) {
            result.add(method);
            scratch.add(method);
        }
        sortMethods(result, scratch, 0, result.size());
        return result;
    }

    private static void sortMethods(
        final List<EntryPoint> methods,
        final List<EntryPoint> scratch,
        final int from,
        final int to
    ) {
        if (to - from < 2) {
            return;
        }
        final int middle = from + (to - from) / 2;
        sortMethods(methods, scratch, from, middle);
        sortMethods(methods, scratch, middle, to);
        int left = from;
        int right = middle;
        int target = from;
        while (left < middle && right < to) {
            if (compare(methods.get(left), methods.get(right)) <= 0) {
                scratch.set(target++, methods.get(left++));
            } else {
                scratch.set(target++, methods.get(right++));
            }
        }
        while (left < middle) {
            scratch.set(target++, methods.get(left++));
        }
        while (right < to) {
            scratch.set(target++, methods.get(right++));
        }
        for (int index = from; index < to; index++) {
            methods.set(index, scratch.get(index));
        }
    }

    private static int compare(final EntryPoint left, final EntryPoint right) {
        int value = Strings2.compareAscii(left.className(), right.className());
        if (value != 0) return value;
        value = Strings2.compareAscii(left.methodName(), right.methodName());
        if (value != 0) return value;
        return Strings2.compareAscii(left.descriptor(), right.descriptor());
    }

    private static List<String> sortedStrings(final Set<String> values) {
        final List<String> unordered = new ArrayList<>();
        for (final String value : values) {
            unordered.add(value);
        }
        final List<String> result = new ArrayList<>();
        for (final Integer index : Strings2.sortedIndexes(unordered)) {
            result.add(unordered.get(index.intValue()));
        }
        return result;
    }
}
