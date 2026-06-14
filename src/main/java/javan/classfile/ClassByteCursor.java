package javan.classfile;

import java.io.EOFException;
import java.io.UTFDataFormatException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Deterministic byte cursor for JVM classfile parsing.
 */
public final class ClassByteCursor {
    private final byte[] bytes;
    private int position;

    /**
     * Creates a cursor over a defensive copy of the supplied bytes.
     *
     * @param bytes classfile bytes
     */
    public ClassByteCursor(final byte[] bytes) {
        this(bytes, true);
    }

    private ClassByteCursor(final byte[] bytes, final boolean copy) {
        final byte[] checked = Objects.requireNonNull(bytes, "bytes");
        if (copy) {
            this.bytes = checked.clone();
            return;
        }
        this.bytes = checked;
    }

    /**
     * Reads an unsigned one-byte integer.
     *
     * @return unsigned one-byte value
     * @throws IOException when the classfile is truncated
     */
    public int u1() throws IOException {
        require(1L);
        final int value = bytes[position] & 0xFF;
        position++;
        return value;
    }

    /**
     * Reads an unsigned two-byte big-endian integer.
     *
     * @return unsigned two-byte value
     * @throws IOException when the classfile is truncated
     */
    public int u2() throws IOException {
        require(2L);
        final int value = ((bytes[position] & 0xFF) << 8)
            | (bytes[position + 1] & 0xFF);
        position += 2;
        return value;
    }

    /**
     * Reads an unsigned four-byte big-endian integer.
     *
     * @return unsigned four-byte value
     * @throws IOException when the classfile is truncated
     */
    public long u4() throws IOException {
        return i4() & 0xFFFF_FFFFL;
    }

    /**
     * Reads a signed four-byte big-endian integer.
     *
     * @return signed four-byte value
     * @throws IOException when the classfile is truncated
     */
    public int i4() throws IOException {
        require(4L);
        final int value = ((bytes[position] & 0xFF) << 24)
            | ((bytes[position + 1] & 0xFF) << 16)
            | ((bytes[position + 2] & 0xFF) << 8)
            | (bytes[position + 3] & 0xFF);
        position += 4;
        return value;
    }

    /**
     * Reads a signed eight-byte big-endian integer.
     *
     * @return signed eight-byte value
     * @throws IOException when the classfile is truncated
     */
    public long i8() throws IOException {
        require(8L);
        final long value = ((long) (bytes[position] & 0xFF) << 56)
            | ((long) (bytes[position + 1] & 0xFF) << 48)
            | ((long) (bytes[position + 2] & 0xFF) << 40)
            | ((long) (bytes[position + 3] & 0xFF) << 32)
            | ((long) (bytes[position + 4] & 0xFF) << 24)
            | ((long) (bytes[position + 5] & 0xFF) << 16)
            | ((long) (bytes[position + 6] & 0xFF) << 8)
            | (bytes[position + 7] & 0xFFL);
        position += 8;
        return value;
    }

    /**
     * Reads a four-byte IEEE 754 float.
     *
     * @return decoded float value
     * @throws IOException when the classfile is truncated
     */
    public float f4() throws IOException {
        return Float.intBitsToFloat(i4());
    }

    /**
     * Reads an eight-byte IEEE 754 double.
     *
     * @return decoded double value
     * @throws IOException when the classfile is truncated
     */
    public double f8() throws IOException {
        return Double.longBitsToDouble(i8());
    }

    /**
     * Reads exactly the requested number of bytes.
     *
     * @param length byte count
     * @return copied bytes
     * @throws IOException when the classfile is truncated
     */
    public byte[] bytes(final long length) throws IOException {
        require(length);
        final int count = Math.toIntExact(length);
        final byte[] result = Arrays.copyOfRange(bytes, position, position + count);
        position += count;
        return result;
    }

    /**
     * Skips exactly the requested number of bytes.
     *
     * @param length byte count
     * @return this cursor
     * @throws IOException when the classfile is truncated
     */
    public ClassByteCursor skip(final long length) throws IOException {
        require(length);
        position += Math.toIntExact(length);
        return this;
    }

    /**
     * Reads a classfile modified UTF-8 string prefixed by a {@code u2} byte length.
     *
     * @return decoded string
     * @throws IOException when the bytes are truncated or malformed
     */
    public String modifiedUtf8() throws IOException {
        final int length = u2();
        require(length);
        final int start = position;
        final int end = position + length;
        position = end;
        final char[] chars = new char[length];
        int byteIndex = start;
        int charIndex = 0;
        while (byteIndex < end) {
            final int first = bytes[byteIndex] & 0xFF;
            if (first <= 0x7F) {
                chars[charIndex] = (char) first;
                charIndex++;
                byteIndex++;
            } else if ((first >> 4) == 12 || (first >> 4) == 13) {
                if (byteIndex + 1 >= end) {
                    throw malformed(byteIndex - start);
                }
                final int second = bytes[byteIndex + 1] & 0xFF;
                if ((second & 0xC0) != 0x80) {
                    throw malformed(byteIndex - start);
                }
                chars[charIndex] = (char) (((first & 0x1F) << 6) | (second & 0x3F));
                charIndex++;
                byteIndex += 2;
            } else if ((first >> 4) == 14) {
                if (byteIndex + 2 >= end) {
                    throw malformed(byteIndex - start);
                }
                final int second = bytes[byteIndex + 1] & 0xFF;
                final int third = bytes[byteIndex + 2] & 0xFF;
                if ((second & 0xC0) != 0x80 || (third & 0xC0) != 0x80) {
                    throw malformed(byteIndex - start);
                }
                chars[charIndex] = (char) (((first & 0x0F) << 12) | ((second & 0x3F) << 6) | (third & 0x3F));
                charIndex++;
                byteIndex += 3;
            } else {
                throw malformed(byteIndex - start);
            }
        }
        return new String(chars, 0, charIndex);
    }

    private void require(final long length) throws EOFException {
        if (length < 0L) {
            throw new IllegalArgumentException("Negative classfile length: " + length);
        }
        if (length > Integer.MAX_VALUE || length > bytes.length - position) {
            throw new EOFException("Unexpected end of class file");
        }
    }

    private static UTFDataFormatException malformed(final int offset) {
        return new UTFDataFormatException("Malformed modified UTF-8 at byte " + offset);
    }
}
