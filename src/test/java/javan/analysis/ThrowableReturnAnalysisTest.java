package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldInfo;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ThrowableReturnAnalysisTest {
    @Test
    void staticallyBoundPlatformThrowableFactoryKeepsPlatformRepresentation() {
        final MethodRef factory = new MethodRef(
            "com/acme/Main",
            "factory",
            "()Ljava/lang/RuntimeException;"
        );
        final ClassFile owner = classFile(
            factory.owner(),
            "java/lang/Object",
            new MethodInfo(0x0008, factory.name(), factory.descriptor(), Optional.of(new CodeAttribute(
                2,
                0,
                new byte[0],
                0,
                List.of(
                    classInstruction(0, 187, "new", "java/lang/UnsupportedOperationException"),
                    instruction(3, 89, "dup"),
                    methodInstruction(4, 183, "invokespecial", new MethodRef(
                        "java/lang/UnsupportedOperationException",
                        "<init>",
                        "()V"
                    )),
                    instruction(7, 176, "areturn")
                )
            )))
        );

        assertThat(ThrowableReturnAnalysis.analyze(Map.of(owner.name(), owner), factory, true))
            .contains(new ThrowableReturnAnalysis.Result(
                "java/lang/UnsupportedOperationException",
                List.of("java/lang/UnsupportedOperationException"),
                false
            ));
    }

    @Test
    void broadPlatformFactoryWithMultipleConcreteTypesRemainsUnknown() {
        final MethodRef factory = new MethodRef(
            "com/acme/Main",
            "factory",
            "()Ljava/lang/RuntimeException;"
        );
        final ClassFile owner = classFile(
            factory.owner(),
            "java/lang/Object",
            new MethodInfo(0x0008, factory.name(), factory.descriptor(), Optional.of(new CodeAttribute(
                2,
                0,
                new byte[0],
                0,
                List.of(
                    classInstruction(0, 187, "new", "java/lang/UnsupportedOperationException"),
                    instruction(3, 89, "dup"),
                    methodInstruction(4, 183, "invokespecial", new MethodRef(
                        "java/lang/UnsupportedOperationException",
                        "<init>",
                        "()V"
                    )),
                    instruction(7, 87, "pop"),
                    classInstruction(8, 187, "new", "java/lang/IllegalArgumentException"),
                    instruction(11, 89, "dup"),
                    methodInstruction(12, 183, "invokespecial", new MethodRef(
                        "java/lang/IllegalArgumentException",
                        "<init>",
                        "()V"
                    )),
                    instruction(15, 176, "areturn")
                )
            )))
        );

        assertThat(ThrowableReturnAnalysis.analyze(Map.of(owner.name(), owner), factory, true)).isEmpty();
    }

    @Test
    void wideReferenceLoadsCannotMasqueradeAsGeneratedThrowableReturns() {
        final MethodRef factory = new MethodRef(
            "com/acme/Main",
            "factory",
            "(Ljava/lang/RuntimeException;)Ljava/lang/RuntimeException;"
        );
        final ClassFile owner = classFile(
            factory.owner(),
            "java/lang/Object",
            new MethodInfo(0x0008, factory.name(), factory.descriptor(), Optional.of(new CodeAttribute(
                3,
                1,
                new byte[0],
                0,
                List.of(
                    classInstruction(0, 187, "new", "com/acme/First"),
                    instruction(3, 89, "dup"),
                    methodInstruction(4, 183, "invokespecial", new MethodRef("com/acme/First", "<init>", "()V")),
                    instruction(7, 87, "pop"),
                    operandsInstruction(8, 196, "wide", 25, 0, 0),
                    instruction(12, 176, "areturn")
                )
            )))
        );
        final ClassFile throwable = classFile("com/acme/First", "java/lang/RuntimeException");

        assertThat(ThrowableReturnAnalysis.analyze(
            Map.of(owner.name(), owner, throwable.name(), throwable),
            factory,
            true
        )).isEmpty();
    }

    private static ClassFile classFile(
        final String name,
        final String superName,
        final MethodInfo... methods
    ) {
        return new ClassFile(
            69,
            name,
            superName,
            0,
            List.of(),
            List.<FieldInfo>of(),
            List.of(methods),
            Path.of(name + ".class"),
            true
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic) {
        return operandsInstruction(offset, opcode, mnemonic);
    }

    private static Instruction operandsInstruction(
        final int offset,
        final int opcode,
        final String mnemonic,
        final int... operands
    ) {
        final byte[] bytes = new byte[operands.length];
        for (int index = 0; index < operands.length; index++) {
            bytes[index] = (byte) operands[index];
        }
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            bytes,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction classInstruction(
        final int offset,
        final int opcode,
        final String mnemonic,
        final String className
    ) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.of(className),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction methodInstruction(
        final int offset,
        final int opcode,
        final String mnemonic,
        final MethodRef method
    ) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.of(method),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
