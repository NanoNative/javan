package javan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class JsonTest {
    @Test
    void stringEscapesLowControlCharacter() {
        assertThat(Json.string("\b")).isEqualTo("\"\\u0008\"");
    }

    @Test
    void stringEscapesHighControlCharacter() {
        assertThat(Json.string("\u001f")).isEqualTo("\"\\u001f\"");
    }

    @Test
    void stringListSeparatesValues() {
        assertThat(Json.stringList(List.of("a", "b"))).isEqualTo("[\"a\", \"b\"]");
    }

    @Test
    void intListSeparatesValues() {
        assertThat(Json.intList(List.of(1, 2))).isEqualTo("[1, 2]");
    }
}
