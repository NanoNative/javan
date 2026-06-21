package javan.classfile;

import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.UTFDataFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ClassByteCursorTest {
    @Test
    void cursorReadsPrimitiveValuesAndSlices() throws Exception {
        final ClassByteCursor cursor = new ClassByteCursor(new byte[]{
            0x01,
            0x00, 0x02,
            0x00, 0x00, 0x00, 0x03,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04,
            0x40, 0x20, 0x00, 0x00,
            0x40, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x02, 'O', 'K'
        });

        assertThat(cursor.u1()).isEqualTo(1);
        assertThat(cursor.u2()).isEqualTo(2);
        assertThat(cursor.u4()).isEqualTo(3);
        assertThat(cursor.i8()).isEqualTo(4L);
        assertThat(cursor.f4()).isEqualTo(2.5f);
        assertThat(cursor.f8()).isEqualTo(3.0d);
        assertThat(cursor.modifiedUtf8()).isEqualTo("OK");
    }

    @Test
    void cursorRejectsNegativeLengths() {
        final ClassByteCursor cursor = new ClassByteCursor(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> cursor.skip(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Negative classfile length: -1");
    }

    @Test
    void cursorRejectsTruncatedReads() {
        final ClassByteCursor cursor = new ClassByteCursor(new byte[]{0x00});

        assertThatThrownBy(cursor::u2)
            .isInstanceOf(EOFException.class)
            .hasMessage("Unexpected end of class file");
    }

    @Test
    void cursorRejectsMalformedModifiedUtf8() {
        final ClassByteCursor cursor = new ClassByteCursor(new byte[]{
            0x00, 0x02,
            (byte) 0xC2, 0x20
        });

        assertThatThrownBy(cursor::modifiedUtf8)
            .isInstanceOf(UTFDataFormatException.class)
            .hasMessage("Malformed modified UTF-8 at byte 0");
    }
}
