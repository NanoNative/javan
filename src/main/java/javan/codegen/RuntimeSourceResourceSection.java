package javan.codegen;

import javan.build.ResourceBundler;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

final class RuntimeSourceResourceSection {
    private RuntimeSourceResourceSection() {
    }

    static String render(final List<ResourceBundler.ResourceFile> resources) throws IOException {
        final StringBuilder result = new StringBuilder();
        result.append("""
            
            typedef struct {
                const char* path;
                int length;
                const signed char* bytes;
            } javan_embedded_resource;

            typedef struct {
                int magic;
                int loader_kind;
                int reserved0;
                int reserved1;
            } javan_class_loader_value;
            """);
        appendEmbeddedResources(result, resources);
        result.append("""

            #define JAVAN_CLASS_LOADER_MAGIC 0x4a434c44
            #define JAVAN_CLASS_LOADER_KIND_SYSTEM 1

            static javan_class_loader_value javan_system_class_loader_value = {
                JAVAN_CLASS_LOADER_MAGIC,
                JAVAN_CLASS_LOADER_KIND_SYSTEM,
                0,
                0
            };

            static javan_byte_input_stream_value* javan_byte_input_stream_checked(
                void* value,
                int expected_magic,
                const char* null_message,
                const char* invalid_message
            ) {
                if (value == NULL) {
                    javan_panic(null_message);
                }
                javan_byte_input_stream_value* stream = (javan_byte_input_stream_value*) value;
                javan_byte_array* bytes = (javan_byte_array*) stream->bytes;
                if (stream->magic != expected_magic
                    || stream->bytes == NULL
                    || stream->length < 0
                    || stream->position < 0
                    || stream->position > stream->length
                    || bytes->length != stream->length) {
                    javan_panic(invalid_message);
                }
                return stream;
            }

            static const javan_embedded_resource* javan_embedded_resource_find(const char* path) {
                if (path == NULL) {
                    return NULL;
                }
                for (int index = 0; index < javan_embedded_resource_count; index++) {
                    const javan_embedded_resource* resource = &javan_embedded_resources[index];
                    if (resource->path != NULL && strcmp(resource->path, path) == 0) {
                        return resource;
                    }
                }
                return NULL;
            }

            static char* javan_embedded_resource_duplicate_path(const char* value) {
                unsigned long length = (unsigned long) strlen(value);
                char* result = (char*) javan_raw_calloc_retry(length + 1UL);
                if (result == NULL) {
                    javan_panic("out of memory");
                }
                if (length > 0UL) {
                    memcpy(result, value, length);
                }
                result[length] = '\\0';
                return result;
            }

            static char* javan_class_resource_path(javan_runtime_class_state* state, const char* name) {
                if (name == NULL) {
                    javan_panic("null resource name");
                }
                if (name[0] == '/') {
                    return javan_embedded_resource_duplicate_path(name + 1);
                }
                const char* binary_name = state->binary_name;
                const char* package_end = strrchr(binary_name, '.');
                if (package_end == NULL) {
                    return javan_embedded_resource_duplicate_path(name);
                }
                unsigned long package_length = (unsigned long) (package_end - binary_name);
                unsigned long name_length = (unsigned long) strlen(name);
                char* path = (char*) javan_raw_calloc_retry(package_length + 1UL + name_length + 1UL);
                if (path == NULL) {
                    javan_panic("out of memory");
                }
                for (unsigned long index = 0; index < package_length; index++) {
                    char ch = binary_name[index];
                    path[index] = ch == '.' ? '/' : ch;
                }
                path[package_length] = '/';
                if (name_length > 0UL) {
                    memcpy(path + package_length + 1UL, name, name_length);
                }
                path[package_length + 1UL + name_length] = '\\0';
                return path;
            }

            int javan_is_system_class_loader(void* value) {
                return value == (void*) &javan_system_class_loader_value
                    && javan_system_class_loader_value.magic == JAVAN_CLASS_LOADER_MAGIC
                    && javan_system_class_loader_value.loader_kind == JAVAN_CLASS_LOADER_KIND_SYSTEM;
            }

            void* javan_class_loader_system(void) {
                return (void*) &javan_system_class_loader_value;
            }

            static void* javan_embedded_resource_as_stream(const javan_embedded_resource* resource) {
                if (resource == NULL) {
                    return NULL;
                }
                void* bytes_root = NULL;
                void* stream_root = NULL;
                void** roots[] = {
                    (void**) &bytes_root,
                    (void**) &stream_root
                };
                javan_root_frame_push(roots, 2);
                bytes_root = javan_byte_array_from(resource->bytes, resource->length);
                stream_root = javan_alloc(sizeof(javan_byte_input_stream_value));
                javan_byte_input_stream_value* stream = (javan_byte_input_stream_value*) stream_root;
                stream->magic = JAVAN_RESOURCE_INPUT_STREAM_MAGIC;
                stream->position = 0;
                stream->length = resource->length;
                stream->reserved0 = 0;
                stream->bytes = bytes_root;
                javan_update_runtime_allocation_kind(stream_root, JAVAN_RUNTIME_KIND_RESOURCE_INPUT_STREAM);
                javan_root_frame_pop(roots);
                return stream_root;
            }

            void* javan_class_loader_resource_as_stream(void* loader_value, void* name_value) {
                if (javan_is_system_class_loader(loader_value) == 0) {
                    javan_panic("unsupported class loader");
                }
                if (name_value == NULL) {
                    javan_panic("null resource name");
                }
                const char* name = (const char*) name_value;
                if (name[0] == '/') {
                    return NULL;
                }
                return javan_embedded_resource_as_stream(javan_embedded_resource_find(name));
            }

            void* javan_class_resource_as_stream(void* class_value, void* name_value) {
                void* class_root = class_value;
                void* name_root = name_value;
                void** roots[] = {
                    (void**) &class_root,
                    (void**) &name_root
                };
                javan_root_frame_push(roots, 2);
                javan_runtime_lock_enter();
                javan_runtime_class_state* state = javan_runtime_class_checked_unlocked(class_root);
                char* resource_path = javan_class_resource_path(state, (const char*) name_root);
                const javan_embedded_resource* resource = javan_embedded_resource_find(resource_path);
                free(resource_path);
                void* stream = javan_embedded_resource_as_stream(resource);
                javan_runtime_lock_leave();
                javan_root_frame_pop(roots);
                return stream;
            }

            void* javan_loader_resource_as_stream(void* name_value) {
                return javan_class_loader_resource_as_stream(javan_class_loader_system(), name_value);
            }

            static int javan_byte_input_stream_read(
                void* value,
                int expected_magic,
                const char* null_message,
                const char* invalid_message
            ) {
                javan_byte_input_stream_value* stream = javan_byte_input_stream_checked(value, expected_magic, null_message, invalid_message);
                javan_byte_array* bytes = (javan_byte_array*) stream->bytes;
                if (stream->position >= stream->length) {
                    return -1;
                }
                int next = ((unsigned char) bytes->values[stream->position]) & 0xff;
                stream->position++;
                return next;
            }

            static int javan_byte_input_stream_read_bytes_range(
                void* value,
                void* bytes_value,
                int offset,
                int length,
                int expected_magic,
                const char* null_message,
                const char* invalid_message
            ) {
                javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
                javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
                javan_array_range_checked((javan_array_header*) bytes, offset, length);
                if (length == 0) {
                    return 0;
                }
                javan_byte_input_stream_value* stream = javan_byte_input_stream_checked(value, expected_magic, null_message, invalid_message);
                javan_byte_array* source = (javan_byte_array*) stream->bytes;
                if (stream->position >= stream->length) {
                    return -1;
                }
                int remaining = stream->length - stream->position;
                int count = remaining < length ? remaining : length;
                memcpy(bytes->values + offset, source->values + stream->position, (unsigned long) count);
                stream->position += count;
                return count;
            }

            static void* javan_byte_input_stream_read_all_bytes(
                void* value,
                int expected_magic,
                const char* null_message,
                const char* invalid_message
            ) {
                void* stream_root = value;
                void* result = NULL;
                void** roots[] = {
                    (void**) &stream_root,
                    (void**) &result
                };
                javan_root_frame_push(roots, 2);
                javan_byte_input_stream_value* stream = javan_byte_input_stream_checked(stream_root, expected_magic, null_message, invalid_message);
                result = javan_arrays_copy_of_range_byte(stream->bytes, stream->position, stream->length);
                stream = javan_byte_input_stream_checked(stream_root, expected_magic, null_message, invalid_message);
                stream->position = stream->length;
                javan_root_frame_pop(roots);
                return result;
            }

            int javan_resource_input_stream_read(void* value) {
                return javan_byte_input_stream_read(value, JAVAN_RESOURCE_INPUT_STREAM_MAGIC, "null resource input stream", "unsupported resource input stream object");
            }

            int javan_resource_input_stream_read_bytes(void* value, void* bytes_value) {
                javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
                javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
                return javan_byte_input_stream_read_bytes_range(value, bytes_value, 0, bytes->length, JAVAN_RESOURCE_INPUT_STREAM_MAGIC, "null resource input stream", "unsupported resource input stream object");
            }

            int javan_resource_input_stream_read_bytes_range(void* value, void* bytes_value, int offset, int length) {
                return javan_byte_input_stream_read_bytes_range(value, bytes_value, offset, length, JAVAN_RESOURCE_INPUT_STREAM_MAGIC, "null resource input stream", "unsupported resource input stream object");
            }

            void* javan_resource_input_stream_read_all_bytes(void* value) {
                return javan_byte_input_stream_read_all_bytes(value, JAVAN_RESOURCE_INPUT_STREAM_MAGIC, "null resource input stream", "unsupported resource input stream object");
            }

            int javan_http_exchange_input_stream_read(void* value) {
                return javan_byte_input_stream_read(value, JAVAN_HTTP_EXCHANGE_INPUT_STREAM_MAGIC, "null HTTP request body stream", "invalid HTTP request body stream");
            }

            int javan_http_exchange_input_stream_read_bytes(void* value, void* bytes_value) {
                javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
                javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
                return javan_byte_input_stream_read_bytes_range(value, bytes_value, 0, bytes->length, JAVAN_HTTP_EXCHANGE_INPUT_STREAM_MAGIC, "null HTTP request body stream", "invalid HTTP request body stream");
            }

            int javan_http_exchange_input_stream_read_bytes_range(void* value, void* bytes_value, int offset, int length) {
                return javan_byte_input_stream_read_bytes_range(value, bytes_value, offset, length, JAVAN_HTTP_EXCHANGE_INPUT_STREAM_MAGIC, "null HTTP request body stream", "invalid HTTP request body stream");
            }

            void* javan_http_exchange_input_stream_read_all_bytes(void* value) {
                return javan_byte_input_stream_read_all_bytes(value, JAVAN_HTTP_EXCHANGE_INPUT_STREAM_MAGIC, "null HTTP request body stream", "invalid HTTP request body stream");
            }

            void javan_resource_input_stream_close(void* value) {
                (void) value;
            }
            """);
        return result.toString();
    }

