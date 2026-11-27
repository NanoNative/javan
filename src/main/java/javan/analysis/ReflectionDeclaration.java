package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads the optional application-owned reflection declaration without executing it. */
public final class ReflectionDeclaration {
    private static final String METHOD = "javanReflectionTargets";
    private static final String DESCRIPTOR = "()[Ljava/lang/reflect/AnnotatedElement;";

    private ReflectionDeclaration() {
    }

    /** Parsed class targets and deterministic validation errors. */
    public record Result(List<String> classes, List<Diagnostic> diagnostics) {
        public Result {
            classes = List.copyOf(classes);
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
            return new Result(List.of(), List.of());
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
        if (count < 0 || instructions.size() != 3L + 4L * count) {
            return invalid(ownerName, "Return an array literal containing compiled class literals.");
        }
        final List<String> targets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            final int offset = 2 + 4 * index;
            final Instruction value = instructions.get(offset + 2);
            if (instructions.get(offset).opcode() != 89
                || instructions.get(offset + 1).intValue().orElse(-1) != index
                || (value.opcode() != 18 && value.opcode() != 19)
                || value.className().isEmpty()
                || !classes.containsKey(value.className().orElseThrow())
                || instructions.get(offset + 3).opcode() != 83) {
                return invalid(ownerName, "Each array element must be a class literal from the compiled world.");
            }
            targets.add(value.className().orElseThrow());
        }
        if (instructions.getLast().opcode() != 176) {
            return invalid(ownerName, "Return the declared array directly.");
        }
        return new Result(targets, List.of());
    }

    private static Result invalid(final String owner, final String reason) {
        return new Result(List.of(), List.of(Diagnostic.error(
            "JAVAN179", "invalid reflection declaration", owner, METHOD, METHOD, reason,
            "Declare one public static AnnotatedElement[] javanReflectionTargets() returning an array of compiled class literals."
        )));
    }
}
