package javan.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Computes SHA-256 without depending on JDK security providers unavailable to the native compiler.
 */
public final class Sha256 {
    private static final String HEX = "0123456789abcdef";
    private static final int[] ROUND = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };
    private final int[] state = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };
    private final byte[] block = new byte[64];
    private long length;
    private int blockLength;
    private boolean finished;

    /**
     * Computes the digest of one regular file.
     *
     * @param file file to read
     * @return lowercase hexadecimal digest
     * @throws IOException when the file cannot be read
     */
    public static String of(final Path file) throws IOException {
        return new Sha256().update(Files.readAllBytes(file)).hex();
    }

    /**
     * Adds bytes to this digest.
     *
     * @param value bytes to add
     * @return this digest
     */
    public Sha256 update(final byte[] value) {
        for (int index = 0; index < value.length; index++) {
            updateByte(value[index] & 0xff);
        }
        return this;
    }

    /**
     * Adds one big-endian integer to this digest.
     *
     * @param value integer to add
     * @return this digest
     */
    public Sha256 updateInt(final int value) {
        updateByte(value >>> 24);
        updateByte(value >>> 16);
        updateByte(value >>> 8);
        updateByte(value);
        return this;
    }

    /**
     * Adds one big-endian long to this digest.
     *
     * @param value long to add
     * @return this digest
     */
    public Sha256 updateLong(final long value) {
        updateInt((int) (value >>> 32));
        updateInt((int) value);
        return this;
    }

    /**
     * Finishes this digest and returns lowercase hexadecimal output.
     *
     * @return 64-character digest
     */
    public String hex() {
        finish();
        final StringBuilder result = new StringBuilder(64);
        for (int index = 0; index < state.length; index++) {
            for (int shift = 28; shift >= 0; shift -= 4) {
                result.append(HEX.charAt((state[index] >>> shift) & 15));
            }
        }
        return result.toString();
    }

    private void updateByte(final int value) {
        if (finished) {
            throw new IllegalStateException("SHA-256 is already finished");
        }
        block[blockLength] = (byte) value;
        blockLength++;
        length++;
        if (blockLength == block.length) {
            compress();
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        final long bits = length * 8L;
        updateByte(0x80);
        while (blockLength != 56) {
            updateByte(0);
        }
        for (int shift = 56; shift >= 0; shift -= 8) {
            updateByte((int) (bits >>> shift));
        }
        finished = true;
    }

    private void compress() {
        final int[] words = new int[64];
        for (int index = 0; index < 16; index++) {
            final int offset = index * 4;
            words[index] = ((block[offset] & 0xff) << 24)
                | ((block[offset + 1] & 0xff) << 16)
                | ((block[offset + 2] & 0xff) << 8)
                | (block[offset + 3] & 0xff);
        }
        for (int index = 16; index < words.length; index++) {
            final int first = rotateRight(words[index - 15], 7)
                ^ rotateRight(words[index - 15], 18)
                ^ (words[index - 15] >>> 3);
            final int second = rotateRight(words[index - 2], 17)
                ^ rotateRight(words[index - 2], 19)
                ^ (words[index - 2] >>> 10);
            words[index] = words[index - 16] + first + words[index - 7] + second;
        }
        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];
        int f = state[5];
        int g = state[6];
        int h = state[7];
        for (int index = 0; index < words.length; index++) {
            final int first = h
                + (rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25))
                + ((e & f) ^ (~e & g))
                + ROUND[index]
                + words[index];
            final int second = (rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22))
                + ((a & b) ^ (a & c) ^ (b & c));
            h = g;
            g = f;
            f = e;
            e = d + first;
            d = c;
            c = b;
            b = a;
            a = first + second;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
        state[4] += e;
        state[5] += f;
        state[6] += g;
        state[7] += h;
        blockLength = 0;
    }

    private static int rotateRight(final int value, final int distance) {
        return (value >>> distance) | (value << (32 - distance));
    }
}
