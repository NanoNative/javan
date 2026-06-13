package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.verify.Diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Builds a small closed-world call graph for reachable application methods.
 */
public final class ReachabilityAnalyzer {
    /**
     * Analyzes reachability from a main class.
     *
     * @param classes parsed application classes
     * @param mainClass JVM internal main class
     * @return call graph
     */
    public CallGraph analyze(final Map<String, ClassFile> classes, final String mainClass) {
        final EntryPoint entry = new EntryPoint(mainClass, "main", "([Ljava/lang/String;)V");
        return analyze(classes, List.of(entry));
    }

    /**
     * Analyzes reachability from explicit entry points.
     *
     * @param classes parsed application classes
     * @param entries entry points
     * @return call graph
     */
    public CallGraph analyze(final Map<String, ClassFile> classes, final List<EntryPoint> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Reachability requires at least one entry point");
        }
        final Set<EntryPoint> reachable = new LinkedHashSet<>();
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final Queue<EntryPoint> work = new ArrayDeque<>();
        work.addAll(entries);

        while (!work.isEmpty()) {
            final EntryPoint current = work.remove();
            if (!reachable.add(current)) {
                continue;
            }
            final Optional<MethodInfo> method = method(classes, current);
            if (method.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    "JAVAN011",
                    "reachable method cannot be resolved",
                    current.className(),
                    current.methodName() + current.descriptor(),
                    current.display(),
                    "Closed-world analysis requires every reachable application method to be known.",
                    "Compile all application classes before running javan."
                ));
                continue;
            }
            method.orElseThrow().code().ifPresent(code -> code.instructions().forEach(instruction -> {
                enqueueClassInitializer(classes, instruction, work);
                enqueueApplicationCall(classes, instruction, work, diagnostics, current);
            }));
        }
        return new CallGraph(entries.getFirst(), Set.copyOf(reachable), List.copyOf(diagnostics));
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entryPoint) {
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return Optional.empty();
        }
        return classFile.method(entryPoint.methodName(), entryPoint.descriptor());
    }

    private static void enqueueApplicationCall(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final Queue<EntryPoint> work,
        final List<Diagnostic> diagnostics,
        final EntryPoint current
    ) {
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return;
        }
        final MethodRef target = methodRef.orElseThrow();
        if (isEnumIntrinsic(classes, target)) {
            return;
        }
        if (instruction.opcode() == 185) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target);
            if (!targetMethods.isEmpty()) {
                work.addAll(targetMethods);
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                current.methodName() + current.descriptor(),
                target.display(),
                "Interface dispatch requires at least one concrete implementation in the closed world.",
                "Add an implementation class or remove the unreachable interface call."
            ));
            return;
        }
        if (!classes.containsKey(target.owner())) {
            if (!target.owner().startsWith("java/") && !target.owner().startsWith("jdk/") && !target.owner().startsWith("sun/")) {
                diagnostics.add(Diagnostic.error(
                    "JAVAN011",
                    "reachable call target cannot be resolved",
                    current.className(),
                    current.methodName() + current.descriptor(),
                    target.display(),
                    "Closed-world analysis requires every reachable non-JDK call target to be known.",
                    "Add the class to the project classes or dependency classpath."
                ));
            }
            return;
        }
        if (instruction.opcode() == 184) {
            work.add(new EntryPoint(target.owner(), target.name(), target.descriptor()));
            return;
        }
        if (instruction.opcode() == 183 && "<init>".equals(target.name())) {
            work.add(new EntryPoint(target.owner(), target.name(), target.descriptor()));
            return;
        }
        if (instruction.opcode() == 183) {
            work.add(new EntryPoint(target.owner(), target.name(), target.descriptor()));
            return;
        }
        if (instruction.opcode() == 182 && isConcreteExactCallTarget(classes, target.owner())) {
            work.add(new EntryPoint(target.owner(), target.name(), target.descriptor()));
            return;
        }
        if (instruction.opcode() == 182) {
            final List<EntryPoint> targets = virtualTargets(classes, target);
            if (!targets.isEmpty()) {
                work.addAll(targets);
                return;
            }
        }
        diagnostics.add(Diagnostic.error(
            "JAVAN012",
            "unsupported reachable application method call",
            current.className(),
            current.methodName() + current.descriptor(),
            target.display(),
            "The current native profile could not resolve a closed-world dispatch target.",
            "Make sure at least one concrete application class implements the invoked method."
        ));
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final Queue<EntryPoint> work
    ) {
        if (instruction.opcode() == 178 || instruction.opcode() == 179) {
            instruction.fieldRef()
                .map(FieldRef::owner)
                .ifPresent(owner -> enqueueClassInitializer(classes, owner, work));
            return;
        }
        if (instruction.opcode() == 184) {
            instruction.methodRef()
                .map(MethodRef::owner)
                .ifPresent(owner -> enqueueClassInitializer(classes, owner, work));
            return;
        }
        if (instruction.opcode() == 187) {
            instruction.className().ifPresent(owner -> enqueueClassInitializer(classes, owner, work));
        }
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final String owner,
        final Queue<EntryPoint> work
    ) {
        final ClassFile classFile = classes.get(owner);
        if (classFile == null || classFile.isEnum()) {
            return;
        }
        classFile.method("<clinit>", "()V")
            .ifPresent(method -> work.add(new EntryPoint(owner, method.name(), method.descriptor())));
    }

    private static boolean isConcreteExactCallTarget(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile target = classes.get(owner);
        if (target == null || target.isInterface()) {
            return false;
        }
        return target.isFinal() || classes.values().stream().noneMatch(candidate -> owner.equals(candidate.superName()));
    }

    private static boolean isEnumIntrinsic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        return owner != null
            && owner.isEnum()
            && ("name".equals(target.name()) || "toString".equals(target.name()))
            && "()Ljava/lang/String;".equals(target.descriptor());
    }

    private static List<EntryPoint> interfaceTargets(final Map<String, ClassFile> classes, final MethodRef target) {
        return classes.values().stream()
            .filter(candidate -> !candidate.isInterface())
            .filter(candidate -> candidate.interfaces().contains(target.owner()))
            .filter(candidate -> candidate.method(target.name(), target.descriptor()).isPresent())
            .map(candidate -> new EntryPoint(candidate.name(), target.name(), target.descriptor()))
            .toList();
    }

    private static List<EntryPoint> virtualTargets(final Map<String, ClassFile> classes, final MethodRef target) {
        return classes.values().stream()
            .filter(candidate -> !candidate.isInterface())
            .filter(candidate -> isSubtypeOf(classes, candidate.name(), target.owner()))
            .map(candidate -> resolvedVirtualTarget(classes, candidate.name(), target))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    }

    private static Optional<EntryPoint> resolvedVirtualTarget(final Map<String, ClassFile> classes, final String receiver, final MethodRef target) {
        String current = receiver;
        while (classes.containsKey(current)) {
            final ClassFile classFile = classes.get(current);
            if (classFile.method(target.name(), target.descriptor()).isPresent()) {
                return Optional.of(new EntryPoint(current, target.name(), target.descriptor()));
            }
            current = classFile.superName();
        }
        return Optional.empty();
    }

    private static boolean isSubtypeOf(final Map<String, ClassFile> classes, final String candidate, final String expectedSuper) {
        String current = candidate;
        while (classes.containsKey(current)) {
            if (current.equals(expectedSuper)) {
                return true;
            }
            current = classes.get(current).superName();
        }
        return current.equals(expectedSuper);
    }
}
