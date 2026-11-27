package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Computes the closed-world classes whose runtime metadata can be observed. */
public final class ClassMetadataReachability {
    private ClassMetadataReachability() {
    }

    /**
     * Computes the classes retained by reachable code and explicit reflection declarations.
     *
     * @param classes parsed closed-world classes
     * @param callGraph current reachable methods and instantiated types
     * @param initialization class initialization dependencies
     * @param declaration parsed project reflection declaration
     * @return retained JVM class names, including required hierarchies
     */
    public static Set<String> collect(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final ClassInitializationGraph.Result initialization,
        final ReflectionDeclaration.Result declaration
    ) {
        if (!callGraph.instantiatedTypes().complete()) {
            return Set.copyOf(classes.keySet());
        }
        final Set<String> retained = new HashSet<>();
        for (final String target : declaration.classes()) {
            addClass(classes, retained, target);
        }
        for (final EntryPoint entry : callGraph.reachableMethods()) {
            if (isClassForName(entry.className(), entry.methodName(), entry.descriptor())) {
                return Set.copyOf(classes.keySet());
            }
            addClass(classes, retained, entry.className());
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty() : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final CodeAttribute code = method.orElseThrow().code().orElseThrow();
            for (final CodeException handler : code.exceptionTable()) {
                if (handler.catchType().isPresent()) {
                    addClass(classes, retained, handler.catchType().orElseThrow());
                }
            }
            final List<Instruction> instructions = code.instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Instruction instruction = instructions.get(index);
                if (instruction.className().isPresent()) {
                    addClass(classes, retained, instruction.className().orElseThrow());
                }
                if (instruction.methodRef().isPresent()) {
                    final MethodRef reference = instruction.methodRef().orElseThrow();
                    if (isClassForName(reference)) {
                        final Optional<String> target = ReachabilityAnalyzer.literalClassForNameTarget(code, instruction);
                        if (target.isPresent() && classes.containsKey(target.orElseThrow())) {
                            addClass(classes, retained, target.orElseThrow());
                        } else if (!declaration.present()) {
                            return Set.copyOf(classes.keySet());
                        } else if ("(Ljava/lang/String;)Ljava/lang/Class;".equals(reference.descriptor())) {
                            for (final String literal : ReflectionStringFlow.literals(method.orElseThrow(), index - 1)) {
                                addClass(classes, retained, literal.replace('.', '/'));
                            }
                        } else if (index >= 3) {
                            final Instruction loader = instructions.get(index - 1);
                            final Instruction initialize = instructions.get(index - 2);
                            final int loaderOpcode = loader.opcode();
                            final boolean directLoader = loaderOpcode == 1 || loaderOpcode == 25
                                || loaderOpcode >= 42 && loaderOpcode <= 45
                                || loaderOpcode == 184 && loader.methodRef().isPresent()
                                    && "java/lang/ClassLoader".equals(loader.methodRef().orElseThrow().owner())
                                    && "getSystemClassLoader".equals(loader.methodRef().orElseThrow().name())
                                    && "()Ljava/lang/ClassLoader;".equals(loader.methodRef().orElseThrow().descriptor());
                            final int flagOpcode = initialize.opcode();
                            final boolean directFlag = initialize.intValue().isPresent() || flagOpcode == 21
                                || flagOpcode >= 26 && flagOpcode <= 29;
                            if (directLoader && directFlag) {
                                for (final String literal : ReflectionStringFlow.literals(method.orElseThrow(), index - 3)) {
                                    addClass(classes, retained, literal.replace('.', '/'));
                                }
                            }
                        }
                    }
                    addClass(classes, retained, reference.owner());
                }
                if (instruction.fieldRef().isPresent()) {
                    addClass(classes, retained, instruction.fieldRef().orElseThrow().owner());
                }
            }
        }
        for (final String type : callGraph.instantiatedTypes().types()) {
            addClass(classes, retained, type);
        }
        for (final Map.Entry<String, List<String>> entry : initialization.dependencies().entrySet()) {
            addClass(classes, retained, entry.getKey());
            for (final String dependency : entry.getValue()) {
                addClass(classes, retained, dependency);
            }
        }
        return Set.copyOf(retained);
    }

    /**
     * Identifies a supported class-name lookup call.
     *
     * @param reference resolved method reference
     * @return whether it is a supported Class.forName overload
     */
    public static boolean isClassForName(final MethodRef reference) {
        return isClassForName(reference.owner(), reference.name(), reference.descriptor());
    }

    private static boolean isClassForName(final String owner, final String name, final String descriptor) {
        return "java/lang/Class".equals(owner) && "forName".equals(name)
            && ("(Ljava/lang/String;)Ljava/lang/Class;".equals(descriptor)
                || "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;".equals(descriptor));
    }

    private static void addClass(final Map<String, ClassFile> classes, final Set<String> retained, final String reference) {
        if (reference == null || reference.isEmpty()) {
            return;
        }
        String name = reference;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        final ClassFile classFile = classes.get(name);
        if (classFile == null || !retained.add(name)) {
            return;
        }
        addClass(classes, retained, classFile.superName());
        for (final String interfaceName : classFile.interfaces()) {
            addClass(classes, retained, interfaceName);
        }
    }
}
