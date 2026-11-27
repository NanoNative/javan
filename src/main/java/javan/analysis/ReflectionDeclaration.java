package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads the optional application-owned reflection declaration without executing it. */
public final class ReflectionDeclaration {
    private static final String METHOD = "javanReflectionTargets";
    private static final String DESCRIPTOR = "()[Ljava/lang/reflect/AnnotatedElement;";

    private ReflectionDeclaration() {
    }

    /**
     * Parsed project declaration.
     *
     * @param present whether a valid declaration exists, including an empty declaration
     * @param classes declared classes and required parameter classes
     * @param methods declared method identities, including abstract methods
     * @param diagnostics deterministic validation errors
     */
    public record Result(boolean present, List<String> classes, List<EntryPoint> methods, List<Diagnostic> diagnostics) {
        public Result {
            classes = List.copyOf(classes);
            methods = List.copyOf(methods);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /**
     * Inspects application bytecode for the optional project-wide declaration.
     *
     * @param classes scanned application and dependency classes
     * @return class targets and validation diagnostics
     */
    public static Result analyze(final Map<String, ClassFile> classes) {
        final List<ClassFile> owners = new ArrayList<>();
        for (final ClassFile owner : classes.values()) {
            if (!owner.application()) {
                continue;
            }
            boolean hasDeclaration = false;
            for (final MethodInfo method : owner.methods()) {
                hasDeclaration |= METHOD.equals(method.name());
            }
            if (hasDeclaration) {
                int index = 0;
                while (index < owners.size() && Strings2.compareAscii(owners.get(index).name(), owner.name()) < 0) {
                    index++;
                }
                owners.add(index, owner);
            }
        }
        ClassFile declarationOwner = null;
        MethodInfo declaration = null;
        for (final ClassFile owner : owners) {
            for (final MethodInfo method : owner.methods()) {
                if (!METHOD.equals(method.name())) {
                    continue;
                }
                if (declaration != null) {
                    return invalid(owner.name(), "Only one declaration is permitted per project.");
                }
                declarationOwner = owner;
                declaration = method;
            }
        }
        if (declaration == null) {
            return new Result(false, List.of(), List.of(), List.of());
        }
        final String ownerName = declarationOwner.name();
        if (!declarationOwner.isPublic() || !declaration.isPublic() || !declaration.isStatic()
            || !DESCRIPTOR.equals(declaration.descriptor()) || declaration.code().isEmpty()) {
            return invalid(ownerName, "The declaration must be a public static AnnotatedElement[] method on a public class.");
        }
        final CodeAttribute code = declaration.code().orElseThrow();
        if (!code.exceptionTable().isEmpty()) {
            return invalid(ownerName, "Exception handlers are not supported in the declaration.");
        }
        final List<Instruction> instructions = code.instructions();
        if (instructions.size() < 3 || instructions.get(0).intValue().isEmpty()
            || instructions.get(1).opcode() != 189
            || !instructions.get(1).className().orElse("").equals("java/lang/reflect/AnnotatedElement")) {
            return invalid(ownerName, "Return an array literal containing compiled class literals.");
        }
        final int count = instructions.get(0).intValue().orElseThrow();
        if (count < 0 || instructions.size() < 3L + 4L * count) {
            return invalid(ownerName, "Return an array literal containing class or method literals.");
        }
        final List<String> targets = new ArrayList<>();
        final List<EntryPoint> methods = new ArrayList<>();
        int cursor = 2;
        for (int index = 0; index < count; index++) {
            if (cursor + 3 >= instructions.size() || instructions.get(cursor).opcode() != 89
                || instructions.get(cursor + 1).intValue().orElse(-1) != index) {
                return invalid(ownerName, "Each array element must be a class or declared method literal.");
            }
            final Instruction value = instructions.get(cursor + 2);
            if ((value.opcode() != 18 && value.opcode() != 19)
                || value.className().isEmpty()
                || !classes.containsKey(value.className().orElseThrow())) {
                return invalid(ownerName, "Each target must name a class from the compiled world.");
            }
            final String target = value.className().orElseThrow();
            targets.add(target);
            cursor += 3;
            if (instructions.get(cursor).opcode() != 83) {
                if (cursor + 3 >= instructions.size() || instructions.get(cursor).stringValue().isEmpty()
                    || instructions.get(cursor + 1).intValue().isEmpty()
                    || instructions.get(cursor + 2).opcode() != 189
                    || !instructions.get(cursor + 2).className().orElse("").equals("java/lang/Class")) {
                    return invalid(ownerName, "Use getDeclaredMethod with a literal name and parameter class literals.");
                }
                final String name = instructions.get(cursor).stringValue().orElseThrow();
                final int parameters = instructions.get(cursor + 1).intValue().orElseThrow();
                cursor += 3;
                if (parameters < 0 || instructions.size() - cursor < 4L * parameters + 2) {
                    return invalid(ownerName, "Use a literal parameter class array for " + name + ".");
                }
                final StringBuilder descriptor = new StringBuilder("(");
                for (int parameter = 0; parameter < parameters; parameter++) {
                    if (instructions.get(cursor).opcode() != 89
                        || instructions.get(cursor + 1).intValue().orElse(-1) != parameter
                        || instructions.get(cursor + 3).opcode() != 83) {
                        return invalid(ownerName, "Use a literal parameter class array for " + name + ".");
                    }
                    final Optional<String> type = classLiteralDescriptor(instructions.get(cursor + 2));
                    if (type.isEmpty()) {
                        return invalid(ownerName, "Each method parameter must be a class literal for " + name + ".");
                    }
                    final String parameterType = type.orElseThrow();
                    descriptor.append(parameterType);
                    String elementType = parameterType;
                    while (elementType.startsWith("[")) {
                        elementType = elementType.substring(1);
                    }
                    if (elementType.startsWith("L") && elementType.endsWith(";")) {
                        final String parameterClass = elementType.substring(1, elementType.length() - 1);
                        if (classes.containsKey(parameterClass)) {
                            targets.add(parameterClass);
                        }
                    }
                    cursor += 4;
                }
                descriptor.append(')');
                final Instruction lookup = instructions.get(cursor++);
                final MethodRef reference = lookup.methodRef().orElse(null);
                if (lookup.opcode() != 182 || reference == null || !"java/lang/Class".equals(reference.owner())
                    || !"getDeclaredMethod".equals(reference.name())
                    || !"(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(reference.descriptor())) {
                    return invalid(ownerName, "Use getDeclaredMethod with literal parameters for " + name + ".");
                }
                boolean found = false;
                for (final MethodInfo method : classes.get(target).methods()) {
                    if (name.equals(method.name()) && method.descriptor().startsWith(descriptor.toString())) {
                        found = true;
                        methods.add(new EntryPoint(target, method.name(), method.descriptor()));
                    }
                }
                if (!found) {
                    return invalid(ownerName, "No declared method " + target + "." + name + descriptor + " exists.");
                }
            }
            if (cursor >= instructions.size() || instructions.get(cursor++).opcode() != 83) {
                return invalid(ownerName, "Return an array literal containing class or declared method literals.");
            }
        }
        if (cursor != instructions.size() - 1 || instructions.getLast().opcode() != 176) {
            return invalid(ownerName, "Return the declared array directly.");
        }
        return new Result(true, targets, methods, List.of());
    }

    private static Optional<String> classLiteralDescriptor(final Instruction instruction) {
        if ((instruction.opcode() == 18 || instruction.opcode() == 19) && instruction.className().isPresent()) {
            final String name = instruction.className().orElseThrow();
            return Optional.of(name.startsWith("[") ? name : "L" + name + ";");
        }
        if (instruction.opcode() != 178 || instruction.fieldRef().isEmpty()
            || !"TYPE".equals(instruction.fieldRef().orElseThrow().name())
            || !"Ljava/lang/Class;".equals(instruction.fieldRef().orElseThrow().descriptor())) {
            return Optional.empty();
        }
        return switch (instruction.fieldRef().orElseThrow().owner()) {
            case "java/lang/Boolean" -> Optional.of("Z");
            case "java/lang/Byte" -> Optional.of("B");
            case "java/lang/Character" -> Optional.of("C");
            case "java/lang/Short" -> Optional.of("S");
            case "java/lang/Integer" -> Optional.of("I");
            case "java/lang/Long" -> Optional.of("J");
            case "java/lang/Float" -> Optional.of("F");
            case "java/lang/Double" -> Optional.of("D");
            case "java/lang/Void" -> Optional.of("V");
            default -> Optional.empty();
        };
    }

    private static Result invalid(final String owner, final String reason) {
        return new Result(false, List.of(), List.of(), List.of(Diagnostic.error(
            "JAVAN096", "invalid reflection declaration", owner, METHOD, METHOD, reason,
            "Return an array of compiled class literals and getDeclaredMethod calls with literal parameters."
        )));
    }
}
