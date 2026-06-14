package javan.build;

import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.util.Strings2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds deterministic Java jar output from compiled classes and copied resources.
 */
public final class JarPackager {
    private static final int ZIP_LOCAL_FILE_HEADER = 0x04034B50;
    private static final int ZIP_CENTRAL_DIRECTORY_HEADER = 0x02014B50;
    private static final int ZIP_END_OF_CENTRAL_DIRECTORY = 0x06054B50;
    private static final int ZIP_STORED = 0;
    private static final int ZIP_UTF8_FLAG = 1 << 11;
    private static final int ZIP_VERSION_NEEDED = 10;
    private static final int ZIP_VERSION_MADE_BY = 20;
    private static final int ZIP_DOS_TIME = 0;
    private static final int ZIP_DOS_DATE_1980_01_01 = 33;

    private static final List<String> IGNORED_DIRECTORIES = List.of(
        ".git", ".idea", ".gradle", ".mvn", ".javan", "target", "build", "out", "node_modules"
    );

    /**
     * Packages a Java jar.
     *
     * @param layout detected project layout
     * @param output target jar
     * @param mainClass optional manifest main class
     * @return target jar
     * @throws IOException when packaging fails
     */
    public Path packageJar(final ProjectLayout layout, final Path output, final Optional<String> mainClass) throws IOException {
        Files.createDirectories(output.getParent());
        if (layout.inputKind() == InputKind.JAR_FILE) {
            Files.copy(layout.input(), output, StandardCopyOption.REPLACE_EXISTING);
            return output;
        }
        final List<ArchiveEntry> entries = new ArrayList<>();
        final List<String> written = new ArrayList<>();
        entries.add(new ArchiveEntry("META-INF/MANIFEST.MF", manifestBytes(mainClass)));
        written.add("META-INF/MANIFEST.MF");
        for (final Path root : layout.classFolders()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            final List<Path> files = regularFiles(root);
            for (final Path file : files) {
                final String name = Strings2.replaceChar(root.relativize(file).toString(), File.separatorChar, '/');
                if (name.equals("META-INF/MANIFEST.MF") || containsString(written, name)) {
                    continue;
                }
                written.add(name);
                entries.add(new ArchiveEntry(name, Files.readAllBytes(file)));
            }
        }
        Files.write(output, archive(entries));
        return output;
    }

    private static boolean containsString(final List<String> values, final String target) {
        for (final String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> regularFiles(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        final List<Path> result = new ArrayList<>();
        collectRegularFiles(root, root, result);
        return result;
    }

    private static void collectRegularFiles(final Path root, final Path directory, final List<Path> result) throws IOException {
        final DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        for (final Path file : stream) {
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!ignoredTopLevelDirectory(root, file)) {
                    collectRegularFiles(root, file, result);
                }
                continue;
            }
            if (Files.isRegularFile(file)) {
                insertSorted(result, file);
            }
        }
        stream.close();
    }

    private static void insertSorted(final List<Path> values, final Path value) {
        int position = values.size() - 1;
        final String text = value.toString();
        while (position >= 0 && Strings2.compareAscii(values.get(position).toString(), text) > 0) {
            position--;
        }
        values.add(position + 1, value);
    }

    private static boolean ignoredTopLevelDirectory(final Path root, final Path directory) {
        final Path name = directory.getFileName();
        if (name == null || !containsString(IGNORED_DIRECTORIES, name.toString())) {
            return false;
        }
        return topLevelChild(root, directory);
    }

    private static boolean topLevelChild(final Path root, final Path directory) {
        if (directory.equals(root)) {
            return false;
        }
        return root.toAbsolutePath().normalize().relativize(directory.toAbsolutePath().normalize()).getNameCount() == 1;
    }

    private static byte[] manifestBytes(final Optional<String> mainClass) {
        final String main = mainClass.orElse("");
        final StringBuilder manifest = new StringBuilder();
        manifest.append("Manifest-Version: 1.0\r\n");
        if (!Strings2.isBlank(main)) {
            manifest.append("Main-Class: ").append(main).append("\r\n");
        }
        manifest.append("\r\n");
        return utf8(manifest.toString());
    }

    private static byte[] archive(final List<ArchiveEntry> entries) throws IOException {
        if (entries.size() > 65_535) {
            throw new IOException("Too many jar entries");
        }
        final ByteVector output = new ByteVector();
        final List<CentralRecord> central = new ArrayList<>();
        for (final ArchiveEntry entry : entries) {
            final byte[] name = utf8(entry.name);
            final int offset = output.size();
            final int crc = crc32(entry.data);
            writeLocalHeader(output, name, entry.data, crc);
            output.addBytes(entry.data);
            central.add(new CentralRecord(name, crc, entry.data.length, offset));
        }
        final int centralStart = output.size();
        for (final CentralRecord record : central) {
            writeCentralRecord(output, record);
        }
        final int centralSize = output.size() - centralStart;
        writeEndRecord(output, central.size(), centralSize, centralStart);
        return output.toArray();
    }

