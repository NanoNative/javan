package javan.compat;

import javan.classfile.MethodRef;
import javan.verify.ForbiddenApiRules;

/**
 * Exact callable-level accounting status for scanned JDK members.
 */
final class JdkCallableAccounting {
    private static final ForbiddenApiRules FORBIDDEN_API_RULES = new ForbiddenApiRules();

    private JdkCallableAccounting() {
    }

    static Status status(final MethodRef methodRef) {
        if (JdkCallSupport.isSupported(methodRef)) {
            return Status.SUPPORTED;
        }
        if (FORBIDDEN_API_RULES.forbiddenReason(methodRef).isPresent() || isExactRejected(methodRef)) {
            return Status.EXPLICIT_REJECTED;
        }
        return Status.UNKNOWN;
    }

    private static boolean isExactRejected(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        final String methodName = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("java/lang/Object".equals(owner)) {
            if ("wait".equals(methodName)) {
                return "()V".equals(descriptor) || "(J)V".equals(descriptor) || "(JI)V".equals(descriptor);
            }
            if ("notify".equals(methodName) || "notifyAll".equals(methodName)) {
                return "()V".equals(descriptor);
            }
            return false;
        }
        if ("java/util/concurrent/Executors".equals(owner)) {
            if ("newSingleThreadExecutor".equals(methodName) || "newCachedThreadPool".equals(methodName)) {
                return "()Ljava/util/concurrent/ExecutorService;".equals(descriptor);
            }
            return false;
        }
        if ("java/lang/String".equals(owner)) {
            if ("<init>".equals(methodName)) {
                return "(Ljava/lang/StringBuffer;)V".equals(descriptor)
                    || "([III)V".equals(descriptor)
                    || "([B)V".equals(descriptor)
                    || "([BI)V".equals(descriptor)
                    || "([BII)V".equals(descriptor)
                    || "([BIII)V".equals(descriptor)
                    || "([BIILjava/lang/String;)V".equals(descriptor)
                    || "([BIILjava/nio/charset/Charset;)V".equals(descriptor)
                    || "([BLjava/lang/String;)V".equals(descriptor)
                    || "([BLjava/nio/charset/Charset;)V".equals(descriptor);
            }
            if ("matches".equals(methodName)) {
                return "(Ljava/lang/String;)Z".equals(descriptor);
            }
            if ("replaceFirst".equals(methodName) || "replaceAll".equals(methodName)) {
                return "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;".equals(descriptor);
            }
            if ("split".equals(methodName)) {
                return "(Ljava/lang/String;I)[Ljava/lang/String;".equals(descriptor)
                    || "(Ljava/lang/String;)[Ljava/lang/String;".equals(descriptor);
            }
            if ("splitWithDelimiters".equals(methodName)) {
                return "(Ljava/lang/String;I)[Ljava/lang/String;".equals(descriptor);
            }
            if ("format".equals(methodName)) {
                return "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;".equals(descriptor)
                    || "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;".equals(descriptor);
            }
            if ("formatted".equals(methodName)) {
                return "([Ljava/lang/Object;)Ljava/lang/String;".equals(descriptor);
            }
            if ("codePointAt".equals(methodName) || "codePointBefore".equals(methodName)) {
                return "(I)I".equals(descriptor);
            }
            if ("codePointCount".equals(methodName) || "offsetByCodePoints".equals(methodName)) {
                return "(II)I".equals(descriptor);
            }
            if ("getChars".equals(methodName)) {
                return "(II[CI)V".equals(descriptor);
            }
            if ("getBytes".equals(methodName)) {
                return "(II[BI)V".equals(descriptor)
                    || "(Ljava/lang/String;)[B".equals(descriptor)
                    || "(Ljava/nio/charset/Charset;)[B".equals(descriptor)
                    || "()[B".equals(descriptor);
            }
            if ("toLowerCase".equals(methodName) || "toUpperCase".equals(methodName)) {
                return "(Ljava/util/Locale;)Ljava/lang/String;".equals(descriptor);
            }
            if ("strip".equals(methodName)
                || "stripLeading".equals(methodName)
                || "stripTrailing".equals(methodName)
                || "lines".equals(methodName)
                || "chars".equals(methodName)
                || "codePoints".equals(methodName)
                || "toCharArray".equals(methodName)
                || "stripIndent".equals(methodName)
                || "translateEscapes".equals(methodName)) {
                if (("strip".equals(methodName)
                    || "stripLeading".equals(methodName)
                    || "stripTrailing".equals(methodName))
                    && "()Ljava/lang/String;".equals(descriptor)) {
                    return false;
                }
                return descriptor.startsWith("()");
            }
            return "indent".equals(methodName) && "(I)Ljava/lang/String;".equals(descriptor);
        }
        if ("java/lang/StringBuilder".equals(owner)) {
            if ("<init>".equals(methodName)) {
                return "(Ljava/lang/CharSequence;)V".equals(descriptor);
            }
            if ("append".equals(methodName)) {
                return "(Ljava/lang/StringBuffer;)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;II)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(Ljava/lang/StringBuffer;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("appendCodePoint".equals(methodName)) {
                return "(I)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(I)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("insert".equals(methodName)) {
                return "(ILjava/lang/CharSequence;)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;II)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("repeat".equals(methodName)) {
                return "(II)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;I)Ljava/lang/StringBuilder;".equals(descriptor)
                    || "(II)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;I)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("chars".equals(methodName) || "codePoints".equals(methodName)) {
                return "()Ljava/util/stream/IntStream;".equals(descriptor);
            }
            if ("codePointAt".equals(methodName) || "codePointBefore".equals(methodName)) {
                return "(I)I".equals(descriptor);
            }
            if ("codePointCount".equals(methodName) || "offsetByCodePoints".equals(methodName)) {
                return "(II)I".equals(descriptor);
            }
            return "getChars".equals(methodName) && "(II[CI)V".equals(descriptor);
        }
        if ("java/lang/StringBuffer".equals(owner)) {
            if ("<init>".equals(methodName)) {
                return "(Ljava/lang/CharSequence;)V".equals(descriptor);
            }
            if ("append".equals(methodName)) {
                return "(Ljava/lang/StringBuffer;)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;II)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(Ljava/lang/AbstractStringBuilder;)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(Ljava/lang/StringBuffer;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/AbstractStringBuilder;)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("appendCodePoint".equals(methodName)) {
                return "(I)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(I)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("insert".equals(methodName)) {
                return "(ILjava/lang/CharSequence;)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;II)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("repeat".equals(methodName)) {
                return "(II)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;I)Ljava/lang/StringBuffer;".equals(descriptor)
                    || "(II)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;I)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("chars".equals(methodName) || "codePoints".equals(methodName)) {
                return "()Ljava/util/stream/IntStream;".equals(descriptor);
            }
            if ("codePointAt".equals(methodName) || "codePointBefore".equals(methodName)) {
                return "(I)I".equals(descriptor);
            }
            if ("codePointCount".equals(methodName) || "offsetByCodePoints".equals(methodName)) {
                return "(II)I".equals(descriptor);
            }
            return "getChars".equals(methodName) && "(II[CI)V".equals(descriptor);
        }
        if ("java/lang/AbstractStringBuilder".equals(owner)) {
            if ("<init>".equals(methodName)) {
                return "(Ljava/lang/CharSequence;)V".equals(descriptor);
            }
            if ("append".equals(methodName)) {
                return "(Ljava/lang/StringBuffer;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/AbstractStringBuilder;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("appendCodePoint".equals(methodName)) {
                return "(I)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("insert".equals(methodName)) {
                return "(ILjava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(ILjava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("repeat".equals(methodName)) {
                return "(II)Ljava/lang/AbstractStringBuilder;".equals(descriptor)
                    || "(Ljava/lang/CharSequence;I)Ljava/lang/AbstractStringBuilder;".equals(descriptor);
            }
            if ("chars".equals(methodName) || "codePoints".equals(methodName)) {
                return "()Ljava/util/stream/IntStream;".equals(descriptor);
            }
            if ("codePointAt".equals(methodName) || "codePointBefore".equals(methodName)) {
                return "(I)I".equals(descriptor);
            }
            if ("codePointCount".equals(methodName) || "offsetByCodePoints".equals(methodName)) {
                return "(II)I".equals(descriptor);
            }
            return "getChars".equals(methodName) && "(II[CI)V".equals(descriptor);
        }
        if (isInternalStringHelperOwner(owner)) {
            return true;
        }
        if (isCharacterDataOwner(owner)) {
            return true;
        }
        if ("java/lang/ConditionalSpecialCasing".equals(owner)
            || "java/lang/String$CaseInsensitiveComparator".equals(owner)) {
            return true;
        }
        if ("java/lang/ConditionalSpecialCasing$Entry".equals(owner)) {
            return true;
        }
        if (owner.startsWith("java/util/regex/")) {
            return true;
        }
        if (owner.startsWith("java/util/function/")) {
            return true;
        }
        if (owner.startsWith("java/util/stream/")) {
            return true;
        }
        if (owner.startsWith("java/util/zip/")) {
            return true;
        }
        if (owner.startsWith("java/time/format/")) {
            return true;
        }
        if (owner.startsWith("java/math/")) {
            return true;
        }
        if (owner.startsWith("java/nio/charset/")) {
            return true;
        }
        if (owner.startsWith("java/text/")) {
            return true;
        }
        if (owner.startsWith("java/util/concurrent/atomic/")) {
            return true;
        }
        if (owner.startsWith("java/lang/module/")) {
            return true;
        }
        if (owner.startsWith("java/lang/foreign/")) {
            return true;
        }
        if (owner.startsWith("java/lang/invoke/")) {
            if (isOwnerFamily(owner, "java/lang/invoke/StringConcatFactory")) {
                return false;
            }
            if (JdkCallSupport.isPlatformThrowable(owner)) {
                return false;
            }
            return true;
        }
        if (owner.startsWith("java/lang/classfile/")) {
            if (JdkCallSupport.isPlatformThrowable(owner)) {
                return false;
            }
            return true;
        }
        if (owner.startsWith("java/security/") && !owner.startsWith("java/security/cert/")) {
            if (JdkCallSupport.isPlatformThrowable(owner)) {
                return false;
            }
            return true;
        }
        if (owner.startsWith("java/security/cert/")) {
            if (JdkCallSupport.isPlatformThrowable(owner)) {
                return false;
            }
            return true;
        }
        if (owner.startsWith("java/time/chrono/")) {
            return true;
        }
        if (owner.startsWith("java/util/logging/")) {
            return true;
        }
        if (owner.startsWith("java/awt/")) {
            return true;
        }
        if (owner.startsWith("java/beans/")) {
            if (JdkCallSupport.isPlatformThrowable(owner)) {
                return false;
            }
            return true;
        }
        if (owner.startsWith("javax/swing/")) {
            return true;
        }
        if (owner.startsWith("java/util/concurrent/locks/")) {
            return true;
        }
        if (owner.startsWith("jdk/jfr/")) {
            return true;
        }
        if (owner.startsWith("jdk/incubator/vector/")) {
            return true;
        }
        if (owner.startsWith("jdk/graal/compiler/")) {
            return true;
        }
        if (owner.startsWith("jdk/vm/ci/")) {
            return true;
        }
        if (owner.startsWith("jdk/jpackage/internal/")) {
            return true;
        }
        if (owner.startsWith("jdk/internal/classfile/impl/")) {
            return true;
        }
        if (owner.startsWith("com/sun/tools/javac/")) {
            return true;
        }
        if (owner.startsWith("com/sun/tools/jdi/")) {
            return true;
        }
        if (owner.startsWith("com/apple/laf/")) {
            return true;
        }
        if (owner.startsWith("com/sun/media/sound/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/bcel/internal/generic/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/bcel/internal/classfile/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xerces/internal/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xml/internal/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xalan/internal/")) {
            return true;
        }
        if (owner.startsWith("jdk/internal/org/jline/")) {
            return true;
        }
        if (owner.startsWith("jdk/internal/org/commonmark/")) {
            return true;
        }
        if (owner.startsWith("com/sun/tools/example/debug/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xalan/internal/xsltc/compiler/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xerces/internal/dom/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xml/internal/dtm/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xml/internal/serializer/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xml/internal/utils/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xalan/internal/xsltc/dom/")) {
            return true;
        }
        if (owner.startsWith("com/sun/org/apache/xerces/internal/impl/xs/")) {
            return true;
        }
        if (owner.startsWith("org/jcp/xml/dsig/internal/dom/")) {
            return true;
        }
        if (owner.startsWith("com/sun/tools/jdeps/")) {
            return true;
        }
        if (owner.startsWith("sun/font/")) {
            return true;
        }
        if (owner.startsWith("sun/lwawt/")) {
            return true;
        }
        if (owner.startsWith("sun/awt/")) {
            return true;
        }
        if (owner.startsWith("sun/nio/cs/ext/")) {
            return true;
        }
        if (owner.startsWith("sun/print/")) {
            return true;
        }
        if (owner.startsWith("sun/jvm/hotspot/")) {
            return true;
        }
        if (owner.startsWith("jdk/javadoc/internal/doclets/")) {
            return true;
        }
        if (owner.startsWith("jdk/jshell/")) {
            return true;
        }
        if (owner.startsWith("jdk/internal/jshell/")) {
            return true;
        }
        if (owner.startsWith("jdk/tools/jlink/internal/")) {
            return true;
        }
        if (owner.startsWith("sun/util/resources/cldr/ext/")) {
            return true;
        }
        if (owner.startsWith("sun/tools/jconsole/")) {
            return true;
        }
        if ("sun/misc/Unsafe".equals(owner)) {
            return true;
        }
        return false;
    }

    private static boolean isInternalStringHelperOwner(final String owner) {
        return isOwnerFamily(owner, "java/lang/StringLatin1")
            || isOwnerFamily(owner, "java/lang/StringUTF16")
            || isOwnerFamily(owner, "java/lang/StringConcatHelper")
            || "java/lang/StringCoding".equals(owner);
    }

    private static boolean isOwnerFamily(final String owner, final String family) {
        return family.equals(owner) || owner.startsWith(family + "$");
    }

    private static boolean isCharacterDataOwner(final String owner) {
        return owner.equals("java/lang/CharacterData") || owner.startsWith("java/lang/CharacterData");
    }

    enum Status {
        SUPPORTED,
        EXPLICIT_REJECTED,
        UNKNOWN
    }
}
