package javan.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class Strings2Test {
    @Test
    void executableNameUsesLastClassToken() {
        assertThat(Strings2.executableName("com.acme.HelloTool")).isEqualTo("hellotool");
    }

    @Test
    void executableNameFallsBackWhenLastTokenIsEmpty() {
        assertThat(Strings2.executableName("com.acme.")).isEqualTo("app");
    }

    @Test
    void executableNameCollapsesRepeatedUnsupportedCharacters() {
        assertThat(Strings2.executableName("Hello  Tool")).isEqualTo("hello-tool");
    }

    @Test
    void trimAsciiRemovesLeadingAndTrailingAsciiWhitespace() {
        assertThat(Strings2.trimAscii(" \tvalue\r\n")).isEqualTo("value");
    }

    @Test
    void stripTrailingAsciiKeepsLeadingWhitespace() {
        assertThat(Strings2.stripTrailingAscii(" value \n")).isEqualTo(" value");
    }

    @Test
    void sliceRejectsNegativeStart() {
        assertThatThrownBy(() -> Strings2.slice("abc", -1, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid string range");
    }

    @Test
    void sliceRejectsEndBeforeStart() {
        assertThatThrownBy(() -> Strings2.slice("abc", 2, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid string range");
    }

    @Test
    void sliceRejectsEndPastLength() {
        assertThatThrownBy(() -> Strings2.slice("abc", 0, 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid string range");
    }

    @Test
    void toAsciiUpperCaseLeavesNonLowercaseCharactersUnchanged() {
        assertThat(Strings2.toAsciiUpperCase("A-1")).isEqualTo("A-1");
    }

    @Test
    void equalsAsciiIgnoreCaseReturnsFalseForDifferentLength() {
        assertThat(Strings2.equalsAsciiIgnoreCase("abc", "ab")).isFalse();
    }

    @Test
    void equalsAsciiIgnoreCaseReturnsFalseForDifferentCharacter() {
        assertThat(Strings2.equalsAsciiIgnoreCase("abc", "abd")).isFalse();
    }

    @Test
    void equalsAsciiIgnoreCaseReturnsTrueForAsciiCaseDifference() {
        assertThat(Strings2.equalsAsciiIgnoreCase("AbC", "aBc")).isTrue();
    }

    @Test
    void hexLongFormatsZero() {
        assertThat(Strings2.hexLong(0L)).isEqualTo("0");
    }

    @Test
    void classNameConversionsReplaceSeparators() {
        assertThat(Strings2.externalClassName("com/acme/Main")).isEqualTo("com.acme.Main");
        assertThat(Strings2.internalClassName("com.acme.Main")).isEqualTo("com/acme/Main");
    }

    @Test
    void runtimeAsciiStringConstantAcceptsAsciiEscapes() {
        assertThat(Strings2.isRuntimeAsciiStringConstant("hello\nworld")).isTrue();
    }

    @Test
    void runtimeAsciiStringConstantRejectsNonAscii() {
        assertThat(Strings2.isRuntimeAsciiStringConstant("caf\u00e9")).isFalse();
    }

    @Test
    void runtimeAsciiStringConstantRejectsEmbeddedNul() {
        assertThat(Strings2.isRuntimeAsciiStringConstant("a\0b")).isFalse();
    }
}
