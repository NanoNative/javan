package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.NetworkApiSupport;
import javan.compat.JavanNativeSubstitutions;
import javan.verify.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        final List<EntryPoint> reachable = new ArrayList<>();
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<EntryPoint> work = new ArrayList<>(entries);
        int workIndex = 0;

        while (workIndex < work.size()) {
            final EntryPoint current = work.get(workIndex);
            workIndex++;
            if (containsEntry(reachable, current)) {
                continue;
            }
            reachable.add(current);
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
            if (isUnsupportedEnumSyntheticEntry(classes, current)) {
                diagnostics.add(unsupportedEnumValueOfDiagnostic(current, current.display()));
                continue;
            }
            final Optional<CodeAttribute> code = method.orElseThrow().code();
            if (code.isPresent()) {
                for (final Instruction instruction : code.orElseThrow().instructions()) {
                    enqueueClassInitializer(classes, instruction, work);
                    enqueueApplicationCall(classes, instruction, work, diagnostics, current);
                }
            }
        }
        return new CallGraph(entries.getFirst(), List.copyOf(reachable), List.copyOf(diagnostics));
    }

    private static boolean containsEntry(final List<EntryPoint> entries, final EntryPoint target) {
        for (final EntryPoint entry : entries) {
            if (sameEntry(entry, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameEntry(final EntryPoint left, final EntryPoint right) {
        if (!left.className().equals(right.className())) {
            return false;
        }
        if (!left.methodName().equals(right.methodName())) {
            return false;
        }
        if (!left.descriptor().equals(right.descriptor())) {
            return false;
        }
        return true;
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
        final List<EntryPoint> work,
        final List<Diagnostic> diagnostics,
        final EntryPoint current
    ) {
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return;
        }
        final MethodRef target = methodRef.orElseThrow();
        if (isEnumIntrinsic(classes, target) || isSupportedEnumSynthetic(classes, target) || isSupportedArrayClone(target)) {
            return;
        }
        if (isUnsupportedEnumSynthetic(classes, target)) {
            diagnostics.add(unsupportedEnumValueOfDiagnostic(current, target.display()));
            return;
        }
        if (isJdkCall(target) || NetworkApiSupport.isNetworkCall(target)) {
            return;
        }
        if (JavanNativeSubstitutions.isSubstitutedCall(target)) {
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
            if (!target.owner().startsWith("java/")
                && !target.owner().startsWith("jdk/")
                && !target.owner().startsWith("sun/")
                && !NetworkApiSupport.isNetworkCall(target)) {
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
        final List<EntryPoint> work
    ) {
        if (instruction.opcode() == 178 || instruction.opcode() == 179) {
            final Optional<FieldRef> fieldRef = instruction.fieldRef();
            if (fieldRef.isPresent()) {
                enqueueClassInitializer(classes, fieldRef.orElseThrow().owner(), work);
            }
            return;
        }
        if (instruction.opcode() == 184) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isPresent()) {
                enqueueClassInitializer(classes, methodRef.orElseThrow().owner(), work);
            }
            return;
        }
        if (instruction.opcode() == 187) {
            final Optional<String> className = instruction.className();
            if (className.isPresent()) {
                enqueueClassInitializer(classes, className.orElseThrow(), work);
            }
        }
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final String owner,
        final List<EntryPoint> work
    ) {
        final ClassFile classFile = classes.get(owner);
        if (classFile == null || classFile.isEnum()) {
            return;
        }
        final Optional<MethodInfo> method = classFile.method("<clinit>", "()V");
        if (method.isPresent()) {
            final MethodInfo classInitializer = method.orElseThrow();
            work.add(new EntryPoint(owner, classInitializer.name(), classInitializer.descriptor()));
        }
    }

    private static boolean isConcreteExactCallTarget(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile target = classes.get(owner);
        if (target == null || target.isInterface()) {
            return false;
        }
        if (target.isFinal()) {
            return true;
        }
        for (final ClassFile candidate : classes.values()) {
            if (owner.equals(candidate.superName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEnumIntrinsic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"()Ljava/lang/String;".equals(target.descriptor())) {
            return false;
        }
        if ("name".equals(target.name())) {
            return true;
        }
        return "toString".equals(target.name());
    }

    private static boolean isUnsupportedEnumSynthetic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"valueOf".equals(target.name())) {
            return false;
        }
        return ("(Ljava/lang/String;)L" + target.owner() + ";").equals(target.descriptor());
    }

    private static boolean isUnsupportedEnumSyntheticEntry(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile owner = classes.get(entry.className());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"valueOf".equals(entry.methodName())) {
            return false;
        }
        return ("(Ljava/lang/String;)L" + entry.className() + ";").equals(entry.descriptor());
    }

    private static Diagnostic unsupportedEnumValueOfDiagnostic(final EntryPoint current, final String subject) {
        return Diagnostic.error(
            "JAVAN015",
            "unsupported reachable enum synthetic method",
            current.className(),
            current.methodName() + current.descriptor(),
            subject,
            "Enum.valueOf(String) requires deterministic enum lookup lowering, which is not implemented yet.",
            "Use direct enum constants, values(), ordinal(), name(), toString(), or enum switch until valueOf lowering is implemented."
        );
    }

    private static boolean isSupportedEnumSynthetic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if ("ordinal".equals(target.name()) && "()I".equals(target.descriptor())) {
            return true;
        }
        if (!"values".equals(target.name())) {
            return false;
        }
        if (!target.descriptor().equals("()[L" + target.owner() + ";")) {
            return false;
        }
        return true;
    }

    private static boolean isJdkCall(final MethodRef target) {
        if (target.owner().startsWith("java/")) {
            return true;
        }
        if (target.owner().startsWith("jdk/")) {
            return true;
        }
        if (target.owner().startsWith("sun/")) {
            return true;
        }
        return false;
    }

    private static boolean isSupportedArrayClone(final MethodRef target) {
        if (!target.owner().startsWith("[")) {
            return false;
        }
        if (!"clone".equals(target.name())) {
            return false;
        }
        if (!"()Ljava/lang/Object;".equals(target.descriptor())) {
            return false;
        }
        if ("[Z".equals(target.owner())) {
            return false;
        }
        return true;
    }

    private static List<EntryPoint> interfaceTargets(final Map<String, ClassFile> classes, final MethodRef target) {
        final List<EntryPoint> targets = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()) {
                continue;
            }
            if (!candidate.interfaces().contains(target.owner())) {
                continue;
            }
            if (candidate.method(target.name(), target.descriptor()).isPresent()) {
                targets.add(new EntryPoint(candidate.name(), target.name(), target.descriptor()));
            }
        }
        return List.copyOf(targets);
    }

    private static List<EntryPoint> virtualTargets(final Map<String, ClassFile> classes, final MethodRef target) {
        final List<EntryPoint> targets = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()) {
                continue;
            }
            if (!isSubtypeOf(classes, candidate.name(), target.owner())) {
                continue;
            }
            final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, candidate.name(), target);
            if (resolved.isPresent()) {
                final EntryPoint entryPoint = resolved.orElseThrow();
                if (!containsEntry(targets, entryPoint)) {
                    targets.add(entryPoint);
                }
            }
        }
        return List.copyOf(targets);
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
        if (current.equals(expectedSuper)) {
            return true;
        }
        return false;
    }

}
