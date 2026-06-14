package javan.compat;

import java.util.Set;
import java.util.TreeSet;

/**
 * Explicit JVM bytecode support table for the current native profile.
 */
public final class BytecodeSupport {
    private static final int[] NATIVE_SUPPORTED = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
        24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
        46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67,
        68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 89, 96,
        97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 118,
        119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164,
        165, 166, 167, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184,
        185, 186, 187, 188, 189, 190, 191, 192, 193, 198, 199
    };

    private static final int[] KNOWN_JVM = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53,
        54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
        71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87,
        88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103,
        104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117,
        118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131,
        132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145,
        146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159,
        160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173,
        174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187,
        188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201
    };

    private static final String[] MNEMONICS = mnemonics();

    private BytecodeSupport() {
    }

    /**
     * Classifies an opcode for native code generation.
     *
     * @param opcode unsigned JVM opcode
     * @return support status
     */
    public static Status classify(final int opcode) {
        if (contains(NATIVE_SUPPORTED, opcode)) {
            return Status.NATIVE_SUPPORTED;
        }
        if (contains(KNOWN_JVM, opcode)) {
            return Status.RECOGNIZED_REJECTED;
        }
        return Status.UNKNOWN_FATAL;
    }

    /**
     * Returns a stable mnemonic for an opcode.
     *
     * @param opcode unsigned JVM opcode
     * @return mnemonic
     */
    public static String mnemonic(final int opcode) {
        if (opcode >= 0 && opcode < MNEMONICS.length) {
            return MNEMONICS[opcode];
        }
        return "opcode_" + opcode;
    }

    /**
     * Returns opcodes known to the JVM specification table used by this release.
     *
     * @return known opcodes
     */
    public static Set<Integer> knownOpcodes() {
        return setOf(KNOWN_JVM);
    }

    /**
     * Returns opcodes implemented by native lowering.
     *
     * @return supported opcodes
     */
    public static Set<Integer> nativeSupportedOpcodes() {
        return setOf(NATIVE_SUPPORTED);
    }

    private static boolean contains(final int[] values, final int target) {
        for (final int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> setOf(final int[] values) {
        final Set<Integer> result = new TreeSet<>();
        for (final int value : values) {
            result.add(value);
        }
        return result;
    }

    private static String[] mnemonics() {
        return new String[]{
            "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1", "iconst_2", "iconst_3", "iconst_4",
            "iconst_5", "lconst_0", "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0", "dconst_1",
            "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload", "lload", "fload", "dload", "aload",
            "iload_0", "iload_1", "iload_2", "iload_3", "lload_0", "lload_1", "lload_2", "lload_3",
            "fload_0", "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2", "dload_3",
            "aload_0", "aload_1", "aload_2", "aload_3", "iaload", "laload", "faload", "daload",
            "aaload", "baload", "caload", "saload", "istore", "lstore", "fstore", "dstore", "astore",
            "istore_0", "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1", "lstore_2", "lstore_3",
            "fstore_0", "fstore_1", "fstore_2", "fstore_3", "dstore_0", "dstore_1", "dstore_2", "dstore_3",
            "astore_0", "astore_1", "astore_2", "astore_3", "iastore", "lastore", "fastore", "dastore",
            "aastore", "bastore", "castore", "sastore", "pop", "pop2", "dup", "dup_x1", "dup_x2", "dup2",
            "dup2_x1", "dup2_x2", "swap", "iadd", "ladd", "fadd", "dadd", "isub", "lsub", "fsub", "dsub",
            "imul", "lmul", "fmul", "dmul", "idiv", "ldiv", "fdiv", "ddiv", "irem", "lrem", "frem", "drem",
            "ineg", "lneg", "fneg", "dneg", "ishl", "lshl", "ishr", "lshr", "iushr", "lushr", "iand", "land",
            "ior", "lor", "ixor", "lxor", "iinc", "i2l", "i2f", "i2d", "l2i", "l2f", "l2d", "f2i", "f2l",
            "f2d", "d2i", "d2l", "d2f", "i2b", "i2c", "i2s", "lcmp", "fcmpl", "fcmpg", "dcmpl", "dcmpg",
            "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle", "if_icmpeq", "if_icmpne", "if_icmplt",
            "if_icmpge", "if_icmpgt", "if_icmple", "if_acmpeq", "if_acmpne", "goto", "jsr", "ret",
            "tableswitch", "lookupswitch", "ireturn", "lreturn", "freturn", "dreturn", "areturn", "return",
            "getstatic", "putstatic", "getfield", "putfield", "invokevirtual", "invokespecial", "invokestatic",
            "invokeinterface", "invokedynamic", "new", "newarray", "anewarray", "arraylength", "athrow",
            "checkcast", "instanceof", "monitorenter", "monitorexit", "wide", "multianewarray", "ifnull",
            "ifnonnull", "goto_w", "jsr_w"
        };
    }

    /**
     * Support status for one bytecode feature.
     */
    public enum Status {
        NATIVE_SUPPORTED,
        RECOGNIZED_REJECTED,
        UNKNOWN_FATAL
    }
}
