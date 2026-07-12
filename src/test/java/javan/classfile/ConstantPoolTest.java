package javan.classfile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ConstantPoolTest {
    @Test
    void classLiteralNameResolvesOnlyClassEntries() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.Utf8Entry("com/acme/Main"),
            new ConstantPool.ClassEntry(1),
            new ConstantPool.StringEntry(1)
        });

        assertThat(pool.classLiteralName(2)).contains("com/acme/Main");
        assertThat(pool.classLiteralName(3)).isEmpty();
    }

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
    void primitiveLiteralHelpersResolveOnlyMatchingRawEntries() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.RawEntry(3, Integer.valueOf(7)),
            new ConstantPool.RawEntry(5, Long.valueOf(9L)),
            new ConstantPool.RawEntry(4, Float.valueOf(1.5f)),
            new ConstantPool.RawEntry(6, Double.valueOf(2.5d)),
            new ConstantPool.RawEntry(3, "not-an-int"),
            new ConstantPool.RawEntry(4, Integer.valueOf(9)),
            new ConstantPool.Utf8Entry("hello"),
            new ConstantPool.RawEntry(5, Integer.valueOf(3)),
            new ConstantPool.RawEntry(6, Float.valueOf(4.5f))
        });

        assertThat(pool.intValue(1)).contains(7);
        assertThat(pool.intValue(5)).isEmpty();
        assertThat(pool.longValue(2)).contains(9L);
        assertThat(pool.longValue(1)).isEmpty();
        assertThat(pool.longValue(7)).isEmpty();
        assertThat(pool.longValue(8)).isEmpty();
        assertThat(pool.floatValue(3)).contains(1.5f);
        assertThat(pool.floatValue(6)).isEmpty();
        assertThat(pool.doubleValue(4)).contains(2.5d);
        assertThat(pool.doubleValue(7)).isEmpty();
        assertThat(pool.doubleValue(9)).isEmpty();
    }

    @Test
    void fieldAndMethodReferencesResolveStructurally() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.RefEntry(9, 2, 3),
            new ConstantPool.ClassEntry(4),
            new ConstantPool.NameAndTypeEntry(5, 6),
            new ConstantPool.Utf8Entry("com/acme/Main"),
            new ConstantPool.Utf8Entry("value"),
            new ConstantPool.Utf8Entry("I"),
            new ConstantPool.RefEntry(10, 2, 8),
            new ConstantPool.NameAndTypeEntry(9, 10),
            new ConstantPool.Utf8Entry("run"),
            new ConstantPool.Utf8Entry("()V")
        });

        assertThat(pool.fieldRef(1)).isEqualTo(new FieldRef("com/acme/Main", "value", "I"));
        assertThat(pool.methodRef(7)).isEqualTo(new MethodRef("com/acme/Main", "run", "()V"));
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
    void dynamicRefResolvesFallbackBootstrapValuesForInvalidHandlesAndUnknownRawValues() {
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
            new ConstantPool.MethodHandleEntry(6, 13),
            new ConstantPool.RawEntry(9, "not-a-ref"),
            new ConstantPool.RawEntry(3, Boolean.TRUE),
            new Object()
        });

        final DynamicRef dynamicRef = pool.dynamicRef(1, List.of(new BootstrapMethod(5, List.of(12, 14, 15)))).orElseThrow();

        assertThat(dynamicRef.bootstrapArguments()).containsExactly("", "", "");
        assertThat(dynamicRef.bootstrapValues()).containsExactly(
            new BootstrapValue(BootstrapValue.Kind.METHOD_HANDLE, ""),
            new BootstrapValue(BootstrapValue.Kind.UNKNOWN, ""),
            new BootstrapValue(BootstrapValue.Kind.UNKNOWN, "")
        );
        assertThat(dynamicRef.lambdaImplementationTarget()).isEmpty();
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

    @Test
    void dynamicRefRejectsBootstrapIndexOutsideAvailableMethodsAndNonHandleBootstrapEntry() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.DynamicEntry(18, -1, 2),
            new ConstantPool.NameAndTypeEntry(3, 4),
            new ConstantPool.Utf8Entry("dyn"),
            new ConstantPool.Utf8Entry("()V"),
            new ConstantPool.DynamicEntry(18, 1, 2),
            new ConstantPool.RawEntry(3, Integer.valueOf(7))
        });

        assertThat(pool.dynamicRef(1, List.of(new BootstrapMethod(6, List.of())))).isEmpty();
        assertThat(pool.dynamicRef(5, List.of(new BootstrapMethod(6, List.of())))).isEmpty();
    }

    @Test
    void dynamicRefRejectsEntriesThatAreNotDynamicConstants() {
        final ConstantPool pool = new ConstantPool(new Object[]{
            null,
            new ConstantPool.RawEntry(3, Integer.valueOf(7))
        });

        assertThat(pool.dynamicRef(1, List.of())).isEmpty();
    }

    @Test
    void dynamicRefFallsBackForMethodHandlesWithOutOfRangeAndNonMethodReferenceTargets() {
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
            new ConstantPool.MethodHandleEntry(6, 0),
            new ConstantPool.MethodHandleEntry(6, 14),
            new ConstantPool.RefEntry(9, 7, 8),
            new ConstantPool.MethodHandleEntry(6, 99)
        });

        final DynamicRef dynamicRef = pool.dynamicRef(1, List.of(new BootstrapMethod(5, List.of(12, 13, 15)))).orElseThrow();

        assertThat(dynamicRef.bootstrapArguments()).containsExactly("", "", "");
        assertThat(dynamicRef.bootstrapValues()).containsExactly(
            new BootstrapValue(BootstrapValue.Kind.METHOD_HANDLE, ""),
            new BootstrapValue(BootstrapValue.Kind.METHOD_HANDLE, ""),
            new BootstrapValue(BootstrapValue.Kind.METHOD_HANDLE, "")
        );
    }
}
