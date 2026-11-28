package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.FieldInfo;
import javan.classfile.FieldRef;
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
     * @param methods declared method and constructor identities, including abstract methods
     * @param fields declared field identities
     * @param diagnostics deterministic validation errors
     */
    public record Result(boolean present, List<String> classes, List<EntryPoint> methods, List<FieldRef> fields, List<Diagnostic> diagnostics) {
        public Result {
            classes = List.copyOf(classes);
            methods = List.copyOf(methods);
            fields = List.copyOf(fields);
            diagnostics = List.copyOf(diagnostics);
        }

        /**
         * Creates a declaration result without field targets.
         *
         * @param present whether a declaration exists
         * @param classes declared class names
         * @param methods declared methods and constructors
         * @param diagnostics validation diagnostics
         * @throws NullPointerException if a list or one of its elements is null
         */
        public Result(
            final boolean present,
            final List<String> classes,
            final List<EntryPoint> methods,
            final List<Diagnostic> diagnostics
        ) {
            this(present, classes, methods, List.of(), diagnostics);
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
            return new Result(false, List.of(), List.of(), List.of(), List.of());
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
            return invalid(ownerName, "Return an array literal containing class or declared member literals.");
        }
        final List<String> targets = new ArrayList<>();
        final List<EntryPoint> methods = new ArrayList<>();
        final List<FieldRef> fields = new ArrayList<>();
        int cursor = 2;
        for (int index = 0; index < count; index++) {
            if (cursor + 3 >= instructions.size() || instructions.get(cursor).opcode() != 89
                || instructions.get(cursor + 1).intValue().orElse(-1) != index) {
                return invalid(ownerName, "Each array element must be a class or declared member literal.");
            }
            final Instruction value = instructions.get(cursor + 2);
            if (value.opcode() == 178 && classLiteralDescriptor(value).isPresent()) {
                cursor += 3;
                if (instructions.get(cursor++).opcode() != 83) {
                    return invalid(ownerName, "Primitive classes cannot declare member targets.");
                }
                continue;
            }
            if ((value.opcode() != 18 && value.opcode() != 19)
                || value.className().isEmpty()) {
                return invalid(ownerName, "Each target must name a class from the compiled world.");
            }
            final String target = value.className().orElseThrow();
            cursor += 3;
            if (target.startsWith("[")) {
                String component = target;
                while (component.startsWith("[")) {
                    component = component.substring(1);
                }
                if (instructions.get(cursor).opcode() != 83) {
                    return invalid(ownerName, "Array classes cannot declare member targets.");
                }
                if (component.startsWith("L") && component.endsWith(";")) {
                    final String componentClass = component.substring(1, component.length() - 1);
                    if (!classes.containsKey(componentClass)) {
                        return invalid(ownerName, "Each array component must name a class from the compiled world.");
                    }
                    targets.add(componentClass);
                } else if (component.length() != 1 || "ZBCSIJFD".indexOf(component.charAt(0)) < 0) {
                    return invalid(ownerName, "Each array component must be a supported class or primitive.");
                }
            } else if (classes.containsKey(target)) {
                targets.add(target);
            } else {
                return invalid(ownerName, "Each target must name a class from the compiled world.");
            }
            if (instructions.get(cursor).opcode() != 83 && cursor + 1 < instructions.size()
                && instructions.get(cursor).stringValue().isPresent()
                && instructions.get(cursor + 1).opcode() == 182
                && instructions.get(cursor + 1).methodRef().isPresent()
                && "java/lang/Class".equals(instructions.get(cursor + 1).methodRef().orElseThrow().owner())
                && "getDeclaredField".equals(instructions.get(cursor + 1).methodRef().orElseThrow().name())
                && "(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(
                    instructions.get(cursor + 1).methodRef().orElseThrow().descriptor())) {
                final String fieldName = instructions.get(cursor).stringValue().orElseThrow();
                cursor += 2;
                boolean found = false;
                for (final FieldInfo field : classes.get(target).fields()) {
                    if (fieldName.equals(field.name())) {
                        found = true;
                        fields.add(new FieldRef(target, field.name(), field.descriptor()));
                    }
                }
                if (!found) return invalid(ownerName, "No declared field " + target + "." + fieldName + " exists.");
            } else if (instructions.get(cursor).opcode() != 83) {
                final boolean constructor = instructions.get(cursor).stringValue().isEmpty();
                final String name = constructor ? "<init>" : instructions.get(cursor++).stringValue().orElseThrow();
                if (cursor + 2 >= instructions.size() || instructions.get(cursor).intValue().isEmpty()
                    || instructions.get(cursor + 1).opcode() != 189
                    || !instructions.get(cursor + 1).className().orElse("").equals("java/lang/Class")) {
                    return invalid(ownerName, "Use getDeclaredField with a literal name or method/constructor lookups with literal parameters.");
                }
                final int parameters = instructions.get(cursor).intValue().orElseThrow();
                cursor += 2;
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
                    || !(constructor ? "getDeclaredConstructor".equals(reference.name()) || "getConstructor".equals(reference.name())
                        : "getDeclaredMethod".equals(reference.name()))
                    || !(constructor ? "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"
                        : "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;").equals(reference.descriptor())) {
                    return invalid(ownerName, "Use getDeclaredMethod, getDeclaredConstructor or getConstructor with literal parameters for " + name + ".");
                }
                boolean found = false;
                for (final MethodInfo method : classes.get(target).methods()) {
                    if (name.equals(method.name()) && method.descriptor().startsWith(descriptor.toString())
                        && (!constructor || !"getConstructor".equals(reference.name()) || method.isPublic())) {
                        found = true;
                        methods.add(new EntryPoint(target, method.name(), method.descriptor()));
                    }
                }
                if (!found) {
                    return invalid(ownerName, "No declared member " + target + "." + name + descriptor + " exists.");
                }
            }
            if (cursor >= instructions.size() || instructions.get(cursor++).opcode() != 83) {
                return invalid(ownerName, "Return an array literal containing class or declared member literals.");
            }
        }
        if (cursor != instructions.size() - 1 || instructions.getLast().opcode() != 176) {
            return invalid(ownerName, "Return the declared array directly.");
        }
        return new Result(true, targets, methods, fields, List.of());
    }

    static Optional<String> classLiteralDescriptor(final Instruction instruction) {
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
        return new Result(false, List.of(), List.of(), List.of(), List.of(Diagnostic.error(
            "JAVAN096", "invalid reflection declaration", owner, METHOD, METHOD, reason,
            "Return compiled class literals or literal getDeclaredField/getDeclaredMethod/getDeclaredConstructor/getConstructor calls."
        )));
    }
}