    private static void appendEmbeddedResources(
        final StringBuilder result,
        final List<ResourceBundler.ResourceFile> resources
    ) throws IOException {
        if (resources.isEmpty()) {
            result.append("""
                static const javan_embedded_resource javan_embedded_resources[] = {
                    {NULL, 0, NULL}
                };
                static const int javan_embedded_resource_count = 0;
                """);
            return;
        }
        for (int index = 0; index < resources.size(); index++) {
            final ResourceBundler.ResourceFile resource = resources.get(index);
            appendResourceBytes(result, index, Files.readAllBytes(resource.source()));
        }
        result.append("static const javan_embedded_resource javan_embedded_resources[] = {\n");
        for (int index = 0; index < resources.size(); index++) {
            final ResourceBundler.ResourceFile resource = resources.get(index);
            result.append("    {\"")
                .append(escapeCString(resource.path()))
                .append("\", ")
                .append(resource.size())
                .append(", javan_embedded_resource_bytes_")
                .append(index)
                .append("}");
            if (index + 1 < resources.size()) {
                result.append(",");
            }
            result.append("\n");
        }
        result.append("};\n");
        result.append("static const int javan_embedded_resource_count = ")
            .append(resources.size())
            .append(";\n");
    }

    private static void appendResourceBytes(final StringBuilder result, final int index, final byte[] bytes) {
        result.append("static const signed char javan_embedded_resource_bytes_")
            .append(index)
            .append("[] = {");
        if (bytes.length == 0) {
            result.append("0");
        } else {
            for (int byteIndex = 0; byteIndex < bytes.length; byteIndex++) {
                if (byteIndex > 0) {
                    result.append(", ");
                }
                result.append(bytes[byteIndex]);
            }
        }
        result.append("};\n");
    }

    private static String escapeCString(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            result.append(escapeCChar(value.charAt(index)));
        }
        return result.toString();
    }

    private static String escapeCChar(final char ch) {
        final StringBuilder result = new StringBuilder();
        switch (ch) {
            case '\\':
                return "\\\\";
            case '"':
                return "\\\"";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            default:
                if (ch < 32 || ch > 126) {
                    result.append('\\');
                    appendOctal(result, ch);
                    return result.toString();
                }
                result.append(ch);
                return result.toString();
        }
    }

    private static void appendOctal(final StringBuilder result, final int value) {
        result.append((char) ('0' + ((value >> 6) & 7)));
        result.append((char) ('0' + ((value >> 3) & 7)));
        result.append((char) ('0' + (value & 7)));
    }
}