    private static void writeLocalHeader(final ByteVector output, final byte[] name, final byte[] data, final int crc) {
        output.addIntLe(ZIP_LOCAL_FILE_HEADER);
        output.addShortLe(ZIP_VERSION_NEEDED);
        output.addShortLe(ZIP_UTF8_FLAG);
        output.addShortLe(ZIP_STORED);
        output.addShortLe(ZIP_DOS_TIME);
        output.addShortLe(ZIP_DOS_DATE_1980_01_01);
        output.addIntLe(crc);
        output.addIntLe(data.length);
        output.addIntLe(data.length);
        output.addShortLe(name.length);
        output.addShortLe(0);
        output.addBytes(name);
    }

    private static void writeCentralRecord(final ByteVector output, final CentralRecord record) {
        output.addIntLe(ZIP_CENTRAL_DIRECTORY_HEADER);
        output.addShortLe(ZIP_VERSION_MADE_BY);
        output.addShortLe(ZIP_VERSION_NEEDED);
        output.addShortLe(ZIP_UTF8_FLAG);
        output.addShortLe(ZIP_STORED);
        output.addShortLe(ZIP_DOS_TIME);
        output.addShortLe(ZIP_DOS_DATE_1980_01_01);
        output.addIntLe(record.crc);
        output.addIntLe(record.size);
        output.addIntLe(record.size);
        output.addShortLe(record.name.length);
        output.addShortLe(0);
        output.addShortLe(0);
        output.addShortLe(0);
        output.addShortLe(0);
        output.addIntLe(0);
        output.addIntLe(record.offset);
        output.addBytes(record.name);
    }

    private static void writeEndRecord(
        final ByteVector output,
        final int entries,
        final int centralDirectorySize,
        final int centralDirectoryOffset
    ) {
        output.addIntLe(ZIP_END_OF_CENTRAL_DIRECTORY);
        output.addShortLe(0);
        output.addShortLe(0);
        output.addShortLe(entries);
        output.addShortLe(entries);
        output.addIntLe(centralDirectorySize);
        output.addIntLe(centralDirectoryOffset);
        output.addShortLe(0);
    }

    private static int crc32(final byte[] data) {
        int crc = -1;
        for (final byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) == 0) {
                    crc >>>= 1;
                } else {
                    crc = (crc >>> 1) ^ 0xEDB88320;
                }
            }
        }
        return ~crc;
    }

    private static byte[] utf8(final String value) {
        final ByteVector bytes = new ByteVector();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch <= 0x7F) {
                bytes.addByte(ch);
            } else if (ch <= 0x7FF) {
                bytes.addByte(0xC0 | (ch >> 6));
                bytes.addByte(0x80 | (ch & 0x3F));
            } else if (ch >= 0xD800 && ch <= 0xDBFF && index + 1 < value.length()) {
                final char low = value.charAt(index + 1);
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    final int codePoint = 0x10000 + ((ch - 0xD800) << 10) + (low - 0xDC00);
                    bytes.addByte(0xF0 | (codePoint >> 18));
                    bytes.addByte(0x80 | ((codePoint >> 12) & 0x3F));
                    bytes.addByte(0x80 | ((codePoint >> 6) & 0x3F));
                    bytes.addByte(0x80 | (codePoint & 0x3F));
                    index++;
                } else {
                    bytes.addByte('?');
                }
            } else {
                bytes.addByte(0xE0 | (ch >> 12));
                bytes.addByte(0x80 | ((ch >> 6) & 0x3F));
                bytes.addByte(0x80 | (ch & 0x3F));
            }
        }
        return bytes.toArray();
    }

    private static final class ByteVector {
        private byte[] data = new byte[256];
        private int size;

        int size() {
            return size;
        }

        void addByte(final int value) {
            ensure(1);
            data[size] = (byte) value;
            size++;
        }

        void addShortLe(final int value) {
            addByte(value);
            addByte(value >>> 8);
        }

        void addIntLe(final int value) {
            addByte(value);
            addByte(value >>> 8);
            addByte(value >>> 16);
            addByte(value >>> 24);
        }

        void addBytes(final byte[] values) {
            ensure(values.length);
            for (final byte value : values) {
                data[size] = value;
                size++;
            }
        }

        byte[] toArray() {
            return Arrays.copyOf(data, size);
        }

        private void ensure(final int additional) {
            final int required = size + additional;
            if (required <= data.length) {
                return;
            }
            int next = data.length;
            while (next < required) {
                next *= 2;
            }
            data = Arrays.copyOf(data, next);
        }
    }

    private static final class ArchiveEntry {
        private final String name;
        private final byte[] data;

        ArchiveEntry(final String name, final byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private static final class CentralRecord {
        private final byte[] name;
        private final int crc;
        private final int size;
        private final int offset;

        CentralRecord(final byte[] name, final int crc, final int size, final int offset) {
            this.name = name;
            this.crc = crc;
            this.size = size;
            this.offset = offset;
        }
    }
}
