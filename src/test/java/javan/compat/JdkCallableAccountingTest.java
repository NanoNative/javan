package javan.compat;

import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkCallableAccountingTest {
    @Test
    void marksSupportedCallableAsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksForbiddenDynamicApiAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectWaitAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "wait", "(J)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectWaitLongIntAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "wait", "(JI)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectNotifyAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "notify", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksObjectNotifyAllAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/Object", "notifyAll", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksUnsupportedExecutorFactoryAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/Executors", "newSingleThreadExecutor", "()Ljava/util/concurrent/ExecutorService;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksInheritableThreadLocalConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/InheritableThreadLocal", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksJfrCallableAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("jdk/jfr/FlightRecorder", "isAvailable", "()Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksUnsafeCallableAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("sun/misc/Unsafe", "getUnsafe", "()Lsun/misc/Unsafe;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringMatchesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "matches", "(Ljava/lang/String;)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringByteConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "<init>", "([B)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringCharsetConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "<init>", "([BLjava/nio/charset/Charset;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringStringBufferConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "<init>", "(Ljava/lang/StringBuffer;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringReplaceFirstAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "replaceFirst", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringReplaceAllAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringSplitRegexLimitAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "split", "(Ljava/lang/String;I)[Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringSplitRegexAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringSplitWithDelimitersAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "splitWithDelimiters", "(Ljava/lang/String;I)[Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringFormatVarargsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringCodePointAtAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "codePointAt", "(I)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringCodePointCountAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "codePointCount", "(II)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringGetCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "getChars", "(II[CI)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringGetBytesCharsetAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "chars", "()Ljava/util/stream/IntStream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringToCharArrayAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "toCharArray", "()[C")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringFormatLocaleVarargsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "format", "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringFormattedAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "formatted", "([Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringToLowerCaseAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "toLowerCase", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringToUpperCaseLocaleAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "toUpperCase", "(Ljava/util/Locale;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringStripAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "strip", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringIsBlankAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "isBlank", "()Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringLinesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "lines", "()Ljava/util/stream/Stream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringIndentAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "indent", "(I)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringTranslateEscapesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "translateEscapes", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringValueOfIntAsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String", "valueOf", "(I)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksStringBuilderCharSequenceConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "<init>", "(Ljava/lang/CharSequence;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderAppendStringBufferAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/StringBuffer;)Ljava/lang/StringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderAppendCharSequenceRangeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/CharSequence;II)Ljava/lang/StringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderAppendCodePointAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "appendCodePoint", "(I)Ljava/lang/StringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderInsertCharSequenceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "insert", "(ILjava/lang/CharSequence;)Ljava/lang/StringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderRepeatAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "repeat", "(II)Ljava/lang/StringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderCodePointAtAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "codePointAt", "(I)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "chars", "()Ljava/util/stream/IntStream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBuilderGetCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuilder", "getChars", "(II[CI)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferCharSequenceConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "<init>", "(Ljava/lang/CharSequence;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferAppendStringBufferAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "append", "(Ljava/lang/StringBuffer;)Ljava/lang/StringBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferAppendCharSequenceRangeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "append", "(Ljava/lang/CharSequence;II)Ljava/lang/StringBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferAppendCodePointAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "appendCodePoint", "(I)Ljava/lang/StringBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferInsertCharSequenceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "insert", "(ILjava/lang/CharSequence;)Ljava/lang/StringBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferRepeatAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "repeat", "(II)Ljava/lang/StringBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferCodePointAtAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "codePointAt", "(I)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "chars", "()Ljava/util/stream/IntStream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringBufferGetCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringBuffer", "getChars", "(II[CI)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderCharSequenceConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "<init>", "(Ljava/lang/CharSequence;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderAppendStringBufferAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "append", "(Ljava/lang/StringBuffer;)Ljava/lang/AbstractStringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderAppendAbstractStringBuilderAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "append", "(Ljava/lang/AbstractStringBuilder;)Ljava/lang/AbstractStringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderAppendCharSequenceRangeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "append", "(Ljava/lang/CharSequence;II)Ljava/lang/AbstractStringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderAppendCodePointAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "appendCodePoint", "(I)Ljava/lang/AbstractStringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderInsertCharSequenceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "insert", "(ILjava/lang/CharSequence;)Ljava/lang/AbstractStringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderRepeatAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "repeat", "(II)Ljava/lang/AbstractStringBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderCodePointAtAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "codePointAt", "(I)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "chars", "()Ljava/util/stream/IntStream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAbstractStringBuilderGetCharsAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/AbstractStringBuilder", "getChars", "(II[CI)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringLatin1HelperAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringLatin1", "charAt", "([BI)C")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringLatin1NestedHelperAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringLatin1$CharsSpliterator", "<init>", "([BII)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringUtf16HelperAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringUTF16", "charAt", "([BI)C")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringUtf16NestedHelperAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringUTF16$CodePointsSpliterator", "<init>", "([BII)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringConcatHelperAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringConcatHelper", "simpleConcat", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringConcatHelperNestedClassAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringConcatHelper$Concat1", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringCodingHelperAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/StringCoding", "countNonZeroAscii", "(Ljava/lang/String;)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharacterDataBaseAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/CharacterData", "of", "(I)Ljava/lang/CharacterData;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharacterDataLatin1AsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/CharacterDataLatin1", "toLowerCase", "(I)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharacterData00AsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/CharacterData00", "getType", "(I)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharacterDataUndefinedAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/CharacterDataUndefined", "isDigit", "(I)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksConditionalSpecialCasingAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/ConditionalSpecialCasing", "toLowerCaseEx", "(Ljava/lang/String;ILjava/util/Locale;)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStringCaseInsensitiveComparatorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/String$CaseInsensitiveComparator", "compare", "(Ljava/lang/String;Ljava/lang/String;)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksConditionalSpecialCasingEntryConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/ConditionalSpecialCasing$Entry", "<init>", "(I[C[CLjava/lang/String;I)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksConditionalSpecialCasingEntryGetterAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/ConditionalSpecialCasing$Entry", "getUpperCase", "()[C")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexPatternCompileAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/Pattern", "compile", "(Ljava/lang/String;)Ljava/util/regex/Pattern;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexPatternNestedIteratorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/Pattern$1MatcherIterator", "<init>", "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexMatcherFindAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/Matcher", "find", "()Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexAsciiAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/ASCII", "isDigit", "(I)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexCharPredicatesAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/CharPredicates", "ASCII_DIGIT", "()Ljava/util/regex/Pattern$CharPredicate;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexIntHashSetAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/IntHashSet", "contains", "(I)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexMatchResultAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/MatchResult", "group", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexPatternSyntaxExceptionAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/PatternSyntaxException", "getPattern", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksRegexPrintPatternAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/regex/PrintPattern", "print", "(Ljava/lang/Object;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksFunctionApplyAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksSupplierGetAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksPredicateTestAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStreamOfArrayAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/stream/Stream", "of", "([Ljava/lang/Object;)Ljava/util/stream/Stream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksIntStreamRangeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/stream/IntStream", "range", "(II)Ljava/util/stream/IntStream;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksBaseStreamIteratorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/stream/BaseStream", "iterator", "()Ljava/util/Iterator;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCollectorsToListAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/stream/Collectors", "toList", "()Ljava/util/stream/Collector;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksZipFileStringConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/zip/ZipFile", "<init>", "(Ljava/lang/String;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksZipInputStreamGetNextEntryAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/zip/ZipInputStream", "getNextEntry", "()Ljava/util/zip/ZipEntry;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksZipOutputStreamPutNextEntryAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/zip/ZipOutputStream", "putNextEntry", "(Ljava/util/zip/ZipEntry;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCrc32UpdateByteArraySliceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/zip/CRC32", "update", "([BII)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDeflaterDeflateByteArraySliceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/zip/Deflater", "deflate", "([BII)I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDateTimeFormatterOfPatternAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/format/DateTimeFormatter", "ofPattern", "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatter;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDateTimeFormatterFormatAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/format/DateTimeFormatter", "format", "(Ljava/time/temporal/TemporalAccessor;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDateTimeFormatterBuilderAppendPatternAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/format/DateTimeFormatterBuilder", "appendPattern", "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDateTimeParseExceptionConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/format/DateTimeParseException", "<init>", "(Ljava/lang/String;Ljava/lang/CharSequence;I)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDecimalStyleOfDefaultLocaleAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/format/DecimalStyle", "ofDefaultLocale", "()Ljava/time/format/DecimalStyle;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksBigIntegerStringConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/math/BigInteger", "<init>", "(Ljava/lang/String;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksBigIntegerAddAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/math/BigInteger", "add", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksBigDecimalStringConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksBigDecimalValueOfLongAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/math/BigDecimal", "valueOf", "(J)Ljava/math/BigDecimal;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksMathContextIntRoundingConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/math/MathContext", "<init>", "(ILjava/math/RoundingMode;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharsetForNameAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/nio/charset/Charset", "forName", "(Ljava/lang/String;)Ljava/nio/charset/Charset;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharsetDefaultCharsetAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/nio/charset/Charset", "defaultCharset", "()Ljava/nio/charset/Charset;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharsetEncoderEncodeCharBufferAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/nio/charset/CharsetEncoder", "encode", "(Ljava/nio/CharBuffer;)Ljava/nio/ByteBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCharsetDecoderDecodeByteBufferAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/nio/charset/CharsetDecoder", "decode", "(Ljava/nio/ByteBuffer;)Ljava/nio/CharBuffer;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksStandardCharsetsInitializerAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/nio/charset/StandardCharsets", "<clinit>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAtomicIntegerConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/atomic/AtomicInteger", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAtomicIntegerGetAndIncrementAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/atomic/AtomicInteger", "getAndIncrement", "()I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAtomicReferenceCompareAndSetAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/atomic/AtomicReference", "compareAndSet", "(Ljava/lang/Object;Ljava/lang/Object;)Z")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAtomicLongArraySizedConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/atomic/AtomicLongArray", "<init>", "(I)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksAtomicBooleanLazySetAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/atomic/AtomicBoolean", "lazySet", "(Z)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksArenaOfAutoAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/foreign/Arena", "ofAuto", "()Ljava/lang/foreign/Arena;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksMemorySegmentByteSizeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/foreign/MemorySegment", "byteSize", "()J")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksValueLayoutWithOrderAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/foreign/ValueLayout", "withOrder", "(Ljava/nio/ByteOrder;)Ljava/lang/foreign/ValueLayout;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksFunctionDescriptorOfVoidAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/foreign/FunctionDescriptor", "ofVoid", "([Ljava/lang/foreign/MemoryLayout;)Ljava/lang/foreign/FunctionDescriptor;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksSymbolLookupFindAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/foreign/SymbolLookup", "find", "(Ljava/lang/String;)Ljava/util/Optional;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksMethodTypeDescriptorStringAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/invoke/MethodType", "descriptorString", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksCallSiteTypeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/invoke/CallSite", "type", "()Ljava/lang/invoke/MethodType;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksLambdaMetafactoryMetafactoryAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void keepsStringConcatFactoryCarveOutUnknown() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/invoke/StringConcatFactory", "makeConcat", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")))
            .isEqualTo(JdkCallableAccounting.Status.UNKNOWN);
    }

    @Test
    void keepsInvokeThrowableConstructorsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/invoke/WrongMethodTypeException", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksClassfileOfMethodAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/classfile/ClassFile", "of", "()Ljava/lang/classfile/ClassFile;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksOpcodeBytecodeAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/classfile/Opcode", "bytecode", "()I")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void keepsClassfileThrowableConstructorsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/classfile/constantpool/ConstantPoolException", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksSecureRandomGetInstanceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/security/SecureRandom", "getInstance", "(Ljava/lang/String;)Ljava/security/SecureRandom;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void keepsGeneralSecurityExceptionConstructorsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/security/GeneralSecurityException", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void keepsCertificateFactoryOutsideSecurityFamilyRejects() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/security/cert/CertificateFactory", "getInstance", "(Ljava/lang/String;)Ljava/security/cert/CertificateFactory;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void keepsCertificateExceptionConstructorsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/security/cert/CertificateException", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksCertPathValidatorGetInstanceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/security/cert/CertPathValidator", "getInstance", "(Ljava/lang/String;)Ljava/security/cert/CertPathValidator;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksIsoChronologyDateNowAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/chrono/IsoChronology", "dateNow", "()Ljava/time/chrono/LocalDate;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksHijrahDateNowAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/chrono/HijrahDate", "now", "()Ljava/time/chrono/HijrahDate;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksChronologyOfLocaleAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/time/chrono/Chronology", "ofLocale", "(Ljava/util/Locale;)Ljava/time/chrono/Chronology;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksLoggerGetLoggerAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/logging/Logger", "getLogger", "(Ljava/lang/String;)Ljava/util/logging/Logger;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksLogManagerGetLogManagerAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/logging/LogManager", "getLogManager", "()Ljava/util/logging/LogManager;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksFileHandlerConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/logging/FileHandler", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksIntrospectorGetBeanInfoAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/beans/Introspector", "getBeanInfo", "(Ljava/lang/Class;)Ljava/beans/BeanInfo;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksPropertyChangeSupportConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/beans/PropertyChangeSupport", "<init>", "(Ljava/lang/Object;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void keepsIntrospectionExceptionConstructorsSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/beans/IntrospectionException", "<init>", "(Ljava/lang/String;)V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksReentrantLockConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/locks/ReentrantLock", "<init>", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksConditionAwaitAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/locks/Condition", "await", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void keepsLockSupportParkSupported() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/util/concurrent/locks/LockSupport", "park", "()V")))
            .isEqualTo(JdkCallableAccounting.Status.SUPPORTED);
    }

    @Test
    void marksFormatFormatObjectAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/text/Format", "format", "(Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksNumberFormatGetInstanceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/text/NumberFormat", "getInstance", "()Ljava/text/NumberFormat;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDecimalFormatPatternConstructorAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/text/DecimalFormat", "<init>", "(Ljava/lang/String;)V")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksDateFormatGetDateInstanceAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/text/DateFormat", "getDateInstance", "()Ljava/text/DateFormat;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksMessageFormatVarargsFormatAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/text/MessageFormat", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksModuleDescriptorNameAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/module/ModuleDescriptor", "name", "()Ljava/lang/String;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksModuleFinderOfSystemAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/module/ModuleFinder", "ofSystem", "()Ljava/lang/module/ModuleFinder;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksModuleReferenceLocationAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/module/ModuleReference", "location", "()Ljava/util/Optional;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }

    @Test
    void marksConfigurationResolveAsExplicitRejected() {
        assertThat(JdkCallableAccounting.status(new MethodRef("java/lang/module/Configuration", "resolve", "(Ljava/lang/module/ModuleFinder;Ljava/lang/module/ModuleFinder;Ljava/util/Collection;)Ljava/lang/module/Configuration;")))
            .isEqualTo(JdkCallableAccounting.Status.EXPLICIT_REJECTED);
    }
}
