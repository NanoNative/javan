package javan.classfile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ConstantPoolTest {
    @Test
    void stringSupportsUtf8AndStringEntries() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.Utf8Entry("hello"),
            new ConstantPool.StringEntry(1),
            new Object()
        });

        assertThat(pool.string(1)).contains("hello");
        assertThat(pool.string(2)).contains("hello");
        assertThat(pool.string(3)).isEmpty();
    }

    @Test
    void dynamicRefResolvesMethodTypeAndRawPrimitiveBootstrapArguments() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.DynamicEntry(18, 0, 2),
            new ConstantPool.NameAndTypeEntry(3, 4),
            new ConstantPool.Utf8Entry("dyn"),
            new ConstantPool.Utf8Entry("()V"),
            new ConstantPool.MethodHandleEntry(6, 6),
            new ConstantPool.RefEntry(10, 7, 8),
            new ConstantPool.ClassEntry(9),
            new ConstantPool.NameAndTypeEntry(10, 11),
            new ConstantPool.Utf8Entry("bootstrap/Owner"),
            new ConstantPool.Utf8Entry("bootstrap"),
            new ConstantPool.Utf8Entry("()V"),
            new ConstantPool.Utf8Entry("hello"),
            new ConstantPool.StringEntry(12),
            new ConstantPool.MethodTypeEntry(4),
            new ConstantPool.RawEntry(3, Integer.valueOf(7)),
            new ConstantPool.RawEntry(5, Long.valueOf(9L)),
            new ConstantPool.RawEntry(4, Float.valueOf(1.5f)),
            new ConstantPool.RawEntry(6, Double.valueOf(2.5d))
        });

        assertThat(pool.dynamicRef(1, List.of(new BootstrapMethod(5, List.of(13, 14, 15, 16, 17, 18)))))
            .contains(new DynamicRef(
                "dyn",
                "()V",
                "bootstrap/Owner",
                "bootstrap",
                "()V",
                List.of("hello", "()V", "7", "9", "1.5", "2.5"),
                List.of(
                    new BootstrapValue(BootstrapValue.Kind.STRING, "hello"),
                    new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
                    new BootstrapValue(BootstrapValue.Kind.INTEGER, "7"),
                    new BootstrapValue(BootstrapValue.Kind.LONG, "9"),
                    new BootstrapValue(BootstrapValue.Kind.FLOAT, "1.5"),
                    new BootstrapValue(BootstrapValue.Kind.DOUBLE, "2.5")
                )
            ));
    }

    @Test
    void dynamicRefResolvesMethodHandleBootstrapArgumentsStructurally() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.DynamicEntry(18, 0, 2),
            new ConstantPool.NameAndTypeEntry(3, 4),
            new ConstantPool.Utf8Entry("run"),
            new ConstantPool.Utf8Entry("()Ljava/lang/Runnable;"),
            new ConstantPool.MethodHandleEntry(6, 6),
            new ConstantPool.RefEntry(10, 7, 8),
            new ConstantPool.ClassEntry(9),
            new ConstantPool.NameAndTypeEntry(10, 11),
            new ConstantPool.Utf8Entry("java/lang/invoke/LambdaMetafactory"),
            new ConstantPool.Utf8Entry("metafactory"),
            new ConstantPool.Utf8Entry("()Ljava/lang/invoke/CallSite;"),
            new ConstantPool.MethodTypeEntry(13),
            new ConstantPool.Utf8Entry("()V"),
            new ConstantPool.MethodHandleEntry(6, 15),
            new ConstantPool.RefEntry(10, 16, 17),
            new ConstantPool.ClassEntry(18),
            new ConstantPool.NameAndTypeEntry(19, 20),
            new ConstantPool.Utf8Entry("com/acme/Task"),
            new ConstantPool.Utf8Entry("lambda$run$0"),
            new ConstantPool.Utf8Entry("(Ljava/lang/String;)V")
        });

        final DynamicRef dynamicRef = pool.dynamicRef(1, List.of(new BootstrapMethod(5, List.of(12, 14, 12)))).orElseThrow();

        assertThat(dynamicRef.isLambdaMetafactory()).isTrue();
        assertThat(dynamicRef.bootstrapArguments()).containsExactly("()V", "com/acme/Task.lambda$run$0(Ljava/lang/String;)V", "()V");
        assertThat(dynamicRef.lambdaImplementationTarget())
            .contains(new MethodRef("com/acme/Task", "lambda$run$0", "(Ljava/lang/String;)V"));
        assertThat(dynamicRef.bootstrapValues()).containsExactly(
            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V"),
            new BootstrapValue(
                BootstrapValue.Kind.METHOD_HANDLE,
                "com/acme/Task.lambda$run$0(Ljava/lang/String;)V",
                Optional.of(new MethodRef("com/acme/Task", "lambda$run$0", "(Ljava/lang/String;)V")),
                Optional.of(Integer.valueOf(6))
            ),
            new BootstrapValue(BootstrapValue.Kind.METHOD_TYPE, "()V")
        );
    }

    @Test
    void dynamicRefRejectsNonInvokeDynamicAndInvalidBootstrapShapes() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.DynamicEntry(17, 0, 2),
            new ConstantPool.NameAndTypeEntry(3, 4),
            new ConstantPool.Utf8Entry("dyn"),
            new ConstantPool.Utf8Entry("()V"),
            new ConstantPool.DynamicEntry(18, 2, 2),
            new ConstantPool.DynamicEntry(18, 0, 2),
            new ConstantPool.RawEntry(3, Integer.valueOf(7))
        });

        assertThat(pool.dynamicRef(1, List.of())).isEmpty();
        assertThat(pool.dynamicRef(5, List.of(new BootstrapMethod(7, List.of())))).isEmpty();
        assertThat(pool.dynamicRef(6, List.of())).isEmpty();
    }
}
